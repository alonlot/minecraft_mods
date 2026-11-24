# Sea Cave Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Locket cave's template, look and player actions with an offshore sea stack whose
cavern, chain, skiff, goblet and Inferi the player sees and feels.

**Architecture:** A new Python package `build_scripts/sea_cave/` writes the template in three passes,
each gated on textured renders. Rotation-safe marker blocks inside the template tell the Java side where
the docks, waypoints, arch and lake are; the basin scans them once into a saved `CaveLayout`. The
existing basin state machine, ambush schedule, thirst and looted tracker are reworked in place.

**Tech Stack:** Forge 1.21 (Forge 51), Java 21, JUnit 5, GameTest; Python 3 with the NBT writer in
`build_scripts/generate_biome_structures.py`; the `rendering-minecraft-structures` skill; Blockbench MCP.

**Spec:** `docs/superpowers/specs/2026-11-24-sea-cave-redesign-design.md`

## Global Constraints

- Structure id stays `hogwartsmagic:locket_cave`; spacing 160, separation 80, salt unchanged.
- Biome tag `has_structure/locket_cave` = `minecraft:ocean`, `deep_ocean`, `cold_ocean`, `deep_cold_ocean`.
- Template about 80 x 90 x 80; waterline at sea level (world y 63 water surface); flared base reaches world y ≈ 22.
- One stone family in strata 2 to 6 thick; neighbouring bands within ~40 luma; never block-by-block mixes.
- No perfect circle, dome, cylinder or straight wall; no light except basin glow and revealed chain.
- Carved space: `cave_air` where it replaces terrain, water where flooded; shell watertight; piece writes only inside its chunk box.
- Island: 10 to 12 across, no step higher than 1 block anywhere from lake bed to top.
- Blood toll 6 damage (`hogwartsmagic:blood_toll`), open 60 s (1200 ticks), free from inside, never reseals on a player.
- Six cups empty the basin; effects, damage cap and refill rule from `DespairRules` unchanged; drinking never kills alone.
- Old caves (no layout version) keep working on the old `CaveGeometry` constants.
- Server-authoritative state; no client classes from common code; no entity event bytes 21, 35, 63; no switch-on-enum in models or renderers.
- Never run gradle while a client is running (compile in `C:/Users/alonl/hp-verify` instead, per memory); never inject keys while the user is at the PC.
- New textures go on a before/after sheet in `texture_proposals/` for the user to pick before commit.
- Commit and push (`git push github sorting-memory`, timeout 60) after each task.

## Review Focus

1. **A cave placed at each of the four rotations** — markers, docks, skiff path and lake test must all hold. Pinned in Task 5 (`CaveLayoutTest.rotatedMarkersStillDescribeTheSameCave`) and Task 8 (GameTest on a rotated template).
2. **An old cave in an already-generated world** — must still be completable with goblet drinking and the hidden chain. Pinned in Task 5 (`CaveLayoutTest.noMarkersMeansLegacyLayout`) and Task 9 (GameTest `legacyBasinAcceptsGoblet`).
3. **The player dies or logs out mid-crossing or mid-drink** — skiff must not strand, basin must refill, latched Inferi must let go. Pinned in Task 8 (`SkiffRulesTest.riderGoneReturnsToShore`) and Task 12 (`InferiusRulesTest.latchReleasedWhenRiderGone`).
4. **A second player arrives while the first is drinking or the ambush runs** — they must not break the drinker's run or get a free Locket. Pinned in Task 9 (`GobletRulesTest.otherPlayerCannotDipWhileSomeoneDrinks`).
5. **Fire used inside the cavern** — burning ground on the island must not spread to the template or soft-lock the skiff dock. Pinned in Task 12 (GameTest `fireHoldsShoreWithoutBlockingDock`).

---

### Task 1: Reference note

**Files:**
- Create: `docs/superpowers/notes/2026-11-24-sea-cave-references.md`

