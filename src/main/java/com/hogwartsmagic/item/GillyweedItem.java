package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class GillyweedItem extends Item {
    private static final int EFFECT_DURATION_TICKS = 20 * 60 * 3; // 3:00

    public GillyweedItem(Properties properties) {
        super(properties.food(new net.minecraft.world.food.FoodProperties.Builder()
                .nutrition(2)
                .saturationModifier(0.2f)
                .alwaysEdible()
                .build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        ItemStack result = super.finishUsingItem(stack, level, livingEntity);
        if (!level.isClientSide) {
            livingEntity.addEffect(new MobEffectInstance(ExampleMod.GILLYWEED_EFFECT.getHolder().get(), EFFECT_DURATION_TICKS, 0, false, false, false));
            livingEntity.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, EFFECT_DURATION_TICKS, 0));
            livingEntity.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, EFFECT_DURATION_TICKS, 0));
            livingEntity.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION_TICKS, 0));
            level.playSound(null, livingEntity.blockPosition(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.PLAYERS, 0.7f, 1.0f);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag flag) {
        tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".gillyweed.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item." + ExampleMod.MODID + ".gillyweed.tooltip.2").withStyle(ChatFormatting.GRAY));
    }
}
