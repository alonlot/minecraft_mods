package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class AcromantulaEntity extends Monster {

    public enum AttackState {
        NONE, ALERT, LUNGE_WINDUP, LUNGE_STRIKE, WEB_SPIT;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(AcromantulaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(AcromantulaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE = SynchedEntityData.defineId(AcromantulaEntity.class, EntityDataSerializers.BYTE);

    public static final int ALERT_DURATION = 14;
    public static final int LUNGE_WINDUP_DURATION = 10;
    public static final int LUNGE_STRIKE_DURATION = 12;
    public static final int LUNGE_STRIKE_HIT_TICK = 3;
    public static final int WEB_SPIT_DURATION = 24;
    public static final int WEB_SPIT_FIRE_TICK = 14;

    private static final double LUNGE_REACH = 3.0D;
    private static final double WEB_MIN_RANGE = 5.0D;
    private static final double WEB_MAX_RANGE = 12.0D;

    // Server side: how long we've been in the current state. Client side: derived in setupAnim by tracking changes.
    private int serverStateTicks;
    private AttackState lastClientState = AttackState.NONE;
    private int clientStateTicks;

    public AcromantulaEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte) 0);
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_ATTACK_STATE, (byte) AttackState.NONE.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new AcromantulaAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean canSpawn(EntityType<AcromantulaEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    public AttackState getAttackState() {
        return AttackState.fromByte(this.entityData.get(DATA_ATTACK_STATE));
    }

    /** Server-authoritative ticks-since-state-change. */
    public int getServerStateTicks() {
        return serverStateTicks;
    }

    /** Client-tracked ticks-since-the-last-synced-state-change. Used by the model. */
    public int getClientStateTicks() {
        AttackState now = getAttackState();
        if (now != lastClientState) {
            lastClientState = now;
            clientStateTicks = 0;
        }
        return clientStateTicks;
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.serverStateTicks = 0;
    }

    boolean isBusy() {
        AttackState s = getAttackState();
        return s != AttackState.NONE;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            tickServerState();
            tickWebGeneration();
            this.setClimbing(this.horizontalCollision);
        } else {
            // Just advance the client tick counter — getClientStateTicks() resets it on state change.
            getClientStateTicks();
            clientStateTicks++;
        }
    }

    private void tickServerState() {
        AttackState state = getAttackState();
        int t = serverStateTicks++;

        switch (state) {
            case ALERT -> {
                if (t == 2) {
                    this.playSound(com.hogwartsmagic.ModSounds.ACROMANTULA_AMBIENT.get(), 1.1F, 0.7F);
                }
                if (t >= ALERT_DURATION) setAttackState(AttackState.NONE);
            }
            case LUNGE_WINDUP -> {
                if (t == 2) {
                    this.playSound(com.hogwartsmagic.ModSounds.ACROMANTULA_AMBIENT.get(), 0.8F, 1.4F);
                }
                if (t >= LUNGE_WINDUP_DURATION) {
                    setAttackState(AttackState.LUNGE_STRIKE);
                }
            }
            case LUNGE_STRIKE -> {
                if (t == LUNGE_STRIKE_HIT_TICK) {
                    doLungeImpact();
                }
                if (t >= LUNGE_STRIKE_DURATION) setAttackState(AttackState.NONE);
            }
            case WEB_SPIT -> {
                if (t == WEB_SPIT_FIRE_TICK) {
                    doWebSpit();
                }
                if (t >= WEB_SPIT_DURATION) setAttackState(AttackState.NONE);
            }
            case NONE -> { /* idle */ }
        }
    }

    private void tickWebGeneration() {
        // Existing behavior: scatter cobwebs occasionally as a flavor effect.
        if (this.tickCount % 20 == 0 && this.random.nextFloat() < 0.3F) {
            BlockPos pos = this.blockPosition();
            pos = pos.offset(this.random.nextInt(3) - 1, this.random.nextInt(2), this.random.nextInt(3) - 1);
            if (this.level().isEmptyBlock(pos)) {
                this.level().setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
            }
        }
    }

    private void doLungeImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB area = this.getBoundingBox().inflate(LUNGE_REACH, 1.0D, LUNGE_REACH);
        List<LivingEntity> victims = sl.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive());
        Vec3 originCentre = this.position();
        for (LivingEntity victim : victims) {
            Vec3 to = victim.position().subtract(originCentre);
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.lengthSqr() > LUNGE_REACH * LUNGE_REACH) continue;
            if (flat.lengthSqr() > 1.0E-4D) {
                double dot = flat.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize());
                if (dot < 0.30D) continue; // front cone only
            }
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            // Gentle nudge instead of the previous 0.6/0.2 shove — the old value kited
            // the player ~1.5 blocks back each hit and out of their own attack reach,
            // making melee against the spider effectively impossible. Keep enough to
            // sell the impact without removing the player from the fight.
            Vec3 push = flat.lengthSqr() < 1.0E-4D ? forward : flat.normalize();
            victim.push(push.x * 0.2D, 0.08D, push.z * 0.2D);
            victim.hurtMarked = true;
        }

        this.playSound(com.hogwartsmagic.ModSounds.ACROMANTULA_HURT.get(), 1.2F, 0.55F + this.random.nextFloat() * 0.15F);
        sl.sendParticles(ParticleTypes.CRIT,
                this.getX() + forward.x * 1.6D, this.getY() + 0.5D, this.getZ() + forward.z * 1.6D,
                10, 0.4D, 0.3D, 0.4D, 0.05D);
    }

    private void doWebSpit() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        BlockPos pos = target.blockPosition();
        // Try to place a cobweb at or just above the target's feet.
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos candidate = pos.above(dy);
            if (sl.isEmptyBlock(candidate)) {
                sl.setBlock(candidate, Blocks.COBWEB.defaultBlockState(), 3);
                break;
            }
        }

        // Visual trail from mouth to target.
        Vec3 from = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.65D, this.getZ());
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5D, 0);
        Vec3 step = to.subtract(from);
        int steps = (int) Math.max(4, step.length() * 2);
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            sl.sendParticles(ParticleTypes.ITEM_SLIME,
                    from.x + step.x * f, from.y + step.y * f, from.z + step.z * f,
                    1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        sl.playSound(null, this.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    void beginAlert() {
        setAttackState(AttackState.ALERT);
    }

    void beginLunge() {
        setAttackState(AttackState.LUNGE_WINDUP);
    }

    void beginWebSpit() {
        setAttackState(AttackState.WEB_SPIT);
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    public void makeStuckInBlock(net.minecraft.world.level.block.state.BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    public boolean isClimbing() {
        return (this.entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setClimbing(boolean climbing) {
        byte b0 = this.entityData.get(DATA_FLAGS_ID);
        if (climbing) {
            b0 = (byte) (b0 | 1);
        } else {
            b0 = (byte) (b0 & -2);
        }
        this.entityData.set(DATA_FLAGS_ID, b0);
    }

    public float getSpiderScale() {
        return this.entityData.get(DATA_SCALE);
    }

    /**
     * Per-instance hitbox: scale the entity-type base dimensions by {@link #getSpiderScale()}.
     * Without this every spider would share the same fixed hitbox regardless of how big the
     * model rendered, which is what was previously pushing players outside their own attack
     * reach (a baby-sized scale-0.2 acromantula with an oversized adult hitbox).
     */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return super.getDefaultDimensions(pose).scale(Math.max(0.2F, this.getSpiderScale()));
    }

    /**
     * The scale is set via SynchedEntityData (so the client also knows it). When it changes
     * we must refresh dimensions so the bounding box updates on both sides.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType reason, @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData spawnData) {
        net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        float[] scales = {0.2F, 0.4F, 0.6F, 0.8F, 1.0F, 1.2F};
        float scale = scales[this.random.nextInt(scales.length)];
        this.entityData.set(DATA_SCALE, scale);

        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D * scale);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D * scale);
        this.setHealth(this.getMaxHealth());

        return data;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("SpiderScale", this.getSpiderScale());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SpiderScale")) {
            this.entityData.set(DATA_SCALE, tag.getFloat("SpiderScale"));
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.ACROMANTULA_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.hogwartsmagic.ModSounds.ACROMANTULA_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.ACROMANTULA_DEATH.get();
    }

    /** Picks ALERT on first engagement, LUNGE at close range, WEB_SPIT at medium range. */
    static class AcromantulaAttackGoal extends Goal {
        private final AcromantulaEntity spider;
        private int decisionCooldown;
        private int webCooldown;
        private boolean hasAlertedThisEngagement;

        AcromantulaAttackGoal(AcromantulaEntity spider) {
            this.spider = spider;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = spider.getTarget();
            return target != null && target.isAlive() && !spider.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            decisionCooldown = 0;
            hasAlertedThisEngagement = false;
        }

        @Override
        public void stop() {
            hasAlertedThisEngagement = false;
            spider.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = spider.getTarget();
            if (target == null) return;

            spider.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (spider.isBusy()) {
                return;
            }

            double distSq = spider.distanceToSqr(target);

            if (!hasAlertedThisEngagement && distSq < 16.0D * 16.0D) {
                hasAlertedThisEngagement = true;
                spider.beginAlert();
                return;
            }

            // Tighter than the impact AABB (which extends LUNGE_REACH from the bounding box edges)
            // so the windup only triggers when the strike can actually land — leaving margin for
            // target drift during the 13-tick wind-up + strike sequence.
            double lungeTriggerReach = spider.getBbWidth() * 0.5D + LUNGE_REACH * 0.7D;
            double lungeReachSq = lungeTriggerReach * lungeTriggerReach;

            if (distSq <= lungeReachSq) {
                spider.getNavigation().stop();
                if (decisionCooldown > 0) { decisionCooldown--; return; }
                spider.beginLunge();
                decisionCooldown = 8;
                return;
            }

            if (webCooldown <= 0 && distSq >= WEB_MIN_RANGE * WEB_MIN_RANGE && distSq <= WEB_MAX_RANGE * WEB_MAX_RANGE
                    && spider.hasLineOfSight(target)) {
                spider.beginWebSpit();
                webCooldown = 80;
                return;
            }
            if (webCooldown > 0) webCooldown--;

            spider.getNavigation().moveTo(target, 1.0D);
        }
    }
}
