package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Optional;

/**
 * The Yeti: a great white ape of the high snows, three blocks tall, stooped, its arms long enough to
 * reach its knees.
 *
 * <p>It is territorial, not a hunter. It attacks any player who comes within 16 blocks of it and
 * gives up once they are 32 blocks away.
 * <ul>
 *   <li><b>Swipe</b>: a heavy blow with a long arm, with hard knockback.</li>
 *   <li><b>Slam</b>: both fists into the ground when a player is close: damage, a throw into the air and
 *       Slowness to everything within 3.5 blocks, and the ground shakes.</li>
 *   <li><b>Boulder</b>: at range it scoops up the snow and ice under it and throws a boulder in an arc.</li>
 *   <li><b>Roar</b>: when it first sees a player, or wakes, it rears up and roars.</li>
 * </ul>
 *
 * <p><b>Fire</b> is what it fears, as the books say. Fire damage hurts it twice as much, and while it
 * burns it cowers and then runs. A player with fire in either hand (a torch, a lantern, a campfire, flint
 * and steel, a fire charge, a lava bucket) cannot be closed with: it keeps five blocks off and throws
 * boulders instead. <b>Cold</b> does nothing to it: no freezing, no Glacius.
 *
 * <p>A Yeti in a cave <b>sleeps</b> in its nest. It wakes when a player walks within 7 blocks, or
 * comes within 2.5 blocks sneaking, or hurts it. After a fight it goes home, and after a quiet while
 * it lies down again. So a careful player can sneak past it to its hoard.
 */
public class YetiEntity extends Monster {

    public enum Action {
        NONE(0), ROAR(36), SLAM(34), THROW(30), COWER(40);

        public final int ticks;

        Action(int ticks) {
            this.ticks = ticks;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ACTION =
            SynchedEntityData.defineId(YetiEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_ASLEEP =
            SynchedEntityData.defineId(YetiEntity.class, EntityDataSerializers.BOOLEAN);

    public static final double TERRITORY = 16.0D;
    public static final double GIVE_UP = 32.0D;
    public static final double WAKE_WALKING = 7.0D;
    public static final double WAKE_SNEAKING = 2.5D;
    public static final double SLAM_RADIUS = 3.5D;
    public static final int SLAM_IMPACT = 20;
    public static final int THROW_RELEASE = 22;
    public static final int ROAR_AT = 10;
    public static final double FIRE_KEEP_AWAY = 5.0D;
    private static final int CALM_TO_SLEEP = 300;

    private int actionTicks;
    private int slamCooldown;
    private int throwCooldown = 40;
    private int fearTicks;
    private int calmTicks;
    @Nullable
    private BlockPos home;
    @Nullable
    private LivingEntity fearSource;
    private boolean roaredAtTarget;

    /** Client: when the action began, and the sleep blend. */
    private int actionStart;
    private float sleep;
    private float sleepO;

    public YetiEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 30;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 140.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    /** On snow, ice or stone under open sky, at any light: the snows are its home, day or night. */
    public static boolean canSpawn(EntityType<YetiEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        boolean ground = below.is(BlockTags.SNOW) || below.is(BlockTags.ICE) || below.is(Blocks.SNOW_BLOCK)
                || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(Blocks.GRAVEL);
        if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.SPAWN_EGG) {
            return true;
        }
        // Rare in the wild: under open sky, and never within 128 blocks of another Yeti, so a
        // mountain has one, not one round every corner.
        return ground && level.canSeeSky(pos) && checkMobSpawnRules(type, level, spawnType, pos, random)
                && level.getEntitiesOfClass(YetiEntity.class, new AABB(pos).inflate(128.0D, 64.0D, 128.0D)).isEmpty();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, (byte) 0);
        builder.define(DATA_ASLEEP, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BusyGoal());
        this.goalSelector.addGoal(2, new FearGoal());
        this.goalSelector.addGoal(3, new FightGoal());
        this.goalSelector.addGoal(4, new HomeGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new TerritoryGoal());
    }

    // --- state -----------------------------------------------------------------------------------------

    public Action action() {
        Action[] all = Action.values();
        int i = this.entityData.get(DATA_ACTION);
        return i >= 0 && i < all.length ? all[i] : Action.NONE;
    }

    public float actionTime(float partialTick) {
        return this.tickCount - this.actionStart + partialTick;
    }

    public boolean isAsleep() {
        return this.entityData.get(DATA_ASLEEP);
    }

    public float sleepAmount(float partialTick) {
        return Mth.lerp(partialTick, sleepO, sleep);
    }

