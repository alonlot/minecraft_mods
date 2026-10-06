package com.hogwartsmagic.entity;

import com.hogwartsmagic.block.entity.DespairBasinBlockEntity;
import com.hogwartsmagic.locket.CaveGeometry;
import com.hogwartsmagic.locket.LocketCaves;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
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
import java.util.Optional;

/**
 * The one-seat boat the Hidden Chain raises. It is server-steered along a straight line between the
 * shore dock and the island dock: mounting starts the trip, and it stops at the far dock. It cannot
 * be hurt, pushed or steered, and it sinks away after two minutes empty.
 */
public class CaveBoatEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final double SPEED = 0.12D;
    private static final double ARRIVE_DIST = 0.3D;
    private static final int EMPTY_LIFETIME = 2400;
    private static final double FLOAT_OFFSET = 0.5D;
    private static final int DOCK_B_SEARCH_STEPS = 20;

    @Nullable
    private BlockPos dockA;
    @Nullable
    private BlockPos dockB;
    @Nullable
    private BlockPos target;
    private int emptyTicks;

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

        level.sendParticles(ParticleTypes.SPLASH, boat.getX(), dockA.getY() + 1.0D, boat.getZ(),
                30, 0.6D, 0.2D, 0.6D, 0.1D);
        level.playSound(null, boat.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
        level.playSound(null, boat.blockPosition(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.NEUTRAL, 1.0F, 1.0F);
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
        if (!this.getPassengers().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.boat_one_rider"), true);
            return InteractionResult.FAIL;
        }
        if (player.startRiding(this)) {
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
        return new Vec3(0.0D, 0.2D, 0.0D);
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
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        if (this.getPassengers().isEmpty()) {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.dockedAtIsland()) {
                return;
            }
            if (++this.emptyTicks >= EMPTY_LIFETIME) {
                server.sendParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.4D, this.getZ(),
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

    /** Parked at the island dock: the rider needs it to get home, so it never sinks there. */
    private boolean dockedAtIsland() {
        return this.target == null && this.dockB != null && distSqrXZ(this.position(), this.dockB) <= 0.25D;
    }

    // ---- data -----------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.dockA = tag.contains("DockA") ? BlockPos.of(tag.getLong("DockA")) : null;
        this.dockB = tag.contains("DockB") ? BlockPos.of(tag.getLong("DockB")) : null;
        this.target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        this.emptyTicks = tag.getInt("EmptyTicks");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("EmptyTicks", this.emptyTicks);
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
