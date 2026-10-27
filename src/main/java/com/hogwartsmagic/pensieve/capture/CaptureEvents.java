package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus wiring for the Pensieve recorder. Common code only, so it is safe on a dedicated server. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureEvents {
    private CaptureEvents() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            try {
                CaptureLogs.tick(level);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("Pensieve recorder tick failed", e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            try {
                CaptureLogs.lateTick(event.getServer());
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("Pensieve recorder late tick failed", e);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = false)
    public static void onDamage(LivingDamageEvent event) {
        CaptureHooks.onHurt(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = false)
    public static void onDeath(LivingDeathEvent event) {
        CaptureHooks.onDeath(event.getEntity());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof SpellBeamEntity beam && event.getLevel() instanceof ServerLevel level) {
            CaptureHooks.onBeamOpened(level, beam);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof SpellBeamEntity beam && event.getLevel() instanceof ServerLevel level) {
            CaptureHooks.onBeamClosed(level, beam);
        }
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CaptureHooks.markDiscontinuity(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CaptureHooks.markDiscontinuity(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CaptureHooks.markDiscontinuity(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CaptureHooks.markDiscontinuity(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CaptureLogs.forgetPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CaptureLogs.forget(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CaptureLogs.forgetAll();
        RecordingStore.clearCache();
    }
}
