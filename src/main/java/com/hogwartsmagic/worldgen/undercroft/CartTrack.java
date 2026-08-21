package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Gringotts cart line: the descending circuit the undercroft is carved along and the cart
 * later rides.
 *
 * <p>The layout is designed in {@code build_scripts/preview_cart_track.py}, which draws the plan,
 * the elevation and the gradient profile and audits all three. That script is the authority on the
 * shape; {@link #SEGMENTS} here mirrors its own segment list and the two must be changed together.
 * Run {@code python build_scripts/preview_cart_track.py --sweep} after any edit - it re-checks the
 * layout at every depth the undercroft can be built at.
 *
 * <h2>Three things about this curve that are not arbitrary</h2>
 *
 * <p><b>It is a centripetal Catmull-Rom</b> (alpha = 0.5), not a uniform one. Uniform Catmull-Rom
 * forms cusps and little self-intersecting loops wherever control points bunch up, and on this
 * curve a cusp is a corridor that pinches to zero width and a cart that reverses direction at full
 * speed. Centripetal provably cannot do either.
 *
 * <p><b>Everything is sampled by arc length</b>, never by the raw curve parameter. The parameter
 * runs at wildly different speeds through tight and loose segments, so a cart driven by it would
 * accelerate in the corners - exactly backwards.
 *
 * <p><b>Height is applied over arc length, not per segment.</b> Giving each segment its own share of
 * the drop steps dy/ds at every junction, and because the curve also kinks there the arc-length step
 * shrinks at the same moment, spiking the gradient to 1:1.1 at what the plan view shows as an
 * ordinary corner. Instead the segments lay out the plan, and a smoothed grade profile is integrated
 * along it - which is what a real railway does with a vertical curve.
 *
 * <h2>Why nothing here is serialised</h2>
 *
 * <p>Generation is deterministic from three numbers: the origin, the depth, and {@link #VERSION}. So
 * {@link #save} writes those three and {@link #load} rebuilds. That keeps the saved data tiny, makes
 * the client copy free to reconstruct from the same three numbers over the network, and removes any
 * possibility of a stored table disagreeing with the code that reads it.
 */
public final class CartTrack {

    /** Bump when SEGMENTS or the sampling changes, so old saves rebuild instead of misaligning. */
    public static final int VERSION = 1;

    /** Depth budget, station floor to chasm floor. Both ends measured; see the preview script. */
    public static final int DEPTH_MIN = 96;
    public static final int DEPTH_MAX = 180;

    /** Nominal speed the geometric bank angle is computed at, blocks per second. */
    public static final double DESIGN_SPEED = 9.0D;

    private static final double KNOT_SPACING = 9.0D;
    private static final double VERTICAL_CURVE = 16.0D;
    private static final int SAMPLES_PER_KNOT = 16;
    private static final float MAX_BANK_DEG = 35.0F;

    // --- the layout, mirroring preview_cart_track.py -------------------------------------

    private static final double[][] AXES = {
            {0.0D, 0.0D},      // A0: shaft head and station
            {8.0D, 5.0D},      // A1: the chasm and the dragon
            {4.0D, 2.0D},      // A2: the deep approach
    };

    private static final int LINE = 0;
    private static final int ARC = 1;

    /**
     * {kind, axis, sweepDegrees, radius, dropFraction, dx, dz}. A radius of 0 on an arc means
     * "hold whatever radius we arrive at". Every segment continues from the end of the previous
     * one: an arc takes its start angle and radius from the current position rather than being
     * given one, which is what makes the line continuous by construction.
     */
    private static final double[][] SEGMENTS = {
            {LINE, 0, 0, 0, 0.00, 18.0, 0.0},     // station apron
            {ARC, 0, -300, 0, 0.26, 0, 0},        // the plunge
            {LINE, 0, 0, 0, 0.04, -4.0, 24.0},    // loading gallery
            {ARC, 0, 105, 26, 0.05, 0, 0},        // stalactite bend
            {ARC, 0, -85, 15, 0.05, 0, 0},        // stalactite S
            {ARC, 1, 180, 29, 0.12, 0, 0},        // high chasm ledge
            {ARC, 1, -120, 14, 0.15, 0, 0},       // downfall hairpin
            {ARC, 1, 320, 24, 0.13, 0, 0},        // dragon lap
            {ARC, 1, 250, 27, 0.13, 0, 0},        // low chasm ledge
            {ARC, 2, 350, 12, 0.12, 0, 0},        // deep approach
    };

    public static final String[] SEGMENT_NAMES = {
            "station apron", "the plunge", "loading gallery", "stalactite bend", "stalactite S",
            "high chasm ledge", "downfall hairpin", "dragon lap", "low chasm ledge", "deep approach",
    };

    // --- the baked line ------------------------------------------------------------------

    private final BlockPos origin;
    private final int depth;

    /** Dense samples, origin-relative. Parallel arrays rather than objects: this is hot. */
    private final float[] px, py, pz;
    private final float[] tx, ty, tz;      // unit tangent
    private final float[] ux, uy, uz;      // rotation-minimising up, already banked
    private final float[] curvature;       // 1/r, unsigned
    private final float[] bank;            // degrees, signed, right side down positive
    private final float[] arc;             // cumulative arc length
    private final int[] segmentStart;      // first sample index of each segment

    private CartTrack(BlockPos origin, int depth, int count) {
        this.origin = origin;
        this.depth = depth;
        this.px = new float[count];
        this.py = new float[count];
        this.pz = new float[count];
        this.tx = new float[count];
        this.ty = new float[count];
        this.tz = new float[count];
        this.ux = new float[count];
        this.uy = new float[count];
        this.uz = new float[count];
        this.curvature = new float[count];
        this.bank = new float[count];
        this.arc = new float[count];
        this.segmentStart = new int[SEGMENTS.length + 1];
    }

    public BlockPos origin() {
        return this.origin;
    }

    public int depth() {
        return this.depth;
    }

    public double length() {
        return this.arc[this.arc.length - 1];
    }

    public int sampleCount() {
        return this.arc.length;
    }

    /** Arc length at which a segment begins. */
    public double segmentStartLength(int segment) {
        return this.arc[this.segmentStart[Mth.clamp(segment, 0, SEGMENTS.length)]];
    }

    public static int segmentCount() {
        return SEGMENTS.length;
    }

    // --- sampling ------------------------------------------------------------------------

    /**
     * Index of the sample at or before {@code s}, with the fraction to the next one packed into
     * the caller's out-parameter. Binary search: the samples are not evenly spaced in arc length.
     */
    private int locate(double s, float[] fractionOut) {
        double clamped = Mth.clamp(s, 0.0D, length());
        int lo = 0;
        int hi = this.arc.length - 1;
        while (lo < hi - 1) {
            int mid = (lo + hi) >>> 1;
            if (this.arc[mid] <= clamped) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = this.arc[hi] - this.arc[lo];
        fractionOut[0] = span > 1.0E-5F ? (float) ((clamped - this.arc[lo]) / span) : 0.0F;
        return lo;
    }

    public Vec3 positionAt(double s) {
        float[] f = new float[1];
        int i = locate(s, f);
        int j = Math.min(i + 1, this.arc.length - 1);
        return new Vec3(
                this.origin.getX() + 0.5D + Mth.lerp(f[0], this.px[i], this.px[j]),
                this.origin.getY() + Mth.lerp(f[0], this.py[i], this.py[j]),
                this.origin.getZ() + 0.5D + Mth.lerp(f[0], this.pz[i], this.pz[j]));
    }

    public Vec3 tangentAt(double s) {
        float[] f = new float[1];
        int i = locate(s, f);
        int j = Math.min(i + 1, this.arc.length - 1);
        return normalise(Mth.lerp(f[0], this.tx[i], this.tx[j]),
                Mth.lerp(f[0], this.ty[i], this.ty[j]),
                Mth.lerp(f[0], this.tz[i], this.tz[j]));
    }

    public Vec3 upAt(double s) {
        float[] f = new float[1];
        int i = locate(s, f);
        int j = Math.min(i + 1, this.arc.length - 1);
        return normalise(Mth.lerp(f[0], this.ux[i], this.ux[j]),
                Mth.lerp(f[0], this.uy[i], this.uy[j]),
                Mth.lerp(f[0], this.uz[i], this.uz[j]));
    }

    /** tangent x up: the track's own right-hand side, for laying the corridor out. */
    public Vec3 rightAt(double s) {
        return tangentAt(s).cross(upAt(s)).normalize();
    }

    public double curvatureAt(double s) {
        float[] f = new float[1];
        int i = locate(s, f);
        int j = Math.min(i + 1, this.arc.length - 1);
        return Mth.lerp(f[0], this.curvature[i], this.curvature[j]);
    }

    public float bankAt(double s) {
        float[] f = new float[1];
        int i = locate(s, f);
        int j = Math.min(i + 1, this.arc.length - 1);
        return Mth.lerp(f[0], this.bank[i], this.bank[j]);
    }

    /**
     * Nearest arc length to a world position, or -1 past {@code maxDistance}. Used to recover a
     * cart that was spawned off the line, or loaded against a track that has been regenerated.
     */
    public double project(Vec3 world, double maxDistance) {
        double ox = world.x - (this.origin.getX() + 0.5D);
        double oy = world.y - this.origin.getY();
        double oz = world.z - (this.origin.getZ() + 0.5D);
        double best = Double.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < this.arc.length; i++) {
            double dx = this.px[i] - ox;
            double dy = this.py[i] - oy;
            double dz = this.pz[i] - oz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 && best <= maxDistance * maxDistance ? this.arc[bestIndex] : -1.0D;
    }

    // --- construction --------------------------------------------------------------------

    /** Builds the line for a station at {@code origin} with {@code depth} blocks beneath it. */
    public static CartTrack build(BlockPos origin, int depth) {
        int clamped = Mth.clamp(depth, DEPTH_MIN, DEPTH_MAX);

        // 1. plan only. Height comes later, over arc length.
        DoubleList knotX = new DoubleList();
        DoubleList knotZ = new DoubleList();
        int[] knotSegmentEnd = new int[SEGMENTS.length];
        knotX.add(0.0D);
        knotZ.add(0.0D);

        for (int seg = 0; seg < SEGMENTS.length; seg++) {
            double[] spec = SEGMENTS[seg];
            double sx = knotX.last();
            double sz = knotZ.last();

            if ((int) spec[0] == LINE) {
                double dx = spec[5];
                double dz = spec[6];
                int steps = Math.max(2, (int) Math.round(Math.hypot(dx, dz) / KNOT_SPACING));
                for (int i = 1; i <= steps; i++) {
                    double f = (double) i / steps;
                    knotX.add(sx + dx * f);
                    knotZ.add(sz + dz * f);
                }
            } else {
                double cx = AXES[(int) spec[1]][0];
                double cz = AXES[(int) spec[1]][1];
                double r0 = Math.hypot(sx - cx, sz - cz);
                double r1 = spec[3] > 0 ? spec[3] : r0;
                double a0 = Math.atan2(sz - cz, sx - cx);
                double sweep = Math.toRadians(spec[2]);
                double span = Math.abs(sweep) * (r0 + r1) * 0.5D;
                // Knot spacing scales with radius: at a fixed 9 blocks a tight helix gets a knot
                // every 43 degrees and the spline ripples between them, which the cart feels as a
                // surge twice per turn.
                double spacing = Math.min(KNOT_SPACING, Math.max(2.5D, Math.min(r0, r1) * 0.42D));
                int steps = Math.max(3, (int) Math.round(span / spacing));
                for (int i = 1; i <= steps; i++) {
                    double f = (double) i / steps;
                    double ease = f * f * (3.0D - 2.0D * f);
                    double r = r0 + (r1 - r0) * ease;
                    double a = a0 + sweep * f;
                    knotX.add(cx + r * Math.cos(a));
                    knotZ.add(cz + r * Math.sin(a));
                }
            }
            knotSegmentEnd[seg] = knotX.size() - 1;
        }

        // 2. dense-sample the spline
        int knots = knotX.size();
        int count = (knots - 1) * SAMPLES_PER_KNOT + 1;
        CartTrack track = new CartTrack(origin, clamped, count);
        sampleSpline(knotX, knotZ, track);

        // 3. arc length, then the height profile over it, then re-measure arc length because the
        //    line just got longer in three dimensions than it was in two.
        track.measureArc();
        track.applyProfile(knotSegmentEnd, clamped);
        track.measureArc();

        // 4. frames, curvature and bank, all of which depend on the finished 3D line
        track.buildFrames();

        for (int seg = 0; seg < SEGMENTS.length; seg++) {
            track.segmentStart[seg + 1] =
                    Math.min(knotSegmentEnd[seg] * SAMPLES_PER_KNOT, count - 1);
        }
        return track;
    }

    private static void sampleSpline(DoubleList kx, DoubleList kz, CartTrack out) {
        int n = kx.size();
        int at = 0;
        for (int i = 0; i < n - 1; i++) {
            // Reflect the ends so the first and last spans are drawn rather than skipped.
            double[] p0 = knot(kx, kz, i - 1, true);
            double[] p1 = knot(kx, kz, i, false);
            double[] p2 = knot(kx, kz, i + 1, false);
            double[] p3 = knot(kx, kz, i + 2, true);

            double t0 = 0.0D;
            double t1 = t0 + Math.sqrt(Math.max(dist(p0, p1), 1.0E-6D));
            double t2 = t1 + Math.sqrt(Math.max(dist(p1, p2), 1.0E-6D));
            double t3 = t2 + Math.sqrt(Math.max(dist(p2, p3), 1.0E-6D));

            for (int s = 0; s < SAMPLES_PER_KNOT; s++) {
                double tt = t1 + (t2 - t1) * ((double) s / SAMPLES_PER_KNOT);
                double[] a1 = mix(p0, p1, (t1 - tt) / (t1 - t0), (tt - t0) / (t1 - t0));
                double[] a2 = mix(p1, p2, (t2 - tt) / (t2 - t1), (tt - t1) / (t2 - t1));
                double[] a3 = mix(p2, p3, (t3 - tt) / (t3 - t2), (tt - t2) / (t3 - t2));
                double[] b1 = mix(a1, a2, (t2 - tt) / (t2 - t0), (tt - t0) / (t2 - t0));
                double[] b2 = mix(a2, a3, (t3 - tt) / (t3 - t1), (tt - t1) / (t3 - t1));
                double[] p = mix(b1, b2, (t2 - tt) / (t2 - t1), (tt - t1) / (t2 - t1));
                out.px[at] = (float) p[0];
                out.pz[at] = (float) p[1];
                at++;
            }
        }
        out.px[at] = (float) kx.get(n - 1);
        out.pz[at] = (float) kz.get(n - 1);
    }

    private static double[] knot(DoubleList kx, DoubleList kz, int i, boolean reflect) {
        int n = kx.size();
        if (i >= 0 && i < n) {
            return new double[]{kx.get(i), kz.get(i)};
        }
        if (!reflect) {
            int c = Mth.clamp(i, 0, n - 1);
            return new double[]{kx.get(c), kz.get(c)};
        }
        // Mirror the neighbour through the end point, so the curve leaves straight.
        int edge = i < 0 ? 0 : n - 1;
        int inner = i < 0 ? 1 : n - 2;
        return new double[]{2.0D * kx.get(edge) - kx.get(inner), 2.0D * kz.get(edge) - kz.get(inner)};
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static double[] mix(double[] a, double[] b, double wa, double wb) {
        return new double[]{a[0] * wa + b[0] * wb, a[1] * wa + b[1] * wb};
    }

    private void measureArc() {
        this.arc[0] = 0.0F;
        for (int i = 1; i < this.arc.length; i++) {
            double dx = this.px[i] - this.px[i - 1];
            double dy = this.py[i] - this.py[i - 1];
            double dz = this.pz[i] - this.pz[i - 1];
            this.arc[i] = this.arc[i - 1] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * Lays each segment's share of the drop over its stretch of arc length, smooths the resulting
     * grade so the transitions are ramps rather than steps, integrates it, and rescales so the
     * total lands exactly on the depth budget.
     */
    private void applyProfile(int[] knotSegmentEnd, int depth) {
        int count = this.arc.length;
        float[] grade = new float[count];
        int previousEdge = 0;
        for (int seg = 0; seg < SEGMENTS.length; seg++) {
            int edge = Math.min(knotSegmentEnd[seg] * SAMPLES_PER_KNOT, count - 1);
            double run = this.arc[edge] - this.arc[previousEdge];
            if (edge > previousEdge && run > 1.0E-6D) {
                float g = (float) (SEGMENTS[seg][4] * depth / run);
                for (int i = previousEdge; i < edge; i++) {
                    grade[i] = g;
                }
            }
            previousEdge = edge;
        }

        double mean = this.arc[count - 1] / Math.max(1, count - 1);
        int window = Math.max(3, (int) (VERTICAL_CURVE / Math.max(mean, 1.0E-6D)));
        float[] smoothed = boxBlur(grade, window);

        double y = 0.0D;
        this.py[0] = 0.0F;
        for (int i = 1; i < count; i++) {
            y -= smoothed[i] * (this.arc[i] - this.arc[i - 1]);
            this.py[i] = (float) y;
        }
        if (this.py[count - 1] < -1.0E-4F) {
            float scale = (float) (depth / -this.py[count - 1]);
            for (int i = 0; i < count; i++) {
                this.py[i] *= scale;
            }
        }
    }

    private static float[] boxBlur(float[] src, int window) {
        float[] out = new float[src.length];
        int half = window / 2;
        for (int i = 0; i < src.length; i++) {
            float sum = 0.0F;
            int n = 0;
            for (int k = Math.max(0, i - half); k <= Math.min(src.length - 1, i + half); k++) {
                sum += src[k];
                n++;
            }
            out[i] = sum / Math.max(1, n);
        }
        return out;
    }

    /**
     * Tangents, a rotation-minimising up vector, curvature and the geometric bank.
     *
     * <p>The frame is parallel-transported rather than taken from the curve's own normal. A Frenet
     * normal flips through 180 degrees at every inflection point, which on this line - a descent
     * made almost entirely of reversing arcs - would snap-roll the cart upside down several times
     * per ride.
     */
    private void buildFrames() {
        int count = this.arc.length;

        for (int i = 0; i < count; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(count - 1, i + 1);
            Vec3 t = normalise(this.px[b] - this.px[a], this.py[b] - this.py[a],
                    this.pz[b] - this.pz[a]);
            this.tx[i] = (float) t.x;
            this.ty[i] = (float) t.y;
            this.tz[i] = (float) t.z;
        }

        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        for (int i = 0; i < count; i++) {
            Vec3 t = new Vec3(this.tx[i], this.ty[i], this.tz[i]);
            // Remove any component along the tangent, then renormalise: that is the transport.
            Vec3 transported = up.subtract(t.scale(up.dot(t)));
            if (transported.lengthSqr() < 1.0E-8D) {
                transported = new Vec3(0.0D, 1.0D, 0.0D).subtract(t.scale(t.y));
            }
            up = transported.normalize();
            this.ux[i] = (float) up.x;
            this.uy[i] = (float) up.y;
            this.uz[i] = (float) up.z;
        }

        for (int i = 0; i < count; i++) {
            int a = Math.max(0, i - 2);
            int b = Math.min(count - 1, i + 2);
            double run = this.arc[b] - this.arc[a];
            double k = 0.0D;
            if (run > 1.0E-4D) {
                Vec3 ta = new Vec3(this.tx[a], this.ty[a], this.tz[a]);
                Vec3 tb = new Vec3(this.tx[b], this.ty[b], this.tz[b]);
                k = tb.subtract(ta).length() / run;
            }
            this.curvature[i] = (float) k;

            // Sign the bank by which way the line is turning, using the horizontal cross product.
            Vec3 ta = new Vec3(this.tx[a], 0.0D, this.tz[a]);
            Vec3 tb = new Vec3(this.tx[b], 0.0D, this.tz[b]);
            double turn = ta.x * tb.z - ta.z * tb.x;
            double geometric = Math.toDegrees(Math.atan(
                    DESIGN_SPEED * DESIGN_SPEED * k / 20.0D));
            this.bank[i] = Mth.clamp((float) (turn < 0 ? geometric : -geometric),
                    -MAX_BANK_DEG, MAX_BANK_DEG);
        }

        // One smoothing pass: curvature from finite differences is noisy, and the bank is what the
        // camera follows, so noise in it is felt directly as jitter.
        System.arraycopy(boxBlur(this.bank, 9), 0, this.bank, 0, count);
        System.arraycopy(boxBlur(this.curvature, 9), 0, this.curvature, 0, count);
    }

    private static Vec3 normalise(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return len < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(x / len, y / len, z / len);
    }

    // --- self-audit ----------------------------------------------------------------------

    /** The corridor is 7 wide and 6 tall, so the clearance it needs is not a sphere. */
    private static final double BORE_CLEARANCE_H = 9.0D;
    private static final double BORE_CLEARANCE_V = 8.0D;
    /** Arc-length gap past which two samples count as genuinely different bits of track. */
    private static final double SELF_APPROACH_WINDOW = 40.0D;
    /** |dy/ds| past which the cart stops being on a rail. */
    public static final double MAX_GRADE = 0.50D;

    /**
     * What {@code preview_cart_track.py} prints, computed from the baked line instead.
     *
     * <p>Having the same audit on both sides is the point: the Python settles the layout, and this
     * proves the Java port of it produces the same curve rather than a plausible-looking different
     * one. Numbers that disagree mean the port drifted.
     *
     * @param tightestGap fraction of the required clearance, so below 1.0 the corridor overlaps
     */
    public record Audit(double length, double drop, double worstGrade, double worstGradeAt,
                        double tightestGap, double tightestGapAt, double reach) {

        public boolean ok() {
            return this.worstGrade <= MAX_GRADE && this.tightestGap >= 1.0D;
        }
    }

    public Audit audit() {
        int count = this.arc.length;

        double worst = 0.0D;
        double worstAt = 0.0D;
        for (int i = 1; i < count; i++) {
            double run = this.arc[i] - this.arc[i - 1];
            if (run <= 1.0E-6D) {
                continue;
            }
            double g = Math.abs(this.py[i] - this.py[i - 1]) / run;
            if (g > worst) {
                worst = g;
                worstAt = this.arc[i];
            }
        }

        double tightest = Double.MAX_VALUE;
        double tightestAt = 0.0D;
        int stride = Math.max(1, count / 400);
        for (int i = 0; i < count; i += stride) {
            for (int j = 0; j < count; j += stride) {
                if (Math.abs(this.arc[j] - this.arc[i]) <= SELF_APPROACH_WINDOW) {
                    continue;
                }
                double dh = Math.hypot(this.px[j] - this.px[i], this.pz[j] - this.pz[i]);
                double dv = Math.abs(this.py[j] - this.py[i]);
                double occupancy = Math.hypot(dh / BORE_CLEARANCE_H, dv / BORE_CLEARANCE_V);
                if (occupancy < tightest) {
                    tightest = occupancy;
                    tightestAt = this.arc[i];
                }
            }
        }

        double reach = 0.0D;
        for (int i = 0; i < count; i++) {
            reach = Math.max(reach, Math.max(Math.abs(this.px[i]), Math.abs(this.pz[i])));
        }

        return new Audit(length(), -this.py[count - 1], worst, worstAt,
                tightest == Double.MAX_VALUE ? Double.POSITIVE_INFINITY : tightest,
                tightestAt, reach);
    }

    // --- persistence ---------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putLong("Origin", this.origin.asLong());
        tag.putInt("Depth", this.depth);
        return tag;
    }

    /** Rebuilds from the three numbers that determine the line, or null if the format moved on. */
    public static CartTrack load(CompoundTag tag) {
        if (tag.getInt("Version") != VERSION) {
            return null;
        }
        return build(BlockPos.of(tag.getLong("Origin")), tag.getInt("Depth"));
    }

    /** A growable double array, because this runs once per undercroft and boxing 100 knots is silly. */
    private static final class DoubleList {
        private double[] items = new double[128];
        private int size;

        void add(double value) {
            if (this.size == this.items.length) {
                double[] bigger = new double[this.size * 2];
                System.arraycopy(this.items, 0, bigger, 0, this.size);
                this.items = bigger;
            }
            this.items[this.size++] = value;
        }

        double get(int index) {
            return this.items[index];
        }

        double last() {
            return this.items[this.size - 1];
        }

        int size() {
            return this.size;
        }
    }
}
