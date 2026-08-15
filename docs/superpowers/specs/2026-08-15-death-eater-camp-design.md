# Death Eater Camp — design

A Forbidden Forest structure for the mod's dark wizards, with a Dark Mark painting hung inside it.

## The site

Canon settles what this place is. Voldemort's camp during the Battle of Hogwarts was not a camp they
built — it was **Aragog's abandoned lair**, taken over after the Death Eaters drove the Acromantulas
out, and the clearing still had *"webs draped overhead"* when Harry walked into it.

That fixes the whole design. They moved into something that already existed, so the build splits in
two and the split is legible on sight:

| Theirs (deliberate, stonework) | The spiders' (found, decaying) |
| --- | --- |
| the hearth, the rite wall, the throne, the prisoner post | the ring of dead trunks, the web canopy, the beaten ground |

The web is burned open above the fire. That hole is the single detail that says *someone took this
place from something else*.

## Layout

26 × 12 × 26, surface, one jigsaw pool element. Centre is the hearth at (13, 13).

- **Clearing** — trodden podzol and coarse dirt out to r≈12.5, hard-packed inside r=8. Undergrowth
  only past the trunk ring, so the inside reads as occupied rather than as a gap in the trees.
- **Hearth** — soul campfire on a scraped blackstone and basalt pad, five felled logs dragged up
  around it and one gap where a sixth should be.
- **Trunk ring** — eight dead dark-oak trunks at r=10.5, 6–9 tall, one snapped to a stump so the ring
  is not a colonnade. Branches reach inward; the canopy hangs from them.
- **Web canopy** — a dome to y=11. Radial strands run from each branch tip to the lip of the hole,
  then a fill thickens toward the fringe (where a real web is still anchored) and thins to nothing
  over the fire.
- **Rite wall** — deepslate low into blackstone high, x 6–20, ruined at both ends, pilasters standing
  a block proud with lantern corbels, coping following the broken top. Carries the painting, flanked
  by two Slytherin banners.
- **Altar bench** — low on purpose, so nothing stands between a viewer and the mark.
- **Throne** — across the fire from the wall, facing it.
- **Prisoner post** — stripped log, crossbar, chains. Left standing and empty.
- **Supplies** — chest (`minecraft:chests/pillager_outpost`), barrel, cauldron, book pile.

Three `hogwartsmagic:wizard` are baked in, spread 120° apart on bare earth outside the hearth, plus
`spawn_overrides` so the camp keeps producing them. Baking matters: overrides only bias the ongoing
spawn cycle, so a camp generated at noon would sit empty until nightfall.

## The painting

A new data-driven variant, `hogwartsmagic:summoning_rite`, 4×3 blocks (64×48 px), kept out of
`#minecraft:placeable` so it exists only here.

It depicts the rite that actually summons Voldemort in this mod, painted rather than diagrammed:
three ward stones on the forest floor in the arrangement the code demands — two on a line four
apart, the third set back two diagonal steps from each, which is
`VoldemortSummoningEntity.formsDiagonalTriangle` asking for squared distances of 8, 8 and 16 — with
the light between them drawn in so the triangle reads. Above them, the Dark Mark in the smoke, which
is what Morsmordre leaves when the rite takes.

No wards are placed in the camp. The painting tells; it does not perform.

Style follows vanilla: one luminous subject on a dark canvas, a 23-colour palette, Bayer-dithered
banding. The last pass snaps the whole image onto that palette — compositing light over ground over
sky produced 580 colours on the first draft, which looks like a render rather than paint.

## Verification

These builds are written blind, so the checks are the design.

- `painting_span()` reimplements `Painting.calculateBoundingBox` rather than guessing: the anchor is
  neither the corner nor the middle, so a 4×3 hung at x=13 spans 12–15.
- `audit_paintings()` checks the canvas has air in front and solid wall behind every block. Both arms
  were confirmed to fire by deliberately breaking the build.
- `audit_painting_variants()` checks the texture is exactly `width*16 × height*16`, since a variant
  whose PNG is the wrong size fails to load with no symptom but a missing painting.
- `NEEDS_SOIL` checks plants against `BlockTags.DIRT`, which is narrower than it looks — gravel, sand
  and mossy cobblestone are all absent. This caught a pre-existing fault in `willow_clearing`, where
  ferns could land on mossy cobblestone and drop themselves on the first block update.

Known and harmless: vanilla logs one `Block-attached entity at invalid position` per placement.
`BlockAttachedEntity.readAdditionalSaveData` compares the baked TileX/Y/Z against the world position
`StructureTemplate` has already substituted into `Pos`, and template-local coordinates can never be
within its 16-block tolerance. The position it keeps is the one `reapplyPosition()` derived from
`Pos`, which is correct, and `moveTo` overwrites it immediately after.

## Files

| | |
| --- | --- |
| `build_scripts/generate_biome_structures.py` | `death_eater_camp()` build, painting helpers, two new audits |
| `build_scripts/generate_summoning_rite_painting.py` | the 64×48 texture |
| `data/hogwartsmagic/structure/death_eater_camp.nbt` | compiled template |
| `data/hogwartsmagic/painting_variant/summoning_rite.json` | 4×3, `asset_id` → the texture |
| `data/hogwartsmagic/worldgen/{structure,structure_set,template_pool}/death_eater_camp.json` | placement, salt 741220007, spacing 26 / separation 10 |
| `data/hogwartsmagic/tags/worldgen/biome/has_structure/death_eater_camp.json` | `hogwartsmagic:forbidden_forest` only |
| `assets/hogwartsmagic/textures/painting/summoning_rite.png` | 64×48, 23 colours |
