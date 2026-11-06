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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
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

    /** Villager to werewolf (3 s) and back (1.5 s); the server freezes the mob for as long. */
    public static final int MORPH_TO_WOLF_TICKS = 60;
    public static final int MORPH_TO_HUMAN_TICKS = 30;
    /** Server: the transformation's climax, where it rears up and howls (ticks left). */
    private static final int MORPH_HOWL_AT_TICKS_LEFT = 18;
    public static final int ATTACK_ANIM_TICKS = 12;
    public static final int HOWL_ANIM_TICKS = 60;

    /** Entity events (61/62: see the event-byte note in memory, 21/35/63 crash the client). */
    private static final byte EVENT_SWIPE = 61;
    private static final byte EVENT_HOWL = 62;

    private Boolean clientPrevWerewolfForm;
    private boolean clientMorphingToWolf;
    private boolean clientMorphingToHuman;
    private int clientMorphStartTick;

    /** Client animation inputs, each with its previous-tick value for partial-tick interpolation. */
    private float clientStride, clientStrideO;
    private float clientMove, clientMoveO;
    private float clientQuad, clientQuadO;
    private float clientSnarl, clientSnarlO;
    private int clientAttackStartTick = Integer.MIN_VALUE / 2;
    private boolean clientAttackLeft;
    private int clientHowlStartTick = Integer.MIN_VALUE / 2;

    private int transformCooldownTicks;

    /** Server: ticks left of a transformation (mob frozen, no attacks). */
    private int transformingTicks;
    /** Server: ticks left of a howl (frozen unless it has a target). */
    private int howlingTicks;
    private boolean transformingToWolf;

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

    // ---- client animation inputs (read by the werewolf model each frame) -----------------------

    public float clientStride(float pt) {
        return Mth.lerp(pt, this.clientStrideO, this.clientStride);
    }

    public float clientMove(float pt) {
        return Mth.lerp(pt, this.clientMoveO, this.clientMove);
    }

    public float clientQuad(float pt) {
        return Mth.lerp(pt, this.clientQuadO, this.clientQuad);
    }

    public float clientSnarl(float pt) {
        return Mth.lerp(pt, this.clientSnarlO, this.clientSnarl);
    }

    /** 0..1 through the current claw swipe, or -1. */
    public float clientAttackProgress(float pt) {
        return progress(this.clientAttackStartTick, ATTACK_ANIM_TICKS, pt);
    }

    public boolean clientAttackLeft() {
        return this.clientAttackLeft;
    }

    public float clientHowlProgress(float pt) {
        return progress(this.clientHowlStartTick, HOWL_ANIM_TICKS, pt);
    }

    public float clientMorphToWolfProgress(float pt) {
        return this.clientMorphingToWolf ? Math.min(1.0F, progress(this.clientMorphStartTick, MORPH_TO_WOLF_TICKS, pt)) : -1.0F;
    }

    public float clientMorphToHumanProgress(float pt) {
        return this.clientMorphingToHuman ? Math.min(1.0F, progress(this.clientMorphStartTick, MORPH_TO_HUMAN_TICKS, pt)) : -1.0F;
    }

    private float progress(int start, int length, float pt) {
        float p = (this.tickCount - start + pt) / length;
        return p >= 0.0F && p < 1.0F ? p : -1.0F;
    }

    public void setWerewolfForm(boolean wolf) {
        setWerewolfForm(wolf, true);
    }

    // ---- a cursed player's stand-in (client only, never added to the world) ------------------------------------

    /**
     * Client: flips the form of a surrogate drawn in a cursed player's place. Going through the synced data plays
     * the same morph clip a real werewolf plays, unless this is the first form the surrogate has seen.
     */
    public void clientSetSurrogateForm(boolean wolf) {
        if (this.level().isClientSide) {
            this.entityData.set(WEREWOLF_FORM, wolf);
        }
    }

    /** Client: a surrogate's claw swipe (its player swung). */
    public void clientSwipe() {
        handleEntityEvent(EVENT_SWIPE);
    }

    /** Client: a surrogate's howl. */
    public void clientHowl() {
        handleEntityEvent(EVENT_HOWL);
    }

    /** @param transform play the transformation (freeze, sounds); false on spawn. */
    private void setWerewolfForm(boolean wolf, boolean transform) {
        if (this.level().isClientSide) {
            return;
        }
        if (this.entityData.get(WEREWOLF_FORM) == wolf) {
            return;
        }
        this.entityData.set(WEREWOLF_FORM, wolf);
        if (transform) {
            this.transformingTicks = wolf ? MORPH_TO_WOLF_TICKS : MORPH_TO_HUMAN_TICKS;
            this.transformingToWolf = wolf;
            this.getNavigation().stop();
            this.playSound(com.hogwartsmagic.ModSounds.WEREWOLF_HURT.get(), 1.0F, wolf ? 0.6F : 0.9F);
        }
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
        this.goalSelector.addGoal(1, new HoldStillGoal());
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

    /** Server: holds the mob in place (and out of melee) through a transformation or a lone howl. */
    private final class HoldStillGoal extends Goal {
        HoldStillGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private boolean holding() {
            LycanthropeVillagerEntity self = LycanthropeVillagerEntity.this;
            return self.transformingTicks > 0 || (self.howlingTicks > 0 && self.getTarget() == null);
        }

        @Override
        public boolean canUse() {
            return holding();
        }

        @Override
        public boolean canContinueToUse() {
            return holding();
        }

        @Override
        public void start() {
            LycanthropeVillagerEntity.this.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LycanthropeVillagerEntity self = LycanthropeVillagerEntity.this;
            self.getNavigation().stop();
            self.setDeltaMovement(self.getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (isWerewolfForm()) {
            this.level().broadcastEntityEvent(this, EVENT_SWIPE);
        }
        return hit;
    }

    /** Server: howl, with the animation on every watching client. */
    private void howl() {
        this.playSound(com.hogwartsmagic.ModSounds.WEREWOLF_HOWL.get(), 1.4F, 0.75F + this.random.nextFloat() * 0.15F);
        this.level().broadcastEntityEvent(this, EVENT_HOWL);
        this.howlingTicks = HOWL_ANIM_TICKS;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SWIPE) {
            this.clientAttackStartTick = this.tickCount;
            this.clientAttackLeft = !this.clientAttackLeft;
        } else if (id == EVENT_HOWL) {
            this.clientHowlStartTick = this.tickCount;
        } else {
            super.handleEntityEvent(id);
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
            if (this.transformingTicks > 0) {
                this.transformingTicks--;
                if (this.transformingToWolf && this.transformingTicks == MORPH_HOWL_AT_TICKS_LEFT) {
                    // the roar the transformation rears up into (the client poses it in the morph clip)
                    this.playSound(com.hogwartsmagic.ModSounds.WEREWOLF_HOWL.get(), 1.5F, 0.7F);
                }
            }
            if (this.howlingTicks > 0) {
                this.howlingTicks--;
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
        if (this.transformingTicks <= 0 && this.howlingTicks <= 0 && this.getTarget() == null) {
            // full moon: often; any other night: now and then
            int oneIn = isNaturalDimensionFullMoon() ? 260 : 1400;
            if (this.random.nextInt(oneIn) == 0) {
                howl();
            }
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
        }
    }

    private void clientSyncMorphWithSyncedForm() {
        boolean wolf = isWerewolfForm();
        if (this.clientPrevWerewolfForm == null) {
            this.clientPrevWerewolfForm = wolf;   // first sight: no transformation replayed
            return;
        }
        if (wolf == this.clientPrevWerewolfForm) {
            return;
        }
        this.clientMorphStartTick = this.tickCount;
        this.clientMorphingToWolf = wolf;
        this.clientMorphingToHuman = !wolf;
        this.clientQuad = this.clientQuadO = 0.0F;
        if (wolf) {
            clientTransformBurst();               // the villager vanishes into it; the curled beast is left
        }
        this.clientPrevWerewolfForm = wolf;
    }

    private void tickClientMorphAndAnimations() {
        this.clientSyncMorphWithSyncedForm();
        if (this.clientMorphingToWolf && this.tickCount >= this.clientMorphStartTick + MORPH_TO_WOLF_TICKS) {
            this.clientMorphingToWolf = false;
        }
        if (this.clientMorphingToHuman && this.tickCount >= this.clientMorphStartTick + MORPH_TO_HUMAN_TICKS) {
            this.clientMorphingToHuman = false;
            clientTransformBurst();               // and the villager steps out of it
        }
        if ((this.clientMorphingToWolf || this.clientMorphingToHuman) && this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getRandomX(0.6), this.getRandomY(), this.getRandomZ(0.6), 0, 0.02, 0);
        }

        this.clientStrideO = this.clientStride;
        this.clientMoveO = this.clientMove;
        this.clientQuadO = this.clientQuad;
        this.clientSnarlO = this.clientSnarl;
        double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        this.clientStride += dist;
        if (this.clientStride > 6400.0F) {                 // keep float precision; both stride lengths divide it
            this.clientStride -= 6400.0F;
            this.clientStrideO -= 6400.0F;
        }
        float moveTarget = Mth.clamp(dist / 0.11F, 0.0F, 1.0F);
        this.clientMove += (moveTarget - this.clientMove) * 0.3F;
        boolean hunting = isWerewolfForm() && this.isAggressive();
        float quadTarget = hunting && this.clientMove > 0.3F ? 1.0F : 0.0F;
        this.clientQuad = approach(this.clientQuad, quadTarget, 0.12F);
        this.clientSnarl = approach(this.clientSnarl, hunting ? 1.0F : 0.0F, 0.1F);
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    /** Client: the smoke the swap between villager and beast happens inside. */
    private void clientTransformBurst() {
        for (int i = 0; i < 28; i++) {
            double vx = this.random.nextGaussian() * 0.04, vy = this.random.nextDouble() * 0.06, vz = this.random.nextGaussian() * 0.04;
            this.level().addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.POOF,
                    this.getRandomX(0.9), this.getY() + this.random.nextDouble() * 2.0, this.getRandomZ(0.9), vx, vy, vz);
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
            setWerewolfForm(shouldBeWerewolfFromWorld(), false);
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
