# Sorting Memory Stage 2: Privet Drive and the Letters, Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the opening of the Sorting memory with ~37 s of Privet Drive. It starts in first person: the eyes open in the cupboard under the stairs. Then the first letter arrives and Uncle Vernon snatches it. More letters come, and Vernon nails the slot shut. Owls line the rooftops, and letters pour down the chimney on Sunday. The memory then hands over to the existing stub hat scene.

**Architecture:**
- **Two engine pieces**, each general enough for other memories:
  - a `memory_letter` entity, a flat fluttering envelope that a memory can path like any actor;
  - a `letter_flood` effect, a deterministic client-side swarm of those envelopes that bursts from a point, whirls inside a room box and settles on the floor.
- **Shared set:** one Python module builds 4 Privet Drive and its street.
- **Two chained scenes** use that set: `sorting` (the morning) chains to `sorting_privet_sunday`, which chains to `sorting_hat` (the Stage 1 stub, renamed).

**Tech Stack:** Forge 1.21 (51.0.33), Java 21, JUnit 5, Forge gametests, Python 3 scene generators, Fish Audio TTS, ffmpeg.

**Spec:** `docs/superpowers/specs/2026-10-17-sorting-memory-design.md` (beat 1, engine work 7, build order step 2). The Stage 1 plan is `docs/superpowers/plans/2026-10-17-sorting-memory-stage1.md`.

## Global Constraints

- **Server/client safety:** nothing in `pensieve/`, `sorting/`, `network/` or `entity/` imports `net.minecraft.client.*` or `com.hogwartsmagic.client.*`.
- **Client code:** no `switch` on an enum in client/render code (use if-chains). Avoid entity event bytes 21, 35 and 63.
- **Gradle:** never run gradle while a Minecraft dev client runs (`tasklist | findstr java`; a GradleDaemon is fine).
- **Scenes:**
  - Scene JSON is at most 262,144 characters (`PensieveStartPacket.MAX_JSON`). The set radius is at most 200.
  - Follow `.claude/skills/writing-pensieve-memories/SKILL.md`: shot list first, a Python generator, `physics_problems` clean, `SceneDataTest` green.
  - Keep that skill file accurate for every key, entity and effect added.
- **Spoken lines:**
  - No spoken line contains a name.
  - The player is a boy, addressed as "boy" or "you". The name appears only through the `{viewer}` text placeholder.
  - Dialogue is original; short famous nods are fine ("No post on Sundays!"), whole film exchanges are not.
- **Sizes:** the player actor (`"skin": {"viewer": true}`) and Dudley are at `scale` 0.75. Adults are at 1.0. The house is built at normal block scale.
- **First person is used only for the eyes opening.** Every other shot is filmed from outside.
- **Git:**
  - Never `git stash`, `git reset` or `git checkout -- <file>`.
  - Never stage `.claude/settings.local.json`, `.claude/skills/rendering-minecraft-structures/textured/viewer.html` or `src/main/resources/data/hogwartsmagic/memories/lake_patronus.json`. Use `git add <explicit paths>`.
  - End every commit with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Write messages containing apostrophes to a file and use `git commit -F`.
  - Never push.
- **Assets:**
  - Downloads (skins) are made by the controller only after the user approves each file.
  - The Fish Audio key is at `C:\Users\alonl\fish_key.txt`. Never print or commit it.

## Review Focus

1. **Leaving mid-flood** (X, yank, hat off, death): every client-only letter entity the flood spawned must vanish with the memory. None may stay in the real world. Task 2 tests `EffectVisuals.clear` discarding them (`floodClearRemovesLetters` gametest-free check via a unit-testable registry list).
2. **Letters through walls:** a flood letter must never leave its room box or fly behind the fireplace wall. Task 2 has `staysInsideRoomBox` and `neverBehindEmitter`.
3. **Joining a flood late** (a hand-over that lands mid-effect, or a late tick): the swarm must be in the same state as if watched from the start, not restarted. Task 2 has `fastForwardMatchesStepping`.
4. **The chain must end and name real scenes** after the rename (`sorting` → `sorting_privet_sunday` → `sorting_hat`). The Stage 1 `SceneDataTest` chain check covers it, and Task 4 runs it.
5. **Camera inside the tiny cupboard:** the first-person wake-up camera must not clip into the stair blocks. A child-scale lying actor must fit in the 1 × 2 cupboard. Task 3 exports the cupboard cell coordinates, and Task 4 has a generator assert that the camera key sits in an air cell.

