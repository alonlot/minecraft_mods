# Sea cave references: what makes a sea stack and a sea cave read

Date: 2026-11-24. Serves `docs/superpowers/specs/2026-11-24-sea-cave-redesign-design.md` §2 and §3.
Sources: Wikipedia (Old Man of Hoy, Ball's Pyramid, Stack (geology), Fingal's Cave, Cliffs of Moher),
Am Buachaille and Faroe stack (Risin og Kellingin, Drangarnir) pages, Benagil guides, a Leaky Cauldron
interview with Stuart Craig on the film cave, and photo/still search results viewed in a browser.
Nothing from the stills is saved in the repo.

## What the real places show

**Silhouette and lean.** A stack is a leftover piece of cliff, not a cone. The Old Man of Hoy (137 m)
is a column that stays almost the same width to a top only a few metres across; its edges are
notched, with layers sticking out by different amounts, and a crack has opened a big overhang near
the top. Ball's Pyramid is a ridge about 3.5 times longer than it is wide, so it reads as a blade from
one side and a spike from the other. Stacks come with satellites: Risin og Kellingin are a pair, and
Drangarnir is a big stack with a small one beside it. Mass sits low and to one side, and the top is
off-centre over the base.

**Strata.** Stacks form in bedded rock, and the beds show. Hoy alternates soft sandstone with hard
flagstone: soft beds recess and hard beds jut, so the outline is stepped. At Moher, beds from a few
cm to several m thick run unbroken across the whole face with a slight dip. Seen from far away, the
bands are the texture: long, nearly level lines that bend a little and carry on round corners.

**The foot.** Waves cut a notch at the waterline and leave a wide apron below it: a basalt plinth and
rubble at Hoy, a debris-filled channel between stack and cliff. The foot is broader, lower and
messier than the column, with white water breaking on scattered blocks. Stacks begin as a crack along
a joint, then a cave, then an arch, then a pillar, so a crack opening at the waterline is exactly what
a real stack has.

**Scale of a cleft.** Openings are narrow and tall: a sea cave entrance follows one joint. A 3-wide,
10-tall crack is about 1.6 player widths across and 5 players high, which reads as a crack you can
swim into, not a doorway. The opening should be darker and taller than it is wide, narrowing upward
into a hairline that runs on up the face.

**The cavern ceiling.** Benagil is the one true dome, and it has a collapse hole in the top, so even
there the vault is broken. Fingal's ceiling is the broken ends of columns, a stepped and faceted roof
over a narrow, deep hall (45 to 85 m deep, about 22 m high) with a walkway of broken columns just above
high water. The ceiling follows the rock's joints and beds, so it is flat in panels, stepped where a
bed broke away, and lowest where the walls lean in.

**The film cave.** Stuart Craig put the entrance in a sea cliff (after the Cliffs of Moher) and
avoided ordinary stalactites; the inside was based on crystal and salt caves. In the stills: surf
breaking white on a rock at the foot of a dark cliff; inside, a black-teal lake with a pale pebble
shore in the foreground, dark rock masses closing in around it; the island is a heap of angular,
tilted slabs stacked like broken plates, and the basin's green light is the only colour. We take the
darkness, the one green light, the low island of tilted slabs and the shore in the foreground. We do
not copy the crystal look.

**Three things that make each read as real**

- Stack: (1) bands that run on unbroken and step the outline; (2) a broad, broken, lower foot with a
  wave notch and boulders; (3) an off-centre summit and a smaller satellite needle.
- Cleft and tunnel: (1) taller than wide, following one joint up the face; (2) darker inside than out
  at once; (3) the roof drops below the water within a few blocks, so you must dive.
- Cavern: (1) a ceiling of flat panels and steps at different heights, lowest at the edges; (2) slabs
  and talus that have obviously fallen from that ceiling; (3) the lake shore is uneven: pebble beach
  on one side, cliff straight into water on another.

## The frame and the features Tasks 2 and 3 place

Frame: template local x 0..79 (east +), y 0..89, z 0..79 (south +), before jigsaw rotation.
**Sea level: the top water block is local y 42**; local y 43 is the first open-air layer outside.
Local y 0 is about world 20, so the seabed apron sits near local y 24 to 28 at the frame edge.
**Cavern lake surface: top water block at local y 10.** All coordinates are rough block positions.
For walkable places two heights are named: **feet y** is the air block the player's feet occupy, and
**solid top** is the block they stand on, always feet y minus 1. Opening heights are air blocks,
inclusive. Water positions are the top water block.

