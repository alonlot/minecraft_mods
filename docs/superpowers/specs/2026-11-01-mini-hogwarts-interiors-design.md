# Mini Hogwarts: a castle worth walking into

Date: 2026-11-01. Branch: `sorting-memory` (see Licence). Status: approved by the user 2026-11-01.

## Why

Mini Hogwarts was built to look good from outside. Inside it is a shell: 36k blocks in a 96 x 109 x 145
schematic, 49 torches, 17 bookshelves, and every big wing hollow from the rock to the roof with no floor.
The only furnished room is the Potions classroom (`build_scripts/add_potions_classroom.py`). When a castle
appears it pops in over ~5 s with a chat line.

The user wants it to look good, magical and cinematic **from the inside as well**, and said not to be afraid to
make it bigger. They asked that the Pensieve memories be mined for anything that can be reused, and chose to
swap the castle's own Great Hall for the memory's film-styled one.

## Goals

1. **Every shell becomes real rooms** with floors, light, furniture and a reason to walk through them.
2. **The Sorting memory's Great Hall becomes the castle's Great Hall**, replacing the current diagonal hall wing.
   The castle grows to fit it.
3. **Magic you can see in the real world**, reusing the memory renderers: the enchanted ceiling and floating
   candles in the Great Hall, light rays through its windows, house banners.
4. **Cinematic moments**: the castle builds itself up out of the rock instead of popping in; a title card the first
   time you reach it; the Great Hall doors swing open as you walk up; the ceiling opens into sky on your first
   step inside.
5. Exterior silhouette kept: towers, spires, viaduct, gatehouse stay where they are and look as they do, apart from
   the new hall wing and windows that glow at night.

## Non-goals

- No change to how or where castles are triggered (`maybeAutoSpawn`, spacing, `/spawnminihogwarts`,
  `/findminihogwarts`), beyond the build animation.
- Castles already standing in existing worlds are not rebuilt; the new castle applies to castles built after the
  update.
- Not the Battle of Hogwarts castle or the Quidditch pitch: both are cut from downloaded maps and are out of scope
  (the pitch can come later as grounds).
- No new voiced content and no new creatures.

## Anti-goals (would be a failure even if it technically works)

- Rooms that are boxes with a chest in the middle. Each room needs a focal point, light that reads, and density
  judged in the textured renderer (see `.claude/skills/rendering-minecraft-structures`).
- A hall wing that looks pasted on from outside: it must take the castle's own palette and roofline.
- Anything that can crash or stall a dedicated server, or calls a client class from common code
  (memory: server/client safety). Effects are client-side visuals fed by server data.
- A build animation that hitches the server or leaves a half-built castle on restart.
- Loud-block blizzards (cobwebs, candles, lanterns) — density is checked in textures, not counted.

## Licence

`great_hall.nbt` comes from a user-supplied schematic of unknown licence and lives only on `sorting-memory`
for the user's own use. Because the castle now contains it, **this whole change stays on `sorting-memory`** and
must not be merged to `main` or pushed to the public GitHub mirror as-is.

## What exists to reuse

| Thing | Where | Use in the castle |
|---|---|---|
| Great Hall set (film restyle: mud-brick walls, dark-oak joinery, stone piers with pointed arches, braziers, eagle corbels, four house tables with candles, High Table on a dais, Gothic double portal, entrance hall with an imperial staircase, banners) | `build_scripts/pensieve_scenes/great_hall_set.py`, source `build_scripts/pensieve_scenes/source/great_hall.schem`, output `structure/great_hall.nbt` (114 x 57 x 45, 36k blocks) | The castle's Great Hall + entrance hall |
| Enchanted ceiling (night: gradient, Milky Way, twinkling stars, drifting clouds, shooting stars; storm: cloud volume + lightning) | `client/pensieve/EnchantedCeiling.java`, `pensieve/scene/CeilingSky.java` | Over the hall |
| Floating candles (hundreds, bobbing, flickering) | `client/pensieve/FloatingCandlesRenderer.java`, `pensieve/scene/FloatingCandles.java`; positions in `memories/sorting_hall.json` | Over the tables |
| Light rays | `client/pensieve/LightRaysRenderer.java`, `pensieve/scene/LightRays.java` | Through the hall's tall windows by day |
| Hall doors (scale-4 leaves that swing) | `entity/MemoryDoorEntity.java` + its renderer | The hall's portal |
| House banners | `memory_banner_*` entities | Hall and common rooms |
| Wand-box shelving (mud/tuff bricks + near-empty chiseled bookshelves, dark-oak bands) | `build_scripts/pensieve_scenes/diagon_set.py` (Ollivanders) | Library walls, Restricted Section |
| Living Statue (dormant stone knight that wakes for hostiles) | `entity/LivingStatueEntity.java` | Suits of armour in corridors that defend the castle |
| Fat Lady living portrait | `portrait/PortraitPlacement.java` | Guards the Gryffindor common room door |
| Public Pensieve, themed chests, Potions classroom, students, house elves | `worldgen/MiniHogwartsManager.java` | Kept; re-sited into the new rooms |

