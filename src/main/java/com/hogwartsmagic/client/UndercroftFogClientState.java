package com.hogwartsmagic.client;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The one undercroft the client is currently standing in, and the fog it gets.
 *
 * <p><b>Why this is not a biome.</b> A carved region with its own fog is exactly what a biome is
 * for, and it was the first plan. It does not work in 1.21: there is no per-section biome delta
 * packet, so clients cache biomes from the initial chunk packet and re-syncing means resending
 * forty-nine whole chunks immediately after streaming four hundred thousand block changes into
 * them, which is a visible relight under a standing player. One small packet on entry and one on
 * exit does the same job for nothing.
 *
 * <p>It also buys something a biome could not: <b>fog that changes with depth</b>. The colour walks
 * from the warm soot of the bank's own workings at the top to near-black at the bottom, and the far
 * plane opens as it goes.
 *
 * <p>That far plane is the trick the whole chasm rests on, and its value is not free choice. It has
 * to be <em>wider than the chasm</em> (about seventy blocks) so the sight-lines across it survive,
 * and <em>shorter than the chasm is deep</em> (seventy-eight) so the floor never resolves. Ninety-six
 * is the only comfortable place to stand between those two numbers - and it is also the reason the
 * footprint is small. A chasm two hundred across would need a far plane of two hundred and twenty,
 * and at two hundred and twenty you can see the floor.
 *
 * <p>The colour is near-black on purpose rather than grey. At that distance grey reads as
 * <em>mist</em>, and mist makes a big space look small and weather-beaten. This has to read as an
 * absence of light.
 */
@OnlyIn(Dist.CLIENT)
public final class UndercroftFogClientState {

    /** Warm soot, at the top, where the bank's own workings still are. */
    private static final float[] TOP = {0.10F, 0.075F, 0.055F};
    /** Near-black, at the bottom. Not grey: grey at range reads as mist. */
    private static final float[] BOTTOM = {0.012F, 0.014F, 0.020F};

    private static final float FAR_TOP = 48.0F;
    private static final float FAR_BOTTOM = 96.0F;
    private static final float NEAR = 1.0F;

    /**
     * The fog stops at the rotunda floor, not at the region box.
     *
     * <p>The bank hall shares its floor level with the station, and the region is a column forty-
     * eight blocks either side of it, so a headroom of even a couple of blocks fogs the banking
     * hall - a lit marble room - in chasm black. Below the floor only, which is also the honest
     * boundary: the fog starts the moment the lift does.
     */
    private static final int HEADROOM = 0;

    @Nullable
    private static BlockPos centre;
    private static int radius;
    private static int topY;
    private static int bottomY;

    private UndercroftFogClientState() {
    }

    /** A radius of zero clears it. */
    public static void set(BlockPos where, int r, int top, int bottom) {
        if (r <= 0) {
            centre = null;
            return;
        }
        centre = where;
        radius = r;
        topY = top;
        bottomY = bottom;
    }

    public static void clear() {
        centre = null;
    }

    /**
     * How far down the band the camera is, 0 at the station and 1 at the bottom, or -1 if the
     * camera is not in an undercroft at all.
     */
    public static float depthAt(double x, double y, double z) {
        BlockPos c = centre;
        if (c == null) {
            return -1.0F;
        }
        if (Math.abs(x - c.getX()) > radius || Math.abs(z - c.getZ()) > radius) {
            return -1.0F;
        }
        if (y >= topY + HEADROOM || y < bottomY - 32) {
            return -1.0F;
        }
        int span = Math.max(1, topY - bottomY);
        return (float) Math.min(1.0D, Math.max(0.0D, (topY - y) / (double) span));
    }

    public static float red(float depth) {
        return lerp(TOP[0], BOTTOM[0], depth);
    }

    public static float green(float depth) {
        return lerp(TOP[1], BOTTOM[1], depth);
    }

    public static float blue(float depth) {
        return lerp(TOP[2], BOTTOM[2], depth);
    }

    public static float near() {
        return NEAR;
    }

    public static float far(float depth) {
        return lerp(FAR_TOP, FAR_BOTTOM, depth);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
