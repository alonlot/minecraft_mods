package com.hogwartsmagic.portrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Hangs a portrait on the nearest free wall, for structures that place her themselves (Mini Hogwarts). */
public final class PortraitPlacement {
    private static final int[] HEIGHTS = {1, 0, 2};
    private static final int[] SLIDES = {0, 1, -1, 2, -2};

    private PortraitPlacement() {
    }

    /**
     * Walks out from {@code floor} (an air block standing on the floor) in all four directions and hangs the
     * Fat Lady on the closest wall within {@code reach} blocks where she survives, her bottom edge about a block
     * above the floor. Never loads a chunk. Returns the portrait, or null when no wall fits.
     */
    public static FatLadyPortraitEntity hangFatLadyNear(ServerLevel level, BlockPos floor, int reach) {
        FatLadyPortraitEntity best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Direction toWall : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= reach && k < bestDist; k++) {
                BlockPos probe = floor.relative(toWall, k).above();
                if (!level.isLoaded(probe)) {
                    break;
                }
                if (level.getBlockState(probe).isAir()) {
                    continue;
                }
                FatLadyPortraitEntity fit = fitOnWall(level, probe, toWall.getOpposite());
                if (fit != null) {
                    best = fit;
                    bestDist = k;
                }
                break; // the first solid block this way is the wall, fit or not
            }
        }
        if (best != null) {
            level.addFreshEntity(best);
        }
        return best;
    }

    /** Tries a few heights and slides along the wall face that {@code wall} shows toward {@code facing}. */
    private static FatLadyPortraitEntity fitOnWall(ServerLevel level, BlockPos wall, Direction facing) {
        BlockPos front = wall.relative(facing);
        Direction along = facing.getClockWise();
        for (int dy : HEIGHTS) {
            for (int slide : SLIDES) {
                FatLadyPortraitEntity portrait =
                        new FatLadyPortraitEntity(level, front.relative(along, slide).above(dy), facing);
                net.minecraft.world.phys.AABB box = portrait.getBoundingBox().inflate(1.0); // canvas + wall behind
                if (level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ)) && portrait.survives()) {
                    return portrait;
                }
            }
        }
        return null;
    }
}
