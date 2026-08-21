package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sweeps the corridor's cross-section along the cart line.
 *
 * <p>Three passes, in this order and not any other. The <b>shell</b> writes rock everywhere the
 * corridor's skin goes; the <b>void</b> hollows it out; the <b>furnish</b> pass lays the deck and
 * the parapet last.
 *
 * <p>Shell before void means the lining exists before any air does, so there is never a moment
 * where a carved cavern is open to whatever aquifer happens to be next door, and air always wins
 * where the two overlap - which is what you want at a junction.
 *
 * <p>Furnish last is not tidiness, it is a bug fix. Cross-sections are swept every 0.4 blocks and
 * they overlap heavily, and the frame rotates as it goes, so a cell that is deck for one section is
 * open air for the next one along. Writing the deck inside the void pass let each section punch
 * holes in the one before it: the first audit of this carve found 115 places along the line with no
 * floor under them. Laying the deck in its own pass afterwards means nothing can overwrite it.
 *
 * <p><b>The cross-section changes as you go, and that is the whole point.</b> A tunnel is only
 * boring because it is the same tunnel: a single profile run the length of a 700-block descent is
 * the single most likely way this build fails. So the profile switches between four shapes on a
 * ~26-block cycle, weighted per segment - the plunge is a bored shaft because it is a shaft, the
 * chasm segments are open ledges and trestles because the void is the reason to be there.
 *
 * <p>Which side is open is derived, not chosen. The chasm is centred on the axis the track spirals
 * around, so the void is on the <em>inside</em> of the turn and the rock is outside - and the sign
 * of the bank already knows which way the line is turning.
 */
public final class CorridorCarver {

    public static final int PASS_SHELL = 0;
    public static final int PASS_VOID = 1;
    public static final int PASS_FURNISH = 2;
    public static final int PASSES = 3;

    /** Profiles. Ints rather than an enum, matching the discipline used elsewhere in this feature. */
    public static final int BORE = 0;       // fully enclosed
    public static final int LEDGE = 1;      // open on the void side
    public static final int TRESTLE = 2;    // open both sides, no ceiling at all
    public static final int GALLERY = 3;    // a widening, where something happens

    /**
     * Arc-length step between swept cross-sections. Smaller than a block on purpose: at a full
     * block the sections separate on steep or tightly curved stretches and the corridor comes out
     * with gaps in its wall.
     */
    private static final double STEP = 0.4D;

    /** No profile may run longer than this, or it stops being a change of pace. */
    private static final double PROFILE_CELL = 26.0D;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState PARAPET = Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState();

    /** Where the Downfall hangs, as a fraction of the line, and how thick the curtain is. */
    private static final double DOWNFALL_AT = 0.605D;
    private static final double DOWNFALL_THICK = 2.5D;

    /** Which profiles each segment is allowed to use, in the order the line meets them. */
    private static final int[][] SEGMENT_PROFILES = {
            {GALLERY},                        // station apron
            {BORE, BORE, BORE, LEDGE},        // the plunge - a shaft, mostly enclosed
            {GALLERY, GALLERY, BORE},         // loading gallery
            {LEDGE, BORE, LEDGE, GALLERY},    // stalactite bend
            {BORE, LEDGE},                    // stalactite S
            {LEDGE, TRESTLE, LEDGE},          // high chasm ledge - the void is the point
            {BORE, BORE, LEDGE},              // downfall hairpin
            {LEDGE, LEDGE, TRESTLE, GALLERY}, // dragon lap
            {TRESTLE, LEDGE, TRESTLE},        // low chasm ledge
            {BORE, LEDGE, BORE},              // deep approach
    };

    private final CartTrack track;
    private final UndercroftPalette palette;
    private final int steps;

    public CorridorCarver(CartTrack track, UndercroftPalette palette) {
        this.track = track;
        this.palette = palette;
        this.steps = (int) Math.ceil(track.length() / STEP) + 1;
    }

    public int stepCount() {
        return this.steps;
    }

    /** Rough number of block writes a step costs, for budgeting ticks. */
    public static int costPerStep() {
        return 90;
    }

