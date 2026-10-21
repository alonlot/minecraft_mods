package com.hogwartsmagic.pensieve.scene;

/**
 * The seed for an effect's random look (the letter flood's swarm, the ceiling's stars, the light rays, the candles,
 * the wand-choice motes). It hashes the event position <em>relative to the scene origin</em> and the event time, never
 * the world position: a memory plays in a different session slot each time, and the slot origin must not change what
 * the viewer sees.
 */
public final class EffectSeed {
    private EffectSeed() {}

    /** The seed for an event at world position (wx, wy, wz) in a scene whose origin is (ox, oy, oz), at scene time {@code t}. */
    public static long of(double wx, double wy, double wz, double ox, double oy, double oz, float t) {
        long h = Float.floatToIntBits((float) (wx - ox));
        h = h * 31 + Float.floatToIntBits((float) (wy - oy));
        h = h * 31 + Float.floatToIntBits((float) (wz - oz));
        return h * 31 + Float.floatToIntBits(t);
    }
}
