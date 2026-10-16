---
name: writing-pensieve-memories
description: Use when the user asks for a Pensieve memory, a cinematic, a cutscene or a tutorial scene ("make me a memory of X") - a JSON scene of skinned player actors, spells, per-limb animation, creature animations and a directed camera, played inside the Pensieve dimension.
---

# Writing Pensieve Memories

A memory is one JSON file: a built set, actors, timed events, words and a camera. Everything the viewer sees is
a pure function of the scene clock, so you author it as data and never write Java. The parser rejects unknown
keys with the JSON path, so a typo is loud. Source of truth if this file ever disagrees with the code:
`pensieve/scene/SceneParser.java`, `SceneValidator.java`, `SceneNames.java`, `CameraTrack.java`.

## Step 0 - Shot list first

Before any JSON, write (in your reply) the beats and shots: what happens, who is where, one line per shot with
its start time, length, framing and camera move. Check the total against `length`. Then write the JSON.

## Film craft rules

- Establish the place with a wide, slow shot first (fov 70 or more, camera drifting).
- Cut on action: put a `"cut": true` key on the swing, the cast, the fall, not between beats.
- Keep shots 2-6 seconds. Under 2 reads as a glitch, over 6 drags.
- Move the camera while actors move (a slow dolly or arc), lock it off for still moments.
- Hold on the key moment: ease in, then 1-2 seconds of nothing new.
- Narrow the fov (40-55) for close-ups, widen (80-100) for scale or dread. Roll 3-8 degrees for unease.
- Give every spell a wind-up (pose `wand_raised`), the cast, then a reaction (`hurt`, `die`, `cower`) 5-10 ticks later.
- End on a slow push-in (pos moves 1-2 blocks over 3+ seconds, fov narrows) before the fade; the fade is automatic at `length`.
- Dialogue (`words`) lasts 8 s on screen; space lines 2+ s apart. Narration is centred, italic, default 4 s.

## Scene format

Times are ticks (20 per second) as a number, or seconds as a string: `40` or `"2s"` or `"2.5s"`. Any time must lie
in `0..length`. Positions are blocks relative to the set origin (see Coordinates). Unknown keys are errors.

