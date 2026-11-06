package com.hogwartsmagic.entity;

import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.fx.CreatureFx;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The Hungarian Horntail — a boss-tier wyvern dragon modelled on the Goblet of Fire Triwizard dragon.
 *
 * <p>It moves the way the film's Horntail does: a low, heavy knuckle-walking crawl (wings double as
 * forelimbs), a snaking neck that tracks its prey, and a sweeping, tracking jet of fire rather than a
 * single puff. On the ground it bites, spins its spiked tail through everything around it and gusts
 * with its wings; when it wants distance it climbs, circles its prey, strafes it with fire from the
 * air and finishes with a dive.
 *
 * <p>Everything authoritative (movement, damage, fire, flight, the boss bar) lives server-side; the
 * client only reads the synced attack state + tick to drive animation. See the project's
 * server/client-safety rule.
 */
public class HungarianHorntailEntity extends Monster implements HorntailPoseSource, com.hogwartsmagic.fire.FireBreather {

    public enum AttackState {
        // NOTE: append new constants at the END — the ordinal is what gets synced to clients.
        NONE, ROAR, BITE, TAIL_WHIP, BREATH_WINDUP, BREATH, WING_GUST, TAKEOFF, FLY, DIVE, LAND, STAGGER,
        AIR_BREATH;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICK =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BOOLEAN);
    /** Which way the body spins during a tail sweep — the model needs it to trail the tail correctly. */
    /** The smoothed fire aim (unit), synched so clients draw the jet along the line the damage cone is cast on. */
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_BREATH_AIM =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_SWEEP_LEFT =
            SynchedEntityData.defineId(HungarianHorntailEntity.class, EntityDataSerializers.BOOLEAN);

    // --- State timings (ticks). "strike" frames are noted per-state. ---
    public static final int ROAR_DURATION = 54;
    public static final int BITE_DURATION = 20;
    public static final int BITE_STRIKE_TICK = 9;
    public static final int TAIL_WHIP_DURATION = 30;
    public static final int BREATH_WINDUP_DURATION = 22;
    public static final int BREATH_DURATION = 46;
    public static final int WING_GUST_DURATION = 26;
    public static final int WING_GUST_STRIKE_TICK = 11;
    public static final int TAKEOFF_DURATION = 30;
    public static final int DIVE_DURATION = 44;
    public static final int LAND_DURATION = 22;
    public static final int STAGGER_DURATION = 18;
    public static final int AIR_BREATH_DURATION = 52;
    public static final int AIR_BREATH_WINDUP = 12;

    /** Total time spent airborne (FLY + AIR_BREATH) before the dragon commits to a dive. */
    private static final int FLIGHT_BUDGET = 150;

    // --- Tail sweep. The dragon pivots its whole body and the tail trails behind it, so the arc the
    // model draws and the arc that deals damage are derived from the same functions below. ---
    public static final float TAIL_WIND_END = 0.28F;   // fraction of the state spent winding up
    public static final float TAIL_SWEEP_END = 0.72F;  // fraction at which the sweep finishes
    /** Total body yaw (radians) the dragon spins through during the sweep. */
    public static final float TAIL_BODY_SPIN = 3.5F;
    /** How far (radians) the tail trails behind the body at the peak of the sweep. */
    public static final float TAIL_TRAIL = 1.15F;

    private static final double BITE_RANGE = 6.0D;
    private static final double BITE_COS = 0.45D;      // ~63 degree half-cone in front of the snout
    private static final double TAIL_INNER = 2.0D;
    private static final double TAIL_RANGE = 8.0D;
    private static final double TAIL_ARC_HALF = 0.55D; // radians of tail thickness for hit detection
    private static final double WING_GUST_RANGE = 7.0D;
    private static final double BREATH_RANGE = 18.0D;
    /** Radius of the flame cone at the mouth, and how fast it widens per block travelled. */
    private static final double BREATH_CONE_BASE = 0.9D;
    private static final double BREATH_CONE_GROWTH = 0.13D;
    private static final float RAGE_HP_FRACTION = 0.45F;
    private static final double HOVER_HEIGHT = 12.0D;
    private static final double CIRCLE_RADIUS = 14.0D;

    private boolean hasRoared;
    private int actionCooldown;
    private int struckThisState;
    private final Set<Integer> hitThisState = new HashSet<>();
    private double hoverTargetY;
    private int flightTicks;
    private double circlePhase;
    private int circleDir = 1;
    private int airBreathCooldown;
    /** Smoothed aim of the fire jet, so the flame sweeps across the ground instead of snapping. */
    private Vec3 breathAim = new Vec3(0.0D, 0.0D, 1.0D);
    /** Yaw the dragon spun to when the current tail sweep started. */
    private float sweepStartYaw;
    private AttackState lastGroundAttack = AttackState.NONE;
    private int repeatedAttacks;


    public HungarianHorntailEntity(EntityType<? extends HungarianHorntailEntity> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.moveControl = new net.minecraft.world.entity.ai.control.MoveControl(this);
    }

