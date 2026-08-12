package com.hogwartsmagic.entity;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Whomping Willow — a rooted, violently territorial tree.
 *
 * <p>It never takes a step, which is the whole design. Every other large mob in this mod closes the
 * distance for you; this one cannot, so the encounter is entirely about range discipline — step
 * inside during a recovery window, step out during a wind-up. That also means the usual mob
 * machinery is deliberately absent: no navigation, no stroll goal, no look control, and a trunk yaw
 * frozen at spawn. Which of the four branches swings is chosen by bearing instead of by turning.
 *
 * <p><b>The damage arc and the visible branch are the same numbers.</b> {@link #strikeReach},
 * {@link #branchTrail} and {@link #branchWorldYaw} are pure statics called by both this class and
 * {@code WhompingWillowModel}, following the contract {@code HungarianHorntailEntity.tailTrail}
 * established. {@link #BRANCH_RANGE} is deliberately shorter than the branch's visible reach, so the
 * failure mode is a branch whistling past your face rather than one hitting you from a foot short.
 *
 * <p>Server-authoritative throughout; the client only reads synched state to drive the model.
 */
public class WhompingWillowEntity extends Monster {

    public enum AttackState {
        // NOTE: append new constants at the END -- the ordinal is what gets synced to clients.
        // NOTE: never switch() on this in render code; if/else only. The synthetic switch-map
        // helper class has been observed to fail to load in this mod's dev environment.
        NONE, DORMANT, WAKE, SWEEP, SLAM, ROOT_STOMP, SWAT, GRAB, SMASH, STAGGER, FROZEN;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    // Attack PARAMETERS are declared before the state they belong to, so that within a single
    // ClientboundSetEntityDataPacket the model sees the branch and direction before the state that
    // consumes them.
    private static final EntityDataAccessor<Float> DATA_PLANTED_YAW =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_ACTIVE_BRANCH =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SWEEP_SIGN =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_KNOT_BRANCH =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_AWAKE =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HELD_VICTIM =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICK =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.INT);

    // --- timings (ticks). The model reads these, which is why they are public. -------------------
    public static final int WAKE_DURATION = 30;
    public static final int SWEEP_DURATION = 30;
    public static final float SWEEP_WIND_END = 0.30F;
    public static final float SWEEP_STRIKE_END = 0.70F;
    public static final int SLAM_DURATION = 34;
    public static final int SLAM_STRIKE_TICK = 16;
    public static final int STOMP_DURATION = 40;
    public static final int STOMP_STRIKE_TICK = 22;
    public static final int SWAT_DURATION = 26;
    public static final int SWAT_STRIKE_TICK = 11;
    public static final int GRAB_DURATION = 18;
    public static final int GRAB_CATCH_TICK = 10;
    public static final int SMASH_DURATION = 48;
    public static final int SMASH_BLOW_A = 18;
    public static final int SMASH_BLOW_B = 32;
    public static final int SMASH_RELEASE_TICK = 38;
    public static final int STAGGER_DURATION = 20;

    // --- geometry, shared with the model ---------------------------------------------------------
    /** Horizontal reach of the damage arc. The branch is visibly ~7.04 blocks long; the shortfall is
     *  deliberate, so a near miss reads as a near miss. */
    public static final double BRANCH_RANGE = 6.8D;
    public static final double BRANCH_INNER = 2.4D;
    public static final double BRANCH_ARC_HALF = 0.50D;
    private static final double GRAB_INNER = 2.0D;
    private static final double GRAB_RANGE = 5.6D;
    private static final double GRAB_ARC_HALF = 0.45D;
    private static final double STOMP_RANGE = 3.6D;
    private static final double SLAM_OFFSET = 5.0D;
    private static final double SLAM_RADIUS = 3.2D;
    private static final double SWAT_INNER = 2.0D;
    private static final double SWAT_RANGE = 6.4D;
    private static final double SWAT_ARC_HALF = 0.55D;
    /** The whole canopy volume, not a slice: a narrow band would leave a safe flight corridor
     *  straight through the crown. The rule a pilot learns is "climb above it or go around". */
    private static final double SWAT_LOW = 2.5D;
    private static final double SWAT_HIGH = 11.5D;

    private static final double WAKE_RADIUS = 9.0D;
    private static final double LEASH_RADIUS = 12.0D;
    private static final int WAKE_PATIENCE = 15;
    private static final int ANGRY_TICKS = 400;
    private static final int FROZEN_TICKS = 2400;
    private static final float SMASH_HARDNESS_LIMIT = 3.5F;
    private static final int MAX_DEBRIS = 4;

    private final Set<Integer> hitThisState = new HashSet<>();
    private boolean struck;
    private boolean victimReleased;
    private int crowdTicks;
    private int angryTicks;
    private int frozenTicks;
    private int actionCooldown;
    private AttackState lastAttack = AttackState.NONE;
    private int repeatedAttacks;

    public WhompingWillowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 45;
    }

    // --- shared pure statics: the model calls every one of these ----------------------------------

    /**
     * Normalised extension of the striking branch at state progress {@code p}: 0 coiled, 1 fully
     * out. Deliberately unitless — each attack scales it by its own reach constants and the model
     * drives its bone from the same number, so the visible extension and the damage radius cannot
     * drift apart.
     */
    public static float strikeReach(float p) {
        if (p <= SWEEP_WIND_END) {
            // wind back: ease out, so the cock-back reads as a deliberate tell
            float w = p / SWEEP_WIND_END;
            return -0.25F * (1.0F - (1.0F - w) * (1.0F - w));
        }
        if (p >= SWEEP_STRIKE_END) {
            float r = (p - SWEEP_STRIKE_END) / (1.0F - SWEEP_STRIKE_END);
            return 1.0F - r * r;
        }
        float s = (p - SWEEP_WIND_END) / (SWEEP_STRIKE_END - SWEEP_WIND_END);
        return Mth.sin(s * Mth.PI * 0.5F);
    }

    /** Yaw offset of the striking branch from its rest bearing, in radians. Multiply by the sweep
     *  sign. Runs from wound-back to follow-through across the swing. */
    public static float branchTrail(float p) {
        return Mth.lerp(Mth.clamp(p, 0.0F, 1.0F), -0.55F, 1.30F);
    }

    /** Compass bearing of branch {@code b}'s rest direction, in degrees, Minecraft yaw convention.
     *  The trunk never turns, so this is the only thing that decides where a branch points. */
    public static float branchWorldYaw(int b, float plantedYaw) {
        return Mth.wrapDegrees(plantedYaw + b * 90.0F);
    }

    /** Height above the entity's feet of branch {@code b}'s host joint, in blocks. Mirrors
     *  BRANCH_HOST/BRANCH_LIFT in build_scripts/generate_whomping_willow_model.py. */
    public static float branchHostY(int b) {
        return (b % 2 == 0) ? 3.5F : 5.0F;
    }

    /** Raise-hammer-raise-hammer curve for a held victim across the SMASH state. */
    public static float holdLift(float p) {
        float t = Mth.clamp(p, 0.0F, 1.0F);
        if (t < 0.30F) return t / 0.30F;
        if (t < 0.42F) return 1.0F - (t - 0.30F) / 0.12F;
        if (t < 0.62F) return (t - 0.42F) / 0.20F;
        if (t < 0.72F) return 1.0F - (t - 0.62F) / 0.10F;
        return 0.15F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 220.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                // It cannot chase, so a large follow range only makes it flail at things it can
                // never touch.
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.STEP_HEIGHT, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PLANTED_YAW, 0.0F);
        builder.define(DATA_ACTIVE_BRANCH, (byte) 0);
        builder.define(DATA_SWEEP_SIGN, (byte) 1);
        builder.define(DATA_KNOT_BRANCH, (byte) 0);
        builder.define(DATA_AWAKE, false);
        builder.define(DATA_ENRAGED, false);
        builder.define(DATA_HELD_VICTIM, -1);
        builder.define(DATA_OWNER, Optional.empty());
        // DORMANT, not 0. A willow that spawned in NONE would be awake and holding its branches up
        // from the moment the chunk loaded, which defeats the ambush entirely.
        builder.define(DATA_ATTACK_STATE, (byte) AttackState.DORMANT.ordinal());
        builder.define(DATA_STATE_TICK, 0);
    }

    @Override
    protected void registerGoals() {
        // Exactly one goal. No FloatGoal, no stroll, no LookAtPlayerGoal -- a rooted tree neither
        // swims, wanders, nor turns its head.
        this.goalSelector.addGoal(1, new WillowAttackGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // mustReach MUST be false: with it true, TargetGoal asks the navigation for a path on every
        // evaluation, and a mob with MOVEMENT_SPEED 0 never produces one, so it would never acquire
        // a target at all. mustSee is false because a tree feels footfalls.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class,
                10, false, false, this::isValidVictim));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class,
                10, false, false, e -> e instanceof LivingEntity l && isValidVictim(l)));
    }

    private boolean isValidVictim(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e instanceof WhompingWillowEntity) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        // A willow you grew ignores you. That is the entire point of growing one.
        return !isOwner(e);
    }

    public boolean isOwner(Entity e) {
        UUID owner = this.entityData.get(DATA_OWNER).orElse(null);
        return owner != null && owner.equals(e.getUUID());
    }

    public void setOwner(@Nullable UUID owner) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        // Snapped to a right angle so the four branches sit on the compass. This also sidesteps a
        // quantisation trap: yaw travels to clients as a byte (1.4 degree steps), and at 6.8 blocks
        // of reach that is 0.17 blocks of drift between the branch you see and the arc that hurts.
        float yaw = Math.round(this.getYRot() / 90.0F) * 90.0F;
        this.entityData.set(DATA_PLANTED_YAW, yaw);
        this.entityData.set(DATA_KNOT_BRANCH, (byte) this.random.nextInt(4));
        pinYaw(yaw);
        // Never ambient wildlife: a landmark tree that despawns is a bug report.
        this.setPersistenceRequired();
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    private void pinYaw(float yaw) {
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
        this.yHeadRot = yaw;
        this.yHeadRotO = yaw;
    }

    public float getPlantedYaw() {
        return this.entityData.get(DATA_PLANTED_YAW);
    }

    public AttackState getAttackState() {
        return AttackState.fromByte(this.entityData.get(DATA_ATTACK_STATE));
    }

    public int getStateTick() {
        return this.entityData.get(DATA_STATE_TICK);
    }

    public int getActiveBranch() {
        return this.entityData.get(DATA_ACTIVE_BRANCH);
    }

    public int getSweepSign() {
        return this.entityData.get(DATA_SWEEP_SIGN);
    }

    public int getKnotBranch() {
        return this.entityData.get(DATA_KNOT_BRANCH);
    }

    public boolean isAwake() {
        return this.entityData.get(DATA_AWAKE);
    }

    public boolean isEnraged() {
        return this.entityData.get(DATA_ENRAGED);
    }

    /** Progress through the current state, 0..1, for the model. */
    public float stateProgress(int duration) {
        return duration <= 0 ? 0.0F : Mth.clamp(getStateTick() / (float) duration, 0.0F, 1.0F);
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_STATE_TICK, 0);
        this.struck = false;
        this.victimReleased = false;
        this.hitThisState.clear();
    }

    @Nullable
    public LivingEntity getHeldVictim() {
        int id = this.entityData.get(DATA_HELD_VICTIM);
        if (id < 0) return null;
        Entity e = this.level().getEntity(id);
        return e instanceof LivingEntity living ? living : null;
    }

    private void setHeldVictim(@Nullable LivingEntity victim) {
        this.entityData.set(DATA_HELD_VICTIM, victim == null ? -1 : victim.getId());
    }

    // --- tick -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        pinYaw(getPlantedYaw());
        if (this.level().isClientSide) {
            return;
        }

        AttackState state = getAttackState();

        if (tickRedstone(state)) {
            return;
        }

        // Dormant costs nothing: no state tick means no synched-data traffic, which matters when a
        // stand of these is standing around as scenery.
        if (state != AttackState.DORMANT) {
            this.entityData.set(DATA_STATE_TICK, getStateTick() + 1);
        }
        if (this.actionCooldown > 0) this.actionCooldown--;

        if (!isEnraged() && this.getHealth() < this.getMaxHealth() * 0.40F) {
            this.entityData.set(DATA_ENRAGED, true);
        }

        if (state == AttackState.DORMANT) {
            tickProvocation();
            // It can neither flee nor close, so without this a bow from forty blocks beats it for
            // free over ten minutes. Only ever runs after twenty seconds unprovoked, so a live
            // fight is never undone.
            if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
            return;
        }

        if (state == AttackState.FROZEN) {
            if (--this.frozenTicks <= 0) setAttackState(AttackState.NONE);
            return;
        }

        advanceState(state, getStateTick() - 1);
        tickAggro();
    }

    /**
     * Redstone at the roots holds the tree limp. Sampling the entity's own base beats inventing a
     * knot <i>block</i>: an entity cannot receive redstone updates, a block at the trunk would have
     * to be kept in sync with spawn, death and despawn, and this works identically for wild trees
     * and grown ones with no worldgen coupling. A lever gives you a gate; a pressure plate gives
     * you a path.
     *
     * @return true if the caller should stop ticking this entity
     */
    private boolean tickRedstone(AttackState state) {
        if (this.tickCount % 4 != 0) return state == AttackState.FROZEN && this.frozenTicks < 0;
        boolean powered = this.level().getBestNeighborSignal(this.blockPosition()) > 0
                || this.level().hasNeighborSignal(this.blockPosition().below());
        if (powered) {
            if (state != AttackState.FROZEN) {
                dropVictim();
                setAttackState(AttackState.FROZEN);
                playSound(ModSounds.WILLOW_GROAN.get(), 1.4F, 0.8F);
            }
            // Negative means "held by wire": survives damage and never times out.
            this.frozenTicks = -1;
            return true;
        }
        if (state == AttackState.FROZEN && this.frozenTicks < 0) {
            setAttackState(AttackState.NONE);
        }
        return false;
    }

    private void tickProvocation() {
        List<LivingEntity> near = this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - WAKE_RADIUS, getY() - 3.0D, getZ() - WAKE_RADIUS,
                        getX() + WAKE_RADIUS, getY() + 12.0D, getZ() + WAKE_RADIUS),
                this::isValidVictim);
        if (near.isEmpty()) {
            this.crowdTicks = 0;
            return;
        }
        if (++this.crowdTicks >= WAKE_PATIENCE) {
            wake();
        }
    }

    private void wake() {
        this.crowdTicks = 0;
        this.angryTicks = ANGRY_TICKS;
        this.entityData.set(DATA_AWAKE, true);
        setAttackState(AttackState.WAKE);
        playSound(ModSounds.WILLOW_ALERT.get(), 2.0F, 0.9F);
    }

    private void tickAggro() {
        LivingEntity target = getTarget();
        boolean anyoneClose = target != null && horizontalDistTo(target.position()) <= LEASH_RADIUS
                && isValidVictim(target);
        if (anyoneClose) {
            this.angryTicks = ANGRY_TICKS;
        } else if (--this.angryTicks <= 0 && getAttackState() == AttackState.NONE) {
            setTarget(null);
            this.entityData.set(DATA_AWAKE, false);
            setAttackState(AttackState.DORMANT);
        }
    }

    private void advanceState(AttackState state, int t) {
        if (state == AttackState.WAKE) {
            if (t == 3) {
                playSound(ModSounds.WILLOW_ROOTS.get(), 1.6F, 0.9F);
                leafBurst(2.0D, 30);
            }
            if (t >= WAKE_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.SWEEP) {
            if (t == 2) playSound(ModSounds.WILLOW_WHIP.get(), 1.8F, 0.95F);
            float p = t / (float) SWEEP_DURATION;
            if (p >= SWEEP_WIND_END && p <= SWEEP_STRIKE_END) doSweepTick(p);
            if (t >= SWEEP_DURATION) finishAction();
        } else if (state == AttackState.SLAM) {
            if (t == 2) playSound(ModSounds.WILLOW_GROAN.get(), 1.7F, 1.0F);
            if (t < SLAM_STRIKE_TICK && t % 2 == 0) markSlamZone();
            if (t == SLAM_STRIKE_TICK && !this.struck) {
                this.struck = true;
                doSlamImpact();
            }
            if (t >= SLAM_DURATION) finishAction();
        } else if (state == AttackState.ROOT_STOMP) {
            if (t == 2) playSound(ModSounds.WILLOW_ROOTS.get(), 2.0F, 0.85F);
            if (t == STOMP_STRIKE_TICK && !this.struck) {
                this.struck = true;
                doStompImpact();
            }
            if (t >= STOMP_DURATION) finishAction();
        } else if (state == AttackState.SWAT) {
            if (t == 2) playSound(ModSounds.WILLOW_WHIP.get(), 2.0F, 1.1F);
            if (t == SWAT_STRIKE_TICK && !this.struck) {
                this.struck = true;
                doSwatImpact();
            }
            if (t >= SWAT_DURATION) finishAction();
        } else if (state == AttackState.GRAB) {
            if (t == 2) playSound(ModSounds.WILLOW_WHIP.get(), 1.4F, 0.8F);
            if (t == GRAB_CATCH_TICK) {
                LivingEntity caught = findGrabVictim();
                if (caught != null) {
                    setHeldVictim(caught);
                    setAttackState(AttackState.SMASH);
                    return;
                }
            }
            if (t >= GRAB_DURATION) finishAction();
        } else if (state == AttackState.SMASH) {
            tickSmash(t);
            if (t >= SMASH_DURATION) {
                dropVictim();
                finishAction();
            }
        } else if (state == AttackState.STAGGER) {
            if (t >= STAGGER_DURATION) setAttackState(AttackState.NONE);
        }
    }

    private void finishAction() {
        setAttackState(AttackState.NONE);
        this.actionCooldown = isEnraged() ? 8 : 14;
    }

    // --- attacks -----------------------------------------------------------------------------------

    private Vec3 branchDir(int branch, double trail) {
        Vec3 rest = Vec3.directionFromRotation(0.0F, branchWorldYaw(branch, getPlantedYaw()));
        Vec3 side = new Vec3(-rest.z, 0.0D, rest.x);
        return rest.scale(Math.cos(trail)).add(side.scale(Math.sin(trail))).normalize();
    }

    private double horizontalDistTo(Vec3 at) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * A moving arc, re-evaluated every tick of the swing rather than a single cone at the strike
     * frame, so what the branch sweeps through is what it hits. Copied from the Horntail's tail
     * whip, including the annulus — a branch tip passing overhead should not hit something pressed
     * against the trunk — and the per-state hit set so one swing cannot hit the same victim twice.
     */
    private void doSweepTick(float p) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        int branch = getActiveBranch();
        Vec3 dir = branchDir(branch, branchTrail(p) * getSweepSign());
        double tipY = getY() + Mth.lerp(Mth.clamp(strikeReach(p), 0.0F, 1.0F), 3.3D, 0.9D);

        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(BRANCH_RANGE, 4.0D, BRANCH_RANGE), this::isValidVictim)) {
            if (this.hitThisState.contains(victim.getId())) continue;
            Vec3 to = victim.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double d = flat.length();
            if (d < BRANCH_INNER || d > BRANCH_RANGE) continue;
            if (flat.normalize().dot(dir) < Math.cos(BRANCH_ARC_HALF)) continue;
            if (victim.getBoundingBox().maxY < tipY - 1.9D
                    || victim.getBoundingBox().minY > tipY + 1.9D) continue;
            this.hitThisState.add(victim.getId());
            strike(victim, damage(1.0F), flat.normalize(), 1.9D, 0.42D);
        }
        if (p > SWEEP_WIND_END + 0.05F) {
            Vec3 tip = position().add(dir.scale(BRANCH_RANGE * 0.95D)).add(0.0D, tipY - getY(), 0.0D);
            sl.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 3, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    private void markSlamZone() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 c = slamCentre();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5.0;
            sl.sendParticles(ParticleTypes.CRIT,
                    c.x + Math.cos(a) * SLAM_RADIUS, c.y + 0.2D, c.z + Math.sin(a) * SLAM_RADIUS,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private Vec3 slamCentre() {
        Vec3 dir = branchDir(getActiveBranch(), 0.0D);
        double x = getX() + dir.x * SLAM_OFFSET;
        double z = getZ() + dir.z * SLAM_OFFSET;
        int y = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
        return new Vec3(x, y, z);
    }

    private void doSlamImpact() {
        Vec3 c = slamCentre();
        playSound(ModSounds.WILLOW_IMPACT.get(), 2.2F, 0.8F);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(c.x - SLAM_RADIUS, c.y - 2.5D, c.z - SLAM_RADIUS,
                        c.x + SLAM_RADIUS, c.y + 2.5D, c.z + SLAM_RADIUS), this::isValidVictim)) {
            Vec3 push = victim.position().subtract(c);
            strike(victim, damage(1.55F), new Vec3(push.x, 0.0D, push.z).normalize(), 0.8D, 0.75D);
        }
        if (this.level() instanceof ServerLevel sl) {
            smashBlocks(sl, BlockPos.containing(c));
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5D, c.z, 4, 1.2D, 0.2D, 1.2D, 0.0D);
        }
    }

    /** Exists so that "stand at the trunk and hold left-click" is not a solution. */
    private void doStompImpact() {
        playSound(ModSounds.WILLOW_IMPACT.get(), 2.4F, 0.7F);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(STOMP_RANGE, 2.5D, STOMP_RANGE), this::isValidVictim)) {
            Vec3 to = victim.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() > STOMP_RANGE) continue;
            strike(victim, damage(1.2F), flat.normalize(), 2.6D, 0.55D);
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2D, getZ(),
                    26, 2.4D, 0.1D, 2.4D, 0.02D);
        }
    }

    private void strike(LivingEntity victim, float amount, Vec3 push, double force, double lift) {
        victim.hurt(this.damageSources().mobAttack(this), amount);
        victim.push(push.x * force, lift, push.z * force);
        victim.hurtMarked = true;
        if (victim instanceof ServerPlayer sp) {
            ModEvents.interruptActiveCasting(sp);
        }
    }

    private float damage(float scale) {
        float base = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
        return isEnraged() ? base * 1.2F : base;
    }

    // --- broom swat ---------------------------------------------------------------------------------

    private List<Player> broomRidersInReach() {
        List<Player> out = new ArrayList<>();
        for (Player p : this.level().getEntitiesOfClass(Player.class,
                getBoundingBox().inflate(SWAT_RANGE, 12.0D, SWAT_RANGE), this::isValidVictim)) {
            if (!(p.getVehicle() instanceof BroomEntity)) continue;
            double d = horizontalDistTo(p.position());
            double rel = p.getY() - getY();
            if (d >= SWAT_INNER && d <= SWAT_RANGE && rel >= SWAT_LOW && rel <= SWAT_HIGH) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Flicks a rider out of the canopy.
     *
     * <p>The dismount has to happen server-side: {@code BroomEntity.removePassenger} is what
     * broadcasts the teleport and motion correction the broom needs, because while ridden the broom
     * is client-authoritative and the client is still sitting at a stale position. A client-side
     * {@code stopRiding} never reaches that code and the broom appears to vanish. The broom's new
     * motion is therefore set <i>after</i> the dismount, or it would be overwritten by the
     * correction packet carrying the pre-swat velocity.
     *
     * <p>Slow falling is not a courtesy. {@code ModEvents} cancels fall damage only while the
     * player is still on the broom, so without this a swat at altitude is an unavoidable kill with
     * no feedback and no counter-play. The effect also shows on the HUD, which tells the player why
     * they survived.
     */
    private void doSwatImpact() {
        for (Player p : broomRidersInReach()) {
            if (this.hitThisState.contains(p.getId())) continue;
            this.hitThisState.add(p.getId());
            Vec3 dir = branchDir(getActiveBranch(), 0.0D);

            p.hurt(this.damageSources().mobAttack(this), damage(0.9F));
            p.fallDistance = 0.0F;
            if (p instanceof ServerPlayer sp) {
                ModEvents.interruptActiveCasting(sp);
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true, true));
            }

            Entity vehicle = p.getVehicle();
            p.stopRiding();
            if (vehicle instanceof BroomEntity broom) {
                broom.setDeltaMovement(dir.x * 0.5D, 0.25D, dir.z * 0.5D);
                if (this.level() instanceof ServerLevel sl) {
                    sl.getChunkSource().broadcast(broom, new ClientboundSetEntityMotionPacket(broom));
                }
            }

            p.setDeltaMovement(dir.x * 1.5D, 0.55D, dir.z * 1.5D);
            p.hurtMarked = true;
            if (p instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(p));
            }
        }
        playSound(ModSounds.WILLOW_WHIP.get(), 2.2F, 0.9F);
    }

    // --- grab and smash -----------------------------------------------------------------------------

    @Nullable
    private LivingEntity findGrabVictim() {
        Vec3 dir = branchDir(getActiveBranch(), 0.0D);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity c : this.level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(GRAB_RANGE, 4.0D, GRAB_RANGE), this::isValidVictim)) {
            if (!canBeGrabbed(c)) continue;
            Vec3 to = c.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            double d = flat.length();
            if (d < GRAB_INNER || d > GRAB_RANGE) continue;
            if (flat.normalize().dot(dir) < Math.cos(GRAB_ARC_HALF)) continue;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private boolean canBeGrabbed(LivingEntity c) {
        // Riders route to SWAT, which dismounts them first -- grabbing a passenger would start an
        // unbounded fight between this entity's teleport and the vehicle's.
        return c.getBbHeight() < 3.0F && !c.isPassenger();
    }

    private void tickSmash(int t) {
        LivingEntity victim = getHeldVictim();
        if (victim == null) return;
        // The Giant has no equivalent guard because nothing in its fight teleports. This mod has
        // Apparition, and pushing a position through a stale connection to a player who has changed
        // dimension is a hard desync.
        if (victim.isRemoved() || victim.level() != this.level()
                || victim.distanceToSqr(this) > 144.0D) {
            dropVictim();
            return;
        }

        Vec3 dir = branchDir(getActiveBranch(), 0.0D);
        float p = t / (float) SMASH_DURATION;
        double lift = Mth.lerp(holdLift(p), 1.2D, 7.4D);
        Vec3 at = position().add(dir.scale(3.2D)).add(0.0D, lift, 0.0D);
        holdVictimAt(victim, at);

        if ((t == SMASH_BLOW_A || t == SMASH_BLOW_B)) {
            int gy = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(at.x), Mth.floor(at.z));
            Vec3 ground = new Vec3(at.x, gy, at.z);
            holdVictimAt(victim, ground);
            victim.hurt(this.damageSources().mobAttack(this), damage(1.1F));
            victim.push(0.0D, 0.25D, 0.0D);
            victim.hurtMarked = true;
            if (victim instanceof ServerPlayer sp) ModEvents.interruptActiveCasting(sp);
            playSound(ModSounds.WILLOW_IMPACT.get(), 2.0F, 1.0F);
            if (t == SMASH_BLOW_A && this.level() instanceof ServerLevel sl) {
                smashBlocks(sl, BlockPos.containing(ground));
            }
        }

        if (t >= SMASH_RELEASE_TICK && !this.victimReleased) {
            this.victimReleased = true;
            // A soft toss, never a launch. The Giant can hurl you clear because that ends the fight
            // in its favour; a willow that flung you out of reach would be ending it in yours.
            victim.setDeltaMovement(dir.x * 0.6D, 0.35D, dir.z * 0.6D);
            victim.fallDistance = 0.0F;
            victim.hurtMarked = true;
            if (victim instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetEntityMotionPacket(victim));
            }
            setHeldVictim(null);
        }
    }

    private void holdVictimAt(LivingEntity victim, Vec3 at) {
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        victim.hurtMarked = true;
        if (victim instanceof ServerPlayer sp) {
            // A player's own client owns its position, so it has to be told rather than moved.
            sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
        } else {
            victim.setPos(at.x, at.y, at.z);
            victim.setOldPosAndRot();
        }
    }

    private void dropVictim() {
        LivingEntity victim = getHeldVictim();
        if (victim != null) {
            victim.fallDistance = 0.0F;
            victim.hurtMarked = true;
        }
        setHeldVictim(null);
    }

    // --- block smashing ------------------------------------------------------------------------------

    /** Only SLAM and the first SMASH blow do this. A sweep that ploughed a furrow every three
     *  seconds would eat the lawn, and unlike a giant the willow cannot walk away from the crater. */
    private void smashBlocks(ServerLevel level, BlockPos centre) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        List<BlockPos> doomed = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    // Never dig out its own footing.
                    double d = Math.hypot(pos.getX() + 0.5D - getX(), pos.getZ() + 0.5D - getZ());
                    if (d < 2.0D) continue;
                    if (isSmashable(level.getBlockState(pos), pos)) doomed.add(pos);
                }
            }
        }
        int debris = 0;
        for (BlockPos pos : doomed) {
            if (debris < MAX_DEBRIS && this.random.nextFloat() < 0.35F) {
                debris++;
                FallingBlockEntity chunk = FallingBlockEntity.fall(level, pos, level.getBlockState(pos));
                chunk.setHurtsEntities(2.0F, 20);
                chunk.setDeltaMovement((this.random.nextDouble() - 0.5D) * 0.4D, 0.45D,
                        (this.random.nextDouble() - 0.5D) * 0.4D);
            } else {
                level.destroyBlock(pos, true, this);
            }
        }
    }

    private boolean isSmashable(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return false;
        float hardness = state.getDestroySpeed(this.level(), pos);
        return hardness >= 0.0F && hardness < SMASH_HARDNESS_LIMIT;
    }

    private void leafBurst(double spread, int count) {
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.COMPOSTER, getX(), getY() + 5.0D, getZ(),
                    count, spread, 2.0D, spread, 0.02D);
        }
    }

    // --- the knot ------------------------------------------------------------------------------------

    /**
     * The book's trick: press the knot and the tree goes still. Only from the knot's own side, only
     * low down, and only while sneaking, so it cannot be stumbled into during a fight.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return isAtKnot(player) && player.isShiftKeyDown()
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown() || !isAtKnot(player)) {
            return InteractionResult.PASS;
        }
        dropVictim();
        setAttackState(AttackState.FROZEN);
        this.frozenTicks = FROZEN_TICKS;
        this.entityData.set(DATA_AWAKE, false);
        setTarget(null);
        playSound(ModSounds.WILLOW_GROAN.get(), 1.6F, 1.1F);
        leafBurst(1.5D, 12);
        return InteractionResult.CONSUME;
    }

    private boolean isAtKnot(Player player) {
        if (player.getY() > getY() + 2.0D) return false;
        Vec3 to = player.position().subtract(position());
        Vec3 flat = new Vec3(to.x, 0.0D, to.z);
        if (flat.length() > 2.5D) return false;
        Vec3 knot = Vec3.directionFromRotation(0.0F, branchWorldYaw(getKnotBranch(), getPlantedYaw()));
        return flat.normalize().dot(knot) >= Math.cos(0.61D); // +/- 35 degrees
    }

    // --- damage ---------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Every modifier lives here and nowhere else, so they can never compound.
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 1.6F;
        } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.6F;
        } else if (source.getDirectEntity() instanceof LivingEntity attacker
                && attacker.getMainHandItem().is(ItemTags.AXES)) {
            amount *= 1.35F;
        }

        boolean hurt = super.hurt(source, amount);
        if (!hurt || this.level().isClientSide) return hurt;

        AttackState state = getAttackState();
        if (state == AttackState.FROZEN && this.frozenTicks > 0) {
            setAttackState(AttackState.NONE);   // wire-held freezes survive; a pressed knot does not
        }
        if (state == AttackState.DORMANT) {
            wake();
        } else if (amount >= 12.0F && state != AttackState.SMASH && state != AttackState.STAGGER) {
            dropVictim();
            setAttackState(AttackState.STAGGER);
        }
        this.angryTicks = ANGRY_TICKS;
        this.entityData.set(DATA_AWAKE, true);
        return true;
    }

    @Override
    public void die(DamageSource source) {
        dropVictim();
        super.die(source);
    }

    // --- rooted ----------------------------------------------------------------------------------------

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 input) {
        super.travel(Vec3.ZERO);
    }

    @Override
    public int getMaxHeadYRot() {
        return 0;
    }

    @Override
    public int getHeadRotSpeed() {
        return 0;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPreventingPlayerRest(Player player) {
        // Monster returns true unconditionally, which would let a dormant tree block sleep forever
        // with nothing the player could do about it.
        return isAwake();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        // Levicorpus and Wingardium Leviosa exist in this mod and must not uproot it.
        if (effect.getEffect() == MobEffects.LEVITATION) return false;
        return super.canBeAffected(effect);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // The trunk box is 3 wide; the branches reach seven blocks out. Without this the canopy
        // pops out of existence the moment the trunk leaves the frustum.
        return this.getBoundingBox().inflate(8.0D, 2.0D, 8.0D);
    }

    // --- sound / save -----------------------------------------------------------------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        // Mob skips ambient sound entirely when this is null, which is what keeps a dormant willow
        // indistinguishable from scenery.
        return isAwake() ? ModSounds.WILLOW_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.WILLOW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.WILLOW_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // Losing plantedYaw would rotate the tree, its damage arcs and its knot on every reload.
        tag.putFloat("PlantedYaw", getPlantedYaw());
        tag.putByte("KnotBranch", (byte) getKnotBranch());
        tag.putBoolean("Awake", isAwake());
        tag.putBoolean("Enraged", isEnraged());
        tag.putInt("AngryTicks", this.angryTicks);
        this.entityData.get(DATA_OWNER).ifPresent(u -> tag.putUUID("Owner", u));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        float yaw = tag.getFloat("PlantedYaw");
        this.entityData.set(DATA_PLANTED_YAW, yaw);
        pinYaw(yaw);
        this.entityData.set(DATA_KNOT_BRANCH, tag.getByte("KnotBranch"));
        this.entityData.set(DATA_AWAKE, tag.getBoolean("Awake"));
        this.entityData.set(DATA_ENRAGED, tag.getBoolean("Enraged"));
        this.angryTicks = tag.getInt("AngryTicks");
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"));
    }

    /** Spawn rule: it needs headroom, and it does not care what time it is. */
    public static boolean canSpawn(EntityType<WhompingWillowEntity> type, ServerLevelAccessor level,
                                   MobSpawnType spawnType, BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) return false;
        for (int dy = 0; dy < 9; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) return false;
        }
        return true;
    }

    // --- the one goal ---------------------------------------------------------------------------------

    private static class WillowAttackGoal extends Goal {
        private final WhompingWillowEntity willow;

        WillowAttackGoal(WhompingWillowEntity willow) {
            this.willow = willow;
            // No flags: this goal owns no movement, because there is none to own.
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            if (!this.willow.isAwake()) return false;
            return this.willow.getTarget() != null || !this.willow.broomRidersInReach().isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (this.willow.getAttackState() != AttackState.NONE) return;
            if (this.willow.actionCooldown > 0) return;

            List<Player> riders = this.willow.broomRidersInReach();
            if (!riders.isEmpty()) {
                begin(AttackState.SWAT, riders.get(0).position());
                return;
            }

            LivingEntity target = this.willow.getTarget();
            if (target == null) return;
            double d = this.willow.horizontalDistTo(target.position());
            if (d > BRANCH_RANGE + 0.8D) return;

            AttackState pick = choose(d);
            // Never the same attack three times running -- a predictable tree is a solved tree.
            if (pick == this.willow.lastAttack && ++this.willow.repeatedAttacks >= 2) {
                pick = choose(d);
                this.willow.repeatedAttacks = 0;
            } else if (pick != this.willow.lastAttack) {
                this.willow.repeatedAttacks = 0;
            }
            this.willow.lastAttack = pick;
            begin(pick, target.position());
        }

        private AttackState choose(double d) {
            float r = this.willow.random.nextFloat();
            if (d <= 3.2D) return r < 0.60F ? AttackState.ROOT_STOMP : AttackState.GRAB;
            if (r < 0.50F) return AttackState.SWEEP;
            return r < 0.75F ? AttackState.SLAM : AttackState.GRAB;
        }

        private void begin(AttackState state, Vec3 at) {
            // Parameters first, state second: the model must never see a state without its branch.
            int branch = this.willow.branchFor(at);
            this.willow.entityData.set(DATA_ACTIVE_BRANCH, (byte) branch);
            float off = Mth.wrapDegrees(bearing(at) - branchWorldYaw(branch, this.willow.getPlantedYaw()));
            this.willow.entityData.set(DATA_SWEEP_SIGN, (byte) (off >= 0.0F ? 1 : -1));
            this.willow.setAttackState(state);
        }

        private float bearing(Vec3 at) {
            return (float) (Mth.atan2(at.z - this.willow.getZ(), at.x - this.willow.getX())
                    * Mth.RAD_TO_DEG) - 90.0F;
        }
    }

    /** Which branch owns the bearing to {@code at}. Partitions the compass into the same four
     *  quadrants {@link #branchWorldYaw} lays out, so selection and geometry cannot disagree. */
    private int branchFor(Vec3 at) {
        float bearing = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float rel = Mth.wrapDegrees(bearing - getPlantedYaw());
        return Math.floorMod(Math.round(rel / 90.0F), 4);
    }
}
