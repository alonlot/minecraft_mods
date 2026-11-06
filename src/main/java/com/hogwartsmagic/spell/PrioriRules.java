package com.hogwartsmagic.spell;

/**
 * The numbers of a Priori Incantatem lock, kept free of Minecraft classes so a simulation can check that a duel
 * actually ends. The bead runs from -1 (at A's wand) to +1 (at B's wand).
 *
 * <p>Tuned per second at 20 ticks: a player holding the cast key (a press every 4 ticks) pushes 0.35/s, clicking
 * at 8 a second 0.56/s. A student leans 0.18/s, a Death Eater 0.24/s, the Dark Lord 0.38/s. So holding beats a
 * Death Eater in about six seconds, standing idle loses to one in about four, and the Dark Lord needs real
 * clicking. Past the middle the bead also runs away from whoever is losing ground, so a lead turns into a win
 * instead of a stand-off.
 */
public final class PrioriRules {
    public static final int MAX_TICKS = 300;
    public static final float PLAYER_PUSH = 0.07f;
    public static final float STUDENT_PRESSURE = 0.009f;
    public static final float WIZARD_PRESSURE = 0.012f;
    public static final float VOLDEMORT_PRESSURE = 0.019f;
    /** Each tick the bead drifts this fraction of its offset further the way it already leans. */
    public static final float RUNAWAY = 0.006f;
    /** At the time limit, a lead this big still decides it; anything closer blows apart. */
    public static final float TIMEOUT_LEAD = 0.1f;

    public static final int ONGOING = 0;
    public static final int A_WINS = 1;
    public static final int B_WINS = 2;
    public static final int BLOW_APART = 3;

    private PrioriRules() {
    }

    /** An NPC's push this tick: its pressure, surging between half and one and a half times it. */
    public static float npcPush(float pressure, float roll01) {
        return pressure * (0.5f + roll01);
    }

    /** The bead after one tick of pushes (A drives it toward +1, B toward -1) and a little jitter. */
    public static float step(float bead, float pushA, float pushB, float jitter) {
        float next = bead + pushA - pushB + jitter;
        next += next * RUNAWAY;
        return Math.max(-1.0f, Math.min(1.0f, next));
    }

    /** How the lock stands at {@code age} ticks with the bead at {@code bead}. */
    public static int outcome(float bead, int age) {
        if (bead >= 1.0f) {
            return A_WINS;
        }
        if (bead <= -1.0f) {
            return B_WINS;
        }
        if (age >= MAX_TICKS) {
            if (bead >= TIMEOUT_LEAD) {
                return A_WINS;
            }
            if (bead <= -TIMEOUT_LEAD) {
                return B_WINS;
            }
            return BLOW_APART;
        }
        return ONGOING;
    }
}
