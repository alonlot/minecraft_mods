package com.hogwartsmagic.spell.motion;

/**
 * Wingardium Leviosa: from Expert it lifts blocks and throws what it holds; at Master it holds two and
 * throws harder. Also the carried block's physics.
 */
public final class WingardiumRules {
    /** Stone's hardness: anything harder stays put. */
    public static final float MAX_HARDNESS = 1.5F;
    /** A carried block counts as held for this long after the channel last steered it. */
    public static final int HELD_GRACE_TICKS = 3;
    /** A loose carried block that hasn't landed after this long lands where it is. */
    public static final int IDLE_MAX_TICKS = 1200;
    public static final double GRAVITY = 0.04;
    public static final double DRAG = 0.98;
    public static final double THROW_SPEED = 1.6;
    public static final double THROW_LIFT = 0.1;
    public static final int THROW_TRACK_TICKS = 40;
    public static final double SECOND_TARGET_RADIUS = 3.0;
    /** Sideways distance of each of two held things from the centre of the hold. */
    public static final double PAIR_SPACING = 0.9;
    /** Same as the channel's own hold range (ModEvents.WINGARDIUM_HOLD_RANGE): the second held thing slips away past it. */
    public static final double HOLD_RANGE = 7.5;
    private static final float[] THROW_DAMAGE = {0.0F, 0.0F, 3.0F, 6.0F};

    private WingardiumRules() {
    }

    public static boolean liftsBlocks(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static boolean canThrow(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static int maxHeld(int rank) {
        return RankIndex.of(rank) == 3 ? 2 : 1;
    }

    public static float throwDamage(int rank) {
        return THROW_DAMAGE[RankIndex.of(rank)];
    }

    /**
     * A plain block: not air, no block entity (no inventories or vaults), not replaceable (grass, snow
     * layers, fire), no fluid in it, not half of a two-part or extended block, not tagged immune, and
     * hardness between 0 and stone's (bedrock and similar are -1).
     */
    public static boolean isLiftable(float hardness, boolean air, boolean hasBlockEntity, boolean replaceable,
                                     boolean fluidEmpty, boolean multipart, boolean immune) {
        if (air || hasBlockEntity || replaceable || !fluidEmpty || multipart || immune) {
            return false;
        }
        return hardness >= 0.0F && hardness <= MAX_HARDNESS;
    }

    public static boolean risingEdge(boolean wasDown, boolean isDown) {
        return isDown && !wasDown;
    }
}
