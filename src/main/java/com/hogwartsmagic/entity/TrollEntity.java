package com.hogwartsmagic.entity;

import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class TrollEntity extends Monster {

    public enum AttackState {
        NONE, ROAR, SLAM_WINDUP, SLAM_STRIKE, SWEEP, STAGGER;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICK =
            SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
            SynchedEntityData.defineId(TrollEntity.class, EntityDataSerializers.BOOLEAN);

    // State timings (ticks). The "strike" frame inside SLAM_STRIKE is at the start of that state.
    public static final int ROAR_DURATION = 36;
    public static final int SLAM_WINDUP_DURATION = 18;
    public static final int SLAM_STRIKE_DURATION = 16;
    public static final int SWEEP_DURATION = 22;
    public static final int SWEEP_STRIKE_TICK = 12;
    public static final int STAGGER_DURATION = 14;

    private static final double SLAM_RADIUS = 4.0D;
    private static final double SWEEP_RANGE = 5.0D;
    private static final float RAGE_HP_FRACTION = 0.40F;

    private boolean hasRoared;
    private int sweepStruckThisState;

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
        builder.define(DATA_STATE_TICK, 0);
        builder.define(DATA_RAGING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
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

    public int getStateTick() {
        return this.entityData.get(DATA_STATE_TICK);
    }

    public boolean isRaging() {
        return this.entityData.get(DATA_RAGING);
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_STATE_TICK, 0);
        this.sweepStruckThisState = 0;
    }

    private boolean isBusy() {
        AttackState s = getAttackState();
        return s != AttackState.NONE && s != AttackState.STAGGER;
    }

    private boolean canMoveWhileAttacking() {
        AttackState s = getAttackState();
        // Locked in place during windups/strikes/roar; stagger also halts.
        return s == AttackState.NONE;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // Advance per-state tick.
            AttackState state = getAttackState();
            int t = getStateTick();
            this.entityData.set(DATA_STATE_TICK, t + 1);

            // Rage threshold (one-way latch).
            if (!isRaging() && this.getHealth() <= this.getMaxHealth() * RAGE_HP_FRACTION) {
                this.entityData.set(DATA_RAGING, true);
                this.playSound(SoundEvents.WARDEN_ANGRY, 1.4F, 0.7F);
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
                        this.playSound(SoundEvents.WARDEN_ROAR, 1.5F, 0.55F);
                    }
                    if (t >= ROAR_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case SLAM_WINDUP -> {
                    if (t == 4) {
                        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.5F);
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
                    if (t == SWEEP_STRIKE_TICK && sweepStruckThisState == 0) {
                        sweepStruckThisState = 1;
                        doSweepImpact();
                    }
                    if (t >= SWEEP_DURATION) {
                        setAttackState(AttackState.NONE);
                    }
                }
                case STAGGER -> {
                    if (t >= STAGGER_DURATION) {
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

    private void doSlamImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        // Impact point in front of the troll, at ground level.
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double cx = this.getX() + forward.x * 2.2D;
        double cy = this.getY();
        double cz = this.getZ() + forward.z * 2.2D;

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.75F : 1.5F);
        AABB area = new AABB(cx - SLAM_RADIUS, cy - 1.0D, cz - SLAM_RADIUS,
                cx + SLAM_RADIUS, cy + 2.5D, cz + SLAM_RADIUS);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive());
        for (LivingEntity victim : victims) {
            double dx = victim.getX() - cx;
            double dz = victim.getZ() - cz;
            if (dx * dx + dz * dz > SLAM_RADIUS * SLAM_RADIUS) continue;
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 away = victim.position().subtract(cx, cy, cz).normalize();
            victim.push(away.x * 1.1D, 0.55D, away.z * 1.1D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }

        // Visuals + audio.
        this.playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.4F);
        this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.55F);
        sl.sendParticles(ParticleTypes.EXPLOSION, cx, cy + 0.1D, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        sl.sendParticles(ParticleTypes.POOF, cx, cy + 0.1D, cz, 60, SLAM_RADIUS * 0.6D, 0.2D, SLAM_RADIUS * 0.6D, 0.05D);
        sl.sendParticles(ParticleTypes.CLOUD, cx, cy + 0.2D, cz, 40, SLAM_RADIUS * 0.5D, 0.1D, SLAM_RADIUS * 0.5D, 0.04D);

        // Camera shake — pulse nausea on nearby players.
        for (Player p : sl.players()) {
            if (p.distanceToSqr(cx, cy, cz) < 144.0D) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.CONFUSION, 14, 0, false, false, false));
            }
        }
    }

    private void doSweepImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.15F : 0.95F);
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        AABB area = this.getBoundingBox().inflate(SWEEP_RANGE, 1.5D, SWEEP_RANGE);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive());
        for (LivingEntity victim : victims) {
            Vec3 to = victim.position().subtract(this.position());
            if (to.lengthSqr() > SWEEP_RANGE * SWEEP_RANGE) continue;
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            if (toFlat.lengthSqr() < 1.0E-4D) continue;
            double dot = toFlat.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize());
            if (dot < 0.35D) continue; // ~70° cone in front

            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 push = toFlat.normalize();
            victim.push(push.x * 1.4D, 0.35D, push.z * 1.4D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }

        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.55F);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                this.getX() + forward.x * 2.0D, this.getY() + 1.2D, this.getZ() + forward.z * 2.0D,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
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

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            this.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 1.2F, 0.55F + this.random.nextFloat() * 0.12F);
            if (target instanceof LivingEntity living) {
                knockBackFromClub(living);
                interruptIfCaster(living);
            }
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF,
                        target.getX(), target.getY(0.45D), target.getZ(),
                        10, 0.35D, 0.25D, 0.35D, 0.02D);
            }
        }
        return hit;
    }

    private void knockBackFromClub(LivingEntity target) {
        Vec3 away = target.position().subtract(this.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = Vec3.directionFromRotation(0.0F, this.getYRot());
        }
        away = away.normalize();
        target.push(away.x * 1.35D, 0.22D, away.z * 1.35D);
        target.hurtMarked = true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
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

    /** Custom melee goal: picks roar / slam / sweep / basic and respects the troll's busy state. */
    static class TrollAttackGoal extends Goal {
        private final TrollEntity troll;
        private int nextDecisionCooldown;

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

            double distSq = troll.distanceToSqr(target);
            double reachSq = trollAttackReachSq(target);

            // First-contact roar before the first close-up engagement.
            if (!troll.hasRoared && distSq < 64.0D) {
                troll.beginRoar();
                return;
            }

            // Approach until close enough.
            if (distSq > reachSq) {
                double speed = troll.isRaging() ? 1.2D : 0.95D;
                troll.getNavigation().moveTo(target, speed);
                return;
            }

            // In range: pick an attack.
            troll.getNavigation().stop();
            if (nextDecisionCooldown > 0) {
                nextDecisionCooldown--;
                return;
            }

            int roll = troll.random.nextInt(100);
            boolean clustered = countNearbyTargets(target) >= 2;

            if (troll.isRaging()) {
                if (clustered || roll < 50) {
                    troll.beginSlam();
                } else if (roll < 90) {
                    troll.beginSweep();
                } else {
                    troll.doHurtTarget(target);
                }
            } else {
                if (clustered && roll < 70) {
                    troll.beginSlam();
                } else if (roll < 55) {
                    troll.beginSlam();
                } else if (roll < 80) {
                    troll.beginSweep();
                } else {
                    troll.doHurtTarget(target);
                }
            }

            // Small cooldown between attack decisions to avoid stutter even after fast states.
            nextDecisionCooldown = 6;
        }

        private double trollAttackReachSq(LivingEntity target) {
            double reach = troll.getBbWidth() * 2.0D + target.getBbWidth();
            return reach * reach;
        }

        private int countNearbyTargets(LivingEntity around) {
            AABB box = around.getBoundingBox().inflate(3.5D);
            return troll.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != troll && (e instanceof Player || e instanceof WizardEntity || e instanceof StudentEntity)).size();
        }
    }
}