- [ ] **Step 1:** Search the web for real sea stacks (Old Man of Hoy, Ball's Pyramid, Am Buachaille, Faroe stacks) and sea caves (Fingal's Cave, Benagil). View film stills of the Locket cave for reference only; save nothing from them to the repo.
- [ ] **Step 2:** Write the half-page note: silhouette and lean, how strata run, how the foot is wave-cut, scale of a cleft against a person, what a cavern ceiling looks like, three things that make each read as real. End with a list of 8 to 12 named features for the stack and cavern (each with rough local coordinates in the 80 x 90 x 80 frame) that Tasks 2 and 3 place deliberately.
- [ ] **Step 3:** Commit `Note what makes a sea stack and sea cave read`.

### Task 2: Rock pass 1 — silhouette (gated)

**Files:**
- Create: `build_scripts/sea_cave/__init__.py`, `build_scripts/sea_cave/frame.py` (constants), `build_scripts/sea_cave/shape.py`, `build_scripts/sea_cave/audit.py`
- Modify: `build_scripts/generate_biome_structures.py` (`BUILDS['locket_cave']` → `sea_cave.build`; delete old `locket_cave()` and `audit_locket_cave()`; call `sea_cave.audit.problems(blocks)`)

**Interfaces:**
- Produces: `frame.py` constants `SIZE = (80, 90, 80)`, `SEA_Y` (local water-surface Y, chosen so world 63 = local `SEA_Y` with `start_height` from Task 5), `LAKE_Y` (local lake surface, below `SEA_Y`); `shape.solid(x, y, z) -> bool`, `shape.carved(x, y, z) -> 'air'|'water'|None`; `build() -> (blocks, entities)` in `__init__.py`; `audit.problems(blocks) -> list[str]`.

- [ ] **Step 1: Write the audits first** in `audit.py`, each returning a problem string:
  - `cleft_open`: a 3-wide, ≥8-tall opening at the waterline reaches the tunnel.
  - `watertight`: no carved cell is face-adjacent to a cell outside the template or to an unlisted cell.
  - `island_steps`: for every pair of adjacent island-or-lakebed columns within the island's radius + 4, top heights differ by ≤ 1.
  - `route_walkable`: BFS from antechamber floor to the island shore over walkable cells (step ≤ 1, headroom 2), with the lake counted as swimmable; must succeed.
  - `no_primitive`: the cavern's horizontal cross-sections at 5 heights must not fit an ellipse within 1 block on more than 60% of their boundary.
- [ ] **Step 2:** Run `python build_scripts/generate_biome_structures.py`. Expected: `locket_cave` FAILS (no builder output yet).
- [ ] **Step 3:** Implement `shape.py` in a single stone (`minecraft:deepslate`): stack from layered noise plus Task 1's named features, leaning; cleft; tunnel; antechamber pool with lip above `SEA_Y`; arch passage; descending passage; cavern; sloped island continuing under water to the bed.
- [ ] **Step 4:** Run the generator. Expected: `locket_cave ... ok`.
- [ ] **Step 5: Visual gate.** Flat renders (`render_structure.py`) from N, E, S, W, top, and a sea-level view 40 blocks out; textured `cut=x`, `cut=z`, `hide=water`. Save to `build/shots/sea_cave_p1_*.png`. Write the gate check (six skill failure modes + "would a person believe it was shaped?" + "any perfect primitive?") into `docs/superpowers/notes/2026-11-24-sea-cave-references.md` under "Pass 1". Redo Step 3 until every item passes.
- [ ] **Step 6:** Commit `Sea cave pass 1: silhouette`.

### Task 3: Rock pass 2 — strata and features (gated)

**Files:**
- Create: `build_scripts/sea_cave/strata.py`, `build_scripts/sea_cave/features.py`

**Interfaces:**
- Consumes: `shape.solid`, `frame.*`.
- Produces: `strata.block_at(x, y, z) -> str` (block id for a solid cell); `features.apply(blocks)`.

