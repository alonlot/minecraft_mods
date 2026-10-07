# Cauldron Brewing Phase 3 (Polish and World) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** Three brewing advancements ("First Brew" when you bottle your first finished brew, "Toil and Trouble" when your own step ruins a brew, "Liquid Luck" when you first hold Felix Felicis); a working Potions classroom in Mini Hogwarts (seven heated Wizard Cauldrons, shelves, a lectern and two stocked ingredient chests) in every castle built from now on; and both wikis (`MOD_FEATURES_WIKI.md` and the web wiki in `wiki/`) brought up to date with everything Brewing Phases 1 and 2 built.

**Architecture:**
- **Pure rules (test-first):** new `brewing/ClassroomSupplies` holds what a classroom chest contains (registry names, count ranges, the rare Ashwinder egg chance) and rolls one chest from injected random functions.
- **Advancements:** three JSON files under `data/hogwartsmagic/advancement/hp/`. "First Brew" and "Toil and Trouble" use the `minecraft:impossible` trigger and are granted from code by a new `brewing/BrewAdvancements.grant(Player, String)` (the `VoldemortEntity.grantAllCriteria` pattern) at two spots in `WizardCauldronBlockEntity`. "Liquid Luck" is a vanilla `minecraft:inventory_changed` on `hogwartsmagic:felix_felicis`.
- **The classroom is part of the schematic.** A new script, `build_scripts/add_potions_classroom.py`, edits `mini_hogwarts.schem` once (type-preserving NBT, byte-identical round trip checked first): it floors the floorless apse tower behind the gatehouse's upstairs room and furnishes it. `MiniHogwartsManager` gains one thing: every chest that comes from the schematic (the castle had none) is filled with `ClassroomSupplies` when the build finishes.
- **Docs:** text edits to `MOD_FEATURES_WIKI.md` and `wiki/data.js`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5, Python 3 (stdlib only), Node 22 for the wiki syntax check.

**Spec:** `docs/superpowers/specs/2026-10-07-cauldron-brewing-roadmap.md`: "Phase 3: Polish and world", "Global constraints". Facts for the docs come from the Phase 1 and Phase 2 plans (`docs/superpowers/plans/2026-10-07-cauldron-brewing-phase1.md`, `2026-10-08-cauldron-brewing-phase2.md`), their ledgers and fix-wave rulings, and the code and `en_us.json` as they are on the branch.

## Requirements (Phase 3 of the spec)

1. Advancement "Brew your first potion", a child of `cauldron_class`.
2. Advancement "Ruin a brew".
3. Advancement "Liquid Luck" (Felix Felicis).
4. A Potions classroom in Mini Hogwarts with working cauldrons (the castle has none).
5. `MOD_FEATURES_WIKI.md` and the web wiki (`wiki/`) describe every brewing feature of Phases 1 and 2: the seven potions, the ingredients, dittany, petrification and statues, the give-in choice.

## Rulings made while planning

1. **The classroom goes into the schematic (option a), not a separate template.** Reading `mini_hogwarts.schem` (96 x 109 x 145, about 36,000 solid blocks) shows the castle has almost no rooms: the gatehouse (x 27..49, z 100..106) holds Defense Against the Dark Arts (ground floor, east), a stair tower (west), and upstairs a bookshelf room (x 35..40) and the Mirror of Erised room (x 42..47); its sign texts are block-entity data that `MiniHogwartsSchematic` never loads. None is free. Behind the upstairs bookshelf room, through the spruce door at (38, 33..34, 106), stands the tall apse tower (x 32..45, z 106..120, walls from y 31 to its roof near y 63) with **no floor**: the door opens onto a five-block cobblestone ledge and a drop of about thirty blocks. Flooring that tower at y 32 gives a 9 x 13 room with a 30-block ceiling, reached by an existing door, and removes a fall trap. A separate template placed after the build would need its own site, terrain clearing and foundations; the schematic edit needs none, and the schematic stays the single source the renderer can show.
2. **Exact layout (schematic coordinates).** Floor: every air cell at y 32 enclosed by the tower walls (96 cells, x 34..42, z 107..119; the script checks the count and the box) becomes `minecraft:stone_bricks`, except under the seven cauldrons, which get `minecraft:magma_block` (in `#hogwartsmagic:cauldron_heat`, no flame, no smoke, nobody stands on it). At y 33: student cauldrons at x 36, 38, 40 by z 111, 114 and the teacher's at (38, 116), each `hogwartsmagic:wizard_cauldron[level=3]`; a lectern at (38, 118) facing north; twelve empty chiseled bookshelves (the shelves) facing east at x 34, z 110..115, y 33 and 34; on the east wall x 42, barrels (facing up) at z 110, 112, 113, 115, chests (facing west, single) at z 111 and 114, lanterns standing on the barrels at (42, 34, 110) and (42, 34, 115). No bookshelf, iron bars or enchanting table is added, so the existing themed-chest landmarks do not move (checked: all five themed spots are the same before and after).
3. **Cauldrons work at once.** A `level=3` cauldron with no saved data is "not initialized": it shows classic green and, on first use, becomes three doses of plain water (Phase 1 ruling 2). Magma below is heat. So a player can add the first ingredient straight away.
4. **The chests are filled in Java.** `MiniHogwartsSchematic` ignores the schematic's block entities, so chest contents cannot live in the `.schem`. `PlacementJob.analyze` records every `minecraft:chest` cell of the schematic (the castle had none, so these are the classroom's), and `furnish` fills each with one `ClassroomSupplies.roll` in shuffled slots. Items are resolved by registry name; an unknown name is skipped.
5. **Chest contents (`ClassroomSupplies.CHEST`, per chest):** glass bottle 6-9, water bucket 1, Fine Wood Stick 1, honey bottle 1-3, glow berries 2-6, blaze powder 1-3, mandrake root 3-5, dittany 3-6, asphodel 2-3, wormwood 1-2, valerian sprigs 2-4, arcane dust 1-2, gunpowder 2-3; plus one Ashwinder egg at 10%. The minimums brew each of Wiggenweld, Pepperup, Essence of Dittany, the Mandrake Draught (with its splash tail) and Living Death once (a test checks it). No Acromantula venom, gold nugget or sample: Polyjuice and Felix Felicis stay earned.
6. **Old worlds:** nothing happens. A castle is placed once by an in-memory job; built castles are only a tracked centre in `MiniHogwartsTracker`, and nothing re-reads the schematic for them. They are never rebuilt or touched, and they get no classroom. New castles (auto-spawned or `/spawnminihogwarts`) get it. A server stopped mid-build loses that job as it always did.
7. **The skirt:** the build's second phase fills stone under each column's lowest solid block down to the ground (at most 64). The new floor is now the lowest solid of the tower's 96 inner columns, so a stone plinth is filled under it. That is wanted (no void under the floor) and costs a few thousand blocks in a 1.5M-cell job.
8. **"First Brew" (`hp/first_brew`)** is granted from code when a player bottles a dose of a finished brew whose result is not a Failed Potion (a ruined brew, or a Polyjuice brew whose stored target is empty, does not count). A vanilla `inventory_changed` on the potion items was rejected: apothecaries sell Polyjuice and creative players hold everything, so it would fire without brewing. Parent `cauldron_class`, icon Wiggenweld Potion, frame task. Title "First Brew", description "Brew your first potion in a Wizard Cauldron and bottle it."
9. **"Toil and Trouble" (`hp/ruined_brew`)** is granted from code inside `WizardCauldronBlockEntity.ruin` when a player caused the ruin (a wrong ingredient or stir). An overbrew ruin has no player (Phase 1 ruling 20) and grants nothing. Parent `cauldron_class`, icon Failed Potion, frame task, description "Ruin a brew with a wrong step."
10. **"Liquid Luck" (`hp/liquid_luck`)** is `minecraft:inventory_changed` with `hogwartsmagic:felix_felicis`: holding one, however it came. Parent `first_brew`, icon Felix Felicis, frame challenge (a ten-minute brew from rare eggs), description "Hold a bottle of Felix Felicis."
11. **`BrewAdvancements.grant`** ignores anything that is not a `ServerPlayer`, Forge `FakePlayer`s (machines bottling or stirring), a missing advancement (a datapack removed it) and an advancement already done. It awards every remaining criterion, like `VoldemortEntity.grantAllCriteria`, which is left as it is.
12. **No custom trigger.** A `CriterionTrigger` would need a registry and a codec for two one-shot grants; `minecraft:impossible` plus a code grant is the pattern the mod already uses.
13. **Lang:** six lines after `"advancement.hogwartsmagic.polyjuice_plot.description": ...`. No guide change (the classroom is something to find; the wikis tell it).
14. **Docs follow the code and the fix-wave rulings:** the Fine Wood Stick is the stirring rod (its display name is "Fine Wood Stick"); a wild Dittany gives two, a planted one gives one; the give-in key is J by default; Polyjuice is brewed only in the cauldron; the Wizard Potion stays on the brewing stand (Awkward Potion + Acromantula Venom). The stale "Werewolf Fang: used in Wizard Potion brewing" line (missed by Phase 1 ruling 26) is corrected. The web wiki's recipe pages (`recipes-data.js`, `recipes-manifest.json`) hold crafting-grid recipes only and stay untouched; brewing recipes go into `data.js` text. `stats` in `data.js` are set to the real array lengths.
15. **Shared files are anchored on old, stable text:** the lang line of `polyjuice_plot`, the `tell(player, "ruin");` and `result = bottledPotion(match.recipe());` lines of the cauldron, the `else if (state.is(Blocks.IRON_BARS))` and `LOGGER.info("Mini Hogwarts furnished with {} themed chests", placed);` lines of `MiniHogwartsManager`, and headings and lines of the wikis quoted below.

