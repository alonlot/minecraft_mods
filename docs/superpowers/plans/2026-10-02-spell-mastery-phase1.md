# Spell Mastery Phase 1 (Backbone) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every spell levels from Novice (I) to Master (IV) through meaningful use. Each rank makes the spell cheaper and faster and permanently raises max mana (capped). Ranks show in the Grimoire and on the spell wheel.

**Architecture:** The pure arithmetic (`MasteryMath`, `MasteryRules`, `HitLedger`) has no Minecraft imports and is unit-tested with JUnit. `SpellMasteryData` stores XP in `PlayerPersisted/<modid>/SpellMastery` (a float per spell id) and syncs it to the client with a new packet. A new `SpellCosts` helper replaces about 20 copy-pasted cost and cooldown formulas, so the mastery discount applies everywhere. `MasteryAwards` grants XP from two hooks: the beam's `hitSuccessful` branch and `SpellCosts.startCooldown`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (new, for pure classes only).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md` (the decisions), with the ideas from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md`. Where they disagree, the roadmap wins.

## Global Constraints

- Ranks: I Novice, II Adept, III Expert, IV Master. Cumulative XP thresholds default to `0 / 30 / 120 / 350` (config).
- XP per meaningful cast = `clamp(round(baseCooldownSeconds / 3), 1, 20)` × target multiplier × config XP multiplier. XP is stored as a **float**, so 25% of 1 XP still counts.
- The discount is additive: −8% mana cost and −8% cooldown per rank above Novice (−24% at Master). It multiplies with the wand and house multipliers.
- **The house multiplier applies to cooldown only, never to mana cost** (today's behaviour; keep it).
- Max mana bonus = +1 per rank gained on any spell, **capped at +50** (both configurable).
- Anti-farm: same spell on the same target within 200 ticks gives no XP. Allied, tamed or owned targets give 0×; passive mobs 0.25×; spawner mobs 0.5×; if several apply, the lowest wins.
- Gated spells (block-utility, self and channel) earn XP at most once per `max(baseCooldownTicks, 200)` ticks per spell.
- No XP in creative or spectator mode. **Players only:** NPC casters (Student, Wizard, Voldemort) are not touched.
- Server authoritative. Common code never references `com.hogwartsmagic.client.*`. Client code only reads synced NBT.
- No `switch` on an enum in client or render code. Use `if` chains or `EnumSet`.
- **Never run gradle while the Minecraft client is running.**
- Commit after each task. Don't push; the user pushes.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java` | Pure: rank from XP, XP per cast, cost multiplier, mana bonus |
| Create `src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java` | Pure: target multiplier, gate window |
| Create `src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java` | Pure: "awarded this key recently?" memory |
| Create `src/main/java/com/hogwartsmagic/spell/mastery/SpellMasteryData.java` | NBT storage, rank queries, server-side add/set/reset, sync |
| Create `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | XP award rules, spawner tagging, ledger cleanup, rank-up feedback |
| Create `src/main/java/com/hogwartsmagic/spell/SpellCosts.java` | The single source of the mana-cost and cooldown formulas |
| Create `src/main/java/com/hogwartsmagic/command/SpellMasteryCommand.java` | `/spellmastery get/set/reset` (op) |
| Create `src/test/java/com/hogwartsmagic/spell/mastery/MasteryMathTest.java` | Unit tests |
| Create `src/test/java/com/hogwartsmagic/spell/mastery/MasteryRulesTest.java` | Unit tests |
| Create `src/test/java/com/hogwartsmagic/spell/mastery/HitLedgerTest.java` | Unit tests |
| Modify `build.gradle` | JUnit dependencies and `test { useJUnitPlatform() }` |
| Modify `Config.java` | Mastery config values |
| Modify `network/SpellPackets.java` | `SyncSpellMasteryPacket`; cost call sites |
| Modify `client/ClientRuntimeBridge.java` | Apply the mastery sync on the client |
| Modify `event/ModEvents.java` | Cost call sites; sync on login, respawn and dimension change |
| Modify `client/ModEventsClientHooks.java:111` | Client-side mana pre-check uses `SpellCosts` |
| Modify `util/PlayerMagicData.java` | `getMaxMana` adds the mastery bonus |
| Modify `entity/SpellBeamEntity.java` | Confringo fix (line 541); XP hook in `onHit` |
| Modify `client/SpellCompendiumScreen.java` | Rank badge, XP bar, effective cost |
| Modify `client/WandRadialMenuScreen.java` | Rank pip |
| Modify `ExampleMod.java:870` | Register the command |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | New strings |

---

### Task 1: Stop Confringo breaking terrain

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java:541`

**Interfaces:** none.

- [ ] **Step 1: Make the change**

At line 541, inside the `spell == SpellType.CONFRINGO` block-hit branch, change:
```java
this.level().explode(getOwner(), this.getX(), this.getY(), this.getZ(), 1.5f, true, Level.ExplosionInteraction.BLOCK);
```
to:
```java
// Confringo is the combat blast: fire and knockback, never terrain. Bombarda is the one that breaks blocks.
this.level().explode(getOwner(), this.getX(), this.getY(), this.getZ(), 1.5f, true, Level.ExplosionInteraction.NONE);
```
Keep `fire = true`. The fire still lights flammable blocks, which is intended until Phase 4.

- [ ] **Step 2: Build**

Run (client closed): `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**
```bash
git add src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
git commit -m "Stop Confringo cratering the ground it lands on"
```

---

### Task 2: JUnit and the pure mastery math

**Files:**
- Modify: `build.gradle` (the `dependencies {}` block, plus a new `test {}` block)
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mastery/MasteryMathTest.java`

**Interfaces:**
- Produces:
  - `MasteryMath.NOVICE = 1`, `MasteryMath.MAX_RANK = 4`
  - `static int rankForXp(float xp, int[] thresholds)`: `thresholds` has length 4, `thresholds[0] == 0`
  - `static float xpToNextRank(float xp, int[] thresholds)`: returns `-1` at Master
  - `static float rankProgress(float xp, int[] thresholds)`: 0..1 within the current rank, `1` at Master
  - `static int xpPerCast(int baseCooldownTicks)`
  - `static float scaledXp(int baseXp, double multiplier)`
  - `static float costMultiplier(int rank, double perRankDiscount)`
  - `static float maxManaBonus(int ranksGained, double perRank, double cap)`
  - `static String roman(int rank)`: `"I"`..`"IV"`

- [ ] **Step 1: Add JUnit to the build**

In `build.gradle`, inside `dependencies { ... }`, after the `annotationProcessor` line, add:
```groovy
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```
After the `dependencies` block, add:
```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/hogwartsmagic/spell/mastery/MasteryMathTest.java`:
```java
package com.hogwartsmagic.spell.mastery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryMathTest {
    private static final int[] T = {0, 30, 120, 350};

    @Test
    void rankForXpWalksTheThresholds() {
        assertEquals(1, MasteryMath.rankForXp(0f, T));
        assertEquals(1, MasteryMath.rankForXp(29.9f, T));
        assertEquals(2, MasteryMath.rankForXp(30f, T));
        assertEquals(3, MasteryMath.rankForXp(120f, T));
        assertEquals(4, MasteryMath.rankForXp(350f, T));
        assertEquals(4, MasteryMath.rankForXp(99999f, T));
    }

    @Test
    void xpToNextRankIsMinusOneAtMaster() {
        assertEquals(30f, MasteryMath.xpToNextRank(0f, T), 1e-4);
        assertEquals(10f, MasteryMath.xpToNextRank(110f, T), 1e-4);
        assertEquals(-1f, MasteryMath.xpToNextRank(350f, T), 1e-4);
    }

    @Test
    void rankProgressIsFractionOfCurrentBand() {
        assertEquals(0f, MasteryMath.rankProgress(0f, T), 1e-4);
        assertEquals(0.5f, MasteryMath.rankProgress(15f, T), 1e-4);
        assertEquals(0.5f, MasteryMath.rankProgress(75f, T), 1e-4);
        assertEquals(1f, MasteryMath.rankProgress(500f, T), 1e-4);
    }

    @Test
    void xpPerCastScalesWithCooldownAndIsClamped() {
        assertEquals(1, MasteryMath.xpPerCast(40));     // Expelliarmus, 2s
        assertEquals(2, MasteryMath.xpPerCast(100));    // 5s
        assertEquals(5, MasteryMath.xpPerCast(300));    // Fiendfyre, 15s
        assertEquals(20, MasteryMath.xpPerCast(12000)); // Avada, 600s
        assertEquals(1, MasteryMath.xpPerCast(0));
    }

    @Test
    void scaledXpKeepsFractionsAndZeroesNonPositive() {
        assertEquals(0.25f, MasteryMath.scaledXp(1, 0.25), 1e-4);
        assertEquals(10f, MasteryMath.scaledXp(20, 0.5), 1e-4);
        assertEquals(0f, MasteryMath.scaledXp(5, 0.0), 1e-4);
        assertEquals(0f, MasteryMath.scaledXp(5, -1.0), 1e-4);
    }

    @Test
    void costMultiplierIsAdditivePerRankAboveNovice() {
        assertEquals(1.00f, MasteryMath.costMultiplier(1, 0.08), 1e-4);
        assertEquals(0.92f, MasteryMath.costMultiplier(2, 0.08), 1e-4);
        assertEquals(0.84f, MasteryMath.costMultiplier(3, 0.08), 1e-4);
        assertEquals(0.76f, MasteryMath.costMultiplier(4, 0.08), 1e-4);
        assertEquals(0.76f, MasteryMath.costMultiplier(9, 0.08), 1e-4);
        assertEquals(1.00f, MasteryMath.costMultiplier(0, 0.08), 1e-4);
    }

    @Test
    void maxManaBonusIsCapped() {
        assertEquals(0f, MasteryMath.maxManaBonus(0, 1.0, 50.0), 1e-4);
        assertEquals(12f, MasteryMath.maxManaBonus(12, 1.0, 50.0), 1e-4);
        assertEquals(50f, MasteryMath.maxManaBonus(114, 1.0, 50.0), 1e-4);
        assertEquals(0f, MasteryMath.maxManaBonus(-3, 1.0, 50.0), 1e-4);
    }

    @Test
    void romanNumerals() {
        assertEquals("I", MasteryMath.roman(1));
        assertEquals("IV", MasteryMath.roman(4));
        assertEquals("I", MasteryMath.roman(0));
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

Run: `./gradlew test --tests com.hogwartsmagic.spell.mastery.MasteryMathTest`
Expected: compile FAIL, `cannot find symbol: class MasteryMath`.
If gradle instead fails setting up the test source set, which ForgeGradle can do: stop and report it. Don't drop the tests.

- [ ] **Step 4: Implement**

`src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java`:
```java
package com.hogwartsmagic.spell.mastery;

/**
 * Rank and XP arithmetic for spell mastery. Deliberately free of Minecraft types so it is
 * unit-tested directly; everything that touches a player goes through {@link SpellMasteryData}.
 */
public final class MasteryMath {
    public static final int NOVICE = 1;
    public static final int MAX_RANK = 4;
    private static final String[] ROMAN = {"I", "II", "III", "IV"};

    private MasteryMath() {
    }

    /** {@code thresholds[i]} is the cumulative XP for rank {@code i + 1}; {@code thresholds[0]} is 0. */
    public static int rankForXp(float xp, int[] thresholds) {
        int rank = NOVICE;
        for (int i = 1; i < thresholds.length && i < MAX_RANK; i++) {
            if (xp >= thresholds[i]) {
                rank = i + 1;
            }
        }
        return rank;
    }

    /** XP still missing for the next rank, or -1 once Master. */
    public static float xpToNextRank(float xp, int[] thresholds) {
        int rank = rankForXp(xp, thresholds);
        if (rank >= MAX_RANK) {
            return -1f;
        }
        return thresholds[rank] - xp;
    }

    /** How far through the current rank's band, 0..1; 1 at Master. Drives the Grimoire bar. */
    public static float rankProgress(float xp, int[] thresholds) {
        int rank = rankForXp(xp, thresholds);
        if (rank >= MAX_RANK) {
            return 1f;
        }
        float low = thresholds[rank - 1];
        float high = thresholds[rank];
        return Math.max(0f, Math.min(1f, (xp - low) / (high - low)));
    }

    /** Long-cooldown spells pay more per cast so every spell takes a similar real time to master. */
    public static int xpPerCast(int baseCooldownTicks) {
        int perCast = Math.round(baseCooldownTicks / 20.0f / 3.0f);
        return Math.max(1, Math.min(20, perCast));
    }

    public static float scaledXp(int baseXp, double multiplier) {
        if (multiplier <= 0.0) {
            return 0f;
        }
        return (float) (baseXp * multiplier);
    }

    /** Additive: -perRankDiscount for each rank above Novice. */
    public static float costMultiplier(int rank, double perRankDiscount) {
        int ranksAbove = Math.max(0, Math.min(MAX_RANK, rank) - NOVICE);
        return (float) Math.max(0.0, 1.0 - perRankDiscount * ranksAbove);
    }

    public static float maxManaBonus(int ranksGained, double perRank, double cap) {
        return (float) Math.min(cap, Math.max(0, ranksGained) * perRank);
    }

    public static String roman(int rank) {
        int index = Math.max(NOVICE, Math.min(MAX_RANK, rank)) - 1;
        return ROMAN[index];
    }
}
```

- [ ] **Step 5: Run it and confirm it passes**

Run: `./gradlew test --tests com.hogwartsmagic.spell.mastery.MasteryMathTest`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**
```bash
git add build.gradle src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java src/test/java/com/hogwartsmagic/spell/mastery/MasteryMathTest.java
git commit -m "Add the rank arithmetic spell mastery is built on"
```

---

### Task 3: Anti-farm rules and the hit ledger

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mastery/MasteryRulesTest.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mastery/HitLedgerTest.java`

**Interfaces:**
- Produces:
  - `MasteryRules.SAME_TARGET_WINDOW_TICKS = 200L`
  - `static double MasteryRules.targetMultiplier(boolean allied, boolean passive, boolean fromSpawner)`
  - `static long MasteryRules.gateWindowTicks(int baseCooldownTicks)`
  - `new HitLedger()`, `boolean tryAward(String key, long now, long windowTicks)`, `int size()`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/mastery/MasteryRulesTest.java`:
```java
package com.hogwartsmagic.spell.mastery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryRulesTest {
    @Test
    void alliedAlwaysZero() {
        assertEquals(0.0, MasteryRules.targetMultiplier(true, false, false), 1e-9);
        assertEquals(0.0, MasteryRules.targetMultiplier(true, true, true), 1e-9);
    }

    @Test
    void lowestPenaltyWins() {
        assertEquals(1.0, MasteryRules.targetMultiplier(false, false, false), 1e-9);
        assertEquals(0.25, MasteryRules.targetMultiplier(false, true, false), 1e-9);
        assertEquals(0.5, MasteryRules.targetMultiplier(false, false, true), 1e-9);
        assertEquals(0.25, MasteryRules.targetMultiplier(false, true, true), 1e-9);
    }

    @Test
    void gateWindowIsAtLeastTenSeconds() {
        assertEquals(200L, MasteryRules.gateWindowTicks(20));
        assertEquals(1200L, MasteryRules.gateWindowTicks(1200));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/mastery/HitLedgerTest.java`:
```java
package com.hogwartsmagic.spell.mastery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitLedgerTest {
    @Test
    void secondAwardInsideWindowIsRefused() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryAward("hit:stupefy:a", 1000L, 200L));
        assertFalse(ledger.tryAward("hit:stupefy:a", 1199L, 200L));
        assertTrue(ledger.tryAward("hit:stupefy:a", 1200L, 200L));
    }

    @Test
    void refusedAttemptDoesNotRestartTheWindow() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryAward("k", 0L, 200L));
        assertFalse(ledger.tryAward("k", 150L, 200L));
        assertTrue(ledger.tryAward("k", 200L, 200L));
    }

    @Test
    void keysAreIndependent() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryAward("hit:stupefy:a", 0L, 200L));
        assertTrue(ledger.tryAward("hit:glacius:a", 0L, 200L));
        assertTrue(ledger.tryAward("hit:stupefy:b", 0L, 200L));
    }

    @Test
    void oldEntriesArePrunedOnceLarge() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("k" + i, 0L, 200L);
        }
        ledger.tryAward("fresh", HitLedger.PRUNE_AGE_TICKS + 1, 200L);
        assertEquals(1, ledger.size());
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew test --tests "com.hogwartsmagic.spell.mastery.*"`
Expected: compile FAIL, `cannot find symbol: class MasteryRules` / `HitLedger`.

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java`:
```java
package com.hogwartsmagic.spell.mastery;

/** The anti-farm numbers, kept apart from entity checks so they can be tested. */
public final class MasteryRules {
    /** Same spell on the same target pays once per this many ticks. */
    public static final long SAME_TARGET_WINDOW_TICKS = 200L;
    private static final long MIN_GATE_TICKS = 200L;

    private MasteryRules() {
    }

    public static double targetMultiplier(boolean allied, boolean passive, boolean fromSpawner) {
        if (allied) {
            return 0.0;
        }
        double multiplier = 1.0;
        if (passive) {
            multiplier = Math.min(multiplier, 0.25);
        }
        if (fromSpawner) {
            multiplier = Math.min(multiplier, 0.5);
        }
        return multiplier;
    }

    /** Block-utility and self/channel spells pay at most once per this window. */
    public static long gateWindowTicks(int baseCooldownTicks) {
        return Math.max(baseCooldownTicks, MIN_GATE_TICKS);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java`:
