package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class MandrakePatchFeature extends Feature<NoneFeatureConfiguration> {

    public MandrakePatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        boolean placed = false;
        int targetPlacements = 1 + random.nextInt(3);
        int placements = 0;

        for (int attempt = 0; attempt < 18 && placements < targetPlacements; attempt++) {
            int x = origin.getX() + random.nextInt(8) - random.nextInt(8);
            int z = origin.getZ() + random.nextInt(8) - random.nextInt(8);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos plantPos = new BlockPos(x, y, z);
            BlockState plant = ExampleMod.MANDRAKE_PLANT.get().defaultBlockState();

            if (!level.isEmptyBlock(plantPos) || !plant.canSurvive(level, plantPos)) {
                continue;
            }

            BlockState floor = level.getBlockState(plantPos.below());
            if (!isMandrakeSoil(floor)) {
                continue;
            }

            level.setBlock(plantPos, plant, 3);
            placed = true;
            placements++;
        }

        return placed;
    }

    private static boolean isMandrakeSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MOSS_BLOCK);
    }
}
