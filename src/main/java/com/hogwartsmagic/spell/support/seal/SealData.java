package com.hogwartsmagic.spell.support.seal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The Colloportus seals of one dimension, oldest first, saved with the world. Seals are few (at most 8 per player),
 * so this plain list is the index: a lookup is one integer distance test per seal. Server thread only.
 */
public final class SealData extends SavedData {
    public static final String DATA_NAME = "hogwartsmagic_colloportus_seals";

    private final List<Seal> seals = new ArrayList<>();

    public static SealData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(SealData::new, SealData::load, null), DATA_NAME);
    }

    public boolean isEmpty() {
        return seals.isEmpty();
    }

    /** Every seal in this dimension, oldest first (read only). */
    public List<Seal> seals() {
        return Collections.unmodifiableList(seals);
    }

    /** The seals that cover at least one of positions, oldest first. */
    public List<Seal> covering(Collection<BlockPos> positions) {
        List<Seal> found = new ArrayList<>();
        for (Seal seal : seals) {
            for (BlockPos pos : positions) {
                if (SealRules.covers(seal, pos.getX(), pos.getY(), pos.getZ())) {
                    found.add(seal);
                    break;
                }
            }
        }
        return found;
    }

    public List<Seal> ownedBy(UUID owner) {
        List<Seal> found = new ArrayList<>();
        for (Seal seal : seals) {
            if (seal.owner().equals(owner)) {
                found.add(seal);
            }
        }
        return found;
    }

    public void add(Seal seal) {
        seals.add(seal);
        setDirty();
    }

    public void remove(Seal seal) {
        if (seals.remove(seal)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Seal seal : seals) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Owner", seal.owner());
            one.putString("Name", seal.ownerName());
            one.putInt("Rank", seal.rank());
            one.putInt("X", seal.x());
            one.putInt("Y", seal.y());
            one.putInt("Z", seal.z());
            one.putInt("R", seal.radius());
            one.putLong("At", seal.createdAt());
            list.add(one);
        }
        tag.put("Seals", list);
        return tag;
    }

    public static SealData load(CompoundTag tag, HolderLookup.Provider registries) {
        SealData data = new SealData();
        ListTag list = tag.getList("Seals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            if (!one.hasUUID("Owner")) {
                continue;
            }
            int rank = Math.max(1, Math.min(4, one.getInt("Rank")));
            int radius = Math.max(1, Math.min(SealRules.MAX_RADIUS, one.getInt("R")));
            data.seals.add(new Seal(one.getUUID("Owner"), one.getString("Name"), rank,
                    one.getInt("X"), one.getInt("Y"), one.getInt("Z"), radius, one.getLong("At")));
        }
        return data;
    }
}
