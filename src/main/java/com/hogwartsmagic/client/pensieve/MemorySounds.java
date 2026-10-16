package com.hogwartsmagic.client.pensieve;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Every positioned sound a memory makes (scene {@code sound} events and the casts of {@link SpellVisuals}). A
 * memory is a recollection, not the moment: each sound is a little quieter and lower than the real thing, and is
 * followed a moment later by a faint copy of itself, like a sound heard down a long hall. The dive's own
 * non-positioned sounds are {@link PensieveSounds}.
 */
public final class MemorySounds {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final float VOLUME_SCALE = 0.75f;
    static final float PITCH_SCALE = 0.92f;
    static final int ECHO_DELAY_TICKS = 6;
    static final float ECHO_VOLUME_SCALE = 0.25f;

    private static final List<Echo> ECHOES = new ArrayList<>();
    private static final Set<String> WARNED = new HashSet<>();

    private MemorySounds() {
    }

    /** A scene sound event: {@code id} names a registered sound. Unknown ids are skipped with one warning. */
    public static void play(String id, Vec3 at, float volume, float pitch) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        SoundEvent event = rl == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(rl).orElse(null);
        if (event == null) {
            if (WARNED.add(String.valueOf(id))) LOGGER.warn("Pensieve: unknown sound '{}' in a memory", id);
            return;
        }
        play(event, at, volume, pitch);
    }

    /** Play {@code event} at {@code at} as a memory plays it: volume x0.75, pitch x0.92, then an echo. */
    public static void play(SoundEvent event, Vec3 at, float volume, float pitch) {
        float v = volume * VOLUME_SCALE;
        float p = pitch * PITCH_SCALE;
        start(event, at, v, p);
        ECHOES.add(new Echo(ECHO_DELAY_TICKS, event, at, v * ECHO_VOLUME_SCALE, p));
    }

    /** Advance the pending echoes one client tick. */
    static void tick() {
        if (ECHOES.isEmpty()) return;
        for (Iterator<Echo> it = ECHOES.iterator(); it.hasNext(); ) {
            Echo e = it.next();
            if (--e.ticks <= 0) {
                it.remove();
                start(e.event, e.at, e.volume, e.pitch);
            }
        }
    }

    /** Drop the echoes still waiting (the memory ended). Sounds already playing finish on their own. */
    static void clear() {
        ECHOES.clear();
    }

    private static void start(SoundEvent event, Vec3 at, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, volume,
                pitch, RandomSource.create(), at.x, at.y, at.z));
    }

    private static final class Echo {
        int ticks;
        final SoundEvent event;
        final Vec3 at;
        final float volume;
        final float pitch;

        Echo(int ticks, SoundEvent event, Vec3 at, float volume, float pitch) {
            this.ticks = ticks;
            this.event = event;
            this.at = at;
            this.volume = volume;
            this.pitch = pitch;
        }
    }
}
