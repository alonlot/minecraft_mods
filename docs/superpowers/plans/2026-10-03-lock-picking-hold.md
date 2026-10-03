# Hold-to-Pick for Piston Locks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan (see the ledger's cloud rulings).

**Goal:** Reading a piston door's lock becomes something the caster *does*. After Alohomora or Revelio strikes a block near a piston door, the caster keeps holding the cast key. While it is held:
- the wand twists in first person, as if picking a lock;
- quiet lock clicks play at the caster, with an occasional faint piston sound at the door, and both come faster as the search narrows;
- sparks fly at the door, so other players can see it;
- the action bar shows a progress bar and an estimated time to solve (`~?s` for the first half second).

Letting go stops the search at once. The caster is told "You stop picking the lock.", gets no XP, and nothing in the world changes. When the solver finds the answer, a success click plays and the hold no longer matters: Alohomora plays the answer back and Revelio shows its hints, as they do today. `/lockinfo` stays instant and does not need a hold.

Task 0 first closes the small leftovers from the forced-subsets review (commit 8adfe2a).

**Architecture:**
- **Hold signal (no new C2S packet).** The client reuses the existing `CastSpellPacket` channel heartbeat, the same way Crucio does. After an Alohomora or Revelio cast, `ClientModEvents` sends a heartbeat on every tick the hold key is down. The hold key is right-click, or the slot key if the cast came from a slot key. When the key comes up, the client sends `releaseChannel`. On the server, `CastSpellPacket.castAs` passes heartbeats for these two spells to `PistonLockSpell.heartbeat`, and never treats them as a cast. It passes releases to `PistonLockSpell.release`.
- **Hold state (server thread).** Each OPEN or REVEAL job gets a `Hold`: which spell it needs, when the last heartbeat came, and when the worker started. Every level tick, `tickHolds` checks each hold. It drops the job the moment any of these is true:
  - the heartbeat is more than 6 ticks old;
  - the caster has another spell selected or no wand in hand;
  - the caster is stunned or silenced, controlled or possessing;
  - the caster is dead, offline or in another dimension.
  Dropping a job cancels its `Future` with interrupt, so the solver stops. A held solve may run 30 s (600 ticks) from the moment the worker starts it. The work budget is unchanged.
- **Progress (solver to server).** `LockSolver.Progress` holds three volatile fields (`started`, `attempts`, `work`). Only the solver thread writes them; the server thread reads them. Before submitting, the server thread computes `LockSolver.plannedAttempts`, the worst-case number of attempts for the inputs, presses and forced subsets. The pure maths lives in `LockPickRules`: the attempt count, the ETA, the bar fill, and how often the clicks and piston sounds play. It is unit-tested. The worker still touches only `LockCircuit` and `LockSolver`, and `Progress` is nested in `LockSolver`.
- **Feedback.**
  - The action bar is sent by the server with `displayClientMessage(..., true)`.
  - Sounds are vanilla events played by the server with `level.playSound`. No new assets.
  - Sparks are server particles (`ELECTRIC_SPARK`) at the struck block.
  - The first-person animation is client-only. One new S2C packet, `LockPickStatePacket(boolean picking)`, goes only to the caster. Its handler reaches the client through `ClientSideBridge.handlePacket("lockPickState")`, then `ClientRuntimeBridge`, then `LockPickClient`. `LockPickClient` cancels the main-hand `RenderHandEvent` and draws the wand itself, twisted.
  - The client's own cooldown and "ready" action bar line is suppressed while picking, so it does not overwrite the progress bar.

**Why one S2C packet is needed (the controller asked for proof):** the client cannot know by itself that a lock read started. The beam may miss, hit a block with no door, or be refused (busy, piston logic, too many switches). Two client-only things depend on knowing it: the wand animation, and suppressing `ModEventsClientHooks.onClientPlayerTick`'s per-tick cooldown action bar. That line is redrawn every tick while Alohomora is on cooldown, which it always is right after the cast, so it would hide the progress bar. No existing S2C packet carries a per-player "picking" flag. `PatronusShieldStatePacket` has Patronus-specific meaning and goes to trackers. The hold itself needs no new C2S packet.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5.

**Spec:** No spec file. The binding inputs are the user's request and the controller's rulings, both copied below. The ledger is `.superpowers/sdd/2026-10-03-alohomora-piston-locks/progress.md`, entry "User instruction (2026-10-03): hold-to-pick".

## Requirements (from the user)

1. After casting Alohomora or Revelio at a block near a piston door, the caster must keep holding right-click.
2. While it is held: a lock-picking animation, lock-picking sounds and piston sounds, and an estimated time to solve.
3. Releasing stops the simulation.
4. Finding the solution ends the hold, and the spell proceeds.

## Controller rulings (binding)

- Reuse the existing channel heartbeat, as Crucio does. Add a packet only if it is shown to be needed (see Architecture: one S2C packet, no C2S packet).
- Release cancels the solver at once (interrupt), with the line "You stop picking the lock.", no XP and no world change.
- A first-person wand picking animation, through a client hook reached via `ClientSideBridge`. No client classes in common code, and no `switch` on an enum in client code.
- Spark particles at the door for other players (server particles).
- An action-bar progress bar with an ETA computed from the remaining attempts and the measured throughput. It shows `~?s` for the first half second.
- Quiet vanilla lock-click sounds with random pitch, and occasional faint piston sounds at the door, both more frequent as the search narrows. Vanilla sound events only, no new assets.
- On success, a success click. Then Alohomora plays the answer back, or Revelio shows its hints, and releasing no longer matters.
- Holding extends the solve cap from 10 s to 30 s. The work budget is unchanged.
- `/lockinfo` stays instant. The feature covers both Alohomora and Revelio.
- The solver exposes its progress to the server thread safely. The worker still touches only `LockCircuit` and `LockSolver`. The search size (attempts per pass, given the inputs, presses and forced subsets) is estimated up front for the ETA.
- Task 0, from the coordinator, closes the forced-subsets review leftovers M1 to M4 of 8adfe2a.

## Rulings made while planning (record them in the ledger)

- Ruling: **one cast per press.** While right-click stays down after an Alohomora or Revelio cast, the client does not cast again. This is the same use-key latch Crucio has. Why: otherwise vanilla's 4-tick use-repeat would recast and hit "You are still working the last lock." Cost: holding right-click no longer repeats these two spells.
- Ruling: **a slot-key cast is held by its slot key.** Key repeats of that key do not recast while it holds a pick.
- Ruling: **the cast press counts as held.** The cast packet itself marks the caster as holding (`noteHoldStart`), and heartbeats refresh it. A release marks the caster as released. When the beam lands, the job takes the caster's last mark. So a caster who let go before the beam arrived is stopped on the first tick with the stop line. A point-blank hit in the same tick as the cast is still treated as held.
- Ruling: **heartbeat timeout 6 ticks** (`LockPickRules.HOLD_TIMEOUT_TICKS`, as `CrucioRules.HEARTBEAT_TIMEOUT`). A client lag spike longer than 0.3 s stops the pick.
- Ruling: **mana is charged once, at the cast, as today.** There is no upkeep and no refund on release.
- Ruling: **XP.** Alohomora still pays once, when the last action lands, so a released pick pays nothing. Revelio's XP comes from the beam hit, as before (the existing ruling "Revelio XP unchanged"). A released Revelio therefore keeps the XP of its ore and chest reveal, but it shows no lock hints.
- Ruling: **one solver thread (unchanged).** A second caster's job waits in the queue. It shows an empty bar with `~?s`, and its 30 s cap starts only when the worker picks it up. While queued it never times out, but its caster can let go.
- Ruling: **the struck block stands for the door.** Sparks and piston sounds play there (it lies within 6 blocks of the door pistons; `TracedLock` keeps no piston positions).
- Ruling: **the "reading" line is no longer sent for OPEN and REVEAL.** The progress bar replaces it. The lang key stays.
- Ruling: **finish order.** In one tick, `tickHolds` runs before `finishJobs`. A job whose hold lapsed is dropped even if its result came in that same tick. A job whose result arrives before the first heartbeat succeeds as long as the press mark is fresh.
- Ruling: **Task 0 M2.** A forced on-phase settles for at most `FORCE_TICKS / 2 = 30` simulator ticks. Simulator ticks are redstone ticks, two game ticks each, so 30 of them are the 60 game ticks the world holds a forced source. If it is still ticking then, the attempt is not scored as unstable:
  - traps are checked as usual;
  - a door piston counts as moved only if it kept the same value over the last simulated tick of the hold;
  - then the release is checked as usual.
- Ruling: **Task 0 M3 test shape.** A trap that is off at baseline and off while the subset is held cannot turn on after the release with gates alone. With the lever constant, the trap is a function of the AND output, and that output is 0 both at baseline and after the release. So the test uses a trap that is already powered at baseline (NOT of the AND). It goes off while the subset is held and rises again on release, and the existing rose-during-settle check must reject it.
- Ruling: **the network protocol version stays 5** (it was never bumped when earlier phases added packets).

## Global Constraints

- Server authoritative. `spell/`, `network/`, `event/ModEvents`, `util/` and `entity/` never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/` (and the already client-only `event/ClientModEvents`). `ModEvents` reaches the client only through `util/ClientSideBridge` → `client/ClientRuntimeBridge`.
- No `switch` on an enum in client code. `ClientRuntimeBridge.handlePacket` is a String switch, which is fine. Use if-chains and `==` on enums.
- The solver thread touches only `LockCircuit`, `LockSimulator` (inside `LockSolver`) and `LockSolver.Progress`. Every other new field is read and written on the server thread.
- Never load a chunk: particles and sounds at the door go through `level.isLoaded(door)` or only play sounds (`playSound` does not load).
- Cloud build rules (`.superpowers/sdd/CLOUD-OVERRIDES.md`) replace the standing Windows rules:
  - Before any gradle run, check `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If another build is running, wait (poll every 30 s, at most 20 min).
  - Build and test: `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew compileJava test --console=plain`.
  - Server boot: the CLOUD-OVERRIDES block.
