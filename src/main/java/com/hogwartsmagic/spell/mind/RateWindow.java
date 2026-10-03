package com.hogwartsmagic.spell.mind;

/** A fixed-window limiter: at most {@code capacity} acquisitions per {@code windowTicks} of game time.
 * It is a fixed window, so up to twice the capacity can pass across a window edge. */
public final class RateWindow {
    private final int capacity;
    private final long windowTicks;
    private long windowStart = Long.MIN_VALUE;
    private int used;

    public RateWindow(int capacity, long windowTicks) {
        if (capacity < 1 || windowTicks < 1) {
            throw new IllegalArgumentException("capacity and windowTicks must be at least 1");
        }
        this.capacity = capacity;
        this.windowTicks = windowTicks;
    }

    public boolean tryAcquire(long now) {
        if (this.windowStart == Long.MIN_VALUE || now < this.windowStart || now - this.windowStart >= this.windowTicks) {
            this.windowStart = now;
            this.used = 0;
        }
        if (this.used >= this.capacity) {
            return false;
        }
        this.used++;
        return true;
    }
}
