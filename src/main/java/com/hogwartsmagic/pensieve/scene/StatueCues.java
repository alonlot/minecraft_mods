package com.hogwartsmagic.pensieve.scene;

import com.hogwartsmagic.entity.LivingStatueEntity;

import java.util.List;

/**
 * The memory Living Statue's state from its {@code anims} cues and {@code swing} actions, a pure function of them and
 * the scene clock. The real statue runs on synced counters its own tick advances; a memory puppet is never ticked, so
 * the client sets those counters from here each tick ({@link LivingStatueEntity#setMemoryState}) and the model reads
 * them exactly as it does for the real thing. Cue names are {@link SceneNames#STATUE_ANIMS} (case-insensitive):
 * <ul>
 *   <li>{@code WAKE}: from the cue it rises out of the kneel over {@link LivingStatueEntity#WAKE_TICKS}, the real
 *       counter (1 at the cue tick, then one more a tick), and stands once it has;</li>
 *   <li>{@code CRUMBLE}: awake, then folds back to the kneel over {@link LivingStatueEntity#CRUMBLE_TICKS} and holds it;</li>
 *   <li>{@code IDLE}: awake and standing at once (what {@code WAKE} turns into when it has finished).</li>
 * </ul>
 * Before its first cue the statue is dormant (kneeling, halberd planted). Each {@code swing} action is one blow of the
 * real combo, the blows cycling 0, 1, 2 (the two diagonals, then the overhead split) with their own lengths
 * ({@link LivingStatueEntity#SWING_TICKS}); a blow only shows on an awake, standing statue.
 */
public final class StatueCues {
    private StatueCues() {
    }

    /**
     * The three blows' lengths in ticks: {@link LivingStatueEntity#SWING_TICKS}' values, copied because that array is
     * not a compile-time constant and reading it would load the entity class (these maths must stay pure).
     */
    public static final int[] SWING_TICKS = {34, 34, 46};
    /** The action that is one blow of the combo. */
    public static final String SWING = "swing";

    /** The two counters the model reads: wake ticks (0..WAKE_TICKS) and crumble ticks (0..CRUMBLE_TICKS). */
    public record State(int wakeTicks, int crumbleTicks) {
    }

    /** A blow in flight: which blow of the combo and the whole ticks into it. */
    public record Swing(int combo, int ticks) {
    }

    /** The counters at scene time {@code t}; {@code cues} in any order. */
    public static State at(List<SceneDef.Cue> cues, float t) {
        SceneDef.Cue last = null;
        for (SceneDef.Cue c : cues) {
            if (c.t() <= t && (last == null || c.t() >= last.t())) last = c;
        }
        if (last == null) return new State(0, 0);
        int ticks = (int) Math.floor(t - last.t()) + 1;
        return switch (last.name().toUpperCase(java.util.Locale.ROOT)) {
            case "WAKE" -> new State(Math.min(LivingStatueEntity.WAKE_TICKS, ticks), 0);
            case "CRUMBLE" -> new State(LivingStatueEntity.WAKE_TICKS, Math.min(LivingStatueEntity.CRUMBLE_TICKS, ticks));
            default -> new State(LivingStatueEntity.WAKE_TICKS, 0);
        };
    }

    /** The blow in flight at {@code t} (from the {@code swing} actions, in any order), or null when none is. */
    public static Swing swing(List<SceneDef.ActionCue> actions, float t) {
        int n = 0;
        float last = Float.NEGATIVE_INFINITY;
        for (SceneDef.ActionCue a : actions) {
            if (!SWING.equals(a.action()) || a.t() > t) continue;
            n++;
            last = Math.max(last, a.t());
        }
        if (n == 0) return null;
        // The nth blow of the scene is blow (n - 1) mod 3: ties at one instant count as separate blows, which is
        // harmless (the same instant, the same time-ordered answer).
        int combo = (n - 1) % SWING_TICKS.length;
        float elapsed = t - last;
        if (elapsed >= SWING_TICKS[combo]) return null;
        return new Swing(combo, (int) Math.floor(elapsed));
    }

    /** True when the cue name is one of the statue's ({@link SceneNames#STATUE_ANIMS}). */
    public static boolean known(String name) {
        return SceneNames.STATUE_ANIMS.contains(name.toUpperCase(java.util.Locale.ROOT));
    }
}
