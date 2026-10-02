# Spell Mastery Phase 1b (Tomes of Mastery) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four rare loot-only books (Common, Uncommon, Rare, Epic Tome of Mastery) that each raise one learned spell of their tier or below by exactly one rank, chosen from a picker, with the same feedback as a natural rank-up.

**Architecture:** The pure rules (`TomeRules`: which tier can raise which spell, the XP a tome jumps to, which tome a chest roll gives) have no Minecraft types beyond the plain `SpellLootRarity` enum and are unit-tested with JUnit. `TomeOfMasteryItem` (one class, four registered items) computes the eligible spells on the server and sends them to the client in `OpenTomePickerPacket`; `ClientRuntimeBridge` opens `TomePickerScreen`, which sends `UseTomePacket` back. The server re-validates everything in `TomeOfMasteryItem.tryRaise`, sets the spell's XP to the start of the next rank through `SpellMasteryData.setXp`, consumes the tome and calls the now-public `MasteryAwards.celebrate`. A new global loot modifier, `TomeOfMasteryLootModifier`, rolls at most one tome per chest from four config chances, with Rare limited to dungeon-tier chests and mod structures and Epic limited to endgame loot.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already set up in `build.gradle`), Python 3 + Pillow 10 for the textures.

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 1b: Tomes of Mastery". Phase 1 (`docs/superpowers/plans/2026-10-02-spell-mastery-phase1.md`) is already on this branch.

## Global Constraints

- Tiers are `SpellLootRarity` (`COMMON < UNCOMMON < RARE < EPIC`, by ordinal). A tome raises a spell only if `spell.getLootRarity().ordinal() <= tome tier ordinal`.
- **Exactly one rank per tome** (I→II, II→III, III→IV), in every game mode, all the way to Master. The spell's XP is **set** to the cumulative threshold of the next rank (`Config.getMasteryThresholds()[currentRank]`), never added to.
- **Server authoritative.** The picker is only a menu. The server checks: the stack in the named hand is a Tome of Mastery, the spell id is a real spell (strict lookup, no `SpellType.fromId` fallback), the spell is learned, the tome's tier allows it, and it is not already Master. Spectators can't use tomes.
- A successful tome use gives the same chat line, chime and particles as a natural rank-up (`MasteryAwards.celebrate`, made public, not duplicated). The +1 max mana follows on its own: `PlayerMagicData.getMaxMana` already counts ranks and the player tick resends a changed max mana.
- Chest loot only, no recipe. Default chances per eligible chest: Common 3%, Uncommon 1.5%, Rare 0.5%, Epic 0.15%, all config values. At most one tome per chest.
- **Common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`.** Screens live in `client/`, are opened through `ClientSideBridge.handlePacket` → `ClientRuntimeBridge` string dispatch, and send a C2S packet the server validates.
- **No `switch` on an enum in client or render code** (the synthetic `$1` switch-map class fails to load in this dev env). Use `if` chains. This plan uses `if` chains in common code too.
- **Custom screens use `renderTransparentBackground`, never the blur backdrop.** Override `renderBlurredBackground` and `renderBackground` as no-ops, as the Grimoire does for the former.
- **Never run gradle while the Minecraft client is running.**
- Entity event bytes 21/35/63 crash the client (this plan broadcasts no entity events).
- Commit after each task. Each commit message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command below does that). **Never push**; the user pushes.
- Stage only the files named in each task. **Never stage `.claude/settings.local.json`.**
- Item textures are 16x16 RGBA PNGs.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/mastery/TomeRules.java` | Pure: tier check, eligibility, XP after a tome, chest roll to tome tier |
| Create `src/test/java/com/hogwartsmagic/spell/mastery/TomeRulesTest.java` | Unit tests |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | Four tome chest chances |
| Create `src/main/java/com/hogwartsmagic/item/TomeOfMasteryItem.java` | The item: name colour, tooltip, open-picker on use, server-side `tryRaise` |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | `celebrate` becomes public |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `OpenTomePickerPacket` (S2C), `UseTomePacket` (C2S) |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Register four items, creative tab entries, loot modifier codec |
| Create `src/main/java/com/hogwartsmagic/client/TomePickerScreen.java` | The picker |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | `"openTomePicker"` dispatch |
| Create `build_scripts/generate_tome_textures.py` | Paints the four tier textures |
| Create `src/main/resources/assets/hogwartsmagic/textures/item/{common,uncommon,rare,epic}_tome_of_mastery.png` | Generated textures |
| Create `src/main/resources/assets/hogwartsmagic/models/item/{common,uncommon,rare,epic}_tome_of_mastery.json` | Item models |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Names, tooltips, picker and error strings |
| Create `src/main/java/com/hogwartsmagic/loot/TomeOfMasteryLootModifier.java` | Chest injection |
| Create `src/main/resources/data/hogwartsmagic/loot_modifiers/tomes_of_mastery_in_chests.json` | Modifier instance |
| Modify `src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json` | Enable it |
| Create `src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_endgame.json` | Structures whose chests can roll Epic (Horntail nest, the Chamber) |
| Create `src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_rare.json` | Mod structures whose chests can roll Rare |

---

### Task 1: Pure tome rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/TomeRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mastery/TomeRulesTest.java`

**Interfaces:**
- Consumes: `MasteryMath.rankForXp(float, int[])`, `MasteryMath.MAX_RANK`, `SpellLootRarity` (plain enum, no Minecraft imports, safe in JUnit)
- Produces:
  - `static boolean TomeRules.canRaise(SpellLootRarity tomeTier, SpellLootRarity spellTier)`
  - `static boolean TomeRules.isEligible(SpellLootRarity tomeTier, SpellLootRarity spellTier, boolean learned, int rank)`
  - `static float TomeRules.xpForNextRank(float xp, int[] thresholds)`: the next rank's threshold, `-1` at Master
  - `static SpellLootRarity TomeRules.pickTome(double roll, double commonChance, double uncommonChance, double rareChance, double epicChance, boolean rareAllowed, boolean epicAllowed)`: `null` when the roll gives no tome

- [ ] **Step 1: Write the failing test**