---

## File map

| File | Task | Responsibility |
|---|---|---|
| `src/main/java/com/hogwartsmagic/entity/MemoryLetterEntity.java` (new) | 1 | A no-AI, no-gravity, no-collision envelope entity, used as a memory actor and by the flood |
| `src/main/java/com/hogwartsmagic/entity/ModEntities.java` | 1 | Register `memory_letter` (0.4 × 0.05) |
| `src/main/java/com/hogwartsmagic/pensieve/scene/LetterFlutter.java` (new) | 1 | Pure flutter maths (roll and wobble from age and seed) |
| `src/main/java/com/hogwartsmagic/client/MemoryLetterRenderer.java` (new) | 1 | Draws `textures/item/hogwarts_invite.png` on a double-sided 0.45 × 0.3 quad, oriented by yaw/pitch plus flutter |
| `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | 1 | Register the renderer |
| `src/main/java/com/hogwartsmagic/pensieve/scene/LetterFlood.java` (new) | 2 | Pure, deterministic swarm simulation |
| `SceneDef.EventDef`, `SceneParser`, `SceneValidator`, `SceneNames` | 2 | New event key `yaw`, new effect `letter_flood` |
| `src/main/java/com/hogwartsmagic/client/pensieve/EffectVisuals.java`, `MemoryEvents.java` | 2 | Run the flood with client-only `MemoryLetterEntity`s, and clear them |
| `build_scripts/pensieve_scenes/privet_drive.py` (new) | 3 | Builds the set: the street and No. 4 inside and out; exports named anchor points |
| `build_scripts/pensieve_scenes/sorting.py` | 4 | Generates `sorting.json` (morning), `sorting_privet_sunday.json` and `sorting_hat.json` (the old stub) |
| `src/main/resources/data/hogwartsmagic/memories/*.json` | 4, 5 | The scenes |
| `src/main/resources/assets/hogwartsmagic/sounds/pensieve/voice/sorting_*.ogg`, `sounds.json`, `ModSounds.java` | 5 | Vernon, Petunia and hat voice lines |
| `src/main/resources/assets/hogwartsmagic/textures/memory_skin/{vernon,petunia,dudley}.png`, `_skins.json` | 0 | Approved skins |

---

### Task 0 (controller, with the user): assets

- [ ] **Step 1:** Show the user the skin candidates for Vernon, Petunia and Dudley (from the research), naming each file, its source and its size. Download only the approved ones into a fresh empty scratch directory, then check each is a 64 × 64 PNG with Python `-I`.
- [ ] **Step 2:** Copy them to `textures/memory_skin/{vernon,petunia,dudley}.png`. Add the names to `_skins.json`.
- [ ] **Step 3:** Show the user the Fish Audio voice candidates for Vernon and Petunia, and the line list (Task 5). Record the picks in the ledger.
- [ ] **Step 4:** Run `./gradlew test --tests "*SceneDataTest*"` and expect PASS. Commit with message `Add the Dursley memory skins`.

Tasks 1–3 do not wait for Task 0. Task 4 uses the skin names `vernon`, `petunia` and `dudley`; if Task 0 is not finished, it uses `hooded_figure` as a stand-in and leaves a `# TODO skin` comment, which Task 5 resolves.

### Task 1: The `memory_letter` entity (Sonnet)

**Files:** create `entity/MemoryLetterEntity.java`, `pensieve/scene/LetterFlutter.java`, `client/MemoryLetterRenderer.java`; modify `entity/ModEntities.java`, `event/ClientModEvents.java`, `SKILL.md`. Test: `src/test/java/com/hogwartsmagic/pensieve/LetterFlutterTest.java`.

**Interfaces:**
- Produces:
  - `ModEntities.MEMORY_LETTER` (`EntityType<MemoryLetterEntity>`, id `memory_letter`, `MobCategory.MISC`, sized 0.4 × 0.05, `clientTrackingRange(4)`).
  - `MemoryLetterEntity(EntityType<? extends MemoryLetterEntity>, Level)`.
  - The public field `int flutterSeed`. It defaults to the entity id, and the flood sets it.
  - `LetterFlutter.roll(float age, int seed) -> float` (degrees) and `LetterFlutter.wobble(float age, int seed) -> float` (degrees, pitch added).
  - `LetterFlutter.resting(float speed) -> boolean`: true when the speed is under 0.01 blocks per tick, meaning no flutter.
- [ ] **Step 1: Write `LetterFlutterTest`.**
  - `rollIsBounded`: for ages 0..400 step 0.5 and seeds 0..50, `abs(roll) <= 35` and `abs(wobble) <= 20`.
  - `seedsDiffer`: `roll(10, 1) != roll(10, 2)`.
  - `isSmooth`: for every age, `abs(roll(a + 0.5f, s) - roll(a, s)) < 6`.
- [ ] **Step 2:** Run `./gradlew test --tests "*LetterFlutterTest*"` and expect FAIL (the class is missing).
- [ ] **Step 3: Implement.**
  - `LetterFlutter` sums two sines with seed-derived phase and frequency between 0.15 and 0.35 rad/tick.
  - `MemoryLetterEntity` mirrors how `FiendfyreSerpentEntity` is a bare `Entity`: `noPhysics = true`, `setNoGravity(true)`, no save data needed (`readAdditionalSaveData`/`addAdditionalSaveData` empty, `defineSynchedData` empty), `isPickable` false, `isPushable` false.
  - **The renderer:**
    - It translates up 0.025, then rotates Y by `-yRot` and X by `xRot + wobble`.
    - It applies Z roll (`roll`, zero when the entity moved under 0.01 blocks since last tick) and draws the item texture on both faces.
    - It uses `RenderType.entityCutoutNoCull(texture)` with the light from the packed light argument.
    - The texture is the 1024² `hogwarts_invite.png`. The envelope sits in the UV rect (0.19, 0.265)–(0.81, 0.672), measured from the PNG's opaque bounds, so the quad shows only the envelope.
  - Register the renderer in `ClientModEvents` next to `FIENDFYRE_SERPENT`.
  - **The skill doc:** add `hogwartsmagic:memory_letter` under actors. Note that it is a flat envelope you path like any actor, that `look` pitch 90 lays it flat, and that it flutters while moving and lies still when its path holds.
- [ ] **Step 4:** Run `./gradlew test --tests "*LetterFlutterTest*" --tests "*SceneDataTest*"` and expect PASS. Run `./gradlew runServer` until "Done" with no errors from `hogwartsmagic` (proves the registration is server-safe), then stop it.
- [ ] **Step 5:** Commit the touched files. Message: `Add the memory_letter envelope actor`.

### Task 2: The `letter_flood` effect (Sonnet)

**Files:**
- Create `pensieve/scene/LetterFlood.java`.
- Modify `SceneDef.java` (`EventDef` gains `float yaw` as its last component; `Float.NaN` when absent), `SceneParser.java` (accept `"yaw"` on events), `SceneValidator.java`, `SceneNames.java` (`EFFECTS` adds `letter_flood`), `client/pensieve/EffectVisuals.java`, `client/pensieve/MemoryEvents.java` (pass `e.count()`, `e.spread()` and `e.yaw()` through) and `SKILL.md`.
- Test `src/test/java/com/hogwartsmagic/pensieve/LetterFloodTest.java`, and extend `SceneParserTest` and `SceneValidatorTest`.

**Interfaces:**
- Consumes: `ModEntities.MEMORY_LETTER`, `MemoryLetterEntity.flutterSeed` (Task 1).
- Produces:
  - `new LetterFlood(double ax, double ay, double az, float yawDeg, float hx, float hy, float hz, int count, int duration, long seed)`.
  - `void step()`, which advances one tick.
  - `void fastForward(int ticks)`.
  - `int age()`, `int count()`.
  - `double x(int i)`, `y(int i)`, `z(int i)`, `float yaw(int i)`, `float pitch(int i)`.
  - `boolean emitted(int i)`, `boolean resting(int i)`.
  - `EffectVisuals.play(String effect, Vec3 at, int duration, int elapsed, int count, float[] spread, float yaw)`. The existing 2- and 4-arg overloads delegate with `count` 0, `spread` null and `yaw` NaN.
- **Scene format (copy into `SKILL.md`):** `letter_flood`.
  - It needs `at` (the fireplace mouth, with y at the floor top, the block above the floor layer) and `yaw` (the direction into the room, Minecraft convention; required).
  - Optional keys:
    - `count`: letters, 1–400, default 160.
    - `spread`: the room half-size `[hx, hy, hz]`, each 0.5–12, default `[3, 2.5, 3]`.
    - `duration`: emission time in ticks, default 120.
  - **The room box** is the axis-aligned box centred on `at + forward × hz`, with half-sizes `hx, hy, hz` in world X/Y/Z rotated by yaw to the nearest 90°. The floor is `at.y`.
  - **Behaviour:**
    - Letters leave `at` evenly over the first 60% of `duration`.
    - They launch within ±60° of `yaw` and 10–45° upward at 0.35–0.6 blocks per tick.
    - They fly with gravity 0.015 per tick², drag 0.97 and a swirl round the box's vertical centre axis (tangential 0.04 blocks per tick).
    - They bounce off the box walls with 0.4 damping.
    - They come to rest on the floor (y = `at.y`) once their speed is under 0.03 at floor contact, then lie flat at a random yaw until the scene ends.
    - Sound when it starts (not for a late joiner): `minecraft:block.fire.extinguish` and `minecraft:entity.breeze.wind_burst` at `at`, then `minecraft:item.book.page_turn` every 4 ticks at random volume 0.3–0.6 while letters fly.
- **Validator:**
  - `letter_flood` without `at` gives `"... letter_flood needs at"`.
  - Without `yaw` gives `"... letter_flood needs yaw"`.
  - A `count` outside 1–400 is an error, and so is a spread component outside 0.5–12.
  - `yaw` on any event kind other than `effect` is an error: `"yaw is only for effects"`.
- [ ] **Step 1: Write `LetterFloodTest`.** Use count 200, duration 120, spread [3, 2.5, 3], and yaws 0, 90, 180 and -90.
  - `staysInsideRoomBox`: step 400 ticks, and every emitted letter at every tick is inside the box with a 0.05 tolerance.
  - `neverBehindEmitter`: the dot product of (pos − at) and the forward vector is ≥ −0.05 at every tick.
  - `allEmittedBy60Percent`: after 72 ticks every letter is `emitted`.
  - `allRestOnTheFloor`: after 400 ticks every letter is `resting`, with `abs(y − at.y) < 0.06`.
  - `fastForwardMatchesStepping`: `fastForward(150)` equals 150 × `step()` exactly, per letter x/y/z.
  - `deterministic`: the same seed gives the same positions, and seed + 1 does not.
  - In `SceneParserTest`, `parsesEffectYaw`. In `SceneValidatorTest`, `letterFloodNeedsYaw`, `letterFloodNeedsAt`, `letterFloodCountRange` and `yawOnlyOnEffects`.
- [ ] **Step 2:** Run `./gradlew test --tests "*LetterFlood*" --tests "*SceneParserTest*" --tests "*SceneValidatorTest*"` and expect FAIL.
- [ ] **Step 3: Implement `LetterFlood`.** Use plain doubles and arrays only, with no Minecraft imports. Draw random numbers from a `java.util.SplittableRandom(seed)`, all of them taken up front in the constructor, so stepping is pure.
  - **`EffectVisuals`:**
    - A `Flood implements Running` creates one client-only `MemoryLetterEntity` per letter when that letter is emitted, in the `darkMark` pattern (`setId(MemoryEvents.nextEntityId())`, `setOldPosAndRot()`, `level.addEntity`). It sets `flutterSeed` to the letter index plus the seed.
    - Each tick it moves the entities to the simulation positions, using `setPos` with `xo/yo/zo` kept for interpolation.
    - It keeps them in a `LETTERS` list that `clear()` discards. The Running ends only when the scene clears: the letters stay on the floor.
    - A late join calls `fastForward(elapsed)` before spawning the already-emitted letters.
    - The seed is `Float.floatToIntBits(at)` mixed with the event's `t` (pass it in from `MemoryEvents`), so two floods in one scene differ.
  - Extract the entity bookkeeping as package-private `static int liveFloodLetters()` for the test below.
- [ ] **Step 4:** Add to `PensieveGameTests` (or the client-free part): none. `liveFloodLetters()` is client-only, so it is checked in game in Task 6, item 6. Run the Step 2 command and expect PASS, then the full `./gradlew test` and expect PASS.
- [ ] **Step 5:** Commit. Message: `Add the letter_flood memory effect`.

### Task 3: The Privet Drive set (Opus, design-heavy)

**Files:** create `build_scripts/pensieve_scenes/privet_drive.py`. Create the preview renders under `build_scripts/pensieve_scenes/previews/privet_*.png`, kept local and not committed.

**Interfaces:**
- Produces:
  - `build(world: dict) -> dict`, which writes cells `(x, y, z) -> block id` into `world` and returns anchors.
  - `ANCHORS` keys, each an `[x, y, z]` float list relative to the origin, with the floor top at y 0:
    - `cupboard_bed` (the feet of the lying boy), `cupboard_eye` (the first-person camera spot), `cupboard_door` (block).
    - `hall_mat` (where letters land inside the front door), `letter_slot` (the slot's outer face, at y ≈ 0.9), `front_door` (block).
    - `kitchen_table`, `vernon_chair`, `living_fireplace_mouth`, `living_fireplace_yaw` (float), `living_room_box` (`[hx, hy, hz]`), `armchair`.
    - `street_wide_cam`, `roof_owl_spots` (a list of 15 or more positions on ridges, chimneys, fences and the lamp post).
    - `stairs_top`.
  - `fills(world) -> list`: merges the cells into the fewest box fills (greedy along x, then z, then y, per block id), for `set.fills`.
  - `RADIUS` (int).
- **Design brief (from the film; refine with the research notes in the ledger):**
  - **The street:**
    - Identical 2-storey suburban houses in a row, in a cul-de-sac curve if it fits.
    - Reddish-brown brick (`minecraft:bricks`, with `granite`/`terracotta` accents), a tile roof (`minecraft:dark_oak_stairs` or `deepslate_tile_stairs`) and white window frames (`white_concrete`/`quartz`).
    - Each has a bay window downstairs at the front, a white garage door, a small porch with a front door, low hedges, front lawns and a pavement with a grey road.
    - There are lamp posts and a street sign. No. 4 is in the middle and fully detailed. The neighbours are exterior shells, possibly mirrored.
  - **The interior of No. 4 (ground floor needed; upstairs may be a shell):**
    - From the front door, a hall runs straight back. The staircase rises along one side wall.
    - Under the stairs is the cupboard: a door in the stair side, inside 1 wide × 2 long × 1.5–2 high with the sloped stair underside overhead. It holds a cot (a `white_wool` or bed block, low), a shelf and a bare-bulb light.
    - The living room is to one side, with a fireplace (a brick surround and mantel) on its outer wall, an armchair and a sofa, and pale walls with wallpaper tone.
    - The kitchen and dining area is at the back, with a table for 3 and counters.
    - The front door has a letter slot at about y 0.9. Since a slot has no block, use the door at `front_door` and record `letter_slot` as the point on the outer face; letters are pathed through it.
  - **Physics:**
    - The set floats over the void, so it needs a solid layer under everything, no unsupported gravity blocks and no water.
    - `physics_problems(world, RADIUS)` must return `[]`.
  - **Lighting:** morning. Keep window glass panes so the camera inside reads the daylight, and use `minecraft:light` blocks where interior shots would be dark.
- [ ] **Step 1: Write the build.** Add a `__main__` that builds the set, asserts `physics_problems == []`, and prints the fill count and the JSON size of `{"fills": fills(world)}`. The size must be under 150,000 characters, leaving room for actors and the camera in each scene.
- [ ] **Step 2: Check it visually.**
  - Render previews with the `rendering-minecraft-structures` skill: the street from the `street_wide_cam` angle, a top-down cut of the ground floor, and the cupboard section.
  - Look at them and fix anything that does not read as the film's house.
  - Send the street render and the ground-floor cut to the user (SendUserFile) for a look; the controller relays any feedback.
- [ ] **Step 3:** Run `python -I build_scripts/pensieve_scenes/privet_drive.py` and expect it to print the counts with no assertion error.
- [ ] **Step 4:** Commit `privet_drive.py`. Message: `Build 4 Privet Drive for the Sorting memory`.

### Task 4: The Privet Drive scenes (Opus, cinematic)

**Files:**
- Modify `build_scripts/pensieve_scenes/sorting.py` so it writes three scenes.
- Write `memories/sorting.json` (rewritten), `memories/sorting_privet_sunday.json` (new) and `memories/sorting_hat.json` (new; the Stage 1 stub's content, unchanged except its id).

**Interfaces:**
- Consumes:
  - `privet_drive.build`, `ANCHORS`, `fills`, `RADIUS` (Task 3).
  - Actor type `hogwartsmagic:memory_letter` (Task 1).
  - Effect `letter_flood` with `yaw` (Task 2).
  - Existing entity `hogwartsmagic:owl`, and skins `vernon`/`petunia`/`dudley` (Task 0).
- Produces:
  - Scene ids `sorting` → `sorting_privet_sunday` → `sorting_hat`.
  - `SortingHatManager.MEMORY_SCENE` stays `"sorting"`.
  - Voice event ids `hogwartsmagic:pensieve.voice.sorting_<line>` from the line table in Task 5. Until Task 5 they are `words` only; the generator has a `VOICES = False` switch that Task 5 turns on.
- **Beat outline:** about 37 s in all. The implementer writes the full shot list in their report first, per the skill, and keeps these beats and times within ±1.5 s.

  | Scene | t | Beat |
  |---|---|---|
  | `sorting` (20 s, morning, `time_of_day` 1000) | 0–3.5 | `eyes_open` (3 s) at t 0. First person: the camera at `cupboard_eye`, looking up at the stair underside. Thuds of feet on the stairs above (`minecraft:block.wood.step`), dust falling (`minecraft:white_ash` particles). The hat's opening murmur voices over black. |
  | | 3.5–7 | A knock. The cupboard door opens (a block event to the open door state). Petunia's voiced wake-up. The camera pulls back out through the doorway, revealing the boy lying on the cot in the tiny cupboard. |
  | | 7–10 | Exterior establishing shot: a wide, slow drift down the street to No. 4 in morning light (fov 75). |
  | | 10–13.5 | Hall: one letter drops through the slot onto the mat. The boy walks over, stoops (`sneak`) and picks it up (`hold hogwartsmagic:hogwarts_invite`). Close-up insert with narration: `Mr. {viewer}` / `The Cupboard under the Stairs` / `4 Privet Drive, Little Whinging, Surrey`. |
  | | 13.5–16.5 | Kitchen: Vernon snatches it (a cut on the grab), with a voiced line. Dudley gawps. Petunia goes pale. |
  | | 16.5–20 | A montage of days: letters through the slot, 3, then 8, then 20 (memory_letter actors pathed through `letter_slot`). Vernon hammers planks over the slot (`hold minecraft:oak_planks`, swings, `minecraft:block.wood.hit` and `minecraft:entity.zombie.attack_wooden_door`). His voiced "no more post" line. The scene fades. |
  | `sorting_privet_sunday` (17 s, `time_of_day` 1500) | 0–4 | Wide exterior: owls (15+ `hogwartsmagic:owl` at `roof_owl_spots`) on every ridge, chimney and fence, heads turning toward No. 4. |
  | | 4–7 | The living room: Vernon smug in the armchair with a voiced "No post on Sundays!" Petunia and Dudley are there, and the boy sits by the wall. |
  | | 7–13 | A rumble in the chimney (sounds, `minecraft:large_smoke` at the fireplace). Then `letter_flood` at `living_fireplace_mouth` with `living_fireplace_yaw`, `living_room_box`, count 220 and 110 ticks. Low wide shot (fov 85), then a cut to the boy jumping and grabbing (`arms_out`, a jump path), Vernon shouting and the family cowering (`cower`). |
  | | 13–17 | A slow push-in on the boy closing his hand on one letter in the whirl. Fade (chain). |
  | `sorting_hat` | — | The Stage 1 stub as it is, until Stage 7 replaces it. |
- **Rules:**
  - The viewer actor is `{"viewer": true}`, scale 0.75. Dudley is scale 0.75. Every adult is 1.0.
  - Only the first beat is first person.
  - Children's eyes are 0.75 × 1.62 above their feet. Camera framings for close-ups use that.
- [ ] **Step 1:** Write the shot list in the report.
- [ ] **Step 2:** In the generator, assert that the `cupboard_eye` camera position's cell and the cell above it are air (Review Focus 5), that each scene's JSON is under 262,144 characters, and that every `next` names a scene this generator writes.
- [ ] **Step 3:** Run `python -I build_scripts/pensieve_scenes/sorting.py`, then `./gradlew test --tests "*SceneDataTest*" --tests "*Sorting*"`, and expect PASS. Update any Stage 1 test that names the stub's contents to follow `sorting_hat`.
- [ ] **Step 4:** Run `./gradlew runGameTestServer` and expect all gametests to pass.
- [ ] **Step 5:** Commit the generator and the three scenes. Message: `Open the Sorting memory at 4 Privet Drive`.

### Task 5: The voices (Sonnet, after Task 0 picks)

**Files:**
- `assets/hogwartsmagic/sounds/pensieve/voice/sorting_*.ogg`
- `sounds.json`, `ModSounds.java` (as `pensieve.voice.*`, like `lake_patronus`)
- `build_scripts/pensieve_scenes/sorting.py` (set `VOICES = True` and fix the timings to the real clip lengths)
- the regenerated scenes

**Line table (shown to the user in Task 0; no names):**

| id | Speaker | Line |
|---|---|---|
| `sorting_hat_open` | Hat (voice A, `c944589a55ad450e8109d39cd3ecc488`) | "Hmm... let us look back. Back to where it all began." |
| `sorting_petunia_up` | Petunia | "Up! Get up! Now!" |
| `sorting_vernon_mine` | Vernon | "Who'd be writing to you? Give that here, boy!" |
| `sorting_vernon_nomail` | Vernon | "No more post! Not one more letter comes through that door!" |
| `sorting_vernon_sunday` | Vernon | "Fine day, Sunday. No post on Sundays!" |
| `sorting_vernon_shout` | Vernon | "Get away from them! Out! Out!" |

- [ ] **Step 1:** Generate each line with Fish Audio (`POST https://api.fish.audio/v1/tts`, `model: speech-1.6`, the picked `reference_id`). Use one expressive take per line, plus a retake if the first is flat. Convert with the Stage 1 pipeline: ffmpeg `silenceremove`, then `loudnorm I=-16:TP=-1.5`, mono 44.1 kHz vorbis.
- [ ] **Step 2:** Register the sounds. Regenerate the scenes with `VOICES = True`, so each `words` line by that actor is paired with a `voice` event at the same `t`, and later lines are shifted if a clip overruns its slot.
- [ ] **Step 3:** Run `./gradlew test --tests "*SceneDataTest*"` and `./gradlew runServer` (to Done), and expect PASS with no errors.
- [ ] **Step 4:** Commit. Message: `Voice the Privet Drive scenes`.

### Task 6 (controller): in-game check and final review

- [ ] **Step 1:** Run the Opus final review of the Stage 2 range (all tasks) against this plan and the spec.
- [ ] **Step 2:** Make sure no Minecraft client is running, build, then launch `./gradlew runClient` in the background. In game, `/pensieve preview sorting` plays the chain. Check, with screenshots:
  1. it opens black, the eyes open, and the cupboard view does not clip;
  2. the pull-out reveal;
  3. the exterior street;
  4. the letter on the mat and the address card showing the player's name;
  5. Vernon's snatch and the slot being boarded;
  6. the owls, then the chimney flood filling the room. After X mid-flood, no letters are left in the real world;
  7. the hand-over to `sorting_hat`.
- [ ] **Step 3:** Send the screenshots to the user. Fix rounds go through a Sonnet implementer and an Opus re-review. Ask the user to push `sorting-memory`.
