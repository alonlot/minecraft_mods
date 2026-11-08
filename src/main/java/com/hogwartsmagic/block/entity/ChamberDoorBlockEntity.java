package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.util.MagicParticles;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.ChamberDoorBlock;
import com.hogwartsmagic.block.ChamberDoorLeafBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the Chamber door, once, as the film does it: <b>the stone moves</b>, it does not crumble.
 *
 * <p>Five beats, about nine seconds:
 *
 * <ol>
 *   <li><b>Awaken</b> - the Parseltongue lands. The lock lights green and the whole leaf trembles in
 *       its frame. Nothing has moved yet, and that is the wind-up.</li>
 *   <li><b>The serpent</b> - a stone serpent slides out and runs one full circuit of the rim, then
 *       spirals in and goes into the lock. Each of the seven serpent bolts screws back into the door
 *       the moment the head reaches it, so the bolts retract in sequence round the ring and
 *       something visibly causes each one.</li>
 *   <li><b>Unseat</b> - the leaf rocks in its frame, breaking a seal a thousand years old, and grit
 *       pours off the seam. It sinks a hair back from the face of the wall as it comes loose.</li>
 *   <li><b>Roll</b> - the round leaf rolls sideways into the wall like a millstone, turning as it
 *       goes, until it is gone into the stone and the doorway stands open.</li>
 * </ol>
 *
 * <p>Rolling rather than swinging is a measured choice. A hinge has to sit at the edge of the hole,
 * and in the Chamber that edge is over the causeway: a door swung open on it stood across two of the
 * seven causeway columns, as drawn stone with no collision. Rolled into the bulkhead the leaf is
 * hidden by solid wall on both sides, and once it is in there is nothing left to draw.
 *
 * <h2>How a block moves</h2>
 *
 * <p>It does not. The whole animation is drawn by {@link com.hogwartsmagic.client.ChamberDoorRenderer},
 * which is free to put geometry anywhere. While that happens the real leaf sits in
 * {@code PHASE = MOVING} (invisible, still solid, so nobody walks through a door that has not opened)
 * and at the end in {@code PARTED} (invisible, no collision). The blocks are kept rather than removed
 * because this block entity lives in the lock: deleting the door would delete the thing running it. The lightning rods that stand for the
 * bolt heads are vanilla blocks and cannot go invisible, so they are taken out of the world at the
 * start and drawn by the renderer from then on; the blackstone sockets behind them become leaf
 * blocks, so they leave with the door instead of floating in the doorway after it.
 *
 * <p>The client is sent the shape of the door once, when the sequence starts, and then counts along
 * on its own, with a correction at each beat boundary. Every pose is a pure function of the tick
 * count, so a correction never makes the stone jump.
 *
 * <p>The door's plane is measured, never assumed. The jigsaw may place the Chamber at any of four
 * rotations; the axis the leaf is thinnest along is the one it faces. Which way is "into the hall" is
 * measured too: the lock sits in the leaf's front layer, so the leaf's bulk lies on the hall side of
 * it. That is what tells the bolts which way to point and the serpent which face to run on.
 */
public class ChamberDoorBlockEntity extends BlockEntity {
    public static final int AWAKEN = 24;
    public static final int RIM = 72;
    public static final int UNSEAT = 24;
    public static final int ROLL = 70;
    public static final int TOTAL = AWAKEN + RIM + UNSEAT + ROLL;

    /** Where the serpent starts: the top of the door. It runs clockwise from there. */
    public static final double SERPENT_START = Math.PI / 2.0;
    /** Body segments behind the head, and the angle between two of them on the rim. */
    public static final int SERPENT_SEGMENTS = 18;
    public static final double SERPENT_SPACING = 0.11;
    /** One circuit of the rim, then half a turn spiralling into the lock. */
    public static final double SERPENT_PATH = 3.0 * Math.PI;
    /** The head goes this far, so that the tail has gone into the lock too by the end of the beat. */
    public static final double SERPENT_RUN = SERPENT_PATH + SERPENT_SEGMENTS * SERPENT_SPACING;
    /** Ticks a bolt takes to screw back into the door. */
    public static final int BOLT_TICKS = 10;
    /** How far past its own width the leaf rolls, so its last edge is well inside the wall. */
    public static final double ROLL_EXTRA = 0.5;

