package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a scene's set around a region origin a few thousand blocks per tick, and clears it again.
 *
 * <p>Templates, fills and disks are collected up front (sets are capped at radius 200, so the list is small
 * enough), de-duplicated (a later layer wins), sorted outward from the origin with {@link BuildOrder} and then
 * placed {@code budget} blocks per {@link #advance}. Anything more than {@code radius} blocks from the origin
 * horizontally is dropped, so an oversized template can never spill into a neighbouring region.
 *
 * <p>Features go last. They are placed through a proxy of the level that forces every write to flags
 * {@code 2 | 16}, records every position written into {@link #placed()} and counts the writes against the
 * budget. A feature is only started while budget remains; one feature may overshoot the budget by its own size.
 *
 * <p>All writes use flags {@code 2 | 16}: clients are told, but no neighbour updates, no drops, nothing flows
 * or ticks.
 *
 * <p>Templates: blocks only, no entities. Air, structure voids, jigsaws and structure blocks in a template are
 * skipped, so a template never carves holes in what a fill laid down underneath it.
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

    public SetBuilder(ServerLevel level, BlockPos origin, SceneDef.SetDef set, int radius) {
        this.level = level;
        this.origin = origin;
        this.set = set;
        this.radius = radius;
        this.tickets = new Tickets(level, origin, radius);
    }

    /** Blocks the template, fill and disk phase will place (features are extra). */
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
        prepare();
        tickets.acquire();
        int[] left = {budget};
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
        boolean done = featureCursor >= features.size();
        if (done) tickets.release();
        return done;
    }

    private void record(BlockPos pos) {
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
            if (st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.JIGSAW) || st.is(Blocks.STRUCTURE_BLOCK)) {
                continue;
            }
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            put(byPos, base.getX() + pos.getInt(0), base.getY() + pos.getInt(1), base.getZ() + pos.getInt(2),
                    stateIndex(st, index));
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
            if (!held) {
                held = true;
                for (ChunkPos c : chunks) level.getChunkSource().addRegionTicket(TICKET, c, 0, c);
            }
            int left = budget;
            while (cursor < positions.size() && left > 0) {
                level.setBlock(positions.get(cursor++), Blocks.AIR.defaultBlockState(), FLAGS);
                left--;
            }
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
