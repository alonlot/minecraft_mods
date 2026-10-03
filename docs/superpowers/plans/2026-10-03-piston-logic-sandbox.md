# Piston-Logic Sandbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan (see the ledger's cloud rulings).

**Goal:** The hybrid. Normal piston locks keep the fast pure solver (`LockSolver` on its worker thread). A lock the tracer flags `pistonLogic` (pistons moving redstone blocks or observers, slime or honey as logic) is refused today with "magic cannot read it". At Master rank it is instead read in a **sandbox**: the lock's box of blocks is copied into a private dimension the server owns, each candidate answer is worked there with the same actions and the same timing as Alohomora's playback, real Minecraft redstone and pistons tick, and the door pistons are watched. The answer goes back through the existing code: Alohomora plays it back, Revelio shows hints, `/lockinfo` prints it. Both spells use the existing hold-to-pick (progress bar, ETA, release cancels).

**Architecture:**
- **The private level is a datapack dimension, `hogwartsmagic:lock_sandbox`.** It is an empty flat void (`minecraft:the_void` biome, no layers), declared like Diagon Alley's (`data/hogwartsmagic/dimension/`), with the overworld dimension type, so heights match overworld locks. It is a real `ServerLevel`. A fake `Level` subclass cannot work: every scheduled block tick in 1.21 is `BlockBehaviour.tick(BlockState, ServerLevel, BlockPos, RandomSource)`, and `PistonBaseBlock.checkIfExtend` casts the level to `ServerLevel` to read `isHandlingTick()` (checked with `javap -c`: offsets 186 to 202). Creating a `ServerLevel` at runtime is not an option either: its constructor builds its own chunk map, worker threads and storage. A datapack dimension gets all of that from vanilla, and the mod already ships one.
- **Its own clock and extra ticks (the time slicing).** All non-overworld levels read the overworld's game time through their own `DerivedLevelData`. `MinecraftServer.createLevels` makes a new one per level (offset 418, inside the loop). Scheduled ticks are timed from `getLevelData().getGameTime()` (`LevelAccessor.createTick`). So a one-line mixin (`DerivedLevelDataClockMixin`) gives the sandbox's own `DerivedLevelData` a private clock.
  - The sandbox still gets its normal level tick from the server, like every dimension. Its clock advances by one at `LevelTickEvent` START, and that tick is one tick of every copy.
  - At END, `LockSandbox.drive` runs extra **virtual ticks** while they fit `SandboxRules.TICK_BUDGET_NANOS` (2 ms per server tick, the sandbox's own level tick included), at most 40 per server tick, and none while the server's average tick is over 40 ms.
  - A virtual tick is exactly the part of `ServerLevel.tick` a copy needs, in its order, under its `handlingTick` flag: clock + 1, `blockTicks.tick`, `fluidTicks.tick`, `runBlockEvents`, then `tickBlockEntities` (checked with `javap -c ServerLevel.tick`: `handlingTick = true` at offset 7, `blockTicks` at 217, `runBlockEvents` at 319, `handlingTick = false` at 324, `tickBlockEntities` at 455). Private members are reached through Mixin invokers and accessors.
- **Copies keep every `BlockPos` hash.** 1.21 redstone dust updates its neighbours from a `HashSet<BlockPos>` (`RedStoneWireBlock.updatePowerStrength` calls `Sets.newHashSet`), so update order, and with it which piston fires first, depends on absolute coordinates. `Vec3i.hashCode` is `(y + 31 z) * 31 + x` = `31 y + 961 z + x` (checked with `javap -c`; `BlockPos` does not override it). Copy `m` therefore sits at `(x − 961 m, y, z + m)`, which leaves every hash unchanged. Copy 0 is at the lock's own coordinates.
- **Copying is a chunk-load, not a placement.** Block states are written straight into `LevelChunkSection`s: no `onPlace`, no neighbour updates, exactly as when a chunk loads from disk. Block entities are reloaded from NBT (`saveWithFullMetadata`, then `BlockEntity.loadStatic`, then `LevelChunk.addAndRegisterBlockEntity`, which registers tickers and game-event listeners and was checked in the bytecode). Pending scheduled ticks keep their delays and their order. Pending block events are copied too. Moving pistons, comparator outputs and observer pulses come along with their state and NBT.
- **One settled template, cheap resets.** Copy 0 receives the live snapshot and runs until nothing is scheduled or moving (the baseline). That settled box becomes the template. Each attempt starts from the template, rewriting only the positions that differ, reloading the box's block entities and clearing its ticks and events.
- **An attempt.** The levers are flipped at tick 0. The presses come at `LockTimeline.pressTicks` (+20, then +40), the same `LockActions.operate` and the same timeline `PistonLockSpell.schedule` uses. Then the copy runs until it is quiet. The door count is the door pistons whose block differs from the template and has held still for 3 ticks, taken at the best moment.
  - An attempt is rejected if a trap fires (a dispenser or dropper dispensing, or TNT exploding: hooked, and cancelled only in the sandbox), if any entity tries to join the sandbox, if an input will not work, if the copy never settles, or if the copy changes the outer layer of its box (that layer is the escape check).
- **Search order and answer.** `AttemptPlan` enumerates exactly `LockSolver`'s first pass: fewest actions first, more levers before more presses. Up to 8 copies run attempts in parallel. `SandboxSearch` keeps the answer the sequential solver would keep: the lowest-index attempt with the most pistons moved. It stops dispatching after a full answer.
- **Hold and results.** A sandbox read returns a `CompletableFuture<LockSolver.Solution>` and a `LockSolver.Progress`, so `PistonLockSpell`'s `Job`, `tickHolds`, `showProgress`, `finishJobs`, playback, hints and `/lockinfo` work unchanged. Before playback or hints, the real traced blocks are compared with the settled template, and any difference gives "shifted".
- **Thread model.** Everything in the sandbox runs on the server thread: snapshots, writes, ticks, sampling and completing the future. Vanilla world code is single-threaded, which is why this differs from the solver worker. `Progress` keeps its single writer (now the server thread).

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5, Mixin (accessors, invokers, HEAD injects).

**Spec:** No spec file. The binding inputs are the user's instruction "build the hybrid" and the controller rulings in `docs/superpowers/handoff/rulings.md` (the "Piston locks" and "Lock-picking hold" sections), copied below.

## Requirements (from the user)

1. Keep the fast pure solver for normal locks.
2. For locks the tracer flags `pistonLogic`, fall back to a vanilla-exact sandbox: copy the traced region into a private world, apply the inputs there, tick real Minecraft, and watch the door pistons.

## Controller rulings (binding)

- Master rank only. At most 8 inputs and 2 presses.
- Time-sliced across server ticks with a per-tick time budget, so the server never stutters.
- Alohomora and Revelio both use it, through the existing hold-to-pick: progress bar, ETA, and release cancels.
- No forcing inside the sandbox unless the plan shows it is safe. The plan does not use it (Ruling R6).
- Never load chunks in the real world.
- Never power a trap (dispensers, droppers, TNT). The sandbox detects a trap firing and rejects that attempt.
- `/lockinfo` reports sandbox results too.
- Carried from the piston-locks rulings:
  - only player casters;
  - the spell moves the door pistons from whatever state they are in;
  - Alohomora pays XP once, when the last action lands; Revelio XP is unchanged;
  - hint entities stay as they are;
  - one implementer at a time.

## Rulings made while planning (record them in the ledger)

- **R1. A datapack dimension.** The private level is `hogwartsmagic:lock_sandbox`, a datapack dimension. It is not a fake `Level` and not a `ServerLevel` created at runtime (Architecture). It uses the overworld dimension type, so it needs no custom dimension type and its heights match overworld locks. The `the_void` biome has no precipitation and no spawns, so there is no lightning and there are no mobs. Cost: one more dimension in the world, like Diagon Alley.
- **R2. Its own clock, plus extra ticks.** The sandbox runs on a private clock (`DerivedLevelDataClockMixin`) and gets extra virtual ticks. Vanilla gives it one tick per server tick. Without the clock, the throughput would need about 64 copies (several hundred chunks) to finish a worst case in 30 s; with it, 8 copies are enough. Cost: one HEAD inject on `DerivedLevelData.getGameTime` (an identity compare per call) and three invokers.
- **R3. Budget.**
  - 2 ms of wall time per server tick, the sandbox's own level tick included.
  - At most 40 extra ticks per server tick.
  - No extra ticks while the server's average tick is over 40 ms.
  - A sandbox level tick over 15 ms, 3 times in a row, ends the read as OUT_OF_TIME.
  - The constants live in pure `SandboxRules`, not `Config`.
- **R4. Hash-preserving copies.** Copy `m` sits at `(x − 961 m, y, z + m)`, which keeps every position's hash, so updates run in the same order. Copy 0 is at the door's own coordinates. A box side may be at most 64 blocks (well below 961) and the box at most 32 768 blocks.
- **R5. The copy box.**
  - It holds every traced block, plus each traced piston's reach (13 blocks in front), plus a margin of 2.
  - If anything on its outermost layer changes during an attempt, the whole read is refused with "This lock's moving parts reach too far to copy." Larger slime and honey machines end up here.
- **R6. No forcing and no item keys.** The sandbox retraces with `Mode(false, false)`.
  - Comparators reading containers read the real copied contents, which is exact.
  - Item frames are entities and are refused (R8).
  - Forcing could be made safe later: `ForcedSignals` is keyed by dimension, runs on the server thread, and expires by game time, which the sandbox shares through its clock. It is left out by the binding default.
- **R7. Refused blocks ("This lock is built from things magic cannot copy.").**
  - Any block or block entity not in the `minecraft` namespace.
  - Command blocks (all three), structure and jigsaw blocks, daylight detectors (they read a sky the copy does not have), sculk shriekers, spawners, trial spawners and vaults, nether and end portals, end gateways, beacons, conduits, bee nests and hives, and respawn anchors.
- **R8. Refused surroundings.**
  - Any entity in the box other than players and lock hints, or any pressed plate, tripwire or detector rail, gives "Something is standing in this lock". Entities are not copied, so the copy would differ.
  - A flowing (non-source) fluid or a pending fluid tick in the box gives "Flowing water or lava runs through this lock".
- **R9. Trap.**
  - A trap is a dispenser or dropper reaching `dispenseFrom`, or TNT reaching `explode`, in the sandbox. Both are cancelled there, so nothing is fired, even in the copy.
  - Any entity trying to join the sandbox (a falling block, an item, an arrow) is cancelled and also rejects the attempt.
  - A trap firing in the baseline, with nobody touching the lock, gives UNSTABLE.
- **R10. The door.**
  - The sandbox's door is the traced door pistons minus the pistons the tracer flags as logic. Under the plan-mandated M3, every piston within 6 blocks counts as the door; the pistons that push wiring are dropped here.
  - Moved = the block at a door piston's position differs from the settled template, and every door piston has held its block for 3 ticks with none moving. The best such moment of the attempt counts, as `LockSolver` counts the best moment.
  - The attempt is valid only if the copy is quiet for 4 ticks within 300 ticks of the last action. Quiet means nothing scheduled, no block event and no moving piston in the box.
- **R11. Same timeline as playback.**
  - The levers are flipped at tick 0, in answer order.
  - The first press comes 20 ticks after the levers (at once if there are none), and the next one 40 ticks later.
  - `PistonLockSpell.schedule` is refactored onto the same `LockTimeline`, so the two cannot drift.
  - A sculk sensor that is not ready fails the attempt; there is no wait-and-retry. The playback then never meets that wait.
- **R12. The world must still match.** Before playback or hints, every traced block of the real lock must equal the settled template (copy 0 sits at the real coordinates). Otherwise the caster gets "shifted".
- **R13. One read at a time.** One sandbox read runs at a time; the others wait in a queue. A held read in the queue shows `~?s` and its 30 s cap starts only when it becomes active (as the solver queue). `/lockinfo` on a sandbox lock uses the 30 s held cap instead of 10 s, once started.
- **R14. Not saved.**
  - The sandbox has `noSave = true`. Every copy is wiped to air when its read ends, and its chunk tickets are released.
  - When a read starts, block entities and ticks in its chunks are cleared, in case a crashed read left any.
  - Nothing deletes region files. Junk blocks left outside a box by a crash stay inert. Accepted.
- **R15. Nobody enters it.**
  - `EntityTravelToDimensionEvent` to the sandbox is cancelled.
  - A player who still arrives (by `/execute in … tp`) or logs in there is sent to the overworld spawn on the next server tick.
- **R16. Random ticks.** They still happen in the sandbox's own level tick and are skipped in the extra ticks. Both are random, so this is accepted.
- **R17. Cave Inimicum.** `ModEvents.onPistonPost` returns early in the sandbox. Otherwise every piston move in a copy would queue a Cave Inimicum scan of 125 positions on the sandbox level.
- **R18. Lang.**
  - `lock.piston_logic` now reads "This lock moves blocks with pistons; only a Master can read it."; it is shown only below Master now.
  - Five new `lock.sandbox_*` keys.
  - The spell descriptions are unchanged.
- **R19. No new packets.** The network protocol stays at 6.

## Controller rulings on the pre-flight decisions (binding; numbers match preflight.md)

1. Datapack dimension `hogwartsmagic:lock_sandbox` - accepted (a fake Level cannot run 1.21 scheduled ticks; a runtime ServerLevel means rebuilding chunk storage).
2. Private clock mixin plus extra ticks - accepted.
3. Budget constants (2 ms per tick, 40 extra ticks, overload at 40 ms, heavy stop after 3 ticks over 15 ms, 400k copy-ticks per read) - accepted as constants in `SandboxRules`, not `Config`; they can move to Config later if a server needs it.
4. Hash-preserving copies (x - 961m, z + m), 8 copies, box at most 64 per side and 32,768 blocks, margin 2, piston reach 13 - accepted.
5. CHANGED: sandbox reads get a 60 s held cap (`HELD_SANDBOX_TIMEOUT_TICKS = 1200`) and a 60 s `/lockinfo` cap; the pure solver keeps 30 s held and 10 s for `/lockinfo`. Reason: typical locks take 1-5 s, and the cap only matters for heavy ones, which otherwise always fail.
6. No forcing and no item keys in the sandbox; item frames refused - accepted.
7. The refused block list - accepted.
8. CHANGED: item entities and experience orbs inside the box are ignored (not copied) instead of refusing the read; every other entity still refuses. Pressed plates, tripwires and detector rails, and flowing fluids, still refuse. Reason: one dropped item should not make a door unreadable; a plate it rests on is already caught by the pressed-plate refusal.
9. Trap rules (dispenser, dropper, TNT cancelled in the sandbox only; an entity join rejects the attempt; a trap firing at rest gives UNSTABLE) - accepted.
10. Door excludes logic pistons; success = door still for 3 ticks; the copy must go quiet for 4 ticks within 300 - accepted.
11. Same timeline as playback; an unready sculk sensor fails the attempt - accepted.
12. Real-lock-matches-copy check before playback or hints, else "shifted" - accepted.
13. CHANGED: random ticks are off in the sandbox level entirely (its own level tick too, via the existing mixin set), so leaves and crops on the outer layer never trip the escape check. An outer-layer change still refuses the whole read. Cost: one more hook; random-tick blocks are not redstone logic.
14. One sandbox read at a time, the rest queued - accepted.
15. No region-file deletion; wipe at the end and clean chunks at start - accepted (deleting folders is the riskier code).
16. Players reaching the sandbox by command go to overworld spawn - accepted.
17. Hopper/block-entity ticker order following copy order - accepted; random ticks per ruling 13.
18. Cave Inimicum piston hook skips the sandbox - accepted.
19. CHANGED: also update the Alohomora and Revelio descriptions to say a Master can read piston-logic locks (one phrase each), besides the new `piston_logic` line and the 5 new lines.
20. An unexpected error switches the sandbox off until restart - accepted (safer than retrying a broken state).
21. Other mods' piston and claim rules apply in the sandbox - accepted as a known limit.
22. Task 8 exactness rows 4, 5 and 18 - accepted; add one row: a hidden door driven by a sticky piston moving a redstone block (a piston-tape or T flip-flop), opened by Alohomora Master and checked with `/lockinfo`.

Status: plan only. The user asked to stop after the plan; nothing in it is built yet.

## Global Constraints

- Server authoritative. `spell/`, `network/`, `event/ModEvents`, `util/`, `entity/` and `mixin/` (non-client) never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. This plan adds no client code.
- No `switch` on an enum in client code (none is added). The new server code uses if-chains.
- The solver worker still touches only `LockCircuit`, `LockSimulator`, `LockSolver` and `LockPickRules`. Every sandbox class runs on the server thread.
- Never load a chunk in the real world. The real world is read only through `getChunkNow` (it returns null for an unloaded chunk) and `isLoaded`-checked positions. The sandbox's chunks are loaded only by its own region tickets.
- Terrain rules (mobGriefing, claims, wards) are not consulted, by the user's earlier piston-lock requirement 4 (ledger). The sandbox changes nothing outside its own dimension.
- Cloud build rules (`.superpowers/sdd/CLOUD-OVERRIDES.md`) replace the standing Windows rules:
  - No `tasklist` check. Before every gradle run, run `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If another build is running, wait (poll every 30 s, at most 20 min).
  - Build and test: `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew compileJava test --console=plain`.
  - Server boot: the CLOUD-OVERRIDES block (`run/eula.txt` must contain `eula=true`).
  - JSON: `python3 -c "import json;json.load(open('PATH',encoding='utf-8'))"`.
- Commit per task:
  - Stage only the named files. Never stage `.claude/settings.local.json` or `.superpowers/`.
  - Use `git commit -m "<subject>" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"`.
  - No apostrophes in heredoc bodies. Never push (the controller pushes).
- `en_us.json` stays valid JSON. Inserted lines end with a comma and go after a line that already ends with one.
- Line numbers drift. Every edit names the exact text to find, quoted from HEAD f24118f (or from an earlier task of this plan, which is said where it applies).
- The existing tests stay unchanged and green. Call the count Gradle prints at f24118f **N**: 319 `@Test` methods by grep; use the number from the first run in Task 1 Step 2. This plan adds 31.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxRules.java` | Pure: caps, copy offsets, box maths, budget pacing | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockTimeline.java` | Pure: when each playback step runs (shared by playback and sandbox) | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/support/lock/SandboxRulesTest.java` | 12 tests | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/support/lock/LockTimelineTest.java` | 5 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/AttemptPlan.java` | Pure: LockSolver's first-pass order as a list | 2 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSearch.java` | Pure: out-of-order results, the sequential answer, when to stop | 2 |
| Create `src/test/java/com/hogwartsmagic/spell/support/lock/AttemptPlanTest.java` | 7 tests | 2 |
| Create `src/test/java/com/hogwartsmagic/spell/support/lock/SandboxSearchTest.java` | 7 tests | 2 |
| Create `src/main/resources/data/hogwartsmagic/dimension/lock_sandbox.json` | The dimension | 3 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxWorld.java` | Level ownership: clock, tickets, virtual tick, guards | 3 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java` | Stub (T3), then the driver (T5) | 3, 5 |
| Create `src/main/java/com/hogwartsmagic/mixin/DerivedLevelDataClockMixin.java` | Sandbox game time | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/ServerLevelSandboxAccessor.java` | `tickBlock`, `tickFluid`, `runBlockEvents`, `handlingTick`, `blockEvents` | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/LevelBlockEntityTickInvoker.java` | `tickBlockEntities` | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/RedstoneTorchTogglesAccessor.java` | Torch burnout memory (static) | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/RedstoneTorchToggleAccessor.java` | A toggle's position | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/DispenserSandboxMixin.java` | Trap hook | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/DropperSandboxMixin.java` | Trap hook | 3 |
| Create `src/main/java/com/hogwartsmagic/mixin/TntSandboxMixin.java` | Trap hook | 3 |
| Modify `src/main/resources/hogwartsmagic.mixins.json` | Register the 8 mixins | 3 |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | `onPistonPost` skips the sandbox | 3 |
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java` | `TracedLock` gets `region`, `doorPistons`, `logicPistons` | 4 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSnapshot.java` | Capture, write, compare, wipe a box | 4 |
| Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockActions.java` | `operate`, moved out of `PistonLockSpell` | 4 |
| Modify `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java` | `operate` moved; `schedule` on `LockTimeline` (T4); the sandbox branch (T6) | 4, 6 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Sandbox lines | 6 |
| Modify `src/main/java/com/hogwartsmagic/command/LockInfoCommand.java` | Comment and javadoc | 6 |

## Pre-flight table (the controller writes it to the ledger)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T4, T5, T6 | `SandboxRules`: `MAX_INPUTS`, `MAX_PRESSES`, `MAX_SLOTS`, `MARGIN`, `PUSH_REACH`, `MAX_SPAN`, `MAX_VOLUME`, `SLOT_STEP`, `DOOR_STABLE_TICKS`, `QUIET_TICKS`, `SETTLE_CAP_TICKS`, `PREPARE_TIMEOUT_TICKS`, `TICK_BUDGET_NANOS`, `MAX_EXTRA_TICKS`, `OVERLOADED_TICK_NANOS`, `HEAVY_TICK_NANOS`, `HEAVY_TICKS_IN_A_ROW`, `SLOT_TICK_BUDGET`; `eligible(int)`, `slotDx(int)`, `slotDz(int)`, `positionHash(int,int,int)`, `anotherTick(int,long,long,long)`, `fits(Box)`, `heightFits(Box,int,int,int,int)`; `Box(minX,minY,minZ,maxX,maxY,maxZ)` with `of`, `include`, `grow`, `offset`, `sizeX/Y/Z`, `volume`, `contains`, `onShell`, `index`, `x/y/z(int)` | names identical |
| T1 → T4, T5 | `LockTimeline.togetherAt(int)`, `pressTicks(int, boolean, boolean[])` | identical |
| T2 → T5, T6 | `AttemptPlan.of(int[], int[][], int[], int)` → `List<Attempt>` or null; `Attempt(index, set, to, presses)`, `actions()`; `SandboxSearch(List<Attempt>, int)`, `next()`, `finish(int,int)`, `complete()`, `best()`, `moved(int)`, `finished()` | identical |
| T3 → T4, T5 | `SandboxWorld.KEY`, `clockOwner`, `clock()`, `level(MinecraftServer)`, `isSandbox(Level)`, `markBroken(Throwable)`, `virtualTick(ServerLevel)`, `hold/release/ready(ServerLevel, ChunkPos)`; accessors `ServerLevelSandboxAccessor.hogwartsmagic$tickBlock/$tickFluid/$runBlockEvents/$setHandlingTick/$getBlockEvents`, `LevelBlockEntityTickInvoker.hogwartsmagic$tickBlockEntities`, `RedstoneTorchTogglesAccessor.hogwartsmagic$getRecentToggles()`, `RedstoneTorchToggleAccessor.hogwartsmagic$getPos()` | identical |
| T3 → T5 | The `LockSandbox` stub: `busy()`, `drive(ServerLevel, long)`, `spilled(BlockPos)`, `trapFired(BlockPos)`; T5 replaces the whole file and keeps the four signatures | stub quoted in T5 |
| T4 → T5, T6 | `CircuitTracer.TracedLock(circuit, inputs, pistons, blocks, region, doorPistons, logicPistons)`; `SandboxSnapshot.boxFor`, `capture`, `Capture(snapshot, refusal)`, `box()`, `stateAt`, `write`, `shellChanged`, `matches`, `clear`, `clean`; `LockActions.operate(ServerLevel, BlockPos, BlockState)` | identical |
| T5 → T6 | `LockSandbox.submit(TracedLock, SandboxSnapshot, List<BlockPos>, List<Attempt>)` → `Handle` with `result`, `progress`, `refusal`, `stillAsCopied(ServerLevel)`, `stats()` | identical |

---

### Task 1: SandboxRules and LockTimeline (pure, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxRules.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/LockTimeline.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/SandboxRulesTest.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/LockTimelineTest.java`

**Produces:** everything in the pre-flight rows T1.

- [ ] **Step 1: Write the failing tests.** Create `SandboxRulesTest.java`:

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxRulesTest {
    @Test
    void masterOnly() {
        assertFalse(SandboxRules.eligible(3));
        assertTrue(SandboxRules.eligible(4));
    }

    @Test
    void caps() {
        assertEquals(8, SandboxRules.MAX_INPUTS);
        assertEquals(2, SandboxRules.MAX_PRESSES);
        assertEquals(8, SandboxRules.MAX_SLOTS);
        assertEquals(2_000_000L, SandboxRules.TICK_BUDGET_NANOS);
    }

    @Test
    void copyZeroIsTheDoorItself() {
        assertEquals(0, SandboxRules.slotDx(0));
        assertEquals(0, SandboxRules.slotDz(0));
    }

    @Test
    void copiesKeepEveryPositionHash() {
        int[][] points = {{0, 64, 0}, {123, -40, -987}, {-29_999_000, 300, 29_999_000}, {5, 0, -5}};
        for (int m = 0; m < SandboxRules.MAX_SLOTS; m++) {
            for (int[] p : points) {
                assertEquals(SandboxRules.positionHash(p[0], p[1], p[2]),
                        SandboxRules.positionHash(p[0] + SandboxRules.slotDx(m), p[1], p[2] + SandboxRules.slotDz(m)),
                        "copy " + m);
            }
        }
    }

    @Test
    void copiesNeverTouch() {
        assertTrue(SandboxRules.MAX_SPAN < SandboxRules.SLOT_STEP);
        assertEquals(-961, SandboxRules.slotDx(1) - SandboxRules.slotDx(0));
        assertEquals(1, SandboxRules.slotDz(1) - SandboxRules.slotDz(0));
    }

    @Test
    void boxIndexRoundTrips() {
        SandboxRules.Box b = new SandboxRules.Box(-3, 10, 5, 2, 12, 9);
        assertEquals(6 * 3 * 5, b.volume());
        for (int i = 0; i < b.volume(); i++) {
            assertEquals(i, b.index(b.x(i), b.y(i), b.z(i)));
            assertTrue(b.contains(b.x(i), b.y(i), b.z(i)));
        }
    }

    @Test
    void includeAndGrow() {
        SandboxRules.Box b = SandboxRules.Box.of(0, 0, 0).include(3, -2, 1).grow(2);
        assertEquals(new SandboxRules.Box(-2, -4, -2, 5, 2, 3), b);
    }

    @Test
    void shell() {
        SandboxRules.Box b = new SandboxRules.Box(0, 0, 0, 4, 4, 4);
        assertTrue(b.onShell(0, 2, 2));
        assertTrue(b.onShell(2, 4, 2));
        assertTrue(b.onShell(2, 2, 0));
        assertFalse(b.onShell(2, 2, 2));
        assertFalse(b.onShell(5, 2, 2));
    }

    @Test
    void offset() {
        assertEquals(new SandboxRules.Box(-961, 0, 1, -951, 5, 11),
                new SandboxRules.Box(0, 0, 0, 10, 5, 10).offset(-961, 0, 1));
    }

    @Test
    void fits() {
        assertTrue(SandboxRules.fits(new SandboxRules.Box(0, 0, 0, 31, 31, 31)));
        assertFalse(SandboxRules.fits(new SandboxRules.Box(0, 0, 0, 64, 0, 0))); // 65 long
        assertFalse(SandboxRules.fits(new SandboxRules.Box(0, 0, 0, 63, 63, 63))); // 262 144 blocks
        assertTrue(SandboxRules.fits(new SandboxRules.Box(0, 0, 0, 63, 7, 63))); // exactly 32 768
    }

    @Test
    void heightFits() {
        // overworld lock: same limits as the sandbox
        assertTrue(SandboxRules.heightFits(new SandboxRules.Box(0, -64, 0, 10, 80, 10), -64, 320, -64, 320));
        assertFalse(SandboxRules.heightFits(new SandboxRules.Box(0, -70, 0, 10, 80, 10), -64, 320, -64, 320));
        // nether lock: other limits, so it must stay a piston reach away from them
        assertFalse(SandboxRules.heightFits(new SandboxRules.Box(0, 5, 0, 10, 20, 10), 0, 256, -64, 320));
        assertTrue(SandboxRules.heightFits(new SandboxRules.Box(0, 40, 0, 10, 60, 10), 0, 256, -64, 320));
        assertFalse(SandboxRules.heightFits(new SandboxRules.Box(0, 200, 0, 10, 250, 10), 0, 256, -64, 320));
    }

    @Test
    void extraTicksStayInTheBudget() {
        assertTrue(SandboxRules.anotherTick(0, 0, 0, 10_000_000L));
        assertFalse(SandboxRules.anotherTick(SandboxRules.MAX_EXTRA_TICKS, 0, 1, 0));
        assertFalse(SandboxRules.anotherTick(0, 1_900_000L, 200_000L, 0)); // 1.9 ms spent + 0.2 ms > 2 ms
        assertTrue(SandboxRules.anotherTick(0, 1_700_000L, 200_000L, 0));
        assertFalse(SandboxRules.anotherTick(0, 0, 1, SandboxRules.OVERLOADED_TICK_NANOS + 1));
    }
}
```

Create `LockTimelineTest.java`:

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LockTimelineTest {
    @Test
    void leversOnly() {
        assertArrayEquals(new int[0], LockTimeline.pressTicks(3, false, new boolean[0]));
        assertEquals(20, LockTimeline.togetherAt(3));
        assertEquals(0, LockTimeline.togetherAt(0));
    }

    @Test
    void pressesAfterLevers() {
        assertArrayEquals(new int[] {20, 60}, LockTimeline.pressTicks(2, false, new boolean[] {false, false}));
    }

    @Test
    void pressesWithoutLevers() {
        assertArrayEquals(new int[] {0, 40}, LockTimeline.pressTicks(0, false, new boolean[2]));
    }

    @Test
    void forcedPressesWaitLonger() {
        assertArrayEquals(new int[] {0, 80, 120}, LockTimeline.pressTicks(0, false, new boolean[] {true, false, false}));
    }

    @Test
    void aForcedSubsetComesFirst() {
        assertArrayEquals(new int[] {100}, LockTimeline.pressTicks(1, true, new boolean[] {false}));
    }
}
```

- [ ] **Step 2: Run them and see them fail.** Run `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"` (wait if a build is running), then `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew test --console=plain`. Expected: compilation failure (no `SandboxRules`, no `LockTimeline`). Record **N** as the test count of the Step 5 run minus 17, and write it in the report so later tasks can check their counts.

- [ ] **Step 3: Write `SandboxRules.java`.**

```java
package com.hogwartsmagic.spell.support.lock;

/**
 * The piston-logic sandbox (Alohomora and Revelio Master): which locks it copies, where the copies go and how
 * it is paced. Pure numbers.
 */
public final class SandboxRules {
    /** Levers, buttons, plates and sensors the sandbox may work; it never forces a source or sets an item key. */
    public static final int MAX_INPUTS = 8;
    public static final int MAX_PRESSES = 2;
    /** Copies of the lock that run side by side. */
    public static final int MAX_SLOTS = 8;
    /** The copy reaches this far past the traced wiring and every piston's reach. */
    public static final int MARGIN = 2;
    /** A piston pushes at most 12 blocks; the last one moves into the 13th position. */
    public static final int PUSH_REACH = 13;
    /** Longest side of a copy, in blocks; far below SLOT_STEP, so two copies never touch. */
    public static final int MAX_SPAN = 64;
    public static final int MAX_VOLUME = 32_768;
    /**
     * Copy m sits at (x - 961 m, y, z + m). BlockPos.hashCode is (y + 31 z) * 31 + x = 31 y + 961 z + x, so every
     * copied position keeps its hash: hash sets of positions (redstone dust updates its neighbours from one)
     * iterate in the same order as at the real door, and update order is part of how pistons behave.
     */
    public static final int SLOT_STEP = 961;
    /** A door piston counts only once it has held the same block for this many ticks. */
    public static final int DOOR_STABLE_TICKS = 3;
    /** After the last action, a copy is done once nothing is scheduled, queued or moving for this many ticks. */
    public static final int QUIET_TICKS = 4;
    /** A copy still busy this many ticks after its last action (or after it was made) never settles. */
    public static final int SETTLE_CAP_TICKS = 300;
    /** Server ticks to wait for the copies' chunks to load in the sandbox before giving up. */
    public static final int PREPARE_TIMEOUT_TICKS = 200;
    /** Wall time the sandbox may use per server tick, its own level tick included. */
    public static final long TICK_BUDGET_NANOS = 2_000_000L;
    /** Extra sandbox ticks per server tick, at most, on top of the level tick every dimension gets. */
    public static final int MAX_EXTRA_TICKS = 40;
    /** While the server's average tick is slower than this, the sandbox runs no extra ticks. */
    public static final long OVERLOADED_TICK_NANOS = 40_000_000L;
    /** A sandbox level tick slower than this, HEAVY_TICKS_IN_A_ROW times running, ends the read. */
    public static final long HEAVY_TICK_NANOS = 15_000_000L;
    public static final int HEAVY_TICKS_IN_A_ROW = 3;
    /** Copy-ticks (one copy for one tick) a whole read may spend: the progress bar's work budget. */
    public static final long SLOT_TICK_BUDGET = 400_000L;

    private SandboxRules() {
    }

    public static boolean eligible(int rank) {
        return rank >= 4;
    }

    public static int slotDx(int m) {
        return -SLOT_STEP * m;
    }

    public static int slotDz(int m) {
        return m;
    }

    /** Vec3i.hashCode in 1.21 (BlockPos does not override it). */
    public static int positionHash(int x, int y, int z) {
        return (y + z * 31) * 31 + x;
    }

    /**
     * May the sandbox run one more extra tick this server tick? done: extra ticks run so far; spentNanos: the
     * sandbox's time this server tick, its level tick included; lastTickNanos: what the last tick cost.
     */
    public static boolean anotherTick(int done, long spentNanos, long lastTickNanos, long serverAverageNanos) {
        return done < MAX_EXTRA_TICKS && serverAverageNanos <= OVERLOADED_TICK_NANOS
                && spentNanos + Math.max(1L, lastTickNanos) <= TICK_BUDGET_NANOS;
    }

    /** True if a copy this big may be made. */
    public static boolean fits(Box box) {
        return box.sizeX() <= MAX_SPAN && box.sizeY() <= MAX_SPAN && box.sizeZ() <= MAX_SPAN
                && box.volume() <= MAX_VOLUME;
    }

    /**
     * True if a copy of box lies inside the sandbox's build height, and stays a piston's reach away from any
     * build limit of its own world that differs from the sandbox's (a piston at a world's floor or roof cannot
     * push past it, while in the copy it could).
     */
    public static boolean heightFits(Box box, int sourceMin, int sourceMax, int sandboxMin, int sandboxMax) {
        if (box.minY() < sandboxMin || box.maxY() >= sandboxMax) {
            return false;
        }
        boolean floor = sourceMin == sandboxMin || box.minY() - PUSH_REACH >= sourceMin;
        boolean roof = sourceMax == sandboxMax || box.maxY() + PUSH_REACH < sourceMax;
        return floor && roof;
    }

    /** A box of block positions, both corners inclusive. Index order: x fastest, then z, then y. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Box of(int x, int y, int z) {
            return new Box(x, y, z, x, y, z);
        }

        public Box include(int x, int y, int z) {
            return new Box(Math.min(minX, x), Math.min(minY, y), Math.min(minZ, z),
                    Math.max(maxX, x), Math.max(maxY, y), Math.max(maxZ, z));
        }

        public Box grow(int by) {
            return new Box(minX - by, minY - by, minZ - by, maxX + by, maxY + by, maxZ + by);
        }

        public Box offset(int dx, int dy, int dz) {
            return new Box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
        }

        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public long volume() {
            return (long) sizeX() * sizeY() * sizeZ();
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** On the outermost layer of the box. */
        public boolean onShell(int x, int y, int z) {
            return contains(x, y, z)
                    && (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
        }

        public int index(int x, int y, int z) {
            return (x - minX) + sizeX() * ((z - minZ) + sizeZ() * (y - minY));
        }

        public int x(int index) {
            return minX + index % sizeX();
        }

        public int z(int index) {
            return minZ + (index / sizeX()) % sizeZ();
        }

        public int y(int index) {
            return minY + index / (sizeX() * sizeZ());
        }
    }
}
```

- [ ] **Step 4: Write `LockTimeline.java`.**

```java
package com.hogwartsmagic.spell.support.lock;

/**
 * When each step of a played-back answer runs, in ticks after the first step. Alohomora's playback
 * (PistonLockSpell.schedule) and the sandbox's copies (LockSandbox) both use it, so a copy is worked on the
 * same timeline the door will be. Pure.
 */
public final class LockTimeline {
    private LockTimeline() {
    }

    /** Levers and item keys all go at tick 0; a forced subset goes once they have settled. */
    public static int togetherAt(int levers) {
        return levers > 0 ? LockRules.LEVER_SETTLE_TICKS : 0;
    }

    /**
     * The tick of each press. levers: how many levers or item keys are set first; together: a forced subset is
     * held on before the presses; forced[i]: press i forces a source (a longer gap follows it).
     */
    public static int[] pressTicks(int levers, boolean together, boolean[] forced) {
        int at = togetherAt(levers) + (together ? LockRules.FORCE_GAP_TICKS : 0);
        int[] out = new int[forced.length];
        for (int i = 0; i < forced.length; i++) {
            out[i] = at;
            at += forced[i] ? LockRules.FORCE_GAP_TICKS : LockRules.PRESS_GAP_TICKS;
        }
        return out;
    }
}
```

- [ ] **Step 5: Run the tests.** `timeout 900 ./gradlew test --console=plain`. Expected: BUILD SUCCESSFUL, N + 17 tests, all green.

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/SandboxRules.java src/main/java/com/hogwartsmagic/spell/support/lock/LockTimeline.java src/test/java/com/hogwartsmagic/spell/support/lock/SandboxRulesTest.java src/test/java/com/hogwartsmagic/spell/support/lock/LockTimelineTest.java
git commit -m "Add the piston sandbox rules and the shared playback timeline" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: AttemptPlan and SandboxSearch (pure, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/AttemptPlan.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSearch.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/AttemptPlanTest.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/lock/SandboxSearchTest.java`

**Consumes:** `LockPickRules.attempts` (existing).
**Produces:** the pre-flight row T2.

`AttemptPlan` reproduces `LockSolver.Search.run` with `forceFrom = -1` (pass 1), loop for loop:
- cost from 1;
- `k` from `min(cost, n)` down to `max(0, cost − pressCap)`;
- k-combinations in lexicographic order, skipping a pick in which any input has no other level;
- the mixed odometer over the alternatives;
- the press-sequence odometer.

Its size equals `LockPickRules.attempts(alternativeCounts, pulses, 0, maxPresses)`, which the progress bar already uses.

- [ ] **Step 1: Write the failing tests.** Create `AttemptPlanTest.java`:

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttemptPlanTest {
    private static void assertAttempt(AttemptPlan.Attempt a, int[] set, int[] to, int[] presses) {
        assertArrayEquals(set, a.set(), "set of " + a.index());
        assertArrayEquals(to, a.to(), "to of " + a.index());
        assertArrayEquals(presses, a.presses(), "presses of " + a.index());
    }

    @Test
    void theOrderOfLockSolversFirstPass() {
        // levers 0 and 1 (both off), button 2, one press
        List<AttemptPlan.Attempt> plan = AttemptPlan.of(new int[] {0, 1}, new int[][] {{15}, {15}}, new int[] {2}, 1);
        assertEquals(7, plan.size());
        assertAttempt(plan.get(0), new int[] {0}, new int[] {15}, new int[0]);
        assertAttempt(plan.get(1), new int[] {1}, new int[] {15}, new int[0]);
        assertAttempt(plan.get(2), new int[0], new int[0], new int[] {2});
        assertAttempt(plan.get(3), new int[] {0, 1}, new int[] {15, 15}, new int[0]);
        assertAttempt(plan.get(4), new int[] {0}, new int[] {15}, new int[] {2});
        assertAttempt(plan.get(5), new int[] {1}, new int[] {15}, new int[] {2});
        assertAttempt(plan.get(6), new int[] {0, 1}, new int[] {15, 15}, new int[] {2});
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(i, plan.get(i).index());
        }
    }

    @Test
    void sequencesRepeatAButton() {
        List<AttemptPlan.Attempt> plan = AttemptPlan.of(new int[0], new int[0][], new int[] {4, 7}, 2);
        assertEquals(6, plan.size());
        assertArrayEquals(new int[] {4}, plan.get(0).presses());
        assertArrayEquals(new int[] {7}, plan.get(1).presses());
        assertArrayEquals(new int[] {4, 4}, plan.get(2).presses());
        assertArrayEquals(new int[] {4, 7}, plan.get(3).presses());
        assertArrayEquals(new int[] {7, 4}, plan.get(4).presses());
        assertArrayEquals(new int[] {7, 7}, plan.get(5).presses());
    }

    @Test
    void theCountMatchesTheProgressBarsPlan() {
        int[][] alts = {{15}, {0}, {15}, {15}};
        List<AttemptPlan.Attempt> plan = AttemptPlan.of(new int[] {0, 1, 2, 3}, alts, new int[] {4, 5, 6, 7}, 2);
        assertEquals(LockPickRules.attempts(new int[] {1, 1, 1, 1}, 4, 0, 2), plan.size());
        assertEquals(335, plan.size()); // 16 lever settings x 21 press sequences - the empty attempt
    }

    @Test
    void anInputWithNoOtherLevelIsSkipped() {
        List<AttemptPlan.Attempt> plan = AttemptPlan.of(new int[] {0, 1}, new int[][] {{}, {15}}, new int[0], 2);
        assertEquals(1, plan.size());
        assertAttempt(plan.get(0), new int[] {1}, new int[] {15}, new int[0]);
    }

    @Test
    void nothingToWork() {
        assertEquals(0, AttemptPlan.of(new int[0], new int[0][], new int[0], 2).size());
    }

    @Test
    void tooBigIsRefused() {
        int[] settable = new int[12];
        int[][] alts = new int[12][];
        for (int i = 0; i < 12; i++) {
            settable[i] = i;
            alts[i] = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        }
        assertNull(AttemptPlan.of(settable, alts, new int[0], 2));
    }

    @Test
    void fewestActionsFirst() {
        int[][] alts = {{15}, {15}, {15}, {15}};
        List<AttemptPlan.Attempt> plan = AttemptPlan.of(new int[] {0, 1, 2, 3}, alts, new int[] {4, 5, 6, 7}, 2);
        for (int i = 1; i < plan.size(); i++) {
            assertTrue(plan.get(i - 1).actions() <= plan.get(i).actions(), "attempt " + i);
        }
    }
}
```

Create `SandboxSearchTest.java`:

```java
package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxSearchTest {
    private static List<AttemptPlan.Attempt> plan(int n) {
        List<AttemptPlan.Attempt> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new AttemptPlan.Attempt(i, new int[0], new int[0], new int[] {i}));
        }
        return out;
    }

    @Test
    void dispatchesInOrder() {
        SandboxSearch s = new SandboxSearch(plan(3), 2);
        assertEquals(0, s.next().index());
        assertEquals(1, s.next().index());
        assertEquals(2, s.next().index());
        assertNull(s.next());
    }

    @Test
    void theLowestIndexWithTheMostMovedWins() {
        SandboxSearch s = new SandboxSearch(plan(4), 3);
        for (int i = 0; i < 4; i++) {
            s.next();
        }
        s.finish(3, 2);
        s.finish(1, 2);
        assertFalse(s.complete());
        s.finish(0, 1);
        s.finish(2, -1);
        assertTrue(s.complete());
        assertEquals(1, s.best());
        assertEquals(2, s.moved(1));
    }

    @Test
    void aFullAnswerStopsLaterAttempts() {
        SandboxSearch s = new SandboxSearch(plan(6), 2);
        s.next();
        s.next();
        s.next();
        s.finish(2, 2);
        assertNull(s.next());
        assertFalse(s.complete());
        s.finish(0, 0);
        assertFalse(s.complete());
        s.finish(1, 1);
        assertTrue(s.complete());
        assertEquals(2, s.best());
    }

    @Test
    void anEarlierFullAnswerReplacesALaterOne() {
        SandboxSearch s = new SandboxSearch(plan(5), 1);
        for (int i = 0; i < 4; i++) {
            s.next();
        }
        s.finish(3, 1);
        s.finish(1, 1);
        assertNull(s.next());
        s.finish(0, -1);
        assertTrue(s.complete()); // attempt 2 can no longer win, so it need not finish
        assertEquals(1, s.best());
    }

    @Test
    void nothingMovesMeansNoAnswer() {
        SandboxSearch s = new SandboxSearch(plan(2), 1);
        s.next();
        s.next();
        s.finish(0, -1);
        s.finish(1, 0);
        assertTrue(s.complete());
        assertEquals(-1, s.best());
    }

    @Test
    void anEmptyPlanIsComplete() {
        SandboxSearch s = new SandboxSearch(List.of(), 1);
        assertNull(s.next());
        assertTrue(s.complete());
        assertEquals(-1, s.best());
    }

    @Test
    void finishedCounts() {
        SandboxSearch s = new SandboxSearch(plan(3), 1);
        s.next();
        s.next();
        s.finish(1, 0);
        assertEquals(1, s.finished());
        s.finish(0, 0);
        assertEquals(2, s.finished());
    }
}
```

- [ ] **Step 2: Run them and see them fail.** Run the gradle check, then `timeout 900 ./gradlew test --console=plain`. Expected: compilation failure (no `AttemptPlan`, no `SandboxSearch`).

- [ ] **Step 3: Write `AttemptPlan.java`.**

```java
package com.hogwartsmagic.spell.support.lock;

import java.util.ArrayList;
import java.util.List;

/**
 * Every attempt LockSolver's first pass makes, in its order: fewest actions first; for the same number, more
 * levers before more presses; then lexicographic picks, levels and press sequences. The piston sandbox runs
 * them on copies of the lock instead of the model. Pure.
 */
public final class AttemptPlan {
    /** A plan bigger than this is refused (the sandbox caps keep real ones under 500). */
    public static final int MAX_PLANNED = 4096;

    /** set[i] goes to level to[i] at tick 0, then presses in order (circuit input indexes). index: the order. */
    public record Attempt(int index, int[] set, int[] to, int[] presses) {
        public int actions() {
            return set.length + presses.length;
        }
    }

    private AttemptPlan() {
    }

    /**
     * settable: lever or item-key inputs; alternatives[i]: the levels settable[i] can move to; pressable: buttons,
     * plates and sensors. Returns null when the plan would be bigger than MAX_PLANNED.
     */
    public static List<Attempt> of(int[] settable, int[][] alternatives, int[] pressable, int maxPresses) {
        int n = settable.length;
        int[] counts = new int[n];
        for (int i = 0; i < n; i++) {
            counts[i] = alternatives[i].length;
        }
        if (LockPickRules.attempts(counts, pressable.length, 0, maxPresses) > MAX_PLANNED) {
            return null;
        }
        List<Attempt> out = new ArrayList<>();
        int pressCap = pressable.length == 0 ? 0 : maxPresses;
        for (int cost = 1; cost <= n + pressCap; cost++) {
            for (int k = Math.min(cost, n); k >= Math.max(0, cost - pressCap); k--) {
                int len = cost - k;
                int[] pick = new int[k];
                for (int i = 0; i < k; i++) {
                    pick[i] = i;
                }
                do {
                    int[] set = new int[k];
                    int[][] alts = new int[k][];
                    boolean possible = true;
                    for (int i = 0; i < k; i++) {
                        set[i] = settable[pick[i]];
                        alts[i] = alternatives[pick[i]];
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
                            int[] presses = new int[len];
                            for (int j = 0; j < len; j++) {
                                presses[j] = pressable[seq[j]];
                            }
                            out.add(new Attempt(out.size(), set, to, presses));
                        } while (nextSequence(seq, pressable.length));
                    } while (nextMixed(choice, alts));
                } while (nextCombination(pick, n));
            }
        }
        return out;
    }

    /** Next k-subset of 0..n-1 in lexicographic order; false after the last (as LockSolver). */
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

    private static boolean nextSequence(int[] seq, int base) {
        for (int i = seq.length - 1; i >= 0; i--) {
            if (++seq[i] < base) {
                return true;
            }
            seq[i] = 0;
        }
        return false;
    }

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

(`continue` inside a `do … while` jumps to the condition, as it does in `LockSolver.Search.run`.)

- [ ] **Step 4: Write `SandboxSearch.java`.**

```java
package com.hogwartsmagic.spell.support.lock;

import java.util.Arrays;
import java.util.List;

/**
 * The sandbox's book of attempts. Copies finish attempts out of order; the answer is the one LockSolver's
 * sequential pass would keep (it only replaces its best on strictly more pistons moved): the lowest-index
 * attempt with the most pistons moved. Once some attempt moves every door piston, later attempts cannot win
 * and are not started. Pure.
 */
public final class SandboxSearch {
    private static final int PENDING = -2;

    private final List<AttemptPlan.Attempt> plan;
    private final int movable;
    private final int[] moved;
    private int next;
    private int fullAt = Integer.MAX_VALUE;
    private int finished;

    /** movable: the door pistons; an attempt that moves them all is a full answer. */
    public SandboxSearch(List<AttemptPlan.Attempt> plan, int movable) {
        this.plan = plan;
        this.movable = movable;
        this.moved = new int[plan.size()];
        Arrays.fill(moved, PENDING);
    }

    /** The next attempt to run, or null when none is left that could still win. */
    public AttemptPlan.Attempt next() {
        if (next >= plan.size() || next > fullAt) {
            return null;
        }
        return plan.get(next++);
    }

    /** result: door pistons moved, or -1 if the attempt was rejected (a trap, an unsettled copy, a failed input). */
    public void finish(int index, int result) {
        if (moved[index] == PENDING) {
            finished++;
        }
        moved[index] = result;
        if (movable > 0 && result >= movable && index < fullAt) {
            fullAt = index;
        }
    }

    /** True once every attempt that could still be the answer has finished. */
    public boolean complete() {
        int limit = fullAt == Integer.MAX_VALUE ? plan.size() : fullAt + 1;
        for (int i = 0; i < limit; i++) {
            if (moved[i] == PENDING) {
                return false;
            }
        }
        return true;
    }

    /** Index of the answer among the attempts finished so far, or -1 if none moved anything. */
    public int best() {
        int limit = fullAt == Integer.MAX_VALUE ? plan.size() : fullAt + 1;
        int best = -1;
        int most = 0;
        for (int i = 0; i < limit; i++) {
            if (moved[i] > most) {
                most = moved[i];
                best = i;
            }
        }
        return best;
    }

    public int moved(int index) {
        return moved[index];
    }

    public int finished() {
        return finished;
    }
}
```

- [ ] **Step 5: Run the tests.** `timeout 900 ./gradlew test --console=plain`. Expected: BUILD SUCCESSFUL, N + 31 tests, all green.

- [ ] **Step 6: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/AttemptPlan.java src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSearch.java src/test/java/com/hogwartsmagic/spell/support/lock/AttemptPlanTest.java src/test/java/com/hogwartsmagic/spell/support/lock/SandboxSearchTest.java
git commit -m "Plan sandbox attempts in the solver order and keep its answer when copies finish out of order" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The sandbox dimension, its clock and its guards

**Files:**
- Create: `src/main/resources/data/hogwartsmagic/dimension/lock_sandbox.json`
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxWorld.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java` (stub)
- Create: the 8 mixin files listed in File Structure
- Modify: `src/main/resources/hogwartsmagic.mixins.json`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`

**Produces:** the pre-flight rows T3.

No unit tests: this is Mixin and level code. It is verified by compiling, by a dedicated-server boot, and in game (Task 8). Mixin's `"defaultRequire": 1` makes a missed injection fail the boot.

- [ ] **Step 1: The dimension.** Create `src/main/resources/data/hogwartsmagic/dimension/lock_sandbox.json`:

```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:the_void",
      "lakes": false,
      "features": false,
      "layers": [],
      "structure_overrides": []
    }
  }
}
```

Validate it: `python3 -c "import json;json.load(open('src/main/resources/data/hogwartsmagic/dimension/lock_sandbox.json',encoding='utf-8'))"`. Expected: no output.

- [ ] **Step 2: The mixins.** Create each file in `src/main/java/com/hogwartsmagic/mixin/`.

`DerivedLevelDataClockMixin.java`:

```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.support.lock.SandboxWorld;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The piston sandbox runs on its own clock. Every level but the overworld reads the overworld's game time
 * through its own DerivedLevelData (one per level, made in MinecraftServer.createLevels), and scheduled ticks
 * are timed from it, so answering for the sandbox's instance alone gives that level a private time.
 */