`src/test/java/com/hogwartsmagic/spell/mastery/TomeRulesTest.java`:
```java
package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.spell.SpellLootRarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomeRulesTest {
    private static final int[] T = {0, 30, 120, 350};
    private static final double C = 0.03;
    private static final double U = 0.015;
    private static final double R = 0.005;
    private static final double E = 0.0015;

    @Test
    void tomeRaisesItsTierAndBelow() {
        assertTrue(TomeRules.canRaise(SpellLootRarity.COMMON, SpellLootRarity.COMMON));
        assertFalse(TomeRules.canRaise(SpellLootRarity.COMMON, SpellLootRarity.UNCOMMON));
        assertTrue(TomeRules.canRaise(SpellLootRarity.UNCOMMON, SpellLootRarity.COMMON));
        assertFalse(TomeRules.canRaise(SpellLootRarity.RARE, SpellLootRarity.EPIC));
        for (SpellLootRarity spell : SpellLootRarity.values()) {
            assertTrue(TomeRules.canRaise(SpellLootRarity.EPIC, spell));
        }
    }

    @Test
    void eligibilityNeedsLearnedTierAndRoomToGrow() {
        assertTrue(TomeRules.isEligible(SpellLootRarity.RARE, SpellLootRarity.RARE, true, 3));
        assertFalse(TomeRules.isEligible(SpellLootRarity.RARE, SpellLootRarity.RARE, false, 1));
        assertFalse(TomeRules.isEligible(SpellLootRarity.EPIC, SpellLootRarity.COMMON, true, 4));
        assertFalse(TomeRules.isEligible(SpellLootRarity.COMMON, SpellLootRarity.RARE, true, 1));
    }

    @Test
    void xpJumpsToTheStartOfTheNextRank() {
        assertEquals(30f, TomeRules.xpForNextRank(0f, T), 1e-4);
        assertEquals(30f, TomeRules.xpForNextRank(29.9f, T), 1e-4);
        assertEquals(120f, TomeRules.xpForNextRank(30f, T), 1e-4);
        assertEquals(350f, TomeRules.xpForNextRank(200f, T), 1e-4);
        assertEquals(-1f, TomeRules.xpForNextRank(350f, T), 1e-4);
        assertEquals(-1f, TomeRules.xpForNextRank(9999f, T), 1e-4);
    }

    @Test
    void xpForNextRankAlwaysLandsExactlyOneRankHigher() {
        for (float xp : new float[]{0f, 15f, 30f, 100f, 120f, 349.9f}) {
            int before = MasteryMath.rankForXp(xp, T);
            int after = MasteryMath.rankForXp(TomeRules.xpForNextRank(xp, T), T);
            assertEquals(before + 1, after);
        }
    }

    @Test
    void endgameChestWalksEpicRareUncommonCommon() {
        assertEquals(SpellLootRarity.EPIC, TomeRules.pickTome(0.0, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.EPIC, TomeRules.pickTome(0.0014, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.RARE, TomeRules.pickTome(0.0016, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.007, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.COMMON, TomeRules.pickTome(0.03, C, U, R, E, true, true));
        assertNull(TomeRules.pickTome(0.06, C, U, R, E, true, true));
    }

    @Test
    void ordinaryChestNeverRollsRareOrEpic() {
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.0, C, U, R, E, false, false));
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.0149, C, U, R, E, false, false));
        assertEquals(SpellLootRarity.COMMON, TomeRules.pickTome(0.015, C, U, R, E, false, false));
        assertEquals(SpellLootRarity.COMMON, TomeRules.pickTome(0.0449, C, U, R, E, false, false));
        assertNull(TomeRules.pickTome(0.045, C, U, R, E, false, false));
    }

    @Test
    void dungeonChestCanRollRareButNotEpic() {
        assertEquals(SpellLootRarity.RARE, TomeRules.pickTome(0.0, C, U, R, E, true, false));
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.005, C, U, R, E, true, false));
    }

    @Test
    void zeroChancesGiveNothing() {
        assertNull(TomeRules.pickTome(0.0, 0, 0, 0, 0, true, true));
        assertNull(TomeRules.pickTome(0.0, -1, -1, -1, -1, true, true));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run (client closed): `./gradlew test --tests com.hogwartsmagic.spell.mastery.TomeRulesTest`
Expected: compile FAIL, `cannot find symbol: class TomeRules`.

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/mastery/TomeRules.java`:
```java
package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.spell.SpellLootRarity;

/**
 * The Tome of Mastery rules, kept free of Minecraft types so they are unit-tested directly.
 * A tome raises spells of its own tier or below, by exactly one rank, and a chest gives at most
 * one tome.
 */
public final class TomeRules {
    private TomeRules() {
    }

    /** Tiers compare by ordinal: COMMON < UNCOMMON < RARE < EPIC. */
    public static boolean canRaise(SpellLootRarity tomeTier, SpellLootRarity spellTier) {
        return spellTier.ordinal() <= tomeTier.ordinal();
    }

    public static boolean isEligible(SpellLootRarity tomeTier, SpellLootRarity spellTier, boolean learned, int rank) {
        return learned && rank < MasteryMath.MAX_RANK && canRaise(tomeTier, spellTier);
    }

    /**
     * The XP a tome sets the spell to: the start of the next rank, so leftover progress inside
     * the current rank isn't carried over and doubled. -1 once the spell is Master.
     */
    public static float xpForNextRank(float xp, int[] thresholds) {
        int rank = MasteryMath.rankForXp(xp, thresholds);
        if (rank >= MasteryMath.MAX_RANK) {
            return -1f;
        }
        return thresholds[rank];
    }

    /**
     * One roll per chest, so each tier's chance is exactly its config value and a chest never
     * gets two tomes. Rarest first: [0, epic) is Epic, the next band Rare, then Uncommon, then
     * Common. Disallowed tiers take no band. Returns null when the roll lands past every band.
     */
    public static SpellLootRarity pickTome(double roll, double commonChance, double uncommonChance,
                                           double rareChance, double epicChance,
                                           boolean rareAllowed, boolean epicAllowed) {
        double edge = 0.0;
        if (epicAllowed) {
            edge += Math.max(0.0, epicChance);
            if (roll < edge) {
                return SpellLootRarity.EPIC;
            }
        }
        if (rareAllowed) {
            edge += Math.max(0.0, rareChance);
            if (roll < edge) {
                return SpellLootRarity.RARE;
            }
        }
        edge += Math.max(0.0, uncommonChance);
        if (roll < edge) {
            return SpellLootRarity.UNCOMMON;
        }
        edge += Math.max(0.0, commonChance);
        if (roll < edge) {
            return SpellLootRarity.COMMON;
        }
        return null;
    }
}
```

- [ ] **Step 4: Run it and confirm it passes**

