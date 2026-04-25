package com.hogwartsmagic.relic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class RelicItem extends Item {
    private final RelicType relicType;

    public RelicItem(RelicType relicType, Properties properties) {
        super(properties.stacksTo(1));
        this.relicType = relicType;
    }

    public RelicType getRelicType() {
        return relicType;
    }

    public static RelicType getRelicType(ItemStack stack) {
        if (stack.getItem() instanceof RelicItem relicItem) {
            return relicItem.getRelicType();
        }
        return null;
    }

    public static boolean isRelic(ItemStack stack) {
        return getRelicType(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        RelicType type = getRelicType();
        tooltipComponents.add(Component.translatable("tooltip.hogwartsmagic.relic.slot." + type.getSlotType().getId())
                .withStyle(ChatFormatting.GOLD));
        if (type.getMaxManaBonus() > 0.0f) {
            tooltipComponents.add(Component.translatable("tooltip.hogwartsmagic.relic.max_mana", (int) type.getMaxManaBonus())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (type.getManaRegenMultiplier() > 1.0f) {
            int percent = Math.round((type.getManaRegenMultiplier() - 1.0f) * 100.0f);
            tooltipComponents.add(Component.translatable("tooltip.hogwartsmagic.relic.regen", percent)
                    .withStyle(ChatFormatting.BLUE));
        }
        tooltipComponents.add(Component.translatable(type.isFoundOnly()
                        ? "tooltip.hogwartsmagic.relic.found_only"
                        : "tooltip.hogwartsmagic.relic.crafted")
                .withStyle(ChatFormatting.GRAY));
    }
}
