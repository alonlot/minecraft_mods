package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.DespairBasinBlockEntity;
import com.hogwartsmagic.locket.BasinStage;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Basin of Despair. The visual stage is synced through the block state (no block entity sync);
 * the drinking rules live in {@link DespairBasinBlockEntity}.
 */
public class DespairBasinBlock extends BaseEntityBlock {
    public static final MapCodec<DespairBasinBlock> CODEC = simpleCodec(DespairBasinBlock::new);
    public static final EnumProperty<BasinStage> STAGE = EnumProperty.create("stage", BasinStage.class);
    /** True while Slytherin's Locket rests on the dry basin floor, waiting to be taken. */
    public static final BooleanProperty LOCKET = BooleanProperty.create("locket");

    /** How full the basin looks: 6 full, one lower per cup drunk, 0 dry. STAGE stays the logic; this is only the look. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 6);

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public DespairBasinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, BasinStage.FULL).setValue(LOCKET, false).setValue(LEVEL, 6));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, LOCKET, LEVEL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DespairBasinBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ExampleMod.DESPAIR_BASIN_BLOCK_ENTITY.get(), DespairBasinBlockEntity::serverTick);
    }

    /** A goblet dipped in the basin; the rules live in the block entity. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof com.hogwartsmagic.item.CaveGobletItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DespairBasinBlockEntity be) {
            be.dip(serverPlayer, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand lifts the Locket from the dry basin. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && serverPlayer.getMainHandItem().isEmpty()
                && level.getBlockEntity(pos) instanceof DespairBasinBlockEntity be) {
            be.takeLocket(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