Run: `./gradlew test --tests com.hogwartsmagic.spell.mastery.TomeRulesTest`
Expected: PASS (8 tests). Then `./gradlew test`: all four test classes pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mastery/TomeRules.java src/test/java/com/hogwartsmagic/spell/mastery/TomeRulesTest.java
git commit -m "Add the rules for which spells a Tome of Mastery can raise" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Tome chest chances in the config

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/Config.java` (defines just above `static final ForgeConfigSpec SPEC = BUILDER.build();`, line ~130; fields after `public static double masteryManaCap = 50.0;`, line ~158; loads at the end of `onLoad`, after `masteryManaCap = MASTERY_MANA_CAP.get();`; getters after `getMasteryManaCap()`)

**Interfaces:**
- Produces: `Config.getTomeCommonChance()`, `Config.getTomeUncommonChance()`, `Config.getTomeRareChance()`, `Config.getTomeEpicChance()`, all `double`

- [ ] **Step 1: Add the definitions** (directly above `static final ForgeConfigSpec SPEC = BUILDER.build();`)
```java
    public static final ForgeConfigSpec.DoubleValue TOME_COMMON_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) that a chest which can roll a spell book also holds a Common Tome.")
            .defineInRange("tomeCommonChestChance", 0.03, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue TOME_UNCOMMON_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) that a chest which can roll a spell book holds an Uncommon Tome.")
            .defineInRange("tomeUncommonChestChance", 0.015, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue TOME_RARE_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) of a Rare Tome in a dungeon-tier chest or a mod structure chest.")
            .defineInRange("tomeRareChestChance", 0.005, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue TOME_EPIC_CHEST_CHANCE = BUILDER
            .comment("Tomes of Mastery: chance (0-1) of an Epic Tome in endgame chests only (Gringotts vaults, the Horntail nest, the Chamber). A chest holds at most one tome; keep the four chances summing below 1.")
            .defineInRange("tomeEpicChestChance", 0.0015, 0.0, 1.0);
```

- [ ] **Step 2: Add the static fields** (after `public static double masteryManaCap = 50.0;`)
```java
    public static double tomeCommonChestChance = 0.03;
    public static double tomeUncommonChestChance = 0.015;
    public static double tomeRareChestChance = 0.005;
    public static double tomeEpicChestChance = 0.0015;
```

- [ ] **Step 3: Load them** (at the end of `onLoad`, after `masteryManaCap = MASTERY_MANA_CAP.get();`)
```java
        tomeCommonChestChance = TOME_COMMON_CHEST_CHANCE.get();
        tomeUncommonChestChance = TOME_UNCOMMON_CHEST_CHANCE.get();
        tomeRareChestChance = TOME_RARE_CHEST_CHANCE.get();
        tomeEpicChestChance = TOME_EPIC_CHEST_CHANCE.get();
```

- [ ] **Step 4: Add the getters** (after `getMasteryManaCap()`)
```java
    public static double getTomeCommonChance() {
        return tomeCommonChestChance;
    }

    public static double getTomeUncommonChance() {
        return tomeUncommonChestChance;
    }

    public static double getTomeRareChance() {
        return tomeRareChestChance;
    }

    public static double getTomeEpicChance() {
        return tomeEpicChestChance;
    }
```

- [ ] **Step 5: Build**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/Config.java
git commit -m "Make Tome of Mastery chest chances configurable" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The tome item, its packets and the server-side rank-up

**Files:**
- Create: `src/main/java/com/hogwartsmagic/item/TomeOfMasteryItem.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` (`celebrate` visibility)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (two builders at the end of `register()`, after the `CancelApparitionPacket` builder; two records at the end of the class, after `CancelApparitionPacket`)
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (items after `MAGIC_BOOK`, line ~573; creative tab after `output.accept(MERCHIEFTAINESS_CROWN.get());`, line ~788)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `TomeRules.isEligible/xpForNextRank`, `SpellMasteryData.getXp/getRank/setXp`, `MagicBookItem.isSpellLearned(Player, SpellType)`, `Config.getMasteryThresholds()`, `SpellType.values()/getId()/getLootRarity()`
- Produces:
  - `public static void MasteryAwards.celebrate(ServerPlayer caster, SpellType spell, int newRank)`
  - `new TomeOfMasteryItem(SpellLootRarity tier, Item.Properties properties)`, `SpellLootRarity getTier()`
  - `static Rarity TomeOfMasteryItem.displayRarity(SpellLootRarity)`
  - `static String TomeOfMasteryItem.registryName(SpellLootRarity)` → `"common_tome_of_mastery"` etc.
  - `static ItemStack TomeOfMasteryItem.stackFor(SpellLootRarity)`
  - `static List<SpellType> TomeOfMasteryItem.eligibleSpells(Player, SpellLootRarity)` (safe on both sides)
  - `static boolean TomeOfMasteryItem.tryRaise(ServerPlayer, boolean mainHand, String spellId)` (server only)
  - `SpellPackets.OpenTomePickerPacket(boolean mainHand, int tierOrdinal, List<String> spellIds)` (S2C, dispatched as `"openTomePicker"`)
  - `SpellPackets.UseTomePacket(boolean mainHand, String spellId)` (C2S)
  - `ExampleMod.COMMON_TOME_OF_MASTERY`, `UNCOMMON_TOME_OF_MASTERY`, `RARE_TOME_OF_MASTERY`, `EPIC_TOME_OF_MASTERY` (`RegistryObject<Item>`)

- [ ] **Step 1: Make the rank-up feedback reusable.** In `MasteryAwards.java`, replace:
```java
    private static void celebrate(ServerPlayer caster, SpellType spell, int newRank) {
```
with:
```java
    /** The rank-up moment: gold chat line, level-up chime, enchant and spell-coloured particles. Also used by Tomes of Mastery. */
    public static void celebrate(ServerPlayer caster, SpellType spell, int newRank) {
```
The body stays as it is.

- [ ] **Step 2: Create the item**

`src/main/java/com/hogwartsmagic/item/TomeOfMasteryItem.java`:
```java
package com.hogwartsmagic.item;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.spell.mastery.TomeRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A Tome of Mastery: raises one learned spell of this tome's tier or below by exactly one rank.
 * Right-click asks the server for the eligible spells, which opens the client picker; the
 * picker's choice comes back as {@link SpellPackets.UseTomePacket} and {@link #tryRaise}
 * re-checks everything before touching anything. One class, four registered items.
 */
public class TomeOfMasteryItem extends Item {
    private final SpellLootRarity tier;

    public TomeOfMasteryItem(SpellLootRarity tier, Properties properties) {
        super(properties.rarity(displayRarity(tier)));
        this.tier = tier;
    }

    public SpellLootRarity getTier() {
        return tier;
    }

    /** Vanilla rarity colours the name: white, yellow, aqua, light purple. */
    public static Rarity displayRarity(SpellLootRarity tier) {
        if (tier == SpellLootRarity.UNCOMMON) {
            return Rarity.UNCOMMON;
        }
        if (tier == SpellLootRarity.RARE) {
            return Rarity.RARE;
        }
        if (tier == SpellLootRarity.EPIC) {
            return Rarity.EPIC;
        }
        return Rarity.COMMON;
    }

    /** Registry path, e.g. {@code common_tome_of_mastery}; the lang key is {@code item.hogwartsmagic.<path>}. */
    public static String registryName(SpellLootRarity tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "_tome_of_mastery";
    }

    public static ItemStack stackFor(SpellLootRarity tier) {
        if (tier == SpellLootRarity.UNCOMMON) {
            return new ItemStack(ExampleMod.UNCOMMON_TOME_OF_MASTERY.get());
        }
        if (tier == SpellLootRarity.RARE) {
            return new ItemStack(ExampleMod.RARE_TOME_OF_MASTERY.get());
        }
        if (tier == SpellLootRarity.EPIC) {
            return new ItemStack(ExampleMod.EPIC_TOME_OF_MASTERY.get());
        }
        return new ItemStack(ExampleMod.COMMON_TOME_OF_MASTERY.get());
    }

    /** Learned, non-Master spells this tier can raise, in SpellType order. Reads only synced data. */
    public static List<SpellType> eligibleSpells(Player player, SpellLootRarity tier) {
        List<SpellType> eligible = new ArrayList<>();
        for (SpellType spell : SpellType.values()) {
            if (TomeRules.isEligible(tier, spell.getLootRarity(),
                    MagicBookItem.isSpellLearned(player, spell), SpellMasteryData.getRank(player, spell))) {
                eligible.add(spell);
            }
        }
        return eligible;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
            List<SpellType> eligible = eligibleSpells(serverPlayer, tier);
            if (eligible.isEmpty()) {
                serverPlayer.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".tome.nothing_to_raise")
                        .withStyle(ChatFormatting.RED), true);
            } else {
                List<String> ids = new ArrayList<>(eligible.size());
                for (SpellType spell : eligible) {
                    ids.add(spell.getId());
                }
                SpellPackets.CHANNEL.send(new SpellPackets.OpenTomePickerPacket(hand == InteractionHand.MAIN_HAND, tier.ordinal(), ids),
                        PacketDistributor.PLAYER.with(serverPlayer));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Server side of a picker click. Nothing from the client is trusted: the hand must still hold
     * a tome, the id must be a real spell, and the spell must be learned, allowed by this tome's
     * tier and below Master. On success the XP is set to the start of the next rank (exactly one
     * rank), the tome is consumed and the normal rank-up feedback plays.
     */
    public static boolean tryRaise(ServerPlayer player, boolean mainHand, String spellId) {
        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }
        ItemStack stack = player.getItemInHand(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(stack.getItem() instanceof TomeOfMasteryItem tome)) {
            return false;
        }
        SpellType spell = strictSpell(spellId);
        if (spell == null) {
            return false;
        }
        boolean learned = MagicBookItem.isSpellLearned(player, spell);
        int rank = SpellMasteryData.getRank(player, spell);
        if (!TomeRules.isEligible(tome.getTier(), spell.getLootRarity(), learned, rank)) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".tome.not_eligible")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float nextXp = TomeRules.xpForNextRank(SpellMasteryData.getXp(player, spell), Config.getMasteryThresholds());
        if (nextXp < 0f) {
            return false;
        }
        SpellMasteryData.setXp(player, spell, nextXp);
        stack.shrink(1);
        MasteryAwards.celebrate(player, spell, SpellMasteryData.getRank(player, spell));
        return true;
    }

    /** SpellType.fromId falls back to Wingardium Leviosa on an unknown id; a packet must not. */
    private static SpellType strictSpell(String id) {
        if (id == null) {
            return null;
        }
        for (SpellType spell : SpellType.values()) {
            if (spell.getId().equals(id)) {
                return spell;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item." + ExampleMod.MODID + ".tome_of_mastery.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item." + ExampleMod.MODID + ".tome_of_mastery.works_on."
                + tier.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.DARK_PURPLE));
    }
}
```
`SpellMasteryData.setXp` already syncs the new XP to the client, so the Grimoire and wheel update at once.

- [ ] **Step 3: Add the packet records** at the end of `SpellPackets` (after the `CancelApparitionPacket` record, before the class's closing brace):
```java
    /** Server to client: open the Tome of Mastery picker with the spells the server says are eligible. */
    public record OpenTomePickerPacket(boolean mainHand, int tierOrdinal, List<String> spellIds) {
        public static void handle(OpenTomePickerPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("openTomePicker", p));
        }
    }

    /** Client to server: the player picked a spell in the tome picker. Fully re-validated in TomeOfMasteryItem.tryRaise. */
    public record UseTomePacket(boolean mainHand, String spellId) {
        public static void handle(UseTomePacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.item.TomeOfMasteryItem.tryRaise(player, p.mainHand, p.spellId);
            }
        }
    }
