# Alohomora and Revelio Piston Locks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The two spells use the same lock reader.
- **Alohomora** works out a piston door's lock and works it itself:
  - At Expert, it opens a small lock (up to 4 levers or buttons).
  - At Master, it cracks the full lock: lever combinations of up to 20 switches, button orders of up to 4 presses, and, where no switch will do, forcing a comparator, observer or other redstone source to fire for 3 seconds.
- **Revelio** works out the same lock but *shows* the answer: glowing outlines and floating labels on the levers to flip and the buttons to press, numbered in order. At Master it also covers item keys: which item frame to fill and how many times to turn it, and how many items to put in a chest.

**Architecture:**
- **Trace (server thread).** `CircuitTracer` reads the redstone around the pistons near the struck block into a `LockCircuit`: a plain logic graph with no Minecraft types.
  - Inputs are levers, buttons/plates/sculk sensors, forceable sources (Alohomora Master) and item keys (Revelio Master).
  - Goals are the door pistons. Dangers are dispensers, droppers and TNT.
- **Solve (one background thread).** `LockSolver` simulates every setting, fewest actions first, and never one that powers a danger. It returns the setting that moves the most door pistons. Real switches are always tried before forcing a source.
- **Act on the answer.** `PistonLockSpell` has three uses:
  - **open** (Alohomora): play the answer back on the real blocks;
  - **reveal** (Revelio): hand it to `LockHints`, which spawns glowing block displays and text labels for 15 seconds;
  - **inspect** (`/lockinfo`): print it.
- **Forcing.** `ForcedSignals` is a short-lived per-dimension map. A mixin on `BlockStateBase.getSignal` and `getDirectSignal` makes a forced comparator, observer or source read as 15 for its 3 seconds.
- **Testing.** All logic is pure Java and unit-tested. The world-reading parts are checked in game with `/lockinfo`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, Mixin 0.8 (already configured, `hogwartsmagic.mixins.json`), JUnit 5.

**Spec:** No spec file. The requirements were agreed with the user in chat on 2026-10-03 and are copied below; they are binding.

## Requirements (from the user)

1. Alohomora at Expert (rank 3) opens a piston door with a *small* lock. At Master (rank 4) it opens the *full* lock.
2. The mod works the lock out itself: it simulates every combination and performs only the one that really opens the door.
3. Combination locks open at Master.
4. No protection checks on the piston-door path.
5. The search cap is big.
6. Alohomora also "simulates different things that can output redstone, like a comparator, sculk sensor or observer, and just forces redstone output from there for a few seconds."
7. Revelio "simulates the output of nearby redstone doors and highlights what should be done": highlight a button, or show the item position in an item frame, or what to put in a chest, and so on.

## Rulings made while planning (record them in the ledger)

- Ruling: "small lock" = at most 4 inputs and one press, tracing 512 blocks. "Full lock" = at most 20 inputs and up to 4 presses, tracing 8192 blocks. Why: a 4-switch lock has 16 settings, and 8192 blocks covers any player-built door with its wiring. Cost if wrong: numbers in `LockRules`.
- Ruling: forcing a source (requirement 6) is Master only. The solver tries it only after every real-switch setting has failed to open the door. At most 8 sources, nearest to the door first, become forceable, and they don't count toward the 20-input cap. Why: forcing can open almost anything, so it is the last resort of the top rank. Cost if wrong: one rank check.
- Ruling: a forced source outputs 15 for 60 ticks. A comparator or observer outputs only out of its front, as it normally does; a daylight sensor, target block, trapped chest, tripwire hook and similar sources output in all directions. Sculk sensors are not forced; they are activated for real, like a button.
- Ruling: Revelio gets the same ranks: Expert reads small locks, Master full locks plus item keys (up to 3 frames or containers per lock). Revelio never forces anything; it shows what a person can do. Its hints are world entities that anyone nearby can see, for 15 seconds.
- Ruling: only player casters use any of this. Why: boss NPCs cast at rank 3, and an NPC cracking a player's base door was not asked for.
- Ruling: the spell moves the door pistons from whatever state they are in, so a cast at an open door may close it. Why: a door can be built either way round.
- Ruling: the solver runs on one daemon worker thread, bounded by a work budget (Master 200M node updates) and a 10-second timeout.
- Ruling: some things are not modelled: pistons used as logic, exact repeater delays and pulse lengths, and item frames that hold a *specific* item (any item works for the comparator; only the rotation matters). Without forcing or item keys, a comparator reading a container stays at its current value and the lock is "keyed".
- Ruling: chunks are never loaded. An unloaded block ends the trace there and marks it truncated.
- Ruling: Alohomora pays XP once, when the last action of a found solution lands. Revelio's XP is unchanged (it is paid by the beam hit, as today).
- Ruling: hint entities carry the tag `hogwartsmagic_lock_hint`. Any such entity that loads from disk after a restart is refused on join, and a glowing frame is un-glowed when the server stops. Why: display entities are saved with the world.

## Global Constraints

