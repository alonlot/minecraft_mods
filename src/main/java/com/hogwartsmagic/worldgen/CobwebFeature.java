package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class CobwebFeature extends Feature<NoneFeatureConfiguration> {

    public CobwebFeature(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        boolean placedAny = false;
        // Generate a dense, localized "cluster" of cobwebs
        for (int i = 0; i < 64; i++) {
            // Smaller radius to force tight bunching
            BlockPos pos = origin.offset(
                    random.nextInt(4) - random.nextInt(4),
                    random.nextInt(3) - random.nextInt(3),
                    random.nextInt(4) - random.nextInt(4)
            );

            if (level.isEmptyBlock(pos) || level.getBlockState(pos).canBeReplaced()) {
                // Must be near a solid block (ground or tree)
                boolean hasSupport = false;
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    if (level.getBlockState(pos.relative(dir)).isSolidRender(level, pos.relative(dir))) {
                        hasSupport = true;
                        break;
                    }
                }

                if (hasSupport) {
                    level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
                    placedAny = true;
                }
            }
        }
        return placedAny;
    }
}
