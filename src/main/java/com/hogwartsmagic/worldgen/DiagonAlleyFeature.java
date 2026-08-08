package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.ModVillagers;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

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

    // Vanilla forbids feature writes outside the 3x3 chunk window around the generating
    // chunk. Clip by *chunk coordinate* — a fixed block radius can spill into a 4th
    // chunk if the anchor sits near a chunk edge. ThreadLocal because worldgen worker
    // threads can run place() concurrently.
    private static final ThreadLocal<BlockPos> SAFE_ANCHOR = ThreadLocal.withInitial(() -> null);

    static void setSafe(WorldGenLevel level, BlockPos pos, BlockState state, int flags) {
        BlockPos anchor = SAFE_ANCHOR.get();
        if (anchor != null) {
            int dcx = (pos.getX() >> 4) - (anchor.getX() >> 4);
            int dcz = (pos.getZ() >> 4) - (anchor.getZ() >> 4);
            if (dcx < -1 || dcx > 1 || dcz < -1 || dcz > 1) return;
        }
        // Underlying write — written via a local alias so the wrapper-replacement pattern
        // `level.setBlock(` doesn't accidentally rewrite this recursion site.
        WorldGenLevel lvl = level;
        lvl.setBlock(pos, state, flags);
    }

    public DiagonAlleyFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Diagon Alley is much larger than a single feature-decoration chunk. Placing it
        // here causes "Detected setBlock in a far chunk" spam and can stall chunk loading
        // on both integrated and dedicated servers, so automatic biome placement is
        // intentionally disabled until this is converted to a real Structure. Use
        // /spawnalley to drop one at the player's location instead.
        return false;
    }

    /**
     * Manually drop a Diagon Alley centered on {@code at}, using the server level
     * directly. No SAFE_ANCHOR is set, so writes are unrestricted — this is safe
     * because {@code ServerLevel.setBlock} outside worldgen has no chunk window.
     * Returns the recorded center position (for /findalley), or {@code null} if the
     * surface couldn't be sampled.
     */
    public static BlockPos spawnManually(ServerLevel level, BlockPos at) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, at.getX(), at.getZ());
        BlockPos base = new BlockPos(at.getX(), y, at.getZ());
        DiagonAlleyFeature inst = new DiagonAlleyFeature(NoneFeatureConfiguration.CODEC);
        return inst.buildAt(level, base, level.getRandom()) ? base : null;
    }

    private boolean buildAt(WorldGenLevel level, BlockPos base, RandomSource rng) {
        int y = base.getY();
        int x0 = base.getX() - 22;
        int z0 = base.getZ() - 22;

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

        // Market stalls narrowing the street + merchant clutter in the gaps between shops,
        // so the avenue reads as a working market rather than an empty promenade.
        placeMarketStall(level, new BlockPos(avenueX0 + 12, y, avenueZ0), Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL);
        placeMarketStall(level, new BlockPos(avenueX0 + 44, y, avenueZ0 + 3), Blocks.LIME_WOOL, Blocks.WHITE_WOOL);
        placeClutterCluster(level, new BlockPos(x0 + 27, y + 1, z0 + 19), 0);
        placeClutterCluster(level, new BlockPos(x0 + 43, y + 1, z0 + 20), 1);
        placeClutterCluster(level, new BlockPos(x0 + 37, y + 1, z0 + 29), 2);
        placeClutterCluster(level, new BlockPos(x0 + 48, y + 1, z0 + 29), 1);
        placeClutterCluster(level, new BlockPos(x0 + 60, y + 1, z0 + 29), 0);

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

        // Themed loot chests in each shop — one or two per building, scattered in interiors.
        placeLootChest(level, new BlockPos(x0 + 4, y + 1, z0 + 21),  Direction.EAST,  LootTheme.LEAKY_CAULDRON_FOOD, rng);
        placeLootChest(level, new BlockPos(x0 + 17, y + 1, z0 + 14), Direction.SOUTH, LootTheme.EEYLOPS_FEATHERS, rng);
        placeLootChest(level, new BlockPos(x0 + 31, y + 1, z0 + 13), Direction.SOUTH, LootTheme.FLOURISH_BOOKS, rng);
        placeLootChest(level, new BlockPos(x0 + 46, y + 1, z0 + 11), Direction.SOUTH, LootTheme.OLLIVANDERS_WANDS, rng);
        placeLootChest(level, new BlockPos(x0 + 17, y + 1, z0 + 31), Direction.NORTH, LootTheme.QUIDDITCH_BROOMS, rng);
        placeLootChest(level, new BlockPos(x0 + 29, y + 1, z0 + 31), Direction.NORTH, LootTheme.MALKINS_CLOTHES, rng);
        placeLootChest(level, new BlockPos(x0 + 40, y + 1, z0 + 31), Direction.NORTH, LootTheme.APOTHECARY_INGREDIENTS, rng);
        placeLootChest(level, new BlockPos(x0 + 51, y + 1, z0 + 31), Direction.NORTH, LootTheme.WHEEZES_PRANKS, rng);
        placeLootChest(level, new BlockPos(x0 + 63, y + 1, z0 + 31), Direction.NORTH, LootTheme.FORTESCUES_SWEETS, rng);
        // Two chests in the Gringotts vault behind the iron bars.
        placeLootChest(level, new BlockPos(x0 + 66, y + 1, z0 + 23), Direction.NORTH, LootTheme.GRINGOTTS_VAULT, rng);
        placeLootChest(level, new BlockPos(x0 + 68, y + 1, z0 + 23), Direction.NORTH, LootTheme.GRINGOTTS_VAULT, rng);

        // Mod-block flourishes: bring the shop interiors to life with the mod's own decor.
        decorateWithModBlocks(level, x0, y, z0);

        // Knockturn Alley — a darker side-fork branching south from the avenue midpoint.
        buildKnockturnAlley(level, new BlockPos(avenueX0 + 22, y, avenueZ0 + 6), rng);

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

    private void clearAndFoundation(WorldGenLevel level, int x0, int y, int z0, int sx, int sz) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                for (int dy = 0; dy < 26; dy++) {
                    BlockPos p = new BlockPos(x0 + dx, y + dy, z0 + dz);
                    if (!level.isEmptyBlock(p)) {
                        setSafe(level,p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // Cobblestone + stone foundation: cobble surface, packed stone below.
                setSafe(level,new BlockPos(x0 + dx, y - 1, z0 + dz), Blocks.COBBLESTONE.defaultBlockState(), 2);
                setSafe(level,new BlockPos(x0 + dx, y - 2, z0 + dz), Blocks.STONE.defaultBlockState(), 2);
                setSafe(level,new BlockPos(x0 + dx, y - 3, z0 + dz), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    /**
     * Worn market-street paving: a cobble/stone/andesite/gravel mix, a darker deepslate
     * wheel-track worn down the center, and moss hugging the edges where the walls shade it.
     * Reads "old and trodden", not "planned".
     */
    private void paveAvenue(WorldGenLevel level, int x0, int y, int z0, int sx, int sz, RandomSource rng) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                int h = (dx * 31 + dz * 17) & 0x7FFFFFFF;
                int v = h % 100;
                boolean center = dz == sz / 2;
                boolean edge = dz == 0 || dz == sz - 1;
                BlockState paving;
                if (center && v < 45) paving = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                else if (edge && v < 18) paving = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                else if (v < 40) paving = Blocks.COBBLESTONE.defaultBlockState();
                else if (v < 60) paving = Blocks.STONE.defaultBlockState();
                else if (v < 75) paving = Blocks.ANDESITE.defaultBlockState();
                else if (v < 85) paving = Blocks.GRAVEL.defaultBlockState();
                else if (v < 92) paving = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                else paving = Blocks.STONE_BRICKS.defaultBlockState();
                setSafe(level,new BlockPos(x0 + dx, y, z0 + dz), paving, 2);
                // Occasional pebble (floor button) for texture underfoot.
                if (((h >> 5) % 53) == 0) {
                    setSafe(level,new BlockPos(x0 + dx, y + 1, z0 + dz),
                            Blocks.STONE_BUTTON.defaultBlockState()
                                    .setValue(BlockStateProperties.ATTACH_FACE,
                                            net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), 2);
                }
            }
        }
    }

    /**
     * A little canopied market stall: fence posts, a two-tone striped wool canopy,
     * a barrel-and-hay counter and a lantern glowing underneath.
     */
    private void placeMarketStall(WorldGenLevel level, BlockPos corner,
                                  net.minecraft.world.level.block.Block woolA,
                                  net.minecraft.world.level.block.Block woolB) {
        int x0 = corner.getX(), y = corner.getY(), z0 = corner.getZ();
        for (int[] post : new int[][]{{0, 0}, {2, 0}, {0, 1}, {2, 1}}) {
            setSafe(level,new BlockPos(x0 + post[0], y + 1, z0 + post[1]), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + post[0], y + 2, z0 + post[1]), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        }
        for (int dx = 0; dx <= 2; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                setSafe(level,new BlockPos(x0 + dx, y + 3, z0 + dz),
                        ((dx & 1) == 0 ? woolA : woolB).defaultBlockState(), 2);
            }
        }
        setSafe(level,new BlockPos(x0, y + 1, z0), Blocks.BARREL.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 1, y + 1, z0), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 2, y + 1, z0), Blocks.BARREL.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 1, y + 2, z0 + 1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    /** Small clusters of merchant clutter dropped into the gaps between shopfronts. */
    private void placeClutterCluster(WorldGenLevel level, BlockPos at, int variant) {
        if (variant == 0) {
            // Stacked delivery barrels.
            setSafe(level,at, Blocks.BARREL.defaultBlockState(), 2);
            setSafe(level,at.above(), Blocks.BARREL.defaultBlockState(), 2);
            setSafe(level,at.east(), Blocks.BARREL.defaultBlockState(), 2);
        } else if (variant == 1) {
            // Brewer's corner: cauldron, hay and a lamp on a post.
            setSafe(level,at, Blocks.CAULDRON.defaultBlockState(), 2);
            setSafe(level,at.east(), Blocks.HAY_BLOCK.defaultBlockState(), 2);
            setSafe(level,at.west(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            setSafe(level,at.west().above(), Blocks.LANTERN.defaultBlockState(), 2);
        } else {
            // Crate with a lid and a composter of scraps.
            setSafe(level,at, Blocks.BARREL.defaultBlockState(), 2);
            setSafe(level,at.above(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM), 2);
            setSafe(level,at.east(), Blocks.COMPOSTER.defaultBlockState(), 2);
        }
    }

    private void layCurbs(WorldGenLevel level, int x0, int y, int z0, int sx, int sz) {
        BlockState curb = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        for (int dx = 0; dx < sx; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y, z0), curb, 2);
            setSafe(level,new BlockPos(x0 + dx, y, z0 + sz - 1), curb, 2);
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
                setSafe(level,p, edge ? wall : floor, 2);
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
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz), timber ? frame : wall, 2);
                }
            }
        }
        // Diamond windows on front (east face).
        setSafe(level,new BlockPos(x0 + 7, y0 + 2, z0 + 2), Blocks.BROWN_STAINED_GLASS_PANE.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 7, y0 + 2, z0 + 6), Blocks.BROWN_STAINED_GLASS_PANE.defaultBlockState(), 2);

        // Sloped roof — double-tier gabled stairs.
        for (int dz = 0; dz < 9; dz++) {
            for (int dx = 0; dx < 8; dx++) {
                setSafe(level,new BlockPos(x0 + dx, y0 + 5, z0 + dz), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
            }
        }
        // Ridge with stair pairs.
        for (int dz = 0; dz < 9; dz++) {
            BlockState southStair = roof.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            BlockState northStair = roof.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM);
            setSafe(level,new BlockPos(x0 + 3, y0 + 6, z0 + dz), southStair, 2);
            setSafe(level,new BlockPos(x0 + 4, y0 + 6, z0 + dz), northStair, 2);
        }

        // Chimney with smoke (campfire on top → light + smoke).
        for (int dy = 5; dy <= 8; dy++) {
            setSafe(level,new BlockPos(x0 + 1, y0 + dy, z0 + 1), Blocks.BRICKS.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + 1, y0 + 9, z0 + 1), Blocks.CAMPFIRE.defaultBlockState(), 2);

        // Door on east face (toward the brick wall passage on east).
        BlockState doorLower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = doorLower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        setSafe(level,new BlockPos(x0 + 7, y0 + 1, z0 + 4), doorLower, 2);
        setSafe(level,new BlockPos(x0 + 7, y0 + 2, z0 + 4), doorUpper, 2);
        // Hanging lantern over the door.
        setSafe(level,new BlockPos(x0 + 7, y0 + 4, z0 + 4),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Pub interior: a bar counter + barrels of ale + a fire.
        for (int dx = 1; dx < 7; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 7), Blocks.DARK_OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        setSafe(level,new BlockPos(x0 + 1, y0 + 1, z0 + 7), Blocks.BARREL.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 6, y0 + 1, z0 + 7), Blocks.BARREL.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 1, y0 + 1, z0 + 2), Blocks.CAMPFIRE.defaultBlockState(), 2);
        // Interior table + stools.
        for (int dx = 3; dx <= 4; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 3), Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
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
                        setSafe(level,p, Blocks.AIR.defaultBlockState(), 2);
                        continue;
                    }
                    BlockState block = ((dz + dy + wallX) % 4 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : ((dz + dy) % 5 == 0) ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : Blocks.BRICKS.defaultBlockState();
                    setSafe(level,p, block, 2);
                }
            }
        }

        // Crown: chiseled brick capstone + rampart stairs.
        for (int dz = -2; dz < 6; dz++) {
            setSafe(level,new BlockPos(x, y + 8, z0 + dz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x + 1, y + 8, z0 + dz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x + 2, y + 8, z0 + dz),
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
        setSafe(level,new BlockPos(x, y + 4, z0 - 1), southStair, 2);
        setSafe(level,new BlockPos(x, y + 4, z0 + 4), northStair, 2);
        setSafe(level,new BlockPos(x + 1, y + 4, z0 - 1), southStair, 2);
        setSafe(level,new BlockPos(x + 1, y + 4, z0 + 4), northStair, 2);

        // Key bricks (Hagrid's tap pattern hint): chiseled blocks at right side, 3 up + 2 across.
        for (int marker = 0; marker < 3; marker++) {
            setSafe(level,new BlockPos(x + 1, y + 1 + marker, z0 + 5), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        for (int marker = 0; marker < 2; marker++) {
            setSafe(level,new BlockPos(x + 1, y + 3, z0 + 5 + marker), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }

        // Hanging lanterns inside the passage.
        setSafe(level,new BlockPos(x, y + 4, z0 + 1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        setSafe(level,new BlockPos(x + 1, y + 4, z0 + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Bin (cauldron) reference + stacked barrels.
        setSafe(level,new BlockPos(x - 1, y + 1, z0 + 6), Blocks.CAULDRON.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x - 1, y + 1, z0 - 3), Blocks.BARREL.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x - 1, y + 2, z0 - 3), Blocks.BARREL.defaultBlockState(), 2);
    }

    // =================================================================================
    // Buildings — Ollivanders (wandmaker)
    // =================================================================================

    private void buildOllivanders(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        // Ollivanders: tall, dark and ancient — black plaster, aged copper belts, slate roof.
        int sx = 9, sz = 9;
        int floors = 3;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        TudorShopEngine.Style style = new TudorShopEngine.Style(
                Blocks.BLACK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
                Blocks.STRIPPED_SPRUCE_LOG, Blocks.WAXED_EXPOSED_CUT_COPPER,
                Blocks.DEEPSLATE_TILE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
                Blocks.BROWN_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.SPRUCE_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, style);

        // Interior: dense bookshelves stacked tall (wand boxes), lecterns, wand workbench.
        for (int dz = 2; dz < sz - 1; dz++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dz == sz / 2) continue;
                setSafe(level,new BlockPos(x0 + 1, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
                setSafe(level,new BlockPos(x0 + sx - 2, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
            }
        }
        // Lecterns in center.
        setSafe(level,new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + 2), Blocks.LECTERN.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + 2), Blocks.LECTERN.defaultBlockState(), 2);
        // Wand Workbench — backs Wandmaker villager into the WANDMAKER profession via POI.
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 2), ExampleMod.WAND_WORKBENCH.get().defaultBlockState(), 2);
        // Counter slabs flanking the workbench.
        setSafe(level,new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + sz - 2), Blocks.SPRUCE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        setSafe(level,new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + sz - 2), Blocks.SPRUCE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);

        // Carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.BROWN_CARPET.defaultBlockState(), 2);
        }
        // Hanging lantern.
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
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
                setSafe(level,new BlockPos(x0 + dx, y0 + step, z0 - step - 1), s, 2);
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
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz), block, 2);
                }
            }
        }

        // Grand colonnade across the south face — 6 columns at y0+1..y0+hLow.
        for (int col : new int[]{1, 3, 5, 8, 10, 12}) {
            for (int dy = 1; dy <= hLow; dy++) {
                setSafe(level,new BlockPos(x0 + col, y0 + dy, z0), pillar, 2);
            }
            // Column base + cap.
            setSafe(level,new BlockPos(x0 + col, y0 + 1, z0), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + col, y0 + hLow, z0), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
        }
        // Gap between columns is open (passage into porch).
        for (int dx = 0; dx < sx; dx++) {
            boolean isPillar = (dx == 1 || dx == 3 || dx == 5 || dx == 8 || dx == 10 || dx == 12);
            if (!isPillar) {
                for (int dy = 1; dy <= hLow - 1; dy++) {
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // Pediment above the colonnade — chiseled quartz frieze + gold trim.
        for (int dx = 0; dx < sx; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + hLow + 1, z0), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + hLow + 2, z0), accent, 2);
        }

        // Second story — narrower than first, set in 1 block.
        for (int dy = hLow + 2; dy <= hHigh; dy++) {
            for (int dx = 1; dx < sx - 1; dx++) {
                for (int dz = 1; dz < sz - 1; dz++) {
                    boolean edge = dx == 1 || dx == sx - 2 || dz == 1 || dz == sz - 2;
                    if (edge) {
                        // Windows every other block on second story.
                        boolean window = (dx % 2 == 0) && dy == hLow + 4;
                        setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                window ? Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState() : marble, 2);
                    }
                }
            }
        }
        // Inner roof at hHigh+1 + golden pyramid cap.
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dz = 1; dz < sz - 1; dz++) {
                setSafe(level,new BlockPos(x0 + dx, y0 + hHigh + 1, z0 + dz), roof, 2);
            }
        }
        // Stepped pyramid roof — three tiers shrinking inward, gold-block crown.
        for (int tier = 0; tier < 3; tier++) {
            int inset = tier + 2;
            for (int dx = inset; dx < sx - inset; dx++) {
                for (int dz = inset; dz < sz - inset; dz++) {
                    setSafe(level,new BlockPos(x0 + dx, y0 + hHigh + 2 + tier, z0 + dz),
                            tier == 2 ? accent : marble, 2);
                }
            }
        }
        // Golden spire on top.
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + hHigh + 5, z0 + sz / 2), accent, 2);
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + hHigh + 6, z0 + sz / 2),
                Blocks.LIGHTNING_ROD.defaultBlockState(), 2);

        // INTERIOR: marble floor, gold-block vault wall at back, iron-bar teller counter.
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dz = 1; dz < sz - 1; dz++) {
                setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + dz), floor, 2);
            }
        }
        // Vault gold blocks across back wall.
        for (int dx = 2; dx < sx - 2; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), accent, 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), accent, 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 3, z0 + sz - 2), accent, 2);
        }
        // Iron bars in front of vault as a teller barrier.
        for (int dx = 1; dx < sx - 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + sz - 4), Blocks.IRON_BARS.defaultBlockState(), 2);
            }
        }
        // Marble counter in front of bars with teller windows.
        for (int dx = 2; dx < sx - 2; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 5), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        // Long red carpet from entrance to vault.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.RED_CARPET.defaultBlockState(), 2);
        }

        // Chandeliers — hanging lanterns from the ceiling.
        for (int dx : new int[]{3, sx / 2, sx - 4}) {
            for (int dz : new int[]{3, sz / 2, sz - 4}) {
                setSafe(level,new BlockPos(x0 + dx, y0 + hLow - 1, z0 + dz),
                        Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
            }
        }

        // Teller behind the counter, plus guards posted around the vault.
        spawnGoblinTeller(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 6), "Gringotts Teller");
        spawnGoblinGuard(level, new BlockPos(x0 + 2, y0 + 1, z0 + sz - 7), "Vault Guard");
        spawnGoblinGuard(level, new BlockPos(x0 + sx - 3, y0 + 1, z0 + sz - 7), "Vault Guard");
        spawnGoblinGuard(level, new BlockPos(x0 + sx / 2 + 3, y0 + 1, z0 + 22), "Vault Guard");
    }

    // =================================================================================
    // Buildings — Eeylops Owl Emporium
    // =================================================================================

    private void buildEeylops(WorldGenLevel level, BlockPos corner, Direction front) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        // Eeylops Owl Emporium: quiet cream-and-timber shop under a weathered spruce roof.
        int sx = 11, sz = 8;
        int floors = 2;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        TudorShopEngine.Style eeylopsStyle = new TudorShopEngine.Style(
                Blocks.WHITE_TERRACOTTA, Blocks.MUD_BRICKS,
                Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_SLAB,
                Blocks.GLASS_PANE, Blocks.BIRCH_DOOR, Blocks.DARK_OAK_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, eeylopsStyle);

        // INTERIOR: rows of owl perches (oak fences) on hay bale stands, scatter of feathers.
        for (int dx = 2; dx < sx - 2; dx += 2) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.HAY_BLOCK.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 3, z0 + sz - 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
            // Spawn an owl on top of perch.
        }
        // Cages stacked (display).
        setSafe(level,new BlockPos(x0 + 1, y0 + 1, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 1, y0 + 2, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx - 2, y0 + 1, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx - 2, y0 + 2, z0 + 1), Blocks.IRON_BARS.defaultBlockState(), 2);
        // Carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.WHITE_CARPET.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
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
        // Flourish & Blotts: scholarly teal bookshop under a dark prismarine roof.
        int sx = 12, sz = 9;
        int floors = 3;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        TudorShopEngine.Style flourishStyle = new TudorShopEngine.Style(
                Blocks.CYAN_TERRACOTTA, Blocks.WHITE_TERRACOTTA,
                Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.DARK_PRISMARINE_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.DARK_PRISMARINE_SLAB,
                Blocks.GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, flourishStyle);

        // INTERIOR: walls of bookshelves stacked floor-to-ceiling, lecterns at counters, hanging lanterns.
        for (int dz = 2; dz < sz - 1; dz++) {
            for (int dy = 1; dy <= 5; dy++) {
                if (dz == sz / 2) continue;
                setSafe(level,new BlockPos(x0 + 1, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
                setSafe(level,new BlockPos(x0 + sx - 2, y0 + dy, z0 + dz), Blocks.BOOKSHELF.defaultBlockState(), 2);
            }
        }
        // Centre book table (planks + lecterns).
        for (int dx = 4; dx < sx - 4; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 3), Blocks.DARK_OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2 - 1, y0 + 2, z0 + sz - 3), Blocks.LECTERN.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx / 2 + 1, y0 + 2, z0 + sz - 3), Blocks.LECTERN.defaultBlockState(), 2);
        // Green carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.LIME_CARPET.defaultBlockState(), 2);
        }
        // Hanging lanterns.
        setSafe(level,new BlockPos(x0 + 3, y0 + h - 1, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        setSafe(level,new BlockPos(x0 + sx - 4, y0 + h - 1, z0 + sz / 2),
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
        // Quality Quidditch Supplies: mud-brick and timber with scarlet windows.
        int sx = 10, sz = 9;
        int floors = 2;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        TudorShopEngine.Style quidditchStyle = new TudorShopEngine.Style(
                Blocks.MUD_BRICKS, Blocks.WHITE_TERRACOTTA,
                Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.DARK_OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_SLAB,
                Blocks.RED_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.SPRUCE_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, quidditchStyle);

        // INTERIOR: broom rack display, counter with golden snitch (gold block in the air).
        for (int dx = 2; dx < sx - 2; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
        }
        // Snitch in a glass cage.
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 2, z0 + 2), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 2, z0 + 2).relative(d), Blocks.GLASS_PANE.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 3, z0 + 2), Blocks.GLASS_PANE.defaultBlockState(), 2);

        // Counter.
        for (int dx = 1; dx < sx - 1; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 4), Blocks.DARK_OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        // Red carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.RED_CARPET.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
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
        // Madam Malkin's: plum-and-magenta boutique under a prismarine roof.
        int sx = 9, sz = 9;
        int floors = 3;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.OAK_PLANKS.defaultBlockState());
        TudorShopEngine.Style malkinsStyle = new TudorShopEngine.Style(
                Blocks.PURPLE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA,
                Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_OAK_LOG,
                Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PURPUR_STAIRS, Blocks.PRISMARINE_BRICK_SLAB,
                Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, malkinsStyle);

        // INTERIOR: armor stands (wool stacks) + loom + curtained dressing room.
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx % 2 == 0) {
                setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.PINK_WOOL.defaultBlockState(), 2);
                setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + 1), Blocks.MAGENTA_WOOL.defaultBlockState(), 2);
            }
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 2), Blocks.LOOM.defaultBlockState(), 2);
        // Pink carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.PINK_CARPET.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 3, z0 + sz / 2),
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
        // Slug & Jiggers Apothecary: herb-green plaster under a mossy stone roof.
        int sx = 9, sz = 9;
        int floors = 2;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        TudorShopEngine.Style apothecaryStyle = new TudorShopEngine.Style(
                Blocks.GREEN_TERRACOTTA, Blocks.MUD_BRICKS,
                Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG,
                Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB,
                Blocks.GREEN_STAINED_GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, apothecaryStyle);

        // INTERIOR: brewing stand wall + cauldrons + flower pots of ingredients.
        for (int dx = 2; dx < sx - 2; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + 2, y0 + 1, z0 + 2), Blocks.CAULDRON.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx - 3, y0 + 1, z0 + 2), Blocks.CAULDRON.defaultBlockState(), 2);
        for (int dx : new int[]{2, 4, sx - 3}) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + 1),
                    (dx % 2 == 0 ? Blocks.POTTED_FERN : Blocks.POTTED_AZURE_BLUET).defaultBlockState(), 2);
        }
        // Green carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.GREEN_CARPET.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
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
        // Weasleys' Wizard Wheezes: the loudest building on the street — four stories of
        // orange and yellow with the giant top hat back on the ridge.
        int sx = 10, sz = 9;
        int floors = 4;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.RED_NETHER_BRICKS.defaultBlockState());
        TudorShopEngine.Style wheezesStyle = new TudorShopEngine.Style(
                Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG,
                Blocks.ACACIA_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.ACACIA_SLAB,
                Blocks.ORANGE_STAINED_GLASS_PANE, Blocks.ACACIA_DOOR, Blocks.ACACIA_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, wheezesStyle);

        // Giant top hat perched on the ridge (the iconic Weasley sign).
        int hatY = y0 + h + 6;
        int hatZ = z0 + sz / 2;
        for (int dy = 0; dy < 3; dy++) {
            setSafe(level,new BlockPos(x0 + sx / 2, hatY + dy, hatZ), Blocks.ORANGE_WOOL.defaultBlockState(), 2);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            setSafe(level,new BlockPos(x0 + sx / 2, hatY, hatZ).relative(d), Blocks.ORANGE_WOOL.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, hatY + 3, hatZ), Blocks.YELLOW_WOOL.defaultBlockState(), 2);

        // INTERIOR: chaos shelves — wool blocks, TNT (defused), jukebox, redstone lamps.
        for (int dx = 1; dx < sx - 1; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 1),
                    ((dx + 1) % 3 == 0 ? Blocks.TNT : Blocks.YELLOW_WOOL).defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + 1),
                    ((dx % 2 == 0) ? Blocks.PINK_WOOL : Blocks.LIME_WOOL).defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz / 2), Blocks.JUKEBOX.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + sz / 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx / 2 + 1, y0 + 1, z0 + sz / 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
        // Carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.ORANGE_CARPET.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
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
        // Florean Fortescue's: pastel parlour — white plaster, birch timber, cherry roof.
        int sx = 10, sz = 8;
        int floors = 2;
        int h = TudorShopEngine.wallHeight(floors);
        fillRect(level, x0, y0, z0, sx, 1, sz, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        TudorShopEngine.Style fortescuesStyle = new TudorShopEngine.Style(
                Blocks.WHITE_TERRACOTTA, Blocks.CALCITE,
                Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG,
                Blocks.CHERRY_STAIRS, Blocks.BIRCH_STAIRS, Blocks.CHERRY_SLAB,
                Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.BIRCH_DOOR, Blocks.BIRCH_TRAPDOOR);
        TudorShopEngine.buildShell(level, x0, y0, z0, sx, sz, front, floors, fortescuesStyle);

        // OUTDOOR SEATING — tables (oak slabs on fence posts) with stools in front of the shop.
        int seatingZ = (front == Direction.NORTH) ? z0 - 4 : z0 + sz + 3;
        for (int dx = 0; dx <= 6; dx += 3) {
            placeCafeTable(level, new BlockPos(x0 + 1 + dx, y0 + 1, seatingZ));
        }

        // INTERIOR: counter with cake (ice cream stand-in) + packed-ice freezer + blue ice display.
        for (int dx = 1; dx < sx - 1; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 2), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2 - 1, y0 + 2, z0 + 2), Blocks.CAKE.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx / 2 + 1, y0 + 2, z0 + 2), Blocks.CAKE.defaultBlockState(), 2);
        for (int dx = 1; dx < sx - 1; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.PACKED_ICE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2),
                    (dx % 2 == 0 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 2);
        }
        // Light-blue carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.LIGHT_BLUE_CARPET.defaultBlockState(), 2);
        }
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + h, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                VillagerProfession.BUTCHER, "Florean Fortescue");
    }

    private void placeCafeTable(WorldGenLevel level, BlockPos at) {
        // Fence post + slab top = café table.
        setSafe(level,at, Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        setSafe(level,at.above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
        // Stools on either side.
        setSafe(level,at.east(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        setSafe(level,at.east().above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
        setSafe(level,at.west(), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        setSafe(level,at.west().above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState(), 2);
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
                    setSafe(level,p, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
                } else if (r == radius) {
                    setSafe(level,p, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
                } else {
                    setSafe(level,p, Blocks.AIR.defaultBlockState(), 2);
                    setSafe(level,p.below(), Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
        // Tiered pillar with sea-lantern crown.
        BlockPos pillar = new BlockPos(x0, y, z0);
        for (int dy = 0; dy < 3; dy++) {
            setSafe(level,pillar.above(dy), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            setSafe(level,pillar.above(2).relative(d), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, d)
                    .setValue(BlockStateProperties.HALF, Half.TOP), 2);
        }
        setSafe(level,pillar.above(3), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        setSafe(level,pillar.above(4), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        setSafe(level,pillar.above(5), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
        // Water sources above to pour from the column (decorative).
        setSafe(level,pillar.above(2).north().above(), Blocks.WATER.defaultBlockState(), 2);
        setSafe(level,pillar.above(2).south().above(), Blocks.WATER.defaultBlockState(), 2);
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
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);
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
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz), b, 2);
                }
            }
        }
        // Crenellated top.
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                if (edge && (dx + dz) % 2 == 0) {
                    setSafe(level,new BlockPos(x0 + dx, y0 + height, z0 + dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Bright crown — visible from far away.
        setSafe(level,new BlockPos(x0 + 2, y0 + height, z0 + 2), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 2, y0 + height + 1, z0 + 2), Blocks.GLOWSTONE.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 2, y0 + height + 2, z0 + 2), Blocks.JACK_O_LANTERN.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 2, y0 + height + 3, z0 + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        setSafe(level,new BlockPos(x0 + 2, y0 + height + 4, z0 + 2), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
    }

    private void buildLanternPole(WorldGenLevel level, BlockPos base) {
        for (int dy = 0; dy < 3; dy++) {
            setSafe(level,base.above(dy), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        }
        setSafe(level,base.above(3), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        setSafe(level,base.above(2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private void placeBench(WorldGenLevel level, BlockPos pos, Direction facing) {
        BlockState seat = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        setSafe(level,pos, seat, 2);
    }

    private void placeFlowerPlanter(WorldGenLevel level, BlockPos pos) {
        setSafe(level,pos, Blocks.HAY_BLOCK.defaultBlockState(), 2);
        BlockState plant = switch ((pos.getX() + pos.getZ()) & 3) {
            case 0 -> Blocks.POTTED_POPPY.defaultBlockState();
            case 1 -> Blocks.POTTED_ALLIUM.defaultBlockState();
            case 2 -> Blocks.POTTED_DANDELION.defaultBlockState();
            default -> Blocks.POTTED_AZURE_BLUET.defaultBlockState();
        };
        setSafe(level,pos.above(), plant, 2);
    }

    // =================================================================================
    // Generic helpers
    // =================================================================================

    private void fillRect(WorldGenLevel level, int x0, int y0, int z0, int sx, int sy, int sz, BlockState block) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dy = 0; dy < sy; dy++) {
                for (int dz = 0; dz < sz; dz++) {
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz), block, 2);
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
        setSafe(level,new BlockPos(doorX, doorY, doorZ), lower, 2);
        setSafe(level,new BlockPos(doorX, doorY + 1, doorZ), upper, 2);
    }

    private void placeAwning(WorldGenLevel level, int doorX, int awningY, int doorZ, Direction front, net.minecraft.world.level.block.Block stairBlock) {
        Direction awningFacing = front.getOpposite();
        BlockState s = stairBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, awningFacing)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        // Step outward by 1 from the door.
        int outZ = doorZ + (front == Direction.NORTH ? -1 : 1);
        for (int dx = -2; dx <= 2; dx++) {
            setSafe(level,new BlockPos(doorX + dx, awningY, outZ), s, 2);
        }
        BlockState leftCap = stairBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        BlockState rightCap = stairBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.HALF, Half.TOP);
        setSafe(level,new BlockPos(doorX - 3, awningY, outZ), leftCap, 2);
        setSafe(level,new BlockPos(doorX + 3, awningY, outZ), rightCap, 2);
        // Lantern hanging from the awning.
        setSafe(level,new BlockPos(doorX - 2, awningY - 1, outZ),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        setSafe(level,new BlockPos(doorX + 2, awningY - 1, outZ),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    // =================================================================================
    // Knockturn Alley — darker side fork (Borgin & Burkes)
    // =================================================================================

    private void buildKnockturnAlley(WorldGenLevel level, BlockPos start, RandomSource rng) {
        int x0 = start.getX();
        int y = start.getY();
        int z0 = start.getZ();
        // A 4-wide deepslate-brick alley running 18 blocks south.
        for (int dz = 0; dz < 18; dz++) {
            for (int dx = 0; dx < 4; dx++) {
                BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
                BlockState paving = ((dx + dz) % 5 == 0) ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                        : ((dx + dz) % 3 == 0) ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                setSafe(level,p, paving, 2);
                // Clear air above.
                for (int dy = 1; dy < 18; dy++) {
                    BlockPos pp = new BlockPos(x0 + dx, y + dy, z0 + dz);
                    if (!level.isEmptyBlock(pp)) setSafe(level,pp, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        // Soul-lantern poles every 5 blocks for the eerie blue glow.
        for (int dz = 3; dz < 18; dz += 5) {
            for (int dy = 0; dy < 3; dy++) {
                setSafe(level,new BlockPos(x0 - 1, y + 1 + dy, z0 + dz), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 2);
                setSafe(level,new BlockPos(x0 + 4, y + 1 + dy, z0 + dz), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(), 2);
            }
            setSafe(level,new BlockPos(x0 - 1, y + 4, z0 + dz),
                    Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
            setSafe(level,new BlockPos(x0 + 4, y + 4, z0 + dz),
                    Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        }
        // West building: Borgin & Burkes — dark wizards' antique shop.
        buildBorginAndBurkes(level, new BlockPos(x0 - 9, y, z0 + 4), rng);
        // East building: Cursed Curio Shop.
        buildCursedCurioShop(level, new BlockPos(x0 + 5, y, z0 + 4), rng);
        // Dead-end at the far south: a blackstone arch with cobwebs.
        for (int dy = 0; dy < 5; dy++) {
            setSafe(level,new BlockPos(x0 - 1, y + dy, z0 + 17), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + 4, y + dy, z0 + 17), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
        }
        for (int dx = 0; dx < 4; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y + 4, z0 + 17), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y + 5, z0 + 17), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
        }
        for (int dx = 0; dx < 4; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y + 1, z0 + 16), Blocks.COBWEB.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + dx, y + 2, z0 + 16), Blocks.COBWEB.defaultBlockState(), 2);
        }
        // Add a couple of cobwebs along the walls for atmosphere.
        setSafe(level,new BlockPos(x0, y + 3, z0 + 8), Blocks.COBWEB.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + 3, y + 3, z0 + 12), Blocks.COBWEB.defaultBlockState(), 2);
    }

    private void buildBorginAndBurkes(WorldGenLevel level, BlockPos corner, RandomSource rng) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 9, sz = 9, h = 6;
        BlockState wall = Blocks.BLACKSTONE.defaultBlockState();
        BlockState accent = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState window = Blocks.PURPLE_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean isCorner = (dx == 0 || dx == sx - 1) && (dz == 0 || dz == sz - 1);
                    boolean band = dy == 4;
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                            isCorner || band ? accent : wall, 2);
                }
            }
        }
        // Front door faces east (toward the Knockturn alley path).
        int frontX = x0 + sx - 1;
        for (int dz = 2; dz < sz - 2; dz++) {
            if (dz == sz / 2) continue;
            setSafe(level,new BlockPos(frontX, y0 + 2, z0 + dz), window, 2);
        }
        BlockState doorLower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = doorLower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        setSafe(level,new BlockPos(frontX, y0 + 1, z0 + sz / 2), doorLower, 2);
        setSafe(level,new BlockPos(frontX, y0 + 2, z0 + sz / 2), doorUpper, 2);
        // Sinister hanging soul lantern over the door.
        setSafe(level,new BlockPos(frontX, y0 + 4, z0 + sz / 2),
                Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Slanted blackstone roof.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                setSafe(level,new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), accent, 2);
            }
        }
        // Spires at corners.
        for (int dy = 0; dy < 3; dy++) {
            setSafe(level,new BlockPos(x0, y0 + h + 2 + dy, z0), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + sx - 1, y0 + h + 2 + dy, z0), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0, y0 + h + 2 + dy, z0 + sz - 1), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
            setSafe(level,new BlockPos(x0 + sx - 1, y0 + h + 2 + dy, z0 + sz - 1), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
        }

        // INTERIOR: skull displays, withered shelves, cursed object on pedestal, cobwebs.
        for (int dx = 1; dx < sx - 1; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.SOUL_SAND.defaultBlockState(), 2);
            if (dx % 2 == 0)
                setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + 1), Blocks.SKELETON_SKULL.defaultBlockState(), 2);
        }
        // Pedestal with cursed item.
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz / 2), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx / 2, y0 + 2, z0 + sz / 2), Blocks.WITHER_SKELETON_SKULL.defaultBlockState(), 2);
        // Cobwebs in corners.
        setSafe(level,new BlockPos(x0 + 1, y0 + h - 1, z0 + 1), Blocks.COBWEB.defaultBlockState(), 2);
        setSafe(level,new BlockPos(x0 + sx - 2, y0 + h - 1, z0 + sz - 2), Blocks.COBWEB.defaultBlockState(), 2);
        // Purple carpet runner.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.PURPLE_CARPET.defaultBlockState(), 2);
        }
        // Loot chest.
        placeLootChest(level, new BlockPos(x0 + 2, y0 + 1, z0 + sz - 2), Direction.WEST, LootTheme.BORGIN_DARK, rng);

        spawnNamedVillager(level, new BlockPos(x0 + sx / 2 - 1, y0 + 1, z0 + sz - 2),
                VillagerProfession.WEAPONSMITH, "Mr. Borgin");
    }

    private void buildCursedCurioShop(WorldGenLevel level, BlockPos corner, RandomSource rng) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 8, sz = 9, h = 5;
        BlockState wall = Blocks.BASALT.defaultBlockState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState window = Blocks.RED_STAINED_GLASS_PANE.defaultBlockState();

        fillRect(level, x0, y0, z0, sx, 1, sz, floor);
        for (int dy = 1; dy <= h; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    setSafe(level,new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                            dy == h ? Blocks.POLISHED_BASALT.defaultBlockState() : wall, 2);
                }
            }
        }
        // Door faces west (toward the alley path).
        BlockState doorLower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = doorLower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        setSafe(level,new BlockPos(x0, y0 + 1, z0 + sz / 2), doorLower, 2);
        setSafe(level,new BlockPos(x0, y0 + 2, z0 + sz / 2), doorUpper, 2);
        for (int dz = 2; dz < sz - 2; dz++) {
            if (dz == sz / 2) continue;
            setSafe(level,new BlockPos(x0, y0 + 2, z0 + dz), window, 2);
        }
        setSafe(level,new BlockPos(x0, y0 + 4, z0 + sz / 2),
                Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        // Roof.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                setSafe(level,new BlockPos(x0 + dx, y0 + h + 1, z0 + dz), Blocks.POLISHED_BASALT.defaultBlockState(), 2);
            }
        }
        // Interior: glass display cases (panes around air), random "cursed" blocks.
        for (int dx = 2; dx < sx - 2; dx++) {
            setSafe(level,new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), 2);
            if (dx % 2 == 0)
                setSafe(level,new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.BONE_BLOCK.defaultBlockState(), 2);
        }
        // Cobweb in the doorway corner.
        setSafe(level,new BlockPos(x0 + sx - 2, y0 + h - 1, z0 + 1), Blocks.COBWEB.defaultBlockState(), 2);
        // Red carpet.
        for (int dz = 1; dz < sz - 1; dz++) {
            setSafe(level,new BlockPos(x0 + sx / 2, y0 + 1, z0 + dz), Blocks.RED_CARPET.defaultBlockState(), 2);
        }
        placeLootChest(level, new BlockPos(x0 + sx - 2, y0 + 1, z0 + sz - 2), Direction.WEST, LootTheme.BORGIN_DARK, rng);
        spawnNamedVillager(level, new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz - 3),
                VillagerProfession.MASON, "Caretaker");
    }

    // =================================================================================
    // Mod-block flourishes (Floo Fireplace, Owl Perch, etc.)
    // =================================================================================

    private void decorateWithModBlocks(WorldGenLevel level, int x0, int y, int z0) {
        // Floo Fireplace in the Leaky Cauldron, replacing the indoor campfire location.
        BlockState floo = optionalBlock("FLOO_FIREPLACE");
        if (floo != null) {
            setSafe(level,new BlockPos(x0 + 1, y + 1, z0 + 16), floo, 2);
        }
        // Owl Perches in Eeylops (in front of each hay-fence stand we placed).
        BlockState owlPerch = optionalBlock("OWL_PERCH");
        if (owlPerch != null) {
            for (int dx = 18; dx <= 24; dx += 2) {
                setSafe(level,new BlockPos(x0 + dx, y + 2, z0 + 18), owlPerch, 2);
            }
        }
        // Wizard Cauldrons in the Apothecary.
        BlockState wizardCauldron = optionalBlock("WIZARD_CAULDRON");
        if (wizardCauldron != null) {
            setSafe(level,new BlockPos(x0 + 41, y + 1, z0 + 32), wizardCauldron, 2);
            setSafe(level,new BlockPos(x0 + 46, y + 1, z0 + 32), wizardCauldron, 2);
        }
        // Enchanted Book Piles in Flourish & Blotts (on the centre book table).
        BlockState bookPile = optionalBlock("ENCHANTED_BOOK_PILE");
        if (bookPile != null) {
            setSafe(level,new BlockPos(x0 + 33, y + 2, z0 + 18), bookPile, 2);
            setSafe(level,new BlockPos(x0 + 35, y + 2, z0 + 18), bookPile, 2);
        }
        // Candle Clusters dotted in interiors for cozy lighting.
        BlockState candles = optionalBlock("CANDLE_CLUSTER");
        if (candles != null) {
            setSafe(level,new BlockPos(x0 + 19, y + 1, z0 + 14), candles, 2);
            setSafe(level,new BlockPos(x0 + 33, y + 1, z0 + 14), candles, 2);
            setSafe(level,new BlockPos(x0 + 48, y + 1, z0 + 13), candles, 2);
        }
        // Moving Portraits as wall art in shops with internal back-wall space.
        BlockState portrait = optionalBlock("MOVING_PORTRAIT");
        if (portrait != null) {
            setSafe(level,new BlockPos(x0 + 33, y + 3, z0 + 12), portrait, 2);
            setSafe(level,new BlockPos(x0 + 49, y + 3, z0 + 10), portrait, 2);
        }
        // Hogwarts house banners along the Gringotts colonnade interior.
        BlockState gryf = optionalBlock("GRYFFINDOR_BANNER_BLOCK");
        BlockState raven = optionalBlock("RAVENCLAW_BANNER_BLOCK");
        BlockState huff = optionalBlock("HUFFLEPUFF_BANNER_BLOCK");
        BlockState slyth = optionalBlock("SLYTHERIN_BANNER_BLOCK");
        if (gryf != null)  setSafe(level,new BlockPos(x0 + 62, y + 4, z0 + 11), gryf, 2);
        if (raven != null) setSafe(level,new BlockPos(x0 + 66, y + 4, z0 + 11), raven, 2);
        if (huff != null)  setSafe(level,new BlockPos(x0 + 70, y + 4, z0 + 11), huff, 2);
        if (slyth != null) setSafe(level,new BlockPos(x0 + 72, y + 4, z0 + 11), slyth, 2);
    }

    private BlockState optionalBlock(String fieldName) {
        try {
            java.lang.reflect.Field f = ExampleMod.class.getField(fieldName);
            Object regObject = f.get(null);
            if (regObject instanceof net.minecraftforge.registries.RegistryObject<?> ro) {
                Object value = ro.get();
                if (value instanceof net.minecraft.world.level.block.Block block) {
                    return block.defaultBlockState();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // =================================================================================
    // Custom loot chests
    // =================================================================================

    /** Themed loot for each shop. Items chosen from the mod's own item registry where possible. */
    private enum LootTheme {
        OLLIVANDERS_WANDS,
        FLOURISH_BOOKS,
        QUIDDITCH_BROOMS,
        APOTHECARY_INGREDIENTS,
        MALKINS_CLOTHES,
        FORTESCUES_SWEETS,
        WHEEZES_PRANKS,
        EEYLOPS_FEATHERS,
        GRINGOTTS_VAULT,
        LEAKY_CAULDRON_FOOD,
        BORGIN_DARK
    }

    private void placeLootChest(WorldGenLevel level, BlockPos pos, Direction facing, LootTheme theme, RandomSource rng) {
        BlockState chestState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, facing);
        setSafe(level,pos, chestState, 2);
        // ChestBlockEntity.setItem inherits from BaseContainerBlockEntity.setItem which only modifies
        // the items list + calls setChanged() (which is null-safe when the BE's level is null during
        // worldgen). Safe to populate directly.
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        List<ItemStack> rolled = rollLoot(theme, rng);
        int slot = rng.nextInt(3);
        for (ItemStack stack : rolled) {
            if (slot >= 27) break;
            chest.setItem(slot, stack);
            slot += 1 + rng.nextInt(3); // scatter across slots so it looks lived-in
        }
    }

    private List<ItemStack> rollLoot(LootTheme theme, RandomSource rng) {
        List<ItemStack> out = new ArrayList<>();
        switch (theme) {
            case OLLIVANDERS_WANDS -> {
                // Spare wand cores, fine wood sticks, and a chance at a real wand.
                addStack(out, optionalItem("FINE_WOOD_STICK"), 3 + rng.nextInt(5));
                addStack(out, optionalItem("ARCANE_DUST"), 2 + rng.nextInt(4));
                addStack(out, optionalItem("ARCANE_CORE"), 1 + rng.nextInt(2));
                if (rng.nextFloat() < 0.30F) addStack(out, optionalItem("PHOENIX_CORE_WAND"), 1);
                if (rng.nextFloat() < 0.15F) addStack(out, optionalItem("UNICORN_CORE_WAND"), 1);
                if (rng.nextFloat() < 0.08F) addStack(out, optionalItem("DRAGON_CORE_WAND"), 1);
                // A starter spell book.
                addBook(out, SpellType.LUMOS);
                if (rng.nextFloat() < 0.5F) addBook(out, SpellType.ALOHOMORA);
            }
            case FLOURISH_BOOKS -> {
                // Magic books — Flourish & Blotts is THE bookshop. Lots of spell books.
                SpellType[] commons = {SpellType.LUMOS, SpellType.ALOHOMORA, SpellType.REVELIO,
                        SpellType.INCENDIO, SpellType.ARRESTO_MOMENTUM, SpellType.WINGARDIUM_LEVIOSA};
                SpellType[] advanced = {SpellType.PROTEGO, SpellType.STUPEFY, SpellType.EXPELLIARMUS,
                        SpellType.REPARO};
                for (int i = 0; i < 3 + rng.nextInt(4); i++) {
                    addBook(out, commons[rng.nextInt(commons.length)]);
                }
                for (int i = 0; i < 1 + rng.nextInt(2); i++) {
                    addBook(out, advanced[rng.nextInt(advanced.length)]);
                }
                addStack(out, Items.BOOK, 4 + rng.nextInt(6));
                addStack(out, Items.WRITABLE_BOOK, 1 + rng.nextInt(2));
                addStack(out, Items.PAPER, 6 + rng.nextInt(8));
            }
            case QUIDDITCH_BROOMS -> {
                addStack(out, optionalItem("BROOM"), 1);
                if (rng.nextFloat() < 0.35F) addStack(out, optionalItem("NIMBUS_2000"), 1);
                if (rng.nextFloat() < 0.10F) addStack(out, optionalItem("FIREBOLT"), 1);
                addStack(out, Items.FEATHER, 4 + rng.nextInt(6));
                addStack(out, Items.STICK, 6 + rng.nextInt(6));
                addStack(out, Items.LEATHER, 2 + rng.nextInt(3));
                addStack(out, Items.GOLD_NUGGET, 3 + rng.nextInt(4));
            }
            case APOTHECARY_INGREDIENTS -> {
                addStack(out, optionalItem("GILLYWEED"), 1 + rng.nextInt(3));
                addStack(out, optionalItem("MANDRAKE_ROOT"), 1 + rng.nextInt(2));
                addStack(out, optionalItem("ACROMANTULA_VENOM"), rng.nextInt(2));
                addStack(out, optionalItem("ARCANE_DUST"), 2 + rng.nextInt(4));
                addStack(out, optionalItem("WOLF_FANG"), rng.nextInt(2));
                addStack(out, Items.GLASS_BOTTLE, 4 + rng.nextInt(4));
                addStack(out, Items.SPIDER_EYE, 2 + rng.nextInt(3));
                addStack(out, Items.BLAZE_POWDER, 1 + rng.nextInt(3));
                addStack(out, Items.NETHER_WART, 2 + rng.nextInt(3));
            }
            case MALKINS_CLOTHES -> {
                // Robes: leather armor + dyed leather.
                addStack(out, Items.LEATHER_HELMET, 1);
                addStack(out, Items.LEATHER_CHESTPLATE, 1);
                addStack(out, Items.LEATHER_LEGGINGS, 1);
                addStack(out, Items.LEATHER_BOOTS, 1);
                addStack(out, Items.WHITE_WOOL, 4 + rng.nextInt(4));
                addStack(out, Items.STRING, 4 + rng.nextInt(8));
                addStack(out, Items.PINK_DYE, 2 + rng.nextInt(3));
                if (rng.nextFloat() < 0.25F) addStack(out, optionalItem("INVISIBILITY_CLOAK"), 1);
            }
            case FORTESCUES_SWEETS -> {
                addStack(out, Items.CAKE, 1);
                addStack(out, Items.PUMPKIN_PIE, 1 + rng.nextInt(2));
                addStack(out, Items.COOKIE, 4 + rng.nextInt(6));
                addStack(out, Items.SWEET_BERRIES, 4 + rng.nextInt(6));
                addStack(out, Items.HONEY_BOTTLE, 1 + rng.nextInt(2));
                addStack(out, Items.SUGAR, 4 + rng.nextInt(4));
                addStack(out, Items.MILK_BUCKET, 1);
            }
            case WHEEZES_PRANKS -> {
                addStack(out, Items.TNT, 2 + rng.nextInt(3));
                addStack(out, Items.GUNPOWDER, 4 + rng.nextInt(6));
                addStack(out, Items.REDSTONE, 8 + rng.nextInt(8));
                addStack(out, Items.SLIME_BALL, 2 + rng.nextInt(3));
                addStack(out, Items.FIREWORK_ROCKET, 4 + rng.nextInt(4));
                addStack(out, Items.MAGMA_CREAM, 1 + rng.nextInt(2));
                // Random music disc.
                if (rng.nextFloat() < 0.4F) addStack(out, Items.MUSIC_DISC_CAT, 1);
            }
            case EEYLOPS_FEATHERS -> {
                addStack(out, Items.FEATHER, 8 + rng.nextInt(8));
                addStack(out, Items.EGG, 4 + rng.nextInt(4));
                addStack(out, Items.LEATHER, 2 + rng.nextInt(3));
                addStack(out, Items.STRING, 4 + rng.nextInt(4));
                addStack(out, optionalItem("OWL_PERCH_ITEM"), 1 + rng.nextInt(2));
            }
            case GRINGOTTS_VAULT -> {
                addStack(out, Items.GOLD_INGOT, 8 + rng.nextInt(8));
                addStack(out, Items.GOLD_NUGGET, 16 + rng.nextInt(16));
                addStack(out, Items.EMERALD, 6 + rng.nextInt(8));
                addStack(out, Items.DIAMOND, 1 + rng.nextInt(3));
                if (rng.nextFloat() < 0.05F) addStack(out, optionalItem("ELDER_WAND"), 1);
                if (rng.nextFloat() < 0.15F) addStack(out, optionalItem("HORCRUX_ITEM"), 1);
            }
            case LEAKY_CAULDRON_FOOD -> {
                addStack(out, Items.BREAD, 4 + rng.nextInt(6));
                addStack(out, Items.COOKED_BEEF, 2 + rng.nextInt(4));
                addStack(out, Items.GOLDEN_CARROT, 1 + rng.nextInt(2));
                addStack(out, Items.MUSHROOM_STEW, 1 + rng.nextInt(3));
                addStack(out, Items.GLASS_BOTTLE, 4);
                addStack(out, Items.POTION, 1);
            }
            case BORGIN_DARK -> {
                // Cursed items: bones, rotten flesh, soul-flavored stuff, withered loot.
                addStack(out, Items.BONE, 4 + rng.nextInt(6));
                addStack(out, Items.ROTTEN_FLESH, 4 + rng.nextInt(6));
                addStack(out, Items.SKELETON_SKULL, rng.nextInt(2));
                addStack(out, Items.WITHER_SKELETON_SKULL, rng.nextFloat() < 0.20F ? 1 : 0);
                addStack(out, Items.SOUL_SAND, 2 + rng.nextInt(3));
                addStack(out, Items.OBSIDIAN, 1 + rng.nextInt(3));
                addStack(out, optionalItem("ACROMANTULA_VENOM"), 1 + rng.nextInt(2));
                if (rng.nextFloat() < 0.20F) addStack(out, optionalItem("HORCRUX_ITEM"), 1);
            }
        }
        return out;
    }

    /** Looks up a registered mod item by its ExampleMod field name; returns null if missing. */
    private Item optionalItem(String fieldName) {
        try {
            java.lang.reflect.Field f = ExampleMod.class.getField(fieldName);
            Object regObject = f.get(null);
            if (regObject instanceof net.minecraftforge.registries.RegistryObject<?> ro) {
                Object value = ro.get();
                if (value instanceof Item item) return item;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void addStack(List<ItemStack> out, Item item, int count) {
        if (item == null || count <= 0) return;
        out.add(new ItemStack(item, count));
    }

    private void addBook(List<ItemStack> out, SpellType spell) {
        try {
            out.add(MagicBookItem.createBook(spell));
        } catch (Throwable ignored) {}
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

    /** Places a Gringotts teller behind the counter. */
    private void spawnGoblinTeller(WorldGenLevel level, BlockPos pos, String name) {
        try {
            var goblin = com.hogwartsmagic.entity.ModEntities.GOBLIN.get().create(level.getLevel());
            if (goblin == null) return;
            goblin.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            goblin.makeTeller(name);
            level.addFreshEntity(goblin);
        } catch (Throwable ignored) {}
    }

    /** Stations an armed goblin guard. */
    private void spawnGoblinGuard(WorldGenLevel level, BlockPos pos, String name) {
        try {
            var guard = com.hogwartsmagic.entity.ModEntities.GOBLIN_GUARD.get().create(level.getLevel());
            if (guard == null) return;
            guard.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            guard.makeGuard(name);
            guard.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                    new net.minecraft.world.item.ItemStack(com.hogwartsmagic.ExampleMod.GOBLIN_SILVER_DAGGER.get()));
            guard.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.15F);
            level.addFreshEntity(guard);
        } catch (Throwable ignored) {}
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
