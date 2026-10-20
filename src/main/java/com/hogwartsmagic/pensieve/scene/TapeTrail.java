package com.hogwartsmagic.pensieve.scene;

/**
 * The shape of the {@code memory_tape} actor: a cloth measuring tape whose free end is the actor and whose body
 * trails behind it along the actor's own recent path, the way the Fiendfyre serpent's body follows its head. Unlike
 * the serpent it is not remembered frame to frame: it is read back off the path track, so it is a pure function of
 * the scene time and a late joiner sees the same tape. Whatever length the path behind does not cover (the path has
 * just begun, or the tape holds still) hangs straight down from the last point. Drawn by
 * {@code client/MemoryTapeRenderer}.
 */
public final class TapeTrail {
    /** The tape's length at actor {@code scale} 1, in blocks. */
    public static final float LENGTH = 1.5f;
    /** Points along the drawn tape. */
    public static final int POINTS = 32;
    /**
     * How finely the path is read back, in ticks, and how far back at most (3 s): a tape that has held still that
     * long hangs, and one moving slower than {@code length} per 3 s droops at its tail.
     */
    private static final float STEP_TICKS = 0.25f;
    private static final int MAX_STEPS = 240;

    private TapeTrail() {
    }

    /**
     * {@code points} points (at least 2) evenly spaced along a tape of {@code length} blocks, relative to the head
     * (point 0 is {0, 0, 0}): back along {@code path} from scene time {@code t}, then down.
     */
    public static float[][] trail(Track path, float t, float length, int points) {
        int n = Math.max(2, points);
        float[][] out = new float[n][];
        out[0] = new float[]{0, 0, 0};
        float seg = length / (n - 1);
        float[] head = path.isEmpty() ? null : path.sample(t);
        // Walk back in small time steps, emitting a point each time another seg of arc is used up.
        float[] prev = {0, 0, 0};
        float carried = 0;      // arc since the last emitted point
        int next = 1;
        if (head != null && head.length >= 3) {
            float firstKey = path.keys().get(0).t();
            for (int s = 1; s <= MAX_STEPS && next < n; s++) {
                float bt = t - s * STEP_TICKS;
                if (bt < firstKey - STEP_TICKS) break;
                float[] p = path.sample(bt);
                float[] cur = {p[0] - head[0], p[1] - head[1], p[2] - head[2]};
                float d = dist(prev, cur);
                while (d > 0 && carried + d >= seg && next < n) {
                    float k = (seg - carried) / d;
                    float[] q = lerp(prev, cur, k);
                    out[next++] = q;
                    d = dist(q, cur);
                    prev = q;
                    carried = 0;
                }
                carried += d;
                prev = cur;
            }
        }
        // The rest hangs down from where the path ran out.
        float y = prev[1];
        float left = seg - carried;
        while (next < n) {
            y -= left;
            out[next++] = new float[]{prev[0], y, prev[2]};
            left = seg;
        }
        return out;
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float[] lerp(float[] a, float[] b, float k) {
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }
}