All the renderers today read their state from `EffectVisuals` lists and their clock from
`PensieveClientState.sceneTime`, so they only draw inside a memory. They need a second source: castle effects
driven by world time.

## The castle

### Shells today (schematic coordinates, x 0..95, z 0..144, y 0..108)

Found by floor plans of the schematic; the builder should confirm with renders.

- **Great Hall wing**: a diagonal building from about (73, 23) to (40, 60), ~16 wide, hollow from the rock to a
  roof ridge near y 70, with small towers on the ridge. **This is what gets replaced.**
- **Central tower**: round, about (68, 64), radius ~10, the tallest (to ~y 94).
- **Bridge tower**: round, about (66, 85..93), reached by a short bridge (z 75..83).
- **Viaduct**: x ~40..45, z 59..95, deck near y 24..26, joining the hall wing to the gatehouse.
- **Gatehouse**: z 95..123, x ~5..55: two corner towers, a central block with the clock face, ground-floor rooms
  with oak floors at y 24, the apse tower (x 32..45, z 106..120) holding the Potions classroom.
- **West descent**: small structures and steps, x 4..30, z 41..67.
- The rock: ~13k stone under everything, the castle's hill.

### The new Great Hall wing

- Take the memory hall **with its roof** (the set removes everything at local y >= 21 over the hall for the
  open sky; the castle keeps it), the film restyle, the Gothic portal and the entrance hall. Generate it from the
  set builder's code rather than from `great_hall.nbt`, so the roof comes back; factor the shared steps out of
  `great_hall_set.py` instead of copying them, and leave the memory's output byte-identical.
- Remove the diagonal hall wing (its walls, roof and ridge towers), keeping the rock under it.
- Set the new wing **on an axis**, its floor at the castle's main level (the gatehouse and viaduct deck, ~y 24),
  in the area the old wing used, extending the schematic bounds as far as it needs. The entrance hall joins the
  castle at the central tower, so the imperial staircase leads on into the Grand Staircase.
- From outside it must look like part of this castle: re-skin the outer faces and roof in the castle's palette and
  roofline (stone, cobblestone trim, the same roof material and pitch), tall windows lit from inside at night, and
  rock or stone skirt under any part over open ground.
- Exact siting, orientation and the join are the builder's call, decided from renders against the goals above.

### Rooms (in priority order)

Each room gets a focal point, layered light (the base light blocks plus visible sources), and furniture dense
enough to read in the textured renderer. Assigning rooms to shells by size is the builder's call; the
suggestions are where they fit best.

1. **Great Hall + entrance hall** — from the memory, as above. The Sorting stool and a Sorting Hat (block or
   item display) on the dais. Themed Great Hall chest moves here.
2. **Grand Staircase** — central tower. An open stairwell up the full height, flights switching back round the
   wall with landings at every floor, portraits on the walls (paintings; the mod's `painting_variant`s where they
   fit), a few floating candles in the void. Stretch: one flight that swings between two landings every minute or so
   (block swap with a grinding sound), never while a player stands on it.
3. **Library** — tall shelving to the ceiling, ladders, reading tables with lanterns, a Restricted Section behind
   iron bars using the Ollivanders shelving language. The public Pensieve and the library chest move here.
4. **Gryffindor common room** — top of a tower: red and gold, a big fireplace, armchairs, a stair up to
   dormitories (beds). The Fat Lady hangs at its door instead of the Great Hall wall.
5. **Headmaster's office** — top of a tower, reached by a spiral stair behind a gargoyle: round room, desk,
   instruments, a phoenix perch, shelves and portraits. The tower treasure chest goes here.
