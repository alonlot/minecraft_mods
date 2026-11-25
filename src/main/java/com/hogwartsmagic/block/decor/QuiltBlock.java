package com.hogwartsmagic.block.decor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A patchwork quilt: a one-pixel layer like a carpet. Laid on a bed, the model drops seven pixels to rest on the
 * mattress (a bed is nine pixels tall). It has no collision, so it never stops anyone sleeping.
 */
public class QuiltBlock extends Block {
    public static final MapCodec<QuiltBlock> CODEC = simpleCodec(QuiltBlock::new);
    public static final BooleanProperty ON_BED = BooleanProperty.create("on_bed");
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    /** The quilt on a bed is drawn seven pixels down, so its outline goes there too. */
    private static final VoxelShape ON_BED_SHAPE = Block.box(0.0, -7.0, 0.0, 16.0, -6.0, 16.0);

    public QuiltBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().noCollission());
        registerDefaultState(stateDefinition.any().setValue(ON_BED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ON_BED);
    }

    /** True if the block under {@code pos} is a bed of any colour. */
    public static boolean isOnBed(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.BEDS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_BED) ? ON_BED_SHAPE : SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.isEmptyBlock(pos.below());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ON_BED, isOnBed(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state.setValue(ON_BED, isOnBed(level, pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        boolean onBed = isOnBed(level, pos);
        if (state.getValue(ON_BED) != onBed) {
            level.setBlock(pos, state.setValue(ON_BED, onBed), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
