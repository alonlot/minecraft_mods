package com.hogwartsmagic.entity;

import com.hogwartsmagic.block.decor.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An invisible mount that lets a player sit on a chair, bench or armchair. It sits at the seat
 * surface, removes itself when its rider leaves or the seat block is gone, and is never saved.
 */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SeatEntity create(Level level, BlockPos pos, double seatHeight) {
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.setPos(pos.getX() + 0.5, pos.getY() + seatHeight, pos.getZ() + 0.5);
        return seat;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof SeatBlock)) {
            ejectPassengers();
            discard();
        }
    }

    /** The rider sits with its hips on the seat surface, which is where this entity stands. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        if (state.getBlock() instanceof SeatBlock && state.hasProperty(SeatBlock.FACING)) {
            // Stand up toward the front of the seat first, then the sides, then the back.
            Direction front = state.getValue(SeatBlock.FACING);
            Direction[] order = {front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()};
            for (Direction d : order) {
                Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos.relative(d), true);
                if (spot != null) {
                    return spot;
                }
            }
        }
        Vec3 above = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos.above(), true);
        return above != null ? above : super.getDismountLocationForPassenger(passenger);
    }
}
