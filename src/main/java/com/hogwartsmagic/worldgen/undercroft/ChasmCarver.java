package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The void the lower half of the descent is pinned to the side of.
 *
 * <p>Without this the undercroft is a very long tunnel. The chasm is what turns the same spiral
 * into a road cut into a cliff: the ledges and trestles the corridor already draws only read as
 * ledges and trestles once there is nothing on the open side of them.
 *
 * <h2>Its width is taken from the track, not chosen</h2>
 *
 * <p>At each height the chasm is carved to whatever radius the track happens to be at there, minus
 * a margin. That is the only way to keep the track on the rim for the whole descent: the spiral's
 * radius breathes between roughly 13 and 31, so any fixed radius would either cut the track clean
 * off the wall where the line comes in, or leave it buried in rock where the line swings out. It
 * also means the chasm inherits the breathing - it bulges and pinches with height instead of being
 * a cone, which is worth more than any amount of decoration.
 *
 * <h2>Making it bottomless</h2>
 *
 * <p>There is a real floor. What sells the drop is that nothing lights it: the bottom band gets the
 * blackest palette and no light source at all, so at the fog distances the undercroft uses the
 * floor is simply never visible from the track. Water falls past into that dark and never arrives,
 * which is the most convincing depth cue Minecraft has, and the two overlapping lobes mean a spur
 * of rock always hides part of the far wall - a wall you can see all of is a wall you can measure.
 */
public final class ChasmCarver {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    /**
     * How far inside the line the chasm wall stands: the corridor's own half-width.
     *
     * <p>This was six, which left two blocks of untouched rock between the corridor wall and the
     * void, so a ledge "open on the void side" opened onto a rock face and the only place the drop
     * could be seen at all was a trestle. At the corridor's half-width the void begins exactly at the
     * edge of the deck, which is what a ledge is.
     */
    private static final double WALL = 3.0D;

    /** The chasm opens this far down the descent; above it the line is still a tunnel. */
    private static final double OPENS_AT = 0.34D;

    /**
     * A buttress of rock left standing out from the chasm wall, so the far side is never fully in
     * view, turning slowly about the axis as it descends.
     *
     * <p>This used to be a second lobe of void added to the first, and a lobe that pokes out of the
     * main circle pokes out of it straight through the line: it reached past the rim by up to six
     * blocks, cutting the rock the vaults are dug into from below. Leaving rock IN the void does the
     * same job for the eye - a spur across the far wall - without ever reaching the track.
     */
    private static final double SPUR_INSET = 7.0D;
    private static final double SPUR_R = 4.5D;

    /** How many falls come down the wall. Three reads as weather; a dozen reads as plumbing. */
    private static final int FALLS = 3;

    /**
     * Stalactite clusters hung from the chasm roof.
     *
     * <p>Clusters of a few with a shared base and graded lengths, never evenly spaced singles.
     * A noise-gated scatter of one-block spikes reads as confetti rather than as rock, which is
     * the "scatter where structure was intended" failure in the rendering skill's own list, and
     * the density cap is there because anything with a busy texture on transparency carries far
     * more visual weight than its block count - the same thing that turned 367 cobwebs into a
     * blizzard in this repo once already.
     */
    private static final int CLUSTERS = 16;
    private static final int CLUSTER_MIN = 3;
    private static final int CLUSTER_MAX = 7;
    private static final int SPIKE_MAX = 6;

    /** Height above the floor below which nothing is lit, so the bottom is never visible. */
    public static final int DARK_BAND = 22;

    private final CartTrack track;
    private final UndercroftPalette palette;
    private final int topY;
    private final int floorY;
    /**
     * The chasm's centre and radius at each height, indexed by {@code topY - y}.
     *
     * <p>Per height because the spiral's centre drifts as it descends, and the chasm has to drift
     * with it or the line wanders on and off its rim. Worked out once: every slice needs its own
     * and the search over the line is the expensive part.
     */
    private final double[] centreX;
    private final double[] centreZ;
    private final double[] radius;
    /** Where the falls come down, fixed in world space, or NaN where no column stays clear. */
    private final double[] fallX = new double[FALLS];
    private final double[] fallZ = new double[FALLS];

