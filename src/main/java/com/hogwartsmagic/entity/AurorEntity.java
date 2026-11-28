package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.auror.AurorJustice;
import com.hogwartsmagic.auror.AurorPrison;
import com.hogwartsmagic.auror.AurorRules;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.support.ProtegoShield;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * An Auror: a Ministry dark-wizard catcher. Guards the Auror towers, hunts Death Eaters and players whose dark magic
 * has gone past a slip (AurorRules.HUNTED_TIER) or who were seen casting it, and fights with the Ministry's spells:
 * stunning, disarming, binding and shielding, never the Unforgivables. A hunting squad (AurorJustice) is the same mob
 * Apparated next to a dark player, which leaves again once its fight is over.
 *
 * <p>Not a Monster: villagers, iron golems and students leave it alone, and it never counts toward the hostile cap.
 */
public class AurorEntity extends PathfinderMob implements DisarmableWandUser, CastingPose {
    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(AurorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DISARMED =
            SynchedEntityData.defineId(AurorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(AurorEntity.class, EntityDataSerializers.INT);
    /** In light flight: the body is hidden and TrailFx draws a silver-white streak in its place. */
    private static final EntityDataAccessor<Boolean> DATA_LIGHT_FLIGHT =
            SynchedEntityData.defineId(AurorEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int VARIANTS = 3;

    private final Map<SpellType, Long> spellCooldowns = new EnumMap<>(SpellType.class);
    private long lastHitTime;
    /** A hunting squad member (leaves when its fight is over); tower Aurors stay. */
    private boolean squad;
    /** Squad members: ticks lived and ticks since they last had a target. */
    private int squadAge;
    private int squadIdle;
    private long nextFlight;
    /** Light flight: ticks left, where to, and whether to keep homing on the target while flying. */
    private int flightTicks;
    @Nullable
    private net.minecraft.world.phys.Vec3 flightDest;
    private boolean flightChase;

    public AurorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CASTING, false);
        builder.define(DATA_DISARMED, false);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_LIGHT_FLIGHT, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new RetrieveDroppedWandGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new AurorDuelGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Strike one Auror and the rest of the post answers.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, AurorEntity.class, StudentEntity.class)
                .setAlertOthers(AurorEntity.class));
        // Death Eaters on sight, except the ones still behind bars.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, WizardEntity.class, 10, true, false,
                w -> !AurorPrison.isPrisoner((WizardEntity) w)));
        this.targetSelector.addGoal(2, new HuntDarkPlayerGoal(this));
        // And the ordinary dangers of the road.
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                m -> !(m instanceof Creeper) && !(m instanceof WizardEntity w && AurorPrison.isPrisoner(w))));
    }

    // ---- who is fair game --------------------------------------------------------------------------------------

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof AurorEntity || target instanceof StudentEntity || target instanceof LivingStatueEntity) {
            return false;
        }
        if (target instanceof WizardEntity wizard && AurorPrison.isPrisoner(wizard)) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (this.level().isClientSide) {
            return super.hurt(source, damage);
        }
        if (this.isLightFlying() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // spells pass through the streak
        }
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !com.hogwartsmagic.spell.mind.AvadaSpell.isLanding(this) && ProtegoShield.has(this)) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHIELD_BLOCK,
                    SoundSource.NEUTRAL, 1.0f, 1.5f);
            if (ProtegoShield.absorbHit(this)) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK,
                        SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
            return false;
        }
        this.lastHitTime = this.level().getGameTime();
        boolean hurt = super.hurt(source, damage);
        if (hurt && this.isAlive() && this.getTarget() != null && this.random.nextFloat() < AurorRules.DODGE_CHANCE) {
            this.dodge(this.getTarget());
        }
        if (hurt && source.getEntity() instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()) {
            AurorJustice.markWanted(player); // attacking an Auror is a crime in itself
        }
        return hurt;
    }

    boolean wasRecentlyHit(long now) {
        return now - this.lastHitTime < 20L;
    }

    // ---- life of a squad member --------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.flightTicks > 0) {
            this.tickLightFlight();
        }
        super.tick();
        if (this.level().isClientSide || !this.squad) {
            return;
        }
        this.squadAge++;
        this.squadIdle = this.getTarget() != null && this.getTarget().isAlive() ? 0 : this.squadIdle + 1;
        if (this.squadIdle >= AurorRules.SQUAD_IDLE_LEAVE_TICKS || this.squadAge >= AurorRules.SQUAD_MAX_LIFE_TICKS) {
            AurorJustice.disapparate(this);
        }
    }

    // ---- light flight --------------------------------------------------------------------------------------

    public boolean isLightFlying() {
        return this.entityData.get(DATA_LIGHT_FLIGHT);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LIGHT_FLIGHT.equals(key)) {
            this.setInvisible(this.isLightFlying());
        }
    }

    /**
     * Turns to a streak of silver-white smoke and flies at the destination (homing on the target when chasing),
     * landing within a few blocks of it or when the flight runs out.
     */
    public void startLightFlight(net.minecraft.world.phys.Vec3 dest, boolean chase) {
        if (this.isPassenger() || this.isDisarmed()) {
            return;
        }
        this.flightDest = dest;
        this.flightChase = chase;
        this.flightTicks = AurorRules.FLIGHT_MAX_TICKS;
        this.nextFlight = this.level().getGameTime() + AurorRules.FLIGHT_COOLDOWN_TICKS;
        this.entityData.set(DATA_LIGHT_FLIGHT, true);
        this.setInvisible(true);
        this.setNoGravity(true);
        this.setCasting(false);
        this.getNavigation().stop();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                SoundSource.NEUTRAL, 0.7f, 1.6f);
    }

    private void tickLightFlight() {
        this.flightTicks--;
        this.fallDistance = 0.0f;
        this.getNavigation().stop();
        LivingEntity target = this.getTarget();
        net.minecraft.world.phys.Vec3 dest = this.flightDest;
        if (this.flightChase && target != null && target.isAlive()) {
            // Land a few blocks short of the target, on the near side.
            net.minecraft.world.phys.Vec3 back = this.position().subtract(target.position()).multiply(1, 0, 1);
            back = back.lengthSqr() > 1.0E-4 ? back.normalize().scale(AurorRules.FLIGHT_LAND_SHORT)
                    : net.minecraft.world.phys.Vec3.ZERO;
            dest = target.position().add(back);
        }
        if (dest == null) {
            this.endLightFlight();
            return;
        }
        double dx = dest.x - this.getX();
        double dz = dest.z - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < AurorRules.FLIGHT_ARRIVE || this.flightTicks <= 0) {
            this.endLightFlight();
            return;
        }
        int ground = this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.util.Mth.floor(this.getX()), net.minecraft.util.Mth.floor(this.getZ()));
        // Skim a couple of blocks over the ground, dipping toward the destination as it nears. Indoors the
        // heightmap is the roof, so never aim higher than the destination plus the cruise height.
        double cruise = Math.min(Math.max(ground, dest.y), dest.y + AurorRules.FLIGHT_CRUISE_HEIGHT)
                + (flat > 6.0 ? AurorRules.FLIGHT_CRUISE_HEIGHT : 0.6);
        double dy = net.minecraft.util.Mth.clamp((cruise - this.getY()) * 0.35, -0.5, 0.5);
        if (this.horizontalCollision) {
            dy = 0.6; // over the wall
        }
        double speed = Math.min(AurorRules.FLIGHT_SPEED, flat);
        this.setDeltaMovement(dx / flat * speed, dy, dz / flat * speed);
        float yaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
    }

    private void endLightFlight() {
        this.flightTicks = 0;
        this.flightDest = null;
        this.entityData.set(DATA_LIGHT_FLIGHT, false);
        this.setInvisible(false);
        this.setNoGravity(false);
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2, 0.0, 0.2));
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(net.minecraft.core.particles.ParticleTypes.END_ROD),
                    this.getX(), this.getY() + 1.0, this.getZ(), 12, 0.3, 0.6, 0.3, 0.04);
        }
    }

    /** Off to one side of the target, in a streak, out of the line of fire. */
    private void dodge(LivingEntity target) {
        if (this.level().getGameTime() < this.nextFlight || this.isLightFlying()) {
            return;
        }
        net.minecraft.world.phys.Vec3 toMe = this.position().subtract(target.position()).multiply(1, 0, 1);
        if (toMe.lengthSqr() < 1.0E-4) {
            toMe = new net.minecraft.world.phys.Vec3(1, 0, 0);
        }
        toMe = toMe.normalize();
        double side = this.random.nextBoolean() ? 1.0 : -1.0;
        net.minecraft.world.phys.Vec3 perp = new net.minecraft.world.phys.Vec3(-toMe.z * side, 0, toMe.x * side);
        this.startLightFlight(this.position().add(perp.scale(AurorRules.DODGE_DISTANCE)).add(toMe.scale(2.0)), false);
    }

    public void setSquad(boolean squad) {
        this.squad = squad;
    }

    public boolean isSquad() {
        return this.squad;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToPlayer) {
        return this.squad;
    }

    // ---- equipment and looks -----------------------------------------------------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
            @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        this.setVariant(this.random.nextInt(VARIANTS));
        return result;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        // Structure-baked and /summon'ed Aurors skip finalizeSpawn: arm them here (unless disarmed mid-fight).
        if (!this.level().isClientSide && !this.isDisarmed() && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        }
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(DATA_VARIANT, Math.floorMod(variant, VARIANTS));
    }

    @Override
    public boolean isCasting() {
        return this.entityData.get(DATA_CASTING);
    }

    public void setCasting(boolean casting) {
        this.entityData.set(DATA_CASTING, casting);
    }

    @Override
    public boolean isDisarmed() {
        return this.entityData.get(DATA_DISARMED);
    }

    @Override
    public void setDisarmed(boolean disarmed) {
        this.entityData.set(DATA_DISARMED, disarmed);
        if (disarmed) {
            this.setCasting(false);
            this.getNavigation().stop();
        }
    }

    @Override
    public Mob asMob() {
        return this;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.getVariant());
        tag.putBoolean("Disarmed", this.isDisarmed());
        tag.putBoolean("Squad", this.squad);
        tag.putInt("SquadAge", this.squadAge);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(tag.getInt("Variant"));
        this.setDisarmed(tag.getBoolean("Disarmed"));
        this.squad = tag.getBoolean("Squad");
        this.squadAge = tag.getInt("SquadAge");
        this.setNoGravity(false); // a flight cut short by a reload lands
    }

    // ---- spells -----------------------------------------------------------------------------------------------

    private boolean onCooldown(SpellType spell, long now) {
        return this.spellCooldowns.getOrDefault(spell, 0L) > now;
    }

    private void cooldown(SpellType spell, long now) {
        this.spellCooldowns.put(spell, now + spell.getCooldownTicks());
    }

    private void fire(SpellType spell, LivingEntity target, float speed, float spread) {
        if (ProtegoShield.has(this)) {
            ProtegoShield.remove(this, ProtegoShield.DROPPED);
        }
        this.swing(InteractionHand.MAIN_HAND);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), spell.getSoundEvent(),
                SoundSource.NEUTRAL, 1.0f, 1.0f);
        SpellBeamEntity beam = new SpellBeamEntity(this.level(), this, spell);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.5D) - beam.getY();
        double dz = target.getZ() - this.getZ();
        beam.shoot(dx, dy, dz, speed, spread);
        this.level().addFreshEntity(beam);
    }

    /** Targets a player whose dark tier, or a witnessed crime, makes them hunted; lets go once that lapses. */
    static class HuntDarkPlayerGoal extends NearestAttackableTargetGoal<Player> {
        HuntDarkPlayerGoal(AurorEntity auror) {
            super(auror, Player.class, 10, true, false, p -> p instanceof Player player && AurorJustice.isHunted(player));
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            if (target instanceof Player player && !AurorJustice.isHunted(player)
                    && this.mob.getLastHurtByMob() != player) {
                return false;
            }
            return super.canContinueToUse();
        }
    }

    /**
     * The Auror's fight: hold at casting range, shield when spells come in, stun, disarm and bind, heal when hurt,
     * push back anything that closes in, and Apparate after a target that runs.
     */
    static class AurorDuelGoal extends Goal {
        private final AurorEntity auror;
        private int attackTime;
        private int castPhaseTime;

        AurorDuelGoal(AurorEntity auror) {
            this.auror = auror;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.auror.isDisarmed()) {
                return false;
            }
            LivingEntity target = this.auror.getTarget();
            return target != null && target.isAlive() && this.auror.canAttack(target);
        }

        @Override
        public void start() {
            this.attackTime = 15;
            this.castPhaseTime = 0;
        }

        @Override
        public void stop() {
            this.auror.setCasting(false);
            this.castPhaseTime = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.auror.getTarget();
            if (target == null) {
                return;
            }
            long now = this.auror.level().getGameTime();
            double distSq = this.auror.distanceToSqr(target);
            this.auror.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (this.auror.isLightFlying()) {
                this.auror.setCasting(false);
                this.castPhaseTime = 0;
                return; // the flight steers itself
            }
            if (distSq > AurorRules.FLIGHT_MIN_DISTANCE * AurorRules.FLIGHT_MIN_DISTANCE
                    && now >= this.auror.nextFlight && !this.auror.isPassenger()
                    && this.auror.getRandom().nextInt(20) == 0) {
                this.auror.startLightFlight(target.position(), true);
                return;
            }

            if (distSq < 6.0D) {
                this.auror.getNavigation().stop();
                if (--this.attackTime <= 0) {
                    this.attackTime = 20;
                    SpellType repel = !this.auror.onCooldown(SpellType.DEPULSO, now) ? SpellType.DEPULSO
                            : !this.auror.onCooldown(SpellType.EXPELLIARMUS, now) ? SpellType.EXPELLIARMUS : null;
                    if (repel != null) {
                        this.auror.fire(repel, target, 1.7F, 0.5F);
                        this.auror.cooldown(repel, now);
                    } else {
                        this.auror.doHurtTarget(target);
                    }
                }
                this.auror.setCasting(false);
                this.castPhaseTime = 0;
                return;
            }

            double range = this.auror.getAttributeValue(Attributes.FOLLOW_RANGE);
            if (distSq > range * range || !this.auror.getSensing().hasLineOfSight(target)) {
                this.auror.getNavigation().moveTo(target, 1.1D);
                this.auror.setCasting(false);
                this.castPhaseTime = 0;
                return;
            }

            if (this.castPhaseTime > 0) {
                this.auror.setCasting(true);
                this.auror.getNavigation().stop();
                if (--this.castPhaseTime <= 0) {
                    this.cast(target, now);
                    this.auror.setCasting(false);
                    this.attackTime = 30 + this.auror.getRandom().nextInt(30);
                }
                return;
            }
            if (--this.attackTime <= 0) {
                this.castPhaseTime = 14; // quicker on the draw than a Death Eater (20)
            }
            if (distSq > 144.0D) {
                this.auror.getNavigation().moveTo(target, 1.0D);
            } else {
                this.auror.getNavigation().stop();
            }
        }

        private void cast(LivingEntity target, long now) {
            if (this.auror.getHealth() < this.auror.getMaxHealth() * 0.35f && !this.auror.onCooldown(SpellType.HEAL, now)) {
                SpellType.HEAL.applyHitEffect(this.auror, this.auror);
                this.auror.cooldown(SpellType.HEAL, now);
                return;
            }
            boolean incoming = this.auror.wasRecentlyHit(now) || !this.auror.level().getEntitiesOfClass(
                    SpellBeamEntity.class, this.auror.getBoundingBox().inflate(8.0D),
                    beam -> beam.getOwner() != this.auror).isEmpty();
            if (incoming && !ProtegoShield.has(this.auror) && !this.auror.onCooldown(SpellType.PROTEGO, now)) {
                ProtegoShield.raise(this.auror, 300, 1);
                this.auror.cooldown(SpellType.PROTEGO, now);
                this.auror.level().playSound(null, this.auror.getX(), this.auror.getY(), this.auror.getZ(),
                        ModSounds.SPELL_PROTEGO.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
                this.auror.swing(InteractionHand.MAIN_HAND);
                return;
            }
            List<SpellType> spells = new ArrayList<>();
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.STUPEFY);
            spells.add(SpellType.EXPELLIARMUS);
            spells.add(SpellType.EXPELLIARMUS);
            spells.add(SpellType.INCARCEROUS);
            spells.add(SpellType.PETRIFICUS_TOTALUS);
            spells.add(SpellType.CONFRINGO);
            spells.add(SpellType.DIFFINDO);
            // A disarmed target is bound, not hit again with Expelliarmus.
            if (target instanceof DisarmableWandUser user && user.isDisarmed()) {
                spells.removeIf(s -> s == SpellType.EXPELLIARMUS);
                spells.add(SpellType.INCARCEROUS);
            }
            spells.removeIf(s -> this.auror.onCooldown(s, now));
            if (spells.isEmpty()) {
                return;
            }
            SpellType spell = spells.get(this.auror.getRandom().nextInt(spells.size()));
            this.auror.fire(spell, target, 1.6F, 0.8F);
            this.auror.cooldown(spell, now);
        }
    }
}
