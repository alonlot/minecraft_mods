package com.hogwartsmagic.cosy;

import com.hogwartsmagic.block.decor.ModDecorations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Holds nothing: it exists so the client has a renderer to draw the three candles bobbing. The lit state lives
 * in the block state, so it needs no saving and no sync.
 */
public class FloatingCandleBlockEntity extends BlockEntity {
    public FloatingCandleBlockEntity(BlockPos pos, BlockState state) {
        super(ModDecorations.FLOATING_CANDLE_BLOCK_ENTITY.get(), pos, state);
    }

    /** The candles stay inside their own cell, so the default infinite box (empty collision shape) is not needed. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition);
    }
}
