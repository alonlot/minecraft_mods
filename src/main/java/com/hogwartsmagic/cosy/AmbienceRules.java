package com.hogwartsmagic.cosy;

/** The numbers behind the indoor ambience loops. Pure, so JUnit can pin them. */
public final class AmbienceRules {
    private AmbienceRules() {
    }

    /** A loop fades in or out in 40 ticks, two seconds. */
    public static final int FADE_TICKS = 40;
    /** The hearth crackle is audible out to this many blocks. */
    public static final double CRACKLE_RANGE = 12.0;
    /** How far the player looks for window glass, in blocks, and for a hearth. */
    public static final int GLASS_RADIUS = 5;

    /** Rain on the windows: it rains here, the player is indoors (a roof of glass counts), and some glass nearby has rain on it. */
    public static boolean rainOnWindow(boolean raining, boolean playerUnderRoof, boolean exposedGlassNearby) {
        return raining && playerUnderRoof && exposedGlassNearby;
    }

    /** One tick of a fade: toward 1 when the loop is wanted, toward 0 when it is not. */
    public static float fadeStep(float current, boolean wanted) {
        float step = 1.0f / FADE_TICKS;
        float next = wanted ? current + step : current - step;
        // Forty float additions of a fortieth land a hair short of 1, so snap the last half step to the end.
        if (next > 1.0f - step * 0.5f) {
            return 1.0f;
        }
        return next < step * 0.5f ? 0.0f : next;
    }

    /** Volume scale of the hearth crackle at a distance: 1 at the hearth, falling linearly to 0 at the range. */
    public static float crackleScale(double distance) {
        return (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / CRACKLE_RANGE));
    }
}