1. **Main spire.** Summit at (54, 87, 30), the highest point, about 45 above the sea. The waterline
   footprint is about x 22..68, z 20..60, an uneven outline longer north-east to south-west (a blade,
   like Ball's Pyramid), with re-entrants and points, never round. Below y 66 the stack is a broad
   massive block; above y 66 only the north-east third rises as the spire, so the top sits about 13
   blocks north-east of the waterline centroid (45, 42, 40).
2. **West shoulder.** A lower, flat-topped secondary summit at (32, 68, 46), stepped down from the
   spire by two ledges at y 76 and 72 where hard bands jut.
3. **North overhang.** At y 74..80 on the spire's north face (x 51..58, z 24), a slab juts 3 blocks out
   over -z with a vertical crack behind it at z 27, like Hoy's south-face crack.
4. **Wave-cut notch and apron.** A notch at y 41..45 cut 1 to 3 blocks into the face, deepest on the
   exposed west and south-west (x 24..32). Below it an apron of rubble and boulders at y 36..43,
   widest on the west (out to x 14), narrow on the east. Under the water the base flares toward the frame
   edge and stays solid: at y 24..45 it reaches at least x 70 on the east side between z 24..62, to
   wrap the descending passage.
5. **Satellite needle.** At (72, 60, 67): a thin needle, base about 6 x 5 at the waterline, 18 above
   the sea, separated from the main foot by a channel 3 to 5 wide above y 36 (joined below by the
   shared underwater flare). Second, a low stump at
   (18, 48, 22) north-west, 6 above the sea, partly awash.
6. **The cleft.** On the south face at x 37..39, z 57 (the face is recessed there). Open from y 39 (under water) to y 51 at the
   face, 3 wide, then a 1-wide hairline crack continuing up to about y 58. It follows the strata dip,
   so it leans 1 block over its height. No rock is built across it.
7. **Flooded tunnel.** Rough cross-section 3 x 3, carved from the cleft mouth (38, 39, 57) through
   (40, 37, 52), (43, 36, 47) and (45, 37, 44) to rise into the antechamber pool at (46, 41, 41).
   About 18 blocks long, roof below y 42 from z 54 to z 43 so it is fully flooded, water throughout.
8. **Antechamber.** Irregular pocket about 8 x 7, centre (47, 45, 40). Pool about 4 x 3 at
   (45..48, 38..42, 40..42), water top at y 42 like the sea. Climbing out: a 1-block step inside
   the pool edge (solid top y 43, feet y 44), then the lip all round (solid top y 44, feet y 45),
   2 blocks above the sea's water top, so the sea cannot spill over it. The antechamber floor beyond
   is at the lip height (solid top 44, feet 45). The ceiling (first solid block) slopes from y 51 over
   the pool to y 48 at the exit.
9. **Blood arch.** A short passage, 2 wide and 3 tall (air y 45..47, solid top 44, roof solid from
   y 48), runs from the antechamber lip (48, feet 45, 38) through (50, feet 45, 35) to the arch. The
   arch stands in the plane x = 52, opening z 32..34, the same 3 tall as the passage: air y 45..47,
   sill solid top y 44, solid above from y 48. The outline is rough and pointed, not a semicircle.
10. **Descending passage.** From the arch it winds east, south and back west inside the eastern flank,
    floor stepping down at most 1 per block, 2 to 3 wide, 3 tall. Waypoints as (x, feet y, z):
    (55, 45, 33), (59, 43, 31), (63, 41, 34), (64, 39, 39), (62, 37, 43) (a small widening, the
    "halfway" standpoint), (63, 35, 47), (65, 33, 51), (64, 31, 55), (61, 29, 52), (58, 27, 48).
    It keeps at least 4 blocks of rock between it and the sea and between it and the cavern.
11. **Passage mouth.** The passage opens high on the cavern's east wall at (56, feet 26, 46), air
    y 26..28, onto a ledge at x 53..55, z 45..47 (solid top 25, feet 26), 15 above the lake. The
    cavern ceiling round the mouth is at least y 31 (first solid block).
12. **The cavern.** Irregular chamber about x 8..58, z 26..66 (50 x 40); the east wall reaches x 58
    only around the mouth (z 42..48) and falls back to x 55 south of z 48, clear of the descent. The lake fills most
    of the floor: lake about x 12..52, z 30..60, bed y 3..8 (deepest in the middle, shallow shelves
    near shores). The ceiling is lowest at the edges: highest air y 22..24 under the south and west (first solid
    block y 23..25), rising in steps to a high vault, highest air y 35, at about (36, 35, 40). Above any part
    of the cavern, solid rock is at least 4 thick: under the tunnel the highest cavern air stays at or below
    y 31, under the antechamber at or below y 33.
    **Roof cap outside the stack.** Parts of the cavern lie outside the stack footprint (x 8..21 on
    the west, z 61..66 on the south). Over the whole cavern plan plus a 4-block margin (about x 4..62,
    z 22..70) the template writes its own solid rock cap: its top surface is at least y 28 everywhere
    and at least 4 solid blocks above the highest cavern air beneath it, uneven and rubbly on top,
    sloping down outside the margin to meet the seabed at the frame edge. It never relies on world
    terrain for cover. Below the cavern the template is solid down to y 0.
13. **Shore path.** From the mouth ledge down along the east and south-east wall on a rough ramp of
    ledges, never over the water, as (x, feet y, z): (53, 23, 50), (51, 20, 54), (48, 17, 58),
    (45, 14, 60), reaching the south shore at (42, 12, 61) (solid top 11, 1 above the lake). East of x 50 the floor is dry talus, not lake.
14. **Iron ring and dock.** Ring set in a shore boulder at (41, 12, 62). Skiff dock (water) at
    (40, 10, 57). Pebble beach along the south shore x 34..50, z 59..63, the only gentle shore.
15. **Skiff waypoints.** Dock (40, 10, 57), then (39, 10, 52), (35, 10, 48), landing (32, 10, 45) on the
    island's east shore. A curve, not a line.
16. **The island.** Off-centre west-north of the lake centre (33, 46): footprint about x 20..31,
    z 37..47 (11 x 10, an uneven oval with a bite on the north side). Highest ground solid top y 14
    (feet 15) at (24, 14, 41), 4 above the water; the shore slopes all round with no step over 1 block, continuing
    under the water as shelves down to the bed at y 5. Basin on the highest ground at (24, 15, 41);
    goblet beside it at (25, 15, 42). Built of tilted slabs, like the film island.
17. **Fallen slab.** A big tilted slab about 7 x 3 x 2 lying half in the lake at the north-west wall,
    x 13..19, y 9..12, z 31..34, with the matching scar in the ceiling above it at about y 24.
18. **North ledge.** A side ledge along the north wall at y 18, x 20..40, z 27..29, 2 to 3 deep,
    with talus spilling from it to the water.
19. **Dripstone clusters.** Under the two ceiling lows: south-west (14..22, 22..24, 52..60) and
    south-east (44..50, 23..25, 60..64). Not under the high vault.

The waterline centroid, cleft, tunnel, antechamber, arch, descent and mouth chain together in this
order, and every one of them sits inside the stack footprint or its solid underwater flare.

## Pass 1: silhouette (gate check)

Built by `build_scripts/sea_cave/` (frame, shape, audit), one stone (`minecraft:deepslate`, axis y)
plus `cave_air` and water. Template 80 x 88 x 80, 256,082 listed cells. Audits all pass
(cleft_open, watertight, island_steps, route_walkable, no_primitive, plus rock_cover: 4 blocks of
template rock between every carved cell and the open sea, and between the descent and the cavern).
Cavern sections fit their best ellipse on 16 / 29 / 41 / 44 / 38 % of their outer wall at
y 12 / 16 / 20 / 24 / 28 (limit 60 %).

Choices made while shaping (the deferred review notes):
- **Cleft lean: west.** The beds dip east (0.06 per block) and a little south; the cleft is a joint at
  right angles to them, so it steps one block west going up (x 37..39 at the waterline, 36..38 from
  y 46, a 2-wide top at 50..51, and the 1-wide hairline at x 37 then 36 up to y 58).
- **Rock under the tunnel and beside the descent.** The cavern is carved only beyond 4.5 blocks of the
  tunnel, pool, antechamber, arch passage and every descent leg but the last two (which run into the
  wall at the mouth); the `rock_cover` audit checks the descent against the cavern cell by cell.
- **Shore ramp.** The drops are placed unevenly along a 4-connected walk through the waypoints; the
  solid-top profile from the ledge is 25 24 23 22 22 21 21 20 19 19 19 18 17 16 16 16 16 16 16 15 14
  14 13 12 11 11 11: runs of one to three drops between landings of one to six blocks.
- **Roof cap edge.** Over the cavern plus 4 blocks the seabed is at least y 28 and 5 above the highest
  cavern air, with 0..1.6 of rubble on top; beyond the margin it loses about 1.3 a block (1.8 on a
  diagonal) until it meets the apron or the seabed slope, so there is no ledge at its rim.

Iterations, and what each render showed:
1. Polygon plan per mass with banded recesses and a body top that terraced toward its rim. Every
   view read as a **beehive / ziggurat**: concentric tiers body > ledges > spire, a knob on top, the
   needle a string of beads. Failed (mechanical regularity, dome-like outline).
2. Plans redrawn in blade coordinates along the north-east joint set with 1..4-block jogs; spire,
   ledges and shoulder made flush with the north-west face. Plan read as a blade, but the textured
   views showed a **stack of pancakes**: every bed made a rim all round. Failed.
3. Walls fluted instead of ringed (vertical facets drifting slowly with height, only the softest beds
   recessed and only along part of the perimeter), beds snapped to the nearest boundary. First pass
   that read as rock. The cleft sat at a corner of an indent and did not read as a crack; the face
   was rebuilt round it (buttress east of it, face at z 58..59 west of it).
4. North-west face given bays at the saddle and between spire and ledges; saddle and south-west tip
   lowered so the shoulder, ledges and spire stand as separate masses; spire top changed from a
   radial crown (read as a box) to a corner summit falling away south-west and steeply south-east.
5. The east platform read as a round **plate** under the spire: made unsnapped and broken on top,
   edge noise doubled, two surge channels cut across it.
6. Cavern walls lean in as they rise (0.55 a block above y 12, roughened in 3D, undercut at the
   waterline); island rebuilt as a faceted mound (it had grown to about 20 across through the
   no-step relaxation): now 11 x 10 above the lake, crest 14 at (24, 41).

Gate check on the first submission (`build/shots/sea_cave_p1_*.png` under the textured viewer). Rows
marked in the review-fix table below supersede these:

| View | What I saw |
|---|---|
| North (`_n`, flat `_flat_elev_north`) | The blade end-on: a spike, broad foot, summit off to the east side, a sheer north corner. |
| East (`_e`, `_esea`) | Needle at left, separate above the water; the spire's north-east face one cliff from the platform to the top; the broken east platform low under it. Some bed steps still stack on the spire's south-east side. |
| South (`_s`, sea level 40 out `_sea`) | Broad, lopsided mass: low south-west tip, shoulder, spire behind at the east, needle at right. From sea level the top line is fairly level and reads as a massif more than a column. The cleft shows only as a narrow slot here (the viewer has no light); the depth-shaded face (`_flat_face_south`) shows it as a dark slot 3 wide and 13 tall with the hairline above. |
| West (`_w`) | The blade end-on from the tip: a tall narrow mass with the spire rising behind. |
| North-west (`_nw`), east-north-east (`_ene`) | Shoulder and spire as two separate jointed columns with a bay between; fluted faces, stepped tops. The best reads of the set. |
| Top (`_top`, `_flat_top`) | Blade north-east to south-west, jogged outline, stump north-west, needle south-east, apron widest west. |
| Cutaways (`_cutx`, `_cutx_low`, `_cutz`, `_hidewater`, flat `_flat_cut_*`, `_flat_floor`, `_flat_ceiling`) | Cavern sections irregular with leaning walls and a ceiling stepping in panels up to the vault (35 at (36, 40)); island a low faceted mound with shelves; the pool sits well above the cavern roof. The textured interior shots are hard to read: the viewer has no lighting, and with water hidden it shows thin see-through slits that the watertight audit says are not real holes. |

The six failure modes:
1. **Loud density** - n/a for one stone; nothing scattered.
2. **Proportion** - the underwater plinth fills the whole 80 x 80 frame down to y 0, with straight
   sides at the frame edge from about y 23 down. It is meant to sit in the seabed, but over deep
   ocean it would stand as a square cliff (Task 5 placement concern). Above water nothing eats the
   frame.
3. **Focal point** - the spire is the clear focus from every side but due south at sea level, where
   the massif hides it.
4. **Scatter** - boulders are hand-placed, mostly west, not sprinkled.
5. **One-block contrast** - n/a (one stone).
6. **Mechanical regularity** - the tiers and rings are gone; flutes and joints vary. Residual: the
   spire's south-east side still shows three or four stacked bed steps from the east.

**Would a person believe it was shaped?** From north-west, east-north-east and east at sea level,
yes: jointed columns, a broken platform, a separate needle. From due south at sea level it reads as a
rocky massif with a crenellated top line rather than a stack; acceptable for a silhouette pass, worth
revisiting when strata go on (Task 3), since banding will change how that face reads.

**Any perfect primitive?** No circle, dome, cylinder or straight wall above the seabed. The island is
mound-shaped (it has to be: no step over 1 from bed to top) but faceted and bitten on the north.
The only straight lines are the frame-edge sides of the buried plinth.

Still owed: the in-game check at night, and standpoint renders from pass 2 on. The viewer cannot
show darkness, so whether the cleft reads dark from the sea is unverified.

### Pass 1, review fixes

The review found six faults; all are fixed and the audits pass again (generator: `locket_cave
78x88x78 166811 blocks 3 block types ... ok`; the template is 78 square now because nothing is
listed within 2 of the frame edge).

1. **Square plinth.** The template now writes only an irregular footprint: the stack masses, every
   carved column and the cavern plan plus 2, grown by 11..14 blocks with noise, never within 2 of the
   frame edge. Within 6 blocks of the footprint's rim nothing stands above local 10 (the rim sinks to
   local 2..9, world ~23..30); inward of that the flank climbs at a wandering 1.8..5.4 a block. Corners
   and frame edges are unlisted, so the natural sea floor and the sea run up to the rock. New audit
   `footprint_rim`. To make room, the cavern's west wall moved from x 8 to about x 13..15 and its south
   wall from z 66 to about z 63..64; the lake's west shore moved with it (x 15..17), the fallen slab moved
   3 east (x 16..22), the satellite needle moved 4 west (centre x 68, top still 60), and the east
   platform stops at x 71.
