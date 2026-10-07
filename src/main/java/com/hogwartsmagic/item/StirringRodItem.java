package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.LevelReader;

import java.util.List;

/**
 * The fine wood stick, which is also the cauldron's stirring rod. Vanilla skips the block when a sneaking player
 * holds an item; at a Wizard Cauldron this rod lets the click through, so sneaking stirs anticlockwise. Forge needs
 * both hands to allow it: an empty off hand does.
 */
public class StirringRodItem extends Item {
    public StirringRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).is(ExampleMod.WIZARD_CAULDRON.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.fine_wood_stick.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
