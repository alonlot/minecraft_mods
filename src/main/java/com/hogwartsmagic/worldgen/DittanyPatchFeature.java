package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DittanyPlantBlock;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A small patch of wild dittany (2-4 plants within 5 blocks) on the ground below any leaves, as MandrakePatchFeature
 * does for mandrakes.
 */
public class DittanyPatchFeature extends Feature<NoneFeatureConfiguration> {

    public DittanyPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState plant = ExampleMod.DITTANY_PLANT.get().defaultBlockState().setValue(DittanyPlantBlock.WILD, true);
        int target = 2 + random.nextInt(3);
        int placed = 0;
        for (int attempt = 0; attempt < 20 && placed < target; attempt++) {
            int x = origin.getX() + random.nextInt(6) - random.nextInt(6);
            int z = origin.getZ() + random.nextInt(6) - random.nextInt(6);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isEmptyBlock(pos) || !plant.canSurvive(level, pos)) {
                continue;
            }
            level.setBlock(pos, plant, 2);
            placed++;
        }
        return placed > 0;
    }
}