- [ ] **Step 1:** Add audit `palette_contrast` to `audit.py`: no solid cell has more than 2 of its 6 neighbours in a different band, except along band boundaries; and every adjacent band pair is within the 40-luma table in `strata.py`.
- [ ] **Step 2:** Run generator; expected FAIL on `palette_contrast` (single stone has no band table yet).
- [ ] **Step 3:** Implement tilted, warped strata (deepslate, cobbled deepslate, blackstone, basalt, smooth basalt; tuff only as a rare thin band) and the named big features (spires, overhangs, fallen slabs, side ledges).
- [ ] **Step 4:** Generator passes.
- [ ] **Step 5: Visual gate**, textured, same views as Task 2 plus the ten eye-level standpoints from the spec §3, rendered with a matrix helper on `window.__renderer`. Write "Pass 2" check; redo until it passes.
- [ ] **Step 6:** Commit `Sea cave pass 2: strata and features`.

### Task 4: Rock pass 3 — detail, markers, in-game shots, user sheet (hard stop)

**Files:**
- Create: `build_scripts/sea_cave/detail.py`, `build_scripts/sea_cave/markers.py`
- Modify: `src/main/resources/data/hogwartsmagic/structure/locket_cave.nbt` (generated)

**Interfaces:**
- Produces: in the template, `hogwartsmagic:cave_waypoint` blocks (registered in Task 5) with block-entity NBT `{Role: "<role>", Index: n}`, roles: `basin`, `arch` (one per arch, at its centre), `ring`, `dock_shore`, `dock_island`, `path` (Index 0..n in crossing order), `lake` (one cell on the lake surface). Plus the basin, the goblet on the island, and `hogwartsmagic:blood_seal` filling the arch.

- [ ] **Step 1:** Audit `markers_complete`: exactly one each of `basin`, `ring`, `dock_shore`, `dock_island`, `lake`; ≥ 1 `arch`; `path` indices contiguous from 0 with ≥ 3 entries; every consecutive `path` pair has a straight water line of ≥ 2-block clearance; the `dock_island` cell's neighbour toward the island top is a slope step ≤ 1.
- [ ] **Step 2:** Run; expected FAIL.
- [ ] **Step 3:** Implement `detail.py` (dripstone clusters under ceiling lows, rubble at wall feet, wet band at waterlines, kelp and seagrass on the outer base) and `markers.py`.
- [ ] **Step 4:** Generator passes; block census via `block_census.py` shows no loud block over 2% except stone bands and water.
- [ ] **Step 5: Visual gate** (textured, all standpoints). "Pass 3" check.
- [ ] **Step 6: In-game shots.** After Task 5 lands (needs the waypoint block registered), launch per the game skill, `/place structure hogwartsmagic:locket_cave` in ocean at night, `/tp` to each standpoint and screenshot. No key injection while the user is at the PC.
- [ ] **Step 7:** Build a sheet `texture_proposals/sea_cave_sheet.png` of the best renders and shots; send it to the user. **Stop until the user approves the rock.** Feedback loops back to Tasks 2 to 4.
- [ ] **Step 8:** Commit `Sea cave pass 3: detail and markers`.

### Task 5: Placement and layout on the Java side

**Files:**
- Create: `src/main/java/com/hogwartsmagic/block/CaveWaypointBlock.java`, `src/main/java/com/hogwartsmagic/block/entity/CaveWaypointBlockEntity.java`, `src/main/java/com/hogwartsmagic/locket/CaveLayout.java`, `src/test/java/com/hogwartsmagic/locket/CaveLayoutTest.java`
- Modify: `ExampleMod.java` (register block, item-less, block entity), `locket/CaveGeometry.java`, `locket/LocketCaves.java:94-122` (`findBasinFor`), `block/entity/DespairBasinBlockEntity.java` (store layout, `inCave`/`inLake`/`onIsland`/`idleGuardians` via layout), `worldgen/structure/locket_cave.json` (`start_height`), `tags/worldgen/biome/has_structure/locket_cave.json`, blockstate/model JSON for the invisible waypoint
- Test: `CaveLayoutTest`, existing `CaveGeometryTest`