## Global Constraints

- Server authoritative. `brewing/`, `worldgen/`, `block/entity/` never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. This plan adds no client code.
- No `switch` on an enum anywhere in the new code. Use if-chains and `==`.
- Never load a chunk. The chest fill reads block entities of a castle the job has just placed (its chunks are loaded).
- No new hurt, death or effect handler is added.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- `en_us.json` and every JSON file touched stay valid JSON. Validate with `python3 -c "import json;json.load(open('<file>',encoding='utf-8'))"`.
- Build and test (cloud):
  - First `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If a build is running, wait (poll every 30 s, at most 20 min).
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*ClassroomSuppliesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- Dedicated-server boot (Tasks 2, 4 and 7), "the boot block":
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
  Write the message with a heredoc to `/tmp/msg.txt`, `git add` the new files, then commit by path: `git commit -F /tmp/msg.txt -- <paths>`.
- **Tests: the count at the start of Task 1; all green.** Call it BASE. Before Task 1, run the test count command once and write BASE into the ledger. Task 1 adds 4 tests; no other task adds or changes tests. Every later task ends at BASE + 4, all green.
- Line numbers drift, and other phases (the Brewing Phase 2 fix wave, Dark Magic Phases 2-4) land on this branch while this plan runs. Every edit names the exact text to find. If an anchor is not found exactly once, stop and report it; do not guess.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java` | Classroom chest contents, `roll` | 1 |
| Create `src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java` | 4 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java` | Code grants | 2 |
| Create `src/main/resources/data/hogwartsmagic/advancement/hp/first_brew.json`, `ruined_brew.json`, `liquid_luck.json` | Advancements | 2 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Six advancement lines | 2 |
| Modify `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java` | Two grant calls, one import | 2 |
| Create `build_scripts/add_potions_classroom.py` | The schematic edit | 3 |
| Modify `src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem` | Floor and furniture | 3 |
| Modify `src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsManager.java` | Record schematic chests, stock them | 4 |
| Modify `MOD_FEATURES_WIKI.md` | Brewing, ingredients, potions, petrification, Mini Hogwarts, advancements | 5 |
| Modify `wiki/data.js` | Systems, mob, blocks, items, stats | 6 |

Untouched on purpose: `MiniHogwartsSchematic.java` (it already parses mod blocks and partial states), `HogwartsGuideScreen.java`, `VoldemortEntity.java`, `wiki/recipes-data.js`, `wiki/recipes-manifest.json`, every other schematic and structure.

## Pre-flight table (shared names between tasks)

| Name | Defined in | Used in |
|---|---|---|
| `ClassroomSupplies.CHEST`, `.roll(IntBinaryOperator, double)`, `.Stack(item, count)`, `.ASHWINDER_CHANCE`, `.GLASS_BOTTLE`, `.WATER_BUCKET`, `.STIRRING_ROD` | Task 1 | Task 4 |
| `BrewAdvancements.grant(Player, String)`, `.FIRST_BREW = "first_brew"`, `.RUINED_BREW = "ruined_brew"` | Task 2 | Task 2 |
| Advancement ids `hogwartsmagic:hp/first_brew`, `hp/ruined_brew`, `hp/liquid_luck` | Task 2 | Tasks 5, 6, 7 |
| Schematic chests at (42, 33, 111) and (42, 33, 114) | Task 3 | Task 4 |
| Existing: `BrewRules` recipe constants and item names, `WizardCauldronBlockEntity.bottle` and `.ruin`, `ExampleMod.FAILED_POTION`, `MiniHogwartsManager.PlacementJob` | Phases 1-2 | Tasks 1, 2, 4 |

---

## Task 1: `ClassroomSupplies` (pure, test-first)

**Files:**
- Create: `src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java`
- Create: `src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java`

**Interfaces:**
- Consumes: `BrewRules.HONEY_BOTTLE`, `GLOW_BERRIES`, `BLAZE_POWDER`, `MANDRAKE_ROOT`, `DITTANY`, `ASPHODEL`, `WORMWOOD`, `VALERIAN_SPRIGS`, `ARCANE_DUST`, `GUNPOWDER`, `ASHWINDER_EGG`, `ACROMANTULA_VENOM`, `POLYJUICE_SAMPLE`, `MAX_DOSES`; recipes `WIGGENWELD`, `PEPPERUP`, `DITTANY_ESSENCE`, `MANDRAKE_DRAUGHT`, `LIVING_DEATH` (`steps()`, `tail()`), `BrewRules.Kind.ADD`, `Step.item()`, `Step.amount()`.
- Produces: `public final class ClassroomSupplies` with `record Supply(String item, int min, int max)`, `record Stack(String item, int count)`, `static final List<Supply> CHEST`, `static final double ASHWINDER_CHANCE = 0.10`, `static final String GLASS_BOTTLE, WATER_BUCKET, STIRRING_ROD`, `static List<Stack> roll(IntBinaryOperator between, double eggRoll)`.

- [ ] **Step 1: Record BASE.** Run the build-and-test command, then the test count command. Write "BASE = <n>" into the ledger.

- [ ] **Step 2: Write the failing test.** Create `src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java`:

```java
package com.hogwartsmagic.brewing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassroomSuppliesTest {

    private static ClassroomSupplies.Supply supply(String item) {
        for (ClassroomSupplies.Supply supply : ClassroomSupplies.CHEST) {
            if (supply.item().equals(item)) {
                return supply;
            }
        }
        return null;
    }

    private static boolean hasEgg(List<ClassroomSupplies.Stack> stacks) {
        for (ClassroomSupplies.Stack stack : stacks) {
            if (stack.item().equals(BrewRules.ASHWINDER_EGG)) {
                return true;
            }
        }
        return false;
    }

    private static void addNeeds(Map<String, Integer> needed, List<BrewRules.Step> steps) {
        for (BrewRules.Step step : steps) {
            if (step.kind() == BrewRules.Kind.ADD) {
                needed.merge(step.item(), step.amount(), Integer::sum);
            }
        }
    }

    @Test
    void oneChestBrewsEveryRecipeWithoutARareIngredientOnce() {
        Map<String, Integer> needed = new HashMap<>();
        for (BrewRules.Recipe recipe : List.of(BrewRules.WIGGENWELD, BrewRules.PEPPERUP, BrewRules.DITTANY_ESSENCE,
                BrewRules.MANDRAKE_DRAUGHT, BrewRules.LIVING_DEATH)) {
            addNeeds(needed, recipe.steps());
            addNeeds(needed, recipe.tail());
        }
        for (Map.Entry<String, Integer> need : needed.entrySet()) {
            ClassroomSupplies.Supply supply = supply(need.getKey());
            assertNotNull(supply, need.getKey() + " is not supplied");
            assertTrue(supply.min() >= need.getValue(),
                    need.getKey() + ": at least " + supply.min() + " but a brew of each needs " + need.getValue());
        }
        assertTrue(supply(ClassroomSupplies.GLASS_BOTTLE).min() >= BrewRules.MAX_DOSES);
        assertNotNull(supply(ClassroomSupplies.WATER_BUCKET));
        assertNotNull(supply(ClassroomSupplies.STIRRING_ROD));
    }

    @Test
    void countsStayInsideTheirRange() {
        List<ClassroomSupplies.Stack> low = ClassroomSupplies.roll((min, max) -> min, 0.99);
        List<ClassroomSupplies.Stack> high = ClassroomSupplies.roll((min, max) -> max, 0.99);
        List<ClassroomSupplies.Stack> tooHigh = ClassroomSupplies.roll((min, max) -> 1000, 0.99);
        List<ClassroomSupplies.Stack> tooLow = ClassroomSupplies.roll((min, max) -> -5, 0.99);
        assertEquals(ClassroomSupplies.CHEST.size(), low.size());
        for (int i = 0; i < ClassroomSupplies.CHEST.size(); i++) {
            ClassroomSupplies.Supply supply = ClassroomSupplies.CHEST.get(i);
            assertEquals(supply.item(), low.get(i).item());
            assertEquals(supply.min(), low.get(i).count());
            assertEquals(supply.max(), high.get(i).count());
            assertEquals(supply.max(), tooHigh.get(i).count());
            assertEquals(supply.min(), tooLow.get(i).count());
        }
    }

    @Test
    void anAshwinderEggOnlyBelowTheChance() {
        List<ClassroomSupplies.Stack> lucky = ClassroomSupplies.roll((min, max) -> min, 0.05);
        assertTrue(lucky.contains(new ClassroomSupplies.Stack(BrewRules.ASHWINDER_EGG, 1)));
        assertEquals(ClassroomSupplies.CHEST.size() + 1, lucky.size());
        assertFalse(hasEgg(ClassroomSupplies.roll((min, max) -> min, ClassroomSupplies.ASHWINDER_CHANCE)));
        assertFalse(hasEgg(ClassroomSupplies.roll((min, max) -> min, 0.5)));
    }

    @Test
    void suppliesAreSaneAndRareThingsStayEarned() {
        Set<String> items = new HashSet<>();
        for (ClassroomSupplies.Supply supply : ClassroomSupplies.CHEST) {
            assertTrue(items.add(supply.item()), "listed twice: " + supply.item());
            assertTrue(supply.min() >= 1 && supply.min() <= supply.max() && supply.max() <= 16, supply.item());
        }
        assertFalse(items.contains(BrewRules.ASHWINDER_EGG));
        assertFalse(items.contains(BrewRules.ACROMANTULA_VENOM));
        assertFalse(items.contains(BrewRules.POLYJUICE_SAMPLE));
        assertTrue(ClassroomSupplies.CHEST.size() + 1 <= 27, "everything fits one chest");
    }
}
```

- [ ] **Step 3: Run it and see it fail.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*ClassroomSuppliesTest" --console=plain --max-workers=2`. Expected: compilation fails (`cannot find symbol ... ClassroomSupplies`). That is RED.

