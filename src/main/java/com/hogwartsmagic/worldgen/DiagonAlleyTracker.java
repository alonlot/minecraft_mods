package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent per-dimension list of Diagon Alley centers. The feature appends a position
 * each time it generates one; the /findalley command queries the nearest entry.
 */
public class DiagonAlleyTracker extends SavedData {
    public static final String DATA_NAME = "diagon_alleys";

    private final List<BlockPos> alleys = new ArrayList<>();

    public static DiagonAlleyTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(DiagonAlleyTracker::new, DiagonAlleyTracker::load, null),
                DATA_NAME
        );
    }

    public void add(BlockPos pos) {
        // Deduplicate: don't store positions within 64 blocks of an existing one
        // (worldgen may emit overlapping calls for the same feature center).
        for (BlockPos existing : alleys) {
            if (existing.distSqr(pos) < 64.0 * 64.0) return;
        }
        alleys.add(pos.immutable());
        setDirty();
    }

    public List<BlockPos> getAll() {
        return alleys;
    }

    /** Returns the position with smallest squared XZ distance, or null if empty. */
    public BlockPos findClosest(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : alleys) {
            double dx = p.getX() - from.getX();
            double dz = p.getZ() - from.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (BlockPos p : alleys) {
            list.add(LongTag.valueOf(p.asLong()));
        }
        tag.put("Alleys", list);
        return tag;
    }

    public static DiagonAlleyTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        DiagonAlleyTracker tracker = new DiagonAlleyTracker();
        ListTag list = tag.getList("Alleys", net.minecraft.nbt.Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            tracker.alleys.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
        return tracker;
    }
}
