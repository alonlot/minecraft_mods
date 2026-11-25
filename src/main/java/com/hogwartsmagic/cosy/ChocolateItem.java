package com.hogwartsmagic.cosy;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A chocolate bar: quick to eat, and the cure for a Dementor's touch. Server side only. */
public class ChocolateItem extends Item {
    public ChocolateItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack left = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide) {
            FoodRules.applyChocolateCure(entity);
        }
        return left;
    }
}
