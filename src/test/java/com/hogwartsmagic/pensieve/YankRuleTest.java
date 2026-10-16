package com.hogwartsmagic.pensieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YankRuleTest {
    @Test
    void belowAThirdYanks() {
        assertTrue(YankRule.shouldYank(6.66f, 20f));
    }

    @Test
    void atAThirdStays() {
        assertFalse(YankRule.shouldYank(6.67f, 20f));
    }

    @Test
    void fullHealthStays() {
        assertFalse(YankRule.shouldYank(20f, 20f));
    }
}
