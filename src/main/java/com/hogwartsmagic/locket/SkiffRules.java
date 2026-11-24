package com.hogwartsmagic.locket;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The skiff's numbers and pure steering: a Catmull-Rom curve through the layout's path, slowed by latched Inferi. */
public final class SkiffRules {
    public static final int RISE_TICKS = 60;
    public static final double SPEED = 0.09D;
    public static final float LATCH_SLOW = 0.3F;
    public static final int MAX_LATCHED = 2;
    public static final int EMPTY_LIFETIME = 2400;
    public static final double BOB_AMPLITUDE = 0.06D;
    public static final double BOB_HZ = 0.15D;
    private static final double MIN_SPEED_FRACTION = 0.4D;
    private static final double LOOKAHEAD = 0.9D;

    private SkiffRules() {
    }

    /** Water cells of the crossing in travel order: shore dock, every path marker, island dock (markers sit one above the water). */
    public static List<BlockPos> route(BlockPos dockShore, List<BlockPos> path, BlockPos dockIsland) {
        List<BlockPos> r = new ArrayList<>(path.size() + 2);
        r.add(dockShore.below());
        for (BlockPos p : path) {
            r.add(p.below());
        }
        r.add(dockIsland.below());
        return r;
    }

    /**
     * Point on the Catmull-Rom segment that runs from {@code path[seg-1]} (t = 0) to {@code path[seg]} (t = 1); the
     * ends of the path repeat their own point so the curve starts and finishes on the waypoints.
     */
    public static Vec3 curvePoint(List<Vec3> path, int seg, double t) {
        int last = path.size() - 1;
        seg = Math.max(1, Math.min(seg, last));
        Vec3 p1 = path.get(seg - 1);
        Vec3 p2 = path.get(seg);
        Vec3 p0 = seg - 2 >= 0 ? path.get(seg - 2) : p1;
        Vec3 p3 = seg + 1 <= last ? path.get(seg + 1) : p2;
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(cr(p0.x, p1.x, p2.x, p3.x, t, t2, t3), cr(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                cr(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static double cr(double a, double b, double c, double d, double t, double t2, double t3) {
        return 0.5D * (2.0D * b + (c - a) * t + (2.0D * a - 5.0D * b + 4.0D * c - d) * t2 + (-a + 3.0D * b - 3.0D * c + d) * t3);
    }

    /**
     * One tick of horizontal velocity of length {@code speed}, from {@code pos} toward a point a little further along
     * the curve of the segment that ends at waypoint {@code nextIndex}. Never leaves the water plane (y is 0).
     */
    public static Vec3 steer(Vec3 pos, List<Vec3> path, int nextIndex, double speed) {
        int seg = Math.max(1, Math.min(nextIndex, path.size() - 1));
        Vec3 a = path.get(seg - 1);
        Vec3 b = path.get(seg);
        double cx = b.x - a.x;
        double cz = b.z - a.z;
        double len2 = cx * cx + cz * cz;
        double len = Math.sqrt(len2);
        double t0 = len2 < 1e-9 ? 1.0D : ((pos.x - a.x) * cx + (pos.z - a.z) * cz) / len2;
        t0 = Math.max(0.0D, Math.min(1.0D, t0));
        double t = len < 1e-6 ? 1.0D : Math.min(1.0D, t0 + Math.max(LOOKAHEAD, speed * 8.0D) / len);
        Vec3 target = curvePoint(path, seg, t);
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-6) {
            dx = b.x - pos.x;
            dz = b.z - pos.z;
            d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1e-6) {
                return Vec3.ZERO;
            }
        }
        return new Vec3(dx / d * speed, 0.0D, dz / d * speed);
    }

    public static double speedWith(int latched) {
        return SPEED * Math.max(MIN_SPEED_FRACTION, 1.0D - LATCH_SLOW * latched);
    }

    /** An empty skiff goes back to the shore unless someone is near the island dock, where it must stay for them. */
    public static boolean riderGoneReturnsToShore(boolean riderPresent, boolean playerNearIsland) {
        return !riderPresent && !playerNearIsland;
    }

    /** Vertical bob of the hull, in blocks, at {@code ageTicks} (fractional): 0.06 at 0.15 Hz. */
    public static double bob(double ageTicks) {
        return BOB_AMPLITUDE * Math.sin(2.0D * Math.PI * BOB_HZ * ageTicks / 20.0D);
    }
}