```java
package com.hogwartsmagic.spell.mastery;

import java.util.HashMap;
import java.util.Map;

/** Remembers when each award key last paid out, so repeat hits inside a window earn nothing. */
public final class HitLedger {
    /** Longer than any gate window (Avada's 12000-tick cooldown), so pruning never forgets a live entry. */
    static final long PRUNE_AGE_TICKS = 24000L;
    private static final int PRUNE_AT_SIZE = 256;

    private final Map<String, Long> lastAward = new HashMap<>();

    public boolean tryAward(String key, long now, long windowTicks) {
        Long last = lastAward.get(key);
        if (last != null && now - last < windowTicks) {
            return false;
        }
        if (lastAward.size() >= PRUNE_AT_SIZE) {
            lastAward.values().removeIf(time -> now - time >= PRUNE_AGE_TICKS);
        }
        lastAward.put(key, now);
        return true;
    }

    public int size() {
        return lastAward.size();
    }
}
```

- [ ] **Step 4: Run them and confirm they pass**

Run: `./gradlew test --tests "com.hogwartsmagic.spell.mastery.*"`
Expected: PASS (all three test classes).

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java src/test/java/com/hogwartsmagic/spell/mastery/MasteryRulesTest.java src/test/java/com/hogwartsmagic/spell/mastery/HitLedgerTest.java
git commit -m "Keep spell mastery from paying out for farming the same target"
```

---

### Task 4: Mastery config

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/Config.java` (defines go before `static final ForgeConfigSpec SPEC = BUILDER.build();` at line 102; static fields next to the others near line 123; loads in `onLoad`; getters next to `getBaseMaxMana()`)

