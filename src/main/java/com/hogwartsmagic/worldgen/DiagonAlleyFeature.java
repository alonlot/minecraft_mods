package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.ModVillagers;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagon Alley — a small wizarding district built into the world. Unlike the earlier
 * version (which was a row of identical cabins), each building here is hand-detailed
 * with its own architecture, materials, and props: Gringotts is a marble-and-gold
 * temple with columns, Ollivanders is a tall crooked log shop with a wand workbench
 * and a Wandmaker villager, Weasley's is a chaotic two-story orange façade, Florean
 * Fortescue's spills out onto the street with tables and umbrellas, etc.
 *
 * Layout (top-down, +X east, +Z south, footprint ~78x52):
 *
 *     [ Leaky Cauldron + brick wall ]    [ Eeylops Owls ]   [ Flourish & Blotts ]
 *     ============================ AVENUE ===========================  → [ Gringotts ]
 *                  [ Plaza w/ fountain ]                       [ Tower ]
 *     [ Quality Quidditch ] [ Madam Malkin's ] [ Apothecary ] [ Wheezes ] [ Fortescue's ]
 *
 * WORLDGEN NOTE: do not call SignBlockEntity#updateText — the BE's level pointer is
 * null during feature placement and markUpdated() NPEs. Architecture + named villager
 * shopkeepers identify each building.
 */
