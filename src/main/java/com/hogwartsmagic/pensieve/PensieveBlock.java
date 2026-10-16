package com.hogwartsmagic.pensieve;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The Pensieve basin. Pour a memory in, lift it out, dive into it. All use logic runs on the server. */
public class PensieveBlock extends BaseEntityBlock {
    public static final MapCodec<PensieveBlock> CODEC = simpleCodec(PensieveBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);

    public PensieveBlock(Properties properties) {
        super(properties);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PensieveBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PensieveMemory)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof PensieveBlockEntity basin && basin.memory().isEmpty()) {
            basin.pour(stack, player.getUUID());
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
            return ItemInteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.full"), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PensieveBlockEntity basin) || basin.memory().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            boolean op = player instanceof ServerPlayer sp && sp.hasPermissions(2);
            boolean anyoneInside = PensieveSessions.anyoneInside(level, pos);
            if (BasinAccess.canTakeOut(basin.pourer(), player.getUUID(), op, basin.publicBasin(), anyoneInside)) {
                // The pourer or an op lifting it out while people watch pulls them all out.
                PensieveSessions.abortAt(level, pos);
                player.getInventory().placeItemBackInInventory(basin.takeOut());
            } else {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.not_yours"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer sp) {
            PensieveSessions.startOrJoin(sp, basin);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PensieveSessions.abortAt(level, pos);
            if (level.getBlockEntity(pos) instanceof PensieveBlockEntity basin && !basin.memory().isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, basin.takeOut());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