- Server authoritative. No class in this plan references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. No packets are added; hints are vanilla display entities. The worker thread touches only `LockCircuit` and `LockSolver`.
- Never load a chunk: every world read goes after `level.isLoaded(pos)`. No per-tick `getPersistentData` reads. The mixin's fast path is one `volatile boolean` read.
- Use if-chains on enums, not `switch` (the mod's switch-map helper has failed to load before).
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -ci javaw`, and the expected output is `0`. Otherwise stop and ask the user to close Minecraft.
- Build: `./gradlew compileJava test`. Dedicated-server boot check: `./gradlew runServer` until `Done (`, then `stop`.
- Commit per task:
  - Stage only the files the task names, never `.claude/settings.local.json`.
  - Commit with `git commit -m "..." -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`.
  - No apostrophes in bash heredoc bodies (this plan uses none).
  - Never push.
- `en_us.json` must stay valid JSON. Replaced lines keep their trailing comma, and inserted lines end with a comma and go after a line that already ends with one.
- Line numbers drift. Every edit names the exact text to find.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockRules.java` | Pure: per-rank caps, timings, container item maths |
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/LockCircuit.java` (built by the cloud, `fd45d0b`) | Pure: immutable logic graph; add button/forced inputs, goals, dangers, flags |
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/LockSimulator.java` (built by the cloud, `1c0942a`) | Pure: runs a circuit to rest; widen inputs, add goal reads |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java` | Pure: searches settings and presses |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java` | World → `LockCircuit` (server thread) |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/ForcedSignals.java` | Forced sources per dimension; their expiry |
| Create `src/main/java/com/hogwartsmagic/mixin/BlockStateSignalMixin.java` | `getSignal` / `getDirectSignal` read `ForcedSignals` |
| Modify `src/main/resources/hogwartsmagic.mixins.json` | Register the mixin |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java` | Jobs, worker thread, open / reveal / inspect, messages, XP |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockHints.java` | Revelio's glowing outlines and labels, their cleanup |
| Create `src/main/java/com/hogwartsmagic/command/LockInfoCommand.java` | `/lockinfo [rank]` |
| Modify `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java` | Hand piston doors to `PistonLockSpell` (open) |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Revelio block hit hands the door to `PistonLockSpell` (reveal) |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Register `/lockinfo` |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Descriptions, rank lines, messages, hint labels |
| Create tests `src/test/java/com/hogwartsmagic/spell/support/lock/{LockRulesTest,LockCircuitGoalsTest,LockSolverTest}.java` | |

## Pre-flight table (the controller writes it to the ledger)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2..T8 | `LockRules` constants and getters | names identical everywhere |
| T2 → T3 | `LockCircuit` package fields, input kinds, `inputDomain`, simulator `setLevel` / `setInput` | T3 uses exactly these |
| T2 → T4 | Builder `input` (LEVER/PULSE/FORCE), `analog`, `constant`, `node`, `link`, `side` (into diodes only), `goal`, `danger`, `markKeyed`, `markTruncated` | T4 uses exactly these; `side` throws if aimed at anything but a repeater or comparator |
| T3 → T6, T7 | `Solution(status, set, setTo, presses, moved, movable, work)` | identical |
| T4 → T6, T7 | `trace(level, hit, cap, Mode)`, `Mode`, `TracedLock`, `InputSite` and its source constants, `isOn` | identical |
| T5 → T6 | `ForcedSignals.force(ServerLevel, BlockPos, int ticks)` | identical |
| T6 → T7, T8 | `PistonLockSpell.begin(level, hit, player, rank, Use)`, `Use {OPEN, REVEAL, INSPECT}` | identical |
| T7 → T6 | `LockHints.show(ServerLevel, TracedLock, Solution)` | T6 calls it; T7 creates it. T6 compiles only after T7 exists, **so T6 adds a stub `LockHints.show` that T7 replaces** |

---

### Task 1: LockRules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/LockRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/LockRulesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockRulesTest {
    @Test
    void pistonDoorsFromExpert() {
        assertFalse(LockRules.readsLocks(2));
        assertTrue(LockRules.readsLocks(3));
        assertTrue(LockRules.readsLocks(4));
    }

    @Test
    void masterOnlyExtras() {
        assertFalse(LockRules.forcesSources(3));
        assertTrue(LockRules.forcesSources(4));
        assertFalse(LockRules.readsItemKeys(3));
        assertTrue(LockRules.readsItemKeys(4));
    }

    @Test
    void expertReadsASmallLock() {
        assertEquals(512, LockRules.traceBlocks(3));
        assertEquals(4, LockRules.maxInputs(3));
        assertEquals(1, LockRules.maxPresses(3));
        assertEquals(2_000_000L, LockRules.budget(3));
    }

    @Test
    void masterReadsTheFullLock() {
        assertEquals(8192, LockRules.traceBlocks(4));
        assertEquals(20, LockRules.maxInputs(4));
        assertEquals(4, LockRules.maxPresses(4));
        assertEquals(200_000_000L, LockRules.budget(4));
    }

    @Test
    void badRanksClamp() {
        assertEquals(0, LockRules.maxInputs(0));
        assertEquals(20, LockRules.maxInputs(9));
    }

    @Test
    void timingsAndCaps() {
        assertEquals(6, LockRules.DOOR_RADIUS);
        assertEquals(32, LockRules.MAX_DOOR_PISTONS);
        assertEquals(20, LockRules.LEVER_SETTLE_TICKS);
        assertEquals(40, LockRules.PRESS_GAP_TICKS);
        assertEquals(60, LockRules.FORCE_TICKS);
        assertEquals(80, LockRules.FORCE_GAP_TICKS);
        assertEquals(8, LockRules.MAX_FORCED_SOURCES);
        assertEquals(3, LockRules.MAX_ITEM_KEYS);
        assertEquals(300, LockRules.HINT_TICKS);
        assertEquals(200, LockRules.SOLVE_TIMEOUT_TICKS);
    }

    @Test
    void containerSignalMatchesVanilla() {
        // vanilla: floor(fullness * 14) + (any item ? 1 : 0), fullness in full-stack units over all slots
        assertEquals(0, LockRules.containerSignal(0, 27));
        assertEquals(1, LockRules.containerSignal(1, 27));
        assertEquals(15, LockRules.containerSignal(27 * 64, 27));
        assertEquals(1, LockRules.containerSignal(123, 27));
        assertEquals(2, LockRules.containerSignal(124, 27));
    }

    @Test
    void itemsForSignal() {
        assertArrayEquals(new int[] {0, 0}, LockRules.itemsForSignal(0, 27));
        assertArrayEquals(new int[] {1, 123}, LockRules.itemsForSignal(1, 27));
        assertArrayEquals(new int[] {124, 246}, LockRules.itemsForSignal(2, 27));
        assertArrayEquals(new int[] {1728, 1728}, LockRules.itemsForSignal(15, 27));
        assertArrayEquals(new int[] {1, 4}, LockRules.itemsForSignal(1, 1));
    }
}
```

Hand-check: a 27-slot chest holds 1728 items. Signal 2 needs `floor(n·14 / 1728) ≥ 1`, so n ≥ 124 (1728/14 = 123.4). For one slot of 64, signal 1 covers `n·14/64 < 1`, so n ≤ 4.

- [ ] **Step 2: Run it and see it fail**

Run: `tasklist | grep -ci javaw` (expect `0`), then `./gradlew test --tests "*LockRulesTest"`. Expected: compilation failure.

- [ ] **Step 3: Write `LockRules`**

```java
package com.hogwartsmagic.spell.support.lock;

/** Alohomora and Revelio on piston doors: how much lock each rank can read. Pure numbers. */
public final class LockRules {
    /** Pistons this far (a cube) from the struck block count as the door. */
    public static final int DOOR_RADIUS = 6;
    public static final int MAX_DOOR_PISTONS = 32;
    /** Wait after flipping levers before the first press. */
    public static final int LEVER_SETTLE_TICKS = 20;
    /** Between two presses: longer than any button, plate or sculk sensor stays on. */
    public static final int PRESS_GAP_TICKS = 40;
    /** A forced source reads 15 for this long. */
    public static final int FORCE_TICKS = 60;
    /** Wait after starting a forced source before the next action. */
    public static final int FORCE_GAP_TICKS = 80;
    public static final int MAX_FORCED_SOURCES = 8;
    /** Frames and containers Revelio may solve for in one lock. */
    public static final int MAX_ITEM_KEYS = 3;
    public static final int HINT_TICKS = 300;
    public static final int SOLVE_TIMEOUT_TICKS = 200;

    private static final int[] TRACE_BLOCKS = {0, 0, 512, 8192};
    private static final int[] MAX_INPUTS = {0, 0, 4, 20};
    private static final int[] MAX_PRESSES = {0, 0, 1, 4};
    private static final long[] BUDGET = {0L, 0L, 2_000_000L, 200_000_000L};

    private LockRules() {
    }

    public static boolean readsLocks(int rank) {
        return rank >= 3;
    }

    public static boolean forcesSources(int rank) {
        return rank >= 4;
    }

    public static boolean readsItemKeys(int rank) {
        return rank >= 4;
    }

    public static int traceBlocks(int rank) {
        return TRACE_BLOCKS[index(rank)];
    }

    public static int maxInputs(int rank) {
        return MAX_INPUTS[index(rank)];
    }

    public static int maxPresses(int rank) {
        return MAX_PRESSES[index(rank)];
    }

    /** Node updates the solver may spend before it settles for the best it has found. */
    public static long budget(int rank) {
        return BUDGET[index(rank)];
    }

    /** The comparator signal of a container holding this many 64-stacking items, as vanilla computes it. */
    public static int containerSignal(int items, int slots) {
        if (items <= 0 || slots <= 0) {
            return 0;
        }
        return Math.min(15, items * 14 / (64 * slots) + 1);
    }

    /** {fewest, most} 64-stacking items that give exactly this signal. */
    public static int[] itemsForSignal(int signal, int slots) {
        int capacity = 64 * slots;
        if (signal <= 0 || slots <= 0) {
            return new int[] {0, 0};
        }
        int min = signal == 1 ? 1 : ceilDiv((signal - 1) * capacity, 14);
        int max = signal >= 15 ? capacity : Math.min(capacity, ceilDiv(signal * capacity, 14) - 1);
        return new int[] {min, max};
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static int index(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

Check `itemsForSignal(15, 27)`: `min = ceil(14·1728/14) = 1728` and `max = capacity = 1728`. Check `(2, 27)`: `min = ceil(1728/14) = 124` and `max = ceil(3456/14) − 1 = 247 − 1 = 246`. Both match the test.

- [ ] **Step 4: Run the test and see it pass.** Run `./gradlew test --tests "*LockRulesTest"`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockRules.java src/test/java/com/hogwartsmagic/spell/support/lock/LockRulesTest.java
git commit -m "Add the per-rank numbers for reading piston door locks" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Extend the existing LockCircuit and LockSimulator

**Already built.** The cloud session built these as commits `5e6f874` and `f4fbc12` on GitHub `claude/keen-lamport-g1ykt4`; they were cherry-picked locally as `fd45d0b` and `1c0942a`. What's there now:
- `src/main/java/com/hogwartsmagic/spell/support/lock/LockCircuit.java`
- `src/main/java/com/hogwartsmagic/spell/support/lock/LockSimulator.java`
- `src/test/java/com/hogwartsmagic/spell/support/lock/LockSimulatorTest.java`, which matches the plan's 8 tests.
- `src/test/java/com/hogwartsmagic/spell/support/lock/LockSimulatorExtraTest.java`, with 2 more tests.

**What it already does (do not change):**
- Node kinds: `MAX=3`, `NOT=4`, `REPEATER=5`, `COMPARE=6`, `SUBTRACT=7`, `OBSERVER=8`, `INPUT=9`, `CONSTANT=10`. Input kinds: `LEVER=1`, `ANALOG=2`.
- Builder: `input(LEVER, on)`, `analog(initial, domain)`, `constant(level)`, `node(kind, initial)`, `link(from, to, loss)`, `side(from, to, loss)` (into repeaters and comparators only), and `build()`. It validates indices, levels and links, and throws `IllegalArgumentException` on bad ones.
- Circuit getters: `nodeCount()`, `inputCount()`, `inputKind(i)`, `inputDomain(i)`, `inputNode(i)`, `kind(node)`.
- Simulator: `reset()`, `settle()`, `settle(maxTicks)`, `setInput(i, on)` (levers only today), `setLevel(i, level)` (analog only today), `value(node)`, `snapshot()`, `restore(int[])` and `work()`. It also has `MAX_TICKS = 256` and `PULSE_TICKS = 2`.
- Timing:
  - Dust (`MAX`) is instant, the least fixpoint, recomputed every tick.
  - Torches, repeaters, comparators and observers take one tick, all updating at the same moment.
  - An observer pulse lasts 2 ticks.
  - The simulator is primed at construction, so no observer fires at start.
  - Inputs changed together before one `settle()` are one simultaneous change. Task 3's solver settles after every action, so press order is honoured.

**What later tasks need that is missing:**
- Input kinds `PULSE` (buttons, plates, sculk sensors) and `FORCE` (sources Alohomora Master forces), each with domain {0, 15}.
- Goals (door pistons), dangers (dispensers, TNT), the `keyed` and `truncated` flags, and `controllableGoals()`.
- `goalPowered` and `dangerPowered` on the simulator.
- `setLevel` must accept any input whose domain holds the level, levers included: the solver sets levers and item keys through one call. `setInput` must accept `LEVER`, `PULSE` and `FORCE` (any non-analog input).

**Interfaces produced** (used by Tasks 3, 4, 6, 7 and 8):
- `LockCircuit.PULSE = 11` and `LockCircuit.FORCE = 12`.
- Builder: `input(kind, on)` for `LEVER`, `PULSE` or `FORCE`; `goal(int node)`; `danger(int node)`; `markKeyed()`; `markTruncated()`.
- Circuit: `goalCount()`, `goalNode(int)`, `dangerCount()`, `dangerNode(int)`, `keyed()`, `truncated()` and `controllableGoals()`.
- Simulator: `goalPowered(int goal)`, `dangerPowered(int danger)`, with `setInput` and `setLevel` widened as above.
- The circuit's node count is `nodeCount()`, and settling is bounded by the simulator's own `MAX_TICKS` (256).

- [ ] **Step 1: Write the failing test** `src/test/java/com/hogwartsmagic/spell/support/lock/LockCircuitGoalsTest.java`

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockCircuitGoalsTest {
    @Test
    void pulseAndForceInputsAreOnOff() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int button = b.input(LockCircuit.PULSE, false);
        int forced = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(button, door, 0);
        b.link(forced, door, 0);
        LockCircuit c = b.build();
        assertEquals(LockCircuit.PULSE, c.inputKind(0));
        assertEquals(LockCircuit.FORCE, c.inputKind(1));
        assertArrayEquals(new int[] {0, 15}, c.inputDomain(0));
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        sim.setInput(1, true);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(door));
    }

    @Test
    void setLevelWorksOnLeversWithinTheirDomain() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.input(LockCircuit.LEVER, false);
        LockSimulator sim = new LockSimulator(b.build());
        sim.setLevel(0, 15);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(0));
        assertThrows(IllegalArgumentException.class, () -> sim.setLevel(0, 7));
    }

    @Test
    void setInputRefusesAnalog() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.analog(0, new int[] {0, 5});
        LockSimulator sim = new LockSimulator(b.build());
        assertThrows(IllegalArgumentException.class, () -> sim.setInput(0, true));
    }

    @Test
    void goalsDangersAndFlags() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int reached = b.node(LockCircuit.MAX, 0);
        b.link(lever, reached, 0);
        int stranded = b.node(LockCircuit.MAX, 0);
        int tnt = b.node(LockCircuit.MAX, 0);
        b.link(lever, tnt, 0);
        b.goal(reached);
        b.goal(reached);
        b.goal(stranded);
        b.danger(tnt);
        LockCircuit c = b.build();
        assertEquals(2, c.goalCount());
        assertEquals(1, c.dangerCount());
        assertEquals(1, c.controllableGoals());
        assertFalse(c.keyed());
        assertFalse(c.truncated());
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        assertFalse(sim.goalPowered(0));
        sim.setInput(0, true);
        assertTrue(sim.settle());
        assertTrue(sim.goalPowered(0));
        assertTrue(sim.dangerPowered(0));
    }

    @Test
    void flagsStick() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.markKeyed();
        b.markTruncated();
        LockCircuit c = b.build();
        assertTrue(c.keyed());
        assertTrue(c.truncated());
    }

    @Test
    void badGoalIndexThrows() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        assertThrows(IllegalArgumentException.class, () -> b.goal(3));
        assertThrows(IllegalArgumentException.class, () -> b.danger(-1));
    }
}
```

- [ ] **Step 2: Run it and see it fail.** Run `tasklist | grep -ci javaw` (expect `0`), then `./gradlew test --tests "*LockCircuitGoalsTest"`. Expected: compilation failure (no `PULSE`, `goal`, and so on).

- [ ] **Step 3: Extend `LockCircuit`.** Make these exact edits.

Below `public static final int CONSTANT = 10;`, add:

```java
    /** A button, plate or sculk sensor: pressed, then springs back. */
    public static final int PULSE = 11;
    /** A comparator, observer or other source forced on for a few seconds (Alohomora Master). */
    public static final int FORCE = 12;
```

Below `private final int[][] inputDomains;`, add:

```java
    private final int[] goals;
    private final int[] dangers;
    private final boolean keyed;
    private final boolean truncated;
```

At the end of the constructor, after `inputDomains = b.inputDomains.toArray(new int[0][]);`, add:

```java
        goals = b.goals.stream().distinct().mapToInt(Integer::intValue).toArray();
        dangers = b.dangers.stream().distinct().mapToInt(Integer::intValue).toArray();
        keyed = b.keyed;
        truncated = b.truncated;
```

After the `kind(int node)` getter, add:

```java
    public int goalCount() {
        return goals.length;
    }

    public int goalNode(int goal) {
        return goals[goal];
    }

    public int dangerCount() {
        return dangers.length;
    }

    public int dangerNode(int danger) {
        return dangers[danger];
    }

    /** Part of this lock is an item, not a switch, and the reader could not set it. */
    public boolean keyed() {
        return keyed;
    }

    /** The trace stopped at its block cap or an unloaded chunk: some wiring is missing. */
    public boolean truncated() {
        return truncated;
    }

    /** Door pistons any input reaches at all: the most a solution can move. */
    public int controllableGoals() {
        int n = kinds.length;
        List<List<Integer>> readers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            readers.add(new ArrayList<>());
        }
        for (int to = 0; to < n; to++) {
            for (int from : mainFrom[to]) {
                readers.get(from).add(to);
            }
            for (int from : sideFrom[to]) {
                readers.get(from).add(to);
            }
        }
        boolean[] seen = new boolean[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int node : inputNodes) {
            if (!seen[node]) {
                seen[node] = true;
                queue.add(node);
            }
        }
        while (!queue.isEmpty()) {
            for (int next : readers.get(queue.poll())) {
                if (!seen[next]) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        int count = 0;
        for (int goal : goals) {
            if (seen[goal]) {
                count++;
            }
        }
        return count;
    }
```

In `Builder`, below `private final List<int[]> inputDomains = new ArrayList<>();`, add:

```java
        private final List<Integer> goals = new ArrayList<>();
        private final List<Integer> dangers = new ArrayList<>();
        private boolean keyed;
        private boolean truncated;
```

Replace the body of `Builder.input`:

```java
        public int input(int kind, boolean on) {
            if (kind != LEVER) {
                throw new IllegalArgumentException("input kind must be LEVER: " + kind);
            }
            int node = add(INPUT, on ? 15 : 0);
            inputNodes.add(node);
            inputKinds.add(LEVER);
            inputDomains.add(new int[] {0, 15});
            return node;
        }
```

with:

```java
        /** A LEVER, PULSE or FORCE input; each is off (0) or on (15). */
        public int input(int kind, boolean on) {
            if (kind != LEVER && kind != PULSE && kind != FORCE) {
                throw new IllegalArgumentException("input kind must be LEVER, PULSE or FORCE: " + kind);
            }
            int node = add(INPUT, on ? 15 : 0);
            inputNodes.add(node);
            inputKinds.add(kind);
            inputDomains.add(new int[] {0, 15});
            return node;
        }
```

After `Builder.side(...)`, add:

```java
        /** A door piston: the solver tries to change whether it is powered. */
        public void goal(int node) {
            checkNode(node);
            goals.add(node);
        }

        /** A dispenser, dropper or TNT: no solution may power it if it was unpowered. */
        public void danger(int node) {
            checkNode(node);
            dangers.add(node);
        }

        public void markKeyed() {
            keyed = true;
        }

        public void markTruncated() {
            truncated = true;
        }

        private void checkNode(int node) {
            if (node < 0 || node >= kinds.size()) {
                throw new IllegalArgumentException("node index out of range: " + node);
            }
        }
```

- [ ] **Step 4: Widen the `LockSimulator` input methods and add the goal reads.** Replace:

```java
    public void setInput(int input, boolean on) {
        if (circuit.inputKind(input) != LockCircuit.LEVER) {
            throw new IllegalArgumentException("input is not a lever: " + input);
        }
        applyInput(input, on ? 15 : 0);
    }

    public void setLevel(int input, int level) {
        if (circuit.inputKind(input) != LockCircuit.ANALOG) {
            throw new IllegalArgumentException("input is not analog: " + input);
        }
        if (!circuit.inDomain(input, level)) {
            throw new IllegalArgumentException("level not in domain: " + level);
        }
        applyInput(input, level);
    }
```

with:

```java
    /** A lever, button, plate, sensor or forced source: on (15) or off (0). */
    public void setInput(int input, boolean on) {
        if (circuit.inputKind(input) == LockCircuit.ANALOG) {
            throw new IllegalArgumentException("input is analog, use setLevel: " + input);
        }
        applyInput(input, on ? 15 : 0);
    }

    /** Any input, to any level in its domain (a lever's domain is 0 and 15). */
    public void setLevel(int input, int level) {
        if (!circuit.inDomain(input, level)) {
            throw new IllegalArgumentException("level not in domain: " + level);
        }
        applyInput(input, level);
    }

    public boolean goalPowered(int goal) {
        return value[circuit.goalNode(goal)] > 0;
    }

    public boolean dangerPowered(int danger) {
        return value[circuit.dangerNode(danger)] > 0;
    }
```

- [ ] **Step 5: Run every lock test.** Run `./gradlew test --tests "*.lock.*"`. Expected: `LockSimulatorTest` (8), `LockSimulatorExtraTest` (2) and `LockCircuitGoalsTest` (6) all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockCircuit.java src/main/java/com/hogwartsmagic/spell/support/lock/LockSimulator.java src/test/java/com/hogwartsmagic/spell/support/lock/LockCircuitGoalsTest.java
git commit -m "Give lock circuits button and forced inputs, door goals, traps and flags" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: LockSolver

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverTest.java`

**Interfaces produced:**
- `LockSolver.solve(LockCircuit c, int maxInputs, int maxPresses, long budget)`, which returns a `Solution`.
- `record Solution(Status status, int[] set, int[] setTo, int[] presses, int moved, int movable, long work)`. `set[i]` is an input index (lever or item key) to set to level `setTo[i]`. `presses` holds the input indexes to press or force, in order.
- `enum Status { SOLVED, NO_DOOR, TOO_MANY_INPUTS, UNSTABLE, NO_SOLUTION, OUT_OF_TIME }`.

**Search rules:**
- **Settable inputs:** `LEVER` and `ANALOG`. **Pressable inputs:** `PULSE` in pass 1, and `PULSE` plus `FORCE` in pass 2. Pass 2 runs only if the circuit has `FORCE` inputs and pass 1 didn't move every controllable piston.
- **Cap:** inputs that aren't `FORCE` count toward `maxInputs`.
- **Cost:** the number of inputs set plus the number of presses, tried from 1 upwards.
  - At each cost, `k` (inputs set) counts down from `min(cost, settable)` to `max(0, cost − pressCap)`.
  - Each set input runs through every level in its domain except its current one.
  - Every press order of length `cost − k` is tried; repeats are allowed.
- **An attempt:**
  1. Restore the baseline.
  2. Set the chosen levels, settle, and check.
  3. For each press: on, settle, check; off, settle, check.
- **A check** returns −1 if any danger that was off at baseline is on, or if the circuit didn't settle. Otherwise it counts the door pistons that differ from baseline. The attempt scores its highest check.
- **Strictly better replaces the best**, so the earliest (fewest actions, lever-only before presses, pass 1 before pass 2) wins ties.
- **Stopping:** at the end of a cost level whose best equals `controllableGoals()`, on budget exhaustion or thread interrupt (`OUT_OF_TIME` unless something was found), or when the search space runs out.

- [ ] **Step 1: Write the failing test**

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LockSolverTest {
    private static final long BIG = 10_000_000L;

    private static int piston(LockCircuit.Builder b, int from) {
        int p = b.node(LockCircuit.MAX, 0);
        b.link(from, p, 0);
        b.goal(p);
        return p;
    }

    private static int not(LockCircuit.Builder b, int from, int initial) {
        int t = b.node(LockCircuit.NOT, initial);
        b.link(from, t, 0);
        return t;
    }

    /** door += NOT(MAX(NOT x, NOT y)), i.e. x AND y. */
    private static void and(LockCircuit.Builder b, int x, int y, int door) {
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, x, 15), or, 0);
        b.link(not(b, y, 15), or, 0);
        b.link(not(b, or, 0), door, 0);
    }

    @Test
    void buttonDoor() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int button = b.input(LockCircuit.PULSE, false);
        int dust = b.node(LockCircuit.MAX, 0);
        b.link(button, dust, 0);
        piston(b, dust);
        piston(b, dust);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[0], s.set());
        assertArrayEquals(new int[] {0}, s.presses());
        assertEquals(2, s.moved());
    }

    /** Opens only with A on, B off, C on: piston = NOT(MAX(NOT A, B, NOT C)). */
    private static LockCircuit threeLeverCombo() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.LEVER, false);
        int bb = b.input(LockCircuit.LEVER, false);
        int c = b.input(LockCircuit.LEVER, false);
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, a, 15), or, 0);
        b.link(bb, or, 0);
        b.link(not(b, c, 15), or, 0);
        piston(b, not(b, or, 0));
        return b.build();
    }

    @Test
    void leverCombination() {
        LockSolver.Solution s = LockSolver.solve(threeLeverCombo(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0, 2}, s.set());
        assertArrayEquals(new int[] {15, 15}, s.setTo());
        assertArrayEquals(new int[0], s.presses());
    }

    @Test
    void neverFiresATrap() {
        // Lever 0 alone opens the door but also primes the TNT; levers 1 and 2 together open it safely.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int trapLever = b.input(LockCircuit.LEVER, false);
        int x = b.input(LockCircuit.LEVER, false);
        int y = b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(trapLever, door, 0);
        b.goal(door);
        int tnt = b.node(LockCircuit.MAX, 0);
        b.link(trapLever, tnt, 0);
        b.danger(tnt);
        and(b, x, y, door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {1, 2}, s.set());
    }

    @Test
    void prefersFewestActions() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int single = b.input(LockCircuit.LEVER, false);
        int x = b.input(LockCircuit.LEVER, false);
        int y = b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(single, door, 0);
        b.goal(door);
        and(b, x, y, door);
        assertArrayEquals(new int[] {0}, LockSolver.solve(b.build(), 4, 1, BIG).set());
    }

    /** Press A, then B: A sets a latch, B opens only while it is set. */
    private static LockCircuit buttonOrder() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.PULSE, false);
        int bb = b.input(LockCircuit.PULSE, false);
        int[] latch = LockSimulatorTest.norLatch(b, a);
        int door = b.node(LockCircuit.MAX, 0);
        b.goal(door);
        and(b, latch[0], bb, door);
        return b.build();
    }

    @Test
    void buttonOrderAtMaster() {
        LockSolver.Solution s = LockSolver.solve(buttonOrder(), 20, 4, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0, 1}, s.presses());
    }

    @Test
    void buttonOrderBeyondExpert() {
        assertEquals(LockSolver.Status.NO_SOLUTION, LockSolver.solve(buttonOrder(), 4, 1, BIG).status());
    }

    /** The door opens only when a frame reads exactly 7: (a >= 7) AND NOT (a >= 8). */
    @Test
    void itemKeyLevel() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int key = b.analog(0, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8});
        int ge7 = b.node(LockCircuit.COMPARE, 0);
        b.link(key, ge7, 0);
        b.side(b.constant(7), ge7, 0);
        int ge8 = b.node(LockCircuit.COMPARE, 0);
        b.link(key, ge8, 0);
        b.side(b.constant(8), ge8, 0);
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, ge7, 15), or, 0);
        b.link(ge8, or, 0);
        piston(b, not(b, or, 0));
        LockSolver.Solution s = LockSolver.solve(b.build(), 20, 4, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[] {7}, s.setTo());
    }

    @Test
    void forcesOnlyWhenNoSwitchWorks() {
        // A forced source opens the door; so does the lever. The lever must win.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int force = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(lever, door, 0);
        b.link(force, door, 0);
        b.goal(door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 20, 4, BIG);
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[0], s.presses());
    }

    @Test
    void forcesAKeyedComparator() {
        // A comparator reading an empty chest (0) feeds the door; forcing its output opens it.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int force = b.input(LockCircuit.FORCE, false);
        int comparator = b.node(LockCircuit.COMPARE, 0);
        b.link(b.constant(0), comparator, 0);
        int out = b.node(LockCircuit.MAX, 0);
        b.link(comparator, out, 0);
        b.link(force, out, 0);
        piston(b, out);
        LockSolver.Solution s = LockSolver.solve(b.build(), 20, 4, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0}, s.presses());
    }

    @Test
    void forcedSourcesDoNotCountTowardTheCap() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int door = b.node(LockCircuit.MAX, 0);
        for (int i = 0; i < 5; i++) {
            b.link(b.input(LockCircuit.FORCE, false), door, 0);
        }
        b.goal(door);
        assertEquals(LockSolver.Status.SOLVED, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void tooManyInputs() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int door = b.node(LockCircuit.MAX, 0);
        for (int i = 0; i < 5; i++) {
            b.link(b.input(LockCircuit.LEVER, false), door, 0);
        }
        b.goal(door);
        assertEquals(LockSolver.Status.TOO_MANY_INPUTS, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void clockIsUnstable() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int torch = b.node(LockCircuit.NOT, 0);
        b.link(torch, torch, 0);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(lever, door, 0);
        b.link(torch, door, 0);
        b.goal(door);
        assertEquals(LockSolver.Status.UNSTABLE, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void noDoor() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.input(LockCircuit.LEVER, false);
        assertEquals(LockSolver.Status.NO_DOOR, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void tinyBudgetRunsOut() {
        assertEquals(LockSolver.Status.OUT_OF_TIME, LockSolver.solve(threeLeverCombo(), 4, 1, 1).status());
    }
}
```

`forcedSourcesDoNotCountTowardTheCap` uses `maxPresses = 1`, and one forced press opens the door.

- [ ] **Step 2: Run it and see it fail.** Run `./gradlew test --tests "*LockSolverTest"`. Expected: compilation failure.

- [ ] **Step 3: Write `LockSolver`**

```java
package com.hogwartsmagic.spell.support.lock;

import java.util.Arrays;

/**
 * Finds the setting of a lock's levers and item keys, and the order of its presses, that moves the most
 * door pistons: fewest actions first, real switches before forced sources, never a setting that powers a
 * trap. Pure; runs on the solver thread.
 */
public final class LockSolver {
    public enum Status { SOLVED, NO_DOOR, TOO_MANY_INPUTS, UNSTABLE, NO_SOLUTION, OUT_OF_TIME }

    /** set[i] goes to level setTo[i]; presses are pressed (or forced) in order. Indexes are circuit inputs. */
    public record Solution(Status status, int[] set, int[] setTo, int[] presses, int moved, int movable, long work) {
        static Solution none(Status status, int movable, long work) {
            return new Solution(status, new int[0], new int[0], new int[0], 0, movable, work);
        }
    }

    private LockSolver() {
    }

    public static Solution solve(LockCircuit c, int maxInputs, int maxPresses, long budget) {
        if (c.goalCount() == 0) {
            return Solution.none(Status.NO_DOOR, 0, 0);
        }
        int movable = c.controllableGoals();
        int counted = 0;
        for (int i = 0; i < c.inputCount(); i++) {
            if (c.inputKind(i) != LockCircuit.FORCE) {
                counted++;
            }
        }
        if (counted > maxInputs) {
            return Solution.none(Status.TOO_MANY_INPUTS, movable, 0);
        }
        if (c.inputCount() == 0 || movable == 0) {
            return Solution.none(Status.NO_SOLUTION, movable, 0);
        }
        LockSimulator sim = new LockSimulator(c);
        if (!sim.reset()) {
            return Solution.none(Status.UNSTABLE, movable, sim.work());
        }
        Search search = new Search(c, sim, budget, movable);
        int[] settable = concat(ofKind(c, LockCircuit.LEVER), ofKind(c, LockCircuit.ANALOG));
        int[] pulses = ofKind(c, LockCircuit.PULSE);
        int[] forced = ofKind(c, LockCircuit.FORCE);
        search.run(settable, pulses, maxPresses);
        if (search.bestMoved < movable && !search.outOfTime && forced.length > 0) {
            search.run(settable, concat(pulses, forced), maxPresses);
        }
        return search.result();
    }

    private static final class Search {
        private final LockCircuit c;
        private final LockSimulator sim;
        private final long budget;
        private final int movable;
        private final int[] base;
        private final boolean[] goalBase;
        private final boolean[] dangerBase;
        int bestMoved;
        private int[] bestSet = new int[0];
        private int[] bestTo = new int[0];
        private int[] bestPresses = new int[0];
        boolean outOfTime;

        Search(LockCircuit c, LockSimulator sim, long budget, int movable) {
            this.c = c;
            this.sim = sim;
            this.budget = budget;
            this.movable = movable;
            base = sim.snapshot();
            goalBase = new boolean[c.goalCount()];
            for (int g = 0; g < goalBase.length; g++) {
                goalBase[g] = sim.goalPowered(g);
            }
            dangerBase = new boolean[c.dangerCount()];
            for (int d = 0; d < dangerBase.length; d++) {
                dangerBase[d] = sim.dangerPowered(d);
            }
        }

        void run(int[] settable, int[] pressable, int maxPresses) {
            int pressCap = pressable.length == 0 ? 0 : maxPresses;
            for (int cost = 1; cost <= settable.length + pressCap; cost++) {
                for (int k = Math.min(cost, settable.length); k >= Math.max(0, cost - pressCap); k--) {
                    int len = cost - k;
                    int[] pick = firstCombination(k);
                    do {
                        int[] set = select(settable, pick);
                        int[][] alts = new int[k][];
                        boolean possible = true;
                        for (int i = 0; i < k; i++) {
                            alts[i] = alternatives(set[i]);
                            possible &= alts[i].length > 0;
                        }
                        if (!possible) {
                            continue;
                        }
                        int[] choice = new int[k];
                        do {
                            int[] to = new int[k];
                            for (int i = 0; i < k; i++) {
                                to[i] = alts[i][choice[i]];
                            }
                            int[] seq = new int[len];
                            do {
                                if (sim.work() > budget || Thread.currentThread().isInterrupted()) {
                                    outOfTime = true;
                                    return;
                                }
                                int[] presses = select(pressable, seq);
                                int moved = attempt(set, to, presses);
                                if (moved > bestMoved) {
                                    bestMoved = moved;
                                    bestSet = set;
                                    bestTo = to;
                                    bestPresses = presses;
                                }
                            } while (nextSequence(seq, pressable.length));
                        } while (nextMixed(choice, alts));
                    } while (nextCombination(pick, settable.length));
                }
                if (bestMoved == movable) {
                    return;
                }
            }
        }

        Solution result() {
            if (bestMoved > 0) {
                return new Solution(Status.SOLVED, bestSet, bestTo, bestPresses, bestMoved, movable, sim.work());
            }
            return Solution.none(outOfTime ? Status.OUT_OF_TIME : Status.NO_SOLUTION, movable, sim.work());
        }

        /** Levels this input can move to from where it stands at baseline. */
        private int[] alternatives(int input) {
            int now = base[c.inputNode(input)];
            return Arrays.stream(c.inputDomain(input)).filter(v -> v != now).toArray();
        }

        /** Door pistons moved at the best moment of this attempt; -1 if it never settles or springs a trap. */
        private int attempt(int[] set, int[] to, int[] presses) {
            sim.restore(base);
            int best = 0;
            if (set.length > 0) {
                for (int i = 0; i < set.length; i++) {
                    sim.setLevel(set[i], to[i]);
                }
                best = check();
                if (best < 0) {
                    return -1;
                }
            }
            for (int input : presses) {
                for (int phase = 0; phase < 2; phase++) {
                    sim.setInput(input, phase == 0);
                    int moved = check();
                    if (moved < 0) {
                        return -1;
                    }
                    best = Math.max(best, moved);
                }
            }
            return best;
        }

        private int check() {
            if (!sim.settle()) {
                return -1;
            }
            for (int d = 0; d < dangerBase.length; d++) {
                if (!dangerBase[d] && sim.dangerPowered(d)) {
                    return -1;
                }
            }
            int moved = 0;
            for (int g = 0; g < goalBase.length; g++) {
                if (sim.goalPowered(g) != goalBase[g]) {
                    moved++;
                }
            }
            return moved;
        }
    }

    private static int[] ofKind(LockCircuit c, int kind) {
        int[] out = new int[c.inputCount()];
        int count = 0;
        for (int i = 0; i < c.inputCount(); i++) {
            if (c.inputKind(i) == kind) {
                out[count++] = i;
            }
        }
        return Arrays.copyOf(out, count);
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int[] select(int[] from, int[] indexes) {
        int[] out = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            out[i] = from[indexes[i]];
        }
        return out;
    }

    private static int[] firstCombination(int k) {
        int[] pick = new int[k];
        for (int i = 0; i < k; i++) {
            pick[i] = i;
        }
        return pick;
    }

    /** Next k-subset of 0..n-1 in lexicographic order; false after the last. */
    private static boolean nextCombination(int[] pick, int n) {
        int k = pick.length;
        for (int i = k - 1; i >= 0; i--) {
            if (pick[i] < n - k + i) {
                pick[i]++;
                for (int j = i + 1; j < k; j++) {
                    pick[j] = pick[j - 1] + 1;
                }
                return true;
            }
        }
        return false;
    }

    /** Next sequence over 0..base-1 (an odometer); false after the last. */
    private static boolean nextSequence(int[] seq, int base) {
        for (int i = seq.length - 1; i >= 0; i--) {
            if (++seq[i] < base) {
                return true;
            }
            seq[i] = 0;
        }
        return false;
    }

    /** Odometer where position i runs over alts[i]; false after the last. */
    private static boolean nextMixed(int[] choice, int[][] alts) {
        for (int i = choice.length - 1; i >= 0; i--) {
            if (++choice[i] < alts[i].length) {
                return true;
            }
            choice[i] = 0;
        }
        return false;
    }
}
```

Note on `continue` inside the `do { } while (nextCombination(...))` loop: in Java, `continue` in a do-while jumps to the condition, so the next combination is still taken. That is intended.

- [ ] **Step 4: Run the tests.** Run `./gradlew test --tests "*LockSolverTest" --tests "*LockSimulatorTest"`. Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverTest.java
git commit -m "Search lock settings, item keys and press orders, real switches before forced sources" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: CircuitTracer (world → circuit)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java`

**Interfaces produced:**
- `record Mode(boolean forceSources, boolean itemKeys)`.
- `@Nullable static TracedLock trace(ServerLevel level, BlockPos hit, int blockCap, Mode mode)`, which returns null when there is no piston near `hit`.
- `record TracedLock(LockCircuit circuit, List<InputSite> inputs, int pistons, int blocks)`. `inputs.get(i)` is circuit input `i`.
- `record InputSite(BlockPos pos, Block block, boolean on, int kind, int level, int source, int slots, @Nullable UUID frame)`, with the source constants `InputSite.BLOCK`, `InputSite.CONTAINER`, `InputSite.FRAME` and `InputSite.OTHER`.
- `static boolean isOn(BlockState)`.

**Invariant:** every `b.input(...)` or `b.analog(...)` call is *immediately* followed by `sites.add(...)`, so site `i` is input `i`.

**Vanilla facts** (verified against the 1.21 mapped sources):

Diodes and observers:
- `DiodeBlock` (repeaters and comparators) reads its input at `pos.relative(FACING)` and outputs to `pos.relative(FACING.getOpposite())`. `getSignal` is non-zero only when the queried direction equals `FACING`.
- `ObserverBlock` watches `pos.relative(FACING)` and outputs to `pos.relative(FACING.getOpposite())`, with the same direction rule.

Torches:
- A torch strongly powers only the block above it.
- It weakly powers every neighbour except its attachment.
- Its input is its attachment: below, or `FACING.getOpposite()` for `RedstoneWallTorchBlock`, which extends `RedstoneTorchBlock`.

Levers, buttons and pressure plates:
- Levers and buttons are attached by `FACE`: `FLOOR` below, `CEILING` above, `WALL` at `FACING.getOpposite()`.
- They strongly power that block and weakly power all neighbours.
- A plate strongly powers the block below it.

Pistons and other receivers:
- A piston reads every neighbour except its front, and every neighbour of `pos.above()` except `DOWN`.
- A dispenser or dropper reads `pos` and `pos.above()`.
- TNT reads `pos`.

Dust:
- Dust reads the strong signal of conductor neighbours. From other dust it takes one less: horizontally, up a step when the block above it isn't a conductor, and down a step when the side block isn't a conductor.
- Dust powers the block below it, and horizontal neighbours whose `RedStoneWireBlock.PROPERTY_BY_DIRECTION` side isn't `NONE`.
- A diode's main input reads dust power whichever way the dust points.

Comparators:
- A comparator's main input becomes the analog value when the block behind it has one. When the block behind is a conductor, it is instead the analog value of an item frame on the block two behind (an `ItemFrame` in that block space whose `getDirection() == FACING`), or else that block's own analog value.
- `ItemFrame.getAnalogOutput()` is `empty ? 0 : rotation % 8 + 1`.
- A container's signal is `Mth.lerpDiscrete(fullness, 0, 15)`.
- `ChestBlock.getContainer(ChestBlock, BlockState, Level, BlockPos, boolean)` is public static and returns the combined double chest.
- A comparator's side reads dust, redstone blocks and signal sources facing in. A repeater's side reads only diodes facing in.

**Build rules:**

1. **Door pistons, region and their relevance tests.**
   - Door pistons: every `PistonBaseBlock` in the cube of radius `DOOR_RADIUS` around `hit`, loaded only, nearest first, at most `MAX_DOOR_PISTONS`. If there are none, return null.
   - Region: run a breadth-first search from the door pistons. A position joins if it is loaded and is a component, a sink, or a conductor touching a component.
   - Expansion: all 6 neighbours; for dust, also up/down of each horizontal neighbour; for sinks, also `pos.above()` and its neighbours; for comparators, also the block two behind.
   - An unloaded position, or reaching the cap, marks the trace truncated; the cap also stops the search.
2. **Nodes.** These are per block type, with the world value as the initial value. `core(pos)` is the node that receives inputs; `out(pos)` is the node others read.

   | Region block | core | out |
   |---|---|---|
   | conductor | `strong = node(MAX, getDirectSignalTo)`, `weak = node(MAX, getBestNeighborSignal)`, `strong → weak` | (uses `strong` / `weak`) |
   | dust | `node(MAX, POWER)` | same |
   | torch | `node(NOT, LIT ? 15 : 0)` | same |
   | repeater | `node(REPEATER, POWERED ? 15 : 0)` | same |
   | comparator | `node(SUBTRACT or COMPARE, getOutputSignal())` | `forceWrap` |
   | observer | `node(OBSERVER, POWERED ? 15 : 0)` | `forceWrap` |
   | lever | `input(LEVER)` + site | same |
   | button, plate, sculk sensor | `input(PULSE)` + site | same |
   | redstone block | `constant(15)` | same |
   | other signal source | `constant(strongest getSignal)` | `forceWrap` |
   | sink | `node(MAX, hasNeighborSignal ? 15 : 0)`, with `goal` / `danger` | same |

   `forceWrap(pos, node, initial)`: when `mode.forceSources()` is set and fewer than `MAX_FORCED_SOURCES` wraps exist, create `f = input(FORCE, false)` with its site and `w = node(MAX, initial)`, and link `node → w` and `f → w`. Otherwise return `node` unchanged.
3. **Comparator item keys.** These come from `itemKey(back, in)`:
   - **with `mode.itemKeys()`** and fewer than `MAX_ITEM_KEYS` keys so far: an `analog` input. A frame gets domain {0..8}, source `FRAME` and the frame's UUID. A container gets domain 0..15, source `CONTAINER` and its slot count. Anything else gets domain 0..15 and source `OTHER`.
   - **otherwise**: `constant(current)` and `markKeyed()`.
4. **Edges.** The wiring follows the same table as the vanilla facts. Every receiver reads into its `core` node; every source is read through its `out` node.

- [ ] **Step 1: Write `CircuitTracer`**

```java
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
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
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
        return !isComponent(s) && !isSink(s) && s.isRedstoneConductor(level, pos);
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
            if (level.isLoaded(n) && isComponent(level.getBlockState(n))) {
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
                int node = b.node(LockCircuit.MAX, level.hasNeighborSignal(pos) ? 15 : 0);
                put(pos, node, node);
                if (block instanceof PistonBaseBlock && doorPistons.contains(pos)) {
                    b.goal(node);
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
                int st = b.node(LockCircuit.MAX, level.getDirectSignalTo(pos));
                int wk = b.node(LockCircuit.MAX, level.getBestNeighborSignal(pos));
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
            } else if (block instanceof RedStoneWireBlock) {
                wireDust(pos);
            } else if (block instanceof RedstoneTorchBlock) {
                Direction attach = torchAttach(s);
                feedWeak(pos.relative(attach), attach.getOpposite(), core.get(pos));
            } else if (block instanceof DiodeBlock) {
                wireDiode(pos, s);
            } else if (block instanceof ObserverBlock) {
                BlockPos watchedPos = pos.relative(s.getValue(ObserverBlock.FACING));
                Integer watched = out.containsKey(watchedPos) ? out.get(watchedPos) : weak.get(watchedPos);
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
            if (!roofed) {
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
                src = direct(n, ns, toward);
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
        if (!frames.isEmpty()) {
            ItemFrame frame = frames.get(0);
            int now = frame.getAnalogOutput();
            if (!takeItemKey()) {
                return b.constant(now);
            }
            int node = b.analog(now, FRAME_LEVELS);
            sites.add(new InputSite(further, Blocks.AIR, now > 0, LockCircuit.ANALOG, now, InputSite.FRAME, 0, frame.getUUID()));
            return node;
        }
        BlockState fs = level.getBlockState(further);
        return fs.hasAnalogOutputSignal() ? analogBlock(further, fs) : -1;
    }

    private int analogBlock(BlockPos pos, BlockState state) {
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
```

The frame's `InputSite.block` is `Blocks.AIR` (a frame is an entity). Task 6's shifted-lock check skips frames and containers, and Task 7 finds the frame by UUID.

- [ ] **Step 2: Compile.** Run `tasklist | grep -ci javaw` (expect `0`), then `./gradlew compileJava`. Expected: BUILD SUCCESSFUL. If a vanilla name doesn't resolve, look it up in the mapped sources jar under `~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.21-51.0.33_mapped_official_1.21/`, use the real name, and say so in the report.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java
git commit -m "Trace the redstone around a piston door into a lock circuit, with forceable sources and item keys" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: ForcedSignals and the signal mixin

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/ForcedSignals.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/BlockStateSignalMixin.java`
- Modify: `src/main/resources/hogwartsmagic.mixins.json`

**Interfaces produced:**
- `ForcedSignals.force(ServerLevel level, BlockPos pos, int ticks)`.
- `ForcedSignals.signal(ServerLevel level, BlockPos pos, BlockState state, Direction direction, boolean strong)`, which returns 15 or 0.
- `ForcedSignals.ACTIVE`, a `volatile boolean`.

**Semantics:**
- A forced comparator or observer returns 15 from `getSignal` and `getDirectSignal` only when the queried direction equals its `FACING` (the vanilla convention: it then powers `pos.relative(FACING.getOpposite())`).
- Any other forced block returns 15 from `getSignal` in every direction, and 0 from `getDirectSignal` (it doesn't strongly power).
- Forcing and expiry both call `level.updateNeighborsAt(pos, block)`. For a comparator or observer they also update around the block it strongly powers, so receivers recompute.
- An entry expires at the end of a level tick. If its chunk is unloaded by then, it is dropped silently.
- Reads happen only on the server thread.

- [ ] **Step 1: Write `ForcedSignals`**

```java
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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

    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> FORCED = new HashMap<>();

    private ForcedSignals() {
    }

    public static void force(ServerLevel level, BlockPos pos, int ticks) {
        FORCED.computeIfAbsent(level.dimension(), k -> new HashMap<>()).put(pos.immutable(), level.getGameTime() + ticks);
        ACTIVE = true;
        notifyAround(level, pos);
    }

    public static int signal(ServerLevel level, BlockPos pos, BlockState state, Direction direction, boolean strong) {
        if (!level.getServer().isSameThread()) {
            return 0;
        }
        Map<BlockPos, Long> map = FORCED.get(level.dimension());
        if (map == null || !map.containsKey(pos)) {
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
        Map<BlockPos, Long> map = FORCED.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            if (now < e.getValue()) {
                continue;
            }
            it.remove();
            if (level.isLoaded(e.getKey())) {
                notifyAround(level, e.getKey());
            }
        }
        ACTIVE = FORCED.values().stream().anyMatch(m -> !m.isEmpty());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FORCED.clear();
        ACTIVE = false;
    }
}
```

- [ ] **Step 2: Write the mixin**

```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.support.lock.ForcedSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A source an Alohomora Master forced on reads as 15 for its few seconds (see ForcedSignals). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSignalMixin {
    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$forcedSignal(BlockGetter getter, BlockPos pos, Direction direction,
                                            CallbackInfoReturnable<Integer> cir) {
        if (ForcedSignals.ACTIVE && getter instanceof ServerLevel level) {
            int forced = ForcedSignals.signal(level, pos, (BlockState) (Object) this, direction, false);
            if (forced > 0) {
                cir.setReturnValue(forced);
            }
        }
    }

    @Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$forcedDirectSignal(BlockGetter getter, BlockPos pos, Direction direction,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (ForcedSignals.ACTIVE && getter instanceof ServerLevel level) {
            int forced = ForcedSignals.signal(level, pos, (BlockState) (Object) this, direction, true);
            if (forced > 0) {
                cir.setReturnValue(forced);
            }
        }
    }
}
```

- [ ] **Step 3: Register it.** In `src/main/resources/hogwartsmagic.mixins.json`, find `    "LivingEntityLastHurtAccessor"` (the last entry in `"mixins"`) and replace it with:

```json
    "LivingEntityLastHurtAccessor",
    "BlockStateSignalMixin"
```

- [ ] **Step 4: Build and boot.** Run `tasklist | grep -ci javaw` (expect `0`), then `./gradlew compileJava test`. Then run `./gradlew runServer` to `Done (`, type `stop`, and confirm the log has no `Mixin` apply error naming `BlockStateSignalMixin`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/ForcedSignals.java src/main/java/com/hogwartsmagic/mixin/BlockStateSignalMixin.java src/main/resources/hogwartsmagic.mixins.json
git commit -m "Let a forced comparator, observer or source read as full power for a few seconds" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: PistonLockSpell and the Alohomora hook

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`
- Create (stub, replaced in Task 7): `src/main/java/com/hogwartsmagic/spell/support/lock/LockHints.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java`

**Interfaces produced:** `public enum Use { OPEN, REVEAL, INSPECT }` and `public static boolean begin(ServerLevel level, BlockPos hit, ServerPlayer caster, int rank, Use use)`. `begin` returns false when there is no piston near `hit`.

**Tracer mode per use:**
- `OPEN` and `INSPECT`: `new Mode(LockRules.forcesSources(rank), false)`.
- `REVEAL`: `new Mode(false, LockRules.readsItemKeys(rank))`.

- [ ] **Step 1: Write the `LockHints` stub** (so this task compiles alone)

```java
package com.hogwartsmagic.spell.support.lock;

import net.minecraft.server.level.ServerLevel;

/** Revelio's hints. Filled in by the next task. */
public final class LockHints {
    private LockHints() {
    }

    public static void show(ServerLevel level, CircuitTracer.TracedLock lock, LockSolver.Solution solution) {
    }
}
```

- [ ] **Step 2: Write `PistonLockSpell`**

```java
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
        int maxInputs = LockRules.maxInputs(rank);
        int maxPresses = LockRules.maxPresses(rank);
        long budget = LockRules.budget(rank);
        Future<LockSolver.Solution> result = SOLVER.submit(() -> LockSolver.solve(lock.circuit(), maxInputs, maxPresses, budget));
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
        } else if (status == LockSolver.Status.NO_SOLUTION) {
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
```

- [ ] **Step 3: Hook Alohomora.** In `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java`, find:

```java
        boolean opened = openOne(level, pos, state, owner, rank);
        int radius = AlohomoraRules.areaRadius(rank);
        if (radius > 0) {
            opened |= openAround(level, pos, radius, owner, rank) > 0;
        }
        return opened;
```

and replace it with:

```java
        boolean opened = openOne(level, pos, state, owner, rank);
        int radius = AlohomoraRules.areaRadius(rank);
        if (radius > 0) {
            opened |= openAround(level, pos, radius, owner, rank) > 0;
        }
        if (!opened && owner instanceof ServerPlayer player
                && com.hogwartsmagic.spell.support.lock.LockRules.readsLocks(rank)) {
            // A piston door: its lock is read and worked over the next seconds; XP is paid when it gives way.
            com.hogwartsmagic.spell.support.lock.PistonLockSpell.begin(level, pos, player, rank,
                    com.hogwartsmagic.spell.support.lock.PistonLockSpell.Use.OPEN);
        }
        return opened;
```

In the class Javadoc, replace `Expert: every openable door within 4 blocks.` with `Expert: every openable door within 4 blocks, and a piston door with a small lock (see PistonLockSpell).`. Replace `and a hostile Ward's barrier, off for` with `any piston door lock (forcing a comparator, observer or sensor if it must), and a hostile Ward's barrier, off for`.

- [ ] **Step 4: Compile and test.** Run `tasklist | grep -ci javaw` (expect `0`), then `./gradlew compileJava test`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java src/main/java/com/hogwartsmagic/spell/support/lock/LockHints.java src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java
git commit -m "Let Expert and Master Alohomora work a piston door lock on its real levers, buttons and sources" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Revelio hints

**Files:**
- Replace: `src/main/java/com/hogwartsmagic/spell/support/lock/LockHints.java` (the stub)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`

**What the player sees** (for `HINT_TICKS`, 15 s):
- **Levers, buttons, plates and sculk sensors:** a glowing cyan copy of the block (a `minecraft:block_display` with `Glowing`), visible through walls as an outline. Above it, a see-through label (a `minecraft:text_display`, `billboard: center`, `see_through: true`).
- **Item frames:** the frame itself glows (`setGlowingTag(true)`, undone at expiry and at server stop), with a label over it.
- **Containers and other analog blocks:** a glowing copy and a label.

**Labels** (translation keys, Task 8 adds the text):

| Input | Setting | Key and arguments |
|---|---|---|
| lever | to 15 | `hint.lever_on` |
| lever | to 0 | `hint.lever_off` |
| button | presses at orders n, m… | `hint.press` with the list joined as `1, 3` |
| pressure plate | presses | `hint.step` with the list |
| sculk sensor | presses | `hint.sound` with the list |
| frame | to 0 | `hint.frame_empty` |
| frame | to v > 0 | `hint.frame` with `v − 1` (turns after placing an item) |
| container | to 0 | `hint.container_empty` |
| container | to v > 0 | `hint.container` with `min`, `max` from `LockRules.itemsForSignal(v, slots)` |
| other analog | to v | `hint.signal` with `v` |

All keys are prefixed `message.hogwartsmagic.lock.`.

- [ ] **Step 1: Write `LockHints`**

```java
package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Revelio's answer to a piston door: glowing outlines and floating labels on what to flip, press, turn or
 * fill, for 15 seconds. They are vanilla display entities, so no client code; any that survive a restart
 * are refused when they load. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockHints {
    public static final String TAG = "hogwartsmagic_lock_hint";
    private static final int GLOW = 0x55FFFF;

    private record Shown(ResourceKey<Level> dimension, UUID entity, long until, boolean frame) {
    }

    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final Set<UUID> LIVE = new HashSet<>();

    private LockHints() {
    }

    public static void show(ServerLevel level, CircuitTracer.TracedLock lock, LockSolver.Solution s) {
        long until = level.getGameTime() + LockRules.HINT_TICKS;
        List<CircuitTracer.InputSite> sites = lock.inputs();
        for (int i = 0; i < s.set().length; i++) {
            CircuitTracer.InputSite site = sites.get(s.set()[i]);
            mark(level, site, settingLabel(site, s.setTo()[i]), until);
        }
        Map<Integer, List<Integer>> orders = new LinkedHashMap<>();
        for (int i = 0; i < s.presses().length; i++) {
            orders.computeIfAbsent(s.presses()[i], k -> new ArrayList<>()).add(i + 1);
        }
        for (Map.Entry<Integer, List<Integer>> e : orders.entrySet()) {
            CircuitTracer.InputSite site = sites.get(e.getKey());
            String list = e.getValue().stream().map(String::valueOf).collect(Collectors.joining(", "));
            String key = site.block() instanceof BasePressurePlateBlock ? "step"
                    : site.block() instanceof SculkSensorBlock ? "sound" : "press";
            mark(level, site, Component.translatable("message.hogwartsmagic.lock.hint." + key, list), until);
        }
    }

    private static Component settingLabel(CircuitTracer.InputSite site, int to) {
        String p = "message.hogwartsmagic.lock.hint.";
        if (site.kind() == LockCircuit.LEVER) {
            return Component.translatable(p + (to > 0 ? "lever_on" : "lever_off"));
        }
        if (site.source() == CircuitTracer.InputSite.FRAME) {
            return to == 0 ? Component.translatable(p + "frame_empty") : Component.translatable(p + "frame", to - 1);
        }
        if (site.source() == CircuitTracer.InputSite.CONTAINER) {
            if (to == 0) {
                return Component.translatable(p + "container_empty");
            }
            int[] items = LockRules.itemsForSignal(to, site.slots());
            return Component.translatable(p + "container", items[0], items[1]);
        }
        return Component.translatable(p + "signal", to);
    }

    private static void mark(ServerLevel level, CircuitTracer.InputSite site, Component label, long until) {
        Vec3 labelAt;
        if (site.frame() != null && level.getEntity(site.frame()) instanceof ItemFrame frame) {
            frame.setGlowingTag(true);
            SHOWN.add(new Shown(level.dimension(), frame.getUUID(), until, true));
            labelAt = frame.position().add(0, 0.5, 0);
        } else {
            BlockPos pos = site.pos();
            if (!level.isLoaded(pos)) {
                return;
            }
            CompoundTag outline = new CompoundTag();
            outline.putString("id", "minecraft:block_display");
            outline.put("block_state", NbtUtils.writeBlockState(level.getBlockState(pos)));
            outline.putBoolean("Glowing", true);
            outline.putInt("glow_color_override", GLOW);
            spawn(level, outline, Vec3.atLowerCornerOf(pos), until);
            labelAt = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        }
        CompoundTag text = new CompoundTag();
        text.putString("id", "minecraft:text_display");
        text.putString("text", Component.Serializer.toJson(label, level.registryAccess()));
        text.putString("billboard", "center");
        text.putBoolean("see_through", true);
        text.putInt("background", 0x90000000);
        spawn(level, text, labelAt, until);
        level.sendParticles(ParticleTypes.GLOW, labelAt.x, labelAt.y - 0.3, labelAt.z, 10, 0.25, 0.25, 0.25, 0.01);
    }

    private static void spawn(ServerLevel level, CompoundTag tag, Vec3 at, long until) {
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
            return e;
        });
        if (entity == null) {
            return;
        }
        LIVE.add(entity.getUUID());
        if (level.addFreshEntity(entity)) {
            SHOWN.add(new Shown(level.dimension(), entity.getUUID(), until, false));
        } else {
            LIVE.remove(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SHOWN.isEmpty() || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Shown> it = SHOWN.iterator();
        while (it.hasNext()) {
            Shown shown = it.next();
            if (shown.dimension() != level.dimension() || now < shown.until()) {
                continue;
            }
            it.remove();
            clear(level, shown);
        }
    }

    private static void clear(ServerLevel level, Shown shown) {
        Entity entity = level.getEntity(shown.entity());
        if (shown.frame()) {
            if (entity != null) {
                entity.setGlowingTag(false);
            }
        } else {
            LIVE.remove(shown.entity());
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /** A hint saved with the world before a restart is never let back in. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide() && entity.getTags().contains(TAG) && !LIVE.contains(entity.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Shown shown : SHOWN) {
            ServerLevel level = event.getServer().getLevel(shown.dimension());
            if (level != null) {
                clear(level, shown);
            }
        }
        SHOWN.clear();
        LIVE.clear();
    }
}
```

If a frame has gone (`level.getEntity` returns null), `mark` takes the block branch. It outlines whatever is in the frame's block space (usually air, so nothing shows) and still places the label there. `labelAt` is assigned on every path that reaches the label.

- [ ] **Step 2: Hook Revelio.** In `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`, find:

```java
                    if (this.level() instanceof ServerLevel sl) {
                        revealNearbySecrets(sl, pos,
                                com.hogwartsmagic.spell.disable.DisableSupport.casterRank(getOwner(), SpellType.REVELIO));
                        com.hogwartsmagic.spell.support.RevelioSpell.revealHidden(sl, this.position(), getOwner());
                    }
```

and replace it with:

```java
                    if (this.level() instanceof ServerLevel sl) {
                        int revelioRank = com.hogwartsmagic.spell.disable.DisableSupport.casterRank(getOwner(), SpellType.REVELIO);
                        revealNearbySecrets(sl, pos, revelioRank);
                        com.hogwartsmagic.spell.support.RevelioSpell.revealHidden(sl, this.position(), getOwner());
                        if (getOwner() instanceof ServerPlayer revealer
                                && com.hogwartsmagic.spell.support.lock.LockRules.readsLocks(revelioRank)) {
                            // From Expert: show how the piston door here opens (see LockHints).
                            com.hogwartsmagic.spell.support.lock.PistonLockSpell.begin(sl, pos, revealer, revelioRank,
                                    com.hogwartsmagic.spell.support.lock.PistonLockSpell.Use.REVEAL);
                        }
                    }
```

If `ServerPlayer` isn't imported in `SpellBeamEntity`, use the full name `net.minecraft.server.level.ServerPlayer`.

- [ ] **Step 3: Compile and test.** Run `tasklist | grep -ci javaw` (expect `0`), then `./gradlew compileJava test`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockHints.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
git commit -m "Let Revelio show how a piston door opens: glowing switches, numbered presses, frame turns and chest fills" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 8: `/lockinfo` and all text

**Files:**
- Create: `src/main/java/com/hogwartsmagic/command/LockInfoCommand.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

- [ ] **Step 1: Write the command**

```java
package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.support.lock.PistonLockSpell;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** /lockinfo [3|4]: trace and solve the piston door you look at, as Alohomora of that rank. Changes nothing. */
public final class LockInfoCommand {
    private LockInfoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lockinfo")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> run(ctx.getSource(), 4))
                .then(Commands.argument("rank", IntegerArgumentType.integer(3, 4))
                        .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rank")))));
    }

    private static int run(CommandSourceStack source, int rank) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(20.0, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("Look at a block of a piston door within 20 blocks."));
            return 0;
        }
        if (!PistonLockSpell.begin(player.serverLevel(), blockHit.getBlockPos(), player, rank, PistonLockSpell.Use.INSPECT)) {
            source.sendFailure(Component.literal("No piston within 6 blocks of that block."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Reading the lock..."), false);
        return 1;
    }
}
```

- [ ] **Step 2: Register it.** In `ExampleMod.java`, find `        com.hogwartsmagic.command.ImperioCommand.register(event.getDispatcher());` and add directly after it:

```java
        com.hogwartsmagic.command.LockInfoCommand.register(event.getDispatcher());
```

- [ ] **Step 3: Text.** In `en_us.json`, replace these five lines in place (each keeps its trailing comma).

The line starting `  "spell.hogwartsmagic.alohomora.desc":` becomes:

```json
  "spell.hogwartsmagic.alohomora.desc": "Unlocks doors, trapdoors and fence gates. Adept opens iron doors too (they swing shut after 5 seconds); Expert opens every door within 4 blocks and works out a small piston door lock (up to 4 levers or buttons). At Master it cracks any piston door lock, combinations and button orders included, forcing a comparator, observer or sensor to fire if no switch will do; it also opens a Gringotts vault door from afar (the goblins still notice) and switches off a hostile Ward's barrier for 5 seconds.",
```

The line starting `  "spell.hogwartsmagic.alohomora.rank.3":` becomes:

```json
  "spell.hogwartsmagic.alohomora.rank.3": "Opens up to 12 doors, gates and trapdoors within 4 blocks of the target at once, and works out the levers or button of a small piston door lock (up to 4 switches).",
```

The line starting `  "spell.hogwartsmagic.alohomora.rank.4":` becomes:

```json
  "spell.hogwartsmagic.alohomora.rank.4": "Cracks full piston door locks (up to 20 switches, button orders of up to 4 presses, and forcing a comparator, observer or sensor for 3s when no switch will do), opens Gringotts vault doors and suppresses hostile Cave Inimicum wards for 5s.",
```

The line starting `  "spell.hogwartsmagic.revelio.desc":` becomes:

```json
  "spell.hogwartsmagic.revelio.desc": "Reveals invisible mobs, highlights nearby ores and chests through walls (farther with each rank), and briefly breaks Cave Inimicum wards. From Expert it shows how a piston door opens: its levers and buttons glow, with the presses numbered. At Master it also reads item locks (which frame to fill and how far to turn it, how full a chest must be) and unmasks every Polyjuice disguise and Invisibility Cloak within 20 blocks for 5 seconds.",
```

The line starting `  "spell.hogwartsmagic.revelio.rank.3":` becomes:

```json
  "spell.hogwartsmagic.revelio.rank.3": "Reveals ore within 16 blocks and chests within 12 blocks, and shows how to open a piston door with a small lock (up to 4 switches).",
```

The line starting `  "spell.hogwartsmagic.revelio.rank.4":` becomes:

```json
  "spell.hogwartsmagic.revelio.rank.4": "Looks 20 blocks for ore, 16 for chests, shows how to open any piston door lock (item frames and chest fills included), and unmasks Polyjuice disguises and Cloaks.",
```

That is six lines in total, counting the two Revelio rank lines.

Then find `  "message.hogwartsmagic.alohomora.ward": "The Ward's barrier falters.",` and insert directly after it:

```json
  "message.hogwartsmagic.lock.reading": "You feel along the lock's wiring...",
  "message.hogwartsmagic.lock.busy": "You are still working the last lock.",
  "message.hogwartsmagic.lock.too_big": "This lock has %s switches; you can work %s.",
  "message.hogwartsmagic.lock.unstable": "The lock's wiring never stops ticking; there is nothing to read.",
  "message.hogwartsmagic.lock.keyed": "This lock answers to an item, not a switch.",
  "message.hogwartsmagic.lock.too_far": "The lock's wiring runs further than you can follow.",
  "message.hogwartsmagic.lock.no_solution": "No setting of this lock's switches moves the door.",
  "message.hogwartsmagic.lock.out_of_time": "The lock is too intricate to read in time.",
  "message.hogwartsmagic.lock.shifted": "Someone touched the lock while you read it.",
  "message.hogwartsmagic.lock.opened": "The lock gives way.",
  "message.hogwartsmagic.lock.revealed": "The door's secret glows for a moment.",
  "message.hogwartsmagic.lock.hint.lever_on": "Flip on",
  "message.hogwartsmagic.lock.hint.lever_off": "Flip off",
  "message.hogwartsmagic.lock.hint.press": "Press: %s",
  "message.hogwartsmagic.lock.hint.step": "Step on: %s",
  "message.hogwartsmagic.lock.hint.sound": "Make a sound: %s",
  "message.hogwartsmagic.lock.hint.frame": "Put any item in, turn it %s times",
  "message.hogwartsmagic.lock.hint.frame_empty": "Leave this frame empty",
  "message.hogwartsmagic.lock.hint.container": "Fill with %s to %s items (stacks of 64)",
  "message.hogwartsmagic.lock.hint.container_empty": "Empty this",
  "message.hogwartsmagic.lock.hint.signal": "Needs signal %s here",
```

- [ ] **Step 4: Build, test, boot.** Run `tasklist | grep -ci javaw` (expect `0`), then `./gradlew compileJava test`. Expected: all green, including `RankChangeTextTest`. Then run `./gradlew runServer` to `Done (`, type `stop`, and confirm no exception mentions `hogwartsmagic`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hogwartsmagic/command/LockInfoCommand.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add /lockinfo and the piston lock text for Alohomora and Revelio" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 9: In-game verification (with the user)

This can't be automated. The controller builds the jar and copies it to `C:\Users\alonl\curseforge\minecraft\Instances\hogwarts\mods\hogwartsmagic-1.0.0.jar` with no `javaw` running, then gives the user this checklist. Run `/lockinfo` before each cast; its output is what to report back when a door misbehaves. Set ranks with `/spellmastery` or Tomes.

| # | Build | Spell, rank | Expected |
|---|---|---|---|
| 1 | 2×1 sticky piston door, one button | Alohomora Adept | Nothing |
| 2 | same | Alohomora Expert | "You feel along...", the button clicks by itself, the door opens and closes again, "The lock gives way.", XP |
| 3 | 2×2 flush door on one lever | Alohomora Expert | The lever flips and the door stays open; casting again closes it |
| 4 | 3-lever AND lock (A on, B off, C on) | Alohomora Expert | A and C flip; B is untouched |
| 5 | 6-lever lock | Alohomora Expert / Master | Expert: "This lock has 6 switches; you can work 4." Master: opens |
| 6 | button-order lock (A sets an RS latch, B opens while set) | Alohomora Expert / Master | Expert: "No setting...". Master: A, then B 2s later, and it opens |
| 7 | a wrong lever also powers a dispenser of arrows or TNT | Alohomora Master | Opens without firing it |
| 8 | door with a torch clock in its wiring | Alohomora Master | "...never stops ticking..." |
| 9 | door opened by a comparator reading a chest | Alohomora Master | The comparator is forced: a chime, the door opens for 3s, then closes |
| 10 | door opened by an observer watching a note block | Alohomora Master | The observer is forced; the door opens |
| 11 | door with no switch at all, only a daylight sensor | Alohomora Master | The sensor is forced; the door opens for 3s |
| 12 | same as 9 | Alohomora Expert | "This lock answers to an item, not a switch." |
| 13 | door from row 2 | Revelio Expert | The button glows cyan through the wall, labelled "Press: 1"; gone after 15s |
| 14 | lock from row 6 | Revelio Master | A labelled "Press: 1" and B "Press: 2" |
| 15 | lock from row 4 | Revelio Expert | A and C glow "Flip on" |
| 16 | comparator reading an item frame (needs 4 turns) | Revelio Master | The frame glows: "Put any item in, turn it 4 times" (put the item in, then click it 4 more times) |
| 17 | comparator reading a chest, needs signal 3 | Revelio Master | The chest glows: "Fill with 247 to 370 items (stacks of 64)" for a single chest; following it opens the door |
| 18 | quit with hints showing, restart | — | No leftover labels or outlines; no frame still glowing |
| 19 | two players cast at once; one recasts while busy | Alohomora Master | Both work; the recast says "You are still working the last lock." |
| 20 | dedicated server plus a client join | — | No registry mismatch; forcing works on the server (the mixin is common) |

Failures go back to an implementer with the `/lockinfo` output and the build described. Known gaps (pistons used as logic, exact timing, frames needing a specific item) mean a rare "no setting moves the door" on an exotic build is expected. A crash, a wrong lever flipped, or a fired trap is not.

---

## Self-review notes

- **Requirement coverage.**
  - 1 (Expert small, Master full): Tasks 1 and 6.
  - 2 (simulate everything, do the one that opens): Tasks 2 and 3.
  - 3 (combination locks at Master): Task 3 tests plus rows 4–6.
  - 4 (no protection): Task 6 has no `mayUse` or claim check.
  - 5 (big cap): Task 1.
  - 6 (force comparator, sculk, observer): Tasks 4, 5 and 6, plus rows 9–11; sculk sensors are activated for real.
  - 7 (Revelio highlights, frame positions, chest contents): Tasks 4 (item keys) and 7, plus rows 13–17.
- **Type consistency:** `Solution(status, set, setTo, presses, moved, movable, work)` is the same in Tasks 3, 6 and 7. `InputSite(pos, block, on, kind, level, source, slots, frame)` is the same in Tasks 4, 6 and 7. `Use` is the same in Tasks 6, 7 and 8.
- **Placeholder scan:** none left. `ForcedSignals.signal` uses `facing(state)` directly.
