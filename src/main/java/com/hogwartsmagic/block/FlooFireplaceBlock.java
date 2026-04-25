package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(ACTIVE)) {
            return;
        }
        super.entityInside(state, level, pos, entity);
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
