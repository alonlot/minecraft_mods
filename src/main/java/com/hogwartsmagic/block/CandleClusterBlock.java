package com.hogwartsmagic.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CandleClusterBlock extends Block {
    public static final MapCodec<CandleClusterBlock> CODEC = simpleCodec(CandleClusterBlock::new);
    private static final VoxelShape SHAPE = Block.box(2.5, 0.0, 2.5, 13.5, 12.0, 13.5);

    public CandleClusterBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> 13).noOcclusion());
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Wick tips in pixels {x, y, z}, matching CLUSTER_CANDLES in build_scripts/generate_decorations.py. */
    private static final double[][] WICKS = {
            {5.25, 11.0, 6.25}, {9.0, 9.0, 4.5}, {11.25, 12.0, 8.25}, {6.5, 8.0, 10.5}, {10.0, 6.5, 11.5}};

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (double[] w : WICKS) {
            double x = pos.getX() + w[0] / 16.0;
            double y = pos.getY() + w[1] / 16.0 + 0.03;
            double z = pos.getZ() + w[2] / 16.0;
            level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.0, 0.0);
            if (random.nextFloat() < 0.1f) {
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.05, z, 0.0, 0.0, 0.0);
            }
        }
    }
}
