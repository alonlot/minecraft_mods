package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.EnumMap;

/**
 * Hogwarts students - peaceful NPCs that defend villages and attack monsters/death eaters
 * Each student is randomly assigned to a house (Gryffindor, Hufflepuff, Ravenclaw, Slytherin)
 */
public class StudentEntity extends AbstractIllager implements DisarmableWandUser {

    private static final EntityDataAccessor<Boolean> DATA_IS_CASTING = SynchedEntityData.defineId(StudentEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_FLYING = SynchedEntityData.defineId(StudentEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HOUSE = SynchedEntityData.defineId(StudentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_DISARMED = SynchedEntityData.defineId(StudentEntity.class, EntityDataSerializers.BOOLEAN);

    // per-student cooldown tracking
    private final Map<SpellType, Long> spellCooldowns = new EnumMap<>(SpellType.class);
    private long lastHitTime = 0;

    // House ids are kept as primitive ints to avoid nested-class load issues in runtime/dev env.
    public static final int HOUSE_GRYFFINDOR = 0;
    public static final int HOUSE_HUFFLEPUFF = 1;
    public static final int HOUSE_RAVENCLAW = 2;
    public static final int HOUSE_SLYTHERIN = 3;
    private static final int HOUSE_COUNT = 4;

    private static int sanitizeHouseId(int houseId) {
        return (houseId >= HOUSE_GRYFFINDOR && houseId < HOUSE_COUNT) ? houseId : HOUSE_GRYFFINDOR;
    }

    private boolean isSpellOnCooldown(SpellType spell, long now) {
        return spellCooldowns.getOrDefault(spell, 0L) > now;
    }

    private void setSpellCooldown(SpellType spell, long tickEnd) {
        spellCooldowns.put(spell, tickEnd);
    }

    public void recordHit() {
        this.lastHitTime = this.level().getGameTime();
    }

    public boolean wasRecentlyHit(long now) {
        return (now - lastHitTime) < 20;
    }

    public StudentEntity(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        // flight effect is managed globally in ModEvents.onLivingUpdate
        if (!this.level().isClientSide && this.isSleeping() && this.level().isDay()) {
            this.stopSleeping();
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float damage) {
        if (this.isSleeping()) {
            this.stopSleeping();
        }
        // Intercept damage if we have Protego, unless it's void/starve/drown damage
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && this.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            net.minecraft.world.effect.MobEffectInstance currentEffect = this.getEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            if (currentEffect != null) {
                int currentAmplifier = currentEffect.getAmplifier();
                
                // Play absorption / clink sound
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.5f);
                
                // If it was the last charge, break the shield
                if (currentAmplifier <= 0) {
                    this.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                    
                    if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                    }
                } else {
                    this.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), currentEffect.getDuration(), currentAmplifier - 1));
                }
                return false; 
            }
        }
        
        recordHit();
        return super.hurt(source, damage);
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
        // Retrieving a dropped wand outranks everything else — a disarmed student's only objective.
        this.goalSelector.addGoal(0, new RetrieveDroppedWandGoal(this, 1.2D));
        // Flee when hurt, similar to villagers
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.5D));
        // At night, path to a village bed and sleep (like villagers)
        this.goalSelector.addGoal(2, new StudentSleepGoal(this));
        this.goalSelector.addGoal(3, new StudentSpellCastGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Students attack monsters and death eaters, NOT players
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, StudentEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true)); // Attack death eaters
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                target -> !(target instanceof StudentEntity))); // Attack monsters, but never other students
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_IS_CASTING, false);
        builder.define(DATA_IS_FLYING, false);
        builder.define(DATA_HOUSE, 0); // Default to Gryffindor
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
    public net.minecraft.world.entity.Mob asMob() {
        return this;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        // Guarantee a wand even when /summon skips populateDefaultEquipmentSlots.
        if (!this.level().isClientSide && !this.isDisarmed() && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        }
    }

    public void setCasting(boolean casting) {
        this.entityData.set(DATA_IS_CASTING, casting);
    }

    public boolean isCasting() {
        return this.entityData.get(DATA_IS_CASTING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(DATA_IS_FLYING, flying);
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_IS_FLYING);
    }

    public void setHouse(int houseId) {
        this.entityData.set(DATA_HOUSE, sanitizeHouseId(houseId));
    }

    public int getHouseId() {
        return sanitizeHouseId(this.entityData.get(DATA_HOUSE));
    }

    @Override
    protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random, net.minecraft.world.DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(random, difficulty);
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor p_33282_, net.minecraft.world.DifficultyInstance p_33283_, net.minecraft.world.entity.MobSpawnType p_33284_, @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData p_33285_) {
        net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(p_33282_, p_33283_, p_33284_, p_33285_);

        // Vanilla Mob.finalizeSpawn does NOT invoke populateDefaultEquipmentSlots automatically —
        // subclasses like Pillager/Zombie call it explicitly. Without this line our wand-setup
        // override was never running, so students spawned bare-handed.
        this.populateDefaultEquipmentSlots(this.random, p_33283_);

        // Randomly assign house
        this.setHouse(this.random.nextInt(HOUSE_COUNT));

        return data;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag p_33353_) {
        super.addAdditionalSaveData(p_33353_);
        p_33353_.putInt("House", this.getHouseId());
        p_33353_.putBoolean("Disarmed", this.isDisarmed());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag p_33354_) {
        super.readAdditionalSaveData(p_33354_);
        if (p_33354_.contains("House")) {
            this.setHouse(p_33354_.getInt("House"));
        }
        if (p_33354_.contains("Disarmed")) this.setDisarmed(p_33354_.getBoolean("Disarmed"));
    }

    /**
     * Path to the nearest bed at night and sleep when close, like villagers.
     */
    static class StudentSleepGoal extends Goal {
        private static final int BED_SEARCH_RADIUS = 14;
        private static final int BED_SEARCH_VERTICAL = 5;
        private static final double LIE_DOWN_DIST_SQ = 2.75 * 2.75;
        private static final int CACHE_REFRESH_TICKS = 50;

        private final StudentEntity student;
        private BlockPos bedPos;
        private int nextBedSearchTick;

        StudentSleepGoal(StudentEntity student) {
            this.student = student;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private static boolean isHostileNearby(StudentEntity student) {
            return !student.level().getEntitiesOfClass(Monster.class,
                    student.getBoundingBox().inflate(12.0D, 4.0D, 12.0D),
                    m -> m.isAlive() && m != student && !(m instanceof StudentEntity)).isEmpty();
        }

        private BlockPos findNearestBed() {
            BlockPos center = student.blockPosition();
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            int cx = center.getX();
            int cy = center.getY();
            int cz = center.getZ();
            for (int dx = -BED_SEARCH_RADIUS; dx <= BED_SEARCH_RADIUS; dx++) {
                for (int dy = -BED_SEARCH_VERTICAL; dy <= BED_SEARCH_VERTICAL; dy++) {
                    for (int dz = -BED_SEARCH_RADIUS; dz <= BED_SEARCH_RADIUS; dz++) {
                        mut.set(cx + dx, cy + dy, cz + dz);
                        if (student.level().getBlockState(mut).is(BlockTags.BEDS)) {
                            double d = student.distanceToSqr(mut.getX() + 0.5D, mut.getY(), mut.getZ() + 0.5D);
                            if (d < bestDist) {
                                bestDist = d;
                                best = mut.immutable();
                            }
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if (student.level().isClientSide) {
                return false;
            }
            if (!student.level().dimensionType().natural()) {
                return false;
            }
            if (student.isFlying()) {
                return false;
            }
            if (student.level().isDay()) {
                return false;
            }
            if (student.isSleeping()) {
                return false;
            }
            if (student.getTarget() != null) {
                return false;
            }
            if (isHostileNearby(student)) {
                return false;
            }
            if (student.getLastHurtByMob() != null && student.tickCount - student.getLastHurtByMobTimestamp() < 100) {
                return false;
            }
            if (student.tickCount < nextBedSearchTick) {
                return bedPos != null;
            }
            nextBedSearchTick = student.tickCount + CACHE_REFRESH_TICKS;
            bedPos = findNearestBed();
            return bedPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (student.level().isDay()) {
                return false;
            }
            if (student.getTarget() != null) {
                return false;
            }
            if (isHostileNearby(student)) {
                return false;
            }
            if (student.isSleeping()) {
                return true;
            }
            return bedPos != null && student.level().getBlockState(bedPos).is(BlockTags.BEDS);
        }

        @Override
        public void start() {
            nextBedSearchTick = student.tickCount + CACHE_REFRESH_TICKS;
            if (bedPos == null) {
                bedPos = findNearestBed();
            }
        }

        @Override
        public void stop() {
            if (student.isSleeping()) {
                student.stopSleeping();
            }
            bedPos = null;
        }

        @Override
        public void tick() {
            if (student.isSleeping()) {
                return;
            }
            if (bedPos == null || !student.level().getBlockState(bedPos).is(BlockTags.BEDS)) {
                return;
            }
            Vec3 bedCenter = Vec3.atBottomCenterOf(bedPos);
            student.getLookControl().setLookAt(bedCenter.x, bedCenter.y, bedCenter.z, 30.0F, 30.0F);
            if (student.distanceToSqr(bedCenter.x, bedCenter.y, bedCenter.z) <= LIE_DOWN_DIST_SQ) {
                student.getNavigation().stop();
                student.startSleeping(bedPos);
            } else {
                student.getNavigation().moveTo(bedCenter.x, bedCenter.y, bedCenter.z, 0.45D);
            }
        }
    }

    static class StudentSpellCastGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final StudentEntity student;
        private int attackTime;
        private int castPhaseTime;
        private int escapeTime;
        private boolean wasFlying;

        public StudentSpellCastGoal(StudentEntity student) {
            this.student = student;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
            this.escapeTime = 0;
            this.wasFlying = false;
        }

        @Override
        public boolean canUse() {
            if (this.student.isSleeping() || this.student.isDisarmed()) {
                return false;
            }
            LivingEntity livingentity = this.student.getTarget();
            return livingentity != null && livingentity.isAlive() && this.student.canAttack(livingentity);
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
            this.wasFlying = false;
        }

        @Override
        public void stop() {
            this.student.setCasting(false);
            this.castPhaseTime = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.student.getTarget();
            if (target == null) return;

            long now = this.student.level().getGameTime();
            double distSq = this.student.distanceToSqr(target);

            this.student.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (distSq < 4.0D) {
                this.student.getNavigation().stop();
                if (--this.attackTime <= 0) {
                    this.attackTime = 20;
                    if (!this.castRepelSpell(target, now)) {
                        net.minecraft.world.phys.Vec3 fleeDir = this.student.position().subtract(target.position()).normalize();
                        this.student.setDeltaMovement(fleeDir.x * 0.45D, Math.max(0.08D, this.student.getDeltaMovement().y), fleeDir.z * 0.45D);
                    }
                }
                this.student.setCasting(false);
                this.castPhaseTime = 0;
            } else if (distSq < this.getFollowDistance() * this.getFollowDistance()) {
                if (this.castPhaseTime > 0) {
                    this.student.setCasting(true);
                    this.student.getNavigation().stop();

                    if (--this.castPhaseTime <= 0) {
                        this.castSpell(target);
                        this.student.setCasting(false);
                        this.attackTime = 40 + this.student.getRandom().nextInt(40);
                    }
                } else {
                    if (--this.attackTime <= 0) {
                        this.castPhaseTime = 20;
                    }

                    if (distSq > 100.0D) {
                        this.student.getNavigation().moveTo(target, 1.0D);
                    } else {
                        this.student.getNavigation().stop();
                    }
                }
            } else {
                this.student.getNavigation().moveTo(target, 1.0D);
                this.student.setCasting(false);
                this.castPhaseTime = 0;
            }

            super.tick();
        }

        private double getFollowDistance() {
            return this.student.getAttributeValue(Attributes.FOLLOW_RANGE);
        }

        private boolean castRepelSpell(LivingEntity target, long now) {
            SpellType spell = null;
            if (!this.student.isSpellOnCooldown(SpellType.DEPULSO, now)) {
                spell = SpellType.DEPULSO;
            } else if (!this.student.isSpellOnCooldown(SpellType.EXPELLIARMUS, now)) {
                spell = SpellType.EXPELLIARMUS;
            }

            if (spell == null) {
                return false;
            }

            if (spell != SpellType.PROTEGO && this.student.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
                this.student.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
            }

            this.student.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            this.student.level().playSound(null, this.student.getX(), this.student.getY(), this.student.getZ(),
                    spell.getSoundEvent(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);

            SpellBeamEntity beam = new SpellBeamEntity(this.student.level(), this.student, spell);
            if (spell == SpellType.ARRESTO_MOMENTUM) {
                beam.setHomingTarget(target);
            }
            double d0 = target.getX() - this.student.getX();
            double d1 = target.getY(0.5D) - beam.getY();
            double d2 = target.getZ() - this.student.getZ();
            beam.shoot(d0, d1, d2, 1.55F, 0.9F);
            this.student.level().addFreshEntity(beam);
            this.student.setSpellCooldown(spell, now + spell.getCooldownTicks());
            return true;
        }

        private void castSpell(LivingEntity target) {
            long now = this.student.level().getGameTime();
            if (this.student.getHealth() < this.student.getMaxHealth() * 0.3f && !this.student.isSpellOnCooldown(SpellType.HEAL, now)) {
                SpellType.HEAL.applyHitEffect(this.student, this.student);
                this.student.setSpellCooldown(SpellType.HEAL, now + SpellType.HEAL.getCooldownTicks());
                return;
            }
            
            // Reactive Protego casting: only if attacked recently or a spell is incoming
            boolean needsShield = false;
            if (this.student.wasRecentlyHit(now)) {
                needsShield = true;
            } else {
                java.util.List<SpellBeamEntity> incoming = this.student.level().getEntitiesOfClass(
                        SpellBeamEntity.class, this.student.getBoundingBox().inflate(8.0));
                for (SpellBeamEntity beam : incoming) {
                    if (beam.getOwner() != this.student) {
                        needsShield = true;
                        break;
                    }
                }
            }
            if (needsShield && !this.student.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get()) && !this.student.isSpellOnCooldown(SpellType.PROTEGO, now)) {
                this.student.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), 300, 1));
                this.student.setSpellCooldown(SpellType.PROTEGO, now + SpellType.PROTEGO.getCooldownTicks());
                this.student.level().playSound(null, this.student.getX(), this.student.getY(), this.student.getZ(),
                        com.hogwartsmagic.ModSounds.SPELL_PROTEGO.get(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                this.student.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                return;
            }

            java.util.List<SpellType> spells = new java.util.ArrayList<>();
            // Heavy weighting on offensive spells
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.EXPELLIARMUS);
            spells.add(SpellType.EXPELLIARMUS);
            spells.add(SpellType.CONFRINGO);
            spells.add(SpellType.INCENDIO);
            spells.add(SpellType.INCENDIO);
            spells.add(SpellType.GLACIUS);
            spells.add(SpellType.HEAL);


            spells.removeIf(s -> this.student.isSpellOnCooldown(s, now));
            if (spells.isEmpty()) {
                return;
            }

            SpellType spell = spells.get(this.student.getRandom().nextInt(spells.size()));

            if (spell == SpellType.HEAL) {
                spell.applyHitEffect(this.student, this.student);
            } else {
                // Break Protego if casting offensive spell
                if (spell != SpellType.PROTEGO && this.student.hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
                     this.student.removeEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get());
                     this.student.level().playSound(null, this.student.getX(), this.student.getY(), this.student.getZ(),
                             net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);
                     if (this.student.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                          sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, this.student.getX(), this.student.getY() + 1.0, this.student.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                     }
                }
            
                this.student.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                this.student.level().playSound(null, this.student.getX(), this.student.getY(), this.student.getZ(),
                        spell.getSoundEvent(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.0f);

                SpellBeamEntity beam = new SpellBeamEntity(this.student.level(), this.student, spell);
                if (spell == SpellType.ARRESTO_MOMENTUM) {
                    beam.setHomingTarget(target);
                }
                double d0 = target.getX() - this.student.getX();
                double d1 = target.getY(0.5D) - beam.getY();
                double d2 = target.getZ() - this.student.getZ();
                beam.shoot(d0, d1, d2, 1.5F, 1.0F);
                this.student.level().addFreshEntity(beam);
            }

            this.student.setSpellCooldown(spell, now + spell.getCooldownTicks());
        }
    }
}
