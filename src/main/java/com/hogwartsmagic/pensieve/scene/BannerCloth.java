package com.hogwartsmagic.pensieve.scene;

/**
 * Pure maths for a {@code memory_banner_<design>} actor: a tall cloth banner hanging from a rod, swaying very gently
 * in the still air of a hall. At scale 1 it is {@link #WIDTH} wide and {@link #HEIGHT} tall, the point included.
 *
 * <p>Banner frame: x across (-WIDTH/2 to +WIDTH/2), y up (0 at the rod, -HEIGHT at the tip), z out of the cloth
 * toward the front (the side the art reads from, which faces the actor's yaw). A cloth point is named by
 * {@code u} (0 to 1 across, left to right seen from the front) and {@code s} (0 at the hanging top edge, 1 at the
 * foot). {@link #offset} moves it from its flat rest position: a slow swing out of the plane growing toward the foot,
 * a slower sideways drift, a soft ripple running across and down the cloth, and the small lift a swinging pendulum
 * makes. The top edge ({@code s = 0}) never moves.
 *
 * <p>Deterministic from a seed and the time, so every viewer sees the same sway. Each banner has its own periods
 * and phases from its seed, so a row of banners never sways in step. The drawing is
 * {@code client/MemoryBannerRenderer}.
 */
public final class BannerCloth {
    public static final float WIDTH = 2f, HEIGHT = 8f;
    /** The cloth mesh: quads across and down. */
    public static final int COLS = 6, ROWS = 32;
    /** Largest swing out of the plane at the foot, in blocks at scale 1 (two sines, never both at full). */
    public static final float SWING = 0.26f;
    /** Largest sideways drift at the foot, in blocks. */
    public static final float DRIFT = 0.07f;
    /** Largest ripple out of the plane, in blocks (grows with s). */
    public static final float RIPPLE = 0.05f;
    /** Upper bound of the whole offset in any direction (see the tests). */
    public static final float MAX_OFFSET = SWING + DRIFT + RIPPLE;

    /** Indices into a {@link #params} array. */
    private static final int W1 = 0, W2 = 1, W3 = 2, W4 = 3, P1 = 4, P2 = 5, P3 = 6, P4 = 7;

    private BannerCloth() {
    }

    /**
     * Periods and phases for one banner: swing 5 to 7 s, a second swing 2.8 to 3.8 s, ripple 3 to 4.5 s, drift
     * 8 to 11 s, as angular speeds (radians per second), then four phases.
     */
    public static float[] params(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        float[] p = new float[8];
        float[][] periods = {{5f, 7f}, {2.8f, 3.8f}, {3f, 4.5f}, {8f, 11f}};
        for (int i = 0; i < 4; i++) {
            h = mix(h + i);
            float period = periods[i][0] + (periods[i][1] - periods[i][0]) * unit(h);
            p[i] = 6.2831855f / period;
        }
        for (int i = 4; i < 8; i++) {
            h = mix(h + i);
            p[i] = 6.2831855f * unit(h);
        }
        return p;
    }

    /**
     * The offset {dx, dy, dz} in blocks (scale 1, banner frame) of the cloth point at {@code u, s} at time
     * {@code t} seconds, for a banner with these {@link #params}. Zero along the top edge.
     */
    public static float[] offset(float[] p, float u, float s, float t) {
        s = Math.max(0f, Math.min(1f, s));
        u = Math.max(0f, Math.min(1f, u));
        // the swing: stiff near the rod, freest at the foot; the lower cloth lags the upper a little
        float reach = (float) Math.pow(s, 1.6);
        float swing = 0.65f * (float) Math.sin(p[W1] * t + p[P1] - 1.1f * s)
                + 0.35f * (float) Math.sin(p[W2] * t + p[P2] - 2.2f * s);
        float dz = SWING * reach * swing;
        // a soft ripple across the width that travels down the cloth
        dz += RIPPLE * s * (float) Math.sin(6.2831855f * (u - 0.5f) * 0.9f + p[W3] * t + p[P3] - 4f * s)
                * (0.6f + 0.4f * (float) Math.sin(p[W1] * 0.5f * t + p[P4]));
        // a slow sideways drift, a little more at the outer edges than the middle (the cloth twists slightly)
        float dx = DRIFT * s * s * ((float) Math.sin(p[W4] * t + p[P4]) * (0.8f + 0.2f * (u - 0.5f) * 2f));
        // a pendulum rises as it swings: the lift the swing and drift make, at most a few centimetres
        float arm = Math.max(0.5f, s * HEIGHT);
        float dy = (dz * dz + dx * dx) / (2f * arm);
        return new float[]{dx, dy, dz};
    }

    /** A seed from an actor's id (its Pensieve UUID bits): the same id always sways the same way. */
    public static long seed(long mostBits, long leastBits) {
        return mix(mostBits ^ (leastBits * 0xC2B2AE3D27D4EB4FL));
    }

    /** SplitMix64 finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, 1) from the top 24 bits. */
    private static float unit(long h) {
        return (h >>> 40) / (float) (1 << 24);
    }
}
