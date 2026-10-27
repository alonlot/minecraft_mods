package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.spell.summon.SerpensortiaRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * A snake. Conjured by Serpensortia it belongs to its caster: it follows them, bites whatever hurts them
 * or whatever they hurt (Poison by the caster's rank), never turns on them or on their allies, and
 * vanishes when its time runs out. A Master caster can order it to attack a creature or guard a spot
 * (SerpensortiaSpell.command). A snake with no owner (spawn egg, /summon) is wild: it hunts players,
 * except Masters of Serpensortia, whom snakes recognise as Parselmouths.
 */
public class SnakeEntity extends PathfinderMob implements OwnableEntity {
    @Nullable
    private UUID ownerUuid;
    /** Ticks left before a conjured snake vanishes; -1 for a wild snake. */
    private int lifeTicks = -1;
    private int poisonAmplifier;
    /** Consecutive ticks a conjured snake has had no living owner to find (not saved: a restart starts it again). */
    private int ownerGoneTicks;
    private int mode = SerpensortiaRules.MODE_FOLLOW;
    @Nullable
    private BlockPos guardPos;
    @Nullable
    private ResourceKey<Level> guardDimension;

    public SnakeEntity(EntityType<? extends SnakeEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, true));
        this.goalSelector.addGoal(2, new GuardPostGoal(this));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new DefendOwnerGoal(this));
        this.targetSelector.addGoal(2, new AssistOwnerGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new GuardTargetGoal(this));
        // Wild snakes only (the predicate reads ownerUuid lazily, after construction finished).
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                candidate -> this.ownerUuid == null && candidate instanceof Player player && !isParselmouth(player)));
    }

    /** Serpensortia: bind this snake to its caster for {@code lifeTicks}. Call before adding it to the level. */
    public void conjure(LivingEntity owner, int lifeTicks, int poisonAmplifier) {
        this.ownerUuid = owner.getUUID();
        this.lifeTicks = lifeTicks;
        this.poisonAmplifier = Math.max(0, poisonAmplifier);
        this.mode = SerpensortiaRules.MODE_FOLLOW;
        this.guardPos = null;
        this.guardDimension = null;
    }

    public boolean isConjured() {
        return this.ownerUuid != null;
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUuid;
    }

    /** Players anywhere this level can see them; NPC owners (Voldemort) only on the server. */
    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (this.ownerUuid == null) {
            return null;
        }
        Player player = this.level().getPlayerByUUID(this.ownerUuid);
        if (player != null) {
            return player;
        }
        if (this.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(this.ownerUuid) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    public static boolean isParselmouth(Player player) {
        return !player.level().isClientSide()
                && SerpensortiaRules.parseltongue(SpellMasteryData.getRank(player, SpellType.SERPENSORTIA));
    }

    /**
     * Who this snake may bite. A wild snake may bite anyone; a conjured one never its owner, the owner's
     * other snakes or owned mobs, team-mates, ALLIED mobs (player owners) or creative/spectator players.
     */
    public boolean isFairTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || candidate == this || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player player && player.isCreative()) {
            return false;
        }
        if (this.ownerUuid == null) {
            return true;
        }
        if (this.ownerUuid.equals(candidate.getUUID())) {
            return false;
        }
        if (candidate instanceof SnakeEntity other && this.ownerUuid.equals(other.ownerUuid)) {
            return false;
        }
        if (candidate instanceof OwnableEntity ownable && this.ownerUuid.equals(ownable.getOwnerUUID())) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        if (owner != null) {
            if (candidate.isAlliedTo(owner) || owner.isAlliedTo(candidate)) {
                return false;
            }
            if (owner instanceof Player && candidate.getTags().contains(CombatTags.ALLIED)) {
                return false;
            }
        }
        return true;
    }

    /** Every route to a target (goals, retaliation, commands) is checked here. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.ownerUuid != null && !this.isFairTarget(target)) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (this.ownerUuid != null) {
            if (this.ownerUuid.equals(other.getUUID())) {
                return true;
            }
            if (other instanceof SnakeEntity snake && this.ownerUuid.equals(snake.ownerUuid)) {
                return true;
            }
        }
        return super.isAlliedTo(other);
    }

    public int getMode() {
        return this.mode;
    }

    public void commandAttack(LivingEntity target) {
        if (this.isFairTarget(target)) {
            this.setTarget(target);
        }
    }

    /** Follow, or guard {@code anchor} in {@code dimension} (the post is ignored in any other dimension). */
    public void commandMode(int newMode, BlockPos anchor, ResourceKey<Level> dimension) {
        this.mode = newMode;
        if (newMode == SerpensortiaRules.MODE_GUARD) {
            this.guardPos = anchor.immutable();
            this.guardDimension = dimension;
        } else {
            this.guardPos = null;
            this.guardDimension = null;
        }
        this.getNavigation().stop();
    }

    @Nullable
    BlockPos activeGuardPost() {
        if (this.mode != SerpensortiaRules.MODE_GUARD || this.guardPos == null || this.guardDimension == null) {
            return null;
        }
        return this.level().dimension().equals(this.guardDimension) ? this.guardPos : null;
    }

    /** A puff of smoke and gone: the end of a conjured snake's time, or a new cast replacing it. */
    public void vanish() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2D, this.getZ(),
                    12, 0.25D, 0.1D, 0.25D, 0.01D);
        }
        this.discard();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.ownerUuid != null) {
            // Logged out, dead, in another dimension or missing after a restart: it must never turn wild.
            LivingEntity owner = this.getOwner();
            if (owner == null || !owner.isAlive()) {
                if (++this.ownerGoneTicks >= SerpensortiaRules.OWNER_GONE_TICKS) {
                    this.vanish();
                    return;
                }
            } else {
                this.ownerGoneTicks = 0;
            }
        }
        if (this.lifeTicks >= 0) {
            this.lifeTicks--;
            if (this.lifeTicks <= 0) {
                this.vanish();
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            this.playSound(SoundEvents.CAT_HISS, 0.5F, 1.6F);
            living.addEffect(new MobEffectInstance(MobEffects.POISON, SerpensortiaRules.POISON_TICKS, this.poisonAmplifier), this);
        }
        return hit;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    /** Wild snakes despawn like any monster; conjured ones stay until their time runs out. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return this.ownerUuid == null;
    }

    /** Jumps beside the owner onto solid ground, but only into free space. */
    boolean teleportNear(LivingEntity owner) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = this.random.nextInt(5) - 2;
            int dz = this.random.nextInt(5) - 2;
            if (dx == 0 && dz == 0) {
                continue;
            }
            BlockPos feet = BlockPos.containing(owner.getX() + dx, owner.getY(), owner.getZ() + dz);
            BlockPos below = feet.below();
            if (!this.level().getBlockState(below).isFaceSturdy(this.level(), below, Direction.UP)) {
                continue;
            }
            double x = feet.getX() + 0.5D;
            double y = feet.getY();
            double z = feet.getZ() + 0.5D;
            AABB box = this.getBoundingBox().move(x - this.getX(), y - this.getY(), z - this.getZ());
            if (this.level().noCollision(this, box)) {
                this.moveTo(x, y, z, this.getYRot(), this.getXRot());
                this.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + (this.random.nextFloat() - 0.5F) * 0.2F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        tag.putInt("LifeTicks", this.lifeTicks);
        tag.putInt("Poison", this.poisonAmplifier);
        tag.putInt("Mode", this.mode);
        if (this.guardPos != null && this.guardDimension != null) {
            tag.putLong("GuardPos", this.guardPos.asLong());
            tag.putString("GuardDim", this.guardDimension.location().toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.lifeTicks = tag.contains("LifeTicks") ? tag.getInt("LifeTicks") : -1;
        this.poisonAmplifier = tag.getInt("Poison");
        this.mode = tag.getInt("Mode");
        this.guardPos = null;
        this.guardDimension = null;
        if (tag.contains("GuardPos") && tag.contains("GuardDim")) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("GuardDim"));
            if (dimension != null) {
                this.guardPos = BlockPos.of(tag.getLong("GuardPos"));
                this.guardDimension = ResourceKey.create(Registries.DIMENSION, dimension);
            }
        }
    }

    /** Stays near the owner in follow mode; jumps to them past TELEPORT_DISTANCE. */
    static final class FollowOwnerGoal extends Goal {
        private final SnakeEntity snake;
        private int recalc;

        FollowOwnerGoal(SnakeEntity snake) {
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.snake.mode != SerpensortiaRules.MODE_FOLLOW || this.snake.getTarget() != null) {
                return false;
            }
            LivingEntity owner = this.snake.getOwner();
            return owner != null && !owner.isSpectator()
                    && this.snake.distanceToSqr(owner) > SerpensortiaRules.FOLLOW_START * SerpensortiaRules.FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity owner = this.snake.getOwner();
            return this.snake.mode == SerpensortiaRules.MODE_FOLLOW && this.snake.getTarget() == null && owner != null
                    && !this.snake.getNavigation().isDone()
                    && this.snake.distanceToSqr(owner) > SerpensortiaRules.FOLLOW_STOP * SerpensortiaRules.FOLLOW_STOP;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.snake.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity owner = this.snake.getOwner();
            if (owner == null) {
                return;
            }
            this.snake.getLookControl().setLookAt(owner, 10.0F, this.snake.getMaxHeadXRot());
            if (--this.recalc > 0) {
                return;
            }
            this.recalc = this.adjustedTickDelay(10);
            if (this.snake.distanceToSqr(owner) > SerpensortiaRules.TELEPORT_DISTANCE * SerpensortiaRules.TELEPORT_DISTANCE) {
                this.snake.teleportNear(owner);
            } else {
                this.snake.getNavigation().moveTo(owner, 1.2D);
            }
        }
    }

    /** Walks back to the guard post when it strays past GUARD_LEASH with nothing to fight. */
    static final class GuardPostGoal extends Goal {
        private final SnakeEntity snake;

        GuardPostGoal(SnakeEntity snake) {
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            BlockPos post = this.snake.activeGuardPost();
            return post != null && this.snake.getTarget() == null
                    && this.snake.distanceToSqr(Vec3.atBottomCenterOf(post)) > SerpensortiaRules.GUARD_LEASH * SerpensortiaRules.GUARD_LEASH;
        }

        @Override
        public boolean canContinueToUse() {
            return this.snake.activeGuardPost() != null && this.snake.getTarget() == null && !this.snake.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos post = this.snake.activeGuardPost();
            if (post != null) {
                this.snake.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 1.1D);
            }
        }

        @Override
        public void stop() {
            this.snake.getNavigation().stop();
        }
    }

    /** The owner's attacker becomes the target, like OwnerHurtByTargetGoal but for any owner. */
    static final class DefendOwnerGoal extends TargetGoal {
        private final SnakeEntity snake;
        @Nullable
        private LivingEntity attacker;
        private int seenTimestamp;

        DefendOwnerGoal(SnakeEntity snake) {
            super(snake, false);
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.snake.getOwner();
            if (owner == null) {
                return false;
            }
            this.attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != this.seenTimestamp && this.snake.isFairTarget(this.attacker)
                    && this.canAttack(this.attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.attacker);
            LivingEntity owner = this.snake.getOwner();
            if (owner != null) {
                this.seenTimestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** Whatever the owner hits becomes the target, like OwnerHurtTargetGoal but for any owner. */
    static final class AssistOwnerGoal extends TargetGoal {
        private final SnakeEntity snake;
        @Nullable
        private LivingEntity victim;
        private int seenTimestamp;

        AssistOwnerGoal(SnakeEntity snake) {
            super(snake, false);
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.snake.getOwner();
            if (owner == null) {
                return false;
            }
            this.victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != this.seenTimestamp && this.snake.isFairTarget(this.victim)
                    && this.canAttack(this.victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.victim);
            LivingEntity owner = this.snake.getOwner();
            if (owner != null) {
                this.seenTimestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    /** On guard: the nearest hostile mob (or mob hunting the owner) within GUARD_RADIUS of the post. */
    static final class GuardTargetGoal extends TargetGoal {
        private final SnakeEntity snake;
        @Nullable
        private LivingEntity found;

        GuardTargetGoal(SnakeEntity snake) {
            super(snake, false);
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            BlockPos post = this.snake.activeGuardPost();
            if (post == null || this.snake.getTarget() != null || this.snake.tickCount % 10 != 0) {
                return false;
            }
            LivingEntity owner = this.snake.getOwner();
            Vec3 centre = Vec3.atBottomCenterOf(post);
            AABB box = new AABB(post).inflate(SerpensortiaRules.GUARD_RADIUS);
            this.found = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity candidate : this.snake.level().getEntitiesOfClass(LivingEntity.class, box, this.snake::isFairTarget)) {
                boolean hostile = (candidate instanceof Enemy && !(candidate instanceof StudentEntity))
                        || (candidate instanceof Mob mob && owner != null && mob.getTarget() == owner);
                if (!hostile) {
                    continue;
                }
                double distance = candidate.distanceToSqr(centre);
                if (distance < best) {
                    best = distance;
                    this.found = candidate;
                }
            }
            return this.found != null;
        }

        @Override
        public void start() {
            this.mob.setTarget(this.found);
            super.start();
        }
    }
}
