# The Letter Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first time a new player opens their Hogwarts acceptance letter, a ~170 s film memory plays: the cupboard, the letters, the chimney flood, the stormy crossing, Hagrid crashing into the hut and handing the boy the letter. Then the player wakes in the real world as the letter screen unfolds.

**Architecture:**
- **Four chained scenes:** `letter` (the cupboard, first letter, more letters) → `letter_flood` (Sunday, the chimney) → `letter_crossing` (the storm at sea) → `letter_hut` (Hagrid, ending `end_action: "wake"`).
- **Two sets** built by Python: Privet Drive, and the sea with the rock and hut.
- **New engine:**
  - a rough-sea effect and a rocking rowing-boat actor;
  - a falling door leaf;
  - fixes to the parked `memory_letter` and `letter_flood`.
- **Trigger:** a server hook on `HogwartsInviteItem.use` starts the memory once for eligible players. A home listener opens the letter screen after the wake.

**Tech Stack:** Forge 1.21 (51.0.33), Java 21, JUnit 5, Forge gametests, Python 3 generators, Fish Audio TTS, ElevenLabs sound generation, ffmpeg.

**Spec:** `docs/superpowers/specs/2026-10-20-letter-and-wand-memories-design.md` ("The Letter Memory"). This supersedes `docs/superpowers/plans/2026-10-20-sorting-memory-stage2.md`. Its Tasks 3–4 (set brief, beats) are reference material here, and its done Tasks 1–2 (`memory_letter` 260dd31, `letter_flood` ce110d6) are reused.

## Global Constraints

