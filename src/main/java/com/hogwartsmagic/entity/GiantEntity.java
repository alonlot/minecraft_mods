package com.hogwartsmagic.entity;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Grawp: sixteen feet of dim-witted, short-tempered giant.
 *
 * <p>Left alone he lumbers about the forest muttering to himself. Hit him, or
 * crowd him, and the anger latch flips: he roars, then closes in to swipe, to
 * kick the ground out from under you, and — his signature — to snatch a victim
 * out of the world and hurl them over the treeline. He is no friendlier to the
 * local wildlife, and swats at any other mob he can reach.
 *
 * <p>Attack timing lives in a server-authoritative state byte plus a per-state
 * tick, both synched, so the client can drive the matching keyframe clip without
 * needing entity events.
 */
public class GiantEntity extends Monster {

    public enum AttackState {
        NONE, ROAR, SWIPE, KICK, GRAB, THROW, STAGGER;

        private static final AttackState[] VALUES = values();

        public static AttackState fromByte(byte b) {
            if (b < 0 || b >= VALUES.length) return NONE;
            return VALUES[b];
        }
    }

    private static final EntityDataAccessor<Byte> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICK =
            SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ANGRY =
            SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
            SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HELD_VICTIM =
            SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.INT);

    // State durations, in ticks. Strike frames are the moment damage lands.
    public static final int ROAR_DURATION = 40;
    public static final int SWIPE_DURATION = 26;
    public static final int SWIPE_STRIKE_TICK = 12;
    public static final int KICK_DURATION = 30;
    public static final int KICK_STRIKE_TICK = 14;
    public static final int GRAB_DURATION = 14;
    public static final int THROW_DURATION = 18;
    public static final int THROW_RELEASE_TICK = 8;
    public static final int STAGGER_DURATION = 16;

    private static final double SWIPE_RANGE = 6.0D;
    private static final double KICK_RANGE = 5.5D;
    private static final double GRAB_RANGE = 5.0D;
    private static final float RAGE_HP_FRACTION = 0.35F;

    /** How long provocation keeps the giant hostile to players. */
    private static final int ANGER_DURATION = 600;
    /** Standing this close for this many ticks counts as crowding him. */
    private static final double CROWDING_RADIUS = 4.0D;
    private static final int CROWDING_PATIENCE = 60;

    /** Blocks at or above this destroy speed shrug off a kick. */
    private static final float SMASH_HARDNESS_LIMIT = 3.5F;
    /** How many smashed blocks are thrown clear as debris rather than dropped. */
    private static final int MAX_DEBRIS = 4;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState roarAnimationState = new AnimationState();
    public final AnimationState swipeAnimationState = new AnimationState();
    public final AnimationState kickAnimationState = new AnimationState();
    public final AnimationState grabAnimationState = new AnimationState();
    public final AnimationState throwAnimationState = new AnimationState();
    public final AnimationState staggerAnimationState = new AnimationState();

    private int angryTicks;
    private int crowdingTicks;
    private boolean hasRoared;
    private boolean swipeStruck;
    private boolean kickStruck;
    private boolean victimReleased;

    public GiantEntity(EntityType<? extends GiantEntity> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, MobSpawnType spawnType,
            net.minecraft.world.entity.SpawnGroupData groupData) {
        // A giant the player deliberately placed is a set piece and should stay
        // put; one the world spawned is allowed to wander off and despawn, or
        // they would accumulate in every forest the player has ever visited.
        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND) {
            this.setPersistenceRequired();
        }
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 250.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95D)
                // Two blocks of step height so a five-and-a-half block giant is
                // not permanently wedged behind the first fence it meets.
                .add(Attributes.STEP_HEIGHT, 2.0D);
    }

    public static boolean canSpawn(EntityType<GiantEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random) {
        // Giants keep no hours; they need headroom and solid ground, not darkness.
        return level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                && level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK_STATE, (byte) AttackState.NONE.ordinal());
        builder.define(DATA_STATE_TICK, 0);
        builder.define(DATA_ANGRY, false);
        builder.define(DATA_RAGING, false);
        builder.define(DATA_HELD_VICTIM, -1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new GiantAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 20.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Players are only fair game once he has been provoked.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> this.isAngry()));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
        // Everything else that moves. Giants bully whatever is in reach, but they
        // do not turn on their own kind.
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                target -> target != null && !(target instanceof GiantEntity)));
    }

    // --- state accessors ---------------------------------------------------

    public AttackState getAttackState() {
        return AttackState.fromByte(this.entityData.get(DATA_ATTACK_STATE));
    }

    public int getStateTick() {
        return this.entityData.get(DATA_STATE_TICK);
    }

    public boolean isAngry() {
        return this.entityData.get(DATA_ANGRY);
    }

    public boolean isRaging() {
        return this.entityData.get(DATA_RAGING);
    }

    /** True while a committed clip is playing, so head tracking can stand down. */
    public boolean isPerformingAction() {
        return getAttackState() != AttackState.NONE;
    }

    private void setAttackState(AttackState state) {
        this.entityData.set(DATA_ATTACK_STATE, (byte) state.ordinal());
        this.entityData.set(DATA_STATE_TICK, 0);
        this.swipeStruck = false;
        this.kickStruck = false;
        this.victimReleased = false;
    }

    private boolean isBusy() {
        AttackState s = getAttackState();
        return s != AttackState.NONE && s != AttackState.STAGGER;
    }

    public LivingEntity getHeldVictim() {
        int id = this.entityData.get(DATA_HELD_VICTIM);
        if (id < 0) return null;
        Entity e = this.level().getEntity(id);
        return e instanceof LivingEntity living ? living : null;
    }

    private void setHeldVictim(LivingEntity victim) {
        this.entityData.set(DATA_HELD_VICTIM, victim == null ? -1 : victim.getId());
    }

    public void provoke(LivingEntity by) {
        this.angryTicks = ANGER_DURATION;
        if (!this.entityData.get(DATA_ANGRY)) {
            this.entityData.set(DATA_ANGRY, true);
            this.playSound(ModSounds.GIANT_ANGRY.get(), 2.0F, 0.9F + this.random.nextFloat() * 0.15F);
        }
        if (by != null && this.getTarget() == null) {
            this.setTarget(by);
        }
    }

    // --- ticking -----------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            return;
        }

        AttackState state = getAttackState();
        int t = getStateTick();
        this.entityData.set(DATA_STATE_TICK, t + 1);

        if (this.angryTicks > 0) {
            this.angryTicks--;
            if (this.angryTicks == 0) {
                this.entityData.set(DATA_ANGRY, false);
            }
        }

        // Rage threshold, a one-way latch.
        if (!isRaging() && this.getHealth() <= this.getMaxHealth() * RAGE_HP_FRACTION) {
            this.entityData.set(DATA_RAGING, true);
            this.playSound(ModSounds.GIANT_ROAR.get(), 2.2F, 0.75F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        this.getX(), this.getY() + this.getBbHeight() * 0.85D, this.getZ(),
                        20, 1.0D, 0.6D, 1.0D, 0.02D);
            }
        }

        tickCrowding();
        advanceState(state, t);

        // Committed actions plant him in place.
        if (state != AttackState.NONE) {
            this.getNavigation().stop();
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.15D, v.y, v.z * 0.15D);
        }

        // Heavy footfalls while he is actually walking.
        if (state == AttackState.NONE && this.tickCount % 12 == 0
                && this.getDeltaMovement().horizontalDistanceSqr() > 0.004D) {
            this.playSound(ModSounds.GIANT_STEP.get(), 1.4F, 0.7F + this.random.nextFloat() * 0.15F);
        }
    }

    /** Loitering inside his personal space eventually earns a swat. */
    private void tickCrowding() {
        if (isAngry()) {
            this.crowdingTicks = 0;
            return;
        }
        Player nearby = this.level().getNearestPlayer(this, CROWDING_RADIUS);
        if (nearby != null && !nearby.isCreative() && !nearby.isSpectator()) {
            this.crowdingTicks++;
            if (this.crowdingTicks >= CROWDING_PATIENCE) {
                this.crowdingTicks = 0;
                provoke(nearby);
            }
        } else if (this.crowdingTicks > 0) {
            this.crowdingTicks--;
        }
    }

    private void advanceState(AttackState state, int t) {
        if (state == AttackState.ROAR) {
            if (t == 6) {
                this.playSound(ModSounds.GIANT_ROAR.get(), 2.4F, 0.85F);
                doRoarShock();
            }
            if (t >= ROAR_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.SWIPE) {
            if (t == 4) {
                this.playSound(ModSounds.GIANT_SWIPE.get(), 1.6F, 0.9F);
            }
            if (t == SWIPE_STRIKE_TICK && !this.swipeStruck) {
                this.swipeStruck = true;
                doSwipeImpact();
            }
            if (t >= SWIPE_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.KICK) {
            if (t == KICK_STRIKE_TICK && !this.kickStruck) {
                this.kickStruck = true;
                doKickImpact();
            }
            if (t >= KICK_DURATION) setAttackState(AttackState.NONE);
        } else if (state == AttackState.GRAB) {
            if (t == 2) {
                this.playSound(ModSounds.GIANT_GRAB.get(), 1.6F, 1.0F);
            }
            if (t >= GRAB_DURATION) {
                LivingEntity caught = findGrabVictim();
                if (caught != null) {
                    setHeldVictim(caught);
                    setAttackState(AttackState.THROW);
                } else {
                    setAttackState(AttackState.NONE);
                }
            }
        } else if (state == AttackState.THROW) {
            tickThrow(t);
            if (t >= THROW_DURATION) {
                setHeldVictim(null);
                setAttackState(AttackState.NONE);
            }
        } else if (state == AttackState.STAGGER) {
            if (t >= STAGGER_DURATION) setAttackState(AttackState.NONE);
        }
    }

    // --- attacks -----------------------------------------------------------

    private void doRoarShock() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(8.0D), e -> e != this && e.isAlive() && !(e instanceof GiantEntity))) {
            Vec3 away = victim.position().subtract(this.position());
            if (away.lengthSqr() < 1.0E-4D) continue;
            away = away.normalize();
            victim.push(away.x * 0.6D, 0.25D, away.z * 0.6D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
            if (victim instanceof Player p) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.CONFUSION, 60, 0, false, false, false));
            }
        }

        sl.sendParticles(ParticleTypes.CLOUD,
                this.getX(), this.getY() + this.getBbHeight() * 0.8D, this.getZ(),
                40, 1.2D, 0.6D, 1.2D, 0.06D);
    }

    private void doSwipeImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.25F : 1.0F);
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(SWIPE_RANGE, 2.5D, SWIPE_RANGE),
                e -> e != this && e.isAlive() && !(e instanceof GiantEntity))) {
            Vec3 to = victim.position().subtract(this.position());
            if (to.lengthSqr() > SWIPE_RANGE * SWIPE_RANGE) continue;
            Vec3 flat = new Vec3(to.x, 0.0D, to.z);
            if (flat.lengthSqr() < 1.0E-4D) continue;
            if (flat.normalize().dot(new Vec3(forward.x, 0.0D, forward.z).normalize()) < 0.3D) continue;

            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 push = flat.normalize();
            victim.push(push.x * 1.8D, 0.45D, push.z * 1.8D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }

        sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                this.getX() + forward.x * 3.0D, this.getY() + 3.0D, this.getZ() + forward.z * 3.0D,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void doKickImpact() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double cx = this.getX() + forward.x * 3.5D;
        double cz = this.getZ() + forward.z * 3.5D;

        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (isRaging() ? 1.4F : 1.15F);
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class,
                new AABB(cx - KICK_RANGE, this.getY() - 1.0D, cz - KICK_RANGE,
                        cx + KICK_RANGE, this.getY() + 3.0D, cz + KICK_RANGE),
                e -> e != this && e.isAlive() && !(e instanceof GiantEntity))) {
            victim.hurt(this.damageSources().mobAttack(this), dmg);
            Vec3 away = victim.position().subtract(cx, this.getY(), cz);
            if (away.lengthSqr() < 1.0E-4D) away = forward;
            away = away.normalize();
            victim.push(away.x * 1.5D, 0.7D, away.z * 1.5D);
            victim.hurtMarked = true;
            interruptIfCaster(victim);
        }

        smashBlocks(sl, BlockPos.containing(cx, this.getY(), cz));

        this.playSound(ModSounds.GIANT_KICK_SMASH.get(), 2.0F, 0.85F);
        sl.sendParticles(ParticleTypes.POOF, cx, this.getY() + 0.2D, cz,
                50, 2.0D, 0.3D, 2.0D, 0.06D);

        // A kick that heavy is felt through the ground.
        for (Player p : sl.players()) {
            if (p.distanceToSqr(cx, this.getY(), cz) < 196.0D) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.CONFUSION, 20, 0, false, false, false));
            }
        }
    }

    /**
     * Kicks a 5x3x5 volume of soft terrain apart. Anything a pickaxe would be the
     * right tool for — stone, cobble, deepslate, ore, iron blocks, obsidian —
     * survives by construction, so the rule needs no hand-maintained blocklist.
     */
    private void smashBlocks(ServerLevel level, BlockPos centre) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;

        List<BlockPos> smashable = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    if (isSmashable(level.getBlockState(pos), pos)) {
                        smashable.add(pos);
                    }
                }
            }
        }
        if (smashable.isEmpty()) return;

        // A handful of the wreckage is kicked clear; the rest simply breaks.
        Collections.shuffle(smashable, new java.util.Random(this.random.nextLong()));
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        int debris = 0;
        for (BlockPos pos : smashable) {
            BlockState state = level.getBlockState(pos);
            if (debris < MAX_DEBRIS && !state.isAir()) {
                debris++;
                FallingBlockEntity chunk = FallingBlockEntity.fall(level, pos, state);
                chunk.setDeltaMovement(
                        forward.x * (0.35D + this.random.nextDouble() * 0.3D),
                        0.45D + this.random.nextDouble() * 0.35D,
                        forward.z * (0.35D + this.random.nextDouble() * 0.3D));
                chunk.setHurtsEntities(2.0F, 20);
            } else {
                level.destroyBlock(pos, true, this);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    12, 0.3D, 0.3D, 0.3D, 0.08D);
        }
    }

    private boolean isSmashable(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity()) return false;
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return false;
        float hardness = state.getDestroySpeed(this.level(), pos);
        // Negative hardness means unbreakable (bedrock, barriers, portal frames).
        return hardness >= 0.0F && hardness < SMASH_HARDNESS_LIMIT;
    }

    // --- grab and throw ----------------------------------------------------

    private LivingEntity findGrabVictim() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double cx = this.getX() + forward.x * 3.0D;
        double cz = this.getZ() + forward.z * 3.0D;

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(GRAB_RANGE, 3.0D, GRAB_RANGE),
                e -> e != this && e.isAlive() && canBeGrabbed(e))) {
            double dist = candidate.distanceToSqr(cx, this.getY(), cz);
            if (dist < bestDist && dist <= GRAB_RANGE * GRAB_RANGE) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private boolean canBeGrabbed(LivingEntity candidate) {
        if (candidate instanceof GiantEntity) return false;
        if (candidate instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        // Nothing bigger than he is, and nothing already riding something.
        return candidate.getBbHeight() < this.getBbHeight() && !candidate.isPassenger();
    }

    private void tickThrow(int t) {
        LivingEntity victim = getHeldVictim();
        if (victim == null || !victim.isAlive()) {
            setHeldVictim(null);
            return;
        }

        if (t < THROW_RELEASE_TICK) {
            // The fist sweeps up and over as the arm comes round.
            float progress = (float) t / THROW_RELEASE_TICK;
            holdVictimAt(victim, fistPosition(progress));
        } else if (!this.victimReleased) {
            this.victimReleased = true;
            releaseVictim(victim);
        }
    }

    /** World position of the throwing fist, tracking the arm through the clip. */
    private Vec3 fistPosition(float progress) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        double reach = Mth.lerp(progress, 4.2D, 0.6D);
        double lift = Mth.lerp(progress, 3.4D, 6.0D);
        return new Vec3(this.getX() + forward.x * reach, this.getY() + lift, this.getZ() + forward.z * reach);
    }

    private void holdVictimAt(LivingEntity victim, Vec3 at) {
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        victim.hurtMarked = true;
        if (victim instanceof ServerPlayer sp) {
            // A player's own client owns its position, so it has to be told.
            sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
        } else {
            victim.setPos(at.x, at.y, at.z);
            victim.setOldPosAndRot();
        }
    }

    private void releaseVictim(LivingEntity victim) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F;
        victim.hurt(this.damageSources().mobAttack(this), dmg);

        // Flat and fast rather than high: the arc should be spectacular without
        // the landing being an execution from full health.
        victim.setDeltaMovement(forward.x * 1.9D, 0.85D, forward.z * 1.9D);
        victim.hurtMarked = true;
        victim.fallDistance = 0.0F;
        if (victim instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(victim));
        }
        interruptIfCaster(victim);

        this.playSound(ModSounds.GIANT_THROW.get(), 2.0F, 0.9F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, victim.getX(), victim.getY(), victim.getZ(),
                    16, 0.4D, 0.4D, 0.4D, 0.1D);
        }
    }

    /** Drops whatever he is holding without throwing it. */
    private void dropVictim() {
        LivingEntity victim = getHeldVictim();
        if (victim != null) {
            victim.fallDistance = 0.0F;
        }
        setHeldVictim(null);
    }

    private void interruptIfCaster(LivingEntity victim) {
        if (victim instanceof ServerPlayer sp) {
            ModEvents.interruptActiveCasting(sp);
        }
    }

    // --- goal entry points -------------------------------------------------

    void beginRoar() {
        this.hasRoared = true;
        setAttackState(AttackState.ROAR);
    }

    void beginSwipe() {
        setAttackState(AttackState.SWIPE);
    }

    void beginKick() {
        setAttackState(AttackState.KICK);
    }

    void beginGrab() {
        setAttackState(AttackState.GRAB);
    }

    boolean hasRoared() {
        return this.hasRoared;
    }

    // --- damage, sound, persistence ---------------------------------------

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            this.playSound(ModSounds.GIANT_SWIPE.get(), 1.4F, 0.85F + this.random.nextFloat() * 0.1F);
            if (target instanceof LivingEntity living) {
                Vec3 away = living.position().subtract(this.position());
                if (away.lengthSqr() < 1.0E-4D) {
                    away = Vec3.directionFromRotation(0.0F, this.getYRot());
                }
                away = away.normalize();
                living.push(away.x * 1.6D, 0.4D, away.z * 1.6D);
                living.hurtMarked = true;
                interruptIfCaster(living);
            }
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasBusy = isBusy();
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide) {
            if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof GiantEntity)) {
                provoke(attacker);
            }
            // A solid blow rocks him back, and anything in his fist falls out.
            if (!wasBusy && amount >= 14.0F && this.isAlive()) {
                dropVictim();
                setAttackState(AttackState.STAGGER);
            }
        }
        return result;
    }

    @Override
    public void die(DamageSource source) {
        dropVictim();
        super.die(source);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!this.level().isClientSide || !DATA_ATTACK_STATE.equals(key)) return;

        // Restart the clip that matches the new state and stop the rest, so the
        // client never needs an entity event to stay in sync.
        AttackState state = getAttackState();
        this.roarAnimationState.stop();
        this.swipeAnimationState.stop();
        this.kickAnimationState.stop();
        this.grabAnimationState.stop();
        this.throwAnimationState.stop();
        this.staggerAnimationState.stop();

        if (state == AttackState.ROAR) {
            this.roarAnimationState.start(this.tickCount);
        } else if (state == AttackState.SWIPE) {
            this.swipeAnimationState.start(this.tickCount);
        } else if (state == AttackState.KICK) {
            this.kickAnimationState.start(this.tickCount);
        } else if (state == AttackState.GRAB) {
            this.grabAnimationState.start(this.tickCount);
        } else if (state == AttackState.THROW) {
            this.throwAnimationState.start(this.tickCount);
        } else if (state == AttackState.STAGGER) {
            this.staggerAnimationState.start(this.tickCount);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AngryTicks", this.angryTicks);
        tag.putBoolean("Raging", isRaging());
        tag.putBoolean("HasRoared", this.hasRoared);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.angryTicks = tag.getInt("AngryTicks");
        this.entityData.set(DATA_ANGRY, this.angryTicks > 0);
        if (tag.contains("Raging")) {
            this.entityData.set(DATA_RAGING, tag.getBoolean("Raging"));
        }
        this.hasRoared = tag.getBoolean("HasRoared");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.random.nextBoolean() ? ModSounds.GIANT_AMBIENT1.get() : ModSounds.GIANT_AMBIENT2.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GIANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GIANT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.5F);
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    /**
     * Picks between roar, swipe, kick and grab, and respects the giant's busy
     * state so a committed clip is never cut short by a new decision.
     */
    static class GiantAttackGoal extends Goal {
        private final GiantEntity giant;
        private int decisionCooldown;

        GiantAttackGoal(GiantEntity giant) {
            this.giant = giant;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = giant.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            decisionCooldown = 0;
        }

        @Override
        public void stop() {
            giant.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = giant.getTarget();
            if (target == null) return;

            giant.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (giant.getAttackState() != AttackState.NONE) {
                return;
            }

            double distSq = giant.distanceToSqr(target);

            // He announces himself before the first engagement.
            if (!giant.hasRoared() && distSq < 256.0D) {
                giant.beginRoar();
                return;
            }

            double reach = giant.getBbWidth() * 1.6D + target.getBbWidth() + 2.0D;
            if (distSq > reach * reach) {
                giant.getNavigation().moveTo(target, giant.isRaging() ? 1.25D : 1.0D);
                return;
            }

            giant.getNavigation().stop();
            if (decisionCooldown > 0) {
                decisionCooldown--;
                return;
            }

            int roll = giant.random.nextInt(100);
            boolean grabbable = giant.canBeGrabbed(target);

            if (giant.isRaging()) {
                if (grabbable && roll < 45) {
                    giant.beginGrab();
                } else if (roll < 75) {
                    giant.beginKick();
                } else {
                    giant.beginSwipe();
                }
            } else {
                if (grabbable && roll < 28) {
                    giant.beginGrab();
                } else if (roll < 58) {
                    giant.beginSwipe();
                } else if (roll < 85) {
                    giant.beginKick();
                } else {
                    giant.doHurtTarget(target);
                }
            }

            decisionCooldown = 8;
        }
    }
}
