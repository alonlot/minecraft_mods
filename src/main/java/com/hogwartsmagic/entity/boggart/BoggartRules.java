package com.hogwartsmagic.entity.boggart;

import java.util.Map;

/**
 * The numbers behind the Boggart, with no Minecraft imports so they can be unit tested.
 * See docs/superpowers/plans/2026-10-08-boggart.md.
 */
public final class BoggartRules {
    /** How close a player must come, with line of sight, before a hidden Boggart takes a shape. */
    public static final double REVEAL_RADIUS = 8.0;
    /** Radius of the fear pulse. Slowness reaches only half of it. */
    public static final double FEAR_RADIUS = 6.0;
    public static final int FEAR_DARKNESS_TICKS = 80;
    public static final int FEAR_SLOWDOWN_TICKS = 60;
    public static final int FEAR_PULSE_INTERVAL = 40;
    public static final int REVEAL_CHECK_INTERVAL = 10;
    /** Players this far away no longer keep it revealed. */
    public static final double LOSE_INTEREST_RADIUS = 16.0;
    public static final int HIDE_AFTER_TICKS = 200;
    public static final int LAUGH_TICKS = 60;
    /** The wobble runs only for this many laugh ticks right after each hit (laughTicks counts down from LAUGH_TICKS). */
    public static final int LAUGH_WOBBLE_TICKS = 10;
    public static final float MIN_SCALE = 0.35f;
    /** No other Boggart within this many blocks, or the spawn rule refuses. */
    public static final double SPAWN_SPACING = 48.0;

    public static final String PLAYER_KEY = "player";
    public static final String PLAYER_FORM = "hogwartsmagic:dementor";
    public static final String DEFAULT_FORM = "hogwartsmagic:acromantula";

    private BoggartRules() {
    }

    /** Riddikulus hits before it bursts: 1 to 3, 2 to 2, 3 to 2, 4 to 1. Ranks clamp to 1..4. */
    public static int hitsToBanish(int rank) {
        int clamped = Math.max(1, Math.min(4, rank));
        return switch (clamped) {
            case 1 -> 3;
            case 2, 3 -> 2;
            default -> 1;
        };
    }

    /**
     * The shape a Boggart takes for a player: the killer with the most kills, ties broken by natural string order.
     * A player killer becomes a Dementor (it never wears a player). Nothing recorded: an Acromantula.
     */
    public static String pickForm(Map<String, Integer> fears) {
        if (fears == null || fears.isEmpty()) {
            return DEFAULT_FORM;
        }
        String best = null;
        int bestCount = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : fears.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (key == null) {
                continue;
            }
            if (best == null || count > bestCount || (count == bestCount && key.compareTo(best) < 0)) {
                best = key;
                bestCount = count;
            }
        }
        if (best == null) {
            return DEFAULT_FORM;
        }
        return PLAYER_KEY.equals(best) ? PLAYER_FORM : best;
    }

    /**
     * Render scale: 1.0 until the first hit, then it shrinks by half over the hits it takes, never below MIN_SCALE.
     * In the first LAUGH_WOBBLE_TICKS laugh ticks it also wobbles.
     */
    public static float laughScale(int laughTicks, int hits, int needed) {
        if (hits <= 0) {
            return 1.0f;
        }
        double scale = needed <= 0 ? MIN_SCALE : Math.max(MIN_SCALE, 1.0 - 0.5 * hits / needed);
        // laughTicks counts down from LAUGH_TICKS, so the first ten ticks after a hit are the high values.
        if (laughTicks > LAUGH_TICKS - LAUGH_WOBBLE_TICKS) {
            scale *= 1.0 + 0.15 * Math.sin(laughTicks * 1.2);
        }
        return (float) scale;
    }

    public static boolean shouldHide(int ticksWithoutPlayers) {
        return ticksWithoutPlayers >= HIDE_AFTER_TICKS;
    }
}
