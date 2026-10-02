package com.hogwartsmagic.spell.mastery;

import java.util.HashMap;
import java.util.Map;

/** Remembers when each award key last paid out, so repeat hits inside a window earn nothing. */
public final class HitLedger {
    /** Longer than any gate window (Avada's 12000-tick cooldown), so pruning never forgets a live entry. */
    static final long PRUNE_AGE_TICKS = 24000L;
    private static final int PRUNE_AT_SIZE = 256;

    private final Map<String, Long> lastAward = new HashMap<>();

    public boolean tryAward(String key, long now, long windowTicks) {
        Long last = lastAward.get(key);
        if (last != null && now - last < windowTicks) {
            return false;
        }
        if (lastAward.size() >= PRUNE_AT_SIZE) {
            lastAward.values().removeIf(time -> now - time >= PRUNE_AGE_TICKS);
        }
        lastAward.put(key, now);
        return true;
    }

    public int size() {
        return lastAward.size();
    }
}
