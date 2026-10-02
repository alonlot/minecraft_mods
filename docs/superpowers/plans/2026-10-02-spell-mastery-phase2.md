# Spell Mastery Phase 2 (Shared Combat Rules) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Five shared combat effects (`stunned`, `bleeding`, `cursed_wound`, `brittle`, `silenced`) that fully work, one crowd-control entry point with diminishing returns, a boss rule and a PvP rule, and the leftover Phase 1 fixes (server mastery config on clients, max mana resync, PvP XP, spawner tagging, Reparo discount, a scrollable Grimoire pane and small cleanups).

**Architecture:** The numbers live in Minecraft-free classes with JUnit tests: `CrowdControlMath` (boss and PvP scaling), `DiminishingReturns` (the 6-second chain), `BleedRules`, `BrittleRules`, plus hardened `MasteryMath`, `MasteryRules` and `HitLedger`. The effects are ordinary registered `MobEffect`s. Their behaviour comes from three places: `CombatEffects` (Forge event hooks: heal halving, the brittle hit, milk protection, stun locks, NPC beam gate), three small mixins (stunned entities report `isImmobile()`, bleeding blocks natural regeneration), and cast-handler gates. `CrowdControl.apply(target, effect, baseTicks, source)` is the single door later phases use for hard crowd control. Clients read the effect instances vanilla already syncs to the local player, so the input lock and the greyed spell wheel need no new packet. Mastery config gets one new S2C packet and a `MasteryConfig` view that picks the server copy on the client.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`), SpongePowered Mixin (already set up), Python 3 + Pillow 10 for the icons.

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, sections "Phase 1 follow-ups (carried into Phase 2)" and "Phase 2: Shared combat rules", with numbers from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` ("Global balance rules", "New shared effects needed"). Where they disagree, the roadmap wins.

**In-flight work this plan avoids:** Phase 1b (Tomes of Mastery) is being built on this branch. This plan never edits `spell/mastery/TomeRules.java`, `item/TomeOfMasteryItem.java`, `client/TomePickerScreen.java`, `loot/TomeOfMasteryLootModifier.java`, the tome packets or `client/ClientRuntimeBridge.java`, and depends on none of them. Shared files (`Config.java`, `SpellPackets.java`, `ExampleMod.java`, `MasteryAwards.java`, `en_us.json`) are edited at anchors Phase 1b does not touch, quoted by text. If Phase 1b is still mid-flight when you start a task, finish or stash nothing of theirs: only stage the files your task names.

## Global Constraints

- **Effects (ids under `hogwartsmagic:`):**
  - `stunned`: no movement, attacks, item use or casting. Mobs: AI step skipped (goals, brain, navigation). Players: movement input zeroed on the server and on their own client.
  - `bleeding`: `amplifier + 1` damage per second (stacks 1..3, a higher amplifier from `/effect` is clamped to 3), no natural regeneration, all other healing ×0.5. Damage type `hogwartsmagic:bleeding` (bypasses armor, no knockback, death message "bled out").
  - `cursed_wound`: −2 max health per level (`amplifier + 1` hearts). Milk and any other drink or food cannot remove it; `/effect clear`, expiry and `CombatEffects.cureCursedWound` can.
  - `brittle`: the next explosion, projectile or armour-blockable melee hit deals ×(1 + 0.3 × level) damage, then the effect is removed. Level = `amplifier + 1`, capped at 5.
  - `silenced`: no casting (players and NPCs); spell wheel greyed.
  - Stun or silence landing on a player interrupts their active channel (`ModEvents.interruptActiveCasting`), and casts, channel starts and heartbeats are refused while it lasts.
- **Hard crowd control** goes through `CrowdControl.apply` only. Duration = base × diminishing factor × 0.5 if the target is in `#hogwartsmagic:bosses` × the PvP multiplier (default 0.6, config toggle) if a player's effect lands on another player. Diminishing returns per target: the first hard CC is full length, a second within 120 ticks (6s) of the previous landed one lasts 50%, then nothing until 120 ticks after that second one. Attempts that land nothing don't extend the window.
- **Mastery config on clients:** the server sends thresholds, rank discount, mana per rank and mana cap on login and after a config reload. Client-side reads use that copy; server-side reads always use the server's own config.
- **PvP XP rule:** a player target pays 0.5× XP, and a given caster earns from a given player at most once per 600 ticks (30s) whatever the spell, on top of the 10s same-spell rule. Team-mates still pay 0.
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/`, is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch or a `Dist.CLIENT` event subscriber, and talks back through C2S packets the server validates.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine.
- Screens: `renderTransparentBackground`, no blur.
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards. (This plan changes no terrain.)
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses none).
- `en_us.json` must stay valid JSON after every edit: every inserted line ends with a comma, and the insertion point is always after an existing line that already ends with a comma.

## File Structure

| File | Responsibility |
|---|---|
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java` | Stores expiry times; `isOpen` peek |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java` | NaN guards, threshold validation, clamps |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java` | Player-target multiplier and pair window |
| Create `src/main/java/com/hogwartsmagic/spell/mastery/MasteryConfig.java` | Server-or-synced view of the mastery config; send and broadcast |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/SpellMasteryData.java` | Reads through `MasteryConfig`; NaN-safe; resyncs mana on rank change |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | PvP XP rule, spawner fallback, shared allied constant |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellCosts.java` | Discount from `MasteryConfig` |
| Modify `src/main/java/com/hogwartsmagic/util/PlayerMagicData.java` | `resyncMana(ServerPlayer)` |
| Modify `src/main/java/com/hogwartsmagic/util/ReparoManager.java` | Per-block mana gets the mastery discount |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | Threshold warning, reload broadcast, PvP crowd-control values |
| Create `src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java` | Drops the synced copy on logout |
| Create `src/main/java/com/hogwartsmagic/spell/combat/CombatTags.java` | `ALLIED` tag string, `BOSSES` entity tag, `isBoss` |
| Create `src/main/java/com/hogwartsmagic/spell/combat/CrowdControlMath.java` | Pure: boss and PvP duration scaling |
| Create `src/main/java/com/hogwartsmagic/spell/combat/DiminishingReturns.java` | Pure: per-target hard-CC chain |
| Create `src/main/java/com/hogwartsmagic/spell/combat/BleedRules.java` | Pure: stacks, damage, heal multiplier |
| Create `src/main/java/com/hogwartsmagic/spell/combat/BrittleRules.java` | Pure: which hits consume brittle, bonus |
| Create `src/main/java/com/hogwartsmagic/spell/combat/CombatDamage.java` | The bleeding damage source |
| Create `src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java` | Effect queries, apply helpers, event hooks |
| Create `src/main/java/com/hogwartsmagic/spell/combat/CrowdControl.java` | The hard-CC entry point |
| Create `src/main/java/com/hogwartsmagic/spell/StunnedEffect.java`, `BleedingEffect.java`, `CursedWoundEffect.java` | Effect classes |
| Create `src/main/java/com/hogwartsmagic/mixin/LivingEntityStunMixin.java` | Stunned → `isImmobile()` |
| Create `src/main/java/com/hogwartsmagic/mixin/FoodDataBleedingMixin.java` | No food regeneration while bleeding |
| Create `src/main/java/com/hogwartsmagic/mixin/PlayerBleedingMixin.java` | No peaceful regeneration while bleeding |
| Modify `src/main/resources/hogwartsmagic.mixins.json` | Register the three mixins |
| Create `src/main/java/com/hogwartsmagic/client/StunInputLock.java` | Client input lock while stunned |
| Create `src/main/java/com/hogwartsmagic/command/CrowdControlCommand.java` | `/crowdcontrol stun/reset` (op) for testing |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Five effects; command registration |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `SyncMasteryConfigPacket`; cast, Imperio and Apparition gates; drop unused local |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Send mastery config on login; allied constant; client wand-use guard |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Allied constant |
| Modify `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java` | Client cast guard |
| Modify `src/main/java/com/hogwartsmagic/client/WandRadialMenuScreen.java` | Greyed wheel |
| Modify `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java` | Synced config reads, scrollable detail pane, rank colours |
| Create `build_scripts/generate_combat_effect_icons.py` | Paints the five effect icons |
| Create `src/main/resources/assets/hogwartsmagic/textures/mob_effect/{stunned,bleeding,cursed_wound,brittle,silenced}.png` | Generated icons (18x18, vanilla effect icon size) |
| Create `src/main/resources/data/hogwartsmagic/damage_type/bleeding.json` | Damage type |
| Create `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`, `no_knockback.json` | Damage type tags |
| Create `src/main/resources/data/hogwartsmagic/tags/entity_type/bosses.json` | Boss tag |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | New strings |
| Create/modify tests under `src/test/java/com/hogwartsmagic/spell/mastery/` and `src/test/java/com/hogwartsmagic/spell/combat/` | Unit tests |

---

### Task 1: Harden the pure mastery code

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java` (whole file)
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mastery/HitLedgerTest.java`, `MasteryMathTest.java`, `MasteryRulesTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `HitLedger.isOpen(String key, long now)` → `boolean` (no recording); `tryAward(String key, long now, long windowTicks)` unchanged signature, now stores `now + windowTicks`
  - `MasteryMath.thresholdsValid(int adept, int expert, int master)` → `boolean`
  - `MasteryMath.thresholdsOrDefault(int adept, int expert, int master)` → `int[4]` (a fresh array every call)
  - `MasteryMath.sanitizeXp(float)` → `float` (NaN, infinite or negative become 0)
  - `MasteryMath.clampFinite(double value, double min, double max, double fallback)` → `double`
  - `MasteryRules.PLAYER_TARGET_MULTIPLIER = 0.5`, `MasteryRules.PLAYER_PAIR_WINDOW_TICKS = 600L`
  - `MasteryRules.targetMultiplier(boolean allied, boolean passive, boolean fromSpawner, boolean player)` (the 3-argument form stays and delegates with `player = false`)

- [ ] **Step 1: Write the failing tests**

In `HitLedgerTest.java`, replace the whole `oldEntriesArePrunedOnceLarge` method:
```java
    @Test
    void oldEntriesArePrunedOnceLarge() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("k" + i, 0L, 200L);
        }
        ledger.tryAward("fresh", HitLedger.PRUNE_AGE_TICKS + 1, 200L);
        assertEquals(1, ledger.size());
    }
```
with:
```java
    @Test
    void expiredEntriesArePrunedOnceLarge() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("k" + i, 0L, 200L);
        }
        ledger.tryAward("fresh", 200L, 200L);
        assertEquals(1, ledger.size());
    }

    @Test
    void liveEntriesSurvivePruning() {
        HitLedger ledger = new HitLedger();
        for (int i = 0; i < 300; i++) {
            ledger.tryAward("long" + i, 0L, 12000L);
        }
        ledger.tryAward("fresh", 200L, 200L);
        assertEquals(301, ledger.size());
        assertFalse(ledger.tryAward("long7", 11999L, 200L));
        assertTrue(ledger.tryAward("long7", 12000L, 200L));
    }

    @Test
    void isOpenDoesNotRecord() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.isOpen("k", 0L));
        assertTrue(ledger.isOpen("k", 0L));
        assertTrue(ledger.tryAward("k", 0L, 200L));
        assertFalse(ledger.isOpen("k", 199L));
        assertTrue(ledger.isOpen("k", 200L));
    }
```

In `MasteryMathTest.java`, replace the import line
```java
import static org.junit.jupiter.api.Assertions.assertEquals;
```
with:
```java
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
```
and add these methods just before the class's final closing brace:
```java
    @Test
    void scaledXpRejectsNaNAndInfinity() {
        assertEquals(0f, MasteryMath.scaledXp(5, Double.NaN), 1e-4);
        assertEquals(0f, MasteryMath.scaledXp(5, Double.POSITIVE_INFINITY), 1e-4);
        assertEquals(0f, MasteryMath.scaledXp(-5, 1.0), 1e-4);
    }

    @Test
    void sanitizeXpZeroesGarbage() {
        assertEquals(0f, MasteryMath.sanitizeXp(Float.NaN), 1e-4);
        assertEquals(0f, MasteryMath.sanitizeXp(Float.POSITIVE_INFINITY), 1e-4);
        assertEquals(0f, MasteryMath.sanitizeXp(-3f), 1e-4);
        assertEquals(42.5f, MasteryMath.sanitizeXp(42.5f), 1e-4);
    }

    @Test
    void thresholdsFallBackUnlessStrictlyRising() {
        assertTrue(MasteryMath.thresholdsValid(30, 120, 350));
        assertFalse(MasteryMath.thresholdsValid(120, 120, 350));
        assertFalse(MasteryMath.thresholdsValid(0, 120, 350));
        assertArrayEquals(new int[]{0, 10, 20, 30}, MasteryMath.thresholdsOrDefault(10, 20, 30));
        assertArrayEquals(new int[]{0, 30, 120, 350}, MasteryMath.thresholdsOrDefault(50, 40, 30));
    }

    @Test
    void defaultThresholdsCannotBeMutatedThroughTheReturnedArray() {
        int[] first = MasteryMath.thresholdsOrDefault(50, 40, 30);
        first[1] = 999;
        assertArrayEquals(new int[]{0, 30, 120, 350}, MasteryMath.thresholdsOrDefault(50, 40, 30));
    }

    @Test
    void rankProgressStaysInsideZeroToOne() {
        assertEquals(0f, MasteryMath.rankProgress(Float.NaN, T), 1e-4);
        assertEquals(0f, MasteryMath.rankProgress(-50f, T), 1e-4);
        assertEquals(0f, MasteryMath.rankProgress(Float.POSITIVE_INFINITY, T), 1e-4);
    }

    @Test
    void clampFiniteFallsBackOnNaN() {
        assertEquals(0.08, MasteryMath.clampFinite(Double.NaN, 0.0, 0.3, 0.08), 1e-9);
        assertEquals(0.08, MasteryMath.clampFinite(Double.NEGATIVE_INFINITY, 0.0, 0.3, 0.08), 1e-9);
        assertEquals(0.3, MasteryMath.clampFinite(5.0, 0.0, 0.3, 0.08), 1e-9);
        assertEquals(0.0, MasteryMath.clampFinite(-1.0, 0.0, 0.3, 0.08), 1e-9);
        assertEquals(0.1, MasteryMath.clampFinite(0.1, 0.0, 0.3, 0.08), 1e-9);
    }
