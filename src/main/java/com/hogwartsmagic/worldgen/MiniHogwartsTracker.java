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
 * Persistent per-dimension list of placed Mini Hogwarts castles. The manager appends a position
 * each time one is built; {@code /findminihogwarts} queries the nearest entry. Mirrors
 * {@link DiagonAlleyTracker}.
 */
public class MiniHogwartsTracker extends SavedData {
    public static final String DATA_NAME = "mini_hogwarts_castles";

    private final List<BlockPos> castles = new ArrayList<>();

    public static MiniHogwartsTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(MiniHogwartsTracker::new, MiniHogwartsTracker::load, null),
                DATA_NAME
        );
    }

    public void add(BlockPos pos) {
        // Deduplicate within 128 blocks (overlapping triggers for the same castle).
        for (BlockPos existing : castles) {
            if (existing.distSqr(pos) < 128.0 * 128.0) {
                return;
            }
        }
        castles.add(pos.immutable());
        setDirty();
    }

    public List<BlockPos> getAll() {
        return castles;
    }

    /** Returns the position with smallest squared XZ distance, or null if empty. */
    public BlockPos findClosest(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : castles) {
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
        for (BlockPos p : castles) {
            list.add(LongTag.valueOf(p.asLong()));
        }
        tag.put("Castles", list);
        return tag;
    }

    public static MiniHogwartsTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        MiniHogwartsTracker tracker = new MiniHogwartsTracker();
        ListTag list = tag.getList("Castles", net.minecraft.nbt.Tag.TAG_LONG);
        for (int i = 0; i < list.size(); i++) {
            tracker.castles.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
        return tracker;
    }
}
