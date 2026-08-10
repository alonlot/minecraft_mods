# Giant (Grawp) — design

Date: 2026-08-10

A film-accurate Harry Potter giant: Grawp, Hagrid's half-brother from the
Forbidden Forest. Neutral until provoked, then a wrecking ball — it swipes,
kicks blocks apart, and grabs victims out of the world to hurl them.

## Entity

`hogwartsmagic:giant`, `MobCategory.MONSTER`, extends `Monster` but neutral by
default (see Temperament).

| Attribute | Value |
|---|---|
| Hitbox | 2.2 W × 5.5 H |
| Max health | 250 |
| Movement speed | 0.19 (×1.35 when raging) |
| Attack damage | 14 |
| Follow range | 40 |
| Knockback resistance | 0.95 |
| Step height | 2.0 |

The model is built 111.5 px tall and rendered at `MODEL_SCALE = 0.79`, which
puts the rendered height at the registered 5.5 blocks.

## Appearance

Built in Blockbench (`blockbench_models/giant.bbmodel`, `modded_entity` format),
exported through the Modded Entity codec into `GiantModel`.

Large rounded skull with heavy brow, small close-set eyes, a broad squashed
upturned nose, a jutting jaw and thick lower lip, small round ears. Matted dark
hair with a fringe and side tufts. Enormous pale mottled torso with a sagging
belly, a ragged fur pelt slung over one shoulder and crossing the chest, and a
hide loincloth. Very long thick arms hanging to the knees with huge fists.
Short thick legs and big bare feet.

## Temperament

Neutral. The giant wanders, mumbles, and watches players. An anger latch
(`DATA_ANGRY` + a 600-tick timer, refreshed on each provocation) flips when:

- it is damaged by a living entity, or
- a player stays within 4 blocks for more than 3 seconds.

Giants spawned by egg or command are persistent; naturally spawned ones despawn
normally so they do not accumulate across a world.

While angry it targets players, wizards, students, and **any other `Mob`**
except other giants — it fights animals and monsters alike. Below 35% health a
one-way rage latch raises its speed and damage and biases it toward the grab.

## Attack states

Server-authoritative synched byte + state tick, following `TrollEntity`'s shape.
Movement locks during every committed state.

| State | Duration | Behaviour |
|---|---|---|
| `ROAR` | 40 t | One-time on first engagement. Nausea pulse and knockback within 8 blocks. |
| `SWIPE` | 26 t | Strike at t=12. 100° front cone, 6.0 reach, heavy sideways knockback. |
| `KICK` | 30 t | Strike at t=14. Damages entities in front and smashes blocks. |
| `GRAB` | 14 t | Windup; at the end, seizes a victim within 5.0 in front. |
| `THROW` | 18 t | Victim is locked to the fist, swept overhead, released at t=8. |
| `STAGGER` | 16 t | Triggered by a single uncommitted hit ≥ 14 damage. |

### Grab and throw

One motion, no hold phase and no escape window. On the `GRAB` strike frame the
nearest valid victim in front is captured (`heldVictimId`, synched so the client
can position it). Through `THROW` the victim's position is set each tick to the
fist's world position, derived from the same arm angles the animation uses. At
t=8 the victim is released with velocity `forward × 1.9 + up × 0.85`, takes
`ATTACK_DAMAGE × 1.6`, and eats whatever the landing costs. The upward component
is capped so the arc is dramatic but survivable from full health.

Works on players and mobs. Victims are dropped early (not thrown) if the giant
dies or is staggered mid-throw.

### Block kicking

On the `KICK` strike frame, every block in a 5 × 3 × 5 volume centred 3.5 blocks
in front of the giant at foot level is tested. A block breaks when **all** hold:

- it is **not** in `minecraft:mineable/pickaxe`,
- destroy speed ≥ 0 and < 3.5 (so wooden doors, at exactly 3.0, give way),
- it is not air, not liquid, and has no block entity.

That rule spares stone, cobble, deepslate, ore, iron blocks and obsidian by
construction, while dirt, grass, sand, logs, planks, leaves, wool, glass,
fences, doors and crops shatter. Blocks drop normally. Up to 4 of the broken
blocks launch as `FallingBlockEntity` debris on an outward arc. The whole
routine is gated on the `mobGriefing` gamerule.

## Sounds

Eleven clips generated through the ElevenLabs SFX API and converted to mono OGG at
44.1 kHz, under `assets/hogwartsmagic/sounds/giant/`:

`ambient1`, `ambient2`, `angry`, `roar`, `step`, `swipe`, `kick_smash`, `grab`,
`throw`, `hurt`, `death`.

`step` is played from the walk cycle's foot-plant frames.

## Animations

Keyframe clips exported from Blockbench into `GiantAnimations`:

`IDLE` (breathing sway, head loll, fingers flexing), `WALK` (wide lumbering
gait, counter-swinging arms, torso rock), `CHARGE` (hunched, faster, arms
pumping), `ROAR`, `SWIPE`, `KICK`, `GRAB`, `THROW`, `STAGGER`.

Selection in `GiantModel#setupAnim` uses if/else on the state ordinal, never a
`switch` on the enum — the synthetic switch-map helper has been observed to fail
to load in this mod's dev environment.

## Spawning

- `giant_spawn_egg` in the creative tab.
- Rare natural spawns in the mod's Forbidden Forest biome plus dark forest and
  both old growth taigas, via a biome modifier: weight 2, group size 1.

## Files

New: `entity/GiantEntity`, `client/model/GiantModel`, `client/model/GiantAnimations`,
`client/renderer/GiantRenderer`, `blockbench_models/giant.bbmodel`,
`textures/entity/giant.png`, 11 OGGs, `data/.../biome_modifier/giant_spawns.json`.

Edited: `ModEntities`, `ModEntityAttributes`, `ModSpawnPlacements`,
`ClientModEvents`, `ExampleMod` (spawn egg), `ModSounds`, `sounds.json`,
`en_us.json`, `HogwartsGuideScreen`.