    public ChasmCarver(CartTrack track, UndercroftPalette palette) {
        this.track = track;
        this.palette = palette;
        Vec3 start = track.positionAt(0.0D);
        Vec3 deep = track.positionAt(track.length());
        this.topY = (int) Math.floor(Mthlerp(start.y, deep.y, OPENS_AT));
        this.floorY = (int) Math.floor(deep.y) - 3;

        int levels = Math.max(1, this.topY - this.floorY + 1);
        this.centreX = new double[levels];
        this.centreZ = new double[levels];
        this.radius = new double[levels];
        double stride = Math.max(0.5D, track.length() / 1200.0D);
        for (int i = 0; i < levels; i++) {
            int y = this.topY - i;
            double bestS = 0.0D;
            double bestGap = Double.MAX_VALUE;
            for (double s = 0.0D; s <= track.length(); s += stride) {
                double gap = Math.abs(track.positionAt(s).y - y);
                if (gap < bestGap) {
                    bestGap = gap;
                    bestS = s;
                }
            }
            Vec3 on = track.positionAt(bestS);
            Vec3 axis = track.axisAt(bestS);
            this.centreX[i] = axis.x;
            this.centreZ[i] = axis.z;
            this.radius[i] = Math.hypot(on.x - axis.x, on.z - axis.z) - WALL;
        }

        // Each fall is one fixed column of world XZ, so it has to be clear of the wall at EVERY
        // height it passes. Search outward from the middle of the descent for the widest radius
        // at which that holds, so the falls stand as near the rim - and the riders - as they can.
        int mid = levels / 2;
        for (int f = 0; f < FALLS; f++) {
            double a = f * (2.0D * Math.PI / FALLS) + 0.4D;
            this.fallX[f] = Double.NaN;
            this.fallZ[f] = Double.NaN;
            for (double rho = 16.0D; rho >= 2.0D; rho -= 0.5D) {
                double fx = this.centreX[mid] + rho * Math.cos(a);
                double fz = this.centreZ[mid] + rho * Math.sin(a);
                boolean clear = true;
                for (int i = 6; i < levels - 3 && clear; i++) {
                    clear = Math.hypot(fx - this.centreX[i], fz - this.centreZ[i])
                            < this.radius[i] - 2.5D;
                }
                if (clear) {
                    this.fallX[f] = fx;
                    this.fallZ[f] = fz;
                    break;
                }
            }
        }
    }

    private double radiusAt(int y) {
        return this.radius[Math.max(0, Math.min(this.radius.length - 1, this.topY - y))];
    }

    private double centreXAt(int y) {
        return this.centreX[Math.max(0, Math.min(this.centreX.length - 1, this.topY - y))];
    }

    private double centreZAt(int y) {
        return this.centreZ[Math.max(0, Math.min(this.centreZ.length - 1, this.topY - y))];
    }

    private static double Mthlerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    public int stepCount() {
        return Math.max(1, this.topY - this.floorY + 1);
    }

    public int floorY() {
        return this.floorY;
    }

    public int topY() {
        return this.topY;
    }

