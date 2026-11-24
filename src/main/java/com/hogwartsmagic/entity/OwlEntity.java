package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.item.HogwartsInviteItem;
import com.hogwartsmagic.item.OwlRouteTagItem;
import com.hogwartsmagic.owlpost.OwlPost;
import com.hogwartsmagic.owlpost.OwlPostData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Owls: wild ones, tamed ones, the owl that brings a first-year letter, and post owls.
 *
 * <p>A post owl is a tamed owl carrying a route tag ({@link OwlRouteTagItem}): it knows the perches on it and lives
 * at one of them, its home. It waits there; when a stack is put in that perch's outbox for a perch it knows, it takes
 * the stack, flies up and away, and leaves the world: the trip goes on in {@link OwlPostData} until it is put back
 * above its home (see {@link OwlPost}).
 */
public class OwlEntity extends TamableAnimal implements FlyingAnimal {
    public static final double PERCH_INTERACTION_RANGE = 1.75D;
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_MAIL =
            SynchedEntityData.defineId(OwlEntity.class, EntityDataSerializers.ITEM_STACK);
    // Persistent-data keys (the owl's ForgeData).
    private static final String MAIL_STACK = "hogwartsmagic:mail_stack";
    private static final String MAIL_REPATH_TICKS = "hogwartsmagic:mail_repath_ticks";
    private static final String MAIL_TARGET_PLAYER = "hogwartsmagic:mail_target_player";
    private static final String MAIL_FLIGHT_TICKS = "hogwartsmagic:mail_flight_ticks";
    private static final String ROUTE_TAG_ITEM = "hogwartsmagic:route_tag_item";
    private static final String HOME = "hogwartsmagic:home";
    private static final String ERRAND = "hogwartsmagic:errand";
    private static final String DEPART_TARGET = "hogwartsmagic:depart_target";
    private static final String DEPART_TICKS = "hogwartsmagic:depart_ticks";
    private static final String DEPART_REPATH_TICKS = "hogwartsmagic:depart_repath_ticks";
    // From before destinations: an owl flying a letter from one perch to its linked perch in the open.
    private static final String LEGACY_MAIL_TARGET = "hogwartsmagic:mail_target";
    private static final String LEGACY_MAIL_SOURCE = "hogwartsmagic:mail_source";

    /** A letter owl that has flown this long without reaching its reader hands the letter over anyway. */
    private static final int LETTER_FLIGHT_LIMIT = 400;
    /** ...as it does at once when the reader is this far off (teleported, or far below in a cave). */
    private static final double LETTER_HAND_OVER_DISTANCE = 64.0D;
    /** How long a post owl is seen flying off before it is out of sight. */
    private static final int ERRAND_DEPART_TICKS = 50;
    /** A home farther than this is not flown to; the owl follows its owner instead. */
    public static final double HOME_RANGE = 96.0D;

    public final AnimationState flyingAnimationState = new AnimationState();

