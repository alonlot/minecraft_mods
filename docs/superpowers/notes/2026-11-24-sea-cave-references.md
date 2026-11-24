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
