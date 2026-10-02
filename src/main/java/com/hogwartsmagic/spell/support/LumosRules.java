package com.hogwartsmagic.spell.support;

/**
 * Lumos: wand-light brightness, the Expert beam with its orbs, and Lumos Solem. The light effect's
 * amplifier carries the brightness (0 = level 12, 1 = level 15) so the light tick needs no rank lookup.
 */
public final class LumosRules {
    public static final int LIGHT_TICKS = 12000;
    public static final int MAX_ORBS = 3;
    public static final int ORB_TICKS = 6000;
    public static final int ORB_LIGHT = 15;
    public static final float SOLEM_DAMAGE = 6.0f;
    public static final int SOLEM_BLIND_TICKS = 60;
    public static final int FOLLOW_RECHECK_TICKS = 10;
    public static final double FOLLOW_SEARCH_RADIUS = 1.5;

    private LumosRules() {
    }

    public static int lightForAmplifier(int amplifier) {
        return amplifier <= 0 ? 12 : 15;
    }

    public static int amplifierForRank(int rank) {
        return rank >= 2 ? 1 : 0;
    }

    public static boolean firesBeam(int rank) {
        return rank >= 3;
    }

    public static boolean placesOrbs(int rank) {
        return rank >= 3;
    }

    public static boolean solem(int rank) {
        return rank >= 4;
    }
}