@Mixin(DerivedLevelData.class)
public abstract class DerivedLevelDataClockMixin {
    @Inject(method = "getGameTime", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$sandboxClock(CallbackInfoReturnable<Long> cir) {
        if (SandboxWorld.clockOwner == (Object) this) {
            cir.setReturnValue(SandboxWorld.clock());
        }
    }
}
```

`ServerLevelSandboxAccessor.java`:

```java
package com.hogwartsmagic.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The parts of ServerLevel.tick the piston sandbox runs again, and the block event queue it copies. */
@Mixin(ServerLevel.class)
public interface ServerLevelSandboxAccessor {
    @Invoker("tickBlock")
    void hogwartsmagic$tickBlock(BlockPos pos, Block block);

    @Invoker("tickFluid")
    void hogwartsmagic$tickFluid(BlockPos pos, Fluid fluid);

    @Invoker("runBlockEvents")
    void hogwartsmagic$runBlockEvents();

    @Accessor("handlingTick")
    void hogwartsmagic$setHandlingTick(boolean handling);

    @Accessor("blockEvents")
    ObjectLinkedOpenHashSet<BlockEventData> hogwartsmagic$getBlockEvents();
}
```

`LevelBlockEntityTickInvoker.java`:

```java
package com.hogwartsmagic.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the piston sandbox tick its block entities (moving pistons, hoppers) in an extra tick. */
@Mixin(Level.class)
public interface LevelBlockEntityTickInvoker {
    @Invoker("tickBlockEntities")
    void hogwartsmagic$tickBlockEntities();
}
```

`RedstoneTorchTogglesAccessor.java`:

```java
package com.hogwartsmagic.mixin;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** The torches' burnout memory, per level: a reset copy must not remember the last attempt's toggles. */
@Mixin(RedstoneTorchBlock.class)
public interface RedstoneTorchTogglesAccessor {
    @Accessor("RECENT_TOGGLES")
    static Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> hogwartsmagic$getRecentToggles() {
        throw new AssertionError();
    }
}
```

`RedstoneTorchToggleAccessor.java`:

```java
package com.hogwartsmagic.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RedstoneTorchBlock.Toggle.class)
public interface RedstoneTorchToggleAccessor {
    @Accessor("pos")
    BlockPos hogwartsmagic$getPos();
}
```

`DispenserSandboxMixin.java`:

```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.support.lock.LockSandbox;
import com.hogwartsmagic.spell.support.lock.SandboxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dispenser firing in the piston sandbox is a trap: the attempt is rejected and nothing is dispensed. */
@Mixin(DispenserBlock.class)
public abstract class DispenserSandboxMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$sandboxDispenserTrap(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (SandboxWorld.isSandbox(level)) {
            LockSandbox.trapFired(pos);
            ci.cancel();
        }
    }
}
```

`DropperSandboxMixin.java`:

```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.support.lock.LockSandbox;
import com.hogwartsmagic.spell.support.lock.SandboxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DropperBlock overrides dispenseFrom without calling DispenserBlock's, so it needs its own trap hook. */
@Mixin(DropperBlock.class)
public abstract class DropperSandboxMixin {
    @Inject(method = "dispenseFrom", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$sandboxDropperTrap(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (SandboxWorld.isSandbox(level)) {
            LockSandbox.trapFired(pos);
            ci.cancel();
        }
    }
}
```

`TntSandboxMixin.java`:

```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.support.lock.LockSandbox;
import com.hogwartsmagic.spell.support.lock.SandboxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** TNT going off in the piston sandbox is a trap: the attempt is rejected and nothing is primed. */
@Mixin(TntBlock.class)
public abstract class TntSandboxMixin {
    @Inject(method = "explode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void hogwartsmagic$sandboxTnt(Level level, BlockPos pos, @Nullable LivingEntity igniter, CallbackInfo ci) {
        if (!level.isClientSide && SandboxWorld.isSandbox(level)) {
            LockSandbox.trapFired(pos);
            ci.cancel();
        }
    }
}
```

(Signatures checked with `javap -p` against the mapped 1.21 jar:
- `DispenserBlock.dispenseFrom(ServerLevel, BlockState, BlockPos)` and `DropperBlock.dispenseFrom(ServerLevel, BlockState, BlockPos)` are protected;
- `TntBlock.explode(Level, BlockPos, LivingEntity)` is private static;
- `ServerLevel.tickBlock(BlockPos, Block)`, `tickFluid(BlockPos, Fluid)` and `runBlockEvents()` are private;
- `ServerLevel.handlingTick` is a private boolean, and `blockEvents` a private final `ObjectLinkedOpenHashSet<BlockEventData>`;
- `Level.tickBlockEntities()` is protected;
- `RedstoneTorchBlock.RECENT_TOGGLES` is a private static final `Map<BlockGetter, List<Toggle>>`;
- `RedstoneTorchBlock.Toggle.pos` is a package-private final `BlockPos`;
- `DerivedLevelData.getGameTime()` is public.)

- [ ] **Step 3: Register them.** In `hogwartsmagic.mixins.json`, find:

```
    "BlockStateSignalMixin",
    "BasePressurePlateTypeAccessor"
  ],
