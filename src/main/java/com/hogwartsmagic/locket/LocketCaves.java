package com.hogwartsmagic.locket;

import com.hogwartsmagic.block.ConcealedRockBlock;
import com.hogwartsmagic.block.entity.CaveMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** World-facing helpers for the Locket cave (the pure rules live beside this class). Server only. */
public final class LocketCaves {
    public static final int REVEAL_RADIUS = 48;
    private static final int REVEAL_TICKS = 1200;

    private LocketCaves() {
    }

    /** Reveals Concealed Rock and hidden chains near center. Only walks loaded chunk block entities. */
    public static int reveal(ServerLevel level, Vec3 center, int radius) {
        int count = 0;
        double r2 = (double) radius * radius;
        int minCx = SectionPos.blockToSectionCoord(center.x - radius);
        int maxCx = SectionPos.blockToSectionCoord(center.x + radius);
        int minCz = SectionPos.blockToSectionCoord(center.z - radius);
        int maxCz = SectionPos.blockToSectionCoord(center.z + radius);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (!(be instanceof CaveMarkerBlockEntity)) {
                        continue;
                    }
                    BlockPos p = be.getBlockPos();
                    if (p.distToCenterSqr(center) > r2) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (!s.hasProperty(ConcealedRockBlock.REVEALED) || s.getValue(ConcealedRockBlock.REVEALED)) {
                        continue;
                    }
                    level.setBlock(p, s.setValue(ConcealedRockBlock.REVEALED, true), Block.UPDATE_ALL);
                    level.scheduleTick(p, s.getBlock(), REVEAL_TICKS);
                    count++;
                }
            }
        }
        return count;
    }
}
