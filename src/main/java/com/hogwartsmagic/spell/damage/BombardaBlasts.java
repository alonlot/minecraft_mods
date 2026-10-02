package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.TerrainRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Breaks Bombarda's blocks a few dozen at a time on the server thread: at most
 * {@link BombardaRules#BLOCKS_PER_TICK} positions per level per tick, nearest first. Every position is
 * re-checked when its turn comes (loaded chunk, still breakable, TerrainRules.canBreak with a fresh
 * BreakEvent), so nothing here can load a chunk or act on a stale block. A job ends early if its caster
 * logs out or leaves the dimension. Jobs hold only a caster UUID and positions, never a level or entity.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BombardaBlasts {
    private static final int MAX_JOBS_PER_LEVEL = 32;
    private static final Map<ResourceKey<Level>, ArrayDeque<Job>> JOBS = new HashMap<>();

    private static final class Job {
        final UUID casterId;
        final int rank;
        final List<BlockPos> positions;
        int next;
        int broken;

        Job(UUID casterId, int rank, List<BlockPos> positions) {
            this.casterId = casterId;
            this.rank = rank;
            this.positions = positions;
        }
    }

    private BombardaBlasts() {
    }

    /** @return true if the job was queued; false if there was nothing to break or the queue is at its cap */
    static boolean queue(ServerLevel level, ServerPlayer caster, int rank, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return false;
        }
        ArrayDeque<Job> jobs = JOBS.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        if (jobs.size() < MAX_JOBS_PER_LEVEL) {
            jobs.add(new Job(caster.getUUID(), rank, positions));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        ArrayDeque<Job> jobs = JOBS.get(level.dimension());
        if (jobs == null) {
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            JOBS.remove(level.dimension()); // griefing was switched off after the cast: drop what is queued
            return;
        }
        if (jobs.isEmpty()) {
            JOBS.remove(level.dimension());
            return;
        }
        int budget = BombardaRules.BLOCKS_PER_TICK;
        while (budget > 0 && !jobs.isEmpty()) {
            Job job = jobs.peek();
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(job.casterId);
            if (caster == null || caster.level() != level) {
                jobs.poll();
                continue;
            }
            while (budget > 0 && job.next < job.positions.size()) {
                if (breakOne(level, job.positions.get(job.next++), caster, job.rank)) {
                    job.broken++;
                }
                budget--;
            }
            if (job.next >= job.positions.size()) {
                jobs.poll();
                if (job.broken > 0 && com.hogwartsmagic.item.MagicBookItem.isSpellLearned(caster, com.hogwartsmagic.spell.SpellType.BOMBARDA)) {
                    com.hogwartsmagic.spell.mastery.MasteryAwards.onBlockEffect(caster, com.hogwartsmagic.spell.SpellType.BOMBARDA);
                }
            }
        }
        if (jobs.isEmpty()) {
            JOBS.remove(level.dimension());
        }
    }

    /** @return true if the block was broken (or a TNT block was primed) */
    private static boolean breakOne(ServerLevel level, BlockPos pos, ServerPlayer caster, int rank) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BaseFireBlock) {
            return false;
        }
        if (state.hasBlockEntity() || state.is(DamageSupport.BOMBARDA_IMMUNE) || state.is(BlockTags.WITHER_IMMUNE)) {
            return false; // spawners, containers, horcruxes, wards; bedrock and the mod's locks
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (!BombardaRules.canBreak(rank, hardness, state.is(DamageSupport.BOMBARDA_OBSIDIAN_TIER), level.random.nextDouble())) {
            return false;
        }
        // Last gate: this posts a real BreakEvent with side effects, so a true means break it now.
        if (!TerrainRules.canBreak(level, pos, state, caster)) {
            return false;
        }
        if (state.getBlock() instanceof TntBlock) {
            state.onCaughtFire(level, pos, null, caster); // primes the TNT (Forge's onCaughtFire only spawns it); the block is removed below
            if (level.getBlockState(pos).getBlock() instanceof TntBlock) {
                level.removeBlock(pos, false);
            }
            return true;
        }
        return level.destroyBlock(pos, level.random.nextDouble() < BombardaRules.dropChance(rank), caster);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            JOBS.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }
}
