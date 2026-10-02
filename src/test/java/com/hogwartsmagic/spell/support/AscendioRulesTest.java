package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AscendioRulesTest {
    @Test
    void apexMatchesTheVanillaJump() {
        // A vanilla jump starts at 0.42 blocks/tick and peaks at about 1.252 blocks.
        assertEquals(1.252, AscendioRules.apexHeight(0.42), 0.01);
        assertEquals(0.0, AscendioRules.apexHeight(0.0), 1e-9);
    }

    @Test
    void launchVelocityReachesTheHeight() {
        for (int rank = 1; rank <= 4; rank++) {
            double blocks = AscendioRules.heightBlocks(rank);
            double apex = AscendioRules.apexHeight(AscendioRules.launchVelocity(blocks));
            assertTrue(apex >= blocks - 1e-6, "rank " + rank + " apex " + apex);
            assertTrue(apex < blocks + 0.3, "rank " + rank + " apex " + apex);
        }
        assertTrue(AscendioRules.launchVelocity(15) > AscendioRules.launchVelocity(9));
        assertTrue(AscendioRules.launchVelocity(9) > AscendioRules.launchVelocity(6));
        assertEquals(0.0, AscendioRules.launchVelocity(0), 1e-9);
    }

    @Test
    void heightsPerRank() {
        assertEquals(6.0, AscendioRules.heightBlocks(1), 1e-9);
        assertEquals(9.0, AscendioRules.heightBlocks(2), 1e-9);
        assertEquals(12.0, AscendioRules.heightBlocks(3), 1e-9);
        assertEquals(15.0, AscendioRules.heightBlocks(4), 1e-9);
    }

    @Test
    void selfCastIsAimingDownOrCrouching() {
        assertTrue(AscendioRules.wantsSelfCast(70f, false));
        assertTrue(AscendioRules.wantsSelfCast(60f, false));
        assertTrue(AscendioRules.wantsSelfCast(10f, true));
        assertFalse(AscendioRules.wantsSelfCast(10f, false));
    }

    @Test
    void unlocks() {
        assertFalse(AscendioRules.doubleCasts(2));
        assertTrue(AscendioRules.doubleCasts(3));
        assertFalse(AscendioRules.surges(3));
        assertTrue(AscendioRules.surges(4));
        assertFalse(AscendioRules.pushesOnTakeoff(3));
        assertTrue(AscendioRules.pushesOnTakeoff(4));
    }
}