2. **East apron cut by the frame.** Gone with the footprint: every column is clamped by its distance
   to the rim, not by the frame.
3. **Island joined to the north wall.** The north ledge's talus now stops at the water, and the
   no-step relaxation only runs over lake and island columns within the island's reach (it used to
   drag the ledge height out into the lake). `island_steps` now counts island columns (dry ground
   joined to the crest) and lake-bed columns (water above), not the dry shore under the walls. New
   audit `island_isolated`: dry ground joined to the crest stays within radius + 1 and spans 10..12
   (it is x 20..30, z 37..48).
4. **Shoulder and ledges as extruded cylinders.** Their tops are now bed planes tilted away from a
   high edge with pieces broken out in steps (not a dome falling off radially, which my first try did
   and which read as a turret). The ledge plans have 2..4-block jogs; the shoulder is drawn square to
   the map on the cross-joint set (faces 2..6 long meeting at corners), with no taper and weaker flutes.
5. **Spire tiers.** The spire only steps back on its south-west side (toward the ledges); its
   north-west, north-east and south-east faces run straight up from the body. Its top is no longer
   snapped to the beds, and the body's south-east flank drops by a varying 2..8 instead of a uniform 5.
6. **`cleft_open` could pass a 1 x 2 slot.** It now measures carved cells only: a 3-wide window of
   carved cells across the waterline, allowed to shift one block per row (the lean), at least 8 tall,
   with rock on both sides at the waterline and the open sea within 4 blocks in front, then a flood to
   the tunnel. The notch is suppressed round the cleft so its sides stand.
