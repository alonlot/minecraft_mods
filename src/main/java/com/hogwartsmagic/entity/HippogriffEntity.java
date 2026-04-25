package com.hogwartsmagic.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class HippogriffEntity extends TamableAnimal implements FlyingAnimal, PlayerRideableJumping {
    private static final double CALM_APPROACH_SPEED_SQR = 0.015D;
    private static final double THREAT_APPROACH_SPEED_SQR = 0.09D;
    private static final int PLAYER_JUMP_COOLDOWN_TICKS = 10;
    private static final float BASE_MOUNT_JUMP = 0.42F;
    private static final float MAX_MOUNT_JUMP_BONUS = 0.38F;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState flyingAnimationState = new AnimationState();

    private float wingFlap;
    private float wingFlapO;
    private float flightProgress;
    private float flightProgressO;
    private float playerJumpStrength;
    private int warningSoundCooldown;
    private int playerJumpCooldown;

    public HippogriffEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 14, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FLYING_SPEED, 0.50D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.FOLLOW_RANGE, 28.0D);
    }

    public static boolean canSpawn(EntityType<HippogriffEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.1D, Ingredient.of(Items.RABBIT), false));
        // Intentionally no FollowOwnerGoal: tamed hippogriffs stay where you leave them.
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new HippogriffRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Monster.class, false));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.RABBIT);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (this.isFood(stack) && !this.isTame()) {
            if (!this.isCalmApproach(player)) {
                if (!this.level().isClientSide) {
                    this.reactToRudeApproach(player);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            if (!this.level().isClientSide) {
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.navigation.stop();
                    this.setTarget(null);
                    this.setOrderedToSit(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (!this.isTame() && hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide) {
                this.reactToRudeApproach(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.isTame() && this.isOwnedBy(player)) {
            if (player.isShiftKeyDown()) {
                this.setOrderedToSit(!this.isOrderedToSit());
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            if (!this.level().isClientSide && !this.isBaby()) {
                this.setOrderedToSit(false);
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();

        this.wingFlapO = this.wingFlap;
        this.flightProgressO = this.flightProgress;

        boolean airborne = this.isAirbornePose();
        float speed = (float) this.getDeltaMovement().horizontalDistance();
        this.flightProgress = Mth.approach(this.flightProgress, airborne ? 1.0F : 0.0F, airborne ? 0.12F : 0.16F);
        this.wingFlap += airborne ? 0.55F + speed * 2.4F : 0.04F + speed * 0.35F;
        if (this.warningSoundCooldown > 0) {
            this.warningSoundCooldown--;
        }
        if (this.playerJumpCooldown > 0) {
            this.playerJumpCooldown--;
        }

        if (!this.isVehicle()) {
            boolean shouldAutofly = !this.isOrderedToSit() && (!this.navigation.isDone() || !this.onGround());
            this.setNoGravity(shouldAutofly);
            if (shouldAutofly && this.onGround() && this.tickCount % 12 == 0) {
                Vec3 motion = this.getDeltaMovement();
                this.setDeltaMovement(motion.x, Math.max(motion.y, 0.42D), motion.z);
                this.hasImpulse = true;
            }
        }

        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (airborne) {
                this.flyingAnimationState.startIfStopped(this.tickCount);
            } else {
                this.flyingAnimationState.stop();
            }
        } else if (!this.isTame() && !this.isOrderedToSit() && this.tickCount % 10 == 0) {
            Player nearestPlayer = this.level().getNearestPlayer(this, 3.0D);
            if (nearestPlayer != null && this.hasLineOfSight(nearestPlayer) && this.isThreateningApproach(nearestPlayer)) {
                this.reactToRudeApproach(nearestPlayer);
            }
        }
    }

    public float getWingFlap(float partialTick) {
        return Mth.lerp(partialTick, this.wingFlapO, this.wingFlap);
    }

    public float getFlightProgress(float partialTick) {
        return Mth.lerp(partialTick, this.flightProgressO, this.flightProgress);
    }

    public boolean isAirbornePose() {
        return !this.onGround() || this.isVehicle();
    }

    private boolean isCalmApproach(Player player) {
        return player.isShiftKeyDown()
                && !player.isSprinting()
                && player.getDeltaMovement().horizontalDistanceSqr() <= CALM_APPROACH_SPEED_SQR;
    }

    private boolean isThreateningApproach(Player player) {
        return !player.isShiftKeyDown()
                || player.isSprinting()
                || player.getDeltaMovement().horizontalDistanceSqr() >= THREAT_APPROACH_SPEED_SQR;
    }

    private void reactToRudeApproach(Player player) {
        this.setOrderedToSit(false);
        this.setTarget(player);
        if (this.warningSoundCooldown <= 0) {
            this.playSound(SoundEvents.HORSE_ANGRY, 0.9F, 0.95F + this.random.nextFloat() * 0.15F);
            this.warningSoundCooldown = 50;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.HORSE_BREATHE, 0.9F, 0.8F + this.random.nextFloat() * 0.25F);
        return super.doHurtTarget(target);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity passenger = this.getFirstPassenger();
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.0D, 0.95D, -0.1D);
    }

    @Override
    public void onPlayerJump(int jumpPower) {
        // The actual launch is handled in travel() so rider movement and lift are applied together.
    }

    @Override
    public boolean canJump() {
        return this.isAlive()
                && this.isTame()
                && !this.isBaby()
                && this.isVehicle()
                && this.playerJumpCooldown == 0
                && (this.onGround() || this.horizontalCollision);
    }

    @Override
    public void handleStartJump(int jumpPower) {
        if (!this.canJump()) {
            return;
        }

        float normalizedPower = Mth.clamp(jumpPower, 0, 90) / 90.0F;
        this.playerJumpStrength = BASE_MOUNT_JUMP + normalizedPower * MAX_MOUNT_JUMP_BONUS;
        this.hasImpulse = true;
    }

    @Override
    public void handleStopJump() {
        // Nothing to do. Hippogriffs use a single takeoff impulse instead of a charge release state.
    }

    @Override
    public void travel(Vec3 travelVector) {
        LivingEntity rider = this.getControllingPassenger();
        if (this.isAlive() && rider instanceof Player player && this.isVehicle() && this.isTame() && this.isOwnedBy(player)) {
            this.setYRot(rider.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(rider.getXRot() * 0.55F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;

            float forward = rider.zza;
            float strafe = rider.xxa * 0.75F;
            float pitchFactor = Mth.clamp(-rider.getXRot() / 45.0F, -1.0F, 1.0F);

            Vec3 look = rider.getLookAngle();
            Vec3 forwardDir = new Vec3(look.x, look.y * 0.85D, look.z).normalize();
            Vec3 rightDir = new Vec3(look.z, 0.0D, -look.x).normalize();

            Vec3 desired = Vec3.ZERO;
            if (Math.abs(forward) > 0.01F) {
                desired = desired.add(forwardDir.scale(forward));
            }
            if (Math.abs(strafe) > 0.01F) {
                desired = desired.add(rightDir.scale(strafe));
            }

            double lift = Math.max(0.0D, forward) * Math.max(0.0F, pitchFactor) * 0.16D;
            double dive = Math.max(0.0D, forward) * Math.max(0.0F, -pitchFactor) * 0.12D;
            double verticalBias = lift - dive;
            if (this.playerJumpStrength > 0.0F && (this.onGround() || this.horizontalCollision)) {
                verticalBias = Math.max(verticalBias, this.playerJumpStrength);
                this.playerJumpStrength = 0.0F;
                this.playerJumpCooldown = PLAYER_JUMP_COOLDOWN_TICKS;
                this.hasImpulse = true;
            }
            if (this.onGround() && this.horizontalCollision && forward > 0.1F) {
                verticalBias = Math.max(verticalBias, 0.42D);
            }
            if (Math.abs(forward) < 0.01F && !this.onGround()) {
                verticalBias -= 0.035D;
            }

            double speed = this.getAttributeValue(Attributes.FLYING_SPEED) * (forward > 0.0F ? 1.05D : 0.8D);
            if (desired.lengthSqr() > 1.0E-4D) {
                Vec3 desiredVel = desired.normalize().scale(speed).add(0.0D, verticalBias, 0.0D);
                this.setDeltaMovement(this.getDeltaMovement().scale(0.86D).add(desiredVel.scale(0.17D)));
            } else {
                Vec3 slowed = this.getDeltaMovement().scale(0.9D);
                if (!this.onGround()) {
                    slowed = slowed.add(0.0D, -0.03D, 0.0D);
                }
                this.setDeltaMovement(slowed);
            }

            this.setNoGravity(!this.onGround());
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.calculateEntityAnimation(false);
            return;
        }

        this.playerJumpStrength = 0.0F;
        this.setNoGravity(false);
        super.travel(travelVector);
    }

    @Override
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
        // Hippogriffs do not take fall damage.
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return ModEntities.HIPPOGRIFF.get().create(level);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    private static class HippogriffRandomFlyingGoal extends WaterAvoidingRandomFlyingGoal {
        private final HippogriffEntity hippogriff;

        private HippogriffRandomFlyingGoal(HippogriffEntity hippogriff, double speedModifier) {
            super(hippogriff, speedModifier);
            this.hippogriff = hippogriff;
        }

        @Override
        public boolean canUse() {
            if (this.hippogriff.isOrderedToSit() || this.hippogriff.isVehicle()) {
                return false;
            }

            if (this.hippogriff.onGround() && this.hippogriff.getRandom().nextInt(4) != 0) {
                return false;
            }

            return super.canUse();
        }

        @Override
        public void start() {
            super.start();
            if (this.hippogriff.onGround()) {
                Vec3 motion = this.hippogriff.getDeltaMovement();
                this.hippogriff.setDeltaMovement(motion.x, Math.max(motion.y, 0.42D), motion.z);
                this.hippogriff.hasImpulse = true;
            }
        }
    }
}
