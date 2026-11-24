# The Sea Cave, rebuilt — Design

Date: 2026-11-24
Status: written after brainstorm, awaiting the user's approval
Replaces the structure, look and player actions of `2026-10-06-locket-cave-design.md`. The Horcrux arc
rules in that spec (inventory is the progress, one Locket per cave, never returns) are unchanged.

## Why

The user played the Locket cave and rejected nearly all of it. In their words: it does not look good,
it looks AI generated and not hand built, the textures are bad, all the actions there are bad, the
Inferi cannot reach you, the boat is weird, the wall of blood is weird, the hidden entrance is weird,
and the placement is weird and out of place.

Looking at the template and code confirmed each point:

- The outside is a rounded lump of tuff and blackstone mixed block by block, so it reads as static.
  It is pasted at a fixed height on a beach with no blending.
- The cavern is a mathematically perfect dome over an elliptical lake. The island is a straight-sided
  pillar. The whole build uses 12 block types.
- The island rises sheer out of deep water, so Inferi float beside it with no way up.
- The boat is a grey-tinted vanilla boat that pops into being and is moved by `setPos` in a straight line.
- Drinking is six right-clicks on a block with chat lines.

## Goals

1. A place that looks designed: a black rock standing in the sea, and under it a cavern that looks
   like rock, not like a formula.
2. Every action the player takes is something they see and feel, not a click with a chat line.
3. The Inferi are a real threat: they reach the player, on the island and on the way out.
4. It works for one player alone.

## Non-goals

- The Diary, the Cup and the finale (separate design, next).
- The R.A.B. fake locket.
- A new Inferius model or new Inferius sounds. The existing ones stay unless the user says otherwise
  (open question 1).
- Re-shooting the `locket_cave` Pensieve memory, which has its own copy of the old cave as a set
  (open question 2).

## Anti-goals

- Anything that reads as generated: perfect circles, domes or cylinders; one-block salt-and-pepper
  palettes; equal spacing; symmetric layouts.
- Any step whose only feedback is a chat message.
- A cave a solo player cannot finish.
- Committing a shape nobody has looked at. See "Seeing what is built".

## Approaches considered

1. **Sculpt in Blender, convert to blocks.** Most organic shapes. More steps; conversion needs tuning.
2. **The user builds the shell in creative.** Truly hand built; costs the user hours and every change
   is manual.
3. **A better script, visualised heavily.** Fast and regenerable. It is the method that produced the
   rejected cave, so it only works if every stage is looked at.

**The user picked 3**, on the condition that it is visualised a lot, "so you know what you build".
That condition is a requirement of this design, not a nicety. The visual gates below carry it.

For everything except the rock, the existing code is reworked, not rewritten (my call): the basin
state machine, ambush schedule, thirst, looted-cave tracker and Cave Map are sound underneath.

## 1. Placement

Decided by the user: **an offshore sea stack**, standing alone in open ocean.

My calls:

- Structure id stays `hogwartsmagic:locket_cave`, so the Cave Map, `LootedCaveTracker` and
  `CaveSearch` keep working unchanged.
- Biome tag `has_structure/locket_cave` becomes `minecraft:ocean`, `deep_ocean`, `cold_ocean`,
  `deep_cold_ocean`. No beaches or shores.
- Spacing stays 160 / separation 80 to start. Oceans are commoner than stony shores, so expect more
  caves than before; tune by playtest. Target is still "very rare but findable with a map".
- Fixed height so the template's waterline is sea level. The template brings its own flared base
  down to about world y 22, so it buries into any ocean floor and never floats.
- `terrain_adaptation: none`, monster `spawn_overrides` empty inside the piece, as now.
- Old caves in chunks that already generated keep their old shape. They must not crash and must stay
  completable on the old layout numbers: the basin block entity records a layout version, and a
  missing version means the old `CaveGeometry` constants. No other effort goes into old caves.
  Old caves already have a goblet block on the island, so the dip-and-drink flow works there and the
  direct right-click drink is removed for every cave. They have no iron ring, so `hidden_chain` and
  `concealed_rock` keep working as they do now. Their red wall becomes rock too, since it is the
  same block; that is accepted.

## 2. The rock

One template, about 80 x 90 x 80 (x, y, z), written by a new `build_scripts/generate_sea_cave.py`.
The old generator no longer exists in the repo; start fresh.

**Above the water: the stack.** A tall, lopsided black rock, tallest point roughly 40 to 50 blocks
above sea level, with one or two lower satellite needles and a broken, wave-cut foot. It leans; it is
not round in plan. On one side a **cleft**: a dark vertical crack about 3 wide and 8 to 12 tall at
the waterline, visible from the sea, with no fake rock over it.

**Below the water: the way in and the cavern.** The cavern is under the seabed and is wider than the
stack above it. The route:

1. Swim into the cleft. A flooded tunnel of about 15 to 20 blocks, swimmable on one breath.
2. Surface in a small **antechamber** pool whose lip is above sea level, so the sea stays out.
3. A short passage ends in bare rock: the **blood arch** (section 3).
4. Beyond it a rough **descending passage**, natural steps and drops of at most 1 block, winding down
   below sea level.
