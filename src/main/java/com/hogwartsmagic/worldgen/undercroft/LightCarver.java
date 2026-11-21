package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Hangs the lights, following the reference's own lighting table rather than a spacing rule.
 *
 * <p>Darkness is the default and light is an event:
 * <ul>
 *   <li><b>The transition band</b>, the first stretch below the bank: warm lanterns at fairly
 *       regular intervals, the bank's own habit still running down the line. Here and nowhere
 *       below.</li>
 *   <li><b>Below it</b>: warm <b>clusters</b> - two or three lanterns on chains within a few
 *       blocks of each other, somebody works here - with long unlit runs between them. A single
 *       lamp every twenty blocks is fence posts; a pool of light every forty is a place.</li>
 *   <li><b>The Thief's Downfall</b>: the only cold light in the build, so it can be seen from far
 *       above and read as wrong. This pass used to hang soul lanterns the whole way down below the
 *       transition, which spent the one colour the Downfall needed on everything.</li>
 *   <li><b>The dragon's hall</b>: nothing hangs in there at all. It is lit red from the floor
 *       ({@link CavernCarver}).</li>
 *   <li><b>The chasm floor</b>: nothing, by construction. Every fitting hangs off the corridor.</li>
 * </ul>
 *
 * <p>Every fitting has something to hang from or stand on. Wall brackets used to be set one block
 * in from the wall, so the bracket floated and its lantern hung off a block hanging off nothing.
 */
public final class LightCarver {

    /** Below this many blocks under the station the transition band ends. */
    private static final int TRANSITION_BELOW = 30;

    /** Gaps in the transition band: regular, nearly. */
    private static final double BAND_GAP = 10.0D;
    private static final double BAND_JITTER = 4.0D;

    /** Gaps between clusters below it: long, and never the same twice. */
    private static final double CLUSTER_GAP_MIN = 32.0D;
    private static final double CLUSTER_GAP_RANGE = 30.0D;

    /** How far either side of the Downfall the cold light reaches. */
    private static final double DOWNFALL_REACH = 7.0D;