public class DiagonAlleyFeature extends Feature<NoneFeatureConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");

    public DiagonAlleyFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource rng = context.random();

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        BlockPos base = new BlockPos(origin.getX(), surfaceY, origin.getZ());

        // Footprint is 78 × 52; require reasonably flat ground so we don't half-bury buildings.
        if (!isReasonablyFlat(level, base, 78, 52, 3)) {
            return false;
        }

        int y = base.getY();
        int x0 = base.getX();
        int z0 = base.getZ();

        clearAndFoundation(level, x0, y, z0, 78, 52);

        // Main avenue: a stone-brick promenade running east, 5 blocks wide centered Z=z0+24.
        int avenueX0 = x0 + 6;
        int avenueZ0 = z0 + 22;
        paveAvenue(level, avenueX0, y, avenueZ0, 64, 5, rng);

        // Curbs along both sides — alternating brick slabs + chiseled accents.
        layCurbs(level, avenueX0 - 1, y, avenueZ0 - 1, 66, 7);

        // Street lamps every 7 blocks along both sides.
        for (int dx = 3; dx < 64; dx += 7) {
            buildLanternPole(level, new BlockPos(avenueX0 + dx, y + 1, avenueZ0 - 2));
            buildLanternPole(level, new BlockPos(avenueX0 + dx, y + 1, avenueZ0 + 6));
        }

        // Benches + planters between the lamps.
        for (int dx = 7; dx < 60; dx += 14) {
            placeBench(level, new BlockPos(avenueX0 + dx, y + 1, avenueZ0 - 2), Direction.SOUTH);
            placeBench(level, new BlockPos(avenueX0 + dx, y + 1, avenueZ0 + 6), Direction.NORTH);
        }
        for (int dx = 5; dx < 62; dx += 5) {
            if ((dx + 2) % 14 == 0) continue;
            placeFlowerPlanter(level, new BlockPos(avenueX0 + dx, y, avenueZ0 - 3));
            placeFlowerPlanter(level, new BlockPos(avenueX0 + dx, y, avenueZ0 + 7));
        }

        // West entrance: Leaky Cauldron facade + iconic brick wall passage.
        buildLeakyCauldron(level, new BlockPos(x0 + 0, y, z0 + 14));

        // Central plaza fountain (avenue middle).
        buildGrandFountain(level, new BlockPos(avenueX0 + 28, y, avenueZ0));

        // North-row shops (front faces SOUTH, toward avenue).
        buildEeylops(level,           new BlockPos(x0 + 16, y, z0 + 13), Direction.SOUTH);
        buildFlourishAndBlotts(level, new BlockPos(x0 + 30, y, z0 + 12), Direction.SOUTH);
        buildOllivanders(level,       new BlockPos(x0 + 45, y, z0 + 10), Direction.SOUTH);

        // South-row shops (front faces NORTH, toward avenue).
        buildQuidditchSupplies(level, new BlockPos(x0 + 16, y, z0 + 30), Direction.NORTH);
        buildMadamMalkins(level,      new BlockPos(x0 + 28, y, z0 + 30), Direction.NORTH);
        buildApothecary(level,        new BlockPos(x0 + 39, y, z0 + 30), Direction.NORTH);
        buildWeasleyWheezes(level,    new BlockPos(x0 + 50, y, z0 + 30), Direction.NORTH);
        buildFortescues(level,        new BlockPos(x0 + 62, y, z0 + 30), Direction.NORTH);

        // East end: massive Gringotts bank as the centerpiece landmark.
        buildGringotts(level, new BlockPos(x0 + 60, y, z0 + 10));

        // Tall lantern tower at far east, behind Gringotts — visible from far away.
        buildLanternTower(level, new BlockPos(x0 + 74, y, z0 + 22));

        // Persistent locator: record center position.
        ServerLevel serverLevel = level.getLevel();
        BlockPos announcePos = new BlockPos(avenueX0 + 30, y, avenueZ0 + 2);
        DiagonAlleyTracker.get(serverLevel).add(announcePos);

        MinecraftServer server = serverLevel.getServer();
        if (server != null) {
            Component msg = Component.literal("[Hogwarts Magic] Diagon Alley generated at "
                    + announcePos.getX() + ", " + announcePos.getY() + ", " + announcePos.getZ()
                    + " — try /findalley");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
            }
        }
        LOGGER.info("Diagon Alley generated at {} {} {}", announcePos.getX(), announcePos.getY(), announcePos.getZ());
        return true;
    }

    // =================================================================================
    // Site preparation
    // =================================================================================

    private boolean isReasonablyFlat(WorldGenLevel level, BlockPos base, int sx, int sz, int tolerance) {
        int by = base.getY();
        int minY = by, maxY = by;
        for (int dx = 0; dx < sx; dx += 4) {
            for (int dz = 0; dz < sz; dz += 4) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, base.getX() + dx, base.getZ() + dz);
                if (h < minY) minY = h;
                if (h > maxY) maxY = h;
                if (maxY - minY > tolerance) return false;
            }
        }
        return true;
    }

    private void clearAndFoundation(WorldGenLevel level, int x0, int y, int z0, int sx, int sz) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                for (int dy = 0; dy < 26; dy++) {
                    BlockPos p = new BlockPos(x0 + dx, y + dy, z0 + dz);
                    if (!level.isEmptyBlock(p)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // Cobblestone + stone foundation: cobble surface, packed stone below.
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 + dz), Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y - 2, z0 + dz), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y - 3, z0 + dz), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    private void paveAvenue(WorldGenLevel level, int x0, int y, int z0, int sx, int sz, RandomSource rng) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                BlockState paving;
                int h = (dx * 31 + dz * 17) & 0x7FFFFFFF;
                int v = h % 11;
                if (v == 0) paving = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                else if (v == 1) paving = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                else if (v == 2) paving = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                else if (v == 3) paving = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                else paving = Blocks.STONE_BRICKS.defaultBlockState();
                level.setBlock(new BlockPos(x0 + dx, y, z0 + dz), paving, 2);
            }
        }
    }

    private void layCurbs(WorldGenLevel level, int x0, int y, int z0, int sx, int sz) {
        BlockState curb = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        for (int dx = 0; dx < sx; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y, z0), curb, 2);
            level.setBlock(new BlockPos(x0 + dx, y, z0 + sz - 1), curb, 2);
        }
    }

    // =================================================================================
    // Buildings — Leaky Cauldron + brick wall passage
    // =================================================================================

    private void buildLeakyCauldron(WorldGenLevel level, BlockPos corner) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();

        // Pub building: 8 wide × 9 deep × 5 tall, dark-oak log frame with cobblestone infill.
        BlockState frame = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState wall = Blocks.COBBLESTONE.defaultBlockState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_STAIRS.defaultBlockState();

        // Footprint + floor.
        for (int dx = 0; dx < 8; dx++) {
            for (int dz = 0; dz < 9; dz++) {
                BlockPos p = new BlockPos(x0 + dx, y0, z0 + dz);
                boolean edge = dx == 0 || dx == 7 || dz == 0 || dz == 8;
                level.setBlock(p, edge ? wall : floor, 2);
            }
        }
        // Walls + corner logs as Tudor framing.
        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = 0; dx < 8; dx++) {
                for (int dz = 0; dz < 9; dz++) {
                    boolean edge = dx == 0 || dx == 7 || dz == 0 || dz == 8;
                    if (!edge) continue;
                    boolean isCorner = (dx == 0 || dx == 7) && (dz == 0 || dz == 8);
                    // Vertical timber strip at door, and Tudor crosspiece at y0+3.
                    boolean timber = isCorner || (dy == 3 && (dx == 2 || dx == 5)) || (dx == 4 && dy < 3);
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), timber ? frame : wall, 2);
                }
            }
        }
        // Diamond windows on front (east face).
        level.setBlock(new BlockPos(x0 + 7, y0 + 2, z0 + 2), Blocks.BROWN_STAINED_GLASS_PANE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 7, y0 + 2, z0 + 6), Blocks.BROWN_STAINED_GLASS_PANE.defaultBlockState(), 2);

        // Sloped roof — double-tier gabled stairs.
        for (int dz = 0; dz < 9; dz++) {
            for (int dx = 0; dx < 8; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 5, z0 + dz), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
            }
        }
        // Ridge with stair pairs.
        for (int dz = 0; dz < 9; dz++) {
            BlockState southStair = roof.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            BlockState northStair = roof.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            level.setBlock(new BlockPos(x0 + 3, y0 + 6, z0 + dz), southStair, 2);
            level.setBlock(new BlockPos(x0 + 4, y0 + 6, z0 + dz), northStair, 2);
        }

        // Chimney with smoke (campfire on top → light + smoke).
        for (int dy = 5; dy <= 8; dy++) {
            level.setBlock(new BlockPos(x0 + 1, y0 + dy, z0 + 1), Blocks.BRICKS.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + 1, y0 + 9, z0 + 1), Blocks.CAMPFIRE.defaultBlockState(), 2);

        // Door on east face (toward the brick wall passage on east).
        BlockState doorLower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = doorLower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        level.setBlock(new BlockPos(x0 + 7, y0 + 1, z0 + 4), doorLower, 2);
        level.setBlock(new BlockPos(x0 + 7, y0 + 2, z0 + 4), doorUpper, 2);
        // Hanging lantern over the door.
        level.setBlock(new BlockPos(x0 + 7, y0 + 4, z0 + 4),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Pub interior: a bar counter + barrels of ale + a fire.
        for (int dx = 1; dx < 7; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 7), Blocks.DARK_OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        level.setBlock(new BlockPos(x0 + 1, y0 + 1, z0 + 7), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 6, y0 + 1, z0 + 7), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 1, y0 + 1, z0 + 2), Blocks.CAMPFIRE.defaultBlockState(), 2);
        // Interior table + stools.
        for (int dx = 3; dx <= 4; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 3), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
        }

        // Now the famous brick wall passage at +X +9.
        buildBrickWallPassage(level, new BlockPos(x0 + 10, y0, z0 + 3));

        // Bartender villager (CARTOGRAPHER for the curiosity-shop feel).
        spawnNamedVillager(level, new BlockPos(x0 + 3, y0 + 1, z0 + 5), VillagerProfession.CARTOGRAPHER, "Tom (Innkeeper)");
    }

    private void buildBrickWallPassage(WorldGenLevel level, BlockPos base) {
        int x = base.getX();
        int y = base.getY();
        int z0 = base.getZ();

        // Two-block-thick brick wall with central 4×5 opening.
        for (int wallX = x; wallX <= x + 1; wallX++) {
            for (int dz = -2; dz < 6; dz++) {
                for (int dy = 0; dy < 8; dy++) {
                    BlockPos p = new BlockPos(wallX, y + dy, z0 + dz);
                    boolean opening = dz >= 0 && dz <= 3 && dy >= 0 && dy < 5;
                    if (opening) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                        continue;
                    }
                    BlockState block = ((dz + dy + wallX) % 4 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : ((dz + dy) % 5 == 0) ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : Blocks.BRICKS.defaultBlockState();
                    level.setBlock(p, block, 2);
                }
            }
        }

        // Crown: chiseled brick capstone + rampart stairs.
        for (int dz = -2; dz < 6; dz++) {
            level.setBlock(new BlockPos(x, y + 8, z0 + dz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x + 1, y + 8, z0 + dz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x + 2, y + 8, z0 + dz),
                    Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                            .setValue(BlockStateProperties.HALF, Half.BOTTOM), 2);
        }

        // "Swirl reveal": angled stairs inside opening as if bricks spiraled outward.
        BlockState southStair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM);
        BlockState northStair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM);
        level.setBlock(new BlockPos(x, y + 4, z0 - 1), southStair, 2);
        level.setBlock(new BlockPos(x, y + 4, z0 + 4), northStair, 2);
        level.setBlock(new BlockPos(x + 1, y + 4, z0 - 1), southStair, 2);
        level.setBlock(new BlockPos(x + 1, y + 4, z0 + 4), northStair, 2);

        // Key bricks (Hagrid's tap pattern hint): chiseled blocks at right side, 3 up + 2 across.
        for (int marker = 0; marker < 3; marker++) {
            level.setBlock(new BlockPos(x + 1, y + 1 + marker, z0 + 5), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        for (int marker = 0; marker < 2; marker++) {
            level.setBlock(new BlockPos(x + 1, y + 3, z0 + 5 + marker), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }

        // Hanging lanterns inside the passage.
        level.setBlock(new BlockPos(x, y + 4, z0 + 1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        level.setBlock(new BlockPos(x + 1, y + 4, z0 + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Bin (cauldron) reference + stacked barrels.
        level.setBlock(new BlockPos(x - 1, y + 1, z0 + 6), Blocks.CAULDRON.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x - 1, y + 1, z0 - 3), Blocks.BARREL.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x - 1, y + 2, z0 - 3), Blocks.BARREL.defaultBlockState(), 2);
    }

    // =================================================================================
    // Buildings — Ollivanders (wandmaker)
    // =================================================================================

    private void buildOllivanders(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        // Tall narrow shop: 9 wide × 9 deep × 8 tall. Window-heavy front, leaning roof.
        BlockState log = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState window = Blocks.BROWN_STAINED_GLASS_PANE.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        int sx = 9, sz = 9, h = 7;

        // Floor.
        fillRect(level, x0, y0, z0, sx, 1, sz, floor);

        // Walls with corner timbers + window strip.
        for (int dy = 0; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean isCorner = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    BlockState block;
                    if (isCorner) block = log;
                    else if (dy == 0) block = log;
                    else if (dy == h) block = log;
                    else if ((dy == 2 || dy == 5) && !isCorner) block = log;
                    else block = planks;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), block, 2);
                }
            }
        }
        // Front face windows (faces -Z, south is +Z so front=NORTH means avenue is north).
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx == sx / 2) continue;
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, z0), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 4, z0), window, 2);
        }
        // Front display window — dramatic floor-to-ceiling pane on the front.
        for (int dy = 2; dy <= h - 1; dy++) {
            level.setBlock(new BlockPos(x0 + 1, y0 + dy, z0 + sz - 1), window, 2);
            level.setBlock(new BlockPos(x0 + sx - 2, y0 + dy, z0 + sz - 1), window, 2);
        }

        // Doorway in center front.
        int doorX = x0 + sx / 2;
        placeDoor(level, doorX, y0 + 1, frontZ, front, Blocks.DARK_OAK_DOOR);

        // Awning over door.
        placeAwning(level, doorX, y0 + 4, frontZ, front, Blocks.SPRUCE_STAIRS);

        // Sloped roof — TWO peaks (mansard style) for a leaning Diagon look.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), planks, 2);
            }
        }
        for (int dz = 0; dz < sz; dz++) {
            BlockState eastStair = Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            BlockState westStair = Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            level.setBlock(new BlockPos(x0 + 3, y0 + h + 2, z0 + dz), eastStair, 2);
            level.setBlock(new BlockPos(x0 + 5, y0 + h + 2, z0 + dz), westStair, 2);
            level.setBlock(new BlockPos(x0 + 4, y0 + h + 2, z0 + dz), planks, 2);
        }
        // Chimney.
        for (int dy = h + 1; dy < h + 4; dy++) {
            level.setBlock(new BlockPos(x0 + 1, y0 + dy, z0 + 1), Blocks.BRICKS.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + 1, y0 + h + 4, z0 + 1), Blocks.CAMPFIRE.defaultBlockState(), 2);

        // Interior: dense bookshelves stacked tall (wand boxes), lecterns, wand workbench.
        for (int dz = 2; dz < sz - 1; dz++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dz == sz / 2) continue;
                level.setBlock(new BlockPos(x0 + 1, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + sx - 2, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
            }
        }
        // Lecterns in center.
        level.setBlock(new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + 2), Blocks.LECTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + 2), Blocks.LECTERN.defaultBlockState(), 2);
        // Wand Workbench — backs Wandmaker villager into the WANDMAKER profession via POI.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 2), ExampleMod.WAND_WORKBENCH.get().defaultBlockState(), 2);
        // Counter slabs flanking the workbench.
        level.setBlock(new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + sz - 2), Blocks.SPRUCE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        level.setBlock(new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + sz - 2), Blocks.SPRUCE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);

        // Carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.BROWN_CARPET.defaultBlockState(), 2);
        }
        // Hanging lantern.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Wandmaker villager — needs the wand_workbench POI nearby (which we just placed).
        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                ModVillagers.WANDMAKER.get(), "Mr. Ollivander");
    }

    // =================================================================================
    // Buildings — Gringotts (the centerpiece bank)
    // =================================================================================

    private void buildGringotts(WorldGenLevel level, BlockPos corner) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        // Massive marble bank: 14 wide × 16 deep × 12 tall. Pillars, gold accents, two stories.
        int sx = 14, sz = 16, hLow = 6, hHigh = 11;
        BlockState marble = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState roof = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState accent = Blocks.GOLD_BLOCK.defaultBlockState();

        // Wide stone-brick steps leading up to the entrance (south face).
        for (int step = 0; step < 3; step++) {
            for (int dx = 0; dx < sx; dx++) {
                BlockState s = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                        .setValue(BlockStateProperties.HALF, Half.BOTTOM);
                level.setBlock(new BlockPos(x0 + dx, y0 + step, z0 - step - 1), s, 2);
            }
        }

        // Foundation.
        fillRect(level, x0, y0, z0, sx, 1, sz, floor);

        // Outer walls — solid marble with pillar columns at each column position.
        for (int dy = 1; dy <= hLow; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean isPillar = (dx == 0 || dx == sx - 1 || dx == 4 || dx == 9) && (dz == 0 || dz == sz - 1)
                            || (dz == 0 && (dx == 3 || dx == 10));
                    BlockState block = isPillar ? pillar : marble;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), block, 2);
                }
            }
        }

        // Grand colonnade across the south face — 6 columns at y0+1..y0+hLow.
        for (int col : new int[]{1, 3, 5, 8, 10, 12}) {
            for (int dy = 1; dy <= hLow; dy++) {
                level.setBlock(new BlockPos(x0 + col, y0 + dy, z0), pillar, 2);
            }
            // Column base + cap.
            level.setBlock(new BlockPos(x0 + col, y0 + 1, z0), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + col, y0 + hLow, z0), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
        }
        // Gap between columns is open (passage into porch).
        for (int dx = 0; dx < sx; dx++) {
            boolean isPillar = (dx == 1 || dx == 3 || dx == 5 || dx == 8 || dx == 10 || dx == 12);
            if (!isPillar) {
                for (int dy = 1; dy <= hLow - 1; dy++) {
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // Pediment above the colonnade — chiseled quartz frieze + gold trim.
        for (int dx = 0; dx < sx; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + hLow + 1, z0), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + hLow + 2, z0), accent, 2);
        }

        // Second story — narrower than first, set in 1 block.
        for (int dy = hLow + 2; dy <= hHigh; dy++) {
            for (int dx = 1; dx < sx - 1; dx++) {
                for (int dz = 1; dz < sz - 1; dz++) {
                    boolean edge = dx == 1 || dx == sx - 2 || dz == 1 || dz == sz - 2;
                    if (edge) {
                        // Windows every other block on second story.
                        boolean window = (dx % 2 == 0) && dy == hLow + 4;
                        level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                window ? Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState() : marble, 2);
                    }
                }
            }
        }
        // Inner roof at hHigh+1 + golden pyramid cap.
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dz = 1; dz < sz - 1; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + hHigh + 1, z0 + dz), roof, 2);
            }
        }
        // Stepped pyramid roof — three tiers shrinking inward, gold-block crown.
        for (int tier = 0; tier < 3; tier++) {
            int inset = tier + 2;
            for (int dx = inset; dx < sx - inset; dx++) {
                for (int dz = inset; dz < sz - inset; dz++) {
                    level.setBlock(new BlockPos(x0 + dx, y0 + hHigh + 2 + tier, z0 + dz),
                            tier == 2 ? accent : marble, 2);
                }
            }
        }
        // Golden spire on top.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + hHigh + 5, z0 + sz / 2), accent, 2);
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + hHigh + 6, z0 + sz / 2),
                Blocks.LIGHTNING_ROD.defaultBlockState(), 2);

        // INTERIOR: marble floor, gold-block vault wall at back, iron-bar teller counter.
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dz = 1; dz < sz - 1; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + dz), floor, 2);
            }
        }
        // Vault gold blocks across back wall.
        for (int dx = 2; dx < sx - 2; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), accent, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), accent, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, z0 + sz - 2), accent, 2);
        }
        // Iron bars in front of vault as a teller barrier.
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + sz - 4), Blocks.IRON_BARS.defaultBlockState(), 2);
            }
        }
        // Marble counter in front of bars with teller windows.
        for (int dx = 2; dx < sx - 2; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 5), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        // Long red carpet from entrance to vault.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.RED_CARPET.defaultBlockState(), 2);
        }

        // Chandeliers — hanging lanterns from the ceiling.
        for (int dx : new int[]{3, sx / 2, sx - 4}) {
            for (int dz : new int[]{3, sz / 2, sz - 4}) {
                level.setBlock(new BlockPos(x0 + dx, y0 + hLow - 1, z0 + dz),
                        Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
            }
        }

        // Goblin teller villager (weaponsmith for the gold-handling vibe).
        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 6),
                VillagerProfession.WEAPONSMITH, "Gringotts Goblin");
        // Side guard (armorer).
        spawnNamedVillager(level, new BlockPos(x0 + 2, y0 + 1, z0 + sz - 7),
                VillagerProfession.ARMORER, "Vault Guard");
    }

    // =================================================================================
    // Buildings — Eeylops Owl Emporium
    // =================================================================================

    private void buildEeylops(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 11, sz = 8, h = 5;
        BlockState log = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
        BlockState planks = Blocks.BIRCH_PLANKS.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState window = Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        // Walls.
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean corner2 = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), corner2 || dy == h ? log : planks, 2);
                }
            }
        }
        // Front windows.
        int frontZ = (front == Direction.SOUTH) ? z0 + sz - 1 : z0;
        for (int dx = 2; dx < sx - 2; dx++) {
            if (dx == sx / 2) continue;
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, z0), window, 2);
        }
        // Door.
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.BIRCH_DOOR);
        placeAwning(level, x0 + sx / 2, y0 + 4, frontZ, front, Blocks.BIRCH_STAIRS);

        // Mansard roof (two-pitch).
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), planks, 2);
            }
        }
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + sz / 2), Blocks.BIRCH_PLANKS.defaultBlockState(), 2);
        }

        // INTERIOR: rows of owl perches (oak fences) on hay bale stands, scatter of feathers.
        for (int dx = 2; dx < sx - 2; dx += 2) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.HAY_BLOCK.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, z0 + sz - 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
            // Spawn an owl on top of perch.
        }
        // Cages stacked (display).
        level.setBlock(new BlockPos(x0 + 1, y0 + 1, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 1, y0 + 2, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx - 2, y0 + 1, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx - 2, y0 + 2, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        // Carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.WHITE_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Spawn an actual owl entity!
        spawnOwl(level, new BlockPos(x0 + sx / 2, y0 + 2, z0 + sz - 2));
        spawnNamedVillager(level, new BlockPos(x0 + sx / 2 - 2, y0 + 1, z0 + sz / 2),
                VillagerProfession.LEATHERWORKER, "Mr. Eeylops");
    }

    // =================================================================================
    // Buildings — Flourish and Blotts (bookshop)
    // =================================================================================

    private void buildFlourishAndBlotts(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 12, sz = 9, h = 8;
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState wall = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState window = Blocks.LIME_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, planks);
        // Walls — Tudor framing.
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean corner2 = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    boolean beam = dy == 3 || dy == h || corner2 || (dx == 4 && dy < 6) || (dx == sx - 5 && dy < 6);
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), beam ? log : wall, 2);
                }
            }
        }
        // Big bay window on the front showcasing books.
        int frontZ = (front == Direction.SOUTH) ? z0 + sz - 1 : z0;
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx >= sx / 2 - 1 && dx <= sx / 2 + 1) continue; // skip door area
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, z0), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 4, z0), window, 2);
        }
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.DARK_OAK_DOOR);
        placeAwning(level, x0 + sx / 2, y0 + 4, frontZ, front, Blocks.DARK_OAK_STAIRS);

        // Two-tier mansard roof.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), planks, 2);
            }
        }
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dz = 1; dz < sz - 1; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + dz), planks, 2);
            }
        }
        // Bell tower spire on top.
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + h + 3 + dy, z0 + sz / 2), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h + 6, z0 + sz / 2), Blocks.BELL.defaultBlockState(), 2);

        // INTERIOR: walls of bookshelves stacked floor-to-ceiling, lecterns at counters, hanging lanterns.
        for (int dz = 2; dz < sz - 1; dz++) {
            for (int dy = 1; dy <= 5; dy++) {
                if (dz == sz / 2) continue;
                level.setBlock(new BlockPos(x0 + 1, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + sx - 2, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
            }
        }
        // Centre book table (planks + lecterns).
        for (int dx = 4; dx < sx - 4; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 3), Blocks.DARK_OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2 - 1, y0 + 2, z0 + sz - 3), Blocks.LECTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx / 2 + 1, y0 + 2, z0 + sz - 3), Blocks.LECTERN.defaultBlockState(), 2);
        // Green carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.LIME_CARPET.defaultBlockState(), 2);
        }
        // Hanging lanterns.
        level.setBlock(new BlockPos(x0 + 3, y0 + h - 1, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        level.setBlock(new BlockPos(x0 + sx - 4, y0 + h - 1, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 4),
                VillagerProfession.LIBRARIAN, "Mr. Flourish");
    }

    // =================================================================================
    // Buildings — Quality Quidditch Supplies
    // =================================================================================

    private void buildQuidditchSupplies(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 10, sz = 9, h = 5;
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState window = Blocks.RED_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean corner2 = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), (corner2 || dy == h) ? log : planks, 2);
                }
            }
        }
        // Display windows along front (faces NORTH → frontZ = z0).
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx == sx / 2) continue;
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, frontZ), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, frontZ), window, 2);
        }
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.DARK_OAK_DOOR);
        placeAwning(level, x0 + sx / 2, y0 + 4, frontZ, front, Blocks.DARK_OAK_STAIRS);
        // Red banner over door.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, frontZ), Blocks.RED_WOOL.defaultBlockState(), 2);

        // Pitched roof.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), planks, 2);
            }
        }
        for (int dx = 1; dx < sx - 1; dx++) {
            BlockState n = Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
            BlockState s = Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + sz / 2 - 1), s, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + sz / 2 + 1), n, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + sz / 2), planks, 2);
        }

        // INTERIOR: broom rack display, counter with golden snitch (gold block in the air).
        for (int dx = 2; dx < sx - 2; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
        }
        // Snitch in a glass cage.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 2, z0 + 2), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 2, z0 + 2).relative(d), Blocks.GLASS_PANE.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 3, z0 + 2), Blocks.GLASS_PANE.defaultBlockState(), 2);

        // Counter.
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 4), Blocks.DARK_OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        // Red carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.RED_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                VillagerProfession.FLETCHER, "Broom Master");
    }

    // =================================================================================
    // Buildings — Madam Malkin's Robes for All Occasions
    // =================================================================================

    private void buildMadamMalkins(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 9, sz = 9, h = 7; // taller, two-story
        BlockState log = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState wall = Blocks.PINK_TERRACOTTA.defaultBlockState();
        BlockState window = Blocks.PINK_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, planks);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean corner2 = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    boolean midFloor = dy == 4;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                            corner2 ? log : (midFloor ? log : (dy == h ? log : wall)), 2);
                }
            }
        }
        // Tall arched display window on front, ground floor.
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx == sx / 2) continue;
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, frontZ), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, frontZ), window, 2);
        }
        // Second-story windows.
        for (int dx = 1; dx < sx - 1; dx += 2) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 5, frontZ), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 6, frontZ), window, 2);
        }
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.OAK_DOOR);
        placeAwning(level, x0 + sx / 2, y0 + 4, frontZ, front, Blocks.OAK_STAIRS);
        // Pink banner.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, frontZ), Blocks.PINK_WOOL.defaultBlockState(), 2);

        // Pitched roof.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), planks, 2);
            }
        }
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + sz / 2), planks, 2);
        }

        // INTERIOR: armor stands (wool stacks) + loom + curtained dressing room.
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx % 2 == 0) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.PINK_WOOL.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + 1), Blocks.MAGENTA_WOOL.defaultBlockState(), 2);
            }
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 2), Blocks.LOOM.defaultBlockState(), 2);
        // Pink carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.PINK_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 3, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                VillagerProfession.SHEPHERD, "Madam Malkin");
    }

    // =================================================================================
    // Buildings — Slug & Jiggers Apothecary
    // =================================================================================

    private void buildApothecary(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 9, sz = 9, h = 6;
        BlockState wall = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState accent = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState window = Blocks.GREEN_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean band = dy == 4;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), band ? accent : wall, 2);
                }
            }
        }
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx == sx / 2) continue;
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, frontZ), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, frontZ), window, 2);
        }
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.SPRUCE_DOOR);
        placeAwning(level, x0 + sx / 2, y0 + 4, frontZ, front, Blocks.SPRUCE_STAIRS);
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, frontZ), Blocks.GREEN_WOOL.defaultBlockState(), 2);

        // Sloped slate roof.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
            }
        }
        // Chimney on roof.
        for (int dy = h + 1; dy < h + 5; dy++) {
            level.setBlock(new BlockPos(x0 + 1, y0 + dy, z0 + 1), Blocks.BRICKS.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + 1, y0 + h + 5, z0 + 1), Blocks.CAMPFIRE.defaultBlockState(), 2);

        // INTERIOR: brewing stand wall + cauldrons + flower pots of ingredients.
        for (int dx = 2; dx < sx - 2; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + 2), Blocks.CAULDRON.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx - 3, y0 + 1, z0 + 2), Blocks.CAULDRON.defaultBlockState(), 2);
        for (int dx : new int[]{2, 4, sx - 3}) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + 1),
                    (dx % 2 == 0 ? Blocks.POTTED_FERN : Blocks.POTTED_AZURE_BLUET).defaultBlockState(), 2);
        }
        // Green carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.GREEN_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                VillagerProfession.CLERIC, "Slug & Jiggers");
    }

    // =================================================================================
    // Buildings — Weasley's Wizard Wheezes
    // =================================================================================

    private void buildWeasleyWheezes(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 10, sz = 9, h = 8; // tallest of the shops
        BlockState frame = Blocks.STRIPPED_ACACIA_LOG.defaultBlockState();
        BlockState wall = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        BlockState floor = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        BlockState window = Blocks.ORANGE_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean corner2 = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    boolean band = dy == 3 || dy == 5;
                    boolean stripe = ((dx + dy + dz) & 1) == 0;
                    BlockState block = corner2 || band ? frame
                            : (stripe ? wall : Blocks.YELLOW_TERRACOTTA.defaultBlockState());
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), block, 2);
                }
            }
        }
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        for (int dx = 1; dx < sx - 1; dx += 2) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, frontZ), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 6, frontZ), window, 2);
        }
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.ACACIA_DOOR);
        placeAwning(level, x0 + sx / 2, y0 + 4, frontZ, front, Blocks.ACACIA_STAIRS);
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 7, frontZ), Blocks.ORANGE_WOOL.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, frontZ), Blocks.YELLOW_WOOL.defaultBlockState(), 2);

        // Roof with chaotic angles.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), Blocks.ACACIA_PLANKS.defaultBlockState(), 2);
            }
        }
        // Giant top hat on roof (the iconic Weasley sign): tower of orange wool.
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + h + 2 + dy, z0 + sz / 2), Blocks.ORANGE_WOOL.defaultBlockState(), 2);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + h + 2, z0 + sz / 2).relative(d), Blocks.ORANGE_WOOL.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h + 5, z0 + sz / 2), Blocks.YELLOW_WOOL.defaultBlockState(), 2);

        // INTERIOR: chaos shelves — wool blocks, TNT (defused), jukebox, redstone lamps.
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1),
                    ((dx + 1) % 3 == 0 ? Blocks.TNT : Blocks.YELLOW_WOOL).defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + 1),
                    ((dx % 2 == 0) ? Blocks.PINK_WOOL : Blocks.LIME_WOOL).defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz / 2), Blocks.JUKEBOX.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + sz / 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + sz / 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
        // Carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.ORANGE_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + sz - 2),
                VillagerProfession.TOOLSMITH, "Fred Weasley");
        spawnNamedVillager(level, new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + sz - 2),
                VillagerProfession.TOOLSMITH, "George Weasley");
    }

    // =================================================================================
    // Buildings — Florean Fortescue's Ice Cream Parlour (with outdoor seating)
    // =================================================================================

    private void buildFortescues(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 10, sz = 8, h = 5;
        BlockState wall = Blocks.CALCITE.defaultBlockState();
        BlockState floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean band = dy == 3;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                            band ? Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState() : wall, 2);
                }
            }
        }
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        // Big shop window taking up most of the front.
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx == sx / 2) continue;
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, frontZ), window, 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 3, frontZ), window, 2);
        }
        placeDoor(level, x0 + sx / 2, y0 + 1, frontZ, front, Blocks.BIRCH_DOOR);
        // Striped awning (light blue / white wool checkered).
        Direction awningFacing = (front == Direction.NORTH) ? Direction.SOUTH : Direction.NORTH;
        BlockState awningStair = Blocks.BIRCH_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, awningFacing)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        int awnZ = (front == Direction.NORTH) ? frontZ - 1 : frontZ + 1;
        for (int dx = -3; dx <= 3; dx++) {
            level.setBlock(new BlockPos(x0 + sx / 2 + dx, y0 + 4, awnZ), awningStair, 2);
            level.setBlock(new BlockPos(x0 + sx / 2 + dx, y0 + 5, awnZ),
                    (dx % 2 == 0) ? Blocks.LIGHT_BLUE_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState(), 2);
        }
        // Flat roof with railing.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
            }
        }
        for (int dx = 0; dx < sx; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + h + 2, z0 + sz - 1), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
        }

        // OUTDOOR SEATING — tables (oak slabs on fence posts) with stools in front of the shop.
        int seatingZ = (front == Direction.NORTH) ? z0 - 4 : z0 + sz + 3;
        for (int dx = 0; dx <= 6; dx += 3) {
            placeCafeTable(level, new BlockPos(x0 + 1 + dx, y0 + 1, seatingZ));
        }

        // INTERIOR: counter with cake (ice cream stand-in) + packed-ice freezer + blue ice display.
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 2), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2 - 1, y0 + 2, z0 + 2), Blocks.CAKE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + sx / 2 + 1, y0 + 2, z0 + 2), Blocks.CAKE.defaultBlockState(), 2);
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.PACKED_ICE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2),
                    (dx % 2 == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 2);
        }
        // Light-blue carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            level.setBlock(new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.LIGHT_BLUE_CARPET.defaultBlockState(), 2);
        }
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                VillagerProfession.BUTCHER, "Florean Fortescue");
    }

    private void placeCafeTable(WorldGenLevel level, BlockPos at) {
        // Fence post + slab top = café table.
        level.setBlock(at, Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(at.above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
        // Stools on either side.
        level.setBlock(at.east(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(at.east().above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
        level.setBlock(at.west(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        level.setBlock(at.west().above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
    }

    // =================================================================================
    // Plaza fountain + tower + props
    // =================================================================================

    private void buildGrandFountain(WorldGenLevel level, BlockPos corner) {
        int x0 = corner.getX();
        int y = corner.getY();
        int z0 = corner.getZ();
        int radius = 3;
        // Octagonal-ish basin.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int r = Math.abs(dx) + Math.abs(dz);
                if (r > radius + 1) continue;
                BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
                if (r == radius + 1) {
                    level.setBlock(p, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
                } else if (r == radius) {
                    level.setBlock(p, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
                } else {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(p.below(), Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
        // Tiered pillar with sea-lantern crown.
        BlockPos pillar = new BlockPos(x0, y, z0);
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(pillar.above(dy), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlock(pillar.above(2).relative(d), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, d)
                    .setValue(BlockStateProperties.HALF, Half.TOP), 2);
        }
        level.setBlock(pillar.above(3), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        level.setBlock(pillar.above(4), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        level.setBlock(pillar.above(5), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
        // Water sources above to pour from the column (decorative).
        level.setBlock(pillar.above(2).north().above(), Blocks.WATER.defaultBlockState(), 2);
        level.setBlock(pillar.above(2).south().above(), Blocks.WATER.defaultBlockState(), 2);
    }

    private void buildLanternTower(WorldGenLevel level, BlockPos base) {
        int x0 = base.getX();
        int y0 = base.getY();
        int z0 = base.getZ();
        int height = 22;
        // Deep foundation.
        for (int dx = -1; dx <= 5; dx++) {
            for (int dz = -1; dz <= 5; dz++) {
                for (int dy = -3; dy < 0; dy++) {
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Square tower with mixed brick textures.
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                    if (!edge) continue;
                    BlockState b = ((dx + dz + dy) % 7 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : ((dy % 4 == 3 && (dx == 2 || dz == 2)) ? Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState()
                            : ((dy % 7 == 6) ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()));
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), b, 2);
                }
            }
        }
        // Crenellated top.
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                if (edge && (dx + dz) % 2 == 0) {
                    level.setBlock(new BlockPos(x0 + dx, y0 + height, z0 + dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Bright crown — visible from far away.
        level.setBlock(new BlockPos(x0 + 2, y0 + height, z0 + 2), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 2, y0 + height + 1, z0 + 2), Blocks.GLOWSTONE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 2, y0 + height + 2, z0 + 2), Blocks.JACK_O_LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 2, y0 + height + 3, z0 + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        level.setBlock(new BlockPos(x0 + 2, y0 + height + 4, z0 + 2), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
    }

    private void buildLanternPole(WorldGenLevel level, BlockPos base) {
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(base.above(dy), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        }
        level.setBlock(base.above(3), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        level.setBlock(base.above(2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private void placeBench(WorldGenLevel level, BlockPos pos, Direction facing) {
        BlockState seat = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        level.setBlock(pos, seat, 2);
    }

    private void placeFlowerPlanter(WorldGenLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.HAY_BLOCK.defaultBlockState(), 2);
        BlockState plant = switch ((pos.getX() + pos.getZ()) & 3) {
            case 0 -> Blocks.POTTED_POPPY.defaultBlockState();
            case 1 -> Blocks.POTTED_ALLIUM.defaultBlockState();
            case 2 -> Blocks.POTTED_DANDELION.defaultBlockState();
            default -> Blocks.POTTED_AZURE_BLUET.defaultBlockState();
        };
        level.setBlock(pos.above(), plant, 2);
    }

    // =================================================================================
    // Generic helpers
    // =================================================================================

    private void fillRect(WorldGenLevel level, int x0, int y0, int z0, int sx, int sy, int sz, BlockState block) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dy = 0; dy < sy; dy++) {
                for (int dz = 0; dz < sz; dz++) {
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), block, 2);
                }
            }
        }
    }

    private void placeDoor(WorldGenLevel level, int doorX, int doorY, int doorZ, Direction outward, net.minecraft.world.level.block.Block doorBlock) {
        // Outward is the door facing — door swings open into the building.
        BlockState lower = doorBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, outward.getOpposite())
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upper = doorBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, outward.getOpposite())
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        level.setBlock(new BlockPos(doorX, doorY, doorZ), lower, 2);
        level.setBlock(new BlockPos(doorX, doorY + 1, doorZ), upper, 2);
    }

    private void placeAwning(WorldGenLevel level, int doorX, int awningY, int doorZ, Direction front, net.minecraft.world.level.block.Block stairBlock) {
        Direction awningFacing = front.getOpposite();
        BlockState s = stairBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, awningFacing)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        // Step outward by 1 from the door.
        int outZ = doorZ + (front == Direction.NORTH ? -1 : 1);
        for (int dx = -2; dx <= 2; dx++) {
            level.setBlock(new BlockPos(doorX + dx, awningY, outZ), s, 2);
        }
        BlockState leftCap = stairBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        BlockState rightCap = stairBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        level.setBlock(new BlockPos(doorX - 3, awningY, outZ), leftCap, 2);
        level.setBlock(new BlockPos(doorX + 3, awningY, outZ), rightCap, 2);
        // Lantern hanging from the awning.
        level.setBlock(new BlockPos(doorX - 2, awningY - 1, outZ),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        level.setBlock(new BlockPos(doorX + 2, awningY - 1, outZ),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private void spawnNamedVillager(WorldGenLevel level, BlockPos pos, VillagerProfession profession, String name) {
        Villager villager = EntityType.VILLAGER.create(level.getLevel());
        if (villager == null) return;
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, 3));
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
    }

    private void spawnOwl(WorldGenLevel level, BlockPos pos) {
        // Spawn one of our mod owls if registered, otherwise skip silently.
        try {
            var owlType = com.hogwartsmagic.entity.ModEntities.OWL.get();
            var owl = owlType.create(level.getLevel());
            if (owl != null) {
                owl.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                level.addFreshEntity(owl);
            }
        } catch (Throwable ignored) {}
    }
}