7. (Optional item) **Castle windows on the south face.** These were the cleft's hairline, broken into
   two pockets where it stepped west, and the south-east-face joint gully. The hairline is now one
   continuous crack cut through to the face, and that gully is gone.

| View (fix round, `sea_cave_p1_fix_*`) | What I saw |
|---|---|
| East (`_e`) | The spire is one face from the body to the summit with a hooked top, no longer four or five tiers. Below it the body and platform still show two or three bed steps on the south-east flank. Needle at left. |
| North-west close (`_nw_close`) and west (`_shoulder_w`) | The spire is a tall fluted column. The shoulder now reads as a broken square block with a tilted top. From the north-west corner it is still a fairly compact block next to the spire, but no longer a round turret with a lid. Ledges step down toward it with irregular edges. |
| South at sea level (`_sea`, `_windows`) | The rectangular pits are gone in the close view. From 40 out the hairline shows as two dark dots where a protruding block hides its middle. It reads as a crack in the close view, less so at that distance. |
| Under water, east (`_rim_e`) and south-west (`_rim_sw`) | The footprint ends in a low skirt at local 2..9 everywhere, with irregular outline and no square edge. Above the skirt the flank climbs steeply. On the east it is effectively the platform's own fluted wall, about 30 blocks of near-vertical underwater cliff, because the platform has to reach x 71 to cover the descent and the rim must stay inside the frame. From the south-west it reads as a broad, uneven mound, not a frustum now that the slope wanders. |
| Top (`_top`, flat `_fixflat_top`) | Listed area is an irregular blob x 2..77, z 3..77 with all four corners empty. |
| North (`_n`), west (`_w`), ENE (`_ene`) | As before. The spire's north-east face is a single tall face. |
| Cavern (flat `_fixflat_cut_*`, `_floor`, `_ceiling`) | Same character as before, a little narrower in the west. The island is clearly separate from the north ledge, with water between. |

