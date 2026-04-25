package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class PolyjuiceSampleItem extends Item {

    public PolyjuiceSampleItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        if (PolyjuiceData.hasSample(stack)) {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".polyjuice_sample.target", PolyjuiceData.describe(PolyjuiceData.getSample(stack))).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".polyjuice_sample.empty").withStyle(ChatFormatting.GRAY));
        }
    }
}
