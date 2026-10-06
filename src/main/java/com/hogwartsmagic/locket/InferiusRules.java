package com.hogwartsmagic.locket;

/** Numbers for the Inferius. Pure: no Minecraft imports. */
public final class InferiusRules {
    public static final int MAX_HEALTH = 24;
    public static final int GRAB_TICKS = 40;
    public static final int GRAB_COOLDOWN = 100;
    public static final float NON_FIRE_FACTOR = 0.3f;
    public static final float BURNING_FACTOR = 2.0f;
    public static final int FEAR_RADIUS = 6;
    public static final int BLOCK_FEAR_SCAN = 4;
    public static final int IGNITE_ON_FIRE_HIT_SECONDS = 8;
    public static final double WATER_PULL_PER_TICK = 0.05;
    public static final int IDLE_DESPAWN_TICKS = 2400;

    private InferiusRules() {}

    public static float scaleDamage(float amount, boolean fireSource, boolean burning) {
        float scaled = fireSource ? amount : amount * NON_FIRE_FACTOR;
        return burning ? scaled * BURNING_FACTOR : scaled;
    }

    public static boolean isGrabReady(long now, long lastGrab) {
        return now - lastGrab >= GRAB_COOLDOWN;
    }
}
