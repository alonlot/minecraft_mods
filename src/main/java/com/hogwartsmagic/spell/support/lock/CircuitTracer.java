package com.hogwartsmagic.spell.support.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the redstone around a piston door into a LockCircuit: the door pistons are its goals, levers,
 * buttons, forceable sources and item keys its inputs, dispensers and TNT its dangers. Server thread only;
 * never loads a chunk.
 */
public final class CircuitTracer {
    /** forceSources: Alohomora Master. itemKeys: Revelio Master. */
    public record Mode(boolean forceSources, boolean itemKeys) {
    }

    /** One input in the world. level: its strength now. slots: a container's size. frame: an item frame. */
    public record InputSite(BlockPos pos, Block block, boolean on, int kind, int level, int source, int slots,
                            @Nullable UUID frame) {
        public static final int BLOCK = 0;
        public static final int CONTAINER = 1;
        public static final int FRAME = 2;
        public static final int OTHER = 3;
    }

    public record TracedLock(LockCircuit circuit, List<InputSite> inputs, int pistons, int blocks) {
    }

    private static final int[] FRAME_LEVELS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] ALL_LEVELS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    private final ServerLevel level;
    private final int cap;
    private final Mode mode;
    private final Map<BlockPos, BlockState> region = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> core = new HashMap<>();
    private final Map<BlockPos, Integer> out = new HashMap<>();
    private final Map<BlockPos, Integer> strong = new HashMap<>();
    private final Map<BlockPos, Integer> weak = new HashMap<>();
    private final LockCircuit.Builder b = new LockCircuit.Builder();
    private final List<InputSite> sites = new ArrayList<>();
    private int forced;
    private int itemKeys;

    private CircuitTracer(ServerLevel level, int cap, Mode mode) {
        this.level = level;
        this.cap = cap;
        this.mode = mode;
    }

    @Nullable
    public static TracedLock trace(ServerLevel level, BlockPos hit, int blockCap, Mode mode) {
        List<BlockPos> pistons = doorPistons(level, hit);
        if (pistons.isEmpty()) {
            return null;
        }
        CircuitTracer t = new CircuitTracer(level, blockCap, mode);
        t.collect(pistons);
        t.createNodes(new HashSet<>(pistons));
        t.wire();
        return new TracedLock(t.b.build(), List.copyOf(t.sites), pistons.size(), t.region.size());
    }

    /** Pressed, flipped, powered: the state a lever or button is in. */
    public static boolean isOn(BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return state.getValue(BlockStateProperties.POWERED);
        }
        return state.hasProperty(BlockStateProperties.POWER) && state.getValue(BlockStateProperties.POWER) > 0;
    }

    private static List<BlockPos> doorPistons(ServerLevel level, BlockPos hit) {
        int r = LockRules.DOOR_RADIUS;
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(hit.offset(-r, -r, -r), hit.offset(r, r, r))) {
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof PistonBaseBlock) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(hit)));
        return found.size() > LockRules.MAX_DOOR_PISTONS ? found.subList(0, LockRules.MAX_DOOR_PISTONS) : found;
    }

    // ---- classification ----

    private static boolean isComponent(BlockState s) {
        Block block = s.getBlock();
        return block instanceof RedStoneWireBlock || block instanceof RedstoneTorchBlock || block instanceof DiodeBlock
                || block instanceof ObserverBlock || block instanceof LeverBlock || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock || block instanceof SculkSensorBlock
                || s.is(Blocks.REDSTONE_BLOCK) || s.isSignalSource();
    }

    private static boolean isSink(BlockState s) {
        Block block = s.getBlock();
        return block instanceof PistonBaseBlock || block instanceof DispenserBlock || block instanceof TntBlock;
    }

    private boolean conductorAt(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState s = level.getBlockState(pos);
        // Dispensers and droppers conduct power like any solid block, as well as being sinks.
        return !isComponent(s) && (!isSink(s) || s.getBlock() instanceof DispenserBlock)
                && s.isRedstoneConductor(level, pos);
    }

    /**
     * Pistons used as logic: a piston pushing a redstone block or observer (a door piston's front, a
     * non-door piston's front, or two ahead when extended), or slime or honey touching a non-door piston.
     * A redstone block or observer merely beside a piston's back or side is normal wiring.
     */
    private boolean pistonLogic(BlockPos pos, BlockState piston, boolean door) {
        Direction facing = piston.getValue(PistonBaseBlock.FACING);
        if (pushesLogicBlock(pos.relative(facing))) {
            return true;
        }
        if (door) {
            return false;
        }
        if (piston.getValue(PistonBaseBlock.EXTENDED) && pushesLogicBlock(pos.relative(facing, 2))) {
            return true;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n)) {
                BlockState ns = level.getBlockState(n);
                if (ns.is(Blocks.SLIME_BLOCK) || ns.is(Blocks.HONEY_BLOCK)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean pushesLogicBlock(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState s = level.getBlockState(pos);
        return s.is(Blocks.REDSTONE_BLOCK) || s.getBlock() instanceof ObserverBlock;
    }

    // ---- 1. region ----

    private void collect(List<BlockPos> pistons) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(pistons);
        Set<BlockPos> seen = new HashSet<>(pistons);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                b.markTruncated();
                continue;
            }
            BlockState s = level.getBlockState(pos);
            if (!relevant(pos, s)) {
                continue;
            }
            if (region.size() >= cap) {
                b.markTruncated();
                break;
            }
            region.put(pos, s);
            List<BlockPos> next = new ArrayList<>();
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                next.add(n);
                if (s.getBlock() instanceof RedStoneWireBlock && d.getAxis().isHorizontal()) {
                    next.add(n.above());
                    next.add(n.below());
                }
            }
            if (isSink(s)) {
                BlockPos up = pos.above();
                next.add(up);
                for (Direction d : Direction.values()) {
                    next.add(up.relative(d));
                }
            }
            if (s.getBlock() instanceof ComparatorBlock) {
                next.add(pos.relative(s.getValue(ComparatorBlock.FACING), 2));
            }
            for (BlockPos n : next) {
                BlockPos key = n.immutable();
                if (seen.add(key)) {
                    queue.add(key);
                }
            }
        }
    }

    private boolean relevant(BlockPos pos, BlockState s) {
        if (isComponent(s) || isSink(s)) {
            return true;
        }
        if (!s.isRedstoneConductor(level, pos)) {
            return false;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n)) {
                b.markTruncated();
            } else if (isComponent(level.getBlockState(n))) {
                return true;
            }
        }
        return false;
    }

    // ---- 2. nodes ----

    private void createNodes(Set<BlockPos> doorPistons) {
        for (Map.Entry<BlockPos, BlockState> e : region.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState s = e.getValue();
            Block block = s.getBlock();
            if (isSink(s)) {
                int node = b.node(LockCircuit.MAX, 0); // MAX values are rebuilt by the simulator; no neighbour reads
                put(pos, node, node);
                if (block instanceof DispenserBlock) {
                    int st = b.node(LockCircuit.MAX, 0);
                    int wk = b.node(LockCircuit.MAX, 0);
                    b.link(st, wk, 0);
                    strong.put(pos, st);
                    weak.put(pos, wk);
                }
                if (block instanceof PistonBaseBlock && doorPistons.contains(pos)) {
                    b.goal(node);
                }
                if (block instanceof PistonBaseBlock && pistonLogic(pos, s, doorPistons.contains(pos))) {
                    b.markPistonLogic();
                }
                if (block instanceof DispenserBlock || block instanceof TntBlock) {
                    b.danger(node);
                }
            } else if (block instanceof RedStoneWireBlock) {
                int node = b.node(LockCircuit.MAX, s.getValue(RedStoneWireBlock.POWER));
                put(pos, node, node);
            } else if (block instanceof RedstoneTorchBlock) {
                int node = b.node(LockCircuit.NOT, s.getValue(RedstoneTorchBlock.LIT) ? 15 : 0);
                put(pos, node, node);
            } else if (block instanceof RepeaterBlock) {
                int node = b.node(LockCircuit.REPEATER, s.getValue(DiodeBlock.POWERED) ? 15 : 0);
                put(pos, node, node);
            } else if (block instanceof ComparatorBlock) {
                int now = level.getBlockEntity(pos) instanceof ComparatorBlockEntity cbe ? cbe.getOutputSignal() : 0;
                int kind = s.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT ? LockCircuit.SUBTRACT : LockCircuit.COMPARE;
                int node = b.node(kind, now);
                put(pos, node, forceWrap(pos, block, node, now));
            } else if (block instanceof ObserverBlock) {
                int now = s.getValue(ObserverBlock.POWERED) ? 15 : 0;
                int node = b.node(LockCircuit.OBSERVER, now);
                put(pos, node, forceWrap(pos, block, node, now));
            } else if (block instanceof LeverBlock || block instanceof ButtonBlock
                    || block instanceof BasePressurePlateBlock || block instanceof SculkSensorBlock) {
                int kind = block instanceof LeverBlock ? LockCircuit.LEVER : LockCircuit.PULSE;
                int node = b.input(kind, isOn(s));
                sites.add(new InputSite(pos, block, isOn(s), kind, isOn(s) ? 15 : 0, InputSite.BLOCK, 0, null));
                put(pos, node, node);
            } else if (s.is(Blocks.REDSTONE_BLOCK)) {
                int node = b.constant(15);
                put(pos, node, node);
            } else if (isComponent(s)) {
                int strongest = 0;
                for (Direction d : Direction.values()) {
                    strongest = Math.max(strongest, s.getSignal(level, pos, d));
                }
                int node = b.constant(strongest);
                put(pos, node, forceWrap(pos, block, node, strongest));
            } else {
                int st = b.node(LockCircuit.MAX, 0);
                int wk = b.node(LockCircuit.MAX, 0);
                b.link(st, wk, 0);
                strong.put(pos, st);
                weak.put(pos, wk);
            }
        }
    }

    private void put(BlockPos pos, int coreNode, int outNode) {
        core.put(pos, coreNode);
        out.put(pos, outNode);
    }

    /** At Alohomora Master, a source the spell may force on: its output also reads a FORCE input. */
    private int forceWrap(BlockPos pos, Block block, int node, int initial) {
        if (!mode.forceSources() || forced >= LockRules.MAX_FORCED_SOURCES) {
            return node;
        }
        forced++;
        int force = b.input(LockCircuit.FORCE, false);
        sites.add(new InputSite(pos, block, false, LockCircuit.FORCE, 0, InputSite.BLOCK, 0, null));
        int wrap = b.node(LockCircuit.MAX, initial);
        b.link(node, wrap, 0);
        b.link(force, wrap, 0);
        return wrap;
    }

    // ---- 3. edges ----

    private void wire() {
        for (Map.Entry<BlockPos, BlockState> e : region.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState s = e.getValue();
            Block block = s.getBlock();
            if (strong.containsKey(pos)) {
                wireConductor(pos);
                if (isSink(s)) {
                    wireSink(pos, s); // a dispenser or dropper is both a conductor and a sink
                }
            } else if (block instanceof RedStoneWireBlock) {
                wireDust(pos);
            } else if (block instanceof RedstoneTorchBlock) {
                Direction attach = torchAttach(s);
                feedWeak(pos.relative(attach), attach.getOpposite(), core.get(pos));
            } else if (block instanceof DiodeBlock) {
                wireDiode(pos, s);
            } else if (block instanceof ObserverBlock) {
                BlockPos watchedPos = pos.relative(s.getValue(ObserverBlock.FACING));
                // Block-state changes only: a conductor's power change or a forced source does not fire it.
                Integer watched = core.get(watchedPos);
                if (watched != null) {
                    link(watched, core.get(pos));
                }
            } else if (isSink(s)) {
                wireSink(pos, s);
            }
        }
    }

    private void wireConductor(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = region.get(n);
            if (ns == null) {
                continue;
            }
            Direction toward = d.getOpposite(); // from n toward pos
            int st = strongInto(n, ns, toward);
            if (st >= 0) {
                link(st, strong.get(pos));
            }
            if (ns.getBlock() instanceof RedStoneWireBlock && dustPoints(ns, toward)) {
                link(out.get(n), weak.get(pos));
            }
        }
    }

    private void wireDust(BlockPos pos) {
        int dust = core.get(pos);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = region.get(n);
            if (ns == null) {
                continue;
            }
            if (strong.containsKey(n)) {
                link(strong.get(n), dust);
            } else if (!(ns.getBlock() instanceof RedStoneWireBlock)) {
                int direct = direct(n, ns, d.getOpposite());
                if (direct >= 0) {
                    link(direct, dust);
                }
            }
        }
        boolean roofed = conductorAt(pos.above());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            dustLink(n, dust);
            if (!roofed && conductorAt(n)) {
                dustLink(n.above(), dust);
            }
            if (!conductorAt(n)) {
                dustLink(n.below(), dust);
            }
        }
    }

    private void dustLink(BlockPos from, int dust) {
        BlockState s = region.get(from);
        if (s != null && s.getBlock() instanceof RedStoneWireBlock && out.get(from) != dust) {
            b.link(out.get(from), dust, 1);
        }
    }

    private void wireDiode(BlockPos pos, BlockState s) {
        int node = core.get(pos);
        Direction in = s.getValue(DiodeBlock.FACING);
        BlockPos back = pos.relative(in);
        boolean comparator = s.getBlock() instanceof ComparatorBlock;
        int key = comparator ? itemKey(back, in) : -1;
        if (key >= 0) {
            link(key, node);
        } else {
            BlockState bs = region.get(back);
            if (bs != null && bs.getBlock() instanceof RedStoneWireBlock) {
                link(out.get(back), node);
            } else {
                feedWeak(back, in.getOpposite(), node);
            }
        }
        for (Direction sd : new Direction[] {in.getClockWise(), in.getCounterClockWise()}) {
            BlockPos n = pos.relative(sd);
            BlockState ns = region.get(n);
            if (ns == null) {
                continue;
            }
            Direction toward = sd.getOpposite();
            int src = -1;
            if (ns.getBlock() instanceof DiodeBlock) {
                src = direct(n, ns, toward);
            } else if (comparator && ns.getBlock() instanceof RedStoneWireBlock) {
                src = out.get(n);
            } else if (comparator && !strong.containsKey(n)) {
                // Vanilla getControlInputSignal: 15 from a redstone block, else the source's strong signal.
                src = ns.is(Blocks.REDSTONE_BLOCK) && out.containsKey(n) ? out.get(n) : strongInto(n, ns, toward);
            }
            if (src >= 0 && src != node) {
                b.side(src, node, 0);
            }
        }
    }

    /** The node a comparator reads from a container, frame or other analog block behind it, or -1. */
    private int itemKey(BlockPos back, Direction in) {
        if (!level.isLoaded(back)) {
            return -1;
        }
        BlockState bs = level.getBlockState(back);
        if (bs.hasAnalogOutputSignal()) {
            return analogBlock(back, bs);
        }
        if (!bs.isRedstoneConductor(level, back)) {
            return -1;
        }
        BlockPos further = back.relative(in);
        if (!level.isLoaded(further)) {
            return -1;
        }
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(further), f -> f.getDirection() == in);
        // Vanilla ignores the frames when more than one faces the comparator.
        int frameNode = -1;
        if (frames.size() == 1) {
            ItemFrame frame = frames.get(0);
            int now = frame.getAnalogOutput();
            if (!takeItemKey()) {
                frameNode = b.constant(now);
            } else {
                frameNode = b.analog(now, FRAME_LEVELS);
                sites.add(new InputSite(further, Blocks.AIR, now > 0, LockCircuit.ANALOG, now, InputSite.FRAME, 0, frame.getUUID()));
            }
        }
        BlockState fs = level.getBlockState(further);
        int blockNode = fs.hasAnalogOutputSignal() ? analogBlock(further, fs) : -1;
        int reading;
        if (frameNode >= 0 && blockNode >= 0) {
            reading = b.node(LockCircuit.MAX, 0);
            b.link(frameNode, reading, 0);
            b.link(blockNode, reading, 0);
        } else {
            reading = frameNode >= 0 ? frameNode : blockNode;
        }
        Integer conductor = weak.get(back);
        if (reading < 0 || conductor == null) {
            return reading;
        }
        // Vanilla reads past the conductor only while the conductor's own signal is below 15:
        // out = (conductor >= 15) ? conductor : reading.
        int gate = b.node(LockCircuit.GATE15, 0);
        b.link(reading, gate, 0);
        b.side(conductor, gate, 0);
        return gate;
    }

    private int analogBlock(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && !level.isLoaded(pos.relative(ChestBlock.getConnectedDirection(state)))) {
            b.markTruncated();
            return -1;
        }
        int now = state.getAnalogOutputSignal(level, pos);
        if (!takeItemKey()) {
            return b.constant(now);
        }
        int slots = containerSize(pos, state);
        int node = b.analog(now, ALL_LEVELS);
        sites.add(new InputSite(pos, state.getBlock(), now > 0, LockCircuit.ANALOG, now,
                slots > 0 ? InputSite.CONTAINER : InputSite.OTHER, slots, null));
        return node;
    }

    /** True if this item key may become an input; otherwise the lock is marked keyed. */
    private boolean takeItemKey() {
        if (mode.itemKeys() && itemKeys < LockRules.MAX_ITEM_KEYS) {
            itemKeys++;
            return true;
        }
        b.markKeyed();
        return false;
    }

    private int containerSize(BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ChiseledBookShelfBlock || block instanceof JukeboxBlock || block instanceof CrafterBlock) {
            return 0; // their signal is not a fullness a player can set by filling slots: OTHER
        }
        if (state.getBlock() instanceof ChestBlock chest) {
            Container c = ChestBlock.getContainer(chest, state, level, pos, true);
            return c == null ? 0 : c.getContainerSize();
        }
        return level.getBlockEntity(pos) instanceof Container c ? c.getContainerSize() : 0;
    }

    private void wireSink(BlockPos pos, BlockState s) {
        int node = core.get(pos);
        boolean piston = s.getBlock() instanceof PistonBaseBlock;
        Direction front = piston ? s.getValue(PistonBaseBlock.FACING) : null;
        for (Direction d : Direction.values()) {
            if (d != front) {
                feedWeak(pos.relative(d), d.getOpposite(), node);
            }
        }
        if (piston || s.getBlock() instanceof DispenserBlock) {
            BlockPos up = pos.above();
            for (Direction d : Direction.values()) {
                if (d != Direction.DOWN || !piston) {
                    feedWeak(up.relative(d), d.getOpposite(), node);
                }
            }
        }
    }

    // ---- signal helpers ----

    /** Link into receiver whatever block n sends toward n.relative(toward). */
    private void feedWeak(BlockPos n, Direction toward, int receiver) {
        Integer wk = weak.get(n);
        if (wk != null) {
            link(wk, receiver);
            return;
        }
        BlockState s = region.get(n);
        if (s == null) {
            return;
        }
        if (s.getBlock() instanceof RedStoneWireBlock) {
            if (dustPoints(s, toward)) {
                link(out.get(n), receiver);
            }
            return;
        }
        int d = direct(n, s, toward);
        if (d >= 0) {
            link(d, receiver);
        }
    }

    /** The out node of a component at n that powers its neighbour in direction toward, or -1. */
    private int direct(BlockPos n, BlockState s, Direction toward) {
        Integer node = out.get(n);
        if (node == null || isSink(s) || s.getBlock() instanceof RedStoneWireBlock) {
            return -1;
        }
        Block block = s.getBlock();
        if (block instanceof RedstoneTorchBlock) {
            return toward == torchAttach(s) ? -1 : node;
        }
        if (block instanceof DiodeBlock) {
            return toward == s.getValue(DiodeBlock.FACING).getOpposite() ? node : -1;
        }
        if (block instanceof ObserverBlock) {
            return toward == s.getValue(ObserverBlock.FACING).getOpposite() ? node : -1;
        }
        return node; // levers, buttons, plates, sculk sensors, redstone blocks, other sources
    }

    /** The out node that strongly powers the conductor next to n in direction toward, or -1. */
    private int strongInto(BlockPos n, BlockState s, Direction toward) {
        Integer node = out.get(n);
        if (node == null) {
            return -1;
        }
        Block block = s.getBlock();
        if (block instanceof LeverBlock || block instanceof ButtonBlock) {
            return toward == attachOf(s) ? node : -1;
        }
        if (block instanceof BasePressurePlateBlock) {
            return toward == Direction.DOWN ? node : -1;
        }
        if (block instanceof RedstoneTorchBlock) {
            return toward == Direction.UP ? node : -1;
        }
        if (block instanceof DiodeBlock) {
            return toward == s.getValue(DiodeBlock.FACING).getOpposite() ? node : -1;
        }
        if (block instanceof ObserverBlock) {
            return toward == s.getValue(ObserverBlock.FACING).getOpposite() ? node : -1;
        }
        // Other sources: forcing gives them no strong output, so read the unforced core node.
        Integer unforced = core.get(n);
        if (unforced == null) {
            return -1;
        }
        if (block instanceof TrappedChestBlock || block instanceof LecternBlock || block instanceof DetectorRailBlock
                || block instanceof SculkSensorBlock) {
            return toward == Direction.DOWN ? unforced : -1;
        }
        if (block instanceof TripWireHookBlock) {
            return toward == s.getValue(TripWireHookBlock.FACING).getOpposite() ? unforced : -1;
        }
        if (block instanceof LightningRodBlock) {
            return toward == s.getValue(LightningRodBlock.FACING).getOpposite() ? unforced : -1;
        }
        return -1;
    }

    private static boolean dustPoints(BlockState dust, Direction toward) {
        if (toward == Direction.DOWN) {
            return true;
        }
        if (toward == Direction.UP) {
            return false;
        }
        return dust.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(toward)) != RedstoneSide.NONE;
    }

    /** Direction from a lever or button to the block it is mounted on. */
    private static Direction attachOf(BlockState s) {
        AttachFace face = s.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        if (face == AttachFace.FLOOR) {
            return Direction.DOWN;
        }
        if (face == AttachFace.CEILING) {
            return Direction.UP;
        }
        return s.getValue(FaceAttachedHorizontalDirectionalBlock.FACING).getOpposite();
    }

    /** Direction from a torch to the block it hangs on. */
    private static Direction torchAttach(BlockState s) {
        return s.getBlock() instanceof RedstoneWallTorchBlock
                ? s.getValue(RedstoneWallTorchBlock.FACING).getOpposite() : Direction.DOWN;
    }

    private void link(int from, int to) {
        if (from != to) {
            b.link(from, to, 0);
        }
    }
}
