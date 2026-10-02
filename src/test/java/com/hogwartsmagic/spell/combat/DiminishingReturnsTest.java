package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiminishingReturnsTest {
    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);

    @Test
    void firstFullSecondHalfThirdNothing() {
        DiminishingReturns dr = new DiminishingReturns();
        assertEquals(1.0, dr.apply(A, 0L), 1e-9);
        assertEquals(0.5, dr.apply(A, 40L), 1e-9);
        assertEquals(0.0, dr.apply(A, 80L), 1e-9);
    }

    @Test
    void windowRestartsFromTheSecondLandedHit() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        dr.apply(A, 100L);
        assertEquals(0.0, dr.apply(A, 219L), 1e-9);
        assertEquals(1.0, dr.apply(A, 220L), 1e-9);
    }

    @Test
    void immuneAttemptsDoNotExtendImmunity() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        dr.apply(A, 10L);
        assertEquals(0.0, dr.apply(A, 100L), 1e-9);
        assertEquals(0.0, dr.apply(A, 129L), 1e-9);
        assertEquals(1.0, dr.apply(A, 130L), 1e-9);
    }

    @Test
    void sixSecondsAfterALoneHitIsFresh() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        assertEquals(1.0, dr.apply(A, 120L), 1e-9);
    }

    @Test
    void targetsAreIndependent() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        assertEquals(1.0, dr.apply(B, 0L), 1e-9);
        assertEquals(0.5, dr.apply(A, 1L), 1e-9);
    }

    @Test
    void peekDoesNotRecord() {
        DiminishingReturns dr = new DiminishingReturns();
        assertEquals(1.0, dr.peek(A, 0L), 1e-9);
        assertEquals(1.0, dr.apply(A, 0L), 1e-9);
        assertEquals(0.5, dr.peek(A, 10L), 1e-9);
        assertEquals(0.5, dr.apply(A, 10L), 1e-9);
    }

    @Test
    void clearForgetsATarget() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        dr.apply(A, 1L);
        dr.clear(A);
        assertEquals(1.0, dr.apply(A, 2L), 1e-9);
    }

    @Test
    void expiredEntriesArePruned() {
        DiminishingReturns dr = new DiminishingReturns();
        for (int i = 0; i < 300; i++) {
            dr.apply(new UUID(1L, i), 0L);
        }
        dr.apply(A, 120L);
        assertEquals(1, dr.size());
    }

    @Test
    void uncommittedZeroTickHitLeavesTheChainAtOne() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.commit(A, 0L);
        double factor = dr.peek(A, 10L);
        assertEquals(0.5, factor, 1e-9);
        int ticks = CrowdControlMath.scaledTicks(1, factor, false, true, 0.0);
        assertEquals(0, ticks);
        // ticks == 0, so the caller does not commit; the target is still at landed == 1.
        assertEquals(0.5, dr.peek(A, 20L), 1e-9);
        assertEquals(0.5, dr.apply(A, 20L), 1e-9);
    }

    @Test
    void peekAtTheLastTickOfTheWindowStillHalves() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.commit(A, 0L);
        assertEquals(0.5, dr.peek(A, 119L), 1e-9);
        assertEquals(1.0, dr.peek(A, 120L), 1e-9);
    }
}
