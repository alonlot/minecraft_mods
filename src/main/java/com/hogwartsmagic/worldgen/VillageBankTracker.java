package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent per-dimension record of villages that have already been considered
 * for a Gringotts branch, and where the branches ended up.
 *
 * <p>Two lists rather than one: {@code visited} remembers every village we have
 * judged (including the ones that did not get a bank) so a village is never
 * re-rolled, and {@code banks} is what {@code /findgringotts} searches. Mirrors
 * {@link MiniHogwartsTracker}.
 */
public class VillageBankTracker extends SavedData {
    public static final String DATA_NAME = "gringotts_village_banks";

    /** Villages within this distance of a recorded one are treated as the same village. */
    private static final double SAME_VILLAGE_DISTANCE = 96.0D;

    private final List<BlockPos> visited = new ArrayList<>();
    private final List<BlockPos> banks = new ArrayList<>();

    public static VillageBankTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(VillageBankTracker::new, VillageBankTracker::load, null),
                DATA_NAME
        );
    }

    /** True if this village has already been judged, whether or not it got a bank. */
    public boolean hasVisited(BlockPos villageCenter) {
        return contains(this.visited, villageCenter);
    }

    public void markVisited(BlockPos villageCenter) {
        if (contains(this.visited, villageCenter)) {
            return;
        }
        this.visited.add(villageCenter.immutable());
        setDirty();
    }

    public void addBank(BlockPos pos) {
        if (contains(this.banks, pos)) {
            return;
        }
        this.banks.add(pos.immutable());
        setDirty();
    }

    public List<BlockPos> getBanks() {
        return this.banks;
    }

    /** Returns the recorded bank with the smallest squared XZ distance, or null. */
    public BlockPos findClosest(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : this.banks) {
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

    private static boolean contains(List<BlockPos> list, BlockPos pos) {
        for (BlockPos existing : list) {
            double dx = existing.getX() - pos.getX();
            double dz = existing.getZ() - pos.getZ();
            if (dx * dx + dz * dz < SAME_VILLAGE_DISTANCE * SAME_VILLAGE_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("Visited", toList(this.visited));
        tag.put("Banks", toList(this.banks));
        return tag;
    }

    public static VillageBankTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageBankTracker tracker = new VillageBankTracker();
        readInto(tag.getList("Visited", Tag.TAG_LONG), tracker.visited);
        readInto(tag.getList("Banks", Tag.TAG_LONG), tracker.banks);
        return tracker;
    }

    private static ListTag toList(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos p : positions) {
            list.add(LongTag.valueOf(p.asLong()));
        }
        return list;
    }

    private static void readInto(ListTag list, List<BlockPos> out) {
        for (int i = 0; i < list.size(); i++) {
            out.add(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
        }
    }
}
