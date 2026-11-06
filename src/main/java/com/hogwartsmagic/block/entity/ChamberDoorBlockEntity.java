package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.util.MagicParticles;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.ChamberDoorBlock;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Opens the Chamber door, once, as a serpent rather than as a demolition.
 *
 * <p>Three beats, in the order the film plays them. A snake of light wakes and runs a full circuit
 * of the rim, and each of the seven bolts draws back as the head reaches it -- so the bolts retract
 * in sequence around the ring rather than in scan order, and something visibly causes each one. Then
 * the leaf unwinds: blocks are taken in <em>spiral</em> order, angle plus a turn per unit of radius,
 * so the stone peels from the rim inward the way a coiled snake straightens. The lock goes last.
 *
 * <p>Everything is driven from polar coordinates in the door's own plane, and that plane is measured
 * rather than assumed. The jigsaw may place the Chamber at any of four rotations; the axis the leaf
 * barely varies along is the one the door faces, and the other two are the ones an angle can be
 * measured in. Hard-coded offsets would spiral the door open through the wall in three rotations out
 * of four -- which is also why the leaf is found by scanning for its own block rather than by walking
 * a circle out from here.
 */
public class ChamberDoorBlockEntity extends BlockEntity {
    private static final int SEARCH = 7;
    private static final int RIM_TICKS = 56;        // one circuit of the rim, about three seconds
    private static final int LEAF_PER_TICK = 3;
    private static final double SPIRAL_TURNS = 1.6; // extra turns added across the leaf's radius
    private static final String TICKS_KEY = "OpenTicks";

    private int ticks = -1;                         // -1 idle, else ticks since the Parseltongue
    private final List<BlockPos> bolts = new ArrayList<>();
    private final List<BlockPos> leaf = new ArrayList<>();
    private int boltsTaken;
    private int leafTaken;
    private boolean surveyed;

    private int uAxis = 0, vAxis = 1;               // the two axes the door's plane is spanned by
    private double maxRadius = 1.0;

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

    // --- the door's own plane ----------------------------------------------------------------

    private static int axis(BlockPos p, int a) {
        return a == 0 ? p.getX() : a == 1 ? p.getY() : p.getZ();
    }

    /** Offset of a block from the lock, along the two axes the door lies in. */
    private double[] plane(BlockPos p) {
        return new double[]{axis(p, uAxis) - axis(worldPosition, uAxis),
                            axis(p, vAxis) - axis(worldPosition, vAxis)};
    }

    private double angleOf(BlockPos p) {
        double[] d = plane(p);
        return Math.atan2(d[1], d[0]);
    }

    private double radiusOf(BlockPos p) {
        double[] d = plane(p);
        return Math.hypot(d[0], d[1]);
    }

    /** A point in the door's plane, for drawing the serpent, which has no blocks of its own. */
    private Vec3 at(double angle, double radius) {
        double[] c = {worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5};
        c[uAxis] += radius * Math.cos(angle);
        c[vAxis] += radius * Math.sin(angle);
        return new Vec3(c[0], c[1], c[2]);
    }