```
(In `rankProgressStaysInsideZeroToOne`, NaN, negative and infinite XP are all sanitized to 0, so progress is 0.)

In `MasteryRulesTest.java`, add before the final closing brace:
```java
    @Test
    void playersPayHalfAndAlliedPlayersNothing() {
        assertEquals(0.5, MasteryRules.targetMultiplier(false, false, false, true), 1e-9);
        assertEquals(0.0, MasteryRules.targetMultiplier(true, false, false, true), 1e-9);
        assertEquals(0.25, MasteryRules.targetMultiplier(false, true, false, true), 1e-9);
        assertEquals(1.0, MasteryRules.targetMultiplier(false, false, false, false), 1e-9);
    }

    @Test
    void playerPairWindowIsThirtySeconds() {
        assertEquals(600L, MasteryRules.PLAYER_PAIR_WINDOW_TICKS);
    }
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.mastery.*"`
Expected: compile FAIL, `cannot find symbol` for `isOpen`, `sanitizeXp`, `thresholdsValid`, `thresholdsOrDefault`, `clampFinite`, `PLAYER_PAIR_WINDOW_TICKS` and the 4-argument `targetMultiplier`.

- [ ] **Step 3: Implement `HitLedger`** (replace the whole file)
```java
package com.hogwartsmagic.spell.mastery;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers until when each award key is closed, so repeat hits inside a window earn nothing.
 * Storing the expiry (not the award time) makes pruning exact for every window length: an entry
 * is dropped the moment it can no longer refuse anything.
 */
public final class HitLedger {
    private static final int PRUNE_AT_SIZE = 256;

    private final Map<String, Long> closedUntil = new HashMap<>();

    /** True if {@code key} would pay out at {@code now}. Records nothing. */
    public boolean isOpen(String key, long now) {
        Long until = closedUntil.get(key);
        return until == null || now >= until;
    }

    public boolean tryAward(String key, long now, long windowTicks) {
        if (!isOpen(key, now)) {
            return false;
        }
        if (closedUntil.size() >= PRUNE_AT_SIZE) {
            closedUntil.values().removeIf(until -> now >= until);
        }
        closedUntil.put(key, now + windowTicks);
        return true;
    }

    public int size() {
        return closedUntil.size();
    }
}
```

- [ ] **Step 4: Implement the `MasteryMath` changes**

Add, directly under `private static final String[] ROMAN = {"I", "II", "III", "IV"};`:
```java
    private static final int[] DEFAULT_THRESHOLDS = {0, 30, 120, 350};
```
Replace the whole `rankProgress` method:
```java
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
```
with:
```java
    /** How far through the current rank's band, always 0..1; 1 at Master. Drives the Grimoire bar. */
    public static float rankProgress(float xp, int[] thresholds) {
        float safeXp = sanitizeXp(xp);
        int rank = rankForXp(safeXp, thresholds);
        if (rank >= MAX_RANK) {
            return 1f;
        }
        float low = thresholds[rank - 1];
        float span = thresholds[rank] - low;
        if (!(span > 0f)) {
            return 1f;
        }
        float progress = (safeXp - low) / span;
        return Float.isNaN(progress) ? 0f : Math.max(0f, Math.min(1f, progress));
    }
```
Replace the whole `scaledXp` method:
```java
    public static float scaledXp(int baseXp, double multiplier) {
        if (multiplier <= 0.0) {
            return 0f;
        }
        return (float) (baseXp * multiplier);
    }
```
with:
```java
    /** Zero for a non-positive, NaN or infinite multiplier, and never negative. */
    public static float scaledXp(int baseXp, double multiplier) {
        if (!(multiplier > 0.0) || Double.isInfinite(multiplier)) {
            return 0f;
        }
        return sanitizeXp((float) (baseXp * multiplier));
    }

    /** Stored XP that is NaN, infinite or negative counts as 0. */
    public static float sanitizeXp(float xp) {
        if (Float.isNaN(xp) || Float.isInfinite(xp) || xp < 0f) {
            return 0f;
        }
        return xp;
    }

    public static boolean thresholdsValid(int adept, int expert, int master) {
        return adept > 0 && adept < expert && expert < master;
    }

    /** {0, adept, expert, master}, or a fresh copy of the defaults if those aren't strictly rising. */
    public static int[] thresholdsOrDefault(int adept, int expert, int master) {
        if (thresholdsValid(adept, expert, master)) {
            return new int[]{0, adept, expert, master};
        }
        return DEFAULT_THRESHOLDS.clone();
    }

    /** Clamp to [min, max]; NaN and infinities become {@code fallback}. Used on values a server sends. */
    public static double clampFinite(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
```

- [ ] **Step 5: Implement the `MasteryRules` changes**

Add under `private static final long MIN_GATE_TICKS = 200L;`:
```java
    /** A player target pays half: PvP still teaches, but a partner is a poor training dummy. */
    public static final double PLAYER_TARGET_MULTIPLIER = 0.5;
    /** One caster earns from one player at most once per this many ticks, whatever the spell. */
    public static final long PLAYER_PAIR_WINDOW_TICKS = 600L;
```
Replace the whole `targetMultiplier` method:
```java
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
```
with:
```java
    public static double targetMultiplier(boolean allied, boolean passive, boolean fromSpawner) {
        return targetMultiplier(allied, passive, fromSpawner, false);
    }

    public static double targetMultiplier(boolean allied, boolean passive, boolean fromSpawner, boolean player) {
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
        if (player) {
            multiplier = Math.min(multiplier, PLAYER_TARGET_MULTIPLIER);
        }
        return multiplier;
    }
```

- [ ] **Step 6: Run them and confirm they pass**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.mastery.*"`
Expected: PASS. `MasteryMathTest` 14 tests, `MasteryRulesTest` 5, `HitLedgerTest` 6 (plus `TomeRulesTest` if Phase 1b has landed).

- [ ] **Step 7: Compile the mod** (nothing else referenced `PRUNE_AGE_TICKS`)

Run: `tasklist | grep -i javaw`, then `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mastery/HitLedger.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryMath.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryRules.java src/test/java/com/hogwartsmagic/spell/mastery/HitLedgerTest.java src/test/java/com/hogwartsmagic/spell/mastery/MasteryMathTest.java src/test/java/com/hogwartsmagic/spell/mastery/MasteryRulesTest.java
git commit -m "Harden mastery math against NaN and make the hit ledger prune exactly" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Server mastery config on clients, and max mana right after rank changes

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryConfig.java`
- Create: `src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/SpellMasteryData.java` (whole file)
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellCosts.java` (`masteryMultiplier`)
- Modify: `src/main/java/com/hogwartsmagic/util/PlayerMagicData.java` (new method)
- Modify: `src/main/java/com/hogwartsmagic/Config.java` (logger, `getMasteryThresholds`, end of `onLoad`)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (builder after the `SyncSpellMasteryPacket` builder; record after the `SyncSpellMasteryPacket` record)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`onPlayerJoin`)
- Modify: `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java` (three config reads and the bar clamp)

**Interfaces:**
- Consumes: `MasteryMath.thresholdsOrDefault/thresholdsValid/clampFinite/sanitizeXp` (Task 1), `Config.masteryXpAdept/masteryXpExpert/masteryXpMaster` (public static fields), `Config.getMasteryRankDiscount/getMasteryManaPerRank/getMasteryManaCap`
- Produces:
  - `MasteryConfig.Values(int adept, int expert, int master, double rankDiscount, double manaPerRank, double manaCap)` with `int[] thresholds()`
  - `MasteryConfig.server()`, `MasteryConfig.forSide(boolean clientSide)`, `MasteryConfig.of(Player)` → `Values`
  - `MasteryConfig.acceptSynced(Values)`, `MasteryConfig.clearSynced()`, `MasteryConfig.sendTo(ServerPlayer)`, `MasteryConfig.broadcastAfterReload()`
  - `SpellPackets.SyncMasteryConfigPacket(int adept, int expert, int master, double rankDiscount, double manaPerRank, double manaCap)`
  - `PlayerMagicData.resyncMana(ServerPlayer)`: recompute max mana, clamp mana, save, send `SyncManaPacket`
  - `Config.getMasteryThresholds()` keeps its signature

- [ ] **Step 1: Add `PlayerMagicData.resyncMana`.** In `PlayerMagicData.java`, add directly after the `getMaxMana` method:
```java
    /**
     * Recompute max mana, clamp current mana to it, save both and tell the client. Called right after
     * anything that changes max mana at once (a rank change, a mastery config reload), so the bar never
     * waits for the next regen tick and mana never sits above a lowered max.
     */
    public static void resyncMana(net.minecraft.server.level.ServerPlayer player) {
        CompoundTag modData = getModData(player);
        float maxMana = getMaxMana(player);
        float mana = modData.contains("Mana") ? Math.min(modData.getFloat("Mana"), maxMana) : maxMana;
        modData.putFloat("MaxMana", maxMana);
        modData.putFloat("Mana", mana);
        saveModData(player, modData);
        com.hogwartsmagic.network.SpellPackets.CHANNEL.send(
                new com.hogwartsmagic.network.SpellPackets.SyncManaPacket(mana, maxMana),
                net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
    }
```

- [ ] **Step 2: Create `MasteryConfig`**
```java
package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * The mastery numbers every rank and cost calculation reads. The config file is COMMON, so a client
 * has its own copy that can disagree with the server's. The server sends its values on login and
 * after a config reload ({@link SpellPackets.SyncMasteryConfigPacket}); client-side reads (Grimoire,
 * spell wheel, cost pre-check) use that copy, server-side reads always use the server's own config.
 * Pure Java plus common Minecraft types: safe on a dedicated server.
 */
public final class MasteryConfig {
    public record Values(int adept, int expert, int master, double rankDiscount, double manaPerRank, double manaCap) {
        /** Validated thresholds: the defaults if the three values aren't strictly rising. */
        public int[] thresholds() {
            return MasteryMath.thresholdsOrDefault(adept, expert, master);
        }
    }

    /** The copy the server sent; null until the first sync and after logout. Client side only. */
    private static volatile Values synced;

    private MasteryConfig() {
    }

    public static Values server() {
        return new Values(Config.masteryXpAdept, Config.masteryXpExpert, Config.masteryXpMaster,
                Config.getMasteryRankDiscount(), Config.getMasteryManaPerRank(), Config.getMasteryManaCap());
    }

    public static Values forSide(boolean clientSide) {
        Values copy = synced;
        return clientSide && copy != null ? copy : server();
    }

    public static Values of(Player player) {
        return forSide(player.level().isClientSide());
    }

    /** Client: store the server's values, clamped to the same ranges the config spec allows. */
    public static void acceptSynced(Values values) {
        synced = new Values(values.adept(), values.expert(), values.master(),
                MasteryMath.clampFinite(values.rankDiscount(), 0.0, 0.3, 0.08),
                MasteryMath.clampFinite(values.manaPerRank(), 0.0, 100.0, 1.0),
                MasteryMath.clampFinite(values.manaCap(), 0.0, 100000.0, 50.0));
    }

    public static void clearSynced() {
        synced = null;
    }

    public static void sendTo(ServerPlayer player) {
        Values values = server();
        SpellPackets.CHANNEL.send(new SpellPackets.SyncMasteryConfigPacket(values.adept(), values.expert(),
                        values.master(), values.rankDiscount(), values.manaPerRank(), values.manaCap()),
                PacketDistributor.PLAYER.with(player));
    }

    /**
     * After the config file is edited and reloaded: resend the values and the (possibly changed) max
     * mana to everyone online. ModConfigEvent.Reloading can arrive on the config watcher thread, so the
     * work is handed to the server thread. No server running (main menu) means nothing to do.
     */
    public static void broadcastAfterReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendTo(player);
                PlayerMagicData.resyncMana(player);
            }
        });
    }
}
```

- [ ] **Step 3: Add the packet.** In `SpellPackets.java`, directly after the `SyncSpellMasteryPacket` builder in `register()`:
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
add:
```java
        CHANNEL.messageBuilder(SyncMasteryConfigPacket.class)
                .encoder((p, buf) -> {
                    buf.writeVarInt(p.adept);
                    buf.writeVarInt(p.expert);
                    buf.writeVarInt(p.master);
                    buf.writeDouble(p.rankDiscount);
                    buf.writeDouble(p.manaPerRank);
                    buf.writeDouble(p.manaCap);
                })
                .decoder(buf -> new SyncMasteryConfigPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()))
                .consumerMainThread(SyncMasteryConfigPacket::handle)
                .add();
```
Directly after the `SyncSpellMasteryPacket` record:
```java
    public record SyncSpellMasteryPacket(net.minecraft.nbt.CompoundTag mastery) {
        public static void handle(SyncSpellMasteryPacket p, CustomPayloadEvent.Context ctx) {
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncSpellMastery", p));
        }
    }
```
add:
```java
    /** Server → client: the server's mastery numbers, so client-side ranks and costs match the server. */
    public record SyncMasteryConfigPacket(int adept, int expert, int master, double rankDiscount, double manaPerRank, double manaCap) {
        public static void handle(SyncMasteryConfigPacket p, CustomPayloadEvent.Context ctx) {
            // MasteryConfig is common code with no client imports, so no bridge hop is needed.
            enqueueClientWork(ctx, () -> com.hogwartsmagic.spell.mastery.MasteryConfig.acceptSynced(
                    new com.hogwartsmagic.spell.mastery.MasteryConfig.Values(
                            p.adept, p.expert, p.master, p.rankDiscount, p.manaPerRank, p.manaCap)));
        }
    }
