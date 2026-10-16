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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
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
 * placed {@code budget} blocks per {@link #advance}. Features go last: a configured feature can place blocks
 * anywhere near its origin, so each one is placed whole (counted as {@link #FEATURE_COST} against the budget)
 * and the blocks it changed are found by diffing a box around it, so {@link #placed()} stays exact.
 *
 * <p>Blocks use flags {@code 2 | 16}: clients are told, but no neighbour updates, no drops, nothing flows or
 * ticks. Features place with their own normal flags (their blocks are still recorded and can be cleared).
 *
 * <p>Templates: blocks only, no entities. Air, structure voids, jigsaws and structure blocks in a template are
 * skipped, so a template never carves holes in what a fill laid down underneath it.
 */
public final class SetBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int FLAGS = 2 | 16;
    static final int FEATURE_COST = 1000;
    private static final int FEATURE_RADIUS = 8;
    private static final int FEATURE_DOWN = 4;
    private static final int FEATURE_UP = 24;

    private final ServerLevel level;
    private final BlockPos origin;
    private final SceneDef.SetDef set;
    private final List<BlockState> states = new ArrayList<>();
    private List<int[]> order;
    private int cursor;
    private int featureCursor;
    private boolean prepared;
    private final List<BlockPos> placed = new ArrayList<>();
    private final Set<String> warned = new HashSet<>();

    public SetBuilder(ServerLevel level, BlockPos origin, SceneDef.SetDef set) {
        this.level = level;
        this.origin = origin;
        this.set = set;
    }

    /** Blocks the template, fill and disk phase will place (features are extra). */
    public int total() {
        prepare();
        return order.size();
    }

    public List<BlockPos> placed() {
        return placed;
    }

    /** Places at most {@code budget} blocks; true when the whole set is built. */
    public boolean advance(int budget) {
        prepare();
        int left = budget;
        while (cursor < order.size() && left > 0) {
            int[] p = order.get(cursor++);
            BlockPos pos = new BlockPos(p[0], p[1], p[2]);
            level.setBlock(pos, states.get(p[3]), FLAGS);
            placed.add(pos);
            left--;
        }
        if (cursor < order.size()) return false;
        List<SceneDef.FeatureRef> features = set.features();
        while (featureCursor < features.size() && left > 0) {
            placeFeature(features.get(featureCursor++));
            left -= FEATURE_COST;
        }
        return featureCursor >= features.size();
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
            addTemplate(tpl.get(), origin.offset(ref.at()[0], ref.at()[1], ref.at()[2]), byPos, index);
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

    private static void put(Map<Long, int[]> byPos, int x, int y, int z, int state) {
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

    private void addTemplate(StructureTemplate tpl, BlockPos base, Map<Long, int[]> byPos,
                             Map<BlockState, Integer> index) {
        CompoundTag tag = tpl.save(new CompoundTag());
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palStates = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            palStates.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    palette.getCompound(i)));
        }
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            BlockState st = palStates.get(b.getInt("state"));
            if (st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.JIGSAW) || st.is(Blocks.STRUCTURE_BLOCK)) {
                continue;
            }
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            put(byPos, base.getX() + pos.getInt(0), base.getY() + pos.getInt(1), base.getZ() + pos.getInt(2),
                    stateIndex(st, index));
        }
    }

    private void placeFeature(SceneDef.FeatureRef ref) {
        ResourceLocation id = ResourceLocation.tryParse(ref.id());
        ConfiguredFeature<?, ?> feature = id == null ? null : level.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE).get(id);
        if (feature == null) {
            warnOnce("feature " + ref.id());
            return;
        }
        BlockPos at = origin.offset(ref.at()[0], ref.at()[1], ref.at()[2]);
        List<BlockPos> box = new ArrayList<>();
        List<BlockState> before = new ArrayList<>();
        for (int x = -FEATURE_RADIUS; x <= FEATURE_RADIUS; x++) {
            for (int z = -FEATURE_RADIUS; z <= FEATURE_RADIUS; z++) {
                for (int y = -FEATURE_DOWN; y <= FEATURE_UP; y++) {
                    BlockPos p = at.offset(x, y, z);
                    box.add(p);
                    before.add(level.getBlockState(p));
                }
            }
        }
        feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(at.asLong()), at);
        for (int i = 0; i < box.size(); i++) {
            if (!level.getBlockState(box.get(i)).equals(before.get(i))) {
                placed.add(box.get(i));
            }
        }
    }

    private void warnOnce(String what) {
        if (warned.add(what)) LOGGER.warn("Pensieve set: unknown {}; skipped", what);
    }

    /** Sets a list of positions back to air, a budget of blocks per call. */
    public static ClearJob clear(ServerLevel level, List<BlockPos> positions) {
        return new ClearJob(level, new ArrayList<>(positions));
    }

    public static final class ClearJob {
        private final ServerLevel level;
        private final List<BlockPos> positions;
        private int cursor;

        private ClearJob(ServerLevel level, List<BlockPos> positions) {
            this.level = level;
            this.positions = positions;
        }

        /** True when everything is cleared. */
        public boolean advance(int budget) {
            int left = budget;
            while (cursor < positions.size() && left > 0) {
                level.setBlock(positions.get(cursor++), Blocks.AIR.defaultBlockState(), FLAGS);
                left--;
            }
            return cursor >= positions.size();
        }
    }
}
