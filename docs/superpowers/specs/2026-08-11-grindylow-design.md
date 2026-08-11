# Grindylow — design

Date: 2026-08-11

A second inhabitant for the Black Lake, and a deliberate contrast to the
merpeople who keep them. Where a merperson warns you, duels you at spear reach
and lets you leave, a grindylow does none of that.

## Which grindylow

Built to the **Fantastic Beasts concept art**, not the book's one-line
description. That design decides the whole silhouette:

- Olive-khaki, wet-looking, mottled — nothing about it saturated or clean.
- A head far too big for the body, with the mouth as the dominant feature: a
  wide gape full of long needle teeth.
- Eyes set **wide and high on the sides** of the skull, green, and lit.
- Curved spines swept **back** off the skull rather than upright horns — three a
  side plus two on the crown.
- Two thin arms ending in long clawed fingers.
- **Six curling tentacles where legs ought to be.** This is the thing that makes
  it read as a grindylow from across the water and not as a small goblin.

`blockbench_models/grindylow.bbmodel`, 45 cubes on a 128×64 sheet, rendered at
0.7 so it is small enough to hide in a weed bed.

## It is a trap, not a fighter

This is the whole design, and everything else follows from it.

| | Merperson | Grindylow |
|---|---|---|
| Warning | Rears, flares, screeches, waits 5s | None |
| Numbers | 2–4, a leader | Whatever is in the weed |
| Attack | Spear thrust, real damage | **Takes hold and does not let go** |
| Health | 30 | 8 |
| Damage | 7 | 2 |
| Getting away | Leave its territory | Hit the thing holding you |

The attack is **denial, not damage**. Reaching you, it latches on: plants itself
on your legs, stops navigating entirely, halves your horizontal movement, drags
you down, and takes **four air per tick** on top of vanilla's one. Two damage a
second is almost incidental. What kills you is the clock.

Multiple grindylows stack — each one attached adds its own drag and its own air
drain, which is what turns a nest into a genuine emergency.

**The escape is the canon one.** Their fingers are long and strong and
*brittle*: any hit at all, from anything, breaks the grip and puts a 30-tick
cooldown on that one re-seizing. Harry breaks grindylow fingers in Goblet of
Fire and that is exactly the intended play — don't thrash, turn, hit the thing
holding you, deal with the next one. A grip also lapses on its own after ten
seconds, so a player with no weapon at all is not simply dead.

## Lurking

A synched flag. While it is set the grindylow does nothing whatsoever: no
navigation, velocity damped to nothing, no ambient sound, no head tracking, and
its tentacles ball up tight.

The targeting goal still runs while it lurks — useful, because it does the "can
I actually see a player" work — but its result is **gated**: a target inside 4.5
blocks wakes it, and anything further away is discarded and navigation stopped.
Without that gate a sleeper would path across the village at the first sight of
anyone and the ambush would be spoiled before it started.

Waking is one-way. It shrieks, the lunge clip fires, and it comes.

## The nest at the hoard

Five grindylows lie dormant in the weed in an arc around the merfolk hoard.
They are **baked into the structure template**, not left to a spawn cycle — the
ambush is a property of the place and always happens.

That needed the structure writer taught to emit an `entities` list, which meant
adding `TAG_FLOAT` and `TAG_DOUBLE` to the minimal NBT writer in
`generate_biome_structures.py`. Each entry carries `pos` (three doubles),
`blockPos` (three ints) and an `nbt` compound with the entity id plus
`Lurking: 1b` and `PersistenceRequired: 1b`. The template is round-trip parsed
and type-checked after every build.

Opening the hoard chest calls `GrindylowEntity#rouseNest` alongside
`MerpersonEntity#raiseAlarm`, so the merpeople and the nest come up together.

They can also be found free-swimming: weight 24 in the deep Black Lake and 18 in
the shallows, in groups of 2–4.

## Animation

Six rotation-only clips — `lurk`, `drift`, `lunge`, `clutch`, `hurt`, `death` —
driving the head, jaw, arms and spines. The spine clips are generated in a loop
with a per-spine splay so the crown flares rather than moving as one plate.

**The tentacles are solved in Java**, for the same reason the merperson's tail
is: how tightly they are drawn in *is* the read on what the creature is doing —
balled tight while lurking, streaming out behind it while swimming, splayed as
it lunges, clenched while it has hold of you — and a fixed clip cannot know
which of those is happening. Each of the six three-link chains gets a curl
weighted by where it sits in the ring, plus a travelling wave phase-offset by
ring angle so six identical limbs never move like one comb.

## Sounds

Six through the ElevenLabs SFX API into `sounds/grindylow/`: `ambient`,
`shriek`, `grab`, `snap`, `hurt`, `death`. All of them small, thin and wet —
nothing deep or heavy. `shriek` is the one that matters: it is what a nest makes
when the hoard opens, and it has to sound like being in trouble.

## The eyes are the tell

The emissive layer is not decoration. A lurking grindylow is a motionless olive
lump in a weed bed and no player will pick it out by its outline — but two green
points of light low in the weed is something you can learn to spot before you
touch the chest. It is the only warning the creature gives, and it is given
before the encounter starts rather than during it.

## Files

**New:** `blockbench_models/grindylow.bbmodel`, `entity/GrindylowEntity`,
`client/model/GrindylowModel`, `client/model/GrindylowAnimations`,
`client/renderer/GrindylowRenderer`,
`build_scripts/generate_grindylow_texture.py`,
`build_scripts/generate_grindylow_sounds.py`, two entity textures, six OGGs, the
grindylow loot table.

**Edited:** `ModEntities`, `ModSounds`, `ModEntityAttributes`,
`ModSpawnPlacements`, `ClientModEvents`, `ExampleMod`, `MerfolkEvents`,
`HogwartsGuideScreen`, `generate_biome_structures.py`, `sounds.json`,
`en_us.json`, both Black Lake biome JSONs, `merfolk_village.nbt`.
