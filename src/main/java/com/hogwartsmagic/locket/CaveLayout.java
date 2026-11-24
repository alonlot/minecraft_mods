package com.hogwartsmagic.locket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where one cave's parts are, in world coordinates, read from the waypoint markers the template carries
 * (they turn with the template, so the cave works at any rotation). Version 1 is a cave from before the
 * markers existed and answers exactly as {@link CaveGeometry} does; version 2 answers from the markers.
 * Pure: water is passed in, never looked up.
 */
public record CaveLayout(int version, BlockPos basin, List<BlockPos> arches, BlockPos ring, BlockPos dockShore,
                         BlockPos dockIsland, List<BlockPos> path, int lakeSurfaceY, int radius, int down, int up) {
    public record Marker(String role, int index, BlockPos pos) {}

    /** How far past the outermost marker the cave still counts as the cave. */
    private static final int MARGIN = 12;
    private static final int ISLAND_PAD = 2;
    private static final int ISLAND_DY = 4;

    public CaveLayout {
        arches = List.copyOf(arches);
        path = List.copyOf(path);
    }

    public static CaveLayout legacy(BlockPos basin) {
        return new CaveLayout(1, basin, List.of(), basin, basin, basin, List.of(), basin.getY() - 1,
                CaveGeometry.CAVE_RADIUS, CaveGeometry.CAVE_DOWN, CaveGeometry.CAVE_UP);
    }

    public static CaveLayout fromMarkers(BlockPos basin, List<Marker> markers) {
        if (markers.isEmpty()) {
            return legacy(basin);
        }
        List<Marker> sorted = new ArrayList<>(markers);
        sorted.sort(Comparator.comparingInt(Marker::index));
        List<BlockPos> arches = new ArrayList<>();
        List<BlockPos> path = new ArrayList<>();
        BlockPos ring = basin;
        BlockPos dockShore = basin;
        BlockPos dockIsland = basin;
        Integer lakeY = null;
        int radius = 0;
        int down = 0;
        int up = 0;
        for (Marker m : sorted) {
            BlockPos p = m.pos();
            switch (m.role()) {
                case "arch" -> arches.add(p);
                case "ring" -> ring = p;
                case "dock_shore" -> dockShore = p;
                case "dock_island" -> dockIsland = p;
                case "path" -> path.add(p);
                case "lake" -> lakeY = p.getY() - 1;
                default -> { }
            }
            radius = Math.max(radius, Math.max(Math.abs(p.getX() - basin.getX()), Math.abs(p.getZ() - basin.getZ())));
            down = Math.max(down, basin.getY() - p.getY());
            up = Math.max(up, p.getY() - basin.getY());
        }
        if (lakeY == null) {
            lakeY = dockShore != basin ? dockShore.getY() - 1 : basin.getY() - 1;
        }
        return new CaveLayout(2, basin, arches, ring, dockShore, dockIsland, path, lakeY,
                radius + MARGIN, down + MARGIN, up + MARGIN);
    }

    /** Horizontal reach of the island around the basin. */
    public int islandRadius() {
        if (version == 1) {
            return CaveGeometry.ISLAND_RADIUS;
        }
        return (int) Math.ceil(horizontalDistance(dockIsland, basin)) + ISLAND_PAD;
    }

    public boolean inCave(BlockPos p) {
        int dx = p.getX() - basin.getX();
        int dy = p.getY() - basin.getY();
        int dz = p.getZ() - basin.getZ();
        if (version == 1) {
            return CaveGeometry.inCave(dx, dy, dz);
        }
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius && dy >= -down && dy <= up;
    }

    public boolean onIsland(BlockPos p) {
        int dx = p.getX() - basin.getX();
        int dy = p.getY() - basin.getY();
        int dz = p.getZ() - basin.getZ();
        if (version == 1) {
            return CaveGeometry.onIsland(dx, dy, dz);
        }
        int r = islandRadius();
        return dx * dx + dz * dz <= r * r && Math.abs(dy) <= ISLAND_DY;
    }

    /** The black lake proper: water at or below the lake surface inside the cave bounds (version 2). */
    public boolean inLake(BlockPos p, boolean isWater) {
        if (version == 1) {
            return CaveGeometry.inLake(p.getX() - basin.getX(), p.getY() - basin.getY(), p.getZ() - basin.getZ());
        }
        return isWater && p.getY() <= lakeSurfaceY && inCave(p);
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Version", version);
        t.putInt("LakeY", lakeSurfaceY);
        t.putInt("Radius", radius);
        t.putInt("Down", down);
        t.putInt("Up", up);
        t.putLong("Ring", ring.asLong());
        t.putLong("DockShore", dockShore.asLong());
        t.putLong("DockIsland", dockIsland.asLong());
        t.putLongArray("Arches", arches.stream().mapToLong(BlockPos::asLong).toArray());
        t.putLongArray("Path", path.stream().mapToLong(BlockPos::asLong).toArray());
        return t;
    }

    public static CaveLayout load(CompoundTag t, BlockPos basin) {
        if (t.getInt("Version") < 2) {
            return legacy(basin);
        }
        List<BlockPos> arches = new ArrayList<>();
        for (long l : t.getLongArray("Arches")) {
            arches.add(BlockPos.of(l));
        }
        List<BlockPos> path = new ArrayList<>();
        for (long l : t.getLongArray("Path")) {
            path.add(BlockPos.of(l));
        }
        return new CaveLayout(t.getInt("Version"), basin, arches, BlockPos.of(t.getLong("Ring")),
                BlockPos.of(t.getLong("DockShore")), BlockPos.of(t.getLong("DockIsland")), path,
                t.getInt("LakeY"), t.getInt("Radius"), t.getInt("Down"), t.getInt("Up"));
    }
}
