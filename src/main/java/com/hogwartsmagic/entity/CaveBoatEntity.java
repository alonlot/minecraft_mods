package com.hogwartsmagic.entity;

import com.hogwartsmagic.block.entity.DespairBasinBlockEntity;
import com.hogwartsmagic.locket.CaveGeometry;
import com.hogwartsmagic.locket.CaveLayout;
import com.hogwartsmagic.locket.SkiffRules;
import com.hogwartsmagic.locket.LocketCaves;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one-seat skiff the cave raises. Old caves: server-steered along a straight line between the two docks.
 * Version-2 caves: it rises bow first, then follows a Catmull-Rom curve through the waypoints of the layout
 * (shore dock, path, island dock) and back. Mounting starts the trip; it stops at the far dock; an empty skiff
 * left mid-lake goes back to the shore, one parked at the island stays, one at the shore sinks after two minutes.
 * There is one skiff per cave. It cannot be hurt, pushed or steered.
 */
public class CaveBoatEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final EntityDataAccessor<Integer> DATA_RISE =
            SynchedEntityData.defineId(CaveBoatEntity.class, EntityDataSerializers.INT);

    private static final double SPEED = 0.12D;
    private static final double WAYPOINT_DIST = 1.0D;
    private static final float TURN_RATE = 5.0F;
    private static final double ARRIVE_DIST = 0.3D;
    private static final int EMPTY_LIFETIME = SkiffRules.EMPTY_LIFETIME;
    private static final double ISLAND_GUARD = 14.0D;
    private static final double FLOAT_OFFSET = 0.5D;
    private static final int DOCK_B_SEARCH_STEPS = 20;

    @Nullable
    private BlockPos dockA;
    @Nullable
    private BlockPos dockB;
    @Nullable
    private BlockPos target;
    private int emptyTicks;

    // Version-2 crossing: water cells shore -> island. Empty for the boat of an old cave.
    private List<BlockPos> route = new ArrayList<>();
    private List<Vec3> routeFwd = List.of();
    private List<Vec3> routeBack = List.of();
    /** Index (in {@link #route}) of the waypoint being sailed to, or -1 when docked. */
    private int next = -1;
    private int dir = 1;
    /** Which end the skiff is docked at when {@code next == -1}: 0 shore, route.size()-1 island. */
    private int dockEnd;
    private final List<InferiusEntity> latched = new ArrayList<>();

    public CaveBoatEntity(EntityType<? extends CaveBoatEntity> type, Level level) {
        super(type, level);
    }

    // ---- summoning ------------------------------------------------------------------------------

    /** Raises the boat at the water nearest the chain, unless one is already docked there. */
    public static void summonAtShore(ServerLevel level, BlockPos chainPos) {
        AABB near = new AABB(chainPos).inflate(8.0D);
        if (!level.getEntitiesOfClass(CaveBoatEntity.class, near).isEmpty()) {
            return;
        }
        BlockPos dockA = findSurfaceWater(level, chainPos, 5);
        if (dockA == null) {
            LOGGER.debug("Cave boat: no water surface within 5 blocks of chain {}", chainPos);
            chainSlack(level, chainPos);
            return;
        }
        Optional<DespairBasinBlockEntity> basin = LocketCaves.findBasinFor(level, chainPos);
        if (basin.isEmpty()) {
            LOGGER.debug("Cave boat: no basin found for chain {}", chainPos);
            chainSlack(level, chainPos);
            return;
        }
        BlockPos dockB = findIslandDock(level, basin.get().getBlockPos(), dockA);
        if (dockB == null) {
            LOGGER.debug("Cave boat: no island dock found from basin {} toward {}",
                    basin.get().getBlockPos(), dockA);
            chainSlack(level, chainPos);
            return;
        }

        CaveBoatEntity boat = new CaveBoatEntity(ModEntities.CAVE_BOAT.get(), level);
        boat.dockA = dockA;
        boat.dockB = dockB;
        boat.moveTo(dockA.getX() + 0.5D, dockA.getY() + FLOAT_OFFSET, dockA.getZ() + 0.5D,
                yawToward(dockA.getX() + 0.5D, dockA.getZ() + 0.5D, dockB.getX() + 0.5D, dockB.getZ() + 0.5D), 0.0F);
        level.addFreshEntity(boat);

        level.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SPLASH), boat.getX(), dockA.getY() + 1.0D, boat.getZ(),
                30, 0.6D, 0.2D, 0.6D, 0.1D);
        level.playSound(null, boat.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
        level.playSound(null, boat.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /**
     * Raises the boat for a version-2 cave: at the shore dock, bound for the island dock, both from the layout
     * (so it works at any template rotation). Does nothing if a boat is already within 8 blocks. Returns the boat
     * or null.
     */
    @Nullable
    public static CaveBoatEntity rise(ServerLevel level, CaveLayout layout) {
        if (layout.version() < 2) {
            return null;
        }
        List<BlockPos> route = SkiffRules.route(layout.dockShore(), layout.path(), layout.dockIsland());
        BlockPos shore = route.get(0);
        BlockPos island = route.get(route.size() - 1);
        // One skiff per cave: an empty one parked at the island is called back instead of a second being raised,
        // unless someone is standing on the island (they would be left without a way home).
        for (CaveBoatEntity existing : level.getEntities(EntityTypeTest.forClass(CaveBoatEntity.class),
                e -> e.isAlive() && shore.equals(e.shoreKey()))) {
            if (existing.getPassengers().isEmpty() && existing.isDockedAtIsland()
                    && level.getNearestPlayer(island.getX() + 0.5D, island.getY(), island.getZ() + 0.5D, ISLAND_GUARD, false) == null) {
                existing.callBack();
                return existing;
            }
            return null;
        }
        if (!level.getEntitiesOfClass(CaveBoatEntity.class, new AABB(layout.dockShore()).inflate(8.0D)).isEmpty()) {
            return null;
        }
        CaveBoatEntity boat = new CaveBoatEntity(ModEntities.CAVE_BOAT.get(), level);
        boat.setRoute(route);
        boat.dockA = shore;
        boat.dockB = island;
        Vec3 ahead = boat.routeFwd.get(1);
        boat.moveTo(shore.getX() + 0.5D, shore.getY() + FLOAT_OFFSET, shore.getZ() + 0.5D,
                yawToward(shore.getX() + 0.5D, shore.getZ() + 0.5D, ahead.x, ahead.z), 0.0F);
        boat.entityData.set(DATA_RISE, SkiffRules.RISE_TICKS);
        level.addFreshEntity(boat);
        level.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SPLASH), boat.getX(), shore.getY() + 1.0D, boat.getZ(),
                30, 0.6D, 0.2D, 0.6D, 0.1D);
        level.playSound(null, boat.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
        return boat;
    }

    private void setRoute(List<BlockPos> cells) {
        this.route = new ArrayList<>(cells);
        List<Vec3> fwd = new ArrayList<>(cells.size());
        for (BlockPos p : cells) {
            fwd.add(new Vec3(p.getX() + 0.5D, 0.0D, p.getZ() + 0.5D));
        }
        List<Vec3> back = new ArrayList<>(fwd);
        java.util.Collections.reverse(back);
        this.routeFwd = fwd;
        this.routeBack = back;
    }

    @Nullable
    private BlockPos shoreKey() {
        return this.route.isEmpty() ? null : this.route.get(0);
    }

    private boolean isV2() {
        return !this.route.isEmpty();
    }

    public boolean isDockedAtIsland() {
        return isV2() && this.next < 0 && this.dockEnd == this.route.size() - 1;
    }

    /** Sends an empty skiff back along the crossing to the shore dock. */
    private void callBack() {
        this.dir = -1;
        this.next = this.route.size() - 2;
        this.emptyTicks = 0;
    }

    public int latchedCount() {
        this.latched.removeIf(i -> !i.isAlive() || i.isRemoved());
        return this.latched.size();
    }

    /** Server side: an Inferius grabs the hull. Refused beyond {@link SkiffRules#MAX_LATCHED}. */
    public boolean latch(InferiusEntity inferius) {
        if (latchedCount() >= SkiffRules.MAX_LATCHED || this.latched.contains(inferius)) {
            return false;
        }
        this.latched.add(inferius);
        return true;
    }

    public void unlatch(InferiusEntity inferius) {
        this.latched.remove(inferius);
    }

    /** Ticks of rising left (SkiffRules.RISE_TICKS down to 0); the client draws the bow-first rise from it. */
    public int riseTicks() {
        return this.entityData.get(DATA_RISE);
    }

    private static void chainSlack(ServerLevel level, BlockPos chainPos) {
        Player nearest = level.getNearestPlayer(chainPos.getX() + 0.5D, chainPos.getY() + 0.5D,
                chainPos.getZ() + 0.5D, 6.0D, false);
        if (nearest != null) {
            nearest.displayClientMessage(Component.translatable("message.hogwartsmagic.chain_slack"), true);
        }
    }

    @Nullable
    private static BlockPos findSurfaceWater(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (isSurfaceWater(level, p)) {
                double d = p.distSqr(center);
                if (d < bestDist) {
                    bestDist = d;
                    best = p.immutable();
                }
            }
        }
        return best;
    }

    private static boolean isSurfaceWater(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir();
    }

    /** Walks from the basin toward the shore dock until it reaches surface water at the dock level. */
    @Nullable
    private static BlockPos findIslandDock(ServerLevel level, BlockPos basin, BlockPos dockA) {
        double dx = dockA.getX() - basin.getX();
        double dz = dockA.getZ() - basin.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6D) {
            return null;
        }
        dx /= len;
        dz /= len;
        for (int i = 0; i <= DOCK_B_SEARCH_STEPS; i++) {
            double dist = CaveGeometry.ISLAND_RADIUS - 2 + i;
            BlockPos p = new BlockPos((int) Math.round(basin.getX() + dx * dist), dockA.getY(),
                    (int) Math.round(basin.getZ() + dz * dist));
            if (isSurfaceWater(level, p)) {
                return p;
            }
        }
        return null;
    }

    private static float yawToward(double fromX, double fromZ, double toX, double toZ) {
        return (float) (Math.atan2(-(toX - fromX), toZ - fromZ) * (180.0D / Math.PI));
    }

    // ---- interaction ----------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        if (riseTicks() > 0) {
            return InteractionResult.FAIL;
        }
        if (!this.getPassengers().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.boat_one_rider"), true);
            return InteractionResult.FAIL;
        }
        if (player.startRiding(this)) {
            if (isV2()) {
                if (this.next < 0) {
                    boolean fromShore = this.dockEnd == 0;
                    this.dir = fromShore ? 1 : -1;
                    this.next = fromShore ? 1 : this.route.size() - 2;
                }
                return InteractionResult.CONSUME;
            }
            if (this.dockA != null && this.dockB != null) {
                Vec3 here = this.position();
                this.target = distSqrXZ(here, this.dockA) <= distSqrXZ(here, this.dockB) ? this.dockB : this.dockA;
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static double distSqrXZ(Vec3 v, BlockPos p) {
        double dx = v.x - (p.getX() + 0.5D);
        double dz = v.z - (p.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.0D, 0.3D, 0.0D);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos origin = this.blockPosition();
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3, -2, -3), origin.offset(3, 2, 3))) {
            if (!this.level().getFluidState(p).isEmpty()
                    || !this.level().getFluidState(p.below()).isEmpty()
                    || !this.level().getFluidState(p.above()).isEmpty()) {
                continue;
            }
            Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), this.level(), p, false);
            if (spot == null) {
                continue;
            }
            double d = spot.distanceToSqr(this.position());
            if (d < bestDist && DismountHelper.canDismountTo(this.level(), passenger,
                    passenger.getLocalBoundsForPose(passenger.getPose()).move(spot))) {
                bestDist = d;
                best = spot;
            }
        }
        return best != null ? best : super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ---- tick -----------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientWake();
            return;
        }
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (isV2()) {
            tickCrossing(server);
            return;
        }
        if (this.getPassengers().isEmpty()) {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.dockedAtIsland()) {
                return;
            }
            if (++this.emptyTicks >= EMPTY_LIFETIME) {
                server.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.BUBBLE), this.getX(), this.getY() + 0.4D, this.getZ(),
                        25, 0.5D, 0.2D, 0.5D, 0.05D);
                this.discard();
            }
            return;
        }
        this.emptyTicks = 0;
        if (this.target == null || this.dockA == null) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        double dx = this.target.getX() + 0.5D - this.getX();
        double dz = this.target.getZ() + 0.5D - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double y = this.dockA.getY() + FLOAT_OFFSET;
        if (dist <= ARRIVE_DIST) {
            this.target = null;
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(this.getX(), y, this.getZ());
            return;
        }
        double step = Math.min(SPEED, dist);
        double sx = dx / dist * step;
        double sz = dz / dist * step;
        this.setYRot(yawToward(0.0D, 0.0D, dx, dz));
        this.setDeltaMovement(sx, 0.0D, sz);
        this.setPos(this.getX() + sx, y, this.getZ() + sz);
    }

    // ---- version-2 crossing ---------------------------------------------------------------------

    private void tickCrossing(ServerLevel server) {
        int rise = riseTicks();
        double y = this.route.get(0).getY() + FLOAT_OFFSET;
        if (rise > 0) {
            this.entityData.set(DATA_RISE, rise - 1);
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(this.getX(), y, this.getZ());
            if (rise % 4 == 0) {
                server.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.BUBBLE), this.getX(), this.getY() + 0.3D,
                        this.getZ(), 6, 0.5D, 0.1D, 0.9D, 0.05D);
            }
            return;
        }
        boolean rider = !this.getPassengers().isEmpty();
        if (this.next >= 0 && this.dir > 0 && SkiffRules.riderGoneReturnsToShore(rider, false)) {
            this.dir = -1; // the rider is gone mid-lake: turn back for the shore along the waypoint behind
            this.next = Math.max(0, this.next - 1);
        }
        if (this.next < 0) {
            this.setDeltaMovement(Vec3.ZERO);
            if (rider) {
                this.emptyTicks = 0;
            } else if (this.dockEnd == 0 && ++this.emptyTicks >= EMPTY_LIFETIME) {
                server.sendParticles(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.BUBBLE), this.getX(), this.getY() + 0.4D, this.getZ(),
                        25, 0.5D, 0.2D, 0.5D, 0.05D);
                this.discard();
            }
            return;
        }
        this.emptyTicks = 0;
        int last = this.route.size() - 1;
        BlockPos waypoint = this.route.get(this.next);
        boolean isEnd = this.next == (this.dir > 0 ? last : 0);
        double dx = waypoint.getX() + 0.5D - this.getX();
        double dz = waypoint.getZ() + 0.5D - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= (isEnd ? ARRIVE_DIST : WAYPOINT_DIST)) {
            if (isEnd) {
                this.dockEnd = this.next;
                this.next = -1;
                this.setDeltaMovement(Vec3.ZERO);
                this.setPos(waypoint.getX() + 0.5D, y, waypoint.getZ() + 0.5D);
                return;
            }
            this.next += this.dir;
        }
        List<Vec3> pts = this.dir > 0 ? this.routeFwd : this.routeBack;
        int idx = this.dir > 0 ? this.next : last - this.next;
        Vec3 v = SkiffRules.steer(this.position(), pts, idx, SkiffRules.speedWith(latchedCount()));
        if (v.lengthSqr() > 1.0E-9D) {
            float want = yawToward(0.0D, 0.0D, v.x, v.z);
            this.setYRot(Mth.approachDegrees(this.getYRot(), want, TURN_RATE));
        }
        this.setDeltaMovement(v);
        this.setPos(this.getX() + v.x, y, this.getZ() + v.z);
        if (this.tickCount % 10 == 0) {
            double side = this.random.nextBoolean() ? 1.3D : -1.3D;
            double rad = Math.toRadians(this.getYRot());
            server.sendParticles(ParticleTypes.WHITE_ASH, this.getX() + Math.cos(rad) * side, this.getY() - 1.0D - this.random.nextDouble() * 2.0D,
                    this.getZ() + Math.sin(rad) * side, 3, 0.4D, 0.3D, 0.4D, 0.005D);
        }
    }

    /** Client only: a wake behind the stern while the hull moves. */
    private void clientWake() {
        double mx = this.getX() - this.xo;
        double mz = this.getZ() - this.zo;
        if (mx * mx + mz * mz < 0.0004D || riseTicks() > 0) {
            return;
        }
        double rad = Math.toRadians(this.getYRot());
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double wy = this.getY() + 0.4D;
        for (int i = 0; i < 2; i++) {
            double spread = (this.random.nextDouble() - 0.5D) * 0.5D;
            this.level().addParticle(ParticleTypes.SPLASH, this.getX() - fx * 1.7D - fz * spread, wy, this.getZ() - fz * 1.7D + fx * spread,
                    -fx * 0.05D, 0.05D, -fz * 0.05D);
        }
        if (this.tickCount % 3 == 0) {
            this.level().addParticle(ParticleTypes.FISHING, this.getX() + fx * 2.0D, wy, this.getZ() + fz * 2.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Parked at the island dock: the rider needs it to get home, so it never sinks there. */
    private boolean dockedAtIsland() {
        return this.target == null && this.dockB != null && distSqrXZ(this.position(), this.dockB) <= 0.25D;
    }

    // ---- data -----------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RISE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.dockA = tag.contains("DockA") ? BlockPos.of(tag.getLong("DockA")) : null;
        this.dockB = tag.contains("DockB") ? BlockPos.of(tag.getLong("DockB")) : null;
        this.target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        this.emptyTicks = tag.getInt("EmptyTicks");
        if (tag.contains("Route")) {
            List<BlockPos> cells = new ArrayList<>();
            for (long l : tag.getLongArray("Route")) {
                cells.add(BlockPos.of(l));
            }
            if (cells.size() >= 2) {
                setRoute(cells);
            }
        }
        this.next = tag.contains("Next") ? tag.getInt("Next") : -1;
        this.dir = tag.contains("Dir") ? tag.getInt("Dir") : 1;
        this.dockEnd = tag.getInt("DockEnd");
        this.entityData.set(DATA_RISE, tag.getInt("Rise"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("EmptyTicks", this.emptyTicks);
        if (isV2()) {
            tag.putLongArray("Route", this.route.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putInt("Next", this.next);
            tag.putInt("Dir", this.dir);
            tag.putInt("DockEnd", this.dockEnd);
            tag.putInt("Rise", riseTicks());
        }
        if (this.dockA != null) {
            tag.putLong("DockA", this.dockA.asLong());
        }
        if (this.dockB != null) {
            tag.putLong("DockB", this.dockB.asLong());
        }
        if (this.target != null) {
            tag.putLong("Target", this.target.asLong());
        }
    }
}
