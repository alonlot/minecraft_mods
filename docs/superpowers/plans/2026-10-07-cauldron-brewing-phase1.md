# Cauldron Brewing Phase 1 (The Working Cauldron) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** The Wizard Cauldron becomes a working, in-world brewing station. You fill it with water, put heat under it and right-click it with ingredients, the stirring rod and time, in order. The liquid changes colour at every correct step, a wrong step ruins it, a finished brew shimmers and gives 3 doses, and a finished brew left on the heat too long overbrews. Phase 1 ships three recipes (Wiggenweld, Pepperup, Polyjuice), the Failed Potion, and moves Polyjuice off the brewing stand.

**Architecture:**
- **Pure rules** (`brewing/BrewRules`): the recipe table, the step model (a history of merged runs), `match(history) -> ON_TRACK | FINISHED | RUINED`, when time simmers, when the overbrew clock runs, the overbrew numbers, the liquid colour after every step, the Polyjuice tail (sample, then optional redstone, then optional gunpowder) and the empty-sample refusal. Ingredients are registry-name strings. Minecraft-free, test-first.
- **Block and block entity** (`WizardCauldronBlock extends BaseEntityBlock`, `WizardCauldronBlockEntity`): a `level` blockstate (0..3) that equals the water doses and picks the model, and a block entity that holds the doses, the history, the ruined flag, the overbrew seconds, the Polyjuice target and the liquid colour. All interactions are `useItemOn`/`useWithoutItem` on the server. A server ticker (only while `level > 0`) does its work once a second, and only with heat below.
- **Liquid without a renderer:** one model per level (brew surface at three heights, none at 0). The brew face has `tintindex: 0`; a client `BlockColor` reads the colour from the block entity. The brew texture becomes greyscale. The block entity re-meshes its block on the client when a colour packet arrives.
- **Potions** are items: `BrewedPotionItem` (Wiggenweld, Pepperup, Failed) with their own drink logic. Polyjuice from the cauldron is the existing `polyjuice_potion` item (or a vanilla splash potion carrying the target, as the stand made it), built by one helper, `polyjuice/PolyjuiceStacks`.
- **Feedback** uses vanilla sounds and particles only. Lines go to the action bar.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5, Python 3 with Pillow for the texture and model generators.

**Spec:** `docs/superpowers/specs/2026-10-07-cauldron-brewing-roadmap.md`, Phase 1, decisions 1-15, "The potions" (Wiggenweld, Pepperup, Polyjuice, Failed Potion only), "Draft recipes" (three rows), "Polyjuice in the cauldron" (all items), "User answers", "Global constraints".

## Requirements (Phase 1 of the spec)

1. `WizardCauldronBlock` becomes a `BaseEntityBlock` with a `WizardCauldronBlockEntity`: water doses (0..3), step history, simmer counter, ruined flag, finished recipe, overbrew timer. A server ticker runs only while there is heat and liquid.
2. Pure `brewing/BrewRules`, test-first: recipe table, step model, `match`, simmer and overbrew math, colour per step, registry-name strings.
3. `useItemOn`: water bucket, water bottle, ingredient, stirring rod (sneak for anticlockwise), glass bottle, empty bucket (empties a ruined brew). Empty hand: one-line status in the action bar.
4. Heat: the block below is in `hogwartsmagic:cauldron_heat`; campfires count only when lit.
5. `level` property (0..3), one model per level, `BlockColor` from the block entity, brew face `tintindex: 0`, greyscale brew texture. Placed and structure cauldrons stay "full, green".
6. Wiggenweld, Pepperup, Polyjuice, the Failed Potion and `BrewedPotionItem`.
7. Polyjuice leaves the brewing stand: `PolyjuiceStacks`, the four stand recipes removed in the same task that makes the cauldron recipe usable, the leftover base renamed, both wikis corrected.
8. Lang lines and a "Potions" section in the Hogwarts Guide for the three recipes.
9. Migration: old-world cauldrons and the `diagon_alley.nbt` cauldrons work.
10. Done when: brew all three by the guide, ruin one, overbrew one, bottle 3 doses, cure Bleeding with Wiggenweld, turn into a sample's target with cauldron Polyjuice (normal, extended, splash), the stand refuses every Polyjuice step but still brews the Wizard Potion, an old Polyjuice bottle still works, and `runServer` boots clean.

## Rulings made while planning

1. **Old worlds and structures (verified).** Decompiled 1.21 (`forge-1.21-51.0.33_mapped_official_1.21.jar`, `javap -c`): `Level.getBlockEntity` returns null off the server thread, and otherwise calls `LevelChunk.getBlockEntity(pos, EntityCreationType.IMMEDIATE)`, which calls `createBlockEntity` and `addAndRegisterBlockEntity` when none exists (that also registers the ticker). So a cauldron saved before this change gets its block entity the first time it is used. As a fallback the block still creates and sets one if `getBlockEntity` returns null. The three structure templates store `hogwartsmagic:wizard_cauldron` with no `Properties` (checked in the gzipped NBT), and old chunks have no `level` either: both load as the default state.
2. **Default state is `level=3`; a player-placed cauldron is `level=0`** (`getStateForPlacement`). A block entity that was never used (`Initialized` false) reports `BrewRules.CLASSIC_GREEN` (`0x3A9E4E`), so decorative cauldrons look exactly as before. On its first use it initializes to `doses = level` with plain water (3 doses for a structure cauldron) and turns water blue.
3. **The history is a list of merged runs.** Consecutive identical actions merge ("add glow berries" twice is one run of 2; each simmered second adds 1 to the simmer run). A history fits a recipe when every run but the last equals the step at its index and the last run is the same action with an amount up to the step's. Recipes may not have two consecutive steps of the same action (a test checks the table). This merge also makes "redstone only once" fall out: a second redstone makes a run of 2, which fits nothing.
4. **The simmer counter is the last run of the history,** not a separate field. Time counts only when the next or current step of the first fitting recipe is a Simmer step, so heat during an Add or Stir step never ruins a brew, and a simmer can never overshoot.
5. **Overbrew:** the clock counts heated seconds once the recipe's last Simmer step is complete (finished, or waiting for the Polyjuice sample) and never resets for that brew. More than 120 s ruins it. Off heat it holds forever. The last 20 s play `LAVA_POP` each second as a warning. A finished brew on heat shows 2 `END_ROD` sparkles a second.
6. **The ticker** is registered while `level > 0` (the block state, so it re-registers itself when the level changes) and does its work once every 20 ticks (phased by position), only with heat below. Heat cannot select the ticker because it is another block.
7. **Ingredients need a full cauldron** (3 doses), so a brew always gives 3 doses. Water (bucket or bottle) is refused once a brew has started. An empty cauldron refuses ingredients. Items that are not in any recipe (`BrewRules.isIngredient`) pass through to vanilla, so you can still place blocks against a cauldron; a recipe item in the wrong place ruins the brew.
8. **Stirring plain water is not a step** ("You stir the water. Add an ingredient first.").
9. **Sneak-stirring:** vanilla skips the block when a sneaking player holds an item. `fine_wood_stick` becomes a `StirringRodItem` (same registry id) whose Forge `doesSneakBypassUse` returns true at a Wizard Cauldron. Forge requires both hands to bypass, and an empty off hand does; the guide says "keep your other hand empty".
10. **Anything after a finished brew ruins it,** except the recipe's tail steps (Polyjuice: redstone, then gunpowder, each at most once, in that order). A filled sample added before the simmer is complete ruins the brew.
11. **Bottling:** a glass bottle on plain water gives a water bottle; on a finished brew the potion; on a ruined brew a Failed Potion; on an unfinished brew it is refused. The last dose resets the cauldron to empty (`level=0`).
12. **Buckets:** a water bucket fills plain water to 3 doses. An empty bucket on a ruined brew pours it away (the bucket stays empty); on 3 doses of plain water it takes a water bucket; anything else is refused with a line.
13. **Ingredients are consumed one per click** (none in creative). A honey bottle gives back a glass bottle. The Polyjuice sample is consumed, and its target is copied onto the cauldron when the step is accepted.
14. **Polyjuice output:** normal = `polyjuice_potion` with the target and a 3:00 effect; redstone = 8:00 (`MobEffectInstance(effect, 9600, 0, false, false, true)`, as the old extend recipe); gunpowder = vanilla `SPLASH_POTION` with the same contents, the target and the custom name `item.hogwartsmagic.splash_polyjuice_potion`, as the old splash recipe, because `ModEvents.onProjectileImpact` only handles `Items.SPLASH_POTION`. A finished Polyjuice with an empty stored target bottles as Failed Potion.
15. **`PolyjuiceStacks` is written in Task 2 from the stand recipes' code,** and Task 3 deletes the four stand recipe classes. The stand classes are not refactored to call it first, because they are deleted one task later.
16. **Numbers.** Wiggenweld: `HealSpell.cureBleeding`, then Regeneration IV (amplifier 3) for 48 ticks, which heals 1 HP every 6 ticks = 8 HP over 2.4 s. Pepperup: `SupportMana.add(player, 40)`, Fire Resistance 600 ticks, 14 `CLOUD` particles at the eyes and `FIRE_EXTINGUISH` at pitch 1.6. Failed Potion: Nausea 200 ticks, Poison 60 ticks. All three stack to 1 and leave a glass bottle.
17. **Colours (RGB).** Water `0x3F76E4`, classic green `0x3A9E4E`, ruined `0x6B5A45`. Wiggenweld steps `0xD9A33A, 0xE8C85A, 0x8FD16A, 0x4CC38A`. Pepperup `0xE0662B, 0xB5562E, 0xD9452B, 0xE8322A`. Polyjuice `0x6E8B3D, 0x5C6E2E, 0x4E7A55, 0x55663A, 0x6B6B2A, 0x9C8F4A`, tail redstone `0xA0563E`, gunpowder `0x7D7468`. Inside a step the colour blends from the previous step's colour by progress, so every accepted click visibly changes the liquid. Potion item tints: Wiggenweld `0x4CC38A`, Pepperup `0xE8322A`, Failed `0x6B5A45`.
18. **Sounds and particles.** Accepted ingredient or stir: `BUBBLE_COLUMN_BUBBLE_POP` + 6 `BUBBLE_POP`; stir adds `GENERIC_SPLASH` + 6 `SPLASH`. Ruin: `FIRE_EXTINGUISH` (pitch 0.7) + `NOTE_BLOCK_BASS` (pitch 0.5) + 16 `SMOKE`. Finish: `AMETHYST_BLOCK_CHIME` (pitch 1.2) + `BREWING_STAND_BREW` + 16 `END_ROD`. A simmer step completing: `BREWING_STAND_BREW` (pitch 1.2). Each simmered second: 3 `BUBBLE_POP`. Water in: `BUCKET_EMPTY`/`BOTTLE_EMPTY`; out: `BUCKET_FILL`/`BOTTLE_FILL`. The old `WITCH` ambient particles stay, only when `level > 0`.
19. **Sync.** Every change calls `setChanged` + `sendBlockUpdated`. While simmering the client is updated every 5 s and when the step completes, not every second (each update re-meshes a chunk section). The update tag carries only `Initialized`, `Doses`, `Color`. The block entity overrides Forge's `onDataPacket(Connection, ClientboundBlockEntityDataPacket, HolderLookup.Provider)` (signature checked in the Forge 51.0.33 sources) and calls `level.sendBlockUpdated(..., Block.UPDATE_IMMEDIATE)` on the client so the colour is redrawn even when the block state did not change.
20. **An overbrew ruin has no chat line** (no player is involved). The sound, the smoke and the "Ruined." status tell the story.
21. **Item textures reuse vanilla:** the three potions use `minecraft:item/potion_overlay` (tinted, layer 0) over `minecraft:item/potion`, coloured by an `ItemColor`. No new item textures.
22. **The cauldron item** (parent: the level-3 model) gets an `ItemColor` that returns classic green, so it does not show a grey liquid.
23. **Level models** are written by a new generator, `build_scripts/generate_wizard_cauldron_level_models.py`: level 1 surface at y 5 across 3..13, level 2 at y 7 across 3..13, level 3 the existing surface at y 9.5 across 4..12, level 0 none. The existing `wizard_cauldron.json` stays the level-3 model and only gains `tintindex: 0`.
24. **Greyscale brew ramp:** `(96,96,96) (128,128,128) (160,160,160) (196,196,196) (228,228,228) (255,255,255)`. The generator's random calls do not change, so the other four sheets come out byte-identical.
25. **Breaking a cauldron loses its brew** (and a stored Polyjuice target). It drops only itself, as today.
26. **Wikis.** The Polyjuice entries are corrected as the spec says. The Wizard Cauldron entries ("Decorative") and the Wizard Potion entries ("Werewolf Fang", "intermediate brew before Polyjuice") would be false after this phase, so they are corrected in the same task. Entries for the new potions wait for Phase 3.
27. **Guide:** a seventh tab, "Potions", after "Craft & Tools", with 6 pages: the cauldron, signs in the liquid, Wiggenweld, Pepperup, Polyjuice, and the Polyjuice sample and tail.
28. **Lang:** the Polyjuice tooltip that described the stand chain now points to the cauldron. The base potion name becomes "Stabilized Polyjuice Base (no longer used)".
29. **The network protocol does not change.** No packet is added; block entity data uses the vanilla packet.

## Global Constraints

- Server authoritative. `brewing/`, `block/`, `block/entity/`, `item/`, `polyjuice/` never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/` and in the existing client-only `event/ClientModEvents.java`.
- No `switch` on an enum anywhere in the new code. Use if-chains and `==`.
- Never load a chunk. The heat check reads only `pos.below()` of a loaded cauldron (the same chunk column).
- No new hurt, death or effect handler is added. (If one were: LOWEST priority and an `isCanceled` guard.)
- Bleeding is cured only through `HealSpell.cureBleeding`, never `removeEffect`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- `en_us.json` and every JSON file touched stay valid JSON. Validate with `python3 -c "import json;json.load(open('<file>',encoding='utf-8'))"`.
- Build and test (cloud):
  - First `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If a build is running, wait (poll every 30 s, at most 20 min).
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*BrewRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
- Dedicated-server boot (Task 3 and Task 6), "the boot block":
  1. `cd /home/user/minecraft_mods && mkdir -p run && echo "eula=true" > run/eula.txt && rm -f run/logs/latest.log`
  2. `(timeout 600 bash ./gradlew runServer --console=plain --max-workers=2 > /tmp/srv.log 2>&1 &)`
  3. Poll every 5 s, up to 10 min: `grep -E "Done \(|Exception|Crash" run/logs/latest.log` until it prints something.
  4. `grep -n "Done (\|Exception\|ERROR" run/logs/latest.log | head -40`
  5. `pkill -f runServer; pkill -f "net.minecraftforge"; sleep 5; pgrep -fa java | grep -v GradleDaemon` must print no server process.
