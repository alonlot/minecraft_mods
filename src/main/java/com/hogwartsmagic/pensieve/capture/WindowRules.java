package com.hogwartsmagic.pensieve.capture;

/** Pure rules deciding which game tick a capture window may start at. */
public final class WindowRules {
    public static final int MIN_TICKS = 40;
    public static final int JUMP_BLOCKS = 8;
    public static final int WATCH_MARGIN = 48;
    private static final double MAX_DY = 32.0;

    private WindowRules() {}

    /**
     * Earliest usable game tick. ownerXyz holds x,y,z triples, one per entry of ownerTicks.
     */
    public static long start(long now, int lengthTicks, long logValidSince, long lastDiscontinuity,
                             long[] ownerTicks, double[] ownerXyz, double[] centre, int radius) {
        long start = Math.max(now - lengthTicks + 1, Math.max(logValidSince, lastDiscontinuity + 1));
        if (ownerTicks.length > 0) {
            start = Math.max(start, ownerTicks[0]);
        }
        // Trade-off: island cells beyond the watched square may not be rewound (fidelity vs memory length).
        double far = radius + WATCH_MARGIN;
        for (int i = 0; i < ownerTicks.length; i++) {
            double x = ownerXyz[i * 3];
            double y = ownerXyz[i * 3 + 1];
            double z = ownerXyz[i * 3 + 2];
            double dx = x - centre[0];
            double dz = z - centre[2];
            if (Math.sqrt(dx * dx + dz * dz) > far || Math.abs(y - centre[1]) > MAX_DY) {
                start = Math.max(start, ownerTicks[i] + 1);
            }
            if (i > 0) {
                double sx = x - ownerXyz[(i - 1) * 3];
                double sy = y - ownerXyz[(i - 1) * 3 + 1];
                double sz = z - ownerXyz[(i - 1) * 3 + 2];
                if (Math.sqrt(sx * sx + sy * sy + sz * sz) > JUMP_BLOCKS) {
                    start = Math.max(start, ownerTicks[i]);
                }
            }
        }
        return start;
    }

    public static boolean usable(long start, long now) {
        return now - start + 1 >= MIN_TICKS;
    }
}