```

Replace with:

```
    "BlockStateSignalMixin",
    "BasePressurePlateTypeAccessor",
    "DerivedLevelDataClockMixin",
    "ServerLevelSandboxAccessor",
    "LevelBlockEntityTickInvoker",
    "RedstoneTorchTogglesAccessor",
    "RedstoneTorchToggleAccessor",
    "DispenserSandboxMixin",
    "DropperSandboxMixin",
    "TntSandboxMixin"
  ],
```

Validate: `python3 -c "import json;json.load(open('src/main/resources/hogwartsmagic.mixins.json',encoding='utf-8'))"`. Expected: no output.

- [ ] **Step 4: The `LockSandbox` stub.** Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java` (Task 5 replaces the whole file):

```java
package com.hogwartsmagic.spell.support.lock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Reading piston-logic locks in the sandbox. Filled in by the sandbox driver task. */
public final class LockSandbox {
    private LockSandbox() {
    }

    static boolean busy() {
        return false;
    }

    static void drive(ServerLevel sandbox, long levelTickNanos) {
    }

    static void spilled(BlockPos pos) {
    }

    public static void trapFired(BlockPos pos) {
    }
}
```

- [ ] **Step 5: `SandboxWorld`.** Create `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxWorld.java`:

```java
package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.mixin.LevelBlockEntityTickInvoker;
import com.hogwartsmagic.mixin.ServerLevelSandboxAccessor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The private level the piston sandbox copies locks into: hogwartsmagic:lock_sandbox, an empty flat dimension
 * from the mod's datapack (data/hogwartsmagic/dimension/lock_sandbox.json), like Diagon Alley's. It ticks like
 * every dimension; LockSandbox adds extra ticks of it on its own clock (DerivedLevelDataClockMixin), so a copy
 * can run many ticks in one server tick. Nobody may enter it, no entity may join it, and it is not saved on
 * purpose. Server thread only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SandboxWorld {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "lock_sandbox"));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("hogwartsmagic_lock_sandbox",
            Comparator.comparingLong(ChunkPos::toLong));

    /** The sandbox level's own DerivedLevelData; DerivedLevelDataClockMixin answers its getGameTime with clock. */
    @Nullable
    public static volatile Object clockOwner;
    private static volatile long clock;
    /** Set after an unexpected error: every piston-logic lock is refused until the server restarts. */
    private static boolean broken;
    private static long tickStartNanos;
    private static final Set<UUID> EVICT = new HashSet<>();

    private SandboxWorld() {
    }

    public static long clock() {
        return clock;
    }

    /** The sandbox, or null if the datapack did not load it or it was switched off after an error. */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return broken ? null : server.getLevel(KEY);
    }

    public static boolean isSandbox(Level level) {
        return KEY.equals(level.dimension());
    }

    static void markBroken(Throwable error) {
        if (!broken) {
            LOGGER.error("Lock sandbox switched off until the server restarts", error);
        }
        broken = true;
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && isSandbox(level)) {
            clock = level.getGameTime(); // still the overworld's time: clockOwner is not set yet
            clockOwner = level.getLevelData();
            level.noSave = true;
            LOGGER.info("Lock sandbox ready");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clockOwner = null;
        broken = false;
        EVICT.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level) || !isSandbox(level)) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (level.tickRateManager().runsNormally()) {
                clock++; // the level's own tick that follows is one tick of every copy
            }
            if (LockSandbox.busy()) {
                level.resetEmptyTime(); // a level without players stops ticking block entities after 300 ticks
            }
            tickStartNanos = System.nanoTime();
        } else {
            LockSandbox.drive(level, System.nanoTime() - tickStartNanos);
        }
    }

    /**
     * One extra tick of the sandbox, in ServerLevel.tick's order and under its handlingTick flag (pistons read it
     * to tell a 0-tick pulse): the clock, scheduled block ticks, fluid ticks, block events, block entities. Not
     * the world border, weather, raids, random ticks, chunk upkeep or entities: no copy uses them.
     */
    static void virtualTick(ServerLevel level) {
        long now = ++clock;
        ServerLevelSandboxAccessor access = (ServerLevelSandboxAccessor) level;
        access.hogwartsmagic$setHandlingTick(true);
        try {
            level.getBlockTicks().tick(now, 65536, access::hogwartsmagic$tickBlock);
            level.getFluidTicks().tick(now, 65536, access::hogwartsmagic$tickFluid);
            access.hogwartsmagic$runBlockEvents();
        } finally {
            access.hogwartsmagic$setHandlingTick(false);
        }
        ((LevelBlockEntityTickInvoker) level).hogwartsmagic$tickBlockEntities();
    }

    /** Keeps a copy's chunk loaded and entity-ticking (ticket level 31, as a forced chunk), without saving it. */
    static void hold(ServerLevel level, ChunkPos pos) {
        level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
    }

    static void release(ServerLevel level, ChunkPos pos) {
        level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos);
    }

    /** Loaded, ticking and with its entities loaded: LevelTicks only runs ticks in such a chunk. */
    static boolean ready(ServerLevel level, ChunkPos pos) {
        long key = pos.toLong();
        return level.getChunkSource().getChunkNow(pos.x, pos.z) != null
                && level.getChunkSource().isPositionTicking(key) && level.areEntitiesLoaded(key);
    }

    /** Falling blocks, items, arrows, primed TNT: a copy that makes an entity is doing something it cannot copy. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !isSandbox(event.getLevel()) || event.getEntity() instanceof Player) {
            return;
        }
        event.setCanceled(true);
        LockSandbox.spilled(event.getEntity().blockPosition());
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (KEY.equals(event.getDimension())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (KEY.equals(event.getTo())) {
            EVICT.add(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (isSandbox(event.getEntity().level())) {
            EVICT.add(event.getEntity().getUUID());
        }
    }

    /** A player who got into the sandbox anyway (a command) is sent to the overworld spawn a tick later. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || EVICT.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (UUID id : List.copyOf(EVICT)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && isSandbox(player.level())) {
                player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
            }
        }
        EVICT.clear();
    }
}
```

