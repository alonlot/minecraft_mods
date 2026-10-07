package com.hogwartsmagic.spell.dark;

import java.util.EnumSet;
import java.util.Set;

/**
 * Dark Magic costs (Phase 2), free of Minecraft so they are unit-tested. Every cost reads the tier
 * (DarkRules.CLEAN..LOST, through DarkMagicData.getTier, so enableDarkMagic off reads Clean), never the raw number.
 * A tier outside 0..4 is clamped into it. A tier keeps the costs of the tiers below it, except where a harder cost
 * replaces a softer one: no corporeal Patronus replaces the halved chance from Tainted, and students attacking
 * replaces students keeping away at Lost. The Patronus never fails: the corporeal chance only falls, and a roll that
 * misses it gives the shield.
 */
public final class DarkCostRules {

    /** One cost and the tiers it applies at (inclusive). */
    public enum Cost {
        HALF_CORPOREAL_PATRONUS(DarkRules.SHADOWED, DarkRules.SHADOWED),
        NO_CORPOREAL_PATRONUS(DarkRules.TAINTED, DarkRules.LOST),
        EARLY_INSOMNIA(DarkRules.TAINTED, DarkRules.LOST),
        SHIELD_UPKEEP(DarkRules.CORRUPTED, DarkRules.LOST),
        DEMENTORS_PREFER(DarkRules.CORRUPTED, DarkRules.LOST),
        STUDENTS_KEEP_AWAY(DarkRules.CORRUPTED, DarkRules.CORRUPTED),
        SMOKE(DarkRules.CORRUPTED, DarkRules.LOST),
        LOST_MAX_HEALTH(DarkRules.LOST, DarkRules.LOST),
        STUDENTS_ATTACK(DarkRules.LOST, DarkRules.LOST),
        ELVES_FLEE(DarkRules.LOST, DarkRules.LOST);

        private final int fromTier;
        private final int toTier;

        Cost(int fromTier, int toTier) {
            this.fromTier = fromTier;
            this.toTier = toTier;
        }

        /** The lowest tier this cost applies at. NPC goals compare the darkest online tier with it. */
        public int fromTier() {
            return this.fromTier;
        }

        public int toTier() {
            return this.toTier;
        }

        public boolean appliesAt(int tier) {
            int t = clampTier(tier);
            return t >= this.fromTier && t <= this.toTier;
        }
    }

    /** Shadowed: the corporeal chance is halved (15% to 7.5%). */
    public static final float CORPOREAL_HALF = 0.5f;
    /** Corrupted and Lost: the Patronus shield costs half as much again to hold. */
    public static final float SHIELD_UPKEEP_MULTIPLIER = 1.5f;
    public static final int TICKS_PER_DAY = 24_000;
    /** Same stat as phantoms (ticks since the last bed or respawn): insomnia Dementors after 5 days... */
    public static final int BASE_INSOMNIA_TICKS = 5 * TICKS_PER_DAY;
    /** ...and after 3 days from Tainted. */
    public static final int DARK_INSOMNIA_TICKS = 3 * TICKS_PER_DAY;
    /** Lost: one heart (the soul is torn). */
    public static final double LOST_MAX_HEALTH_DELTA = -2.0D;
    public static final int SMOKE_INTERVAL_TICKS = 10;
    public static final int SMOKE_COUNT_CORRUPTED = 2;
    public static final int SMOKE_COUNT_LOST = 4;
    public static final float STUDENT_KEEP_AWAY_DISTANCE = 8.0f;
    public static final double ELF_FLEE_DISTANCE = 8.0D;
    public static final double ELF_FLEE_STOP_DISTANCE = 12.0D;
    /** The darkest-online cache is recomputed at most once per second. */
    public static final int PRESENCE_REFRESH_TICKS = 20;
    /** Avoid and flee goals search once per this many goal evaluations (a mob evaluates every 2 ticks). */
    public static final int NPC_CHECK_EVERY_CALLS = 5;

    private DarkCostRules() {
    }

    public static int clampTier(int tier) {
        return Math.max(DarkRules.CLEAN, Math.min(DarkRules.LOST, tier));
    }

    /** Every cost that applies at this tier. */
    public static Set<Cost> costsAt(int tier) {
        EnumSet<Cost> costs = EnumSet.noneOf(Cost.class);
        for (Cost cost : Cost.values()) {
            if (cost.appliesAt(tier)) {
                costs.add(cost);
            }
        }
        return costs;
    }

    /** The chance a Patronus cast is corporeal: the base, half of it at Shadowed, none from Tainted. */
    public static float corporealPatronusChance(float base, int tier) {
        if (Cost.NO_CORPOREAL_PATRONUS.appliesAt(tier)) {
            return 0.0f;
        }
        if (Cost.HALF_CORPOREAL_PATRONUS.appliesAt(tier)) {
            return base * CORPOREAL_HALF;
        }
        return base;
    }

    public static float shieldUpkeepMultiplier(int tier) {
        return Cost.SHIELD_UPKEEP.appliesAt(tier) ? SHIELD_UPKEEP_MULTIPLIER : 1.0f;
    }

    /** Ticks without rest before insomnia Dementors may come at night. */
    public static int insomniaTicks(int tier) {
        return Cost.EARLY_INSOMNIA.appliesAt(tier) ? DARK_INSOMNIA_TICKS : BASE_INSOMNIA_TICKS;
    }

    public static boolean dementorsPrefer(int tier) {
        return Cost.DEMENTORS_PREFER.appliesAt(tier);
    }

    public static boolean studentsKeepAway(int tier) {
        return Cost.STUDENTS_KEEP_AWAY.appliesAt(tier);
    }

    public static boolean studentsAttack(int tier) {
        return Cost.STUDENTS_ATTACK.appliesAt(tier);
    }

    public static boolean elvesFlee(int tier) {
        return Cost.ELVES_FLEE.appliesAt(tier);
    }

    /** The max-health modifier amount this tier wants: -2 at Lost, else none. */
    public static double maxHealthDelta(int tier) {
        return Cost.LOST_MAX_HEALTH.appliesAt(tier) ? LOST_MAX_HEALTH_DELTA : 0.0D;
    }

    /**
     * Health after the max changed: lowered to the max when above it, never below 1, never raised, and a dead (or
     * NaN) value is returned as it is. So applying the Lost cost never kills and removing it never heals.
     */
    public static float healthAfterMaxChange(float health, float maxHealth) {
        if (!(health > 0.0f) || health <= maxHealth) {
            return health;
        }
        return Math.max(1.0f, maxHealth);
    }

    /** Smoke particles per puff: none below Corrupted, 2 at Corrupted, 4 at Lost. */
    public static int smokeCount(int tier) {
        if (!Cost.SMOKE.appliesAt(tier)) {
            return 0;
        }
        return clampTier(tier) >= DarkRules.LOST ? SMOKE_COUNT_LOST : SMOKE_COUNT_CORRUPTED;
    }

    /** One tick in SMOKE_INTERVAL_TICKS, spread over players by a per-player phase key (the entity id). */
    public static boolean dueToSmoke(long tickCount, long phaseKey) {
        return Math.floorMod(tickCount + phaseKey, (long) SMOKE_INTERVAL_TICKS) == 0L;
    }

    /** True when the cached darkest-online tier must be recomputed (a second old, or the clock went back). */
    public static boolean presenceStale(int now, int cachedAt) {
        return now < cachedAt || now - cachedAt >= PRESENCE_REFRESH_TICKS;
    }
}