- Commit per task:
  - Stage only the named files. Never `.claude/settings.local.json` or `.superpowers/`.
  - `git commit -m "<subject>" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`.
  - No apostrophes in heredoc bodies. Never push (the controller pushes).
- `en_us.json` stays valid JSON. Inserted lines end with a comma and go after a line that already ends with one. Validate with `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`.
- Line numbers drift. Every edit names the exact text to find (quoted from HEAD 8adfe2a).
- The existing lock tests stay unchanged and green (284 tests at 8adfe2a).

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java` | M1 sensor-aware start checks; hold stubs (T3); the whole hold (T4) | 0, 3, 4 |
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java` | M2 forced on-phase, M4 early return; `Progress`, `plannedAttempts`, solve overload | 0, 2 |
| Modify `src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverForceSetTest.java` | M2 and M3 tests | 0 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockPickRules.java` | Pure: hold timings, attempt count, ETA, bar, sound pacing | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/support/lock/LockPickRulesTest.java` | | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverProgressTest.java` | planned == counted, progress flags | 2 |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `LockPickStatePacket` (S2C) and its registration; heartbeat, release and press hooks in `castAs` | 3 |
| Modify `src/main/java/com/hogwartsmagic/util/ClientSideBridge.java` | `startLockHold(Player)` | 3 |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | `lockPickState` action, `startLockHold` | 3 |
| Create `src/main/java/com/hogwartsmagic/client/LockPickClient.java` | Client picking flag, first-person wand animation | 3 |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Lock hold: heartbeats or release per tick; slot-key holds | 3 |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Use-key latch, `ClientSideBridge.startLockHold` | 3 |
| Modify `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java` | No cooldown action bar while picking | 3 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | `lock.picking`, `lock.stopped`, two description phrases | 4 |

## Pre-flight table (the controller writes it to the ledger)

| Pair / task | Shared thing | Check |
|---|---|---|
| T0 → T2 | `LockSolver.Search` after T0: `check()`, `checkForcedOn()`, `score()`, `trapped()`; `runHeld` early return | T2's edits anchor on T0's text (quoted in T2) |
| T1 → T2, T4 | `LockPickRules.attempts(int[], int, int, int)`, `fraction(...)`, `etaSeconds(...)`, `clickInterval(double)`, `pistonChance(double)`, `filledCells(double)`, `holdLapsed(long, long)`, constants `HOLD_TIMEOUT_TICKS`, `HELD_SOLVE_TIMEOUT_TICKS`, `ETA_UNKNOWN_TICKS`, `BAR_INTERVAL_TICKS`, `SPARK_INTERVAL_TICKS`, `BAR_CELLS`, `UNKNOWN` | names identical |
| T2 → T4 | `LockSolver.Progress` (`started()`, `attempts()`, `work()`), `solve(c, maxInputs, maxPresses, budget, Progress)`, `plannedAttempts(c, maxInputs, maxPresses)` | identical |
| T3 → T4 | `SpellPackets.LockPickStatePacket(boolean picking)`; `PistonLockSpell.heartbeat(ServerPlayer)`, `release(ServerPlayer)`, `noteHoldStart(ServerPlayer)` (T3 adds empty stubs, T4 replaces them) | stub text quoted in T4 |
| T3 | `ClientSideBridge.startLockHold(Player)` → `ClientRuntimeBridge.startLockHold(Player)` → `ClientModEvents.startLockHold(KeyMapping)`; `ModEvents.clearLockUseLatch()`; `LockPickClient.isPicking()`, `accept(LockPickStatePacket)` | identical |

---

### Task 0: Forced-subsets leftovers (M1 to M4 of 8adfe2a)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverForceSetTest.java`

- [ ] **Step 1: Write the failing tests (M2, M3).** In `LockSolverForceSetTest.java`, add these two tests before the final closing `}` of the class. That `}` is the one after `theBudgetBoundsTheSubsetPass`.

```java
    @Test
    void aForcedSourceThatStartsAClockStillOpensTheDoor() {
        // While x is forced on, a torch loop ticks forever; the world lets go after 3 s, so this is no unstable lock.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(x, door, 0);
        b.goal(door);
        int gate = b.node(LockCircuit.MAX, 15);
        b.link(not(b, x, 15), gate, 0);
        int torch = not(b, gate, 0);
        b.link(torch, gate, 0);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 2, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertEquals(1, s.moved());
        assertArrayEquals(new int[0], s.set());
        assertArrayEquals(new int[] {0}, s.presses());
    }

    @Test
    void aTrapThatFiresOnlyWhenTheSubsetIsReleasedIsRejected() {
        // The trap is NOT(x AND y): powered at baseline, off while both are held, and it rises again on release.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.FORCE, false);
        int y = b.input(LockCircuit.FORCE, false);
        int both = b.node(LockCircuit.MAX, 0);
        and(b, x, y, both);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(both, door, 0);
        b.goal(door);
        b.danger(not(b, both, 15));
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 2, BIG);
        assertEquals(LockSolver.Status.NO_SOLUTION, s.status());
    }
```

- [ ] **Step 2: Run them.** Command: `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"` (wait if a build is running), then `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew test --tests "*LockSolverForceSetTest" --console=plain`. Expected: `aForcedSourceThatStartsAClockStillOpensTheDoor` FAILS, because today the unsettled on-phase gives NO_SOLUTION. `aTrapThatFiresOnlyWhenTheSubsetIsReleasedIsRejected` PASSES; it is a regression guard for the release check. Record both in the report.

- [ ] **Step 3: M2. Forced on-phases settle for the world's hold.** In `LockSolver.java`, inside `attemptHeld`, find:

```java
            for (int input : subset) {
                sim.setInput(input, true);
            }
            int best = check();
```

Replace with:

```java
            for (int input : subset) {
                sim.setInput(input, true);
            }
            int best = checkForcedOn();
```

In `attempt`, find:

```java
                    sim.setInput(input, phase == 0);
                    int moved = check();
```

Replace with:

```java
                    sim.setInput(input, phase == 0);
                    int moved = phase == 0 && c.inputKind(input) == LockCircuit.FORCE ? checkForcedOn() : check();
```

Replace the whole `check()` method:

```java
        private int check() {
            if (!sim.settle(LockSimulator.MAX_TICKS, limit)) {
                if (sim.stoppedForBudget()) {
                    halted = true; // out of budget or interrupted: not the same as an oscillating lock
                }
                return -1;
            }
            if (sim.dangerRoseInLastSettle()) {
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
```

with:

```java
        private int check() {
            if (!sim.settle(LockSimulator.MAX_TICKS, limit)) {
                if (sim.stoppedForBudget()) {
                    halted = true; // out of budget or interrupted: not the same as an oscillating lock
                }
                return -1;
            }
            return score();
        }

        /**
         * The on-phase of a forced source. The world holds it for LockRules.FORCE_TICKS game ticks, which is
         * FORCE_TICKS / 2 simulator (redstone) ticks. A circuit still ticking when the hold ends is not unstable:
         * the world lets go then, and the release is checked next. Traps are checked as in check(). A door
         * piston counts as moved only if it kept its value over the last simulated tick of the hold.
         */
        private int checkForcedOn() {
            int hold = LockRules.FORCE_TICKS / 2;
            if (sim.settle(hold - 1, limit)) {
                return score();
            }
            if (sim.stoppedForBudget()) {
                halted = true;
                return -1;
            }
            if (trapped()) {
                return -1;
            }
            boolean[] before = new boolean[goalBase.length];
            for (int g = 0; g < before.length; g++) {
                before[g] = sim.goalPowered(g);
            }
            if (sim.settle(1, limit)) {
                return score();
            }
            if (sim.stoppedForBudget()) {
                halted = true;
                return -1;
            }
            if (trapped()) {
                return -1;
            }
            int moved = 0;
            for (int g = 0; g < goalBase.length; g++) {
                boolean now = sim.goalPowered(g);
                if (now == before[g] && now != goalBase[g]) {
                    moved++;
                }
            }
            return moved;
        }

        /** After a settle: -1 if a trap fired, otherwise the door pistons moved from baseline. */
        private int score() {
            if (trapped()) {
                return -1;
            }
            int moved = 0;
            for (int g = 0; g < goalBase.length; g++) {
                if (sim.goalPowered(g) != goalBase[g]) {
                    moved++;
                }
            }
            return moved;
        }

        /** A trap rose during the last settle, or is on now when it was off at baseline. */
        private boolean trapped() {
            if (sim.dangerRoseInLastSettle()) {
                return true;
            }
            for (int d = 0; d < dangerBase.length; d++) {
                if (!dangerBase[d] && sim.dangerPowered(d)) {
                    return true;
                }
            }
            return false;
        }
```

- [ ] **Step 4: M4. Stop the subset pass on a perfect answer.** In `runHeld`, find:

```java
                    if (moved > bestMoved) {
                        bestMoved = moved;
                        bestSet = subset;
                        bestTo = to;
                        bestPresses = new int[0];
                    }
                }
```

Replace with:

```java
                    if (moved > bestMoved) {
                        bestMoved = moved;
                        bestSet = subset;
                        bestTo = to;
                        bestPresses = new int[0];
                        if (bestMoved == movable) {
                            return; // nothing later can do better: a later subset only wins if strictly better
                        }
                    }
                }
```

The `if (bestMoved == movable) { return; }` after the size loop stays.

- [ ] **Step 5: M1. The start-of-playback checks treat a sculk sensor as `stepShifted` does.** In `PistonLockSpell.java`, inside `schedule`, find:

```java
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
```

Replace with:

