package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Smoke flight (the film's Death Eaters streaking across the sky as black smoke): the {@code smoke} action turns an
 * actor into a trail of smoke until the next {@code unsmoke}. Pure maths, a function of the actor's actions, its path
 * and the scene clock, so every viewer (late joiners included) sees the same streak. While smoking the actor's model,
 * name tag, held items and shadow are not drawn; the client ({@code MemoryActors}, {@code SmokeTrailRenderer}) puts
 * dark smoke particles at it every tick and draws a tapering ribbon along its path over the last {@link #TRAIL_TICKS}.
 *
 * <p>The ribbon is sampled every {@link #STEP} ticks back from the actor ({@link #trail}); once the smoke ends it does
 * not vanish but stays where it was and fades over the same {@link #TRAIL_TICKS}, so the streak dies out behind a
 * Death Eater who has landed.
 */
public final class SmokeFlight {
    private SmokeFlight() {
    }

    public static final String ON = "smoke", OFF = "unsmoke";
    /** How far back the ribbon reaches (1 s), the spacing of its samples, and the most points it can have. */
    public static final float TRAIL_TICKS = 20f, STEP = 2f;
    public static final int MAX_POINTS = (int) (TRAIL_TICKS / STEP) + 1;
    /** Floats per trail point: x, y, z (scene-relative, at the body's middle) and the age in ticks. */
    public static final int STRIDE = 4;
    /** The height of the body's middle above the path, at scale 1. */
    public static final float BODY_MIDDLE = 0.9f;
    /** A step this long (blocks over {@link #STEP} ticks) is a cut, not flight: the ribbon stops there. */
    public static final float CUT_BLOCKS = 12f;

    /** When an actor smokes: the {@code [start, end)} periods in time order ({@code end} is infinity when open). */
    public static final class Timeline {
        private final float[] start, end;

        private Timeline(float[] start, float[] end) {
            this.start = start;
            this.end = end;
        }

        /** The timeline of these actions (any order, other actions ignored), or null when there is no {@code smoke}. */
        public static Timeline of(List<SceneDef.ActionCue> actions) {
            List<SceneDef.ActionCue> sorted = new ArrayList<>();
            for (SceneDef.ActionCue a : actions) {
                if (ON.equals(a.action()) || OFF.equals(a.action())) sorted.add(a);
            }
            sorted.sort(Comparator.comparingDouble(SceneDef.ActionCue::t));
            List<float[]> periods = new ArrayList<>();
            float[] open = null;
            for (SceneDef.ActionCue a : sorted) {
                if (ON.equals(a.action())) {
                    if (open == null) {
                        open = new float[]{a.t(), Float.POSITIVE_INFINITY};
                        periods.add(open);
                    }
                } else if (open != null) {
                    open[1] = a.t();
                    open = null;
                }
            }
            if (periods.isEmpty()) return null;
            float[] s = new float[periods.size()], e = new float[periods.size()];
            for (int i = 0; i < s.length; i++) {
                s[i] = periods.get(i)[0];
                e[i] = periods.get(i)[1];
            }
            return new Timeline(s, e);
        }

        /** Whether the actor is smoke at scene time {@code t}: from a {@code smoke} until the next {@code unsmoke}. */
        public boolean on(float t) {
            int i = index(t);
            return i >= 0 && t < end[i];
        }

        /** The index of the last period that has started by {@code t}, or -1. */
        private int index(float t) {
            int found = -1;
            for (int i = 0; i < start.length; i++) {
                if (start[i] <= t) found = i;
                else break;
            }
            return found;
        }

        /** How many periods there are. */
        public int periods() {
            return start.length;
        }
    }

    /**
     * The ribbon at scene time {@code t}: the actor's path sampled every {@link #STEP} ticks back from where its smoke
     * last was (now, or when it ended), no further than {@link #TRAIL_TICKS} before {@code t} and no earlier than the
     * start of that smoke, written into {@code out} as {@link #STRIDE} floats a point, newest first (x, y, z at the
     * body's middle, scene-relative; the age in ticks, 0 at the head of a running smoke). Returns the point count: 0
     * when the actor is not smoke and was not within {@link #TRAIL_TICKS}, and a path with a cut (a jump over
     * {@link #CUT_BLOCKS}) ends the ribbon there. {@code out} needs {@link #MAX_POINTS} x {@link #STRIDE} floats.
     */
    public static int trail(SceneDef.ActorDef def, Timeline timeline, float t, float[] out) {
        if (timeline == null) return 0;
        int i = timeline.index(t);
        if (i < 0) return 0;
        float head = Math.min(t, timeline.end[i]);
        float tail = Math.max(timeline.start[i], t - TRAIL_TICKS);
        if (head - tail < 1e-3f && head < t) return 0;       // ended and gone
        if (t - head >= TRAIL_TICKS) return 0;
        int n = 0;
        float lift = BODY_MIDDLE * def.scale();
        float px = 0, py = 0, pz = 0;
        for (int k = 0; k < MAX_POINTS; k++) {
            float s = head - k * STEP;
            if (s < tail - 1e-3f) break;
            float[] p = Vehicle.path(def, s);
            if (p == null || p.length < 3) break;
            float x = p[0], y = p[1] + lift, z = p[2];
            if (n > 0) {
                float dx = x - px, dy = y - py, dz = z - pz;
                if (dx * dx + dy * dy + dz * dz > CUT_BLOCKS * CUT_BLOCKS) break;
            }
            out[n * STRIDE] = x;
            out[n * STRIDE + 1] = y;
            out[n * STRIDE + 2] = z;
            out[n * STRIDE + 3] = t - s;
            px = x;
            py = y;
            pz = z;
            n++;
        }
        return n;
    }

    /** The share of full width and strength a ribbon point of this age has: 1 at the head, 0 at {@link #TRAIL_TICKS}. */
    public static float taper(float age) {
        float k = 1f - age / TRAIL_TICKS;
        return k <= 0f ? 0f : k >= 1f ? 1f : k;
    }
}
