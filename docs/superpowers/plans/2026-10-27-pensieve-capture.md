# Pensieve Memory Capture Implementation Plan (plan 2 of 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Players draw the last 60 seconds around them into a Memory phial (sneak + use a wand with an Empty Memory Phial in the other hand), pour it into any Pensieve, and relive it inside the memory dimension: the exact blocks rebuilt from shadow, every player and creature as a puppet with its real skin, look, pose, held items and animations, spell beams, sounds and chat, watched with a free ghost camera bounded to the island, a key that snaps the view into the owner's eyes, and other viewers drifting nearby as silver ghosts.

**Architecture:** Server-side sampling, not packet capture. Each `ServerLevel` keeps one shared rolling log for everything near *any* player (block changes with old and new state from a `LevelChunk#setBlockState` hook, per-tick entity samples with synched-data and equipment diffs, swings, hurts, deaths, spell beams, sounds, particles, level events). Each player keeps only a small log of the chat they received. Capture copies the chunk sections around the owner on the server thread, then a worker thread rewinds the block changes to the window's start, picks the entities, and encodes everything as a compressed binary `Recording` in `<world>/data/hogwartsmagic_memories/<uuid>.hpm`. Playback reuses plan 1's whole session machinery: dive, swallow, fall, build from shadow, the body at the basin, the yank, shared viewing, exit and recovery. The server's `SetBuilder` places the snapshot and the server replays the block changes. The client receives the recording in binary parts, converts it with a pure `RecordingScene` into the same `SceneDef` that story memories play (actors, words, sounds, beams), and steps each recorded actor from its per-tick track instead of a keyframed path.

**Why sampling and not packets (decided here, as the spec allowed):** In singleplayer the integrated server hands packets to the client as objects and never encodes them, so a netty-level tap sees nothing. A tap above that would have to re-encode every packet, then rewrite absolute positions and entity ids in each of dozens of packet types (vanilla and the mod's own) to replay them in another dimension, and hold a client-side packet player. That is ReplayMod-sized work, and it breaks whenever a packet changes. Sampling records a world fact once per level no matter how many players are near it. It covers every cause of a block change, because they all end in `LevelChunk#setBlockState`. It gets almost all creature fidelity (poses, sheep colours, villager professions, the mod mobs' `DATA_ATTACK_STATE` animations, dropped-item stacks, invisibility, fire) for free by recording `SynchedEntityData` diffs and replaying them through `assignValues`. Spell beams replay through the real beam visuals in `SpellVisuals`.

**Tech Stack:** Forge 51 / Minecraft 1.21 / Java 21, Mixin, JUnit 5.10 (pure tests: Minecraft classes are on the test classpath but not bootstrapped), Forge GameTest, `java.util.zip.Deflater/Inflater`, Python texture scripts.

**Spec:** `docs/superpowers/specs/2026-10-16-pensieve-design.md`, especially "Capturing a memory", "Inside the memory", "Shared viewing", "Calls made after the builder review" and "Edge cases the builder must handle". Plan 1 (built): `docs/superpowers/plans/2026-10-16-pensieve-story-memories.md`.

**Already delivered by plan 1 and reused unchanged:** `PensieveBlock`/`PensieveBlockEntity` pour and take out with `BasinAccess` (pourer, op, or anyone on the public basin while nobody is inside), `PensieveSessions` (dive, `SWALLOW_TICKS`, cross with `SeamlessMove`, BUILD/PLAY/CLOSING, joining at the current moment, `leave`/`abortAt`, `onLogin`/`onLogout` recovery through the `"PensieveReturn"` tag, `endAll`), `PensieveBodyEntity` with damage forwarding and `YankRule`, `RegionAllocator` and `RegionData` clearing, `PensieveClientState` transitions, `PensieveVoid` fog fray, `PensievePost` grade, `MemoryActors`/`ActorSkins`/`ActorPoser`, `SpellVisuals`, `MemorySounds` (muffled with an echo), `MemoryWords` (silver chat lines), and the leave key `X`. **Not delivered and planned here:** the recorder, capture, phials, recorded playback, free camera, snap-to-owner and silver ghosts. Plan 1 pins every viewer to `Session.anchor()` each tick and draws no other viewers.

## Global Constraints

- Every authoritative action runs on the server. No `net.minecraft.client.*` or `com.hogwartsmagic.client.*` reference from common or server code. Client code is reached only through `ClientSideBridge.handlePacket(action, payload)` → `client/ClientRuntimeBridge`, or through Forge client events. Common mixins (`mixins` list) must not touch client classes. A predicate a common mixin needs on the client side reads a plain static flag in a common class that client code sets. Must boot on a dedicated server: `./gradlew runServer` reaches `Done (` with no `/ERROR`.
- Never run gradle while the dev client is running. It rewrites `build/sourcesSets/main` and the lazily classloading client then throws `ClassNotFoundException` for classes it had not loaded yet.
- No `switch` on enums anywhere on the client or render path. Use `if` chains or int constants (`PensieveClientState` uses `static final int` phases for this reason).
- `broadcastEntityEvent` bytes 21, 35 and 63 crash the client. None are needed here. If one ever is, use 61, 62, 64, 66 or 67.
- HUD drawing goes in the existing `PensieveGuiMixin` (`Gui#render(GuiGraphics, DeltaTracker)` TAIL). Forge 51 has no HUD event.
- `RenderLevelStageEvent` stages from `AFTER_ENTITIES` on start from an identity matrix, with the camera subtracted in double precision on one anchor. `event.getPartialTick()` is the frame delta, not the partial tick. Use `PensieveClientState.partial()`.
- 1.21 API: `addAdditionalSaveData`/`readAdditionalSaveData` are public. Datapack folders are singular (`recipe/`, `loot_table/`). `runServer` is the cheapest full data check.
- Block placement in the memory dimension stays inside `SetBuilder`/session ticking under the shared `pensieveBlocksPerTick` budget, with flags `2 | 16` and `SetBuilder.clearTicks`, so nothing flows, falls or drops. No bulk placement ever happens in one tick, and nothing goes through worldgen.
- No visible lag spike on capture. Only the section copy (`PalettedContainer#copy`) and the copy of log slices run on the server thread. Rewinding, selection, string conversion, encoding and compression run on `Util.backgroundExecutor()`, and file IO runs on `Util.ioPool()`.
- Packets go on `SpellPackets.CHANNEL` and are registered in `PensieveNet.register()`. Bump `networkProtocolVersion(10)` to `11` once, in Task 6.
- Spec values: a **60 s** window and a **32-block** radius, both configurable. Watching does **not** use a memory up. The memory is stored **in the world save, not in the item**. The remembered area is centred on where the owner stood **at capture**. The memory starts after the last teleport, apparition, Floo trip or dimension change. Snap-to-owner follows the owner even off the island. Capture is refused in the memory dimension. With no phial, sneak + use of a wand still opens the spell menu.
- After each task: commit with the given message, then `timeout 60 git push`. If the push fails, report it and do not retry.

## Review Focus