**Interfaces:**
- Produces:
  - `CaveWaypointBlock`: invisible, no collision, not targetable, `RenderShape.INVISIBLE`, survives water (`LiquidBlockContainer` like `HiddenChainBlock`).
  - `CaveWaypointBlockEntity#role(): String`, `#index(): int`.
  - `record CaveLayout(int version, BlockPos basin, List<BlockPos> arches, BlockPos ring, BlockPos dockShore, BlockPos dockIsland, List<BlockPos> path, int lakeSurfaceY, int radius, int down, int up)` with
    `static CaveLayout fromMarkers(BlockPos basin, List<Marker> markers)`, `static CaveLayout legacy(BlockPos basin)`,
    `boolean inCave(BlockPos p)`, `boolean onIsland(BlockPos p)`, `boolean inLake(BlockPos p, boolean isWater)`,
    `CompoundTag save()`, `static CaveLayout load(CompoundTag, BlockPos basin)`; `record Marker(String role, int index, BlockPos pos)`.
  - `CaveGeometry.SEARCH_RADIUS = 48` (max of old 34 and new radius).
  - `DespairBasinBlockEntity#layout(): CaveLayout` (scanned lazily on first server tick from waypoint block entities in chunks within `SEARCH_RADIUS`, then saved).

- [ ] **Step 1: Write failing tests** in `CaveLayoutTest`:
  - `noMarkersMeansLegacyLayout`: `fromMarkers(b, List.of())` has `version == 1` and `inCave`/`onIsland` agree with `CaveGeometry` for a grid of offsets.
  - `markersGiveVersionTwo`: version 2, `path` sorted by index, `lakeSurfaceY` from the `lake` marker.
  - `rotatedMarkersStillDescribeTheSameCave`: the same marker set rotated 90/180/270 about the basin gives `onIsland` true for the rotated island points and `inLake` the same membership.
  - `inLakeNeedsWaterAtOrBelowSurface`: a point above `lakeSurfaceY` or not water is not lake.
  - `saveLoadRoundTrip`.
- [ ] **Step 2:** `./gradlew test --tests "*CaveLayoutTest*"` in hp-verify. Expected FAIL (class missing).
- [ ] **Step 3:** Implement. Version-2 `onIsland` = within the horizontal radius of `dockIsland`→`basin` distance + 2 of the basin and within 4 vertically of it; `radius`/`down`/`up` = bounding box of all markers + 12.
- [ ] **Step 4:** Tests pass; `CaveGeometryTest` still passes.
- [ ] **Step 5:** Set `start_height` so the template's `SEA_Y` lands at world 63 (`absolute: 63 - SEA_Y`); replace the biome tag. `./gradlew runServer` data check in hp-verify: no registry or worldgen errors.
- [ ] **Step 6:** Commit `Read the cave's layout from rotation-safe markers`.

### Task 6: Blood arch

**Files:**
- Modify: `block/BloodSealBlock.java`, its blockstate/models, `build_scripts/generate_cave_block_textures.py` (or a new `generate_sea_cave_textures.py`)

**Interfaces:**
- Produces: `BloodSealBlock.STAGE` `IntegerProperty` 0..3 (0 sealed rock with hairline, 1 smeared, 2 melting, 3 open — replaces the visual use of `OPEN`; keep `OPEN` for collision logic).

- [ ] **Step 1:** GameTest `bloodArchOpensOverOneSecondAndCostsToll` in a new `gametest/SeaCaveGameTests.java`: a mock player right-clicks a seal; health drops by 6; stage reaches 3 within 20 ticks; stage 3 has no collision; after 1200 ticks with no player inside it returns to 0.
- [ ] **Step 2:** Run GameTests in a private worktree (per memory); expected FAIL.
- [ ] **Step 3:** Implement staged melt (scheduled ticks 0→1→2→3 over 20 ticks across the connected arch), placeholder textures, red drip particle (`DRIPPING_LAVA`-style, tinted) when a player is within 4 blocks, client-side `animateTick`.
- [ ] **Step 4:** GameTest passes.
- [ ] **Step 5:** Commit `Blood arch melts from bare rock`.

