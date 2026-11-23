package com.hogwartsmagic.portrait;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.voice.VoiceEars;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Carries what players say to the portraits that guard a passage ({@link PortraitKind#guardsPassage()}; see
 * {@link PortraitDoor}): chat lines and, through wand voice casting, spoken phrases. What was meant for a portrait
 * (a new password, the password, an answer to her "Password?") never reaches the public chat; the speaker sees it
 * as a whisper. Everything else is left alone. Common code, server side only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PortraitPasswords {
    private static final double SEARCH = 8.0;
    /** Players some portrait wants to hear aloud now (their voice client passes short phrases on). */
    private static final Set<UUID> LISTENING = new HashSet<>();

    private PortraitPasswords() {
    }

    public static boolean listensTo(UUID player) {
        return LISTENING.contains(player);
    }

    /** Runs on the server thread (the chat handler is queued there before Forge fires this). */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (heardChat(event.getPlayer(), event.getRawText())) {
            event.setCanceled(true);
        }
    }

    /** {@code said} typed in chat; true when a portrait took it (the chat event is then cancelled). */
    public static boolean heardChat(ServerPlayer player, String said) {
        if (!deliver(player, said, false)) {
            return false;
        }
        player.sendSystemMessage(Component.literal("You whisper to the portrait: " + said)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return true;
    }

    /** A short phrase the player's voice client heard while a portrait listened. */
    public static void heardSpoken(ServerPlayer player, String said) {
        if (LISTENING.contains(player.getUUID())) {
            deliver(player, said, true);
        }
    }

    /** Offers {@code said} to the guarding portraits near the player, nearest first; true when one took it. */
    private static boolean deliver(ServerPlayer player, String said, boolean spoken) {
        for (PortraitEntity portrait : guardsNear(player)) {
            if (portrait.door().heard(player, said, spoken)) {
                return true;
            }
        }
        return false;
    }

    private static List<PortraitEntity> guardsNear(ServerPlayer player) {
        List<PortraitEntity> list = player.serverLevel().getEntitiesOfClass(PortraitEntity.class,
                player.getBoundingBox().inflate(SEARCH), p -> p.isAlive() && p.kind().guardsPassage());
        list.sort(Comparator.comparingDouble(p -> p.distanceToSqr(player)));
        return list;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient() || !(event.level instanceof ServerLevel level)
                || level.getGameTime() % 10L != 0L) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            boolean want = false;
            if (!p.isSpectator()) {
                for (PortraitEntity portrait : guardsNear(p)) {
                    if (portrait.door().listensTo(p, now)) {
                        want = true;
                        break;
                    }
                }
            }
            if (want != LISTENING.contains(p.getUUID())) {
                if (want) {
                    LISTENING.add(p.getUUID());
                } else {
                    LISTENING.remove(p.getUUID());
                }
                VoiceEars.sync(p);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LISTENING.remove(event.getEntity().getUUID());
        VoiceEars.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        LISTENING.clear();
        VoiceEars.clear();
    }
}
