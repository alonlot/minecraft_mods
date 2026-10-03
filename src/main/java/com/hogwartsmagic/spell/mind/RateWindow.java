package com.hogwartsmagic.spell.mind;

/** A fixed-window limiter: at most {@code capacity} acquisitions per {@code windowTicks} of game time. */
public final class RateWindow {
    private final int capacity;
    private final long windowTicks;
    private long windowStart = Long.MIN_VALUE;
    private int used;

    public RateWindow(int capacity, long windowTicks) {
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