```

- [ ] **Step 4: Rewrite `SpellMasteryData`** (replace the whole file)
```java
package com.hogwartsmagic.spell.mastery;

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
 * (the client copy arrives via {@link SpellPackets.SyncSpellMasteryPacket}, and the thresholds and
 * mana numbers come from {@link MasteryConfig}, which is the server's copy on the client); writes are
 * server-only. Any rank change resends max mana at once.
 */
public final class SpellMasteryData {
    public static final String KEY = "SpellMastery";

    private SpellMasteryData() {
    }

    private static CompoundTag tag(Player player) {
        return PlayerMagicData.getModData(player).getCompound(KEY);
    }

    public static float getXp(Player player, SpellType spell) {
        return MasteryMath.sanitizeXp(tag(player).getFloat(spell.getId()));
    }

    public static int getRank(Player player, SpellType spell) {
        return MasteryMath.rankForXp(getXp(player, spell), MasteryConfig.of(player).thresholds());
    }

    public static int getRanksGained(Player player) {
        CompoundTag tag = tag(player);
        int[] thresholds = MasteryConfig.of(player).thresholds();
        int total = 0;
        for (String id : tag.getAllKeys()) {
            total += MasteryMath.rankForXp(MasteryMath.sanitizeXp(tag.getFloat(id)), thresholds) - MasteryMath.NOVICE;
        }
        return total;
    }

    public static float getMaxManaBonus(Player player) {
        MasteryConfig.Values config = MasteryConfig.of(player);
        return MasteryMath.maxManaBonus(getRanksGained(player), config.manaPerRank(), config.manaCap());
    }

    public static int addXp(ServerPlayer player, SpellType spell, float amount) {
        if (!(amount > 0f) || Float.isInfinite(amount)) {
            return 0;
        }
        float before = getXp(player, spell);
        float after = MasteryMath.sanitizeXp(before + amount);
        int[] thresholds = MasteryConfig.of(player).thresholds();
        int oldRank = MasteryMath.rankForXp(before, thresholds);
        int newRank = MasteryMath.rankForXp(after, thresholds);
        write(player, spell, after);
        return newRank > oldRank ? newRank : 0;
    }

    public static void setXp(ServerPlayer player, SpellType spell, float xp) {
        write(player, spell, MasteryMath.sanitizeXp(xp));
    }

    public static void reset(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        modData.remove(KEY);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
        PlayerMagicData.resyncMana(player);
    }

    public static void sync(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.SyncSpellMasteryPacket(tag(player).copy()),
                PacketDistributor.PLAYER.with(player));
    }

    private static void write(ServerPlayer player, SpellType spell, float xp) {
        int oldRank = getRank(player, spell);
        CompoundTag modData = PlayerMagicData.getModData(player);
        CompoundTag tag = modData.getCompound(KEY);
        tag.putFloat(spell.getId(), xp);
        modData.put(KEY, tag);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
        if (getRank(player, spell) != oldRank) {
            // Up (natural, tome, command) or down (command): the max mana bonus moved with it.
            PlayerMagicData.resyncMana(player);
        }
    }
}
```

- [ ] **Step 5: `SpellCosts` reads the discount through `MasteryConfig`.** In `SpellCosts.java`, replace:
```java
    public static float masteryMultiplier(Player player, SpellType spell) {
        return MasteryMath.costMultiplier(SpellMasteryData.getRank(player, spell), Config.getMasteryRankDiscount());
    }
```
with:
```java
    public static float masteryMultiplier(Player player, SpellType spell) {
        return MasteryMath.costMultiplier(SpellMasteryData.getRank(player, spell),
                com.hogwartsmagic.spell.mastery.MasteryConfig.of(player).rankDiscount());
    }
```
Then delete the now-unused line `import com.hogwartsmagic.Config;` at the top of `SpellCosts.java`.

- [ ] **Step 6: `Config` warns on bad thresholds and broadcasts after a reload**

Directly under `private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();` add:
```java
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
```
Replace the `getMasteryThresholds` method:
```java
    /** Cumulative XP per rank, index 0 = Novice. Falls back to defaults if the config isn't strictly rising. */
    public static int[] getMasteryThresholds() {
        if (masteryXpAdept < masteryXpExpert && masteryXpExpert < masteryXpMaster) {
            return new int[]{0, masteryXpAdept, masteryXpExpert, masteryXpMaster};
        }
        return new int[]{0, 30, 120, 350};
    }
```
with:
```java
    /** Cumulative XP per rank, index 0 = Novice. Falls back to defaults (with a warning at load) if the config isn't strictly rising. */
    public static int[] getMasteryThresholds() {
        return com.hogwartsmagic.spell.mastery.MasteryMath.thresholdsOrDefault(masteryXpAdept, masteryXpExpert, masteryXpMaster);
    }
```
At the end of `onLoad`, directly above its closing brace (the brace just before the blank line that precedes `public static float getBaseMaxMana() {`), add:
```java
        if (!com.hogwartsmagic.spell.mastery.MasteryMath.thresholdsValid(masteryXpAdept, masteryXpExpert, masteryXpMaster)) {
            LOGGER.warn("Spell mastery XP thresholds {} / {} / {} are not strictly rising; using the defaults 30 / 120 / 350.",
                    masteryXpAdept, masteryXpExpert, masteryXpMaster);
        }
        if (event instanceof ModConfigEvent.Reloading) {
            com.hogwartsmagic.spell.mastery.MasteryConfig.broadcastAfterReload();
        }
```
(Phase 1b may have added tome assignments at the end of `onLoad`; these lines go after them.)

- [ ] **Step 7: Send the config on login.** In `ModEvents.onPlayerJoin`, replace:
```java
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);
            HousePerks.applyHouseAttributes(player);
```
with:
```java
            com.hogwartsmagic.spell.mastery.MasteryConfig.sendTo(player);
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);
            HousePerks.applyHouseAttributes(player);
```
Respawn and dimension change don't need it: the client keeps its copy until logout.

- [ ] **Step 8: Drop the synced copy on logout.** Create `src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mastery.MasteryConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: a mastery config synced by the last server must not leak into the next world. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MasteryConfigClientEvents {
    private MasteryConfigClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MasteryConfig.clearSynced();
    }
}
```

- [ ] **Step 9: The Grimoire reads the synced copy and clamps its bar.** In `SpellCompendiumScreen.java`:

Replace:
```java
            g.renderComponentTooltip(this.font, List.of(Component.translatable(
                    "gui." + ExampleMod.MODID + ".mastery.tooltip",
                    Math.round(com.hogwartsmagic.Config.getMasteryRankDiscount() * 100),
                    String.format("%.0f", com.hogwartsmagic.Config.getMasteryManaPerRank()))), mouseX, mouseY);
```
with:
```java
            com.hogwartsmagic.spell.mastery.MasteryConfig.Values mastery =
                    com.hogwartsmagic.spell.mastery.MasteryConfig.of(player);
            g.renderComponentTooltip(this.font, List.of(Component.translatable(
                    "gui." + ExampleMod.MODID + ".mastery.tooltip",
                    Math.round(mastery.rankDiscount() * 100),
                    String.format("%.0f", mastery.manaPerRank()))), mouseX, mouseY);
```
Replace:
```java
            int[] thresholds = com.hogwartsmagic.Config.getMasteryThresholds();
```
with:
```java
            int[] thresholds = com.hogwartsmagic.spell.mastery.MasteryConfig.of(player).thresholds();
```
Replace:
```java
            float progress = com.hogwartsmagic.spell.mastery.MasteryMath.rankProgress(xp, thresholds);
```
with:
```java
            float progress = Math.max(0f, Math.min(1f,
                    com.hogwartsmagic.spell.mastery.MasteryMath.rankProgress(xp, thresholds)));
```

- [ ] **Step 10: Check nothing on the client still reads the mastery config directly**

Run: `grep -rn "getMasteryThresholds\|getMasteryRankDiscount\|getMasteryManaPerRank\|getMasteryManaCap" src/main/java/com/hogwartsmagic/client`
Expected: no output. (Server-side callers such as `SpellMasteryCommand` and `TomeOfMasteryItem` keep using `Config` directly, which is correct on the server.)

- [ ] **Step 11: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Then run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: no stack trace mentioning `MasteryConfig`, `SyncMasteryConfigPacket` or any `client` class.

- [ ] **Step 12: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mastery/MasteryConfig.java src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java src/main/java/com/hogwartsmagic/spell/mastery/SpellMasteryData.java src/main/java/com/hogwartsmagic/spell/SpellCosts.java src/main/java/com/hogwartsmagic/util/PlayerMagicData.java src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java
git commit -m "Sync the server mastery config to clients and resend max mana on rank changes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: XP and cost follow-ups (PvP XP, spawner fallback, allied constant, Reparo, cast handler)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/combat/CombatTags.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` (`ALLIED_TAG`, `onLivingHit`, `isAllied`, spawner handlers)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (allied target check, ~line 3859)
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (two `addTag` calls, ~lines 283 and 429)
- Modify: `src/main/java/com/hogwartsmagic/util/ReparoManager.java` (`castReparo`)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.handle`)

**Interfaces:**
- Consumes: `HitLedger.isOpen` (Task 1), `MasteryRules.targetMultiplier(4 args)`, `MasteryRules.PLAYER_PAIR_WINDOW_TICKS` (Task 1), `SpellCosts.masteryMultiplier(Player, SpellType)`
- Produces: `CombatTags.ALLIED = "hogwartsmagic:allied"` (Task 8 adds `BOSSES` and `isBoss` to the same class)

- [ ] **Step 1: Create `CombatTags`**
```java
package com.hogwartsmagic.spell.combat;

/** Tags shared by the combat and mastery rules, in one place so the spellings can't drift. */
public final class CombatTags {
    /** Scoreboard tag on a mob fighting for a caster (Imperio thralls, Serpensortia snakes). */
    public static final String ALLIED = "hogwartsmagic:allied";

