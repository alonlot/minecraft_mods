# Pensieve and Story Memories Implementation Plan (plan 1 of 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Pensieve block that plays hand-written cinematic story memories (scene files Claude authors) with a swallow-fall-build entry, a void-floating set, puppet actors with skins, limb animation, spells and a directed camera, a helpless body left at the basin, shared viewing, and three shipped tutorials: the Locket cave, the Chamber, and the Summoning.

**Architecture:** The server owns the session: it moves viewers into a flat empty `hogwartsmagic:memory` dimension (seamlessly, the Diagon Alley way, so there is no loading screen), builds the scene's set there block by block outward from the centre, applies timed block events, leaves a body entity at the basin and forwards its damage. Every viewer's client receives the scene JSON and the session's start game time, and runs everything visual locally and deterministically from the same clock: actors (client-only surrogate entities rendered by hand), limb poses, spell and effect visuals, words, sounds, the directed camera, the void fog/sky and the transitions.

**Tech Stack:** Forge 51 / Minecraft 1.21 / Java 21, Mixin, Gson, JUnit 5.10 (pure tests, no Minecraft bootstrap), Forge GameTest, GLSL post shader, Python build scripts for textures and sets.

**Spec:** `docs/superpowers/specs/2026-10-16-pensieve-design.md`

**Out of scope here (plan 2, "Memory capture"):** the rolling 60-second recorder, the capture gesture, Empty Memory Phials and recorded player memories, the free ghost camera, snap-to-owner, replaying real chat, and silver ghosts of other viewers in a free-camera memory. Plan 2 converts a recording into the same `SceneDef` this plan plays, so everything here is built to be reused by it.

## Global Constraints

- Every authoritative action server-side; no client-only class referenced from common/server code; client code reached only through `ClientSideBridge.handlePacket(action, payload)` → `client/ClientRuntimeBridge`. Must boot on a dedicated server (`./gradlew runServer`).
- Never run gradle while the dev client is running (rewrites `build/sourcesSets/main`, client throws ClassNotFoundException).
- Big block placement spread over ticks; never place a whole set in one tick.
- No `switch` on enums in render/model/client code (synthetic `$1` switch-map class has failed to load in this dev env). Use a field or lambda on the enum constant, or `if` chains.
- `broadcastEntityEvent` bytes 21/35/63 crash the client; use 61/62/64/66/67 if one is needed.
- Custom screens use `renderTransparentBackground`, never the blur backdrop.
- HUD drawing goes in a mixin at `Gui#render(GuiGraphics, DeltaTracker)` TAIL (Forge 51 has no HUD event).
- `RenderLevelStageEvent` world rendering starts from an identity `PoseStack`, not `event.getPoseStack()`.
- Packets go on `SpellPackets.CHANNEL`; bump `networkProtocolVersion(9)` to `10` once.
- Datapack folders are singular: `structure/`, `loot_table/`, `recipe/`, `advancement/`.
- Commit after each task; the user pushes (a session cannot push).
- Spec values: body yank when health falls **below a third** of max; viewers cannot affect anything; story memory camera is **directed**; story memory items are **not consumed** by watching; only the pourer or an op can take a memory out, anyone on the public Mini Hogwarts basin while nobody is inside; Memory of the Cave still gives the Cave Map afterwards and drinking it still works.

## Review Focus

1. **Server stop or crash, or the viewer disconnects, mid-memory** → on next login the player stands at their body's position in the original dimension, never in `hogwartsmagic:memory`. Pinned by `PensieveGameTests.loginInsideMemoryReturnsPlayer` (Task 6).
2. **The basin is broken, or its memory taken out, while people are inside** → every viewer gets the exit transition and returns; no body entities remain. Pinned by `PensieveGameTests.breakingBasinEndsSession` (Task 6).
3. **A second player dives into a basin that is already playing** → they join the same timeline at its current moment, not from the start. Pinned by `SessionClockTest.joinerSeesCurrentMoment` (Task 2) and `PensieveGameTests.secondViewerJoinsSameSession` (Task 6).
4. **A scene names something that does not exist** (entity, spell, sound, block, item, template, body part, animation, skin) → a named error from the validator or the preview, never a crash or a silent no-op. Pinned by `SceneValidatorTest` (Task 3) and the client part check (Task 10).
5. **Two basins play at the same time, and a region is reused after a memory ends** → the sets never overlap and a reused region has no leftover blocks. Pinned by `RegionAllocatorTest` (Task 4) and `PensieveGameTests.regionIsClearedOnRelease` (Task 4).

---

## File map

Common/server, `src/main/java/com/hogwartsmagic/`:
- `pensieve/scene/` (pure, no Minecraft imports): `Ease`, `Keyframe`, `Track`, `PartTrack`, `SceneDef` (+ nested records), `SceneParser`, `SceneException`, `CameraKey`, `CameraTrack`, `CameraPose`, `SceneNames`, `SceneValidator`, `SessionClock`.
- `pensieve/`: `PensieveDimension`, `RegionAllocator`, `BuildOrder`, `SetBuilder`, `MemoryRegistry`, `PensieveBlock`, `PensieveBlockEntity`, `PensieveMemory`, `StoryMemoryItem`, `BasinAccess`, `PensieveSessions`, `Session`, `ExitReason`, `YankRule`, `PensieveBodyEntity`, `PensieveEvents`, `PensieveCommand`.
- `pensieve/net/`: `PensieveEnterPacket`, `PensieveStartPacket`, `PensieveExitPacket`, `PensieveThudPacket`, `PensieveLeavePacket`.
- `util/SeamlessMove` (extracted from `worldgen/DiagonAlleyPortal.cross`).

Client, `src/main/java/com/hogwartsmagic/client/pensieve/`: `PensieveClientState`, `PensieveCamera`, `PensieveOverlay`, `PensieveVoid`, `PensievePost`, `MemoryActors`, `ActorRuntime`, `ActorSkins`, `ModelPartIndex`, `ActorPoser`, `ActorAnimationLibrary`, `SpellVisuals`, `EffectVisuals`, `MemoryWords`, `MemorySounds`, `PensieveBodyRenderer`, `PensieveBasinRenderer`.