6. **Classrooms** — Potions (exists, keep), Defence Against the Dark Arts, Transfiguration (gatehouse; the door is
   already labelled), Charms. Desks, a teacher's desk, a theme prop each.
7. **Astronomy platform** — the top of the tallest tower, open, with telescopes and a rail.
8. **Dungeons and kitchens** — dug into the rock under the castle (the castle may grow downward): a torchlit
   dungeon corridor with cells (the dungeon chest), the Slytherin common room (green light, low vaults, a dark
   fireplace), and the kitchens under the Great Hall with four long tables mirroring the ones above (the house elves
   spawn here).
9. **Corridors and stairs** connecting all of it: lanterns or torches every few blocks, banners, carpets, dormant
   Living Statues in alcoves (they wake only for hostiles, so they guard rather than attack players).
   *Builder's call (not the user's):* castle statues are a third mode next to the ruin guardian and the bound
   sentry: no owner, wake only for hostile mobs, never target players; a player hitting one makes it fall and
   reform at its post, and it drops nothing, so they cannot be farmed.

Every new room must be reachable on foot from the main entrance without breaking blocks.

### Lighting

Interiors use light blocks (invisible) for the base level so rooms are lit without torch spam, plus visible
sources for mood (braziers, lanterns, candles, fireplaces). Windows on the outer faces get light behind them so the
castle glows at night (the quidditch pitch set already does this with glowstone behind the castle's windows).
Hostile mobs must not spawn indoors on any floor.

## Magic in the real world

A small client-side **castle ambience** system:

- The server knows each built castle (`MiniHogwartsTracker`) and the effect anchors the new schematic records
  (the ceiling box, candle boxes, light-ray windows, door hinge points), in the castle's world frame.
- It tells clients about castles near them (on login, dimension change, and when a castle finishes building) with
  a small packet; clients keep the list and drop it on logout. Nothing about this runs on a dedicated server's
  render path, and nothing on the server references a client class.
- The renderers (`EnchantedCeiling`, `FloatingCandlesRenderer`, `LightRaysRenderer`) draw from both
  `EffectVisuals` (memories) and the castle list. Castle effects use the world's game time as their clock, so all
  players see the same candles. They draw only within render range, and only in the overworld.
- **Ceiling — it follows the real sky (the user's decision).** Under the hall roof (the plane sits at the wall tops;
  the roof above it is hidden behind the sky) the ceiling mirrors the overworld's own sky: a **new day style** (a
  bright blue gradient with drifting white clouds, no stars), blending through dawn and dusk (warm horizon tints,
  stars fading in and out) into the existing **night style** after sunset, driven by the world's time of day. When it
  rains the clouds thicken and grey over; when it thunders, the **storm style** with its lightning, if the storm's
  cloud volume renders acceptably under a roof, otherwise the day/night sky heavily overcast (builder's call after
  seeing it). `CeilingSky` and `EnchantedCeiling` gain the day style and the blend; the memory's own ceilings keep
  their explicit `night` / `storm` styles and must render exactly as before. This is real renderer work and is
  checked in game at noon, sunset, midnight, in rain and in a thunderstorm.
- **First step inside**: the first time a player enters a given hall, the ceiling plays its reveal (the dissolve
  from a plank roof into sky, `CeilingSky.DEFAULT_REVEAL_TICKS`); after that it is simply open.
- **Doors**: two scale-4 door leaves at the hall portal swing open when a player comes within a few blocks and
  close a while after the last one leaves. Use the existing door model; whether as a real synced entity or a
  client-only visual over an always-passable opening is the builder's call, as long as it works for everyone and
  never traps a player.
- **Light rays**: shafts through the hall's tall windows while the sun is up and it is not raining, so they match
  the day ceiling above them; warmer and lower at dawn and dusk.
- **Banners**: real blocks where block banners suffice; the memory banner entities only where hanging banners
  matter (the hall), and only if they can exist as server entities safely.

## The ceiling spell: bewitch your own ceiling

The user asked for a spell that lets a player put the enchanted ceiling into their own builds by choosing two
corners.

- **A new spell**, added like the others (`SpellType`, a spellbook with a texture from the `texturing-spellbooks`
  skill, a wiki entry). Incantation *Caelum Fascino* ("bewitch the sky"; no canon spell exists, so the name is the
  builder's call, not the user's).
