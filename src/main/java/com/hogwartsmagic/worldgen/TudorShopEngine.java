package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Shared facade engine for Diagon Alley's shops. Encodes the techniques experienced
 * Minecraft builders use so every shop reads as hand-built rather than extruded:
 *
 * <ul>
 *   <li><b>Layered materials</b> — a noisy stone-brick base course (stone bricks / cracked /
 *       cobble / andesite, moss only at street level), plaster upper floors, timber frame.</li>
 *   <li><b>Jettied upper floors</b> — every floor above the ground floor steps one block out
 *       over the street, carried on upside-down stair brackets. Opposing jetties are what make
 *       a narrow wizard street feel like it's closing overhead.</li>
 *   <li><b>Proud timber grid</b> — corner posts, horizontal floor bands and vertical studs,
 *       so walls have a real shadow line instead of a flat plane.</li>
 *   <li><b>Steep gabled roofs</b> — 1:1 stair pitch with a one-block overhang on all sides,
 *       upside-down stair trim under the eaves, a two-tone gradient in one material family,
 *       slab ridge caps, plus dormers and brick chimneys on the taller shops.</li>
 *   <li><b>Shopfront kit</b> — big ground-floor display windows with trapdoor shutters,
 *       upper windows in the stud bays, a chain-hung shop sign and lanterns under the jetty.</li>
 * </ul>
 *
 * Only the shell is built here; each shop keeps its own interior, loot and shopkeeper.
 * All writes go through {@link DiagonAlleyFeature#setSafe}.
 */
final class TudorShopEngine {

    /** Per-shop identity: one bold color family against the shared stone/timber base. */
    static final class Style {
        final BlockState plaster;
        final BlockState plasterAlt;
        final BlockState timber;
        final BlockState band;        // horizontal belt at each floor seam
        final Block roofStair;
        final Block roofStairAlt;
        final BlockState roofSlab;
        final BlockState glass;
        final Block door;
        final Block shutter;

        Style(Block plaster, Block plasterAlt, Block timber, Block band,
              Block roofStair, Block roofStairAlt, Block roofSlab,
              Block glass, Block door, Block shutter) {
            this.plaster = plaster.defaultBlockState();
            this.plasterAlt = plasterAlt.defaultBlockState();
            this.timber = timber.defaultBlockState();
            this.band = band.defaultBlockState();
            this.roofStair = roofStair;
            this.roofStairAlt = roofStairAlt;
            this.roofSlab = roofSlab.defaultBlockState();
            this.glass = glass.defaultBlockState();
            this.door = door;
            this.shutter = shutter;
        }
    }

    private TudorShopEngine() {}

    /** Wall height (top timber band) for a shop with this many floors: ground 4 + 3 per upper. */
    static int wallHeight(int floors) {
        return 4 + 3 * (floors - 1);
    }

    /**
     * Builds the complete shell: walls, jetty, windows, door, sign, roof.
     * The interior volume is left hollow for the caller to furnish.
     * {@code front} must be NORTH or SOUTH (the street runs east-west).
     */
    static void buildShell(WorldGenLevel level, int x0, int y0, int z0, int sx, int sz,
                           Direction front, int floors, Style st) {
        int h = wallHeight(floors);
        int frontZ = (front == Direction.NORTH) ? z0 : z0 + sz - 1;
        int backZ = (front == Direction.NORTH) ? z0 + sz - 1 : z0;
        int step = (front == Direction.NORTH) ? -1 : 1;  // toward the street
        int jettyZ = frontZ + step;                       // upper front wall plane
        int doorX = x0 + sx / 2;
        RandomSource rng = RandomSource.create(x0 * 341873128712L ^ z0 * 132897987541L);

        // ---- walls ------------------------------------------------------------------
        for (int dy = 1; dy <= h; dy++) {
            boolean upper = dy >= 5;
            boolean seam = dy == 4 || (dy > 4 && (dy - 4) % 3 == 0);
            for (int dx = 0; dx < sx; dx++) {
                for (int dz = 0; dz < sz; dz++) {
                    boolean edge = dx == 0 || dx == sx - 1 || dz == 0 || dz == sz - 1;
                    if (!edge) continue;
                    boolean isFrontWall = (z0 + dz) == frontZ && dx > 0 && dx < sx - 1;
                    if (upper && isFrontWall) continue; // upper front lives on the jetty plane
                    place(level, x0 + dx, y0 + dy, z0 + dz,
                            wallBlock(st, rng, dx, dy, dz, sx, sz, seam, upper));
                }
            }
            // Jettied front plane (posts included) for upper floors.
            if (upper) {
                for (int dx = 0; dx < sx; dx++) {
                    place(level, x0 + dx, y0 + dy, jettyZ,
                            wallBlock(st, rng, dx, dy, front == Direction.NORTH ? -1 : sz, sx, sz + 1, seam, true));
                }
            }
        }

        // Jetty brackets: upside-down stairs under the overhanging lip.
        BlockState bracket = stair(st.roofStairAlt, front.getOpposite(), Half.TOP);
        for (int dx = 0; dx < sx; dx++) {
            place(level, x0 + dx, y0 + 4, jettyZ, bracket);
        }

        // ---- ground-floor shopfront --------------------------------------------------
        for (int dx = 1; dx < sx - 1; dx++) {
            if (dx >= sx / 2 - 1 && dx <= sx / 2 + 1) continue; // door bay stays solid
            place(level, x0 + dx, y0 + 2, frontZ, st.glass);
            place(level, x0 + dx, y0 + 3, frontZ, st.glass);
        }
        // Trapdoor shutters flanking the display glass, folded open against the wall.
        BlockState shutterState = st.shutter.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, front)
                .setValue(BlockStateProperties.OPEN, true);
        for (int dy = 2; dy <= 3; dy++) {
            place(level, x0 + 1, y0 + dy, frontZ + step, shutterState);
            place(level, x0 + sx - 2, y0 + dy, frontZ + step, shutterState);
        }

        // ---- upper windows in the stud bays -------------------------------------------
        for (int f = 1; f < floors; f++) {
            int wy = y0 + 5 + (f - 1) * 3;
            for (int dx = 2; dx < sx - 2; dx += 3) {
                place(level, x0 + dx, wy, jettyZ, st.glass);
                place(level, x0 + dx, wy + 1, jettyZ, st.glass);
            }
            // Side windows so the gable walls aren't blind.
            place(level, x0, wy, z0 + sz / 2, st.glass);
            place(level, x0 + sx - 1, wy, z0 + sz / 2, st.glass);
        }

        // ---- door, hanging sign, lanterns under the jetty ------------------------------
        placeDoorLocal(level, doorX, y0 + 1, frontZ, front, st.door);
        // Chain-hung shop sign and lantern flanking the door, replacing two jetty brackets
        // so they tuck under the overhang (and never collide with street lamp poles).
        int signX = doorX + 2 < x0 + sx - 1 ? doorX + 2 : doorX - 2;
        place(level, signX, y0 + 4, jettyZ, Blocks.CHAIN.defaultBlockState());
        place(level, signX, y0 + 3, jettyZ, Blocks.SPRUCE_HANGING_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, 4)
                .setValue(BlockStateProperties.ATTACHED, true));
        int lampX = doorX + 2 < x0 + sx - 1 ? doorX - 2 : doorX + 2;
        place(level, lampX, y0 + 4, jettyZ, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true));

        // ---- roof ----------------------------------------------------------------------
        int zA = Math.min(z0, jettyZ);
        int zB = Math.max(z0 + sz - 1, jettyZ);
        buildGabledRoof(level, x0, y0 + h, z0, sx, zA, zB, frontZ + step * 2, front, floors, st, rng, doorX);

        // Chimney at a back corner, poking through the slope.
        int chimX = (rng.nextBoolean() ? x0 + 1 : x0 + sx - 2);
        int chimTop = y0 + h + (zB - zA) / 2 + 2;
        for (int cy = y0 + h; cy <= chimTop; cy++) {
            place(level, chimX, cy, backZ - step, Blocks.BRICKS.defaultBlockState());
        }
        place(level, chimX, chimTop + 1, backZ - step, Blocks.BRICK_WALL.defaultBlockState());
        place(level, chimX, chimTop + 2, backZ - step, Blocks.CAMPFIRE.defaultBlockState());
    }

    /** Steep 1:1 gable with overhang, eave trim, gradient, gable-end infill and dormer. */
    private static void buildGabledRoof(WorldGenLevel level, int x0, int wallTopY, int z0, int sx,
                                        int zA, int zB, int eaveFrontZ, Direction front, int floors,
                                        Style st, RandomSource rng, int doorX) {
        int xL = x0 - 1, xR = x0 + sx; // one-block overhang on the gable ends
        // Upside-down stair trim under both eaves.
        for (int x = xL; x <= xR; x++) {
            place(level, x, wallTopY, zA - 1, stair(st.roofStairAlt, Direction.SOUTH, Half.TOP));
            place(level, x, wallTopY, zB + 1, stair(st.roofStairAlt, Direction.NORTH, Half.TOP));
        }
        int t = 0;
        while (true) {
            int zn = zA - 1 + t;
            int zs = zB + 1 - t;
            int y = wallTopY + 1 + t;
            if (zn >= zs) {
                // Odd width: slab ridge cap.
                if (zn == zs) {
                    for (int x = xL; x <= xR; x++) {
                        place(level, x, y, zn, st.roofSlab);
                    }
                }
                break;
            }
            for (int x = xL; x <= xR; x++) {
                // Two-tone gradient, clustered by a positional hash; weathered blocks low.
                boolean alt = ((x * 7 + t * 13) % 10) < (t == 0 ? 4 : 2);
                Block mat = alt ? st.roofStairAlt : st.roofStair;
                place(level, x, y, zn, stair(mat, Direction.SOUTH, Half.BOTTOM));
                place(level, x, y, zs, stair(mat, Direction.NORTH, Half.BOTTOM));
            }
            if (zs - zn == 2) {
                // One-row gap left: cap it with slabs on the next level.
                for (int x = xL; x <= xR; x++) {
                    place(level, x, y + 1, zn + 1, st.roofSlab);
                }
                break;
            }
            // Gable-end triangle infill on the wall planes.
            for (int z = zn + 1; z <= zs - 1; z++) {
                place(level, x0, y, z, z == (zA + zB) / 2 ? st.timber : st.plaster);
                place(level, x0 + sx - 1, y, z, z == (zA + zB) / 2 ? st.timber : st.plasterAlt);
            }
            t++;
        }
        // Dormer over the door on 3+ floor shops: two plaster cheeks, a pane, a slab brow.
        if (floors >= 3) {
            int dy = wallTopY + 2;
            int dz = front == Direction.NORTH ? zA - 1 : zB + 1;
            place(level, doorX - 1, dy, dz, st.plaster);
            place(level, doorX + 1, dy, dz, st.plaster);
            place(level, doorX, dy, dz, st.glass);
            place(level, doorX - 1, dy + 1, dz, st.roofSlab);
            place(level, doorX, dy + 1, dz, st.roofSlab);
            place(level, doorX + 1, dy + 1, dz, st.roofSlab);
        }
    }

    /** Wall material chooser: stone-course ground floor, timbered plaster above. */
    private static BlockState wallBlock(Style st, RandomSource rng, int dx, int dy, int dz,
                                        int sx, int sz, boolean seam, boolean upper) {
        boolean cornerPost = (dx == 0 || dx == sx - 1);
        if (seam) return st.band;
        if (!upper) {
            // Ground floor: noisy stone base course; moss hugs street level only.
            int roll = rng.nextInt(100);
            if (dy == 1 && roll < 12) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            if (roll < 45) return Blocks.STONE_BRICKS.defaultBlockState();
            if (roll < 65) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            if (roll < 80) return Blocks.COBBLESTONE.defaultBlockState();
            if (roll < 90) return Blocks.ANDESITE.defaultBlockState();
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        // Upper floors: corner posts + a stud every 3 blocks, plaster infill in clustered tones.
        if (cornerPost || dx % 3 == 0) return st.timber;
        boolean altPatch = ((dx / 2 + dy / 2) & 1) == 0 && rng.nextInt(4) == 0;
        return altPatch ? st.plasterAlt : st.plaster;
    }

    private static BlockState stair(Block block, Direction facing, Half half) {
        return block.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half);
    }

    private static void placeDoorLocal(WorldGenLevel level, int x, int y, int z, Direction outward, Block doorBlock) {
        BlockState lower = doorBlock.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, outward)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        BlockState upper = lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        place(level, x, y, z, lower);
        place(level, x, y + 1, z, upper);
    }

    private static void place(WorldGenLevel level, int x, int y, int z, BlockState state) {
        DiagonAlleyFeature.setSafe(level, new BlockPos(x, y, z), state, 2);
    }
}
