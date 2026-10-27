package com.hogwartsmagic.fire;

/**
 * When the Fiendfyre serpent roars the dragon's fire: pure of its synched life ticks, so the server's damage, the
 * jaws the client draws and the jet it breathes all keep the same beat. The first roar gathers {@link #FIRST} ticks
 * after it is summoned, then one every {@link #PERIOD}: {@link #WINDUP} ticks of the jaws opening as the fire gathers,
 * then {@link #BREATH} ticks of fire. Pure: no Minecraft classes.
 */
public final class SerpentRoar {
    public static final int FIRST = 14, PERIOD = 64, WINDUP = 8, BREATH = 22;
    /** The serpent's jet beside a wild Horntail's. */
    public static final double SCALE = 0.85;

    private SerpentRoar() {
    }

    /** Ticks into the roar now running, 0..{@link #PERIOD}, or -1 before the first. */
    public static int roarTick(int lifeTicks) {
        int t = lifeTicks - FIRST;
        return t < 0 ? -1 : t % PERIOD;
    }

    /** Ticks since the fire began leaving the jaws, or -1 while it does not. */
    public static int breathAge(int lifeTicks) {
        int r = roarTick(lifeTicks);
        return r >= WINDUP && r < WINDUP + BREATH ? r - WINDUP : -1;
    }

    /** The fire gathering in the jaws, 0..1, or -1 while it is not. */
    public static float charge(int lifeTicks) {
        int r = roarTick(lifeTicks);
        return r >= 0 && r < WINDUP ? r / (float) WINDUP : -1;
    }

    /**
     * How far past the breathing gape the jaws are wrenched by the roar at {@code t} ticks into it (a partial tick
     * allowed; negative for none): opening over the windup, held wide through the fire, shutting over 6 ticks.
     */
    public static float gape(float t) {
        if (t < 0) return 0;
        if (t < WINDUP) return smooth(t / WINDUP);
        if (t < WINDUP + BREATH) return 1;
        return 1 - smooth((t - WINDUP - BREATH) / 6f);
    }

    private static float smooth(float x) {
        float c = Math.max(0, Math.min(1, x));
        return c * c * (3 - 2 * c);
    }
}
