package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.worldgen.MiniHogwartsSchematic;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a scene's set around a region origin a few thousand blocks per tick, and clears it again.
 *
 * <p>Templates, schematics, fills and disks are collected up front (sets are capped at radius 200, so the list is small
 * enough), de-duplicated (a later layer wins), sorted outward from the origin with {@link BuildOrder} and then
 * placed {@code budget} blocks per {@link #advance}. Anything more than {@code radius} blocks from the origin
 * horizontally is dropped, so an oversized template can never spill into a neighbouring region.
 *
 * <p>Features go last. They are placed through a proxy of the level that forces every write to flags
 * {@code 2 | 16}, records every position written into {@link #placed()} and counts the writes against the
 * budget. A feature is only started while budget remains; one feature may overshoot the budget by its own size.
 *
 * <p>All writes use flags {@code 2 | 16}: clients are told, but no neighbour updates and no drops. Those flags do
 * not stop {@code onPlace}: gravel, sand, water and the like still schedule a block or fluid tick for themselves.
 * So every {@link #advance} ends by clearing the scheduled block and fluid ticks over the box it touched
 * ({@link Touched}), and a feature's own {@code scheduleTick} calls are swallowed by the proxy. Nothing in a set
 * ever falls or flows, even where the scene data leaves gravel over air or water beside an empty cell.
 *
 * <p>Templates: blocks only, no entities. Air, structure voids, jigsaws and structure blocks in a template are
 * skipped, so a template never carves holes in what a fill laid down underneath it.
 *
 * <p>Schematics ({@code set.schematics}, placed right after templates, so fills and disks overwrite them): the
 * bundled Sponge schematics of {@link com.hogwartsmagic.pensieve.scene.SceneNames#SCHEMATICS}, loaded once and cached by their own class
 * ({@link MiniHogwartsSchematic}). Each is turned by its rotation (positions and block states, like a rotated
 * structure template), its rotated footprint's min corner at {@code at}. The same cells as a template are skipped,
 * and command blocks too. Anything beyond the radius is cropped like everything else.
 *
 * <p>Chunks: the chunks the set covers are held with a region ticket from the first {@code advance} until it
 * returns true; call {@link #release()} to drop them early if the builder is abandoned (idempotent).
 */
public final class SetBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int FLAGS = 2 | 16;
    static final TicketType<ChunkPos> TICKET = TicketType.create("hogwartsmagic_pensieve",
            Comparator.comparingLong(ChunkPos::toLong));

    private final ServerLevel level;
    private final BlockPos origin;
    private final SceneDef.SetDef set;
    private final int radius;
    private final List<BlockState> states = new ArrayList<>();
    private List<int[]> order;
    private int cursor;
    private int featureCursor;
    private boolean prepared;
    private final List<BlockPos> placed = new ArrayList<>();
    private final Set<Long> placedSet = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private final Tickets tickets;
    private final Touched touched = new Touched();

    public SetBuilder(ServerLevel level, BlockPos origin, SceneDef.SetDef set, int radius) {
        this.level = level;
        this.origin = origin;
        this.set = set;
        this.radius = radius;
        this.tickets = new Tickets(level, origin, radius);
    }

    /** Blocks the template, schematic, fill and disk phase will place (features are extra). */
    public int total() {
        prepare();
        return order.size();
    }

    public List<BlockPos> placed() {
        return placed;
    }

    /** Drops the chunk tickets; also done automatically when {@link #advance} returns true. */
    public void release() {
        tickets.release();
    }

    /** Places roughly {@code budget} blocks; true when the whole set is built. */
    public boolean advance(int budget) {
        return advance(new int[]{budget});
    }

    /**
     * Places blocks against a budget shared with other jobs: {@code left[0]} is decremented per block written
     * (a feature may take it below zero). True when the whole set is built.
     */
    public boolean advance(int[] left) {
        prepare();
        tickets.acquire();
        try {
            while (cursor < order.size() && left[0] > 0) {
                int[] p = order.get(cursor++);
                BlockPos pos = new BlockPos(p[0], p[1], p[2]);
                level.setBlock(pos, states.get(p[3]), FLAGS);
                record(pos);
                left[0]--;
            }
            if (cursor < order.size()) return false;
            List<SceneDef.FeatureRef> features = set.features();
            while (featureCursor < features.size() && left[0] > 0) {
                placeFeature(features.get(featureCursor++), left);
            }
        } finally {
            touched.clearTicks(level);
        }
        boolean done = featureCursor >= set.features().size();
        if (done) tickets.release();
        return done;
    }

    private void record(BlockPos pos) {
        touched.add(pos.getX(), pos.getY(), pos.getZ());
        if (placedSet.add(pos.asLong())) placed.add(pos.immutable());
    }

    private boolean inRegion(int x, int z) {
        return Math.abs(x - origin.getX()) <= radius && Math.abs(z - origin.getZ()) <= radius;
    }

    private void prepare() {
        if (prepared) return;
        prepared = true;
        Map<Long, int[]> byPos = new LinkedHashMap<>();
        Map<BlockState, Integer> index = new LinkedHashMap<>();

        for (SceneDef.TemplateRef ref : set.templates()) {
            ResourceLocation id = ResourceLocation.tryParse(ref.id());
            Optional<StructureTemplate> tpl = id == null ? Optional.empty()
                    : level.getServer().getStructureManager().get(id);
            if (tpl.isEmpty()) {
                warnOnce("template " + ref.id());
                continue;
            }
            addTemplate(ref.id(), tpl.get(), origin.offset(ref.at()[0], ref.at()[1], ref.at()[2]), byPos, index);
        }
        for (SceneDef.SchematicRef ref : set.schematics()) {
            addSchematic(ref, byPos, index);
        }
        for (SceneDef.Fill f : set.fills()) {
            BlockState state = block(f.block());
            if (state == null) continue;
            int s = stateIndex(state, index);
            for (int x = Math.min(f.from()[0], f.to()[0]); x <= Math.max(f.from()[0], f.to()[0]); x++) {
                for (int y = Math.min(f.from()[1], f.to()[1]); y <= Math.max(f.from()[1], f.to()[1]); y++) {
                    for (int z = Math.min(f.from()[2], f.to()[2]); z <= Math.max(f.from()[2], f.to()[2]); z++) {
                        put(byPos, origin.getX() + x, origin.getY() + y, origin.getZ() + z, s);
                    }
                }
            }
        }
        for (SceneDef.Disk d : set.disks()) {
            BlockState state = block(d.block());
            if (state == null) continue;
            int s = stateIndex(state, index);
            int r = d.radius();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        put(byPos, origin.getX() + d.center()[0] + dx, origin.getY() + d.center()[1],
                                origin.getZ() + d.center()[2] + dz, s);
                    }
                }
            }
        }
        states.addAll(index.keySet());
        order = BuildOrder.sortOutward(new ArrayList<>(byPos.values()),
                new int[]{origin.getX(), origin.getY(), origin.getZ()});
    }

    private void put(Map<Long, int[]> byPos, int x, int y, int z, int state) {
        if (!inRegion(x, z)) return;
        byPos.put(BlockPos.asLong(x, y, z), new int[]{x, y, z, state});
    }

    private static int stateIndex(BlockState state, Map<BlockState, Integer> index) {
        Integer existing = index.get(state);
        if (existing != null) return existing;
        int next = index.size();
        index.put(state, next);
        return next;
    }

    private BlockState block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Optional<Block> b = rl == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(rl);
        if (b.isEmpty()) {
            warnOnce("block " + id);
            return null;
        }
        return b.get().defaultBlockState();
    }

    private void addTemplate(String name, StructureTemplate tpl, BlockPos base, Map<Long, int[]> byPos,
                             Map<BlockState, Integer> index) {
        CompoundTag tag = tpl.save(new CompoundTag());
        // Templates are saved with either one "palette" or several "palettes"; the first of several is used.
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty() && tag.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = tag.getList("palettes", Tag.TAG_LIST);
            if (!palettes.isEmpty()) palette = palettes.getList(0);
        }
        List<BlockState> palStates = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            palStates.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    palette.getCompound(i)));
        }
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int idx = b.getInt("state");
            if (idx < 0 || idx >= palStates.size()) {
                warnOnce("palette index in template " + name);
                continue;
            }
            BlockState st = palStates.get(idx);
            if (skipped(st)) continue;
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            put(byPos, base.getX() + pos.getInt(0), base.getY() + pos.getInt(1), base.getZ() + pos.getInt(2),
                    stateIndex(st, index));
        }
    }

    /** The loaded schematic for a scene's schematic id, or null if the id is unknown or the file failed to load. */
    private static MiniHogwartsSchematic schematic(String id) {
        return "mini_hogwarts".equals(id) ? MiniHogwartsSchematic.get() : null;
    }

    /** 0, 90, 180, 270 degrees clockwise seen from above; null for anything else. */
    static Rotation rotation(int degrees) {
        if (degrees == 0) return Rotation.NONE;
        if (degrees == 90) return Rotation.CLOCKWISE_90;
        if (degrees == 180) return Rotation.CLOCKWISE_180;
        if (degrees == 270) return Rotation.COUNTERCLOCKWISE_90;
        return null;
    }

    /**
     * Where schematic cell (x, z) lands inside the rotated footprint, as an offset from its min corner. The
     * schematic is {@code w} by {@code l}; turned 90 or 270 degrees the footprint is {@code l} by {@code w}. The
     * same turn as {@link Rotation} applies to a structure template, shifted back to non-negative offsets.
     */
    public static int[] rotateXZ(int x, int z, int w, int l, int degrees) {
        if (degrees == 90) return new int[]{l - 1 - z, x};
        if (degrees == 180) return new int[]{w - 1 - x, l - 1 - z};
        if (degrees == 270) return new int[]{z, w - 1 - x};
        return new int[]{x, z};
    }

    /** Template cells that are never placed; for a schematic, command blocks as well. */
    private static boolean skipped(BlockState st) {
        return st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.JIGSAW) || st.is(Blocks.STRUCTURE_BLOCK);
    }

    private void addSchematic(SceneDef.SchematicRef ref, Map<Long, int[]> byPos, Map<BlockState, Integer> index) {
        MiniHogwartsSchematic schem = schematic(ref.id());
        if (schem == null) {
            warnOnce("schematic " + ref.id());
            return;
        }
        Rotation rot = rotation(ref.rotation());
        if (rot == null) {
            warnOnce("rotation " + ref.rotation() + " of schematic " + ref.id());
            return;
        }
        int w = schem.width(), l = schem.length(), wl = w * l;
        int bx = origin.getX() + ref.at()[0], by = origin.getY() + ref.at()[1], bz = origin.getZ() + ref.at()[2];
        // Block states are shared objects, so the turned state and its index are looked up once per palette entry.
        Map<BlockState, Integer> turned = new IdentityHashMap<>();
        for (int i = 0, n = schem.volume(); i < n; i++) {
            BlockState st = schem.blockAt(i);
            if (skipped(st) || st.is(Blocks.COMMAND_BLOCK) || st.is(Blocks.CHAIN_COMMAND_BLOCK)
                    || st.is(Blocks.REPEATING_COMMAND_BLOCK)) {
                continue;
            }
            Integer s = turned.get(st);
            if (s == null) {
                s = stateIndex(st.rotate(rot), index);
                turned.put(st, s);
            }
            int x = i % w, z = (i / w) % l, y = i / wl;
            int[] r = rotateXZ(x, z, w, l, ref.rotation());
            put(byPos, bx + r[0], by + y, bz + r[1], s);
        }
    }

    private void placeFeature(SceneDef.FeatureRef ref, int[] left) {
        ResourceLocation id = ResourceLocation.tryParse(ref.id());
        ConfiguredFeature<?, ?> feature = id == null ? null : level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).get(id);
        if (feature == null) {
            warnOnce("feature " + ref.id());
            return;
        }
        BlockPos at = origin.offset(ref.at()[0], ref.at()[1], ref.at()[2]);
        WorldGenLevel proxy = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, (self, method, args) -> {
                    String n = method.getName();
                    // A feature's water spring or falling sand would otherwise tick in the finished set.
                    if (n.equals("scheduleTick")) return null;
                    if (n.equals("setBlock") && args != null && args.length >= 3 && args[0] instanceof BlockPos pos) {
                        if (!inRegion(pos.getX(), pos.getZ())) return false;
                        Object[] forced = args.clone();
                        forced[2] = FLAGS;
                        boolean ok = (boolean) invoke(method, forced);
                        record(pos);
                        left[0]--;
                        return ok;
                    }
                    if ((n.equals("removeBlock") || n.equals("destroyBlock")) && args != null
                            && args[0] instanceof BlockPos pos) {
                        if (!inRegion(pos.getX(), pos.getZ())) return false;
                        boolean ok = level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        record(pos);
                        left[0]--;
                        return ok;
                    }
                    return invoke(method, args);
                });
        feature.place(proxy, level.getChunkSource().getGenerator(), RandomSource.create(at.asLong()), at);
    }

    private Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(level, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void warnOnce(String what) {
        if (warned.add(what)) LOGGER.warn("Pensieve set: unknown or bad {}; skipped", what);
    }

    /**
     * The box of every block one job step wrote. {@link #clearTicks} drops the block and fluid ticks the writes
     * scheduled there through {@code onPlace} (falling gravel and sand, flowing water) and resets the box.
     */
    static final class Touched {
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void add(int x, int y, int z) {
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        void clearTicks(ServerLevel level) {
            if (minX > maxX) return;
            SetBuilder.clearTicks(level, new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
            minX = minY = minZ = Integer.MAX_VALUE;
            maxX = maxY = maxZ = Integer.MIN_VALUE;
        }
    }

    /** Drops every scheduled block and fluid tick inside {@code box} (blocks a set or an event just wrote). */
    static void clearTicks(ServerLevel level, BoundingBox box) {
        level.getBlockTicks().clearArea(box);
        level.getFluidTicks().clearArea(box);
    }

    /** Holds region tickets over a square of chunks for as long as a job runs. */
    private static final class Tickets {
        private final ServerLevel level;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private boolean held;

        Tickets(ServerLevel level, BlockPos origin, int radius) {
            this.level = level;
            int x0 = (origin.getX() - radius) >> 4, x1 = (origin.getX() + radius) >> 4;
            int z0 = (origin.getZ() - radius) >> 4, z1 = (origin.getZ() + radius) >> 4;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) chunks.add(new ChunkPos(x, z));
            }
        }

        void acquire() {
            if (held) return;
            held = true;
            for (ChunkPos c : chunks) level.getChunkSource().addRegionTicket(TICKET, c, 0, c);
        }

        void release() {
            if (!held) return;
            held = false;
            for (ChunkPos c : chunks) level.getChunkSource().removeRegionTicket(TICKET, c, 0, c);
        }
    }

    /** Sets a list of positions back to air; chunks are held until it finishes (or {@link ClearJob#release}). */
    public static ClearJob clear(ServerLevel level, List<BlockPos> positions) {
        return new ClearJob(level, new ArrayList<>(positions));
    }

    public static final class ClearJob {
        private final ServerLevel level;
        private final List<BlockPos> positions;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private int cursor;
        private boolean held;

        private ClearJob(ServerLevel level, List<BlockPos> positions) {
            this.level = level;
            this.positions = positions;
            Set<Long> seen = new HashSet<>();
            for (BlockPos p : positions) {
                ChunkPos c = new ChunkPos(p);
                if (seen.add(c.toLong())) chunks.add(c);
            }
        }

        /** True when everything is cleared; the chunk tickets are dropped then. */
        public boolean advance(int budget) {
            return advance(new int[]{budget});
        }

        /** As {@link #advance(int)}, against a budget shared with other jobs ({@code left[0]} goes down). */
        public boolean advance(int[] left) {
            if (!held) {
                held = true;
                for (ChunkPos c : chunks) level.getChunkSource().addRegionTicket(TICKET, c, 0, c);
            }
            Touched touched = new Touched();
            while (cursor < positions.size() && left[0] > 0) {
                BlockPos pos = positions.get(cursor++);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                touched.add(pos.getX(), pos.getY(), pos.getZ());
                left[0]--;
            }
            touched.clearTicks(level);
            boolean done = cursor >= positions.size();
            if (done) release();
            return done;
        }

        /** Drops the chunk tickets early; idempotent. */
        public void release() {
            if (!held) return;
            held = false;
            for (ChunkPos c : chunks) level.getChunkSource().removeRegionTicket(TICKET, c, 0, c);
        }
    }
}
