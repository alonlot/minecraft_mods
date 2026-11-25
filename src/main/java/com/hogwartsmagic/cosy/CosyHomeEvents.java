package com.hogwartsmagic.cosy;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-side bookkeeping for the cosy home pack. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class CosyHomeEvents {
    private CosyHomeEvents() {
    }

    /** Warmed only mends the unhurt, so remember when each player last took damage. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof net.minecraft.server.level.ServerPlayer && event.getAmount() > 0.0F) {
            entity.getPersistentData().putLong(WarmedEffect.HURT_KEY, entity.level().getGameTime());
        }
    }
}
