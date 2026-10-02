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
 */
public final class DiminishingReturns {
    public static final long WINDOW_TICKS = 120L;
    public static final double SECOND_HIT_FACTOR = 0.5;
    private static final int PRUNE_AT_SIZE = 256;

    private record Entry(int landed, long windowEnd) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** Records a hard-CC attempt on {@code target} and returns its duration factor: 1.0, 0.5 or 0.0. */
    public double apply(UUID target, long now) {
        Entry entry = live(target, now);
        if (entry == null) {
            if (entries.size() >= PRUNE_AT_SIZE) {
                entries.values().removeIf(e -> now >= e.windowEnd());
            }
            entries.put(target, new Entry(1, now + WINDOW_TICKS));
            return 1.0;
        }
        if (entry.landed() == 1) {
            entries.put(target, new Entry(2, now + WINDOW_TICKS));
            return SECOND_HIT_FACTOR;
        }
        return 0.0;
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
