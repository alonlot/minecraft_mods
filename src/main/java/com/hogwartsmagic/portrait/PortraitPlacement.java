package com.hogwartsmagic.portrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where a living portrait hangs, for structures that place one themselves: the cell a Mini Hogwarts anchor names
 * ({@link #canvasCell}, {@link #portraitInCell}, used on worldgen threads), and an in-world search for the nearest
 * free wall ({@link #hangNear}, which the castle's schematic-only fallback mirrors).
 */
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

    /**
     * The cell a portrait hanging on the {@code facing} face of the block {@code wall} occupies: the cell in front
     * of that block, which is the entity's tile position. A castle anchor names the wall block (see
     * {@code MiniHogwartsAnchors}), so this is the one place that turns it into where the portrait hangs.
     *
     * <p>The canvas then covers that cell and its neighbours as {@code PortraitEntity.calculateBoundingBox} lays
     * them out: along the wall from {@code (w - 1) / 2} cells left of it to {@code w / 2} right
     * ({@code facing.getCounterClockWise()}), and from {@code (h - 1) / 2} below it to {@code h / 2} above, for a
     * {@code w} x {@code h} canvas. For the castle's Fat Lady (2 x 4), wall (60, 47, 155) facing north: cell
     * (60, 47, 154), canvas x 59..60, y 46..49. For Sir Cadogan (4 x 2), wall (57, 33, 138) facing north: cell
     * (57, 33, 137), canvas x 55..58, y 33..34. The wall's face is behind every cell of either.
     */
    public static BlockPos canvasCell(BlockPos wall, Direction facing) {
        return wall.relative(facing);
    }

    /**
     * Builds, without adding or testing, a portrait of this kind for {@link #canvasCell}'s cell. Reads no blocks, so
     * it is safe on a worldgen thread; the caller decides whether it fits and adds it.
     */
    public static PortraitEntity portraitInCell(Level level, PortraitKind kind, BlockPos cell, Direction facing) {
        return new PortraitEntity(level, kind, cell, facing);
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
