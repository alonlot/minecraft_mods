package com.hogwartsmagic.spell.support.seal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SealRulesTest {
    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);
    private static final UUID CARA = new UUID(0L, 3L);

    private static Seal seal(UUID owner, int rank) {
        return new Seal(owner, "p", rank, 0, 64, 0, SealRules.radius(rank), 0L);
    }

    @Test
    void radiusGrowsWithRank() {
        assertEquals(1, SealRules.radius(1));
        assertEquals(2, SealRules.radius(2));
        assertEquals(3, SealRules.radius(3));
        assertEquals(4, SealRules.radius(4));
        assertEquals(SealRules.MAX_RADIUS, SealRules.radius(4));
    }

    @Test
    void capDoublesWithRank() {
        assertEquals(1, SealRules.cap(1));
        assertEquals(2, SealRules.cap(2));
        assertEquals(4, SealRules.cap(3));
        assertEquals(8, SealRules.cap(4));
    }

    @Test
    void ranksOutsideOneToFourClamp() {
        assertEquals(1, SealRules.radius(0));
        assertEquals(4, SealRules.radius(9));
        assertEquals(1, SealRules.cap(-3));
        assertEquals(8, SealRules.cap(5));
    }

    @Test
    void coversASphereAroundTheTarget() {
        Seal s = new Seal(ALICE, "p", 2, 10, 64, -5, 2, 0L);
        assertTrue(SealRules.covers(s, 10, 64, -5));
        assertTrue(SealRules.covers(s, 12, 64, -5)); // straight out to the radius
        assertTrue(SealRules.covers(s, 11, 65, -4)); // 1 + 1 + 1 = 3 <= 4
        assertFalse(SealRules.covers(s, 12, 65, -5)); // 4 + 1 = 5 > 4
        assertFalse(SealRules.covers(s, 10, 67, -5));
        Seal novice = new Seal(ALICE, "p", 1, 0, 64, 0, 1, 0L);
        assertTrue(SealRules.covers(novice, 0, 65, 0)); // the other half of a door
        assertFalse(SealRules.covers(novice, 1, 65, 0));
    }

    @Test
    void breaksAtAStrictlyHigherRank() {
        assertTrue(SealRules.breaks(2, 1));
        assertTrue(SealRules.breaks(4, 3));
        assertFalse(SealRules.breaks(1, 1));
        assertFalse(SealRules.breaks(3, 4));
        assertFalse(SealRules.breaks(2, 3));
    }

    @Test
    void aMasterAlohomoraBreaksAMasterSeal() {
        assertTrue(SealRules.breaks(4, 4));
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 4)), ALICE, 4, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(0, d.target());
    }

    @Test
    void anExpertAlohomoraDoesNotBreakAnExpertSeal() {
        assertFalse(SealRules.breaks(3, 3));
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 3)), ALICE, 3, true);
        assertEquals(SealRules.Verdict.REFUSE, d.verdict());
        assertEquals(0, d.target());
    }

    @Test
    void noSealPasses() {
        SealRules.Decision d = SealRules.decide(List.of(), ALICE, 1, true);
        assertEquals(SealRules.Verdict.PASS, d.verdict());
        assertEquals(-1, d.target());
    }

    @Test
    void theSealerPassesTheirOwnSeal() {
        assertEquals(SealRules.Verdict.PASS, SealRules.decide(List.of(seal(ALICE, 4)), ALICE, 1, true).verdict());
    }

    @Test
    void anEqualOrLowerRankIsRefused() {
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 2)), ALICE, 2, true);
        assertEquals(SealRules.Verdict.REFUSE, d.verdict());
        assertEquals(0, d.target());
        assertEquals(SealRules.Verdict.REFUSE, SealRules.decide(List.of(seal(BOB, 3)), ALICE, 1, true).verdict());
        assertEquals(SealRules.Verdict.REFUSE, SealRules.decide(List.of(seal(BOB, 2)), null, 2, true).verdict()); // an NPC
    }

    @Test
    void aHigherRankBreaksTheSeal() {
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 2)), ALICE, 3, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(0, d.target());
        assertEquals(SealRules.Verdict.BREAK, SealRules.decide(List.of(seal(BOB, 1)), null, 3, true).verdict()); // a boss NPC
    }

    @Test
    void theHighestCoveringSealDecides() {
        List<Seal> covering = List.of(seal(BOB, 1), seal(CARA, 3), seal(BOB, 2));
        SealRules.Decision expert = SealRules.decide(covering, ALICE, 3, true);
        assertEquals(SealRules.Verdict.REFUSE, expert.verdict());
        assertEquals(1, expert.target());
        SealRules.Decision master = SealRules.decide(covering, ALICE, 4, true);
        assertEquals(SealRules.Verdict.BREAK, master.verdict());
        assertEquals(1, master.target());
    }

    @Test
    void ownSealsAreIgnoredAmongOthers() {
        SealRules.Decision d = SealRules.decide(List.of(seal(ALICE, 4), seal(BOB, 1)), ALICE, 2, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(1, d.target());
    }

    @Test
    void theOldestOfEqualSealsBreaksFirst() {
        SealRules.Decision d = SealRules.decide(List.of(seal(BOB, 2), seal(CARA, 2)), ALICE, 3, true);
        assertEquals(SealRules.Verdict.BREAK, d.verdict());
        assertEquals(0, d.target());
    }

    @Test
    void aPuppetCastNeitherPassesNorBreaks() {
        // Through a puppet the caster counts as nobody (null) and may not break.
        SealRules.Decision d = SealRules.decide(List.of(seal(ALICE, 1)), null, 4, false);
        assertEquals(SealRules.Verdict.REFUSE, d.verdict());
        assertEquals(0, d.target());
    }

    @Test
    void evictionsKeepTheCap() {
        assertEquals(0, SealRules.toEvict(0, 1));
        assertEquals(1, SealRules.toEvict(1, 1));
        assertEquals(0, SealRules.toEvict(3, 4));
        assertEquals(1, SealRules.toEvict(4, 4));
        assertEquals(2, SealRules.toEvict(9, 8)); // more than the cap after a rank drop
        assertEquals(2, SealRules.toEvict(2, 1));
    }
}