**Interfaces:**
- Produces: `Config.getMasteryThresholds()` → `int[4]`, `Config.getMasteryXpMultiplier()` → `double`, `Config.getMasteryRankDiscount()` → `double`, `Config.getMasteryManaPerRank()` → `double`, `Config.getMasteryManaCap()` → `double`

- [ ] **Step 1: Add the definitions** (just above `static final ForgeConfigSpec SPEC = BUILDER.build();`)
```java
    public static final ForgeConfigSpec.IntValue MASTERY_XP_ADEPT = BUILDER
            .comment("Spell mastery: cumulative XP to reach rank II (Adept).")
            .defineInRange("masteryXpAdept", 30, 1, 100000);

    public static final ForgeConfigSpec.IntValue MASTERY_XP_EXPERT = BUILDER
            .comment("Spell mastery: cumulative XP to reach rank III (Expert). Must exceed masteryXpAdept.")
            .defineInRange("masteryXpExpert", 120, 2, 100000);

    public static final ForgeConfigSpec.IntValue MASTERY_XP_MASTER = BUILDER
            .comment("Spell mastery: cumulative XP to reach rank IV (Master). Must exceed masteryXpExpert.")
            .defineInRange("masteryXpMaster", 350, 3, 100000);

    public static final ForgeConfigSpec.DoubleValue MASTERY_XP_MULTIPLIER = BUILDER
            .comment("Spell mastery: multiplier on all XP earned. 0 disables progression.")
            .defineInRange("masteryXpMultiplier", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue MASTERY_RANK_DISCOUNT = BUILDER
            .comment("Spell mastery: mana cost and cooldown reduction per rank above Novice (0.08 = 8%, so 24% at Master).")
            .defineInRange("masteryRankDiscount", 0.08, 0.0, 0.3);

    public static final ForgeConfigSpec.DoubleValue MASTERY_MANA_PER_RANK = BUILDER
            .comment("Spell mastery: permanent max mana gained per rank on any spell.")
            .defineInRange("masteryManaPerRank", 1.0, 0.0, 100.0);

    public static final ForgeConfigSpec.DoubleValue MASTERY_MANA_CAP = BUILDER
            .comment("Spell mastery: ceiling on the total max mana bonus from ranks.")
            .defineInRange("masteryManaCap", 50.0, 0.0, 100000.0);
```

