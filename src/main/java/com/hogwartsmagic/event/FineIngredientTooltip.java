package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Marks fine ingredients with a gold line. ItemTooltipEvent is a common class that only ever fires on the client. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FineIngredientTooltip {
    private FineIngredientTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getOrDefault(ExampleMod.FINE_INGREDIENT.get(), false)) {
            event.getToolTip().add(Component.translatable("tooltip.hogwartsmagic.fine_ingredient")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
