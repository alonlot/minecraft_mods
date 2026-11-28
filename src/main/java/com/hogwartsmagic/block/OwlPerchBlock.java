package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.OwlPerchBlockEntity;
import com.hogwartsmagic.owlpost.OwlPost;
import com.hogwartsmagic.owlpost.OwlPostData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class OwlPerchBlock extends BaseEntityBlock {
    public static final MapCodec<OwlPerchBlock> CODEC = simpleCodec(OwlPerchBlock::new);
    private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    public OwlPerchBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OwlPerchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ExampleMod.OWL_PERCH_BLOCK_ENTITY.get(), OwlPerchBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof OwlPerchBlockEntity perch)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // The route tag (adds this perch to its list) and a named name tag (names the perch, ModEvents) do their own
        // thing. SKIP, not PASS_TO_DEFAULT: that ran useWithoutItem below, which always succeeds, so the tag's own
        // useOn never ran and a perch could not be added to a route without sneaking.
        if (stack.is(ExampleMod.OWL_ROUTE_TAG.get())
                || (stack.is(net.minecraft.world.item.Items.NAME_TAG)
                && stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            OwlPost.openSendScreen(serverPlayer, perch);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whatever the perch holds drops when it is broken, and it leaves the owl post's list of perches. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof OwlPerchBlockEntity perch) {
                net.minecraft.world.Containers.dropContents(level, pos, perch);
            }
            if (level instanceof ServerLevel serverLevel) {
                OwlPostData.get(serverLevel.getServer()).removePerch(GlobalPos.of(level.dimension(), pos));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Empty hand: take what owls brought (the inbox), or else take back a parcel still waiting to go, or else hear
     * how the perch stands. Sneaking: choose where hoppers feeding this perch send to.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof OwlPerchBlockEntity perch)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            OwlPost.openDefaultScreen(serverPlayer, perch);
            return InteractionResult.SUCCESS;
        }
        if (perch.hasInbox() && perch.mayManage(player)) {
            int stacks = 0;
            for (ItemStack stack : perch.takeInbox()) {
                stacks++;
                if (!player.addItem(stack)) {
                    player.drop(stack, false);
                }
            }
            serverPlayer.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.took_inbox",
                    "You take %s parcel(s) off the perch.", stacks), true);
            return InteractionResult.SUCCESS;
        }
        if (perch.hasOutbox() && (player.getUUID().equals(perch.getOutboxSender())
                || (perch.getOutboxSender() == null && perch.mayManage(player)))) {
            // Only whoever queued the parcel takes it back (the owner, for one a hopper loaded).
            ItemStack stack = perch.takeOutbox();
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            serverPlayer.displayClientMessage(Component.translatableWithFallback("message.hogwartsmagic.owl_post.took_back",
                    "You take the parcel back."), true);
            return InteractionResult.SUCCESS;
        }
        serverPlayer.displayClientMessage(OwlPost.statusMessage(serverLevel, perch), false);
        return InteractionResult.SUCCESS;
    }
}
