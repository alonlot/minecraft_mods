package com.hogwartsmagic.spell.disable;

/**
 * The beats of the Incarcerous rope animation, in ticks since the bind arrived. Kept free of Minecraft
 * imports so the client renderer, the bound-body pose and the tests all read the same clock.
 *
 * <p>Each rope streams out of the caster's wand (FLIGHT), then its head snakes round the body (WRAP), each
 * coil landing loose and springing tight behind it. The torso rope goes first and the leg rope follows
 * {@link #LEG_DELAY} ticks later; when the leg rope lands the whole bind cinches. Once settled the target
 * strains against the ropes every few seconds, and on release the ropes slacken, drop and thin away.
 */
public final class IncarcerousRopeTimeline {
    public static final double FLIGHT = 6.0;
    public static final double WRAP = 10.0;
    public static final double LEG_DELAY = 4.0;
    /** The leg rope's last coil lands: the whole bind cinches here. */
    public static final double CINCHED = LEG_DELAY + FLIGHT + WRAP;
    /** The anchor rope plunges into the ground over this long after the cinch. */
    public static final double ANCHOR_GROW = 5.0;
    public static final double RELEASE = 16.0;
    /** Struggling starts this long after the cinch, so the first strain doesn't land on the jerk. */
    private static final double STRAIN_SETTLE = 8.0;
    private static final double STRAIN_LENGTH = 22.0;
    private static final int STRAIN_PERIOD = 55;

    private IncarcerousRopeTimeline() {
    }

    /**
     * Arc length of a rope's head along its whole path (flight then coil), for a rope that started
     * {@code t} ticks ago. The flight is linear (the rope whips out at full speed); the wrap eases out so the
     * last coil lands softly into the cinch.
     */
    public static double head(double t, double flightLength, double coilLength) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (t < FLIGHT) {
            return flightLength * (t / FLIGHT);
        }
        double x = Math.min(1.0, (t - FLIGHT) / WRAP);
        return flightLength + coilLength * (1.0 - (1.0 - x) * (1.0 - x));
    }

    /** The rope trails the head by exactly the coil's length, so when the wrap ends the tail sits at the coil start. */
    public static double tail(double head, double coilLength) {
        return Math.max(0.0, head - coilLength);
    }

    /** When (rope ticks) the head reached a coil point {@code along} into a coil of {@code coilLength}: the inverse of {@link #head}. */
    public static double laidAt(double along, double coilLength) {
        double e = coilLength <= 0.0 ? 1.0 : Math.min(1.0, Math.max(0.0, along / coilLength));
        return FLIGHT + WRAP * (1.0 - Math.sqrt(1.0 - e));
    }

    /**
     * Extra radius on a coil laid {@code since} ticks ago, as a fraction: it lands half again too wide and
     * springs in with a small undershoot. 0 for anything long settled.
     */
    public static double looseness(double since) {
        if (since < 0.0) {
            return 0.55;
        }
        return 0.55 * Math.exp(-since / 2.2) * Math.cos(since * 0.85);
    }

    /** The cinch: a short squeeze (negative) when the last coil lands, {@code t} ticks since the bind arrived. */
    public static double cinch(double t) {
        double x = t - CINCHED;
        if (x < 0.0 || x > 6.0) {
            return 0.0;
        }
        return -0.09 * Math.sin(Math.PI * x / 6.0) * Math.exp(-x / 4.0);
    }

    /**
     * How hard the bound target is straining against the ropes, 0 to 1, {@code t} ticks since the bind
     * arrived. The rhythm is seeded per target so a group doesn't struggle in step.
     */
    public static double strain(long seed, double t) {
        double since = t - CINCHED - STRAIN_SETTLE;
        if (since < 0.0) {
            return 0.0;
        }
        int period = STRAIN_PERIOD + (int) Math.floorMod(seed, 40L);
        double phase = (since + Math.floorMod(seed * 31L, (long) period)) % period;
        if (phase >= STRAIN_LENGTH) {
            return 0.0;
        }
        double s = Math.sin(Math.PI * phase / STRAIN_LENGTH);
        double tremor = 0.85 + 0.15 * Math.sin(t * 2.3);
        return s * s * tremor;
    }

    /** 0 until the release, then rises to 1 as the ropes finish falling away. */
    public static double release(double sinceRelease) {
        if (sinceRelease <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, sinceRelease / RELEASE);
    }

    /** How fully the target is held (the bound pose weight): eases in as the torso rope wraps, out as it is released. */
    public static double hold(double t, double sinceRelease) {
        double in = clamp01((t - FLIGHT) / WRAP);
        in = in * in * (3.0 - 2.0 * in);
        double out = sinceRelease <= 0.0 ? 1.0 : 1.0 - clamp01(sinceRelease / 6.0);
        return in * out;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(1.0, v);
    }
}
