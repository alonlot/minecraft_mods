package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Physical anchor block for Cave Inimicum. Players configure the ward here and
 * the spell must be cast directly onto this block.
 */
public class WardBlock extends BaseEntityBlock {
    public static final MapCodec<WardBlock> CODEC = simpleCodec(WardBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0),
            Block.box(4.0, 0.0, 0.0, 12.0, 16.0, 2.0),
            Block.box(4.0, 0.0, 14.0, 12.0, 16.0, 16.0),
            Block.box(0.0, 0.0, 4.0, 2.0, 16.0, 12.0),
            Block.box(14.0, 0.0, 4.0, 16.0, 16.0, 12.0)
    );

    public WardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CaveInimicumWardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ExampleMod.CAVE_INIMICUM_WARD_BLOCK_ENTITY.get(), CaveInimicumWardBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                if (ward.isActive()) {
                    ward.showOutlinePreview();
                    serverPlayer.displayClientMessage(Component.literal("Ward is active - showing current outline"), true);
                } else {
                    int size = ward.cycleConfiguredSize();
                    serverPlayer.displayClientMessage(Component.literal("Ward size set to " + size + "x" + size + "x" + size), true);
                }
            } else {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && ward.activatePreparedModes(serverLevel, player)) {
                    ward.showOutlinePreview();
                    serverPlayer.displayClientMessage(Component.literal("Ward activated"), true);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                ward.showOutlinePreview();
                serverPlayer.displayClientMessage(ward.getStatusMessage(), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward) {
            ward.deactivate(serverLevel, true);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
