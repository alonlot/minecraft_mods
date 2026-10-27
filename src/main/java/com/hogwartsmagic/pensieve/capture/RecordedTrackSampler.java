package com.hogwartsmagic.pensieve.capture;

import java.util.List;

/** Reads a recorded entity's per-tick arrays at a fractional tick. Pure. */
public final class RecordedTrackSampler {
    /** How long a killed entity stays after its last sample, for its death animation. */
    public static final int DEATH_LINGER = 20;

    private RecordedTrackSampler() {
    }

    /** True while the entity exists: {@code first <= tick <= last}, plus the death linger when it was killed. */
    public static boolean present(Recording.Track t, float tick) {
        int end = t.last() + (t.removal() == Recording.REMOVAL_KILLED ? DEATH_LINGER : 0);
        return tick >= t.first() && tick <= end;
    }

    /** {@code {x, y, z, yaw, headYaw, bodyYaw, pitch}} at {@code tick}, clamped to the array ends. */
    public static float[] pose(Recording.Track t, float tick) {
        float rel = tick - t.first();
        int n = t.x().length;
        int i0 = (int) Math.floor(rel);
        float f = rel - i0;
        int a = Math.max(0, Math.min(n - 1, i0));
        int b = Math.max(0, Math.min(n - 1, i0 + 1));
        if (i0 < 0 || i0 >= n - 1) {
            f = 0;
        }
        return new float[] {
                lerp(t.x()[a], t.x()[b], f), lerp(t.y()[a], t.y()[b], f), lerp(t.z()[a], t.z()[b], f),
                lerpAngle(t.yaw()[a], t.yaw()[b], f), lerpAngle(t.headYaw()[a], t.headYaw()[b], f),
                lerpAngle(t.bodyYaw()[a], t.bodyYaw()[b], f), lerp(t.pitch()[a], t.pitch()[b], f)};
    }

    /** The index after the last patch whose tick is {@code <= tick}, scanning on from {@code fromIndex}. */
    public static int patchesUpTo(List<?> sortedByTick, int fromIndex, int tick) {
        int i = fromIndex;
        while (i < sortedByTick.size() && tickOf(sortedByTick.get(i)) <= tick) {
            i++;
        }
        return i;
    }

    private static int tickOf(Object patch) {
        if (patch instanceof Recording.DataPatch d) return d.tick();
        if (patch instanceof Recording.EquipPatch e) return e.tick();
        if (patch instanceof Recording.Act a) return a.tick();
        throw new IllegalArgumentException("not a patch: " + patch);
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    /** Lerps the short way round, the {@code Mth.wrapDegrees} logic written out. */
    private static float lerpAngle(float a, float b, float f) {
        float d = (b - a) % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return a + d * f;
    }
}
