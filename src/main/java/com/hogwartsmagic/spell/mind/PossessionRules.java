package com.hogwartsmagic.spell.mind;

/**
 * Imperio Master: possession. Every number of the session lives here so it is testable without
 * Minecraft. Times are ticks (20 per second).
 */
public final class PossessionRules {
    public static final byte ROLE_NONE = 0;
    public static final byte ROLE_CASTER = 1;
    public static final byte ROLE_VICTIM = 2;

    public static final int MAX_TICKS = 400;
    public static final int BOSS_MAX_TICKS = 100;
    public static final int RESIST_DELAY_TICKS = 160;
    public static final int RESIST_WINDOW_TICKS = 10;
    public static final int RESIST_MIN_PRESSES = 6;
    public static final int RESIST_MAX_PRESSES = 12;
    public static final int RESIST_REPORT_GAP_TICKS = 8;
    public static final double RESIST_CHANCE = 0.2;
    public static final float BODY_HIT_EJECT = 4.0f;
    public static final int IMMUNITY_TICKS = 60;
    public static final int NAUSEA_TICKS = 60;
    public static final double MAX_DISTANCE = 48.0;
    public static final int HEARTBEAT_TIMEOUT = 10;
    public static final int ATTACK_COOLDOWN = 10;
    public static final int SPECIAL_ATTACK_COOLDOWN = 20;
    public static final int INPUTS_PER_WINDOW = 30;
    public static final int HOTBAR_PER_WINDOW = 10;
    public static final int CASTS_PER_WINDOW = 4;
    public static final int RATE_WINDOW_TICKS = 20;
    public static final int SNAPSHOT_INTERVAL_TICKS = 20;
    public static final int STATE_KEEPALIVE_TICKS = 20;
    public static final int CLIENT_STATE_TIMEOUT = 40;
    public static final int MARKER_GRACE_TICKS = 40;
    public static final int UPKEEP_INTERVAL = 10;

    private PossessionRules() {
    }

    /** Players and mobs share the full duration; only a boss gets the short leash. */
    public static int maxTicks(boolean boss) {
        return boss ? BOSS_MAX_TICKS : MAX_TICKS;
    }

    public static boolean expired(long elapsed, int maxTicks) {
        return elapsed >= maxTicks;
    }

    /** The caster streams input every tick while holding; ten ticks of silence means they let go. */
    public static boolean heartbeatLapsed(long now, long lastSeen) {
        return now - lastSeen > HEARTBEAT_TIMEOUT;
    }

    public static boolean resistOpen(long elapsed) {
        return elapsed >= RESIST_DELAY_TICKS;
    }

    /** Index of the half-second resist window {@code elapsed} falls in, or -1 before resisting opens. */
    public static int resistWindow(long elapsed) {
        return resistOpen(elapsed) ? (int) ((elapsed - RESIST_DELAY_TICKS) / RESIST_WINDOW_TICKS) : -1;
    }

    public static int clampPresses(int presses) {
        return Math.max(0, Math.min(RESIST_MAX_PRESSES, presses));
    }

    /** Callers must pass presses already clamped with {@link #clampPresses(int)}. */
    public static boolean breaksFree(int presses, double roll) {
        return presses >= RESIST_MIN_PRESSES && roll < RESIST_CHANCE;
    }

    public static boolean reportAllowed(long now, long lastReport) {
        return now - lastReport >= RESIST_REPORT_GAP_TICKS;
    }

    public static boolean ejects(float damage) {
        return damage >= BODY_HIT_EJECT;
    }

    public static boolean tooFar(double distanceSqr) {
        return distanceSqr > MAX_DISTANCE * MAX_DISTANCE;
    }

    public static float axis(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    public static float pitch(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(-90.0f, Math.min(90.0f, value));
    }

    public static float yaw(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    public static boolean validHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    /** No puppet chains, no teleporting the victim away, and no held/channel spells. */
    public static boolean isPuppetBlocked(String spellId) {
        return "imperio".equals(spellId) || "apparition".equals(spellId) || "crucio".equals(spellId)
                || "protego_maxima".equals(spellId) || "fiendfyre".equals(spellId)
                || "wingardium_leviosa".equals(spellId) || "expecto_patronum".equals(spellId);
    }
}