- Commit per task. Stage only the named files; never `.claude/settings.local.json` or `.superpowers/`. Never push. No apostrophes in heredoc bodies. Every commit message ends with a blank line and exactly:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
  ```
  Use `git commit -F /tmp/msg.txt` with the message written by a heredoc.
- **339 tests at ea0bbf6**, all green. They stay unchanged and green. Task 1 adds 20 (359 total).
- Line numbers drift. Every edit names the exact text to find, quoted from ea0bbf6 (or from an earlier task of this plan, which says so).

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/brewing/BrewRules.java` | Pure: recipes, runs, match, simmer, overbrew, colours, tail, refusal | 1 |
| Create `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java` | 20 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java` | Wiggenweld, Pepperup, Failed Potion drink logic | 2 |
| Create `src/main/java/com/hogwartsmagic/item/StirringRodItem.java` | `fine_wood_stick` with sneak bypass at a cauldron | 2 |
| Create `src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceStacks.java` | Builds a Polyjuice stack (target, extended, splash) | 2 |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | 3 potion items, stick class, creative tab (T2); block entity type, brewing stand recipes (T3) | 2, 3 |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Potion item colours (T2); cauldron block and item colours (T4) | 2, 4 |
| Create `src/main/resources/assets/hogwartsmagic/models/item/{wiggenweld,pepperup,failed}_potion.json` | Vanilla potion layers | 2 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Items (T2); cauldron lines, Polyjuice tooltip, base name (T3); guide tab (T4) | 2, 3, 4 |
| Modify `src/main/java/com/hogwartsmagic/block/WizardCauldronBlock.java` | BaseEntityBlock, `LEVEL`, heat tag, interactions | 3 |
| Create `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java` | Brew state, interactions, ticker, save, sync | 3 |
| Create `src/main/resources/data/hogwartsmagic/tags/block/cauldron_heat.json` | Heat sources | 3 |
| Modify `src/main/resources/assets/hogwartsmagic/blockstates/wizard_cauldron.json` | One variant per level | 3 |
| Create `build_scripts/generate_wizard_cauldron_level_models.py` | Writes the level models, tints the surface | 3 |
| Modify `src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron.json` | `tintindex: 0` on the brew face | 3 |
| Create `src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_{empty,level1,level2}.json` | Level 0, 1, 2 models | 3 |
| Delete `src/main/java/com/hogwartsmagic/polyjuice/{StabilizedPolyjuiceBase,Polyjuice,PolyjuiceRedstoneExtend,PolyjuiceSplash}BrewingRecipe.java` | Stand recipes gone | 3 |
| Create `src/main/java/com/hogwartsmagic/client/WizardCauldronColors.java` | BlockColor and ItemColor for the cauldron | 4 |
| Modify `build_scripts/generate_wizard_cauldron_textures.py` | Greyscale BREW ramp | 4 |
| Modify `src/main/resources/assets/hogwartsmagic/textures/block/wizard_cauldron_brew.png` | Regenerated greyscale | 4 |
| Modify `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` | "Potions" section | 4 |
| Modify `MOD_FEATURES_WIKI.md`, `wiki/data.js` | Polyjuice, cauldron and Wizard Potion entries | 5 |

Untouched on purpose: `entity/ModVillagers.java` (`poi()` builds the POI from `getStateDefinition().getPossibleStates()`, so all four levels are job-site states), `PolyjuicePotionItem`, `PolyjuiceData`, `ModEvents` (splash Polyjuice handling stays), `WizardPotionBrewingRecipe`, the `stabilized_polyjuice_base` registration, the cauldron recipe and loot table, the advancements (`polyjuice_plot` still triggers on owning a `polyjuice_potion`), `SpellPackets`.

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T3, T4 | `BrewRules.Kind {ADD, STIR_CLOCKWISE, STIR_ANTICLOCKWISE, SIMMER}`, `Kind.byName(String)`; `BrewRules.Step(Kind kind, String item, int amount)` with `add(String,int)`, `stirClockwise(int)`, `stirAnticlockwise(int)`, `simmer(int)`, `sameAction(Step)`; `Recipe(String id, List<Step> steps, List<Integer> colors, List<Step> tail, List<Integer> tailColors, int doses)`; `Status {ON_TRACK, FINISHED, RUINED}`; `Match(Status status, Recipe recipe)` | names identical |
| T1 → T3 | `append(List<Step>, Step)`, `match(List<Step>)`, `simmerLeft(List<Step>)`, `wantsSimmer(List<Step>)`, `overbrewClock(List<Step>)`, `overbrewed(int)`, `overbrewLeft(int)`, `overbrewWarning(int)`, `color(List<Step>)`, `usedTail(List<Step>, String)`, `isIngredient(String)`, `refusesEmptySample(String, boolean)`; constants `MAX_DOSES`, `SIMMER_SYNC_SECONDS`, `WATER_COLOR`, `RUINED_COLOR`, `CLASSIC_GREEN`, `REDSTONE`, `GUNPOWDER`, `WIGGENWELD`, `PEPPERUP`, `POLYJUICE` | identical |
| T2 → T3 | `ExampleMod.WIGGENWELD_POTION`, `PEPPERUP_POTION`, `FAILED_POTION`; `PolyjuiceStacks.make(CompoundTag target, boolean extended, boolean splash)`; `fine_wood_stick` is a `StirringRodItem` | written in T2 |
| T2 → T4 | `BrewedPotionItem.Kind.color()`, `BrewedPotionItem.kind()`; the `registerItemColors` text after T2 (quoted in T4) | identical |
| T3 → T4 | `WizardCauldronBlockEntity.getColor()`; `ExampleMod.WIZARD_CAULDRON`, `WIZARD_CAULDRON_ITEM`; `BrewRules.CLASSIC_GREEN` | identical |
| T2 → T3 → T4 | Last lang line: T2 ends with `item.hogwartsmagic.fine_wood_stick.tooltip`, T3 ends with `brew.hogwartsmagic.polyjuice`, T4 anchors on that | each task quotes the previous task's last line |
| T3 | Deleting the four recipe classes: their only references are the four `addRecipe` lines in `ExampleMod.onRegisterBrewingRecipes` | `grep` in T3 step 9 |

---

### Task 1: BrewRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/brewing/BrewRules.java`
- Test: `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java`

**Interfaces.** Consumes: nothing. Produces: everything in the T1 rows of the pre-flight table.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java`:

```java
package com.hogwartsmagic.brewing;

