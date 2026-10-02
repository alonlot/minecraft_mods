package com.hogwartsmagic.spell.damage;

/** Diffindo: honest damage that cuts partly through armour, the magic shears, and a Master cleave. */
public final class DiffindoRules {
    public static final double CLEAVE_RANGE = 3.0;
    public static final double CLEAVE_HALF_WIDTH = 1.25;
    private static final float[] DAMAGE = {6f, 7f, 8f, 9f};
    private static final double[] ARMOR_IGNORE = {0.25, 0.40, 0.40, 0.60};
    private static final double[] CUT_RADIUS = {0.0, 1.0, 1.0, 2.0};

    private DiffindoRules() {
    }

    public static float damage(int rank) {
        return DAMAGE[RankIndex.of(rank)];
    }

    public static double armorIgnore(int rank) {
        return ARMOR_IGNORE[RankIndex.of(rank)];
    }

    public static double cutRadius(int rank) {
        return CUT_RADIUS[RankIndex.of(rank)];
    }

    public static boolean cleaves(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /**
     * Vanilla's armour formula (CombatRules.getDamageAfterAbsorb, without weapon enchantments) run on
     * armour and toughness reduced by {@code ignoreFraction}. The result is dealt as armour-bypassing
     * magic damage, so armour only counts once.
     */
    public static float damageAfterArmor(float damage, double armor, double toughness, double ignoreFraction) {
        if (!(damage > 0f)) {
            return 0f;
        }
        double keep = 1.0 - Math.max(0.0, Math.min(1.0, ignoreFraction));
        double a = Math.max(0.0, armor) * keep;
        double t = Math.max(0.0, toughness) * keep;
        double f = 2.0 + t / 4.0;
        double effective = Math.max(a * 0.2, Math.min(20.0, a - damage / f));
        return (float) (damage * (1.0 - effective / 25.0));
    }

    /** Offset (from the first target) lies behind it along the cast direction, within 3 blocks, in a 1.25-wide lane. */
    public static boolean inCleaveLane(double forwardX, double forwardZ, double offsetX, double offsetZ) {
        double length = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (length < 1.0e-6) {
            return false;
        }
        double fx = forwardX / length;
        double fz = forwardZ / length;
        double along = offsetX * fx + offsetZ * fz;
        if (along <= 0.0 || along > CLEAVE_RANGE) {
            return false;
        }
        double lateralSq = offsetX * offsetX + offsetZ * offsetZ - along * along;
        return lateralSq <= CLEAVE_HALF_WIDTH * CLEAVE_HALF_WIDTH;
    }
}
