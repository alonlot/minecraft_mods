package com.hogwartsmagic.item;

import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Personal focus item that can be bound to an active Cave Inimicum ward. While
 * carried, it grants the holder true sight into that specific ward from outside.
 */
public class CaveInimicumTotemItem extends Item {
    private static final String TAG_WARD_POS = "BoundWardPos";
    private static final String TAG_DIMENSION = "BoundWardDimension";

    public CaveInimicumTotemItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)
                || !(context.getPlayer() instanceof ServerPlayer player)
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CaveInimicumWardBlockEntity ward)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        if (!ward.isActive()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.cave_inimicum_totem.needs_active_ward")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        bindToWard(context.getItemInHand(), serverLevel, ward);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.cave_inimicum_totem.bound")
                .withStyle(ChatFormatting.AQUA), true);
        com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(serverLevel).syncRelevantWardsToPlayer(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.item.Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.cave_inimicum_totem.tooltip").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = getCustomTag(stack);
        if (tag.contains(TAG_WARD_POS)) {
            BlockPos wardPos = BlockPos.of(tag.getLong(TAG_WARD_POS));
            tooltip.add(Component.translatable("item.hogwartsmagic.cave_inimicum_totem.bound_pos",
                    wardPos.getX(), wardPos.getY(), wardPos.getZ()).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.hogwartsmagic.cave_inimicum_totem.unbound").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean isWardBoundToPlayerInventory(Player player, ServerLevel level, CaveInimicumWardBlockEntity ward) {
        for (ItemStack stack : player.getInventory().items) {
            if (isWardBoundToTotem(stack, level, ward)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isWardBoundToTotem(stack, level, ward)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (isWardBoundToTotem(stack, level, ward)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWardBoundToTotem(ItemStack stack, ServerLevel level, CaveInimicumWardBlockEntity ward) {
        if (!(stack.getItem() instanceof CaveInimicumTotemItem)) {
            return false;
        }
        CompoundTag tag = getCustomTag(stack);
        if (!tag.contains(TAG_WARD_POS) || !tag.contains(TAG_DIMENSION)) {
            return false;
        }
        if (!tag.getString(TAG_DIMENSION).equals(level.dimension().location().toString())) {
            return false;
        }
        return BlockPos.of(tag.getLong(TAG_WARD_POS)).equals(ward.getBlockPos());
    }

    private static void bindToWard(ItemStack stack, ServerLevel level, CaveInimicumWardBlockEntity ward) {
        CompoundTag tag = getCustomTag(stack);
        tag.putLong(TAG_WARD_POS, ward.getBlockPos().asLong());
        tag.putString(TAG_DIMENSION, level.dimension().location().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