    private static final BlockState HANGING = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);
    private static final BlockState STANDING = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.FALSE);
    private static final BlockState SOUL_HANGING = Blocks.SOUL_LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);
    private static final BlockState SOUL_STANDING = Blocks.SOUL_LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.FALSE);
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    private static final BlockState POST = Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState();
    private static final BlockState BRACKET = Blocks.CHISELED_DEEPSLATE.defaultBlockState();

    /** One fitting: where on the line, and which kind. */
    private record Fitting(double s, int kind, int index) {
    }

    private static final int SINGLE = 0;
    private static final int CLUSTER = 1;
    private static final int COLD = 2;

    private final CartTrack track;
    private final List<Fitting> at = new ArrayList<>();

    public LightCarver(CartTrack track, UndercroftPalette palette, int stationY,
                       @Nullable CavernCarver cavern) {
        this.track = track;
        double downfall = track.length() * CorridorCarver.DOWNFALL_AT;

        // Worked out up front rather than as the pass runs: a gap depends on the one before it, and
        // the carve is resumed from a cursor after a restart, so the sequence has to be a list that
        // any step can be indexed into.
        double s = 6.0D;
        int i = 0;
        while (s < track.length() - 4.0D) {
            boolean inBand = stationY - track.positionAt(s).y < TRANSITION_BELOW;
            boolean nearDownfall = Math.abs(s - downfall) < DOWNFALL_REACH + 6.0D;
            boolean inHall = cavern != null && cavern.covers(s);
            if (!inHall && !nearDownfall) {
                this.at.add(new Fitting(s, inBand ? SINGLE : CLUSTER, i));
            }
            s += inBand
                    ? BAND_GAP + BAND_JITTER * (UndercroftPalette.hash(i, 0, 0, 311) - 0.5D)
                    : CLUSTER_GAP_MIN + CLUSTER_GAP_RANGE * UndercroftPalette.hash(i, 0, 0, 312);
            i++;
        }
        // The cold pair either side of the Downfall, so the curtain is lit from both approaches.
        this.at.add(new Fitting(downfall - DOWNFALL_REACH, COLD, i++));
        this.at.add(new Fitting(downfall + CorridorCarver.DOWNFALL_THICK + DOWNFALL_REACH, COLD, i));
    }

    public int stepCount() {
        return this.at.size();
    }

    /** Rough number of block writes a step costs, for budgeting ticks. */
    public static int costPerStep() {
        return 12;
    }

    public void carveStep(CarveSink sink, CorridorCarver corridor, int step) {
        Fitting fitting = this.at.get(step);
        if (fitting.kind() == CLUSTER) {
            // Two or three lamps within a few blocks, on chains of different lengths.
            int count = 2 + (UndercroftPalette.hash(fitting.index(), 2, 0, 314) < 0.4D ? 1 : 0);
            for (int k = 0; k < count; k++) {
                double offset = k * (2.0D + 1.5D * UndercroftPalette.hash(fitting.index(), k, 0, 315));
                int drop = (int) (UndercroftPalette.hash(fitting.index(), k, 1, 315) * 2.0D);
                fit(sink, corridor, fitting.s() + offset, fitting.index() + k, drop, false);
            }
            return;
        }
        fit(sink, corridor, fitting.s(), fitting.index(), 0, fitting.kind() == COLD);
    }

    /** One lamp, on whatever the profile at {@code s} offers to hang it from. */
    private void fit(CarveSink sink, CorridorCarver corridor, double s, int index, int drop,
                     boolean cold) {
        Vec3 centre = this.track.positionAt(s);
        Vec3 up = this.track.upAt(s);
        Vec3 right = this.track.rightAt(s);

        int profile = corridor.profileAt(s);
        int openSide = this.track.voidSideAt(s);
        int halfW = CorridorCarver.halfWidth(profile);
        int ceiling = CorridorCarver.ceilingHeight(profile);
        BlockState hanging = cold ? SOUL_HANGING : HANGING;
        BlockState standing = cold ? SOUL_STANDING : STANDING;

        if (profile == CorridorCarver.TRESTLE) {
            // No ceiling to hang anything from, so the trestle gets a lamp post on its edge, which
            // is the right answer anyway: a bridge over a void wants its rail lit, not its roof.
            int side = (index % 2 == 0) ? 1 : -1;
            put(sink, centre, right, up, side * halfW, CorridorCarver.FLOOR + 1, POST);
            put(sink, centre, right, up, side * halfW, CorridorCarver.FLOOR + 2, standing);
            return;
        }

        // Rock side for a ledge - a lantern hung over the drop has nothing to hang from - and
        // alternating sides for anything enclosed, so the light does not run down one wall.
        int side = profile == CorridorCarver.LEDGE ? -openSide : ((index % 2 == 0) ? 1 : -1);

        boolean low = UndercroftPalette.hash(index, 1, 0, 313) < 0.35D;
        if (low) {
            // A bracket set against the wall (the last course of the bore before the lining), with
            // the lamp hung under it - but only where the wall really is there beside it.
            int u = side * halfW;
            if (solid(sink, centre, right, up, u + side, CorridorCarver.FLOOR + 3)) {
                put(sink, centre, right, up, u, CorridorCarver.FLOOR + 3, BRACKET);
                put(sink, centre, right, up, u, CorridorCarver.FLOOR + 2, hanging);
                return;
            }
        }
        // From the roof, a little in from the wall, on a chain that varies the height. The banked
        // frame does not always put the roof directly over the cell below it, so look up for it:
        // chain up to the first solid block, and hang nothing if there is none within reach.
        int u = side * (halfW - 1);
        Vec3 lamp = centre.add(right.scale(u)).add(up.scale(ceiling - drop));
        int lx = (int) Math.floor(lamp.x);
        int ly = (int) Math.floor(lamp.y);
        int lz = (int) Math.floor(lamp.z);
        int top = ly + 1;
        while (top <= ly + drop + 3 && sink.isAir(lx, top, lz)) {
            top++;
        }
        if (top > ly + drop + 3) {
            return;
        }
        for (int y = ly + 1; y < top; y++) {
            sink.set(lx, y, lz, CHAIN);
        }
        sink.set(lx, ly, lz, hanging);
    }

    private boolean solid(CarveSink sink, Vec3 centre, Vec3 right, Vec3 up, double u, double v) {
        Vec3 p = centre.add(right.scale(u)).add(up.scale(v));
        return !sink.isAir((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
    }

    private void put(CarveSink sink, Vec3 centre, Vec3 right, Vec3 up, double u, double v,
                     BlockState state) {
        Vec3 p = centre.add(right.scale(u)).add(up.scale(v));
        sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), state);
    }
}
