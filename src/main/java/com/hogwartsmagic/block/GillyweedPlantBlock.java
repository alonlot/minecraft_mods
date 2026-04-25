package com.hogwartsmagic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Underwater plant for swamp worldgen; behaves like seagrass (replaces water, survives on valid floor).
 */
public class GillyweedPlantBlock extends SeagrassBlock {
    public GillyweedPlantBlock(Properties properties) {
        super(properties);
    }

    /**
     * Vanilla seagrass only allows a small set of substrates. Swamps (especially mangrove) use mud and
     * roots that can fail the base check even where underwater placement is valid.
     */
    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        if (super.mayPlaceOn(floor, level, pos)) {
            return true;
        }
        return floor.is(Blocks.MUD)
                || floor.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || floor.is(Blocks.MANGROVE_ROOTS)
                || floor.is(Blocks.ROOTED_DIRT)
                || floor.is(Blocks.MOSS_BLOCK);
    }
}