(APIs checked with `javap` on the mapped 1.21 jar:
- `ServerChunkCache.addRegionTicket/removeRegionTicket(TicketType<T>, ChunkPos, int, T)`, `getChunkNow(int, int)` and `isPositionTicking(long)`;
- `ServerLevel.areEntitiesLoaded(long)`, `resetEmptyTime()`, `tickRateManager()` and the public `noSave` field;
- `TicketType.create(String, Comparator<T>)`;
- `ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)`;
- `EntityTravelToDimensionEvent` and `EntityJoinLevelEvent` are `@Cancelable`;
- `TickEvent.ServerTickEvent.getServer()`;
- Forge posts `LevelTickEvent` around `ServerLevel.tick` in `MinecraftServer.tickChildren` (`onPreLevelTick`/`onPostLevelTick`).)

- [ ] **Step 6: Cave Inimicum skips the sandbox.** In `ModEvents.java`, find:

```java
    public static void onPistonPost(net.minecraftforge.event.level.PistonEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
```

Replace with:

```java
    public static void onPistonPost(net.minecraftforge.event.level.PistonEvent.Post event) {
        // The piston sandbox moves pistons by the thousand and has no wards.
        if (!(event.getLevel() instanceof ServerLevel level)
                || com.hogwartsmagic.spell.support.lock.SandboxWorld.isSandbox(level)) {
            return;
        }
```

