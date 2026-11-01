package com.hogwartsmagic.client.ambience;

import com.hogwartsmagic.pensieve.scene.CeilingSky;

/**
 * When a world ceiling's roof starts to dissolve, on the {@link AmbienceClock} clock, as the sky drawing wants it
 * (the reveal is {@code CeilingSky.revealAt(now - start)}). Pure, so the rule can be tested: a sealed ceiling (the
 * castle's hall) shows its plank roof until this player is told its reveal started, or that they already had it; an
 * ordinary ceiling (a spell's) is open; a reveal the server told always plays from its start.
 */
public final class WorldCeilingReveal {
    /** A start so far ahead that the reveal is 0 for as long as the game runs: the roof stays solid. */
    public static final float NEVER = Float.MAX_VALUE;
    /** A start long enough ago that the reveal is complete. */
    public static final float LONG_AGO = -2f * CeilingSky.DEFAULT_REVEAL_TICKS;

    private WorldCeilingReveal() {
    }

    /**
     * @param sealed    the ceiling starts as a plank roof
     * @param opened    the server said this player already had it revealed
     * @param told      a reveal was started for this player
     * @param toldClock when it started (only read if {@code told})
     */
    public static float start(boolean sealed, boolean opened, boolean told, float toldClock) {
        if (told) {
            return toldClock;
        }
        return sealed && !opened ? NEVER : LONG_AGO;
    }
}
