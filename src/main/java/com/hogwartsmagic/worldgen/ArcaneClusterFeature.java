package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class ArcaneClusterFeature extends Feature<NoneFeatureConfiguration> {

    public ArcaneClusterFeature(com.mojang.serialization.Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos start = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, context.origin());
        boolean placedAny = false;

        for (int i = 0; i < 18; i++) {
            BlockPos candidate = start.offset(
                    random.nextInt(8) - random.nextInt(8),
                    random.nextInt(5) - 2,
                    random.nextInt(8) - random.nextInt(8)
            );

            if (!level.getBlockState(candidate).canBeReplaced()) {
                continue;
            }

            Direction facing = findSupportDirection(level, candidate, random);
            if (facing == null) {
                continue;
            }

            BlockState cluster = ExampleMod.ARCANE_CLUSTER.get().defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, facing)
                    .setValue(AmethystClusterBlock.WATERLOGGED, false);

            if (cluster.canSurvive(level, candidate)) {
                level.setBlock(candidate, cluster, 2);
                placedAny = true;
            }
        }

        return placedAny;
    }

    private static Direction findSupportDirection(WorldGenLevel level, BlockPos pos, RandomSource random) {
        Direction[] directions = Direction.values();
        int startIndex = random.nextInt(directions.length);

        for (int i = 0; i < directions.length; i++) {
            Direction facing = directions[(startIndex + i) % directions.length];
            BlockPos supportPos = pos.relative(facing.getOpposite());
            BlockState support = level.getBlockState(supportPos);

            if (!support.isFaceSturdy(level, supportPos, facing)) {
                continue;
            }

            if (isGoodArcaneSupport(support)) {
                return facing;
            }
        }

        return null;
    }

    private static boolean isGoodArcaneSupport(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE);
    }
}
