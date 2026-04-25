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

public class WizardCauldronBlock extends Block {
    public static final MapCodec<WizardCauldronBlock> CODEC = simpleCodec(WizardCauldronBlock::new);
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0);

    public WizardCauldronBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> 7).noOcclusion());
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
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.WITCH,
                    pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.8 + random.nextDouble() * 0.2,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0.0, 0.02, 0.0);
        }
    }
}