import com.hogwartsmagic.brewing.BrewRules.Match;
import com.hogwartsmagic.brewing.BrewRules.Recipe;
import com.hogwartsmagic.brewing.BrewRules.Status;
import com.hogwartsmagic.brewing.BrewRules.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewRulesTest {
    private static final String HONEY = "minecraft:honey_bottle";
    private static final String GLOW = "minecraft:glow_berries";
    private static final String BLAZE = "minecraft:blaze_powder";
    private static final String MANDRAKE = "hogwartsmagic:mandrake_root";
    private static final String VENOM = "hogwartsmagic:acromantula_venom";
    private static final String GILLY = "hogwartsmagic:gillyweed";
    private static final String SAMPLE = "hogwartsmagic:polyjuice_sample";
    private static final String REDSTONE = "minecraft:redstone";
    private static final String GUNPOWDER = "minecraft:gunpowder";

    /** Applies single actions one by one, as the cauldron does. */
    private static List<Step> run(List<Step> start, Step... actions) {
        List<Step> history = start;
        for (Step action : actions) {
            history = BrewRules.append(history, action);
        }
        return history;
    }

    private static List<Step> run(Step... actions) {
        return run(new ArrayList<>(), actions);
    }

    private static List<Step> simmer(List<Step> history, int seconds) {
        List<Step> out = history;
        for (int i = 0; i < seconds; i++) {
            out = BrewRules.append(out, Step.simmer(1));
        }
        return out;
    }

    private static Step add(String item) {
        return Step.add(item, 1);
    }

    private static Step cw() {
        return Step.stirClockwise(1);
    }

    private static Step ccw() {
        return Step.stirAnticlockwise(1);
    }

    private static List<Step> wiggenweldToSimmer() {
        return run(add(HONEY), add(GLOW), add(GLOW), cw(), cw(), cw());
    }

    private static List<Step> polyjuiceToSimmer() {
        return run(add(VENOM), add(MANDRAKE), add(MANDRAKE), add(GILLY), cw(), cw(), cw(), cw());
    }

    @Test
    void emptyHistoryIsOnTrackAndWater() {
        Match match = BrewRules.match(List.of());
        assertEquals(Status.ON_TRACK, match.status());
        assertNull(match.recipe());
        assertEquals(BrewRules.WATER_COLOR, BrewRules.color(List.of()));
        assertFalse(BrewRules.wantsSimmer(List.of()));
        assertFalse(BrewRules.overbrewClock(List.of()));
    }

    @Test
    void appendMergesTheSameAction() {
        List<Step> history = run(add(GLOW), add(GLOW), cw(), cw(), ccw(), add(GLOW));
        assertEquals(List.of(Step.add(GLOW, 2), Step.stirClockwise(2), Step.stirAnticlockwise(1), Step.add(GLOW, 1)), history);
        assertEquals(List.of(Step.simmer(3)), simmer(new ArrayList<>(), 3));
    }

    @Test
    void wiggenweldFinishesAfterItsSimmer() {
        List<Step> almost = simmer(wiggenweldToSimmer(), 19);
        assertEquals(Status.ON_TRACK, BrewRules.match(almost).status());
        assertSame(BrewRules.WIGGENWELD, BrewRules.match(almost).recipe());
        Match done = BrewRules.match(simmer(almost, 1));
        assertEquals(Status.FINISHED, done.status());
        assertSame(BrewRules.WIGGENWELD, done.recipe());
        assertEquals("wiggenweld", done.recipe().id());
        assertEquals(3, done.recipe().doses());
    }

    @Test
    void pepperupFinishesWithAnticlockwiseStirs() {
        List<Step> history = simmer(run(add(BLAZE), add(MANDRAKE), ccw(), ccw()), 15);
        Match match = BrewRules.match(history);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.PEPPERUP, match.recipe());
    }

    @Test
    void aWrongFirstIngredientRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(add(GUNPOWDER))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(GLOW))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(cw())).status());
    }

    @Test
    void tooFewThenTheNextStepRuins() {
        assertEquals(Status.ON_TRACK, BrewRules.match(run(add(HONEY), add(GLOW))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(HONEY), add(GLOW), cw())).status());
    }

    @Test
    void tooManyRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(add(HONEY), add(GLOW), add(GLOW), add(GLOW))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(wiggenweldToSimmer(), cw())).status());
    }

    @Test
    void theWrongStirDirectionRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(add(BLAZE), add(MANDRAKE), cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(HONEY), add(GLOW), add(GLOW), ccw())).status());
    }

    @Test
    void anythingAfterAFinishedBrewRuins() {
        List<Step> finished = simmer(wiggenweldToSimmer(), 20);
        assertEquals(Status.RUINED, BrewRules.match(run(finished, cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(run(finished, add(REDSTONE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(finished, add(HONEY))).status());
    }

    @Test
    void simmerCountsOnlyAtASimmerStep() {
        assertFalse(BrewRules.wantsSimmer(run(add(HONEY))));
        assertEquals(0, BrewRules.simmerLeft(run(add(HONEY))));
        List<Step> ready = wiggenweldToSimmer();
        assertTrue(BrewRules.wantsSimmer(ready));
        assertEquals(20, BrewRules.simmerLeft(ready));
        assertEquals(12, BrewRules.simmerLeft(simmer(ready, 8)));
        assertTrue(BrewRules.wantsSimmer(simmer(ready, 19)));
        assertFalse(BrewRules.wantsSimmer(simmer(ready, 20)));
        assertEquals(0, BrewRules.simmerLeft(simmer(ready, 20)));
    }

    @Test
    void overbrewClockStartsAfterTheLastSimmer() {
        assertFalse(BrewRules.overbrewClock(wiggenweldToSimmer()));
        assertFalse(BrewRules.overbrewClock(simmer(wiggenweldToSimmer(), 10)));
        assertTrue(BrewRules.overbrewClock(simmer(wiggenweldToSimmer(), 20)));
        List<Step> waiting = simmer(polyjuiceToSimmer(), 480);
        assertEquals(Status.ON_TRACK, BrewRules.match(waiting).status());
        assertFalse(BrewRules.wantsSimmer(waiting));
        assertTrue(BrewRules.overbrewClock(waiting));
        assertFalse(BrewRules.overbrewClock(simmer(polyjuiceToSimmer(), 479)));
        assertFalse(BrewRules.overbrewClock(run(add(HONEY), add(GUNPOWDER))));
    }

    @Test
    void overbrewMath() {
        assertFalse(BrewRules.overbrewed(0));
        assertFalse(BrewRules.overbrewed(120));
        assertTrue(BrewRules.overbrewed(121));
        assertEquals(120, BrewRules.overbrewLeft(0));
        assertEquals(15, BrewRules.overbrewLeft(105));
        assertEquals(0, BrewRules.overbrewLeft(200));
        assertFalse(BrewRules.overbrewWarning(99));
        assertTrue(BrewRules.overbrewWarning(100));
        assertTrue(BrewRules.overbrewWarning(119));
        assertFalse(BrewRules.overbrewWarning(120));
    }

    @Test
    void polyjuiceFinishesWithTheSampleAndTakesItsTail() {
        List<Step> waiting = simmer(polyjuiceToSimmer(), 480);
        List<Step> brewed = run(waiting, add(SAMPLE));
        Match match = BrewRules.match(brewed);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.POLYJUICE, match.recipe());
        assertFalse(BrewRules.usedTail(brewed, REDSTONE));
        assertFalse(BrewRules.usedTail(brewed, GUNPOWDER));

        List<Step> extended = run(brewed, add(REDSTONE));
        assertEquals(Status.FINISHED, BrewRules.match(extended).status());
        assertTrue(BrewRules.usedTail(extended, REDSTONE));

        List<Step> both = run(extended, add(GUNPOWDER));
        assertEquals(Status.FINISHED, BrewRules.match(both).status());
        assertTrue(BrewRules.usedTail(both, REDSTONE));
        assertTrue(BrewRules.usedTail(both, GUNPOWDER));

        List<Step> splashOnly = run(brewed, add(GUNPOWDER));
        assertEquals(Status.FINISHED, BrewRules.match(splashOnly).status());
        assertFalse(BrewRules.usedTail(splashOnly, REDSTONE));
        assertTrue(BrewRules.usedTail(splashOnly, GUNPOWDER));
    }

    @Test
    void polyjuiceTailIsInOrderAndOnce() {
        List<Step> brewed = run(simmer(polyjuiceToSimmer(), 480), add(SAMPLE));
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(GUNPOWDER), add(REDSTONE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(REDSTONE), add(REDSTONE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(REDSTONE), add(GUNPOWDER), add(GUNPOWDER))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(MANDRAKE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(brewed, add(SAMPLE))).status());
        assertFalse(BrewRules.usedTail(run(brewed, add(GUNPOWDER), add(REDSTONE)), GUNPOWDER));
    }

    @Test
    void theSampleTooEarlyRuins() {
        assertEquals(Status.RUINED, BrewRules.match(run(simmer(polyjuiceToSimmer(), 100), add(SAMPLE))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(polyjuiceToSimmer(), add(SAMPLE))).status());
    }

    @Test
    void anEmptySampleIsRefused() {
        assertTrue(BrewRules.refusesEmptySample(SAMPLE, false));
        assertFalse(BrewRules.refusesEmptySample(SAMPLE, true));
        assertFalse(BrewRules.refusesEmptySample(HONEY, false));
    }

    @Test
    void coloursFollowTheSteps() {
        assertEquals(0xD9A33A, BrewRules.color(run(add(HONEY))));
        assertEquals(BrewRules.lerpColor(0xD9A33A, 0xE8C85A, 0.5f), BrewRules.color(run(add(HONEY), add(GLOW))));
        assertEquals(0xE8C85A, BrewRules.color(run(add(HONEY), add(GLOW), add(GLOW))));
        assertEquals(0x4CC38A, BrewRules.color(simmer(wiggenweldToSimmer(), 20)));
        assertEquals(BrewRules.RUINED_COLOR, BrewRules.color(run(add(GUNPOWDER))));
        List<Step> brewed = run(simmer(polyjuiceToSimmer(), 480), add(SAMPLE));
        assertEquals(0x9C8F4A, BrewRules.color(brewed));
        assertEquals(0xA0563E, BrewRules.color(run(brewed, add(REDSTONE))));
        assertEquals(0x7D7468, BrewRules.color(run(brewed, add(REDSTONE), add(GUNPOWDER))));
        assertEquals(0x000000, BrewRules.lerpColor(0x000000, 0xFFFFFF, 0.0f));
        assertEquals(0xFFFFFF, BrewRules.lerpColor(0x000000, 0xFFFFFF, 1.0f));
        assertEquals(0x808080, BrewRules.lerpColor(0x000000, 0xFFFFFF, 0.5f));
        assertEquals(0xFFFFFF, BrewRules.lerpColor(0x000000, 0xFFFFFF, 3.0f));
    }

    @Test
    void ingredientsAreTheRecipeItems() {
        for (String id : List.of(HONEY, GLOW, BLAZE, MANDRAKE, VENOM, GILLY, SAMPLE, REDSTONE, GUNPOWDER)) {
            assertTrue(BrewRules.isIngredient(id), id);
        }
        assertFalse(BrewRules.isIngredient("minecraft:dirt"));
        assertFalse(BrewRules.isIngredient("hogwartsmagic:fine_wood_stick"));
        assertFalse(BrewRules.isIngredient("minecraft:glass_bottle"));
    }

    @Test
    void theTableIsWellFormed() {
        Set<String> ids = new HashSet<>();
        for (Recipe recipe : BrewRules.RECIPES) {
            assertTrue(ids.add(recipe.id()), recipe.id());
            assertEquals(recipe.steps().size(), recipe.colors().size(), recipe.id());
            assertEquals(recipe.tail().size(), recipe.tailColors().size(), recipe.id());
            assertEquals(BrewRules.MAX_DOSES, recipe.doses(), recipe.id());
            assertEquals(BrewRules.Kind.ADD, recipe.steps().get(0).kind(), recipe.id());
            for (int i = 1; i < recipe.steps().size(); i++) {
                assertFalse(recipe.steps().get(i).sameAction(recipe.steps().get(i - 1)), recipe.id() + " step " + i);
            }
            for (Step step : recipe.steps()) {
                assertTrue(step.amount() > 0, recipe.id());
            }
        }
        assertEquals(List.of(BrewRules.WIGGENWELD, BrewRules.PEPPERUP, BrewRules.POLYJUICE), BrewRules.RECIPES);
    }

    @Test
    void kindByName() {
        assertEquals(BrewRules.Kind.SIMMER, BrewRules.Kind.byName("SIMMER"));
        assertEquals(BrewRules.Kind.STIR_ANTICLOCKWISE, BrewRules.Kind.byName("STIR_ANTICLOCKWISE"));
        assertNull(BrewRules.Kind.byName("BOIL"));
        assertNull(BrewRules.Kind.byName(""));
    }
}
```

- [ ] **Step 2: Run it and see it fail.** Run the gradle check (`pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`), then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*BrewRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`package com.hogwartsmagic.brewing does not exist` / `cannot find symbol BrewRules`).

- [ ] **Step 3: Write `BrewRules`.** Create `src/main/java/com/hogwartsmagic/brewing/BrewRules.java`:

```java
package com.hogwartsmagic.brewing;

import java.util.ArrayList;
import java.util.List;

/**
 * Cauldron brewing: the recipe table, the step model, how a history matches the recipes, when time simmers, the
 * overbrew clock and the liquid colour. Pure: ingredients are registry-name strings.
 *
 * A history is a list of runs. Consecutive identical actions merge: two glow berries in a row are one run of 2,
 * and each simmered second adds 1 to the simmer run. A history fits a recipe when every run but the last equals
 * the recipe step at its index and the last run is the same action with an amount up to the step's. After the last
 * required step, runs must be the recipe's optional tail steps, in order, each exactly once. A recipe may not have
 * two consecutive steps of the same action.
 */
public final class BrewRules {
    public static final String HONEY_BOTTLE = "minecraft:honey_bottle";
    public static final String GLOW_BERRIES = "minecraft:glow_berries";
    public static final String BLAZE_POWDER = "minecraft:blaze_powder";
    public static final String REDSTONE = "minecraft:redstone";
    public static final String GUNPOWDER = "minecraft:gunpowder";
    public static final String MANDRAKE_ROOT = "hogwartsmagic:mandrake_root";
    public static final String ACROMANTULA_VENOM = "hogwartsmagic:acromantula_venom";
    public static final String GILLYWEED = "hogwartsmagic:gillyweed";
    public static final String POLYJUICE_SAMPLE = "hogwartsmagic:polyjuice_sample";

    /** A full cauldron; ingredients go only into a full one, so a brew gives this many doses. */
    public static final int MAX_DOSES = 3;
    /** A finished brew (or one waiting after its last simmer) on heat for longer than this is ruined. */
    public static final int OVERBREW_SECONDS = 120;
    /** The last seconds before an overbrew, when the cauldron warns. */
    public static final int OVERBREW_WARNING_SECONDS = 20;
    /** While simmering, the client colour is refreshed this often (and when the step completes). */
    public static final int SIMMER_SYNC_SECONDS = 5;

    /** Plain water. */
    public static final int WATER_COLOR = 0x3F76E4;
    /** A cauldron nobody has used: the green that decorative cauldrons always showed. */
    public static final int CLASSIC_GREEN = 0x3A9E4E;
    /** A ruined brew: grey-brown. */
    public static final int RUINED_COLOR = 0x6B5A45;

    public enum Kind {
        ADD, STIR_CLOCKWISE, STIR_ANTICLOCKWISE, SIMMER;

        /** The kind with this name, or null (an unknown name from a damaged save). */
        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.name().equals(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /**
     * One recipe step, or one run of the history. item: the registry name for ADD, "" otherwise. amount: items,
     * turns or seconds.
     */
    public record Step(Kind kind, String item, int amount) {
        public static Step add(String item, int count) {
            return new Step(Kind.ADD, item, count);
        }

        public static Step stirClockwise(int turns) {
            return new Step(Kind.STIR_CLOCKWISE, "", turns);
        }

        public static Step stirAnticlockwise(int turns) {
            return new Step(Kind.STIR_ANTICLOCKWISE, "", turns);
        }

        public static Step simmer(int seconds) {
            return new Step(Kind.SIMMER, "", seconds);
        }

        /** The same action, whatever the amount. */
        public boolean sameAction(Step other) {
            return kind == other.kind && item.equals(other.item);
        }
    }

    /**
     * colors.get(i): the liquid once step i is done. tail: optional last steps, in order, each at most once, after
     * the brew is finished; tailColors likewise.
     */
    public record Recipe(String id, List<Step> steps, List<Integer> colors, List<Step> tail, List<Integer> tailColors,
                         int doses) {
    }

    public enum Status { ON_TRACK, FINISHED, RUINED }

    /**
     * recipe: the finished recipe (FINISHED); the first recipe the history still fits (ON_TRACK, null for an empty
     * history); null (RUINED).
     */
    public record Match(Status status, Recipe recipe) {
    }

    /**
     * index: the required step the last required run belongs to; done: that run's amount; complete: every required
     * step is done; lastTail: the index of the last tail step used, or -1.
     */
    private record Fit(int index, int done, boolean complete, int lastTail) {
    }

    public static final Recipe WIGGENWELD = new Recipe("wiggenweld",
            List.of(Step.add(HONEY_BOTTLE, 1), Step.add(GLOW_BERRIES, 2), Step.stirClockwise(3), Step.simmer(20)),
            List.of(0xD9A33A, 0xE8C85A, 0x8FD16A, 0x4CC38A),
            List.of(), List.of(), MAX_DOSES);

    public static final Recipe PEPPERUP = new Recipe("pepperup",
            List.of(Step.add(BLAZE_POWDER, 1), Step.add(MANDRAKE_ROOT, 1), Step.stirAnticlockwise(2), Step.simmer(15)),
            List.of(0xE0662B, 0xB5562E, 0xD9452B, 0xE8322A),
            List.of(), List.of(), MAX_DOSES);

    public static final Recipe POLYJUICE = new Recipe("polyjuice",
            List.of(Step.add(ACROMANTULA_VENOM, 1), Step.add(MANDRAKE_ROOT, 2), Step.add(GILLYWEED, 1),
                    Step.stirClockwise(4), Step.simmer(480), Step.add(POLYJUICE_SAMPLE, 1)),
            List.of(0x6E8B3D, 0x5C6E2E, 0x4E7A55, 0x55663A, 0x6B6B2A, 0x9C8F4A),
            List.of(Step.add(REDSTONE, 1), Step.add(GUNPOWDER, 1)),
            List.of(0xA0563E, 0x7D7468),
            MAX_DOSES);

    public static final List<Recipe> RECIPES = List.of(WIGGENWELD, PEPPERUP, POLYJUICE);

    private BrewRules() {
    }

    /** The history after one more action: merged into the last run when it is the same action. */
    public static List<Step> append(List<Step> history, Step action) {
        List<Step> next = new ArrayList<>(history);
        if (!next.isEmpty() && next.get(next.size() - 1).sameAction(action)) {
            Step last = next.remove(next.size() - 1);
            next.add(new Step(last.kind(), last.item(), last.amount() + action.amount()));
        } else {
            next.add(action);
        }
        return next;
    }

    public static Match match(List<Step> history) {
        if (history.isEmpty()) {
            return new Match(Status.ON_TRACK, null);
        }
        Recipe first = null;
        for (Recipe recipe : RECIPES) {
            Fit fit = fit(recipe, history);
            if (fit == null) {
                continue;
            }
            if (fit.complete()) {
                return new Match(Status.FINISHED, recipe);
            }
            if (first == null) {
                first = recipe;
            }
        }
        return first == null ? new Match(Status.RUINED, null) : new Match(Status.ON_TRACK, first);
    }

    /**
     * Seconds of simmering the brew still wants: the rest of the current simmer step, or the whole next step when
     * the current step is done and the next one is a simmer. 0 when time does not count now.
     */
    public static int simmerLeft(List<Step> history) {
        Match match = match(history);
        if (match.status() != Status.ON_TRACK || match.recipe() == null) {
            return 0;
        }
        List<Step> steps = match.recipe().steps();
        Fit fit = fit(match.recipe(), history);
        Step current = steps.get(fit.index());
        if (current.kind() == Kind.SIMMER && fit.done() < current.amount()) {
            return current.amount() - fit.done();
        }
        if (fit.done() == current.amount() && fit.index() + 1 < steps.size()
                && steps.get(fit.index() + 1).kind() == Kind.SIMMER) {
            return steps.get(fit.index() + 1).amount();
        }
        return 0;
    }

    /** Heat makes time count: the brew is at (or right before) a simmer step. */
    public static boolean wantsSimmer(List<Step> history) {
        return simmerLeft(history) > 0;
    }

    /** The overbrew clock runs: the brew is finished, or its last simmer step is done and it waits for the rest. */
    public static boolean overbrewClock(List<Step> history) {
        Match match = match(history);
        if (match.status() == Status.FINISHED) {
            return true;
        }
        if (match.status() == Status.RUINED || match.recipe() == null) {
            return false;
        }
        List<Step> steps = match.recipe().steps();
        int lastSimmer = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).kind() == Kind.SIMMER) {
                lastSimmer = i;
            }
        }
        if (lastSimmer < 0) {
            return false;
        }
        Fit fit = fit(match.recipe(), history);
        return fit.index() > lastSimmer
                || (fit.index() == lastSimmer && fit.done() == steps.get(lastSimmer).amount());
    }

    public static boolean overbrewed(int seconds) {
        return seconds > OVERBREW_SECONDS;
    }

    public static int overbrewLeft(int seconds) {
        return Math.max(0, OVERBREW_SECONDS - seconds);
    }

    /** The last OVERBREW_WARNING_SECONDS before the brew is lost. */
    public static boolean overbrewWarning(int seconds) {
        int left = overbrewLeft(seconds);
        return left > 0 && left <= OVERBREW_WARNING_SECONDS;
    }

    /**
     * The liquid colour (RGB). Inside a step it blends from the previous step's colour by progress; plain water is
     * WATER_COLOR and a ruined brew RUINED_COLOR.
     */
    public static int color(List<Step> history) {
        if (history.isEmpty()) {
            return WATER_COLOR;
        }
        Match match = match(history);
        if (match.status() == Status.RUINED) {
            return RUINED_COLOR;
        }
        Recipe recipe = match.recipe();
        Fit fit = fit(recipe, history);
        if (fit.lastTail() >= 0) {
            return recipe.tailColors().get(fit.lastTail());
        }
        int from = fit.index() == 0 ? WATER_COLOR : recipe.colors().get(fit.index() - 1);
        int to = recipe.colors().get(fit.index());
        return lerpColor(from, to, fit.done() / (float) recipe.steps().get(fit.index()).amount());
    }

    public static int lerpColor(int from, int to, float t) {
        float c = Math.max(0.0f, Math.min(1.0f, t));
        int r = channel(from >> 16, to >> 16, c);
        int g = channel(from >> 8, to >> 8, c);
        int b = channel(from, to, c);
        return (r << 16) | (g << 8) | b;
    }

    /** A finished brew used this tail ingredient (Polyjuice: redstone extends, gunpowder makes it splash). */
    public static boolean usedTail(List<Step> history, String item) {
        Match match = match(history);
        if (match.status() != Status.FINISHED) {
            return false;
        }
        for (int i = match.recipe().steps().size(); i < history.size(); i++) {
            if (history.get(i).item().equals(item)) {
                return true;
            }
        }
        return false;
    }

    /** The item appears as an Add step (or tail step) of some recipe. Other items are not brewing actions. */
    public static boolean isIngredient(String item) {
        for (Recipe recipe : RECIPES) {
            for (Step step : recipe.steps()) {
                if (step.kind() == Kind.ADD && step.item().equals(item)) {
                    return true;
                }
            }
            for (Step step : recipe.tail()) {
                if (step.kind() == Kind.ADD && step.item().equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An empty Polyjuice sample is refused with a line and does not count as a step (or ruin anything). */
    public static boolean refusesEmptySample(String item, boolean sampleFilled) {
        return POLYJUICE_SAMPLE.equals(item) && !sampleFilled;
    }

    private static int channel(int from, int to, float c) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return Math.round(a + (b - a) * c);
    }

    /** How the history fits the recipe, or null when it does not. */
    private static Fit fit(Recipe recipe, List<Step> history) {
        if (history.isEmpty()) {
            return new Fit(-1, 0, false, -1);
        }
        List<Step> steps = recipe.steps();
        int required = Math.min(history.size(), steps.size());
        for (int i = 0; i < required; i++) {
            Step run = history.get(i);
            Step want = steps.get(i);
            if (!run.sameAction(want)) {
                return null;
            }
            boolean lastRun = i == history.size() - 1;
            if (lastRun ? run.amount() > want.amount() : run.amount() != want.amount()) {
                return null;
            }
        }
        int index = required - 1;
        int done = history.get(index).amount();
        boolean complete = history.size() > steps.size()
                || (history.size() == steps.size() && done == steps.get(index).amount());
        int lastTail = -1;
        int next = 0;
        for (int i = steps.size(); i < history.size(); i++) {
            Step run = history.get(i);
            int found = -1;
            for (int t = next; t < recipe.tail().size(); t++) {
                if (recipe.tail().get(t).sameAction(run)) {
                    found = t;
                    break;
                }
            }
            if (found < 0 || run.amount() != recipe.tail().get(found).amount()) {
                return null;
            }
            lastTail = found;
            next = found + 1;
        }
        return new Fit(index, done, complete, lastTail);
    }
}
```

- [ ] **Step 4: Run it and see it pass.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*BrewRulesTest" --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 20 tests pass. Then `timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 359 tests, all green (`find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'` prints `359`).

- [ ] **Step 5: Greps.** `grep -n "import net.minecraft\|import com.hogwartsmagic\|switch" src/main/java/com/hogwartsmagic/brewing/BrewRules.java`. Expected: nothing.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add BrewRules: cauldron recipes, step matching, simmer, overbrew and colours

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/brewing/BrewRules.java src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java
git commit -F /tmp/msg.txt
```

---

### Task 2: Potion items, the stirring rod and PolyjuiceStacks

**Files:**
- Create: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`
- Create: `src/main/java/com/hogwartsmagic/item/StirringRodItem.java`
- Create: `src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceStacks.java`
- Create: `src/main/resources/assets/hogwartsmagic/models/item/wiggenweld_potion.json`, `pepperup_potion.json`, `failed_potion.json`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: nothing from Task 1. Produces:
- `public class BrewedPotionItem extends Item` with `public enum Kind { WIGGENWELD, PEPPERUP, FAILED }` (`String id()`, `int color()`), `public BrewedPotionItem(Properties, Kind)`, `public Kind kind()`.
- `public class StirringRodItem extends Item` with `doesSneakBypassUse(ItemStack, LevelReader, BlockPos, Player)`.
- `public static ItemStack PolyjuiceStacks.make(CompoundTag target, boolean extended, boolean splash)`.
- `ExampleMod.WIGGENWELD_POTION`, `ExampleMod.PEPPERUP_POTION`, `ExampleMod.FAILED_POTION` (`RegistryObject<Item>`).

- [ ] **Step 1: `BrewedPotionItem`.** Create `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`:

```java
package com.hogwartsmagic.item;

import com.hogwartsmagic.spell.support.HealSpell;
import com.hogwartsmagic.spell.support.SupportMana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * A potion brewed in the Wizard Cauldron. Not a vanilla Potion entry: the drink calls the mod's own helpers (the
 * Bleeding cure, mana), which vanilla potion contents cannot do.
 */
public class BrewedPotionItem extends Item {
    public enum Kind {
        WIGGENWELD("wiggenweld_potion", 0x4CC38A),
        PEPPERUP("pepperup_potion", 0xE8322A),
        FAILED("failed_potion", 0x6B5A45);

        private final String id;
        private final int color;

        Kind(String id, int color) {
            this.id = id;
            this.color = color;
        }

        /** The item registry path. */
        public String id() {
            return id;
        }

        /** RGB tint of the bottle's liquid. */
        public int color() {
            return color;
        }
    }

    /** Regeneration IV heals 1 HP every 6 ticks: 48 ticks = 8 HP over 2.4 s. */
    public static final int WIGGENWELD_REGEN_TICKS = 48;
    public static final int WIGGENWELD_REGEN_AMPLIFIER = 3;
    public static final float PEPPERUP_MANA = 40.0f;
    public static final int PEPPERUP_FIRE_RESISTANCE_TICKS = 20 * 30;
    public static final int FAILED_NAUSEA_TICKS = 20 * 10;
    public static final int FAILED_POISON_TICKS = 20 * 3;

    private final Kind kind;

    public BrewedPotionItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker) {
        if (!level.isClientSide) {
            applyDrink(level, drinker);
        }
        if (drinker instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    return new ItemStack(Items.GLASS_BOTTLE);
                }
                if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                    player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
                }
            }
        }
        drinker.gameEvent(GameEvent.DRINK);
        return stack;
    }

    private void applyDrink(Level level, LivingEntity drinker) {
        if (kind == Kind.WIGGENWELD) {
            // The only removal path for Bleeding that the drink guard lets through.
            HealSpell.cureBleeding(drinker);
            drinker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, WIGGENWELD_REGEN_TICKS, WIGGENWELD_REGEN_AMPLIFIER));
        } else if (kind == Kind.PEPPERUP) {
            if (drinker instanceof ServerPlayer player) {
                SupportMana.add(player, PEPPERUP_MANA);
            }
            drinker.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, PEPPERUP_FIRE_RESISTANCE_TICKS, 0));
            if (level instanceof ServerLevel server) {
                // Steam from the ears.
                server.sendParticles(ParticleTypes.CLOUD, drinker.getX(), drinker.getEyeY() + 0.1, drinker.getZ(),
                        14, 0.3, 0.05, 0.3, 0.03);
                server.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.5f, 1.6f);
            }
        } else {
            drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FAILED_NAUSEA_TICKS, 0));
            drinker.addEffect(new MobEffectInstance(MobEffects.POISON, FAILED_POISON_TICKS, 0));
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic." + kind.id() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
```

- [ ] **Step 2: `StirringRodItem`.** Create `src/main/java/com/hogwartsmagic/item/StirringRodItem.java`:

```java
package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.LevelReader;

import java.util.List;

/**
 * The fine wood stick, which is also the cauldron's stirring rod. Vanilla skips the block when a sneaking player
 * holds an item; at a Wizard Cauldron this rod lets the click through, so sneaking stirs anticlockwise. Forge needs
 * both hands to allow it: an empty off hand does.
 */
public class StirringRodItem extends Item {
    public StirringRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockState(pos).is(ExampleMod.WIZARD_CAULDRON.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.fine_wood_stick.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
```

- [ ] **Step 3: `PolyjuiceStacks`.** Create `src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceStacks.java`. The code is the stand recipes' output logic (`PolyjuiceBrewingRecipe.getOutput`, `PolyjuiceRedstoneExtendBrewingRecipe.getOutput`, `PolyjuiceSplashBrewingRecipe.getOutput`) in one place:

```java
package com.hogwartsmagic.polyjuice;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.PolyjuicePotionItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * Builds a Polyjuice bottle for a sampled target, as the brewing stand recipes did: a polyjuice_potion with a 3:00
 * effect, 8:00 when extended, or a vanilla splash potion with the same contents and target when splash (only
 * Items.SPLASH_POTION is handled on impact, in ModEvents.onProjectileImpact).
 */
public final class PolyjuiceStacks {
    private PolyjuiceStacks() {
    }

    public static ItemStack make(CompoundTag target, boolean extended, boolean splash) {
        MobEffectInstance polyjuice = extended
                ? new MobEffectInstance(ExampleMod.POLYJUICE_EFFECT.getHolder().get(),
                        PolyjuicePotionItem.EXTENDED_DISGUISE_DURATION_TICKS, 0, false, false, true)
                : new MobEffectInstance(ExampleMod.POLYJUICE_EFFECT.getHolder().get(),
                        PolyjuicePotionItem.BASE_DISGUISE_DURATION_TICKS, 0);
        ItemStack out = new ItemStack(splash ? Items.SPLASH_POTION : ExampleMod.POLYJUICE_POTION.get());
        out.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), List.of(polyjuice)));
        PolyjuiceData.setPolyjuiceTarget(out, target);
        if (splash) {
            out.set(DataComponents.CUSTOM_NAME, Component.translatable("item.hogwartsmagic.splash_polyjuice_potion"));
        }
        return out;
    }
}
```

- [ ] **Step 4: Register the items.** In `ExampleMod.java`, find:

```java
    public static final RegistryObject<Item> FINE_WOOD_STICK = ITEMS.register("fine_wood_stick",
            () -> new Item(new Item.Properties()));
```

Replace with:

```java
    public static final RegistryObject<Item> FINE_WOOD_STICK = ITEMS.register("fine_wood_stick",
            () -> new com.hogwartsmagic.item.StirringRodItem(new Item.Properties()));
```

Find:

```java
    public static final RegistryObject<Item> POLYJUICE_POTION = ITEMS.register("polyjuice_potion",
            () -> new PolyjuicePotionItem(new Item.Properties()
                    .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
                    .component(DataComponents.POTION_CONTENTS, PolyjuicePotionItem.defaultPotionContents())));
```

Replace with:

```java
    public static final RegistryObject<Item> POLYJUICE_POTION = ITEMS.register("polyjuice_potion",
            () -> new PolyjuicePotionItem(new Item.Properties()
                    .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
                    .component(DataComponents.POTION_CONTENTS, PolyjuicePotionItem.defaultPotionContents())));
    // Wizard Cauldron potions (brewing/BrewRules).
    public static final RegistryObject<Item> WIGGENWELD_POTION = ITEMS.register("wiggenweld_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.WIGGENWELD));
    public static final RegistryObject<Item> PEPPERUP_POTION = ITEMS.register("pepperup_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.PEPPERUP));
    public static final RegistryObject<Item> FAILED_POTION = ITEMS.register("failed_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FAILED));
```

In the creative tab, find:

```java
                output.accept(POLYJUICE_POTION.get());
```

Replace with:

```java
                output.accept(POLYJUICE_POTION.get());
                output.accept(WIGGENWELD_POTION.get());
                output.accept(PEPPERUP_POTION.get());
                output.accept(FAILED_POTION.get());
```

- [ ] **Step 5: Item models.** Create these three files, each a vanilla potion bottle whose layer 0 (the liquid) is tinted:

`src/main/resources/assets/hogwartsmagic/models/item/wiggenweld_potion.json`, `src/main/resources/assets/hogwartsmagic/models/item/pepperup_potion.json` and `src/main/resources/assets/hogwartsmagic/models/item/failed_potion.json`, all with this content:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/potion_overlay",
    "layer1": "minecraft:item/potion"
  }
}
```

- [ ] **Step 6: Item colours.** In `ClientModEvents.java`, find:

```java
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> event.getBlockColors()
                        .getColor(ExampleMod.FINE_WOOD_LEAVES.get().defaultBlockState(), null, null, tintIndex),
                ExampleMod.FINE_WOOD_LEAVES_ITEM.get());
    }
