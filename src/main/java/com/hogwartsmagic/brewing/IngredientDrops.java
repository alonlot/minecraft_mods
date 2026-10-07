package com.hogwartsmagic.brewing;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A Blaze now and then drops an ashwinder egg (any kill, no looting bonus). */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IngredientDrops {
    private IngredientDrops() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead.getType() != EntityType.BLAZE) {
            return;
        }
        if (PotionRules.rolls(dead.getRandom().nextDouble(), PotionRules.ASHWINDER_BLAZE_CHANCE)) {
            event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                    new ItemStack(ExampleMod.ASHWINDER_EGG.get())));
        }
    }
}
