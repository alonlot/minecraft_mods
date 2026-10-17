package com.hogwartsmagic.client.sorting;

import com.hogwartsmagic.ExampleMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * Plays the Sorting Hat's spoken lines: non-positional on the voice channel, one at a time. A line that arrives while
 * another is playing waits for it to finish (the server sends a reaction and the next question back to back); the
 * greeting of a new quiz cuts everything and starts over. An unknown sound id is logged at debug level and ignored.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HatVoice {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String GREETING = "sorting.voice.greeting";
    private static final String REACTION = "sorting.voice.react";
    private static final Set<String> SHOUTS = Set.of("sorting.voice.gryffindor", "sorting.voice.ravenclaw",
            "sorting.voice.hufflepuff", "sorting.voice.slytherin");
    /** Ticks after a line starts before a missing handle counts as finished (the engine registers it a tick or two late). */
    private static final int START_GRACE = 5;

    private static final Deque<SoundEvent> queue = new ArrayDeque<>();
    private static SoundInstance current;
    private static SoundEvent currentEvent;
    private static int age;

    private HatVoice() {
    }

    public static void play(String soundId) {
        if (soundId == null || soundId.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        SoundEvent event = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
        if (event == null) {
            LOGGER.debug("Sorting Hat voice line '{}' is not a registered sound", soundId);
            return;
        }
        String path = event.getLocation().getPath();
        if (path.equals(GREETING)) {
            stop();
        } else if (SHOUTS.contains(path)) {
            // The sorted shout lands at once: nothing else is worth hearing any more.
            if (current != null && !SHOUTS.contains(currentEvent.getLocation().getPath())) stop();
            queue.clear();
        } else if (path.startsWith(REACTION)) {
            // A fast reader must not build a backlog: only the newest reaction and question wait.
            queue.clear();
        } else {
            queue.removeIf(e -> !e.getLocation().getPath().startsWith(REACTION));
        }
        if (current == null) {
            start(event);
        } else {
            queue.add(event);
        }
    }

    /** Cut the line that is playing and drop the waiting ones. */
    public static void stop() {
        queue.clear();
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
            currentEvent = null;
        }
    }

    /** The quiz screen was closed by the server: silence the hat, unless a house shout is still to be heard. */
    public static void closed() {
        boolean shout = currentEvent != null && SHOUTS.contains(currentEvent.getLocation().getPath());
        SoundEvent queued = null;
        for (SoundEvent e : queue) {
            if (SHOUTS.contains(e.getLocation().getPath())) queued = e;
        }
        if (shout) {
            queue.clear();
        } else if (queued != null) {
            SoundEvent keep = queued;
            stop();
            start(keep);
        } else {
            stop();
        }
    }

    private static void start(SoundEvent event) {
        SoundInstance instance = new SimpleSoundInstance(event.getLocation(), SoundSource.VOICE, 1.0f, 1.0f,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        current = instance;
        currentEvent = event;
        age = 0;
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || current == null) return;
        if (++age < START_GRACE || Minecraft.getInstance().getSoundManager().isActive(current)) return;
        current = null;
        currentEvent = null;
        SoundEvent next = queue.poll();
        if (next != null) start(next);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }
}