```

Replace with:

```java
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> event.getBlockColors()
                        .getColor(ExampleMod.FINE_WOOD_LEAVES.get().defaultBlockState(), null, null, tintIndex),
                ExampleMod.FINE_WOOD_LEAVES_ITEM.get());
        // Cauldron potions: layer 0 is the vanilla potion_overlay, tinted with the potion's colour.
        event.register(
                (stack, tintIndex) -> tintIndex == 0 && stack.getItem() instanceof com.hogwartsmagic.item.BrewedPotionItem potion
                        ? net.minecraft.util.FastColor.ARGB32.opaque(potion.kind().color())
                        : -1,
                ExampleMod.WIGGENWELD_POTION.get(), ExampleMod.PEPPERUP_POTION.get(), ExampleMod.FAILED_POTION.get());
    }
```

- [ ] **Step 7: Text.** In `en_us.json`, find the last entry:

```
  "message.hogwartsmagic.colloportus.lifted": "You lift your seal."
}
```

Replace with:

```
  "message.hogwartsmagic.colloportus.lifted": "You lift your seal.",
  "item.hogwartsmagic.wiggenweld_potion": "Wiggenweld Potion",
  "item.hogwartsmagic.wiggenweld_potion.tooltip": "Heals 8 health over 2 seconds and stops Bleeding.",
  "item.hogwartsmagic.pepperup_potion": "Pepperup Potion",
  "item.hogwartsmagic.pepperup_potion.tooltip": "Restores 40 mana and gives 30 seconds of Fire Resistance.",
  "item.hogwartsmagic.failed_potion": "Failed Potion",
  "item.hogwartsmagic.failed_potion.tooltip": "A ruined brew. Drinking it is a bad idea.",
  "item.hogwartsmagic.fine_wood_stick.tooltip": "Stirring rod: right-click a Wizard Cauldron to stir clockwise, sneak and right-click to stir anticlockwise."
}
```

Validate: `python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"` and the same for the three item models. Expected: no output.

