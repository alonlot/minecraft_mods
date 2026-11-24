package com.hogwartsmagic.locket;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GobletRulesTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test void otherPlayerCannotDipWhileSomeoneDrinks() {
        assertTrue(GobletRules.mayDip(null, a, 0), "a full basin takes anyone's goblet");
        assertFalse(GobletRules.mayDip(a, b, 2), "someone else is mid-run");
        assertFalse(GobletRules.mayDip(a, b, 0), "a claimed basin is claimed even before the first sip");
    }

    @Test void drinkerCanDipUntilSix() {
        for (int drinks = 0; drinks < DespairRules.DRINKS_TO_EMPTY; drinks++) {
            assertTrue(GobletRules.mayDip(a, a, drinks), "dip number " + (drinks + 1));
        }
        assertFalse(GobletRules.mayDip(a, a, DespairRules.DRINKS_TO_EMPTY), "six cups empty it");
    }

    @Test void nobodyCanDipLootedBasin() {
        assertFalse(GobletRules.mayDip(null, a, DespairRules.DRINKS_TO_EMPTY));
        assertFalse(GobletRules.mayDip(a, a, DespairRules.DRINKS_TO_EMPTY + 1));
        assertFalse(GobletRules.mayDip(null, a, -1), "a nonsense count is refused");
    }

    @Test void gobletCrumblesOnlyOutsideTheCave() {
        assertFalse(GobletRules.leavesCave(true));
        assertTrue(GobletRules.leavesCave(false));
    }
}
