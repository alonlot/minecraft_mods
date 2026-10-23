package com.hogwartsmagic.fire;

import com.hogwartsmagic.pensieve.scene.DragonFire;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wild Horntail fire: the aim sync and decode, and the scorch book (fade, caps, pruning, cleanup). */
class WildFireTest {
    private static final double[] UP = {0, 1, 0};

    // ---- The aim ----------------------------------------------------------------------------------------------

    @Test
    void aimRoundTripsAsAUnitVector() {
        Vec3 aim = new Vec3(3, -1, 2);
        double[] d = BreathAim.decode(BreathAim.encode(aim));
        double l = aim.length();
        assertArrayEquals(new double[]{aim.x / l, aim.y / l, aim.z / l}, d, 1e-6);
        assertEquals(1.0, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
    }

    @Test
    void zeroOrBadAimReadsAsDefault() {
        assertArrayEquals(new double[]{0, 0, 1}, BreathAim.decode(BreathAim.encode(Vec3.ZERO)), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, BreathAim.decode(new Vector3f(0, 0, 0)), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, BreathAim.decode(new Vector3f(Float.NaN, 0, 0)), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, BreathAim.decode(null), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, BreathAim.decode(BreathAim.encode(new Vec3(Double.NaN, 1, 1))), 1e-9);
    }

    @Test
    void encodedAimIsAFreshCopy() {
        Vector3f a = BreathAim.encode(Vec3.ZERO);
        a.set(5, 5, 5);
        assertEquals(1f, BreathAim.DEFAULT.z, 0f);
        assertEquals(0f, BreathAim.DEFAULT.x, 0f);
    }

    @Test
    void blendIsUnitAndHitsTheEnds() {
        double[] a = {1, 0, 0}, b = {0, 0, 1};
        assertArrayEquals(a, BreathAim.blend(a, b, 0), 1e-9);
        assertArrayEquals(b, BreathAim.blend(a, b, 1), 1e-9);
        double[] mid = BreathAim.blend(a, b, 0.5);
        assertEquals(1.0, Math.sqrt(mid[0] * mid[0] + mid[1] * mid[1] + mid[2] * mid[2]), 1e-9);
        assertEquals(mid[0], mid[2], 1e-9);
        // Opposite aims never give a zero vector.
        double[] opp = BreathAim.blend(a, new double[]{-1, 0, 0}, 0.5);
        assertEquals(1.0, Math.sqrt(opp[0] * opp[0] + opp[1] * opp[1] + opp[2] * opp[2]), 1e-9);
    }

    // ---- The fade ---------------------------------------------------------------------------------------------

    @Test
    void soot_holdsThenFadesOverThirtyToSixtySeconds() {
        assertEquals(1.0, WildScorch.fade(100, 100), 0);
        assertEquals(1.0, WildScorch.fade(100 + WildScorch.HOLD_TICKS, 100), 0);
        double mid = WildScorch.fade(100 + WildScorch.HOLD_TICKS + WildScorch.FADE_TICKS / 2, 100);
        assertTrue(mid > 0.3 && mid < 0.7, "halfway through the fade: " + mid);
        assertEquals(0.0, WildScorch.fade(100 + WildScorch.HOLD_TICKS + WildScorch.FADE_TICKS, 100), 0);
        // Gone between 30 s and 60 s after the last hit (600 and 1200 ticks).
        double gone = WildScorch.HOLD_TICKS + WildScorch.FADE_TICKS;
        assertTrue(gone >= 600 && gone <= 1200, "gone after " + gone + " ticks");
        // Never rises again as time goes on.
        double prev = 1;
        for (double t = 0; t < 1500; t += 10) {
            double f = WildScorch.fade(t, 0);
            assertTrue(f <= prev + 1e-12);
            prev = f;
        }
        // A spot with no hit yet (every hit still to come) is not faded.
        assertEquals(1.0, WildScorch.fade(5, Double.NEGATIVE_INFINITY), 0);
    }

    // ---- The book ---------------------------------------------------------------------------------------------

    private static void hit(WildScorch b, int owner, double x, double t) {
        b.hit(owner, x, 64, 0, UP, t);
    }

    @Test
    void nearbyHitsJoinOneSpot() {
        WildScorch b = new WildScorch();
        hit(b, 1, 0, 1);
        hit(b, 1, 0.5, 2);
        hit(b, 1, 3, 3);
        assertEquals(2, b.size());
        // Another dragon never joins a spot that is not its own.
        hit(b, 2, 0, 4);
        assertEquals(3, b.size());
    }

    @Test
    void perDragonCapEvictsTheOldest() {
        WildScorch b = new WildScorch();
        for (int i = 0; i < WildScorch.PER_DRAGON + 5; i++) hit(b, 1, i * 10, i);
        assertEquals(WildScorch.PER_DRAGON, b.count(1));
        // The oldest five went; the newest is there.
        List<DragonFire.Spot> spots = b.spots(1);
        assertEquals(5 * 10, spots.get(0).x, 0);
        assertEquals((WildScorch.PER_DRAGON + 4) * 10, spots.get(spots.size() - 1).x, 0);
    }

    @Test
    void totalCapHoldsAcrossDragons() {
        WildScorch b = new WildScorch();
        for (int owner = 0; owner < 10; owner++) {
            for (int i = 0; i < WildScorch.PER_DRAGON; i++) hit(b, owner, i * 10, owner * 1000 + i);
        }
        assertTrue(b.size() <= WildScorch.TOTAL, "total " + b.size());
        assertEquals(WildScorch.TOTAL, b.size());
        // The newest dragon kept its newest spot.
        assertTrue(b.count(9) > 0);
    }

    @Test
    void evictionsAreReportedSoTheirDrawingCanBeForgotten() {
        WildScorch b = new WildScorch();
        List<DragonFire.Spot> all = new java.util.ArrayList<>();
        for (int i = 0; i < WildScorch.PER_DRAGON; i++) hit(b, 1, i * 10, i);
        all.addAll(b.spots(1));
        List<DragonFire.Spot> ev = b.hit(1, 9999, 64, 0, UP, 100);
        assertEquals(1, ev.size());
        assertTrue(all.contains(ev.get(0)));
        assertFalse(b.all().contains(ev.get(0)));
    }

    @Test
    void aSpotsHitsAreCappedSoItNeverGrowsWithoutEnd() {
        WildScorch b = new WildScorch();
        for (int i = 0; i < WildScorch.MAX_HITS * 3; i++) hit(b, 1, 0, i * 0.25);
        DragonFire.Spot s = b.all().get(0);
        assertEquals(WildScorch.MAX_HITS, s.hitCount());
        // Still fully blackened.
        assertEquals(DragonFire.SCORCH_MAX, s.radius(1e9), 1e-9);
    }

    @Test
    void fadedSpotsArePrunedAndSoAreSpotsWhoseBlockIsGone() {
        WildScorch b = new WildScorch();
        hit(b, 1, 0, 0);
        hit(b, 1, 50, 0);
        hit(b, 1, 100, 1000);
        double late = WildScorch.HOLD_TICKS + WildScorch.FADE_TICKS + 10;
        // At `late` only the two hit at 0 have faded.
        List<DragonFire.Spot> gone = b.prune(late, s -> false);
        assertEquals(2, gone.size());
        assertEquals(1, b.size());
        // The block under the last one went.
        gone = b.prune(late, s -> s.x == 100);
        assertEquals(1, gone.size());
        assertEquals(0, b.size());
    }

    @Test
    void aFadedSpotKeepsLivingWhileItIsStillBeingHit() {
        WildScorch b = new WildScorch();
        for (int i = 0; i < 2000; i += 100) hit(b, 1, 0, i);
        assertTrue(b.prune(1900 + 100, s -> false).isEmpty());
        assertEquals(1, b.size());
    }

    @Test
    void removingADragonClearsItsSpotsOnly() {
        WildScorch b = new WildScorch();
        for (int i = 0; i < 5; i++) {
            hit(b, 1, i * 10, i);
            hit(b, 2, i * 10, i);
        }
        List<DragonFire.Spot> gone = b.removeOwner(1);
        assertEquals(5, gone.size());
        assertEquals(0, b.count(1));
        assertEquals(5, b.count(2));
    }

    @Test
    void retainOwnersDropsEveryoneNotLive() {
        WildScorch b = new WildScorch();
        for (int owner = 1; owner <= 4; owner++) hit(b, owner, 0, 0);
        Set<Integer> live = new HashSet<>(List.of(2, 4));
        assertEquals(2, b.retainOwners(live).size());
        assertEquals(2, b.size());
        assertEquals(0, b.count(1));
        b.clear();
        assertEquals(0, b.size());
    }

    @Test
    void churnOfDragonsLeavesNothingBehind() {
        WildScorch b = new WildScorch();
        for (int round = 0; round < 200; round++) {
            for (int i = 0; i < 8; i++) hit(b, round, i * 10, round * 10 + i);
            assertTrue(b.size() <= WildScorch.TOTAL);
            if (round >= 3) b.removeOwner(round - 3);
        }
        for (int owner = 0; owner < 200; owner++) b.removeOwner(owner);
        assertEquals(0, b.size());
    }
}
