# Spell Mastery Phase 5b (Utility: Reparo, Accio, Ascendio, Depulso, Apparition) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The second half of the support and utility spells gets its rank identity. Reparo mends more blocks over a wider radius per rank and, from Expert, mends items. Accio pulls items as well as creatures, empties the first stack of a container from Expert, and snatches a player's held item at Master. Ascendio launches you (aim down or crouch-cast) with fall protection, double-casts in midair at Expert and surges up through water and shoves enemies away at Master. Depulso slams targets into walls, pushes a crowd and bats projectiles back from Expert, and smashes mobs into each other at Master. Apparition's range and channel time scale with rank, splinching is a config value (10% at Novice by default, can be switched off), and a Master can jump to any Floo fireplace they have used in the same dimension.

**Architecture:** Same shape as Phase 5a. Per-rank numbers in Minecraft-free `*Rules` classes in `spell/support/`, tested in Task 1. One Minecraft-bound class per spell (`ReparoSpell`, `AccioSpell`, `AscendioSpell`, `DepulsoSpell`, `ApparitionSpell`) holds the behaviour and its Forge hooks; `SpellType.applyHitEffect`, `SpellBeamEntity` and `SpellPackets.CastSpellPacket.handle` delegate. Reparo keeps `util/ReparoManager` and only swaps its fixed radius and cap for rank values. Apparition keeps its channel in `ModEvents` and reads its numbers from `ApparitionRules`; known fireplaces are a small list in `PlayerPersisted` (survives death), filled whenever the player uses a Floo fireplace.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 5: Support and utility", "Decisions that change the design doc" (Decision 1; Decision 10: splinching configurable, 10% at Novice, can be turned off) and "Global constraints". Numbers the roadmap leaves open come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` sections "E. Movement and force spells", "F. Defense and support" (Reparo) and "H. Special-purpose spells" (Apparition). Where they disagree, the roadmap wins. Apparition mana stays a flat 100 (Phase 1 ruling).

**Depends on Phases 2, 3, 4 and 5a (all merged first).** This plan calls, by exact signature:
- Phase 2: `CombatEffects.responsible(@Nullable Entity)`, `CombatEffects.isCastingBlocked(LivingEntity)`, `CombatEffects.notifyCastBlocked(Player)`, `CombatTags.isBoss(@Nullable Entity)` → `boolean`.
- Phase 3: `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int`, `DisableSupport.isEnemyOf(@Nullable Entity caster, LivingEntity candidate, LivingEntity mainTarget)` → `boolean`.
- Phase 4: `DamageSupport.scaled(@Nullable Entity caster, float base)` → `float`, `DamageSupport.isSplashTarget(@Nullable Entity caster, LivingEntity candidate, @Nullable LivingEntity primary)` → `boolean`.
- Phase 5a: `SupportMana.trySpend(ServerPlayer, float)` → `boolean`; package-private `RankIndex.of(int)` in `spell.support`; the private helper `ModEvents.isLeftClickSelfCast(SpellType)`; `HEAL` in `MasteryAwards.SELF_OR_CHANNEL`.
- Phase 1: `SpellMasteryData.getRank(Player, SpellType)`, `SpellCosts.manaCost/masteryMultiplier/startCooldown`, `MasteryAwards.onBlockEffect(ServerPlayer, SpellType)`.

Before Task 1, confirm they are in:
```bash
grep -n "public static int casterRank" src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java
grep -n "public static boolean isSplashTarget" src/main/java/com/hogwartsmagic/spell/damage/DamageSupport.java
grep -n "public static boolean trySpend" src/main/java/com/hogwartsmagic/spell/support/SupportMana.java
grep -n "private static boolean isLeftClickSelfCast" src/main/java/com/hogwartsmagic/event/ModEvents.java
```
Each must print one line. If any doesn't, stop: the phase it belongs to isn't merged.

## Global Constraints

- **Ranks:** every per-rank behaviour reads the caster's rank through `DisableSupport.casterRank(caster, spell)` or, for player-only paths, `SpellMasteryData.getRank(player, spell)` (1..4). **NPC casters are rank 1**, except that NPC Reparo keeps today's sweep (radius 6, up to 32 blocks) so the Students' rebuilding AI is not crippled.
- **Per-rank numbers (ticks at 20/s):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Reparo (blocks) | up to 4 blocks within 6 | 10 within 10 | 24 within 14 | 48 within 20 |
  | Reparo (items) | — | — | **left-click self-cast mends the off-hand item, else the first damaged armour piece (head to feet): +10% of max durability (rounded up) per cast**; mana = the normal cast + `round(maxDurability / 100)` (0..20) × the mastery discount | same |
  | Accio (creatures) | reels in up to 20 max health | 40 | 80 | any non-boss; bosses are never reeled |
  | Accio (items) | a block hit pulls every dropped item within 1.5 blocks of the impact to you (at most 16) | 3 | 4; **plus the first stack of a container you could open** | 5; **a PvP hit also pulls the victim's main-hand item to you** (never the Elder Wand) |
  | Ascendio (self) | aim down (pitch ≥ 60) or crouch-cast: launch to 6 blocks, forward momentum kept, no fall damage for 100 | 9 blocks | 12; **a midair self-cast gives a second boost (half the height), ignoring the cooldown once per jump** | 15; **cast in water: surge upward at 1.2 blocks/tick for up to 60; on take-off, enemies within 2 blocks are shoved away (1.0, lift 0.3)** |
  | Depulso | knockback 1.5 (lift 0.5); a wall within 10 ticks: 3 slam damage | 1.8; slam 5 | 2.0; slam 5; **enemies within 2 blocks of the impact are banished too (also on a block hit); projectiles within 1.5 blocks of the beam's path fly back, owned by you** | 2.2; slam 8; **a target banished into another mob: both take the slam** |
  | Apparition | range 48, channel 80; splinch = config chance (default 10%) | 80, 60; half the chance | 128, 40; a fifth | 200, 20; never; **known Floo fireplaces in this dimension listed on the map (nearest 8): a click Apparates you there at any distance** |

- **Slam damage** is `flyIntoWall` damage × the caster's house multiplier (`DamageSupport.scaled`). The beam's 0.1 chip already landed a tick earlier, so vanilla invulnerability frames deduct 0.1 from the slam; that's accepted.
- **Accio and claims:** a container pull first asks `level.mayInteract` (spawn protection), the container's own lock (`BaseContainerBlockEntity.canOpen`), a blocked chest lid, and then posts a `PlayerInteractEvent.RightClickBlock` as the caster: claim mods veto there, and the mod's own guardians react as if the chest were opened (the Gringotts alarm, the Living Statues, the merfolk hoard). Pulled container and PvP items are reserved for the caster (`ItemEntity.setTarget`).
- **PvP item pull** needs the server's PvP switch on and `thief.canHarmPlayer(victim)` (team friendly-fire rules).
- **Apparition:** mana stays a flat 100; a jump to a fireplace lands on the block above it (like Floo), is refused if that spot is in a Cave Inimicum ward, may load the destination chunk once (it has no range limit), and forgets a fireplace that no longer exists. Known fireplaces: the source and destination of every completed Floo trip and every fireplace the player opens the Floo menu at, at most 32 remembered.
- **Splinch config:** `apparitionSplinching` (default `true`) and `apparitionSplinchChance` (default `0.10`). The chance no longer grows with distance.
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/`, is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch, and talks back through C2S packets the server validates. This plan adds one C2S packet (`ChooseApparitionFireplacePacket`, re-validated against the server's own known list) and extends one S2C packet (`OpenApparitionMapPacket`).
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine.
- Screens: `renderTransparentBackground`, no blur. (`ApparitionMapScreen` already does; it gains no widgets, so `super.render` is never called.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards. (This plan places and breaks no blocks; Reparo's existing restore path is unchanged.)
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs).
- `en_us.json` must stay valid JSON after every edit: replacements keep the original line's trailing comma, and an inserted line ends with a comma and goes after a line that already ends with one.
- Line numbers drift. Every edit below names the exact text to find; trust the text, not a number.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/support/ReparoRules.java` | Pure: blocks, radius, item repair and price |
| Create `src/main/java/com/hogwartsmagic/spell/support/AccioRules.java` | Pure: reel cap, item radius, unlocks, pull speed |
| Create `src/main/java/com/hogwartsmagic/spell/support/AscendioRules.java` | Pure: heights, launch velocity, self-cast test, unlocks |
| Create `src/main/java/com/hogwartsmagic/spell/support/DepulsoRules.java` | Pure: knockback, slam, unlocks |
| Create `src/main/java/com/hogwartsmagic/spell/support/ApparitionRules.java` | Pure: range, channel, splinch chance, map scaling |
| Create `src/main/java/com/hogwartsmagic/spell/support/ReparoSpell.java` | Item repair self-cast |
| Create `src/main/java/com/hogwartsmagic/spell/support/AccioSpell.java` | Reel caps, item and container pulls, PvP snatch, item homing |
| Create `src/main/java/com/hogwartsmagic/spell/support/AscendioSpell.java` | Self-launch, double cast, surge, take-off shove, fall protection |
| Create `src/main/java/com/hogwartsmagic/spell/support/DepulsoSpell.java` | Banish, wall and mob slams, area push, projectile deflect |
| Create `src/main/java/com/hogwartsmagic/spell/support/ApparitionSpell.java` | Known Floo fireplaces |
| Modify `src/main/java/com/hogwartsmagic/util/ReparoManager.java` | Rank radius and cap |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | Reparo self-cast, Ascendio self-cast and double cast, Apparition packets, Floo memory |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Accio and Depulso delegate |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Accio and Depulso block branches, Depulso deflect tick |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Reparo left-click, Apparition numbers, fireplace jump, Floo memory |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | Ascendio earns cast XP |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | Splinch toggle and chance |
| Modify `src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java` | Scaled map, fireplace list |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | Pass fireplaces to the map |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Descriptions and messages |
| Create tests under `src/test/java/com/hogwartsmagic/spell/support/` | Unit tests |

---

### Task 1: The pure rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/ReparoRules.java`, `AccioRules.java`, `AscendioRules.java`, `DepulsoRules.java`, `ApparitionRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/ReparoRulesTest.java`, `AccioRulesTest.java`, `AscendioRulesTest.java`, `DepulsoRulesTest.java`, `ApparitionRulesTest.java`