### Task 7: Iron ring and chain

**Files:**
- Create: `block/CaveRingBlock.java`, `client/renderer/CaveChainRenderer.java` (block entity renderer)
- Modify: `ExampleMod.java`, `locket/LocketCaves.java` (Revelio hook reveals the chain from the ring), `entity/CaveBoatEntity.java` (`summonAtShore` takes the layout)

**Interfaces:**
- Consumes: `CaveLayout.ring`, `dockShore`.
- Produces: `CaveRingBlock` with block entity `CaveRingBlockEntity`: `boolean revealed`, `int haulTicks` (0..60), synced; `void reveal(int ticks)`, `void pull(ServerPlayer)`. Pull while revealed runs a 60-tick haul (bubbles at `dockShore`), then calls `CaveBoatEntity.rise(ServerLevel, CaveLayout)`.

- [ ] **Step 1:** GameTest `pullingRingRaisesSkiff`: reveal, pull, after 60 ticks plus rise time exactly one skiff exists at `dockShore`; a second pull does nothing.
- [ ] **Step 2:** Run; FAIL.
- [ ] **Step 3:** Implement. Chain rendered as vanilla chain-model links from ring down to the water, green emissive tint while revealed, shortening with `haulTicks`. Right-click on an unrevealed ring reveals it for 200 ticks.
- [ ] **Step 4:** Pass. In-game screenshot of the chain at the shore.
- [ ] **Step 5:** Commit `Iron ring and a chain you can see`.

### Task 8: The skiff

**Files:**
- Create: `client/model/CaveSkiffModel.java` (Blockbench export), `locket/SkiffRules.java`, `src/test/java/com/hogwartsmagic/locket/SkiffRulesTest.java`
- Modify: `entity/CaveBoatEntity.java` (rise, waypoint path, bob, wake, latching hooks), `client/renderer/CaveBoatRenderer.java`

**Interfaces:**
- Produces:
  - `SkiffRules.RISE_TICKS = 60`, `SPEED = 0.09`, `LATCH_SLOW = 0.3f` per latched Inferius, `MAX_LATCHED = 2`, `EMPTY_LIFETIME = 2400`.
  - `static Vec3 steer(Vec3 pos, List<Vec3> path, int nextIndex, double speed)` (Catmull-Rom through path points), `static double speedWith(int latched)`, `static boolean riderGoneReturnsToShore(boolean riderPresent, boolean atIsland)`.
  - `CaveBoatEntity.rise(ServerLevel, CaveLayout)`, `int latchedCount()`, `boolean latch(InferiusEntity)`, `void unlatch(InferiusEntity)`; synced `DATA_RISE` int for the client's rise and bob.

- [ ] **Step 1: Failing tests** `SkiffRulesTest`: `steerFollowsCurveNotChord` (midpoint of a 3-point bent path is off the straight line), `speedWithLatchedSlowsAndFloors` (0 → `SPEED`, 2 → `SPEED*0.4`), `riderGoneReturnsToShore` (empty at island → stays docked; empty mid-lake → goes back to shore).
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement rules and entity. Rise: bow first over `RISE_TICKS`. Path: `dockShore` → `path[0..n]` → `dockIsland` and reverse. Bob ±0.06 at 0.15 Hz client-side; wake particles behind at speed. Pale-shape particles drift 1 to 3 blocks under the surface near the hull. Grounds at `dockIsland`.
- [ ] **Step 4:** Model in Blockbench (narrow skiff, prow lantern, green-tinged wood) via the Blockbench MCP pipeline; placeholder texture to the sheet. Start the Blockbench server first; if it will not connect, keep the vanilla model and flag it.
- [ ] **Step 5:** GameTest `skiffCrossesAndReturns` on the real template at rotations 0 and 90: rider reaches `dockIsland` and back within a time limit.
- [ ] **Step 6:** Tests pass. Commit `A skiff that rises and follows the lake`.

