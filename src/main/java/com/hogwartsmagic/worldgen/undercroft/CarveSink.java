package com.hogwartsmagic.worldgen.undercroft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a carve writes its blocks.
 *
 * <p>Two implementations, for the same reason the structure renderers exist: the undercroft is
 * generated procedurally, so unlike every {@code .nbt} in this repo there is no file to look at
 * before it goes in the ground. {@link Template} gives the carve somewhere to write that is not the
 * world, so the same code can produce something the deepslate viewer can open.
 */
public interface CarveSink {

    /** Absolute world coordinates. */
    void set(int x, int y, int z, BlockState state);

    boolean isAir(int x, int y, int z);

    /** True if this column is somewhere we may write at all. */
    boolean available(int x, int z);

    int minY();

    int maxY();

    /** How many blocks have actually been written. */
    int written();

    // ---------------------------------------------------------------------------------

    /** Writes straight into the world. */
    final class Level implements CarveSink {
        /** Same flag both existing over-ticks builders use: clients are told, neighbours are not.
         *  Adding UPDATE_NEIGHBORS here would cascade gravity, fluid flow and redstone across the
         *  whole footprint while several hundred thousand cells are in flux. */
        private static final int FLAGS = Block.UPDATE_CLIENTS;

        private final ServerLevel level;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final int floor;
        private int written;

        public Level(ServerLevel level, int floor) {
            this.level = level;
            this.floor = floor;
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            if (y < this.floor || y >= this.level.getMaxBuildHeight()) {
                return;
            }
            this.cursor.set(x, y, z);
            // A state read is far cheaper than a write plus a lighting update plus a packet, and a
            // good fraction of a deep carve is already cave air.
            if (this.level.getBlockState(this.cursor) == state) {
                return;
            }
            this.level.setBlock(this.cursor, state, FLAGS);
            this.written++;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            this.cursor.set(x, y, z);
            return this.level.getBlockState(this.cursor).isAir();
        }

        @Override
        public boolean available(int x, int z) {
            return this.level.hasChunkAt(this.cursor.set(x, this.floor, z));
        }

        @Override
        public int minY() {
            return this.floor;
        }

        @Override
        public int maxY() {
            return this.level.getMaxBuildHeight() - 1;
        }

        @Override
        public int written() {
            return this.written;
        }
    }

    // ---------------------------------------------------------------------------------

    /**
     * Collects the carve in memory so it can be written out as a structure template and rendered.
     *
     * <p>Deliberately records only what the carve touches, so the dump is the shape of the build
     * rather than the shape of its bounding box. Air is kept, because in a carve the air IS the
     * build - dropping it would dump a solid lump of rock.
     */
    final class Template implements CarveSink {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final int floor;
        private final int ceiling;

        public Template(int floor, int ceiling) {
            this.floor = floor;
            this.ceiling = ceiling;
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            if (y < this.floor || y > this.ceiling) {
                return;
            }
            this.blocks.put(new BlockPos(x, y, z), state);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            BlockState state = this.blocks.get(new BlockPos(x, y, z));
            return state != null && state.isAir();
        }

        @Override
        public boolean available(int x, int z) {
            return true;
        }

        @Override
        public int minY() {
            return this.floor;
        }

        @Override
        public int maxY() {
            return this.ceiling;
        }

        @Override
        public int written() {
            return this.blocks.size();
        }

        public Map<BlockPos, BlockState> blocks() {
            return this.blocks;
        }

        /** What was written at a cell, or null if the carve never touched it. */
        public BlockState at(int x, int y, int z) {
            return this.blocks.get(new BlockPos(x, y, z));
        }
    }
}
