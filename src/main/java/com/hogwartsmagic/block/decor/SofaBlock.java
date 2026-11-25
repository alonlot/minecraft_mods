package com.hogwartsmagic.block.decor;

import com.hogwartsmagic.cosy.FurnitureRules;
import com.hogwartsmagic.cosy.SofaPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * A sofa that joins its neighbours like stairs do. Sofas facing the same way side by side form one long sofa:
 * only the two ends have arms. Left and right are the view of the person sitting, facing the way the sofa faces.
 */
public class SofaBlock extends SeatBlock {
    public static final EnumProperty<SofaPart> PART = EnumProperty.create("part", SofaPart.class);

    private static final VoxelShape SEAT = DecorShapes.boxes(
            new double[]{0, 0, 1, 16, 9, 15}, new double[]{0, 9, 11, 16, 18, 15});
    private static final VoxelShape LEFT_ARM = DecorShapes.boxes(new double[]{0, 9, 1, 3, 13, 11});
    private static final VoxelShape RIGHT_ARM = DecorShapes.boxes(new double[]{13, 9, 1, 16, 13, 11});

    /** [facing][part] shapes, written for north and turned. */
    private final Map<Direction, Map<SofaPart, VoxelShape>> shapes = new EnumMap<>(Direction.class);

    public SofaBlock(BlockBehaviour.Properties properties) {
        super(properties, SEAT, 9.0 / 16.0);
        for (SofaPart part : SofaPart.values()) {
            VoxelShape north = SEAT;
            if (part.hasLeftArm()) {
                north = Shapes.or(north, LEFT_ARM);
            }
            if (part.hasRightArm()) {
                north = Shapes.or(north, RIGHT_ARM);
            }
            Map<Direction, VoxelShape> turned = DecorShapes.allHorizontal(north);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                shapes.computeIfAbsent(facing, f -> new EnumMap<>(SofaPart.class)).put(part, turned.get(facing));
            }
        }
        registerDefaultState(defaultBlockState().setValue(PART, SofaPart.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING)).get(state.getValue(PART));
    }

    /** The part this sofa should be, from the sofas that face the same way on its left and right. */
    public SofaPart partAt(BlockGetter level, BlockPos pos, Direction facing) {
        boolean left = joins(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing);
        boolean right = joins(level.getBlockState(pos.relative(facing.getClockWise())), facing);
        return FurnitureRules.sofaPart(left, right);
    }

    private boolean joins(BlockState neighbour, Direction facing) {
        return neighbour.is(this) && neighbour.getValue(FACING) == facing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(PART, partAt(context.getLevel(), context.getClickedPos(), state.getValue(FACING)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PART, partAt(level, pos, state.getValue(FACING)));
    }

    /** A sofa set down any other way (commands, structures) still works out its own arms. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        SofaPart part = partAt(level, pos, state.getValue(FACING));
        if (state.getValue(PART) != part) {
            BlockState fresh = state.setValue(PART, part);
            level.setBlock(pos, fresh, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            // Changing the state inside onPlace makes the caller skip its neighbour updates, so do them here.
            fresh.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }
}
