package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tags a block as part of the Locket cave hidden dressing (Concealed Rock, the hidden chain) so
 * {@code LocketCaves.reveal} can find them by walking loaded chunk block entities instead of
 * scanning a volume of blocks. Holds no data and never ticks.
 */
public class CaveMarkerBlockEntity extends BlockEntity {
    public CaveMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.CAVE_MARKER_BLOCK_ENTITY.get(), pos, state);
    }
}
