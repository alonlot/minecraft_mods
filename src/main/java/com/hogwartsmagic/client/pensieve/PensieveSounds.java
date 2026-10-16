package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The dive's sounds, by their final names ({@code pensieve.swallow}, {@code fall}, {@code void_hum}, {@code rise},
 * {@code gasp}, {@code distant_thud}). Each name maps to its {@link ModSounds}
 * event in {@link #event}.
 */
public final class PensieveSounds {
    private static Loop fall;
    private static Loop hum;

    private PensieveSounds() {
    }

    static SoundEvent event(String name) {
        if ("swallow".equals(name)) return ModSounds.PENSIEVE_SWALLOW.get();
        if ("fall".equals(name)) return ModSounds.PENSIEVE_FALL.get();
        if ("void_hum".equals(name)) return ModSounds.PENSIEVE_VOID_HUM.get();
        if ("rise".equals(name)) return ModSounds.PENSIEVE_RISE.get();
        if ("gasp".equals(name)) return ModSounds.PENSIEVE_GASP.get();
        if ("distant_thud".equals(name)) return ModSounds.PENSIEVE_DISTANT_THUD.get();
        return ModSounds.PENSIEVE_SWALLOW.get();
    }

    /** One-shot, not positioned: the dive happens inside the viewer's head. */
    public static void play(String name, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(event(name), pitch, volume));
    }

    public static void startFall() {
        stopFall();
        fall = new Loop(event("fall"), 1.0f, 0.6f);
        Minecraft.getInstance().getSoundManager().play(fall);
    }

    public static void stopFall() {
        if (fall != null) fall.fadeOut();
        fall = null;
    }

    /** The low hum under the whole memory; {@code false} fades it out. */
    public static void hum(boolean on) {
        if (on) {
            if (hum != null && !hum.isStopped() && !hum.fading) return;
            hum = new Loop(event("void_hum"), 1.0f, 0.5f);
            Minecraft.getInstance().getSoundManager().play(hum);
        } else if (hum != null) {
            hum.fadeOut();
            hum = null;
        }
    }

    public static void stopAll() {
        stopFall();
        hum(false);
    }

    /** A looping, non-positional sound that fades in and out instead of cutting. */
    static final class Loop extends AbstractTickableSoundInstance {
        private final float target;
        private boolean fading;

        Loop(SoundEvent sound, float pitch, float target) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.target = target;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public void tick() {
            if (fading) {
                volume -= target / 15f;
                if (volume <= 0.01f) stop();
            } else if (volume < target) {
                volume = Math.min(target, volume + target / 10f);
            }
            if (!fading && !PensieveClientState.active()) fading = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