- [ ] **Step 2: Add the static fields** (next to the other `public static` value fields)
```java
    public static int masteryXpAdept = 30;
    public static int masteryXpExpert = 120;
    public static int masteryXpMaster = 350;
    public static double masteryXpMultiplier = 1.0;
    public static double masteryRankDiscount = 0.08;
    public static double masteryManaPerRank = 1.0;
    public static double masteryManaCap = 50.0;
```

- [ ] **Step 3: Load them** (at the end of `onLoad`)
```java
        masteryXpAdept = MASTERY_XP_ADEPT.get();
        masteryXpExpert = MASTERY_XP_EXPERT.get();
        masteryXpMaster = MASTERY_XP_MASTER.get();
        masteryXpMultiplier = MASTERY_XP_MULTIPLIER.get();
        masteryRankDiscount = MASTERY_RANK_DISCOUNT.get();
        masteryManaPerRank = MASTERY_MANA_PER_RANK.get();
        masteryManaCap = MASTERY_MANA_CAP.get();
```

- [ ] **Step 4: Add the getters** (next to `getBaseMaxMana()`)
```java
    /** Cumulative XP per rank, index 0 = Novice. Falls back to defaults if the config isn't strictly rising. */
    public static int[] getMasteryThresholds() {
        if (masteryXpAdept < masteryXpExpert && masteryXpExpert < masteryXpMaster) {
            return new int[]{0, masteryXpAdept, masteryXpExpert, masteryXpMaster};
        }
        return new int[]{0, 30, 120, 350};
    }

    public static double getMasteryXpMultiplier() {
        return masteryXpMultiplier;
    }

    public static double getMasteryRankDiscount() {
        return masteryRankDiscount;
    }

    public static double getMasteryManaPerRank() {
        return masteryManaPerRank;
    }

    public static double getMasteryManaCap() {
        return masteryManaCap;
    }
```

- [ ] **Step 5: Build**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/Config.java
git commit -m "Make spell mastery pacing configurable"
```

---

### Task 5: Mastery storage and client sync

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/SpellMasteryData.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (new record after `SyncLearnedSpellsPacket`, line ~498; registration after the `SyncLearnedSpellsPacket` builder, lines 97–101)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` (new `case` near line 30; new method after `syncLearnedSpells`, line ~166)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (after each `new SpellPackets.SyncLearnedSpellsPacket(...)` send, at lines ~542, ~601 and ~732)

**Interfaces:**
- Consumes: `MasteryMath.*`, `Config.getMasteryThresholds()`, `Config.getMasteryManaPerRank()`, `Config.getMasteryManaCap()`, `PlayerMagicData.getModData/saveModData`
- Produces:
  - `SpellMasteryData.KEY = "SpellMastery"`
  - `static float getXp(Player, SpellType)`, `static int getRank(Player, SpellType)`, `static int getRanksGained(Player)`, `static float getMaxManaBonus(Player)` (safe on both sides)
  - Server only: `static int addXp(ServerPlayer, SpellType, float)` returns the new rank if a boundary was crossed, else 0; `static void setXp(ServerPlayer, SpellType, float)`; `static void reset(ServerPlayer)`; `static void sync(ServerPlayer)`
  - `SpellPackets.SyncSpellMasteryPacket(CompoundTag mastery)`

- [ ] **Step 1: Create `SpellMasteryData`**
```java
package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Per-player spell XP, stored as {@code PlayerPersisted/<modid>/SpellMastery/<spellId> = float}.
 * Forge copies PlayerPersisted across death, so ranks survive dying. Reads are safe on either side
 * (the client copy arrives via {@link SpellPackets.SyncSpellMasteryPacket}); writes are server-only.
 */
public final class SpellMasteryData {
    public static final String KEY = "SpellMastery";

    private SpellMasteryData() {
    }

    private static CompoundTag tag(Player player) {
        return PlayerMagicData.getModData(player).getCompound(KEY);
    }

    public static float getXp(Player player, SpellType spell) {
        return tag(player).getFloat(spell.getId());
    }

    public static int getRank(Player player, SpellType spell) {
        return MasteryMath.rankForXp(getXp(player, spell), Config.getMasteryThresholds());
    }

    public static int getRanksGained(Player player) {
        CompoundTag tag = tag(player);
        int[] thresholds = Config.getMasteryThresholds();
        int total = 0;
        for (String id : tag.getAllKeys()) {
            total += MasteryMath.rankForXp(tag.getFloat(id), thresholds) - MasteryMath.NOVICE;
        }
        return total;
    }

    public static float getMaxManaBonus(Player player) {
        return MasteryMath.maxManaBonus(getRanksGained(player),
                Config.getMasteryManaPerRank(), Config.getMasteryManaCap());
    }

    public static int addXp(ServerPlayer player, SpellType spell, float amount) {
        if (amount <= 0f) {
            return 0;
        }
        float before = getXp(player, spell);
        float after = before + amount;
        write(player, spell, after);
        int[] thresholds = Config.getMasteryThresholds();
        int oldRank = MasteryMath.rankForXp(before, thresholds);
        int newRank = MasteryMath.rankForXp(after, thresholds);
        return newRank > oldRank ? newRank : 0;
    }

    public static void setXp(ServerPlayer player, SpellType spell, float xp) {
        write(player, spell, Math.max(0f, xp));
    }

    public static void reset(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        modData.remove(KEY);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.SyncSpellMasteryPacket(tag(player).copy()),
                PacketDistributor.PLAYER.with(player));
    }

    private static void write(ServerPlayer player, SpellType spell, float xp) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        CompoundTag tag = modData.getCompound(KEY);
        tag.putFloat(spell.getId(), xp);
        modData.put(KEY, tag);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
    }
}
```

- [ ] **Step 2: Add the packet record** to `SpellPackets.java`, directly after the `SyncLearnedSpellsPacket` record:
```java
    public record SyncSpellMasteryPacket(net.minecraft.nbt.CompoundTag mastery) {
        public static void handle(SyncSpellMasteryPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncSpellMastery", p));
        }
    }
```

- [ ] **Step 3: Register it** directly after the `SyncLearnedSpellsPacket` builder (lines 97–101):
```java
        CHANNEL.messageBuilder(SyncSpellMasteryPacket.class)
                .encoder((p, buf) -> buf.writeNbt(p.mastery))
                .decoder(buf -> {
                    net.minecraft.nbt.CompoundTag tag = buf.readNbt();
                    return new SyncSpellMasteryPacket(tag == null ? new net.minecraft.nbt.CompoundTag() : tag);
                })
                .consumerMainThread(SyncSpellMasteryPacket::handle)
                .add();
```