```

- [ ] **Step 4: Register them** at the end of `register()`, directly after the `CancelApparitionPacket` builder's `.add();`:
```java
        CHANNEL.messageBuilder(OpenTomePickerPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBoolean(p.mainHand);
                    buf.writeVarInt(p.tierOrdinal);
                    buf.writeVarInt(p.spellIds.size());
                    for (String id : p.spellIds) {
                        buf.writeUtf(id, 64);
                    }
                })
                .decoder(buf -> {
                    boolean mainHand = buf.readBoolean();
                    int tierOrdinal = buf.readVarInt();
                    int size = clampDecodedSize(buf.readVarInt(), SpellType.values().length);
                    List<String> ids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        ids.add(buf.readUtf(64));
                    }
                    return new OpenTomePickerPacket(mainHand, tierOrdinal, ids);
                })
                .consumerMainThread(OpenTomePickerPacket::handle)
                .add();
        CHANNEL.messageBuilder(UseTomePacket.class)
                .encoder((p, buf) -> {
                    buf.writeBoolean(p.mainHand);
                    buf.writeUtf(p.spellId, 64);
                })
                .decoder(buf -> new UseTomePacket(buf.readBoolean(), buf.readUtf(64)))
                .consumerMainThread(UseTomePacket::handle)
                .add();
```
`List`, `ArrayList`, `ServerPlayer`, `SpellType` and `CustomPayloadEvent` are already imported in `SpellPackets`. The server never sends more ids than there are spells, so the clamp only guards against a malformed packet.

- [ ] **Step 5: Register the items.** In `ExampleMod.java`, directly after the `MAGIC_BOOK` registration (`ITEMS.register("magic_book", ...)`), add:
```java
    // Tomes of Mastery: loot-only, one rank per tome, tier-gated (see TomeOfMasteryItem)
    public static final RegistryObject<Item> COMMON_TOME_OF_MASTERY = ITEMS.register("common_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.COMMON, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> UNCOMMON_TOME_OF_MASTERY = ITEMS.register("uncommon_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.UNCOMMON, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RARE_TOME_OF_MASTERY = ITEMS.register("rare_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.RARE, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> EPIC_TOME_OF_MASTERY = ITEMS.register("epic_tome_of_mastery",
            () -> new com.hogwartsmagic.item.TomeOfMasteryItem(com.hogwartsmagic.spell.SpellLootRarity.EPIC, new Item.Properties().stacksTo(16)));
```
In the `MAIN_TAB` `displayItems` lambda, directly after `output.accept(MERCHIEFTAINESS_CROWN.get()); // Merchieftainess's Crown`, add:
```java
                output.accept(COMMON_TOME_OF_MASTERY.get());
                output.accept(UNCOMMON_TOME_OF_MASTERY.get());
                output.accept(RARE_TOME_OF_MASTERY.get());
                output.accept(EPIC_TOME_OF_MASTERY.get());
```

- [ ] **Step 6: Add the strings** to `en_us.json`, directly after `"gui.hogwartsmagic.mastery.tooltip": ...,` (keep the trailing commas valid):
```json
  "item.hogwartsmagic.common_tome_of_mastery": "Common Tome of Mastery",
  "item.hogwartsmagic.uncommon_tome_of_mastery": "Uncommon Tome of Mastery",
  "item.hogwartsmagic.rare_tome_of_mastery": "Rare Tome of Mastery",
  "item.hogwartsmagic.epic_tome_of_mastery": "Epic Tome of Mastery",
  "item.hogwartsmagic.tome_of_mastery.tooltip": "Right-click to raise one learned spell by one rank.",
  "item.hogwartsmagic.tome_of_mastery.works_on.common": "Works on Novice-tier spells",
  "item.hogwartsmagic.tome_of_mastery.works_on.uncommon": "Works on Novice and Apprentice-tier spells",
  "item.hogwartsmagic.tome_of_mastery.works_on.rare": "Works on Novice, Apprentice and Adept-tier spells",
  "item.hogwartsmagic.tome_of_mastery.works_on.epic": "Works on any spell",
  "gui.hogwartsmagic.tome.nothing_to_raise": "None of your spells can be raised by this tome.",
  "gui.hogwartsmagic.tome.not_eligible": "This tome cannot raise that spell.",
```
The tier words are the Grimoire's own tier headers (`gui.hogwartsmagic.grimoire.tier.*`: Novice = Common, Apprentice = Uncommon, Adept = Rare, Master = Epic), since that's the only place players see a spell's tier.

- [ ] **Step 7: Validate the JSON, build and boot a dedicated server**

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); print('lang ok')"`
Expected: `lang ok`.

Run: `./gradlew compileJava test`, then `./gradlew runServer` until the console prints `Done (`, then type `stop`.
Expected: `BUILD SUCCESSFUL`, all tests pass, and `run/logs/latest.log` has no exception mentioning `TomeOfMastery`, `SpellPackets` or any `client` class. Right-clicking a tome in-game before Task 4 logs `Unknown client packet action: openTomePicker` on the client; Task 4 adds that case, so don't play-test until then.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/item/TomeOfMasteryItem.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add Tomes of Mastery and the server-checked one-rank raise" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The tome picker screen

