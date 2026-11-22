package com.hogwartsmagic.block.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Shape helpers for decorations. Every decoration shape is written as if the block faces NORTH
 * (its front looks toward negative Z, a wall-mounted piece hangs on the south face of its cell),
 * then turned with the same clockwise rotation the blockstate files use (east 90, south 180, west 270).
 */
public final class DecorShapes {
    private DecorShapes() {
    }

    /** Union of boxes given in pixels: {x1, y1, z1, x2, y2, z2} each. */
    public static VoxelShape boxes(double[]... boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            shape = Shapes.or(shape, Shapes.box(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16));
        }
        return shape.optimize();
    }

    /** Turns a NORTH-facing shape clockwise (seen from above) to face the given horizontal direction. */
    public static VoxelShape rotate(VoxelShape north, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape shape = north;
        for (int i = 0; i < turns; i++) {
            VoxelShape[] out = {Shapes.empty()};
            // One clockwise quarter turn about the cell centre: (x, z) -> (1 - z, x).
            shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                    out[0] = Shapes.or(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            shape = out[0];
        }
        return shape.optimize();
    }

    public static Map<Direction, VoxelShape> allHorizontal(VoxelShape north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            map.put(d, rotate(north, d));
        }
        return map;
    }
}
