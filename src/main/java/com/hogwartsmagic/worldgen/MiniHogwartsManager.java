package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbienceNet;
import com.hogwartsmagic.ambience.AmbienceRegistry;
import com.hogwartsmagic.brewing.ClassroomSupplies;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.util.Titles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBannerBlock;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Furnishes the Mini Hogwarts castle as it generates, and shows the "Hogwarts" title card when a player first
 * sees one. The castle itself is a worldgen structure ({@code worldgen/structure/MiniHogwartsStructure}); its
 * piece calls {@link #furnishChunk} for every chunk it decorates.
 *
 * <p>What goes where comes from {@link MiniHogwartsAnchors}, the list the castle generator writes beside the
 * schematic: themed chests, the public Pensieve, the Fat Lady, paintings, wall banners, the Sorting Hat on its
 * stool, the castle's guardian statues, students and house elves. Only if that file cannot be read does the
 * furnishing fall back to the old schematic-only heuristics (themed chests by the library's bookshelves, the
 * enchanting table, the dungeon's iron bars, the Great Hall and the top of the tallest tower; the Fat Lady on the
 * nearest Great Hall wall; students on each floor and elves below stairs). Either way the world is not read to
 * decide: worldgen decorates a chunk before its neighbours, so their blocks are not there yet. The positions are
 * worked out once per schematic into a {@link FurnishPlan}; each chunk then places only the plan entries that
 * fall inside its own box. The result no longer depends on which chunk generated first, and nothing is written
 * outside the chunk being decorated.
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
    private static MiniHogwartsAnchors plannedAnchors;
    private static FurnishPlan plan;

    private MiniHogwartsManager() {
    }

    // ── Title card ──────────────────────────────────────────────────────────────────────

    /**
     * Where the title card is measured from and aimed at: the anchors' {@code title_point} (a room names one
     * when the castle has a spot worth looking at) placed on the castle, else the castle's recorded centre. A
     * castle from an old save has no recorded anchor, so it keeps its centre.
     */
    public static BlockPos titlePoint(MiniHogwartsTracker.Castle castle) {
        MiniHogwartsAnchors anchors = castle.anchor() == null ? null : MiniHogwartsAnchors.get();
        if (anchors != null && anchors.titlePoint() != null) {
            return castle.anchor().offset(anchors.titlePoint());
        }
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

    /** A player further than this from a castle's centre (blocks) is nowhere near its hall: skipped without a box test. */
    private static final double HALL_CHECK_REACH = 200.0;

    /**
     * Called every 10 ticks per player. The first time a player walks into a castle's Great Hall (the anchors'
     * {@code hall_box} placed on the castle) it remembers that player for that castle and tells that player alone to
     * play the hall ceiling's reveal, the plank roof dissolving into sky; after that the ceiling is simply open.
     * Reads only positions and the tracker, never a block, so it loads no chunk. Returns the castle entered, or null
     * (also when the castle never registered its ceiling, as in a world from before the ceiling existed: nothing to
     * reveal, nothing remembered).
     */
    @Nullable
    public static MiniHogwartsTracker.Castle maybeRevealHall(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MiniHogwartsTracker tracker = MiniHogwartsTracker.get(level);
        if (tracker.castles().isEmpty()) {
            return null;
        }
        MiniHogwartsAnchors anchors = MiniHogwartsAnchors.get();
        if (anchors == null) {
            return null;
        }
        Vec3 at = player.position();
        for (MiniHogwartsTracker.Castle castle : tracker.castles()) {
            BlockPos anchor = castle.anchor();
            if (anchor == null || castle.enteredHall().contains(player.getUUID())) {
                continue;
            }
            double dx = castle.center().getX() + 0.5 - at.x;
            double dz = castle.center().getZ() + 0.5 - at.z;
            if (dx * dx + dz * dz > HALL_CHECK_REACH * HALL_CHECK_REACH) {
                continue;
            }
            if (!anchors.hallBox().move(anchor.getX(), anchor.getY(), anchor.getZ()).contains(at)) {
                continue;
            }
            java.util.UUID ceiling = MiniHogwartsAmbience.ceilingId(anchor);
            if (AmbienceRegistry.get(level).get(ceiling) == null) {
                continue;
            }
            tracker.markEnteredHall(player.getUUID(), castle);
            AmbienceNet.sendReveal(player, ceiling);
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
     * themed chests (stocked from {@code random}), the Potions classroom's chest stock, the public Pensieve, the
     * Fat Lady, paintings, wall banners, the Sorting Hat, the guardian statues, students and house elves. Called
     * from the structure piece's {@code postProcess}, on a worldgen thread or (for {@code /place}) the server
     * thread; reads and writes only {@code level} inside the box (a painting or portrait whose canvas crosses the
     * box edge is judged by the schematic there). An entity is created for the {@code ServerLevel} behind the
     * region and added through the region, like every other mob the castle spawns.
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
                f.place(level, schem, anchor, pos.immutable(), chunkBox, random);
            }
        }
    }

    /**
     * Loads the schematic and its anchors and builds the furnishing plan now, on the server thread as the server
     * starts, so the first castle chunk a worldgen worker decorates does not pay for it (a 2.4M-cell scan) while holding
     * the class locks the server thread may be waiting on. Safe to call again: everything is cached.
     */
    public static void prewarm() {
        try {
            MiniHogwartsSchematic schem = MiniHogwartsSchematic.get();
            if (schem != null) {
                planFor(schem);
            }
        } catch (RuntimeException e) {
            // Never stop a server from starting over this: the first castle chunk tries again (and logs again).
            LOGGER.error("Could not prewarm the Mini Hogwarts furnishing plan", e);
        }
    }

    /** Whether the furnishing plan for {@code schem} (with its current anchors) is already built. GameTests only. */
    public static synchronized boolean planReadyFor(MiniHogwartsSchematic schem) {
        return plan != null && plannedFor == schem && plannedAnchors == MiniHogwartsAnchors.forSchematic(schem);
    }

    /**
     * The plan for {@code schem}, worked out on first use (the schematic and its anchors never change once
     * loaded). Built under the lock, so two chunks asking at once share one plan.
     */
    static synchronized FurnishPlan planFor(MiniHogwartsSchematic schem) {
        MiniHogwartsAnchors anchors = MiniHogwartsAnchors.forSchematic(schem);
        if (plannedFor != schem || plannedAnchors != anchors) {
            plan = new FurnishPlan(schem, anchors);
            plannedFor = schem;
            plannedAnchors = anchors;
            LOGGER.info("Mini Hogwarts furnishing plan ({}): {}", anchors != null ? "from the anchors" : "heuristic",
                    plan.summary);
            for (String problem : plan.problems) {
                LOGGER.warn("Mini Hogwarts anchor skipped: {}", problem);
            }
        }
        return plan;
    }

    /**
     * Everything wrong with {@code anchors} against {@code schem}, for the GameTest that guards the shipped files:
     * a chest, Pensieve, statue, student or elf cell that is not free floor, an unknown chest theme, a banner with
     * no wall behind it, a stool that is not a slab, and every painting and the Fat Lady that finds no spot where
     * the survival rules of the game itself hold. Reads the schematic only; {@code level} just owns the entities
     * tried.
     */
    public static List<String> anchorProblems(Level level, MiniHogwartsSchematic schem, MiniHogwartsAnchors anchors) {
        List<String> problems = new ArrayList<>(new FurnishPlan(schem, anchors).problems);
        Function<BlockPos, BlockState> states = p -> schem.blockAt(p.getX(), p.getY(), p.getZ());
        for (MiniHogwartsAnchors.Wall wall : anchors.paintings()) {
            BlockPos cell = wall.at().relative(wall.facing());
            if (choosePainting(level, level.registryAccess(), cell, wall.facing(), states, p -> true,
                    RandomSource.create(1L)) == null) {
                problems.add("no painting fits on the wall " + wall.at().toShortString() + " facing " + wall.facing());
            }
        }
        MiniHogwartsAnchors.Wall lady = anchors.fatLady();
        if (lady != null) {
            BlockPos cell = com.hogwartsmagic.portrait.PortraitPlacement.canvasCell(lady.at(), lady.facing());
            var portrait = com.hogwartsmagic.portrait.PortraitPlacement.portraitInCell(level, cell, lady.facing());
            if (!hangingFits(states, portrait, lady.facing())) {
                problems.add("the Fat Lady does not fit on " + lady.at().toShortString() + " facing " + lady.facing());
            }
        }
        return problems;
    }

    /** What one furnishing is; int constants rather than an enum, so the plan stays a flat list of records. */
    private static final int CHEST = 0, BLOCK = 1, PENSIEVE = 2, STOCK = 3, FAT_LADY = 4, STUDENT = 5, ELF = 6,
            PAINTING = 7, BANNER = 8, STATUE = 9, HAT = 10;

    /**
     * One thing to place, at local {@code (x, y, z)} from the anchor. {@code theme} is for a CHEST, {@code state}
     * for a BLOCK or a BANNER (its wall-banner state), {@code facing} for the CHEST, FAT_LADY, PAINTING (the way
     * the canvas faces; for these two, and the BANNER, the position is the cell in front of the wall) and
     * BANNER, and {@code yaw} for a STATUE.
     */
    private record Furnishing(int kind, int x, int y, int z, @Nullable Theme theme, @Nullable BlockState state,
                              @Nullable Direction facing, float yaw) {

        Furnishing(int kind, int x, int y, int z, @Nullable Theme theme, @Nullable BlockState state,
                   @Nullable Direction facing) {
            this(kind, x, y, z, theme, state, facing, 0.0F);
        }

        void place(WorldGenLevel level, MiniHogwartsSchematic schem, BlockPos anchor, BlockPos pos,
                   BoundingBox chunkBox, RandomSource random) {
            if (kind == CHEST) {
                placeChest(level, pos, theme, facing, random);
            } else if (kind == BLOCK) {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, state, SETBLOCK_FLAGS);
                }
            } else if (kind == PENSIEVE) {
                placePublicPensieve(level, pos);
            } else if (kind == STOCK) {
                stockClassroomChest(level, pos, random);
            } else if (kind == FAT_LADY) {
                var portrait = com.hogwartsmagic.portrait.PortraitPlacement.portraitInCell(level.getLevel(), pos, facing);
                if (hangingFits(worldAndSchematic(level, schem, anchor, chunkBox), portrait, facing)) {
                    level.addFreshEntity(portrait);
                } else {
                    LOGGER.warn("Mini Hogwarts: the Fat Lady does not fit at {} facing {}; not hung", pos.toShortString(), facing);
                }
            } else if (kind == PAINTING) {
                Painting painting = choosePainting(level.getLevel(), level.registryAccess(), pos, facing,
                        worldAndSchematic(level, schem, anchor, chunkBox), chunkBox::isInside, random);
                if (painting != null) {
                    level.addFreshEntity(painting);
                } else {
                    LOGGER.warn("Mini Hogwarts: no painting survives at {} facing {}; none hung", pos.toShortString(), facing);
                }
            } else if (kind == BANNER) {
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, state, SETBLOCK_FLAGS);
                }
            } else if (kind == STATUE) {
                spawnStatue(level, pos, yaw);
            } else if (kind == HAT) {
                placeSortingHat(level, pos);
            } else if (kind == STUDENT) {
                spawnResident(level, pos, random, com.hogwartsmagic.entity.ModEntities.STUDENT.get().create(level.getLevel()));
            } else if (kind == ELF) {
                spawnResident(level, pos, random, com.hogwartsmagic.entity.ModEntities.HOUSE_ELF.get().create(level.getLevel()));
            }
        }
    }

    /**
     * The blocks a hanging thing is judged against: this chunk's own cells from the world (already written by this
     * same piece, or the terrain if the castle was placed without clearing it), every other cell from the
     * schematic, because the chunk next door has not been decorated yet.
     */
    private static Function<BlockPos, BlockState> worldAndSchematic(WorldGenLevel level, MiniHogwartsSchematic schem,
                                                                    BlockPos anchor, BoundingBox chunkBox) {
        return p -> chunkBox.isInside(p) ? level.getBlockState(p)
                : schem.blockAt(p.getX() - anchor.getX(), p.getY() - anchor.getY(), p.getZ() - anchor.getZ());
    }

    /**
     * Would {@code hanging} survive where it is? The same two tests {@code HangingEntity.survives} runs, on
     * {@code states} instead of a level: every cell its box covers has no collision (air, a light block, a
     * torch), and the cell behind each is solid (or a repeater). Stricter than the game in one way: a block
     * with any collision shape in the canvas fails, even one that would not touch the thin box. The game's
     * entity-overlap test is left out; the furnishing never puts two on one wall cell.
     */
    static boolean hangingFits(Function<BlockPos, BlockState> states, HangingEntity hanging, Direction facing) {
        net.minecraft.world.phys.AABB box = hanging.getBoundingBox();
        double eps = 1.0E-4D;
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos behind = new BlockPos.MutableBlockPos();
        for (int y = (int) Math.floor(box.minY + eps); y <= (int) Math.floor(box.maxY - eps); y++) {
            for (int z = (int) Math.floor(box.minZ + eps); z <= (int) Math.floor(box.maxZ - eps); z++) {
                for (int x = (int) Math.floor(box.minX + eps); x <= (int) Math.floor(box.maxX - eps); x++) {
                    cell.set(x, y, z);
                    if (!states.apply(cell).getCollisionShape(EmptyBlockGetter.INSTANCE, cell).isEmpty()) {
                        return false;
                    }
                    behind.set(x - facing.getStepX(), y, z - facing.getStepZ());
                    BlockState wall = states.apply(behind);
                    if (!wall.isSolid() && !DiodeBlock.isDiode(wall)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * A painting for the wall block in front of which {@code cell} lies: the biggest placeable variant (the
     * game's own rule) that survives somewhere in the recess, which is the cell and the ones beside it and above
     * it, chosen at random among the equally big fits. Null when nothing does. {@code entityOk} keeps the
     * painting's tile position inside the chunk that places it. Built for {@code level} and never added here.
     */
    @Nullable
    static Painting choosePainting(Level level, net.minecraft.core.RegistryAccess access, BlockPos cell,
                                   Direction facing, Function<BlockPos, BlockState> states,
                                   Predicate<BlockPos> entityOk, RandomSource random) {
        List<Holder<PaintingVariant>> variants = new ArrayList<>();
        access.registryOrThrow(Registries.PAINTING_VARIANT)
                .getTagOrEmpty(net.minecraft.tags.PaintingVariantTags.PLACEABLE).forEach(variants::add);
        variants.sort(Comparator.comparingInt((Holder<PaintingVariant> v) -> v.value().width() * v.value().height()).reversed());
        Direction along = facing.getClockWise();
        List<Painting> fits = new ArrayList<>();
        int bestArea = 0;
        for (Holder<PaintingVariant> variant : variants) {
            int area = variant.value().width() * variant.value().height();
            if (area < bestArea) {
                break; // the sort puts the bigger ones first; a smaller one only matters if nothing bigger fit
            }
            for (int side = -1; side <= 1; side++) {
                for (int up = 0; up <= 1; up++) {
                    BlockPos pos = cell.relative(along, side).above(up);
                    if (!entityOk.test(pos)) {
                        continue;
                    }
                    Painting painting = new Painting(level, pos, facing, variant);
                    if (hangingFits(states, painting, facing)) {
                        bestArea = area;
                        fits.add(painting);
                    }
                }
            }
        }
        return fits.isEmpty() ? null : fits.get(random.nextInt(fits.size()));
    }

    private static boolean placeChest(WorldGenLevel level, BlockPos pos, Theme theme, @Nullable Direction facing,
                                     RandomSource rng) {
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        level.setBlock(pos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing != null ? facing : Direction.NORTH), SETBLOCK_FLAGS);
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

    /** A dormant castle guardian statue posted on {@code pos}, facing {@code yaw}, when it has room to stand. */
    private static void spawnStatue(WorldGenLevel level, BlockPos pos, float yaw) {
        com.hogwartsmagic.entity.LivingStatueEntity statue =
                com.hogwartsmagic.entity.ModEntities.LIVING_STATUE.get().create(level.getLevel());
        if (statue == null || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        statue.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        statue.yBodyRot = statue.yBodyRotO = yaw;
        statue.yHeadRot = statue.yHeadRotO = yaw;
        statue.guardCastle(pos, yaw);
        level.addFreshEntity(statue);
    }

    /** How big the Sorting Hat's sprite is drawn, and how far above the seat its middle floats (blocks). */
    private static final float HAT_SCALE = 0.7F;

    /**
     * The Sorting Hat on its stool: an item display of the mod's hat, a flat sprite that turns to face whoever
     * looks. {@code stool} is the slab block; the hat rests on the seat (a bottom slab's top face, half a block up).
     * Skipped if the stool block is not a slab in the world, so a hat never floats over terrain.
     */
    private static void placeSortingHat(WorldGenLevel level, BlockPos stool) {
        if (!(level.getBlockState(stool).getBlock() instanceof SlabBlock)) {
            return;
        }
        net.minecraft.world.entity.Entity display = net.minecraft.world.entity.EntityType.ITEM_DISPLAY.create(level.getLevel());
        if (display == null) {
            return;
        }
        double y = stool.getY() + 0.5D + HAT_SCALE / 2.0D;
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.put("item", new ItemStack(ExampleMod.SORTING_HAT.get()).save(level.registryAccess()));
        tag.putString("item_display", "fixed");
        tag.putString("billboard", "vertical");
        net.minecraft.nbt.CompoundTag transform = new net.minecraft.nbt.CompoundTag();
        transform.put("translation", floats(0.0F, 0.0F, 0.0F));
        transform.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transform.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transform.put("scale", floats(HAT_SCALE, HAT_SCALE, HAT_SCALE));
        tag.put("transformation", transform);
        display.load(tag);
        display.setPos(stool.getX() + 0.5D, y, stool.getZ() + 0.5D);
        level.addFreshEntity(display);
    }

    private static net.minecraft.nbt.ListTag floats(float... values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (float v : values) {
            list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        }
        return list;
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
     *
     * <p>With {@link MiniHogwartsAnchors} the landmarks are not guessed: the plan takes the generator's own list
     * and only checks each entry against the schematic, putting what is wrong with an entry in {@link #problems}
     * and leaving that entry out. Without them (the file is unreadable, or a GameTest's stand-in castle has none)
     * it falls back to the heuristics.
     */
    static final class FurnishPlan {
        final List<Furnishing> furnishings = new ArrayList<>();
        /** What the anchors got wrong about this schematic: one line per skipped entry. */
        final List<String> problems = new ArrayList<>();
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

        FurnishPlan(MiniHogwartsSchematic schem, @Nullable MiniHogwartsAnchors anchors) {
            this.schem = schem;
            this.width = schem.width();
            this.height = schem.height();
            this.length = schem.length();
            int stocked = analyze();
            if (anchors == null) {
                int chests = planRooms();
                int students = planStudents();
                int elves = planHouseElves();
                this.summary = chests + " themed chests, " + stocked + " classroom chests, " + students + " students, "
                        + elves + " house elves";
                return;
            }
            int chests = planAnchoredChests(anchors);
            int students = planAnchoredSpots(anchors.studentSpots(), STUDENT, "student");
            int elves = anchors.elfSpots().isEmpty() ? planHouseElves()
                    : planAnchoredSpots(anchors.elfSpots(), ELF, "house elf");
            int statues = planAnchoredStatues(anchors);
            int hung = planAnchoredHangings(anchors);
            this.summary = chests + " themed chests, " + stocked + " classroom chests, " + students + " students, "
                    + elves + " house elves, " + statues + " statues, " + hung
                    + " paintings, banners, portraits, pensieve and hat";
        }

        // ── From the anchors ──

        private int planAnchoredChests(MiniHogwartsAnchors anchors) {
            int placed = 0;
            for (MiniHogwartsAnchors.Chest chest : anchors.chests()) {
                Theme theme = Theme.byName(chest.theme());
                BlockPos at = chest.at();
                if (theme == null) {
                    problems.add("chest at " + at.toShortString() + " has an unknown theme '" + chest.theme() + "'");
                } else if (!standable(at, 1)) {
                    problems.add("chest at " + at.toShortString() + " is not on free floor");
                } else {
                    put(at, Blocks.CHEST.defaultBlockState());
                    furnishings.add(new Furnishing(CHEST, at.getX(), at.getY(), at.getZ(), theme, null, chest.facing()));
                    placed++;
                }
            }
            return placed;
        }

        /** Students or elves at their spots: free floor with head room, or the spot is skipped. */
        private int planAnchoredSpots(List<BlockPos> spots, int kind, String what) {
            int placed = 0;
            for (BlockPos at : spots) {
                if (!standable(at, 2)) {
                    problems.add(what + " spot " + at.toShortString() + " is not free floor with head room");
                    continue;
                }
                furnishings.add(new Furnishing(kind, at.getX(), at.getY(), at.getZ(), null, null, null));
                placed++;
            }
            return placed;
        }

        private int planAnchoredStatues(MiniHogwartsAnchors anchors) {
            int placed = 0;
            for (MiniHogwartsAnchors.Statue statue : anchors.statues()) {
                BlockPos at = statue.at();
                if (!standable(at, 3)) {
                    problems.add("statue post " + at.toShortString() + " is not free floor with 3 blocks of head room");
                    continue;
                }
                furnishings.add(new Furnishing(STATUE, at.getX(), at.getY(), at.getZ(), null, null, null, statue.yaw()));
                placed++;
            }
            return placed;
        }

        /** The Pensieve, the Fat Lady, paintings, banners and the hat. Their fit is judged where they land. */
        private int planAnchoredHangings(MiniHogwartsAnchors anchors) {
            int planned = 0;
            BlockPos pensieve = anchors.pensieve();
            if (pensieve != null) {
                if (standable(pensieve, 1)) {
                    put(pensieve, ExampleMod.PENSIEVE.get().defaultBlockState());
                    furnishings.add(new Furnishing(PENSIEVE, pensieve.getX(), pensieve.getY(), pensieve.getZ(), null, null, null));
                    planned++;
                } else {
                    problems.add("pensieve at " + pensieve.toShortString() + " is not on free floor");
                }
            }
            MiniHogwartsAnchors.Wall lady = anchors.fatLady();
            if (lady != null) {
                BlockPos cell = com.hogwartsmagic.portrait.PortraitPlacement.canvasCell(lady.at(), lady.facing());
                furnishings.add(new Furnishing(FAT_LADY, cell.getX(), cell.getY(), cell.getZ(), null, null, lady.facing()));
                planned++;
            }
            for (MiniHogwartsAnchors.Wall wall : anchors.paintings()) {
                BlockPos cell = wall.at().relative(wall.facing());
                furnishings.add(new Furnishing(PAINTING, cell.getX(), cell.getY(), cell.getZ(), null, null, wall.facing()));
                planned++;
            }
            for (MiniHogwartsAnchors.Banner banner : anchors.banners()) {
                BlockState state = bannerState(banner.house(), banner.facing());
                BlockPos at = banner.at();
                if (state == null) {
                    problems.add("banner at " + at.toShortString() + " has an unknown house '" + banner.house() + "'");
                } else if (!isAir(at.getX(), at.getY(), at.getZ())
                        || !at(at.getX() - banner.facing().getStepX(), at.getY(), at.getZ() - banner.facing().getStepZ()).isSolid()) {
                    problems.add("banner at " + at.toShortString() + " is not an empty cell against a wall");
                } else {
                    furnishings.add(new Furnishing(BANNER, at.getX(), at.getY(), at.getZ(), null, state, banner.facing()));
                    planned++;
                }
            }
            BlockPos stool = anchors.stool();
            if (stool != null) {
                if (at(stool.getX(), stool.getY(), stool.getZ()).getBlock() instanceof SlabBlock) {
                    furnishings.add(new Furnishing(HAT, stool.getX(), stool.getY(), stool.getZ(), null, null, null));
                    planned++;
                } else {
                    problems.add("the stool at " + stool.toShortString() + " is not a slab");
                }
            }
            return planned;
        }

        /** {@code headroom} air cells at {@code at} standing on something. */
        private boolean standable(BlockPos at, int headroom) {
            for (int dy = 0; dy < headroom; dy++) {
                if (!inside(at.getX(), at.getY() + dy, at.getZ()) || !isAir(at.getX(), at.getY() + dy, at.getZ())) {
                    return false;
                }
            }
            return !isAir(at.getX(), at.getY() - 1, at.getZ());
        }

        /** The plain wall banner of a house's colour, facing {@code facing}; null for a house we do not know. */
        @Nullable
        private static BlockState bannerState(String house, Direction facing) {
            net.minecraft.world.level.block.Block block = switch (house.toLowerCase(Locale.ROOT)) {
                case "gryffindor" -> Blocks.RED_WALL_BANNER;
                case "slytherin" -> Blocks.GREEN_WALL_BANNER;
                case "ravenclaw" -> Blocks.BLUE_WALL_BANNER;
                case "hufflepuff" -> Blocks.YELLOW_WALL_BANNER;
                default -> null;
            };
            return block == null ? null : block.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
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

    private enum Theme {
        LIBRARY, SPELL_STUDY, DUNGEON, GREAT_HALL, TOWER;

        /** The theme an anchors file names (any case), or null for one the loot table does not know. */
        @Nullable
        static Theme byName(String name) {
            for (Theme t : values()) {
                if (t.name().equalsIgnoreCase(name)) {
                    return t;
                }
            }
            return null;
        }
    }

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