    public boolean isAfraid() {
        return fearTicks > 0;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    public void setHome(@Nullable BlockPos home) {
        this.home = home == null ? null : home.immutable();
        if (home != null) {
            this.restrictTo(home, 12);
        } else {
            this.clearRestriction();
        }
    }

    public void setAsleep(boolean asleep) {
        this.entityData.set(DATA_ASLEEP, asleep);
        if (asleep) {
            this.getNavigation().stop();
            this.setTarget(null);
            endAction();
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION.equals(key)) {
            this.actionStart = this.tickCount;
        }
    }

    public void startAction(Action action) {
        this.entityData.set(DATA_ACTION, (byte) action.ordinal());
        this.actionTicks = 0;
        this.getNavigation().stop();
    }

    private void endAction() {
        this.entityData.set(DATA_ACTION, (byte) 0);
        this.actionTicks = 0;
    }

    // --- ticking -----------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            sleepO = sleep;
            sleep = Mth.approach(sleep, isAsleep() ? 1.0F : 0.0F, 0.05F);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (slamCooldown > 0) slamCooldown--;
        if (throwCooldown > 0) throwCooldown--;
        if (fearTicks > 0) fearTicks--;

        if (this.isOnFire() && fearTicks < 40) {
            panic(this.getLastHurtByMob());
        }
        if (isAsleep()) {
            tickAsleep(level);
            return;
        }
        LivingEntity target = getTarget();
        if (target != null && !roaredAtTarget && action() == Action.NONE && !isAfraid()
                && hasLineOfSight(target)) {
            roaredAtTarget = true;
            startAction(Action.ROAR);
        }
        if (target == null) {
            roaredAtTarget = false;
            calmTicks++;
            if (home != null && calmTicks > CALM_TO_SLEEP && action() == Action.NONE
                    && blockPosition().distSqr(home) <= 4.0D) {
                setAsleep(true);
            }
        } else {
            calmTicks = 0;
        }
        if (action() != Action.NONE) {
            tickAction(level);
        }
    }

