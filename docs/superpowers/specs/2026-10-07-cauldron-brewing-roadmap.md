# Cauldron Brewing Roadmap

This doc decides **what ships, in what order, and what gets cut** for real potion brewing in the
Wizard Cauldron. It is a roadmap, like `2026-10-02-spell-mastery-roadmap.md`. Each phase below gets
its own implementation plan in `docs/superpowers/plans/` (written by a phase planner under
`handoff/standing-rules/planner-rules.md`) once the phase before it has been played.

---

## Why this feature

Potions are the largest gap between the mod and the books. Today:

- The Wizard Cauldron is decorative. It has particles and nothing else.
- All five brewing recipes use the vanilla brewing stand, and all five are on the Polyjuice line.
- The mod already has ingredients with no strong use (Mandrake root, Acromantula venom, Wolf fang,
  Gillyweed), a Mandrake pulling minigame, and two apothecary shops in Diagon Alley.

Brewing gives those ingredients a purpose, gives the apothecaries something to sell, and gives the
player a non-combat skill.

---

## What the code looks like today (the facts this plan is built on)

Line numbers drift. Find each spot by its text.

- **The cauldron** is `block/WizardCauldronBlock.java`, a plain `Block` (not an `EntityBlock`):
  a 1..15 x 0..13 shape, light 7, WITCH particles in `animateTick`, no use handler.
  - **New model and textures (2026-10-07, already committed):** `models/block/wizard_cauldron.json`
    is a hollow cast-iron pot: four feet, a floor, belly walls, a narrower neck, a rolled rim with an
    opening from 4 to 12, two side lugs, and a flat brew surface at y 9.5 (the `brew` texture,
    animated). The textures come from `build_scripts/generate_wizard_cauldron_textures.py`
    (`wizard_cauldron_side`, `_inner`, `_top`, `_bottom`, `_brew`). The old single
    `wizard_cauldron.png` is deleted. The brew surface is always shown for now, so decorative
    cauldrons in structures still look full.
  - Registered in `ExampleMod.java` as `wizard_cauldron` with `Properties.ofFullCopy(Blocks.CAULDRON).strength(1.5f)`.
  - Recipe `data/hogwartsmagic/recipe/wizard_cauldron.json` (iron + arcane dust). Loot drops itself.
  - It is the job site of the `APOTHECARY` profession (`entity/ModVillagers.java`, `APOTHECARY_POI`).
  - It is placed by structures: the Diagon Alley apothecary windows (`diagon_alley.nbt`), the Death
    Eater camp (`death_eater_camp.nbt`) and the Hogwarts ruin camp (`hogwarts_ruin_camp.nbt`).
    Mini Hogwarts has no cauldron.
- **Block entity pattern:** `BLOCK_ENTITY_TYPES` in `ExampleMod.java`.
  `WandWorkbenchBlock` / `WandWorkbenchBlockEntity` is the model: `BaseEntityBlock`, a server-only
  ticker through `createTickerHelper`, `saveAdditional`/`loadAdditional(tag, HolderLookup.Provider)`,
  `getUpdateTag` + `getUpdatePacket`, and `setChanged` + `sendBlockUpdated` on change.
  Block entity renderers are registered in `event/ClientModEvents.registerRenderers` and live in `client/`.
- **No custom recipe types exist.** There is no `RecipeType`/`RecipeSerializer` register, and no JEI
  (its deps are commented out in `build.gradle`). Workbench recipes are hardcoded Java lists
  (`BroomWorkbenchBlockEntity`).
- **Effects and potions:** `MOB_EFFECTS` and `POTIONS` registers in `ExampleMod.java`.
  `wizard_elixir` is the mana regen effect. Its boost is applied in `ModEvents.onPlayerTick`
  (`regenMult *= 1.5f + 0.25f * amp`). The `wizard` potion holds it for 3600 ticks.
- **Brewing stand recipes:** `ExampleMod.onRegisterBrewingRecipes` registers five `IBrewingRecipe`s from
  `polyjuice/`. The Wizard Potion is Awkward + **Acromantula venom** (`MOD_FEATURES_WIKI.md` says Wolf fang,
  which is wrong).
- **Ingredients that exist** (registry names): `mandrake_root`, `acromantula_venom`, `wolf_fang`,
  `centaur_talisman`, `gillyweed`, `arcane_dust`, `arcane_core`, `fine_wood_stick`, the three wand
  cores, `magical_silk`. There is **no** basilisk fang item.
