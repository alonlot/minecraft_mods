package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class VoldemortEntity extends WizardEntity {
    private static final Component DEFAULT_NAME = Component.literal("Lord Voldemort");
    private static final ResourceLocation DARK_WIZARD_ADVANCEMENT_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hp/dark_wizard_down");
    private static final ResourceLocation VOLDEMORT_ADVANCEMENT_ID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hp/voldemort_falls");
    private final ServerBossEvent bossBar = new ServerBossEvent(DEFAULT_NAME, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);

    @Nullable
    private SpellType queuedSpell;
    private int queuedSpellTicks;
    private int attackCooldown;
    private int stormRefreshCooldown;
    private int lightningCooldown;

    public VoldemortEntity(EntityType<? extends net.minecraft.world.entity.monster.AbstractIllager> type, Level level) {
        super(type, level);
        this.bossBar.setVisible(true);
        this.attackCooldown = 40;
        this.stormRefreshCooldown = 10;
        this.lightningCooldown = 15;
        this.xpReward = 250;
        this.setCustomName(DEFAULT_NAME);
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.ARMOR, 8.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new VoldemortSpellGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Villager.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.animal.IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, DifficultyInstance difficulty) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.setRaidLeader(false);
        this.setCustomName(DEFAULT_NAME);
        this.setCustomNameVisible(true);
        this.setHealth(this.getMaxHealth());
        return data;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossBar.setName(this.getDisplayName());
        this.bossBar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));

        if (this.level() instanceof ServerLevel serverLevel) {
            if (--this.stormRefreshCooldown <= 0) {
                this.stormRefreshCooldown = 20;
                serverLevel.setWeatherParameters(0, 20 * 30, true, true);
            }

            if (--this.lightningCooldown <= 0) {
                this.lightningCooldown = 14 + this.getRandom().nextInt(18);
                spawnThreatLightning(serverLevel);
            }

            emitAura(serverLevel);
        }

        if (isCastingLockedOut() && this.queuedSpell != null) {
            cancelQueuedSpell(true);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource damageSource) {
        super.die(damageSource);
        if (this.level() instanceof ServerLevel serverLevel) {
            awardKillAdvancement(serverLevel, damageSource);
        }
        this.bossBar.removeAllPlayers();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource, boolean hitByPlayer) {
        super.dropCustomDeathLoot(serverLevel, damageSource, hitByPlayer);
        this.spawnAtLocation(new ItemStack(ExampleMod.ELDER_WAND.get()));
    }

    private void awardKillAdvancement(ServerLevel serverLevel, net.minecraft.world.damagesource.DamageSource damageSource) {
        java.util.LinkedHashSet<ServerPlayer> recipients = new java.util.LinkedHashSet<>();
        if (damageSource.getEntity() instanceof ServerPlayer player) {
            recipients.add(player);
        } else if (damageSource.getDirectEntity() instanceof SpellBeamEntity beam && beam.getOwner() instanceof ServerPlayer player) {
            recipients.add(player);
        } else if (this.getKillCredit() instanceof ServerPlayer player) {
            recipients.add(player);
        }

        for (ServerPlayer nearby : serverLevel.players()) {
            if (nearby.isAlive() && nearby.distanceToSqr(this) <= 96.0D * 96.0D) {
                recipients.add(nearby);
            }
        }

        if (recipients.isEmpty()) {
            return;
        }

        AdvancementHolder darkWizardAdvancement = serverLevel.getServer().getAdvancements().get(DARK_WIZARD_ADVANCEMENT_ID);
        AdvancementHolder voldemortAdvancement = serverLevel.getServer().getAdvancements().get(VOLDEMORT_ADVANCEMENT_ID);
        if (darkWizardAdvancement == null && voldemortAdvancement == null) {
            return;
        }

        for (ServerPlayer recipient : recipients) {
            grantAllCriteria(recipient, darkWizardAdvancement);
            grantAllCriteria(recipient, voldemortAdvancement);
            recipient.displayClientMessage(Component.literal("Advancement granted: The Dark Lord Falls"), false);
        }
    }

    private static void grantAllCriteria(ServerPlayer player, @Nullable AdvancementHolder advancement) {
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        var advancementProgress = progress.getOrStartProgress(advancement);
        for (String criterion : advancementProgress.getRemainingCriteria()) {
            progress.award(advancement, criterion);
        }
    }

    /** True while the Tenebris-Flight evasion effect is active. Used by the renderer
     *  to drop into a hover pose (legs hanging, slight forward lean) instead of the
     *  default walk cycle. */
    public boolean isHovering() {
        return this.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get());
    }

    /** True while the AK windup is queued. Renderer uses this to trigger a more
     *  theatrical pose (head tilts back, both arms slowly raise) than a normal cast. */
    public boolean isChargingAvadaKedavra() {
        return this.queuedSpell == SpellType.AVADA_KEDAVRA && this.queuedSpellTicks > 0;
    }

    public boolean tryEvadeAvada(@Nullable Entity attacker) {
        if (isCastingLockedOut()) {
            return false;
        }
        if (this.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get())) {
            return true;
        }
        long now = this.level().getGameTime();

        if (attacker instanceof LivingEntity livingAttacker) {
            this.setTarget(livingAttacker);
        }

        cancelQueuedSpell(false);
        this.swing(InteractionHand.MAIN_HAND);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SPELL_MORSMORDRE.get(), SoundSource.HOSTILE, 1.0f, 1.4f);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.7f);
        this.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
        this.setSpellCooldown(SpellType.TENEBRIS_FLIGHT, now + getBossCooldown(SpellType.TENEBRIS_FLIGHT));
        return true;
    }

    private boolean isCastingLockedOut() {
        // Stunned (Stupefy, Petrificus Totalus, a Glacius freeze) or silenced. Incarcerous only tethers
        // him now: a bound target can still fight back, so it no longer stops his casting.
        if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(this)) {
            return true;
        }
        if (this.hasEffect(MobEffects.BLINDNESS) || this.hasEffect(MobEffects.LEVITATION)) {
            return true;
        }
        MobEffectInstance slowness = this.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slowness != null && slowness.getAmplifier() >= 4) {
            return true;
        }
        MobEffectInstance jump = this.getEffect(MobEffects.JUMP);
        return jump != null && jump.getAmplifier() >= 200;
    }

    private void cancelQueuedSpell(boolean interrupted) {
        this.queuedSpell = null;
        this.queuedSpellTicks = 0;
        this.setCasting(false);
        if (interrupted && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.2D, this.getZ(), 18, 0.35D, 0.45D, 0.35D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.3D, this.getZ(), 10, 0.25D, 0.35D, 0.25D, 0.05D);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8f, 0.8f);
        }
    }

    /** Stupefy and other stuns call this on hit: a stunned boss skips his AI step, so the lockout check can't. */
    public void interruptCasting() {
        if (this.queuedSpell != null) {
            cancelQueuedSpell(true);
        }
    }

    private void queueSpell(SpellType spell, int warmupTicks) {
        this.queuedSpell = spell;
        this.queuedSpellTicks = warmupTicks;
        this.setCasting(true);
        this.swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Continuous dark aura — a thin smoky halo of soul-fire and squid-ink particles
     * that orbits Voldemort and trails a wisp upward. Drastically cheaper than the
     * casting warning particles since it ticks every server tick. Density doubles
     * while the AK windup is queued so the cast feels visibly more dangerous.
     */
    private void emitAura(ServerLevel serverLevel) {
        boolean charging = this.queuedSpell == SpellType.AVADA_KEDAVRA;
        // Orbiting ring around the chest.
        int ringCount = charging ? 6 : 3;
        for (int i = 0; i < ringCount; i++) {
            double angle = (this.tickCount * 0.18D) + (Math.PI * 2.0D * i) / ringCount;
            double r = 0.55D + (charging ? 0.15D * Math.sin(this.tickCount * 0.3D) : 0.0D);
            double x = this.getX() + Math.cos(angle) * r;
            double y = this.getY() + 1.2D;
            double z = this.getZ() + Math.sin(angle) * r;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // Wispy ground smoke that drifts upward — gives a "robe trailing dark mist" look.
        if (this.tickCount % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    this.getX() + (this.getRandom().nextDouble() - 0.5D) * 0.6D,
                    this.getY() + 0.15D,
                    this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 0.6D,
                    1, 0.0D, 0.02D, 0.0D, 0.01D);
        }
        // While AK is charging, a vertical column of warning sparks rises from the wand.
        if (charging && this.tickCount % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    this.getX(), this.getY() + 1.5D, this.getZ(),
                    2, 0.1D, 0.1D, 0.1D, 0.04D);
        }
    }

    private void spawnThreatLightning(ServerLevel serverLevel) {
        BlockPos strikePos = serverLevel.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(
                        this.getX() + (this.getRandom().nextDouble() - 0.5D) * 18.0D,
                        this.getY(),
                        this.getZ() + (this.getRandom().nextDouble() - 0.5D) * 18.0D
                )
        );
        var lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning != null) {
            lightning.moveTo(Vec3.atBottomCenterOf(strikePos));
            lightning.setVisualOnly(true);
            serverLevel.addFreshEntity(lightning);
        }
    }

    private int getBossCooldown(SpellType spell) {
        return switch (spell) {
            case AVADA_KEDAVRA -> 140;
            case FIENDFYRE -> 130;
            case CRUCIO, DOLOHOV_CURSE, BOMBARDA -> 75;
            case SECTUMSEMPRA, STUPEFY, SERPENSORTIA, DEPULSO -> 48;
            case PROTEGO -> 65;
            case HEAL -> 145;
            case TENEBRIS_FLIGHT -> 20;
            default -> Math.max(36, spell.getCooldownTicks() / 3);
        };
    }

    private void emitCastingWarning(ServerLevel serverLevel, LivingEntity target) {
        double handX = this.getX();
        double handY = this.getY() + 1.35D;
        double handZ = this.getZ();
        boolean avada = this.queuedSpell == SpellType.AVADA_KEDAVRA;

        if (avada) {
            double charge = 1.0D - (this.queuedSpellTicks / 60.0D);
            double radius = 0.28D + charge * 0.9D;
            double heightStep = 0.16D + charge * 0.1D;
            for (int i = 0; i < 4; i++) {
                double angle = this.tickCount * 0.42D + (Math.PI * 0.5D * i);
                double x = handX + Math.cos(angle) * radius;
                double y = handY - 0.15D + i * heightStep;
                double z = handZ + Math.sin(angle) * radius;
                serverLevel.sendParticles(this.queuedSpell.getDustParticle(), x, y, z, 2, 0.03D, 0.03D, 0.03D, 0.0D);
                if (i % 2 == 0) {
                    serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
                }
            }
            serverLevel.sendParticles(ParticleTypes.WITCH, handX, handY, handZ, 2, 0.12D, 0.12D, 0.12D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, handX, handY, handZ, 3, 0.12D, 0.12D, 0.12D, 0.02D);
            if (this.queuedSpellTicks % 10 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.7f, 0.45f + (60 - this.queuedSpellTicks) * 0.008f);
            }
        } else {
            serverLevel.sendParticles(this.queuedSpell.getDustParticle(), handX, handY, handZ, 6, 0.14D, 0.14D, 0.14D, 0.01D);
            Vec3 targetPos = target.getEyePosition();
            Vec3 source = new Vec3(handX, handY, handZ);
            Vec3 delta = targetPos.subtract(source);
            for (int i = 0; i < 6; i++) {
                double progress = i / 6.0D;
                Vec3 point = source.add(delta.scale(progress));
                serverLevel.sendParticles(this.queuedSpell.getTrailParticle(),
                        point.x, point.y, point.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
            }
        }
    }

    private void executeQueuedSpell(LivingEntity target) {
        SpellType spell = this.queuedSpell;
        this.queuedSpell = null;
        this.queuedSpellTicks = 0;
        this.setCasting(false);
        if (spell == null) {
            return;
        }

        long now = this.level().getGameTime();
        this.setSpellCooldown(spell, now + getBossCooldown(spell));

        if (spell == SpellType.HEAL) {
            this.heal(28.0F);
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0f, 0.8f);
            return;
        }

        if (spell == SpellType.PROTEGO) {
            this.addEffect(new MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), 220, 2));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SPELL_PROTEGO.get(), SoundSource.HOSTILE, 1.0f, 0.95f);
            return;
        }

        this.swing(InteractionHand.MAIN_HAND);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), spell.getSoundEvent(), SoundSource.HOSTILE, 1.2f, spell == SpellType.AVADA_KEDAVRA ? 0.75f : 1.0f);

        SpellBeamEntity beam = new SpellBeamEntity(this.level(), this, spell);
        if (spell == SpellType.ARRESTO_MOMENTUM) {
            beam.setHomingTarget(target);
        }
        double d0 = target.getX() - this.getX();
        double d1 = target.getY(0.5D) - beam.getY();
        double d2 = target.getZ() - this.getZ();
        float velocity = spell == SpellType.AVADA_KEDAVRA ? 1.95F : 1.75F;
        float inaccuracy = spell == SpellType.AVADA_KEDAVRA ? 0.08F : 0.18F;
        beam.shoot(d0, d1, d2, velocity, inaccuracy);
        this.level().addFreshEntity(beam);
        this.attackCooldown = spell == SpellType.AVADA_KEDAVRA ? 42 : 26 + this.getRandom().nextInt(16);
    }

    @Nullable
    private SpellType selectSpell(LivingEntity target, long now, double distSq) {
        if (this.getHealth() < this.getMaxHealth() * 0.45F && !isSpellOnCooldown(SpellType.HEAL, now) && this.getRandom().nextFloat() < 0.22f) {
            return SpellType.HEAL;
        }
        if (this.wasRecentlyHit(now) && !this.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get()) && !isSpellOnCooldown(SpellType.PROTEGO, now) && this.getRandom().nextFloat() < 0.35f) {
            return SpellType.PROTEGO;
        }
        if (!isSpellOnCooldown(SpellType.AVADA_KEDAVRA, now) && this.getRandom().nextFloat() < 0.28f) {
            return SpellType.AVADA_KEDAVRA;
        }

        List<SpellType> spells = new ArrayList<>();
        addWeightedSpell(spells, SpellType.CRUCIO, now, 3);
        addWeightedSpell(spells, SpellType.FIENDFYRE, now, 3);
        addWeightedSpell(spells, SpellType.DOLOHOV_CURSE, now, 3);
        addWeightedSpell(spells, SpellType.SECTUMSEMPRA, now, 3);
        addWeightedSpell(spells, SpellType.BOMBARDA, now, 2);
        addWeightedSpell(spells, SpellType.SERPENSORTIA, now, 2);
        addWeightedSpell(spells, SpellType.STUPEFY, now, 2);
        if (distSq < 16.0D) {
            addWeightedSpell(spells, SpellType.DEPULSO, now, 3);
        }
        if (spells.isEmpty()) {
            if (!isSpellOnCooldown(SpellType.TENEBRIS_FLIGHT, now)) {
                return SpellType.TENEBRIS_FLIGHT;
            }
            return null;
        }
        return spells.get(this.getRandom().nextInt(spells.size()));
    }

    private void addWeightedSpell(List<SpellType> spells, SpellType spell, long now, int weight) {
        if (isSpellOnCooldown(spell, now)) {
            return;
        }
        for (int i = 0; i < weight; i++) {
            spells.add(spell);
        }
    }

    static class VoldemortSpellGoal extends Goal {
        private final VoldemortEntity voldemort;

        VoldemortSpellGoal(VoldemortEntity voldemort) {
            this.voldemort = voldemort;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.voldemort.getTarget();
            return target != null && target.isAlive() && this.voldemort.canAttack(target);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            this.voldemort.cancelQueuedSpell(false);
        }

        @Override
        public void tick() {
            LivingEntity target = this.voldemort.getTarget();
            if (target == null || !target.isAlive()) {
                this.voldemort.cancelQueuedSpell(false);
                return;
            }

            if (this.voldemort.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get())) {
                this.voldemort.setCasting(false);
                return;
            }

            this.voldemort.getLookControl().setLookAt(target, 40.0F, 40.0F);
            double distSq = this.voldemort.distanceToSqr(target);

            if (this.voldemort.queuedSpell != null) {
                if (this.voldemort.isCastingLockedOut()) {
                    this.voldemort.cancelQueuedSpell(true);
                    this.voldemort.attackCooldown = 16;
                    return;
                }

                this.voldemort.getNavigation().stop();
                this.voldemort.setCasting(true);
                if (this.voldemort.level() instanceof ServerLevel serverLevel) {
                    this.voldemort.emitCastingWarning(serverLevel, target);
                }
                if (--this.voldemort.queuedSpellTicks <= 0) {
                    this.voldemort.executeQueuedSpell(target);
                }
                return;
            }

            if (this.voldemort.attackCooldown > 0) {
                this.voldemort.attackCooldown--;
            }

            if (distSq > 144.0D) {
                this.voldemort.getNavigation().moveTo(target, 1.15D);
            } else {
                this.voldemort.getNavigation().stop();
            }

            if (this.voldemort.attackCooldown > 0) {
                return;
            }

            long now = this.voldemort.level().getGameTime();
            SpellType nextSpell = this.voldemort.selectSpell(target, now, distSq);
            if (nextSpell == null) {
                this.voldemort.attackCooldown = 18;
                return;
            }

            if (nextSpell == SpellType.TENEBRIS_FLIGHT) {
                SpellType.TENEBRIS_FLIGHT.applyHitEffect(this.voldemort, this.voldemort);
                this.voldemort.setSpellCooldown(SpellType.TENEBRIS_FLIGHT, now + this.voldemort.getBossCooldown(SpellType.TENEBRIS_FLIGHT));
                this.voldemort.attackCooldown = 36;
                return;
            }

            if (nextSpell == SpellType.AVADA_KEDAVRA) {
                this.voldemort.queueSpell(nextSpell, 60);
            } else {
                this.voldemort.queuedSpell = nextSpell;
                this.voldemort.executeQueuedSpell(target);
            }
        }
    }
}