```java
        for (int input : s.set()) {
            if (siteShifted(level, sites.get(input))) {
                tell(player, "shifted", ChatFormatting.GOLD);
                return;
            }
        }
        for (int input : s.presses()) {
            if (siteShifted(level, sites.get(input))) {
                tell(player, "shifted", ChatFormatting.GOLD);
                return;
            }
        }
```

Then replace the whole `stepShifted` method:

```java
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
```

with:

```java
    /** The inputs this step works are gone or not as traced (see {@link #siteShifted}). */
    private static boolean stepShifted(ServerLevel level, Playback play, Step step) {
        for (int input : step.inputs()) {
            if (siteShifted(level, play.inputs.get(input))) {
                return true;
            }
        }
        return false;
    }

    /**
     * One input is gone or not as traced. A sculk sensor only has to still be a sensor: it hears the playback
     * and the world around it, so its power and phase are never compared with the trace.
     */
    private static boolean siteShifted(ServerLevel level, CircuitTracer.InputSite site) {
        return site.block() instanceof SculkSensorBlock ? gone(level, site) : shifted(level, site, site.on());
    }
```

- [ ] **Step 6: Run the tests.** Run `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, 286 tests (284 + 2), all green, including every existing `LockSolver*Test`.

- [ ] **Step 7: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverForceSetTest.java
git commit -m "Forced holds last as long as in the world, sensors at playback start, early stop on a full answer" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 1: LockPickRules (pure maths, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/LockPickRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/LockPickRulesTest.java`

**Produces:** the constants and static methods listed in the pre-flight table.

How the attempt count is derived: it mirrors `LockSolver.solve`, counting one attempt per `attempt` or `attemptHeld` call.
- Let `settable` be the levers and item keys. Input `i` has `alternatives[i] = domain size - 1` levels to move to.
- Pass 1 runs every (k settable inputs, len presses) pair with `k + len >= 1` and `len <= pressCap`, where `pressCap = pulses == 0 ? 0 : maxPresses`. For each pair, every k-subset of settable inputs, every alternative level of each, and every press sequence over the pulses. That is `(sum over k of e_k) * (sum over len of pulses^len) - 1`, where `e_k` is the k-th elementary symmetric sum of the alternatives.
- Pass 2 (forced > 0) only counts sequences that press at least one forced source: `(sum of e_k) * (sum over len = 1..maxPresses of (pulses+forced)^len - pulses^len)`.
- Pass 3 (forced >= 2) counts the subsets of size 2 and up of the first `n = min(forced, 8)`: `2^n - 1 - n`.
- Everything saturates at `Long.MAX_VALUE`.
- It is an upper bound: the solver stops early on a full answer.

- [ ] **Step 1: Write the failing test**

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockPickRulesTest {
    @Test
    void constants() {
        assertEquals(6, LockPickRules.HOLD_TIMEOUT_TICKS);
        assertEquals(600, LockPickRules.HELD_SOLVE_TIMEOUT_TICKS);
        assertEquals(10, LockPickRules.ETA_UNKNOWN_TICKS);
        assertEquals(2, LockPickRules.BAR_INTERVAL_TICKS);
        assertEquals(4, LockPickRules.SPARK_INTERVAL_TICKS);
        assertEquals(20, LockPickRules.BAR_CELLS);
        assertEquals(-1, LockPickRules.UNKNOWN);
    }

    @Test
    void holdLapsesAfterSixQuietTicks() {
        assertFalse(LockPickRules.holdLapsed(10, 4));
        assertTrue(LockPickRules.holdLapsed(11, 4));
        assertTrue(LockPickRules.holdLapsed(1000, Long.MIN_VALUE / 2));
    }

    @Test
    void attemptsOfLeversAndAButton() {
        // 3 levers (1 alternative each), 1 button, 2 presses: (1+3+3+1) * (1+1+1) - 1
        assertEquals(23, LockPickRules.attempts(new int[] {1, 1, 1}, 1, 0, 2));
    }

    @Test
    void attemptsOfForcedSourcesOnly() {
        // pass 1: none; pass 2: 2 + 4 sequences that force something; pass 3: the one pair
        assertEquals(7, LockPickRules.attempts(new int[0], 0, 2, 2));
    }

    @Test
    void attemptsWithAnItemKey() {
        // alternatives 15 and 1: e = 1, 16, 15 (sum 32); 2 buttons, 1 press: 32 * 3 - 1
        assertEquals(95, LockPickRules.attempts(new int[] {15, 1}, 2, 0, 1));
    }

    @Test
    void attemptsWithOneLeverAndOneForcedSource() {
        // pass 1: 2 * 1 - 1 = 1; pass 2: e-sum 2 * (1 - 0) = 2; no pass 3
        assertEquals(3, LockPickRules.attempts(new int[] {1}, 0, 1, 1));
    }

    @Test
    void attemptsOfNothing() {
        assertEquals(0, LockPickRules.attempts(new int[0], 0, 0, 4));
    }

    @Test
    void attemptsSaturate() {
        int[] keys = new int[20];
        Arrays.fill(keys, 15);
        assertEquals(Long.MAX_VALUE, LockPickRules.attempts(keys, 20, 8, 4));
    }

    @Test
    void etaIsUnknownForTheFirstHalfSecondOrWithoutAMeasurement() {
        assertEquals(LockPickRules.UNKNOWN, LockPickRules.etaSeconds(5, 100, 50, 1000, 9, 600));
        assertEquals(LockPickRules.UNKNOWN, LockPickRules.etaSeconds(0, 100, 0, 1000, 40, 600));
    }

    @Test
    void etaFromRemainingAttempts() {
        // 10 of 110 in 20 ticks: 100 left at 0.5 per tick = 200 ticks = 10 s
        assertEquals(10, LockPickRules.etaSeconds(10, 110, 100, 1_000_000, 20, 600));
        // 20 of 120 in 41 ticks: 205 ticks = 10.25 s, shown as 11
        assertEquals(11, LockPickRules.etaSeconds(20, 120, 1, 1_000_000_000_000L, 41, 600));
    }

    @Test
    void etaFromTheWorkBudget() {
        // half the budget in 20 ticks: 20 more ticks = 1 s, though the attempt plan is huge
        assertEquals(1, LockPickRules.etaSeconds(10, 1_000_000_000_000L, 500_000, 1_000_000, 20, 600));
    }

    @Test
    void etaFromTheCap() {
        assertEquals(25, LockPickRules.etaSeconds(1, 1_000_000_000_000L, 1, 1_000_000_000_000L, 100, 600));
    }

    @Test
    void fractionIsTheFurthestOfThree() {
        assertEquals(0.5, LockPickRules.fraction(50, 100, 0, 1000, 0, 600), 1e-9);
        assertEquals(0.5, LockPickRules.fraction(0, 0, 0, 0, 300, 600), 1e-9);
        assertEquals(0.25, LockPickRules.fraction(1, 100, 250, 1000, 6, 600), 1e-9);
        assertEquals(1.0, LockPickRules.fraction(200, 100, 0, 1000, 0, 600), 1e-9);
    }

    @Test
    void barCells() {
        assertEquals(0, LockPickRules.filledCells(0.0));
        assertEquals(0, LockPickRules.filledCells(0.04));
        assertEquals(10, LockPickRules.filledCells(0.5));
        assertEquals(20, LockPickRules.filledCells(1.0));
        assertEquals(20, LockPickRules.filledCells(3.0));
    }

    @Test
    void soundsQuickenAsTheSearchNarrows() {
        assertEquals(8, LockPickRules.clickInterval(0.0));
        assertEquals(6, LockPickRules.clickInterval(0.5));
        assertEquals(3, LockPickRules.clickInterval(1.0));
        assertEquals(0.04, LockPickRules.pistonChance(0.0), 1e-9);
        assertEquals(0.24, LockPickRules.pistonChance(1.0), 1e-9);
        assertEquals(0.24, LockPickRules.pistonChance(2.0), 1e-9);
    }
}
```

Hand-checks:
- `fraction(1, 100, 250, 1000, 6, 600)`: the three ratios are 0.01, 0.25 and 0.01, and the largest is 0.25.
- `clickInterval(0.5)` is `round(8 - 2.5) = round(5.5) = 6` (Java's `Math.round` rounds half up).
- `etaSeconds(5, ...)` with `runTicks 9` is below 10, so UNKNOWN.
- In the second UNKNOWN case, nothing has been measured (0 attempts and 0 work).

- [ ] **Step 2: Run it and see it fail.** Run the gradle check, then `timeout 900 ./gradlew test --tests "*LockPickRulesTest" --console=plain`. Expected: compilation failure (no `LockPickRules`).

- [ ] **Step 3: Write `LockPickRules`**

```java
package com.hogwartsmagic.spell.support.lock;

/**
 * Hold-to-pick on piston locks: how long a hold may lapse, how long a held solve may run, how big the search
 * is, how far it has got, how long is left, and how often the picking sounds play. Pure numbers.
 */
public final class LockPickRules {
    /** Ticks without a heartbeat before the hold counts as let go (as Crucio). */
    public static final int HOLD_TIMEOUT_TICKS = 6;
    /** A held solve may run this long once the worker has started it (the unheld cap is 10 s). */
    public static final int HELD_SOLVE_TIMEOUT_TICKS = 600;
    /** The time left reads "~?s" until the worker has run this long. */
    public static final int ETA_UNKNOWN_TICKS = 10;
    public static final int BAR_INTERVAL_TICKS = 2;
    public static final int SPARK_INTERVAL_TICKS = 4;
    public static final int BAR_CELLS = 20;
    public static final int UNKNOWN = -1;

    private LockPickRules() {
    }

    public static boolean holdLapsed(long now, long lastSeen) {
        return now - lastSeen > HOLD_TIMEOUT_TICKS;
    }

