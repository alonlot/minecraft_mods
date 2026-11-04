package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Basilisk: a fourteen-block serpent whose stare kills, given time.
 *
 * <p>The killing gaze is charged rather than instant. Meeting its eyes starts a
 * counter that runs to {@link #GAZE_FULL_CHARGE} ticks and decays twice as fast
 * the moment the victim looks away, breaks line of sight, or the basilisk is
 * blinded. The charge is published to the client as the amplifier of a
 * {@code basilisk_gaze} effect, which is what the screen overlay reads — no
 * custom packet, so it behaves on a dedicated server.
 *
 * <p>Damage landed from in front accumulates against a head hitzone. Past
 * {@link #BLIND_THRESHOLD} the basilisk is permanently blinded: the gaze dies,
 * the eye layer is dropped, and it starts hunting whatever it can hear instead
 * of whatever it can see.
 *
 * <p>The client also reads the position ring buffer here to place the spine —
 * see {@code BasiliskModel}. It is recorded on both sides because positions are
 * synced, so both agree on the path.
 */
public class BasiliskEntity extends Monster {

    // --- gaze -----------------------------------------------------------------
    private static final double GAZE_RANGE = 24.0D;
    private static final int GAZE_FULL_CHARGE = 50;
    private static final int GAZE_DECAY_RATE = 2;
    /** How closely the basilisk must be facing its victim: cos of the half-angle. */
    private static final double BASILISK_AIM_DOT = 0.82D;
    /** How closely the victim must be looking back — about 12 degrees. */
    private static final double VICTIM_AIM_DOT = 0.978D;
    private static final int GAZE_EFFECT_DURATION = 40;
    private static final int GAZE_EFFECT_REFRESH = 15;
    private static final int PETRIFY_DURATION = 60;

    // --- blinding -------------------------------------------------------------
    private static final float BLIND_THRESHOLD = 60.0F;
    /** Cos of 60 degrees: hits landed inside this front cone count as head hits. */
    private static final double HEAD_HITZONE_DOT = 0.5D;

    // --- action timings -------------------------------------------------------
    private static final int REAR_TICKS = 30;
    private static final int STRIKE_TICKS = 14;
    private static final int STRIKE_HIT_TICK = 6;
    private static final int SPIT_TICKS = 22;
    private static final int SPIT_RELEASE_TICK = 10;
    private static final int SWEEP_TICKS = 24;
    private static final int SWEEP_HIT_TICK = 12;
    private static final int BURROW_DOWN_TICKS = 26;
    private static final int BURROWED_MAX_TICKS = 70;
    private static final int BURROW_UP_TICKS = 22;
    private static final int BURROW_ERUPT_TICK = 8;

    private static final int STRIKE_COOLDOWN = 70;
    private static final int SPIT_COOLDOWN = 90;
    private static final int SWEEP_COOLDOWN = 110;
    private static final int BURROW_COOLDOWN = 260;

    private static final double STRIKE_MIN_RANGE = 4.0D;
    private static final double STRIKE_MAX_RANGE = 12.0D;
    private static final double STRIKE_REACH = 7.0D;
    private static final double SPIT_MIN_RANGE = 10.0D;
    private static final double SPIT_MAX_RANGE = 26.0D;
    private static final double SWEEP_RANGE = 7.0D;
    private static final double BURROW_MIN_RANGE = 14.0D;

    // --- spine trail ----------------------------------------------------------
    private static final int TRAIL_LENGTH = 160;

    private static final EntityDataAccessor<Byte> DATA_ACTION_STATE =
            SynchedEntityData.defineId(BasiliskEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICK =
            SynchedEntityData.defineId(BasiliskEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BLINDED =
            SynchedEntityData.defineId(BasiliskEntity.class, EntityDataSerializers.BOOLEAN);

    public enum ActionState {
        NONE, REAR, STRIKE, SPIT, TAIL_SWEEP, BURROW_DOWN, BURROWED, BURROW_UP;

        private static final ActionState[] VALUES = values();

        public static ActionState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) {
                return NONE;
            }
            return VALUES[b];
        }
    }

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState rearAnimationState = new AnimationState();
    public final AnimationState strikeAnimationState = new AnimationState();
    public final AnimationState spitAnimationState = new AnimationState();
    public final AnimationState biteAnimationState = new AnimationState();
    public final AnimationState burrowAnimationState = new AnimationState();
    public final AnimationState eruptAnimationState = new AnimationState();
    public final AnimationState blindedAnimationState = new AnimationState();
    public final AnimationState deathAnimationState = new AnimationState();

    private final Vec3[] trail = new Vec3[TRAIL_LENGTH];
    private final float[] trailYaw = new float[TRAIL_LENGTH];
    private int trailWrite;
    private int trailCount;

    /** Per-victim gaze charge, server side only. Keyed by UUID so it survives reloads of the target. */
    private final Map<UUID, Integer> gazeCharge = new HashMap<>();
    private final Map<UUID, Integer> gazeTier = new HashMap<>();

    private int strikeCooldown;
    private int spitCooldown;
    private int sweepCooldown;
    private int burrowCooldown;
    private float headDamage;
    private int burrowedTicks;

    /**
     * One hit volume per body section.
     *
     * @param distance     how far back along the spine the section sits, in blocks;
     *                     negative is forward of the entity origin, i.e. the head
     * @param axis         height of the body's centre line above the feet, in blocks
     * @param damageScale  a blow to the head hurts more than one to the tail tip
     */
    private record PartAnchor(double distance, double axis, float width, float height,
                              float damageScale, boolean head) {}

    /**
     * Sizes are a little wider than the cubes they cover, to absorb the lateral
     * undulation the model adds on top of the path — otherwise a swinging tail
     * would sometimes be visually in front of a sword and still not take the hit.
     */
    private static final PartAnchor[] PART_ANCHORS = {
            new PartAnchor(-1.30D, 1.05D, 2.0F, 1.5F, 1.30F, true),
            new PartAnchor(0.60D, 0.75D, 1.4F, 1.2F, 1.00F, false),
            new PartAnchor(2.20D, 0.75D, 1.8F, 1.5F, 1.00F, false),
            new PartAnchor(3.80D, 0.75D, 2.0F, 1.6F, 1.00F, false),
            new PartAnchor(5.40D, 0.72D, 1.8F, 1.5F, 1.00F, false),
            new PartAnchor(7.40D, 0.62D, 1.4F, 1.2F, 0.85F, false),
            new PartAnchor(9.60D, 0.50D, 1.1F, 0.9F, 0.70F, false),
            new PartAnchor(12.00D, 0.32D, 0.8F, 0.7F, 0.70F, false),
    };

    private final BasiliskPart[] bodyParts;
    private final double[] partDistances = new double[PART_ANCHORS.length];
    private final Vec3[] partPoints = new Vec3[PART_ANCHORS.length];

    public BasiliskEntity(EntityType<? extends BasiliskEntity> type, Level level) {
        super(type, level);
        this.xpReward = 40;

        // Built here, in the constructor, on purpose: each part is an Entity and
        // takes the next id from the global counter, so the basilisk reserves
        // getId()+1 .. getId()+n and nothing else can claim them. setId below
        // then reproduces exactly that layout on the client.
        this.bodyParts = new BasiliskPart[PART_ANCHORS.length];
        for (int i = 0; i < PART_ANCHORS.length; i++) {
            PartAnchor anchor = PART_ANCHORS[i];
            this.bodyParts[i] = new BasiliskPart(this, anchor.width(), anchor.height(),
                    anchor.damageScale(), anchor.head());
        }
        // Entity ids are handed out by a counter shared across threads, so the
        // ones the parts just took are not guaranteed to be consecutive. Restate
        // the layout explicitly: the server keys its part lookup by these, and
        // the client rebuilds them from the parent id, so they have to agree or
        // every blow to the body lands on nothing.
        setId(getId());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95D);
    }

    public static boolean canSpawn(EntityType<BasiliskEntity> type, ServerLevelAccessor level,
                                   MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION_STATE, (byte) ActionState.NONE.ordinal());
        builder.define(DATA_STATE_TICK, 0);
        builder.define(DATA_BLINDED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BasiliskCombatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> !this.isBlinded() || isAudible(target)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, 10, true, false,
                target -> !this.isBlinded() || isAudible(target)));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, 10, true, false,
                target -> !this.isBlinded() || isAudible(target)));
    }

    /**
     * A blinded basilisk hunts by sound. Sprinting, swimming and generally
     * moving fast gives a victim away; creeping does not.
     */
    private static boolean isAudible(LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (target.isSprinting() || target.isSwimming()) {
            return true;
        }
        if (target.isCrouching()) {
            return false;
        }
        return target.getDeltaMovement().horizontalDistanceSqr() > 0.006D;
    }

    // --- state ----------------------------------------------------------------

    public ActionState getActionState() {
        return ActionState.fromByte(this.entityData.get(DATA_ACTION_STATE));
    }

    public int getStateTick() {
        return this.entityData.get(DATA_STATE_TICK);
    }

    public boolean isBlinded() {
        return this.entityData.get(DATA_BLINDED);
    }

    public boolean isPerformingAction() {
        return getActionState() != ActionState.NONE;
    }

    public boolean isBurrowing() {
        ActionState state = getActionState();
        return state == ActionState.BURROW_DOWN || state == ActionState.BURROW_UP;
    }

    public boolean isFullyBurrowed() {
        return getActionState() == ActionState.BURROWED;
    }

    private void setActionState(ActionState state) {
        this.entityData.set(DATA_ACTION_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_STATE_TICK, 0);
    }

    /** Ticks into the current state, smoothed across the render frame. */
    private float smoothedStateTick(float partialTick) {
        return getStateTick() + partialTick;
    }

    /** How far the front of the body is reared up, 0 to 1. */
    public float rearProgress(float partialTick) {
        ActionState state = getActionState();
        float t = smoothedStateTick(partialTick);
        if (state == ActionState.REAR) {
            return Mth.clamp(t / 10.0F, 0.0F, 1.0F);
        }
        if (state == ActionState.STRIKE) {
            // The rear collapses forward as the head lances out.
            return Mth.clamp(1.0F - t / (float) STRIKE_HIT_TICK, 0.0F, 1.0F);
        }
        if (state == ActionState.SPIT) {
            return Mth.clamp(t / 8.0F, 0.0F, 1.0F) * 0.45F;
        }
        return 0.0F;
    }

    /** How hard the back half is coiled for a sweep, 0 to 1. */
    public float coilProgress(float partialTick) {
        if (getActionState() != ActionState.TAIL_SWEEP) {
            return 0.0F;
        }
        float t = smoothedStateTick(partialTick);
        return Mth.clamp(Math.min(t / 8.0F, (SWEEP_TICKS - t) / 8.0F), 0.0F, 1.0F);
    }

    /** How much of the body is underground, 0 to 1. */
    public float burrowProgress(float partialTick) {
        ActionState state = getActionState();
        float t = smoothedStateTick(partialTick);
        if (state == ActionState.BURROW_DOWN) {
            return Mth.clamp(t / BURROW_DOWN_TICKS, 0.0F, 1.0F);
        }
        if (state == ActionState.BURROWED) {
            return 1.0F;
        }
        if (state == ActionState.BURROW_UP) {
            return Mth.clamp(1.0F - t / (float) BURROW_UP_TICKS, 0.0F, 1.0F);
        }
        return 0.0F;
    }

    // --- spine trail ----------------------------------------------------------

    private void recordTrail() {
        this.trail[this.trailWrite] = this.position();
        this.trailYaw[this.trailWrite] = this.yBodyRot;
        this.trailWrite = (this.trailWrite + 1) % TRAIL_LENGTH;
        if (this.trailCount < TRAIL_LENGTH) {
            this.trailCount++;
        }
    }

    /** {@code back} of 0 is the most recently recorded point. */
    private int trailIndex(int back) {
        return ((this.trailWrite - 1 - back) % TRAIL_LENGTH + TRAIL_LENGTH) % TRAIL_LENGTH;
    }

    /**
     * Walks back along the recorded path filling {@code out} with the world
     * points at each of the given arc-length distances, which must be ascending.
     * Distance rather than time, so the body neither bunches up when the
     * basilisk slows nor stretches when it sprints.
     *
     * <p>When the recorded path runs out — a fresh spawn, or a long spell of
     * standing still — the remainder is extended in a straight line along the
     * body yaw recorded at that point. Using the <em>recorded</em> yaw rather
     * than the current one is what lets the tail lag behind a turn in place.
     */
    public void samplePath(Vec3 head, double[] distances, Vec3[] out) {
        int wanted = 0;
        Vec3 previous = head;
        double travelled = 0.0D;
        float runoutYaw = this.yBodyRot;

        for (int back = 0; back < this.trailCount && wanted < distances.length; back++) {
            int index = trailIndex(back);
            Vec3 current = this.trail[index];
            if (current == null) {
                break;
            }
            double span = previous.distanceTo(current);
            while (wanted < distances.length && travelled + span >= distances[wanted]) {
                double t = span < 1.0E-5D ? 1.0D : (distances[wanted] - travelled) / span;
                out[wanted] = previous.lerp(current, t);
                wanted++;
            }
            travelled += span;
            previous = current;
            runoutYaw = this.trailYaw[index];
        }

        if (wanted < distances.length) {
            double yaw = runoutYaw * Mth.DEG_TO_RAD;
            // Straight back from the tail end: the reverse of the facing vector.
            Vec3 backwards = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
            for (; wanted < distances.length; wanted++) {
                out[wanted] = previous.add(backwards.scale(distances[wanted] - travelled));
            }
        }
    }

    /** Where the visible head is, roughly: forward of and above the entity origin. */
    public Vec3 getGazeOrigin() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        return this.position().add(forward.scale(1.3D)).add(0.0D, 1.05D, 0.0D);
    }

    // --- multipart hitboxes ---------------------------------------------------

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.minecraftforge.entity.PartEntity<?>[] getParts() {
        return this.bodyParts;
    }

    /**
     * Reproduces the server's id layout on the client. The parts were built in
     * the constructor immediately after the basilisk itself, so on the server
     * they already hold {@code getId() + 1 + i}; the client arrives here through
     * {@code recreateFromPacket} and must land on the same numbers or hits on
     * the body resolve to nothing.
     */
    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.bodyParts.length; i++) {
            this.bodyParts[i].setId(id + 1 + i);
        }
    }

    /** Everything is struck through a part, so the parent itself is not a target. */
    @Override
    public boolean isPickable() {
        return false;
    }

    /**
     * Lays the hit volumes along the same path the model draws the spine on, so
     * what you can see is what you can hit — including round a corner, where a
     * single box would be nowhere near the tail.
     */
    private void tickParts() {
        for (int i = 0; i < PART_ANCHORS.length; i++) {
            this.partDistances[i] = Math.max(0.0D, PART_ANCHORS[i].distance());
        }
        samplePath(this.position(), this.partDistances, this.partPoints);

        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        float rear = rearProgress(1.0F);
        boolean hidden = isFullyBurrowed();

        for (int i = 0; i < this.bodyParts.length; i++) {
            BasiliskPart part = this.bodyParts[i];
            PartAnchor anchor = PART_ANCHORS[i];
            part.setOldPosAndRot();

            if (hidden) {
                // Underground: fold the whole animal onto its own origin so no
                // stray box is left standing in the open.
                part.setPos(this.getX(), this.getY(), this.getZ());
                continue;
            }

            Vec3 point = this.partPoints[i];
            double x = point.x;
            double z = point.z;
            if (anchor.distance() < 0.0D) {
                // The head sits ahead of the origin, which the path cannot express.
                x -= forward.x * anchor.distance();
                z -= forward.z * anchor.distance();
            }

            // The same lift the model gives the front of the body when it rears,
            // so a reared head is struck where it actually is.
            double lift = rear * Math.max(0.0D, 1.0D - Math.max(0.0D, anchor.distance()) / 5.0D) * 1.6D;
            double centreY = point.y + anchor.axis() + lift;
            part.setPos(x, centreY - anchor.height() / 2.0D, z);
        }
    }

    /**
     * Damage routed in from a body section. Hits to the head count double toward
     * blinding it, which is the whole point of aiming there.
     */
    public boolean hurtPart(BasiliskPart part, DamageSource source, float amount) {
        if (isFullyBurrowed()) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && !isBlinded() && amount > 0.0F && part.isHead()) {
            this.headDamage += amount;
            if (this.headDamage >= BLIND_THRESHOLD) {
                setBlinded();
            }
        }
        return hurt;
    }

    // --- ticking --------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        recordTrail();
        // Both sides: the server resolves the hit, but the client has to have the
        // boxes in the right place to aim at them in the first place.
        tickParts();

        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (this.isBlinded()) {
                this.blindedAnimationState.startIfStopped(this.tickCount);
            } else {
                this.blindedAnimationState.stop();
            }
            if (this.deathTime > 0) {
                this.deathAnimationState.startIfStopped(this.tickCount);
            }
            spawnAmbientParticles();
            return;
        }

        this.entityData.set(DATA_STATE_TICK, getStateTick() + 1);
        if (this.strikeCooldown > 0) this.strikeCooldown--;
        if (this.spitCooldown > 0) this.spitCooldown--;
        if (this.sweepCooldown > 0) this.sweepCooldown--;
        if (this.burrowCooldown > 0) this.burrowCooldown--;

        advanceActionState();
        tickGaze();
    }

    private void spawnAmbientParticles() {
        if (isFullyBurrowed() || this.tickCount % 3 != 0) {
            return;
        }
        Vec3 mouth = getGazeOrigin();
        this.level().addParticle(ParticleTypes.SMOKE,
                mouth.x + (this.random.nextDouble() - 0.5D) * 0.8D,
                mouth.y - 0.35D,
                mouth.z + (this.random.nextDouble() - 0.5D) * 0.8D,
                0.0D, 0.01D, 0.0D);
    }

    private void advanceActionState() {
        ActionState state = getActionState();
        if (state == ActionState.NONE) {
            return;
        }
        int t = getStateTick();

        if (state == ActionState.REAR) {
            if (t >= REAR_TICKS) {
                setActionState(ActionState.STRIKE);
            }
        } else if (state == ActionState.STRIKE) {
            if (t == STRIKE_HIT_TICK) {
                doStrike();
            }
            if (t >= STRIKE_TICKS) {
                setActionState(ActionState.NONE);
            }
        } else if (state == ActionState.SPIT) {
            if (t == SPIT_RELEASE_TICK) {
                doSpit();
            }
            if (t >= SPIT_TICKS) {
                setActionState(ActionState.NONE);
            }
        } else if (state == ActionState.TAIL_SWEEP) {
            if (t == SWEEP_HIT_TICK) {
                doTailSweep();
            }
            if (t >= SWEEP_TICKS) {
                setActionState(ActionState.NONE);
            }
        } else if (state == ActionState.BURROW_DOWN) {
            if (t == 1) {
                burstGroundParticles(40);
                playActionSound(ModSounds.BASILISK_BURROW.get(), 1.2F, 0.9F);
            }
            if (t >= BURROW_DOWN_TICKS) {
                setActionState(ActionState.BURROWED);
                this.burrowedTicks = 0;
            }
        } else if (state == ActionState.BURROWED) {
            tickBurrowedTravel();
        } else if (state == ActionState.BURROW_UP) {
            if (t == BURROW_ERUPT_TICK) {
                doErupt();
            }
            if (t >= BURROW_UP_TICKS) {
                setActionState(ActionState.NONE);
                this.setNoGravity(false);
                this.noPhysics = false;
            }
        }
    }

    // --- attacks --------------------------------------------------------------

    private void beginRear() {
        setActionState(ActionState.REAR);
        this.strikeCooldown = STRIKE_COOLDOWN;
        playActionSound(ModSounds.BASILISK_HISS_THREAT.get(), 1.5F, 1.0F);
    }

    private void beginSpit() {
        setActionState(ActionState.SPIT);
        this.spitCooldown = SPIT_COOLDOWN;
    }

    private void beginTailSweep() {
        setActionState(ActionState.TAIL_SWEEP);
        this.sweepCooldown = SWEEP_COOLDOWN;
    }

    private void beginBurrow() {
        setActionState(ActionState.BURROW_DOWN);
        this.burrowCooldown = BURROW_COOLDOWN;
    }

    /** The lunge: the head lances out along the reared curve and bites. */
    private void doStrike() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        this.setDeltaMovement(this.getDeltaMovement().add(forward.scale(1.1D)).add(0.0D, 0.22D, 0.0D));
        this.hasImpulse = true;

        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.35F;
        AABB area = this.getBoundingBox().inflate(STRIKE_REACH, 2.5D, STRIKE_REACH);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area, this::isValidVictim)) {
            Vec3 flat = flatDirectionTo(victim);
            if (flat == null || flat.dot(new Vec3(forward.x, 0.0D, forward.z).normalize()) < 0.35D) {
                continue;
            }
            applyVenomousBite(victim, damage);
        }
        playActionSound(ModSounds.BASILISK_STRIKE.get(), 1.6F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                this.getX() + forward.x * 3.0D, this.getY() + 1.2D, this.getZ() + forward.z * 3.0D,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * Imperio possession (Spell Mastery Phase 6b): spit venom straight along the current look, on the
     * Basilisk's own spit cooldown (counted down in tick(), so it runs while the possessed AI is paused).
     */
    public void possessedAttack() {
        if (!(this.level() instanceof ServerLevel) || this.spitCooldown > 0 || isFullyBurrowed() || isBurrowing()) {
            return;
        }
        Vec3 from = getGazeOrigin();
        Vec3 look = this.getLookAngle();
        BasiliskVenomEntity venom = new BasiliskVenomEntity(this.level(), this);
        venom.setPos(from.x, from.y, from.z);
        venom.shoot(look.x, look.y + 0.12D, look.z, 1.05F, 1.0F);
        this.level().addFreshEntity(venom);
        playActionSound(ModSounds.BASILISK_SPIT.get(), 1.3F, 1.0F);
        this.spitCooldown = SPIT_COOLDOWN;
    }

    /** Ranged venom: an arcing glob that leaves a lingering pool where it lands. */
    private void doSpit() {
        LivingEntity target = this.getTarget();
        if (target == null || !(this.level() instanceof ServerLevel)) {
            return;
        }
        Vec3 from = getGazeOrigin();
        BasiliskVenomEntity venom = new BasiliskVenomEntity(this.level(), this);
        venom.setPos(from.x, from.y, from.z);

        double dx = target.getX() - from.x;
        double dy = target.getY(0.5D) - from.y;
        double dz = target.getZ() - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        venom.shoot(dx, dy + horizontal * 0.18D, dz, 1.05F, 3.0F);

        this.level().addFreshEntity(venom);
        playActionSound(ModSounds.BASILISK_SPIT.get(), 1.3F, 1.0F);
    }

    /** The tail whips a full arc through everything around the back half. */
    private void doTailSweep() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F;
        AABB area = this.getBoundingBox().inflate(SWEEP_RANGE, 2.0D, SWEEP_RANGE);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area, this::isValidVictim)) {
            Vec3 flat = flatDirectionTo(victim);
            if (flat == null || this.distanceToSqr(victim) > SWEEP_RANGE * SWEEP_RANGE) {
                continue;
            }
            victim.hurt(this.damageSources().mobAttack(this), damage);
            victim.push(flat.x * 1.7D, 0.55D, flat.z * 1.7D);
            victim.hurtMarked = true;
        }
        this.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY() + 0.6D, this.getZ(),
                4, 2.5D, 0.3D, 2.5D, 0.0D);
    }

    /** Bursts out of the ground, throwing everything standing over the exit. */
    private void doErupt() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        burstGroundParticles(60);
        playActionSound(ModSounds.BASILISK_ERUPT.get(), 1.7F, 1.0F);

        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB area = this.getBoundingBox().inflate(4.5D, 3.0D, 4.5D);
        for (LivingEntity victim : serverLevel.getEntitiesOfClass(LivingEntity.class, area, this::isValidVictim)) {
            victim.hurt(this.damageSources().mobAttack(this), damage);
            Vec3 away = flatDirectionTo(victim);
            double px = away == null ? 0.0D : away.x * 0.6D;
            double pz = away == null ? 0.0D : away.z * 0.6D;
            victim.push(px, 1.1D, pz);
            victim.hurtMarked = true;
        }
    }

    private void tickBurrowedTravel() {
        this.burrowedTicks++;
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);

        LivingEntity target = this.getTarget();
        if (target == null || this.burrowedTicks >= BURROWED_MAX_TICKS) {
            surfaceHere();
            return;
        }

        // Travel toward the victim a couple of blocks under their feet.
        Vec3 toTarget = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
        double horizontal = toTarget.horizontalDistance();
        double desiredY = target.getY() - 2.5D;
        double stepY = Mth.clamp(desiredY - this.getY(), -0.35D, 0.35D);

        if (horizontal < 1.8D) {
            surfaceHere();
            return;
        }
        Vec3 step = toTarget.normalize().scale(Math.min(0.42D, horizontal));
        this.setPos(this.getX() + step.x, this.getY() + stepY, this.getZ() + step.z);
        this.setYRot((float) (Mth.atan2(step.z, step.x) * Mth.RAD_TO_DEG) - 90.0F);
        this.yBodyRot = this.getYRot();

        if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 2 == 0) {
            BlockState below = this.level().getBlockState(this.blockPosition());
            if (!below.isAir()) {
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below),
                        this.getX(), this.getY() + 2.6D, this.getZ(), 6, 0.6D, 0.1D, 0.6D, 0.02D);
            }
        }
    }

    /** Climbs back to the first open space above and starts the eruption. */
    private void surfaceHere() {
        BlockPos.MutableBlockPos probe = this.blockPosition().mutable();
        for (int i = 0; i < 12; i++) {
            if (this.level().getBlockState(probe).isAir()
                    && this.level().getBlockState(probe.above()).isAir()) {
                break;
            }
            probe.move(0, 1, 0);
        }
        this.setPos(this.getX(), probe.getY(), this.getZ());
        this.setNoGravity(false);
        this.noPhysics = false;
        setActionState(ActionState.BURROW_UP);
    }

    private void burstGroundParticles(int count) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState ground = this.level().getBlockState(this.blockPosition().below());
        if (ground.isAir()) {
            return;
        }
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                this.getX(), this.getY() + 0.3D, this.getZ(), count, 1.6D, 0.4D, 1.6D, 0.28D);
    }

    private boolean isValidVictim(LivingEntity candidate) {
        return candidate != this && candidate.isAlive() && !(candidate instanceof BasiliskEntity);
    }

    /** Horizontal unit vector from this basilisk to the victim, or null if coincident. */
    private Vec3 flatDirectionTo(LivingEntity victim) {
        Vec3 to = new Vec3(victim.getX() - this.getX(), 0.0D, victim.getZ() - this.getZ());
        if (to.lengthSqr() < 1.0E-4D) {
            return null;
        }
        return to.normalize();
    }

    private void applyVenomousBite(LivingEntity victim, float damage) {
        if (damage > 0.0F) {
            victim.hurt(this.damageSources().mobAttack(this), damage);
        }
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 180, 1));
        victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    victim.getX(), victim.getY(0.75D), victim.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.02D);
        }
        if (victim instanceof ServerPlayer serverPlayer) {
            com.hogwartsmagic.event.ModEvents.interruptActiveCasting(serverPlayer);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            applyVenomousBite(living, 0.0F);
            playActionSound(ModSounds.BASILISK_BITE.get(), 1.2F, 1.0F);
            if (this.level() instanceof ServerLevel) {
                this.level().broadcastEntityEvent(this, EVENT_BITE);
            }
        }
        return hit;
    }

    // --- the gaze -------------------------------------------------------------

    private void tickGaze() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean gazeAvailable = !isBlinded() && !isFullyBurrowed() && !isBurrowing() && this.isAlive();
        // Creative and spectator players cannot be killed by the stare, so they must not be
        // petrified or shown the overlay either.
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(GAZE_RANGE),
                e -> isValidVictim(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));

        for (LivingEntity candidate : nearby) {
            UUID id = candidate.getUUID();
            int charge = this.gazeCharge.getOrDefault(id, 0);
            boolean meeting = gazeAvailable && isMeetingEyes(candidate);
            charge = meeting ? charge + 1 : charge - GAZE_DECAY_RATE;
            charge = Mth.clamp(charge, 0, GAZE_FULL_CHARGE);

            if (charge >= GAZE_FULL_CHARGE) {
                this.gazeCharge.remove(id);
                this.gazeTier.remove(id);
                petrifyAndKill(candidate);
                continue;
            }

            if (charge <= 0) {
                if (this.gazeCharge.containsKey(id)) {
                    this.gazeCharge.remove(id);
                    this.gazeTier.remove(id);
                    candidate.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
                }
                continue;
            }

            this.gazeCharge.put(id, charge);
            publishGazeTier(candidate, charge);
            // The heartbeat under the stare (spec: it rises in pitch as the charge climbs). Played
            // to the victim alone, once per clip length (1.6 s), so beats never overlap.
            if (candidate instanceof ServerPlayer victimPlayer && this.tickCount % 32 == 0) {
                int tier = Mth.clamp(charge * 5 / GAZE_FULL_CHARGE, 0, 4);
                victimPlayer.playNotifySound(ModSounds.BASILISK_GAZE_HEARTBEAT.get(), this.getSoundSource(),
                        0.6F + tier * 0.1F, 0.8F + tier * 0.1F);
            }
        }

        // Anything that walked out of range stops being tracked, and loses the
        // overlay with it.
        Iterator<Map.Entry<UUID, Integer>> it = this.gazeCharge.entrySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next().getKey();
            boolean stillNearby = false;
            for (LivingEntity candidate : nearby) {
                if (candidate.getUUID().equals(id)) {
                    stillNearby = true;
                    break;
                }
            }
            if (!stillNearby) {
                it.remove();
                this.gazeTier.remove(id);
            }
        }
    }

    /**
     * Publishes the charge to the client as an effect amplifier. Re-applying an
     * effect every tick is a packet every tick, so this only fires when the tier
     * moves or the instance is close to lapsing.
     */
    private void publishGazeTier(LivingEntity victim, int charge) {
        int tier = Mth.clamp(charge * 5 / GAZE_FULL_CHARGE, 0, 4);
        Integer previous = this.gazeTier.get(victim.getUUID());
        MobEffectInstance active = victim.getEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        boolean lapsing = active == null || active.getDuration() <= GAZE_EFFECT_DURATION - GAZE_EFFECT_REFRESH;
        if (previous != null && previous == tier && !lapsing) {
            return;
        }
        this.gazeTier.put(victim.getUUID(), tier);
        victim.addEffect(new MobEffectInstance(ExampleMod.BASILISK_GAZE.getHolder().get(),
                GAZE_EFFECT_DURATION, tier, false, false, true));
        if (tier >= 3) {
            // The last stretch drags: the victim is already going stiff.
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, tier - 2, false, false, false));
        }
    }

    private boolean isMeetingEyes(LivingEntity victim) {
        if (victim.isInvisible() || !victim.isAlive()) {
            return false;
        }
        if (this.distanceToSqr(victim) > GAZE_RANGE * GAZE_RANGE) {
            return false;
        }
        if (!this.hasLineOfSight(victim) || !victim.hasLineOfSight(this)) {
            return false;
        }
        Vec3 head = getGazeOrigin();
        Vec3 victimEyes = victim.getEyePosition();

        Vec3 toVictim = victimEyes.subtract(head);
        if (toVictim.lengthSqr() < 1.0E-6D) {
            return false;
        }
        Vec3 basiliskLook = Vec3.directionFromRotation(this.getXRot(), this.yHeadRot).normalize();
        if (basiliskLook.dot(toVictim.normalize()) < BASILISK_AIM_DOT) {
            return false;
        }

        Vec3 toHead = head.subtract(victimEyes);
        if (toHead.lengthSqr() < 1.0E-6D) {
            return false;
        }
        return victim.getViewVector(1.0F).normalize().dot(toHead.normalize()) >= VICTIM_AIM_DOT;
    }

    private void petrifyAndKill(LivingEntity victim) {
        victim.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        victim.addEffect(new MobEffectInstance(ExampleMod.BASILISK_PETRIFICATION.getHolder().get(),
                PETRIFY_DURATION, 0, false, true, true));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 2, false, false, false));

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    victim.getX(), victim.getY(0.5D), victim.getZ(), 24, 0.3D, 0.6D, 0.3D, 0.01D);
        }
        if (victim.isAlive()) {
            victim.hurt(this.damageSources().magic(), Float.MAX_VALUE);
        }
    }

    // --- blinding -------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFullyBurrowed() && !source.is(DamageTypes.GENERIC_KILL)) {
            // Underground and out of reach; suffocation in particular must not
            // apply, since it is travelling through solid rock by design.
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && !isBlinded() && amount > 0.0F) {
            accumulateHeadDamage(source, amount);
        }
        return hurt;
    }

    /** Only blows landed from in front count toward putting the eyes out. */
    private void accumulateHeadDamage(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker == null) {
            return;
        }
        Vec3 toAttacker = new Vec3(attacker.getX() - this.getX(), 0.0D, attacker.getZ() - this.getZ());
        if (toAttacker.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        if (toAttacker.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize()) < HEAD_HITZONE_DOT) {
            return;
        }

        this.headDamage += amount;
        if (this.headDamage >= BLIND_THRESHOLD) {
            setBlinded();
        }
    }

    private void setBlinded() {
        this.entityData.set(DATA_BLINDED, true);
        this.setTarget(null);
        for (UUID id : this.gazeCharge.keySet()) {
            Entity tracked = this.level() instanceof ServerLevel serverLevel ? serverLevel.getEntity(id) : null;
            if (tracked instanceof LivingEntity living) {
                living.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
            }
        }
        this.gazeCharge.clear();
        this.gazeTier.clear();
        playActionSound(ModSounds.BASILISK_HURT.get(), 1.8F, 0.75F);
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 head = getGazeOrigin();
            serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, head.x, head.y, head.z,
                    30, 0.6D, 0.4D, 0.6D, 0.1D);
        }
    }

    // --- plumbing -------------------------------------------------------------

    private static final byte EVENT_BITE = 61;

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_BITE) {
            this.biteAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!this.level().isClientSide || !DATA_ACTION_STATE.equals(key)) {
            return;
        }
        // Restart the clip matching the new state and stop the rest, so the
        // client never needs an entity event to stay in sync.
        ActionState state = getActionState();
        this.rearAnimationState.stop();
        this.strikeAnimationState.stop();
        this.spitAnimationState.stop();
        this.burrowAnimationState.stop();
        this.eruptAnimationState.stop();

        if (state == ActionState.REAR) {
            this.rearAnimationState.start(this.tickCount);
        } else if (state == ActionState.STRIKE) {
            this.strikeAnimationState.start(this.tickCount);
        } else if (state == ActionState.SPIT) {
            this.spitAnimationState.start(this.tickCount);
        } else if (state == ActionState.BURROW_DOWN) {
            this.burrowAnimationState.start(this.tickCount);
        } else if (state == ActionState.BURROW_UP) {
            this.eruptAnimationState.start(this.tickCount);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && !isFullyBurrowed()) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.level().isClientSide) {
            return;
        }
        for (UUID id : this.gazeCharge.keySet()) {
            if (this.level() instanceof ServerLevel serverLevel
                    && serverLevel.getEntity(id) instanceof LivingEntity living) {
                living.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
            }
        }
        this.gazeCharge.clear();
        this.gazeTier.clear();
    }

    /**
     * The tail trails far outside the collision box, so the render box has to
     * cover the whole animal or the body vanishes when the head leaves the frustum.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(20.0D);
    }

    // A body this long cannot pivot like a chicken; slow turning also keeps the
    // spine solver's straight-line fallback from snapping around.
    @Override
    public int getMaxHeadYRot() {
        return 45;
    }

    @Override
    public int getHeadRotSpeed() {
        return 6;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.random.nextInt(3) == 0
                ? ModSounds.BASILISK_AMBIENT2.get()
                : ModSounds.BASILISK_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.BASILISK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BASILISK_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.4F;
    }

    private void playActionSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound,
                this.getSoundSource(), volume, pitch);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Blinded", isBlinded());
        tag.putFloat("HeadDamage", this.headDamage);
        tag.putBoolean("Burrowed", isFullyBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_BLINDED, tag.getBoolean("Blinded"));
        this.headDamage = tag.getFloat("HeadDamage");
        // The action state is not saved, but NoGravity is. A basilisk saved while BURROWED would
        // otherwise reload in solid rock with no gravity and no way up, suffocating in place.
        // Resume it as burrowed with its travel time spent, so the next tick surfaces it through
        // the normal eruption.
        if (tag.getBoolean("Burrowed")) {
            this.entityData.set(DATA_ACTION_STATE, (byte) ActionState.BURROWED.ordinal());
            this.entityData.set(DATA_STATE_TICK, 0);
            this.burrowedTicks = BURROWED_MAX_TICKS;
            this.noPhysics = true;
        }
    }

    /**
     * Chooses between rear-and-strike, venom spit, tail sweep and burrowing,
     * gated by range and cooldown. Movement locks for the whole of every
     * committed action so the wind-up actually reads as a telegraph.
     */
    static class BasiliskCombatGoal extends Goal {
        private final BasiliskEntity basilisk;

        BasiliskCombatGoal(BasiliskEntity basilisk) {
            this.basilisk = basilisk;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.basilisk.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || this.basilisk.isPerformingAction();
        }

        @Override
        public void tick() {
            if (this.basilisk.isPerformingAction()) {
                if (!this.basilisk.isFullyBurrowed() && !this.basilisk.isBurrowing()) {
                    this.basilisk.getNavigation().stop();
                    Vec3 velocity = this.basilisk.getDeltaMovement();
                    this.basilisk.setDeltaMovement(velocity.x * 0.35D, velocity.y, velocity.z * 0.35D);
                }
                return;
            }

            LivingEntity target = this.basilisk.getTarget();
            if (target == null) {
                return;
            }
            this.basilisk.getLookControl().setLookAt(target, 30.0F, 30.0F);

            double distanceSq = this.basilisk.distanceToSqr(target);
            boolean visible = this.basilisk.hasLineOfSight(target);

            if (this.basilisk.sweepCooldown == 0 && distanceSq <= SWEEP_RANGE * SWEEP_RANGE
                    && this.basilisk.random.nextInt(30) == 0) {
                this.basilisk.beginTailSweep();
                return;
            }

            if (this.basilisk.strikeCooldown == 0 && visible
                    && distanceSq >= STRIKE_MIN_RANGE * STRIKE_MIN_RANGE
                    && distanceSq <= STRIKE_MAX_RANGE * STRIKE_MAX_RANGE) {
                this.basilisk.beginRear();
                return;
            }

            if (this.basilisk.spitCooldown == 0 && visible
                    && distanceSq >= SPIT_MIN_RANGE * SPIT_MIN_RANGE
                    && distanceSq <= SPIT_MAX_RANGE * SPIT_MAX_RANGE) {
                this.basilisk.beginSpit();
                return;
            }

            // Out of reach, or blocked: go under and come up beneath them.
            if (this.basilisk.burrowCooldown == 0
                    && (distanceSq >= BURROW_MIN_RANGE * BURROW_MIN_RANGE || !visible)
                    && this.basilisk.onGround()) {
                this.basilisk.beginBurrow();
            }
        }
    }
}
