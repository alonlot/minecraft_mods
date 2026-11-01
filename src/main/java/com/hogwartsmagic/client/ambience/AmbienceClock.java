package com.hogwartsmagic.client.ambience;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The clock the world's ambient effects (candles, sky, light rays) are drawn from. It is the level's game time, so
 * every player sees the same candles bob and the same lightning strike, but handed to the renderers as a small float:
 * a float holds a whole tick exactly only to 16.7 million ticks (233 hours), and the renderers sample it with a
 * fraction of a tick, so the game time is reduced first.
 *
 * <p>The reduction is a per-session base, the game time when this client got its level rounded down to a multiple of
 * {@link #EPOCH}: the clock never jumps or wraps while the session runs, the numbers stay small, and every player who
 * joined within the same epoch (nearly everyone, an epoch is 14.5 hours) reads the same time. A base of the exact join
 * time would be just as smooth but would give every player a different candle phase and a different thunderstorm.
 * Render thread only.
 */
public final class AmbienceClock {
    /** Ticks per epoch (2^20, about 14.5 hours). */
    public static final long EPOCH = 1L << 20;

    private static ClientLevel baseLevel;
    private static long base;

    private AmbienceClock() {
    }

    /** The base for a client that got its level at game time {@code joinGameTime}. */
    public static long baseFor(long joinGameTime) {
        return Math.floorDiv(joinGameTime, EPOCH) * EPOCH;
    }

    /** Ticks since {@code base} at {@code gameTime} plus a partial tick, computed wide and narrowed once. */
    public static float elapsed(long base, long gameTime, float partial) {
        return (float) ((gameTime - base) + (double) partial);
    }

    /** The clock now, on this level's game time with this frame's partial tick. */
    public static float now(ClientLevel level, float partial) {
        return elapsed(baseOf(level), level.getGameTime(), partial);
    }

    /** The clock at a past (or future) whole game tick, e.g. when a reveal started. */
    public static float at(ClientLevel level, long gameTime) {
        return elapsed(baseOf(level), gameTime, 0f);
    }

    private static long baseOf(ClientLevel level) {
        if (level != baseLevel) {
            baseLevel = level;
            base = baseFor(level.getGameTime());
        }
        return base;
    }

    /**
     * How much daylight there is for a sun at {@code sunAngleRad} (vanilla's {@code Level#getSunAngle}: 0 at noon, half
     * a turn at midnight), 0 to 1: full in the middle of the day, falling to nothing as the sun reaches the horizon.
     */
    public static float daylight(float sunAngleRad) {
        float c = (float) Math.cos(sunAngleRad);
        return Math.max(0f, Math.min(1f, c * 2f));
    }
}