- **Apothecary trades** (`ModVillagers.java`) sell gillyweed, the Moonlit Phial, arcane dust, the
  cauldron, Polyjuice and an arcane core, and buy mandrake root and venom. The class Javadoc says
  apothecaries are "the only source of bottled ingredients", but **no bottled ingredient items exist.**
- **Mana API:** `spell/support/SupportMana.add(ServerPlayer, float)` adds mana, clamps and syncs.
- **Debuffs a potion could cure:**
  - `bleeding`: it **cannot be removed with `removeEffect` while the drinker is using an item**
    (`HealSpell.onBleedingRemove` cancels it, so milk does not work). A potion must call
    `HealSpell.cureBleeding(LivingEntity)`.
  - `cursed_wound`: same block (`CombatEffects.onEffectRemove`). A potion must call
    `CombatEffects.cureCursedWound(LivingEntity)`.
  - **Basilisk petrification always kills.** `BasiliskEntity.petrifyAndKill` applies the effect for
    60 ticks and then deals `Float.MAX_VALUE` magic damage. A non-player victim leaves a
    `PetrifiedRemainsEntity`, a cosmetic statue that stores the mob type, size and yaw.
    The gaze itself builds up `basilisk_gaze` tiers over 50 ticks first.
- **Crowd control:** `spell/combat/CrowdControl.apply(target, effect, ticks, source)` applies
  `stunned` and similar effects with diminishing returns, the boss rules and the PvP scaling.
- **Polyjuice:** `ModEvents.breakPolyjuiceDisguise(ServerPlayer)` ends a disguise and syncs it.
- **The Hogwarts Guide** (`client/HogwartsGuideScreen.java`) is hardcoded sections of
  `page(title, List<Component>)`. A new section is cheap.
- **Advancements:** `data/hogwartsmagic/advancement/hp/`. `cauldron_class` (own a cauldron) is the
  parent of `repotting_class` and `polyjuice_plot`.
- **Tests:** pure `*Rules` classes with JUnit 5 tests in `src/test/java`. No Minecraft-bootstrapped tests.

---

## Decisions

1. **Brewing happens in the world, not in a GUI.** You fill the cauldron, put heat under it, and
   right-click it with ingredients in order. You see the liquid change colour. A GUI would be faster
   to build, but it would make the cauldron a furnace with a different skin. The in-world steps are
   the feature.
2. **A recipe is an ordered list of steps.** A step is one of:
   - **Add** an ingredient (one item per click, the count matters).
   - **Stir** clockwise N times (right-click with a `fine_wood_stick`, the "stirring rod").
   - **Stir** anticlockwise N times (the same, while sneaking).
   - **Simmer** for N seconds (wait with heat under the cauldron).

   The cauldron compares what you do with every recipe that starts the same way. While at least one
   recipe still matches, the brew is "on track". When nothing matches, the brew is **ruined**.
3. **Feedback without a guide in hand.** The liquid colour changes at each correct step (each recipe
   step has its colour). A wrong step turns it grey-brown with a sad sound and SMOKE particles. A
   finished brew shimmers (sparkle particles and a chime). The player never has to guess whether
   the last click counted.
4. **Heat is a block under the cauldron:** campfire, soul campfire, fire, soul fire, lava or a magma
   block. Without heat, you can add ingredients and stir, but Simmer steps do not count down.
   This is a cheap way to make the cauldron a build, not a single block.
5. **Water first.** A cauldron takes one water bucket (3 doses), or three water bottles. An empty
   cauldron refuses ingredients.
6. **Overbrewing.** A finished brew that stays on heat for more than 120 seconds is ruined. Taking
   it off the heat (breaking the fire) holds it forever. This gives tension without timing windows
   on every step.
7. **One finished brew is 3 doses.** Right-click with a glass bottle to take one. Felix Felicis is
   the exception: it gives 1 dose. A ruined brew bottles as "Failed Potion" (Nausea 10 s, Poison 3 s)
   or is emptied with a bucket.
8. **Recipes are hardcoded in Java, in a pure class** (`BrewRules` with a `RECIPES` table), like the
   workbench recipes. No datapack recipe type and no JEI in this pass. A datapack format is a
   separate, later decision if someone wants modpack support.
9. **Recipes are learned from the Hogwarts Guide.** A new "Potions" section lists each recipe. A
   later phase can gate recipes behind an "Advanced Potion-Making" book. Not now.