### Task 9: Goblet and basin

**Files:**
- Create: `item/CaveGobletItem.java`, `locket/GobletRules.java`, `src/test/java/com/hogwartsmagic/locket/GobletRulesTest.java`
- Modify: `block/CrystalGobletBlock.java` (pick up into `CaveGobletItem`), `ExampleMod.java` (`CRYSTAL_GOBLET_ITEM` becomes `CaveGobletItem`), `block/DespairBasinBlock.java` (`LEVEL` `IntegerProperty` 0..6 replaces visual use of `STAGE`/`LOCKET`), `block/entity/DespairBasinBlockEntity.java` (`onUse` no longer drinks; new `dip(ServerPlayer, ItemStack)` and `drink(ServerPlayer)`)

**Interfaces:**
- Produces:
  - `CaveGobletItem` with data component `hogwartsmagic:goblet_fill` = `EMPTY|POTION|WATER`; `useDuration` 32, `UseAnim.DRINK`; `finishUsingItem` calls `basin.drink(player)` for `POTION`, `LocketCaves.cureThirst` + ambush for `WATER`.
  - `GobletRules.mayDip(UUID drinker, UUID who, int drinks) -> boolean`; `GobletRules.leavesCave(boolean inCave) -> boolean` (crumble).
  - `DespairBasinBlockEntity#dip(ServerPlayer, ItemStack) -> boolean`, `#drink(ServerPlayer)` (body = today's drink branch minus the chat whisper, plus `applyDespairStage`).

- [ ] **Step 1: Failing tests** `GobletRulesTest`: `otherPlayerCannotDipWhileSomeoneDrinks`, `drinkerCanDipUntilSix`, `nobodyCanDipLootedBasin`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement. Basin `LEVEL` drops one per cup (6 full → 0 dry); Locket model at 0 until taken. Taking the Locket is a right-click with an empty hand. A goblet outside `layout().inCave` crumbles (particles) and a new goblet block is set beside the basin.
- [ ] **Step 4:** GameTest `legacyBasinAcceptsGoblet` (a basin with no markers around it and a goblet beside it accepts a dip and a drink) and `sixCupsShowLocket`.
- [ ] **Step 5:** Pass. Commit `Drink from the goblet, watch the basin drain`.

### Task 10: Despair on screen

**Files:**
- Create: `spell/DespairEffect.java` (hidden mob effect), `client/DespairOverlay.java`, `mixin/client/DespairGuiMixin.java`, `client/sounds/` whisper sounds (ElevenLabs pipeline, envelope-trimmed)
- Modify: `ExampleMod.java` (register `DESPAIR` effect), `DespairBasinBlockEntity#drink`, mixin json, `locket/DespairRules.java` (remove `WHISPERS`, add `stageFor(int cup) -> int`, `kneelTicks(int cup)`)

**Interfaces:**
- Produces: `DESPAIR` effect, amplifier = stage 0..5, duration refreshed per cup (600 ticks). Client reads the local player's amplifier only.

- [ ] **Step 1:** Update `DespairRulesTest`: `stageRisesWithCup` (cup 1 → 0, cup 6 → 5), `kneelOnlyAtFiveAndSix` (0 for cups 1 to 4, > 0 for 5 and 6); delete whisper tests.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement. Overlay modelled on `BasiliskGazeOverlay` + `BasiliskGazeGuiMixin`: vignette darkening and edge blur by stage; whispers as positional sounds near the player; small view shake via `Fx.shake(level, pos, strength, radius, ticks)` with strength 0.15 × stage; kneel = forced crouch pose and `SLOWNESS` 255 for `kneelTicks`. Chat whispers removed.
- [ ] **Step 4:** Tests pass; in-game screenshots at cups 1, 3, 6.
- [ ] **Step 5:** Commit `Despair is felt, not read`.

### Task 11: Thirst through the goblet

**Files:**
- Modify: `locket/LocketCaves.java` (`onRightClickItem`: goblet on lake water fills `WATER`), `item/CaveGobletItem.java`, `spell/AquaStream.java` (water landing within 3 blocks of a thirsty player, or on a goblet, vanishes with a puff)

- [ ] **Step 1:** GameTest `aquaIntoGobletVanishes` and `gobletInLakeStartsAmbush` (thirsty player dips the goblet in the lake → `WATER`; drinking it cures and `isAmbushActive()`).
- [ ] **Step 2:** FAIL. **Step 3:** Implement. Bare-hand and bottle paths stay.
- [ ] **Step 4:** Pass. Commit `Only the lake slakes the thirst`.

### Task 12: Inferi that reach you

**Files:**
- Modify: `entity/InferiusEntity.java`, `locket/InferiusRules.java`, `block/entity/DespairBasinBlockEntity.java` (spawn ring near the island shore; hands-first rise), `entity/CaveBoatEntity.java` (latch)
- Test: `InferiusRulesTest`, `SeaCaveGameTests`

**Interfaces:**
- Consumes: `CaveLayout`, `CaveBoatEntity#latch/unlatch/latchedCount`.
- Produces: `InferiusRules.LAND_PULL_PER_TICK = 0.02`, `LATCH_DAMAGE = 1f`, `LATCH_DAMAGE_INTERVAL = 40`; `static boolean pastArch(double distFromBasin, double archDist)`; `static boolean latchReleasedWhenRiderGone(boolean riderPresent, boolean hit, boolean burning)`.

- [ ] **Step 1: Failing tests** `InferiusRulesTest`: `landPullWeakerThanWater`, `pastArchMeansTurnBack`, `latchReleasedWhenRiderGone`.
- [ ] **Step 2: Failing GameTests** on the real template: `inferiusReachesIslandFromLake` (spawned at lake edge, player on island top, reaches melee range within 400 ticks — must fail on a sheer island), `fireHoldsShoreWithoutBlockingDock` (fire on the shore makes Inferi recoil; `dockIsland` stays clear and fire does not burn template blocks).
- [ ] **Step 3:** Implement: grab drags on land at `LAND_PULL_PER_TICK` toward the nearest lake water, holds under once in water (existing diminishing returns); ambush spawns in water within 3 blocks of the island shore and rises hands first (new animation state, event byte 66); swimming Inferi within 2 blocks of a moving skiff latch (max 2); any hit or burning unlatches; cave-bound Inferi farther from the basin than the arch drop their target and return.
- [ ] **Step 4:** Tests pass. Commit `Inferi climb the shore, drag, and cling to the skiff`.

### Task 13: Texture sheet and the old dressing

**Files:**
- Create: `build_scripts/generate_sea_cave_textures.py`, `texture_proposals/sea_cave_textures.png`
- Modify: textures for blood arch stages, basin levels, goblet block and item (16 x 16 per the item-texturing skill), ring, skiff

- [ ] **Step 1:** Generate the candidates; build a before/after sheet; send to the user. **Stop for picks.**
- [ ] **Step 2:** Apply the picks. Confirm `ConcealedRockBlock` and `HiddenChainBlock` still register and work in old caves (GameTest `legacyChainStillRaisesBoat`).
- [ ] **Step 3:** Commit `Sea cave textures`.

### Task 14: Play-through, tuning, wiki

**Files:**
- Modify: `MOD_FEATURES_WIKI.md` (Locket cave, Inferius, Memory of the Cave entries), tunables in the rule classes

- [ ] **Step 1:** Dedicated-server boot with a cave generated in ocean (memory: dedicated server harness); no errors.
- [ ] **Step 2:** Full client run by command at night: Cave Map → stack → cleft → arch → ring → skiff → six cups → Locket → thirst → lake → ambush → fire → escape → looted cave skipped by a second map. Screenshot each step.
- [ ] **Step 3:** Tune; update the wiki; note that the `locket_cave` Pensieve memory still shows the old cave.
- [ ] **Step 4:** Commit `Sea cave: play-through and wiki`, then hand to the user for their in-game pass.
