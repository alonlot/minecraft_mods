package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.MagicParticleOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A unicorn of the Forbidden Forest: pure white, silver-maned, gold-hooved. Foals are gold
 * and hornless.
 *
 * <p>It is shy rather than fierce. Any player who walks up standing, sprinting or carrying a
 * weapon is fled from; crouch and come slowly and it lets you near. Offered gold (a golden
 * carrot sometimes, a golden apple always) it comes to trust you, after which it no longer
 * flees, can be bred with more gold, and will let you comb a few hairs from its mane - wand
 * cores - every few minutes. Strike it and that trust is gone; strike it at close range and it
 * rears and lashes out with its forehooves before bolting.
 *
 * <p>Wounded, it leaves a trail of silver blood. Killing one drops that blood, and puts a
 * long curse on the killer.
 *
 * <p>Animation is client-side and procedural ({@code UnicornModel}); the server only sends three
 * one-shot events: rear (61), graze (62) and head toss (64). See feedback on the trap bytes
 * 21/35/63, which must never be used here.
 */
public class UnicornEntity extends Animal {
    public static final byte EVENT_REAR = 61;
    public static final byte EVENT_GRAZE = 62;
    public static final byte EVENT_TOSS = 64;
    public static final byte EVENT_GRAZE_END = 66;

    public static final int REAR_TICKS = 32;
    public static final int GRAZE_TICKS = 70;
    public static final int TOSS_TICKS = 24;
    /** Tick of the rear at which the forehooves come down on whoever is in front. */
    private static final int REAR_STRIKE_TICK = 20;
    private static final int HAIR_COOLDOWN_TICKS = 20 * 60 * 5;

    private static final int SILVER = 0xE4E9F8;
    private static final int GOLD = 0xF5D98A;

    private final Set<UUID> trusted = new HashSet<>();
    private int hairCooldown;

    // One-shot animation timers. Run on both sides: the server uses them to hold the unicorn
    // still and time the strike, the client to drive the model.
    private int rearTicks;
    private int grazeTicks;
    private int tossTicks;
    @Nullable
    private UUID rearTarget;

    // Client-only gait state.
    private float gaitSpeed;
    private float gaitSpeedO;
    private float gaitPhase;
    private float gaitPhaseO;