- [ ] **Step 7: Build and the safety greps.** Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, N + 31 tests green. Then run `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/support/lock/ src/main/java/com/hogwartsmagic/mixin/*.java`. Expected: nothing.

- [ ] **Step 8: Dedicated-server boot.** Run the CLOUD-OVERRIDES boot block. Expected:
  - `Done (` appears;
  - `grep -n "Lock sandbox ready" run/logs/latest.log` prints one line;
  - there is no `Exception`, no `ERROR` and no `Mixin` apply failure naming any of the 8 new mixins;
  - afterwards no server java is left.

- [ ] **Step 9: Commit.**

```bash
git add src/main/resources/data/hogwartsmagic/dimension/lock_sandbox.json src/main/java/com/hogwartsmagic/spell/support/lock/SandboxWorld.java src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java src/main/java/com/hogwartsmagic/mixin/DerivedLevelDataClockMixin.java src/main/java/com/hogwartsmagic/mixin/ServerLevelSandboxAccessor.java src/main/java/com/hogwartsmagic/mixin/LevelBlockEntityTickInvoker.java src/main/java/com/hogwartsmagic/mixin/RedstoneTorchTogglesAccessor.java src/main/java/com/hogwartsmagic/mixin/RedstoneTorchToggleAccessor.java src/main/java/com/hogwartsmagic/mixin/DispenserSandboxMixin.java src/main/java/com/hogwartsmagic/mixin/DropperSandboxMixin.java src/main/java/com/hogwartsmagic/mixin/TntSandboxMixin.java src/main/resources/hogwartsmagic.mixins.json src/main/java/com/hogwartsmagic/event/ModEvents.java
git commit -m "Add the lock sandbox dimension with its own clock, trap hooks and entry guards" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Copying a lock (tracer positions, snapshots, shared actions)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSnapshot.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/lock/LockActions.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`

**Consumes:** `SandboxRules`, `LockTimeline` (T1); `SandboxWorld.clock()` and the accessors (T3); `LockHints.TAG` (existing).
**Produces:** the pre-flight row T4.

No new unit tests: these classes are bound to Minecraft. The existing lock tests must stay green, and `schedule` must keep its timing, which `LockTimelineTest` pins.

- [ ] **Step 1: `TracedLock` keeps its positions.** In `CircuitTracer.java`, find:

```java
    public record TracedLock(LockCircuit circuit, List<InputSite> inputs, int pistons, int blocks) {
    }
```

Replace with:

```java
    /**
     * region: every traced block. doorPistons: the pistons near the struck block (the goals). logicPistons: the
     * pistons that move wiring (see pistonLogic); the piston sandbox does not count them as the door.
     */
    public record TracedLock(LockCircuit circuit, List<InputSite> inputs, int pistons, int blocks,
                             List<BlockPos> region, List<BlockPos> doorPistons, List<BlockPos> logicPistons) {
    }
```

Find:

```java
    private int forced;
    private int itemKeys;
```

Replace with:

```java
    private int forced;
    private int itemKeys;
    private final List<BlockPos> logic = new ArrayList<>();
```

Find:

```java
        return new TracedLock(t.b.build(), List.copyOf(t.sites), pistons.size(), t.region.size());
```

Replace with:

```java
        return new TracedLock(t.b.build(), List.copyOf(t.sites), pistons.size(), t.region.size(),
                List.copyOf(t.region.keySet()), List.copyOf(pistons), List.copyOf(t.logic));
```

Find:

```java
                if (block instanceof PistonBaseBlock && pistonLogic(pos, s, doorPistons.contains(pos))) {
                    b.markPistonLogic();
                }
```

Replace with:

```java
                if (block instanceof PistonBaseBlock && pistonLogic(pos, s, doorPistons.contains(pos))) {
                    b.markPistonLogic();
                    logic.add(pos);
                }
```

- [ ] **Step 2: `LockActions`.** Create `src/main/java/com/hogwartsmagic/spell/support/lock/LockActions.java`. The body of `operate` is moved verbatim from `PistonLockSpell`:

```java
package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.mixin.BasePressurePlateTypeAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Working one input of a lock, as a hand or a foot would. Alohomora's playback (PistonLockSpell) and the
 * piston sandbox's copies (LockSandbox) share it, so a copy is worked exactly as the door will be. Server
 * thread only.
 */
final class LockActions {
    private LockActions() {
    }

    /**
     * Flip a lever, or press a button, plate or sculk sensor, as a hand or a foot would. False if nothing
     * happened (a button already down, a plate or sensor that cannot be set off).
     */
    static boolean operate(ServerLevel level, BlockPos pos, BlockState state) {
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
}
```

- [ ] **Step 3: `PistonLockSpell` uses it.** In `PistonLockSpell.java`, delete the whole method that starts with:

```java
    /**
     * Flip a lever, or press a button, plate or sculk sensor, as a hand or a foot would. False if nothing
     * happened (a button already down, a plate or sensor that cannot be set off).
     */
    private static boolean operate(ServerLevel level, BlockPos pos, BlockState state) {
```

and ends with the `return false;` and `}` just before the `/lockinfo` javadoc (`     * /lockinfo: what the lock is and what the solver found, without touching anything. Two chat lines.`). Then find:

```java
                    acted = operate(level, pos, level.getBlockState(pos));
```

Replace with:

```java
                    acted = LockActions.operate(level, pos, level.getBlockState(pos));
```

Remove these imports, which only `operate` used:

```java
import com.hogwartsmagic.mixin.BasePressurePlateTypeAccessor;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
```

(`SculkSensorBlock`, `BlockState`, `SoundEvents` and `SoundSource` stay; they are used elsewhere in the file.)

- [ ] **Step 4: `schedule` on the shared timeline.** In `PistonLockSpell.schedule`, find:

```java
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
```

Replace with:

```java
        // The piston sandbox works its copies on this same timeline (LockTimeline), so the two never drift.
        boolean[] forced = new boolean[s.presses().length];
        for (int i = 0; i < forced.length; i++) {
            forced[i] = sites.get(s.presses()[i]).kind() == LockCircuit.FORCE;
        }
        int[] pressAt = LockTimeline.pressTicks(levels.size(), !together.isEmpty(), forced);
        for (int i = 0; i < levels.size(); i++) {
            boolean last = s.presses().length == 0 && together.isEmpty() && i == levels.size() - 1;
            STEPS.add(new Step(play, new int[] {levels.get(i)}, now, last, 0));
        }
        if (!together.isEmpty()) {
            STEPS.add(new Step(play, together.stream().mapToInt(Integer::intValue).toArray(),
                    now + LockTimeline.togetherAt(levels.size()), s.presses().length == 0, 0));
        }
        for (int i = 0; i < s.presses().length; i++) {
            STEPS.add(new Step(play, new int[] {s.presses()[i]}, now + pressAt[i], i == s.presses().length - 1, 0));
        }
    }
```

(Same ticks as before: levers at `now`; the subset at `now + 20` if there were levers, else `now`; the presses from there on, +80 after the subset or a forced press and +40 after any other.)

- [ ] **Step 5: `SandboxSnapshot`.** Create `src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSnapshot.java`:

```java
package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.mixin.RedstoneTorchToggleAccessor;
import com.hogwartsmagic.mixin.RedstoneTorchTogglesAccessor;
import com.hogwartsmagic.mixin.ServerLevelSandboxAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A frozen copy of a box of blocks: the states, the block entities (as NBT), and the scheduled ticks and block
 * events pending in it. It is written into the piston sandbox by setting chunk sections directly, the way a
 * chunk loads from disk: no block is placed, so nothing reacts to being copied. Never loads a chunk of the
 * world it reads. Server thread only.
 */
final class SandboxSnapshot {
    /** A scheduled tick: its delay from the capture; the list keeps the original order. */
    record Pending<T>(BlockPos pos, T type, long delay, TickPriority priority) {
    }

    /** What capture found: a snapshot, or the lang key suffix of why the box cannot be copied. */
    record Capture(@Nullable SandboxSnapshot snapshot, @Nullable String refusal) {
        static Capture refuse(String why) {
            return new Capture(null, why);
        }
    }

    /** Vanilla blocks a copy must not hold: commands, structure tools, sky readers, spawners, portals and the like. */
    private static final Set<Block> DENIED = Set.of(Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, Blocks.DAYLIGHT_DETECTOR,
            Blocks.SCULK_SHRIEKER, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.BEACON, Blocks.CONDUIT, Blocks.BEE_NEST, Blocks.BEEHIVE,
            Blocks.RESPAWN_ANCHOR);

    private final SandboxRules.Box box;
    private final BlockState[] states;
    private final Map<Integer, CompoundTag> blockEntities;
    private final List<Pending<Block>> blockTicks;
    private final List<Pending<Fluid>> fluidTicks;
    private final List<BlockEventData> events;

    private SandboxSnapshot(SandboxRules.Box box, BlockState[] states, Map<Integer, CompoundTag> blockEntities,
                            List<Pending<Block>> blockTicks, List<Pending<Fluid>> fluidTicks, List<BlockEventData> events) {
        this.box = box;
        this.states = states;
        this.blockEntities = blockEntities;
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
        this.events = events;
    }

    SandboxRules.Box box() {
        return box;
    }

    BlockState stateAt(BlockPos pos) {
        return states[box.index(pos.getX(), pos.getY(), pos.getZ())];
    }

    /** The box a lock's copy needs: every traced block and each traced piston's reach, plus the margin. */
    static SandboxRules.Box boxFor(ServerLevel level, CircuitTracer.TracedLock lock) {
        SandboxRules.Box box = null;
        for (BlockPos p : lock.region()) {
            box = box == null ? SandboxRules.Box.of(p.getX(), p.getY(), p.getZ()) : box.include(p.getX(), p.getY(), p.getZ());
            BlockState s = level.getBlockState(p); // traced, so loaded
            if (s.getBlock() instanceof PistonBaseBlock) {
                BlockPos reach = p.relative(s.getValue(PistonBaseBlock.FACING), SandboxRules.PUSH_REACH);
                box = box.include(reach.getX(), reach.getY(), reach.getZ());
            }
        }
        return box.grow(SandboxRules.MARGIN);
    }

    /**
     * Reads box from level. world: level is the real world, so a box that cannot be copied faithfully is refused
     * (flowing fluids, entities, something standing on a plate). Never loads a chunk.
     */
    static Capture capture(ServerLevel level, SandboxRules.Box box, boolean world) {
        long now = level.getGameTime();
        Map<Integer, CompoundTag> entities = new HashMap<>();
        List<ScheduledTick<Block>> blockTicks = new ArrayList<>();
        List<ScheduledTick<Fluid>> fluidTicks = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return Capture.refuse("too_far"); // never load a chunk
                }
                if (chunk.getBlockTicks() instanceof LevelChunkTicks<Block> bt) {
                    bt.getAll().filter(t -> inside(box, t.pos())).forEach(blockTicks::add);
                }
                if (chunk.getFluidTicks() instanceof LevelChunkTicks<Fluid> ft) {
                    ft.getAll().filter(t -> inside(box, t.pos())).forEach(fluidTicks::add);
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (!inside(box, p)) {
                        continue;
                    }
                    ResourceLocation type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                    if (type == null || !"minecraft".equals(type.getNamespace())) {
                        return Capture.refuse("sandbox_strange");
                    }
                    entities.put(box.index(p.getX(), p.getY(), p.getZ()), be.saveWithFullMetadata(level.registryAccess()));
                }
            }
        }
        if (world && !fluidTicks.isEmpty()) {
            return Capture.refuse("sandbox_fluid");
        }
        BlockState[] states = new BlockState[(int) box.volume()];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < states.length; i++) {
            cursor.set(box.x(i), box.y(i), box.z(i));
            BlockState s = level.getBlockState(cursor);
            states[i] = s;
            if (!"minecraft".equals(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace())
                    || DENIED.contains(s.getBlock())) {
                return Capture.refuse("sandbox_strange");
            }
            if (world) {
                FluidState fluid = s.getFluidState();
                if (!fluid.isEmpty() && !fluid.isSource()) {
                    return Capture.refuse("sandbox_fluid");
                }
                if (pressed(s)) {
                    return Capture.refuse("sandbox_crowded");
                }
            }
        }
        if (world) {
            AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
            if (!level.getEntities((Entity) null, area,
                    e -> !(e instanceof Player) && !e.getTags().contains(LockHints.TAG)).isEmpty()) {
                return Capture.refuse("sandbox_crowded");
            }
        }
        List<BlockEventData> events = new ArrayList<>();
        for (BlockEventData e : ((ServerLevelSandboxAccessor) level).hogwartsmagic$getBlockEvents()) {
            if (inside(box, e.pos())) {
                events.add(e);
            }
        }
        return new Capture(new SandboxSnapshot(box, states, entities, pending(blockTicks, now), pending(fluidTicks, now),
                events), null);
    }

    /**
     * Makes the box at (dx, 0, dz) in the sandbox exactly this snapshot. Changed blocks are set straight in their
     * chunk sections (no onPlace, no neighbour updates). Every block entity is reloaded from its NBT and any other
     * one in the box is removed. The box's ticks, block events and torch burnout memory become the snapshot's,
     * timed from the sandbox clock. The chunks must be loaded (LockSandbox holds tickets on them).
     */
    void write(ServerLevel sandbox, int dx, int dz) {
        for (int i = 0; i < states.length; i++) {
            int x = box.x(i) + dx;
            int y = box.y(i);
            int z = box.z(i) + dz;
            LevelChunkSection section = section(sandbox, x, y, z);
            if (section.getBlockState(x & 15, y & 15, z & 15) != states[i]) {
                section.setBlockState(x & 15, y & 15, z & 15, states[i]);
            }
        }
        SandboxRules.Box target = box.offset(dx, 0, dz);
        removeBlockEntities(sandbox, target);
        for (Map.Entry<Integer, CompoundTag> e : blockEntities.entrySet()) {
            int i = e.getKey();
            BlockPos pos = new BlockPos(box.x(i) + dx, box.y(i), box.z(i) + dz);
            BlockEntity be = BlockEntity.loadStatic(pos, states[i], e.getValue(), sandbox.registryAccess());
            if (be != null) {
                chunk(sandbox, pos.getX(), pos.getZ()).addAndRegisterBlockEntity(be);
            }
        }
        BoundingBox area = area(target);
        sandbox.getBlockTicks().clearArea(area);
        sandbox.getFluidTicks().clearArea(area);
        sandbox.clearBlockEvents(area);
        forgetTorchToggles(sandbox, target);
        long now = SandboxWorld.clock();
        for (Pending<Block> t : blockTicks) {
            sandbox.getBlockTicks().schedule(new ScheduledTick<>(t.type(), t.pos().offset(dx, 0, dz), now + t.delay(),
                    t.priority(), sandbox.nextSubTickCount()));
        }
        for (Pending<Fluid> t : fluidTicks) {
            sandbox.getFluidTicks().schedule(new ScheduledTick<>(t.type(), t.pos().offset(dx, 0, dz), now + t.delay(),
                    t.priority(), sandbox.nextSubTickCount()));
        }
        for (BlockEventData e : events) {
            sandbox.blockEvent(e.pos().offset(dx, 0, dz), e.block(), e.paramA(), e.paramB());
        }
    }

    /** True if the copy at (dx, 0, dz) changed any block of the box's outer layer: it reached past the copy. */
    boolean shellChanged(ServerLevel sandbox, int dx, int dz) {
        for (int i = 0; i < states.length; i++) {
            int x = box.x(i);
            int y = box.y(i);
            int z = box.z(i);
            if (!box.onShell(x, y, z)) {
                continue;
            }
            LevelChunkSection section = section(sandbox, x + dx, y, z + dz);
            if (section.getBlockState((x + dx) & 15, y & 15, (z + dz) & 15) != states[i]) {
                return true;
            }
        }
        return false;
    }

    /** True if every one of positions is loaded in level and holds the same block state as this snapshot. */
    boolean matches(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (!box.contains(p.getX(), p.getY(), p.getZ()) || !level.isLoaded(p)
                    || level.getBlockState(p) != states[box.index(p.getX(), p.getY(), p.getZ())]) {
                return false;
            }
        }
        return true;
    }

    /** Wipes a copy: air in every loaded position of target, no block entity, no tick, no block event. */
    static void clear(ServerLevel sandbox, SandboxRules.Box target) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = target.minY(); y <= target.maxY(); y++) {
            for (int z = target.minZ(); z <= target.maxZ(); z++) {
                for (int x = target.minX(); x <= target.maxX(); x++) {
                    LevelChunk chunk = sandbox.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    if (!section.getBlockState(x & 15, y & 15, z & 15).isAir()) {
                        section.setBlockState(x & 15, y & 15, z & 15, air);
                    }
                }
            }
        }
        removeBlockEntities(sandbox, target);
        BoundingBox area = area(target);
        sandbox.getBlockTicks().clearArea(area);
        sandbox.getFluidTicks().clearArea(area);
        sandbox.clearBlockEvents(area);
        forgetTorchToggles(sandbox, target);
    }

    /** Whatever a read cut short by a crash left in these chunks: no block entity and no tick keeps running. */
    static void clean(ServerLevel sandbox, List<ChunkPos> chunks) {
        for (ChunkPos c : chunks) {
            LevelChunk chunk = chunk(sandbox, c.getMinBlockX(), c.getMinBlockZ());
            for (BlockPos p : List.copyOf(chunk.getBlockEntitiesPos())) {
                chunk.removeBlockEntity(p);
            }
            BoundingBox area = new BoundingBox(c.getMinBlockX(), sandbox.getMinBuildHeight(), c.getMinBlockZ(),
                    c.getMaxBlockX(), sandbox.getMaxBuildHeight() - 1, c.getMaxBlockZ());
            sandbox.getBlockTicks().clearArea(area);
            sandbox.getFluidTicks().clearArea(area);
            sandbox.clearBlockEvents(area);
        }
    }

    private static boolean pressed(BlockState s) {
        Block block = s.getBlock();
        if (block instanceof BasePressurePlateBlock) {
            return CircuitTracer.isOn(s);
        }
        if (block instanceof TripWireBlock) {
            return s.getValue(TripWireBlock.POWERED);
        }
        if (block instanceof DetectorRailBlock) {
            return s.getValue(DetectorRailBlock.POWERED);
        }
        return false;
    }

    private static <T> List<Pending<T>> pending(List<ScheduledTick<T>> ticks, long now) {
        ticks.sort(Comparator.comparingLong(ScheduledTick::subTickOrder));
        List<Pending<T>> out = new ArrayList<>(ticks.size());
        for (ScheduledTick<T> t : ticks) {
            out.add(new Pending<>(t.pos(), t.type(), t.triggerTick() - now, t.priority()));
        }
        return out;
    }

    private static void removeBlockEntities(ServerLevel sandbox, SandboxRules.Box target) {
        for (int cx = target.minX() >> 4; cx <= target.maxX() >> 4; cx++) {
            for (int cz = target.minZ() >> 4; cz <= target.maxZ() >> 4; cz++) {
                LevelChunk chunk = sandbox.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos p : List.copyOf(chunk.getBlockEntitiesPos())) {
                    if (inside(target, p)) {
                        chunk.removeBlockEntity(p);
                    }
                }
            }
        }
    }

    private static void forgetTorchToggles(ServerLevel sandbox, SandboxRules.Box target) {
        List<RedstoneTorchBlock.Toggle> toggles = RedstoneTorchTogglesAccessor.hogwartsmagic$getRecentToggles().get(sandbox);
        if (toggles != null) {
            toggles.removeIf(t -> inside(target, ((RedstoneTorchToggleAccessor) t).hogwartsmagic$getPos()));
        }
    }

    private static LevelChunk chunk(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) {
            throw new IllegalStateException("lock sandbox chunk not loaded at " + x + ", " + z);
        }
        return chunk;
    }

    private static LevelChunkSection section(ServerLevel level, int x, int y, int z) {
        LevelChunk chunk = chunk(level, x, z);
        return chunk.getSection(chunk.getSectionIndex(y));
    }

    private static BoundingBox area(SandboxRules.Box b) {
        return new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    private static boolean inside(SandboxRules.Box b, BlockPos p) {
        return b.contains(p.getX(), p.getY(), p.getZ());
    }
}
```

