package com.hogwartsmagic.entity.boggart;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Boggart: a shape-shifter with no shape of its own that hides in the dark. When a player comes close it takes
 * the form of whatever has killed that player most often (see {@link BoggartFears}) and spreads fear. Riddikulus
 * shrinks it; enough hits and it bursts into laughter and smoke.
 *
 * <p>Three states, all read from the synched data so the renderer sees the same thing:
 * HIDDEN ({@code form == ""}): stands still, smokes a little, waits. REVEALED: a slow melee monster that pulses
 * Darkness. LAUGHING ({@code laughTicks > 0}, inside REVEALED): giggles in place, cannot attack, still takes damage.
 * The public API is fixed by docs/superpowers/plans/2026-10-08-boggart.md.
 */
public class BoggartEntity extends Monster implements RiddikulusTarget {
    private static final EntityDataAccessor<String> DATA_FORM =
            SynchedEntityData.defineId(BoggartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_RIDDIKULUS_HITS =
            SynchedEntityData.defineId(BoggartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LAUGH_TICKS =
            SynchedEntityData.defineId(BoggartEntity.class, EntityDataSerializers.INT);

    public static final String ADVANCEMENT = "riddikulus";

    /** Ticks since a player was last within LOSE_INTEREST_RADIUS. Server only; not saved (it just hides on reload). */
    private int ticksWithoutPlayers;
    /** The player whose fear it wears, so the fear pulse knows who hears the cave sound. */
    @Nullable
    private UUID fearedBy;
    /** The rank of the last Riddikulus hit, so hitsToBanish uses the caster's rank, not a guess. Saved. */
    private int lastRank = 1;

    public BoggartEntity(EntityType<? extends BoggartEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FORM, "");
        builder.define(DATA_RIDDIKULUS_HITS, 0);
        builder.define(DATA_LAUGH_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Every movement goal is wrapped: a hidden or laughing Boggart does not move, attack or look about.
        this.goalSelector.addGoal(2, new ActiveOnly(new MeleeAttackGoal(this, 1.0D, false)));
        this.goalSelector.addGoal(5, new ActiveOnly(new WaterAvoidingRandomStrollGoal(this, 0.8D)));
        this.goalSelector.addGoal(6, new ActiveOnly(new LookAtPlayerGoal(this, Player.class, 8.0F)));
        this.goalSelector.addGoal(7, new ActiveOnly(new RandomLookAroundGoal(this)));
        this.targetSelector.addGoal(1, new ActiveOnly(new HurtByTargetGoal(this)));
        this.targetSelector.addGoal(2, new ActiveOnly(new NearestAttackableTargetGoal<>(this, Player.class, true)));
    }

    /** A goal that only runs while the Boggart is revealed and not laughing. */
    private final class ActiveOnly extends Goal {
        private final Goal inner;

        ActiveOnly(Goal inner) {
            this.inner = inner;
            this.setFlags(inner.getFlags());
        }

        private boolean active() {
            return !isHidden() && !isLaughing();
        }

        @Override
        public boolean canUse() {
            return active() && inner.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return active() && inner.canContinueToUse();
        }

        @Override
        public boolean isInterruptable() {
            return inner.isInterruptable();
        }

        @Override
        public void start() {
            inner.start();
        }

        @Override
        public void stop() {
            inner.stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return inner.requiresUpdateEveryTick();
        }

        @Override
        public void tick() {
            inner.tick();
        }
    }

    // ------------------------------------------------------------------ state

    public String getFormId() {
        return this.entityData.get(DATA_FORM);
    }

    public boolean isHidden() {
        return getFormId().isEmpty();
    }

    public int getRiddikulusHits() {
        return this.entityData.get(DATA_RIDDIKULUS_HITS);
    }

    public int getLaughTicks() {
        return this.entityData.get(DATA_LAUGH_TICKS);
    }

    public boolean isLaughing() {
        return getLaughTicks() > 0;
    }

    /** Hits it still needs at the rank of the last hit; what the renderer scales by. */
    public int getHitsToBanish() {
        return BoggartRules.hitsToBanish(this.lastRank);
    }

    private void setForm(String form) {
        this.entityData.set(DATA_FORM, form);
    }

    private void setRiddikulusHits(int hits) {
        this.entityData.set(DATA_RIDDIKULUS_HITS, hits);
    }

    private void setLaughTicks(int ticks) {
        this.entityData.set(DATA_LAUGH_TICKS, ticks);
    }

    // ------------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (isLaughing()) {
            setLaughTicks(getLaughTicks() - 1);
        }
        if (isHidden()) {
            tickHidden();
        } else {
            tickRevealed();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        // Hidden or laughing: the goals are off, but stray momentum (a shove, knockback) must not carry it.
        if (isHidden() || isLaughing()) {
            this.getNavigation().stop();
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, motion.y, 0.0D);
            this.setTarget(null);
        }
    }