    /** One horizontal slice per step, working down. */
    public void carveStep(CarveSink sink, int step) {
        int y = this.topY - step;
        if (y < this.floorY) {
            return;
        }

        double radius = radiusAt(y);
        if (radius < 4.0D) {
            return;
        }
        double axisX = centreXAt(y);
        double axisZ = centreZAt(y);
        // Only once the void is wide enough to have a spur and still be a void around it.
        boolean spur = radius > SPUR_INSET + SPUR_R + 4.0D;
        double spurX = axisX + (radius - SPUR_INSET) * Math.cos(y * 0.045D + 1.3D);
        double spurZ = axisZ + (radius - SPUR_INSET) * Math.sin(y * 0.045D + 1.3D);

        int reach = (int) Math.ceil(radius) + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double wx = axisX + dx;
                double wz = axisZ + dz;
                double main = Math.hypot(dx, dz);
                // A ragged edge, so the wall is rock rather than a lathe-turned cylinder.
                double ragged = 1.6D * (UndercroftPalette.hash((int) wx, y, (int) wz, 5) - 0.5D);
                boolean inside = main < radius + ragged
                        && !(spur && Math.hypot(wx - spurX, wz - spurZ) < SPUR_R + ragged);
                if (!inside) {
                    continue;
                }
                int bx = (int) Math.floor(wx);
                int bz = (int) Math.floor(wz);
                if (y == this.floorY) {
                    sink.set(bx, y, bz, this.palette.rock(bx, y, bz));
                } else {
                    sink.set(bx, y, bz, AIR);
                }
            }
        }

        // Hung on the LAST slice, not the first. The chasm is carved downwards, so anything
        // placed at the roof on step one is quietly deleted by every level carved beneath it.
        if (y == this.floorY) {
            hangStalactites(sink, this.topY, radiusAt(this.topY));
        }

        // Three falls down the wall, and they are CONTINUOUS. Gating each level on a hash made
        // them intermittent, and intermittent water is not a waterfall - it is blue confetti down
        // a cliff, which is the "scatter where structure was intended" failure in the skill's own
        // list. A fall is only a depth cue if you can follow it with your eye all the way into the
        // dark, so every level in the band gets its block.
        if (y < this.topY - 6 && y > this.floorY + 3) {
            for (int i = 0; i < FALLS; i++) {
                // Fixed in WORLD space, not at a fixed angle from a radius that breathes. Tying a
                // fall to the wall makes it wander in and out with the wall, and what you get is
                // three blue lines zigzagging across the ledges rather than water falling. Held at
                // one XZ that is clear of the wall at every height, each fall is a vertical column
                // in open air the whole way down, which is the thing worth looking at.
                double fx = this.fallX[i];
                double fz = this.fallZ[i];
                if (Double.isNaN(fx)) {
                    continue;
                }
                // Two by two, not one. A single column seventy blocks long reads as a blue wire at
                // any distance worth looking from, and distance is the entire job these are doing.
                for (int ox = 0; ox <= 1; ox++) {
                    for (int oz = 0; oz <= 1; oz++) {
                        sink.set((int) Math.floor(fx) + ox, y, (int) Math.floor(fz) + oz, WATER);
                    }
                }
            }
        }
    }

    /** Clusters of dripstone hanging from the roof of the chasm. */
    private void hangStalactites(CarveSink sink, int roofY, double radius) {
        for (int c = 0; c < CLUSTERS; c++) {
            double a = UndercroftPalette.hash(c, 0, 0, 201) * 2.0D * Math.PI;
            // Jitter the radius as well as the angle: equal radii at equal angles is a ring of
            // fence posts, which is the other half of the same failure.
            double r = radius * (0.25D + 0.65D * UndercroftPalette.hash(c, 1, 0, 202));
            double cx = centreXAt(roofY) + r * Math.cos(a);
            double cz = centreZAt(roofY) + r * Math.sin(a);

            int spikes = CLUSTER_MIN + (int) (UndercroftPalette.hash(c, 2, 0, 203)
                    * (CLUSTER_MAX - CLUSTER_MIN + 1));
            for (int i = 0; i < spikes; i++) {
                double sa = UndercroftPalette.hash(c, i, 3, 204) * 2.0D * Math.PI;
                double sr = UndercroftPalette.hash(c, i, 4, 205) * 2.4D;
                int bx = (int) Math.floor(cx + sr * Math.cos(sa));
                int bz = (int) Math.floor(cz + sr * Math.sin(sa));
                // Graded: the middle of a cluster hangs longest, the edges barely at all.
                int length = 1 + (int) ((1.0D - sr / 2.4D) * SPIKE_MAX
                        * (0.5D + 0.5D * UndercroftPalette.hash(bx, i, bz, 206)));
                for (int d = 0; d < length; d++) {
                    boolean tip = d == length - 1;
                    sink.set(bx, roofY - d, bz, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.state.properties
                                    .BlockStateProperties.VERTICAL_DIRECTION,
                                    net.minecraft.core.Direction.DOWN)
                            .setValue(net.minecraft.world.level.block.state.properties
                                    .BlockStateProperties.DRIPSTONE_THICKNESS,
                                    tip ? net.minecraft.world.level.block.state.properties
                                            .DripstoneThickness.TIP
                                            : d == 0
                                            ? net.minecraft.world.level.block.state.properties
                                                    .DripstoneThickness.BASE
                                            : net.minecraft.world.level.block.state.properties
                                                    .DripstoneThickness.MIDDLE));
                }
            }
        }
    }

}