10. **Polyjuice moves to the cauldron (user rulings, 2026-10-07).** The cauldron is the only place
    to brew Polyjuice. The four brewing stand recipes of the Polyjuice line are removed **in Phase 1,
    in the same task that adds the cauldron recipe**, never before: removing them first would leave
    no way to make Polyjuice. The Wizard Potion (mana regen) stays on the stand. See "Polyjuice in
    the cauldron" below.
11. **Potions are custom items, not vanilla `Potion` entries.** Each potion is a `BrewedPotionItem`
    with its own drink logic (cure helpers, mana, revive). Vanilla potion contents cannot call
    `HealSpell.cureBleeding`. Throwable potions (Draught of Living Death, Mandrake Draught) have a
    splash variant made by adding gunpowder as the last step in the cauldron.
12. **Basilisk petrification freezes, it does not kill (user ruling, 2026-10-07).** The victim
    chooses: wait for a rescue, or give in and respawn. See "Petrification" below. Bosses (the
    `hogwartsmagic:bosses` tag) keep the old lethal rule.
13. **No brewing mastery or potion quality in this pass.** A recipe is right or ruined. Quality tiers
    (better ingredients, better stirring) are a later idea.
14. **Brewing works the same in every dimension.** No moon-phase or biome rules.
15. **Server authoritative.** Brewing sends nothing new from the client. All steps are `useItemOn`
    on the server, and the client only draws the liquid from the synced block entity tag. The one
    new client-to-server packet is `GiveInPacket` (Phase 2), and the server checks the petrified
    state before it acts on it.

---

## The potions (first pass: six new ones, plus Polyjuice)

Each potion ties to a system that already exists. Numbers are first values to tune in play.

| Potion | Effect | Ties to |
|---|---|---|
| **Wiggenweld Potion** | Heals 8 HP over 2 s and cures Bleeding (through `HealSpell.cureBleeding`) | Sectumsempra, Bleed rules |
| **Pepperup Potion** | Restores 40 mana now, and 30 s of Fire Resistance. Steam from the ears (CLOUD particles) | Mana |
| **Essence of Dittany** | Cures Cursed Wound (through `CombatEffects.cureCursedWound`), heals 4 HP | Dolohov's Curse, splinching |
| **Mandrake Restorative Draught** | Cures petrification: splash it on petrified players and statues (4-block radius), or right-click a petrified player with a bottle. Drunk by a free player, it clears `basilisk_gaze` build-up and gives 8 s where the gaze cannot build | Basilisk, Mandrakes |
| **Draught of Living Death** | Splash only. `stunned` for 20 s base, through `CrowdControl.apply`, so the existing rules apply: diminishing returns, half duration on bosses, 60% in PvP | Crowd control |
| **Felix Felicis** | 3 minutes of Luck III (vanilla loot rolls) and +25% mastery XP. Brewing takes 10 minutes of simmer and gives 1 dose | Mastery, Gringotts loot |

**Cut from this pass:** Veritaserum (Revelio already reveals Polyjuice, so it would add little; built
later, see "Later"),
Amortentia (no mechanic to hang it on), Skele-Gro (no bone-loss mechanic), Wolfsbane (it needs a
player werewolf system, which does not exist).

### Draft recipes

Ingredients marked **new** are added in Phase 2. Phase 1 ships only the three recipes that use
existing items: Wiggenweld, Pepperup and Polyjuice.

| Potion | Steps (after water and heat) |
|---|---|
| Wiggenweld | Add 1 `honey_bottle`, add 2 `glow_berries`, stir clockwise 3, simmer 20 s |
| Pepperup | Add 1 `blaze_powder`, add 1 `mandrake_root`, stir anticlockwise 2, simmer 15 s |
| Essence of Dittany | Add 3 **`dittany`**, stir clockwise 5, simmer 30 s |
| Mandrake Draught | Add 2 `mandrake_root`, add 1 **`asphodel`**, stir clockwise 4, simmer 45 s, add 1 `arcane_dust` |
| Living Death | Add 1 **`asphodel`**, add 1 **`wormwood`**, add 2 **`valerian_sprigs`**, stir anticlockwise 7, stir clockwise 1, simmer 60 s, then gunpowder |
| Felix Felicis | Add 1 **`ashwinder_egg`**, add 1 `acromantula_venom`, add 1 `gold_nugget`, stir clockwise 3, simmer 600 s, stir anticlockwise 1 |
| Polyjuice Potion | Add 1 `acromantula_venom`, add 2 `mandrake_root`, add 1 `gillyweed`, stir clockwise 4, simmer 480 s, add 1 filled `polyjuice_sample` |

