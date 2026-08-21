package com.hogwartsmagic.worldgen.undercroft;

import com.hogwartsmagic.worldgen.GringottsSchematic;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * The handful of places inside the imported Gringotts bank the undercroft needs to know about,
 * derived from the schematic itself rather than written down as offsets.
 *
 * <p>All coordinates here are <em>schematic-local</em>: add
 * {@link com.hogwartsmagic.worldgen.VillageGringottsBank}'s paste anchor to get world coordinates.
 * The paste is never rotated, so that is the whole conversion.
 *
 * <p>The bank already contains the room this feature wants. Behind the banking hall, at the east
 * end, is a tall octagonal rotunda: a checkerboard floor ring around a void annulus, with a small
 * island in the middle reached by a short causeway, and nothing at all below it. That is a cart
 * station with the platform already built. What it needs is a hole in the middle.
 *
 * <p>Finding it is deliberately not a heuristic. The island's dead centre is a single
 * {@code dark_prismarine} block, and that block occurs <strong>exactly once in all 149,600 cells of
 * the schematic</strong> - it is the only one in the build. So the search is one pass and one
 * equality test, and a count of anything other than 1 means the schematic has been re-extracted and
 * changed underneath us. In that case this refuses and says so, rather than sinking a lift shaft
 * through somebody's banking hall. Everything else - the room's extent, its ceiling, the gate in the
 * bar screen - is measured outward from that one block.
 */
public final class GringottsLandmarks {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Landmarks");

    /**
     * The one-of-a-kind block at the centre of the rotunda island. Chosen because it is unique, not
     * because it is meaningful - whoever built the bank in 1.12 presumably just liked the colour.
     */
    private static final Block MARKER = Blocks.DARK_PRISMARINE;

    /** How far out from the island we will look before deciding the room is not what we think. */
    private static final int MAX_ROOM_RADIUS = 24;

    /** Sanity bounds on the rotunda's interior, measured: 12x13 across, ~25 tall. */
    private static final int MIN_ROOM_SPAN = 6;
    private static final int MIN_ROOM_HEIGHT = 8;

    /** How far off the centre line to probe, to miss the one open column in the bar screen. */
    private static final int GATE_PROBE_OFFSET = 2;

    /** Above this, the room is wider than the rotunda can be and something has gone wrong. */
    private static final int MAX_ROOM_SPAN = 20;

    /** Centre of the rotunda island, and the block the lift replaces. Measured (92, 2, 15). */
    public final int islandX;
    public final int islandY;
    public final int islandZ;

    /** Interior extent of the rotunda at standing height, exclusive of its walls. */
    public final int minX;
    public final int maxX;
    public final int minZ;
    public final int maxZ;

    /** First solid cell above the island - the underside of the cone roof. Measured ~27. */
    public final int apexY;

    /** The gap in the iron-bar screen the hall walks through, or -1 if it could not be identified. */
    public final int gateX;

    private GringottsLandmarks(int islandX, int islandY, int islandZ,
                               int minX, int maxX, int minZ, int maxZ,
                               int apexY, int gateX) {
        this.islandX = islandX;
        this.islandY = islandY;
        this.islandZ = islandZ;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.apexY = apexY;
        this.gateX = gateX;
    }

    /** Interior headroom over the island, floor to the first solid cell above it. */
    public int headroom() {
        return this.apexY - this.islandY - 1;
    }

    @Override
    public String toString() {
        return String.format(
                "island=(%d,%d,%d) interior=x%d..%d z%d..%d apex=%d headroom=%d gate=x%d",
                this.islandX, this.islandY, this.islandZ,
                this.minX, this.maxX, this.minZ, this.maxZ,
                this.apexY, this.headroom(), this.gateX);
    }

