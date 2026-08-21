package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent record of the undercrofts dug in a dimension, and how far each one got.
 *
 * <p>Mirrors {@link com.hogwartsmagic.worldgen.VillageBankTracker} in shape, with one addition that
 * matters: the carve's progress is stored, not just its position. A bank goes up in twenty seconds
 * and losing one to a restart costs nothing; an undercroft is several hundred thousand blocks, and
 * a half-carved chasm with no record of it is a hole in the world nobody can finish.
 *
 * <p>Because every pass of the carve is a pure function of position, resuming from a saved cursor
 * produces exactly the same undercroft as never having stopped.
 */
public class UndercroftTracker extends SavedData {

    public static final String DATA_NAME = "gringotts_undercrofts";

    /** Two undercrofts within this distance are the same one. */
    private static final double SAME_SITE = 64.0D;

    public static final int PENDING = 0;
    public static final int CARVING = 1;
    public static final int COMPLETE = 2;
    public static final int REFUSED = 3;

    public static final class Undercroft {
        public final BlockPos station;
        public final int depth;
        public int stage;
        public int workItem;
        public long cursor;

        public Undercroft(BlockPos station, int depth, int stage, int workItem, long cursor) {
            this.station = station.immutable();
            this.depth = depth;
            this.stage = stage;
            this.workItem = workItem;
            this.cursor = cursor;
        }
    }

    private final List<Undercroft> sites = new ArrayList<>();

    public static UndercroftTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(UndercroftTracker::new, UndercroftTracker::load, null), DATA_NAME);
    }

    public List<Undercroft> all() {
        return this.sites;
    }

    @Nullable
    public Undercroft at(BlockPos station) {
        for (Undercroft site : this.sites) {
            if (site.station.distSqr(station) < SAME_SITE * SAME_SITE) {
                return site;
            }
        }
        return null;
    }

    /** The nearest undercroft that is not finished, so a passing player can get it going again. */
    @Nullable
    public Undercroft nearestUnfinished(BlockPos from, double within) {
        Undercroft best = null;
        double bestDist = within * within;
        for (Undercroft site : this.sites) {
            if (site.stage == COMPLETE || site.stage == REFUSED) {
                continue;
            }
            double d = site.station.distSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = site;
            }
        }
        return best;
    }

    public Undercroft put(BlockPos station, int depth, int stage) {
        Undercroft existing = at(station);
        if (existing != null) {
            existing.stage = stage;
            setDirty();
            return existing;
        }
        Undercroft site = new Undercroft(station, depth, stage, 0, 0L);
        this.sites.add(site);
        setDirty();
        return site;
    }

    public void progress(Undercroft site, int workItem, long cursor, int stage) {
        site.workItem = workItem;
        site.cursor = cursor;
        site.stage = stage;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Undercroft site : this.sites) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Station", site.station.asLong());
            entry.putInt("Depth", site.depth);
            entry.putInt("Stage", site.stage);
            entry.putInt("Item", site.workItem);
            entry.putLong("Cursor", site.cursor);
            list.add(entry);
        }
        tag.put("Sites", list);
        return tag;
    }

    public static UndercroftTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        UndercroftTracker tracker = new UndercroftTracker();
        ListTag list = tag.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            tracker.sites.add(new Undercroft(
                    BlockPos.of(entry.getLong("Station")),
                    entry.getInt("Depth"),
                    entry.getInt("Stage"),
                    entry.getInt("Item"),
                    entry.getLong("Cursor")));
        }
        return tracker;
    }
}