- [ ] **Step 8: Build.** Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 359 tests, all green.
- `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch" src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/item/StirringRodItem.java src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceStacks.java`. Expected: nothing.

- [ ] **Step 9: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add the cauldron potion items, the stirring rod and PolyjuiceStacks

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/item/StirringRodItem.java src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceStacks.java src/main/resources/assets/hogwartsmagic/models/item/wiggenweld_potion.json src/main/resources/assets/hogwartsmagic/models/item/pepperup_potion.json src/main/resources/assets/hogwartsmagic/models/item/failed_potion.json src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -F /tmp/msg.txt
```

---

### Task 3: The working cauldron, and Polyjuice leaves the brewing stand

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/block/WizardCauldronBlock.java` (rewritten)
- Create: `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Create: `src/main/resources/data/hogwartsmagic/tags/block/cauldron_heat.json`
- Modify: `src/main/resources/assets/hogwartsmagic/blockstates/wizard_cauldron.json`
- Create: `build_scripts/generate_wizard_cauldron_level_models.py`
- Modify: `src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron.json` (by the script)
- Create: `src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_empty.json`, `wizard_cauldron_level1.json`, `wizard_cauldron_level2.json` (by the script)
- Delete: `src/main/java/com/hogwartsmagic/polyjuice/StabilizedPolyjuiceBaseBrewingRecipe.java`, `PolyjuiceBrewingRecipe.java`, `PolyjuiceRedstoneExtendBrewingRecipe.java`, `PolyjuiceSplashBrewingRecipe.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: all of `BrewRules` (Task 1); `ExampleMod.WIGGENWELD_POTION`, `PEPPERUP_POTION`, `FAILED_POTION`, `PolyjuiceStacks.make` (Task 2); `PolyjuiceData.hasSample(ItemStack)`, `getSample(ItemStack)` (existing). Produces:
- `WizardCauldronBlock.LEVEL` (`IntegerProperty`, 0..3), `WizardCauldronBlock.CAULDRON_HEAT` (`TagKey<Block>`), `public static boolean WizardCauldronBlock.isHeated(Level, BlockPos)`.
- `WizardCauldronBlockEntity(BlockPos, BlockState)`, `public int getColor()`, `public static boolean handles(ItemStack)`, `public void useItem(ServerLevel, ItemStack, Player, InteractionHand)`, `public void showStatus(Player)`, `public static void serverTick(Level, BlockPos, BlockState, WizardCauldronBlockEntity)`.
- `ExampleMod.WIZARD_CAULDRON_BLOCK_ENTITY`.