Mixins (`src/main/java/com/hogwartsmagic/mixin/client/`, listed in `src/main/resources/hogwartsmagic.mixins.json` `client`): `PensieveCameraMixin` (Camera#setup TAIL), `PensieveGuiMixin` (Gui#render TAIL), `PensieveLevelRendererMixin` (cancel sky, rain/snow, clouds while active), `PensieveGameRendererMixin` (post chain after level render), `ActorPoseMixin` (LivingEntityRenderer#render after `setupAnim`), `ActorSkinMixin` (AbstractClientPlayer#getSkin), `ModelPartAccessor` (ModelPart `children`).

Resources: `data/hogwartsmagic/dimension/memory.json`, `worldgen/biome/memory_void.json`, `data/hogwartsmagic/memories/{locket_cave,chamber,summoning}.json`, block/item models and textures, `assets/hogwartsmagic/textures/memory_skin/*.png`, `assets/hogwartsmagic/shaders/post/pensieve.json` + `shaders/program/pensieve.{json,fsh}`, sounds, lang, recipe, loot entries.

Tests: `src/test/java/com/hogwartsmagic/pensieve/*Test.java` (pure), `src/main/java/com/hogwartsmagic/gametest/PensieveGameTests.java` (run with `./gradlew runGameTestServer`).

---

### Task 1: Scene model, parser and keyframe sampling (pure)

**Files:**
- Create: `pensieve/scene/Ease.java`, `Keyframe.java`, `Track.java`, `PartTrack.java`, `SceneDef.java`, `SceneParser.java`, `SceneException.java`
- Test: `src/test/java/com/hogwartsmagic/pensieve/SceneParserTest.java`, `TrackTest.java`

**Interfaces:**
- Produces:
  - `enum Ease { LINEAR, IN, OUT, IN_OUT; float apply(float x) }`: cubic in/out; implement with a per-constant lambda field, **no switch**. `static Ease parse(String)` accepting `"linear","in","out","in_out"`; default `IN_OUT`.
  - `record Keyframe(float t, float[] v, Ease ease)`: `ease` shapes the segment that *ends* at this key.
  - `final class Track { Track(List<Keyframe>); float[] sample(float t); boolean isEmpty(); static final Track EMPTY }`: before first key → first value; after last → last; between → per-component lerp with `next.ease.apply(frac)`.
  - `record PartTrack(Track rot, Track offset)`: rotation in degrees `[x,y,z]`, offset in model pixels.
  - `record SceneDef(String id, String title, int length, int radius, SetDef set, Map<String, Map<String, float[]>> poses, List<ActorDef> actors, List<EventDef> events, List<WordDef> words, CameraTrack camera, String endAction)`, with nested records:
    - `SetDef(List<TemplateRef> templates, List<Fill> fills, List<Disk> disks, List<FeatureRef> features, long timeOfDay, int buildTicks)`; `TemplateRef(String id, int[] at)`, `Fill(String block, int[] from, int[] to)`, `Disk(String block, int[] center, int radius)`, `FeatureRef(String id, int[] at)`.
    - `ActorDef(String id, String type, SkinRef skin, String name, Map<String,String> items, Track path, Track look, Map<String, PartTrack> limbs, List<Cue> poses, List<Cue> anims, List<ActionCue> actions)`: `type` is an entity id or `"player"`; `look` values are `[yaw, pitch]`.
    - `SkinRef(String texture, String username, boolean slim)`.
    - `Cue(float t, String name, float blend)`.
    - `ActionCue(float t, String action, String spell, Target target, String item)`: actions `swing, sneak, stand, hurt, die, vanish, appear, stone, unstone, cast, use_item, hold`.
    - `Target(String actor, float[] point)`.
    - `EventDef(float t, String kind, String id, int[] at, float volume, float pitch, int count, float[] spread, int duration)`: kinds `block, sound, particles, effect`.
    - `WordDef(float t, String by, String text, boolean narration, int duration)`.
  - `final class SceneParser { static SceneDef parse(String id, JsonObject json) throws SceneException }`: times accept a number (ticks) or a string `"2.5s"` (×20); unknown top-level keys are an error; defaults `radius 40`, `buildTicks 60`, `timeOfDay 18000`, `title = id`.
  - `final class SceneException extends Exception { SceneException(String path, String message); String path() }`: `getMessage()` is `"<path>: <message>"`.
- Consumes: nothing earlier. `SceneDef.camera` is typed `CameraTrack` from Task 2; create that class here as an empty shell with `static final CameraTrack EMPTY` and leave `camera` = `EMPTY`; Task 2 fills it in and wires parsing.

- [ ] **Step 1: Write the failing tests**
  - `TrackTest.holdsEndsAndInterpolates`: keys `t0=[0]`, `t20=[10]` LINEAR → `sample(-5)==[0]`, `sample(10)==[5]`, `sample(40)==[10]`.
  - `TrackTest.easeShapesTheSegmentEndingAtTheKey`: same keys with IN on the second → `sample(10)[0] < 5`.
  - `SceneParserTest.parsesMinimalScene`: `{"length":100,"set":{},"actors":[]}` → `length()==100`, `radius()==40`, `set().buildTicks()==60`, `title()=="t"` for id `"t"`.
  - `SceneParserTest.secondsStringsBecomeTicks`: `"length":"2.5s"` → `50`.
  - `SceneParserTest.actorTracksAndCues`: one player actor with `path` 2 keys, `limbs.right_arm.rot` 2 keys, `poses:[{"t":5,"name":"wand_raised"}]`, `actions:[{"t":10,"do":"cast","spell":"stupefy","target":"actor:b"}]` → parsed values, `target.actor()=="b"`.
  - `SceneParserTest.unknownKeyIsNamedError`: `{"length":1,"lenght":2}` → `SceneException` whose `path()` is `"lenght"`.
  - `SceneParserTest.badKeyframeNamesItsPath`: `actors[0].path[1]` missing `pos` → `path()=="actors[0].path[1].pos"`.
- [ ] **Step 2: Run them, expect FAIL (classes missing).** `./gradlew test --tests "com.hogwartsmagic.pensieve.*"`
- [ ] **Step 3: Implement the classes above.** Key shapes in JSON: `"path":[{"t":0,"pos":[x,y,z],"ease":"in_out"}]`, `"look":[{"t":0,"yaw":0,"pitch":0}]`, `"limbs":{"head":{"rot":[{"t":0,"v":[x,y,z]}],"offset":[...]}}`, `"skin":{"texture":"hogwartsmagic:regulus"}` or `{"username":"Notch","slim":false}`, `"items":{"mainhand":"hogwartsmagic:student_wand","head":"..."}`, targets `"actor:<id>"` or `[x,y,z]`.
- [ ] **Step 4: Run the tests, expect PASS.**
- [ ] **Step 5: Commit** `Add the Pensieve scene model and parser`.

### Task 2: Directed camera track and session clock (pure)

**Files:**
- Create: `pensieve/scene/CameraKey.java`, `CameraTrack.java`, `CameraPose.java`, `SessionClock.java`
- Test: `src/test/java/com/hogwartsmagic/pensieve/CameraTrackTest.java`, `SessionClockTest.java`

**Interfaces:**
- Produces:
  - `record CameraKey(float t, float[] pos, float[] lookAt, String lookAtActor, float fov, float roll, Ease ease, boolean cut)`.
  - `record CameraPose(double x, double y, double z, float yaw, float pitch, float roll, float fov)`.
  - `final class CameraTrack { static final CameraTrack EMPTY; static CameraTrack parse(JsonArray) throws SceneException; CameraPose sample(float t, Function<String, float[]> actorEye); boolean isEmpty() }`: position, fov and roll interpolate with the ending key's ease; a key with `cut:true` jumps (holds the previous segment until `t`, then snaps); look direction = from interpolated position toward interpolated look target (point, or the named actor's eye position from `actorEye`); yaw/pitch in Minecraft convention (yaw 0 = +Z, pitch positive = down). Default fov `70`.
  - `final class SessionClock { static float elapsed(long startGameTime, long gameTime, float partialTick); static boolean finished(long startGameTime, long gameTime, int length) }`: `elapsed = gameTime - startGameTime + partialTick`, clamped to ≥ 0.
- Consumes: `Ease`, `SceneException` (Task 1).

- [ ] **Step 1: Write the failing tests**
  - `CameraTrackTest.interpolatesPositionAndFov`: keys `t0 pos[0,0,0] fov70`, `t20 pos[20,0,0] fov50` LINEAR → `sample(10).x()==10`, `fov()==60`.
  - `CameraTrackTest.cutSnapsInsteadOfGliding`: second key `cut:true` at `t20` → `sample(19).x()==0`, `sample(20).x()==20`.
  - `CameraTrackTest.looksAtTarget`: pos `[0,0,0]`, lookAt `[0,0,10]` → `yaw()==0`, `pitch()==0`; lookAt `[10,0,0]` → `yaw()==-90`.
  - `CameraTrackTest.looksAtActor`: `lookAtActor "a"`, `actorEye` returns `[0,-10,0]` → `pitch()==90`.
  - `SessionClockTest.joinerSeesCurrentMoment`: start 1000, now 1150, partial 0.5 → `150.5`.
  - `SessionClockTest.finishedAtLength`: start 0, length 100 → `finished(0,99,100)` false, `finished(0,100,100)` true.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**, and wire `camera` parsing into `SceneParser` (`"camera":[{"t":0,"pos":[..],"look_at":[..] | "actor:<id>","fov":70,"roll":0,"ease":"in_out","cut":false}]`).
