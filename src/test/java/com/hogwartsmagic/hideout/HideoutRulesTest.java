package com.hogwartsmagic.hideout;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HideoutRulesTest {
    @Test void leaderNameIsStable() {
        assertEquals(HideoutRules.leaderName(42L), HideoutRules.leaderName(42L));
    }

    @Test void everyNameIsReachableAndNoneBlank() {
        Set<String> seen = new HashSet<>();
        for (long s = 0; s < 2000; s++) {
            String n = HideoutRules.leaderName(s * 7919L - 1000L);
            assertFalse(n.isBlank());
            seen.add(n);
        }
        assertEquals(new HashSet<>(HideoutRules.LEADER_NAMES), seen);
    }

    @Test void leaderStaysPutInsideTheLeash() {
        assertFalse(HideoutRules.shouldHeadHome(0.0, false));
        assertFalse(HideoutRules.shouldHeadHome(5 * 5, false));
    }

    @Test void leaderTurnsBackBeyondTheLeash() {
        assertTrue(HideoutRules.shouldHeadHome(10 * 10, false));
    }

    @Test void returningLeaderWalksAllTheWayHome() {
        assertTrue(HideoutRules.shouldHeadHome(5 * 5, true));
        assertFalse(HideoutRules.shouldHeadHome(2 * 2, true));
    }

    @Test void soulRisesClearOfTheRooms() {
        assertTrue(HideoutRules.SOUL_RISE_BLOCKS >= 10);
    }
}
