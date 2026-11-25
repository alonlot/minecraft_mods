package com.hogwartsmagic.cosy;

/**
 * The life of one living Famous Wizard Card portrait: when the figure is in the frame, when it walks out (or plays
 * its exit clip), when it blinks and glances. Pure client-side maths with no saved state: everything is computed from
 * {@code (game time, card id, view seed)}, so the same card in two places is out of step but one view is stable from
 * frame to frame. No Minecraft classes, so JUnit covers it.
 *
 * <p>The year is cut into 30-minute "super slots" (shifted per view so views are out of step). Inside one super slot
 * the cycle chain restarts at its start: idle for 1200-3600 ticks, exit (40-tick walk, or the clip), absent for
 * 400-1200 ticks, a 40-tick walk back in, and so on. The last cycle is stretched so its walk back ends exactly at the
 * slot end, which keeps every idle gap inside 1200-3600 ticks, seams included. Costs about a dozen hashes per call however long the world has been running.
 */
public final class CardTimeline {
    /** The figure fully out of the frame, in portrait widths: a little over 1 so none of it is left in the window. */
    public static final float OFF_EDGE = 1.2F;
    public static final int WALK_TICKS = 40;
    public static final int CLIP_FPS = 12;

    private static final long SUPER_SLOT = 36000L;
    private static final int IDLE_MIN = 1200;
    private static final int IDLE_SPAN = 2401;   // 1200..3600
    private static final int IDLE_MAX = 3600;
    private static final int ABSENT_MAX = 1200;
    private static final int ABSENT_MIN = 400;
    private static final int ABSENT_SPAN = 801;  // 400..1200
    private static final long BLOCK = 2400L;

    /**
     * What the portrait shows. {@code present} is false only while the frame is wholly empty. {@code figureOffsetX} is
     * in portrait widths (negative = left); {@code look} is -1, 0 or 1; {@code clipFrame} is -1 when no clip plays.
     */
    public record State(boolean present, float figureOffsetX, boolean blink, int look, int clipFrame) {
        /** True when the figure sits still in the frame, so the eye overlays may draw. */
        public boolean idle() {
            return this.present && this.figureOffsetX == 0.0F && this.clipFrame < 0;
        }
    }

    private CardTimeline() {
    }

    /** How many ticks a clip of {@code frames} frames takes at {@link #CLIP_FPS}. */
    public static int clipTicks(int frames) {
        return frames <= 0 ? 0 : (frames * 20 + CLIP_FPS - 1) / CLIP_FPS;
    }

    public static State at(long gameTime, float partial, int cardId, long viewSeed, boolean legendaryClip, int clipFrames) {
        long seed = cardId * 31L + viewSeed;
        double t = gameTime + (double) partial;
        boolean clip = legendaryClip && clipFrames > 0;
        int exitTicks = clip ? clipTicks(clipFrames) : WALK_TICKS;

        long shift = Math.floorMod(mix(seed ^ 0x51ED270B3C5A9E11L), SUPER_SLOT);
        double ts = t + shift;
        long slot = (long) Math.floor(ts / SUPER_SLOT);
        double local = ts - slot * (double) SUPER_SLOT;
        long chain = mix(seed * 0x9E3779B97F4A7C15L + slot);

        // The chain is packed so the LAST cycle's return walk ends exactly at the slot's end: the next slot then opens
        // with a plain idle stretch, so no idle gap (even across a seam) leaves 1200..3600 ticks (60..180 s).
        long pos = 0;
        for (int i = 0; i < 64; i++) {
            long h = mix(chain + i * 0xD1B54A32D192ED03L);
            long space = SUPER_SLOT - pos - exitTicks - WALK_TICKS;      // what idle + absent may use up
            long idle = IDLE_MIN + Math.floorMod(h, IDLE_SPAN);
            long absent = ABSENT_MIN + Math.floorMod(h >>> 20, ABSENT_SPAN);
            boolean last = space <= IDLE_MAX + ABSENT_MAX;
            if (last) {
                long lo = Math.max(IDLE_MIN, space - ABSENT_MAX);
                long hi = Math.min(IDLE_MAX, space - ABSENT_MIN);
                idle = Math.max(lo, Math.min(hi, idle));
                absent = space - idle;
            } else {
                // leave room for one more whole cycle (at least the shortest idle + absent)
                idle = Math.min(idle, space - (IDLE_MIN + ABSENT_MIN) - absent - exitTicks - WALK_TICKS);
            }
            double exitStart = pos + idle;
            double absentStart = exitStart + exitTicks;
            double returnStart = absentStart + absent;
            double end = returnStart + WALK_TICKS;
            if (local < exitStart) {
                break; // idle before this exit
            }
            if (local < absentStart) {
                double p = local - exitStart;
                if (clip) {
                    int frame = Math.min(clipFrames - 1, (int) (p * CLIP_FPS / 20.0));
                    return new State(true, 0.0F, false, 0, frame);
                }
                float side = ((h >>> 41) & 1L) == 0 ? -1.0F : 1.0F;
                return new State(true, side * OFF_EDGE * ease((float) (p / WALK_TICKS)), false, 0, -1);
            }
            if (local < returnStart) {
                return new State(false, 0.0F, false, 0, -1);
            }
            if (local < end) {
                float side = ((h >>> 42) & 1L) == 0 ? -1.0F : 1.0F;
                float p = (float) ((local - returnStart) / WALK_TICKS);
                return new State(true, side * OFF_EDGE * (1.0F - ease(p)), false, 0, -1);
            }
            pos = (long) end;
            if (last) {
                break;
            }
        }
        // Idle: the eyes live.
        boolean blink = eventAt(seed ^ 0x2545F4914F6CDD1DL, t, 40, 80, 3, 3) >= 0;
        long glance = eventAt(seed ^ 0x7B6F0E1D3A59C2B7L, t, 100, 200, 20, 21);
        int look = glance < 0 ? 0 : (((glance >>> 40) & 1L) == 0 ? -1 : 1);
        return new State(true, 0.0F, blink, look, -1);
    }

    /** A smoothstep: rises monotonically from 0 to 1. */
    private static float ease(float p) {
        float c = Math.max(0.0F, Math.min(1.0F, p));
        return c * c * (3.0F - 2.0F * c);
    }

    /**
     * Events spaced {@code minGap + [0, gapSpan)} ticks apart (start to start) inside 2400-tick blocks, each lasting
     * {@code minDur + [0, durSpan)} ticks. Returns a hash of the event that {@code t} falls in, or -1 outside any.
     */
    private static long eventAt(long seed, double t, int minGap, int gapSpan, int minDur, int durSpan) {
        long block = (long) Math.floor(t / BLOCK);
        double local = t - block * (double) BLOCK;
        long base = mix(seed + block * 0x9E3779B97F4A7C15L);
        double pos = Math.floorMod(base, (long) (minGap + gapSpan));
        for (int i = 0; i < 128 && pos < BLOCK; i++) {
            long h = mix(base + (i + 1) * 0xBF58476D1CE4E5B9L);
            double dur = minDur + Math.floorMod(h >>> 8, (long) durSpan);
            if (local < pos) {
                return -1;
            }
            if (local < pos + dur) {
                return h & Long.MAX_VALUE;
            }
            pos += minGap + Math.floorMod(h >>> 24, (long) gapSpan);
        }
        return -1;
    }

    /** SplitMix64 finaliser. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