- [ ] **Step 4: Run, expect PASS (Task 1 tests still pass).**
- [ ] **Step 5: Commit** `Add the Pensieve camera track and session clock`.

### Task 3: Validation, the memory registry, and the authoring commands

**Files:**
- Create: `pensieve/scene/SceneNames.java`, `SceneValidator.java`, `pensieve/MemoryRegistry.java`, `pensieve/PensieveCommand.java`
- Modify: `assignment/AssignmentEvents.java` is the precedent; register `MemoryRegistry` from a new `@SubscribeEvent onAddReloadListeners` in `pensieve/PensieveEvents.java` (create, Forge bus); `ExampleMod.onRegisterCommands` (~L1122) calls `PensieveCommand.register(dispatcher)`; `Config.java` adds `pensieveDevSceneDir` (string, default `""`).
- Test: `src/test/java/com/hogwartsmagic/pensieve/SceneValidatorTest.java`, `SceneDataTest.java`

**Interfaces:**
- Produces:
  - `record SceneNames(Set<String> entityTypes, Set<String> spells, Set<String> sounds, Set<String> blocks, Set<String> items, Set<String> templates, Set<String> features, Set<String> particles, Set<String> skins)`, plus constants `EFFECTS = Set.of("summoning_ritual","lightning","dark_mark")`, `END_ACTIONS = Set.of("cave_map")`, `ACTIONS` (Task 1 list), `SPELL_VISUALS` (filled by Task 11; until then every beam spell id).
  - `final class SceneValidator { static List<String> validate(SceneDef, SceneNames) }`: one message per problem, each prefixed with its JSON path, e.g. `actors[1].type: unknown entity 'hogwartsmagic:basilsk'`; also checks: actor ids unique, `target actor:<id>` and `by` refer to existing actors, cue names exist in `poses` or are known pose presets, cast actions name a spell in `SPELL_VISUALS`, every time `0 ≤ t ≤ length`.
  - `final class MemoryRegistry extends SimpleJsonResourceReloadListener` (folder `"memories"`): stores parsed `SceneDef` plus the raw JSON string per id; `static Optional<SceneDef> get(String id)`, `static Optional<String> rawJson(String id)`, `static Set<String> ids()`; bad files are logged with the `SceneException` message and skipped. `record LoadedScene(SceneDef def, String json)`; `static Optional<LoadedScene> loadForPreview(String id) throws SceneException`: if `Config.pensieveDevSceneDir` is non-empty and `<dir>/<id>.json` exists, parse that file from disk (so a scene can be edited and re-previewed without a build), else the registry's copy. Files whose name starts with `_` are not scenes and are skipped everywhere.
  - `PensieveCommand` (op level 2): `/pensieve validate [id]` (validates one or all against live registries: `BuiltInRegistries.ENTITY_TYPE/BLOCK/ITEM/SOUND_EVENT/PARTICLE_TYPE`, `SpellType` ids, the server's structure templates and configured features, skins from `assets/hogwartsmagic/textures/memory_skin/` known to the server via a list file `data/hogwartsmagic/memories/_skins.json`), prints each message in chat or `"<id>: OK"`; `/pensieve list`. (`preview` is added in Task 6.)
- Consumes: Tasks 1–2.

- [ ] **Step 1: Write the failing tests**
  - `SceneValidatorTest.unknownEntityIsNamed`: actor type `hogwartsmagic:basilsk` with names containing only `hogwartsmagic:basilisk` → exactly one message, starting `actors[0].type`.
  - `SceneValidatorTest.unknownSpellBlockSoundTemplate`: one bad each → four messages with their paths.
  - `SceneValidatorTest.danglingActorTarget`: cast target `actor:ghost` → message names `actors[0].actions[0].target`.
  - `SceneValidatorTest.timeOutsideLength`: event `t` 120 with length 100 → message.
  - `SceneValidatorTest.validSceneHasNoMessages`.
  - `SceneDataTest.everyShippedSceneParsesAndValidates`: for each `src/main/resources/data/hogwartsmagic/memories/*.json` (skipping `_skins.json`), parse, then validate against names harvested from disk, the way `AssignmentDataTest` does: spells from `SpellType.java`, entity ids from `ModEntities.java` register calls plus a fixed vanilla allow-list (`minecraft:zombie, skeleton, villager, armor_stand, bat, spider, cave_spider, wither_skeleton, evoker, vindicator, witch, horse, wolf, cat, fox, sheep, cow, pig, chicken, squid, glow_squid, salmon, cod, lightning_bolt`) plus `player`, templates from `data/hogwartsmagic/structure/*.nbt`, skins from `_skins.json`, block/item/sound/particle ids accepted if they are `minecraft:` or appear in the mod's registration sources or `sounds.json`. Passes trivially while no scenes exist.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run tests, expect PASS; then `./gradlew runServer` with `stop` piped in and confirm `Done (` and no `/ERROR` mentioning `memories`.**
- [ ] **Step 5: Commit** `Add Pensieve scene validation, the memory registry and /pensieve validate`.

### Task 4: The memory dimension, seamless moves, regions and set building

**Files:**
- Create: `data/hogwartsmagic/dimension/memory.json` (copy of `diagon_alley.json` with biome `hogwartsmagic:memory_void`), `data/hogwartsmagic/worldgen/biome/memory_void.json` (no precipitation, sky/fog/water colours `0`, empty spawners, no features), `pensieve/PensieveDimension.java`, `util/SeamlessMove.java`, `pensieve/RegionAllocator.java`, `pensieve/BuildOrder.java`, `pensieve/SetBuilder.java`
- Modify: `worldgen/DiagonAlleyPortal.java:347` `cross(...)` delegates its move to `SeamlessMove.move` (no behaviour change); add the biome to whatever list registers `diagon_alley` biome tags if one exists.
- Test: `src/test/java/com/hogwartsmagic/pensieve/RegionAllocatorTest.java`, `BuildOrderTest.java`; gametests in `gametest/PensieveGameTests.java` (create, `@GameTestHolder(ExampleMod.MODID)`, template `hogwartsmagic:gametest/empty3`).

**Interfaces:**
- Produces:
  - `PensieveDimension { static final ResourceKey<Level> KEY /* hogwartsmagic:memory */; static ServerLevel level(MinecraftServer); static boolean isMemory(Level) }`.
  - `SeamlessMove { static void move(ServerPlayer, ServerLevel target, double x, double y, double z, float yRot, float xRot) }`: the exact steps of `DiagonAlleyPortal.cross` (`removePlayerImmediately(CHANGED_DIMENSION)`, `revive`, `setServerLevel`, `addDuringTeleport`, `connection.teleport`, `resetPosition`), so the client never shows a loading screen.
  - `RegionAllocator { int acquire(); void release(int slot); static int[] originFor(int slot) }`: lowest free slot; `originFor(n) = {n * 1024, 96, 0}`.
  - `BuildOrder { static List<int[]> sortOutward(List<int[]> positions, int[] center) }`: by squared horizontal distance, then y ascending; stable.
  - `SetBuilder(ServerLevel level, BlockPos origin, SetDef set)`: `int total()`, `boolean advance(int budget)` (true when done; places templates (blocks only, no entities) via `StructureTemplateManager.get(id)` palette, then fills, disks, features, all in `BuildOrder` order around `origin`), `List<BlockPos> placed()`, `static ClearJob clear(ServerLevel, List<BlockPos>)` with `boolean advance(int budget)` setting air with flags `2 | 16` (no neighbour updates, no drops). `advance` uses flags `2 | 16` too so nothing ticks, flows or drops. Budget per tick: config `pensieveBlocksPerTick` default `4000`.
- Consumes: `SetDef` (Task 1).

- [ ] **Step 1: Write the failing tests**
  - `RegionAllocatorTest.reusesLowestFreedSlot`: acquire 0,1,2; release 1; acquire → 1.
  - `RegionAllocatorTest.regionsNeverOverlap`: `originFor(1)[0] - originFor(0)[0] >= 1024` (set radius cap is 200, checked by the validator: add `radius > 200` as a validator message in this task).
  - `BuildOrderTest.centreFirstThenRings`: positions at distance 3, 0, 1 → order 0, 1, 3.
  - Gametest `setBuilderPlacesFillsInTheMemoryDimension`: build a `SetDef` with one `Fill` of stone 3×1×3 at a fresh region; advance to completion; assert the 9 blocks are stone in `PensieveDimension.level`.
  - Gametest `regionIsClearedOnRelease`: after the above, run `clear` to completion; all 9 are air.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** Then confirm the Diagon Alley entry still works by reading `DiagonAlleyPortal` after the refactor; it must call `SeamlessMove.move` with the same arguments it used before.
- [ ] **Step 4: `./gradlew test` and `./gradlew runGameTestServer`, expect PASS; `runServer` boot shows the `hogwartsmagic:memory` level loaded with no `/ERROR`.**
- [ ] **Step 5: Commit** `Add the memory dimension, seamless moves and the Pensieve set builder`.

### Task 5: The Pensieve block, story memory items, pouring and ownership

**Files:**
- Create: `pensieve/PensieveBlock.java`, `PensieveBlockEntity.java`, `PensieveMemory.java`, `StoryMemoryItem.java`, `BasinAccess.java`; `data/hogwartsmagic/recipe/pensieve.json`; loot additions
- Modify: `ExampleMod.java`: register `PENSIEVE` block (stone, strength 3, light 6, non-occluding), its `BlockItem`, `PENSIEVE_BLOCK_ENTITY`, items `CHAMBER_MEMORY` and `SUMMONING_MEMORY` (`StoryMemoryItem`, stacks to 1); add all three to `MAIN_TAB`. `item/CaveMemoryItem.java` implements `PensieveMemory` (scene `locket_cave`) and gains `public static boolean grantCaveMap(ServerPlayer)` holding today's map-giving code (the drink calls it; behaviour unchanged). `worldgen/MiniHogwartsManager.java` `furnish()` (~L306) places a public Pensieve at `floorSpotNear(<library centroid>, 6)` using the existing library landmark from `analyze()`. Loot: `loot_table/chests/death_eater_camp.json` adds `summoning_memory` weight equal to the cave memory's 15% entry; `loot_table/entities/wizard.json` adds `summoning_memory` 2% on player kill (same shape as the cave entry); the Mini Hogwarts library chest (found via `placeChest` in `furnish()`) and `chamber_hall` chests get `chamber_memory` at 1 in 7.
- Test: `src/test/java/com/hogwartsmagic/pensieve/BasinAccessTest.java`; gametests in `PensieveGameTests`.

**Interfaces:**
- Produces:
  - `interface PensieveMemory { String sceneId(ItemStack stack); default boolean consumedOnWatch() { return false; } }`.
  - `StoryMemoryItem(Properties, String sceneId) implements PensieveMemory`.
  - `PensieveBlockEntity`: `ItemStack memory()`, `UUID pourer()`, `boolean publicBasin()`, `void pour(ItemStack, UUID)`, `ItemStack takeOut()`; saves/loads all three; `getUpdatePacket`/`getUpdateTag` sync `memory` so the client can swirl brighter (Task 12).
  - `BasinAccess { static boolean canTakeOut(UUID pourer, UUID who, boolean op, boolean publicBasin, boolean anyoneInside) }`: pourer or op always; others only when `publicBasin && !anyoneInside`.
  - `PensieveBlock` use rules (server): holding a `PensieveMemory` item and the basin is empty → pour (moves one item into the basin); sneak + empty hand → take out if `canTakeOut` (else action-bar `message.hogwartsmagic.pensieve.not_yours`); On remove: drop the memory. (Task 6 adds the dive branch, empty hand and not sneaking while the basin holds a memory → `PensieveSessions.startOrJoin`, and the `abortAt` call on remove.)
- Consumes: none earlier.

- [ ] **Step 1: Write the failing tests**
  - `BasinAccessTest`: pourer → true; stranger private → false; stranger public empty → true; stranger public with viewers → false; op → true.
  - Gametest `pourAndTakeOut`: place Pensieve, mock player pours `chamber_memory`, BE holds it; the same player sneak-uses with empty hand, gets it back.
  - Gametest `strangerCannotTakeOutPrivateMemory`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**, with a temporary placeholder block model (cauldron-like) until Task 12; recipe: `"pattern":["DAD","S S","SSS"]`, `D` polished deepslate, `A` `hogwartsmagic:arcane_dust`, `S` `minecraft:stone`.
- [ ] **Step 4: Run tests and gametests, expect PASS; `runServer` boot clean.**
- [ ] **Step 5: Commit** `Add the Pensieve block and story memory items`.

### Task 6: Sessions: entering, building, playing, leaving, and recovery

**Files:**
- Create: `pensieve/PensieveSessions.java`, `Session.java`, `ExitReason.java`, `pensieve/net/{PensieveEnterPacket,PensieveStartPacket,PensieveExitPacket,PensieveLeavePacket}.java`
- Modify: `pensieve/PensieveBlock.java` (dive branch and `abortAt` on remove, see Task 5); `network/SpellPackets.java` (register the four packets, protocol `9`→`10`); `ModEvents.onServerTick` (~L2823) calls `PensieveSessions.tick(server)`; `ModEvents.onPlayerLogout` (~L666) calls `PensieveSessions.onLogout(player)`; `ModEvents.onPlayerJoin` (~L485) calls `PensieveSessions.onLogin(player)`; server stopping calls `PensieveSessions.endAll()`; `PensieveEvents` cancels, for viewers (`PensieveSessions.isViewer`): `LivingAttackEvent` on them (except the body's forwarded damage, Task 7), `PlayerInteractEvent.*`, `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`, `AttackEntityEvent`, `EntityItemPickupEvent`, item tossing, and chat commands other than `/pensieve`; `client/ClientRuntimeBridge.handlePacket` gains actions `pensieveEnter`, `pensieveStart`, `pensieveExit` forwarding to `PensieveClientState` (Task 8; until then no-op methods); `PensieveCommand` adds `/pensieve preview <id>` and `/pensieve leave`.
- Test: gametests in `PensieveGameTests`.

**Interfaces:**
- Consumes: `MemoryRegistry`, `SetBuilder`, `RegionAllocator`, `SeamlessMove`, `PensieveDimension`, `PensieveBlockEntity`, `SessionClock`.
- Produces:
  - `enum ExitReason { END, YANK, LEFT, ABORT }` (server only).
  - `PensieveSessions { static void startOrJoin(ServerPlayer, PensieveBlockEntity); static void startPreview(ServerPlayer, String sceneId); static void tick(MinecraftServer); static void leave(ServerPlayer, ExitReason); static void abortAt(Level, BlockPos basin); static boolean isViewer(Player); static Optional<Session> sessionOf(Player); static void onLogin(ServerPlayer); static void onLogout(ServerPlayer); static void endAll() }`.
  - `Session`: `sceneId`, `rawJson`, `SceneDef scene`, `int slot`, `BlockPos origin` (memory dimension), `ResourceKey<Level> basinDim`, `BlockPos basinPos`, `Phase phase` (`BUILD`, `PLAY`, `CLOSING`), `long startGameTime` (memory dimension game time when `PLAY` begins), `Map<UUID, Viewer>`; `Viewer(UUID id, Vec3 returnPos, float yRot, float xRot, ResourceKey<Level> returnDim, int swallowTicksLeft, UUID bodyId)`.
  - Packets (exact fields):
    - `PensieveEnterPacket(BlockPos basin)` s→c: start the swallow toward the basin.
    - `PensieveStartPacket(String sceneId, String sceneJson, BlockPos origin, long startGameTime, int buildTicks, boolean preview)` s→c: sent when a viewer arrives in the memory dimension; `startGameTime = -1` while building (client shows build-from-shadow), resent with the real value when `PLAY` begins.
    - `PensieveExitPacket(int reason /* ExitReason.ordinal() */)` s→c.
    - `PensieveLeavePacket()` c→s: leave early (`ExitReason.LEFT`).
  - Flow:
    1. **Dive**: viewer record created, return point = current position/rotation/dimension, also persisted in `PlayerMagicData.getModData(player)` under `"PensieveReturn"` (dim, x, y, z, yRot, xRot); send `PensieveEnterPacket`; viewer is frozen (no movement: re-teleport to the return point each tick) for `SWALLOW_TICKS = 30`.
    2. **Cross** after 30 ticks: `SeamlessMove.move` to `origin + (0.5, 1, 0.5)`; set invisible and invulnerable to everything but forwarded damage, `abilities.mayfly = flying = true`, `noPhysics`-equivalent by re-teleporting each tick to the anchor (the directed camera is client-side); send `PensieveStartPacket`.
    3. **Build** (first viewer only): a new session acquires a slot, creates `SetBuilder` at `origin`, sets the memory level's day time to `set.timeOfDay`, advances it each tick with `pensieveBlocksPerTick`, and enters `PLAY` when done **and** at least `set.buildTicks` ticks have passed since the first cross; then resends `PensieveStartPacket` with `startGameTime = memoryLevel.getGameTime()` to all viewers.
    4. **Play**: each tick apply due `EventDef` of kind `block` (server sets the block at `origin + at`, flags `2|16`). Other event kinds are client-side. When `SessionClock.finished`, phase `CLOSING`: send `PensieveExitPacket(END)` to all; 30 ticks later move each viewer back.
    5. **Join**: a dive on a basin whose session is in `BUILD` or `PLAY` adds a viewer to that session (same origin, same `startGameTime`).
    6. **Leave** (any reason): `PensieveExitPacket(reason)`; after 30 ticks (`YANK`: 10 ticks; `ABORT`: 20) `SeamlessMove.move` back to the return point, restore visibility, abilities and vulnerability, clear `"PensieveReturn"`, remove the body (Task 7). Last viewer gone → `ClearJob` on the placed blocks, then `RegionAllocator.release`.
    7. **Recovery**: `onLogout` removes the viewer immediately (no packets) and moves them back before the save; `onLogin` with a `"PensieveReturn"` tag and no live session (crash case) or while in the memory dimension → move to the return point, clear the tag.
    8. **Preview**: like a dive but the return point is where the op stands, there is no basin and no body; uses `MemoryRegistry.loadForPreview`; validation messages from Task 3 are sent to chat first and a scene with messages does not start.
  - Story memory items are never consumed; `CaveMemoryItem` end action is applied in Task 14.

- [ ] **Step 1: Write the failing gametests**
  - `diveMovesPlayerIntoMemoryAndBack`: a 10-tick scene with a 1-block fill; player dives; after 30 ticks they are in `PensieveDimension.KEY`; after the scene ends + 30 ticks they are back within 0.1 blocks of the return point, visible and vulnerable.
  - `secondViewerJoinsSameSession`: second player dives 40 ticks later → same `Session` object, same `startGameTime`.
  - `breakingBasinEndsSession`: break the basin while a viewer is inside → after 20 ticks the viewer is back and `isViewer` is false.
  - `loginInsideMemoryReturnsPlayer`: put a `"PensieveReturn"` tag on a player standing in the memory dimension with no session, call `PensieveSessions.onLogin` → player is at the return point, tag gone.
  - `viewerCannotBreakBlocks`: `BlockEvent.BreakEvent` for a viewer is cancelled.
- [ ] **Step 2: Run `./gradlew runGameTestServer`, expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Gametests PASS, `./gradlew test` PASS, `runServer` boot clean.**
- [ ] **Step 5: Commit** `Run Pensieve sessions on the server`.

### Task 7: The body left at the basin, damage forwarding and the yank

**Files:**
- Create: `pensieve/PensieveBodyEntity.java`, `pensieve/YankRule.java`, `pensieve/net/PensieveThudPacket.java`
- Modify: `entity/ModEntities.java` (`PENSIEVE_BODY`, `MobCategory.MISC`, sized like a player `0.6F, 1.8F`, `clientTrackingRange(10)`), `event/ModEntityAttributes.java` (player-like attributes: max health 20, no movement); `SpellPackets` registers `PensieveThudPacket` (protocol stays `10`); `PensieveSessions` spawns the body on dive (at the return point, facing the basin) and removes it on leave; `ClientRuntimeBridge` action `pensieveThud`.
- Test: `src/test/java/com/hogwartsmagic/pensieve/YankRuleTest.java`; gametests.

**Interfaces:**
- Produces:
  - `YankRule { static boolean shouldYank(float health, float maxHealth) }` → `health < maxHealth / 3f`.
  - `PensieveBodyEntity extends LivingEntity`: synced `Optional<UUID> owner` and `String ownerName`; no AI, no gravity, no knockback, not pushable, `shouldBeSaved()` false, no drops; `hurt(source, amount)`: if the owner is a live viewer, call `PensieveSessions.forwardDamage(owner, source, amount)` and return true (play hurt animation); otherwise discard.
  - `PensieveSessions.forwardDamage(ServerPlayer, DamageSource, float)`: marks the call so the viewer's invulnerability lets it through, applies `owner.hurt(source, amount)`; sends `PensieveThudPacket(min(1, amount / 6))`; if the owner died, the normal death flow runs and `leave(LEFT)` happens via the death event (the respawn must not land them in the memory dimension: on `PlayerEvent.Clone`/respawn for a player with `"PensieveReturn"`, clear it); else if `YankRule.shouldYank` → `leave(YANK)`.
  - `PensieveThudPacket(float strength)` s→c.
- Consumes: Task 6.

- [ ] **Step 1: Failing tests**
  - `YankRuleTest`: `(6.66, 20)` true, `(6.67, 20)` false, `(20, 20)` false.
  - Gametest `hittingBodyHurtsViewer`: viewer at 20 health, damage the body by 4 → viewer health 16, still a viewer.
  - Gametest `heavyHitsYankViewerOut`: damage the body to bring the viewer to 6 → viewer leaves with `YANK` (back at return point within 10 ticks + move).
  - Gametest `bodyIsRemovedWhenViewerLeaves`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** (The body's look, bent over the basin with the owner's skin, is drawn in Task 10.)
- [ ] **Step 4: PASS; `runServer` clean.**
- [ ] **Step 5: Commit** `Leave a helpless body at the Pensieve and forward its damage`.

### Task 8: Client state, transitions, the void and the directed camera

**Files:**
- Create: `client/pensieve/PensieveClientState.java`, `PensieveCamera.java`, `PensieveOverlay.java`, `PensieveVoid.java`, `PensievePost.java`; mixins `PensieveCameraMixin`, `PensieveGuiMixin`, `PensieveLevelRendererMixin`, `PensieveGameRendererMixin`; `assets/hogwartsmagic/shaders/post/pensieve.json`, `shaders/program/pensieve.json`, `shaders/program/pensieve.fsh`; overlay textures `textures/gui/pensieve/{silver_surface,wisp}.png` (generated by `build_scripts/generate_pensieve_gui.py`, animated surface as horizontal frames)
- Modify: `hogwartsmagic.mixins.json` (client list); `ClientRuntimeBridge` actions call `PensieveClientState.onEnter/onStart/onExit/onThud`; `ClientForgeEvents.onFogColor/onRenderFog` defer to `PensieveVoid` while active; `ClientModEvents.onComputeFov` and `onComputeCameraAngles` defer to `PensieveCamera` while active; key mapping `key.hogwartsmagic.pensieve_leave` (default `X`) in `ClientModEvents` (L109–120, L766) sends `PensieveLeavePacket`; hide hand (`RenderHandEvent` cancel) and the vanilla HUD (except chat) while active.

**Interfaces:**
- Consumes: Tasks 1, 2, 6, 7 packets; `MemoryActors.actorEye(String)` from Task 9 for camera look-at (until Task 9, return null and fall back to the key's point).
- Produces:
  - `PensieveClientState`: phases `NONE, SWALLOW, FALL, BUILD, PLAY, EXIT, YANK` as `static final int`s (not an enum, to stay clear of enum switches); `static boolean active()`; `static int phase()`; `static float phaseTime(float partial)` (ticks since the phase began); `static SceneDef scene()`; `static BlockPos origin()`; `static float sceneTime(float partial)` = `SessionClock.elapsed(startGameTime, level.getGameTime(), partial)`; `static float thud()` (decays over 12 ticks).
  - Timeline the client plays (ticks):
    - **SWALLOW 30** on `onEnter(basin)`: camera eases from the eye to a point 0.2 above the basin centre while pitch eases to 90° (looking straight down), fov 70 → 30; overlay draws the animated silver surface rising from the bottom to fill the screen over the last 12 ticks; sound `pensieve.swallow`.
    - **FALL** from tick 30 until `onStart`: black screen with silver wisps streaking upward in three parallax layers (falling sensation), slight camera roll sway, sound `pensieve.fall` + loop `pensieve.void_hum`.
    - **BUILD** on `onStart(startGameTime = -1)`: camera at the scene's first camera key; void fog with distance growing from 2 to the scene radius over `buildTicks`; post effect desaturation 0.9 → 0.35 and darkness 1 → 0 over the same span; dark smoke particles at newly appeared blocks (client `BlockEvent`-free approach: each tick sample 40 random positions inside the current fog radius ring and spawn `minecraft:large_smoke` tinted dark where the block is not air).
    - **PLAY** on `onStart(startGameTime ≥ 0)`: camera = `CameraTrack.sample(sceneTime)`; steady grade: desaturation 0.35, slight silver tint, vignette; void: black clear colour, fog start `radius - 8`, end `radius + 2`, drifting dark fragment particles on the fog ring (12 per tick).
    - **EXIT 30** on `onExit(END|LEFT|ABORT)`: fog radius collapses to 0 and darkness rises (first 15), then the wisps streak downward (rising sensation, last 15); at the end of the move back the camera eases from the basin surface back to the eye in 15 ticks; sound `pensieve.rise`.
    - **YANK 10** on `onExit(YANK)`: red-white flash (2 ticks), hard shake, instant black, `pensieve.gasp`; return camera snaps (no ease).
    - **Thud** (`onThud(strength)`) during any phase inside: muffled distant sound `pensieve.distant_thud` at volume `strength`, red tint on the vignette edges and a 6-tick screen shudder scaled by strength.
  - `PensieveCamera { static boolean override(); static void apply(Camera camera, float partial) }` (mixin calls `apply` at `Camera#setup` TAIL through an invoker for `setPosition`/`setRotation`); fov and roll through the existing ComputeFov / ComputeCameraAngles handlers.
  - `PensieveVoid`: fog colour black, fog distances per phase; `PensieveLevelRendererMixin` cancels sky, clouds and rain/snow rendering while active.
  - `PensievePost { static void process(float partial) }`: lazily builds a `PostChain` from `hogwartsmagic:shaders/post/pensieve.json` on the main render target, sets uniforms `Desaturation`, `Darkness`, `Tint` (vec3), `Vignette`, `RedEdge`, `Time`; on any load failure logs once and becomes a no-op (overlays still run). Called by `PensieveGameRendererMixin` after the level is rendered and before the GUI.
- [ ] **Step 1: Build check.** No unit test covers rendering. Verify by preview instead (Step 4).
- [ ] **Step 2: Implement.** Keep every phase driven only by `phaseTime`/`sceneTime` so two viewers see the same film.
- [ ] **Step 3: `./gradlew compileJava test runServer` (server must still boot: no client class reached from common code).**
- [ ] **Step 4: Visual check in the dev client** (close it before any later gradle run): add a throwaway scene `data/hogwartsmagic/memories/_test_void.json` (a 7×1×7 stone fill, length 200, one camera orbit) with `pensieveDevSceneDir = "../src/main/resources/data/hogwartsmagic/memories"` in `run/config/hogwartsmagic-common.toml`; place a Pensieve, pour a story memory whose scene is overridden by preview, and run `/pensieve preview _test_void`, then the basin dive. Screenshot each phase (swallow, fall, build, play, exit) and the yank (`/damage` the body from a second account or a zombie). Delete `_test_void.json` after.
- [ ] **Step 5: Commit** `Add the Pensieve transitions, void and directed camera`.

### Task 9: Actors: puppets with skins, paths, items and actions

**Files:**
- Create: `client/pensieve/MemoryActors.java`, `ActorRuntime.java`, `ActorSkins.java`, mixin `ActorSkinMixin`; `assets/hogwartsmagic/textures/memory_skin/` with an initial set of skins (64×64 PNGs, generated by `build_scripts/generate_memory_skins.py`: `hooded_figure`, `regulus`, `student_gryffindor`, `student_slytherin`, `death_eater`, `voldemort` (match the existing Wizard Voldemort texture palette)), and `data/hogwartsmagic/memories/_skins.json` listing them.
- Modify: `PensieveClientState` starts/clears `MemoryActors` on `PLAY`/exit; a `RenderLevelStageEvent` handler at `AFTER_ENTITIES` calls `MemoryActors.renderAll`; client tick calls `MemoryActors.tick()`.

**Interfaces:**
- Consumes: `SceneDef.ActorDef`, `Track`, `SessionClock`, `PensieveClientState`.
- Produces:
  - `MemoryActors { static void start(SceneDef, BlockPos origin, ClientLevel); static void tick(); static void renderAll(PoseStack identity, MultiBufferSource.BufferSource, Camera, float partial); static void clear(); static float[] actorEye(String id); static ActorRuntime runtimeFor(Entity); static boolean isActor(Entity) }`.
  - Actors are client-only entities **not added to the level** (no ticking, no AI, no sounds of their own): `type "player"` → `RemotePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(id), name))`; any other → `EntityType.byString(type).create(level)`. Rendered with `Minecraft.getInstance().getEntityRenderDispatcher().render(...)` at `origin + path(t)` with packed light from `LevelRenderer.getLightColor(level, pos)`; ids are negative and unique.
  - Each frame per actor: position from `path`, yaw/pitch from `look` (else face movement direction), `yBodyRot` eases toward yaw, head yaw = look yaw; walk animation from path speed (`walkAnimation.setSpeed` from blocks per tick, position advanced via `walkAnimation.update`); items from `items` (`mainhand, offhand, head, chest, legs, feet`); actions at their `t`: `swing` (main arm swing), `sneak`/`stand` (pose `CROUCHING`/`STANDING`), `hurt` (red flash, `hurtTime`), `die` (death tilt over 20 ticks then poof particles), `vanish`/`appear` (black-silver smoke burst, visibility toggle), `stone`/`unstone` (render tinted grey with the petrified overlay used by `PetrifiedRemainsRenderer`), `hold` (swap held item to `item`), `use_item` (start using the held item: arm raise and drink/eat animation for 32 ticks), `cast` (handled in Task 11).
  - `ActorSkins { static PlayerSkin skinFor(SkinRef ref) }`: texture refs → `ResourceLocation("<ns>", "textures/memory_skin/<path>.png")` with `PlayerSkin.Model.SLIM` or `WIDE`; usernames → `SkullBlockEntity.fetchGameProfile(name)` then `Minecraft.getSkinManager().getOrLoad(profile)`, cached; until loaded or offline → `DefaultPlayerSkin.get(uuid)`. `ActorSkinMixin` returns `ActorSkins` result from `AbstractClientPlayer#getSkin` for actor players.
- [ ] **Step 1: Build check** (`compileJava`, `runServer` clean).
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Visual check** with a throwaway scene: two skinned player actors walking a 10-block path toward each other, one swinging, one sneaking, a zombie and a Basilisk standing; a username skin actor (`Notch`). Screenshot.
- [ ] **Step 4: Commit** `Add Pensieve actors with skins, paths and actions`.

### Task 10: Limb animation, named poses and creature animations

**Files:**
- Create: `client/pensieve/ModelPartIndex.java`, `ActorPoser.java`, `ActorAnimationLibrary.java`, `PensieveBodyRenderer.java`; mixins `ActorPoseMixin`, `ModelPartAccessor`
- Modify: `ClientModEvents.registerRenderers` registers `PensieveBodyRenderer` for `PENSIEVE_BODY`.

**Interfaces:**
- Consumes: `ActorDef.limbs/poses/anims`, `SceneDef.poses`, `MemoryActors.runtimeFor`.
- Produces:
  - `ModelPartIndex { static Map<String, ModelPart> of(EntityModel<?> model) }`, cached per model class: `HierarchicalModel` → walk `root()` children by name (via `ModelPartAccessor.children()`), names as-is; `HumanoidModel` and subclasses → `head, hat, body, right_arm, left_arm, right_leg, left_leg` (+ `jacket, left_sleeve, right_sleeve, left_pants, right_pants` for `PlayerModel`); anything else → every `ModelPart` field by reflection, camelCase → snake_case; then also walk each found part's children.
  - Pose presets (built in, usable as `poses` cue names without defining them): `wand_raised`, `wand_point`, `kneel`, `cower`, `arms_out`, `bent_over_basin`, `hands_up`, `look_down`. Values are part → `[x,y,z]` degrees for player models.
  - `ActorPoser { static void apply(EntityModel<?> model, Entity entity, float partial) }`, called by `ActorPoseMixin` right after `setupAnim` in `LivingEntityRenderer#render` when `MemoryActors.isActor(entity)` or the entity is a `PensieveBodyEntity`. Order: (1) named creature animation, if a cue is active: `KeyframeAnimations.animate(model, def, elapsedMillis, 1f, scratch)` on `HierarchicalModel`s only; (2) pose cues blended in over `blend` ticks (default 5); (3) limb tracks: `rot` added to the part's current rotation (degrees → radians), `offset` added to `x,y,z`; a limb key with `"mode":"set"` replaces instead of adding.
  - `ActorAnimationLibrary { static Optional<AnimationDefinition> find(String entityType, String name); static Set<String> names(String entityType) }`: reflects the `public static final AnimationDefinition` fields of a fixed map `entity id → Animations class` (`basilisk → BasiliskAnimations`, `dementor`, `giant`, `goblin`, `goblin_guard`, `grindylow`, `house_elf`, `inferius`, `merperson`, `troll`, `lycanthrope_villager → Whulvk*Animations`, `owl/flying_mob → FlyingMobAnimation`, `hippogriff`); names are the field names (e.g. `REAR`, `STRIKE`).
  - Client part check: in preview sessions only, on `MemoryActors.start`, each limb name, pose part and animation name not found for that actor's model is reported in chat: `actors[<i>].limbs.<name>: no part '<name>' on <type> (has: a, b, c)` / `no animation '<name>' (has: ...)`.
  - `PensieveBodyRenderer`: renders a `RemotePlayer` surrogate with the owner's profile (skin via the normal `PlayerInfo` when online) in the `bent_over_basin` pose, facing the basin, through the same `ActorPoser`.
- [ ] **Step 1: Build check.**
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Visual check** with a throwaway scene: a player actor going through every preset pose; a right-arm limb track slashing (the example in the spec conversation: `-150,0,10` → `-90,0,0`); a Basilisk playing `REAR` then `STRIKE` with an added `jaw` rotation; a zombie with arms moved by limb tracks; one deliberately wrong part name to see the chat error. Also dive from a real basin and look at your own body from a second client or F5 before the swallow ends. Screenshot.
- [ ] **Step 4: Commit** `Animate Pensieve actors limb by limb`.

### Task 11: Spells, effects, words and sounds inside memories

**Files:**
- Create: `client/pensieve/SpellVisuals.java`, `EffectVisuals.java`, `MemoryWords.java`, `MemorySounds.java`
- Modify: `entity/SpellBeamEntity.java`: add `private boolean visualOnly` with `public void setVisualOnly()`; when true, `tick()` skips all server work and `onHit` skips `onHitInner` and spawns the spell's impact particles, then discards; it never hits the local player. `entity/VoldemortSummoningEntity.java`: make the ritual particle/beam emitters reusable as `static void emitRitualEffects(Level, Vec3 centre, int age)` (current private code moved, behaviour unchanged) so `EffectVisuals` can call them client-side. `SceneNames.SPELL_VISUALS` becomes the set of spells `SpellVisuals` supports. `PensieveGuiMixin` also calls `MemoryWords.render`.

**Interfaces:**
- Consumes: `ActionCue cast`, `EventDef`, `WordDef`, `MemoryActors`.
- Produces:
  - `SpellVisuals { static Set<String> supported(); static void cast(ClientLevel, Vec3 from, Vec3 to, String spellId) }`: beam spells → a client-only `SpellBeamEntity` with `setVisualOnly()`, added with `ClientLevel.addEntity` (negative id), `shoot` at 1.55 speed toward `to`, plus the spell's cast sound from `SpellType.getSoundEvent()` through `MemorySounds`. Non-beam spells need an adapter; ship adapters for `revelio` (cyan pulse sphere + the reveal shimmer on blocks), `lumos` (light orb particles on the wand tip for `duration`), `protego` (the shield dome visual used by the real spell, held for 40 ticks), `morsmordre` (a client-only `DarkMarkEntity` rising from the target). Every other non-beam spell is absent from `supported()`, so the validator names it.
  - `EffectVisuals { static void play(String effect, Vec3 at, int duration) }`: `summoning_ritual` (calls `VoldemortSummoningEntity.emitRitualEffects` each tick for `duration`), `lightning` (client-only `LightningBolt` with `setVisualOnly(true)`), `dark_mark` (as morsmordre).
  - `EventDef` kinds handled client-side at their `t`: `sound` → `MemorySounds.play(id, origin+at, volume, pitch)`; `particles` → `level.addParticle` `count` times within `spread`; `effect` → `EffectVisuals.play`. (`block` is server-side, Task 6.)
  - `MemorySounds.play(...)`: volume × 0.75, pitch × 0.92, plus a quiet delayed echo (same sound, 6 ticks later, volume × 0.25).
  - `MemoryWords.render(GuiGraphics, float partial)`: actor lines (`by`) appear bottom-left as chat-styled lines `<name> text` in silver (`#C9D6E3`), fading after 8 seconds; `narration` lines appear centred low in italic silver, fading in and out over `duration` (default 60). Never written to the real chat log.
- [ ] **Step 1: Failing test**: `SceneValidatorTest.unsupportedSpellIsNamed` with `SPELL_VISUALS` lacking `fiendfyre` → message `actors[0].actions[0].spell: no memory visual for 'fiendfyre'`.
- [ ] **Step 2: Run, expect FAIL; implement; run, expect PASS.**
- [ ] **Step 3: `runServer` clean, and confirm in code review that a real (non-visual) `SpellBeamEntity` is unchanged when `visualOnly` is false.**
- [ ] **Step 4: Visual check** with a throwaway scene: a duel (stupefy and expelliarmus beams between two actors, a protego), a revelio pulse, a summoning_ritual effect with lightning, actor chat lines and a narration line, a scene sound. Screenshot.
- [ ] **Step 5: Commit** `Cast spells, play effects and show words inside memories`.

### Task 12: Sounds, the basin's look, item textures and language

**Files:**
- Create: `build_scripts/generate_pensieve_textures.py` (basin stone texture, animated silver surface `pensieve_surface.png` + `.mcmeta`, `chamber_memory.png`, `summoning_memory.png`, in the 32×32 animated silver-wisp style of `generate_cave_memory_texture.py`: green-tinged wisps for the Chamber, sickly green and black for the Summoning); block model `models/block/pensieve.json` (stone basin on a pedestal with a recessed liquid plane), blockstate, item models; `client/pensieve/PensieveBasinRenderer.java` (block entity renderer: draws the liquid plane, swirling faster and brighter when the basin holds a memory and brighter still while a session plays at it (client checks the synced memory and a `playing` flag synced by the BE)).
- Modify: `ModSounds.java` + `sounds.json`: `pensieve.swallow`, `pensieve.fall`, `pensieve.void_hum`, `pensieve.rise`, `pensieve.gasp`, `pensieve.distant_thud`, `pensieve.pour`; `PensieveBlockEntity` syncs a `playing` boolean set by `PensieveSessions`; `lang/en_us.json`: block, items, the leave key, `message.hogwartsmagic.pensieve.*` (`not_yours`, `empty`, `busy_preview`), subtitles, and the three memory titles; `ClientModEvents` registers `PensieveBasinRenderer`.

- [ ] **Step 1: Sounds.** Follow the ElevenLabs SFX pipeline (key in the user's local file, never committed; MP3 → mono OGG with ffmpeg; envelope-trim and level-match each take before saving). Prompts: swallow (*a deep liquid gulp into a silver basin, airy rush*), fall (*long falling whoosh through a dark void with soft shimmering*), void_hum (*low seamless loop, distant breathing hum*), rise (*reverse whoosh rising, ends in a soft surface break*), gasp (*a sharp startled gasp with a liquid splash*), distant_thud (*a muffled thud heard from underwater*), pour (*a thin silver liquid pouring into stone*). If the key file is missing, register each sound as a `"type":"event"` alias of a fitting vanilla event (e.g. `minecraft:item.bottle.empty`, `minecraft:ambient.underwater.loop`) and say so in the commit message.
- [ ] **Step 2: Run the texture script; implement the renderer and models; add lang.**
- [ ] **Step 3: `./gradlew compileJava test runServer` clean; in the client, the basin looks like a basin, the surface swirls, holding a memory brightens it, the three items show their textures, every new sound plays.**
- [ ] **Step 4: Commit** `Give the Pensieve its look, its sounds and its words`.

### Task 13: The authoring skill

**Files:**
- Create: `.claude/skills/writing-pensieve-memories/SKILL.md`

The skill is what lets a future Claude session write a memory on the user's command. It must cover, briefly and with one complete small example scene:
- When to use it (the user asks for a memory, a cinematic, a tutorial scene).
- The full scene format from Task 1–2 (every key, time units, coordinates relative to the set origin, yaw/pitch convention, ease names, cut shots), the pose presets, how to find part names (`ModelPartIndex` rules; the preview's chat error lists them), the creature animation names table from Task 10, the supported spells and effects, the shipped skins and how to add a skin (drop a 64×64 PNG in `textures/memory_skin/`, add it to `_skins.json`).
- The loop: set `pensieveDevSceneDir`, launch the dev client, `/pensieve preview <id>`, screenshot, edit the JSON, preview again; `/pensieve validate`; `./gradlew test` (SceneDataTest) only with the client closed.
- Film craft rules of thumb for these scenes: establish the place with a wide slow shot, cut on action, keep shots 2–6 seconds, move the camera while actors move, hold on the key moment, end on a slow push-in before the fade.

- [ ] **Step 1: Write it.**
- [ ] **Step 2: Check it against the code: every key and name in it exists.**
- [ ] **Step 3: Commit** `Add the skill for writing Pensieve memories`.

### Task 14: Story memory: the Locket cave

**Files:**
- Create: `data/hogwartsmagic/memories/locket_cave.json`
- Modify: `PensieveSessions` applies `endAction "cave_map"` to each viewer of a scene whose end action is set, when the scene ends with `END` (calls `CaveMemoryItem.grantCaveMap`); `CaveMemoryItem` implements `PensieveMemory` (Task 5).

Content (from the spec): night, a hooded figure on the clifftop over the black sea; Revelio shows the hidden crack; the swim through the tunnel; paying the Blood Seal (three heart particles, the figure staggering); the hidden chain shown by Revelio, the boat rising; rowing across the black lake; stopping at the Basin of Despair as the scene fades. Do not show the drink or the ambush. Set: `hogwartsmagic:locket_cave` template plus a sea fill and a clifftop around it within radius. Length about 60–90 s. Camera: wide establishing shot of the headland, then follow and cut on each step.

- [ ] **Step 1: Write the scene with the skill; `SceneDataTest` passes (client closed).**
- [ ] **Step 2: Preview in the client, iterate until every shot reads; screenshot the key shots.**
- [ ] **Step 3: Watch it through a real basin with a Memory of the Cave: the Cave Map arrives at the end; drinking a vial still works as before.**
- [ ] **Step 4: Commit** `Add the Memory of the Cave as a Pensieve cinematic`.

### Task 15: Story memory: the Chamber (Basilisk)

**Files:** Create `data/hogwartsmagic/memories/chamber.json`.

Content (from the spec): a student enters the Chamber; the Basilisk wakes; the student's view greys at the edges under the gaze (a camera shot from behind the student with a grey vignette event); they look away and fight from the edge of their vision; it rears before a strike (`REAR`, `STRIKE`), spits venom (`SPIT` + particles), burrows (`BURROW`, `ERUPT`); blows to the head blind it (`BLINDED`); it hunts by sound while the student crouch-walks; a second careless figure meets its eyes and turns to stone (`stone` action); a friend brings them back with a Mandrake Restorative Draught (`use_item` then `unstone`). Set: `hogwartsmagic:chamber_hall`. Length about 90 s.

- [ ] **Step 1: Write; `SceneDataTest` passes.** Add a `grey_edges` client effect to `EffectVisuals` and `SceneNames.EFFECTS` (vignette greying for `duration`) if the gaze shot needs it.
- [ ] **Step 2: Preview and iterate; screenshots.**
- [ ] **Step 3: Commit** `Add the Memory of the Chamber`.

### Task 16: Story memory: the Summoning

**Files:** Create `data/hogwartsmagic/memories/summoning.json`.

Content (from the spec): night, a dark clearing (disk of grass and podzol, dark oak features around, low fog particles); Death Eaters place three Wards in a diagonal triangle (`block` events with Ward blocks and `swing`); one strikes a Ward with a spell beam; the ritual builds (`summoning_ritual` effect, `lightning`, `dark_mark`); Voldemort rises (`appear` with smoke, `kneel` poses on the Death Eaters); cut away as he turns toward the camera. Length about 45–60 s.

- [ ] **Step 1: Write; `SceneDataTest` passes.**
- [ ] **Step 2: Preview and iterate; screenshots.**
- [ ] **Step 3: Commit** `Add the Memory of the Summoning`.

### Task 17: Documentation and full verification

**Files:** Modify `MOD_FEATURES_WIKI.md` (a Pensieve section under Blocks, the two new items under Items, a short "Story memories" list), `wiki/` pages that list blocks/items (`wiki/data.js` entries following the existing shape).

- [ ] **Step 1: Write the docs.**
- [ ] **Step 2: With the client closed: `./gradlew test`, `./gradlew runGameTestServer` (all Pensieve gametests pass), `./gradlew runServer` boots with no `/ERROR`.**
- [ ] **Step 3: In the client: place a Mini Hogwarts (`/findminihogwarts` or the spawn command) and confirm its Pensieve; watch all three memories through it, two players at once for one of them (second client or LAN), and get yanked once by a zombie. Screenshot.**
- [ ] **Step 4: Commit** `Document the Pensieve`.
