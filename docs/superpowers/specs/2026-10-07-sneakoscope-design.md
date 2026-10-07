# Sneakoscope Design

The user asked (2026-10-07) for an item that rings when a dark player, the Dark Mark, Death Eaters or
Dementors are near, and that can be placed down to give a redstone signal when they are close.

Canon has exactly this: the **Sneakoscope**, a glass spinning top that lights up, spins and whistles
when someone untrustworthy is near. This doc makes it one item that works in two ways: carried, it
whistles at you; placed, it is a redstone sensor.

---

## What the code looks like today

- **Death Eaters** are `entity/WizardEntity` (an `AbstractIllager`). **Voldemort** is
  `entity/VoldemortEntity`. **Dementors** are `entity/DementorEntity`. **The Dark Mark** is
  `entity/DarkMarkEntity` (it lives 4800 ticks in the sky after a Morsmordre raid or a summoning).
- **Dark players** do not exist yet. They need the Darkness score from Dark Magic Phase 1
  (`2026-10-07-dark-magic-roadmap.md`, `DarkMagicData`, tiers).
- **Block entity and block patterns:** `WandWorkbenchBlock` / `WandWorkbenchBlockEntity`, the
  `BLOCK_ENTITY_TYPES` register in `ExampleMod.java`, server tickers through `createTickerHelper`.

---

## Decisions

1. **One item, `sneakoscope`, a `BlockItem` for a `SneakoscopeBlock`.** Right-click on a block
   places it. In the inventory it works as the carried detector. Breaking the block drops the item.
2. **What it detects** (a pure `SneakoscopeRules` class holds the list and the ranges):

   | Source | Range (blocks) | Why this range |
   |---|---|---|
   | Death Eaters (`WizardEntity`) and Voldemort | 16 | Close danger |
   | Dementors | 16 | Close danger |
   | The Dark Mark (`DarkMarkEntity`) | 48 | It hangs high in the sky, so 16 would rarely reach it |
   | A player at Corrupted or Lost (Darkness 60+) | 16 | Part B only, see below |

   - The holder never detects themselves, so a dark player can still carry one.
   - **The Invisibility Cloak and Polyjuice do not hide you from it.** A Sneakoscope senses intent,
     not looks. That makes it the counter to a disguised dark player.
   - Corrupted is the threshold because from Corrupted a dark player already shows smoke to other
     players (Dark Magic decision 7). Shadowed and Tainted players do not set it off, so a player who
     tried a dark spell once is not exposed.
   - Spectators and creative players are never detected.
3. **Strength.** The nearest detected source decides it: `strength = 15 * (1 - distance / range)`,
   rounded up, from 1 at the edge of the range to 15 at the source. The Dark Mark uses its own range,
   so a Dark Mark 40 blocks up gives a weak signal, a Death Eater at 2 blocks a strong one.
4. **Carried.** The check runs once a second for each player who has a Sneakoscope anywhere in the
   inventory (one check per player, even with several Sneakoscopes).
   - When strength > 0: a whistle sound, repeated faster as the source comes closer (every 40 ticks
     at strength 1, every 10 ticks at 15). Only the holder hears it.
   - The item spins and glows in the hand and in the inventory while it detects something (an item
     model property `hogwartsmagic:spinning`, synced from the server as a data component on the
     stack, cleared when nothing is near).
   - No chat or action bar line. The sound and the spin are the whole signal, as in the book.
5. **Placed.** The block entity checks once a second (staggered by position, as the cauldron ticker)
   and stores the strength in a blockstate property `power` (0..15).
   - `isSignalSource` is true and `getSignal` returns `power`, from every side, like a daylight
     detector. Reading the power from the state means redstone never touches the block entity.
   - When `power` goes from 0 to more than 0, the block whistles once for everyone within 16 blocks.
     While power > 0 it spins (a model per state: still, and spinning with an animated texture) and
     gives light level 7.
   - It never loads chunks: the area check uses `getEntitiesOfClass` on an AABB inside loaded chunks,
     and the Dark Mark check only reads entities that exist.
6. **Shape and look.** A small glass top on a brass base, about 6 x 8 x 6 pixels, so it fits on a
   desk or a shelf. A texture generator in `build_scripts/` paints it in the style of the other
   generators (Pillow, tileable noise, Bayer ramp). The spinning texture is an animated strip.
7. **Sound.** A short high whistle. First pass: the vanilla `NOTE_BLOCK_FLUTE` at pitch 2.0 with a
   quick `NOTE_BLOCK_CHIME` at 1.8. A custom whistle sound (a generator like
   `generate_dementor_sounds.py`) is a later choice.
8. **How to get one.**
   - Crafting: glass pane in the middle, gold nuggets on the four sides, arcane dust below, and a
     gold ingot on top. Arcane dust keeps it a mid-game item.
   - Trade: the curio dealer in Diagon Alley sells one for 14 emeralds (level 2).
9. **Server authoritative.** The client only draws the spin and plays the sounds the server sends.
   No new packet: the carried spin is a data component on the stack, and the placed spin is the
   block state.
10. **Config:** `sneakoscopeRange` (16), `sneakoscopeDarkMarkRange` (48), `sneakoscopeDetectsPlayers`
    (true; a server can turn off player detection for PvP reasons).

---

## Two parts

### Part A: everything except dark players (can be built now)

The item, the block, the redstone output, the sounds, the spin, the recipe and the trade, and
detection of Death Eaters, Voldemort, Dementors and the Dark Mark. Pure `SneakoscopeRules`
(the source table, the strength formula, the whistle interval), test-first.

**Done when:** a carried Sneakoscope whistles faster as a Death Eater comes closer; a placed one
lights a redstone lamp when a Dementor flies past, with a comparator-readable strength that rises as it
comes closer; a Dark Mark 40 blocks up gives a weak signal; and `runServer` boots clean.

### Part B: dark players (after Dark Magic Phase 1)

One more source in `SneakoscopeRules`: players whose Darkness tier is Corrupted or Lost, read with
`DarkMagicData` on the server, never the holder, never with `enableDarkMagic` off or
`sneakoscopeDetectsPlayers` off.

**Done when:** a Corrupted player under the Invisibility Cloak and under Polyjuice sets off a
Sneakoscope, and a Tainted player does not.

---

## Build order

Brewing Phase 1 is in progress and goes first. Then Sneakoscope Part A, which needs nothing else.
Part B is added to the end of Dark Magic Phase 1, because it is one check on top of `DarkMagicData`.

---

## Global constraints

The same as the brewing and Dark Magic roadmaps: server authoritative, no enum `switch` in client
code, never load a chunk, pure rules first with JUnit 5 tests, valid JSON, entity event bytes
21/35/63 never used, cloud build `bash ./gradlew compileJava test --console=plain --max-workers=2`
with the 429 retry rule, commit per task.
