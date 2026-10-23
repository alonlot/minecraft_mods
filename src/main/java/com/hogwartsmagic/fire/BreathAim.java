package com.hogwartsmagic.fire;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The wild Horntail fire aim as it travels from server to client: the server smoothed {@code breathAim} (the very
 * direction its damage cone is cast along) packed into a synched {@link Vector3f}, and the client reading of it.
 * Pure maths, no client classes, so it is safe on a dedicated server.
 */
public final class BreathAim {
    private BreathAim() {
    }

    /** What the synched value holds while no breath has set it: straight ahead in +z. */
    public static final Vector3f DEFAULT = new Vector3f(0f, 0f, 1f);

    /** The synched form of {@code aim}: its unit vector, or {@link #DEFAULT} for a zero or non-finite aim. */
    public static Vector3f encode(Vec3 aim) {
        double l = Math.sqrt(aim.x * aim.x + aim.y * aim.y + aim.z * aim.z);
        if (!(l > 1e-9) || Double.isInfinite(l)) return new Vector3f(DEFAULT);
        return new Vector3f((float) (aim.x / l), (float) (aim.y / l), (float) (aim.z / l));
    }

    /** The unit aim {x, y, z} the client draws along, from the synched value (a bad one reads as {@link #DEFAULT}). */
    public static double[] decode(Vector3f v) {
        if (v == null) return new double[]{0, 0, 1};
        double l = Math.sqrt((double) v.x * v.x + (double) v.y * v.y + (double) v.z * v.z);
        if (!(l > 1e-6) || Double.isInfinite(l)) return new double[]{0, 0, 1};
        return new double[]{v.x / l, v.y / l, v.z / l};
    }

    /** The aim {@code f} of the way (0..1, a tick partial) from {@code a} to {@code b}, renormalised; both unit. */
    public static double[] blend(double[] a, double[] b, double f) {
        double x = a[0] + (b[0] - a[0]) * f, y = a[1] + (b[1] - a[1]) * f, z = a[2] + (b[2] - a[2]) * f;
        double l = Math.sqrt(x * x + y * y + z * z);
        // Opposite aims cancel: take the newer one rather than a zero vector.
        if (l < 1e-6) return b.clone();
        return new double[]{x / l, y / l, z / l};
    }
}