(APIs checked with `javap` on the mapped 1.21 jar:
- `LevelChunk`:
  - `getBlockTicks()` and `getFluidTicks()` return `TickContainerAccess<T>`; a loaded `LevelChunk`'s is a `LevelChunkTicks<T>`, with a public `getAll()` stream;
  - `getBlockEntities()`, `addAndRegisterBlockEntity(BlockEntity)` (it calls `setBlockEntity`, `addGameEventListener`, `updateBlockEntityTicker` and `onLoad`) and `removeBlockEntity(BlockPos)`;
- `ChunkAccess`: `getBlockEntitiesPos()`, `getSection(int)` and `getSectionIndex(int)`;
- `LevelChunkSection.getBlockState/setBlockState(int, int, int[, BlockState])`;
- `LevelTicks`: `clearArea(BoundingBox)` and `schedule(ScheduledTick)`;
- `ScheduledTick(T, BlockPos, long, TickPriority, long)`;
- `ServerLevel`: `clearBlockEvents(BoundingBox)`, `blockEvent(BlockPos, Block, int, int)` and `nextSubTickCount()`;
- `BlockEntity`: `loadStatic(BlockPos, BlockState, CompoundTag, HolderLookup.Provider)` and `saveWithFullMetadata(HolderLookup.Provider)`.)

- [ ] **Step 6: Build, tests and boot.**
  - Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, N + 31 tests green, every existing `Lock*Test` unchanged.
  - Run `grep -n "operate(" src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`. Expected: the `LockActions.operate` call only.
  - Run the CLOUD-OVERRIDES boot block. Expected: `Done (`, with no `Exception` or `ERROR`.

- [ ] **Step 7: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/CircuitTracer.java src/main/java/com/hogwartsmagic/spell/support/lock/SandboxSnapshot.java src/main/java/com/hogwartsmagic/spell/support/lock/LockActions.java src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java
git commit -m "Copy a lock box exactly: traced positions, snapshots, shared lock actions and timeline" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The sandbox driver (`LockSandbox`)

**Files:**
- Modify (replace the Task 3 stub entirely): `src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java`

**Consumes:** T1, T2, T3, T4. **Produces:** the pre-flight row T5.

The behaviour, all on the server thread:
1. `submit` queues a `Run` and hands back its `Handle`: a `CompletableFuture<LockSolver.Solution>`, a `LockSolver.Progress`, a refusal key and the settled template.
2. `drive` runs at the END of the sandbox's level tick:
   - it ends the active run if its future is done (cancelled by a release, a lapsed hold or a deadline);
   - it activates the next run;
   - it runs the active one.
3. **PREPARE.** Up to 8 copies are placed. Copy 0 sits at the door's coordinates; later copies that fall outside the sandbox's world border are skipped. Region tickets are taken on their chunks, and the run waits until every chunk is ready (at most 200 server ticks). Then the chunks are cleaned, the live snapshot is written into copy 0, and the baseline starts.
4. **BASELINE.** Copy 0 runs until it is quiet with steady doors (at most 300 ticks). A trap gives UNSTABLE; a spill gives `sandbox_crowded`. The settled box is captured as the template, and the template's door states become the baseline.
5. **SEARCH.** After every tick of the sandbox (its own level tick, then each extra tick):
   - each running copy advances `t`, is observed (doors, quiet), and gets the actions due at its `t`;
   - each idle copy is reset from the template and starts the next attempt, with its tick-0 actions;
   - extra ticks run while `SandboxRules.anotherTick` allows.
   - An attempt finishes when the copy is quiet and steady after its last action, scoring its best door count. It scores −1 on a trap, a spill, an input that will not work, or no settle within 300 ticks.
   - A finished copy whose outer layer changed refuses the whole read with `sandbox_too_big`.
6. The run ends when `SandboxSearch.complete()`, giving SOLVED or NO_SOLUTION. It also ends when the copy-tick budget runs out (the best so far if any moved, else OUT_OF_TIME), or after 3 heavy level ticks in a row (OUT_OF_TIME). Every copy is wiped and its tickets released.
7. Any `RuntimeException`:
   - completes the futures exceptionally, which `finishJobs` reports as `no_solution` and `ERROR`;
   - wipes what it can;
   - switches the sandbox off until restart (`SandboxWorld.markBroken`).

- [ ] **Step 1: Replace `LockSandbox.java`.** The Task 3 stub, quoted in full:

```java
/** Reading piston-logic locks in the sandbox. Filled in by the sandbox driver task. */
public final class LockSandbox {
    private LockSandbox() {
    }

    static boolean busy() {
        return false;
    }

    static void drive(ServerLevel sandbox, long levelTickNanos) {
    }

    static void spilled(BlockPos pos) {
    }

    public static void trapFired(BlockPos pos) {
    }
}
```

Replace the whole file with:

