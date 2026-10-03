# Pre-flight: piston-logic sandbox (the hybrid)

- Plan: `docs/superpowers/plans/2026-10-03-piston-logic-sandbox.md`. Base HEAD f24118f, branch spell-mastery. Tasks 1 to 8; Task 8 is the user's in-game checklist.
- Anchors are quoted from f24118f. Task 6 quotes `PistonLockSpell` as Task 4 leaves it; Task 4 only touches `operate`, its imports and `schedule`.
- Baseline test count: 319 `@Test` methods by grep. The real N is whatever Gradle prints. The plan adds 31: T1 adds 17 (12 + 5) and T2 adds 14 (7 + 7). Every later task expects N + 31.
- Checked with `javap` / `javap -c` on `forge-1.21-51.0.33_mapped_official_1.21.jar`.
- **The tick and its order.** In `ServerLevel.tick`:
  - `handlingTick = true` at offset 7;
  - `blockTicks.tick(getGameTime(), 65536, …)` at 217–230, then `fluidTicks`;
  - `chunkSource.tick` at 301;
  - `runBlockEvents` at 319;
  - `handlingTick = false` at 324;
  - entities and `tickBlockEntities` at 455, run only when there are players or forced chunks, or `emptyTime < 300` (`ForgeChunkManager.hasForcedChunks`).
- **Why a fake `Level` cannot work.** `PistonBaseBlock.checkIfExtend` reads `getGameTime() == PistonMovingBlockEntity.getLastTicked()` and `((ServerLevel) level).isHandlingTick()` (offsets 186–202). That is the 0-tick behaviour, and it is why the virtual tick must set `handlingTick` and run on the sandbox clock.
- **Per-level time.** `MinecraftServer.createLevels` makes a new `DerivedLevelData` inside the per-level loop (offset 418, loop 363–510). `LevelAccessor.createTick` reads `getLevelData().getGameTime()`.
- **Locational updates.** `Vec3i.hashCode` = `(y + 31 z) * 31 + x`, and `BlockPos` does not override it. `RedStoneWireBlock` uses `Sets.newHashSet`.
- **Signatures:**
  - `DispenserBlock` / `DropperBlock.dispenseFrom(ServerLevel, BlockState, BlockPos)` are protected. `TntBlock.explode(Level, BlockPos, LivingEntity)` is private static.
  - `RedstoneTorchBlock.RECENT_TOGGLES` is a `Map<BlockGetter, List<Toggle>>`, and `Toggle.pos` is package-private.
  - `LevelChunk.addAndRegisterBlockEntity` calls `setBlockEntity`, `addGameEventListener`, `updateBlockEntityTicker` and `onLoad`.
  - `LevelChunkTicks.getAll()` and `count()`, `LevelTicks.clearArea` / `schedule`, the 5-argument `ScheduledTick` constructor, and `ServerLevel.clearBlockEvents` / `nextSubTickCount` / `areEntitiesLoaded` / `resetEmptyTime` / `noSave` all exist.
  - `ServerChunkCache.addRegionTicket` / `removeRegionTicket` / `getChunkNow` / `isPositionTicking`, `TicketType.create(String, Comparator)`, `MinecraftServer.getAverageTickTimeNanos()` and `WorldBorder.isWithinBounds(AABB)` all exist.
  - `EntityTravelToDimensionEvent` and `EntityJoinLevelEvent` are `@Cancelable`. `TickEvent.ServerTickEvent.getServer()` exists.
  - `ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)` exists.
- Precedent: the mod already ships a datapack dimension, `data/hogwartsmagic/dimension/diagon_alley.json` (flat, no layers, overworld type).

## Throughput estimate (for decision 5)

| Item | Estimate |
|---|---|
| Attempts at the caps (8 inputs, 2 presses) | 8 levers: 255. 8 buttons: 72. 4 levers + 4 buttons: 335. Worst case, 6 levers + 2 buttons: 447 |
| Length of one attempt | Levers only: about 10–20 ticks (a piston takes 2–3 ticks, then 4 quiet ticks). With one press: about 40. With two presses: 20 + 40 + settle, about 80–100 |
| Cost of a copy-tick | A medium lock with dozens of active components: about 10–50 µs |
| Pace | With 8 copies, 8 × 20 µs = 0.16 ms per sandbox tick, so the 2 ms budget runs 10+ extra ticks per server tick. That gives about 1–1.5 attempts per server tick: the worst case (447) in about 15–20 s, typical locks in 1–5 s |
| Heavy locks | At 100 µs per copy-tick, about 2–3 extra ticks and 0.3 attempts per tick, so 447 attempts take about 70 s and give OUT_OF_TIME (or the best found) at the 30 s held cap |
| Memory and chunks | 8 copies of at most 5×5 chunk columns each: at most 200 tickets, typically 30–70 chunks. All are released when the read ends |

## Risks per task

**T1 (SandboxRules, LockTimeline)**
- Low risk. `LockTimeline` must equal the old `schedule` arithmetic. `LockTimelineTest` pins it, and T4 refactors `schedule` onto it.
- If `LockRules.LEVER_SETTLE_TICKS`, `PRESS_GAP_TICKS` or `FORCE_GAP_TICKS` change, playback and the sandbox move together, by design.

