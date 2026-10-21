# The Flying Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first time a sorted player mounts a broom, a ~165 s film memory plays: their first flying lesson (Hooch, "Up!", Neville's fall, Malfoy and the Remembrall, the diving catch, McGonagall at the window), then their first match as Seeker in a storm before packed, roaring stands beside Hogwarts, ending on the Snitch held up to the crowd. They wake back on their broom.

**Architecture:**
- **Four chained scenes per house** (the generator writes all four chains, 16 files): `flying_<h>` (the lesson, ~40 s) → `flying_<h>_chase` (the Remembrall, ~35 s) → `flying_<h>_match` (walk-out to the dive, ~50 s) → `flying_<h>_snitch` (the dive and the catch, ~40 s, `end_action: "wake"`). `<h>` is the viewer's house. The opponent is `rival(h)`: Slytherin, or Gryffindor when `h` is Slytherin. Per-house files need no engine placeholder work, and each validates as it ships.
- **Two sets built by Python:**
  - `flying_lawn` is the castle lawn with McGonagall's window, the Mini Hogwarts on the skyline. It is small, so it builds fast at the memory's opening.
  - `quidditch_pitch` is cut from the user's world save, with the Mini Hogwarts beside it as a `schematics` entry. It is built as the chain's standby while the lesson plays.
- **New engine:**
  - shared Python mirrors of the engine;
  - a memory broom with riders, mount windows, its own flight rule and spline actor tracks;
  - the Quidditch balls and the Remembrall;
  - a storm sky;
  - a batched stadium crowd.
- **Trigger:** `FlyingMemory`, hooked into `BroomEntity.interact`. It starts once per player, after the Sorting. The broom is parked while they are away and remounted when they get home.

**Tech Stack:** Forge 1.21 (51.0.33), Java 21, JUnit 5, Forge gametests, Python 3 generators (stdlib only, plus Pillow for skins), Fish Audio TTS, ElevenLabs sound generation, ffmpeg.

**Spec:** `docs/superpowers/specs/2026-10-21-flying-memory-design.md`. Read it with this plan. The Letter memory's records are the precedent: `docs/superpowers/plans/2026-10-21-letter-memory.md` and `.superpowers/sdd/2026-10-21-letter-memory/` (`progress.md`, `task-7-report.md`, `pass1-*.md`).

**Precondition:** the pass-1 clock fix must be committed before Task 1 starts. That fix is the asymmetric slew with `SNAP_BACK_TICKS` 40 in `SmoothedSceneClock`, and it is uncommitted in the working tree as this plan is written. The match's standby build (about 300k blocks) is exactly the server-lag case it fixes.

## Global Constraints

Every task's requirements include these.

**Code and build**
- **Server/client safety:** nothing in `pensieve/`, `sorting/`, `network/`, `entity/` or `item/` imports `net.minecraft.client.*` or `com.hogwartsmagic.client.*`. New entity types register in common code, and their renderers register client-side only. Authoritative actions (start, park, remount, flags) are server-side.
- **Client code:** no `switch` on an enum (an `int` switch is fine). Never use entity event bytes 21, 35 or 63.
- **Gradle:**
  - Never run it while a Minecraft dev client runs (`tasklist | findstr java`; the daemon is fine).
  - Take the lock first: `mkdir .superpowers/gradle.lock` (if it exists, wait 20 s and retry). `rmdir` it afterwards, even on failure.
  - Other agents work in this checkout.

**Scenes**
- **Limits:** each scene JSON is at most 900,000 characters and under 1,000,000 UTF-8 bytes. Radius is at most 200, unless Task 6 proves the pitch cannot fit (then Task 6b). Follow `.claude/skills/writing-pensieve-memories/SKILL.md`. The shot list comes first.
- **Lines:**
  - No spoken line contains the player's name. The viewer is "you" or "the Seeker", and the name appears only as `{viewer}` text.
  - Dialogue is original apart from short nods ("Up!", "Nobody moves!").
  - Every spoken line has a subtitle with its measured clip length.
- **Scales:** first-years 0.75, match players (third-years) 0.85, adults 1.0. The viewer is a boy (`skin.viewer`) wearing `hogwartsmagic:<h>_robes` in `chest`.
- **First person** only for three moments: the broom leaping into the hand, the Remembrall catch and the Snitch catch.
- **Sets:**
  - `physics_problems` must be empty.
  - Every set built at a scene's opening is at most 100,000 blocks (25 ticks at 4,000 per tick).
  - Bigger sets are built only as a chain standby. The scene before one lasts at least 2 × its build ticks.

**Generator asserts.** These are the Letter lessons, and they run on every generation of every scene. Each one prints its worst number. Each must be shown to bite once by a mutation, noted in the report.
1. **Engine mirrors only.** Every camera, look, eye, pose, rider and broom position is computed with the shared mirrors in `scene_engine.py` (Task 1), never with a private copy.
2. **Every moving camera uses spline keys.** A segment whose position or look target moves more than 0.05 blocks is either part of a run of `"ease": "spline"` keys (ended with an `in_out` key to settle), or a lone `in_out` move of at most two keys. Dense `linear` camera keys fail.
3. **Smoothness per frame, across every shot of every scene.** Sample at 60 fps (1/3 tick) through `engine_cam`/`engine_look`, leaving out frame pairs across a cut. The limits:

   | metric | limit |
   |---|---|
   | `a` (change of velocity) | ≤ 0.002 blocks/frame |
   | `aw` (change of turn rate) | ≤ 0.08 deg/frame |
   | `ja` (spike in `a`) | ≤ 0.0003 |
   | `jw` (spike in `aw`) | ≤ 0.01 |

   A camera that looks at an actor reads that actor's eye through `actor_eye_height` (EyeEase), or through a broom rider's frame.
4. **Clearance per tick.**
   - The camera is sampled 4 times a tick against every player actor's six part boxes. Each part is posed from the emitted JSON: rest or crouch, the held-item raise, the pose cue, look pitch, limbs in add or set mode, and the rider's broom frame (yaw, pitch and roll).
   - It is also sampled against every broom and ball box.
   - It must stay at least 0.15 away, unless no point of the part is inside the frustum (aspect 2.0). The first-person head is the one allowed exception.
   - Camera in air: every key, and the real spline path between keys in steps of 0.08, stays clear of block shapes by 0.06.
