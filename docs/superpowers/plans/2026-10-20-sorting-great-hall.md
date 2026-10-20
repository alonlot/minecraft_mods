# Sorting Memory: The Great Hall, Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When an unsorted player puts on the Sorting Hat, the memory is about 75 s, all in the Great Hall:
- It opens in darkness, then the eyes open in a torch-lit entrance hall among the first-years.
- The great doors swing open dramatically onto the hall: the house tables full of students, the staff table, hundreds of floating candles and the enchanted night-sky ceiling.
- Hermione and Ron are called and sorted to roaring cheers. The hall falls silent, and the player's name appears large on screen.
- The player walks up and sits, and the hat comes down over their eyes in first person. Then black, and the voiced quiz from Stage 1.

**Architecture:**
- **The set:** the user-supplied Great Hall world is converted into a structure template `great_hall.nbt`. A new entrance hall and a widened doorway are added in the converter.
- **Engine pieces:** four small general ones, plus one larger one.
  - The small ones are the `equip` action, text `size` on words, an `sfx` event kind and a hinged `memory_door` entity.
  - The larger one is an `enchanted_ceiling` effect.
- **Sound:** ElevenLabs makes every sound effect. Fish Audio makes the voices with the user's curated character voices. The user supplies the film music.
- **Scene:** the Opus-written generator `great_hall_scene.py` writes `memories/sorting.json`, which replaces the Stage 1 stub.

**Tech Stack:** Forge 1.21 (51.0.33), Java 21, JUnit 5, Forge gametests, Python 3 generators, ElevenLabs sound-generation API, Fish Audio TTS, ffmpeg.

**Spec:** `docs/superpowers/specs/2026-10-17-sorting-memory-design.md`, beat 7 and engine work 9.