- [ ] **Step 4: Write the class.** Create `src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java`:

```java
package com.hogwartsmagic.brewing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * What each ingredient chest of the Mini Hogwarts Potions classroom holds. Pure: items are registry names.
 * The minimums brew each recipe that needs no rare ingredient once (Wiggenweld, Pepperup, Essence of Dittany, the
 * Mandrake Restorative Draught with its splash tail, the Draught of Living Death), with bottles, water and a
 * stirring rod. Polyjuice (a sample, Acromantula venom) and Felix Felicis (an Ashwinder egg, venom, gold) stay
 * earned; now and then a chest holds one Ashwinder egg.
 */
public final class ClassroomSupplies {
    public static final String GLASS_BOTTLE = "minecraft:glass_bottle";
    public static final String WATER_BUCKET = "minecraft:water_bucket";
    /** The Fine Wood Stick is the stirring rod. */
    public static final String STIRRING_ROD = "hogwartsmagic:fine_wood_stick";
    /** Each chest holds one Ashwinder egg with this chance. */
    public static final double ASHWINDER_CHANCE = 0.10;

    /** One kind of supply: between min and max of item, both inclusive. */
    public record Supply(String item, int min, int max) {
    }

    /** One stack for a chest. */
    public record Stack(String item, int count) {
    }

    public static final List<Supply> CHEST = List.of(
            new Supply(GLASS_BOTTLE, 6, 9),
            new Supply(WATER_BUCKET, 1, 1),
            new Supply(STIRRING_ROD, 1, 1),
            new Supply(BrewRules.HONEY_BOTTLE, 1, 3),
            new Supply(BrewRules.GLOW_BERRIES, 2, 6),
            new Supply(BrewRules.BLAZE_POWDER, 1, 3),
            new Supply(BrewRules.MANDRAKE_ROOT, 3, 5),
            new Supply(BrewRules.DITTANY, 3, 6),
            new Supply(BrewRules.ASPHODEL, 2, 3),
            new Supply(BrewRules.WORMWOOD, 1, 2),
            new Supply(BrewRules.VALERIAN_SPRIGS, 2, 4),
            new Supply(BrewRules.ARCANE_DUST, 1, 2),
            new Supply(BrewRules.GUNPOWDER, 2, 3));

    private ClassroomSupplies() {
    }

    /**
     * One chest, in CHEST order. between(min, max) picks a count (clamped to the range); eggRoll is a roll in
     * [0, 1): below ASHWINDER_CHANCE adds one Ashwinder egg at the end.
     */
    public static List<Stack> roll(IntBinaryOperator between, double eggRoll) {
        List<Stack> out = new ArrayList<>();
        for (Supply supply : CHEST) {
            int count = between.applyAsInt(supply.min(), supply.max());
            out.add(new Stack(supply.item(), Math.max(supply.min(), Math.min(supply.max(), count))));
        }
        if (eggRoll < ASHWINDER_CHANCE) {
            out.add(new Stack(BrewRules.ASHWINDER_EGG, 1));
        }
        return out;
    }
}
```

- [ ] **Step 5: Run it and see it pass.** The one-class command: expected `BUILD SUCCESSFUL`, 4 tests in `ClassroomSuppliesTest`, all passing.

- [ ] **Step 6: Full build and count.** The build-and-test command, then the test count: expected BASE + 4, all green.

- [ ] **Step 7: Commit.**

```bash
cd /home/user/minecraft_mods && cat > /tmp/msg.txt <<'EOF'
Brewing Phase 3: ClassroomSupplies, the Potions classroom chest contents

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java
```

---

## Task 2: The three advancements

**Files:**
- Create: `src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java`
- Create: `src/main/resources/data/hogwartsmagic/advancement/hp/first_brew.json`, `ruined_brew.json`, `liquid_luck.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`
- Modify: `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`

**Interfaces:**
- Consumes: `WizardCauldronBlockEntity.bottle(ServerLevel, Player, InteractionHand, ItemStack)` and `ruin(ServerLevel, @Nullable Player)` (private, Phase 1), `ExampleMod.FAILED_POTION`, `ServerPlayer.serverLevel().getServer().getAdvancements().get(ResourceLocation)`, `PlayerAdvancements.getOrStartProgress`/`award`, `AdvancementProgress.isDone`/`getRemainingCriteria`, `net.minecraftforge.common.util.FakePlayer`.
- Produces: `public final class BrewAdvancements` with `static final String FIRST_BREW = "first_brew"`, `RUINED_BREW = "ruined_brew"`, `static void grant(Player player, String name)`.

- [ ] **Step 1: Create `src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java`:**

```java
package com.hogwartsmagic.brewing;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Grants the brewing advancements that no vanilla trigger can see (they use minecraft:impossible). Server side only.
 * Same approach as VoldemortEntity.grantAllCriteria.
 */
public final class BrewAdvancements {
    /** hogwartsmagic:hp/first_brew: bottled a dose of a finished brew. */
    public static final String FIRST_BREW = "first_brew";
    /** hogwartsmagic:hp/ruined_brew: a step of yours ruined a brew. */
    public static final String RUINED_BREW = "ruined_brew";

    private BrewAdvancements() {
    }

    /**
     * Completes hogwartsmagic:hp/name for a real server player. A client-side player, a FakePlayer, a missing
     * advancement or one already done is ignored.
     */
    public static void grant(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) {
            return;
        }
        MinecraftServer server = serverPlayer.serverLevel().getServer();
        AdvancementHolder advancement = server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "hp/" + name));
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = serverPlayer.getAdvancements();
        AdvancementProgress state = progress.getOrStartProgress(advancement);
        if (state.isDone()) {
            return;
        }
        for (String criterion : state.getRemainingCriteria()) {
            progress.award(advancement, criterion);
        }
    }
}
```

- [ ] **Step 2: Create `src/main/resources/data/hogwartsmagic/advancement/hp/first_brew.json`:**

```json
{
  "parent": "hogwartsmagic:hp/cauldron_class",
  "display": {
    "icon": {
      "id": "hogwartsmagic:wiggenweld_potion",
      "count": 1
    },
    "title": {
      "translate": "advancement.hogwartsmagic.first_brew.title"
    },
    "description": {
      "translate": "advancement.hogwartsmagic.first_brew.description"
    },
    "frame": "task",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": false
  },
  "criteria": {
    "bottled_a_finished_brew": {
      "trigger": "minecraft:impossible"
    }
  }
}
```

- [ ] **Step 3: Create `src/main/resources/data/hogwartsmagic/advancement/hp/ruined_brew.json`:**

```json
{
  "parent": "hogwartsmagic:hp/cauldron_class",
  "display": {
    "icon": {
      "id": "hogwartsmagic:failed_potion",
      "count": 1
    },
    "title": {
      "translate": "advancement.hogwartsmagic.ruined_brew.title"
    },
    "description": {
      "translate": "advancement.hogwartsmagic.ruined_brew.description"
    },
    "frame": "task",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": false
  },
  "criteria": {
    "ruined_a_brew": {
      "trigger": "minecraft:impossible"
    }
  }
}
```

- [ ] **Step 4: Create `src/main/resources/data/hogwartsmagic/advancement/hp/liquid_luck.json`:**

```json
{
  "parent": "hogwartsmagic:hp/first_brew",
  "display": {
    "icon": {
      "id": "hogwartsmagic:felix_felicis",
      "count": 1
    },
    "title": {
      "translate": "advancement.hogwartsmagic.liquid_luck.title"
    },
    "description": {
      "translate": "advancement.hogwartsmagic.liquid_luck.description"
    },
    "frame": "challenge",
    "show_toast": true,
    "announce_to_chat": true,
    "hidden": false
  },
  "criteria": {
    "has_felix_felicis": {
      "trigger": "minecraft:inventory_changed",
      "conditions": {
        "items": [
          {
            "items": [
              "hogwartsmagic:felix_felicis"
            ]
          }
        ]
      }
    }
  }
}
```

- [ ] **Step 5: Lang.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json` find the line

```
  "advancement.hogwartsmagic.polyjuice_plot.description": "Brew Polyjuice Potion and prepare for a very bad idea.",
