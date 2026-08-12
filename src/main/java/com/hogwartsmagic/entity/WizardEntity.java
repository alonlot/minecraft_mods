package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.goal.Goal;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.event.ModEvents;

import net.minecraft.world.entity.Mob;

import java.util.EnumSet;
import java.util.Map;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;

public class WizardEntity extends AbstractIllager implements DisarmableWandUser {

    private static final EntityDataAccessor<Boolean> DATA_IS_CASTING = SynchedEntityData.defineId(WizardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_FLYING = SynchedEntityData.defineId(WizardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_RAID_LEADER = SynchedEntityData.defineId(WizardEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_DISARMED = SynchedEntityData.defineId(WizardEntity.class, EntityDataSerializers.BOOLEAN);

    // per-wizard cooldown tracking to mimic player wand behavior
    protected final Map<SpellType, Long> spellCooldowns = new EnumMap<>(SpellType.class);
    private long lastHitTime = 0; // Track when wizard was last hit for flight escape

    protected boolean isSpellOnCooldown(SpellType spell, long now) {
        return spellCooldowns.getOrDefault(spell, 0L) > now;
    }

    protected void setSpellCooldown(SpellType spell, long tickEnd) {
        spellCooldowns.put(spell, tickEnd);
    }

    public void recordHit() {
        this.lastHitTime = this.level().getGameTime();
    }

    public boolean wasRecentlyHit(long now) {
        return (now - lastHitTime) < 20; // within 1 second
    }

    public WizardEntity(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        // flight effect is managed globally in ModEvents.onLivingUpdate now; no
        // need to randomly apply it here
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float damage) {
        // Intercept damage if we have Protego, unless it's void/starve/drown damage
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && this.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            net.minecraft.world.effect.MobEffectInstance currentEffect = this.getEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            if (currentEffect != null) {
                int currentAmplifier = currentEffect.getAmplifier();
                
                // Play absorption / clink sound
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.5f);
                
                // If it was the last charge, break the shield
                if (currentAmplifier <= 0) {
                    this.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
                    
                    // Break particles
                    if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                    }
                } else {
                    // Reduce charges
                    this.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), currentEffect.getDuration(), currentAmplifier - 1));
                }
                