    /**
     * Worst-case attempts of LockSolver.solve: alternatives[i] is how many other levels settable input i can
     * move to; pulses the buttons, plates and sensors; forced the forceable sources. Saturates at Long.MAX_VALUE.
     */
    public static long attempts(int[] alternatives, int pulses, int forced, int maxPresses) {
        int n = alternatives.length;
        long[] e = new long[n + 1];
        e[0] = 1;
        for (int i = 0; i < n; i++) {
            for (int k = i + 1; k >= 1; k--) {
                e[k] = add(e[k], mul(e[k - 1], Math.max(0, alternatives[i])));
            }
        }
        long settings = 0;
        for (long v : e) {
            settings = add(settings, v);
        }
        int pressCap = pulses == 0 ? 0 : maxPresses;
        long sequences = 0;
        for (int len = 0; len <= pressCap; len++) {
            sequences = add(sequences, pow(pulses, len));
        }
        long all = mul(settings, sequences);
        long total = all == Long.MAX_VALUE ? all : all - 1; // the empty attempt is never made
        if (forced <= 0) {
            return Math.max(0, total);
        }
        long forcing = 0;
        for (int len = 1; len <= maxPresses; len++) {
            forcing = add(forcing, pow(pulses + forced, len) - pow(pulses, len));
        }
        total = add(total, mul(settings, forcing));
        if (forced >= 2) {
            int held = Math.min(forced, 8);
            total = add(total, (1L << held) - 1 - held);
        }
        return total;
    }

    /** How far the search is towards its end, 0..1: the furthest of attempts, work and time. */
    public static double fraction(long done, long total, long work, long budget, long runTicks, long capTicks) {
        double f = 0.0;
        if (total > 0) {
            f = Math.max(f, done / (double) total);
        }
        if (budget > 0) {
            f = Math.max(f, work / (double) budget);
        }
        if (capTicks > 0) {
            f = Math.max(f, runTicks / (double) capTicks);
        }
        return Math.min(1.0, Math.max(0.0, f));
    }

    /**
     * Seconds left, rounded up, at the throughput measured so far: the soonest of the attempts running out,
     * the work budget running out and the cap. UNKNOWN in the first half second or before anything was measured.
     */
    public static int etaSeconds(long done, long total, long work, long budget, long runTicks, long capTicks) {
        if (runTicks < ETA_UNKNOWN_TICKS || (done <= 0 && work <= 0)) {
            return UNKNOWN;
        }
        double ticks = Math.max(0, capTicks - runTicks);
        if (done > 0 && total > 0) {
            ticks = Math.min(ticks, Math.max(0, total - done) * (double) runTicks / done);
        }
        if (work > 0 && budget > 0) {
            ticks = Math.min(ticks, Math.max(0, budget - work) * (double) runTicks / work);
        }
        return (int) Math.ceil(ticks / 20.0);
    }

    public static int filledCells(double fraction) {
        return (int) Math.floor(clamp(fraction) * BAR_CELLS);
    }

    /** Ticks between two lock clicks: 8 at the start, 3 at the end. */
    public static int clickInterval(double fraction) {
        return (int) Math.round(8 - 5 * clamp(fraction));
    }

    /** Chance that a click also brings a faint piston sound from the door. */
    public static double pistonChance(double fraction) {
        return 0.04 + 0.2 * clamp(fraction);
    }

    private static double clamp(double f) {
        return Math.min(1.0, Math.max(0.0, f));
    }

