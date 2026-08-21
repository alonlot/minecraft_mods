package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.gringotts.GringottsDisguise;
import com.hogwartsmagic.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Vault guard. Neutral until someone strikes a goblin or starts helping
 * themselves to the contents of a vault, then relentless.
 */
public class GoblinGuardEntity extends PathfinderMob implements NeutralMob, GoblinAnimated {

    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);

    /** How close a chest has to be opened before a guard treats it as a robbery. */
    public static final double THEFT_RADIUS = 12.0D;

    private static final byte EVENT_ATTACK = 61;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState busyAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();

    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;

    public GoblinGuardEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        // Left-handed so the blade renders in the arm the attack clip swings.
        this.setLeftHanded(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.45D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** Set on guards stationed below ground. See makeSentry. */
    private boolean sentry;

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.4D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(GoblinGuardEntity.class));
        // Only ever targets players it is already angry at - never hostile on sight. A guard on the
        // banking floor stands among customers all day, and one that swung at everybody who walked
        // in would make the hall unusable.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                this::isAngryAt));
        // Below ground is the exception, and the reason the flag exists: there is no innocent
        // reason to be standing in the vaults, so a sentry does not wait to be provoked. It still
        // lets a goblin past, which is the entire point of going to the trouble of being one.
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                living -> this.sentry && living instanceof Player p && !p.isCreative()
                        && !p.isSpectator() && !GringottsDisguise.isGoblin(p)));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        this.populateDefaultEquipmentSlots(level.getRandom(), difficulty);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
    }

    // --------------------------------------------------------------- aggro

    /**
     * Wakes every guard within {@code radius} of {@code source} and sets them on
     * {@code offender}. Server-side only; a plain radius scan, so there is no
     * persistent territory state to fall out of sync.
     */
    public static void alertGuards(LivingEntity source, LivingEntity offender, double radius) {
        if (source.level().isClientSide || offender instanceof GoblinGuardEntity || offender instanceof GoblinEntity) {
            return;
        }
        AABB box = source.getBoundingBox().inflate(radius, 8.0D, radius);
        List<GoblinGuardEntity> guards = source.level().getEntitiesOfClass(GoblinGuardEntity.class, box);
        for (GoblinGuardEntity guard : guards) {
            guard.angerAt(offender);
        }
    }

    /** Turns this guard on {@code offender} and makes it shout about it. */
    public void angerAt(LivingEntity offender) {
        if (offender == this || this.getTarget() == offender) {
            return;
        }
        this.setTarget(offender);
        this.setPersistentAngerTarget(offender.getUUID());
        this.startPersistentAngerTimer();
        this.playSound(ModSounds.GOBLIN_ANGRY.get(), 1.0F, 1.0F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            alertGuards(this, attacker, GoblinEntity.GUARD_ALERT_RADIUS);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, EVENT_ATTACK);
        }
        return hit;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_ATTACK) {
            this.attackAnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
        }
    }

    @Override
    protected void customServerAiStep() {
        this.updatePersistentAnger((ServerLevel) this.level(), true);
        super.customServerAiStep();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ------------------------------------------------------------ NeutralMob

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        this.remainingPersistentAngerTime = time;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.persistentAngerTarget = target;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.addPersistentAngerSaveData(tag);
        tag.putBoolean("Sentry", this.sentry);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
        this.sentry = tag.getBoolean("Sentry");
    }

    // ----------------------------------------------------------------- sound

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (this.getTarget() != null) {
            return ModSounds.GOBLIN_ANGRY.get();
        }
        return this.random.nextBoolean() ? ModSounds.GOBLIN_AMBIENT1.get() : ModSounds.GOBLIN_AMBIENT2.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GOBLIN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GOBLIN_DEATH.get();
    }

    /**
     * Turns this guard into an undercroft sentry: hostile on sight, and it stays put.
     *
     * <p>A plain field rather than synched data. Nothing about it is visible - a sentry and a hall
     * guard look identical, which is rather the idea - so shipping it to the client would buy a
     * tracker entry per guard and nothing else.
     */
    public void makeSentry() {
        this.sentry = true;
        setPersistenceRequired();
    }

    public boolean isSentry() {
        return this.sentry;
    }

    /** Convenience used by worldgen when stationing guards inside the vault. */
    public void makeGuard(String name) {
        this.setCustomName(Component.literal(name));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
    }

    // ------------------------------------------------------------ animations

    @Override
    public AnimationState goblinIdleAnimationState() {
        return this.idleAnimationState;
    }

    @Override
    public AnimationState goblinBusyAnimationState() {
        return this.busyAnimationState;
    }

    @Override
    public AnimationState goblinAttackAnimationState() {
        return this.attackAnimationState;
    }

    @Override
    public boolean goblinCountsCoins() {
        return false;
    }

    @Override
    public boolean goblinHoldsWeapon() {
        return true;
    }
}
