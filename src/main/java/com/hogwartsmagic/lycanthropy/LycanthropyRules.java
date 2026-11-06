package com.hogwartsmagic.lycanthropy;

/**
 * The werewolf curse's numbers and decisions, free of Minecraft so they are unit-tested. {@link Lycanthropy} reads
 * them on the server.
 *
 * A bite from a werewolf (a Lycanthrope Villager in its beast form, or a cursed player who has turned) may pass the
 * curse on. In a natural (overworld-like) dimension a cursed player turns into the beast only at the full moon, and
 * turns back at dawn worn out. Every other night they stay themselves, spells and all, but restless: keen eyes,
 * quick feet, a hunger and a growl. The night before the full moon warns them. Someone bitten at night feels nothing
 * until the next dusk. Only the Wolfsbane Potion lifts the curse; dying does not.
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

    /** A beast howls now and then: once in this many checks. */
    public static final int HOWL_ONE_IN_CHECKS = 30;
    /** A restless night: a growl once in this many checks. */
    public static final int GROWL_ONE_IN_CHECKS = 90;

    /** The beast's Strength and Speed level (amplifier): II. */
    public static final int BEAST_AMPLIFIER = 1;

    /** Moon phases (vanilla: (day / 24000) % 8). 0 is full; 7 is the waxing gibbous of the night before it. */
    public static final int FULL_MOON_PHASE = 0;
    public static final int EVE_OF_FULL_MOON_PHASE = 7;

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
     * Whether the curse is awake right now: a cursed player at night in a natural dimension. awaitingDusk: bitten
     * during this night, so nothing stirs until the next one. naturalDimension: the Nether, the End and the mod's own
     * dimensions have no moon to answer to.
     */
    public static boolean curseAwake(boolean cursed, boolean awaitingDusk, boolean night, boolean naturalDimension) {
        return cursed && !awaitingDusk && night && naturalDimension;
    }

    /** The full change into the beast: only under the full moon. */
    public static boolean shouldBeBeast(boolean cursed, boolean awaitingDusk, boolean night, boolean naturalDimension,
                                        int moonPhase) {
        return curseAwake(cursed, awaitingDusk, night, naturalDimension) && moonPhase == FULL_MOON_PHASE;
    }

    /** Any other night: the player stays themself, but restless. */
    public static boolean isRestless(boolean cursed, boolean awaitingDusk, boolean night, boolean naturalDimension,
                                     int moonPhase) {
        return curseAwake(cursed, awaitingDusk, night, naturalDimension) && moonPhase != FULL_MOON_PHASE;
    }

    /** The restless night before the full moon carries a warning. */
    public static boolean warnsOfFullMoon(int moonPhase) {
        return moonPhase == EVE_OF_FULL_MOON_PHASE;
    }

    /** A bite during the night waits for the next dusk; a bite by day turns the victim this very night. */
    public static boolean awaitsDuskAfterBite(boolean night) {
        return night;
    }

}
