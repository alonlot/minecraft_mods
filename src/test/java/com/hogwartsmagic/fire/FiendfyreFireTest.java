package com.hogwartsmagic.fire;

import com.hogwartsmagic.pensieve.scene.DragonFire;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Fiendfyre serpent's share of the dragon's fire: the roar beat and the loose puffs shed off what burns. */
class FiendfyreFireTest {

    // ---- The roar -----------------------------------------------------------------------------------------------

    @Test
    void noRoarBeforeTheFirst() {
        for (int t = 0; t < SerpentRoar.FIRST; t++) {
            assertEquals(-1, SerpentRoar.roarTick(t));
            assertEquals(-1, SerpentRoar.breathAge(t));
            assertEquals(-1, SerpentRoar.charge(t));
            assertEquals(0, SerpentRoar.gape(SerpentRoar.roarTick(t)));
        }
    }

    @Test
    void eachRoarGathersThenBreathesForItsLengthThenRests() {
        for (int n = 0; n < 3; n++) {
            int start = SerpentRoar.FIRST + n * SerpentRoar.PERIOD;
            int breathing = 0;
            for (int t = start; t < start + SerpentRoar.PERIOD; t++) {
                int r = t - start;
                boolean gathering = r < SerpentRoar.WINDUP;
                assertEquals(gathering, SerpentRoar.charge(t) >= 0, "charge at roar tick " + r);
                if (SerpentRoar.breathAge(t) >= 0) {
                    assertEquals(r - SerpentRoar.WINDUP, SerpentRoar.breathAge(t));
                    assertFalse(gathering);
                    breathing++;
                }
            }
            assertEquals(SerpentRoar.BREATH, breathing);
        }
    }

    @Test
    void theJawsAreWideWhileTheFirePours() {
        assertEquals(0, SerpentRoar.gape(0), 1e-6);
        for (int r = SerpentRoar.WINDUP; r < SerpentRoar.WINDUP + SerpentRoar.BREATH; r++) {
            assertEquals(1, SerpentRoar.gape(r + 0.5f), 1e-6);
        }
        assertEquals(0, SerpentRoar.gape(SerpentRoar.WINDUP + SerpentRoar.BREATH + 6), 1e-6);
        // It eases: never a jump of more than a third between ticks.
        for (float t = 0; t < SerpentRoar.PERIOD; t += 1) {
            assertTrue(Math.abs(SerpentRoar.gape(t + 1) - SerpentRoar.gape(t)) < 0.34f, "jump at " + t);
        }
    }

    // ---- The loose puffs ----------------------------------------------------------------------------------------

    private static LooseFlame.Puff puff(boolean smokes) {
        return new LooseFlame.Puff(10, 64, -5, 0.02, 0.06, 0, 0.4, 100, 12, 1, 0, 0.1, 0.5, smokes);
    }

    @Test
    void aPuffRisesSwellsAndCoolsOverItsLife() {
        LooseFlame.Puff p = puff(true);
        DragonFire.Flame early = LooseFlame.flame(p, 1), late = LooseFlame.flame(p, 10);
        assertNotNull(early);
        assertNotNull(late);
        assertTrue(late.y() > early.y());
        assertTrue(late.radius() > early.radius());
        assertTrue(late.heat() < early.heat());
        assertTrue(late.alpha() < early.alpha());
        assertNull(LooseFlame.flame(p, -0.5));
        assertNull(LooseFlame.flame(p, 12.5));
        assertEquals(0, LooseFlame.flame(p, 12).alpha(), 1e-9);
    }

    @Test
    void itsSmokeRollsUpOffWhereItBurnedOutAndIsGoneInTime() {
        LooseFlame.Puff p = puff(true);
        assertNull(LooseFlame.smoke(p, 5));
        DragonFire.Smoke a = LooseFlame.smoke(p, 12), b = LooseFlame.smoke(p, 30);
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(b.y() > a.y());
        assertTrue(b.soot() > a.soot());
        assertNull(LooseFlame.smoke(p, 12 - 3 + LooseFlame.SMOKE_TICKS + 0.5));
        assertFalse(p.gone(100 + 12 + LooseFlame.SMOKE_TICKS));
        assertTrue(p.gone(100 + 12 + LooseFlame.SMOKE_TICKS + 0.1));
    }

    @Test
    void aCleanPuffLeavesNoSmokeAndGoesWithItsFlame() {
        LooseFlame.Puff p = puff(false);
        for (double a = 0; a < 60; a += 0.5) assertNull(LooseFlame.smoke(p, a));
        assertFalse(p.gone(112));
        assertTrue(p.gone(112.1));
    }
}