```

and add right after it:

```
  "advancement.hogwartsmagic.first_brew.title": "First Brew",
  "advancement.hogwartsmagic.first_brew.description": "Brew your first potion in a Wizard Cauldron and bottle it.",
  "advancement.hogwartsmagic.ruined_brew.title": "Toil and Trouble",
  "advancement.hogwartsmagic.ruined_brew.description": "Ruin a brew with a wrong step.",
  "advancement.hogwartsmagic.liquid_luck.title": "Liquid Luck",
  "advancement.hogwartsmagic.liquid_luck.description": "Hold a bottle of Felix Felicis.",
```

- [ ] **Step 6: The import.** In `WizardCauldronBlockEntity.java` find

```java
import com.hogwartsmagic.brewing.BrewRules;
```

and replace it with

```java
import com.hogwartsmagic.brewing.BrewAdvancements;
import com.hogwartsmagic.brewing.BrewRules;
```

- [ ] **Step 7: First Brew.** In `bottle(...)` find (once)

```java
            result = bottledPotion(match.recipe());
```

and replace it with

```java
            result = bottledPotion(match.recipe());
            if (!result.is(ExampleMod.FAILED_POTION.get())) {
                BrewAdvancements.grant(player, BrewAdvancements.FIRST_BREW);
            }
```

- [ ] **Step 8: Toil and Trouble.** In `ruin(...)` find (once)

```java
            tell(player, "ruin");
```

and replace it with

```java
            tell(player, "ruin");
            BrewAdvancements.grant(player, BrewAdvancements.RUINED_BREW);
```

(It sits inside `if (player != null)`, so the overbrew ruin, which passes null, grants nothing.)

- [ ] **Step 9: Validate JSON.**

```bash
cd /home/user/minecraft_mods && for f in src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/resources/data/hogwartsmagic/advancement/hp/first_brew.json src/main/resources/data/hogwartsmagic/advancement/hp/ruined_brew.json src/main/resources/data/hogwartsmagic/advancement/hp/liquid_luck.json; do python3 -c "import json,sys;json.load(open(sys.argv[1],encoding='utf-8'));print('ok',sys.argv[1])" "$f"; done
```

Expected: four `ok` lines.

- [ ] **Step 10: Build and test.** Expected `BUILD SUCCESSFUL`, BASE + 4 tests, all green.

- [ ] **Step 11: Boot.** Run the boot block. Expected: `Done (`, no `Exception`, and `grep -n "first_brew\|ruined_brew\|liquid_luck" run/logs/latest.log` prints nothing (an advancement that fails to parse is logged with its id). Stop the server as step 5 says.

- [ ] **Step 12: Commit.**

```bash
cd /home/user/minecraft_mods && cat > /tmp/msg.txt <<'EOF'
Brewing Phase 3: First Brew, Toil and Trouble and Liquid Luck advancements

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java src/main/resources/data/hogwartsmagic/advancement/hp/first_brew.json src/main/resources/data/hogwartsmagic/advancement/hp/ruined_brew.json src/main/resources/data/hogwartsmagic/advancement/hp/liquid_luck.json
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java src/main/resources/data/hogwartsmagic/advancement/hp/first_brew.json src/main/resources/data/hogwartsmagic/advancement/hp/ruined_brew.json src/main/resources/data/hogwartsmagic/advancement/hp/liquid_luck.json src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java
```

---

## Task 3: The Potions classroom in the schematic

**Files:**
- Create: `build_scripts/add_potions_classroom.py`
- Modify (by the script): `src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem`

**Interfaces:**
- Consumes: the schematic as it is on the branch (Sponge v3, `Schematic/Blocks/Palette` + `Data`), `build_scripts/schem_to_nbt.py` (for the preview), the skill `rendering-minecraft-structures`.
- Produces: chests at schematic (42, 33, 111) and (42, 33, 114), seven `hogwartsmagic:wizard_cauldron[level=3]` over `minecraft:magma_block`, a stone brick floor at y 32 over the tower's 96 enclosed cells.

- [ ] **Step 1: Create `build_scripts/add_potions_classroom.py`** with exactly this content:

```python
"""Furnish the Potions classroom in mini_hogwarts.schem.

    python3 build_scripts/add_potions_classroom.py [--schem PATH] [--dry-run]

WHERE
-----
Behind the Transfiguration room of the gatehouse (the spruce door at schematic x=38, y=33-34,
z=106) stands the tall apse tower (x 32..45, z 106..120). It has walls from y=31 up to its roof at
y~63 but no floor: the door opened onto a five-block ledge (cobblestone at y=32, x 34..38,
z 107..109) and a drop of about thirty blocks to whatever ground the castle was dropped on. This
script lays a floor at y=32 over every air cell enclosed by the tower walls (96 cells: x 34..42,
z 107..119) and furnishes the room at y=33:

  * six student cauldrons (x 36, 38, 40 by z 111, 114) and a teacher's cauldron at (38, 116),
    each a hogwartsmagic:wizard_cauldron[level=3] (a full, classic green brew) over a magma
    block in the floor, so every cauldron is heated and works the moment it is used;
  * a lectern at (38, 118) facing the class;
  * twelve empty chiseled bookshelves (the shelves) along the west wall, x 34, z 110..115, y 33-34;
  * along the east wall, x 42: barrels at z 110, 112, 113, 115, two lanterns on the end barrels,
    and two chests (facing west) at z 111 and 114. MiniHogwartsManager fills every chest of the
    schematic with ClassroomSupplies when a castle is built (the castle had no chests before).

Nothing else changes: every other cell, the palette order, the block entities and the metadata are
written back as they were. The NBT is read and written with tag types preserved, and the script
proves it by re-encoding the untouched tree and comparing bytes before it edits anything.

Re-running is safe: a schematic that already holds a wizard cauldron is left alone.
"""

import argparse
import gzip
import io
import os
import struct
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SCHEM = os.path.join(REPO, 'src', 'main', 'resources', 'data', 'hogwartsmagic',
                             'schematic', 'mini_hogwarts.schem')

# --- type-preserving NBT -------------------------------------------------------------
# A node is (tag_id, value). Compounds are lists of (name, node) pairs so the key order
# survives; lists are (elem_id, [values]).

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, \
    INT_ARRAY, LONG_ARRAY = range(13)
_FMT = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}


class Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def take(self, n):
        out = self.b[self.i:self.i + n]
        self.i += n
        return out

    def num(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.num('>H')).decode('utf-8')

    def payload(self, tag):
        if tag in _FMT:
            return self.num(_FMT[tag])
        if tag == BYTE_ARRAY:
            return bytes(self.take(self.num('>i')))
        if tag == STRING:
            return self.string()
        if tag == LIST:
            elem = self.num('>b')
            count = self.num('>i')
            return (elem, [self.payload(elem) for _ in range(count)])
        if tag == COMPOUND:
            items = []
            while True:
                child = self.num('>b')
                if child == END:
                    return items
                name = self.string()
                items.append((name, (child, self.payload(child))))
        if tag == INT_ARRAY:
            return [self.num('>i') for _ in range(self.num('>i'))]
        if tag == LONG_ARRAY:
            return [self.num('>q') for _ in range(self.num('>i'))]
        raise ValueError('unknown tag id %d' % tag)


def encode_payload(tag, value):
    if tag in _FMT:
        return struct.pack(_FMT[tag], value)
    if tag == BYTE_ARRAY:
        return struct.pack('>i', len(value)) + bytes(value)
    if tag == STRING:
        raw = value.encode('utf-8')
        return struct.pack('>H', len(raw)) + raw
    if tag == LIST:
        elem, values = value
        return struct.pack('>bi', elem, len(values)) + b''.join(encode_payload(elem, v) for v in values)
    if tag == COMPOUND:
        out = b''
        for name, (child, child_value) in value:
            raw = name.encode('utf-8')
            out += struct.pack('>bH', child, len(raw)) + raw + encode_payload(child, child_value)
        return out + b'\x00'
    if tag == INT_ARRAY:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>i', v) for v in value)
    if tag == LONG_ARRAY:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>q', v) for v in value)
    raise ValueError('unknown tag id %d' % tag)


def read_root(raw):
    r = Reader(raw)
    if r.num('>b') != COMPOUND:
        sys.exit('not an NBT compound')
    name = r.string()
    value = r.payload(COMPOUND)
    if r.i != len(raw):
        sys.exit('trailing bytes after the root compound')
    return name, value


def encode_root(name, value):
    raw = name.encode('utf-8')
    return struct.pack('>bH', COMPOUND, len(raw)) + raw + encode_payload(COMPOUND, value)


def child(compound, name):
    for key, node in compound:
        if key == name:
            return node
    sys.exit('missing tag %s' % name)


def set_child(compound, name, node):
    for i, (key, _) in enumerate(compound):
        if key == name:
            compound[i] = (name, node)
            return
    sys.exit('missing tag %s' % name)


# --- varints (the Sponge block data) ----------------------------------------------------

def decode_varints(data, expected):
    out = []
    cursor = 0
    while len(out) < expected and cursor < len(data):
        value = 0
        shift = 0
        while True:
            byte = data[cursor]
            cursor += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
        out.append(value)
    if len(out) != expected or cursor != len(data):
        sys.exit('block data: decoded %d cells from %d of %d bytes, expected %d cells'
                 % (len(out), cursor, len(data), expected))
    return out


def encode_varints(values):
    out = bytearray()
    for value in values:
        while True:
            byte = value & 0x7F
            value >>= 7
            if value:
                out.append(byte | 0x80)
            else:
                out.append(byte)
                break
    return bytes(out)


# --- the room ---------------------------------------------------------------------------

