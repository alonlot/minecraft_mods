package com.hogwartsmagic.portkey;

import com.hogwartsmagic.portkey.PortkeyRules.Candidate;
import com.hogwartsmagic.portkey.PortkeyRules.Refusal;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who travels, where a Portkey may go, and where an Old Portkey throws you. */
class PortkeyRulesTest {

    private static Candidate player(double distSq) {
        return new Candidate(distSq, true, false, false, false, false, false);
    }

    @Test
    void travelsRules() {
        assertTrue(PortkeyRules.travels(player(3.9)), "a player inside two blocks travels");
        assertFalse(PortkeyRules.travels(player(4.1)), "a player just outside two blocks stays");
        assertTrue(PortkeyRules.travels(new Candidate(1.0, false, true, false, false, false, false)),
                "a standing tamed pet travels");
        assertFalse(PortkeyRules.travels(new Candidate(1.0, false, true, true, false, false, false)),
                "a sitting pet stays");
        assertFalse(PortkeyRules.travels(new Candidate(1.0, false, false, false, false, false, false)),
                "an untamed animal stays");
        assertFalse(PortkeyRules.travels(new Candidate(1.0, true, false, false, true, false, false)),
                "someone held by Incarcerous or Levicorpus stays");
        assertFalse(PortkeyRules.travels(new Candidate(1.0, true, false, false, false, true, false)),
                "a spectator or Pensieve viewer stays");
        assertFalse(PortkeyRules.travels(new Candidate(1.0, true, false, false, false, false, true)),
                "someone already on a journey is not taken twice");
    }

    @Test
    void refusals() {
        assertEquals(Refusal.INTO_MEMORY, PortkeyRules.refusal("minecraft:overworld", PortkeyRules.MEMORY));
        assertEquals(Refusal.INTO_ALLEY, PortkeyRules.refusal("minecraft:the_nether", PortkeyRules.ALLEY));
        assertEquals(Refusal.OUT_OF_MEMORY, PortkeyRules.refusal(PortkeyRules.MEMORY, "minecraft:overworld"));
        assertEquals(Refusal.NONE, PortkeyRules.refusal(PortkeyRules.ALLEY, "minecraft:overworld"));
        assertEquals(Refusal.NONE, PortkeyRules.refusal("minecraft:overworld", "minecraft:the_nether"));
    }

    @Test
    void binding() {
        assertFalse(PortkeyRules.canBindIn(PortkeyRules.MEMORY));
        assertFalse(PortkeyRules.canBindIn(PortkeyRules.ALLEY));
        assertTrue(PortkeyRules.canBindIn("minecraft:the_end"));
    }

    @Test
    void oldDimensions() {
        assertTrue(PortkeyRules.oldWorksIn("minecraft:overworld", true, false));
        assertFalse(PortkeyRules.oldWorksIn("minecraft:the_nether", true, true));
        assertFalse(PortkeyRules.oldWorksIn("minecraft:the_end", false, false));
        assertFalse(PortkeyRules.oldWorksIn(PortkeyRules.MEMORY, true, false));
        assertFalse(PortkeyRules.oldWorksIn(PortkeyRules.ALLEY, true, false));
    }

    @Test
    void cooldown() {
        assertEquals(1200L, PortkeyRules.cooldownLeft(100, 1300));
        assertEquals(0L, PortkeyRules.cooldownLeft(1400, 1300));
        assertEquals(1, PortkeyRules.cooldownSeconds(1));
        assertEquals(60, PortkeyRules.cooldownSeconds(1200));
    }

    @Test
    void oldOffsetAnnulus() {
        double[] east = PortkeyRules.oldOffset(0.0, 0.0);
        assertEquals(2000.0, east[0], 1e-6);
        assertEquals(0.0, east[1], 1e-6);
        double[] south = PortkeyRules.oldOffset(0.25, 1.0);
        assertEquals(0.0, south[0], 1e-6);
        assertEquals(4000.0, south[1], 1e-6);

        Random random = new Random(1234L);
        double[] lengths = new double[1000];
        for (int i = 0; i < lengths.length; i++) {
            double[] o = PortkeyRules.oldOffset(random.nextDouble(), random.nextDouble());
            lengths[i] = Math.hypot(o[0], o[1]);
            assertTrue(lengths[i] >= 2000.0 - 1e-6 && lengths[i] <= 4000.0 + 1e-6, "length " + lengths[i]);
        }
        Arrays.sort(lengths);
        double median = (lengths[499] + lengths[500]) / 2.0;
        double expected = Math.sqrt((2000.0 * 2000.0 + 4000.0 * 4000.0) / 2.0);
        assertEquals(expected, median, expected * 0.02, "area-uniform median");
    }

    @Test
    void border() {
        assertTrue(PortkeyRules.insideBorder(0, 0, -100, -100, 100, 100, 16));
        assertFalse(PortkeyRules.insideBorder(90, 0, -100, -100, 100, 100, 16));
    }

    @Test
    void dimensionNames() {
        assertEquals("portkey.hogwartsmagic.dim.minecraft.overworld", PortkeyRules.dimensionKey("minecraft:overworld"));
        assertEquals("deep dark realm", PortkeyRules.fallbackDimensionName("foo:deep_dark_realm"));
    }

    @Test
    void waitGivesUp() {
        assertFalse(PortkeyRules.waitGivesUp(199));
        assertTrue(PortkeyRules.waitGivesUp(200));
    }
}
