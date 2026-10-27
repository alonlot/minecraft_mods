package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinEntity;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.worldgen.undercroft.GringottsLandmarks;
import com.hogwartsmagic.worldgen.undercroft.UndercroftBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raises the imported Gringotts bank on the outskirts of some villages.
 *
 * <p>The building comes from {@link GringottsSchematic} (100x44x34, lifted from
 * a 1.12 world), so it is far too big to slot in among the houses. It is placed
 * a little way out from the village bell instead, facing back toward it.
 *
 * <p>Built exactly like {@link MiniHogwartsManager}: placement runs outside
 * worldgen entirely, in two phases spread across server ticks - solids first
 * (air cells are skipped so the surrounding terrain is never carved into a box),
 * then a support skirt under each column so the bank never floats.
 *
 * <p>Village detection deliberately uses {@link ServerLevel#isVillage} and the
 * meeting-point POI rather than {@code StructureManager.getStructureWithPieceAt}.
 * The structure lookup force-loads and generates chunks synchronously, which
 * froze the server when a player teleported into fresh terrain.
 */
public final class VillageGringottsBank {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-VillageBank");

    private VillageGringottsBank() {
    }

    /** Roughly one village in three gets a branch. */
    private static final int BANK_CHANCE_DENOMINATOR = 3;

    /** How often a given player re-checks for an unbanked village, in ticks. */
    private static final int CHECK_INTERVAL = 100;

    /** How far from the player to look for the village bell. */
    private static final int BELL_SEARCH_RADIUS = 48;

    /**
     * Distance from the bell to the centre of the bank. The building is 100
     * blocks wide, so this keeps it clear of the houses while staying close
     * enough to read as part of the settlement.
     */
    private static final int OFFSET_FROM_BELL = 72;

    /** Schematic cells examined per server tick while the bank goes up. */
    private static final int CELLS_PER_TICK = 8000;

    /** Skirt columns filled per server tick during the support phase. */
    private static final int SKIRT_COLUMNS_PER_TICK = 400;

    /** How far a support skirt will reach down before giving up. */
    private static final int MAX_SKIRT_DEPTH = 48;

    /**
     * Highest schematic Y a column's lowest solid block may sit at and still be
     * treated as that column's floor.
     *
     * <p>{@code lowestSolidY} is just the lowest non-air cell in the column, with
     * no notion of whether it is something to stand on or something overhead. For
     * a column that is open all the way up - the rotunda's ring void, or anything
     * under a roof overhang or a spire - the lowest solid is the *ceiling*, and
     * skirting from just beneath it fills the room itself with stone brick rather
     * than propping up a floor. 46 columns in the rotunda alone have their lowest
     * solid at y=26-27 (the quartz cone), which buried it under ~1,170 blocks;
     * 354 columns across the whole build are above this limit.
     *
     * <p>The building's real floor is at y=0-2 everywhere it matters, so anything
     * this high up is an overhang and wants no skirt at all.
     */
    private static final int SKIRT_LOWEST_Y_LIMIT = 8;

    /** How far up the rotunda screen runs. The bars stop well below the cone roof. */
    private static final int GATE_HEIGHT = 12;
    /** A doorway is about twenty cells; the cap only exists so a leak cannot fill the room. */
    private static final int GATE_DOORWAY_CAP = 120;

    /** Sink the base slightly so the ground floor meets the terrain. */
    private static final int FOUNDATION_SINK = 1;

    /** Largest tolerated height difference across the sampled footprint. */
    private static final int MAX_GROUND_SPREAD = 10;

    private static final int SETBLOCK_FLAGS = Block.UPDATE_CLIENTS;
    private static final BlockState SKIRT = Blocks.STONE_BRICKS.defaultBlockState();

    @Nullable
    private static PlacementJob activeJob;

    public static boolean isBusy() {
        return activeJob != null;
    }

    /** Drops any in-progress job. Called when the server stops, so a job never outlives its level. */
    public static void reset() {
        activeJob = null;
    }

    // ------------------------------------------------------------------ ticking

    /** Called every server tick (END phase) to advance an in-progress build. */
    public static void tick(MinecraftServer server) {
        PlacementJob job = activeJob;
        if (job == null) {
            return;
        }
        try {
            if (job.advance()) {
                job.staff();
                VillageBankTracker tracker = VillageBankTracker.get(job.level);
                if (job.villageCenter != null) {
                    tracker.markVisited(job.villageCenter);
                }
                tracker.addBank(job.locator());
                announce(job.level, job.locator());
                LOGGER.info("Gringotts bank complete at {}", job.locator());
                ServerLevel finished = job.level;
                BlockPos anchor = job.anchor;
                activeJob = null;
                // Straight on to the undercroft, while this is the best moment it will ever get:
                // the bank's chunks are provably resident (we just wrote 21,000 blocks into them)
                // and the player who triggered the build is still within 72 blocks. If the wider
                // footprint is not loaded yet the request records itself as pending and gets
                // picked up the next time somebody walks past.
                lockGate(finished, anchor);
                startUndercroft(finished, anchor);
            }
        } catch (Exception e) {
            LOGGER.error("Gringotts placement failed; abandoning job", e);
            activeJob = null;
        }
    }

    /**
     * Opens up whatever is under a branch that has just gone up.
     *
     * <p>A Gringotts with nothing beneath it is the thing this whole feature exists to stop, so
     * every branch gets one - or gets a recorded refusal, if the terrain there cannot give the
     * descent enough room to work.
     */
    /**
     * Turns the rotunda screen into a gate, and fills the doorway in it.
     *
     * <p>The schematic draws the screen as ordinary iron bars with a gap on the hall centre line,
     * and a gap is not a gate. Two passes: swap every bar of the rear screen for the goblin-forged
     * block, then flood the doorway void with the same thing so there is something to open.
     *
     * <p>Both are found rather than measured. The screen is whatever iron bars stand within the
     * rotunda box, and the doorway is whatever air is reachable from the gate line inside it - so a
     * bank placed at any elevation, and a schematic somebody re-exports with the doorway one block
     * wider, both come out right. A hard-coded rectangle would come out sideways in a wall.
     */
    static void lockGate(ServerLevel level, BlockPos anchor) {
        lockGate(level, anchor, Rotation.NONE);
    }

    /**
     * Package-private so GringottsFurnishing can run it for the Diagon Alley copy of the bank too,
     * which may have been turned to face down whichever way the street runs.
     *
     * <p>Every schematic-local offset goes through {@link GringottsFurnishing#at} so it lands where
     * the turned building actually is. The doorway flood is confined to the plane of the screen, and
     * that plane turns too: the screen is one block thick along the schematic X axis, so its
     * sideways direction is the schematic Z axis as turned.
     */
    static void lockGate(ServerLevel level, BlockPos anchor, Rotation rotation) {
        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            return;
        }
        GringottsLandmarks marks = GringottsLandmarks.of(schem);
        if (marks == null || marks.gateX < 0) {
            return;
        }
        BlockState gate = ExampleMod.GRINGOTTS_GATE.get().defaultBlockState();
        int floorY = anchor.getY() + marks.islandY;

        int swapped = 0;
        // Two opposite corners of the search box; betweenClosed sorts out which is the low one, so
        // turning them is all the rotation this needs.
        for (BlockPos pos : BlockPos.betweenClosed(
                GringottsFurnishing.at(anchor, rotation, marks.gateX - 2, marks.islandY - 1,
                        marks.minZ - 3),
                GringottsFurnishing.at(anchor, rotation, marks.maxX + 2, marks.islandY + GATE_HEIGHT,
                        marks.maxZ + 3))) {
            if (level.getBlockState(pos).is(Blocks.IRON_BARS)) {
                level.setBlock(pos.immutable(), gate, Block.UPDATE_CLIENTS);
                swapped++;
            }
        }

        int[] sideways = GringottsFurnishing.turn(0, 1, rotation);
        int filled = fillDoorway(level,
                GringottsFurnishing.at(anchor, rotation, marks.gateX, marks.islandY + 1,
                        marks.islandZ),
                gate, floorY, sideways[0], sideways[1]);
        LOGGER.info("Gringotts gate locked: {} bars replaced, {} cells of doorway filled",
                swapped, filled);
    }

    /**
     * Floods the doorway void with gate, staying inside the plane of the screen.
     *
     * <p>Confined to one column of the schematic on purpose: the screen is one block thick, and a
     * flood that can step across it would pour out of the doorway and fill the rotunda behind it.
     * {@code sideX}/{@code sideZ} is the world direction of the screen sideways (schematic Z, turned
     * with the building); the flood may step along that, up and down, and nowhere else.
     */
    private static int fillDoorway(ServerLevel level, BlockPos seed, BlockState gate, int floorY,
                                    int sideX, int sideZ) {
        if (!level.getBlockState(seed).isAir()) {
            return 0;
        }
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(seed);
        seen.add(seed);
        int filled = 0;
        while (!queue.isEmpty() && filled < GATE_DOORWAY_CAP) {
            BlockPos pos = queue.poll();
            level.setBlock(pos, gate, Block.UPDATE_CLIENTS);
            filled++;
            for (BlockPos next : new BlockPos[]{pos.above(), pos.below(),
                    pos.offset(sideX, 0, sideZ), pos.offset(-sideX, 0, -sideZ)}) {
                if (next.getY() < floorY || next.getY() > floorY + GATE_HEIGHT
                        || !seen.add(next.immutable()) || !level.getBlockState(next).isAir()) {
                    continue;
                }
                queue.add(next.immutable());
            }
        }
        return filled;
    }

    private static void startUndercroft(ServerLevel level, BlockPos anchor) {
        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            return;
        }
        GringottsLandmarks marks = GringottsLandmarks.of(schem);
        if (marks == null) {
            LOGGER.warn("Bank built, but its landmarks could not be read; no undercroft dug");
            return;
        }
        BlockPos station = anchor.offset(marks.islandX, marks.islandY, marks.islandZ);
        UndercroftBuilder.request(level, station);
    }

    /** Throttled per-player check; the village test reads POI data only. */
    public static void maybeBuild(ServerPlayer player) {
        if (activeJob != null || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        try {
            attemptBuild(player);
        } catch (Exception e) {
            LOGGER.error("Gringotts site search failed", e);
        }
    }

    private static void attemptBuild(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        if (!level.isVillage(playerPos)) {
            return;
        }

        BlockPos bell = findVillageCenter(level, playerPos);
        if (bell == null) {
            return;
        }

        VillageBankTracker tracker = VillageBankTracker.get(level);
        if (tracker.hasVisited(bell)) {
            return;
        }

        if (!villageGetsBank(level, bell)) {
            tracker.markVisited(bell);
            return;
        }

        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            // Missing or corrupt resource - do not burn the village's one chance.
            return;
        }

        BlockPos anchor = findAnchor(level, bell, schem);
        if (anchor == null) {
            // Usually terrain that is too broken, or chunks not resident yet.
            // Deliberately not marked visited, so it retries.
            return;
        }

        RandomSource rng = RandomSource.create(level.getSeed() ^ bell.asLong());
        activeJob = new PlacementJob(level, schem, anchor, bell, rng);
        LOGGER.info("Gringotts bank starting at {} for village bell {}", anchor, bell);
    }

    /** The village bell is the village's identity; reads resident POI data only. */
    @Nullable
    private static BlockPos findVillageCenter(ServerLevel level, BlockPos near) {
        Optional<BlockPos> bell = level.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.MEETING), near, BELL_SEARCH_RADIUS,
                PoiManager.Occupancy.ANY);
        return bell.orElse(null);
    }

    /** Deterministic per village and per world. */
    private static boolean villageGetsBank(ServerLevel level, BlockPos bell) {
        RandomSource rng = RandomSource.create(level.getSeed() ^ (bell.asLong() * 0x9E3779B97F4A7C15L));
        return rng.nextInt(BANK_CHANCE_DENOMINATOR) == 0;
    }

    // -------------------------------------------------------------- site search

    /**
     * Tries eight compass points around the bell and takes the first where the
     * ground under the footprint is not wildly uneven and is not water. Returns
     * the min-corner the schematic should be pasted from.
     */
    @Nullable
    private static BlockPos findAnchor(ServerLevel level, BlockPos bell, GringottsSchematic schem) {
        for (int step = 0; step < 8; step++) {
            double angle = step * Math.PI / 4.0D;
            int cx = bell.getX() + (int) Math.round(Math.cos(angle) * OFFSET_FROM_BELL);
            int cz = bell.getZ() + (int) Math.round(Math.sin(angle) * OFFSET_FROM_BELL);
            BlockPos anchor = anchorAt(level, cx, cz, schem, true);
            if (anchor != null) {
                return anchor;
            }
        }
        return null;
    }

    /**
     * Works out the paste corner for a bank centred on (cx, cz).
     *
     * <p>{@code picky} controls whether uneven or waterlogged ground disqualifies
     * the spot. Automatic village placement is picky because it has seven other
     * compass points to try; an operator running the command has asked for it
     * <em>here</em>, so only the chunk-residency check is enforced.
     */
    @Nullable
    private static BlockPos anchorAt(ServerLevel level, int cx, int cz, GringottsSchematic schem, boolean picky) {
        int w = schem.width();
        int l = schem.length();
        int minX = cx - w / 2;
        int minZ = cz - l / 2;

        // Every corner must already be resident; we never want to trigger
        // synchronous chunk generation from a tick handler.
        if (!resident(level, minX, minZ) || !resident(level, minX + w, minZ)
                || !resident(level, minX, minZ + l) || !resident(level, minX + w, minZ + l)
                || !resident(level, cx, cz)) {
            return null;
        }

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        boolean wet = false;
        for (int sx = 0; sx <= w; sx += w / 4) {
            for (int sz = 0; sz <= l; sz += l / 4) {
                int x = minX + sx;
                int z = minZ + sz;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                lowest = Math.min(lowest, ground);
                highest = Math.max(highest, ground);
                if (!level.getFluidState(new BlockPos(x, ground - 1, z)).is(Fluids.EMPTY)) {
                    wet = true;
                }
            }
        }
        if (picky && (wet || highest - lowest > MAX_GROUND_SPREAD)) {
            return null;
        }

        // Sit on the highest sampled ground so the bank crowns the rise and
        // the skirt fills the downhill side, rather than burying its floor.
        int anchorY = Math.max(level.getMinBuildHeight() + 1, highest - FOUNDATION_SINK);
        return new BlockPos(minX, anchorY, minZ);
    }

    /**
     * Builds a bank centred on {@code center}, regardless of any village. Used by
     * {@code /buildbank}. Returns the locator position, or null if a build is
     * already running, the schematic is missing, or the chunks are not resident.
     */
    @Nullable
    public static BlockPos requestSpawn(ServerLevel level, BlockPos center) {
        if (activeJob != null) {
            return null;
        }
        GringottsSchematic schem = GringottsSchematic.get();
        if (schem == null) {
            return null;
        }
        BlockPos anchor = anchorAt(level, center.getX(), center.getZ(), schem, false);
        if (anchor == null) {
            return null;
        }
        RandomSource rng = RandomSource.create(level.getSeed() ^ center.asLong());
        // No village center: a hand-placed bank must not consume some nearby
        // village's one chance at getting its own.
        PlacementJob job = new PlacementJob(level, schem, anchor, null, rng);
        activeJob = job;
        LOGGER.info("Gringotts bank starting at {} (manual)", anchor);
        return job.locator();
    }

    private static boolean resident(ServerLevel level, int x, int z) {
        return level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z));
    }

    private static void announce(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Component msg = Component.literal("[Hogwarts Magic] Gringotts has opened a branch at "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " — try /findgringotts");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    // ------------------------------------------------------------------- the job

    /**
     * A bank under construction, advanced across ticks in two phases:
     * <ol>
     *   <li>Solids - place every non-air schematic cell. Air is skipped so the
     *       surrounding terrain is never carved into a box.</li>
     *   <li>Skirt - under each column, fill stone from the building's lowest
     *       block straight down until it meets ground, so nothing floats. Only
     *       air is replaced, so existing terrain beside it survives.</li>
     * </ol>
     */
    private static final class PlacementJob {
        private final ServerLevel level;
        private final GringottsSchematic schem;
        private final BlockPos anchor;
        /** Null for a hand-placed bank, which belongs to no village. */
        @Nullable
        private final BlockPos villageCenter;
        private final RandomSource rng;
        private final int width;
        private final int length;
        private final int total;
        /** Lowest non-air schematic Y per column (z*width + x), or -1 if empty. */
        private final int[] lowestSolidY;
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        private int cursor;
        private int columnCursor;

        PlacementJob(ServerLevel level, GringottsSchematic schem, BlockPos anchor,
                     @Nullable BlockPos villageCenter, RandomSource rng) {
            this.level = level;
            this.schem = schem;
            this.anchor = anchor;
            this.villageCenter = villageCenter;
            this.rng = rng;
            this.width = schem.width();
            this.length = schem.length();
            this.total = schem.volume();
            this.lowestSolidY = new int[this.width * this.length];
            java.util.Arrays.fill(this.lowestSolidY, -1);
        }

        BlockPos locator() {
            return new BlockPos(
                    this.anchor.getX() + this.width / 2,
                    this.anchor.getY(),
                    this.anchor.getZ() + this.length / 2);
        }

        /** Returns true when both phases are finished. */
        boolean advance() {
            if (this.cursor < this.total) {
                placeSolids();
                return false;
            }
            return fillSkirt();
        }

        private void placeSolids() {
            int end = Math.min(this.cursor + CELLS_PER_TICK, this.total);
            for (; this.cursor < end; this.cursor++) {
                if (this.schem.isAir(this.cursor)) {
                    continue;
                }
                int x = this.cursor % this.width;
                int z = (this.cursor / this.width) % this.length;
                int y = this.cursor / (this.width * this.length);

                this.mutable.set(this.anchor.getX() + x, this.anchor.getY() + y, this.anchor.getZ() + z);
                this.level.setBlock(this.mutable, this.schem.blockAt(this.cursor), SETBLOCK_FLAGS);

                int column = z * this.width + x;
                if (this.lowestSolidY[column] < 0) {
                    // Cells are visited in ascending Y, so the first hit is the lowest.
                    this.lowestSolidY[column] = y;
                }
            }
        }

        /** Returns true when every column has been skirted. */
        private boolean fillSkirt() {
            int columns = this.width * this.length;
            int end = Math.min(this.columnCursor + SKIRT_COLUMNS_PER_TICK, columns);
            for (; this.columnCursor < end; this.columnCursor++) {
                int lowest = this.lowestSolidY[this.columnCursor];
                // lowest < 0: nothing in this column at all. Above the limit: what we
                // found is a roof, not a floor - see SKIRT_LOWEST_Y_LIMIT.
                if (lowest < 0 || lowest > SKIRT_LOWEST_Y_LIMIT) {
                    continue;
                }
                int x = this.anchor.getX() + (this.columnCursor % this.width);
                int z = this.anchor.getZ() + (this.columnCursor / this.width);
                int startY = this.anchor.getY() + lowest - 1;
                for (int depth = 0; depth < MAX_SKIRT_DEPTH; depth++) {
                    int y = startY - depth;
                    if (y <= this.level.getMinBuildHeight()) {
                        break;
                    }
                    this.mutable.set(x, y, z);
                    if (!this.level.getBlockState(this.mutable).isAir()) {
                        break;
                    }
                    this.level.setBlock(this.mutable, SKIRT, SETBLOCK_FLAGS);
                }
            }
            return this.columnCursor >= columns;
        }

        /**
         * Populates the finished bank: a teller behind each counter, and a couple of guards loose
         * in the hall.
         *
         * <p>The work moved to {@link GringottsFurnishing} when a second thing started pasting this
         * same schematic — the copy baked into the Diagon Alley template. Both want the same
         * tellers behind the same counters, and neither should own the code. Everything it needs is
         * the schematic and the anchor it was pasted at, which is what made the extraction possible.
         */
        void staff() {
            GringottsFurnishing.staff(this.level, this.anchor, this.rng);
        }
    }
}
