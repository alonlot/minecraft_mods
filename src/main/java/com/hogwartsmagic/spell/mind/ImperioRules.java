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
    /** A thrall player's client is told its velocity at least this often, and sooner only on a real change. */
    public static final int PUSH_RESEND_TICKS = 4;

    /** How long a Master holds the cast key before possession starts (the client's threshold; the server re-checks it). */
    public static final int HOLD_START_TICKS = 40;
    /** The server accepts a hold start this many ticks early, for latency and tick-alignment slop. */
    public static final int HOLD_SLACK_TICKS = 10;

    private ImperioRules() {
    }

    /** True when a Master cast at {@code castAt} has been held long enough by {@code now} to start possession. */
    public static boolean holdLongEnough(long castAt, long now) {
        return now - castAt >= HOLD_START_TICKS - HOLD_SLACK_TICKS;
    }

    /** Pure: the entity is a thrall of this very puppeteer. */
    public static boolean sameThrallHousehold(@javax.annotation.Nullable java.util.UUID puppeteer,
                                              @javax.annotation.Nullable java.util.UUID entityOwner) {
        return puppeteer != null && puppeteer.equals(entityOwner);
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

    /** Only a plain mob thrall can be extended by casting again; players and bosses can't be held on to. */
    public static boolean mayRefresh(boolean player, boolean boss) {
        return !player && !boss;
    }

    /** Is this owner still barred from this boss or player? */
    public static boolean lockedOut(long until, long now) {
        return now < until;
    }

    /** The lockout after a boss or player thrall: one Imperio cooldown, with the owner's mastery discount. */
    public static long lockoutTicks(double baseCooldownTicks, double masteryMultiplier) {
        return Math.max(1L, Math.round(baseCooldownTicks * masteryMultiplier));
    }

    public static boolean atCap(int rank, int current) {
        return current >= maxThralls(rank);
    }

    /** An owner's fight (who hurt them, whom they hurt) stays interesting for five seconds. */
    public static boolean fresh(long now, long at) {
        return now - at <= ASSIST_MEMORY_TICKS;
    }

    /**
     * Throttle for the motion packets that walk a thrall player: push when nothing was sent yet, when
     * {@link #PUSH_RESEND_TICKS} have passed, or when the wanted velocity really changed (a jump, a stop or
     * start, a speed change, or a turn of more than 15 degrees). Never one packet per tick.
     */
    public static boolean needsPush(boolean havePrevious, double lastX, double lastY, double lastZ,
                                    double wantX, double wantY, double wantZ, long lastAt, long now) {
        if (!havePrevious || now < lastAt || now - lastAt >= PUSH_RESEND_TICKS) {
            return true;
        }
        if (Math.abs(wantY - lastY) > 0.3) {
            return true;
        }
        double last = Math.sqrt(lastX * lastX + lastZ * lastZ);
        double want = Math.sqrt(wantX * wantX + wantZ * wantZ);
        if (Math.abs(last - want) > 0.02) {
            return true;
        }
        if (last < 1.0E-4 || want < 1.0E-4) {
            return false;
        }
        double cos = (lastX * wantX + lastZ * wantZ) / (last * want);
        return cos < Math.cos(Math.toRadians(15.0));
    }
}
