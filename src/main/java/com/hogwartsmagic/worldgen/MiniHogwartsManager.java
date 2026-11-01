package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.ClassroomSupplies;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.util.Titles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Furnishes the Mini Hogwarts castle as it generates, and shows the "Hogwarts" title card when a player first
 * sees one. The castle itself is a worldgen structure ({@code worldgen/structure/MiniHogwartsStructure}); its
 * piece calls {@link #furnishChunk} for every chunk it decorates.
 *
 * <p>The furnishing heuristics (themed chests by the library's bookshelves, the enchanting table, the dungeon's
 * iron bars, the Great Hall and the top of the tallest tower; the library's public Pensieve; the Fat Lady on the
 * nearest Great Hall wall; students on each floor and elves below stairs) used to read the finished castle in the
 * world. Worldgen cannot: a chunk is decorated before its neighbours, so their blocks are not there yet. They now
 * read the schematic instead, once per schematic, into a {@link FurnishPlan} of positions; each chunk then places
 * only the plan entries that fall inside its own box. The result no longer depends on which chunk generated
 * first, and nothing is written outside the chunk being decorated.
 */
public final class MiniHogwartsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-MiniHogwarts");

    private static final int SETBLOCK_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
    /** How close (blocks, to the title point) a player must come for the title card. */
    private static final double TITLE_RANGE = 80.0;
    /**
     * For a castle from an old save (no anchor, so no known box): a sight line that stops on a block within this
     * many blocks (XZ) of the title point counts as seeing the castle.
     */
    private static final int ANCHORLESS_CASTLE_REACH = 48;

    /** The plan for the schematic last asked about; rebuilt only when the schematic object changes. */
    private static MiniHogwartsSchematic plannedFor;
    private static FurnishPlan plan;

    private MiniHogwartsManager() {
    }

    // ── Title card ──────────────────────────────────────────────────────────────────────

    /** Where the title card is measured from and aimed at. The centre for now; Task 10 swaps in an anchor. */
    public static BlockPos titlePoint(MiniHogwartsTracker.Castle castle) {
        return castle.center();
    }

    /**
     * Called every 20 ticks per player. The first time a player is within 80 blocks of a castle's title point and
     * can see it, shows "Hogwarts" / "School of Witchcraft and Wizardry" and remembers that player for that
     * castle. Returns the castle titled, or null. Never loads a chunk: a castle whose sight line crosses an
     * unloaded chunk waits for a later check.
     */
    @Nullable
    public static MiniHogwartsTracker.Castle maybeShowTitle(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MiniHogwartsTracker tracker = MiniHogwartsTracker.get(level);
        if (tracker.castles().isEmpty()) {
            return null;
        }
        Vec3 eyes = player.getEyePosition();
        for (MiniHogwartsTracker.Castle castle : tracker.castles()) {
            if (castle.titled().contains(player.getUUID())) {
                continue;
            }
            BlockPos point = titlePoint(castle);
            Vec3 target = Vec3.atCenterOf(point);
            if (eyes.distanceToSqr(target) > TITLE_RANGE * TITLE_RANGE) {
                continue;
            }
            BlockPos from = BlockPos.containing(eyes);
            if (!level.hasChunksAt(Math.min(from.getX(), point.getX()), Math.min(from.getY(), point.getY()),
                    Math.min(from.getZ(), point.getZ()), Math.max(from.getX(), point.getX()),
                    Math.max(from.getY(), point.getY()), Math.max(from.getZ(), point.getZ()))) {
                continue;
            }
            if (!inSight(level, player, eyes, target, castle)) {
                continue;
            }
            Titles.send(player, "Hogwarts", "School of Witchcraft and Wizardry", 20, 70, 30);
            tracker.markTitled(player.getUUID(), castle);
            return castle;
        }
        return null;
    }

    /** True when the sight line from the eyes reaches the title point, or first stops on the castle itself. */
    private static boolean inSight(ServerLevel level, ServerPlayer player, Vec3 eyes, Vec3 target,
                                   MiniHogwartsTracker.Castle castle) {
        BlockHitResult hit = level.clip(new ClipContext(eyes, target, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        BlockPos at = hit.getBlockPos();
        BlockPos anchor = castle.anchor();
        MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
        if (anchor != null && schem != null) {
            return at.getX() >= anchor.getX() - 1 && at.getX() <= anchor.getX() + schem.width()
                    && at.getY() >= anchor.getY() - 1 && at.getY() <= anchor.getY() + schem.height()
                    && at.getZ() >= anchor.getZ() - 1 && at.getZ() <= anchor.getZ() + schem.length();
        }
        BlockPos point = titlePoint(castle);
        return Math.abs(at.getX() - point.getX()) <= ANCHORLESS_CASTLE_REACH
                && Math.abs(at.getZ() - point.getZ()) <= ANCHORLESS_CASTLE_REACH;
    }

    // ── Furnishing, one chunk at a time ─────────────────────────────────────────────────

    /**
     * Places every furnishing of the castle anchored at {@code anchor} whose position is inside {@code chunkBox}:
     * themed chests (stocked from {@code random}), the Potions classroom's chest stock, decorations, the public
     * Pensieve, the Fat Lady, students and house elves. Called from the structure piece's {@code postProcess},
     * on a worldgen thread or (for {@code /place}) the server thread; touches only {@code level} inside the box.
     */
    public static void furnishChunk(WorldGenLevel level, BlockPos anchor, BoundingBox chunkBox, RandomSource random) {
        MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
        if (schem == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Furnishing f : planFor(schem).furnishings) {
            pos.set(anchor.getX() + f.x, anchor.getY() + f.y, anchor.getZ() + f.z);
            if (chunkBox.isInside(pos)) {
                f.place(level, pos.immutable(), chunkBox, random);
            }
        }
    }

    /** The plan for {@code schem}, worked out on first use (the schematic never changes once loaded). */
    static synchronized FurnishPlan planFor(MiniHogwartsSchematic schem) {
        if (plannedFor != schem) {
            plan = new FurnishPlan(schem);
            plannedFor = schem;
            LOGGER.info("Mini Hogwarts furnishing plan: {}", plan.summary);
        }
        return plan;
    }

    /** What one furnishing is; int constants rather than an enum, so the plan stays a flat list of records. */
    private static final int CHEST = 0, BLOCK = 1, PENSIEVE = 2, STOCK = 3, FAT_LADY = 4, STUDENT = 5, ELF = 6;

    /**
     * One thing to place, at local {@code (x, y, z)} from the anchor. {@code theme} is for a CHEST, {@code state}
     * for a BLOCK, {@code facing} for the FAT_LADY (the way her canvas faces).
     */
    private record Furnishing(int kind, int x, int y, int z, @Nullable Theme theme, @Nullable BlockState state,
                              @Nullable Direction facing) {

        void place(WorldGenLevel level, BlockPos pos, BoundingBox chunkBox, RandomSource random) {
            if (kind == CHEST) {
                placeChest(level, pos, theme, random);
            } else if (kind == BLOCK) {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, state, SETBLOCK_FLAGS);
                }
            } else if (kind == PENSIEVE) {
                placePublicPensieve(level, pos);
            } else if (kind == STOCK) {
                stockClassroomChest(level, pos, random);
            } else if (kind == FAT_LADY) {
                if (fatLadyFits(level, pos, facing, chunkBox)) {
                    level.addFreshEntity(new com.hogwartsmagic.portrait.FatLadyPortraitEntity(level.getLevel(), pos, facing));
                }
            } else if (kind == STUDENT) {
                spawnResident(level, pos, random, com.hogwartsmagic.entity.ModEntities.STUDENT.get().create(level.getLevel()));
            } else if (kind == ELF) {
                spawnResident(level, pos, random, com.hogwartsmagic.entity.ModEntities.HOUSE_ELF.get().create(level.getLevel()));
            }
        }
    }

    /**
     * The world-side half of the Fat Lady's fit (the plan already checked the schematic): every canvas cell inside
     * this chunk is air and every wall cell behind one inside this chunk is solid, so terrain that filled the hall
     * does not leave her hanging where she would pop off on her first tick. Cells in other chunks are not read;
     * their castle blocks may not be placed yet.
     */
    private static boolean fatLadyFits(WorldGenLevel level, BlockPos pos, Direction facing, BoundingBox chunkBox) {
        Direction right = facing.getCounterClockWise();
        for (int w = 0; w < com.hogwartsmagic.portrait.FatLadyPortraitEntity.WIDTH; w++) {
            for (int h = -1; h < com.hogwartsmagic.portrait.FatLadyPortraitEntity.HEIGHT - 1; h++) {
                BlockPos cell = pos.relative(right, w).above(h);
                BlockPos behind = cell.relative(facing.getOpposite());
                if (chunkBox.isInside(cell) && !level.getBlockState(cell).isAir()) {
                    return false;
                }
                if (chunkBox.isInside(behind) && !level.getBlockState(behind).isSolid()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean placeChest(WorldGenLevel level, BlockPos pos, Theme theme, RandomSource rng) {
        if (!level.getBlockState(pos).isAir()) {
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

    /** A shared Pensieve in the library: public, with no pourer, and empty until someone pours into it. */
    private static void placePublicPensieve(WorldGenLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) {
            return;
        }
        level.setBlock(pos, ExampleMod.PENSIEVE.get().defaultBlockState(), SETBLOCK_FLAGS);
        if (level.getBlockEntity(pos) instanceof com.hogwartsmagic.pensieve.PensieveBlockEntity basin) {
            basin.setPublicBasin(true);
        }
    }

    /**
     * Fills one of the schematic's own chests (the Potions classroom's) with one roll of ClassroomSupplies, in
     * shuffled slots. The chest was placed by this same chunk's schematic pass just before.
     */
    private static void stockClassroomChest(WorldGenLevel level, BlockPos pos, RandomSource rng) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        List<ClassroomSupplies.Stack> stacks = ClassroomSupplies.roll(
                (min, max) -> min + rng.nextInt(max - min + 1), rng.nextDouble(), rng.nextDouble());
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

    /** A persistent student or elf standing at {@code pos}, when it and the block above are still air. */
    private static void spawnResident(WorldGenLevel level, BlockPos pos, RandomSource rng, @Nullable Mob mob) {
        if (mob == null || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, rng.nextFloat() * 360.0F, 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }

    /**
     * The castle's furnishings worked out from the schematic alone. Reads cells, never the world: beside the
     * schematic counts as air, and below its footprint counts as ground (worldgen beards the terrain up to the
     * castle's base, so an open courtyard at local y 0 stands on grass, which is where the old in-world pass found
     * the "Great Hall" spot of this castle). A floor tile is an air cell over a solid one (cave air, the "clear this
     * interior" marker, is air here too). Every placement still checks the world when it lands, so a spot the
     * terrain did fill (a {@code /place} castle is not bearded) is skipped.
     * Each placement is laid into an overlay as it is planned, so later heuristics see it (a decoration never
     * lands on the chest it decorates), exactly as the old in-world pass saw what it had already placed.
     */
    static final class FurnishPlan {
        final List<Furnishing> furnishings = new ArrayList<>();
        final String summary;
        private final MiniHogwartsSchematic schem;
        private final int width, height, length;
        private static final BlockState GROUND = Blocks.STONE.defaultBlockState();
        /** Blocks planned so far, by local position; read before the schematic. */
        private final Map<Long, BlockState> overlay = new HashMap<>();
        // Landmark locals found during analysis (-1 / 0-count when absent).
        private int enchantX = -1, enchantY = -1, enchantZ = -1;
        private long bookSumX, bookSumY, bookSumZ;
        private int bookCount;
        private long ironSumX, ironSumY, ironSumZ;
        private int ironCount;
        private int topX = -1, topY = -1, topZ = -1; // highest solid block (for a tower treasure)

        FurnishPlan(MiniHogwartsSchematic schem) {
            this.schem = schem;
            this.width = schem.width();
            this.height = schem.height();
            this.length = schem.length();
            int stocked = analyze();
            int chests = planRooms();
            int students = planStudents();
            int elves = planHouseElves();
            this.summary = chests + " themed chests, " + stocked + " classroom chests, " + students + " students, "
                    + elves + " house elves";
        }

        /** Single pass: landmark locations (enchanting room, library, dungeon, tower) and the schematic's chests. */
        private int analyze() {
            int stocked = 0;
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        BlockState state = schem.blockAt(x, y, z);
                        if (state.isAir()) {
                            continue;
                        }
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
                            // The Potions classroom's ingredient chests: stocked by the chunk that holds them.
                            furnishings.add(new Furnishing(STOCK, x, y, z, null, null, null));
                            stocked++;
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
            }
            return stocked;
        }

        /** Themed chests and decorations in the landmark rooms, the library Pensieve and the Fat Lady. */
        private int planRooms() {
            int placed = 0;
            // Library — wherever the bookshelves cluster.
            if (bookCount > 0) {
                BlockPos spot = floorSpotNear(bookSumX / bookCount, bookSumY / bookCount, bookSumZ / bookCount, 6);
                if (planChest(spot, Theme.LIBRARY)) {
                    placed++;
                    decoNear(spot, Blocks.LECTERN.defaultBlockState());
                    decoNear(spot, Blocks.LANTERN.defaultBlockState());
                }
                BlockPos basin = floorSpotNear(bookSumX / bookCount, bookSumY / bookCount, bookSumZ / bookCount, 6);
                if (basin != null) {
                    put(basin, ExampleMod.PENSIEVE.get().defaultBlockState());
                    furnishings.add(new Furnishing(PENSIEVE, basin.getX(), basin.getY(), basin.getZ(), null, null, null));
                }
            }
            // Spell study — beside the enchanting table.
            if (enchantX >= 0) {
                BlockPos spot = floorSpotNear(enchantX, enchantY, enchantZ, 5);
                if (planChest(spot, Theme.SPELL_STUDY)) {
                    placed++;
                    decoNear(spot, Blocks.LECTERN.defaultBlockState());
                    decoNear(spot, Blocks.LANTERN.defaultBlockState());
                }
            }
            // Dungeon — among the iron bars.
            if (ironCount > 0) {
                BlockPos spot = floorSpotNear(ironSumX / ironCount, ironSumY / ironCount, ironSumZ / ironCount, 6);
                if (planChest(spot, Theme.DUNGEON)) {
                    placed++;
                    decoNear(spot, Blocks.COBWEB.defaultBlockState());
                    decoNear(spot, Blocks.TORCH.defaultBlockState());
                }
            }
            // Great Hall — the ground floor at the footprint center, with a short carpet runner.
            BlockPos hall = floorSpotNear(width / 2, 1, length / 2, 10);
            if (planChest(hall, Theme.GREAT_HALL)) {
                placed++;
                layCarpetRunner(hall);
                decoNear(hall, Blocks.LANTERN.defaultBlockState());
                decoNear(hall, Blocks.FLOWER_POT.defaultBlockState());
            }
            // The Fat Lady keeps watch on the nearest Great Hall wall.
            if (hall != null && !planFatLady(hall, 16)) {
                LOGGER.info("Mini Hogwarts: no free wall near the Great Hall for the Fat Lady");
            }
            // Tower treasure — a hidden pocket near the very top.
            if (topX >= 0) {
                BlockPos spot = floorSpotNear(topX, topY - 2, topZ, 6);
                if (planChest(spot, Theme.TOWER)) {
                    placed++;
                    decoNear(spot, Blocks.LANTERN.defaultBlockState());
                }
            }
            return placed;
        }

        /** A crowd of wandering students across the castle's floors. */
        private int planStudents() {
            final int target = 32;
            int planned = 0;
            int[] floorYs = {2, Math.max(2, height / 4), Math.max(2, height / 2), Math.max(2, (height * 3) / 4)};
            int stepX = Math.max(5, width / 7);
            int stepZ = Math.max(5, length / 7);
            outer:
            for (int ly : floorYs) {
                for (int lx = stepX / 2; lx < width; lx += stepX) {
                    for (int lz = stepZ / 2; lz < length; lz += stepZ) {
                        if (planned >= target) {
                            break outer;
                        }
                        BlockPos spot = floorSpotNear(lx, ly, lz, 5);
                        if (spot == null || !isAir(spot.getX(), spot.getY() + 1, spot.getZ())) {
                            continue; // need head room for the student
                        }
                        furnishings.add(new Furnishing(STUDENT, spot.getX(), spot.getY(), spot.getZ(), null, null, null));
                        planned++;
                    }
                }
            }
            return planned;
        }

        /**
         * A few house elves on the ground floor, where the kitchens would be. Finding one indoors is a good deal
         * kinder than hunting the Forbidden Forest for the single wild one. They spawn wild, so they can still be
         * taken into service the hard way.
         */
        private int planHouseElves() {
            final int target = 3;
            int planned = 0;
            int stepX = Math.max(7, width / 4);
            int stepZ = Math.max(7, length / 4);
            outer:
            for (int lx = stepX / 2; lx < width; lx += stepX) {
                for (int lz = stepZ / 2; lz < length; lz += stepZ) {
                    if (planned >= target) {
                        break outer;
                    }
                    BlockPos spot = floorSpotNear(lx, 2, lz, 6);
                    if (spot == null || !isAir(spot.getX(), spot.getY() + 1, spot.getZ())) {
                        continue;
                    }
                    furnishings.add(new Furnishing(ELF, spot.getX(), spot.getY(), spot.getZ(), null, null, null));
                    planned++;
                }
            }
            return planned;
        }

        // ── Reading the schematic (plus what is already planned) ──

        private BlockState at(int x, int y, int z) {
            BlockState planned = overlay.get(BlockPos.asLong(x, y, z));
            if (planned != null) {
                return planned;
            }
            if (y < 0 && x >= 0 && z >= 0 && x < width && z < length) {
                return GROUND; // under the footprint: the bearded terrain, level with the castle's base
            }
            return schem.blockAt(x, y, z);
        }

        private boolean isAir(int x, int y, int z) {
            return at(x, y, z).isAir();
        }

        private boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < length;
        }

        private void put(BlockPos local, BlockState state) {
            overlay.put(local.asLong(), state);
        }

        /** An empty floor tile (air over a solid cell) inside the schematic near a local position, or null. */
        private BlockPos floorSpotNear(long localX, long localY, long localZ, int radius) {
            for (int r = 0; r <= radius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue; // only the ring at exactly distance r
                        }
                        for (int dy : new int[]{0, 1, -1, 2}) {
                            int x = (int) localX + dx;
                            int y = (int) localY + dy;
                            int z = (int) localZ + dz;
                            if (inside(x, y, z) && isAir(x, y, z) && !isAir(x, y - 1, z)) {
                                return new BlockPos(x, y, z);
                            }
                        }
                    }
                }
            }
            return null;
        }

        private boolean planChest(BlockPos spot, Theme theme) {
            if (spot == null || !isAir(spot.getX(), spot.getY(), spot.getZ())) {
                return false;
            }
            put(spot, Blocks.CHEST.defaultBlockState());
            furnishings.add(new Furnishing(CHEST, spot.getX(), spot.getY(), spot.getZ(), theme, null, null));
            return true;
        }

        /** A decoration on the nearest empty floor tile around a spot (none when there is no room). */
        private void decoNear(BlockPos near, BlockState deco) {
            if (near == null) {
                return;
            }
            BlockPos spot = floorSpotNear(near.getX(), near.getY(), near.getZ(), 3);
            if (spot != null && !spot.equals(near)) {
                put(spot, deco);
                furnishings.add(new Furnishing(BLOCK, spot.getX(), spot.getY(), spot.getZ(), null, deco, null));
            }
        }

        private void layCarpetRunner(BlockPos start) {
            BlockState[] colors = {
                    Blocks.RED_CARPET.defaultBlockState(), Blocks.BLUE_CARPET.defaultBlockState(),
                    Blocks.YELLOW_CARPET.defaultBlockState(), Blocks.GREEN_CARPET.defaultBlockState()
            };
            for (int i = 0; i < 6; i++) {
                int x = start.getX() + i;
                if (!inside(x, start.getY(), start.getZ()) || !isAir(x, start.getY(), start.getZ())
                        || isAir(x, start.getY() - 1, start.getZ())) {
                    continue;
                }
                BlockPos at = new BlockPos(x, start.getY(), start.getZ());
                put(at, colors[i % colors.length]);
                furnishings.add(new Furnishing(BLOCK, x, start.getY(), start.getZ(), null, colors[i % colors.length], null));
            }
        }

        // ── The Fat Lady: PortraitPlacement's wall search, on the schematic ──

        private static final int[] HEIGHTS = {1, 0, 2};
        private static final int[] SLIDES = {0, 1, -1, 2, -2};

        /**
         * Walks out from {@code floor} in all four directions and plans the Fat Lady on the closest wall within
         * {@code reach} where her 2 x 4 canvas has air in front and solid blocks behind, her bottom edge about a
         * block above the floor (the same search {@code PortraitPlacement.hangFatLadyNear} runs in the world).
         */
        private boolean planFatLady(BlockPos floor, int reach) {
            BlockPos best = null;
            Direction bestFacing = null;
            int bestDist = Integer.MAX_VALUE;
            for (Direction toWall : Direction.Plane.HORIZONTAL) {
                for (int k = 1; k <= reach && k < bestDist; k++) {
                    BlockPos probe = floor.relative(toWall, k).above();
                    if (!inside(probe.getX(), probe.getY(), probe.getZ())) {
                        break;
                    }
                    if (isAir(probe.getX(), probe.getY(), probe.getZ())) {
                        continue;
                    }
                    Direction facing = toWall.getOpposite();
                    BlockPos fit = fitOnWall(probe, facing);
                    if (fit != null) {
                        best = fit;
                        bestFacing = facing;
                        bestDist = k;
                    }
                    break; // the first solid block this way is the wall, fit or not
                }
            }
            if (best == null) {
                return false;
            }
            furnishings.add(new Furnishing(FAT_LADY, best.getX(), best.getY(), best.getZ(), null, null, bestFacing));
            LOGGER.info("Mini Hogwarts: the Fat Lady hangs at local {} {} {} facing {}", best.getX(), best.getY(),
                    best.getZ(), bestFacing);
            return true;
        }

        private BlockPos fitOnWall(BlockPos wall, Direction facing) {
            BlockPos front = wall.relative(facing);
            Direction along = facing.getClockWise();
            for (int dy : HEIGHTS) {
                for (int slide : SLIDES) {
                    BlockPos pos = front.relative(along, slide).above(dy);
                    if (canvasFits(pos, facing)) {
                        return pos;
                    }
                }
            }
            return null;
        }

        /**
         * The canvas a Fat Lady hung at {@code pos} facing {@code facing} covers {@code pos} and the block to its
         * right ({@code facing.getCounterClockWise()}), from one block below {@code pos} to two above (see
         * {@code FatLadyPortraitEntity.calculateBoundingBox}); every one of those cells must be air and the cell
         * behind each must be solid.
         */
        private boolean canvasFits(BlockPos pos, Direction facing) {
            Direction right = facing.getCounterClockWise();
            for (int w = 0; w < com.hogwartsmagic.portrait.FatLadyPortraitEntity.WIDTH; w++) {
                for (int h = -1; h < com.hogwartsmagic.portrait.FatLadyPortraitEntity.HEIGHT - 1; h++) {
                    BlockPos cell = pos.relative(right, w).above(h);
                    BlockPos behind = cell.relative(facing.getOpposite());
                    if (!inside(cell.getX(), cell.getY(), cell.getZ()) || !isAir(cell.getX(), cell.getY(), cell.getZ())
                            || !inside(behind.getX(), behind.getY(), behind.getZ())
                            || !at(behind.getX(), behind.getY(), behind.getZ()).isSolid()) {
                        return false;
                    }
                }
            }
            return true;
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
                SpellType[] advanced = {SpellType.PROTEGO, SpellType.EXPELLIARMUS, SpellType.STUPEFY, SpellType.DIFFINDO, SpellType.CAELUM_FASCINO};
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
