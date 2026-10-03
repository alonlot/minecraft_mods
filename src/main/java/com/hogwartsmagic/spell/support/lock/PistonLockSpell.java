package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.mixin.BasePressurePlateTypeAccessor;
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
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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

    /** What {@link #begin} did: started a read, refused one (the caster was told why), or found no piston door. */
    public enum Begin { STARTED, REFUSED, NO_PISTON }

    /** door: the block that was struck, standing for the door's chunk. */
    private record Job(UUID caster, ResourceKey<Level> dimension, BlockPos door, CircuitTracer.TracedLock lock, int rank,
                       Use use, Future<LockSolver.Solution> result, long deadline) {
    }

    /**
     * One caster's playback: the lock's whole input list, and which levers it has already flipped, so every step
     * can check that the world still stands as traced (or as the playback left it). Server thread only.
     */
    private static final class Playback {
        final UUID caster;
        final ResourceKey<Level> dimension;
        final BlockPos door;
        final List<CircuitTracer.InputSite> inputs;
        final boolean[] flipped;

        Playback(UUID caster, ResourceKey<Level> dimension, BlockPos door, List<CircuitTracer.InputSite> inputs) {
            this.caster = caster;
            this.dimension = dimension;
            this.door = door;
            this.inputs = inputs;
            this.flipped = new boolean[inputs.size()];
        }
    }

    /**
     * One thing to do at tick at: work one input, or (several inputs, all forceable) force them together.
     * retries: how often a sculk sensor step has already waited for its sensor to cool down.
     */
    private record Step(Playback play, int[] inputs, long at, boolean last, int retries) {
        Step later(long by, int moreRetries) {
            return new Step(play, inputs, at + by, last, retries + moreRetries);
        }
    }

    private static final int SENSOR_WAIT_TICKS = 40;
    private static final int SENSOR_MAX_RETRIES = 2;

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

    /**
     * Start reading the lock near hit. NO_PISTON if there is no piston door there at all (nothing was said);
     * REFUSED if there is one but the caster was told it cannot be read now; STARTED once the solver has it.
     */
    public static Begin begin(ServerLevel level, BlockPos hit, ServerPlayer caster, int rank, Use use) {
        UUID id = caster.getUUID();
        if (!CircuitTracer.hasDoorPiston(level, hit)) {
            return Begin.NO_PISTON; // cheap probe first: nothing is traced for a block that is no door
        }
        if (JOBS.stream().anyMatch(j -> j.caster().equals(id)) || STEPS.stream().anyMatch(s -> s.play().caster.equals(id))) {
            tell(caster, "busy", ChatFormatting.GRAY);
            inspectLine(caster, use, "BUSY");
            return Begin.REFUSED;
        }
        CircuitTracer.Mode mode = use == Use.REVEAL
                ? new CircuitTracer.Mode(false, LockRules.readsItemKeys(rank))
                : new CircuitTracer.Mode(LockRules.forcesSources(rank), false);
        CircuitTracer.TracedLock lock;
        ForcedSignals.TRACING = true; // a source forced a moment ago must not be traced as on
        try {
            lock = CircuitTracer.trace(level, hit, LockRules.traceBlocks(rank), mode);
        } finally {
            ForcedSignals.TRACING = false;
        }
        if (lock == null) {
            return Begin.NO_PISTON;
        }
        if (lock.circuit().pistonLogic()) {
            tell(caster, "piston_logic", ChatFormatting.GOLD); // pistons that move wiring are not modelled
            inspectLine(caster, use, "PISTON_LOGIC");
            return Begin.REFUSED;
        }
        LockCircuit circuit = lock.circuit();
        int maxInputs = LockRules.maxInputs(rank);
        int maxPresses = LockRules.maxPresses(rank);
        long budget = LockRules.budget(rank);
        Future<LockSolver.Solution> result = SOLVER.submit(() -> LockSolver.solve(circuit, maxInputs, maxPresses, budget));
        JOBS.add(new Job(id, level.dimension(), hit.immutable(), lock, rank, use, result,
                level.getGameTime() + LockRules.SOLVE_TIMEOUT_TICKS));
        if (use != Use.INSPECT) {
            tell(caster, "reading", ChatFormatting.GRAY);
        }
        return Begin.STARTED;
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
                    inspectLine(player, job.use(), "OUT_OF_TIME");
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
                tell(player, "no_solution", ChatFormatting.GOLD);
                inspectLine(player, job.use(), "ERROR");
                continue;
            }
            if (job.use() == Use.INSPECT) {
                report(player, job, solution);
            } else if (explainFailure(player, job, solution)) {
                if (job.use() == Use.REVEAL) {
                    LockHints.show(level, job.lock(), solution);
                    tell(player, "revealed", ChatFormatting.AQUA);
                } else if (job.lock().circuit().truncated()) {
                    // Part of the wiring was never read, so working the levers would be a guess.
                    tell(player, "too_far", ChatFormatting.GOLD);
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
                    circuit.nonForcedInputCount(), LockRules.maxInputs(job.rank())).withStyle(ChatFormatting.GOLD), true);
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
        Playback play = new Playback(job.caster(), job.dimension(), job.door(), sites);
        if (inputsShifted(level, play)) {
            tell(player, "shifted", ChatFormatting.GOLD);
            return;
        }
        LockCircuit circuit = job.lock().circuit();
        for (int input : s.set()) {
            if (shifted(level, sites.get(input), sites.get(input).on())) {
                tell(player, "shifted", ChatFormatting.GOLD);
                return;
            }
        }
        for (int input : s.presses()) {
            if (shifted(level, sites.get(input), sites.get(input).on())) {
                tell(player, "shifted", ChatFormatting.GOLD);
                return;
            }
        }
        // A FORCE index inside set means "hold these sources on together" (one step); the rest are levels to set.
        List<Integer> levels = new ArrayList<>();
        List<Integer> together = new ArrayList<>();
        for (int input : s.set()) {
            (circuit.inputKind(input) == LockCircuit.FORCE ? together : levels).add(input);
        }
        long at = now;
        for (int i = 0; i < levels.size(); i++) {
            boolean last = s.presses().length == 0 && together.isEmpty() && i == levels.size() - 1;
            STEPS.add(new Step(play, new int[] {levels.get(i)}, at, last, 0));
        }
        if (!levels.isEmpty()) {
            at += LockRules.LEVER_SETTLE_TICKS;
        }
        if (!together.isEmpty()) {
            STEPS.add(new Step(play, together.stream().mapToInt(Integer::intValue).toArray(), at,
                    s.presses().length == 0, 0));
            at += LockRules.FORCE_GAP_TICKS;
        }
        for (int i = 0; i < s.presses().length; i++) {
            CircuitTracer.InputSite site = sites.get(s.presses()[i]);
            STEPS.add(new Step(play, new int[] {s.presses()[i]}, at, i == s.presses().length - 1, 0));
            at += site.kind() == LockCircuit.FORCE ? LockRules.FORCE_GAP_TICKS : LockRules.PRESS_GAP_TICKS;
        }
    }

    /**
     * The block is unloaded or gone, or a lever or button is not in the state it is expected to be in
     * (expectedOn is ignored for every other kind of input).
     */
    private static boolean shifted(ServerLevel level, CircuitTracer.InputSite site, boolean expectedOn) {
        if (!level.isLoaded(site.pos())) {
            return true;
        }
        BlockState state = level.getBlockState(site.pos());
        if (state.getBlock() != site.block()) {
            return true;
        }
        int kind = site.kind();
        return (kind == LockCircuit.LEVER || kind == LockCircuit.PULSE) && CircuitTracer.isOn(state) != expectedOn;
    }

    /** The block is unloaded or has been replaced. */
    private static boolean gone(ServerLevel level, CircuitTracer.InputSite site) {
        return !level.isLoaded(site.pos()) || level.getBlockState(site.pos()).getBlock() != site.block();
    }

    /**
     * Some lever or button of the lock no longer stands as the playback expects: a lever the playback has not
     * flipped as traced, one it has flipped as flipped, a button (which springs back) as traced. A sculk sensor
     * fires on its own when it hears the playback, so for it only the block is checked, never its power or
     * phase; the step that works a sensor waits for it separately.
     */
    private static boolean inputsShifted(ServerLevel level, Playback play) {
        for (int i = 0; i < play.inputs.size(); i++) {
            CircuitTracer.InputSite site = play.inputs.get(i);
            int kind = site.kind();
            if (kind != LockCircuit.LEVER && kind != LockCircuit.PULSE) {
                continue;
            }
            if (site.block() instanceof SculkSensorBlock) {
                if (gone(level, site)) {
                    return true;
                }
                continue;
            }
            boolean expected = kind == LockCircuit.LEVER && play.flipped[i] ? !site.on() : site.on();
            if (shifted(level, site, expected)) {
                return true;
            }
        }
        return false;
    }

    private static void runSteps(ServerLevel level, long now) {
        Set<Playback> aborted = new HashSet<>();
        Map<Playback, Long> deferred = new HashMap<>();
        List<Step> due = new ArrayList<>();
        for (Step step : STEPS) {
            if (step.play().dimension == level.dimension() && step.at() <= now) {
                due.add(step);
            }
        }
        STEPS.removeAll(due);
        for (Step step : due) {
            Playback play = step.play();
            if (aborted.contains(play)) {
                continue;
            }
            Long shift = deferred.get(play);
            if (shift != null) {
                STEPS.add(step.later(shift, 0)); // an earlier step of this playback is waiting: keep the gaps
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(play.caster);
            if (player == null || !player.isAlive() || player.level() != level) {
                abort(aborted, play, player); // logged out, died or left the dimension
                continue;
            }
            if (step.last() && !level.isLoaded(play.door)) {
                abort(aborted, play, player); // never load a chunk to finish the door
                continue;
            }
            if (inputsShifted(level, play) || stepShifted(level, play, step)) {
                abort(aborted, play, player); // vanished, unloaded, or flipped or pressed by someone else
                continue;
            }
            CircuitTracer.InputSite first = play.inputs.get(step.inputs()[0]);
            if (first.block() instanceof SculkSensorBlock && !SculkSensorBlock.canActivate(level.getBlockState(first.pos()))) {
                // The sensor is active or cooling down (it may have heard the playback itself): wait, twice at most.
                if (step.retries() >= SENSOR_MAX_RETRIES) {
                    abort(aborted, play, player);
                    continue;
                }
                long by = now + SENSOR_WAIT_TICKS - step.at();
                ListIterator<Step> later = STEPS.listIterator();
                while (later.hasNext()) {
                    Step other = later.next();
                    if (other.play() == play && other.at() > step.at()) {
                        later.set(other.later(by, 0));
                    }
                }
                STEPS.add(step.later(by, 1));
                deferred.put(play, by);
                continue;
            }
            boolean acted = true;
            for (int input : step.inputs()) {
                CircuitTracer.InputSite site = play.inputs.get(input);
                BlockPos pos = site.pos();
                if (site.kind() == LockCircuit.FORCE) {
                    ForcedSignals.force(level, pos, LockRules.FORCE_TICKS);
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.4f);
                } else {
                    acted = operate(level, pos, level.getBlockState(pos));
                }
                if (!acted) {
                    break;
                }
                if (site.kind() == LockCircuit.LEVER) {
                    play.flipped[input] = true;
                }
                level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        12, 0.3, 0.3, 0.3, 0.4);
            }
            if (!acted) {
                abort(aborted, play, player); // a button already down is not skipped: the lock is not as read
                continue;
            }
            if (step.last()) {
                MasteryAwards.onBlockEffect(player, SpellType.ALOHOMORA);
                tell(player, "opened", ChatFormatting.AQUA);
            }
        }
        if (!aborted.isEmpty()) {
            STEPS.removeIf(s -> aborted.contains(s.play()));
        }
    }

    /** The inputs this step works are gone or not as traced (a sculk sensor only has to still be a sensor). */
    private static boolean stepShifted(ServerLevel level, Playback play, Step step) {
        for (int input : step.inputs()) {
            CircuitTracer.InputSite site = play.inputs.get(input);
            if (site.block() instanceof SculkSensorBlock ? gone(level, site) : shifted(level, site, site.on())) {
                return true;
            }
        }
        return false;
    }

    /** Ends a playback and tells the caster, if still around, that the lock changed under the spell. */
    private static void abort(Set<Playback> aborted, Playback play, ServerPlayer player) {
        aborted.add(play);
        if (player != null) {
            tell(player, "shifted", ChatFormatting.GOLD);
        }
    }

    /**
     * Flip a lever, or press a button, plate or sculk sensor, as a hand or a foot would. False if nothing
     * happened (a button already down, a plate or sensor that cannot be set off).
     */
    private static boolean operate(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LeverBlock lever) {
            lever.pull(state, level, pos, null);
            return true;
        } else if (block instanceof ButtonBlock button) {
            if (state.getValue(ButtonBlock.POWERED)) {
                return false;
            }
            button.press(state, level, pos, null);
            return true;
        } else if (block instanceof SculkSensorBlock sensor) {
            if (!SculkSensorBlock.canActivate(state)) {
                return false;
            }
            sensor.activate(null, level, pos, state, 15, 0);
            return true;
        } else if (block instanceof BasePressurePlateBlock) {
            BlockState pressed = state.hasProperty(BlockStateProperties.POWERED)
                    ? state.setValue(BlockStateProperties.POWERED, true)
                    : state.hasProperty(BlockStateProperties.POWER) ? state.setValue(BlockStateProperties.POWER, 15) : state;
            if (pressed == state) {
                return false;
            }
            level.setBlock(pos, pressed, Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, block);
            level.updateNeighborsAt(pos.below(), block);
            level.scheduleTick(pos, block, 20); // the plate's own tick finds nobody on it and lets go
            level.playSound(null, pos, ((BasePressurePlateTypeAccessor) block).hogwartsmagic$getType().pressurePlateClickOn(),
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            level.gameEvent(null, GameEvent.BLOCK_ACTIVATE, pos);
            return true;
        }
        return false;
    }

    /**
     * /lockinfo: what the lock is and what the solver found, without touching anything. Two chat lines.
     * <pre>
     * Lock (rank R): P door pistons, B blocks, N nodes; L levers, U buttons/plates/sensors, F forceable,
     *     K item keys[, TRUNCATED][, KEYED][, T traps]
     * STATUS: moves M/V pistons, work W[ | set X Y Z to LEVEL]...[ | force together X Y Z; X Y Z]
     *     [ | I. press X Y Z | J. force X Y Z]...
     * </pre>
     * The first line is the lock (the bracketed parts appear only when they apply); the second the solver's
     * answer: its Status name, door pistons moved of those any input reaches, node updates spent, then each
     * lever or item key to set (position and level), the forceable sources held on together in one step if the
     * answer needs several at once (one "force together" item listing their positions, separated by semicolons),
     * and each press or forced source in order, numbered from 1.
     * Positions are x, y, z short strings. When the read does not finish, one word is sent instead:
     * BUSY, PISTON_LOGIC, OUT_OF_TIME or ERROR (see {@link #inspectLine}).
     */
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
        List<String> together = new ArrayList<>();
        for (int i = 0; i < s.set().length; i++) {
            CircuitTracer.InputSite site = lock.inputs().get(s.set()[i]);
            if (site.kind() == LockCircuit.FORCE) {
                together.add(site.pos().toShortString());
            } else {
                line.append(" | set ").append(site.pos().toShortString()).append(" to ").append(s.setTo()[i]);
            }
        }
        if (!together.isEmpty()) {
            line.append(" | force together ").append(String.join("; ", together));
        }
        for (int i = 0; i < s.presses().length; i++) {
            CircuitTracer.InputSite site = lock.inputs().get(s.presses()[i]);
            line.append(" | ").append(i + 1).append(site.kind() == LockCircuit.FORCE ? ". force " : ". press ")
                    .append(site.pos().toShortString());
        }
        player.sendSystemMessage(Component.literal(line.toString()));
    }

    /** For /lockinfo only: a plain word the user can paste back when the read stopped before a report. */
    private static void inspectLine(ServerPlayer player, Use use, String word) {
        if (use == Use.INSPECT) {
            player.sendSystemMessage(Component.literal(word));
        }
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
