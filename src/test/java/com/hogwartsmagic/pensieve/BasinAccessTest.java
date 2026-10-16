package com.hogwartsmagic.pensieve;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasinAccessTest {
    private static final UUID POURER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Test
    void pourerCanAlwaysTakeOut() {
        assertTrue(BasinAccess.canTakeOut(POURER, POURER, false, false, true));
    }

    @Test
    void strangerCannotTakeOutPrivate() {
        assertFalse(BasinAccess.canTakeOut(POURER, STRANGER, false, false, false));
    }

    @Test
    void strangerCanTakeOutEmptyPublic() {
        assertTrue(BasinAccess.canTakeOut(null, STRANGER, false, true, false));
    }

    @Test
    void strangerCannotTakeOutPublicWithViewers() {
        assertFalse(BasinAccess.canTakeOut(null, STRANGER, false, true, true));
    }

    @Test
    void opCanAlwaysTakeOut() {
        assertTrue(BasinAccess.canTakeOut(POURER, STRANGER, true, false, true));
    }
}