- [ ] **Step 1: The block.** Replace the whole of `src/main/java/com/hogwartsmagic/block/WizardCauldronBlock.java` (it is the 51-line plain `Block` quoted in the spec's code facts, starting `public class WizardCauldronBlock extends Block {`) with:

```java
package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.WizardCauldronBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Wizard Cauldron: a brewing station. LEVEL is the water doses (0..3) and picks the model. The default state is
 * full (3), so cauldrons from structures and from worlds saved before brewing still look full; a cauldron placed by a
 * player starts empty. Everything else lives in {@link WizardCauldronBlockEntity}.
 */
public class WizardCauldronBlock extends BaseEntityBlock {
    public static final MapCodec<WizardCauldronBlock> CODEC = simpleCodec(WizardCauldronBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    /** Blocks that heat a cauldron from below. Campfires count only when lit. */
    public static final TagKey<Block> CAULDRON_HEAT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "cauldron_heat"));
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0);

    public WizardCauldronBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> 7).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 3));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LEVEL, 0);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WizardCauldronBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only with liquid; the ticker itself does work once a second and only with heat below.
        if (level.isClientSide || state.getValue(LEVEL) == 0) {
            return null;
        }
        return createTickerHelper(type, ExampleMod.WIZARD_CAULDRON_BLOCK_ENTITY.get(), WizardCauldronBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (!WizardCauldronBlockEntity.handles(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        WizardCauldronBlockEntity cauldron = cauldronAt(level, pos, state);
        if (cauldron == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        cauldron.useItem((ServerLevel) level, stack, player, hand);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        WizardCauldronBlockEntity cauldron = cauldronAt(level, pos, state);
        if (cauldron == null) {
            return InteractionResult.PASS;
        }
        cauldron.showStatus(player);
        return InteractionResult.CONSUME;
    }

    /**
     * The cauldron's block entity. On the server thread Level.getBlockEntity creates a missing one for an
     * EntityBlock (LevelChunk.EntityCreationType.IMMEDIATE), which covers cauldrons saved before brewing existed.
     * If it still returns null, one is created here.
     */
    @Nullable
    private static WizardCauldronBlockEntity cauldronAt(Level level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof WizardCauldronBlockEntity cauldron) {
            return cauldron;
        }
        if (blockEntity == null) {
            WizardCauldronBlockEntity created = new WizardCauldronBlockEntity(pos, state);
            level.setBlockEntity(created);
            return created;
        }
        return null;
    }

    /** Heat below: a block in #hogwartsmagic:cauldron_heat, and lit if it can be lit. Reads one loaded block. */
    public static boolean isHeated(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(CAULDRON_HEAT)) {
            return false;
        }
        return !below.hasProperty(BlockStateProperties.LIT) || below.getValue(BlockStateProperties.LIT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEVEL) > 0 && random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.WITCH,
                    pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.8 + random.nextDouble() * 0.2,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0.0, 0.02, 0.0);
        }
    }
}
```

- [ ] **Step 2: The block entity.** Create `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`:

```java
package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.WizardCauldronBlock;
import com.hogwartsmagic.brewing.BrewRules;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.hogwartsmagic.polyjuice.PolyjuiceStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The brew in a Wizard Cauldron: water doses, the history of steps (the last simmer run is the simmer counter), the
 * ruined flag, the overbrew seconds, the Polyjuice target and the liquid colour. Every rule comes from BrewRules.
 * A cauldron nobody has used (from a structure or an old world) is not initialized: it shows the classic green and
 * becomes plain water (doses = its block state level) on first use.
 */
public class WizardCauldronBlockEntity extends BlockEntity {
    private boolean initialized;
    private int doses;
    private List<BrewRules.Step> history = new ArrayList<>();
    private boolean ruined;
    private int overbrewSeconds;
    private CompoundTag polyjuiceTarget = new CompoundTag();
    private int color = BrewRules.CLASSIC_GREEN;
    /** Simmered seconds since the client colour was last refreshed. Not saved. */
    private int simmerSinceSync;

    public WizardCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.WIZARD_CAULDRON_BLOCK_ENTITY.get(), pos, state);
    }

    /** RGB of the liquid, read by the client BlockColor. */
    public int getColor() {
        return color;
    }

    /** The items the cauldron reacts to; anything else passes through to vanilla. Same answer on both sides. */
    public static boolean handles(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE)
                || stack.is(ExampleMod.FINE_WOOD_STICK.get()) || isWaterBottle(stack)) {
            return true;
        }
        return BrewRules.isIngredient(itemId(stack));
    }

    public static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION)
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ---------------------------------------------------------------- interactions (server only)

    public void useItem(ServerLevel level, ItemStack stack, Player player, InteractionHand hand) {
        ensureInitialized();
        if (stack.is(Items.WATER_BUCKET)) {
            pourWater(level, player, hand, stack, BrewRules.MAX_DOSES, true);
        } else if (isWaterBottle(stack)) {
            pourWater(level, player, hand, stack, doses + 1, false);
        } else if (stack.is(Items.BUCKET)) {
            useBucket(level, player, hand, stack);
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            bottle(level, player, hand, stack);
        } else if (stack.is(ExampleMod.FINE_WOOD_STICK.get())) {
            stir(level, player);
        } else {
            addIngredient(level, player, hand, stack);
        }
    }

    public void showStatus(Player player) {
        ensureInitialized();
        player.displayClientMessage(statusLine(), true);
    }

    private Component statusLine() {
        if (doses == 0) {
            return line("status.empty");
        }
        if (ruined) {
            return line("status.ruined");
        }
        if (history.isEmpty()) {
            return line("status.water", doses);
        }
        boolean heated = WizardCauldronBlock.isHeated(this.level, this.worldPosition);
        BrewRules.Match match = BrewRules.match(history);
        if (match.status() == BrewRules.Status.FINISHED) {
            Component name = Component.translatable("brew.hogwartsmagic." + match.recipe().id());
            return heated
                    ? line("status.ready_heat", name, doses, BrewRules.overbrewLeft(overbrewSeconds))
                    : line("status.ready", name, doses);
        }
        if (BrewRules.wantsSimmer(history)) {
            return line(heated ? "status.simmering" : "status.needs_heat", BrewRules.simmerLeft(history));
        }
        if (BrewRules.overbrewClock(history)) {
            return heated ? line("status.waiting", BrewRules.overbrewLeft(overbrewSeconds)) : line("status.waiting_cold");
        }
        return line("status.brewing");
    }

    private void pourWater(ServerLevel level, Player player, InteractionHand hand, ItemStack stack, int newDoses, boolean bucket) {
        if (ruined || !history.isEmpty()) {
            tell(player, "already_brewing");
            return;
        }
        if (doses >= BrewRules.MAX_DOSES) {
            tell(player, "full");
            return;
        }
        doses = Math.min(BrewRules.MAX_DOSES, newDoses);
        ItemStack empty = new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE);
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, empty));
        level.playSound(null, worldPosition, bucket ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        changed();
    }

    private void useBucket(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        if (ruined) {
            reset();
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.sendParticles(ParticleTypes.SMOKE, cx(), surfaceY(), cz(), 8, 0.2, 0.05, 0.2, 0.01);
            tell(player, "poured");
            changed();
            return;
        }
        if (history.isEmpty() && doses == BrewRules.MAX_DOSES) {
            reset();
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            changed();
            return;
        }
        tell(player, "bucket_refused");
    }

    private void bottle(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        ItemStack result;
        if (ruined) {
            result = new ItemStack(ExampleMod.FAILED_POTION.get());
        } else if (history.isEmpty()) {
            result = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        } else {
            BrewRules.Match match = BrewRules.match(history);
            if (match.status() != BrewRules.Status.FINISHED) {
                tell(player, "not_ready");
                return;
            }
            result = bottledPotion(match.recipe());
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        doses--;
        if (doses == 0) {
            reset();
        }
        changed();
    }

    private ItemStack bottledPotion(BrewRules.Recipe recipe) {
        String id = recipe.id();
        if (BrewRules.WIGGENWELD.id().equals(id)) {
            return new ItemStack(ExampleMod.WIGGENWELD_POTION.get());
        }
        if (BrewRules.PEPPERUP.id().equals(id)) {
            return new ItemStack(ExampleMod.PEPPERUP_POTION.get());
        }
        if (BrewRules.POLYJUICE.id().equals(id) && !polyjuiceTarget.isEmpty()) {
            return PolyjuiceStacks.make(polyjuiceTarget,
                    BrewRules.usedTail(history, BrewRules.REDSTONE),
                    BrewRules.usedTail(history, BrewRules.GUNPOWDER));
        }
        return new ItemStack(ExampleMod.FAILED_POTION.get());
    }

    private void stir(ServerLevel level, Player player) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        if (ruined) {
            tell(player, "ruined_already");
            return;
        }
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4f, 1.4f);
        level.sendParticles(ParticleTypes.SPLASH, cx(), surfaceY(), cz(), 6, 0.2, 0.02, 0.2, 0.05);
        if (history.isEmpty()) {
            tell(player, "stir_water");
            return;
        }
        BrewRules.Step turn = player.isShiftKeyDown()
                ? BrewRules.Step.stirAnticlockwise(1)
                : BrewRules.Step.stirClockwise(1);
        applyStep(level, turn, player);
        changed();
    }

    private void addIngredient(ServerLevel level, Player player, InteractionHand hand, ItemStack stack) {
        if (doses == 0) {
            tell(player, "empty");
            return;
        }
        if (ruined) {
            tell(player, "ruined_already");
            return;
        }
        if (history.isEmpty() && doses < BrewRules.MAX_DOSES) {
            tell(player, "needs_full", doses);
            return;
        }
        String id = itemId(stack);
        boolean filledSample = stack.is(ExampleMod.POLYJUICE_SAMPLE.get()) && PolyjuiceData.hasSample(stack);
        if (BrewRules.refusesEmptySample(id, filledSample)) {
            tell(player, "empty_sample");
            return;
        }
        CompoundTag sample = filledSample ? PolyjuiceData.getSample(stack) : null;
        consumeOne(player, hand, stack);
        boolean accepted = applyStep(level, BrewRules.Step.add(id, 1), player);
        if (accepted && sample != null) {
            // BrewRules sees only the item name; the target travels with the brew.
            polyjuiceTarget = sample;
        }
        changed();
    }

    private static void consumeOne(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.HONEY_BOTTLE)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            return;
        }
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
    }

    /** Adds one action to the history. Returns false when it ruined the brew. The caller syncs. */
    private boolean applyStep(ServerLevel level, BrewRules.Step action, @Nullable Player player) {
        boolean wasFinished = BrewRules.match(history).status() == BrewRules.Status.FINISHED;
        history = BrewRules.append(history, action);
        BrewRules.Match match = BrewRules.match(history);
        if (match.status() == BrewRules.Status.RUINED) {
            ruin(level, player);
            return false;
        }
        if (match.status() == BrewRules.Status.FINISHED && !wasFinished) {
            finish(level, match.recipe(), player);
            return true;
        }
        level.playSound(null, worldPosition, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.8f,
                1.0f + level.random.nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, cx(), surfaceY(), cz(), 6, 0.2, 0.05, 0.2, 0.01);
        return true;
    }

    private void ruin(ServerLevel level, @Nullable Player player) {
        ruined = true;
        history = new ArrayList<>();
        overbrewSeconds = 0;
        polyjuiceTarget = new CompoundTag();
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.7f);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
        level.sendParticles(ParticleTypes.SMOKE, cx(), surfaceY(), cz(), 16, 0.25, 0.1, 0.25, 0.02);
        if (player != null) {
            tell(player, "ruin");
        }
    }

    private void finish(ServerLevel level, BrewRules.Recipe recipe, @Nullable Player player) {
        doses = recipe.doses();
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.END_ROD, cx(), surfaceY() + 0.1, cz(), 16, 0.25, 0.2, 0.25, 0.02);
        if (player != null) {
            tell(player, "finished", Component.translatable("brew.hogwartsmagic." + recipe.id()));
        }
    }

    private void reset() {
        doses = 0;
        history = new ArrayList<>();
        ruined = false;
        overbrewSeconds = 0;
        polyjuiceTarget = new CompoundTag();
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        BlockState state = getBlockState();
        int fill = state.hasProperty(WizardCauldronBlock.LEVEL) ? state.getValue(WizardCauldronBlock.LEVEL) : 0;
        reset();
        doses = fill; // a structure or old-world cauldron (level 3) becomes 3 doses of plain water
        changed();
    }

    /** Recomputes the colour, keeps the block state level equal to the doses, saves and sends the change. */
    private void changed() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        this.color = !initialized ? BrewRules.CLASSIC_GREEN
                : ruined ? BrewRules.RUINED_COLOR
                : BrewRules.color(history);
        BlockState state = getBlockState();
        if (state.hasProperty(WizardCauldronBlock.LEVEL) && state.getValue(WizardCauldronBlock.LEVEL) != doses) {
            serverLevel.setBlock(worldPosition, state.setValue(WizardCauldronBlock.LEVEL, doses), Block.UPDATE_ALL);
        }
        setChanged();
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    // ---------------------------------------------------------------- ticker

    public static void serverTick(Level level, BlockPos pos, BlockState state, WizardCauldronBlockEntity cauldron) {
        if (level.getGameTime() % 20L != Math.floorMod(pos.asLong(), 20L)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            cauldron.secondTick(serverLevel);
        }
    }

    private void secondTick(ServerLevel level) {
        if (!initialized || ruined || doses == 0 || history.isEmpty()) {
            return;
        }
        if (!WizardCauldronBlock.isHeated(level, worldPosition)) {
            return; // off the heat nothing simmers and nothing overbrews
        }
        if (BrewRules.wantsSimmer(history)) {
            history = BrewRules.append(history, BrewRules.Step.simmer(1));
            level.sendParticles(ParticleTypes.BUBBLE_POP, cx(), surfaceY(), cz(), 3, 0.2, 0.02, 0.2, 0.0);
            BrewRules.Match match = BrewRules.match(history);
            if (match.status() == BrewRules.Status.FINISHED) {
                finish(level, match.recipe(), null);
                simmerSinceSync = 0;
                changed();
                return;
            }
            if (!BrewRules.wantsSimmer(history)) {
                level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8f, 1.2f);
                simmerSinceSync = 0;
                changed();
                return;
            }
            simmerSinceSync++;
            if (simmerSinceSync >= BrewRules.SIMMER_SYNC_SECONDS) {
                simmerSinceSync = 0;
                changed();
            } else {
                setChanged();
            }
            return;
        }
        if (BrewRules.overbrewClock(history)) {
            overbrewSeconds++;
            if (BrewRules.overbrewed(overbrewSeconds)) {
                ruin(level, null);
                changed();
                return;
            }
            if (BrewRules.overbrewWarning(overbrewSeconds)) {
                level.playSound(null, worldPosition, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6f, 1.0f);
            }
            if (BrewRules.match(history).status() == BrewRules.Status.FINISHED) {
                level.sendParticles(ParticleTypes.END_ROD, cx(), surfaceY() + 0.1, cz(), 2, 0.2, 0.1, 0.2, 0.01);
            }
            setChanged();
        }
    }

    // ---------------------------------------------------------------- save and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Initialized", initialized);
        tag.putInt("Doses", doses);
        tag.putBoolean("Ruined", ruined);
        tag.putInt("Overbrew", overbrewSeconds);
        tag.putInt("Color", color);
        ListTag list = new ListTag();
        for (BrewRules.Step step : history) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Kind", step.kind().name());
            entry.putString("Item", step.item());
            entry.putInt("Amount", step.amount());
            list.add(entry);
        }
        tag.put("History", list);
        if (!polyjuiceTarget.isEmpty()) {
            tag.put("PolyjuiceTarget", polyjuiceTarget.copy());
        }
    }

    /** Also reads the small client update tag (Initialized, Doses, Color): missing keys fall back to defaults. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        initialized = tag.getBoolean("Initialized");
        doses = Mth.clamp(tag.getInt("Doses"), 0, BrewRules.MAX_DOSES);
        ruined = tag.getBoolean("Ruined");
        overbrewSeconds = tag.getInt("Overbrew");
        history = new ArrayList<>();
        ListTag list = tag.getList("History", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BrewRules.Kind kind = BrewRules.Kind.byName(entry.getString("Kind"));
            if (kind == null) {
                ruined = true; // a damaged save: a ruined brew, never a crash
                continue;
            }
            history.add(new BrewRules.Step(kind, entry.getString("Item"), entry.getInt("Amount")));
        }
        polyjuiceTarget = tag.getCompound("PolyjuiceTarget").copy();
        color = tag.contains("Color") ? tag.getInt("Color") : BrewRules.CLASSIC_GREEN;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Initialized", initialized);
        tag.putInt("Doses", doses);
        tag.putInt("Color", color);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        if (this.level != null && this.level.isClientSide) {
            // The colour is not in the block state: re-mesh this block so the BlockColor runs again.
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    // ---------------------------------------------------------------- helpers

    private double cx() {
        return worldPosition.getX() + 0.5;
    }

    private double cz() {
        return worldPosition.getZ() + 0.5;
    }

    private double surfaceY() {
        return worldPosition.getY() + 0.65;
    }

    private static Component line(String key, Object... args) {
        return Component.translatable("message.hogwartsmagic.cauldron." + key, args);
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(line(key, args), true);
    }
}
```

- [ ] **Step 3: Register the block entity type and drop the stand recipes.** In `ExampleMod.java`, find:

```java
    public static final RegistryObject<Block> WIZARD_CAULDRON = BLOCKS.register("wizard_cauldron",
            () -> new com.hogwartsmagic.block.WizardCauldronBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).strength(1.5f)));
```

Replace with:

```java
    public static final RegistryObject<Block> WIZARD_CAULDRON = BLOCKS.register("wizard_cauldron",
            () -> new com.hogwartsmagic.block.WizardCauldronBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON).strength(1.5f)));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.WizardCauldronBlockEntity>> WIZARD_CAULDRON_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("wizard_cauldron", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.WizardCauldronBlockEntity::new,
                    WIZARD_CAULDRON.get()
            ).build(null));
```

Find:

```java
    private static void onRegisterBrewingRecipes(BrewingRecipeRegisterEvent event) {
        event.addRecipe(new com.hogwartsmagic.polyjuice.WizardPotionBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.StabilizedPolyjuiceBaseBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.PolyjuiceBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.PolyjuiceRedstoneExtendBrewingRecipe());
        event.addRecipe(new com.hogwartsmagic.polyjuice.PolyjuiceSplashBrewingRecipe());
        LOGGER.info("Registered mana regeneration / Polyjuice brewing recipes (awkward+acromantula venom -> mana regeneration; mana regeneration+mandrake root -> stabilized base; stabilized base+sample -> polyjuice; polyjuice+redstone -> extended; polyjuice+gunpowder -> splash)");
    }
```

Replace with:

```java
    private static void onRegisterBrewingRecipes(BrewingRecipeRegisterEvent event) {
        event.addRecipe(new com.hogwartsmagic.polyjuice.WizardPotionBrewingRecipe());
        // Polyjuice is brewed only in the Wizard Cauldron (BrewRules.POLYJUICE). The stabilized_polyjuice_base potion
        // stays registered so bottles already in worlds still load, but nothing makes or uses it any more.
        LOGGER.info("Registered the mana regeneration brewing recipe (awkward + acromantula venom); Polyjuice is brewed in the Wizard Cauldron");
    }
```

- [ ] **Step 4: Delete the four stand recipe classes.**

```bash
cd /home/user/minecraft_mods
git rm src/main/java/com/hogwartsmagic/polyjuice/StabilizedPolyjuiceBaseBrewingRecipe.java src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceBrewingRecipe.java src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceRedstoneExtendBrewingRecipe.java src/main/java/com/hogwartsmagic/polyjuice/PolyjuiceSplashBrewingRecipe.java
```

- [ ] **Step 5: The heat tag.** Create `src/main/resources/data/hogwartsmagic/tags/block/cauldron_heat.json`:

```json
{
  "replace": false,
  "values": [
    "minecraft:campfire",
    "minecraft:soul_campfire",
    "minecraft:fire",
    "minecraft:soul_fire",
    "minecraft:lava",
    "minecraft:magma_block"
  ]
}
```

- [ ] **Step 6: The level models.** Create `build_scripts/generate_wizard_cauldron_level_models.py`:

```python
"""Write the per-level Wizard Cauldron block models from the full one.

    python3 build_scripts/generate_wizard_cauldron_level_models.py

models/block/wizard_cauldron.json is the full pot (blockstate level=3). Its brew surface is the one element whose
only face is an "up" face with the #brew texture. This script gives that face tintindex 0 (the client BlockColor
colours it from the cauldron's block entity) and writes three copies with the surface moved or removed:

  wizard_cauldron_empty.json    level 0: no surface
  wizard_cauldron_level1.json   level 1: the surface at y 5, across the belly (3..13)
  wizard_cauldron_level2.json   level 2: the surface at y 7, across the belly (3..13)
  wizard_cauldron.json          level 3: the surface stays at y 9.5, inside the neck (4..12)

The belly walls run from 2 to 3 and 13 to 14 between y 3 and 9, so the lower surfaces are 10 wide; the neck walls
leave 4..12 above y 9. Running the script again changes nothing.
"""

import json
import os

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                         "assets", "hogwartsmagic", "models", "block")

# name: (surface height, low edge, high edge)
LEVELS = {
    "wizard_cauldron_level1": (5, 3, 13),
    "wizard_cauldron_level2": (7, 3, 13),
}


def is_surface(element):
    faces = element.get("faces", {})
    return list(faces.keys()) == ["up"] and faces["up"].get("texture") == "#brew"


def write(name, model):
    with open(os.path.join(MODEL_DIR, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")


def main():
    with open(os.path.join(MODEL_DIR, "wizard_cauldron.json"), encoding="utf-8") as fh:
        full = json.load(fh)
    surfaces = [e for e in full["elements"] if is_surface(e)]
    if len(surfaces) != 1:
        raise SystemExit("expected one brew surface in wizard_cauldron.json, found %d" % len(surfaces))
    surfaces[0]["faces"]["up"]["tintindex"] = 0
    write("wizard_cauldron", full)

    pot = [e for e in full["elements"] if not is_surface(e)]
    write("wizard_cauldron_empty", dict(full, elements=pot))
    for name, (y, lo, hi) in LEVELS.items():
        surface = {
            "from": [lo, y, lo],
            "to": [hi, y, hi],
            "faces": {"up": {"texture": "#brew", "tintindex": 0}},
        }
        write(name, dict(full, elements=pot + [surface]))
    print("wrote 4 cauldron models to", os.path.abspath(MODEL_DIR))


if __name__ == "__main__":
    main()
```

Run `cd /home/user/minecraft_mods && python3 build_scripts/generate_wizard_cauldron_level_models.py`. Expected: `wrote 4 cauldron models to .../models/block`. Then `git diff --stat src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron.json`. Expected: `1 file changed, 2 insertions(+), 1 deletion(-)` (the `"texture": "#brew"` line gains a comma and `"tintindex": 0` is added). If the diff is larger, the file was not written by `json.dump(indent=2)` before: stop and report the diff. Run the script a second time and check `git status --short src/main/resources/assets/hogwartsmagic/models/block/` shows the same four files (nothing new changed).

- [ ] **Step 7: The blockstate.** Replace the whole of `src/main/resources/assets/hogwartsmagic/blockstates/wizard_cauldron.json`, which is:

```json
{
  "variants": {
    "": { "model": "hogwartsmagic:block/wizard_cauldron" }
  }
}
```

with:

```json
{
  "variants": {
    "level=0": { "model": "hogwartsmagic:block/wizard_cauldron_empty" },
    "level=1": { "model": "hogwartsmagic:block/wizard_cauldron_level1" },
    "level=2": { "model": "hogwartsmagic:block/wizard_cauldron_level2" },
    "level=3": { "model": "hogwartsmagic:block/wizard_cauldron" }
  }
}
```

- [ ] **Step 8: Text.** In `en_us.json`, find:

```
  "item.hogwartsmagic.polyjuice_potion.tooltip": "Brew: Awkward + Acromantula Venom -> Mana Regeneration Potion; Mana Regeneration Potion + Mandrake Root -> Stabilized Polyjuice Base; Base + filled Polyjuice Sample -> Polyjuice. Add redstone to extend disguise (3:00 -> 8:00) or gunpowder to make it splash.",
```

Replace with:

```
  "item.hogwartsmagic.polyjuice_potion.tooltip": "Brewed in a Wizard Cauldron (Hogwarts Guide, Potions). Redstone after the sample extends the disguise (3:00 -> 8:00); gunpowder after that makes it splash.",
```

Find:

```
  "item.minecraft.potion.effect.hogwartsmagic.stabilized_polyjuice_base": "Stabilized Polyjuice Base",
```

Replace with:

```
  "item.minecraft.potion.effect.hogwartsmagic.stabilized_polyjuice_base": "Stabilized Polyjuice Base (no longer used)",
```

Find the last entry (added in Task 2):

```
  "item.hogwartsmagic.fine_wood_stick.tooltip": "Stirring rod: right-click a Wizard Cauldron to stir clockwise, sneak and right-click to stir anticlockwise."
}
```

Replace with:

```
  "item.hogwartsmagic.fine_wood_stick.tooltip": "Stirring rod: right-click a Wizard Cauldron to stir clockwise, sneak and right-click to stir anticlockwise.",
  "message.hogwartsmagic.cauldron.empty": "The cauldron is empty. Fill it with a water bucket or three water bottles.",
  "message.hogwartsmagic.cauldron.needs_full": "Fill the cauldron to 3 doses of water before you add ingredients (%s of 3 now).",
  "message.hogwartsmagic.cauldron.full": "The cauldron is already full.",
  "message.hogwartsmagic.cauldron.already_brewing": "A brew is already started. More water would spoil it.",
  "message.hogwartsmagic.cauldron.stir_water": "You stir the water. Add an ingredient first.",
  "message.hogwartsmagic.cauldron.ruined_already": "This brew is ruined. Pour it away with a bucket, or bottle it as Failed Potion.",
  "message.hogwartsmagic.cauldron.ruin": "The brew turns a murky grey-brown. It is ruined.",
  "message.hogwartsmagic.cauldron.not_ready": "The brew is not finished yet.",
  "message.hogwartsmagic.cauldron.bucket_refused": "A bucket only takes plain water from a full cauldron, or pours away a ruined brew.",
  "message.hogwartsmagic.cauldron.poured": "You pour the ruined brew away.",
  "message.hogwartsmagic.cauldron.empty_sample": "This sample is empty.",
  "message.hogwartsmagic.cauldron.finished": "The brew shimmers: %s is ready.",
  "message.hogwartsmagic.cauldron.status.empty": "Empty.",
  "message.hogwartsmagic.cauldron.status.water": "Water: %s of 3 doses.",
  "message.hogwartsmagic.cauldron.status.brewing": "Brewing: on track.",
  "message.hogwartsmagic.cauldron.status.simmering": "Simmering: %s s left.",
  "message.hogwartsmagic.cauldron.status.needs_heat": "Needs heat underneath to simmer: %s s left.",
  "message.hogwartsmagic.cauldron.status.waiting": "Simmered. Add the next ingredient: it overbrews in %s s on the heat.",
  "message.hogwartsmagic.cauldron.status.waiting_cold": "Simmered and off the heat. Add the next ingredient.",
  "message.hogwartsmagic.cauldron.status.ready": "Ready: %s, %s doses left.",
  "message.hogwartsmagic.cauldron.status.ready_heat": "Ready: %s, %s doses left. Overbrews in %s s on the heat.",
  "message.hogwartsmagic.cauldron.status.ruined": "Ruined.",
  "brew.hogwartsmagic.wiggenweld": "Wiggenweld Potion",
  "brew.hogwartsmagic.pepperup": "Pepperup Potion",
  "brew.hogwartsmagic.polyjuice": "Polyjuice Potion"
}
```

- [ ] **Step 9: Validate, build, greps.**
- JSON: run `python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/resources/assets/hogwartsmagic/blockstates/wizard_cauldron.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_empty.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_level1.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_level2.json src/main/resources/data/hogwartsmagic/tags/block/cauldron_heat.json`. Expected: no output.
- Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 359 tests, all green.
- `grep -rn "StabilizedPolyjuiceBaseBrewingRecipe\|PolyjuiceBrewingRecipe\|PolyjuiceRedstoneExtendBrewingRecipe\|PolyjuiceSplashBrewingRecipe" src/`. Expected: nothing.
- `grep -n "WizardPotionBrewingRecipe\|STABILIZED_POLYJUICE_BASE" src/main/java/com/hogwartsmagic/ExampleMod.java`. Expected: the `addRecipe` line and the potion registration (2 lines).
- `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch" src/main/java/com/hogwartsmagic/block/WizardCauldronBlock.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`. Expected: nothing.
- Run the boot block (Global Constraints). Expected: `Done (`, and no `Exception` or `ERROR` naming `wizard_cauldron`, `WizardCauldron`, `cauldron_heat`, `BrewRules`, `apothecary` or a blockstate. Afterwards no server java process is left.

- [ ] **Step 10: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Make the Wizard Cauldron brew: water, heat, steps, overbrew and bottling; Polyjuice leaves the brewing stand

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/block/WizardCauldronBlock.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/data/hogwartsmagic/tags/block/cauldron_heat.json src/main/resources/assets/hogwartsmagic/blockstates/wizard_cauldron.json build_scripts/generate_wizard_cauldron_level_models.py src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_empty.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_level1.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_level2.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -F /tmp/msg.txt
```

(The four deleted classes are already staged by `git rm` in Step 4.)

---

### Task 4: The tinted liquid and the Guide's Potions section

**Files:**
- Create: `src/main/java/com/hogwartsmagic/client/WizardCauldronColors.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Modify: `build_scripts/generate_wizard_cauldron_textures.py`
- Modify: `src/main/resources/assets/hogwartsmagic/textures/block/wizard_cauldron_brew.png` (regenerated)
- Modify: `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `WizardCauldronBlockEntity.getColor()`, `ExampleMod.WIZARD_CAULDRON`, `WIZARD_CAULDRON_ITEM` (Task 3), `BrewRules.CLASSIC_GREEN` (Task 1). Produces: `WizardCauldronColors.block(BlockState, BlockAndTintGetter, BlockPos, int)`, `WizardCauldronColors.item(ItemStack, int)`.

- [ ] **Step 1: The colour handlers.** Create `src/main/java/com/hogwartsmagic/client/WizardCauldronColors.java`:

```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.block.entity.WizardCauldronBlockEntity;
import com.hogwartsmagic.brewing.BrewRules;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Colours the Wizard Cauldron's brew face (tintindex 0). The colour comes from the block entity, which the server
 * keeps in sync; a cauldron with no block entity yet, and the item, show the classic green.
 */
public final class WizardCauldronColors {
    private WizardCauldronColors() {
    }

    public static int block(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        if (level != null && pos != null && level.getBlockEntity(pos) instanceof WizardCauldronBlockEntity cauldron) {
            return FastColor.ARGB32.opaque(cauldron.getColor());
        }
        return FastColor.ARGB32.opaque(BrewRules.CLASSIC_GREEN);
    }

    public static int item(ItemStack stack, int tintIndex) {
        return tintIndex == 0 ? FastColor.ARGB32.opaque(BrewRules.CLASSIC_GREEN) : -1;
    }
}
```

- [ ] **Step 2: Register them.** In `ClientModEvents.java`, find:

```java
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) ->
                        level != null && pos != null
                                ? BiomeColors.getAverageFoliageColor(level, pos)
                                : FoliageColor.getDefaultColor(),
                ExampleMod.FINE_WOOD_LEAVES.get());
    }
