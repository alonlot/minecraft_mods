package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FlooFireplaceBlock extends CampfireBlock {
    public static final MapCodec<FlooFireplaceBlock> CODEC = simpleCodec(FlooFireplaceBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.SIGNAL_FIRE;

    public FlooFireplaceBlock(BlockBehaviour.Properties properties) {
        super(true, 2, properties.noOcclusion().lightLevel(state -> 12));
        registerDefaultState(defaultBlockState()
                .setValue(LIT, true)
                .setValue(SIGNAL_FIRE, false)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<CampfireBlock> codec() {
        return (MapCodec<CampfireBlock>) (MapCodec<?>) CODEC;
    }

    /**
     * The hearth cell of the walk-in fireplace: floor and fire-back only, so a player can step in and
     * stand on it. Written for FACING north (the placing player looked north, so the back wall is on the
     * north side and the opening faces them). The jambs, lintel and mantel are {@link FlooFireplacePartBlock}s.
     */
    private static final java.util.Map<Direction, VoxelShape> SHAPES =
            com.hogwartsmagic.block.decor.DecorShapes.allHorizontal(com.hogwartsmagic.block.decor.DecorShapes.boxes(
                    new double[]{0, 0, 0, 16, 2, 16},
                    new double[]{0, 2, 0, 16, 16, 3}));

    /** Height of the hearth floor a traveller stands on, in blocks above the hearth's own y. */
    public static final double FLOOR = 2.0 / 16.0;

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    // ------------------------------------------------------------------ the three-by-three frame

    /** Where the part in this column (0..2, left to right from the front) and row (0..2) sits. */
    public static BlockPos partPos(BlockPos hearth, Direction facing, int column, int row) {
        return hearth.relative(facing.getClockWise(), column - 1).above(row);
    }

    /** The hearth for a click on any cell of a fireplace; any other position comes back unchanged. */
    public static BlockPos resolveHearth(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof FlooFireplacePartBlock ? FlooFireplacePartBlock.hearthOf(state, pos) : pos;
    }

    /** The spot a traveller stands on: the middle of the hearth floor. */
    public static Vec3 standingSpot(BlockPos hearth) {
        return new Vec3(hearth.getX() + 0.5, hearth.getY() + FLOOR, hearth.getZ() + 0.5);
    }

    private static BlockState partState(Direction facing, int column, int row) {
        return ExampleMod.FLOO_FIREPLACE_PART.get().defaultBlockState()
                .setValue(FlooFireplacePartBlock.FACING, facing)
                .setValue(FlooFireplacePartBlock.COLUMN, column)
                .setValue(FlooFireplacePartBlock.ROW, row);
    }

    private static boolean isHearthCell(int column, int row) {
        return column == 1 && row == 0;
    }

    /** True if every part around this hearth is in place. */
    public static boolean isComplete(BlockGetter level, BlockPos hearth, Direction facing) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (!isHearthCell(column, row)
                        && level.getBlockState(partPos(hearth, facing, column, row)) != partState(facing, column, row)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Builds whatever parts are missing, all or nothing: only when every missing cell is loaded and free.
     * Placing a fireplace uses this, and so do fireplaces from before it was walk-in (one block), which
     * grow their frame as soon as there is room. Returns true if the frame is complete afterwards.
     */
    public static boolean completeFrame(Level level, BlockPos hearth, Direction facing) {
        java.util.List<BlockState> states = new java.util.ArrayList<>();
        java.util.List<BlockPos> missing = new java.util.ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (isHearthCell(column, row)) {
                    continue;
                }
                BlockPos at = partPos(hearth, facing, column, row);
                if (!level.isLoaded(at) || level.isOutsideBuildHeight(at)) {
                    return false;
                }
                BlockState wanted = partState(facing, column, row);
                BlockState there = level.getBlockState(at);
                if (there == wanted) {
                    continue;
                }
                if (!there.canBeReplaced() || !level.isUnobstructed(wanted, at, CollisionContext.empty())) {
                    return false;
                }
                missing.add(at);
                states.add(wanted);
            }
        }
        for (int i = 0; i < missing.size(); i++) {
            level.setBlock(missing.get(i), states.get(i), Block.UPDATE_ALL);
        }
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        // The fireplace opens toward the player; the frame needs its whole three-by-three free.
        Direction facing = state.getValue(FACING);
        Level level = context.getLevel();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (isHearthCell(column, row)) {
                    continue;
                }
                BlockPos at = partPos(context.getClickedPos(), facing, column, row);
                if (level.isOutsideBuildHeight(at) || !level.getBlockState(at).canBeReplaced(context)
                        || !level.isUnobstructed(partState(facing, column, row), at, CollisionContext.empty())) {
                    return null;
                }
            }
        }
        // Campfires turn into signal fires over hay; for the Floo hearth that flag means "green flames".
        return state.setValue(SIGNAL_FIRE, false).setValue(LIT, true).setValue(WATERLOGGED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            completeFrame(level, pos, state.getValue(FACING));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            Direction facing = state.getValue(FACING);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    BlockPos at = partPos(pos, facing, column, row);
                    if (!isHearthCell(column, row) && level.getBlockState(at) == partState(facing, column, row)) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // CampfireBlock recomputes SIGNAL_FIRE from the block below (hay makes a signal fire). Here that
        // property is the green Floo flame, which only the block entity may switch.
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        return updated.is(this) ? updated.setValue(SIGNAL_FIRE, state.getValue(SIGNAL_FIRE)) : updated;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlooFireplaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ExampleMod.FLOO_FIREPLACE_BLOCK_ENTITY.get(), FlooFireplaceBlockEntity::tick);
    }

    /**
     * The campfire's own crackle and ember pops, drawn as the mod's sparks: vanilla's lava pops (and the vanilla
     * smoke each one trails) would otherwise come with the inherited campfire behaviour.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    net.minecraft.sounds.SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(net.minecraft.core.particles.ParticleTypes.LAVA),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    (random.nextFloat() - 0.5F) * 0.1F, 0.12F + random.nextFloat() * 0.08F, (random.nextFloat() - 0.5F) * 0.1F);
        }
    }

    /** Travellers stand in this fire to use it, so it never burns (the campfire it is built on would). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity fireplace) {
            Component message = fireplace.getStatusMessage(player);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(message, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
