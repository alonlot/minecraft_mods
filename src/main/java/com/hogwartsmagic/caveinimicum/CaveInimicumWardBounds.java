package com.hogwartsmagic.caveinimicum;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Defines the inclusive cube bounds for a Cave Inimicum ward and centralizes
 * index math so snapshots, sync, and visibility checks all agree.
 */
public record CaveInimicumWardBounds(BlockPos center, int edgeLength) {
    public static final int DEFAULT_EDGE_LENGTH = 16;

    public CaveInimicumWardBounds {
        if (edgeLength <= 0 || (edgeLength & 1) != 0) {
            throw new IllegalArgumentException("Ward edge length must be a positive even number");
        }
        center = center.immutable();
    }

    public BlockPos min() {
        int half = edgeLength / 2;
        return center.offset(-half, -half, -half);
    }

    public BlockPos max() {
        int half = edgeLength / 2;
        return center.offset(half - 1, half - 1, half - 1);
    }

    public int volume() {
        return edgeLength * edgeLength * edgeLength;
    }

    public boolean contains(BlockPos pos) {
        BlockPos min = min();
        BlockPos max = max();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean contains(Entity entity) {
        return contains(entity.blockPosition());
    }

    public int indexOf(BlockPos pos) {
        BlockPos min = min();
        return indexOf(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
    }

    public int indexOf(int localX, int localY, int localZ) {
        return localX + (localZ * edgeLength) + (localY * edgeLength * edgeLength);
    }

    public BlockPos posAtIndex(int index) {
        int layerSize = edgeLength * edgeLength;
        int localY = index / layerSize;
        int rem = index % layerSize;
        int localZ = rem / edgeLength;
        int localX = rem % edgeLength;
        BlockPos min = min();
        return min.offset(localX, localY, localZ);
    }
}
