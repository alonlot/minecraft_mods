package com.hogwartsmagic.fx;

/**
 * The cinematic effect kinds {@link Fx} can send. Each feature adds its own kinds here with a one-line note on the
 * numbers that ride with it (params[0], params[1], ...). Strings, not an enum: a client that doesn't know a kind
 * ignores it, and switch-on-enum has failed to load in this mod's render code before.
 */
public final class FxKinds {

    private FxKinds() {
    }

    /** The view shakes. params: strength, radius (blocks), duration (ticks), frequency (1 = normal). */
    public static final String SHAKE = "shake";
}
