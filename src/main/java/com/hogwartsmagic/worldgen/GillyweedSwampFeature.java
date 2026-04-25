package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class GillyweedSwampFeature extends Feature<NoneFeatureConfiguration> {

    public GillyweedSwampFeature(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean placed = false;
        int targetPlacements = 4 + random.nextInt(6);
        int placements = 0;

        for (int attempt = 0; attempt < 28 && placements < targetPlacements; attempt++) {
            BlockPos floorPos = findSwampFloorBelowWater(level, origin.offset(
                    random.nextInt(8) - random.nextInt(8),
                    0,
                    random.nextInt(8) - random.nextInt(8)
            ));
            if (floorPos == null) {
                continue;
            }

            BlockPos plantPos = findPlacementInWaterColumn(level, floorPos, random);
            if (plantPos == null) {
                continue;
            }

            BlockState plant = ExampleMod.GILLYWEED_PLANT.get().defaultBlockState();
            if (!plant.canSurvive(level, plantPos)) {
                continue;
            }

            level.setBlock(plantPos, plant, 3);
            placed = true;
            placements++;
        }

        return placed;
    }

    private static BlockPos findSwampFloorBelowWater(WorldGenLevel level, BlockPos column) {
        int x = column.getX();
        int z = column.getZ();
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1;
        int minY = Math.max(level.getMinBuildHeight() + 1, topY - 20);
        boolean foundWater = false;

        for (int y = topY; y >= minY; y--) {
            BlockPos currentPos = new BlockPos(x, y, z);
            BlockState currentState = level.getBlockState(currentPos);

            if (currentState.getFluidState().is(FluidTags.WATER)) {
                foundWater = true;
                continue;
            }

            if (foundWater && isSwampFloor(currentState) && level.getBlockState(currentPos.above()).getFluidState().is(FluidTags.WATER)) {
                return currentPos;
            }

            if (foundWater) {
                return null;
            }
        }

        return null;
    }

    private static BlockPos findPlacementInWaterColumn(WorldGenLevel level, BlockPos floorPos, RandomSource random) {
        BlockPos.MutableBlockPos scan = floorPos.above().mutable();
        BlockPos[] validPositions = new BlockPos[8];
        int validCount = 0;

        for (int i = 0; i < 8; i++) {
            if (!level.isWaterAt(scan)) {
                break;
            }

            BlockState here = level.getBlockState(scan);
            BlockState belowPlant = level.getBlockState(scan.below());
            if (here.is(Blocks.WATER)
                    && here.getFluidState().is(FluidTags.WATER)
                    && isSwampFloor(belowPlant)
                    && !level.getBlockState(scan.above()).is(ExampleMod.GILLYWEED_PLANT.get())) {
                validPositions[validCount++] = scan.immutable();
            }

            scan.move(Direction.UP);
        }

        if (validCount == 0) {
            return null;
        }

        return validPositions[random.nextInt(validCount)];
    }

    private static boolean isSwampFloor(BlockState state) {
        return state.is(Blocks.MUD)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE);
    }
}