Six failure modes after the fixes: (2) proportion: the east underwater cliff is the one thing that
still looks abrupt, and only from below the sea; (6) mechanical regularity: the spire's tiering is
gone, and two or three bed steps remain on the south-east flank of the body. The others are
unchanged. **Would a person believe it was shaped?** From the north-west, ENE, east at sea level and
west, yes. From due south at sea level it is still more massif than column. **Perfect primitive?**
None above the seabed. Under water the flank is no longer a uniform-slope frustum. The east face is a
near-vertical wall in section, but not straight in plan.

### Pass 1, review fixes round 2

- **Plumb wall under the east waterline.** It could not be fixed by trimming the platform alone.
  The rim must sink below local 10 within 6 of its edge, and the edge must stay 2 inside the frame.
  With a fall of at most 6 a block, any rock at sea level has to stand at x <= 65 (and z <= 65). The
  note's descent loop ran out to x 65, so it needed rock to x 70. The loop is pulled 3..5 west:
  (58,43,31) (59,41,34) (59,39,39) (58,37,43) halfway, (59,35,47) (60,33,49) (59,31,52) (56,29,52)
  (54,27,48), and the mouth is now (53,26,46). With it, the cavern's east wall moves 3 west (x 52..55),
  the mouth ledge moves to x 50..52 and the shore ramp shifts 2..3 west ((51,26,47) (50,23,50) (48,20,54)
  (46,17,58) (44,14,60) (42,12,61)). The body's east corner and the platform stop at x 64..65, the
  platform's east edge wanders 63.5..65 along z, and the needle moves to (60, 60, 63). It still stands
  apart above y 36 (3..4 blocks of water to the platform) and drops into the sea without a wave notch,
  which used to pinch it. Under the sea the flank now falls 4.5..5.5 a block from the foot to the rim
  skirt. New audit `column_drop`: no step over 6 between neighbouring columns below the sea outside
  the cleft mouth. On the round-1 build it failed with 192 steps, worst 34 at (71,52) next to (72,52).
