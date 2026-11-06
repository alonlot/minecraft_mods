package com.hogwartsmagic.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * What the big creatures do to the world around them, in two calls: a ground slam (the earth jumps, dust rolls out
 * in a ring, chunks of the ground fly, the view shakes) and a rumble (a long low shudder under a roar). Common code
 * that only sends {@link Fx} messages, so a dedicated server can call it; how it looks is {@code client.fx.creature}.
 */
public final class CreatureFx {

    /** Players this far past the slam's own radius still get the message (the dust is seen from far off). */
    private static final double VISIBLE_EXTRA = 40.0;
    /** A roar rattles at this fraction of a normal shake's frequency: slow, heavy, rolling. */
    private static final float RUMBLE_FREQUENCY = 0.4f;

    private CreatureFx() {
    }

    /** Only the look of a slam (ring, dust, chunks) at {@code at}, on the ground there. */
    public static void slam(ServerLevel level, Vec3 at, float radius, float strength) {
        Fx.send(level, FxKinds.SLAM, at, VISIBLE_EXTRA + radius, -1, radius, strength);
    }

    /**
     * The look of a slam plus the view shaking under it: {@code shakeStrength} about 1.2 for a Troll's club, 2.5 for
     * a Giant's fist, felt out to {@code shakeRadius} blocks for {@code shakeTicks}.
     */
    public static void slam(ServerLevel level, Vec3 at, float radius, float strength, float shakeStrength,
                            float shakeRadius, int shakeTicks) {
        slam(level, at, radius, strength);
        Fx.shake(level, at, shakeStrength, shakeRadius, shakeTicks);
    }

    /** A long low rumble: a roar, a chained dragon pulling free. About 40 ticks, strength 1 to 1.6. */
    public static void rumble(ServerLevel level, Vec3 at, float strength, float radius, int ticks) {
        Fx.send(level, FxKinds.SHAKE, at, radius + 4.0, -1, strength, radius, ticks, RUMBLE_FREQUENCY);
    }
}
