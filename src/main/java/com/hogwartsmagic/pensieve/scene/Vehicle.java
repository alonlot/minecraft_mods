package com.hogwartsmagic.pensieve.scene;

/**
 * Riding, for every vehicle type ({@link SceneNames#VEHICLES}: the rowboat, {@link Rowboat}, and the two memory
 * brooms, {@link BroomFlight}): the seam MemoryActors asks where a rider is. Pure.
 *
 * <p>An actor's {@code ride} names its vehicle, optionally with a window {@code from <= t < until}. Inside the window
 * the rider's {@code path} is an offset in the vehicle frame and its {@code look} yaw is relative to the vehicle's;
 * outside it both are world space. Each part of a track is sampled on its own ({@link Track#sampleWithin}): the keys
 * before the window, the keys in it and the keys after it never blend, so a mount is continuous when the path is
 * bracketed, a world key at {@code T - 0.001} where the vehicle-frame key at {@code T} puts the rider.
 */
public final class Vehicle {
    private Vehicle() {
    }

    public static boolean isVehicle(String type) {
        return type != null && SceneNames.VEHICLES.contains(type);
    }

    /** The riders a vehicle of this type carries at once: 2 on a broom, the rowboat's seats. */
    public static int capacity(String type) {
        return BroomFlight.isBroom(type) ? BroomFlight.MAX_RIDERS : Rowboat.SEATS.size();
    }

    /** Whether {@code a} rides its vehicle at t (in its window, or always when it has none). */
    public static boolean ridesAt(SceneDef.ActorDef a, float t) {
        return a.ride() != null && t >= a.rideFrom() && t < a.rideUntil();
    }

    /**
     * The part [lo, hi) of the actor's ride window that time t falls in: before, inside or after it (all time when it
     * rides nothing).
     */
    public static float[] window(SceneDef.ActorDef a, float t) {
        return part(a, t);
    }

    private static float[] part(SceneDef.ActorDef a, float t) {
        if (a.ride() == null) return new float[]{Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY};
        if (t < a.rideFrom()) return new float[]{Float.NEGATIVE_INFINITY, a.rideFrom()};
        if (t >= a.rideUntil()) return new float[]{a.rideUntil(), Float.POSITIVE_INFINITY};
        return new float[]{a.rideFrom(), a.rideUntil()};
    }

    /** The actor's own path at t from the keys of the part of its ride window t is in; null when that part has none. */
    public static float[] path(SceneDef.ActorDef a, float t) {
        return within(a.path(), a, t);
    }

    /** The actor's own look at t, likewise. */
    public static float[] look(SceneDef.ActorDef a, float t) {
        return within(a.look(), a, t);
    }

    private static float[] within(Track track, SceneDef.ActorDef a, float t) {
        if (track.isEmpty()) return null;
        if (a.ride() == null || (Float.isInfinite(a.rideFrom()) && Float.isInfinite(a.rideUntil()))) return track.sample(t);
        float[] w = part(a, t);
        return track.sampleWithin(t, w[0], w[1]);
    }

    /** The vehicle actor {@code rider} rides, or null when it rides nothing the scene has. */
    public static SceneDef.ActorDef vehicleOf(SceneDef scene, SceneDef.ActorDef rider) {
        if (rider.ride() == null) return null;
        SceneDef.ActorDef found = null;
        for (SceneDef.ActorDef a : scene.actors()) {
            if (a.id().equals(rider.ride())) found = a;
        }
        return found != null && isVehicle(found.type()) ? found : null;
    }

    /**
     * A seated rider: its root (scene-relative), world yaw, the vehicle's pitch and roll in the rowboat's convention
     * (bow up positive, left side up positive; what the renderer tilts the rider by), the vehicle's yaw and M, and for
     * a broom its path speed, its own pitch (nose down positive) and the rider layer's weight.
     */
    public record Seat(double x, double y, double z, float yaw, float pitch, float roll, float vehicleYaw, double[] m,
                       boolean broom, double speed, float broomPitch, float weight) {
    }

    /** Where {@code rider} sits at t, or null when it does not ride then (or its vehicle is missing). */
    public static Seat seat(SceneDef scene, SceneDef.ActorDef rider, float t) {
        if (!ridesAt(rider, t)) return null;
        SceneDef.ActorDef v = vehicleOf(scene, rider);
        if (v == null) return null;
        float[] look = look(rider, t);
        float lookYaw = look != null && look.length > 0 ? look[0] : 0f;
        double s = v.scale();
        if (BroomFlight.isBroom(v.type())) {
            BroomFlight.Pose b = BroomFlight.place(scene, v, t);
            float[] p = path(rider, t);
            double[] off = p != null ? new double[]{p[0], p[1], p[2]} : BroomFlight.seat(rider.scale());
            double[] w = BroomFlight.toWorld(b.x(), b.y(), b.z(), b.m(), new double[]{off[0] * s, off[1] * s, off[2] * s});
            return new Seat(w[0], w[1], w[2], b.yaw() + lookYaw, -b.pitch(), b.roll(), b.yaw(), b.m(), true, b.speed(),
                    b.pitch(), BroomFlight.riderWeight(rider.rideFrom(), rider.rideUntil(), t));
        }
        Rowboat.Pose b = Rowboat.place(scene, v, t);
        float[] off = path(rider, t);
        if (off == null) off = new float[]{0, 0, 0};
        double[] w = Rowboat.toWorld(b, new double[]{off[0] * s, off[1] * s, off[2] * s});
        return new Seat(w[0], w[1], w[2], b.yaw() + lookYaw, b.pitch(), b.roll(), b.yaw(), b.m(), false, 0, 0, 1f);
    }

    /**
     * Any non-vehicle actor's root at t, scene-relative: its seat while it rides, else its own path in world space
     * ({0, 0, 0} with none).
     */
    public static double[] root(SceneDef scene, SceneDef.ActorDef a, float t) {
        Seat seat = scene == null ? null : seat(scene, a, t);
        if (seat != null) return new double[]{seat.x(), seat.y(), seat.z()};
        float[] p = path(a, t);
        if (p == null) return new double[]{0, 0, 0};
        return new double[]{p.length > 0 ? p[0] : 0, p.length > 1 ? p[1] : 0, p.length > 2 ? p[2] : 0};
    }
}
