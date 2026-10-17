package com.hogwartsmagic.client.sorting;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

/**
 * Plays the Sorting Hat's spoken lines: non-positional on the voice channel, one line at a time (a new line cuts
 * the previous one). An unknown sound id is logged at debug level and ignored.
 */
public final class HatVoice {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static SoundInstance current;

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
        stop();
        SoundInstance instance = new SimpleSoundInstance(event.getLocation(), SoundSource.VOICE, 1.0f, 1.0f,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        current = instance;
        Minecraft.getInstance().getSoundManager().play(instance);
    }

    /** Cut the line that is playing, if any. */
    public static void stop() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }
}
