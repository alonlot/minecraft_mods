package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the undercroft is made of, as a function of how deep you are.
 *
 * <p>The contrast lives in <em>bands</em>, never between neighbouring blocks. Mixing blocks 60 luma
 * apart at one-block frequency is not texture, it is a chessboard; within a band everything stays
 * inside roughly a 40 luma spread and one hue family. Across bands the change is large and slow, so
 * the stone tells you how far down you are without a coordinate readout - which is most of what
 * stops a long descent reading as one endless tube.
 *
 * <p>The bands also carry the story the reference spec asks for: dressed masonry at the top, where
 * the bank's own stonework is still running out, and raw unlit rock at the bottom.
 */
public final class UndercroftPalette {

    /** {@code {block, weight}} pairs, resolved by a position hash. */
    private static final BlockState[] TRANSITION = weighted(
            Blocks.STONE_BRICKS.defaultBlockState(), 12,
            Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), 6,
            Blocks.ANDESITE.defaultBlockState(), 6,
            Blocks.POLISHED_ANDESITE.defaultBlockState(), 4,
            Blocks.TUFF.defaultBlockState(), 3,
            Blocks.GRANITE.defaultBlockState(), 2);

    private static final BlockState[] UPPER = weighted(
            Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 16,
            Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), 6,
            Blocks.DEEPSLATE_TILES.defaultBlockState(), 6,
            Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 4,
            Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);

    // Weights are scaled up rather than kept small so the ore can be genuinely rare. At one part in
    // sixteen a gold seam is a rash; at one in fifty it is a glint you notice on the third pass,
    // and it is worth having because you are boring through the rock a goblin bank was dug into.
    private static final BlockState[] MIDDLE = weighted(
            Blocks.DEEPSLATE.defaultBlockState(), 18,
            Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 9,
            Blocks.BLACKSTONE.defaultBlockState(), 12,
            Blocks.BASALT.defaultBlockState(), 6,
            Blocks.GILDED_BLACKSTONE.defaultBlockState(), 4,
            Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), 1);

    private static final BlockState[] DEEP = weighted(
            Blocks.BLACKSTONE.defaultBlockState(), 21,
            Blocks.BASALT.defaultBlockState(), 9,
            Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 6,
            Blocks.GILDED_BLACKSTONE.defaultBlockState(), 2,
            Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), 1);

    /** The cart deck. Dark, flat and quiet, so the rails on top of it read. */
    public static final BlockState DECK = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

    private final int stationY;

    public UndercroftPalette(int stationY) {
        this.stationY = stationY;
    }

    /** Rock for a cell, blended within its depth band. */
    public BlockState rock(int x, int y, int z) {
        int below = this.stationY - y;
        BlockState[] band = below < 20 ? TRANSITION
                : below < 34 ? UPPER
                : below < 52 ? MIDDLE
                : DEEP;
        int pick = (int) (hash(x, y, z, 17) * band.length);
        return band[Math.min(pick, band.length - 1)];
    }

    /**
     * Deterministic 0..1 from a position. The carve must produce the same undercroft every time it
     * runs - it is resumed from a cursor after a restart, and the client rebuilds the same line -
     * so there is no RNG anywhere in here.
     */
    public static double hash(int x, int y, int z, int salt) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (int) (salt * 2654435761L);
        h &= 0x7FFFFFFF;
        h = (h ^ (h >>> 13)) * 1274126177;
        h &= 0x7FFFFFFF;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0D;
    }

    /** Expands {block, weight, block, weight, ...} into a flat pick table. */
    private static BlockState[] weighted(Object... pairs) {
        int total = 0;
        for (int i = 1; i < pairs.length; i += 2) {
            total += (Integer) pairs[i];
        }
        BlockState[] table = new BlockState[total];
        int at = 0;
        for (int i = 0; i < pairs.length; i += 2) {
            BlockState state = (BlockState) pairs[i];
            int weight = (Integer) pairs[i + 1];
            for (int n = 0; n < weight; n++) {
                table[at++] = state;
            }
        }
        return table;
    }
}
