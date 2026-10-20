package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.CueKinds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The soundtrack and spoken lines of a memory (scene {@code music} and {@code voice} events). Unlike
 * {@link MemorySounds} they are not "heard down a hall": no pitch or volume scaling, no echo, and no position, so
 * they never fade as the directed camera moves. Both are non-positional tickable instances.
 *
 * <p>While any voice plays, every music instance eases down to {@link #DUCK_LEVEL} of its volume and back up
 * after, {@link #DUCK_STEP} per tick. When the memory ends music fades out over {@link #FADE_TICKS} ticks and
 * voices stop at once ({@link #cut()}). While a music instance is playing the vanilla music track is held off.
 * Cue files carry their own fades; a cue is played whole from its {@code t} (an OGG cannot be sought into, so a late
 * joiner skips cues that are already past: that rule lives in {@link MemoryEvents}).
 */
public final class MemoryAudio {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Music volume multiplier while a voice speaks. */
    static final float DUCK_LEVEL = 0.45f;
    /** How far the duck multiplier moves per tick. */
    static final float DUCK_STEP = 0.08f;
    /** Ticks a music instance takes to fade out when the memory ends. */
    static final int FADE_TICKS = 20;
    /** A just-started instance is not judged finished until it has had this many ticks to register as active. */
    private static final int START_GRACE = 10;

    private static final List<Cue> MUSIC = new ArrayList<>();
    private static final List<Cue> VOICES = new ArrayList<>();
    /** Scene {@code sfx} cues: plain non-positional one-shots on the ambient channel, never ducking music. */
    private static final List<Cue> SFX = new ArrayList<>();
    private static final Set<String> WARNED = new HashSet<>();
    private static int clock;
    private static boolean ducking;

    private MemoryAudio() {
    }

    /** Start a soundtrack cue. Unknown ids are skipped with one warning. */
    static void playMusic(String id, float volume) {
        SoundEvent event = lookup(id);
        if (event == null) return;
        MusicInstance m = new MusicInstance(event, volume);
        MUSIC.add(new Cue(m, clock));
        Minecraft.getInstance().getSoundManager().play(m);
    }

    /** Start a spoken line. Unknown ids are skipped with one warning. */
    static void playVoice(String id, float volume) {
        SoundEvent event = lookup(id);
        if (event == null) return;
        VoiceInstance v = new VoiceInstance(event, volume);
        VOICES.add(new Cue(v, clock));
        Minecraft.getInstance().getSoundManager().play(v);
    }

    /** Start a non-positional sound effect (scene {@code sfx}). Unknown ids are skipped with one warning. */
    static void playSfx(String id, float volume) {
        SoundEvent event = lookup(id);
        if (event == null) return;
        SfxInstance s = new SfxInstance(event, volume);
        SFX.add(new Cue(s, clock));
        Minecraft.getInstance().getSoundManager().play(s);
    }

    /** The live cues of an event kind, for the kinds that are cut at the end of a memory. */
    private static List<Cue> cutList(String kind) {
        if (!CueKinds.stopsAtEnd(kind)) return List.of();
        return "sfx".equals(kind) ? SFX : VOICES;
    }

    /**
     * The memory, or one scene of a chain, ended: voices stop at once, music fades out over {@link #FADE_TICKS} ticks
     * and then stops. A chained scene's music may still be fading under the next scene's opening.
     */
    static void cut() {
        for (String kind : new String[]{"voice", "sfx"}) {
            for (Cue c : cutList(kind)) Minecraft.getInstance().getSoundManager().stop(c.sound);
            cutList(kind).clear();
        }
        ducking = false;
        for (Cue c : MUSIC) ((MusicInstance) c.sound).fadeOut();
    }

    /** Stop everything this instant (a new memory is starting; nothing of the old one may linger). */
    static void stopNow() {
        for (Cue c : VOICES) Minecraft.getInstance().getSoundManager().stop(c.sound);
        for (Cue c : SFX) Minecraft.getInstance().getSoundManager().stop(c.sound);
        for (Cue c : MUSIC) Minecraft.getInstance().getSoundManager().stop(c.sound);
        VOICES.clear();
        SFX.clear();
        MUSIC.clear();
        ducking = false;
    }

    /** One client tick, run whether or not a memory is on: tracks what is still playing, and holds off vanilla music. */
    static void tick() {
        clock++;
        if (MUSIC.isEmpty() && VOICES.isEmpty() && SFX.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        prune(VOICES, mc);
        prune(SFX, mc);
        prune(MUSIC, mc);
        ducking = !VOICES.isEmpty();
        if (!MUSIC.isEmpty()) mc.getMusicManager().stopPlaying();
    }

    /** True while a voice line is playing: music eases down. */
    static boolean ducking() {
        return ducking;
    }

    private static void prune(List<Cue> cues, Minecraft mc) {
        cues.removeIf(c -> clock - c.started > START_GRACE
                && (c.sound.isStopped() || !mc.getSoundManager().isActive(c.sound)));
    }

    private static SoundEvent lookup(String id) {
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        SoundEvent event = rl == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(rl).orElse(null);
        if (event == null && WARNED.add(String.valueOf(id))) LOGGER.warn("Pensieve: unknown sound '{}' in a memory", id);
        return event;
    }

    private record Cue(AbstractTickableSoundInstance sound, int started) {
    }

    /** One soundtrack cue: non-positional, ducked under voices, faded out on request. */
    private static final class MusicInstance extends AbstractTickableSoundInstance {
        private final float base;
        private float duck = 1f;
        private int fadeTicks = -1;

        MusicInstance(SoundEvent event, float volume) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.base = volume;
            this.volume = volume;
            this.pitch = 1f;
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        void fadeOut() {
            if (fadeTicks < 0) fadeTicks = 0;
        }

        @Override
        public void tick() {
            float target = ducking() ? DUCK_LEVEL : 1f;
            if (duck < target) duck = Math.min(target, duck + DUCK_STEP);
            else if (duck > target) duck = Math.max(target, duck - DUCK_STEP);
            float fade = 1f;
            if (fadeTicks >= 0) {
                fadeTicks++;
                if (fadeTicks >= FADE_TICKS) {
                    stop();
                    return;
                }
                fade = 1f - fadeTicks / (float) FADE_TICKS;
            }
            this.volume = base * duck * fade;
        }
    }

    /** One spoken line: non-positional, full pitch and volume. */
    private static final class VoiceInstance extends AbstractTickableSoundInstance {
        VoiceInstance(SoundEvent event, float volume) {
            super(event, SoundSource.VOICE, RandomSource.create());
            this.volume = volume;
            this.pitch = 1f;
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        @Override
        public void tick() {
        }
    }

    /** One sound effect: non-positional, full pitch, no echo, no ducking. */
    private static final class SfxInstance extends AbstractTickableSoundInstance {
        SfxInstance(SoundEvent event, float volume) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.volume = volume;
            this.pitch = 1f;
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        @Override
        public void tick() {
        }
    }
}