    public UnicornEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                // Long-legged enough to walk straight up a block.
                .add(Attributes.STEP_HEIGHT, 1.1D);
    }

    public static boolean canSpawn(EntityType<UnicornEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random) {
        // Not checkAnimalSpawnRules: that wants daylight, and the forest floor under its canopy
        // never gets enough of it.
        return checkMobSpawnRules(type, level, spawnType, pos, random)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HoldStillGoal());
        this.goalSelector.addGoal(2, new PanicGoal(this, 2.3D));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 12.0F, 1.5D, 2.3D, this::shiesFrom));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Monster.class, 10.0F, 1.5D, 2.3D));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new TemptGoal(this, 0.9D,
                Ingredient.of(Items.GOLDEN_APPLE, Items.GOLDEN_CARROT, Items.ENCHANTED_GOLDEN_APPLE), false));
        this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(7, new GrazeGoal());
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------------------ trust

    public boolean trusts(Player player) {
        return trusted.contains(player.getUUID());
    }

    /** Who it runs from: anyone it does not trust who is not creeping up with empty hands. */
    private boolean shiesFrom(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (trusts(player)) {
            return false;
        }
        return !player.isCrouching() || player.isSprinting() || isArmed(player);
    }

    private static boolean isArmed(Player player) {
        ItemStack held = player.getMainHandItem();
        return held.getItem() instanceof TieredItem
                || held.getItem() instanceof ProjectileWeaponItem
                || held.getItem() instanceof TridentItem
                || held.getItem() instanceof MaceItem;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);

        if (isFood(stack) && !trusts(player)) {
            if (level().isClientSide) {
                return InteractionResult.CONSUME;
            }
            if (!player.isCrouching()) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn.shy")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                return InteractionResult.FAIL;
            }
            usePlayerItem(player, hand, stack);
            playSound(SoundEvents.HORSE_EAT, 1.0F, getVoicePitch());
            boolean won = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                    || this.random.nextInt(3) == 0;
            if (won) {
                trusted.add(player.getUUID());
                startToss();
                ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight() * 0.9D, getZ(),
                        5, 0.4D, 0.3D, 0.4D, 0.0D);
                sparkleBurst(14);
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn.trusts")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), true);
            } else {
                ((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.9D, getZ(),
                        4, 0.3D, 0.2D, 0.3D, 0.0D);
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn.wary")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
            return InteractionResult.SUCCESS;
        }

        // Trust lives on the server only, so the client cannot tell; it claims every empty-handed
        // click on an adult so the click is not passed on to the off hand.
        if (stack.isEmpty() && !isBaby() && level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && trusts(player) && !isBaby()) {
            if (hairCooldown > 0) {
                startToss();
                playSound(SoundEvents.HORSE_BREATHE, 1.0F, getVoicePitch());
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn.groom_wait")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
                return InteractionResult.SUCCESS;
            }
            hairCooldown = HAIR_COOLDOWN_TICKS;
            spawnAtLocation(new ItemStack(ExampleMod.UNICORN_HAIR_CORE.get()), 1.4F);
            startToss();
            playSound(SoundEvents.HORSE_BREATHE, 1.0F, getVoicePitch());
            sparkleBurst(10);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn.groomed")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), true);
            return InteractionResult.SUCCESS;
        }

        // Trusted and holding gold: vanilla breeding / foal growth.
        if (isFood(stack) && trusts(player)) {
            return super.mobInteract(player, hand);
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------------------ hurt / death

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            Entity attacker = source.getEntity();
            if (attacker instanceof Player player) {
                trusted.remove(player.getUUID());
            }
            stopGraze();
            ((ServerLevel) level()).sendParticles(MagicParticleOptions.spark(SILVER, 0.9F),
                    getX(), getY() + getBbHeight() * 0.55D, getZ(), 10, 0.3D, 0.25D, 0.3D, 0.15D);
            if (attacker instanceof LivingEntity living && !isBaby() && rearTicks == 0
                    && distanceToSqr(living) < 4.0D * 4.0D && isAlive()) {
                startRear(living);
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(MagicParticleOptions.mote(isBaby() ? GOLD : SILVER, 1.3F),
                    getX(), getY() + getBbHeight() * 0.5D, getZ(), 40, 0.6D, 0.5D, 0.6D, 0.02D);
            if (source.getEntity() instanceof ServerPlayer killer) {
                killer.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 20 * 60 * 20, 1));
                killer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60 * 2, 0));
                killer.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 6, 0));
                killer.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn.slain")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            }
        }
    }

    // ------------------------------------------------------------------------------ one-shot moves

    private void startRear(LivingEntity target) {
        stopGraze();
        rearTicks = REAR_TICKS;
        rearTarget = target.getUUID();
        getNavigation().stop();
        level().broadcastEntityEvent(this, EVENT_REAR);
        playSound(SoundEvents.HORSE_ANGRY, 1.2F, getVoicePitch());
    }

    private void startToss() {
        if (rearTicks > 0) {
            return;
        }
        tossTicks = TOSS_TICKS;
        level().broadcastEntityEvent(this, EVENT_TOSS);
    }

    private void stopGraze() {
        if (grazeTicks > 0) {
            grazeTicks = 0;
            level().broadcastEntityEvent(this, EVENT_GRAZE_END);
        }
    }

    private void startGraze() {
        grazeTicks = GRAZE_TICKS;
        getNavigation().stop();
        level().broadcastEntityEvent(this, EVENT_GRAZE);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_REAR) {
            rearTicks = REAR_TICKS;
            grazeTicks = 0;
        } else if (id == EVENT_GRAZE) {
            grazeTicks = GRAZE_TICKS;
        } else if (id == EVENT_TOSS) {
            tossTicks = TOSS_TICKS;
        } else if (id == EVENT_GRAZE_END) {
            grazeTicks = 0;
        } else {
            super.handleEntityEvent(id);
        }
    }

    public boolean isBusy() {
        return rearTicks > 0 || grazeTicks > 0;
    }

    private boolean isRearing() {
        return rearTicks > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (rearTicks > 0) {
            rearTicks--;
            if (!level().isClientSide) {
                setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
                if (rearTicks == REAR_TICKS - REAR_STRIKE_TICK) {
                    strikeWithForehooves();
                }
            }
        }
        if (grazeTicks > 0) {
            grazeTicks--;
        }
        if (tossTicks > 0) {
            tossTicks--;
        }
        if (level().isClientSide) {
            tickGait();
            tickClientSparkle();
        } else {
            if (hairCooldown > 0) {
                hairCooldown--;
            }
            tickBloodTrail();
        }
    }

    private void strikeWithForehooves() {
        if (rearTarget == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = server.getEntity(rearTarget);
        rearTarget = null;
        if (!(target instanceof LivingEntity living) || !living.isAlive() || distanceToSqr(living) > 3.5D * 3.5D) {
            return;
        }
        playSound(SoundEvents.HORSE_LAND, 1.2F, 0.9F);
        if (doHurtTarget(living)) {
            Vec3 push = living.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D).normalize();
            living.push(push.x * 1.1D, 0.35D, push.z * 1.1D);
            living.hurtMarked = true;
        }
    }

    /** A wounded unicorn bleeds silver as it goes. */
    private void tickBloodTrail() {
        if (getHealth() >= getMaxHealth() * 0.5F || tickCount % 4 != 0) {
            return;
        }
        double moved = getDeltaMovement().horizontalDistanceSqr();
        if (moved < 1.0E-4D && tickCount % 16 != 0) {
            return;
        }
        ((ServerLevel) level()).sendParticles(MagicParticleOptions.mote(SILVER, 0.7F),
                getX(), getY() + getBbHeight() * 0.35D, getZ(), 2, 0.25D, 0.15D, 0.25D, 0.0D);
    }

    private void tickClientSparkle() {
        if (this.random.nextInt(isBaby() ? 14 : 9) != 0 || isInvisible()) {
            return;
        }
        float scale = getScale();
        double r = 0.6D * scale;
        level().addParticle(MagicParticleOptions.mote(isBaby() ? GOLD : SILVER, 0.45F * scale + 0.2F),
                getX() + (random.nextDouble() - 0.5D) * 2.0D * r,
                getY() + (0.5D + random.nextDouble() * 1.2D) * scale,
                getZ() + (random.nextDouble() - 0.5D) * 2.0D * r,
                0.0D, 0.01D, 0.0D);
    }

    private void sparkleBurst(int count) {
        ((ServerLevel) level()).sendParticles(MagicParticleOptions.mote(isBaby() ? GOLD : SILVER, 0.9F),
                getX(), getY() + getBbHeight() * 0.8D, getZ(), count, 0.5D, 0.4D, 0.5D, 0.01D);
    }

    // ------------------------------------------------------------------------------ client gait

    /** Blocks travelled per full gait cycle at a given speed (blocks/tick), at adult size. */
    public static float strideFor(float speed) {
        float trot = Mth.clamp((speed - 0.08F) / 0.07F, 0.0F, 1.0F);
        float gallop = Mth.clamp((speed - 0.17F) / 0.08F, 0.0F, 1.0F);
        return Mth.lerp(gallop, Mth.lerp(trot, 1.5F, 2.3F), 3.6F);
    }

    private void tickGait() {
        gaitSpeedO = gaitSpeed;
        gaitPhaseO = gaitPhase;
        double dx = getX() - xo;
        double dz = getZ() - zo;
        float moved = (float) Math.sqrt(dx * dx + dz * dz);
        if (moved > 1.0F) {
            moved = 0.0F; // teleport, not a gallop
        }
        gaitSpeed += (moved - gaitSpeed) * 0.35F;
        float scale = Math.max(0.3F, getScale());
        gaitPhase += moved / (strideFor(gaitSpeed / scale) * scale);
        if (gaitPhase > 1000.0F) {
            gaitPhase -= 1000.0F;
            gaitPhaseO -= 1000.0F;
        }
    }

    public float getGaitSpeed(float partialTick) {
        return Mth.lerp(partialTick, gaitSpeedO, gaitSpeed) / Math.max(0.3F, getScale());
    }

    public float getGaitPhase(float partialTick) {
        return Mth.lerp(partialTick, gaitPhaseO, gaitPhase);
    }

    /** 0..1 progress of a rear, or -1 when not rearing. */
    public float getRearProgress(float partialTick) {
        return rearTicks <= 0 ? -1.0F : Mth.clamp(1.0F - (rearTicks - partialTick) / REAR_TICKS, 0.0F, 1.0F);
    }

    public float getGrazeProgress(float partialTick) {
        return grazeTicks <= 0 ? -1.0F : Mth.clamp(1.0F - (grazeTicks - partialTick) / GRAZE_TICKS, 0.0F, 1.0F);
    }

    public float getTossProgress(float partialTick) {
        return tossTicks <= 0 ? -1.0F : Mth.clamp(1.0F - (tossTicks - partialTick) / TOSS_TICKS, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------------------ breeding / spawning

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        UnicornEntity foal = ModEntities.UNICORN.get().create(level);
        if (foal != null && otherParent instanceof UnicornEntity other) {
            // A foal trusts whoever its parents trusted.
            foal.trusted.addAll(this.trusted);
            foal.trusted.addAll(other.trusted);
        }
        return foal;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        if (groupData == null) {
            // The first of a herd is always grown; any after it may be a foal.
            groupData = new AgeableMobGroupData(0.35F);
        }
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // ------------------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag list = new ListTag();
        for (UUID id : trusted) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Trusted", list);
        tag.putInt("HairCooldown", hairCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        trusted.clear();
        ListTag list = tag.getList("Trusted", Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            trusted.add(NbtUtils.loadUUID(t));
        }
        hairCooldown = tag.getInt("HairCooldown");
    }

    // ------------------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.18F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.liquid()) {
            return;
        }
        boolean galloping = getDeltaMovement().horizontalDistanceSqr() > 0.17D * 0.17D;
        playSound(galloping ? SoundEvents.HORSE_GALLOP : SoundEvents.HORSE_STEP, galloping ? 0.3F : 0.18F,
                isBaby() ? 1.4F : 1.1F);
    }

    // ------------------------------------------------------------------------------ goals

    /** Freezes navigation while it rears, so the animation is not dragged along and nothing else runs. */
    private final class HoldStillGoal extends Goal {
        HoldStillGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isRearing();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getNavigation().stop();
        }
    }

    /** Now and then, standing on grass with nothing to worry about, it lowers its head and grazes. */
    private final class GrazeGoal extends Goal {
        GrazeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (isBusy() || getRandom().nextInt(isBaby() ? 900 : 500) != 0 || !onGround()) {
                return false;
            }
            BlockState below = level().getBlockState(blockPosition().below());
            return below.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                    || below.is(net.minecraft.world.level.block.Blocks.PODZOL)
                    || below.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK);
        }

        @Override
        public void start() {
            startGraze();
        }

        @Override
        public boolean canContinueToUse() {
            return grazeTicks > 0;
        }

        @Override
        public void stop() {
            // Interrupted by fear (or simply finished): lift the head now.
            stopGraze();
        }
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && other instanceof UnicornEntity && isInLove() && other.isInLove();
    }

    /** Keeps an untrusting unicorn from simply wandering into someone. */
    @Override
    public boolean isPushable() {
        return !isBusy() && super.isPushable();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (rearTicks > 0 && rearTarget != null && level() instanceof ServerLevel server) {
            Entity target = server.getEntity(rearTarget);
            if (target != null) {
                getLookControl().setLookAt(target, 30.0F, 30.0F);
                float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(Mth.approachDegrees(getYRot(), yaw, 12.0F));
                yBodyRot = getYRot();
            }
        }
    }

}
