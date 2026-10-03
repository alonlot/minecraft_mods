package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlatusRulesTest {
    @Test
    void fasterInfusionWithRank() {
        assertEquals(110, AlatusRules.infusionTicks(1));
        assertEquals(80, AlatusRules.infusionTicks(2));
        assertEquals(60, AlatusRules.infusionTicks(3));
        assertEquals(40, AlatusRules.infusionTicks(4));
    }

    @Test
    void qualityRollsByRank() {
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(1, 0.0));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(2, 0.14));
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(2, 0.15));
        assertEquals(AlatusRules.QUALITY_MASTERWORK, AlatusRules.rollQuality(3, 0.04));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(3, 0.05));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(3, 0.349));
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(3, 0.35));
        assertEquals(AlatusRules.QUALITY_MASTERWORK, AlatusRules.rollQuality(4, 0.149));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(4, 0.15));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(4, 0.549));
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(4, 0.55));
    }

    @Test
    void reinfuseAtMasterOnly() {
        assertFalse(AlatusRules.reinfuses(3));
        assertTrue(AlatusRules.reinfuses(4));
        assertEquals(4, AlatusRules.REINFUSE_DUST_COST);
    }

    @Test
    void speedAndStamina() {
        assertEquals(1.0, AlatusRules.speedMultiplier(0), 1e-9);
        assertEquals(1.05, AlatusRules.speedMultiplier(1), 1e-9);
        assertEquals(1.10, AlatusRules.speedMultiplier(2), 1e-9);
        assertEquals(1.10, AlatusRules.speedMultiplier(9), 1e-9);
        assertEquals(1.0, AlatusRules.staminaDrainMultiplier(1), 1e-9);
        assertEquals(1.0 / 1.15, AlatusRules.staminaDrainMultiplier(2), 1e-9);
        assertEquals(0, AlatusRules.clampQuality(-3));
        assertEquals(2, AlatusRules.clampQuality(5));
    }

    @Test
    void outOfRangeRanksClampAndNaNRollIsNormal() {
        assertEquals(AlatusRules.infusionTicks(1), AlatusRules.infusionTicks(0));
        assertEquals(AlatusRules.infusionTicks(1), AlatusRules.infusionTicks(-1));
        assertEquals(AlatusRules.infusionTicks(4), AlatusRules.infusionTicks(5));
        assertEquals(AlatusRules.fineChance(4), AlatusRules.fineChance(5), 1e-9);
        assertEquals(AlatusRules.masterworkChance(1), AlatusRules.masterworkChance(0), 1e-9);
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(4, Double.NaN));
    }
}
