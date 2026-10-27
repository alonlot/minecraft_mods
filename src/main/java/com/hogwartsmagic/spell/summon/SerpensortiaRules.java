package com.hogwartsmagic.spell.summon;

/** Serpensortia: more and longer-lived snakes with rank, Poison II from Expert, Parseltongue at Master. */
public final class SerpensortiaRules {
    public static final int MODE_FOLLOW = 0;
    public static final int MODE_GUARD = 1;
    public static final int POISON_TICKS = 60;
    public static final double COMMAND_RANGE = 24.0;
    public static final double GUARD_RADIUS = 8.0;
    public static final double GUARD_LEASH = 4.0;
    public static final int COMMAND_COOLDOWN_TICKS = 10;
    public static final double FOLLOW_START = 5.0;
    public static final double FOLLOW_STOP = 2.0;
    public static final double TELEPORT_DISTANCE = 16.0;
    public static final double SPAWN_SPACING = 0.8;
    public static final double DISMISS_RADIUS = 64.0;
    /** A conjured snake whose owner cannot be found for this many consecutive ticks vanishes. */
    public static final int OWNER_GONE_TICKS = 40;
    private static final int[] COUNT = {1, 2, 3, 3};
    private static final int[] LIFETIME = {400, 600, 800, 1200};
    private static final int[] POISON_AMPLIFIER = {0, 0, 1, 1};

    private SerpensortiaRules() {
    }

    public static int snakeCount(int rank) {
        return COUNT[RankIndex.of(rank)];
    }

    public static int lifetimeTicks(int rank) {
        return LIFETIME[RankIndex.of(rank)];
    }

    public static int poisonAmplifier(int rank) {
        return POISON_AMPLIFIER[RankIndex.of(rank)];
    }

    public static boolean parseltongue(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** A commanded attack on another player needs the server's PvP on and the caster able to harm them. */
    public static boolean commandMayHitPlayer(boolean pvpAllowed, boolean casterCanHarm) {
        return pvpAllowed && casterCanHarm;
    }

    public static int toggledMode(int mode) {
        return mode == MODE_FOLLOW ? MODE_GUARD : MODE_FOLLOW;
    }

    /** Sideways offset of snake {@code index} of {@code count}, centred on the impact. */
    public static double spawnOffset(int index, int count) {
        return (index - (count - 1) / 2.0) * SPAWN_SPACING;
    }
}
