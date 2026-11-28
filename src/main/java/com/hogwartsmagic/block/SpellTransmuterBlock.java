package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.SpellTransmuterBlockEntity;
import com.hogwartsmagic.item.MagicBookItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Binds a spell book into an enchanted book. Right-click with a spell book, a plain book or arcane dust to lay it on
 * the top; an empty hand takes the finished book (or, before the rite, the last thing laid on) back.
 */
public class SpellTransmuterBlock extends BaseEntityBlock {
    public static final MapCodec<SpellTransmuterBlock> CODEC = simpleCodec(SpellTransmuterBlock::new);
    /** Follows models/block/spell_transmuter.json: foot, column, top, crystals. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0),
            Block.box(4.0, 3.0, 4.0, 12.0, 10.0, 12.0),
            Block.box(2.0, 10.0, 2.0, 14.0, 13.0, 14.0)
    );

    public SpellTransmuterBlock(BlockBehaviour.Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpellTransmuterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ExampleMod.SPELL_TRANSMUTER_BLOCK_ENTITY.get(), SpellTransmuterBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (held.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        int slot = SpellTransmuterBlockEntity.slotFor(held);
        if (slot < 0) {
            if (held.is(ExampleMod.MAGIC_BOOK.get()) && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.spell_transmuter.no_binding",
                        MagicBookItem.getSpell(held) == null ? Component.literal("?")
                                : Component.translatable("spell.hogwartsmagic." + MagicBookItem.getSpell(held).getId()))
                        .withStyle(ChatFormatting.GRAY), true);
                return ItemInteractionResult.CONSUME;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SpellTransmuterBlockEntity be) {
            ItemStack one = player.getAbilities().instabuild ? held.copy() : held;
            if (be.insert((ServerLevel) level, one)) {
                return ItemInteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable(be.rite() >= 0
                    ? "message.hogwartsmagic.spell_transmuter.busy"
                    : "message.hogwartsmagic.spell_transmuter.full").withStyle(ChatFormatting.GRAY), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SpellTransmuterBlockEntity be) {
            ItemStack taken = be.takeOne();
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.2F);
            } else if (be.rite() < 0) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.spell_transmuter.hint")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SpellTransmuterBlockEntity be) {
            be.dropContents(level);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("block.hogwartsmagic.spell_transmuter.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
