package com.hogwartsmagic.spell;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simulated duels: a player (A) pressing every {@code pressEvery} ticks against an NPC (B). */
class PrioriRulesTest {
    private static final int RUNS = 400;

    private static int[] duel(int pressEvery, float npcPressure, long seed) {
        Random random = new Random(seed);
        int[] tally = new int[4];
        for (int run = 0; run < RUNS; run++) {
            float bead = 0.0f;
            int age = 0;
            int result = PrioriRules.ONGOING;
            int offset = random.nextInt(4);
            while (result == PrioriRules.ONGOING) {
                age++;
                float pushA = pressEvery > 0 && (age + offset) % pressEvery == 0 ? PrioriRules.PLAYER_PUSH : 0.0f;
                float pushB = PrioriRules.npcPush(npcPressure, random.nextFloat());
                bead = PrioriRules.step(bead, pushA, pushB, (random.nextFloat() - 0.5f) * 0.01f);
                result = PrioriRules.outcome(bead, age);
            }
            tally[result]++;
        }
        return tally;
    }

    @Test
    void holdingTheKeyBeatsADeathEater() {
        int[] t = duel(4, PrioriRules.WIZARD_PRESSURE, 1L);
        assertEquals(RUNS, t[PrioriRules.A_WINS], "holding should win every time");
    }

    @Test
    void holdingTheKeyBeatsAStudent() {
        assertEquals(RUNS, duel(4, PrioriRules.STUDENT_PRESSURE, 2L)[PrioriRules.A_WINS]);
    }

    @Test
    void standingIdleLoses() {
        assertEquals(RUNS, duel(0, PrioriRules.WIZARD_PRESSURE, 3L)[PrioriRules.B_WINS]);
        assertEquals(RUNS, duel(0, PrioriRules.STUDENT_PRESSURE, 4L)[PrioriRules.B_WINS]);
    }

    @Test
    void theDarkLordBeatsAHeldKeyButNotFastClicking() {
        int[] held = duel(4, PrioriRules.VOLDEMORT_PRESSURE, 5L);
        assertTrue(held[PrioriRules.B_WINS] > RUNS * 0.6, "holding should mostly lose to him: " + held[PrioriRules.B_WINS]);
        int[] clicking = duel(2, PrioriRules.VOLDEMORT_PRESSURE, 6L);
        assertEquals(RUNS, clicking[PrioriRules.A_WINS], "10 clicks a second should beat him");
    }

    @Test
    void almostNoDuelEndsWithoutAWinner() {
        for (int every : new int[]{0, 2, 3, 4, 5, 6}) {
            for (float pressure : new float[]{PrioriRules.STUDENT_PRESSURE, PrioriRules.WIZARD_PRESSURE,
                    PrioriRules.VOLDEMORT_PRESSURE}) {
                int[] t = duel(every, pressure, every * 31L + (long) (pressure * 1000));
                assertTrue(t[PrioriRules.BLOW_APART] < RUNS * 0.15,
                        "every " + every + " vs " + pressure + ": " + t[PrioriRules.BLOW_APART] + " stand-offs");
            }
        }
    }

    @Test
    void outcomeAtTheTimeLimitGoesToTheLeader() {
        assertEquals(PrioriRules.A_WINS, PrioriRules.outcome(0.3f, PrioriRules.MAX_TICKS));
        assertEquals(PrioriRules.B_WINS, PrioriRules.outcome(-0.3f, PrioriRules.MAX_TICKS));
        assertEquals(PrioriRules.BLOW_APART, PrioriRules.outcome(0.05f, PrioriRules.MAX_TICKS));
        assertEquals(PrioriRules.ONGOING, PrioriRules.outcome(0.99f, 10));
    }
}