    /**
     * Persistence is decided by <i>how</i> the dragon got here, not by what it is.
     *
     * <p>A Horntail you summoned — by command, by spawn egg, by any deliberate act — is the centre of
     * whatever you are doing, so it is pinned in place and never despawns. A Horntail that generated on
     * its own in {@code hogwartsmagic:horntail_peaks} is scenery, and scenery has to be allowed to go
     * away again: a boss that both spawns naturally and never despawns would silently accumulate one
     * more 320-HP dragon per visit until the region was unplayable.
     */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData groupData) {
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            this.setPersistenceRequired();
        }
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    /**
     * Natural-spawn rule for the Horntail Peaks: high, open crags only. The height and sky checks are
     * what keep the dragon out of the caves and gullies under the range — without them a MONSTER-category
     * mob would happily pick the first dark pocket of rock it found. Light level is deliberately not
     * checked; dragons are not afraid of daylight.
     */
    public static boolean canSpawn(EntityType<HungarianHorntailEntity> type, ServerLevelAccessor level,
                                   MobSpawnType spawnType, BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        // The floor was 120 when Horntail Peaks only claimed vanilla's peaks erosion band. Widening
        // that band to take the mountains generally -- which is what made the biome findable at all --
        // left most of its ground below 120, so the dragon had a habitat it could not legally stand
        // in. Open sky is what actually keeps it out of the caves and gullies; the height floor is
        // only there to stop it nesting on a hill.
        return pos.getY() >= 96
                && level.canSeeSky(pos)
                && checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.STEP_HEIGHT, 1.2D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_STATE, (byte) AttackState.NONE.ordinal());
        builder.define(DATA_STATE_TICK, 0);
        builder.define(DATA_RAGING, false);
        builder.define(DATA_FLYING, false);
        builder.define(DATA_SWEEP_LEFT, false);
        builder.define(DATA_BREATH_AIM, new org.joml.Vector3f(com.hogwartsmagic.fire.BreathAim.DEFAULT));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new HorntailCombatGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5D, 0.7F));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // A heavy animal does not snap its head around; it swings it.
    @Override
    public int getMaxHeadYRot() {
        return 60;
    }

    @Override
    public int getHeadRotSpeed() {
        return 9;
    }

    // -------------------------------------------------------------------------------------------
    // Synced state accessors
    // -------------------------------------------------------------------------------------------

    public AttackState getAttackState() {
        return AttackState.fromByte(this.entityData.get(DATA_ATTACK_STATE));
    }

    public int getStateTick() {
        return this.entityData.get(DATA_STATE_TICK);
    }

    public boolean isRaging() {
        return this.entityData.get(DATA_RAGING);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public boolean isSweepingLeft() {
        return this.entityData.get(DATA_SWEEP_LEFT);
    }

    /** The unit direction the fire jet is cast along now (the server's smoothed aim, synched), {x, y, z}. */
    @Override
    public double[] breathAimSynced() {
        return com.hogwartsmagic.fire.BreathAim.decode(this.entityData.get(DATA_BREATH_AIM));
    }

    /** The fire leaves her mouth through a ground BREATH, and an AIR_BREATH once its windup is over. */
    @Override
    public int breathAge() {
        AttackState st = getAttackState();
        int tick = getStateTick();
        if (st == AttackState.BREATH) return tick;
        if (st == AttackState.AIR_BREATH && tick >= AIR_BREATH_WINDUP) return tick - AIR_BREATH_WINDUP;
        return -1;
    }

    @Override
    public float breathDuration() {
        return getAttackState() == AttackState.AIR_BREATH ? AIR_BREATH_DURATION - AIR_BREATH_WINDUP : BREATH_DURATION;
    }

    @Override
    public float breathCharge() {
        AttackState st = getAttackState();
        int tick = getStateTick();
        if (st == AttackState.BREATH_WINDUP) return tick / (float) BREATH_WINDUP_DURATION;
        if (st == AttackState.AIR_BREATH && tick < AIR_BREATH_WINDUP) return tick / (float) AIR_BREATH_WINDUP;
        return -1;
    }

    /** Publishes the current {@link #breathAim} to the clients (a no-op while it has not changed). */
    private void syncBreathAim() {
        this.entityData.set(DATA_BREATH_AIM, com.hogwartsmagic.fire.BreathAim.encode(this.breathAim));
    }

    // -------------------------------------------------------------------------------------------
    // Model hooks: what the model asks of the entity each frame (HorntailFlightPose holds the wild
    // dragon's answers; the memory dragon overrides them from its scene).
    // -------------------------------------------------------------------------------------------

    /** The state's age the overlays animate by, in ticks: the synced whole ticks. */
    public float animStateTick() {
        return getStateTick();
    }

    /** The flight beat, -1 (wings up) to 1 (wings down), for a model beat rate in rad/tick. */
    public float wingBeat(float ageInTicks, float rate) {
        return HorntailFlightPose.wingBeat(ageInTicks, rate);
    }

    /** The flight beat's amplitude, given the model's own for the state. */
    public float wingAmp(float amp) {
        return HorntailFlightPose.wingAmp(amp);
    }

    /** The takeoff overlay's beat at its progress p (0 to 1). */
    public float takeoffBeat(float p) {
        return HorntailFlightPose.takeoffBeat(p);
    }

    /** The body's bank in flight (radians, model zRot). */
    public float flightBank() {
        return HorntailFlightPose.flightBank(this.getYRot(), this.yRotO);
    }

    /** The body's pitch in flight (radians, model xRot), given the beat. */
    public float flightPitch(float beat) {
        return HorntailFlightPose.flightPitch(this.getDeltaMovement().y, beat);
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_STATE_TICK, 0);
        this.struckThisState = 0;
        this.hitThisState.clear();
    }

    private boolean isAerial() {
        AttackState s = getAttackState();
        return s == AttackState.TAKEOFF || s == AttackState.FLY || s == AttackState.DIVE
                || s == AttackState.LAND || s == AttackState.AIR_BREATH;
    }

    private boolean isBusy() {
        AttackState s = getAttackState();
        return s != AttackState.NONE && s != AttackState.STAGGER;
    }

    /** Committed ground actions lock movement; aerial states drive their own movement. */
    private boolean groundLocked() {
        AttackState s = getAttackState();
        return s == AttackState.ROAR || s == AttackState.BITE || s == AttackState.TAIL_WHIP
                || s == AttackState.BREATH_WINDUP || s == AttackState.BREATH || s == AttackState.WING_GUST
                || s == AttackState.STAGGER;
    }

    // -------------------------------------------------------------------------------------------
    // Tick / state machine
    // -------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        // While airborne we drive velocity ourselves; clear any ground path each tick first so the
        // stroll/move-control goals can't fight the flight (this runs before super's AI step).
        if (!this.level().isClientSide && isAerial()) {
            this.getNavigation().stop();
        }
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        // A dead dragon stops fighting: no dive impact or fire from the corpse, and one that
        // died in the air falls instead of hanging there through its death animation.
        if (this.isDeadOrDying()) {
            if (this.isNoGravity() || isFlying()) {
                this.setNoGravity(false);
                this.entityData.set(DATA_FLYING, false);
            }
            if (getAttackState() != AttackState.NONE) {
                setAttackState(AttackState.NONE);
            }
            return;
        }

        AttackState state = getAttackState();
        int t = getStateTick();
        // Idle costs nothing: no state tick means no synched-data traffic (setAttackState resets it to 0
        // whenever a state starts, so the first tick of any state still sees t == 0).
        if (state != AttackState.NONE) {
            this.entityData.set(DATA_STATE_TICK, t + 1);
        }

        if (this.actionCooldown > 0) this.actionCooldown--;
        if (this.airBreathCooldown > 0) this.airBreathCooldown--;

        // Safety net: vanilla persists NoGravity but not the attack state, so a dragon saved in
        // mid-air would reload hovering forever. Anything not in an aerial state falls.
        if (!isAerial() && (this.isNoGravity() || isFlying())) {
            this.setNoGravity(false);
            this.entityData.set(DATA_FLYING, false);
        }

        // Rage threshold (one-way latch): faster, more aggressive, more fire.
        if (!isRaging() && this.getHealth() <= this.getMaxHealth() * RAGE_HP_FRACTION) {
            this.entityData.set(DATA_RAGING, true);
            this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_ROAR.get(), 4.0F, 0.85F);
            if (this.level() instanceof ServerLevel sl) {
                // The enraged roar: a long low rumble under the ground, not a puff of flame.
                CreatureFx.rumble(sl, this.position(), 1.6F, 44.0F, 46);
                CreatureFx.slam(sl, this.position(), 8.0F, 0.7F);
            }
        }

        driveState(state, t);
        emitAmbientBreathSmoke();
        footfalls();

        if (groundLocked()) {
            this.getNavigation().stop();
            Vec3 v = this.getDeltaMovement();
            // Ease to a stop instead of slamming to zero — a 4-tonne animal has momentum.
            this.setDeltaMovement(v.x * 0.62D, v.y, v.z * 0.62D);
        }
    }

    /** Nostril smoke while idle — the Horntail is always smouldering in the film. */
    private void emitAmbientBreathSmoke() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        if (isAerial()) return;
        int period = isRaging() ? 4 : 9;
        if (this.tickCount % period != 0) return;
        Vec3 mouth = mouthPos();
        Vec3 dir = this.getLookAngle();
        sl.sendParticles(ParticleTypes.SMOKE,
                mouth.x, mouth.y, mouth.z, 0,
                dir.x * 0.06D, 0.03D, dir.z * 0.06D, 1.0D);
        if (isRaging() && this.random.nextInt(3) == 0) {
            sl.sendParticles(ParticleTypes.SMALL_FLAME,
                    mouth.x, mouth.y, mouth.z, 0,
                    dir.x * 0.08D, 0.02D, dir.z * 0.08D, 1.0D);
        }
    }

    /** Ground shake + dust when a foot plants, so the walk reads as heavy. */
    private void footfalls() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        if (!this.onGround() || isAerial() || groundLocked()) return;
        if (this.getDeltaMovement().horizontalDistanceSqr() < 0.0025D) return;
        if (this.tickCount % 11 != 0) return;
        this.playSound(SoundEvents.RAVAGER_STEP, 1.1F, 0.45F);
        CreatureFx.slam(sl, this.position(), 2.4F, 0.3F, 0.3F, 18.0F, 7);
    }

    private void driveState(AttackState state, int t) {
        if (state == AttackState.ROAR) {
            tickRoar(t);
        } else if (state == AttackState.BITE) {
            tickBite(t);
        } else if (state == AttackState.TAIL_WHIP) {
            tickTailWhip(t);
        } else if (state == AttackState.BREATH_WINDUP) {
            tickBreathWindup(t);
        } else if (state == AttackState.BREATH) {
            tickBreath(t);
        } else if (state == AttackState.WING_GUST) {
            tickWingGust(t);
        } else if (state == AttackState.TAKEOFF) {
            tickTakeoff(t);
        } else if (state == AttackState.FLY) {
            tickFly(t);
        } else if (state == AttackState.AIR_BREATH) {
            tickAirBreath(t);
        } else if (state == AttackState.DIVE) {
            tickDive(t);
        } else if (state == AttackState.LAND) {
            tickLand(t);
        } else if (state == AttackState.STAGGER) {
            if (t >= STAGGER_DURATION) setAttackState(AttackState.NONE);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Ground attacks
    // -------------------------------------------------------------------------------------------

    private void tickRoar(int t) {
        if (t == 8) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_ROAR.get(), 6.0F, 0.75F);
        // The roar shoves loose dust and makes the ground shudder — pure theatre, no damage.
        if (this.level() instanceof ServerLevel sl && t == 8) {
            CreatureFx.rumble(sl, this.position(), 1.6F, 44.0F, 44);
            CreatureFx.slam(sl, this.position(), 8.0F, 0.7F);
        }
        LivingEntity target = this.getTarget();
        if (target != null) faceTowards(target, 5.0F);
        if (t >= ROAR_DURATION) setAttackState(AttackState.NONE);
    }

    private void tickBite(int t) {
        LivingEntity target = this.getTarget();
        // Keep tracking through the wind-up so the snap actually lands where the head is pointing.
        if (target != null && t < BITE_STRIKE_TICK) faceTowards(target, 12.0F);
        if (t == 2) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_BITE.get(), 1.2F, 1.4F);
        // Lunge: the whole body drives forward into the bite instead of the head floating out.
        if (t >= 4 && t <= BITE_STRIKE_TICK) {
            Vec3 fwd = Vec3.directionFromRotation(0.0F, this.getYRot());
            double push = isRaging() ? 0.26D : 0.19D;
            this.setDeltaMovement(this.getDeltaMovement().add(fwd.x * push, 0.0D, fwd.z * push));
        }
        if (t == BITE_STRIKE_TICK && struckThisState == 0) {
            struckThisState = 1;
            doBiteImpact();
        }
        if (t >= BITE_DURATION) setAttackState(AttackState.NONE);
    }

    private void doBiteImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.getYRot());
        Vec3 flatFwd = new Vec3(fwd.x, 0.0D, fwd.z).normalize();
        Vec3 jaw = mouthPos();
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.2F : 1.0F);
        boolean connected = false;
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(BITE_RANGE, 3.0D, BITE_RANGE), e -> e != this && e.isAlive())) {
            Vec3 to = victim.getBoundingBox().getCenter().subtract(this.position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.lengthSqr() > BITE_RANGE * BITE_RANGE) continue;
            // Only what is actually in front of the snout gets bitten.
            if (flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(flatFwd) < BITE_COS) continue;
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 away = flat.lengthSqr() > 1.0E-4D ? flat.normalize() : flatFwd;
            victim.push(away.x * 0.9D, 0.38D, away.z * 0.9D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
            connected = true;
        }
        this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_BITE.get(), 1.5F, 1.3F);
        if (connected) {
            sl.sendParticles(ParticleTypes.CRIT, jaw.x, jaw.y, jaw.z, 14, 0.5D, 0.5D, 0.5D, 0.15D);
        } else {
            // A missed snap still cracks shut — teeth clash, no crit sparks.
            sl.sendParticles(ParticleTypes.SMOKE, jaw.x, jaw.y, jaw.z, 6, 0.3D, 0.2D, 0.3D, 0.02D);
        }
    }

    private void beginTailWhipInternal() {
        setAttackState(AttackState.TAIL_WHIP);
        LivingEntity target = this.getTarget();
        // Spin toward whichever side puts the tail through the target soonest.
        boolean left = true;
        if (target != null) {
            Vec3 to = target.position().subtract(this.position());
            Vec3 side = leftVector();
            left = (to.x * side.x + to.z * side.z) >= 0.0D;
        } else {
            left = this.random.nextBoolean();
        }
        this.entityData.set(DATA_SWEEP_LEFT, left);
        this.sweepStartYaw = this.getYRot();
    }

    /** Progress of the body spin (0..1 of TAIL_BODY_SPIN) at a given state fraction. */
    public static float tailSpinProgress(float p) {
        if (p <= TAIL_WIND_END) return 0.0F;
        if (p >= TAIL_SWEEP_END) return 1.0F;
        float w = (p - TAIL_WIND_END) / (TAIL_SWEEP_END - TAIL_WIND_END);
        // Ease in, hold speed, ease out — a whip cracks, it does not rotate linearly.
        return w * w * (3.0F - 2.0F * w);
    }

    /**
     * How far the tail lags behind the body (radians, positive = trailing) at a given state fraction.
     * The model and the hit detection both read this so the damage arc is exactly where the spikes are.
     */
    public static float tailTrail(float p) {
        if (p < TAIL_WIND_END) {
            // Coil: the tail winds the other way before the spin starts.
            return -TAIL_TRAIL * 0.55F * (p / TAIL_WIND_END);
        }
        if (p < TAIL_SWEEP_END) {
            float w = (p - TAIL_WIND_END) / (TAIL_SWEEP_END - TAIL_WIND_END);
            return Mth.sin(w * Mth.PI) * TAIL_TRAIL - TAIL_TRAIL * 0.55F * (1.0F - w);
        }
        float w = (p - TAIL_SWEEP_END) / (1.0F - TAIL_SWEEP_END);
        return TAIL_TRAIL * 0.25F * (1.0F - w);
    }

    private void tickTailWhip(int t) {
        float p = (float) t / TAIL_WHIP_DURATION;
        boolean left = isSweepingLeft();
        float sign = left ? 1.0F : -1.0F;

        if (t == 4) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_TAIL_WHIP.get(), 1.6F, 0.7F);

        // Drive the body yaw directly — the spin IS the attack.
        float spin = tailSpinProgress(p) * TAIL_BODY_SPIN * Mth.RAD_TO_DEG * sign;
        this.setYRot(this.sweepStartYaw + spin);
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();

        // During the sweep, damage whatever the spikes are actually passing through this tick.
        if (p >= TAIL_WIND_END && p <= TAIL_SWEEP_END) {
            doTailSweepTick(p, sign);
        }
        if (t >= TAIL_WHIP_DURATION) {
            setAttackState(AttackState.NONE);
            this.actionCooldown = isRaging() ? 10 : 18;
        }
    }

    private void doTailSweepTick(float p, float sign) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        // World direction the tail tip currently points: straight back, rotated by the trail angle.
        Vec3 back = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(-1.0D);
        Vec3 leftV = leftVector();
        double trail = -tailTrail(p) * sign; // trailing means rotated opposite the spin direction
        Vec3 tailDir = back.scale(Math.cos(trail)).add(leftV.scale(Math.sin(trail))).normalize();

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.35F : 1.15F);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(TAIL_RANGE, 3.0D, TAIL_RANGE), e -> e != this && e.isAlive())) {
            if (this.hitThisState.contains(victim.getId())) continue;
            Vec3 to = victim.position().subtract(this.position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double d = flat.length();
            if (d < TAIL_INNER || d > TAIL_RANGE) continue;
            if (flat.normalize().dot(tailDir) < Math.cos(TAIL_ARC_HALF)) continue;
            this.hitThisState.add(victim.getId());
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 push = flat.normalize();
            victim.push(push.x * 2.4D, 0.65D, push.z * 2.4D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
            this.playSound(SoundEvents.PLAYER_ATTACK_CRIT, 1.4F, 0.6F);
        }

        // The spikes drag through the ground: dust and chunks off the outer end of the arc, the view shuddering.
        int sweepTick = this.getStateTick();
        if (sweepTick % 3 == 0) {
            Vec3 pnt = this.position().add(tailDir.scale(TAIL_RANGE * 0.85D));
            CreatureFx.slam(sl, pnt, 2.4F, 0.4F);
        }
        if (sweepTick % 6 == 0) {
            Fx.shake(sl, this.position(), 1.1F, 24.0F, 12);
        }
    }

    private void tickWingGust(int t) {
        LivingEntity target = this.getTarget();
        if (target != null && t < WING_GUST_STRIKE_TICK) faceTowards(target, 10.0F);
        if (t == 4) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 3.0F, 0.8F);
        if (t == WING_GUST_STRIKE_TICK && struckThisState == 0) {
            struckThisState = 1;
            doWingGust();
        }
        if (t >= WING_GUST_DURATION) setAttackState(AttackState.NONE);
    }

    private void doWingGust() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        Vec3 flatFwd = new Vec3(forward.x, 0.0D, forward.z).normalize();
        AABB area = this.getBoundingBox().inflate(WING_GUST_RANGE, 3.0D, WING_GUST_RANGE);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive())) {
            Vec3 to = victim.position().subtract(this.position());
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            if (toFlat.lengthSqr() < 1.0E-4D || toFlat.lengthSqr() > WING_GUST_RANGE * WING_GUST_RANGE) continue;
            if (toFlat.normalize().dot(flatFwd) < 0.1D) continue;
            victim.hurt(this.damageSources().mobAttack(this), 6.0F);
            Vec3 push = toFlat.normalize();
            victim.push(push.x * 2.8D, 0.9D, push.z * 2.8D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }
        this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 4.0F, 1.0F);
        // A wall of dust rolling away from the wings, and the thump of the beat in the ground.
        CreatureFx.slam(sl, this.position().add(flatFwd.scale(3.0D)), (float) (WING_GUST_RANGE * 0.7D), 0.9F,
                0.9F, 26.0F, 12);
    }

    // -------------------------------------------------------------------------------------------
    // Fire breath
    // -------------------------------------------------------------------------------------------

    private void tickBreathWindup(int t) {
        LivingEntity target = this.getTarget();
        if (target != null) faceTowards(target, 9.0F);
        if (t == 0) {
            this.breathAim = this.getLookAngle();
            if (target != null) this.breathAim = aimAt(target);
            syncBreathAim();
        }
        if (t == 2) this.playSound(SoundEvents.FIRECHARGE_USE, 2.5F, 0.5F);
        if (this.level() instanceof ServerLevel sl) {
            Vec3 mouth = mouthPos();
            float charge = (float) t / BREATH_WINDUP_DURATION;
            // Embers are pulled INTO the mouth as it inhales — the glow builds up behind the teeth.
            int n = 2 + Math.round(charge * 6.0F);
            for (int i = 0; i < n; i++) {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double r = 1.0D + this.random.nextDouble() * 1.2D;
                Vec3 off = new Vec3(Math.cos(a) * r, (this.random.nextDouble() - 0.5D) * r, Math.sin(a) * r);
                Vec3 from = mouth.add(off);
                Vec3 vel = off.normalize().scale(-0.22D);
                sl.sendParticles(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, 0, vel.x, vel.y, vel.z, 1.0D);
            }
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, 1, 0.2D, 0.2D, 0.2D, 0.01D);
        }
        if (t >= BREATH_WINDUP_DURATION) setAttackState(AttackState.BREATH);
    }

    private void tickBreath(int t) {
        if (t == 1) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FIRE_BREATH.get(), 4.0F, 0.6F);
        if (t % 11 == 0) this.playSound(SoundEvents.FIRE_AMBIENT, 3.0F, 0.5F);
        LivingEntity target = this.getTarget();
        // The whole animal turns to follow, slowly — that is what makes the jet visibly sweep.
        if (target != null) faceTowards(target, isRaging() ? 5.5F : 3.5F);
        breatheFire(t, target, false);
        if (t >= BREATH_DURATION) {
            setAttackState(AttackState.NONE);
            this.actionCooldown = isRaging() ? 16 : 30;
        }
    }

    /**
     * One tick of fire. The jet is a real cone of moving particles emitted from the mouth, clipped to
     * whatever block it hits, so it stops at walls instead of spraying through them.
     */
    private void breatheFire(int t, LivingEntity target, boolean airborne) {
        if (!(this.level() instanceof ServerLevel sl)) return;

        // Ease the aim toward the target so the flame sweeps across the ground rather than snapping.
        if (target != null) {
            Vec3 want = aimAt(target);
            double turn = airborne ? 0.16D : 0.11D;
            this.breathAim = this.breathAim.add(want.subtract(this.breathAim).scale(turn)).normalize();
        }
        syncBreathAim();
        Vec3 dir = this.breathAim;
        // Point the head where the fire actually goes, THEN read the muzzle position off it, so the
        // jet leaves the snout the client is drawing rather than trailing it by a tick.
        this.setXRot((float) (-Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)) * Mth.RAD_TO_DEG));
        this.yHeadRot = (float) (Math.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
        Vec3 mouth = mouthPos();

        double jet = jetLength(mouth, dir);
        Vec3 end = mouth.add(dir.scale(jet));

        // The flame itself is drawn on the clients (client/pensieve/DragonFireRenderer, the same fire as the First
        // Task memory) from the synched aim and the model snout; only the damage and the ignition live here.

        // Damage on a slower cadence — LivingEntity invulnerability makes per-tick chip damage useless.
        if (t % 8 == 4) applyBreathDamage(sl, mouth, dir, jet);

        // Scorch the ground it is pointed at.
        if (this.random.nextFloat() < (isRaging() ? 0.14F : 0.08F)) {
            igniteAt(sl, end);
        }
    }

    /** Distance the jet travels before something solid stops it. */
    private double jetLength(Vec3 mouth, Vec3 dir) {
        Vec3 far = mouth.add(dir.scale(BREATH_RANGE));
        HitResult hit = this.level().clip(new ClipContext(mouth, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return BREATH_RANGE;
        return Math.max(1.5D, hit.getLocation().distanceTo(mouth));
    }

    private void applyBreathDamage(ServerLevel sl, Vec3 mouth, Vec3 dir, double jet) {
        float dmg = isRaging() ? 9.0F : 6.0F;
        AABB scan = new AABB(mouth, mouth.add(dir.scale(jet))).inflate(BREATH_CONE_BASE + jet * BREATH_CONE_GROWTH);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, scan, e -> e != this && e.isAlive())) {
            Vec3 to = victim.getBoundingBox().getCenter().subtract(mouth);
            double along = to.dot(dir);
            if (along < 0.5D || along > jet) continue;
            // Perpendicular distance from the jet axis, against a cone that widens with distance.
            double perp = to.subtract(dir.scale(along)).length();
            if (perp > BREATH_CONE_BASE + along * BREATH_CONE_GROWTH) continue;
            victim.hurt(this.damageSources().onFire(), dmg);
            victim.igniteForSeconds(isRaging() ? 8 : 5);
            interruptIfCaster(victim);
        }
    }

    private void igniteAt(ServerLevel sl, Vec3 near) {
        if (!sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        BlockPos pos = BlockPos.containing(near.x, near.y, near.z);
        for (BlockPos candidate : new BlockPos[]{pos, pos.above(), pos.below()}) {
            if (sl.isEmptyBlock(candidate) && !sl.isEmptyBlock(candidate.below())
                    && sl.getBlockState(candidate.below()).isSolidRender(sl, candidate.below())
                    && com.hogwartsmagic.spell.TerrainRules.canPlace(sl, candidate, this)) {
                sl.setBlock(candidate, Blocks.FIRE.defaultBlockState(), 11);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Flight
    // -------------------------------------------------------------------------------------------

    private void beginTakeoff() {
        setAttackState(AttackState.TAKEOFF);
        this.entityData.set(DATA_FLYING, true);
        this.setNoGravity(true);
        this.getNavigation().stop();
        this.hoverTargetY = groundBelowY() + HOVER_HEIGHT;
        this.flightTicks = 0;
        this.circleDir = this.random.nextBoolean() ? 1 : -1;
        LivingEntity target = this.getTarget();
        if (target != null) {
            this.circlePhase = Math.atan2(this.getZ() - target.getZ(), this.getX() - target.getX());
        }
        this.airBreathCooldown = 20;
        this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 5.0F, 0.7F);
    }

    private void tickTakeoff(int t) {
        // Crouch, then explode upward on the third beat — not a constant elevator ride.
        double thrust;
        if (t < 6) {
            thrust = -0.04D;                       // compress the legs
        } else {
            double climb = (this.hoverTargetY - this.getY()) * 0.22D;
            thrust = Mth.clamp(climb, 0.18D, 0.72D);
            // Pulse with the wingbeats so the rise is not perfectly smooth.
            thrust *= 0.75D + 0.25D * Math.max(0.0D, Math.sin((t - 6) * 0.55D));
        }
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x * 0.86D, thrust, v.z * 0.86D);
        if (t == 7 || t == 15 || t == 23) {
            this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 4.0F, 0.85F);
            if (this.level() instanceof ServerLevel sl && this.getY() < this.hoverTargetY - 6.0D) {
                // The downdraught of each wingbeat as it heaves itself up.
                CreatureFx.slam(sl, this.position(), 6.0F, 0.6F, 0.8F, 22.0F, 10);
            }
        }
        LivingEntity target = this.getTarget();
        if (target != null) this.getLookControl().setLookAt(target, 20.0F, 20.0F);
        if (this.getY() >= this.hoverTargetY - 1.5D || t >= TAKEOFF_DURATION) {
            setAttackState(AttackState.FLY);
        }
    }

    private void tickFly(int t) {
        this.flightTicks++;
        LivingEntity target = this.getTarget();
        if (target == null) {
            if (t >= 25) beginLand();
            return;
        }

        // Circle the prey at altitude instead of parking overhead.
        double speedScale = isRaging() ? 1.25D : 1.0D;
        this.circlePhase += this.circleDir * 0.045D * speedScale;
        double desiredY = Math.max(groundBelowY() + HOVER_HEIGHT, target.getY() + HOVER_HEIGHT);
        Vec3 anchor = new Vec3(
                target.getX() + Math.cos(this.circlePhase) * CIRCLE_RADIUS,
                desiredY,
                target.getZ() + Math.sin(this.circlePhase) * CIRCLE_RADIUS);

        steerTowards(anchor, 0.55D * speedScale, 0.16D);
        Vec3 vel = this.getDeltaMovement();
        if (vel.horizontalDistanceSqr() > 0.004D) faceHeading(vel, 8.0F);
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (t % 9 == 0) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 3.0F, 1.0F);

        // Strafe the target with fire from the air once it has a clear line on it.
        if (this.airBreathCooldown <= 0 && t > 22 && this.hasLineOfSight(target)
                && this.distanceToSqr(target) < 26.0D * 26.0D) {
            this.breathAim = aimAt(target);
            syncBreathAim();
            setAttackState(AttackState.AIR_BREATH);
            return;
        }
        if (this.flightTicks >= FLIGHT_BUDGET) {
            beginDive();
        }
    }

    private void tickAirBreath(int t) {
        this.flightTicks++;
        LivingEntity target = this.getTarget();
        if (target == null) {
            beginLand();
            return;
        }
        // Hold station and hover while it pours fire down — wings hammering, body almost still.
        double desiredY = Math.max(groundBelowY() + HOVER_HEIGHT * 0.85D, target.getY() + HOVER_HEIGHT * 0.85D);
        Vec3 hold = new Vec3(this.getX(), desiredY, this.getZ());
        steerTowards(hold, 0.18D, 0.22D);
        if (t % 5 == 0) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 3.5F, 1.1F);

        if (t < AIR_BREATH_WINDUP) {
            faceTowards(target, 14.0F);
            if (t == 1) this.playSound(SoundEvents.FIRECHARGE_USE, 2.5F, 0.5F);
            if (this.level() instanceof ServerLevel sl) {
                Vec3 mouth = mouthPos();
                sl.sendParticles(ParticleTypes.SMALL_FLAME, mouth.x, mouth.y, mouth.z, 3, 0.2D, 0.2D, 0.2D, 0.02D);
            }
        } else {
            int bt = t - AIR_BREATH_WINDUP;
            if (bt == 0) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FIRE_BREATH.get(), 4.0F, 0.65F);
            faceTowards(target, 6.0F);
            breatheFire(bt, target, true);
        }

        if (t >= AIR_BREATH_DURATION) {
            this.airBreathCooldown = isRaging() ? 50 : 90;
            if (this.flightTicks >= FLIGHT_BUDGET) {
                beginDive();
            } else {
                setAttackState(AttackState.FLY);
            }
        }
    }

    private void beginDive() {
        setAttackState(AttackState.DIVE);
        this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_ROAR.get(), 6.0F, 1.0F);
    }

    private void tickDive(int t) {
        LivingEntity target = this.getTarget();
        if (target == null) {
            beginLand();
            return;
        }
        // Lead the target so it is a stoop at where they will be, not where they were.
        Vec3 lead = target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(6.0D));
        Vec3 to = lead.subtract(this.position());
        Vec3 dir = to.lengthSqr() > 1.0E-3D ? to.normalize() : lookDir();
        double speed = isRaging() ? 1.05D : 0.85D;

        // Flatten out just above the ground so it rakes across instead of drilling into the dirt.
        double clearance = this.getY() - groundBelowY();
        if (clearance < 4.0D) {
            dir = new Vec3(dir.x, Math.max(dir.y, (2.5D - clearance) * 0.25D), dir.z).normalize();
        }
        Vec3 v = this.getDeltaMovement();
        Vec3 want = dir.scale(speed);
        this.setDeltaMovement(v.add(want.subtract(v).scale(0.35D)));
        faceHeading(this.getDeltaMovement(), 18.0F);

        // The burning wake (fire and heavy smoke streaming off the body) is drawn by the client from the synced DIVE
        // state (client.fx.creature.TrailFx); the server only scorches the ground where the stoop rakes close over it.
        if (this.level() instanceof ServerLevel sl && clearance < 5.0D && t % 3 == 0) {
            Fx.send(sl, FxKinds.MARK_SCORCH, this.getX(), groundBelowY(), this.getZ(), 64.0D, -1, 2.6F, 45.0F);
        }

        boolean closeToTarget = this.distanceToSqr(target) < 12.0D;
        boolean nearGround = clearance <= 1.6D;
        if (closeToTarget || nearGround || t >= DIVE_DURATION) {
            doDiveImpact();
            beginLand();
        }
    }

    private void doDiveImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 c = this.position();
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.5F : 1.25F);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(5.5D, 3.5D, 5.5D), e -> e != this && e.isAlive())) {
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            victim.igniteForSeconds(5);
            Vec3 away = victim.position().subtract(c);
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);
            if (flat.lengthSqr() > 1.0E-4D) flat = flat.normalize();
            else flat = Vec3.directionFromRotation(0.0F, this.getYRot());
            victim.push(flat.x * 2.0D, 0.75D, flat.z * 2.0D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }
        // A body-slam, not a TNT charge: heavy impact, dust ring, fire washing outward.
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.42F);
        this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 5.0F, 0.6F);
        for (int i = 0; i < 22; i++) {
            double a = i / 22.0D * Math.PI * 2.0D;
            Vec3 out = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            sl.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3D, c.z, 0,
                    out.x * 0.55D, 0.10D, out.z * 0.55D, 1.0D);
        }
        Vec3 ground = new Vec3(c.x, groundBelowY(), c.z);
        CreatureFx.slam(sl, ground, 8.0F, 1.4F, 2.0F, 32.0F, 24);
        Fx.send(sl, FxKinds.MARK_SCORCH, ground, 64.0D, -1, 5.0F, 90.0F);
    }

    private void beginLand() {
        setAttackState(AttackState.LAND);
    }

    private void tickLand(int t) {
        // Flare and brake: fall fast while high, then cushion the last couple of blocks.
        double clearance = this.getY() - groundBelowY();
        double descent = -Mth.clamp(clearance * 0.22D, 0.10D, 0.55D);
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.x * 0.72D, Mth.lerp(0.4D, v.y, descent), v.z * 0.72D);
        if (t == 1) this.playSound(com.hogwartsmagic.ModSounds.HORNTAIL_FLAP.get(), 4.5F, 0.75F);
        if (this.onGround() || clearance <= 0.4D || t >= LAND_DURATION) {
            this.setNoGravity(false);
            this.entityData.set(DATA_FLYING, false);
            setAttackState(AttackState.NONE);
            this.actionCooldown = isRaging() ? 16 : 32;
            this.playSound(SoundEvents.RAVAGER_STEP, 4.0F, 0.4F);
            if (this.level() instanceof ServerLevel sl) {
                // Four tonnes of dragon settle onto the ground.
                CreatureFx.slam(sl, this.position(), 7.0F, 1.2F, 2.0F, 32.0F, 20);
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------------

    /** Blend the current velocity toward a velocity that heads for {@code point}. */
    private void steerTowards(Vec3 point, double maxSpeed, double responsiveness) {
        Vec3 to = point.subtract(this.position());
        double len = to.length();
        Vec3 want = len > 1.0E-4D ? to.scale(Math.min(maxSpeed, len * 0.22D) / len) : Vec3.ZERO;
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.add(want.subtract(v).scale(responsiveness)));
    }

    private void faceTowards(Entity target, float speed) {
        this.getLookControl().setLookAt(target, speed * 2.0F, speed * 2.0F);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float wanted = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(rotateTowards(this.getYRot(), wanted, speed));
        this.yBodyRot = this.getYRot();
    }

    /** Point the body along a velocity vector — flying nose-first, the way a real flier moves. */
    private void faceHeading(Vec3 velocity, float speed) {
        if (velocity.horizontalDistanceSqr() < 1.0E-5D) return;
        float wanted = (float) (Math.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(rotateTowards(this.getYRot(), wanted, speed));
        this.yBodyRot = this.getYRot();
    }

    private static float rotateTowards(float from, float to, float max) {
        float delta = Mth.wrapDegrees(to - from);
        delta = Mth.clamp(delta, -max, max);
        return from + delta;
    }

    /** Unit vector pointing out of the dragon's left flank. */
    private Vec3 leftVector() {
        double yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
    }

    private Vec3 aimAt(LivingEntity target) {
        Vec3 mouth = mouthPos();
        // Aim at the chest, not the feet, so the cone washes over the player.
        Vec3 point = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = point.subtract(mouth);
        return to.lengthSqr() > 1.0E-4D ? to.normalize() : lookDir();
    }

    /** How far below the dragon the floor is searched for before falling back to the surface heightmap. */
    private static final int GROUND_SCAN_DEPTH = 24;

    /**
     * The top of the floor under the dragon: scanned down from its own height (so a roof or a cave ceiling above
     * it is ignored: Gringotts, not the grass over Gringotts), skipping leaves; the surface heightmap only when
     * nothing solid lies within {@link #GROUND_SCAN_DEPTH} blocks (high in the open sky).
     */
    private double groundBelowY() {
        if (this.level() instanceof ServerLevel sl) {
            BlockPos start = this.blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            int minY = sl.getMinBuildHeight();
            for (int i = 0; i <= GROUND_SCAN_DEPTH; i++) {
                int y = start.getY() - i;
                if (y < minY) break;
                m.set(start.getX(), y, start.getZ());
                net.minecraft.world.level.block.state.BlockState st = sl.getBlockState(m);
                if (!st.getCollisionShape(sl, m).isEmpty() && !st.is(net.minecraft.tags.BlockTags.LEAVES)) {
                    return y + 1.0D;
                }
            }
            return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ());
        }
        return this.getY();
    }

    private Vec3 lookDir() {
        return this.getLookAngle();
    }

    /**
     * Where the fire actually leaves the head. Measured off the model: the neck roots 1.0 block in
     * front of the entity origin at 1.65 blocks up, and the neck + skull run 3.05 blocks out from
     * there along the head's aim.
     */
    /** Where the fire leaves her, from her head turn (the client falls back to this when her snout was not drawn). */
    @Override
    public Vec3 mouthEstimate() {
        return mouthPos();
    }

    private Vec3 mouthPos() {
        float headYaw = this.yHeadRot * Mth.DEG_TO_RAD;
        float pitch = this.getXRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(headYaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(headYaw) * Mth.cos(pitch));
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 neckBase = this.position().add(fwd.x * 1.0D, 1.65D, fwd.z * 1.0D);
        return neckBase.add(dir.scale(3.05D));
    }

    private void interruptIfCaster(LivingEntity victim) {
        if (victim instanceof ServerPlayer sp) {
            ModEvents.interruptActiveCasting(sp);
        }
    }

    // Action triggers used by the combat goal.
    void beginRoar() { setAttackState(AttackState.ROAR); hasRoared = true; }
    void beginBite() { setAttackState(AttackState.BITE); }
    void beginTailWhip() { beginTailWhipInternal(); }
    public void beginBreath() { setAttackState(AttackState.BREATH_WINDUP); }
    void beginWingGust() { setAttackState(AttackState.WING_GUST); }
    void beginFlight() { beginTakeoff(); }

    /**
     * Imperio possession (Spell Mastery Phase 6b): breathe fire along the current look when a move can start.
     * The possessed dragon has no target, so the windup aims along getLookAngle(), which the caster steers.
     */
    public void possessedAttack() {
        if (canStartAction()) {
            beginBreath();
        }
    }

    boolean canStartAction() {
        return getAttackState() == AttackState.NONE && this.actionCooldown <= 0;
    }

    boolean inAerialSequence() {
        return isAerial();
    }

    /** Discourages picking the same move three times running — repetition is what reads as "robotic". */
    private boolean overusing(AttackState move) {
        return this.lastGroundAttack == move && this.repeatedAttacks >= 2;
    }

    private void noteAttack(AttackState move) {
        if (this.lastGroundAttack == move) {
            this.repeatedAttacks++;
        } else {
            this.lastGroundAttack = move;
            this.repeatedAttacks = 1;
        }
    }

    // -------------------------------------------------------------------------------------------
    // Damage / hooks
    // -------------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Stagger on heavy ground hits when not committed; never stagger out of flight.
        boolean wasBusy = isBusy() || isAerial();
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide && !wasBusy && amount >= 14.0F && this.isAlive()) {
            setAttackState(AttackState.STAGGER);
        }
        return result;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.igniteForSeconds(3);
            interruptIfCaster(living);
        }
        return hit;
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
        if (this.level() instanceof ServerLevel sl && this.deathTime % 4 == 0) {
            sl.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX() + (this.random.nextDouble() - 0.5D) * this.getBbWidth(),
                    this.getY() + this.random.nextDouble() * this.getBbHeight(),
                    this.getZ() + (this.random.nextDouble() - 0.5D) * this.getBbWidth(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // Only consulted for dragons that are not persistence-required, i.e. the wild ones that
        // generated in the peaks. Summoned dragons are pinned in finalizeSpawn and never reach here.
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // a flying dragon doesn't take fall damage
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Raging", isRaging());
        tag.putBoolean("HasRoared", hasRoared);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Raging")) this.entityData.set(DATA_RAGING, tag.getBoolean("Raging"));
        if (tag.contains("HasRoared")) this.hasRoared = tag.getBoolean("HasRoared");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.HORNTAIL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return com.hogwartsmagic.ModSounds.HORNTAIL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.HORNTAIL_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    // -------------------------------------------------------------------------------------------
    // Combat goal — reads the situation instead of rolling dice on every option.
    // -------------------------------------------------------------------------------------------

    static class HorntailCombatGoal extends Goal {
        private final HungarianHorntailEntity dragon;
        private int decisionCooldown;
        /** Non-zero while the dragon is deliberately giving ground to open up firing distance. */
        private int backOffTicks;
        private int strafeDir = 1;
        private int strafeTicks;

        HorntailCombatGoal(HungarianHorntailEntity dragon) {
            this.dragon = dragon;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = dragon.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            dragon.getNavigation().stop();
            this.backOffTicks = 0;
            this.strafeTicks = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = dragon.getTarget();
            if (target == null) return;

            // Aerial sequence is self-driving in the entity tick; the goal just waits it out.
            if (dragon.inAerialSequence()) {
                return;
            }

            dragon.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (decisionCooldown > 0) decisionCooldown--;
            if (backOffTicks > 0) backOffTicks--;
            if (strafeTicks > 0) strafeTicks--;

            if (!dragon.canStartAction()) {
                return; // committed to an action or on cooldown
            }

            // Intro roar before the first engagement.
            if (!dragon.hasRoared && dragon.distanceToSqr(target) < 400.0D) {
                dragon.beginRoar();
                return;
            }

            double dist = Math.sqrt(dragon.distanceToSqr(target));
            int nearby = countNearby(target);

            // --- Backing off: after taking a beating up close, the dragon retreats to burn them. ---
            if (backOffTicks > 0 && dist < 9.0D) {
                retreatFrom(target);
                return;
            }

            if (dist <= BITE_RANGE) {
                dragon.getNavigation().stop();
                if (decisionCooldown > 0) return;
                chooseCloseAttack(target, nearby);
                return;
            }

            if (dist <= 11.0D) {
                // Just outside snapping distance: circle, size them up, tail-sweep or close in.
                if (decisionCooldown <= 0 && !dragon.overusing(AttackState.TAIL_WHIP)
                        && dragon.random.nextInt(100) < 30) {
                    dragon.beginTailWhip();
                    dragon.noteAttack(AttackState.TAIL_WHIP);
                    decisionCooldown = dragon.isRaging() ? 14 : 24;
                    return;
                }
                strafe(target);
                return;
            }

            if (dist <= BREATH_RANGE) {
                if (decisionCooldown <= 0 && facingTarget(target) && dragon.hasLineOfSight(target)) {
                    int roll = dragon.random.nextInt(100);
                    int flyChance = dragon.isRaging() ? 26 : 15;
                    if (roll < flyChance) {
                        dragon.beginFlight();
                    } else {
                        dragon.beginBreath();
                        dragon.noteAttack(AttackState.BREATH);
                    }
                    decisionCooldown = dragon.isRaging() ? 18 : 30;
                    return;
                }
                // Stalk in slowly — a predator closing, not a zombie sprinting.
                dragon.getNavigation().moveTo(target, dragon.isRaging() ? 1.0D : 0.8D);
                return;
            }

            // Far: charge in, or climb and hunt from the air.
            if (decisionCooldown <= 0 && dragon.random.nextInt(100) < (dragon.isRaging() ? 26 : 14)) {
                dragon.beginFlight();
                decisionCooldown = 40;
                return;
            }
            dragon.getNavigation().moveTo(target, dragon.isRaging() ? 1.25D : 1.05D);
        }

        private void chooseCloseAttack(LivingEntity target, int nearby) {
            int roll = dragon.random.nextInt(100);
            // Surrounded -> sweep. Otherwise favour the bite, with the odd gust to make room.
            if ((nearby >= 2 || roll < 26) && !dragon.overusing(AttackState.TAIL_WHIP)) {
                dragon.beginTailWhip();
                dragon.noteAttack(AttackState.TAIL_WHIP);
            } else if (roll < 44 && !dragon.overusing(AttackState.WING_GUST)) {
                dragon.beginWingGust();
                dragon.noteAttack(AttackState.WING_GUST);
                // Blowing them away is a set-up for fire, so step back afterwards.
                backOffTicks = 40;
            } else if (roll < 88 && !dragon.overusing(AttackState.BITE)) {
                dragon.beginBite();
                dragon.noteAttack(AttackState.BITE);
            } else {
                dragon.beginBreath();
                dragon.noteAttack(AttackState.BREATH);
            }
            decisionCooldown = dragon.isRaging() ? 8 : 16;
        }

        /** Walk backwards away from the target while keeping it in the sights. */
        private void retreatFrom(LivingEntity target) {
            Vec3 away = dragon.position().subtract(target.position());
            if (away.lengthSqr() < 1.0E-4D) return;
            Vec3 to = dragon.position().add(away.normalize().scale(8.0D));
            dragon.getNavigation().moveTo(to.x, to.y, to.z, 1.0D);
        }

        /** Sidestep around the target — dragons circle their prey, they do not stand and stare. */
        private void strafe(LivingEntity target) {
            if (strafeTicks <= 0) {
                strafeTicks = 30 + dragon.random.nextInt(30);
                if (dragon.random.nextInt(3) == 0) strafeDir = -strafeDir;
            }
            Vec3 to = dragon.position().subtract(target.position());
            if (to.lengthSqr() < 1.0E-4D) return;
            double angle = Math.atan2(to.z, to.x) + strafeDir * 0.45D;
            double radius = 8.5D;
            double x = target.getX() + Math.cos(angle) * radius;
            double z = target.getZ() + Math.sin(angle) * radius;
            dragon.getNavigation().moveTo(x, target.getY(), z, 0.85D);
        }

        private boolean facingTarget(LivingEntity target) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, dragon.getYRot());
            Vec3 to = target.position().subtract(dragon.position());
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            if (toFlat.lengthSqr() < 1.0E-4D) return true;
            return toFlat.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize()) > 0.6D;
        }

        private int countNearby(LivingEntity around) {
            AABB box = around.getBoundingBox().inflate(4.0D);
            return dragon.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != dragon && e instanceof Player).size();
        }
    }
}
