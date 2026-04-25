package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.item.HogwartsInviteItem;
import com.hogwartsmagic.item.OwlRouteTagItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class OwlEntity extends TamableAnimal implements FlyingAnimal {
    public static final double PERCH_INTERACTION_RANGE = 1.75D;
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_MAIL =
            SynchedEntityData.defineId(OwlEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final String MAIL_TARGET = "hogwartsmagic:mail_target";
    private static final String MAIL_SOURCE = "hogwartsmagic:mail_source";
    private static final String MAIL_STACK = "hogwartsmagic:mail_stack";
    private static final String MAIL_REPATH_TICKS = "hogwartsmagic:mail_repath_ticks";
    private static final String MAIL_TARGET_PLAYER = "hogwartsmagic:mail_target_player";
    private static final String ROUTE_TAG_ITEM = "hogwartsmagic:route_tag_item";
    private static final String DEPART_TARGET = "hogwartsmagic:depart_target";
    private static final String DEPART_TICKS = "hogwartsmagic:depart_ticks";
    private static final String DEPART_REPATH_TICKS = "hogwartsmagic:depart_repath_ticks";

    public final AnimationState flyingAnimationState = new AnimationState();

    public OwlEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FLYING_SPEED, 0.6D);
    }

    public static boolean canSpawn(EntityType<OwlEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return checkMobSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CARRIED_MAIL, ItemStack.EMPTY);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new OwlRoutePerchGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new OwlFollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(4, new OwlIdleFlightGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
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
    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            tickPostalFlight();
        }
        if (this.level().isClientSide) {
            if (this.isFlying()) {
                this.flyingAnimationState.startIfStopped(this.tickCount);
            } else {
                this.flyingAnimationState.stop();
            }
        }
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return ModEntities.OWL.get().create(level);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
        // Owls don't take fall damage
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return net.minecraft.sounds.SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return net.minecraft.sounds.SoundEvents.PARROT_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.PARROT_DEATH;
    }

    @Override
    public boolean isFood(net.minecraft.world.item.ItemStack stack) {
        return stack.is(net.minecraft.world.item.Items.WHEAT_SEEDS);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.WHEAT_SEEDS) && !this.isTame()) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (!this.level().isClientSide) {
                if (this.random.nextInt(3) == 0) {
                    this.tame(player);
                    this.navigation.stop();
                    this.setOrderedToSit(true);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.isTame() && this.isOwnedBy(player)) {
            if (stack.is(ExampleMod.OWL_ROUTE_TAG.get()) && OwlRouteTagItem.hasRoute(stack)) {
                if (!this.level().isClientSide) {
                    setRouteTag(stack.copyWithCount(1));
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.owl.route_set"), true);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (stack.isEmpty() && player.isShiftKeyDown()) {
                if (!this.level().isClientSide) {
                    ItemStack removed = removeRouteTag();
                    if (!removed.isEmpty()) {
                        if (!player.addItem(removed)) {
                            player.drop(removed, false);
                        }
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.owl.route_cleared"), true);
                        return InteractionResult.SUCCESS;
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    public boolean isPostalBusy() {
        return this.getPersistentData().contains(MAIL_TARGET);
    }

    public ItemStack getCarriedMail() {
        return this.entityData.get(DATA_CARRIED_MAIL);
    }

    public boolean canServeRoute(BlockPos sourcePos, BlockPos targetPos) {
        ItemStack routeTag = getRouteTag();
        return routeTag.isEmpty() || OwlRouteTagItem.matchesRoute(routeTag, sourcePos, targetPos);
    }

    public ItemStack getRouteTag() {
        CompoundTag data = getPersistentData();
        if (!data.contains(ROUTE_TAG_ITEM)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(this.level().registryAccess(), data.getCompound(ROUTE_TAG_ITEM));
    }

    public boolean hasAssignedRoute() {
        return !getRouteTag().isEmpty();
    }

    @Nullable
    public BlockPos getAssignedPerchAnchor() {
        ItemStack routeTag = getRouteTag();
        if (routeTag.isEmpty()) {
            return null;
        }
        BlockPos routeA = OwlRouteTagItem.getRouteA(routeTag);
        BlockPos routeB = OwlRouteTagItem.getRouteB(routeTag);
        if (routeA == null) {
            return routeB;
        }
        if (routeB == null) {
            return routeA;
        }
        return this.blockPosition().distSqr(routeA) <= this.blockPosition().distSqr(routeB) ? routeA : routeB;
    }

    public void setRouteTag(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        stack.save(this.level().registryAccess(), tag);
        getPersistentData().put(ROUTE_TAG_ITEM, tag);
        this.navigation.stop();
    }

    public ItemStack removeRouteTag() {
        ItemStack routeTag = getRouteTag();
        if (!routeTag.isEmpty()) {
            getPersistentData().remove(ROUTE_TAG_ITEM);
        }
        return routeTag;
    }

    @Nullable
    private BlockPos getRoutePickupPerch() {
        ItemStack routeTag = getRouteTag();
        if (routeTag.isEmpty()) {
            return null;
        }

        BlockPos routeA = OwlRouteTagItem.getRouteA(routeTag);
        BlockPos routeB = OwlRouteTagItem.getRouteB(routeTag);
        BlockPos pickup = getDispatchReadyPerch(routeA, routeB);
        if (pickup != null) {
            return pickup;
        }
        pickup = getDispatchReadyPerch(routeB, routeA);
        if (pickup != null) {
            return pickup;
        }
        return null;
    }

    @Nullable
    private BlockPos getDispatchReadyPerch(@Nullable BlockPos sourcePos, @Nullable BlockPos targetPos) {
        if (sourcePos == null || targetPos == null) {
            return null;
        }
        if (!this.level().isLoaded(sourcePos)) {
            return null;
        }
        if (!(this.level().getBlockEntity(sourcePos) instanceof OwlPerchBlockEntity perch)) {
            return null;
        }
        BlockPos linkedPos = perch.getLinkedPos();
        if (linkedPos == null || !linkedPos.equals(targetPos)) {
            return null;
        }
        return perch.hasDispatchReadyMail() ? sourcePos : null;
    }

    @Nullable
    private BlockPos getRouteGoalPerch() {
        BlockPos pickup = getRoutePickupPerch();
        return pickup != null ? pickup : getAssignedPerchAnchor();
    }

    public boolean isCloseEnoughToPerch(BlockPos perchPos) {
        Vec3 perchCenter = Vec3.atCenterOf(perchPos).add(0.0D, 1.0D, 0.0D);
        return this.position().distanceToSqr(perchCenter) <= PERCH_INTERACTION_RANGE * PERCH_INTERACTION_RANGE;
    }

    public void beginPostalDelivery(BlockPos sourcePos, BlockPos targetPos, ItemStack payload) {
        CompoundTag data = getPersistentData();
        data.putLong(MAIL_SOURCE, sourcePos.asLong());
        data.putLong(MAIL_TARGET, targetPos.asLong());
        CompoundTag stackTag = new CompoundTag();
        payload.save(this.level().registryAccess(), stackTag);
        data.put(MAIL_STACK, stackTag);
        this.entityData.set(DATA_CARRIED_MAIL, payload.copy());
        this.setOrderedToSit(false);
        this.navigation.stop();
    }

    public void beginPlayerDelivery(ServerPlayer player, ItemStack payload) {
        CompoundTag data = getPersistentData();
        data.putUUID(MAIL_TARGET_PLAYER, player.getUUID());
        CompoundTag stackTag = new CompoundTag();
        payload.save(this.level().registryAccess(), stackTag);
        data.put(MAIL_STACK, stackTag);
        this.entityData.set(DATA_CARRIED_MAIL, payload.copy());
        this.setOrderedToSit(false);
        this.navigation.stop();
        this.setPersistenceRequired();
    }

    private void tickPostalFlight() {
        CompoundTag data = getPersistentData();
        if (data.contains(DEPART_TARGET)) {
            tickDepartureFlight(data);
            return;
        }
        if (data.hasUUID(MAIL_TARGET_PLAYER) && data.contains(MAIL_STACK)) {
            tickPlayerDelivery(data);
            return;
        }
        if (!data.contains(MAIL_TARGET) || !data.contains(MAIL_STACK)) {
            return;
        }
        BlockPos targetPos = BlockPos.of(data.getLong(MAIL_TARGET));
        Vec3 targetCenter = Vec3.atCenterOf(targetPos).add(0.0, 1.2, 0.0);
        Vec3 delta = targetCenter.subtract(this.position());

        if (delta.lengthSqr() < 2.0D) {
            ItemStack carried = getCarriedMail().copy();
            if (carried.isEmpty() && data.contains(MAIL_STACK)) {
                carried = ItemStack.parseOptional(this.level().registryAccess(), data.getCompound(MAIL_STACK));
            }
            if (!this.level().isLoaded(targetPos)) {
                this.navigation.moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 0.8D);
                return;
            }
            if (this.level().getBlockEntity(targetPos) instanceof com.hogwartsmagic.block.entity.OwlPerchBlockEntity perch) {
                if (perch.receiveDelivery(carried)) {
                    clearDeliveryData(data);
                    this.navigation.stop();
                    this.setNoGravity(false);
                    this.setDeltaMovement(Vec3.ZERO);
                } else {
                    this.navigation.moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 0.8D);
                }
                return;
            } else {
                this.spawnAtLocation(carried);
                clearDeliveryData(data);
                this.navigation.stop();
                this.setNoGravity(false);
                this.setDeltaMovement(Vec3.ZERO);
                return;
            }
        }

        this.setNoGravity(true);
        this.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 30.0F, 30.0F);

        int repathTicks = data.getInt(MAIL_REPATH_TICKS);
        if (repathTicks <= 0 || this.navigation.isDone()) {
            this.navigation.moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 1.25D);
            data.putInt(MAIL_REPATH_TICKS, 10);
        } else {
            data.putInt(MAIL_REPATH_TICKS, repathTicks - 1);
        }

        if (this.horizontalCollision || delta.y > 1.5D) {
            Vec3 lift = this.getDeltaMovement().add(0.0D, 0.18D, 0.0D);
            this.setDeltaMovement(lift.x, Math.min(lift.y, 0.35D), lift.z);
            this.hasImpulse = true;
        }

        if (this.navigation.isDone() && delta.lengthSqr() > 16.0D) {
            Vec3 nudge = delta.normalize().scale(0.10D);
            this.setDeltaMovement(
                    this.getDeltaMovement().x * 0.82D + nudge.x,
                    this.getDeltaMovement().y * 0.82D + Math.max(0.02D, nudge.y),
                    this.getDeltaMovement().z * 0.82D + nudge.z
            );
            this.hasImpulse = true;
        }
    }

    private void tickPlayerDelivery(CompoundTag data) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(data.getUUID(MAIL_TARGET_PLAYER));
        ItemStack carried = getCarriedMail().copy();
        if (carried.isEmpty() && data.contains(MAIL_STACK)) {
            carried = ItemStack.parseOptional(this.level().registryAccess(), data.getCompound(MAIL_STACK));
        }
        if (carried.isEmpty()) {
            clearDeliveryData(data);
            startDepartureFlight(this.position());
            return;
        }

        if (target == null || target.level() != this.level()) {
            this.spawnAtLocation(carried);
            clearDeliveryData(data);
            startDepartureFlight(this.position());
            return;
        }

        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() + 2.6D, 0.0D);
        Vec3 delta = targetCenter.subtract(this.position());

        if (delta.lengthSqr() < 3.0D) {
            net.minecraft.world.entity.item.ItemEntity invite = new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel,
                    target.getX(),
                    target.getY() + target.getBbHeight() + 1.0D,
                    target.getZ(),
                    carried.copy()
            );
            invite.setPickUpDelay(0);
            invite.setDeltaMovement(0.0D, -0.12D, 0.0D);
            serverLevel.addFreshEntity(invite);
            if (carried.is(ExampleMod.HOGWARTS_INVITE.get())) {
                net.minecraft.world.entity.item.ItemEntity guide = new net.minecraft.world.entity.item.ItemEntity(
                        serverLevel,
                        target.getX() + 0.25D,
                        target.getY() + target.getBbHeight() + 1.0D,
                        target.getZ(),
                        new ItemStack(ExampleMod.HOGWARTS_GUIDE.get())
                );
                guide.setPickUpDelay(0);
                guide.setDeltaMovement(0.0D, -0.12D, 0.0D);
                serverLevel.addFreshEntity(guide);
                target.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.hogwartsmagic.invite_and_guide_arrived",
                                HogwartsInviteItem.getRecipient(carried)
                        ),
                        false
                );
            } else {
                target.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.hogwartsmagic.invite_arrived",
                                HogwartsInviteItem.getRecipient(carried)
                        ),
                        false
                );
            }
            clearDeliveryData(data);
            startDepartureFlight(target.position());
            return;
        }

        this.setNoGravity(true);
        this.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 30.0F, 30.0F);

        int repathTicks = data.getInt(MAIL_REPATH_TICKS);
        if (repathTicks <= 0 || this.navigation.isDone()) {
            this.navigation.moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 1.35D);
            data.putInt(MAIL_REPATH_TICKS, 10);
        } else {
            data.putInt(MAIL_REPATH_TICKS, repathTicks - 1);
        }

        if (this.horizontalCollision || delta.y > 1.5D) {
            Vec3 lift = this.getDeltaMovement().add(0.0D, 0.18D, 0.0D);
            this.setDeltaMovement(lift.x, Math.min(lift.y, 0.35D), lift.z);
            this.hasImpulse = true;
        }

        if (this.navigation.isDone() && delta.lengthSqr() > 16.0D) {
            Vec3 nudge = delta.normalize().scale(0.11D);
            this.setDeltaMovement(
                    this.getDeltaMovement().x * 0.82D + nudge.x,
                    this.getDeltaMovement().y * 0.82D + Math.max(0.02D, nudge.y),
                    this.getDeltaMovement().z * 0.82D + nudge.z
            );
            this.hasImpulse = true;
        }
    }

    private void startDepartureFlight(Vec3 origin) {
        CompoundTag data = getPersistentData();
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double distance = 22.0D + this.random.nextDouble() * 10.0D;
        BlockPos departTarget = BlockPos.containing(
                origin.x + Math.cos(angle) * distance,
                Math.max(origin.y + 16.0D + this.random.nextDouble() * 8.0D, this.getY() + 10.0D),
                origin.z + Math.sin(angle) * distance
        );
        data.putLong(DEPART_TARGET, departTarget.asLong());
        data.putInt(DEPART_TICKS, 80);
        data.putInt(DEPART_REPATH_TICKS, 0);
        this.navigation.stop();
        this.setNoGravity(true);
    }

    private void tickDepartureFlight(CompoundTag data) {
        int remainingTicks = data.getInt(DEPART_TICKS);
        if (remainingTicks <= 0) {
            data.remove(DEPART_TARGET);
            data.remove(DEPART_TICKS);
            data.remove(DEPART_REPATH_TICKS);
            this.discard();
            return;
        }

        BlockPos targetPos = BlockPos.of(data.getLong(DEPART_TARGET));
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        Vec3 delta = targetCenter.subtract(this.position());
        this.setNoGravity(true);
        this.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 30.0F, 30.0F);

        int repathTicks = data.getInt(DEPART_REPATH_TICKS);
        if (repathTicks <= 0 || this.navigation.isDone()) {
            this.navigation.moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 1.45D);
            data.putInt(DEPART_REPATH_TICKS, 8);
        } else {
            data.putInt(DEPART_REPATH_TICKS, repathTicks - 1);
        }

        if (this.horizontalCollision || delta.y > 1.0D) {
            Vec3 lift = this.getDeltaMovement().add(0.0D, 0.2D, 0.0D);
            this.setDeltaMovement(lift.x, Math.min(lift.y, 0.36D), lift.z);
            this.hasImpulse = true;
        }

        if (this.navigation.isDone() && delta.lengthSqr() > 9.0D) {
            Vec3 nudge = delta.normalize().scale(0.12D);
            this.setDeltaMovement(
                    this.getDeltaMovement().x * 0.8D + nudge.x,
                    this.getDeltaMovement().y * 0.8D + Math.max(0.02D, nudge.y),
                    this.getDeltaMovement().z * 0.8D + nudge.z
            );
            this.hasImpulse = true;
        }

        data.putInt(DEPART_TICKS, remainingTicks - 1);
    }

    private void clearDeliveryData(CompoundTag data) {
        data.remove(MAIL_SOURCE);
        data.remove(MAIL_TARGET);
        data.remove(MAIL_STACK);
        data.remove(MAIL_REPATH_TICKS);
        data.remove(MAIL_TARGET_PLAYER);
        this.entityData.set(DATA_CARRIED_MAIL, ItemStack.EMPTY);
    }

    private static class OwlFollowOwnerGoal extends Goal {
        private final OwlEntity owl;
        private final double speedModifier;
        private final float startDistance;
        private final float stopDistance;
        @Nullable
        private Player owner;
        private int timeToRecalcPath;

        private OwlFollowOwnerGoal(OwlEntity owl, double speedModifier, float startDistance, float stopDistance) {
            this.owl = owl;
            this.speedModifier = speedModifier;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!owl.isTame() || owl.isOrderedToSit() || owl.isPassenger() || owl.isPostalBusy() || owl.hasAssignedRoute()) {
                return false;
            }
            LivingEntity ownerEntity = owl.getOwner();
            if (!(ownerEntity instanceof Player player)) {
                return false;
            }
            if (player.isSpectator()) {
                return false;
            }
            if (owl.distanceToSqr(player) < (double) (startDistance * startDistance)) {
                return false;
            }
            this.owner = player;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null
                    && owner.isAlive()
                    && !owl.isOrderedToSit()
                    && !owl.isPostalBusy()
                    && !owl.hasAssignedRoute()
                    && !owl.isPassenger()
                    && owl.distanceToSqr(owner) > (double) (stopDistance * stopDistance);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            owl.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) {
                return;
            }
            owl.getLookControl().setLookAt(owner, 20.0F, (float) owl.getMaxHeadXRot());
            if (--timeToRecalcPath > 0) {
                return;
            }
            timeToRecalcPath = 10;
            Vec3 followTarget = owner.position().add(0.0D, 1.0D, 0.0D);
            owl.getNavigation().moveTo(followTarget.x, followTarget.y, followTarget.z, speedModifier);

            if (owl.horizontalCollision || owner.getY() - owl.getY() > 1.5D) {
                Vec3 lift = owl.getDeltaMovement().add(0.0D, 0.18D, 0.0D);
                owl.setDeltaMovement(lift.x, Math.min(lift.y, 0.32D), lift.z);
                owl.hasImpulse = true;
            }
        }
    }

    private static class OwlRoutePerchGoal extends Goal {
        private final OwlEntity owl;
        private final double speedModifier;
        @Nullable
        private BlockPos perchPos;
        private int timeToRecalcPath;

        private OwlRoutePerchGoal(OwlEntity owl, double speedModifier) {
            this.owl = owl;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!owl.isTame() || owl.isOrderedToSit() || owl.isPassenger() || owl.isPostalBusy() || !owl.hasAssignedRoute()) {
                return false;
            }
            BlockPos targetPerch = owl.getRouteGoalPerch();
            if (targetPerch == null) {
                return false;
            }
            if (owl.isCloseEnoughToPerch(targetPerch)) {
                return false;
            }
            this.perchPos = targetPerch;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (owl.isOrderedToSit() || owl.isPassenger() || owl.isPostalBusy() || !owl.hasAssignedRoute()) {
                return false;
            }
            BlockPos targetPerch = owl.getRouteGoalPerch();
            if (targetPerch != null) {
                this.perchPos = targetPerch;
            }
            return perchPos != null && !owl.isCloseEnoughToPerch(perchPos);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            this.perchPos = null;
            owl.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos targetPerch = owl.getRouteGoalPerch();
            if (targetPerch != null) {
                this.perchPos = targetPerch;
            }
            if (perchPos == null) {
                return;
            }
            Vec3 perchCenter = Vec3.atCenterOf(perchPos).add(0.0D, 1.0D, 0.0D);
            owl.getLookControl().setLookAt(perchCenter.x, perchCenter.y, perchCenter.z, 20.0F, (float) owl.getMaxHeadXRot());
            if (--timeToRecalcPath > 0) {
                return;
            }
            timeToRecalcPath = 10;
            owl.getNavigation().moveTo(perchCenter.x, perchCenter.y, perchCenter.z, speedModifier);

            if (owl.horizontalCollision || perchCenter.y - owl.getY() > 1.5D) {
                Vec3 lift = owl.getDeltaMovement().add(0.0D, 0.18D, 0.0D);
                owl.setDeltaMovement(lift.x, Math.min(lift.y, 0.32D), lift.z);
                owl.hasImpulse = true;
            }
        }
    }

    private static class OwlIdleFlightGoal extends WaterAvoidingRandomFlyingGoal {
        private final OwlEntity owl;

        private OwlIdleFlightGoal(OwlEntity owl, double speedModifier) {
            super(owl, speedModifier);
            this.owl = owl;
        }

        @Override
        public boolean canUse() {
            return !owl.hasAssignedRoute() && !owl.isPostalBusy() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !owl.hasAssignedRoute() && !owl.isPostalBusy() && super.canContinueToUse();
        }
    }
}
