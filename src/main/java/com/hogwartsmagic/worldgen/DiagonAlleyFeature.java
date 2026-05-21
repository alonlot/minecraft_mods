package com.hogwartsmagic.worldgen;

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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * Diagon Alley — a small wizarding town built procedurally in plains-style biomes.
 *
 * Layout: a stone-brick avenue running east-west (≈42 long × 6 wide), flanked by themed
 * shop cabins on both sides. Centerpiece is an octagonal water fountain. The east end
 * caps with a tall lantern tower (≈18 blocks high) that is visible from far away. Every
 * shop has a custom villager shopkeeper.
 *
 * IMPORTANT: do not call SignBlockEntity#updateText from worldgen — the BE has a null
 * level pointer there and the markUpdated() call NPEs. We leave signs blank and rely on
 * the visible architecture / shopkeeper names instead.
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

        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        BlockPos base = new BlockPos(origin.getX(), surfaceY, origin.getZ());

        // Footprint: 50x32. Require reasonable flatness so we don't slice into mountains.
        if (!isReasonablyFlat(level, base, 50, 32, 3)) {
            return false;
        }

        int y = base.getY();
        int x0 = base.getX();
        int z0 = base.getZ();

        // Clear the airspace above the footprint so trees/grass don't poke through buildings.
        for (int dx = 0; dx < 50; dx++) {
            for (int dz = 0; dz < 32; dz++) {
                for (int dy = 0; dy < 20; dy++) {
                    BlockPos p = new BlockPos(x0 + dx, y + dy, z0 + dz);
                    if (!level.isEmptyBlock(p)) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                // Foundation: solid cobble two blocks deep so the road and buildings sit on stable ground.
                level.setBlock(new BlockPos(x0 + dx, y - 1, z0 + dz), Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y - 2, z0 + dz), Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        }

        // Central avenue: stone-brick paving, 42 long × 6 wide, centered Z.
        int avenueX0 = x0 + 4;
        int avenueZ0 = z0 + 13;
        for (int dx = 0; dx < 42; dx++) {
            for (int dz = 0; dz < 6; dz++) {
                BlockPos p = new BlockPos(avenueX0 + dx, y, avenueZ0 + dz);
                // Mossy variation every few blocks for character.
                BlockState paving = ((dx + dz) % 5 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : ((dx * 7 + dz * 3) % 11 == 0) ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                        : Blocks.STONE_BRICKS.defaultBlockState();
                level.setBlock(p, paving, 2);
            }
        }

        // Border curb along both sides of the avenue: dark-oak fences + bottom slabs.
        for (int dx = -1; dx < 43; dx++) {
            BlockPos northCurb = new BlockPos(avenueX0 + dx, y, avenueZ0 - 1);
            BlockPos southCurb = new BlockPos(avenueX0 + dx, y, avenueZ0 + 6);
            level.setBlock(northCurb, Blocks.STONE_BRICK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), 2);
            level.setBlock(southCurb, Blocks.STONE_BRICK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), 2);
        }

        // Lantern poles down both sides every 8 blocks.
        for (int dx = 2; dx < 42; dx += 8) {
            buildLanternPole(level, new BlockPos(avenueX0 + dx, y + 1, avenueZ0 - 2));
            buildLanternPole(level, new BlockPos(avenueX0 + dx, y + 1, avenueZ0 + 7));
        }

        // Fountain at center of the avenue (avenue dx 18..23, dz 0..5).
        buildFountain(level, new BlockPos(avenueX0 + 19, y, avenueZ0 + 1));

        // North-row shops (z negative side, building front faces +Z toward avenue).
        // Each is 10 wide × 7 deep, sitting at z0 + 4..10 (front edge at avenueZ0 - 3 = z0 + 10).
        buildShop(level, new BlockPos(x0 + 4,  y, z0 + 4), 10, 7, ShopType.OLLIVANDERS,    /*frontIsSouth=*/ true);
        buildShop(level, new BlockPos(x0 + 15, y, z0 + 4), 10, 7, ShopType.MALKINS,        true);
        buildShop(level, new BlockPos(x0 + 26, y, z0 + 4), 10, 7, ShopType.APOTHECARY,     true);
        buildShop(level, new BlockPos(x0 + 37, y, z0 + 5),  9, 6, ShopType.OWLPOST,        true);

        // South-row shops (front faces -Z toward avenue).
        buildShop(level, new BlockPos(x0 + 4,  y, z0 + 21), 10, 7, ShopType.QUIDDITCH,      /*frontIsSouth=*/ false);
        buildShop(level, new BlockPos(x0 + 15, y, z0 + 21), 10, 7, ShopType.FORTESCUES,     false);
        buildShop(level, new BlockPos(x0 + 26, y, z0 + 21),  9, 7, ShopType.WEASLEY_WHEEZES, false);
        buildShop(level, new BlockPos(x0 + 36, y, z0 + 22), 10, 6, ShopType.GRINGOTTS,      false);

        // West entrance arch.
        buildArch(level, new BlockPos(avenueX0 - 1, y, avenueZ0 + 2));

        // East lantern tower — landmark visible from far away.
        buildTower(level, new BlockPos(x0 + 46, y, z0 + 14));

        // Record location in persistent saved data so /findalley can locate it later.
        ServerLevel serverLevel = level.getLevel();
        BlockPos announcePos = new BlockPos(x0 + 25, y, z0 + 16);
        DiagonAlleyTracker tracker = DiagonAlleyTracker.get(serverLevel);
        tracker.add(announcePos);

        // Announce to all online players so they can /tp or /findalley immediately.
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

    private enum ShopType {
        OLLIVANDERS, QUIDDITCH, APOTHECARY, MALKINS, FORTESCUES,
        WEASLEY_WHEEZES, GRINGOTTS, OWLPOST
    }

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

    // -----------------------------------------------------------------------------------------
    // Buildings
    // -----------------------------------------------------------------------------------------

    private void buildShop(WorldGenLevel level, BlockPos corner, int sx, int sz, ShopType shopType, boolean frontIsSouth) {
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int height = heightFor(shopType); // total wall height above ground (4 to 6)
        BlockState wall = wallBlockFor(shopType);
        BlockState floor = floorBlockFor(shopType);
        BlockState roofSolid = roofSolidFor(shopType);
        BlockState roofStair = roofStairFor(shopType);
        BlockState accent = accentBlockFor(shopType);

        // Floor + foundation ring.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                BlockPos p = new BlockPos(x0 + dx, y0, z0 + dz);
                boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                level.setBlock(p, edge ? wall : floor, 2);
            }
        }

        // Walls.
        for (int dy = 1; dy <= height; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    // Accent stripe at mid-height for visual interest.
                    BlockState b = (dy == height - 1 && shouldStripe(shopType)) ? accent : wall;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), b, 2);
                }
            }
        }

        // Windows along side walls.
        int windowY = y0 + 2;
        for (int dz = 2; dz < sz - 2; dz += 2) {
            level.setBlock(new BlockPos(x0, windowY, z0 + dz), Blocks.GLASS_PANE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 1, windowY, z0 + dz), Blocks.GLASS_PANE.defaultBlockState(), 2);
        }

        // Front wall (faces avenue).
        int frontZ = frontIsSouth ? z0 + sz - 1 : z0;
        Direction outward = frontIsSouth ? Direction.SOUTH : Direction.NORTH;
        int doorX = x0 + sx / 2;
        // Door opening.
        level.setBlock(new BlockPos(doorX, y0 + 1, frontZ), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(new BlockPos(doorX, y0 + 2, frontZ), Blocks.AIR.defaultBlockState(), 2);
        BlockState doorLower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, outward)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, outward)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        level.setBlock(new BlockPos(doorX, y0 + 1, frontZ), doorLower, 2);
        level.setBlock(new BlockPos(doorX, y0 + 2, frontZ), doorUpper, 2);

        // Front windows flanking the door.
        level.setBlock(new BlockPos(doorX - 2, windowY, frontZ), Blocks.GLASS_PANE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(doorX + 2, windowY, frontZ), Blocks.GLASS_PANE.defaultBlockState(), 2);

        // Lantern on each side of the door, hanging from the front wall above.
        level.setBlock(new BlockPos(doorX - 2, y0 + 4, frontZ),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        level.setBlock(new BlockPos(doorX + 2, y0 + 4, frontZ),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);

        // Banner block (wool) on the front above the door, themed per shop.
        BlockState banner = bannerWoolFor(shopType);
        level.setBlock(new BlockPos(doorX, y0 + height, frontZ), banner, 2);
        level.setBlock(new BlockPos(doorX, y0 + height + 1, frontZ), banner, 2);

        // Flat-and-gabled roof: solid layer at top, then ridge stairs along the long axis.
        int roofY = y0 + height + 1;
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, roofY, z0 + dz), roofSolid, 2);
            }
        }
        // Ridge raised by 1: stair blocks on either side flanking a slab top.
        int ridgeZ = z0 + sz / 2;
        for (int dx = 1; dx < sx - 1; dx++) {
            // Stair facing south on the south slope, north on the north slope.
            BlockState stairSouth = roofStair
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                    .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
            BlockState stairNorth = roofStair
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                    .setValue(BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT);
            level.setBlock(new BlockPos(x0 + dx, roofY + 1, ridgeZ - 1), stairSouth, 2);
            level.setBlock(new BlockPos(x0 + dx, roofY + 1, ridgeZ + 1), stairNorth, 2);
            level.setBlock(new BlockPos(x0 + dx, roofY + 1, ridgeZ), roofSolid, 2);
        }
        // Chimney at one end (corner).
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(new BlockPos(x0 + 1, roofY + 1 + dy, z0 + 1), Blocks.COBBLESTONE.defaultBlockState(), 2);
        }
        // Crowning torch on the chimney for warmth.
        level.setBlock(new BlockPos(x0 + 1, roofY + 4, z0 + 1), Blocks.TORCH.defaultBlockState(), 2);

        // Interior fixtures + shopkeeper.
        placeInterior(level, x0, y0, z0, sx, sz, shopType);
        BlockPos shopkeeperPos = new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz / 2);
        spawnShopkeeper(level, shopkeeperPos, shopType);
    }

    private int heightFor(ShopType s) {
        if (s == ShopType.OLLIVANDERS) return 5;
        if (s == ShopType.GRINGOTTS) return 5;
        if (s == ShopType.WEASLEY_WHEEZES) return 5;
        return 4;
    }

    private boolean shouldStripe(ShopType s) {
        return s == ShopType.WEASLEY_WHEEZES || s == ShopType.MALKINS || s == ShopType.FORTESCUES;
    }

    private BlockState wallBlockFor(ShopType s) {
        if (s == ShopType.OLLIVANDERS) return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        if (s == ShopType.QUIDDITCH) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        if (s == ShopType.APOTHECARY) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (s == ShopType.MALKINS) return Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        if (s == ShopType.FORTESCUES) return Blocks.CALCITE.defaultBlockState();
        if (s == ShopType.WEASLEY_WHEEZES) return Blocks.STRIPPED_ACACIA_LOG.defaultBlockState();
        if (s == ShopType.GRINGOTTS) return Blocks.POLISHED_DIORITE.defaultBlockState();
        return Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
    }

    private BlockState floorBlockFor(ShopType s) {
        if (s == ShopType.GRINGOTTS) return Blocks.POLISHED_DIORITE.defaultBlockState();
        if (s == ShopType.FORTESCUES) return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        if (s == ShopType.WEASLEY_WHEEZES) return Blocks.RED_NETHER_BRICKS.defaultBlockState();
        return Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private BlockState roofSolidFor(ShopType s) {
        if (s == ShopType.GRINGOTTS) return Blocks.POLISHED_ANDESITE.defaultBlockState();
        if (s == ShopType.FORTESCUES) return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        if (s == ShopType.WEASLEY_WHEEZES) return Blocks.NETHER_BRICKS.defaultBlockState();
        return Blocks.DARK_OAK_PLANKS.defaultBlockState();
    }

    private BlockState roofStairFor(ShopType s) {
        if (s == ShopType.GRINGOTTS) return Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState();
        if (s == ShopType.FORTESCUES) return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState();
        if (s == ShopType.WEASLEY_WHEEZES) return Blocks.NETHER_BRICK_STAIRS.defaultBlockState();
        return Blocks.DARK_OAK_STAIRS.defaultBlockState();
    }

    private BlockState accentBlockFor(ShopType s) {
        if (s == ShopType.MALKINS) return Blocks.PINK_WOOL.defaultBlockState();
        if (s == ShopType.WEASLEY_WHEEZES) return Blocks.ORANGE_WOOL.defaultBlockState();
        if (s == ShopType.FORTESCUES) return Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
        return Blocks.DARK_OAK_PLANKS.defaultBlockState();
    }

    private BlockState bannerWoolFor(ShopType s) {
        if (s == ShopType.OLLIVANDERS) return Blocks.BROWN_WOOL.defaultBlockState();
        if (s == ShopType.QUIDDITCH) return Blocks.RED_WOOL.defaultBlockState();
        if (s == ShopType.APOTHECARY) return Blocks.GREEN_WOOL.defaultBlockState();
        if (s == ShopType.MALKINS) return Blocks.PINK_WOOL.defaultBlockState();
        if (s == ShopType.FORTESCUES) return Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
        if (s == ShopType.WEASLEY_WHEEZES) return Blocks.ORANGE_WOOL.defaultBlockState();
        if (s == ShopType.GRINGOTTS) return Blocks.GOLD_BLOCK.defaultBlockState();
        return Blocks.PURPLE_WOOL.defaultBlockState();
    }

    private void placeInterior(WorldGenLevel level, int x0, int y0, int z0, int sx, int sz, ShopType s) {
        int cx = x0 + sx / 2;
        int cz = z0 + sz / 2;
        if (s == ShopType.OLLIVANDERS) {
            // Two banks of bookshelves + lecterns + central display.
            for (int dx = 1; dx < sx - 1; dx++) {
                if (dx == sx / 2) continue;
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.BOOKSHELF.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + 1), Blocks.BOOKSHELF.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(cx - 1, y0 + 1, cz), Blocks.LECTERN.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 1, y0 + 1, cz), Blocks.LECTERN.defaultBlockState(), 2);
        } else if (s == ShopType.QUIDDITCH) {
            for (int dx = 1; dx < sx - 1; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.DARK_OAK_SLAB.defaultBlockState(), 2);
            }
            // Broom-handle display: stripped logs standing in barrels.
            level.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + sz - 2), Blocks.BARREL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 3, y0 + 1, z0 + sz - 2), Blocks.BARREL.defaultBlockState(), 2);
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(new BlockPos(x0 + 2, y0 + 1 + dy, z0 + sz - 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + sx - 3, y0 + 1 + dy, z0 + sz - 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
            }
        } else if (s == ShopType.APOTHECARY) {
            level.setBlock(new BlockPos(cx - 1, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 1, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx - 2, y0 + 1, z0 + sz - 2), Blocks.CAULDRON.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 2, y0 + 1, z0 + sz - 2), Blocks.CAULDRON.defaultBlockState(), 2);
            // Hanging ingredients (potted plants on the counter).
            for (int dx = 1; dx < sx - 1; dx += 2) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), 2);
            }
        } else if (s == ShopType.MALKINS) {
            // Armor stands flanking the doorway side; clothing on item frames represented by wool blocks.
            for (int dx = 1; dx < sx - 1; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1), ((dx % 2 == 0) ? Blocks.PINK_WOOL : Blocks.MAGENTA_WOOL).defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + 1), ((dx % 2 == 0) ? Blocks.PINK_WOOL : Blocks.MAGENTA_WOOL).defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(cx, y0 + 1, cz), Blocks.LOOM.defaultBlockState(), 2);
        } else if (s == ShopType.FORTESCUES) {
            // Ice cream parlour: blue ice / snow display, packed ice "freezer".
            for (int dx = 1; dx < sx - 1; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 1), Blocks.PACKED_ICE.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(cx, y0 + 2, z0 + 1), Blocks.BLUE_ICE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx - 1, y0 + 1, cz), Blocks.CAKE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 1, y0 + 1, cz), Blocks.CAKE.defaultBlockState(), 2);
        } else if (s == ShopType.WEASLEY_WHEEZES) {
            // Bright chaotic interior — colored wool, jukebox, redstone gizmos.
            level.setBlock(new BlockPos(cx, y0 + 1, cz), Blocks.JUKEBOX.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx - 1, y0 + 1, cz), Blocks.ORANGE_WOOL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 1, y0 + 1, cz), Blocks.YELLOW_WOOL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx, y0 + 1, z0 + sz - 2), Blocks.REDSTONE_LAMP.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx - 2, y0 + 1, z0 + sz - 2), Blocks.TNT.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 2, y0 + 1, z0 + sz - 2), Blocks.TNT.defaultBlockState(), 2);
        } else if (s == ShopType.GRINGOTTS) {
            // Gold-block vault display behind iron-bar counter.
            for (int dx = 1; dx < sx - 1; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.IRON_BARS.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.IRON_BARS.defaultBlockState(), 2);
            }
            level.setBlock(new BlockPos(cx - 1, y0 + 1, z0 + sz - 3), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx, y0 + 1, z0 + sz - 3), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx + 1, y0 + 1, z0 + sz - 3), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            level.setBlock(new BlockPos(cx, y0 + 1, cz), Blocks.SMITHING_TABLE.defaultBlockState(), 2);
        } else if (s == ShopType.OWLPOST) {
            // Owl post: perches (fences) along the back, hay bales below.
            for (int dx = 1; dx < sx - 1; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + sz - 2), Blocks.HAY_BLOCK.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y0 + 2, z0 + sz - 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + dx, y0 + 3, z0 + sz - 2), Blocks.OAK_FENCE.defaultBlockState(), 2);
            }
        }

        // Universal interior lantern hanging from the ceiling.
        int roofY = y0 + heightFor(s) + 1;
        level.setBlock(new BlockPos(cx, roofY - 1, cz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private void spawnShopkeeper(WorldGenLevel level, BlockPos pos, ShopType s) {
        Villager villager = EntityType.VILLAGER.create(level.getLevel());
        if (villager == null) return;
        VillagerProfession profession;
        String name;
        switch (s) {
            case OLLIVANDERS    -> { profession = VillagerProfession.LIBRARIAN;  name = "Mr. Ollivander"; }
            case QUIDDITCH      -> { profession = VillagerProfession.FLETCHER;   name = "Broom Master"; }
            case APOTHECARY     -> { profession = VillagerProfession.CLERIC;     name = "Slug & Jiggers"; }
            case MALKINS        -> { profession = VillagerProfession.SHEPHERD;   name = "Madam Malkin"; }
            case FORTESCUES     -> { profession = VillagerProfession.BUTCHER;    name = "Florean Fortescue"; }
            case WEASLEY_WHEEZES-> { profession = VillagerProfession.TOOLSMITH;  name = "Fred & George"; }
            case GRINGOTTS      -> { profession = VillagerProfession.WEAPONSMITH; name = "Gringotts Goblin"; }
            case OWLPOST        -> { profession = VillagerProfession.LEATHERWORKER; name = "Owl Postmaster"; }
            default             -> { profession = VillagerProfession.NONE;       name = "Shopkeeper"; }
        }
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, 3));
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
    }

    // -----------------------------------------------------------------------------------------
    // Standalone props
    // -----------------------------------------------------------------------------------------

    private void buildLanternPole(WorldGenLevel level, BlockPos base) {
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(base.above(dy), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        }
        // Cross-arm at top with hanging lantern.
        level.setBlock(base.above(3), Blocks.DARK_OAK_FENCE.defaultBlockState(), 2);
        level.setBlock(base.above(2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private void buildFountain(WorldGenLevel level, BlockPos corner) {
        // 6x6 fountain footprint. Border of polished stone, inner 4x4 water with a centerpiece pillar.
        int x0 = corner.getX();
        int y = corner.getY();
        int z0 = corner.getZ();
        for (int dx = 0; dx < 6; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
                boolean inner = dx >= 1 && dx <= 4 && dz >= 1 && dz <= 2;
                if (inner) {
                    // Water basin, sunk one block.
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    level.setBlock(p.below(), Blocks.WATER.defaultBlockState(), 2);
                } else {
                    level.setBlock(p, Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
                }
            }
        }
        // Centerpiece: chiseled stone pillar rising 3 blocks with a lantern on top.
        BlockPos pillar = new BlockPos(x0 + 2, y, z0 + 1);
        level.setBlock(pillar.below(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(pillar.above(dy), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        level.setBlock(pillar.above(3), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        // Wider pillar at the other end.
        BlockPos pillar2 = new BlockPos(x0 + 3, y, z0 + 2);
        for (int dy = 0; dy < 3; dy++) {
            level.setBlock(pillar2.above(dy), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        level.setBlock(pillar2.above(3), Blocks.SEA_LANTERN.defaultBlockState(), 2);
    }

    private void buildArch(WorldGenLevel level, BlockPos base) {
        // 4-block-wide arch over the avenue entrance.
        int x = base.getX();
        int y = base.getY();
        int z0 = base.getZ();
        for (int dy = 0; dy < 5; dy++) {
            level.setBlock(new BlockPos(x, y + dy, z0 - 1), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x, y + dy, z0 + 4), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        for (int dz = 0; dz < 4; dz++) {
            level.setBlock(new BlockPos(x, y + 5, z0 + dz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
        }
        // Hanging lanterns under the arch.
        level.setBlock(new BlockPos(x, y + 4, z0 + 1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
        level.setBlock(new BlockPos(x, y + 4, z0 + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private void buildTower(WorldGenLevel level, BlockPos base) {
        // 5x5 stone-brick tower rising 18 blocks, with a glowing lantern crown.
        int x0 = base.getX();
        int y0 = base.getY();
        int z0 = base.getZ();
        int height = 18;
        // Solid stone base for the tower (3 blocks deep).
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                for (int dy = -3; dy < 0; dy++) {
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Hollow stone-brick tube with arrow-slit windows.
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                    if (!edge) continue;
                    BlockState b = ((dx + dz + dy) % 7 == 0) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                            : ((dy % 4 == 3 && (dx == 2 || dz == 2)) ? Blocks.GLASS_PANE.defaultBlockState()
                            : Blocks.STONE_BRICKS.defaultBlockState());
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), b, 2);
                }
            }
        }
        // Crenellated top + lantern crown.
        int topY = y0 + height;
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
                if (edge && (dx + dz) % 2 == 0) {
                    level.setBlock(new BlockPos(x0 + dx, topY, z0 + dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        // Bright crown so the tower is visible from a distance.
        level.setBlock(new BlockPos(x0 + 2, topY, z0 + 2), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 2, topY + 1, z0 + 2), Blocks.GLOWSTONE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 2, topY + 2, z0 + 2), Blocks.JACK_O_LANTERN.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x0 + 2, topY + 3, z0 + 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }
}