FLOOR_Y = 32
ROOM_Y = 33
SEED = (39, 112)              # an air cell in the middle of the tower at FLOOR_Y
EXPECTED_FLOOR_CELLS = 96
EXPECTED_BOX = (34, 42, 107, 119)   # x0, x1, z0, z1 of the enclosed air at FLOOR_Y

FLOOR = 'minecraft:stone_bricks'
HEAT = 'minecraft:magma_block'
CAULDRON = 'hogwartsmagic:wizard_cauldron[level=3]'
LECTERN = 'minecraft:lectern[facing=north,has_book=false,powered=false]'
SHELF = ('minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=false,'
         'slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]')
BARREL = 'minecraft:barrel[facing=up,open=false]'
CHEST = 'minecraft:chest[facing=west,type=single,waterlogged=false]'
LANTERN = 'minecraft:lantern[hanging=false,waterlogged=false]'

CAULDRONS = [(36, 111), (38, 111), (40, 111), (36, 114), (38, 114), (40, 114), (38, 116)]


def furniture():
    """(x, y, z, state) for everything that stands on the floor."""
    out = [(x, ROOM_Y, z, CAULDRON) for x, z in CAULDRONS]
    out.append((38, ROOM_Y, 118, LECTERN))
    for z in range(110, 116):
        out.append((34, ROOM_Y, z, SHELF))
        out.append((34, ROOM_Y + 1, z, SHELF))
    for z in (110, 112, 113, 115):
        out.append((42, ROOM_Y, z, BARREL))
    for z in (111, 114):
        out.append((42, ROOM_Y, z, CHEST))
    out.append((42, ROOM_Y + 1, 110, LANTERN))
    out.append((42, ROOM_Y + 1, 115, LANTERN))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--schem', default=DEFAULT_SCHEM)
    ap.add_argument('--dry-run', action='store_true', help='check and report, write nothing')
    args = ap.parse_args()

    with open(args.schem, 'rb') as fh:
        raw = gzip.decompress(fh.read())
    root_name, root = read_root(raw)
    if encode_root(root_name, root) != raw:
        sys.exit('NBT round trip is not byte-identical; refusing to edit')

    schem = child(root, 'Schematic')[1]
    width = child(schem, 'Width')[1] & 0xFFFF
    height = child(schem, 'Height')[1] & 0xFFFF
    length = child(schem, 'Length')[1] & 0xFFFF
    blocks = child(schem, 'Blocks')[1]
    palette_tag, palette = child(blocks, 'Palette')
    data_tag, data = child(blocks, 'Data')
    if palette_tag != COMPOUND or data_tag != BYTE_ARRAY:
        sys.exit('unexpected Palette/Data tag types')

    by_state = {name: node[1] for name, node in palette}
    if any(name.startswith('hogwartsmagic:wizard_cauldron') for name in by_state):
        print('mini_hogwarts.schem already has the Potions classroom; nothing to do')
        return

    cells = decode_varints(data, width * height * length)
    air = by_state['minecraft:air']

    def index(x, y, z):
        return x + z * width + y * width * length

    def state_id(state):
        if state not in by_state:
            new_id = max(by_state.values()) + 1
            by_state[state] = new_id
            palette.append((state, (INT, new_id)))
        return by_state[state]

    # The floor: every air cell at FLOOR_Y that the tower walls enclose.
    seen = {SEED}
    stack = [SEED]
    if cells[index(SEED[0], FLOOR_Y, SEED[1])] != air:
        sys.exit('the seed cell is not air: wrong schematic?')
    while stack:
        x, z = stack.pop()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in seen:
                continue
            if not (0 <= nx < width and 0 <= nz < length):
                sys.exit('the floor leaks out of the schematic: wrong schematic?')
            if cells[index(nx, FLOOR_Y, nz)] == air:
                seen.add((nx, nz))
                stack.append((nx, nz))
    box = (min(x for x, _ in seen), max(x for x, _ in seen), min(z for _, z in seen), max(z for _, z in seen))
    if len(seen) != EXPECTED_FLOOR_CELLS or box != EXPECTED_BOX:
        sys.exit('enclosed floor is %d cells in %s, expected %d in %s: wrong schematic?'
                 % (len(seen), box, EXPECTED_FLOOR_CELLS, EXPECTED_BOX))

    items = furniture()
    heated = set(CAULDRONS)
    for x, y, z, state in items:
        if cells[index(x, y, z)] != air:
            sys.exit('(%d, %d, %d) is not air; refusing to overwrite it' % (x, y, z))
        if y == ROOM_Y and (x, z) not in seen:
            sys.exit('(%d, %d, %d) does not stand on the new floor' % (x, y, z))
    for x, z in heated:
        if (x, z) not in seen:
            sys.exit('cauldron (%d, %d) is off the floor' % (x, z))

    floor_id = state_id(FLOOR)
    heat_id = state_id(HEAT)
    for x, z in seen:
        cells[index(x, FLOOR_Y, z)] = heat_id if (x, z) in heated else floor_id
    for x, y, z, state in items:
        cells[index(x, y, z)] = state_id(state)

    set_child(blocks, 'Data', (BYTE_ARRAY, encode_varints(cells)))

    print('floor: %d cells (%d magma), furniture: %d blocks, palette now %d entries'
          % (len(seen), len(heated), len(items), len(by_state)))
    if args.dry_run:
        print('dry run: nothing written')
        return
    buf = io.BytesIO()
    with gzip.GzipFile(filename='', mode='wb', fileobj=buf, mtime=0) as fh:
        fh.write(encode_root(root_name, root))
    with open(args.schem, 'wb') as out:
        out.write(buf.getvalue())
    print('wrote %s (%d bytes)' % (args.schem, os.path.getsize(args.schem)))


if __name__ == '__main__':
    main()
