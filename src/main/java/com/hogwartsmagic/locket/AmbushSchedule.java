package com.hogwartsmagic.locket;

/** When ambush Inferi rise. Pure. */
public final class AmbushSchedule {
    public static final int TOTAL = 16;
    public static final int WAVE_SIZE = 4;
    public static final int WAVE_INTERVAL = 100;
    public static final int WARNING_MIN = 2;
    public static final int WARNING_MAX = 3;
    public static final int WARNING_COOLDOWN = 1200;

    /** Idle cave-bound Inferi allowed in a cave before warning grabs stop adding more. */
    public static final int MAX_IDLE_GUARDIANS = 6;

    private AmbushSchedule() {}

    public static int spawnsDueAt(int ticksSinceStart) {
        if (ticksSinceStart < 0 || ticksSinceStart % WAVE_INTERVAL != 0) return 0;
        int wave = ticksSinceStart / WAVE_INTERVAL;
        return wave < TOTAL / WAVE_SIZE ? WAVE_SIZE : 0;
    }

    public static boolean mayAddGuardian(int idleGuardians) {
        return idleGuardians < MAX_IDLE_GUARDIANS;
    }

    public static boolean isOver(int spawned, int alive, boolean anyPlayerInCave) {
        return !anyPlayerInCave || (spawned >= TOTAL && alive == 0);
    }
}
