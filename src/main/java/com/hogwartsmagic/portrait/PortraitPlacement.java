package com.hogwartsmagic.portrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Hangs a portrait on the nearest free wall, for structures that place it themselves (Mini Hogwarts). */
public final class PortraitPlacement {
    private static final int[] HEIGHTS = {1, 0, 2};
    private static final int[] SLIDES = {0, 1, -1, 2, -2, 3, -3};

    private PortraitPlacement() {
    }

    /**
     * Walks out from {@code floor} (an air block standing on the floor) in all four directions and hangs a portrait
     * of this kind on the closest wall within {@code reach} blocks where it survives, its bottom edge about a block
     * above the floor. Never loads a chunk. Returns the portrait, or null when no wall fits.
     */
    public static PortraitEntity hangNear(ServerLevel level, PortraitKind kind, BlockPos floor, int reach) {
        PortraitEntity best = null;
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
                PortraitEntity fit = fitOnWall(level, kind, probe, toWall.getOpposite());
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
    private static PortraitEntity fitOnWall(ServerLevel level, PortraitKind kind, BlockPos wall, Direction facing) {
        BlockPos front = wall.relative(facing);
        Direction along = facing.getClockWise();
        int maxSlide = Math.max(2, kind.widthBlocks() - 1); // the 2-wide Fat Lady slides up to 2, a 4-wide one to 3
        for (int dy : HEIGHTS) {
            for (int slide : SLIDES) {
                if (Math.abs(slide) > maxSlide) {
                    continue;
                }
                PortraitEntity portrait =
                        new PortraitEntity(level, kind, front.relative(along, slide).above(dy), facing);
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
