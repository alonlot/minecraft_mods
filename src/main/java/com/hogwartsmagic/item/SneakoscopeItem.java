package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Sneakoscope item. Placed, it is SneakoscopeBlock. Carried, SneakoscopeCarried sets the transient
 * SNEAKOSCOPE_SPINNING component while it detects something; the client draws the spin from it.
 */
public class SneakoscopeItem extends BlockItem {
    public SneakoscopeItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static boolean isSpinning(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ExampleMod.SNEAKOSCOPE_SPINNING.get()));
    }

    /** Sets or clears the spin. Callers only call it when the value changes, so the slot syncs only then. */
    public static void setSpinning(ItemStack stack, boolean spinning) {
        if (spinning) {
            stack.set(ExampleMod.SNEAKOSCOPE_SPINNING.get(), Boolean.TRUE);
        } else {
            stack.remove(ExampleMod.SNEAKOSCOPE_SPINNING.get());
        }
    }

    /** A spin change in the hand is the same item: no equip bob. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /** A thrown Sneakoscope stops spinning: nobody holds it any more. */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        setSpinning(stack, false);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.sneakoscope.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
