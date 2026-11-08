package com.hogwartsmagic.entity;

import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.fx.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The mountain troll. It closes on foot, picks one of three club attacks by how far away you are, and turns
 * to follow you only until the swing is committed, so the blows can be sidestepped and land where the
 * club comes down.
 *
 * <p>Wingardium Leviosa on a troll holding its club takes the club instead of the troll: it hangs over the
 * troll's head, drops on it and knocks it flat for a few seconds.
 */
public class TrollEntity extends Monster {

    public enum AttackState {
        NONE, ROAR, SLAM_WINDUP, SLAM_STRIKE, SWEEP, STAGGER, BACKHAND, DISARMED, KNOCKED_OUT, GET_UP;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
            SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOLDING_CLUB =
            SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.BOOLEAN);

    // State timings (ticks). The "strike" frame inside SLAM_STRIKE is at the start of that state.
    public static final int ROAR_DURATION = 36;
    public static final int SLAM_WINDUP_DURATION = 18;
    public static final int SLAM_STRIKE_DURATION = 16;
    public static final int SWEEP_DURATION = 22;
    public static final int SWEEP_STRIKE_TICK = 12;
    public static final int STAGGER_DURATION = 14;
    public static final int BACKHAND_DURATION = 16;
    public static final int BACKHAND_STRIKE_TICK = 7;
    /** The clips' lengths, and the tick the falling club lands on the troll's head. */
    public static final int DISARMED_DURATION = 52;
    public static final int BONK_TICK = 42;
    public static final int KNOCKED_OUT_DURATION = 120;
    public static final int GET_UP_DURATION = 30;
    public static final int RETRIEVE_CLUB_TICK = 14;

    /** How far in front of the troll the club head lands at the bottom of the slam. Measured from the model. */
    public static final double SLAM_REACH = 2.75D;
    /** Full force within this distance of where the club lands; beyond it the shock wave fades out. */
    private static final double SLAM_CORE = 1.6D;
    private static final double SLAM_RADIUS = 3.6D;
    private static final double SWEEP_RANGE = 4.2D;
    private static final double BACKHAND_RANGE = 3.8D;
    private static final double ATTACK_RANGE = 4.2D;
    /** Closer than this the overhead swing is too slow to bother with. */
    private static final double CLOSE_RANGE = 3.0D;
    /** Degrees per tick the troll can turn while it winds up. */
    private static final float WINDUP_TURN = 9.0F;
    private static final float RAGE_HP_FRACTION = 0.40F;
    private static final float BONK_DAMAGE = 25.0F;
    private static final float KNOCKED_OUT_DAMAGE_MULTIPLIER = 1.5F;
    /** Ticks after it wakes before the same trick works again. */
    private static final int PLUCK_COOLDOWN = 600;

    private boolean hasRoared;
    private int stateTick;
    private long nextPluckTime;
    @Nullable
    private UUID plucker;

    // Client-side clip drivers. These live here rather than in the renderer because
    // AnimationState is a common class; nothing on this side of the wire pulls a
    // client-only type onto a dedicated server.
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState roarAnimationState = new AnimationState();
    public final AnimationState slamWindupAnimationState = new AnimationState();
    public final AnimationState slamStrikeAnimationState = new AnimationState();
    public final AnimationState sweepAnimationState = new AnimationState();
    public final AnimationState staggerAnimationState = new AnimationState();
    public final AnimationState backhandAnimationState = new AnimationState();
    public final AnimationState disarmedAnimationState = new AnimationState();
    public final AnimationState knockedOutAnimationState = new AnimationState();
    public final AnimationState getUpAnimationState = new AnimationState();

