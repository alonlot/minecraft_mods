package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.portrait.FatLadyLines;
import com.hogwartsmagic.portrait.FatLadyPortraitEntity;
import com.hogwartsmagic.portrait.PortraitRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side of a talking portrait: watches every portrait near the player and, when its synced SERIAL changes,
 * starts that line exactly once: the voice at her position, the subtitle in the action bar (within 16 blocks) and
 * the animation. The first SERIAL seen for an entity (and any change in its first few ticks, while the spawn data
 * is still arriving) is only recorded, so walking up to her never replays an old line.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PortraitClient {
    private static final double WATCH_RANGE_SQR = 96.0 * 96.0;
    private static final double SUBTITLE_RANGE_SQR = 16.0 * 16.0;
    private static final int SETTLE_TICKS = 3;
    private static final long SUBTITLE_REFRESH_NANOS = 2_500_000_000L;

    private static final Map<Integer, PortraitAnim> ANIMS = new HashMap<>();
    private static int sweep;

    private PortraitClient() {
    }

    static PortraitAnim anim(FatLadyPortraitEntity entity) {
        return ANIMS.computeIfAbsent(entity.getId(), id -> new PortraitAnim());
    }

    static void clear() {
        ANIMS.values().forEach(PortraitClient::silence);
        ANIMS.clear();
        PortraitVideo.releaseAll();
    }

    private static void silence(PortraitAnim anim) {
        if (anim.sound != null) {
            Minecraft.getInstance().getSoundManager().stop(anim.sound);
            anim.sound = null;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            if (!ANIMS.isEmpty()) {
                clear();
            }
            return;
        }
        long now = System.nanoTime();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof FatLadyPortraitEntity portrait)) {
                continue;
            }
            if (portrait.distanceToSqr(player) <= WATCH_RANGE_SQR) {
                watch(minecraft, level, player, portrait, now);
            } else {
                PortraitAnim far = ANIMS.get(portrait.getId());
                if (far != null) {
                    far.haveSerial = false; // re-baseline on return so a line said while away never replays
                }
            }
        }
        if (++sweep >= 20) {
            sweep = 0;
            ANIMS.entrySet().removeIf(e -> {
                if (level.getEntity(e.getKey()) instanceof FatLadyPortraitEntity) {
                    return false;
                }
                silence(e.getValue()); // broken mid-line: the voice stops with her
                return true;
            });
            PortraitVideo.releaseIdle(now);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void watch(Minecraft minecraft, ClientLevel level, LocalPlayer player,
                              FatLadyPortraitEntity portrait, long now) {
        PortraitAnim anim = anim(portrait);
        anim.ticksSeen++;
        int serial = portrait.getSerial();
        if (!anim.haveSerial) {
            anim.haveSerial = true;
            anim.serial = serial;
            return;
        }
        if (serial != anim.serial) {
            anim.serial = serial;
            if (anim.ticksSeen > SETTLE_TICKS) {
                startLine(minecraft, level, player, portrait, anim, now);
            }
        }
        // a long line outlasts the action bar's three seconds: keep it up while she talks
        FatLadyLines.Line line = anim.line;
        if (line != null && now >= anim.nextSubtitleNs && anim.lineTime(now) < line.seconds()
                && portrait.distanceToSqr(player) <= SUBTITLE_RANGE_SQR) {
            subtitle(player, line);
            anim.nextSubtitleNs = now + SUBTITLE_REFRESH_NANOS;
        }
    }

    private static void startLine(Minecraft minecraft, ClientLevel level, LocalPlayer player,
                                  FatLadyPortraitEntity portrait, PortraitAnim anim, long now) {
        FatLadyLines.Line line = FatLadyLines.get(portrait.getLineIndex());
        if (line == null) {
            return;
        }
        if (anim.sound != null) {
            minecraft.getSoundManager().stop(anim.sound); // an interruption cuts the old line off
            anim.sound = null;
        }
        SoundEvent voice = PortraitRegistry.soundFor(line);
        if (voice != null) {
            SimpleSoundInstance instance = new SimpleSoundInstance(voice, SoundSource.VOICE, 1.4f, 1.0f,
                    level.getRandom(), portrait.getX(), portrait.getY() + 0.5, portrait.getZ());
            minecraft.getSoundManager().play(instance);
            anim.sound = instance;
        }
        PortraitAssets.Clip clip = line.isVideo() ? PortraitAssets.clip(line.clip()) : null;
        if (clip != null) {
            PortraitVideo.prime(line.clip(), clip, now);
        }
        anim.begin(line, PortraitAssets.envelope(line.key()), clip, now);
        if (portrait.distanceToSqr(player) <= SUBTITLE_RANGE_SQR) {
            subtitle(player, line);
            anim.nextSubtitleNs = now + SUBTITLE_REFRESH_NANOS;
        }
    }

    private static void subtitle(LocalPlayer player, FatLadyLines.Line line) {
        player.displayClientMessage(Component.literal("The Fat Lady: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\"" + line.text() + "\"").withStyle(ChatFormatting.WHITE)), true);
    }
}