**Interfaces:**
- Consumes: `RankIndex.of(int)` (Phase 5a, package-private, same package)
- Produces:
  - `ReparoRules.NPC_MAX_BLOCKS = 32`, `NPC_RADIUS = 6.0`, `ITEM_EXTRA_MANA_CAP = 20`; `static int maxBlocks(int rank)`, `static double radius(int rank)`, `static boolean repairsItems(int rank)`, `static int itemRepairAmount(int maxDamage)`, `static int itemExtraMana(int maxDamage)`
  - `AccioRules.MAX_ITEMS = 16`, `ITEM_PULL_TICKS = 60`, `ARRIVE_DISTANCE = 1.2`; `static float maxPullHealth(int rank)`, `static boolean canReel(int rank, float maxHealth, boolean boss)`, `static double itemRadius(int rank)`, `static boolean pullsContainers(int rank)`, `static boolean pullsPlayerItem(int rank)`, `static double itemPullSpeed(double distance)`
  - `AscendioRules.GRAVITY = 0.08`, `DRAG = 0.98`, `SELF_CAST_PITCH = 60.0f`, `SAFE_FALL_TICKS = 100`, `DOUBLE_CAST_HEIGHT_FACTOR = 0.5`, `SURGE_SPEED = 1.2`, `SURGE_MAX_TICKS = 60`, `PUSH_RADIUS = 2.0`, `PUSH_STRENGTH = 1.0`, `PUSH_LIFT = 0.3`, `AIR_CHARGE_GRACE_TICKS = 5`; `static double heightBlocks(int rank)`, `static double apexHeight(double initialVelocity)`, `static double launchVelocity(double blocks)`, `static boolean wantsSelfCast(float pitch, boolean crouching)`, `static boolean doubleCasts(int rank)`, `static boolean surges(int rank)`, `static boolean pushesOnTakeoff(int rank)`
  - `DepulsoRules.SLAM_WINDOW_TICKS = 10`, `VERTICAL_LIFT = 0.5`, `AREA_RADIUS = 2.0`, `DEFLECT_RADIUS = 1.5`, `WALL_PROBE = 0.35`, `MOB_CONTACT_INFLATE = 0.15`; `static double knockback(int rank)`, `static float slamDamage(int rank)`, `static boolean pushesArea(int rank)`, `static boolean deflectsProjectiles(int rank)`, `static boolean slamsIntoMobs(int rank)`
  - `ApparitionRules.RANGE_SLACK = 4.0`, `MAX_KNOWN_FIREPLACES = 32`, `MAX_LISTED_FIREPLACES = 8`; `static int range(int rank)`, `static int channelTicks(int rank)`, `static double splinchChance(int rank, double baseChance, boolean enabled)`, `static boolean jumpsToFireplaces(int rank)`, `static int mapStep(int span, int tilePx, int maxPx)`, `static int tileCenterOffset(int tile, int step, int span)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/support/ReparoRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReparoRulesTest {
    @Test
    void blocksAndRadiusPerRank() {
        assertEquals(4, ReparoRules.maxBlocks(1));
        assertEquals(10, ReparoRules.maxBlocks(2));
        assertEquals(24, ReparoRules.maxBlocks(3));
        assertEquals(48, ReparoRules.maxBlocks(4));
        assertEquals(6.0, ReparoRules.radius(1), 1e-9);
        assertEquals(10.0, ReparoRules.radius(2), 1e-9);
        assertEquals(14.0, ReparoRules.radius(3), 1e-9);
        assertEquals(20.0, ReparoRules.radius(4), 1e-9);
    }

    @Test
    void itemsFromExpert() {
        assertFalse(ReparoRules.repairsItems(2));
        assertTrue(ReparoRules.repairsItems(3));
    }

    @Test
    void repairIsATenthRoundedUp() {
        assertEquals(6, ReparoRules.itemRepairAmount(59));
        assertEquals(25, ReparoRules.itemRepairAmount(250));
        assertEquals(157, ReparoRules.itemRepairAmount(1561));
        assertEquals(1, ReparoRules.itemRepairAmount(1));
        assertEquals(0, ReparoRules.itemRepairAmount(0));
    }

    @Test
    void extraManaScalesWithDurabilityAndIsCapped() {
        assertEquals(0, ReparoRules.itemExtraMana(33));
        assertEquals(1, ReparoRules.itemExtraMana(59));
        assertEquals(3, ReparoRules.itemExtraMana(250));
        assertEquals(5, ReparoRules.itemExtraMana(528));
        assertEquals(16, ReparoRules.itemExtraMana(1561));
        assertEquals(20, ReparoRules.itemExtraMana(2031));
        assertEquals(20, ReparoRules.itemExtraMana(5000));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/AccioRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccioRulesTest {
    @Test
    void reelCapGrowsAndNeverTakesBosses() {
        assertTrue(AccioRules.canReel(1, 20f, false));
        assertFalse(AccioRules.canReel(1, 24f, false));
        assertTrue(AccioRules.canReel(2, 40f, false));
        assertTrue(AccioRules.canReel(3, 80f, false));
        assertFalse(AccioRules.canReel(3, 100f, false));
        assertTrue(AccioRules.canReel(4, 500f, false));
        assertFalse(AccioRules.canReel(4, 20f, true));
    }

    @Test
    void itemRadiusPerRank() {
        assertEquals(1.5, AccioRules.itemRadius(1), 1e-9);
        assertEquals(3.0, AccioRules.itemRadius(2), 1e-9);
        assertEquals(4.0, AccioRules.itemRadius(3), 1e-9);
        assertEquals(5.0, AccioRules.itemRadius(4), 1e-9);
    }

    @Test
    void unlocks() {
        assertFalse(AccioRules.pullsContainers(2));
        assertTrue(AccioRules.pullsContainers(3));
        assertFalse(AccioRules.pullsPlayerItem(3));
        assertTrue(AccioRules.pullsPlayerItem(4));
    }

    @Test
    void pullSpeedRisesWithDistanceAndCaps() {
        assertEquals(0.3, AccioRules.itemPullSpeed(0.0), 1e-9);
        assertEquals(0.8, AccioRules.itemPullSpeed(5.0), 1e-9);
        assertEquals(1.2, AccioRules.itemPullSpeed(40.0), 1e-9);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/AscendioRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AscendioRulesTest {
    @Test
    void apexMatchesTheVanillaJump() {
        // A vanilla jump starts at 0.42 blocks/tick and peaks at about 1.252 blocks.
        assertEquals(1.252, AscendioRules.apexHeight(0.42), 0.01);
        assertEquals(0.0, AscendioRules.apexHeight(0.0), 1e-9);
    }

    @Test
    void launchVelocityReachesTheHeight() {
        for (int rank = 1; rank <= 4; rank++) {
            double blocks = AscendioRules.heightBlocks(rank);
            double apex = AscendioRules.apexHeight(AscendioRules.launchVelocity(blocks));
            assertTrue(apex >= blocks - 1e-6, "rank " + rank + " apex " + apex);
            assertTrue(apex < blocks + 0.3, "rank " + rank + " apex " + apex);
        }
        assertTrue(AscendioRules.launchVelocity(15) > AscendioRules.launchVelocity(9));
        assertTrue(AscendioRules.launchVelocity(9) > AscendioRules.launchVelocity(6));
        assertEquals(0.0, AscendioRules.launchVelocity(0), 1e-9);
    }

    @Test
    void heightsPerRank() {
        assertEquals(6.0, AscendioRules.heightBlocks(1), 1e-9);
        assertEquals(9.0, AscendioRules.heightBlocks(2), 1e-9);
        assertEquals(12.0, AscendioRules.heightBlocks(3), 1e-9);
        assertEquals(15.0, AscendioRules.heightBlocks(4), 1e-9);
    }

    @Test
    void selfCastIsAimingDownOrCrouching() {
        assertTrue(AscendioRules.wantsSelfCast(70f, false));
        assertTrue(AscendioRules.wantsSelfCast(60f, false));
        assertTrue(AscendioRules.wantsSelfCast(10f, true));
        assertFalse(AscendioRules.wantsSelfCast(10f, false));
    }

    @Test
    void unlocks() {
        assertFalse(AscendioRules.doubleCasts(2));
        assertTrue(AscendioRules.doubleCasts(3));
        assertFalse(AscendioRules.surges(3));
        assertTrue(AscendioRules.surges(4));
        assertFalse(AscendioRules.pushesOnTakeoff(3));
        assertTrue(AscendioRules.pushesOnTakeoff(4));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/DepulsoRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepulsoRulesTest {
    @Test
    void knockbackAndSlamPerRank() {
        assertEquals(1.5, DepulsoRules.knockback(1), 1e-9);
        assertEquals(1.8, DepulsoRules.knockback(2), 1e-9);
        assertEquals(2.0, DepulsoRules.knockback(3), 1e-9);
        assertEquals(2.2, DepulsoRules.knockback(4), 1e-9);
        assertEquals(3f, DepulsoRules.slamDamage(1), 1e-4);
        assertEquals(5f, DepulsoRules.slamDamage(2), 1e-4);
        assertEquals(5f, DepulsoRules.slamDamage(3), 1e-4);
        assertEquals(8f, DepulsoRules.slamDamage(4), 1e-4);
    }

    @Test
    void unlocks() {
        assertFalse(DepulsoRules.pushesArea(2));
        assertTrue(DepulsoRules.pushesArea(3));
        assertFalse(DepulsoRules.deflectsProjectiles(2));
        assertTrue(DepulsoRules.deflectsProjectiles(3));
        assertFalse(DepulsoRules.slamsIntoMobs(3));
        assertTrue(DepulsoRules.slamsIntoMobs(4));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/ApparitionRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApparitionRulesTest {
    @Test
    void rangeAndChannelPerRank() {
        assertEquals(48, ApparitionRules.range(1));
        assertEquals(80, ApparitionRules.range(2));
        assertEquals(128, ApparitionRules.range(3));
        assertEquals(200, ApparitionRules.range(4));
        assertEquals(80, ApparitionRules.channelTicks(1));
        assertEquals(60, ApparitionRules.channelTicks(2));
        assertEquals(40, ApparitionRules.channelTicks(3));
        assertEquals(20, ApparitionRules.channelTicks(4));
    }

    @Test
    void splinchFallsWithRankAndCanBeSwitchedOff() {
        assertEquals(0.10, ApparitionRules.splinchChance(1, 0.10, true), 1e-9);
        assertEquals(0.05, ApparitionRules.splinchChance(2, 0.10, true), 1e-9);
        assertEquals(0.02, ApparitionRules.splinchChance(3, 0.10, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(4, 0.10, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(1, 0.10, false), 1e-9);
        assertEquals(1.0, ApparitionRules.splinchChance(1, 1.5, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(1, -1.0, true), 1e-9);
    }

    @Test
    void fireplacesAtMaster() {
        assertFalse(ApparitionRules.jumpsToFireplaces(3));
        assertTrue(ApparitionRules.jumpsToFireplaces(4));
    }

    @Test
    void mapStepFitsTheScreen() {
        assertEquals(1, ApparitionRules.mapStep(97, 2, 400));
        assertEquals(2, ApparitionRules.mapStep(161, 2, 300));
        assertEquals(3, ApparitionRules.mapStep(401, 2, 300));
        assertEquals(401, ApparitionRules.mapStep(401, 2, 0));
        assertEquals(1, ApparitionRules.mapStep(0, 2, 300));
    }

    @Test
    void tileCenterStaysInsideTheSpan() {
        assertEquals(0, ApparitionRules.tileCenterOffset(0, 1, 97));
        assertEquals(5, ApparitionRules.tileCenterOffset(5, 1, 97));
        assertEquals(1, ApparitionRules.tileCenterOffset(0, 3, 401));
        assertEquals(31, ApparitionRules.tileCenterOffset(10, 3, 401));
        assertEquals(400, ApparitionRules.tileCenterOffset(133, 3, 401));
        assertEquals(400, ApparitionRules.tileCenterOffset(134, 3, 401));
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests 'com.hogwartsmagic.spell.support.*'`
Expected: compile FAIL, `cannot find symbol: class ReparoRules` (and the other new rule classes).

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/support/ReparoRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Reparo: how much it mends per rank, and the price of mending an item. Pure numbers. */
public final class ReparoRules {
    /** NPC casters (Students rebuilding) keep the sweep they had before mastery. */
    public static final int NPC_MAX_BLOCKS = 32;
    public static final double NPC_RADIUS = 6.0;
    public static final int ITEM_EXTRA_MANA_CAP = 20;

    private static final int[] MAX = {4, 10, 24, 48};
    private static final double[] RADIUS = {6.0, 10.0, 14.0, 20.0};

    private ReparoRules() {
    }

    public static int maxBlocks(int rank) {
        return MAX[RankIndex.of(rank)];
    }

    public static double radius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static boolean repairsItems(int rank) {
        return rank >= 3;
    }

    /** A tenth of max durability, rounded up (integer math, no float drift); 0 for an undamageable item. */
    public static int itemRepairAmount(int maxDamage) {
        if (maxDamage <= 0) {
            return 0;
        }
        return Math.max(1, (maxDamage + 9) / 10);
    }