    public int profileAt(double s) {
        int segment = segmentAt(s);
        int[] allowed = SEGMENT_PROFILES[segment];
        int cell = (int) (s / PROFILE_CELL);
        int pick = (int) (UndercroftPalette.hash(cell, segment, 0, 91) * allowed.length);
        return allowed[Math.min(pick, allowed.length - 1)];
    }

    private int segmentAt(double s) {
        for (int i = CartTrack.segmentCount() - 1; i >= 0; i--) {
            if (s >= this.track.segmentStartLength(i)) {
                return i;
            }
        }
        return 0;
    }

    // --- the sweep ------------------------------------------------------------------------

    public void carveStep(CarveSink sink, int pass, int step) {
        double s = Math.min(step * STEP, this.track.length());
        Vec3 centre = this.track.positionAt(s);
        Vec3 up = this.track.upAt(s);
        Vec3 right = this.track.rightAt(s);
        Vec3 forward = this.track.tangentAt(s);

        int profile = profileAt(s);
        int openSide = this.track.bankAt(s) < 0.0F ? -1 : 1;
        double halfW = halfWidth(profile) + 0.5D;
        double ceiling = ceilingHeight(profile) + 0.5D;

        // Iterate BLOCKS in a bounding box and test each one against the section, rather than
        // stepping integer offsets along `right` and `up` and rounding.
        //
        // Stepping the offsets seems natural and is wrong: once the frame tilts - which it does
        // the moment the line pitches down or banks - the integer lattice in section space no
        // longer lands on every block it passes through, and the carve comes out with pinholes
        // through its floor and walls. The first audit of this sweep found 32 places with rock in
        // the cart's path and three with no floor, all of them on the steepest, most banked
        // stretches. Testing every block in the box cannot miss one.
        double reach = halfW + 2.0D;
        double lift = ceiling + 2.0D;
        double spanX = Math.abs(right.x) * reach + Math.abs(up.x) * lift + Math.abs(forward.x) * SLAB;
        double spanY = Math.abs(right.y) * reach + Math.abs(up.y) * lift + Math.abs(forward.y) * SLAB;
        double spanZ = Math.abs(right.z) * reach + Math.abs(up.z) * lift + Math.abs(forward.z) * SLAB;

        int x0 = (int) Math.floor(centre.x - spanX);
        int x1 = (int) Math.floor(centre.x + spanX);
        int y0 = (int) Math.floor(centre.y - spanY);
        int y1 = (int) Math.floor(centre.y + spanY);
        int z0 = (int) Math.floor(centre.z - spanZ);
        int z1 = (int) Math.floor(centre.z + spanZ);

        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    double dx = bx + 0.5D - centre.x;
                    double dy = by + 0.5D - centre.y;
                    double dz = bz + 0.5D - centre.z;

                    // Only the slab this section is responsible for. Consecutive sections are
                    // STEP apart, so a half-step either side tiles the line with no seams.
                    double w = dx * forward.x + dy * forward.y + dz * forward.z;
                    if (Math.abs(w) > SLAB) {
                        continue;
                    }
                    double u = dx * right.x + dy * right.y + dz * right.z;
                    double v = dx * up.x + dy * up.y + dz * up.z;

                    boolean inside = inVoid(profile, u, v, halfW, ceiling);
                    boolean furnishZone = Math.abs(u) <= halfW
                            && v >= FLOOR - SUBGRADE - 0.5D && v <= ceiling;
                    boolean shell = !inside && inShell(profile, u, v, halfW, ceiling, openSide);
                    if (!inside && !shell && !(pass == PASS_FURNISH && furnishZone)) {
                        continue;
                    }

                    if (pass == PASS_SHELL) {
                        if (shell) {
                            sink.set(bx, by, bz, this.palette.rock(bx, by, bz));
                        }
                    } else if (pass == PASS_VOID) {
                        if (inside) {
                            sink.set(bx, by, bz, AIR);
                        }
                    } else if (furnishZone) {
                        BlockState furniture =
                                furnish(profile, u, v, halfW, openSide, bx, by, bz, s);
                        if (furniture != null) {
                            sink.set(bx, by, bz, furniture);
                        }
                    }
                }
            }
        }
    }

    /** Half-thickness of the slab each swept section owns, in blocks. */
    private static final double SLAB = 0.55D;

    /**
     * Extra courses of deck laid under the running surface.
     *
     * <p>One block is not enough. On a 1:3 descent the deck blocks of consecutive sections step
     * down diagonally, and a single course leaves diagonal slits you can see the void through and
     * fall between. Two courses overlap into a continuous ramp. The audit found 48 unsupported
     * samples on exactly the steep stretches before this went in.
     */
    private static final int SUBGRADE = 1;

    /** The deck sits two below the line, so the rider's eye is roughly on it. */
    private static final int FLOOR = -2;

    private static int halfWidth(int profile) {
        if (profile == GALLERY) {
            return 5;
        }
        if (profile == TRESTLE) {
            return 2;
        }
        return 3;
    }

    private static int ceilingHeight(int profile) {
        if (profile == GALLERY) {
            return 6;
        }
        if (profile == TRESTLE) {
            return 3;      // nominal only; a trestle has no ceiling written
        }
        return 3;
    }

    /** The cells that become open space. */
    private static boolean inVoid(int profile, double u, double v, double halfW, double ceiling) {
        // A trestle clears the same envelope as anything else. It is *drawn* as a bridge -- no
        // walls, no ceiling -- but the rock it is drawn through still has to come out, or the
        // rider is inside solid stone. Once the chasm is carved around it, the surrounding rock is
        // gone anyway and it reads as the bridge it is meant to be.
        return Math.abs(u) <= halfW && v >= FLOOR - 0.5D && v <= ceiling;
    }

    /** The cells that become rock. */
    private static boolean inShell(int profile, double u, double v, double halfW, double ceiling,
                                   int openSide) {
        if (profile == TRESTLE) {
            // Nothing but what holds the deck up. The rest is deliberately left open, which is
            // what makes a trestle read as a bridge in a void rather than as another corridor.
            return v < FLOOR - 0.5D && v >= FLOOR - 1.5D && Math.abs(u) <= halfW;
        }
        if (Math.abs(u) > halfW + 1.0D || v < FLOOR - 1.5D || v > ceiling + 1.0D) {
            return false;
        }
        if (profile == LEDGE) {
            // Open on the void side: no wall past the edge, and no roof over the outer half.
            if (u * openSide > halfW) {
                return false;
            }
            if (v > ceiling && u * openSide > 0.0D) {
                return false;
            }
        }
        return true;
    }

    /** Deck and parapet, or null for "leave it as air". Runs after everything else. */
    private BlockState furnish(int profile, double u, double v, double halfW, int openSide,
                               int x, int y, int z, double s) {
        if (v <= FLOOR + 0.5D && v >= FLOOR - SUBGRADE - 0.5D) {
            return UndercroftPalette.DECK;
        }
        // The Thief's Downfall hangs right across the bore, so there is no way past it but through.
        // Above the deck only, or it would be laid into the trackbed.
        double downfallAt = this.track.length() * DOWNFALL_AT;
        if (s >= downfallAt && s <= downfallAt + DOWNFALL_THICK && v > FLOOR + 0.5D) {
            return com.hogwartsmagic.ExampleMod.THIEFS_DOWNFALL.get().defaultBlockState();
        }
        // A one-block parapet at the open edge, and only one block: two reads as a rampart and
        // hides the drop, which is the entire reason for being out on a ledge.
        boolean openEdge = (profile == LEDGE && u * openSide >= halfW - 1.0D)
                || (profile == TRESTLE && Math.abs(u) >= halfW - 1.0D);
        if (openEdge && Math.abs(v - (FLOOR + 1)) <= 0.5D) {
            // Gapped, not continuous: an unbroken kerb the whole way down reads as a handrail on
            // a ramp. Broken, it reads as something that has been there a long time.
            return UndercroftPalette.hash(x, y, z, 33) < 0.62D ? PARAPET : null;
        }
        return null;
    }
}