- [ ] **Step 4: Apply it on the client.** In `ClientRuntimeBridge.handlePacket`'s `switch (action)` (this switch is on a `String`, not an enum, so it's allowed), add after the `syncLearnedSpells` case:
```java
            case "syncSpellMastery" -> syncSpellMastery((SpellPackets.SyncSpellMasteryPacket) payload);
```
Add the method after `syncLearnedSpells`:
```java
    private static void syncSpellMastery(SpellPackets.SyncSpellMasteryPacket packet) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound("PlayerPersisted");
        CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        modData.put("SpellMastery", packet.mastery());
        persisted.put(ExampleMod.MODID, modData);
        player.getPersistentData().put("PlayerPersisted", persisted);
    }
```

- [ ] **Step 5: Sync on login, respawn and dimension change.** In `ModEvents.java`, find every `new SpellPackets.SyncLearnedSpellsPacket(` (grep; about lines 542, 601 and 732). Directly after each enclosing `if (!learned.isEmpty()) { ... }` block, add:
```java
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);
```
Use whatever the `ServerPlayer` variable is called at each site (`player` or `serverPlayer`). Put it outside the `if`, so a player with no learned spells still gets an empty sync.

- [ ] **Step 6: Build**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mastery/SpellMasteryData.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/event/ModEvents.java
git commit -m "Store spell mastery XP and keep the client copy in step"
```

---

### Task 6: One home for mana cost and cooldown

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/SpellCosts.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`, every player cost/cooldown site (lines ~740, 758, 778, 813, 831, 846, 857, 891, 896, 962, 1001, 1020)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`, every player cost/cooldown site (lines ~1129, 1134, 1342, 1347, 1551, 1556, 1653, 1828, 1865, 1870, 2100, 4014)
- Modify: `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java:111`
- Modify: `src/main/java/com/hogwartsmagic/util/PlayerMagicData.java` (`getMaxMana`)

**Interfaces:**
- Consumes: `SpellMasteryData.getRank`, `SpellMasteryData.getMaxManaBonus`, `MasteryMath.costMultiplier`, `Config.getMasteryRankDiscount()`
- Produces:
  - `static float SpellCosts.masteryMultiplier(Player, SpellType)`
  - `static int SpellCosts.manaCost(Player, ItemStack wand, SpellType)`
  - `static long SpellCosts.cooldownTicks(Player, ItemStack wand, SpellType)` and `cooldownTicks(Player, ItemStack wand, SpellType, double baseTicks)`
  - `static void SpellCosts.startCooldown(ServerPlayer, ItemStack wand, SpellType, long now)` and `startCooldown(ServerPlayer, ItemStack wand, SpellType, long now, double baseTicks)`, which Task 7 extends to award XP

- [ ] **Step 1: Create `SpellCosts`**
```java
package com.hogwartsmagic.spell;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The one place a player's spell cost and cooldown are worked out. Mana cost = base x wand x
 * mastery; cooldown = base x house x wand x mastery. (The house only ever touched cooldown, and
 * that stays as it was.) NPC casters keep their own maths and don't come through here.
 */
public final class SpellCosts {
    private SpellCosts() {
    }

    public static float masteryMultiplier(Player player, SpellType spell) {
        return MasteryMath.costMultiplier(SpellMasteryData.getRank(player, spell), Config.getMasteryRankDiscount());
    }

    public static int manaCost(Player player, ItemStack wand, SpellType spell) {
        return Math.max(1, Math.round(spell.getManaCost()
                * MagicWandItem.getManaCostMultiplier(wand) * masteryMultiplier(player, spell)));
    }

    public static long cooldownTicks(Player player, ItemStack wand, SpellType spell) {
        return cooldownTicks(player, wand, spell, spell.getCooldownTicks());
    }

    /** For the sites that cool down on a fraction of the base (Patronus uses a quarter). */
    public static long cooldownTicks(Player player, ItemStack wand, SpellType spell, double baseTicks) {
        return Math.max(1L, Math.round(baseTicks
                * HousePerks.getCooldownMultiplier(player)
                * MagicWandItem.getCooldownMultiplier(wand)
                * masteryMultiplier(player, spell)));
    }

    public static void startCooldown(ServerPlayer player, ItemStack wand, SpellType spell, long now) {
        startCooldown(player, wand, spell, now, spell.getCooldownTicks());
    }

    public static void startCooldown(ServerPlayer player, ItemStack wand, SpellType spell, long now, double baseTicks) {
        MagicWandItem.setSpellCooldown(wand, spell, now + cooldownTicks(player, wand, spell, baseTicks));
    }
}
```

- [ ] **Step 2: Replace the cost sites.** At each line listed under **Files**, rewrite with these patterns. Use the local player variable at that site (`player`). Leave Student, Wizard and Voldemort files alone.

| Old shape | New shape |
|---|---|
| `Math.max(1, Math.round(spell.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)))` | `SpellCosts.manaCost(player, wand, spell)` |
| `long x = Math.max(1L, Math.round(spell.getCooldownTicks() * HousePerks.getCooldownMultiplier(player) * MagicWandItem.getCooldownMultiplier(wand)));` followed by `MagicWandItem.setSpellCooldown(wand, spell, now + x);` | `SpellCosts.startCooldown(player, wand, spell, now);` |
| Same, but base `(spell.getCooldownTicks() / 4.0D)` (ModEvents ~1340) | `SpellCosts.startCooldown(player, wand, spell, now, spell.getCooldownTicks() / 4.0D);` |
| A cooldown value computed but used for something other than `setSpellCooldown` | `SpellCosts.cooldownTicks(player, wand, spell)` |
| Upkeep helpers like `getProtegoMaximaUpkeepCost(ItemStack wand)` returning `Math.max(k, SpellType.X.getManaCost() * MagicWandItem.getManaCostMultiplier(wand) / n)` | Add a `Player player` first parameter and multiply inside by `SpellCosts.masteryMultiplier(player, SpellType.X)`; update every caller |

Concretely, the upkeep and startup helpers are `getProtegoMaximaUpkeepCost` (ModEvents 1134, SpellPackets 896), `getPatronus…` (ModEvents 1347), Wingardium (ModEvents 1556), Imperio (ModEvents 1828), and Fiendfyre upkeep (ModEvents 1870) and startup (SpellPackets 962). Their new form, using Protego Maxima as the example:
```java
private static float getProtegoMaximaUpkeepCost(Player player, ItemStack wand) {
    return Math.max(0.75f, (SpellType.PROTEGO_MAXIMA.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
            * SpellCosts.masteryMultiplier(player, SpellType.PROTEGO_MAXIMA)) / 24.0f);
}
```
After the edits, `grep -n "getManaCostMultiplier\|getCooldownMultiplier(player)" src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/network/SpellPackets.java` may only show the upkeep helpers' own bodies. Fix any other hit.

The Apparition site (ModEvents ~2098) computes `cooldownTicks` in a `long` and then sets it. Use `SpellCosts.startCooldown(player, wand, SpellType.APPARITION, now)` if the code is `setSpellCooldown(wand, APPARITION, now + cooldownTicks)`; otherwise use `SpellCosts.cooldownTicks(...)`.

- [ ] **Step 3: Client pre-check.** At `ModEventsClientHooks.java:111`, replace:
```java
int requiredMana = Math.max(1, Math.round(selected.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)));
```
with:
```java
int requiredMana = com.hogwartsmagic.spell.SpellCosts.manaCost(player, wand, selected);
```
`SpellCosts` is common code that only reads synced NBT, so it's safe here.

- [ ] **Step 4: Add the mastery bonus to max mana.** In `PlayerMagicData.getMaxMana`:
```java
    public static float getMaxMana(Player player) {
        return Config.getBaseMaxMana()
                + HousePerks.getMaxManaBonus(player)
                + getRelicMaxManaBonus(player)
                + com.hogwartsmagic.spell.mastery.SpellMasteryData.getMaxManaBonus(player);
    }
