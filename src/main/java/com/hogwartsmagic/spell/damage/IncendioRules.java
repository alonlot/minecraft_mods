package com.hogwartsmagic.spell.damage;

/** Incendio: fire as a tool. Longer burns with rank; from Adept the flames spread over flammable blocks. */
public final class IncendioRules {
    /** Today's rain burn is 2s against 5s dry. */
    public static final double RAIN_FACTOR = 0.4;
    public static final int MAX_FIRES = 24;
    public static final int FURNACE_FUEL_TICKS = 200;
    public static final int FURNACE_FUEL_CAP = 1600;
    private static final int[] IGNITE_SECONDS = {4, 5, 6, 7};
    private static final double[] FIRE_RADIUS = {0.0, 1.0, 2.0, 3.0};

    private IncendioRules() {
    }

    public static int igniteSeconds(int rank, boolean raining) {
        int seconds = IGNITE_SECONDS[RankIndex.of(rank)];
        return raining ? Math.max(1, (int) Math.round(seconds * RAIN_FACTOR)) : seconds;
    }

    public static double fireRadius(int rank) {
        return FIRE_RADIUS[RankIndex.of(rank)];
    }

    public static boolean spreadsFire(int rank) {
        return fireRadius(rank) > 0.0;
    }

    /** Ten more seconds of fuel, up to one coal's worth; a furnace already above the cap keeps what it has. */
    public static int furnaceBurnTime(int currentBurnTime) {
        int current = Math.max(0, currentBurnTime);
        if (current >= FURNACE_FUEL_CAP) {
            return current;
        }
        return Math.min(FURNACE_FUEL_CAP, current + FURNACE_FUEL_TICKS);
    }
}