**Files:**
- Create: `src/main/java/com/hogwartsmagic/client/TomePickerScreen.java`
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` (a `case` after `"closeApparitionMap"`; a method after `openApparitionMap`, line ~203)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SpellPackets.OpenTomePickerPacket` (`mainHand()`, `tierOrdinal()`, `spellIds()`), `SpellPackets.UseTomePacket(boolean, String)`, `TomeOfMasteryItem.registryName(SpellLootRarity)`, `SpellMasteryData.getRank(Player, SpellType)` (synced NBT), `MasteryMath.roman/NOVICE/MAX_RANK`
- Produces: `new TomePickerScreen(boolean mainHand, SpellLootRarity tier, List<SpellType> spells)`

- [ ] **Step 1: Create the screen**

`src/main/java/com/hogwartsmagic/client/TomePickerScreen.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.TomeOfMasteryItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * The Tome of Mastery picker: the spells the server said this tome can raise, each with its
 * rank step. It is only a menu; a click sends {@link SpellPackets.UseTomePacket} and the server
 * re-checks everything. Same look as the Grimoire (dark panel, thin border, no blur).
 *
 * <p>No {@code switch} on enums in here: the synthetic switch-map class has been seen to fail
 * to load in this mod's dev client, so the tier colour is an if-chain.
 */
public class TomePickerScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 196;
    private static final int ROW_HEIGHT = 14;
    private static final int COLOR_BODY = 0xFFD8C9F0;
    private static final int COLOR_NAME = 0xFFF2DFA8;
    private static final int COLOR_RANK = 0xFFFFE08A;
    private static final int COLOR_HOVER = 0x40D4AF37;

    private final boolean mainHand;
    private final SpellLootRarity tier;
    private final List<SpellType> spells;

    private int panelLeft;
    private int panelTop;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private float scroll;
    private boolean chosen;

    public TomePickerScreen(boolean mainHand, SpellLootRarity tier, List<SpellType> spells) {
        super(Component.translatable("item." + ExampleMod.MODID + "." + TomeOfMasteryItem.registryName(tier)));
        this.mainHand = mainHand;
        this.tier = tier;
        this.spells = List.copyOf(spells);
    }

    /** The tome's name colour (vanilla rarity): white, yellow, aqua, light purple. */
    private static int tierColor(SpellLootRarity tier) {
        if (tier == SpellLootRarity.UNCOMMON) {
            return 0xFFFFFF55;
        }
        if (tier == SpellLootRarity.RARE) {
            return 0xFF55FFFF;
        }
        if (tier == SpellLootRarity.EPIC) {
            return 0xFFFF55FF;
        }
        return 0xFFFFFFFF;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
        listLeft = panelLeft + 10;
        listRight = panelLeft + PANEL_WIDTH - 10;
        listTop = panelTop + 34;
        listBottom = panelTop + PANEL_HEIGHT - 32;
        scroll = clampScroll(scroll);
        addRenderableWidget(Button.builder(
                        Component.translatable("gui." + ExampleMod.MODID + ".close"),
                        b -> onClose())
                .bounds(panelLeft + (PANEL_WIDTH - 60) / 2, panelTop + PANEL_HEIGHT - 26, 60, 18)
                .build());
    }

    private float maxScroll() {
        return Math.max(0.0F, spells.size() * ROW_HEIGHT - (float) (listBottom - listTop));
    }

    private float clampScroll(float value) {
        return Math.max(0.0F, Math.min(value, maxScroll()));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) {
            return -1;
        }
        int index = (int) ((mouseY - listTop + scroll) / ROW_HEIGHT);
        return index >= 0 && index < spells.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = clampScroll(scroll - (float) deltaY * ROW_HEIGHT * 2.0F);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !chosen) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                chosen = true;
                SpellPackets.CHANNEL.send(new SpellPackets.UseTomePacket(mainHand, spells.get(index).getId()),
                        PacketDistributor.SERVER.noArg());
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Hard no-op: never run the menu-blur post effect for this screen.
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op so super.render() can't draw the blurred/menu backdrop over the panel.
        // render() draws the plain dim itself with renderTransparentBackground.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);

        int right = panelLeft + PANEL_WIDTH;
        int bottom = panelTop + PANEL_HEIGHT;
        int accent = tierColor(tier);
        g.fill(panelLeft - 4, panelTop - 4, right + 4, bottom + 4, 0xF0140A1E);
        drawBorder(g, panelLeft - 4, panelTop - 4, right + 4, bottom + 4, accent);

        int centerX = panelLeft + PANEL_WIDTH / 2;
        g.drawCenteredString(this.font, this.title, centerX, panelTop + 6, accent);
        g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".tome.choose"),
                centerX, panelTop + 19, COLOR_BODY);

        if (spells.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".tome.empty"),
                    centerX, listTop + 8, COLOR_BODY);
        }

        int hovered = rowAt(mouseX, mouseY);
        g.enableScissor(listLeft, listTop, listRight, listBottom);
        int y = (int) (listTop - scroll);
        for (int i = 0; i < spells.size(); i++) {
            if (y + ROW_HEIGHT >= listTop && y <= listBottom) {
                SpellType spell = spells.get(i);
                if (i == hovered) {
                    g.fill(listLeft, y, listRight, y + ROW_HEIGHT, COLOR_HOVER);
                }
                g.drawString(this.font, Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId()),
                        listLeft + 4, y + 3, COLOR_NAME);
                String step = rankStep(spell);
                g.drawString(this.font, step, listRight - 4 - this.font.width(step), y + 3, COLOR_RANK);
            }
            y += ROW_HEIGHT;
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** "II -> III" from the synced rank; the server decides the real outcome. */
    private String rankStep(SpellType spell) {
        int rank = this.minecraft != null && this.minecraft.player != null
                ? SpellMasteryData.getRank(this.minecraft.player, spell)
                : MasteryMath.NOVICE;
        int next = Math.min(MasteryMath.MAX_RANK, rank + 1);
        return MasteryMath.roman(rank) + " → " + MasteryMath.roman(next);
    }

    private void drawBorder(GuiGraphics g, int left, int top, int right, int bottom, int color) {
        g.fill(left, top, right, top + 1, color);
        g.fill(left, bottom - 1, right, bottom, color);
        g.fill(left, top, left + 1, bottom, color);
        g.fill(right - 1, top, right, bottom, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: Dispatch it.** In `ClientRuntimeBridge.handlePacket`'s `switch (action)` (a `String` switch, not an enum, so it's allowed), add directly after the `"closeApparitionMap"` case:
```java
            case "openTomePicker" -> openTomePicker((SpellPackets.OpenTomePickerPacket) payload);
```
Add this method directly after `openApparitionMap`:
```java
    private static void openTomePicker(SpellPackets.OpenTomePickerPacket packet) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        com.hogwartsmagic.spell.SpellLootRarity[] tiers = com.hogwartsmagic.spell.SpellLootRarity.values();
        if (packet.tierOrdinal() < 0 || packet.tierOrdinal() >= tiers.length) {
            return;
        }
        List<com.hogwartsmagic.spell.SpellType> spells = new ArrayList<>();
        for (String id : packet.spellIds()) {
            for (com.hogwartsmagic.spell.SpellType spell : com.hogwartsmagic.spell.SpellType.values()) {
                if (spell.getId().equals(id)) {
                    spells.add(spell);
                    break;
                }
            }
        }
        minecraft.setScreen(new TomePickerScreen(packet.mainHand(), tiers[packet.tierOrdinal()], spells));
    }