    private CombatTags() {
    }
}
```

- [ ] **Step 2: Use it everywhere.**
  - `MasteryAwards.java`: delete the line `    private static final String ALLIED_TAG = "hogwartsmagic:allied";` and in `isAllied` replace `target.getTags().contains(ALLIED_TAG)` with `target.getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED)`.
  - `ModEvents.java`: replace
    ```java
        if (event.getEntity().getTags().contains("hogwartsmagic:allied")) {
            if (event.getNewTarget() != null && event.getNewTarget().getTags().contains("hogwartsmagic:allied")) {
    ```
    with
    ```java
        if (event.getEntity().getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED)) {
            if (event.getNewTarget() != null && event.getNewTarget().getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED)) {
    ```
  - `SpellType.java`: replace `mob.addTag("hogwartsmagic:allied");` with `mob.addTag(com.hogwartsmagic.spell.combat.CombatTags.ALLIED);` and `snake.addTag("hogwartsmagic:allied");` with `snake.addTag(com.hogwartsmagic.spell.combat.CombatTags.ALLIED);`.

Run: `grep -rn "\"hogwartsmagic:allied\"" src/main/java`
Expected: exactly one hit, in `CombatTags.java`.

- [ ] **Step 3: The PvP XP rule.** In `MasteryAwards.onLivingHit`, replace:
```java
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
```
with:
```java
        boolean playerTarget = target instanceof Player;
        double multiplier = MasteryRules.targetMultiplier(
                isAllied(caster, target), isPassive(target), target.getTags().contains(SPAWNER_TAG), playerTarget);
        if (multiplier <= 0.0) {
            return;
        }
        HitLedger ledger = ledger(caster);
        long now = caster.level().getGameTime();
        String key = "hit:" + spell.getId() + ":" + target.getUUID();
        // PvP: one player pays a given caster at most once per PLAYER_PAIR_WINDOW_TICKS whatever the
        // spell, so two friends can't farm each other by cycling through their spell wheel.
        String pairKey = playerTarget ? "pvp:" + target.getUUID() : null;
        if (!ledger.isOpen(key, now) || (pairKey != null && !ledger.isOpen(pairKey, now))) {
            return;
        }
        ledger.tryAward(key, now, MasteryRules.SAME_TARGET_WINDOW_TICKS);
        if (pairKey != null) {
            ledger.tryAward(pairKey, now, MasteryRules.PLAYER_PAIR_WINDOW_TICKS);
        }
        grant(caster, spell, multiplier);
```

- [ ] **Step 4: Spawner tagging that can't be skipped.** Forge 51 already fires `FinalizeSpawn` for every spawner mob, custom-NBT ones included (its `BaseSpawner` patch fires it unconditionally), but another mod cancelling it would hide it from a normal listener. In `MasteryAwards.java`, replace:
```java
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
```
with:
```java
    /** receiveCanceled: another mod cancelling FinalizeSpawn must not make a spawner mob pay full XP. */
    @SubscribeEvent(receiveCanceled = true)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
```
and add directly after that method:
```java
    /**
     * Fallback tag. PositionCheck fires for every spawner attempt before FinalizeSpawn, whatever NBT
     * the spawner carries. A mob that then fails the check is discarded, so tagging it is harmless.
     */
    @SubscribeEvent(receiveCanceled = true)
    public static void onSpawnerPositionCheck(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWNER || type == MobSpawnType.TRIAL_SPAWNER) {
            event.getEntity().addTag(SPAWNER_TAG);
        }
    }
```

- [ ] **Step 5: Reparo per-block mana gets the discount.** In `ReparoManager.castReparo`, replace:
```java
        float maxMana = player != null ? PlayerMagicData.getMaxMana(player) : 0f;
        int repairedCount = 0;
```
with:
```java
        float maxMana = player != null ? PlayerMagicData.getMaxMana(player) : 0f;
        // The per-block surcharge gets the same mastery discount as the cast itself.
        float manaPerBlock = player != null
                ? EXTRA_MANA_PER_BLOCK * com.hogwartsmagic.spell.SpellCosts.masteryMultiplier(player, SpellType.REPARO)
                : EXTRA_MANA_PER_BLOCK;
        int repairedCount = 0;
```
then replace `availableMana < EXTRA_MANA_PER_BLOCK` with `availableMana < manaPerBlock`, and `availableMana -= EXTRA_MANA_PER_BLOCK;` with `availableMana -= manaPerBlock;`.

Run: `grep -n "EXTRA_MANA_PER_BLOCK" src/main/java/com/hogwartsmagic/util/ReparoManager.java`
Expected: three hits only (the constant and the two arms of `manaPerBlock`).

- [ ] **Step 6: Drop the unused local.** In `SpellPackets.CastSpellPacket.handle`, delete this line and the blank line after it:
```java
            long cooldownTicks = SpellCosts.cooldownTicks(player, wand, spell);
```

- [ ] **Step 7: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/combat/CombatTags.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/util/ReparoManager.java src/main/java/com/hogwartsmagic/network/SpellPackets.java
git commit -m "Halve PvP mastery XP, back up spawner tagging and discount Reparo per block" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Scrollable Grimoire detail pane and rank colours

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java`

**Interfaces:**
- Consumes: Task 2's version of this file.
- Produces: nothing used elsewhere.

- [ ] **Step 1: Constants and fields.** Directly after `private static final int COLOR_BODY = 0xFFD8C9F0;` add:
```java

    // Rank accent colours (ARGB) for the mastery rank line and XP bar. Same hues as the tiers on
    // purpose, but named for the rank they mean so the two scales can change independently.
    private static final int RANK_COLOR_NOVICE = 0xFF9BE38B;
    private static final int RANK_COLOR_ADEPT = 0xFF6FC2FF;
    private static final int RANK_COLOR_EXPERT = 0xFFC79BFF;
    private static final int RANK_COLOR_MASTER = 0xFFFF7A6B;
    private static final int DETAIL_LINE = 11;
```
Directly after `private float scroll;` add:
```java
    private float detailScroll;
    private int detailContentHeight;
    private int detailTop;
    private int detailBottom;
```

- [ ] **Step 2: A rank colour helper.** Directly after the `tierColor` method add:
```java
    private static int rankColor(int rank) {
        if (rank >= 4) {
            return RANK_COLOR_MASTER;
        } else if (rank == 3) {
            return RANK_COLOR_EXPERT;
        } else if (rank == 2) {
            return RANK_COLOR_ADEPT;
        }
        return RANK_COLOR_NOVICE;
    }
```
In `renderDetailBody`, replace:
```java
            int rankColor = rank >= 4 ? COLOR_MASTER : rank == 3 ? COLOR_ADEPT : rank == 2 ? COLOR_APPRENTICE : COLOR_NOVICE;
```
with:
```java
            int rankColor = rankColor(rank);
```

- [ ] **Step 3: Scroll handling.** Replace the whole `mouseScrolled` method:
```java
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = clampScroll(scroll - (float) deltaY * ROW_HEIGHT * 2.0F);
        return true;
    }
```
with:
```java
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int divX = panelLeft + 12 + LIST_WIDTH + 6;
        if (mouseX > divX) {
            // Over the detail column: long entries (Apparition's source list) scroll here.
            detailScroll = clampDetailScroll(detailScroll - (float) deltaY * DETAIL_LINE * 2.0F);
            return true;
        }
        scroll = clampScroll(scroll - (float) deltaY * ROW_HEIGHT * 2.0F);
        return true;
    }

    private float maxDetailScroll() {
        return Math.max(0.0F, detailContentHeight - (detailBottom - detailTop));
    }

    private float clampDetailScroll(float value) {
        return Math.max(0.0F, Math.min(value, maxDetailScroll()));
    }
```
In `mouseClicked`, replace:
```java
                if (!row.header() && row.spell() != null) {
                    selected = row.spell();
                    return true;
                }
```
with:
```java
                if (!row.header() && row.spell() != null) {
                    if (selected != row.spell()) {
                        detailScroll = 0.0F;
                    }
                    selected = row.spell();
                    return true;
                }
```

- [ ] **Step 4: Render the detail pane scrolled and clipped.** Replace the whole `renderDetail` method:
```java
    private void renderDetail(GuiGraphics g, int x, int y, int rightEdge, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        int wrap = rightEdge - x;

        // Safety net: nothing in the detail column may draw over the Close button or the panel edge.
        g.enableScissor(x, y, rightEdge, panelTop + 234);
        try {
            renderDetailBody(g, x, y, wrap, mouseX, mouseY);
        } finally {
            g.disableScissor();
        }
    }
```
with:
```java
    private void renderDetail(GuiGraphics g, int x, int y, int rightEdge, int mouseX, int mouseY) {
        if (selected == null) {
            return;
        }
        int wrap = rightEdge - x;
        detailTop = y;
        detailBottom = panelTop + 234;
        detailScroll = clampDetailScroll(detailScroll);
        int start = y - Math.round(detailScroll);

        // Nothing in the detail column may draw over the Close button or the panel edge; what
        // doesn't fit scrolls with the mouse wheel over this column.
        g.enableScissor(x, detailTop, rightEdge, detailBottom);
        try {
            int end = renderDetailBody(g, x, start, wrap, mouseX, mouseY);
            detailContentHeight = end - start;
        } finally {
            g.disableScissor();
        }

        float max = maxDetailScroll();
        if (max > 0.0F) {
            int track = detailBottom - detailTop;
            int thumb = Math.max(12, Math.round(track * (track / (float) (track + max))));
            int thumbTop = detailTop + Math.round((track - thumb) * (detailScroll / max));
            g.fill(rightEdge + 3, detailTop, rightEdge + 5, detailBottom, 0x40D4AF37);
            g.fill(rightEdge + 3, thumbTop, rightEdge + 5, thumbTop + thumb, COLOR_GOLD);
        }
    }
```
Change the signature line:
```java
    private void renderDetailBody(GuiGraphics g, int x, int y, int wrap, int mouseX, int mouseY) {
```
to:
```java
    /** Draws the selected spell's detail from {@code y} down and returns the y just below the last line. */
    private int renderDetailBody(GuiGraphics g, int x, int y, int wrap, int mouseX, int mouseY) {
```
At the end of `renderDetailBody`, replace:
```java
        for (String sourceKey : MagicBookChestLoot.getSourceHintKeys(selected)) {
            Component bullet = Component.literal("• ").append(Component.translatable(sourceKey));
            for (FormattedCharSequence line : this.font.split(bullet, wrap)) {
                g.drawString(this.font, line, x + 2, y, COLOR_BODY);
                y += 11;
            }
        }
    }
```
with:
```java
        for (String sourceKey : MagicBookChestLoot.getSourceHintKeys(selected)) {
            Component bullet = Component.literal("• ").append(Component.translatable(sourceKey));
            for (FormattedCharSequence line : this.font.split(bullet, wrap)) {
                g.drawString(this.font, line, x + 2, y, COLOR_BODY);
                y += 11;
            }
        }
        return y;
    }
```
The XP-bar tooltip must only trigger on the visible part of the pane. Replace:
```java
            if (mouseX >= x && mouseX < barX + barWidth && mouseY >= y && mouseY < y + 9) {
```
with:
```java
            if (mouseX >= x && mouseX < barX + barWidth && mouseY >= y && mouseY < y + 9
                    && mouseY >= detailTop && mouseY < detailBottom) {
```

- [ ] **Step 5: Build**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java
git commit -m "Let the Grimoire detail pane scroll and name the rank colours for ranks" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The pure combat rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/combat/CrowdControlMath.java`
- Create: `src/main/java/com/hogwartsmagic/spell/combat/DiminishingReturns.java`
- Create: `src/main/java/com/hogwartsmagic/spell/combat/BleedRules.java`
- Create: `src/main/java/com/hogwartsmagic/spell/combat/BrittleRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/combat/CrowdControlMathTest.java`, `DiminishingReturnsTest.java`, `BleedRulesTest.java`, `BrittleRulesTest.java`

**Interfaces:**
- Produces:
  - `CrowdControlMath.BOSS_MULTIPLIER = 0.5`; `static int scaledTicks(int baseTicks, double diminishingFactor, boolean boss, boolean pvp, double pvpMultiplier)`
  - `DiminishingReturns.WINDOW_TICKS = 120L`, `SECOND_HIT_FACTOR = 0.5`; `double apply(UUID, long now)`, `double peek(UUID, long now)`, `void clear(UUID)`, `void clearAll()`, `int size()`
  - `BleedRules.MAX_STACKS = 3`, `HEAL_MULTIPLIER = 0.5f`, `TICKS_PER_DAMAGE = 20`; `static int nextAmplifier(int currentAmplifier)` (pass -1 for none), `static int stacks(int amplifier)`, `static float damagePerSecond(int amplifier)`, `static boolean isDamageTick(int remainingTicks)`
  - `BrittleRules.BONUS_PER_LEVEL = 0.3`, `MAX_LEVEL = 5`; `static boolean countsAsBrittleHit(boolean explosion, boolean projectile, boolean directLivingAttacker, boolean bypassesArmor, boolean fire)`, `static float bonusMultiplier(int amplifier)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/combat/CrowdControlMathTest.java`:
```java
package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrowdControlMathTest {
    @Test
    void fullDurationWithNoModifiers() {
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, false, 0.6));
    }

    @Test
    void bossesTakeHalf() {
        assertEquals(30, CrowdControlMath.scaledTicks(60, 1.0, true, false, 0.6));
    }

    @Test
    void playersTakeThePvpMultiplier() {
        assertEquals(36, CrowdControlMath.scaledTicks(60, 1.0, false, true, 0.6));
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, true, 1.0));
    }

    @Test
    void diminishingStacksWithEverythingElse() {
        assertEquals(30, CrowdControlMath.scaledTicks(60, 0.5, false, false, 0.6));
        assertEquals(15, CrowdControlMath.scaledTicks(60, 0.5, true, false, 0.6));
        assertEquals(18, CrowdControlMath.scaledTicks(60, 0.5, false, true, 0.6));
    }

    @Test
    void immuneOrEmptyGivesZero() {
        assertEquals(0, CrowdControlMath.scaledTicks(60, 0.0, false, false, 0.6));
        assertEquals(0, CrowdControlMath.scaledTicks(0, 1.0, false, false, 0.6));
        assertEquals(0, CrowdControlMath.scaledTicks(-5, 1.0, false, false, 0.6));
        assertEquals(0, CrowdControlMath.scaledTicks(60, Double.NaN, false, false, 0.6));
    }

    @Test
    void badPvpMultiplierIsClamped() {
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, true, Double.NaN));
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, true, 3.0));
        assertEquals(0, CrowdControlMath.scaledTicks(60, 1.0, false, true, -1.0));
    }

    @Test
    void roundsToNearestTick() {
        assertEquals(2, CrowdControlMath.scaledTicks(3, 0.5, false, false, 0.6));
        assertEquals(1, CrowdControlMath.scaledTicks(1, 1.0, true, false, 0.6));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/combat/DiminishingReturnsTest.java`:
```java
package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiminishingReturnsTest {
    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);

    @Test
    void firstFullSecondHalfThirdNothing() {
        DiminishingReturns dr = new DiminishingReturns();
        assertEquals(1.0, dr.apply(A, 0L), 1e-9);
        assertEquals(0.5, dr.apply(A, 40L), 1e-9);
        assertEquals(0.0, dr.apply(A, 80L), 1e-9);
    }

    @Test
    void windowRestartsFromTheSecondLandedHit() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        dr.apply(A, 100L);
        assertEquals(0.0, dr.apply(A, 219L), 1e-9);
        assertEquals(1.0, dr.apply(A, 220L), 1e-9);
    }

    @Test
    void immuneAttemptsDoNotExtendImmunity() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        dr.apply(A, 10L);
        assertEquals(0.0, dr.apply(A, 100L), 1e-9);
        assertEquals(0.0, dr.apply(A, 129L), 1e-9);
        assertEquals(1.0, dr.apply(A, 130L), 1e-9);
    }

    @Test
    void sixSecondsAfterALoneHitIsFresh() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        assertEquals(1.0, dr.apply(A, 120L), 1e-9);
    }

    @Test
    void targetsAreIndependent() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        assertEquals(1.0, dr.apply(B, 0L), 1e-9);
        assertEquals(0.5, dr.apply(A, 1L), 1e-9);
    }

    @Test
    void peekDoesNotRecord() {
        DiminishingReturns dr = new DiminishingReturns();
        assertEquals(1.0, dr.peek(A, 0L), 1e-9);
        assertEquals(1.0, dr.apply(A, 0L), 1e-9);
        assertEquals(0.5, dr.peek(A, 10L), 1e-9);
        assertEquals(0.5, dr.apply(A, 10L), 1e-9);
    }

    @Test
    void clearForgetsATarget() {
        DiminishingReturns dr = new DiminishingReturns();
        dr.apply(A, 0L);
        dr.apply(A, 1L);
        dr.clear(A);
        assertEquals(1.0, dr.apply(A, 2L), 1e-9);
    }

    @Test
    void expiredEntriesArePruned() {
        DiminishingReturns dr = new DiminishingReturns();
        for (int i = 0; i < 300; i++) {
            dr.apply(new UUID(1L, i), 0L);
        }
        dr.apply(A, 120L);
        assertEquals(1, dr.size());
    }
}
```

`src/test/java/com/hogwartsmagic/spell/combat/BleedRulesTest.java`:
```java
package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BleedRulesTest {
    @Test
    void stacksClimbToThree() {
        assertEquals(0, BleedRules.nextAmplifier(-1));
        assertEquals(1, BleedRules.nextAmplifier(0));
        assertEquals(2, BleedRules.nextAmplifier(1));
        assertEquals(2, BleedRules.nextAmplifier(2));
        assertEquals(2, BleedRules.nextAmplifier(7));
    }

    @Test
    void damageIsOnePerStackCappedAtThree() {
        assertEquals(1f, BleedRules.damagePerSecond(0), 1e-6);
        assertEquals(2f, BleedRules.damagePerSecond(1), 1e-6);
        assertEquals(3f, BleedRules.damagePerSecond(2), 1e-6);
        assertEquals(3f, BleedRules.damagePerSecond(9), 1e-6);
    }

    @Test
    void damageTicksOncePerSecond() {
        assertTrue(BleedRules.isDamageTick(120));
        assertTrue(BleedRules.isDamageTick(20));
        assertFalse(BleedRules.isDamageTick(119));
        assertFalse(BleedRules.isDamageTick(0));
        assertFalse(BleedRules.isDamageTick(-1));
    }

    @Test
    void healingIsHalved() {
        assertEquals(0.5f, BleedRules.HEAL_MULTIPLIER, 1e-6);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/combat/BrittleRulesTest.java`:
```java
package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrittleRulesTest {
    @Test
    void explosionsAndProjectilesAlwaysCount() {
        assertTrue(BrittleRules.countsAsBrittleHit(true, false, false, true, true));
        assertTrue(BrittleRules.countsAsBrittleHit(false, true, false, false, true));
    }

    @Test
    void meleeCountsButMagicAndFireDoNot() {
        assertTrue(BrittleRules.countsAsBrittleHit(false, false, true, false, false));
        assertFalse(BrittleRules.countsAsBrittleHit(false, false, true, true, false));
        assertFalse(BrittleRules.countsAsBrittleHit(false, false, true, false, true));
        assertFalse(BrittleRules.countsAsBrittleHit(false, false, false, false, false));
    }

    @Test
    void bonusIsThirtyPercentPerLevel() {
        assertEquals(1.3f, BrittleRules.bonusMultiplier(0), 1e-4);
        assertEquals(1.6f, BrittleRules.bonusMultiplier(1), 1e-4);
        assertEquals(2.5f, BrittleRules.bonusMultiplier(4), 1e-4);
        assertEquals(2.5f, BrittleRules.bonusMultiplier(99), 1e-4);
        assertEquals(1.3f, BrittleRules.bonusMultiplier(-3), 1e-4);
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.combat.*"`
Expected: compile FAIL, `cannot find symbol: class CrowdControlMath` (and the other three).

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/combat/CrowdControlMath.java`:
```java
package com.hogwartsmagic.spell.combat;

/**
 * How long a hard crowd-control effect lasts once diminishing returns, the boss rule and the PvP
 * rule are applied. Minecraft-free so it is unit-tested; {@link CrowdControl} feeds it real entities.
 */
public final class CrowdControlMath {
    /** Bosses ({@code #hogwartsmagic:bosses}) shrug off half of every hard crowd control. */
    public static final double BOSS_MULTIPLIER = 0.5;

    private CrowdControlMath() {
    }

    /**
     * @param diminishingFactor 1.0, 0.5 or 0.0 from {@link DiminishingReturns}
     * @param pvpMultiplier     applied only when {@code pvp}; clamped to 0..1, NaN counts as 1
     * @return ticks to apply, rounded to the nearest tick; 0 means nothing lands
     */
    public static int scaledTicks(int baseTicks, double diminishingFactor, boolean boss, boolean pvp, double pvpMultiplier) {
        if (baseTicks <= 0 || !(diminishingFactor > 0.0)) {
            return 0;
        }
        double ticks = baseTicks * Math.min(1.0, diminishingFactor);
        if (boss) {
            ticks *= BOSS_MULTIPLIER;
        }
        if (pvp) {
            ticks *= Double.isNaN(pvpMultiplier) ? 1.0 : Math.max(0.0, Math.min(1.0, pvpMultiplier));
        }
        return (int) Math.round(ticks);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/combat/DiminishingReturns.java`:
```java
package com.hogwartsmagic.spell.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hard crowd-control diminishing returns, per target. The first hard CC lands in full; a second
 * one within {@link #WINDOW_TICKS} of it lasts {@link #SECOND_HIT_FACTOR}; after that nothing lands
 * until {@link #WINDOW_TICKS} have passed since that second one. Attempts that land nothing do not
 * extend the window, so a target can always count on getting out. Without this, Stupefy into
 * Petrificus into Incarcerous locks someone down forever.
 */
public final class DiminishingReturns {
    public static final long WINDOW_TICKS = 120L;
    public static final double SECOND_HIT_FACTOR = 0.5;
    private static final int PRUNE_AT_SIZE = 256;

    private record Entry(int landed, long windowEnd) {
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** Records a hard-CC attempt on {@code target} and returns its duration factor: 1.0, 0.5 or 0.0. */
    public double apply(UUID target, long now) {
        Entry entry = live(target, now);
        if (entry == null) {
            if (entries.size() >= PRUNE_AT_SIZE) {
                entries.values().removeIf(e -> now >= e.windowEnd());
            }
            entries.put(target, new Entry(1, now + WINDOW_TICKS));
            return 1.0;
        }
        if (entry.landed() == 1) {
            entries.put(target, new Entry(2, now + WINDOW_TICKS));
            return SECOND_HIT_FACTOR;
        }
        return 0.0;
    }

    /** The factor {@link #apply} would return now, without recording anything. */
    public double peek(UUID target, long now) {
        Entry entry = live(target, now);
        if (entry == null) {
            return 1.0;
        }
        return entry.landed() == 1 ? SECOND_HIT_FACTOR : 0.0;
    }

    public void clear(UUID target) {
        entries.remove(target);
    }

    public void clearAll() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    private Entry live(UUID target, long now) {
        Entry entry = entries.get(target);
        return entry != null && now < entry.windowEnd() ? entry : null;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/combat/BleedRules.java`:
```java
package com.hogwartsmagic.spell.combat;

/** Bleeding numbers: one damage per second per stack, up to three stacks, healing halved. */
public final class BleedRules {
    public static final int MAX_STACKS = 3;
    public static final float HEAL_MULTIPLIER = 0.5f;
    public static final int TICKS_PER_DAMAGE = 20;

    private BleedRules() {
    }

    /** The amplifier after one more bleed lands; pass -1 when the target isn't bleeding yet. */
    public static int nextAmplifier(int currentAmplifier) {
        if (currentAmplifier < 0) {
            return 0;
        }
        return Math.min(MAX_STACKS - 1, currentAmplifier + 1);
    }

    /** Stacks for an amplifier, clamped to 1..3 so a large /effect amplifier still means three. */
    public static int stacks(int amplifier) {
        return Math.max(1, Math.min(MAX_STACKS, amplifier + 1));
    }

    public static float damagePerSecond(int amplifier) {
        return stacks(amplifier);
    }

    /** Effect ticks fire on the remaining duration; once per second, never at 0 or for an infinite effect. */
    public static boolean isDamageTick(int remainingTicks) {
        return remainingTicks > 0 && remainingTicks % TICKS_PER_DAMAGE == 0;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/combat/BrittleRules.java`:
```java
package com.hogwartsmagic.spell.combat;

/**
 * Which hits shatter a brittle target, and by how much. "Physical" means a hit armour could stop:
 * an explosion, a projectile, or a melee blow from a living attacker that isn't magic or fire.
 */
public final class BrittleRules {
    public static final double BONUS_PER_LEVEL = 0.3;
    public static final int MAX_LEVEL = 5;

    private BrittleRules() {
    }

    public static boolean countsAsBrittleHit(boolean explosion, boolean projectile, boolean directLivingAttacker,
                                             boolean bypassesArmor, boolean fire) {
        if (explosion || projectile) {
            return true;
        }
        return directLivingAttacker && !bypassesArmor && !fire;
    }

    /** Damage multiplier for the consuming hit: +30% per level, level = amplifier + 1, capped at 5. */
    public static float bonusMultiplier(int amplifier) {
        int level = Math.max(1, Math.min(MAX_LEVEL, amplifier + 1));
        return (float) (1.0 + BONUS_PER_LEVEL * level);
    }
}
```

- [ ] **Step 4: Run them and confirm they pass**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.combat.*"`
Expected: PASS. `CrowdControlMathTest` 7 tests, `DiminishingReturnsTest` 8, `BleedRulesTest` 4, `BrittleRulesTest` 3.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/combat/CrowdControlMath.java src/main/java/com/hogwartsmagic/spell/combat/DiminishingReturns.java src/main/java/com/hogwartsmagic/spell/combat/BleedRules.java src/main/java/com/hogwartsmagic/spell/combat/BrittleRules.java src/test/java/com/hogwartsmagic/spell/combat/CrowdControlMathTest.java src/test/java/com/hogwartsmagic/spell/combat/DiminishingReturnsTest.java src/test/java/com/hogwartsmagic/spell/combat/BleedRulesTest.java src/test/java/com/hogwartsmagic/spell/combat/BrittleRulesTest.java
git commit -m "Add the tested rules for crowd control, bleeding and brittle" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Register the five effects, their icons and the bleeding damage type

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/StunnedEffect.java`, `BleedingEffect.java`, `CursedWoundEffect.java`
- Create: `src/main/java/com/hogwartsmagic/spell/combat/CombatDamage.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (after the `BASILISK_GAZE` registration, line ~175)
- Create: `build_scripts/generate_combat_effect_icons.py`
- Create: `src/main/resources/assets/hogwartsmagic/textures/mob_effect/{stunned,bleeding,cursed_wound,brittle,silenced}.png` (generated)
- Create: `src/main/resources/data/hogwartsmagic/damage_type/bleeding.json`
- Create: `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`, `no_knockback.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `BleedRules` (Task 5)
- Produces:
  - `ExampleMod.STUNNED`, `BLEEDING`, `CURSED_WOUND`, `BRITTLE`, `SILENCED`: `RegistryObject<MobEffect>`
  - `CombatDamage.BLEEDING` (`ResourceKey<DamageType>`), `static DamageSource CombatDamage.bleeding(Level)`
  - `CursedWoundEffect.HEALTH_PER_LEVEL = -2.0`

- [ ] **Step 1: The bleeding damage type and its tags**

`src/main/resources/data/hogwartsmagic/damage_type/bleeding.json`:
```json
{
  "exhaustion": 0.0,
  "message_id": "hogwartsmagic.bleeding",
  "scaling": "never"
}
```
`src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`:
```json
{
  "replace": false,
  "values": [
    "hogwartsmagic:bleeding"
  ]
}
```
`src/main/resources/data/minecraft/tags/damage_type/no_knockback.json`:
```json
{
  "replace": false,
  "values": [
    "hogwartsmagic:bleeding"
  ]
}
```

- [ ] **Step 2: `CombatDamage`**
```java
package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Damage sources for the shared combat effects. The types are data-driven (data/hogwartsmagic/damage_type). */
public final class CombatDamage {
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "bleeding"));

    private CombatDamage() {
    }

    /** Bypasses armour, no knockback (see the damage_type tags); death message "bled out". */
    public static DamageSource bleeding(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLEEDING));
    }
}
```

- [ ] **Step 3: The effect classes**

`src/main/java/com/hogwartsmagic/spell/StunnedEffect.java`:
```java
package com.hogwartsmagic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hard stun. The lock itself lives elsewhere: LivingEntityStunMixin makes a stunned entity report
 * isImmobile() (no AI step and no movement input, on the server and on the stunned player's own
 * client), and CombatEffects cancels attacks, item use and casting. This class only draws the
 * circling stars.
 */
public class StunnedEffect extends MobEffect {
    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, 0xF2D64B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            double base = (entity.tickCount % 40) / 40.0 * Math.PI * 2.0;
            double radius = Math.max(0.35, entity.getBbWidth() * 0.6);
            double y = entity.getY() + entity.getBbHeight() + 0.3;
            for (int i = 0; i < 3; i++) {
                double angle = base + i * (Math.PI * 2.0 / 3.0);
                level.sendParticles(ParticleTypes.CRIT, entity.getX() + Math.cos(angle) * radius, y,
                        entity.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        return true;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/BleedingEffect.java`:
```java
package com.hogwartsmagic.spell;

import com.hogwartsmagic.spell.combat.BleedRules;
import com.hogwartsmagic.spell.combat.CombatDamage;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * One damage per second per stack (amplifier + 1, at most 3). The no-regeneration and half-healing
 * parts are in FoodDataBleedingMixin, PlayerBleedingMixin and CombatEffects.onLivingHeal.
 */
public class BleedingEffect extends MobEffect {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.0f);

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0303);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return BleedRules.isDamageTick(duration);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            entity.hurt(CombatDamage.bleeding(level), BleedRules.damagePerSecond(amplifier));
            level.sendParticles(BLOOD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    6 + 3 * BleedRules.stacks(amplifier), entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.25,
                    entity.getBbWidth() * 0.3, 0.0);
        }
        return true;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/CursedWoundEffect.java`:
```java
package com.hogwartsmagic.spell;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lowers max health by one heart per level (vanilla scales the modifier by amplifier + 1). Health
 * above the new max is clamped by vanilla. Milk can't lift it: see CombatEffects.onEffectRemove.
 */
public class CursedWoundEffect extends MobEffect {
    public static final double HEALTH_PER_LEVEL = -2.0;

    public CursedWoundEffect() {
        super(MobEffectCategory.HARMFUL, 0x3B0A45);
        this.addAttributeModifier(
                Attributes.MAX_HEALTH,
                ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "cursed_wound/max_health"),
                HEALTH_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE);
    }
}
```

- [ ] **Step 4: Register them.** In `ExampleMod.java`, directly after:
```java
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BASILISK_GAZE = MOB_EFFECTS.register("basilisk_gaze",
            com.hogwartsmagic.spell.BasiliskGazeEffect::new);
```
add:
```java

    // Shared combat effects (Spell Mastery Phase 2). Later phases apply them; hard crowd control
    // goes through com.hogwartsmagic.spell.combat.CrowdControl so diminishing returns, the boss
    // rule and the PvP rule always run.
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> STUNNED = MOB_EFFECTS.register("stunned",
            com.hogwartsmagic.spell.StunnedEffect::new);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BLEEDING = MOB_EFFECTS.register("bleeding",
            com.hogwartsmagic.spell.BleedingEffect::new);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> CURSED_WOUND = MOB_EFFECTS.register("cursed_wound",
            com.hogwartsmagic.spell.CursedWoundEffect::new);
    /** Marker: CombatEffects consumes it on the next physical or explosion hit for bonus damage. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> BRITTLE = MOB_EFFECTS.register("brittle",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x9FD8F0) {});
    /** Marker: the cast handlers refuse while it is active and the spell wheel greys out. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> SILENCED = MOB_EFFECTS.register("silenced",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x6E6E8F) {});
```

- [ ] **Step 5: The icon generator.** Create `build_scripts/generate_combat_effect_icons.py`:
```python
"""Paint the five 18x18 shared combat effect icons.

    python build_scripts/generate_combat_effect_icons.py [output_dir]

Writes assets/hogwartsmagic/textures/mob_effect/<effect>.png for stunned, bleeding, cursed_wound,
brittle and silenced (or into output_dir when one is given, for previewing).

WHAT THEY DEPICT
----------------
  * stunned       three gold stars with little speed marks, the cartoon "seeing stars"
  * bleeding      a dark red drop with a highlight
  * cursed_wound  a violet heart split by a black, green-edged crack
  * brittle       a pale ice shard with white fracture lines
  * silenced      a wand under a red no-entry ring

STYLE
-----
Vanilla effect icons are 18x18, flat-shaded, with a dark outline and one highlight, so these are too.
Shapes are drawn with PIL's polygon and ellipse primitives (no anti-aliasing) and then outlined by
a one-pixel dilation pass, which keeps the outline exactly one pixel wide everywhere.
"""

import math
import os
import sys

from PIL import Image, ImageDraw

SIZE = 18
DEFAULT_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                           "assets", "hogwartsmagic", "textures", "mob_effect")


def blank():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def outline(image, colour):
    """Add a one-pixel outline around every opaque pixel (4-neighbour dilation)."""
    src = image.copy()
    px = src.load()
    out = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and px[nx, ny][3] != 0:
                    out[x, y] = colour + (255,)
                    break
    return image


def star_points(cx, cy, outer, inner):
    points = []
    for i in range(10):
        radius = outer if i % 2 == 0 else inner
        angle = -math.pi / 2 + i * math.pi / 5
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


def stunned():
    image = blank()
    draw = ImageDraw.Draw(image)
    for cx, cy, outer in ((5.0, 5.5, 4.0), (13.0, 5.0, 3.5), (9.0, 13.0, 3.5)):
        draw.polygon(star_points(cx, cy, outer, outer * 0.45), fill=(255, 214, 64, 255))
    image = outline(image, (92, 64, 8))
    marks = ImageDraw.Draw(image)
    for x in (1, 2, 15, 16):
        marks.point((x, 11), fill=(214, 196, 140, 255))
    marks.point((3, 12), fill=(214, 196, 140, 255))
    marks.point((14, 12), fill=(214, 196, 140, 255))
    image.putpixel((5, 5), (255, 250, 214, 255))
    image.putpixel((13, 4), (255, 250, 214, 255))
    image.putpixel((9, 12), (255, 250, 214, 255))
    return image


def bleeding():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.polygon([(9, 2), (13, 9), (5, 9)], fill=(150, 10, 12, 255))
    draw.ellipse((4, 7, 14, 16), fill=(150, 10, 12, 255))
    draw.ellipse((10, 11, 13, 15), fill=(110, 4, 6, 255))
    image = outline(image, (48, 0, 2))
    image.putpixel((7, 9), (255, 170, 160, 255))
    image.putpixel((7, 10), (230, 90, 84, 255))
    return image


def cursed_wound():
    image = blank()
    draw = ImageDraw.Draw(image)
    violet = (112, 40, 140, 255)
    draw.ellipse((2, 3, 9, 10), fill=violet)
    draw.ellipse((8, 3, 15, 10), fill=violet)
    draw.polygon([(2, 7), (15, 7), (8.5, 15)], fill=violet)
    image = outline(image, (34, 6, 44))
    crack = [(9, 4), (8, 6), (10, 8), (8, 10), (9, 12), (8, 14)]
    draw = ImageDraw.Draw(image)
    draw.line(crack, fill=(16, 0, 20, 255))
    for x, y in ((10, 6), (9, 9), (10, 11)):
        image.putpixel((x, y), (120, 230, 120, 255))
    image.putpixel((5, 5), (200, 150, 222, 255))
    return image


def brittle():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.polygon([(9, 1), (14, 7), (11, 16), (6, 16), (4, 7)], fill=(170, 222, 240, 255))
    draw.polygon([(9, 1), (14, 7), (11, 16), (9, 9)], fill=(120, 186, 214, 255))
    image = outline(image, (30, 70, 100))
    draw = ImageDraw.Draw(image)
    draw.line([(7, 4), (9, 8), (7, 11), (9, 14)], fill=(255, 255, 255, 255))
    draw.line([(9, 8), (12, 9)], fill=(240, 252, 255, 255))
    return image


def silenced():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.line([(4, 14), (13, 5)], fill=(120, 78, 40, 255), width=2)
    image.putpixel((13, 4), (255, 255, 255, 255))
    image.putpixel((14, 4), (255, 255, 255, 255))
    image = outline(image, (40, 24, 10))
    draw = ImageDraw.Draw(image)
    draw.ellipse((1, 1, 16, 16), outline=(210, 30, 30, 255), width=2)
    draw.line([(4, 4), (13, 13)], fill=(210, 30, 30, 255), width=2)
    return image


ICONS = {
    "stunned": stunned,
    "bleeding": bleeding,
    "cursed_wound": cursed_wound,
    "brittle": brittle,
    "silenced": silenced,
}


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    os.makedirs(out_dir, exist_ok=True)
    for name, painter in ICONS.items():
        image = painter()
        assert image.size == (SIZE, SIZE), name + " is not 18x18"
        path = os.path.normpath(os.path.join(out_dir, name + ".png"))
        image.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
```

Run: `python build_scripts/generate_combat_effect_icons.py`
Expected: five `wrote ...textures\mob_effect\<name>.png` lines. Open one PNG to confirm it is 18x18 with a transparent background. Vanilla's `mob_effects` atlas picks up `textures/mob_effect/` in every namespace, so no atlas JSON is needed.

- [ ] **Step 6: Strings.** In `en_us.json`, directly after the line `  "gui.hogwartsmagic.mastery.tooltip": "Each rank: -%s%% mana cost and cooldown, +%s max mana",` insert:
```json
  "effect.hogwartsmagic.stunned": "Stunned",
  "effect.hogwartsmagic.stunned.description": "Can't move, attack, use items or cast.",
  "effect.hogwartsmagic.bleeding": "Bleeding",
  "effect.hogwartsmagic.bleeding.description": "Loses health every second. No natural regeneration, and healing is halved.",
  "effect.hogwartsmagic.cursed_wound": "Cursed Wound",
  "effect.hogwartsmagic.cursed_wound.description": "Lowers max health. Milk can't lift this curse.",
  "effect.hogwartsmagic.brittle": "Brittle",
  "effect.hogwartsmagic.brittle.description": "The next physical or explosive hit deals bonus damage.",
  "effect.hogwartsmagic.silenced": "Silenced",
  "effect.hogwartsmagic.silenced.description": "Can't cast spells.",
  "death.attack.hogwartsmagic.bleeding": "%1$s bled out",
```

- [ ] **Step 7: Build, test and boot a dedicated server** (the damage type is a new data-driven registry entry)

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Then run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: no error mentioning `damage_type`, `bleeding` or `hogwartsmagic:stunned`; `Done (` is reached.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/StunnedEffect.java src/main/java/com/hogwartsmagic/spell/BleedingEffect.java src/main/java/com/hogwartsmagic/spell/CursedWoundEffect.java src/main/java/com/hogwartsmagic/spell/combat/CombatDamage.java src/main/java/com/hogwartsmagic/ExampleMod.java build_scripts/generate_combat_effect_icons.py src/main/resources/assets/hogwartsmagic/textures/mob_effect/stunned.png src/main/resources/assets/hogwartsmagic/textures/mob_effect/bleeding.png src/main/resources/assets/hogwartsmagic/textures/mob_effect/cursed_wound.png src/main/resources/assets/hogwartsmagic/textures/mob_effect/brittle.png src/main/resources/assets/hogwartsmagic/textures/mob_effect/silenced.png src/main/resources/data/hogwartsmagic/damage_type/bleeding.json src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json src/main/resources/data/minecraft/tags/damage_type/no_knockback.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Register the stunned, bleeding, cursed wound, brittle and silenced effects" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Make the effects behave (event hooks and mixins)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/LivingEntityStunMixin.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/FoodDataBleedingMixin.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/PlayerBleedingMixin.java`
- Modify: `src/main/resources/hogwartsmagic.mixins.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ExampleMod.STUNNED/BLEEDING/CURSED_WOUND/BRITTLE/SILENCED` (Task 6), `BleedRules`, `BrittleRules` (Task 5), `ModEvents.interruptActiveCasting(ServerPlayer)` (existing)
- Produces (all `public static` on `CombatEffects`):
  - `boolean has(LivingEntity, RegistryObject<MobEffect>)`, `isStunned(LivingEntity)`, `isSilenced(LivingEntity)`, `isBleeding(LivingEntity)`, `isCastingBlocked(LivingEntity)` (stunned or silenced). Safe on both sides.
  - `void notifyCastBlocked(Player)`: action-bar line, works from either side
  - `int addBleeding(LivingEntity target, int ticks, @Nullable Entity source)` → stacks now on the target (0 if none)
  - `boolean applyCursedWound(LivingEntity target, int ticks, int hearts, @Nullable Entity source)` (hearts 1..10)
  - `boolean applyBrittle(LivingEntity target, int ticks, int amplifier, @Nullable Entity source)`
  - `boolean applySilenced(LivingEntity target, int ticks, @Nullable Entity source)`
  - `boolean cureCursedWound(LivingEntity target)` (the only code path past the milk guard; Phase 5 Heal uses it)
  - `@Nullable Entity responsible(@Nullable Entity source)` (a projectile's owner, else the source)

- [ ] **Step 1: Create `CombatEffects`**
```java
package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;

/**
 * The five shared combat effects: queries, helpers for the non-crowd-control ones, and the event
 * hooks that make them behave. Hard crowd control (stun and friends) is applied through
 * {@link CrowdControl} instead, so diminishing returns and the boss and PvP rules always run.
 *
 * <p>Common code. The queries also run on the client (input lock, spell wheel), where they read the
 * effect instances vanilla syncs to the local player; every hook that changes the world checks for
 * the server side first.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatEffects {
    public static final int MAX_CURSED_HEARTS = 10;

    /** Set only inside {@link #cureCursedWound}, the one removal path the milk guard lets through. */
    private static boolean cursedWoundCureInProgress;

    private CombatEffects() {
    }

    // ---- Queries (both sides) ---------------------------------------------------------------

    public static boolean has(LivingEntity entity, RegistryObject<MobEffect> effect) {
        return effect.getHolder().map(entity::hasEffect).orElse(false);
    }

    public static boolean isStunned(LivingEntity entity) {
        return has(entity, ExampleMod.STUNNED);
    }

    public static boolean isSilenced(LivingEntity entity) {
        return has(entity, ExampleMod.SILENCED);
    }

    public static boolean isBleeding(LivingEntity entity) {
        return has(entity, ExampleMod.BLEEDING);
    }

    /** Stunned or silenced: no casts, no channel starts, no channel heartbeats. */
    public static boolean isCastingBlocked(LivingEntity entity) {
        return isStunned(entity) || isSilenced(entity);
    }

    public static void notifyCastBlocked(Player player) {
        String key = isStunned(player)
                ? "gui." + ExampleMod.MODID + ".combat.cast_blocked_stunned"
                : "gui." + ExampleMod.MODID + ".combat.cast_blocked_silenced";
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    /** Who is responsible for a hit: a projectile's (or spell beam's) owner, otherwise the entity itself. */
    @Nullable
    public static Entity responsible(@Nullable Entity source) {
        if (source instanceof Projectile projectile && projectile.getOwner() != null) {
            return projectile.getOwner();
        }
        return source;
    }

    // ---- Helpers for later phases (server side) ----------------------------------------------

    /** Adds one bleed stack (max 3) and refreshes the duration to at least {@code ticks}. Returns the stacks now on the target. */
    public static int addBleeding(LivingEntity target, int ticks, @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return 0;
        }
        Holder<MobEffect> holder = ExampleMod.BLEEDING.getHolder().orElse(null);
        if (holder == null) {
            return 0;
        }
        MobEffectInstance current = target.getEffect(holder);
        int amplifier = BleedRules.nextAmplifier(current == null ? -1 : current.getAmplifier());
        int duration = Math.max(ticks, current == null ? 0 : current.getDuration());
        target.addEffect(new MobEffectInstance(holder, duration, amplifier, false, true, true), responsible(source));
        MobEffectInstance now = target.getEffect(holder);
        return now == null ? 0 : BleedRules.stacks(now.getAmplifier());
    }

    /** Takes {@code hearts} (1..10) of max health for {@code ticks}. */
    public static boolean applyCursedWound(LivingEntity target, int ticks, int hearts, @Nullable Entity source) {
        int amplifier = Math.max(1, Math.min(MAX_CURSED_HEARTS, hearts)) - 1;
        return add(target, ExampleMod.CURSED_WOUND, ticks, amplifier, source);
    }

    public static boolean applyBrittle(LivingEntity target, int ticks, int amplifier, @Nullable Entity source) {
        return add(target, ExampleMod.BRITTLE, ticks, Math.max(0, amplifier), source);
    }

    /** Silence is not hard crowd control, so it skips diminishing returns. */
    public static boolean applySilenced(LivingEntity target, int ticks, @Nullable Entity source) {
        return add(target, ExampleMod.SILENCED, ticks, 0, source);
    }

    /** The only way to lift a cursed wound early (Phase 5: Heal at Master). */
    public static boolean cureCursedWound(LivingEntity target) {
        Holder<MobEffect> holder = ExampleMod.CURSED_WOUND.getHolder().orElse(null);
        if (holder == null || target.level().isClientSide()) {
            return false;
        }
        cursedWoundCureInProgress = true;
        try {
            return target.removeEffect(holder);
        } finally {
            cursedWoundCureInProgress = false;
        }
    }

    private static boolean add(LivingEntity target, RegistryObject<MobEffect> effect, int ticks, int amplifier,
                               @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return false;
        }
        Holder<MobEffect> holder = effect.getHolder().orElse(null);
        if (holder == null) {
            return false;
        }
        return target.addEffect(new MobEffectInstance(holder, ticks, amplifier, false, true, true), responsible(source));
    }

    // ---- Bleeding ----------------------------------------------------------------------------

    /** Natural regeneration is blocked by the FoodData and Player mixins; every other heal is halved. */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (isBleeding(event.getEntity())) {
            event.setAmount(event.getAmount() * BleedRules.HEAL_MULTIPLIER);
        }
    }

    // ---- Brittle -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || event.getAmount() <= 0.0F) {
            return;
        }
        Holder<MobEffect> brittle = ExampleMod.BRITTLE.getHolder().orElse(null);
        MobEffectInstance instance = brittle == null ? null : target.getEffect(brittle);
        if (instance == null) {
            return;
        }
        DamageSource source = event.getSource();
        boolean counts = BrittleRules.countsAsBrittleHit(
                source.is(DamageTypeTags.IS_EXPLOSION),
                source.is(DamageTypeTags.IS_PROJECTILE),
                source.getDirectEntity() instanceof LivingEntity,
                source.is(DamageTypeTags.BYPASSES_ARMOR),
                source.is(DamageTypeTags.IS_FIRE));
        if (!counts) {
            return;
        }
        event.setAmount(event.getAmount() * BrittleRules.bonusMultiplier(instance.getAmplifier()));
        target.removeEffect(brittle);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                    target.getZ(), 16, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8F, 1.4F);
        }
    }

    // ---- Cursed wound ------------------------------------------------------------------------

    /**
     * Forge 51 has no curative-item list: milk calls removeAllEffects() from finishUsingItem, while
     * the bucket is still the entity's use item. So any removal during item use (milk, honey, a modded
     * drink) is refused for cursed_wound. /effect clear and natural expiry are not item use (expiry
     * fires MobEffectEvent.Expired, not Remove), so they still work.
     */
    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (cursedWoundCureInProgress || event.getEffect() != ExampleMod.CURSED_WOUND.get()) {
            return;
        }
        if (event.getEntity().isUsingItem()) {
            event.setCanceled(true);
        }
    }

    // ---- Stun and silence --------------------------------------------------------------------

    /** Stun or silence landing on a player drops whatever channel they were holding. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffect effect = event.getEffectInstance().getEffect().value();
        if (effect == ExampleMod.STUNNED.get() || effect == ExampleMod.SILENCED.get()) {
            com.hogwartsmagic.event.ModEvents.interruptActiveCasting(player);
        }
    }

    /** Server housekeeping for a stunned entity; the movement lock itself is LivingEntityStunMixin. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !isStunned(entity)) {
            return;
        }
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setJumping(false);
        }
        if (entity instanceof Creeper creeper) {
            creeper.setSwellDir(-1);
        }
        if (entity instanceof Player player) {
            player.setSprinting(false);
        }
    }

    /** A stunned mob or player can't land a melee blow (projectiles already in flight still hit). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity()
                && !source.is(DamageTypeTags.IS_EXPLOSION) && isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfStunned(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (isStunned(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * NPC casters (Students, Death Eaters, Voldemort) cast by spawning a SpellBeamEntity from about
     * ten places in their goals. Refusing the beam here silences all of them in one spot; a player's
     * beam never gets this far because the cast packet is refused first.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        if (event.getEntity() instanceof SpellBeamEntity beam
                && beam.getOwner() instanceof LivingEntity caster
                && isCastingBlocked(caster)) {
            event.setCanceled(true);
        }
    }

    private static void cancelIfStunned(PlayerInteractEvent event) {
        if (event.isCancelable() && isStunned(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
```

- [ ] **Step 2: The stun mixin.** `src/main/java/com/hogwartsmagic/mixin/LivingEntityStunMixin.java`:
```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A stunned entity is immobile. LivingEntity.aiStep then skips serverAiStep (goals, brain,
 * navigation, move and look controls: the mob's AI is paused) and zeroes xxa, zza and jumping, so
 * gravity and knockback still apply but the entity can't walk. For players this runs on the server
 * and on the stunned player's own client (which has the synced effect), so their movement input is
 * zeroed on both sides. Player.isImmobile calls super, so players are covered too.
 *
 * <p>Common mixin (registered under "mixins"): a dedicated server needs it as much as a client.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStunMixin {

    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$stunnedIsImmobile(CallbackInfoReturnable<Boolean> cir) {
        if (CombatEffects.isStunned((LivingEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
```

- [ ] **Step 3: The bleeding mixins.**

`src/main/java/com/hogwartsmagic/mixin/FoodDataBleedingMixin.java`:
```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bleeding acts like naturalRegeneration=false for that one player: both food-regeneration branches
 * of FoodData.tick read the game rule through this single getBoolean call, so the saturation and the
 * slow regen are both skipped (and no exhaustion is spent on them). Starvation is unaffected.
 */
@Mixin(FoodData.class)
public abstract class FoodDataBleedingMixin {

    @Redirect(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean hogwartsmagic$noRegenWhileBleeding(GameRules rules, GameRules.Key<GameRules.BooleanValue> key,
                                                       Player player) {
        return rules.getBoolean(key) && !CombatEffects.isBleeding(player);
    }
}
```

`src/main/java/com/hogwartsmagic/mixin/PlayerBleedingMixin.java`:
```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Peaceful difficulty's once-a-second heal (the only heal call in Player.aiStep) is natural regeneration too. */
@Mixin(Player.class)
public abstract class PlayerBleedingMixin {

    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void hogwartsmagic$noPeacefulRegenWhileBleeding(Player self, float amount) {
        if (!CombatEffects.isBleeding(self)) {
            self.heal(amount);
        }
    }
}
```

- [ ] **Step 4: Register the mixins.** In `src/main/resources/hogwartsmagic.mixins.json`, replace:
```json
    "PlayerChunkSenderMixin"
  ],
```
with:
```json
    "PlayerChunkSenderMixin",
    "LivingEntityStunMixin",
    "FoodDataBleedingMixin",
    "PlayerBleedingMixin"
  ],
```

- [ ] **Step 5: Strings.** In `en_us.json`, directly after `  "death.attack.hogwartsmagic.bleeding": "%1$s bled out",` insert:
```json
  "gui.hogwartsmagic.combat.cast_blocked_stunned": "You are stunned and can't cast!",
  "gui.hogwartsmagic.combat.cast_blocked_silenced": "You are silenced and can't cast!",
```

- [ ] **Step 6: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass, and no Mixin annotation-processor error about `isImmobile`, `tick` or `aiStep`.
Then run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: no `InvalidInjectionException`, no `MixinApplyError`, no stack trace mentioning `CombatEffects` or any `client` class. Bootstrap loads `LivingEntity` and `Player` (vanilla builds the player's default attributes), so their mixins are proven here. `FoodData` only loads when a player joins, so `FoodDataBleedingMixin` is proven by the in-game checks in Task 10; with `"defaultRequire": 1` a bad target fails loudly on the first join rather than silently.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java src/main/java/com/hogwartsmagic/mixin/LivingEntityStunMixin.java src/main/java/com/hogwartsmagic/mixin/FoodDataBleedingMixin.java src/main/java/com/hogwartsmagic/mixin/PlayerBleedingMixin.java src/main/resources/hogwartsmagic.mixins.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make stun, bleeding, cursed wound, brittle and silence actually work" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The crowd-control entry point, bosses and the PvP rule

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/combat/CrowdControl.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/combat/CombatTags.java` (whole file)
- Create: `src/main/resources/data/hogwartsmagic/tags/entity_type/bosses.json`
- Modify: `src/main/java/com/hogwartsmagic/Config.java` (two values)
- Create: `src/main/java/com/hogwartsmagic/command/CrowdControlCommand.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (`onRegisterCommands`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `CrowdControlMath.scaledTicks`, `DiminishingReturns` (Task 5), `CombatEffects.responsible` (Task 7), `ExampleMod.STUNNED` (Task 6)
- Produces:
  - `CombatTags.BOSSES` (`TagKey<EntityType<?>>`), `static boolean CombatTags.isBoss(@Nullable Entity)`
  - `CrowdControl.apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, @Nullable Entity source)` → ticks applied (0 = nothing landed)
  - `CrowdControl.apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, int amplifier, @Nullable Entity source)`
  - `CrowdControl.scaleDuration(LivingEntity target, int baseTicks, @Nullable Entity source)` → ticks; consumes a diminishing-returns step (for CC that isn't a MobEffect, such as a tether)
  - `CrowdControl.isPvp(LivingEntity target, @Nullable Entity source)`, `CrowdControl.resetDiminishing(LivingEntity)`
  - `Config.getPvpCrowdControlMultiplier()` → `double` (1.0 when the toggle is off)

- [ ] **Step 1: The boss tag.** `src/main/resources/data/hogwartsmagic/tags/entity_type/bosses.json`:
```json
{
  "replace": false,
  "values": [
    "hogwartsmagic:voldemort",
    "hogwartsmagic:hungarian_horntail",
    "hogwartsmagic:ukrainian_ironbelly",
    "hogwartsmagic:basilisk"
  ]
}
```
(Registry names from `entity/ModEntities.java`. Add any future boss here.)

- [ ] **Step 2: `CombatTags`** (replace the whole file)
```java
package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

