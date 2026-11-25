package com.hogwartsmagic.cosy;

import com.hogwartsmagic.block.decor.DecorShapes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A single-block stone fireplace, built on the campfire like the Floo hearth, so shovels, water, flint and
 * steel, fire charges and Incendio all work on it (see the {@code minecraft:campfires} tag). Unlike the
 * Floo hearth it can go out. It is lit when placed, sends chimney smoke up and warms players nearby.
 */
public class HearthBlock extends CampfireBlock {
    public static final MapCodec<HearthBlock> CODEC = simpleCodec(HearthBlock::new);

    /** Written for FACING north: the back wall is on the north side and the opening faces the player. */
    private static final Map<Direction, VoxelShape> SHAPES = DecorShapes.allHorizontal(DecorShapes.boxes(
            new double[]{0, 0, 0, 16, 2, 16},
            new double[]{0, 2, 0, 16, 16, 5},
            new double[]{0, 2, 5, 3, 12, 16},
            new double[]{13, 2, 5, 16, 12, 16},
            new double[]{0, 12, 5, 16, 16, 16}));

    public HearthBlock(BlockBehaviour.Properties properties) {
        super(true, 0, properties);
        registerDefaultState(defaultBlockState()
                .setValue(LIT, true)
                .setValue(SIGNAL_FIRE, false)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<CampfireBlock> codec() {
        return (MapCodec<CampfireBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        // Hay below would make a campfire a signal fire; that flag means nothing for a hearth.
        return state == null ? null : state.setValue(SIGNAL_FIRE, false);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        return updated.is(this) ? updated.setValue(SIGNAL_FIRE, false) : updated;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HearthBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // The client ticker makes the chimney smoke at the campfire rate on every hearth in view, not just the
        // few that animateTick happens to pick.
        return level.isClientSide
                ? createTickerHelper(type, CosyHome.HEARTH_BLOCK_ENTITY.get(), HearthBlockEntity::clientTick)
                : createTickerHelper(type, CosyHome.HEARTH_BLOCK_ENTITY.get(), HearthBlockEntity::serverTick);
    }

    /** Ember pops (the chimney smoke is the client ticker). The crackle is the hearth loop (see IndoorAmbience), not the vanilla one. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(net.minecraft.core.particles.ParticleTypes.LAVA),
                    pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D,
                    (random.nextFloat() - 0.5F) * 0.1F, 0.1F + random.nextFloat() * 0.08F, (random.nextFloat() - 0.5F) * 0.1F);
        }
    }

    /** People stand beside a hearth to get warm; it never burns them. */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    }
}