                // Block the damage completely
                return false; 
            }
        }
        
        recordHit(); // Track that wizard was hit
        return super.hurt(source, damage);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        // Death Eaters never attack each other!
        if (target instanceof WizardEntity) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (this.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            return IllagerArmPose.CROSSED; // They hold hands up/crossed when shielding
        }
        if (this.isCasting()) {
            return IllagerArmPose.SPELLCASTING;
        }
        return this.isAggressive() ? IllagerArmPose.ATTACKING : IllagerArmPose.CROSSED;
    }

    @Override
    public void applyRaidBuffs(net.minecraft.server.level.ServerLevel p_33840_, int p_33841_, boolean p_33842_) {
    }

    @Override
    public net.minecraft.sounds.SoundEvent getCelebrateSound() {
        return net.minecraft.sounds.SoundEvents.EVOKER_CELEBRATE;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Retrieving a dropped wand outranks everything — a disarmed wizard's only objective.
        this.goalSelector.addGoal(0, new RetrieveDroppedWandGoal(this, 1.2D));
        this.goalSelector.addGoal(1, new WizardRaidLeaderGoal(this));
        this.goalSelector.addGoal(2, new WizardSpellCastGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.Villager.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.animal.IronGolem.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }



    /**
     * Natural-spawn rule for Death Eaters.
     *
     * <p>This exists mostly so that one is <i>registered</i>. With no {@code SpawnPlacements} entry
     * Minecraft falls back to {@code NO_RESTRICTIONS} and an always-true predicate, which means the
     * mob skips the ground, fluid and difficulty checks entirely and can appear standing in the air
     * or in a lake. Light is deliberately not checked — Death Eaters raid in daylight — so this is
     * the any-light rule, not the ordinary monster one.
     */
    public static boolean canSpawn(EntityType<WizardEntity> type,
                                   net.minecraft.world.level.ServerLevelAccessor level,
                                   net.minecraft.world.entity.MobSpawnType spawnType,
                                   net.minecraft.core.BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_CASTING, false);
        builder.define(DATA_IS_FLYING, false);
        builder.define(DATA_IS_RAID_LEADER, false);
        builder.define(DATA_IS_DISARMED, false);
    }

    @Override
    public boolean isDisarmed() {
        return this.entityData.get(DATA_IS_DISARMED);
    }

    @Override
    public void setDisarmed(boolean disarmed) {
        this.entityData.set(DATA_IS_DISARMED, disarmed);
        if (disarmed) {
            this.setCasting(false);
            this.getNavigation().stop();
        }
    }

    @Override
    public Mob asMob() {
        return this;
    }

    public void setCasting(boolean casting) {
        this.entityData.set(DATA_IS_CASTING, casting);
    }

    public boolean isCasting() {
        return this.entityData.get(DATA_IS_CASTING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_IS_FLYING, flying);
        // Don't set invisibility here - let the effect handle it!
        // Invisibility is managed by the TENEBRIS_FLIGHT_EFFECT duration
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_IS_FLYING);
    }

    public void setRaidLeader(boolean leader) {
        this.entityData.set(DATA_IS_RAID_LEADER, leader);
    }

    public boolean isRaidLeader() {
        return this.entityData.get(DATA_IS_RAID_LEADER);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag p_33353_) {
        super.addAdditionalSaveData(p_33353_);
        p_33353_.putBoolean("RaidLeader", this.isRaidLeader());
        p_33353_.putBoolean("Disarmed", this.isDisarmed());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("RaidLeader")) this.setRaidLeader(tag.getBoolean("RaidLeader"));
        if (tag.contains("Disarmed")) this.setDisarmed(tag.getBoolean("Disarmed"));
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        // Guarantee a wand even when /summon skips finalizeSpawn / populateDefaultEquipmentSlots —
        // but only if we're not currently disarmed (otherwise this would magic the wand back).
        if (!this.level().isClientSide && !this.isDisarmed() && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        }
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, InteractionHand hand) {
        // Sneak + right-click with a wand challenges the Death Eater to a formal duel.
        // Dark wizards respect dueling tradition — but they duel with dark magic.
        if (player.isShiftKeyDown() && com.hogwartsmagic.item.MagicWandItem.isAnyWand(player.getItemInHand(hand))) {
            if (!this.level().isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.hogwartsmagic.duel.DuelManager.requestDuel(sp, this);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> p_33340_) {
        super.onSyncedDataUpdated(p_33340_);
        if (DATA_IS_FLYING.equals(p_33340_)) {
            this.setInvisible(this.isFlying());
        }
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor p_33282_, net.minecraft.world.DifficultyInstance p_33283_, net.minecraft.world.entity.MobSpawnType p_33284_, @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData p_33285_) {
        net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(p_33282_, p_33283_, p_33284_, p_33285_);

        // Vanilla Mob.finalizeSpawn does NOT invoke populateDefaultEquipmentSlots automatically —
        // subclasses like Pillager/Zombie call it explicitly. Without this line our wand-setup
        // override was never running, so wizards spawned bare-handed.
        this.populateDefaultEquipmentSlots(this.random, p_33283_);

        // 10% chance to be a raid leader
        if (p_33284_ != net.minecraft.world.entity.MobSpawnType.EVENT && p_33284_ != net.minecraft.world.entity.MobSpawnType.COMMAND) {
            if (this.random.nextFloat() < 0.1f) {
                this.setRaidLeader(true);
                this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.WHITE_BANNER));
            }
        }
        
        // small chance the wizard spawns riding a broom
        if (p_33284_ != net.minecraft.world.entity.MobSpawnType.EVENT && this.random.nextFloat() < 0.05f) {
            Level lvl = (Level)p_33282_.getLevel();
            com.hogwartsmagic.entity.BroomEntity broom = new com.hogwartsmagic.entity.BroomEntity(com.hogwartsmagic.entity.ModEntities.BROOM.get(), lvl);
            broom.setPos(this.getX(), this.getY(), this.getZ());
            if (lvl.addFreshEntity(broom)) {
                this.startRiding(broom, true);
            }
            // give a short burst of flight effect so the wizard is already in smoke/invisible mode
            this.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
            setSpellCooldown(SpellType.TENEBRIS_FLIGHT, p_33282_.getLevel().getGameTime() + SpellType.TENEBRIS_FLIGHT.getCooldownTicks());
        }
        
        return data;
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
    }

    static class WizardRaidLeaderGoal extends Goal {
        private final WizardEntity wizard;
        private int checkTimer = 0;
        private int ritualTimer = 0;
        private boolean hasSummoned = false;

        public WizardRaidLeaderGoal(WizardEntity wizard) {
            this.wizard = wizard;
        }

        @Override
        public boolean canUse() {
            return this.wizard.isRaidLeader() && !hasSummoned;
        }

        @Override
        public void tick() {
            if (ritualTimer > 0) {
                ritualTimer--;
                this.wizard.getNavigation().stop();
                this.wizard.setCasting(true);
                
                // Ritual Particles
                if (this.wizard.level().isClientSide) {
                    for(int i = 0; i < 5; i++) {
                        this.wizard.level().addParticle(ParticleTypes.SQUID_INK, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(), 0, 0.2, 0);
                    }
                }
                
                if (ritualTimer == 0) {
                    startRaid();
                }
                return;
            }

            if (--checkTimer <= 0) {
                checkTimer = 20; // Check more frequently (every second)
                if (this.wizard.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    boolean nearVillagers = !serverLevel.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, this.wizard.getBoundingBox().inflate(32.0D)).isEmpty();
                    
                    if (nearVillagers) {
                        ritualTimer = 60; // 3 seconds of drama
                        this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                                com.hogwartsmagic.ModSounds.SPELL_MORSMORDRE.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.8f);
                    }
                }
            }
        }

        private void startRaid() {
            this.hasSummoned = true;
            this.wizard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            List<WizardEntity> raidMembers = new ArrayList<>();
            raidMembers.add(this.wizard);
            
            // Cast Mark
            DarkMarkEntity mark = new DarkMarkEntity(this.wizard.level(), this.wizard.getX(), this.wizard.getY() + 18, this.wizard.getZ(), 4800);
            this.wizard.level().addFreshEntity(mark);

            // Summon 5-9 wizards
            int count = 5 + this.wizard.getRandom().nextInt(5);
            for (int i = 0; i < count; i++) {
                double rx = this.wizard.getX() + (this.wizard.getRandom().nextDouble() - 0.5) * 40.0;
                double rz = this.wizard.getZ() + (this.wizard.getRandom().nextDouble() - 0.5) * 40.0;
                net.minecraft.core.BlockPos spawnPos = this.wizard.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.core.BlockPos.containing(rx, 0, rz));

                WizardEntity raidWizard = ModEntities.WIZARD.get().create(this.wizard.level());
                if (raidWizard != null) {
                    // spawn slightly above ground and kick them upward so they appear
                    raidWizard.setPos(spawnPos.getX(), spawnPos.getY() + 2.0, spawnPos.getZ());
                    raidWizard.finalizeSpawn((net.minecraft.server.level.ServerLevel)this.wizard.level(), this.wizard.level().getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.EVENT, null);
                    // Give Tenebris Flight for a short burst (same 2‑second duration) - invisibility will be handled by the effect
                    raidWizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
                    // 10% chance the raid wizard appears on a broom
                    if (this.wizard.random.nextFloat() < 0.1f) {
                        com.hogwartsmagic.entity.BroomEntity broom = new com.hogwartsmagic.entity.BroomEntity(com.hogwartsmagic.entity.ModEntities.BROOM.get(), this.wizard.level());
                        broom.setPos(raidWizard.getX(), raidWizard.getY(), raidWizard.getZ());
                        if (this.wizard.level().addFreshEntity(broom)) {
                            raidWizard.startRiding(broom, true);
                        }
                    }

                    this.wizard.level().addFreshEntity(raidWizard);
                    raidMembers.add(raidWizard);
                }
            }

            if (this.wizard.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                ModEvents.startWizardRaid(serverLevel, this.wizard.blockPosition(), raidMembers);
            }
        }
    }

    static class WizardSpellCastGoal extends Goal {
        // utility to pick a weighted spell list and handle cooldowns
        private final WizardEntity wizard;
        private int attackTime;
        private int castPhaseTime; // Time spent with arms raised before firing
        private int escapeTime; // Time to spend fleeing after flight effect ends

        // Fiendfyre charged cast. When the wizard rolls Fiendfyre, instead of firing
        // immediately we hold the wand aloft for FIENDFYRE_CHARGE_TICKS, emitting flame
        // particles around the wand to broadcast the danger. After the charge finishes,
        // a fast spell beam launches and the existing FIENDFYRE.applyHitEffect spawns
        // the spreading fire-serpent (FiendfyreEntity) at the impact point.
        private static final int FIENDFYRE_CHARGE_TICKS = 36;
        private int fiendfyreChargeTicks;

        public WizardSpellCastGoal(WizardEntity wizard) {
            this.wizard = wizard;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
            this.escapeTime = 0;
            this.fiendfyreChargeTicks = 0;
        }

        @Override
        public boolean canUse() {
            if (this.wizard.isDisarmed()) return false;
            LivingEntity livingentity = this.wizard.getTarget();
            return livingentity != null && livingentity.isAlive() && this.wizard.canAttack(livingentity);
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse();
        }

        @Override
        public void start() {
            this.attackTime = 20;
            this.castPhaseTime = 0;
            this.escapeTime = 0;
        }

        @Override
        public void stop() {
            this.wizard.setCasting(false);
            this.castPhaseTime = 0;
            this.fiendfyreChargeTicks = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.wizard.getPersistentData().getBoolean("hogwartsmagic:tenebris_escape_after_hit")) {
                this.escapeTime = 30;
                this.wizard.getPersistentData().remove("hogwartsmagic:tenebris_escape_after_hit");
            }
            boolean isFlying = this.wizard.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get());

            // abort if flight effect active
            if (isFlying) {
                this.wizard.getNavigation().stop();
                this.wizard.setCasting(false);
                this.fiendfyreChargeTicks = 0;
                return;
            }

            // Fiendfyre charge takes precedence over any other behaviour. The wizard
            // freezes in place with arms raised, emits flame around the wand each
            // tick, and only fires when the charge counter hits zero.
            if (this.fiendfyreChargeTicks > 0) {
                LivingEntity chargeTarget = this.wizard.getTarget();
                if (chargeTarget == null || !chargeTarget.isAlive()) {
                    this.fiendfyreChargeTicks = 0;
                    this.wizard.setCasting(false);
                    return;
                }
                this.wizard.getNavigation().stop();
                this.wizard.getLookControl().setLookAt(chargeTarget, 30.0F, 30.0F);
                this.wizard.setCasting(true);
                emitFiendfyreChargeParticles();
                if (--this.fiendfyreChargeTicks <= 0) {
                    releaseFiendfyreSerpent(chargeTarget);
                    this.wizard.setCasting(false);
                    this.attackTime = 60 + this.wizard.getRandom().nextInt(40);
                }
                return;
            }

            // If in escape mode, flee from target
            if (this.escapeTime > 0) {
                this.escapeTime--;
                LivingEntity target = this.wizard.getTarget();
                if (target != null) {
                    // Move away from target for a short distance at moderate speed
                    net.minecraft.world.phys.Vec3 fleeDir = this.wizard.position()
                            .subtract(target.position())
                            .normalize();
                    this.wizard.getNavigation().moveTo(
                            this.wizard.getX() + fleeDir.x * 8,
                            this.wizard.getY(),
                            this.wizard.getZ() + fleeDir.z * 8,
                            1.1D
                    );
                }
                this.wizard.setCasting(false);
                this.castPhaseTime = 0;
                return;
            }

            LivingEntity target = this.wizard.getTarget();
            if (target == null) return;

            long now = this.wizard.level().getGameTime();
            double distSq = this.wizard.distanceToSqr(target);

            // Strategic flight triggering:
            // 1. If recently hit by a spell, use flight to escape
            // 2. If enemy approaching and low health, use flight to attack and escape
            boolean shouldFly = false;
            if (this.wizard.wasRecentlyHit(now)
                    && !this.wizard.isSpellOnCooldown(SpellType.TENEBRIS_FLIGHT, now)
                    && this.wizard.getRandom().nextFloat() < 0.25f) {
                shouldFly = true; // Was hit, occasionally escape via flight
            } else if (distSq < 64.0D
                    && this.wizard.getHealth() < this.wizard.getMaxHealth() * 0.35f
                    && !this.wizard.isSpellOnCooldown(SpellType.TENEBRIS_FLIGHT, now)
                    && this.wizard.getRandom().nextFloat() < 0.2f) {
                shouldFly = true; // Rare panic flight when very threatened
            }

            if (shouldFly) {
                SpellType.TENEBRIS_FLIGHT.applyHitEffect(this.wizard, this.wizard);
                this.wizard.setSpellCooldown(SpellType.TENEBRIS_FLIGHT, now + SpellType.TENEBRIS_FLIGHT.getCooldownTicks());
                return;
            }

            this.wizard.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (distSq < 4.0D) {
                // Too close: use pushback spells instead of melee.
                this.wizard.getNavigation().stop();
                if (--this.attackTime <= 0) {
                    this.attackTime = 20;
                    if (!this.castRepelSpell(target, now)) {
                        net.minecraft.world.phys.Vec3 fleeDir = this.wizard.position().subtract(target.position()).normalize();
                        this.wizard.setDeltaMovement(fleeDir.x * 0.6D, Math.max(0.1D, this.wizard.getDeltaMovement().y), fleeDir.z * 0.6D);
                    }
                }
                this.wizard.setCasting(false);
                this.castPhaseTime = 0;
            } else if (distSq < this.getFollowDistance() * this.getFollowDistance()) {
                // Ranged spell attack
                if (this.castPhaseTime > 0) {
                    // We are in the "raising arms" phase
                    this.wizard.setCasting(true);
                    this.wizard.getNavigation().stop();

                    if (--this.castPhaseTime <= 0) {
                        this.castSpell(target);
                        this.wizard.setCasting(false);
                        this.attackTime = 40 + this.wizard.getRandom().nextInt(40); // Cooldown 2-4 seconds
                    }
                } else {
                    // Wait for cooldown or start casting
                    if (--this.attackTime <= 0) {
                        this.castPhaseTime = 20; // 1 second of "preparing"
                    }

                    if (distSq > 100.0D) { // Move closer if fairly far
                        this.wizard.getNavigation().moveTo(target, 1.0D);
                    } else {
                        this.wizard.getNavigation().stop();
                    }
                }
            } else {
                this.wizard.getNavigation().moveTo(target, 1.0D);
                this.wizard.setCasting(false);
                this.castPhaseTime = 0;
            }

            super.tick();
        }

        private double getFollowDistance() {
            return this.wizard.getAttributeValue(Attributes.FOLLOW_RANGE);
        }

        private boolean castRepelSpell(LivingEntity target, long now) {
            SpellType spell = null;
            if (!this.wizard.isSpellOnCooldown(SpellType.DEPULSO, now)) {
                spell = SpellType.DEPULSO;
            } else if (!this.wizard.isSpellOnCooldown(SpellType.EXPELLIARMUS, now)) {
                spell = SpellType.EXPELLIARMUS;
            }

            if (spell == null) {
                return false;
            }

            if (spell != SpellType.PROTEGO && this.wizard.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
                this.wizard.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            }

            this.wizard.swing(InteractionHand.MAIN_HAND);
            this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                    spell.getSoundEvent(), SoundSource.HOSTILE, 1.0f, 1.0f);

            SpellBeamEntity beam = new SpellBeamEntity(this.wizard.level(), this.wizard, spell);
            if (spell == SpellType.ARRESTO_MOMENTUM) {
                beam.setHomingTarget(target);
            }
            double d0 = target.getX() - this.wizard.getX();
            double d1 = target.getY(0.5D) - beam.getY();
            double d2 = target.getZ() - this.wizard.getZ();
            beam.shoot(d0, d1, d2, 1.7F, 0.6F);
            this.wizard.level().addFreshEntity(beam);
            this.wizard.setSpellCooldown(spell, now + spell.getCooldownTicks());
            return true;
        }

        private void castSpell(LivingEntity target) {
            long now = this.wizard.level().getGameTime();
            // if low on health, immediately cast heal if available
            if (this.wizard.getHealth() < this.wizard.getMaxHealth() * 0.3f && !this.wizard.isSpellOnCooldown(SpellType.HEAL, now)) {
                SpellType heal = SpellType.HEAL;
                heal.applyHitEffect(this.wizard, this.wizard);
                this.wizard.setSpellCooldown(heal, now + heal.getCooldownTicks());
                return;
            }
            
            // Reactive Protego casting: only if attacked recently or a spell is incoming
            boolean needsShield = false;
            if (this.wizard.wasRecentlyHit(now)) {
                needsShield = true;
            } else {
                java.util.List<com.hogwartsmagic.entity.SpellBeamEntity> incoming = this.wizard.level().getEntitiesOfClass(
                        com.hogwartsmagic.entity.SpellBeamEntity.class, this.wizard.getBoundingBox().inflate(8.0));
                for (com.hogwartsmagic.entity.SpellBeamEntity beam : incoming) {
                    if (beam.getOwner() != this.wizard) {
                        needsShield = true;
                        break;
                    }
                }
            }
            if (needsShield && !this.wizard.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get()) && !this.wizard.isSpellOnCooldown(SpellType.PROTEGO, now)) {
                // Cast self-shield (with 1 extra charge = amplifier 1 = absorbs 2 hits)
                this.wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), 300, 1));
                this.wizard.setSpellCooldown(SpellType.PROTEGO, now + SpellType.PROTEGO.getCooldownTicks());
                this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                        ModSounds.SPELL_PROTEGO.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
                this.wizard.swing(InteractionHand.MAIN_HAND);
                return;
            }
            // build weighted list of candidate spells
            List<SpellType> spells = new ArrayList<>();
            // add heavy offensive spells once (avada will be extremely rare via override)
            spells.add(SpellType.CRUCIO);
            spells.add(SpellType.IMPERIO);
            spells.add(SpellType.FIENDFYRE);
            // add some standard spells with slight weight
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.EXPELLIARMUS);
            spells.add(SpellType.CONFRINGO);
            spells.add(SpellType.INCENDIO);
            spells.add(SpellType.GLACIUS);
            spells.add(SpellType.ACCIO);
            spells.add(SpellType.SECTUMSEMPRA);
            spells.add(SpellType.DOLOHOV_CURSE);
            spells.add(SpellType.AVADA_KEDAVRA);
            // include flight occasionally and heal occasionally
            spells.add(SpellType.TENEBRIS_FLIGHT);
            spells.add(SpellType.HEAL);

            // filter out those currently on cooldown
            spells.removeIf(s -> this.wizard.isSpellOnCooldown(s, now));
            if (spells.isEmpty()) {
                return; // nothing we can cast right now
            }
            SpellType spell = spells.get(this.wizard.getRandom().nextInt(spells.size()));
            // small chance to choose flight when far away
            if (!this.wizard.hasEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get())
                    && !this.wizard.isSpellOnCooldown(SpellType.TENEBRIS_FLIGHT, now)
                    && target != null && this.wizard.distanceToSqr(target) > 100.0
                    && this.wizard.getRandom().nextFloat() < 0.12f) {
                spell = SpellType.TENEBRIS_FLIGHT;
            }
            // very small chance to upgrade to Avada Kedavra
            if (this.wizard.getRandom().nextFloat() < 0.01f) {
                spell = SpellType.AVADA_KEDAVRA;
            }

            // perform spell
            if (spell == SpellType.FIENDFYRE) {
                // Begin the charged windup — actual fire-serpent launches once the
                // counter ticks down to zero in the goal's tick loop.
                this.fiendfyreChargeTicks = FIENDFYRE_CHARGE_TICKS;
                this.wizard.setCasting(true);
                this.wizard.swing(InteractionHand.MAIN_HAND);
                this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                        net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.6f, 0.55f);
                this.wizard.setSpellCooldown(SpellType.FIENDFYRE, now + SpellType.FIENDFYRE.getCooldownTicks());
                return;
            } else if (spell == SpellType.TENEBRIS_FLIGHT) {
                spell.applyHitEffect(this.wizard, this.wizard);
            } else if (spell == SpellType.HEAL) {
                // heal self
                spell.applyHitEffect(this.wizard, this.wizard);
            } else {
                // Break Protego if casting offensive spell
                if (spell != SpellType.PROTEGO && this.wizard.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
                     this.wizard.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
                     this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                             net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f);
                     if (this.wizard.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                          sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, this.wizard.getX(), this.wizard.getY() + 1.0, this.wizard.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                     }
                }
            
                this.wizard.swing(InteractionHand.MAIN_HAND);
                this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                        spell.getSoundEvent(), SoundSource.HOSTILE, 1.0f, 1.0f);

                SpellBeamEntity beam = new SpellBeamEntity(this.wizard.level(), this.wizard, spell);
                if (spell == SpellType.ARRESTO_MOMENTUM) {
                    beam.setHomingTarget(target);
                }
                double d0 = target.getX() - this.wizard.getX();
                double d1 = target.getY(0.5D) - beam.getY();
                double d2 = target.getZ() - this.wizard.getZ();
                beam.shoot(d0, d1, d2, 1.5F, 1.0F); // Reduced inaccuracy for wizards
                this.wizard.level().addFreshEntity(beam);
            }
            // apply cooldown for the spell
            this.wizard.setSpellCooldown(spell, now + spell.getCooldownTicks());
        }

        // ---- fiendfyre charged cast ----------------------------------------------------

        /**
         * Spawn flame, lava-drip and soul-flame particles each tick of the charge so
         * the windup reads as the wizard barely containing the fire. Particle density
         * ramps up as the charge nears completion.
         */
        private void emitFiendfyreChargeParticles() {
            if (!(this.wizard.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
            float progress = 1.0f - (this.fiendfyreChargeTicks / (float) FIENDFYRE_CHARGE_TICKS);
            double handX = this.wizard.getX();
            double handY = this.wizard.getY() + 1.45D;
            double handZ = this.wizard.getZ();
            int flameCount = Math.max(2, (int) (2 + progress * 6));

            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    handX, handY, handZ, flameCount, 0.25D, 0.25D, 0.25D, 0.02D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    handX, handY, handZ, flameCount, 0.20D, 0.20D, 0.20D, 0.0D);
            if (this.fiendfyreChargeTicks % 4 == 0) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.DRIPPING_LAVA,
                        handX, handY + 0.2D, handZ, 1, 0.15D, 0.05D, 0.15D, 0.0D);
            }
            // Audible heartbeat / fire roar that quickens as the charge fills.
            if (this.fiendfyreChargeTicks % Math.max(2, 10 - (int)(progress * 8)) == 0) {
                this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                        net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE,
                        0.5f + progress * 0.5f, 0.7f + progress * 0.4f);
            }
        }

        /**
         * Launch the fire-serpent. Implemented as a high-velocity FIENDFYRE
         * SpellBeamEntity — its existing on-hit path spawns the spreading
         * FiendfyreEntity, so the serpent reads as a flaming projectile that
         * leaves a real fiendfyre at the strike point.
         */
        private void releaseFiendfyreSerpent(LivingEntity target) {
            this.wizard.swing(InteractionHand.MAIN_HAND);
            this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                    SpellType.FIENDFYRE.getSoundEvent(), SoundSource.HOSTILE, 1.4f, 0.85f);
            this.wizard.level().playSound(null, this.wizard.getX(), this.wizard.getY(), this.wizard.getZ(),
                    net.minecraft.sounds.SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.2f, 0.65f);

            // Mark the beam so we can recognise it as a charged serpent (renderer-side
            // can add an extra trail if it cares — at minimum the spawn-burst here
            // gives it a distinct visual signature on launch).
            if (this.wizard.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                        this.wizard.getX(), this.wizard.getY() + 1.4D, this.wizard.getZ(),
                        12, 0.4D, 0.4D, 0.4D, 0.05D);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        this.wizard.getX(), this.wizard.getY() + 1.4D, this.wizard.getZ(),
                        30, 0.5D, 0.5D, 0.5D, 0.25D);
            }

            SpellBeamEntity beam = new SpellBeamEntity(this.wizard.level(), this.wizard, SpellType.FIENDFYRE);
            double d0 = target.getX() - this.wizard.getX();
            double d1 = target.getY(0.5D) - beam.getY();
            double d2 = target.getZ() - this.wizard.getZ();
            // Faster than the regular beam — the charge converted into speed.
            beam.shoot(d0, d1, d2, 2.1F, 0.5F);
            this.wizard.level().addFreshEntity(beam);
        }
    }
}