/** Tags shared by the combat and mastery rules, in one place so the spellings can't drift. */
public final class CombatTags {
    /** Scoreboard tag on a mob fighting for a caster (Imperio thralls, Serpensortia snakes). */
    public static final String ALLIED = "hogwartsmagic:allied";

    /**
     * Bosses: hard crowd control lasts half as long. Later phases also read it (Obliviate, Confundo
     * and Avada's instant kill skip bosses; Imperio is capped at short durations).
     */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "bosses"));

    private CombatTags() {
    }

    public static boolean isBoss(@Nullable Entity entity) {
        return entity != null && entity.getType().is(BOSSES);
    }
}
```

- [ ] **Step 3: PvP config.** In `Config.java`:

Directly above `static final ForgeConfigSpec SPEC = BUILDER.build();` add:
```java
    public static final ForgeConfigSpec.BooleanValue PVP_CROWD_CONTROL_REDUCTION = BUILDER
            .comment("Combat: if true, hard crowd control (stuns, freezes, binds) one player puts on another lasts pvpCrowdControlMultiplier as long.")
            .define("pvpCrowdControlReduction", true);

    public static final ForgeConfigSpec.DoubleValue PVP_CROWD_CONTROL_MULTIPLIER = BUILDER
            .comment("Combat: duration multiplier for hard crowd control between players while pvpCrowdControlReduction is on.")
            .defineInRange("pvpCrowdControlMultiplier", 0.6, 0.0, 1.0);
