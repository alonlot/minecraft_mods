package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Which walls have an alley behind them, and where in the alley dimension it was built.
 *
 * <p>This is all the persistent state the feature has. It replaces {@code DiagonAlleySnapshot},
 * which had to hold a copy of every block the alley covered so the world could be put back; nothing
 * is covered any more, because nothing is built in anybody's world, so there is nothing to put back
 * and nothing to remember except an anchor.
 *
 * <h2>Why entries are kept</h2>
 *
 * <p>Building the street is the only expensive part of opening a wall. Keeping the paste means a
 * wall is built once ever: the second time you tap it, the alley is already standing and the arch
 * opens onto it immediately. It also means anything you left in a shop is still there.
 *
 * <p>The cost is that the alley dimension grows by one street per wall anyone ever opens. That is
 * the trade the alternative could not make -- a single shared alley would save the space, but it
 * would have to sit at fixed coordinates, and {@link DiagonAlleyPortal} can only be seamless when
 * the alley is at the coordinates of the wall itself.
 *
 * <p>Lives on the alley level rather than on the level the wall is in, so one lookup finds every
 * alley regardless of which world its door is in.
 */
public class DiagonAlleyRegistry extends SavedData {

    private static final String NAME = "hogwartsmagic_diagon_alleys";

    /**
     * One wall, and the street behind it.
     *
     * <p>{@code complete} is false from the moment the paste is started until its last phase has
     * run. An entry is recorded before the first block goes down, because the portal and the arch
     * mirror need it while the street is still going up; without the flag a server that stopped
     * mid-build came back with a half-street that read as "already built" for good.
     */
    public record Entry(String sourceDimension, BlockPos arch, BlockPos anchor, Direction facing,
                        int sizeX, int sizeY, int sizeZ, boolean complete) {

        /** Is this world position inside the street this entry built? */
        public boolean contains(BlockPos pos) {
            return pos.getX() >= anchor.getX() && pos.getX() < anchor.getX() + sizeX
                    && pos.getY() >= anchor.getY() && pos.getY() < anchor.getY() + sizeY
                    && pos.getZ() >= anchor.getZ() && pos.getZ() < anchor.getZ() + sizeZ;
        }

        /** Does this chunk hold any of the street? */
        public boolean coversChunk(int chunkX, int chunkZ) {
            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            return x0 + 16 > anchor.getX() && x0 < anchor.getX() + sizeX
                    && z0 + 16 > anchor.getZ() && z0 < anchor.getZ() + sizeZ;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public DiagonAlleyRegistry() {
    }

    public static DiagonAlleyRegistry get(ServerLevel alleyLevel) {
        return alleyLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DiagonAlleyRegistry::new, DiagonAlleyRegistry::load, null),
                NAME);
    }

    /** The alley behind this particular wall, or null if it has never been opened. */
    @Nullable
    public Entry forArch(ServerLevel source, BlockPos arch) {
        String dim = source.dimension().location().toString();
        for (Entry e : entries) {
            if (e.arch().equals(arch) && e.sourceDimension().equals(dim)) {
                return e;
            }
        }
        return null;
    }

    /** Any alley whose door is this arch, whichever world the door is in. */
    @Nullable
    public Entry byArch(BlockPos arch) {
        for (Entry e : entries) {
            if (e.arch().equals(arch)) {
                return e;
            }
        }
        return null;
    }

    /** The alley a position in the alley dimension belongs to, or null if it is out in the void. */
    @Nullable
    public Entry at(BlockPos pos) {
        for (Entry e : entries) {
            if (e.contains(pos)) {
                return e;
            }
        }
        return null;
    }

    /** The alley whose street covers this chunk, or null. */
    @Nullable
    public Entry coveringChunk(int chunkX, int chunkZ) {
        for (Entry e : entries) {
            if (e.coversChunk(chunkX, chunkZ)) {
                return e;
            }
        }
        return null;
    }

    /** Every alley whose street would share space with this footprint. */
    public List<Entry> overlapping(BlockPos anchor, int sx, int sy, int sz) {
        List<Entry> hit = new ArrayList<>();
        for (Entry e : entries) {
            if (anchor.getX() < e.anchor().getX() + e.sizeX()
                    && anchor.getX() + sx > e.anchor().getX()
                    && anchor.getZ() < e.anchor().getZ() + e.sizeZ()
                    && anchor.getZ() + sz > e.anchor().getZ()
                    && anchor.getY() < e.anchor().getY() + e.sizeY()
                    && anchor.getY() + sy > e.anchor().getY()) {
                hit.add(e);
            }
        }
        return hit;
    }

    public void remove(Entry entry) {
        if (entries.remove(entry)) {
            setDirty();
        }
    }

    public void record(Entry entry) {
        entries.removeIf(e -> e.arch().equals(entry.arch())
                && e.sourceDimension().equals(entry.sourceDimension()));
        entries.add(entry);
        setDirty();
    }

    /** The paste behind this wall has finished; the street is whole. */
    public void markComplete(Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.arch().equals(entry.arch()) && e.sourceDimension().equals(entry.sourceDimension())) {
                if (!e.complete()) {
                    entries.set(i, new Entry(e.sourceDimension(), e.arch(), e.anchor(), e.facing(),
                            e.sizeX(), e.sizeY(), e.sizeZ(), true));
                    setDirty();
                }
                return;
            }
        }
    }

    /** A street whose paste never finished (the server stopped under it), or null. */
    @Nullable
    public Entry firstIncomplete() {
        for (Entry e : entries) {
            if (!e.complete()) {
                return e;
            }
        }
        return null;
    }

    /** Forget a wall, so tapping it builds a fresh street. Used when the wall itself is destroyed. */
    public void forget(ServerLevel source, BlockPos arch) {
        String dim = source.dimension().location().toString();
        if (entries.removeIf(e -> e.arch().equals(arch) && e.sourceDimension().equals(dim))) {
            setDirty();
        }
    }

    public List<Entry> all() {
        return entries;
    }

    // ---------------------------------------------------------------------------- persistence

    public static DiagonAlleyRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        DiagonAlleyRegistry data = new DiagonAlleyRegistry();
        ListTag list = tag.getList("Alleys", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int[] arch = e.getIntArray("Arch");
            int[] anchor = e.getIntArray("Anchor");
            int[] size = e.getIntArray("Size");
            if (arch.length != 3 || anchor.length != 3 || size.length != 3) {
                continue;
            }
            Direction facing = Direction.from3DDataValue(e.getInt("Facing"));
            // Saves from before the flag existed carry only finished streets.
            boolean complete = !e.contains("Complete", Tag.TAG_BYTE) || e.getBoolean("Complete");
            data.entries.add(new Entry(
                    e.getString("From"),
                    new BlockPos(arch[0], arch[1], arch[2]),
                    new BlockPos(anchor[0], anchor[1], anchor[2]),
                    facing, size[0], size[1], size[2], complete));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.putString("From", e.sourceDimension());
            c.putIntArray("Arch", new int[]{e.arch().getX(), e.arch().getY(), e.arch().getZ()});
            c.putIntArray("Anchor",
                    new int[]{e.anchor().getX(), e.anchor().getY(), e.anchor().getZ()});
            c.putIntArray("Size", new int[]{e.sizeX(), e.sizeY(), e.sizeZ()});
            c.putInt("Facing", e.facing().get3DDataValue());
            c.putBoolean("Complete", e.complete());
            list.add(c);
        }
        tag.put("Alleys", list);
        return tag;
    }
}
