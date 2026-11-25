package com.hogwartsmagic.cosy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Bertie Bott's Every Flavour Beans: a box of ten. Each eat takes one bean (16 ticks) and rolls a flavour from the
 * {@link BeanTable}: a good effect, nothing much, or a silly one. The flavour shows on the action bar. The beans left
 * live in the {@link CosyFood#BEANS_LEFT} component (absent reads as a full box); the box is gone when it is empty.
 */
public class BertieBottsItem extends Item {
    public BertieBottsItem(Properties properties) {
        super(properties);
    }

    /** Beans left in a box; a box with no component yet is full. */
    public static int beansLeft(ItemStack box) {
        return box.getOrDefault(CosyFood.BEANS_LEFT.get(), BeanTable.BOX_SIZE);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return stack;
        }
        BeanTable.Outcome flavour = BeanTable.roll(level.random);
        player.getFoodData().eat(1, 0.1F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.5F, 1.2F);
        if (flavour.effect() != null) {
            BuiltInRegistries.MOB_EFFECT.getHolder(flavour.effect()).ifPresent(effect ->
                    player.addEffect(new MobEffectInstance(effect, flavour.ticks(), flavour.amp())));
        }
        if (BeanTable.BURP.equals(flavour.flavourKey())) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP,
                    SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        player.displayClientMessage(Component.translatable("bean.hogwartsmagic." + flavour.flavourKey()), true);

        if (!player.getAbilities().instabuild) {
            int left = beansLeft(stack) - 1;
            if (left <= 0) {
                stack.shrink(1);
            } else {
                stack.set(CosyFood.BEANS_LEFT.get(), left);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.bertie_botts_beans.left", beansLeft(stack))
                .withStyle(ChatFormatting.GRAY));
    }
}
