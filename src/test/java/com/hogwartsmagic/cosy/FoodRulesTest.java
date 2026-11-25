package com.hogwartsmagic.cosy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chocolate Courage has to outlast the Dementor's once-a-second aura pulse, or it would shield nobody. */
class FoodRulesTest {

    @Test
    void courageLongerThanAuraPulse() {
        assertTrue(FoodRules.COURAGE_TICKS > 20, "courage must cover more than one aura pulse");
        assertEquals(FoodRules.COURAGE_TICKS, CosyFood.COURAGE_TICKS);
    }
}
