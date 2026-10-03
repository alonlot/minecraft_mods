package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reading a piston door's lock. The wiring is traced on the server thread and solved on one background
 * thread. Then the answer is played back on the real levers and buttons (Alohomora), shown as hints
 * (Revelio), or printed (/lockinfo). No protection checks, by the user's choice. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PistonLockSpell {
    public enum Use { OPEN, REVEAL, INSPECT }

    private static final Logger LOGGER = LogUtils.getLogger();

    private record Job(UUID caster, ResourceKey<Level> dimension, CircuitTracer.TracedLock lock, int rank, Use use,
                       Future<LockSolver.Solution> result, long deadline) {
    }

    private record Step(UUID caster, ResourceKey<Level> dimension, CircuitTracer.InputSite site, long at, boolean last) {
    }

    private static final ExecutorService SOLVER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hogwartsmagic-lock-solver");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final List<Job> JOBS = new ArrayList<>();
    private static final List<Step> STEPS = new ArrayList<>();

    private PistonLockSpell() {
    }

    /** Start reading the lock near hit. False if there is no piston door there at all. */
    public static boolean begin(ServerLevel level, BlockPos hit, ServerPlayer caster, int rank, Use use) {
        UUID id = caster.getUUID();
        if (JOBS.stream().anyMatch(j -> j.caster().equals(id)) || STEPS.stream().anyMatch(s -> s.caster().equals(id))) {
            tell(caster, "busy", ChatFormatting.GRAY);
            return true;
        }
        CircuitTracer.Mode mode = use == Use.REVEAL
                ? new CircuitTracer.Mode(false, LockRules.readsItemKeys(rank))
                : new CircuitTracer.Mode(LockRules.forcesSources(rank), false);
        CircuitTracer.TracedLock lock = CircuitTracer.trace(level, hit, LockRules.traceBlocks(rank), mode);
        if (lock == null) {
            return false;
        }
        if (lock.circuit().pistonLogic()) {
            tell(caster, "piston_logic", ChatFormatting.GOLD); // pistons that move wiring are not modelled
            return true;
        }
        LockCircuit circuit = lock.circuit();
        int maxInputs = LockRules.maxInputs(rank);
        int maxPresses = LockRules.maxPresses(rank);
        long budget = LockRules.budget(rank);
        Future<LockSolver.Solution> result = SOLVER.submit(() -> LockSolver.solve(circuit, maxInputs, maxPresses, budget));
        JOBS.add(new Job(id, level.dimension(), lock, rank, use, result, level.getGameTime() + LockRules.SOLVE_TIMEOUT_TICKS));
        if (use != Use.INSPECT) {
            tell(caster, "reading", ChatFormatting.GRAY);
        }
        return true;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || (JOBS.isEmpty() && STEPS.isEmpty())) {
            return;
        }
        long now = level.getGameTime();
        finishJobs(level, now);
        runSteps(level, now);
    }

    private static void finishJobs(ServerLevel level, long now) {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.dimension() != level.dimension() || (!job.result().isDone() && now < job.deadline())) {
                continue;
            }
            it.remove();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(job.caster());
            if (!job.result().isDone()) {
                job.result().cancel(true);
                if (player != null) {
                    tell(player, "out_of_time", ChatFormatting.GOLD);
                }
                continue;
            }
            if (player == null) {
                continue;
            }
            LockSolver.Solution solution;
            try {
                solution = job.result().get();
            } catch (Exception e) {
                LOGGER.warn("Piston lock solver failed", e);
                continue;
            }
            if (job.use() == Use.INSPECT) {
                report(player, job, solution);
            } else if (explainFailure(player, job, solution)) {
                if (job.use() == Use.REVEAL) {
                    LockHints.show(level, job.lock(), solution);
                    tell(player, "revealed", ChatFormatting.AQUA);
                } else {
                    schedule(level, player, job, solution, now);
                }
            }
        }
    }

    /** Tells the caster why there is nothing to do; true if the solution is usable. */
    private static boolean explainFailure(ServerPlayer player, Job job, LockSolver.Solution s) {
        LockCircuit circuit = job.lock().circuit();
        LockSolver.Status status = s.status();
        if (status == LockSolver.Status.SOLVED) {
            return true;
        }
        if (status == LockSolver.Status.TOO_MANY_INPUTS) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.lock.too_big",
                    circuit.inputCount(), LockRules.maxInputs(job.rank())).withStyle(ChatFormatting.GOLD), true);
        } else if (status == LockSolver.Status.UNSTABLE) {
            tell(player, "unstable", ChatFormatting.GOLD);
        } else if (status == LockSolver.Status.OUT_OF_TIME) {
            tell(player, "out_of_time", ChatFormatting.GOLD);
        } else if (status == LockSolver.Status.NO_SOLUTION || status == LockSolver.Status.NO_DOOR) {
            tell(player, circuit.keyed() ? "keyed" : circuit.truncated() ? "too_far" : "no_solution", ChatFormatting.GOLD);
        }
        return false;
    }

    private static void schedule(ServerLevel level, ServerPlayer player, Job job, LockSolver.Solution s, long now) {
        List<CircuitTracer.InputSite> sites = job.lock().inputs();
        for (int input : s.set()) {
            if (shifted(level, sites.get(input))) {
                tell(player, "shifted", ChatFormatting.GOLD);
                return;
            }
        }
        for (int input : s.presses()) {
            if (shifted(level, sites.get(input))) {
                tell(player, "shifted", ChatFormatting.GOLD);
                return;
            }
        }
        long at = now;
        for (int i = 0; i < s.set().length; i++) {
            boolean last = s.presses().length == 0 && i == s.set().length - 1;
            STEPS.add(new Step(job.caster(), job.dimension(), sites.get(s.set()[i]), at, last));
        }
        if (s.set().length > 0) {
            at += LockRules.LEVER_SETTLE_TICKS;
        }
        for (int i = 0; i < s.presses().length; i++) {
            CircuitTracer.InputSite site = sites.get(s.presses()[i]);
            STEPS.add(new Step(job.caster(), job.dimension(), site, at, i == s.presses().length - 1));
            at += site.kind() == LockCircuit.FORCE ? LockRules.FORCE_GAP_TICKS : LockRules.PRESS_GAP_TICKS;
        }
    }

    /** The block is gone, or a lever or button no longer stands as it was traced. */
    private static boolean shifted(ServerLevel level, CircuitTracer.InputSite site) {
        if (!level.isLoaded(site.pos())) {
            return true;
        }
        BlockState state = level.getBlockState(site.pos());
        if (state.getBlock() != site.block()) {
            return true;
        }
        int kind = site.kind();
        return (kind == LockCircuit.LEVER || kind == LockCircuit.PULSE) && CircuitTracer.isOn(state) != site.on();
    }

    private static void runSteps(ServerLevel level, long now) {
        Set<UUID> aborted = new HashSet<>();
        List<Step> due = new ArrayList<>();
        for (Step step : STEPS) {
            if (step.dimension() == level.dimension() && step.at() <= now) {
                due.add(step);
            }
        }
        STEPS.removeAll(due);
        for (Step step : due) {
            if (aborted.contains(step.caster())) {
                continue;
            }
            BlockPos pos = step.site().pos();
            BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
            if (state == null || state.getBlock() != step.site().block()) {
                aborted.add(step.caster()); // never load a chunk; a vanished lever ends the attempt
                continue;
            }
            if (step.site().kind() == LockCircuit.LEVER && CircuitTracer.isOn(state) != step.site().on()) {
                aborted.add(step.caster()); // someone flipped it since the trace; never pull it the wrong way
                ServerPlayer moved = level.getServer().getPlayerList().getPlayer(step.caster());
                if (moved != null) {
                    tell(moved, "shifted", ChatFormatting.GOLD);
                }
                continue;
            }
            if (step.site().kind() == LockCircuit.FORCE) {
                ForcedSignals.force(level, pos, LockRules.FORCE_TICKS);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
            } else {
                operate(level, pos, state);
            }
            level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                    12, 0.3, 0.3, 0.3, 0.4);
            if (step.last()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(step.caster());
                if (player != null) {
                    MasteryAwards.onBlockEffect(player, SpellType.ALOHOMORA);
                    tell(player, "opened", ChatFormatting.AQUA);
                }
            }
        }
        if (!aborted.isEmpty()) {
            STEPS.removeIf(s -> aborted.contains(s.caster()));
        }
    }

    /** Flip a lever, or press a button, plate or sculk sensor, as a hand or a foot would. */
    private static void operate(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LeverBlock lever) {
            lever.pull(state, level, pos, null);
        } else if (block instanceof ButtonBlock button) {
            if (!state.getValue(ButtonBlock.POWERED)) {
                button.press(state, level, pos, null);
            }
        } else if (block instanceof SculkSensorBlock sensor) {
            if (SculkSensorBlock.canActivate(state)) {
                sensor.activate(null, level, pos, state, 15, 0);
            }
        } else if (block instanceof BasePressurePlateBlock) {
            BlockState pressed = state.hasProperty(BlockStateProperties.POWERED)
                    ? state.setValue(BlockStateProperties.POWERED, true)
                    : state.hasProperty(BlockStateProperties.POWER) ? state.setValue(BlockStateProperties.POWER, 15) : state;
            if (pressed != state) {
                level.setBlock(pos, pressed, Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, block);
                level.updateNeighborsAt(pos.below(), block);
                level.scheduleTick(pos, block, 20); // the plate's own tick finds nobody on it and lets go
                level.playSound(null, pos, SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.3f, 0.6f);
            }
        }
    }

    /** /lockinfo: what the lock is and what the solver found, without touching anything. */
    private static void report(ServerPlayer player, Job job, LockSolver.Solution s) {
        CircuitTracer.TracedLock lock = job.lock();
        LockCircuit c = lock.circuit();
        int levers = 0;
        int pulses = 0;
        int forced = 0;
        int keys = 0;
        for (int i = 0; i < c.inputCount(); i++) {
            int kind = c.inputKind(i);
            if (kind == LockCircuit.LEVER) {
                levers++;
            } else if (kind == LockCircuit.PULSE) {
                pulses++;
            } else if (kind == LockCircuit.FORCE) {
                forced++;
            } else {
                keys++;
            }
        }
        player.sendSystemMessage(Component.literal("Lock (rank " + job.rank() + "): " + lock.pistons() + " door pistons, "
                + lock.blocks() + " blocks, " + c.nodeCount() + " nodes; " + levers + " levers, "
                + pulses + " buttons/plates/sensors, " + forced + " forceable, "
                + keys + " item keys" + (c.truncated() ? ", TRUNCATED" : "")
                + (c.keyed() ? ", KEYED" : "") + (c.dangerCount() > 0 ? ", " + c.dangerCount() + " traps" : "")));
        StringBuilder line = new StringBuilder(s.status() + ": moves " + s.moved() + "/" + s.movable() + " pistons, work " + s.work());
        for (int i = 0; i < s.set().length; i++) {
            line.append(" | set ").append(lock.inputs().get(s.set()[i]).pos().toShortString()).append(" to ").append(s.setTo()[i]);
        }
        for (int i = 0; i < s.presses().length; i++) {
            CircuitTracer.InputSite site = lock.inputs().get(s.presses()[i]);
            line.append(" | ").append(i + 1).append(site.kind() == LockCircuit.FORCE ? ". force " : ". press ")
                    .append(site.pos().toShortString());
        }
        player.sendSystemMessage(Component.literal(line.toString()));
    }

    private static void tell(ServerPlayer player, String key, ChatFormatting colour) {
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.lock." + key).withStyle(colour), true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.forEach(j -> j.result().cancel(true));
        JOBS.clear();
        STEPS.clear();
    }
}