- **Two corners.** Cast at a block: the first cast marks corner one (a lingering glow on that block, shown to the
  caster); the second cast at another block marks corner two and **creates the ceiling** over the rectangle
  between them. The sky plane sits just under the lower face of the targeted blocks, at the lower of the two
  corners' heights, so a player targets two opposite corners of their room's ceiling. The blocks stay: the ceiling
  is purely visual and drawn just in front of the real ceiling's underside (the same trick as the hall roof).
  The mark expires after a short while or when the caster casts somewhere too far away, with a hint.
- **It looks and behaves like the hall's**: plays the reveal (a plank roof dissolving into sky) when created, then
  follows that level's real sky — day, dawn, dusk, night, rain, storm — through the same renderer.
- **Undo.** Sneak-cast at a bewitched ceiling to remove it. Only its creator or an op can.
- **Limits** so it cannot hurt performance or be abused (exact numbers are the builder's call): a maximum side
  length (on the order of 64 blocks), a per-player count, and no overlap with another ceiling.
- **Same system as the castle.** Spell ceilings and the castle's hall ceiling are the same thing: ceiling regions
  kept server-side in `SavedData` per level and sent to nearby clients with the ambience packets. A castle simply
  registers its hall's region when it is built.
- **Where to get it**: the spellbook appears in the castle library's chest / Restricted Section and from the Diagon
  Alley Bookseller; whether it also needs a spell level or practice follows how comparable utility spells work.
- Gametests: two-corner creation, the side and count limits, removal permissions, persistence across a restart.

## Cinematic arrival

- **The castle builds itself up out of the rock.** Placement stays layer-by-layer bottom-up (the schematic index is
  already y-major), slowed so a build takes about a minute, with a rumble at the castle, dust and falling-gravel
  particles along the layer being placed, and a gentle camera shake for players within ~100 blocks. Furnishing,
  chests, students and elves still land once the build finishes. The server must not hitch, and a server stop
  mid-build must leave a castle that finishes or can be finished (today the job is dropped on stop; that has to
  change, e.g. resuming from a saved cursor in `MiniHogwartsTracker`).
- **Title card**: the first time a player comes within sight of a castle (~80 blocks), "Hogwarts" with
  "School of Witchcraft and Wizardry" beneath, as a title/subtitle, once per player per castle.
- The announcement chat line stays.

## Constraints

- Forge 1.21 (1.21.0 client jar in the gradle cache), Java. Known traps: no switch-on-enum in render code; entity
  event bytes 21/35/63 crash the client; `RenderLevelStageEvent` partial tick is `PensieveClientState.partial()`
  style, not `event.getPartialTick()` (memories `reference_renderlevelstage_matrix`,
  `feedback_no_enum_switch_in_models`, `feedback_entity_event_bytes`).
- Big builds go through `ServerLevel.setBlock` outside worldgen, chunked over ticks (memory
  `project_big_structures_outside_worldgen`). The loader `MiniHogwartsSchematic` reads Sponge v3 and ignores
  block-entity NBT; banners with patterns, signs with text and filled containers need either loader support or
  post-placement code.
- Never run gradle while the user's client is running.
- Leaves need an opaque block behind them; sculpting needs ~20 blocks for a head to read (memories).
- Commit and push after each finished stage (memory). Push to `origin` only (Licence).

## Verification

- Every stage is rendered in the textured viewer, with cutaways (`cut=x|z`, `maxy=`) for each room, and the
  pictures looked at before moving on.
- A block-type census per stage (light sources, loud blocks).
- `runServer` (or the dedicated server harness) builds a castle headless without errors or far-chunk spam.
- Gametests for anything with logic (door trigger, title-once, resume-after-restart, ambience packet round trip).
- The user's in-game pass is the final check (one review per task; memory `feedback_review_cost`).

## Left to the builder

- Exact siting, orientation and join of the hall wing; which room goes in which shell.
- Interior decoration choices within each room's brief.
- How the castle-effect anchors are recorded (in the schematic metadata, a sidecar JSON, or computed).
- Whether the doors are a synced entity or a client visual.
- Build speed, particle and shake details, within "about a minute, no hitch".
- Splitting the work into stages; a sensible order is: hall wing → rooms → lighting/windows → ambience system
  (day/night ceiling) → ceiling spell → arrival.