```
Directly above `    private Config() {` add:
```java
    public static boolean pvpCrowdControlReduction = true;
    public static double pvpCrowdControlMultiplier = 0.6;
```
In `onLoad`, directly above the threshold warning added in Task 2 (the line `        if (!com.hogwartsmagic.spell.mastery.MasteryMath.thresholdsValid(`), add:
```java
        pvpCrowdControlReduction = PVP_CROWD_CONTROL_REDUCTION.get();
        pvpCrowdControlMultiplier = PVP_CROWD_CONTROL_MULTIPLIER.get();
```
At the end of the class, directly above its final closing brace, add:
```java

    /** Duration multiplier for hard crowd control between players; 1.0 when the reduction is switched off. */
    public static double getPvpCrowdControlMultiplier() {
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }
```

- [ ] **Step 4: `CrowdControl`**
```java
package com.hogwartsmagic.spell.combat;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The single door for hard crowd control (stun, petrify, bind, hang, levitate, freeze). Every later
 * phase calls {@link #apply} (or {@link #scaleDuration} for crowd control that isn't a MobEffect), so
 * diminishing returns, the boss rule and the PvP rule can't be forgotten. Server side only: on the
 * client every method returns 0 and records nothing.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrowdControl {
    private static final DiminishingReturns DIMINISHING = new DiminishingReturns();

    private CrowdControl() {
    }

    /** {@link #apply(LivingEntity, Holder, int, int, Entity)} at amplifier 0. */
    public static int apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, @Nullable Entity source) {
        return apply(target, effect, baseTicks, 0, source);
    }

    /**
     * Apply a hard crowd-control effect for {@code baseTicks} scaled by the rules.
     *
     * @param source the caster (or its projectile/beam); decides the PvP rule
     * @return the ticks applied; 0 if nothing landed (diminishing returns, an immune target, a dead
     *         target, the client side, or a longer copy of the effect already running)
     */
    public static int apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, int amplifier, @Nullable Entity source) {
        if (target.level().isClientSide() || !target.isAlive() || baseTicks <= 0) {
            return 0;
        }
        MobEffectInstance probe = new MobEffectInstance(effect, baseTicks, Math.max(0, amplifier), false, true, true);
        if (!target.canBeAffected(probe)) {
            return 0; // immune targets don't burn a diminishing-returns step
        }
        int ticks = scaleDuration(target, baseTicks, source);
        if (ticks <= 0) {
            return 0;
        }
        boolean added = target.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amplifier), false, true, true),
                CombatEffects.responsible(source));
        return added ? ticks : 0;
    }

    /** Scaled duration for this target right now. Consumes one diminishing-returns step. */
    public static int scaleDuration(LivingEntity target, int baseTicks, @Nullable Entity source) {
        if (target.level().isClientSide() || !target.isAlive() || baseTicks <= 0) {
            return 0;
        }
        double factor = DIMINISHING.apply(target.getUUID(), target.level().getGameTime());
        return CrowdControlMath.scaledTicks(baseTicks, factor, CombatTags.isBoss(target),
                isPvp(target, source), Config.getPvpCrowdControlMultiplier());
    }

    /** A player's crowd control landing on another player. NPC casters and self-casts don't count. */
    public static boolean isPvp(LivingEntity target, @Nullable Entity source) {
        Entity responsible = CombatEffects.responsible(source);
        return target instanceof Player && responsible instanceof Player && responsible != target;
    }

    /** Forget a target's diminishing-returns chain (debug command, tests). */
    public static void resetDiminishing(LivingEntity target) {
        DIMINISHING.clear(target.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DIMINISHING.clearAll();
    }
}
```

- [ ] **Step 5: A debug command, so the rules can be checked in game before any spell uses them.** `src/main/java/com/hogwartsmagic/command/CrowdControlCommand.java`:
```java
package com.hogwartsmagic.command;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /crowdcontrol stun <targets> <seconds>} and {@code /crowdcontrol reset <targets>}, op only.
 * Stuns through {@link CrowdControl#apply} with the command's executor as the source, so diminishing
 * returns, the boss rule and (when one player runs it on another) the PvP rule all show in the
 * reported ticks. Phase 2 has no spell that applies crowd control yet; this is how it is tested.
 */
public final class CrowdControlCommand {
    private CrowdControlCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("crowdcontrol")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("stun")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("seconds", FloatArgumentType.floatArg(0.05f, 60f))
                                        .executes(CrowdControlCommand::stun))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(CrowdControlCommand::reset))));
    }

    private static int stun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int baseTicks = Math.round(FloatArgumentType.getFloat(ctx, "seconds") * 20f);
        Entity source = ctx.getSource().getEntity();
        Holder<MobEffect> stunned = ExampleMod.STUNNED.getHolder().orElseThrow();
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            int ticks = CrowdControl.apply(target, stunned, baseTicks, source);
            Component name = target.getDisplayName();
            ctx.getSource().sendSuccess(() -> ticks > 0
                    ? Component.translatable("commands.hogwartsmagic.crowdcontrol.stunned", name, ticks)
                    : Component.translatable("commands.hogwartsmagic.crowdcontrol.resisted", name), false);
            count++;
        }
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof LivingEntity target) {
                CrowdControl.resetDiminishing(target);
                count++;
            }
        }
        int cleared = count;
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.crowdcontrol.reset", cleared), true);
        return cleared;
    }
}
```
Register it in `ExampleMod.onRegisterCommands`, directly after:
```java
        com.hogwartsmagic.command.SpellMasteryCommand.register(event.getDispatcher());
