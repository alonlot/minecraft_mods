package com.hogwartsmagic.block.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * A table that joins with the same table next to it into one long top, like the house tables in the
 * Great Hall. A leg shows only at a corner where neither side joins another table.
 */
public class TableBlock extends Block {
    public static final MapCodec<TableBlock> CODEC = simpleCodec(TableBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape TOP = Block.box(0, 13, 0, 16, 16, 16);
    private static final VoxelShape LEG_NW = Block.box(1, 0, 1, 4, 13, 4);
    private static final VoxelShape LEG_NE = Block.box(12, 0, 1, 15, 13, 4);
    private static final VoxelShape LEG_SE = Block.box(12, 0, 12, 15, 13, 15);
    private static final VoxelShape LEG_SW = Block.box(1, 0, 12, 4, 13, 15);

    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public TableBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, buildShape(state));
        }
    }

    private static VoxelShape buildShape(BlockState s) {
        VoxelShape shape = TOP;
        boolean n = s.getValue(NORTH), e = s.getValue(EAST), so = s.getValue(SOUTH), w = s.getValue(WEST);
        if (!n && !w) shape = Shapes.or(shape, LEG_NW);
        if (!n && !e) shape = Shapes.or(shape, LEG_NE);
        if (!so && !e) shape = Shapes.or(shape, LEG_SE);
        if (!so && !w) shape = Shapes.or(shape, LEG_SW);
        return shape.optimize();
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private static BooleanProperty property(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = context.getClickedPos().relative(d);
            state = state.setValue(property(d), context.getLevel().getBlockState(n).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(property(direction), neighborState.is(this));
        }
        return state;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(property(rotation.rotate(d)), state.getValue(property(d)));
        }
        return out;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            out = out.setValue(property(mirror.mirror(d)), state.getValue(property(d)));
        }
        return out;
    }
}