1. **Capture right after arriving: a teleport, a Floo trip, joining the server, or the first tick of a world.** Expected: a shorter memory that starts after the arrival, or the action-bar hint `capture.too_soon` when less than 2 s is usable. Never a crash, and never a memory that splices two places together. Pinned by `WindowRulesTest` (Task 2) and the gametest `captureAfterTeleportStartsAfterIt` (Task 4).
2. **A block that changed several times inside the window** (a door opened and shut, TNT, fire, flowing water). Expected: the island shows the state at the window's start, and each change happens at its own tick. Pinned by `BlockRewindTest` (Task 2) and the gametest `recordedBlockChangesReplayInOrder` (Task 6).
3. **A busy area: 200 mobs, a mass of explosions, or the block log overflowing.** Expected: the memory is thinned (farthest entities and particles dropped first, the window start moved after the overflow) and stays under `pensieveCaptureMaxKb`, with no server freeze. Pinned by `WindowRulesTest.logOverflowMovesStart`, `EntitySelectTest` (Task 2) and `RecordingAssemblyTest.thinsToFitCap` (Task 4).
4. **A Memory item whose recording is missing, corrupt, from a newer format or from another world.** Expected: the action-bar message `memory_faded`, no session, and the basin and item untouched. Pinned by `RecordingCodecTest.rejectsUnknownVersion` (Task 1) and the gametest `missingRecordingFadesQuietly` (Task 6).
5. **A free ghost flying away from the island, or holding a direction into the void.** Expected: the client clamps them softly at the radius and the server snaps them back past `radius + 4`. They never reach a neighbouring slot's island and never leave the memory dimension. Pinned by `IslandBoundsTest` (Task 2) and the gametest `ghostIsClampedToIsland` (Task 6).

---

## File map

Pure (no Minecraft imports beyond what tests already load), `src/main/java/com/hogwartsmagic/pensieve/capture/`:
- `Recording` (records and nested records), `RecordingCodec`, `WindowRules`, `BlockRewind`, `SnapshotCull`, `EntitySelect`, `IslandBounds`, `RecordingScene`, `RecordedTrackSampler`.

Server, `pensieve/capture/`:
- `CaptureLogs` (per-level registry), `LevelLog`, `EntityHistory`, `PlayerChatLog`, `CaptureEvents`, `MemoryCapture`, `RecordingAssembly`, `RecordingStore`, `CaptureHooks` (static entry points the mixins call).

`pensieve/`:
- `EmptyMemoryPhialItem`, `RecordedMemoryItem`.

`pensieve/net/`:
- `PensieveCapturePacket` (c→s)
- `PensieveCaptureFxPacket` (s→c)
- `PensieveRecordingPacket` (s→c, one part of a recording)
- `PensieveGhostsPacket` (s→c)

Common mixins, `mixin/`:
- `LevelChunkCaptureMixin`
- `ServerLevelCaptureMixin`
- `LivingEntitySwingCaptureMixin`
- `ServerPlayerChatCaptureMixin`
- `PlayerGhostNoclipMixin`

Client, `client/pensieve/`:
- `RecordingCache`, `RecordedActors`, `RecordedFx`, `GhostCamera`, `SilverGhosts`, `CaptureFx`.

Tests:
- `src/test/java/com/hogwartsmagic/pensieve/capture/*Test.java`
- `src/main/java/com/hogwartsmagic/gametest/PensieveCaptureGameTests.java`: `@GameTestHolder(ExampleMod.MODID)`, template `hogwartsmagic:gametest/empty3`, batches `capture` and `recorded_sessions`. Run with `./gradlew runGameTestServer`.

---

### Task 1: The Recording model and its codec (pure)

**Files:**
- Create: `pensieve/capture/Recording.java`, `pensieve/capture/RecordingCodec.java`
- Test: `src/test/java/com/hogwartsmagic/pensieve/capture/RecordingCodecTest.java`

