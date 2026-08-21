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

    /** Rock left between the chasm wall and the track running along it. */
    private static final double WALL = 6.0D;

    /** The chasm opens this far down the descent; above it the line is still a tunnel. */
    private static final double OPENS_AT = 0.34D;

    /** The second lobe, offset so the far side is never fully in view. */
    private static final double LOBE_OFFSET = 14.0D;
    private static final double LOBE_SCALE = 0.62D;

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
    private final double axisX;
    private final double axisZ;
    /** Radius the falls sit at: inside the narrowest point, so every one of them is in open air. */
    private final double fallRadius;

    public ChasmCarver(CartTrack track, UndercroftPalette palette) {
        this.track = track;
        this.palette = palette;
        Vec3 start = track.positionAt(0.0D);
        Vec3 deep = track.positionAt(track.length());
        this.topY = (int) Math.floor(Mthlerp(start.y, deep.y, OPENS_AT));
        this.floorY = (int) Math.floor(deep.y) - 3;
        // The spiral turns about its origin; drift is small enough that one axis serves.
        Vec3 mid = track.positionAt(track.length() * 0.7D);
        Vec3 mid2 = track.positionAt(track.length() * 0.7D + halfTurn());
        this.axisX = (mid.x + mid2.x) * 0.5D;
        this.axisZ = (mid.z + mid2.z) * 0.5D;

        double narrowest = Double.MAX_VALUE;
        for (int y = this.topY; y >= this.floorY; y--) {
            narrowest = Math.min(narrowest, radiusAt(y));
        }
        this.fallRadius = Math.max(3.0D, narrowest - 2.0D);
    }

    private double halfTurn() {
        // Half a lap, near enough: the line is one spiral, so two points half a turn apart
        // straddle the axis and their midpoint is on it.
        return this.track.length() / 10.0D;
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
        double lobeR = radius * LOBE_SCALE;
        double lobeX = this.axisX + LOBE_OFFSET * Math.cos(y * 0.06D);
        double lobeZ = this.axisZ + LOBE_OFFSET * Math.sin(y * 0.06D);

        int reach = (int) Math.ceil(Math.max(radius, LOBE_OFFSET + lobeR)) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double wx = this.axisX + dx;
                double wz = this.axisZ + dz;
                double main = Math.hypot(dx, dz);
                double lobe = Math.hypot(wx - lobeX, wz - lobeZ);
                // A ragged edge, so the wall is rock rather than a lathe-turned cylinder.
                double ragged = 1.6D * (UndercroftPalette.hash((int) wx, y, (int) wz, 5) - 0.5D);
                boolean inside = main < radius + ragged || lobe < lobeR + ragged;
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
                double a = i * (2.0D * Math.PI / FALLS) + 0.4D;
                // Fixed in WORLD space, not at a fixed angle from a radius that breathes. Tying a
                // fall to the wall makes it wander in and out with the wall, and what you get is
                // three blue lines zigzagging across the ledges rather than water falling. Held at
                // one XZ inside the narrowest point of the chasm, each fall is a vertical column
                // in open air the whole way down, which is the thing worth looking at.
                double fx = this.axisX + this.fallRadius * Math.cos(a);
                double fz = this.axisZ + this.fallRadius * Math.sin(a);
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
            double cx = this.axisX + r * Math.cos(a);
            double cz = this.axisZ + r * Math.sin(a);

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

    /**
     * The chasm's radius at a height: whatever the track is doing there, pulled in by the wall
     * thickness. Found by walking the line for the sample nearest that height - the descent is
     * monotonic, so there is exactly one.
     */
    private double radiusAt(int y) {
        double bestS = 0.0D;
        double bestGap = Double.MAX_VALUE;
        double stride = Math.max(1.0D, this.track.length() / 400.0D);
        for (double s = 0.0D; s <= this.track.length(); s += stride) {
            double gap = Math.abs(this.track.positionAt(s).y - y);
            if (gap < bestGap) {
                bestGap = gap;
                bestS = s;
            }
        }
        Vec3 on = this.track.positionAt(bestS);
        return Math.hypot(on.x - this.axisX, on.z - this.axisZ) - WALL;
    }
}
