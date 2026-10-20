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
| `fog_far` | int blocks | none | opt-in far view: without it the void fog closes in just past `radius` (start radius-8, end radius+2), so nothing beyond ~radius shows. With it the fog is a soft haze from 75% of `fog_far` to `fog_far` (`PensieveTimeline.farFog`), growing and collapsing with the reveal like the default, and never past the viewer's own render-distance fog (12 chunks = 192 blocks). 1 to 512. For a distant backdrop; `lake_patronus` uses 200 for Hogwarts across the lake. The edge fragments still sit at `radius` |
| `set` | object | empty set | below |
| `poses` | object | none | custom poses, below |
| `actors` | array | none | below |
| `events` | array | none | below |
| `words` | array | none | below |
| `camera` | array | none | empty = viewer looks freely; otherwise directed |
| `next` | string | none | id of another memory scene (lowercase `[a-z0-9_]+`, not this scene's own id) to chain into when this one ends; whether it exists is checked when the hand-over happens, not at validation. A chained scene fades to black over its last 20 ticks (instead of the exit), the viewer is moved to the next scene, and it fades in from black over 20 ticks with its own actors, sounds and time of day; nothing of the last scene carries over except music still fading out over 20 ticks |
| `end_action` | string | none | only `cave_map`: when the scene ends at `length` (not when a viewer leaves early, is yanked or the basin breaks), each viewer gets `CaveMemoryItem.grantCaveMap` once, server-side, after they are moved home, so the Cave Map lands in their real inventory (with the drink's vision lines and nausea; "The memory is too faint..." and no map when no unlooted cave is near, or outside the Overworld). `/pensieve preview` runs it too. Used by `locket_cave` (whose 80 Inferi and Fiendfyre, `locket_cave.py`, are the worked example of a crowd: ring slots round the hero, straight radial paths, everything timed off the fire's wavefront, `write_scene(..., width=400)` to stay under the size cap) |

### `set`
| key | type | default | notes |
|---|---|---|---|
| `templates` | `[{id, at:[x,y,z]}]` | none | `.nbt` structure templates (`data/hogwartsmagic/structure/*.nbt`, id `hogwartsmagic:<name>`); the template's corner is placed at `at`. Sizes (x,y,z): see below. Shipped: black_lake_ruin, chamber_hall, death_eater_camp, diagon_alley, fen_gibbet, goblin_vault, hogwarts_ruin_camp, horntail_nest, locket_cave, merfolk_village, willow_clearing |
| `schematics` | `[{id, at:[x,y,z], rotation?}]` | none | a bundled Sponge schematic; only `mini_hogwarts` (96 x 109 x 145, about 36,400 non-air blocks, its own rocky hill included; `worldgen/MiniHogwartsSchematic`). `rotation` 0/90/180/270 clockwise from above (default 0) turns positions and block states; the rotated footprint (145 x 109 x 96 at 90/270) has its min corner at `at`. Air and command blocks are skipped; cells beyond the radius are cropped; cleared with the set. Only the anchor is validated, so fit anchor + footprint inside +-radius yourself. Generators: `schematic_grid`/`rotate_xz` in `scene_common.py` give the same cells and rotation as `SetBuilder` |
| `fills` | `[{block, from:[x,y,z], to:[x,y,z]}]` | none | solid box; max 2,000,000 blocks each; inside the radius |
| `disks` | `[{block, center:[x,y,z], radius}]` | none | flat horizontal disc at `center` y; inside the radius |
| `features` | `[{id, at:[x,y,z]}]` | none | configured worldgen features (e.g. trees), id like `minecraft:oak`; only the anchor is checked |
| `time_of_day` | number | 18000 | day time at the set: 0 dawn, 6000 noon, 12000 dusk, 18000 midnight |
| `build_ticks` | int ticks | 60 | how long the dive waits while the set builds |

Later entries overwrite earlier ones where they overlap (templates first, then schematics, then fills, then disks), but a template or schematic does not carve holes in a fill beneath it: their air is skipped. A fill can therefore re-block cells of a schematic (`lake_patronus` lights Hogwarts' windows with single-cell glowstone fills over its black glass, picked in the generator).
Block ids are namespaced (`minecraft:stone_bricks`).

**Extent trap:** the validator checks only a template's or feature's *anchor* against the radius. `SetBuilder` then silently crops every block more than `radius` from the origin (X or Z), with no error. So make sure anchor + template size fits inside +-radius, and raise `radius` (max 200) for big templates. Template sizes (x,y,z) from each `.nbt`'s `size` tag: black_lake_ruin 15x9x15, chamber_hall 51x32x101, death_eater_camp 27x14x27, diagon_alley 233x45x49 (wider than radius 200 even when centred: crops), fen_gibbet 10x13x10, goblin_vault 17x8x11, hogwarts_ruin_camp 39x17x29, horntail_nest 14x5x14, locket_cave 64x42x64, merfolk_village 25x14x25, willow_clearing 23x3x23. For a new template, read it: `python -c "import gzip,struct;d=gzip.open('f.nbt').read();i=d.find(b'\x09\x00\x04size')+7;print(struct.unpack('>3i',d[i+5:i+17]))"`. Centre a template with `at` = minus half its x/z size.

**Physics trap (unsupported gravity blocks and open water edges):** the set is placed with no neighbour updates and the server clears the block and fluid ticks each placement schedules, so nothing falls or flows while the memory plays. Do not lean on that. Gravel, sand or concrete powder over nothing (template air counts as nothing: air in a template is skipped, and below the set is void) collapses into falling blocks the moment anything updates it, and water beside or above an empty cell, or at the radius edge, drains into the void. Put a solid layer (stone, deepslate) under every seabed and lake bed, and close every water edge with rock or a `minecraft:barrier` rim. In a generator, build a `world` dict of the set (cell to block id) and add `problems += physics_problems(world, R)` from `scene_common.py`: it lists unsupported gravity blocks and open water edges, and `unsupported_gravity(world)` gives the cells that need a block under them (`locket_cave.py` fills them with deepslate).

### `actors[]`
Keys: `id` (required, unique), `type` (required), `skin`, `name`, `items`, `path`, `look`, `limbs`, `poses`, `anims`, `actions`.

- `type`: `"player"` (a skinned humanoid) or a namespaced entity id (`hogwartsmagic:troll`, `minecraft:zombie`). Mobs have no AI, no gravity, no collision.
- `hogwartsmagic:memory_letter`: a flat Hogwarts-letter envelope you path like any actor. `look` pitch 90 lays it flat; it flutters (roll and wobble) while its path moves it and lies still when the path holds.
- `skin` (player only): `{ "texture": "<name>" }` a bundled skin, or `{ "username": "Notch" }` fetched in the background (falls back to the default skin when offline), plus optional `"slim": true` for the 3-px arm model.
- `skin.viewer`: `{ "viewer": true }` renders the watching player's own skin; it cannot be combined with `texture` or `username`.
- `scale`: actor size multiplier, 0.25 to 4 (default 1).
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
| `ignite` | - | sets the actor on fire: the vanilla fire overlay (the actor is never ticked, so it burns until it dies, vanishes or `appear`s, which puts it out), with a burst of flame, embers and smoke and a fire-charge whoosh |
| `hold` | `item` | sets the main-hand item |
| `equip` | `slot`, `item` | puts `item` in `slot` (`head`, `mainhand`, `offhand`, `chest`, `legs`, `feet`); `minecraft:air` clears it. Both keys required. An armour item in `head` is worn (e.g. `hogwartsmagic:sorting_hat`). The slot's item is the last `equip` (or, for `mainhand`, `hold`) at or before the scene time, else the actor's `items`, so a late joiner sees it already worn (`pensieve/scene/EquipTimeline`) |
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
| `expecto_patronum` | the full Patronus cast: a silver-white (#DDEEFF), full-bright stream pours from the wand tip toward `target` (10 blocks ahead if none) for 2 s (40 ticks): a widening cone of end-rod mist round a bright core, a flash and glow at the tip. The stream front rushes out over 6 ticks and thins over the last 8; it reaches at most 40 blocks. Plays the real Expecto Patronum sound at the caster. The stag is a separate actor (`hogwartsmagic:patronus`, form 0) |
| `patronus_wisp` | a weak, half-managed Patronus (memory-only: not a real spell id), no stag: a puff of silver mist bursts from the wand tip and spreads over 6 ticks into a hazy disc of glowing mist about 3 blocks across, facing the target and 4-6 blocks ahead (the aim distance, held to 4-6; 5 ahead if no target); it holds, flickering, for 2 s (40 ticks), then breaks up, drifting outward and down, and fades over 1 s (20 ticks): 66 ticks in all. Far smaller than `expecto_patronum`. Same sound at volume 0.5, pitch 0.85. It moves nothing by itself: path the Dementors back yourself (`lake_patronus.py` drives the 4 nearest back ~0.3 s after the cast with `RECOIL`) |

A beam travels 1.55 blocks/tick, so the hit lands about distance/1.55 ticks after the cast (20 blocks = 13 ticks): time the `hurt` accordingly.

| `fiendfyre` | the cursed fire leaving the wand: a flash and a ball of flame at the tip, then a writhing, widening gout of flame, lava sparks and black smoke rushing toward `target` (8 ahead with none, at most 14) over 5 ticks, roaring for 1.5 s (30 ticks) and guttering out over the last 8. Plays the real Fiendfyre sound. The fire filling the place is the `fiendfyre_rings` effect; the serpents are `hogwartsmagic:fiendfyre_serpent` actors (a flaming snake drawn from its own movement: path it with `linear` keys every 0.2 s, faster = a longer body, hide it with `vanish` at t 0 and `appear` it once its path sits at the wand) |

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
Common keys: `t`, `kind`, `id`, `at`, `volume`, `pitch`, `count`, `spread`, `duration`, `yaw` (effects only). `kind` is required, `id` is required for all kinds. `music`, `voice` and `sfx` take only `t`, `kind`, `id`, `volume`.
| kind | id | uses | effect |
|---|---|---|---|
| `block` | block id | `at:[x,y,z]` | the server places that block at `t` (a door slams, a torch lights). Removed with the set |
| `sound` | sound id (`minecraft:entity.lightning_bolt.thunder`, `hogwartsmagic:...`) | `volume` 1, `pitch` 1, optional `at` (none = heard in your own ears) | plays once at `t` |
| `particles` | plain particle id (`minecraft:poof`, `minecraft:end_rod`) | `at`, `count` 1 (max 500), `spread:[x,y,z]` box half-size | a burst; parameterised particles (dust, block) cannot be used |
| `effect` | `summoning_ritual`, `lightning`, `dark_mark`, `grey_edges`, `patronus_light`, `fiendfyre_rings`, `eyes_open`, `eyes_close`, `letter_flood` | `at`, `duration` (`letter_flood` also `yaw`, `count`, `spread`) | scripted set-pieces below |
| `music` | sound id (`hogwartsmagic:pensieve.music.<file>`) | `volume` 1 (0 to 4) | a soundtrack cue, played whole from `t` |
| `voice` | sound id (`hogwartsmagic:pensieve.voice.<file>`) | `volume` 1 (0 to 4) | a spoken line, played whole from `t` |
| `sfx` | sound id (`hogwartsmagic:pensieve.sfx.<file>`) | `volume` 1 (0 to 4) | a non-positional sound effect on `SoundSource.AMBIENT`, full pitch, no echo, no ducking; played whole from `t`, stops at once when the memory ends |

Effects: `lightning` - one visual-only bolt with thunder; `eyes_open` / `eyes_close` - the viewer's eyelids, screen-only (`at` ignored), default 2 s (40 ticks): two black lids each covering up to half the screen with a soft 24 px edge; `eyes_open` starts fully shut, stays shut for 30% of `duration`, half-opens for a blink (peak at 45%), shuts again by 55%, then opens fully by the end (ease-out) and is gone after `duration`; `eyes_close` starts open and shuts by the end of `duration` (ease-in) and then stays shut until the scene ends (it never expires); the most-closed running lid wins (`pensieve/scene/Eyelids`). An `eyes_open` at `t` 0 (or less) also holds the lids fully shut for the whole set build and until it starts, so such a memory opens in darkness and the half-built set never shows: put one at `t: 0` to begin a memory from black (`Eyelids.opensShut`, `PensieveClientState.lidsHeldShut`). Spoken (`by`) lines are hidden under shut lids and a chain's fade to black; narration still shows over black; `summoning_ritual` - the three beam columns, smoke and a bolt every second, default 5 s; `dark_mark` - a Dark Mark that rises 18 blocks and fades, default 5 s; `grey_edges` - the Basilisk's stare from inside: the real gaze overlay's grey closes in from the screen edges over 2.5 s (the real charge) and drains over the last 1.2 s of `duration` (default 3 s), so end it where the victim looks away; screen-only, `at` is ignored. Give it a `duration` over 1.2 s (24 ticks) or nothing shows, and at least ~3.7 s (74 ticks: the 50-tick rise plus the 24-tick drain) for it to reach full grey; a shorter one peaks partway (`EffectVisuals.GreyEdges`). `patronus_light` - a silver-white burst of light at `at`, default 4 s (80 ticks): a ring of glowing particles on the ground plane plus a faint sphere grow from radius 0 to 22 blocks over the first 60% of `duration` (ease-out: fast, then slowing), then thin out and fade over the last 40%; a dense white spray at the centre; a flash at the start; and a soft white screen wash (alpha peaking 0.35 at 0.4 s = 8 ticks, gone by 2 s = 40 ticks, or by the end of a shorter `duration`) for viewers whose camera is within 40 blocks of `at`. Plays the real Expecto Patronum sound once at `at` (volume 1.2) when it starts, not for a late joiner. Visual only. No duration floor (a short one cuts the ring early and fades the wash out by its end). `fiendfyre_rings` - Fiendfyre filling the place in circles, centred on `at` (put `at` y at the floor block, -1): while the age is under `duration` (default 80 ticks) a new wave is born every 16 ticks, from age 0; each is a writhing wall of flame (about 2.4 blocks tall, the first 3.2) that starts 1.5 blocks out, rolls outward 0.25 blocks a tick (5 a second) and burns out at 22 blocks, thinning over its last third (82 ticks a wave). A ring of fire wraps the centre while waves are born. Fiendfyre sound and a ghast roar at the start, a blaze roar per wave. Particles only: no light (lay `minecraft:light` block events along the first wave, as `locket_cave.py` does) and nothing burns by itself, so time each actor's `ignite`: the first wave reaches distance d from `at` at `t + (d - 1.5) / 5` seconds (`EffectVisuals.FiendfyreRings`). A late joiner skips old events but joins a still-running effect. `letter_flood` - a swarm of Hogwarts-letter envelopes pouring out of a fireplace, whirling round a room and settling on the floor. It needs `at` (the fireplace mouth, with y at the floor top, the block above the floor layer) and `yaw` (the direction into the room, Minecraft convention; required). Optional keys: `count` (letters, 1 to 400, default 160); `spread` (the room half-size `[hx, hy, hz]`, each 0.5 to 12, default `[3, 2.5, 3]`); `duration` (emission time in ticks, default 120). The room box is the axis-aligned box centred on `at + forward x hz`, with half-sizes `hx, hy, hz` in world X/Y/Z rotated by yaw to the nearest 90 degrees; the floor is `at.y`; it starts at the fireplace wall, so no letter flies behind it or outside the room. Behaviour: letters leave `at` evenly over the first 60% of `duration`; they launch within 60 degrees of `yaw` and 10 to 45 degrees upward at 0.35 to 0.6 blocks per tick; they fly with gravity 0.015 per tick squared, drag 0.97 and a swirl round the box's vertical centre axis that calms after the emission ends; they bounce off the walls with 0.4 damping; they come to rest on the floor (y = `at.y`) once their speed is under 0.03 at floor contact (or after 6 bounces), then lie flat at a random yaw until the scene ends. Sound when it starts (not for a late joiner): `minecraft:block.fire.extinguish` and `minecraft:entity.breeze.wind_burst` at `at`, then `minecraft:item.book.page_turn` every 4 ticks at random volume 0.3 to 0.6 while letters fly. The letters are client-only `hogwartsmagic:memory_letter` entities run by `pensieve/scene/LetterFlood` (deterministic: a late joiner, including one arriving after the emission, sees the same swarm as from the start) and removed when the memory ends. Two floods in one scene differ (the seed mixes in `at` and `t`).

**Music and voice cues** (`client/pensieve/MemoryAudio`). `sound` events go through `MemorySounds`: volume x0.75, pitch x0.92, an echo 6 ticks later, positioned in the world, never stopped. That is wrong for a soundtrack or a spoken line, so use `music`/`voice` (or `sfx` for effects) for those:
- Both are non-positional (`Attenuation.NONE`, no `at`), at full pitch and volume, with no echo. `music` plays on `SoundSource.MUSIC`, `voice` on `SoundSource.VOICE`. Register the OGG as `pensieve.music.<file>` / `pensieve.voice.<file>` in `ModSounds` and `sounds.json` (`"stream": true` for music, no subtitle); `id` is checked like a `sound` id.
- `sfx` is the same but on `SoundSource.AMBIENT` (register as `pensieve.sfx.<file>`). Like `voice` it is skipped when `t` is more than 5 ticks past for a late joiner, and it stops at once when the memory ends (`pensieve/scene/CueKinds.stopsAtEnd`: true for `voice` and `sfx`, music fades). It neither ducks music nor is ducked.
- Ducking: while any voice plays, every music instance eases to 45% volume (0.08 per tick) and back after it ends.
- Ending: when the memory ends for any reason (end, leave, yank, basin broken, death, disconnect, a new memory starting) voices stop at once and music fades out over 20 ticks. While memory music plays, vanilla background music is held off.
- Cues play the whole file from `t`: bake fades into the file, and overlap consecutive tracks by about 2 s to crossfade.
- Late joiners: Minecraft cannot seek into an OGG, so a cue whose `t` is already past (more than 5 ticks) when the viewer joins is skipped, like any one-shot event. A viewer who joins mid-track hears silence until the next cue.


### `words[]`
`{ t, by?, text, narration?, duration?, size? }`. With `by` (an actor id) the line shows as `<name> text` at the bottom left for 8 s; without `by`, plain text. `"narration": true` shows centred silver italic text that fades in and out over `duration` (default 80 ticks). `size` (1 to 4, default 1; narration only) scales the narration text about its centre, e.g. 3 for a large title; the wrap width shrinks to match. Never written to real chat.

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
Shipped (`texture` value): `hooded_figure`, `regulus`, `student_gryffindor`, `student_slytherin`, `death_eater`, `voldemort`, `sirius` (long brown hair, beard, brown coat over a dark red shirt), `hermione`, `harry` (all three downloaded from NovaSkin, not generated: the generator lists them and checks the PNGs exist; classic 4-px arms, no `slim`).
To add one: put a 64x64 modern-layout PNG at `src/main/resources/assets/hogwartsmagic/textures/memory_skin/<name>.png` and add `"<name>"` to the array in `src/main/resources/data/hogwartsmagic/memories/_skins.json` (the validator and `SceneDataTest` both read that list, and the test fails if a listed PNG is missing). `build_scripts/generate_memory_skins.py` is the generator of the shipped ones (needs Pillow); add a new entry there if you want it reproducible, it rewrites the list too. A `username` skin needs no files.

## The loop
1. Client closed? Edit nothing in `build/` and **never run gradle while the dev client is running**: gradle rewrites `build/sourcesSets/main` and the live client throws `ClassNotFoundException`. Check with `tasklist | findstr java` (the GradleDaemon is fine; a Minecraft java process is not).
2. Set the config `pensieveDevSceneDir` (`run/config/hogwartsmagic-common.toml`) to a folder; relative paths are against the game run dir `run/` (so `../src/main/resources/data/hogwartsmagic/memories`). `/pensieve preview` then reads `<dir>/<id>.json` fresh from disk each time, so no rebuild between edits.
3. Write the scene to `src/main/resources/data/hogwartsmagic/memories/<id>.json`. For anything bigger than a few events, write a Python generator in `build_scripts/pensieve_scenes/<id>.py` (the shipped ones are there, with `scene_common.py` for template reading and the JSON layout; run them from the repo root) and commit it with the scene. Files starting with `_` are not scenes. `<id>` is lowercase with underscores.
4. In the dev client (op): `/pensieve preview <id>` plays it from the start with no basin. `/pensieve validate [id]` only checks scenes loaded from the registry (built into the game), NOT dev-dir files: during the edit loop, `preview` is the validator (it validates the fresh file and reports problems), and validate is for shipped scenes after a build. `/pensieve list` shows loaded ids. `/pensieve leave` or the **X** key leaves a memory.
5. Screenshot, read the chat for part/animation errors, edit the JSON, then leave (press **X**) and run `/pensieve preview <id>` again; a preview cannot be restarted while you are inside the memory.
6. With the client closed: `./gradlew test --tests "*SceneDataTest*"` parses and validates every shipped scene (`./gradlew test` runs the lot). It must pass before committing.
7. Commit the scene (and any new skin) - and ask the user to push.

Size caps: scene JSON max 262,144 characters, scene id max 256 (a bigger scene is refused when sent to the client).

`SceneDataTest` is narrower than the game: its entity types are only the mod's own entities, `player`, and a fixed `VANILLA_MOBS` list in `SceneDataTest.java` (zombie, skeleton, villager, armor_stand, bat, spider, cave_spider, wither_skeleton, evoker, vindicator, witch, horse, wolf, cat, fox, sheep, cow, pig, chicken, squid, glow_squid, salmon, cod, lightning_bolt). Any other vanilla mob (even a valid one in game) fails the test until you add it to that list. Its `features` accept only `minecraft:` ids, and its `sounds`/`particles`/`blocks`/`items` accept any `minecraft:` id but only mod ids that are registered in source.

Validation rules to remember: ids are namespaced (`minecraft:`, `hogwartsmagic:`), `player` is the only bare entity id, spells are bare, skins are bare names from `_skins.json`; pose cue names must be a preset or a custom pose; a `cast` needs `spell`; actor ids must be unique; `words[].by`, `look_at` and `target` must name an existing actor.

## Giving a scene an item (so players can pour it)
Players get a scene through an item that implements `PensieveMemory`. Existing: `chamber_memory` -> scene `chamber`, `summoning_memory` -> scene `summoning` (both `StoryMemoryItem`, in `ExampleMod`), `patronus_memory` -> `lake_patronus` (`StoryMemoryItem`; given once, with a silver chat line, the first time a player learns Expecto Patronum: `MagicBookItem.grantPatronusMemory`), `cave_memory` -> `locket_cave` (`CaveMemoryItem`). For a new scene, only if the user wants an item:
- Register in `ExampleMod`: `ITEMS.register("<id>_memory", () -> new com.hogwartsmagic.pensieve.StoryMemoryItem(new Item.Properties(), "<scene id>"))`.
- Add it to the creative tab list next to `output.accept(SUMMONING_MEMORY.get())`.
- Add `item.hogwartsmagic.<id>_memory` to `lang/en_us.json`, and `models/item/<id>_memory.json` (copy `chamber_memory.json`) plus a 16x16 texture `textures/item/<id>_memory.png`.
- Scene ids must match exactly; a missing scene just logs and plays nothing.
Op preview needs no item.

## Complete example
An illustrative dark courtyard duel (not shipped as a file; save it under `data/hogwartsmagic/memories/` to try it). For a shipped, validated worked example read `data/hogwartsmagic/memories/summoning.json` and the script that generates it, `build_scripts/pensieve_scenes/summoning.py`.

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
