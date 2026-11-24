package com.hogwartsmagic.block;

import com.hogwartsmagic.block.decor.DecorShapes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * One of the eight stone cells around a Floo hearth: the fireplace is three blocks wide and three tall,
 * and the hearth ({@link FlooFireplaceBlock}, column 1 row 0) is the only cell with a block entity.
 * A part never drops anything itself; breaking it breaks the hearth, which drops the fireplace.
 * Columns run left to right as seen from the front, rows bottom to top.
 */
public class FlooFireplacePartBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FlooFireplacePartBlock> CODEC = simpleCodec(FlooFireplacePartBlock::new);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);

    /** Collision per cell, written for FACING north (back wall at z = 0, opening toward +z) like the models. */
    private static final Map<Integer, Map<Direction, VoxelShape>> SHAPES = new HashMap<>();

    static {
        double[] back = {0, 0, 0, 16, 16, 3};
        // row 0: hearth floor + back wall + jamb; row 1: back wall + jamb; row 2: capital, lintel, shelf, breast
        put(0, 0, back, new double[]{0, 0, 0, 16, 2, 16}, new double[]{0, 2, 3, 11, 16, 16});
        put(2, 0, back, new double[]{0, 0, 0, 16, 2, 16}, new double[]{5, 2, 3, 16, 16, 16});
        put(0, 1, back, new double[]{0, 0, 3, 11, 16, 16});
        put(1, 1, back);
        put(2, 1, back, new double[]{5, 0, 3, 16, 16, 16});
        double[] lintel = {0, 2, 3, 16, 10, 16};
        double[] breast = {0, 10, 3, 16, 16, 6};
        put(0, 2, back, lintel, breast, new double[]{0, 0, 3, 11, 2, 16});
        put(1, 2, back, lintel, breast);
        put(2, 2, back, lintel, breast, new double[]{5, 0, 3, 16, 2, 16});
        put(1, 0, back); // never placed: the hearth owns this cell
    }

    private static void put(int column, int row, double[]... boxes) {
        SHAPES.put(column * 3 + row, DecorShapes.allHorizontal(DecorShapes.boxes(boxes)));
    }

    public FlooFireplacePartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(COLUMN) * 3 + state.getValue(ROW)).get(state.getValue(FACING));
    }

    /** The hearth this part belongs to. */
    public static BlockPos hearthOf(BlockState state, BlockPos pos) {
        Direction across = state.getValue(FACING).getClockWise();
        return pos.relative(across, 1 - state.getValue(COLUMN)).below(state.getValue(ROW));
    }

    private static boolean hasHearth(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState hearth = level.getBlockState(hearthOf(state, pos));
        return hearth.getBlock() instanceof FlooFireplaceBlock
                && hearth.getValue(FlooFireplaceBlock.FACING) == state.getValue(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos hearthPos = hearthOf(state, pos);
        BlockState hearth = level.getBlockState(hearthPos);
        if (!hasHearth(state, level, pos)) {
            return InteractionResult.PASS;
        }
        return hearth.useWithoutItem(level, player, hit.withPosition(hearthPos));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(com.hogwartsmagic.ExampleMod.FLOO_FIREPLACE_ITEM.get());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // In creative, breaking a part must not let the hearth drop the fireplace.
        if (!level.isClientSide && player.isCreative() && hasHearth(state, level, pos)) {
            BlockPos hearthPos = hearthOf(state, pos);
            BlockState hearth = level.getBlockState(hearthPos);
            level.setBlock(hearthPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            level.levelEvent(player, 2001, hearthPos, Block.getId(hearth));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Any part leaving (mined, blown up, replaced) takes the whole fireplace with it; the hearth drops the item.
        if (!level.isClientSide && !newState.is(this) && hasHearth(state, level, pos)) {
            level.destroyBlock(hearthOf(state, pos), true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
