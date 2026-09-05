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
 * Builds Diagon Alley, spread across ticks.
 *
 * <p>The build is 233 x 45 x 51 -- about half a million cells and some 89,000 placed blocks.
 * Writing that in one go stalls the server for seconds and, on a dedicated server, is long enough
 * for the watchdog to notice. So it runs as a job that does a fixed slice per tick and reports when
 * it is done, the same shape as {@link MiniHogwartsManager}.
 *
 * <p>This is also why Diagon Alley is not a worldgen feature. Vanilla forbids a feature from writing
 * outside the 3x3 chunk window around the chunk being generated, and this build is fifteen chunks
 * long; the old version spammed "Detected setBlock in a far chunk" and could wedge chunk loading.
 * Placing it from a command against a {@link ServerLevel} has no such window.
 *
 * <h2>Two ways in, and only one of them clears anything</h2>
 *
 * <p>An alley opened through a wall is built into {@link DiagonAlleyDimension}, which is empty. That
 * removes most of this class: there is nothing to record, because nothing of anybody's is being
 * covered; nothing to clear, because there is nothing there; and no skirt to build, because the
 * street is not standing on a hillside. The job runs one phase -- place -- and then staffs the shops.
 *
 * <p>{@code /spawnalley} still writes into whatever world it is run in, and still clears and skirts,
 * because a street dropped onto real terrain needs both. It is a testing and rendering command.
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
    /**
     * Clients only, no neighbour updates, and <b>no drops</b>. Without SUPPRESS_DROPS, clearing
     * ground that happens to hold a chest or a furnace makes its onRemove dump the contents --
     * hundreds of item entities popping at once, which is both the noise heard while the alley
     * goes up and a floor covered in somebody's belongings afterwards.
     */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    /**
     * Template x below this is the Leaky Cauldron courtyard and its brick wall.
     *
     * <p>It used to be dropped for an in-place opening, because the player had built their own wall
     * and was standing where it would have gone. In the dimension it is wanted: it is the inside
     * face of the same wall, and the way back out.
     */
    private static final int COURTYARD_END_X = 15;

    /**
     * The template column the brick wall itself stands in. Everything below it is the Leaky
     * Cauldron courtyard, and an alley built into the dimension deliberately leaves that out.
     *
     * <p>The courtyard sits on the near side of the wall -- the side the player is standing on --
     * so building it would put a second, wrong courtyard at the same coordinates as whatever they
     * actually have there. Their own garden is the courtyard. What the dimension needs is the wall
     * and everything through it, and nothing else, which is also what makes looking back out
     * through the arch show the world they came from: there is simply nothing there to send.
     */
    private static final int WALL_X = 14;

    private static PlacementJob activeJob;

    private DiagonAlleyManager() {
    }

    public static boolean isBusy() {
        return activeJob != null;
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

    /** Called every server tick to advance an in-progress build. */
    public static void tick(MinecraftServer server) {
        if (activeJob != null && activeJob.advance()) {
            LOGGER.info("Diagon Alley placement complete ({} blocks, {} entities)",
                    activeJob.placed, activeJob.spawned);
            activeJob = null;
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
     * Open the alley behind a wall the player built.
     *
     * <p>The street goes into {@link DiagonAlleyDimension}, at the coordinates of the wall itself.
     * Not near them and not scaled to them -- the same numbers. That is what makes the crossing
     * invisible: the client keeps the chunk cache it already has, centred where it already was, and
     * the alley simply arrives in it. An alley anywhere else in the dimension would be a long jump,
     * and a long jump cannot be hidden (see {@link DiagonAlleyPortal}).
     *
     * <p>Built once per wall. A wall that has been opened before is already standing behind itself,
     * so this returns immediately and the arch opens onto a street that never went away.
     *
     * <p>Returns the anchor, or null if the dimension or the template is missing, or a build is
     * already running.
     */
    public static BlockPos open(ServerLevel from, BlockPos archCentre, Direction facing) {
        MinecraftServer server = from.getServer();
        if (server == null || isBusy()) {
            return null;
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            LOGGER.error("The Diagon Alley dimension is missing; the wall cannot open");
            return null;
        }
        DiagonAlleyTemplate template = DiagonAlleyTemplate.get();
        if (template == null) {
            return null;
        }

        DiagonAlleyRegistry registry = DiagonAlleyRegistry.get(alley);
        DiagonAlleyRegistry.Entry already = registry.forArch(from, archCentre);
        if (already != null) {
            LOGGER.info("Reopening the alley behind {}; it is already built at {}",
                    archCentre, already.anchor());
            return already.anchor();
        }

        Rotation rotation = rotationFor(facing);
        BlockPos anchor = anchorFor(template, archCentre, facing, rotation);
        int[] footprint = rotatedFootprint(template.sizeX(), template.sizeZ(), rotation);

        // A wall rebuilt a block or two over would otherwise leave its old street standing and,
        // worse, its old doorway in the registry -- two doorways in range of one player is what
        // made the view thrash badly enough to exhaust the heap.
        java.util.List<DiagonAlleyRegistry.Entry> stale =
                registry.overlapping(anchor, footprint[0], template.sizeY(), footprint[1]);
        int[] clearBox = null;
        if (!stale.isEmpty()) {
            int x0 = anchor.getX(), y0 = anchor.getY(), z0 = anchor.getZ();
            int x1 = x0 + footprint[0] - 1, y1 = y0 + template.sizeY() - 1, z1 = z0 + footprint[1] - 1;
            for (DiagonAlleyRegistry.Entry old : stale) {
                x0 = Math.min(x0, old.anchor().getX());
                y0 = Math.min(y0, old.anchor().getY());
                z0 = Math.min(z0, old.anchor().getZ());
                x1 = Math.max(x1, old.anchor().getX() + old.sizeX() - 1);
                y1 = Math.max(y1, old.anchor().getY() + old.sizeY() - 1);
                z1 = Math.max(z1, old.anchor().getZ() + old.sizeZ() - 1);
                registry.remove(old);
                LOGGER.info("Dropping the alley behind {}; a new wall overlaps it", old.arch());
            }
            clearBox = new int[]{x0, y0, z0, x1, y1, z1};
        }

        activeJob = new PlacementJob(alley, template, anchor, rotation, true, clearBox);
        registry.record(new DiagonAlleyRegistry.Entry(
                from.dimension().location().toString(), archCentre, anchor, facing,
                footprint[0], template.sizeY(), footprint[1]));
        alley.getDataStorage().save();
        DiagonAlleyTracker.get(from).add(archCentre);
        LOGGER.info("Building Diagon Alley: arch {} facing {}, anchor {}, rotation {}",
                archCentre, facing, anchor, rotation);
        return anchor;
    }

    /**
     * Where the template has to sit for its street to come out through this arch.
     *
     * <p>The template is authored with the courtyard at low x and the street leaving it at
     * {@link #COURTYARD_END_X}; rotating that entry point and subtracting it from the world cell
     * just through the arch puts the two together whichever way the wall faces.
     */
    public static BlockPos anchorFor(DiagonAlleyTemplate template, BlockPos archCentre,
                                     Direction facing, Rotation rotation) {
        int entryZ = streetEntryZ(template);
        int[] entry = rotate(COURTYARD_END_X, 1, entryZ, template.sizeX(), template.sizeZ(), rotation);
        BlockPos worldEntry = archCentre.relative(facing);
        return new BlockPos(worldEntry.getX() - entry[0],
                worldEntry.getY() - entry[1],
                worldEntry.getZ() - entry[2]);
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
        /**
         * True when the target is the empty alley dimension, which is every opening through a
         * wall. There is nothing there to clear and nothing under the street to hold it up, so
         * both of those phases are skipped and the job is just a paste.
         */
        private final boolean intoVoid;
        /**
         * A box of the alley dimension to empty before placing, as {x0,y0,z0,x1,y1,z1}, or null.
         *
         * <p>Only ever set when a wall is rebuilt a block or two from where it was. The old street
         * is still standing at the old coordinates and the new one lands almost on top of it, so
         * without this the dimension ends up with two streets a couple of blocks apart -- and, far
         * worse, two doorways, which is what made the view code thrash and the server run out of
         * memory.
         */
        private final int[] clearBox;
        private int oldCursor;

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
        private int clearCursor;
        private int placeCursor;
        private int columnCursor;
        private int entityCursor;
        int placed;
        int spawned;

        PlacementJob(ServerLevel level, DiagonAlleyTemplate template, BlockPos anchor) {
            this(level, template, anchor, Rotation.NONE, false);
        }

        PlacementJob(ServerLevel level, DiagonAlleyTemplate template, BlockPos anchor,
                     Rotation rotation, boolean intoVoid) {
            this(level, template, anchor, rotation, intoVoid, null);
        }

        PlacementJob(ServerLevel level, DiagonAlleyTemplate template, BlockPos anchor,
                     Rotation rotation, boolean intoVoid, int[] clearBox) {
            this.clearBox = clearBox;
            this.level = level;
            this.template = template;
            this.anchor = anchor;
            this.rotation = rotation;
            this.intoVoid = intoVoid;

            int tx = template.sizeX();
            int tz = template.sizeZ();
            int[] footprint = rotatedFootprint(tx, tz, rotation);
            this.fx = footprint[0];
            this.fy = template.sizeY();
            this.fz = footprint[1];

            int kept = 0;
            for (int i = 0; i < template.count(); i++) {
                if (!intoVoid || template.localX(i) >= WALL_X) {
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
                if (intoVoid && template.localX(i) < WALL_X) {
                    continue;               // the courtyard is the player own garden; leave it
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

            if (intoVoid) {
                this.phase = clearBox != null ? 1 : 2;   // empty, unless an older street is in the way
            } else {
                this.phase = 1;                 // /spawnalley writes into real terrain; clear first
            }
        }

        private int cellIndex(int lx, int ly, int lz) {
            return (ly * fz + lz) * fx + lx;
        }

        /** One tick's worth of work. Returns true when the whole job is finished. */
        boolean advance() {
            switch (phase) {
                case 1 -> {
                    if (intoVoid) {
                        clearOld();
                    } else {
                        clear();
                    }
                }
                case 2 -> place();
                case 3 -> {
                    if (intoVoid) {
                        phase = 4;              // nothing under it to hold up; see skirt()
                    } else {
                        skirt();
                    }
                }
                case 4 -> populate();
                case 5 -> furnishBank();
                default -> {
                    return true;
                }
            }
            return phase >= 6;
        }

        /** Empty the box an older street left behind, a slice per tick like everything else. */
        private void clearOld() {
            int sx = clearBox[3] - clearBox[0] + 1;
            int sy = clearBox[4] - clearBox[1] + 1;
            int sz = clearBox[5] - clearBox[2] + 1;
            int total = sx * sy * sz;
            int end = Math.min(total, oldCursor + CELLS_PER_TICK);
            for (int idx = oldCursor; idx < end; idx++) {
                int lx = idx % sx;
                int rest = idx / sx;
                int lz = rest % sz;
                int ly = rest / sz;
                cursor.set(clearBox[0] + lx, clearBox[1] + ly, clearBox[2] + lz);
                if (!level.getBlockState(cursor).isAir()) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            oldCursor = end;
            if (oldCursor >= total) {
                phase = 2;
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

        /**
         * Stone under the build so it does not float on a slope.
         *
         * <p>Only for {@code /spawnalley}. An alley built in its own dimension skips it: there is
         * no slope to bridge, and a column of stone dropping away below the street would be visible
         * from the upstairs windows as a plinth hanging in the void.
         */
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
                if (intoVoid && lx < WALL_X) {
                    continue;               // nobody stands in a courtyard that is not built
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
}
