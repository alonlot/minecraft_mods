package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Hangs the lights, and it is the pass this build most needed.
 *
 * <p>Before it existed the whole descent contained <b>fourteen</b> light sources, all of them the
 * pair inside each vault, and the first look at it in game came back as "flat grey" - which it was,
 * because the only light in seven hundred blocks of tunnel was whatever the player brought with
 * them. Grey rock lit by nothing is grey rock.
 *
 * <h2>Rhythm, not spacing</h2>
 *
 * <p>Gaps come from a noise sequence over a <b>3x range</b> - nine blocks to twenty-seven - and
 * the side, the height and the fitting all vary with it. Evenly spaced lanterns at a constant
 * height read as fence posts, and fence posts are the thing that makes a long corridor feel like
 * one corridor. Uneven ones give you pools of light with dark between them, which is a rhythm you
 * move <em>through</em>.
 *
 * <h2>The light is where the colour is</h2>
 *
 * <p>The rock is banded by depth but every band of it is grey, so the bands barely read. The lamps
 * are what carry the change: warm iron lanterns through the bank's own stonework at the top, and
 * <b>soul lanterns</b> once you are past it, whose cold blue-green is both the film's palette down
 * there and the single cheapest way to make grey stone look like a different place. Crossing the
 * line where the light changes colour is the moment you have left the bank and entered the caves.
 *
 * <h2>Nothing below the last ledge</h2>
 *
 * <p>Every fitting here hangs off the corridor, and the corridor stops where the track does, so the
 * bottom of the chasm gets nothing by construction. That is deliberate and load-bearing: the floor
 * is only invisible because there is no light anywhere near it, and one lantern down there would
 * measure the drop for the player in a single glance.
 */
public final class LightCarver {

    /** Shortest and longest gap between fittings, in blocks along the line. */
    private static final double GAP_MIN = 9.0D;
    private static final double GAP_RANGE = 18.0D;

    /** Below this many blocks under the station the light turns cold. */
    private static final int SOUL_BELOW = 34;

    private static final BlockState HANGING = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);
    private static final BlockState STANDING = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.FALSE);
    private static final BlockState SOUL_HANGING = Blocks.SOUL_LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);
    private static final BlockState SOUL_STANDING = Blocks.SOUL_LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.FALSE);
    private static final BlockState POST = Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState();
    private static final BlockState BRACKET = Blocks.CHISELED_DEEPSLATE.defaultBlockState();

    private final CartTrack track;
    private final int stationY;
    private final List<Double> at = new ArrayList<>();

    public LightCarver(CartTrack track, UndercroftPalette palette, int stationY) {
        this.track = track;
        this.stationY = stationY;
        // Worked out up front rather than as the pass runs: a gap depends on the one before it, and
        // the carve is resumed from a cursor after a restart, so the sequence has to be a list that
        // any step can be indexed into rather than a running total somebody has to remember.
        double s = 6.0D;
        int i = 0;
        while (s < track.length() - 4.0D) {
            this.at.add(s);
            s += GAP_MIN + GAP_RANGE * UndercroftPalette.hash(i, 0, 0, 311);
            i++;
        }
    }

    public int stepCount() {
        return this.at.size();
    }

    /** Rough number of block writes a step costs, for budgeting ticks. */
    public static int costPerStep() {
        return 6;
    }

    public void carveStep(CarveSink sink, CorridorCarver corridor, int step) {
        double s = this.at.get(step);
        Vec3 centre = this.track.positionAt(s);
        Vec3 up = this.track.upAt(s);
        Vec3 right = this.track.rightAt(s);

        int profile = corridor.profileAt(s);
        int openSide = this.track.bankAt(s) < 0.0F ? -1 : 1;
        int halfW = CorridorCarver.halfWidth(profile);
        int ceiling = CorridorCarver.ceilingHeight(profile);

        boolean cold = this.stationY - (int) Math.round(centre.y) > SOUL_BELOW;

        if (profile == CorridorCarver.TRESTLE) {
            // No ceiling to hang anything from, so the trestle gets lamp posts on its edge, which
            // is the right answer anyway: a bridge over a void wants its rail lit, not its roof.
            int side = (step % 2 == 0) ? 1 : -1;
            put(sink, centre, right, up, side * halfW, CorridorCarver.FLOOR + 1, POST);
            put(sink, centre, right, up, side * halfW, CorridorCarver.FLOOR + 2,
                    cold ? SOUL_STANDING : STANDING);
            return;
        }

        // Rock side for a ledge - a lantern hung over the drop has nothing to hang from - and
        // alternating sides for anything enclosed, so the light does not run down one wall.
        int u = profile == CorridorCarver.LEDGE
                ? -openSide * (halfW - 1)
                : ((step % 2 == 0) ? 1 : -1) * (halfW - 1);

        // Height varies with the same noise as the gaps. A row of lanterns all at ceiling height
        // is the fence-post failure one axis over.
        boolean low = UndercroftPalette.hash(step, 1, 0, 313) < 0.35D;
        if (low) {
            put(sink, centre, right, up, u, CorridorCarver.FLOOR + 3, BRACKET);
            put(sink, centre, right, up, u, CorridorCarver.FLOOR + 2,
                    cold ? SOUL_HANGING : HANGING);
        } else {
            put(sink, centre, right, up, u, ceiling, cold ? SOUL_HANGING : HANGING);
        }

        // A gallery is a widening where something happens, so it gets the pair rather than the one.
        if (profile == CorridorCarver.GALLERY) {
            put(sink, centre, right, up, -u, ceiling, cold ? SOUL_HANGING : HANGING);
        }
    }

    private void put(CarveSink sink, Vec3 centre, Vec3 right, Vec3 up, double u, double v,
                     BlockState state) {
        Vec3 p = centre.add(right.scale(u)).add(up.scale(v));
        sink.set((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z), state);
    }
}