```
`List` and `ArrayList` are already imported in `ClientRuntimeBridge`.

- [ ] **Step 3: Strings** (`en_us.json`, directly after `"gui.hogwartsmagic.tome.not_eligible": ...,`):
```json
  "gui.hogwartsmagic.tome.choose": "Choose a spell to raise by one rank",
  "gui.hogwartsmagic.tome.empty": "Nothing left to raise.",
```

- [ ] **Step 4: Validate, build and boot a dedicated server**

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); print('lang ok')"`
Expected: `lang ok`.

Run: `./gradlew compileJava test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: `BUILD SUCCESSFUL`; no `ClassNotFoundException`/`NoClassDefFoundError` for `TomePickerScreen`, `Screen` or `GuiGraphics` in `run/logs/latest.log`. `TomePickerScreen` is only reached through the reflective `ClientSideBridge`, which returns early on a dedicated server, so the boot proves it never loads there.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/client/TomePickerScreen.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add the Tome of Mastery spell picker" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Tome textures and item models

**Files:**
- Create: `build_scripts/generate_tome_textures.py`
- Create: `src/main/resources/assets/hogwartsmagic/textures/item/common_tome_of_mastery.png`, `uncommon_tome_of_mastery.png`, `rare_tome_of_mastery.png`, `epic_tome_of_mastery.png` (generated)
- Create: `src/main/resources/assets/hogwartsmagic/models/item/common_tome_of_mastery.json`, `uncommon_tome_of_mastery.json`, `rare_tome_of_mastery.json`, `epic_tome_of_mastery.json`

**Interfaces:**
- Consumes: the registry names from Task 3 (`<tier>_tome_of_mastery`)
- Produces: textures `hogwartsmagic:item/<tier>_tome_of_mastery`

- [ ] **Step 1: Create the generator** (Pillow is already used by the other `build_scripts/generate_*_texture.py` scripts)

`build_scripts/generate_tome_textures.py`:
```python
"""Paint the four 16x16 Tome of Mastery item textures.

    python build_scripts/generate_tome_textures.py

Writes assets/hogwartsmagic/textures/item/<tier>_tome_of_mastery.png for common, uncommon, rare
and epic.

WHAT IT DEPICTS
---------------
A closed book seen face-on: spine down the left with two bands, page edges along the bottom, gold
caps on the cover corners and a gold diamond in the middle of the cover. One shared sprite is
painted in each tier's cover colour, so the four read as one family and the tier is legible from
the colour alone. The colours follow the item names (white, yellow, aqua, light purple), which
are vanilla's rarity colours.

STYLE
-----
Flat three-step shading on the cover (highlight on the top and left edge, shade on the right and
bottom) with a darker outline, the way vanilla item sprites are lit from the top left. Every
colour on the cover is derived from the one base colour per tier, so retuning a tier is a
one-line change.
"""

import os

from PIL import Image

SIZE = 16
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "item")

# Legend: . clear, o outline, S spine, b spine band, L cover highlight, M cover, D cover shade,
# g emblem and corner caps, G emblem highlight, p page, P page shadow.
SPRITE = [
    "................",
    ".oooooooooooooo.",
    ".oSgLLLLLLLLLgo.",
    ".oSLMMMMMMMMMDo.",
    ".obLMMMMMMMMMDo.",
    ".oSLMMMMgMMMMDo.",
    ".oSLMMMgGgMMMDo.",
    ".oSLMMgGGGgMMDo.",
    ".oSLMMMgGgMMMDo.",
    ".oSLMMMMgMMMMDo.",
    ".obLMMMMMMMMMDo.",
    ".oSLMMMMMMMMMDo.",
    ".oSgDDDDDDDDDgo.",
    ".oSppppppppppPo.",
    ".oooooooooooooo.",
    "................",
]

GOLD = ((201, 152, 48), (255, 222, 120))
PAGE = (238, 228, 204)
PAGE_SHADOW = (190, 176, 146)

# Cover base colour and (emblem, emblem highlight) per tier. Uncommon swaps the gold emblem for
# cream, because gold on a yellow cover disappears.
TIERS = {
    "common": ((196, 190, 178), GOLD),
    "uncommon": ((214, 170, 40), ((255, 246, 214), (255, 255, 255))),
    "rare": ((46, 170, 184), GOLD),
    "epic": ((160, 80, 206), GOLD),
}


def shade(colour, factor):
    return tuple(max(0, min(255, round(v * factor))) for v in colour)


def lighten(colour, amount=0.35):
    return tuple(min(255, round(v + (255 - v) * amount)) for v in colour)


def palette(cover, emblem):
    dark, light = emblem
    return {
        "o": shade(cover, 0.32),
        "S": shade(cover, 0.58),
        "b": dark,
        "L": lighten(cover),
        "M": cover,
        "D": shade(cover, 0.78),
        "g": dark,
        "G": light,
        "p": PAGE,
        "P": PAGE_SHADOW,
    }


def paint(cover, emblem):
    assert len(SPRITE) == SIZE, "sprite must have 16 rows"
    colours = palette(cover, emblem)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(SPRITE):
        assert len(row) == SIZE, "row %d is %d wide" % (y, len(row))
        for x, key in enumerate(row):
            if key == ".":
                continue
            image.putpixel((x, y), colours[key] + (255,))
    return image


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for tier, (cover, emblem) in TIERS.items():
        path = os.path.normpath(os.path.join(OUT_DIR, tier + "_tome_of_mastery.png"))
        paint(cover, emblem).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Generate and check the textures**

Run: `python build_scripts/generate_tome_textures.py`
Expected: four `wrote ...<tier>_tome_of_mastery.png` lines.

Run: `python -c "from PIL import Image; [print(t, Image.open('src/main/resources/assets/hogwartsmagic/textures/item/%s_tome_of_mastery.png' % t).size) for t in ('common', 'uncommon', 'rare', 'epic')]"`
Expected: each prints `(16, 16)`.

- [ ] **Step 3: Create the four item models.** Each file is the same shape; only the texture name changes.

`src/main/resources/assets/hogwartsmagic/models/item/common_tome_of_mastery.json`:
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hogwartsmagic:item/common_tome_of_mastery"
  }
}
```
`src/main/resources/assets/hogwartsmagic/models/item/uncommon_tome_of_mastery.json`:
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hogwartsmagic:item/uncommon_tome_of_mastery"
  }
}
```
`src/main/resources/assets/hogwartsmagic/models/item/rare_tome_of_mastery.json`:
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hogwartsmagic:item/rare_tome_of_mastery"
  }
}
```
`src/main/resources/assets/hogwartsmagic/models/item/epic_tome_of_mastery.json`:
```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hogwartsmagic:item/epic_tome_of_mastery"
  }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew processResources`
Expected: `BUILD SUCCESSFUL`. (The models are checked visually in Task 7.)

- [ ] **Step 5: Commit**
```bash
git add build_scripts/generate_tome_textures.py src/main/resources/assets/hogwartsmagic/textures/item/common_tome_of_mastery.png src/main/resources/assets/hogwartsmagic/textures/item/uncommon_tome_of_mastery.png src/main/resources/assets/hogwartsmagic/textures/item/rare_tome_of_mastery.png src/main/resources/assets/hogwartsmagic/textures/item/epic_tome_of_mastery.png src/main/resources/assets/hogwartsmagic/models/item/common_tome_of_mastery.json src/main/resources/assets/hogwartsmagic/models/item/uncommon_tome_of_mastery.json src/main/resources/assets/hogwartsmagic/models/item/rare_tome_of_mastery.json src/main/resources/assets/hogwartsmagic/models/item/epic_tome_of_mastery.json
git commit -m "Give each Tome of Mastery tier its own book texture" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Tomes in chest loot

