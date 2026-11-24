package com.hogwartsmagic.portkey;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * An Old Portkey: a cracked, mouldering boot found in forgotten houses and hideouts, still faintly enchanted. It is
 * already active and cannot be bound. Hold use for three seconds and it throws you (and anyone beside you) two to
 * four thousand blocks away in a direction nobody chose, onto dry ground under the open sky, and then crumbles to
 * dust in your hand. It shimmers like an enchanted item.
 */
public class OldPortkeyItem extends PortkeyItem {

    public OldPortkeyItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean bindable() {
        return false;
    }

    @Override
    protected boolean active(ItemStack stack) {
        return !PortkeyData.spent(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.hogwartsmagic.old_portkey.active").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("tooltip.hogwartsmagic.old_portkey.hold").withStyle(ChatFormatting.GRAY));
    }
}
