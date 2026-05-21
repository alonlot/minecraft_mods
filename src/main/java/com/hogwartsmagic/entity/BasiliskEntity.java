package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class BasiliskEntity extends Monster {
    private static final int PETRIFY_INTERVAL_TICKS = 2;
    private static final double PETRIFY_RANGE = 28.0D;
    private static final int PETRIFY_DURATION = 60;
    private static final double MELEE_RANGE_SQ = 4.5D * 4.5D;
    private static final double BASILISK_EYE_AIM_DOT = 0.94D;
    private static final double TARGET_EYE_AIM_DOT = 0.985D;
    private static final double BASILISK_EYE_AIM_RADIUS = 0.85D;
    private static final double TARGET_EYE_AIM_RADIUS = 0.33D;

    private static final double LUNGE_MIN_RANGE = 6.0D;
    private static final double LUNGE_MAX_RANGE = 14.0D;
    private static final int LUNGE_WINDUP_TICKS = 12;
    private static final int LUNGE_STRIKE_TICKS = 10;
    private static final int LUNGE_COOLDOWN_TICKS = 80;

    private static final double TAIL_WHIP_RANGE = 6.0D;
    private static final int TAIL_WHIP_WINDUP_TICKS = 8;
    private static final int TAIL_WHIP_STRIKE_TICKS = 12;
    private static final int TAIL_WHIP_COOLDOWN_TICKS = 100;

    /** Synced animation state so the client can drive jaw / body curves. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_ACTION_STATE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(BasiliskEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    public enum ActionState {
        NONE, LUNGE_WINDUP, LUNGE_STRIKE, TAIL_WHIP_WINDUP, TAIL_WHIP_STRIKE;

        private static final ActionState[] VALUES = values();

        public static ActionState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private int actionTicks;
    private int lungeCooldown;
    private int tailWhipCooldown;

    public BasiliskEntity(EntityType<? extends BasiliskEntity> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.ATTACK_DAMAGE, 16.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
    }

    public static boolean canSpawn(EntityType<BasiliskEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION_STATE, (byte) ActionState.NONE.ordinal());
    }

    public ActionState getActionState() {
        return ActionState.fromByte(this.entityData.get(DATA_ACTION_STATE));
    }

    private void setActionState(ActionState state) {
        this.entityData.set(DATA_ACTION_STATE, (byte) state.ordinal());
        this.actionTicks = 0;
    }

    boolean isBusy() {
        return getActionState() != ActionState.NONE;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BasiliskCombatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.tickCount % PETRIFY_INTERVAL_TICKS == 0) {
            this.tryPetrifyNearbyVictims();
        }

        if (!this.level().isClientSide) {
            advanceActionState();
            if (lungeCooldown > 0) lungeCooldown--;
            if (tailWhipCooldown > 0) tailWhipCooldown--;
        }

        if (this.level().isClientSide && this.tickCount % 2 == 0) {
            this.level().addParticle(
                    ParticleTypes.ENCHANTED_HIT,
                    this.getRandomX(0.9D),
                    this.getEyeY() - 0.2D,
                    this.getRandomZ(0.9D),
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private void advanceActionState() {
        ActionState state = getActionState();
        if (state == ActionState.NONE) return;
        actionTicks++;
        switch (state) {
            case LUNGE_WINDUP -> {
                if (actionTicks >= LUNGE_WINDUP_TICKS) {
                    setActionState(ActionState.LUNGE_STRIKE);
                    doLungeStrike();
                }
            }
            case LUNGE_STRIKE -> {
                if (actionTicks >= LUNGE_STRIKE_TICKS) setActionState(ActionState.NONE);
            }
            case TAIL_WHIP_WINDUP -> {
                if (actionTicks >= TAIL_WHIP_WINDUP_TICKS) {
                    setActionState(ActionState.TAIL_WHIP_STRIKE);
                    doTailWhipStrike();
                }
            }
            case TAIL_WHIP_STRIKE -> {
                if (actionTicks >= TAIL_WHIP_STRIKE_TICKS) setActionState(ActionState.NONE);
            }
            default -> {}
        }
    }

    void beginLungeWindup() {
        setActionState(ActionState.LUNGE_WINDUP);
        lungeCooldown = LUNGE_COOLDOWN_TICKS;
        this.playSound(SoundEvents.SNIFFER_DEATH, 1.2F, 0.55F);
        if (this.level() instanceof ServerLevel sl) {
            Vec3 mouth = this.getEyePosition().add(this.getViewVector(1.0F).scale(1.4D));
            sl.sendParticles(ParticleTypes.SPIT, mouth.x, mouth.y, mouth.z, 14, 0.3D, 0.2D, 0.3D, 0.05D);
        }
    }

    void beginTailWhip() {
        setActionState(ActionState.TAIL_WHIP_WINDUP);
        tailWhipCooldown = TAIL_WHIP_COOLDOWN_TICKS;
    }

    /** Lunge: snap forward and deliver a venomous bite to anything in the front cone. */
    private void doLungeStrike() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity target = this.getTarget();
        Vec3 forward = this.getViewVector(1.0F).normalize();

        // Hop forward to close the gap on the strike tick.
        this.setDeltaMovement(this.getDeltaMovement().add(forward.scale(1.4D)).add(0, 0.2D, 0));
        this.hasImpulse = true;

        AABB area = this.getBoundingBox().inflate(3.5D, 1.5D, 3.5D);
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.4F;
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !(e instanceof BasiliskEntity))) {
            Vec3 to = victim.position().subtract(this.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 1.0E-4D) continue;
            double dot = flat.normalize().dot(new Vec3(forward.x, 0, forward.z).normalize());
            if (dot < 0.30D) continue; // front cone
            applyVenomousBite(victim, dmg);
        }
        this.playSound(SoundEvents.HOGLIN_ATTACK, 1.3F, 0.6F);
    }

    /** Sweeping tail attack — hits anything in a wide arc to the rear+sides. */
    private void doTailWhipStrike() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        AABB area = this.getBoundingBox().inflate(TAIL_WHIP_RANGE, 1.5D, TAIL_WHIP_RANGE);
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.85F;
        Vec3 forward = this.getViewVector(1.0F);
        Vec3 back = new Vec3(-forward.x, 0, -forward.z).normalize();
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && !(e instanceof BasiliskEntity))) {
            Vec3 to = victim.position().subtract(this.position());
            if (to.lengthSqr() > TAIL_WHIP_RANGE * TAIL_WHIP_RANGE) continue;
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 1.0E-4D) continue;
            // Hit anything in the rear hemisphere, not just behind a narrow cone — it's a tail SWEEP.
            double dot = flat.normalize().dot(back);
            if (dot < -0.20D) continue; // skip targets directly in front
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 push = flat.normalize();
            victim.push(push.x * 1.5D, 0.4D, push.z * 1.5D);
            victim.hurtMarked = true;
        }
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.55F);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                this.getX() + back.x * 3.0D, this.getY() + 0.5D, this.getZ() + back.z * 3.0D,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /** Venomous bite: heavy damage + sustained poison + brief wither (necrotic). Used by lunge and the
     * vanilla melee path via doHurtTarget below. */
    private void applyVenomousBite(LivingEntity victim, float damage) {
        victim.hurt(this.damageSources().mobAttack(this), damage);
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 1));
        victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, victim.getX(), victim.getY(0.75D), victim.getZ(), 10, 0.25D, 0.25D, 0.25D, 0.02D);
        }
        if (victim instanceof ServerPlayer sp) {
            com.hogwartsmagic.event.ModEvents.interruptActiveCasting(sp);
        }
    }

    private void tryPetrifyNearbyVictims() {
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(PETRIFY_RANGE), this::canPetrifyTarget)) {
            if (isMutuallyLooking(target)) {
                this.petrifyAndKill(target);
            }
        }
    }

    private boolean canPetrifyTarget(LivingEntity target) {
        if (target == this || !target.isAlive() || target instanceof BasiliskEntity) {
            return false;
        }
        if (target.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())) {
            return false;
        }
        if (this.distanceToSqr(target) > PETRIFY_RANGE * PETRIFY_RANGE) {
            return false;
        }
        return this.hasLineOfSight(target) && target.hasLineOfSight(this);
    }

    private boolean isMutuallyLooking(LivingEntity target) {
        Vec3 basiliskEyes = this.getEyePosition();
        Vec3 targetEyes = target.getEyePosition();
        return isLookingAtEyes(this, targetEyes, BASILISK_EYE_AIM_RADIUS, BASILISK_EYE_AIM_DOT)
                && isLookingAtEyes(target, basiliskEyes, TARGET_EYE_AIM_RADIUS, TARGET_EYE_AIM_DOT);
    }

    private static boolean isLookingAtEyes(LivingEntity viewer, Vec3 eyeTarget, double maxOffset, double minDot) {
        Vec3 viewerEyes = viewer.getEyePosition();
        Vec3 toTarget = eyeTarget.subtract(viewerEyes);
        double distanceSquared = toTarget.lengthSqr();
        if (distanceSquared < 1.0E-6D) {
            return false;
        }

        Vec3 directionToTarget = toTarget.normalize();
        Vec3 viewVector = viewer.getViewVector(1.0F).normalize();
        double facing = viewVector.dot(directionToTarget);
        if (facing < minDot) {
            return false;
        }

        double projectedDistance = toTarget.dot(viewVector);
        if (projectedDistance <= 0.0D) {
            return false;
        }

        Vec3 closestPoint = viewerEyes.add(viewVector.scale(projectedDistance));
        return closestPoint.distanceToSqr(eyeTarget) <= maxOffset * maxOffset;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            applyVenomousBite(living, 0.0F); // venom on top of vanilla melee damage already applied
            this.petrifyAndKill(living);
        }
        return hit;
    }

    private void petrifyAndKill(LivingEntity target) {
        target.addEffect(new MobEffectInstance(ExampleMod.BASILISK_PETRIFICATION.getHolder().get(), PETRIFY_DURATION, 0, false, true, true));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 20, 2, false, false, false));

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ASH, target.getX(), target.getEyeY(), target.getZ(), 28, 0.35D, 0.7D, 0.35D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.5D), target.getZ(), 18, 0.25D, 0.55D, 0.25D, 0.01D);
        }

        if (target.isAlive()) {
            target.hurt(this.damageSources().magic(), Float.MAX_VALUE);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.distanceToSqr(target) <= MELEE_RANGE_SQ) {
                this.setYRot(Mth.rotateIfNecessary(this.getYRot(), this.yHeadRot, 12.0F));
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.3F;
    }

    /** Picks between lunge (mid-range) and tail whip (close-range with target behind/beside), gated by cooldowns. */
    static class BasiliskCombatGoal extends Goal {
        private final BasiliskEntity basilisk;

        BasiliskCombatGoal(BasiliskEntity basilisk) {
            this.basilisk = basilisk;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = basilisk.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = basilisk.getTarget();
            if (target == null) return;
            basilisk.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (basilisk.isBusy()) {
                // Lock movement during action wind-ups so the strike telegraph reads.
                if (basilisk.getActionState() == ActionState.LUNGE_WINDUP || basilisk.getActionState() == ActionState.TAIL_WHIP_WINDUP) {
                    basilisk.getNavigation().stop();
                    Vec3 v = basilisk.getDeltaMovement();
                    basilisk.setDeltaMovement(v.x * 0.4D, v.y, v.z * 0.4D);
                }
                return;
            }

            double distSq = basilisk.distanceToSqr(target);

            // Tail whip when something is clustered close (player flanked around the body).
            if (basilisk.tailWhipCooldown == 0) {
                AABB whipArea = basilisk.getBoundingBox().inflate(TAIL_WHIP_RANGE, 1.5D, TAIL_WHIP_RANGE);
                List<LivingEntity> behind = basilisk.level().getEntitiesOfClass(LivingEntity.class, whipArea,
                        e -> e != basilisk && e.isAlive() && !(e instanceof BasiliskEntity));
                if (!behind.isEmpty() && basilisk.random.nextInt(40) == 0) {
                    basilisk.beginTailWhip();
                    return;
                }
            }

            // Lunge when the target is at medium range — closes the gap with a venomous bite.
            if (basilisk.lungeCooldown == 0
                    && distSq >= LUNGE_MIN_RANGE * LUNGE_MIN_RANGE
                    && distSq <= LUNGE_MAX_RANGE * LUNGE_MAX_RANGE
                    && basilisk.hasLineOfSight(target)) {
                basilisk.beginLungeWindup();
            }
        }
    }
}