```
add:
```java
        com.hogwartsmagic.command.CrowdControlCommand.register(event.getDispatcher());
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.combat.cast_blocked_silenced": "You are silenced and can't cast!",` insert:
```json
  "commands.hogwartsmagic.crowdcontrol.stunned": "Stunned %s for %s ticks",
  "commands.hogwartsmagic.crowdcontrol.resisted": "%s resisted (diminishing returns or immunity)",
  "commands.hogwartsmagic.crowdcontrol.reset": "Cleared diminishing returns on %s targets",
```

- [ ] **Step 7: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/combat/CrowdControl.java src/main/java/com/hogwartsmagic/spell/combat/CombatTags.java src/main/resources/data/hogwartsmagic/tags/entity_type/bosses.json src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/command/CrowdControlCommand.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add the crowd control entry point with diminishing returns, bosses and PvP" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Refuse casting while stunned or silenced, lock stunned input, grey the wheel

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.handle`, `ImperioControlPacket.handle`, `ChooseApparitionDestinationPacket.handle`)
- Modify: `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java` (`castSelectedSpell`)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`handleWandUse`)
- Modify: `src/main/java/com/hogwartsmagic/client/WandRadialMenuScreen.java` (`render`)
- Create: `src/main/java/com/hogwartsmagic/client/StunInputLock.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `CombatEffects.isCastingBlocked/isStunned/isSilenced/notifyCastBlocked` (Task 7), `ModEvents.cancelApparitionChannel(ServerPlayer, ItemStack)` (existing)
- Produces: nothing new for later tasks.

