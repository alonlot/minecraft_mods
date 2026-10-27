package com.hogwartsmagic.spell.summon;

/**
 * Expecto Patronum by rank. Every cast rolls once: a hit summons a corporeal Patronus, a miss holds up
 * the cone of silver light. The chance of a corporeal answer grows with rank, and from Expert the
 * corporeal Patronus takes the caster's own animal form. Pure Java: safe on a dedicated server.
 */
public final class PatronusRules {
    /** Indexed by rank - 1: Novice, Adept, Expert, Master. */
    private static final float[] CORPOREAL_CHANCE = {0.15f, 0.30f, 0.50f, 0.75f};
    private static final int FORM_RANK = 3;

    /** Master: the Patronus follows its caster, lights the dark around them and drives back the undead. */
    public static final int MASTER_RANK = 4;
    public static final double UNDEAD_RADIUS = 6.0;
    public static final int UNDEAD_INTERVAL = 5;
    public static final double UNDEAD_PUSH = 0.6;
    public static final int NIGHT_VISION_TICKS = 260;
    public static final int NIGHT_VISION_INTERVAL = 40;
    public static final double LIGHT_RADIUS = 8.0;
    public static final double FOLLOW_START = 6.0;
    public static final double FOLLOW_STOP = 3.0;
    public static final double TELEPORT_DISTANCE = 24.0;

    /** Master: a message by Patronus reaches a player this many blocks away, in the same dimension. */
    public static final int MESSAGE_RANGE = 500;
    public static final int MESSAGE_MAX_LENGTH = 200;
    public static final int MESSAGE_COOLDOWN_TICKS = 600;
    public static final int MESSENGER_TICKS = 100;

    private PatronusRules() {
    }

    public static float corporealChance(int rank) {
        return CORPOREAL_CHANCE[index(rank)];
    }

    /** True when a roll in [0, 1) answers with a corporeal Patronus. */
    public static boolean isCorporeal(int rank, float roll) {
        return roll < corporealChance(rank);
    }

    /** Expert and Master: the corporeal Patronus takes the caster's own form. */
    public static boolean personalForm(int rank) {
        return index(rank) + 1 >= FORM_RANK;
    }

    public static boolean masterAura(int rank) {
        return rank >= MASTER_RANK;
    }

    public static boolean sendsMessages(int rank) {
        return rank >= MASTER_RANK;
    }

    public static boolean inMessageRange(double distanceSqr) {
        return distanceSqr <= (double) MESSAGE_RANGE * MESSAGE_RANGE;
    }

    /** Ticks until the next message may be sent; 0 when ready or when none was ever sent. */
    public static long messageCooldownLeft(boolean sentBefore, long lastSentAt, long now) {
        if (!sentBefore) {
            return 0L;
        }
        // Capped so a stored stamp from a clock that later ran backwards cannot lock the caster out.
        return Math.min(MESSAGE_COOLDOWN_TICKS, Math.max(0L, lastSentAt + MESSAGE_COOLDOWN_TICKS - now));
    }

    /** Drops the section sign (colour codes) and control characters, caps the length and trims. */
    public static String sanitizeMessage(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < MESSAGE_MAX_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c == '\u00a7' || Character.isISOControl(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString().strip();
    }

    /** Undead and Inferi inside the radius are driven back; bosses are never moved. */
    public static boolean repels(boolean undeadOrInferius, boolean boss, double distanceSqr) {
        return undeadOrInferius && !boss && distanceSqr <= UNDEAD_RADIUS * UNDEAD_RADIUS;
    }

    private static int index(int rank) {
        return Math.max(0, Math.min(CORPOREAL_CHANCE.length - 1, rank - 1));
    }
}
