package com.hogwartsmagic.cosy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Famous Wizard Card. One item; the card is its {@link CosyFood#CARD_ID} component, and the frame colour
 * (bronze, silver or gold) comes from a custom-model-data value of rarity + 1 that the item model switches on.
 */
public class WizardCardItem extends Item {
    public WizardCardItem(Properties properties) {
        super(properties);
    }

    /** A fresh stack of one card. */
    public static ItemStack create(WizardCards.Card card) {
        ItemStack stack = new ItemStack(CosyFood.WIZARD_CARD.get());
        stack.set(CosyFood.CARD_ID.get(), card.id());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(card.rarity() + 1));
        return stack;
    }

    /** The card a stack carries, or null for a blank one. */
    @Nullable
    public static WizardCards.Card cardOf(ItemStack stack) {
        Integer id = stack.get(CosyFood.CARD_ID.get());
        return id == null ? null : WizardCards.byId(id);
    }

    @Override
    public Component getName(ItemStack stack) {
        WizardCards.Card card = cardOf(stack);
        if (card == null) {
            return super.getName(stack);
        }
        return Component.translatable("card.hogwartsmagic." + card.key() + ".name");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WizardCards.Card card = cardOf(stack);
        if (card == null) {
            return;
        }
        ChatFormatting colour = card.rarity() == WizardCards.LEGENDARY ? ChatFormatting.GOLD
                : card.rarity() == WizardCards.RARE ? ChatFormatting.AQUA : ChatFormatting.GRAY;
        tooltip.add(Component.translatable("card.hogwartsmagic.rarity." + card.rarity()).withStyle(colour));
        tooltip.add(Component.translatable("card.hogwartsmagic." + card.key() + ".line")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
