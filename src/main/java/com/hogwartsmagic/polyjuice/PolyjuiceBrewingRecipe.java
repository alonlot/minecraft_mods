package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.common.brewing.IBrewingRecipe;

import java.util.List;
import java.util.Optional;

public class PolyjuiceBrewingRecipe implements IBrewingRecipe {

    @Override
    public boolean isInput(ItemStack input) {
        if (!input.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.potion().isPresent() && contents.potion().get().is(ExampleMod.STABILIZED_POLYJUICE_BASE.getKey());
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return ingredient.is(ExampleMod.POLYJUICE_SAMPLE.get()) && PolyjuiceData.hasSample(ingredient);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) {
            return ItemStack.EMPTY;
        }
        ItemStack output = new ItemStack(ExampleMod.POLYJUICE_POTION.get());
        PolyjuiceData.setPolyjuiceTarget(output, PolyjuiceData.getSample(ingredient));
        MobEffectInstance polyjuice = new MobEffectInstance(
                ExampleMod.POLYJUICE_EFFECT.getHolder().get(),
                PolyjuicePotionItem.BASE_DISGUISE_DURATION_TICKS,
                0);
        output.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(polyjuice)));
        return output;
    }
}
