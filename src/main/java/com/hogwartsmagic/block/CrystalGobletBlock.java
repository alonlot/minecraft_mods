package com.hogwartsmagic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small decorative goblet that sits on the Basin of Despair's island. */
public class CrystalGobletBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 1, 11),
            Block.box(7.5, 1, 7.5, 8.5, 5, 8.5),
            Block.box(5, 5, 5, 11, 10, 11));

    public CrystalGobletBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
