package com.hogwartsmagic.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * Lingering marks left on the world by magic and fire: soot-black scorches with dying embers, and Glacius frost,
 * laid on the block faces round a spot and fading over about a minute. Client-side only, drawn not placed (no block
 * changes), and they respect {@code fx.FxConfig#LINGERING_MARKS}. The server can ask for them with the
 * {@code fx.FxKinds} mark kinds.
 *
 * <p>STUB: the signatures are the contract other features build against; the "lingering marks" work fills them in.
 * Until then every call is a no-op.
 */
public final class Marks {

    private Marks() {
    }

    /** A scorch of {@code radius} blocks centred on {@code at}, lasting about {@code seconds}. */
    public static void scorch(Vec3 at, float radius, float seconds) {
    }

    /** Glacius frost of {@code radius} blocks centred on {@code at}, lasting about {@code seconds}. */
    public static void frost(Vec3 at, float radius, float seconds) {
    }
}
