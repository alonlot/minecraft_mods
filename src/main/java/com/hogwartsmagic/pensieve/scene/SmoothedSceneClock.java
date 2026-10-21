package com.hogwartsmagic.pensieve.scene;

/**
 * A scene clock that eases toward the server's instead of jumping with it. Pure; no Minecraft types.
 *
 * <p>Vanilla overwrites the client's game time with the server's every 20 ticks, so a raw {@code gameTime + partial}
 * scene clock jumps by a tick about once a second whenever the two disagree, and every camera and actor jerks with it.
 * This clock instead keeps its own value: {@link #tick} is called once per client tick with the authoritative scene
 * time for that tick's end, advances by the rate chosen on the previous tick (so the clock is continuous from the
 * last frame of one tick to the first frame of the next), and then picks the next tick's rate, at most
 * {@link #MAX_SLEW} off 1, to close the error. An error over {@link #SNAP_TICKS} (a join, a long stall, a real desync)
 * is snapped at once. Frames read {@link #at}: the tick's base plus the partial tick times that rate, so the clock
 * only ever moves forward between snaps.
 */
public final class SmoothedSceneClock {
    /** Largest change of speed: 5%, so a one-tick error is absorbed over about a second. */
    public static final double MAX_SLEW = 0.05;
    /** An error larger than this many ticks is not eased but snapped. */
    public static final double SNAP_TICKS = 10;
    /** Rate change per tick of error, before the {@link #MAX_SLEW} cap: a small error closes exponentially. */
    public static final double GAIN = 0.2;

    private boolean running;
    private double base;
    private double rate = 1;
    private double lastError;

    /** Forget everything: the next {@link #tick} snaps to the authoritative time. */
    public void reset() {
        running = false;
        base = 0;
        rate = 1;
        lastError = 0;
    }

    public boolean running() {
        return running;
    }

    /**
     * One client tick has ended and {@code target} is the authoritative scene time at that moment. Returns true when
     * the clock snapped (first tick, or the error was over {@link #SNAP_TICKS}).
     */
    public boolean tick(double target) {
        if (!running) {
            snap(target);
            lastError = 0;
            return true;
        }
        base += rate;
        double err = target - base;
        lastError = err;
        if (Math.abs(err) > SNAP_TICKS) {
            snap(target);
            return true;
        }
        rate = 1 + Math.max(-MAX_SLEW, Math.min(MAX_SLEW, err * GAIN));
        return false;
    }

    private void snap(double target) {
        running = true;
        base = target;
        rate = 1;
    }

    /** The scene time of a frame {@code partial} (0..1) of a tick after the last {@link #tick}. */
    public double at(float partial) {
        return base + Math.max(0f, Math.min(1f, partial)) * rate;
    }

    /** The authoritative time minus this clock's, as measured at the last {@link #tick}. */
    public double lastError() {
        return lastError;
    }

    /** The speed the clock runs at until the next {@link #tick}, 1 +- {@link #MAX_SLEW}. */
    public double rate() {
        return rate;
    }
}
