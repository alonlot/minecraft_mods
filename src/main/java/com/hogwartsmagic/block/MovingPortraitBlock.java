package com.hogwartsmagic.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MovingPortraitBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MovingPortraitBlock> CODEC = simpleCodec(MovingPortraitBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SHAPE = Block.box(1.0, 1.0, 14.0, 15.0, 15.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 2.0);
    private static final VoxelShape WEST_SHAPE = Block.box(14.0, 1.0, 1.0, 16.0, 15.0, 15.0);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 1.0, 1.0, 2.0, 15.0, 15.0);

    public MovingPortraitBlock(BlockBehaviour.Properties properties) {
        super(properties.sound(SoundType.WOOD).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(20) == 0) {
            level.addParticle(ParticleTypes.ENCHANT,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.6 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.01, 0.0);
        }
    }
}
