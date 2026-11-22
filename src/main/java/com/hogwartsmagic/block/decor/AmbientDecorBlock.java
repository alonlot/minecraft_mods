package com.hogwartsmagic.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A free-standing decoration with a client-side ambient effect (particles) each animate tick. */
public class AmbientDecorBlock extends DecorBlock {
    @FunctionalInterface
    public interface Ambient {
        void tick(BlockState state, Level level, BlockPos pos, RandomSource random);
    }

    private final Ambient ambient;

    public AmbientDecorBlock(BlockBehaviour.Properties properties, VoxelShape northShape, Ambient ambient) {
        super(properties, northShape);
        this.ambient = ambient;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        ambient.tick(state, level, pos, random);
    }
}
