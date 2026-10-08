package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * Builds a Polyjuice bottle for a sampled target, as the brewing stand recipes did: a polyjuice_potion with a 3:00
 * effect, 8:00 when extended, or a vanilla splash potion with the same contents and target when splash (only
 * Items.SPLASH_POTION is handled on impact, in ModEvents.onProjectileImpact).
 */
public final class PolyjuiceStacks {
    private PolyjuiceStacks() {
    }

    public static ItemStack make(CompoundTag target, boolean extended, boolean splash) {
        MobEffectInstance polyjuice = extended
                ? new MobEffectInstance(ExampleMod.POLYJUICE_EFFECT.getHolder().get(),
                        PolyjuicePotionItem.EXTENDED_DISGUISE_DURATION_TICKS, 0, false, false, true)
                : new MobEffectInstance(ExampleMod.POLYJUICE_EFFECT.getHolder().get(),
                        PolyjuicePotionItem.BASE_DISGUISE_DURATION_TICKS, 0);
        ItemStack out = new ItemStack(splash ? Items.SPLASH_POTION : ExampleMod.POLYJUICE_POTION.get());
        out.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(polyjuice)));
        PolyjuiceData.setPolyjuiceTarget(out, target);
        if (splash) {
            out.set(DataComponents.CUSTOM_NAME, Component.translatable("item.hogwartsmagic.splash_polyjuice_potion"));
        }
        return out;
    }
}
