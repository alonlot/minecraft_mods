package com.hogwartsmagic.locket;

import java.util.List;

/** The Drink of Despair. Pure. */
public final class DespairRules {
    public static final int DRINKS_TO_EMPTY = 6;
    public static final int REFILL_TIMEOUT_TICKS = 600;
    public static final int MIN_TICKS_BETWEEN_DRINKS = 20;
    public static final int THIRST_DURATION_TICKS = 12000;
    public static final int THIRST_DAMAGE_INTERVAL = 100;
    public static final int VISION_TICKS = 100;

    public record DrinkEffect(String effectId, int ticks, int amplifier) {}

    private static final List<List<DrinkEffect>> TABLE = List.of(
            List.of(new DrinkEffect("nausea", 200, 0)),
            List.of(new DrinkEffect("nausea", 300, 0), new DrinkEffect("weakness", 600, 0)),
            List.of(new DrinkEffect("nausea", 300, 0), new DrinkEffect("weakness", 600, 0),
                    new DrinkEffect("slowness", 600, 0)),
            List.of(new DrinkEffect("nausea", 300, 0), new DrinkEffect("weakness", 600, 0),
                    new DrinkEffect("slowness", 600, 1), new DrinkEffect("darkness", 300, 0)),
            List.of(new DrinkEffect("nausea", 300, 0), new DrinkEffect("weakness", 600, 0),
                    new DrinkEffect("slowness", 600, 1), new DrinkEffect("darkness", 300, 0),
                    new DrinkEffect("blindness", 100, 0), new DrinkEffect("mining_fatigue", 600, 1)),
            List.of(new DrinkEffect("nausea", 300, 0), new DrinkEffect("weakness", 600, 0),
                    new DrinkEffect("slowness", 600, 1), new DrinkEffect("darkness", 300, 0),
                    new DrinkEffect("mining_fatigue", 600, 1), new DrinkEffect("hunger", 600, 1)));
    private static final float[] DAMAGE = {0f, 0f, 2f, 3f, 4f, 4f};

    private static final String[] WHISPERS = {
            "The cold reaches in and finds every regret you kept.",
            "Nobody is coming. Nobody was ever coming.",
            "You failed them. You will fail them again.",
            "Stop. Please. Make it stop.",
            "It is your fault. All of it was always your fault.",
            "Water... there must be water somewhere..."};

    private DespairRules() {}

    private static int index(int drinkNumber) {
        if (drinkNumber < 1 || drinkNumber > DRINKS_TO_EMPTY)
            throw new IllegalArgumentException("drink " + drinkNumber);
        return drinkNumber - 1;
    }

    public static List<DrinkEffect> effectsFor(int drinkNumber) { return TABLE.get(index(drinkNumber)); }
    public static float damageFor(int drinkNumber) { return DAMAGE[index(drinkNumber)]; }

    public static float cappedDamage(float wanted, float health) {
        return Math.max(0f, Math.min(wanted, health - 1f));
    }

    public static boolean shouldRefill(long now, long lastDrink, boolean drinkerOnIsland, boolean drinkerAlive) {
        return !drinkerAlive || !drinkerOnIsland || now - lastDrink > REFILL_TIMEOUT_TICKS;
    }

    public static String whisper(int index) { return WHISPERS[Math.floorMod(index, WHISPERS.length)]; }
}
