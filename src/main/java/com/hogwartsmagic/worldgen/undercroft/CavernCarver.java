package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The dragon's hall: a cavern cut into the rock at the very bottom of the line, where it ends.
 *
 * <p>The Ironbelly used to stand inside the deepest vault, a room nine wide and eight tall, which
 * is a cupboard for an animal that size. It could not turn round in it, its chain radius was wider
 * than the room, and from the line you never saw it at all - it was behind a vault door. The film
 * puts the dragon in the open in front of the deepest vaults, and the reason is the same one the
 * whole undercroft is built on: scale needs something to measure it against, and a dragon in a
 * cavern is that thing.
 *
 * <h2>Shape</h2>
 *
 * <p>The hall is laid out along the last {@link #LENGTH} blocks of the line, on the rock side. Each
 * column of it is placed by where it projects onto the line - how far along, how far out into the
 * rock - so the hall follows the curve of the track rather than being a box the track passes
 * through, and its floor follows the track's descent, flush with the deck the whole way. The void
 * side is left alone: the hall opens straight onto the chasm floor, which is the one place the
 * bottom of the drop is allowed to be seen, because by now you are standing on it.
 *
 * <h2>Light</h2>
 *
 * <p>Red, from below, which is the reference's own note. Fissures run across the floor with light
 * under red glass in them, so the dragon is lit from its belly up - uplighting is what makes a thing
 * that big read as big. There is no hanging light in here at all.
 *
 * <h2>What it hands on</h2>
 *
 * <p>Where the dragon is chained and where its four anchor stubs stand, and where in the back wall
 * the deepest vault's door goes. {@link VaultCarver} cuts that vault, the builder spawns the dragon,
 * and both ask this class rather than recomputing the hall.
 */
public final class CavernCarver {

    /** How much of the end of the line the hall spans. */
    public static final double LENGTH = 38.0D;
    /** Widest reach into the rock from the line's centre. */
    private static final double REACH = 15.0D;
    /** Tallest point of the dome above the deck. */
    private static final double HEIGHT = 13.0D;
    /** How far past the end of the line the hall carries on, behind the buffer stop. */
    private static final double END_CAP = 7.0D;
    /**
     * The running surface, sleepers included: nothing in here may touch it.
     *
     * <p>Not the corridor's full width. The hall measures distance from the line flat, the
     * corridor measures it in its banked frame, and at the high edge the two disagree by a block -
     * so keeping the corridor's whole half-width left a two-block ridge of rock between the deck
     * and the hall floor. The hall floor now meets the deck flush.
     */
    private static final double DECK_KEEP = 2.5D;

    /** Where along the hall the dragon is chained, as a fraction of its length. */
    private static final double ANCHOR_AT = 0.52D;
    /** How far the anchor stubs stand from the anchor. */
    private static final double STUB_RING = 5.5D;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState GLASS = Blocks.RED_STAINED_GLASS.defaultBlockState();
    private static final BlockState EMBER = Blocks.SHROOMLIGHT.defaultBlockState();
    private static final BlockState HEARTH = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState PLATE = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState SCORCH = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState ASH = Blocks.BASALT.defaultBlockState();
    private static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();
    private static final BlockState STUB = Blocks.LIGHTNING_ROD.defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.UP);
    private static final BlockState BUFFER = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
    private static final BlockState BUFFER_BEAM = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    private final CartTrack track;
    private final UndercroftPalette palette;
    private final double sA;
    private final double sB;

    // The plan, one entry per column of the box the hall can occupy.
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    /** Floor height of each column, or Integer.MIN_VALUE where the column is not hall. */
    private final int[] floorY;
    /** Clear height above the floor. */
    private final int[] height;
    /** Lateral distance from the line toward the rock, so 0 is the line and positive is rock. */
    private final float[] lateral;
    /** Fraction along the hall, 0 at the start, 1 at the buffer stop, past 1 behind it. */
    private final float[] along;
    /** True for columns of the rock shell just outside the hall. */
    private final boolean[] shell;

    private final Vec3 anchor;
    private final List<BlockPos> stubs = new ArrayList<>();
    private final double vaultS;
    private final double vaultClear;

    public CavernCarver(CartTrack track, UndercroftPalette palette) {
        this.track = track;
        this.palette = palette;
        this.sB = track.length();
        this.sA = Math.max(0.0D, this.sB - LENGTH);

        // Bounding box: every point of the hall's stretch of line, out by its reach either way.
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (double s = this.sA - 4.0D; s <= this.sB; s += 1.0D) {
            Vec3 p = track.positionAt(s);
            x0 = Math.min(x0, p.x);
            x1 = Math.max(x1, p.x);
            z0 = Math.min(z0, p.z);
            z1 = Math.max(z1, p.z);
        }
        double pad = REACH + END_CAP + 3.0D;
        this.minX = (int) Math.floor(x0 - pad);
        this.minZ = (int) Math.floor(z0 - pad);
        this.sizeX = (int) Math.ceil(x1 + pad) - this.minX + 1;
        this.sizeZ = (int) Math.ceil(z1 + pad) - this.minZ + 1;
        int n = this.sizeX * this.sizeZ;
        this.floorY = new int[n];
        this.height = new int[n];
        this.lateral = new float[n];
        this.along = new float[n];
        this.shell = new boolean[n];

        // Samples of the line to project columns onto. A little before the hall begins as well,
        // so a column next to the approach projects onto the approach and is left alone, rather
        // than clamping onto the hall's first sample and being swallowed into it.
        int samples = (int) Math.ceil((this.sB - (this.sA - 24.0D)) / 0.5D) + 1;
        double[] ss = new double[samples];
        Vec3[] ps = new Vec3[samples];
        Vec3[] rock = new Vec3[samples];
        Vec3[] fwd = new Vec3[samples];
        for (int i = 0; i < samples; i++) {
            double s = Math.min(this.sB, Math.max(0.0D, this.sA - 24.0D + i * 0.5D));
            ss[i] = s;
            ps[i] = track.positionAt(s);
            Vec3 t = track.tangentAt(s);
            fwd[i] = new Vec3(t.x, 0.0D, t.z).normalize();
            Vec3 r = track.rightAt(s);
            Vec3 flat = new Vec3(r.x, 0.0D, r.z).normalize();
            rock[i] = flat.scale(-track.voidSideAt(s));
        }

        for (int ix = 0; ix < this.sizeX; ix++) {
            for (int iz = 0; iz < this.sizeZ; iz++) {
                int k = ix * this.sizeZ + iz;
                this.floorY[k] = Integer.MIN_VALUE;
                double wx = this.minX + ix + 0.5D;
                double wz = this.minZ + iz + 0.5D;
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int i = 0; i < samples; i++) {
                    double dx = wx - ps[i].x;
                    double dz = wz - ps[i].z;
                    double d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
                double s = ss[best];
                double dx = wx - ps[best].x;
                double dz = wz - ps[best].z;
                double u = dx * rock[best].x + dz * rock[best].z;
                double e = dx * fwd[best].x + dz * fwd[best].z;
                double a = (s - this.sA) / Math.max(1.0D, this.sB - this.sA);
                if (s >= this.sB - 0.25D && e > 0.0D) {
                    a = 1.0D + e / LENGTH;        // behind the buffer stop
                }
                this.lateral[k] = (float) u;
                this.along[k] = (float) a;

                double w = widthAt(a, ix, iz);
                boolean inPlan = a >= 0.0D && u >= -2.5D && u <= w;
                if (a > 1.0D) {
                    // The end cap rounds off rather than stopping square.
                    double cap = (a - 1.0D) * LENGTH / END_CAP;
                    inPlan = cap < 1.0D && u >= -2.5D && u <= w * Math.sqrt(1.0D - cap * cap);
                }
                if (s <= this.sA + 0.25D) {
                    inPlan = false;
                }
                int deckY = (int) Math.floor(ps[best].y - 2.0D);
                if (inPlan) {
                    this.floorY[k] = deckY;
                    this.height[k] = heightAt(a, u, w, ix, iz);
                } else if (u > DECK_KEEP && u <= w + 2.0D && a > 0.0D && a < 1.0D + END_CAP / LENGTH + 0.08D) {
                    // A course of rock round the outside, so the hall never opens into whatever
                    // cave happens to be next door. Only on the rock side: the void side is the
                    // chasm, and the approach is the corridor's to line.
                    this.shell[k] = true;
                    this.floorY[k] = Integer.MIN_VALUE + 1;
                    this.height[k] = deckY;   // reused: the floor height the shell starts from
                }
            }
        }

        // The dragon's place, and the deepest vault in the wall behind it.
        this.anchor = pointAt(ANCHOR_AT, 0.50D);
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0D + 0.5D;
            int bx = (int) Math.floor(this.anchor.x + STUB_RING * Math.cos(angle));
            int bz = (int) Math.floor(this.anchor.z + STUB_RING * Math.sin(angle));
            int f = floorAt(bx, bz);
            if (f != Integer.MIN_VALUE) {
                this.stubs.add(new BlockPos(bx, f + 1, bz));
            }
        }
        this.vaultS = this.sA + (this.sB - this.sA) * 0.62D;
        this.vaultClear = widthAt(0.62D, 0, 0) + 1.0D;
    }

    /** How far into the rock the hall reaches at a point along it. Swells to the far end. */
    private static double widthAt(double a, int ix, int iz) {
        double t = Math.max(0.0D, Math.min(1.0D, a));
        double swell = Math.sin(Math.PI * Math.pow(t, 0.8D));
        double ragged = 1.4D * (UndercroftPalette.hash(ix, 0, iz, 401) - 0.5D);
        return REACH * (0.45D + 0.55D * swell) + ragged;
    }

    /** A dome: low at the walls, highest a little out from the line. */
    private static int heightAt(double a, double u, double w, int ix, int iz) {
        double mid = w * 0.45D;
        double span = w * 0.55D + 5.0D;
        double across = Math.max(0.0D, 1.0D - ((u - mid) / span) * ((u - mid) / span));
        double t = Math.max(0.0D, Math.min(1.0D, a));
        double lengthwise = 0.55D + 0.45D * Math.sin(Math.PI * t);
        double ragged = 1.8D * (UndercroftPalette.hash(ix, 1, iz, 402) - 0.5D);
        return (int) Math.round(4.0D + (HEIGHT - 4.0D) * Math.sqrt(across) * lengthwise + ragged);
    }

    /** The world point at a fraction along the hall and a fraction of its reach into the rock. */
    private Vec3 pointAt(double a, double reachFraction) {
        double s = this.sA + (this.sB - this.sA) * a;
        Vec3 p = this.track.positionAt(s);
        Vec3 r = this.track.rightAt(s);
        Vec3 flat = new Vec3(r.x, 0.0D, r.z).normalize().scale(-this.track.voidSideAt(s));
        double reach = widthAt(a, 0, 0) * reachFraction;
        int x = (int) Math.floor(p.x + flat.x * reach);
        int z = (int) Math.floor(p.z + flat.z * reach);
        int f = floorAt(x, z);
        return new Vec3(x + 0.5D, (f == Integer.MIN_VALUE ? Math.floor(p.y - 2.0D) : f) + 1.0D, z + 0.5D);
    }

    private int index(int x, int z) {
        int ix = x - this.minX;
        int iz = z - this.minZ;
        if (ix < 0 || iz < 0 || ix >= this.sizeX || iz >= this.sizeZ) {
            return -1;
        }
        return ix * this.sizeZ + iz;
    }

    /** Floor height of the hall at a column, or {@code Integer.MIN_VALUE} if it is not hall. */
    public int floorAt(int x, int z) {
        int k = index(x, z);
        return k < 0 || this.floorY[k] <= Integer.MIN_VALUE + 1 ? Integer.MIN_VALUE : this.floorY[k];
    }

    /** True for a world cell inside the hall's open space. */
    public boolean contains(int x, int y, int z) {
        int k = index(x, z);
        if (k < 0 || this.floorY[k] <= Integer.MIN_VALUE + 1) {
            return false;
        }
        return y > this.floorY[k] && y <= this.floorY[k] + this.height[k];
    }

    /** The stretch of line the hall covers, so the lights can leave it dark. */
    public boolean covers(double s) {
        return s >= this.sA - 2.0D;
    }

    /** Where the dragon stands and is chained. */
    public Vec3 anchor() {
        return this.anchor;
    }

    public List<BlockPos> stubs() {
        return this.stubs;
    }

    /** Where along the line the deepest vault's door faces, and how far out its door stands. */
    public double vaultS() {
        return this.vaultS;
    }

    public double vaultClear() {
        return this.vaultClear;
    }

    // ------------------------------------------------------------------ carving

    /** One row of columns per step, then one last step to dress the floor. */
    public int stepCount() {
        return this.sizeX + 1;
    }

    public void carveStep(CarveSink sink, int step) {
        if (step >= this.sizeX) {
            dress(sink);
            return;
        }
        int ix = step;
        for (int iz = 0; iz < this.sizeZ; iz++) {
            int k = ix * this.sizeZ + iz;
            int x = this.minX + ix;
            int z = this.minZ + iz;
            if (this.shell[k]) {
                int base = this.height[k];
                for (int y = base - 1; y <= base + (int) HEIGHT + 2; y++) {
                    sink.set(x, y, z, this.palette.rock(x, y, z));
                }
                continue;
            }
            int f = this.floorY[k];
            if (f == Integer.MIN_VALUE) {
                continue;
            }
            double u = this.lateral[k];
            int h = this.height[k];
            boolean deck = Math.abs(u) <= DECK_KEEP && this.along[k] <= 1.0D;
            if (!deck) {
                sink.set(x, f - 1, z, this.palette.rock(x, f - 1, z));
                sink.set(x, f, z, floor(x, f, z, k));
            }
            // Over the deck itself, leave the course directly on top of it: the deck is banked, so
            // on its high side the running surface is a block above the hall floor, and clearing
            // that course would hole the deck along one edge.
            for (int y = deck ? f + 2 : f + 1; y <= f + h; y++) {
                sink.set(x, y, z, AIR);
            }
            // A roof only over the rock side. Over the line the hall is open to the chasm above,
            // and a course of roof there would be a slab hanging in the void.
            // Not where the chasm has already opened the rock above either: the lap overhead can
            // swing wider than this one, and its void then reaches over the hall. A one-block roof
            // between two voids is a floating slab; leaving it out lets the hall open to the
            // chasm overhead, which it is entitled to.
            if (u > DECK_KEEP + 1.5D && !sink.isAir(x, f + h + 2, z)) {
                sink.set(x, f + h + 1, z, this.palette.rock(x, f + h + 1, z));
            }
        }
    }

    /** The floor itself: scorched near the anchor, fissured with light, plain rock elsewhere. */
    private BlockState floor(int x, int y, int z, int k) {
        double dx = x + 0.5D - this.anchor.x;
        double dz = z + 0.5D - this.anchor.z;
        double fromAnchor = Math.sqrt(dx * dx + dz * dz);
        if (fromAnchor < 3.0D) {
            return PLATE;
        }
        if (fromAnchor < 11.0D) {
            // Where it lies and where it breathes: burnt black, a fair bit of it basalt.
            double n = UndercroftPalette.hash(x, y, z, 403);
            return n < 0.55D ? SCORCH : n < 0.85D ? ASH : this.palette.rock(x, y, z);
        }
        return this.palette.rock(x, y, z);
    }

    /** Fissures, bones, stubs, the buffer stop, and the stalactites. Written last. */
    private void dress(CarveSink sink) {
        // Fissures: three crooked lines from near the line out toward the back wall, light under
        // red glass in them. Walkable, and they put the light under the dragon rather than over it.
        for (int f = 0; f < 3; f++) {
            double a = 0.18D + f * 0.30D + 0.08D * (UndercroftPalette.hash(f, 0, 0, 404) - 0.5D);
            for (double t = 0.12D; t <= 0.92D; t += 0.04D) {
                double wobble = 0.05D * Math.sin(t * 9.0D + f * 2.0D);
                Vec3 p = pointAt(a + wobble, t);
                int x = (int) Math.floor(p.x);
                int z = (int) Math.floor(p.z);
                int floor = floorAt(x, z);
                int k = index(x, z);
                if (floor == Integer.MIN_VALUE || k < 0 || Math.abs(this.lateral[k]) <= DECK_KEEP + 1.0D) {
                    continue;
                }
                sink.set(x, floor - 2, z, HEARTH);
                sink.set(x, floor - 1, z, EMBER);
                sink.set(x, floor, z, GLASS);
            }
        }

        // The stubs it is chained to, each on a plate of its own so it reads as set in, not lost.
        for (BlockPos stub : this.stubs) {
            sink.set(stub.getX(), stub.getY() - 1, stub.getZ(), PLATE);
            sink.set(stub.getX(), stub.getY(), stub.getZ(), STUB);
        }

        // What is left of the last people who came down here. Two small heaps, not a carpet.
        for (int heap = 0; heap < 2; heap++) {
            Vec3 c = pointAt(heap == 0 ? 0.30D : 0.80D, heap == 0 ? 0.80D : 0.70D);
            for (int i = 0; i < 5; i++) {
                int x = (int) Math.floor(c.x + 2.0D * (UndercroftPalette.hash(heap, i, 0, 405) - 0.5D));
                int z = (int) Math.floor(c.z + 2.0D * (UndercroftPalette.hash(heap, i, 1, 405) - 0.5D));
                int floor = floorAt(x, z);
                if (floor == Integer.MIN_VALUE || !contains(x, floor + 1, z)) {
                    continue;
                }
                sink.set(x, floor + 1, z, i == 0 ? Blocks.SKELETON_SKULL.defaultBlockState() : BONE);
            }
        }

        // A buffer stop just past the end of the line: a beam on two posts, so the line visibly
        // ENDS rather than running into the dark.
        Vec3 end = this.track.positionAt(this.sB);
        Vec3 t = this.track.tangentAt(this.sB);
        Vec3 fwd = new Vec3(t.x, 0.0D, t.z).normalize();
        Vec3 side = new Vec3(-fwd.z, 0.0D, fwd.x);
        int deckY = (int) Math.floor(end.y - 2.0D);
        for (int i = -2; i <= 2; i++) {
            Vec3 p = end.add(fwd.scale(1.5D)).add(side.scale(i));
            int x = (int) Math.floor(p.x);
            int z = (int) Math.floor(p.z);
            boolean post = Math.abs(i) == 2;
            sink.set(x, deckY, z, BUFFER_BEAM);
            sink.set(x, deckY + 1, z, post ? BUFFER : BUFFER_BEAM);
            if (post) {
                sink.set(x, deckY + 2, z, BUFFER);
            }
        }

        // Stalactites over the floor, in a few clusters, never a rash of singles.
        for (int c = 0; c < 6; c++) {
            Vec3 at = pointAt(0.1D + 0.8D * UndercroftPalette.hash(c, 0, 0, 406),
                    0.25D + 0.6D * UndercroftPalette.hash(c, 1, 0, 406));
            for (int i = 0; i < 4; i++) {
                int x = (int) Math.floor(at.x + 2.2D * (UndercroftPalette.hash(c, i, 2, 406) - 0.5D));
                int z = (int) Math.floor(at.z + 2.2D * (UndercroftPalette.hash(c, i, 3, 406) - 0.5D));
                int k = index(x, z);
                if (k < 0 || this.floorY[k] <= Integer.MIN_VALUE + 1) {
                    continue;
                }
                int roof = this.floorY[k] + this.height[k];
                int len = 1 + (int) (UndercroftPalette.hash(c, i, 4, 406) * 4.0D);
                for (int d = 0; d < len && roof - d > this.floorY[k] + 4; d++) {
                    sink.set(x, roof - d, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                            .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN)
                            .setValue(BlockStateProperties.DRIPSTONE_THICKNESS,
                                    d == len - 1 ? DripstoneThickness.TIP
                                            : d == 0 ? DripstoneThickness.BASE
                                            : DripstoneThickness.MIDDLE));
                }
            }
        }
    }
}
