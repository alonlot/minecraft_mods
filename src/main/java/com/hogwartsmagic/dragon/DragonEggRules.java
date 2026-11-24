package com.hogwartsmagic.dragon;

/**
 * The numbers behind stealing a dragon egg: how far a dragon notices the theft, how long it
 * holds the grudge, how far it chases, and what Borgin pays. The player sees every dragon
 * within sixty-four blocks turn on them for five minutes, longer while they carry the egg.
 * Plain statics so they can be unit tested without Minecraft.
 */
public final class DragonEggRules {
    /** How far, in blocks, a dragon notices an egg being taken. */
    public static final double THEFT_RADIUS = 64.0D;
    /** How long a grudge lasts, in ticks (five minutes). */
    public static final int GRUDGE_TICKS = 6000;
    /** How far a dragon keeps after a thief it already hunts. */
    public static final double PURSUIT_RANGE = 96.0D;
    /** How far from its nest a wild Ironbelly roams before heading home. */
    public static final double NEST_RADIUS = 12.0D;
    /** Emeralds Borgin pays for a Horntail egg. */
    public static final int HORNTAIL_EGG_PRICE = 32;
    /** Emeralds Borgin pays for the rarer Ironbelly egg. */
    public static final int IRONBELLY_EGG_PRICE = 40;

    private DragonEggRules() {
    }

    public static boolean raisesGrudge(boolean creative, boolean spectator) {
        return !creative && !spectator;
    }

    public static boolean notices(double distanceSqr) {
        return distanceSqr <= THEFT_RADIUS * THEFT_RADIUS;
    }

    public static long grudgeExpiry(long now) {
        return now + GRUDGE_TICKS;
    }

    public static boolean grudgeActive(long now, long expiresAt) {
        return now < expiresAt;
    }

    /** Carrying an egg inside pursuit range extends the grudge; it is never shortened. */
    public static long renewedExpiry(long now, long expiresAt, boolean thiefCarriesEgg, double distanceSqr) {
        if (thiefCarriesEgg && distanceSqr <= PURSUIT_RANGE * PURSUIT_RANGE) {
            return Math.max(expiresAt, now + GRUDGE_TICKS);
        }
        return expiresAt;
    }

    public static boolean pursues(long now, long expiresAt, double distanceSqr) {
        return grudgeActive(now, expiresAt) && distanceSqr <= PURSUIT_RANGE * PURSUIT_RANGE;
    }

    /** Only a freed, anchorless Ironbelly with no nest yet (the wild one) takes its spot as a nest. */
    public static boolean adoptsNest(boolean chained, boolean hasAnchor, boolean hasNest) {
        return !chained && !hasAnchor && !hasNest;
    }

    public static boolean shouldReturnHome(boolean hasTarget, double distanceSqrFromNest) {
        return !hasTarget && distanceSqrFromNest > NEST_RADIUS * NEST_RADIUS;
    }

    public static int priceEmeralds(boolean ironbelly) {
        return ironbelly ? IRONBELLY_EGG_PRICE : HORNTAIL_EGG_PRICE;
    }
}
