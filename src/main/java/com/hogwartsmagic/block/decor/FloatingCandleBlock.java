package com.hogwartsmagic.block.decor;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A candle that floats in mid-air, as in the Great Hall. It needs no support, has no collision and
 * gives full light. Three candles of different heights hover in one cell.
 */
public class FloatingCandleBlock extends Block {
    public static final MapCodec<FloatingCandleBlock> CODEC = simpleCodec(FloatingCandleBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.0, 1.0, 3.0, 13.0, 14.0, 13.0);

    /** Wick tips in pixels, matching the model: {x, y, z}. */
    private static final double[][] WICKS = {{5.0, 14.0, 6.0}, {10.0, 12.0, 5.0}, {8.0, 11.0, 11.0}};

    public FloatingCandleBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().noCollission().lightLevel(state -> 14));
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

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (double[] w : WICKS) {
            double x = pos.getX() + w[0] / 16.0;
            double y = pos.getY() + w[1] / 16.0 + 0.03;
            double z = pos.getZ() + w[2] / 16.0;
            // animateTick reaches a given block only now and then, so every call lights every wick,
            // the same as vanilla candles.
            level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SMALL_FLAME), x, y, z, 0.0, 0.0, 0.0);
            if (random.nextFloat() < 0.15f) {
                level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SMOKE), x, y + 0.05, z, 0.0, 0.0, 0.0);
            }
        }
    }
}
