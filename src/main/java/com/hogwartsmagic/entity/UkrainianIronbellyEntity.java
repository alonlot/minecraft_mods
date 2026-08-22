package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Ukrainian Ironbelly the goblins keep in front of the deepest vaults.
 *
 * <p>A subclass of the Hungarian Horntail on purpose, which is the compromise this was built to:
 * it inherits the model, the renderer, the animations and the attack machine, and changes what
 * makes it a <em>different animal</em> - it is bigger, half-starved, chained by the leg, and it
 * has been kept in the dark so long that it barely sees.
 *
 * <p>It is never aerial, so the parent's whole flight state machine sits inert: nothing here ever
 * sets it flying, and the parent already grounds anything that is not in an aerial state.
 *
 * <h2>It hunts by hearing</h2>
 *
 * <p>Follow range is a third of the Horntail's, and inside that it is nearly blind. What it
 * actually reacts to is <b>noise</b> - sprinting, breaking blocks, Clankers - which makes the whole
 * cavern a different problem from every other mob in the mod: you can walk past it if you walk.
 *
 * <h2>The chains are a radius, not entities</h2>
 *
 * <p>Kept within reach of its anchor by pulling it back rather than by any physical tether, and the
 * anchor stubs in the floor are the only breakable blocks in the room. Break the last one and it
 * leaves - a scripted climb to the ceiling shaft rather than real flight AI, because real flight AI
 * in a cavern this shape is how a dragon ends up screaming inside a wall forever.
 */
public class UkrainianIronbellyEntity extends HungarianHorntailEntity {