"Stir anticlockwise 7, stir clockwise 1" is the Half-Blood Prince's note, on purpose.

### New ingredients (Phase 2)

| Item | Source |
|---|---|
| `dittany` | A new plant (bush block) in Forbidden Forest and Horntail Peaks; apothecaries sell 2 for 6 |
| `asphodel` | Apothecary L2, 1 for 8 |
| `wormwood` | Apothecary L2, 2 for 6 |
| `valerian_sprigs` | Apothecary L1, 2 for 4; rare drop from Hogwarts ruin camp chests |
| `ashwinder_egg` | Rare: Death Eater camp and Chamber chests; dropped by a Blaze at 5% |

This also makes the apothecary Javadoc true.

---

## Polyjuice in the cauldron (user ruling, 2026-10-07)

1. **The recipe** is in the table above. It uses only items that exist today, so it ships in
   Phase 1. The 480 s simmer makes it the second longest brew after Felix Felicis (canon: a month).
2. **The sample is the last step.** After the simmer, the brew waits for the sample. The overbrew
   rule (decision 6) applies while it waits on heat, so take it off the heat while you fetch the
   sample. An empty `polyjuice_sample` is refused with a line ("This sample is empty.") and does
   not ruin the brew. A filled one finishes it.
3. **The target is stored on the cauldron.** `BrewRules` sees only the item name
   `polyjuice_sample`, so it stays pure. The block entity copies the sample's target data when the
   step is accepted and saves it with the brew.
4. **Optional last steps,** in this order, after the sample:
   - **redstone:** extended Polyjuice (8 minutes), as the old stand recipe did;
   - **gunpowder:** splash Polyjuice, as the old stand recipe did.
   Each one is accepted only once. Anything else after the sample ruins the brew.
5. **3 doses,** each a normal `polyjuice_potion` that carries the same target. Drinking it works
   exactly as today (`PolyjuicePotionItem`, the 3-second delay, the disguise sync).
6. **The stand recipes go.** In `ExampleMod.onRegisterBrewingRecipes`, remove the four
   `addRecipe` lines for `StabilizedPolyjuiceBaseBrewingRecipe`, `PolyjuiceBrewingRecipe`,
   `PolyjuiceRedstoneExtendBrewingRecipe` and `PolyjuiceSplashBrewingRecipe`. Keep
   `WizardPotionBrewingRecipe`.
   - The code that builds a Polyjuice stack from a sample (the target copy, the potion contents,
     the extended duration, the splash name) moves first into one helper the cauldron calls,
     `polyjuice/PolyjuiceStacks`. Then the four recipe classes are deleted.
   - **The `stabilized_polyjuice_base` potion stays registered,** with its lang line. Removing a
     registry entry breaks the bottles that players already have in their worlds. With no recipe
     left that uses it, it is a leftover bottle; its lang name gets " (no longer used)".
   - Polyjuice that players already brewed keeps working: it is the same `polyjuice_potion` item.
7. **The advancement `polyjuice_plot`** triggers on owning a `polyjuice_potion`, so it needs no
   change.
8. **Docs:** `MOD_FEATURES_WIKI.md` (the Polyjuice entries under Potions) and the web wiki
   (`wiki/`) say Polyjuice is brewed on the stand. Phase 1 corrects them, because the change ships
   there and not in Phase 3.

---

## Petrification (user ruling, 2026-10-07)

The user asked for canon petrification: the Basilisk's full gaze turns you to stone instead of
killing you. You can wait for someone to save you with the Mandrake Draught, or press a button to
give in and respawn. Petrified mobs can be saved the same way.

**This ships in Phase 2, in the same plan as the Mandrake Draught.** A freeze with no cure would be
a slow death, so until the Draught exists the gaze stays lethal.

### Players

1. **The full gaze petrifies.** `BasiliskEntity.petrifyAndKill` becomes `petrify`. For a player it
   no longer deals `Float.MAX_VALUE` damage. It sets a persisted state
   (`PlayerPersisted/<modid>/Petrified`, with the game time it started) and applies the
   `basilisk_petrification` effect with no end time.
2. **While petrified, the player:**
   - cannot move, look, jump, attack, use items, cast or open the inventory (the server zeroes
     movement each tick, as the effect already does, and refuses use, attack and cast packets);
   - takes no damage, except void damage, `/kill` and the "give in" choice (a stone statue does not
     bleed, and nobody can farm a frozen player);
   - is not a target: mobs, the Basilisk, Dementors and NPC casters ignore them;
   - does not get hungry, and holds their air under water;
   - keeps the stone tint that the client already draws for the effect (`ClientForgeEvents`).
