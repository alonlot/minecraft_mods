# Wizarding-world additions: gnomes, Portkeys, hidden houses, hideouts, dragon nests

Date: 2026-11-24. Branch `wizard-world` (worktree `C:/Users/alonl/hp-wizard-world`), cut from
`sorting-memory` at 74807457.

## Intent

The user wants the mod to be an open world where magic turns up everywhere, playable alone. It
should not feel like a school game built around one special place (that is why the castle is a
small Mini Hogwarts). These five additions spread magic across the overworld: things to find
while exploring, a way to travel, and a pest you can deal with in your own garden.

Non-goals: Quidditch, house points, anything tied to Hogwarts progress. **Anti-goals the user
named:** no baby dragons, no dragon hatching, no dragon riding.

## Approaches

Structures: the one sensible way is the mod's existing pipeline. `build_scripts/generate_biome_structures.py`
writes `.nbt` templates, a `jigsaw` / `dry_jigsaw` structure JSON places them, and a `structure_set`
sets the spacing. That is how the cursed tomb, the Horntail lair and the poachers' camp were made,
and it keeps each build inside one template (no far-chunk `setBlock`). The other options were live
`ServerLevel` placement, which is meant for huge builds like Diagon Alley and is overkill here, and
a worldgen `Feature`, which is banned for builds bigger than a chunk.

Entities, items and blocks follow the mod's existing patterns: Java-coded `ModelPart` models,
Python texture generators, and `ModSounds` with ElevenLabs takes.

## 1. Garden gnomes

- A small mob about 0.5 blocks tall: potato-shaped, bald and knobbly, leathery brown-grey skin,
  with a Java-coded model and a generated texture.
- Spawns by day in small groups (2–4) on grassy overworld biomes (plains, meadow, sunflower plains,
  forest edges), and is drawn to farmland and villages.
- Behaviour: walks to mature crops and uproots them (the block drops as an item, which the gnome
  picks up and "eats"), digs small holes in grass and dirt (grass → coarse dirt, sometimes a
  one-block pit), giggles, and flees players at a distance.
- **De-gnoming:** right-click grabs the gnome, which is carried at the hand. Holding use spins it
  (a server-tracked spin counter with a visible spin). Releasing throws it: speed scales with spin
  time, up to about 25 blocks. A gnome thrown 12+ blocks lands dazed and leaves for good (despawns
  once out of sight). A gnome grabbed without spinning bites (1 heart) a few seconds later and wriggles free.
- Sounds: giggle, squeak, bite, a thrown wail.

## 2. Portkeys (VFX and SFX must be outstanding)

- Item: **Portkey** (an old boot). Crafted from leather boots + Floo Powder + gold nugget.
  Sneak-use binds it to the player's position and dimension, shown in the tooltip ("Bound: X Y Z in
  the Overworld").
- Activation: hold use for 3 s. Every player and tamed pet within 2 blocks travels together.
  It works across dimensions, except into the Pensieve dimension or Diagon Alley, or out of the
  Pensieve dimension. Reusable with a 60 s cooldown, one way.
- **Old Portkey** (found as loot in hidden houses and hideouts): already active and one use. It
  throws the holder 2,000–4,000 blocks away in a random direction onto a safe surface found by the
  server, then crumbles.
- **VFX (client, through the existing `Fx` kinds / `CameraShake` / `Cinematic` / bloom systems, no
  new packet types beyond what is needed):**
  - Charge (3 s): the boot glows blue-white, spiral particle streams pull inward, the ground ripples
    with rune-like glints, the FOV slowly narrows, and a faint shake builds.
  - Hook (the yank): a flash plus a sharp FOV punch, as if pulled from behind the navel. Particles
    snap to the boot.
  - Transit (about 1.2 s, a full-screen client overlay): a spinning tunnel of coloured streaks,
    motion-blur feel, hands-and-wind sense of tumbling. The world is hidden while the server
    teleports, so the chunk load happens behind the overlay.
  - Landing: the overlay snaps away, a dust and leaf burst at the feet, a hard camera shake, a
    brief stagger (slowness plus a short camera roll), and a ground mark decal.
  - Bystanders see the group vanish in a whirl and an implosion of light, and a matching arrival
    burst at the other end.
- **SFX (ElevenLabs, mastered per the pipeline):** a rising charge hum, the hook whoosh-snap, a
  roaring wind tunnel bed for the transit, a landing thump with skid, and a bystander pop at both
  ends.

## 3. Hidden wizard houses

- Biomes: forests, birch and dark forests, taiga, plains, meadows. Rare: spacing about 28 chunks.
- On the surface: a crumbled, overgrown ruin (broken walls, moss, vines).
- **Revelio within 10 blocks** reveals the whole house at once, permanently:
  - **Phantom ruin blocks** (look like mossy cobblestone, cobblestone and stone bricks) vanish.
  - **Glamoured blocks** (invisible, no collision, no occlusion, holding the real block state in a
    block entity) turn into their real blocks. The real states are written into the template by
    the generator.
  - A shimmer sweep spreads outward from the caster through the house, plus a reveal sound.
- Inside: a cottage (fireplace, bookshelves, a cauldron, a bed, a table). Chests with spellbooks
  and tomes, brewing ingredients, Floo Powder, sometimes an Old Portkey.
- Traps: Cursed Glyph floor tiles (reused, so Revelio shows them as it does in tombs) and a
  jinxed chest: opening it unrevealed fires a hex (knockback plus blindness) once.

## 4. Dragon nests (no hatching, no babies, no riding)

- The existing Horntail lair's five decorative eggs (tuff and wall blocks in the generator) become
  real **Horntail Egg** blocks. Mining one gives the egg item, and the lair's Horntail (any Horntail
  within 64 blocks) targets the thief and hunts them. The egg is a placeable trophy block.
- New structure, **Ironbelly nest**, on vanilla `jagged_peaks` / `frozen_peaks`: a rocky scrape
  with a clutch of **Ironbelly Eggs** (pale, metallic-grey) and a wild Ironbelly guarding it.
  The Ironbelly is free (not chained) and uses its existing roaming and combat goals.
- Eggs can be sold to a villager the mod already has, if one fits; otherwise they are trophies
  with a small chance in high-tier loot tables.

## 5. Dark wizard hideouts

- Biomes: dark forest, swamp, mangrove swamp, the mod's Forbidden Forest and Dementor Fen.
  Spacing about 30 chunks.
- Surface: a shabby, half-collapsed hut. A trapdoor leads down a stair to 4–5 rooms 10–20 blocks
  underground: cells with Inferi (spawned or baked in, like the tomb), a potion lab, a library, and
  a ritual room with an altar.
- Traps: Cursed Glyph tiles (Revelio shows them), plus a concealed wall (existing
  `ConcealedRock`-style) to the ritual room.
- The ritual room: a Dark Wizard leader (an existing `WizardEntity`, buffed and named) guards a
  **Horcrux**, a new Hufflepuff-cup-style cursed object, using the existing Horcrux destruction
  path. Destroying it releases the existing soul entity and pays out a reward chest.

## Constraints

- Every feature has to be safe on a dedicated server: client-only code stays in `client/`, and
  every authoritative action runs on the server.
- Avoid entity event bytes 21/35/63. Don't switch on an enum in render code. Screens use
  `renderTransparentBackground`.
- `runServer` boot is the data check. GameTests where practical. Render each new structure (the
  rendering skill) before committing it.
- One commit per feature, pushed to `github`.

## Left to the builder

Loot tables, spawn weights, exact sizes, sound prompts, and texture palettes. Each is noted in the
final report as a call the user can change.
