package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.ClassroomSupplies;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * Don't auto-spawn another castle within this XZ distance of an existing one. Wide on purpose:
     * the Forbidden Forest now covers far more ground, so a short spacing would hand out a castle
     * every time you crossed one. At this range finding a second is a journey, not a stroll.
     */
    private static final int AUTO_SPAWN_SPACING = 6000;
    /** How far from the triggering player to drop an auto-spawned castle (so it isn't on their head). */
    private static final int AUTO_SPAWN_OFFSET = 90;
    /** Tuck the base this far below the HIGHEST ground under the footprint so it sits on the hill, not buried. */
    private static final int FOUNDATION_SINK = 2;
    /** Max depth of the stone support skirt filled under floating parts (only fills air, stops at ground). */
    private static final int MAX_SKIRT_DEPTH = 64;
    private static final BlockState FOUNDATION_FILLER = Blocks.STONE.defaultBlockState();
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

        // Anchor the base to the HIGHEST ground under the footprint (sampled on a 3x3 grid), so the castle
        // sits on top of the hill rather than burying its lower floors; the support skirt fills under the
        // downhill side. Sample ground excluding tree leaves so we don't perch it on the canopy.
        int w = schem.width();
        int l = schem.length();
        int maxGround = Integer.MIN_VALUE;
        for (int sx = -w / 2; sx <= w / 2; sx += w / 2) {
            for (int sz = -l / 2; sz <= l / 2; sz += l / 2) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + sx, center.getZ() + sz);
                if (h > level.getMinBuildHeight() + 1) {
                    maxGround = Math.max(maxGround, h);
                }
            }
        }
        if (maxGround == Integer.MIN_VALUE) {
            maxGround = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, center.getX(), center.getZ());
        }
        int anchorY = Math.max(level.getMinBuildHeight() + 1, maxGround - FOUNDATION_SINK);
        BlockPos anchor = new BlockPos(center.getX() - w / 2, anchorY, center.getZ() - l / 2);

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
        PlacementJob job = activeJob;
        if (job == null) {
            return;
        }
        try {
            if (job.advance(BLOCKS_PER_TICK)) {
                job.furnish(); // one-shot: themed chests + decorations in the landmark rooms
                job.spawnStudents(); // populate the finished castle with a crowd of students
                job.spawnHouseElves(); // and a few elves below stairs
                LOGGER.info("Mini Hogwarts placement complete ({} blocks)", job.total);
                activeJob = null;
            }
        } catch (Exception e) {
            LOGGER.error("Mini Hogwarts placement failed; abandoning job", e);
            activeJob = null;
        }
    }

    /** Drops any in-progress job. Called when the server stops, so a job never outlives its level. */
    public static void reset() {
        activeJob = null;
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

    /**
     * A castle build in progress, run across ticks in two phases:
     *   1. Solids — place every NON-air schematic block (air is skipped, so the surrounding terrain
     *      is never carved into a box and nothing to the side is destroyed).
     *   2. Skirt — under each footprint column, fill a stone support straight down from the castle's
     *      lowest block until it meets the ground. Only AIR is filled, so the castle never floats and
     *      no existing side terrain is replaced.
     */
    private static final class PlacementJob {
        private final ServerLevel level;
        private final MiniHogwartsSchematic schem;
        private final BlockPos anchor;
        private final int width;
        private final int length;
        private final int total;
        /** Lowest non-air schematic Y per column (z*width + x), or -1 if the column is all air. */
        private final int[] lowestSolidY;
        // Landmark locals found during analysis (-1 / 0-count when absent).
        private int enchantX = -1, enchantY = -1, enchantZ = -1;
        private long bookSumX, bookSumY, bookSumZ;
        private int bookCount;
        private long ironSumX, ironSumY, ironSumZ;
        private int ironCount;
        private int topX = -1, topY = -1, topZ = -1; // highest solid block (for a tower treasure)
        /** Chests in the schematic itself (local x, y, z): the Potions classroom's ingredient chests. */
        private final List<BlockPos> schematicChests = new ArrayList<>();
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        private int cursor;       // phase 1: cell index
        private int columnCursor; // phase 2: column index

        PlacementJob(ServerLevel level, MiniHogwartsSchematic schem, BlockPos anchor) {
            this.level = level;
            this.schem = schem;
            this.anchor = anchor;
            this.width = schem.width();
            this.length = schem.length();
            this.total = schem.volume();
            this.lowestSolidY = new int[width * length];
            analyze();
        }

        /** Single pass: lowest-solid per column + landmark locations (enchanting room, library, dungeon, tower). */
        private void analyze() {
            int wl = width * length;
            java.util.Arrays.fill(lowestSolidY, -1);
            for (int idx = 0; idx < total; idx++) {
                BlockState state = schem.blockAt(idx);
                if (state.isAir()) {
                    continue;
                }
                int col = idx % wl;
                int y = idx / wl;
                if (lowestSolidY[col] == -1) {
                    lowestSolidY[col] = y; // y-ascending iteration → first hit is lowest
                }
                int x = col % width;
                int z = col / width;
                if (state.is(Blocks.ENCHANTING_TABLE) && enchantX == -1) {
                    enchantX = x;
                    enchantY = y;
                    enchantZ = z;
                } else if (state.is(Blocks.BOOKSHELF)) {
                    bookSumX += x;
                    bookSumY += y;
                    bookSumZ += z;
                    bookCount++;
                } else if (state.is(Blocks.CHEST)) {
                    schematicChests.add(new BlockPos(x, y, z));
                } else if (state.is(Blocks.IRON_BARS)) {
                    ironSumX += x;
                    ironSumY += y;
                    ironSumZ += z;
                    ironCount++;
                }
                if (y > topY) {
                    topY = y;
                    topX = x;
                    topZ = z;
                }
            }
        }

        /** Place up to roughly {@code budget} blocks; returns true when the whole castle (and skirt) is done. */
        boolean advance(int budget) {
            int wl = width * length;
            int placed = 0;

            // Phase 1: place solid blocks only.
            while (cursor < total && placed < budget) {
                BlockState state = schem.blockAt(cursor);
                if (!state.isAir()) {
                    int x = cursor % width;
                    int z = (cursor / width) % length;
                    int y = cursor / wl;
                    mutable.set(anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
                    level.setBlock(mutable, state, SETBLOCK_FLAGS);
                    placed++;
                }
                cursor++;
            }
            if (cursor < total) {
                return false;
            }

            // Phase 2: foundation skirt. Whole columns at a time (small overshoot of budget is fine).
            while (columnCursor < wl && placed < budget) {
                int low = lowestSolidY[columnCursor];
                if (low >= 0) {
                    int x = columnCursor % width;
                    int z = columnCursor / width;
                    int wx = anchor.getX() + x;
                    int wz = anchor.getZ() + z;
                    int y = anchor.getY() + low - 1;
                    int floor = level.getMinBuildHeight() + 1;
                    for (int depth = 0; depth < MAX_SKIRT_DEPTH && y > floor; depth++, y--) {
                        mutable.set(wx, y, wz);
                        if (!level.getBlockState(mutable).isAir()) {
                            break; // reached solid ground — stop, never replace existing terrain
                        }
                        level.setBlock(mutable, FOUNDATION_FILLER, SETBLOCK_FLAGS);
                        placed++;
                    }
                }
                columnCursor++;
            }
            return columnCursor >= wl;
        }

        // ── Furnishing: themed chests + decorations in the landmark rooms ──────────────────

        /** Runs once after the castle is fully placed. Each item only lands on an empty floor tile. */
        void furnish() {
            RandomSource rng = level.getRandom();
            int placed = 0;

            // Library — wherever the bookshelves cluster.
            if (bookCount > 0) {
                BlockPos spot = floorSpotNear(bookSumX / bookCount, bookSumY / bookCount, bookSumZ / bookCount, 6);
                if (placeChest(spot, Theme.LIBRARY, rng)) {
                    placed++;
                    decoNear(spot, Blocks.LECTERN.defaultBlockState());
                    decoNear(spot, Blocks.LANTERN.defaultBlockState());
                }
                placePublicPensieve(bookSumX / bookCount, bookSumY / bookCount, bookSumZ / bookCount);
            }
            // Spell study — beside the enchanting table.
            if (enchantX >= 0) {
                BlockPos spot = floorSpotNear(enchantX, enchantY, enchantZ, 5);
                if (placeChest(spot, Theme.SPELL_STUDY, rng)) {
                    placed++;
                    decoNear(spot, Blocks.LECTERN.defaultBlockState());
                    decoNear(spot, Blocks.LANTERN.defaultBlockState());
                }
            }
            // Dungeon — among the iron bars.
            if (ironCount > 0) {
                BlockPos spot = floorSpotNear(ironSumX / ironCount, ironSumY / ironCount, ironSumZ / ironCount, 6);
                if (placeChest(spot, Theme.DUNGEON, rng)) {
                    placed++;
                    decoNear(spot, Blocks.COBWEB.defaultBlockState());
                    decoNear(spot, Blocks.TORCH.defaultBlockState());
                }
            }
            // Great Hall — the ground floor at the footprint center, with a short carpet runner.
            BlockPos hall = floorSpotNear(width / 2, 1, length / 2, 10);
            if (placeChest(hall, Theme.GREAT_HALL, rng)) {
                placed++;
                layCarpetRunner(hall);
                decoNear(hall, Blocks.LANTERN.defaultBlockState());
                decoNear(hall, Blocks.FLOWER_POT.defaultBlockState());
            }
            // Tower treasure — a hidden pocket near the very top.
            if (topX >= 0) {
                BlockPos spot = floorSpotNear(topX, topY - 2, topZ, 6);
                if (placeChest(spot, Theme.TOWER, rng)) {
                    placed++;
                    decoNear(spot, Blocks.LANTERN.defaultBlockState());
                }
            }
            LOGGER.info("Mini Hogwarts furnished with {} themed chests", placed);
            int stocked = stockClassroomChests(rng);
            LOGGER.info("Mini Hogwarts Potions classroom: {} ingredient chests stocked", stocked);
        }

        /**
         * Fills every chest that came from the schematic (the Potions classroom's) with one roll of
         * ClassroomSupplies, in shuffled slots. Returns how many chests were stocked. A chest whose
         * chunk is not loaded is skipped (never load a chunk).
         */
        private int stockClassroomChests(RandomSource rng) {
            int stocked = 0;
            for (BlockPos local : schematicChests) {
                BlockPos pos = anchor.offset(local);
                if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                    continue;
                }
                List<ClassroomSupplies.Stack> stacks = ClassroomSupplies.roll(
                        (min, max) -> min + rng.nextInt(max - min + 1), rng.nextDouble());
                int[] slots = shuffledSlots(chest.getContainerSize(), rng);
                int next = 0;
                for (ClassroomSupplies.Stack stack : stacks) {
                    Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(stack.item())).orElse(null);
                    if (item == null || next >= slots.length) {
                        continue;
                    }
                    chest.setItem(slots[next++], new ItemStack(item, stack.count()));
                }
                chest.setChanged();
                stocked++;
            }
            return stocked;
        }

        /** The slot numbers 0..size-1 in a random order. */
        private static int[] shuffledSlots(int size, RandomSource rng) {
            int[] slots = new int[size];
            for (int i = 0; i < size; i++) {
                slots[i] = i;
            }
            for (int i = size - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int swap = slots[i];
                slots[i] = slots[j];
                slots[j] = swap;
            }
            return slots;
        }

        /** Populate the finished castle with a crowd of wandering Hogwarts students across its floors. */
        void spawnStudents() {
            RandomSource rng = level.getRandom();
            final int target = 32;
            int spawned = 0;
            int h = schem.height();
            int[] floorYs = { 2, Math.max(2, h / 4), Math.max(2, h / 2), Math.max(2, (h * 3) / 4) };
            int stepX = Math.max(5, width / 7);
            int stepZ = Math.max(5, length / 7);
            outer:
            for (int ly : floorYs) {
                for (int lx = stepX / 2; lx < width; lx += stepX) {
                    for (int lz = stepZ / 2; lz < length; lz += stepZ) {
                        if (spawned >= target) {
                            break outer;
                        }
                        BlockPos spot = floorSpotNear(lx, ly, lz, 5);
                        if (spot == null || !level.getBlockState(spot.above()).isAir()) {
                            continue; // need head room for the student
                        }
                        com.hogwartsmagic.entity.StudentEntity student =
                                com.hogwartsmagic.entity.ModEntities.STUDENT.get().create(level);
                        if (student == null) {
                            continue;
                        }
                        student.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                                rng.nextFloat() * 360.0F, 0.0F);
                        student.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                                net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
                        student.setPersistenceRequired();
                        level.addFreshEntity(student);
                        spawned++;
                    }
                }
            }
            LOGGER.info("Mini Hogwarts populated with {} students", spawned);
        }

        /**
         * Tuck a few house elves into the castle. The kitchens are where they
         * belong, and finding one indoors is a good deal kinder than hunting the
         * Forbidden Forest for the single wild one. They spawn wild, so they can
         * still be taken into service the hard way.
         */
        void spawnHouseElves() {
            RandomSource rng = level.getRandom();
            final int target = 3;
            int spawned = 0;
            // Ground floor only - that is where the kitchens would be.
            int stepX = Math.max(7, width / 4);
            int stepZ = Math.max(7, length / 4);
            outer:
            for (int lx = stepX / 2; lx < width; lx += stepX) {
                for (int lz = stepZ / 2; lz < length; lz += stepZ) {
                    if (spawned >= target) {
                        break outer;
                    }
                    BlockPos spot = floorSpotNear(lx, 2, lz, 6);
                    if (spot == null || !level.getBlockState(spot.above()).isAir()) {
                        continue;
                    }
                    com.hogwartsmagic.entity.HouseElfEntity elf =
                            com.hogwartsmagic.entity.ModEntities.HOUSE_ELF.get().create(level);
                    if (elf == null) {
                        continue;
                    }
                    elf.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                            rng.nextFloat() * 360.0F, 0.0F);
                    elf.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                            net.minecraft.world.entity.MobSpawnType.STRUCTURE, null);
                    elf.setPersistenceRequired();
                    level.addFreshEntity(elf);
                    spawned++;
                }
            }
            LOGGER.info("Mini Hogwarts staffed with {} house elves", spawned);
        }

        /** Find an empty floor tile (air with a solid block below) near a local position, or null. */
        private BlockPos floorSpotNear(long localX, long localY, long localZ, int radius) {
            for (int r = 0; r <= radius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue; // only the ring at exactly distance r
                        }
                        for (int dy : new int[]{0, 1, -1, 2}) {
                            int wx = anchor.getX() + (int) localX + dx;
                            int wy = anchor.getY() + (int) localY + dy;
                            int wz = anchor.getZ() + (int) localZ + dz;
                            mutable.set(wx, wy, wz);
                            if (!level.getBlockState(mutable).isAir()) {
                                continue;
                            }
                            mutable.set(wx, wy - 1, wz);
                            if (!level.getBlockState(mutable).isAir()) {
                                return new BlockPos(wx, wy, wz);
                            }
                        }
                    }
                }
            }
            return null;
        }

        /** A shared Pensieve in the library: public, with no pourer, and empty until someone pours into it. */
        private void placePublicPensieve(long localX, long localY, long localZ) {
            BlockPos spot = floorSpotNear(localX, localY, localZ, 6);
            if (spot == null || !level.getBlockState(spot).isAir()) {
                return;
            }
            level.setBlock(spot, ExampleMod.PENSIEVE.get().defaultBlockState(), SETBLOCK_FLAGS);
            if (level.getBlockEntity(spot) instanceof com.hogwartsmagic.pensieve.PensieveBlockEntity basin) {
                basin.setPublicBasin(true);
            }
        }

        private boolean placeChest(BlockPos pos, Theme theme, RandomSource rng) {
            if (pos == null || !level.getBlockState(pos).isAir()) {
                return false;
            }
            level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), SETBLOCK_FLAGS);
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                return false;
            }
            List<ItemStack> loot = rollLoot(theme, rng);
            int slot = rng.nextInt(3);
            for (ItemStack stack : loot) {
                if (slot >= 27) {
                    break;
                }
                chest.setItem(slot, stack);
                slot += 1 + rng.nextInt(3);
            }
            return true;
        }

        /** Place a decoration on the nearest empty floor tile around a spot (no-op if none / not air). */
        private void decoNear(BlockPos near, BlockState deco) {
            if (near == null) {
                return;
            }
            BlockPos spot = floorSpotNear(near.getX() - anchor.getX(), near.getY() - anchor.getY(),
                    near.getZ() - anchor.getZ(), 3);
            if (spot != null && level.getBlockState(spot).isAir() && !spot.equals(near)) {
                level.setBlock(spot, deco, SETBLOCK_FLAGS);
            }
        }

        private void layCarpetRunner(BlockPos start) {
            BlockState[] colors = {
                    Blocks.RED_CARPET.defaultBlockState(), Blocks.BLUE_CARPET.defaultBlockState(),
                    Blocks.YELLOW_CARPET.defaultBlockState(), Blocks.GREEN_CARPET.defaultBlockState()
            };
            for (int i = 0; i < 6; i++) {
                mutable.set(start.getX() + i, start.getY(), start.getZ());
                if (!level.getBlockState(mutable).isAir()) {
                    continue;
                }
                mutable.set(start.getX() + i, start.getY() - 1, start.getZ());
                if (level.getBlockState(mutable).isAir()) {
                    continue;
                }
                level.setBlock(new BlockPos(start.getX() + i, start.getY(), start.getZ()), colors[i % colors.length], SETBLOCK_FLAGS);
            }
        }
    }

    // ── Loot tables (mirrors the Diagon Alley themed-chest approach) ──────────────────────

    private enum Theme { LIBRARY, SPELL_STUDY, DUNGEON, GREAT_HALL, TOWER }

    private static List<ItemStack> rollLoot(Theme theme, RandomSource rng) {
        List<ItemStack> out = new ArrayList<>();
        switch (theme) {
            case LIBRARY -> {
                SpellType[] commons = {SpellType.LUMOS, SpellType.ALOHOMORA, SpellType.REVELIO,
                        SpellType.WINGARDIUM_LEVIOSA, SpellType.ARRESTO_MOMENTUM, SpellType.REPARO};
                SpellType[] advanced = {SpellType.PROTEGO, SpellType.EXPELLIARMUS, SpellType.STUPEFY, SpellType.DIFFINDO};
                for (int i = 0; i < 3 + rng.nextInt(4); i++) {
                    addBook(out, rng, commons[rng.nextInt(commons.length)]);
                }
                for (int i = 0; i < 1 + rng.nextInt(2); i++) {
                    addBook(out, rng, advanced[rng.nextInt(advanced.length)]);
                }
                addStack(out, Items.BOOK, 4 + rng.nextInt(6));
                addStack(out, Items.WRITABLE_BOOK, 1 + rng.nextInt(2));
                addStack(out, Items.PAPER, 6 + rng.nextInt(8));
                if (rng.nextInt(7) == 0) {
                    addStack(out, optionalItem("CHAMBER_MEMORY"), 1);
                }
            }
            case SPELL_STUDY -> {
                SpellType[] study = {SpellType.PROTEGO, SpellType.STUPEFY, SpellType.REVELIO,
                        SpellType.INCENDIO, SpellType.GLACIUS, SpellType.PETRIFICUS_TOTALUS};
                for (int i = 0; i < 2 + rng.nextInt(3); i++) {
                    addBook(out, rng, study[rng.nextInt(study.length)]);
                }
                addStack(out, Items.LAPIS_LAZULI, 6 + rng.nextInt(10));
                addStack(out, Items.ENCHANTED_BOOK, 1 + rng.nextInt(2));
                addStack(out, Items.EXPERIENCE_BOTTLE, 3 + rng.nextInt(5));
                addStack(out, Items.GLASS_BOTTLE, 3 + rng.nextInt(3));
                addStack(out, optionalItem("ARCANE_DUST"), 2 + rng.nextInt(4));
            }
            case DUNGEON -> {
                SpellType[] dark = {SpellType.SECTUMSEMPRA, SpellType.CRUCIO, SpellType.DOLOHOV_CURSE,
                        SpellType.INCARCEROUS, SpellType.CONFRINGO};
                addBook(out, rng, dark[rng.nextInt(dark.length)]);
                if (rng.nextFloat() < 0.25F) {
                    addBook(out, rng, dark[rng.nextInt(dark.length)]);
                }
                addStack(out, Items.BONE, 4 + rng.nextInt(6));
                addStack(out, Items.ROTTEN_FLESH, 4 + rng.nextInt(6));
                addStack(out, Items.SOUL_SAND, 2 + rng.nextInt(3));
                addStack(out, Items.IRON_INGOT, 1 + rng.nextInt(3));
                addStack(out, Items.COBWEB, 1 + rng.nextInt(3));
                if (rng.nextFloat() < 0.20F) {
                    addStack(out, Items.SKELETON_SKULL, 1);
                }
            }
            case GREAT_HALL -> {
                addStack(out, Items.BREAD, 4 + rng.nextInt(6));
                addStack(out, Items.COOKED_BEEF, 2 + rng.nextInt(4));
                addStack(out, Items.GOLDEN_CARROT, 1 + rng.nextInt(2));
                addStack(out, Items.CAKE, 1);
                addStack(out, Items.PUMPKIN_PIE, 1 + rng.nextInt(2));
                addStack(out, Items.POTION, 1);
                addStack(out, Items.GLASS_BOTTLE, 3);
                addBook(out, rng, SpellType.LUMOS);
                if (rng.nextFloat() < 0.5F) {
                    addBook(out, rng, SpellType.ACCIO);
                }
            }
            case TOWER -> {
                SpellType[] rare = {SpellType.EXPECTO_PATRONUM, SpellType.PROTEGO_MAXIMA,
                        SpellType.FIENDFYRE, SpellType.BOMBARDA, SpellType.APPARITION};
                addBook(out, rng, rare[rng.nextInt(rare.length)]);
                addStack(out, Items.GOLD_INGOT, 4 + rng.nextInt(6));
                addStack(out, Items.DIAMOND, 1 + rng.nextInt(3));
                addStack(out, Items.EMERALD, 3 + rng.nextInt(5));
                addStack(out, Items.EXPERIENCE_BOTTLE, 4 + rng.nextInt(4));
                if (rng.nextFloat() < 0.05F) {
                    addStack(out, optionalItem("ELDER_WAND"), 1);
                }
            }
        }
        return out;
    }

    /** Looks up a registered mod item by its ExampleMod field name; returns null if missing. */
    private static Item optionalItem(String fieldName) {
        try {
            java.lang.reflect.Field f = ExampleMod.class.getField(fieldName);
            Object regObject = f.get(null);
            if (regObject instanceof net.minecraftforge.registries.RegistryObject<?> ro && ro.get() instanceof Item item) {
                return item;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void addStack(List<ItemStack> out, Item item, int count) {
        if (item != null && count > 0) {
            out.add(new ItemStack(item, count));
        }
    }

    private static void addBook(List<ItemStack> out, RandomSource rng, SpellType spell) {
        try {
            out.add(com.hogwartsmagic.assignment.AssignmentLoot.bookOrAssignment(spell, rng));
        } catch (Throwable ignored) {
        }
    }
}
