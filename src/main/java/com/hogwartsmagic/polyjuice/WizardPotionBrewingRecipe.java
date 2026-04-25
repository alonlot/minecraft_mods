package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.brewing.IBrewingRecipe;

import java.util.List;
import java.util.Optional;

/**
 * Awkward potion + acromantula venom -> vanilla drinkable bottle with {@link ExampleMod#WIZARD_POTION}.
 */
public class WizardPotionBrewingRecipe implements IBrewingRecipe {

    @Override
    public boolean isInput(ItemStack input) {
        if (!input.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.potion().isPresent() && contents.potion().get().is(Potions.AWKWARD);
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return ingredient.is(ExampleMod.ACROMANTULA_VENOM.get());
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = new ItemStack(Items.POTION);
        out.set(DataComponents.POTION_CONTENTS, new PotionContents(
                ExampleMod.WIZARD_POTION.getHolder(),
                Optional.empty(),
                List.of()));
        return out;
    }
}
