package com.hogwartsmagic.spell.summon;

/**
 * Fiendfyre by rank: how long the held fire lasts, how many serpents answer, and how likely one is to
 * turn on its caster. A serpent that will turn glows red for TELEGRAPH_TICKS first (roadmap Decision 11).
 */
public final class FiendfyreRules {
    public static final int TELEGRAPH_TICKS = 20;
    public static final int MIN_TURN_TICKS = 30;
    public static final double ROGUE_SEARCH_RADIUS = 16.0;
    public static final int ROGUE_RETARGET_INTERVAL = 20;
    public static final int DISPATCH_TICKS = 80;
    public static final double DISPATCH_RANGE = 48.0;
    public static final int HORCRUX_REACH = 2;
    public static final int HORCRUX_SCAN_INTERVAL = 10;
    /** A rogue serpent rampages for at least this long, even when it turns late. */
    private static final int MIN_RAMPAGE_TICKS = 40;
    private static final int[] DURATION = {120, 160, 200, 240};
    private static final double[] ROGUE_CHANCE = {0.15, 0.08, 0.03, 0.0};
    private static final int[] SERPENTS = {1, 1, 2, 3};

    private FiendfyreRules() {
    }

    public static int durationTicks(int rank) {
        return DURATION[RankIndex.of(rank)];
    }

    public static double rogueChance(int rank) {
        return ROGUE_CHANCE[RankIndex.of(rank)];
    }

    public static boolean goesRogue(int rank, double roll) {
        return roll < rogueChance(rank);
    }

    public static int serpentCount(int rank) {
        return SERPENTS[RankIndex.of(rank)];
    }

    public static int rogueIndex(int count, double roll) {
        return Math.min(count - 1, Math.max(0, (int) (roll * count)));
    }

    /** Between MIN_TURN + TELEGRAPH (so the glow never starts at once) and duration − TELEGRAPH. */
    public static int rogueTurnTick(int durationTicks, double roll) {
        int earliest = MIN_TURN_TICKS + TELEGRAPH_TICKS;
        int latest = Math.max(earliest, durationTicks - TELEGRAPH_TICKS);
        int span = latest - earliest + 1;
        double r = Math.max(0.0, Math.min(roll, 0.999999));
        return earliest + (int) (r * span);
    }

    public static boolean telegraphing(int lifeTicks, int turnTick) {
        return turnTick >= 0 && lifeTicks >= turnTick - TELEGRAPH_TICKS && lifeTicks < turnTick;
    }

    public static int rogueLifeEnd(int durationTicks, int turnTick) {
        return Math.max(durationTicks, turnTick + MIN_RAMPAGE_TICKS);
    }

    public static boolean expired(long startedAt, long now, int rank) {
        return now - startedAt >= durationTicks(rank);
    }

    /** Sideways place in the formation, in blocks to the caster's right (negative is left). */
    public static double lateralOffset(int index, int count) {
        if (count <= 1) {
            return 0.0;
        }
        if (count == 2) {
            return index == 0 ? -1.6 : 1.6;
        }
        return (index - (count - 1) / 2.0) * 2.4;
    }

    public static boolean dispatches(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean destroysHorcrux(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /**
     * Whether a rogue serpent may hunt this target. Only mobs and players are prey (never an armor stand).
     * It never hunts a creative or spectator player; it may always hunt its own caster; another player
     * only when PvP is on and the caster could harm them.
     */
    public static boolean rogueMayTarget(boolean isCaster, boolean isPlayer, boolean creativeOrSpectator,
                                         boolean pvpAllowed, boolean casterCanHarm, boolean isMobOrPlayer) {
        if (!isMobOrPlayer || creativeOrSpectator) {
            return false;
        }
        if (isCaster) {
            return true;
        }
        if (isPlayer) {
            return pvpAllowed && casterCanHarm;
        }
        return true;
    }

    /**
     * Whether a Master may send a serpent at this target. Never the caster, a creative or spectator
     * player, or an ally (an owned mob fighting for the caster, or a player allied to them); another
     * player only when PvP is on and the caster could harm them.
     */
    public static boolean dispatchMayTarget(boolean isCaster, boolean isPlayer, boolean creativeOrSpectator,
                                            boolean pvpAllowed, boolean casterCanHarm, boolean alliedToCaster) {
        if (isCaster || creativeOrSpectator || alliedToCaster) {
            return false;
        }
        if (isPlayer) {
            return pvpAllowed && casterCanHarm;
        }
        return true;
    }
}
