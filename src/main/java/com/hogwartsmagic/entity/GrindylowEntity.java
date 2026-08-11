package com.hogwartsmagic.entity;

import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A Grindylow: a small horned water demon of the Black Lake, built to the
 * Fantastic Beasts design - oversized head, needle teeth, and six curling
 * tentacles instead of legs.
 *
 * <p><b>It is not a fighter, it is a trap.</b> A merperson warns you, duels you
 * at spear reach and lets you leave. A grindylow does none of that. It lies in
 * the weed doing nothing at all until something wakes it, then it comes in
 * numbers, and what it does when it reaches you is not damage — it takes hold of
 * your leg, drags you down and keeps you there while your air runs out.
 *
 * <p>The way out is the canon one: their fingers are long and strong but
 * <em>brittle</em>. Any hit at all breaks the grip. That is the whole encounter —
 * do not panic, turn, and hit the thing holding you, while the rest of them
 * arrive.
 *
 * <p>The merpeople keep them. A nest of them lies dormant in the weed around the
 * hoard at the merfolk village, and opening that chest is what wakes them.
 */
public class GrindylowEntity extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> DATA_LURKING =
            SynchedEntityData.defineId(GrindylowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_VICTIM =
            SynchedEntityData.defineId(GrindylowEntity.class, EntityDataSerializers.INT);

    /** How close you have to blunder for a lurking one to wake on its own. */
    private static final double NOTICE_RADIUS = 4.5D;
    /** Reach at which it stops swimming and takes hold. */
    private static final double SEIZE_RANGE = 1.9D;
    /** Extra air lost per tick per grindylow attached. Vanilla drowning is 1. */
    private static final int AIR_DRAIN = 4;
    /** It cannot hold on forever; this is the longest one grip lasts. */
    private static final int MAX_HOLD_TICKS = 200;
    /** Downward pull applied to a held victim, per grindylow. */
    private static final double DRAG_DOWN = 0.055D;

    private static final byte EVENT_HURT = 64;
    private static final byte EVENT_LUNGE = 66;

    public final AnimationState lurkAnimation = new AnimationState();
    public final AnimationState driftAnimation = new AnimationState();
    public final AnimationState lungeAnimation = new AnimationState();
    public final AnimationState clutchAnimation = new AnimationState();
    public final AnimationState hurtAnimation = new AnimationState();
    public final AnimationState deathAnimation = new AnimationState();

    private int holdTicks;
    private int seizeCooldown;

    /** Smoothed on both sides, so the tentacles need no packet of their own. */
    private float curl;
    private float curlO;
    private float wave;
    private float waveO;

    public GrindylowEntity(EntityType<? extends GrindylowEntity> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 90, 12, 0.03F, 0.14F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 8);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)          // brittle, by design
                .add(Attributes.MOVEMENT_SPEED, 1.15D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)       // the damage is not the point
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LURKING, false);
        builder.define(DATA_VICTIM, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 20));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(GrindylowEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    // ------------------------------------------------------------------ state

    public boolean isLurking() {
        return this.entityData.get(DATA_LURKING);
    }

    public void setLurking(boolean lurking) {
        this.entityData.set(DATA_LURKING, lurking);
        if (lurking) {
            this.setTarget(null);
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    public boolean hasVictim() {
        return this.entityData.get(DATA_VICTIM) != 0;
    }

    @Nullable
    public LivingEntity getVictim() {
        int id = this.entityData.get(DATA_VICTIM);
        if (id == 0) {
            return null;
        }
        return this.level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    private void setVictim(@Nullable LivingEntity victim) {
        this.entityData.set(DATA_VICTIM, victim == null ? 0 : victim.getId());
        this.holdTicks = victim == null ? 0 : MAX_HOLD_TICKS;
    }

    /** 0 = tentacles balled up tight, 1 = splayed wide. */
    public float getTentacleCurl(float partialTick) {
        return Mth.lerp(partialTick, this.curlO, this.curl);
    }

    /** 0 = barely stirring, 1 = thrashing. */
    public float getTentacleWave(float partialTick) {
        return Mth.lerp(partialTick, this.waveO, this.wave);
    }

    /**
     * Wakes a lurking grindylow and sets it on someone.
     *
     * <p>Called by the merfolk hoard alarm, so the nest in the weed comes up
     * together the moment the chest is opened.
     */
    public void rouse(@Nullable LivingEntity at) {
        if (isLurking()) {
            setLurking(false);
            this.level().broadcastEntityEvent(this, EVENT_LUNGE);
            playSound(ModSounds.GRINDYLOW_SHRIEK.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.3F);
        }
        if (at != null && at.isAlive()) {
            setTarget(at);
        }
    }

    // ------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();

        this.curlO = this.curl;
        this.waveO = this.wave;
        float curlTarget;
        float waveTarget;
        if (isLurking()) {
            curlTarget = -0.55F;                    // balled up in the weed
            waveTarget = 0.05F;
        } else if (hasVictim()) {
            curlTarget = -0.30F;                    // clenched around its grip
            waveTarget = 0.9F;
        } else {
            double dx = this.getX() - this.xo;
            double dy = this.getY() - this.yo;
            double dz = this.getZ() - this.zo;
            float speed = (float) Mth.clamp(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.28D, 0.0D, 1.0D);
            curlTarget = 0.15F + speed * 0.45F;     // streams out behind it
            waveTarget = 0.25F + speed * 0.6F;
        }
        this.curl += (curlTarget - this.curl) * 0.12F;
        this.wave += (waveTarget - this.wave) * 0.15F;

        if (this.level().isClientSide) {
            tickClientAnimations();
            return;
        }

        if (this.seizeCooldown > 0) {
            this.seizeCooldown--;
        }
        if (isLurking()) {
            tickLurking();
        } else {
            tickHold();
        }
    }

    private void tickClientAnimations() {
        if (this.deathTime > 0) {
            this.lurkAnimation.stop();
            this.driftAnimation.stop();
            this.clutchAnimation.stop();
            this.deathAnimation.startIfStopped(this.tickCount);
            return;
        }
        if (isLurking()) {
            this.driftAnimation.stop();
            this.clutchAnimation.stop();
            this.lurkAnimation.startIfStopped(this.tickCount);
        } else if (hasVictim()) {
            this.lurkAnimation.stop();
            this.driftAnimation.stop();
            this.clutchAnimation.startIfStopped(this.tickCount);
        } else {
            this.lurkAnimation.stop();
            this.clutchAnimation.stop();
            this.driftAnimation.startIfStopped(this.tickCount);
        }
    }

    /**
     * Dormant in the weed until something blunders close enough to matter.
     *
     * <p>The targeting goal still runs while it lurks, which is useful — it does
     * the "can I actually see a player" work — but its result has to be gated or
     * a sleeper would path across the village at the first sight of anyone. So a
     * target inside {@link #NOTICE_RADIUS} wakes it and anything further away is
     * discarded, and navigation is stopped every tick either way.
     */
    private void tickLurking() {
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 0.6D, 0.6D));

        LivingEntity spotted = getTarget();
        if (spotted == null) {
            return;
        }
        boolean worthWaking = spotted.isAlive() && spotted.isInWater()
                && this.distanceToSqr(spotted) <= NOTICE_RADIUS * NOTICE_RADIUS
                && !(spotted instanceof Player player && (player.isCreative() || player.isSpectator()));
        if (worthWaking) {
            rouse(spotted);
        } else {
            setTarget(null);
        }
    }

    /**
     * Take hold, and hold on.
     *
     * <p>While attached it plants itself on the victim's legs and stops
     * navigating: it is no longer a swimmer, it is a weight.
     */
    private void tickHold() {
        LivingEntity victim = getVictim();
        if (victim == null || !victim.isAlive() || !victim.isInWater()
                || this.distanceToSqr(victim) > 9.0D || --this.holdTicks <= 0) {
            if (hasVictim()) {
                release();
            }
            trySeize();
            return;
        }

        this.getNavigation().stop();
        Vec3 ankle = new Vec3(victim.getX(), victim.getY() + 0.15D, victim.getZ());
        this.setPos(ankle.x, ankle.y, ankle.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.getLookControl().setLookAt(victim, 60.0F, 60.0F);

        // Drag down, hold in place, and take the air.
        victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.55D, 1.0D, 0.55D)
                .add(0.0D, -DRAG_DOWN, 0.0D));
        victim.hurtMarked = true;
        if (victim.isUnderWater()) {
            victim.setAirSupply(Math.max(-20, victim.getAirSupply() - AIR_DRAIN));
        }
        if (this.tickCount % 20 == 0) {
            victim.hurt(this.damageSources().mobAttack(this), 1.0F);
            playSound(ModSounds.GRINDYLOW_GRAB.get(), 0.8F, 1.0F + this.random.nextFloat() * 0.3F);
        }
    }

    private void trySeize() {
        if (this.seizeCooldown > 0 || isLurking()) {
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !target.isInWater()
                || this.distanceToSqr(target) > SEIZE_RANGE * SEIZE_RANGE) {
            return;
        }
        setVictim(target);
        this.level().broadcastEntityEvent(this, EVENT_LUNGE);
        playSound(ModSounds.GRINDYLOW_GRAB.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.3F);
    }

    /** Lets go. Brittle fingers: any hit at all does this. */
    public void release() {
        setVictim(null);
        this.seizeCooldown = 30;
        this.holdTicks = 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean held = hasVictim();
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, EVENT_HURT);
            if (isLurking()) {
                rouse(source.getEntity() instanceof LivingEntity living ? living : null);
            }
            // The canon escape: the fingers snap. Being struck at all breaks the
            // grip, so a player who keeps their head always has a way out.
            if (held) {
                release();
                playSound(ModSounds.GRINDYLOW_SNAP.get(), 1.0F, 1.0F + this.random.nextFloat() * 0.4F);
            }
        }
        return hit;
    }

    @Override
    public void die(DamageSource source) {
        if (hasVictim()) {
            release();
        }
        super.die(source);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_HURT) {
            this.hurtAnimation.start(this.tickCount);
        } else if (id == EVENT_LUNGE) {
            this.lungeAnimation.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ---------------------------------------------------------------- plumbing

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isEffectiveAi() && this.isInWater() && !isLurking() && !hasVictim()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.88D));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Something dormant in the weed should not drift off or wander away. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isLurking() && !isPersistenceRequired();
    }

    public static boolean checkGrindylowSpawnRules(EntityType<GrindylowEntity> type, ServerLevelAccessor level,
                                                   MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.above()).is(FluidTags.WATER)
                && pos.getY() < level.getSeaLevel();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isLurking() ? null : ModSounds.GRINDYLOW_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GRINDYLOW_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GRINDYLOW_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Lurking", isLurking());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLurking(tag.getBoolean("Lurking"));
    }

    /** Wakes every dormant grindylow in range. Used by the merfolk hoard alarm. */
    public static int rouseNest(ServerLevel level, BlockPos pos, LivingEntity at, double radius) {
        var nest = level.getEntitiesOfClass(GrindylowEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(radius), GrindylowEntity::isAlive);
        for (GrindylowEntity grindylow : nest) {
            grindylow.rouse(at);
        }
        if (!nest.isEmpty()) {
            level.playSound(null, pos, ModSounds.GRINDYLOW_SHRIEK.get(), SoundSource.HOSTILE, 1.4F, 0.85F);
        }
        return nest.size();
    }
}
