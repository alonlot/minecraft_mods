package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Digs the Gringotts undercroft into the world, across server ticks, outside worldgen entirely.
 *
 * <p>Same reasoning as {@link com.hogwartsmagic.worldgen.VillageGringottsBank} and
 * {@link com.hogwartsmagic.worldgen.MiniHogwartsManager}: a build this size cannot go in a
 * {@code Feature} without the far-chunk restriction, so it runs from a tick handler with a budget.
 *
 * <p>One thing it does that neither of those has to. Both of them <em>place solids into open air
 * and skip air cells</em> - the bank's own comment says air is skipped so the surrounding terrain is
 * never carved into a box. This does the opposite: it carves air out of solid rock, so every cell
 * of the corridor interior is a real write, and opening that much unlit space at once is a workload
 * the lighting engine has never been given here before. The budget is per block actually written
 * rather than per cell considered, so a pass that mostly skips costs nothing.
 */
public final class UndercroftBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-Undercroft");

    /** Blocks actually written per tick. Two-thirds of what MiniHogwarts uses, for a longer job. */
    private static final int BLOCKS_PER_TICK = 10_000;

    /** How far below the rotunda floor the cart station sits. */
    public static final int SHAFT_DROP = 26;

    /** Never carve within this of the world floor. */
    private static final int ROCK_MARGIN = 8;

    /** The deepest we aim for, and the lowest the chasm floor may sit. */
    private static final int TARGET_BOTTOM = -48;

    /** Half-width of the horizontal box the undercroft is allowed to occupy. */
    public static final int FOOTPRINT_HALF = 48;

    // Order matters. The chasm is opened BEFORE the corridor, so that the corridor's shell pass
    // then lines the line where it crosses the void: a ledge ends up with rock behind it and
    // nothing in front, which is the whole point of it being a ledge. Carving the chasm afterwards
    // would simply delete the corridor.
    private static final int WORK_SHAFT = 0;
    private static final int WORK_CHASM = 1;
    private static final int WORK_CORRIDOR_SHELL = 2;
    private static final int WORK_CORRIDOR_VOID = 3;
    private static final int WORK_CORRIDOR_FURNISH = 4;
    // Vaults last, so they cut their mouths into a corridor wall that already exists.
    private static final int WORK_VAULTS = 5;
    private static final int WORK_DONE = 6;

    @Nullable
    private static Job activeJob;

    private UndercroftBuilder() {
    }

    public static boolean isBusy() {
        return activeJob != null;
    }

    // ------------------------------------------------------------------ starting

    /**
     * Works out how deep an undercroft under {@code stationTop} can go, or -1 if the terrain
     * cannot give it enough room.
     */
    public static int depthFor(ServerLevel level, BlockPos stationTop) {
        int bottom = Math.max(level.getMinBuildHeight() + ROCK_MARGIN,
                Math.min(TARGET_BOTTOM, stationTop.getY() - SHAFT_DROP - CartTrack.DEPTH_MAX));
        int depth = stationTop.getY() - SHAFT_DROP - bottom;
        return depth < CartTrack.DEPTH_MIN ? -1 : Math.min(depth, CartTrack.DEPTH_MAX);
    }

    /**
     * Starts (or resumes) a carve. Returns null if one is already running, the terrain is too
     * shallow, or the footprint is not resident - never forcing chunk generation from a tick.
     */
    @Nullable
    public static UndercroftTracker.Undercroft request(ServerLevel level, BlockPos stationTop) {
        if (activeJob != null) {
            return null;
        }
        int depth = depthFor(level, stationTop);
        UndercroftTracker tracker = UndercroftTracker.get(level);
        if (depth < 0) {
            LOGGER.info("No undercroft at {}: only {} blocks of rock beneath it, {} needed",
                    stationTop, stationTop.getY() - SHAFT_DROP - level.getMinBuildHeight(),
                    CartTrack.DEPTH_MIN);
            tracker.put(stationTop, 0, UndercroftTracker.REFUSED);
            return null;
        }
        if (!footprintResident(level, stationTop)) {
            tracker.put(stationTop, depth, UndercroftTracker.PENDING);
            return null;
        }

        UndercroftTracker.Undercroft site = tracker.at(stationTop);
        if (site == null) {
            site = tracker.put(stationTop, depth, UndercroftTracker.CARVING);
        } else if (site.stage == UndercroftTracker.COMPLETE || site.stage == UndercroftTracker.REFUSED) {
            return null;
        }
        site.stage = UndercroftTracker.CARVING;
        activeJob = new Job(level, site);
        LOGGER.info("Undercroft starting at {} depth {} (resuming at item {} cursor {})",
                stationTop, site.depth, site.workItem, site.cursor);
        return site;
    }

    /** Every column of the footprint must already be loaded before a single block is written. */
    private static boolean footprintResident(ServerLevel level, BlockPos centre) {
        for (int cx = (centre.getX() - FOOTPRINT_HALF) >> 4;
             cx <= (centre.getX() + FOOTPRINT_HALF) >> 4; cx++) {
            for (int cz = (centre.getZ() - FOOTPRINT_HALF) >> 4;
                 cz <= (centre.getZ() + FOOTPRINT_HALF) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(MinecraftServer server) {
        Job job = activeJob;
        if (job == null) {
            return;
        }
        try {
            if (job.advance()) {
                UndercroftTracker.get(job.level)
                        .progress(job.site, WORK_DONE, 0L, UndercroftTracker.COMPLETE);
                spawnCart(job.level, job.trackOrigin, job.site.depth);
                spawnDragon(job.level, job.vaults.deepestChamberCentre());
                com.hogwartsmagic.gringotts.GringottsAlarm.get(job.level)
                        .addRegion(job.site.station, FOOTPRINT_HALF);
                announce(job.level, job.site.station);
                LOGGER.info("Undercroft complete at {}: {} blocks written",
                        job.site.station, job.written);
                activeJob = null;
            }
        } catch (Exception e) {
            LOGGER.error("Undercroft carve failed; abandoning and keeping the cursor", e);
            UndercroftTracker.get(job.level)
                    .progress(job.site, job.item, job.cursor, UndercroftTracker.CARVING);
            activeJob = null;
        }
    }

    /** Picks up anything left half-dug, when somebody wanders back into range of it. */
    public static void maybeResume(ServerPlayer player) {
        if (activeJob != null || player.tickCount % 100 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        UndercroftTracker.Undercroft site =
                UndercroftTracker.get(level).nearestUnfinished(player.blockPosition(), 160.0D);
        if (site != null) {
            request(level, site.station);
        }
    }

    /** Leaves a cart standing on the platform, bound to the line that was just dug. */
    private static void spawnCart(ServerLevel level, BlockPos trackOrigin, int depth) {
        com.hogwartsmagic.entity.GringottsCartEntity cart =
                com.hogwartsmagic.entity.ModEntities.GRINGOTTS_CART.get().create(level);
        if (cart == null) {
            return;
        }
        cart.bind(trackOrigin, depth);
        level.addFreshEntity(cart);
        LOGGER.info("Cart waiting at the head of the line, bound to {} depth {}",
                trackOrigin, depth);
    }

    /** Puts the Ironbelly in front of the deepest vault, chained to the spot it stands on. */
    private static void spawnDragon(ServerLevel level, Vec3 where) {
        com.hogwartsmagic.entity.UkrainianIronbellyEntity dragon =
                com.hogwartsmagic.entity.ModEntities.UKRAINIAN_IRONBELLY.get().create(level);
        if (dragon == null) {
            return;
        }
        dragon.moveTo(where.x, where.y, where.z, 0.0F, 0.0F);
        dragon.setAnchor(BlockPos.containing(where));
        dragon.setPersistenceRequired();
        level.addFreshEntity(dragon);
        LOGGER.info("Ironbelly chained in front of the deepest vault at {}", where);
    }

    private static void announce(ServerLevel level, BlockPos station) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Component msg = Component.literal(
                "[Hogwarts Magic] Something under Gringotts has been opened up at "
                        + station.getX() + ", " + station.getY() + ", " + station.getZ());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    // ------------------------------------------------------------------- the job

    private static final class Job {
        private final ServerLevel level;
        private final UndercroftTracker.Undercroft site;
        private final CarveSink sink;
        private final StationCarver station;
        private final ChasmCarver chasm;
        private final CorridorCarver corridor;
        private final VaultCarver vaults;

        private final BlockPos trackOrigin;
        private int item;
        private long cursor;
        private int written;

        Job(ServerLevel level, UndercroftTracker.Undercroft site) {
            this.level = level;
            this.site = site;
            BlockPos top = site.station;
            int bottomY = top.getY() - SHAFT_DROP;
            UndercroftPalette palette = new UndercroftPalette(top.getY());

            this.sink = new CarveSink.Level(level, level.getMinBuildHeight() + ROCK_MARGIN);
            this.station = new StationCarver(top, bottomY, palette);
            // Place the line so its very first block IS the foot of the shaft, rather than
            // wherever the spiral happens to begin relative to its own origin.
            BlockPos origin = CartTrack.originForStart(
                    new BlockPos(top.getX(), bottomY, top.getZ()), site.depth);
            this.trackOrigin = origin;
            CartTrack track = CartTrack.build(origin, site.depth);
            this.chasm = new ChasmCarver(track, palette);
            this.corridor = new CorridorCarver(track, palette);
            this.vaults = new VaultCarver(track, palette);

            this.item = site.workItem;
            this.cursor = site.cursor;
        }

        /** Returns true when everything is carved. */
        boolean advance() {
            int budget = BLOCKS_PER_TICK;
            int before = this.sink.written();

            while (this.item < WORK_DONE && this.sink.written() - before < budget) {
                int steps = stepsFor(this.item);
                if (this.cursor >= steps) {
                    this.item++;
                    this.cursor = 0L;
                    continue;
                }
                carve(this.item, (int) this.cursor);
                this.cursor++;
            }

            this.written += this.sink.written() - before;
            UndercroftTracker.get(this.level)
                    .progress(this.site, this.item, this.cursor, UndercroftTracker.CARVING);
            return this.item >= WORK_DONE;
        }

        private int stepsFor(int item) {
            if (item == WORK_SHAFT) {
                return this.station.stepCount();
            }
            if (item == WORK_CHASM) {
                return this.chasm.stepCount();
            }
            return item == WORK_VAULTS ? this.vaults.stepCount() : this.corridor.stepCount();
        }

        private void carve(int item, int step) {
            if (item == WORK_SHAFT) {
                this.station.carveStep(this.sink, step);
            } else if (item == WORK_CHASM) {
                this.chasm.carveStep(this.sink, step);
            } else if (item == WORK_VAULTS) {
                this.vaults.carveStep(this.sink, step);
            } else if (item == WORK_CORRIDOR_SHELL) {
                this.corridor.carveStep(this.sink, CorridorCarver.PASS_SHELL, step);
            } else if (item == WORK_CORRIDOR_VOID) {
                this.corridor.carveStep(this.sink, CorridorCarver.PASS_VOID, step);
            } else {
                this.corridor.carveStep(this.sink, CorridorCarver.PASS_FURNISH, step);
            }
        }
    }

    /** Where the cart line starts in the world, for anything that needs to find it. */
    public static Vec3 stationPlatform(BlockPos stationTop) {
        return new Vec3(stationTop.getX() + 0.5D, stationTop.getY() - SHAFT_DROP + 1.0D,
                stationTop.getZ() + 0.5D);
    }
}
