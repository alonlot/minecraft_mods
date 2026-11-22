package com.hogwartsmagic.worldgen.mechanism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * What each Mini Hogwarts castle's moving parts are doing, so a restart finds them where they were: per castle (its
 * schematic anchor), whether the Headmaster's stair is up and which config each moving flight is in. A part records its
 * <em>target</em> and a busy flag the moment it starts to move; if the server stops mid-move, the next start writes the
 * target's blocks at once (the animation's own blocks were taken away while it ran).
 */
public final class MechanismData extends SavedData {
    public static final String DATA_NAME = "hogwartsmagic_castle_mechanisms";

    private final Map<Long, CompoundTag> castles = new HashMap<>();

    public static MechanismData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(MechanismData::new, MechanismData::load, null), DATA_NAME);
    }

    private CompoundTag castle(BlockPos anchor) {
        return castles.computeIfAbsent(anchor.asLong(), k -> new CompoundTag());
    }

    /** 1 when the stair is (or is going) up, 0 when down. */
    public int stair(BlockPos anchor) {
        return castle(anchor).getInt("stair");
    }

    public boolean stairBusy(BlockPos anchor) {
        return castle(anchor).getBoolean("stair_busy");
    }

    public void setStair(BlockPos anchor, int target, boolean busy) {
        CompoundTag t = castle(anchor);
        t.putInt("stair", target);
        t.putBoolean("stair_busy", busy);
        setDirty();
    }

    /** The config moving flight {@code index} is in (or going to). */
    public int flight(BlockPos anchor, int index) {
        return castle(anchor).getInt("flight" + index);
    }

    public boolean flightBusy(BlockPos anchor, int index) {
        return castle(anchor).getBoolean("flight" + index + "_busy");
    }

    public void setFlight(BlockPos anchor, int index, int target, boolean busy) {
        CompoundTag t = castle(anchor);
        t.putInt("flight" + index, target);
        t.putBoolean("flight" + index + "_busy", busy);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<Long, CompoundTag> e : castles.entrySet()) {
            all.put(Long.toString(e.getKey()), e.getValue().copy());
        }
        tag.put("castles", all);
        return tag;
    }

    public static MechanismData load(CompoundTag tag, HolderLookup.Provider provider) {
        MechanismData data = new MechanismData();
        CompoundTag all = tag.getCompound("castles");
        for (String key : all.getAllKeys()) {
            try {
                data.castles.put(Long.parseLong(key), all.getCompound(key).copy());
            } catch (NumberFormatException ignored) {
                // a hand-edited key: drop it
            }
        }
        return data;
    }
}
