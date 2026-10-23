# The First Task Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first time a player helps kill a Hungarian Horntail, a ~115 s film memory plays once the death animation ends: the champion's tent, the arena and the Horntail on her nest, "Accio Firebolt!", the chase over Hogwarts' roofs that ends with her crashing into a bridge, and the golden egg grabbed in first person before a roaring crowd. Then the player wakes where they were.

**Architecture:**
- **One chain of four scenes** (no house variants):
  - `first_task` (the tent, 16 s, its own small set);
  - `first_task_arena` (the reveal, the roar, the summon, 36 s);
  - `first_task_chase` (the snap, the launch, the castle, the crash, 47 s);
  - `first_task_egg` (the dive and the grab, 16 s, `end_action: "wake"`).
- **Two sets built by Python:**
  - `first_task_tent` is small, so it builds at the opening;
  - `first_task_grounds` holds the rocky ravine arena, its stands and nest, the Mini Hogwarts as a `schematics` entry, the terrain that joins them, and a real bridge. It is built as the standby for each of the last three scenes.
- **New engine:**
  - the memory Horntail actor, with flight along spline paths, wingbeats matched to speed, state cues (roar, bite, breath, takeoff, stagger) and auto-bank;
  - a `breathe` action that draws a real jet of dragon fire from her snout, with smoke, a heat shimmer and scorch marks;
  - a white chain hand-over;
  - props: the Firebolt broom actor, the golden egg, the champion robes, and a `triwizard` crowd style.
- **Reused from the Flying memory, not rebuilt:**
  - `crowd` and `crowd_react`, with the `flying_crowd_swell/erupt/gasp/chant` layers;
  - the broom rider rig and ride windows, and spline actor tracks;
  - the `sky` effect (here `overcast`), the sfx `loop` flag, the smoothed clock;
  - spline cameras, the `scene_engine` mirrors, and the Flying generator kit (`Sc`, `Keys`, `Rig`, the checks) once Task 10 lifts it out of `flying_scene.py`.
- **Trigger:** `FirstTaskMemory`. Server-side damage records on each Horntail; on its death, every player who damaged it in the last 60 s and is within 64 blocks is marked pending, and the memory starts 21 ticks later, or when they are next free.

**Tech Stack:** Forge 1.21 (51.0.33), Java 21, JUnit 5, Forge gametests, Python 3 generators (stdlib only, plus Pillow for textures), Fish Audio TTS, ElevenLabs sound generation, ffmpeg.

**Spec:** `docs/superpowers/specs/2026-10-21-first-task-memory-design.md`. Read it with this plan. The precedents are the Flying memory (`docs/superpowers/plans/2026-10-21-flying-memory.md`, its ledger `.superpowers/sdd/2026-10-21-flying-memory/progress.md` and the task reports there) and the Letter memory (`docs/superpowers/plans/2026-10-21-letter-memory.md`).

**Ledger:** `.superpowers/sdd/2026-10-23-first-task-memory/progress.md`. It holds the parallel grouping, the rulings and every task's status. It is never committed.

**Precondition, and what can run now:** the Flying memory's Task 10 is still in progress. It owns `build_scripts/pensieve_scenes/flying_scene.py`, `SceneDataTest`, the `flying_*.json` scenes, and the music lines in `sounds.json` and `ModSounds`. Until it is committed:
- only the tasks marked **[parallel now]** may run;
- the tasks marked **[gradle lane]** may run one at a time, holding the gradle lock for every gradle command, and must not edit any of the files Flying owns, nor `scene_engine.py`;
- the tasks marked **[after Flying T10]** wait for it.

**Review gates (each task):** each task ends with the SDD two-stage review: spec compliance first, then quality, both on Opus. Then come at most 5 fix rounds, each with a scoped re-review, and each round is noted in the ledger. A task that depends on another starts only after that task's review is clean. Implementers never push; the controller runs `timeout 60 git push origin sorting-memory` after every commit. Previews meant for the user go to the controller, never straight to the user. Model choice: Opus for design-heavy and cinematic work and for retries after a failure, Sonnet for well-specified coding, never Haiku.

## Global Constraints

Every task's requirements include these.

**Code and build**
- **Server/client safety:**
  - Nothing in `pensieve/`, `entity/`, `item/`, `network/` or `event/` (outside the client events) imports `net.minecraft.client.*` or `com.hogwartsmagic.client.*`.
  - New entity types register in common code, and their renderers register client-side only.
  - The trigger, the flags and the damage records are server-side.
  - `MemoryHorntailEntity` is a client puppet. If one is ever made on a server (a `/summon`), it discards itself on its first server tick.
- **Client code:** no `switch` on an enum (map names with `AttackState.valueOf` or a `Map`). Never use entity event bytes 21, 35 or 63.
- **Gradle:**
  - Never run it while a Minecraft dev client runs (`tasklist | findstr java`; the daemon is fine).
  - Take the lock first with `mkdir .superpowers/gradle.lock`. If it exists, wait 20 s and retry. `rmdir` it afterwards, even on failure.
  - Other agents work in this checkout, the Flying Task 10 agent among them.
- **Shared files:** before editing `SKILL.md`, `SceneNames`, `SceneValidator`, `SceneParser`, `MemoryActors`, `EffectVisuals`, `ModEntities`, `ClientModEvents`, `ModSounds`, `sounds.json` or `scene_engine.py`, re-read the file from disk. Run `git diff --stat` before staging. If a file to be staged holds hunks that are not yours, stop and report; never commit them (the 24a8039 lesson).

**Scenes**
- **Limits:**
  - Each scene JSON is at most 900,000 characters and under 1,000,000 UTF-8 bytes.
  - The radius is at most 200, unless Task 2 proves the grounds cannot fit (then Task 2b).
  - Follow `.claude/skills/writing-pensieve-memories/SKILL.md`, and write the shot list first.
- **Lines:**
  - No spoken line contains the player's name. The viewer is "you" or "our champion", and the name appears only as `{viewer}` text.
  - Dialogue is original, apart from "Accio Firebolt!".
  - Every spoken line has a subtitle with its measured clip length.
- **The champion (the viewer):**
  - 14 years old, at scale 0.85, with `skin.viewer`;
  - `hogwartsmagic:triwizard_robes` in `chest` (house-neutral; no line uses the house);
  - `{viewer_wand}` in the main hand until the grab.
- **First person** only for three moments: the summon, the moment of mounting, and the egg grab.
- **Sets:**
  - `physics_problems` must be empty.
  - The set built at the opening (`first_task_tent`) is at most 100,000 blocks.
  - `first_task_grounds` (template plus schematic plus fills) is at most 450,000 blocks and is built only as a standby. The scene before each standby lasts at least 2 × its build ticks at 4,000 a tick; the tent is the tight one (16 s ≥ 2 × 113 ticks).
- **The other champions:** none on screen except a glimpse in the tent, and no other dragons.

