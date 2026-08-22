package com.hogwartsmagic.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the world looked like before Diagon Alley was built on top of it.
 *
 * <p>The alley is opened <em>in place</em>: seventy-six thousand blocks written into whatever the
 * player was standing on, so that walking through the arch needs no teleport, no loading screen and
 * no portal trickery — you are simply looking at a street that was not there a moment ago. The
 * price of that is this class. Every block the alley is about to cover is recorded here first, and
 * put back when the last visitor leaves.
 *
 * <h2>The rule that makes this safe</h2>
 *
 * <p><b>The snapshot is captured, marked active, and flushed to disk before a single block of the
 * alley is placed.</b> Not after, not alongside. The failure this exists to survive is the server
 * dying halfway through a visit, and a snapshot that was still in memory when that happened is
 * worth nothing. Because it is on disk and flagged, {@link DiagonAlleyManager} can restore it on
 * the next boot, which turns "your world is permanently scarred" into "your world came back".
 *
 * <h2>Why blockstates and not state IDs</h2>
 *
 * <p>Block states are stored through {@link NbtUtils#writeBlockState}, the same way structure
 * templates store theirs, rather than as the numeric IDs {@code Block.getId} hands out. The numeric
 * IDs are an artefact of what happens to be registered this launch: install a mod, or update one,
 * and every ID after the insertion point shifts. That is fine for something that lives for a few
 * ticks, and not fine for the one record standing between the player and a hole in their world.
 *
 * <h2>Shape</h2>
 *
 * <p>Sparse. Only non-air cells are stored, so opening the arch in a field costs very little and
 * only opening it inside a mountain is expensive. Positions are held as a flat index into the
 * region rather than as absolute coordinates, which halves the storage and keeps the record
 * meaningful relative to its own origin.
 */
public class DiagonAlleySnapshot extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");

    public static final String DATA_NAME = "diagon_alley_snapshot";

    private boolean active;
    private BlockPos origin = BlockPos.ZERO;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    /**
     * The archway the alley was opened through, so it can be resealed when the world is put back.
     * Stored here rather than in a static because it has to survive a restart: the whole point of
     * this record is that it is still meaningful after a crash.
     */
    private BlockPos archPos = BlockPos.ZERO;

    /** Palette of distinct states, and one entry per recorded cell. */
    private final List<BlockState> palette = new ArrayList<>();
    private int[] cells = new int[0];
    private int[] states = new int[0];
    /** Block entities keyed by the same flat cell index — chests keep their contents. */
    private final Map<Integer, CompoundTag> blockEntities = new HashMap<>();

    public static DiagonAlleySnapshot get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(DiagonAlleySnapshot::new, DiagonAlleySnapshot::load, null),
                DATA_NAME);
    }

    public boolean isActive() {
        return this.active;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public BlockPos archPos() {
        return this.archPos;
    }

    public void setArchPos(BlockPos pos) {
        this.archPos = pos.immutable();
        setDirty();
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public int volume() {
        return this.sizeX * this.sizeY * this.sizeZ;
    }

    public int cellCount() {
        return this.cells.length;
    }

    public int cellAt(int i) {
        return this.cells[i];
    }

    public BlockState stateAt(int i) {
        int id = this.states[i];
        return id >= 0 && id < this.palette.size() ? this.palette.get(id) : Blocks.AIR.defaultBlockState();
    }

    public CompoundTag blockEntityAt(int cell) {
        return this.blockEntities.get(cell);
    }

    public BlockPos posOfCell(int cell) {
        int x = cell % this.sizeX;
        int rest = cell / this.sizeX;
        int z = rest % this.sizeZ;
        int y = rest / this.sizeZ;
        return new BlockPos(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
    }

    private int cellIndex(int lx, int ly, int lz) {
        return (ly * this.sizeZ + lz) * this.sizeX + lx;
    }

    // --- capture, in slices ---------------------------------------------------------------------
    //
    // Half a million cells is too many to walk in one tick. It is taken a slice at a time, under
    // cover of the seven and a half seconds the archway animation is already running for, and only
    // committed at the end. Nothing is placed until commit() has returned, so a crash part-way
    // through a capture leaves a world that was never touched.

    private transient Map<BlockState, Integer> paletteIndex;
    private transient List<Integer> cellList;
    private transient List<Integer> stateList;

    /** Start a capture of {@code sx * sy * sz} cells at {@code origin}. */
    public void begin(BlockPos origin, int sx, int sy, int sz) {
        this.active = false;                    // not yet: nothing has been placed
        this.origin = origin.immutable();
        this.sizeX = sx;
        this.sizeY = sy;
        this.sizeZ = sz;
        this.palette.clear();
        this.blockEntities.clear();
        this.cells = new int[0];
        this.states = new int[0];
        this.paletteIndex = new HashMap<>();
        this.cellList = new ArrayList<>();
        this.stateList = new ArrayList<>();
    }

    /** Record cells {@code [from, to)} of the region. Returns how many were non-air. */
    public int captureSlice(ServerLevel level, int from, int to) {
        if (this.cellList == null) {
            return 0;
        }
        int recorded = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cell = from; cell < to; cell++) {
            int lx = cell % this.sizeX;
            int rest = cell / this.sizeX;
            int lz = rest % this.sizeZ;
            int ly = rest / this.sizeZ;
            cursor.set(this.origin.getX() + lx, this.origin.getY() + ly, this.origin.getZ() + lz);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;                       // sparse: air is what a missing cell restores to
            }
            Integer id = this.paletteIndex.get(state);
            if (id == null) {
                id = this.palette.size();
                this.palette.add(state);
                this.paletteIndex.put(state, id);
            }
            this.cellList.add(cell);
            this.stateList.add(id);
            recorded++;

            BlockEntity be = level.getBlockEntity(cursor);
            if (be != null) {
                // A chest full of somebody's belongings has to come back full.
                this.blockEntities.put(cell, be.saveWithFullMetadata(level.registryAccess()));
            }
        }
        return recorded;
    }

    /**
     * Finish the capture, mark it active, and <b>force it to disk</b>.
     *
     * <p>The forced write is the whole point of this class. {@code setDirty()} alone only queues the
     * record for the next autosave, which could be minutes away — and the failure this exists to
     * survive is the server dying during those minutes with the alley already built. Only once this
     * has returned may a block be placed.
     */
    public void commit(ServerLevel level) {
        this.cells = new int[this.cellList.size()];
        this.states = new int[this.stateList.size()];
        for (int i = 0; i < this.cells.length; i++) {
            this.cells[i] = this.cellList.get(i);
            this.states[i] = this.stateList.get(i);
        }
        this.paletteIndex = null;
        this.cellList = null;
        this.stateList = null;
        this.active = true;
        setDirty();
        level.getDataStorage().save();
        LOGGER.info("Captured {} blocks ({} states, {} block entities) over {}x{}x{} at {}, flushed",
                this.cells.length, this.palette.size(), this.blockEntities.size(),
                this.sizeX, this.sizeY, this.sizeZ, this.origin);
    }

    /** Called once the world has been put back, so the next boot does not restore it again. */
    public void clear() {
        this.active = false;
        this.palette.clear();
        this.blockEntities.clear();
        this.cells = new int[0];
        this.states = new int[0];
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("Active", this.active);
        tag.putIntArray("Origin", new int[]{this.origin.getX(), this.origin.getY(), this.origin.getZ()});
        tag.putIntArray("Size", new int[]{this.sizeX, this.sizeY, this.sizeZ});
        tag.putLong("Arch", this.archPos.asLong());

        ListTag paletteTag = new ListTag();
        for (BlockState state : this.palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", paletteTag);
        tag.putIntArray("Cells", this.cells);
        tag.putIntArray("States", this.states);

        ListTag beTag = new ListTag();
        for (Map.Entry<Integer, CompoundTag> entry : this.blockEntities.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putInt("Cell", entry.getKey());
            one.put("Nbt", entry.getValue());
            beTag.add(one);
        }
        tag.put("BlockEntities", beTag);
        return tag;
    }

    public static DiagonAlleySnapshot load(CompoundTag tag, HolderLookup.Provider provider) {
        DiagonAlleySnapshot snap = new DiagonAlleySnapshot();
        snap.active = tag.getBoolean("Active");
        int[] origin = tag.getIntArray("Origin");
        if (origin.length == 3) {
            snap.origin = new BlockPos(origin[0], origin[1], origin[2]);
        }
        int[] size = tag.getIntArray("Size");
        if (size.length == 3) {
            snap.sizeX = size[0];
            snap.sizeY = size[1];
            snap.sizeZ = size[2];
        }
        snap.archPos = BlockPos.of(tag.getLong("Arch"));

        HolderLookup<net.minecraft.world.level.block.Block> blocks =
                provider.lookupOrThrow(Registries.BLOCK);
        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < paletteTag.size(); i++) {
            snap.palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(i)));
        }
        snap.cells = tag.getIntArray("Cells");
        snap.states = tag.getIntArray("States");

        ListTag beTag = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < beTag.size(); i++) {
            CompoundTag one = beTag.getCompound(i);
            snap.blockEntities.put(one.getInt("Cell"), one.getCompound("Nbt"));
        }
        return snap;
    }
}
