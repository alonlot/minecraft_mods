package com.hogwartsmagic.cosy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/** What is in the cooking pot, one stack of one item per ingredient. */
public record PotInput(List<ItemStack> items) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public int size() {
        return this.items.size();
    }
}