```
The existing player tick (ModEvents ~764) already notices a changed `MaxMana` and resends it, so nothing else is needed.

- [ ] **Step 5: Build and run the unit tests**

Run: `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Behaviour check: costs must be unchanged at Novice.** With every spell at Novice, `masteryMultiplier` is exactly 1.0, so all costs and cooldowns are identical to before. Run `./gradlew runServer` and confirm it reaches `Done (` with no exception mentioning `SpellCosts` or `SpellMasteryData`, then stop it.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/SpellCosts.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java src/main/java/com/hogwartsmagic/util/PlayerMagicData.java
git commit -m "Work out spell cost and cooldown in one place so mastery can discount them"
```

---

### Task 7: Earning XP and the rank-up moment

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellCosts.java` (`startCooldown` calls `MasteryAwards.onCastCompleted`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (`onHit`: line 491 declaration, line ~705 capture, line ~733 award)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SpellMasteryData.addXp`, `MasteryMath.xpPerCast/scaledXp/roman`, `MasteryRules.*`, `HitLedger`, `Config.getMasteryXpMultiplier()`
- Produces: `MasteryAwards.onLivingHit(ServerPlayer, SpellType, LivingEntity)`, `MasteryAwards.onBlockEffect(ServerPlayer, SpellType)`, `MasteryAwards.onCastCompleted(ServerPlayer, SpellType)`, `MasteryAwards.SPAWNER_TAG`, `MasteryAwards.rankKey(int)` → `"gui.hogwartsmagic.mastery.rank.<n>"`

- [ ] **Step 1: Create `MasteryAwards`**
```java
package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decides when a cast was meaningful enough to earn mastery XP. Three kinds of spell:
 * beams that hit living things (paid per target, with anti-farm), beams whose job is a block
 * (paid when the block effect worked), and self/channel spells (paid when the cast completes and
 * its cooldown starts). Every path is server-only and players-only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MasteryAwards {
    public static final String SPAWNER_TAG = "hogwartsmagic:from_spawner";
    private static final String ALLIED_TAG = "hogwartsmagic:allied";

    private static final Set<SpellType> SELF_OR_CHANNEL = EnumSet.of(
            SpellType.LUMOS, SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA, SpellType.TENEBRIS_FLIGHT,
            SpellType.EXPECTO_PATRONUM, SpellType.APPARITION, SpellType.FIENDFYRE,
            SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO);

    private static final Set<SpellType> BLOCK_UTILITY = EnumSet.of(
            SpellType.ACCIO, SpellType.ALOHOMORA, SpellType.INCENDIO, SpellType.AQUA_ERUCTO,
            SpellType.MORSMORDRE, SpellType.CAVE_INIMICUM, SpellType.ALATUS, SpellType.REPARO,
            SpellType.PIERTOTUM_LOCOMOTOR, SpellType.SERPENSORTIA);

    private static final Map<UUID, HitLedger> LEDGERS = new HashMap<>();

    private MasteryAwards() {
    }

    public static void onLivingHit(ServerPlayer caster, SpellType spell, LivingEntity target) {
        if (!eligible(caster) || target == caster || SELF_OR_CHANNEL.contains(spell)) {
            return;
        }
        double multiplier = MasteryRules.targetMultiplier(
                isAllied(caster, target), isPassive(target), target.getTags().contains(SPAWNER_TAG));
        if (multiplier <= 0.0) {
            return;
        }
        String key = "hit:" + spell.getId() + ":" + target.getUUID();
        if (!ledger(caster).tryAward(key, caster.level().getGameTime(), MasteryRules.SAME_TARGET_WINDOW_TICKS)) {
            return;
        }
        grant(caster, spell, multiplier);
    }

    public static void onBlockEffect(ServerPlayer caster, SpellType spell) {
        if (eligible(caster) && BLOCK_UTILITY.contains(spell)) {
            gated(caster, spell, "block:");
        }
    }

    public static void onCastCompleted(ServerPlayer caster, SpellType spell) {
        if (eligible(caster) && SELF_OR_CHANNEL.contains(spell)) {
            gated(caster, spell, "cast:");
        }
    }

    public static String rankKey(int rank) {
        return "gui." + ExampleMod.MODID + ".mastery.rank." + rank;
    }

    private static void gated(ServerPlayer caster, SpellType spell, String prefix) {
        long window = MasteryRules.gateWindowTicks(spell.getCooldownTicks());
        if (ledger(caster).tryAward(prefix + spell.getId(), caster.level().getGameTime(), window)) {
            grant(caster, spell, 1.0);
        }
    }

    private static void grant(ServerPlayer caster, SpellType spell, double multiplier) {
        float xp = MasteryMath.scaledXp(MasteryMath.xpPerCast(spell.getCooldownTicks()),
                multiplier * Config.getMasteryXpMultiplier());
        int newRank = SpellMasteryData.addXp(caster, spell, xp);
        if (newRank > 0) {
            celebrate(caster, spell, newRank);
        }
    }

    private static void celebrate(ServerPlayer caster, SpellType spell, int newRank) {
        caster.sendSystemMessage(Component.translatable("gui." + ExampleMod.MODID + ".mastery.rank_up",
                        Component.translatable("spell." + ExampleMod.MODID + "." + spell.getId()),
                        MasteryMath.roman(newRank),
                        Component.translatable(rankKey(newRank)))
                .withStyle(ChatFormatting.GOLD));
        caster.level().playSound(null, caster.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.3f);
        if (caster.level() instanceof ServerLevel level) {
            double x = caster.getX();
            double y = caster.getY() + 1.0;
            double z = caster.getZ();
            level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 40, 0.5, 0.8, 0.5, 0.6);
            level.sendParticles(spell.getDustParticle(), x, y, z, 24, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private static boolean eligible(ServerPlayer caster) {
        return !caster.isCreative() && !caster.isSpectator();
    }

    private static boolean isAllied(ServerPlayer caster, LivingEntity target) {
        if (target.getTags().contains(ALLIED_TAG) || caster.isAlliedTo(target)) {
            return true;
        }
        return target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }

    /** Mobs that neither hunt you nor fight back: cows, villagers, owls. */
    private static boolean isPassive(LivingEntity target) {
        if (target instanceof Player || target instanceof Enemy || target instanceof NeutralMob) {
            return false;
        }
        return target instanceof Mob;
    }

    private static HitLedger ledger(ServerPlayer caster) {
        return LEDGERS.computeIfAbsent(caster.getUUID(), id -> new HitLedger());
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER || type == MobSpawnType.TRIAL_SPAWNER) {
            event.getEntity().addTag(SPAWNER_TAG);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LEDGERS.remove(event.getEntity().getUUID());
    }
}
```
If `MobSpawnEvent.FinalizeSpawn#getSpawnType` doesn't exist under that name in Forge 51.0.33, find the accessor in the decompiled class (in IntelliJ, open `MobSpawnEvent$FinalizeSpawn`). Don't remove the spawner rule.

- [ ] **Step 2: Award on cast completion.** In `SpellCosts.startCooldown(ServerPlayer, ItemStack, SpellType, long, double)`, after the `setSpellCooldown` line, add:
```java
        com.hogwartsmagic.spell.mastery.MasteryAwards.onCastCompleted(player, spell);
```

- [ ] **Step 3: Award on beam success.** In `SpellBeamEntity.onHit`:
  1. Directly after `boolean hitSuccessful = false;` (line 491), add:
     ```java
     LivingEntity masteryTarget = null;
     ```
  2. In the "Normal hit" branch, directly after the `hitSuccessful = true;` that sits just before the chip-damage comment (line ~705), add:
     ```java
     masteryTarget = living;
     ```
     Only this branch counts. Protego blocks, Voldemort's dodge and the Morsmordre/Piertotum fizzle all stay at zero XP.
  3. In the success branch (`if (!hitSuccessful) { spawnFizzleEffects(); } else {`, line ~731), make the first statement inside `else {`:
     ```java
     if (getOwner() instanceof ServerPlayer masteryCaster) {
         if (masteryTarget != null) {
             com.hogwartsmagic.spell.mastery.MasteryAwards.onLivingHit(masteryCaster, spell, masteryTarget);
         } else if (result instanceof BlockHitResult) {
             com.hogwartsmagic.spell.mastery.MasteryAwards.onBlockEffect(masteryCaster, spell);
         }
     }
     ```
     If `ServerPlayer` isn't imported in this file, use `net.minecraft.server.level.ServerPlayer`.

- [ ] **Step 4: Add the strings** to `en_us.json`, next to the other `gui.hogwartsmagic.grimoire.*` keys:
```json
  "gui.hogwartsmagic.mastery.rank_up": "%s reached rank %s: %s!",
  "gui.hogwartsmagic.mastery.rank.1": "Novice",
  "gui.hogwartsmagic.mastery.rank.2": "Adept",
  "gui.hogwartsmagic.mastery.rank.3": "Expert",
  "gui.hogwartsmagic.mastery.rank.4": "Master",
```

- [ ] **Step 5: Build, test and boot a dedicated server**

Run: `./gradlew compileJava test`, then `./gradlew runServer` until `Done (`, then stop it.
Expected: build passes, tests pass, no exception in the server log mentioning `MasteryAwards`. `MasteryAwards` only imports common classes, so loading it on a dedicated server is safe; the boot proves it.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/java/com/hogwartsmagic/spell/SpellCosts.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let spells earn mastery from casts that actually did something"
```

---

### Task 8: `/spellmastery` debug command

**Files:**
- Create: `src/main/java/com/hogwartsmagic/command/SpellMasteryCommand.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java:870` (register next to `OllivandersCommand`)

**Interfaces:**
- Consumes: `SpellMasteryData.getXp/getRank/setXp/reset`, `SpellType.fromId`, `SpellType.values()`, `Config.getMasteryThresholds()`

- [ ] **Step 1: Create the command**
```java
package com.hogwartsmagic.command;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * {@code /spellmastery get|set|setrank|reset} -- inspect and force spell ranks while tuning.
 * Op-only. Creative casts earn no XP, so this is how a tester jumps straight to Master.
 */
public class SpellMasteryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spellmastery")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SpellMasteryCommand::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SpellType.values()).map(SpellType::getId), b))
                                        .then(Commands.argument("xp", FloatArgumentType.floatArg(0f))
                                                .executes(SpellMasteryCommand::set)))))
                .then(Commands.literal("setrank")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SpellType.values()).map(SpellType::getId), b))
                                        .then(Commands.argument("rank", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4))
                                                .executes(SpellMasteryCommand::setRank)))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SpellMasteryCommand::reset))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        StringBuilder out = new StringBuilder();
        for (SpellType spell : SpellType.values()) {
            float xp = SpellMasteryData.getXp(player, spell);
            if (xp > 0f) {
                out.append(spell.getId()).append(' ')
                        .append(MasteryMath.roman(SpellMasteryData.getRank(player, spell)))
                        .append(String.format(" (%.1f)  ", xp));
            }
        }
        String text = out.length() == 0 ? "no mastery yet" : out.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": " + text), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        SpellType spell = SpellType.fromId(StringArgumentType.getString(ctx, "spell"));
        if (spell == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown spell"));
            return 0;
        }
        float xp = FloatArgumentType.getFloat(ctx, "xp");
        SpellMasteryData.setXp(player, spell, xp);
        ctx.getSource().sendSuccess(() -> Component.literal(spell.getId() + " -> " + xp + " XP"), true);
        return 1;
    }

    private static int setRank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        SpellType spell = SpellType.fromId(StringArgumentType.getString(ctx, "spell"));
        if (spell == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown spell"));
            return 0;
        }
        int rank = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "rank");
        SpellMasteryData.setXp(player, spell, Config.getMasteryThresholds()[rank - 1]);
        ctx.getSource().sendSuccess(() -> Component.literal(spell.getId() + " -> rank " + MasteryMath.roman(rank)), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        SpellMasteryData.reset(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Reset spell mastery for " + player.getScoreboardName()), true);
        return 1;
    }
}
```
Check that `SpellType.fromId` returns `null` for an unknown id (`SpellType.java:462`). If it returns a default instead, compare `getId()` against the input before accepting it.

- [ ] **Step 2: Register it.** In `ExampleMod.onRegisterCommands`, next to line 870, add:
```java
        com.hogwartsmagic.command.SpellMasteryCommand.register(event.getDispatcher());