5. It opens high on the wall of **the cavern**: an irregular chamber roughly 50 x 40 with an uneven
   ceiling 12 to 25 high, stalactites, fallen slabs, side ledges, and a black lake filling most of
   the floor. A rough path leads down to the shore.
6. On the shore an **iron ring** set in the rock (section 4).
7. Off-centre in the lake, **the island**: a low hump of rock about 10 to 12 across with a **sloped
   shore all the way round** (no step higher than 1 block from water to top), the basin near its
   highest point and the goblet beside it.

**Rock rules** (from the sculpting and rendering lessons already in memory):

- One stone family. Strata: bands 2 to 6 thick of deepslate, cobbled deepslate, blackstone and
  basalt / smooth basalt, tilted and gently warped, continuous across the build. Never mixed block by
  block. Keep neighbouring bands within about 40 luma of each other. Tuff, if used at all, only as a
  rare thin band.
- No perfect primitives. Shapes come from layered noise, carved joints and a hand-set list of
  features (named spires, overhangs, slabs, ledges), each placed deliberately with coordinates in the
  script, then roughened.
- Detail by rule, not sprinkle: pointed dripstone in clusters under ceiling lows; rubble at the foot
  of walls; a wet band near every waterline; kelp and seagrass on the outer base.
- No light anywhere except the basin's green glow and the revealed chain.
- Carved space inside the template is written as `cave_air` where it replaces terrain and water where
  flooded. The whole shell around the cavern is solid template blocks so aquifers and the sea cannot
  leak in. The structure piece writes only inside its chunk box (big-structures memory).
- The script also writes the layout the Java side needs (lake surface Y, island centre, cave bounds,
  dock and waypoint markers) to a generated resource or constants file, so `CaveGeometry` cannot
  drift from the template. Lake membership becomes "water at or below the lake surface inside the
  cave bounds", not an ellipse.

- **Rotation.** The jigsaw places each cave at a random rotation. The old round lake hid that; a
  lopsided cave does not. Docks, skiff waypoints, the island centre and the cave bounds must
  therefore come from marker blocks inside the template (they turn with it) or from offsets rotated
  by a rotation stored in the basin block entity. Never from fixed world-axis offsets.
- **Finding the basin.** `CaveGeometry.CAVE_RADIUS` (34) drives `findBasinFor`, `idleGuardians` and
  the "player is in the cave" check. The new cave is bigger, and the lookup runs before the layout
  version is known, so it searches with the larger of the old and new radii.
- `start_height` is a fixed y 40 today and is re-derived so the new template's waterline is sea level.

Exact shapes, counts and noise settings are the builder's, inside these rules.

## 3. Seeing what is built

This is the user's condition for choosing a script. Nothing below is optional.

**Before any block is placed:** collect reference images (real sea stacks and sea caves, and stills
of the cave from the films, viewed for reference only and not copied into the repo) and write a
half-page of what makes them read: silhouette, lean, strata, scale against a person.

**Three passes, each gated on looking:**

1. *Silhouette* in a single stone. Flat render from the four compass points, from above, and from
   sea level 40 blocks out; cutaways `cut=x` and `cut=z` with water hidden.
2. *Strata and big features.* Textured render, same views.
3. *Detail.* Textured render, plus the standpoints below.

