package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.ChamberDoorBlock;
import com.hogwartsmagic.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs the Chamber door opening, once, over about five seconds.
 *
 * <p>Two phases, because the door in the film does two distinct things: the ring of serpent bolts
 * retracts one bolt at a time, and only then does the leaf itself roll away. Doing both at once
 * removes the same blocks in the same time and reads as the door simply evaporating.
 *
 * <p>The leaf is found by scanning for tuff around the lock rather than by walking a hard-coded
 * circle. The jigsaw may place the Chamber at any of four rotations, and offsets baked in template
 * coordinates would sweep the door open through the wall in three of them.
 */
public class ChamberDoorBlockEntity extends BlockEntity {
    private static final int SEARCH = 7;
    private static final int BOLT_EVERY = 6;        // ticks between bolts; there are seven of them
    private static final int LEAF_START = 46;       // once the last bolt has gone
    private static final int LEAF_PER_TICK = 3;
    private static final String TICKS_KEY = "OpenTicks";

    private int ticks = -1;                         // -1 idle, else ticks since the Parseltongue
    private final List<BlockPos> bolts = new ArrayList<>();
    private final List<BlockPos> leaf = new ArrayList<>();
    private int boltsTaken;
    private int leafTaken;
    private boolean surveyed;

    public ChamberDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.CHAMBER_DOOR_BLOCK_ENTITY.get(), pos, state);
    }

    public boolean beginOpening(ServerLevel level) {
        if (ticks >= 0 || getBlockState().getValue(ChamberDoorBlock.OPEN)) {
            return false;
        }
        ticks = 0;
        surveyed = false;
        level.setBlock(worldPosition, getBlockState().setValue(ChamberDoorBlock.OPEN, Boolean.TRUE), 3);
        level.playSound(null, worldPosition, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 1.6F, 0.7F);
        setChanged();
        return true;
    }

    /**
     * Collect what is going to move: the bolts are the lightning rods standing in the rim, the leaf
     * is every piece of tuff within reach of the lock.
     */
    private void survey(ServerLevel level) {
        bolts.clear();
        leaf.clear();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-SEARCH, -SEARCH, -SEARCH),
                                                   worldPosition.offset(SEARCH, SEARCH, SEARCH))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LIGHTNING_ROD)) {
                bolts.add(pos.immutable());
            } else if (state.is(Blocks.TUFF_BRICKS) || state.is(Blocks.POLISHED_TUFF)
                    || state.is(Blocks.CHISELED_TUFF)) {
                leaf.add(pos.immutable());
            }
        }

        // The door lies in a plane, and which plane depends on the rotation it was placed at. The
        // axis the leaf barely varies along is the one it faces; the other two are the ones an angle
        // can be measured in, so the leaf peels away in one unwinding sweep rather than in scan
        // order, which would wipe it off in flat rows.
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos pos : leaf) {
            int[] d = {pos.getX(), pos.getY(), pos.getZ()};
            for (int a = 0; a < 3; a++) {
                lo[a] = Math.min(lo[a], d[a]);
                hi[a] = Math.max(hi[a], d[a]);
            }
        }
        int normal = 0;
        for (int a = 1; a < 3; a++) {
            if (hi[a] - lo[a] < hi[normal] - lo[normal]) {
                normal = a;
            }
        }
        final int u = normal == 0 ? 1 : 0;
        final int v = normal == 2 ? 1 : 2;
        Comparator<BlockPos> unwind = Comparator.comparingDouble(pos -> {
            int[] d = {pos.getX() - worldPosition.getX(), pos.getY() - worldPosition.getY(),
                       pos.getZ() - worldPosition.getZ()};
            return Math.atan2(d[v], d[u]);
        });
        leaf.sort(unwind);
        bolts.sort(unwind);
        surveyed = true;
    }

    private void dissolve(ServerLevel level, BlockPos pos) {
        BlockState was = level.getBlockState(pos);
        if (was.isAir()) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        level.removeBlock(pos, false);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChamberDoorBlockEntity door) {
        if (door.ticks < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        if (!door.surveyed) {
            door.survey(server);
        }
        door.ticks++;

        if (door.ticks <= LEAF_START) {
            int due = Math.min(door.bolts.size(), door.ticks / BOLT_EVERY);
            while (door.boltsTaken < due) {
                door.dissolve(server, door.bolts.get(door.boltsTaken++));
                server.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.6F);
            }
            return;
        }

        if (door.leafTaken >= door.leaf.size()) {
            // The lock goes last, and goes too. It sits dead centre so a spell can reach it, which
            // means leaving it behind would strand one block floating in the middle of an open
            // doorway. Removing the block here discards this block entity, so nothing may touch
            // `door` afterwards.
            door.ticks = -1;
            server.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.4F, 0.5F);
            door.dissolve(server, pos);
            return;
        }
        if (door.leafTaken == 0) {
            server.playSound(null, pos, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.5F, 0.4F);
        }
        for (int i = 0; i < LEAF_PER_TICK && door.leafTaken < door.leaf.size(); i++) {
            door.dissolve(server, door.leaf.get(door.leafTaken++));
        }
        if (door.leafTaken % 12 < LEAF_PER_TICK) {
            server.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
        door.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, ticks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ticks = tag.contains(TICKS_KEY) ? tag.getInt(TICKS_KEY) : -1;
        // Whatever had already gone stays gone: the survey re-runs on the next tick and simply finds
        // less of the door than before, which is the correct state to carry on from.
        surveyed = false;
        boltsTaken = 0;
        leafTaken = 0;
    }
}
