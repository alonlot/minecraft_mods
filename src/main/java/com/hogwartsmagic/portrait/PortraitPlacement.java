package com.hogwartsmagic.portrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

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

    /**
     * The cell a portrait hanging on the {@code facing} face of the block {@code wall} occupies: the cell in front
     * of that block, which is the entity's tile position. A castle anchor names the wall block (see
     * {@code MiniHogwartsAnchors}), so this is the one place that turns it into where she hangs.
     *
     * <p>Her canvas then covers that cell and the one to its right ({@code facing.getCounterClockWise()}), from one
     * block below the cell to two above it (see {@code FatLadyPortraitEntity.calculateBoundingBox}). For the
     * castle's anchor, wall (60, 47, 155) facing north: cell (60, 47, 154), canvas x 59..60, y 46..49, z 154, with
     * the wall's face at z 155 behind every cell of it.
     */
    public static BlockPos canvasCell(BlockPos wall, Direction facing) {
        return wall.relative(facing);
    }

    /**
     * Builds, without adding or testing, the Fat Lady for {@link #canvasCell}'s cell. Reads no blocks, so it is safe
     * on a worldgen thread; the caller decides whether she fits and adds her.
     */
    public static FatLadyPortraitEntity portraitInCell(Level level, BlockPos cell, Direction facing) {
        return new FatLadyPortraitEntity(level, cell, facing);
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
