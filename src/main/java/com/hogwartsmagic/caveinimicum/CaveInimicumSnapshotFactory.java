package com.hogwartsmagic.caveinimicum;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * Captures the ward's frozen visual state from the live world the moment the
 * spell is cast.
 */
public final class CaveInimicumSnapshotFactory {
    private CaveInimicumSnapshotFactory() {
    }

    public static CaveInimicumSnapshot capture(ServerLevel level, BlockPos center, int edgeLength) {
        CaveInimicumWardBounds bounds = new CaveInimicumWardBounds(center, edgeLength);
        int[] stateIds = new int[bounds.volume()];
        for (int i = 0; i < stateIds.length; i++) {
            stateIds[i] = Block.getId(level.getBlockState(bounds.posAtIndex(i)));
        }
        return new CaveInimicumSnapshot(bounds, stateIds);
    }
}
