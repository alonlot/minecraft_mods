package com.hogwartsmagic.pensieve.scene;

/** Scene time derived from the shared game clock, so a late joiner sees the current moment. */
public final class SessionClock {
    private SessionClock() {
    }

    public static float elapsed(long startGameTime, long gameTime, float partialTick) {
        return Math.max(0f, (gameTime - startGameTime) + partialTick);
    }

    public static boolean finished(long startGameTime, long gameTime, int length) {
        return gameTime - startGameTime >= length;
    }
}
