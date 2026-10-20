# The Wand Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first time a new player buys a wand from the wandmaker, a ~160 s film memory plays instead of the old in-world ceremony:
- the brick wall behind the Leaky Cauldron opens brick by brick at Hagrid's knock;
- a crowded Diagon Alley with shop windows;
- Ollivanders: the ladder, the measuring tape, three wrong wands, then the player's own wand choosing them in golden light and wind.

Then they wake in the real world, wand in hand.

**Architecture:**
- **Three chained scenes:** `wand_wall` → `wand_alley` → `wand_ollivanders` (`end_action: "wake"`).
- **New engine:**
  - a generic `memory_block` actor: any block state, pathed and rotated, used for bricks, wand boxes and the vase;
  - a `memory_tape` actor;
  - a `wand_choice` effect;
  - an item placeholder `{viewer_wand}` filled by the server from the purchase.
- **Trigger:** `ModEvents`' wandmaker trade hook starts the memory instead of `OllivandersCeremony.begin`. The ceremony stays as the fallback.

**Tech Stack:** Forge 1.21 (51.0.33), Java 21, JUnit 5, Forge gametests, Python 3 generators, Fish Audio, ElevenLabs, ffmpeg.

**Spec:** `docs/superpowers/specs/2026-10-20-letter-and-wand-memories-design.md` ("The Wand Memory").

## Global Constraints

The same as `docs/superpowers/plans/2026-10-21-letter-memory.md` Global Constraints (server/client safety, no enum switch, gradle lock, never gradle with a client running, the 900,000-character cap, the SKILL.md process and asserts, no names in spoken lines, scales, first person only for the eyes and the wand moment, git rules, keys, approvals and music), plus:

- **The same lines for every wand** (user decision): Ollivander never names a wood or core.
- **New players only** (user decision): the trigger reuses `hogwartsmagic:ollivanders_done`. Players who already have it never get the memory on their own.

## Review Focus

1. **The wand is never lost or duplicated:** the purchase completes before the memory starts, and leaving, death, disconnect or a failed start leaves exactly the bought wand in the inventory. Covered by Task 7's `wandKeptWhenMemoryFails` and `leaveEarlyKeepsWand`.
2. **The wandmaker villager:** trading mid-memory isn't possible, and the villager isn't stuck in a trade UI after the player's return. Covered by Task 7's `tradeScreenClosedBeforeMemory`.
3. **`{viewer_wand}` with no purchase** (`/ollivanders` replay) falls back to the held wand, then to `harry_potter_wand`. Covered by Task 1's `viewerWandFallbacks`.
4. **Hundreds of brick actors:** the wall opening must not tank the frame rate. `memory_block` rendering is batched or cheap per instance, and its per-actor cost is measured. Task 1 records the cost; the in-game pass confirms.
5. **The crowd never walks through anyone,** or through stalls, shop fronts or the archway: the generator asserts from the Great Hall work apply. Task 6.

---

## File map

| File | Task | Responsibility |
|---|---|---|
| `entity/MemoryBlockEntity.java`, `client/MemoryBlockRenderer.java`, `pensieve/scene/WordText.java` (or a new `ItemText`), `PensieveSessions` (start context), `SceneValidator` | 1 | The block actor and `{viewer_wand}` |
| `entity/MemoryTapeEntity.java`, `client/MemoryTapeRenderer.java`, `pensieve/scene/WandChoice.java`, `client/pensieve/WandChoiceRenderer.java` | 2 | The tape and the wand-choice effect |
| `build_scripts/generate_memory_skins.py`, `textures/memory_skin/{witch,wizard,shopkeeper}_*.png`, `ollivander.png` | 3 | Crowd and Ollivander skins |
| `build_scripts/pensieve_scenes/diagon_set.py`, `structure/wand_diagon.nbt`, `structure/wand_ollivanders.nbt`, an optional `wand_boxes` decorative block (block + model + texture) | 4 | Sets |
| `sounds/pensieve/{voice,sfx}/wand_*.ogg`, `sounds.json`, `ModSounds.java`, `build_scripts/sounds/wand_*.py` | 5 | Voices, sound effects, music |
| `build_scripts/pensieve_scenes/wand_scene.py`, `memories/wand_*.json` | 6 | Scenes |
| `event/ModEvents.java` (wandmaker trade hook), `command/OllivandersCommand.java`, `pensieve/WandMemory.java` (new), `gametest/WandMemoryGameTests.java` | 7 | Trigger |

