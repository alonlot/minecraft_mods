package com.hogwartsmagic.pensieve.scene;

/**
 * Which chunks of a {@link Crowd} the renderer rebuilds this frame. Pure, so the frame's work is tested. A chunk (a
 * cell of nearby spectators, {@link Crowd#chunks}) keeps its GPU mesh until its key changes; the key is made of:
 * <ul>
 *   <li>the level of detail of each of its spectators, and the light read at their heads;</li>
 *   <li>for the chunks nearest the camera (within {@link #EVERY_FRAME_RANGE}, at most {@link #EVERY_FRAME_FIGURES}
 *       spectators between them): the frame while a reaction moves them, so close figures jump and wave smoothly, and
 *       the tick while they are idle;</li>
 *   <li>for any other chunk with full-detail figures (they sway and turn their heads all the time): the tick while a
 *       reaction moves them, every {@link #IDLE_FULL_PERIOD} ticks while idle (the sway is a few seconds long);</li>
 *   <li>for a chunk with a reaction under way (some spectator between its onset and its letting go): its pose step, every
 *       {@link #MID_PERIOD} ticks in two-box range, every {@link #FAR_PERIOD} beyond;</li>
 *   <li>otherwise nothing: an idle two-box or strip figure is drawn still ({@link Crowd#pose} with {@code still}), so
 *       its mesh does not change and the chunk is not rebuilt or re-uploaded. The key's change when a reaction lets
 *       go makes one last rebuild, back to the idle pose.</li>
 * </ul>
 * {@link #plan} lists the chunks to rebuild, the nearest first; the renderer rebuilds as many as its budget allows and
 * marks each with {@link #built}.
 */
public final class CrowdSchedule {
    public static final float EVERY_FRAME_RANGE = 16f;
    public static final int EVERY_FRAME_FIGURES = 150;
    public static final int MID_PERIOD = 2, FAR_PERIOD = 5, IDLE_FULL_PERIOD = 4;
    /** The chunk cell (blocks) and the most spectators in one chunk. */
    public static final float CHUNK_CELL = 12f;
    public static final int CHUNK_MAX = 96;

    public final Crowd crowd;
    public final int[][] chunks;
    private final float[] chunkX, chunkY, chunkZ, chunkR, near;
    private final int[] stagger, order, list;
    private final long[] built, want, lightSig;
    private final boolean[] everyFrame;
    /** Per chunk and reaction: the earliest onset and the latest let-go of its spectators. */
    private final float[][] window;
    private final float[] d2;
    private final float[] cut = new float[2];
    private final int[] bins = new int[96];
    private int cursor;
    /** This frame's level-of-detail counts. */
    public int full, mid, strip;

