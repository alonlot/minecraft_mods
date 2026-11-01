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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A stone knight: a statue at attention until something comes too close, then a slow, heavy
 * fighter that cannot be pushed around.
 *
 * <h2>The states</h2>
 * The mob is a small state machine and every state is meant to be readable from across the room:
 * <ul>
 *   <li><b>Dormant</b> -- no AI, no motion of any kind, standing at attention with the halberd
 *       planted. It is scenery. Two things end it: something hostile coming inside
 *       {@link #WAKE_RADIUS}, or {@link #wake()} being called from outside (which is how the ruin
 *       camp's pair are set off by the chest).</li>
 *   <li><b>Waking</b> -- {@link #WAKE_TICKS} of grinding loose one joint at a time, during which
 *       it does not move or strike. That delay is the point: it is the window the player gets to
 *       realise what is happening and back off.</li>
 *   <li><b>Active</b> -- fights. Slow, strong, immovable, and planted for the whole of a blow.</li>
 *   <li><b>Crumbling</b> -- drops to its knees and its head falls off. A ruin guardian is then
 *       gone; a player-animated sentry reassembles at its post instead (see {@link #hurt}).</li>
 *   <li><b>Reforming</b> -- the sentry's fall run backwards at its post, ending dormant.</li>
 * </ul>
 *
 * <h2>The three kinds</h2>
 * A <i>ruin guardian</i> belongs to no one and hunts players. A <i>bound sentry</i> belongs to the player who
 * raised it and hunts what hunts them. A <i>castle statue</i> ({@link #guardCastle}) belongs to a post in the
 * Mini Hogwarts castle: it wakes only for hostile mobs, never targets or harms a player, and when a player hits
 * it, or a mob would kill it, it crumbles and reforms at its post like a sentry does. It drops nothing, so the
 * castle's statues cannot be farmed.
 *
 * <h2>Why it fights the way it does</h2>
 * Everything about the combat profile says "several tons of granite". Knockback resistance is a
 * flat 1.0, so nothing shoves it -- not a sword, not an explosion, not Depulso. Its own attack
 * knockback is high, so it throws <i>you</i>. It swings on a {@link #ATTACK_COOLDOWN}-tick cycle
 * rather than the usual 20, and hits about three times as hard to pay for it: the fight is meant
 * to be about spacing and timing, not about trading blows. Movement speed is low for the same
 * reason -- you can outrun it, and that is deliberate, because a sentry you cannot escape is a
 * death sentence rather than a threat.
 */
public class LivingStatueEntity extends Monster {

    /** How long the rise takes. Slow on purpose -- this is the tell, not a formality. */
    public static final int WAKE_TICKS = 50;
    /** How long the fall takes when destroyed: knees, then the head. */
    public static final int CRUMBLE_TICKS = 30;
    /** A ruin guardian lies in pieces this long after the fall before it breaks up for good. */
    private static final int RUBBLE_TICKS = 25;
    /** How long a bound sentry takes to reassemble at its post. */
    public static final int REFORM_TICKS = 30;
    /**
     * The wake beats, in ticks: head, hand off the sword, halberd up, the step down. Each starts
     * with a grind of stone; the step lands at the last one. Must match WAKE_BEATS in
     * LivingStatueModel (the animation) -- these are where the sound and dust go.
     */
    private static final int[] WAKE_BEATS = {5, 13, 21, 33};
    private static final int WAKE_LANDING = 46;
    /** A dormant statue notices anything hostile inside this many blocks. */
    public static final double WAKE_RADIUS = 5.0;
    /**
     * The combo: two diagonal cleaves and then a two-handed overhead split that cracks the floor.
     *
     * <p>Each entry is the whole beat -- wind-up, strike, and the stagger afterwards -- so the
     * statue is committed for that long once it starts. The third is longer because it hits
     * harder and takes longer to haul back up, and the recovery is what gives a player the room
     * to punish it. {@link #STRIKE_AT} is where in the beat the blow actually lands, which is
     * deliberately NOT the moment the AI asks to attack: the damage has to arrive with the blade,
     * or a player who backs off during the wind-up still gets hit by a swing that visibly missed.
     */
    public static final int[] SWING_TICKS = {34, 34, 46};
    /** Fraction into a beat at which the blade arrives. Matches the model's strike window. */
    public static final float STRIKE_AT = 0.45F;
    /** Breather after a full three-hit combo, before it can start another. */
    public static final int COMBO_COOLDOWN = 45;
    /**
     * A castle statue that has been awake this many ticks with nothing to fight and nothing hostile in
     * {@link #WAKE_RADIUS} falls apart and reforms dormant at its post (see {@link #standDownWhenIdle}).
     */
    public static final int STAND_DOWN_TICKS = 200;

    private static final EntityDataAccessor<Integer> DATA_WAKE =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CRUMBLE =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    /** True for one the player raised with Piertotum Locomotor; false for a ruin guardian. */
    private static final EntityDataAccessor<Boolean> DATA_BOUND =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.BOOLEAN);
    /** True for a statue posted in the Mini Hogwarts castle; see {@link #guardCastle}. */
    private static final EntityDataAccessor<Boolean> DATA_CASTLE =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.BOOLEAN);
    /** Which blow of the combo is in flight, or -1 for none. Synched: the model draws from it. */
    private static final EntityDataAccessor<Integer> DATA_COMBO =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    /** Ticks into the current blow. Synched for the same reason. */
    private static final EntityDataAccessor<Integer> DATA_SWING =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);
    /** Ticks into reassembling at its post, or 0. */
    private static final EntityDataAccessor<Integer> DATA_REFORM =
            SynchedEntityData.defineId(LivingStatueEntity.class, EntityDataSerializers.INT);

    /**
     * Client side: the tick each counter last arrived from the server. The counters advance one a
     * tick on the server, but packets do not land exactly one a client tick, so reading the raw
     * value plus the partial tick jitters back and forth. Carrying the value forward from when it
     * arrived (at most two ticks, in case a packet is late) is what makes the motion smooth.
     */
    private int wakeSyncTick;
    private int swingSyncTick;
    private int reformSyncTick;

    @Nullable
    private UUID ownerUUID;
    /** Where it was raised. A bound sentry returns here when it is put down. */
    @Nullable
    private BlockPos homePos;
    /** The way a castle statue faces at its post (degrees), or NaN when it keeps whatever it has. */
    private float homeYaw = Float.NaN;
    /** Ticks left before another combo may start. */
    private int comboCooldown;
    /** Set once the current blow's damage has landed, so one swing cannot hit twice. */
    private boolean struck;
    /**
     * Server side, castle statues only: consecutive awake ticks with no live target and no hostile mob near.
     * Deliberately not saved -- a statue unloaded mid-lull simply gets a fresh {@link #STAND_DOWN_TICKS} on
     * load, which is harmless (it still stands down, a little later), where saving it would add a tag for a
     * timer nobody can observe.
     */
    private int idleTicks;

    public LivingStatueEntity(EntityType<? extends LivingStatueEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // It is made of rock, so it wades rather than walking round water. Fire immunity is on the
        // EntityType in ModEntities, where the builder wants it; drowning is left alone, because
        // canBreatheUnderwater is final in 1.21 and the only way to buy it is a tick override that
        // fights the vanilla air counter every tick for a case that never comes up on land.
        this.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                // It throws you; nothing throws it.
                .add(Attributes.ATTACK_KNOCKBACK, 2.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WAKE, 0);
        builder.define(DATA_CRUMBLE, 0);
        builder.define(DATA_BOUND, false);
        builder.define(DATA_CASTLE, false);
        builder.define(DATA_COMBO, -1);
        builder.define(DATA_SWING, 0);
        builder.define(DATA_REFORM, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_WAKE.equals(key)) {
            this.wakeSyncTick = this.tickCount;
        } else if (DATA_SWING.equals(key) || DATA_COMBO.equals(key)) {
            this.swingSyncTick = this.tickCount;
        } else if (DATA_REFORM.equals(key)) {
            this.reformSyncTick = this.tickCount;
        }
    }

    private float since(int syncTick, float partialTick) {
        return Math.min(2, Math.max(0, this.tickCount - syncTick)) + partialTick;
    }

    @Override
    protected void registerGoals() {
        // Every goal is gated on being awake, so a dormant statue runs no AI at all rather than
        // standing still because its goals happen to have nothing to do. That gate has to include
        // the look goal: LookControl drives yHeadRot, BodyRotationControl then drags yBodyRot after
        // it, and a "dormant" statue that swivels its whole body to watch you across ten blocks
        // stops being scenery a long way before it stops being asleep.
        this.goalSelector.addGoal(1, new StatueMeleeGoal(this));
        this.goalSelector.addGoal(8, new StatueLookGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, StudentEntity.class));
        // A ruin guardian hunts players. A bound sentry hunts whatever hunts the player.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, 10, true, false,
                p -> this.isAwake() && !this.isSentry()));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, Mob.class, 10, true, false,
                m -> this.isAwake() && this.isSentry() && this.isHostileMob(m)));
    }

    /**
     * Statues and students are on the same side, always.
     *
     * <p>Enforced here rather than only in the target goals because there are three other ways a
     * mob acquires a target -- retaliation, a goal added later, another mob's revenge chain -- and
     * a rule that only lives in one goal's predicate is a rule that leaks. A bound sentry is
     * knockback-immune with 70 HP and a student cannot meaningfully hurt it back, so a fight
     * between them never resolves; it just runs forever next to the fire.
     *
     * <p>This also covers the ruin guardians, which is deliberate: the camp bakes in four students
     * and two dormant statues a few blocks apart, and the guardians are meant to wake for whoever
     * opens the chest, not to start on the residents.
     */
    @Override
    public boolean canAttack(LivingEntity target) {
        // A castle statue guards the castle for everyone in it: it never takes a player for the enemy.
        return !(target instanceof StudentEntity) && !(this.isCastle() && target instanceof Player)
                && super.canAttack(target);
    }

    // --- state ------------------------------------------------------------------------------

    public int getWakeTicks() {
        return this.entityData.get(DATA_WAKE);
    }

    /** Ticks into the rise, smoothed: 0 dormant, {@link #WAKE_TICKS} once risen. Drives the model. */
    public float getWakeTime(float partialTick) {
        int wake = this.getWakeTicks();
        if (wake <= 0 || wake >= WAKE_TICKS) {
            return wake;
        }
        return Math.min(WAKE_TICKS, wake + this.since(this.wakeSyncTick, partialTick));
    }

    public boolean isAwake() {
        return this.getWakeTicks() >= WAKE_TICKS;
    }

    public boolean isDormant() {
        return this.getWakeTicks() == 0;
    }

    public boolean isCrumbling() {
        return this.entityData.get(DATA_CRUMBLE) > 0;
    }

    /**
     * 0..1 through the fall, for a sentry being put down AND a guardian being destroyed -- the
     * latter runs on the vanilla death clock, which both sides already advance in lockstep.
     */
    public float getCrumbleProgress(float partialTick) {
        int ticks = Math.max(this.entityData.get(DATA_CRUMBLE), this.deathTime);
        if (ticks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (ticks + partialTick) / CRUMBLE_TICKS);
    }

    public boolean isReforming() {
        return this.entityData.get(DATA_REFORM) > 0;
    }

    /** 0..1 through reassembling at its post, or 0 when it is not. */
    public float getReformProgress(float partialTick) {
        int ticks = this.entityData.get(DATA_REFORM);
        if (ticks <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (ticks + this.since(this.reformSyncTick, partialTick)) / REFORM_TICKS);
    }

    public boolean isBound() {
        return this.entityData.get(DATA_BOUND);
    }

    /** Is this one of the Mini Hogwarts castle's own statues? */
    public boolean isCastle() {
        return this.entityData.get(DATA_CASTLE);
    }

    /**
     * Posts this statue in the castle at {@code home}: it wakes only for hostile mobs, hunts only them, and
     * crumbles and reforms at {@code home} instead of fighting a player or dying. Keeps its current facing.
     */
    public void guardCastle(BlockPos home) {
        this.guardCastle(home, this.getYRot());
    }

    /** As {@link #guardCastle(BlockPos)}, and it stands facing {@code yaw} every time it reforms. */
    public void guardCastle(BlockPos home, float yaw) {
        this.homePos = home.immutable();
        this.homeYaw = yaw;
        this.entityData.set(DATA_CASTLE, true);
    }

    /**
     * Does it answer for a post rather than for a master or for nobody? True for a bound sentry and a castle
     * statue: both come back to {@link #getHomePos} when put down, wake for hostile mobs only, and hunt them.
     */
    private boolean isSentry() {
        return this.isBound() || this.isCastle();
    }

    /**
     * A hostile mob a sentry should object to. A castle statue does not count the other statues (they are
     * monsters too) as enemies; a bound sentry's rule is left as it was.
     */
    private boolean isHostileMob(LivingEntity other) {
        return other instanceof Enemy && !(this.isCastle() && other instanceof LivingStatueEntity);
    }

    /** Which blow of the combo is in flight, or -1 if it is not swinging. Drives the model. */
    public int getComboIndex() {
        return this.entityData.get(DATA_COMBO);
    }

    public boolean isSwinging() {
        return this.getComboIndex() >= 0;
    }

    /**
     * How far through the current blow, 0..1, across the strike AND the stagger after it.
     *
     * <p>{@code partialTick} is folded in here rather than left to the model because the beat is
     * only 34 ticks: stepping it a whole tick at a time makes a swing that is meant to accelerate
     * look like it judders through four frames.
     */
    public float getSwingProgress(float partialTick) {
        int index = this.getComboIndex();
        if (index < 0) {
            return 0.0F;
        }
        float total = SWING_TICKS[Math.min(index, SWING_TICKS.length - 1)];
        return Math.min(1.0F, (this.entityData.get(DATA_SWING)
                + this.since(this.swingSyncTick, partialTick)) / total);
    }

    /** Start the rise. Safe to call repeatedly; only the first call does anything. */
    public void wake() {
        if (this.getWakeTicks() == 0 && !this.isCrumbling() && !this.isReforming()) {
            this.entityData.set(DATA_WAKE, 1);
            this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_WAKE.get(),
                    SoundSource.HOSTILE, 1.3F, 1.0F);
        }
    }

    /**
     * A Pensieve memory puppet's state, set by the client each tick from the scene clock
     * ({@code pensieve/scene/StatueCues}): the puppet is never added to the level, so the counters
     * {@link #tick()} would advance never move on their own. {@code combo} is -1 when no blow is in
     * flight. Only ever called on such a puppet, client side; a real statue never reaches it.
     */
    public void setMemoryState(int wake, int crumble, int combo, int swing) {
        this.entityData.set(DATA_WAKE, wake);
        this.entityData.set(DATA_CRUMBLE, crumble);
        this.entityData.set(DATA_COMBO, combo);
        this.entityData.set(DATA_SWING, swing);
    }

    public void bindTo(Player owner, BlockPos home) {
        this.ownerUUID = owner.getUUID();
        this.homePos = home;
        this.entityData.set(DATA_BOUND, true);
    }

    @Nullable
    public BlockPos getHomePos() {
        return this.homePos;
    }

    /**
     * Is {@code entity} the player this was raised by? Compared by UUID rather than by holding a
     * reference: the owner logs out, and the statue outlives them.
     */
    public boolean isOwnedBy(@Nullable net.minecraft.world.entity.Entity entity) {
        return entity != null && this.ownerUUID != null && this.ownerUUID.equals(entity.getUUID());
    }

    // --- ticking ----------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        int wake = this.getWakeTicks();
        if (wake < WAKE_TICKS || this.isReforming()) {
            // Stone does not turn. While it is dormant, rising or reassembling, body and head are
            // pinned to its facing on BOTH sides, the old values included, so there is nothing for
            // the renderer to interpolate between and not a degree of drift. Server-only pinning
            // left the client's own body-rotation smoothing free to nudge it.
            //
            // A statue baked into a structure also needs this: StructureTemplate.placeEntities
            // calls moveTo, which sets yRot only, so after a rotated placement the body would
            // still face the template-local yaw.
            float yaw = this.getYRot();
            this.yBodyRot = this.yBodyRotO = yaw;
            this.yHeadRot = this.yHeadRotO = yaw;
            this.setXRot(0.0F);
            this.xRotO = 0.0F;
        }
        if (this.level().isClientSide) {
            if (!this.isDeadOrDying()) {
                this.clientEffects(wake);
            }
            return;
        }

        if (this.comboCooldown > 0) {
            this.comboCooldown--;
        }
        this.advanceCombo();
        if (this.isDeadOrDying()) {
            // The fall is all a corpse does: no rising on, no sensing intruders, no wake sounds.
            return;
        }

        int reform = this.entityData.get(DATA_REFORM);
        if (reform > 0) {
            this.entityData.set(DATA_REFORM, reform + 1 >= REFORM_TICKS ? 0 : reform + 1);
            if (reform == REFORM_TICKS - 8) {
                this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_RISE.get(),
                        SoundSource.HOSTILE, 1.0F, 0.85F);
            }
            return;
        }

        int crumble = this.entityData.get(DATA_CRUMBLE);
        if (crumble > 0) {
            this.entityData.set(DATA_CRUMBLE, crumble + 1);
            this.crumbleEffects(crumble + 1);
            if (crumble + 1 >= CRUMBLE_TICKS) {
                this.finishCrumble();
            }
            return;
        }

        if (wake == 0) {
            if (this.tickCount % 10 == 0 && this.senseIntruder()) {
                this.wake();
            }
            return;
        }
        if (wake < WAKE_TICKS) {
            this.entityData.set(DATA_WAKE, wake + 1);
            for (int beat : WAKE_BEATS) {
                if (wake == beat) {
                    this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_RISE.get(),
                            SoundSource.HOSTILE, 1.0F, 0.9F + 0.05F * beat / WAKE_BEATS[3]);
                }
            }
            if (wake == WAKE_LANDING) {
                this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_STEP.get(),
                        SoundSource.HOSTILE, 1.6F, 0.8F);
            }
        } else if (this.isCastle()) {
            this.standDownWhenIdle();
        }
    }

    /**
     * A castle statue is scenery that guards: once the fight that woke it is over it goes back to being
     * scenery. After {@link #STAND_DOWN_TICKS} awake ticks with no live target, no swing in flight and no
     * hostile mob inside {@link #WAKE_RADIUS} it takes the same crumble-and-reform path a player's hit does,
     * so it rebuilds itself dormant at its post facing its home yaw. A live target, or an enemy still near
     * (which would only wake it again the moment it reformed), resets the count, so it never stands down
     * mid-fight. Bound sentries and ruin guardians never run this.
     */
    private void standDownWhenIdle() {
        LivingEntity target = this.getTarget();
        boolean engaged = target != null && target.isAlive()
                || this.isSwinging()
                // Sensed every ten ticks like the dormant check; a miss between checks only delays the reset.
                || this.tickCount % 10 == 0 && this.senseIntruder();
        if (engaged) {
            this.idleTicks = 0;
        } else if (++this.idleTicks >= STAND_DOWN_TICKS) {
            this.idleTicks = 0;
            this.beginCrumble();
        }
    }

    /**
     * Grit and dust, client side only, and only when something is actually moving. Dust coming
     * off a dormant statue would give the ambush away -- and dust coming off one standing still
     * reads as a particle bug, not as stone.
     */
    private void clientEffects(int wake) {
        BlockParticleOption grit = new BlockParticleOption(
                ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        if (wake > 0 && wake < WAKE_TICKS) {
            for (int beat : WAKE_BEATS) {
                if (wake == beat) {
                    // A crack of dust off the shoulders as each joint breaks loose.
                    for (int i = 0; i < 10; i++) {
                        this.level().addParticle(grit, this.getRandomX(0.6D),
                                this.getY() + 1.6D + this.random.nextDouble() * 0.9D,
                                this.getRandomZ(0.6D), 0.0D, 0.0D, 0.0D);
                    }
                }
            }
            if (wake == WAKE_LANDING) {
                this.dustRing(grit, 14);
            }
            return;
        }
        boolean moving = this.isAwake() && this.walkAnimation.speed() > 0.15F;
        if ((moving || this.isSwinging()) && this.random.nextInt(4) == 0) {
            this.level().addParticle(grit, this.getRandomX(0.7D),
                    this.getY() + this.random.nextDouble() * 2.2D,
                    this.getRandomZ(0.7D), 0.0D, -0.02D, 0.0D);
        }
    }

    private void dustRing(BlockParticleOption dust, int points) {
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0D) * i / points;
            this.level().addParticle(dust, this.getX() + Math.cos(a) * 0.9D, this.getY() + 0.1D,
                    this.getZ() + Math.sin(a) * 0.9D, Math.cos(a) * 0.08D, 0.02D,
                    Math.sin(a) * 0.08D);
        }
    }

    /**
     * Server: what the fall throws off, timed to the model -- the knees hitting the floor a third
     * of the way in, the head landing at the end. Runs for both a sentry being put down and a
     * guardian being destroyed.
     */
    private void crumbleEffects(int tick) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        BlockParticleOption stone = new BlockParticleOption(
                ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        if (tick == 10) {
            server.sendParticles(stone, this.getX(), this.getY() + 0.3D, this.getZ(),
                    40, 0.6D, 0.2D, 0.6D, 0.08D);
            server.playSound(null, this.blockPosition(), ModSounds.STATUE_SLAM.get(),
                    SoundSource.HOSTILE, 0.9F, 1.2F);
        } else if (tick == CRUMBLE_TICKS - 2) {
            server.sendParticles(stone, this.getX(), this.getY() + 0.25D, this.getZ(),
                    25, 0.8D, 0.15D, 0.8D, 0.05D);
            server.playSound(null, this.blockPosition(), ModSounds.STATUE_IMPACT.get(),
                    SoundSource.HOSTILE, 0.8F, 1.3F);
        }
    }

    /** Is something it should object to standing inside {@link #WAKE_RADIUS}? */
    private boolean senseIntruder() {
        AABB box = this.getBoundingBox().inflate(WAKE_RADIUS);
        if (this.isSentry()) {
            return !this.level().getEntitiesOfClass(Mob.class, box,
                    m -> this.isHostileMob(m) && m.isAlive() && m != this).isEmpty();
        }
        return this.level().getEntitiesOfClass(Player.class, box,
                p -> !p.isSpectator() && !p.isCreative()).stream().findAny().isPresent();
    }

    /**
     * A dormant statue is stone, and stone does not stagger, drift, or fall over. Freezing the
     * whole physics step is the only way to get that: left to the normal path it slides on ice,
     * gets pushed by water, and rocks under attack, all of which break the illusion instantly.
     */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (!this.isAwake() || this.isCrumbling()) {
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            super.travel(net.minecraft.world.phys.Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity other) {
        // It pushes through things; nothing pushes it.
    }

    /**
     * Stone among stone. A statue placed against a wall or under a low lintel has its head in a
     * block, and suffocation damage there flashed it red and woke it every half second -- the
     * "glitching" dormant statue. Drowning is the same story for one that wades.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)
                || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Checked here, before anything below can wake it: vanilla only tests immunity inside
        // super.hurt, by which point the wake() further down has already run -- and a statue in
        // a wall took no damage yet woke every half second all the same.
        // Server only: Player.attack calls hurt on the client too, and wake() there would write
        // the client's copy of the state for a hit the server may yet refuse.
        if (this.level().isClientSide) {
            return false;
        }
        boolean absolute = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        if ((this.isCrumbling() || this.isReforming()) && !absolute
                || this.isInvulnerableTo(source)) {
            return false;
        }
        // Its own caster cannot hurt it, and so cannot start a fight with it. Without this,
        // HurtByTargetGoal makes one stray Confringo or one curious punch turn a 70 HP sentry
        // that cannot be outrun and cannot be killed onto the player who raised it -- and since a
        // bound statue re-forms at full health, that fight has no end. Refusing the damage costs
        // the owner nothing they could otherwise do: lethal damage never destroyed one anyway.
        if (this.isBound() && this.isOwnedBy(source.getEntity())) {
            return false;
        }
        // A castle statue never fights a player. Whatever a player hits it with (a fist, a blade, an arrow) it
        // falls apart and goes back to its post, and the hit costs it nothing: the castle's statues are
        // scenery that guards, not a mob to be killed for loot. Checked before the wake below, which a hit
        // would otherwise start.
        if (this.isCastle() && source.getEntity() instanceof Player
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.beginCrumble();
            return true;
        }
        // Hitting a sleeping statue is a perfectly good way to find out what it is.
        boolean wasDormant = this.isDormant();
        if (wasDormant && !source.isCreativePlayer()) {
            this.wake();
        }
        // Stone shrugs off arrows and takes very little from anything thrown at it, but a
        // pickaxe-shaped answer (any player melee) lands normally.
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            amount *= 0.35F;
        }
        if (!this.level().isClientSide && this.isSentry()
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && this.getHealth() - amount <= 0.0F) {
            // A bound sentry is never destroyed by damage. It is put down, and it comes back at
            // the post it was raised on -- which is what "goes back to where it was created"
            // means mechanically, and what stops a player losing the statue heart they spent.
            //
            // BYPASSES_INVULNERABILITY is let through on purpose: /kill and falling out of the
            // world route here too, and a sentry that shrugs those off is an entity an admin
            // cannot remove from their own server.
            //
            // The comparison is against the RAW amount, before armour, so a big enough blow puts
            // it down a hit sooner than its 14 armour says it should. That is the safe direction
            // and the only cheap one: the alternative is to let super.hurt run and revive it
            // afterwards, by which point LivingEntity has already called die() and rolled the
            // entity loot table -- which would hand the player back the heart they spent.
            this.setHealth(1.0F);
            this.beginCrumble();
            return true;
        }
        return super.hurt(source, amount);
    }

    /**
     * The slow swing, enforced here rather than by widening MeleeAttackGoal's interval.
     *
     * <p>MeleeAttackGoal's cooldown hook has moved between versions (it has been both
     * {@code getAttackInterval()} and {@code getAttackInterval(LivingEntity)}), and overriding a
     * signature that does not exist fails at compile time for a reason that reads as unrelated.
     * {@code doHurtTarget} has been stable throughout, so the cooldown lives on this side of the
     * fence: the goal may ask to swing as often as it likes, and only one in
     * {@link #ATTACK_COOLDOWN} ticks actually lands. That also gives the model a clean signal to
     * animate against.
     */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (this.isSwinging() || this.comboCooldown > 0 || !this.isAwake() || this.isCrumbling()) {
            return false;
        }
        // Deliberately NO damage here. This only opens the combo; every blow lands from
        // advanceCombo at its own strike frame, so the hit arrives with the blade instead of on
        // the frame the AI happened to ask. MeleeAttackGoal resets its own 20-tick cooldown
        // whatever this returns, and StatueMeleeGoal holds that timer still while a combo runs,
        // so the real pause after a combo is COMBO_COOLDOWN plus that 20.
        this.beginSwing(0);
        return true;
    }

    /**
     * Runs the three-blow combo on its own clock.
     *
     * <p>Once it starts it finishes, whether or not the target is still standing there. A combo
     * that re-checks its victim between blows stutters to a halt the moment the player steps
     * back -- which is precisely when a committed wind-up should be leaving it open.
     */
    private void advanceCombo() {
        int index = this.getComboIndex();
        if (index < 0) {
            return;
        }
        if (this.isCrumbling() || !this.isAwake() || this.isDeadOrDying()) {
            this.endCombo();
            return;
        }
        // Planted for the whole blow. Sliding along the floor while the halberd comes round is
        // exactly what makes a heavy swing read as weightless.
        this.getNavigation().stop();
        int tick = this.entityData.get(DATA_SWING) + 1;
        this.entityData.set(DATA_SWING, tick);
        int total = SWING_TICKS[Math.min(index, SWING_TICKS.length - 1)];
        if (!this.struck && tick >= total * STRIKE_AT) {
            this.struck = true;
            this.performStrike(index);
        }
        if (tick >= total) {
            if (index + 1 < SWING_TICKS.length) {
                this.beginSwing(index + 1);
            } else {
                this.endCombo();
                this.comboCooldown = COMBO_COOLDOWN;
            }
        }
    }

    private void beginSwing(int index) {
        this.entityData.set(DATA_COMBO, index);
        this.entityData.set(DATA_SWING, 0);
        this.struck = false;
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        // The wind-up is audible before it is dangerous: stone grinding as it hauls the halberd
        // up. Pitched lower for the heavy third so the player can hear which one is coming.
        this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_SWING.get(),
                SoundSource.HOSTILE, 1.2F, index == 2 ? 0.82F : 1.0F);
    }

    private void endCombo() {
        this.entityData.set(DATA_COMBO, -1);
        this.entityData.set(DATA_SWING, 0);
        this.struck = false;
    }

    /**
     * The blade arrives.
     *
     * <p>The cleaves sweep an arc in FRONT of the statue rather than hitting only the one entity
     * the AI picked -- a halberd that connects with a single target reads as a punch. The
     * overhead split skips the facing test on purpose: it is a shockwave, not a swing.
     */
    private void performStrike(int index) {
        boolean heavy = index == 2;
        double reach = heavy ? 4.5D : 3.8D;
        AABB box = this.getBoundingBox().inflate(reach, heavy ? 2.5D : 1.5D, reach);
        net.minecraft.world.phys.Vec3 facing =
                net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, this.yBodyRot);
        for (LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, box,
                this::canStrike)) {
            if (!heavy) {
                net.minecraft.world.phys.Vec3 towards =
                        victim.position().subtract(this.position()).normalize();
                if (facing.dot(towards) < 0.2D) {
                    continue;
                }
            }
            super.doHurtTarget(victim);
        }
        this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_IMPACT.get(),
                SoundSource.HOSTILE, heavy ? 1.7F : 1.1F, heavy ? 0.82F : 1.0F);
        if (heavy) {
            this.groundSlam();
        }
    }

    /** Never the caster, never a student, never another statue. Mirrors {@link #canAttack}. */
    private boolean canStrike(LivingEntity other) {
        return other != this
                && other.isAlive()
                && !(other instanceof LivingStatueEntity)
                && !(other instanceof StudentEntity)
                && !(this.isBound() && this.isOwnedBy(other))
                && !(this.isCastle() && other instanceof Player); // nor a player standing near the fight
    }

    /**
     * The floor takes it.
     *
     * <p>1.21 has no camera-shake hook, so the shake is sold three ways at once: rings of dust
     * torn off whatever it is actually standing on, a low crack that carries further than the
     * swing sounds, and a shove that lifts everything nearby off its feet. The last one is the
     * part a player feels rather than sees, and it is why the push has an upward component --
     * a purely horizontal knockback reads as being hit, not as the ground moving.
     */
    private void groundSlam() {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        // The rings spread from where the blade bites, not from the statue's feet: the halberd
        // lands a good two blocks out in front.
        net.minecraft.world.phys.Vec3 facing =
                net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, this.yBodyRot);
        double cx = this.getX() + facing.x * 2.2D;
        double cz = this.getZ() + facing.z * 2.2D;
        net.minecraft.world.level.block.state.BlockState floor = server.getBlockState(
                BlockPos.containing(cx, this.getY() - 0.5D, cz));
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK,
                floor.isAir() ? Blocks.STONE.defaultBlockState() : floor);
        for (int ring = 1; ring <= 3; ring++) {
            double radius = ring * 1.2D;
            int points = 10 * ring;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0D) * i / points;
                server.sendParticles(dust, cx + Math.cos(a) * radius,
                        this.getY() + 0.15D, cz + Math.sin(a) * radius,
                        2, 0.1D, 0.05D, 0.1D, 0.12D);
            }
        }
        server.playSound(null, this.blockPosition(), ModSounds.STATUE_SLAM.get(),
                SoundSource.HOSTILE, 1.3F, 1.0F);
        for (LivingEntity shaken : server.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(7.0D), this::canStrike)) {
            double dx = shaken.getX() - this.getX();
            double dz = shaken.getZ() - this.getZ();
            double dist = Math.max(0.4D, Math.sqrt(dx * dx + dz * dz));
            double falloff = 1.0D - Math.min(1.0D, dist / 7.0D);
            shaken.push(dx / dist * falloff * 0.55D, 0.30D * falloff + 0.10D,
                    dz / dist * falloff * 0.55D);
            // Without this the server never tells the client its velocity changed, and a player
            // standing in the blast simply does not move.
            shaken.hurtMarked = true;
        }
    }

    /**
     * Forget the fight for good. setTarget(null) alone does not stick: a running target goal
     * falls back on the target it stored and sets it again next tick, so a sentry put down
     * mid-fight would reassemble at its post still locked on the old mob.
     */
    private void dropTarget() {
        this.targetSelector.getAvailableGoals().stream()
                .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).toList()
                .forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        this.goalSelector.getAvailableGoals().stream()
                .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).toList()
                .forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        this.setTarget(null);
    }

    private void beginCrumble() {
        this.entityData.set(DATA_CRUMBLE, 1);
        this.idleTicks = 0;
        this.dropTarget();
        this.level().playSound(null, this.blockPosition(), ModSounds.STATUE_CRUMBLE.get(),
                SoundSource.HOSTILE, 1.5F, 1.0F);
    }

    /**
     * End of the fall: the pieces vanish in a burst of grit and reassemble at its post, then it
     * stands dormant again at full strength.
     */
    private void finishCrumble() {
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(
                            ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    this.getX(), this.getY() + 0.6D, this.getZ(), 50, 0.7D, 0.4D, 0.7D, 0.05D);
        }
        this.entityData.set(DATA_CRUMBLE, 0);
        this.entityData.set(DATA_WAKE, 0);
        this.endCombo();
        this.setHealth(this.getMaxHealth());
        this.dropTarget();
        this.getNavigation().stop();
        if (this.homePos != null) {
            this.teleportTo(this.homePos.getX() + 0.5D, this.homePos.getY(),
                    this.homePos.getZ() + 0.5D);
        }
        if (this.isCastle() && !Float.isNaN(this.homeYaw)) {
            // It stood at attention facing the way the castle's builder posed it; turning up anywhere else
            // would put its back to the room.
            this.setYRot(this.homeYaw);
            this.yRotO = this.homeYaw;
            this.yBodyRot = this.yBodyRotO = this.homeYaw;
            this.yHeadRot = this.yHeadRotO = this.homeYaw;
        }
        this.entityData.set(DATA_REFORM, 1);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(
                            ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    this.getX(), this.getY() + 0.5D, this.getZ(), 30, 0.6D, 0.3D, 0.6D, 0.04D);
        }
    }

    /**
     * A ruin guardian's death IS the fall: knees, head, then a moment as rubble before it breaks
     * up. Replaces vanilla's 20-tick red topple-over and puff of smoke, which read as a mob dying,
     * not as a statue being smashed. The model reads {@code deathTime} (see
     * {@link #getCrumbleProgress}); the renderer skips the death roll.
     */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.level().isClientSide()) {
            return;
        }
        this.crumbleEffects(this.deathTime);
        if (this.deathTime >= CRUMBLE_TICKS + RUBBLE_TICKS && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel server) {
                server.sendParticles(new BlockParticleOption(
                                ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        this.getX(), this.getY() + 0.4D, this.getZ(), 70, 0.8D, 0.3D, 0.8D, 0.1D);
                server.playSound(null, this.blockPosition(), ModSounds.STATUE_CRUMBLE.get(),
                        SoundSource.HOSTILE, 1.0F, 1.2F);
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    /** A castle statue leaves nothing behind, not even when a command kills it. */
    @Override
    protected boolean shouldDropLoot() {
        return !this.isCastle() && super.shouldDropLoot();
    }

    @Override
    public boolean shouldDropExperience() {
        return !this.isCastle() && super.shouldDropExperience();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /**
     * Peaceful does not clear these away. {@code Mob.checkDespawn} tests this <i>before</i> it
     * tests {@code isPersistenceRequired}, so the flag the constructor sets is no protection at
     * all: on Peaceful a sentry would be discarded the tick after it was raised, having already
     * eaten the player's statue heart and five blocks of stone, and discarding is not a death, so
     * nothing would drop back.
     */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** Dormant statues make no sound at all -- an idle groan gives the ambush away. */
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return this.isAwake() ? ModSounds.STATUE_AMBIENT.get() : null;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.STATUE_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return ModSounds.STATUE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(ModSounds.STATUE_STEP.get(), 0.9F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Wake", this.getWakeTicks());
        // Mid-fall and mid-reassembly are saved too, or a sentry unloaded in that window comes
        // back awake at 1 HP away from its post instead of finishing the trip home.
        tag.putInt("Crumble", this.entityData.get(DATA_CRUMBLE));
        tag.putInt("Reform", this.entityData.get(DATA_REFORM));
        tag.putBoolean("Bound", this.isBound());
        tag.putBoolean("Castle", this.isCastle());
        if (!Float.isNaN(this.homeYaw)) {
            tag.putFloat("HomeYaw", this.homeYaw);
        }
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
        if (this.homePos != null) {
            tag.putIntArray("Home", new int[]{
                    this.homePos.getX(), this.homePos.getY(), this.homePos.getZ()});
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_WAKE, tag.getInt("Wake"));
        this.entityData.set(DATA_CRUMBLE, tag.getInt("Crumble"));
        this.entityData.set(DATA_REFORM, tag.getInt("Reform"));
        this.entityData.set(DATA_BOUND, tag.getBoolean("Bound"));
        this.entityData.set(DATA_CASTLE, tag.getBoolean("Castle"));
        if (tag.contains("HomeYaw")) {
            this.homeYaw = tag.getFloat("HomeYaw");
        }
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
        int[] home = tag.getIntArray("Home");
        if (home.length == 3) {
            this.homePos = new BlockPos(home[0], home[1], home[2]);
        }
    }

    /**
     * Melee on a long cycle, and it will not start swinging until it is fully risen.
     *
     * <p>Extending MeleeAttackGoal only to widen the interval is deliberate: the goal already
     * handles pathing, reach and line of sight correctly, and reimplementing that to change one
     * number is how you end up with a mob that hits through walls.
     */
    private static class StatueMeleeGoal extends MeleeAttackGoal {
        private final LivingStatueEntity statue;

        StatueMeleeGoal(LivingStatueEntity statue) {
            super(statue, 1.0D, true);
            this.statue = statue;
        }

        @Override
        public boolean canUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canContinueToUse();
        }

        /**
         * Mid-blow the goal stops steering. Through the wind-up the statue turns slowly to keep
         * the target in front of it; from the strike on it is locked where the blow went, so a
         * player who side-steps a committed swing is not tracked round by the blade.
         */
        @Override
        public void tick() {
            if (!this.statue.isSwinging()) {
                super.tick();
                return;
            }
            LivingEntity target = this.statue.getTarget();
            if (target != null && this.statue.getSwingProgress(0.0F) < STRIKE_AT * 0.7F) {
                this.statue.getLookControl().setLookAt(target, 6.0F, 30.0F);
            }
        }

    }

    /**
     * Tracks the player with its head, but only once it is on its feet.
     *
     * <p>The gate cannot be a constructor argument -- LookAtPlayerGoal takes a probability, not a
     * predicate -- so it has to be a subclass, the same way the melee goal is one.
     */
    private static class StatueLookGoal extends LookAtPlayerGoal {
        private final LivingStatueEntity statue;

        StatueLookGoal(LivingStatueEntity statue) {
            super(statue, Player.class, 10.0F);
            this.statue = statue;
        }

        @Override
        public boolean canUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.statue.isAwake() && !this.statue.isCrumbling() && super.canContinueToUse();
        }
    }
}
