package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.TerrainRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
        final boolean masteryEligible;
        /** Where the beam hit: the debris is thrown away from here. */
        final Vec3 impact;
        int broken;

        Job(UUID casterId, int rank, List<BlockPos> positions, boolean masteryEligible, Vec3 impact) {
            this.casterId = casterId;
            this.impact = impact;
            this.masteryEligible = masteryEligible;
            this.rank = rank;
            this.positions = positions;
        }
    }

    private BombardaBlasts() {
    }

    /** @return true if the job was queued; false if there was nothing to break or the queue is at its cap */
    static boolean queue(ServerLevel level, ServerPlayer caster, int rank, List<BlockPos> positions,
                         boolean masteryEligible, Vec3 impact) {
        if (positions.isEmpty()) {
            return false;
        }
        ArrayDeque<Job> jobs = JOBS.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        if (jobs.size() < MAX_JOBS_PER_LEVEL) {
            jobs.add(new Job(caster.getUUID(), rank, positions, masteryEligible, impact));
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
            List<BlockPos> brokenNow = new ArrayList<>();
            List<BlockState> brokenStates = new ArrayList<>();
            while (budget > 0 && job.next < job.positions.size()) {
                BlockPos pos = job.positions.get(job.next++);
                BlockState before = level.isLoaded(pos) ? level.getBlockState(pos) : null;
                if (breakOne(level, pos, caster, job.rank)) {
                    job.broken++;
                    if (before != null && !(before.getBlock() instanceof TntBlock)) {
                        brokenNow.add(pos);
                        brokenStates.add(before);
                    }
                }
                budget--;
            }
            sendDebris(level, job.impact, caster.getEyePosition().subtract(job.impact), brokenNow, brokenStates);
            if (job.next >= job.positions.size()) {
                jobs.poll();
                if (job.broken > 0 && job.masteryEligible && com.hogwartsmagic.item.MagicBookItem.isSpellLearned(caster, com.hogwartsmagic.spell.SpellType.BOMBARDA)) {
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

    /** Tells the players who can see the blast which blocks just broke, so their clients throw debris. */
    private static void sendDebris(ServerLevel level, Vec3 impact, Vec3 out, List<BlockPos> positions, List<BlockState> states) {
        if (positions.isEmpty()) {
            return;
        }
        Vec3 dir = out.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : out.normalize();
        for (int from = 0; from < positions.size(); from += SpellPackets.MAX_DEBRIS_ENTRIES) {
            int to = Math.min(positions.size(), from + SpellPackets.MAX_DEBRIS_ENTRIES);
            long[] packed = new long[to - from];
            int[] ids = new int[to - from];
            for (int i = from; i < to; i++) {
                packed[i - from] = positions.get(i).asLong();
                ids[i - from] = Block.getId(states.get(i));
            }
            SpellPackets.CHANNEL.send(new SpellPackets.BlockDebrisPacket(impact.x, impact.y, impact.z,
                            (float) dir.x, (float) dir.y, (float) dir.z, packed, ids),
                    PacketDistributor.TRACKING_CHUNK.with(level.getChunkAt(BlockPos.containing(impact))));
        }
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