    public CrowdSchedule(Crowd crowd) {
        this.crowd = crowd;
        this.chunks = crowd.chunks(CHUNK_CELL, CHUNK_MAX);
        int nc = chunks.length;
        chunkX = new float[nc];
        chunkY = new float[nc];
        chunkZ = new float[nc];
        chunkR = new float[nc];
        near = new float[nc];
        stagger = new int[nc];
        order = new int[nc];
        list = new int[nc];
        built = new long[nc];
        want = new long[nc];
        lightSig = new long[nc];
        everyFrame = new boolean[nc];
        java.util.Arrays.fill(built, Long.MIN_VALUE);
        int nr = crowd.reactionCount();
        window = new float[nc][2 * nr];
        for (int c = 0; c < nc; c++) {
            float sx = 0, sy = 0, sz = 0;
            for (int i : chunks[c]) {
                sx += crowd.x(i);
                sy += crowd.y(i);
                sz += crowd.z(i);
            }
            int m = Math.max(1, chunks[c].length);
            chunkX[c] = sx / m;
            chunkY[c] = sy / m + 1f;
            chunkZ[c] = sz / m;
            float r = 0;
            for (int i : chunks[c]) {
                float dx = crowd.x(i) - chunkX[c], dy = crowd.y(i) + 1f - chunkY[c], dz = crowd.z(i) - chunkZ[c];
                r = Math.max(r, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            chunkR[c] = r + 1f;
            stagger[c] = (c * 7) % 10;
            for (int q = 0; q < nr; q++) {
                float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
                for (int i : chunks[c]) {
                    float o = crowd.onset(q, i);
                    if (o != o) continue;
                    lo = Math.min(lo, o);
                    hi = Math.max(hi, o + crowd.span(q));
                }
                window[c][2 * q] = lo;
                window[c][2 * q + 1] = hi;
            }
        }
        d2 = new float[crowd.size()];
    }

    /** This frame's squared level-of-detail cut-offs {@code {full, mid}} (after {@link #plan}). */
    public float[] cut() {
        return cut;
    }

    /** Set chunk {@code c}'s light signature (a hash of the light at its heads); a change makes it rebuild. */
    public void light(int c, long signature) {
        lightSig[c] = signature;
    }

    /**
     * Plan the frame at scene time {@code now} (frame number {@code frame}), camera {@code (cx, cy, cz)} scene-relative:
     * returns how many chunks to rebuild, listed by {@link #chunk}, every-frame chunks first.
     */
    public int plan(float now, long frame, float cx, float cy, float cz) {
        int n = crowd.size();
        for (int i = 0; i < n; i++) {
            float dx = crowd.x(i) - cx, dy = crowd.y(i) + 1f - cy, dz = crowd.z(i) - cz;
            d2[i] = dx * dx + dy * dy + dz * dz;
        }
        Crowd.lodCutoffs(d2, n, CrowdMesh.FULL_RANGE, CrowdMesh.MID_RANGE, CrowdMesh.MAX_FULL, CrowdMesh.MAX_MID, bins, cut);
        full = mid = strip = 0;
        int nc = chunks.length;
        for (int c = 0; c < nc; c++) {
            float dx = chunkX[c] - cx, dy = chunkY[c] - cy, dz = chunkZ[c] - cz;
            near[c] = Math.max(0f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - chunkR[c]);
            everyFrame[c] = false;
        }
        // The nearest chunks, closest first, until the every-frame figures are used up.
        int cand = 0;
        for (int c = 0; c < nc; c++) {
            if (near[c] > EVERY_FRAME_RANGE) continue;
            int j = cand++;
            while (j > 0 && near[order[j - 1]] > near[c]) {
                order[j] = order[j - 1];
                j--;
            }
            order[j] = c;
        }
        int figures = 0;
        for (int k = 0; k < cand; k++) {
            int c = order[k];
            if (figures + chunks[c].length > EVERY_FRAME_FIGURES && figures > 0) break;
            figures += chunks[c].length;
            everyFrame[c] = true;
        }
        int count = 0;
        // Every-frame chunks lead the list, then the rest from a rotating start so a budget cut spreads fairly.
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < nc; k++) {
                int c = (k + cursor) % nc;
                if (everyFrame[c] != (pass == 0)) continue;
                long lod = 0;
                boolean hasFull = false;
                for (int i : chunks[c]) {
                    int band = d2[i] <= cut[0] ? 0 : d2[i] <= cut[1] ? 1 : 2;
                    if (band == 0) full++;
                    else if (band == 1) mid++;
                    else strip++;
                    hasFull |= band == 0;
                    lod = lod * 3 + band + 1;
                }
                long base = Crowd.mix(lod ^ Crowd.mix(lightSig[c]));
                boolean moves = moving(c, now);
                long key;
                if (everyFrame[c] && moves) {
                    key = Crowd.mix(base + 1) + frame;
                } else if (everyFrame[c] || (hasFull && moves)) {
                    key = Crowd.mix(base + 2) + (long) Math.floor(now + stagger[c]);
                } else if (hasFull) {
                    // Idle full figures only sway and turn their heads, slowly: a step every IDLE_FULL_PERIOD ticks.
                    key = Crowd.mix(base + 5) + (long) Math.floor((now + stagger[c]) / IDLE_FULL_PERIOD);
                } else if (moves) {
                    int period = near[c] * near[c] <= cut[1] ? MID_PERIOD : FAR_PERIOD;
                    key = Crowd.mix(base + 3) + (long) Math.floor((now + stagger[c]) / period);
                } else {
                    key = Crowd.mix(base + 4);
                }
                want[c] = key;
                if (key != built[c]) list[count++] = c;
            }
        }
        cursor++;
        return count;
    }

    /** The {@code k}th chunk to rebuild this frame. */
    public int chunk(int k) {
        return list[k];
    }

    /** Whether chunk {@code c} is one of this frame's every-frame chunks. */
    public boolean everyFrame(int c) {
        return everyFrame[c];
    }

    /** Mark chunk {@code c} rebuilt for this frame's key. */
    public void built(int c) {
        built[c] = want[c];
    }

    /** Whether any spectator of chunk {@code c} is inside a reaction's window at {@code now}. */
    public boolean moving(int c, float now) {
        float[] w = window[c];
        for (int q = 0; q < w.length; q += 2) {
            if (w[q] <= now && now <= w[q + 1]) return true;
        }
        return false;
    }
}
