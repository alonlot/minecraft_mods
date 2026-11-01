package com.hogwartsmagic.client.ambience;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The clock the world's ambient effects (candles, sky, light rays) are drawn from. It is the level's game time, so
 * every player sees the same candles bob and the same lightning strike, but handed to the renderers as a small float:
 * a float holds a whole tick exactly only to 16.7 million ticks (233 hours), and the renderers sample it with a
 * fraction of a tick, so the game time is reduced first.
 *
 * <p>The reduction is the game time modulo {@link #EPOCH} (2^20 ticks, 14.5 hours), taken afresh from the current game
 * time every time the clock is read. That keeps the numbers small and, because it depends on nothing but the game
 * time, makes every client read exactly the same value on the same tick, however long it has been in the world: a
 * player who joined an hour ago and one who joined a week ago see the same candles and the same storm. The cost is
 * one step back to 0 at each epoch boundary (a candle phase and the lightning schedule jump once every 14.5 hours);
 * differences between two times read on the same tick (a reveal's elapsed time) are unaffected. There is no state to
 * reset on a level change or a logout. Render thread only.
 */
public final class AmbienceClock {
    /** Ticks per epoch (2^20, about 14.5 hours). */
    public static final long EPOCH = 1L << 20;

    private AmbienceClock() {
    }

    /** The base for game time {@code gameTime}: the start of the epoch it falls in. */
    public static long baseFor(long gameTime) {
        return Math.floorDiv(gameTime, EPOCH) * EPOCH;
    }

    /** Ticks since {@code base} at {@code gameTime} plus a partial tick, computed wide and narrowed once. */
    public static float elapsed(long base, long gameTime, float partial) {
        return (float) ((gameTime - base) + (double) partial);
    }

    /** The clock at game time {@code gameTime} with a partial tick: the same on every client. */
    public static float shared(long gameTime, float partial) {
        return elapsed(baseFor(gameTime), gameTime, partial);
    }

    /** The clock now, on this level's game time with this frame's partial tick. */
    public static float now(ClientLevel level, float partial) {
        return shared(level.getGameTime(), partial);
    }

    /**
     * The clock at another whole game tick on the same footing as {@link #now} (the epoch of the current game time), so
     * {@code now - at(...)} is the real elapsed time even when the two fall either side of a boundary.
     */
    public static float at(ClientLevel level, long gameTime) {
        return elapsed(baseFor(level.getGameTime()), gameTime, 0f);
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