    private static final int SEARCH = 7;
    private static final String TICKS_KEY = "OpenTicks";
    private static final String LEAF_KEY = "Leaf";
    private static final String STUDS_KEY = "Studs";
    private static final String RODS_KEY = "Rods";
    private static final String AXES_KEY = "Axes";
    private static final String RADIUS_KEY = "Radius";

    private int ticks = -1;                         // -1 shut, 0..TOTAL opening, TOTAL open
    private final List<BlockPos> leaf = new ArrayList<>();
    private final List<BlockPos> studs = new ArrayList<>();
    private final List<BlockPos> rods = new ArrayList<>();
    private int boltsFired;
    private boolean legacyChecked;                  // not saved: one re-survey per load at most

    private int uAxis = 0, vAxis = 1;               // the two axes the door's plane is spanned by
    private int inward = 1;                         // +1 or -1 along the normal axis: toward the hall
    private double maxRadius = 1.0;

    public ChamberDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.CHAMBER_DOOR_BLOCK_ENTITY.get(), pos, state);
    }

    // --- what the renderer reads -----------------------------------------------------------

    public int ticks() {
        return ticks;
    }

    public List<BlockPos> leafPositions() {
        return leaf;
    }

    public List<BlockPos> studPositions() {
        return studs;
    }

    public List<BlockPos> rodPositions() {
        return rods;
    }

    public int uAxis() {
        return uAxis;
    }

    public int vAxis() {
        return vAxis;
    }

    public int normalAxis() {
        return 3 - uAxis - vAxis;
    }

    /** +1 or -1: which way along the normal axis the hall lies. */
    public int inward() {
        return inward;
    }

    public double maxRadius() {
        return maxRadius;
    }

    /** The radius the serpent runs at: over the outer edge of the leaf, outside the bolts. */
    public double serpentRadius() {
        return Math.max(1.0, maxRadius - 0.1);
    }

    /** The direction the bolt heads point: out of the door, toward the antechamber. */
    public Direction outward() {
        Direction.Axis a = normalAxis() == 0 ? Direction.Axis.X : Direction.Axis.Z;
        return Direction.fromAxisAndDirection(a, inward > 0 ? Direction.AxisDirection.NEGATIVE
                : Direction.AxisDirection.POSITIVE);
    }

    public double planeU(BlockPos p) {
        return axis(p, uAxis) - axis(worldPosition, uAxis);
    }

    public double planeV(BlockPos p) {
        return axis(p, vAxis) - axis(worldPosition, vAxis);
    }

    /** Offset along the normal axis, positive toward the hall. */
    public double depth(BlockPos p) {
        return (axis(p, normalAxis()) - axis(worldPosition, normalAxis())) * (double) inward;
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The leaf ends up well outside its own cells, and a block entity is culled against this box.
        return new AABB(worldPosition).inflate(SEARCH + 12);
    }

    // --- the serpent's path, shared by the server's particles and the client's geometry -------

    /** How far along its path the head is, in radians, at tick {@code t} of the whole sequence. */
    public static double headAt(double t) {
        return (t - AWAKEN) / RIM * SERPENT_RUN;
    }

    /**
     * A point on the serpent's path, in the door's plane: {u, v}. A full circle at the rim, then a
     * half-turn spiral from the rim into the lock.
     */
    public static double[] pathPoint(double theta, double rimRadius) {
        double r = theta <= 2.0 * Math.PI ? rimRadius
                : rimRadius * Math.max(0.0, 1.0 - (theta - 2.0 * Math.PI) / Math.PI);
        double a = SERPENT_START - theta;
        return new double[]{r * Math.cos(a), r * Math.sin(a)};
    }

    /** How far round the rim, from the serpent's start, a cell is. */
    public double pathAngleOf(BlockPos p) {
        double a = SERPENT_START - Math.atan2(planeV(p), planeU(p));
        a %= 2.0 * Math.PI;
        return a < 0 ? a + 2.0 * Math.PI : a;
    }

    /** The tick a bolt starts to draw back: the tick the serpent's head reaches it. */
    public double boltFireTick(BlockPos rod) {
        return AWAKEN + pathAngleOf(rod) / SERPENT_RUN * RIM;
    }

    // --- starting --------------------------------------------------------------------------

    public boolean beginOpening(ServerLevel level) {
        BlockState state = getBlockState();
        if (ticks >= 0 || state.getValue(ChamberDoorBlock.OPEN)
                || state.getValue(ChamberDoorBlock.PHASE) != ChamberDoorLeafBlock.SHUT) {
            return false;
        }
        survey(level);
        ticks = 0;
        boltsFired = 0;
        handOver(level);
        level.playSound(null, worldPosition, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 1.6F, 0.7F);
        sync(level);
        return true;
    }

    /**
     * Gives the whole door to the renderer: every cell invisible and still solid, the bolt heads out
     * of the world, their sockets turned into leaf so they travel with it.
     */
    private void handOver(ServerLevel level) {
        BlockState moving = ExampleMod.CHAMBER_DOOR_LEAF.get().defaultBlockState()
                .setValue(ChamberDoorLeafBlock.PHASE, ChamberDoorLeafBlock.MOVING);
        for (BlockPos pos : rods) {
            if (level.getBlockState(pos).is(Blocks.LIGHTNING_ROD)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos pos : studs) {
            level.setBlock(pos, moving, Block.UPDATE_ALL);
        }
        for (BlockPos pos : leaf) {
            setPhase(level, pos, ChamberDoorLeafBlock.MOVING);
        }
        level.setBlock(worldPosition, getBlockState().setValue(ChamberDoorBlock.OPEN, Boolean.TRUE)
                .setValue(ChamberDoorBlock.PHASE, ChamberDoorLeafBlock.MOVING), Block.UPDATE_ALL);
    }

    private static void setPhase(ServerLevel level, BlockPos pos, int phase) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(ChamberDoorLeafBlock.PHASE) && state.getValue(ChamberDoorLeafBlock.PHASE) != phase) {
            level.setBlock(pos, state.setValue(ChamberDoorLeafBlock.PHASE, phase), Block.UPDATE_ALL);
        }
    }

    /** Pushes the whole animation state to everyone watching. Called at beat boundaries only. */
    private void sync(ServerLevel level) {
        setChanged();
        BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private static int axis(BlockPos p, int a) {
        return a == 0 ? p.getX() : a == 1 ? p.getY() : p.getZ();
    }

    private void survey(ServerLevel level) {
        leaf.clear();
        studs.clear();
        rods.clear();
        List<BlockPos> rodCandidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-SEARCH, -SEARCH, -SEARCH),
                                                   worldPosition.offset(SEARCH, SEARCH, SEARCH))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LIGHTNING_ROD)) {
                rodCandidates.add(pos.immutable());
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
        // The door stands in a wall, never in a floor: it swings on a vertical hinge, so its normal
        // has to be one of the horizontals.
        if (normal == 1) {
            normal = (hi[0] - lo[0]) <= (hi[2] - lo[2]) ? 0 : 2;
        }
        uAxis = normal == 0 ? 1 : 0;
        vAxis = normal == 2 ? 1 : 2;

        // The lock is in the front layer, so the bulk of the leaf lies behind it, on the hall side.
        double sum = 0.0;
        for (BlockPos pos : leaf) {
            sum += axis(pos, normal) - axis(worldPosition, normal);
        }
        inward = sum >= 0.0 ? 1 : -1;

        maxRadius = 1.0;
        for (BlockPos pos : leaf) {
            maxRadius = Math.max(maxRadius, Math.hypot(planeU(pos), planeV(pos)));
        }

        // A bolt is a rod standing one block proud of the front face, in front of a blackstone socket.
        for (BlockPos rod : rodCandidates) {
            if (depth(rod) != -1.0 || Math.hypot(planeU(rod), planeV(rod)) > maxRadius + 0.5) {
                continue;
            }
            rods.add(rod);
            BlockPos socket = rod.relative(outward().getOpposite());
            if (level.getBlockState(socket).is(Blocks.POLISHED_BLACKSTONE)) {
                studs.add(socket);
            }
        }
        rods.sort((a, b) -> Double.compare(pathAngleOf(a), pathAngleOf(b)));
    }

    // --- ticking ---------------------------------------------------------------------------

    /**
     * The client's whole job: count. It is corrected at every beat boundary, and the pose is a pure
     * function of the count, so it never has to carry the animation more than a beat on its own.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ChamberDoorBlockEntity door) {
        if (door.ticks >= 0 && door.ticks < TOTAL) {
            door.ticks++;
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ChamberDoorBlockEntity door) {
        if (door.ticks < 0 || door.ticks >= TOTAL || !(level instanceof ServerLevel server)) {
            return;
        }
        if (door.leaf.isEmpty() && !door.legacyChecked) {
            door.legacyChecked = true;
            // A door caught mid-way by an older version of the mod, which kept no record of its
            // shape: measure what is left of it and carry on from there.
            door.survey(server);
            door.handOver(server);
            door.sync(server);
        }
        int t = door.ticks++;

        if (t == AWAKEN || t == AWAKEN + RIM || t == AWAKEN + RIM + UNSEAT) {
            door.sync(server);
        }
        if (t < AWAKEN) {
            door.beatAwaken(server, t);
        } else if (t < AWAKEN + RIM) {
            door.beatSerpent(server, t);
        } else if (t < AWAKEN + RIM + UNSEAT) {
            door.beatUnseat(server, t - AWAKEN - RIM);
        } else if (t < TOTAL - 1) {
            door.beatRoll(server, t - AWAKEN - RIM - UNSEAT);
        } else {
            door.finish(server);
        }
    }

    private Vec3 at(double u, double v, double depth) {
        double[] c = {worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5};
        c[uAxis] += u;
        c[vAxis] += v;
        c[normalAxis()] += depth * inward;
        return new Vec3(c[0], c[1], c[2]);
    }

    /** Green motes drawn in to the lock from all round the face: the door is listening. */
    private void beatAwaken(ServerLevel level, int t) {
        double a = t * 0.9;
        double r = maxRadius * (1.0 - t / (double) AWAKEN);
        Vec3 p = at(r * Math.cos(a), r * Math.sin(a), -0.7);
        level.sendParticles(MagicParticles.MOTE_GREEN, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        if (t % 6 == 0) {
            Vec3 lock = at(0, 0, -0.6);
            level.sendParticles(MagicParticles.SMOKE_GREEN, lock.x, lock.y, lock.z, 2, 0.2, 0.2, 0.2, 0.005);
        }
    }

    /** The serpent runs the rim; each bolt clanks back as the head reaches it. */
    private void beatSerpent(ServerLevel level, int t) {
        double theta = headAt(t);
        if (theta <= SERPENT_PATH) {
            double[] head = pathPoint(theta, serpentRadius());
            Vec3 p = at(head[0], head[1], -0.75);
            level.sendParticles(MagicParticles.MOTE_GREEN, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.004);
            if (t % 3 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                        p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.0);
            }
        }
        if (t % 9 == 0 && theta <= SERPENT_PATH) {
            level.playSound(null, worldPosition, SoundEvents.SNIFFER_HAPPY, SoundSource.BLOCKS, 0.35F, 2.0F);
        }
        while (boltsFired < rods.size() && boltFireTick(rods.get(boltsFired)) <= t) {
            BlockPos rod = rods.get(boltsFired++);
            Vec3 p = Vec3.atCenterOf(rod);
            level.sendParticles(MagicParticles.SPARK_GREEN, p.x, p.y, p.z, 14, 0.25, 0.25, 0.25, 0.12);
            level.sendParticles(MagicParticles.SMOKE_GREY, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0.01);
            // Seven identical clanks are a rattle. Climbing, they are a sequence.
            level.playSound(null, rod, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F,
                    0.5F + 0.06F * boltsFired);
            level.playSound(null, rod, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 0.45F, 1.7F);
        }
        if (t == AWAKEN + RIM - 1) {
            // The tail has gone into the lock. Every bolt is drawn: the door is unlocked.
            Vec3 lock = at(0, 0, -0.6);
            level.sendParticles(MagicParticles.MOTE_GREEN, lock.x, lock.y, lock.z, 30, 0.3, 0.3, 0.3, 0.06);
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.4F, 0.45F);
            level.playSound(null, worldPosition, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 1.3F, 0.85F);
        }
    }

    /** The leaf rocks loose in its frame, and grit runs off the seam. */
    private void beatUnseat(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, worldPosition, ModSounds.VAULT_DOOR_COGS.get(), SoundSource.BLOCKS, 1.6F, 0.6F);
            level.playSound(null, worldPosition, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.6F, 0.4F);
        }
        seamGrit(level, t, 3);
        if (t % 8 == 0) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.45F);
        }
    }

    /** Grit pouring off the seam between leaf and frame, scattered evenly round it. */
    private void seamGrit(ServerLevel level, int t, int count) {
        BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.TUFF.defaultBlockState());
        for (int i = 0; i < count; i++) {
            double a = (t * count + i) * 2.399;     // the golden angle: an even scatter
            double r = maxRadius + 0.5;
            Vec3 p = at(r * Math.cos(a), r * Math.sin(a), -0.55 + level.random.nextDouble() * 3.1);
            level.sendParticles(grit, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /**
     * The leaf rolls into the wall. Stone grinds where its edge goes into the frame, and the air of a
     * room shut a thousand years comes out through the widening gap.
     */
    private void beatRoll(ServerLevel level, int t) {
        if (t == 0) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.6F, 0.4F);
        }
        seamGrit(level, t, 2);
        if (t % 7 == 0) {
            level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.2F, 0.5F);
        }
        Vec3 mouth = at(0, 0, 1.0);
        level.sendParticles(MagicParticles.SMOKE_GREEN, mouth.x, mouth.y - 1.5, mouth.z, 2,
                maxRadius * 0.3, 0.3, maxRadius * 0.3, 0.004);
        if (t % 5 == 0) {
            level.sendParticles(MagicParticles.SMOKE_DARK, mouth.x, mouth.y, mouth.z, 2,
                    maxRadius * 0.4, maxRadius * 0.4, maxRadius * 0.4, 0.01);
        }
        if (t == ROLL - 6) {
            // It comes to rest inside the wall with weight.
            level.playSound(null, worldPosition, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.4F, 0.35F);
        }
    }

    private void finish(ServerLevel level) {
        for (BlockPos pos : leaf) {
            setPhase(level, pos, ChamberDoorLeafBlock.PARTED);
        }
        for (BlockPos pos : studs) {
            setPhase(level, pos, ChamberDoorLeafBlock.PARTED);
        }
        level.setBlock(worldPosition, level.getBlockState(worldPosition)
                .setValue(ChamberDoorBlock.PHASE, ChamberDoorLeafBlock.PARTED), Block.UPDATE_ALL);
        ticks = TOTAL;
        level.playSound(null, worldPosition, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 0.9F, 0.6F);
        sync(level);
    }

    // --- persistence and sync --------------------------------------------------------------

    private static long[] pack(List<BlockPos> cells) {
        long[] out = new long[cells.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = cells.get(i).asLong();
        }
        return out;
    }

    private static void unpack(long[] packed, List<BlockPos> into) {
        into.clear();
        for (long p : packed) {
            into.add(BlockPos.of(p));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TICKS_KEY, ticks);
        tag.putLongArray(LEAF_KEY, pack(leaf));
        tag.putLongArray(STUDS_KEY, pack(studs));
        tag.putLongArray(RODS_KEY, pack(rods));
        tag.putIntArray(AXES_KEY, new int[]{uAxis, vAxis, inward});
        tag.putDouble(RADIUS_KEY, maxRadius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ticks = tag.contains(TICKS_KEY) ? tag.getInt(TICKS_KEY) : -1;
        unpack(tag.getLongArray(LEAF_KEY), leaf);
        unpack(tag.getLongArray(STUDS_KEY), studs);
        unpack(tag.getLongArray(RODS_KEY), rods);
        int[] axes = tag.getIntArray(AXES_KEY);
        if (axes.length == 3) {
            uAxis = axes[0];
            vAxis = axes[1];
            inward = axes[2] >= 0 ? 1 : -1;
        }
        maxRadius = Math.max(1.0, tag.getDouble(RADIUS_KEY));
        // New data, so a door with no record of its shape may be measured again. A block entity can
        // outlive its data: a structure placed over a door of the same block keeps this object.
        legacyChecked = false;
        // Bolts already past the head are not fired twice: count the ones the clock has gone by.
        boltsFired = 0;
        while (boltsFired < rods.size() && boltFireTick(rods.get(boltsFired)) < ticks) {
            boltsFired++;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
