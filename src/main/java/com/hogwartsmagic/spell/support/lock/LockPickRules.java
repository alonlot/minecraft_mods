package com.hogwartsmagic.spell.support.lock;

/**
 * Hold-to-pick on piston locks: how long a hold may lapse, how long a held solve may run, how big the search
 * is, how far it has got, how long is left, and how often the picking sounds play. Pure numbers.
 */
public final class LockPickRules {
    /** Ticks without a heartbeat before the hold counts as let go (as Crucio). */
    public static final int HOLD_TIMEOUT_TICKS = 6;
    /** A held solve may run this long once the worker has started it (the unheld cap is 10 s). */
    public static final int HELD_SOLVE_TIMEOUT_TICKS = 600;
    /** The time left reads "~?s" until the worker has run this long. */
    public static final int ETA_UNKNOWN_TICKS = 10;
    public static final int BAR_INTERVAL_TICKS = 2;
    public static final int SPARK_INTERVAL_TICKS = 4;
    public static final int BAR_CELLS = 20;
    public static final int UNKNOWN = -1;

    private LockPickRules() {
    }

    public static boolean holdLapsed(long now, long lastSeen) {
        return now - lastSeen > HOLD_TIMEOUT_TICKS;
    }

    /**
     * Worst-case attempts of LockSolver.solve: alternatives[i] is how many other levels settable input i can
     * move to; pulses the buttons, plates and sensors; forced the forceable sources. Saturates at Long.MAX_VALUE.
     */
    public static long attempts(int[] alternatives, int pulses, int forced, int maxPresses) {
        int n = alternatives.length;
        long[] e = new long[n + 1];
        e[0] = 1;
        for (int i = 0; i < n; i++) {
            for (int k = i + 1; k >= 1; k--) {
                e[k] = add(e[k], mul(e[k - 1], Math.max(0, alternatives[i])));
            }
        }
        long settings = 0;
        for (long v : e) {
            settings = add(settings, v);
        }
        int pressCap = pulses == 0 ? 0 : maxPresses;
        long sequences = 0;
        for (int len = 0; len <= pressCap; len++) {
            sequences = add(sequences, pow(pulses, len));
        }
        long all = mul(settings, sequences);
        long total = all == Long.MAX_VALUE ? all : all - 1; // the empty attempt is never made
        if (forced <= 0) {
            return Math.max(0, total);
        }
        long forcing = 0;
        for (int len = 1; len <= maxPresses; len++) {
            forcing = add(forcing, pow(pulses + forced, len) - pow(pulses, len));
        }
        total = add(total, mul(settings, forcing));
        if (forced >= 2) {
            int held = Math.min(forced, 8);
            total = add(total, (1L << held) - 1 - held);
        }
        return total;
    }

    /** How far the search is towards its end, 0..1: the furthest of attempts, work and time. */
    public static double fraction(long done, long total, long work, long budget, long runTicks, long capTicks) {
        double f = 0.0;
        if (total > 0) {
            f = Math.max(f, done / (double) total);
        }
        if (budget > 0) {
            f = Math.max(f, work / (double) budget);
        }
        if (capTicks > 0) {
            f = Math.max(f, runTicks / (double) capTicks);
        }
        return Math.min(1.0, Math.max(0.0, f));
    }

    /**
     * Seconds left, rounded up, at the throughput measured so far: the soonest of the attempts running out,
     * the work budget running out and the cap. UNKNOWN in the first half second or before anything was measured.
     */
    public static int etaSeconds(long done, long total, long work, long budget, long runTicks, long capTicks) {
        if (runTicks < ETA_UNKNOWN_TICKS || (done <= 0 && work <= 0)) {
            return UNKNOWN;
        }
        double ticks = capTicks > 0 ? Math.max(0, capTicks - runTicks) : Double.MAX_VALUE; // no cap, as fraction()
        if (done > 0 && total > 0) {
            ticks = Math.min(ticks, Math.max(0, total - done) * (double) runTicks / done);
        }
        if (work > 0 && budget > 0) {
            ticks = Math.min(ticks, Math.max(0, budget - work) * (double) runTicks / work);
        }
        return (int) Math.ceil(ticks / 20.0 - 1e-9);
    }

    public static int filledCells(double fraction) {
        return (int) Math.floor(clamp(fraction) * BAR_CELLS);
    }

    /** Ticks between two lock clicks: 8 at the start, 3 at the end. */
    public static int clickInterval(double fraction) {
        return (int) Math.round(8 - 5 * clamp(fraction));
    }

    /** Chance that a click also brings a faint piston sound from the door. */
    public static double pistonChance(double fraction) {
        return 0.04 + 0.2 * clamp(fraction);
    }

    private static double clamp(double f) {
        return Math.min(1.0, Math.max(0.0, f));
    }

    private static long add(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    private static long mul(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static long pow(long base, int exp) {
        long r = 1;
        for (int i = 0; i < exp; i++) {
            r = mul(r, base);
        }
        return r;
    }
}
