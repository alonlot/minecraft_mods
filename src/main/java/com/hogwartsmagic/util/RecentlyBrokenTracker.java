package com.hogwartsmagic.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tracks blocks that have recently been broken by players so spells like Reparo
 * can still restore them within a short window. Bounded per-dimension and entries
 * expire on a fixed TTL — this is a memory-only cache, not a save-game feature.
 *
 * <p>Records are stored newest-first so iteration finds the freshest break first;
 * stale records (past {@link #TTL_TICKS}) are evicted lazily on every lookup.</p>
 */
public final class RecentlyBrokenTracker {

    /** A single broken-block record. {@code state} is the state that existed before the break. */
    public static final class Record {
        public final BlockPos pos;
        public final BlockState state;
        public final long gameTime;

        public Record(BlockPos pos, BlockState state, long gameTime) {
            this.pos = pos;
            this.state = state;
            this.gameTime = gameTime;
        }
    }

    /** Maximum live records per dimension before oldest entries get pushed out. */
    private static final int MAX_PER_DIM = 256;

    /** A record older than this is considered "too stale to repair" and gets dropped. 5 minutes. */
    private static final long TTL_TICKS = 20L * 60L * 5L;

    private static final Map<ResourceKey<Level>, Deque<Record>> RECORDS = new HashMap<>();

    private RecentlyBrokenTracker() {}

    /**
     * Record a freshly-broken block. {@code state} should be the state that existed
     * before the break (Forge's BlockEvent.BreakEvent gives the right one).
     */
    public static synchronized void record(Level level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) return;
        ResourceKey<Level> key = level.dimension();
        Deque<Record> deq = RECORDS.computeIfAbsent(key, k -> new ArrayDeque<>());
        deq.addFirst(new Record(pos.immutable(), state, level.getGameTime()));
        while (deq.size() > MAX_PER_DIM) deq.removeLast();
    }

    /**
     * Find recently-broken blocks within {@code radius} of {@code center} whose original
     * position is currently air (so we don't try to restore something already replaced).
     * Evicts stale records as a side-effect.
     */
    public static synchronized List<Record> findNearby(Level level, BlockPos center, double radius) {
        Deque<Record> deq = RECORDS.get(level.dimension());
        if (deq == null) return Collections.emptyList();
        long now = level.getGameTime();
        double r2 = radius * radius;
        List<Record> out = new ArrayList<>();
        Iterator<Record> it = deq.iterator();
        while (it.hasNext()) {
            Record r = it.next();
            if (now - r.gameTime > TTL_TICKS) {
                it.remove();
                continue;
            }
            if (r.pos.distSqr(center) <= r2 && level.getBlockState(r.pos).isAir()) {
                out.add(r);
            }
        }
        return out;
    }

    /** Drop a specific record (called after restoration so it isn't repaired twice). */
    public static synchronized void consume(Level level, BlockPos pos) {
        Deque<Record> deq = RECORDS.get(level.dimension());
        if (deq == null) return;
        deq.removeIf(r -> r.pos.equals(pos));
    }

    /**
     * Search for an {@link net.minecraft.world.entity.item.ItemEntity} within {@code radius}
     * of {@code center} whose stack item matches the {@link BlockState}'s block item, and
     * consume exactly one count from it. Returns {@code true} on a successful consume.
     *
     * <p>This is what makes "Reparo only fixes blocks whose drops are still nearby" work:
     * if the rubble dropped a stone and that stone is still lying on the ground, the spell
     * can use it; if a player has already picked the rubble up, Reparo just fizzles.</p>
     */
    public static boolean tryConsumeNearbyDrop(net.minecraft.world.level.Level level,
                                               BlockPos center, BlockState state, double radius) {
        net.minecraft.world.item.Item item = state.getBlock().asItem();
        if (item == net.minecraft.world.item.Items.AIR) {
            // No item form (e.g. flowing fluids, fire) — nothing to consume, treat as
            // un-repairable so Reparo doesn't silently rebuild things that have no drops.
            return false;
        }
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(center).inflate(radius);
        java.util.List<net.minecraft.world.entity.item.ItemEntity> drops =
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                        ie -> ie.isAlive() && ie.getItem().is(item));
        if (drops.isEmpty()) return false;
        net.minecraft.world.entity.item.ItemEntity ie = drops.get(0);
        net.minecraft.world.item.ItemStack stack = ie.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) {
            ie.discard();
        } else {
            ie.setItem(stack);
        }
        return true;
    }
}
