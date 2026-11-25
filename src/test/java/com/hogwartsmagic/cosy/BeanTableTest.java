package com.hogwartsmagic.cosy;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Bertie Bott's bean table: weights, effect lengths, unique flavours, and the good/neutral/silly split. */
class BeanTableTest {

    @Test
    void atLeastSixteenFlavours() {
        assertTrue(BeanTable.ALL.size() >= 16, "only " + BeanTable.ALL.size());
    }

    @Test
    void everyOutcomeHasWeightAndSaneEffectTicks() {
        for (BeanTable.Outcome o : BeanTable.ALL) {
            assertTrue(o.weight() > 0, o.flavourKey() + " has no weight");
            assertTrue(o.ticks() == 0 || (o.ticks() >= 40 && o.ticks() <= 600),
                    o.flavourKey() + " ticks " + o.ticks());
            if (o.effect() == null) {
                assertEquals(0, o.ticks(), o.flavourKey() + " has ticks but no effect");
            } else {
                assertTrue(o.ticks() > 0, o.flavourKey() + " has an effect but no ticks");
            }
        }
    }

    @Test
    void levitationNeverLastsMoreThanThreeSeconds() {
        boolean any = false;
        for (BeanTable.Outcome o : BeanTable.ALL) {
            if (o.effect() != null && o.effect().getPath().equals("levitation")) {
                any = true;
                assertTrue(o.ticks() <= 60, o.flavourKey() + " levitates for " + o.ticks());
            }
        }
        assertTrue(any, "a levitation bean exists");
    }

    @Test
    void flavourKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (BeanTable.Outcome o : BeanTable.ALL) {
            assertTrue(keys.add(o.flavourKey()), "duplicate " + o.flavourKey());
        }
    }

    @Test
    void roughlyFortyThirtyThirtySplit() {
        int[] weight = new int[3];
        for (BeanTable.Outcome o : BeanTable.ALL) {
            weight[BeanTable.category(o)] += o.weight();
        }
        int total = weight[0] + weight[1] + weight[2];
        assertEquals(40, Math.round(100f * weight[BeanTable.GOOD] / total));
        assertEquals(30, Math.round(100f * weight[BeanTable.NEUTRAL] / total));
        assertEquals(30, Math.round(100f * weight[BeanTable.SILLY] / total));
    }

    @Test
    void rollFollowsTheWeights() {
        RandomSource r = RandomSource.create(7);
        int[] seen = new int[3];
        for (int i = 0; i < 20_000; i++) {
            seen[BeanTable.category(BeanTable.roll(r))]++;
        }
        assertTrue(seen[BeanTable.GOOD] > 7_200 && seen[BeanTable.GOOD] < 8_800, "good " + seen[BeanTable.GOOD]);
        assertTrue(seen[BeanTable.NEUTRAL] > 5_200 && seen[BeanTable.NEUTRAL] < 6_800, "neutral " + seen[BeanTable.NEUTRAL]);
        assertTrue(seen[BeanTable.SILLY] > 5_200 && seen[BeanTable.SILLY] < 6_800, "silly " + seen[BeanTable.SILLY]);
    }
}