- **Server/client safety:** nothing in `pensieve/`, `sorting/`, `network/`, `entity/` or `item/` imports `net.minecraft.client.*` or `com.hogwartsmagic.client.*`.
- **Client code:** no `switch` on an enum in client code. Avoid entity event bytes 21, 35 and 63.
- **Gradle:** never while a Minecraft dev client runs (`tasklist | findstr java`; the daemon is fine). Use the lock: `mkdir .superpowers/gradle.lock` before any gradle command (if it exists, wait 20 s and retry), and `rmdir` it after.
- **Scenes:**
  - Each scene JSON is at most 900,000 characters. The radius is at most 200.
  - Follow `.claude/skills/writing-pensieve-memories/SKILL.md`:
    - shot list first;
    - generator `build_scripts/pensieve_scenes/letter_scene.py`;
    - `physics_problems` clean;
    - collision, leg-box and camera-in-air asserts (reuse `great_hall_scene.py`'s helpers by import or by extracting them into `scene_common.py`);
    - subtitles on every spoken line with the real clip length;
    - human-motion clips instead of hand-keyed limbs.
- **Lines:** no spoken line contains the player's name. The boy is addressed as "boy" or "you". The name appears only as `{viewer}` text. Dialogue is original apart from short nods ("No post on Sundays!", "You're a wizard").
- **Scales:** the viewer and Dudley are 0.75, adults 1.0, Hagrid 1.5.
- **First person** only for the eyes opening, the hand taking the letter, and the eyes closing.
- **Git:**
  - Never `git stash`, `git reset` or `git checkout -- <file>`.
  - Never stage `.claude/settings.local.json`, `.claude/skills/rendering-minecraft-structures/textured/viewer.html` or `memories/lake_patronus.json`; use explicit `git add` paths.
  - End every commit with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  - Never push.
- **Keys:** `C:\Users\alonl\fish_key.txt` and `C:\Users\alonl\eleven_key.txt` are read from file, never printed or committed.
- **Downloads and music:** downloads need the user's approval per file. The music is user-supplied and private-use only.

## Review Focus

1. **Leaving early**, from any of the four scenes (X, death, disconnect, yank): the letter screen still opens when the player is home and able, the pending tag stays set so it replays next time, and nothing from the memory remains. Covered by Task 8's gametests `leaveEarlyOpensLetterAndKeepsPending` and `yankKeepsPending`.
2. **Old saves never get it on their own:** a player without `LetterMemoryPending` opens the letter normally. Covered by Task 8's `oldSaveOpensLetterDirectly`.
3. **Double use:** right-clicking the letter again while the memory is pending or building must not start a second memory or open the screen underneath. Covered by Task 8's `secondUseWhileInMemoryIgnored`.
4. **The flood never leaves the living room:** no letter crosses a wall, and none remain after leaving. Task 1 re-checks `letter_flood`'s box against the real room; the in-game check confirms.
5. **The boat on rough water stays on the water:** the boat's hull and its seated actors never sink below or float above the wave surface by more than 0.15. Covered by Task 2's `boatRidesTheSurface`.

---

## File map

| File | Task | Responsibility |
|---|---|---|
| `entity/MemoryLetterEntity.java`, `client/MemoryLetterRenderer.java`, `pensieve/scene/LetterFlutter.java`, `pensieve/scene/LetterFlood.java`, `client/pensieve/EffectVisuals.java` | 1 | The review fixes |
| `pensieve/scene/RoughSea.java`, `client/pensieve/RoughSeaRenderer.java`, `entity/MemoryRowboatEntity.java`, `client/MemoryRowboatRenderer.java`, `SceneNames`, `SceneValidator` | 2 | Storm sea, rain and boat |
| `pensieve/scene/DoorHinge.java`, `client/MemoryDoorRenderer.java` | 3 | Door leaf falling (pitch about the bottom edge) |
| `build_scripts/pensieve_scenes/privet_drive.py`, `structure/privet_drive.nbt` | 4 | The house and street |
| `build_scripts/pensieve_scenes/letter_island.py`, `structure/letter_island.nbt` | 5 | The rock, the hut, the boat landing |
| `sounds/pensieve/{voice,sfx}/letter_*.ogg`, `sounds.json`, `ModSounds.java`, `build_scripts/sounds/letter_*.py` | 6 | Voices, sound effects, music |
| `build_scripts/pensieve_scenes/letter_scene.py`, `memories/letter*.json` | 7 | The four scenes |
| `item/HogwartsInviteItem.java`, `event/ModEvents.java` (`spawnFirstYearInvite`), `pensieve/LetterMemory.java` (new), `gametest/LetterMemoryGameTests.java` | 8 | The trigger |
| `textures/memory_skin/{vernon,petunia,dudley}.png`, `_skins.json`, `generate_memory_skins.py` | 0 | Skins |

---

### Task 0 (controller, with the user): assets

- [ ] **Step 1: Skins.** Copy the approved `vernon.png`, `petunia.png` and `dudley.png` from the 2026-10-20 scratchpad `skins_dl/` (or re-download the approved NovaSkin ids 1aisrt9qman, 2740043036 and 4202223350 with the user's OK if it's gone).
  - Touch-ups: Petunia's hair recoloured blonde; Dudley blond with a striped jumper.
  - Credit the posts in `generate_memory_skins.py` and add the names to `_skins.json`.
- [ ] **Step 2: Voices.**
  - The user picks Vernon and Petunia from `run/voice_samples/stage2/`.
  - Generate a Hagrid sample with `f6fd51174e2743db91c4e91fe5de4ec1` for the user to OK. If it doesn't fit, search Fish for a gruff giant and let the user pick.
  - Show the user the line table (Task 6) for approval.
- [ ] **Step 3: Music.** Ask the user for the music: "Visit to the Zoo and Letters from Hogwarts", plus a storm or Hagrid cue.
- [ ] **Step 4: Commit.** Run `SceneDataTest` and expect PASS. Commit with the message `Add the Dursley memory skins`.

### Task 1: The letter fixes (Sonnet; Opus review of the flood first)

**Interfaces:** they stay as built. **The pitch convention changes:** after this task, `look` pitch 90 lays a letter flat in both the renderer and `LetterFlood` (the SKILL.md convention), and pitch 0 stands it upright.

- [ ] **Step 1: Opus review.** The controller dispatches an Opus review of ce110d6 (`letter_flood`) and appends its findings to this task.
- [ ] **Step 2: Write the failing tests.**
  - `LetterFlutterTest.seedStableAcrossReplays`: the default seed comes from a stable value, not the global entity counter.
  - `LetterFloodTest.restingLettersLieFlat`: resting pitch is 90.
  - Plus any review findings that need tests.
- [ ] **Step 3: Apply the 260dd31 review list:**
  - build the quad in the XY plane;
  - use `entityCutout` with two faces wound outward, and the back-face U mirrored;
  - interpolate rotation (the render `yaw` argument, a lerp of `xRotO`);
  - scale flutter by speed (`clamp(speed/0.05, 0, 1)`);
  - a stable default seed (`MemoryActors` seeds it from the actor id);
  - `getBoundingBoxForCulling` inflated by 0.3;
  - a downscaled envelope texture (128 × 84, UV 0..1);
  - the `entity.hogwartsmagic.memory_letter` lang key (already added in b2d0569; verify);
  - the SKILL.md note on the speed threshold.
  
  Then the flood review fixes (Opus review of ce110d6, 2026-10-21):
  1. **Box can cross a wall (high).** `LetterFlood.java:69-82` centres the box on `at`, which `MemoryEvents.position` turns into a block corner, so the box sits up to 1 block into the far wall and 0.5 into a side wall. Add an explicit `box` key (min/max corners, from `living_room_box`) and clamp to it; `at` is only the emitter. Tests: a SceneDataTest check that the flood box is all air in the set; a `LetterFlood` test with an off-centre box.
  2. **Corners poke through (medium).** `MARGIN` 0.15 (`LetterFlood.java:36`) is less than the envelope half-diagonal of about 0.27. Make it at least 0.3, as a constant shared with the renderer; test the corners, not just the centres.
  3. **Swarm changes per replay (medium).** `floodSeed` (`EffectVisuals.java:255`) hashes the world-space `at`, which includes the session slot origin. Seed from the scene-relative `e.at()` + `t` and simulate relative to `at`. The same fix applies to `enchanted_ceiling`, `light_rays`, `floating_candles` and `wand_choice`. Test: the same result at different origins.
  4. **Runs on client ticks (low-medium).** With `MemoryEvents.java:123` passing `elapsed` 0 when up to 5 ticks late, late joiners stay behind. In `Flood.tick`, step until `sim.age()` reaches `floor(sceneTime - t) + 1`, with a catch-up cap, through a pure helper with a test.
  5. **Any yaw accepted (low).** `SceneValidator.java:267`: reject flood yaws that are not multiples of 90, with a validator test.
  6. **Test gaps (low).** Add sim tests with 300 and 400 letters. Base the flutter phase on `sim.age() - emitTick[i]`, not `tickCount` (`MemoryLetterRenderer.java:42`). Cleanup after exit (`liveFloodLetters()`) is checked in game.
- [ ] **Step 4:** Run `./gradlew test` under the lock and expect PASS. Commit with the message `Fix the memory letter and the letter flood from review`.

### Task 2: Storm sea, rain and the rowing boat (Opus)

**Interfaces:**
- **Pure maths:** `RoughSea.height(double x, double z, double t, long seed) -> double` gives the surface height offset in blocks (a sum of 3–4 directional swells plus a chop; amplitude about 0.6 at `intensity` 1). `RoughSea.normal(...) -> double[3]`.
- **Effect `rough_sea`:**
  - keys `at` (the centre at the still-water surface), `spread` (`[hx, 0, hz]`), `intensity` (0.1–2), and `duration` (0 means until the scene ends);
  - draws an animated wave surface over the area: dark grey-green, specular-ish highlights, white foam on the crests;
  - spray particles on crests;
  - the set's water blocks under it are the deep body.
- **Effect `rain`:** keys `at`, `spread` (`[hx, hy, hz]`) and `intensity`. Slanted, wind-driven rain streaks in that box with splashes on surfaces. Client particles or a batched streak renderer; the builder decides.
- **Actor `hogwartsmagic:memory_rowboat`:**
  - a small wooden rowing boat model, about 4 blocks long, scale-able;
  - its `path` is the still-water position, and the client adds `RoughSea` heave, pitch and roll from the sea at that point (the sea's params come from the scene's `rough_sea` event; document how they bind, for example the nearest `rough_sea`);
  - it carries seated passengers: actors whose root follows the boat via a new actor key `"ride": "<boat actor id>"` with an offset, so passengers heave with it.
- [ ] **Step 1: Write the failing tests.**
  - `RoughSeaTest`: bounded height, deterministic, continuous in t and space, intensity scales the amplitude.
  - `boatRidesTheSurface`: the hull's sampled corners stay within 0.15 of the surface.
  - `riderFollowsBoat`.
  - Validator tests for `rough_sea`, `rain` and `ride`.
- [ ] **Step 2: Implement.** Add a dev scene `run/pensieve_dev/dev_storm.json`. Update SKILL.md. Run the full test suite and gametests under the lock.
- [ ] **Step 3: Commit** with the message `Add a stormy sea, rain and a rocking rowing boat to memories`.

### Task 3: The door crashing in (Sonnet)

**Interfaces:** for `memory_door` and `memory_door_mirrored`, `look` pitch tips the leaf about its bottom edge on the free side. Pitch 0 means upright, and −90 means fallen flat inward (toward the side the leaf opens). Combine it with yaw so the hinge can break: the scene keys the yaw, then the pitch.

- [ ] **Step 1: Write the failing tests** in `DoorHingeTest`:
  - `fallsAboutBottomEdge`: the bottom edge stays put at pitch −90, and the top edge lands at floor level, a leaf-height away;
  - `pitchAndYawCompose`.
- [ ] **Step 2: Implement.** Implement it, update SKILL.md, and run the tests under the lock.
- [ ] **Step 3: Commit** with the message `Let memory doors fall flat`.

### Task 3b: A daytime sky (Sonnet; after Task 2, which also edits EffectVisuals)

Added 2026-10-21 from the Task 4 review: the memory biome's sky is black (`memory_void.json` sky_color 0), so a morning Privet Drive reads as night. Diagon Alley (`time_of_day` 4000) has the same problem.

**Interfaces:** an effect `sky` with keys `at` (the set centre), `style` (`"day"`, `"overcast"` or `"dawn"`), `t` and `duration` (0 means until the scene ends). It draws a large camera-centred dome with a vertical colour gradient (horizon to zenith) and a few soft cloud layers. It is drawn before the set so it is behind everything, it is deterministic (EffectSeed), and it does not touch the night or storm effects.
- [ ] **Step 1: Write the failing tests.** Validator tests for `sky` and a pure test of the gradient colour at the horizon and the zenith per style.
- [ ] **Step 2: Implement.** Implement it, add it to SKILL.md, and add `sky` `"day"` to `wand_wall.json`, `wand_alley.json` and `wand_ollivanders.json` through their generator (do not hand-edit the JSON). Run the tests under the lock.
- [ ] **Step 3: Commit** with the message `Give daytime memories a sky`.

### Task 4: The Privet Drive set (Opus, design-heavy)

**Interfaces:**
- **Produces:** `privet_drive.py` with `build(world) -> ANCHORS`, `write_nbt()`, `TEMPLATE_AT` and `RADIUS`, and the template `privet_drive.nbt`.
- **`ANCHORS`:**
  - `cupboard_bed` and `cupboard_eye`;
  - `cupboard_door` (a hinge for a `memory_door` at a small scale, or a block);
  - `hall_mat`, `letter_slot` and `front_door`;
  - `kitchen_table` and the seats (with seat tops for `human_motion.py`);
  - `armchair` (with seat top), `living_fireplace_mouth`, `living_fireplace_yaw` and `living_room_box`;
  - `street_wide_cam`, `roof_owl_spots` (20 or more), `stairs_top`.
- **Design brief:** the parked Stage 2 plan's Task 3 brief, plus the research notes in `.superpowers/sdd/2026-10-20-sorting-memory-stage2/progress.md`. Build at normal block scale, with the cupboard at child scale (1 × 2 × 1.5–2).
- [ ] **Step 1: Build.**
  - Write the script and the `.nbt`.
  - Assert that `physics_problems` is empty, every anchor stands on solid ground with air above, and `cupboard_eye` is in air.
  - Render previews (street, ground-floor cut, cupboard section, living room) with the `rendering-minecraft-structures` skill, look at them, iterate, and send the user the street and the living room.
- [ ] **Step 2: Commit** the script and the `.nbt` (no gradle needed; the controller runs `SceneDataTest` later). Message: `Build 4 Privet Drive for the Letter memory`.

### Task 5: The sea, the rock and the hut (Opus, design-heavy)

**Interfaces:**
- **Produces:** `letter_island.py` with `build(world) -> ANCHORS` and the template `letter_island.nbt`.
- **The set:**
  - A dark sea (water over a deepslate bed, edges closed with barrier, per the physics trap).
  - A bare black rock islet with a ramshackle one-room shack: weathered dark planks, a sagging roof, one window, a fireplace with embers, a sofa where Dudley sleeps, the floor where the boy lies, and the Dursleys' corner.
  - A low doorway, so Hagrid at scale 1.5 must stoop, with a door-leaf hinge anchor for Task 3's falling door.
  - A small landing where the boat beaches.
- **`ANCHORS`:**
  - `boat_start`, `boat_path` (a list), `boat_landing`;
  - `sea_center` and `sea_spread`;
  - `hut_door_hinge` and `hut_door_scale`;
  - `hagrid_outside` and `hagrid_inside`;
  - `boy_floor`, `dudley_sofa` (with seat top), `vernon_corner`, `petunia_corner`;
  - `fireplace` and `rain_box`.
- [ ] **Step 1: Build.** Write the script, the `.nbt`, the asserts and the previews (the islet at night, the hut interior). Send the user the previews.
- [ ] **Step 2: Commit.** Message: `Build the rock and the hut for the Letter memory`.

### Task 6: Voices, sound effects, music (Sonnet; after Task 0)

- **Lines** (the user approves them in Task 0; original; no names):

  | id | Speaker | Line |
  |---|---|---|
  | `letter_petunia_up` | Petunia | "Up! Get up! Now!" |
  | `letter_vernon_mine` | Vernon | "Who'd be writing to you? Give that here, boy!" |
  | `letter_vernon_nomail` | Vernon | "No more post! Not one more letter comes through that door!" |
  | `letter_vernon_sunday` | Vernon | "Fine day, Sunday. No post on Sundays!" |
  | `letter_vernon_shout` | Vernon | "Get away from them! Out! Out!" |
  | `letter_vernon_gun` | Vernon | "Who's there? I'm warning you, I'm armed!" |
  | `letter_hagrid_sorry` | Hagrid | "Sorry about that." |
  | `letter_hagrid_found` | Hagrid | "There you are. Last time I saw you, you were only a baby." |
  | `letter_hagrid_wizard` | Hagrid | "You're a wizard." |
  | `letter_hagrid_letter` | Hagrid | "This is for you. It's been trying to reach you for weeks." |

- **Sound effects (ElevenLabs),** mastered per `reference_elevenlabs_sfx`:

  | id | What |
  |---|---|
  | `letter_slot` | a letter dropping through a slot onto a mat |
  | `letter_hammer` | hammering nails into wood |
  | `letter_owls` | many owls hooting outside |
  | `letter_chimney_rumble` | a rumble in the chimney |
  | `letter_whoosh` | a dense rush of fluttering paper |
  | `letter_storm` | a stormy sea with wind and rain, 20 s |
  | `letter_waves` | waves slapping a small wooden boat |
  | `letter_thunder` | thunder |
  | `letter_pound` | huge fists pounding on a wooden door |
  | `letter_door_crash` | a door torn off its hinges and slamming flat on the floor |
  | `letter_fire` | a fire crackling |

- **Music:** register the user's tracks as `pensieve.music.letter_*` (cut and faded as was done for `hall_*`).
- [ ] **Step 1: Generate.** Generate and master everything, register it in `sounds.json` and `ModSounds`, and verify every entry resolves.
- [ ] **Step 2: Build.** Run `SceneDataTest` and `runServer` under the lock.
- [ ] **Step 3: Commit.** Message: `Add the Letter memory voices and sound`.

### Task 7: The four scenes (Opus, cinematic)

**Consumes:** everything above. **Produces:** `letter.json` → `letter_flood.json` → `letter_crossing.json` → `letter_hut.json` (`end_action: "wake"`), from `letter_scene.py`.

- **Beats:** the spec's table: cupboard 15 s, first letter 20 s, more letters 20 s, flood 30 s, crossing 15 s, Hagrid 50 s, the letter 10 s, about 170 s in all.
- **Opening:** `letter` opens from black with `eyes_open` at t 0.
- **Flood (`letter_flood`):** uses `letter_flood` with the living-room anchors (count about 300), with the human-motion clips (`CHEER` is wrong here; the boy uses jump paths with `arms_out` and reaching).
- **Crossing (`letter_crossing`):** `rough_sea` and `rain` with the boat; the Dursleys and the boy `ride` the boat.
- **Hut (`letter_hut`):**
  - the pounding;
  - the door falling (`look` pitch −90 after the hinge breaks);
  - Hagrid stooping through, lit by `lightning`;
  - first person for his hand holding out the letter: a `memory_letter` in his hand via `hold hogwarts_invite`;
  - the boy's hand reaching (a `REACH`-style arm via `limbs` on a `life: false` first-person rig, or the `REACH_PLACE` clip);
  - `eyes_close`, then the end.
- [ ] **Step 1: Shot list.** Write the shot list in the report first.
- [ ] **Step 2: Generator asserts:**
  - size;
  - unique ids;
  - camera in air;
  - collisions;
  - leg boxes;
  - subtitles against the measured clip lengths;
  - every `next` exists;
  - the `letter_flood` event's `box` (from `living_room_box`) is all air in `privet_drive.nbt` (a SceneDataTest check, carried over from Task 1).
- [ ] **Step 3: Test.** Run `python -I build_scripts/pensieve_scenes/letter_scene.py`, then the full `./gradlew test` and `runGameTestServer` under the lock. Expect PASS.
- [ ] **Step 4: Commit.** Message: `Stage the Letter memory`.

### Task 8: The trigger (Sonnet)

**Interfaces:**
- `pensieve/LetterMemory.java`:
  - `static final String TAG_PENDING = "LetterMemoryPending"`;
  - `markPending(ServerPlayer)`;
  - `boolean tryStart(ServerPlayer, ItemStack letter)`: true if it started the memory;
  - `register()`: home listeners.
- `ModEvents.spawnFirstYearInvite` calls `LetterMemory.markPending(player)`. This is the only place the tag is set, so old saves never get it.
- `HogwartsInviteItem.use` calls `LetterMemory.tryStart` first. Only if that returns false does it send `OpenHogwartsInviteScreenPacket` as now.
- **When the memory starts:** `PensieveSessions.startSolo(player, "letter")`.
- **When the player gets home** (any home-coming of a letter-memory session, including the wake):
  - send `OpenHogwartsInviteScreenPacket` (after `WAKE_DONE_TICKS` when the end was a wake);
  - clear `TAG_PENDING` only when `letter_hut` ended normally (`length` reached).
- **Ignore re-entry:** `tryStart` returns true without doing anything while the player is a viewer or the memory is building, so the screen doesn't open underneath.
- [ ] **Step 1: Write the failing gametests** in `LetterMemoryGameTests`:
  - `pendingPlayerStartsMemory`;
  - `oldSaveOpensLetterDirectly`;
  - `memoryEndClearsPendingAndOpensLetter`;
  - `leaveEarlyOpensLetterAndKeepsPending`;
  - `yankKeepsPending`;
  - `secondUseWhileInMemoryIgnored`.
  
  Use small stand-in scenes registered under the shipped ids, as `SortingGameTests` does.
- [ ] **Step 2: Implement.** Implement it, update SKILL.md (a "Story triggers" note), and run the full test suite and gametests under the lock.
- [ ] **Step 3: Commit.** Message: `Play the Letter memory the first time the letter is opened`.

### Task 9 (controller): review and the user's in-game pass

- [ ] **Step 1: Final review.** Run an Opus final review of the whole range against this plan and the spec, then apply a fix wave (Sonnet).
- [ ] **Step 2: In-game pass.** The user watches with a fresh player: the owl delivers the letter, they open it, the memory plays, they wake and the letter opens. Gather their notes for a feedback round. Ask them to push.
