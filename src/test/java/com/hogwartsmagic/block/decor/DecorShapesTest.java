package com.hogwartsmagic.block.decor;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The hit box must turn the same way as the blockstate turns the model: y = 90 is a clockwise
 * quarter turn seen from above, so a piece hanging on the south side of its cell (facing north)
 * hangs on the west side when it faces east.
 */
class DecorShapesTest {
    private static final double PX = 1.0 / 16.0;

    private static AABB only(VoxelShape shape) {
        List<AABB> boxes = shape.toAabbs();
        assertEquals(1, boxes.size());
        return boxes.get(0);
    }

    private static void assertBox(AABB box, double x1, double y1, double z1, double x2, double y2, double z2) {
        double e = 1e-9;
        assertEquals(x1 * PX, box.minX, e);
        assertEquals(y1 * PX, box.minY, e);
        assertEquals(z1 * PX, box.minZ, e);
        assertEquals(x2 * PX, box.maxX, e);
        assertEquals(y2 * PX, box.maxY, e);
        assertEquals(z2 * PX, box.maxZ, e);
    }

    private static final VoxelShape WALL = DecorShapes.boxes(new double[]{1, 0, 14, 15, 16, 16});

    @Test
    void northIsUnchanged() {
        assertBox(only(DecorShapes.rotate(WALL, Direction.NORTH)), 1, 0, 14, 15, 16, 16);
    }

    @Test
    void eastHangsOnTheWestSide() {
        assertBox(only(DecorShapes.rotate(WALL, Direction.EAST)), 0, 0, 1, 2, 16, 15);
    }

    @Test
    void southHangsOnTheNorthSide() {
        assertBox(only(DecorShapes.rotate(WALL, Direction.SOUTH)), 1, 0, 0, 15, 16, 2);
    }

    @Test
    void westHangsOnTheEastSide() {
        assertBox(only(DecorShapes.rotate(WALL, Direction.WEST)), 14, 0, 1, 16, 16, 15);
    }

    @Test
    void offCentreBoxTurnsAroundTheCellCentre() {
        // A box in the north-west corner goes to the north-east corner after one clockwise turn.
        VoxelShape corner = DecorShapes.boxes(new double[]{0, 0, 0, 4, 8, 2});
        assertBox(only(DecorShapes.rotate(corner, Direction.EAST)), 14, 0, 0, 16, 8, 4);
    }
}