```

Replace with:

```java
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) ->
                        level != null && pos != null
                                ? BiomeColors.getAverageFoliageColor(level, pos)
                                : FoliageColor.getDefaultColor(),
                ExampleMod.FINE_WOOD_LEAVES.get());
        event.register(com.hogwartsmagic.client.WizardCauldronColors::block, ExampleMod.WIZARD_CAULDRON.get());
    }
```

Find (the text Task 2 wrote):

```java
                ExampleMod.WIGGENWELD_POTION.get(), ExampleMod.PEPPERUP_POTION.get(), ExampleMod.FAILED_POTION.get());
    }
```

Replace with:

```java
                ExampleMod.WIGGENWELD_POTION.get(), ExampleMod.PEPPERUP_POTION.get(), ExampleMod.FAILED_POTION.get());
        event.register(com.hogwartsmagic.client.WizardCauldronColors::item, ExampleMod.WIZARD_CAULDRON_ITEM.get());
    }
```

- [ ] **Step 3: A greyscale brew.** In `build_scripts/generate_wizard_cauldron_textures.py`, find:

```python
# The brew: a deep green potion, darker at the walls, with lighter swirl and bubbles.
BREW = [
    (14, 52, 30),
    (20, 74, 40),
    (30, 100, 52),
    (46, 132, 66),
    (82, 176, 96),
    (150, 226, 150),
]
```

Replace with:

```python
# The brew: greyscale, darker at the walls, with lighter swirl and bubbles. The block is tinted at runtime
# (tintindex 0, WizardCauldronColors), so the tint gives the colour: the classic green for an unused cauldron,
# water blue, each recipe step's colour, grey-brown when ruined. Light values keep the tint bright.
BREW = [
    (96, 96, 96),
    (128, 128, 128),
    (160, 160, 160),
    (196, 196, 196),
    (228, 228, 228),
    (255, 255, 255),
]
```

Find in the docstring:

```
  wizard_cauldron_brew.png    the brew surface, 4 animated frames (16x64, with .mcmeta)
```

Replace with:

```
  wizard_cauldron_brew.png    the brew surface, 4 animated frames (16x64, with .mcmeta), greyscale and tinted
```

Run `cd /home/user/minecraft_mods && python3 build_scripts/generate_wizard_cauldron_textures.py`. Expected: `wrote 5 textures to ...`. Then `git status --short src/main/resources/assets/hogwartsmagic/textures/block/`. Expected: exactly one line, ` M src/main/resources/assets/hogwartsmagic/textures/block/wizard_cauldron_brew.png` (the other four sheets and the `.mcmeta` are byte-identical because the random calls did not change). Check it is grey: `python3 -c "from PIL import Image;im=Image.open('src/main/resources/assets/hogwartsmagic/textures/block/wizard_cauldron_brew.png').convert('RGB');print(im.size, all(r==g==b for r,g,b in im.getdata()))"`. Expected: `(16, 64) True`.

- [ ] **Step 4: The Guide's Potions section.** In `HogwartsGuideScreen.java`, find:

```java
                page("What to build first", List.of(
                        "A trustworthy wand should come before vanity.",
                        "A broom is more than a luxury; it is often the difference between escape and disaster.",
                        "Treat every new station as a doorway into another branch of magical life."
                ))
        ));
```

Replace with:

```java
                page("What to build first", List.of(
                        "A trustworthy wand should come before vanity.",
                        "A broom is more than a luxury; it is often the difference between escape and disaster.",
                        "Treat every new station as a doorway into another branch of magical life."
                ))
        )),
        POTIONS("gui.hogwartsmagic.hogwarts_guide.section.potions", List.of(
                page("The Wizard Cauldron", List.of(
                        "Set the cauldron over heat: a lit campfire or soul campfire, fire, soul fire, lava or a magma block.",
                        "Fill it with a water bucket, or three water bottles. Ingredients go only into a full cauldron.",
                        "Right-click with each ingredient, one at a time. Stir with a Fine Wood Stick: right-click to stir clockwise, sneak and right-click to stir anticlockwise (keep your other hand empty).",
                        "Simmer steps count down only while there is heat. Right-click with an empty hand to read the brew."
                )),
                page("Signs in the liquid", List.of(
                        "Every correct step changes the colour of the brew. A wrong step turns it grey-brown with a hiss of smoke: the brew is ruined.",
                        "A finished brew shimmers and chimes. Left on the heat more than 120 seconds after its last simmer, it boils into ruin. Take the heat away and it keeps forever.",
                        "A glass bottle takes one dose, and a brew gives three. A ruined brew bottles as Failed Potion, or a bucket pours it away."
                )),
                page("Wiggenweld Potion", List.of(
                        "Heals 8 health over two seconds and stops Bleeding.",
                        "1. Add 1 Honey Bottle.",
                        "2. Add 2 Glow Berries.",
                        "3. Stir clockwise 3 times.",
                        "4. Simmer 20 seconds."
                )),
                page("Pepperup Potion", List.of(
                        "Restores 40 mana and gives 30 seconds of Fire Resistance. Steam pours from your ears.",
                        "1. Add 1 Blaze Powder.",
                        "2. Add 1 Mandrake Root.",
                        "3. Stir anticlockwise 2 times.",
                        "4. Simmer 15 seconds."
                )),
                page("Polyjuice Potion", List.of(
                        "Turns the drinker into whoever the sample came from.",
                        "1. Add 1 Acromantula Venom.",
                        "2. Add 2 Mandrake Roots.",
                        "3. Add 1 Gillyweed.",
                        "4. Stir clockwise 4 times.",
                        "5. Simmer 480 seconds (8 minutes)."
                )),
                page("Polyjuice: the sample", List.of(
                        "6. Add a filled Polyjuice Sample. An empty one is refused. Take the cauldron off the heat while you fetch the sample, or it overbrews.",
                        "Then, if you like, in this order: 1 Redstone for an 8-minute disguise, then 1 Gunpowder to make every dose a splash potion.",
                        "The brew gives 3 doses, each carrying the same sample. The brewing stand no longer makes Polyjuice."
                ))
        ));
```

- [ ] **Step 5: The tab label.** In `en_us.json`, find:

```
  "gui.hogwartsmagic.hogwarts_guide.section.crafting": "Craft & Tools",
```

Replace with:

```
  "gui.hogwartsmagic.hogwarts_guide.section.crafting": "Craft & Tools",
  "gui.hogwartsmagic.hogwarts_guide.section.potions": "Potions",
```

Validate `en_us.json` (command in Global Constraints). Expected: no output.

- [ ] **Step 6: Build and greps.**
- Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 359 tests, all green.
- `grep -n "switch" src/main/java/com/hogwartsmagic/client/WizardCauldronColors.java`. Expected: nothing.
- `grep -rn "WizardCauldronColors" src/main/java | grep -v "client/WizardCauldronColors.java\|event/ClientModEvents.java"`. Expected: nothing (only client code references it).

- [ ] **Step 7: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Tint the cauldron brew from its block entity and add the Potions section to the Hogwarts Guide

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/client/WizardCauldronColors.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java build_scripts/generate_wizard_cauldron_textures.py src/main/resources/assets/hogwartsmagic/textures/block/wizard_cauldron_brew.png src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -F /tmp/msg.txt
```

---

### Task 5: Correct the two wikis

**Files:**
- Modify: `MOD_FEATURES_WIKI.md`
- Modify: `wiki/data.js`

**Interfaces.** None (documentation).

- [ ] **Step 1: `MOD_FEATURES_WIKI.md`.** Make these five replacements.

Find:

```
  Players can collect identity samples, brew Polyjuice, transform after a short delay, and maintain disguises for the potion duration.
```

Replace with:

```
  Players can collect identity samples, brew Polyjuice in the Wizard Cauldron, transform after a short delay, and maintain disguises for the potion duration.
```

Find:

```
### Wizard Cauldron

- Decorative and atmospheric cauldron block.
- Supports magical potion-class ambience.
```

Replace with:

```
### Wizard Cauldron

- Brewing cauldron. Fill it with a water bucket or three water bottles and put heat under it (a lit campfire or soul campfire, fire, soul fire, lava or a magma block).
- Right-click with ingredients in order, stir with a Fine Wood Stick (sneak for anticlockwise), and let simmer steps run on the heat. The liquid changes colour at every correct step; a wrong step ruins the brew.
- A finished brew gives 3 doses (glass bottles). Left on the heat more than 120 seconds after its last simmer, it is ruined. A ruined brew bottles as Failed Potion or is poured away with a bucket.
- Recipes: Wiggenweld Potion, Pepperup Potion and Polyjuice Potion (Hogwarts Guide, Potions).
- Cauldrons placed by structures start full and work like any other.
```

Find:

```
- Applies a Polyjuice effect for the potion duration.
- Can be extended with redstone.
```

Replace with:

```
- Applies a Polyjuice effect for the potion duration.
- Brewed only in the Wizard Cauldron. Redstone right after the sample extends it; gunpowder after that makes it splash.
```

Find:

```
- Brewed from Awkward Potion + Werewolf Fang.
```

Replace with:

```
- Brewed on the brewing stand from Awkward Potion + Acromantula Venom.
```

Find:

