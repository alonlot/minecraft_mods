package com.hogwartsmagic.locket;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The pure rules of the cave goblet: who may dip it in the basin, and when it crumbles. */
public final class GobletRules {
    private GobletRules() {
    }

    /**
     * Whether {@code who} may dip a goblet into a basin whose current drinker is {@code drinker} (null when nobody
     * has started) and which has had {@code drinks} cups drunk. Once six are drunk the basin is empty.
     */
    public static boolean mayDip(@Nullable UUID drinker, UUID who, int drinks) {
        if (drinks < 0 || drinks >= DespairRules.DRINKS_TO_EMPTY) {
            return false;
        }
        return drinker == null || drinker.equals(who);
    }

    /** A goblet that is no longer inside its cave crumbles. */
    public static boolean leavesCave(boolean inCave) {
        return !inCave;
    }
}
