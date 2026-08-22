package com.hogwartsmagic.worldgen;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

    private static PlacementJob activeJob;

    private DiagonAlleyManager() {
    }

    public static boolean isBusy() {
        return activeJob != null;
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
        if (activeJob == null) {
            return;
        }
        if (activeJob.advance()) {
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

    private static final class PlacementJob {
        private final ServerLevel level;
        private final DiagonAlleyTemplate template;
        private final BlockPos anchor;
        private final int sx;
        private final int sy;
        private final int sz;

        /** One bit per cell of the bounding box: does the template place something here? */
        private final long[] covered;
        /** Lowest template Y in each column, or -1 where the column is empty. */
        private final int[] lowestY;

        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private int phase;            // 0 clear, 1 place, 2 skirt, 3 entities, 4 done
        private int clearCursor;
        private int placeCursor;
        private int columnCursor;
        private int entityCursor;
        int placed;
        int spawned;

        PlacementJob(ServerLevel level, DiagonAlleyTemplate template, BlockPos anchor) {
            this.level = level;
            this.template = template;
            this.anchor = anchor;
            this.sx = template.sizeX();
            this.sy = template.sizeY();
            this.sz = template.sizeZ();
            this.covered = new long[(sx * sy * sz + 63) >> 6];
            this.lowestY = new int[sx * sz];
            java.util.Arrays.fill(this.lowestY, -1);

            for (int i = 0; i < template.count(); i++) {
                int lx = template.localX(i);
                int ly = template.localY(i);
                int lz = template.localZ(i);
                int idx = cellIndex(lx, ly, lz);
                covered[idx >> 6] |= 1L << (idx & 63);
                if (!template.stateAt(i).isAir()) {
                    int col = lz * sx + lx;
                    if (lowestY[col] < 0 || ly < lowestY[col]) {
                        lowestY[col] = ly;
                    }
                }
            }
        }

        private int cellIndex(int lx, int ly, int lz) {
            return (ly * sz + lz) * sx + lx;
        }

        /** One tick's worth of work. Returns true when the whole job is finished. */
        boolean advance() {
            switch (phase) {
                case 0 -> clear();
                case 1 -> place();
                case 2 -> skirt();
                case 3 -> populate();
                default -> {
                    return true;
                }
            }
            return phase >= 4;
        }

        private void clear() {
            int total = sx * sy * sz;
            int end = Math.min(total, clearCursor + CELLS_PER_TICK);
            for (int idx = clearCursor; idx < end; idx++) {
                if ((covered[idx >> 6] & (1L << (idx & 63))) != 0) {
                    continue;                       // phase 1 writes this one
                }
                int lx = idx % sx;
                int rest = idx / sx;
                int lz = rest % sz;
                int ly = rest / sz;
                cursor.set(anchor.getX() + lx, anchor.getY() + ly, anchor.getZ() + lz);
                BlockState existing = level.getBlockState(cursor);
                if (!existing.isAir()) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            clearCursor = end;
            if (clearCursor >= total) {
                phase = 1;
            }
        }

        private void place() {
            int total = template.count();
            int end = Math.min(total, placeCursor + BLOCKS_PER_TICK);
            for (int i = placeCursor; i < end; i++) {
                cursor.set(anchor.getX() + template.localX(i),
                        anchor.getY() + template.localY(i),
                        anchor.getZ() + template.localZ(i));
                BlockState state = template.stateAt(i);
                level.setBlock(cursor, state, FLAGS);
                placed++;

                CompoundTag be = template.blockEntityAt(i);
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
            if (placeCursor >= total) {
                phase = 2;
            }
        }

        private void skirt() {
            int total = sx * sz;
            int end = Math.min(total, columnCursor + CELLS_PER_TICK / 8);
            for (int col = columnCursor; col < end; col++) {
                int low = lowestY[col];
                if (low < 0) {
                    continue;
                }
                int lx = col % sx;
                int lz = col / sx;
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
                phase = 3;
            }
        }

        private void populate() {
            java.util.List<CompoundTag> list = template.entities();
            int end = Math.min(list.size(), entityCursor + 16);
            for (int i = entityCursor; i < end; i++) {
                CompoundTag tag = list.get(i).copy();
                ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
                double ex = pos.getDouble(0) + anchor.getX();
                double ey = pos.getDouble(1) + anchor.getY();
                double ez = pos.getDouble(2) + anchor.getZ();
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
                if (entity != null && level.addFreshEntity(entity)) {
                    spawned++;
                }
            }
            entityCursor = end;
            if (entityCursor >= list.size()) {
                phase = 4;
            }
        }
    }
}