    public TrollEntity(EntityType<? extends TrollEntity> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.FOLLOW_RANGE, 34.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.STEP_HEIGHT, 1.1D);
    }

    public static boolean canSpawn(EntityType<TrollEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_STATE, (byte) AttackState.NONE.ordinal());
        builder.define(DATA_RAGING, false);
        builder.define(DATA_HOLDING_CLUB, true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OutColdGoal(this));
        this.goalSelector.addGoal(2, new TrollAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.75D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }

    public AttackState getAttackState() {
        return AttackState.fromByte(this.entityData.get(DATA_ATTACK_STATE));
    }

    public boolean isRaging() {
        return this.entityData.get(DATA_RAGING);
    }

    public boolean isHoldingClub() {
        return this.entityData.get(DATA_HOLDING_CLUB);
    }

    /** Disarmed, flat out or still climbing back up: nothing else may start until it is on its feet. */
    public boolean isIncapacitated() {
        AttackState s = getAttackState();
        return s == AttackState.DISARMED || s == AttackState.KNOCKED_OUT || s == AttackState.GET_UP;
    }

    private boolean isSwinging() {
        AttackState s = getAttackState();
        return s == AttackState.SLAM_WINDUP || s == AttackState.SLAM_STRIKE || s == AttackState.SWEEP || s == AttackState.BACKHAND;
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.stateTick = 0;
    }

    private boolean isBusy() {
        AttackState s = getAttackState();
        return s != AttackState.NONE && s != AttackState.STAGGER;
    }

    /** How much of the look direction the head follows: the clips own the head while they throw it about. */
    public float headTrackingWeight() {
        AttackState s = getAttackState();
        if (s == AttackState.NONE) {
            return 0.7F;
        }
        return isIncapacitated() ? 0.0F : 0.2F;
    }

    private boolean canMoveWhileAttacking() {
        AttackState s = getAttackState();
        // Locked in place during windups/strikes/roar; stagger also halts.
        return s == AttackState.NONE;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (isIncapacitated()) {
                this.idleAnimationState.stop();
            } else {
                this.idleAnimationState.startIfStopped(this.tickCount);
            }
            // The server never sends body yaw and the client body only trails the head, so draw the swing
            // along the facing the blow actually resolves on.
            if (isSwinging()) {
                this.yBodyRot = getYRot();
            }
        }

        // A dying troll plays out its death, not the rest of its swing.
        if (!this.level().isClientSide && isAlive()) {
            // Advance per-state tick.
            AttackState state = getAttackState();
            int t = this.stateTick++;

            // Rage threshold (one-way latch).
            if (!isRaging() && this.getHealth() <= this.getMaxHealth() * RAGE_HP_FRACTION) {
                this.entityData.set(DATA_RAGING, true);
                this.playSound(com.hogwartsmagic.ModSounds.TROLL_RAGE.get(), 1.5F, 0.95F);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                            this.getX(), this.getY() + this.getBbHeight() * 0.9D, this.getZ(),
                            14, 0.6D, 0.4D, 0.6D, 0.02D);
                }
            }

            // Drive state transitions.
            switch (state) {
                case ROAR -> {
                    if (t == 4) {
                        this.playSound(com.hogwartsmagic.ModSounds.TROLL_ROAR.get(), 1.6F, 1.0F);
                        if (this.level() instanceof ServerLevel sl) {
                            com.hogwartsmagic.fx.CreatureFx.rumble(sl, this.position(), 0.5F, 20.0F, 36);
                        }
                    }
                    if (t >= ROAR_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case SLAM_WINDUP -> {
                    if (t == 3) {
                        this.playSound(com.hogwartsmagic.ModSounds.TROLL_SLAM_WINDUP.get(), 1.1F, 1.0F);
                    }
                    if (t <= SLAM_WINDUP_DURATION - 6) {
                        turnToTarget();
                    }
                    if (t >= SLAM_WINDUP_DURATION) {
                        setAttackState(AttackState.SLAM_STRIKE);
                        doSlamImpact();
                    }
                }
                case SLAM_STRIKE -> {
                    if (t >= SLAM_STRIKE_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case SWEEP -> {
                    // The whoosh leads the hit box by half the swing, so you hear it
                    // coming rather than at the moment it lands.
                    if (t == 2) {
                        this.playSound(com.hogwartsmagic.ModSounds.TROLL_SWEEP.get(), 1.3F, 1.0F);
                    }
                    if (t <= SWEEP_STRIKE_TICK - 5) {
                        turnToTarget();
                    }
                    if (t == SWEEP_STRIKE_TICK) {
                        doSweepImpact();
                    }
                    if (t >= SWEEP_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case BACKHAND -> {
                    if (t <= BACKHAND_STRIKE_TICK - 3) {
                        turnToTarget();
                    }
                    if (t == BACKHAND_STRIKE_TICK) {
                        doBackhandImpact();
                    }
                    if (t >= BACKHAND_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case STAGGER -> {
                    if (t >= STAGGER_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case DISARMED -> {
                    if (t == BONK_TICK) {
                        doBonk();
                    }
                    if (t == DISARMED_DURATION - 3 && this.level() instanceof ServerLevel sl) {
                        this.playSound(com.hogwartsmagic.ModSounds.TROLL_SLAM_IMPACT.get(), 1.4F, 0.8F);
                        com.hogwartsmagic.fx.CreatureFx.slam(sl, this.position(), 2.5F, 0.7F, 0.8F, 10.0F, 8);
                    }
                    if (t >= DISARMED_DURATION) {
                        setAttackState(AttackState.KNOCKED_OUT);
                    }
                }
                case KNOCKED_OUT -> {
                    if (t % 40 == 20) {
                        // Snoring.
                        this.playSound(com.hogwartsmagic.ModSounds.TROLL_AMBIENT.get(), 0.9F, 0.6F);
                    }
                    if (t % 5 == 0 && this.level() instanceof ServerLevel sl) {
                        Vec3 back = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(-3.0D);
                        sl.sendParticles(ParticleTypes.CRIT, this.getX() + back.x, this.getY() + 1.2D, this.getZ() + back.z,
                                4, 0.5D, 0.15D, 0.5D, 0.05D);
                    }
                    if (t >= KNOCKED_OUT_DURATION) {
                        this.nextPluckTime = this.level().getGameTime() + PLUCK_COOLDOWN;
                        setAttackState(AttackState.GET_UP);
                    }
                }
                case GET_UP -> {
                    if (t == RETRIEVE_CLUB_TICK) {
                        this.entityData.set(DATA_HOLDING_CLUB, true);
                        this.playSound(com.hogwartsmagic.ModSounds.TROLL_SLAM_WINDUP.get(), 0.9F, 0.8F);
                    }
                    if (t >= GET_UP_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case NONE -> { /* idle */ }
            }

            // Lock movement during committed actions.
            if (!canMoveWhileAttacking()) {
                this.getNavigation().stop();
                Vec3 v = this.getDeltaMovement();
                this.setDeltaMovement(v.x * 0.2D, v.y, v.z * 0.2D);
            }
        }
    }

    /** Swings track the target only until they are committed; after that you can step out of the way. */
    private void turnToTarget() {
        LivingEntity target = getTarget();
        if (target == null) {
            return;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), wanted, WINDUP_TURN);
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    private void doSlamImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        // Where the club head comes down, at ground level.
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double cx = this.getX() + forward.x * SLAM_REACH;
        double cy = this.getY();
        double cz = this.getZ() + forward.z * SLAM_REACH;

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.75F : 1.5F);
        AABB area = new AABB(cx - SLAM_RADIUS, cy - 1.0D, cz - SLAM_RADIUS,
                cx + SLAM_RADIUS, cy + 2.5D, cz + SLAM_RADIUS);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive());
        for (LivingEntity victim : victims) {
            double dx = victim.getX() - cx;
            double dz = victim.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > SLAM_RADIUS) continue;
            // Under the club it is the whole blow; further out, only the ground jumping.
            float force = dist <= SLAM_CORE ? 1.0F
                    : Mth.lerp((float) ((dist - SLAM_CORE) / (SLAM_RADIUS - SLAM_CORE)), 0.7F, 0.3F);
            victim.hurt(this.damageSources().mobAttack(this), dmg * force);
            Vec3 away = dist < 1.0E-3D ? forward : new Vec3(dx / dist, 0.0D, dz / dist);
            victim.push(away.x * 1.1D * force, 0.55D * force, away.z * 1.1D * force);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }

        // Visuals + audio.
        this.playSound(com.hogwartsmagic.ModSounds.TROLL_SLAM_IMPACT.get(), 1.8F, 1.0F);
        // The club comes down: the ground jumps, dust rolls out, chunks fly, the view shudders.
        com.hogwartsmagic.fx.CreatureFx.slam(sl, new Vec3(cx, cy, cz), (float) SLAM_RADIUS, 1.0F, 1.2F, 14.0F, 14);
    }

    private void doSweepImpact() {
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.15F : 0.95F);
        if (swingAt(SWEEP_RANGE, 0.35D, dmg, 1.4D, 0.35D, false) && this.level() instanceof ServerLevel sl) {
            this.playSound(com.hogwartsmagic.ModSounds.TROLL_CLUB_HIT.get(), 1.3F, 0.95F);
            Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
            Fx.shake(sl, this.getX() + forward.x * 2.0D, this.getY() + 1.0D, this.getZ() + forward.z * 2.0D,
                    0.6F, 10.0F, 9);
        }
    }

    private void doBackhandImpact() {
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8F;
        if (swingAt(BACKHAND_RANGE, 0.5D, dmg, 1.35D, 0.22D, true) && this.level() instanceof ServerLevel sl) {
            this.playSound(com.hogwartsmagic.ModSounds.TROLL_CLUB_HIT.get(), 1.2F,
                    0.95F + this.random.nextFloat() * 0.12F);
            Fx.shake(sl, this.getX(), this.getY() + 1.0D, this.getZ(), 0.45F, 8.0F, 8);
        }
    }

    /** Hurts what is inside a forward cone; a backhand stops at its first victim. True if anything was hit. */
    private boolean swingAt(double range, double minDot, float damage, double push, double lift, boolean single) {
        if (!(this.level() instanceof ServerLevel sl)) return false;
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        forward = new Vec3(forward.x, 0.0D, forward.z).normalize();
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range, 1.5D, range),
                e -> e != this && e.isAlive());
        victims.sort(Comparator.comparingDouble(this::distanceToSqr));
        boolean connected = false;
        for (LivingEntity victim : victims) {
            Vec3 flat = new Vec3(victim.getX() - getX(), 0.0D, victim.getZ() - getZ());
            double reach = range + victim.getBbWidth() * 0.5D;
            if (flat.lengthSqr() > reach * reach || flat.lengthSqr() < 1.0E-4D) continue;
            if (flat.normalize().dot(forward) < minDot) continue;
            victim.hurt(this.damageSources().mobAttack(this), damage);
            Vec3 away = flat.normalize();
            victim.push(away.x * push, lift, away.z * push);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
            connected = true;
            if (single) break;
        }
        return connected;
    }

    /**
     * Wingardium Leviosa, on a troll that still has its club: the charm takes the club, not the troll.
     * False when there is no club to take, so the spell falls back to lifting the troll.
     */
    public boolean pluckClub(@Nullable LivingEntity caster) {
        if (this.level().isClientSide || !isAlive() || !isHoldingClub() || isIncapacitated()
                || this.level().getGameTime() < this.nextPluckTime) {
            return false;
        }
        this.plucker = caster == null ? null : caster.getUUID();
        this.entityData.set(DATA_HOLDING_CLUB, false);
        setAttackState(AttackState.DISARMED);
        return true;
    }

    /** The club comes down on the troll's own skull. */
    private void doBonk() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Player caster = this.plucker == null ? null : sl.getPlayerByUUID(this.plucker);
        this.invulnerableTime = 0;
        hurt(caster != null ? damageSources().indirectMagic(caster, caster) : damageSources().magic(), BONK_DAMAGE);
        this.playSound(com.hogwartsmagic.ModSounds.TROLL_CLUB_HIT.get(), 1.6F, 0.7F);
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        sl.sendParticles(ParticleTypes.CRIT, getX() + forward.x, getY() + 3.5D, getZ() + forward.z,
                24, 0.4D, 0.3D, 0.4D, 0.25D);
        Fx.shake(sl, getX(), getY() + 2.0D, getZ(), 0.5F, 10.0F, 8);
    }

    private void interruptIfCaster(LivingEntity victim) {
        if (victim instanceof ServerPlayer sp) {
            ModEvents.interruptActiveCasting(sp);
        }
    }

    void beginRoar() {
        if (!hasRoared) {
            hasRoared = true;
        }
        setAttackState(AttackState.ROAR);
    }

    void beginSlam() {
        setAttackState(AttackState.SLAM_WINDUP);
    }

    void beginSweep() {
        setAttackState(AttackState.SWEEP);
    }

    void beginBackhand() {
        setAttackState(AttackState.BACKHAND);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getAttackState() == AttackState.KNOCKED_OUT) {
            amount *= KNOCKED_OUT_DAMAGE_MULTIPLIER;
        }
        boolean wasBusy = isBusy();
        boolean result = super.hurt(source, amount);
        // Heavy hits while not committed knock the troll into a brief stagger.
        if (result && !this.level().isClientSide && !wasBusy && amount >= 8.0F && this.isAlive()) {
            setAttackState(AttackState.STAGGER);
        }
        return result;
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
        if (tag.contains("Raging")) {
            this.entityData.set(DATA_RAGING, tag.getBoolean("Raging"));
        }
        if (tag.contains("HasRoared")) {
            this.hasRoared = tag.getBoolean("HasRoared");
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!this.level().isClientSide || !DATA_ATTACK_STATE.equals(key)) return;

        // Restart the clip that matches the new state and stop the rest, so the
        // client stays in sync off the synched state alone and never needs an
        // entity event. Attacks always pass back through NONE, so a repeated
        // attack still registers as a change here.
        AttackState state = getAttackState();
        this.roarAnimationState.stop();
        this.slamWindupAnimationState.stop();
        this.slamStrikeAnimationState.stop();
        this.sweepAnimationState.stop();
        this.staggerAnimationState.stop();
        this.backhandAnimationState.stop();
        this.disarmedAnimationState.stop();
        this.knockedOutAnimationState.stop();
        this.getUpAnimationState.stop();

        if (state == AttackState.ROAR) {
            this.roarAnimationState.start(this.tickCount);
        } else if (state == AttackState.SLAM_WINDUP) {
            this.slamWindupAnimationState.start(this.tickCount);
        } else if (state == AttackState.SLAM_STRIKE) {
            this.slamStrikeAnimationState.start(this.tickCount);
        } else if (state == AttackState.SWEEP) {
            this.sweepAnimationState.start(this.tickCount);
        } else if (state == AttackState.STAGGER) {
            this.staggerAnimationState.start(this.tickCount);
        } else if (state == AttackState.BACKHAND) {
            this.backhandAnimationState.start(this.tickCount);
        } else if (state == AttackState.DISARMED) {
            this.disarmedAnimationState.start(this.tickCount);
        } else if (state == AttackState.KNOCKED_OUT) {
            this.knockedOutAnimationState.start(this.tickCount);
        } else if (state == AttackState.GET_UP) {
            this.getUpAnimationState.start(this.tickCount);
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        this.playSound(com.hogwartsmagic.ModSounds.TROLL_STEP.get(), 0.9F, 0.9F + this.random.nextFloat() * 0.15F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.TROLL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return com.hogwartsmagic.ModSounds.TROLL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.TROLL_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 1.25F;
    }

    /** Holds the troll still, head and all, while it is out of the fight. */
    static class OutColdGoal extends Goal {
        private final TrollEntity troll;

        OutColdGoal(TrollEntity troll) {
            this.troll = troll;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return troll.isIncapacitated();
        }

        @Override
        public void start() {
            troll.getNavigation().stop();
        }
    }

    /** Custom melee goal: walks into range, then picks an attack by distance and respects the busy state. */
    static class TrollAttackGoal extends Goal {
        private static final int REPATH_INTERVAL = 10;

        private final TrollEntity troll;
        private int nextDecisionCooldown;
        private int repathCooldown;

        TrollAttackGoal(TrollEntity troll) {
            this.troll = troll;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = troll.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            nextDecisionCooldown = 0;
            repathCooldown = 0;
        }

        @Override
        public void stop() {
            troll.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = troll.getTarget();
            if (target == null) return;

            troll.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // If busy doing an attack/roar/stagger, hold position; movement lock is enforced in tick().
            if (troll.isBusy() || troll.getAttackState() == TrollEntity.AttackState.STAGGER) {
                return;
            }

            double dist = Math.sqrt(troll.distanceToSqr(target));

            // First-contact roar before the first close-up engagement.
            if (!troll.hasRoared && dist < 8.0D) {
                troll.beginRoar();
                return;
            }

            // Approach until close enough. Re-pathing every tick would restart the walk constantly.
            if (dist > ATTACK_RANGE + target.getBbWidth() * 0.5D) {
                if (--repathCooldown <= 0 || troll.getNavigation().isDone()) {
                    troll.getNavigation().moveTo(target, troll.isRaging() ? 1.2D : 0.95D);
                    repathCooldown = REPATH_INTERVAL;
                }
                return;
            }

            // In range: stand and square up, so a swing starts from where the target is now.
            troll.getNavigation().stop();
            troll.turnToTarget();
            repathCooldown = 0;
            if (nextDecisionCooldown > 0) {
                nextDecisionCooldown--;
                return;
            }

            int roll = troll.random.nextInt(100);
            boolean clustered = countNearbyTargets(target) >= 2;
            if (clustered && roll < 70) {
                troll.beginSlam();
            } else if (dist < CLOSE_RANGE) {
                if (roll < 45) {
                    troll.beginBackhand();
                } else if (roll < 80) {
                    troll.beginSweep();
                } else {
                    troll.beginSlam();
                }
            } else if (roll < 70) {
                troll.beginSlam();
            } else {
                troll.beginSweep();
            }

            nextDecisionCooldown = troll.isRaging() ? 3 : 10;
        }

        private int countNearbyTargets(LivingEntity around) {
            AABB box = around.getBoundingBox().inflate(3.5D);
            return troll.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != troll && (e instanceof Player || e instanceof WizardEntity || e instanceof StudentEntity)).size();
        }
    }
}
