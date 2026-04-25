package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraftforge.common.brewing.IBrewingRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Polyjuice potion (base duration) + redstone → extended duration (vanilla-style 3:00 → 8:00).
 */
public class PolyjuiceRedstoneExtendBrewingRecipe implements IBrewingRecipe {

    @Override
    public boolean isInput(ItemStack input) {
        if (!input.is(ExampleMod.POLYJUICE_POTION.get()) || !PolyjuiceData.hasPolyjuiceTarget(input)) {
            return false;
        }
        return PolyjuicePotionItem.getPolyjuiceEffectDurationTicks(input) == PolyjuicePotionItem.BASE_DISGUISE_DURATION_TICKS;
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        return ingredient.is(Items.REDSTONE);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = input.copyWithCount(1);
        PotionContents old = out.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        List<MobEffectInstance> next = new ArrayList<>();
        for (MobEffectInstance e : old.getAllEffects()) {
            if (e.getEffect().is(ExampleMod.POLYJUICE_EFFECT.getKey())) {
                next.add(new MobEffectInstance(
                        e.getEffect(),
                        PolyjuicePotionItem.EXTENDED_DISGUISE_DURATION_TICKS,
                        e.getAmplifier(),
                        e.isAmbient(),
                        false,
                        true));
            } else {
                next.add(e);
            }
        }
        out.set(DataComponents.POTION_CONTENTS, new PotionContents(old.potion(), old.customColor(), next));
        return out;
    }
}
