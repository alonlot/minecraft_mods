package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GoblinEntity;
import com.hogwartsmagic.entity.GoblinGuardEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
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
 * Drops a Gringotts branch office into some villages: a small marble-and-gold
 * bank staffed by a goblin teller with two guards on the door.
 *
 * <p>Built the same way as {@link MiniHogwartsManager}: the whole structure is
 * planned up front into a placement list, then drained a few hundred blocks per
 * server tick. Placement happens outside worldgen entirely, so there is no
 * far-chunk generation crash and no single-tick stall.
 *
 * <p>Village detection deliberately uses {@link ServerLevel#isVillage} and the
 * meeting-point POI rather than {@code StructureManager.getStructureWithPieceAt}.
 * The structure lookup walks structure references and calls
 * {@code level.getChunk(..., ChunkStatus.STRUCTURE_STARTS)}, which force-loads
 * and generates chunks synchronously - running that on a player tick froze the
 * server when a player teleported into fresh terrain. The POI path only reads
 * data that is already resident.
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

    /** Blocks committed per server tick while a branch is going up. */
    private static final int BLOCKS_PER_TICK = 250;

    // Footprint: 11 wide, 9 deep, walls 5 tall with a roof and pediment above.
    private static final int WIDTH = 11;
    private static final int DEPTH = 9;
    private static final int WALL_HEIGHT = 5;

    /** One block of clearance is required all round the footprint. */
    private static final int MARGIN = 1;

    /** Largest tolerated ground height spread across the footprint. */
    private static final int MAX_GROUND_SPREAD = 2;

    /** Hard ceiling on candidate sites examined per attempt, to bound the scan. */
    private static final int MAX_CANDIDATES = 120;

    private static final int SETBLOCK_FLAGS = Block.UPDATE_CLIENTS;

    private static final BlockState MARBLE = Blocks.QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** At most one branch goes up at a time, server-wide. */
    @Nullable
    private static PlacementJob activeJob;

    public static boolean isBusy() {
        return activeJob != null;
    }

    // ------------------------------------------------------------------ ticking

    /** Called every server tick (END phase) to advance an in-progress build. */
    public static void tick(MinecraftServer server) {
        PlacementJob job = activeJob;
        if (job == null) {
            return;
        }
        try {
            if (job.advance(BLOCKS_PER_TICK)) {
                job.staff();
                VillageBankTracker tracker = VillageBankTracker.get(job.level);
                tracker.markVisited(job.villageCenter);
                tracker.addBank(job.site.origin);
                announce(job.level, job.site.origin);
                LOGGER.info("Gringotts branch complete at {} {} {} ({} blocks)",
                        job.site.origin.getX(), job.site.origin.getY(), job.site.origin.getZ(),
                        job.placements.size());
                activeJob = null;
            }
        } catch (Exception e) {
            // Never let a build fault take the server down with it.
            LOGGER.error("Gringotts branch placement failed; abandoning job", e);
            activeJob = null;
        }
    }

    /**
     * Throttled per-player check. Cheap to reject: the village test reads POI
     * data only, and everything else is behind it.
     */
    public static void maybeBuild(ServerPlayer player) {
        if (activeJob != null || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        try {
            attemptBuild(player);
        } catch (Exception e) {
            LOGGER.error("Gringotts branch site search failed", e);
        }
    }

    private static void attemptBuild(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();

        // Cheap POI-backed test - the same one vanilla raids use.
        if (!level.isVillage(playerPos)) {
            return;
        }

        BlockPos center = findVillageCenter(level, playerPos);
        if (center == null) {
            return;
        }

        VillageBankTracker tracker = VillageBankTracker.get(level);
        if (tracker.hasVisited(center)) {
            return;
        }

        if (!villageGetsBank(level, center)) {
            // A settled "no" - record it so this village is never re-rolled.
            tracker.markVisited(center);
            return;
        }

        Site site = findSite(level, center);
        if (site == null) {
            // Usually just chunks that are not resident yet. Deliberately not
            // marked visited, so walking further into the village retries rather
            // than costing the village its bank forever.
            return;
        }

        RandomSource rng = RandomSource.create(level.getSeed() ^ center.asLong());
        activeJob = new PlacementJob(level, site, center, rng);
        LOGGER.info("Gringotts branch starting at {} {} {} facing {}",
                site.origin.getX(), site.origin.getY(), site.origin.getZ(), site.facing);
    }

    /**
     * The village bell (meeting point POI) is the village's identity and centre.
     * Reads resident POI data; does not force chunk generation.
     */
    @Nullable
    private static BlockPos findVillageCenter(ServerLevel level, BlockPos near) {
        Optional<BlockPos> bell = level.getPoiManager().findClosest(
                holder -> holder.is(PoiTypes.MEETING), near, BELL_SEARCH_RADIUS,
                PoiManager.Occupancy.ANY);
        return bell.orElse(null);
    }

    /**
     * Deterministic per village and per world: the same village in the same
     * world always gets the same answer, regardless of who walks in first.
     */
    private static boolean villageGetsBank(ServerLevel level, BlockPos center) {
        RandomSource rng = RandomSource.create(level.getSeed() ^ (center.asLong() * 0x9E3779B97F4A7C15L));
        return rng.nextInt(BANK_CHANCE_DENOMINATOR) == 0;
    }

    // ------------------------------------------------------------- site search

    /** An accepted build location: the front-left floor corner plus a facing. */
    private record Site(BlockPos origin, Direction facing) {
    }

    /**
     * Looks for a flat, unbuilt patch near the bell, working outward so the bank
     * lands among the houses rather than in a field. The entrance is turned to
     * face back toward the village centre.
     */
    @Nullable
    private static Site findSite(ServerLevel level, BlockPos center) {
        int examined = 0;
        for (int radius = 10; radius <= 38; radius += 4) {
            for (int angleStep = 0; angleStep < 16; angleStep++) {
                if (++examined > MAX_CANDIDATES) {
                    return null;
                }
                double angle = angleStep * Math.PI / 8.0D;
                int cx = center.getX() + (int) Math.round(Math.cos(angle) * radius);
                int cz = center.getZ() + (int) Math.round(Math.sin(angle) * radius);

                Direction facing = facingToward(cx, cz, center);
                Site candidate = siteAt(level, cx, cz, facing);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** The cardinal direction pointing from (x, z) back toward the village centre. */
    private static Direction facingToward(int x, int z, BlockPos center) {
        int dx = center.getX() - x;
        int dz = center.getZ() - z;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Validates a candidate centred on (cx, cz): every column must be resident,
     * standing on natural ground, free of fluid, and level to within
     * {@link #MAX_GROUND_SPREAD}.
     */
    @Nullable
    private static Site siteAt(ServerLevel level, int cx, int cz, Direction facing) {
        boolean rotated = facing.getAxis() == Direction.Axis.X;
        int spanX = (rotated ? DEPTH : WIDTH) + MARGIN * 2;
        int spanZ = (rotated ? WIDTH : DEPTH) + MARGIN * 2;
        int minX = cx - spanX / 2;
        int minZ = cz - spanZ / 2;

        // Bail before touching any block if the footprint is not fully resident.
        if (!level.hasChunkAt(new BlockPos(minX, level.getMinBuildHeight(), minZ))
                || !level.hasChunkAt(new BlockPos(minX + spanX, level.getMinBuildHeight(), minZ + spanZ))
                || !level.hasChunkAt(new BlockPos(minX, level.getMinBuildHeight(), minZ + spanZ))
                || !level.hasChunkAt(new BlockPos(minX + spanX, level.getMinBuildHeight(), minZ))) {
            return null;
        }

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (int x = minX; x < minX + spanX; x++) {
            for (int z = minZ; z < minZ + spanZ; z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                if (!isNaturalGround(level.getBlockState(ground))) {
                    return null;
                }
                if (!level.getFluidState(ground).is(Fluids.EMPTY)) {
                    return null;
                }
                lowest = Math.min(lowest, surface);
                highest = Math.max(highest, surface);
                if (highest - lowest > MAX_GROUND_SPREAD) {
                    return null;
                }
            }
        }

        // The floor replaces the top ground block, so it sits flush with grade.
        BlockPos floorCorner = frontLeftCorner(minX + MARGIN, lowest - 1, minZ + MARGIN, facing);
        return new Site(floorCorner, facing);
    }

    /**
     * Converts the min-corner of the axis-aligned footprint into the local-space
     * origin (local 0,0,0 = front-left of the floor) for the given facing.
     */
    private static BlockPos frontLeftCorner(int minX, int y, int minZ, Direction facing) {
        if (facing == Direction.NORTH) {
            return new BlockPos(minX, y, minZ);
        }
        if (facing == Direction.SOUTH) {
            return new BlockPos(minX + WIDTH - 1, y, minZ + DEPTH - 1);
        }
        if (facing == Direction.WEST) {
            return new BlockPos(minX, y, minZ + WIDTH - 1);
        }
        return new BlockPos(minX + DEPTH - 1, y, minZ);
    }

    /** Only build on untouched terrain - this also keeps us off village roads. */
    private static boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.STONE)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.TERRACOTTA)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MYCELIUM);
    }

    private static void announce(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Component msg = Component.literal("[Hogwarts Magic] A Gringotts branch has opened at "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " — try /findgringotts");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    // ---------------------------------------------------------------- the job

    /**
     * A branch under construction. The full block list is planned in the
     * constructor - which is only a few thousand cheap reads - and then committed
     * a slice at a time so no single tick carries the whole build.
     */
    private static final class PlacementJob {
        private final ServerLevel level;
        private final Site site;
        private final BlockPos villageCenter;
        private final RandomSource rng;
        private final List<Placement> placements = new ArrayList<>();
        private final List<BlockPos> chestPositions = new ArrayList<>();
        private int cursor;

        private record Placement(BlockPos pos, BlockState state) {
        }

        PlacementJob(ServerLevel level, Site site, BlockPos villageCenter, RandomSource rng) {
            this.level = level;
            this.site = site;
            this.villageCenter = villageCenter;
            this.rng = rng;
            plan();
        }

        /** Commits up to {@code budget} blocks; returns true when finished. */
        boolean advance(int budget) {
            int end = Math.min(this.cursor + budget, this.placements.size());
            for (; this.cursor < end; this.cursor++) {
                Placement p = this.placements.get(this.cursor);
                this.level.setBlock(p.pos, p.state, SETBLOCK_FLAGS);
            }
            if (this.cursor < this.placements.size()) {
                return false;
            }
            fillChests();
            return true;
        }

        private BlockPos world(int dx, int dy, int dz) {
            BlockPos o = this.site.origin;
            Direction f = this.site.facing;
            if (f == Direction.NORTH) {
                return new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
            }
            if (f == Direction.SOUTH) {
                return new BlockPos(o.getX() - dx, o.getY() + dy, o.getZ() - dz);
            }
            if (f == Direction.WEST) {
                return new BlockPos(o.getX() + dz, o.getY() + dy, o.getZ() - dx);
            }
            return new BlockPos(o.getX() - dz, o.getY() + dy, o.getZ() + dx);
        }

        private void put(int dx, int dy, int dz, BlockState state) {
            this.placements.add(new Placement(world(dx, dy, dz), state));
        }

        private void plan() {
            planShell();
            planFacade();
            planRoof();
            planInterior();
        }

        /** Floor, foundation skirt, walls, hollow interior. */
        private void planShell() {
            for (int dx = 0; dx < WIDTH; dx++) {
                for (int dz = 0; dz < DEPTH; dz++) {
                    put(dx, 0, dz, FLOOR);

                    // Carry the foundation down to solid ground on sloped sites.
                    BlockPos below = world(dx, -1, dz);
                    for (int drop = 0; drop < 6; drop++) {
                        BlockPos p = below.below(drop);
                        if (this.level.getBlockState(p).isSolid()) {
                            break;
                        }
                        this.placements.add(new Placement(p, Blocks.STONE_BRICKS.defaultBlockState()));
                    }

                    boolean edge = dx == 0 || dx == WIDTH - 1 || dz == 0 || dz == DEPTH - 1;
                    for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                        if (edge) {
                            boolean corner = (dx == 0 || dx == WIDTH - 1) && (dz == 0 || dz == DEPTH - 1);
                            put(dx, dy, dz, corner ? PILLAR : MARBLE);
                        } else {
                            put(dx, dy, dz, AIR);
                        }
                    }
                }
            }
        }

        /** Colonnade, doorway and threshold across the front (dz == 0) face. */
        private void planFacade() {
            for (int dx = 1; dx < WIDTH - 1; dx++) {
                for (int dy = 1; dy < WALL_HEIGHT; dy++) {
                    put(dx, dy, 0, AIR);
                }
            }
            for (int col : new int[]{1, 3, 7, 9}) {
                for (int dy = 1; dy < WALL_HEIGHT; dy++) {
                    put(col, dy, 0, PILLAR);
                }
                put(col, WALL_HEIGHT - 1, 0, CHISELED);
            }
            for (int dx = 4; dx <= 6; dx++) {
                put(dx, WALL_HEIGHT, 0, GOLD);
            }

            // Threshold row. The floor already sits flush with the surrounding
            // ground, so this is trim rather than stairs - and it stays inside
            // the one-block margin the site search validated.
            for (int dx = 2; dx <= WIDTH - 3; dx++) {
                put(dx, 0, -1, FLOOR);
                put(dx, 1, -1, AIR);
            }
            put(5, 0, -1, GOLD);

            BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            put(3, WALL_HEIGHT - 1, 1, lantern);
            put(7, WALL_HEIGHT - 1, 1, lantern);
        }

        /** Flat marble roof with a gold cornice and a pediment over the door. */
        private void planRoof() {
            for (int dx = -1; dx <= WIDTH; dx++) {
                for (int dz = -1; dz <= DEPTH; dz++) {
                    boolean overhang = dx < 0 || dx >= WIDTH || dz < 0 || dz >= DEPTH;
                    put(dx, WALL_HEIGHT + 1, dz, overhang ? GOLD : MARBLE);
                }
            }
            for (int dx = 3; dx <= 7; dx++) {
                put(dx, WALL_HEIGHT + 2, 0, CHISELED);
            }
            for (int dx = 4; dx <= 6; dx++) {
                put(dx, WALL_HEIGHT + 3, 0, CHISELED);
            }
            put(5, WALL_HEIGHT + 4, 0, GOLD);
        }

        /** Teller counter, vault grille, strongboxes and lighting. */
        private void planInterior() {
            for (int dx = 2; dx <= WIDTH - 3; dx++) {
                put(dx, 1, DEPTH - 4, FLOOR);
                put(dx, 2, DEPTH - 4, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            }
            put(3, 2, DEPTH - 4, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());
            put(WIDTH - 4, 2, DEPTH - 4, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState());

            for (int dx = 3; dx <= WIDTH - 4; dx++) {
                for (int dy = 1; dy <= 3; dy++) {
                    put(dx, dy, DEPTH - 1, Blocks.IRON_BARS.defaultBlockState());
                }
            }
            put(2, 1, DEPTH - 1, GOLD);
            put(WIDTH - 3, 1, DEPTH - 1, GOLD);

            BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, this.site.facing);
            for (int dx : new int[]{3, WIDTH - 4}) {
                put(dx, 1, DEPTH - 2, chest);
                this.chestPositions.add(world(dx, 1, DEPTH - 2));
            }

            BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            put(2, WALL_HEIGHT, 3, lantern);
            put(WIDTH - 3, WALL_HEIGHT, 3, lantern);
            put(5, WALL_HEIGHT, DEPTH - 3, lantern);

            for (int dz = 1; dz < DEPTH - 3; dz++) {
                put(5, 0, dz, GOLD);
            }
        }

        /** Stock the strongboxes once their block entities exist. */
        private void fillChests() {
            for (BlockPos pos : this.chestPositions) {
                if (!(this.level.getBlockEntity(pos) instanceof Container container)) {
                    continue;
                }
                container.setItem(0, new ItemStack(ExampleMod.GALLEON.get(), 6 + this.rng.nextInt(11)));
                container.setItem(2, new ItemStack(Items.GOLD_INGOT, 2 + this.rng.nextInt(4)));
                container.setItem(4, new ItemStack(Items.GOLD_NUGGET, 4 + this.rng.nextInt(9)));
                if (this.rng.nextInt(3) == 0) {
                    container.setItem(6, new ItemStack(Items.EMERALD, 1 + this.rng.nextInt(3)));
                }
                if (this.rng.nextInt(6) == 0) {
                    container.setItem(8, new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get()));
                }
            }
        }

        /** One teller behind the counter, two guards on the door. */
        void staff() {
            GoblinEntity teller = ModEntities.GOBLIN.get().create(this.level);
            if (teller != null) {
                BlockPos pos = world(5, 1, DEPTH - 3);
                teller.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                        this.site.facing.toYRot(), 0.0F);
                teller.makeTeller("Gringotts Teller");
                this.level.addFreshEntity(teller);
            }

            for (int dx : new int[]{2, WIDTH - 3}) {
                GoblinGuardEntity guard = ModEntities.GOBLIN_GUARD.get().create(this.level);
                if (guard == null) {
                    continue;
                }
                BlockPos pos = world(dx, 1, 2);
                guard.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                        this.site.facing.toYRot(), 0.0F);
                guard.makeGuard("Vault Guard");
                guard.setItemSlot(EquipmentSlot.MAINHAND,
                        new ItemStack(ExampleMod.GOBLIN_SILVER_DAGGER.get()));
                guard.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
                this.level.addFreshEntity(guard);
            }
        }
    }
}