    @Nullable
    private List<GlobalPos> knownCache;
    private boolean homeCached;
    @Nullable
    private GlobalPos homeCache;

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
        this.goalSelector.addGoal(2, new OwlHomePerchGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new OwlFollowOwnerGoal(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(4, new OwlIdleFlightGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
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
        } else if (this.isFlying()) {
            this.flyingAnimationState.startIfStopped(this.tickCount);
        } else {
            this.flyingAnimationState.stop();
        }
    }

    /**
     * The stack in its talons and the route tag it was given are the player's items, held only in this owl's data.
     * Without this they vanished with the bird.
     */
    @Override
    protected void dropEquipment() {
        // Here and not in dropCustomDeathLoot: that one is skipped when doMobLoot is off, and these are not loot.
        super.dropEquipment();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = getPersistentData();
        ItemStack carried = getCarriedMail();
        if (carried.isEmpty() && data.contains(MAIL_STACK)) {
            carried = ItemStack.parseOptional(level.registryAccess(), data.getCompound(MAIL_STACK));
        }
        if (!carried.isEmpty()) {
            this.spawnAtLocation(carried.copy());
            if (data.hasUUID(MAIL_TARGET_PLAYER) && carried.is(ExampleMod.HOGWARTS_INVITE.get())) {
                this.spawnAtLocation(new ItemStack(ExampleMod.HOGWARTS_GUIDE.get()));
            }
        }
        clearDeliveryData(data);
        data.remove(ERRAND);
        ItemStack routeTag = getRouteTag();
        if (!routeTag.isEmpty()) {
            data.remove(ROUTE_TAG_ITEM);
            this.spawnAtLocation(routeTag);
        }
    }

    /** A post owl that dies is struck off the post office's list (being stored for a flight is a discard, not this). */
    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && this.level() instanceof ServerLevel serverLevel) {
            OwlPostData.get(serverLevel.getServer()).removeOwl(this.getUUID());
        }
        super.remove(reason);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return ModEntities.OWL.get().create(level);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // Owls don't take fall damage
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.OWL_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.hogwartsmagic.ModSounds.OWL_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.OWL_DEATH.get();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.WHEAT_SEEDS);
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
            if (stack.is(ExampleMod.OWL_ROUTE_TAG.get())) {
                return OwlRouteTagItem.assignToOwl(stack, player, this);
            }
            if (stack.isEmpty() && player.isShiftKeyDown() && this.hasAssignedRoute() && !this.level().isClientSide) {
                if (this.isOnErrand()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatableWithFallback(
                            "message.hogwartsmagic.owl_post.owl_busy", "This owl is busy with a delivery."), true);
                } else if (player instanceof ServerPlayer serverPlayer) {
                    OwlPost.openOwlScreen(serverPlayer, this);
                }
                return InteractionResult.SUCCESS;
            }
            // Sit / stand, as a wolf does, with an empty hand. A carrier in flight is left alone.
            if (stack.isEmpty() && !this.isOnErrand()) {
                if (!this.level().isClientSide) {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    this.navigation.stop();
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.knownCache = null;
        this.homeCached = false;
        // The carried stack is synced data and never saved; the delivery itself lives in the persistent data (read
        // before this runs). Rebuilt here, or a reloaded owl flies with nothing in its talons.
        CompoundTag data = getPersistentData();
        if (data.contains(MAIL_STACK) && (data.contains(ERRAND) || data.hasUUID(MAIL_TARGET_PLAYER) || data.contains(LEGACY_MAIL_TARGET))) {
            this.entityData.set(DATA_CARRIED_MAIL,
                    ItemStack.parseOptional(this.level().registryAccess(), data.getCompound(MAIL_STACK)));
        }
    }

    // ------------------------------------------------------------------ post owl state

    /** Carrying something: a first-year letter, or a parcel on its way out of sight. */
    public boolean isOnErrand() {
        CompoundTag data = getPersistentData();
        return data.contains(ERRAND) || data.hasUUID(MAIL_TARGET_PLAYER) || data.contains(LEGACY_MAIL_TARGET);
    }

    /** Old name, kept for the goals below. */
    public boolean isPostalBusy() {
        return isOnErrand();
    }

    public ItemStack getCarriedMail() {
        return this.entityData.get(DATA_CARRIED_MAIL);
    }

    public ItemStack getRouteTag() {
        CompoundTag data = getPersistentData();
        if (!data.contains(ROUTE_TAG_ITEM)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(this.level().registryAccess(), data.getCompound(ROUTE_TAG_ITEM));
    }

    public boolean hasAssignedRoute() {
        return !getKnownPerches().isEmpty();
    }

    /** The perches on this owl's route tag. */
    public List<GlobalPos> getKnownPerches() {
        if (knownCache == null) {
            ItemStack tag = getRouteTag();
            knownCache = tag.isEmpty() ? List.of() : List.copyOf(OwlRouteTagItem.getPerches(tag, this.level().dimension()));
        }
        return knownCache;
    }

    public boolean knows(GlobalPos perch) {
        return getKnownPerches().contains(perch);
    }

    @Nullable
    public GlobalPos getHome() {
        if (!homeCached) {
            CompoundTag data = getPersistentData();
            homeCache = data.contains(HOME) ? OwlPostData.readPos(data.getCompound(HOME)) : null;
            homeCached = true;
        }
        return homeCache;
    }

    public void setHome(@Nullable GlobalPos home) {
        if (home == null) {
            getPersistentData().remove(HOME);
        } else {
            getPersistentData().put(HOME, OwlPostData.writePos(home));
        }
        homeCache = home;
        homeCached = true;
        this.navigation.stop();
        syncPostRecord();
    }

    /**
     * Gives this owl a route tag and returns the one it carried before (or empty). Its home stays if the new tag still
     * has it; otherwise it moves to the nearest perch on the tag here, so a freshly tagged owl has somewhere to live.
     */
    public ItemStack giveRouteTag(ItemStack stack) {
        ItemStack old = getRouteTag();
        // save(provider) returns the tag: the two-argument form merges into a COPY of the tag it is given, so the
        // old code here (and for the carried letter) stored an empty compound and the item was lost.
        getPersistentData().put(ROUTE_TAG_ITEM, stack.save(this.level().registryAccess()));
        knownCache = null;
        GlobalPos home = getHome();
        if (home == null || !knows(home)) {
            setHome(nearestKnownPerch());
        } else {
            syncPostRecord();
        }
        return old;
    }

    public ItemStack removeRouteTag() {
        ItemStack routeTag = getRouteTag();
        getPersistentData().remove(ROUTE_TAG_ITEM);
        knownCache = null;
        setHome(null);
        return routeTag;
    }

    @Nullable
    private GlobalPos nearestKnownPerch() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        OwlPostData data = OwlPostData.get(serverLevel.getServer());
        GlobalPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (GlobalPos pos : getKnownPerches()) {
            if (!pos.dimension().equals(this.level().dimension())) {
                continue;
            }
            double d = pos.pos().distSqr(this.blockPosition());
            boolean exists = data.hasPerch(pos);
            // An existing perch always beats one the post office has not heard of.
            if (best == null || (exists && !data.hasPerch(best)) || (exists == data.hasPerch(best) && d < bestDist)) {
                best = pos;
                bestDist = d;
            }
        }
        return best;
    }

    /** Mirrors this owl into the post office's list (what it knows, where it lives), or strikes it off. */
    public void syncPostRecord() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        OwlPostData data = OwlPostData.get(serverLevel.getServer());
        if (!this.isTame() || getKnownPerches().isEmpty()) {
            data.removeOwl(this.getUUID());
            return;
        }
        data.putOwl(this.getUUID(), new OwlPostData.OwlRecord(this.getOwnerUUID(), getHome(), getKnownPerches()));
    }

    private boolean hasReachableHome() {
        GlobalPos home = getHome();
        return home != null
                && home.dimension().equals(this.level().dimension())
                && home.pos().distSqr(this.blockPosition()) <= HOME_RANGE * HOME_RANGE
                && this.level().isLoaded(home.pos());
    }

    // ------------------------------------------------------------------ carrying a parcel

    /** Takes a perch's outbox stack and sets off with it. */
    public void carryOff(ItemStack payload, GlobalPos from, GlobalPos dest, @Nullable UUID sender) {
        CompoundTag data = getPersistentData();
        CompoundTag errand = new CompoundTag();
        errand.put("From", OwlPostData.writePos(from));
        errand.put("Dest", OwlPostData.writePos(dest));
        if (sender != null) {
            errand.putUUID("Sender", sender);
        }
        data.put(ERRAND, errand);
        data.put(MAIL_STACK, payload.save(this.level().registryAccess()));
        this.entityData.set(DATA_CARRIED_MAIL, payload.copy());
        this.setOrderedToSit(false);
        this.navigation.stop();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), com.hogwartsmagic.ModSounds.OWL_AMBIENT.get(),
                SoundSource.NEUTRAL, 0.9F, 1.15F);
        startDepartureFlight(this.position(), ERRAND_DEPART_TICKS);
    }

    /** Out of sight: the parcel and this owl become entries in the post office, and the entity leaves the world. */
    private void vanishIntoFlight() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag data = getPersistentData();
        CompoundTag errand = data.getCompound(ERRAND);
        GlobalPos from = OwlPostData.readPos(errand.getCompound("From"));
        GlobalPos dest = OwlPostData.readPos(errand.getCompound("Dest"));
        UUID sender = errand.hasUUID("Sender") ? errand.getUUID("Sender") : null;
        ItemStack payload = getCarriedMail().copy();
        if (payload.isEmpty() && data.contains(MAIL_STACK)) {
            payload = ItemStack.parseOptional(serverLevel.registryAccess(), data.getCompound(MAIL_STACK));
        }
        data.remove(ERRAND);
        clearDeliveryData(data);
        clearDeparture(data);
        this.setNoGravity(false);
        this.setDeltaMovement(Vec3.ZERO);
        this.navigation.stop();
        if (from == null || dest == null) {
            if (!payload.isEmpty()) {
                this.spawnAtLocation(payload);
            }
            return;
        }
        OwlPostData post = OwlPostData.get(serverLevel.getServer());
        long now = OwlPost.now(serverLevel.getServer());
        int travel = OwlPost.travelTicks(from, dest);
        post.addDelivery(dest, payload, now + travel, sender, from);

        if (this.isLeashed()) {
            this.dropLeash(true, true);
        }
        this.stopRiding();
        this.ejectPassengers();
        GlobalPos home = getHome() != null ? getHome() : from;
        CompoundTag saved = new CompoundTag();
        if (!this.save(saved)) {
            return; // cannot be stored (should not happen once unmounted): it stays here, the parcel still goes
        }
        post.addReturn(this.getUUID(), saved, home, now + 2L * travel);
        serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.4D, this.getZ(), 8, 0.25D, 0.25D, 0.25D, 0.02D);
        this.discard();
    }

    // ------------------------------------------------------------------ first-year letter

    public void beginPlayerDelivery(ServerPlayer player, ItemStack payload) {
        CompoundTag data = getPersistentData();
        data.putUUID(MAIL_TARGET_PLAYER, player.getUUID());
        data.put(MAIL_STACK, payload.save(this.level().registryAccess()));
        data.putInt(MAIL_FLIGHT_TICKS, 0);
        this.entityData.set(DATA_CARRIED_MAIL, payload.copy());
        this.setOrderedToSit(false);
        this.navigation.stop();
        this.setPersistenceRequired();
    }

    private void tickPostalFlight() {
        CompoundTag data = getPersistentData();
        if (data.contains(LEGACY_MAIL_TARGET)) {
            convertLegacyFlight(data);
        }
        if (data.contains(DEPART_TARGET)) {
            tickDepartureFlight(data);
            return;
        }
        if (data.contains(ERRAND)) {
            startDepartureFlight(this.position(), ERRAND_DEPART_TICKS); // reloaded mid take-off
            return;
        }
        if (data.hasUUID(MAIL_TARGET_PLAYER) && data.contains(MAIL_STACK)) {
            tickPlayerDelivery(data);
            return;
        }
        if (this.tickCount % 100 == 37 && this.isTame()) {
            if (hasAssignedRoute() && getHome() == null) {
                setHome(nearestKnownPerch()); // an owl tagged before homes existed settles at its nearest perch
            } else {
                syncPostRecord();
            }
        }
    }

    /** An owl saved mid-flight by the old perch-to-perch post: its letter lands as an ordinary delivery. */
    private void convertLegacyFlight(CompoundTag data) {
        if (this.level() instanceof ServerLevel serverLevel && data.contains(MAIL_STACK)) {
            ItemStack payload = ItemStack.parseOptional(serverLevel.registryAccess(), data.getCompound(MAIL_STACK));
            GlobalPos dest = GlobalPos.of(serverLevel.dimension(), BlockPos.of(data.getLong(LEGACY_MAIL_TARGET)));
            GlobalPos source = data.contains(LEGACY_MAIL_SOURCE)
                    ? GlobalPos.of(serverLevel.dimension(), BlockPos.of(data.getLong(LEGACY_MAIL_SOURCE))) : null;
            OwlPostData.get(serverLevel.getServer()).addDelivery(dest, payload, OwlPost.now(serverLevel.getServer()), null, source);
        }
        data.remove(LEGACY_MAIL_TARGET);
        data.remove(LEGACY_MAIL_SOURCE);
        clearDeliveryData(data);
        this.setNoGravity(false);
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
            startDepartureFlight(this.position(), 80);
            return;
        }

        if (target == null) {
            // Logged out mid-flight: hold here and wait, the delivery is saved with the owl (persistence required).
            // Dropping the letter in the open lost the one-time first-year invite whenever somebody else kept
            // the chunk loaded long enough for the item to despawn.
            this.navigation.stop();
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        int flown = data.getInt(MAIL_FLIGHT_TICKS) + 1;
        data.putInt(MAIL_FLIGHT_TICKS, flown);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (target.level() != this.level()
                || dx * dx + dz * dz > LETTER_HAND_OVER_DISTANCE * LETTER_HAND_OVER_DISTANCE
                || flown > LETTER_FLIGHT_LIMIT) {
            // Out of reach (another dimension, teleported away, or no way through to them - a cave, a roof): the
            // letter goes straight into their hands. It is the one first-year letter and must not be lost.
            giveOrDrop(target, carried.copy());
            if (carried.is(ExampleMod.HOGWARTS_INVITE.get())) {
                giveOrDrop(target, new ItemStack(ExampleMod.HOGWARTS_GUIDE.get()));
            }
            announceArrival(target, carried);
            clearDeliveryData(data);
            startDepartureFlight(this.position(), 80);
            return;
        }

        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() + 2.6D, 0.0D);
        Vec3 delta = targetCenter.subtract(this.position());

        if (delta.lengthSqr() < 3.0D) {
            net.minecraft.world.entity.item.ItemEntity invite = new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel, target.getX(), target.getY() + target.getBbHeight() + 1.0D, target.getZ(), carried.copy());
            invite.setPickUpDelay(0);
            invite.setDeltaMovement(0.0D, -0.12D, 0.0D);
            serverLevel.addFreshEntity(invite);
            if (carried.is(ExampleMod.HOGWARTS_INVITE.get())) {
                net.minecraft.world.entity.item.ItemEntity guide = new net.minecraft.world.entity.item.ItemEntity(
                        serverLevel, target.getX() + 0.25D, target.getY() + target.getBbHeight() + 1.0D, target.getZ(),
                        new ItemStack(ExampleMod.HOGWARTS_GUIDE.get()));
                guide.setPickUpDelay(0);
                guide.setDeltaMovement(0.0D, -0.12D, 0.0D);
                serverLevel.addFreshEntity(guide);
            }
            announceArrival(target, carried);
            clearDeliveryData(data);
            startDepartureFlight(target.position(), 80);
            return;
        }

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
                    this.getDeltaMovement().z * 0.82D + nudge.z);
            this.hasImpulse = true;
        }
    }

    private static void announceArrival(ServerPlayer target, ItemStack carried) {
        target.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        carried.is(ExampleMod.HOGWARTS_INVITE.get())
                                ? "message.hogwartsmagic.invite_and_guide_arrived"
                                : "message.hogwartsmagic.invite_arrived",
                        HogwartsInviteItem.getRecipient(carried)),
                false);
    }

    private static void giveOrDrop(ServerPlayer target, ItemStack stack) {
        if (!target.addItem(stack)) {
            target.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------ flying off

    private void startDepartureFlight(Vec3 origin, int ticks) {
        CompoundTag data = getPersistentData();
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        double distance = 22.0D + this.random.nextDouble() * 10.0D;
        BlockPos departTarget = BlockPos.containing(
                origin.x + Math.cos(angle) * distance,
                Math.max(origin.y + 16.0D + this.random.nextDouble() * 8.0D, this.getY() + 10.0D),
                origin.z + Math.sin(angle) * distance);
        data.putLong(DEPART_TARGET, departTarget.asLong());
        data.putInt(DEPART_TICKS, ticks);
        data.putInt(DEPART_REPATH_TICKS, 0);
        this.navigation.stop();
        this.setNoGravity(true);
    }

    private static void clearDeparture(CompoundTag data) {
        data.remove(DEPART_TARGET);
        data.remove(DEPART_TICKS);
        data.remove(DEPART_REPATH_TICKS);
    }

    private void tickDepartureFlight(CompoundTag data) {
        boolean errand = data.contains(ERRAND);
        if (!errand && this.isTame()) {
            // A letter owl that somebody tamed on the way stays theirs instead of flying off for good.
            clearDeparture(data);
            this.setNoGravity(false);
            this.navigation.stop();
            return;
        }
        int remainingTicks = data.getInt(DEPART_TICKS);
        if (remainingTicks <= 0) {
            clearDeparture(data);
            if (errand) {
                vanishIntoFlight();
            } else {
                this.discard();
            }
            return;
        }

        BlockPos targetPos = BlockPos.of(data.getLong(DEPART_TARGET));
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        Vec3 delta = targetCenter.subtract(this.position());
        // Leaving the area where entities tick would freeze it mid-air, parcel and all (a force-loaded post
        // station, a player at the edge of simulation distance): it is out of sight already, so go now.
        Vec3 ahead = this.position().add(this.getDeltaMovement().scale(20.0D)).add(delta.normalize().scale(2.0D));
        if (this.level() instanceof ServerLevel serverLevel
                && (!serverLevel.isPositionEntityTicking(BlockPos.containing(ahead))
                || !serverLevel.isPositionEntityTicking(this.blockPosition()))) {
            clearDeparture(data);
            if (errand) {
                vanishIntoFlight();
            } else {
                this.discard();
            }
            return;
        }
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
                    this.getDeltaMovement().z * 0.8D + nudge.z);
            this.hasImpulse = true;
        }
        data.putInt(DEPART_TICKS, remainingTicks - 1);
    }

    private void clearDeliveryData(CompoundTag data) {
        data.remove(MAIL_STACK);
        data.remove(MAIL_REPATH_TICKS);
        data.remove(MAIL_TARGET_PLAYER);
        data.remove(MAIL_FLIGHT_TICKS);
        this.entityData.set(DATA_CARRIED_MAIL, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ goals

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
            if (!owl.isTame() || owl.isOrderedToSit() || owl.isPassenger() || owl.isOnErrand() || owl.hasReachableHome()) {
                return false;
            }
            LivingEntity ownerEntity = owl.getOwner();
            if (!(ownerEntity instanceof Player player) || player.isSpectator()) {
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
                    && !owl.isOnErrand()
                    && !owl.hasReachableHome()
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

    /** A post owl at home: flies back to its perch and stays on it. */
    private static class OwlHomePerchGoal extends Goal {
        private final OwlEntity owl;
        private final double speedModifier;
        private int timeToRecalcPath;

        private OwlHomePerchGoal(OwlEntity owl, double speedModifier) {
            this.owl = owl;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Nullable
        private BlockPos homePerch() {
            if (!owl.isTame() || owl.isOrderedToSit() || owl.isPassenger() || owl.isOnErrand() || !owl.hasReachableHome()) {
                return null;
            }
            BlockPos pos = owl.getHome().pos();
            if (!(owl.level().getBlockEntity(pos) instanceof OwlPerchBlockEntity)) {
                owl.setHome(null); // its perch was broken
                return null;
            }
            return pos;
        }

        private static Vec3 top(BlockPos perch) {
            return Vec3.atCenterOf(perch).add(0.0D, 1.0D, 0.0D);
        }

        @Override
        public boolean canUse() {
            BlockPos perch = homePerch();
            return perch != null && !owl.isCloseEnoughToPerch(perch);
        }

        @Override
        public boolean canContinueToUse() {
            BlockPos perch = homePerch();
            return perch != null && owl.position().distanceToSqr(top(perch)) > 0.6D * 0.6D;
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            owl.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos perch = homePerch();
            if (perch == null) {
                return;
            }
            Vec3 perchTop = top(perch);
            owl.getLookControl().setLookAt(perchTop.x, perchTop.y, perchTop.z, 20.0F, (float) owl.getMaxHeadXRot());
            if (--timeToRecalcPath > 0) {
                return;
            }
            timeToRecalcPath = 10;
            owl.getNavigation().moveTo(perchTop.x, perchTop.y, perchTop.z, speedModifier);
            if (owl.horizontalCollision || perchTop.y - owl.getY() > 1.5D) {
                Vec3 lift = owl.getDeltaMovement().add(0.0D, 0.18D, 0.0D);
                owl.setDeltaMovement(lift.x, Math.min(lift.y, 0.32D), lift.z);
                owl.hasImpulse = true;
            }
        }
    }

    public boolean isCloseEnoughToPerch(BlockPos perchPos) {
        Vec3 perchCenter = Vec3.atCenterOf(perchPos).add(0.0D, 1.0D, 0.0D);
        return this.position().distanceToSqr(perchCenter) <= PERCH_INTERACTION_RANGE * PERCH_INTERACTION_RANGE;
    }

    private static class OwlIdleFlightGoal extends WaterAvoidingRandomFlyingGoal {
        private final OwlEntity owl;

        private OwlIdleFlightGoal(OwlEntity owl, double speedModifier) {
            super(owl, speedModifier);
            this.owl = owl;
        }

        @Override
        public boolean canUse() {
            return !owl.hasReachableHome() && !owl.isOnErrand() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !owl.hasReachableHome() && !owl.isOnErrand() && super.canContinueToUse();
        }
    }
}