    private void tickHidden() {
        ServerLevel level = (ServerLevel) this.level();
        if (this.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.6D, this.getZ(),
                    1, 0.2D, 0.3D, 0.2D, 0.005D);
        }
        if (this.tickCount % BoggartRules.REVEAL_CHECK_INTERVAL != 0) {
            return;
        }
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : level.players()) {
            if (!(player instanceof ServerPlayer sp) || !sp.isAlive() || sp.isSpectator() || sp.isCreative()) {
                continue;
            }
            double distSqr = this.distanceToSqr(sp);
            if (distSqr <= BoggartRules.REVEAL_RADIUS * BoggartRules.REVEAL_RADIUS
                    && distSqr < best && this.hasLineOfSight(sp)) {
                best = distSqr;
                nearest = sp;
            }
        }
        if (nearest != null) {
            reveal(nearest);
        }
    }

    /** Takes the shape of what this player fears most. Unknown ids fall back to the Acromantula. */
    private void reveal(ServerPlayer player) {
        String form = BoggartFears.fearOf(player);
        if (EntityType.byString(form).isEmpty()) {
            form = BoggartRules.DEFAULT_FORM;
        }
        setForm(form);
        this.fearedBy = player.getUUID();
        this.ticksWithoutPlayers = 0;
        this.setTarget(player);
        this.level().playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE,
                0.6F, 1.4F);
        ((ServerLevel) this.level()).sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 1.0D,
                this.getZ(), 40, 0.4D, 0.6D, 0.4D, 0.05D);
    }

    private void tickRevealed() {
        ServerLevel level = (ServerLevel) this.level();
        boolean anyoneNear = level.getNearestPlayer(this, BoggartRules.LOSE_INTEREST_RADIUS) != null;
        this.ticksWithoutPlayers = anyoneNear ? 0 : this.ticksWithoutPlayers + 1;
        if (BoggartRules.shouldHide(this.ticksWithoutPlayers)) {
            hide();
            return;
        }
        if (!isLaughing() && this.tickCount % BoggartRules.FEAR_PULSE_INTERVAL == 0) {
            pulseFear(level);
        }
    }

    /** Back into the dark: no shape, no hits, full health. */
    private void hide() {
        setForm("");
        setRiddikulusHits(0);
        setLaughTicks(0);
        this.fearedBy = null;
        this.ticksWithoutPlayers = 0;
        this.setTarget(null);
        this.getNavigation().stop();
        this.setHealth(this.getMaxHealth());
    }

    private void pulseFear(ServerLevel level) {
        double radius = BoggartRules.FEAR_RADIUS;
        double halfSqr = (radius / 2.0D) * (radius / 2.0D);
        for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius),
                p -> p.isAlive() && !p.isSpectator())) {
            double distSqr = this.distanceToSqr(player);
            if (distSqr > radius * radius) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BoggartRules.FEAR_DARKNESS_TICKS, 0,
                    false, false, true), this);
            if (distSqr <= halfSqr) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BoggartRules.FEAR_SLOWDOWN_TICKS,
                        0, false, false, true), this);
            }
            // Only the one whose fear it wears hears the cave sound: it is personal.
            if (player instanceof ServerPlayer sp && sp.getUUID().equals(this.fearedBy)) {
                level.playSound(null, this.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE,
                        1.0F, 0.9F);
            }
        }
    }

    // ------------------------------------------------------------- riddikulus

    @Override
    public void onRiddikulus(int rank, @Nullable Entity caster) {
        if (this.level().isClientSide || !this.isAlive()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        this.lastRank = Math.max(1, Math.min(4, rank));
        if (isHidden()) {
            // A hidden blob has nothing to make silly, but the charm still finds a target: wear the caster's fear.
            if (caster instanceof ServerPlayer sp) {
                reveal(sp);
            } else {
                setForm(BoggartRules.DEFAULT_FORM);
            }
        }
        setRiddikulusHits(getRiddikulusHits() + 1);
        setLaughTicks(BoggartRules.LAUGH_TICKS);
        this.setTarget(null);
        this.getNavigation().stop();

        for (Player player : level.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(BoggartRules.FEAR_RADIUS))) {
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        level.playSound(null, this.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.HOSTILE, 1.0F, 1.6F);
        level.sendParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.2D, this.getZ(),
                12, 0.5D, 0.5D, 0.5D, 1.0D);

        if (getRiddikulusHits() >= BoggartRules.hitsToBanish(this.lastRank)) {
            banish(caster);
        }
    }

    /** Gone in a puff of laughter. Discarded, not killed: no drops, no death animation. */
    private void banish(@Nullable Entity caster) {
        ServerLevel level = (ServerLevel) this.level();
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0D, this.getZ(),
                30, 0.5D, 0.7D, 0.5D, 0.04D);
        level.sendParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.4D, this.getZ(),
                20, 0.7D, 0.6D, 0.7D, 1.0D);
        level.playSound(null, this.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.HOSTILE,
                1.0F, 1.1F);
        if (caster instanceof Player player) {
            grant(player, ADVANCEMENT);
        }
        this.discard();
    }

    /**
     * Completes hogwartsmagic:hp/name for a real server player. Copied from BrewAdvancements.grant with the same
     * guard: a client player, a fake player, a missing advancement or one already done is ignored.
     */
    private static void grant(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || serverPlayer.server.getPlayerList().getPlayer(serverPlayer.getUUID()) != serverPlayer) {
            return;
        }
        AdvancementHolder advancement = serverPlayer.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hp/" + name));
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = serverPlayer.getAdvancements();
        AdvancementProgress state = progress.getOrStartProgress(advancement);
        if (state.isDone()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        state.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            progress.award(advancement, criterion);
        }
    }

    // ---------------------------------------------------------------- plumbing

    @Override
    public boolean doHurtTarget(Entity target) {
        // It giggles in place; a laughing Boggart lands no blows.
        return !isLaughing() && !isHidden() && super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !this.level().isClientSide && isHidden() && source.getEntity() instanceof ServerPlayer sp) {
            // Poked while hiding: it turns on whoever did it.
            reveal(sp);
        }
        return hit;
    }

    /** No drops of any kind: it is smoke and fear, not meat. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isPersistenceRequired();
    }

    public static boolean checkBoggartSpawnRules(EntityType<BoggartEntity> type, ServerLevelAccessor level,
                                                 MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        // Indoors or under cover only: open sky is not where a Boggart lives.
        if (level.getBrightness(LightLayer.SKY, pos) != 0) {
            return false;
        }
        // At most one per neighbourhood.
        double spacing = BoggartRules.SPAWN_SPACING;
        return level.getEntitiesOfClass(BoggartEntity.class, new AABB(pos).inflate(spacing), LivingEntity::isAlive)
                .isEmpty();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        if (isHidden()) {
            return null;
        }
        return isLaughing() ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.AMBIENT_CAVE.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Form", getFormId());
        tag.putInt("RiddikulusHits", getRiddikulusHits());
        tag.putInt("LaughTicks", getLaughTicks());
        tag.putInt("LastRank", this.lastRank);
        if (this.fearedBy != null) {
            tag.putUUID("FearedBy", this.fearedBy);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setForm(tag.getString("Form"));
        setRiddikulusHits(tag.getInt("RiddikulusHits"));
        setLaughTicks(tag.getInt("LaughTicks"));
        this.lastRank = tag.contains("LastRank") ? Math.max(1, Math.min(4, tag.getInt("LastRank"))) : 1;
        this.fearedBy = tag.hasUUID("FearedBy") ? tag.getUUID("FearedBy") : null;
    }
}
