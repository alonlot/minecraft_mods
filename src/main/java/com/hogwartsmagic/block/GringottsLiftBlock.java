package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.GringottsLiftBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The plinth in the middle of the bank's rear rotunda, and its opposite number on the platform far
 * below it.
 *
 * <p>The rotunda already had this shape: a floor ring around a void, a short causeway, and a small
 * island in the middle standing over nothing. Every part of that reads as somewhere you are meant
 * to be carried down from, so the island is the lift and the shaft is cut straight through it.
 *
 * <p>You go down in it, not past it - stepping off the plinth while it is moving simply leaves you
 * on the stair, which winds down the same shaft for anyone who would rather walk or who gets there
 * when the cage is at the bottom.
 */
public class GringottsLiftBlock extends BaseEntityBlock {

    public static final MapCodec<GringottsLiftBlock> CODEC = simpleCodec(GringottsLiftBlock::new);

    /** True at the top of the shaft, false at the bottom. Which way this one travels. */
    public static final BooleanProperty DESCENDS = BooleanProperty.create("descends");

    public GringottsLiftBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DESCENDS, Boolean.TRUE));
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
        builder.add(DESCENDS);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GringottsLiftBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                ExampleMod.GRINGOTTS_LIFT_BLOCK_ENTITY.get(), GringottsLiftBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(level.getBlockEntity(pos) instanceof GringottsLiftBlockEntity lift)) {
            return InteractionResult.PASS;
        }
        if (lift.isMoving()) {
            return InteractionResult.CONSUME;
        }
        if (!lift.begin(server)) {
            // Nothing to travel to: the shaft has not been dug, or something has filled it in.
            server.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 0.6F);
            player.displayClientMessage(
                    Component.translatable("block.hogwartsmagic.gringotts_lift.stuck"), true);
            return InteractionResult.CONSUME;
        }
        server.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 1.0D,
                pos.getZ() + 0.5D, 12, 0.4D, 0.1D, 0.4D, 0.02D);
        return InteractionResult.CONSUME;
    }
}
