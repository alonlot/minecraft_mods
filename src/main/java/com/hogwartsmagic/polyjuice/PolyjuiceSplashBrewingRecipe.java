package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraftforge.common.brewing.IBrewingRecipe;

/**
 * Polyjuice potion + gunpowder -> vanilla splash potion that keeps the Polyjuice target data.
 */
public class PolyjuiceSplashBrewingRecipe implements IBrewingRecipe {

    @Override
    public boolean isInput(ItemStack input) {
        return input.is(ExampleMod.POLYJUICE_POTION.get()) && PolyjuiceData.hasPolyjuiceTarget(input);
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return ingredient.is(Items.GUNPOWDER);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) {
            return ItemStack.EMPTY;
        }
        ItemStack output = new ItemStack(Items.SPLASH_POTION);
        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        output.set(DataComponents.POTION_CONTENTS, contents);
        PolyjuiceData.setPolyjuiceTarget(output, PolyjuiceData.getPolyjuiceTarget(input));
        output.set(DataComponents.CUSTOM_NAME, Component.translatable("item.hogwartsmagic.splash_polyjuice_potion"));
        return output;
    }
}
