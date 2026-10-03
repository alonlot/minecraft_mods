package com.hogwartsmagic.spell.mind;

/** Crucio: a held channel. The caster is rooted, the victim stunned and hurt; at Master a survivor flees. */
public final class CrucioRules {
    private static final int[] CHANNEL_TICKS = {40, 60, 80, 100};
    public static final int DAMAGE_INTERVAL = 10;
    public static final float DAMAGE = 1.5f;
    public static final int HEARTBEAT_TIMEOUT = 6;
    public static final double MAX_RANGE = 24.0;
    public static final int FLEE_TICKS = 200;

    private CrucioRules() {
    }

    public static int channelTicks(int rank) {
        return CHANNEL_TICKS[RankIndex.of(rank)];
    }

    public static boolean fleesAfter(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Damage lands every half second after the channel starts (elapsed 10, 20, ...). */
    public static boolean isDamageTick(long elapsed) {
        return elapsed > 0 && elapsed % DAMAGE_INTERVAL == 0;
    }

    public static float fullChannelDamage(int rank) {
        return (channelTicks(rank) / DAMAGE_INTERVAL) * DAMAGE;
    }

    public static boolean heartbeatLapsed(long now, long lastSeen) {
        return now - lastSeen > HEARTBEAT_TIMEOUT;
    }
}