```
### Polyjuice Potion

- Brewed from Wizard Potion + filled Polyjuice Sample.
- Applies the Polyjuice effect.
- Base disguise duration is about 3 minutes.

### Extended Polyjuice Potion

- Brewed by adding Redstone to Polyjuice Potion.
- Extended disguise duration is about 8 minutes.
```

Replace with:

```
### Polyjuice Potion

- Brewed in the Wizard Cauldron: add 1 Acromantula Venom, 2 Mandrake Roots and 1 Gillyweed, stir clockwise 4 times, simmer 8 minutes, then add a filled Polyjuice Sample. One brew gives 3 doses.
- The brewing stand no longer makes Polyjuice. Bottles brewed before this change still work; a leftover Stabilized Polyjuice Base has no use.
- Applies the Polyjuice effect.
- Base disguise duration is about 3 minutes.

### Extended and Splash Polyjuice

- Add Redstone in the cauldron right after the sample: the disguise lasts about 8 minutes.
- Add Gunpowder after that (or right after the sample): every dose is a splash potion.
```

- [ ] **Step 2: `wiki/data.js`.** Make these four replacements.

Find:

```
        "Wizard Potion intermediate brew",
```

Replace with:

```
        "Brewed in the Wizard Cauldron (six steps and an 8-minute simmer)",
```

Find:

```
    { name: "Wizard Cauldron", kind: "Decor", summary: "Atmospheric potion-class cauldron.", features: ["Magical bubbling ambience"] },
```

Replace with:

```
    { name: "Wizard Cauldron", kind: "Brewing", summary: "Brewing cauldron: fill it with water, set it over heat and add ingredients in order.", features: ["Wiggenweld, Pepperup and Polyjuice recipes", "The liquid colour shows every step", "A finished brew gives 3 doses"] },
```

Find:

```
    { name: "Polyjuice Potion", kind: "Potion", summary: "Drinkable disguise potion with delayed transformation.", features: ["Requires a valid sample", "Supports base and extended durations"] },
```

Replace with:

```
    { name: "Polyjuice Potion", kind: "Potion", summary: "Drinkable disguise potion with delayed transformation.", features: ["Brewed in the Wizard Cauldron with a filled sample", "Redstone extends it, gunpowder makes it splash"] },
```

Find:

```
    { name: "Wizard Potion", kind: "Potion", summary: "Intermediate brew used before Polyjuice.", features: ["Made from Awkward Potion plus Werewolf Fang", "Improves mana regeneration"] },
```

Replace with:

```
    { name: "Wizard Potion", kind: "Potion", summary: "Mana regeneration potion from the brewing stand.", features: ["Made from Awkward Potion plus Acromantula Venom", "Improves mana regeneration"] },
```

- [ ] **Step 3: Check.**
- `grep -n "Werewolf Fang\|Wizard Potion + filled\|intermediate brew\|Decorative and atmospheric cauldron" MOD_FEATURES_WIKI.md wiki/data.js`. Expected: nothing.
- `node -e "require('fs').readFileSync('wiki/data.js','utf8'); new Function(require('fs').readFileSync('wiki/data.js','utf8'))"` if `node` exists (`command -v node`). Expected: no output. Without node, `python3 -c "s=open('wiki/data.js',encoding='utf-8').read();print(s.count('{')-s.count('}'), s.count('[')-s.count(']'))"`. Expected: `0 0`.

- [ ] **Step 4: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Wikis: Polyjuice and the Wizard Cauldron brew in the cauldron now, Wizard Potion uses Acromantula Venom

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add MOD_FEATURES_WIKI.md wiki/data.js
git commit -F /tmp/msg.txt
```

---

### Task 6: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 359 tests (339 + 20), all green.
- [ ] **Step 2:** Validate every JSON file the phase touched: `python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/resources/assets/hogwartsmagic/blockstates/wizard_cauldron.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_empty.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_level1.json src/main/resources/assets/hogwartsmagic/models/block/wizard_cauldron_level2.json src/main/resources/assets/hogwartsmagic/models/item/wiggenweld_potion.json src/main/resources/assets/hogwartsmagic/models/item/pepperup_potion.json src/main/resources/assets/hogwartsmagic/models/item/failed_potion.json src/main/resources/data/hogwartsmagic/tags/block/cauldron_heat.json`. Expected: no output.
- [ ] **Step 3:** Safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/brewing src/main/java/com/hogwartsmagic/block src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/item/StirringRodItem.java src/main/java/com/hogwartsmagic/polyjuice`. Expected: nothing new compared with `git grep -n "net.minecraft.client\|com.hogwartsmagic.client" ea0bbf6 -- src/main/java/com/hogwartsmagic/block src/main/java/com/hogwartsmagic/polyjuice`.
  - `grep -n "switch" src/main/java/com/hogwartsmagic/brewing/BrewRules.java src/main/java/com/hogwartsmagic/block/WizardCauldronBlock.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/client/WizardCauldronColors.java`. Expected: nothing.
  - `grep -rn "removeEffect" src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`. Expected: nothing.
  - `grep -rn "SubscribeEvent" src/main/java/com/hogwartsmagic/brewing src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`. Expected: nothing.
  - `git status --short`. Expected: clean except untracked run/ output (no unstaged source changes).
- [ ] **Step 4:** Run the boot block (Global Constraints). Expected: `Done (`; no mixin, registry, blockstate, model-loading or class-loading errors; no server java process left afterwards.
- [ ] **Step 5:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 7: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 6. For rows 22-24, first save a world with the jar built at ea0bbf6 (before this phase): place a Wizard Cauldron, brew a Polyjuice bottle on the brewing stand, and find a Diagon Alley apothecary.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | Creative | Place a Wizard Cauldron | Empty pot, no liquid surface, no witch particles |
| 2 | Row 1 | Right-click with an empty hand | Action bar "Empty." |
| 3 | Row 1 | Use a water bucket | Liquid fills to the top, water blue; you hold an empty bucket |
| 4 | New cauldron | Use three water bottles one by one | The surface rises at 1, 2, 3; bottles come back empty |
| 5 | Row 3, no heat | Add 1 Honey Bottle | Liquid turns amber, bubble pop; you get a glass bottle back |
| 6 | Row 5 | Add 2 Glow Berries, stir clockwise 3 times with a Fine Wood Stick | Colour shifts with every click; empty-hand status "Brewing: on track." then "Needs heat underneath to simmer: 20 s left." |
| 7 | Row 6 | Light a campfire under the cauldron, wait | Status counts down "Simmering: N s left."; after 20 s a chime, sparkles, liquid teal-green, status "Ready: Wiggenweld Potion, 3 doses left. Overbrews in N s on the heat." |
| 8 | Row 7 | Use three glass bottles | Three Wiggenweld Potions (teal liquid icon); the surface drops each time; after the third the pot is empty |
| 9 | Hit yourself with Sectumsempra from a second player (or `/effect give @s hogwartsmagic:bleeding 30`) | Drink Wiggenweld | Bleeding is gone at once; about 4 hearts heal over 2 s |
| 10 | Full water over a lit campfire | Brew Pepperup: Blaze Powder, Mandrake Root, sneak-stir 2 times (other hand empty), wait 15 s | Red liquid, chime; bottles are Pepperup Potions |
| 11 | Spend mana, then drink Pepperup | | About 40 mana back, Fire Resistance 0:30, steam clouds from your head |
| 12 | Full water | Add Glow Berries first | Grey-brown liquid, hiss, low note, smoke; "The brew turns a murky grey-brown. It is ruined." |
| 13 | Row 12 | Bottle one dose, drink it | "Failed Potion"; Nausea 10 s and Poison 3 s |
| 14 | Row 12 | Use an empty bucket | "You pour the ruined brew away."; the pot is empty; the bucket stays empty |
| 15 | A finished Wiggenweld on a lit campfire | Wait 2 minutes | In the last 20 s lava pops; then the brew turns grey-brown (overbrewed) |
| 16 | A finished brew | Break the campfire, wait 3 minutes | Still "Ready: ..." (it holds off the heat) |
| 17 | Full water over heat | Polyjuice: Venom, 2 Mandrake Roots, Gillyweed, stir clockwise 4, simmer 480 s | After 8 minutes "Simmered. Add the next ingredient: it overbrews in N s on the heat." |
| 18 | Row 17 | Right-click with an empty Polyjuice Sample | "This sample is empty."; nothing else changes |
| 19 | Row 17 | Add a filled sample of a villager | Chime, sparkles; bottle 3 Polyjuice Potions; drinking one turns you into a villager after 3 s for 3:00 |
| 20 | Row 17 again, sample then Redstone then Gunpowder | Bottle | Three "Splash Polyjuice Potion"; one thrown at a friend disguises them for 8:00 |
| 21 | Row 17 again, sample then Redstone Dust only | Bottle it | The tooltip shows Polyjuice 8:00; drinking it disguises you for 8:00 |
| 22 | Old world (see above) | Right-click the old cauldron with an empty hand | "The cauldron is full."; the liquid stays green; the first bottle, bucket, rod or ingredient turns it into 3 doses of water (blue), and it brews like a new one |
| 23 | Old world | Drink the Polyjuice brewed on the stand before the change | It still works |
| 24 | Old world, Diagon Alley apothecary | Look at and use its cauldron; watch the apothecary villager | Full green liquid until used; it brews; the apothecary keeps its job |
| 25 | Brewing stand | Awkward Potion + Acromantula Venom | Mana Regeneration Potion, as before |
| 26 | Brewing stand | Try Polyjuice in a bottle slot and Mandrake Root in the ingredient slot; a Splash Polyjuice with Redstone or Gunpowder; a Wizard Potion brew | Polyjuice will not go into a bottle slot and Mandrake Root will not go into the ingredient slot; a Splash Polyjuice with Redstone or Gunpowder brews nothing; the Wizard Potion still brews |
| 27 | An old Stabilized Polyjuice Base bottle | Hover it | Its name ends in "(no longer used)"; nothing crashes |
| 28 | Hogwarts Guide | Open the "Potions" tab | Six pages: the cauldron, signs in the liquid, Wiggenweld, Pepperup, Polyjuice, the sample |
| 29 | Creative tab | Look at the cauldron item and the three new potions | Cauldron shows green liquid; potions show teal, red and grey-brown liquid |
| 30 | Full water | Hold dirt and right-click the cauldron | The dirt is placed (non-ingredients pass through) |
| 31 | Dedicated server and a client | Join, then rows 3, 7 and 12 | The colour changes show for both players; no registry mismatch |
| 32 | Full water cauldron | Right-click with an empty main hand and a glass bottle in the off hand | The off-hand bottle is used |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Phase 1 bullets.**
  - BaseEntityBlock with a block entity holding doses, history, simmer counter (the last run, ruling 4), ruined flag, finished recipe (derived by `BrewRules.match`, ruling 4), overbrew timer: Task 3 steps 1-2. Ticker only with liquid and heat: `getTicker` (level > 0) and `secondTick` (heat), ruling 6.
  - Pure `BrewRules`, test-first, recipe table, step model, `match`, simmer and overbrew math, colour per step, registry-name strings: Task 1.
  - Interactions (water bucket, water bottle, ingredient, rod with sneak, glass bottle, empty bucket, empty-hand status): Task 3 `useItem`, `showStatus`; sneak via `StirringRodItem` (Task 2, ruling 9); rows 2-14.
  - Heat tag with lit campfires: Task 3 step 5, `isHeated`; rows 7, 16.
  - `level` property, per-level models, BlockColor from the block entity, tintindex, greyscale texture, default "full, green": Task 3 steps 1, 6, 7; Task 4 steps 1-3; rulings 2, 19, 22-24; rows 1, 4, 21, 23, 28.
  - Wiggenweld, Pepperup, Failed Potion, `BrewedPotionItem`: Task 2; rows 8-13.
  - Polyjuice leaves the stand, `PolyjuiceStacks`, removal in the task that makes the cauldron recipe usable (Task 3, after `PolyjuiceStacks` in Task 2), base renamed, wikis: Tasks 2, 3, 5; rows 19-20, 22, 24-26.
  - Lang lines and the Guide "Potions" section: Tasks 2, 3, 4; row 27.
  - Migration (old worlds, `diagon_alley.nbt`): ruling 1, `cauldronAt` fallback; rows 21, 23.
  - `runServer` boots clean: Task 3 step 9 and Task 6 step 4.
- **Polyjuice in the cauldron, items 1-8.** 1 recipe: `BrewRules.POLYJUICE`. 2 sample last, overbrew while waiting, empty sample refused without ruin: `overbrewClock`, `refusesEmptySample`, `addIngredient`; tests `overbrewClockStartsAfterTheLastSimmer`, `anEmptySampleIsRefused`. 3 target stored on the cauldron: `polyjuiceTarget`, saved as `PolyjuiceTarget`. 4 redstone then gunpowder, each once, anything else ruins: `tail` and `fit`; test `polyjuiceTailIsInOrderAndOnce`. 5 three doses with the same target, drinking unchanged: `bottledPotion` and `PolyjuiceStacks` (splash as `SPLASH_POTION`, ruling 14). 6 stand recipes go, `WizardPotionBrewingRecipe` stays, base stays registered and renamed: Task 3 steps 3, 4, 8. 7 advancement unchanged: listed under untouched. 8 docs: Task 5.
- **Decisions 1-15.** In-world (1), step kinds (2), colour, ruin and finish feedback (3), heat list (4), water first (5, ruling 7), overbrew 120 s (6), 3 doses and Failed Potion (7), hardcoded Java table (8), Guide (9), Polyjuice moved (10), custom items, no vanilla Potion (11), petrification untouched (12, Phase 2), no quality (13), no dimension rules (14), server authoritative with no new packet (15, ruling 29).
- **Type consistency.** `BrewRules.Step(Kind, String, int)` everywhere; `Match(Status, Recipe)`; `PolyjuiceStacks.make(CompoundTag, boolean, boolean)` called with `(polyjuiceTarget, usedTail(REDSTONE), usedTail(GUNPOWDER))`; `WizardCauldronBlockEntity.serverTick(Level, BlockPos, BlockState, WizardCauldronBlockEntity)` matches `BlockEntityTicker`; `WizardCauldronColors.block` matches `BlockColor.getColor(BlockState, BlockAndTintGetter, BlockPos, int)`; `item` matches `ItemColor.getColor(ItemStack, int)`. `useItemOn`, `useWithoutItem`, `doesSneakBypassUse` and `onDataPacket` signatures were checked against the 1.21 mapped jar and the Forge 51.0.33 sources.
- **Placeholder scan:** none. Every number, colour, sound, particle and message is given.
- **Counts:** Task 1 adds 20 tests. 339 + 20 = 359.
