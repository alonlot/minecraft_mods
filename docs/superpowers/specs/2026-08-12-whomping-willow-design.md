# Whomping Willow — design

Date: 2026-08-12

The tree from the Hogwarts grounds: a huge, rooted, violently territorial willow
that looks like scenery until you walk under it. It cannot chase you — it never
takes a step — so the whole encounter is about range discipline. Step inside its
reach during a recovery window, step out during a wind-up.

It is also the mod's first buildable defence. A rare sapling grows one that is
*yours* and ignores you, and the knot at its base answers to a redstone signal,
so a grown willow is a living wall with a switch on it.

## Entity

`hogwartsmagic:whomping_willow`, `MobCategory.MONSTER`, extends `Monster`.
Dormant by default (see Temperament).

| Attribute | Value |
|---|---|
| Hitbox | 3.0 W × 9.0 H |
| Max health | 220 |
| Movement speed | 0.0 |
| Attack damage | 9.0 |
| Follow range | 24 |
| Knockback resistance | 1.0 |
| Armor | 8 |
| XP | 45 |

The model is built 180 px tall and rendered at `MODEL_SCALE = 0.80`, putting the
rendered height at the registered 9.0 blocks exactly.

Damage taken is scaled once, in `hurt`: fire ×1.6 (Incendio, Confringo and
Fiendfyre are the intended answer), axes ×1.35, projectiles ×0.6 — arrows stick
in bark, and archer-cheese is further discouraged by dormant regeneration.

**One AABB, no `PartEntity`.** Branch-tip hit volumes were considered and
rejected: `SpellBeamEntity` only damages `instanceof LivingEntity` but discards
the beam unconditionally, so every spell that clipped a part would fizzle *and*
be consumed. In a spell mod, a partially spell-proof boss is a worse bug than a
generous hitbox. A rooted tree never moves, so one static box fits the trunk
forever.

## Appearance

Geometry is **generated**, not sculpted — `build_scripts/generate_whomping_willow_model.py`
emits both `blockbench_models/whomping_willow.bbmodel` and the `createBodyLayer()`
body spliced into `WhompingWillowModel`. A tree is the one creature in this mod
where every number is `f(index)`: trunk taper, segment series, branch ring, leaf
scatter. The Giant needed Blockbench because there is no formula for a face; a
willow is nothing but formula.

An eight-ring nested trunk chain tapering 46 px to 18 px, gnarled with a
deterministic per-ring jitter, so a bend at the base compounds all the way to the
crown. Four long branches of five segments each, hung at two different trunk
heights rather than a flat cross, with a willow droop baked into their rest pose.
A hollow maw low on the trunk. Radial root buttresses. Leaf slabs whose outer
ring is eroded to alpha-0 in the texture — `HierarchicalModel` renders as
`entityCutoutNoCull`, so that buys a ragged canopy silhouette out of flat boxes.

A visible **knot** is painted on one compass side, chosen per tree.

## Temperament

Dormant. A dormant willow is a tree: branches drooped, no ambient sound, no
attacks, and — deliberately — no synched state ticking at all, so a stand of them
on the grounds costs nothing in packets.

It wakes when a valid victim stays within 9 blocks for 15 ticks, or instantly
when damaged. `WAKE` is a 30-tick shudder that deals nothing: a free hit for
whoever provoked it on purpose.

De-aggro is at 12 blocks — deliberately wider than the wake radius, so a player
pacing the boundary does not strobe it in and out of sleep. After 400 ticks with
nobody inside, it returns to `DORMANT` and regenerates 1 HP/second. That
regeneration exists to kill exactly one strategy: it cannot flee and cannot
close, so without it a bow from 40 blocks beats it for free.

A **grown** willow never targets its owner and skips them in its provocation
check entirely. It attacks everything else, including other players.

## Attack states

Server-authoritative synched byte + state tick, following `GiantEntity`'s shape.
The trunk yaw is frozen at spawn; which branch fires is chosen by bearing.

| State | Duration | Strike | Behaviour |
|---|---|---|---|
| `WAKE` | 30 | — | Shudder and leaf burst. Harmless. |
| `SWEEP` | 30 | p 0.30–0.70 | A branch scythes horizontally through an annulus 2.4–6.8, arc re-evaluated every tick of the swing. 9 damage. |
| `SLAM` | 34 | 16 | A branch rears vertically and hammers a 3.2 disc at 5.0 out. 14 damage, smashes blocks. |
| `ROOT_STOMP` | 40 | 22 | All four branches rear together; the roots heave a 3.6 disc centred on the trunk. 11 damage, heavy knockback. Exists so that standing at the trunk is not a solution. |
| `SWAT` | 26 | 11 | Flicks a broom rider out of the canopy. 8 damage. |
| `GRAB` | 18 | catch 10 | Reaches into 2.0–5.6. Whiffs if the target steps out — that is the teaching moment. |
| `SMASH` | 48 | 18, 32 | Holds the victim, hammers them into the ground twice for 10 each, then drops them. |
| `STAGGER` | 20 | — | Interrupt on a heavy blow. |
| `FROZEN` | — | — | Knot pressed or redstone-powered. Limp. |