```java
package com.hogwartsmagic.spell.support.lock;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.mixin.ServerLevelSandboxAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Reading a piston-logic lock in the sandbox (Alohomora and Revelio Master, /lockinfo 4). The lock's box is copied
 * into SandboxWorld up to SandboxRules.MAX_SLOTS times. Each copy runs one attempt of an AttemptPlan at a time,
 * worked with LockActions on LockTimeline exactly as Alohomora's playback would, and the door pistons are
 * watched. One lock at a time; the rest wait in a queue. All on the server thread: in the sandbox's own level
 * tick plus as many extra ticks as fit SandboxRules.TICK_BUDGET_NANOS.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockSandbox {
    private static final int PREPARE = 0;
    private static final int BASELINE = 1;
    private static final int SEARCH = 2;

    /** What PistonLockSpell keeps for a sandbox read. Server thread only. */
    static final class Handle {
        final CompletableFuture<LockSolver.Solution> result = new CompletableFuture<>();
        final LockSolver.Progress progress = new LockSolver.Progress();
        /** The traced blocks, compared with the template before the answer is used. */
        final List<BlockPos> watched;
        /** Lang key suffix when the sandbox gave up on the lock (the solution is then NO_SOLUTION). */
        @Nullable
        String refusal;
        /** The settled copy of the lock, at the lock's own coordinates. */
        @Nullable
        SandboxSnapshot template;
        int copies;
        long ticks;

        Handle(List<BlockPos> watched) {
            this.watched = watched;
        }

        /** The real lock still stands as its copy settled: the answer still applies. */
        boolean stillAsCopied(ServerLevel level) {
            return template != null && template.matches(level, watched);
        }

        /** For /lockinfo. */
        String stats() {
            return copies + " copies, " + progress.attempts() + " attempts, " + ticks + " sandbox ticks"
                    + (refusal == null ? "" : ", " + refusal);
        }
    }

    /** One copy of the lock in the sandbox. */
    private static final class Slot {
        final int dx;
        final int dz;
        final SandboxRules.Box box;
        final List<ChunkPos> chunks = new ArrayList<>();
        final BlockState[] last;
        final int[] stable;
        /** Tick offset and circuit input of every action of the attempt. */
        final List<int[]> actions = new ArrayList<>();
        boolean written;
        boolean baseline;
        @Nullable
        AttemptPlan.Attempt attempt;
        int t;
        int lastAction;
        int quiet;
        int best;
        boolean steady;
        boolean failed;
        boolean trap;
        boolean spill;

        Slot(int m, SandboxRules.Box source, int doors) {
            dx = SandboxRules.slotDx(m);
            dz = SandboxRules.slotDz(m);
            box = source.offset(dx, 0, dz);
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
            last = new BlockState[doors];
            stable = new int[doors];
        }

        boolean running() {
            return baseline || attempt != null;
        }

        void startBaseline() {
            forget();
            baseline = true;
        }

        /** The doors start as the settled copy had them; the actions follow Alohomora's playback timeline. */
        void start(AttemptPlan.Attempt next, BlockState[] baseDoors) {
            forget();
            attempt = next;
            for (int i = 0; i < last.length; i++) {
                last[i] = baseDoors[i];
                stable[i] = SandboxRules.DOOR_STABLE_TICKS;
            }
            steady = true;
            for (int input : next.set()) {
                actions.add(new int[] {0, input});
            }
            int[] at = LockTimeline.pressTicks(next.set().length, false, new boolean[next.presses().length]);
            for (int i = 0; i < at.length; i++) {
                actions.add(new int[] {at[i], next.presses()[i]});
            }
            lastAction = actions.isEmpty() ? 0 : actions.get(actions.size() - 1)[0];
        }

        private void forget() {
            baseline = false;
            attempt = null;
            t = 0;
            lastAction = 0;
            quiet = 0;
            best = 0;
            steady = false;
            failed = false;
            trap = false;
            spill = false;
            actions.clear();
            for (int i = 0; i < last.length; i++) {
                last[i] = null;
                stable[i] = 0;
            }
        }
    }

    /** One lock being read. */
    private static final class Run {
        final Handle handle;
        final CircuitTracer.TracedLock lock;
        final SandboxSnapshot copy;
        final List<BlockPos> doors;
        final List<AttemptPlan.Attempt> plan;
        final SandboxSearch search;
        final List<Slot> slots = new ArrayList<>();
        int phase = PREPARE;
        int prepareTicks;
        int heavy;
        long work;
        boolean ended;
        @Nullable
        SandboxSnapshot template;
        @Nullable
        BlockState[] baseDoors;

        Run(CircuitTracer.TracedLock lock, SandboxSnapshot copy, List<BlockPos> doors, List<AttemptPlan.Attempt> plan) {
            this.handle = new Handle(lock.region());
            this.lock = lock;
            this.copy = copy;
            this.doors = doors;
            this.plan = plan;
            this.search = new SandboxSearch(plan, doors.size());
        }

        /** Copies lie 961 blocks apart along x, so x alone tells which one a position belongs to. */
        @Nullable
        Slot slotNear(int x) {
            for (Slot s : slots) {
                if (Math.abs(x - (s.box.minX() + s.box.maxX()) / 2) < SandboxRules.SLOT_STEP / 2) {
                    return s;
                }
            }
            return null;
        }
    }

    private static final Deque<Run> QUEUE = new ArrayDeque<>();
    @Nullable
    private static Run active;

    private LockSandbox() {
    }

    static boolean busy() {
        return active != null || !QUEUE.isEmpty();
    }

    /**
     * Queues a read. lock: traced without forcing or item keys; copy: the live box; doors: the door pistons
     * (logic pistons left out); plan: the attempts, in order. The future completes on the server thread.
     */
    static Handle submit(CircuitTracer.TracedLock lock, SandboxSnapshot copy, List<BlockPos> doors,
                         List<AttemptPlan.Attempt> plan) {
        Run run = new Run(lock, copy, doors, plan);
        QUEUE.add(run);
        return run.handle;
    }

    /** A dispenser, dropper or TNT went off in the sandbox (the trap mixins). */
    public static void trapFired(BlockPos pos) {
        Slot slot = active == null ? null : active.slotNear(pos.getX());
        if (slot != null) {
            slot.trap = true;
        }
    }

    /** Something tried to make an entity in the sandbox (SandboxWorld.onJoin). */
    static void spilled(BlockPos pos) {
        Slot slot = active == null ? null : active.slotNear(pos.getX());
        if (slot != null) {
            slot.spill = true;
        }
    }

    /** At the END of the sandbox's level tick; levelTickNanos: what that tick cost. */
    static void drive(ServerLevel sandbox, long levelTickNanos) {
        long start = System.nanoTime();
        try {
            if (active != null && active.handle.result.isDone()) {
                end(sandbox, active); // let go of, out of time, or the server is stopping
            }
            QUEUE.removeIf(r -> r.handle.result.isDone());
            if (active == null && !QUEUE.isEmpty()) {
                active = QUEUE.poll();
                begin(sandbox, active);
            }
            Run run = active;
            if (run == null || !sandbox.tickRateManager().runsNormally()) {
                return;
            }
            run.heavy = levelTickNanos > SandboxRules.HEAVY_TICK_NANOS ? run.heavy + 1 : 0;
            if (run.heavy >= SandboxRules.HEAVY_TICKS_IN_A_ROW) {
                give(sandbox, run, LockSolver.Status.OUT_OF_TIME, null); // the copies cost the server too much
                return;
            }
            step(sandbox, run, levelTickNanos, start);
        } catch (RuntimeException e) {
            fail(sandbox, e);
        }
    }

    private static void begin(ServerLevel sandbox, Run run) {
        run.handle.progress.start(); // the hold's cap counts from here
        int copies = Math.min(SandboxRules.MAX_SLOTS, Math.max(1, run.plan.size()));
        for (int m = 0; m < copies; m++) {
            Slot slot = new Slot(m, run.copy.box(), run.doors.size());
            SandboxRules.Box b = slot.box;
            if (!sandbox.getWorldBorder().isWithinBounds(
                    new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1))) {
                break; // copy 0 is where the door is; a later copy may fall outside the border
            }
            run.slots.add(slot);
            for (ChunkPos c : slot.chunks) {
                SandboxWorld.hold(sandbox, c);
            }
        }
        run.handle.copies = run.slots.size();
        if (run.slots.isEmpty()) {
            give(sandbox, run, LockSolver.Status.NO_SOLUTION, "sandbox_too_big");
        }
    }

    private static void step(ServerLevel sandbox, Run run, long levelTickNanos, long start) {
        if (run.phase == PREPARE) {
            prepare(sandbox, run);
            return;
        }
        afterTick(sandbox, run); // the sandbox's own level tick just ran: a tick of every running copy
        int extra = 0;
        long lastTick = levelTickNanos;
        while (active == run) {
            dispatch(sandbox, run);
            if (active != run) {
                return;
            }
            long spent = levelTickNanos + (System.nanoTime() - start);
            if (!SandboxRules.anotherTick(extra, spent, lastTick, sandbox.getServer().getAverageTickTimeNanos())) {
                return;
            }
            long tickStart = System.nanoTime();
            SandboxWorld.virtualTick(sandbox);
            lastTick = System.nanoTime() - tickStart;
            extra++;
            afterTick(sandbox, run);
        }
    }

    private static void prepare(ServerLevel sandbox, Run run) {
        for (Slot slot : run.slots) {
            for (ChunkPos c : slot.chunks) {
                if (!SandboxWorld.ready(sandbox, c)) {
                    if (++run.prepareTicks > SandboxRules.PREPARE_TIMEOUT_TICKS) {
                        give(sandbox, run, LockSolver.Status.OUT_OF_TIME, null);
                    }
                    return;
                }
            }
        }
        for (Slot slot : run.slots) {
            SandboxSnapshot.clean(sandbox, slot.chunks);
        }
        Slot first = run.slots.get(0);
        run.copy.write(sandbox, first.dx, first.dz); // the live lock, mid-motion if it was
        first.written = true;
        first.startBaseline();
        run.phase = BASELINE;
    }

    private static void afterTick(ServerLevel sandbox, Run run) {
        run.handle.ticks++;
        for (Slot slot : run.slots) {
            if (!slot.running()) {
                continue;
            }
            run.work++;
            slot.t++;
            observe(sandbox, run, slot);
            if (run.phase == BASELINE) {
                baseline(sandbox, run, slot);
            } else {
                attempt(sandbox, run, slot);
            }
            if (active != run) {
                return;
            }
        }
        if (run.phase == SEARCH && run.work > SandboxRules.SLOT_TICK_BUDGET) {
            finish(sandbox, run, true);
        }
    }

    /** Door pistons: how many differ from the settled copy, once all have held still; and whether the copy is quiet. */
    private static void observe(ServerLevel sandbox, Run run, Slot slot) {
        boolean steady = true;
        int moved = 0;
        for (int i = 0; i < run.doors.size(); i++) {
            BlockState now = sandbox.getBlockState(run.doors.get(i).offset(slot.dx, 0, slot.dz));
            if (now == slot.last[i]) {
                slot.stable[i]++;
            } else {
                slot.stable[i] = 0;
                slot.last[i] = now;
            }
            if (now.is(Blocks.MOVING_PISTON) || slot.stable[i] < SandboxRules.DOOR_STABLE_TICKS) {
                steady = false;
            }
            if (run.baseDoors != null && now != run.baseDoors[i]) {
                moved++;
            }
        }
        slot.steady = steady;
        if (steady && run.baseDoors != null) {
            slot.best = Math.max(slot.best, moved);
        }
        slot.quiet = quiet(sandbox, slot) ? slot.quiet + 1 : 0;
    }

    /** Nothing scheduled, queued or moving inside the copy's box. */
    private static boolean quiet(ServerLevel sandbox, Slot slot) {
        SandboxRules.Box b = slot.box;
        for (ChunkPos c : slot.chunks) {
            LevelChunk chunk = sandbox.getChunkSource().getChunkNow(c.x, c.z);
            if (chunk == null) {
                return false;
            }
            if (chunk.getBlockTicks() instanceof LevelChunkTicks<Block> bt && bt.getAll().anyMatch(t -> inside(b, t.pos()))) {
                return false;
            }
            if (chunk.getFluidTicks() instanceof LevelChunkTicks<Fluid> ft && ft.getAll().anyMatch(t -> inside(b, t.pos()))) {
                return false;
            }
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof PistonMovingBlockEntity && inside(b, be.getBlockPos())) {
                    return false;
                }
            }
        }
        for (BlockEventData e : ((ServerLevelSandboxAccessor) sandbox).hogwartsmagic$getBlockEvents()) {
            if (inside(b, e.pos())) {
                return false;
            }
        }
        return true;
    }

    private static void baseline(ServerLevel sandbox, Run run, Slot slot) {
        if (slot.trap) {
            give(sandbox, run, LockSolver.Status.UNSTABLE, null); // a trap fires with nobody touching the lock
            return;
        }
        if (slot.spill) {
            give(sandbox, run, LockSolver.Status.NO_SOLUTION, "sandbox_crowded");
            return;
        }
        if (slot.quiet >= SandboxRules.QUIET_TICKS && slot.steady) {
            // Copy 0 sits at the lock's own coordinates, so its box is the lock's box.
            SandboxSnapshot.Capture settled = SandboxSnapshot.capture(sandbox, slot.box, false);
            if (settled.snapshot() == null) {
                give(sandbox, run, LockSolver.Status.NO_SOLUTION, settled.refusal());
                return;
            }
            run.template = settled.snapshot();
            run.handle.template = run.template;
            run.baseDoors = new BlockState[run.doors.size()];
            for (int i = 0; i < run.baseDoors.length; i++) {
                run.baseDoors[i] = run.template.stateAt(run.doors.get(i));
            }
            slot.baseline = false;
            run.phase = SEARCH;
            return;
        }
        if (slot.t > SandboxRules.SETTLE_CAP_TICKS) {
            give(sandbox, run, LockSolver.Status.UNSTABLE, null); // the lock never stops ticking on its own
        }
    }

    private static void attempt(ServerLevel sandbox, Run run, Slot slot) {
        if (!slot.failed && !slot.trap && !slot.spill) {
            act(sandbox, run, slot); // the inputs worked at this tick
        }
        if (slot.failed || slot.trap || slot.spill) {
            done(sandbox, run, slot, -1);
            return;
        }
        if (slot.t <= slot.lastAction) {
            return;
        }
        if (slot.quiet >= SandboxRules.QUIET_TICKS && slot.steady) {
            done(sandbox, run, slot, slot.best);
        } else if (slot.t - slot.lastAction > SandboxRules.SETTLE_CAP_TICKS) {
            done(sandbox, run, slot, -1); // never settles
        }
    }

    /** The actions due at the copy's tick, between two ticks of the sandbox, as playback acts between world ticks. */
    private static void act(ServerLevel sandbox, Run run, Slot slot) {
        for (int[] action : slot.actions) {
            if (action[0] != slot.t) {
                continue;
            }
            CircuitTracer.InputSite site = run.lock.inputs().get(action[1]);
            BlockPos pos = site.pos().offset(slot.dx, 0, slot.dz);
            if (!LockActions.operate(sandbox, pos, sandbox.getBlockState(pos))) {
                slot.failed = true; // a button still down, a sensor not ready: playback would not get through either
                return;
            }
            slot.quiet = 0;
            slot.steady = false;
        }
    }

    private static void done(ServerLevel sandbox, Run run, Slot slot, int moved) {
        AttemptPlan.Attempt finished = slot.attempt;
        slot.attempt = null;
        if (run.template.shellChanged(sandbox, slot.dx, slot.dz)) {
            give(sandbox, run, LockSolver.Status.NO_SOLUTION, "sandbox_too_big"); // it reached past the copy
            return;
        }
        if (moved < 0) {
            run.template.write(sandbox, slot.dx, slot.dz); // stop whatever the failed attempt left running
        }
        run.search.finish(finished.index(), moved);
        run.handle.progress.attempted(run.work);
    }

    /** Every idle copy is reset to the settled lock and starts the next attempt, then its tick-0 actions run. */
    private static void dispatch(ServerLevel sandbox, Run run) {
        if (run.phase != SEARCH) {
            return;
        }
        for (Slot slot : run.slots) {
            if (slot.running()) {
                continue;
            }
            AttemptPlan.Attempt next = run.search.next();
            if (next == null) {
                break;
            }
            run.template.write(sandbox, slot.dx, slot.dz);
            slot.written = true;
            slot.start(next, run.baseDoors);
            act(sandbox, run, slot);
        }
        if (run.search.complete()) {
            finish(sandbox, run, false);
        }
    }

    /** The answer LockSolver would have kept: the lowest-index attempt that moved the most door pistons. */
    private static void finish(ServerLevel sandbox, Run run, boolean outOfBudget) {
        int best = run.search.best();
        LockSolver.Solution s;
        if (best >= 0) {
            AttemptPlan.Attempt a = run.plan.get(best);
            s = new LockSolver.Solution(LockSolver.Status.SOLVED, a.set(), a.to(), a.presses(), run.search.moved(best),
                    run.doors.size(), run.work);
        } else {
            s = LockSolver.Solution.none(outOfBudget ? LockSolver.Status.OUT_OF_TIME : LockSolver.Status.NO_SOLUTION,
                    run.doors.size(), run.work);
        }
        run.handle.result.complete(s);
        end(sandbox, run);
    }

    private static void give(ServerLevel sandbox, Run run, LockSolver.Status status, @Nullable String refusal) {
        run.handle.refusal = refusal;
        run.handle.result.complete(LockSolver.Solution.none(status, run.doors.size(), run.work));
        end(sandbox, run);
    }

    /** Wipes every copy and lets its chunks go. */
    private static void end(ServerLevel sandbox, Run run) {
        if (active == run) {
            active = null;
        }
        if (run.ended) {
            return;
        }
        run.ended = true;
        for (Slot slot : run.slots) {
            if (slot.written) {
                SandboxSnapshot.clear(sandbox, slot.box);
            }
            for (ChunkPos c : slot.chunks) {
                SandboxWorld.release(sandbox, c);
            }
        }
    }

    private static void fail(ServerLevel sandbox, RuntimeException e) {
        SandboxWorld.markBroken(e);
        Run run = active;
        active = null;
        if (run != null) {
            run.handle.result.completeExceptionally(e);
            try {
                end(sandbox, run);
            } catch (RuntimeException ignored) {
                // the sandbox is off from now on; its chunks are cleaned when a read next starts
            }
        }
        QUEUE.forEach(r -> r.handle.result.completeExceptionally(e));
        QUEUE.clear();
    }

    private static boolean inside(SandboxRules.Box b, BlockPos p) {
        return b.contains(p.getX(), p.getY(), p.getZ());
    }

    /** Before the final save: wipe the copies, so nothing of a lock is left in the sandbox's chunks. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel sandbox = event.getServer().getLevel(SandboxWorld.KEY);
        Run run = active;
        active = null;
        if (run != null) {
            run.handle.result.cancel(true);
            if (sandbox != null) {
                end(sandbox, run);
            }
        }
        QUEUE.forEach(r -> r.handle.result.cancel(true));
        QUEUE.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        active = null;
        QUEUE.clear();
    }
}
```

(`LockSolver.Solution.none`, `Progress.start()` and `Progress.attempted(long)` are package-private; `LockSandbox` is in the same package. `MinecraftServer.getAverageTickTimeNanos()` and `WorldBorder.isWithinBounds(AABB)` were checked with `javap`.)

- [ ] **Step 2: Build, greps and boot.**
  - Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, N + 31 tests green.
  - Run `grep -n "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java`. Expected: nothing.
  - Run the CLOUD-OVERRIDES boot block. Expected: `Done (`, `Lock sandbox ready`, and no `Exception` or `ERROR`.

- [ ] **Step 3: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/LockSandbox.java
git commit -m "Run piston-logic locks in sandbox copies on a time budget and keep the solver answer" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The hybrid in `PistonLockSpell`, the lines and `/lockinfo`

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`
- Modify: `src/main/java/com/hogwartsmagic/command/LockInfoCommand.java`

**Consumes:** everything above. The text quoted below is HEAD f24118f as left by Task 4: Task 4 only touched `operate`, its imports and `schedule`.

- [ ] **Step 1: Imports.** In `PistonLockSpell.java`, find:

```java
import java.util.List;
import java.util.ListIterator;
```

Replace with:

```java
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
```

- [ ] **Step 2: `Job` carries the sandbox read.** Find:

```java
    private record Job(UUID caster, ResourceKey<Level> dimension, BlockPos door, CircuitTracer.TracedLock lock, int rank,
                       Use use, Future<LockSolver.Solution> result, InspectClock clock, LockSolver.Progress progress,
                       long planned, long budget, @Nullable Hold hold) {
    }
```

Replace with:

```java
    private record Job(UUID caster, ResourceKey<Level> dimension, BlockPos door, CircuitTracer.TracedLock lock, int rank,
                       Use use, Future<LockSolver.Solution> result, InspectClock clock, LockSolver.Progress progress,
                       long planned, long budget, @Nullable Hold hold, @Nullable LockSandbox.Handle sandbox) {
        // sandbox: a lock that moves blocks with pistons, read in the sandbox (Master); null for the solver
    }
```

- [ ] **Step 3: The branch in `begin`.** Find:

```java
        if (lock.circuit().pistonLogic()) {
            tell(caster, "piston_logic", ChatFormatting.GOLD); // pistons that move wiring are not modelled
            inspectLine(caster, use, "PISTON_LOGIC");
            return Begin.REFUSED;
        }
```

Replace with:

```java
        if (lock.circuit().pistonLogic()) {
            if (!SandboxRules.eligible(rank)) {
                tell(caster, "piston_logic", ChatFormatting.GOLD); // the solver does not model pistons that move wiring
                inspectLine(caster, use, "PISTON_LOGIC");
                return Begin.REFUSED;
            }
            return beginSandbox(level, hit, caster, rank, use); // Master: run copies of the lock instead
        }
```

Find:

```java
        long now = level.getGameTime();
        Hold hold = null;
        if (use != Use.INSPECT) {
            // Held from the press (or the last heartbeat); a caster who let go before the beam landed is RELEASED.
            Long seen = HELD.get(id);
            hold = new Hold(use == Use.REVEAL ? SpellType.REVELIO : SpellType.ALOHOMORA, seen == null ? now : seen);
        }
        JOBS.add(new Job(id, level.dimension(), hit.immutable(), lock, rank, use, result,
                new InspectClock(now + QUEUED_CEILING_TICKS), progress, LockSolver.plannedAttempts(circuit, maxInputs, maxPresses),
                budget, hold));
        if (hold != null) {
            sendState(caster, true);
        }
        return Begin.STARTED;
    }