```

- [ ] **Step 3: Build, then check on the dedicated server**

Run: `./gradlew compileJava`, then `./gradlew runServer`. At the console, type `spellmastery get @p`.
Expected: with no player online, the console prints a "no player was found" style error, which proves the command registered. Stop the server.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/command/SpellMasteryCommand.java src/main/java/com/hogwartsmagic/ExampleMod.java
git commit -m "Add /spellmastery for checking and forcing spell ranks"
```

---

### Task 9: Show ranks in the Grimoire and on the spell wheel

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java` (`renderDetail`, lines 230–299)
- Modify: `src/main/java/com/hogwartsmagic/client/WandRadialMenuScreen.java` (slot drawing, line ~73)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SpellMasteryData.getXp/getRank` (reads synced NBT on the client), `MasteryMath.rankProgress/xpToNextRank/roman`, `MasteryAwards.rankKey`, `SpellCosts.manaCost/cooldownTicks`, `Config.getMasteryThresholds()`, `Config.getMasteryRankDiscount()`

- [ ] **Step 1: Grimoire.** In `renderDetail`, directly after the learned/locked status line (`g.drawString(this.font, status, ...); y += 16;`), insert:
```java
        if (learned) {
            float xp = com.hogwartsmagic.spell.mastery.SpellMasteryData.getXp(player, selected);
            int[] thresholds = com.hogwartsmagic.Config.getMasteryThresholds();
            int rank = com.hogwartsmagic.spell.mastery.MasteryMath.rankForXp(xp, thresholds);
            int rankColor = rank >= 4 ? COLOR_MASTER : rank == 3 ? COLOR_ADEPT : rank == 2 ? COLOR_APPRENTICE : COLOR_NOVICE;
            Component rankLine = Component.translatable("gui." + ExampleMod.MODID + ".mastery.rank_line",
                    com.hogwartsmagic.spell.mastery.MasteryMath.roman(rank),
                    Component.translatable(com.hogwartsmagic.spell.mastery.MasteryAwards.rankKey(rank)));
            g.drawString(this.font, rankLine, x, y, rankColor);
            y += 11;

            int barWidth = Math.min(120, wrap);
            float progress = com.hogwartsmagic.spell.mastery.MasteryMath.rankProgress(xp, thresholds);
            g.fill(x, y, x + barWidth, y + 4, 0xFF2A2340);
            g.fill(x, y, x + Math.round(barWidth * progress), y + 4, rankColor);
            y += 7;

            float toNext = com.hogwartsmagic.spell.mastery.MasteryMath.xpToNextRank(xp, thresholds);
            Component next = toNext < 0
                    ? Component.translatable("gui." + ExampleMod.MODID + ".mastery.maxed")
                    : Component.translatable("gui." + ExampleMod.MODID + ".mastery.next",
                            String.format("%.0f", Math.ceil(toNext)),
                            Math.round(com.hogwartsmagic.Config.getMasteryRankDiscount() * 100));
            for (FormattedCharSequence line : this.font.split(next, wrap)) {
                g.drawString(this.font, line, x, y, COLOR_BODY);
                y += 11;
            }
            y += 5;
        }
```
The ternary chain is on `int`s, not an enum switch, so it's allowed.