    private void tickAsleep(ServerLevel level) {
        if (this.tickCount % 40 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.2D, getZ(), 2, 0.4D, 0.2D, 0.4D, 0.01D);
        }
        if (this.tickCount % 10 != 0) {
            return;
        }
        for (Player p : level.players()) {
            if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
                continue;
            }
            if (disturbs(p.distanceTo(this), p.isCrouching(), p.isSprinting())) {
                wake(p);
                return;
            }
        }
    }

    /** Whether a player this far off, moving this way, wakes a sleeping Yeti. Running carries furthest. */
    public static boolean disturbs(double distance, boolean crouching, boolean sprinting) {
        return distance < (crouching ? WAKE_SNEAKING : WAKE_WALKING) || (sprinting && distance < WAKE_WALKING * 1.5D);
    }

    /** Wakes, roaring, with the one who woke it as its target. */
    public void wake(@Nullable LivingEntity by) {
        if (!isAsleep()) {
            return;
        }
        setAsleep(false);
        calmTicks = 0;
        if (by != null) {
            setTarget(by);
        }
        roaredAtTarget = true;
        startAction(Action.ROAR);
    }

    private void tickAction(ServerLevel level) {
        Action a = action();
        actionTicks++;
        LivingEntity target = getTarget();
        if (target != null && a != Action.COWER) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        switch (a) {
            case ROAR -> {
                if (actionTicks == ROAR_AT) {
                    playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.75F);
                    Vec3 mouth = position().add(Vec3.directionFromRotation(0.0F, getYHeadRot()).scale(1.2D)).add(0, 2.4D, 0);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, mouth.x, mouth.y, mouth.z, 40, 0.6D, 0.4D, 0.6D, 0.15D);
                    level.sendParticles(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 8, 0.3D, 0.2D, 0.3D, 0.05D);
                    Fx.shake(level, position(), 0.5F, 16.0F, 18);
                    for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(10.0D),
                            EntitySelector.NO_CREATIVE_OR_SPECTATOR)) {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0));
                    }
                }
            }
            case SLAM -> {
                if (actionTicks == 6) {
                    playSound(SoundEvents.POLAR_BEAR_WARNING, 1.6F, 0.6F);
                }
                if (actionTicks == SLAM_IMPACT) {
                    slamImpact(level);
                }
            }
            case THROW -> {
                if (actionTicks == 4) {
                    BlockState ground = level.getBlockState(blockPosition().below());
                    if (!ground.isAir()) {
                        Vec3 f = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.0D));
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                                f.x, f.y + 0.2D, f.z, 20, 0.4D, 0.1D, 0.4D, 0.1D);
                    }
                    playSound(SoundEvents.SNOW_BREAK, 1.2F, 0.6F);
                }
                if (actionTicks == THROW_RELEASE) {
                    throwBoulder(level, target);
                }
            }
            case COWER -> {
                if (actionTicks % 12 == 1) {
                    playSound(SoundEvents.POLAR_BEAR_HURT, 1.4F, 0.55F);
                }
            }
            default -> {
            }
        }
        if (actionTicks >= a.ticks) {
            endAction();
        }
    }

    // --- attacks ---------------------------------------------------------------------------------------------

    /** Both fists into the ground: everything near takes damage, goes up and is slowed. */
    public void slamImpact(ServerLevel level) {
        Vec3 at = position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(1.1D));
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75F;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(SLAM_RADIUS, 1.5D, SLAM_RADIUS), e -> e != this && e.isAlive())) {
            if (e.distanceToSqr(at) > SLAM_RADIUS * SLAM_RADIUS || e instanceof YetiEntity) {
                continue;
            }
            if (e instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
                continue;
            }
            e.hurt(damageSources().mobAttack(this), damage);
            Vec3 push = e.position().subtract(at).normalize().scale(0.6D);
            e.push(push.x, 0.55D, push.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (ground.isAir()) {
            ground = Blocks.SNOW_BLOCK.defaultBlockState();
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1D, at.z,
                60, 1.4D, 0.1D, 1.4D, 0.25D);
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.3D, at.z, 40, 1.6D, 0.3D, 1.6D, 0.1D);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2D, at.z, 12, 1.2D, 0.1D, 1.2D, 0.05D);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7F, 0.55F);
        level.playSound(null, BlockPos.containing(at), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        Fx.shake(level, at, 0.9F, 12.0F, 14);
    }

    /** The boulder leaves its hands in an arc that lands on the target. */
    public void throwBoulder(ServerLevel level, @Nullable LivingEntity target) {
        YetiBoulderEntity boulder = new YetiBoulderEntity(level, this);
        Vec3 hands = position().add(0.0D, 3.1D, 0.0D).add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.6D));
        boulder.setPos(hands.x, hands.y, hands.z);
        if (target != null) {
            double dx = target.getX() - hands.x;
            double dz = target.getZ() - hands.z;
            double dy = target.getY(0.4D) - hands.y;
            double flat = Math.sqrt(dx * dx + dz * dz);
            boulder.shoot(dx, dy + flat * 0.18D, dz, 1.25F, 3.0F);
        } else {
            Vec3 f = Vec3.directionFromRotation(-10.0F, getYRot());
            boulder.shoot(f.x, f.y, f.z, 1.1F, 2.0F);
        }
        level.addFreshEntity(boulder);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
        playSound(SoundEvents.POLAR_BEAR_WARNING, 1.2F, 0.7F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
        }
        return hit;
    }

    // --- fire and cold -----------------------------------------------------------------------------------------

    /** Burning: it cowers, then runs from whatever burned it. */
    public void panic(@Nullable LivingEntity source) {
        fearTicks = 120;
        fearSource = source != null ? source : getTarget();
        if (action() != Action.COWER) {
            startAction(Action.COWER);
        }
    }

    public static boolean holdsFire(@Nullable LivingEntity e) {
        if (e == null) {
            return false;
        }
        for (ItemStack s : new ItemStack[] {e.getMainHandItem(), e.getOffhandItem()}) {
            if (s.is(Items.TORCH) || s.is(Items.SOUL_TORCH) || s.is(Items.LANTERN) || s.is(Items.SOUL_LANTERN)
                    || s.is(Items.CAMPFIRE) || s.is(Items.SOUL_CAMPFIRE) || s.is(Items.FLINT_AND_STEEL)
                    || s.is(Items.FIRE_CHARGE) || s.is(Items.LAVA_BUCKET)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        boolean wasAsleep = isAsleep();
        boolean hurt = super.hurt(source, fire ? amount * 2.0F : amount);
        if (hurt && !this.level().isClientSide) {
            LivingEntity by = source.getEntity() instanceof LivingEntity l ? l : null;
            if (wasAsleep) {
                wake(by);
            }
            if (fire) {
                panic(by);
            }
        }
        return hurt;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        var e = effect.getEffect().value();
        if (e == ExampleMod.GLACIUS_CHILL.get() || e == ExampleMod.GLACIUS_FROZEN.get()) {
            return false;       // cold is its home
        }
        return super.canBeAffected(effect);
    }

    // --- spawning and saving ----------------------------------------------------------------------------------

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
        if (type == MobSpawnType.STRUCTURE) {
            setHome(blockPosition());
            setAsleep(true);
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Asleep", isAsleep());
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Optional<BlockPos> h = NbtUtils.readBlockPos(tag, "Home");
        h.ifPresent(this::setHome);
        // A Yeti baked into a cave template says "Den": its home is wherever it was placed.
        if (tag.getBoolean("Den") && home == null) {
            setHome(blockPosition());
        }
        this.entityData.set(DATA_ASLEEP, tag.getBoolean("Asleep"));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return home == null && super.removeWhenFarAway(distance);
    }

    // --- sounds ------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return isAsleep() ? null : SoundEvents.POLAR_BEAR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.POLAR_BEAR_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.6F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    // --- goals ---------------------------------------------------------------------------------------------------

    /** Asleep, or roaring, slamming, throwing, cowering: nothing else moves it. */
    private final class BusyGoal extends Goal {
        BusyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isAsleep() || action() != Action.NONE;
        }

        @Override
        public void tick() {
            getNavigation().stop();
        }
    }

    /** It ran from fire: away from whoever burned it until the fear wears off. */
    private final class FearGoal extends Goal {
        private int repath;

        FearGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return fearTicks > 0 && action() == Action.NONE;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (--repath > 0 && !getNavigation().isDone()) {
                return;
            }
            repath = 15;
            Vec3 from = fearSource != null ? fearSource.position() : position();
            Vec3 to = DefaultRandomPos.getPosAway(YetiEntity.this, 12, 5, from);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, 1.35D);
            }
        }
    }

    /** Closing, swiping, slamming and throwing; and standing off from a held flame. */
    private final class FightGoal extends Goal {
        private int swing;
        private int repath;

        FightGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && fearTicks == 0 && action() == Action.NONE && !isAsleep();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) {
                return;
            }
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = distanceTo(t);
            boolean fire = holdsFire(t);
            boolean sees = hasLineOfSight(t);
            if (swing > 0) swing--;

            // A held flame: keep off it and throw.
            if (fire && d < FIRE_KEEP_AWAY + 2.0D) {
                if (d < FIRE_KEEP_AWAY && --repath <= 0) {
                    repath = 10;
                    Vec3 to = DefaultRandomPos.getPosAway(YetiEntity.this, 8, 4, t.position());
                    if (to != null) {
                        getNavigation().moveTo(to.x, to.y, to.z, 1.1D);
                    }
                } else if (d >= FIRE_KEEP_AWAY) {
                    getNavigation().stop();
                }
                if (throwCooldown == 0 && sees) {
                    throwCooldown = 60;
                    startAction(Action.THROW);
                }
                return;
            }
            if (d > 7.0D && d < 24.0D && throwCooldown == 0 && sees && getRandom().nextInt(3) == 0) {
                throwCooldown = 80 + getRandom().nextInt(40);
                startAction(Action.THROW);
                return;
            }
            if (d < SLAM_RADIUS && slamCooldown == 0 && t.onGround()) {
                slamCooldown = 140;
                startAction(Action.SLAM);
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(t, 1.15D);
            }
            double reach = getBbWidth() * 0.5D + t.getBbWidth() * 0.5D + 1.6D;
            if (d <= reach && swing == 0) {
                swing = 24;
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                doHurtTarget(t);
            }
        }
    }

    /** No fight: back to the den, if it has one. */
    private final class HomeGoal extends Goal {
        HomeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return home != null && getTarget() == null && fearTicks == 0 && !isAsleep()
                    && blockPosition().distSqr(home) > 4.0D;
        }

        @Override
        public void start() {
            getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.9D);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && !getNavigation().isDone();
        }
    }

    /** Any player in its territory: within 16 blocks of it, and of its den if it has one. */
    private final class TerritoryGoal extends Goal {
        @Override
        public boolean canUse() {
            if (isAsleep() || getTarget() != null || getRandom().nextInt(5) != 0) {
                return false;
            }
            Player best = null;
            double bestD = TERRITORY * TERRITORY;
            for (Player p : level().players()) {
                if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) || !p.isAlive()) {
                    continue;
                }
                if (home != null && p.blockPosition().distSqr(home) > 24.0D * 24.0D) {
                    continue;
                }
                double d = p.distanceToSqr(YetiEntity.this);
                if (d < bestD && hasLineOfSight(p)) {
                    best = p;
                    bestD = d;
                }
            }
            if (best != null) {
                setTarget(best);
            }
            return false;
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && target.distanceTo(this) > GIVE_UP) {
            target = null;
        }
        super.setTarget(target);
    }

    @Override
    public void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity t = getTarget();
        if (t != null && (t.distanceTo(this) > GIVE_UP || !t.isAlive()
                || (t instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)))) {
            setTarget(null);
        }
    }
}
