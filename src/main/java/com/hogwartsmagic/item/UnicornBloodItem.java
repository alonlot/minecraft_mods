package com.hogwartsmagic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Silver blood taken from a slain unicorn. Drunk, it drags you back from the edge of death -
 * full health, regeneration, a layer of absorption - and then the price arrives: a long run of
 * ill luck, weakness and hunger. The curse is the point; it is never worth it lightly.
 */
public class UnicornBloodItem extends Item {
    private static final int DRINK_TICKS = 32;

    public UnicornBloodItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (!level.isClientSide) {
            living.removeEffect(MobEffects.WITHER);
            living.removeEffect(MobEffects.POISON);
            living.setHealth(living.getMaxHealth());
            living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 60, 1));
            living.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 120, 1));
            living.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 0));
            // The price.
            living.addEffect(new MobEffectInstance(MobEffects.UNLUCK, 20 * 60 * 60, 1));
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 60 * 10, 0));
            living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 1));
            living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 5, 0));
            if (living instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.unicorn_blood.drink")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
            }
            level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.SOUL_ESCAPE.value(),
                    living.getSoundSource(), 1.0F, 0.7F);
        }
        if (living instanceof Player player && player.hasInfiniteMaterials()) {
            return stack;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        if (living instanceof Player player && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
            player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.unicorn_blood.desc").withStyle(ChatFormatting.GRAY,
                ChatFormatting.ITALIC));
    }
}