**User decisions on 2026-10-20 (override the spec's order):**
- Build only the Great Hall now; Privet Drive (Stage 2) is parked.
- The memory starts when the hat goes on, and the doors open dramatically.
- ElevenLabs sound effects; the user supplies the film music; about 75 s; fully cinematic.

## Global Constraints

- **Server/client safety:** nothing in `pensieve/`, `sorting/`, `network/` or `entity/` imports `net.minecraft.client.*` or `com.hogwartsmagic.client.*`.
- **Client code:** no `switch` on an enum in client/render code. Avoid entity event bytes 21, 35 and 63.
- **Rendering:** world-space rendering in `RenderLevelStageEvent` starts from an identity `PoseStack` with the camera rotation applied once. Never use `event.getPoseStack()` (memory note `reference_renderlevelstage_matrix`).
- **Gradle:** never run gradle while a Minecraft dev client runs (`tasklist | findstr java`; a GradleDaemon is fine).
- **Scenes:**
  - Scene JSON is at most 262,144 characters. The radius is at most 200.
  - Follow `.claude/skills/writing-pensieve-memories/SKILL.md`: shot list first, a generator, `physics_problems` clean, `SceneDataTest` green.
  - Keep that skill file accurate for every key, entity and effect added.
- **Spoken lines:**
  - Never use the player's name; the player is a boy ("you"). The name appears only via `{viewer}`.
  - McGonagall may call "Granger, Hermione" and "Weasley, Ronald" (those are other characters, not the player).
  - Dialogue is original apart from short nods.
- **Sizes:** first-years (the player via `{"viewer": true}`, Ron, Hermione and the others) are at `scale` 0.75. Older students are at 0.9. Adults are at 1.0, and Hagrid is at 1.5.
- **First person is used only for the eyes opening and the hat coming down.**
- **The hall build:** its licence is unknown (no author found). It stays on the `sorting-memory` branch for the user's own use. The ledger notes that permission is needed before any public release.
- **Git:**
  - Never `git stash`, `git reset` or `git checkout -- <file>`.
  - Never stage `.claude/settings.local.json`, `.claude/skills/rendering-minecraft-structures/textured/viewer.html` or `src/main/resources/data/hogwartsmagic/memories/lake_patronus.json`. Use `git add <explicit paths>`.
  - End every commit with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Write messages containing apostrophes to a file and use `git commit -F`.
  - Never push.
- **Keys:** the ElevenLabs key is in `C:\Users\alonl\eleven_key.txt` and the Fish Audio key in `C:\Users\alonl\fish_key.txt`. Never print or commit either; scripts read the file.

## Review Focus

1. **Leaving the memory mid-scene** (X, hat off, yank, death): the ceiling plane, the door entities, equipped hats and every `sfx` sound must stop or vanish with it. Nothing may stay in the real world or keep playing. Task 1 has `sfxStopsOnClear`, Task 3 has `ceilingClearedWithScene`, and the in-game check repeats it.
2. **Late joining and hand-over:** the `sfx` and `equip` state must be right for a viewer whose scene clock is already past the cue. An equip at t 10 must show the hat at t 12 without replaying. Task 1 has `equipStateIsAFunctionOfTime`.
3. **Size cap:** about 100 actors with paths must stay under 262,144 characters. Task 6's generator asserts the size.
4. **Door leaves:** a door leaf rotates about its hinge edge, not its centre, at any `scale`. Task 2 has `hingeStaysPut`.
5. **Ceiling over a hall that is not axis-centred, and late joiners:** the plane covers exactly `at ± spread` and draws above the candles (Task 3 tests). A late joiner sees the ceiling already revealed (`revealAt(elapsed)`).

---

## File map

| File | Task | Responsibility |
|---|---|---|
| `SceneNames`, `SceneParser`, `SceneValidator`, `SceneDef`, `client/pensieve/MemoryActors.java`, `MemoryEvents.java`, `MemoryAudio.java`, `MemoryWords.java` | 1 | `equip` action, `words.size`, `sfx` event kind |
| `entity/MemoryDoorEntity.java`, `pensieve/scene/DoorHinge.java`, `client/MemoryDoorRenderer.java`, `textures/entity/memory_door.png` (generated by `build_scripts/generate_memory_door_texture.py`) | 2 | Hinged door leaf actor |
| `pensieve/scene/CeilingSky.java`, `client/pensieve/EnchantedCeiling.java`, `EffectVisuals.java` | 3 | `enchanted_ceiling` effect |
| `build_scripts/pensieve_scenes/great_hall_set.py`, `data/hogwartsmagic/structure/great_hall.nbt` | 4 | The converted hall, plus the entrance hall and the doorway |
| `sounds/pensieve/sfx/*.ogg`, `sounds/pensieve/voice/hall_*.ogg`, `sounds.json`, `ModSounds.java`, `build_scripts/sounds/great_hall_sfx.py` | 5 | ElevenLabs sound effects and Fish voices |
| `textures/memory_skin/*.png`, `_skins.json`, `build_scripts/generate_memory_skins.py` | 0 | Character and house-student skins |
| `build_scripts/pensieve_scenes/great_hall_scene.py`, `memories/sorting.json` | 6 | The scene |

---

### Task 0 (controller, with the user): skins and music

- [ ] **Step 1: Downloaded skins.** Show the user the NovaSkin candidates (contact sheet) for McGonagall, Dumbledore, Hagrid, Snape, Quirrell and Ron. Download only the approved ones (name the file, source and size) into an empty scratch folder. Check each is a 64 × 64 PNG. Copy it to `textures/memory_skin/<name>.png` and credit the post URL in `generate_memory_skins.py`'s comment list.
- [ ] **Step 2: Generated skins.** In `generate_memory_skins.py`, add `student_hufflepuff` (trim (236, 185, 30)) and `student_ravenclaw` (trim (30, 60, 150)), copying the `student_gryffindor` call with a different seed. Also add 2 hair variants per house, `student_<house>_b` (seed + 10, a different hair colour), so the tables do not look cloned; add `_b` for gryffindor and slytherin too. Also add `first_year` (a plain black robe with no house trim). Run it and add the names to `_skins.json`.
- [ ] **Step 3: Music.** Ask the user for the music files. Suggested cues: the entrance into the Great Hall, and a quieter Sorting or "Hedwig's Theme" bed. Save them as `sounds/pensieve/music/hall_*.ogg` (mono 44.1 kHz, `"stream": true`).
- [ ] **Step 4:** Run `./gradlew test --tests "*SceneDataTest*"` and expect PASS. Commit with message `Add the Great Hall memory skins` (and the music, if it has arrived).

### Task 1: `equip`, words `size`, `sfx` (Sonnet)

**Interfaces:**
- **Produces, part 1: the `equip` action.**
  - `{"t", "do": "equip", "slot": "head"|"mainhand"|"offhand"|"chest"|"legs"|"feet", "item": "<id>" | "minecraft:air"}`.
  - `slot` and `item` are required. `ActionDef` gains `String slot`.
  - The validator rejects other slots with `"equip slot must be one of ..."`.
- **Part 2: words `size`.** It is a float, default 1, range 1–4.
  - It scales narration text about its centre, using `PoseStack` scale on the narration draw.
  - It is allowed only with `narration: true`; otherwise the validator says `"size is only for narration"`. `WordDef` gains `float size`.
- **Part 3: the `sfx` event kind.** It takes `t`, `id` and `volume` (0–4).
  - It plays non-positionally on `SoundSource.AMBIENT` at full pitch, with no echo and no ducking.
  - It joins `MemoryAudio`'s stop-on-end set, so it stops at once when the memory ends for any reason. Like `voice`, it is skipped if `t` is more than 5 ticks past for a late joiner.
- **Equip state:** `MemoryActors` computes the equipped item per slot at the scene time `t` as the last `equip` or `hold` at or before `t` (the base is `items`). This is the same rule `hold` uses, so a late joiner is correct. If `hold` is not already time-derived, make both derived. The rule is pure and testable as `pensieve/scene/EquipTimeline.itemAt(List<ActionDef> actions, Map<String,String> base, String slot, float t) -> String`.
- [ ] **Step 1: Write the tests.**
  - `EquipTimelineTest`:
    - `baseBeforeAnyEquip`;
    - `lastEquipWins`;
    - `airClears` (returns `"minecraft:air"`);
    - `holdCountsAsMainhandEquip`;
    - `equipStateIsAFunctionOfTime` (the item at t 12 for an equip at t 10 is the same whether evaluated once or after stepping).
  - `SceneParserTest`: `parsesEquip`, `parsesWordSize`, `parsesSfx`.
  - `SceneValidatorTest`: `equipNeedsSlotAndItem`, `equipBadSlot`, `sizeOnlyForNarration`, `sizeRange`, `sfxVolumeRange`.
  - For sfx stopping: `MemoryAudio`'s stop path is client code. Put the "which cues stop on end" rule in a pure helper `pensieve/scene/CueKinds.stopsAtEnd(String kind)` (true for `voice` and `sfx`; music fades) with `sfxStopsOnClear` asserting it, and have `MemoryAudio` use the helper.
- [ ] **Step 2:** Run `./gradlew test --tests "*EquipTimeline*" --tests "*SceneParserTest*" --tests "*SceneValidatorTest*" --tests "*CueKinds*"` and expect FAIL.
- [ ] **Step 3: Implement.** Update every `new SceneDef.ActionDef` and `new SceneDef.WordDef` call site, in main and test. Update `SKILL.md` (actions table, words section, events table and the music/voice paragraph).
- [ ] **Step 4:** Run the full `./gradlew test` and expect PASS.
- [ ] **Step 5:** Commit. Message: `Add equip, narration size and sfx cues to memories`.

### Task 2: The `memory_door` entity (Sonnet)

**Interfaces:**
- `ModEntities.MEMORY_DOOR` (id `memory_door`, MISC, sized 1.0 × 3.0, `clientTrackingRange(10)`). The entity is a bare no-physics, no-gravity, unpickable, unpushable `Entity`, as `MemoryLetterEntity` is.
- **The model:** the leaf at scale 1 is 1.0 wide (local +X from the hinge), 3.0 tall and 0.2 thick. Its origin is the hinge's bottom corner, the entity position, so `look` yaw swings it about the hinge. Actor `scale` multiplies all three dimensions.
- `pensieve/scene/DoorHinge.corner(float yawDeg, float scale, int corner) -> double[3]`: the world offset of a leaf corner from the hinge, used by the renderer and the test. Corners are 0..7.
- **The texture:** `textures/entity/memory_door.png` (64 × 192, faces laid out by the renderer). It shows dark oak vertical planks with three wrought-iron strap hinges, iron studs and a ring handle, with a darker 2 px border. It is generated by `build_scripts/generate_memory_door_texture.py` (Pillow, or zlib if Pillow is missing).
- [ ] **Step 1: Write the tests.** `DoorHingeTest`:
  - `hingeStaysPut`: corners 0 and 1 (the hinge edge, bottom and top) are the same for every yaw and scale;
  - `swingsAboutHinge`: at yaw 0 the free edge is at local +X; at yaw −90 it has swung to +Z (Minecraft yaw convention, so check against `Mth`-style sin/cos);
  - `scaleMultiplies`: the leaf width at scale 4 is 4.0.
- [ ] **Step 2:** Run `./gradlew test --tests "*DoorHingeTest*"` and expect FAIL.
- [ ] **Step 3: Implement** the entity, `DoorHinge`, the renderer (a textured box from `DoorHinge` corners, `RenderType.entityCutout`, packed light, interpolated yaw via the `yaw` render argument), the texture generator and the PNG. Register it next to `MEMORY_LETTER`. Add an `en_us.json` entity name. Add `hogwartsmagic:memory_door` to `SKILL.md`, and document the yaw convention: a pair of doors is two leaves whose hinges sit on opposite jambs, one turning +yaw and the other −yaw.
- [ ] **Step 4:** Run `./gradlew test` and expect PASS. Run `./gradlew runServer` to Done with no `hogwartsmagic` errors.
- [ ] **Step 5:** Commit. Message: `Add the hinged memory_door actor`.

### Task 3: The `enchanted_ceiling` effect (Opus, visual quality)

**Interfaces:**
- **Scene format:** `{"kind": "effect", "id": "enchanted_ceiling", "at": [x, y, z], "spread": [hx, 0, hz], "duration": reveal}`.
  - `at` is the centre of the ceiling plane, and `y` is its height. `spread` is the half-size, each 1–100.
  - `duration` is the reveal, in ticks; default 60 (3 s).
  - Once revealed, the ceiling stays until the scene clears.
  - The validator requires `at` and `spread`.
- **Pure maths in `pensieve/scene/CeilingSky.java`:**
  - `revealAt(int elapsed, int duration) -> float` (0..1, ease-out).
  - `stars(long seed, float hx, float hz, int count) -> float[][]` (x, z offsets and size; deterministic; inside the rectangle).
  - `twinkle(float t, int starIndex) -> float` (0.35..1).
  - `cloudOffset(float t) -> float` (UV drift, 0.0006 per tick).
- **Look (client, `EnchantedCeiling`):**
  - A downward-facing plane at `at.y`, full-bright, drawn in `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`.
  - Base: a deep night gradient from #0B1030 at the edges to #1A2350 at the centre.
  - Stars: about 900 at 1–3 px equivalent, as small billboard-free quads 2 cm below the plane, twinkling.
  - Clouds: two layers of soft grey-blue cloud wisps (a tiling 128² texture generated by `build_scripts/generate_ceiling_textures.py`, alpha ≤ 0.35) scrolling slowly in opposite directions.
  - Reveal: the reveal fades the plane in from transparent, so the real roof shows through at first. The effect reads as the roof dissolving into sky.
  - Lifecycle: registered in `EffectVisuals` as a `Running`, removed by `clear()`.
- [ ] **Step 1: Write the tests.** `CeilingSkyTest`:
  - `revealEndsAtOne`, `revealStartsAtZero`, `revealMonotonic`;
  - `starsInsideRect`;
  - `starsDeterministic`;
  - `twinkleBounded`.
  - Validator tests: `ceilingNeedsSpread`, `ceilingNeedsAt`, `ceilingSpreadRange`.
- [ ] **Step 2:** Run `./gradlew test --tests "*CeilingSky*" --tests "*SceneValidatorTest*"` and expect FAIL.
- [ ] **Step 3: Implement** it, with `ceilingClearedWithScene` as a test on a pure `EffectVisuals`-independent registry if feasible; otherwise mark it for the in-game check. Write a dev scene, `run/pensieve_dev/dev_ceiling.json`: a stone room 30 × 20 with the ceiling effect and a few lit candles. Update `SKILL.md`.
- [ ] **Step 4:** Run `./gradlew test` and expect PASS.
- [ ] **Step 5:** Commit. Message: `Add the enchanted_ceiling memory effect`.

### Task 4: The hall set (Opus, design-heavy)

**Input:** `scratchpad/great_hall_out/great_hall.schem` (89 × 57 × 45, already 1.21.1-safe; `firefly_bush` became `azalea_leaves`), plus the measured facts below. The controller copies it to `build_scripts/pensieve_scenes/source/great_hall.schem` (committed, for reproducibility).
- **Hall interior:** x −79..−7, z −26..10 in world coordinates. In schem-local coordinates subtract (−89, −60, −30): interior x 10..82, z 4..40. The floor block is local y 0; standing is y 1.
- The long axis is X, with the staff end at the west (low x) and the entrance slot at the east (local x 83..85, z 21..23, y 1..12).
- The house tables run local x 24..74 at local z 8..12, 16..20, 24..28 and 32..36. The benches are wall signs. The dais is at local x 2..17. The lectern is at local (18, 2, 22).
- Floating candles: 676. Hanging posts come down to local y 40. The walls are 20 tall (to local y 20), and the roof ridge is at local y 56.

**Produces:**
- `data/hogwartsmagic/structure/great_hall.nbt`: a structure template written by `great_hall_set.py` from the schem plus these edits.
  1. **The doorway.** Widen the east entrance to a pointed arch 9 wide × 14 tall, centred on the hall axis (local z 22). Frame it with sandstone and gold trim to match. Leave the opening empty: the doors are `memory_door` actors.
  2. **The entrance hall.** Build it east of the doorway, about 26 deep × 30 wide × 22 tall, in grey stone (`stone_bricks`, `polished_andesite` floor with a `deepslate_tiles` runner). It has torches in iron brackets (`wall_torch`/`lantern` on chains), a grand staircase rising along the far wall, two tall arched windows (dark, night), banners, and enough floor in front of the doors for 12 first-years and McGonagall.
  3. **The sorting stool.** A stool (`spruce_fence` leg plus `spruce_pressure_plate` seat, or a `spruce_slab`) at the head of the centre aisle, in front of the staff table at local (19, 1, 22). Also add a `minecraft:light` level 15 set around the stool for the close-ups.
  4. **Lighting.** Check that every candle is `lit=true`. Add `minecraft:light` blocks at about 6-block spacing at local y 6 over the tables, so faces read in the dark hall.
  5. **The ceiling.** The ceiling plane for Task 3 goes at local y 21 (just above the walls), spanning the interior. Hide the hanging fence posts below y 21 (remove them) so the sky reads clean, and keep the candles below y 20.
  6. **Physics.** `physics_problems` must be clean, with the `sand` (1,698) supported. Replace unsupported sand with sandstone.
- `ANCHORS` (scene-relative, with the template placed so the centre-aisle floor at the staff end is near the origin; the module chooses `TEMPLATE_AT`):
  - `doors_hinge_n`, `doors_hinge_s`, `door_leaf_scale`;
  - `entrance_floor_center`, `first_year_spots` (12), `mcgonagall_entrance`;
  - `aisle_start`, `aisle_end`, `stool`, `stool_front`, `mcgonagall_stool`;
  - `staff_seats` (12, west to east, centre first), `table_seats` (dict house → list of seat positions with facing yaw, both sides of each table);
  - `ceiling_center`, `ceiling_spread`, `RADIUS`.
- [ ] **Step 1: Write `great_hall_set.py`.** It reads the schem (reuse `scene_common` NBT reading), applies the edits, writes the `.nbt` (gzipped NBT `size`, `palette`, `blocks`, `entities: []`, `DataVersion` 3953), and asserts that `physics_problems` is empty, the size fits `RADIUS` ≤ 120, and every anchor stands on a solid block with 2 air blocks above.
- [ ] **Step 2: Render previews** with the `rendering-minecraft-structures` skill:
  - the entrance hall facing the doors;
  - the hall from the doors (roof removed);
  - top-down.
  Send the user two images. Fix anything ugly.
- [ ] **Step 3:** Run `python -I build_scripts/pensieve_scenes/great_hall_set.py` (no assertion errors), then `./gradlew test --tests "*SceneDataTest*"`, and expect PASS.
- [ ] **Step 4:** Commit the script, source schem and nbt. Message: `Build the Great Hall memory set from the supplied hall`.

### Task 5: Sound effects and voices (Sonnet)

**ElevenLabs sound effects** (`build_scripts/sounds/great_hall_sfx.py`, reading the key file). Follow the memory note `reference_elevenlabs_sfx` exactly: prompt loud, read the envelope, trim per clip, level-match, and use input seeking.

| id (`pensieve.sfx.hall_*`) | Length | Prompt gist |
|---|---|---|
| `door_groan` | 4 s | huge ancient oak doors creaking open slowly, deep wooden groan, iron hinges, close and loud |
| `door_boom` | 3 s | massive wooden doors thudding fully open against stone walls, deep boom with stone-hall reverb |
| `murmur_a` / `murmur_b` | 20 s each | a large stone hall full of hundreds of chattering schoolchildren, dense continuous excited murmur, cutlery clinks, reverberant |
| `hush` | 3 s | a large crowd falling quickly silent, shushing |
| `cheer_big` | 6 s | a house table of a hundred students erupting in cheers, clapping and whoops in a stone hall |
| `cheer_small` | 5 s | applause and cheers, slightly smaller |
| `steps_group` | 8 s | a group of children's footsteps on stone flagstones walking together, continuous |
| `steps_one` | 4 s | a single child's footsteps on stone, nervous pace |
| `stool` | 1.5 s | a wooden stool scraping on a stone floor then creaking as someone sits |
| `ceiling_shimmer` | 4 s | a magical shimmering chime swell, glittering, like stars appearing |
| `hat_brim` | 2 s | a soft heavy fabric whoosh and rustle of an old leather hat lowered over the head, close |
| `whispers` | 5 s | a big hall of students whispering in surprise, dense hiss of whispers |

**Fish Audio voices** (`pensieve.voice.hall_*`). Voice ids come from the user's curated set (`harry-potter-dnd/game.js` `HP_FISH_VOICES`): McGonagall `d625beac29e2417faba8ace4c3d6ccdd` (speed 0.95), Dumbledore `90dfa283adee46eea0f69383dc5ab8f3`, the Hat (Stage 1 voice A) `c944589a55ad450e8109d39cd3ecc488`.

| id | Speaker | Line |
|---|---|---|
| `hall_hat_open` | Hat | "Hmm... So. Let us begin where every sorting begins." |
| `hall_mcg_doors` | McGonagall | "In a few moments you will pass through these doors and be sorted. Smarten yourselves up. Follow me." |
| `hall_mcg_call` | McGonagall | "When I call your name, you will come forward. I shall place the Sorting Hat on your head." |
| `hall_mcg_granger` | McGonagall | "Granger, Hermione." |
| `hall_mcg_weasley` | McGonagall | "Weasley, Ronald." |
| `hall_dumbledore` | Dumbledore | "Welcome! Welcome to a new year at Hogwarts." |

The hat's house shouts reuse Stage 1's `hogwartsmagic:sorting.voice.gryffindor`. Show the line table to the user before generating; the user approved the voices themselves earlier.

- [ ] **Step 1:** Generate, trim and register: `sounds.json` (sfx entries have no subtitle, as voice entries do), `ModSounds` `register("pensieve.sfx.hall_door_groan")` and so on. Add a script check that every `sounds.json` entry resolves to an .ogg.
- [ ] **Step 2:** Run `./gradlew test --tests "*SceneDataTest*"` and `./gradlew runServer` to Done, and expect PASS with no errors.
- [ ] **Step 3:** Commit. Message: `Add the Great Hall sound effects and voices`.

### Task 6: The Great Hall scene (Opus, cinematic)

**Consumes:** everything above. **Produces:** `memories/sorting.json` (id `sorting`, no `next`, ends with `eyes_close`), replacing the Stage 1 stub; and `great_hall_scene.py`. `sorting.py` is retired, but its `SEATED` root maths is kept by importing it if useful.

**Beat outline** (the implementer writes the full shot list first and keeps each beat within ±1.5 s; the total is about 75 s):

| t (s) | Beat |
|---|---|
| 0–4 | Black. `hall_hat_open` murmurs. `eyes_open` (3 s) from t 0.5. First person at the boy's eye height (0.75 scale) in the entrance hall, looking up at the towering doors in torchlight. The first-years shuffle (`steps_group`, low). |
| 4–11 | Cut to third person. McGonagall turns at the doors (`hall_mcg_doors`). Shots of the first-years: Hermione whispering, Ron swallowing, the boy among them. The murmur leaks under the doors (`murmur_a`, low volume). |
| 11–17 | **The doors.** A low wide angle on the doors. `door_groan`, and the two `memory_door` leaves swing inward over 3.5 s (ease `in_out`). A warm glow, and the murmur rises (`murmur_b`, full). `door_boom` as they hit. The camera pushes through the doorway (dolly 10 blocks, fov 70 → 85). The music cue enters here. |
| 17–30 | **The reveal.** A crane up from the aisle to high above the tables: the candles and the four tables of turning heads. The camera tilts up as `enchanted_ceiling` reveals (3 s) with `ceiling_shimmer`. A slow track down the staff table: Dumbledore centre, Hagrid (1.5) grinning, Snape staring, Quirrell twitching. The first-years walk the aisle (`steps_group`). |
| 30–35 | At the stool: McGonagall with the scroll (`offhand minecraft:paper`) and the hat (`mainhand hogwartsmagic:sorting_hat`). `hush`. Dumbledore's line, then `hall_mcg_call`. |
| 35–45 | `hall_mcg_granger`. Hermione hurries up and sits, and McGonagall `equip`s the hat on her head (a close-up). A beat, then the hat shouts Gryffindor (`sorting.voice.gryffindor`). `cheer_big`, and the Gryffindor table on its feet (seated students: arms up via `limbs`, staggered 0–6 ticks). Hermione beams and runs to the table (the hat goes back to McGonagall's hand via `equip` air plus `hold`). |
| 45–54 | `hall_mcg_weasley`. Ron does the same, faster. The hat shouts Gryffindor, `cheer_small`, and Ron sags with relief. |
| 54–60 | Quiet. McGonagall looks down at the scroll (a close-up push-in). `whispers`. A narration card shows `{viewer}` at `size` 3 for 4 s, centred: the name, large. Heads across the hall turn toward the boy. |
| 60–68 | The boy walks up the aisle alone (`steps_one`): a tracking shot from behind, then a frontal. He sits on the stool (`stool`) and looks out at the hall: a wide from behind the stool over the boy's shoulder at the four tables. |
| 68–75 | First person from the boy's eye on the stool, looking out at the hall. McGonagall's arm rises with the hat above the camera, and the camera pitches up slightly. `hat_brim`. `eyes_close` over 1.5 s from t 72. End at 75. |

**Rules:**
- Seated students: `table_seats` from Task 4. About 12 per table side, at 0.9 scale, in skins of their house (two variants alternating).
- Each seated student has small idle head-turns (look keys every 3–6 s, deterministic random) and turns to face the stool at 54 s.
- Staff are seated at `staff_seats`.
- The music cue ids come from Task 0. If none has arrived, the generator emits no `music` events and prints a warning.
- [ ] **Step 1:** Write the shot list in the report.
- [ ] **Step 2:** The generator asserts:
  - the JSON is under 262,144 characters;
  - every camera `pos` is in an air cell of the set;
  - all actor ids are unique;
  - the `eyes_close` is the last event.
- [ ] **Step 3:** Run `python -I build_scripts/pensieve_scenes/great_hall_scene.py`, then the full `./gradlew test` and `./gradlew runGameTestServer`. Expect all to pass. Update any Stage 1 test or gametest that names stub contents.
- [ ] **Step 4:** Commit. Message: `Stage the Sorting memory in the Great Hall`.

### Task 7 (controller): review and in-game check

- [ ] **Step 1:** Run the Opus final review of the whole range against this plan and the spec.
- [ ] **Step 2:** In game, with no client running during the build: put on the Sorting Hat as an unsorted player. Take screenshots of:
  1. the black open and the eyes opening on the doors;
  2. the doors swinging open about their hinges;
  3. the hall reveal, the candles and the ceiling;
  4. the staff table with Hagrid's size;
  5. Hermione's sorting and the cheer;
  6. the large name card;
  7. the first-person hat-down into the quiz.

  Also check that pressing X mid-scene leaves no doors, letters or sounds behind.
- [ ] **Step 3:** Send the screenshots to the user. Fix rounds go through a Sonnet implementer and an Opus re-review. Ask the user to push.

---

## Addendum (2026-10-20, user): the doors open like an animated film

The user wants the door opening animated like an animated movie, not a plain rotation. Three changes:

**A. Task 2b: door fixes and secondary motion (Sonnet, after Task 3 frees gradle).**
- **The Task 2 review fixes:**
  - Add a `memory_door_mirrored` entity type, a leaf with local x negated (faces re-wound outward), so a pair is one leaf mirrored and the two sit flush.
  - Light: take the max over the hinge block, its 4 neighbours and the block one leaf-width along, sampled at 1/3 of the height and above.
  - SKILL.md: state the yaw direction and fix the offset advice.
  - Comments.
  - Texture at 2× (128 × 384).
- **The iron ring handle as real geometry:** it hangs from a stud on the front face and swings as a damped pendulum driven by the leaf's angular acceleration.
  - Pure maths in `pensieve/scene/DoorRing.angle(float[] yawKeysSampled...)`, or a stateful per-entity spring stepped each client tick.
  - Constants: spring k 0.18, damping 0.86, max swing 50°.
  - Test: it settles to 0 at rest, swings opposite to the acceleration, and stays bounded.
- **Optional, decided by the implementer:** a 1-pixel shudder of the leaf on a sudden stop (yaw decelerating over 8° per tick), drawn as a ±0.3° jitter for 4 ticks.

**B. Task 3b: `light_rays` effect (Opus, the ceiling author continues).**
- Warm volumetric god-rays pour through a doorway: additive, full-bright, soft-edged translucent quads fanning out from a rectangle.
- **Keys:** `at` (doorway centre at the floor), `yaw` (the direction the light travels), `spread` `[half-width, height, length]`, `duration` (ticks to full width).
- **Animation:**
  - The opening width follows the actual gap, from a thin crack to full over `duration` (ease-in).
  - The rays shimmer slowly, and dust motes drift in them (`minecraft:white_ash`-like, client-drawn).
  - It stays until the scene clears.
- **Tests:** a pure `LightRays` maths class (gap width over time, monotonic and bounded; ray quads inside the box).

**C. Task 6 choreography rules for the doors** (animation principles, written into the generator):
1. **Anticipation:** both leaves first creak a hair *outward*, −2° over 6 ticks, with `door_groan` starting. A deep thud is heard (the lock).
2. **Stagger:** the left leaf leads by 3 ticks, so it never moves in perfect lockstep.
3. **Slow in, heavy out:** a slow crack open of 10° over 20 ticks, with `light_rays` starting as a thin bright line, then the swing to 92° over 28 ticks (ease `in`), heavy and accelerating.
4. **Overshoot and settle:** the leaves hit 92°, bounce back to 86° and settle at 90° (keys at +4 and +10 ticks, ease `out`), timed with `door_boom`.
5. **Camera shake** on the boom: 3 camera keys with ±0.08 block offsets over 6 ticks, plus dust falling from the lintel (`particles` `minecraft:white_ash`, count 80, across the lintel width).
6. **Light and sound bloom:** `minecraft:light` events brighten the entrance floor as the gap widens. The murmur rises, and the music hits on the boom.
7. **Cutting:** the shot before the doors is a low push-in on the doors. The swing itself is one held wide (no cuts during the motion), then a reverse shot from inside the hall of the doors settling, with the first-years silhouetted in the light.

## Addendum 2 (2026-10-20, user): look like the film

The user sent film stills (kept outside the repo, in the scratchpad `hall_refs/`). Two changes:

**D. Task 4b: restyle the hall to the film (Opus, Python only).** Re-skin and dress `great_hall_set.py`'s output to match the stills:
- **Walls:** warm grey-tan stone instead of the yellow sandstone and end stone. Remap the palette to tuff/mud/stone-brick tones, with darker mortar accents.
- **Gothic detail:** pillars with pointed arches along the side walls, and an upper gallery/arcade suggestion.
- **Lower walls:** dark oak wainscot panelling, about 4–5 high, with door-like panels.
- **Braziers:** big iron braziers burning on wall brackets between the pillars, in two rows at 2 heights (iron bars/chains with a campfire or fire on soul-free netherrack in an iron bowl), plus owl or eagle statues on the pillar corbels where they fit.
- **Floor:** pale flagstones with small dark diamond inserts (checker), and a centre aisle.
- **Tables:** long dark wood. Gold plates (the existing gold pressure plates) and goblets laid at every seat, with small lit candlesticks in a row down every table.
- **Staff end:** a tall gothic stained-glass window behind the staff table.
- **Banners:** house banners hung high on the end wall.
- **Lighting:** a warm candle-gold mood. The hall is lit by fire and candles, never flat.

Keep every Task 4 anchor valid, or update them and report the diffs. Keep `physics_problems` clean. Re-render the previews from the same angles as the stills (the doorway view down the aisle; the view from the staff dais over the tables) and send them to the user.

**E. Task 3c: a storm ceiling and floating candles (Opus, the ceiling author).**
- **`enchanted_ceiling`, restyled as the film's storm sky.**
  - Heavy, billowing night storm clouds in dark blue-grey and charcoal roll slowly, lit warm from below by the candle glow near the walls. Stars glint through gaps.
  - Distant lightning flickers inside the clouds every 6–14 s: a soft flash lighting a cloud region from within, sometimes a thin forked bolt across the sky. Each flash triggers a soft thunder sound (vanilla `entity.lightning_bolt.thunder` at low volume, or an ElevenLabs rumble).
  - The sky must read as *connected* to the ceiling: it starts where the walls end, with no seam. The roof beams fade into the cloud, and it reaches down slightly at the edges with mist.
  - Keep the reveal and dissolve. Keep a calmer style as `"style": "night"` and make `"storm"` the default.
- **New effect `floating_candles`:** hundreds of tall cream-white wax candles, about 0.12 × 0.5 to 0.8 blocks, with real flames (an additive flame sprite with flicker and a warm glow halo) floating in a box. They bob slowly and independently (±0.15 blocks, 3–6 s periods), with slight drift.
  - **Keys:** `at` (the centre), `spread` (half box) and `count` (up to 1500, default 400).
  - **Rendering:** client-drawn in one batched pass, no entities. Light comes from scene `minecraft:light` blocks.
  - **Tests:** pure positions are deterministic and inside the box, and the bob is bounded.
  - Replace the hall's block candles above head height with this effect in Task 6, or keep both.

## Addendum 3 (2026-10-20, user feedback round 1)

The user's notes on the scene:
1. **Mind intro.** On putting on the hat, the player first sees everything black, like the Stage 1 stub, with the boy sitting on a chair "inside your mind". Only then does the Great Hall begin.
   - The scene `sorting` becomes this intro, about 12 s:
     - a black void, with the boy seated (viewer skin, 0.75) on a lone chair in a soft pool of light;
     - silver memory wisps drifting;
     - the eyes open from black;
     - a slow orbiting camera;
     - the hat's `hall_hat_open` line.
   - It then chains (`next`) into `sorting_hall`, the Great Hall scene renamed. The hat line moves out of the hall scene.
2. **Claps after each house is announced.** A table starts cheering only once the hat's house shout has finished, not over it or before it.
3. **Ron must not walk through other students.** All walking paths are routed through free floor. The generator asserts that no moving actor comes within 0.6 blocks (horizontal) of any other actor at any sampled tick, sampled every 2 ticks, and that no walking actor passes through a table or bench cell.
4. **A unique skin for every student.**
   - **Older students:** about 30 per house, `student_<house>_<nn>`, procedurally generated with varied skin tone, hair colour, hair style (short, long, ponytail, curly), eye colour, and boys and girls, with the house trim and tie.
   - **First-years:** 10, `first_year_<nn>`, with plain robes.
   - The existing downloaded and generated house skins stay as extras.
5. **Everyone moves freely, not in unison.**
   - **Idle life:** each actor gets its own randomised life: head turns, glances at neighbours, small body shifts, talking gestures (an arm lifting), leaning on the table.
   - **Timing:** all of it is desynchronised, with per-actor delays drawn over wide windows.
   - **Group reactions:** turning to the doors, to the stool and to the name are staggered over 1.5–3 s with individual variance, and some students react late or not at all.
6. **The door light is too bright.**
   - `light_rays` gains an `intensity` key (0.1–2, default 1). The scene uses about 0.45.
   - The scene also uses fewer, weaker light-block events at the doorway.
7. **It all feels a bit too fast.**
   - Slow the hall scene to about 100 s: longer holds (shots 4–7 s), slower camera moves, more room between beats and lines, and the doors' swing about 1.4× longer.
   - Raise `PensieveStartPacket.MAX_JSON` as engine work 10 allows (to 900,000, within the 1 MiB custom-payload limit, checked on both encode and decode) so the richer crowd fits.
