# Spell Mastery Phase 3 (Disabling Spells) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stupefy, Petrificus Totalus, Incarcerous, Glacius and Levicorpus stop being five flavours of "Slowness X". Each disables in its own way (stun, toppled lock that breaks on damage, tether, freeze build-up into brittle, hang), with a bespoke Novice identity, a bespoke Master capstone, and numeric scaling at Adept and Expert. Every hard crowd control goes through Phase 2's `CrowdControl`, so diminishing returns, the boss rule and the PvP rule always apply.

**Architecture:** All per-rank numbers live in Minecraft-free `*Rules` classes plus a `TetherMath` helper in `spell/disable/`, each with JUnit tests. One Minecraft-bound class per spell (`StupefySpell`, `PetrificusSpell`, `IncarcerousTether`, `GlaciusSpell`, `LevicorpusSpell`) holds the hit behaviour and that spell's own Forge event hooks; `SpellType.applyHitEffect` just delegates to them. `DisableSupport` reads the caster's rank (players: `SpellMasteryData.getRank`; NPCs: 1), applies hidden marker effects through `CrowdControl.scaleDuration`, interrupts casters and picks chain/AoE victims. Three new hidden marker effects (`petrified`, `glacius_chill`, `glacius_frozen`) store the caster's rank in their amplifier, so later hooks need no extra state. Vanilla never syncs a mob's effects to watching clients, so `VisibleEffects` broadcasts the two pose markers (`levicorpus`, `petrified`) with vanilla's own effect packets; the client renders the topple and the upside-down hang from them. Tethers and the Levicorpus hover are enforced server-side every tick from anchors stored in the entity's persistent NBT.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 3: Disabling spells", "Decisions that change the design doc" (Decision 1: Novice identity + Master capstone bespoke, Adept/Expert numeric only) and "Global constraints". Numbers the roadmap leaves open come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` section "A. Disabling spells". Where they disagree, the roadmap wins.

**Depends on Phase 2 (must be merged first):** this plan calls, by exact signature:
- `CrowdControl.apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, @Nullable Entity source)` → `int` ticks applied (0 = nothing landed)
- `CrowdControl.scaleDuration(LivingEntity target, int baseTicks, @Nullable Entity source)` → `int` (consumes one diminishing-returns step)
- `CombatEffects.responsible(@Nullable Entity)`, `CombatEffects.isStunned(LivingEntity)`, `CombatEffects.isCastingBlocked(LivingEntity)`, `CombatEffects.applyBrittle(LivingEntity target, int ticks, int amplifier, @Nullable Entity source)`
- `BrittleRules.bonusMultiplier(int amplifier)` (tests only)
- `CombatTags.ALLIED`
- `ExampleMod.STUNNED`, `ExampleMod.BRITTLE` (`RegistryObject<MobEffect>`)
- Phase 2's last `en_us.json` line `"gui.hogwartsmagic.wand_radial.silenced": "Silenced: no spells",` and its `SILENCED` registration in `ExampleMod.java` are used as text anchors.

Before Task 1, confirm Phase 2 is in: `grep -n "public static int scaleDuration" src/main/java/com/hogwartsmagic/spell/combat/CrowdControl.java` must print one line. If it doesn't, stop: Phase 2 isn't merged.

## Global Constraints

- **Ranks:** every per-rank behaviour reads the caster's rank with `SpellMasteryData.getRank(player, spell)` (1..4) through `DisableSupport.casterRank`. **NPC casters (Students, Death Eaters/`WizardEntity`, Voldemort) and ownerless beams are rank 1.**
- **Hard crowd control** (stun, petrify, freeze, bind, hang) goes through `CrowdControl.apply` (for `stunned`) or Phase 2's hidden-effect overload `CrowdControl.apply(target, effect, baseTicks, amplifier, source, false)` via `DisableSupport.applyHiddenCrowdControl` (for the hidden `incarcerous_chains` and `levicorpus` markers). Never call `addEffect` for a hard-CC effect directly. Secondary markers (`petrified`, `glacius_frozen`) are added only after `CrowdControl.apply` landed, with exactly the ticks it returned.
- **Per-rank numbers (ticks at 20/s):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Stupefy stun | 30, interrupt | 40, knockback 0.4 | 50, knockback 0.4 | 50, knockback 0.4, chains to 1 enemy within 5 blocks for 25, beam speed ×1.25 |
  | Petrificus stun + topple | 100, any damage breaks it | 140, hits under 4 damage don't break it | 180, same | 240, same; the breaking hit deals ×1.5 |
  | Incarcerous | main 100, leash 2 | main 110; enemies within 2.5 for 70 | main 120; radius 3.5 for 80; flyers pulled down | main 140; radius 3.5 for 140; leash 1; bound to the main target, shared knockback |
  | Glacius | Slowness II 80 (+40 in cold biomes) and a chill window of 80; a hit on a chilled target freezes it solid: stun 40 + brittle amp 0; surface water under the target turns to frosted ice | slow 100, window 90, freeze 50 | slow 120, window 100, freeze 60, brittle amp 1 (+60%) | slow 140, window 120, freeze 70, brittle amp 1; a frozen target that dies shatters: Slowness III for 60 to everything within 4 blocks except the caster |
  | Levicorpus | hang 60, 1.5 blocks up, hovering; no item use or shields | 80, takes ×1.25 damage | 100, ×1.25 | 120, ×1.25; recasting on a hanging target drops it for 3 fall damage |

  Brittle lingers 60 ticks past the freeze so the follow-up hit can land. Fire thaws a frozen target at once.
- **Cut on purpose (Decision 1, Adept/Expert get numbers only):** Stupefy's Expert "4s on a mid-windup target", Glacius Expert lava→obsidian and Master "Glacius Tria" cone, Levicorpus Expert disarm chance, Petrificus Expert flyer drop. Gravity mobs already fall while petrified.
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/` (or an existing `Dist.CLIENT` subscriber such as `event/ClientForgeEvents`), is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch or a `Dist.CLIENT` event subscriber, and talks back through C2S packets the server validates. This plan adds no C2S packets: the client only reads effects the server syncs.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine. (`SpellType.applyHitEffect` is common code; its existing switch stays.)
- Screens: `renderTransparentBackground`, no blur. (This plan adds no screens.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Terrain changes respect `mobGriefing`, claims, spawn protection and Cave Inimicum wards. Glacius only *places* ice, so `TerrainRules.canPlace` checks the ward, `ServerLevel.mayInteract` (spawn protection, world border) and fires `BlockEvent.EntityPlaceEvent` for a player caster (the claim-mod hook for placement; `BreakEvent` would wrongly feed Reparo's broken-block log and wake the Ironbelly); an NPC caster needs `mobGriefing`.
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses none).
- `en_us.json` must stay valid JSON after every edit: every inserted line ends with a comma, and the insertion point is after an existing line that already ends with a comma. Replacements keep the original line's trailing comma.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/disable/RankIndex.java` | Pure: rank 1..4 → table index, clamped |
| Create `src/main/java/com/hogwartsmagic/spell/disable/StupefyRules.java` | Pure: Stupefy numbers |
| Create `src/main/java/com/hogwartsmagic/spell/disable/PetrificusRules.java` | Pure: Petrificus numbers, break rule |
| Create `src/main/java/com/hogwartsmagic/spell/disable/IncarcerousRules.java` | Pure: Incarcerous numbers |
| Create `src/main/java/com/hogwartsmagic/spell/disable/GlaciusRules.java` | Pure: Glacius numbers |
| Create `src/main/java/com/hogwartsmagic/spell/disable/LevicorpusRules.java` | Pure: Levicorpus numbers |
| Create `src/main/java/com/hogwartsmagic/spell/disable/TetherMath.java` | Pure: leash velocity correction and snapping |
| Create `src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java` | Caster rank, hidden hard-CC helper, interrupt, enemy picking, snapping |
| Create `src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java` | Broadcasts the pose markers to watching clients |
| Create `src/main/java/com/hogwartsmagic/spell/TerrainRules.java` | Ward, spawn protection, claim and mobGriefing check before placing blocks |
| Create `src/main/java/com/hogwartsmagic/spell/disable/StupefySpell.java` | Stun, interrupt, knockback, Master chain |
| Create `src/main/java/com/hogwartsmagic/spell/disable/PetrificusSpell.java` | Stun + petrified marker, break-on-damage hook |
| Create `src/main/java/com/hogwartsmagic/spell/disable/IncarcerousTether.java` | Tether apply and tick, flyer pull, Master binding and shared knockback |
| Create `src/main/java/com/hogwartsmagic/spell/disable/GlaciusSpell.java` | Chill, freeze solid, frosted ice, fire thaw, Master shatter |
| Create `src/main/java/com/hogwartsmagic/spell/disable/LevicorpusSpell.java` | Hang, hover tick, item and shield lock, damage bonus, Liberacorpus |
| Create `src/main/java/com/hogwartsmagic/client/HangInputLock.java` | Client: zero movement input while hanging |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Five cases delegate to the new classes; drop `isColdEnvironment` |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Glacius block hits go through `TerrainRules` |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | Stupefy Master beam speed |
| Modify `src/main/java/com/hogwartsmagic/spell/IncarcerousChainAnimation.java` | `renderLink` for bound pairs |
| Modify `src/main/java/com/hogwartsmagic/event/ClientForgeEvents.java` | Petrificus topple render |
| Modify `src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java` | Stun/silence lock out his casting, Incarcerous no longer does; `interruptCasting()` |
| Modify `src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java` | A stunned or hanging elf can't Disapparate |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Three marker effects |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Marker names, five spell descriptions |
| Create tests under `src/test/java/com/hogwartsmagic/spell/disable/` | Unit tests |

The existing `mixin/client/LevicorpusLivingEntityRendererMixin.java` is **not** registered in `hogwartsmagic.mixins.json`; the live upside-down render is the `RenderLivingEvent` code in `event/ClientForgeEvents.java`. Leave the mixin unregistered (registering it would flip the body twice, back upright). The topple reuses the `ClientForgeEvents` approach.

---