- **Floating pieces.** The stump lost its wave notch (it cut the stump through) and now stands on the
  apron. Boulders are seated half-buried on the seabed where they lie. Any boulder whose spot is near
  the rim or on a steep flank is dropped (5 of 13 kept, all on the west and north-west apron). Crumbs
  of 8 blocks or fewer left hanging by the carving are removed. New audit `connected`: every solid
  piece is 6-connected to rock on the template floor. On the round-1 build it failed with 3 loose
  pieces: 63 at the stump, 39 at the north boulder, 2 by the cleft.
- **footprint_rim margin.** It now measures against the template as written. A structure void in the
  far top corner keeps the template at its full 80 x 90 x 80 frame, so the 2-block margin is real
  (the void places nothing).

| View (round 2, `sea_cave_p1_r2_*`) | What I saw |
|---|---|
| Under water east (`_rim_e`) | No plumb wall. The east flank falls in steps of 4..6 to the skirt, rough-faced. |
| Under water south-east / south-west (`_rim_se`, `_rim_sw`) | A broad mound whose flank climbs at a near-even 4.5..5.5 a block from the skirt, so it reads as a rough frustum from below. With a fall limit of 6 there is no room to vary it more inside the frame. Only visible from under the sea. |
| East at sea level (`_esea`), east (`_e`), needle (`_needle`) | The needle is now thinner and taller-looking, clearly separate from the main foot. The spire's north-east face is one face. The east platform ends a few blocks sooner. |
| Stump (`_stump`) | Seated on the apron and partly awash. No longer floating. |
| South-east (`_se`) | Main mass, needle in front, platform between. |

Failure modes after round 2: no change from round 1 above the sea. Under the sea the flank is now
plausible as a slope, but uniform. **Perfect primitive?** Not above the sea. The underwater flank is
the closest thing to one (a rough, uneven-outlined frustum), and it sits below the waterline.
