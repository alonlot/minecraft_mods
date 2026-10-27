package com.hogwartsmagic.fire;

import net.minecraft.world.phys.Vec3;

/**
 * Something in the open world that breathes the dragon's fire (client/pensieve/DragonFireRenderer): the wild
 * Horntail and the Fiendfyre serpent. Everything here is read off synched state, so every client draws the same jet
 * along the line the server's damage is cast on. Common code: no client classes.
 */
public interface FireBreather {
    /** The unit direction the jet is cast along now, {x, y, z} (synched). */
    double[] breathAimSynced();

    /** Where the fire leaves the mouth, for a frame the mouth was not drawn. */
    Vec3 mouthEstimate();

    /** Whole ticks since the fire began leaving the mouth, or -1 while it does not. */
    int breathAge();

    /** How long the breath now running lasts, in ticks (read while {@link #breathAge} is 0 or more). */
    float breathDuration();

    /** The breath gathering in the mouth, 0..1, or -1 while it is not. */
    float breathCharge();

    /** The jet's size beside a wild Horntail's (1). */
    default double fireScale() {
        return 1;
    }
}
