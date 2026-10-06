package com.hogwartsmagic.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Persistent set of Locket cave start chunks (CaveSearch.key) whose Locket has been taken. Overworld only. */
public class LootedCaveTracker extends SavedData {
    public static final String DATA_NAME = "locket_caves_looted";

    private final Set<Long> looted = new HashSet<>();

    public static LootedCaveTracker get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(LootedCaveTracker::new, LootedCaveTracker::load, null), DATA_NAME);
    }

    public void markLooted(long chunkKey) {
        if (looted.add(chunkKey)) {
            setDirty();
        }
    }

    public boolean isLooted(long chunkKey) {
        return looted.contains(chunkKey);
    }

    public Set<Long> looted() {
        return Collections.unmodifiableSet(looted);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLongArray("Looted", looted.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public static LootedCaveTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        LootedCaveTracker tracker = new LootedCaveTracker();
        for (long k : tag.getLongArray("Looted")) {
            tracker.looted.add(k);
        }
        return tracker;
    }
}
