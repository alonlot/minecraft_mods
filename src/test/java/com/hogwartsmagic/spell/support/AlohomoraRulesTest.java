package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlohomoraRulesTest {
    @Test
    void ironFromAdept() {
        assertFalse(AlohomoraRules.opensIron(1));
        assertTrue(AlohomoraRules.opensIron(2));
        assertTrue(AlohomoraRules.opensIron(4));
    }

    @Test
    void areaFromExpert() {
        assertEquals(0, AlohomoraRules.areaRadius(1));
        assertEquals(0, AlohomoraRules.areaRadius(2));
        assertEquals(4, AlohomoraRules.areaRadius(3));
        assertEquals(4, AlohomoraRules.areaRadius(4));
    }

    @Test
    void vaultsAndWardsAtMaster() {
        assertFalse(AlohomoraRules.opensVaults(3));
        assertTrue(AlohomoraRules.opensVaults(4));
        assertFalse(AlohomoraRules.suppressesWards(3));
        assertTrue(AlohomoraRules.suppressesWards(4));
        assertEquals(100, AlohomoraRules.IRON_OPEN_TICKS);
        assertEquals(100, AlohomoraRules.WARD_SUPPRESS_TICKS);
    }
}
