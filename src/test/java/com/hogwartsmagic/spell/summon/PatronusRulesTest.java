package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronusRulesTest {
    @Test
    void corporealChanceGrowsWithRank() {
        assertEquals(0.15f, PatronusRules.corporealChance(1), 1e-6);
        assertEquals(0.30f, PatronusRules.corporealChance(2), 1e-6);
        assertEquals(0.50f, PatronusRules.corporealChance(3), 1e-6);
        assertEquals(0.75f, PatronusRules.corporealChance(4), 1e-6);
    }

    @Test
    void outOfRangeRanksClamp() {
        assertEquals(0.15f, PatronusRules.corporealChance(0), 1e-6);
        assertEquals(0.15f, PatronusRules.corporealChance(-3), 1e-6);
        assertEquals(0.75f, PatronusRules.corporealChance(9), 1e-6);
        assertFalse(PatronusRules.personalForm(0));
        assertTrue(PatronusRules.personalForm(9));
    }

    @Test
    void rollIsStrictlyBelowTheChance() {
        assertTrue(PatronusRules.isCorporeal(1, 0.0f));
        assertTrue(PatronusRules.isCorporeal(1, 0.149f));
        assertFalse(PatronusRules.isCorporeal(1, 0.15f));
        assertTrue(PatronusRules.isCorporeal(4, 0.749f));
        assertFalse(PatronusRules.isCorporeal(4, 0.75f));
        assertFalse(PatronusRules.isCorporeal(4, 0.999f));
    }

    @Test
    void personalFormFromExpert() {
        assertFalse(PatronusRules.personalForm(1));
        assertFalse(PatronusRules.personalForm(2));
        assertTrue(PatronusRules.personalForm(3));
        assertTrue(PatronusRules.personalForm(4));
    }

    @Test
    void formIdsRoundTripAndUnknownIsGeneric() {
        for (PatronusForm form : PatronusForm.values()) {
            assertEquals(form, PatronusForm.byId(form.id()));
        }
        assertEquals(PatronusForm.GENERIC, PatronusForm.byId(-1));
        assertEquals(PatronusForm.GENERIC, PatronusForm.byId(99));
    }

    @Test
    void formIdsAreStable() {
        // Stored in player data and synced to clients: these numbers must never change meaning.
        assertEquals(0, PatronusForm.GENERIC.id());
        assertEquals(1, PatronusForm.STAG.id());
        assertEquals(2, PatronusForm.DOE.id());
        assertEquals(3, PatronusForm.HARE.id());
        assertEquals(4, PatronusForm.WOLF.id());
        assertEquals(5, PatronusForm.CAT.id());
        assertEquals(6, PatronusForm.OWL.id());
        assertEquals(7, PatronusForm.FOX.id());
        assertEquals(8, PatronusForm.PHOENIX.id());
    }

    @Test
    void pickIsDeterministicAndFromTheHousePool() {
        Random random = new Random(42);
        for (int house = -1; house < 4; house++) {
            Set<PatronusForm> pool = EnumSet.copyOf(Arrays.asList(PatronusForms.pool(house)));
            for (int i = 0; i < 500; i++) {
                long most = random.nextLong();
                long least = random.nextLong();
                PatronusForm form = PatronusForms.pick(most, least, house);
                assertTrue(pool.contains(form), "house " + house + " gave " + form);
                assertEquals(form, PatronusForms.pick(most, least, house));
                assertNotEquals(PatronusForm.GENERIC, form);
            }
        }
    }

    @Test
    void unsortedPoolHasAllEightAndUnknownHouseUsesIt() {
        assertEquals(8, PatronusForms.pool(-1).length);
        assertEquals(8, PatronusForms.pool(4).length);
        assertEquals(4, PatronusForms.pool(0).length);
    }

    @Test
    void everyFormInAPoolIsReachable() {
        Random random = new Random(7);
        for (int house = -1; house < 4; house++) {
            Set<PatronusForm> seen = EnumSet.noneOf(PatronusForm.class);
            for (int i = 0; i < 4000; i++) {
                seen.add(PatronusForms.pick(random.nextLong(), random.nextLong(), house));
            }
            assertEquals(PatronusForms.pool(house).length, seen.size(), "house " + house);
        }
    }

    @Test
    void poolIsACopy() {
        PatronusForm[] pool = PatronusForms.pool(0);
        pool[0] = PatronusForm.GENERIC;
        assertNotEquals(PatronusForm.GENERIC, PatronusForms.pool(0)[0]);
    }
}
