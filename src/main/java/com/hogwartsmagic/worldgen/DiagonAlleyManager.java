package com.hogwartsmagic.worldgen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds Diagon Alley into a live world, spread across ticks.
 *
 * <p>The build is 168 x 40 x 50 -- about a third of a million cells and some 76,000 placed blocks.
 * Writing that in one go stalls the server for seconds and, on a dedicated server, is long enough
 * for the watchdog to notice. So it runs as a job that does a fixed slice per tick and reports when
 * it is done, the same shape as {@link MiniHogwartsManager}.
 *
 * <p>This is also why Diagon Alley is not a worldgen feature. Vanilla forbids a feature from writing
 * outside the 3x3 chunk window around the chunk being generated, and this build is eleven chunks
 * long; the old version spammed "Detected setBlock in a far chunk" and could wedge chunk loading.
 * Placing it from a command against a {@link ServerLevel} has no such window.
 *
 * <h2>The four phases</h2>
 * <ol>
 *   <li><b>Clear.</b> Every cell of the bounding box that the template does <i>not</i> place is set
 *       to air. Without this a hill runs straight through the shopfronts and a tree grows out of
 *       Gringotts: the template only describes the building, so anything the terrain already had
 *       inside the box has to go. Cells the template does cover are skipped here and written in
 *       phase two, so nothing is written twice.</li>
 *   <li><b>Place.</b> Walk the block list. Block entities -- the shop signs, which arrive with
 *       their text already on them, and the chests, which arrive with their loot tables -- are
 *       loaded straight after their block.</li>
 *   <li><b>Skirt.</b> Under each column, fill stone from the build's lowest block downward until it
 *       meets ground. A street on a slope otherwise ends with half of Knockturn Alley in mid-air.
 *       Only air is replaced, so nothing existing is destroyed.</li>
 *   <li><b>Populate.</b> Spawn the baked-in shopkeepers, goblins and owls.</li>
 * </ol>
 *
 * <p>Blocks are written with {@link Block#UPDATE_CLIENTS} alone -- deliberately no neighbour
 * updates. The template's glass panes, fences and walls already carry the connection states the
 * generator worked out, and letting the game recompute them mid-build makes panes flicker between
 * shapes as their neighbours arrive. It also stops flowers and signs popping off before the block
 * they attach to has been placed.
 */
public final class DiagonAlleyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");

    /** Cells visited per tick. The clear phase is a cheap read-then-maybe-write, so this can be high. */
    private static final int CELLS_PER_TICK = 24000;
    /** Blocks actually placed per tick. Lower, because each one is a real write plus lighting. */
    private static final int BLOCKS_PER_TICK = 9000;
    /** How deep a support skirt may run before giving up (a cliff edge, not a slope). */
    private static final int MAX_SKIRT_DEPTH = 48;
    private static final BlockState SKIRT = Blocks.STONE.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /**
     * Template x below this is the Leaky Cauldron courtyard and its brick wall. When the alley is
     * opened in place the player has built their own wall and is standing where the courtyard would
     * go, so those columns are skipped; {@code /spawnalley} still places them.
     */
    private static final int COURTYARD_END_X = 15;

    /** How often to look for visitors once an alley is open. Cheap, but no need to do it a lot. */
    private static final int OCCUPANCY_CHECK = 40;
    /**
     * How long the alley stays open after the last person walks out. Long enough not to slam shut
     * behind somebody stepping back for a moment, short enough that a forgotten alley closes itself.
     */
    private static final int GRACE_TICKS = 20 * 30;
    /** Slack around the footprint, so standing just outside a shop door still counts as inside. */
    private static final int OCCUPANCY_MARGIN = 12;

    private static PlacementJob activeJob;
    private static RestoreJob activeRestore;
    private static int emptyTicks;

    private DiagonAlleyManager() {
    }

    public static boolean isBusy() {
        return activeJob != null || activeRestore != null;
    }

    /**
     * Which way to turn the template so its street runs out through the arch.
     *
     * <p>The template is authored with the street running along +X. Rotating it is done with
     * vanilla's own {@link Rotation}, applied to the block states as well as the positions —
     * a stair or a pillar whose coordinates are rotated but whose blockstate is not comes out
     * facing the wrong way, and this build is full of both.
     */
    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_180;
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;               // EAST, the frame it was authored in
        };
    }

    /** Rotate a template-local position within a template of the given size. */
    private static int[] rotate(int lx, int ly, int lz, int sx, int sz, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new int[]{sz - 1 - lz, ly, lx};
            case CLOCKWISE_180 -> new int[]{sx - 1 - lx, ly, sz - 1 - lz};
            case COUNTERCLOCKWISE_90 -> new int[]{lz, ly, sx - 1 - lx};
            default -> new int[]{lx, ly, lz};
        };
    }

    /** The template's footprint after rotation, as {width, depth}. */
    private static int[] rotatedFootprint(int sx, int sz, Rotation rotation) {
        boolean swapped = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        return swapped ? new int[]{sz, sx} : new int[]{sx, sz};
    }

    /**
     * Start building an alley centred horizontally on {@code centre}. Returns the position recorded
     * for {@code /findalley} (the middle of the street), or null if a build is already running or
     * the template is missing.
     */
    public static BlockPos requestSpawn(ServerLevel level, BlockPos centre) {
        if (activeJob != null) {
            return null;
        }
        DiagonAlleyTemplate template = DiagonAlleyTemplate.get();
        if (template == null) {
            return null;
        }

        int sx = template.sizeX();
        int sz = template.sizeZ();

        // Average the ground over a grid across the whole footprint rather than taking the highest
        // point. A 168x50 plateau perched on the tallest hill under it looks dropped in; averaging
        // sinks the uphill end slightly and lets the skirt carry the downhill end.
        long sum = 0;
        int samples = 0;
        for (int ox = 0; ox <= sx; ox += Math.max(1, sx / 8)) {
            for (int oz = 0; oz <= sz; oz += Math.max(1, sz / 4)) {
                int gx = centre.getX() - sx / 2 + ox;
                int gz = centre.getZ() - sz / 2 + oz;
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                if (h > level.getMinBuildHeight() + 1) {
                    sum += h;
                    samples++;
                }
            }
        }
        int ground = samples > 0
                ? (int) (sum / samples)
                : level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centre.getX(), centre.getZ());

        // Template y=0 is the foundation course and y=1 is the paving, so the street lands on the
        // sampled ground when the anchor sits one below it.
        int anchorY = Math.max(level.getMinBuildHeight() + 1, ground - 1);
        BlockPos anchor = new BlockPos(centre.getX() - sx / 2, anchorY, centre.getZ() - sz / 2);

        activeJob = new PlacementJob(level, template, anchor);

        BlockPos recorded = new BlockPos(anchor.getX() + sx / 2, anchorY + 2, anchor.getZ() + sz / 2);
        DiagonAlleyTracker.get(level).add(recorded);
        announce(level, recorded);
        LOGGER.info("Diagon Alley placement started: anchor {}, {} blocks to place",
                anchor, template.count());
        return recorded;
    }

    /**
     * Called every server tick to advance an in-progress build or restore.
     *
     * <p>Restoring wins if both are somehow pending: putting the world back is the operation that
     * must not be starved.
     */
    public static void tick(MinecraftServer server) {
        if (activeRestore != null) {
            if (activeRestore.advance()) {
                LOGGER.info("Diagon Alley restore complete ({} blocks put back)",
                        activeRestore.restored);
                ServerLevel level = activeRestore.level;
                DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
                // Everyone goes back to seeing the truth. This has to happen while the snapshot is
                // still loaded, because it is what says which chunks were being lied about.
                DiagonAlleyIllusion.clearAll(level, snapshot);
                // Reseal before clearing: the arch position lives in the snapshot, and clear()
                // throws it away along with everything else.
                reseal(level, snapshot.archPos());
                snapshot.clear();
                level.getDataStorage().save();
                activeRestore = null;
                emptyTicks = 0;
            }
            return;
        }
        if (activeJob != null) {
            if (activeJob.advance()) {
                LOGGER.info("Diagon Alley placement complete ({} blocks, {} entities)",
                        activeJob.placed, activeJob.spawned);
                activeJob = null;
                emptyTicks = 0;
            }
            return;
        }
        if (server.getTickCount() % OCCUPANCY_CHECK == 0) {
            checkForVisitors(server);
        }
    }

    /**
     * Close an alley nobody is in any more.
     *
     * <p>Left open, it would sit on somebody's world indefinitely — and the longer it sits, the
     * longer the window in which a crash leaves it there. So once the last visitor has been gone
     * for {@link #GRACE_TICKS}, the world goes back and the arch reseals.
     */
    private static void checkForVisitors(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
            if (!snapshot.isActive()) {
                continue;
            }
            BlockPos origin = snapshot.origin();
            boolean occupied = false;
            for (ServerPlayer player : level.players()) {
                BlockPos p = player.blockPosition();
                if (p.getX() >= origin.getX() - OCCUPANCY_MARGIN
                        && p.getX() <= origin.getX() + snapshot.sizeX() + OCCUPANCY_MARGIN
                        && p.getZ() >= origin.getZ() - OCCUPANCY_MARGIN
                        && p.getZ() <= origin.getZ() + snapshot.sizeZ() + OCCUPANCY_MARGIN
                        && p.getY() >= origin.getY() - OCCUPANCY_MARGIN
                        && p.getY() <= origin.getY() + snapshot.sizeY() + OCCUPANCY_MARGIN) {
                    occupied = true;
                    break;
                }
            }
            if (occupied) {
                emptyTicks = 0;
                return;
            }
            emptyTicks += OCCUPANCY_CHECK;
            if (emptyTicks >= GRACE_TICKS) {
                LOGGER.info("Nobody has been in the alley for {} ticks; closing it", emptyTicks);
                emptyTicks = 0;
                restore(level);
            }
            return;
        }
    }

    /**
     * Put the archway back to a plain wall, still armed.
     *
     * <p>Still armed on purpose: the spell was cast on this wall once and there is no reason to
     * make somebody cast it again to get back in. Three taps is the price of re-entry.
     */
    private static void reseal(ServerLevel level, BlockPos archPos) {
        if (archPos == null || BlockPos.ZERO.equals(archPos)) {
            return;
        }
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.Deque<BlockPos> queue = new java.util.ArrayDeque<>();
        // Start from the neighbourhood of the arch, because the arch's own cells may be OPEN and
        // the ones that survived are around them.
        for (BlockPos start : BlockPos.betweenClosed(archPos.offset(-2, -2, -2),
                archPos.offset(2, 2, 2))) {
            if (level.getBlockState(start).getBlock()
                    instanceof com.hogwartsmagic.block.DiagonArchwayBlock) {
                BlockPos p = start.immutable();
                queue.add(p);
                seen.add(p);
                break;
            }
        }
        int resealed = 0;
        while (!queue.isEmpty() && resealed < 1024) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof com.hogwartsmagic.block.DiagonArchwayBlock)) {
                continue;
            }
            if (state.getValue(com.hogwartsmagic.block.DiagonArchwayBlock.PHASE)
                    != com.hogwartsmagic.block.DiagonArchwayBlock.SHUT) {
                level.setBlock(pos, state
                                .setValue(com.hogwartsmagic.block.DiagonArchwayBlock.PHASE,
                                        com.hogwartsmagic.block.DiagonArchwayBlock.SHUT)
                                .setValue(com.hogwartsmagic.block.DiagonArchwayBlock.LIT, false),
                        Block.UPDATE_ALL);
                resealed++;
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (resealed > 0) {
            level.playSound(null, archPos, net.minecraft.sounds.SoundEvents.ANCIENT_DEBRIS_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
            LOGGER.info("Resealed the archway ({} bricks)", resealed);
        }
    }

    private static void announce(ServerLevel level, BlockPos centre) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Component msg = Component.literal(
                        "[Hogwarts Magic] Diagon Alley is rising at " + centre.getX() + ", "
                                + centre.getY() + ", " + centre.getZ()
                                + " — tap the wall behind the Leaky Cauldron, three up and two across from the bin.")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    // ---------------------------------------------------------------------------------------

    /**
     * Open the alley in place, through a wall the player built.
     *
     * <p>No teleport, no dimension, no loading screen: the street is written into the world the
     * player is already standing in, which is the only way looking back out through the arch can
     * show the field they came from. The bill for that is {@link DiagonAlleySnapshot}, and the rule
     * that keeps it payable is that the snapshot is captured and flushed <em>before</em> any block
     * of the alley is placed. That is why capture is the job's first phase rather than something
     * done alongside placement.
     *
     * <p>Returns the anchor the alley was placed at, or null if one is already open, a build is
     * already running, or the template is missing.
     */
    public static BlockPos openInPlace(ServerLevel level, BlockPos archCentre, Direction facing) {
        if (isBusy()) {
            return null;
        }
        DiagonAlleyTemplate template = DiagonAlleyTemplate.get();
        if (template == null) {
            return null;
        }
        DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
        if (snapshot.isActive()) {
            LOGGER.info("An alley is already open in this dimension; refusing to open a second");
            return null;
        }

        Rotation rotation = rotationFor(facing);
        int entryZ = streetEntryZ(template);
        int[] entry = rotate(COURTYARD_END_X, 1, entryZ, template.sizeX(), template.sizeZ(), rotation);

        // The street's first cell sits one block through the arch, at the arch's own floor level.
        BlockPos worldEntry = archCentre.relative(facing);
        BlockPos anchor = new BlockPos(worldEntry.getX() - entry[0],
                worldEntry.getY() - entry[1],
                worldEntry.getZ() - entry[2]);

        activeJob = new PlacementJob(level, template, anchor, rotation, true, snapshot);
        snapshot.setArchPos(archCentre);
        DiagonAlleyTracker.get(level).add(archCentre);
        LOGGER.info("Opening Diagon Alley in place: arch {} facing {}, anchor {}, rotation {}",
                archCentre, facing, anchor, rotation);
        return anchor;
    }

    /**
     * The template's street centreline where the street leaves the courtyard, measured rather than
     * written down.
     *
     * <p>At the entry column the street is the run of cells with paving at y=1 and open air above
     * it. Deriving it means the street can be re-routed in the generator without this going quietly
     * out of step.
     */
    private static int streetEntryZ(DiagonAlleyTemplate template) {
        boolean[] paved = new boolean[template.sizeZ()];
        boolean[] blocked = new boolean[template.sizeZ()];
        for (int i = 0; i < template.count(); i++) {
            if (template.localX(i) != COURTYARD_END_X) {
                continue;
            }
            int ly = template.localY(i);
            int lz = template.localZ(i);
            boolean air = template.stateAt(i).isAir();
            if (ly == 1 && !air) {
                paved[lz] = true;
            } else if (ly >= 2 && ly <= 4 && !air) {
                blocked[lz] = true;
            }
        }
        int best = -1;
        int bestRun = 0;
        int run = 0;
        for (int z = 0; z < template.sizeZ(); z++) {
            if (paved[z] && !blocked[z]) {
                run++;
                if (run > bestRun) {
                    bestRun = run;
                    best = z - run / 2;
                }
            } else {
                run = 0;
            }
        }
        if (best < 0) {
            LOGGER.warn("Could not find the street at the template's entry column; using the middle");
            return template.sizeZ() / 2;
        }
        return best;
    }

    /**
     * Put the world back. Safe to call when nothing is open -- it simply does nothing.
     */
    public static boolean restore(ServerLevel level) {
        if (activeRestore != null) {
            return false;
        }
        DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
        if (!snapshot.isActive()) {
            return false;
        }
        activeJob = null;                     // a half-finished build is abandoned, not completed
        activeRestore = new RestoreJob(level, snapshot);
        LOGGER.info("Restoring the world under Diagon Alley: {} blocks", snapshot.cellCount());
        return true;
    }

    /**
     * Called once at server start. If a snapshot is still flagged active then the server went down
     * while an alley was open, and the world is still wearing it -- so put it back before anyone
     * can log in and see.
     */
    public static void restoreOnBoot(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
            if (snapshot.isActive()) {
                LOGGER.warn("Found an alley still open in {} from a previous run -- restoring",
                        level.dimension().location());
                restore(level);
                return;
            }
        }
    }

    /**
     * A build in progress.
     *
     * <p>The template is <b>rotated once, here, into a private copy</b> rather than rotated at every
     * read. Clearing, placing and skirting then all work in one frame and need to know nothing about
     * which way the street runs. Rotating lazily meant every phase had to agree about the
     * transform, and the first one to disagree would have put a shopfront through a wall.
     *
     * <p>Block <i>states</i> are rotated as well as positions, through vanilla's own
     * {@link BlockState#rotate}. Rotating only the coordinates leaves every stair, pillar, door and
     * pane facing the way it did in the template, which is wrong three times in four.
     */
    private static final class PlacementJob {
        private final ServerLevel level;
        private final DiagonAlleyTemplate template;
        private final BlockPos anchor;
        private final Rotation rotation;
        /** Null for {@code /spawnalley}: only an in-place opening records what it covered. */
        private final DiagonAlleySnapshot snapshot;
        private final boolean skipCourtyard;

        /** Footprint after rotation. */
        private final int fx;
        private final int fy;
        private final int fz;

        /** The rotated template. */
        private final int[] rx;
        private final int[] ry;
        private final int[] rz;
        private final BlockState[] rstate;
        private final CompoundTag[] rnbt;
        private final int count;

        private final long[] covered;
        private final int[] lowestY;

        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private int phase;           // 0 capture, 1 clear, 2 place, 3 skirt, 4 populate, 5 furnish
        private int captureCursor;
        private int clearCursor;
        private int placeCursor;
        private int columnCursor;
        private int entityCursor;
        int placed;
        int spawned;

        PlacementJob(ServerLevel level, DiagonAlleyTemplate template, BlockPos anchor) {
            this(level, template, anchor, Rotation.NONE, false, null);
        }

        PlacementJob(ServerLevel level, DiagonAlleyTemplate template, BlockPos anchor,
                     Rotation rotation, boolean skipCourtyard, DiagonAlleySnapshot snapshot) {
            this.level = level;
            this.template = template;
            this.anchor = anchor;
            this.rotation = rotation;
            this.skipCourtyard = skipCourtyard;
            this.snapshot = snapshot;

            int tx = template.sizeX();
            int tz = template.sizeZ();
            int[] footprint = rotatedFootprint(tx, tz, rotation);
            this.fx = footprint[0];
            this.fy = template.sizeY();
            this.fz = footprint[1];

            int kept = 0;
            for (int i = 0; i < template.count(); i++) {
                if (!skipCourtyard || template.localX(i) >= COURTYARD_END_X) {
                    kept++;
                }
            }
            this.count = kept;
            this.rx = new int[kept];
            this.ry = new int[kept];
            this.rz = new int[kept];
            this.rstate = new BlockState[kept];
            this.rnbt = new CompoundTag[kept];

            this.covered = new long[(fx * fy * fz + 63) >> 6];
            this.lowestY = new int[fx * fz];
            java.util.Arrays.fill(this.lowestY, -1);

            int out = 0;
            for (int i = 0; i < template.count(); i++) {
                if (skipCourtyard && template.localX(i) < COURTYARD_END_X) {
                    continue;                   // the player built their own wall; this is theirs
                }
                int[] r = rotate(template.localX(i), template.localY(i), template.localZ(i),
                        tx, tz, rotation);
                this.rx[out] = r[0];
                this.ry[out] = r[1];
                this.rz[out] = r[2];
                this.rstate[out] = template.stateAt(i).rotate(rotation);
                this.rnbt[out] = template.blockEntityAt(i);

                int idx = cellIndex(r[0], r[1], r[2]);
                this.covered[idx >> 6] |= 1L << (idx & 63);
                if (!this.rstate[out].isAir()) {
                    int col = r[2] * fx + r[0];
                    if (this.lowestY[col] < 0 || r[1] < this.lowestY[col]) {
                        this.lowestY[col] = r[1];
                    }
                }
                out++;
            }

            if (snapshot != null) {
                snapshot.begin(anchor, fx, fy, fz);
            } else {
                this.phase = 1;                 // nothing to record; go straight to clearing
            }
        }

        private int cellIndex(int lx, int ly, int lz) {
            return (ly * fz + lz) * fx + lx;
        }

        /** One tick's worth of work. Returns true when the whole job is finished. */
        boolean advance() {
            switch (phase) {
                case 0 -> capture();
                case 1 -> clear();
                case 2 -> place();
                case 3 -> skirt();
                case 4 -> populate();
                case 5 -> furnishBank();
                default -> {
                    return true;
                }
            }
            return phase >= 6;
        }

        /**
         * Record what is about to be covered. Runs first and finishes before a single block is
         * placed, so a crash during it leaves a world that was never touched.
         */
        private void capture() {
            int total = fx * fy * fz;
            int end = Math.min(total, captureCursor + CELLS_PER_TICK);
            snapshot.captureSlice(level, captureCursor, end);
            captureCursor = end;
            if (captureCursor >= total) {
                snapshot.commit(level);
                phase = 1;
            }
        }

        private void clear() {
            int total = fx * fy * fz;
            int end = Math.min(total, clearCursor + CELLS_PER_TICK);
            for (int idx = clearCursor; idx < end; idx++) {
                if ((covered[idx >> 6] & (1L << (idx & 63))) != 0) {
                    continue;                       // the place phase writes this one
                }
                int lx = idx % fx;
                int rest = idx / fx;
                int lz = rest % fz;
                int ly = rest / fz;
                cursor.set(anchor.getX() + lx, anchor.getY() + ly, anchor.getZ() + lz);
                BlockState existing = level.getBlockState(cursor);
                if (!existing.isAir()) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            clearCursor = end;
            if (clearCursor >= total) {
                phase = 2;
            }
        }

        private void place() {
            int end = Math.min(count, placeCursor + BLOCKS_PER_TICK);
            for (int i = placeCursor; i < end; i++) {
                cursor.set(anchor.getX() + rx[i], anchor.getY() + ry[i], anchor.getZ() + rz[i]);
                level.setBlock(cursor, rstate[i], FLAGS);
                placed++;

                CompoundTag be = rnbt[i];
                if (be != null) {
                    BlockEntity entity = level.getBlockEntity(cursor);
                    if (entity != null) {
                        // Copy before stamping coordinates in: the template is cached and shared, so
                        // mutating its tags would bake the first alley's coordinates into the second.
                        CompoundTag tag = be.copy();
                        tag.putInt("x", cursor.getX());
                        tag.putInt("y", cursor.getY());
                        tag.putInt("z", cursor.getZ());
                        entity.loadWithComponents(tag, level.registryAccess());
                        entity.setChanged();
                    }
                }
            }
            placeCursor = end;
            if (placeCursor >= count) {
                phase = 3;
            }
        }

        private void skirt() {
            int total = fx * fz;
            int end = Math.min(total, columnCursor + CELLS_PER_TICK / 8);
            for (int col = columnCursor; col < end; col++) {
                int low = lowestY[col];
                if (low < 0) {
                    continue;
                }
                int lx = col % fx;
                int lz = col / fx;
                int wx = anchor.getX() + lx;
                int wz = anchor.getZ() + lz;
                for (int d = 1; d <= MAX_SKIRT_DEPTH; d++) {
                    int wy = anchor.getY() + low - d;
                    if (wy <= level.getMinBuildHeight()) {
                        break;
                    }
                    cursor.set(wx, wy, wz);
                    BlockState existing = level.getBlockState(cursor);
                    FluidState fluid = existing.getFluidState();
                    // Stop at the first real ground. Replace air and water only, so a hillside is
                    // never cut away and a lake under the footprint is filled rather than drained.
                    if (!existing.isAir() && fluid.isEmpty()) {
                        break;
                    }
                    level.setBlock(cursor, SKIRT, FLAGS);
                    placed++;
                }
            }
            columnCursor = end;
            if (columnCursor >= total) {
                phase = 4;
            }
        }

        private void populate() {
            java.util.List<CompoundTag> list = template.entities();
            int end = Math.min(list.size(), entityCursor + 16);
            for (int i = entityCursor; i < end; i++) {
                CompoundTag tag = list.get(i).copy();
                ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
                double lx = pos.getDouble(0);
                double ly = pos.getDouble(1);
                double lz = pos.getDouble(2);
                if (skipCourtyard && lx < COURTYARD_END_X) {
                    continue;
                }
                double[] r = rotateContinuous(lx, ly, lz, template.sizeX(), template.sizeZ(), rotation);
                double ex = anchor.getX() + r[0];
                double ey = anchor.getY() + r[1];
                double ez = anchor.getZ() + r[2];
                // The stored Pos is template-local; rewrite it before the entity is built, because
                // loadEntityRecursive reads Pos itself and would otherwise put every shopkeeper
                // within fifty blocks of the world origin.
                ListTag moved = new ListTag();
                moved.add(net.minecraft.nbt.DoubleTag.valueOf(ex));
                moved.add(net.minecraft.nbt.DoubleTag.valueOf(ey));
                moved.add(net.minecraft.nbt.DoubleTag.valueOf(ez));
                tag.put("Pos", moved);
                tag.remove("UUID");                 // or every alley after the first refuses to spawn

                Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
                    e.moveTo(ex, ey, ez, e.getYRot(), e.getXRot());
                    return e;
                });
                if (entity != null) {
                    entity.setYRot(entity.getYRot() + rotationDegrees(rotation));
                    if (level.addFreshEntity(entity)) {
                        spawned++;
                    }
                }
            }
            entityCursor = end;
            if (entityCursor >= list.size()) {
                phase = 5;
            }
        }

        /**
         * Staff the bank at the end of the street, and turn its rotunda screen into a real gate.
         *
         * <p>The template carries the Gringotts building but no goblins, because
         * {@link GringottsFurnishing} finds its own teller positions from the gold pressure plates
         * in the desk tops — baking them into the template as well would staff the hall twice.
         *
         * <p>The bank's anchor is <b>derived, not hard-coded</b>. Both the schematic and the
         * template contain exactly one {@code dark_prismarine}, the undercroft station marker, so
         * lining those two up gives the offset the building was pasted at. An offset written down
         * in Java would silently drift the first time the street's layout changed by a block.
         */
        private void furnishBank() {
            phase = 6;
            GringottsSchematic schem = GringottsSchematic.get();
            if (schem == null) {
                return;
            }
            com.hogwartsmagic.worldgen.undercroft.GringottsLandmarks marks =
                    com.hogwartsmagic.worldgen.undercroft.GringottsLandmarks.of(schem);
            if (marks == null) {
                LOGGER.warn("No Gringotts landmarks; the alley's bank will go unstaffed");
                return;
            }
            for (int i = 0; i < count; i++) {
                if (!rstate[i].is(Blocks.DARK_PRISMARINE)) {
                    continue;
                }
                // The marker is rotated with everything else, so the offset back to the building's
                // own origin has to be rotated the same way before it can be subtracted.
                int[] off = rotate(marks.islandX, marks.islandY, marks.islandZ,
                        schem.width(), schem.length(), rotation);
                BlockPos bankAnchor = new BlockPos(anchor.getX() + rx[i] - off[0],
                        anchor.getY() + ry[i] - off[1],
                        anchor.getZ() + rz[i] - off[2]);
                GringottsFurnishing.furnish(level, bankAnchor, level.getRandom());
                LOGGER.info("Staffed the alley's Gringotts, anchored at {}", bankAnchor);
                return;
            }
            LOGGER.warn("No station marker in the Diagon Alley template; bank left unstaffed");
        }
    }

    /** Continuous-coordinate rotation, for entity positions. */
    private static double[] rotateContinuous(double x, double y, double z, int sx, int sz,
                                             Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new double[]{sz - z, y, x};
            case CLOCKWISE_180 -> new double[]{sx - x, y, sz - z};
            case COUNTERCLOCKWISE_90 -> new double[]{z, y, sx - x};
            default -> new double[]{x, y, z};
        };
    }

    private static float rotationDegrees(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 90.0F;
            case CLOCKWISE_180 -> 180.0F;
            case COUNTERCLOCKWISE_90 -> 270.0F;
            default -> 0.0F;
        };
    }

    /**
     * Putting the world back.
     *
     * <p>Two things have to happen, and only doing the first is the bug that would leave the alley
     * standing: every cell the snapshot recorded goes back to what it was, and <b>every cell it did
     * not record goes back to air</b>. The second is what removes the alley, because most of the
     * street was built where there had been nothing at all.
     */
    private static final class RestoreJob {
        private final ServerLevel level;
        private final DiagonAlleySnapshot snapshot;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        /** One bit per cell: did the snapshot record something here? */
        private final long[] recorded;

        private int phase;            // 0 wipe to air, 1 put the recorded blocks back
        private int wipeCursor;
        private int putCursor;
        int restored;

        RestoreJob(ServerLevel level, DiagonAlleySnapshot snapshot) {
            this.level = level;
            this.snapshot = snapshot;
            this.recorded = new long[(snapshot.volume() + 63) >> 6];
            for (int i = 0; i < snapshot.cellCount(); i++) {
                int cell = snapshot.cellAt(i);
                this.recorded[cell >> 6] |= 1L << (cell & 63);
            }
        }

        boolean advance() {
            switch (phase) {
                case 0 -> wipe();
                case 1 -> put();
                default -> {
                    return true;
                }
            }
            return phase >= 2;
        }

        private void wipe() {
            int total = snapshot.volume();
            int end = Math.min(total, wipeCursor + CELLS_PER_TICK);
            for (int cell = wipeCursor; cell < end; cell++) {
                if ((recorded[cell >> 6] & (1L << (cell & 63))) != 0) {
                    continue;                       // phase 1 writes this one
                }
                BlockPos pos = snapshot.posOfCell(cell);
                if (!level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    restored++;
                }
            }
            wipeCursor = end;
            if (wipeCursor >= total) {
                phase = 1;
            }
        }

        private void put() {
            int total = snapshot.cellCount();
            int end = Math.min(total, putCursor + BLOCKS_PER_TICK);
            for (int i = putCursor; i < total && i < end; i++) {
                int cell = snapshot.cellAt(i);
                BlockPos pos = snapshot.posOfCell(cell);
                level.setBlock(pos, snapshot.stateAt(i), FLAGS);
                restored++;
                CompoundTag be = snapshot.blockEntityAt(cell);
                if (be != null) {
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity != null) {
                        CompoundTag tag = be.copy();
                        tag.putInt("x", pos.getX());
                        tag.putInt("y", pos.getY());
                        tag.putInt("z", pos.getZ());
                        entity.loadWithComponents(tag, level.registryAccess());
                        entity.setChanged();
                    }
                }
            }
            putCursor = end;
            if (putCursor >= total) {
                phase = 2;
            }
        }
    }
}
