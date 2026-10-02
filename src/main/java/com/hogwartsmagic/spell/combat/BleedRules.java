package com.hogwartsmagic.spell.combat;

/** Bleeding numbers: one damage per second per stack, up to three stacks, healing halved. */
public final class BleedRules {
    public static final int MAX_STACKS = 3;
    public static final float HEAL_MULTIPLIER = 0.5f;
    public static final int TICKS_PER_DAMAGE = 20;

    private BleedRules() {
    }

    /** The amplifier after one more bleed lands; pass -1 when the target isn't bleeding yet. */
    public static int nextAmplifier(int currentAmplifier) {
        if (currentAmplifier < 0) {
            return 0;
        }
        return Math.min(MAX_STACKS - 1, currentAmplifier + 1);
    }

    /** Stacks for an amplifier, clamped to 1..3 so a large /effect amplifier still means three. */
    public static int stacks(int amplifier) {
        return Math.max(1, Math.min(MAX_STACKS, amplifier + 1));
    }

    public static float damagePerSecond(int amplifier) {
        return stacks(amplifier);
    }

    /** Effect ticks fire on the remaining duration; once per second, never at 0 or for an infinite effect. */
    public static boolean isDamageTick(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks % TICKS_PER_DAMAGE == 0;
    }
}
