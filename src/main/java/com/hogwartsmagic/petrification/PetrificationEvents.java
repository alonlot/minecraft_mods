package com.hogwartsmagic.petrification;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The world around stone: the player tick, no damage, no targeting, keys cleared on another death. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetrificationEvents {
    private PetrificationEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            Petrification.tick(player);
        }
    }

    /** Stone takes no damage except the void, /kill and giving in. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !Petrification.isPetrified(entity)) {
            return;
        }
        if (!PetrificationRules.damagePasses(typeId(event.getSource()))) {
            event.setCanceled(true);
        }
    }

    /** Mobs, the Basilisk, Dementors and NPC casters never take stone as a new target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (target != null && Petrification.isPetrified(target)) {
            event.setCanceled(true);
        }
    }

    /** A stone player who dies another way (the void, /kill) respawns free. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && Petrification.hasPetrifiedFlag(player)) {
            Petrification.clearFlag(player);
        }
    }

    /** A mob that became a statue drops nothing: bringing it back must not duplicate its items. Bosses keep theirs. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!event.isCanceled() && !event.getEntity().level().isClientSide()
                && Petrification.isStatueDeath(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperienceDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (!event.isCanceled() && !event.getEntity().level().isClientSide()
                && Petrification.isStatueDeath(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static String typeId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
    }
}