    private void survey(ServerLevel level) {
        bolts.clear();
        leaf.clear();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-SEARCH, -SEARCH, -SEARCH),
                                                   worldPosition.offset(SEARCH, SEARCH, SEARCH))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LIGHTNING_ROD)) {
                bolts.add(pos.immutable());
            } else if (state.is(ExampleMod.CHAMBER_DOOR_LEAF.get())) {
                leaf.add(pos.immutable());
            }
        }

        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos pos : leaf) {
            for (int a = 0; a < 3; a++) {
                lo[a] = Math.min(lo[a], axis(pos, a));
                hi[a] = Math.max(hi[a], axis(pos, a));
            }
        }
        int normal = 0;
        for (int a = 1; a < 3; a++) {
            if (hi[a] - lo[a] < hi[normal] - lo[normal]) {
                normal = a;
            }
        }
        uAxis = normal == 0 ? 1 : 0;
        vAxis = normal == 2 ? 1 : 2;

        maxRadius = 1.0;
        for (BlockPos pos : leaf) {
            maxRadius = Math.max(maxRadius, radiusOf(pos));
        }
        // Spiral order: angle, plus a whole turn for every fraction of the radius. Sorting by angle
        // alone strips the door in flat wedges like a cut cake; the radius term makes the same blocks
        // leave as one continuous curve running from the rim inward.
        leaf.sort(Comparator.comparingDouble(pos ->
                angleOf(pos) + 2.0 * Math.PI * SPIRAL_TURNS * (1.0 - radiusOf(pos) / maxRadius)));
        bolts.sort(Comparator.comparingDouble(this::angleOf));
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

    /** The snake itself: a bright head with a tail of dimmer segments weaving behind it. */
    private void drawSerpent(ServerLevel level, double angle, double radius) {
        Vec3 head = at(angle, radius);
        level.sendParticles(MagicParticles.MOTE_GREEN, head.x, head.y, head.z, 4, 0.08, 0.08, 0.08, 0.005);
        level.sendParticles(MagicParticles.MOTE_GREEN, head.x, head.y, head.z, 6, 0.15, 0.15, 0.15, 0.02);
        for (int seg = 1; seg <= 7; seg++) {
            // The tail lags in angle and weaves in and out across the rim, so the body reads as a
            // snake following its head rather than as a comet's straight streak.
            Vec3 body = at(angle - seg * 0.17, radius + 0.5 * Math.sin(seg * 0.9 - angle * 2.0));
            level.sendParticles(MagicParticles.MOTE_GREEN, body.x, body.y, body.z,
                    seg < 4 ? 2 : 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChamberDoorBlockEntity door) {
        if (door.ticks < 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        if (!door.surveyed) {
            door.survey(server);
        }
        door.ticks++;

        // Beat one: the snake runs the rim, and each bolt draws back as the head arrives at it.
        if (door.ticks <= RIM_TICKS) {
            double angle = -Math.PI + (double) door.ticks / RIM_TICKS * 2.0 * Math.PI;
            door.drawSerpent(server, angle, door.maxRadius);
            if (door.ticks % 9 == 0) {
                server.playSound(null, pos, SoundEvents.SNIFFER_HAPPY, SoundSource.BLOCKS, 0.35F, 2.0F);
            }
            while (door.boltsTaken < door.bolts.size()
                    && door.angleOf(door.bolts.get(door.boltsTaken)) <= angle) {
                BlockPos bolt = door.bolts.get(door.boltsTaken++);
                door.dissolve(server, bolt);
                server.sendParticles(MagicParticles.SPARK_GREEN, bolt.getX() + 0.5, bolt.getY() + 0.5,
                        bolt.getZ() + 0.5, 14, 0.25, 0.25, 0.25, 0.12);
                server.playSound(null, bolt, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.55F);
                server.playSound(null, bolt, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 0.5F, 1.7F);
            }
            return;
        }

        // Beat two: the leaf unwinds, the snake still leading the stone that is leaving.
        if (door.leafTaken < door.leaf.size()) {
            if (door.leafTaken == 0) {
                server.playSound(null, pos, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.6F, 0.4F);
            }
            BlockPos lead = door.leaf.get(door.leafTaken);
            door.drawSerpent(server, door.angleOf(lead), door.radiusOf(lead));
            for (int i = 0; i < LEAF_PER_TICK && door.leafTaken < door.leaf.size(); i++) {
                door.dissolve(server, door.leaf.get(door.leafTaken++));
            }
            if (door.leafTaken % 12 < LEAF_PER_TICK) {
                server.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.1F, 0.5F);
            }
            door.setChanged();
            return;
        }

        // Beat three: the lock. It sits dead centre so a spell can reach it, which means leaving it
        // behind would strand one block floating in an open doorway. Removing the block discards this
        // block entity, so nothing may touch `door` after this.
        door.ticks = -1;
        server.sendParticles(MagicParticles.MOTE_GREEN, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                40, 0.6, 0.6, 0.6, 0.05);
        server.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5F, 0.45F);
        server.playSound(null, pos, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 1.4F, 0.85F);
        door.dissolve(server, pos);
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