**Generator asserts.** These run on every generation of every scene. Each prints its worst number, and each is shown to bite once by a mutation, noted in the report. Asserts 1–10 are the Flying set, unchanged in their limits, and run through the shared kit (Task 10) and the `scene_engine` mirrors:
1. **Engine mirrors only.** This covers `scene_engine.py` plus `dragon_engine.py` (Task 6).
2. **Spline camera moves** (`check_spline_moves`).
3. **Smoothness per frame:** `a` ≤ 0.002, `aw` ≤ 0.08, `ja` ≤ 0.0003, `jw` ≤ 0.01, sampled at 60 fps with cut pairs left out.
4. **Clearance per tick:** 4 samples a tick.
   - The camera stays ≥ 0.15 from player part boxes and from broom, ball and egg boxes. It stays ≥ 0.3 from every Horntail capsule (`dragon_engine.horntail_parts`), unless no point of that part is in the frustum (aspect 2.0). The first-person head is exempt.
   - The camera stays in air (0.06), its spline path included.
5. **Hand contacts ≤ 0.15 at every sway corner:**
   - the Firebolt into the raised hand;
   - the mount;
   - the hand on the golden egg.
6. **Held items compensated** (`item_raise_steps`). In first person, hand motion is ≤ 0.03 a tick relative to the camera.
7. **Actions end with their moment.** See asserts 11–15 for the First Task causes.
8. **Fliers (the Firebolt):**
   - it stays ≥ 0.5 from every block except at the mount and over the nest dive (named windows);
   - it stays ≥ 1.5 from every Horntail capsule, except the snap, at ≥ 0.6, named;
   - speed changes by ≤ 0.12 blocks/tick², with named exceptions;
   - the lowest broom point stays ≥ 0.6 above the nest's top blocks during the grab.
9. **Ride windows are continuous** within 0.02, bracketed with keys at `T-0.001` and `T`.
10. **Size and wiring:**
    - sizes are under the caps, ids are unique and keys sorted;
    - the chain runs `first_task` → `first_task_arena` → `first_task_chase` → `first_task_egg`, and the last ends `wake` on shut lids;
    - `first_task` opens on `eyes_open` at t 0;
    - no `words` text contains a name placeholder other than `{viewer}`;
    - two runs give byte-identical output (md5).

Asserts 11–17 are new for this memory:

11. **The dragon:**
    - Every Horntail capsule (wings at their current sweep) stays ≥ 0.8 from every block. The named exceptions are:
      - the perch, where her feet are on the nest;
      - the `slate_hits` windows (her tail through the roof cells being smashed);
      - the crash window.
    - Her speed changes by ≤ 0.08 blocks/tick², with the launch and the crash named.
    - Her cue states match her path: no `FLY` or `AIR_BREATH` while she is perched, and no ground cue while she is airborne.
12. **Fire:**
    - Every `breathe` lies inside the fire window of a `BREATH` or `AIR_BREATH` cue (`dragon_engine.fire_window`).
    - The jet (`dragon_engine.fire_jet`, cut at the first solid cell) stays ≥ 1.5 from every part box of the viewer.
    - Every scripted hit lands: the rock face behind the hiding spot (twice), and the castle roofs. The jet's ray meets the named target box.
    - A `first_task_fire_roar` cue starts within 1 tick of each `breathe`.
13. **Wingbeats:** every `first_task_wingbeat` cue lands within 1 tick of a downstroke from `dragon_engine.downstroke_ticks`.
14. **Crash and smash:**
    - The Horntail's capsules first meet the bridge's `break_box` at the crash tick, ±1.
    - The bridge `block` events (to `minecraft:air`), the debris `memory_block` launches and the `first_task_bridge_crash` cue all fall in that tick ±1. Nothing of the bridge changes before it.
    - Each slate smash works the same way against its roof cells.
    - The chain snaps on its tick, and its sfx lands within 1 tick of it.
15. **The crowd:**
    - a `first_task_crowd_hush` cue and a `sit` follow within 0.5 s of the roar's start;
    - a `gasp` comes within 1 s of a near miss (a jet within 3, or her jaws within 2, of the viewer);
    - a `cheer` comes within 0.5 s of the Firebolt passing over a stand;
    - the `erupt` comes within 0.3 s of the grab tick;
    - a cannon (sfx plus a smoke `particles` burst at a `cannon_spots` anchor, on the same tick) fires at the call and after the grab;
    - nobody reacts without a cause in the window before it.
16. **Build budget,** per the Sets rule above, asserted from the emitted `set` blocks.
17. **The egg:** the golden egg actor is parked on the tick the hand closes, and `hold hogwartsmagic:golden_egg_prop` is compensated. The hand is ≤ 0.15 from the egg centre at every corner.

**Git and assets**
- **Git:**
  - Never `git stash`, `git reset`, `git checkout -- <file>`, `git add -A` or `git add .`. Stage by explicit path.
  - Never stage `.superpowers/`, `.claude/settings.local.json`, `.claude/skills/rendering-minecraft-structures/textured/viewer.html`, `memories/lake_patronus.json`, `build.gradle`, `logs/`, `run/` or `build_scripts/pensieve_scenes/source/`.
  - End every commit with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Keys:** `C:\Users\alonl\fish_key.txt` and `C:\Users\alonl\eleven_key.txt` are read from file, never printed, logged or committed.
- **Music:** `run/music/first_task/` (git-ignored) holds `harry_sees_dragons.mp3` (113 s) and `golden_egg.mp3` (370 s). They are for private use only, like the Great Hall and Flying cues.
- **Shell:** Bash heredocs break on any apostrophe in the body, so write files with the Write tool. Never dispatch subagents from inside a task.

## Review Focus

1. **Who gets it.** These cases are covered by Task 9's gametests:
   - two players both damaged her, one is 70 blocks away, and one damaged her 61 s before the kill;
   - a wolf or fire gets the kill;
   - a spell or arrow did the damage (it counts for its caster or shooter).

   Each eligible player gets it once, and nobody else does.
