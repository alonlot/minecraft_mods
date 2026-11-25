package com.hogwartsmagic.block.decor;

import com.hogwartsmagic.cosy.FurnitureRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rug: a one-pixel carpet whose border runs only along the edges with no matching rug beside it, so a block of
 * the same rugs reads as one big rug. The four properties say "this side has a border".
 */
public class RugBlock extends Block {
    public static final MapCodec<RugBlock> CODEC = simpleCodec(RugBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    public RugBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(NORTH, true).setValue(EAST, true)
                .setValue(SOUTH, true).setValue(WEST, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.isEmptyBlock(pos.below());
    }

    /** The state with its borders worked out from the rugs around {@code pos}. */
    public BlockState withBorders(BlockState state, BlockGetter level, BlockPos pos) {
        int mask = FurnitureRules.rugBorderMask(
                level.getBlockState(pos.north()).is(this), level.getBlockState(pos.east()).is(this),
                level.getBlockState(pos.south()).is(this), level.getBlockState(pos.west()).is(this));
        return state.setValue(NORTH, (mask & FurnitureRules.BORDER_NORTH) != 0)
                .setValue(EAST, (mask & FurnitureRules.BORDER_EAST) != 0)
                .setValue(SOUTH, (mask & FurnitureRules.BORDER_SOUTH) != 0)
                .setValue(WEST, (mask & FurnitureRules.BORDER_WEST) != 0);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withBorders(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return withBorders(state, level, pos);
    }

    /** A rug set down any other way (commands, structures) still works out its own borders. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        BlockState fresh = withBorders(state, level, pos);
        if (fresh != state) {
            level.setBlock(pos, fresh, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            // Changing the state inside onPlace makes the caller skip its neighbour updates, so do them here.
            fresh.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }
}
