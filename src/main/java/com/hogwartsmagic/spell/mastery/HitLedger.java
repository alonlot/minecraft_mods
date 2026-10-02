package com.hogwartsmagic.spell.mastery;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers until when each award key is closed, so repeat hits inside a window earn nothing.
 * Storing the expiry (not the award time) makes pruning exact for every window length: an entry
 * is dropped the moment it can no longer refuse anything.
 */
public final class HitLedger {
    private static final int PRUNE_AT_SIZE = 256;

    private final Map<String, Long> closedUntil = new HashMap<>();

    /** True if {@code key} would pay out at {@code now}. Records nothing. */
    public boolean isOpen(String key, long now) {
        Long until = closedUntil.get(key);
        return until == null || now >= until;
    }

    public boolean tryAward(String key, long now, long windowTicks) {
        if (!isOpen(key, now)) {
            return false;
        }
        if (closedUntil.size() >= PRUNE_AT_SIZE) {
            closedUntil.values().removeIf(until -> now >= until);
        }
        closedUntil.put(key, now + windowTicks);
        return true;
    }

    public int size() {
        return closedUntil.size();
    }
}
