package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.client.fx.marks.MarkRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * Lingering marks left on the world by magic and fire: soot-black scorches with dying embers, and Glacius frost,
 * laid on the block faces round a spot and fading over about a minute. Client-side only, drawn not placed (no block
 * changes), and they respect {@code fx.FxConfig#LINGERING_MARKS}. The server can ask for them with the
 * {@code fx.FxKinds} mark kinds; the beam strikes of Incendio and Glacius mark the room on their own
 * ({@code marks.MarkSpells}).
 *
 * <p>A scorch is deep soot, darkest at its core, with a ragged edge; for the first few seconds ember cells glow
 * orange on it and flicker, then die, and the soot slowly fades. Frost is pale blue-white rime in crystalline
 * ridges with a few glinting cells, thinning out from its edge until it is gone. The drawing, its caps (marks alive,
 * cells a frame, distance) and its clean-up on logout and level change live in {@link MarkRenderer}.
 */
public final class Marks {

    private Marks() {
    }

    /** A scorch of {@code radius} blocks centred on {@code at}, lasting about {@code seconds}. */
    public static void scorch(Vec3 at, float radius, float seconds) {
        MarkRenderer.add(false, at, radius, seconds, 0);
    }

    /** As {@link #scorch(Vec3, float, float)}, appearing {@code delayTicks} later (a blast still breaking blocks). */
    public static void scorch(Vec3 at, float radius, float seconds, int delayTicks) {
        MarkRenderer.add(false, at, radius, seconds, delayTicks);
    }

    /** Glacius frost of {@code radius} blocks centred on {@code at}, lasting about {@code seconds}. */
    public static void frost(Vec3 at, float radius, float seconds) {
        MarkRenderer.add(true, at, radius, seconds, 0);
    }
}
