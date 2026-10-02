package com.hogwartsmagic.spell.mastery;

/** The anti-farm numbers, kept apart from entity checks so they can be tested. */
public final class MasteryRules {
    /** Same spell on the same target pays once per this many ticks. */
    public static final long SAME_TARGET_WINDOW_TICKS = 200L;
    private static final long MIN_GATE_TICKS = 200L;

    private MasteryRules() {
    }

    public static double targetMultiplier(boolean allied, boolean passive, boolean fromSpawner) {
        if (allied) {
            return 0.0;
        }
        double multiplier = 1.0;
        if (passive) {
            multiplier = Math.min(multiplier, 0.25);
        }
        if (fromSpawner) {
            multiplier = Math.min(multiplier, 0.5);
        }
        return multiplier;
    }

    /** Block-utility and self/channel spells pay at most once per this window. */
    public static long gateWindowTicks(int baseCooldownTicks) {
        return Math.max(baseCooldownTicks, MIN_GATE_TICKS);
    }
}
