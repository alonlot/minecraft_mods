package com.hogwartsmagic.pensieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandMemoryRulesTest {
    @Test
    void leavingBeforeThePlayStartedDoesNotUseUpTheMemory() {
        assertTrue(WandMemory.marksDone(ExitReason.END, true));
        assertTrue(WandMemory.marksDone(ExitReason.LEFT, true));
        assertFalse(WandMemory.marksDone(ExitReason.LEFT, false), "left during the build or fade: never saw it");
        assertFalse(WandMemory.marksDone(ExitReason.YANK, true));
    }
}
