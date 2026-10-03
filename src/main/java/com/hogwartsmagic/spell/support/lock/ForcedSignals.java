package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Comparators, observers and other sources an Alohomora Master forces on: for a few seconds they read as 15
 * to everything around them (through BlockStateSignalMixin), then let go. Server thread only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForcedSignals {
    /** The mixin's fast path: nothing is forced anywhere. */
    public static volatile boolean ACTIVE;

    /** The block that was forced, so a block that replaced it is not read as forced. */
    private record Entry(Block block, long until) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> FORCED = new HashMap<>();

    private ForcedSignals() {
    }

    public static void force(ServerLevel level, BlockPos pos, int ticks) {
        if (!level.isLoaded(pos)) {
            return;
        }
        Block block = level.getBlockState(pos).getBlock();
        FORCED.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .put(pos.immutable(), new Entry(block, level.getGameTime() + ticks));
        ACTIVE = true;
        notifyAround(level, pos);
    }

    public static int signal(ServerLevel level, BlockPos pos, BlockState state, Direction direction, boolean strong) {
        if (!level.getServer().isSameThread()) {
            return 0;
        }
        Map<BlockPos, Entry> map = FORCED.get(level.dimension());
        if (map == null) {
            return 0;
        }
        Entry entry = map.get(pos);
        if (entry == null || !state.is(entry.block())) {
            return 0;
        }
        if (directional(state)) {
            return direction == facing(state) ? 15 : 0;
        }
        return strong ? 0 : 15;
    }

    private static boolean directional(BlockState state) {
        return state.getBlock() instanceof ComparatorBlock || state.getBlock() instanceof ObserverBlock;
    }

    private static void notifyAround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        level.updateNeighborsAt(pos, block);
        if (directional(state)) {
            level.updateNeighborsAt(pos.relative(facing(state).getOpposite()), block);
        }
    }

    private static Direction facing(BlockState state) {
        return state.getBlock() instanceof ObserverBlock
                ? state.getValue(ObserverBlock.FACING) : state.getValue(ComparatorBlock.FACING);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ACTIVE || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Entry> map = FORCED.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Entry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Entry> e = it.next();
            if (now < e.getValue().until()) {
                continue;
            }
            it.remove();
            if (level.isLoaded(e.getKey())) {
                notifyAround(level, e.getKey());
            }
        }
        ACTIVE = FORCED.values().stream().anyMatch(m -> !m.isEmpty());
    }

    /** Before the final save: let go of everything and tell the neighbours, so no forced power is left behind. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Entry>> e : FORCED.entrySet()) {
            ServerLevel level = event.getServer().getLevel(e.getKey());
            Map<BlockPos, Entry> map = e.getValue();
            Iterator<BlockPos> it = map.keySet().iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                it.remove();
                if (level != null && level.isLoaded(pos)) {
                    notifyAround(level, pos);
                }
            }
        }
        FORCED.clear();
        ACTIVE = false;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FORCED.clear();
        ACTIVE = false;
    }
}
