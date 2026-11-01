package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent per-dimension list of the Mini Hogwarts castles that have generated. The castle structure piece
 * records each one (on the server thread) when the chunk holding its centre is decorated; {@code /findminihogwarts}
 * and the title card read it. Mirrors {@link DiagonAlleyTracker}.
 *
 * <p>Server thread only: worldgen threads hand their record over with {@code server.execute(...)}.
 */
public class MiniHogwartsTracker extends SavedData {
    public static final String DATA_NAME = "mini_hogwarts_castles";

    /** Save key of the current format: a list of castle compounds. */
    private static final String KEY_CASTLE_LIST = "CastleList";
    /**
     * Save key of the original format: a list of LongTag centres. Old saves hold only this one, and it is still
     * written (centres only) so a save opened by an older build keeps its castles.
     */
    private static final String KEY_LEGACY = "Castles";
    /** Two records closer than this are the same castle (the centre chunk can be decorated more than once). */
    private static final double DEDUPE_DISTANCE = 128.0;

    private final List<Castle> castles = new ArrayList<>();

    /**
     * One castle. {@code center} is the locator point (the footprint's middle, a third of the way up);
     * {@code anchor} is the schematic's min corner in the world, null for a castle from an old save. The two sets
     * are the per-player memory: who has had the "Hogwarts" title card, and who has walked into the Great Hall.
     */
    public record Castle(BlockPos center, @Nullable BlockPos anchor, Set<UUID> titled, Set<UUID> enteredHall) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("Center", NbtUtils.writeBlockPos(center));
            if (anchor != null) {
                tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
            }
            tag.put("Titled", uuids(titled));
            tag.put("EnteredHall", uuids(enteredHall));
            return tag;
        }

        @Nullable
        static Castle load(CompoundTag tag) {
            BlockPos center = NbtUtils.readBlockPos(tag, "Center").orElse(null);
            if (center == null) {
                return null;
            }
            Castle castle = new Castle(center, NbtUtils.readBlockPos(tag, "Anchor").orElse(null),
                    new HashSet<>(), new HashSet<>());
            readUuids(tag.getList("Titled", Tag.TAG_INT_ARRAY), castle.titled);
            readUuids(tag.getList("EnteredHall", Tag.TAG_INT_ARRAY), castle.enteredHall);
            return castle;
        }

        private static ListTag uuids(Set<UUID> set) {
            ListTag list = new ListTag();
            for (UUID id : set) {
                list.add(NbtUtils.createUUID(id));
            }
            return list;
        }

        private static void readUuids(ListTag list, Set<UUID> into) {
            for (int i = 0; i < list.size(); i++) {
                into.add(NbtUtils.loadUUID(list.get(i)));
            }
        }
    }

    public static MiniHogwartsTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(MiniHogwartsTracker::new, MiniHogwartsTracker::load, null),
                DATA_NAME
        );
    }

    /**
     * Records a castle, unless one is already recorded within 128 blocks of {@code center}. Returns the castle now
     * on record there (the existing one when deduplicated).
     */
    public Castle add(BlockPos center, @Nullable BlockPos anchor) {
        for (Castle existing : castles) {
            if (existing.center.distSqr(center) < DEDUPE_DISTANCE * DEDUPE_DISTANCE) {
                return existing;
            }
        }
        Castle castle = new Castle(center.immutable(), anchor == null ? null : anchor.immutable(),
                new HashSet<>(), new HashSet<>());
        castles.add(castle);
        setDirty();
        return castle;
    }

    /** The live list of castles, oldest first. */
    public List<Castle> castles() {
        return castles;
    }

    /** The recorded centres of every castle, oldest first. */
    public List<BlockPos> getAll() {
        List<BlockPos> centres = new ArrayList<>(castles.size());
        for (Castle c : castles) {
            centres.add(c.center);
        }
        return centres;
    }

    /** The castle whose centre has the smallest XZ distance to {@code from}, or null when there is none. */
    @Nullable
    public Castle findClosest(BlockPos from) {
        Castle best = null;
        double bestDist = Double.MAX_VALUE;
        for (Castle c : castles) {
            double dx = c.center.getX() - from.getX();
            double dz = c.center.getZ() - from.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** Remembers that {@code player} has had this castle's title card. */
    public void markTitled(UUID player, Castle castle) {
        if (castle.titled.add(player)) {
            setDirty();
        }
    }

    /** Remembers that {@code player} has walked into this castle's Great Hall. */
    public void markEnteredHall(UUID player, Castle castle) {
        if (castle.enteredHall.add(player)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        ListTag legacy = new ListTag();
        for (Castle c : castles) {
            list.add(c.save());
            legacy.add(LongTag.valueOf(c.center.asLong()));
        }
        tag.put(KEY_CASTLE_LIST, list);
        tag.put(KEY_LEGACY, legacy);
        return tag;
    }

    public static MiniHogwartsTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        MiniHogwartsTracker tracker = new MiniHogwartsTracker();
        if (tag.contains(KEY_CASTLE_LIST, Tag.TAG_LIST)) {
            ListTag list = tag.getList(KEY_CASTLE_LIST, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Castle castle = Castle.load(list.getCompound(i));
                if (castle != null) {
                    tracker.castles.add(castle);
                }
            }
            return tracker;
        }
        // Old save: nothing but centres, built by the old live placement. They load with no anchor.
        ListTag legacy = tag.getList(KEY_LEGACY, Tag.TAG_LONG);
        for (int i = 0; i < legacy.size(); i++) {
            tracker.castles.add(new Castle(BlockPos.of(((LongTag) legacy.get(i)).getAsLong()), null,
                    new HashSet<>(), new HashSet<>()));
        }
        return tracker;
    }
}
