package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
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

import javax.annotation.Nullable;

/**
 * The door on a Gringotts vault.
 *
 * <p>Every block of the leaf is one of these, and any of them will start the sequence - the door is
 * opened by putting a hand on it, the way a goblin does, rather than by finding a keyhole. Which
 * block you touch is where the ripple starts from, so it is worth touching the middle.
 *
 * <p>The leaf is unminable on purpose. A vault you can take out with a pickaxe is not a vault, and
 * the whole of the rest of the chamber is sealed rock, so the door is the only way in either way.
 */
public class GringottsVaultDoorBlock extends BaseEntityBlock {

    public static final MapCodec<GringottsVaultDoorBlock> CODEC =
            simpleCodec(GringottsVaultDoorBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public GringottsVaultDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, Boolean.FALSE));
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
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GringottsVaultDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                ExampleMod.GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY.get(),
                GringottsVaultDoorBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (state.getValue(OPEN)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.getMainHandItem().isEmpty()) {
            // A goblin draws a finger down it. Anything held is in the way of that.
            server.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS,
                    0.8F, 0.5F);
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.01D);
            player.displayClientMessage(
                    Component.translatable("block.hogwartsmagic.gringotts_vault_door.hand"), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof GringottsVaultDoorBlockEntity door) {
            door.beginOpening(server, hit.getLocation());
        }
        return InteractionResult.CONSUME;
    }
}