    /**
     * Derives the landmarks, or returns null (having logged why) if the schematic does not look
     * like the bank we measured against.
     */
    @Nullable
    public static GringottsLandmarks of(GringottsSchematic schem) {
        int found = -1;
        int count = 0;
        for (int index = 0; index < schem.volume(); index++) {
            if (schem.blockAt(index).is(MARKER)) {
                count++;
                found = index;
            }
        }
        if (count != 1) {
            LOGGER.error("Gringotts landmark block appears {} times in the schematic, expected"
                    + " exactly 1. The build has changed; refusing to guess where the station goes.",
                    count);
            return null;
        }

        int width = schem.width();
        int length = schem.length();
        int islandX = found % width;
        int islandZ = (found / width) % length;
        int islandY = found / (width * length);

        // Measure at standing height, not floor height: the floor is a ring with a hole in it, so a
        // scan along it would stop at the void annulus rather than at the wall.
        int standY = islandY + 1;

        // ...and measure the X extent two rows OFF the centre line, because the centre line is the
        // one column of the bar screen that is open. Scanning west along z = islandZ walks straight
        // through the gate and keeps going up the banking hall, reporting a room three times its
        // real size. Two rows either side the screen is solid bars, which stops the scan where the
        // room actually ends.
        int probeZ = islandZ - GATE_PROBE_OFFSET;
        if (!schem.isAir(schem.indexOf(islandX, standY, probeZ))) {
            probeZ = islandZ + GATE_PROBE_OFFSET;
        }
        int minX = islandX - openRun(schem, islandX, standY, probeZ, -1, 0);
        int maxX = islandX + openRun(schem, islandX, standY, probeZ, 1, 0);
        // Z is safe to measure on the island's own column: the gate is far to the west, so a
        // north/south scan from here cannot reach it.
        int minZ = islandZ - openRun(schem, islandX, standY, islandZ, 0, -1);
        int maxZ = islandZ + openRun(schem, islandX, standY, islandZ, 0, 1);

        int apexY = islandY + 1;
        while (apexY < schem.height() && schem.isAir(schem.indexOf(islandX, apexY, islandZ))) {
            apexY++;
        }

        GringottsLandmarks landmarks = new GringottsLandmarks(
                islandX, islandY, islandZ, minX, maxX, minZ, maxZ, apexY,
                findGate(schem, islandX, standY, islandZ, minX));

        if (maxX - minX < MIN_ROOM_SPAN || maxZ - minZ < MIN_ROOM_SPAN
                || maxX - minX > MAX_ROOM_SPAN || maxZ - minZ > MAX_ROOM_SPAN
                || landmarks.headroom() < MIN_ROOM_HEIGHT) {
            LOGGER.error("Gringotts landmark found, but the room around it is not the rotunda: {}",
                    landmarks);
            return null;
        }
        LOGGER.info("Gringotts landmarks: {}", landmarks);
        return landmarks;
    }

    /** How many cells of air continue from a start point in one horizontal direction. */
    private static int openRun(GringottsSchematic schem, int x, int y, int z, int dx, int dz) {
        for (int step = 1; step <= MAX_ROOM_RADIUS; step++) {
            int nx = x + dx * step;
            int nz = z + dz * step;
            if (nx < 0 || nz < 0 || nx >= schem.width() || nz >= schem.length()
                    || !schem.isAir(schem.indexOf(nx, y, nz))) {
                return step - 1;
            }
        }
        return MAX_ROOM_RADIUS;
    }

    /**
     * The doorway in the bar screen, on the hall's centre line: walking west from the island there
     * is one column that is open where the two beside it are barred. Returns -1 if no such column
     * exists, which is not fatal - the station only needs the island - but is worth knowing.
     */
    private static int findGate(GringottsSchematic schem, int islandX, int y, int z, int minX) {
        for (int x = minX - 1; x >= Math.max(0, minX - 6); x--) {
            if (!schem.isAir(schem.indexOf(x, y, z))) {
                continue;
            }
            boolean barredNorth = z - 1 >= 0
                    && schem.blockAt(schem.indexOf(x, y, z - 1)).is(Blocks.IRON_BARS);
            boolean barredSouth = z + 1 < schem.length()
                    && schem.blockAt(schem.indexOf(x, y, z + 1)).is(Blocks.IRON_BARS);
            if (barredNorth && barredSouth) {
                return x;
            }
        }
        return -1;
    }
}
