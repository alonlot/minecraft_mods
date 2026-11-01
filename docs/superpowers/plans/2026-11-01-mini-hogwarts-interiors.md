# Mini Hogwarts Interiors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Mini Hogwarts into a castle that is as magical inside as outside: the Sorting memory's Great Hall as a new wing, real rooms in every shell, a real-sky enchanted ceiling with floating candles, a ceiling spell for players' own builds, and a cinematic arrival.

**Architecture:** A Python generator (`build_scripts/mini_hogwarts/`) composes the new `mini_hogwarts.schem` from the old one plus the Great Hall set and room builders, and writes a sidecar `mini_hogwarts.anchors.json` naming every spot the Java side needs (chests, spawns, effects). On the Java side, `MiniHogwartsManager` places it as before (now resumable and animated) and registers the castle's effects in a per-level `AmbienceRegistry`, which is synced to clients and drawn by the existing memory renderers using world time. The ceiling spell writes to the same registry.

**Tech Stack:** Forge 1.21 (Java 21), SimpleChannel packets, SavedData; Python 3 (no deps beyond the repo's scripts) for the schematic; JUnit 5, Forge GameTests, the textured structure viewer.

**Spec:** `docs/superpowers/specs/2026-11-01-mini-hogwarts-interiors-design.md`

## Global Constraints

- Branch `sorting-memory` only. Push to `origin` only; never to the `github` mirror, never merge to `main` (the Great Hall's licence is unknown).
- Commit and push after each task (`timeout 60 git push origin sorting-memory`).
- Never run gradle while the user's Minecraft client is running (ask first if unsure).
- No `switch` on an enum in render code; use int constants or `if` chains.
- Never `broadcastEntityEvent` with bytes 21, 35 or 63.
- In `RenderLevelStageEvent`, take the partial tick the way `PensieveClientState.partial()` does, never from `event.getPartialTick()` (that is the frame delta).
- Common/server code never references a `client` class; client work goes through `ClientSideBridge.handlePacket` → `ClientRuntimeBridge`.
- The castle is a worldgen Structure whose piece writes only inside each chunk's box; never a worldgen Feature; never call `getChunk(..., status, true)` or `getStructureWithPieceAt` on a tick path; level-wide state from worldgen threads only via `server.execute`.
- Leaves always have an opaque block behind them. Judge density in the textured viewer, not by counts.
- The memory's `great_hall.nbt` must stay byte-identical.
- Python scripts that read data run with `python -I`.

## Review Focus

1. **A castle chunk generated while its neighbours are not** must still produce a whole castle once they generate, with no write outside the chunk (Task 11 test `pieceWritesOnlyInsideChunkBox`).
2. **A player teleporting into fresh Forbidden Forest** must not stall worldgen (Task 11 headless check).
3. **A player joining, respawning or changing dimension next to an already-built castle** must see its ceiling and candles (Task 12 test `syncSentOnLoginAndDimensionChange`).
4. **Ceiling spell corners that make no sense** (different dimension, too far apart, overlapping another ceiling, the caster logged out between casts, the mark expired) give a hint and create nothing (Task 15 tests).
5. **Many players in the hall at once**: the first-step reveal plays only for a player who has never entered that hall; others keep the open sky (Task 13 test `revealOncePerPlayer`).

---

## Part A — The castle generator (Python)

Coordinates in Part A are **castle** coordinates: the new schematic's own frame, `x` east, `y` up, `z` south, all ≥ 0. Old-schematic cells are moved by `BASE_SHIFT` into this frame.

### Task 1: Let the Great Hall set keep its roof

**Files:**
- Modify: `build_scripts/pensieve_scenes/great_hall_set.py` (`build()` at ~1071)

**Interfaces:**
- Produces: `great_hall_set.build(open_roof: bool = True) -> Builder` (unchanged result when `open_roof` is true; with false, `open_sky` is skipped and `b.sky_opened = 0`).

- [ ] **Step 1:** Record `md5sum src/main/resources/data/hogwartsmagic/structure/great_hall.nbt`.
- [ ] **Step 2:** Add the `open_roof` parameter; call `open_sky(b)` only when it is true.
- [ ] **Step 3:** Run `python -I build_scripts/pensieve_scenes/great_hall_set.py`. Expected: same summary as before and the same md5.
- [ ] **Step 4:** `python -I -c "import sys; sys.path.insert(0,'build_scripts/pensieve_scenes'); import great_hall_set as G; b=G.build(open_roof=False); assert any(p[1]>=G.CEIL_Y and 1<=p[0]<=82 and 6<=p[2]<=38 for p in b.g)"` passes (the roof is there).
- [ ] **Step 5:** Commit `Let the Great Hall set build keep its roof`.

### Task 2: Castle grid, schematic writer, checks

**Files:**
- Create: `build_scripts/mini_hogwarts/__init__.py` (empty), `build_scripts/mini_hogwarts/grid.py`, `build_scripts/mini_hogwarts/checks.py`, `build_scripts/mini_hogwarts/test_castle.py`
- Read-only source: `build_scripts/mini_hogwarts/source/mini_hogwarts_v1.schem` (copy of today's schematic, committed, so regeneration never reads its own output)

**Interfaces:**
- Produces (grid.py):
  - `Grid` class: `cells: dict[tuple[int,int,int], str]` (block-state strings, no air), `anchors: dict[str, object]`, `set(x,y,z,state)`, `get(x,y,z) -> str` (`'minecraft:air'` when absent), `clear(x,y,z)`, `fill(x0,y0,z0,x1,y1,z1,state)`, `bounds() -> (minx,miny,minz,maxx,maxy,maxz)`.
  - `load_schem(path) -> Grid` (Sponge v2 or v3, via `schem_to_nbt.decode_schem`).
  - `write_schem(grid, path)`: Sponge **v3** (`Schematic{Version:3, DataVersion:3953, Width, Height, Length, Blocks{Palette, Data}}`), size = bounds max + 1, air index 0, LEB128 varints, index `x + z*W + y*W*L`, gzip. No block entities.
  - `rotate_state(state: str, quarter_turns: int) -> str`: clockwise seen from above; rotates `facing`, `axis` (x↔z on odd turns), `rotation` (0..15, +4 per turn), `north/east/south/west` keys, stair `shape` unchanged (rotation preserves handedness), `hinge` unchanged.
  - `paste(dst: Grid, src_cells: dict, origin: (x,y,z), quarter_turns: int, src_size: (sx,sy,sz))`.
- Produces (checks.py):
  - `census(grid) -> collections.Counter` of block ids.
  - `reachable(grid, start: (x,y,z), max_cells=2_000_000) -> set` of walkable feet cells (air or passable at feet and head, solid or stair/slab below; steps up 1, down any ≤ 3; doors, carpets, pressure plates, light, ladders and scaffolding passable).
  - `dark_floor_cells(grid, boxes: list) -> list` of walkable interior cells farther than 7 blocks (Manhattan, through air) from any light source (`light`, torches, lanterns, campfire, glowstone, sea_lantern, candles with lit=true, fire, shroomlight, end_rod, jack_o_lantern, magma not counted).
- Test: `test_castle.py` (plain asserts, run as a script).

- [ ] **Step 1: Write the failing tests** in `test_castle.py`:
  - `test_roundtrip()`: `load_schem(v1)` → `write_schem` → `load_schem` gives identical `cells`.
  - `test_rotate_state()`: `rotate_state('minecraft:oak_stairs[facing=north,half=bottom,shape=straight]',1)` has `facing=east`; `'minecraft:oak_log[axis=x]'` turned 1 → `axis=z`; `'minecraft:white_banner[rotation=2]'` turned 1 → `rotation=6`; `'minecraft:cobblestone_wall[east=low,north=none,south=tall,up=true,west=none]'` turned 1 → `south=low,west=none... north=none` mapping east→south, south→west.
  - `test_reachable_small()`: a 5×1×5 stone floor with a 2-tall wall splitting it except a door gap → both halves reachable; close the gap → only one.
  - `test_dark_floor()`: 20-long floor with one lantern at one end → cells beyond 7 are dark.
- [ ] **Step 2:** `python -I build_scripts/mini_hogwarts/test_castle.py` fails (no module).
- [ ] **Step 3:** Implement `grid.py` and `checks.py`; copy the v1 schematic into `source/`.
- [ ] **Step 4:** Tests pass. Also verify the Java loader reads a v3 file you wrote: the header fields match `MiniHogwartsSchematic`'s reads (`Schematic/Blocks/Palette`, `Schematic/Blocks/Data`).
- [ ] **Step 5:** Commit `Add the Mini Hogwarts generator's grid, schematic writer and checks`.

### Task 3: The Great Hall wing

**Files:**
- Create: `build_scripts/mini_hogwarts/build_castle.py` (entry point; one function per room, called in order), `build_scripts/mini_hogwarts/hall.py`
- Output: `src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem`, `src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.anchors.json`, a viewer copy `build/mh/mini_hogwarts.nbt` (via `schem_to_nbt.py`)

**Interfaces:**
- Consumes: Task 1 `build(open_roof=False)`, Task 2 grid/checks.
- Produces:
  - `build_castle.py` constants: `BASE_SHIFT = (0, 0, 60)` (old cells move 60 south so the hall can grow north; change only if the render shows a clash), `MAIN_FLOOR_Y = 24` (castle floor block layer; walk level 25).
  - `hall.place_hall(grid) -> dict` returning the hall's anchor dict (see below).
  - Anchors JSON top-level keys (castle coords, floats where a centre): `"hall_ceiling": {"min":[x,z],"max":[x,z],"y":planeY}`, `"candles": [{"at":[x,y,z],"spread":[hx,hy,hz],"count":n}, ...]`, `"rays": [{"at":[x,y,z],"yaw":deg,"spread":[hw,h,len]}]`, `"doors": [{"hinge":[x,y,z],"yaw_closed":deg,"mirrored":bool}]`, `"hall_box": {"min":[x,y,z],"max":[x,y,z]}`, `"main_entrance": [x,y,z]`, `"stool": [x,y,z]`.
- Siting (default, from the floor plans in the spec): hall long axis on **z**, staff end **north**, the portal and entrance hall **south**; rotate the set so its local +x points to castle +z (one quarter turn as `rotate_state` defines it, or three, whichever puts the dais north); the entrance hall's south wall touches the central tower's north side (old tower centre ~(68, 64) → castle (68, 124)), hall centre line on castle x ≈ 68; set floor (local y 0) at `MAIN_FLOOR_Y`.

- [ ] **Step 1: Write the failing assertions** at the end of `build_castle.py` (`verify(grid)`):
  - old diagonal wing gone: no castle cell with block id in the old wing's palette (stone, cobblestone, oak_planks, black glass) inside the old wing's polygon (old x 33..80, z 22..63 shifted, y ≥ 25) except where the new hall now stands;
  - `reachable(grid, main_entrance)` contains the hall floor cell next to the stool and a cell on the viaduct deck;
  - the hall roof exists over `hall_ceiling` (every column in it has a solid cell above `y`);
  - nothing of the hall hangs over air: every hall footprint column has solid cells down to rock (a stone plinth from the rock up to `MAIN_FLOOR_Y`, hollowed later in Task 8);
  - `dark_floor_cells(grid, [hall_box])` is empty;
  - the four candle boxes are all inside `hall_box` and below the ceiling plane.
- [ ] **Step 2:** Run `python -I build_scripts/mini_hogwarts/build_castle.py`; assertions fail.
- [ ] **Step 3: Implement `place_hall`:** remove the old wing (walls, roof, ridge towers; keep terrain blocks: stone below the old floor line, grass, dirt, gravel); paste the roofed set; transform the set's anchors (`CEIL_X/CEIL_Z/CEIL_Y`, `DOOR_HINGES`, `STOOL`) and the four `floating_candles` events of `memories/sorting_hall.json` (scene → local is `at - TEMPLATE_AT`) into castle coords; re-skin the exterior faces of the set's walls and roof to the castle palette (stone walls, cobblestone trim and buttresses, the castle's roof material and pitch); add light blocks behind the tall windows' outer face so they glow at night; stone plinth under the hall; open the entrance hall's west wall onto the viaduct with a short covered bridge if the viaduct does not meet it.
- [ ] **Step 4:** Assertions pass. Print a census.
- [ ] **Step 5: Render and look** (`rendering-minecraft-structures` skill): exterior from yaw 0.7 and 2.3, `cut=x` and `cut=z` through the hall, `maxy=` at the dais. Check the wing reads as part of the castle and the join to the tower and viaduct. Fix and rerun until it does.
- [ ] **Step 6:** Commit `Mini Hogwarts: swap in the Great Hall wing` and push.

### Task 4: Grand Staircase (central tower)

**Files:** Create `build_scripts/mini_hogwarts/rooms_staircase.py`; Modify `build_castle.py` (call it, extend `verify`).

**Interfaces:** Produces anchors `"fat_lady": {"wall":[x,y,z],"facing":"north|south|east|west"}`, `"stair_candles": {"at":[x,y,z],"spread":[hx,hy,hz],"count":n}`, `"swing_stair"` (only if the stretch goal is done; see Task 13).

- [ ] **Step 1:** Assertions: from the entrance hall, every landing (one per floor of the tower) is reachable; the top landing connects to the Gryffindor common room door (Task 6 location reserved: the tower that will hold it); `dark_floor_cells` empty for the tower box.
- [ ] **Step 2:** Run, fail.
- [ ] **Step 3:** Build: open stairwell the tower's full height, flights switching back round the wall (stone-brick stairs, dark-oak rails), landings every floor with doorways into the neighbouring wings, paintings (block-placed `minecraft:painting` cannot carry NBT here, so frame niches with dark oak and place paintings in Task 10 from anchors `"paintings": [{"at":[x,y,z],"facing":...}]`), lanterns on chains, a candle box in the void.
- [ ] **Step 4:** Pass; render `cut=x` through the tower and look up the well.
- [ ] **Step 5:** Commit and push.

### Task 5: Library and Restricted Section

**Files:** Create `rooms_library.py`; Modify `build_castle.py`.

**Interfaces:** Produces anchors `"chests": [{"at":[x,y,z],"facing":"...","theme":"LIBRARY"}]` (append), `"pensieve": [x,y,z]`.

- [ ] **Step 1:** Assertions: reachable from the main entrance; ≥ 300 bookshelf-type blocks (bookshelf + chiseled_bookshelf + the Ollivanders pigeonhole mix); a barred Restricted Section with one door; no dark floor.
- [ ] **Step 2–4:** Fail → build (shell: the largest remaining interior after Task 3 and 4, by default the gatehouse's upper floors; shelving to the ceiling with ladders and a mezzanine, reading tables with lanterns, the Restricted Section in the Ollivanders language from `pensieve_scenes/diagon_set.py`) → pass, render `cut=z` and from inside.
- [ ] **Step 5:** Commit and push.

### Task 6: Tower tops — Gryffindor common room, Headmaster's office, Astronomy platform

**Files:** Create `rooms_towers.py`; Modify `build_castle.py`.

**Interfaces:** Produces anchors `"chests"` (TOWER theme in the office), `"banners": [{"at":[x,y,z],"facing":...,"house":"gryffindor"}]`.

- [ ] **Step 1:** Assertions: each of the three reachable from the main entrance; the common room's door is the `fat_lady` wall from Task 4; ≥ 4 beds in the dormitory; the platform's edge is railed (no walkable cell next to a drop > 3 without a fence/wall); no dark floor.
- [ ] **Step 2–4:** Fail → build (common room: red/gold, big stone fireplace, armchairs from stairs, a stair to the dorm; office behind a gargoyle (stone-brick statue ~20 blocks tall is too big — use a 3-block gargoyle carving at the stair door) and a spiral stair, round room, desk, phoenix perch (a gold/orange block perch), shelves; platform: telescopes from lightning rods + spyglass-like blocks, rail) → pass, render each.
- [ ] **Step 5:** Commit and push.

### Task 7: Classrooms

**Files:** Create `rooms_classrooms.py`; Modify `build_castle.py`.

- [ ] **Step 1:** Assertions: Transfiguration (behind the labelled door at old (38, 33..34, 106)), Defence and Charms rooms each reachable, each with ≥ 8 student desks and a teacher's desk; Potions untouched (its 7 cauldrons and 2 chests still there); no dark floor.
- [ ] **Step 2–4:** Fail → build (one theme prop each: Defence a dragon skeleton from bone blocks hung from the ceiling; Transfiguration cages and a blackboard; Charms floating feathers via end rods and a raised tiered floor) → pass, render.
- [ ] **Step 5:** Commit and push.

### Task 8: Dungeons, Slytherin common room, kitchens

**Files:** Create `rooms_below.py`; Modify `build_castle.py`.

**Interfaces:** Produces anchors `"elf_spots": [[x,y,z], ...]` (kitchens), `"chests"` (DUNGEON theme), `"student_spots": [[x,y,z], ...]` appended by every room task from here on (Task 10 spawns from it).

- [ ] **Step 1:** Assertions: carved into the hall's plinth and the rock below `MAIN_FLOOR_Y`; reachable from the entrance hall by a stair; four long tables in the kitchens under the four house tables (same x/z lines); ≥ 3 cells behind iron bars; the Slytherin room lit green (sea lanterns behind green glass) and no dark floor anywhere below.
- [ ] **Step 2–4:** Fail → build → pass, render with `maxy=MAIN_FLOOR_Y` and `cut=x`.
- [ ] **Step 5:** Commit and push.

### Task 9: Corridors, statues, light and the final checks

**Files:** Modify `build_castle.py`, add `rooms_corridors.py`.

**Interfaces:** Produces anchors `"statues": [{"at":[x,y,z],"yaw":deg}]` (alcoves; ≥ 8), `"title_point": [x,y,z]` (the castle's visual centre for the title-card distance check), and finalises `"student_spots"` (≥ 32 spots spread over every room).

- [ ] **Step 1:** Whole-castle assertions in `verify`: every room box listed in `ROOMS` (a dict the room tasks fill: name → box) is reachable from `main_entrance`; `dark_floor_cells(grid, ROOMS.values())` is empty; every anchor point is an air cell with a solid cell under it (chests, spawns, statues, pensieve) or a solid wall cell (fat_lady, paintings); census: cobweb ≤ 40, candles ≤ 600 block candles, torches+lanterns ≤ 900; the schematic writes and `MiniHogwartsSchematic` would load it (size fits 16-bit, palette < 32767).
- [ ] **Step 2–4:** Fail → corridors with lanterns or torches every 5–6 blocks, banners, carpets, statue alcoves, glowing windows on every outer face that has glass → pass.
- [ ] **Step 5:** Full render set (exterior ×2, `cut=x`, `cut=z`, per-room crops); look at each picture.
- [ ] **Step 6:** Commit `Mini Hogwarts: corridors, guardians, light` and push.

## Part B — Castle runtime (Java)

### Task 10: Anchors drive the furnishing; castle guardians

**Files:**
- Create: `src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsAnchors.java`
- Modify: `worldgen/MiniHogwartsManager.java` (`furnish`, `spawnStudents`, `spawnHouseElves`), `entity/LivingStatueEntity.java`
- Test: `src/test/java/com/hogwartsmagic/worldgen/MiniHogwartsAnchorsTest.java`, `src/main/java/com/hogwartsmagic/gametest/MiniHogwartsGameTests.java`

**Interfaces:**
- Produces: `MiniHogwartsAnchors` (immutable): `static @Nullable MiniHogwartsAnchors get()` (loads `/data/hogwartsmagic/schematic/mini_hogwarts.anchors.json` once), `static MiniHogwartsAnchors parse(JsonObject)`, record accessors `List<Chest> chests()` (`record Chest(BlockPos at, Direction facing, String theme)`), `List<BlockPos> studentSpots()`, `List<BlockPos> elfSpots()`, `List<Statue> statues()` (`record Statue(BlockPos at, float yaw)`), `@Nullable BlockPos pensieve()`, `@Nullable Wall fatLady()` (`record Wall(BlockPos at, Direction facing)`), `List<Wall> paintings()`, `List<Banner> banners()`, `Ceiling hallCeiling()` (`record Ceiling(double minX, double minZ, double maxX, double maxZ, double y)`), `List<Box> candles()` (`record Box(Vec3 at, Vec3 spread, int count)`), `List<Ray> rays()`, `List<Door> doors()`, `AABB hallBox()`, `BlockPos mainEntrance()`, `BlockPos titlePoint()`. All positions are schematic-local; callers add the placement anchor.
- `LivingStatueEntity`: `public void guardCastle(BlockPos home)` — sets a new synched boolean `DATA_CASTLE` and `homePos`; a castle statue senses only `Enemy` mobs, never targets players, and when it reaches 0 HP or is hit by a player it crumbles and reforms at `homePos` (reuse the bound path) and drops no loot. NBT key `"Castle"`.

- [ ] **Step 1: Failing tests:** `MiniHogwartsAnchorsTest.parsesEveryKey()` parses a small JSON literal with one of each key and asserts each accessor; `MiniHogwartsAnchorsTest.shippedFileParses()` loads the real resource and asserts `chests().size() >= 5`, `studentSpots().size() >= 32`, `statues().size() >= 8`, `candles().size() >= 4`. GameTest `castleStatueIgnoresPlayers`: a castle statue next to a survival mock player stays dormant for 60 ticks; `castleStatueWakesForZombie`: a zombie within 5 blocks wakes it.
- [ ] **Step 2:** `./gradlew.bat test --tests "com.hogwartsmagic.worldgen.MiniHogwartsAnchorsTest"` fails.
- [ ] **Step 3:** Implement; replace `furnish()`'s landmark heuristics with the anchors (keep `placeChest`, `rollLoot`, `stockClassroomChests`), hang the Fat Lady with `PortraitPlacement` at `fatLady()`, set the Sorting Hat on the stool (an `ItemDisplay` entity of the mod's sorting/pointed hat item if one exists, else a carved black-wool hat in Task 3), place paintings (`Painting` entity, random fitting variant), real wall banners in house colours, spawn students/elves at their spots, spawn castle statues; fall back to today's heuristics only if `get()` returns null.
- [ ] **Step 4:** JUnit passes; `./gradlew.bat runGameTestServer` passes (all tests, not just the new ones).
- [ ] **Step 5:** Commit and push.

### Task 11: The castle as a normal worldgen structure; title card (revised 2026-11-01)

The user does not want the castle built live: it must act like a normal structure (spec "Arrival: a normal
structure"). This replaces the old resumable/cinematic build task.

**Files:**
- Create: `worldgen/structure/MiniHogwartsStructure.java` (the `Structure` subclass + its `MapCodec`), `worldgen/structure/MiniHogwartsPiece.java` (the single `StructurePiece`), `worldgen/structure/ModStructures.java` (DeferredRegisters for `StructureType` and `StructurePieceType`), `data/hogwartsmagic/worldgen/structure/mini_hogwarts.json`, `data/hogwartsmagic/worldgen/structure_set/mini_hogwarts.json`, `data/hogwartsmagic/tags/worldgen/biome/has_structure/mini_hogwarts.json` (the forbidden_forest biome), `util/Titles.java` (the title/subtitle three-packet helper moved out of `DuelManager`)
- Modify: `worldgen/MiniHogwartsSchematic.java` (cache per-column lowest solid y), `worldgen/MiniHogwartsManager.java` (remove the live placement job and `maybeAutoSpawn`; keep the furnishing helpers callable from the piece), `worldgen/MiniHogwartsTracker.java`, `command/SpawnMiniHogwartsCommand.java`, `command/FindMiniHogwartsCommand.java`, `event/ModEvents.java` (drop the auto-spawn and tick hooks; add the title check), `ExampleMod.java` (register `ModStructures`)
- Test: `src/test/java/com/hogwartsmagic/worldgen/MiniHogwartsTrackerTest.java`, GameTests in `gametest/MiniHogwartsGameTests.java`

**Interfaces:**
- `MiniHogwartsPiece(BlockPos anchor)` (schematic min corner in world space; no rotation in this version); bounding box = schematic box from `anchor`, extended down by `MAX_SKIRT_DEPTH` (64). `postProcess(WorldGenLevel, StructureManager, ChunkGenerator, RandomSource, BoundingBox chunkBox, ChunkPos, BlockPos)` places every non-air schematic cell inside `chunkBox` (flag `Block.UPDATE_CLIENTS`), then the skirt for the columns inside `chunkBox` (stone, air cells only, down to the first non-air), then calls `MiniHogwartsManager.furnishChunk(WorldGenLevel, BlockPos anchor, BoundingBox chunkBox, RandomSource)` for chests, the Pensieve, the Fat Lady, students and elves whose positions fall inside `chunkBox` (today's heuristics stay until Task 10 swaps in anchors; run each heuristic only in the chunk that holds its result). When `chunkBox` contains the castle's centre column it schedules `server.execute(() -> MiniHogwartsTracker.get(serverLevel).add(...))`.
- `MiniHogwartsStructure.findGenerationPoint`: centre = the start chunk's middle; ground = max of `chunkGenerator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState)` over the 3x3 footprint sample; `anchorY = max(minY + 1, ground - FOUNDATION_SINK (2))`; returns `Optional.empty()` if the schematic is missing.
- Structure JSON: `"type": "hogwartsmagic:mini_hogwarts"`, `"biomes": "#hogwartsmagic:has_structure/mini_hogwarts"`, `"step": "surface_structures"`, `"terrain_adaptation": "beard_thin"`, `"spawn_overrides": {}`. Structure set: `random_spread`, `spacing 200`, `separation 100`, a fixed salt.
- `MiniHogwartsTracker`: `record Castle(BlockPos center, @Nullable BlockPos anchor, Set<UUID> titled, Set<UUID> enteredHall)`; `add(BlockPos center, BlockPos anchor)` (dedupes within 128 blocks), `castles()`, `findClosest(BlockPos)`, `markTitled(UUID, Castle)`, `markEnteredHall(UUID, Castle)`; old saves (a `"Castles"` long list) load as castles with a null anchor.
- `/spawnminihogwarts` (op): places the structure at the player's position through the vanilla structure placement path (the same code `/place structure` uses); `/findminihogwarts`: nearest tracked castle, else `ServerLevel.findNearestMapStructure` for the structure.
- Title card: every 20 ticks per player, a castle within 80 blocks of its centre with line of sight (`level.clip` from the eyes) whose `titled` set lacks the player: `Titles.send(player, "Hogwarts", "School of Witchcraft and Wizardry", 20, 70, 30)` and `markTitled`. The title point is one method (`titlePoint(Castle)`, the centre for now) so Task 10 can swap in the anchor.

- [ ] **Step 1: Failing tests:** `MiniHogwartsTrackerTest.loadsOldSaveFormat`, `.roundTripsCastles`; GameTests `pieceWritesOnlyInsideChunkBox` (run `postProcess` for one chunk box on a test level and assert no block outside it changed), `structurePlacesWholeCastle` (place the structure via the vanilla placement path in a test area; assert a sampled set of schematic cells matches and the tracker gained the castle), `titleShownOncePerPlayer`.
- [ ] **Step 2:** Fail.
- [ ] **Step 3:** Implement; delete the live job, the auto-spawn, the rumble and resume code.
- [ ] **Step 4:** `./gradlew.bat test` and `./gradlew.bat runGameTestServer` pass. Headless check: a dedicated-server run (`runServer`, or the dedicated server harness from memory `reference_dedicated_server_harness`) with `/place structure hogwartsmagic:mini_hogwarts` and a teleport into a Forbidden Forest finds no errors, no "far chunk" lines and no worldgen stall.
- [ ] **Step 5:** Commit and push.

## Part C — Magic in the real world

### Task 12: Ambience registry and sync

**Files:**
- Create: `src/main/java/com/hogwartsmagic/ambience/AmbientEffect.java`, `ambience/AmbienceRegistry.java`, `ambience/AmbienceNet.java` (packets + send helpers), `ambience/AmbienceEvents.java` (login/respawn/dimension change sync), `client/ambience/WorldAmbience.java` (client store)
- Modify: `network/SpellPackets.java` (register `AmbienceNet` packets), `client/ClientRuntimeBridge.java`, `worldgen/MiniHogwartsManager.java` (register the castle's effects when furnishing finishes)
- Test: `src/test/java/com/hogwartsmagic/ambience/AmbientEffectTest.java`, GameTests in `MiniHogwartsGameTests`

**Interfaces:**
- `public record AmbientEffect(UUID id, int kind, double x, double y, double z, float hx, float hy, float hz, float yaw, int count, @Nullable UUID owner, long createdTick)` with `KIND_CEILING=0, KIND_CANDLES=1, KIND_RAYS=2, KIND_DOOR=3`; `void encode(FriendlyByteBuf)`, `static AmbientEffect decode(FriendlyByteBuf)`, `CompoundTag save()`, `static @Nullable AmbientEffect load(CompoundTag)`, `boolean overlapsXZ(AmbientEffect other)` (ceilings only). For a ceiling: `x,z` centre, `hx,hz` half extents, `y` the plane.
- `AmbienceRegistry extends SavedData` (`DATA_NAME = "hogwartsmagic_ambience"`): `static AmbienceRegistry get(ServerLevel)`, `List<AmbientEffect> all()`, `void add(ServerLevel, AmbientEffect)` and `boolean remove(ServerLevel, UUID)` (both broadcast a delta to that level's players), `List<AmbientEffect> ceilingsOwnedBy(UUID)`, `@Nullable AmbientEffect ceilingAt(Vec3 point)`.
- Packets: `AmbienceSyncPacket(String dimension, List<AmbientEffect> all)`, `AmbienceDeltaPacket(boolean add, AmbientEffect effect)` (remove sends the effect with only `id` meaningful), `CeilingRevealPacket(UUID id)`; client actions `"ambienceSync"`, `"ambienceDelta"`, `"ceilingReveal"`.
- `WorldAmbience` (client): `static List<AmbientEffect> ofKind(int kind)` for the current level only (cleared on level change/logout), `static float revealStart(UUID ceiling)` (`Float.NaN` = no reveal pending; the reveal's game-time start otherwise).

- [ ] **Step 1: Failing tests:** `AmbientEffectTest.roundTripsBuffer()`, `.roundTripsNbt()`, `.overlapsXZ()`; GameTests `castleRegistersEffectsWhenFurnished` (ceiling + ≥ 4 candles + rays + 2 doors registered), `syncSentOnLoginAndDimensionChange` (a mock player's connection receives an `AmbienceSyncPacket` with the castle's effects).
- [ ] **Step 2:** Fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Pass (JUnit + gametests).
- [ ] **Step 5:** Commit and push.

### Task 13: Renderers draw the world's ambience; doors; first-step reveal

**Files:**
- Modify: `client/pensieve/EnchantedCeiling.java`, `client/pensieve/FloatingCandlesRenderer.java`, `client/pensieve/LightRaysRenderer.java`
- Create: `client/ambience/WorldDoors.java` (client-only door puppets), `client/ambience/AmbienceClock.java`
- Modify: `event/ModEvents.java` or `AmbienceEvents` (server: player enters `hallBox` for the first time → `enteredHall` + `CeilingRevealPacket`)
- Test: `src/test/java/com/hogwartsmagic/client/ambience/AmbienceClockTest.java`, GameTest `revealOncePerPlayer`

**Interfaces:**
- `AmbienceClock.now(ClientLevel level, float partial) -> float` = `level.getGameTime() + partial` (as float relative to a per-session base to keep precision: `(gameTime - base) + partial`, base = gameTime at level join).
- Each renderer draws its `EffectVisuals` list exactly as today when in a memory, and otherwise (`!PensieveClientState.inMemory()`) draws `WorldAmbience.ofKind(...)` effects within 128 blocks of the camera using `AmbienceClock.now`. World skies use `CeilingSky.REAL` (Task 14; until then render as `NIGHT`). Sky instances for world ceilings are cached by effect id.
- Light rays: drawn only while `level.getSunAngle`-derived daylight > 0.2 and `level.getRainLevel(partial) < 0.2`, intensity scaled by daylight.
- `WorldDoors`: for each `KIND_DOOR` effect within 64 blocks, a client-side `MemoryDoorEntity` (or `memory_door_mirrored` when `count == 1` marks mirrored) added with `ClientLevel#addEntity` under a negative id at the hinge, scale 4 via the same pose scaling `MemoryActors` uses; yaw eases from `yaw` (closed) to `yaw ± 100°` (open) over 30 ticks when any player is within 6 blocks of the door plane, back after 100 ticks with none; removed when out of range or on level change. The opening behind them is always empty, so nobody is ever blocked.

- [ ] **Step 1: Failing tests:** `AmbienceClockTest.monotonicAcrossTicks()`; GameTest `revealOncePerPlayer` (player A enters → one reveal packet to A; A again → none; B enters → one to B).
- [ ] **Step 2:** Fail.
- [ ] **Step 3:** Implement. The Pensieve path must be unchanged: `CeilingClearedWithSceneTest` and every existing test still pass.
- [ ] **Step 4:** Tests pass. In game (user's pass, checklist in Task 16): candles bob over the tables, doors swing for you and for a second player, rays at noon only.
- [ ] **Step 5:** Commit and push.

### Task 14: The ceiling follows the real sky

**Files:**
- Modify: `pensieve/scene/CeilingSky.java`, `client/pensieve/EnchantedCeiling.java`, `build_scripts/generate_ceiling_textures.py` (only if a day cloud texture is needed; reuse `ceiling_clouds_a/b` tinted white first)
- Test: `src/test/java/com/hogwartsmagic/pensieve/scene/CeilingSkyRealTest.java`

**Interfaces:**
- `CeilingSky.REAL = "real"`.
- `public static float[] realWeights(float dayTime /* 0..24000, level.getDayTime() % 24000 */, float rain, float thunder)` → `{day, dusk, night, overcast, storm}`, each 0..1, `day + dusk + night == 1`. Day full from 1000 to 11000; dusk peaks at 12500 and 23250 (dawn) with ~1500-tick blends; night full 13800..22200; `overcast = rain`; `storm = thunder`.
- `public static int daySkyColour(long seed, float u, float v)` (zenith #3F7FD6 → horizon #A9D2F2 gradient, the same mottling as night) and `public static int duskTint(float u, float v)` (horizon #F2A65A / #D96C6C, zenith #4B4F8C).
- `EnchantedCeiling`: a `Sky` with `style == REAL` draws the day layer (gradient + white clouds drifting with `cloudOffset`), the dusk tint, and the existing night layer, each scaled by its weight; `overcast` greys and thickens the clouds and dims the stars; `storm > 0.5` switches the cloud draw to the storm style (its slices clipped to the space under the roof by drawing only slices below `y + roofClearance`, where `roofClearance` comes from the effect's `hy`) with lightning. Explicit `night`/`storm` styles render exactly as today.

- [ ] **Step 1: Failing tests:** `CeilingSkyRealTest.noonIsDay()` (`realWeights(6000,0,0)[0] == 1`), `.midnightIsNight()` (18000 → night 1), `.sunsetBlends()` (12500 → dusk > 0.5, day + dusk + night == 1 within 1e-5), `.rainIsOvercast()`, `.weightsAlwaysSumToOne()` (every 50 ticks over a day).
- [ ] **Step 2:** `./gradlew.bat test --tests "*CeilingSkyRealTest"` fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Tests pass; existing ceiling tests pass.
- [ ] **Step 5:** Commit and push. (Visual check at noon, sunset, midnight, rain and thunder is on the Task 16 checklist.)

## Part D — The ceiling spell

### Task 15: Caelum Fascino

**Files:**
- Modify: `spell/SpellType.java` (append `CAELUM_FASCINO("caelum_fascino", 0x7FA8FF, 0.5f, 0.66f, 1.0f, ParticleTypes.END_ROD, ParticleTypes.ENCHANT, 40, 20, SpellLootRarity.RARE)`, cases in `applyHitEffect` (no-op) and `getSoundEvent()`), `entity/SpellBeamEntity.java` (block-hit branch; add to the block-only fizzle list), `item/MagicBookItem.java` (`SPELL_MODEL_ORDER` += `"caelum_fascino"`, model data 42), `spell/mastery/MasteryAwards.java` (`BLOCK_UTILITY`), `entity/ModVillagers.java` (Bookseller `advanced` list), `loot/MagicBookChestLoot.java` (`STRONGHOLD_LIBRARY`, `DEFAULT_POOL`), `worldgen/MiniHogwartsManager.java` (`rollLoot` LIBRARY pool), `assets/hogwartsmagic/lang/en_us.json`, `assets/hogwartsmagic/models/item/magic_book.json`, `src/test/java/com/hogwartsmagic/assignment/AssignmentDataTest.java` (41 → 42), `MOD_FEATURES_WIKI.md` (`### Caelum Fascino` after Riddikulus)
- Create: `spell/support/ceiling/CaelumFascinoSpell.java`, `models/item/magic_book_caelum_fascino.json`, `textures/item/magic_books/magic_book_caelum_fascino.png` (via the `texturing-spellbooks` skill), `data/hogwartsmagic/assignments/caelum_fascino.json` (teacher `flitwick`, 3 objective lines, RARE), `advancement/hp/caelum_fascino.json` if comparable spells have one
- Test: `src/test/java/com/hogwartsmagic/spell/ceiling/CaelumFascinoRulesTest.java`, GameTests in `src/main/java/com/hogwartsmagic/gametest/CeilingSpellGameTests.java`

**Interfaces:**
- `CaelumFascinoSpell.onBlockHit(ServerLevel level, BlockPos pos, @Nullable Entity caster) -> Result` with `enum Result { MARKED, CREATED, REMOVED, REFUSED, NOTHING }` (server logic, not render code).
- Pure rules (JUnit-testable, no level): `static @Nullable String refusal(BlockPos a, ResourceKey<Level> dimA, BlockPos b, ResourceKey<Level> dimB, List<AmbientEffect> existing, int ownedByCaster)` returning a lang key or null; constants `MAX_SIDE = 64`, `MAX_PER_PLAYER = 8`, `MARK_TTL_TICKS = 1200`.
- Rectangle: x from `min(ax,bx)` to `max(ax,bx)+1`, z likewise; plane `y = min(ay, by)` (the lower face of the lower corner block). Creates `AmbientEffect(KIND_CEILING, centre, half extents, hy = 0 … roof clearance 0, owner = caster, createdTick = gameTime)` with style REAL; the reveal starts at `createdTick` for every client.
- Sneak-cast on a block inside an existing ceiling's rectangle within 2 blocks of its plane removes it if the caster owns it or is op (permission level 2); otherwise `REFUSED` with a hint.
- Pending marks: `Map<UUID, Mark>` (`record Mark(ResourceKey<Level> dim, BlockPos pos, long tick)`), cleared on logout; while pending, every 10 ticks `sendParticles(caster, END_ROD, ...)` on the marked block's lower face, visible only to the caster.
- Lang keys: `spell.hogwartsmagic.caelum_fascino`, `.desc`, `.rank.2/3/4`, `message.hogwartsmagic.caelum_fascino.marked`, `.created`, `.removed`, `.too_big`, `.too_many`, `.overlap`, `.other_dimension`, `.expired`, `.not_yours`.

- [ ] **Step 1: Failing tests:** `CaelumFascinoRulesTest`: `sixtyFiveWideIsTooBig`, `ninthCeilingRefused`, `overlapRefused`, `otherDimensionRefused`, `validPairAccepted`; GameTests: `twoCornersCreateCeiling` (registry gains one effect with the expected box), `sneakCastRemovesOwnCeiling`, `otherPlayerCannotRemove`, `markExpires`, `ceilingSurvivesRestart` (save/load the registry); `AssignmentDataTest` and `RankChangeTextTest` updated and passing.
- [ ] **Step 2:** Fail.
- [ ] **Step 3:** Implement; texture via the `texturing-spellbooks` skill (match the existing set).
- [ ] **Step 4:** `./gradlew.bat test` and `./gradlew.bat runGameTestServer` all pass.
- [ ] **Step 5:** Commit and push.

## Part E — Finish

### Task 16: Wiki, headless check, in-game checklist

**Files:** Modify `MOD_FEATURES_WIKI.md` (Mini Hogwarts section: rooms, the build, the title, the ceiling following the sky; Caelum Fascino from Task 15), memory notes (`project_big_structures_outside_worldgen.md` update: anchors JSON, resumable job).

- [ ] **Step 1:** `./gradlew.bat test runGameTestServer` all green; `runServer` headless castle build clean (no errors, no far-chunk spam).
- [ ] **Step 2:** Write the user's in-game checklist into the final message: watch a castle build (rumble, dust, shake, ~1 min); title card once; walk every room from the entrance; Great Hall ceiling at noon, sunset, midnight, rain, thunder; candles; doors with two players if possible; reveal on first entry only; Fat Lady at the common room; statues ignore you and fight a zombie; cast Caelum Fascino in a house, then remove it; Fancy and Fabulous graphics.
- [ ] **Step 3:** Commit and push.