Then replace the mana and cooldown stat lines so they show what the player actually pays at their rank (without a wand, since the Grimoire isn't tied to the held wand):
```java
        Component mana = Component.translatable(
                "gui." + ExampleMod.MODID + ".grimoire.mana",
                com.hogwartsmagic.spell.SpellCosts.manaCost(player, net.minecraft.world.item.ItemStack.EMPTY, selected));
        g.drawString(this.font, mana, x, y, 0xFF8FC9FF);
        y += 12;
        String cd = String.format("%.1f",
                com.hogwartsmagic.spell.SpellCosts.cooldownTicks(player, net.minecraft.world.item.ItemStack.EMPTY, selected) / 20.0F);
```
Leave the rest of that block as it is. Note: with `ItemStack.EMPTY`, `MagicWandItem.getManaCostMultiplier` and `getCooldownMultiplier` fall through to `1.0f`, and the cooldown now includes the house multiplier, which is more honest than before.

- [ ] **Step 2: Spell wheel pip.** In `WandRadialMenuScreen`, in the occupied-slot branch right after:
```java
guiGraphics.drawCenteredString(this.font, initials, sx, sy - 4, 0xFFFFFFFF);
```
add:
```java
                int rank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(this.minecraft.player, spell);
                if (rank > 1) {
                    guiGraphics.drawCenteredString(this.font,
                            com.hogwartsmagic.spell.mastery.MasteryMath.roman(rank), sx, sy + 5, 0xFFFFE08A);
                }
```
Novice shows nothing, so a fresh wheel looks exactly as it does today. If `this.minecraft.player` could be null at that point, guard it with `if (this.minecraft != null && this.minecraft.player != null)`.

- [ ] **Step 3: Strings** (`en_us.json`):
```json
  "gui.hogwartsmagic.mastery.rank_line": "Rank %s: %s",
  "gui.hogwartsmagic.mastery.next": "%s XP to next rank (another -%s%% cost and cooldown, +1 max mana)",
  "gui.hogwartsmagic.mastery.maxed": "Mastered. This spell has nothing left to teach you.",
```

- [ ] **Step 4: Build**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java src/main/java/com/hogwartsmagic/client/WandRadialMenuScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Show spell ranks in the Grimoire and on the wand wheel"
```

---

### Task 10: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests and dedicated server**

Run: `./gradlew test`, then `./gradlew runServer` until `Done (`, then stop it.
Expected: all tests pass; the server boots with no stack trace mentioning `mastery`, `SpellCosts` or any `client` class.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed)

In a survival world with a wand and learned spells:
1. Open the Grimoire on a learned spell: it shows "Rank I: Novice", an empty bar and "30 XP to next rank".
2. Cast Stupefy into the sky: no XP (check with `/spellmastery get @s`).
3. Stupefy a zombie: +1 XP. Stupefy the same zombie again within 10s: no XP. Glacius the same zombie: +1 XP (a different spell pays).
4. Stupefy a cow: +0.25 XP.
5. Cast Lumos twice within 10s: only the first pays.
6. `/spellmastery setrank @s stupefy 4`: the Grimoire shows "Rank IV: Master" and the full-bar message. The wheel shows "IV" on Stupefy. Grimoire mana and cooldown are 24% lower than at Novice.
7. Get Stupefy across a rank boundary naturally (`/spellmastery set @s stupefy 29.5`, then hit a zombie): you see the gold chat line, hear the level-up chime and see the particles.
8. Max mana on the mana bar rose by the ranks gained (Stupefy at Master = +3).
9. Die and respawn: ranks are unchanged.
10. Creative mode: hitting a zombie gives no XP.
11. Hit a mob from a monster spawner: half XP.
12. Hit a Confringo on grass: no crater (Task 1).

- [ ] **Step 3: Record results.** Note any failed check and fix it in a follow-up commit with a message describing what was wrong. Then tell the user Phase 1 is ready to push.

---

## Self-Review

- **Spec coverage (roadmap, Phase 1):** Confringo fix (T1); central cost math (T6); XP storage and sync (T5); ranks and generic bonuses (T2, T6); capped mana (T2, T6); XP categories and anti-farm (T3, T7); spawner tagging (T7); rank-up feedback (T7); Grimoire and wheel (T9); config (T4); debug command (T8); dedicated-server check (T6, T7, T10); survives death (T10.9; Forge copies `PlayerPersisted`). Phases 2–7 are out of scope by design and get their own plans.
- **Placeholder scan:** the line numbers (`~`) are anchors that will drift as earlier tasks edit the file. Each one also names the code to look for.
- **Type consistency:** `getXp` returns `float` everywhere. `rankForXp(float, int[])`. `addXp` returns `int`. `startCooldown(ServerPlayer, ItemStack, SpellType, long[, double])`. `MasteryAwards.rankKey(int)` is used in T7 and T9. `SpellMasteryData.KEY` = `"SpellMastery"` matches the client bridge string in T5.