```

- [ ] **Step 2: Dry run.** `cd /home/user/minecraft_mods && python3 build_scripts/add_potions_classroom.py --dry-run`. Expected exactly:

```
floor: 96 cells (7 magma), furniture: 28 blocks, palette now 156 entries
dry run: nothing written
```

If it exits with "wrong schematic?" or "not air", someone changed the schematic: stop and report. Do not edit coordinates to make it pass.

- [ ] **Step 3: Write it.** `python3 build_scripts/add_potions_classroom.py`. Expected the same first line, then `wrote .../mini_hogwarts.schem (about 33000 bytes)`. Run it a second time: expected `mini_hogwarts.schem already has the Potions classroom; nothing to do`, and `git diff --stat` still shows the file changed once.

- [ ] **Step 4: Check the result with the independent reader.** `python3 build_scripts/schem_to_nbt.py src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem --crop 31 31 105 45 36 120 -o build/schem_nbt/potions_classroom.nbt`. Expected: `mini_hogwarts.schem  96x109x145  1517280 cells  palette 156` and no `warning: decoded` line. Then confirm nothing outside the room changed:

```bash
cd /home/user/minecraft_mods && git show HEAD:src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem > /tmp/old.schem && python3 - <<'EOF'
import sys
sys.path.insert(0, 'build_scripts')
import schem_to_nbt as s
W, H, L, old_by, old = s.decode_schem(s.read_nbt('/tmp/old.schem'))
_, _, _, new_by, new = s.decode_schem(s.read_nbt('src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem'))
changed = [i for i in range(W * H * L) if old_by[old[i]] != new_by[new[i]]]
outside = []
for i in changed:
    x, z, y = i % W, (i // W) % L, i // (W * L)
    if not (34 <= x <= 42 and 107 <= z <= 119 and 32 <= y <= 34):
        outside.append((x, y, z))
print('changed', len(changed), 'outside the room', outside)
EOF
```

Expected: `changed 124 outside the room []` (96 floor cells + 28 furniture blocks).

- [ ] **Step 5: Render it and look.** Invoke the skill `rendering-minecraft-structures` and follow it. At minimum:

```bash
cd /home/user/minecraft_mods && python3 .claude/skills/rendering-minecraft-structures/render_structure.py build/schem_nbt/potions_classroom.nbt --views iso,layers --cell 24 --out build/shots/potions_classroom
```

Open (Read) `build/shots/potions_classroom/potions_classroom_layers.png` and `_iso.png` and check: layer y=1 is a closed floor with seven magma cells in two rows of three and one behind; layer y=2 shows the seven cauldrons over them, the shelf row along the west wall, barrels and two chests along the east wall, and the lectern at the narrow (south) end; the door gap in the north wall lines up with the free landing. If the textured viewer of the skill can run in this environment, render the same crop there too (`cut=z`, the room is a shell) and look at density: the magma should read as seven small embers, not a lava floor. Describe what you saw in the task report. If anything is wrong, stop and report; do not change coordinates on your own.

- [ ] **Step 6: Build and test** (the jar now carries the new schematic). Expected `BUILD SUCCESSFUL`, BASE + 4, all green.

- [ ] **Step 7: Commit.**

```bash
cd /home/user/minecraft_mods && cat > /tmp/msg.txt <<'EOF'
Brewing Phase 3: Potions classroom in the Mini Hogwarts apse tower

Floors the floorless tower behind the gatehouse upstairs room and furnishes it:
seven heated Wizard Cauldrons, shelves, barrels, a lectern and two chests.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add build_scripts/add_potions_classroom.py
git commit -F /tmp/msg.txt -- build_scripts/add_potions_classroom.py src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem
```

(`build/` is not committed.)

---

## Task 4: Stock the classroom chests

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsManager.java`

**Interfaces:**
- Consumes: `ClassroomSupplies.roll`, `ClassroomSupplies.Stack` (Task 1); the schematic chests (Task 3); `PlacementJob.anchor`, `level`, `analyze()`, `furnish()`; `BuiltInRegistries.ITEM.getOptional(ResourceLocation)`; `ChestBlockEntity.getContainerSize()`, `setItem(int, ItemStack)`.
- Produces: `PlacementJob.schematicChests` (local `BlockPos` list), `PlacementJob.stockClassroomChests(RandomSource) -> int`, `PlacementJob.shuffledSlots(int, RandomSource) -> int[]`.

- [ ] **Step 1: Imports.** Find

```java
import com.hogwartsmagic.ExampleMod;
```

(the first import of the file) and replace it with

```java
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.ClassroomSupplies;
```

Then find

```java
import net.minecraft.core.Direction;
```

and replace it with

```java
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
```

- [ ] **Step 2: The field.** Find

```java
        private int topX = -1, topY = -1, topZ = -1; // highest solid block (for a tower treasure)
```

and replace it with

```java
        private int topX = -1, topY = -1, topZ = -1; // highest solid block (for a tower treasure)
        /** Chests in the schematic itself (local x, y, z): the Potions classroom's ingredient chests. */
        private final List<BlockPos> schematicChests = new ArrayList<>();
```

- [ ] **Step 3: Record them in `analyze()`.** Find

```java
                } else if (state.is(Blocks.IRON_BARS)) {
```

and replace it with

```java
                } else if (state.is(Blocks.CHEST)) {
                    schematicChests.add(new BlockPos(x, y, z));
                } else if (state.is(Blocks.IRON_BARS)) {
```

- [ ] **Step 4: Call it from `furnish()`.** Find

```java
            LOGGER.info("Mini Hogwarts furnished with {} themed chests", placed);
```

and replace it with

```java
            LOGGER.info("Mini Hogwarts furnished with {} themed chests", placed);
            int stocked = stockClassroomChests(rng);
            LOGGER.info("Mini Hogwarts Potions classroom: {} ingredient chests stocked", stocked);
```

- [ ] **Step 5: The methods.** Find

```java
        /** Populate the finished castle with a crowd of wandering Hogwarts students across its floors. */
```

and insert right before it:

```java
        /**
         * Fills every chest that came from the schematic (the Potions classroom's) with one roll of
         * ClassroomSupplies, in shuffled slots. Returns how many chests were stocked.
         */
        private int stockClassroomChests(RandomSource rng) {
            int stocked = 0;
            for (BlockPos local : schematicChests) {
                BlockPos pos = anchor.offset(local);
                if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                    continue;
                }
                List<ClassroomSupplies.Stack> stacks = ClassroomSupplies.roll(
                        (min, max) -> min + rng.nextInt(max - min + 1), rng.nextDouble());
                int[] slots = shuffledSlots(chest.getContainerSize(), rng);
                int next = 0;
                for (ClassroomSupplies.Stack stack : stacks) {
                    Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(stack.item())).orElse(null);
                    if (item == null || next >= slots.length) {
                        continue;
                    }
                    chest.setItem(slots[next++], new ItemStack(item, stack.count()));
                }
                chest.setChanged();
                stocked++;
            }
            return stocked;
        }

        /** The slot numbers 0..size-1 in a random order. */
        private static int[] shuffledSlots(int size, RandomSource rng) {
            int[] slots = new int[size];
            for (int i = 0; i < size; i++) {
                slots[i] = i;
            }
            for (int i = size - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int swap = slots[i];
                slots[i] = slots[j];
                slots[j] = swap;
            }
            return slots;
        }

```

(`Item`, `ItemStack`, `ResourceLocation`, `ChestBlockEntity`, `RandomSource`, `List` and `ArrayList` are already imported by this file.)

- [ ] **Step 6: Build and test.** Expected `BUILD SUCCESSFUL`, BASE + 4, all green.

- [ ] **Step 7: Boot.** Run the boot block. Expected `Done (` and no `Exception`. (The schematic is parsed when a castle is first built, not at boot; the in-game checklist covers it.)

- [ ] **Step 8: Commit.**

```bash
cd /home/user/minecraft_mods && cat > /tmp/msg.txt <<'EOF'
Brewing Phase 3: stock the Potions classroom chests when a castle is built

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsManager.java
```

---

## Task 5: `MOD_FEATURES_WIKI.md`

**Files:**
- Modify: `MOD_FEATURES_WIKI.md`

**Interfaces:** consumes the facts in the rulings above and in the Phase 1 and 2 plans; produces no code.

Before editing, read the current `### Wizard Cauldron`, `### Basilisk`, `### Petrified Remains` and `## Potions and Effects` sections: the Phase 2 fix wave already rewrote the Basilisk and Petrified Remains entries, which stay as they are. If any anchor below is missing because a later commit reworded it, stop and report.

- [ ] **Step 1: Core systems.** Find

```
- Polyjuice disguises
  Players can collect identity samples, brew Polyjuice in the Wizard Cauldron, transform after a short delay, and maintain disguises for the potion duration.
```

and add after it (keep one blank line between bullets):

```

- Cauldron brewing
  Potions are brewed step by step in the Wizard Cauldron over heat: add ingredients, stir and let it simmer, in order, and watch the liquid change colour. Seven recipes: Wiggenweld Potion, Pepperup Potion, Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis and Polyjuice Potion. The Hogwarts Guide (Potions tab) lists every recipe.

- Petrification
  A basilisk's full gaze turns a player to stone instead of killing them. A stone player waits for a friend with a Mandrake Restorative Draught or gives in and respawns.
```

- [ ] **Step 2: Wizard Cauldron.** Find

```
- Recipes: Wiggenweld Potion, Pepperup Potion and Polyjuice Potion (Hogwarts Guide, Potions).
```

and replace it with

```
- Recipes: Wiggenweld Potion, Pepperup Potion, Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis and Polyjuice Potion (Hogwarts Guide, Potions). Each is described under Potions and Effects.
- Felix Felicis gives a single dose; every other brew gives 3.
- Gunpowder after a finished Mandrake Restorative Draught makes every dose a splash potion. The Draught of Living Death always ends with Gunpowder: it is splash only.
- The Mini Hogwarts Potions classroom has seven of these cauldrons, full and already over heat.
```

- [ ] **Step 3: The Dittany plant.** Find

```
### Fine Wood Tree Set
```

and insert before it:

```
### Dittany Plant

- A small herb that grows wild in patches of two to four in the Forbidden Forest and the Horntail Peaks, on earth and on bare rock.
- Breaking a wild plant gives two Dittany; a plant you set down yourself gives back one.
- Dittany can be planted on earth or stone. It does not spread.

```

- [ ] **Step 4: The stirring rod.** Find

```
- Fine Wood Stick
  Base crafted wand body used for real wandcraft.
```

and replace it with

```
- Fine Wood Stick
  Base crafted wand body used for real wandcraft. It is also the stirring rod for the Wizard Cauldron: right-click to stir clockwise, sneak and right-click (other hand empty) to stir anticlockwise.
```

- [ ] **Step 5: Creature ingredients.** Find

```
- Werewolf Fang
  Used in Wizard Potion brewing.
```

and replace it with

```
- Werewolf Fang
  Magical creature drop/material.
```

Then find

```
- Acromantula Venom
  Magical creature drop/material.
```

and replace it with

```
- Acromantula Venom
  Magical creature drop. Used in the Wizard Potion (brewing stand), and in Polyjuice Potion and Felix Felicis (Wizard Cauldron).
```

- [ ] **Step 6: Brewing ingredients.** Find

```
### Gillyweed

- Eaten as a consumable.
```

and insert before it:

```
### Brewing Ingredients

- Dittany
  Picked from the Dittany plant, or bought from an apothecary (2 for 6 emeralds). Essence of Dittany.

- Asphodel
  Apothecary (second level), 1 for 8 emeralds. Mandrake Restorative Draught and Draught of Living Death.

- Wormwood
  Apothecary (second level), 2 for 6 emeralds. Draught of Living Death.

- Valerian Sprigs
  Apothecary, 2 for 4 emeralds; also in about a quarter of the Hogwarts ruin camp chests. Draught of Living Death.

- Ashwinder Egg
  Rare: 1 Death Eater camp chest in 10, about 1 Chamber of Secrets chest in 7, and 1 Blaze in 20. It does not burn. Felix Felicis.

- Mandrake Root
  Pulled from a Mandrake. Pepperup Potion, Mandrake Restorative Draught and Polyjuice Potion.

- Everyday ingredients
  Honey Bottle and Glow Berries (Wiggenweld), Blaze Powder (Pepperup), Arcane Dust (Mandrake Draught), Gold Nugget (Felix Felicis), Gillyweed (Polyjuice), and Redstone Dust and Gunpowder to extend Polyjuice or make a splash potion.

- Where to buy
  Apothecaries sell Dittany and Valerian Sprigs from their first level and Asphodel and Wormwood from their second. Each apothecary stocks only some of them, so try another shop. The Potions classroom in Mini Hogwarts has two chests of supplies.

```

- [ ] **Step 7: The potions.** Find

```
### Major Custom Effects
```

and insert before it:

```
### Wiggenweld Potion

- Brewed: 1 Honey Bottle, 2 Glow Berries, stir clockwise 3 times, simmer 20 seconds. 3 doses.
- Heals 8 health over about 2 seconds and stops Bleeding, which milk cannot.

### Pepperup Potion

- Brewed: 1 Blaze Powder, 1 Mandrake Root, stir anticlockwise 2 times, simmer 15 seconds. 3 doses.
- Restores 40 mana at once and gives 30 seconds of Fire Resistance. Steam pours from your ears.

### Essence of Dittany

- Brewed: 3 Dittany, stir clockwise 5 times, simmer 30 seconds. 3 doses.
- Closes a Cursed Wound (Dolohov's Curse) and heals 4 health.

### Mandrake Restorative Draught

- Brewed: 2 Mandrake Roots, 1 Asphodel, stir clockwise 4 times, simmer 45 seconds, then 1 Arcane Dust. 3 doses.
- Add 1 Gunpowder after the Arcane Dust and every dose is a Splash Mandrake Restorative Draught.
- Right-click a player turned to stone, or a creature's statue, with the bottle to bring them back (one dose).
- Thrown, it frees every stone player and statue within 4 blocks.
- Drunk, it clears a basilisk's building gaze, and for 8 seconds the gaze cannot build on you.

### Draught of Living Death

- Brewed: 1 Asphodel, 1 Wormwood, 2 Valerian Sprigs, stir anticlockwise 7 times, stir clockwise once (the brew turns clear as water), simmer 60 seconds, then 1 Gunpowder. 3 doses.
- Splash only. Everything within 4 blocks, the thrower too, is stunned for 20 seconds. The usual crowd-control rules apply: repeated stuns get shorter, bosses get half, and players take 60%.

### Felix Felicis

- Brewed: 1 Ashwinder Egg, 1 Acromantula Venom, 1 Gold Nugget, stir clockwise 3 times, simmer 10 minutes, stir anticlockwise once. One dose only.
- 3 minutes of Luck III (better loot) and a quarter more spell mastery XP.

### Failed Potion

- What a ruined brew bottles as. Drinking it gives 10 seconds of Nausea and 3 seconds of Poison.
- An empty bucket pours a ruined brew away instead.

```

- [ ] **Step 8: The effect line.** Find

```
- `Basilisk Petrification`
  Marks petrified victims.
```

and replace it with

```
- `Basilisk Petrification`
  Holds a player turned to stone (see Petrification), and marks a creature whose death leaves a statue.
```

- [ ] **Step 9: Petrification.** Find

```
## House Perks
```

and insert before it:

```
### Petrification

- A basilisk's full gaze turns a survival or adventure player to stone instead of killing them. Creative and spectator players are not affected, and bosses still die to it.
- While stone you cannot move, look around, attack, use items, cast or open your inventory. Nothing hurts you except the void and `/kill`, you do not get hungry or drown, and mobs ignore you. Chat still works, so you can call for help.
- A screen asks you to choose: Wait for rescue, or Give in. Giving in is a normal death (your items drop unless keepInventory is on, and a Horcrux still brings you back). Esc means wait; press J (it can be rebound) to open the screen again.
- A friend frees you with a Mandrake Restorative Draught: right-click you with the bottle, or throw the splash potion. You come back with 5 seconds of Slowness, and both of you get a message.
- Logging out does not end it: you log back in as stone. A server can set a time limit with the config value `petrificationMaxSeconds` (0, the default, means none); when it runs out you give in automatically.
- Creatures turned to stone leave Petrified Remains (see Mobs) that the Draught brings back.

```

- [ ] **Step 10: World content.** Find

```
### Gringotts
```

(the heading line in `## World Content and Generation`; it occurs once) and insert before it:

```
### Dittany Patches

- Small patches of Dittany, two to four plants, on the ground of the Forbidden Forest and the Horntail Peaks.

### Mini Hogwarts

- A small castle raised in the Forbidden Forest the first time a player goes there (no two within 6000 blocks). `/spawnminihogwarts` builds one, `/findminihogwarts` finds the nearest.
- Students wander its floors, a few house elves keep below stairs, and themed chests wait in its library, spell study, dungeon, great hall and tower.
- **Potions classroom.** Climb the gatehouse stairs to the upper room with the bookshelves: the door in its south wall opens into a tall Potions classroom with seven Wizard Cauldrons over magma (six for students, one for the teacher), shelves, a lectern and two ingredient chests. Each chest holds enough for one brew of every common recipe, with bottles, a water bucket and a stirring rod; now and then one holds an Ashwinder Egg. The cauldrons are full and heated, so you can start brewing straight away.
- Castles that already stand are never rebuilt or changed, so only castles built after this update have the classroom.

```

- [ ] **Step 11: Advancements.** Find

```
- Polyjuice brewing.
```

(in the `### Advancements` list) and replace it with

```
- Polyjuice brewing.
- Bottling your first finished brew (First Brew).
- Ruining a brew with a wrong step (Toil and Trouble).
- Holding Felix Felicis (Liquid Luck).
```

- [ ] **Step 12: Read it back.** Read every section you touched once more against the code: `PotionRules` numbers, `BrewRules` recipes, `ModVillagers` apothecary trades, `en_us.json` names (for example "Mandrake Restorative Draught", "Valerian Sprigs", "Fine Wood Stick"), and the give-in key default in `client/PetrificationClient` (J). Fix any line that disagrees with the code and say so in the report.

- [ ] **Step 13: Commit.**

```bash
cd /home/user/minecraft_mods && cat > /tmp/msg.txt <<'EOF'
Brewing Phase 3: MOD_FEATURES_WIKI covers every brewing feature

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- MOD_FEATURES_WIKI.md
```

---

## Task 6: The web wiki (`wiki/data.js`)

**Files:**
- Modify: `wiki/data.js`

**Interfaces:** consumes the text of Task 5; produces no code. `app.js` renders `systems`, `mobs`, `blocks` and `items` generically (`name`/`title`, `kind`/`role`, `summary`, `features`; mobs also `id`, `icon`, `threat`, `habitat`, `detail`).

- [ ] **Step 1: Systems.** Find

```js
        "Base and extended Polyjuice durations"
      ]
    }
  ],
```

and replace it with

```js
        "Base and extended Polyjuice durations"
      ]
    },
    {
      id: "cauldron-brewing",
      title: "Cauldron Brewing",
      icon: "CB",
      summary: "Brew potions step by step in the Wizard Cauldron: water, heat, then ingredients, stirring and simmering in order.",
      features: [
        "Seven recipes: Wiggenweld, Pepperup, Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis and Polyjuice",
        "The liquid changes colour at every correct step; a wrong step ruins the brew",
        "3 doses per brew (Felix Felicis: 1); Gunpowder makes splash potions",
        "A Potions classroom in Mini Hogwarts with seven cauldrons ready to use"
      ]
    },
    {
      id: "petrification",
      title: "Petrification",
      icon: "PT",
      summary: "A basilisk's full gaze turns a player to stone: wait for a Mandrake Restorative Draught or give in.",
      features: [
        "Stone players cannot act, take no damage and are ignored by mobs",
        "A choice screen: Wait for rescue or Give in (J opens it again)",
        "Survives logging out; a server can set a time limit",
        "Creature statues keep the whole mob and come back with the Draught"
      ]
    }
  ],
```

- [ ] **Step 2: The statue.** Find

```js
detail: "A pure ritual delivery entity that exists to sell the Horcrux fantasy." }
```

and replace it with

```js
detail: "A pure ritual delivery entity that exists to sell the Horcrux fantasy." },
    { id: "petrified-remains", name: "Petrified Remains", icon: "PR", role: "Statue", threat: "neutral", habitat: "Wherever a basilisk has looked", summary: "The stone statue a creature leaves when a basilisk's full gaze catches it.", features: ["Keeps the whole mob: name, owner, gear, villager trades", "Drops nothing", "A Mandrake Restorative Draught brings the mob back", "Breaking it, or the void, loses the mob for good"], detail: "Bosses never leave a statue that comes back. Save the statue and you save the creature." }
```

- [ ] **Step 3: Blocks.** Find

```js
features: ["Wiggenweld, Pepperup and Polyjuice recipes", 
```

and replace it with

```js
features: ["Seven recipes: Wiggenweld, Pepperup, Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis and Polyjuice", "Seven ready to use in the Mini Hogwarts Potions classroom", 
```

Then find

```js
    { name: "Gillyweed Plant", kind: "Worldgen Plant", 
```

and insert this line before it:

```js
    { name: "Dittany Plant", kind: "Worldgen Plant", summary: "Herb that grows wild in the Forbidden Forest and the Horntail Peaks.", features: ["Patches of two to four on earth or bare rock", "A wild plant gives two Dittany, one you planted gives back one", "The ingredient of Essence of Dittany"] },
```

- [ ] **Step 4: Items.** Find

```js
    { name: "Gillyweed", kind: "Consumable", 
```

and insert these lines before it:

```js
    { name: "Wiggenweld Potion", kind: "Potion", summary: "Healing draught brewed in the Wizard Cauldron.", features: ["Honey Bottle, 2 Glow Berries, stir clockwise 3, simmer 20 s", "Heals 8 health over about 2 seconds", "Stops Bleeding"] },
    { name: "Pepperup Potion", kind: "Potion", summary: "Warming draught that restores mana.", features: ["Blaze Powder, Mandrake Root, stir anticlockwise 2, simmer 15 s", "Restores 40 mana", "30 seconds of Fire Resistance"] },
    { name: "Essence of Dittany", kind: "Potion", summary: "Closes cursed wounds.", features: ["3 Dittany, stir clockwise 5, simmer 30 s", "Cures a Cursed Wound", "Heals 4 health"] },
    { name: "Mandrake Restorative Draught", kind: "Potion", summary: "Brings back anyone the basilisk has turned to stone.", features: ["2 Mandrake Roots, Asphodel, stir clockwise 4, simmer 45 s, Arcane Dust", "Right-click a stone player or a statue with it", "Gunpowder after the dust makes a splash version that frees all stone within 4 blocks", "Drunk, the basilisk's gaze cannot build on you for 8 seconds"] },
    { name: "Draught of Living Death", kind: "Potion", summary: "Splash potion that puts everything nearby into a deathlike sleep.", features: ["Asphodel, Wormwood, 2 Valerian Sprigs, stir anticlockwise 7, clockwise 1, simmer 60 s, Gunpowder", "Stuns everything within 4 blocks for 20 seconds", "Repeated stuns get shorter; half on bosses, 60% on players"] },
    { name: "Felix Felicis", kind: "Potion", summary: "Liquid luck: one precious dose.", features: ["Ashwinder Egg, Acromantula Venom, Gold Nugget, stir clockwise 3, simmer 10 min, stir anticlockwise 1", "3 minutes of Luck III", "A quarter more spell mastery XP"] },
    { name: "Failed Potion", kind: "Potion", summary: "What a ruined brew bottles as.", features: ["10 seconds of Nausea and 3 of Poison", "An empty bucket pours a ruined brew away instead"] },
    { name: "Brewing Ingredients", kind: "Crafting", summary: "Herbs and rarities for the Wizard Cauldron.", features: ["Dittany: the Dittany plant, or apothecaries", "Asphodel and Wormwood: apothecaries (second level)", "Valerian Sprigs: apothecaries and Hogwarts ruin camp chests", "Ashwinder Egg: Death Eater camps, the Chamber, now and then a Blaze", "Two chests of supplies in the Mini Hogwarts Potions classroom"] },
```

- [ ] **Step 5: Creature ingredients and the stirring rod.** Find

```js
features: ["Used in advanced wand crafting", "Supports phoenix, unicorn, and dragon wand lines"]
```

and replace it with

```js
features: ["Used in advanced wand crafting", "Supports phoenix, unicorn, and dragon wand lines", "The Fine Wood Stick is also the cauldron stirring rod"]
```

- [ ] **Step 6: Stats.** Print the real counts:

```bash
cd /home/user/minecraft_mods && node -e "eval(require('fs').readFileSync('wiki/data.js','utf8')+';console.log(JSON.stringify({spells:WIKI_DATA.spells.length,mobs:WIKI_DATA.mobs.length,blocks:WIKI_DATA.blocks.length,systems:WIKI_DATA.systems.length}))')"
```

Expected (if nobody else added entries meanwhile): `{"spells":...,"mobs":17,"blocks":17,"systems":12}`. Edit the `stats:` block at the top of `data.js` so `mobs`, `blocks` and `systems` equal the printed numbers (leave `spells` as it is unless it differs from the printed count; then set it too).

- [ ] **Step 7: Syntax check.** Run the Step 6 command again: it must print the same counts with no error, and `stats` must now match them.

- [ ] **Step 8: Commit.**

```bash
cd /home/user/minecraft_mods && cat > /tmp/msg.txt <<'EOF'
Brewing Phase 3: web wiki covers brewing, potions and petrification

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- wiki/data.js
```

---

## Task 7: Final verification and the in-game checklist

**Files:** none changed (unless a check fails; then report, do not patch in this task).

- [ ] **Step 1: Clean build.** `pgrep` check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected `BUILD SUCCESSFUL`; the test count is BASE + 4, all green.

- [ ] **Step 2: JSON.** Validate `en_us.json` and the three advancement files with the Task 2 Step 9 loop. Expected four `ok`.

- [ ] **Step 3: The schematic.** `python3 build_scripts/add_potions_classroom.py --dry-run` prints `mini_hogwarts.schem already has the Potions classroom; nothing to do`.

- [ ] **Step 4: The wiki.** The Task 6 Step 6 node command prints counts equal to `stats`.

- [ ] **Step 5: Scope.** `git diff --stat <commit before Task 1>..HEAD` lists only the files in the File Structure table (plus ledger lines the controller commits).

- [ ] **Step 6: Boot.** The boot block: `Done (`, no `Exception`, no line naming `first_brew`, `ruined_brew` or `liquid_luck`; the server stopped.

- [ ] **Step 7: Write the in-game checklist into the ledger** for the user (a client is needed; the cloud cannot run one):

| # | Check |
|---|---|
| 1 | In a new world, run `/spawnminihogwarts` (or walk into a Forbidden Forest) and wait for the build to finish. `latest.log` has "Mini Hogwarts Potions classroom: 2 ingredient chests stocked" and no "Unknown block in mini_hogwarts palette". |
| 2 | Climb the gatehouse stairs to the upper room with the bookshelves; the door in its south wall opens onto a solid stone brick floor, no drop. |
| 3 | The room has seven green cauldrons over magma (two rows of three, one at the front by the lectern), chiseled shelves on the west wall, barrels with two lanterns and two chests on the east wall. |
| 4 | Both chests hold glass bottles, a water bucket, a Fine Wood Stick, honey bottles, glow berries, blaze powder, mandrake roots, dittany, asphodel, wormwood, valerian sprigs, arcane dust and gunpowder. |
| 5 | Brew a Wiggenweld Potion in a classroom cauldron without adding water first: the first honey bottle is accepted (the cauldron starts as three doses of water), and the brew simmers and finishes over the magma. |
| 6 | Bottle the first dose: the "First Brew" toast and chat line; in the advancement tab it hangs under "Potions Class". |
| 7 | With a fresh player, ruin a brew with a wrong ingredient: "Toil and Trouble". |
| 8 | With another fresh player, let a finished brew overbrew on the heat (120 s): it is ruined, and no "Toil and Trouble". Bottling the Failed Potion gives no "First Brew". |
| 9 | Brewing Polyjuice (with a sample) and bottling it gives "First Brew" to a player who lacks it; buying Polyjuice from an apothecary does not. |
| 10 | `/give @s hogwartsmagic:felix_felicis`: "Liquid Luck" (challenge frame), under "First Brew". |
| 11 | Load a world saved before this phase that already has a Mini Hogwarts: the castle is unchanged (the apse tower behind the upstairs room still has no floor), nothing is rebuilt, no new errors in the log. |
| 12 | Open `wiki/index.html`: the hero stats match, Systems shows Cauldron Brewing and Petrification, Items shows the seven potions and Brewing Ingredients, Blocks shows Dittany Plant, Mobs shows Petrified Remains. |

- [ ] **Step 8: Report** the build result, the test count, the boot line and anything that did not match.

---

## Self-Review

**Spec coverage (Phase 3):**
- "Brew your first potion" (child of `cauldron_class`): Task 2 (`first_brew.json`, parent `cauldron_class`, code grant at bottling, ruling 8).
- "Ruin a brew": Task 2 (`ruined_brew.json`, grant in `ruin` when a player caused it, ruling 9).
- "Liquid Luck" (Felix): Task 2 (`liquid_luck.json`, `inventory_changed`, ruling 10).
- Potions classroom in Mini Hogwarts with working cauldrons: Tasks 3 (schematic: room, heated cauldrons, shelves, lectern, chests) and 4 (chest contents), Task 1 (what the chests hold); old worlds in ruling 6; the spec's "may move to the Mini Hogwarts plan" is resolved: there is no such plan, so it ships here.
- `MOD_FEATURES_WIKI.md` and `wiki/`: Tasks 5 and 6 cover the seven potions (Task 5 Step 7, Task 6 Step 4; Polyjuice and the Wizard Potion entries already exist and are correct), the ingredients (Task 5 Step 6, Task 6 Step 4), dittany (Task 5 Steps 3 and 10, Task 6 Step 3), petrification and statues (Task 5 Steps 1, 8 and 9, Task 6 Steps 1 and 2; the Basilisk and Petrified Remains entries were rewritten by the Phase 2 fix wave), the give-in choice (Task 5 Step 9, Task 6 Step 1), the stirring rod, the classroom and the advancements.

**Placeholder scan:** no TBD, no "similar to Task N". Every edit quotes its anchor and its full replacement; the script and both Java classes are complete.

**Signature consistency:** `ClassroomSupplies.roll(IntBinaryOperator, double)` and `Stack(item, count)` are the same in Task 1 and Task 4; `BrewAdvancements.grant(Player, String)`, `FIRST_BREW`, `RUINED_BREW` are used only as defined; advancement ids match their file names, their lang keys and the parents (`liquid_luck` names `hp/first_brew`). The classroom chests the script writes (`minecraft:chest`, two) are what `analyze` records. The script was run against the current schematic while planning: dry run and write both produce the output quoted in Task 3, a second run is a no-op, the independent reader (`schem_to_nbt.py`) decodes the result, and the flat render shows the layout described in ruling 2.