The wind-ups are the fight, and each is a distinct silhouette: `SWEEP` cocks one
branch **sideways**, `SLAM` rears one **vertically** with a particle ring drawn
on the ground where it will land, `ROOT_STOMP` rears **all four**. A player who
cannot tell those three apart cannot play this encounter.

`SMASH` hammers the victim at the willow's own feet rather than throwing them
clear. The Giant can throw you away because that ends the fight; a willow that
flung you out of range would be ending the fight in your favour.

## The knot

One compass side carries the knot, picked at spawn and persisted.

**Press** — sneak and right-click within 2.5 blocks of the trunk, below y+2, and
within ±35° of the knot bearing. The tree goes limp until it takes damage or two
minutes pass. This is the book's trick and the only way past a wild one without
a fight.

**Redstone** — the entity samples the best neighbour signal at its own root
position each tick. Any power there holds it frozen for as long as the signal
lasts, overriding the timer and surviving damage.

Polling the entity's own base beats inventing a knot *block*: an entity cannot
receive redstone updates, a block at the trunk would have to be kept in sync with
spawn, death and despawn, and sampling works identically for wild and grown trees
with no worldgen coupling. A lever under the roots gives you a gate; a pressure
plate gives you a path.

## Growing your own

A wild willow rarely drops a **Willow Sapling**. Planted, it grows through four
ages on random ticks (bone meal accepted), and at full age checks for a clear
5×10×5 volume — if something is in the way it waits rather than growing into it.
On success it replaces itself with a willow that records the planter as owner.

The freeze mechanics are what make this usable rather than a hazard you regret:
press the knot or throw a lever and walk your friends through.

## Sounds

Generated through the ElevenLabs SFX pipeline into `sounds/whomping_willow/`:
`ambient` (timber creaking under load), `alert`, `whip` (a branch through the
air), `impact`, `groan`, `roots`, `hurt` (an axe into living wood), `death` (a
felling). A distinct sound fires at t=2 of each attack so the wind-up is
audible as well as visible.

## Animations

**No keyframe clips.** Motion is solved procedurally in `setupAnim`, following
`HungarianHorntailModel` and `GrindylowModel.solveTentacles`, both of which are
already fully hand-coded. A tree's motion is entirely wave-and-bend, which is
what math does well and keyframes do badly — and with Blockbench unavailable,
hand-typing `degreeVec` triples through an exporter convention that negates X and
Y, into a system that fails *silently* on a bone-name typo, is the least
verifiable thing available.

The damage arc and the visible branch are bound by shared `public static` pure
functions on the entity — `strikeReach(p)`, `branchTrail(p)`,
`branchWorldYaw(branch, plantedYaw)`, `branchHostY(branch)`, `holdLift(p)` —
called by both sides, exactly as `HungarianHorntailEntity.tailTrail` is. Damage
reach is 6.8 against a 7.25 visual reach, deliberately short, so the failure mode
is "that whistled past me" and never a phantom hit.

## Spawning

Rare natural spawns (weight 1–2, max 1) in the Forbidden Forest and vanilla
dark/old-growth forests, plus a `willow_clearing` jigsaw structure with the tree
baked into the NBT template so one landmark is guaranteed and findable.

## Files

New:
- `src/main/java/com/hogwartsmagic/entity/WhompingWillowEntity.java`
- `src/main/java/com/hogwartsmagic/client/model/WhompingWillowModel.java`
- `src/main/java/com/hogwartsmagic/client/renderer/WhompingWillowRenderer.java`
- `src/main/java/com/hogwartsmagic/block/WillowSaplingBlock.java`
- `build_scripts/generate_whomping_willow_model.py`
- `build_scripts/generate_whomping_willow_texture.py`
- `build_scripts/generate_whomping_willow_sounds.py`
- `blockbench_models/whomping_willow.bbmodel`
- textures, sounds, loot table, biome modifier + tag, structure set

Edited:
- `ModEntities`, `ModEntityAttributes`, `ModSpawnPlacements`, `ModSounds`
- `ExampleMod` (spawn egg, sapling item, creative tab)
- `ClientModEvents` (layer definition, renderer)
- `sounds.json`, `en_us.json`, `HogwartsGuideScreen`, `MOD_FEATURES_WIKI.md`
