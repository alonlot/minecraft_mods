package com.hogwartsmagic.block.decor;

import com.hogwartsmagic.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Furniture a player sits on with a right click. Sneak and right click to place a block against it instead. */
public class SeatBlock extends DecorBlock {
    private final double seatHeight;

    /** @param seatHeight seat surface above the block bottom, in blocks */
    public SeatBlock(BlockBehaviour.Properties properties, VoxelShape northShape, double seatHeight) {
        super(properties, northShape);
        this.seatHeight = seatHeight;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isPassenger() || player.isSpectator() || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty()) {
            return InteractionResult.CONSUME; // someone already sits here
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 9.0) {
            return InteractionResult.PASS;
        }
        SeatEntity seat = SeatEntity.create(level, pos, seatHeight);
        level.addFreshEntity(seat);
        if (!player.startRiding(seat)) {
            seat.discard();
        }
        return InteractionResult.CONSUME;
    }
}