    /** Extra mana for an item repair: one per 100 durability, rounded, 0..20 (wood 1, iron tools 3, diamond 16, netherite 20). */
    public static int itemExtraMana(int maxDamage) {
        return Math.max(0, Math.min(ITEM_EXTRA_MANA_CAP, Math.round(maxDamage / 100.0f)));
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/AccioRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Accio: what it can reel in and how far it reaches for items. Pure numbers. */
public final class AccioRules {
    public static final int MAX_ITEMS = 16;
    public static final int ITEM_PULL_TICKS = 60;
    public static final double ARRIVE_DISTANCE = 1.2;

    private static final float[] MAX_HEALTH = {20.0f, 40.0f, 80.0f, Float.MAX_VALUE};
    private static final double[] ITEM_RADIUS = {1.5, 3.0, 4.0, 5.0};

    private AccioRules() {
    }

    public static float maxPullHealth(int rank) {
        return MAX_HEALTH[RankIndex.of(rank)];
    }

    public static boolean canReel(int rank, float maxHealth, boolean boss) {
        return !boss && maxHealth <= maxPullHealth(rank);
    }

    public static double itemRadius(int rank) {
        return ITEM_RADIUS[RankIndex.of(rank)];
    }

    public static boolean pullsContainers(int rank) {
        return rank >= 3;
    }

    public static boolean pullsPlayerItem(int rank) {
        return rank >= 4;
    }

    /** Blocks per tick toward the caster: faster from far away, capped so items don't overshoot. */
    public static double itemPullSpeed(double distance) {
        return Math.min(1.2, 0.3 + 0.1 * Math.max(0.0, distance));
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/AscendioRules.java`:
```java
package com.hogwartsmagic.spell.support;

/**
 * Ascendio's self-launch. {@link #apexHeight} simulates vanilla living-entity vertical motion (move,
 * then {@code v = (v - 0.08) * 0.98}) so {@link #launchVelocity} can hit a height in blocks exactly.
 * Pure numbers.
 */
public final class AscendioRules {
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    public static final float SELF_CAST_PITCH = 60.0f;
    public static final int SAFE_FALL_TICKS = 100;
    public static final double DOUBLE_CAST_HEIGHT_FACTOR = 0.5;
    public static final double SURGE_SPEED = 1.2;
    public static final int SURGE_MAX_TICKS = 60;
    public static final double PUSH_RADIUS = 2.0;
    public static final double PUSH_STRENGTH = 1.0;
    public static final double PUSH_LIFT = 0.3;
    public static final int AIR_CHARGE_GRACE_TICKS = 5;

    private static final double[] HEIGHT = {6.0, 9.0, 12.0, 15.0};

    private AscendioRules() {
    }

    public static double heightBlocks(int rank) {
        return HEIGHT[RankIndex.of(rank)];
    }

    public static double apexHeight(double initialVelocity) {
        double height = 0.0;
        double v = initialVelocity;
        for (int tick = 0; tick < 1000 && v > 0.0; tick++) {
            height += v;
            v = (v - GRAVITY) * DRAG;
        }
        return height;
    }

    /** Smallest upward speed whose apex reaches {@code blocks}. */
    public static double launchVelocity(double blocks) {
        if (blocks <= 0.0) {
            return 0.0;
        }
        double low = 0.0;
        double high = 6.0;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) * 0.5;
            if (apexHeight(mid) < blocks) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    public static boolean wantsSelfCast(float pitch, boolean crouching) {
        return crouching || pitch >= SELF_CAST_PITCH;
    }

    public static boolean doubleCasts(int rank) {
        return rank >= 3;
    }

    public static boolean surges(int rank) {
        return rank >= 4;
    }

    public static boolean pushesOnTakeoff(int rank) {
        return rank >= 4;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/DepulsoRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Depulso, the banishing charm: knockback and the wall slam. Pure numbers. */
public final class DepulsoRules {
    public static final int SLAM_WINDOW_TICKS = 10;
    public static final double VERTICAL_LIFT = 0.5;
    public static final double AREA_RADIUS = 2.0;
    public static final double DEFLECT_RADIUS = 1.5;
    public static final double WALL_PROBE = 0.35;
    public static final double MOB_CONTACT_INFLATE = 0.15;

    private static final double[] KNOCKBACK = {1.5, 1.8, 2.0, 2.2};
    private static final float[] SLAM = {3.0f, 5.0f, 5.0f, 8.0f};

    private DepulsoRules() {
    }

    public static double knockback(int rank) {
        return KNOCKBACK[RankIndex.of(rank)];
    }

    public static float slamDamage(int rank) {
        return SLAM[RankIndex.of(rank)];
    }

    public static boolean pushesArea(int rank) {
        return rank >= 3;
    }

    public static boolean deflectsProjectiles(int rank) {
        return rank >= 3;
    }

    public static boolean slamsIntoMobs(int rank) {
        return rank >= 4;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/ApparitionRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Apparition: reach, focus time, splinch risk, and how the destination map scales. Pure numbers. */
public final class ApparitionRules {
    public static final double RANGE_SLACK = 4.0;
    public static final int MAX_KNOWN_FIREPLACES = 32;
    public static final int MAX_LISTED_FIREPLACES = 8;

    private static final int[] RANGE = {48, 80, 128, 200};
    private static final int[] CHANNEL = {80, 60, 40, 20};
    private static final double[] SPLINCH_FACTOR = {1.0, 0.5, 0.2, 0.0};

    private ApparitionRules() {
    }

    public static int range(int rank) {
        return RANGE[RankIndex.of(rank)];
    }

    public static int channelTicks(int rank) {
        return CHANNEL[RankIndex.of(rank)];
    }

    /** The Novice chance comes from config; Adept halves it, Expert takes a fifth, Master never splinches. */
    public static double splinchChance(int rank, double baseChance, boolean enabled) {
        if (!enabled) {
            return 0.0;
        }
        double base = Math.max(0.0, Math.min(1.0, baseChance));
        return base * SPLINCH_FACTOR[RankIndex.of(rank)];
    }

    public static boolean jumpsToFireplaces(int rank) {
        return rank >= 4;
    }

    /** Blocks per map tile so {@code span} blocks fit in {@code maxPx} pixels at {@code tilePx} per tile. */
    public static int mapStep(int span, int tilePx, int maxPx) {
        if (span <= 0 || tilePx <= 0) {
            return 1;
        }
        if (maxPx <= 0) {
            return span;
        }
        return Math.max(1, (int) Math.ceil(span * tilePx / (double) maxPx));
    }

    /** Block offset (0..span-1) a tile stands for: its middle block, never past the span. */
    public static int tileCenterOffset(int tile, int step, int span) {
        return Math.min(span - 1, tile * step + step / 2);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests 'com.hogwartsmagic.spell.support.*'`
Expected: `BUILD SUCCESSFUL`; the five new test classes and Phase 5a's six pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/ReparoRules.java src/main/java/com/hogwartsmagic/spell/support/AccioRules.java src/main/java/com/hogwartsmagic/spell/support/AscendioRules.java src/main/java/com/hogwartsmagic/spell/support/DepulsoRules.java src/main/java/com/hogwartsmagic/spell/support/ApparitionRules.java src/test/java/com/hogwartsmagic/spell/support/ReparoRulesTest.java src/test/java/com/hogwartsmagic/spell/support/AccioRulesTest.java src/test/java/com/hogwartsmagic/spell/support/AscendioRulesTest.java src/test/java/com/hogwartsmagic/spell/support/DepulsoRulesTest.java src/test/java/com/hogwartsmagic/spell/support/ApparitionRulesTest.java
git commit -m "Add the per-rank rules for Reparo, Accio, Ascendio, Depulso and Apparition" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Reparo (more blocks per rank, items from Expert)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/ReparoSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/util/ReparoManager.java` (`castReparo`)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (self-cast intercept)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`isLeftClickSelfCast`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ReparoRules` (Task 1); `SupportMana.trySpend` (Phase 5a); `DisableSupport.casterRank` (Phase 3); `SpellCosts.manaCost/masteryMultiplier/startCooldown`, `MasteryAwards.onBlockEffect`, `SpellMasteryData.getRank` (Phase 1)
- Produces: `ReparoSpell.castOnSelf(ServerPlayer player, ItemStack wand, long now)`

- [ ] **Step 1: Rank radius and cap in `ReparoManager`.** Delete the two constant lines:
```java
    private static final double CAST_RADIUS = 6.0D;
    private static final int MAX_REPAIRS_PER_CAST = 32;
```
In the `castReparo` Javadoc, replace:
```java
     * Up to {@link #MAX_REPAIRS_PER_CAST} blocks are repaired per cast, sorted closest
     * to impact first.
```
with:
```java
     * Up to the caster's rank cap ({@link com.hogwartsmagic.spell.support.ReparoRules}) blocks
     * are repaired per cast within the rank radius, sorted closest to impact first. NPC casters
     * keep the old sweep (radius 6, 32 blocks).
```
Then replace:
```java
        prune(level);

        List<TrackedBreakRecord> candidates = RECORDS.values().stream()
                .filter(record -> record.dimensionKey.equals(level.dimension().location().toString()))
                .filter(record -> record.pos.closerToCenterThan(Vec3.atCenterOf(impactPos), CAST_RADIUS))
                .filter(record -> isRepairStillPossible(level, record))
                .sorted(Comparator.comparingDouble(record -> record.pos.distSqr(impactPos)))
                .limit(MAX_REPAIRS_PER_CAST)
                .toList();
```
with:
```java
        prune(level);

        // Players scale with their Reparo rank; NPC casters (students) keep the old wide sweep.
        boolean playerCaster = caster instanceof ServerPlayer;
        int rank = com.hogwartsmagic.spell.disable.DisableSupport.casterRank(caster, SpellType.REPARO);
        double castRadius = playerCaster
                ? com.hogwartsmagic.spell.support.ReparoRules.radius(rank)
                : com.hogwartsmagic.spell.support.ReparoRules.NPC_RADIUS;
        int maxRepairs = playerCaster
                ? com.hogwartsmagic.spell.support.ReparoRules.maxBlocks(rank)
                : com.hogwartsmagic.spell.support.ReparoRules.NPC_MAX_BLOCKS;

        List<TrackedBreakRecord> candidates = RECORDS.values().stream()
                .filter(record -> record.dimensionKey.equals(level.dimension().location().toString()))
                .filter(record -> record.pos.closerToCenterThan(Vec3.atCenterOf(impactPos), castRadius))
                .filter(record -> isRepairStillPossible(level, record))
                .sorted(Comparator.comparingDouble(record -> record.pos.distSqr(impactPos)))
                .limit(maxRepairs)
                .toList();
```
Run: `grep -n "CAST_RADIUS\|MAX_REPAIRS_PER_CAST" src/main/java/com/hogwartsmagic/util/ReparoManager.java`
Expected: no output. (The records are kept within `RETENTION_RADIUS = 30` and for 15s, so radius 20 at Master is covered.)

- [ ] **Step 2: Create `ReparoSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Reparo cast on yourself (left-click), Expert and up: mends the off-hand item, or else the first
 * damaged armour piece from head to feet, by a tenth of its durability. It prices itself (the normal
 * cast plus a share for the item's durability, both mastery-discounted) and takes nothing if there
 * is nothing to mend. Only the damage value changes, so Mending and enchantments are untouched.
 */
public final class ReparoSpell {
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ReparoSpell() {
    }

    public static void castOnSelf(ServerPlayer player, ItemStack wand, long now) {
        int rank = SpellMasteryData.getRank(player, SpellType.REPARO);
        if (!ReparoRules.repairsItems(rank)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.reparo.items_locked")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack item = findDamaged(player);
        if (item.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.reparo.nothing")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int maxDamage = item.getMaxDamage();
        float cost = SpellCosts.manaCost(player, wand, SpellType.REPARO)
                + ReparoRules.itemExtraMana(maxDamage) * SpellCosts.masteryMultiplier(player, SpellType.REPARO);
        if (!SupportMana.trySpend(player, cost)) {
            player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return;
        }
        item.setDamageValue(Math.max(0, item.getDamageValue() - ReparoRules.itemRepairAmount(maxDamage)));
        player.serverLevel().sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.7f, 1.4f);
        ModEvents.revealInvisibilityCloak(player);
        if (!player.isCreative()) {
            SpellCosts.startCooldown(player, wand, SpellType.REPARO, now);
        }
        MasteryAwards.onBlockEffect(player, SpellType.REPARO);
    }

    private static ItemStack findDamaged(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (off.isDamageableItem() && off.isDamaged()) {
            return off;
        }
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isDamageableItem() && worn.isDamaged()) {
                return worn;
            }
        }
        return ItemStack.EMPTY;
    }
}
```

- [ ] **Step 3: Route the self-cast before the flat mana charge.** In `SpellPackets.CastSpellPacket.handle`, replace:
```java
            if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

            // Mana Check
```
with:
```java
            if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

            // Reparo cast on yourself mends the off-hand item or worn armour (Expert and up). It
            // prices itself by the item, so it runs before the flat mana charge below.
            if (p.selfCastProtego && spell == SpellType.REPARO) {
                com.hogwartsmagic.spell.support.ReparoSpell.castOnSelf(player, wand, now);
                return;
            }

            // Mana Check
```

- [ ] **Step 4: Left-click casts Reparo on yourself.** In `ModEvents.java`, in `isLeftClickSelfCast`, replace:
```java
                || spell == SpellType.ARRESTO_MOMENTUM
                || spell == SpellType.LUMOS;
```
with:
```java
                || spell == SpellType.ARRESTO_MOMENTUM
                || spell == SpellType.LUMOS
                || spell == SpellType.REPARO;
```

- [ ] **Step 5: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.reparo.desc": "Rebuilds nearby recently-broken simple blocks if you can pay with matching block items from your inventory.",
```
with:
```json
  "spell.hogwartsmagic.reparo.desc": "Rebuilds recently broken blocks near where it lands, paid with their rubble or matching blocks: 4 within 6 blocks at Novice, up to 48 within 20 at Master. From Expert, left-click mends the item in your off-hand (or your worn armour) by a tenth of its durability.",
```
Then directly after `  "message.hogwartsmagic.apparition.splinched": "You've been Splinched!",` insert:
```json
  "message.hogwartsmagic.reparo.items_locked": "Mending items takes Reparo at Expert rank.",
  "message.hogwartsmagic.reparo.nothing": "Nothing in your off-hand or armour needs mending.",
```

- [ ] **Step 6: Build and test**

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/ReparoSpell.java src/main/java/com/hogwartsmagic/util/ReparoManager.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Scale Reparo by rank and let it mend items from Expert" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Accio (items, containers, the PvP snatch)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/AccioSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`ACCIO` case)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Accio block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `AccioRules` (Task 1); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible`, `CombatTags.isBoss` (Phase 2); `ModEvents.startAccioPull(ServerPlayer, LivingEntity)` (existing)
- Produces: `AccioSpell.onLivingHit(LivingEntity target, @Nullable Entity caster)`, `AccioSpell.onBlockHit(ServerLevel level, BlockPos pos, Direction face, Vec3 impact, @Nullable Entity caster)` → `boolean`

- [ ] **Step 1: Create `AccioSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Accio, the summoning charm. Creatures are reeled in up to a rank weight limit (never bosses).
 * A block hit summons loose items near the impact; from Expert also the first stack of a container
 * the caster could open; at Master a PvP hit snatches the victim's held item (never the Elder Wand,
 * which only Expelliarmus wins). Summoned items home in on the caster for up to 3 seconds. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AccioSpell {
    private record Pull(UUID casterId, ResourceKey<Level> dimension, long until) {
    }

    private static final Map<UUID, Pull> ITEM_PULLS = new HashMap<>();

    private AccioSpell() {
    }

    public static void onLivingHit(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ACCIO);
        Entity owner = CombatEffects.responsible(caster);
        if (!AccioRules.canReel(rank, target.getMaxHealth(), CombatTags.isBoss(target))) {
            if (owner instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.accio.too_heavy"), true);
            }
            return;
        }
        if (AccioRules.pullsPlayerItem(rank) && target instanceof ServerPlayer victim
                && owner instanceof ServerPlayer thief && victim != thief && isPvp(thief, victim)) {
            pullMainHand(victim, thief);
        }
        // Continuously reel the target in toward the caster until it reaches them.
        if (owner instanceof ServerPlayer player) {
            ModEvents.startAccioPull(player, target);
        } else {
            Vec3 toward = target.position().vectorTo(
                    owner != null ? owner.getEyePosition(1) : target.position().add(0, 0, 5))
                    .normalize().scale(0.8);
            target.push(toward.x, 0.3, toward.z);
        }
    }

    public static boolean onBlockHit(ServerLevel level, BlockPos pos, Direction face, Vec3 impact, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        int rank = SpellMasteryData.getRank(player, SpellType.ACCIO);
        int pulled = 0;
        if (AccioRules.pullsContainers(rank) && pullFromContainer(level, pos, face, player)) {
            pulled++;
        }
        double radius = AccioRules.itemRadius(rank);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(impact, impact).inflate(radius),
                e -> e.isAlive() && !e.getItem().isEmpty() && e.distanceToSqr(impact) <= radius * radius)) {
            if (pulled >= AccioRules.MAX_ITEMS) {
                break;
            }
            track(item, player);
            pulled++;
        }
        if (pulled > 0) {
            level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        return pulled > 0;
    }

    private static boolean pullFromContainer(ServerLevel level, BlockPos pos, Direction face, ServerPlayer player) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container) || !mayOpen(level, pos, face, player, blockEntity)) {
            return false;
        }
        if (blockEntity instanceof RandomizableContainer loot) {
            loot.unpackLootTable(player); // an unlooted chest rolls its loot for the caster, as opening would
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack taken = container.removeItem(slot, stack.getCount());
            container.setChanged();
            Vec3 at = Vec3.atCenterOf(pos.relative(face));
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, taken);
            item.setPickUpDelay(10);
            item.setTarget(player.getUUID());
            level.addFreshEntity(item);
            track(item, player);
            return true;
        }
        return false;
    }

    /**
     * Could the caster open this container by hand? Spawn protection, the container's own lock, a
     * blocked chest lid, and then a RightClickBlock posted as the caster: claim mods veto there, and
     * the Gringotts alarm, the Living Statues and the merfolk hoards react as if it were opened.
     */
    private static boolean mayOpen(ServerLevel level, BlockPos pos, Direction face, ServerPlayer player, BlockEntity blockEntity) {
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (blockEntity instanceof BaseContainerBlockEntity base && !base.canOpen(player)) {
            return false; // locked: vanilla tells the player
        }
        if (level.getBlockState(pos).getBlock() instanceof ChestBlock && ChestBlock.isChestBlockedAt(level, pos)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        return !MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit));
    }

    private static void pullMainHand(ServerPlayer victim, ServerPlayer thief) {
        ItemStack held = victim.getMainHandItem();
        if (held.isEmpty() || held.is(ExampleMod.ELDER_WAND.get())) {
            return;
        }
        victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ServerLevel level = victim.serverLevel();
        ItemEntity item = new ItemEntity(level, victim.getX(), victim.getEyeY() - 0.3, victim.getZ(), held.copy());
        item.setPickUpDelay(10);
        item.setTarget(thief.getUUID());
        level.addFreshEntity(item);
        track(item, thief);
    }

    private static boolean isPvp(ServerPlayer thief, ServerPlayer victim) {
        MinecraftServer server = thief.getServer();
        return server != null && server.isPvpAllowed() && thief.canHarmPlayer(victim);
    }

    private static void track(ItemEntity item, ServerPlayer caster) {
        ITEM_PULLS.put(item.getUUID(), new Pull(caster.getUUID(), item.level().dimension(),
                item.level().getGameTime() + AccioRules.ITEM_PULL_TICKS));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || ITEM_PULLS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Pull>> it = ITEM_PULLS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pull> entry = it.next();
            Pull pull = entry.getValue();
            if (pull.dimension() != level.dimension()) {
                continue;
            }
            Entity raw = level.getEntity(entry.getKey());
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(pull.casterId());
            if (!(raw instanceof ItemEntity item) || !item.isAlive() || caster == null || !caster.isAlive()
                    || caster.level() != level || now > pull.until()) {
                it.remove();
                continue;
            }
            Vec3 toCaster = caster.getEyePosition().subtract(0.0, 0.4, 0.0).subtract(item.position());
            double distance = toCaster.length();
            if (distance < AccioRules.ARRIVE_DISTANCE) {
                it.remove(); // close enough: vanilla pickup takes it from here
                continue;
            }
            item.setDeltaMovement(toCaster.scale(AccioRules.itemPullSpeed(distance) / distance));
            item.hasImpulse = true; // send the new motion now, not on the item's slow update interval
            if (now % 3 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, item.getX(), item.getY() + 0.2, item.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ITEM_PULLS.clear();
    }
}
```

- [ ] **Step 2: Wire the living hit.** In `SpellType.java`, replace the whole case:
```java
            case ACCIO -> {
                // Continuously reel the target in toward the caster until it reaches them.
                if (caster instanceof net.minecraft.server.level.ServerPlayer sp) {
                    com.hogwartsmagic.event.ModEvents.startAccioPull(sp, target);
                } else {
                    net.minecraft.world.phys.Vec3 toward = target.position().vectorTo(
                            caster != null ? caster.getEyePosition(1) : target.position().add(0, 0, 5))
                            .normalize().scale(0.8);
                    target.push(toward.x, 0.3, toward.z);
                }
            }
```
with:
```java
            case ACCIO -> com.hogwartsmagic.spell.support.AccioSpell.onLivingHit(target, caster);
```

- [ ] **Step 3: The block branch.** In `SpellBeamEntity.onHit`, directly above:
```java
                } else if (spell == SpellType.ALOHOMORA) {
```
insert:
```java
                } else if (spell == SpellType.ACCIO) {
                    // Summons loose items near the impact; from Expert, the first stack of a container
                    // the caster could open. (A mandrake is handled by the branch above.)
                    if (this.level() instanceof ServerLevel accioLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.AccioSpell.onBlockHit(
                                accioLevel, pos, blockHit.getDirection(), this.position(), getOwner());
                    }
```

- [ ] **Step 4: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.accio.desc": "Pulls objects and mobs towards the caster.",
```
with:
```json
  "spell.hogwartsmagic.accio.desc": "Summons creatures (up to 10 hearts at Novice, any non-boss at Master) and loose items near where it lands. From Expert it pulls the first stack out of a chest you could open; at Master it snatches an opponent's held item in a duel.",
```
Then directly after `  "message.hogwartsmagic.apparition.splinched": "You've been Splinched!",` insert:
```json
  "message.hogwartsmagic.accio.too_heavy": "Too heavy for your Accio. Not yet.",
```

- [ ] **Step 5: Build, test and boot a dedicated server** (new tick subscriber, posted interaction event)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `AccioSpell` or any `client` class.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/AccioSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let Accio summon items, empty containers you could open and snatch duel items at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Ascendio (self-launch, double cast, surge)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/AscendioSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (cooldown bypass, self-cast branch)
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` (`SELF_OR_CHANNEL`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `AscendioRules` (Task 1); `DamageSupport.isSplashTarget` (Phase 4); `SpellMasteryData.getRank` (Phase 1)
- Produces: `AscendioSpell.wantsSelfCast(ServerPlayer)` → `boolean`, `AscendioSpell.canDoubleCast(ServerPlayer)` → `boolean`, `AscendioSpell.launchSelf(ServerPlayer player, long now)` → `boolean` (true if it was the midair double cast)

- [ ] **Step 1: Create `AscendioSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ascendio cast on yourself (aim down, or a crouching keybind cast): launches you to the rank height,
 * keeping your forward momentum, with no fall damage for 5 seconds. Expert: one midair second cast per
 * jump at half the height, which ignores the cooldown. Master: cast in water, you surge to the surface;
 * on take-off, enemies within 2 blocks are shoved away. A cast at a target still tosses it up
 * (SpellType, unchanged). State lives in the player's persistent data. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AscendioSpell {
    public static final String TAG_SAFE_UNTIL = ExampleMod.MODID + ":ascendio_safe_until";
    public static final String TAG_AIR_CHARGE = ExampleMod.MODID + ":ascendio_air_charge";
    public static final String TAG_LAUNCHED_AT = ExampleMod.MODID + ":ascendio_launched_at";
    public static final String TAG_SURGE_UNTIL = ExampleMod.MODID + ":ascendio_surge_until";

    private AscendioSpell() {
    }

    public static boolean wantsSelfCast(ServerPlayer player) {
        return AscendioRules.wantsSelfCast(player.getXRot(), player.isShiftKeyDown());
    }

    /** Expert and up, airborne, with this jump's charge unspent, and casting on yourself. */
    public static boolean canDoubleCast(ServerPlayer player) {
        return !player.onGround()
                && player.getPersistentData().getBoolean(TAG_AIR_CHARGE)
                && AscendioRules.doubleCasts(SpellMasteryData.getRank(player, SpellType.ASCENDIO))
                && wantsSelfCast(player);
    }

    /** @return true if this was the midair double cast (the caller then leaves the cooldown alone). */
    public static boolean launchSelf(ServerPlayer player, long now) {
        int rank = SpellMasteryData.getRank(player, SpellType.ASCENDIO);
        CompoundTag data = player.getPersistentData();
        ServerLevel level = player.serverLevel();
        if (AscendioRules.surges(rank) && player.isInWater()) {
            // Triwizard Task 2: straight up and out. The tick hook keeps pushing until the surface.
            data.putLong(TAG_SURGE_UNTIL, now + AscendioRules.SURGE_MAX_TICKS);
            data.putLong(TAG_SAFE_UNTIL, now + AscendioRules.SURGE_MAX_TICKS + AscendioRules.SAFE_FALL_TICKS);
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, AscendioRules.SURGE_SPEED, motion.z);
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY(), player.getZ(), 30, 0.3, 0.5, 0.3, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.0f, 1.0f);
            return false;
        }
        boolean doubleCast = !player.onGround() && data.getBoolean(TAG_AIR_CHARGE) && AscendioRules.doubleCasts(rank);
        double blocks = AscendioRules.heightBlocks(rank) * (doubleCast ? AscendioRules.DOUBLE_CAST_HEIGHT_FACTOR : 1.0);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, AscendioRules.launchVelocity(blocks), motion.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        data.putLong(TAG_SAFE_UNTIL, now + AscendioRules.SAFE_FALL_TICKS);
        data.putLong(TAG_LAUNCHED_AT, now);
        if (doubleCast) {
            data.remove(TAG_AIR_CHARGE);
        } else if (AscendioRules.doubleCasts(rank)) {
            data.putBoolean(TAG_AIR_CHARGE, true);
        }
        if (!doubleCast && AscendioRules.pushesOnTakeoff(rank)) {
            shoveAway(level, player);
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.4, 0.1, 0.4, 0.05);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, 1.2f);
        return doubleCast;
    }

    private static void shoveAway(ServerLevel level, ServerPlayer player) {
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(AscendioRules.PUSH_RADIUS),
                e -> DamageSupport.isSplashTarget(player, e, null))) {
            Vec3 away = new Vec3(other.getX() - player.getX(), 0.0, other.getZ() - player.getZ());
            away = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
            other.push(away.x * AscendioRules.PUSH_STRENGTH, AscendioRules.PUSH_LIFT, away.z * AscendioRules.PUSH_STRENGTH);
            other.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (data.contains(TAG_SURGE_UNTIL)) {
            if (now < data.getLong(TAG_SURGE_UNTIL) && player.isInWater()) {
                Vec3 motion = player.getDeltaMovement();
                if (motion.y < AscendioRules.SURGE_SPEED) {
                    player.setDeltaMovement(motion.x, AscendioRules.SURGE_SPEED, motion.z);
                    player.hurtMarked = true;
                }
            } else {
                data.remove(TAG_SURGE_UNTIL);
            }
        }
        if (data.getBoolean(TAG_AIR_CHARGE) && player.onGround()
                && now - data.getLong(TAG_LAUNCHED_AT) > AscendioRules.AIR_CHARGE_GRACE_TICKS) {
            data.remove(TAG_AIR_CHARGE); // landed: the next jump earns a fresh charge
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().getGameTime() < player.getPersistentData().getLong(TAG_SAFE_UNTIL)) {
            event.setCanceled(true);
        }
    }
}
```

- [ ] **Step 2: The midair double cast skips the cooldown.** In `SpellPackets.CastSpellPacket.handle`, replace:
```java
            if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

            // Reparo cast on yourself
```
with:
```java
            if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)
                    && !(spell == SpellType.ASCENDIO && com.hogwartsmagic.spell.support.AscendioSpell.canDoubleCast(player))) return;

            // Reparo cast on yourself
```

- [ ] **Step 3: The self-cast branch.** In the same method, directly above:
```java
            if (spell == SpellType.TENEBRIS_FLIGHT) {
                // Tenebris Flight is self-cast only for players
```
insert:
```java
            if (spell == SpellType.ASCENDIO && com.hogwartsmagic.spell.support.AscendioSpell.wantsSelfCast(player)) {
                // Aimed down or crouch-cast: Ascendio launches you instead of firing a beam.
                boolean doubleCast = com.hogwartsmagic.spell.support.AscendioSpell.launchSelf(player, now);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
                if (!creative && !doubleCast) {
                    SpellCosts.startCooldown(player, wand, spell, now);
                }
                return;
            }

```

- [ ] **Step 4: Ascendio earns cast XP (self-launches never hit anything).** In `MasteryAwards.java`, replace:
```java
            SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO, SpellType.HEAL);
```
with:
```java
            SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO, SpellType.HEAL, SpellType.ASCENDIO);
```

- [ ] **Step 5: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.ascendio.desc": "Violently launches the caster or target high into the air.",
```
with:
```json
  "spell.hogwartsmagic.ascendio.desc": "Aim down (or crouch-cast) to launch yourself 6 blocks up, 15 at Master, with no fall damage for 5 seconds; aim at a creature to toss it. Expert: cast again in midair for a second boost. Master: shoot up through water, and shove nearby enemies away as you take off.",
```

- [ ] **Step 6: Build, test and boot a dedicated server** (new player-tick and fall subscribers)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `AscendioSpell` or any `client` class.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/AscendioSpell.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let Ascendio launch the caster with a midair double cast and a Master water surge" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Depulso (wall slam, area push, deflect, mob into mob)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/DepulsoSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`DEPULSO` case)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (deflect tick, block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `DepulsoRules` (Task 1); `DisableSupport.casterRank/isEnemyOf` (Phase 3); `DamageSupport.scaled/isSplashTarget` (Phase 4); `CombatEffects.responsible` (Phase 2)
- Produces: `DepulsoSpell.apply(LivingEntity target, @Nullable Entity caster)`, `DepulsoSpell.onBlockHit(ServerLevel level, Vec3 impact, @Nullable Entity caster)` → `boolean`, `DepulsoSpell.deflectProjectiles(SpellBeamEntity beam)` → `int`, `DepulsoSpell.DEFLECTED_TAG = "hogwartsmagic:depulso_deflected"`

- [ ] **Step 1: Create `DepulsoSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Depulso, the banishing charm. Its knockback slams targets into walls: a banished target that meets
 * a solid block within 10 ticks takes slam damage once. Expert: enemies within 2 blocks of the impact
 * are banished too (also on a block hit), and projectiles near the beam's path fly back, owned by
 * the caster. Master: a target banished into another mob hurts both. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DepulsoSpell {
    public static final String DEFLECTED_TAG = ExampleMod.MODID + ":depulso_deflected";

    private record Slam(ResourceKey<Level> dimension, @Nullable UUID casterId, double dirX, double dirZ, int rank, long until) {
    }

    private static final Map<UUID, Slam> SLAMS = new HashMap<>();

    private DepulsoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.DEPULSO);
        Entity owner = CombatEffects.responsible(caster);
        Vec3 from = owner != null ? owner.position() : target.position().add(0.0, 0.0, -5.0);
        banish(level, target, from, rank, owner);
        if (DepulsoRules.pushesArea(rank)) {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(DepulsoRules.AREA_RADIUS),
                    e -> DisableSupport.isEnemyOf(owner, e, target))) {
                banish(level, other, from, rank, owner);
            }
        }
    }

    /** Expert and up: a Depulso on a wall or floor still banishes the enemies around it. */
    public static boolean onBlockHit(ServerLevel level, Vec3 impact, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.DEPULSO);
        if (!DepulsoRules.pushesArea(rank)) {
            return false;
        }
        Entity owner = CombatEffects.responsible(caster);
        Vec3 from = owner != null ? owner.position() : impact.add(0.0, 0.0, -1.0);
        List<LivingEntity> banished = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(DepulsoRules.AREA_RADIUS),
                e -> DamageSupport.isSplashTarget(owner, e, null));
        for (LivingEntity target : banished) {
            banish(level, target, from, rank, owner);
        }
        return !banished.isEmpty();
    }

    /** Called every server tick of a Depulso beam. Expert and up: incoming projectiles turn around. */
    public static int deflectProjectiles(SpellBeamEntity beam) {
        if (!(beam.level() instanceof ServerLevel level)) {
            return 0;
        }
        Entity owner = beam.getOwner();
        if (!DepulsoRules.deflectsProjectiles(DisableSupport.casterRank(owner, SpellType.DEPULSO))) {
            return 0;
        }
        int deflected = 0;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                beam.getBoundingBox().inflate(DepulsoRules.DEFLECT_RADIUS),
                p -> p != beam && !(p instanceof SpellBeamEntity) && p.isAlive() && p.getOwner() != owner
                        && !p.getTags().contains(DEFLECTED_TAG) && p.getDeltaMovement().lengthSqr() > 0.01)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0));
            projectile.setOwner(owner);
            projectile.addTag(DEFLECTED_TAG);
            projectile.hasImpulse = true;
            level.sendParticles(ParticleTypes.CLOUD, projectile.getX(), projectile.getY(), projectile.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
            deflected++;
        }
        if (deflected > 0) {
            level.playSound(null, beam.getX(), beam.getY(), beam.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        return deflected;
    }

    private static void banish(ServerLevel level, LivingEntity target, Vec3 from, int rank, @Nullable Entity owner) {
        Vec3 away = new Vec3(target.getX() - from.x, 0.0, target.getZ() - from.z);
        away = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
        double knockback = DepulsoRules.knockback(rank);
        target.push(away.x * knockback, DepulsoRules.VERTICAL_LIFT, away.z * knockback);
        target.hurtMarked = true;
        SLAMS.put(target.getUUID(), new Slam(level.dimension(), owner == null ? null : owner.getUUID(),
                away.x, away.z, rank, level.getGameTime() + DepulsoRules.SLAM_WINDOW_TICKS));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || SLAMS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Slam>> it = SLAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Slam> entry = it.next();
            Slam slam = entry.getValue();
            if (slam.dimension() != level.dimension()) {
                continue;
            }
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isAlive() || now > slam.until()) {
                it.remove();
                continue;
            }
            Entity caster = slam.casterId() == null ? null : level.getEntity(slam.casterId());
            float damage = DamageSupport.scaled(caster, DepulsoRules.slamDamage(slam.rank()));
            if (DepulsoRules.slamsIntoMobs(slam.rank())) {
                List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(DepulsoRules.MOB_CONTACT_INFLATE),
                        e -> e != target && e != caster && DisableSupport.isEnemyOf(caster, e, target));
                if (!struck.isEmpty()) {
                    LivingEntity other = struck.get(0);
                    other.hurt(other.damageSources().flyIntoWall(), damage);
                    slamFeedback(level, target);
                    target.hurt(target.damageSources().flyIntoWall(), damage);
                    it.remove();
                    continue;
                }
            }
            if (target.horizontalCollision || blockedAhead(level, target, slam)) {
                target.hurt(target.damageSources().flyIntoWall(), damage);
                slamFeedback(level, target);
                it.remove();
            }
        }
    }

    private static boolean blockedAhead(ServerLevel level, LivingEntity target, Slam slam) {
        double reach = target.getBbWidth() * 0.5 + DepulsoRules.WALL_PROBE;
        BlockPos pos = BlockPos.containing(target.getX() + slam.dirX() * reach,
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ() + slam.dirZ() * reach);
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void slamFeedback(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                12, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND,
                SoundSource.NEUTRAL, 0.35f, 1.4f);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SLAMS.clear();
    }
}
```

- [ ] **Step 2: Wire the living hit.** In `SpellType.java`, replace:
```java
            case DEPULSO -> {
                net.minecraft.world.phys.Vec3 away = target.position().vectorTo(
                        caster != null ? caster.position() : target.position().add(0, 0, -5))
                        .normalize().scale(-1.5); // Negative scale for push away, strong force
                target.push(away.x, 0.5, away.z);
            }
```
with:
```java
            case DEPULSO -> com.hogwartsmagic.spell.support.DepulsoSpell.apply(target, caster);
```

- [ ] **Step 3: Deflect along the beam's path, and a block-hit branch.** In `SpellBeamEntity.tick`, directly above:
```java
            if (getSpellType() == SpellType.AQUA_ERUCTO && hasExceededAquaRange()) {
```
insert:
```java
            if (getSpellType() == SpellType.DEPULSO) {
                com.hogwartsmagic.spell.support.DepulsoSpell.deflectProjectiles(this);
            }
```
In `SpellBeamEntity.onHit`, directly above:
```java
                } else if (spell == SpellType.REPARO) {
```
insert:
```java
                } else if (spell == SpellType.DEPULSO) {
                    // Expert and up: the banishing blast still throws back enemies around the impact.
                    if (this.level() instanceof ServerLevel depulsoLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.DepulsoSpell.onBlockHit(
                                depulsoLevel, this.position(), getOwner());
                    }
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.depulso.desc": "Banishes the target backwards with massive force.",
```
with:
```json
  "spell.hogwartsmagic.depulso.desc": "Banishes the target backwards; slam it into a wall for extra damage (3, up to 8 at Master). Expert: also blasts back enemies around the impact and turns incoming arrows and fireballs around. Master: a target thrown into another mob hurts both.",
```

- [ ] **Step 5: Build, test and boot a dedicated server** (new tick subscriber, beam tick hook)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `DepulsoSpell` or any `client` class.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/DepulsoSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Give Depulso wall slams, a crowd push, projectile deflection and mob-into-mob slams" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Apparition (range, channel and splinching by rank)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/Config.java` (splinch toggle and chance)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`startApparitionChannel`, `tickApparitionChannel`, `completeApparition`, constants)
- Modify: `src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java` (whole file: the map scales to fit)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ApparitionRules.range/channelTicks/splinchChance/mapStep/tileCenterOffset/RANGE_SLACK` (Task 1); `SpellMasteryData.getRank` (Phase 1)
- Produces: `Config.isApparitionSplinching()` → `boolean`, `Config.getApparitionSplinchChance()` → `double`; `ApparitionMapScreen(BlockPos origin, int radius, int durationTicks)` (unchanged signature; Task 7 adds a fourth parameter)

- [ ] **Step 1: Config.** In `Config.java`, directly after:
```java
            .defineInRange("pvpCrowdControlMultiplier", 0.6, 0.0, 1.0);
```
add:
```java

    public static final ForgeConfigSpec.BooleanValue APPARITION_SPLINCHING = BUILDER
            .comment("Apparition: if false, nobody is ever Splinched.")
            .define("apparitionSplinching", true);

    public static final ForgeConfigSpec.DoubleValue APPARITION_SPLINCH_CHANCE = BUILDER
            .comment("Apparition: Splinch chance (0-1) at Novice. Adept takes half of it, Expert a fifth, Master never splinches.")
            .defineInRange("apparitionSplinchChance", 0.10, 0.0, 1.0);
```
After `    public static double pvpCrowdControlMultiplier = 0.6;` add:
```java
    public static boolean apparitionSplinching = true;
    public static double apparitionSplinchChance = 0.10;
```
In `onLoad`, after `        pvpCrowdControlMultiplier = PVP_CROWD_CONTROL_MULTIPLIER.get();` add:
```java
        apparitionSplinching = APPARITION_SPLINCHING.get();
        apparitionSplinchChance = APPARITION_SPLINCH_CHANCE.get();
```
At the end of the class, replace:
```java
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }
}
```
with:
```java
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }

    public static boolean isApparitionSplinching() {
        return apparitionSplinching;
    }

    public static double getApparitionSplinchChance() {
        return apparitionSplinchChance;
    }
}
```

- [ ] **Step 2: Rank range and channel in `ModEvents`.** Delete the two constant declarations (find them by name):
```java
    private static final int APPARITION_CHANNEL_TICKS = 80;          // 4 seconds of "Determination/Deliberation"