---

### Task 0 (controller, with the user): assets

- [ ] **Step 1: Ollivander's skin.** Search NovaSkin for Ollivander (white wild hair, pale, dark robes and waistcoat), send the user a contact sheet, and download the pick only after approval.
- [ ] **Step 2: Ollivander's voice.** Search Fish for Ollivander or a John Hurt-like elderly, soft, whispery British man. Generate 3 samples and let the user pick. Hagrid uses the voice confirmed in the Letter plan's Task 0.
- [ ] **Step 3: Lines.** The user approves the line table (Task 5).
- [ ] **Step 4: Music.** Ask the user for the music: "Diagon Alley and the Gringotts Vault", and a quiet magical cue for Ollivanders.

### Task 1: The `memory_block` actor and `{viewer_wand}` (Opus)

**Interfaces:**
- **Actor `hogwartsmagic:memory_block`:**
  - It renders one block state through the block renderer, at the actor's position and rotation (`look` yaw/pitch, plus a new optional `roll` key on `look` keys if needed).
  - Actor `scale` applies.
  - The block state is set by a new actor key `"block": "minecraft:bricks"` (with properties `minecraft:oak_stairs[facing=east]`), valid only for this type.
  - Light is sampled at the block.
  - The renderer caches the baked model per state.
- **`{viewer_wand}`:** an item placeholder allowed wherever an item id is accepted (`items`, `hold`, `equip`).
  - The validator accepts it.
  - At session start the server substitutes it from a start context: `PensieveSessions.startSolo(ServerPlayer, String sceneId, Map<String,String> context)`, with the existing 2-argument form delegating with an empty map.
  - The substitution is in the scene JSON sent to the client, so clients never guess.
  - Fallback: the held wand, then `hogwartsmagic:harry_potter_wand`.
- [ ] **Step 1: Write the failing tests:**
  - parser and validator for `block` (unknown block, misuse on other types);
  - `viewerWandSubstitution`;
  - `viewerWandFallbacks`;
  - a gametest `blockActorSpawns`.
- [ ] **Step 2: Implement.**
  - Write a dev scene `run/pensieve_dev/dev_blocks.json` with 400 brick actors flying in arcs, and record the frame cost in the report.
  - Update SKILL.md.
  - Run the tests and gametests under the lock.
- [ ] **Step 3: Commit.** Message: `Add a block actor and the viewer's wand to memories`.

### Task 2: The measuring tape and the `wand_choice` effect (Opus)

