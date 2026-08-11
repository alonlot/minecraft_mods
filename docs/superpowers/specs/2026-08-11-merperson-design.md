# Merpeople of the Black Lake — design

Date: 2026-08-11

A new inhabitant for `hogwartsmagic:black_lake`, which until now held nothing but
drowned, guardians and squid. Merpeople: a Blockbench model with a
procedurally-solved tail, seven keyframe clips, seven generated sounds, a
territorial pod AI, a wieldable spear, a merchieftainess variant, and a lakebed
village for them to live in.

## Which merpeople

The three varieties are not interchangeable and the Black Lake ones are
specific: **selkies**, the Scottish kind. Grey-green and wiry, wild dark-green
hair, yellow eyes, broken yellow teeth, ropes of lake pebbles round the neck,
crude spears. Not the Greek siren. Everything in the texture generator is aimed
at that — nothing about this creature is allowed to be pretty.

They are **Beings**, not beasts, and the behaviour follows from that. In Goblet
of Fire they let Harry take his hostage and swim away; they levelled spears at
him only when he tried to take a second. That is the entire AI in one sentence.

## Geometry

`blockbench_models/merperson.bbmodel`, Modded Entity format, 45 cubes on a
256×64 sheet. About three blocks from crown to fluke.

- **`head`**: wedge skull, heavy scowling brow, a short jutting muzzle set
  *below* the eyes rather than in front of them, upper and lower fangs tucked
  inside the closed jaw, gill slits, and two bulging `eye` cubes that protrude
  past the cheek so the eyes read in profile as well as head-on.
- **`fin_ear_left`/`right`**: webbed paddles sweeping six pixels clear of the
  skull. The strongest thing in the silhouette, and what says *not human* from
  distance.
- **`hair_*`**: three groups of long strands that drift when hovering and stream
  back when swimming.
- **`torso`**: chest, ribs, belly, clavicle ridge, the pebble necklace, and a
  long low dorsal fin down the spine.
- **`crown`**: a nacre shell crown, hidden in `setupAnim` for everyone except the
  merchieftainess.
- **`tail01`–`tail05` + `fluke`**: a nested chain tapering to a vertical
  two-lobed caudal fin.

An early pass buried the eyes: the muzzle was as wide as the skull, so the only
uncovered part of the face was a 1.3-pixel strip either side of it. The fix was
to narrow the muzzle and drop it below the eye line — which also gave the face
the deep-set, overhung look it wanted anyway.

## The tail is solved, not keyframed

A clip cannot make a tail beat harder when the animal sprints and go slack when
it hangs still, so `MerpersonModel` runs a travelling wave down the six-part
chain instead. Each segment lags the one in front by 0.86 rad and takes a rising
share of the amplitude, so the whip is at the tip.

Both the amplitude and the rate scale with the entity's own smoothed speed,
which is derived on each side from synced motion — no extra packet.

The wave is on **zRot**, not xRot. Each segment extends along its own local +Y,
so a rotation about Z sweeps it side to side; these are fish tails with a
vertical fluke, and they beat laterally, not like a dolphin. The resting S-curl
that stops the tail hanging like a plumb line is the front-to-back axis, so that
one is xRot.

Body attitude interpolates between upright (treading water) and nearly flat
(streamlined), with the chest counter-rotated back up so the merperson still
looks like it is going somewhere deliberately. Out of the water it simply
collapses and thrashes.

## Clips

Seven, all rotation-only, none of them touching the tail chain.

| Clip | Length | Use |
|---|---|---|
| `HOVER` | 4.0s | Treading water: head drift, arm sway, fins fanning |
| `SWIM` | 1.0s | Arms swept back and pulsing, hair streaming |
| `THRUST` | 0.75s | Spear cocked and lanced, torso twisting through it |
| `WARN` | 2.0s | Rear back, jaw gapes, ear fins flare forward, spear raised, then a lunge |
| `SING` | 5.0s | Head tilted back, jaw working through the chorus |
| `HURT` | 0.4s | Recoil |
| `DEATH` | 2.4s | Goes limp head-first |