```
```java
    private static final double APPARITION_MAX_RANGE = 48.0D;        // matches the minimap radius (blocks)
```
In `startApparitionChannel`, replace:
```java
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(), (int) APPARITION_MAX_RANGE, APPARITION_CHANNEL_TICKS),
                PacketDistributor.PLAYER.with(player));
```
with:
```java
        int apparitionRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION);
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(),
                        com.hogwartsmagic.spell.support.ApparitionRules.range(apparitionRank),
                        com.hogwartsmagic.spell.support.ApparitionRules.channelTicks(apparitionRank)),
                PacketDistributor.PLAYER.with(player));
```
In `tickApparitionChannel`, replace:
```java
        int elapsed = (int) (now - playerData.getLong(TAG_APPARITION_START));
        float progress = Math.min(1.0f, elapsed / (float) APPARITION_CHANNEL_TICKS);
```
with:
```java
        int elapsed = (int) (now - playerData.getLong(TAG_APPARITION_START));
        int channelTicks = com.hogwartsmagic.spell.support.ApparitionRules.channelTicks(
                com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION));
        float progress = Math.min(1.0f, elapsed / (float) channelTicks);
```
and replace:
```java
        if (elapsed >= APPARITION_CHANNEL_TICKS) {
```
with:
```java
        if (elapsed >= channelTicks) {
```

- [ ] **Step 3: Rank range and config splinch in `completeApparition`.** Replace:
```java
        Vec3 from = player.position();
        double dist = Math.hypot((targetX + 0.5) - from.x, (targetZ + 0.5) - from.z);
```
with:
```java
        Vec3 from = player.position();
        int rank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION);
        int range = com.hogwartsmagic.spell.support.ApparitionRules.range(rank);
        double dist = Math.hypot((targetX + 0.5) - from.x, (targetZ + 0.5) - from.z);
```
Replace:
```java
        if (dest == null || dist > APPARITION_MAX_RANGE + 4.0D || warded) {
```
with:
```java
        if (dest == null || dist > range + com.hogwartsmagic.spell.support.ApparitionRules.RANGE_SLACK || warded) {
```
Replace:
```java
        // Splinch risk scales with distance; a botched jump lands you short.
        float risk = 0.04f + 0.30f * (float) Math.min(1.0, dist / APPARITION_MAX_RANGE);
        boolean splinched = !player.isCreative() && player.getRandom().nextFloat() < risk;
```
with:
```java
        // Splinch risk falls with rank and comes from config (10% at Novice by default, and it can be
        // switched off); a botched jump still lands you short.
        double risk = com.hogwartsmagic.spell.support.ApparitionRules.splinchChance(rank,
                Config.getApparitionSplinchChance(), Config.isApparitionSplinching());
        boolean splinched = !player.isCreative() && player.getRandom().nextDouble() < risk;
```
Run: `grep -n "APPARITION_MAX_RANGE\|APPARITION_CHANNEL_TICKS" src/main/java/com/hogwartsmagic/event/ModEvents.java`
Expected: no output.

- [ ] **Step 4: The map scales to fit a Master's 200 blocks.** Replace the whole of `src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java` with:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.support.ApparitionRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Apparition destination picker: a top-down minimap of the loaded terrain around the caster.
 * While this screen is open the player is "channeling": the server runs the rank's timer, shrinks the
 * caster (the imploding effect), and will cancel if they are hit. Clicking a known tile sends the chosen
 * column to the server, which re-validates and performs the authoritative teleport. The map always fits
 * the screen: up close one tile is one block, for a Master's reach one tile stands for several.
 *
 * <p>Pure client rendering only. No {@code switch} on enums; backdrop drawn with
 * {@code renderTransparentBackground} (never {@code renderBackground}) so the world stays crisp.
 */
public class ApparitionMapScreen extends Screen {

    private static final int TILE = 2;              // pixels per map tile
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_BODY = 0xFFE8DBFF;
    private static final int COLOR_UNKNOWN = 0xFF0E0A16;
    private static final int COLOR_PANEL = 0xF0140A1E;

    private final BlockPos origin;
    private final int radius;
    private final int durationTicks;
    private final RandomSource shakeRand = RandomSource.create();

    private int ticksLeft;
    private boolean selectionSent;

    // Precomputed minimap (one sample per tile), built in init().
    private int[] tileColors = new int[0];
    private boolean[] tileKnown = new boolean[0];
    private int span;       // 2*radius + 1 blocks
    private int step = 1;   // blocks per tile
    private int tiles;      // tiles per side

    private int mapLeft;
    private int mapTop;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
    }

    @Override
    protected void init() {
        super.init();
        span = radius * 2 + 1;
        int maxMapPx = Math.max(64, Math.min(this.width - 60, this.height - 100));
        step = ApparitionRules.mapStep(span, TILE, maxMapPx);
        tiles = (span + step - 1) / step;
        int mapSize = tiles * TILE;
        panelWidth = mapSize + 28;
        panelHeight = mapSize + 56;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        mapLeft = panelLeft + 14;
        mapTop = panelTop + 40;
        buildMap();
    }

    private int worldX(int tileX) {
        return origin.getX() - radius + ApparitionRules.tileCenterOffset(tileX, step, span);
    }

    private int worldZ(int tileZ) {
        return origin.getZ() - radius + ApparitionRules.tileCenterOffset(tileZ, step, span);
    }

    /** Sample the surface block color of every tile's middle column we can see, once, so render() stays cheap. */
    private void buildMap() {
        tileColors = new int[tiles * tiles];
        tileKnown = new boolean[tiles * tiles];
        Level level = this.minecraft != null ? this.minecraft.level : null;
        if (level == null) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int tz = 0; tz < tiles; tz++) {
            for (int tx = 0; tx < tiles; tx++) {
                int idx = tz * tiles + tx;
                int wx = worldX(tx);
                int wz = worldZ(tz);
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    tileColors[idx] = COLOR_UNKNOWN;
                    tileKnown[idx] = false;
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                pos.set(wx, surfaceY - 1, wz);
                MapColor mapColor = level.getBlockState(pos).getMapColor(level, pos);
                if (mapColor == MapColor.NONE) {
                    tileColors[idx] = 0xFF182030;
                } else {
                    tileColors[idx] = 0xFF000000 | mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
                }
                tileKnown[idx] = true;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ticksLeft > 0) {
            ticksLeft--;
        }
        if (ticksLeft <= 0 && !selectionSent) {
            onClose(); // ran out of time — cancel
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dimmed backdrop only — no blur shader (see WandRadialMenuScreen / project convention).
        this.renderTransparentBackground(g);

        // Screen shake: grows as the focus tightens and time runs out.
        float progress = 1.0f - (ticksLeft / (float) durationTicks);
        float intensity = progress * progress * 7.0f;
        float shakeX = (shakeRand.nextFloat() * 2.0f - 1.0f) * intensity;
        float shakeY = (shakeRand.nextFloat() * 2.0f - 1.0f) * intensity;

        g.pose().pushPose();
        g.pose().translate(shakeX, shakeY, 0.0f);

        int mapSize = tiles * TILE;

        // Panel + border.
        g.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COLOR_PANEL);
        drawBorder(g, panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COLOR_GOLD);

        // Title + hint.
        g.drawCenteredString(this.font, this.title, panelLeft + panelWidth / 2, panelTop + 8, COLOR_GOLD);
        g.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.hint"),
                panelLeft + panelWidth / 2, panelTop + 22, COLOR_BODY);

        // Minimap tiles (clipped to the map area).
        g.enableScissor(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize);
        for (int tz = 0; tz < tiles; tz++) {
            for (int tx = 0; tx < tiles; tx++) {
                int idx = tz * tiles + tx;
                int px = mapLeft + tx * TILE;
                int py = mapTop + tz * TILE;
                g.fill(px, py, px + TILE, py + TILE, tileColors[idx]);
            }
        }

        // Caster marker at the center.
        int centerTile = radius / step;
        int centerPx = mapLeft + centerTile * TILE;
        int centerPy = mapTop + centerTile * TILE;
        g.fill(centerPx - 1, centerPy - 1, centerPx + TILE + 1, centerPy + TILE + 1, 0xFFFFFFFF);
        g.fill(centerPx, centerPy, centerPx + TILE, centerPy + TILE, 0xFF20C0FF);

        // Hover highlight over a selectable tile.
        int hoverDx = Math.floorDiv(mouseX - mapLeft, TILE);
        int hoverDz = Math.floorDiv(mouseY - mapTop, TILE);
        boolean hoverValid = hoverDx >= 0 && hoverDx < tiles && hoverDz >= 0 && hoverDz < tiles
                && tileKnown[hoverDz * tiles + hoverDx];
        if (hoverValid) {
            int hx = mapLeft + hoverDx * TILE;
            int hy = mapTop + hoverDz * TILE;
            g.fill(hx - 1, hy - 1, hx + TILE + 1, hy + TILE + 1, 0x80FFE08A);
        }
        g.disableScissor();
        drawBorder(g, mapLeft - 1, mapTop - 1, mapLeft + mapSize + 1, mapTop + mapSize + 1, 0x66D4AF37);

        // Countdown bar.
        int barLeft = mapLeft;
        int barTop = mapTop + mapSize + 6;
        int barWidth = mapSize;
        g.fill(barLeft, barTop, barLeft + barWidth, barTop + 4, 0xFF2A2238);
        int filled = Math.round(barWidth * (ticksLeft / (float) durationTicks));
        int barColor = ticksLeft <= durationTicks / 3 ? 0xFFFF5A4B : COLOR_GOLD;
        g.fill(barLeft, barTop, barLeft + filled, barTop + 4, barColor);

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !selectionSent) {
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
            int dz = Math.floorDiv((int) mouseY - mapTop, TILE);
            if (dx >= 0 && dx < tiles && dz >= 0 && dz < tiles && tileKnown[dz * tiles + dx]) {
                SpellPackets.CHANNEL.send(
                        new SpellPackets.ChooseApparitionDestinationPacket(worldX(dx), worldZ(dz)),
                        PacketDistributor.SERVER.noArg());
                selectionSent = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Called from the network bridge when the server ends the channel; suppresses the cancel packet. */
    public void closeFromServer() {
        selectionSent = true;
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        if (!selectionSent) {
            // Closed without choosing (Esc or timeout): tell the server to cancel + start the cooldown.
            SpellPackets.CHANNEL.send(new SpellPackets.CancelApparitionPacket(), PacketDistributor.SERVER.noArg());
            selectionSent = true;
        }
        super.onClose();
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
(`com.hogwartsmagic.spell.support.ApparitionRules` is pure common code, safe to call from the client.)

- [ ] **Step 5: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.apparition.desc": "Channel for four seconds and pick a known spot on a nearby map, then Disapparate with a crack and reappear there. Anyone within a block comes with you. Botch the focus — long range, or a hit while channeling — and you risk being Splinched. Cannot be cast inside an anti-Apparition ward.",
```
with:
```json
  "spell.hogwartsmagic.apparition.desc": "Pick a spot on the map while you focus, then Disapparate with a crack and reappear there; anyone within a block comes with you. Range 48 blocks and 4 seconds of focus at Novice, 200 blocks and 1 second at Master. Novices risk being Splinched; Masters never are, and can Apparate to any Floo fireplace they have used in this dimension. A hit breaks your focus. Cannot be cast inside an anti-Apparition ward.",
```

- [ ] **Step 6: Build, test and boot a dedicated server** (config values, client screen must not load server-side)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; the generated common config contains `apparitionSplinching` and `apparitionSplinchChance`; no stack trace mentioning `Apparition` or any `client` class.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Scale Apparition range and focus by rank and make splinching a config value" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Apparition Master (known Floo fireplaces)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/ApparitionSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`OpenApparitionMapPacket` gains fireplaces; new `ChooseApparitionFireplacePacket`; `openFlooSelection` remembers)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`startApparitionChannel`, new `completeApparitionToFireplace`, Floo arrival remembers)
- Modify: `src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java` (fireplace list)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` (`openApparitionMap`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ApparitionRules.jumpsToFireplaces/MAX_KNOWN_FIREPLACES/MAX_LISTED_FIREPLACES` (Task 1); `PlayerMagicData.getModData/saveModData`, `SpellPackets.FlooDestinationData(BlockPos, String)`, `FlooFireplaceBlockEntity.getFireplaceName()` (existing)
- Produces:
  - `ApparitionSpell.rememberFireplace(ServerPlayer player, ServerLevel level, BlockPos pos, String name)`, `ApparitionSpell.isKnown(ServerPlayer, ServerLevel, BlockPos)` → `boolean`, `ApparitionSpell.forgetFireplace(ServerPlayer, ServerLevel, BlockPos)`, `ApparitionSpell.knownFireplaces(ServerPlayer, ServerLevel)` → `List<SpellPackets.FlooDestinationData>` (same dimension, nearest first, at most 8)
  - `SpellPackets.OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks, List<FlooDestinationData> fireplaces)`
  - `SpellPackets.ChooseApparitionFireplacePacket(BlockPos fireplacePos)` (C2S)
  - `ModEvents.completeApparitionToFireplace(ServerPlayer player, BlockPos fireplacePos)`
  - `ApparitionMapScreen(BlockPos origin, int radius, int durationTicks, List<SpellPackets.FlooDestinationData> fireplaces)`

- [ ] **Step 1: Create `ApparitionSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Floo fireplaces a player knows: the source and destination of every completed Floo trip and
 * every fireplace they open the Floo menu at. Kept in PlayerPersisted (survives death), at most 32,
 * oldest dropped first. A Master Apparates to any of them in the same dimension. Server only.
 */
public final class ApparitionSpell {
    private static final String KEY = "KnownFireplaces";

    private ApparitionSpell() {
    }

    public static void rememberFireplace(ServerPlayer player, ServerLevel level, BlockPos pos, String name) {
        if (name == null || name.isBlank()) {
            return; // an unnamed fireplace isn't on the network yet
        }
        CompoundTag modData = PlayerMagicData.getModData(player);
        ListTag list = modData.getList(KEY, Tag.TAG_COMPOUND);
        removeEntry(list, level, pos);
        CompoundTag entry = new CompoundTag();
        entry.putString("Dim", level.dimension().location().toString());
        entry.putLong("Pos", pos.asLong());
        entry.putString("Name", name);
        list.add(entry);
        while (list.size() > ApparitionRules.MAX_KNOWN_FIREPLACES) {
            list.remove(0);
        }
        modData.put(KEY, list);
        PlayerMagicData.saveModData(player, modData);
    }

    public static boolean isKnown(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ListTag list = PlayerMagicData.getModData(player).getList(KEY, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && entry.getLong("Pos") == pos.asLong()) {
                return true;
            }
        }
        return false;
    }

    public static void forgetFireplace(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        ListTag list = modData.getList(KEY, Tag.TAG_COMPOUND);
        removeEntry(list, level, pos);
        modData.put(KEY, list);
        PlayerMagicData.saveModData(player, modData);
    }

    /** Known fireplaces in the player's dimension, nearest first, at most {@link ApparitionRules#MAX_LISTED_FIREPLACES}. */
    public static List<SpellPackets.FlooDestinationData> knownFireplaces(ServerPlayer player, ServerLevel level) {
        ListTag list = PlayerMagicData.getModData(player).getList(KEY, Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        List<SpellPackets.FlooDestinationData> found = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim"))) {
                found.add(new SpellPackets.FlooDestinationData(BlockPos.of(entry.getLong("Pos")), entry.getString("Name")));
            }
        }
        BlockPos here = player.blockPosition();
        found.sort(Comparator.comparingDouble(d -> d.pos().distSqr(here)));
        return found.size() > ApparitionRules.MAX_LISTED_FIREPLACES
                ? new ArrayList<>(found.subList(0, ApparitionRules.MAX_LISTED_FIREPLACES))
                : found;
    }

    private static void removeEntry(ListTag list, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag entry = list.getCompound(i);
            if (dim.equals(entry.getString("Dim")) && entry.getLong("Pos") == pos.asLong()) {
                list.remove(i);
            }
        }
    }
}
```

- [ ] **Step 2: Packets.** In `SpellPackets.java`, replace the map packet registration:
```java
        CHANNEL.messageBuilder(OpenApparitionMapPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.origin);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.durationTicks);
                })
                .decoder(buf -> new OpenApparitionMapPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()))
                .consumerMainThread(OpenApparitionMapPacket::handle)
                .add();
```
with:
```java
        CHANNEL.messageBuilder(OpenApparitionMapPacket.class)
                .encoder((p, buf) -> {
                    buf.writeBlockPos(p.origin);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.durationTicks);
                    buf.writeVarInt(p.fireplaces.size());
                    for (FlooDestinationData fireplace : p.fireplaces) {
                        buf.writeBlockPos(fireplace.pos());
                        buf.writeUtf(fireplace.name());
                    }
                })
                .decoder(buf -> {
                    BlockPos origin = buf.readBlockPos();
                    int radius = buf.readVarInt();
                    int durationTicks = buf.readVarInt();
                    int size = clampDecodedSize(buf.readVarInt(),
                            com.hogwartsmagic.spell.support.ApparitionRules.MAX_LISTED_FIREPLACES);
                    List<FlooDestinationData> fireplaces = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        fireplaces.add(new FlooDestinationData(buf.readBlockPos(), buf.readUtf()));
                    }
                    return new OpenApparitionMapPacket(origin, radius, durationTicks, fireplaces);
                })
                .consumerMainThread(OpenApparitionMapPacket::handle)
                .add();
        CHANNEL.messageBuilder(ChooseApparitionFireplacePacket.class)
                .encoder((p, buf) -> buf.writeBlockPos(p.fireplacePos))
                .decoder(buf -> new ChooseApparitionFireplacePacket(buf.readBlockPos()))
                .consumerMainThread(ChooseApparitionFireplacePacket::handle)
                .add();
```
Replace the record header:
```java
    public record OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks) {
```
with:
```java
    public record OpenApparitionMapPacket(BlockPos origin, int radius, int durationTicks, List<FlooDestinationData> fireplaces) {
```
Directly above:
```java
    /** Client → server: the player closed the map without choosing — cancel the channel now (full cooldown). */
```
add:
```java
    /** Client to server: a Master picked a known fireplace on the map. Re-validated against the server's own list. */
    public record ChooseApparitionFireplacePacket(BlockPos fireplacePos) {
        public static void handle(ChooseApparitionFireplacePacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                    com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                    com.hogwartsmagic.event.ModEvents.cancelApparitionChannel(player, player.getMainHandItem());
                    return;
                }
                com.hogwartsmagic.event.ModEvents.completeApparitionToFireplace(player, p.fireplacePos);
            }
        }
    }

```
In `openFlooSelection`, replace:
```java
        if (fireplace == null || !fireplace.isNamed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.needs_name"), true);
            return false;
        }
```
with:
```java
        if (fireplace == null || !fireplace.isNamed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.floo.needs_name"), true);
            return false;
        }
        if (player.level() instanceof net.minecraft.server.level.ServerLevel flooLevel) {
            // A fireplace you have stood at is one a Master can Apparate back to.
            com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, flooLevel,
                    fireplace.getBlockPos(), fireplace.getFireplaceName());
        }
```

- [ ] **Step 3: Server side in `ModEvents`.** In `startApparitionChannel`, replace (Task 6's version):
```java
        int apparitionRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION);
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(),
                        com.hogwartsmagic.spell.support.ApparitionRules.range(apparitionRank),
                        com.hogwartsmagic.spell.support.ApparitionRules.channelTicks(apparitionRank)),
                PacketDistributor.PLAYER.with(player));
```
with:
```java
        int apparitionRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION);
        java.util.List<SpellPackets.FlooDestinationData> fireplaces =
                com.hogwartsmagic.spell.support.ApparitionRules.jumpsToFireplaces(apparitionRank)
                        && player.level() instanceof ServerLevel apparitionLevel
                        ? com.hogwartsmagic.spell.support.ApparitionSpell.knownFireplaces(player, apparitionLevel)
                        : java.util.List.of();
        SpellPackets.CHANNEL.send(
                new SpellPackets.OpenApparitionMapPacket(player.blockPosition(),
                        com.hogwartsmagic.spell.support.ApparitionRules.range(apparitionRank),
                        com.hogwartsmagic.spell.support.ApparitionRules.channelTicks(apparitionRank),
                        fireplaces),
                PacketDistributor.PLAYER.with(player));
```
Directly above:
```java
    /** Cancel for any reason (timeout, hit while channeling, lost wand, closed map): clears state and applies the full cooldown. */
```
add:
```java
    /**
     * Master Apparition: straight to a known Floo fireplace in this dimension, at any distance. Lands on
     * the block above it, like Floo. May load the destination chunk once (no range limit on a Master's
     * jump, on a one-minute cooldown). A fireplace that is gone is forgotten.
     */
    public static void completeApparitionToFireplace(ServerPlayer player, BlockPos fireplacePos) {
        net.minecraft.nbt.CompoundTag playerData = player.getPersistentData();
        if (!playerData.getBoolean(TAG_APPARITION_CHANNELING)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!(player.level() instanceof ServerLevel level)
                || !com.hogwartsmagic.spell.support.ApparitionRules.jumpsToFireplaces(
                        com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.APPARITION))
                || !com.hogwartsmagic.spell.support.ApparitionSpell.isKnown(player, level, fireplacePos)) {
            cancelApparitionChannel(player, wand);
            return;
        }
        level.getChunkAt(fireplacePos);
        if (!(level.getBlockEntity(fireplacePos) instanceof com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity)) {
            com.hogwartsmagic.spell.support.ApparitionSpell.forgetFireplace(player, level, fireplacePos);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.fireplace_gone").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            cancelApparitionChannel(player, wand);
            return;
        }
        BlockPos exit = fireplacePos.above();
        if (com.hogwartsmagic.caveinimicum.CaveInimicumManager.get(level).isWardedAt(exit)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hogwartsmagic.apparition.blocked").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            cancelApparitionChannel(player, wand);
            return;
        }

        playerData.remove(TAG_APPARITION_CHANNELING);
        playerData.remove(TAG_APPARITION_START);
        clearImplodeScale(player);
        SpellPackets.CHANNEL.send(new SpellPackets.CloseApparitionMapPacket(), PacketDistributor.PLAYER.with(player));

        // Side-along: anyone within a block comes with you. Gather BEFORE moving.
        java.util.List<LivingEntity> passengers = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(APPARITION_SIDEALONG_RADIUS), e -> e != player && e.isAlive());
        Vec3 from = player.position();
        double x = exit.getX() + 0.5;
        double y = exit.getY();
        double z = exit.getZ() + 0.5;
        crackEffect(level, from.x, from.y, from.z, true);
        player.teleportTo(x, y, z);
        player.fallDistance = 0.0f;
        crackEffect(level, x, y, z, false);
        for (LivingEntity passenger : passengers) {
            passenger.teleportTo(x + (player.getRandom().nextDouble() - 0.5) * 1.5,
                    y, z + (player.getRandom().nextDouble() - 0.5) * 1.5);
            passenger.fallDistance = 0.0f;
        }
        revealInvisibilityCloak(player);
        applyApparitionCooldown(player, wand, true);
    }

```
In `tickPendingFlooTravel`, replace:
```java
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                com.hogwartsmagic.ModSounds.FLOO_ARRIVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        destination.ignite(serverLevel);
```
with:
```java
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                com.hogwartsmagic.ModSounds.FLOO_ARRIVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        destination.ignite(serverLevel);
        // Both ends of a completed trip are fireplaces a Master can Apparate to.
        com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, serverLevel, sourcePos, source.getFireplaceName());
        com.hogwartsmagic.spell.support.ApparitionSpell.rememberFireplace(player, serverLevel, destinationPos, destination.getFireplaceName());
```

- [ ] **Step 4: The fireplace list on the map.** In `ApparitionMapScreen.java` (Task 6's file):

Replace:
```java
    private final int durationTicks;
    private final RandomSource shakeRand = RandomSource.create();
```
with:
```java
    private final int durationTicks;
    private final java.util.List<SpellPackets.FlooDestinationData> fireplaces;
    private final RandomSource shakeRand = RandomSource.create();
    private static final int ROW = 11;
```
Replace:
```java
    private int panelHeight;
```
with:
```java
    private int panelHeight;
    private int listTop;
```
Replace the constructor:
```java
    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
    }
```
with:
```java
    public ApparitionMapScreen(BlockPos origin, int radius, int durationTicks,
                               java.util.List<SpellPackets.FlooDestinationData> fireplaces) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".apparition.title"));
        this.origin = origin;
        this.radius = radius;
        this.durationTicks = Math.max(1, durationTicks);
        this.ticksLeft = this.durationTicks;
        this.fireplaces = fireplaces == null ? java.util.List.of() : java.util.List.copyOf(fireplaces);
    }

    /** Header plus one row per known fireplace; 0 when there are none (below Master). */
    private int fireplaceListHeight() {
        return fireplaces.isEmpty() ? 0 : 12 + ROW * fireplaces.size();
    }

    /** Index of the fireplace row under the mouse, or -1. */
    private int fireplaceRowAt(double mouseX, double mouseY) {
        if (fireplaces.isEmpty()) {
            return -1;
        }
        int mapSize = tiles * TILE;
        if (mouseX < mapLeft || mouseX >= mapLeft + mapSize) {
            return -1;
        }
        int row = Math.floorDiv((int) mouseY - (listTop + 12), ROW);
        return row >= 0 && row < fireplaces.size() ? row : -1;
    }
```
In `init()`, replace:
```java
        panelHeight = mapSize + 56;
```
with:
```java
        panelHeight = mapSize + 56 + fireplaceListHeight();
```
and replace:
```java
        mapTop = panelTop + 40;
        buildMap();
```
with:
```java
        mapTop = panelTop + 40;
        listTop = mapTop + mapSize + 16;
        buildMap();
```
In `render`, directly above the last `        g.pose().popPose();`, insert:
```java
        // Known Floo fireplaces (Master): click one to Apparate there at any distance.
        if (!fireplaces.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".apparition.fireplaces"),
                    mapLeft, listTop, COLOR_GOLD);
            int hovered = fireplaceRowAt(mouseX, mouseY);
            for (int i = 0; i < fireplaces.size(); i++) {
                SpellPackets.FlooDestinationData fireplace = fireplaces.get(i);
                int rowY = listTop + 12 + i * ROW;
                if (i == hovered) {
                    g.fill(mapLeft - 2, rowY - 1, mapLeft + mapSize + 2, rowY + ROW - 1, 0x50FFE08A);
                }
                int distance = (int) Math.sqrt(origin.distSqr(fireplace.pos()));
                String label = this.font.plainSubstrByWidth(fireplace.name() + " (" + distance + "m)", mapSize);
                g.drawString(this.font, label, mapLeft, rowY, COLOR_BODY);
            }
        }

```
In `mouseClicked`, replace:
```java
        if (button == 0 && !selectionSent) {
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
```
with:
```java
        if (button == 0 && !selectionSent) {
            int row = fireplaceRowAt(mouseX, mouseY);
            if (row >= 0) {
                SpellPackets.CHANNEL.send(
                        new SpellPackets.ChooseApparitionFireplacePacket(fireplaces.get(row).pos()),
                        PacketDistributor.SERVER.noArg());
                selectionSent = true;
                if (this.minecraft != null) {
                    this.minecraft.setScreen(null);
                }
                return true;
            }
            int dx = Math.floorDiv((int) mouseX - mapLeft, TILE);
```

- [ ] **Step 5: The bridge passes the list.** In `ClientRuntimeBridge.java`, replace:
```java
                new ApparitionMapScreen(packet.origin(), packet.radius(), packet.durationTicks()));
```
with:
```java
                new ApparitionMapScreen(packet.origin(), packet.radius(), packet.durationTicks(), packet.fireplaces()));
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "message.hogwartsmagic.apparition.splinched": "You've been Splinched!",` insert:
```json
  "gui.hogwartsmagic.apparition.fireplaces": "Known fireplaces (any distance):",
  "message.hogwartsmagic.apparition.fireplace_gone": "That fireplace is gone. You can no longer picture it.",
```

- [ ] **Step 7: Build, test and boot a dedicated server** (changed packet layout, new C2S packet)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `ApparitionSpell`, `ChooseApparitionFireplacePacket` or any `client` class.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/ApparitionSpell.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/ApparitionMapScreen.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master Apparate to any Floo fireplace they have used in the same dimension" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests and dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `spell.support`, `Apparition` or any `client` class.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed). Use `/spellmastery setrank @s <spell> <rank>` to switch ranks.
1. Break 10 blocks, Reparo (Novice) at them: 4 come back. At Master: all 10, and from up to 20 blocks away.
2. Reparo (Adept) left-click with a damaged pickaxe in the off-hand: the "Expert rank" message, no mana spent. At Expert: +10% durability, mana drops by the cast plus a little more for diamond than for wood.
3. Reparo (Expert) left-click with nothing damaged: the "Nothing ... needs mending" message, no mana spent.
4. Accio (Novice) a cow (10 max health): reeled in. A 30-health mob: "Too heavy". Voldemort at Master: never reeled.
5. Accio (Novice) at the floor next to dropped items: they fly to you.
6. Accio (Expert) a chest: the first stack flies to you. A chest inside spawn protection or a claimed area (another player's), or a locked one: nothing. A Gringotts vault chest: the bank alarm goes off.
7. Accio (Master) a second player with PvP on: their held item flies to you; with PvP off (`server.properties` pvp=false): it doesn't. An Elder Wand never moves.
8. Ascendio (Novice) aiming at the floor: you shoot about 6 blocks up and land without damage. At Master: about 15 blocks.
9. Ascendio (Expert): jump with it, cast again midair while looking down: a second, smaller boost even though the cooldown is running; a third midair cast does nothing until you land.
10. Ascendio (Master) under water: you rocket to the surface. Take-off next to two zombies: they're shoved away.
11. Depulso (Novice) a zombie standing a block from a wall: it slams for about 3 damage. In the open: only knockback.
12. Depulso (Expert) the ground in a crowd of zombies: all within 2 blocks fly back. A skeleton's arrow crossing the beam turns around.
13. Depulso (Master) a zombie into another zombie: both take the slam.
14. Apparition (Novice): the map is the old size (48), 4 seconds to choose. At Master: a wide map (200) that still fits the screen, 1 second.
15. Apparition with `apparitionSplinching = false` in the config: never splinched in 20 jumps at Novice.
16. Use a Floo fireplace, walk 500 blocks away, Apparition at Master: the fireplace is listed under the map; clicking it lands you on it. Break that fireplace, cast again and click it: the "fireplace is gone" message and it disappears from the list.

- [ ] **Step 3: Record results.** Note any failed check and fix it in a follow-up commit whose message says what was wrong. Then tell the user Phase 5b is ready to push.

---

## Self-Review

- **Spec coverage (roadmap Phase 5 and the controller's notes, second half):** Reparo more blocks and radius per rank and items at Expert with 10% per self-cast and mana by tier (T2); Accio item pulls, Expert container pull respecting locks and claims via an interaction check, Master PvP main-hand pull (T3); Ascendio self-launch (aim down or crouch-cast) with no fall damage, Expert midair double cast, Master underwater surge and take-off push (T4); Depulso wall slam, Expert crowd push and projectile reflect, Master mob-into-mob (T5); Apparition range and channel by rank, configurable splinching at 10% Novice default with an off switch, flat 100 mana kept (T6), Master jump to known Floo fireplaces at any distance in the same dimension (T7); dedicated-server boots in T3 to T8.
- **Cut on purpose (Decision 1):** Accio's "dropped items within 24 blocks" at Novice (replaced by the impact radius), Ascendio Adept momentum (kept at every rank instead), Depulso Adept stun, Apparition Expert extra side-along passenger, Owl Perches as Master destinations (the roadmap names only Floo), Reparo Master empty block entities.
- **Placeholder scan:** no TBD, no optional steps.
- **Type consistency:** `OpenApparitionMapPacket(BlockPos, int, int, List<FlooDestinationData>)` matches the bridge and the screen's 4-argument constructor (T7); T6 keeps the 3-argument versions until then. `ApparitionRules.tileCenterOffset(int, int, int)` and `mapStep(int, int, int)` are the screen's only rules calls. `AscendioSpell.launchSelf` returns `boolean` and the cast handler skips the cooldown on `true`. `ReparoSpell.castOnSelf(ServerPlayer, ItemStack, long)` matches the intercept. The cooldown line edited in T4 is the one T2 left (followed by `// Reparo cast on yourself`).
