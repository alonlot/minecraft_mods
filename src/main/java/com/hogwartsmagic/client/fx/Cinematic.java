package com.hogwartsmagic.client.fx;

/**
 * The big-moment effects any client feature can call: letterbox bars, an impact flash, the colour draining out of
 * the world, a freeze-frame, and (single player only) the world slowing to a crawl. Each respects its
 * {@code fx.FxConfig} switch.
 *
 * <p>STUB: the signatures are the contract other features build against; the "cinematic moments" work fills them
 * in. Until then every call is a no-op.
 */
public final class Cinematic {

    private Cinematic() {
    }

    /** Black bars slide in, hold for {@code holdTicks}, slide out. Overlapping calls extend the hold. */
    public static void letterbox(int holdTicks) {
    }

    /** A full-screen flash of colour (0..1 rgb) at {@code strength} (0..1), fading over {@code ticks}. */
    public static void flash(float r, float g, float b, float strength, int ticks) {
    }

    /** The world's colour drains by {@code amount} (0..1, 1 = grey) and comes back over {@code ticks}. */
    public static void drain(float amount, int ticks) {
    }

    /** The picture holds still for {@code frames} rendered frames (an impact frame), then carries on. */
    public static void freeze(int frames) {
    }

    /**
     * Single player only (no-op otherwise or when switched off): the world runs at {@code rate} (0.1..1) of normal
     * speed for {@code realTicks} real ticks, then eases back.
     */
    public static void slowMotion(float rate, int realTicks) {
    }
}
