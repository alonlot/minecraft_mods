package com.hogwartsmagic.assignment;

import java.util.List;

/** The numbers and checks around assignments. Pure Java: safe on a dedicated server. */
public final class AssignmentRules {
    /** At most this many assignments can be in progress at once. */
    public static final int MAX_ACTIVE = 3;
    /** A sneak-click must follow the first one within this many ticks to abandon an assignment. */
    public static final long ABANDON_CONFIRM_TICKS = 60L;
    /** A survived fall counts only if the player is still alive this many ticks after landing. */
    public static final long FALL_SURVIVE_TICKS = 20L;
    /** A mob killing a mob counts for players within this many blocks. */
    public static final double WITNESS_RADIUS = 16.0;

    private AssignmentRules() {
    }

    /** Adds {@code amount} to a counter, never past {@code count} and never below 0. */
    public static int add(int current, int amount, int count) {
        long next = (long) Math.max(0, current) + Math.max(0, amount);
        return (int) Math.min(count, next);
    }

    /**
     * True when every objective is done: stored ones by {@code progress}, live ones by {@code liveDone}.
     * Missing entries count as 0 / not done.
     */
    public static boolean isComplete(List<ObjectiveDef> objectives, int[] progress, boolean[] liveDone) {
        for (int i = 0; i < objectives.size(); i++) {
            ObjectiveDef objective = objectives.get(i);
            if (objective.type().isLive()) {
                if (liveDone == null || i >= liveDone.length || !liveDone[i]) {
                    return false;
                }
            } else if (progress == null || i >= progress.length || progress[i] < objective.count()) {
                return false;
            }
        }
        return true;
    }

    /** The progress array resized to the objective count (a datapack edit can change it). */
    public static int[] fit(int[] stored, List<ObjectiveDef> objectives) {
        int[] out = new int[objectives.size()];
        for (int i = 0; i < out.length; i++) {
            int value = stored != null && i < stored.length ? stored[i] : 0;
            out[i] = Math.max(0, Math.min(objectives.get(i).count(), value));
        }
        return out;
    }

    /** Adds {@code id} to a comma-joined set; returns the same string when it is already there. */
    public static String addDistinct(String joined, String id) {
        if (joined == null || joined.isEmpty()) {
            return id;
        }
        for (String token : joined.split(",")) {
            if (token.equals(id)) {
                return joined;
            }
        }
        return joined + "," + id;
    }

    public static int distinctCount(String joined) {
        return joined == null || joined.isEmpty() ? 0 : joined.split(",").length;
    }

    /** True when an item is a melee or ranged weapon by its registry path (cheap and mod-friendly). */
    public static boolean isWeaponPath(String path) {
        return path.endsWith("_sword") || path.endsWith("_axe") || path.equals("bow")
                || path.equals("crossbow") || path.equals("trident") || path.equals("mace");
    }
}
