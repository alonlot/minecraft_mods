package com.hogwartsmagic.locket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaveLayoutTest {
    static final BlockPos BASIN = new BlockPos(1000, 40, -500);

    /** Offsets from the basin: lake surface at dy -1 (so the lake marker is at dy 0). */
    static List<CaveLayout.Marker> markers(int rot) {
        List<CaveLayout.Marker> m = new ArrayList<>();
        m.add(new CaveLayout.Marker("basin", 0, at(0, 2, 0, rot)));
        m.add(new CaveLayout.Marker("arch", 0, at(-3, 10, -38, rot)));
        m.add(new CaveLayout.Marker("ring", 0, at(-5, 4, -30, rot)));
        m.add(new CaveLayout.Marker("dock_shore", 0, at(-6, 0, -26, rot)));
        m.add(new CaveLayout.Marker("path", 2, at(4, 0, -9, rot)));
        m.add(new CaveLayout.Marker("path", 0, at(-4, 0, -20, rot)));
        m.add(new CaveLayout.Marker("path", 1, at(0, 0, -14, rot)));
        m.add(new CaveLayout.Marker("dock_island", 0, at(0, 0, -8, rot)));
        m.add(new CaveLayout.Marker("lake", 0, at(10, 0, 5, rot)));
        return m;
    }

    static BlockPos at(int dx, int dy, int dz, int rot) {
        int x = dx, z = dz;
        for (int i = 0; i < rot; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return BASIN.offset(x, dy, z);
    }

    @Test void noMarkersMeansLegacyLayout() {
        CaveLayout l = CaveLayout.fromMarkers(BASIN, List.of());
        assertEquals(1, l.version());
        for (int dx = -40; dx <= 40; dx += 3) {
            for (int dy = -20; dy <= 30; dy += 2) {
                for (int dz = -40; dz <= 40; dz += 3) {
                    BlockPos p = BASIN.offset(dx, dy, dz);
                    assertEquals(CaveGeometry.inCave(dx, dy, dz), l.inCave(p));
                    assertEquals(CaveGeometry.onIsland(dx, dy, dz), l.onIsland(p));
                    assertEquals(CaveGeometry.inLake(dx, dy, dz), l.inLake(p, true));
                }
            }
        }
        assertEquals(1, CaveLayout.legacy(BASIN).version());
    }

    @Test void markersGiveVersionTwo() {
        CaveLayout l = CaveLayout.fromMarkers(BASIN, markers(0));
        assertEquals(2, l.version());
        assertEquals(3, l.path().size());
        assertEquals(BASIN.offset(-4, 0, -20), l.path().get(0));
        assertEquals(BASIN.offset(0, 0, -14), l.path().get(1));
        assertEquals(BASIN.offset(4, 0, -9), l.path().get(2));
        assertEquals(BASIN.getY() - 1, l.lakeSurfaceY());
        assertEquals(1, l.arches().size());
        assertEquals(BASIN.offset(-5, 4, -30), l.ring());
        assertEquals(BASIN.offset(0, 0, -8), l.dockIsland());
        assertEquals(BASIN.offset(-6, 0, -26), l.dockShore());
        assertEquals(BASIN, l.basin());
    }

    @Test void rotatedMarkersStillDescribeTheSameCave() {
        CaveLayout base = CaveLayout.fromMarkers(BASIN, markers(0));
        int[][] probes = {{0, 0, 0}, {3, 0, 4}, {9, 0, 0}, {0, 0, -9}, {6, 1, -3}, {0, 6, 0}, {10, -3, 5}, {10, 3, 5},
                {-20, -4, -20}, {0, -2, -28}, {45, 0, 0}, {5, 0, -9}};
        for (int rot = 1; rot <= 3; rot++) {
            CaveLayout r = CaveLayout.fromMarkers(BASIN, markers(rot));
            assertEquals(2, r.version());
            assertEquals(at(0, 0, -8, rot), r.dockIsland());
            for (int[] q : probes) {
                BlockPos plain = at(q[0], q[1], q[2], 0);
                BlockPos turned = at(q[0], q[1], q[2], rot);
                assertEquals(base.onIsland(plain), r.onIsland(turned), "island " + rot + " " + java.util.Arrays.toString(q));
                assertEquals(base.inCave(plain), r.inCave(turned), "cave " + rot + " " + java.util.Arrays.toString(q));
                assertEquals(base.inLake(plain, true), r.inLake(turned, true), "lake " + rot + " " + java.util.Arrays.toString(q));
            }
            assertTrue(r.onIsland(at(0, 0, -6, rot)));
            assertFalse(r.onIsland(at(0, 0, -9, rot)));
            assertFalse(r.onIsland(at(0, 0, -12, rot)));
        }
        assertTrue(base.onIsland(BASIN.offset(0, 0, -6)));
        assertEquals(6, base.islandRadius());
        assertFalse(base.onIsland(BASIN.offset(0, 5, 0)));
    }

    @Test void inLakeNeedsWaterAtOrBelowSurface() {
        CaveLayout l = CaveLayout.fromMarkers(BASIN, markers(0));
        BlockPos below = BASIN.offset(8, -5, 8);
        assertTrue(l.inLake(below, true));
        assertFalse(l.inLake(below, false));
        assertTrue(l.inLake(new BlockPos(below.getX(), l.lakeSurfaceY(), below.getZ()), true));
        assertFalse(l.inLake(new BlockPos(below.getX(), l.lakeSurfaceY() + 1, below.getZ()), true));
        assertFalse(l.inLake(BASIN.offset(500, -5, 0), true)); // outside the cave
        assertTrue(l.inLake(BASIN.offset(-2, -3, -17), true)); // between the docks
        assertFalse(l.inLake(BASIN.offset(36, -3, 0), true)); // open sea, same level, inside the cave box
        assertFalse(l.inLake(BASIN.offset(0, -3, -38), true));
        assertFalse(l.inLake(BASIN.offset(8, -10, 8), true)); // below the lake floor
        assertTrue(l.inLake(BASIN.offset(8, -9, 8), true));
    }

    @Test void saveLoadRoundTrip() {
        for (CaveLayout l : List.of(CaveLayout.fromMarkers(BASIN, markers(1)), CaveLayout.legacy(BASIN))) {
            CompoundTag tag = l.save();
            assertEquals(l, CaveLayout.load(tag, BASIN));
        }
    }
}
