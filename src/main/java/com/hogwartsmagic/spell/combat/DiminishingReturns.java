package com.hogwartsmagic.spell.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hard crowd-control diminishing returns, per target. The first hard CC lands in full; a second
 * one within {@link #WINDOW_TICKS} of it lasts {@link #SECOND_HIT_FACTOR}; after that nothing lands
 * until {@link #WINDOW_TICKS} have passed since that second one. Attempts that land nothing do not
 * extend the window, so a target can always count on getting out. Without this, Stupefy into
 * Petrificus into Incarcerous locks someone down forever.
 *
 * <p>Callers MUST: {@code factor = peek(...)}, {@code ticks = CrowdControlMath.scaledTicks(base, factor, ...)},
 * then {@code commit(...)} only if {@code ticks > 0}, so a hit that scales to nothing never counts.
 *
 * <p>Server-thread only; not synchronized.
 */
public final class DiminishingReturns {
    public static final long WINDOW_TICKS = 120L;
    public static final double SECOND_HIT_FACTOR = 0.5;
    private static final int PRUNE_AT_SIZE = 256;

    private record Entry(int landed, long windowEnd) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /**
     * Compatibility shorthand: {@link #peek} then {@link #commit}. It records the hit even if the
     * final duration rounds to 0, so new callers should follow the contract in the class doc instead.
     */
    // Package-private on purpose: production code goes through peek/commit (see CrowdControl); only
    // the tests in this package use this shorthand.
    double apply(UUID target, long now) {
        double factor = peek(target, now);
        if (factor > 0.0) {
            commit(target, now);
        }
        return factor;
    }

    /**
     * Records a hard CC that actually landed (scaled ticks above 0) on {@code target}. Attempts that
     * are immune, or that scale down to 0 ticks, must not be committed.
     */
    public void commit(UUID target, long now) {
        Entry entry = live(target, now);
        if (entry == null) {
            if (entries.size() >= PRUNE_AT_SIZE) {
                entries.values().removeIf(e -> now >= e.windowEnd());
            }
            entries.put(target, new Entry(1, now + WINDOW_TICKS));
        } else if (entry.landed() == 1) {
            entries.put(target, new Entry(2, now + WINDOW_TICKS));
        }
    }

    /** The factor {@link #apply} would return now, without recording anything. */
    public double peek(UUID target, long now) {
        Entry entry = live(target, now);
        if (entry == null) {
            return 1.0;
        }
        return entry.landed() == 1 ? SECOND_HIT_FACTOR : 0.0;
    }

    public void clear(UUID target) {
        entries.remove(target);
    }

    public void clearAll() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    private Entry live(UUID target, long now) {
        Entry entry = entries.get(target);
        return entry != null && now < entry.windowEnd() ? entry : null;
    }
}
