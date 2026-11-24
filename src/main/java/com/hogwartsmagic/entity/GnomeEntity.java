package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.item.HeldGnomeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * A garden gnome: half a block of knobbly, bald, leathery mischief. In daylight small gangs of them wander the
 * meadows and farms, sneak up on ripe crops and yank them out of the ground to eat, scratch up the lawn, and
 * giggle about it. They bolt when you come near, unless you creep.
 *
 * <p>Click to grab it by the ankle; keep holding use to whirl it round; let go to fling it. Hold it without
 * whirling and it bites you.
 *
 * <p>A gnome flung twelve blocks or more lands dazed and then goes for good, once nobody is watching. The throw
 * itself is the pure sums in {@link GnomeRules}.
 */
public class GnomeEntity extends PathfinderMob {
    /** What the gnome is busy with. Timed states end by themselves; every state but NONE roots it. */
    public enum State {
        NONE(0), PULL(20), EAT(30), DIG(40), CARRIED(0), SPUN(0), THROWN(0), DAZED(60);

        public final int ticks;

        State(int ticks) {
            this.ticks = ticks;
        }
    }

    public static final TagKey<net.minecraft.world.item.Item> GNOME_FOOD =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gnome_food"));

    public static final int DAZE_TICKS = State.DAZED.ticks;
    /** A gnome that is leaving gives up being seen after this long, in ticks, and simply goes. */
    public static final int LEAVE_MAX_TICKS = 400;
    /** How far a player can see a gnome leave. */
    public static final double SIGHT_RANGE = 64.0D;
    private static final int CROP_RANGE = 10;
    private static final int FLIGHT_CAP_TICKS = 400;

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(GnomeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_LEAVING =
            SynchedEntityData.defineId(GnomeEntity.class, EntityDataSerializers.BOOLEAN);

    private int stateTicks;
    private int uprootCooldown;
    private int digCooldown = 1200;
    private int seekCooldown = 200;
    private int eatCountdown;
    private int cropsEaten;
    @Nullable
    private BlockPos cropTarget;
    @Nullable
    private BlockPos digTarget;

    private int carriedTicks;
    private boolean everSpun;
    private int spinTicks;
    private int thrownTicks;
    private Vec3 throwOrigin = Vec3.ZERO;
    private double lastThrowDistance;
    private int leaveTicks;
    private boolean loadedLeaving;

    /** Client only: when the current state began, in the entity's ticks. */
    private int stateStart;

    public GnomeEntity(EntityType<? extends GnomeEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    /**
     * By day, on grass, in the light, and preferably near a field or a village path; elsewhere only one try in
     * three succeeds. (No village lookup: it reads POI data, which is a worldgen deadlock risk.)
     */
    public static boolean canSpawn(EntityType<GnomeEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                                   BlockPos pos, RandomSource random) {
        if (spawnType == MobSpawnType.SPAWNER) {
            return true;
        }
        if (!level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)
                || !level.getLevel().isDay()
                || level.getRawBrightness(pos, 0) <= 8
                || !checkMobSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -3, -8), pos.offset(8, 3, 8))) {
            if (!level.hasChunkAt(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)) {
                return true;
            }
        }
        return random.nextInt(3) == 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_LEAVING, Boolean.FALSE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new BusyGoal());
        this.goalSelector.addGoal(2, new LeaveGoal());
        this.goalSelector.addGoal(3, new AvoidEntityGoal<Player>(this, Player.class, 7.0F, 1.0D, 1.35D,
                p -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && !p.isDiscrete()) {
            @Override
            public void start() {
                super.start();
                if (GnomeEntity.this.random.nextInt(3) == 0) {
                    GnomeEntity.this.playSound(ModSounds.GNOME_SQUEAK.get(), 1.0F, getVoicePitch());
                }
            }
        });
        this.goalSelector.addGoal(4, new UprootCropGoal());
        this.goalSelector.addGoal(5, new DigHoleGoal());
        this.goalSelector.addGoal(6, new SeekFarmGoal());
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    // --- state ---------------------------------------------------------------------------------------------

    public State state() {
        State[] all = State.values();
        int i = this.entityData.get(DATA_STATE);
        return i >= 0 && i < all.length ? all[i] : State.NONE;
    }

    public void setState(State s) {
        this.entityData.set(DATA_STATE, (byte) s.ordinal());
        this.stateTicks = 0;
        if (s != State.NONE) {
            this.getNavigation().stop();
        }
    }

    /** Client: ticks since the current state began. */
    public float stateTime(float partialTick) {
        return this.tickCount - this.stateStart + partialTick;
    }

    /** Client: how long it has been whirled, for the model's swing. */
    public float clientSpinTicks(float partialTick) {
        return this.spinTicks + partialTick;
    }

    public boolean isLeaving() {
        return this.entityData.get(DATA_LEAVING);
    }

    public double lastThrowDistance() {
        return lastThrowDistance;
    }

    public int carriedTicks() {
        return carriedTicks;
    }

    public int cropsEaten() {
        return cropsEaten;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key)) {
            this.stateStart = this.tickCount;
        }
    }

    private boolean mobGriefing() {
        return this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    // --- the garden --------------------------------------------------------------------------------------------

    /** The nearest ripe crop within ten blocks, or null (always null when mob griefing is off). */
    @Nullable
    public BlockPos findRipeCrop() {
        if (!mobGriefing()) {
            return null;
        }
        BlockPos here = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(here.offset(-CROP_RANGE, -2, -CROP_RANGE),
                here.offset(CROP_RANGE, 2, CROP_RANGE))) {
            if (!level().hasChunkAt(p)) {
                continue;
            }
            BlockState s = level().getBlockState(p);
            if (s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s)) {
                double d = p.distSqr(here);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    private boolean ripeAt(BlockPos p) {
        BlockState s = level().getBlockState(p);
        return s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s);
    }

    /** Scratches the ground at {@code ground}; see {@link GnomeRules#digOutcome}. Returns the outcome. */
    public int digAt(BlockPos ground, float roll) {
        if (!mobGriefing()) {
            return GnomeRules.DIG_NONE;
        }
        BlockState s = level().getBlockState(ground);
        boolean grassOrDirt = s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT);
        boolean coarse = s.is(Blocks.COARSE_DIRT);
        BlockPos below = ground.below();
        boolean sturdy = level().getBlockState(below).isFaceSturdy(level(), below, Direction.UP);
        int outcome = GnomeRules.digOutcome(grassOrDirt, coarse, sturdy, roll);
        if (outcome == GnomeRules.DIG_COARSE) {
            level().setBlock(ground, Blocks.COARSE_DIRT.defaultBlockState(), 3);
        } else if (outcome == GnomeRules.DIG_PIT) {
            level().setBlock(ground, Blocks.AIR.defaultBlockState(), 3);
        }
        return outcome;
    }

    /** The pull is done: yank the crop if it is still ripe and let the drop fall. */
    private void finishPull() {
        BlockPos p = cropTarget;
        if (p != null && mobGriefing() && ripeAt(p)) {
            level().destroyBlock(p, true, this);
            eatCountdown = 2;
        } else {
            cropTarget = null;
        }
    }

    private void finishDig() {
        BlockPos p = digTarget;
        digTarget = null;
        if (p != null && onGround()) {
            digAt(p, this.random.nextFloat());
        }
    }

    private void eatDroppedCrop() {
        Vec3 at = cropTarget != null ? Vec3.atCenterOf(cropTarget) : position();
        cropTarget = null;
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(at, at).inflate(2.0D), e -> e.getItem().is(GNOME_FOOD));
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            this.take(item, 1);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)),
                        getX(), getY() + 0.3D, getZ(), 8, 0.1D, 0.1D, 0.1D, 0.05D);
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                item.discard();
            }
            setState(State.EAT);
            cropsEaten++;
            playSound(ModSounds.GNOME_GIGGLE.get(), 1.0F, getVoicePitch());
            uprootCooldown = 200 + random.nextInt(201);
            return;
        }
        uprootCooldown = 100;
    }

    // --- ticking ----------------------------------------------------------------------------------------------

    @Override
    public void aiStep() {
        super.aiStep();
        State s = state();
        if (level().isClientSide) {
            if (s == State.DAZED && this.tickCount % 4 == 0) {
                double a = this.tickCount * 0.5D;
                level().addParticle(ParticleTypes.ENCHANTED_HIT, getX() + Math.cos(a) * 0.2D, getY() + 0.55D,
                        getZ() + Math.sin(a) * 0.2D, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        if ((s == State.CARRIED || s == State.SPUN) && !(getVehicle() instanceof Player)) {
            // Its carrier logged out, died away or was moved: the gnome is simply let go.
            setState(State.NONE);
            s = State.NONE;
        }
        if (loadedLeaving) {
            discard();
            return;
        }
        if (uprootCooldown > 0) uprootCooldown--;
        if (digCooldown > 0) digCooldown--;
        if (seekCooldown > 0) seekCooldown--;
        if (eatCountdown > 0 && --eatCountdown == 0) {
            eatDroppedCrop();
        }
        if (s.ticks > 0 && ++stateTicks >= s.ticks) {
            setState(State.NONE);
            if (s == State.PULL) {
                finishPull();
            } else if (s == State.DIG) {
                finishDig();
            }
            s = state();
        }
        if (isLeaving() && s == State.NONE && this.tickCount % 10 == 0) {
            leaveTicks += 10;
            if (leaveTicks >= LEAVE_MAX_TICKS || !watched()) {
                discard();
            }
        }
    }

    /** True when some player has a clear line to the gnome and it is within their forward sixty degrees. */
    private boolean watched() {
        for (Player p : level().players()) {
            if (p.isSpectator() || p.distanceToSqr(this) > SIGHT_RANGE * SIGHT_RANGE || !p.hasLineOfSight(this)) {
                continue;
            }
            Vec3 look = p.getLookAngle();
            Vec3 to = getEyePosition().subtract(p.getEyePosition());
            if (GnomeRules.inView(look.x, look.y, look.z, to.x, to.y, to.z)) {
                return true;
            }
        }
        return false;
    }

    // --- being carried, whirled and flung ---------------------------------------------------------------------

    private boolean grabbableState() {
        State s = state();
        return s == State.NONE || s == State.PULL || s == State.EAT || s == State.DIG;
    }

    /** Whether a click by this player could grab it (the client uses this to predict). */
    public boolean canBeGrabbedBy(Player p) {
        return p.getMainHandItem().isEmpty() && grabbableState() && !isLeaving() && !isPassenger()
                && p.getPassengers().isEmpty() && !p.isSpectator();
    }

    /** Takes the gnome by the ankle. Refused if the player already carries one or another player has this one. */
    public boolean grab(Player p) {
        if (level().isClientSide || !canBeGrabbedBy(p)) {
            return false;
        }
        if (!startRiding(p, true)) {
            return false;
        }
        cropTarget = null;
        digTarget = null;
        eatCountdown = 0;
        setState(State.CARRIED);
        carriedTicks = 0;
        everSpun = false;
        spinTicks = 0;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ExampleMod.HELD_GNOME.get()));
        playSound(ModSounds.GNOME_SQUEAK.get(), 1.0F, getVoicePitch());
        return true;
    }

    /** The carrier is still holding use: the gnome is being whirled. */
    public void spinTick() {
        if (state() != State.SPUN) {
            setState(State.SPUN);
        }
        everSpun = true;
    }

    /** Lets go: it leaves the hand with a speed and an angle set by how long it was whirled and where you look. */
    public void throwFrom(Player p, int spinTicksHeld) {
        stopRiding();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-6D ? Vec3.directionFromRotation(0.0F, p.getYRot()) : flat.normalize();
        setPos(p.getX() + flat.x * GnomeRules.HAND_FORWARD, p.getY() + GnomeRules.THROW_HEIGHT,
                p.getZ() + flat.z * GnomeRules.HAND_FORWARD);
        double e = GnomeRules.launchElevationRadians(p.getXRot());
        double speed = GnomeRules.throwSpeed(spinTicksHeld);
        setDeltaMovement(Math.cos(e) * speed * flat.x, Math.sin(e) * speed, Math.cos(e) * speed * flat.z);
        float yaw = (float) (Mth.atan2(flat.z, flat.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setState(State.THROWN);
        thrownTicks = 0;
        throwOrigin = position();
        this.hurtMarked = true;
        level().playSound(null, this, ModSounds.GNOME_WAIL.get(), getSoundSource(), 1.0F, getVoicePitch());
        HeldGnomeItem.removeFrom(p);
    }

    /** It has come down: dazed, and if it was flung far enough, it will leave for good. */
    public void landAfterThrow(double distance) {
        setState(State.DAZED);
        lastThrowDistance = distance;
        this.entityData.set(DATA_LEAVING, GnomeRules.goneForGood(distance));
        leaveTicks = 0;
        setDeltaMovement(0.0D, Math.min(0.0D, getDeltaMovement().y), 0.0D);
        playSound(SoundEvents.SLIME_BLOCK_FALL, 0.8F, 1.2F);
        playSound(ModSounds.GNOME_SQUEAK.get(), 1.0F, getVoicePitch());
    }

    /** Twists out of the hand. With {@code bite} it takes a bite of its carrier on the way. */
    public void wriggleFree(boolean bite) {
        Player carrier = getVehicle() instanceof Player p ? p : null;
        stopRiding();
        if (carrier != null) {
            if (bite) {
                carrier.hurt(damageSources().mobAttack(this), 2.0F);
                playSound(ModSounds.GNOME_BITE.get(), 1.0F, getVoicePitch());
            }
            Vec3 fwd = Vec3.directionFromRotation(0.0F, carrier.getYRot());
            setPos(carrier.getX() + fwd.x * GnomeRules.HAND_FORWARD, carrier.getY(),
                    carrier.getZ() + fwd.z * GnomeRules.HAND_FORWARD);
            HeldGnomeItem.removeFrom(carrier);
        }
        setDeltaMovement(0.0D, 0.3D, 0.0D);
        this.hurtMarked = true;
        setState(State.NONE);
        carriedTicks = 0;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide) {
            return canBeGrabbedBy(player) ? InteractionResult.sidedSuccess(true) : InteractionResult.PASS;
        }
        return grab(player) ? InteractionResult.sidedSuccess(false) : InteractionResult.PASS;
    }

    /** Hangs at the hand, or swings on the whirl orbit, on both sides so the client draws it smoothly. */
    @Override
    public void rideTick() {
        super.rideTick();
        if (!(getVehicle() instanceof Player carrier)) {
            return;
        }
        State s = state();
        if (s == State.SPUN) {
            spinTicks++;
        } else {
            spinTicks = 0;
        }
        float yaw = carrier.getYHeadRot();
        double[] o = s == State.SPUN ? GnomeRules.spinOffset(yaw, spinTicks) : GnomeRules.handOffset(yaw);
        setPos(carrier.getX() + o[0], carrier.getY() + o[1], carrier.getZ() + o[2]);
        float face = s == State.SPUN ? (float) (Mth.atan2(o[2], o[0]) * Mth.RAD_TO_DEG) - 90.0F : yaw;
        setYRot(face);
        setYBodyRot(face);
        setYHeadRot(face);
        if (level().isClientSide) {
            return;
        }
        carriedTicks++;
        if (!carrier.isAlive() || !carrier.getMainHandItem().is(ExampleMod.HELD_GNOME.get())) {
            wriggleFree(false);
        } else if (GnomeRules.biteDue(carriedTicks, everSpun)) {
            wriggleFree(true);
        }
    }

    /** The thrown flight steps exactly as {@link GnomeRules#flightDistance} does. */
    @Override
    public void travel(Vec3 input) {
        if (state() != State.THROWN || level().isClientSide) {
            super.travel(input);
            return;
        }
        Vec3 dm = getDeltaMovement();
        move(MoverType.SELF, dm);
        setDeltaMovement(dm.x * GnomeRules.DRAG, (dm.y - GnomeRules.GRAVITY) * GnomeRules.DRAG,
                dm.z * GnomeRules.DRAG);
        resetFallDistance();
        thrownTicks++;
        if ((thrownTicks >= 2 && (onGround() || horizontalCollision || isInWater()))
                || thrownTicks >= FLIGHT_CAP_TICKS) {
            double dx = getX() - throwOrigin.x;
            double dz = getZ() - throwOrigin.z;
            landAfterThrow(Math.sqrt(dx * dx + dz * dz));
        }
    }

    /** A gnome has been dropped on its head all its life: no fall damage, so a flung one is never hurt by landing. */
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** Whoever holds it, the carrier's crosshair and attacks pass through it. */
    @Override
    public boolean isPickable() {
        return !isPassenger() && super.isPickable();
    }

    // --- saving --------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Leaving", isLeaving());
        tag.putInt("UprootCooldown", uprootCooldown);
        tag.putInt("DigCooldown", digCooldown);
        tag.putInt("SeekCooldown", seekCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        boolean leaving = tag.getBoolean("Leaving");
        this.entityData.set(DATA_LEAVING, leaving);
        loadedLeaving = leaving;
        uprootCooldown = tag.getInt("UprootCooldown");
        if (tag.contains("DigCooldown")) digCooldown = tag.getInt("DigCooldown");
        if (tag.contains("SeekCooldown")) seekCooldown = tag.getInt("SeekCooldown");
    }

    // --- sounds ---------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.GNOME_GIGGLE.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GNOME_SQUEAK.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GNOME_SQUEAK.get();
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.1F * (isDeadOrDying() ? 0.8F : 1.0F);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ARMADILLO_STEP, 0.1F, 1.8F);
    }

    // --- goals ----------------------------------------------------------------------------------------------------

    /** While it pulls, eats, digs, is carried, flung or dazed it does nothing else and stays where it is. */
    private final class BusyGoal extends Goal {
        BusyGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return state() != State.NONE;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            getNavigation().stop();
            BlockPos look = cropTarget != null ? cropTarget : digTarget;
            if (look != null) {
                getLookControl().setLookAt(Vec3.atCenterOf(look));
            }
        }
    }

    /** A gnome that has been flung away flees the nearest player until it is out of sight. */
    private final class LeaveGoal extends Goal {
        private int repath;

        LeaveGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return isLeaving() && state() == State.NONE;
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
            repath = 20;
            Player from = level().getNearestPlayer(GnomeEntity.this, 48.0D);
            Vec3 to = from != null
                    ? DefaultRandomPos.getPosAway(GnomeEntity.this, 16, 7, from.position())
                    : DefaultRandomPos.getPos(GnomeEntity.this, 16, 7);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, 1.4D);
            }
        }
    }

    /** To the nearest ripe crop, and out it comes. */
    private final class UprootCropGoal extends Goal {
        @Nullable
        private BlockPos target;
        private int ticks;

        UprootCropGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (state() != State.NONE || isLeaving() || uprootCooldown > 0 || isPassenger()) {
                return false;
            }
            target = findRipeCrop();
            if (target == null) {
                uprootCooldown = 40 + random.nextInt(61);
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return state() == State.NONE && target != null && ticks < 200 && ripeAt(target);
        }

        @Override
        public void start() {
            ticks = 0;
            getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.1D);
        }

        @Override
        public void tick() {
            ticks++;
            Vec3 c = Vec3.atBottomCenterOf(target);
            getLookControl().setLookAt(c);
            if (distanceToSqr(c) < 2.25D) {
                cropTarget = target;
                setState(State.PULL);
                return;
            }
            if (getNavigation().isDone() || ticks % 20 == 0) {
                getNavigation().moveTo(c.x, c.y, c.z, 1.1D);
            }
        }

        @Override
        public void stop() {
            if (ticks >= 200) {
                uprootCooldown = 100;
            }
            target = null;
        }
    }

    /** Now and then, with nobody near, it kneels and scratches up the lawn. */
    private final class DigHoleGoal extends Goal {
        DigHoleGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (state() != State.NONE || isLeaving() || digCooldown > 0 || !onGround() || !mobGriefing()) {
                return false;
            }
            if (level().getNearestPlayer(GnomeEntity.this, 6.0D) != null) {
                return false;
            }
            BlockState s = level().getBlockState(blockPosition().below());
            return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            digCooldown = 600 + random.nextInt(1201);
            digTarget = blockPosition().below();
            setState(State.DIG);
        }
    }

    /** With no ripe crop in reach, it wanders off to find a farm. */
    private final class SeekFarmGoal extends Goal {
        @Nullable
        private BlockPos farm;

        SeekFarmGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (state() != State.NONE || isLeaving() || seekCooldown > 0 || !(level() instanceof ServerLevel)) {
                return false;
            }
            seekCooldown = 400 + random.nextInt(401);
            for (int i = 0; i < 24; i++) {
                int x = blockPosition().getX() + random.nextInt(49) - 24;
                int z = blockPosition().getZ() + random.nextInt(49) - 24;
                if (!level().hasChunkAt(new BlockPos(x, 64, z))) {
                    continue;
                }
                int y = level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos p = new BlockPos(x, y, z);
                if (level().getBlockState(p).is(Blocks.FARMLAND)) {
                    farm = p;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return state() == State.NONE && farm != null && !getNavigation().isDone();
        }

        @Override
        public void start() {
            getNavigation().moveTo(farm.getX() + 0.5D, farm.getY() + 1.0D, farm.getZ() + 0.5D, 1.0D);
        }

        @Override
        public void stop() {
            farm = null;
        }
    }
}
