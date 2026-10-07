package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla only sends an entity's effects to that entity itself (if it is a player) and to players
 * riding it, never to players merely watching it. The Levicorpus hang and the Petrificus topple are
 * drawn from the effect on the client, so these two markers are pushed to every watching client with
 * vanilla's own effect packets: on add, on removal, on expiry (the client never expires effects by
 * itself), and to a player who starts watching an entity that already has one. Common code: the
 * packets are vanilla network classes, present on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VisibleEffects {
    private VisibleEffects() {
    }

    public static boolean isVisible(MobEffect effect) {
        // Basilisk stone too: watchers draw the stone tint, and a rescuer's client offers the Mandrake cure.
        return effect == ExampleMod.LEVICORPUS_EFFECT.get() || effect == ExampleMod.PETRIFIED.get()
                || effect == ExampleMod.BASILISK_PETRIFICATION.get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance instance = event.getEffectInstance();
        if (entity.level() instanceof ServerLevel level && isVisible(instance.getEffect().value())) {
            level.getChunkSource().broadcast(entity, new ClientboundUpdateMobEffectPacket(entity.getId(), instance, false));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRemove(MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level && isVisible(event.getEffect())) {
            MobEffectInstance instance = event.getEffectInstance();
            if (instance != null) {
                broadcastRemoval(level, entity, instance.getEffect());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExpired(MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance instance = event.getEffectInstance();
        if (entity.level() instanceof ServerLevel level && instance != null && isVisible(instance.getEffect().value())) {
            broadcastRemoval(level, entity, instance.getEffect());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (isVisible(instance.getEffect().value())) {
                watcher.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), instance, false));
            }
        }
    }

    private static void broadcastRemoval(ServerLevel level, LivingEntity entity, Holder<MobEffect> effect) {
        level.getChunkSource().broadcast(entity, new ClientboundRemoveMobEffectPacket(entity.getId(), effect));
    }
}
