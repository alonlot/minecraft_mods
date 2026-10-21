package com.hogwartsmagic.pensieve.scene;

/** Which scene event kinds are soundtrack-like cues and what happens to them when a memory ends. Pure. */
public final class CueKinds {
    private CueKinds() {
    }

    /** True for cues that stop at once when the memory ends ({@code voice}, {@code sfx}); music fades out instead. */
    public static boolean stopsAtEnd(String kind) {
        return "voice".equals(kind) || "sfx".equals(kind);
    }

    /** Ticks a looping sfx takes to fade out when it is stopped (its duration ran out, or the scene ended). */
    public static final int LOOP_FADE_TICKS = 20;

    /**
     * Problems with an sfx cue's {@code loop} and {@code duration}: a duration only means something on a loop (a plain
     * cue plays whole), it cannot be negative, and a looping cue may not run past the end of the scene. Pure.
     */
    public static java.util.List<String> loopProblems(String path, boolean loop, int duration, float t, float length) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!loop && duration != 0) out.add(path + ".duration: an sfx cue only takes a duration together with \"loop\": true");
        if (duration < 0) out.add(path + ".duration: " + duration + " is negative");
        if (loop && duration > 0 && t + duration > length) {
            out.add(path + ".duration: the loop would run to tick " + (t + duration) + ", past the scene's end at " + length);
        }
        return out;
    }

    /** Volume multiplier of a fading loop: 1 at the start of the fade, 0 after {@code total} ticks. Pure. */
    public static float loopFade(int ticksIntoFade, int total) {
        if (ticksIntoFade <= 0) return 1f;
        return ticksIntoFade >= total ? 0f : 1f - ticksIntoFade / (float) total;
    }
}
