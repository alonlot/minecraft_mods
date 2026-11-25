package com.hogwartsmagic.cosy;

import com.hogwartsmagic.item.MagicWandItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The self-stirring cooking pot. Stand it on a heat source (a lit stove, a campfire, the hearth ...), right-click to
 * put ingredients in (up to six), then stir: an empty hand stirs for three seconds, a wand enchants the ladle to stir
 * itself until the dish is done. Right-click again to take the dish; sneak with an empty hand to take the
 * ingredients back. Breaking it drops whatever it holds.
 */
public class CookingPotBlock extends BaseEntityBlock {
    public static final MapCodec<CookingPotBlock> CODEC = simpleCodec(CookingPotBlock::new);
    /** Drawn with broth in it while it holds ingredients or a finished dish. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 10, 15);

    public CookingPotBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
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
        return new CookingPotBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, CosyKitchen.COOKING_POT_BLOCK_ENTITY.get(), CookingPotBlockEntity::clientTick);
        }
        return createTickerHelper(type, CosyKitchen.COOKING_POT_BLOCK_ENTITY.get(), CookingPotBlockEntity::serverTick);
    }

    // ---------------------------------------------------------------- using it

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (MagicWandItem.isAnyWand(stack)) {
            if (pot.enchantLadle()) {
                level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pot.enchanted"), true);
            } else {
                player.displayClientMessage(Component.translatable(pot.isDone()
                        ? "message.hogwartsmagic.pot.done" : "message.hogwartsmagic.pot.nothing_to_stir"), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (pot.isDone()) {
            // A finished dish waits for an empty hand.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!pot.addIngredient(stack)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pot.full"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 1.2F);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }
        if (pot.isDone()) {
            give(player, pot.takeOutput());
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
        } else if (player.isShiftKeyDown()) {
            List<ItemStack> back = pot.takeIngredients();
            if (back.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pot.empty"), true);
            } else {
                give(player, back);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.5F, 1.4F);
            }
        } else if (pot.contents().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pot.empty"), true);
        } else {
            pot.stirByHand();
            level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.3F);
            if (!CosyKitchen.isHeatedBelow(level, pos)) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pot.cold"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void give(Player player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            ChocolateFrogItem.giveOrDrop(player, stack);
        }
    }

    // ---------------------------------------------------------------- breaking

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof CookingPotBlockEntity pot) {
            for (ItemStack stack : pot.contents()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            }
            for (ItemStack stack : pot.output()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
