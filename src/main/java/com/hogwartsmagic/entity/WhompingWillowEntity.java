package com.hogwartsmagic.entity;

import com.hogwartsmagic.ModParticles;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
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
 * <p><b>The damage is computed from the branch you can see.</b> Every hit test runs against
 * {@link WhompingWillowRig#limbPoints}: forward kinematics of the exact pose the client model draws,
 * from the same synched parameters. A sweep hits what the swinging limb passes through, a slam hits
 * where the limb lands, a grab catches what is under the tip, and a held victim is carried at the
 * tip. There is no separate damage geometry left to drift from the model, which is how the old one
 * ended up mirrored: two of its four branches swung on the far side of the trunk from their arcs.
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
    /** Where the striking limb is aimed: yaw off its rest bearing (radians), and the target's
     *  horizontal distance and height above the roots (blocks). Pose inputs, so they must reach the
     *  client with the state. */
    private static final EntityDataAccessor<Float> DATA_AIM_YAW =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_DIST =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_HEIGHT =
            SynchedEntityData.defineId(WhompingWillowEntity.class, EntityDataSerializers.FLOAT);
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

    // --- timings live in WhompingWillowRig, next to the pose curves built from them -------------
    private static final int WAKE_DURATION = WhompingWillowRig.WAKE_DURATION;
    private static final int SWEEP_DURATION = WhompingWillowRig.SWEEP_DURATION;
    private static final int SLAM_DURATION = WhompingWillowRig.SLAM_DURATION;
    private static final int SLAM_STRIKE_TICK = WhompingWillowRig.SLAM_STRIKE_TICK;
    private static final int STOMP_DURATION = WhompingWillowRig.STOMP_DURATION;
    private static final int STOMP_STRIKE_TICK = WhompingWillowRig.STOMP_STRIKE_TICK;
    private static final int SWAT_DURATION = WhompingWillowRig.SWAT_DURATION;
    private static final int SWAT_STRIKE_TICK = WhompingWillowRig.SWAT_STRIKE_TICK;
    private static final int GRAB_DURATION = WhompingWillowRig.GRAB_DURATION;
    private static final int GRAB_CATCH_TICK = WhompingWillowRig.GRAB_CATCH_TICK;
    private static final int SMASH_DURATION = WhompingWillowRig.SMASH_DURATION;
    private static final int SMASH_BLOW_A = WhompingWillowRig.SMASH_BLOW_A;
    private static final int SMASH_BLOW_B = WhompingWillowRig.SMASH_BLOW_B;
    private static final int SMASH_RELEASE_TICK = WhompingWillowRig.SMASH_RELEASE_TICK;
    private static final int STAGGER_DURATION = WhompingWillowRig.STAGGER_DURATION;

    // --- reach ------------------------------------------------------------------------------------
    /** How far out a target can be and still be worth swinging at: the sweep tip plus its capsule.
     *  Not a damage radius -- damage comes from the limb itself. */
    public static final double BRANCH_RANGE = WhompingWillowRig.SWEEP_DIST + 0.3D;
    /** Slack added to a limb segment's own half-thickness when testing contact: the leaves and
     *  streamers hanging off it are part of what hits you. */
    private static final double LIMB_SLACK = 0.45D;
    private static final double SLAM_SPLASH = 1.1D;
    private static final double GRAB_REACH = 1.3D;
    private static final double SWAT_SLACK = 0.9D;
    private static final double STOMP_RANGE = WhompingWillowRig.STOMP_RANGE;
    /** The whole canopy volume, not a slice: a narrow band would leave a safe flight corridor
     *  straight through the crown. The rule a pilot learns is "climb above it or go around". */
    private static final double SWAT_INNER = 2.0D;
    private static final double SWAT_RANGE = 7.0D;
    private static final double SWAT_LOW = 2.5D;
    private static final double SWAT_HIGH = 11.5D;

    private static final double WAKE_RADIUS = 9.0D;
    private static final double LEASH_RADIUS = 12.5D;
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

    /** Server-side pose scratch. Client poses live in the model. */
    private final WhompingWillowRig.Input rigIn = new WhompingWillowRig.Input();
    private final float[] rigRot = new float[WhompingWillowRig.BONES * 3];
    private final double[] limbPts = new double[(WhompingWillowRig.SEGS + 1) * 3];
    /** Client only: 0 hanging limp .. 1 roused, eased so waking and settling never pop. */
    private float rouse;
    private float rouseO;

    public WhompingWillowEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 45;
    }

    /** Bearing of limb {@code b}'s rest direction, in degrees, Minecraft yaw convention. The
     *  trunk never turns, so this is the only thing that decides where a limb points. */
    public static float branchWorldYaw(int b, float plantedYaw) {
        return Mth.wrapDegrees(plantedYaw + WhompingWillowRig.LIMB_BEARING[Math.floorMod(b, 4)]);
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
        builder.define(DATA_AIM_YAW, 0.0F);
        builder.define(DATA_AIM_DIST, (float) WhompingWillowRig.SWEEP_DIST);
        builder.define(DATA_AIM_HEIGHT, 1.0F);
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

    /**
     * Fills a rig input from the synched state. On the client the state tick runs on with the
     * partial tick and idle motion is switched on; on the server neither, which is what keeps the
     * pose the hit tests use identical to the one the client draws inside every damage window.
     */
    public void rigInput(WhompingWillowRig.Input in, float partial, boolean client) {
        AttackState state = getAttackState();
        float tick = getStateTick();
        if (client && state != AttackState.DORMANT && state != AttackState.FROZEN) {
            tick += partial;
        }
        in.state = state;
        in.tick = tick;
        in.active = getActiveBranch();
        in.sign = getSweepSign();
        in.aimYaw = this.entityData.get(DATA_AIM_YAW);
        in.aimDist = this.entityData.get(DATA_AIM_DIST);
        in.aimHeight = this.entityData.get(DATA_AIM_HEIGHT);
        in.rouse = client ? Mth.lerp(partial, this.rouseO, this.rouse) : 1.0F;
        // Every willow shares one baked mesh; offsetting the clock per entity stops a grove swaying
        // in perfect unison.
        in.age = this.tickCount + partial + (getId() % 97) * 3.7F;
        in.idle = client;
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
            AttackState s = getAttackState();
            float want = (isAwake() && s != AttackState.DORMANT && s != AttackState.FROZEN) ? 1.0F : 0.0F;
            this.rouseO = this.rouse;
            this.rouse += Mth.clamp(want - this.rouse, -0.05F, 0.06F);
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

        // The tick just synched, not the one before it: the client renders this same value, so the
        // pose the hit tests use is the frame the player sees.
        advanceState(state, getStateTick());
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
                leafBurst(3.0D, 40);
            }
            if (t >= WAKE_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.SWEEP) {
            if (t == 2) playSound(ModSounds.WILLOW_WHIP.get(), 1.8F, 0.95F);
            if (t >= WhompingWillowRig.SWEEP_HIT_START && t <= WhompingWillowRig.SWEEP_HIT_END) {
                doSweepTick(t);
            }
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
            if (t >= SWAT_STRIKE_TICK - 1 && t <= SWAT_STRIKE_TICK + 1) {
                doSwatTick(t);
            }
            if (t >= SWAT_DURATION) finishAction();
        } else if (state == AttackState.GRAB) {
            if (t == 2) playSound(ModSounds.WILLOW_WHIP.get(), 1.4F, 0.8F);
            if (t == GRAB_CATCH_TICK) {
                LivingEntity caught = findGrabVictim(t);
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

    // --- the limb, as the client draws it -----------------------------------------------------------

    /** Poses the rig at state tick {@code t} exactly as the client will (minus its idle motion,
     *  which is zero inside every damage window) and leaves the active limb's seven joints, in world
     *  coordinates, in {@link #limbPts}. */
    private double[] limbAt(float t) {
        rigInput(this.rigIn, 0.0F, false);
        this.rigIn.tick = t;
        WhompingWillowRig.pose(this.rigIn, this.rigRot);
        WhompingWillowRig.limbPoints(this.rigIn, this.rigRot, getActiveBranch(), getPlantedYaw(), this.limbPts);
        for (int k = 0; k <= WhompingWillowRig.SEGS; k++) {
            this.limbPts[k * 3] += getX();
            this.limbPts[k * 3 + 1] += getY();
            this.limbPts[k * 3 + 2] += getZ();
        }
        return this.limbPts;
    }

    private static Vec3 joint(double[] pts, int k) {
        return new Vec3(pts[k * 3], pts[k * 3 + 1], pts[k * 3 + 2]);
    }

    /**
     * Closest approach between limb segments {@code fromSeg..5} (segment k runs from joint k to
     * joint k+1) and {@code victim}, modelled as a vertical line through its box, minus the segment's
     * own radius, the victim's half-width and {@code slack}. Zero or less means touching.
     */
    private double limbGap(double[] pts, int fromSeg, LivingEntity victim, double slack) {
        AABB box = victim.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5D, cz = (box.minZ + box.maxZ) * 0.5D;
        double half = (box.maxX - box.minX) * 0.5D;
        double best = Double.MAX_VALUE;
        for (int k = fromSeg; k < WhompingWillowRig.SEGS; k++) {
            double d = segSegDist(pts[k * 3], pts[k * 3 + 1], pts[k * 3 + 2],
                    pts[k * 3 + 3], pts[k * 3 + 4], pts[k * 3 + 5],
                    cx, box.minY, cz, cx, box.maxY, cz);
            double gap = d - WhompingWillowRig.limbRadius(getActiveBranch(), k) - half - slack;
            if (gap < best) best = gap;
        }
        return best;
    }

    /** Shortest distance between segments P0P1 and Q0Q1 (Ericson, Real-Time Collision Detection). */
    static double segSegDist(double px, double py, double pz, double p1x, double p1y, double p1z,
                             double qx, double qy, double qz, double q1x, double q1y, double q1z) {
        double d1x = p1x - px, d1y = p1y - py, d1z = p1z - pz;
        double d2x = q1x - qx, d2y = q1y - qy, d2z = q1z - qz;
        double rx = px - qx, ry = py - qy, rz = pz - qz;
        double a = d1x * d1x + d1y * d1y + d1z * d1z;
        double e = d2x * d2x + d2y * d2y + d2z * d2z;
        double f = d2x * rx + d2y * ry + d2z * rz;
        double s, t;
        if (a <= 1.0E-9 && e <= 1.0E-9) {
            s = 0.0D;
            t = 0.0D;
        } else if (a <= 1.0E-9) {
            s = 0.0D;
            t = Mth.clamp(f / e, 0.0D, 1.0D);
        } else {
            double c = d1x * rx + d1y * ry + d1z * rz;
            if (e <= 1.0E-9) {
                t = 0.0D;
                s = Mth.clamp(-c / a, 0.0D, 1.0D);
            } else {
                double b = d1x * d2x + d1y * d2y + d1z * d2z;
                double denom = a * e - b * b;
                s = denom > 1.0E-9 ? Mth.clamp((b * f - c * e) / denom, 0.0D, 1.0D) : 0.0D;
                t = (b * s + f) / e;
                if (t < 0.0D) {
                    t = 0.0D;
                    s = Mth.clamp(-c / a, 0.0D, 1.0D);
                } else if (t > 1.0D) {
                    t = 1.0D;
                    s = Mth.clamp((b - c) / a, 0.0D, 1.0D);
                }
            }
        }
        double dx = (px + d1x * s) - (qx + d2x * t);
        double dy = (py + d1y * s) - (qy + d2y * t);
        double dz = (pz + d1z * s) - (qz + d2z * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double horizontalDistTo(Vec3 at) {
        double dx = at.x - getX();
        double dz = at.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private Vec3 branchDir(int branch) {
        return Vec3.directionFromRotation(0.0F, branchWorldYaw(branch, getPlantedYaw()));
    }

    private List<LivingEntity> victimsNear(double reach) {
        return this.level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(reach, 3.0D, reach), this::isValidVictim);
    }

    // --- attacks -----------------------------------------------------------------------------------

    /**
     * The sweep. Sub-stepped three times per tick, because at full speed the limb tip moves about a
     * block per tick and one sample per tick would let it pass clean through a player standing
     * between two samples.
     */
    private void doSweepTick(int t) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        List<LivingEntity> near = victimsNear(BRANCH_RANGE + 1.0D);
        Vec3 before = joint(limbAt(t - 1.0F), WhompingWillowRig.SEGS);
        double[] pts = null;
        for (int sub = 2; sub >= 0; sub--) {
            pts = limbAt(t - sub / 3.0F);
            for (LivingEntity victim : near) {
                if (this.hitThisState.contains(victim.getId())) continue;
                if (limbGap(pts, 1, victim, LIMB_SLACK) > 0.0D) continue;
                this.hitThisState.add(victim.getId());
                Vec3 out = victim.position().subtract(position());
                Vec3 flat = new Vec3(out.x, 0.0D, out.z).normalize();
                // Batted along the swing as much as away from the trunk.
                Vec3 swing = joint(pts, WhompingWillowRig.SEGS).subtract(before);
                Vec3 along = new Vec3(swing.x, 0.0D, swing.z);
                Vec3 push = along.lengthSqr() > 1.0E-6 ? flat.add(along.normalize()).normalize() : flat;
                strike(victim, damage(1.0F), push, 1.9D, 0.42D);
            }
        }
        // Trail the outer half of the limb as it actually is this tick, so the arc the player has to
        // learn is drawn by the thing that hits them.
        for (int k = 3; k <= WhompingWillowRig.SEGS; k++) {
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), pts[k * 3], pts[k * 3 + 1], pts[k * 3 + 2],
                    2, 0.3D, 0.25D, 0.3D, 0.03D);
        }
    }

    /** Where the slam will land, drawn from the pose at the strike tick, so the warning and the hit
     *  are the same line on the ground. */
    private void markSlamZone() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        double[] pts = limbAt(SLAM_STRIKE_TICK);
        for (int k = 2; k <= WhompingWillowRig.SEGS; k++) {
            double x = pts[k * 3], z = pts[k * 3 + 2];
            int y = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            sl.sendParticles(ModParticles.WILLOW_GLINT.get(), x, y + 0.15D, z, 2, 0.4D, 0.0D, 0.4D, 0.0D);
        }
    }

    private void doSlamImpact() {
        double[] pts = limbAt(SLAM_STRIKE_TICK);
        Vec3 tip = joint(pts, WhompingWillowRig.SEGS);
        playSound(ModSounds.WILLOW_IMPACT.get(), 2.2F, 0.8F);
        for (LivingEntity victim : victimsNear(BRANCH_RANGE + 2.0D)) {
            if (limbGap(pts, 2, victim, SLAM_SPLASH) > 0.0D) continue;
            Vec3 push = victim.position().subtract(tip);
            strike(victim, damage(1.55F), new Vec3(push.x, 0.0D, push.z).normalize(), 0.8D, 0.75D);
        }
        if (this.level() instanceof ServerLevel sl) {
            int gy = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(tip.x), Mth.floor(tip.z));
            smashBlocks(sl, BlockPos.containing(tip.x, gy, tip.z));
            // Dust thrown up along the whole length that hit the ground.
            for (int k = 2; k <= WhompingWillowRig.SEGS; k++) {
                double x = pts[k * 3], z = pts[k * 3 + 2];
                int y = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                sl.sendParticles(ModParticles.ROOT_DUST.get(), x, y + 0.3D, z, 6, 0.6D, 0.1D, 0.6D, 0.05D);
            }
            sl.sendParticles(ModParticles.BARK_CHIP.get(), tip.x, gy + 0.5D, tip.z, 12, 0.6D, 0.2D, 0.6D, 0.12D);
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), tip.x, gy + 1.2D, tip.z, 14, 1.0D, 0.5D, 1.0D, 0.06D);
        }
    }

    /** Exists so that "stand at the trunk and hold left-click" is not a solution. The ring is the
     *  roots' own reach: the generator refuses to build a tree whose roots do not land on it. */
    private void doStompImpact() {
        playSound(ModSounds.WILLOW_IMPACT.get(), 2.4F, 0.7F);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(STOMP_RANGE, 2.5D, STOMP_RANGE), this::isValidVictim)) {
            Vec3 to = victim.position().subtract(position());
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.length() > STOMP_RANGE + victim.getBbWidth() * 0.5D) continue;
            strike(victim, damage(1.2F), flat.normalize(), 2.6D, 0.55D);
        }
        if (this.level() instanceof ServerLevel sl) {
            for (int i = 0; i < 30; i++) {
                double a = i * Math.PI * 2.0 / 30.0;
                sl.sendParticles(ModParticles.ROOT_DUST.get(),
                        getX() + Math.cos(a) * STOMP_RANGE, getY() + 0.2D, getZ() + Math.sin(a) * STOMP_RANGE,
                        1, 0.1D, 0.05D, 0.1D, 0.05D);
            }
            sl.sendParticles(ModParticles.ROOT_DUST.get(), getX(), getY() + 0.2D, getZ(),
                    26, 2.4D, 0.1D, 2.4D, 0.03D);
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), getX(), getY() + 8.0D, getZ(),
                    30, 3.0D, 1.5D, 3.0D, 0.03D);
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
     * Flicks a rider out of the canopy -- if the limb, aimed where they were when it began its
     * whip, actually reaches them.
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
     * no feedback and no counter-play.
     */
    private void doSwatTick(int t) {
        boolean any = false;
        for (int sub = 1; sub >= 0; sub--) {
            double[] pts = limbAt(t - sub * 0.5F);
            for (Player p : this.level().getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(SWAT_RANGE + 1.0D, 13.0D, SWAT_RANGE + 1.0D), this::isValidVictim)) {
                if (this.hitThisState.contains(p.getId())) continue;
                if (limbGap(pts, 1, p, SWAT_SLACK) > 0.0D) continue;
                this.hitThisState.add(p.getId());
                any = true;
                swat(p);
            }
        }
        if (t == SWAT_STRIKE_TICK || any) playSound(ModSounds.WILLOW_WHIP.get(), 2.2F, 0.9F);
    }

    private void swat(Player p) {
        Vec3 dir = branchDir(getActiveBranch());
        p.hurt(this.damageSources().mobAttack(this), damage(0.9F));
        p.fallDistance = 0.0F;
        if (p instanceof ServerPlayer sp) {
            ModEvents.interruptActiveCasting(sp);
            // Particles off: vanilla's potion swirl would trail the player all the way down. The
            // HUD icon stays, which is what tells them why they survived the fall.
            sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), p.getX(), p.getY() + 1.0D, p.getZ(),
                    16, 0.6D, 0.6D, 0.6D, 0.08D);
            sl.sendParticles(ModParticles.BARK_CHIP.get(), p.getX(), p.getY() + 1.0D, p.getZ(),
                    6, 0.3D, 0.3D, 0.3D, 0.1D);
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

    // --- grab and smash -----------------------------------------------------------------------------

    /** Whatever is under the tip on the catch tick. The lunge was aimed where the target stood when
     *  it began, so stepping aside during the reach is a dodge. */
    @Nullable
    private LivingEntity findGrabVictim(int t) {
        LivingEntity best = null;
        double bestGap = Double.MAX_VALUE;
        for (int sub = 1; sub >= 0; sub--) {
            Vec3 tip = joint(limbAt(t - sub * 0.5F), WhompingWillowRig.SEGS);
            for (LivingEntity c : victimsNear(BRANCH_RANGE + 1.0D)) {
                if (!canBeGrabbed(c)) continue;
                AABB box = c.getBoundingBox();
                double cy = Mth.clamp(tip.y, box.minY, box.maxY);
                double dx = tip.x - (box.minX + box.maxX) * 0.5D;
                double dz = tip.z - (box.minZ + box.maxZ) * 0.5D;
                double gap = Math.sqrt(dx * dx + dz * dz + (tip.y - cy) * (tip.y - cy))
                        - c.getBbWidth() * 0.5D - GRAB_REACH;
                if (gap <= 0.0D && gap < bestGap) {
                    bestGap = gap;
                    best = c;
                }
            }
        }
        return best;
    }

    private boolean canBeGrabbed(LivingEntity c) {
        // Riders route to SWAT, which dismounts them first -- grabbing a passenger would start an
        // unbounded fight between this entity's teleport and the vehicle's.
        return c.getBbHeight() < 3.0F && !c.isPassenger();
    }

    /** The victim rides the tip of the limb the client is drawing, every tick. The tip touches the
     *  ground on exactly SMASH_BLOW_A and SMASH_BLOW_B (see WhompingWillowRig.smashHeight). */
    private void tickSmash(int t) {
        LivingEntity victim = getHeldVictim();
        if (victim == null) return;
        // Apparition exists in this mod, and pushing a position through a stale connection to a
        // player who has changed dimension is a hard desync.
        if (victim.isRemoved() || victim.level() != this.level()
                || victim.distanceToSqr(this) > 196.0D) {
            dropVictim();
            return;
        }

        Vec3 tip = joint(limbAt(t), WhompingWillowRig.SEGS);
        int gy = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(tip.x), Mth.floor(tip.z));
        // Held by the middle, never pushed below the ground the tip is beating it against.
        double feet = Math.max(gy, tip.y - victim.getBbHeight() * 0.5D);
        Vec3 at = new Vec3(tip.x, feet, tip.z);
        if (t < SMASH_RELEASE_TICK) holdVictimAt(victim, at);

        if (t == SMASH_BLOW_A || t == SMASH_BLOW_B) {
            victim.hurt(this.damageSources().mobAttack(this), damage(1.1F));
            victim.push(0.0D, 0.25D, 0.0D);
            victim.hurtMarked = true;
            if (victim instanceof ServerPlayer sp) ModEvents.interruptActiveCasting(sp);
            playSound(ModSounds.WILLOW_IMPACT.get(), 2.0F, 1.0F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ModParticles.ROOT_DUST.get(), tip.x, gy + 0.3D, tip.z, 10, 0.7D, 0.1D, 0.7D, 0.05D);
                sl.sendParticles(ModParticles.BARK_CHIP.get(), tip.x, gy + 0.6D, tip.z, 6, 0.4D, 0.2D, 0.4D, 0.1D);
                if (t == SMASH_BLOW_A) smashBlocks(sl, BlockPos.containing(tip.x, gy, tip.z));
            }
        }

        if (t >= SMASH_RELEASE_TICK && !this.victimReleased) {
            this.victimReleased = true;
            // A soft toss, never a launch: a willow that flung you out of reach would be ending the
            // fight in your favour.
            Vec3 dir = branchDir(getActiveBranch());
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
                    // TerrainRules last: it refuses inside a Cave Inimicum ward, which the
                    // mobGriefing check above does not cover.
                    BlockState at = level.getBlockState(pos);
                    if (isSmashable(at, pos) && com.hogwartsmagic.spell.TerrainRules.canBreak(level, pos, at, this)) {
                        doomed.add(pos);
                    }
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
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), getX(), getY() + 8.0D, getZ(),
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
        if (flat.length() > 2.9D) return false;   // the bole is ~3.4 wide at the knot
        Vec3 knot = Vec3.directionFromRotation(0.0F,
                Mth.wrapDegrees(getPlantedYaw() + WhompingWillowRig.KNOT_BEARING));
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

        if (this.level() instanceof ServerLevel sl) {
            // Splinters fly from the bole where it was struck; leaves shake down from the crown.
            sl.sendParticles(ModParticles.BARK_CHIP.get(), getX(), getY() + 2.5D, getZ(),
                    (int) Math.min(14.0F, 2.0F + amount * 0.6F), 1.2D, 1.0D, 1.2D, 0.08D);
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), getX(), getY() + 8.0D, getZ(),
                    (int) Math.min(24.0F, 3.0F + amount), 3.0D, 2.0D, 3.0D, 0.02D);
        }

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
        if (this.level() instanceof ServerLevel sl) {
            // The whole canopy comes down with it.
            sl.sendParticles(ModParticles.WILLOW_LEAF.get(), getX(), getY() + 8.0D, getZ(),
                    140, 3.6D, 3.0D, 3.6D, 0.04D);
            sl.sendParticles(ModParticles.BARK_CHIP.get(), getX(), getY() + 3.0D, getZ(),
                    30, 1.4D, 2.0D, 1.4D, 0.1D);
            sl.sendParticles(ModParticles.ROOT_DUST.get(), getX(), getY() + 0.4D, getZ(),
                    40, 3.0D, 0.3D, 3.0D, 0.05D);
        }
        super.die(source);
    }

    /**
     * Not super. LivingEntity broadcasts entity event 60 here, which is vanilla's white "poof": on
     * a twelve-block tree that has just crashed over, a mob's puff of smoke reads as wrong. The
     * felled timber settles into the earth in its own dust and leaves instead.
     */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ModParticles.ROOT_DUST.get(), getX(), getY() + 0.5D, getZ(),
                        50, 4.5D, 0.4D, 4.5D, 0.04D);
                sl.sendParticles(ModParticles.WILLOW_LEAF.get(), getX(), getY() + 1.5D, getZ(),
                        60, 5.0D, 0.8D, 5.0D, 0.03D);
                sl.sendParticles(ModParticles.BARK_CHIP.get(), getX(), getY() + 0.8D, getZ(),
                        20, 3.0D, 0.3D, 3.0D, 0.08D);
            }
            this.remove(RemovalReason.KILLED);
        }
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
        // The box is the trunk only: 3 wide and 9 tall, under a crown that stands over twelve and
        // limbs that reach nine out. Without this the canopy pops out of existence the moment the
        // trunk leaves the frustum.
        return this.getBoundingBox().inflate(10.0D, 4.5D, 10.0D);
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
        this.entityData.set(DATA_AWAKE, tag.getBoolean("Awake"));
        this.entityData.set(DATA_ENRAGED, tag.getBoolean("Enraged"));
        this.angryTicks = tag.getInt("AngryTicks");
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"));
        // The attack state is not persisted, so a tree saved awake reloads DORMANT with the awake flag
        // still set: tickAggro never runs, angryTicks never decays and it idles "awake" forever.
        if (isAwake() && getAttackState() == AttackState.DORMANT) {
            setAttackState(AttackState.NONE);
        }
    }

    /**
     * Spawn rule: solid ground, a bit of sky, and no interest in the time of day.
     *
     * <p>Note what is deliberately <i>absent</i>: a full nine-block clearance test. The spawner
     * already ends with {@code level.noCollision(type.getSpawnAABB(...))}, which tests the whole
     * 3x9 box — so re-checking it here only adds a stricter, redundant filter. An earlier version
     * demanded nine blocks of strict air in the column, which under a forest canopy is essentially
     * never true, and the willow simply never spawned.
     */
    public static boolean canSpawn(EntityType<WhompingWillowEntity> type, ServerLevelAccessor level,
                                   MobSpawnType spawnType, BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) return false;
        // Enough headroom that it is standing in a clearing rather than wedged under the canopy;
        // the engine's own box test does the rest.
        return level.getBlockState(pos.above(4)).isAir() && level.getBlockState(pos.above(7)).isAir();
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
            // Inside the roots' reach only the stomp and the grab can connect: a swung limb passes
            // over anyone pressed against the trunk (the hit test is the limb itself, so this is
            // not a rule, it is geometry -- see HitCheck in the parity harness notes).
            if (d <= STOMP_RANGE) return r < 0.60F ? AttackState.ROOT_STOMP : AttackState.GRAB;
            if (r < 0.50F) return AttackState.SWEEP;
            return r < 0.75F ? AttackState.SLAM : AttackState.GRAB;
        }

        private void begin(AttackState state, Vec3 at) {
            // Parameters first, state second: the model must never see a state without its branch.
            int branch = this.willow.branchFor(at);
            this.willow.entityData.set(DATA_ACTIVE_BRANCH, (byte) branch);
            float off = Mth.wrapDegrees(bearing(at) - branchWorldYaw(branch, this.willow.getPlantedYaw()));
            this.willow.entityData.set(DATA_SWEEP_SIGN, (byte) (off >= 0.0F ? 1 : -1));
            // Aim the limb at where the target stands now. The rig turns and bends the limb to this
            // point, and the hit tests run against that same limb, so moving after the wind-up is
            // a dodge and standing still is not.
            this.willow.entityData.set(DATA_AIM_YAW, Mth.clamp(off * Mth.DEG_TO_RAD,
                    -WhompingWillowRig.MAX_AIM_YAW, WhompingWillowRig.MAX_AIM_YAW));
            this.willow.entityData.set(DATA_AIM_DIST, (float) this.willow.horizontalDistTo(at));
            this.willow.entityData.set(DATA_AIM_HEIGHT, (float) (at.y - this.willow.getY())
                    + (state == AttackState.SWAT ? 0.9F : 0.6F));
            this.willow.setAttackState(state);
        }

        private float bearing(Vec3 at) {
            return (float) (Mth.atan2(at.z - this.willow.getZ(), at.x - this.willow.getX())
                    * Mth.RAD_TO_DEG) - 90.0F;
        }
    }

    /**
     * Which branch owns the bearing to {@code at}: simply the nearest one.
     *
     * <p>This used to divide the compass into quadrants, which only worked while the branches sat
     * on exact multiples of 90. Now that they hang at odd angles the nearest-bearing search is the
     * only thing that keeps selection and geometry in agreement.
     */
    private int branchFor(Vec3 at) {
        float bearing = (float) (Mth.atan2(at.z - getZ(), at.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        int best = 0;
        float bestOff = 360.0F;
        for (int b = 0; b < 4; b++) {
            float off = Math.abs(Mth.wrapDegrees(bearing - branchWorldYaw(b, getPlantedYaw())));
            if (off < bestOff) {
                bestOff = off;
                best = b;
            }
        }
        return best;
    }
}
