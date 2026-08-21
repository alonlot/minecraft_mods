package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The shaft between the bank's rear rotunda and the cart station below it.
 *
 * <p>The rotunda already has the shape this wants: a checkerboard floor ring around a void annulus,
 * with a small island in the middle reached by a short causeway, and nothing at all beneath. So the
 * shaft is a hole through that island, and the ring void above it is left alone - standing on the
 * causeway you look past the plinth and straight down the shaft, which is the first of the
 * sight-lines the reference spec asks for.
 *
 * <p>The stair is a helix around the shaft wall rather than a straight flight, for two reasons: a
 * straight flight down 26 blocks needs a 26-block run and the rotunda is 12 across, and a helix
 * leaves the middle of the shaft open so there is something to look down.
 */
public final class StationCarver {

    /** Interior half-width of the shaft. 2 gives a 5x5 bore, which fits the island and its ring. */
    private static final int HALF = 2;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STAIR = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HALF, Half.BOTTOM);
    private static final BlockState LANDING = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState LAMP = Blocks.LANTERN.defaultBlockState()
            .setValue(BlockStateProperties.HANGING, Boolean.TRUE);

    /** The 16 perimeter cells of a 5x5 ring, walked in order, so a step lands on each in turn. */
    private static final int[][] RING = {
            {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2},
            {2, -1}, {2, 0}, {2, 1}, {2, 2},
            {1, 2}, {0, 2}, {-1, 2}, {-2, 2},
            {-2, 1}, {-2, 0}, {-2, -1},
    };

    private final BlockPos top;
    private final int bottomY;
    private final UndercroftPalette palette;

    public StationCarver(BlockPos top, int bottomY, UndercroftPalette palette) {
        this.top = top;
        this.bottomY = bottomY;
        this.palette = palette;
    }

    /** One step per Y level of the shaft. */
    public int stepCount() {
        return Math.max(1, this.top.getY() - this.bottomY + 1);
    }

    public void carveStep(CarveSink sink, int step) {
        int y = this.top.getY() - step;
        if (y < this.bottomY) {
            return;
        }
        int cx = this.top.getX();
        int cz = this.top.getZ();

        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                boolean interior = Math.abs(dx) <= HALF && Math.abs(dz) <= HALF;
                if (interior) {
                    sink.set(cx + dx, y, cz + dz, AIR);
                } else {
                    // Line the shaft, so it does not open into whatever the bank is standing on.
                    sink.set(cx + dx, y, cz + dz, this.palette.rock(cx + dx, y, cz + dz));
                }
            }
        }

        // The helix: one tread per level, walking the ring. Facing is set so the tread rises the
        // way you are travelling, which is what makes it climbable rather than a pile of slabs.
        int[] cell = RING[step % RING.length];
        int[] next = RING[(step + 1) % RING.length];
        Direction facing = towards(cell, next);
        sink.set(cx + cell[0], y, cz + cell[1],
                STAIR.setValue(BlockStateProperties.HORIZONTAL_FACING, facing));

        // A lantern every eighth level, on the wall, so the shaft has a rhythm rather than a glow.
        if (step % 8 == 3) {
            sink.set(cx + cell[0], y + 2, cz + cell[1], LAMP);
        }

        if (y == this.bottomY) {
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    sink.set(cx + dx, y, cz + dz, LANDING);
                }
            }
        }
    }

    private static Direction towards(int[] from, int[] to) {
        int dx = to[0] - from[0];
        int dz = to[1] - from[1];
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Unused today, but the lift replaces this block when it lands. */
    public static boolean isStairBlock(BlockState state) {
        return state.getBlock() instanceof StairBlock;
    }
}