**Standpoints.** At every pass from 2 on, render eye-level views (camera at player height, moved with
the viewer's own matrix helper, not the URL) from at least: the sea approach to the cleft; inside the
tunnel; the antechamber; facing the blood arch; halfway down the passage; the passage mouth
overlooking the cavern; the shore at the ring; mid-lake at boat height; the island looking back; the
island looking up.

**The check at each gate**, written down per pass in the plan's notes: the six failure modes from the
rendering skill (loud density, proportion, weak focal point, scatter, one-block contrast, mechanical
regularity), plus two for this build: "would I believe a person shaped this?" and "is there a perfect
circle, dome or straight wall anywhere?". A pass that fails is redone before the next starts.

**Then in the game.** Screenshots at the same standpoints in a real world at night, placed in ocean,
because renders do not show darkness, fog or water. These are taken with commands (teleport and
screenshot), never by injecting keys while the user is at the PC, and never while a build is running.

**Then the user.** A sheet of the best renders and in-game shots goes to the user before any
mechanics are wired to the new template. Their pass is the real check.

## 4. What the player does

Agreed with the user unless marked as my call.

**The cleft.** Visible, no Concealed Rock, no Revelio needed. (`ConcealedRockBlock` stays registered
for old caves; the new template does not use it.)

**The blood arch.** No red wall. `BloodSealBlock` is retextured to match the passage rock, with a
hairline arch outline. Right-clicking the stone takes the same toll as now (6 damage, `blood_toll`).
A smear shows on the stone, then the arch melts away over about a second and stays open 60 s. Free
from the inside; never reseals on a player. My call: a slow red drip particle on the outline when a
player is within 4 blocks, so the arch can be found without the memory.

**The chain.** The iron ring on the shore is always visible. Right-clicking the ring, or casting
Revelio in the cavern, reveals the chain: real, visible links glowing faint green, running from the
ring down into the water. Pulling it (right-click) hauls it in link by link over about 3 seconds
while the water bubbles.

**The skiff.** Its own model: a narrow, old, green-tinged boat with a lantern at the prow. It rises
bow first out of the lake over a few seconds. One seat, not steered by the player. It crosses slowly
and smoothly with a slight bob and a wake, following a curved path through waypoint markers in the
template, and pale shapes drift under the surface as it passes. It grounds on the island shore and
waits there. Existing rules stay: unbreakable, refuses a second passenger, sinks away if left empty
at the far shore.

**The goblet and the basin.** The crystal goblet can be picked up. Dip it in the basin (right-click)
and it fills; drink it like a potion with the normal drinking animation. Six cups empty the basin,
and the green liquid visibly drops with each one. Effects, damage cap (never kills alone) and the
refill rule (30 s without a cup, leaving the island, or dying) stay as in `DespairRules`.

Each cup is felt on screen, not read in chat. Stages rise with the cup count: darkened and blurred
screen edges, whispers as sound, a shaking view, and at cups 5 and 6 a stagger and a brief forced
kneel. The chat lines are removed. My calls: the stage is carried to the client as the amplifier of
a hidden mob effect; the overlay is drawn through the existing `Gui#render` mixin path (Forge 51 has
no HUD event); the view shake is small and is deliberate here, though casting has none; the goblet
cannot leave the cave (carried out of bounds, it crumbles and reappears by the basin).

**The Locket** lies in the dry basin and is lifted out by hand.

**The thirst.** Unchanged in rule: only the lake cures it. Now the goblet is how: Aqua Eructo into
the goblet or near the player vanishes (new, small: `AquaStream` has no Thirst hook today), and dipping the goblet in the lake fills it. The moment it
touches the water the ambush starts. Bare hands on the lake still work if the goblet was lost.

**The Inferi.** Hands break the surface first, then they crawl up the island shore from every side.
They do little damage; they grab, and drag the player toward the water (on land too, slower than in
water), and hold them under once there. Existing diminishing returns keep it from being a permanent
lock. Fire is the answer: Incendio makes them recoil, burning ground holds the shore, Fiendfyre
clears them. Without fire the player can still fight to the skiff.

**The way out.** With the ambush running, Inferi cling to the skiff on the crossing: up to two latch
on, each slows it and hurts the rider a little, and any hit or fire knocks one off. They follow to
the blood arch and stop there, then return to the lake.

All numbers live in the existing pure rule classes and stay tunable.

## 5. Textures and models

- Blocks: blood arch (rock with hairline outline, plus smear and melt stages), basin (full, each
  drained level, dry, with Locket), goblet (block and item, empty / potion / water), iron ring and
  chain. The old `generate_cave_block_textures.py` output is replaced.
- The skiff model and texture, made with the Blockbench pipeline (the Blockbench server must be
  running; it was not connected during this brainstorm).
- The goblet item follows the item-texturing skill (16 x 16).
- Past texture redraws were rejected by this user, so every new texture goes on a before/after sheet
  in `texture_proposals/` for them to pick from before it is committed.

## 6. Server and client safety

All state stays server-side: arch, chain, skiff path, basin, goblet fill, effects, ambush, latching.
The client only renders. No client classes from common code. Avoid entity event bytes 21, 35 and 63.
No switch-on-enum in models or renderers.

## 7. Testing

- Unit tests on the pure rule classes for everything that changed (goblet states, despair stages,
  drag on land, latch rules, layout-version fallback).
- **A GameTest that an Inferius spawned in the lake reaches a player standing on the island** within a
  set time, on the real template. This is the test for "the Inferi cannot reach you" and it must
  fail on a sheer-sided island.
- A GameTest that the skiff completes shore to island and back along its waypoints.
- `runServer` data check; a dedicated-server boot with a cave generated in ocean.
- The visual gates in section 3.
- The user's play-through.

## Build order

1. References and the written "what makes it read" note.
2. The rock, in three gated passes; in-game shots; the sheet to the user.
3. Worldgen JSON, generated layout, `CaveGeometry` on the new layout with the old-version fallback.
4. Blood arch, ring and chain, with textures.
5. The skiff: model, rise, path, wake.
6. Goblet, basin levels, on-screen despair.
7. Inferi: shore climb, land drag, latching, arch leash; the reach GameTest.
8. Play-through, tuning, wiki entry.

Commit and push after each finished piece.

## Left to the builder

Exact shapes and noise; template size within about 10 percent; how the chain and wake are rendered;
packet shapes; how the layout is emitted; all tunable numbers.

## Open questions for the user

1. "The textures are bad": does that include the Inferius itself, or only the cave's blocks? This
   design redoes the blocks only.
2. The `locket_cave` Pensieve memory still shows the old cave. Re-shoot it on the new set once the
   cave is approved, or leave it?