**Interfaces:**
- Produces (positions are relative to `centre`, in blocks; ticks run from 0 at the window's start):
  - `record Recording(UUID id, UUID owner, String ownerName, String title, long day, String dimension, int[] centre, int radius, int length, long dayTime, Blocks blocks, List<Track> tracks, List<Beam> beams, List<Cast> casts, List<Sound> sounds, List<Particle> particles, List<LevelEvt> levelEvents, List<Chat> chat)`. `Recording.FORMAT = 1`.
  - `record Blocks(List<String> palette, int size, int[] cells, List<BlockEntityData> blockEntities, List<BlockChange> changes)`:
    - `palette` holds block-state strings in `BlockStateParser.serialize` form, and index 0 is always `"minecraft:air"`.
    - `cells` is dense over the cube `[-size, size]³`, ordered x fastest, then z, then y, with `index = ((y+size)*(2size+1) + (z+size))*(2size+1) + (x+size)`, and `-1` for "nothing, do not place".
    - `static int index(int x, int y, int z, int size)`.
  - `record BlockEntityData(int x, int y, int z, String snbt)`.
  - `record BlockChange(int tick, int x, int y, int z, int state)`.
  - `record Track(int actor, String type, UUID uuid, String name, boolean player, String skinValue, String skinSignature, int first, int last, int removal, float[] x, float[] y, float[] z, float[] yaw, float[] headYaw, float[] bodyYaw, float[] pitch, List<DataPatch> data, List<EquipPatch> equipment, List<Act> acts)`:
    - The arrays have length `last - first + 1`. `removal` is `REMOVAL_NONE=0`, `REMOVAL_KILLED=1` or `REMOVAL_GONE=2`.
    - `record DataPatch(int tick, int key, byte[] value)`: one synched-data value as written by `DataValue#write`, without the id byte.
    - `record EquipPatch(int tick, int slot, byte[] stack)`: `slot` is the `EquipmentSlot` ordinal, and `stack` is written with `ItemStack.OPTIONAL_STREAM_CODEC`.
    - `record Act(int tick, int kind)`: `ACT_SWING_MAIN=0`, `ACT_SWING_OFF=1`, `ACT_HURT=2`, `ACT_DIE=3`.
  - `record Beam(int from, int to, String spell, float[] start, float[] end, int caster)`, with `caster` -1 when the caster is not recorded.
  - `record Cast(int tick, int actor, String spell, float[] target)`.
  - `record Sound(int tick, String id, float x, float y, float z, float volume, float pitch)`.
  - `record Particle(int tick, float x, float y, float z, float dx, float dy, float dz, float speed, int count, byte[] options)`.
  - `record LevelEvt(int tick, int type, int x, int y, int z, int data)`.
  - `record Chat(int tick, String sender, String text)`, with `sender` null for a system line.
  - `final class RecordingCodec`:
    - `static byte[] write(Recording r, boolean forClient)`: a `forClient` blob carries `Blocks` with an empty palette, `size 0`, no cells and no changes, because the server builds blocks.
    - `static Recording read(byte[] data) throws IOException`.
    - Layout: a magic `"HPM1"`, the format varint, then a `Deflater(BEST_SPEED)` stream of `DataOutputStream` fields.
    - Float track arrays are stored quantised: positions in 1/512 block and angles in 1/64 degree, as zig-zag varint deltas from the previous tick.
    - Cells are stored as palette varints.
- Consumes: nothing.

- [ ] **Step 1: Write the failing tests**
  - `roundTripsEverything`: one recording with 2 palette states, a 5³ cube (`size 2`), one block entity, two changes, one track of 40 ticks with two data patches, one equipment patch and one act, plus one each of beam, cast, sound, particle, level event and chat. After `read(write(r,false))`, every field equals the original. Floats must match within 1/512 for positions and 1/64 for angles.
  - `clientBlobHasNoBlocks`: `read(write(r,true)).blocks().cells().length == 0` and the tracks are intact.
  - `staticCrowdIsSmall`: 64 tracks × 1200 ticks of a standing entity, plus a 65³ cube that is all air but for a 65×65 floor of one state. `write(r,false).length < 60_000`.
  - `rejectsUnknownVersion`: a blob whose format varint is 99 → `IOException` whose message contains `"format 99"`.
  - `rejectsGarbage`: 100 random bytes → `IOException`, with no other exception type escaping.
- [ ] **Step 2: Run them and expect FAIL.** `./gradlew test --tests "com.hogwartsmagic.pensieve.capture.*"`
- [ ] **Step 3: Implement.** `read` wraps every `RuntimeException` (bad lengths, `EOF`) into an `IOException`. Lengths are capped while reading (palette ≤ 65,536, list sizes ≤ 5,000,000, `size` ≤ 128), so a corrupt file cannot allocate gigabytes.
- [ ] **Step 4: Run the tests and expect PASS.**
- [ ] **Step 5: Commit** `Add the Pensieve recording model and codec`.

### Task 2: Window, rewind, culling, selection and bounds rules (pure)

**Files:**
- Create: `pensieve/capture/WindowRules.java`, `BlockRewind.java`, `SnapshotCull.java`, `EntitySelect.java`, `IslandBounds.java`
- Test: `src/test/java/com/hogwartsmagic/pensieve/capture/{WindowRulesTest,BlockRewindTest,SnapshotCullTest,EntitySelectTest,IslandBoundsTest}.java`

**Interfaces:**
- Produces:
  - `WindowRules`:
    - `static final int MIN_TICKS = 40`, `JUMP_BLOCKS = 8`, `WATCH_MARGIN = 48`.
    - `static long start(long now, int lengthTicks, long logValidSince, long lastDiscontinuity, long[] ownerTicks, double[] ownerXyz, double[] centre, int radius)` returns the earliest usable game tick, which is the maximum of:
      - `now - lengthTicks + 1`
      - `logValidSince`
      - `lastDiscontinuity + 1`
      - one tick after the last owner sample farther than `radius + WATCH_MARGIN` horizontally (xz) or 32 vertically from `centre`
      - one tick after any owner step longer than `JUMP_BLOCKS`
      - the owner's first sample
    - `static boolean usable(long start, long now)`, which is `now - start + 1 >= MIN_TICKS`.
  - `BlockRewind`:
    - `record Change(long tick, int index, int oldState, int newState)`.
    - `static int[] rewind(int[] current, List<Change> inWindowChronological)` gives the cells at the window's start: walk the changes backwards and set `cells[index] = oldState`.
    - `static List<Change> forward(List<Change>)`: the same changes, chronological and unchanged. Kept so the caller does not depend on the order.
  - `SnapshotCull`:
    - `static void cull(int[] cells, int size, int radius, IntPredicate opaque, Set<Integer> keep)`:
      - Sets `-1` on every cell outside the sphere `x²+y²+z² ≤ radius²` and on air (state 0).
      - Also sets `-1` on every cell whose six neighbours are all opaque, inside the sphere, and not air, unless its index is in `keep` (positions that change during the window).
      - The "fully hidden" test uses the cells before any culling.
  - `EntitySelect`:
    - `record Candidate(int actor, boolean player, boolean owner, double minDistance)`.
    - `static List<Integer> pick(List<Candidate>, int max)`: the owner always goes first, then players, then everyone else by `minDistance` ascending. The candidates are those whose samples come within `radius + 4` of the centre at any time in the window; filtering is the caller's job.
  - `IslandBounds`:
    - `static double[] clamp(double x, double y, double z, double cx, double cy, double cz, double radius)` gives the point itself when it is within `radius` of the centre, else its projection onto that sphere.
    - `static boolean outside(double x, double y, double z, double cx, double cy, double cz, double limit)`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing tests**
  - `WindowRulesTest`:
    - `fullWindowWhenNothingHappened`: now 5000, length 1200, the owner stood still since tick 0 → `3801`.
    - `startsAfterTeleport`: `lastDiscontinuity` 4500 → `4501`.
    - `startsAfterJump`: owner samples step 10 blocks between ticks 4200 and 4201 → `4201`.
    - `startsWhenOwnerArrived`: the owner was 100 blocks away until tick 4300 → `4301`.
    - `logOverflowMovesStart`: `logValidSince` 4700 → `4700`.
    - `tooSoon`: `usable(4980, 5000)` is false and `usable(4961, 5000)` is true.
  - `BlockRewindTest`:
    - `doorOpenedAndShut`: current `[D_closed]`, changes `(t10, 0, closed→open)` and `(t30, 0, open→closed)` → rewind `[D_closed]`, `forward` keeps both.
    - `placedThenBroken`: current `[air]`, changes `(t5, air→stone)` and `(t9, stone→air)` → `[air]`.
    - `brokenOnly`: current `[air]`, change `(t5, stone→air)` → `[stone]`.
  - `SnapshotCullTest`: a solid 5³ stone cube with `size 2` and `radius 2` keeps only the sphere's surface cells. The centre cell is culled unless it is in `keep`. Corners outside the sphere are culled.
  - `EntitySelectTest`: 10 candidates with `max` 4 → owner, the 2 players, then the nearest non-player.
  - `IslandBoundsTest`: `(40,0,0)` around the origin with radius 32 → `(32,0,0)`; `(10,5,0)` is unchanged; `outside(37,0,0, 0,0,0, 36)` is true.
- [ ] **Step 2: Run and expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run and expect PASS.**
- [ ] **Step 5: Commit** `Add the capture window, rewind and selection rules`.

### Task 3: Recording to scene conversion and recorded-track sampling (pure)

**Files:**
- Create: `pensieve/capture/RecordingScene.java`, `RecordedTrackSampler.java`
- Modify: `pensieve/scene/SceneDef.java`, javadoc only. It documents the event kind `recorded_beam`, which is never valid in a JSON file: `SceneValidator`'s `EVENT_KINDS` stays as it is, so the validator still rejects it in shipped scenes.
- Test: `src/test/java/com/hogwartsmagic/pensieve/capture/RecordingSceneTest.java`, `RecordedTrackSamplerTest.java`

**Interfaces:**
- Consumes: `Recording` (Task 1); `SceneDef` and its nested records, `CameraTrack.EMPTY`, `Track.EMPTY` (plan 1).
- Produces:
  - `RecordingScene`:
    - `static final String PREFIX = "recorded/"`. `static String sceneId(UUID id)` returns `PREFIX + id`, and `static Optional<UUID> idOf(String sceneId)`.
    - `static String actorId(Recording.Track t)` returns `"owner"` for the owner's track, `"p_" + name` for other players (`"p_" + name + "_" + actor` when a name repeats), and `"e" + actor` for everyone else.
    - `static SceneDef toScene(Recording r)` builds:
      - `id = sceneId(r.id())`, `title = r.title()`, `length = r.length()`, `radius = r.radius()`, `fogFar 0`, `next null`, `endAction null`, `camera = CameraTrack.EMPTY`.
      - `set = new SetDef(List.of(), List.of(), List.of(), List.of(), r.dayTime() % 24000, 60)`. The server builds the blocks itself.
      - One `ActorDef` per track:
        - `type` is `"player"` for players, else the track's type.
        - `skin` is `new SkinRef(null, name, false)` for players and null otherwise.
        - `name`, `items` empty, `path`/`look` `Track.EMPTY`, no limbs, poses or anims, `scale 1`, `life false`.
        - `actions`: `swing` for `ACT_SWING_MAIN`, `hurt`, `die`, and `cast` for each `Cast` of that actor, with `Target(null, target)`.
        - `ACT_SWING_OFF` becomes `swing` with `slot "offhand"`.
      - `events`:
        - each `Sound` → `EventDef(t, "sound", id, {x,y,z}, volume, pitch, -1, null, 0, NaN, null, NaN)`
        - each `Beam` → `EventDef(from, "recorded_beam", spell, start, 1, 1, -1, null, to - from, NaN, null, NaN, new float[][]{start, end})`
        - sorted by `t`.
      - `words`: each `Chat` → `WordDef(t, sender, escape(text), false, 0, 1f, false, null)`. `escape` puts U+200B after every `{`, so a typed `{viewer}` is never filled in.
      - Particles, level events, data and equipment patches are not in the `SceneDef`. The client plays them from the `Recording` (Task 7).
  - `RecordedTrackSampler`:
    - `static boolean present(Recording.Track t, float tick)`, which is `first ≤ tick ≤ last + (removal == KILLED ? 20 : 0)`.
    - `static float[] pose(Recording.Track t, float tick)` returns `{x, y, z, yaw, headYaw, bodyYaw, pitch}`, lerped between whole ticks and clamped to the array ends. Angles are lerped the short way round (`Mth.wrapDegrees` logic, written out pure).
    - `static int patchesUpTo(List<?> sortedByTick, int fromIndex, int tick)` returns the index after the last patch with `tick ≤ tick`.

- [ ] **Step 1: Write the failing tests**
  - `RecordingSceneTest`:
    - `ownerIsNamedOwnerAndPlayersArePlayers`: tracks owner `Alon` plus player `Bea` plus a zombie → actor ids `owner`, `p_Bea`, `e2`; types `player`, `player`, `minecraft:zombie`; Bea's skin username is `"Bea"`.
    - `actsBecomeActions`: swing at 10, hurt at 12, die at 40 → those actions at those `t`.
    - `beamBecomesRecordedBeam`: a beam from 5 to 17 → an event `recorded_beam` at `t` 5 with `duration` 12 and `box[1]` equal to the end.
    - `chatEscapesPlaceholders`: `"hi {viewer}"` → the text contains `"{​viewer}"`.
    - `sceneIdRoundTrips`.
  - `RecordedTrackSamplerTest`:
    - `lerpsBetweenTicks`: x `[0, 2]` at `first` 10 → `pose(t, 10.5)[0] == 1`.
    - `yawWrapsShortWay`: yaw 179 → -179 → the midpoint is ±180, not 0.
    - `absentOutsideLife`.
    - `killedLingersForDeathAnimation`.
- [ ] **Step 2: Run and expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run and expect PASS. `SceneDataTest` and `SceneValidatorTest` still pass.**
- [ ] **Step 5: Commit** `Turn recordings into Pensieve scenes`.

### Task 4: The always-on recorder and capture assembly (server)

**Files:**
- Create: `pensieve/capture/{CaptureLogs,LevelLog,EntityHistory,PlayerChatLog,CaptureHooks,CaptureEvents,RecordingAssembly,RecordingStore}.java`
- Create mixins (common `mixins` list in `hogwartsmagic.mixins.json`): `LevelChunkCaptureMixin`, `ServerLevelCaptureMixin`, `LivingEntitySwingCaptureMixin`, `ServerPlayerChatCaptureMixin`
- Modify:
  - `Config.java`: `pensieveCaptureEnabled` true, `pensieveCaptureSeconds` 60 (10..120), `pensieveCaptureRadius` 32 (8..64), `pensieveCaptureMaxEntities` 64 (1..256), `pensieveCaptureMaxBlockChanges` 200000 per level (1000..2000000), `pensieveCaptureMaxKb` 4096 (256..16384).
  - `SpellPackets.castAs`: after a cast has fired (past the mana and cooldown gates, where the mana is taken), call `CaptureHooks.onCast(player, spellId)`.
  - `pensieve/PensieveBlockEntity.pour`: call `RecordingStore.prefetch(server, id)` when the stack is a `RecordedMemoryItem` (Task 5; until then guard with `instanceof` on the interface plus `RecordingScene.idOf`).
- Test: `src/test/java/com/hogwartsmagic/pensieve/capture/RecordingAssemblyTest.java`; gametests in `PensieveCaptureGameTests` (batch `capture`).

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces:
  - `CaptureLogs`:
    - `static LevelLog of(ServerLevel)`: lazily created. Returns null when capture is disabled, the level is the memory dimension (`PensieveDimension.isMemory`), or the level has no players.
    - `static PlayerChatLog chat(UUID)`.
    - `static void forget(ServerLevel)` on level unload and `forgetPlayer(UUID)` on logout.
    - `static void tick(ServerLevel)`, called from `CaptureEvents` on `TickEvent.LevelTickEvent` phase `END`, server side.
  - `LevelLog` (all times are the level's game time). Logs are trimmed every 20 ticks to `pensieveCaptureSeconds * 20 + 40` ticks.
    - **Watched chunks.** A `LongOpenHashSet` of the chunks within `radius + WatchMargin(48)` of any non-spectator player, rebuilt each tick in `tick`. Every hook ignores positions outside it.
    - **Block changes.** Primitive ring arrays `long tick`, `long pos`, `int old`, `int new`. States are stored as `Block.getId(state)`. `long validSince()` moves forward when the ring overwrites an entry younger than the window, and the ring has `pensieveCaptureMaxBlockChanges` slots.
    - **Entities.** `Map<UUID, EntityHistory>`.
    - **Event lists.** Beams (open and closed, by entity id), casts, sounds, particles, level events.
  - `EntityHistory`:
    - Per tick: `x, y, z, yRot, yHeadRot, yBodyRot, xRot`. For non-living entities, head and body are the yaw.
    - Synched-data diffs. In `tick`, when `entity.getEntityData().isDirty()` (the flags survive until the next tick's `ChunkMap` send), read `getNonDefaultValues()` and store only values whose `value` changed since the last stored one. Also store a full snapshot at the entity's first sample. Encode each value with a `RegistryFriendlyByteBuf` over `level.registryAccess()`. Read the Forge 51 constructor in the decomp jar first: if it also takes a connection-type argument, pass the vanilla or plain value it offers, and use the same constructor on the client in Task 7.
    - Equipment diffs per `EquipmentSlot` (`ItemStack.matches`), and acts.
    - Entities sampled: every entity in a watched chunk within `radius + 48` of a player, except `SpellBeamEntity`, `PensieveBodyEntity` and spectators. A history is dropped 60 s after its entity was last sampled.
  - `PlayerChatLog`: a ring of `(tick, sender, text)` for what this player received. It also holds `long lastDiscontinuity`, set from `EntityTeleportEvent`, `PlayerChangedDimensionEvent`, respawn and login.
  - `CaptureHooks` (static; called by the mixins and events, each guarded by `level instanceof ServerLevel` and `CaptureLogs.of(level) != null`):
    - `onBlockChanged(Level, BlockPos, BlockState old, BlockState now)`. `LevelChunkCaptureMixin` calls it at `setBlockState` RETURN with `cir.getReturnValue()` as the old state, skipping a null return and a return equal to the new state.
    - `onSound(ServerLevel, double x, y, z, Holder<SoundEvent>, float volume, float pitch)`, from both `playSeededSound` overloads (the entity overload records the entity's position).
    - `onParticles(ServerLevel, ParticleOptions, x, y, z, count, dx, dy, dz, speed)`, from the public `sendParticles` overload that broadcasts. Options are encoded with `ParticleTypes.STREAM_CODEC`.
    - `onLevelEvent(ServerLevel, int type, BlockPos, int data)`.
    - `onExplosion(ServerLevel, double x, y, z, float radius)`, at `ServerLevel.explode` RETURN. It is recorded as one `Particle` of `ParticleTypes.EXPLOSION_EMITTER` and one `Sound` `minecraft:entity.generic.explode`.
    - `onSwing(LivingEntity, InteractionHand)`, at `LivingEntity.swing(InteractionHand, boolean)` HEAD, server side.
    - `onCast(ServerPlayer, String spellId)`: only for spells in `SceneNames.SPELL_VISUALS` minus `SceneNames.SPELL_BEAMS`. The target is the eye position plus the look vector × 16.
    - `onChat(ServerPlayer receiver, @Nullable String sender, String text)`, from `ServerPlayerChatCaptureMixin` on `sendChatMessage(OutgoingChatMessage, boolean, ChatType.Bound)` (sender = the bound chat type's name string) and on `sendSystemMessage(Component, boolean overlay)` when `!overlay`.
    - Forge events in `CaptureEvents`:
      - `LivingDamageEvent` → `ACT_HURT`, and `LivingDeathEvent` → `ACT_DIE` plus removal `KILLED`.
      - `EntityJoinLevelEvent`/`EntityLeaveLevelEvent` for `SpellBeamEntity` open and close a `Beam`, with the start and end positions, the spell id, and the caster from `getOwner()`.
      - `EntityTeleportEvent` and `PlayerChangedDimensionEvent` set discontinuities.
      - `LevelEvent.Unload` → `forget`, and `PlayerLoggedOutEvent` → `forgetPlayer`.
  - `RecordingAssembly`:
    - `static CompletableFuture<Recording> assemble(ServerPlayer owner, UUID id, String title)`.
    - On the server thread:
      - Compute the window with `WindowRules.start`.
      - Copy each `LevelChunkSection#getStates()` (`PalettedContainer#copy`) for the sections intersecting the cube of `size = radius`. Unloaded chunks and out-of-height sections read as air.
      - Copy the block-change slice inside the cube and window as `BlockRewind.Change` with cube indices, the selected `EntityHistory` slices (rebased to tick 0), and the event slices, rebased and translated to the centre.
      - Copy the allow-listed block entities' `saveWithoutMetadata(registries)` as SNBT (sign, hanging sign, banner, skull).
      - Copy the owner's chat slice.
      - Copy each recorded player's `"textures"` property from their `GameProfile`.
      - The centre is `owner.blockPosition()`.
    - On `Util.backgroundExecutor()`:
      - Build the current cells.
      - `BlockRewind.rewind`, then `SnapshotCull.cull`. `opaque` comes from a precomputed `boolean[]` over the palette (`state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)`, evaluated on the server thread when the palette is built). `keep` is the changed indices.
      - Map states to palette strings.
      - `EntitySelect.pick` with `pensieveCaptureMaxEntities`.
    - **Thin to fit:** encode, and while the result is over `pensieveCaptureMaxKb`, drop all particles, then the farthest non-owner track, one at a time. Throw only if the owner plus blocks alone are over the cap (`capture.too_big`).
    - Returns null-free `Recording`s; failure is an exceptional future.
  - `RecordingStore`:
    - `static Path dir(MinecraftServer)` returns `getWorldPath(LevelResource.ROOT)/data/hogwartsmagic_memories`.
    - `static CompletableFuture<Void> save(MinecraftServer, Recording)` writes the full codec bytes to `<uuid>.hpm.tmp` and atomically moves the file into place, on `Util.ioPool()`.
    - `static Optional<Recording> load(MinecraftServer, UUID)`: sync, from cache or disk. An `IOException` or a missing file → empty, logged once per id.
    - `static void prefetch(MinecraftServer, UUID)`: an async load into the cache.
    - The cache is an LRU of 8 decoded recordings plus their client blobs (`byte[] clientBlob(UUID)`, encoded lazily, once).
    - `static void overrideForTests(Function<UUID, Optional<Recording>>)`.

- [ ] **Step 1: Write the failing tests**
  - `RecordingAssemblyTest.thinsToFitCap`: drive the pure thinning helper `RecordingAssembly.thin(Recording, int maxBytes)` (package-private, pure) with 50 noisy tracks and 10,000 particles. The result is under the cap, has no particles, still holds the owner, and its dropped tracks are the farthest.
  - Gametests (`capture` batch, `makeMockServerPlayerInLevel` as the owner):
    - `blockChangeIsLoggedWithOldAndNew`: set stone, then dirt, near the player → two entries with matching old and new ids.
    - `memoryDimensionIsNotRecorded`: a change in `PensieveDimension.level` is not logged.
    - `farChangeIsIgnored`: a change 200 blocks from every player is not logged.
    - `entityIsSampledWithData`: spawn a sheep, dye it red after 5 ticks, wait 10 → its history has a data patch at tick ≥ 5.
    - `assembleRebuildsWindowStart`: place stone, wait 30 ticks, break it, wait 30, assemble → the snapshot cell is stone and one change goes to air at tick ≈ 30.
    - `captureAfterTeleportStartsAfterIt`: `teleportTo` 3 blocks (fires `EntityTeleportEvent` through `CaptureEvents`' listener), wait 50 ticks, assemble → `length` ≤ 50.
    - `storeRoundTrips`: `save` then clear the cache, then `load` returns an equal recording.
- [ ] **Step 2: Run `./gradlew test` and `./gradlew runGameTestServer`; expect FAIL.**
- [ ] **Step 3: Implement.** Keep every hook to a few field writes and an `O(1)` watched-chunk lookup, because `setBlockState` and `playSeededSound` are hot paths.
- [ ] **Step 4: Run them and expect PASS, then do the cost check.** `runGameTestServer` passes. `runServer` boots clean. Add a temporary gametest `samplerCost` that spawns 200 zombies around one mock player, times `CaptureLogs.tick` over 100 ticks and logs the mean. It must be under 2 ms per tick on the dev machine. Record the number in the commit body, then delete the test.
- [ ] **Step 5: Commit** `Keep a rolling record of every player's surroundings`.

### Task 5: Phials, the capture gesture and its look

**Files:**
- Create:
  - `pensieve/EmptyMemoryPhialItem.java`, `pensieve/RecordedMemoryItem.java`, `pensieve/capture/MemoryCapture.java`
  - `pensieve/net/PensieveCapturePacket.java` (c→s, no fields) and `pensieve/net/PensieveCaptureFxPacket.java` (s→c `int entityId, boolean mainHandWand`)
  - `client/pensieve/CaptureFx.java`
  - `data/hogwartsmagic/recipe/empty_memory_phial.json`
  - `build_scripts/generate_memory_phial_textures.py`, which writes `empty_memory_phial.png` and `memory.png` (animated silver wisps plus `.mcmeta`, the style of `generate_cave_memory_texture.py`), and the item models
- Modify:
  - `ExampleMod.java`: register `EMPTY_MEMORY_PHIAL` (stacks to 16) and `MEMORY` (`RecordedMemoryItem`, stacks to 1), and add both to `MAIN_TAB`.
  - `event/ModEvents.handleWandUse` (~L3964): client side, when `player.isShiftKeyDown()` and the *other* hand holds an `EmptyMemoryPhialItem`, send `PensieveCapturePacket`, swing, and return true instead of `openMenu`.
  - `PensieveNet.register()`: both packets.
  - `ClientRuntimeBridge`: action `pensieveCaptureFx` → `CaptureFx.start`.
  - `ModSounds` and `sounds.json`: `pensieve.capture`.
  - `lang/en_us.json`: the items; `item.hogwartsmagic.memory.named` = `"%s's Memory — Day %s"`; `message.hogwartsmagic.pensieve.capture.{need_wand,too_soon,too_big,inside,disabled,failed}` and `message.hogwartsmagic.pensieve.memory_faded`; the subtitle.
- Test: gametests in `PensieveCaptureGameTests` (batch `capture`).

**Interfaces:**
- Consumes: `RecordingAssembly`, `RecordingStore`, `RecordingScene.sceneId`.
- Produces:
  - `EmptyMemoryPhialItem.use`: client side, while sneaking with any wand in the other hand, send `PensieveCapturePacket` and return `sidedSuccess`. Sneaking without a wand shows the hint `capture.need_wand` on the action bar (client side, no packet). Not sneaking: pass.
  - `RecordedMemoryItem implements PensieveMemory`:
    - `sceneId(stack)` returns `RecordingScene.sceneId(id)` read from `DataComponents.CUSTOM_DATA` `{MemoryId, Owner, OwnerName, Day}`.
    - `getName(stack)` is `item.hogwartsmagic.memory.named` (OwnerName, Day). An anvil rename (`CUSTOM_NAME`) wins, as in vanilla.
    - The tooltip shows `Day N` and the length in seconds (`Seconds` in the tag).
    - `consumedOnWatch` is false.
  - `MemoryCapture.capture(ServerPlayer)`, the server handler of the packet. Checks, in this order:
    - **Silent no-ops:** not alive or a viewer → no-op; a cooldown of 20 ticks per player (`PlayerMagicData` mod data `"PensieveCaptureAt"`).
    - **Hinted refusals:** capture disabled → `disabled`; in the memory dimension → `inside`; no wand in one hand plus a phial in the other → `need_wand`; window not usable → `too_soon`.
    - **Capture:** shrink one phial at once, so a double press cannot capture twice. Broadcast `PensieveCaptureFxPacket` to the player and every tracker, play `pensieve.capture` at the player, and call `assemble`.
    - **On completion** (`server.execute`): `RecordingStore.save`, then give a `MEMORY` stack, into the hand that held the phial if it is empty, else the inventory, else drop it at the player's feet.
    - **On failure:** give the phial back and show `too_big` or `failed`.
    - `title` is `"<name>'s Memory — Day <day>"`, where `day = overworld.getDayTime() / 24000 + 1`.
  - `CaptureFx.start(entityId, mainHandWand)`: a 30-tick effect.
    - A silver thread (`RenderLevelStageEvent` `AFTER_PARTICLES`, identity matrix, one camera-relative anchor) drawn as a curved ribbon from the entity's temple (eye position + 0.25 to the side of the wand hand, + 0.1 up) along the wand tip to the phial hand, growing in over 18 ticks and fading over the last 12.
    - Small `minecraft:end_rod` motes along it.
    - For the local player only, the camera pitches back by up to 8° over the first 10 ticks and eases back by tick 30, through the existing `ComputeCameraAngles` handler.
- [ ] **Step 1: Write the failing gametests**
  - `captureTurnsPhialIntoMemory`: a mock player with a student wand in the main hand and a phial in the offhand, 60 ticks old → `MemoryCapture.capture`, then wait for the future (poll 40 ticks) → the offhand holds `MEMORY`, `RecordingStore.load` finds it, and its `ownerName` is the player's.
  - `noWandMeansHintAndNoCapture`: a phial in the offhand and an empty main hand → still a phial, and no file written.
  - `insideMemoryRefused`: a player placed in `PensieveDimension.level` → still a phial.
  - `wandWithoutPhialChangesNothing`: `MemoryCapture.capture` with no phial anywhere → nothing happens. This is the server half; the client half is that `handleWandUse` only diverts when a phial is held, checked by code review.
- [ ] **Step 2: Run and expect FAIL.**
- [ ] **Step 3: Implement.** Textures: follow the project skill `texturing-minecraft-items` (16×16 sprites; the filled Memory is the empty phial plus silver wisps). Sound: follow the ElevenLabs pipeline (key file local, never committed; MP3 → mono OGG; envelope-trim and level-match). The prompt is *a soft breathy whisper drawn out like a thread, with a faint silver shimmer*. If the key file is missing, alias `minecraft:block.amethyst_block.chime` in `sounds.json` and say so in the commit.
- [ ] **Step 4: Run and expect PASS; `runServer` boots clean.**
- [ ] **Step 5: Commit** `Draw memories into phials with a wand`.

### Task 6: Recorded sessions on the server: building, replaying, bounds and ghosts

**Files:**
- Create: `pensieve/net/PensieveRecordingPacket.java` (s→c `String sceneId, int part, int parts, byte[] data`, at most 900,000 bytes of data per part), `pensieve/net/PensieveGhostsPacket.java` (s→c `List<UUID> ghosts`), `mixin/PlayerGhostNoclipMixin.java` (common), `pensieve/PensieveGhost.java` (common flag holder)
- Modify:
  - **`PensieveSessions`:**
    - `lookup`: for an id with `RecordingScene.PREFIX`, `RecordingStore.load` → `LoadedScene(RecordingScene.toScene(rec), "")`, and the session keeps the `Recording`.
    - A missing recording → `memory_faded` on the action bar and no session.
    - `createSession` skips the `PensieveStartPacket.fits` check for recorded scenes.
    - `newSession` builds `SetBuilder` with the snapshot.
    - `tickViewer` for a recorded session calls `bound(s, p)` instead of `hold(p, s.anchor())` while `INSIDE`.
    - `sendStart` sends the recording parts first (once per viewer, `Viewer.recordingSent`) and then the start packet.
    - Viewer membership changes send `PensieveGhostsPacket` to every viewer inside.
  - `Session`: `@Nullable Recording recording`, a list of recorded block changes (`recordedChanges`, with its own cursor), and an `@Nullable Vec3 anchorOverride` that `anchor()` returns when set.
  - `Session.Viewer`: `boolean recordingSent`.
  - `SetBuilder`: a constructor `SetBuilder(ServerLevel, BlockPos origin, Recording.Blocks snapshot, int radius)`. `prepare()` adds each `cells[i] >= 0` at `origin + (x, y, z)`, with palette strings parsed by `BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), s, false)`. An unknown block becomes air, warned once. When the blocks are done, block entities load with `loadWithComponents(TagParser.parseTag(snbt), registries)`.
  - `applyBlockEvents` also applies due `recordedChanges` with `SetBuilder.FLAGS`, `clearTicks`, `eventPlaced` and `RegionData.expand`, the same way story block events do.
  - `SpellPackets`: `networkProtocolVersion(10)` → `11`.
  - `PensieveNet.register()`: both packets.
  - `ClientRuntimeBridge`: actions `pensieveRecording` → `RecordingCache.accept`, and `pensieveGhosts` → `SilverGhosts.set` (no-op stubs until Tasks 7 and 8).
  - `hogwartsmagic.mixins.json` `mixins`: `PlayerGhostNoclipMixin`.
- Test: gametests in `PensieveCaptureGameTests` (batch `recorded_sessions`), using `PensieveSessions.overrideForTests` plus `RecordingStore.overrideForTests` with hand-built recordings.

**Interfaces:**
- Consumes: Tasks 1–5; plan 1's session flow.
- Produces:
  - **The anchor of a recorded session.** It is the owner track's position at tick 0, moved 3 blocks back along its yaw and up 1.6, clamped into the island with `IslandBounds.clamp(..., radius - 2)`. With no owner track it is `origin + (0.5, 1, 0.5)`.
  - **`PensieveSessions.bound(Session, ServerPlayer)`:**
    - Apply the ghost state (`applyGhost`).
    - Teleport the player to `IslandBounds.clamp(pos, centre, radius)` only when `IslandBounds.outside(pos, centre, radius + 4)`, where the centre is `origin + (0.5, 0, 0.5)`.
    - Zero the fall distance.
  - **`static boolean PensieveSessions.isFreeGhost(Player)`:** true for a viewer at `INSIDE` in a recorded session.
  - **`PensieveGhost` (common):**
    - `static volatile boolean localNoclip`, set by client code.
    - `static boolean noclip(Player p)`: on the client, `localNoclip && p.isLocalPlayer()`; on the server, `PensieveSessions.isFreeGhost(p)`.
    - `PlayerGhostNoclipMixin` injects into `Player#tick` after the `noPhysics` PUTFIELD (`shift = AFTER`) and sets `noPhysics = true` when `PensieveGhost.noclip(this)`.
  - **Recording parts:** `RecordingStore.clientBlob(id)` split into `parts = ceil(len / 900000)`, sent in order, then `PensieveStartPacket(sceneId, "", origin, start, 60, preview)`.
  - **`PensieveGhostsPacket`:** the UUIDs of the *other* viewers at `INSIDE` in the same recorded session.
- [ ] **Step 1: Write the failing gametests**
  - `recordedMemoryBuildsSnapshot`: a 5³ recording with a 3×1×3 stone floor (`size 2`) → dive (mock player, basin holding a `MEMORY` stack with that id) → after the build the 9 cells are stone at `origin + (x, -1, z)`, and air cells were never placed.
  - `recordedBlockChangesReplayInOrder`: changes at ticks 5 (stone → dirt) and 15 (dirt → air) on one cell → the state at PLAY + 6 is dirt, and at PLAY + 16 is air.
  - `ghostIsClampedToIsland`: during PLAY, `teleportTo` the viewer 50 blocks from the centre (radius 8) → the next tick they are within `radius + 0.01` of the centre and still a viewer.
  - `ghostCanMoveInsideIsland`: move the viewer 3 blocks inside the island → on the next tick they are still at that spot (not pinned to the anchor).
  - `missingRecordingFadesQuietly`: the basin holds a `MEMORY` stack with an unknown id, dive → no session (`isViewer` false), and the stack is still in the basin.
  - `recordedSessionLifecycle`: plan 1's lifecycle on a recorded memory. Break the basin while inside → home within 20 ticks. The body is hit to below a third → yanked. `onLogin` with the return tag → back at the return point.
  - `joinerGetsSameRecording`: a second viewer dives 40 ticks later → the same session and the same `startGameTime`, and both have `recordingSent`.
- [ ] **Step 2: Run and expect FAIL.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them; gametests and unit tests PASS (including `StartPacketSizeTest`), and `runServer` boots clean.**
- [ ] **Step 5: Commit** `Replay recorded memories in the Pensieve`.

### Task 7: Client playback of recordings

**Files:**
- Create: `client/pensieve/RecordingCache.java`, `client/pensieve/RecordedActors.java`, `client/pensieve/RecordedFx.java`
- Modify:
  - **`PensieveClientState.onStart`:** for an id with `RecordingScene.PREFIX`, take the scene from `RecordingCache.scene(id)` instead of parsing the JSON. If the recording is not complete yet, keep `startPending` and retry on each client tick for up to 200 ticks, then `requestLeave()`. Expose `static Recording recording()` (null for story memories).
  - **`MemoryActors.create`/`step`:** when `PensieveClientState.recording()` holds a track for `def.id()` (through `RecordedActors.trackFor(id)`), step that actor with `RecordedActors.step(a, t)` and skip `root`, look, life, ride and boat logic.
  - **`MemoryActors.renderAll`:** skip actors that are not `RecordedTrackSampler.present`.
  - **`MemoryEvents`:** event kind `recorded_beam` → `SpellVisuals.beamTimed(level, from, to, spellId, durationTicks)`.
  - **`SpellVisuals`:** add `beamTimed`, the existing beam with speed `dist / max(1, ticks)` and no cast sound, because the recorded sound already plays.
  - **`ActorSkins`:** add `static PlayerSkin skinForProfile(GameProfile)`, cached, through `SkinManager.getOrLoad`, falling back to `DefaultPlayerSkin.get(uuid)` until it loads.
  - **`ActorSkinMixin`:** prefer `RecordedActors.skin(player)` when non-null.
  - `ClientRuntimeBridge`'s `pensieveRecording` → `RecordingCache.accept`.
  - **Clearing:** `PensieveClientState` clears `RecordingCache` on exit and on `onEnter`.

**Interfaces:**
- Consumes: Tasks 1, 3 and 6.
- Produces:
  - `RecordingCache`: `accept(PensieveRecordingPacket)` reassembles by `(sceneId, parts)`; a new id drops older partials. `@Nullable SceneDef scene(String id)` decodes once with `RecordingCodec.read`, then `RecordingScene.toScene`, and keeps the `Recording` (`@Nullable Recording recording(String id)`). A decode `IOException` is logged and treated as missing, so the 200-tick fallback leaves.
  - `RecordedActors`:
    - `trackFor(String actorId)`.
    - `step(ActorRuntime a, float t)`:
      - Position, `yRot`, `yHeadRot`, `yBodyRot` and `xRot` come from `RecordedTrackSampler.pose`, with `setOldPosAndRot` first, the way plan 1 steps actors.
      - Data patches up to `t` are applied in order: each is decoded with `buf.readVarInt` (serializer) and `DataValue.read`, as the encoder wrote it (check `SynchedEntityData.DataValue` in the decomp jar and mirror the Task 4 encoder exactly), then `entity.getEntityData().assignValues(list)`. One failure clears that actor's remaining patches and logs once.
      - Equipment patches are decoded with `ItemStack.OPTIONAL_STREAM_CODEC` and applied with `setItemSlot`.
      - The walk animation comes from the horizontal speed between ticks (`walkAnimation.setSpeed` and `update`, as plan 1 does).
      - Both buffers are `RegistryFriendlyByteBuf` over `level.registryAccess()`.
    - `@Nullable PlayerSkin skin(AbstractClientPlayer)`: for a recorded player actor, a `GameProfile(uuid, name)` with the recorded `textures` property (when non-null) → `skinForProfile`. With no property, the online `PlayerInfo` by UUID, else the name.
    - Recorded player puppets are created as `RemotePlayer(level, new GameProfile(track.uuid, name))`. The uuid stays the original player's; the entity id is negative as in plan 1.
  - `RecordedFx.tick(float t)` (called from `MemoryEvents`' tick):
    - Particles due since the last tick are decoded with `ParticleTypes.STREAM_CODEC` and added `count` times with the vanilla `sendParticles` spread formula (`count == 0`: one particle with the delta as speed).
    - Level events go to `level.levelEvent(null, type, origin + pos, data)`.
    - On a late join, events older than 2 ticks are skipped.
- [ ] **Step 1: Build check.** `./gradlew compileJava test runServer` (the server must still boot: no client class reached from common code).
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Visual check in the dev client** (close it before any later gradle run):
  - In a world, with a zombie and a dyed sheep nearby, run a few circles, sneak, swing a sword, open and shut a door, place and break blocks, cast Stupefy at the zombie, take a hit, light TNT, and type a chat line.
  - Then capture, pour the memory into a Pensieve and dive.
  - Confirm and screenshot each of these:
    - the island built from shadow with the pre-window blocks
    - the TNT crater appears at its moment
    - your own puppet with your skin, sneaking, swinging, with the sword
    - the zombie's arms up and its hurt flash
    - the sheep's colour
    - the beam flying and hitting
    - the door swinging
    - the chat line in silver
    - muffled sounds
    - the dropped items from the TNT as item puppets
- [ ] **Step 4: Commit** `Play recorded memories on the client`.

### Task 8: Free ghost camera, snap-to-owner and silver ghosts

**Files:**
- Create: `client/pensieve/GhostCamera.java`, `client/pensieve/SilverGhosts.java`
- Modify:
  - **`ClientModEvents`:**
    - Key mapping `key.hogwartsmagic.pensieve_snap` (default `V`, same conflict context as `PENSIEVE_LEAVE_KEY`, registered next to it). On press while `GhostCamera.active()`, call `GhostCamera.toggleSnap()`.
    - Client tick: `GhostCamera.tick()`.
  - **`PensieveCamera.apply`:** in PLAY, when `GhostCamera.snapped()`, the pose is the owner actor's eye (`MemoryActors.actorEye("owner")` plus the origin) with the owner track's head yaw and pitch at `sceneTime(partial)`, and fov 70.
  - **`MemoryActors.renderAll`:** skip the `owner` actor while snapped.
  - **`PensieveOverlay` (from `PensieveGuiMixin`):** a 6-second hint at PLAY start in a free-camera memory: `hint.hogwartsmagic.pensieve.free` = `"Fly freely — %s: see through %s's eyes — %s: leave"` (key names via `getTranslatedKeyMessage`, owner name from the recording).
  - **Ghost rendering:** a `RenderPlayerEvent.Pre` handler cancels and draws through `SilverGhosts.render` for players in `SilverGhosts`' set. `RenderNameTagEvent` hides their name tags.
  - **`PensieveClientState`:** sets `PensieveGhost.localNoclip = GhostCamera.active()` each tick and clears it on exit. It also clears `SilverGhosts` and the snap on exit and on `onEnter`.
  - `lang/en_us.json`: the key and the hint.

**Interfaces:**
- Consumes: Tasks 6 and 7; `PensieveGhost` (Task 6).
- Produces:
  - `GhostCamera`:
    - `static boolean active()`: PLAY or BUILD, with a non-null `PensieveClientState.recording()`.
    - `static boolean snapped()` and `static void toggleSnap()`. A snap only works in PLAY, and it is released on exit.
    - `static void tick()`, the soft bounds: when the local player is past `radius - 1` from the centre (`origin + (0.5, 0, 0.5)`), remove the outward component of `deltaMovement`. Past `radius + 0.5`, also set the position to `IslandBounds.clamp(..., radius)`.
    - While snapped, the local player's movement input is zeroed (`MovementInputUpdateEvent`), so they stay where they were.
    - Releasing the snap returns the view to the viewer's own floating position.
  - `SilverGhosts`:
    - `static void set(List<UUID>)`.
    - `static boolean isGhost(Player)`.
    - `static void render(AbstractClientPlayer, PoseStack, MultiBufferSource, int light, float partial)`: the player model (`PlayerModel` from the player renderer, `setupAnim` with the player's real limb swing) drawn with `RenderType.entityTranslucent(skin texture)` in colour `(0.80, 0.86, 0.95)`, alpha `0.35 + 0.08 * sin(clock * 0.15)`, full-bright, plus a few `minecraft:end_rod`-tinted silver motes every 10 ticks.
    - Never for the local player.
- [ ] **Step 1: Build check.** `./gradlew compileJava test runServer`.
- [ ] **Step 2: Implement.** Use no enum `switch` on the render path.
- [ ] **Step 3: Visual check:**
  - Fly around the island from Task 7's memory.
  - Fly into the fray at the edge and feel the stop.
  - Pass through a wall (noclip).
  - Press `V` to watch from your own past eyes, follow yourself walking off the island into the void, and press `V` again to come back.
  - With a second dev client (LAN) diving into the same basin, each client sees the other as a pulsing silver ghost with no name tag, and the second client joins at the current moment.
  - Take screenshots.
- [ ] **Step 4: Commit** `Fly through recorded memories as a ghost`.

### Task 9: Documentation and full verification

**Files:** Modify `MOD_FEATURES_WIKI.md` (Empty Memory Phial and Memory under Items; "Capturing a memory" under the Pensieve section: the gesture, what is kept, the keys, the config names) and `wiki/data.js` entries following the existing shape. Also update `.claude/skills/writing-pensieve-memories/SKILL.md` with one paragraph: recorded memories are `recorded/<uuid>` scenes built from `Recording`s, and are not hand-authored.

- [ ] **Step 1: Write the docs.**
- [ ] **Step 2: Run the full suite with the client closed.**
  - `./gradlew test`: all pass.
  - `./gradlew runGameTestServer`: all Pensieve and capture gametests pass.
  - `./gradlew runServer` boots with no `/ERROR`.
  - Then a dedicated-server check of a real capture (the dedicated server harness on the Desktop server with this build): join from the dev client, capture, pour and dive.
- [ ] **Step 3: In the client:**
  - A duel against a Death Eater (`WizardEntity`) with beams both ways, captured and watched.
  - A capture right after a Floo trip, which starts after it.
  - The phial held without a wand, which shows the hint.
  - A capture attempted inside a memory, which is refused.
  - Then rename a Memory at an anvil, give it to a second player, and have them watch it.
  - Take screenshots.
- [ ] **Step 4: Commit** `Document capturing Pensieve memories`.

---

## Deferred (not in this plan)

- **Mod effects drawn on the client from s→c `SpellPackets` and not carried by an entity or a beam:** the Patronus shield dome, the Incarcerous chains, the Protego Maxima sky dome, the Imperio and Possession overlays and similar. Player casts of `protego`, `lumos`, `revelio`, `morsmordre`, `expecto_patronum` and `fiendfyre` are replayed through `SpellVisuals` adapters (Task 4 `onCast`). NPC casts of non-beam spells are not.
- **Riding poses.** A rider and their mount are both replayed where they were, but the rider does not sit.
- **Block-entity changes inside the window** (sign edits, banner changes). Allow-listed block entities show their state at capture time.
- **Client-only particles** that a real client would spawn from an entity's own tick (puppets do not tick), and weather.
- **Garbage collection of `.hpm` files** whose Memory item was destroyed.
- **Whispers and team chat** are replayed only for the owner (they are what the owner received). Other viewers see the owner's chat, which may include the owner's private lines.

## Calls made by the planner

- Recording method: per-level server sampling plus hooks, not packet capture (reasons above).
- The watch margin of 48 blocks, with a memory starting when the owner arrived within it. This keeps the block log's rewind correct without logging the whole loaded world.
- A per-tick step over 8 blocks counts as a teleport (apparition, Floo, `SeamlessMove`), alongside `EntityTeleportEvent`, changing dimension and respawning.
- Minimum memory 2 s (`too_soon` below that). Capture cooldown 1 s. The phial is taken at once and returned on failure.
- Hidden-block culling: a solid block enclosed on all six sides is not rebuilt unless it changes in the window.
- Default caps: 64 entities, 200,000 block changes per level, 4,096 KB per memory. Thinning drops particles first, then the farthest creatures.
- Storage: one deflated file per memory under `<world>/data/hogwartsmagic_memories/`, cached LRU 8, prefetched on pour.
- Transport: binary parts of at most 900 KB on the existing channel. The start packet carries an empty JSON for a recorded memory.
- Ghosts pass through walls (noclip), so a viewer is never stuck inside a remembered house.
- The ghost starts 3 blocks behind and 1.6 above where the owner stood when the memory begins.
- The snap key is `V`. Releasing it returns to where you were floating, and while snapped you do not drift.
- Silver ghosts appear only in recorded memories (in story memories every viewer sits at the same anchor).
- Recipe: Empty Memory Phial = shapeless glass bottle + Arcane Dust, giving 1. The Memory is named `"<owner>'s Memory — Day N"`, with day = overworld day + 1.
- Typed `{placeholders}` in recorded chat are escaped, so they are shown as typed.
- The capture sound is ElevenLabs with the pipeline above, or a vanilla alias when the key is missing.

## Needs the user's decision (non-blocking; the plan builds as written)

1. **Privacy on shared servers.** The recorder is always on, and a memory replays other players' movements and the public chat the owner saw. It can be given away and watched by anyone. Is a server toggle enough (`pensieveCaptureEnabled`), or should players be able to opt out of appearing in others' memories (they would show as a faceless grey figure)?
2. **The deferred mod effects** (Patronus shield dome, Incarcerous chains, Protego Maxima and similar s→c effect packets). Is a duel replay of beams, casts, hits, Protego, Patronus streams and sounds convincing enough for this round, or should a follow-up plan record the mod's own effect packets?