**Files:**
- Create: `src/main/java/com/hogwartsmagic/loot/TomeOfMasteryLootModifier.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (codec registration after `MAGICAL_SILK_LOOT_MODIFIER`, line ~90)
- Create: `src/main/resources/data/hogwartsmagic/loot_modifiers/tomes_of_mastery_in_chests.json`
- Modify: `src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json`
- Create: `src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_endgame.json`
- Create: `src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_rare.json`

**Interfaces:**
- Consumes: `TomeRules.pickTome(...)`, `Config.getTomeCommonChance/getTomeUncommonChance/getTomeRareChance/getTomeEpicChance()`, `TomeOfMasteryItem.stackFor(SpellLootRarity)`, `StructureManager.getStructureWithPieceAt(BlockPos, TagKey<Structure>)` (already used by `MerfolkEvents`)
- Produces: `TomeOfMasteryLootModifier.CODEC`, `ExampleMod.TOME_OF_MASTERY_LOOT_MODIFIER`, loot modifier type `hogwartsmagic:tomes_of_mastery`

Which chests count, grounded in the actual loot tables:
- **Common and Uncommon:** every `chests/` table, the same gate `MagicBookLootModifier` uses for spell books.
- **Rare:** the vanilla dungeon-tier tables listed in `DUNGEON_TIER_TABLES`, every `hogwartsmagic:` chest table (`chamber_of_secrets`, `gringotts_vault`, `merfolk_hoard`, `diagon_shop`, `knockturn`), and any chest inside a structure in `#hogwartsmagic:tome_rare` (all nine mod structures; most of their chests use vanilla tables such as `simple_dungeon` or `pillager_outpost`), plus every Epic-eligible chest.
- **Epic:** the `hogwartsmagic:chests/gringotts_vault` table (the deep vaults carved by `VaultCarver`), the `hogwartsmagic:chests/chamber_of_secrets` table, and any chest inside a structure in `#hogwartsmagic:tome_endgame`: `horntail_nest` (its only chest uses the vanilla `minecraft:chests/nether_bridge` table, so the table alone can't identify it) and `chamber_hall` (which also has a `stronghold_corridor` chest). The `goblin_vault` structure is the Goblin Undercity vault row, not Gringotts, so it is Rare, not Epic.

- [ ] **Step 1: Create the structure tags**

`src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_endgame.json`:
```json
{
  "replace": false,
  "values": [
    "hogwartsmagic:horntail_nest",
    "hogwartsmagic:chamber_hall"
  ]
}
```
`src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_rare.json`:
```json
{
  "replace": false,
  "values": [
    "hogwartsmagic:black_lake_ruin",
    "hogwartsmagic:chamber_hall",
    "hogwartsmagic:death_eater_camp",
    "hogwartsmagic:fen_gibbet",
    "hogwartsmagic:goblin_vault",
    "hogwartsmagic:hogwarts_ruin_camp",
    "hogwartsmagic:horntail_nest",
    "hogwartsmagic:merfolk_village",
    "hogwartsmagic:willow_clearing"
  ]
}
```

- [ ] **Step 2: Create the modifier**

`src/main/java/com/hogwartsmagic/loot/TomeOfMasteryLootModifier.java`:
```java
package com.hogwartsmagic.loot;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.TomeOfMasteryItem;
import com.hogwartsmagic.spell.SpellLootRarity;
import com.hogwartsmagic.spell.mastery.TomeRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Tomes of Mastery in chests: one roll per chest, at most one tome. Common and Uncommon can turn
 * up in any chest that can roll a spell book; Rare only in dungeon-tier chests and mod
 * structures; Epic only in endgame loot. All four chances are config values.
 */
public class TomeOfMasteryLootModifier extends LootModifier {
    /** Structures whose every chest is endgame (the Horntail nest uses a vanilla table, so the table can't tell). */
    private static final TagKey<Structure> ENDGAME_STRUCTURES = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tome_endgame"));
    /** Mod structures, for chests inside them that use a vanilla table. */
    private static final TagKey<Structure> RARE_STRUCTURES = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "tome_rare"));

    private static final Set<ResourceLocation> ENDGAME_TABLES = Set.of(
            ResourceLocation.parse("hogwartsmagic:chests/gringotts_vault"),
            ResourceLocation.parse("hogwartsmagic:chests/chamber_of_secrets")
    );

    private static final Set<ResourceLocation> DUNGEON_TIER_TABLES = Set.of(
            ResourceLocation.parse("minecraft:chests/simple_dungeon"),
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"),
            ResourceLocation.parse("minecraft:chests/desert_pyramid"),
            ResourceLocation.parse("minecraft:chests/jungle_temple"),
            ResourceLocation.parse("minecraft:chests/stronghold_library"),
            ResourceLocation.parse("minecraft:chests/stronghold_corridor"),
            ResourceLocation.parse("minecraft:chests/stronghold_crossing"),
            ResourceLocation.parse("minecraft:chests/ancient_city"),
            ResourceLocation.parse("minecraft:chests/ancient_city_ice_box"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),
            ResourceLocation.parse("minecraft:chests/bastion_treasure"),
            ResourceLocation.parse("minecraft:chests/bastion_other"),
            ResourceLocation.parse("minecraft:chests/bastion_bridge"),
            ResourceLocation.parse("minecraft:chests/bastion_hoglin_stable"),
            ResourceLocation.parse("minecraft:chests/nether_bridge"),
            ResourceLocation.parse("minecraft:chests/buried_treasure"),
            ResourceLocation.parse("minecraft:chests/shipwreck_treasure")
    );

    public static final MapCodec<TomeOfMasteryLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, TomeOfMasteryLootModifier::new));

    protected TomeOfMasteryLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        // Same gate as spell books: anything under chests/ can roll a book, so it can roll a tome.
        if (tableId == null || !tableId.getPath().startsWith("chests/")) {
            return generatedLoot;
        }
        boolean endgame = ENDGAME_TABLES.contains(tableId) || insideStructure(context, ENDGAME_STRUCTURES);
        boolean rare = endgame
                || DUNGEON_TIER_TABLES.contains(tableId)
                || ExampleMod.MODID.equals(tableId.getNamespace())
                || insideStructure(context, RARE_STRUCTURES);
        SpellLootRarity tier = TomeRules.pickTome(context.getRandom().nextDouble(),
                Config.getTomeCommonChance(), Config.getTomeUncommonChance(),
                Config.getTomeRareChance(), Config.getTomeEpicChance(),
                rare, endgame);
        if (tier != null) {
            generatedLoot.add(TomeOfMasteryItem.stackFor(tier));
        }
        return generatedLoot;
    }

    /** Loot is rolled when the chest is first opened; ORIGIN is the chest's position. */
    private static boolean insideStructure(LootContext context, TagKey<Structure> structures) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return false;
        }
        return context.getLevel().structureManager()
                .getStructureWithPieceAt(BlockPos.containing(origin), structures).isValid();
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
```

- [ ] **Step 3: Register the codec.** In `ExampleMod.java`, directly after the `MAGICAL_SILK_LOOT_MODIFIER` registration, add:
```java
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.TomeOfMasteryLootModifier>> TOME_OF_MASTERY_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("tomes_of_mastery", () -> com.hogwartsmagic.loot.TomeOfMasteryLootModifier.CODEC);
```

- [ ] **Step 4: Add the modifier data**

`src/main/resources/data/hogwartsmagic/loot_modifiers/tomes_of_mastery_in_chests.json`:
```json
{
  "type": "hogwartsmagic:tomes_of_mastery",
  "conditions": []
}
```
Replace `src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json` with:
```json
{
  "replace": false,
  "entries": [
    "hogwartsmagic:magic_books_in_chests",
    "hogwartsmagic:sorting_hat_in_chests",
    "hogwartsmagic:invisibility_cloak_in_chests",
    "hogwartsmagic:magical_silk_in_chests",
    "hogwartsmagic:tomes_of_mastery_in_chests"
  ]
}
```

- [ ] **Step 5: Build and boot a dedicated server**

Run: `./gradlew compileJava test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: `BUILD SUCCESSFUL`, tests pass, and `run/logs/latest.log` has no line containing `tomes_of_mastery` together with `Couldn't parse`, `Failed` or `Unknown`, and no error about the `tome_endgame`/`tome_rare` tags. (A bad codec or a misspelled structure id in a tag shows up there at datapack load.)

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/loot/TomeOfMasteryLootModifier.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/data/hogwartsmagic/loot_modifiers/tomes_of_mastery_in_chests.json src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_endgame.json src/main/resources/data/hogwartsmagic/tags/worldgen/structure/tome_rare.json
git commit -m "Hide Tomes of Mastery in chests, rarest tiers in the hardest places" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests and dedicated server**

Run: `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass (`HitLedgerTest`, `MasteryMathTest`, `MasteryRulesTest`, `TomeRulesTest`); the server boots with no stack trace mentioning `Tome`, `mastery` or any `client` class.

- [ ] **Step 2: In-game checklist for a human** (`./gradlew runClient`; don't run gradle again until the client is closed)

In a survival world, with an op account for the commands:
1. The creative tab shows four tomes with distinct book textures (grey-white, yellow, aqua, purple) and names in white, yellow, aqua and light purple. Each tooltip shows the "Right-click..." line and the right "Works on..." line.
2. With no spells learned, right-click a Common tome: the action bar says no spell can be raised, and the tome is not consumed.
3. Learn Lumos (Common), Stupefy (Uncommon) and Avada Kedavra (Epic). Right-click a Common tome: the picker lists only Lumos, showing "I → II". The screen has no blur and the world stays visible behind the dim.
4. Click Lumos: the picker closes, the gold rank-up chat line, the level-up chime and the particles play, the tome count drops by one, `/spellmastery get @s` shows lumos II (30.0), and the Grimoire and wheel show rank II.
5. `/spellmastery set @s lumos 100` (rank II, 70% of the way to III), then use a Common tome on Lumos: it becomes exactly 120.0 (rank III), not 190.
6. An Uncommon tome lists Lumos and Stupefy, not Avada Kedavra. An Epic tome lists all three.
7. Use three tomes on Stupefy from Novice: it reaches IV (Master) and stops appearing in every picker. Max mana rose by 1 per rank gained (watch the mana bar; it updates within a tick).
8. Open the picker and press Escape: nothing is consumed and no rank changes. Server guard (do this on the dedicated server from step 11): open an Uncommon tome picker, then from the server console run `item replace entity <yourname> weapon.mainhand with hogwartsmagic:common_tome_of_mastery` and click Stupefy. The server refuses with "This tome cannot raise that spell." and the Common tome is not consumed. Run it again with `minecraft:book` instead: clicking does nothing at all.
9. Creative mode: a tome still raises one rank (tomes are not XP, so creative is allowed) and is consumed.
10. Loot sanity: temporarily set `tomeCommonChestChance`, `tomeUncommonChestChance`, `tomeRareChestChance` and `tomeEpicChestChance` to 0.25 each in `run/config/hogwartsmagic-common.toml`, restart, and open fresh chests: a village house chest gives only Common or Uncommon tomes; a `simple_dungeon` chest (for example `/setblock ~ ~ ~ chest{LootTable:"minecraft:chests/simple_dungeon"}`) can give Rare but never Epic; a chest with `LootTable:"hogwartsmagic:chests/gringotts_vault"` can give Epic; the Horntail nest chest (`/locate structure hogwartsmagic:horntail_nest`) can give Epic while a `nether_bridge` chest set outside the nest never does. No chest ever gives two tomes. Restore the defaults afterwards.
11. Dedicated server with a client connected (`./gradlew runServer`, then connect from `runClient`): steps 3 and 4 behave the same.

- [ ] **Step 3: Record results.** Note any failed check and fix it in a follow-up commit whose message describes what was wrong (with the same `Co-Authored-By` trailer). Then tell the user Phase 1b is ready to push.

---

## Self-Review

- **Spec coverage (roadmap, Phase 1b):** four tiers matching `SpellLootRarity` (T3); a tome raises only its tier or below (T1 `canRaise`, T3 `tryRaise`); exactly one rank per tome, all the way to Master, in survival (T1 `xpForNextRank` + its one-rank property test, T3); right-click picker of learned, non-Master, tier-eligible spells (T3 `eligibleSpells`, T4); clicking ranks up with the same chat line, chime and particles (T3 public `celebrate`, no duplicate) and the +1 max mana (existing `getMaxMana` + player tick, checked in T7.7); tome consumed (T3); server checks learned, rarity and not-Master, and the hand still holds a tome (T3 `tryRaise`); XP jumps to the start of the next rank (T1, T7.5); tier name colours white/yellow/aqua/light purple (T3 `displayRarity`) and a texture per tier (T5); chest loot only, no recipe, per-tier chances as config (T2, T6); Rare only in dungeon-tier and mod-structure chests and Epic only in Gringotts vaults, the Horntail nest and the Chamber (T6, T7.10). Dedicated-server safety is checked in T3, T4, T6 and T7.
- **Placeholder scan:** no TBDs; every code step has the full code. The `~` line numbers are anchors that drift; each one also names the code to look for.
- **Type consistency:** `TomeRules.pickTome(double, double, double, double, double, boolean, boolean)` returns a nullable `SpellLootRarity` in T1 and T6. `xpForNextRank(float, int[])` returns `float` (`-1` at Master) in T1 and T3. `OpenTomePickerPacket(boolean mainHand, int tierOrdinal, List<String> spellIds)` is built in T3 and read in T4 through the record accessors `mainHand()`, `tierOrdinal()`, `spellIds()`. `UseTomePacket(boolean mainHand, String spellId)` is sent in T4 and handled in T3. `TomeOfMasteryItem.registryName(SpellLootRarity)` gives the same `<tier>_tome_of_mastery` path used in T3 registration, the T3 lang keys, the T4 screen title and the T5 model and texture names. The client dispatch string `"openTomePicker"` matches between T3 and T4. Config getters `getTome{Common,Uncommon,Rare,Epic}Chance()` match between T2 and T6.
- **Project rules:** no `switch` on any enum (tier mapping is `if` chains); `TomePickerScreen` overrides `renderBlurredBackground` and `renderBackground` as no-ops and draws `renderTransparentBackground`; `TomeOfMasteryItem`, `TomeOfMasteryLootModifier` and `TomeRules` import no client classes; the screen opens only through `ClientSideBridge`.
