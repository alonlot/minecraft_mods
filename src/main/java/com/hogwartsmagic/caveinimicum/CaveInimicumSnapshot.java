package com.hogwartsmagic.caveinimicum;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/**
 * Immutable saved appearance for a warded cube. The snapshot never changes
 * after activation; later world mutations are tracked separately.
 */
public final class CaveInimicumSnapshot {
    public static final int MAX_EDGE_LENGTH = 64;

    private final CaveInimicumWardBounds bounds;
    private final int[] stateIds;

    public CaveInimicumSnapshot(CaveInimicumWardBounds bounds, int[] stateIds) {
        if (stateIds.length != bounds.volume()) {
            throw new IllegalArgumentException("Snapshot size does not match ward volume");
        }
        this.bounds = bounds;
        this.stateIds = Arrays.copyOf(stateIds, stateIds.length);
    }

    public CaveInimicumWardBounds bounds() {
        return bounds;
    }

    public int edgeLength() {
        return bounds.edgeLength();
    }

    public BlockPos center() {
        return bounds.center();
    }

    public int[] copyStateIds() {
        return Arrays.copyOf(stateIds, stateIds.length);
    }

    public int stateIdAt(BlockPos pos) {
        return stateIds[bounds.indexOf(pos)];
    }

    public BlockState stateAt(BlockPos pos) {
        return Block.stateById(stateIdAt(pos));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Center", center().asLong());
        tag.putInt("EdgeLength", edgeLength());
        tag.putIntArray("States", stateIds);
        return tag;
    }

    public static CaveInimicumSnapshot load(CompoundTag tag) {
        int edgeLength = tag.getInt("EdgeLength");
        if (edgeLength <= 0 || edgeLength > MAX_EDGE_LENGTH || (edgeLength & 1) != 0) {
            return null;
        }
        CaveInimicumWardBounds bounds = new CaveInimicumWardBounds(
                BlockPos.of(tag.getLong("Center")),
                edgeLength);
        int[] storedStates = tag.getIntArray("States");
        if (storedStates.length != bounds.volume()) {
            return null;
        }
        return new CaveInimicumSnapshot(bounds, storedStates);
    }
}
