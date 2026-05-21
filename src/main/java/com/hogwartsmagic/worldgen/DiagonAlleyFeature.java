package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds a three-shop Diagon Alley street: Ollivanders (wand shop), Quality Quidditch
 * Supplies (broom shop), Slug &amp; Jiggers Apothecary. Each shop is a small dark-oak
 * cabin with a themed interior fixture and a robed villager shopkeeper inside. Buildings
 * are arrayed along the +X axis with a cobblestone path between them.
 *
 * The feature is heavy and slow — rely on the rarity_filter in placed_feature JSON to keep
 * it sparse (1 in ~400 chunks rolled, additionally biome/terrain gated).
 */
public class DiagonAlleyFeature extends Feature<NoneFeatureConfiguration> {

    public DiagonAlleyFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // Anchor to surface height at origin.
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX(), origin.getZ());
        BlockPos base = new BlockPos(origin.getX(), surfaceY, origin.getZ());

        // Require a roughly flat 28x14 footprint within ±2 blocks of base.Y; otherwise abort.
        if (!isReasonablyFlat(level, base, 28, 14, 2)) {
            return false;
        }

        int streetY = base.getY();
        int streetMinX = base.getX();
        int streetMinZ = base.getZ();

        // Layout: street runs in +X direction. Shops sit on the +Z side, with one mirrored on -Z.
        // Footprint (X×Z): 28 × 14. Path is 4 wide centered on Z=base+7.
        buildPaving(level, streetMinX, streetY, streetMinZ + 5, 28, 4);

        // Three buildings along +Z side: x offsets 1, 11, 21. Each 8x8 footprint, 5 high.
        // One extra building along -Z side, centered.
        buildShop(level, random,
                new BlockPos(streetMinX + 1, streetY, streetMinZ + 10),
                "Ollivanders", DiagonShopType.OLLIVANDERS);
        buildShop(level, random,
                new BlockPos(streetMinX + 11, streetY, streetMinZ + 10),
                "Quality Quidditch Supplies", DiagonShopType.QUIDDITCH);
        buildShop(level, random,
                new BlockPos(streetMinX + 21, streetY, streetMinZ + 10),
                "Slug & Jiggers Apothecary", DiagonShopType.APOTHECARY);

        // Street lanterns on both sides.
        placeLantern(level, new BlockPos(streetMinX + 4, streetY + 1, streetMinZ + 9));
        placeLantern(level, new BlockPos(streetMinX + 14, streetY + 1, streetMinZ + 9));
        placeLantern(level, new BlockPos(streetMinX + 24, streetY + 1, streetMinZ + 9));
        placeLantern(level, new BlockPos(streetMinX + 4, streetY + 1, streetMinZ + 4));
        placeLantern(level, new BlockPos(streetMinX + 14, streetY + 1, streetMinZ + 4));
        placeLantern(level, new BlockPos(streetMinX + 24, streetY + 1, streetMinZ + 4));

        // Welcome sign.
        BlockPos signPos = new BlockPos(streetMinX + 14, streetY + 1, streetMinZ + 6);
        placeSign(level, signPos, "Diagon Alley", "Welcome, traveller", "", "Hogwarts Magic");

        return true;
    }

    private enum DiagonShopType { OLLIVANDERS, QUIDDITCH, APOTHECARY }

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

    private void buildPaving(WorldGenLevel level, int x0, int y, int z0, int sx, int sz) {
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
                // Stone bricks for the path; ground beneath is cobble to support adjacent terrain.
                level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(p.below(), Blocks.COBBLESTONE.defaultBlockState(), 2);
                // Clear two blocks of air above so foliage doesn't poke through the road.
                level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(p.above(2), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private void buildShop(WorldGenLevel level, RandomSource random, BlockPos corner, String name, DiagonShopType shopType) {
        // 8x6x8 building (X × Y × Z). Door faces -Z (toward the street).
        int x0 = corner.getX();
        int y0 = corner.getY();
        int z0 = corner.getZ();
        int sx = 8;
        int sy = 6;
        int sz = 8;

        BlockState wall = wallBlockFor(shopType);
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState roof = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState window = Blocks.GLASS_PANE.defaultBlockState();

        // Foundation row (y=y0): floor inside, wall ring outside.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                BlockPos p = new BlockPos(x0 + dx, y0, z0 + dz);
                boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                level.setBlock(p, edge ? wall : floor, 2);
            }
        }

        // Walls y0+1..y0+3.
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    level.setBlock(new BlockPos(x0 + dx, y0 + dy, z0 + dz), wall, 2);
                }
            }
        }

        // Windows on the side walls at y0+2.
        level.setBlock(new BlockPos(x0, y0 + 2, z0 + 3), window, 2);
        level.setBlock(new BlockPos(x0, y0 + 2, z0 + 5), window, 2);
        level.setBlock(new BlockPos(x0 + sx - 1, y0 + 2, z0 + 3), window, 2);
        level.setBlock(new BlockPos(x0 + sx - 1, y0 + 2, z0 + 5), window, 2);
        // Front-facing window strip flanking the door.
        level.setBlock(new BlockPos(x0 + 1, y0 + 2, z0), window, 2);
        level.setBlock(new BlockPos(x0 + 2, y0 + 2, z0), window, 2);
        level.setBlock(new BlockPos(x0 + 5, y0 + 2, z0), window, 2);
        level.setBlock(new BlockPos(x0 + 6, y0 + 2, z0), window, 2);

        // Door on -Z (front) wall at dx=3,4 (door is 2 tall, but we use just one block-wide).
        BlockPos doorBottom = new BlockPos(x0 + 4, y0 + 1, z0);
        // Cut out the door slot.
        level.setBlock(doorBottom, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(doorBottom.above(), Blocks.AIR.defaultBlockState(), 2);
        // Place a dark oak door facing south (out toward street, which is at -Z).
        BlockState doorLower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        level.setBlock(doorBottom, doorLower, 2);
        level.setBlock(doorBottom.above(), doorUpper, 2);

        // Roof — solid plank slab at y0+4, and gabled peak at y0+5.
        for (int dx = 0; dx < sx; dx++) {
            for (int dz = 0; dz < sz; dz++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 4, z0 + dz), roof, 2);
            }
        }
        // Simple peaked roof: a single row of stair blocks down the middle.
        for (int dx = 1; dx < sx - 1; dx++) {
            level.setBlock(new BlockPos(x0 + dx, y0 + 5, z0 + sz / 2), roof, 2);
        }

        // Wall sign above the door, facing -Z (NORTH).
        BlockPos signPos = new BlockPos(x0 + 4, y0 + 3, z0);
        placeWallSign(level, signPos, Direction.NORTH, name, "");

        // Interior fixtures + shopkeeper.
        BlockPos interiorCenter = new BlockPos(x0 + sx / 2, y0 + 1, z0 + sz / 2 + 1);
        placeInterior(level, random, x0, y0, z0, sx, sz, shopType);
        spawnShopkeeper(level, interiorCenter, shopType);

        // Interior lantern hanging from the ceiling.
        level.setBlock(new BlockPos(x0 + sx / 2, y0 + 3, z0 + sz / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
    }

    private BlockState wallBlockFor(DiagonShopType shopType) {
        if (shopType == DiagonShopType.OLLIVANDERS) return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        if (shopType == DiagonShopType.QUIDDITCH) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    }

    private void placeInterior(WorldGenLevel level, RandomSource random, int x0, int y0, int z0, int sx, int sz, DiagonShopType shopType) {
        if (shopType == DiagonShopType.OLLIVANDERS) {
            // Two rows of bookshelves with lecterns "displaying" wands.
            level.setBlock(new BlockPos(x0 + 1, y0 + 1, z0 + sz - 2), Blocks.BOOKSHELF.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + sz - 2), Blocks.BOOKSHELF.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 2, y0 + 1, z0 + sz - 2), Blocks.BOOKSHELF.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 3, y0 + 1, z0 + sz - 2), Blocks.BOOKSHELF.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 3, y0 + 1, z0 + sz - 3), Blocks.LECTERN.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 4, y0 + 1, z0 + sz - 3), Blocks.LECTERN.defaultBlockState(), 2);
            // Counter.
            for (int dx = 2; dx < sx - 2; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 2), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
            }
        } else if (shopType == DiagonShopType.QUIDDITCH) {
            // Two display "brooms" leaning on the back wall + a barrel.
            level.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + sz - 2), Blocks.BARREL.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 3, y0 + 1, z0 + sz - 2), Blocks.BARREL.defaultBlockState(), 2);
            // Stripped log "broom handles" sticking up.
            for (int dy = 1; dy <= 2; dy++) {
                level.setBlock(new BlockPos(x0 + 3, y0 + dy, z0 + sz - 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x0 + sx - 4, y0 + dy, z0 + sz - 2), Blocks.STRIPPED_OAK_LOG.defaultBlockState(), 2);
            }
            // Counter.
            for (int dx = 2; dx < sx - 2; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 2), Blocks.DARK_OAK_SLAB.defaultBlockState(), 2);
            }
        } else {
            // Apothecary: brewing stands + cauldrons.
            level.setBlock(new BlockPos(x0 + 2, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 3, y0 + 1, z0 + sz - 2), Blocks.BREWING_STAND.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + 3, y0 + 1, z0 + sz - 2), Blocks.CAULDRON.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x0 + sx - 4, y0 + 1, z0 + sz - 2), Blocks.CAULDRON.defaultBlockState(), 2);
            // Counter.
            for (int dx = 2; dx < sx - 2; dx++) {
                level.setBlock(new BlockPos(x0 + dx, y0 + 1, z0 + 2), Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState(), 2);
            }
        }
    }

    private void spawnShopkeeper(WorldGenLevel level, BlockPos pos, DiagonShopType shopType) {
        Villager villager = EntityType.VILLAGER.create(level.getLevel());
        if (villager == null) return;
        // Custom profession unavailable without registering one — set a vanilla profession that
        // matches the shop's flavor so the villager looks the part on first render.
        VillagerProfession profession;
        if (shopType == DiagonShopType.OLLIVANDERS) {
            profession = VillagerProfession.LIBRARIAN;
        } else if (shopType == DiagonShopType.QUIDDITCH) {
            profession = VillagerProfession.FLETCHER;
        } else {
            profession = VillagerProfession.CLERIC;
        }
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, profession, 3));
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        String displayName = shopType == DiagonShopType.OLLIVANDERS ? "Mr. Ollivander"
                : shopType == DiagonShopType.QUIDDITCH ? "Quidditch Clerk"
                : "Potions Master";
        villager.setCustomName(Component.literal(displayName));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
    }

    private void placeLantern(WorldGenLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.LANTERN.defaultBlockState(), 2);
    }

    private void placeSign(WorldGenLevel level, BlockPos pos, String line1, String line2, String line3, String line4) {
        BlockState signState = Blocks.SPRUCE_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, 8);
        level.setBlock(pos, signState, 2);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.updateText(text -> text
                            .setMessage(0, Component.literal(line1))
                            .setMessage(1, Component.literal(line2))
                            .setMessage(2, Component.literal(line3))
                            .setMessage(3, Component.literal(line4)),
                    true);
        }
    }

    private void placeWallSign(WorldGenLevel level, BlockPos pos, Direction facing, String line1, String line2) {
        BlockState signState = Blocks.SPRUCE_WALL_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        level.setBlock(pos, signState, 2);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.updateText(text -> text
                            .setMessage(0, Component.literal(line1))
                            .setMessage(1, Component.literal(line2)),
                    true);
        }
    }
}