2. **Busy or absent at the kill.** These cases keep the player pending and start the memory when they are free, never inside another memory or its wake: a player inside another memory, in its wake, dead, or logging out before the 21 ticks are up. They are covered by `inMemoryWaitsThenStarts`, `wakeOfAnotherMemoryWaits`, `deadAtKillStartsAfterRespawn` and `logoutKeepsPendingStartsAfterLogin`.
3. **A slow server during the grounds standby** (about 450k blocks while the 16 s tent plays): no clock rewind, and at worst the 60-tick hold in white at the hand-over. Covered by assert 16 and a timed check in Task 14 (the log's `Pensieve clock` lines).
4. **Frame cost in the arena:** about 3,500 spectators, two fire jets, smoke, the Horntail and the castle at 60 fps. Covered by Task 7's jet budget test, the `Pensieve crowd:` and `Pensieve fire:` dev-scene timers, and the in-game check in Task 14.
5. **The dragon clipping the castle before the crash,** her wing tips included. Covered by assert 11 with the wings at their swept extent, and by the chase previews in Task 13.

---

## File map

All paths are under `src/main/java/com/hogwartsmagic/` (Java) or `src/main/resources/{assets,data}/hogwartsmagic/` unless they start with `build_scripts/`.

| File | Task | Responsibility |
|---|---|---|
| `build_scripts/pensieve_scenes/first_task_tent.py`, `structure/first_task_tent.nbt` | 1 | The champions' tent |
| `build_scripts/pensieve_scenes/first_task_grounds.py`, `structure/first_task_grounds.nbt` | 2 | Arena, stands, nest, castle, bridge |
| `build_scripts/sounds/first_task_sfx.py`, `sounds/pensieve/sfx/first_task_*.ogg` | 3 | Sound effects (files only) |
| `build_scripts/sounds/first_task_voices.py`, `first_task_clip_lengths.json`, `sounds/pensieve/voice/first_task_*.ogg` | 4 | Voices (files only) |
| `build_scripts/sounds/first_task_music.py`, `sounds/pensieve/music/first_task_*.ogg` | 5 | Music cuts (files only) |
| `entity/MemoryHorntailEntity.java`, `client/MemoryHorntailRenderer.java`, `pensieve/scene/DragonFlight.java`, `pensieve/scene/HorntailCues.java`, `entity/HungarianHorntailEntity.java` (hooks), `client/model/HungarianHorntailModel.java` (hooks), `MemoryActors`, `SceneNames`/`SceneValidator`, `ModEntities`, `ModEntityAttributes`, `ClientModEvents`, `build_scripts/pensieve_scenes/dragon_engine.py`, `horntail_cases.json`, `dev_horntail.py` | 6 | The memory Horntail |
| `pensieve/scene/DragonFire.java`, `client/pensieve/DragonFireRenderer.java`, `SceneParser`/`SceneValidator`/`SceneNames`, `dragon_engine.py`, `dev_dragon_fire.py` | 7 | The fire jet, smoke, shimmer and scorch |
| `client/pensieve/PensieveTimeline.java`, `PensieveOverlay`, `SceneDef`/`SceneParser`/`SceneValidator` | 8 | The white hand-over |
| `pensieve/FirstTaskMemory.java`, `gametest/FirstTaskMemoryGameTests.java`, SKILL.md | 9 | The trigger |
| `build_scripts/pensieve_scenes/scene_kit.py` (new), `flying_scene.py`, `test_scene_kit.py` | 10 | The shared generator kit |
| `entity/MemoryBroomEntity`/`ModEntities` (Firebolt type), `client/MemoryBroomRenderer`, `BroomFlight`, `entity/MemoryGoldenEggEntity.java`, `client/MemoryGoldenEggRenderer.java`, `ExampleMod` (items), `pensieve/scene/Crowd.java`, `scene_engine.py`, `build_scripts/generate_first_task_textures.py` | 11 | Firebolt, golden egg, champion robes, `triwizard` crowd |
| `sounds.json`, `ModSounds.java`, `lang/en_us.json` | 12 | Registration of every First Task sound |
| `build_scripts/pensieve_scenes/first_task_scene.py`, `memories/first_task*.json` (4), `SceneDataTest` | 13 | The scenes |

---

### Task 0 (controller, with the user): picks and decisions

Per the user's rule, defer non-critical questions: build on the rulings in the ledger, and ask everything at the end in one round.
- [ ] **Step 1: Voices.** Task 4 makes the samples. The user picks:
  - the commentator: `7c90feecce0e4bbb8d5d1a20a98e6a11`, already picked;
  - the viewer's "Accio Firebolt!": three takes with young Harry `67ccf061bf3b4f9c8b635addc77eba6f`, and three each from two Fish-searched teenage-boy voices. The champion is 14, and the young Harry clone sounds 11. The default ruling is the best young Harry take.
- [ ] **Step 2: Lines.** Show the user Task 4's line table for approval, at the end.
- [ ] **Step 3: Decisions.** Present the "Decisions the user can override" section of the ledger, at the end.

### Task 1: The champions' tent set (Opus, design-heavy) [parallel now]

**Interfaces:**
- **Produces:** `first_task_tent.py` with `build(world) -> ANCHORS`, `write_nbt()`, `TEMPLATE_AT` and `RADIUS`, and the template `first_task_tent.nbt` (≤ 40,000 blocks).
- **The set:**
  - a canvas tent about 14 × 9 × 20, cream and white wool walls with a pitched roof on poles;
  - braziers (campfires) and lanterns, a cot, a table and a bench;
  - a flap opening on one short end onto a bright strip of trampled ground and the back of a timber stand;
  - closed underneath, with light blocks so the dim interior reads.
- **`ANCHORS`:**
  - `viewer_start` (on the bench, with a seat top), `flap` ({centre, yaw out, half-width, height});
  - `champions` (3 standing or seated spots in the back half);
  - `lanterns` (4 hanging points, for `memory_block` lanterns that swing on each thud);
  - `dust_boxes` (canvas areas that shed dust particles);
  - `cam_spots` (3);
  - `floor_y`.
- [ ] **Step 1: Build.**
  - **Asserts:** physics clean; every anchor on solid ground with air above; the flap opening all air; the template ≤ 40,000 blocks.
  - **Previews** with `rendering-minecraft-structures`, run one at a time with Task 2's: the interior from `cam_spots[0]`, and the flap looking out. Look at them and iterate, then hand them to the controller.
- [ ] **Step 2: Commit.** Commit the script and the `.nbt` (no gradle). Message: `Build the champions' tent for the First Task memory`.

### Task 2: The arena and the castle grounds (Opus, design-heavy) [parallel now]

**Interfaces:**
- **Produces:** `first_task_grounds.py` with `build(world) -> ANCHORS`, `write_nbt()`, `TEMPLATE_AT`, `RADIUS`, `SCHEMATIC` (the Mini Hogwarts `{id, at, rotation}` entry, read with `scene_common.schematic_grid`/`rotate_xz`) and `FILLS` (the bridge or viaduct and the ground joins, if any), plus the template `first_task_grounds.nbt`. The total is ≤ 450,000 blocks.
- **The set:**
  - **The arena:** a rocky ravine of stone, andesite and tuff walls, with scree.
  - **The stands:** a ring of timber stands on every side, with seat rows for about 3,500 spectators. Each row's head block (seat top + 1.2) is in air.
  - **The nest:** a heap of boulders at the centre, with vanilla `minecraft:dragon_egg` blocks resting on solid rock (physics clean) and a gap for the golden egg.
  - **The ground pieces:** the champions' entrance gap, the hiding rock (a boulder about 3 tall with a flat face toward the nest), the stake for the chain, and two cannon platforms on the stands.
  - **The castle:** the Mini Hogwarts on its hill, 80 to 150 blocks from the arena, joined by real terrain (no floating edge in any chase-height view; the Flying `void_fraction` check ≤ 0.02 from `castle_view`).
  - **The bridge:**
    - Survey the schematic for a bridge span with ≥ 6 blocks of clear air under it and a dragon-wide approach.
    - If there is none, build a stone viaduct as a real part of the set, joining the castle hill to a tower or an outcrop. Make it 3 wide, with an arch ≥ 8 tall and ≥ 14 wide.
- **`ANCHORS`:**
  - `ground_y`, `nest_centre`, `nest_perch` ({feet point, yaw}), `egg_spot` (the golden egg centre), `eggs`;
  - `entrance` ({point, yaw in}), `hide_rock` ({hide spot, face centre, face normal, face box});
  - `stake`, `cannon_spots` (2), `stands` (a list of {rows: seat-top segments}), `commentator_box`;
  - `broom_lane` (spline waypoints of an all-air corridor over a stand into the arena, for the Firebolt);
  - `arena_air_box`, `launch_lane` (air waypoints out of the arena toward the castle);
  - `castle_route` (air waypoints over and between the towers, each ≥ 6 from every block);
  - `slate_roofs` (a list of {cells, roof normal}: roof cells the tail smashes);
  - `bridge` ({span box, `break_box`, the cells that break, the underside y, `fall_column`: an air column below});
  - `castle_view`, `arena_view` (camera spots).
- [ ] **Step 1: Build.**
  - **Asserts:**
    - physics clean;
    - anchors on solid ground with air, and every seat row on solid seat tops with 2 air above;
    - `broom_lane`, `launch_lane`, `castle_route`, `arena_air_box` and `fall_column` all air, with the stated clearances;
    - the count of cells beyond the radius is 0;
    - the total ≤ 450,000;
    - `void_fraction` ≤ 0.02.
  - **Previews,** one at a time with Task 1's: the wide arena with the stands and the nest; the hiding rock from the nest; the castle from `launch_lane`; and the bridge from under it. Iterate, then hand the arena and the bridge to the controller.
  - If the arena, the castle and the joining ground cannot fit ±200 without cutting the stands or the castle, stop and report; Task 2b raises the cap.
- [ ] **Step 2: Commit.** Commit the script, the `.nbt` and any `.gitignore` line for a source folder. Message: `Build the First Task arena beside Hogwarts`.

### Task 2b (only if Task 2 reports no fit): radius 256 (Sonnet) [gradle lane]

The Flying plan's Task 6b, never built.
- [ ] **Step 1: Write the failing tests.** `SceneValidatorTest.radius256Accepted` (257 rejected), and a gametest `bigRadiusSetBuildsAndClears`.
- [ ] **Step 2: Implement.** Raise the cap to 256 in `SceneValidator`, `PensieveSessions.MAX_RADIUS` and SKILL.md. `RegionAllocator.SPACING` 1024 still exceeds 2 × 256. Run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Allow memory sets up to radius 256`.

### Task 3: Sound effects (Sonnet) [parallel now: files only]

ElevenLabs takes, mastered per `reference_elevenlabs_sfx`: envelope-trimmed so the transient opens the clip, RMS-matched to −18 dBFS, limited at 0.65, with input seeking in both passes. Loops are made seamless at `latency=1`, as Flying's fix round did. Prompts ask for the event, loud and dense. The files go in `sounds/pensieve/sfx/`; **do not touch `sounds.json` or `ModSounds`** (that is Task 12).

| id | what | length |
|---|---|---|
| `first_task_tent_crowd` | a huge crowd roaring outside, muffled through heavy canvas | 20 s loop |
| `first_task_ground_thud` | a colossal creature stamping, ground shaking, things rattling | 2 s |
| `first_task_cannon` | a single cannon boom echoing off mountains | 3 s |
| `first_task_crowd_bed` | a packed stadium in daylight, excited roar and chatter, no rain | 20 s loop |
| `first_task_crowd_hush` | a roaring crowd falling to a stunned hush | 3 s |
| `first_task_horntail_roar` | an enormous dragon's roar, rasping and furious | 3.5 s |
| `first_task_horntail_snap` | huge jaws snapping shut with a snarl | 1.2 s |
| `first_task_wingbeat` | one huge leathery wingbeat, a deep whump of air | 1.0 s |
| `first_task_fire_roar` | a roaring jet of dragon fire | 2.5 s |
| `first_task_fire_scorch` | flame blasting against rock, hissing and crackling | 2 s |
| `first_task_chain_snap` | a massive iron chain snapping, links whipping | 1.5 s |
| `first_task_slate_smash` | roof slates smashing and sliding off a roof | 2 s |
| `first_task_bridge_crash` | a stone bridge smashed, masonry crashing down | 4 s |
| `first_task_accio` | a magical summoning swish rising to a whoosh | 1.2 s |
| `first_task_wind_high` | a fast wind rush at height | 20 s loop |
| `first_task_egg_grab` | a hand closing on a heavy metal egg, a gold clink | 0.5 s |

Reused, not regenerated: `flying_crowd_swell`, `flying_crowd_erupt`, `flying_crowd_gasp`, `flying_crowd_chant` and `flying_broom_whoosh`. The Horntail's own `sounds/horntail/*.ogg` are kept as fallbacks for an A/B in review.
- [ ] **Step 1: Generate.** Write `build_scripts/sounds/first_task_sfx.py`, with the `WINDOWS` table of measured cut points in its docstring. It writes the 16 OGGs (mono 44.1 kHz Vorbis).
- [ ] **Step 2: Check.** Running `python -I build_scripts/sounds/first_task_sfx.py check` prints each file's length, RMS and peak, and each loop's seam step. Expect every row within the length ±20%, peaks ≤ 0.65, and every loop seam step ≤ 0.02.
- [ ] **Step 3: Commit** the script and the OGGs. Message: `Make the First Task memory sound effects`.

### Task 4: Voices (Sonnet) [parallel now: files only]

**Lines.** They are original and contain no player name. The user approves them at the end (Task 0). The final table goes in `first_task_voices.py`, and measured lengths go in `first_task_clip_lengths.json`.

| id | Speaker | Line |
|---|---|---|
| `first_task_comm_call` | Commentator (in the tent: the baked muffled take) | "And now, ladies and gentlemen, our final champion!" |
| `first_task_comm_horntail` | Commentator | "And here she is, the Hungarian Horntail! Nerves of steel, now!" |
| `first_task_comm_accio` | Commentator | "A Summoning Charm! Oh, that's clever, very clever!" |
| `first_task_comm_chain` | Commentator | "Her chain's gone! She's loose, she's after them!" |
| `first_task_comm_egg` | Commentator | "They've done it! The golden egg! What a flight!" |
| `first_task_viewer_accio` | the viewer | "Accio Firebolt!" |

- **Voices:**
  - the commentator is `7c90feecce0e4bbb8d5d1a20a98e6a11` (the user's Flying pick);
  - the viewer is young Harry `67ccf061bf3b4f9c8b635addc77eba6f` by default. Also make three takes each from two Fish-searched teenage-boy voices, into `run/voice_samples/first_task/`, because the champion is 14.
- **The pipeline:** per `reference_fish_audio_voices` and the Flying fix round: silence trim, all clips to −16 LUFS by linear gain plus a −1.5 dBTP limiter, mono 44.1 kHz Vorbis. `first_task_comm_call_muffled` is the same take through a 900 Hz low-pass with a short room reverb (it is the one the tent plays).
- **Do not touch** `sounds.json` or `ModSounds` (that is Task 12).
- [ ] **Step 1: Generate** the samples and the default takes, and listen-check them by measuring length and loudness: every clip at −16 ±1 LUFS, and no clip with more than 0.25 s of lead silence.
- [ ] **Step 2: Commit** the script, the OGGs and the lengths. Message: `Voice the First Task memory`.

### Task 5: Music cuts (Sonnet) [parallel now: files only]

**Interfaces:** `build_scripts/sounds/first_task_music.py`, built like `letter_music.py`. It has a `CUES` table `{cue: (source, start s, end s, fade in s, fade out s)}`, one gain per source, and baked fades. It writes stereo 44.1 kHz Vorbis with a −1.5 dBTP limiter. It has three modes:
- `python -I build_scripts/sounds/first_task_music.py` cuts every cue;
- `check` prints each cue's length and loudness;
- `points <source>` prints the loudness envelope (0.5 s bins) and the 12 strongest onsets, so Task 13 can land the roar, the snap, the crash and the grab on hits.

| cue | source | role | length |
|---|---|---|---|
| `first_task_tent` | `harry_sees_dragons` | the tense waiting bed under the tent, out with the white fade | about 18 s |
| `first_task_arena` | `golden_egg` | the reveal, the roar, the summon | about 38 s |
| `first_task_chase` | `golden_egg` | the chase, peaking on the crash | about 49 s |
| `first_task_egg` | `golden_egg` | the dive, the grab and the triumph, out into the wake | about 18 s |

- Consecutive cues overlap about 2 s across each hand-over.
- The cut points are first picks from the envelope (the docstring says why each was chosen). Task 13 retimes `CUES` to the final beats and reruns the script.
- **Do not register** them (that is Task 12).
- [ ] **Step 1: Cut.** Write the script, run `points` on both sources, choose the cuts, and run `check`. Expect four files within their lengths ±1 s, at about −20 LUFS, with peaks ≤ −1.5 dBTP.
- [ ] **Step 2: Commit** the script and the OGGs (the music is private use, as the Flying cues are). Message: `Cut the First Task memory music`.

### Task 6: The memory Horntail (Opus) [gradle lane]

**Interfaces:**
- **Actor type `hogwartsmagic:memory_horntail`:**
  - `MemoryHorntailEntity extends HungarianHorntailEntity`, with no goals. On a server it calls `discard()` on its first tick.
  - Its attributes are copied from the Horntail's in `ModEntityAttributes`.
  - `MemoryHorntailRenderer extends HungarianHorntailRenderer`. At `scale` 1 the size is that of the real dragon (its 1.15 render scale), and the eyes layer is kept.
- **Frame:**
  - The `path` is the root, the ground point under the body at rest.
  - The body's pivot is `DragonFlight.PIVOT_Y` = (24 − 9) / 16 × 1.15 = 1.078 blocks × scale above it. Pitch and roll turn about this pivot.
  - `look` takes `yaw`, `pitch` (nose down positive) and `roll` (right side down positive); add the type to `ROLL_TYPES`.
  - **Without `look`** (as the brooms, through `BroomFlight`'s path helpers, not a copy):
    - yaw follows the heading;
    - pitch follows the climb, clamped ±45 and smoothstepped to level below 0.05 blocks/tick;
    - roll is `DragonFlight.bank`: 1000 × the sideways acceleration, softly limited to 50 by a tanh, faded out below 0.1 blocks/tick.
- **Cues: `HorntailCues.at(List<Cue> anims, double t) -> State(String name, int stateTick, boolean flying)`.**
  - Cue names are `IDLE FLY TAKEOFF LAND ROAR BITE TAIL_WHIP BREATH_WINDUP BREATH WING_GUST DIVE STAGGER AIR_BREATH`, case-insensitive. They are listed in `SceneNames.HORNTAIL_ANIMS`, and the validator rejects any other name for this type.
  - `TAKEOFF` and `FLY` set flying. `LAND` clears it at the end of its `LAND_DURATION`. `IDLE` clears it. Every other cue keeps the flying flag.
  - Once a one-shot passes its `*_DURATION` from `HungarianHorntailEntity`, the state is `FLY` if flying, else `NONE`.
  - `stateTick` is the whole ticks since the cue.
- **Wingbeats** (pure, a function of the path and the clock, so a late joiner matches):
  - `DragonFlight.beatRate(double speed, double climb) -> double` gives rad/tick:
    - 0.34 at a hover (speed < 0.1);
    - easing to 0.20 at a cruise of 0.8 blocks/tick;
    - × (1 + 0.6 × clamp(climb / 0.3, 0, 1)) when climbing;
    - easing to 0 in a glide (speed > 0.9 and climb < −0.15).
  - `wingAmp(speed, climb)` is 0.85 at a hover or climb, 0.60 at a cruise and 0.15 in a glide (wings held half up). Every blend is a smoothstep, so the rate never steps.
  - `wingPhase(Track path, double t)` is the sum of the rate over whole ticks from 0, linear within a tick, and cached per actor.
  - `downstrokes(Track path, int t0, int t1) -> int[]` gives the ticks where sin(phase) reaches −1 (the end of a downstroke).
- **Model hooks** (common, `HorntailFlightPose`, pure statics that the entity's overridable methods delegate to):
  - `wingBeat(float ageInTicks, float rate)` defaults to `Mth.sin(ageInTicks * rate)`;
  - `wingAmp(float amp)` defaults to `amp`;
  - `flightBank()` defaults to the current `clamp(wrap(yRot − yRotO) × 0.055, ±0.7)`;
  - `flightPitch(float beat)` defaults to the current `clamp(−dy × 0.55, ±0.45) + beat × 0.05`.

  `HungarianHorntailModel.applyFlight` calls these four instead of its inline maths, and a wild Horntail renders exactly as before. `MemoryHorntailEntity` returns the scene's `sin(wingPhase)` and `wingAmp`, and 0 for bank and pitch, because the renderer turns the whole body.
- **The snout:** after `setupAnim`, the renderer composes the head part's transform and stores the world snout point and direction on the puppet each frame (`MemoryHorntailEntity.snout()`), for Task 7's fire.
- **Python mirror, `build_scripts/pensieve_scenes/dragon_engine.py`** (imports `scene_engine`, edits nothing in it):
  - `horntail_cue(actor, t)`, `beat_rate`, `wing_amp`, `wing_phase(actor, t)`, `downstroke_ticks(actor, t0, t1)`;
  - `horntail_frame(scene, aid, t) -> (pos, yaw, pitch, roll)`;
  - `horntail_parts(scene, aid, t) -> [(name, capsule)]`: conservative capsules for the body, the neck, the head, four tail segments and each wing at its swept extent for the current amplitude, measured from `createBodyLayer`'s cube extents;
  - `horntail_snout(scene, aid, t) -> (point, dir)`, an approximation of the neck and head pose for the rest, `ROAR` and `BREATH` states.

  **Parity:** `horntail_cases.json` holds 40 cases (perched, a roar, a takeoff, a hover, a cruise, a climb, a glide, a banked turn, an air breath, the stagger). `DragonFlightTest.pythonMirrorAgrees` reads it, and `python -I dragon_engine.py --write-cases` rewrites it.
- [ ] **Step 1: Write the failing tests.**
  - `DragonFlightTest`:
    - `beatRateFollowsSpeed`: hover > cruise > glide, glide = 0;
    - `beatRateIsContinuous`: it changes by ≤ 0.01 rad/tick across a sweep of speed and climb;
    - `wingPhaseIsPureOfTheClock`: the same phase from any start;
    - `downstrokesAreSinMinima`;
    - `bankFollowsTurn`;
    - `bankIsContinuous`;
    - `pythonMirrorAgrees`.
  - `HorntailCuesTest`:
    - `overlayEndsReturnsToFlight`;
    - `landClearsFlyingAtItsEnd`;
    - `idleGrounds`;
    - `cueNamesIgnoreCase`;
    - `stateTickCountsFromCue`.
  - `HorntailFlightPoseTest.defaultsMatchTheOldMaths` (the four defaults against the old expressions over a grid of inputs).
  - `SceneValidatorTest`:
    - `horntailAnimNamesChecked`;
    - `rollAllowedOnHorntail`;
    - `splineAcceptedOnHorntailPath`.
  - `gametest/MemoryHorntailGameTests.discardsOnServer`: a `memory_horntail` made on the server is gone after one tick.
- [ ] **Step 2: Implement.**
  - Write the entity, the renderer, the hooks, the cue and flight classes, the `MemoryActors` driving, the mirror and the case file.
  - Write `run/pensieve_dev/dev_horntail.json` from `build_scripts/pensieve_scenes/dev_horntail.py`. It covers: perched idle; `ROAR`; a ground `BREATH_WINDUP` → `BREATH`; `TAKEOFF`; a banked circuit at hover, cruise and climb, then a glide; `AIR_BREATH`; a `BITE` in the air; and the `STAGGER` with a tumbling fall (look pitch and roll keyed).
  - In a `dev_` scene, the client log gets a `Pensieve horntail:` line every 5 s with the rendered snout against `horntail_snout` from the mirror (the dev script writes the mirror's points as a side file).
  - Document it in SKILL.md: the type, its frame, its cues, its wingbeats, its bank, and the snout.
- [ ] **Step 3: Test.** Run the full `./gradlew test` and `runGameTestServer` under the lock, and expect PASS. The controller then previews `dev_horntail` with no gradle in flight. The snout gap must be ≤ 0.5 throughout; if it is not, re-fit the mirror before the commit.
- [ ] **Step 4: Commit.** Message: `Add the Horntail to memories, flying on splines with wingbeats matched to her speed`.

### Task 7: Dragon fire (Opus; after Task 6) [gradle lane]

**Interfaces:**
- **Action `breathe`,** valid only on `memory_horntail`:
  - `{t, do: "breathe", target?, duration?}`;
  - `target` is `[x,y,z]` or `"actor:<id>"`, and without one the jet goes along the snout's direction;
  - `duration` is 10 to 80 ticks, default 40. It is a new action key, allowed only on `breathe`.
- **`DragonFire` (pure, common):**
  - `front(double age) -> double`: blocks, growing at 1.2 a tick up to `MAX_REACH` 18;
  - `strength(double age, int duration)`: up over 3 ticks, full, then guttering over the last 8;
  - `scorchRadius(double hitAge)`: 0 → 2.5 over 30 ticks of hit;
  - `smokeRate(double age)`.
- **The look** (`client/pensieve/DragonFireRenderer`), all deterministic from the scene clock and `EffectSeed(at = snout at t, t)`:
  - **The jet:** it leaves the puppet's stored snout each frame and is aimed at the target, cut at the first solid block on a ray through the set (client raycast against the memory's blocks).
  - **The core:** camera-facing flame ribbons, additive, white-gold in the core through orange to a dark-red edge, with a scrolling flicker texture (`generate_first_task_textures.py`, Task 11's script; until then a placeholder texture in this task).
  - **Particles:** flame and lava particles along it, at most 300 alive per jet.
  - **Smoke:** black smoke rolling off the front and rising.
  - **The heat shimmer:** faint translucent wavering ribbons above and around the jet. There is no post shader.
  - **The splash:** where the jet meets a surface, flame spreads along it.
  - **Scorch:** dark decal quads on the hit block faces within `scorchRadius`. They keep glowing embers for 3 s and stay until the scene ends. A late joiner sees the scorch already there.
  - Full-bright; it lights nothing.
- **Mirror,** in `dragon_engine.py`:
  - `fire_window(actor, t) -> (t0, t1) | None`: the ticks a `BREATH` or `AIR_BREATH` cue has the jaw open at full fire, measured from the model's breath curves;
  - `fire_jet(scene, aid, t, world) -> (origin, dir, length)`, using `scene_engine.shape_hit` for the cut.
- **Budget:** two jets plus their smoke in ≤ 1.0 ms a frame. The `Pensieve fire:` dev timer logs it every 5 s.
- [ ] **Step 1: Write the failing tests.**
  - `DragonFireTest`:
    - `frontGrowsAndCaps`;
    - `strengthGutters`;
    - `scorchGrowsWithHitTime`;
    - `deterministicAcrossOrigins`;
    - `particleCapHolds`.
  - `SceneValidatorTest`:
    - `breatheOnlyOnHorntail`;
    - `breatheDurationRange`;
    - `durationKeyOnlyOnBreathe`.
- [ ] **Step 2: Implement.**
  - Write the code and `run/pensieve_dev/dev_dragon_fire.json` from `dev_dragon_fire.py`: the Horntail perched breathing at a boulder (scorch), then in the air breathing at a roof, both in a day sky.
  - Add SKILL.md, and run the tests under the lock.
  - The controller previews it and sends the user a screenshot.
- [ ] **Step 3: Commit.** Message: `Let the memory Horntail breathe real fire`.

### Task 8: The white hand-over (Sonnet) [gradle lane]

**Interfaces:**
- Top-level `handover_colour`: `"black"` (the default) or `"white"`. It is valid only with `next`.
- With `"white"`, this scene's 20-tick chain fade goes to white, and the hold while the next set arrives stays white. The next scene's 20-tick fade-in comes from white (the client keeps the colour across the hand-over).
- `eyes_*` lids are still black.
- `PensieveTimeline.handoverColour(...)` is pure, and `PensieveOverlay` draws it.
- [ ] **Step 1: Write the failing tests.**
  - `PensieveTimelineTest`: `whiteHandoverFadesToWhite`, `whiteHoldsThroughTheBuild` and `nextSceneFadesInFromWhite`;
  - `SceneValidatorTest`: `handoverColourNames` and `handoverColourNeedsNext`.
- [ ] **Step 2: Implement.** Implement it, add SKILL.md, and extend `dev_chain.py` with a white pair. Run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Let a memory chain hand over through white`.

### Task 9: The trigger (Sonnet) [gradle lane]

**Interfaces:**
- **`pensieve/FirstTaskMemory.java`:**
  - **Constants:**
    - `TAG_SEEN = "FirstTaskMemorySeen"` and `TAG_PENDING = "FirstTaskMemoryPending"`, both in `PlayerPersisted`;
    - `SCENE = "first_task"`, `LAST = "first_task_egg"`;
    - `WINDOW_TICKS = 1200`, `RANGE = 64`, `DEATH_DELAY = 21` (vanilla's 20-tick death animation, then the removal).
  - `recordDamage(HungarianHorntailEntity dragon, ServerPlayer p, long gameTime)`:
    - It is fed from `LivingDamageEvent` (the Forge 51 class `AssignmentEvents` already uses), for the player `DamageSource.getEntity()` resolves to. So arrows and spells count for their shooter or caster.
    - A pet's damage does not count for its owner.
    - Never record for a `MemoryHorntailEntity`.
  - `onDeath(HungarianHorntailEntity dragon)`, from `LivingDeathEvent`:
    - For every recorded player within `WINDOW_TICKS`, in the same level, within `RANGE`, and without `TAG_SEEN`: set `TAG_PENDING`, and schedule `tryStart` in `DEATH_DELAY` ticks.
    - Drop the dragon's records.
    - The killer does not matter, so a wolf or fire kill counts for the damagers.
  - `boolean tryStart(ServerPlayer p)`:
    - It refuses (keeping pending) when the player is dead, a viewer, building, waking or in another memory's wake, or the scene is missing (logged once).
    - It refuses for good when `TAG_SEEN` is set, or `TAG_PENDING` is not.
    - On start: clear `TAG_PENDING`, then `PensieveSessions.startSolo(p, SCENE)`.
  - **Retries:** every 20 server ticks for online players with `TAG_PENDING`, and 100 ticks after a login.
  - **Seen:** a scene-enter listener on `LAST` marks the session, and the home listener sets `TAG_SEEN` only when that chain ended with `END` in `LAST` (the Flying ledger's note). A leave or a yank leaves it unseen, and the next kill plays it again.
  - There is no reward and no house gate: an unsorted player gets it too.
  - Records older than `WINDOW_TICKS` are pruned on each record and on death, so the map stays bounded.
- [ ] **Step 1: Write the failing gametests** in `FirstTaskMemoryGameTests`, with stand-in scenes under the shipped ids, as `FlyingMemoryGameTests` does:
  - who gets it: `killStartsAfterDeathAnimation` (not before tick 20, started by 22), `bothDamagersGetIt`, `damagerOver64BlocksDoesNot`, `damageOlderThan60sDoesNot`, `wolfKillCountsForDamagers`, `spellDamageCountsCaster`, `unsortedPlayerGetsIt`, `seenPlayerDoesNotReplay`;
  - waiting: `inMemoryWaitsThenStarts`, `wakeOfAnotherMemoryWaits`, `deadAtKillStartsAfterRespawn`, `logoutKeepsPendingStartsAfterLogin`;
  - endings: `endSetsSeen`, `leaveEarlyKeepsUnseenAndNotPending`, `yankKeepsUnseen`.
- [ ] **Step 2: Implement.** Add a SKILL.md story-trigger note, and run the full tests and gametests under the lock.
- [ ] **Step 3: Commit.** Message: `Play the First Task memory for the players who brought down a Horntail`.

### Task 10: Share the generator kit (Sonnet) [after Flying T10]

**Interfaces:**
- **Produces** `build_scripts/pensieve_scenes/scene_kit.py`, moved out of `flying_scene.py` with the same behaviour:
  - the builders: `Keys`, `Channel`, `Rig`, `look_keys`, `limb`, `bend_pose`;
  - `Sc`, now `Sc(sid, title, length_s, world, sfx_prefix, lines, clips, registered)`. `sfx()` and `say()` check against the passed tables instead of the Flying globals;
  - the hands and eyes: `parts_of`, `is_riding`, `hand`, `hand_corners`, `solve_arm`, `reach`, `head_eye`, `mouth`, `broom_point`, `broom_under`;
  - the camera helpers: `smoothed`, `slerp_dir`, `move`, `const`, `ramp`, `turn_look`, `eye_fn`, `first_person`;
  - the checks: `check_clearance` (with an `extra_boxes(scene, t)` hook, which Task 13 feeds the Horntail capsules), `check_camera_air`, `check_contacts`, `check_items`, `check_rides`, `check_fliers`, `check_lines`, `check_keys`, `check_walkers`, `check_framing`, `check_void`.
- `flying_scene.py` imports them, and its output is unchanged.
- [ ] **Step 1: Write the failing test.** `test_scene_kit.py` (plain asserts, run with `python -I`):
  - `flying_outputs_unchanged`: regenerating every `flying_*.json` gives the committed md5s;
  - `sc_takes_any_prefix`: an `Sc` with a `first_task_` prefix accepts its own sfx and rejects a `flying_` one.
- [ ] **Step 2: Run it.** Expect FAIL: there is no `scene_kit` module.
- [ ] **Step 3: Implement.** Move the code and point `flying_scene.py` at it.
- [ ] **Step 4: Run.** Expect PASS, with byte-identical Flying JSON.
- [ ] **Step 5: Commit.** Message: `Share the scene generator kit between memories`.

### Task 11: The Firebolt, the golden egg, the champion robes and the Triwizard crowd (Sonnet) [after Flying T10; gradle lane]

**Interfaces:**
- **`hogwartsmagic:memory_broom_firebolt`:** a vehicle, BroomModelV2's `FIREBOLT_LAYER` with `broom_firebolt_v2.png`.
  - Add it to `SceneNames.VEHICLES` and `ROLL_TYPES`, and to `scene_engine.BROOM_TYPES` and `BROOM_BOX` (measured from the layer).
  - The seat follows `BroomFlight.seat`.
  - Its riders use the same rider layer. `BroomFlightTest.handsOnHandle` and `check_rider_grip` cover its handle radius at rider scale 0.85.
  - Add 6 Firebolt cases to `broom_cases.json`.
- **`hogwartsmagic:memory_golden_egg`:** an actor.
  - The `path` is the egg centre. It is 0.36 long and 0.26 across at `scale` 1, gold with etched bands.
  - A glint flashes every 1.5–2.5 s (`GoldenEgg.glint(t, id)`, pure: a star sprite at the top).
  - It takes no roll.
  - Mirror: `scene_engine.egg_box(scale)`.
- **Items** (display-only: no recipe, in no creative tab):
  - `hogwartsmagic:golden_egg_prop`: its held display size equals the actor at the holder's scale (`GoldenEggTest.itemMatchesTheActorSize`, the Remembrall pattern).
  - `hogwartsmagic:triwizard_robes`: a chest robe made with `createRobeMaterial`, charcoal with bronze trim. It is house-neutral.
- **Crowd style `triwizard`:** robes from the four houses (70%), Beauxbatons powder blue (15%) and Durmstrang blood red with brown fur (15%). It is added to the validator's style list. Seat layout is unaffected, so `crowd_cases.json` gains one style case only if the mirror reads the style.
- **Textures:** `build_scripts/generate_first_task_textures.py` (Pillow) writes the egg, the prop, the robes and the fire flicker strip for Task 7.
- [ ] **Step 1: Write the failing tests:**
  - `BroomFlightTest.fireboltHandsOnHandle`;
  - `GoldenEggTest.glintPeriod` and `GoldenEggTest.itemMatchesTheActorSize`;
  - `CrowdTest.triwizardStyleMixesSchools` (in 2,000 seats, each school's share lies within ±4% of its target);
  - validator tests that the new types and the style parse.
- [ ] **Step 2: Implement.** Write a dev scene `dev_first_task_props.json` (a Firebolt circuit with a rider, the egg glinting on a rock, a `triwizard` crowd bowl), and add SKILL.md. Run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Add the Firebolt, the golden egg and the champion robes to memories`.

### Task 12: Register the First Task sounds (Sonnet) [after Flying T10 and Tasks 3–5; gradle lane]

- [ ] **Step 1: Register.**
  - Register every `first_task_*` sfx, voice (the muffled call included) and music cue in `sounds.json` and `ModSounds`, as `pensieve.sfx.*`, `pensieve.voice.*` and `pensieve.music.*`. Music gets `"stream": true`.
  - Add the subtitle keys to `lang/en_us.json`.
  - Check that every entry resolves to a file, and every file has an entry.
- [ ] **Step 2: Build.** Run `SceneDataTest` and `runServer` under the lock, and expect PASS with no missing-sound warnings in the log.
- [ ] **Step 3: Commit.** Message: `Register the First Task memory sounds`.

### Task 13: The scenes (Opus, cinematic) [after Tasks 1–12]

**Consumes:**
- Tasks 1–2: `ANCHORS`, `SCHEMATIC` and `FILLS`.
- Task 6: the Horntail and `dragon_engine`.
- Task 7: `breathe` and `fire_jet`.
- Task 8: `handover_colour`.
- Task 10: the kit.
- Task 11: the Firebolt, the egg, the robes and the `triwizard` crowd.
- Task 12: the sound ids, and `first_task_clip_lengths.json`.

**Produces:** `build_scripts/pensieve_scenes/first_task_scene.py`, which writes `first_task.json`, `first_task_arena.json`, `first_task_chase.json` and `first_task_egg.json`.

**Beats** (the spec's; about 115 s in all):
- **`first_task`, 16 s, the tent set:**
  - `eyes_open` at t 0, dim and warm;
  - `first_task_tent_crowd` loops;
  - two `first_task_ground_thud`s, each with the lanterns swinging (`memory_block` pendulums keyed off the thud) and dust falling from `dust_boxes`;
  - the three other champions as unnamed, silent figures in the dim back of the tent, never in focus;
  - about 9 s: the cannon (muffled) and `first_task_comm_call_muffled`;
  - the viewer stands and walks to the flap, with `light_rays` pouring in;
  - `handover_colour: "white"`.
- **`first_task_arena`, 36 s, the grounds, `sky` `overcast`:**
  - **The reveal:** out of the white, a wide crane over the ravine and the packed stands (`crowd` on every stand row, style `triwizard`, ≤ 4,000, with `first_task_crowd_bed` looping) down to the nest. The golden egg glints. The Horntail is perched, with her chain to the stake.
  - **The roar:** `first_task_comm_horntail`; she rears and `ROAR`s; the crowd goes quiet (`sit` plus `first_task_crowd_hush`).
  - **The fire:** her `BREATH_WINDUP` → `BREATH` at the viewer, who runs and crouches behind `hide_rock`. The jet scorches the rock face, and a `gasp` follows.
  - **The summon:** first person, the wand raised, "Accio Firebolt!" and `first_task_accio`.
  - **The Firebolt:** it streaks in along `broom_lane` over a stand (a `cheer`) and into the raised hand (contact ≤ 0.15), with `first_task_comm_accio`.
  - **The mount:** she breathes again and scorches the rock behind them as they mount (first person at the mount, then out) and climb out over the nest.
- **`first_task_chase`, 47 s, the grounds, `fog_far` 220:**
  - **The pass:** the viewer dives past her and she `BITE`s, a near miss (`first_task_horntail_snap`, a `gasp`).
  - **The launch:** she strains, and the chain snaps (`memory_block` `minecraft:chain` links on a catenary from her neck to the stake whip and fall). `first_task_comm_chain`, then `TAKEOFF` and the chase.
  - **Out of the arena:** along `launch_lane`, with `first_task_wind_high` looping and speed-matched wingbeats with wingbeat sfx on close shots.
  - **Over Hogwarts:** across `castle_route`, with two `AIR_BREATH`s that scorch the roofs behind the viewer, and her tail smashing `slate_roofs` (cells to air, tumbling `memory_block` slates, `first_task_slate_smash`).
  - **The bridge:** they weave under the bridge, and she hits it. `STAGGER`; the `break_box` cells turn to air with debris arcs and dust; `first_task_bridge_crash`; and she tumbles down `fall_column` out of sight.
  - **Alone:** the viewer, alone in the sky, turns back toward the arena.
- **`first_task_egg`, 16 s, the grounds:**
  - **The dive:** the loop back over the stands (a `cheer` rising), and the dive at the nest.
  - **The grab:** first person, the hand closes on the golden egg (`hold hogwartsmagic:golden_egg_prop`, the actor parked on the same tick, `first_task_egg_grab`).
  - **The triumph:** the `erupt` within 0.3 s; the cannon with smoke from both `cannon_spots`; `first_task_comm_egg`; the viewer raises the egg to the stands.
  - **The wake:** `eyes_close`, then `end_action: "wake"`.

**Build budget:** the tent scene's set is ≤ 100,000 blocks. The other three use `first_task_grounds`, each built as a standby during the scene before, which lasts at least 2 × its build ticks (assert 16).

- [ ] **Step 1: Shot list.** Write the shot list first, in the report and as the generator's docstring: shot, time, framing, move, what happens. Check it against the beats and lengths.
- [ ] **Step 2: Generator asserts.**
  - all 17 Global Constraints asserts, over all four scenes;
  - `crowd_actor_gap` ≥ 0.7;
  - the chain links stay attached to the neck point (≤ 0.1, from `dragon_engine`) until the snap tick;
  - the Firebolt is in frame (16:9) for at least 1 s of its streak in;
  - the golden egg is in frame for at least 0.8 s before the dive begins.
- [ ] **Step 3: Java checks.** Add these to `SceneDataTest`:
  - `firstTaskChainIsWired`: all four exist, the `next` wiring is right, the tent has `handover_colour` `"white"`, and the egg scene ends `wake`;
  - `devHorntailSceneValidates`, `devDragonFireSceneValidates` and `devFirstTaskPropsSceneValidates`, each when its dev scene exists.
- [ ] **Step 4: Music.**
  - Run `first_task_music.py points` and retime its `CUES` so the arena cue's hit lands on the roar, the chase cue's on the chain snap and the crash, and the egg cue's on the grab.
  - Rerun the cuts, and keep the 2 s overlaps across the hand-overs.
  - Re-register only if a file name changed.
- [ ] **Step 5: Test.**
  - Run `python -I build_scripts/pensieve_scenes/first_task_scene.py` twice; the output must be deterministic.
  - Run the full `./gradlew test` and `runGameTestServer` under the lock. Expect PASS.
- [ ] **Step 6: Commit.** Two commits: `Score the First Task memory` (the retimed music), then `Stage the First Task memory` (the generator, the four scenes and the tests).

### Task 14 (controller): review and the user's in-game pass

- [ ] **Step 1: Final review.** Run an Opus final review of the whole range against this plan and the spec, then a Sonnet fix wave and a scoped re-review.
- [ ] **Step 2: Ask the user.** Ask the deferred questions (Task 0) in one round: the voice picks, the lines and the decisions list. Regenerate whatever they change.
- [ ] **Step 3: In-game pass.** The user summons a Horntail (`SummonHorntailCommand`), kills it with a friend nearby if they can, and watches all four scenes before waking where they stood.
  - Grep `run/logs/debug.log` for `Pensieve clock` across the tent → arena hand-over.
  - Note the frame rate in the arena, and in the chase over the castle.
  - Gather their notes for a feedback round.
