package com.hogwartsmagic.entity;

import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Rare village inhabitant that appears as a normal villager by day and becomes a hostile werewolf at night.
 */
public class LycanthropeVillagerEntity extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> WEREWOLF_FORM =
            SynchedEntityData.defineId(LycanthropeVillagerEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double HUMAN_MAX_HEALTH = 20.0;
    private static final double WOLF_MAX_HEALTH = 36.0;
    private static final double HUMAN_SPEED = 0.28;
    private static final double WOLF_SPEED = 0.35;
    private static final double WOLF_DAMAGE = 8.0;
    private static final double WOLF_FULL_MOON_DAMAGE_BONUS = 2.0;
    private static final double WOLF_FULL_MOON_SPEED_MULT = 1.12;
    private static final double BASE_FOLLOW_RANGE = 28.0;
    private static final double WOLF_FULL_MOON_FOLLOW_RANGE = 36.0;
    private static final float WOLF_TRANSFORM_HEAL = 6.0F;

    /** Server: min ticks between transform heals (stops heal spam via commands / rapid toggles). */
    private static final int WOLF_TRANSFORM_HEAL_COOLDOWN_TICKS = 2400;

    /** Server: min ticks between day/night form flips (avoids rapid toggles from sleep/commands). */
    private static final int TRANSFORM_COOLDOWN_TICKS = 100;

    /** Client-side villager→werewolf morph (~10s animation + partial-tick buffer). */
    private static final int MORPH_TO_WOLF_TICKS = 205;
    private static final int MORPH_TO_HUMAN_TICKS = 29;

    /** Hysteresis on {@link #walkAnimation} speed so walk/idle do not restart every tick at the threshold. */
    private static final float WOLF_WALK_SPEED_ENTER = 0.38F;
    private static final float WOLF_WALK_SPEED_EXIT = 0.14F;

    public final AnimationState humanIdleAnimationState = new AnimationState();
    public final AnimationState wolfIdleAnimationState = new AnimationState();
    public final AnimationState walkWolfAnimationState = new AnimationState();
    public final AnimationState idleToWolfAnimationState = new AnimationState();
    public final AnimationState wolfToIdleAnimationState = new AnimationState();

    private Boolean clientPrevWerewolfForm;
    private boolean clientMorphingToWolf;
    private boolean clientMorphingToHuman;
    private int clientMorphStartTick;
    /** Client: smoothed walk vs idle for wolf locomotion (see {@link #tickClientWolfWalkHysteresis}). */
    private boolean clientWolfLegsMoving;

    /** Client: renderer {@code ageInTicks} captured on the first frame of villager→werewolf morph (see model). */
    private float clientMorphToWolfAnimBaselineAgeInTicks = Float.NaN;

    private int transformCooldownTicks;

    /**
     * Server: whether {@link #registerGoalsForCurrentForm()} last ran for werewolf form ({@code true}) or human ({@code false}).
     * Used to skip redundant goal teardown when form is unchanged.
     */
    private boolean goalsRegisteredForWolf;

    /** Server: last full-moon state applied in {@link #applyFormAttributes()} while wolf; {@code null} until first wolf tick. */
    @Nullable
    private Boolean lastFullMoonForAttributes;

    /** Server: {@link #tickCount} when {@link #WOLF_TRANSFORM_HEAL} was last applied (see cooldown). */
    private int lastWolfTransformHealAtTick = Integer.MIN_VALUE;

    public LycanthropeVillagerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static boolean checkSpawn(
            EntityType<LycanthropeVillagerEntity> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        return Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HUMAN_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, HUMAN_SPEED)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WEREWOLF_FORM, false);
    }

    public boolean isWerewolfForm() {
        return this.entityData.get(WEREWOLF_FORM);
    }

    /** Client: villager→werewolf transform clip is playing. */
    public boolean isClientMorphingToWolf() {
        return this.level().isClientSide && this.clientMorphingToWolf;
    }

    /** Client: werewolf→human transform clip is playing (entity is already “human” on server). */
    public boolean isClientMorphingToHuman() {
        return this.level().isClientSide && this.clientMorphingToHuman;
    }

    /**
     * Use the werewolf model instead of the villager surrogate (client render path).
     * True while werewolf or while playing wolf→human morph.
     */
    public boolean useWerewolfModelForClientRender() {
        return isWerewolfForm() || isClientMorphingToHuman();
    }

    /**
     * Client: whether to drive the wolf walk cycle vs idle. Uses the same smoothed signal as vanilla
     * limb swing ({@link #walkAnimation}), with hysteresis to avoid animation restarts.
     */
    public boolean isClientWolfLegsMoving() {
        return this.level().isClientSide && this.clientWolfLegsMoving;
    }

    /** Client: capture renderer {@code ageInTicks} once when morph-to-wolf begins (model calls each frame). */
    public void clientEnsureMorphAnimBaseline(float ageInTicks) {
        if (this.level().isClientSide && Float.isNaN(this.clientMorphToWolfAnimBaselineAgeInTicks)) {
            this.clientMorphToWolfAnimBaselineAgeInTicks = ageInTicks;
        }
    }

    public float getClientMorphAnimBaselineAgeInTicks() {
        return this.clientMorphToWolfAnimBaselineAgeInTicks;
    }

    private void clientResetMorphToWolfAnimBaseline() {
        this.clientMorphToWolfAnimBaselineAgeInTicks = Float.NaN;
    }

    public void setWerewolfForm(boolean wolf) {
        if (this.level().isClientSide) {
            return;
        }
        if (this.entityData.get(WEREWOLF_FORM) == wolf) {
            return;
        }
        this.entityData.set(WEREWOLF_FORM, wolf);
        applyFormAttributes();
        if (wolf) {
            this.lastFullMoonForAttributes = isNaturalDimensionFullMoon();
        } else {
            this.lastFullMoonForAttributes = null;
        }
        syncGoalsIfNeeded();
        if (wolf) {
            // long: the never-healed sentinel is Integer.MIN_VALUE, and int subtraction from it overflows negative.
            if ((long) this.tickCount - this.lastWolfTransformHealAtTick >= WOLF_TRANSFORM_HEAL_COOLDOWN_TICKS) {
                this.setHealth(Math.min(this.getHealth() + WOLF_TRANSFORM_HEAL, (float) WOLF_MAX_HEALTH));
                this.lastWolfTransformHealAtTick = this.tickCount;
            }
            this.playSound(com.hogwartsmagic.ModSounds.WEREWOLF_AMBIENT.get(), 1.0F, 0.7F + this.random.nextFloat() * 0.2F);
        }
    }

    private void applyFormAttributes() {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance follow = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (health == null || speed == null || damage == null || follow == null) {
            return;
        }
        if (isWerewolfForm()) {
            health.setBaseValue(WOLF_MAX_HEALTH);
            boolean fullMoon = isNaturalDimensionFullMoon();
            speed.setBaseValue(WOLF_SPEED * (fullMoon ? WOLF_FULL_MOON_SPEED_MULT : 1.0));
            damage.setBaseValue(WOLF_DAMAGE + (fullMoon ? WOLF_FULL_MOON_DAMAGE_BONUS : 0.0));
            follow.setBaseValue(fullMoon ? WOLF_FULL_MOON_FOLLOW_RANGE : BASE_FOLLOW_RANGE);
            this.setHealth(Math.min(this.getHealth(), (float) WOLF_MAX_HEALTH));
        } else {
            health.setBaseValue(HUMAN_MAX_HEALTH);
            speed.setBaseValue(HUMAN_SPEED);
            damage.setBaseValue(2.0);
            follow.setBaseValue(BASE_FOLLOW_RANGE);
            this.setHealth(Math.min(this.getHealth(), (float) HUMAN_MAX_HEALTH));
            this.setTarget(null);
        }
    }

    /** Moon phase 0 = full moon; only applies in natural (e.g. overworld) dimensions. */
    private boolean isNaturalDimensionFullMoon() {
        return this.level().dimensionType().natural() && this.level().getMoonPhase() == 0;
    }

    /**
     * Drops current AI goals and re-registers for the active form. Uses per-goal removal instead of
     * {@code removeAllGoals(goal -> true)} so we only clear what is actually registered on this mob.
     */
    private void syncGoalsIfNeeded() {
        boolean w = isWerewolfForm();
        if (this.goalsRegisteredForWolf != w) {
            refreshGoalsForForm();
            this.goalsRegisteredForWolf = w;
        }
    }

    private void refreshGoalsForForm() {
        for (WrappedGoal w : new ArrayList<>(this.goalSelector.getAvailableGoals())) {
            this.goalSelector.removeGoal(w.getGoal());
        }
        for (WrappedGoal w : new ArrayList<>(this.targetSelector.getAvailableGoals())) {
            this.targetSelector.removeGoal(w.getGoal());
        }
        registerGoalsForCurrentForm();
    }

    @Override
    protected void registerGoals() {
        registerGoalsForCurrentForm();
    }

    private void registerGoalsForCurrentForm() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (isWerewolfForm()) {
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4D, true));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9D));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            // mustSee false: night-hunt feel — acquire through obstacles; randomInterval matches StudentEntity pattern
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, e -> true));
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, 10, false, false, e -> true));
            this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Animal.class, 10, false, false, e -> true));
        } else {
            this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.45D));
            this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
            this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (this.level().isClientSide && data == WEREWOLF_FORM) {
            this.clientSyncMorphWithSyncedForm();
        }
    }

    /**
     * Client renderer should call this before drawing so a werewolf-form flag that arrived between
     * ticks still starts the morph clip the same frame (avoids one frame of full wolf idle / “instant” transform).
     */
    public void clientSyncMorphBeforeRender() {
        if (this.level().isClientSide) {
            this.clientSyncMorphWithSyncedForm();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            tickClientMorphAndAnimations();
        } else {
            syncGoalsIfNeeded();
            if (isWerewolfForm()) {
                boolean fullMoon = isNaturalDimensionFullMoon();
                if (this.lastFullMoonForAttributes == null || this.lastFullMoonForAttributes != fullMoon) {
                    applyFormAttributes();
                    this.lastFullMoonForAttributes = fullMoon;
                }
            } else {
                this.lastFullMoonForAttributes = null;
            }
            if (this.transformCooldownTicks > 0) {
                this.transformCooldownTicks--;
            }
            boolean night = shouldBeWerewolfFromWorld();
            if (this.transformCooldownTicks <= 0 && night != isWerewolfForm()) {
                setWerewolfForm(night);
                this.transformCooldownTicks = TRANSFORM_COOLDOWN_TICKS;
            }
            tickServerWerewolfAmbience();
        }
    }

    private void tickServerWerewolfAmbience() {
        if (!isWerewolfForm() || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (isNaturalDimensionFullMoon()) {
            if (this.random.nextInt(140) == 0) {
                server.sendParticles(
                        ParticleTypes.ENCHANT,
                        this.getX(),
                        this.getY() + 0.4,
                        this.getZ(),
                        4,
                        0.35,
                        0.25,
                        0.35,
                        0.02);
            }
            if (this.random.nextInt(220) == 0) {
                this.playSound(com.hogwartsmagic.ModSounds.WEREWOLF_HOWL.get(), 1.2F, 0.75F + this.random.nextFloat() * 0.15F);
            }
        }
    }

    private void clientSyncMorphWithSyncedForm() {
        boolean wolf = isWerewolfForm();
        if (this.clientPrevWerewolfForm == null) {
            this.clientPrevWerewolfForm = wolf;
            if (wolf) {
                this.wolfIdleAnimationState.startIfStopped(this.tickCount);
            } else {
                this.humanIdleAnimationState.startIfStopped(this.tickCount);
            }
            return;
        }
        if (wolf == this.clientPrevWerewolfForm) {
            return;
        }
        this.clientMorphStartTick = this.tickCount;
        if (wolf) {
            this.clientMorphingToWolf = true;
            this.clientMorphingToHuman = false;
            this.clientResetMorphToWolfAnimBaseline();
            this.wolfToIdleAnimationState.stop();
            // Villager→wolf pose is driven by KeyframeAnimations in WhulvkWerewolfLycanModel; startIfStopped is a fallback if the model fails.
            this.idleToWolfAnimationState.stop();
            this.idleToWolfAnimationState.startIfStopped(this.tickCount);
        } else {
            this.clientMorphingToHuman = true;
            this.clientMorphingToWolf = false;
            this.idleToWolfAnimationState.stop();
            this.wolfToIdleAnimationState.stop();
            this.wolfToIdleAnimationState.startIfStopped(this.tickCount);
        }
        this.humanIdleAnimationState.stop();
        this.wolfIdleAnimationState.stop();
        this.walkWolfAnimationState.stop();
        this.clientPrevWerewolfForm = wolf;
    }

    private void tickClientMorphAndAnimations() {
        this.clientSyncMorphWithSyncedForm();
        boolean wolf = isWerewolfForm();

        if (this.clientMorphingToWolf && this.tickCount >= this.clientMorphStartTick + MORPH_TO_WOLF_TICKS) {
            this.clientMorphingToWolf = false;
            this.idleToWolfAnimationState.stop();
            this.clientResetMorphToWolfAnimBaseline();
            this.tickClientWolfWalkHysteresis(true);
        }
        if (this.clientMorphingToHuman && this.tickCount >= this.clientMorphStartTick + MORPH_TO_HUMAN_TICKS) {
            this.clientMorphingToHuman = false;
            this.wolfToIdleAnimationState.stop();
        }

        if (this.clientMorphingToWolf || this.clientMorphingToHuman) {
            return;
        }

        if (wolf) {
            this.tickClientWolfWalkHysteresis(false);
            if (this.clientWolfLegsMoving) {
                this.walkWolfAnimationState.startIfStopped(this.tickCount);
                this.wolfIdleAnimationState.stop();
            } else {
                this.wolfIdleAnimationState.startIfStopped(this.tickCount);
                this.walkWolfAnimationState.stop();
            }
            this.humanIdleAnimationState.stop();
        } else {
            this.clientWolfLegsMoving = false;
            this.humanIdleAnimationState.startIfStopped(this.tickCount);
            this.wolfIdleAnimationState.stop();
            this.walkWolfAnimationState.stop();
        }
    }

    /**
     * Updates {@link #clientWolfLegsMoving} from vanilla walk animation speed (already smoothed on the client).
     *
     * @param forceSample when true, set state from current speed without hysteresis (after morph ends).
     */
    private void tickClientWolfWalkHysteresis(boolean forceSample) {
        float speed = this.walkAnimation.speed();
        if (forceSample) {
            this.clientWolfLegsMoving = speed > WOLF_WALK_SPEED_ENTER;
            return;
        }
        if (this.clientWolfLegsMoving) {
            if (speed < WOLF_WALK_SPEED_EXIT) {
                this.clientWolfLegsMoving = false;
            }
        } else {
            if (speed > WOLF_WALK_SPEED_ENTER) {
                this.clientWolfLegsMoving = true;
            }
        }
    }

    /** Night in overworld-style daylight cycle; stays human underground / dimensions without day cycle if always bright. */
    private boolean shouldBeWerewolfFromWorld() {
        return !this.level().isDay();
    }

    @Override
    public boolean isAggressive() {
        return isWerewolfForm() && super.isAggressive();
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WerewolfForm", isWerewolfForm());
        tag.putInt("TransformCooldown", this.transformCooldownTicks);
        // Saved as ticks since the heal: tickCount restarts at 0 on every load, so an absolute tick would be stale.
        tag.putInt("WolfHealAgo", (int) Math.min(Integer.MAX_VALUE,
                (long) this.tickCount - this.lastWolfTransformHealAtTick));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("WerewolfForm")) {
            boolean wolf = tag.getBoolean("WerewolfForm");
            this.entityData.set(WEREWOLF_FORM, wolf);
            applyFormAttributes();
            this.lastFullMoonForAttributes = wolf ? isNaturalDimensionFullMoon() : null;
            this.goalsRegisteredForWolf = !wolf;
            syncGoalsIfNeeded();
        }
        if (tag.contains("TransformCooldown")) {
            this.transformCooldownTicks = tag.getInt("TransformCooldown");
        }
        if (tag.contains("WolfHealAgo")) {
            int ago = tag.getInt("WolfHealAgo");
            this.lastWolfTransformHealAtTick = ago >= WOLF_TRANSFORM_HEAL_COOLDOWN_TICKS
                    ? Integer.MIN_VALUE : this.tickCount - ago;
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        if (!level.getLevel().isClientSide) {
            setWerewolfForm(shouldBeWerewolfFromWorld());
        }
        return data;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (isWerewolfForm()) {
            return com.hogwartsmagic.ModSounds.WEREWOLF_AMBIENT.get();
        }
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        if (isWerewolfForm()) {
            return com.hogwartsmagic.ModSounds.WEREWOLF_HURT.get();
        }
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        if (isWerewolfForm()) {
            return com.hogwartsmagic.ModSounds.WEREWOLF_DEATH.get();
        }
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return isWerewolfForm() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 0.75F : super.getVoicePitch();
    }
}