### Task 1: The pure rules and tether math

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/disable/RankIndex.java`, `StupefyRules.java`, `PetrificusRules.java`, `IncarcerousRules.java`, `GlaciusRules.java`, `LevicorpusRules.java`, `TetherMath.java`
- Test: `src/test/java/com/hogwartsmagic/spell/disable/StupefyRulesTest.java`, `PetrificusRulesTest.java`, `IncarcerousRulesTest.java`, `GlaciusRulesTest.java`, `LevicorpusRulesTest.java`, `TetherMathTest.java`

**Interfaces:**
- Consumes: `BrittleRules.bonusMultiplier(int)` (Phase 2, test only)
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private)
  - `StupefyRules.CHAIN_RADIUS = 5.0`, `MASTER_BEAM_SPEED = 1.25f`; `static int stunTicks(int rank)`, `static double knockback(int rank)`, `static boolean chains(int rank)`, `static int chainStunTicks(int rank)`, `static float beamSpeedMultiplier(int rank)`
  - `PetrificusRules.MASTER_SHATTER = 1.5f`; `static int durationTicks(int rank)`, `static float breakThreshold(int rank)`, `static boolean breaks(int rank, float amount)`, `static float shatterMultiplier(int rank)`
  - `IncarcerousRules.PULL_DOWN_SPEED = 0.35`, `PULL_DOWN_VERTICAL_SLACK = 0.5`, `GROUP_SEARCH_RADIUS = 12.0`; `static int mainTicks(int rank)`, `static int secondaryTicks(int rank)`, `static double secondaryRadius(int rank)`, `static double leash(int rank)`, `static boolean pullsFlyersDown(int rank)`, `static boolean bindsTogether(int rank)`, `static double boundLeash(int rank, double mainWidth, double otherWidth)`
  - `GlaciusRules.SLOW_AMPLIFIER = 1`, `COLD_BONUS_TICKS = 40`, `CHILL_FROST_TICKS = 100`, `BRITTLE_LINGER_TICKS = 60`, `SHATTER_RADIUS = 4.0`, `SHATTER_SLOW_TICKS = 60`, `SHATTER_SLOW_AMPLIFIER = 2`; `static int slowTicks(int rank, boolean cold)`, `static int chillWindowTicks(int rank)`, `static int freezeTicks(int rank)`, `static int brittleAmplifier(int rank)`, `static int brittleTicks(int appliedFreezeTicks)`, `static boolean shatters(int rank)`
  - `LevicorpusRules.HANG_HEIGHT = 1.5`, `HOLD_GAIN = 0.5`, `SNAP_DISTANCE = 1.0`, `LIBERACORPUS_FALL_DAMAGE = 3.0f`, `LIBERACORPUS_DROP_SPEED = -1.2`; `static int hangTicks(int rank)`, `static float damageTakenMultiplier(int rank)`, `static boolean liberacorpus(int rank)`
  - `TetherMath.PULL_GAIN = 0.4`, `MAX_PULL = 0.6`, `SNAP_SLACK = 2.0`; `static boolean isOutside(double dx, double dy, double dz, double leash)`, `static boolean shouldSnap(double dx, double dy, double dz, double leash)`, `static double[] correctedVelocity(double dx, double dy, double dz, double leash, double vx, double vy, double vz)`, `static double[] snapPoint(double ax, double ay, double az, double dx, double dy, double dz, double leash)` (d = position minus anchor)

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/disable/StupefyRulesTest.java`:
```java
package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StupefyRulesTest {
    @Test
    void stunGrowsToTwoAndAHalfSeconds() {
        assertEquals(30, StupefyRules.stunTicks(1));
        assertEquals(40, StupefyRules.stunTicks(2));
        assertEquals(50, StupefyRules.stunTicks(3));
        assertEquals(50, StupefyRules.stunTicks(4));
    }

    @Test
    void ranksOutsideOneToFourAreClamped() {
        assertEquals(30, StupefyRules.stunTicks(0));
        assertEquals(50, StupefyRules.stunTicks(9));
    }

    @Test
    void knockbackStartsAtAdept() {
        assertEquals(0.0, StupefyRules.knockback(1), 1e-9);
        assertEquals(0.4, StupefyRules.knockback(2), 1e-9);
        assertEquals(0.4, StupefyRules.knockback(4), 1e-9);
    }

    @Test
    void onlyMasterChainsForHalfTheStun() {
        assertFalse(StupefyRules.chains(3));
        assertTrue(StupefyRules.chains(4));
        assertEquals(25, StupefyRules.chainStunTicks(4));
        assertEquals(5.0, StupefyRules.CHAIN_RADIUS, 1e-9);
    }

    @Test
    void masterBeamIsAQuarterFaster() {
        assertEquals(1.0f, StupefyRules.beamSpeedMultiplier(3), 1e-6);
        assertEquals(1.25f, StupefyRules.beamSpeedMultiplier(4), 1e-6);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/disable/PetrificusRulesTest.java`:
```java
package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetrificusRulesTest {
    @Test
    void durationsAreFiveSevenNineTwelveSeconds() {
        assertEquals(100, PetrificusRules.durationTicks(1));
        assertEquals(140, PetrificusRules.durationTicks(2));
        assertEquals(180, PetrificusRules.durationTicks(3));
        assertEquals(240, PetrificusRules.durationTicks(4));
    }

    @Test
    void noviceBreaksOnAnyDamage() {
        assertTrue(PetrificusRules.breaks(1, 0.1f));
        assertFalse(PetrificusRules.breaks(1, 0.0f));
        assertFalse(PetrificusRules.breaks(1, Float.NaN));
    }

    @Test
    void fromAdeptHitsUnderTwoHeartsDoNotBreak() {
        assertFalse(PetrificusRules.breaks(2, 3.9f));
        assertTrue(PetrificusRules.breaks(2, 4.0f));
        assertFalse(PetrificusRules.breaks(4, 3.9f));
        assertTrue(PetrificusRules.breaks(4, 10.0f));
    }

    @Test
    void onlyMasterShatters() {
        assertEquals(1.0f, PetrificusRules.shatterMultiplier(3), 1e-6);
        assertEquals(1.5f, PetrificusRules.shatterMultiplier(4), 1e-6);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/disable/IncarcerousRulesTest.java`:
```java
package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncarcerousRulesTest {
    @Test
    void mainAndSecondaryDurations() {
        assertEquals(100, IncarcerousRules.mainTicks(1));
        assertEquals(140, IncarcerousRules.mainTicks(4));
        assertEquals(0, IncarcerousRules.secondaryTicks(1));
        assertEquals(70, IncarcerousRules.secondaryTicks(2));
        assertEquals(80, IncarcerousRules.secondaryTicks(3));
        assertEquals(140, IncarcerousRules.secondaryTicks(4));
    }

    @Test
    void radiusGrowsAndMasterShortensTheLeash() {
        assertEquals(0.0, IncarcerousRules.secondaryRadius(1), 1e-9);
        assertEquals(2.5, IncarcerousRules.secondaryRadius(2), 1e-9);
        assertEquals(3.5, IncarcerousRules.secondaryRadius(3), 1e-9);
        assertEquals(2.0, IncarcerousRules.leash(3), 1e-9);
        assertEquals(1.0, IncarcerousRules.leash(4), 1e-9);
    }

    @Test
    void expertPullsFlyersMasterBinds() {
        assertFalse(IncarcerousRules.pullsFlyersDown(2));
        assertTrue(IncarcerousRules.pullsFlyersDown(3));
        assertFalse(IncarcerousRules.bindsTogether(3));
        assertTrue(IncarcerousRules.bindsTogether(4));
    }

    @Test
    void boundLeashCountsBothBodies() {
        assertEquals(1.6, IncarcerousRules.boundLeash(4, 0.6, 0.6), 1e-9);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/disable/GlaciusRulesTest.java`:
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.spell.combat.BrittleRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlaciusRulesTest {
    @Test
    void slowLastsLongerWithRankAndInTheCold() {
        assertEquals(80, GlaciusRules.slowTicks(1, false));
        assertEquals(120, GlaciusRules.slowTicks(1, true));
        assertEquals(140, GlaciusRules.slowTicks(4, false));
    }

    @Test
    void windowAndFreezeScale() {
        assertEquals(80, GlaciusRules.chillWindowTicks(1));
        assertEquals(120, GlaciusRules.chillWindowTicks(4));
        assertEquals(40, GlaciusRules.freezeTicks(1));
        assertEquals(50, GlaciusRules.freezeTicks(2));
        assertEquals(70, GlaciusRules.freezeTicks(4));
    }

    @Test
    void expertBrittleIsSixtyPercent() {
        assertEquals(1.3f, BrittleRules.bonusMultiplier(GlaciusRules.brittleAmplifier(2)), 1e-4);
        assertEquals(1.6f, BrittleRules.bonusMultiplier(GlaciusRules.brittleAmplifier(3)), 1e-4);
        assertEquals(1.6f, BrittleRules.bonusMultiplier(GlaciusRules.brittleAmplifier(4)), 1e-4);
    }

    @Test
    void brittleOutlastsTheFreeze() {
        assertEquals(100, GlaciusRules.brittleTicks(40));
    }

    @Test
    void onlyMasterShatters() {
        assertFalse(GlaciusRules.shatters(3));
        assertTrue(GlaciusRules.shatters(4));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/disable/LevicorpusRulesTest.java`:
```java
package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevicorpusRulesTest {
    @Test
    void hangsThreeToSixSeconds() {
        assertEquals(60, LevicorpusRules.hangTicks(1));
        assertEquals(80, LevicorpusRules.hangTicks(2));
        assertEquals(100, LevicorpusRules.hangTicks(3));
        assertEquals(120, LevicorpusRules.hangTicks(4));
    }

    @Test
    void damageBonusFromAdept() {
        assertEquals(1.0f, LevicorpusRules.damageTakenMultiplier(1), 1e-6);
        assertEquals(1.25f, LevicorpusRules.damageTakenMultiplier(2), 1e-6);
        assertEquals(1.25f, LevicorpusRules.damageTakenMultiplier(4), 1e-6);
    }

    @Test
    void onlyMasterCanDrop() {
        assertFalse(LevicorpusRules.liberacorpus(3));
        assertTrue(LevicorpusRules.liberacorpus(4));
    }

    @Test
    void dropDealsSixBlocksOfFallDamage() {
        // Vanilla fall damage is distance minus 3 safe blocks.
        assertEquals(6.0f - 3.0f, LevicorpusRules.LIBERACORPUS_FALL_DAMAGE, 1e-6);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/disable/TetherMathTest.java`:
```java
package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetherMathTest {
    @Test
    void insideTheLeashVelocityIsUntouched() {
        assertArrayEquals(new double[]{0.3, 0.1, -0.2},
                TetherMath.correctedVelocity(1, 0, 0, 2, 0.3, 0.1, -0.2), 1e-9);
    }

    @Test
    void outwardMotionIsCancelledAndPulledBack() {
        assertArrayEquals(new double[]{-0.4, 0, 0},
                TetherMath.correctedVelocity(3, 0, 0, 2, 0.5, 0, 0), 1e-9);
    }

    @Test
    void sidewaysMotionSurvives() {
        assertArrayEquals(new double[]{-0.4, 0, 0.3},
                TetherMath.correctedVelocity(3, 0, 0, 2, 0, 0, 0.3), 1e-9);
    }

    @Test
    void inwardMotionIsKept() {
        assertArrayEquals(new double[]{-0.6, 0, 0},
                TetherMath.correctedVelocity(3, 0, 0, 2, -0.2, 0, 0), 1e-9);
    }

    @Test
    void pullIsCapped() {
        assertArrayEquals(new double[]{-0.6, 0, 0},
                TetherMath.correctedVelocity(10, 0, 0, 2, 0, 0, 0), 1e-9);
    }

    @Test
    void outsideAndSnapThresholds() {
        assertTrue(TetherMath.isOutside(2.01, 0, 0, 2));
        assertFalse(TetherMath.isOutside(2.0, 0, 0, 2));
        assertTrue(TetherMath.shouldSnap(4.01, 0, 0, 2));
        assertFalse(TetherMath.shouldSnap(3.99, 0, 0, 2));
    }

    @Test
    void snapPointSitsOnTheLeash() {
        assertArrayEquals(new double[]{10, 64, 12},
                TetherMath.snapPoint(10, 64, 10, 0, 0, 5, 2), 1e-9);
    }

    @Test
    void zeroLengthIsSafe() {
        assertArrayEquals(new double[]{0.1, 0.2, 0.3},
                TetherMath.correctedVelocity(0, 0, 0, 0, 0.1, 0.2, 0.3), 1e-9);
        assertArrayEquals(new double[]{5, 6, 7},
                TetherMath.snapPoint(5, 6, 7, 0, 0, 0, 2), 1e-9);
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.disable.*"`
Expected: compile FAIL, `cannot find symbol: class StupefyRules` (and the other five classes).

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/disable/RankIndex.java`:
```java
package com.hogwartsmagic.spell.disable;

/** Maps a mastery rank (1..4) to a table index (0..3). Anything outside is clamped; NPC casters pass 1. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/disable/StupefyRules.java`:
```java
package com.hogwartsmagic.spell.disable;

/** Stupefy: the fastest true stun, the interrupt button. Master chains to one more enemy. */
public final class StupefyRules {
    private static final int[] STUN_TICKS = {30, 40, 50, 50};
    private static final double[] KNOCKBACK = {0.0, 0.4, 0.4, 0.4};
    public static final double CHAIN_RADIUS = 5.0;
    public static final float MASTER_BEAM_SPEED = 1.25f;

    private StupefyRules() {
    }

    public static int stunTicks(int rank) {
        return STUN_TICKS[RankIndex.of(rank)];
    }

    public static double knockback(int rank) {
        return KNOCKBACK[RankIndex.of(rank)];
    }

    public static boolean chains(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** The chained enemy gets half the stun. */
    public static int chainStunTicks(int rank) {
        return stunTicks(rank) / 2;
    }

    public static float beamSpeedMultiplier(int rank) {
        return chains(rank) ? MASTER_BEAM_SPEED : 1.0f;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/disable/PetrificusRules.java`:
```java
package com.hogwartsmagic.spell.disable;

/** Petrificus Totalus: a long lock that breaks on damage. Master makes the breaking hit a shatter. */
public final class PetrificusRules {
    private static final int[] DURATION_TICKS = {100, 140, 180, 240};
    /** A hit must deal at least this much (before armour) to break the lock; 0 means any damage. */
    private static final float[] BREAK_THRESHOLD = {0.0f, 4.0f, 4.0f, 4.0f};
    public static final float MASTER_SHATTER = 1.5f;

    private PetrificusRules() {
    }

    public static int durationTicks(int rank) {
        return DURATION_TICKS[RankIndex.of(rank)];
    }

    public static float breakThreshold(int rank) {
        return BREAK_THRESHOLD[RankIndex.of(rank)];
    }

    /** NaN and non-positive damage never break it. */
    public static boolean breaks(int rank, float amount) {
        return amount > 0.0f && amount >= breakThreshold(rank);
    }

    public static float shatterMultiplier(int rank) {
        return RankIndex.of(rank) == 3 ? MASTER_SHATTER : 1.0f;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/disable/IncarcerousRules.java`:
```java
package com.hogwartsmagic.spell.disable;

/** Incarcerous: a tether. Bound targets can fight, but not leave the spot. */
public final class IncarcerousRules {
    private static final int[] MAIN_TICKS = {100, 110, 120, 140};
    private static final int[] SECONDARY_TICKS = {0, 70, 80, 140};
    private static final double[] SECONDARY_RADIUS = {0.0, 2.5, 3.5, 3.5};
    private static final double[] LEASH = {2.0, 2.0, 2.0, 1.0};
    /** Downward speed forced on a tethered flyer that is above its anchor (Expert and up). */
    public static final double PULL_DOWN_SPEED = 0.35;
    /** How far above its ground anchor a pulled-down target may be before it is forced down. */
    public static final double PULL_DOWN_VERTICAL_SLACK = 0.5;
    /** Master: how far to look for the other members of a bound group when sharing knockback. */
    public static final double GROUP_SEARCH_RADIUS = 12.0;

    private IncarcerousRules() {
    }

    public static int mainTicks(int rank) {
        return MAIN_TICKS[RankIndex.of(rank)];
    }

    public static int secondaryTicks(int rank) {
        return SECONDARY_TICKS[RankIndex.of(rank)];
    }

    public static double secondaryRadius(int rank) {
        return SECONDARY_RADIUS[RankIndex.of(rank)];
    }

    public static double leash(int rank) {
        return LEASH[RankIndex.of(rank)];
    }

    public static boolean pullsFlyersDown(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static boolean bindsTogether(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Leash between two bound bodies: the rank leash measured edge to edge, not centre to centre. */
    public static double boundLeash(int rank, double mainWidth, double otherWidth) {
        return leash(rank) + (mainWidth + otherWidth) * 0.5;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/disable/GlaciusRules.java`:
```java
package com.hogwartsmagic.spell.disable;

/** Glacius: chill first, freeze solid on the next hit, brittle for the payoff. Master frozen deaths shatter. */
public final class GlaciusRules {
    private static final int[] SLOW_TICKS = {80, 100, 120, 140};
    private static final int[] CHILL_WINDOW_TICKS = {80, 90, 100, 120};
    private static final int[] FREEZE_TICKS = {40, 50, 60, 70};
    private static final int[] BRITTLE_AMPLIFIER = {0, 0, 1, 1};
    /** Slowness II. */
    public static final int SLOW_AMPLIFIER = 1;
    public static final int COLD_BONUS_TICKS = 40;
    /** Vanilla frost overlay for a chilled target; under the 140 that counts as fully frozen. */
    public static final int CHILL_FROST_TICKS = 100;
    public static final int BRITTLE_LINGER_TICKS = 60;
    public static final double SHATTER_RADIUS = 4.0;
    public static final int SHATTER_SLOW_TICKS = 60;
    /** Slowness III. */
    public static final int SHATTER_SLOW_AMPLIFIER = 2;

    private GlaciusRules() {
    }

    public static int slowTicks(int rank, boolean cold) {
        return SLOW_TICKS[RankIndex.of(rank)] + (cold ? COLD_BONUS_TICKS : 0);
    }

    public static int chillWindowTicks(int rank) {
        return CHILL_WINDOW_TICKS[RankIndex.of(rank)];
    }

    public static int freezeTicks(int rank) {
        return FREEZE_TICKS[RankIndex.of(rank)];
    }

    /** Brittle amplifier: 0 is +30%, 1 is +60% (see BrittleRules). */
    public static int brittleAmplifier(int rank) {
        return BRITTLE_AMPLIFIER[RankIndex.of(rank)];
    }

    /** Brittle stays a little after the thaw, so the follow-up hit still lands on it. */
    public static int brittleTicks(int appliedFreezeTicks) {
        return Math.max(0, appliedFreezeTicks) + BRITTLE_LINGER_TICKS;
    }

    public static boolean shatters(int rank) {
        return RankIndex.of(rank) == 3;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/disable/LevicorpusRules.java`:
```java
package com.hogwartsmagic.spell.disable;

/** Levicorpus: the target hangs helpless. Master recasts drop it hard (Liberacorpus). */
public final class LevicorpusRules {
    private static final int[] HANG_TICKS = {60, 80, 100, 120};
    private static final float[] DAMAGE_TAKEN = {1.0f, 1.25f, 1.25f, 1.25f};
    public static final double HANG_HEIGHT = 1.5;
    /** Fraction of the gap to the anchor closed each tick. */
    public static final double HOLD_GAIN = 0.5;
    /** Further than this from the anchor and the target is put back at once. */
    public static final double SNAP_DISTANCE = 1.0;
    /** Fall damage for a 6-block fall: vanilla takes off 3 safe blocks. */
    public static final float LIBERACORPUS_FALL_DAMAGE = 3.0f;
    public static final double LIBERACORPUS_DROP_SPEED = -1.2;

    private LevicorpusRules() {
    }

    public static int hangTicks(int rank) {
        return HANG_TICKS[RankIndex.of(rank)];
    }

    public static float damageTakenMultiplier(int rank) {
        return DAMAGE_TAKEN[RankIndex.of(rank)];
    }

    public static boolean liberacorpus(int rank) {
        return RankIndex.of(rank) == 3;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/disable/TetherMath.java`:
```java
package com.hogwartsmagic.spell.disable;

/**
 * Leash arithmetic, Minecraft-free. {@code d} is always the target position minus the anchor. Inside
 * the leash nothing changes; outside it, motion away from the anchor is cancelled and a pull back
 * proportional to the overshoot is added (capped). Far outside, the caller teleports the target to
 * {@link #snapPoint}, so lag or a big knockback can never carry it away.
 */
public final class TetherMath {
    public static final double PULL_GAIN = 0.4;
    public static final double MAX_PULL = 0.6;
    public static final double SNAP_SLACK = 2.0;

    private TetherMath() {
    }

    private static double length(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean isOutside(double dx, double dy, double dz, double leash) {
        return length(dx, dy, dz) > leash;
    }

    public static boolean shouldSnap(double dx, double dy, double dz, double leash) {
        return length(dx, dy, dz) > leash + SNAP_SLACK;
    }

    public static double[] correctedVelocity(double dx, double dy, double dz, double leash,
                                             double vx, double vy, double vz) {
        double len = length(dx, dy, dz);
        if (!(len > leash) || len < 1.0E-6) {
            return new double[]{vx, vy, vz};
        }
        double nx = dx / len;
        double ny = dy / len;
        double nz = dz / len;
        double outward = vx * nx + vy * ny + vz * nz;
        if (outward > 0.0) {
            vx -= nx * outward;
            vy -= ny * outward;
            vz -= nz * outward;
        }
        double pull = Math.min(MAX_PULL, (len - leash) * PULL_GAIN);
        return new double[]{vx - nx * pull, vy - ny * pull, vz - nz * pull};
    }

    /** The point on the leash in the direction of {@code d}; the anchor itself when {@code d} is zero. */
    public static double[] snapPoint(double ax, double ay, double az, double dx, double dy, double dz, double leash) {
        double len = length(dx, dy, dz);
        if (len < 1.0E-6) {
            return new double[]{ax, ay, az};
        }
        double scale = leash / len;
        return new double[]{ax + dx * scale, ay + dy * scale, az + dz * scale};
    }
}
```

- [ ] **Step 4: Run them and confirm they pass**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.disable.*"`
Expected: PASS. `StupefyRulesTest` 5 tests, `PetrificusRulesTest` 4, `IncarcerousRulesTest` 4, `GlaciusRulesTest` 5, `LevicorpusRulesTest` 4, `TetherMathTest` 8.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/disable/RankIndex.java src/main/java/com/hogwartsmagic/spell/disable/StupefyRules.java src/main/java/com/hogwartsmagic/spell/disable/PetrificusRules.java src/main/java/com/hogwartsmagic/spell/disable/IncarcerousRules.java src/main/java/com/hogwartsmagic/spell/disable/GlaciusRules.java src/main/java/com/hogwartsmagic/spell/disable/LevicorpusRules.java src/main/java/com/hogwartsmagic/spell/disable/TetherMath.java src/test/java/com/hogwartsmagic/spell/disable/StupefyRulesTest.java src/test/java/com/hogwartsmagic/spell/disable/PetrificusRulesTest.java src/test/java/com/hogwartsmagic/spell/disable/IncarcerousRulesTest.java src/test/java/com/hogwartsmagic/spell/disable/GlaciusRulesTest.java src/test/java/com/hogwartsmagic/spell/disable/LevicorpusRulesTest.java src/test/java/com/hogwartsmagic/spell/disable/TetherMathTest.java
git commit -m "Add the tested per-rank rules for the five disabling spells" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Shared plumbing (markers, support helpers, effect sync, terrain rule, NPC checks)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (after Phase 2's `SILENCED` registration)
- Create: `src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java`
- Create: `src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java`
- Create: `src/main/java/com/hogwartsmagic/spell/TerrainRules.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java` (`isCastingLockedOut`, new `interruptCasting`)
- Modify: `src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java` (`canDisapparate`, `wardedOnly`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `CrowdControl.scaleDuration`, `CombatEffects.responsible/isStunned/isCastingBlocked`, `CombatTags.ALLIED` (Phase 2); `SpellMasteryData.getRank(Player, SpellType)` (Phase 1); `ModEvents.interruptActiveCasting(ServerPlayer)`, `CaveInimicumManager.get(ServerLevel).isWardedAt(BlockPos)` (existing)
- Produces:
  - `ExampleMod.PETRIFIED`, `GLACIUS_CHILL`, `GLACIUS_FROZEN`: `RegistryObject<MobEffect>` (hidden markers; amplifier = caster rank − 1)
  - `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int`
  - `DisableSupport.holder(RegistryObject<MobEffect>)` → `Holder<MobEffect>`
  - `DisableSupport.interrupt(LivingEntity target)`
  - `DisableSupport.applyHiddenCrowdControl(LivingEntity target, RegistryObject<MobEffect> effect, int baseTicks, int amplifier, @Nullable Entity source)` → `int` ticks (0 = nothing landed)
  - `DisableSupport.addHiddenMarker(LivingEntity target, RegistryObject<MobEffect> effect, int ticks, int amplifier, @Nullable Entity source)`
  - `DisableSupport.isEnemyOf(@Nullable Entity caster, LivingEntity candidate, LivingEntity mainTarget)` → `boolean`
  - `DisableSupport.snapTo(LivingEntity target, double x, double y, double z)`
  - `VisibleEffects.isVisible(MobEffect)` → `boolean`
  - `TerrainRules.canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`
  - `VoldemortEntity.interruptCasting()`

- [ ] **Step 1: Register the markers.** In `ExampleMod.java`, directly after Phase 2's:
```java
    /** Marker: the cast handlers refuse while it is active and the spell wheel greys out. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> SILENCED = MOB_EFFECTS.register("silenced",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x6E6E8F) {});
```
add:
```java

    // Disabling-spell markers (Spell Mastery Phase 3). Always added hidden (no icon, no particles); the
    // amplifier stores the caster's mastery rank - 1, so the hooks know the rank without extra state.
    /** Petrificus Totalus: drives the topple render (synced by VisibleEffects) and the break-on-damage rule. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> PETRIFIED = MOB_EFFECTS.register("petrified",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xAAAAAA) {});
    /** Glacius: the window in which a second hit freezes the target solid. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GLACIUS_CHILL = MOB_EFFECTS.register("glacius_chill",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xBFEFFF) {});
    /** Glacius: frozen solid (alongside stunned and brittle). Fire thaws it; at Master a death shatters it. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GLACIUS_FROZEN = MOB_EFFECTS.register("glacius_frozen",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x88FFFF) {});
```

- [ ] **Step 2: Create `DisableSupport`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Helpers shared by the five disabling spells. Server side only (callers already are); common code
 * with no client imports.
 */
public final class DisableSupport {
    private DisableSupport() {
    }

    /** The caster's mastery rank for this spell; NPCs, projectiles without a player owner and null are rank 1. */
    public static int casterRank(@Nullable Entity caster, SpellType spell) {
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible instanceof Player player && !player.level().isClientSide()) {
            return SpellMasteryData.getRank(player, spell);
        }
        return 1;
    }

    public static Holder<MobEffect> holder(RegistryObject<MobEffect> effect) {
        return effect.getHolder().orElseThrow();
    }

    /**
     * Break whatever the target is casting: a player's channel, or Voldemort's queued windup. A stunned
     * Voldemort skips his AI step, so his own lockout check can't cancel the windup until the stun ends.
     */
    public static void interrupt(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            ModEvents.interruptActiveCasting(player);
        } else if (target instanceof VoldemortEntity voldemort) {
            voldemort.interruptCasting();
        }
    }

    /**
     * Hard crowd control carried by a hidden marker effect (Incarcerous chains, the Levicorpus hang).
     * Delegates to Phase 2's hidden-effect overload CrowdControl.apply(target, effect, baseTicks, amplifier,
     * source, visible=false), which probes immunity, applies diminishing returns, the boss rule and the PvP
     * rule, and records the diminishing-returns step only if the marker actually landed.
     *
     * @return the ticks applied; 0 if nothing landed
     */
    public static int applyHiddenCrowdControl(LivingEntity target, RegistryObject<MobEffect> effect, int baseTicks,
                                              int amplifier, @Nullable Entity source) {
        return CrowdControl.apply(target, holder(effect), baseTicks, Math.max(0, amplifier), source, false);
    }

    /** A hidden marker riding on crowd control that already landed (same ticks); not itself crowd control. */
    public static void addHiddenMarker(LivingEntity target, RegistryObject<MobEffect> effect, int ticks, int amplifier,
                                       @Nullable Entity source) {
        if (target.level().isClientSide() || ticks <= 0) {
            return;
        }
        target.addEffect(new MobEffectInstance(holder(effect), ticks, Math.max(0, amplifier), false, false, false),
                CombatEffects.responsible(source));
    }

    /**
     * Who a chain (Stupefy) or an area (Incarcerous) may also catch. Never the caster, the main target,
     * allied or owned mobs, team-mates, armour stands or spectators. Players only when the main target
     * is a player (so a PvE cast never stuns a nearby friend). Otherwise: hostile mobs (except Students,
     * which are illagers in code but usually friendly) and any mob currently targeting the caster.
     */
    public static boolean isEnemyOf(@Nullable Entity caster, LivingEntity candidate, LivingEntity mainTarget) {
        Entity owner = CombatEffects.responsible(caster);
        if (candidate == owner || candidate == mainTarget || !candidate.isAlive() || candidate.isSpectator()
                || candidate instanceof ArmorStand) {
            return false;
        }
        if (candidate.getTags().contains(CombatTags.ALLIED)) {
            return false;
        }
        if (owner != null) {
            if (candidate.isAlliedTo(owner)) {
                return false;
            }
            if (candidate instanceof OwnableEntity ownable) {
                UUID ownerId = ownable.getOwnerUUID();
                if (ownerId != null && ownerId.equals(owner.getUUID())) {
                    return false;
                }
            }
        }
        if (candidate instanceof Player) {
            return mainTarget instanceof Player;
        }
        if (candidate instanceof Mob mob && owner != null && mob.getTarget() == owner) {
            return true;
        }
        return candidate instanceof Enemy && !(candidate instanceof StudentEntity);
    }

    /** Put a target back on its leash or anchor. Players need a real teleport packet; mobs just move. */
    public static void snapTo(LivingEntity target, double x, double y, double z) {
        if (target instanceof ServerPlayer player) {
            player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        } else {
            target.teleportTo(x, y, z);
        }
    }
}
```

- [ ] **Step 3: Create `VisibleEffects`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla only sends an entity's effects to that entity itself (if it is a player) and to players
 * riding it, never to players merely watching it. The Levicorpus hang and the Petrificus topple are
 * drawn from the effect on the client, so these two markers are pushed to every watching client with
 * vanilla's own effect packets: on add, on removal, on expiry (the client never expires effects by
 * itself), and to a player who starts watching an entity that already has one. Common code: the
 * packets are vanilla network classes, present on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VisibleEffects {
    private VisibleEffects() {
    }

    public static boolean isVisible(MobEffect effect) {
        return effect == ExampleMod.LEVICORPUS_EFFECT.get() || effect == ExampleMod.PETRIFIED.get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance instance = event.getEffectInstance();
        if (entity.level() instanceof ServerLevel level && isVisible(instance.getEffect().value())) {
            level.getChunkSource().broadcast(entity, new ClientboundUpdateMobEffectPacket(entity.getId(), instance, false));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRemove(MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level() instanceof ServerLevel level && isVisible(event.getEffect())) {
            MobEffectInstance instance = event.getEffectInstance();
            if (instance != null) {
                broadcastRemoval(level, entity, instance.getEffect());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExpired(MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance instance = event.getEffectInstance();
        if (entity.level() instanceof ServerLevel level && instance != null && isVisible(instance.getEffect().value())) {
            broadcastRemoval(level, entity, instance.getEffect());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (isVisible(instance.getEffect().value())) {
                watcher.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), instance, false));
            }
        }
    }

    private static void broadcastRemoval(ServerLevel level, LivingEntity entity, Holder<MobEffect> effect) {
        level.getChunkSource().broadcast(entity, new ClientboundRemoveMobEffectPacket(entity.getId(), effect));
    }
}
```

- [ ] **Step 4: Create `TerrainRules`**
```java
package com.hogwartsmagic.spell;

import com.hogwartsmagic.caveinimicum.CaveInimicumManager;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;

import javax.annotation.Nullable;

/**
 * May a spell put a block here? One check for every spell that places blocks (Glacius ice today).
 * Refused inside a Cave Inimicum ward. A player caster also needs {@code mayInteract} (spawn protection,
 * world border) and an uncancelled {@link BlockEvent.EntityPlaceEvent}, which is the hook claim mods
 * guard placement with. An NPC caster, or no caster, needs the {@code mobGriefing} game rule.
 */
public final class TerrainRules {
    private TerrainRules() {
    }

    public static boolean canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (CaveInimicumManager.get(level).isWardedAt(pos)) {
            return false;
        }
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible instanceof Player player) {
            if (!level.mayInteract(player, pos)) {
                return false;
            }
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player);
            return !MinecraftForge.EVENT_BUS.post(event);
        }
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
}
```

- [ ] **Step 5: Voldemort answers to the new effects.** In `VoldemortEntity.java`, in `isCastingLockedOut()`, replace:
```java
    private boolean isCastingLockedOut() {
        if (this.hasEffect(ExampleMod.INCARCEROUS_CHAINS.getHolder().get())) {
            return true;
        }
```
with:
```java
    private boolean isCastingLockedOut() {
        // Stunned (Stupefy, Petrificus Totalus, a Glacius freeze) or silenced. Incarcerous only tethers
        // him now: a bound target can still fight back, so it no longer stops his casting.
        if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(this)) {
            return true;
        }
```
Then directly above:
```java
    private void queueSpell(SpellType spell, int warmupTicks) {
```
add:
```java
    /** Stupefy and other stuns call this on hit: a stunned boss skips his AI step, so the lockout check can't. */
    public void interruptCasting() {
        if (this.queuedSpell != null) {
            cancelQueuedSpell(true);
        }
    }

```

- [ ] **Step 6: A stunned or hanging house-elf stays pinned.** In `HouseElfEntity.java`, in `canDisapparate()`, replace:
```java
        if (this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            return false; // Petrificus Totalus, Incarcerous, Glacius, Stupefy, Arresto Momentum
        }
```
with:
```java
        if (this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            return false; // Glacius, Arresto Momentum
        }
        if (com.hogwartsmagic.spell.combat.CombatEffects.isStunned(this)
                || this.hasEffect(ExampleMod.LEVICORPUS_EFFECT.getHolder().get())) {
            return false; // Stupefy, Petrificus Totalus, a Glacius freeze, Levicorpus
        }
```
In `wardedOnly()`, replace:
```java
        if (this.subduedTicks > 0
                || this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || this.hasEffect(ExampleMod.INCARCEROUS_CHAINS.getHolder().get())) {
```
with:
```java
        if (this.subduedTicks > 0
                || this.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || this.hasEffect(ExampleMod.INCARCEROUS_CHAINS.getHolder().get())
                || com.hogwartsmagic.spell.combat.CombatEffects.isStunned(this)
                || this.hasEffect(ExampleMod.LEVICORPUS_EFFECT.getHolder().get())) {
```

- [ ] **Step 7: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.wand_radial.silenced": "Silenced: no spells",` insert:
```json
  "effect.hogwartsmagic.petrified": "Petrificus Totalus",
  "effect.hogwartsmagic.glacius_chill": "Chilled",
  "effect.hogwartsmagic.glacius_frozen": "Frozen Solid",
```

- [ ] **Step 8: Build, test and boot a dedicated server** (new registry entries and a new event subscriber)

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Then run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `VisibleEffects`, `DisableSupport`, `TerrainRules`, `petrified`, `glacius` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java src/main/java/com/hogwartsmagic/spell/TerrainRules.java src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add the shared plumbing for the disabling spells" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Stupefy (true stun, interrupt, Master chain)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/disable/StupefySpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case STUPEFY`)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.handle`, beam speed)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.stupefy.desc`)

**Interfaces:**
- Consumes: `StupefyRules` (Task 1); `DisableSupport.casterRank/holder/interrupt/isEnemyOf` (Task 2); `CrowdControl.apply`, `ExampleMod.STUNNED` (Phase 2)
- Produces: `StupefySpell.apply(LivingEntity target, @Nullable Entity caster)`

- [ ] **Step 1: Create `StupefySpell`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Stupefy: the fastest true stun in the mod and the interrupt button. Novice: 1.5s stun and the
 * target's channel or windup is broken (always, even when diminishing returns eat the stun). Adept and
 * Expert: longer, plus a small knockback. Master: the stun jumps to one more enemy within 5 blocks for
 * half the duration (and the beam is faster, see SpellPackets).
 */
public final class StupefySpell {
    private StupefySpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.STUPEFY);
        DisableSupport.interrupt(target);
        CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED), StupefyRules.stunTicks(rank), caster);
        double knockback = StupefyRules.knockback(rank);
        if (knockback > 0.0 && caster != null) {
            target.knockback(knockback, caster.getX() - target.getX(), caster.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        if (StupefyRules.chains(rank)) {
            chain(level, target, caster, rank);
        }
    }

    private static void chain(ServerLevel level, LivingEntity from, @Nullable Entity caster, int rank) {
        double radius = StupefyRules.CHAIN_RADIUS;
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(radius),
                e -> e.distanceToSqr(from) <= radius * radius && DisableSupport.isEnemyOf(caster, e, from));
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double d = candidate.distanceToSqr(from);
            if (d < best) {
                best = d;
                next = candidate;
            }
        }
        if (next == null) {
            return;
        }
        DisableSupport.interrupt(next);
        CrowdControl.apply(next, DisableSupport.holder(ExampleMod.STUNNED), StupefyRules.chainStunTicks(rank), caster);
        drawArc(level, from, next);
        level.playSound(null, next.getX(), next.getY(), next.getZ(), ModSounds.SPELL_STUPEFY.get(), SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    /** A red spark line between the two targets, so the chain reads as one spell. */
    private static void drawArc(ServerLevel level, LivingEntity a, LivingEntity b) {
        Vec3 from = a.position().add(0.0, a.getBbHeight() * 0.6, 0.0);
        Vec3 to = b.position().add(0.0, b.getBbHeight() * 0.6, 0.0);
        int steps = Math.max(6, (int) (from.distanceTo(to) * 4));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
```

- [ ] **Step 2: Delegate from `SpellType`.** Replace:
```java
            case STUPEFY -> {
                interruptActiveCasting(target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                // For "stun", we'd ideally prevent movement/attack, but slowness 4 is very close.
            }
```
with:
```java
            case STUPEFY -> com.hogwartsmagic.spell.disable.StupefySpell.apply(target, caster);
```

- [ ] **Step 3: Master beam speed.** In `SpellPackets.CastSpellPacket.handle`, replace:
```java
            if (spell == SpellType.AQUA_ERUCTO) {
                beamSpeed = 5.75f * HousePerks.getBeamSpeedMultiplier(player) * MagicWandItem.getBeamSpeedMultiplier(wand);
            }
```
with:
```java
            if (spell == SpellType.AQUA_ERUCTO) {
                beamSpeed = 5.75f * HousePerks.getBeamSpeedMultiplier(player) * MagicWandItem.getBeamSpeedMultiplier(wand);
            }
            if (spell == SpellType.STUPEFY) {
                beamSpeed *= com.hogwartsmagic.spell.disable.StupefyRules.beamSpeedMultiplier(
                        com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell));
            }
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.stupefy.desc": "Stuns mobs, making them blind and extremely slow.",
```
with:
```json
  "spell.hogwartsmagic.stupefy.desc": "A short, true stun that breaks any spell the target is casting. At Master it jumps to a second enemy.",
```

- [ ] **Step 5: Check the old effect is gone**

Run: `grep -n "MOVEMENT_SLOWDOWN, 60, 4" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.

- [ ] **Step 6: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/disable/StupefySpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Stupefy a true stun and interrupt with a Master chain" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Petrificus Totalus (topple, lock that breaks on damage, Master shatter)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/disable/PetrificusSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case PETRIFICUS_TOTALUS`)
- Modify: `src/main/java/com/hogwartsmagic/event/ClientForgeEvents.java` (`onRenderLivingPre`, `onRenderLivingPost`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.petrificus_totalus.desc`)

**Interfaces:**
- Consumes: `PetrificusRules` (Task 1); `DisableSupport.casterRank/holder/interrupt/addHiddenMarker`, `ExampleMod.PETRIFIED`, `VisibleEffects` (Task 2); `CrowdControl.apply`, `ExampleMod.STUNNED` (Phase 2)
- Produces: `PetrificusSpell.apply(LivingEntity target, @Nullable Entity caster)`

- [ ] **Step 1: Create `PetrificusSpell`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Petrificus Totalus: the target goes rigid and topples over (a stun plus the petrified marker, which
 * watching clients render rotated 90 degrees). Any damage ends it at Novice; from Adept, hits under 4
 * damage don't. At Master the hit that breaks it deals +50%. Gravity still applies, so a petrified mob
 * in the air falls and takes normal fall damage.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetrificusSpell {
    private PetrificusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.PETRIFICUS_TOTALUS);
        int ticks = CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED),
                PetrificusRules.durationTicks(rank), caster);
        if (ticks <= 0) {
            return;
        }
        DisableSupport.interrupt(target);
        DisableSupport.addHiddenMarker(target, ExampleMod.PETRIFIED, ticks, rank - 1, caster);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.STONE_PLACE, SoundSource.NEUTRAL, 1.0f, 0.6f);
    }

    /** LOW so it sees the amount after other multipliers (brittle); before armour, like brittle. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Holder<MobEffect> petrified = DisableSupport.holder(ExampleMod.PETRIFIED);
        MobEffectInstance instance = target.getEffect(petrified);
        if (instance == null) {
            return;
        }
        int rank = instance.getAmplifier() + 1;
        if (!PetrificusRules.breaks(rank, event.getAmount())) {
            return;
        }
        event.setAmount(event.getAmount() * PetrificusRules.shatterMultiplier(rank));
        target.removeEffect(petrified);
        target.removeEffect(DisableSupport.holder(ExampleMod.STUNNED));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 24,
                target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 1.0f, 0.8f);
    }
}
```

- [ ] **Step 2: Delegate from `SpellType`.** Replace:
```java
            case PETRIFICUS_TOTALUS -> {
                interruptActiveCasting(target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 10)); // Total immobility
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 250)); // Prevent jumping (level 250 makes jump height 0)
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 10)); // Prevent attacking impactfully
            }
```
with:
```java
            case PETRIFICUS_TOTALUS -> com.hogwartsmagic.spell.disable.PetrificusSpell.apply(target, caster);
```

- [ ] **Step 3: The topple render.** In `ClientForgeEvents.java`, replace the whole `onRenderLivingPre` and `onRenderLivingPost` methods:
```java
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        boolean levicorpus = hasLevicorpus(entity);
        boolean petrified = entity.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get());

        if (levicorpus) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0D, entity.getBbHeight() + 0.1D, 0.0D);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(180.0F));
        }

        if (petrified) {
            RenderSystem.setShaderColor(0.42F, 0.43F, 0.45F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity().hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (hasLevicorpus(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }
```
with:
```java
    /** When each visible Petrificus topple started on this client, so the fall plays over a few ticks. */
    private static final java.util.WeakHashMap<LivingEntity, Long> TOPPLE_START = new java.util.WeakHashMap<>();
    private static final float TOPPLE_TICKS = 5.0F;

    /** Petrificus Totalus (synced by VisibleEffects). Levicorpus wins if both are on: it has its own pose. */
    private static boolean isToppled(LivingEntity entity) {
        return !hasLevicorpus(entity) && entity.hasEffect(ExampleMod.PETRIFIED.getHolder().get());
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        boolean levicorpus = hasLevicorpus(entity);
        boolean petrified = entity.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get());

        if (levicorpus) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0.0D, entity.getBbHeight() + 0.1D, 0.0D);
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(180.0F));
        }

        if (isToppled(entity)) {
            long now = entity.level().getGameTime();
            long start = TOPPLE_START.computeIfAbsent(entity, e -> now);
            float progress = Math.min(1.0F, (now - start + event.getPartialTick()) / TOPPLE_TICKS);
            float yaw = net.minecraft.util.Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot)
                    * net.minecraft.util.Mth.DEG_TO_RAD;
            com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
            pose.pushPose();
            // Lift by half the body depth so the back doesn't sink into the floor, then tip backwards
            // about the body's own left-right axis (cos yaw, 0, sin yaw). 90 degrees lies it flat.
            pose.translate(0.0D, entity.getBbWidth() * 0.5D * progress, 0.0D);
            pose.mulPose(new org.joml.Quaternionf().rotateAxis(-net.minecraft.util.Mth.HALF_PI * progress,
                    net.minecraft.util.Mth.cos(yaw), 0.0F, net.minecraft.util.Mth.sin(yaw)));
        } else {
            TOPPLE_START.remove(entity);
        }

        if (petrified) {
            RenderSystem.setShaderColor(0.42F, 0.43F, 0.45F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity().hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (isToppled(event.getEntity())) {
            event.getPoseStack().popPose();
        }
        if (hasLevicorpus(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }
```
(Pre pushes levicorpus then topple; Post pops topple then levicorpus. `isToppled` excludes levicorpus, so at most one of the two is pushed and the order can never mismatch.)

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.petrificus_totalus.desc": "Performs a full body-bind, paralyzing the target completely.",
```
with:
```json
  "spell.hogwartsmagic.petrificus_totalus.desc": "A full body-bind: the target topples over, rigid, until the lock wears off or damage breaks it. At Master the breaking hit deals half again as much.",
```

- [ ] **Step 5: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/disable/PetrificusSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/event/ClientForgeEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Petrificus Totalus topple the target and break on damage" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Incarcerous (tether, area bind, flyer pull, Master binding)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/disable/IncarcerousTether.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/IncarcerousChainAnimation.java` (new `renderLink`)
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case INCARCEROUS`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.incarcerous.desc`)

**Interfaces:**
- Consumes: `IncarcerousRules`, `TetherMath` (Task 1); `DisableSupport.casterRank/holder/applyHiddenCrowdControl/isEnemyOf/snapTo` (Task 2); `IncarcerousChainAnimation.schedule(ServerLevel, LivingEntity)` (existing; its sustained render stays in `ModEvents.onLivingUpdate`)
- Produces: `IncarcerousTether.apply(LivingEntity target, @Nullable Entity caster)`, `IncarcerousTether.TAG = "hogwartsmagic:tether"`, `IncarcerousChainAnimation.renderLink(ServerLevel, LivingEntity, LivingEntity)`

- [ ] **Step 1: A chain between two bound bodies.** In `IncarcerousChainAnimation.java`, directly above:
```java
    private static int sustainedDensity(Vec3 a, Vec3 b) {
```
add:
```java
    /** Master Incarcerous: a light chain from one bound target to another, re-drawn while both stay bound. */
    public static void renderLink(ServerLevel level, LivingEntity a, LivingEntity b) {
        if (!a.isAlive() || !b.isAlive()) {
            return;
        }
        Vec3 from = a.position().add(0.0, a.getBbHeight() * 0.5, 0.0);
        Vec3 to = b.position().add(0.0, b.getBbHeight() * 0.5, 0.0);
        int segments = Mth.clamp((int) (from.distanceTo(to) * 10), 8, 30);
        chainBetween(level, from, to, segments, 0.12, true);
    }

```

- [ ] **Step 2: Create `IncarcerousTether`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.IncarcerousChainAnimation;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Incarcerous: chains from the ground tether the target to where it was bound. It can still attack and
 * cast, but can't get more than the leash (2 blocks, measured flat) from that spot. Adept and Expert
 * also catch nearby enemies; Expert pulls tethered flyers down and keeps them from taking off; Master
 * drags every bound target to the main one on a 1-block leash and makes them share knockback.
 *
 * <p>The bind itself is the hidden incarcerous_chains effect (hard crowd control, applied through
 * DisableSupport.applyHiddenCrowdControl). The leash lives in the entity's persistent NBT under
 * {@link #TAG} and is enforced every server tick while the effect lasts; it is dropped as soon as the
 * effect is gone. Players are corrected with velocity packets and, if far out, a teleport.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IncarcerousTether {
    public static final String TAG = "hogwartsmagic:tether";
    private static boolean sharingKnockback;

    private IncarcerousTether() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.INCARCEROUS);
        boolean pull = IncarcerousRules.pullsFlyersDown(rank);
        boolean bind = IncarcerousRules.bindsTogether(rank);
        double leash = IncarcerousRules.leash(rank);

        int mainTicks = DisableSupport.applyHiddenCrowdControl(target, ExampleMod.INCARCEROUS_CHAINS,
                IncarcerousRules.mainTicks(rank), 0, caster);
        if (mainTicks > 0) {
            tether(level, target, leash, pull, bind ? target.getUUID() : null, null);
            IncarcerousChainAnimation.schedule(level, target);
        }

        double radius = IncarcerousRules.secondaryRadius(rank);
        if (radius <= 0.0) {
            return;
        }
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(radius),
                e -> DisableSupport.isEnemyOf(caster, e, target));
        for (LivingEntity other : nearby) {
            int ticks = DisableSupport.applyHiddenCrowdControl(other, ExampleMod.INCARCEROUS_CHAINS,
                    IncarcerousRules.secondaryTicks(rank), 0, caster);
            if (ticks <= 0) {
                continue;
            }
            if (bind && mainTicks > 0) {
                double boundLeash = IncarcerousRules.boundLeash(rank, target.getBbWidth(), other.getBbWidth());
                tether(level, other, boundLeash, pull, target.getUUID(), target.getUUID());
            } else {
                tether(level, other, leash, pull, null, null);
            }
            IncarcerousChainAnimation.schedule(level, other);
        }
    }

    private static void tether(ServerLevel level, LivingEntity target, double leash, boolean pull,
                               @Nullable UUID group, @Nullable UUID anchorEntity) {
        if (target.isPassenger()) {
            target.stopRiding(); // a mount or broom would carry the bound target away
        }
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", target.getX());
        tag.putDouble("Y", pull ? groundY(level, target) : target.getY());
        tag.putDouble("Z", target.getZ());
        tag.putDouble("Leash", leash);
        tag.putBoolean("Pull", pull);
        if (group != null) {
            tag.putUUID("Group", group);
        }
        if (anchorEntity != null) {
            tag.putUUID("Anchor", anchorEntity);
        }
        target.getPersistentData().put(TAG, tag);
    }

    /** Top of the first solid block under the target (up to 32 down), so a flyer is anchored to the ground. */
    private static double groundY(ServerLevel level, LivingEntity target) {
        BlockPos.MutableBlockPos cursor = target.blockPosition().mutable();
        for (int i = 0; i < 32 && cursor.getY() > level.getMinBuildHeight(); i++) {
            BlockPos below = cursor.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return below.getY() + 1.0;
            }
            cursor.move(Direction.DOWN);
        }
        return target.getY();
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        if (!data.contains(TAG)) {
            return;
        }
        Holder<MobEffect> chains = DisableSupport.holder(ExampleMod.INCARCEROUS_CHAINS);
        if (!target.isAlive() || !target.hasEffect(chains)) {
            data.remove(TAG);
            return;
        }
        CompoundTag tag = data.getCompound(TAG);
        double ax = tag.getDouble("X");
        double ay = tag.getDouble("Y");
        double az = tag.getDouble("Z");
        double leash = tag.getDouble("Leash");
        boolean pull = tag.getBoolean("Pull");
        if (tag.hasUUID("Anchor")) {
            Entity anchor = level.getEntity(tag.getUUID("Anchor"));
            if (anchor instanceof LivingEntity bound && bound.isAlive() && bound.hasEffect(chains)) {
                ax = bound.getX();
                ay = bound.getY();
                az = bound.getZ();
                if (target.tickCount % 2 == 0) {
                    IncarcerousChainAnimation.renderLink(level, target, bound);
                }
            }
        }

        if (target.isPassenger()) {
            target.stopRiding();
        }
        Vec3 v = target.getDeltaMovement();
        boolean changed = false;
        double dx = target.getX() - ax;
        double dz = target.getZ() - az;
        if (TetherMath.shouldSnap(dx, 0.0, dz, leash)) {
            double[] p = TetherMath.snapPoint(ax, target.getY(), az, dx, 0.0, dz, leash);
            DisableSupport.snapTo(target, p[0], p[1], p[2]);
            v = new Vec3(0.0, v.y, 0.0);
            changed = true;
        } else if (TetherMath.isOutside(dx, 0.0, dz, leash)) {
            double[] c = TetherMath.correctedVelocity(dx, 0.0, dz, leash, v.x, v.y, v.z);
            v = new Vec3(c[0], c[1], c[2]);
            changed = true;
        }

        if (pull) {
            if (target instanceof Player player && player.isFallFlying()) {
                player.stopFallFlying();
            }
            double above = target.getY() - ay;
            double slack = IncarcerousRules.PULL_DOWN_VERTICAL_SLACK;
            if (above > slack + TetherMath.SNAP_SLACK) {
                DisableSupport.snapTo(target, target.getX(), ay + slack, target.getZ());
                v = new Vec3(v.x, 0.0, v.z);
                changed = true;
            } else if (above > slack && v.y > -IncarcerousRules.PULL_DOWN_SPEED) {
                v = new Vec3(v.x, -IncarcerousRules.PULL_DOWN_SPEED, v.z);
                changed = true;
            }
        }

        if (changed) {
            target.setDeltaMovement(v);
            if (target instanceof ServerPlayer) {
                target.hurtMarked = true;
            }
        }
    }

    /** Master: knockback on one member of a bound group is passed to the others (they're chained together). */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        if (sharingKnockback || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        UUID group = groupOf(target);
        if (group == null) {
            return;
        }
        Holder<MobEffect> chains = DisableSupport.holder(ExampleMod.INCARCEROUS_CHAINS);
        if (!target.hasEffect(chains)) {
            return;
        }
        List<LivingEntity> members = level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(IncarcerousRules.GROUP_SEARCH_RADIUS),
                e -> e != target && e.hasEffect(chains) && group.equals(groupOf(e)));
        sharingKnockback = true;
        try {
            for (LivingEntity member : members) {
                member.knockback(event.getStrength(), event.getRatioX(), event.getRatioZ());
                if (member instanceof ServerPlayer) {
                    member.hurtMarked = true;
                }
            }
        } finally {
            sharingKnockback = false;
        }
    }

    @Nullable
    private static UUID groupOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG)) {
            return null;
        }
        CompoundTag tag = data.getCompound(TAG);
        return tag.hasUUID("Group") ? tag.getUUID("Group") : null;
    }
}
```

- [ ] **Step 3: Delegate from `SpellType`.** Replace:
```java
            case INCARCEROUS -> {
                final int mainBindTicks = 100;
                final int nearbyBindTicks = 70;
                var chains = ExampleMod.INCARCEROUS_CHAINS.getHolder().get();
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, mainBindTicks, 10)); // Trap
                target.addEffect(new MobEffectInstance(chains, mainBindTicks, 0, false, false, false));
                if (target.level() instanceof ServerLevel server) {
                    net.minecraft.world.phys.AABB area = target.getBoundingBox().inflate(2.6);
                    java.util.List<LivingEntity> trapped = server.getEntitiesOfClass(LivingEntity.class, area, e -> e != target);
                    for (LivingEntity nearby : trapped) {
                        nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, nearbyBindTicks, 4));
                        nearby.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, nearbyBindTicks, 1));
                        nearby.addEffect(new MobEffectInstance(chains, nearbyBindTicks, 0, false, false, false));
                        IncarcerousChainAnimation.schedule(server, nearby);
                    }

                    IncarcerousChainAnimation.schedule(server, target);
                }
            }
```
with:
```java
            case INCARCEROUS -> com.hogwartsmagic.spell.disable.IncarcerousTether.apply(target, caster);
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.incarcerous.desc": "Binds the target with chains from the ground: ankles, then legs together, then wrists.",
```
with:
```json
  "spell.hogwartsmagic.incarcerous.desc": "Chains from the ground tether the target to the spot. It can still fight, but can't get away. Higher ranks catch nearby enemies, pull flyers down and, at Master, chain everyone together.",
```

- [ ] **Step 5: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/disable/IncarcerousTether.java src/main/java/com/hogwartsmagic/spell/IncarcerousChainAnimation.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Turn Incarcerous into a tether that pulls flyers down and binds targets together" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Glacius (chill, freeze solid with brittle, frosted ice, fire thaw, Master shatter)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/disable/GlaciusSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case GLACIUS`, delete `isColdEnvironment`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (`onHit`, the Glacius block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.glacius.desc`)

**Interfaces:**
- Consumes: `GlaciusRules` (Task 1); `DisableSupport.casterRank/holder/interrupt/addHiddenMarker`, `ExampleMod.GLACIUS_CHILL/GLACIUS_FROZEN`, `TerrainRules.canPlace` (Task 2); `CrowdControl.apply`, `CombatEffects.applyBrittle/responsible`, `ExampleMod.STUNNED/BRITTLE` (Phase 2)
- Produces: `GlaciusSpell.apply(LivingEntity target, @Nullable Entity caster)`, `GlaciusSpell.FROZEN_BY_TAG = "hogwartsmagic:glacius_frozen_by"`

- [ ] **Step 1: Create `GlaciusSpell`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Glacius sets up combos. The first hit slows (Slowness II) and chills: a hidden glacius_chill marker
 * that opens a short window. A hit inside that window freezes the target solid: a stun through
 * CrowdControl, brittle (the payoff for Confringo and melee) and the glacius_frozen marker. Surface
 * water under the target turns to frosted ice. Fire thaws a frozen target at once. At Master a frozen
 * target that dies shatters and slows everything nearby except the caster.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GlaciusSpell {
    public static final String FROZEN_BY_TAG = "hogwartsmagic:glacius_frozen_by";

    private GlaciusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.GLACIUS);
        Entity responsible = CombatEffects.responsible(caster);
        frostWaterUnderfoot(level, target, caster);

        Holder<MobEffect> chill = DisableSupport.holder(ExampleMod.GLACIUS_CHILL);
        Holder<MobEffect> frozen = DisableSupport.holder(ExampleMod.GLACIUS_FROZEN);
        boolean primed = target.hasEffect(chill) || target.hasEffect(frozen);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                GlaciusRules.slowTicks(rank, isCold(target)), GlaciusRules.SLOW_AMPLIFIER), responsible);
        if (!primed) {
            DisableSupport.addHiddenMarker(target, ExampleMod.GLACIUS_CHILL, GlaciusRules.chillWindowTicks(rank), rank - 1, caster);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), GlaciusRules.CHILL_FROST_TICKS));
            return;
        }
        target.removeEffect(chill);
        freezeSolid(level, target, caster, rank);
    }

    private static void freezeSolid(ServerLevel level, LivingEntity target, @Nullable Entity caster, int rank) {
        int ticks = CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED), GlaciusRules.freezeTicks(rank), caster);
        if (ticks <= 0) {
            return;
        }
        DisableSupport.interrupt(target);
        DisableSupport.addHiddenMarker(target, ExampleMod.GLACIUS_FROZEN, ticks, rank - 1, caster);
        CombatEffects.applyBrittle(target, GlaciusRules.brittleTicks(ticks), GlaciusRules.brittleAmplifier(rank), caster);
        // Fully frozen for the whole freeze (ticksFrozen decays by 2 a tick): vanilla shakes the body
        // and draws the frost overlay for a player.
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 2 * ticks));
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible != null) {
            target.getPersistentData().putUUID(FROZEN_BY_TAG, responsible.getUUID());
        } else {
            target.getPersistentData().remove(FROZEN_BY_TAG);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                30, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                16, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_PLACE, SoundSource.NEUTRAL, 1.0f, 1.6f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }

    /**
     * Surface water under (or, if it is swimming, around) the target turns to frosted ice, which melts on
     * its own. Blocks the target's own body is in are skipped, so nobody is sealed inside the ice.
     */
    private static void frostWaterUnderfoot(ServerLevel level, LivingEntity target, @Nullable Entity caster) {
        BlockPos feet = target.blockPosition();
        BlockPos center;
        if (level.getFluidState(feet).isEmpty()) {
            center = feet.below();
        } else {
            center = feet;
            for (int i = 0; i < 2 && !level.getFluidState(center.above()).isEmpty(); i++) {
                center = center.above();
            }
        }
        AABB body = target.getBoundingBox();
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()
                        || !level.getBlockState(pos.above()).isAir() || new AABB(pos).intersects(body)) {
                    continue;
                }
                if (!TerrainRules.canPlace(level, pos, caster)) {
                    continue;
                }
                level.setBlockAndUpdate(pos, ice);
                level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 60, 120));
            }
        }
    }

    private static boolean isCold(LivingEntity target) {
        return target.level().getBiome(target.blockPosition()).value().getBaseTemperature() < 0.15f;
    }

    /** Fire thaws a frozen target at once; the frozen-by tag is cleared once the freeze is over. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Holder<MobEffect> frozen = DisableSupport.holder(ExampleMod.GLACIUS_FROZEN);
        CompoundTag data = target.getPersistentData();
        if (!target.hasEffect(frozen)) {
            if (data.contains(FROZEN_BY_TAG)) {
                data.remove(FROZEN_BY_TAG);
            }
            return;
        }
        if (!target.isOnFire()) {
            return;
        }
        target.removeEffect(frozen);
        target.removeEffect(DisableSupport.holder(ExampleMod.STUNNED));
        target.removeEffect(DisableSupport.holder(ExampleMod.BRITTLE));
        target.setTicksFrozen(0);
        data.remove(FROZEN_BY_TAG);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1.0, target.getZ(), 16, 0.35, 0.3, 0.35, 0.01);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    /** Master: a target that dies while frozen solid shatters into shards that slow everything nearby. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        MobEffectInstance frozen = dead.getEffect(DisableSupport.holder(ExampleMod.GLACIUS_FROZEN));
        if (frozen == null || !GlaciusRules.shatters(frozen.getAmplifier() + 1)) {
            return;
        }
        CompoundTag data = dead.getPersistentData();
        UUID frozenBy = data.hasUUID(FROZEN_BY_TAG) ? data.getUUID(FROZEN_BY_TAG) : null;
        double radius = GlaciusRules.SHATTER_RADIUS;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(radius),
                e -> e != dead && e.isAlive() && e.distanceToSqr(dead) <= radius * radius
                        && (frozenBy == null || !frozenBy.equals(e.getUUID())))) {
            nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    GlaciusRules.SHATTER_SLOW_TICKS, GlaciusRules.SHATTER_SLOW_AMPLIFIER));
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(), 60,
                dead.getBbWidth() * 0.6, dead.getBbHeight() * 0.4, dead.getBbWidth() * 0.6, 0.25);
        level.sendParticles(ParticleTypes.SNOWFLAKE, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(),
                40, radius * 0.4, 0.4, radius * 0.4, 0.05);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2f, 0.7f);
        data.remove(FROZEN_BY_TAG);
    }
}
```

- [ ] **Step 2: Delegate from `SpellType` and keep the steam combo.** Replace:
```java
            case GLACIUS -> {
                int freezeDuration = 200;
                int slowDuration = 100;
                if (isColdEnvironment(target)) {
                    freezeDuration += 80;
                    slowDuration += 40;
                }
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowDuration, 10)); // Freeze in place
                target.setTicksFrozen(freezeDuration);
                if (target.isOnFire()) {
                    triggerSteamCombo(target);
                    target.clearFire();
                }
            }
```
with:
```java
            case GLACIUS -> {
                // Steam combo first: put out the fire, or it would thaw the freeze on the next tick.
                if (target.isOnFire()) {
                    triggerSteamCombo(target);
                    target.clearFire();
                }
                com.hogwartsmagic.spell.disable.GlaciusSpell.apply(target, caster);
            }
```
Then delete the now-unused method:
```java
    private boolean isColdEnvironment(LivingEntity target) {
        return target.level().getBiome(target.blockPosition()).value().getBaseTemperature() < 0.15f;
    }

```

- [ ] **Step 3: Glacius block hits respect the terrain rule.** In `SpellBeamEntity.onHit`, replace:
```java
                } else if (spell == SpellType.GLACIUS) {
                    if (state.is(Blocks.WATER)) {
                        this.level().setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                        hitSuccessful = true;
                    } else if (this.level().isEmptyBlock(pos.relative(blockHit.getDirection()))) {
                        BlockPos icePos = pos.relative(blockHit.getDirection());
                        this.level().setBlockAndUpdate(icePos, Blocks.ICE.defaultBlockState());
                        hitSuccessful = true;
                    }
```
with:
```java
                } else if (spell == SpellType.GLACIUS) {
                    // Wards, spawn protection, claims and (for NPC casters) mobGriefing: see TerrainRules.
                    if (this.level() instanceof ServerLevel glaciusLevel) {
                        if (state.is(Blocks.WATER)) {
                            if (com.hogwartsmagic.spell.TerrainRules.canPlace(glaciusLevel, pos, getOwner())) {
                                this.level().setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                                hitSuccessful = true;
                            }
                        } else if (this.level().isEmptyBlock(pos.relative(blockHit.getDirection()))) {
                            BlockPos icePos = pos.relative(blockHit.getDirection());
                            if (com.hogwartsmagic.spell.TerrainRules.canPlace(glaciusLevel, icePos, getOwner())) {
                                this.level().setBlockAndUpdate(icePos, Blocks.ICE.defaultBlockState());
                                hitSuccessful = true;
                            }
                        }
                    }
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.glacius.desc": "Freezes mobs in place and turns water into ice.",
```
with:
```json
  "spell.hogwartsmagic.glacius.desc": "Chills and slows the target; a second hit soon after freezes it solid and leaves it brittle. Fire thaws it. Turns water into ice. At Master, frozen enemies shatter when they die.",
```

- [ ] **Step 5: Check the old freeze is gone**

Run: `grep -n "isColdEnvironment\|MOVEMENT_SLOWDOWN, slowDuration, 10" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.

- [ ] **Step 6: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/disable/GlaciusSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Give Glacius a freeze build-up, brittle freeze, fire thaw and Master shatter" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Levicorpus (hang in place, no items or shields, damage bonus, Liberacorpus)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/disable/LevicorpusSpell.java`
- Create: `src/main/java/com/hogwartsmagic/client/HangInputLock.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case LEVICORPUS`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.levicorpus.desc`)

**Interfaces:**
- Consumes: `LevicorpusRules` (Task 1); `DisableSupport.casterRank/holder/applyHiddenCrowdControl/snapTo`, `VisibleEffects` (Task 2); `ExampleMod.LEVICORPUS_EFFECT` (existing); the upside-down render in `ClientForgeEvents` (existing, unchanged)
- Produces: `LevicorpusSpell.apply(LivingEntity target, @Nullable Entity caster)`, `LevicorpusSpell.isHanging(LivingEntity)` (both sides), `LevicorpusSpell.release(LivingEntity)`, `LevicorpusSpell.ANCHOR_TAG = "hogwartsmagic:levicorpus_anchor"`

- [ ] **Step 1: Create `LevicorpusSpell`**
```java
package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Levicorpus: the target is hoisted 1.5 blocks up by the ankle and hangs there, hovering in place
 * (not drifting upward like the old Levitation). While hanging it can't use items or raise a shield;
 * wands are exempt, so a hanging duellist can still cast. From Adept it takes +25% damage. At Master,
 * casting Levicorpus again on a hanging target drops it hard (Liberacorpus): 6 blocks of fall damage.
 *
 * <p>The hang is the hidden levicorpus effect (hard crowd control via applyHiddenCrowdControl; its
 * amplifier is the caster's rank - 1). The hover anchor and the target's original no-gravity flag sit
 * in persistent NBT under {@link #ANCHOR_TAG}; gravity is restored the moment the effect goes. The
 * upside-down pose is drawn by ClientForgeEvents from the effect, which VisibleEffects syncs.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LevicorpusSpell {
    public static final String ANCHOR_TAG = "hogwartsmagic:levicorpus_anchor";

    private LevicorpusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.LEVICORPUS);
        if (isHanging(target) && LevicorpusRules.liberacorpus(rank)) {
            liberacorpus(level, target);
            return;
        }
        int ticks = DisableSupport.applyHiddenCrowdControl(target, ExampleMod.LEVICORPUS_EFFECT,
                LevicorpusRules.hangTicks(rank), rank - 1, caster);
        if (ticks <= 0) {
            return;
        }
        if (target.isPassenger()) {
            target.stopRiding();
        }
        if (target.isUsingItem()) {
            target.stopUsingItem();
        }
        CompoundTag data = target.getPersistentData();
        if (!data.contains(ANCHOR_TAG)) { // a refresh keeps the first anchor and the original gravity flag
            CompoundTag tag = new CompoundTag();
            tag.putDouble("X", target.getX());
            tag.putDouble("Y", target.getY() + hangHeight(level, target));
            tag.putDouble("Z", target.getZ());
            tag.putBoolean("HadNoGravity", target.isNoGravity());
            data.put(ANCHOR_TAG, tag);
        }
        target.setNoGravity(true);
    }

    /** Works on both sides: the server has the effect, the local player gets its own, watchers get it from VisibleEffects. */
    public static boolean isHanging(LivingEntity entity) {
        return ExampleMod.LEVICORPUS_EFFECT.getHolder().map(entity::hasEffect).orElse(false);
    }

    /** Give gravity back and forget the anchor. Safe to call any time. */
    public static void release(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ANCHOR_TAG)) {
            return;
        }
        boolean hadNoGravity = data.getCompound(ANCHOR_TAG).getBoolean("HadNoGravity");
        data.remove(ANCHOR_TAG);
        entity.setNoGravity(hadNoGravity);
        entity.fallDistance = 0.0F;
    }

    /** Up to 1.5 blocks, less under a low ceiling (0.25-block steps). */
    private static double hangHeight(ServerLevel level, LivingEntity target) {
        double height = LevicorpusRules.HANG_HEIGHT;
        while (height > 0.0 && !level.noCollision(target, target.getBoundingBox().move(0.0, height, 0.0))) {
            height -= 0.25;
        }
        return Math.max(0.0, height);
    }

    private static void liberacorpus(ServerLevel level, LivingEntity target) {
        Holder<MobEffect> hang = DisableSupport.holder(ExampleMod.LEVICORPUS_EFFECT);
        target.removeEffect(hang);
        if (target.hasEffect(hang)) {
            return; // another mod refused the removal
        }
        release(target);
        target.setDeltaMovement(0.0, LevicorpusRules.LIBERACORPUS_DROP_SPEED, 0.0);
        target.hurtMarked = true;
        target.hurt(target.damageSources().fall(), LevicorpusRules.LIBERACORPUS_FALL_DAMAGE);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.2, target.getZ(), 20, 0.5, 0.1, 0.5, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.NEUTRAL, 1.0f, 0.9f);
    }

    /** Hold the hanging target at its anchor; let go once the effect has ended. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        if (!data.contains(ANCHOR_TAG)) {
            return;
        }
        if (!target.isAlive() || !isHanging(target)) {
            release(target);
            return;
        }
        CompoundTag tag = data.getCompound(ANCHOR_TAG);
        double ax = tag.getDouble("X");
        double ay = tag.getDouble("Y");
        double az = tag.getDouble("Z");
        target.setNoGravity(true);
        target.fallDistance = 0.0F;
        if (target.isUsingItem()) {
            target.stopUsingItem(); // drops a raised shield or a drawn bow
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        double dx = ax - target.getX();
        double dy = ay - target.getY();
        double dz = az - target.getZ();
        double snap = LevicorpusRules.SNAP_DISTANCE;
        if (dx * dx + dy * dy + dz * dz > snap * snap) {
            DisableSupport.snapTo(target, ax, ay, az);
            target.setDeltaMovement(Vec3.ZERO);
        } else {
            double gain = LevicorpusRules.HOLD_GAIN;
            target.setDeltaMovement(dx * gain, dy * gain, dz * gain);
        }
        if (target instanceof ServerPlayer) {
            target.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        if (!event.getEntity().level().isClientSide() && event.getEffect() == ExampleMod.LEVICORPUS_EFFECT.get()) {
            release(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (!event.getEntity().level().isClientSide() && instance != null
                && instance.getEffect().value() == ExampleMod.LEVICORPUS_EFFECT.get()) {
            release(event.getEntity());
        }
    }

    /** Adept and up: a hanging target takes +25% damage (before armour). */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        MobEffectInstance hang = target.getEffect(DisableSupport.holder(ExampleMod.LEVICORPUS_EFFECT));
        if (hang != null) {
            event.setAmount(event.getAmount() * LevicorpusRules.damageTakenMultiplier(hang.getAmplifier() + 1));
        }
    }

    // ---- No items or shields while hanging (both sides, so the client never even swings). Wands are exempt.

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (isHanging(event.getEntity()) && !isWand(event.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isHanging(event.getEntity()) && !isWand(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isHanging(event.getEntity()) && !isWand(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static boolean isWand(ItemStack stack) {
        return stack.getItem() instanceof MagicWandItem;
    }
}
```

- [ ] **Step 2: The client movement lock.** Create `src/main/java/com/hogwartsmagic/client/HangInputLock.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.disable.LevicorpusSpell;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client half of the Levicorpus hang, driven by the levicorpus effect the server sends to the hanging
 * player. Movement is client-authoritative for players, so the walk, jump and sneak input is zeroed
 * here; the server also holds the player at the anchor (LevicorpusSpell.onLivingTick) and teleports
 * them back if they drift, so a modified client gains nothing.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HangInputLock {
    private HangInputLock() {
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!LevicorpusSpell.isHanging(event.getEntity())) {
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
}
```

- [ ] **Step 3: Delegate from `SpellType`.** Replace:
```java
            case LEVICORPUS -> {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 1, false, false, false));
                target.addEffect(new MobEffectInstance(ExampleMod.LEVICORPUS_EFFECT.getHolder().get(), 100, 0, false, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            }
```
with:
```java
            case LEVICORPUS -> com.hogwartsmagic.spell.disable.LevicorpusSpell.apply(target, caster);
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.levicorpus.desc": "Dangles the target helplessly in the air by their ankles.",
```
with:
```json
  "spell.hogwartsmagic.levicorpus.desc": "Dangles the target in the air by their ankles: no items, no shield. At Master, cast it again to drop them hard.",
```

- [ ] **Step 5: Check the old Levitation is gone**

Run: `grep -n "MobEffects.LEVITATION, 100, 1" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.

- [ ] **Step 6: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/disable/LevicorpusSpell.java src/main/java/com/hogwartsmagic/client/HangInputLock.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Levicorpus hang the target in place with a Master Liberacorpus drop" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests, server-safety grep and dedicated server**

Run: `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/disable src/main/java/com/hogwartsmagic/spell/TerrainRules.java`
Expected: no output (common code stays client-free).

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `disable`, `TerrainRules`, `VisibleEffects`, `HangInputLock` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed)

Survival world, cheats on, wand in hand, the five spells learned. Use `/spellmastery setrank @s <spell> <1-4>` to switch ranks and `/crowdcontrol reset @e` between checks so diminishing returns don't hide anything.
1. Stupefy I on a zombie: it freezes in place with gold stars for 1.5s, no blindness or slowness particles.
2. Stupefy I on Voldemort while his Avada windup sparks rise: the windup breaks (smoke, glass sound) and he is stunned for 0.75s (boss half).
3. Stupefy on yourself via a second player (LAN) while channelling Protego Maxima: the barrier drops; the stun lasts 0.9s (PvP 60% of 1.5s).
4. Stupefy II: the zombie is knocked back slightly.
5. Stupefy IV next to two zombies: the second one is stunned too, with a red spark line between them. A nearby villager or Student is never chained. The beam visibly flies faster.
6. Petrificus I on a zombie: it tips over backwards and lies flat for 5s; a second player watching sees the same; punching it once stands it back up at once with a stone-break sound.
7. Petrificus II: a 1-damage hit (fist) doesn't break it; a sword hit does.
8. Petrificus IV: the breaking hit deals about 1.5× the usual damage.
9. Petrificus on a zombie mid-jump off a ledge: it falls and takes fall damage.
10. Incarcerous I on a zombie: chains to the ground; it keeps attacking you if you're close but can't follow you past 2 blocks from where it was bound; after 5s it walks freely.
11. Incarcerous II next to a group: nearby zombies are chained too (3.5s); you are never chained by your own cast.
12. Incarcerous III on a flying phantom or a player gliding with elytra: it is dragged to the ground and can't take off again until the chains break.
13. Incarcerous IV on a zombie with two zombies nearby: they are pulled next to it with chain links drawn between them; knocking one back moves all three.
14. Glacius I on a zombie: Slowness II, frost on it, no freeze yet. A second Glacius within 4s: it shakes frozen for 2s; a sword hit right after deals about 30% more with a glass-break sound.
15. Glacius I on a zombie standing in shallow water: the surface water around it turns to frosted ice that melts after a few seconds. In a spawn-protected area as a non-op, or with `mobGriefing false` for a Death Eater caster, no ice appears.
16. Glacius III: the follow-up hit on a frozen target deals about 60% more.
17. Freeze a zombie solid, then hit it with Incendio: it thaws at once (steam puff), the stun ends.
18. Glacius IV: freeze a zombie solid and kill it: it shatters into ice particles and nearby mobs get Slowness III; you don't.
19. A Death Eater casting Glacius twice on you freezes you solid for 2s (rank 1), not longer.
20. Levicorpus I on a zombie: it flips upside down and hangs 1.5 blocks up, hovering still for 3s, then drops normally. A second player watching sees it upside down.
21. Levicorpus on a skeleton: it can't draw its bow while hanging.
22. Levicorpus on yourself via a second player: you hang, can't walk, eat or raise a shield, but can still cast with your wand; after 1.8s (PvP) you fall back to normal gravity.
23. Levicorpus II: a hanging zombie takes about 25% more damage.
24. Levicorpus IV, then Levicorpus IV again on the hanging zombie: it slams down and takes 3 damage at once.
25. Stupefy, then Petrificus, then Incarcerous on one zombie within 6s: the second lasts half as long, the third does nothing (diminishing returns).
26. A house-elf hit by Stupefy or Levicorpus can't Disapparate while it lasts.
27. Voldemort tethered by Incarcerous keeps casting (he's bound, not silenced).

- [ ] **Step 3: Record results.** Fix any failed check in a follow-up commit whose message says what was wrong. Then tell the user Phase 3 is ready to push.

---

## Self-Review

- **Spec coverage, Phase 3 table:**
  - Stupefy: 1.5s true stun via `CrowdControl.apply(STUNNED)`, interrupt (players' channels and Voldemort's windup, always) (T3); Master chain to 1 more enemy for half the stun (T3); Adept/Expert duration and knockback (T1, T3); Master beam speed (T3).
  - Petrificus: topple (hidden `petrified` marker synced by `VisibleEffects`, rendered rotated 90° in `ClientForgeEvents`, the live Levicorpus render approach) + 5s lock that breaks on damage (T2, T4); Master breaking hit +50% (T4); Adept threshold and durations (T1).
  - Incarcerous: 2-block tether enforced server-side (velocity clamp + teleport snap), target can still fight (no stun, no slowness, Voldemort keeps casting) (T2, T5); Expert flyers pulled down and kept down (T5); Master binds targets together on a 1-block leash with shared knockback and chain links (T5).
  - Glacius: freeze build-up (chill marker window) → second hit freezes solid with stunned via `CrowdControl` and brittle via `CombatEffects.applyBrittle`, amplifiers matched to `BrittleRules` in a test (T1, T6); water at the feet → frosted ice through `TerrainRules` (T6); fire thaws (design-doc weakness) (T6); Master shatter on death slows nearby (T6); steam combo kept (T6).
  - Levicorpus: hang in place 1.5 blocks up, hovering, no items or shields (server + client input lock) (T7); Adept +25% damage (T7); Master recast drops hard (T7). Watchers now see the hang (T2 `VisibleEffects`).
  - Diminishing returns, boss 0.5× and PvP 60%: every hard CC goes through `CrowdControl.apply` or `CrowdControl.scaleDuration` (T2 `applyHiddenCrowdControl`, T3 to T7).
  - NPC casters are rank 1 through `DisableSupport.casterRank` (T2).
  - Code that keyed off the old effects updated: Voldemort's lockout (T2), house-elf pinning (T2), spell descriptions (T3 to T7).
- **Placeholder scan:** no TBD, no "similar to". Every edit quotes the text it replaces. Anchors added by Phase 2 (`SILENCED` registration, the `wand_radial.silenced` lang line) are quoted from the Phase 2 plan.
- **Signature consistency:** `DisableSupport.applyHiddenCrowdControl(LivingEntity, RegistryObject<MobEffect>, int, int, Entity)` returns `int` (T2), used in T5 and T7. `DisableSupport.addHiddenMarker(LivingEntity, RegistryObject<MobEffect>, int, int, Entity)` (T2) used in T4 and T6. `DisableSupport.casterRank(Entity, SpellType)` (T2) used in T3 to T7. `CrowdControl.apply(LivingEntity, Holder<MobEffect>, int, Entity)` and `CrowdControl.scaleDuration(LivingEntity, int, Entity)` match the Phase 2 plan. `CombatEffects.applyBrittle(LivingEntity, int, int, Entity)` matches Phase 2. `TetherMath.correctedVelocity/snapPoint` return `double[3]` (T1), used that way in T5. `GlaciusRules.brittleTicks(int appliedFreezeTicks)` (T1) is called with the ticks `CrowdControl.apply` returned (T6). `LevicorpusSpell.isHanging(LivingEntity)` (T7) is used by `HangInputLock` (T7). `VoldemortEntity.interruptCasting()` (T2) is used by `DisableSupport.interrupt` (T2).
- **Known limits, on purpose:** a hacked client can still push a hanging or tethered player a little between server corrections (the snap teleport bounds it); a tethered mob's own pathing fights the leash, which shows as a slight tug at the edge; Novice and Adept tethers measure flat distance, so a flyer can rise until Expert; the chip damage every beam deals breaks a Novice petrification if a second spell hits it; the topple rotates around the feet, so a target toppling against a wall clips into it.
