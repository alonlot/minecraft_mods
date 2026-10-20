package com.hogwartsmagic.pensieve.scene;

/** Pure flutter maths for a drifting envelope: two summed sines with seed-derived phase and rate. */
public final class LetterFlutter {
    private static final float MIN_RATE = 0.15f;
    private static final float MAX_RATE = 0.35f;
    private static final float RESTING_SPEED = 0.01f;

    private LetterFlutter() {}

    /** Roll about the long axis of the letter in degrees, within +-30. */
    public static float roll(float age, int seed) {
        return 17f * wave(age, seed, 0) + 13f * wave(age, seed, 2);
    }

    /** Pitch added to the pitch of the letter in degrees, within +-19. */
    public static float wobble(float age, int seed) {
        return 11f * wave(age, seed, 4) + 8f * wave(age, seed, 6);
    }

    /** True when the speed (blocks per tick) is low enough that the letter should lie still. */
    public static boolean resting(float speed) {
        return speed < RESTING_SPEED;
    }

    private static float wave(float age, int seed, int salt) {
        float rate = MIN_RATE + (MAX_RATE - MIN_RATE) * unit(seed, salt);
        float phase = (float) (Math.PI * 2.0) * unit(seed, salt + 1);
        return (float) Math.sin(age * rate + phase);
    }

    /** A stable pseudo-random value in [0, 1) from a seed and a salt. */
    private static float unit(int seed, int salt) {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }
}