**T2 (AttemptPlan, SandboxSearch)**
- `AttemptPlan` mirrors `LockSolver.Search.run` (pass 1) loop for loop. If the solver's order changes later, this must follow, or the two will prefer different answers. Its size is cross-checked against `LockPickRules.attempts`.
- `SandboxSearch` relies on the sequential rule "replace only on strictly more". Its "lowest index of the maximum" is proven order-independent, and the tests cover out-of-order completion.

**T3 (dimension, clock, mixins, guards)**
- This is the largest new class-loading surface: 8 mixins under `defaultRequire 1`. A wrong target name fails the boot, which is good, and the boot check is mandatory.
- `DerivedLevelDataClockMixin` runs on every `getGameTime` of every derived level (Nether, End, Diagon Alley, other mods' dimensions). It costs one volatile read and one identity compare. It must never answer for any instance other than the sandbox's own.
- The sandbox gets `LevelTickEvent` once per server tick, so other mods' level-tick handlers see one more dimension (as they do Diagon Alley). The extra virtual ticks post no events.
- `ModEvents.onPistonPost` is the only mod handler known to react per piston move. The others were grepped: `BlockEvent.BreakEvent`, `EntityPlaceEvent`, `FluidPlaceBlockEvent` and `ExplosionEvent` need an entity or an explosion, which the sandbox never has. A `FluidPlaceBlockEvent` from a fluid the copy moved would still queue one Cave Inimicum scan. That is rare and accepted.
- If the datapack fails to load the dimension, `SandboxWorld.level()` returns null, and Master reads of piston-logic locks say "The mirror world … is missing".
- A player can still reach the sandbox through commands that skip `EntityTravelToDimensionEvent`; they are evicted a tick later. While there, they see the void and any copy being worked. Accepted.

**T4 (tracer positions, SandboxSnapshot, LockActions, schedule refactor)**
- Raw section writes skip light, heightmaps and client sync. None affects redstone except daylight detectors, which are refused. If a future vanilla update made a redstone component light-dependent, it would diverge.
- Block entity reload: `loadStatic` uses the NBT `id`. A block whose state was replaced with no block entity while its NBT says otherwise cannot occur, because states are written before block entities.
- `boxFor` reads the piston reach from the live world. Positions in unloaded chunks are caught by `capture` (`too_far`).
- The `TracedLock` record grows 3 fields. Only `CircuitTracer` constructs it; no test does (checked by grep).
- The `schedule` refactor must keep playback timing identical. The reviewer should compare the old and new tick arithmetic, and `LockTimelineTest` covers the cases.

**T5 (driver)**
- Ordering: an action is applied after the tick that brings the copy to its `t`, and before the next tick. That mirrors `runSteps` at a level's END. Off-by-one risk: the reviewer checks that a press at offset 20 sees exactly 20 sandbox ticks after the levers.
- `quiet()` filters ticks and moving pistons to the box, so junk from a crashed read in the same chunks cannot keep a copy busy. `clean()` removes block entities and ticks chunk-wide when a read starts.
- **Escape check:** the outer layer is compared at the end of every attempt. Blocks on that layer that change by random ticks (natural leaves decaying, crops) refuse the read as "reaches too far". Locks built into foliage may be refused.
- **Cross-copy flags:** `slotNear(x)` maps a hook position to a copy by x alone (copies are 961 apart). A trap hook outside every copy is ignored.
- **Budget:** the sandbox's own level tick cannot be preempted. A single runaway tick (a large slime machine) is bounded only by the box caps and the heavy-tick stop (15 ms, 3 times in a row).
- **Exceptions:** any `RuntimeException` turns the sandbox off until restart. The futures complete exceptionally, and `finishJobs` says `no_solution` (`ERROR` for `/lockinfo`).
- `Progress` now has the server thread as its single writer, still with one writer. `tickHolds` reads it on the same thread.

**T6 (integration)**
- The first trace (with forcing or item keys) is done, then the lock is retraced with `Mode(false, false)` when it turns out to be piston logic: two traces on the server thread, each at most 8192 blocks. The input indexes of the `Solution` refer to the retraced lock, which the `Job` keeps.
- `stillAsCopied` compares the real traced blocks with the settled template. A lock that legitimately keeps changing on its own (a clock) never settles and is UNSTABLE anyway.
- `/lockinfo` sandbox reads get the 30 s cap once started, with the 60 s queued ceiling unchanged.
- `piston_logic` now says "only a Master can read it". It is still sent below Master and when the sandbox is not eligible.

**T7 / T8**
- Nothing can be tested at runtime in the container without a client. The behaviour is proven only by Task 8.
- The highest-value rows are 2, 4, 5, 14 and 18: basic, toggle, trap, budget and exactness.

## Decisions for the controller to rule on

1. **The private level:** a datapack dimension, `hogwartsmagic:lock_sandbox` (overworld type, flat void, `the_void` biome), like Diagon Alley. Rejected: a fake `Level` subclass (`Block.tick` needs a `ServerLevel`, and pistons cast to `ServerLevel`) and a `ServerLevel` created at runtime (heavy, and outside vanilla's level list). The cost is one more dimension folder in every world.
2. **Ticking:** the sandbox's normal level tick plus extra virtual ticks on a private clock (the `DerivedLevelData` mixin, plus invokers for `tickBlock`, `tickFluid`, `runBlockEvents`, `tickBlockEntities` and `handlingTick`).
   - The alternative is no clock mixin: one tick per server tick, about 64 copies and several hundred chunks for the same throughput.
   - Rule which one.
3. **Budget:**
   - 2 ms per server tick, the sandbox's own tick included, and at most 40 extra ticks;
   - no extra ticks while the average tick is over 40 ms;
   - stop after 3 level ticks over 15 ms in a row;
   - 400 000 copy-ticks per read.
   - These are constants in `SandboxRules`, not `Config`. Should any of them be in `Config` (for example the budget in ms)?
4. **Copies:** at hash-preserving offsets `(x − 961 m, y, z + m)`, so updates run in the same order. Copy 0 is at the door's own coordinates. At most 8 copies. A box side may be at most 64 blocks and the box at most 32 768 blocks. Margin 2, piston reach 13.
5. **Cap:** keep the 30 s held cap for sandbox reads, or raise it to 60 s? The worst cases (heavy 7–8 input locks) may need about 70 s; typical locks need 1–5 s. The cap also applies to `/lockinfo` sandbox reads (instead of 10 s).
6. **Forcing and item keys:** no forcing and no item keys in the sandbox (the binding default). Comparators read real copied containers. Item frames are refused because entities are not copied. Forcing could be enabled later; the evidence that it would be safe is in R6. Rule whether to keep it off.
7. **Refused blocks:**
   - every non-`minecraft` block or block entity;
   - command blocks, structure and jigsaw blocks, daylight detectors, sculk shriekers, spawners, trial spawners, vaults, portals, end gateways, beacons, conduits, bee nests and hives, respawn anchors.
   - Add or remove any?
8. **Refused surroundings:** any non-player, non-hint entity in the box; any pressed plate, tripwire or detector rail; any flowing fluid or pending fluid tick. This is strict (a dropped item inside the lock area refuses it). Keep it strict, or allow item entities away from plates?
9. **Trap:** a dispenser or dropper dispensing, or TNT exploding, in the sandbox is cancelled there and rejects the attempt. Any entity join rejects it too, so a lock that needs a falling block or a crafter's output to open is unreadable. A trap firing in the baseline gives UNSTABLE ("never stops ticking") rather than a new line. Accept?
10. **The door:** the traced door pistons minus the pistons flagged as logic. Under M3 every piston within 6 blocks counts as the door; the sandbox drops the wiring pistons. Success means a door piston's block differs from the settled copy and every door piston has held still for 3 ticks; the attempt counts only if the copy is quiet for 4 ticks within 300 ticks of the last action. Accept?
11. **Same timeline as playback:** levers at 0, presses at +20 and then +40. A sculk sensor that is not ready fails the attempt, rather than reproducing playback's wait-and-retry. Accept?
12. **The world must still match:** before playback or hints, every traced block must equal the settled copy, or the caster gets "shifted". Accept?
13. **Escape:** a change on the outer layer of a copy refuses the whole read ("reaches too far"). Locks inside natural foliage may be refused when leaves decay. Accept?
14. **One read at a time,** others queued. Queued held reads show `~?s` and never time out while queued (as the solver queue). Accept?
15. **Not saved:** `noSave`, a wipe at the end of each read and on server stop, and block entities and ticks cleared chunk-wide at the start. Region files are not deleted, and junk blocks left by a crash stay, inert. Or should the sandbox folder be deleted at `ServerAboutToStartEvent`?
16. **Players** reaching the sandbox by command are sent to the overworld spawn, and travel events into it are cancelled. Accept?
17. **Random ticks** run in the sandbox's own level tick and are skipped in the extra ticks. Block-entity ticker order (for example hopper against hopper) follows copy order, not the world's load order. Both are accepted deviations from "exact"; confirm.
18. **`ModEvents.onPistonPost`** skips the sandbox. The Cave Inimicum scan would otherwise run for every piston move in a copy. Accept?
19. **Lang:** `piston_logic` is reworded to "only a Master can read it", and five new `sandbox_*` lines are added. The spell descriptions are unchanged. Should the Alohomora and Revelio descriptions mention the Master piston-logic reading?
20. **A `RuntimeException` turns the sandbox off** until restart (`markBroken`), and Master piston-logic reads then say "missing". Accept, or only abort that one read?
21. **Other mods:** their `PistonEvent` cancels, claim rules or redstone changes apply in the sandbox exactly as their code applies to any dimension. A mod that treats dimensions differently could make the copy differ from the world. Accept as a known limit?
22. **Exactness** cannot be proven in the container. The Task 8 rows 4, 5 and 18 (toggle, trap, 0-tick and update order) are the acceptance gate. Should more door designs be added to the checklist?