5. **Hand contacts recomputed from the emitted JSON**, the way `ActorPoser.applyLimbs` and vanilla `HumanoidModel` pose them:
   - add mode puts rotation and offset on top of vanilla; set mode replaces rotation and position;
   - include `ArmPose.ITEM`'s 18° raise, the crouch (+0.4 rad, arm pivot y 5.2, model lowered 2/16 per scale) and the rider's frame;
   - check at the `bobModelPart` sway corners (zRot 0 and 0.1 rad, xRot ±0.05) and at `life_arm_corners`.

   It applies to every contact: the broom into the hand, the mount, Malfoy's snatch, the throw's release, the Remembrall catch, every Quaffle catch and throw, every Beater's hit, Hooch's whistle and the Snitch catch. The gap must be ≤ 0.15 at every corner.
6. **Held items are compensated.** Every main-hand change between empty and held on an add-mode arm is balanced by `item_raise_steps`. The hand jumps under 0.01 at every item tick. Hand motion in a first-person close-up is ≤ 0.03 per tick.
7. **Actions end when their moment ends.** Each motion is tied to the event that causes it, asserted from the emitted JSON:
   - a classmate's broom twitches only between their "Up!" and their broom's rest or success;
   - Neville's flailing stops by the tick he falls off;
   - Malfoy's taunting and hovering end when the viewer reaches him;
   - nobody on the lawn leaves the ground while Hooch is away, apart from Malfoy and the viewer;
   - a crowd `cheer`/`goal` reaction starts within 0.5 s after a goal (the Quaffle through a hoop ring);
   - a `gasp` comes within 1 s of a near miss (a Bludger within 1.5 of a player);
   - the `erupt` starts within 0.3 s of the Snitch catch tick;
   - every `sfx` cue for a crack, a Quaffle catch, a whistle or a whoosh lands within 1 tick of its contact or pass;
   - nobody cheers or gasps without a cause in the window before it;
   - the rival Seeker stops diving once he pulls out.
