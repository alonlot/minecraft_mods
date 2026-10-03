package com.hogwartsmagic.spell.mind;

/**
 * Imperio. Ranks I-III make a tame-dog thrall; Master is possession (Phase 6b). Players and bosses get
 * the short column; mobs the long one, under a max-health cap that bosses ignore.
 */
public final class ImperioRules {
    private static final int[] MOB_TICKS = {400, 600, 900, 900};
    private static final int[] SHORT_TICKS = {60, 80, 100, 100};
    private static final int[] MAX_THRALLS = {1, 1, 2, 2};
    private static final float[] MAX_MOB_HEALTH = {40.0f, 60.0f, 100.0f, 100.0f};
    public static final double FOLLOW_START = 6.0;
    public static final double FOLLOW_STOP = 2.5;
    public static final double TELEPORT_DISTANCE = 12.0;
    public static final double MAX_TARGET_DISTANCE = 32.0;
    public static final double PLAYER_REACH = 3.0;
    public static final double PLAYER_WALK_SPEED = 0.21;
    public static final double PLAYER_CHASE_SPEED = 0.25;
    public static final int ASSIST_MEMORY_TICKS = 100;
    public static final float BRAWL_DAMAGE = 2.0f;
    public static final int BRAWL_INTERVAL = 20;

    private ImperioRules() {
    }

    public static int thrallTicks(int rank, boolean playerOrBoss) {
        return playerOrBoss ? SHORT_TICKS[RankIndex.of(rank)] : MOB_TICKS[RankIndex.of(rank)];
    }

    public static int maxThralls(int rank) {
        return MAX_THRALLS[RankIndex.of(rank)];
    }

    public static float maxMobHealth(int rank) {
        return MAX_MOB_HEALTH[RankIndex.of(rank)];
    }

    public static boolean grantsStrength(int rank) {
        return RankIndex.of(rank) >= 1;
    }

    public static boolean isPossessionRank(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean canEnthrall(int rank, float maxHealth, boolean boss, boolean player) {
        return player || boss || maxHealth <= maxMobHealth(rank);
    }

    public static boolean atCap(int rank, int current) {
        return current >= maxThralls(rank);
    }

    /** An owner's fight (who hurt them, whom they hurt) stays interesting for five seconds. */
    public static boolean fresh(long now, long at) {
        return now - at <= ASSIST_MEMORY_TICKS;
    }
}