- [ ] **Step 1: The cast handler refuses on the server.** In `CastSpellPacket.handle`, replace:
```java
            // Check if spell is learned
            if (!com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, spell)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.spell_not_learned"), true);
                return;
            }
```
with:
```java
            // Check if spell is learned
            if (!com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, spell)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.spell_not_learned"), true);
                return;
            }

            // Stunned or silenced: no casts, no channel starts and no heartbeats (a running channel
            // lapses on its own timeout). Releasing a channel is always allowed.
            if (!p.releaseChannel && com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                if (!p.channelHeartbeat) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                }
                return;
            }
```
(This sits before the Apparition branch, so it also stops a stunned or silenced player opening the Apparition map.)

- [ ] **Step 2: Imperio refuses on the server.** In `ImperioControlPacket.handle`, replace:
```java
            if (p.stopChannel) {
                com.hogwartsmagic.event.ModEvents.stopImperioChannel(player, wand);
                return;
            }

            com.hogwartsmagic.event.ModEvents.updateImperioInput(player, p.forward, p.strafe, p.jump, p.attack, p.yaw, p.pitch);
```
with:
```java
            if (p.stopChannel) {
                com.hogwartsmagic.event.ModEvents.stopImperioChannel(player, wand);
                return;
            }

            // The channel was already dropped when the stun or silence landed (CombatEffects.onEffectAdded).
            if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                if (p.startChannel) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                }
                return;
            }

            com.hogwartsmagic.event.ModEvents.updateImperioInput(player, p.forward, p.strafe, p.jump, p.attack, p.yaw, p.pitch);
```

- [ ] **Step 3: Apparition refuses on the server.** In `ChooseApparitionDestinationPacket.handle`, replace:
```java
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.event.ModEvents.completeApparition(player, p.targetX, p.targetZ);
            }
```
with:
```java
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                com.hogwartsmagic.event.ModEvents.completeApparition(player, p.targetX, p.targetZ);
            }
```

- [ ] **Step 4: The client doesn't even try.** In `ModEventsClientHooks.castSelectedSpell`, replace:
```java
    private static boolean castSelectedSpell(Player player, ItemStack wand, SpellType selected) {
        if (selected == SpellType.PROTEGO_MAXIMA && player.getXRot() <= -55.0F) {
```
with:
```java
    private static boolean castSelectedSpell(Player player, ItemStack wand, SpellType selected) {
        // Immediate feedback only; the server refuses on its own (CastSpellPacket.handle).
        if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
            com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
            return true;
        }
        if (selected == SpellType.PROTEGO_MAXIMA && player.getXRot() <= -55.0F) {
```
In `ModEvents.handleWandUse`, replace:
```java
            } else {
                SpellType selected = MagicWandItem.getSelectedSpell(heldItem);
```
with:
```java
            } else {
                if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    return true;
                }
                SpellType selected = MagicWandItem.getSelectedSpell(heldItem);
```
(`handleWandUse` only runs its cast branch on the client; `CombatEffects` is common code.)

- [ ] **Step 5: Grey the wheel.** In `WandRadialMenuScreen.render`, replace:
```java
        int iconRadius = 20;
```
with:
```java
        int iconRadius = 20;
        boolean hasPlayer = this.minecraft != null && this.minecraft.player != null;
        boolean stunned = hasPlayer && com.hogwartsmagic.spell.combat.CombatEffects.isStunned(this.minecraft.player);
        boolean castingBlocked = stunned
                || (hasPlayer && com.hogwartsmagic.spell.combat.CombatEffects.isSilenced(this.minecraft.player));
```
Replace:
```java
                drawFilledCircle(guiGraphics, sx, sy, iconRadius - 1, 0xFF000000 | spell.getColor());
                String initials = initialsFor(spell);
                guiGraphics.drawCenteredString(this.font, initials, sx, sy - 4, 0xFFFFFFFF);
```
with:
```java
                int iconColor = castingBlocked ? 0xFF4A4A4A : 0xFF000000 | spell.getColor();
                drawFilledCircle(guiGraphics, sx, sy, iconRadius - 1, iconColor);
                String initials = initialsFor(spell);
                guiGraphics.drawCenteredString(this.font, initials, sx, sy - 4, castingBlocked ? 0xFF9A9A9A : 0xFFFFFFFF);
```
Replace:
```java
        SpellType hoveredSpell = hoveredSlot >= 0 ? slots.get(hoveredSlot) : null;
        if (hoveredSpell != null) {
```
with:
```java
        if (castingBlocked) {
            String key = stunned ? ".wand_radial.stunned" : ".wand_radial.silenced";
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + key),
                    cx, cy + 22, 0xFFFF5555);
        }

        SpellType hoveredSpell = hoveredSlot >= 0 ? slots.get(hoveredSlot) : null;
        if (hoveredSpell != null) {
```
Binding a slot (left click) still works while silenced: choosing a spell isn't casting it. Releasing onto a slot goes through `selectSpellAndCast`, which the Step 4 guard stops.

- [ ] **Step 6: The client input lock.** Create `src/main/java/com/hogwartsmagic/client/StunInputLock.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the stun lock, driven by the stunned effect vanilla syncs to the local player (the
 * synced flag). LivingEntityStunMixin already zeroes movement in aiStep; this also clears the raw
 * input (no sneak or sprint toggles), swallows attack, use and pick-block clicks so no swing or packet
 * goes out, and drops an item use already in progress. The server enforces all of it on its own.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class StunInputLock {
    private StunInputLock() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!CombatEffects.isStunned(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && CombatEffects.isStunned(player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.isUsingItem() && CombatEffects.isStunned(player)) {
            player.stopUsingItem();
        }
    }
}
```

- [ ] **Step 7: Strings.** In `en_us.json`, directly after `  "commands.hogwartsmagic.crowdcontrol.reset": "Cleared diminishing returns on %s targets",` insert:
```json
  "gui.hogwartsmagic.wand_radial.stunned": "Stunned: no spells",
  "gui.hogwartsmagic.wand_radial.silenced": "Silenced: no spells",
```

- [ ] **Step 8: Check every server cast entry point is gated**

Run: `grep -n "isCastingBlocked" src/main/java/com/hogwartsmagic/network/SpellPackets.java`
Expected: three hits (cast, Imperio, Apparition).

- [ ] **Step 9: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 10: Commit**
```bash
git add src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/WandRadialMenuScreen.java src/main/java/com/hogwartsmagic/client/StunInputLock.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Refuse casting while stunned or silenced and lock a stunned player input" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests and dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `combat`, `mastery`, `Mixin` or any `client` class.

Then check JSON validity:
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/hogwartsmagic.mixins.json'))"`
Expected: no output.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed)

In a survival world with a wand, learned spells and cheats on:
1. Joining the world doesn't crash (this proves `FoodDataBleedingMixin` applied).
2. `/effect give @s hogwartsmagic:stunned 5`: you can't walk, jump, sneak, attack, place, break, eat or cast; looking around still works; gold stars circle your head; casting shows "You are stunned and can't cast!"; the wheel is grey with "Stunned: no spells". Knockback from a zombie still moves you.
3. `/effect give @e[type=minecraft:zombie,limit=1,sort=nearest] hogwartsmagic:stunned 5`: the zombie stands still and doesn't hit you, still falls if pushed off a ledge, and resumes attacking after 5s.
4. `/effect give @s hogwartsmagic:bleeding 6 0`: 1 damage per second with red particles; with a full food bar you don't regenerate; `/effect give @s minecraft:instant_health` heals 2 health instead of 4.
5. `/effect give @s hogwartsmagic:bleeding 6 2` deals 3 per second; amplifier 5 still deals 3.
6. Dying to bleeding shows "<name> bled out".
7. `/effect give @s hogwartsmagic:cursed_wound 30 1`: max health drops by 2 hearts. Add `/effect give @s minecraft:speed 30`, drink milk: speed is gone, the cursed wound stays. `/effect clear @s hogwartsmagic:cursed_wound` removes it.
8. `/effect give @s hogwartsmagic:brittle 30 0`, let a zombie hit you: about 30% more damage, glass-break sound, brittle is gone. With brittle again, `/damage @s 2 minecraft:magic` does not consume it.
9. `/effect give @s hogwartsmagic:silenced 10`: wheel grey with "Silenced: no spells", casts refused with "You are silenced and can't cast!", melee still works. Start a Protego Maxima channel (look up, hold cast), then silence yourself: the barrier drops.
10. `/effect give @e[type=hogwartsmagic:wizard,limit=1,sort=nearest] hogwartsmagic:silenced 20` next to a hostile Death Eater: it fires no beams for 20s.
11. `/crowdcontrol stun @e[type=minecraft:zombie,limit=1,sort=nearest] 4` reports 80 ticks; again within 6s, 40 ticks; a third time, "resisted"; 6s after the second, 80 ticks again.
12. `/summon hogwartsmagic:voldemort`, then `/crowdcontrol stun @e[type=hogwartsmagic:voldemort,limit=1] 4` reports 40 ticks.
13. PvP (LAN with a second client): player A runs `/crowdcontrol stun <B> 5`: 60 ticks. Set `pvpCrowdControlReduction = false` in `run/config/hogwartsmagic-common.toml`, save, `/crowdcontrol reset <B>`, repeat: 100 ticks.
14. Mastery config sync: with the world open, set `masteryXpAdept = 10` in `run/config/hogwartsmagic-common.toml` and save. Within a few seconds the Grimoire shows "10 XP to rank II" on a Novice spell, and the bar stays inside its frame. (On a dedicated server, edit the server config: the client follows without its own config changing.)
15. `/spellmastery setrank @s stupefy 4`: max mana on the bar rises by 3 at once. `/spellmastery reset @s`: max mana drops at once and current mana is clamped to it.
16. PvP XP (LAN): Stupefy the other player: `/spellmastery get @s` shows +0.5 Stupefy XP. Glacius the same player within 30s: no Glacius XP. After 30s, Glacius pays 0.5.
17. Custom-NBT spawner: `/setblock ~2 ~ ~ spawner{SpawnData:{entity:{id:"minecraft:zombie",CustomName:'"Bob"'}}}`, wait for a spawn, `/data get entity @e[name=Bob,limit=1] Tags` lists `hogwartsmagic:from_spawner`.
18. Reparo at rank IV (`/spellmastery setrank @s reparo 4`) spends about 24% less mana per repaired block than at rank I.
19. Grimoire on Apparition: scrolling over the detail column reveals every "Found in" line, a gold scrollbar shows, and switching spells resets the scroll.
20. Grimoire rank line colours look as before (I green, II blue, III purple, IV red).

- [ ] **Step 3: Record results.** Fix any failed check in a follow-up commit whose message says what was wrong. Then tell the user Phase 2 is ready to push.

---

## Self-Review

- **Spec coverage, Phase 2:**
  - `stunned`: movement and attack lock, mob AI paused, player input zeroed on the server and on the client through the synced effect (T7 mixin and hooks, T9 `StunInputLock`); no casting (T9).
  - `bleeding`: damage over time, stacks to 3, no natural regeneration, healing ×0.5 (T5, T6, T7).
  - `cursed_wound`: max-health modifier that milk can't remove (T6, T7 `onEffectRemove`).
  - `brittle`: next physical or explosion hit +30% per level, then consumed (T5, T7).
  - `silenced`: refused server-side at all three cast entry points, NPC beams refused, wheel greyed (T7, T9).
  - Diminishing returns in a pure tested class behind one entry point, `CrowdControl.apply` (T5, T8).
  - Boss tag with the four bosses, half duration, `CombatTags.isBoss` for later phases (T8). Obliviate, Confundo, Avada and the Imperio caps are Phase 6 behaviours that read this tag.
  - PvP 60% with a config toggle (T8).
  - Nothing applies the effects yet except `/effect`, `/crowdcontrol` and tests.
- **Spec coverage, Phase 1 follow-ups:** config sync on login and reload, client reads use the synced copy, HUD bar clamped (T1, T2); max mana resent and clamped after any rank change (T2); PvP XP rule (T1, T3); custom-NBT spawners (T3); Reparo discount (T3); scrollable Grimoire (T4); HitLedger expiry, NaN guards, threshold warning, unused local, shared allied constant, rank colours (T1, T2, T3, T4).
- **Placeholder scan:** no TBD or "similar to". Line numbers marked `~` are anchors only; every edit also quotes the text to find. Phase 1b may shift line numbers in `Config.java`, `SpellPackets.java`, `ExampleMod.java` and `en_us.json`; all anchors are text that Phase 1b doesn't change.
- **Signature consistency:** `CrowdControl.apply(LivingEntity, Holder<MobEffect>, int, [int,] Entity)` returns `int` in T8 and is used that way in `CrowdControlCommand`. `CombatEffects.isCastingBlocked(LivingEntity)` and `notifyCastBlocked(Player)` are defined in T7 and used in T9. `MasteryConfig.of(Player)` returns `Values` with `thresholds()`, `rankDiscount()`, `manaPerRank()`, `manaCap()` in T2, used by `SpellMasteryData`, `SpellCosts` and the Grimoire. `PlayerMagicData.resyncMana(ServerPlayer)` is defined and used in T2. `HitLedger.isOpen(String, long)` (T1) is used in T3. `MasteryRules.targetMultiplier` 4-argument form (T1) is used in T3. `CombatTags.ALLIED` is created in T3 and kept when T8 rewrites the file. `BleedRules.isDamageTick/damagePerSecond/stacks` (T5) are used in `BleedingEffect` (T6) and `CombatEffects` (T7).
- **Known limits, on purpose:** a stunned horse that nobody rides isn't immobile (vanilla's `AbstractHorse.isImmobile` doesn't call super unless ridden); a hacked client can still send movement packets while stunned (vanilla movement is client-authoritative; anti-cheat is out of scope); a boss's custom attacks driven from its own `tick()` rather than its AI aren't paused by the stun; an infinite-duration bleeding (`/effect ... infinite`) deals no damage.