    private static final EntityDataAccessor<Boolean> DATA_COWERING =
            SynchedEntityData.defineId(UkrainianIronbellyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHAINED =
            SynchedEntityData.defineId(UkrainianIronbellyEntity.class, EntityDataSerializers.BOOLEAN);

    /** How far it can get from its anchor before the chains take up. */
    private static final double CHAIN_RADIUS = 11.0D;
    /** How long a ringing of Clankers keeps it cowed. */
    public static final int COWER_TICKS = 100;
    /** How far a noise carries to it. Deliberately further than it can see. */
    public static final double HEARING = 18.0D;
    /**
     * How close is close enough that being half-blind stops helping you.
     *
     * <p>Sneaking past it is the whole game in that room, so this is the number that decides
     * whether the game is fair. Eight blocks is about the width of the chamber floor either side of
     * the track: hug the wall and you get by, walk down the middle and it has you.
     */
    private static final double SMELL = 8.0D;
    private static final int HEARD_MEMORY = 60;
    /** Ticks of the departure once it is freed. */
    private static final int LEAVING_TICKS = 200;

    @Nullable
    private BlockPos anchor;
    private int cowerTicks;
    private int lastHeardTick = -1000;
    @Nullable
    private Vec3 lastHeardAt;
    private int leavingTicks = -1;

    public UkrainianIronbellyEntity(EntityType<? extends HungarianHorntailEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createIronbellyAttributes() {
        return HungarianHorntailEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                // A third of the Horntail's. It is not looking for you; it is listening.
                .add(Attributes.FOLLOW_RANGE, 12.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COWERING, Boolean.FALSE);
        builder.define(DATA_CHAINED, Boolean.TRUE);
    }

    @Override
    protected void registerGoals() {
        // Deliberately not the parent goal set: no stroll, no flight, and a target goal that will
        // not pick somebody up across the cavern just because they happen to be visible.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new IronbellyCombatGoal(this));
        this.goalSelector.addGoal(2, new IronbellyGuardGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // It cannot see you across the room, but it is a dragon and you are standing next to it.
        // mustSee is false on purpose - this one hunts by hearing, so line of sight is not the
        // question; being close, or having just made a noise, is.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class,
                10, false, false, living -> living instanceof Player player
                        && !player.isCreative() && !player.isSpectator()
                        && !isCowering()
                        && (distanceToSqr(player) < SMELL * SMELL || recentlyHeard())));
    }

    public boolean isCowering() {
        return this.entityData.get(DATA_COWERING);
    }

    public boolean isChained() {
        return this.entityData.get(DATA_CHAINED);
    }

    public void setAnchor(BlockPos pos) {
        this.anchor = pos.immutable();
    }

    @Nullable
    public BlockPos anchor() {
        return this.anchor;
    }

    /** Something made a noise. It does not need to see you to come and find out what. */
    public void heard(Vec3 where) {
        if (isCowering()) {
            return;
        }
        this.lastHeardTick = this.tickCount;
        this.lastHeardAt = where;
    }

    public boolean recentlyHeard() {
        return this.tickCount - this.lastHeardTick < HEARD_MEMORY && this.lastHeardAt != null;
    }

    @Nullable
    public Vec3 heardAt() {
        return this.lastHeardAt;
    }

    /** Clankers. It backs off, and it cannot bring itself to attack while it lasts. */
    public void cower() {
        this.cowerTicks = COWER_TICKS;
        this.entityData.set(DATA_COWERING, Boolean.TRUE);
        this.setTarget(null);
        this.lastHeardAt = null;
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), com.hogwartsmagic.ModSounds.IRONBELLY_COWER.get(),
                    SoundSource.HOSTILE, 2.4F, 1.0F);
            server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.6D, getZ(),
                    24, 1.2D, 0.8D, 1.2D, 0.02D);
        }
    }

    /** Every Ironbelly within range of a ringing. */
    public static void cowerNearby(ServerLevel level, Vec3 where, double radius) {
        for (UkrainianIronbellyEntity dragon : level.getEntitiesOfClass(
                UkrainianIronbellyEntity.class, new AABB(where, where).inflate(radius))) {
            dragon.cower();
        }
    }

    /** The last anchor stub has been broken. It goes. */
    public void free(@Nullable Player by) {
        if (!isChained()) {
            return;
        }
        this.entityData.set(DATA_CHAINED, Boolean.FALSE);
        this.leavingTicks = LEAVING_TICKS;
        this.setTarget(null);
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), com.hogwartsmagic.ModSounds.IRONBELLY_ROAR.get(),
                    SoundSource.HOSTILE, 6.0F, 1.0F);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.IRONBELLY_AMBIENT.get();
    }

    /**
     * Half again the Horntail's interval. It is old and it is not hunting - the long gaps are what
     * make the cavern feel like somewhere something is sleeping rather than somewhere a boss waits.
     */
    @Override
    public int getAmbientSoundInterval() {
        return 180;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (this.cowerTicks > 0 && --this.cowerTicks == 0) {
            this.entityData.set(DATA_COWERING, Boolean.FALSE);
        }

        if (this.leavingTicks >= 0) {
            leave();
            return;
        }

        if (isChained() && this.anchor != null) {
            double distance = position().distanceTo(Vec3.atCenterOf(this.anchor));
            if (distance > CHAIN_RADIUS) {
                // Pulled up short, rather than tethered by anything physical.
                Vec3 back = Vec3.atCenterOf(this.anchor).subtract(position()).normalize().scale(0.28D);
                setDeltaMovement(getDeltaMovement().add(back.x, 0.0D, back.z));
                if (this.tickCount % 20 == 0 && this.level() instanceof ServerLevel server) {
                    server.playSound(null, blockPosition(),
                            com.hogwartsmagic.ModSounds.IRONBELLY_CHAIN.get(), SoundSource.HOSTILE,
                            2.0F, 1.0F);
                }
            }
        }
    }

    /** A scripted departure, not flight AI: up the shaft and gone. */
    private void leave() {
        this.leavingTicks--;
        setNoGravity(true);
        this.noPhysics = true;
        setDeltaMovement(0.0D, 0.6D, 0.0D);
        if (this.level() instanceof ServerLevel server && this.tickCount % 4 == 0) {
            server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(),
                    8, 1.5D, 1.0D, 1.5D, 0.02D);
        }
        if (this.leavingTicks <= 0 || getY() > this.level().getMaxBuildHeight() - 8) {
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            heard(attacker.position());
        }
        return super.hurt(source, amount);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.anchor != null) {
            tag.putLong("Anchor", this.anchor.asLong());
        }
        tag.putBoolean("Chained", isChained());
        tag.putInt("Cower", this.cowerTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Anchor")) {
            this.anchor = BlockPos.of(tag.getLong("Anchor"));
        }
        this.entityData.set(DATA_CHAINED, !tag.contains("Chained") || tag.getBoolean("Chained"));
        this.cowerTicks = tag.getInt("Cower");
        this.entityData.set(DATA_COWERING, this.cowerTicks > 0);
    }

    /**
     * What it does once it has decided you are the problem.
     *
     * <p>This did not exist, and its absence is why the dragon stood there. The parent is a flying
     * boss whose entire combat machine lives in a goal that {@code registerGoals} above replaces,
     * so overriding the goals removed the fight along with the flying - the Ironbelly could be hit,
     * could take a target, and then had nothing whatsoever to do with it.
     *
     * <p>So it gets its own, built out of the parent primitives - bite, tail whip, wing gust,
     * breath - with two differences that matter. <b>It never takes off.</b> Real flight AI in a
     * cavern this shape is how a dragon ends up screaming inside a wall, and a chained dragon that
     * flies is not chained. And <b>it will not leave its anchor</b>: pull back past the chain and
     * it stops following and burns you instead, which turns the leash from a limitation into the
     * reason the fight has a shape.
     */
    private static final class IronbellyCombatGoal
            extends net.minecraft.world.entity.ai.goal.Goal {

        /** Snapping distance. */
        private static final double BITE = 6.0D;
        /** Where it stops closing and starts breathing. */
        private static final double BREATH = 18.0D;

        private final UkrainianIronbellyEntity dragon;
        private int cooldown;
        private boolean roared;

        IronbellyCombatGoal(UkrainianIronbellyEntity dragon) {
            this.dragon = dragon;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.dragon.getTarget();
            return target != null && target.isAlive() && !this.dragon.isCowering();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            this.dragon.getNavigation().stop();
            this.cooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.dragon.getTarget();
            if (target == null || this.dragon.inAerialSequence()) {
                return;
            }
            this.dragon.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.cooldown > 0) {
                this.cooldown--;
            }
            if (!this.dragon.canStartAction()) {
                return;
            }

            // The first thing it does is tell you it is there. Everything after is easier to read
            // once you have been told.
            if (!this.roared) {
                this.roared = true;
                this.dragon.beginRoar();
                this.cooldown = 30;
                return;
            }

            double dist = this.dragon.distanceTo(target);

            // Past the chain it stops trying to follow. Standing there straining is what a chained
            // animal does, and breathing fire is what this one does about it.
            if (this.dragon.isChained() && this.dragon.anchor() != null
                    && Vec3.atCenterOf(this.dragon.anchor())
                            .distanceTo(target.position()) > CHAIN_RADIUS + 2.0D) {
                this.dragon.getNavigation().stop();
                if (this.cooldown <= 0 && dist <= BREATH) {
                    this.dragon.beginBreath();
                    this.cooldown = 40;
                }
                return;
            }

            if (dist <= BITE) {
                this.dragon.getNavigation().stop();
                if (this.cooldown > 0) {
                    return;
                }
                int roll = this.dragon.getRandom().nextInt(100);
                if (roll < 24) {
                    this.dragon.beginTailWhip();
                } else if (roll < 38) {
                    this.dragon.beginWingGust();
                } else if (roll < 86) {
                    this.dragon.beginBite();
                } else {
                    this.dragon.beginBreath();
                }
                this.cooldown = this.dragon.isRaging() ? 10 : 18;
                return;
            }

            if (dist <= BREATH) {
                if (this.cooldown <= 0 && this.dragon.getRandom().nextInt(100) < 45) {
                    this.dragon.beginBreath();
                    this.cooldown = this.dragon.isRaging() ? 24 : 40;
                    return;
                }
                // Half-starved and chained, so it lumbers rather than charges.
                this.dragon.getNavigation().moveTo(target, this.dragon.isRaging() ? 0.95D : 0.75D);
                return;
            }

            this.dragon.getNavigation().moveTo(target, 0.85D);
        }
    }

    /**
     * Keeps it near its anchor, and sends it after noises rather than after sightings.
     */
    private static final class IronbellyGuardGoal
            extends net.minecraft.world.entity.ai.goal.Goal {

        private final UkrainianIronbellyEntity dragon;

        IronbellyGuardGoal(UkrainianIronbellyEntity dragon) {
            this.dragon = dragon;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !this.dragon.isCowering();
        }

        @Override
        public void tick() {
            if (this.dragon.isCowering()) {
                // Backs toward its anchor and will not raise its head.
                if (this.dragon.anchor() != null) {
                    this.dragon.getNavigation().moveTo(this.dragon.anchor().getX(),
                            this.dragon.anchor().getY(), this.dragon.anchor().getZ(), 0.6D);
                }
                return;
            }

            if (this.dragon.getTarget() != null) {
                return;      // IronbellyCombatGoal has it
            }

            if (this.dragon.recentlyHeard()) {
                Vec3 where = this.dragon.heardAt();
                this.dragon.getNavigation().moveTo(where.x, where.y, where.z, 0.85D);
                List<Player> near = this.dragon.level().getEntitiesOfClass(Player.class,
                        new AABB(where, where).inflate(6.0D));
                if (!near.isEmpty()) {
                    this.dragon.setTarget(near.get(0));
                }
                return;
            }

            if (this.dragon.anchor() != null
                    && this.dragon.position().distanceTo(
                            Vec3.atCenterOf(this.dragon.anchor())) > CHAIN_RADIUS * 0.7D) {
                this.dragon.getNavigation().moveTo(this.dragon.anchor().getX(),
                        this.dragon.anchor().getY(), this.dragon.anchor().getZ(), 0.5D);
            }
        }
    }
}
