package com.hogwartsmagic.lycanthropy;

/**
 * The werewolf curse's numbers and decisions, free of Minecraft so they are unit-tested. {@link Lycanthropy} reads
 * them on the server.
 *
 * A bite from a werewolf (a Lycanthrope Villager in its beast form, or a cursed player who has turned) may pass the
 * curse on. A cursed player turns every night in a natural (overworld-like) dimension, more savagely at the full
 * moon, and turns back at dawn worn out. Someone bitten at night first turns at the next dusk. Only the Wolfsbane
 * Potion lifts the curse; dying does not.
 */
public final class LycanthropyRules {
    /** Chance that a landed werewolf bite passes the curse on. */
    public static final double BITE_CHANCE = 0.30;
    /** The same at the full moon. */
    public static final double FULL_MOON_BITE_CHANCE = 0.60;

    /** The server re-checks a cursed player this often (ticks). */
    public static final int CHECK_INTERVAL_TICKS = 20;
    /** The beast's effects last this long and are topped up while it stays a beast, so they fade soon after dawn. */
    public static final int BEAST_EFFECT_TICKS = 80;
    /** Topped up when this little is left (fewer refresh events than re-adding every check). */
    public static final int BEAST_EFFECT_REFRESH_BELOW = 40;
    /** Night Vision flickers below 10 seconds, so the beast's runs longer and is removed by hand at dawn. */
    public static final int BEAST_NIGHT_VISION_TICKS = 20 * 16;

    /** The transformation (matches the werewolf's own morph clip): slowed to a crawl while it happens. */
    public static final int MORPH_TO_WOLF_TICKS = 60;
    /** The howl the transformation rears up into, this many ticks before it ends. */
    public static final int MORPH_HOWL_AT_TICKS_LEFT = 18;

    /** Dawn: aching and weak for a minute, and starving for ten seconds. */
    public static final int DAWN_WEAKNESS_TICKS = 20 * 60;
    public static final int DAWN_HUNGER_TICKS = 20 * 10;

    /** A beast howls now and then, once in this many checks (full moon: far more often). */
    public static final int HOWL_ONE_IN_CHECKS = 180;
    public static final int FULL_MOON_HOWL_ONE_IN_CHECKS = 30;

    /** A Wolfsbane Potion drunk without the curse: a few seconds of nausea and nothing else. */
    public static final int UNCURSED_DRINK_NAUSEA_TICKS = 20 * 5;

    private LycanthropyRules() {
    }

    public static double biteChance(boolean fullMoon) {
        return fullMoon ? FULL_MOON_BITE_CHANCE : BITE_CHANCE;
    }

    /** roll: uniform in [0, 1). */
    public static boolean biteInfects(double roll, boolean fullMoon) {
        return roll < biteChance(fullMoon);
    }

    /**
     * Whether a player should be a beast right now. awaitingDusk: bitten during this night, so the first change waits
     * for the next one. naturalDimension: the Nether, the End and the mod's own dimensions have no moon to answer to.
     */
    public static boolean shouldBeBeast(boolean cursed, boolean awaitingDusk, boolean night, boolean naturalDimension) {
        return cursed && !awaitingDusk && night && naturalDimension;
    }

    /** A bite during the night waits for the next dusk; a bite by day turns the victim this very night. */
    public static boolean awaitsDuskAfterBite(boolean night) {
        return night;
    }

    /** Strength and Speed level (amplifier) for the beast: II at the full moon, I otherwise. */
    public static int beastAmplifier(boolean fullMoon) {
        return fullMoon ? 1 : 0;
    }

    public static int howlOneIn(boolean fullMoon) {
        return fullMoon ? FULL_MOON_HOWL_ONE_IN_CHECKS : HOWL_ONE_IN_CHECKS;
    }
}