    private static long add(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    private static long mul(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static long pow(long base, int exp) {
        long r = 1;
        for (int i = 0; i < exp; i++) {
            r = mul(r, base);
        }
        return r;
    }
}
```

Saturation check: `e[20] = 15^20` overflows, so `settings` is MAX, `all` is MAX and `total` is MAX. Adding the pass-2 product keeps MAX. In `pow(pulses + forced, len) - pow(pulses, len)`, both terms are finite for realistic inputs. If the first one saturates, the difference is still near MAX and `add` keeps it non-negative.

- [ ] **Step 4: Run the test and see it pass.** Run `timeout 900 ./gradlew test --tests "*LockPickRulesTest" --console=plain`. Expected: PASS (15 tests).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockPickRules.java src/test/java/com/hogwartsmagic/spell/support/lock/LockPickRulesTest.java
git commit -m "Add the hold-to-pick numbers: search size, time left, bar and sound pacing" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Solver progress and the planned search size

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverProgressTest.java`

**Consumes:** `LockPickRules.attempts` (Task 1); the Task 0 text of `LockSolver`.
**Produces:** `LockSolver.Progress`, `solve(LockCircuit, int, int, long, Progress)`, `plannedAttempts(LockCircuit, int, int)`.

- [ ] **Step 1: Write the failing test**

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSolverProgressTest {
    private static final long BIG = 10_000_000L;

    /** Every input reaches the door and a trap alike, so every attempt is made and rejected. */
    private static LockCircuit allTrapped(int levers, int buttons, int forced) {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int door = b.node(LockCircuit.MAX, 0);
        int trap = b.node(LockCircuit.MAX, 0);
        for (int i = 0; i < levers; i++) {
            int in = b.input(LockCircuit.LEVER, false);
            b.link(in, door, 0);
            b.link(in, trap, 0);
        }
        for (int i = 0; i < buttons; i++) {
            int in = b.input(LockCircuit.PULSE, false);
            b.link(in, door, 0);
            b.link(in, trap, 0);
        }
        for (int i = 0; i < forced; i++) {
            int in = b.input(LockCircuit.FORCE, false);
            b.link(in, door, 0);
            b.link(in, trap, 0);
        }
        b.goal(door);
        b.danger(trap);
        return b.build();
    }

    @Test
    void theFullSearchMakesExactlyThePlannedAttempts() {
        LockCircuit c = allTrapped(3, 1, 0);
        LockSolver.Progress progress = new LockSolver.Progress();
        LockSolver.Solution s = LockSolver.solve(c, 4, 2, BIG, progress);
        assertEquals(LockSolver.Status.NO_SOLUTION, s.status());
        assertEquals(23, LockSolver.plannedAttempts(c, 4, 2));
        assertEquals(23, progress.attempts());
        assertTrue(progress.started());
        assertEquals(s.work(), progress.work());
    }

    @Test
    void theForcedPassesAreCountedToo() {
        LockCircuit c = allTrapped(0, 0, 2);
        LockSolver.Progress progress = new LockSolver.Progress();
        LockSolver.Solution s = LockSolver.solve(c, 4, 2, BIG, progress);
        assertEquals(LockSolver.Status.NO_SOLUTION, s.status());
        assertEquals(7, LockSolver.plannedAttempts(c, 4, 2));
        assertEquals(7, progress.attempts());
    }

    @Test
    void aSolvedSearchStopsWithinThePlan() {
        // Opens with lever A on: piston = A.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.LEVER, false);
        b.input(LockCircuit.LEVER, false);
        b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(a, door, 0);
        b.goal(door);
        LockCircuit c = b.build();
        LockSolver.Progress progress = new LockSolver.Progress();
        LockSolver.Solution s = LockSolver.solve(c, 4, 1, BIG, progress);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertEquals(7, LockSolver.plannedAttempts(c, 4, 1));
        assertTrue(progress.attempts() >= 1 && progress.attempts() <= 7, "attempts " + progress.attempts());
    }

    @Test
    void aLockTooBigToWorkPlansNothing() {
        LockCircuit c = allTrapped(5, 0, 0);
        assertEquals(0, LockSolver.plannedAttempts(c, 4, 1));
        LockSolver.Progress progress = new LockSolver.Progress();
        assertEquals(LockSolver.Status.TOO_MANY_INPUTS, LockSolver.solve(c, 4, 1, BIG, progress).status());
        assertEquals(0, progress.attempts());
    }

    @Test
    void aFreshProgressHasNotStarted() {
        LockSolver.Progress progress = new LockSolver.Progress();
        assertFalse(progress.started());
        assertEquals(0, progress.attempts());
        assertEquals(0, progress.work());
    }
}
```

Hand-check of `theFullSearchMakesExactlyThePlannedAttempts`:
- Pass 1 runs 7 lever settings × 3 sequences (no press, 1 press, 2 presses) plus the 2 press-only sequences: 7 × 3 + 2 = 23. That matches `(8 × 3) - 1`.
- Each settable attempt is rejected at its set phase because the trap rises, and each press-only attempt at its press. Each one is still counted.
- There are no forced sources, so there is no pass 2.

- [ ] **Step 2: Run it and see it fail.** Run `timeout 900 ./gradlew test --tests "*LockSolverProgressTest" --console=plain`. Expected: compilation failure (no `Progress`).

- [ ] **Step 3: Add `Progress` and `plannedAttempts`.** In `LockSolver.java`, find:

```java
    private LockSolver() {
    }

    public static Solution solve(LockCircuit c, int maxInputs, int maxPresses, long budget) {
        if (c.goalCount() == 0) {
```

Replace with:

```java
    /**
     * How far one solve has got, for the caster's progress bar. Only the solver thread writes it and the
     * server thread reads it: the fields are volatile and there is one writer, so no locks are needed.
     */
    public static final class Progress {
        private volatile boolean started;
        private volatile long attempts;
        private volatile long work;

        public boolean started() {
            return started;
        }

        public long attempts() {
            return attempts;
        }

        public long work() {
            return work;
        }

        void start() {
            started = true;
        }

        void attempted(long workSoFar) {
            attempts = attempts + 1; // one writer: the solver thread
            work = workSoFar;
        }
    }

    private LockSolver() {
    }

    /**
     * The most attempts {@link #solve} can make on this lock with these caps (see LockPickRules.attempts);
     * 0 when it would refuse the lock outright. Pure and cheap, for the server thread.
     */
    public static long plannedAttempts(LockCircuit c, int maxInputs, int maxPresses) {
        if (c.goalCount() == 0 || c.nonForcedInputCount() > maxInputs || c.inputCount() == 0
                || c.controllableGoals() == 0) {
            return 0;
        }
        int[] settable = concat(ofKind(c, LockCircuit.LEVER), ofKind(c, LockCircuit.ANALOG));
        int[] alternatives = new int[settable.length];
        for (int i = 0; i < settable.length; i++) {
            alternatives[i] = c.inputDomain(settable[i]).length - 1; // every level but the one it stands at
        }
        return LockPickRules.attempts(alternatives, ofKind(c, LockCircuit.PULSE).length,
                ofKind(c, LockCircuit.FORCE).length, maxPresses);
    }

    public static Solution solve(LockCircuit c, int maxInputs, int maxPresses, long budget) {
        return solve(c, maxInputs, maxPresses, budget, new Progress());
    }

    /** As {@link #solve(LockCircuit, int, int, long)}, reporting each attempt and the work so far to progress. */
    public static Solution solve(LockCircuit c, int maxInputs, int maxPresses, long budget, Progress progress) {
        progress.start();
        if (c.goalCount() == 0) {
```

(`alternatives` uses the domain size minus one. An input node's value at baseline is its own initial level, and the builder guarantees that level is in its domain. So this count equals `Search.alternatives(input).length`.)

- [ ] **Step 4: Pass progress into the search and count attempts.** Find:

```java
        Search search = new Search(c, sim, budget, movable);
```

Replace with:

```java
        Search search = new Search(c, sim, budget, movable, progress);
```

Find:

```java
        private final int movable;
        private final int[] base;
```

Replace with:

```java
        private final int movable;
        private final Progress progress;
        private final int[] base;
```

Find:

```java
        Search(LockCircuit c, LockSimulator sim, long budget, int movable) {
            this.c = c;
            this.sim = sim;
            this.budget = budget;
            this.movable = movable;
```

Replace with:

```java
        Search(LockCircuit c, LockSimulator sim, long budget, int movable, Progress progress) {
            this.c = c;
            this.sim = sim;
            this.budget = budget;
            this.movable = movable;
            this.progress = progress;
```

In `run`, find:

```java
                                int moved = attempt(set, to, presses);
```

Replace with:

```java
                                int moved = attempt(set, to, presses);
                                progress.attempted(sim.work());
```

In `runHeld`, find:

```java
                    int moved = attemptHeld(subset);
```

Replace with:

```java
                    int moved = attemptHeld(subset);
                    progress.attempted(sim.work());
```

- [ ] **Step 5: Run the tests.** Run `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, all green (Task 0's count + 15 + 5). The existing solver tests are unchanged: the 4-argument `solve` still exists.

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockSolver.java src/test/java/com/hogwartsmagic/spell/support/lock/LockSolverProgressTest.java
git commit -m "Let the lock solver report its progress and plan its search size" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The hold on the wire and on the client

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java` (stubs only)
- Modify: `src/main/java/com/hogwartsmagic/util/ClientSideBridge.java`
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`
- Create: `src/main/java/com/hogwartsmagic/client/LockPickClient.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java`

**Produces:** `SpellPackets.LockPickStatePacket(boolean picking)`; the `PistonLockSpell.heartbeat/release/noteHoldStart(ServerPlayer)` stubs; `ClientSideBridge.startLockHold(Player)`; `ClientModEvents.startLockHold(KeyMapping)`; `ModEvents.clearLockUseLatch()`; `LockPickClient.isPicking()` and `accept(...)`.

No unit tests: everything here is network and client code. It is verified by compiling, by a dedicated-server boot (a packet is registered), and in game in Task 6.

- [ ] **Step 1: Server stubs.** In `PistonLockSpell.java`, find:

```java
    private PistonLockSpell() {
    }
```

Replace with:

```java
    private PistonLockSpell() {
    }

    /** The caster still holds the cast (the cast packet heartbeat). Filled in by the hold task. */
    public static void heartbeat(ServerPlayer player) {
    }

    /** The caster let go of the cast. Filled in by the hold task. */
    public static void release(ServerPlayer player) {
    }

    /** An Alohomora or Revelio cast press: it counts as held until the first heartbeat. Filled in by the hold task. */
    public static void noteHoldStart(ServerPlayer player) {
    }
```

- [ ] **Step 2: The packet.** In `SpellPackets.java`, find:

```java
    /** Server → client: {@code casterId} raised or dropped the Patronus shield. */
    public record PatronusShieldStatePacket(int casterId, boolean active) {
        public static void handle(PatronusShieldStatePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("patronusShieldState", p));
        }
    }
```

Replace with:

```java
    /** Server → client: {@code casterId} raised or dropped the Patronus shield. */
    public record PatronusShieldStatePacket(int casterId, boolean active) {
        public static void handle(PatronusShieldStatePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("patronusShieldState", p));
        }
    }

    /** Server → the caster only: a piston lock pick started or ended (the first-person wand animation). */
    public record LockPickStatePacket(boolean picking) {
        public static void handle(LockPickStatePacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("lockPickState", p));
        }
    }
```

Register it last. Find:

```java
        CHANNEL.messageBuilder(PossessionCastPacket.class)
                .encoder((p, buf) -> buf.writeUtf(p.spellId, 64))
                .decoder(buf -> new PossessionCastPacket(buf.readUtf(64)))
                .consumerMainThread(PossessionCastPacket::handle)
                .add();
    }
```

Replace with:

```java
        CHANNEL.messageBuilder(PossessionCastPacket.class)
                .encoder((p, buf) -> buf.writeUtf(p.spellId, 64))
                .decoder(buf -> new PossessionCastPacket(buf.readUtf(64)))
                .consumerMainThread(PossessionCastPacket::handle)
                .add();
        CHANNEL.messageBuilder(LockPickStatePacket.class)
                .encoder((p, buf) -> buf.writeBoolean(p.picking))
                .decoder(buf -> new LockPickStatePacket(buf.readBoolean()))
                .consumerMainThread(LockPickStatePacket::handle)
                .add();
    }
```

- [ ] **Step 3: Server side of the heartbeat in `castAs`.** In the release block, find:

```java
            if (p.releaseChannel) {
                com.hogwartsmagic.spell.mind.CrucioChannel.release(player);
```

Replace with:

```java
            if (p.releaseChannel) {
                com.hogwartsmagic.spell.mind.CrucioChannel.release(player);
                com.hogwartsmagic.spell.support.lock.PistonLockSpell.release(player);
```

Then find:

```java
            // Crucio is held after its beam lands; the heartbeat keeps the channel alive (it never casts again).
            if (p.channelHeartbeat && spell == SpellType.CRUCIO) {
                com.hogwartsmagic.spell.mind.CrucioChannel.heartbeat(player);
                return;
            }
```

Replace with:

```java
            // Crucio is held after its beam lands; the heartbeat keeps the channel alive (it never casts again).
            if (p.channelHeartbeat && spell == SpellType.CRUCIO) {
                com.hogwartsmagic.spell.mind.CrucioChannel.heartbeat(player);
                return;
            }

            // Alohomora and Revelio are held while a piston lock is picked (PistonLockSpell); a heartbeat never casts.
            if (p.channelHeartbeat && (spell == SpellType.ALOHOMORA || spell == SpellType.REVELIO)) {
                com.hogwartsmagic.spell.support.lock.PistonLockSpell.heartbeat(player);
                return;
            }
            if (!p.selfCastProtego && (spell == SpellType.ALOHOMORA || spell == SpellType.REVELIO)) {
                com.hogwartsmagic.spell.support.lock.PistonLockSpell.noteHoldStart(player); // the press counts as held
            }
```

(These branches come after the stun and silence gate, so a stunned caster's heartbeats are dropped and the hold lapses. They come before the cooldown and mana code, so a heartbeat is never charged and never fires a beam. `noteHoldStart` runs even when the cooldown then refuses the cast, which is harmless: no job starts.)

- [ ] **Step 4: Bridge.** In `ClientSideBridge.java`, find:

```java
    public static void startPatronusHold(net.minecraft.world.entity.player.Player player) {
        invoke("startPatronusHold", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
    }
```

Replace with:

```java
    public static void startPatronusHold(net.minecraft.world.entity.player.Player player) {
        invoke("startPatronusHold", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
    }

    /** Alohomora or Revelio was cast with right-click: keep it held to pick a piston lock. */
    public static void startLockHold(net.minecraft.world.entity.player.Player player) {
        invoke("startLockHold", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
    }
```

In `ClientRuntimeBridge.java`, find:

```java
            case "puppetSnapshot" -> PossessionClient.acceptSnapshot((SpellPackets.PuppetSnapshotPacket) payload);
```

Replace with:

```java
            case "puppetSnapshot" -> PossessionClient.acceptSnapshot((SpellPackets.PuppetSnapshotPacket) payload);
            case "lockPickState" -> LockPickClient.accept((SpellPackets.LockPickStatePacket) payload);
```

Then find:

```java
    public static void startPatronusHold(net.minecraft.world.entity.player.Player player) {
        if (com.hogwartsmagic.event.ClientModEvents.startPatronusHold()) {
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
```

Replace with:

```java
    public static void startPatronusHold(net.minecraft.world.entity.player.Player player) {
        if (com.hogwartsmagic.event.ClientModEvents.startPatronusHold()) {
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    public static void startLockHold(net.minecraft.world.entity.player.Player player) {
        com.hogwartsmagic.event.ClientModEvents.startLockHold(net.minecraft.client.Minecraft.getInstance().options.keyUse);
    }
```

- [ ] **Step 5: `LockPickClient`.** Create `src/main/java/com/hogwartsmagic/client/LockPickClient.java`:

```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of hold-to-pick. While the server says the local player is picking a piston lock, the wand is
 * drawn tipped towards the middle of the screen, twisting as if working a lock. Visual only; the server owns
 * the pick and ends it (LockPickStatePacket false), and a logout clears it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class LockPickClient {
    private static boolean picking;

    private LockPickClient() {
    }

    public static void accept(SpellPackets.LockPickStatePacket packet) {
        picking = packet.picking();
    }

    /** Picking now, as far as this client can tell: the server said so and a wand is still in the main hand. */
    public static boolean isPicking() {
        LocalPlayer player = Minecraft.getInstance().player;
        return picking && player != null && MagicWandItem.isAnyWand(player.getMainHandItem());
    }

    /**
     * Draws the main-hand wand in its picking pose instead of the vanilla one. A wand is drawn without an arm
     * in first person, so cancelling and calling renderItem with the vanilla hand offset is the whole job.
     * A hand already hidden (cloak, possession) never reaches here: cancelled events are not delivered.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !isPicking()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!MagicWandItem.isAnyWand(stack)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        event.setCanceled(true);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        float t = player.tickCount + event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * 0.56F, -0.52F, -0.72F); // the vanilla first-person item position
        pose.translate(side * -0.16F, 0.10F + 0.012F * Mth.sin(t * 1.7F), -0.10F); // in towards the lock
        pose.mulPose(Axis.YP.rotationDegrees(side * 18.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-28.0F + 5.0F * Mth.sin(t * 0.8F))); // tip down at the keyhole
        pose.mulPose(Axis.ZP.rotationDegrees(side * 14.0F * Mth.sin(t * 2.2F))); // the picking twist
        minecraft.gameRenderer.itemInHandRenderer.renderItem(player, stack,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        picking = false;
    }
}
```

These APIs were checked against the 1.21 mapped jar:
- `ItemInHandRenderer.renderItem(LivingEntity, ItemStack, ItemDisplayContext, boolean, PoseStack, MultiBufferSource, int)` is public.
- `GameRenderer.itemInHandRenderer` is a public final field.
- `RenderHandEvent` has `getHand`, `getPoseStack`, `getMultiBufferSource`, `getPackedLight`, `getPartialTick` and `getItemStack`.
- `renderArmWithItem` is private, which is why the pose is rebuilt here.

- [ ] **Step 6: The client hold in `ClientModEvents`.** Find:

```java
    private static boolean crucioHoldPending = false;
    private static boolean crucioChannelActive = false;
```

Replace with:

```java
    private static boolean crucioHoldPending = false;
    private static boolean crucioChannelActive = false;
    /** Alohomora or Revelio was cast; while lockHoldKey stays down, heartbeats keep a piston lock pick going. */
    private static boolean lockHoldPending = false;
    @Nullable
    private static KeyMapping lockHoldKey = null;
```

Find the end of the Crucio tick block and the start of the Imperio one:

```java
                            crucioHoldPending = false;
                            crucioChannelActive = false;
                        }
                    }

                    if (imperioHoldPending && (!holdingWand || !imperioSelected)) {
```

Replace with:

```java
                            crucioHoldPending = false;
                            crucioChannelActive = false;
                        }
                    }

                    // Alohomora and Revelio are held after the cast: while a piston lock is picked the heartbeats
                    // keep it going and letting go stops it (PistonLockSpell). Held by right-click or the slot key.
                    boolean lockSelected = holdingWand
                            && (selected == com.hogwartsmagic.spell.SpellType.ALOHOMORA
                            || selected == com.hogwartsmagic.spell.SpellType.REVELIO);
                    if (lockHoldPending && (!lockSelected || lockHoldKey == null || !lockHoldKey.isDown())) {
                        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                        lockHoldPending = false;
                        lockHoldKey = null;
                    } else if (lockHoldPending) {
                        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                    }
                    if (!mc.options.keyUse.isDown()) {
                        com.hogwartsmagic.event.ModEvents.clearLockUseLatch();
                    }

                    if (imperioHoldPending && (!holdingWand || !imperioSelected)) {
```

In the `else` reset block, find:

```java
                    crucioHoldPending = false;
                    crucioChannelActive = false;
                    imperioHoldPending = false;
```

Replace with:

```java
                    crucioHoldPending = false;
                    crucioChannelActive = false;
                    lockHoldPending = false;
                    lockHoldKey = null;
                    imperioHoldPending = false;
```

Slot-key casts. Find:

```java
            while (SPELL_SLOT_KEYS[slot].consumeClick()) {
                SpellType spell = MagicWandItem.getSpellSlot(wand, slot);
                if (spell != null) {
                    com.hogwartsmagic.client.ModEventsClientHooks.selectSpellAndCast(mc.player, spell);
                }
            }
```

Replace with:

```java
            while (SPELL_SLOT_KEYS[slot].consumeClick()) {
                if (lockHoldPending && lockHoldKey == SPELL_SLOT_KEYS[slot]) {
                    continue; // a key repeat while this key holds a lock pick: never cast again
                }
                SpellType spell = MagicWandItem.getSpellSlot(wand, slot);
                if (spell != null) {
                    com.hogwartsmagic.client.ModEventsClientHooks.selectSpellAndCast(mc.player, spell);
                    if (spell == SpellType.ALOHOMORA || spell == SpellType.REVELIO) {
                        startLockHold(SPELL_SLOT_KEYS[slot]);
                    }
                }
            }
```

Add the starter. Find:

```java
    /** Doubles as the use-key latch: while the hold is pending, holding right-click never casts Crucio again. */
    public static boolean isCrucioHoldPending() {
        return crucioHoldPending;
    }
```

Replace with:

```java
    /** Doubles as the use-key latch: while the hold is pending, holding right-click never casts Crucio again. */
    public static boolean isCrucioHoldPending() {
        return crucioHoldPending;
    }

    /**
     * Alohomora or Revelio was cast: while key stays down the client sends heartbeats, and the server picks a
     * piston lock if the beam found one (otherwise the heartbeats are ignored). Letting go sends a release.
     */
    public static void startLockHold(KeyMapping key) {
        lockHoldPending = true;
        lockHoldKey = key;
    }
```

(`KeyMapping`, `Nullable`, `SpellType`, `SpellPackets` and `PacketDistributor` are already imported in `ClientModEvents`.)

- [ ] **Step 7: The right-click latch in `ModEvents`.** Find:

```java
    private static boolean imperioUseLatched = false;
```

Replace with:

```java
    private static boolean imperioUseLatched = false;
    /** Client side: right-click cast Alohomora or Revelio and is still down (it may be picking a lock). */
    private static boolean lockUseLatched = false;
```

In `handleWandUse`, find:

```java
                if (selected == SpellType.CRUCIO) {
                    if (com.hogwartsmagic.event.ClientModEvents.isCrucioHoldPending()) {
                        return true;
                    }
                    com.hogwartsmagic.event.ClientModEvents.startCrucioHold();
                }
                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
                player.swing(InteractionHand.MAIN_HAND);
```

Replace with:

```java
                if (selected == SpellType.CRUCIO) {
                    if (com.hogwartsmagic.event.ClientModEvents.isCrucioHoldPending()) {
                        return true;
                    }
                    com.hogwartsmagic.event.ClientModEvents.startCrucioHold();
                }
                if (selected == SpellType.ALOHOMORA || selected == SpellType.REVELIO) {
                    if (lockUseLatched) {
                        return true; // one cast per press: the held key may be picking a piston lock
                    }
                    lockUseLatched = true;
                    com.hogwartsmagic.util.ClientSideBridge.startLockHold(player);
                }
                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
                player.swing(InteractionHand.MAIN_HAND);
```

(This exact text occurs once in `ModEvents.java`. `ModEventsClientHooks` has a similar block with different indentation. Do not touch that one.)

Find:

```java
    public static void resetUseLatches(boolean resetSerpensortia, boolean resetFiendfyre, boolean resetWingardium, boolean resetImperio) {
```

Replace with:

```java
    /** Client side: right-click is up, so the next press may cast Alohomora or Revelio again. */
    public static void clearLockUseLatch() {
        lockUseLatched = false;
    }

    public static void resetUseLatches(boolean resetSerpensortia, boolean resetFiendfyre, boolean resetWingardium, boolean resetImperio) {
```

- [ ] **Step 8: No cooldown line over the progress bar.** In `ModEventsClientHooks.java`, find:

```java
            if (selected != null && !player.isCreative()) {
                long now = player.level().getGameTime();
```

Replace with:

```java
            // While a lock is picked the server's progress bar owns the action bar.
            if (selected != null && !player.isCreative() && !LockPickClient.isPicking()) {
                long now = player.level().getGameTime();
```

- [ ] **Step 9: Build and the safety greps.** Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, all tests green. Then:
- `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/support/lock/ src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/util/ClientSideBridge.java`. Expected: only lines that existed before (`git diff` adds none).
- `grep -n "switch" src/main/java/com/hogwartsmagic/client/LockPickClient.java`. Expected: nothing.

- [ ] **Step 10: Dedicated-server boot** (a packet was registered and `LockPickClient` must not load on a server). Run the CLOUD-OVERRIDES boot block. Expected: `Done (`, with no `Exception` and no `ERROR` mentioning `LockPick`, `SpellPackets` or `ClientRuntimeBridge`. Afterwards no server java is left.

- [ ] **Step 11: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java src/main/java/com/hogwartsmagic/util/ClientSideBridge.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/LockPickClient.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java
git commit -m "Hold Alohomora and Revelio on the channel heartbeat and draw the lock picking wand" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The hold in `PistonLockSpell` (server)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Consumes:** `LockPickRules` (T1), `LockSolver.Progress`, `solve(..., Progress)` and `plannedAttempts` (T2), `SpellPackets.LockPickStatePacket` and the stubs (T3).

The behaviour, in order of a tick (all on the server thread):
1. `tickHolds` (new). For each held job of this level, drop it at once if any of these is true:
   - the caster is offline, dead or in another dimension;
   - no heartbeat for more than 6 ticks;
   - the main hand holds no wand, or the selected spell is not the job's spell;
   - the caster is stunned or silenced (`CombatEffects.isCastingBlocked`), controlled (`MindControl.isControlled`) or possessing (`PossessionManager.isPossessing`).

   Dropping means: `cancel(true)` on the future (this interrupts the solver), the gray line "You stop picking the lock.", and `LockPickStatePacket(false)`. There is no XP and no world change. Otherwise: if the worker has just started this job, note the time and set the 30 s deadline; if the result is not in, show the bar, the clicks, the piston sounds and the sparks.
2. `finishJobs` (changed). A held job's deadline is its hold's deadline. Any held job leaving here sends `LockPickStatePacket(false)`. On a usable answer, a success click plays before the hints or the playback.
3. `runSteps` (unchanged).

- [ ] **Step 1: Imports.** Find:

```java
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.mixin.BasePressurePlateTypeAccessor;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
```

Replace with:

```java
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.mixin.BasePressurePlateTypeAccessor;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.mind.PossessionManager;
```

Find:

```java
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
```

Replace with:

```java
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
```

Find:

```java
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
```

Replace with:

```java
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
```

Find:

```java
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
```

Replace with:

```java
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.annotation.Nullable;
```

- [ ] **Step 2: Job and Hold.** Find:

```java
    /** door: the block that was struck, standing for the door's chunk. */
    private record Job(UUID caster, ResourceKey<Level> dimension, BlockPos door, CircuitTracer.TracedLock lock, int rank,
                       Use use, Future<LockSolver.Solution> result, long deadline) {
    }
```

Replace with:

```java
    /**
     * door: the block that was struck, standing for the door's chunk (and where the picking is seen and heard).
     * deadline: the unheld cap (/lockinfo). progress: written by the solver thread. planned: the most attempts
     * the solver can make (for the ETA). hold: the caster's hold, for Alohomora and Revelio; null for /lockinfo.
     */
    private record Job(UUID caster, ResourceKey<Level> dimension, BlockPos door, CircuitTracer.TracedLock lock, int rank,
                       Use use, Future<LockSolver.Solution> result, long deadline, LockSolver.Progress progress,
                       long planned, long budget, @Nullable Hold hold) {
    }

    /** A caster keeping the cast held while the lock is picked. Server thread only. */
    private static final class Hold {
        /** The spell the caster must keep selected. */
        final SpellType spell;
        long lastSeen;
        /** Game time the worker was first seen running this job; -1 while it waits in the queue. */
        long startedAt = -1;
        /** startedAt + HELD_SOLVE_TIMEOUT_TICKS once started; a queued job does not time out. */
        long deadline = Long.MAX_VALUE;
        long nextClick;

        Hold(SpellType spell, long lastSeen) {
            this.spell = spell;
            this.lastSeen = lastSeen;
        }
    }

    /** Last time each caster was seen holding the cast (press or heartbeat), or RELEASED. Server thread only. */
    private static final Map<UUID, Long> HELD = new HashMap<>();
    private static final long RELEASED = Long.MIN_VALUE / 2;
```

- [ ] **Step 3: Replace the stubs.** Find (the Task 3 stubs):

```java
    /** The caster still holds the cast (the cast packet heartbeat). Filled in by the hold task. */
    public static void heartbeat(ServerPlayer player) {
    }

    /** The caster let go of the cast. Filled in by the hold task. */
    public static void release(ServerPlayer player) {
    }

    /** An Alohomora or Revelio cast press: it counts as held until the first heartbeat. Filled in by the hold task. */
    public static void noteHoldStart(ServerPlayer player) {
    }
```

Replace with:

```java
    /** The caster still holds Alohomora or Revelio (the cast packet heartbeat). */
    public static void heartbeat(ServerPlayer player) {
        long now = player.level().getGameTime();
        HELD.put(player.getUUID(), now);
        for (Job job : JOBS) {
            if (job.hold() != null && job.caster().equals(player.getUUID())) {
                job.hold().lastSeen = now;
            }
        }
    }

    /** The caster let go: a lock still being picked is dropped at once. */
    public static void release(ServerPlayer player) {
        HELD.put(player.getUUID(), RELEASED);
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.hold() != null && job.caster().equals(player.getUUID())) {
                it.remove();
                stopPicking(player, job);
            }
        }
    }

    /** An Alohomora or Revelio cast press: it counts as held until the first heartbeat. */
    public static void noteHoldStart(ServerPlayer player) {
        HELD.put(player.getUUID(), player.level().getGameTime());
    }

    /** Drops a held job: the solver is interrupted, nothing in the world changes, no XP. */
    private static void stopPicking(@Nullable ServerPlayer player, Job job) {
        job.result().cancel(true);
        if (player != null) {
            tell(player, "stopped", ChatFormatting.GRAY);
            sendState(player, false);
        }
    }

    private static void sendState(ServerPlayer player, boolean picking) {
        SpellPackets.CHANNEL.send(new SpellPackets.LockPickStatePacket(picking), PacketDistributor.PLAYER.with(player));
    }
```

- [ ] **Step 4: `begin` submits with progress and a hold.** Find:

```java
        long budget = LockRules.budget(rank);
        Future<LockSolver.Solution> result = SOLVER.submit(() -> LockSolver.solve(circuit, maxInputs, maxPresses, budget));
        JOBS.add(new Job(id, level.dimension(), hit.immutable(), lock, rank, use, result,
                level.getGameTime() + LockRules.SOLVE_TIMEOUT_TICKS));
        if (use != Use.INSPECT) {
            tell(caster, "reading", ChatFormatting.GRAY);
        }
        return Begin.STARTED;
```

Replace with:

```java
        long budget = LockRules.budget(rank);
        LockSolver.Progress progress = new LockSolver.Progress();
        Future<LockSolver.Solution> result = SOLVER.submit(
                () -> LockSolver.solve(circuit, maxInputs, maxPresses, budget, progress));
        long now = level.getGameTime();
        Hold hold = null;
        if (use != Use.INSPECT) {
            // Held from the press (or the last heartbeat); a caster who let go before the beam landed is RELEASED.
            Long seen = HELD.get(id);
            hold = new Hold(use == Use.REVEAL ? SpellType.REVELIO : SpellType.ALOHOMORA, seen == null ? now : seen);
        }
        JOBS.add(new Job(id, level.dimension(), hit.immutable(), lock, rank, use, result,
                now + LockRules.SOLVE_TIMEOUT_TICKS, progress, LockSolver.plannedAttempts(circuit, maxInputs, maxPresses),
                budget, hold));
        if (hold != null) {
            sendState(caster, true);
        }
        return Begin.STARTED;
```

- [ ] **Step 5: The tick.** Find:

```java
        long now = level.getGameTime();
        finishJobs(level, now);
        runSteps(level, now);
    }
```

Replace with:

```java
        long now = level.getGameTime();
        tickHolds(level, now);
        finishJobs(level, now);
        runSteps(level, now);
    }

    /** Held jobs of this level: drop the ones let go of, start the clock of the ones the worker took, show progress. */
    private static void tickHolds(ServerLevel level, long now) {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            Hold hold = job.hold();
            if (hold == null || job.dimension() != level.dimension()) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(job.caster());
            if (!stillPicking(level, player, hold, now)) {
                it.remove();
                stopPicking(player, job);
                continue;
            }
            if (hold.startedAt < 0 && job.progress().started()) {
                hold.startedAt = now;
                hold.deadline = now + LockPickRules.HELD_SOLVE_TIMEOUT_TICKS;
            }
            if (!job.result().isDone()) {
                showProgress(level, player, job, hold, now);
            }
        }
    }

    /** Still holding the right spell, able to cast and in this level (offline, dead, stunned, controlled: no). */
    private static boolean stillPicking(ServerLevel level, @Nullable ServerPlayer player, Hold hold, long now) {
        if (player == null || !player.isAlive() || player.level() != level) {
            return false;
        }
        if (LockPickRules.holdLapsed(now, hold.lastSeen)) {
            return false;
        }
        if (CombatEffects.isCastingBlocked(player) || MindControl.isControlled(player)
                || PossessionManager.isPossessing(player)) {
            return false;
        }
        ItemStack wand = player.getMainHandItem();
        return MagicWandItem.isAnyWand(wand) && MagicWandItem.getSelectedSpell(wand) == hold.spell;
    }

    /** The bar and time left on the action bar, lock clicks at the caster, faint pistons and sparks at the door. */
    private static void showProgress(ServerLevel level, ServerPlayer player, Job job, Hold hold, long now) {
        LockSolver.Progress p = job.progress();
        long run = hold.startedAt < 0 ? 0 : now - hold.startedAt;
        double fraction = hold.startedAt < 0 ? 0.0 : LockPickRules.fraction(p.attempts(), job.planned(), p.work(),
                job.budget(), run, LockPickRules.HELD_SOLVE_TIMEOUT_TICKS);
        if (now % LockPickRules.BAR_INTERVAL_TICKS == 0) {
            int eta = hold.startedAt < 0 ? LockPickRules.UNKNOWN : LockPickRules.etaSeconds(p.attempts(), job.planned(),
                    p.work(), job.budget(), run, LockPickRules.HELD_SOLVE_TIMEOUT_TICKS);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.lock.picking", bar(fraction),
                    eta == LockPickRules.UNKNOWN ? "~?s" : "~" + eta + "s").withStyle(ChatFormatting.GRAY), true);
        }
        RandomSource random = level.getRandom();
        if (now >= hold.nextClick) {
            hold.nextClick = now + LockPickRules.clickInterval(fraction);
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    random.nextInt(3) == 0 ? SoundEvents.CHAIN_HIT : SoundEvents.TRIPWIRE_CLICK_ON,
                    SoundSource.PLAYERS, 0.18f, 1.2f + random.nextFloat() * 0.8f);
            if (random.nextDouble() < LockPickRules.pistonChance(fraction)) {
                level.playSound(null, job.door(), random.nextBoolean() ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                        SoundSource.BLOCKS, 0.12f, 0.5f + random.nextFloat() * 0.25f);
            }
        }
        BlockPos door = job.door();
        if (now % LockPickRules.SPARK_INTERVAL_TICKS == 0 && level.isLoaded(door)) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5,
                    3, 0.35, 0.35, 0.35, 0.05);
        }
    }

    private static Component bar(double fraction) {
        int filled = LockPickRules.filledCells(fraction);
        return Component.literal("|".repeat(filled)).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("|".repeat(LockPickRules.BAR_CELLS - filled)).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** A held job's cap starts when the worker takes it; /lockinfo keeps its 10 s from the queue. */
    private static long deadline(Job job) {
        return job.hold() == null ? job.deadline() : job.hold().deadline;
    }

    private static void successClick(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.PLAYERS, 0.5f, 1.8f);
    }
```

- [ ] **Step 6: `finishJobs`.** Find:

```java
            if (job.dimension() != level.dimension() || (!job.result().isDone() && now < job.deadline())) {
```

Replace with:

```java
            if (job.dimension() != level.dimension() || (!job.result().isDone() && now < deadline(job))) {
```

Find:

```java
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(job.caster());
            if (!job.result().isDone()) {
```

Replace with:

```java
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(job.caster());
            if (job.hold() != null && player != null) {
                sendState(player, false); // the hold is over either way; letting go no longer matters
            }
            if (!job.result().isDone()) {
```

Find:

```java
                if (job.use() == Use.REVEAL) {
                    LockHints.show(level, job.lock(), solution);
```

Replace with:

```java
                if (job.use() == Use.REVEAL) {
                    successClick(level, player);
                    LockHints.show(level, job.lock(), solution);
```

Find:

```java
                } else {
                    schedule(level, player, job, solution, now);
                }
```

Replace with:

```java
                } else {
                    successClick(level, player);
                    schedule(level, player, job, solution, now);
                }
```

- [ ] **Step 7: Logout and stop.** Find:

```java
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.forEach(j -> j.result().cancel(true));
        JOBS.clear();
        STEPS.clear();
    }
```

Replace with:

```java
    /** A caster who logs out stops picking (tickHolds would drop the job a tick later anyway). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        HELD.remove(id);
        JOBS.removeIf(job -> {
            boolean mine = job.hold() != null && job.caster().equals(id);
            if (mine) {
                job.result().cancel(true);
            }
            return mine;
        });
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.forEach(j -> j.result().cancel(true));
        JOBS.clear();
        STEPS.clear();
        HELD.clear();
    }
```

- [ ] **Step 8: Text.** In `en_us.json`, find:

```
  "message.hogwartsmagic.lock.piston_logic": "This lock moves blocks with pistons; magic cannot read it.",
```

Replace with:

```
  "message.hogwartsmagic.lock.piston_logic": "This lock moves blocks with pistons; magic cannot read it.",
  "message.hogwartsmagic.lock.picking": "Picking the lock %s %s",
  "message.hogwartsmagic.lock.stopped": "You stop picking the lock.",
```

In the `spell.hogwartsmagic.alohomora.desc` line, replace the phrase `works out a small piston door lock (up to 4 levers or buttons).` with `works out a small piston door lock (up to 4 levers or buttons) while you keep the cast held.`. In the `spell.hogwartsmagic.revelio.desc` line, replace `From Expert it shows how a piston door opens:` with `From Expert, while you keep the cast held, it shows how a piston door opens:`. Then validate with `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`. Expected: no output.

- [ ] **Step 9: Build, the safety greps, and the boot.**
- Run `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, all green.
- Run `grep -n "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`. Expected: nothing.
- Run the CLOUD-OVERRIDES boot block. Expected: `Done (`, with no `Exception` or `ERROR` naming `PistonLockSpell`.

- [ ] **Step 10: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Pick piston locks only while the cast is held, with a progress bar, sounds and sparks" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Final verification and jar

**Files:** none changed (fixes, if any, go back to the task that owns the file).

- [ ] **Step 1:** Run the gradle check, then `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew clean build --console=plain`. Expected: BUILD SUCCESSFUL. The test count is 284 + 2 (T0) + 15 (T1) + 5 (T2) = 306, all green, and the report shows no new warnings.
- [ ] **Step 2:** Validate the lang file JSON (the command from Task 4).
- [ ] **Step 3:** Run the safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell src/main/java/com/hogwartsmagic/network src/main/java/com/hogwartsmagic/util src/main/java/com/hogwartsmagic/entity`. Expected: the same lines as at 8adfe2a (compare against `git show 8adfe2a:...` greps), with no new lines.
  - `grep -n "switch" src/main/java/com/hogwartsmagic/client/LockPickClient.java`. Expected: nothing.
- [ ] **Step 4:** Run the dedicated-server boot (the CLOUD-OVERRIDES block). Expected: `Done (`, with no mixin, packet or class-loading errors.
- [ ] **Step 5:** Report the jar path `build/libs/hogwartsmagic-1.0.0.jar` for the user to copy into `C:\Users\alonl\curseforge\minecraft\Instances\hogwarts\mods\` with Minecraft closed. No commit.

---

### Task 6: In-game verification (with the user)

This can't be automated. Install the jar from Task 5. Set ranks with `/spellmastery`. Use `/lockinfo` on any door that misbehaves and paste its two lines back.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | 2×1 sticky piston door, one button; Alohomora Expert | Cast at the door and keep right-click held | The wand tips and twists in first person; quiet clicks; the action bar reads "Picking the lock" with a bar and `~?s`, then a time; a heavier click; the button presses itself, the door opens; "The lock gives way."; XP |
| 2 | same | Cast and let go at once | "You stop picking the lock."; nothing moves; no XP; mana is spent |
| 3 | 6–20-lever lock; Alohomora Master | Hold, watch the bar, let go halfway | Clicks come faster as the bar fills; faint piston sounds from the door now and then; letting go stops it at once with the stop line; nothing changed |
| 4 | same, a second player watching | Hold until it finishes | The watcher sees sparks at the door and hears the clicks; no hand animation on the watcher's screen; the caster's door opens |
| 5 | same | After the success click, let go while the levers are still flipping | The playback finishes anyway; XP once |
| 6 | door from row 1; Revelio Expert | Hold until done / tap and release | Held: success click, the button glows "Press: 1". Released early: stop line, no glow (ore and chest highlights still appear: Revelio's beam XP is unchanged) |
| 7 | row 3 lock | Mid-hold, scroll the hotbar to another item / switch the wand to another spell | The pick stops at once with the stop line; the wand animation ends |
| 8 | row 3 lock, a friend with Stupefy | Get stunned mid-hold | The pick stops with the stop line |
| 9 | row 3 lock | Log out mid-hold, log back in | No stuck animation, no action bar leftover, no door change |
| 10 | two doors, two players, both Master | Both hold at the same moment | The second shows an empty bar and `~?s` until the first finishes, then counts down; both doors open |
| 11 | any door | Keep holding after it opened | No second cast, no "still working" line; the next press casts normally |
| 12 | any door | Cast with a spell-slot key and hold that key | Same as row 1, held by the slot key |
| 13 | any door | Cast point-blank (stand against it) | Same as row 1 (the press counts as held) |
| 14 | stone wall with no door; Alohomora | Hold right-click | Nothing extra happens; no bar, no animation; releasing is silent |
| 15 | door with a comparator reading a chest; Alohomora Master | Hold | After the success click the comparator is forced (chime) and the door opens for 3 s |
| 16 | a door from the piston locks checklist | Run `/lockinfo` | Instant two lines, no hold needed |
| 17 | Alohomora just cast (on cooldown) | Hold on a door | The cooldown timer does not flicker over the progress bar |
| 18 | dedicated server and a client | Join, then row 1 | No registry or packet mismatch; the animation and bar work |

A failure goes back to the implementer of the owning task with the row number, the `/lockinfo` output and `latest.log`.

---

## Self-review notes

- **Requirement coverage.**
  - 1 (must keep holding after the cast): Task 3 (client hold, heartbeat, latch) and Task 4 (`tickHolds`, the press mark, the released mark); rows 1, 2 and 13.
  - 2 (animation, sounds, piston sounds, ETA): Task 3 (`LockPickClient`), Task 4 (`showProgress`) and Task 1 (ETA, pacing); rows 1, 3, 4 and 17.
  - 3 (release stops the simulation): Task 4 `release`/`stopPicking` with an interrupting cancel; rows 2, 3 and 7–9.
  - 4 (the solution ends the hold, the spell proceeds): Task 4 `finishJobs` (sends state false, success click); rows 1, 5, 6 and 11.
- **Controller rulings.**
  - Heartbeat reuse and no C2S packet: Task 3 step 3. The S2C packet is justified in Architecture.
  - Interrupt, line, no XP: Task 4.
  - Client hook via the bridge, no enum switch: Task 3 steps 4 and 9.
  - Server sparks, vanilla sounds: Task 4.
  - Bar plus ETA from remaining attempts and throughput, `~?s` for half a second: Tasks 1 and 4.
  - 30 s held cap with the budget unchanged: `HELD_SOLVE_TIMEOUT_TICKS`; `LockRules.budget` is untouched.
  - `/lockinfo` instant: INSPECT has no hold; row 16.
  - Both spells: `Hold.spell`.
  - Volatile progress and the up-front search size: Task 2.
  - Task 0 M1–M4: Task 0.
- **Type consistency.**
  - `Job(caster, dimension, door, lock, rank, use, result, deadline, progress, planned, budget, hold)` is used only inside `PistonLockSpell`.
  - `LockPickStatePacket(boolean picking)` is the same in Tasks 3 and 4.
  - `startLockHold(Player)` (bridge) and `startLockHold(KeyMapping)` (`ClientModEvents`) are distinct by design.
- **Placeholder scan:** none. The Task 3 stubs are deliberate and Task 4 replaces them by quoted text.
- **Counts:** Task 0 adds 2 tests, Task 1 adds 15 and Task 2 adds 5, which gives 306 if 8adfe2a has 284.