3. **The choice.** When the player is petrified, the client opens a small screen, like the death
   screen, with two buttons:
   - **"Wait for rescue"** closes the screen. A line on the HUD says "You are petrified. Press [G]
     to give in." The key can be rebound, and it opens the screen again.
   - **"Give in"** sends `GiveInPacket` (client to server). The server checks that the player is
     still petrified, then kills them with the Basilisk damage source. This is a normal death, so
     the Horcrux rebirth, item drops and `keepInventory` all work as usual.
   The screen does not pause the game, and chat stays open, so the player can call for help.
4. **The petrified state survives logout.** A player who logs out as stone logs back in as stone,
   with the screen open again.
5. **A safety limit** (config `petrificationMaxSeconds`, default 0 = no limit). A server can set
   one, so that an AFK statue does not stay forever. When it runs out, the player gives in
   automatically.
6. **The cure.** The Mandrake Draught ends the state: the effect goes, the persisted keys are
   cleared, and the player gets back full control with Slowness I for 5 s. A cure sends a line to
   both players ("X brought you back.").

### Mobs

7. **A petrified mob still becomes a statue**, as today (`ModEvents` turns a petrified non-player
   death into a `PetrifiedRemainsEntity`). Two changes make it worth saving:
   - **The statue keeps the whole mob.** At death, `PetrifiedRemainsEntity.fromLiving` also stores
     the mob's full NBT (`saveWithoutId`, without the death fields). Today it stores only the type,
     size and yaw, so a revived pet would come back wild, with no name and no owner.
   - **No loot and no XP.** A petrified death drops nothing (cancel the drops in a LOWEST priority
     handler with an `isCanceled` guard). Otherwise saving a mob would duplicate its items.
8. **The cure** loads the stored NBT, sets health to max, removes the petrification effects,
   places the mob at the statue's position and yaw, and removes the statue. The mob keeps its name,
   owner, villager trades, equipment and inventory. A statue from an older world with no stored NBT
   brings back a fresh mob of the same type.
9. **Breaking a statue still destroys it** (today any hit kills it). That stays: if you break your
   petrified villager, it is gone.

---

## Phases

### Phase 1: The working cauldron (two potions)

- `WizardCauldronBlock` becomes a `BaseEntityBlock` with a `WizardCauldronBlockEntity`:
  water doses (0..3), the step history, the current simmer counter, the ruined flag, the finished
  recipe id, and the overbrew timer. A server ticker runs only while there is heat and liquid.
- Pure `brewing/BrewRules` (Minecraft-free, test-first): the recipe table, the step model,
  `match(history) -> ON_TRACK | FINISHED(recipe) | RUINED`, the simmer and overbrew math, and the
  colour per step. Ingredients are stored as registry-name strings so the class stays pure.
- Interactions in `useItemOn`: water bucket, water bottle, ingredient, stirring rod (sneak for
  anticlockwise), glass bottle, empty bucket (empties a ruined brew). An empty hand shows a one-line
  status in the action bar ("Simmering: 12 s left", "Ruined", "Ready").
- Heat check: the block below is in a `hogwartsmagic:cauldron_heat` block tag (campfire and soul
  campfire count only when lit).
- **The liquid without a block entity renderer.** The new model already has a brew surface. Phase 1
  adds a blockstate property `level` (0..3) and one model per level (the surface at a different
  height; none at 0). A `BlockColor` handler (`client/`, registered like the fine wood leaves
  colour handler in `ClientModEvents`) reads the colour from the block entity, and the brew face
  gets `tintindex: 0`. The `brew` texture must then become greyscale, so the tint gives the colour
  (change the BREW ramp in `generate_wizard_cauldron_textures.py`). This is cheaper than a renderer
  and re-draws only when `sendBlockUpdated` runs. The default state for placed and structure
  cauldrons stays "full, green", so decoration does not change.
- Wiggenweld, Pepperup and Polyjuice (see "Polyjuice in the cauldron"), the Failed Potion, and
  `BrewedPotionItem`.