### Top level
| key | type | default | notes |
|---|---|---|---|
| `title` | string | the file id | |
| `length` | time | required, > 0 | scene ends and fades here |
| `radius` | int | 40 | max 200; everything in `set` must fit within +-radius of origin in X and Z |
| `set` | object | empty set | below |
| `poses` | object | none | custom poses, below |
| `actors` | array | none | below |
| `events` | array | none | below |
| `words` | array | none | below |
| `camera` | array | none | empty = viewer looks freely; otherwise directed |
| `end_action` | string | none | only `cave_map`: when the scene ends at `length` (not when a viewer leaves early, is yanked or the basin breaks), each viewer gets `CaveMemoryItem.grantCaveMap` once, server-side, after they are moved home, so the Cave Map lands in their real inventory (with the drink's vision lines and nausea; "The memory is too faint..." and no map when no unlooted cave is near, or outside the Overworld). `/pensieve preview` runs it too. Used by `locket_cave` |

### `set`
| key | type | default | notes |
|---|---|---|---|
| `templates` | `[{id, at:[x,y,z]}]` | none | `.nbt` structure templates (`data/hogwartsmagic/structure/*.nbt`, id `hogwartsmagic:<name>`); the template's corner is placed at `at`. Sizes (x,y,z): see below. Shipped: black_lake_ruin, chamber_hall, death_eater_camp, diagon_alley, fen_gibbet, goblin_vault, hogwarts_ruin_camp, horntail_nest, locket_cave, merfolk_village, willow_clearing |
| `fills` | `[{block, from:[x,y,z], to:[x,y,z]}]` | none | solid box; max 2,000,000 blocks each; inside the radius |
| `disks` | `[{block, center:[x,y,z], radius}]` | none | flat horizontal disc at `center` y; inside the radius |
| `features` | `[{id, at:[x,y,z]}]` | none | configured worldgen features (e.g. trees), id like `minecraft:oak`; only the anchor is checked |
| `time_of_day` | number | 18000 | day time at the set: 0 dawn, 6000 noon, 12000 dusk, 18000 midnight |
| `build_ticks` | int ticks | 60 | how long the dive waits while the set builds |

Later entries overwrite earlier ones where they overlap (template first, then fills, then disks), but a template does not carve holes in a fill beneath it.
Block ids are namespaced (`minecraft:stone_bricks`).

**Extent trap:** the validator checks only a template's or feature's *anchor* against the radius. `SetBuilder` then silently crops every block more than `radius` from the origin (X or Z), with no error. So make sure anchor + template size fits inside +-radius, and raise `radius` (max 200) for big templates. Template sizes (x,y,z) from each `.nbt`'s `size` tag: black_lake_ruin 15x9x15, chamber_hall 51x32x101, death_eater_camp 27x14x27, diagon_alley 233x45x49 (wider than radius 200 even when centred: crops), fen_gibbet 10x13x10, goblin_vault 17x8x11, hogwarts_ruin_camp 39x17x29, horntail_nest 14x5x14, locket_cave 64x42x64, merfolk_village 25x14x25, willow_clearing 23x3x23. For a new template, read it: `python -c "import gzip,struct;d=gzip.open('f.nbt').read();i=d.find(b'\x09\x00\x04size')+7;print(struct.unpack('>3i',d[i+5:i+17]))"`. Centre a template with `at` = minus half its x/z size.

### `actors[]`
Keys: `id` (required, unique), `type` (required), `skin`, `name`, `items`, `path`, `look`, `limbs`, `poses`, `anims`, `actions`.

- `type`: `"player"` (a skinned humanoid) or a namespaced entity id (`hogwartsmagic:troll`, `minecraft:zombie`). Mobs have no AI, no gravity, no collision.
- `skin` (player only): `{ "texture": "<name>" }` a bundled skin, or `{ "username": "Notch" }` fetched in the background (falls back to the default skin when offline), plus optional `"slim": true` for the 3-px arm model.
- `name`: floating name tag text. Without it there is no tag. Also used as the speaker in `words`.
- `items`: slot to item id: `mainhand offhand head chest legs feet`. Example `{"mainhand": "hogwartsmagic:harry_potter_wand"}`. An unknown slot name is silently ignored.
- `path`: `[{t, pos:[x,y,z], ease?}]` - the feet position. Holds the first value before the first key and the last after it. The body turns to face its movement unless `look` is given.
- `look`: `[{t, yaw, pitch?, ease?}]` - body and head yaw, head pitch (default 0). Overrides movement facing.
- `limbs`: `{ "<part>": { "rot": [{t, v:[x,y,z], ease?}], "offset": [{t, v:[x,y,z], ease?}], "mode": "add"|"set" } }`. `rot` is degrees, `offset` is model pixels (16 px = 1 block). `mode` default `add` (added on top of the current animation); `set` replaces it. Applied last, over poses and creature animations.
- `poses`: `[{t, name, blend?}]` - a preset or a custom pose from top-level `poses`; stays until the next cue. `blend` is the ticks to ease in; 0 or absent means the default 5, so use `"blend": 1` for an instant pose.
- `anims`: `[{t, name, blend?}]` - a creature animation by name (table below). Case-insensitive; stays in force until the next cue. `blend` fades the new clip in from the rest pose over that many ticks (0 = instantly); the previous clip stops at once, it is not cross-faded.
- `actions`: `[{t, do, spell?, target?, item?}]`.

Ease: `linear`, `in`, `out`, `in_out` (cubic). Default `in_out`. The ease belongs to the segment that ends at that key.
Keyframes between keys are interpolated per component; yaw is NOT unwrapped, so keep yaw continuous (say 90 then 180, not 90 then -180).

### Custom poses (top-level `poses`)
`"poses": { "slump": { "head": [40,0,0], "right_arm": [-20,0,5] } }` - part to absolute `[x,y,z]` degrees (no offsets). Use a name in an actor's `poses` cue. Part names must match exactly (no camelCase fallback, unlike `limbs`). Parts the pose leaves out blend back to the model's own state. Sleeves/pants/jacket/hat named in a custom pose are overwritten; to steer a layer separately use `limbs`.

### Actions (`do`)
| do | needs | effect |
|---|---|---|
| `swing` | - | arm swing |
| `sneak` / `stand` | - | crouch / stand upright |
| `hurt` | - | red hit flash, ~10 ticks |
| `die` | - | tilts over (~20 ticks) then vanishes in a poof |
| `vanish` / `appear` | - | hides / shows with a smoke burst |
| `stone` / `unstone` | - | turns the actor into petrified grey stone / back |
| `hold` | `item` | sets the main-hand item |
| `use_item` | optional `item` | raises the arm as if using the item for ~1.6 s |
| `cast` | `spell`, optional `target` | draws the spell from the actor's wand hand to the target |

`target`: `"actor:<id>"` (aims at that actor's chest) or `[x,y,z]` (a point, relative to origin). No target = 10 blocks straight ahead along the actor's facing. Turn the caster toward the target first (`look`).

### Spells (`cast`; the spell ids are bare, no namespace)
| spell | what the viewer sees |
|---|---|
| beam spells | a flying coloured bolt that bursts on impact, with its real cast sound: `expelliarmus stupefy glacius confringo incendio avada_kedavra crucio imperio sectumsempra dolohov_curse incarcerous bombarda depulso levicorpus petrificus_totalus obliviate confundo` |
| `revelio` | cyan sphere of light expanding from the target, glinting off block faces (16 ticks) |
| `lumos` | a white glowing orb at the wand tip (3 s) |
| `protego` | a shimmering shield dome around the caster (2 s) |
| `morsmordre` | the Dark Mark rising above the target (6 s) |

A beam travels 1.55 blocks/tick, so the hit lands about distance/1.55 ticks after the cast (20 blocks = 13 ticks): time the `hurt` accordingly.

Anything else fails validation ("no memory visual for ..."). Nothing is damaged: all drawn client-side only.

### Pose presets (usable by name with no definition)
| name | what it does |
|---|---|
| `wand_raised` | right arm up high, wand overhead (-150) |
| `wand_point` | right arm straight out forward (-90), the aiming stance |
| `look_down` | head tilted down 40 |
| `arms_out` | both arms out to the sides |
| `hands_up` | both arms raised surrendering |
| `cower` | head and body bowed, arms shielding the face |
| `kneel` | knees down, body dropped, arms resting |
| `bent_over_basin` | torso bent ~70 degrees forward, head down, hands on a rim |

Presets are built for the player model; other models ignore parts they lack. Positive x rotation leans/swings a part forward-down, negative x lifts an arm forward.

### Part names
- Player / humanoid: `head hat body right_arm left_arm right_leg left_leg`, and for players `jacket left_sleeve right_sleeve left_pants right_pants`. The outer layers follow their base part automatically unless you name them yourself.
- Creatures built on a `HierarchicalModel` (most mod mobs): names are the part names baked into the model (`ModelPartIndex`). They are not listed here. Start a preview: a wrong limb, pose part or animation name prints a chat error ending in `(has: ...)` that lists every valid name for that actor.
- Other models: every `ModelPart` field, camelCase turned into snake_case.

### Creature animations (`anims[].name`, entity ids without namespace)
| entity | animation names |
|---|---|
| `basilisk` | IDLE SLITHER REAR STRIKE SPIT BITE BURROW ERUPT BLINDED DEATH |
| `dementor` | FLOAT GLIDE KISS RECOIL |
| `giant` | IDLE WALK CHARGE ROAR SWIPE KICK GRAB THROW STAGGER |
| `goblin`, `goblin_guard` | IDLE WALK COUNT_COINS ATTACK |
| `grindylow` | LURK DRIFT LUNGE CLUTCH HURT DEATH |
| `house_elf` | IDLE WALK COWER PUNISH JOY DISAPPARATE |
| `inferius` | RISE GRAB RECOIL IDLE_SWAY WALK |
| `merperson` | HOVER SWIM THRUST WARN SING HURT DEATH |
| `troll` | IDLE WALK CHARGE ROAR SLAM_WINDUP SLAM_STRIKE SWEEP STAGGER |
| `lycanthrope_villager` | idle, slow_human_to_wolf, idle_wolf, wolf_to_idle, walk_wolf |
| `flying_mob` | flying |

Other entities (hippogriff, owl, acromantula, vanilla mobs) have no named animations; pose them with `limbs`. An animation plays from the cue's `t` for its own length; it loops if the definition loops. Give the walk/idle names a cue at the moment the actor starts moving.

### `events[]`
Common keys: `t`, `kind`, `id`, `at`, `volume`, `pitch`, `count`, `spread`, `duration`. `kind` is required, `id` is required for all kinds.
| kind | id | uses | effect |
|---|---|---|---|
| `block` | block id | `at:[x,y,z]` | the server places that block at `t` (a door slams, a torch lights). Removed with the set |
| `sound` | sound id (`minecraft:entity.lightning_bolt.thunder`, `hogwartsmagic:...`) | `volume` 1, `pitch` 1, optional `at` (none = heard in your own ears) | plays once at `t` |
| `particles` | plain particle id (`minecraft:poof`, `minecraft:end_rod`) | `at`, `count` 1 (max 500), `spread:[x,y,z]` box half-size | a burst; parameterised particles (dust, block) cannot be used |
| `effect` | `summoning_ritual`, `lightning`, `dark_mark` | `at`, `duration` | scripted set-pieces below |

Effects: `lightning` - one visual-only bolt with thunder; `summoning_ritual` - the three beam columns, smoke and a bolt every second, default 5 s; `dark_mark` - a Dark Mark that rises 18 blocks and fades, default 5 s. A late joiner skips old events but joins a still-running effect.

### `words[]`
`{ t, by?, text, narration?, duration? }`. With `by` (an actor id) the line shows as `<name> text` at the bottom left for 8 s; without `by`, plain text. `"narration": true` shows centred silver italic text that fades in and out over `duration` (default 80 ticks). Never written to real chat.

### `camera[]`
`{ t, pos:[x,y,z], look_at?, fov?, roll?, ease?, cut? }`, sorted by time. Defaults: `fov` 70, `roll` 0, ease `in_out`.
- `pos` is the camera (eye) position. `look_at` is `"actor:<id>"` (that actor's eye) or `[x,y,z]`; without it the camera looks straight along +Z.
- Between keys position, look target, fov and roll are interpolated (so `look_at` can slide from one actor to another).
- `"cut": true`: the camera holds the previous key until this key's `t`, then jumps to it with no blend. Use for hard cuts. Put the cut key slightly before the action it should catch.
- Before the first key it holds the first; after the last it holds the last.

## Coordinates
- Origin = the block where the set is anchored; viewers float one block above it before the camera takes over. Actors with `pos:[0,0,0]` stand on top of the block layer at y = -1, so lay floors at `y: -1`.
- Y is up. X east, Z south. Yaw follows Minecraft: 0 = facing +Z (south), 90 = facing -X (west), 180 = -Z (north), -90 = +X (east). Pitch: positive = looking down, negative = up, +-90 max. `look_at` and the camera derive yaw/pitch from positions for you; you only write yaw/pitch for actors' `look`.
- Event `at` and `set` coordinates are integer block corners (decimals are rounded). Actor `path` and camera `pos` are relative to the origin block's corner and take decimals, so use `.5` to centre on a block (`[0.5, 0, 0.5]`).
- Everything must stay within `radius` (default 40, max 200) of the origin in X and Z, or validation fails for fills/disks/templates/features.

## Skins
Shipped (`texture` value): `hooded_figure`, `regulus`, `student_gryffindor`, `student_slytherin`, `death_eater`, `voldemort`.
To add one: put a 64x64 modern-layout PNG at `src/main/resources/assets/hogwartsmagic/textures/memory_skin/<name>.png` and add `"<name>"` to the array in `src/main/resources/data/hogwartsmagic/memories/_skins.json` (the validator and `SceneDataTest` both read that list, and the test fails if a listed PNG is missing). `build_scripts/generate_memory_skins.py` is the generator of the shipped ones (needs Pillow); add a new entry there if you want it reproducible, it rewrites the list too. A `username` skin needs no files.

## The loop
1. Client closed? Edit nothing in `build/` and **never run gradle while the dev client is running**: gradle rewrites `build/sourcesSets/main` and the live client throws `ClassNotFoundException`. Check with `tasklist | findstr java` (the GradleDaemon is fine; a Minecraft java process is not).
2. Set the config `pensieveDevSceneDir` (`run/config/hogwartsmagic-common.toml`) to a folder; relative paths are against the game run dir `run/` (so `../src/main/resources/data/hogwartsmagic/memories`). `/pensieve preview` then reads `<dir>/<id>.json` fresh from disk each time, so no rebuild between edits.
3. Write the scene to `src/main/resources/data/hogwartsmagic/memories/<id>.json`. Files starting with `_` are not scenes. `<id>` is lowercase with underscores.
4. In the dev client (op): `/pensieve preview <id>` plays it from the start with no basin. `/pensieve validate [id]` only checks scenes loaded from the registry (built into the game), NOT dev-dir files: during the edit loop, `preview` is the validator (it validates the fresh file and reports problems), and validate is for shipped scenes after a build. `/pensieve list` shows loaded ids. `/pensieve leave` or the **X** key leaves a memory.
5. Screenshot, read the chat for part/animation errors, edit the JSON, then leave (press **X**) and run `/pensieve preview <id>` again; a preview cannot be restarted while you are inside the memory.
6. With the client closed: `./gradlew test --tests "*SceneDataTest*"` parses and validates every shipped scene (`./gradlew test` runs the lot). It must pass before committing.
7. Commit the scene (and any new skin) - and ask the user to push.

Size caps: scene JSON max 262,144 characters, scene id max 256 (a bigger scene is refused when sent to the client).

`SceneDataTest` is narrower than the game: its entity types are only the mod's own entities, `player`, and a fixed `VANILLA_MOBS` list in `SceneDataTest.java` (zombie, skeleton, villager, armor_stand, bat, spider, cave_spider, wither_skeleton, evoker, vindicator, witch, horse, wolf, cat, fox, sheep, cow, pig, chicken, squid, glow_squid, salmon, cod, lightning_bolt). Any other vanilla mob (even a valid one in game) fails the test until you add it to that list. Its `features` accept only `minecraft:` ids, and its `sounds`/`particles`/`blocks`/`items` accept any `minecraft:` id but only mod ids that are registered in source.

Validation rules to remember: ids are namespaced (`minecraft:`, `hogwartsmagic:`), `player` is the only bare entity id, spells are bare, skins are bare names from `_skins.json`; pose cue names must be a preset or a custom pose; a `cast` needs `spell`; actor ids must be unique; `words[].by`, `look_at` and `target` must name an existing actor.

## Giving a scene an item (so players can pour it)
Players get a scene through an item that implements `PensieveMemory`. Existing: `chamber_memory` -> scene `chamber`, `summoning_memory` -> scene `summoning` (both `StoryMemoryItem`, in `ExampleMod`), `cave_memory` -> `locket_cave` (`CaveMemoryItem`). For a new scene, only if the user wants an item:
- Register in `ExampleMod`: `ITEMS.register("<id>_memory", () -> new com.hogwartsmagic.pensieve.StoryMemoryItem(new Item.Properties(), "<scene id>"))`.
- Add it to the creative tab list next to `output.accept(SUMMONING_MEMORY.get())`.
- Add `item.hogwartsmagic.<id>_memory` to `lang/en_us.json`, and `models/item/<id>_memory.json` (copy `chamber_memory.json`) plus a 16x16 texture `textures/item/<id>_memory.png`.
- Scene ids must match exactly; a missing scene just logs and plays nothing.
Op preview needs no item.

## Complete example
`data/hogwartsmagic/memories/example_duel.json` - a dark courtyard duel (parses and validates).

```json
{
  "title": "The Courtyard Duel",
  "length": "20s",
  "radius": 30,
  "set": {
    "fills": [{ "block": "minecraft:stone_bricks", "from": [-14, -1, -14], "to": [14, -1, 14] }],
    "disks": [{ "block": "minecraft:polished_andesite", "center": [0, -1, 0], "radius": 4 }],
    "time_of_day": 18000
  },
  "actors": [
    {
      "id": "harry", "type": "player", "name": "Harry",
      "skin": { "texture": "student_gryffindor" },
      "items": { "mainhand": "hogwartsmagic:harry_potter_wand" },
      "path": [{ "t": 0, "pos": [-5, 0, 0] }, { "t": "3s", "pos": [-3, 0, 0], "ease": "out" }],
      "look": [{ "t": 0, "yaw": -90, "pitch": 0 }],
      "poses": [{ "t": "3s", "name": "wand_point", "blend": 6 }],
      "actions": [{ "t": "6s", "do": "cast", "spell": "stupefy", "target": "actor:death_eater" }]
    },
    {
      "id": "death_eater", "type": "player", "name": "Death Eater",
      "skin": { "texture": "death_eater" },
      "path": [{ "t": 0, "pos": [5, 0, 0] }],
      "look": [{ "t": 0, "yaw": 90, "pitch": 0 }],
      "limbs": { "right_arm": { "rot": [{ "t": "5s", "v": [0, 0, 0] }, { "t": "6s", "v": [-70, 0, 0] }] } },
      "actions": [
        { "t": "6.5s", "do": "hurt" },
        { "t": "7s", "do": "die" }
      ]
    },
    {
      "id": "troll", "type": "hogwartsmagic:troll",
      "path": [{ "t": 0, "pos": [0, 0, 10] }],
      "look": [{ "t": 0, "yaw": 180 }],
      "anims": [{ "t": 0, "name": "IDLE" }, { "t": "8s", "name": "ROAR", "blend": 6 }]
    }
  ],
  "events": [
    { "t": "5s", "kind": "sound", "id": "minecraft:entity.lightning_bolt.thunder", "volume": 0.6 },
    { "t": "7s", "kind": "particles", "id": "minecraft:poof", "at": [5, 1, 0], "count": 30, "spread": [0.5, 0.8, 0.5] },
    { "t": "9s", "kind": "effect", "id": "lightning", "at": [0, 0, 6] },
    { "t": "10s", "kind": "block", "id": "minecraft:lantern", "at": [0, 0, 3] }
  ],
  "words": [
    { "t": "1s", "text": "Hogwarts, the night it ended.", "narration": true, "duration": "3s" },
    { "t": "5s", "by": "harry", "text": "Stupefy!" }
  ],
  "camera": [
    { "t": 0, "pos": [-12, 6, -12], "look_at": [0, 1, 0], "fov": 80 },
    { "t": "3.5s", "pos": [-8, 3, -10], "look_at": [0, 1.5, 0], "fov": 75 },
    { "t": "4s", "pos": [-3, 1.6, -2], "look_at": "actor:harry", "fov": 55, "cut": true },
    { "t": "6.5s", "pos": [-1, 1.7, 3], "look_at": "actor:death_eater", "fov": 50 },
    { "t": "10s", "pos": [0, 2, 12], "look_at": "actor:troll", "fov": 85, "cut": true },
    { "t": "20s", "pos": [0, 2, 8], "look_at": "actor:troll", "fov": 60 }
  ]
}
```
