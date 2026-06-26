package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the {@link MiniHogwartsSchematic} into the world at runtime, OUTSIDE the worldgen feature
 * pipeline (so there is no "setBlock in a far chunk" restriction — same approach Diagon Alley uses).
 *
 * <p>The castle is ~1.5M blocks, so placement is spread across server ticks in small budgets to
 * avoid a hitch. One castle is auto-built the first time a player stands in a Forbidden Forest biome
 * with none already nearby; it can also be summoned with {@code /spawnminihogwarts} and located with
 * {@code /findminihogwarts}.
 */
public final class MiniHogwartsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");

    private static final ResourceKey<Biome> FORBIDDEN_FOREST = ResourceKey.create(
            Registries.BIOME, ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "forbidden_forest"));

    /** Blocks placed per server tick. ~1.5M / 15k ≈ 100 ticks (~5s), smooth enough for a one-time drop. */
    private static final int BLOCKS_PER_TICK = 15000;
    /** Don't auto-spawn another castle within this XZ distance of an existing one. */
    private static final int AUTO_SPAWN_SPACING = 1500;
    /** How far from the triggering player to drop an auto-spawned castle (so it isn't on their head). */
    private static final int AUTO_SPAWN_OFFSET = 90;
    /** Sink the schematic's base this far below the sampled surface so its foundation isn't floating. */
    private static final int FOUNDATION_SINK = 4;
    private static final int SETBLOCK_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;

    private static PlacementJob activeJob;

    private MiniHogwartsManager() {
    }

    public static boolean isBusy() {
        return activeJob != null;
    }

    /**
     * Begin building a castle horizontally centered on {@code center} (its base sunk just below the
     * surface there). Returns the recorded locator center, or null if busy or the schematic is missing.
     */
    public static BlockPos requestSpawn(ServerLevel level, BlockPos center) {
        if (activeJob != null) {
            return null;
        }
        MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
        if (schem == null) {
            return null;
        }

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, center.getX(), center.getZ());
        int anchorY = Math.max(level.getMinBuildHeight() + 1, surfaceY - FOUNDATION_SINK);
        BlockPos anchor = new BlockPos(center.getX() - schem.width() / 2, anchorY, center.getZ() - schem.length() / 2);

        BlockPos recordedCenter = new BlockPos(center.getX(), anchorY + schem.height() / 3, center.getZ());
        activeJob = new PlacementJob(level, schem, anchor);
        MiniHogwartsTracker.get(level).add(recordedCenter);
        announce(level, recordedCenter);
        LOGGER.info("Mini Hogwarts placement started: anchor {} {} {}, center {}",
                anchor.getX(), anchor.getY(), anchor.getZ(), recordedCenter);
        return recordedCenter;
    }

    /** Called every server tick (END phase) to advance an in-progress build. */
    public static void tick(MinecraftServer server) {
        if (activeJob == null) {
            return;
        }
        if (activeJob.advance(BLOCKS_PER_TICK)) {
            LOGGER.info("Mini Hogwarts placement complete ({} blocks)", activeJob.total);
            activeJob = null;
        }
    }

    /** Throttled per-player check: build a castle the first time someone is in a Forbidden Forest. */
    public static void maybeAutoSpawn(ServerPlayer player) {
        if (activeJob != null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos from = player.blockPosition();
        if (!level.getBiome(from).is(FORBIDDEN_FOREST)) {
            return;
        }
        BlockPos closest = MiniHogwartsTracker.get(level).findClosest(from);
        if (closest != null) {
            double dx = closest.getX() - from.getX();
            double dz = closest.getZ() - from.getZ();
            if (dx * dx + dz * dz < (double) AUTO_SPAWN_SPACING * AUTO_SPAWN_SPACING) {
                return;
            }
        }
        requestSpawn(level, from.offset(AUTO_SPAWN_OFFSET, 0, AUTO_SPAWN_OFFSET));
    }

    private static void announce(ServerLevel level, BlockPos center) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Component msg = Component.literal("[Hogwarts Magic] A Mini Hogwarts castle is rising in the Forbidden Forest at "
                + center.getX() + ", " + center.getY() + ", " + center.getZ() + " — try /findminihogwarts");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    /** A castle build in progress: walks the schematic cells, placing a budget of blocks per tick. */
    private static final class PlacementJob {
        private final ServerLevel level;
        private final MiniHogwartsSchematic schem;
        private final BlockPos anchor;
        private final int width;
        private final int length;
        private final int total;
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        private int cursor;

        PlacementJob(ServerLevel level, MiniHogwartsSchematic schem, BlockPos anchor) {
            this.level = level;
            this.schem = schem;
            this.anchor = anchor;
            this.width = schem.width();
            this.length = schem.length();
            this.total = schem.volume();
        }

        /** Place up to {@code budget} more blocks; returns true when the whole castle is placed. */
        boolean advance(int budget) {
            int end = Math.min(total, cursor + budget);
            int wl = width * length;
            for (; cursor < end; cursor++) {
                int x = cursor % width;
                int z = (cursor / width) % length;
                int y = cursor / wl;
                BlockState state = schem.blockAt(cursor);
                mutable.set(anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
                level.setBlock(mutable, state, SETBLOCK_FLAGS);
            }
            return cursor >= total;
        }
    }
}
