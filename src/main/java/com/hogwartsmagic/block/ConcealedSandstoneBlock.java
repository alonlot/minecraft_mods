package com.hogwartsmagic.block;

import com.hogwartsmagic.block.entity.TombMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A tomb wall that looks like sandstone until Revelio is cast close to it, then opens for a minute
 * exactly as Concealed Rock does in the Locket cave. Only the marker differs: this one answers to
 * the short tomb radius in {@code TombCurses.reveal}, not to the cave's.
 */
public class ConcealedSandstoneBlock extends ConcealedRockBlock {
    public ConcealedSandstoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TombMarkerBlockEntity(pos, state);
    }
}