8. **Fliers.**
   - A broom path stays ≥ 0.5 from every block, except at the brooms lying on the lawn, a mount and a landing, and ≥ 1.2 from every other flier.
   - Broom speed changes by ≤ 0.12 blocks/tick² everywhere. The scripted exceptions are listed by name with a reason (Neville's bucking, a Bludger hit).
   - In the dive, the lowest broom point stays ≥ 0.6 above the grass, and the rider's leg boxes ≥ 0.1 above it.
9. **Ride windows are continuous.** At a mount or dismount tick, the world position of the rider's root is continuous within 0.02. The path is bracketed with a key at `T-0.001` in the old frame and one at `T` in the new.
10. **Size and wiring.**
    - Sizes are under the caps, ids are unique and key times sorted.
    - Every `next` exists, the four chains are complete, and each `*_snitch` scene ends `wake` on shut lids.
    - Every chain's first scene opens on `eyes_open` at t 0.
    - No `words` text contains a name placeholder other than `{viewer}`.
    - Two runs give byte-identical output (md5).

**Git and assets**
- **Git:**
  - Never `git stash`, `git reset`, `git checkout -- <file>`, `git add -A` or `git add .`. Stage by explicit path.
  - Never stage `.claude/settings.local.json`, `.claude/skills/rendering-minecraft-structures/textured/viewer.html`, `memories/lake_patronus.json`, `build.gradle`, `logs/` or `build_scripts/pensieve_scenes/source/quidditch_pitch_v2/`.
  - End every commit with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Implementers never push; the controller pushes after each commit (the user wants every commit pushed).
- **Keys:** `C:\Users\alonl\fish_key.txt` and `C:\Users\alonl\eleven_key.txt` are read from file, never printed or committed.
- **Untrusted data:**
  - The pitch world, any downloaded skin and the user's zip are data. Read them with `python -I` scripts that live outside their folders, and pass their paths as arguments.
  - The zip stays untouched.
  - The pitch and the music are for private use only (licence unknown), like the Great Hall.
- **Shell:** Bash heredocs break on any apostrophe in the body, so write files with the Write tool. Never dispatch subagents from inside a task.

## Review Focus

1. **Back on the broom.** After the wake, or after any early leave, the player is mounted on the same broom where they left it. Meanwhile it has not fallen, drifted, been taken by another player or despawned. If the broom is gone, the player stands where they mounted, with nothing lost. Covered by Task 11's `wakeRemountsBroom`, `leaveEarlyRemountsAndKeepsUnseen`, `parkedBroomHoldsStill`, `otherPlayerCannotTakeParkedBroom` and `missingBroomStandsPlayer`.
2. **Every exit from every scene** (X, death, disconnect, yank) releases the broom and leaves the seen flag unset, and nothing of the memory remains. Covered by `deathReleasesBroom` and `disconnectReleasesBroom`, plus the leave and yank tests.
3. **Houses.** Each of the four houses gets its own chain with the right opponent (a Slytherin viewer plays Gryffindor). An unsorted player just mounts. A mob or a Death Eater mounting a broom never triggers anything. Covered by Task 10's `SceneDataTest.everyHouseHasAFlyingChain` and `slytherinFacesGryffindor`, and Task 11's `unsortedMountDoesNotStart` and `wizardMountNeverStarts`.
4. **Frame cost in the storm:** the crowd, rain, storm sky, 14 fliers and the castle at 60 fps. Covered by Task 5's crowd budget test, the frame-time readout in the dev scenes, and the in-game check in Task 12.
5. **A slow server during the pitch's standby build** (about 300k blocks while `flying_<h>_chase` plays): no clock rewind, and no hitch at the hand-over into the match. Covered by the precondition, Task 10's build-budget assert, and a timed check in Task 12 (log `Pensieve clock` lines).

---

## File map

All paths are under `src/main/java/com/hogwartsmagic/` (Java) or `src/main/resources/{assets,data}/hogwartsmagic/` unless they start with `build_scripts/`.

| File | Task | Responsibility |
|---|---|---|
| `build_scripts/pensieve_scenes/scene_engine.py` (new), `letter_scene.py` | 1 | The shared engine mirrors |
| `pensieve/scene/BroomFlight.java`, `pensieve/scene/Track.java`, `entity/MemoryBroomEntity.java`, `client/MemoryBroomRenderer.java`, `client/pensieve/MemoryActors.java`, `ActorPoser`, `SceneDef`/`SceneParser`/`SceneValidator`/`SceneNames`, `scene_engine.py`, `build_scripts/pensieve_scenes/broom_cases.json`, `dev_brooms.py` | 2 | Brooms, riders, ride windows, spline actor tracks |
| `entity/MemoryBallEntity.java`, `client/MemoryBallRenderer.java`, `client/GoldenSnitchRenderer.java` | 3 | Quaffle, Bludger, Remembrall, the Snitch's wing blur |
| `pensieve/scene/SkyDome.java`, `client/pensieve/SkyRenderer.java`, `SceneNames` | 4 | The storm sky; rain over a big set |
| `pensieve/scene/Crowd.java`, `client/pensieve/CrowdRenderer.java`, validator, `dev_crowd.py` | 5 | The stadium crowd |
| `build_scripts/pensieve_scenes/quidditch_pitch_set.py`, `anvil.py`, `structure/quidditch_pitch.nbt`, `.gitignore` | 6 | The pitch beside Hogwarts |
| `build_scripts/pensieve_scenes/flying_lawn.py`, `structure/flying_lawn.nbt` | 7 | The lesson lawn and McGonagall's window |
| `build_scripts/sounds/flying_sfx.py`, `sounds/pensieve/sfx/flying_*.ogg` | 8 | Sound effects |
| `build_scripts/sounds/flying_voices.py`, `flying_clip_lengths.json`, `flying_music.py`, `sounds/pensieve/{voice,music}/flying_*.ogg`, `sounds.json`, `ModSounds.java` | 9 | Voices and music |
| `build_scripts/pensieve_scenes/flying_scene.py`, `memories/flying_*.json` (16), `SceneDataTest` | 10 | The scenes |
| `pensieve/FlyingMemory.java` (new), `entity/BroomEntity.java`, `gametest/FlyingMemoryGameTests.java`, SKILL.md | 11 | The trigger |
| `textures/memory_skin/*.png`, `_skins.json`, `generate_memory_skins.py` | 0 | Skins |

---

### Task 0 (controller, with the user): assets and decisions

- [ ] **Step 1: Skins.**
  - The user approves NovaSkin picks, downloaded with their OK per file:
    - `hooch` (short spiky grey hair, yellow hawk eyes, black-and-white referee robes);
    - `malfoy_young` (slick white-blond hair, Slytherin robes);
    - `neville_young` (round face, Gryffindor robes);
    - `wood` (Gryffindor Quidditch kit), only if a captain line is kept;
    - four Quidditch kits, `quidditch_<h>_01..06` (house-coloured robes over pads, 6 faces each);
    - `quidditch_rival_seeker_<h>`.
  - Students, first-years and `mcgonagall` already ship.
  - Credit each post in `generate_memory_skins.py`, list the names in `_skins.json`, and fill any see-through holes (the Vernon lesson).
- [ ] **Step 2: Voices.**
  - Generate samples for Hooch, Malfoy (young) and Neville (young) from Fish searches, three each, into `run/voice_samples/flying/`, and the user picks one per character.
  - McGonagall reuses `d625beac29e2417faba8ace4c3d6ccdd`.
  - Ask the user:
    - whether the viewer speaks his "Up!" (with Harry young, `67ccf061bf3b4f9c8b635addc77eba6f`) or stays silent;
    - whether to add a captain (Wood) and a match commentator;
  - Show the line table (Task 9) for approval.
- [ ] **Step 3: Music.** Ask the user for the tracks: "The Norwegian Ridgeback and a Change of Season" or "Hedwig's Theme" for the lesson, and "Quidditch, Third Year" for the match. They go in `run/music/flying/`, private use only.
- [ ] **Step 4: Commit.** Run `SceneDataTest` under the lock and expect PASS. Commit with the message `Add the Flying memory skins`.

### Task 1: Share the engine mirrors (Sonnet)

**Interfaces:**
- **Produces** `build_scripts/pensieve_scenes/scene_engine.py`, moved out of `letter_scene.py` with the same signatures:
  - the camera: `engine_blend`, `engine_spline`, `engine_cam`, `engine_look`, `camera_samples`, `camera_frames`, `camera_motion`, `check_camera_smoothness`, `check_camera_clearance`, `rekey`, `turn_between`, `in_view`, `view_angle`;
  - posing and hands: `actor_eye_height`, `posed_parts`, `EquipTimeline_item`, `emitted_arm`, `item_raise_steps`, `life_arm_corners`, `part_corners`, `inside_part`, `box_distance`, `box_surface`, `sample_keys`;
  - blocks: `shape_hit`, `in_air`.

  Plus one new function, `check_spline_moves(sid, scene, problems)` (assert 2).
- **Consumes** nothing new.
- [ ] **Step 1: Write the failing test.** `build_scripts/pensieve_scenes/test_scene_engine.py` (plain asserts, run with `python -I`) has three checks:
  - `letter_outputs_unchanged`: regenerating the four letter JSONs gives the committed md5s;
  - `spline_moves_flags_linear_track`: a 6-key linear moving camera yields one problem, and the same keys as spline yield none;
  - `wand_sample_cam_matches`: `wand_scene.sample_cam` on a spline track equals `engine_cam` (pass-1 minor 4).
- [ ] **Step 2: Run it.** Expect FAIL: no `scene_engine` module.
- [ ] **Step 3: Implement.**
  - Move the functions and point `letter_scene.py` and `wand_scene.sample_cam` at them.
  - Fix `posed_parts` so a `stand` ends the crouch (pass-1 concern).
  - Add `check_spline_moves`.
- [ ] **Step 4: Run.** Run the test and `python -I build_scripts/pensieve_scenes/letter_scene.py`, and expect PASS with byte-identical letter JSON.
- [ ] **Step 5: Commit.** Message: `Share the memory engine mirrors between scene generators`.

### Task 2: Memory brooms, riders and spline actor tracks (Opus)

**Interfaces:**
- **Actor types:**
  - `hogwartsmagic:memory_broom` is the school broom: BroomModelV2's training layer and texture, at `scale` 1 as the real broom.
  - `hogwartsmagic:memory_broom_racing` is the Nimbus layer.
  - **Frame:** the broom-local frame is x left, y up, z toward the handle's tip. The origin is the seat point on the handle, so `path` is where a rider's hips sit.
  - **Orientation:** `look` takes `yaw`, `pitch` (nose down positive, as for heads) and `roll` (bank, right side down positive). `roll` is now allowed on the broom types.
  - **Without `look`:** yaw follows the heading, pitch follows the climb angle (clamped ±70), and roll is the auto-bank `BroomFlight.bank(path, t)`.
- **`BroomFlight` (pure, common).** Every function samples the path with the spline rule below.
  - `bank(Track path, double t) -> double`: −k × (yaw rate × speed), ≤ 45°, from the path over ±3 ticks, continuous.
  - `hoverBob(double speed, double t, long seed) -> double`: 0.04 amplitude at 0.9 Hz, fading to 0 by 0.1 blocks/tick. A broom bobs only while it carries a rider.
  - `riderPose(double speed, double pitch) -> float[][]`, the base rider layer below poses, clips and limbs:
    - legs `[-70, ±6, 0]`;
    - arms on the handle `[-65, ±8, 0]`;
    - the body leaning forward 8° + 22° × clamp(speed/0.6, 0, 1), plus a tuck to 40° when pitch > 30 (a dive), with the head lifted to hold the gaze.
  - `SEAT` gives the rider offset `[0, -0.5722 × riderScale + 0.06, -0.05]` (seat top 0.06 above the handle axis).
  - `frame(...)` returns the yaw × pitch × roll matrix that riders use.
- **Riders:**
  - `ride` takes a string (the whole scene, as before) or an object `{"on": "<vehicle id>", "from": t?, "until": t?}`.
  - Vehicles are `memory_rowboat`, `memory_broom` and `memory_broom_racing`.
  - Inside the window, the rider's `path` is an offset in the vehicle frame and `look` yaw is relative. Outside it, both are world space.
  - On a broom, the rider takes the broom's full yaw × pitch × roll about the seat, gets `riderPose` and the seated life layer, and never walks.
  - **Validator rules:** windows are inside `0..length` with `from < until`; the vehicle exists and is a vehicle; the vehicle does not itself ride; a vehicle carries at most 2 riders at any time.
- **Flight rule (the owls' `Airborne`, for brooms):** a ridden broom is flying above 0.025 blocks/tick and landed under 0.0125, with hysteresis. A landed rider gets a standing life layer, so a rider whose broom hovers still looks as if he sits, not runs.
- **Spline actor tracks:**
  - `"ease": "spline"` is accepted on any actor's `path` and `look` (not on `limbs`).
  - `Track.sample` uses the same Hermite as `CameraTrack.spline`: inner velocity `(h1×d0 + h0×d1)/(h0+h1)`, eased neighbour slopes, natural ends.
  - Gait, `Airborne`, `BroomFlight`, `RoughSea.bind` and `LetterFlutter` all read the splined position.
- **Python mirrors** in `scene_engine.py`:
  - `track_sample(keys, t)` (spline-aware);
  - `broom_frame(scene, broom_id, t) -> (pos, yaw, pitch, roll)`;
  - `rider_root(scene, rider, t) -> (root, matrix)`;
  - `posed_parts` and `emitted_arm` composing the rider frame.

  Parity: `broom_cases.json` holds 40 cases (straight, turning, climbing, diving, hovering, a mount window). `BroomFlightTest.pythonMirrorAgrees` reads them, the way `human_motion_cases.json` is read, and `python -I scene_engine.py --write-broom-cases` rewrites them.
- [ ] **Step 1: Write the failing tests.**
  - `BroomFlightTest`:
    - `bankFollowsTurn`: bank is 0 on a straight line, has the turn's sign, and is ≤ 45;
    - `bankIsContinuous`;
    - `riderLeansWithSpeed`;
    - `diveTucks`;
    - `hoverBobOnlyWhenSlowAndRidden`;
    - `seatLandsOnHandle`: a rider's hip pivot lies within 0.01 of the seat point at any yaw, pitch and roll;
    - `pythonMirrorAgrees`.
  - `TrackTest`: `splineMatchesCameraTrack` (1e-6 against `CameraTrack.spline` on the same keys) and `splineIsC1`.
  - `SceneValidatorTest`:
    - `rideWindowParsed`, `rideWindowOverlapRejected`, `rideOnNonVehicleRejected`;
    - `brooms3RidersRejected`;
    - `rollAllowedOnBroom`, `rollStillRejectedOnPlayer`;
    - `splineRejectedOnLimbs`.
  - `MemoryActorsRideTest` (pure helper): `riderOutsideWindowIsWorldSpace` and `windowEdgeIsContinuousWhenBracketed`.
- [ ] **Step 2: Implement.**
  - Write the entity, the renderer (lerped look, roll, bob) and the parser and validator changes.
  - Generalise `Rowboat.rider` into a `Vehicle` seam that `MemoryActors` asks.
  - Add `ActorPoser`'s rider layer, the mirrors and the case file.
  - Write `run/pensieve_dev/dev_brooms.json` from `build_scripts/pensieve_scenes/dev_brooms.py`, with:
    - a lying broom that leaps into a standing boy's hand;
    - the mount;
    - a banked circuit;
    - a dive and pull-out;
    - two riders racing;
    - a hover with bob.
  - Document it all in SKILL.md (the broom types, riders, windows, spline actor tracks).
- [ ] **Step 3: Test.** Run the full `./gradlew test` and `runGameTestServer` under the lock, and expect PASS. `SceneDataTest` validates the dev scene when it exists.
- [ ] **Step 4: Commit.** Message: `Add memory brooms with riders and spline actor paths`.

### Task 3: The Quidditch balls and the Remembrall (Sonnet; after Task 2)

**Interfaces:**
- **`hogwartsmagic:memory_quaffle`:** a scuffed red leather ball, diameter 0.55 at `scale` 1, with seams. It rolls about its travel axis in proportion to path speed.
- **`hogwartsmagic:memory_bludger`:** an iron-black ball, diameter 0.45, dented, with a faint vibration when moving fast.
- **`hogwartsmagic:memory_remembrall`:** a glass ball, diameter 0.22, with white smoke swirling inside on a clock from the actor id. There is no red state: the lesson never needs it, so it needs no new per-actor key.
- **Common to the three:**
  - The `path` is the ball centre.
  - The renderer is batched and full-bright off.
  - Each type has a `MemoryBallEntity` subtype.
  - Mirrors: `scene_engine.ball_box(type, scale)`.
- **`hogwartsmagic:golden_snitch`** already works as an actor. When it is a memory puppet, its renderer adds a translucent wing-blur disc per wing (the beat is too fast to read). Its wings keep their own flutter.
- [ ] **Step 1: Write the failing tests.**
  - `MemoryBallTest`: `rollFollowsDistance` (the roll angle equals distance / radius) and `stillBallDoesNotRoll`;
  - validator tests that the three types parse;
  - `SceneDataTest` knows the types.
- [ ] **Step 2: Implement.**
  - Add the types and renderers, plus a dev scene `dev_balls.json` (a throw, a catch into a hand, a Bludger pass, a Snitch darting) and a SKILL.md section.
  - Run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Add the Quidditch balls and the Remembrall to memories`.

### Task 4: A storm sky, and rain over a pitch (Sonnet)

**Interfaces:**
- **`sky` style `"storm"`:**
  - Slate horizon `#3A4048` to a near-black zenith `#14171C`.
  - Three fast, low, torn cloud layers drift with the scene wind (the `rain` event's wind when one runs).
  - The fog and clear colour come from the horizon colour.
  - **Flashes:** the dome and the clouds flash with every `lightning` effect in the scene. `SkyDome.flash(double t, double[] strikeTicks) -> double` is pure: two or three flickers over 1.3 s, peaking at 0.85, like `CeilingSky`.
  - Thunder stays with `lightning`.
- **Rain:** `MAX_RAIN_SPREAD` goes from 100 to 200, so one box covers the pitch (drops are still drawn only within 26 blocks of the camera).
- [ ] **Step 1: Write the failing tests.**
  - `SkyDomeTest`: `stormGradient` (horizon and zenith colours), `flashPeaksAfterStrike`, `flashIsZeroWithoutLightning` and `flashFadesBy1_3s`;
  - validator tests: `skyStormAccepted` and `rainSpread200Accepted` (201 rejected).
- [ ] **Step 2: Implement.**
  - Implement both, with a dev scene `dev_storm_sky.json` (an open field, a storm sky, rain, lightning every 6–10 s) and SKILL.md.
  - Run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Give memories a storm sky that flashes with the lightning`.

### Task 5: The stadium crowd (Opus)

**Interfaces:**
- **Effect `crowd`:**
  - **Keys:**
    - `at` is the point the crowd watches (the pitch centre).
    - `rows` is a new event key, allowed only on `crowd`: a list of seat-top segments `[x1, y1, z1, x2, y2, z2]`, scene-relative, inside the radius.
    - `style` is `gryffindor`, `hufflepuff`, `ravenclaw`, `slytherin` or `mixed`: robe, scarf and flag colours.
    - `intensity` (0.1–1) is the share of seats filled.
    - `count` is a cap.
    - `duration` 0 means until the scene ends.
  - **Seats:** one spectator per 0.8–1.0 along each row, jittered.
  - **Figures:** low-poly people (head, torso, two arms, legs folded), from a palette of skin tones and hair colours. There are scarves and some flags or banners, from a deterministic seed (`EffectSeed`).
  - **Idle:** seated with a sway, the head following the play (they look at the camera's `look_at` point, smoothed), and some scarf waving.
- **Effect `crowd_react`:**
  - **Keys:** `at` (the centre of attention), `spread` (the box of spectators who react), `style` (`cheer`, `goal`, `gasp`, `erupt`, `chant`, `sit`) and `duration`.
  - **Timing:** each spectator reacts after its own delay, 0–0.6 s, scaled by its distance from `at`.
  - **The reactions:**
    - `goal` and `cheer`: stand, with arms and flags up;
    - `gasp`: hands to heads for 1 s, then sit;
    - `erupt`: everyone up, jumping, arms thrown up, for at least 3 s;
    - `chant`: a 2 Hz arm pump in phase per stand;
    - `sit`: back down.
  - The pure maths lives in `Crowd`: seat layout, state at `t`, pose per spectator.
- **Budget:**
  - up to 4,000 spectators a scene;
  - LOD: full figures within 40 blocks, 2-box figures to 120, then a flat textured strip;
  - one batched pass with no entities;
  - the mesh is rebuilt only for pose changes;
  - at most 1.5 ms per frame at 3,000 spectators on the dev machine, logged by the dev scene's debug timer.
- **Mirror:** `scene_engine.crowd_seats(event) -> list[(pos, yaw)]`, so the generator can keep real actors off crowd seats (assert: every real actor ≥ 0.7 from every crowd seat).
- [ ] **Step 1: Write the failing tests.**
  - `CrowdTest`:
    - `seatsFollowRows`, `seatsDeterministic`, `fillShare` and `pythonMirrorAgrees` (`crowd_cases.json`);
    - `reactionStaggers`, `gaspIsShort`, `eruptStandsMost` (≥ 80% standing between 1 s and 3 s after it) and `idleNeverStands`;
    - `budgetCapsVertices`.
  - Validator: `rowsOnlyOnCrowd`, `rowsInsideRadius`, `crowdStyleNames` and `crowdCap4000`.
- [ ] **Step 2: Implement.**
  - Implement it, with a dev scene `dev_crowd.json` (a 3,000-seat bowl, then a goal, a gasp and an erupt) and a frame-time debug line.
  - Look at it in a preview (the controller, with the client: see Gradle) and send the user a screenshot.
  - Add SKILL.md, and run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Fill memory stands with a cheering crowd`.

### Task 6: The Quidditch pitch beside Hogwarts (Opus, design-heavy)

**Source:**
- Copy `C:/Users/alonl/AppData/Local/Temp/claude/C--Users-alonl-OneDrive-Desktop-harry-potter-mod/eca4c2f1-9bf3-4c65-b86d-13bbb709117a/scratchpad/quidditch_zip/Quidditch Pitch V2/` (already unpacked from the untouched zip; if it is gone, unzip `C:\Users\alonl\Downloads\quidditch-pitch-v2.zip` into a new empty folder) to `build_scripts/pensieve_scenes/source/quidditch_pitch_v2/`.
- Add that folder to `.gitignore`. It is about 80 MB of regions, so unlike the 13 KB `great_hall.schem` it is never committed.
- **Recon numbers:**
  - level.dat DataVersion 3955 (1.21.1); write 3953;
  - the built pitch is world x −395..−210, y −44..33, z −989..−702: 186 × 78 × 288, 98,063 non-terrain blocks;
  - the materials are lime and green wool, concrete in light grey, yellow, blue, red, black and green, and spruce;
  - the surrounding terrain is grass and dirt.

**Interfaces:**
- **Produces:** `build_scripts/pensieve_scenes/anvil.py` and `quidditch_pitch_set.py`.
  - `anvil.py` is a stdlib Anvil reader: `read_region_box(world_dir, x0, y0, z0, x1, y1, z1) -> dict[(x, y, z), state]`. It uses zlib and `scene_common.read_nbt` and walks the palettes and bit-packed longs (1.16+ packing, no spanning).
  - `quidditch_pitch_set.py` has `build(world) -> ANCHORS`, `write_nbt()`, `TEMPLATE_AT`, `RADIUS` and `SCHEMATIC` (the Mini Hogwarts `{id, at, rotation}` entry Task 10 copies into the set), plus the template `quidditch_pitch.nbt`.
- **The set:**
  - the pitch as built, centred on the origin, with the field's grass y the scene's walking y 0;
  - enough of its own terrain to sit on, closed underneath;
  - hoops checked or rebuilt as three gold rings per end;
  - house drapes or banners on the stand towers;
  - light blocks so actors read in the storm.
- **Hogwarts:** the Mini Hogwarts (96 × 109 × 145, about 36,400 blocks, its own hill) goes beside and behind one long stand. Its rotation and position fit the footprint inside ±200, with ground fills joining it to the pitch, so it reads as one landscape and never floats.
- **`ANCHORS`:**
  - `pitch_centre`, `grass_y`;
  - `hoops` (6 × {centre, normal yaw, radius});
  - `stands` (a list of {house, rows: seat-top segments});
  - `close_rows` (rows by the touchline for real actors);
  - `team_gates` (2);
  - `hooch_spot`, `ball_crate`, `commentator_box`, `teachers_box`;
  - `castle_view` (a camera spot framing the castle over the stands);
  - `rain_box`;
  - `dive_column` (an all-air column 70 tall to the grass, away from stands and hoops).
- [ ] **Step 1: Build.**
  - Write the reader, the set script and the `.nbt`.
  - **Asserts:**
    - `physics_problems` empty;
    - every anchor on solid ground with air above, and every seat row on solid seat tops with 2 air above;
    - each hoop ring open (air at its centre ±1 in its plane);
    - `dive_column` all air;
    - nothing cropped by the radius (the count of cells beyond 200 is 0);
    - the template plus the schematic at most 350,000 blocks.
  - Render previews with `rendering-minecraft-structures`: the wide view with the castle, a stand, the hoops, and field level looking at the castle. Look at them, iterate, and send the user the wide view and the stand.
  - If the pitch, stands and castle cannot fit ±200 without trimming the pitch, stop and report: Task 6b raises the cap.
- [ ] **Step 2: Commit.** Commit the scripts, the `.nbt` and `.gitignore`. Message: `Cut the Quidditch pitch out of the supplied world for the Flying memory`.

### Task 6b (only if Task 6 reports no fit): a larger scene radius (Sonnet)

- [ ] **Step 1: Write the failing tests.** `SceneValidatorTest.radius256Accepted` (257 rejected) and a gametest `bigRadiusSetBuildsAndClears`.
- [ ] **Step 2: Implement.** Raise the cap to 256 in `SceneValidator`, `PensieveSessions.MAX_RADIUS` and SKILL.md. `RegionAllocator.SPACING` 1024 still exceeds 2 × 256. Run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Allow memory sets up to radius 256`.

### Task 7: The lesson lawn and McGonagall's window (Opus, design-heavy)

**Interfaces:**
- **Produces:** `flying_lawn.py` with `build(world) -> ANCHORS`, `write_nbt()`, `TEMPLATE_AT`, `RADIUS` and `SCHEMATIC`, and the template `flying_lawn.nbt` (≤ 60,000 blocks, so that with the Mini Hogwarts the opening build stays ≤ 100,000).
- **The set:**
  - A bright, sloping castle lawn on the grounds.
  - A full-scale castle flank on one side: a stone wall and a tower with McGonagall's lit window (a real-scale bay with a sill she stands behind, its glass seen from the lawn).
  - The Mini Hogwarts beyond, on its hill as the skyline, with the forced perspective checked in the previews.
  - Open sky over a chase volume.
- **`ANCHORS`:**
  - `broom_line` (two facing rows of 10 {broom spot, broom yaw, student spot on its left});
  - `hooch_spot`, `viewer_spot` (in a row);
  - `neville_fall` (grass), `hospital_exit` (where Hooch leads Neville out of frame);
  - `remembrall_drop`;
  - `chase_box` (an air box at least 50 × 30 × 50);
  - `catch_spot` (grass under the dive);
  - `mcg_window` ({her standing spot, window centre, outward yaw});
  - `lawn_y`, `castle_view`.
- [ ] **Step 1: Build.**
  - **Asserts:** physics clean; anchors on solid ground with air; `chase_box` all air; the window glass between `mcg_window` and the lawn.
  - Previews: the lawn with the brooms laid out, the window from the lawn, the skyline. Send the user the lawn and the window.
- [ ] **Step 2: Commit.** Message: `Build the castle lawn for the Flying memory`.

### Task 8: Sound effects (Sonnet; any time)

ElevenLabs takes, mastered per `reference_elevenlabs_sfx`: envelope-trimmed so the transient opens the clip, RMS-matched to −18 dBFS, limited at 0.65, input seeking in both passes. Prompts ask for the event, loud and dense.

| id | what | length |
|---|---|---|
| `flying_lawn_air` | a breezy afternoon, wind in grass, distant birds | 20 s loop |
| `flying_broom_up` | a broom smacking up into a palm | 0.6 s |
| `flying_broom_whoosh` | a broom tearing past a listener | 1.2 s |
| `flying_broom_buck` | a broom rattling and bucking | 2 s |
| `flying_thud` | a boy landing hard on turf | 0.8 s |
| `flying_whistle` | a referee's long whistle blast | 1.5 s |
| `flying_crate` | a heavy wooden crate's lid thrown open, chains rattling | 1.5 s |
| `flying_crowd_bed` | a packed stadium roar under heavy rain | 20 s loop |
| `flying_crowd_swell` | a stadium crowd rising into a cheer | 4 s |
| `flying_crowd_erupt` | a stadium erupting into a huge victory roar | 8 s |
| `flying_crowd_chant` | a stadium chanting rhythmically, clapping | 8 s |
| `flying_crowd_gasp` | a stadium gasping as one | 2 s |
| `flying_rain` | heavy driving rain and gusting wind | 20 s loop |
| `flying_thunder` | a close thunderclap rolling away | 5 s |
| `flying_bludger_crack` | a wooden bat cracking a heavy iron ball | 0.6 s |
| `flying_quaffle_catch` | a leather ball slapped into gloved hands | 0.4 s |
| `flying_snitch_flutter` | tiny metallic wings buzzing | 2 s loop |

- [ ] **Step 1: Generate.**
  - Write `build_scripts/sounds/flying_sfx.py`, with a `WINDOWS` table of measured cut points in its docstring.
  - Register the sounds in `sounds.json` and `ModSounds`, then check that every entry resolves and every subtitle key exists.
- [ ] **Step 2: Build.** Run `SceneDataTest` and `runServer` under the lock.
- [ ] **Step 3: Commit.** Message: `Add the Flying memory sound effects`.

### Task 9: Voices and music (Sonnet; after Task 0)

**Lines.** The user approves them in Task 0. They are original and contain no player name. The final table goes in `flying_voices.py`, and measured lengths go in `flying_clip_lengths.json`.

| id | Speaker | Line |
|---|---|---|
| `flying_hooch_welcome` | Hooch | "Good afternoon, class. Don't stand there gawping, find a broom." |
| `flying_hooch_left` | Hooch | "Stand on the left of it. Right hand out, flat, over the handle." |
| `flying_hooch_up` | Hooch | "Now, firmly, and mean it: say 'Up!'" |
| `flying_class_up` | first-years | "Up!" (a ragged chorus, several takes layered) |
| `flying_viewer_up` | viewer (if kept) | "Up!" |
| `flying_neville_whoa` | Neville | "It's going on its own! I can't stop it, help!" |
| `flying_hooch_nobody` | Hooch | "Nobody moves! Feet on the grass until I'm back, or you'll answer for it." |
| `flying_malfoy_look` | Malfoy | "Look what the clumsy idiot dropped." |
| `flying_malfoy_come` | Malfoy | "Want it? Come up and take it, then." |
| `flying_malfoy_catch` | Malfoy | "Go on, then. Fetch!" |
| `flying_mcg_follow` | McGonagall | "You. With me. Now." (at the window or on the lawn, after the catch) |
| `flying_hooch_clean` | Hooch | "I want a clean game, from all of you." |
| `flying_captain` | Wood (if kept) | "Just catch the Snitch. That's all you've got to do." |
| commentator lines | (if kept) | "And they're off!", "Goal!", "The Seeker's seen something!" |

- **Music:** register the user's tracks as `pensieve.music.flying_*`. Cut points are chosen from loudness envelopes in `flying_music.py`, with fades baked in and a −1.5 dBTP limiter, as `letter_music.py` does. The cut points depend on Task 10's beat timings, so the cutting is finished inside Task 10.
- [ ] **Step 1: Generate.** Generate everything (the Fish pipeline: silence trim, loudnorm −16/−1.5, mono 44.1k Vorbis), register it, and check that it resolves.
- [ ] **Step 2: Build.** Run `SceneDataTest` and `runServer` under the lock.
- [ ] **Step 3: Commit.** Message: `Add the Flying memory voices`.

### Task 10: The scenes (Opus, cinematic)

**Consumes:** everything above: the Task 2–5 types and effects, the Task 6–7 `ANCHORS` and `SCHEMATIC`, the Task 8–9 sound ids and clip lengths, and `scene_engine`.

**Produces:** `build_scripts/pensieve_scenes/flying_scene.py`, which writes `flying_<h>.json`, `flying_<h>_chase.json`, `flying_<h>_match.json` and `flying_<h>_snitch.json` for `h` in gryffindor, hufflepuff, ravenclaw and slytherin, with `rival(h)`. The chains differ only in robes, kits, crowd styles, banners and which stand roars. The staging, timings and cameras are shared.

**Beats** (the spec's; about 165 s in all):
- **`flying_<h>`, about 40 s, on the lawn under a `day` sky:**
  - opens on `eyes_open` at t 0;
  - an establishing crane over the castle lawn;
  - Hooch's lesson;
  - the "Up!" chorus: brooms twitch, roll or smack a boy's face;
  - first person, the viewer's broom leaps into his hand on the first try;
  - the mount;
  - Neville's broom bolts, bucks and drops him;
  - Hooch: "Nobody moves!", and she leads him away.
- **`flying_<h>_chase`, about 35 s, on the lawn:**
  - Malfoy picks up the Remembrall and flies off;
  - the viewer follows on his broom, banking through `chase_box`;
  - Malfoy throws it;
  - the dive, then first person, the catch just above the grass;
  - McGonagall at the window.
- **`flying_<h>_match`, about 50 s, at the pitch:**
  - the storm sky, rain over the pitch box, lightning every 6–12 s over the towers;
  - the fly-out to a roaring crowd: `crowd` on every stand row, a `goal` on the entrance;
  - Hooch's whistle and the crate: the Bludgers and the Snitch are released, and the Quaffle is thrown up;
  - Chasers pass and score: the Quaffle passes through a ring, then `goal` reactions in that house's stands;
  - Bludgers whip past the camera, and a Beater cracks one;
  - a near miss brings a `gasp`;
  - the viewer circles high, hunting, with the castle lit through the rain.
- **`flying_<h>_snitch`, about 40 s:**
  - the glint;
  - the dive side by side with the rival Seeker down `dive_column`;
  - the rival pulls out first;
  - the viewer pulls out just above the grass;
  - first person, his hand closes on the Snitch (`hold hogwartsmagic:golden_snitch` with item compensation, the actor parked on the same tick);
  - `erupt`, and he raises it to the stands;
  - `eyes_close`, then `end_action: "wake"`.

**Build budget:**
- The lawn scenes' sets are at most 100,000 blocks: `flying_lawn` plus the Mini Hogwarts.
- `flying_<h>_match` and `flying_<h>_snitch` use the pitch set. They are built as standbys during `flying_<h>_chase` and `flying_<h>_match`, each of which lasts at least 2 × the build ticks at 4,000 per tick (asserted).

- [ ] **Step 1: Shot list.** Write the shot list first, in the report and as the generator's docstring: shot, time, framing, move, what happens. Check it against the beats and lengths.
- [ ] **Step 2: Generator asserts.**
  - all ten Global Constraints asserts, over every scene of every house;
  - the Task 5 crowd-seat exclusion (≥ 0.7);
  - every lawn broom lies on the grass (broom box bottom within 0.05 of `lawn_y`) until it moves;
  - the Remembrall's path is continuous from Neville's pocket drop to Malfoy's hand, the throw and the catch, at most 0.15 from each hand at contact (assert 5);
  - the Quaffle passes inside each hoop ring it scores through, ≥ 0.3 from the rim;
  - the Snitch is in frame (16:9) for at least 0.6 s before the dive begins;
  - the build-budget assert.
- [ ] **Step 3: Java checks.** Add `SceneDataTest` tests:
  - `everyHouseHasAFlyingChain`: all 16 exist, the chain wiring is right, and each ends `wake`;
  - `slytherinFacesGryffindor`: the Slytherin chain's rival kits are Gryffindor, and every other chain's are Slytherin.
- [ ] **Step 4: Music.** Cut and register the music to the beats (`flying_music.py`), with music cues overlapping about 2 s across chain hand-overs.
- [ ] **Step 5: Test.**
  - Run `python -I build_scripts/pensieve_scenes/flying_scene.py` twice; it must be deterministic.
  - Run the full `./gradlew test` and `runGameTestServer` under the lock. Expect PASS.
- [ ] **Step 6: Commit.** Two commits: `Score the Flying memory` (the music), then `Stage the Flying memory` (the generator, the 16 scenes and the tests).

### Task 11: The trigger (Sonnet)

**Interfaces:**
- `pensieve/FlyingMemory.java`:
  - `static final String TAG_SEEN = "FlyingMemorySeen"` (in `PlayerPersisted`, as `LetterMemory` stores its flag);
  - `static String sceneFor(HogwartsHouse h) -> "flying_" + h.name().toLowerCase()`, and `SNITCH_SUFFIX = "_snitch"`;
  - `boolean tryStart(ServerPlayer player, BroomEntity broom)`: true if it started;
  - `register()`: the home listener, plus the death and logout hooks.
- **The hook:** `BroomEntity.interact`, on the server, after `startRiding` returns true, schedules `FlyingMemory.tryStart` for the next server tick. It runs only for a real `ServerPlayer` (not a `FakePlayer`). Mobs mount with `startRiding(broom, true)`, which never passes through `interact`.
- **`tryStart` refuses** (returns false, and the player stays mounted) when:
  - the player is no longer riding that broom;
  - `TAG_SEEN` is set;
  - `HousePerks.getHouse` is null;
  - the player is a viewer, or a memory is building or waking;
  - the scene is missing (log once).
- **On start:**
  - Record the broom UUID and dimension.
  - Call `broom.holdFor(player.getUUID())`, a new server-only BroomEntity state: no gravity, zero motion, `canAddPassenger` false for anyone else, and not saved.
  - Then `PensieveSessions.startSolo(player, sceneFor(h))`. Its `stopRiding` dismounts the player.
- **Home (any exit of a chain begun by `tryStart`):**
  - After `WAKE_DONE_TICKS` when the chain ended in a wake, otherwise at once: if the broom exists in the player's level within 64 blocks, the player starts riding it. Either way, the hold is released.
  - `TAG_SEEN` is set only when the chain ended with `END` in `sceneFor(h) + SNITCH_SUFFIX`.
  - **Death:** release the broom; no remount.
  - **Logout:** release the broom.
- [ ] **Step 1: Write the failing gametests** in `FlyingMemoryGameTests`, using stand-in scenes under the shipped ids, as `LetterMemoryGameTests` does:
  - starting: `firstMountStartsMemory`, `unsortedMountDoesNotStart`, `seenPlayerJustMounts`, `wizardMountNeverStarts`;
  - the parked broom: `parkedBroomHoldsStill`, `otherPlayerCannotTakeParkedBroom`;
  - coming home: `wakeRemountsBroom`, `leaveEarlyRemountsAndKeepsUnseen`, `yankKeepsUnseen`, `missingBroomStandsPlayer`;
  - exits: `deathReleasesBroom`, `disconnectReleasesBroom`;
  - re-entry: `secondMountWhileInMemoryIgnored`.
- [ ] **Step 2: Implement.** Add a SKILL.md story-trigger note, and run the full tests and gametests under the lock.
- [ ] **Step 3: Commit.** Message: `Play the Flying memory the first time a sorted player mounts a broom`.

### Task 12 (controller): review and the user's in-game pass

- [ ] **Step 1: Final review.** Run an Opus final review of the whole range against this plan and the spec, then a Sonnet fix wave, and a scoped re-review.
- [ ] **Step 2: In-game pass.** The user watches on their own sorted save: mount a broom, watch all four scenes, wake on the broom.
  - Grep `run/logs/debug.log` for `Pensieve clock` across the hand-over into the match.
  - Note the frame rate in the storm.
  - Gather their notes for a feedback round.