- Polyjuice leaves the brewing stand: `PolyjuiceStacks`, the four stand recipes removed, the
  leftover base potion renamed, and the two wikis corrected (items 6 and 8 of "Polyjuice in the
  cauldron").
- Lang lines and the "Potions" section in the Hogwarts Guide for these three recipes.

**Migration:** placed cauldrons in old worlds have no block entity. `Level.getBlockEntity` on the
server thread creates a missing one on demand for an `EntityBlock`, so they should start working when
first used. The Phase 1 plan must confirm this on a world saved before the change, and the cauldrons
in `diagon_alley.nbt` must work after the change too.

**Done when:** you can build a cauldron over a campfire, brew all three potions by the guide, ruin a
brew with a wrong step, overbrew one, bottle 3 doses, cure Bleeding with Wiggenweld, turn into a
sample's target with cauldron Polyjuice (normal, extended and splash), the brewing stand no
longer accepts any Polyjuice step but still brews the Wizard Potion, an old Polyjuice bottle from
before the change still works, and `runServer` boots clean on a dedicated server.

### Phase 2: Ingredients and the other four potions

- The five new ingredient items, the `dittany` plant with its worldgen patch (following
  `MandrakePatchFeature`), apothecary trades and chest loot.
- Essence of Dittany, Mandrake Draught, Draught of Living Death, Felix Felicis.
- Splash variants (gunpowder step) and their thrown entity, following the vanilla splash potion.
- Petrification, as in "Petrification" above: the player state, the choice screen, `GiveInPacket`
  (the network protocol goes up by one), the key binding, the target filters, the statue that keeps
  the full mob, no drops on a petrified death, and the cure for players and statues. The pure parts
  (the state rules, the time limit, which damage passes) go in a Minecraft-free class, test-first.
- Felix: the mastery XP bonus hooks the existing `MasteryAwards` award path (players only).

**Done when:** each potion does what the table says; a petrified player can wait, give in, or be
saved by a second player; a petrified named and tamed wolf comes back with its name and owner; a
petrified death drops nothing; Living Death respects diminishing returns and the boss rule; and the
petrified state survives a relog on a dedicated server.

### Phase 3: Polish and world

- Advancements: "Brew your first potion" (child of `cauldron_class`), "Ruin a brew", "Liquid Luck".
- A Potions classroom in Mini Hogwarts with working cauldrons (the castle has none). This needs the
  schematic changed, so it may move to the Mini Hogwarts plan if one exists by then.
- Update `MOD_FEATURES_WIKI.md` and the web wiki.

### Later

**Built 2026-10-27 (plan `2026-10-27-roadmap-leftovers.md`, branch `roadmap-leftovers`):**
- Brewing quality tiers: Standard, Fine and Exceptional, from fine ingredients and a steady stirring rhythm
  (durations x1.5/x2, amounts x1.25/x1.5).
- The "Advanced Potion-Making" book, which gates the Draught of Living Death, Felix Felicis and Veritaserum
  (Polyjuice stays ungated). Sold by the Bookseller at level 3, found in Mini Hogwarts classroom chests.
- Veritaserum, which this roadmap cut from the first pass: it is given, not thrown, and breaks disguises,
  makes villagers confess and makes Death Eaters give up accomplices.
- The Potion of Remorse (from the Dark Magic roadmap).

**Still not planned:** a datapack recipe format with JEI, Wolfsbane.

---

## User answers (2026-10-07)

1. **In-world steps, not a GUI** (decision 1).
2. **Felix Felicis strength is OK:** Luck III and +25% mastery XP for 3 minutes, from a 10-minute
   brew, 1 dose.
3. **Petrification freezes** with a give-in choice (see "Petrification").
4. **Polyjuice moves to the cauldron,** and the stand recipes go in Phase 1 (decision 10).

No questions are open. Phase 1 can be planned.

---

## Global constraints (apply to every phase)

- Forge 51.0.33, Minecraft 1.21, Java 21.
- Server authoritative. Common and server code never references `com.hogwartsmagic.client.*` or
  `net.minecraft.client.*`. The renderer lives in `client/`.
- No `switch` on an enum in client or render code. Use if-chains or `EnumSet`.
- Pure rules first: every number and every match decision lives in Minecraft-free classes with
  JUnit 5 tests.
- Never load a chunk. The heat check reads only the block below a loaded cauldron.
- Any new hurt, death or effect handler: LOWEST priority and an `isCanceled` guard.
- Effect removal goes through the existing cure helpers, never a raw `removeEffect` for
  `bleeding` or `cursed_wound`.
- Entity event bytes 21, 35 and 63 crash the client. Use 61/62/64/66/67.
- `en_us.json` and every JSON file touched stays valid JSON.
- Build and test in the cloud: `cd /home/user/minecraft_mods && timeout 900 ./gradlew compileJava test --console=plain`,
  after checking `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`.
- Commit per task. Stage only named files.