```

Replace with:

```java
        long now = level.getGameTime();
        Hold hold = holdFor(id, use, now);
        JOBS.add(new Job(id, level.dimension(), hit.immutable(), lock, rank, use, result,
                new InspectClock(now + QUEUED_CEILING_TICKS), progress, LockSolver.plannedAttempts(circuit, maxInputs, maxPresses),
                budget, hold, null));
        if (hold != null) {
            sendState(caster, true);
        }
        return Begin.STARTED;
    }

    /** Held from the press (or the last heartbeat); a caster who let go before the beam landed is RELEASED. */
    @Nullable
    private static Hold holdFor(UUID id, Use use, long now) {
        if (use == Use.INSPECT) {
            return null;
        }
        Long seen = HELD.get(id);
        return new Hold(use == Use.REVEAL ? SpellType.REVELIO : SpellType.ALOHOMORA, seen == null ? now : seen);
    }

    /**
     * Master, a lock that moves blocks with pistons: retraced without forcing or item keys, copied as it stands
     * and read in the sandbox (LockSandbox). Refused with a line (and a /lockinfo word) when it cannot be copied.
     */
    private static Begin beginSandbox(ServerLevel level, BlockPos hit, ServerPlayer caster, int rank, Use use) {
        UUID id = caster.getUUID();
        ServerLevel sandbox = SandboxWorld.level(level.getServer());
        if (sandbox == null || SandboxWorld.isSandbox(level)) {
            return refuse(caster, use, "sandbox_missing");
        }
        CircuitTracer.TracedLock lock;
        ForcedSignals.TRACING = true;
        try {
            lock = CircuitTracer.trace(level, hit, LockRules.traceBlocks(rank), new CircuitTracer.Mode(false, false));
        } finally {
            ForcedSignals.TRACING = false;
        }
        if (lock == null) {
            return Begin.NO_PISTON;
        }
        LockCircuit circuit = lock.circuit();
        if (circuit.truncated()) {
            return refuse(caster, use, "too_far");
        }
        if (circuit.nonForcedInputCount() > SandboxRules.MAX_INPUTS) {
            caster.displayClientMessage(Component.translatable("message.hogwartsmagic.lock.too_big",
                    circuit.nonForcedInputCount(), SandboxRules.MAX_INPUTS).withStyle(ChatFormatting.GOLD), true);
            inspectLine(caster, use, "TOO_MANY_INPUTS");
            return Begin.REFUSED;
        }
        List<BlockPos> doors = new ArrayList<>(lock.doorPistons());
        doors.removeAll(lock.logicPistons()); // a piston that pushes wiring is part of the lock, not the door
        if (doors.isEmpty()) {
            return refuse(caster, use, "no_solution");
        }
        SandboxRules.Box box = SandboxSnapshot.boxFor(level, lock);
        if (!SandboxRules.fits(box) || !SandboxRules.heightFits(box, level.getMinBuildHeight(), level.getMaxBuildHeight(),
                sandbox.getMinBuildHeight(), sandbox.getMaxBuildHeight())) {
            return refuse(caster, use, "sandbox_too_big");
        }
        SandboxSnapshot.Capture capture = SandboxSnapshot.capture(level, box, true);
        if (capture.snapshot() == null) {
            return refuse(caster, use, capture.refusal());
        }
        List<AttemptPlan.Attempt> plan = sandboxPlan(lock);
        if (plan == null) {
            return refuse(caster, use, "sandbox_too_big");
        }
        if (plan.isEmpty()) {
            return refuse(caster, use, "no_solution");
        }
        LockSandbox.Handle handle = LockSandbox.submit(lock, capture.snapshot(), doors, plan);
        long now = level.getGameTime();
        Hold hold = holdFor(id, use, now);
        JOBS.add(new Job(id, level.dimension(), hit.immutable(), lock, rank, use, handle.result,
                new InspectClock(now + QUEUED_CEILING_TICKS), handle.progress, plan.size(), SandboxRules.SLOT_TICK_BUDGET,
                hold, handle));
        if (hold != null) {
            sendState(caster, true);
        }
        return Begin.STARTED;
    }

    /** The sandbox's attempts: every lever to its other state and every button, plate or sensor, in solver order. */
    @Nullable
    private static List<AttemptPlan.Attempt> sandboxPlan(CircuitTracer.TracedLock lock) {
        List<Integer> levers = new ArrayList<>();
        List<int[]> alternatives = new ArrayList<>();
        List<Integer> pulses = new ArrayList<>();
        for (int i = 0; i < lock.inputs().size(); i++) {
            CircuitTracer.InputSite site = lock.inputs().get(i);
            if (site.kind() == LockCircuit.LEVER) {
                levers.add(i);
                alternatives.add(new int[] {site.on() ? 0 : 15});
            } else if (site.kind() == LockCircuit.PULSE) {
                pulses.add(i);
            }
        }
        return AttemptPlan.of(levers.stream().mapToInt(Integer::intValue).toArray(), alternatives.toArray(new int[0][]),
                pulses.stream().mapToInt(Integer::intValue).toArray(), SandboxRules.MAX_PRESSES);
    }

    /** Tells the caster why the sandbox will not read this lock; /lockinfo also gets a word (SANDBOX_TOO_BIG...). */
    private static Begin refuse(ServerPlayer caster, Use use, String key) {
        tell(caster, key, ChatFormatting.GOLD);
        inspectLine(caster, use, "SANDBOX_" + key.replace("sandbox_", "").toUpperCase(Locale.ROOT));
        return Begin.REFUSED;
    }
```

- [ ] **Step 4: /lockinfo's 10 s becomes 30 s for a sandbox read.** In `tickHolds`, find:

```java
                    job.clock().deadline = now + LockRules.SOLVE_TIMEOUT_TICKS;
```

Replace with:

```java
                    job.clock().deadline = now + (job.sandbox() != null
                            ? LockPickRules.HELD_SOLVE_TIMEOUT_TICKS : LockRules.SOLVE_TIMEOUT_TICKS);
```

- [ ] **Step 5: Refusals found while running, and the world check before use.** In `explainFailure`, find:

```java
        if (status == LockSolver.Status.SOLVED) {
            return true;
        }
```

Replace with:

```java
        if (status == LockSolver.Status.SOLVED) {
            return true;
        }
        if (job.sandbox() != null && job.sandbox().refusal != null) {
            tell(player, job.sandbox().refusal, ChatFormatting.GOLD); // the sandbox could not copy or run the lock
            return false;
        }
```

In `finishJobs`, find:

```java
            } else if (explainFailure(player, job, solution)) {
                if (job.use() == Use.REVEAL) {
```

Replace with:

```java
            } else if (explainFailure(player, job, solution)) {
                if (job.sandbox() != null && !job.sandbox().stillAsCopied(level)) {
                    tell(player, "shifted", ChatFormatting.GOLD); // the lock changed after the sandbox copied it
                } else if (job.use() == Use.REVEAL) {
```

(The `} else if (job.lock().circuit().truncated()) {` and `} else {` branches that follow stay as they are.)

- [ ] **Step 6: `/lockinfo` says it was a sandbox read.** In `report`, find:

```java
        player.sendSystemMessage(Component.literal("Lock (rank " + job.rank() + "): " + lock.pistons() + " door pistons, "
```

Replace with:

```java
        player.sendSystemMessage(Component.literal("Lock (rank " + job.rank() + (job.sandbox() != null ? ", sandbox" : "")
                + "): " + lock.pistons() + " door pistons, "
```

Find:

```java
        player.sendSystemMessage(Component.literal(line.toString()));
    }
```

Replace with:

```java
        if (job.sandbox() != null) {
            line.append(" | sandbox: ").append(job.sandbox().stats());
        }
        player.sendSystemMessage(Component.literal(line.toString()));
    }
```

In the `report` javadoc, find:

```java
     * BUSY, PISTON_LOGIC, OUT_OF_TIME or ERROR (see {@link #inspectLine}).
```

Replace with:

```java
     * BUSY, PISTON_LOGIC, TOO_MANY_INPUTS, OUT_OF_TIME, ERROR or a SANDBOX_ word (see {@link #inspectLine}).
     * A lock that moves blocks with pistons, read at rank 4 in the sandbox, says "sandbox" after its rank and ends
     * its second line with " | sandbox: C copies, A attempts, T sandbox ticks[, refusal]".
```

- [ ] **Step 7: Lines.** In `en_us.json`, find:

```
  "message.hogwartsmagic.lock.piston_logic": "This lock moves blocks with pistons; magic cannot read it.",
```

Replace with:

```
  "message.hogwartsmagic.lock.piston_logic": "This lock moves blocks with pistons; only a Master can read it.",
  "message.hogwartsmagic.lock.sandbox_missing": "The mirror world magic reads piston locks in is missing.",
  "message.hogwartsmagic.lock.sandbox_strange": "This lock is built from things magic cannot copy.",
  "message.hogwartsmagic.lock.sandbox_crowded": "Something is standing in this lock; magic cannot copy it.",
  "message.hogwartsmagic.lock.sandbox_fluid": "Flowing water or lava runs through this lock; magic cannot copy it.",
  "message.hogwartsmagic.lock.sandbox_too_big": "This lock's moving parts reach too far to copy.",
```

Validate: `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`. Expected: no output.

- [ ] **Step 8: `LockInfoCommand`.** Find:

```java
/** /lockinfo [3|4]: trace and solve the piston door you look at, as Alohomora of that rank. Changes nothing. */
```

Replace with:

```java
/**
 * /lockinfo [3|4]: trace and solve the piston door you look at, as Alohomora of that rank. Changes nothing in the
 * world; at rank 4 a lock that moves blocks with pistons is read in the sandbox dimension instead.
 */
```

Find:

```java
        return 0; // refused: the player was told why (BUSY or PISTON_LOGIC)
```

Replace with:

```java
        return 0; // refused: the player was told why (BUSY, PISTON_LOGIC, TOO_MANY_INPUTS or a SANDBOX_ word)
```

- [ ] **Step 9: Build, greps and boot.**
  - Run the gradle check, then `timeout 900 ./gradlew compileJava test --console=plain`. Expected: BUILD SUCCESSFUL, N + 31 tests green.
  - Run `grep -n "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/support/lock/*.java`. Expected: nothing.
  - Run the CLOUD-OVERRIDES boot block. Expected: `Done (`, `Lock sandbox ready`, and no `Exception` or `ERROR`.

- [ ] **Step 10: Commit.**

```bash
git add src/main/java/com/hogwartsmagic/spell/support/lock/PistonLockSpell.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/command/LockInfoCommand.java
git commit -m "Read piston-logic locks in the sandbox at Master, through the hold, playback, hints and lockinfo" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Final verification and jar

**Files:** none changed (fixes, if any, go back to the task that owns the file).

- [ ] **Step 1:** Run the gradle check, then `cd /home/user/hogwarts_magic_mod && timeout 900 ./gradlew clean build --console=plain`. Expected: BUILD SUCCESSFUL, N + 31 tests, all green, and no new warnings in the report.
- [ ] **Step 2:** Validate the three JSON files: `en_us.json`, `hogwartsmagic.mixins.json` and `dimension/lock_sandbox.json`.
- [ ] **Step 3:** Run the safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell src/main/java/com/hogwartsmagic/network src/main/java/com/hogwartsmagic/util src/main/java/com/hogwartsmagic/entity src/main/java/com/hogwartsmagic/mixin/*.java`. Expected: the same lines as at f24118f, with no new ones.
  - `git diff f24118f --stat`. Expected: only the files in the File Structure table.
- [ ] **Step 4:** Run the dedicated-server boot. Expected:
  - `Done (` and `Lock sandbox ready`;
  - no mixin, registry or class-loading error;
  - `ls run/world/dimensions/hogwartsmagic/` lists `lock_sandbox` (or nothing yet), with no region files written by an idle boot.
- [ ] **Step 5:** Report the jar path `build/libs/hogwartsmagic-1.0.0.jar`. The user copies it into `C:\Users\alonl\curseforge\minecraft\Instances\hogwarts\mods\` with Minecraft closed. No commit.

---

### Task 8: In-game verification (with the user)

This cannot be automated. Install the jar from Task 7. Set ranks with `/spellmastery`. Run `/lockinfo` on any door that misbehaves and paste both lines back. Run `/forge tps` during a long pick.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | 2×1 sticky piston door; one lever moves a separate sticky piston that pushes a redstone block next to the door pistons (piston logic); Alohomora Expert | Cast and hold | "This lock moves blocks with pistons; only a Master can read it." Nothing moves |
| 2 | same door; Alohomora Master | Cast and keep right-click held | Progress bar, `~?s` then a time; the success click; the lever flips; the door opens; "The lock gives way."; XP once |
| 3 | same | Cast and let go at once | "You stop picking the lock."; nothing moves; no XP |
| 4 | a T flip-flop door (observer or piston toggle) opened by one button | Hold Alohomora Master | The button is pressed once and the door opens (or closes, if it was open) |
| 5 | door 2 plus a second lever that pushes a redstone block into a dispenser with arrows | Hold Alohomora Master | The safe lever is used; no arrow is fired anywhere; the door opens |
| 6 | a piston-logic door whose only opener fires the dispenser | Hold | "No setting of this lock's switches moves the door."; no arrows |
| 7 | door 2; Revelio Master | Hold until done | The lever glows "Flip on" for 15 s; nothing moves |
| 8 | door 2 | `/lockinfo` | "Lock (rank 4, sandbox): ..." and "SOLVED: moves 2/2 pistons ... \| set x, y, z to 15 \| sandbox: 2 copies, … attempts, … sandbox ticks" |
| 9 | door 2 with a modded block in the wiring | Hold | "This lock is built from things magic cannot copy." |
| 10 | door 2 with an item frame or a mob inside the lock area, or someone on its pressure plate | Hold | "Something is standing in this lock; magic cannot copy it." |
| 11 | door 2 with flowing water beside the wiring | Hold | "Flowing water or lava runs through this lock; magic cannot copy it." |
| 12 | a slime-block flying-machine door that travels far | Hold | "This lock's moving parts reach too far to copy." |
| 13 | door 2 | After the success click, flip the logic lever by hand before playback | "Someone touched the lock while you read it." |
| 14 | 6-lever + 2-button piston-logic door | Hold and watch `/forge tps` | Overall mean tick time stays near its normal value (at most about 2–3 ms higher); the bar fills; done within 30 s or "too intricate to read in time" |
| 15 | any | `/execute in hogwartsmagic:lock_sandbox run tp @s 0 100 0` | Sent back to the overworld spawn a moment later |
| 16 | any | Stop the server mid-pick, start it, pick again | No error in the log; the second pick works |
| 17 | dedicated server and a client | Join, then row 2 | No registry or dimension mismatch on join |
| 18 | a 0-tick piston door (instant retraction) and a door that depends on the dust update order | Hold Alohomora Master | The door behaves exactly as when the lever is flipped by hand |
| 19 | a piston-logic door in the Nether | Hold Alohomora Master | Works as in the overworld |
| 20 | a normal (non-piston-logic) 6-lever door | Hold Alohomora Master | Solved by the pure solver as before (`/lockinfo` has no "sandbox") |
| 21 | two players, two piston-logic doors | Both hold at once | The second shows `~?s` until the first finishes, then counts down; both doors open |

A failure goes back to the implementer of the owning task, with the row number, the `/lockinfo` output and `latest.log`.

---

## Self-review notes

- **Requirement coverage.**
  - Requirement 1 (the pure solver stays for normal locks): `begin` reaches `beginSandbox` only when `pistonLogic()` is set and the rank is 4; every other path is unchanged (row 20).
  - Requirement 2 (the sandbox copies, applies, ticks and watches): Tasks 3–5; rows 2, 4, 5, 18 and 19.
- **Controller rulings.**
  - Master only: `SandboxRules.eligible` (row 1).
  - 8 inputs and 2 presses: `MAX_INPUTS` (checked in `beginSandbox`), `MAX_PRESSES` (in the plan).
  - Time-sliced with a budget: `anotherTick`, `TICK_BUDGET_NANOS`, the heavy-tick stop (row 14).
  - Both spells through the hold: `Job` gets `handle.result` and `handle.progress`, and `holdFor` is unchanged (rows 2, 3 and 7).
  - No forcing: `Mode(false, false)`, R6.
  - Never loads a real chunk: `capture` uses `getChunkNow` and `matches` uses `isLoaded`.
  - Traps detected and rejected: R9, the trap mixins, `slot.trap` (rows 5 and 6).
  - `/lockinfo` reports: Task 6 Step 6 (row 8).
- **The hardest parts, decided:**
  - the private level: R1;
  - an exact copy: Architecture and R4 (offsets);
  - cheap reset: the diff write from a settled template;
  - controlled ticking: R2 and R3;
  - attempts per tick: preflight estimate;
  - success: R10;
  - threads: Architecture;
  - mods and failures: R7, R14, R15, the `fail` path and `markBroken`.
- **Type consistency.** `Job(..., hold, sandbox)` is built in two places (solver `null`, sandbox `handle`). `LockSandbox.Handle.result` is a `CompletableFuture`, assigned to the `Future` field. `Solution` arrays are input indexes of the retraced lock, which `schedule`, `LockHints.show` and `report` read through `job.lock()`.
- **Placeholder scan:** none. The Task 3 stub is deliberate; Task 5 replaces it by the quoted text.
- **Counts:** Task 1 adds 17 tests and Task 2 adds 14, so N + 31.