The spear arm is `arm_right`, which bakes to Java x = −5 — where a vanilla
humanoid's right arm sits, so `translateToHand` needs no left-handed flag. The
first pass animated `arm_left`, which the codec mirrors to x = +5, and would
have had the merperson brandishing an empty fist while the spear hung off the
other side.

## Behaviour: ignore, warn, attack

Not hostile. A three-rung ladder, all in `customServerAiStep`:

| Rung | Trigger | Result |
|---|---|---|
| Ignore | Beyond 11 blocks, or not in the water | Nothing. You can swim over the village all day. |
| Warn | Within 11 blocks | Rears up, flares, cries out. Once, then a ~5s cooldown. |
| Attack | Within 5.5 blocks for 5 continuous seconds | Targets you, and stays angry. |

Striking any merperson skips the ladder entirely and calls `rallyPod`, which
turns every merperson within 20 blocks onto the attacker.
`HurtByTargetGoal#setAlertOthers` alone is not enough: it only reaches mobs that
can *see* the victim, and a pod scattered round a lakebed ruin mostly cannot see
each other. Answering a cry for help is the defining thing about these
creatures, so it is done explicitly.

The spear thrust is timed to the clip — the swing starts the animation and the
damage lands five ticks later, when the point is actually out in front, rather
than on the frame the arm is still cocked.

Out of water they suffocate, but slowly: five minutes of grace, then a point of
damage every two seconds. Hauling one ashore is meant to be a viable and grim
tactic, not an instant kill.

## Mermish

The books' joke about Mermish only works per-listener: it is a real language
with a real song in it, and above the surface it is unbearable screeching. Harry
cannot make out a word of the golden egg until he puts his head under the
bathwater.

A broadcast `playSound` would pick one version for everybody in earshot, so
`MerpersonEntity#speak` sends each nearby player their own `ClientboundSoundPacket`
chosen on whether *their* head is submerged. Same event, two clips, and two
players floating either side of the surface hear different things.

The chorus is started only by the chieftainess, with the rest of the pod joining
in behind her — four merpeople each rolling their own cooldown turns the village
square into a round.

## The merchieftainess

One per naturally-spawned pod, assigned in `finalizeSpawn` to the first of the
group (the presence of the returned `SpawnGroupData` marks the leader as taken).
Larger by 15%, 52 health, 10 damage, a separate paler texture with white-green
hair, and the shell crown made visible.

## The village

`data/hogwartsmagic/structure/merfolk_village.nbt`, 25×14×25, built in code by
`build_scripts/generate_biome_structures.py` like the rest of the pack, and
placed as a **jigsaw structure** rather than a Feature — a Feature that writes
blocks outside its own chunk crashes on far chunks.

Four round beehive dwellings of weed-grown lakebed stone around a square, with a
nine-block merchieftainess statue in the middle, her spear planted beside her and
lit at the tip. Round rather than rectilinear on purpose: every other build in
this pack was made by people with set squares, and a ring of stacked rock reads
as a different civilisation from across the water.

Merpeople spawn inside it through `spawn_overrides`, so it arrives inhabited
rather than waiting on a natural spawn cycle. Spacing 26 / separation 11, so it
is markedly rarer than the existing `black_lake_ruin`.

## The Black Lake, inland

`hogwartsmagic:black_lake` claims a slice of the **deep ocean** continentalness
band, which is the only way to get water deep enough to need Gillyweed — but it
means the Black Lake only ever turns up on a coastline. It is an arm of the sea,
not a lake.

`hogwartsmagic:black_lake_shallows` puts the same black water inland. The
mechanism is the **river band**: vanilla carves rivers by handing the two extreme
weirdness bands (below −0.93333 and above +0.93333) to
`OverworldBiomeBuilder#addValleys`, so a biome claiming part of that band gets a
channel of open water cut through whatever land surrounds it. Taking the two
flattest erosion bands as well (0.45–1.0, where vanilla puts swamps) turns a
narrow gorge into a broad sheet. Continentalness runs from the coast band into
mid-inland, so they appear both as sea lochs behind a shoreline and as fully
enclosed lakes.

**Why two biomes rather than one with two slices.** The inland version is
shallow — a few blocks, like any river valley. Depth in 1.18+ terrain comes from
continentalness, and continentalness high enough to be inland cannot also be
deep; no parameter buys both. So the two need to differ in what they carry:

| | `black_lake` | `black_lake_shallows` |
|---|---|---|
| Placement | Deep ocean band, coastal | River band, inland |
| Depth | Past breath-hold range | A few blocks |
| Merpeople | Pods of 2–4, one chieftainess | Ones and twos, no chieftainess |
| Merfolk village | Yes | **No** |
| Black Lake ruin | Yes | **No** |
| Guardians | Yes | No — they belong to a monument |
| Gillyweed | Yes | Yes |

The structures are the reason for the split. A twelve-block merchieftainess
statue standing in four blocks of water reads as a bug, so neither structure is
tagged for the shallow biome.

The merperson spawn rule had to change with it. It used to require
`y < seaLevel - 4`, which excludes every shallow lake in the world; it now asks
for three blocks of water, below the surface, and **within ten blocks of the
floor** — measured against the actual `OCEAN_FLOOR` heightmap, so they hug the
bottom in both a deep arm and a loch.

## The hoard

A chest at the foot of the chieftainess, flanked by dark prismarine, on its own
loot table `hogwartsmagic:chests/merfolk_hoard` — gold and lapis and prismarine
taken off the drowned, and one good thing per chest: a nautilus shell, an
enchanted book, a spear, an arcane core, or rarely a heart of the sea. A second,
lesser stash sits behind one of the huts.

**Opening or breaking either one turns the entire village on you at once**, with
a grudge lasting two minutes rather than the twenty-odd seconds an ordinary
provocation earns. The territorial ladder is skipped entirely: no warning, no
five seconds of grace. That is the Goblet of Fire beat — they let Harry take his
own hostage and swim away, and levelled spears the moment he reached for one
that was not his.

Membership is decided by a **structure tag** (`hogwartsmagic:merfolk_guarded`)
rather than by proximity to a guard, which is how `GoblinEvents` does the same
job at Gringotts. Merpeople swim; a pod scattered round a lakebed village is
nowhere near its own treasure most of the time, and a radius check would let you
rob the place whenever they happened to drift off. The tag also makes the hut
stash count, so there is no "safe" chest.

The alarm fires on **open**, not on taking an item, so they are already on you
while you are still looking at the loot.

## Spear

Iron tier, `+2` damage and `-2.6` speed — five damage and slow, on land.
`ModEvents#onMerpersonSpearHit` adds **+4 while the wielder is underwater**.

It is a bonus rather than a land penalty because item attributes cannot see
whether the holder is submerged, and a subtraction could be enchanted down into
a negative hit. Drops at 12% from a player kill, or 6% off the hand slot.

## Stats and spawning

| Attribute | Merperson | Chieftainess |
|---|---|---|
| Hitbox | 0.9 × 2.2 | ×1.15 |
| Max health | 30 | 52 |
| Movement speed | 1.05 | 1.05 |
| Attack damage | 7 | 10 |
| Armour | 2 | 5 |
| Follow range | 24 | 24 |

`MobCategory.WATER_CREATURE`, weight 30 in the Black Lake, groups of 2–4,
`SpawnPlacementTypes.IN_WATER`, and its own rule keeping them at least four
blocks below sea level — they belong on the lakebed, not in the shallows.
Breathing underwater is the `minecraft:can_breathe_under_water` entity tag in
1.21, not an override; `canBreatheUnderwater` is final.

## Files

**New:** `blockbench_models/merperson.bbmodel`, `client/model/MerpersonModel`,
`client/model/MerpersonAnimations`, `client/renderer/MerpersonRenderer`,
`entity/MerpersonEntity`, `item/MerpersonSpearItem`,
`build_scripts/generate_merperson_textures.py`,
`build_scripts/generate_merperson_sounds.py`, four entity textures, one item
texture, seven OGGs, the village structure and its four worldgen JSONs, the
merperson loot table, and the `can_breathe_under_water` tag.

**Edited:** `ModEntities`, `ModSounds`, `ModEntityAttributes`,
`ModSpawnPlacements`, `ClientModEvents`, `ExampleMod`, `ModEvents`,
`HogwartsGuideScreen`, `generate_biome_structures.py`, `sounds.json`,
`en_us.json`, `worldgen/biome/black_lake.json`.