**Interfaces:**
- **Actor `hogwartsmagic:memory_tape`:** a cloth measuring tape drawn as a ribbon that follows its own recent path (as the Fiendfyre serpent does), with brass ends and numbered markings. Pathed with `linear` keys, it measures the boy in a rising spiral.
- **Effect `wand_choice`:** keys `at` (the wand tip or the boy's chest), `duration` (default 100) and `intensity`.
  - A warm golden glow gathers at `at` over 20 ticks.
  - A soft light bloom washes the screen for nearby viewers.
  - A wind of glowing motes spirals up around the boy, and loose paper lifts.
  - A gentle settle at the end.
  - Pure maths in `WandChoice`, with tests (bounded, deterministic, gather → peak → settle).
- [ ] **Step 1: Write the failing tests:** `WandChoiceTest`, plus tape path smoothing tests if any maths is pure.
- [ ] **Step 2: Implement.** Write a dev scene, update SKILL.md, and run the tests under the lock.
- [ ] **Step 3: Commit.** Message: `Add the measuring tape and the wand-choice moment to memories`.

### Task 3: Skins (Sonnet; after Task 0)

- [ ] **Step 1: Generate the crowd.** Procedurally generate `witch_01..20`, `wizard_01..20` and `shopkeeper_01..06` in `generate_memory_skins.py`:
  - varied ages (some grey hair and beards), skin tones and robe colours (deep purples, greens, browns, burgundy, navy), with some cloaks and pointed hat brims on the hat layer.
  - Add the Ollivander download.
  - Render a contact sheet, look at it, and send it to the user.
- [ ] **Step 2: Commit.** Message: `Add the Diagon Alley crowd skins`.

### Task 4: The sets (Opus, design-heavy)

- **`wand_diagon.nbt`** from `diagon_set.py`:
  - **The yard:** the Leaky Cauldron back yard (a small walled yard, bins, the brick wall with the archway region left as a cut-out filled by `memory_block` bricks, so the scene animates them).
  - **The street:** based on `structure/diagon_alley.nbt`, trimmed or rebuilt to fit radius 200 and restyled like the film (narrow, crooked, overhanging upper storeys, steep roofs, hanging signs, bunting).
  - **Shop windows to dress:**
    - Quality Quidditch Supplies, with a broom on a stand in the window and the glass at child-face height;
    - Eeylops Owl Emporium, with cage anchors for owl actors;
    - the apothecary;
    - Flourish and Blotts;
    - cauldrons outside a shop;
    - Gringotts' white marble at the end, with steps and door anchors for goblin guards.
  - **Lighting:** daylight (`time_of_day` around 4000).
- **`wand_ollivanders.nbt`:**
  - a narrow, tall, dusty shop: walls of wand-box shelving to the ceiling, a counter with a bell, a rolling-ladder rail, a dim window with gold lettering, dust and a single lamp;
  - shelf faces from a new decorative block `hogwartsmagic:wand_boxes` (a texture of stacked narrow boxes in faded colours with labels), or banks of existing blocks if they read well; the builder decides and shows previews.
- **`ANCHORS`:**
  - `wall_bricks` (the list of brick cells in the archway region, with target arch positions), `hagrid_yard`, `boy_yard`, `arch_center`;
  - street path waypoints (a lane graph for the crowd), `window_spots` (each with a viewing position and camera framing), `goblin_doors`, `owl_cages`;
  - `ollivander_ladder_rail`, `counter`, `boy_counter`, `tape_center`, `shelves_box_cells` (for flying boxes), `vase_spot`, `door_bell`.
- [ ] **Step 1: Build.** Write the scripts, the `.nbt` files, and the asserts (physics, anchors in air on solid ground). Render previews (the yard wall, the street, each window, the Ollivanders interior), look at them, iterate, and send the user the best four.
- [ ] **Step 2: Commit.** Message: `Build Diagon Alley and Ollivanders for the Wand memory`.

### Task 5: Voices, sound effects, music (Sonnet; after Task 0)

- **Lines** (the user approves them in Task 0; the same for every wand; no names):

  | id | Speaker | Line |
  |---|---|---|
  | `wand_hagrid_bricks` | Hagrid | "Three up... two across... Right. Stand back." |
  | `wand_hagrid_welcome` | Hagrid | "Welcome to Diagon Alley." |
  | `wand_hagrid_wand` | Hagrid | "Go on, then. Ollivanders. Only place for wands." |
  | `wand_olli_wondered` | Ollivander | "Ah. I wondered when I'd be seeing you." |
  | `wand_olli_wandarm` | Ollivander | "Which is your wand arm? Hold out your hand." |
  | `wand_olli_try` | Ollivander | "Give it a wave." |
  | `wand_olli_no` | Ollivander | "No, no, apparently not." |
  | `wand_olli_no2` | Ollivander | "No. Definitely not." |
  | `wand_olli_tricky` | Ollivander | "A tricky customer. I wonder..." |
  | `wand_olli_curious` | Ollivander | "Curious... very curious. The wand chooses the wizard. It is not always clear why." |
  | `wand_olli_great` | Ollivander | "I think we must expect great things from you." |

- **Sound effects (ElevenLabs):**

  | id | What |
  |---|---|
  | `wand_brick_tap` | an umbrella tapping bricks |
  | `wand_wall_grind` | bricks grinding and sliding, stone on stone, 6 s |
  | `wand_street` | a busy magical street: crowd, owls hooting, shop bells, 20 s |
  | `wand_shop_bell` | a small shop door bell |
  | `wand_ladder` | a wooden library ladder rolling on a rail |
  | `wand_boxes_fall` | dozens of small boxes tumbling off shelves |
  | `wand_vase` | a vase shattering |
  | `wand_fizzle` | a wand sputtering and fizzling |
  | `wand_choice` | a warm magical swell with wind rising and a soft chime |
  | `wand_tape` | a measuring tape whipping |

  Reuse the existing `OLLIVANDERS_*` sounds where they fit.
- **Music:** register the user's tracks as `pensieve.music.wand_*`.
- [ ] **Step 1: Generate.** Generate and master everything, register it, and verify every entry resolves.
- [ ] **Step 2: Build.** Run `SceneDataTest` and `runServer` under the lock.
- [ ] **Step 3: Commit.** Message: `Add the Wand memory voices and sound`.

### Task 6: The three scenes (Opus, cinematic)

- **Beats:** the spec's table: the wall 20 s, the street 50 s, the Ollivanders front 10 s, the wandmaker 15 s, the tape 10 s, three wrong wands 25 s, the one 15 s, "Curious..." 10 s, out 5 s, about 160 s in all.
- **`wand_wall`:**
  - Opens from black with `eyes_open`.
  - Hagrid (1.5) taps the bricks.
  - The archway region's bricks are `memory_block` actors: the tapped brick quivers, then a ripple outward. Each brick twists and slides on its own arc into the arch shape, staggered by its distance from the first brick, with dust particles and `wand_wall_grind`.
  - The light and the street noise rise, and the camera pushes through the arch.
  - Then `next`.
- **`wand_alley`:**
  - A crowd of 60–90 shoppers on the lane graph, each with idle life and clips (`TALK`, carrying parcels via `hold`).
  - Children at the broom window, owls in cages, goblin guards (`hogwartsmagic:goblin_guard`).
  - Hagrid leading the boy, with shots framed at each window.
  - It ends at the Ollivanders door.
- **`wand_ollivanders`:**
  - The bell, then the dim silence, then Ollivander appearing on the ladder.
  - The tape spiral.
  - Three wrong wands, with escalating chaos: flying boxes as `memory_block` actors bursting from the shelves, the vase shatter (a block event to air plus particles), the fizzle.
  - Ollivander snatching each wand back (`hold` swaps).
  - The right wand (`hold {viewer_wand}`) with `wand_choice`, then the still "Curious..." beat.
  - First person on the boy looking at his wand, then `eyes_close`, then `end_action: "wake"`.
- [ ] **Step 1: Shot list.** Write the shot list first.
- [ ] **Step 2: Generator asserts:**
  - size;
  - unique ids;
  - camera in air;
  - collisions on the lane graph;
  - leg boxes for anyone seated;
  - subtitles against the measured clip lengths;
  - brick arcs never pass through Hagrid or the boy.
- [ ] **Step 3: Test.** Run `python -I build_scripts/pensieve_scenes/wand_scene.py`, then the full `./gradlew test` and `runGameTestServer` under the lock. Expect PASS.
- [ ] **Step 4: Commit.** Message: `Stage the Wand memory`.

### Task 7: The trigger (Sonnet)

**Interfaces:**
- **`pensieve/WandMemory.java`:**
  - `boolean tryStart(ServerPlayer player, ItemStack boughtWand)`: starts `wand_wall` with the context `{"viewer_wand": <item id>}`, and returns false if the scene can't start.
  - `register()`: on home-coming after the wake (or after leaving early), `OllivandersCeremony.markDone(player)`.
- **`ModEvents`' trade hook:**
  - It keeps its checks (wandmaker profession, a `*_wand` result, not `hasHadCeremony`, not `isBusy`).
  - It closes the trade screen (`player.closeContainer()`).
  - It calls `WandMemory.tryStart`, and only if that fails calls `OllivandersCeremony.begin` as now.
- **`/ollivanders`** replays the memory (`tryStart` with the held wand), falling back to the ceremony.
- **The flag:** it is set when the memory ends or is left, so a disconnect or yank before the end lets it replay on the next wand purchase. That is fine: purchases are rare. *(My call.)*
- [ ] **Step 1: Write the failing gametests** in `WandMemoryGameTests`:
  - `firstWandStartsMemory`;
  - `flaggedPlayerGetsNothing`;
  - `wandKeptWhenMemoryFails` (with the scene missing, the ceremony runs and the wand is kept);
  - `leaveEarlyKeepsWand`;
  - `tradeScreenClosedBeforeMemory`;
  - `commandReplays`.
- [ ] **Step 2: Implement.** Implement it, update SKILL.md's "Story triggers" note, and run the full test suite and gametests under the lock.
- [ ] **Step 3: Commit.** Message: `Play the Wand memory on the first wand`.

### Task 8 (controller): review and the user's in-game pass

- [ ] **Step 1: Final review.** Run an Opus final review of the whole range, then a Sonnet fix wave.
- [ ] **Step 2: In-game pass.** The user buys a first wand with a fresh player and watches the memory. Gather notes for a feedback round. Ask the user to push.
