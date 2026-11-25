package com.hogwartsmagic.cosy;

/** The numbers behind the hearth and the Warmed effect. Pure, so JUnit can pin them. */
public final class WarmthRules {
    private WarmthRules() {
    }

    /** How far a lit hearth reaches, in blocks. */
    public static final double RADIUS = 6.0;
    /** A lit hearth warms everyone in reach this often. */
    public static final int PULSE_TICKS = 40;
    /** Each pulse sets Warmed to this many ticks, so it outlasts the gap between pulses. */
    public static final int WARMED_TICKS = 200;
    /** Warmed mends one point of health this often. */
    public static final int HEAL_INTERVAL = 80;
    /** Warmed does not mend anyone hurt more recently than this. */
    public static final int HURT_GRACE = 100;

    public static boolean shouldHeal(int effectTicksElapsed, int ticksSinceHurt) {
        return effectTicksElapsed % HEAL_INTERVAL == 0 && ticksSinceHurt >= HURT_GRACE;
    }
}
