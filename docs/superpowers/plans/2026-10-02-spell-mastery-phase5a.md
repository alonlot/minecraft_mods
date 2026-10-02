# Spell Mastery Phase 5a (Support: Heal, Protego, Expelliarmus, Revelio, Alohomora, Lumos) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first half of the support and utility spells gets its rank identity. Heal becomes the only cleanse (bleeding from Adept, cursed wounds at Master). Protego becomes a parry: a timed block dissolves a beam at Novice, reflects it at a random angle at Adept and back at the caster from Expert, and at Master refunds mana and knocks melee attackers back. Expelliarmus silences ("disarmed") and at Master wins the Elder Wand. Revelio's reach grows and at Master it strips Polyjuice disguises and the Invisibility Cloak from view for 5 seconds. Alohomora opens iron doors from Adept, every door around from Expert, and at Master Gringotts vault doors and a hostile Ward's barrier. Lumos first stops leaving light blocks behind, then gains Lumos Maxima orbs at Expert and Lumos Solem at Master.

**Architecture:** Every per-rank number lives in a Minecraft-free `*Rules` class in a new package `spell/support/`, tested with JUnit in one early task. One Minecraft-bound class per spell (`HealSpell`, `ProtegoSpell`, `ExpelliarmusSpell`, `RevelioSpell`, `AlohomoraSpell`, `LumosSpell`) holds the behaviour and that spell's own Forge hooks; `SpellType.applyHitEffect`, `SpellBeamEntity.onHit` and `SpellPackets.CastSpellPacket.handle` delegate to them. Ranks come from Phase 3's `DisableSupport.casterRank` (players: mastery rank; NPCs: 1). The Lumos light-block leak is fixed with a new `LumosLightBlock` that removes itself on a scheduled block tick (saved with the chunk) unless a Lumos holder is beside it, so no light can outlive an unload, a logout, a death or a restart. Phase 5b (`2026-10-02-spell-mastery-phase5b.md`) builds on this plan's `SupportMana`, `RankIndex` and left-click self-cast helper.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 5: Support and utility", "Decisions that change the design doc" (Decision 1: bespoke Novice identity + Master capstone, Adept/Expert numeric, plus the Expert and Adept unlocks the roadmap and the controller name explicitly) and "Global constraints". Numbers the roadmap leaves open come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` section "F. Defense and support". Where they disagree, the roadmap wins.

**Depends on Phases 2, 3 and 4 (all merged first).** This plan calls, by exact signature:
- Phase 2: `CombatEffects.responsible(@Nullable Entity)`, `CombatEffects.applySilenced(LivingEntity target, int ticks, @Nullable Entity source)` → `boolean`, `CombatEffects.cureCursedWound(LivingEntity)` → `boolean`, `CombatEffects.isCastingBlocked(LivingEntity)`; `ExampleMod.BLEEDING`, `ExampleMod.CURSED_WOUND` (`RegistryObject<MobEffect>`).
- Phase 3: `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int`, `TerrainRules.canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`.
- Phase 4: `DamageSupport.scaled(@Nullable Entity caster, float base)` → `float`.
- Phase 1: `SpellMasteryData.getRank(Player, SpellType)`, `SpellCosts.manaCost(Player, ItemStack, SpellType)`, `MasteryAwards.onBlockEffect(ServerPlayer, SpellType)`.

Before Task 1, confirm all three phases are in:
```bash
grep -n "public static int casterRank" src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java
grep -n "public static boolean canPlace" src/main/java/com/hogwartsmagic/spell/TerrainRules.java
grep -n "public static float scaled" src/main/java/com/hogwartsmagic/spell/damage/DamageSupport.java
grep -n "public static boolean cureCursedWound" src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java
```
Each must print one line. If any doesn't, stop: the phase it belongs to isn't merged.

## Global Constraints

- **Ranks:** every per-rank behaviour reads the caster's rank through `DisableSupport.casterRank(caster, spell)` (players: `SpellMasteryData.getRank`, 1..4). **NPC casters (Students, Death Eaters/`WizardEntity`, Voldemort) and ownerless beams are rank 1.**
- **Per-rank numbers (ticks at 20/s):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Heal | heal 6 (3 hearts) | 8; removes `bleeding` | 10; Regeneration I 80 | 12; removes `cursed_wound` |
  | Protego | 60-tick shield, front hits ×0.60 (blocks 40%); **parry window 8**: a front beam in the window dissolves and the shield stays | blocks 50%; window 8; parried beams reflect at a random angle (±60° yaw, ±15° pitch around your facing) | blocks 60%; window 12; reflect back at the caster | blocks 75%; window 12; at the caster; **a parry refunds 50% of the shield's mana (once per shield); front melee attackers are knocked back 1.0** |
  | Expelliarmus | drops the main-hand item (dropped item locked from pickup for the silence time if the victim is a player); `silenced` 40 on players and NPC casters; knockback 0.5 | silence 60; knockback 0.8 | 60; 0.8 | 80; 0.8; **allegiance:** disarming the Elder Wand's holder puts it in your inventory; disarming Voldemort wins you an Elder Wand once (he then no longer drops one) |
  | Revelio | ore radius 12, chest radius 8 | 14 / 10 | 16 / 12 | 20 / 16; **players within 20 blocks of the impact: a Polyjuice disguise is shown as the real player (true skin and name) and a worn Invisibility Cloak is revealed, both for 100 ticks** |
  | Alohomora | wooden doors, trapdoors, fence gates (the struck block) | also iron doors and iron trapdoors, which close again after 100 | every openable door, trapdoor and gate within 4 blocks of the impact (at most 12) | radius 4; **opens a Gringotts vault door from range (the bank's alarm and reseal run as if you had touched it) and turns off a hostile Ward's Protego Maxima barrier for 100** |
  | Lumos | wand light level 12, toggled by the cast (Nox) | light 15 | light 15; **right-click fires a beam: on a surface it leaves a Lumos Maxima orb (light 15) for 6000 ticks, at most 3 per caster**; the light toggle moves to left-click | **Lumos Solem:** the beam deals 6 (house-scaled) to undead, spiders, cave spiders and Acromantulas (`#hogwartsmagic:lumos_solem_vulnerable`) and blinds them for 60 |

- **Unforgivables are never reflected.** Avada Kedavra passes every Protego as today. Crucio and Imperio are absorbed (the shield breaks) even inside the parry window. Heal and Protego beams pass through a shield.
- **A reflected beam** is a fresh `SpellBeamEntity` owned by the reflector, flagged `reflected`: it can't be reflected again and earns its owner no mastery XP.
- **Shields that keep their old rules:** NPC Protego (amplifier ≥ 1 layers) and Protego Maxima's amplifier-3 bubble are untouched. Only a Protego cast through `SpellType.applyHitEffect` (a player's self-cast or a Protego beam) is a ranked shield.
- **Lumos lights** are `hogwartsmagic:lumos_light` blocks, never vanilla `minecraft:light`. A follow light checks for a Lumos holder within 1.5 blocks every 10 ticks (scheduled block tick, saved with the chunk) and on random ticks; a timed light (orbs, Revelio highlights) removes itself on one scheduled tick.
- **Terrain:** Lumos Maxima orbs go through `TerrainRules.canPlace` (ward, spawn protection, claims via `EntityPlaceEvent`). Alohomora only toggles doors; it changes no terrain.
- **Gringotts security is preserved:** Master Alohomora calls the vault door's own `beginOpening` with the caster as the opener, so a non-goblin still raises the alarm (heat, guards, reinforcements) and the vault still reseals. NPCs never open vaults.
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/` (or an existing `Dist.CLIENT` subscriber such as `event/ClientModEvents`), is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch, and talks back through C2S packets the server validates. This plan adds one S2C packet (`RevealDisguisePacket`) and no C2S packets.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine. (`SpellType.applyHitEffect` is common code; its existing switch stays.)
- Screens: `renderTransparentBackground`, no blur. (This plan adds no screens.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards (see Terrain above).
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs).
- `en_us.json` must stay valid JSON after every edit: replacements keep the original line's trailing comma, and an inserted line ends with a comma and goes after a line that already ends with one.
- Line numbers drift (other phases edit the same files). Every edit below names the exact text to find; trust the text, not a number.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/support/RankIndex.java` | Pure: rank 1..4 → table index, clamped (package-private) |
| Create `src/main/java/com/hogwartsmagic/spell/support/HealRules.java` | Pure: heal amounts, cleanses, regeneration |
| Create `src/main/java/com/hogwartsmagic/spell/support/ProtegoRules.java` | Pure: block fraction, parry window and mode, facing test, reflect yaw |
| Create `src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusRules.java` | Pure: silence, knockback, allegiance |
| Create `src/main/java/com/hogwartsmagic/spell/support/RevelioRules.java` | Pure: reveal radii, Master reveal |
| Create `src/main/java/com/hogwartsmagic/spell/support/AlohomoraRules.java` | Pure: iron, area radius, vaults, wards |
| Create `src/main/java/com/hogwartsmagic/spell/support/LumosRules.java` | Pure: light levels, beam, orbs, Solem |
| Create `src/main/java/com/hogwartsmagic/block/LumosLightBlock.java` | Self-removing follow and timed light |
| Create `src/main/resources/assets/hogwartsmagic/blockstates/lumos_light.json` | Invisible block state |
| Create `src/main/java/com/hogwartsmagic/spell/support/HealSpell.java` | Heal, cleanses, milk guard for bleeding |
| Create `src/main/java/com/hogwartsmagic/spell/support/SupportMana.java` | Spend and refund mana (used by Protego here, Reparo in 5b) |
| Create `src/main/java/com/hogwartsmagic/spell/support/ProtegoSpell.java` | Ranked shield, parry, reflect, frontal reduction |
| Create `src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusSpell.java` | Disarm, silence, Elder Wand allegiance |
| Create `src/main/java/com/hogwartsmagic/spell/support/RevelioSpell.java` | Master area reveal |
| Create `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java` | Doors, iron re-close, area, vaults, ward barrier |
| Create `src/main/java/com/hogwartsmagic/spell/support/LumosSpell.java` | Toggle, orbs, Solem |
| Create `src/main/resources/data/hogwartsmagic/tags/entity_type/lumos_solem_vulnerable.json` | Solem targets |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Register `LUMOS_LIGHT` |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Heal, Protego, Expelliarmus, Lumos delegate |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Reflected flag, Protego intercept, Revelio radii and Master reveal, Alohomora and Lumos block branches, Lumos chip |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `RevealDisguisePacket`; Lumos cast branch |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Lumos light handling, Revelio lights, Protego hurt hook, cloak reveal overload, left-click self-cast helper |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | Heal earns cast XP |
| Modify `src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java` | No Elder Wand drop once it was won |
| Modify `src/main/java/com/hogwartsmagic/block/entity/CaveInimicumWardBlockEntity.java` | Barrier suppression |
| Modify `src/main/java/com/hogwartsmagic/caveinimicum/CaveInimicumManager.java` | Suppressed barrier lets projectiles through; Alohomora on the barrier |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | `revealDisguise` dispatch |
| Modify `src/main/java/com/hogwartsmagic/client/PolyjuiceClientState.java` | Reveal window |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Draw the real player while revealed |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Descriptions and messages |
| Create tests under `src/test/java/com/hogwartsmagic/spell/support/` | Unit tests |

---

### Task 1: The pure rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/RankIndex.java`, `HealRules.java`, `ProtegoRules.java`, `ExpelliarmusRules.java`, `RevelioRules.java`, `AlohomoraRules.java`, `LumosRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/support/HealRulesTest.java`, `ProtegoRulesTest.java`, `ExpelliarmusRulesTest.java`, `RevelioRulesTest.java`, `AlohomoraRulesTest.java`, `LumosRulesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private)
  - `HealRules`: `static float healAmount(int rank)`, `static boolean curesBleeding(int rank)`, `static int regenTicks(int rank)`, `static boolean curesCursedWound(int rank)`
  - `ProtegoRules.SHIELD_TICKS = 60`, `PARRY_DISSOLVE = 0`, `PARRY_RANDOM = 1`, `PARRY_AT_CASTER = 2`, `RANDOM_REFLECT_YAW_DEGREES = 60.0`, `RANDOM_REFLECT_PITCH_DEGREES = 15.0`, `MASTER_MELEE_KNOCKBACK = 1.0`; `static float blockFraction(int rank)`, `static int parryWindowTicks(int rank)`, `static int parryMode(int rank)`, `static float refundFraction(int rank)`, `static boolean knocksBackMelee(int rank)`, `static boolean inParryWindow(long raisedAt, long now, int rank)`, `static boolean isFront(double facingX, double facingZ, double toSourceX, double toSourceZ)`, `static float reducedDamage(float amount, int rank)`, `static float randomReflectYaw(float facingYaw, double roll)`, `static boolean canReflect(boolean unforgivable, boolean alreadyReflected)`
  - `ExpelliarmusRules`: `static int silenceTicks(int rank)`, `static double knockback(int rank)`, `static boolean winsElderWand(int rank)`
  - `RevelioRules.MASTER_RADIUS = 20.0`, `REVEAL_TICKS = 100`; `static int oreRadius(int rank)`, `static int chestRadius(int rank)`, `static boolean revealsDisguises(int rank)`
  - `AlohomoraRules.IRON_OPEN_TICKS = 100`, `MAX_AREA_OPENINGS = 12`, `WARD_SUPPRESS_TICKS = 100`; `static boolean opensIron(int rank)`, `static int areaRadius(int rank)`, `static boolean opensVaults(int rank)`, `static boolean suppressesWards(int rank)`
  - `LumosRules.LIGHT_TICKS = 12000`, `MAX_ORBS = 3`, `ORB_TICKS = 6000`, `ORB_LIGHT = 15`, `SOLEM_DAMAGE = 6.0f`, `SOLEM_BLIND_TICKS = 60`, `FOLLOW_RECHECK_TICKS = 10`, `FOLLOW_SEARCH_RADIUS = 1.5`; `static int lightForAmplifier(int amplifier)`, `static int amplifierForRank(int rank)`, `static boolean firesBeam(int rank)`, `static boolean placesOrbs(int rank)`, `static boolean solem(int rank)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/support/HealRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealRulesTest {
    @Test
    void healGrowsByAHeartPerRankAndClamps() {
        assertEquals(6f, HealRules.healAmount(1), 1e-4);
        assertEquals(8f, HealRules.healAmount(2), 1e-4);
        assertEquals(10f, HealRules.healAmount(3), 1e-4);
        assertEquals(12f, HealRules.healAmount(4), 1e-4);
        assertEquals(6f, HealRules.healAmount(0), 1e-4);
        assertEquals(12f, HealRules.healAmount(9), 1e-4);
    }

    @Test
    void bleedingIsCleansedFromAdept() {
        assertFalse(HealRules.curesBleeding(1));
        assertTrue(HealRules.curesBleeding(2));
        assertTrue(HealRules.curesBleeding(4));
    }

    @Test
    void cursedWoundOnlyAtMaster() {
        assertFalse(HealRules.curesCursedWound(3));
        assertTrue(HealRules.curesCursedWound(4));
    }

    @Test
    void regenerationFromExpert() {
        assertEquals(0, HealRules.regenTicks(1));
        assertEquals(0, HealRules.regenTicks(2));
        assertEquals(80, HealRules.regenTicks(3));
        assertEquals(80, HealRules.regenTicks(4));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/ProtegoRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtegoRulesTest {
    @Test
    void blockFractionPerRank() {
        assertEquals(0.40f, ProtegoRules.blockFraction(1), 1e-4);
        assertEquals(0.50f, ProtegoRules.blockFraction(2), 1e-4);
        assertEquals(0.60f, ProtegoRules.blockFraction(3), 1e-4);
        assertEquals(0.75f, ProtegoRules.blockFraction(4), 1e-4);
    }

    @Test
    void parryWindowWidensAtExpert() {
        assertEquals(8, ProtegoRules.parryWindowTicks(1));
        assertEquals(8, ProtegoRules.parryWindowTicks(2));
        assertEquals(12, ProtegoRules.parryWindowTicks(3));
        assertEquals(12, ProtegoRules.parryWindowTicks(4));
    }

    @Test
    void parryModeClimbsFromDissolveToReturnToSender() {
        assertEquals(ProtegoRules.PARRY_DISSOLVE, ProtegoRules.parryMode(1));
        assertEquals(ProtegoRules.PARRY_RANDOM, ProtegoRules.parryMode(2));
        assertEquals(ProtegoRules.PARRY_AT_CASTER, ProtegoRules.parryMode(3));
        assertEquals(ProtegoRules.PARRY_AT_CASTER, ProtegoRules.parryMode(4));
    }

    @Test
    void masterRefundsAndKnocksBack() {
        assertEquals(0f, ProtegoRules.refundFraction(3), 1e-4);
        assertEquals(0.5f, ProtegoRules.refundFraction(4), 1e-4);
        assertFalse(ProtegoRules.knocksBackMelee(3));
        assertTrue(ProtegoRules.knocksBackMelee(4));
    }

    @Test
    void parryWindowCountsFromTheRaise() {
        assertTrue(ProtegoRules.inParryWindow(100, 100, 1));
        assertTrue(ProtegoRules.inParryWindow(100, 107, 1));
        assertFalse(ProtegoRules.inParryWindow(100, 108, 1));
        assertTrue(ProtegoRules.inParryWindow(100, 111, 3));
        assertFalse(ProtegoRules.inParryWindow(100, 112, 3));
        assertFalse(ProtegoRules.inParryWindow(100, 99, 1));
    }

    @Test
    void frontIsTheHalfYouFace() {
        assertTrue(ProtegoRules.isFront(0, 1, 0, 5));
        assertFalse(ProtegoRules.isFront(0, 1, 0, -5));
        assertTrue(ProtegoRules.isFront(0, 1, 3, 0));
        assertTrue(ProtegoRules.isFront(0, 0, 1, 1));
    }

    @Test
    void reducedDamageKeepsTheUnblockedShare() {
        assertEquals(6.0f, ProtegoRules.reducedDamage(10f, 1), 1e-4);
        assertEquals(2.5f, ProtegoRules.reducedDamage(10f, 4), 1e-4);
    }

    @Test
    void randomReflectStaysWithinSixtyDegrees() {
        assertEquals(90f, ProtegoRules.randomReflectYaw(90f, 0.5), 1e-4);
        assertEquals(30f, ProtegoRules.randomReflectYaw(90f, 0.0), 1e-4);
        assertEquals(150f, ProtegoRules.randomReflectYaw(90f, 1.0), 1e-4);
    }

    @Test
    void unforgivablesAndRepeatsAreNeverReflected() {
        assertTrue(ProtegoRules.canReflect(false, false));
        assertFalse(ProtegoRules.canReflect(true, false));
        assertFalse(ProtegoRules.canReflect(false, true));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/ExpelliarmusRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpelliarmusRulesTest {
    @Test
    void silenceGrowsToFourSeconds() {
        assertEquals(40, ExpelliarmusRules.silenceTicks(1));
        assertEquals(60, ExpelliarmusRules.silenceTicks(2));
        assertEquals(60, ExpelliarmusRules.silenceTicks(3));
        assertEquals(80, ExpelliarmusRules.silenceTicks(4));
    }

    @Test
    void knockbackStepsUpAtAdept() {
        assertEquals(0.5, ExpelliarmusRules.knockback(1), 1e-9);
        assertEquals(0.8, ExpelliarmusRules.knockback(2), 1e-9);
        assertEquals(0.8, ExpelliarmusRules.knockback(4), 1e-9);
    }

    @Test
    void onlyAMasterWinsTheElderWand() {
        assertFalse(ExpelliarmusRules.winsElderWand(3));
        assertTrue(ExpelliarmusRules.winsElderWand(4));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/RevelioRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevelioRulesTest {
    @Test
    void radiiGrowWithRank() {
        assertEquals(12, RevelioRules.oreRadius(1));
        assertEquals(14, RevelioRules.oreRadius(2));
        assertEquals(16, RevelioRules.oreRadius(3));
        assertEquals(20, RevelioRules.oreRadius(4));
        assertEquals(8, RevelioRules.chestRadius(1));
        assertEquals(10, RevelioRules.chestRadius(2));
        assertEquals(12, RevelioRules.chestRadius(3));
        assertEquals(16, RevelioRules.chestRadius(4));
    }

    @Test
    void disguisesOnlyAtMaster() {
        assertFalse(RevelioRules.revealsDisguises(3));
        assertTrue(RevelioRules.revealsDisguises(4));
        assertEquals(20.0, RevelioRules.MASTER_RADIUS, 1e-9);
        assertEquals(100, RevelioRules.REVEAL_TICKS);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/AlohomoraRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlohomoraRulesTest {
    @Test
    void ironFromAdept() {
        assertFalse(AlohomoraRules.opensIron(1));
        assertTrue(AlohomoraRules.opensIron(2));
        assertTrue(AlohomoraRules.opensIron(4));
    }

    @Test
    void areaFromExpert() {
        assertEquals(0, AlohomoraRules.areaRadius(1));
        assertEquals(0, AlohomoraRules.areaRadius(2));
        assertEquals(4, AlohomoraRules.areaRadius(3));
        assertEquals(4, AlohomoraRules.areaRadius(4));
    }

    @Test
    void vaultsAndWardsAtMaster() {
        assertFalse(AlohomoraRules.opensVaults(3));
        assertTrue(AlohomoraRules.opensVaults(4));
        assertFalse(AlohomoraRules.suppressesWards(3));
        assertTrue(AlohomoraRules.suppressesWards(4));
        assertEquals(100, AlohomoraRules.IRON_OPEN_TICKS);
        assertEquals(100, AlohomoraRules.WARD_SUPPRESS_TICKS);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/support/LumosRulesTest.java`:
```java
package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumosRulesTest {
    @Test
    void lightLevelFollowsTheAmplifier() {
        assertEquals(12, LumosRules.lightForAmplifier(0));
        assertEquals(15, LumosRules.lightForAmplifier(1));
        assertEquals(12, LumosRules.lightForAmplifier(-1));
        assertEquals(15, LumosRules.lightForAmplifier(5));
    }

    @Test
    void brightFromAdept() {
        assertEquals(0, LumosRules.amplifierForRank(1));
        assertEquals(1, LumosRules.amplifierForRank(2));
        assertEquals(1, LumosRules.amplifierForRank(4));
    }

    @Test
    void beamAndOrbsFromExpertSolemAtMaster() {
        assertFalse(LumosRules.firesBeam(2));
        assertTrue(LumosRules.firesBeam(3));
        assertFalse(LumosRules.placesOrbs(2));
        assertTrue(LumosRules.placesOrbs(3));
        assertFalse(LumosRules.solem(3));
        assertTrue(LumosRules.solem(4));
        assertEquals(3, LumosRules.MAX_ORBS);
        assertEquals(6000, LumosRules.ORB_TICKS);
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests 'com.hogwartsmagic.spell.support.*'`
Expected: compile FAIL, `cannot find symbol: class HealRules` (and the other rule classes).

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/support/RankIndex.java`:
```java
package com.hogwartsmagic.spell.support;

/** Rank 1..4 to a table index 0..3, clamped, so a bad rank never throws. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/HealRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Heal (Episkey to Vulnera Sanentur): the only cleanse for the mod's curses. Pure numbers. */
public final class HealRules {
    private static final float[] HEAL = {6.0f, 8.0f, 10.0f, 12.0f};
    private static final int[] REGEN_TICKS = {0, 0, 80, 80};

    private HealRules() {
    }

    public static float healAmount(int rank) {
        return HEAL[RankIndex.of(rank)];
    }

    public static boolean curesBleeding(int rank) {
        return rank >= 2;
    }

    public static int regenTicks(int rank) {
        return REGEN_TICKS[RankIndex.of(rank)];
    }

    public static boolean curesCursedWound(int rank) {
        return rank >= 4;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/ProtegoRules.java`:
```java
package com.hogwartsmagic.spell.support;

/**
 * Protego as a parry. The shield lasts {@link #SHIELD_TICKS}; damage from the front is cut by
 * {@link #blockFraction}; a beam from the front inside the first {@link #parryWindowTicks} is parried
 * (dissolved, reflected at random, or sent back at its caster, by rank). Pure numbers and geometry.
 */
public final class ProtegoRules {
    public static final int SHIELD_TICKS = 60;
    public static final int PARRY_DISSOLVE = 0;
    public static final int PARRY_RANDOM = 1;
    public static final int PARRY_AT_CASTER = 2;
    public static final double RANDOM_REFLECT_YAW_DEGREES = 60.0;
    public static final double RANDOM_REFLECT_PITCH_DEGREES = 15.0;
    public static final double MASTER_MELEE_KNOCKBACK = 1.0;

    private static final float[] BLOCK = {0.40f, 0.50f, 0.60f, 0.75f};
    private static final int[] WINDOW = {8, 8, 12, 12};
    private static final int[] MODE = {PARRY_DISSOLVE, PARRY_RANDOM, PARRY_AT_CASTER, PARRY_AT_CASTER};

    private ProtegoRules() {
    }

    public static float blockFraction(int rank) {
        return BLOCK[RankIndex.of(rank)];
    }

    public static int parryWindowTicks(int rank) {
        return WINDOW[RankIndex.of(rank)];
    }

    public static int parryMode(int rank) {
        return MODE[RankIndex.of(rank)];
    }

    public static float refundFraction(int rank) {
        return rank >= 4 ? 0.5f : 0.0f;
    }

    public static boolean knocksBackMelee(int rank) {
        return rank >= 4;
    }

    public static boolean inParryWindow(long raisedAt, long now, int rank) {
        long age = now - raisedAt;
        return age >= 0 && age < parryWindowTicks(rank);
    }

    /** True when the source lies in the half-space the holder faces (horizontal only); degenerate input counts as front. */
    public static boolean isFront(double facingX, double facingZ, double toSourceX, double toSourceZ) {
        if (Math.hypot(facingX, facingZ) < 1.0e-6 || Math.hypot(toSourceX, toSourceZ) < 1.0e-6) {
            return true;
        }
        return facingX * toSourceX + facingZ * toSourceZ >= 0.0;
    }

    public static float reducedDamage(float amount, int rank) {
        return amount * (1.0f - blockFraction(rank));
    }

    /** {@code roll} in 0..1 maps to facing yaw -60..+60 degrees. */
    public static float randomReflectYaw(float facingYaw, double roll) {
        return facingYaw + (float) ((roll * 2.0 - 1.0) * RANDOM_REFLECT_YAW_DEGREES);
    }

    public static boolean canReflect(boolean unforgivable, boolean alreadyReflected) {
        return !unforgivable && !alreadyReflected;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Expelliarmus: silenced ("disarmed") time, knockback and the Master allegiance rule. Pure numbers. */
public final class ExpelliarmusRules {
    private static final int[] SILENCE = {40, 60, 60, 80};
    private static final double[] KNOCKBACK = {0.5, 0.8, 0.8, 0.8};

    private ExpelliarmusRules() {
    }

    public static int silenceTicks(int rank) {
        return SILENCE[RankIndex.of(rank)];
    }

    public static double knockback(int rank) {
        return KNOCKBACK[RankIndex.of(rank)];
    }

    public static boolean winsElderWand(int rank) {
        return rank >= 4;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/RevelioRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Revelio: how far it looks, and the Master reveal of disguises and cloaks. Pure numbers. */
public final class RevelioRules {
    public static final double MASTER_RADIUS = 20.0;
    public static final int REVEAL_TICKS = 100;

    private static final int[] ORE = {12, 14, 16, 20};
    private static final int[] CHEST = {8, 10, 12, 16};

    private RevelioRules() {
    }

    public static int oreRadius(int rank) {
        return ORE[RankIndex.of(rank)];
    }

    public static int chestRadius(int rank) {
        return CHEST[RankIndex.of(rank)];
    }

    public static boolean revealsDisguises(int rank) {
        return rank >= 4;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/AlohomoraRules.java`:
```java
package com.hogwartsmagic.spell.support;

/** Alohomora: what it may open at each rank. Pure numbers. */
public final class AlohomoraRules {
    public static final int IRON_OPEN_TICKS = 100;
    public static final int MAX_AREA_OPENINGS = 12;
    public static final int WARD_SUPPRESS_TICKS = 100;

    private static final int[] AREA = {0, 0, 4, 4};

    private AlohomoraRules() {
    }

    public static boolean opensIron(int rank) {
        return rank >= 2;
    }

    public static int areaRadius(int rank) {
        return AREA[RankIndex.of(rank)];
    }

    public static boolean opensVaults(int rank) {
        return rank >= 4;
    }

    public static boolean suppressesWards(int rank) {
        return rank >= 4;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/support/LumosRules.java`:
```java
package com.hogwartsmagic.spell.support;

/**
 * Lumos: wand-light brightness, the Expert beam with its orbs, and Lumos Solem. The light effect's
 * amplifier carries the brightness (0 = level 12, 1 = level 15) so the light tick needs no rank lookup.
 */
public final class LumosRules {
    public static final int LIGHT_TICKS = 12000;
    public static final int MAX_ORBS = 3;
    public static final int ORB_TICKS = 6000;
    public static final int ORB_LIGHT = 15;
    public static final float SOLEM_DAMAGE = 6.0f;
    public static final int SOLEM_BLIND_TICKS = 60;
    public static final int FOLLOW_RECHECK_TICKS = 10;
    public static final double FOLLOW_SEARCH_RADIUS = 1.5;

    private LumosRules() {
    }

    public static int lightForAmplifier(int amplifier) {
        return amplifier <= 0 ? 12 : 15;
    }

    public static int amplifierForRank(int rank) {
        return rank >= 2 ? 1 : 0;
    }

    public static boolean firesBeam(int rank) {
        return rank >= 3;
    }

    public static boolean placesOrbs(int rank) {
        return rank >= 3;
    }

    public static boolean solem(int rank) {
        return rank >= 4;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests 'com.hogwartsmagic.spell.support.*'`
Expected: `BUILD SUCCESSFUL`, 6 test classes pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/RankIndex.java src/main/java/com/hogwartsmagic/spell/support/HealRules.java src/main/java/com/hogwartsmagic/spell/support/ProtegoRules.java src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusRules.java src/main/java/com/hogwartsmagic/spell/support/RevelioRules.java src/main/java/com/hogwartsmagic/spell/support/AlohomoraRules.java src/main/java/com/hogwartsmagic/spell/support/LumosRules.java src/test/java/com/hogwartsmagic/spell/support/HealRulesTest.java src/test/java/com/hogwartsmagic/spell/support/ProtegoRulesTest.java src/test/java/com/hogwartsmagic/spell/support/ExpelliarmusRulesTest.java src/test/java/com/hogwartsmagic/spell/support/RevelioRulesTest.java src/test/java/com/hogwartsmagic/spell/support/AlohomoraRulesTest.java src/test/java/com/hogwartsmagic/spell/support/LumosRulesTest.java
git commit -m "Add the per-rank rules for Heal, Protego, Expelliarmus, Revelio, Alohomora and Lumos" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Stop Lumos leaving light blocks behind

**The bug:** `ModEvents.handleLumosLight` places vanilla `minecraft:light` blocks (a real, persistent block) and remembers them only in the static `LUMOS_POS` map. The light is removed only when the holder ticks again without the effect. Logging out, dying, changing dimension, a mob holder despawning, a chunk unloading or the server stopping all leave the block in the world forever (the map entry is lost or points at the wrong level). Revelio's temporary lights have the same flaw on a server stop (`REVELIO_LIGHTS` is in memory only). The fix: a dedicated `lumos_light` block that removes itself. Lights already leaked into existing worlds as vanilla `minecraft:light` can't be told apart from player-placed ones, so they stay; a Lumos holder walking through one adopts and later clears it, as the old code did.

**Files:**
- Create: `src/main/java/com/hogwartsmagic/block/LumosLightBlock.java`
- Create: `src/main/resources/assets/hogwartsmagic/blockstates/lumos_light.json`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (block registration)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`handleLumosLight`, `cleanupLumosLight`, `addTemporaryRevelioLight`, remove `REVELIO_LIGHTS`/`tickRevelioLights`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `LumosRules.lightForAmplifier/FOLLOW_RECHECK_TICKS/FOLLOW_SEARCH_RADIUS` (Task 1); `ExampleMod.LUMOS_EFFECT` (existing)
- Produces:
  - `ExampleMod.LUMOS_LIGHT` (`RegistryObject<Block>`)
  - `LumosLightBlock.LIGHT` (`IntegerProperty`, 0..15), `LumosLightBlock.TIMED` (`BooleanProperty`)
  - `static boolean LumosLightBlock.placeFollowLight(ServerLevel level, BlockPos pos, int light)`
  - `static void LumosLightBlock.removeFollowLight(ServerLevel level, BlockPos pos)`
  - `static boolean LumosLightBlock.placeTimedLight(ServerLevel level, BlockPos pos, int light, int ticks)`
  - `static boolean LumosLightBlock.isTimedLight(BlockState state)`

- [ ] **Step 1: Create the block**

`src/main/java/com/hogwartsmagic/block/LumosLightBlock.java`:
```java
package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.support.LumosRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The light Lumos leaves in the world. Two kinds, by {@link #TIMED}:
 *
 * <ul>
 *   <li><b>Follow light</b> (above a Lumos holder's head). Every {@link LumosRules#FOLLOW_RECHECK_TICKS}
 *       ticks a scheduled block tick looks for a living Lumos holder within
 *       {@link LumosRules#FOLLOW_SEARCH_RADIUS} blocks and removes the block if there is none. Random
 *       ticks run the same check, so a light whose scheduled tick was lost still goes.</li>
 *   <li><b>Timed light</b> (Lumos Maxima orbs, Revelio highlights). One scheduled tick at placement
 *       removes it. Sparkles on the client so an orb reads as an orb.</li>
 * </ul>
 *
 * <p>Scheduled block ticks are saved with the chunk, so neither kind can outlive an unload, a logout,
 * a death or a restart. That is the bug this block replaces: vanilla light blocks tracked only in a
 * static map. Not an {@code air()} block on purpose: chunk sections only random-tick non-air blocks.
 */
public class LumosLightBlock extends Block {
    public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;
    public static final BooleanProperty TIMED = BooleanProperty.create("timed");

    public LumosLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LIGHT, 15).setValue(TIMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT, TIMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Timed lights are scheduled by placeTimedLight with their own delay.
        if (!level.isClientSide && !state.getValue(TIMED)) {
            level.scheduleTick(pos, this, LumosRules.FOLLOW_RECHECK_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TIMED) || !hasLumosHolderNear(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, LumosRules.FOLLOW_RECHECK_TICKS);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(TIMED);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasLumosHolderNear(level, pos)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(TIMED) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    0.0, 0.005, 0.0);
        }
    }

    private static boolean hasLumosHolderNear(ServerLevel level, BlockPos pos) {
        Holder<MobEffect> lumos = ExampleMod.LUMOS_EFFECT.getHolder().orElse(null);
        if (lumos == null) {
            return false;
        }
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(LumosRules.FOLLOW_SEARCH_RADIUS),
                e -> e.isAlive() && e.hasEffect(lumos)).isEmpty();
    }

    /**
     * Puts (or re-levels) a follow light at {@code pos}. Takes empty space, an existing follow light, or a
     * vanilla light block (the old Lumos code adopted those too, which also clears lights it leaked).
     */
    public static boolean placeFollowLight(ServerLevel level, BlockPos pos, int light) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        boolean ours = current.is(ExampleMod.LUMOS_LIGHT.get()) && !current.getValue(TIMED);
        if (!ours && !current.isAir() && !current.is(Blocks.LIGHT)) {
            return false;
        }
        BlockState wanted = ExampleMod.LUMOS_LIGHT.get().defaultBlockState()
                .setValue(LIGHT, Mth.clamp(light, 0, 15))
                .setValue(TIMED, false);
        if (!current.equals(wanted)) {
            level.setBlock(pos, wanted, Block.UPDATE_ALL);
        }
        return true;
    }

    public static void removeFollowLight(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current.is(ExampleMod.LUMOS_LIGHT.get()) && !current.getValue(TIMED)) {
            level.removeBlock(pos, false);
        }
    }

    /** A light that removes itself after {@code ticks}. Takes empty space or any light block. */
    public static boolean placeTimedLight(ServerLevel level, BlockPos pos, int light, int ticks) {
        if (ticks <= 0 || !level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.is(Blocks.LIGHT) && !current.is(ExampleMod.LUMOS_LIGHT.get())) {
            return false;
        }
        level.setBlock(pos, ExampleMod.LUMOS_LIGHT.get().defaultBlockState()
                .setValue(LIGHT, Mth.clamp(light, 0, 15))
                .setValue(TIMED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, ExampleMod.LUMOS_LIGHT.get(), ticks);
        return true;
    }

    public static boolean isTimedLight(BlockState state) {
        return state.is(ExampleMod.LUMOS_LIGHT.get()) && state.getValue(TIMED);
    }
}
```

- [ ] **Step 2: Register it.** In `ExampleMod.java`, directly above:
```java
    // Custom Invisible Light Block for Patronus
```
insert:
```java
    // Lumos light (Spell Mastery Phase 5): removes itself, so it can never be left behind.
    public static final RegistryObject<Block> LUMOS_LIGHT = BLOCKS.register("lumos_light",
            () -> new com.hogwartsmagic.block.LumosLightBlock(
                    BlockBehaviour.Properties.of()
                            .replaceable()
                            .noCollission()
                            .noOcclusion()
                            .noLootTable()
                            .instabreak()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                            .lightLevel(state -> state.getValue(com.hogwartsmagic.block.LumosLightBlock.LIGHT))));

```

- [ ] **Step 3: The block state file**

`src/main/resources/assets/hogwartsmagic/blockstates/lumos_light.json`:
```json
{
  "multipart": [
    { "apply": { "model": "minecraft:block/air" } }
  ]
}
```

- [ ] **Step 4: Lumos uses the new block.** In `ModEvents.java`, replace the whole `handleLumosLight` and `cleanupLumosLight` methods:
```java
    private static void handleLumosLight(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide && entity.tickCount % 3 == 0) {
            java.util.UUID id = entity.getUUID();
            net.minecraft.core.BlockPos desired = entity.blockPosition().above();
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) entity.level();

            net.minecraft.core.BlockPos prev = LUMOS_POS.get(id);
            if (prev != null && !prev.equals(desired)) {
                if (level.getBlockState(prev).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                    level.setBlock(prev, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }

            // Place light at desired position if air or already light
            if (level.isEmptyBlock(desired) || level.getBlockState(desired).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                level.setBlock(desired, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15), 3);
                LUMOS_POS.put(id, desired);
            }
        }
    }

    private static void cleanupLumosLight(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide) {
            java.util.UUID id = entity.getUUID();
            net.minecraft.core.BlockPos prev = LUMOS_POS.remove(id);
            if (prev != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
                if (level.getBlockState(prev).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                    level.setBlock(prev, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
```
with:
```java
    /**
     * Keeps a Lumos follow light above the holder's head. The light block removes itself when no
     * holder is beside it (LumosLightBlock), so this map is only a fast path for moving it; losing an
     * entry (logout, death, dimension change, restart) can no longer leave a light behind.
     */
    private static void handleLumosLight(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide && entity.tickCount % 3 == 0) {
            java.util.UUID id = entity.getUUID();
            net.minecraft.core.BlockPos desired = entity.blockPosition().above();
            net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) entity.level();
            net.minecraft.world.effect.MobEffectInstance lumos = entity.getEffect(ExampleMod.LUMOS_EFFECT.getHolder().get());
            int light = com.hogwartsmagic.spell.support.LumosRules.lightForAmplifier(lumos == null ? 0 : lumos.getAmplifier());

            net.minecraft.core.BlockPos prev = LUMOS_POS.get(id);
            if (prev != null && !prev.equals(desired)) {
                com.hogwartsmagic.block.LumosLightBlock.removeFollowLight(level, prev);
            }
            if (com.hogwartsmagic.block.LumosLightBlock.placeFollowLight(level, desired, light)) {
                LUMOS_POS.put(id, desired);
            }
        }
    }

    private static void cleanupLumosLight(net.minecraft.world.entity.LivingEntity entity) {
        if (!entity.level().isClientSide) {
            java.util.UUID id = entity.getUUID();
            net.minecraft.core.BlockPos prev = LUMOS_POS.remove(id);
            if (prev != null && entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
                com.hogwartsmagic.block.LumosLightBlock.removeFollowLight(level, prev);
            }
        }
    }
```

- [ ] **Step 5: Revelio's highlights become timed lights.** In `ModEvents.java`, replace the whole `addTemporaryRevelioLight` method:
```java
    public static void addTemporaryRevelioLight(ServerLevel level, BlockPos pos, int ttlTicks) {
        if (ttlTicks <= 0) {
            return;
        }
        if (!(level.isEmptyBlock(pos) || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LIGHT))) {
            return;
        }

        level.setBlock(pos, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15), 3);
        REVELIO_LIGHTS
                .computeIfAbsent(level.dimension(), ignored -> new java.util.HashMap<>())
                .merge(pos.immutable(), ttlTicks, Math::max);
    }
```
with:
```java
    /** A timed Lumos light: its removal is a scheduled block tick saved with the chunk, so it survives no restart. */
    public static void addTemporaryRevelioLight(ServerLevel level, BlockPos pos, int ttlTicks) {
        com.hogwartsmagic.block.LumosLightBlock.placeTimedLight(level, pos, 15, ttlTicks);
    }
```
Then delete the now-unused tracker. Delete the field line:
```java
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.Map<net.minecraft.core.BlockPos, Integer>> REVELIO_LIGHTS = new java.util.HashMap<>();
```
Delete the whole method:
```java
    private static void tickRevelioLights(ServerLevel level) {
        java.util.Map<BlockPos, Integer> active = REVELIO_LIGHTS.get(level.dimension());
        if (active == null || active.isEmpty()) {
            return;
        }

        java.util.Set<BlockPos> lumosPositions = new java.util.HashSet<>(LUMOS_POS.values());
        java.util.Iterator<java.util.Map.Entry<BlockPos, Integer>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<BlockPos, Integer> entry = it.next();
            BlockPos pos = entry.getKey();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                it.remove();
                continue;
            }

            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                if (!lumosPositions.contains(pos)) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }

        if (active.isEmpty()) {
            REVELIO_LIGHTS.remove(level.dimension());
        }
    }

```
And in `onLevelTick`, delete the line:
```java
        tickRevelioLights(level);
```
Check nothing else uses them:
Run: `grep -n "REVELIO_LIGHTS\|tickRevelioLights" src/main/java/com/hogwartsmagic/event/ModEvents.java`
Expected: no output.

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.wand_radial.silenced": "Silenced: no spells",` insert:
```json
  "block.hogwartsmagic.lumos_light": "Lumos Light",
```

- [ ] **Step 7: Build, test, validate and boot a dedicated server** (new block with scheduled and random ticks)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/assets/hogwartsmagic/blockstates/lumos_light.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `LumosLightBlock`, `lumos_light` or any `client` class.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/block/LumosLightBlock.java src/main/resources/assets/hogwartsmagic/blockstates/lumos_light.json src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Stop Lumos and Revelio leaving light blocks behind with a self-removing light block" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Heal (the only cleanse)

Today `HEAL` has no case in `SpellType.applyHitEffect`, so a player's Heal (self-cast with left-click, or the beam) and the Students' and Death Eaters' self-heal do nothing. This task gives it its ranked effect. Bleeding also becomes curable only by Heal: milk (any drink) can no longer remove it, the same guard Phase 2 put on `cursed_wound`.

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/HealSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` (`SELF_OR_CHANNEL`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `HealRules` (Task 1); `DisableSupport.casterRank` (Phase 3); `CombatEffects.cureCursedWound`, `ExampleMod.BLEEDING` (Phase 2)
- Produces: `HealSpell.apply(LivingEntity target, @Nullable Entity caster)`, `static boolean HealSpell.cureBleeding(LivingEntity target)`

- [ ] **Step 1: Create `HealSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Heal (Episkey, Vulnera Sanentur at Master). The only cleanse for Sectumsempra's bleeding (Adept and
 * up) and Dolohov's cursed wound (Master). Cleanses run before the heal so bleeding's heal halving
 * and the wound's lowered max health don't eat it. Common code; callers are server side.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealSpell {
    /** Set only inside {@link #cureBleeding}, the one removal path the drink guard lets through. */
    private static boolean bleedingCureInProgress;

    private HealSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide() || !target.isAlive()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.HEAL);
        if (HealRules.curesBleeding(rank)) {
            cureBleeding(target);
        }
        if (HealRules.curesCursedWound(rank)) {
            CombatEffects.cureCursedWound(target);
        }
        target.heal(HealRules.healAmount(rank));
        int regen = HealRules.regenTicks(rank);
        if (regen > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() * 0.7,
                    target.getZ(), 4 + rank * 2, 0.4, 0.3, 0.4, 0.0);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f + rank * 0.1f);
        }
    }

    public static boolean cureBleeding(LivingEntity target) {
        Holder<MobEffect> holder = ExampleMod.BLEEDING.getHolder().orElse(null);
        if (holder == null || target.level().isClientSide()) {
            return false;
        }
        bleedingCureInProgress = true;
        try {
            return target.removeEffect(holder);
        } finally {
            bleedingCureInProgress = false;
        }
    }

    /**
     * Milk (any drink) calls removeAllEffects while the bucket is the use item: refuse bleeding then,
     * exactly as Phase 2 does for cursed_wound. HIGH priority so Phase 2's bleed-source cleanup (normal
     * priority) never sees the cancelled removal. /effect clear and expiry are not item use.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBleedingRemove(MobEffectEvent.Remove event) {
        if (bleedingCureInProgress || event.getEffect() != ExampleMod.BLEEDING.get()) {
            return;
        }
        if (event.getEntity().isUsingItem()) {
            event.setCanceled(true);
        }
    }
}
```

- [ ] **Step 2: Wire the hit.** In `SpellType.java`, directly above:
```java
            case TENEBRIS_FLIGHT -> {
```
insert:
```java
            case HEAL -> com.hogwartsmagic.spell.support.HealSpell.apply(target, caster);
```

- [ ] **Step 3: Heal earns XP when cast, not by hitting things.** In `MasteryAwards.java`, replace:
```java
    private static final Set<SpellType> SELF_OR_CHANNEL = EnumSet.of(
            SpellType.LUMOS, SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA, SpellType.TENEBRIS_FLIGHT,
            SpellType.EXPECTO_PATRONUM, SpellType.APPARITION, SpellType.FIENDFYRE,
            SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO);
```
with:
```java
    // Heal joined in Spell Mastery Phase 5: it is mostly cast on yourself (no beam, no living hit), and
    // healing a hostile mob to farm hits would be silly. Cast XP is gated like every self spell.
    private static final Set<SpellType> SELF_OR_CHANNEL = EnumSet.of(
            SpellType.LUMOS, SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA, SpellType.TENEBRIS_FLIGHT,
            SpellType.EXPECTO_PATRONUM, SpellType.APPARITION, SpellType.FIENDFYRE,
            SpellType.WINGARDIUM_LEVIOSA, SpellType.IMPERIO, SpellType.HEAL);
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.heal.desc": "A basic healing charm used to mend minor injuries.",
```
with:
```json
  "spell.hogwartsmagic.heal.desc": "Mends wounds on yourself (left-click) or a target (beam): 3 hearts, one more per rank. The only cure for bleeding (Adept) and cursed wounds (Master); milk can't wash either away.",
```

- [ ] **Step 5: Build and test**

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/HealSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Heal work and make it the only cure for bleeding and cursed wounds" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Protego (parry window and reflect)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/SupportMana.java`
- Create: `src/main/java/com/hogwartsmagic/spell/support/ProtegoSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`PROTEGO` case)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (reflected flag, Protego intercept, XP guard)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`onLivingHurt` Protego branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ProtegoRules` (Task 1); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible` (Phase 2); `SpellCosts.manaCost` (Phase 1); `PlayerMagicData.getModData/saveModData/getMaxMana` (existing)
- Produces:
  - `SupportMana.current(ServerPlayer)` → `float`, `SupportMana.trySpend(ServerPlayer, float)` → `boolean` (creative always true), `SupportMana.add(ServerPlayer, float)` (clamped to max, synced)
  - `ProtegoSpell.raise(LivingEntity target, @Nullable Entity caster)`, `ProtegoSpell.isRankedShield(LivingEntity)` → `boolean`, `ProtegoSpell.onBeamHit(LivingEntity holder, SpellBeamEntity beam)` → `ProtegoSpell.BeamOutcome` (`NOT_SHIELDED`, `BLOCKED`, `PARRIED`, `REFLECTED`), `ProtegoSpell.onShieldedHurt(LivingHurtEvent)`
  - `SpellBeamEntity.markReflected()`, `SpellBeamEntity.isReflected()` → `boolean`

- [ ] **Step 1: Create `SupportMana`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Mana changes outside the cast handler's flat charge: Protego's refund, Reparo's priced item repair. Server only. */
public final class SupportMana {
    private SupportMana() {
    }

    public static float current(ServerPlayer player) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        return modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
    }

    /** Takes {@code amount} if the player has it (creative always does, for free). */
    public static boolean trySpend(ServerPlayer player, float amount) {
        if (player.isCreative() || amount <= 0.0f) {
            return true;
        }
        float mana = current(player);
        if (mana < amount) {
            return false;
        }
        set(player, mana - amount);
        return true;
    }

    public static void add(ServerPlayer player, float amount) {
        if (amount <= 0.0f) {
            return;
        }
        set(player, Math.min(PlayerMagicData.getMaxMana(player), current(player) + amount));
    }

    private static void set(ServerPlayer player, float mana) {
        float maxMana = PlayerMagicData.getMaxMana(player);
        CompoundTag modData = PlayerMagicData.getModData(player);
        modData.putFloat("Mana", mana);
        modData.putFloat("MaxMana", maxMana);
        PlayerMagicData.saveModData(player, modData);
        SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
    }
}
```

- [ ] **Step 2: Create `ProtegoSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import javax.annotation.Nullable;

/**
 * A ranked Protego: the shield a player raises (self-cast or a Protego beam), as opposed to the NPC
 * layer shields and Protego Maxima's bubble, which keep their old rules. The shield faces one way.
 * Blows from the front are cut by the rank's block fraction; a beam from the front inside the parry
 * window is parried (dissolved at Novice, reflected from Adept) and the shield stays; a front beam
 * outside the window breaks the shield. Avada Kedavra, Heal and Protego beams pass through; Crucio
 * and Imperio are never reflected. State lives in the holder's persistent data (server only).
 */
public final class ProtegoSpell {
    public static final String TAG_RAISED_AT = ExampleMod.MODID + ":protego_raised_at";
    public static final String TAG_UNTIL = ExampleMod.MODID + ":protego_until";
    public static final String TAG_RANK = ExampleMod.MODID + ":protego_rank";
    public static final String TAG_CASTER = ExampleMod.MODID + ":protego_caster";
    public static final String TAG_REFUND = ExampleMod.MODID + ":protego_refund";

    public enum BeamOutcome { NOT_SHIELDED, BLOCKED, PARRIED, REFLECTED }

    private ProtegoSpell() {
    }

    private static Holder<MobEffect> shield() {
        return ExampleMod.PROTEGO_EFFECT.getHolder().orElseThrow();
    }

    public static void raise(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.PROTEGO);
        long now = target.level().getGameTime();
        target.addEffect(new MobEffectInstance(shield(), ProtegoRules.SHIELD_TICKS, 0));
        CompoundTag data = target.getPersistentData();
        data.putLong(TAG_RAISED_AT, now);
        data.putLong(TAG_UNTIL, now + ProtegoRules.SHIELD_TICKS);
        data.putInt(TAG_RANK, rank);
        data.remove(TAG_CASTER);
        data.remove(TAG_REFUND);
        if (CombatEffects.responsible(caster) instanceof ServerPlayer player) {
            data.putUUID(TAG_CASTER, player.getUUID());
            float refund = ProtegoRules.refundFraction(rank)
                    * SpellCosts.manaCost(player, player.getMainHandItem(), SpellType.PROTEGO);
            if (refund > 0.0f && !player.isCreative()) {
                data.putFloat(TAG_REFUND, refund);
            }
        }
    }

    /** A ranked shield: raised by {@link #raise}, still inside its 60 ticks, and not Protego Maxima's amplifier-3 bubble. */
    public static boolean isRankedShield(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG_UNTIL) || entity.level().getGameTime() >= data.getLong(TAG_UNTIL)) {
            return false;
        }
        MobEffectInstance instance = entity.getEffect(shield());
        return instance != null && instance.getAmplifier() == 0;
    }

    public static BeamOutcome onBeamHit(LivingEntity holder, SpellBeamEntity beam) {
        if (!(holder.level() instanceof ServerLevel level) || !isRankedShield(holder)) {
            return BeamOutcome.NOT_SHIELDED;
        }
        SpellType spell = beam.getSpellType();
        if (spell == SpellType.AVADA_KEDAVRA || spell == SpellType.HEAL || spell == SpellType.PROTEGO) {
            return BeamOutcome.NOT_SHIELDED;
        }
        Vec3 look = holder.getLookAngle();
        Vec3 travel = beam.getDeltaMovement();
        if (!ProtegoRules.isFront(look.x, look.z, -travel.x, -travel.z)) {
            return BeamOutcome.NOT_SHIELDED; // from behind: the shield faces the other way
        }
        CompoundTag data = holder.getPersistentData();
        int rank = data.getInt(TAG_RANK);
        boolean unforgivable = spell == SpellType.CRUCIO || spell == SpellType.IMPERIO;
        if (!unforgivable && ProtegoRules.inParryWindow(data.getLong(TAG_RAISED_AT), level.getGameTime(), rank)) {
            refundOnce(level, data);
            int mode = ProtegoRules.parryMode(rank);
            if (mode != ProtegoRules.PARRY_DISSOLVE && ProtegoRules.canReflect(unforgivable, beam.isReflected())) {
                reflect(level, holder, beam, mode);
                parryFeedback(level, holder, true);
                return BeamOutcome.REFLECTED;
            }
            parryFeedback(level, holder, false);
            return BeamOutcome.PARRIED;
        }
        // Outside the window, or an Unforgivable: the shield takes the beam and breaks.
        holder.removeEffect(shield());
        data.remove(TAG_UNTIL);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.CRIT, holder.getX(), holder.getY() + 1.0, holder.getZ(),
                20, 0.5, 0.5, 0.5, 0.1);
        return BeamOutcome.BLOCKED;
    }

    /** Ranked shield damage rule: blows with a position in front lose the block fraction; Master knocks melee back. */
    public static void onShieldedHurt(LivingHurtEvent event) {
        LivingEntity holder = event.getEntity();
        DamageSource source = event.getSource();
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return; // falls, bleeding, starvation: no direction, so a one-sided shield can't catch them
        }
        Vec3 look = holder.getLookAngle();
        if (!ProtegoRules.isFront(look.x, look.z, from.x - holder.getX(), from.z - holder.getZ())) {
            return;
        }
        int rank = holder.getPersistentData().getInt(TAG_RANK);
        event.setAmount(ProtegoRules.reducedDamage(event.getAmount(), rank));
        holder.level().playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
        if (ProtegoRules.knocksBackMelee(rank)
                && source.getDirectEntity() instanceof LivingEntity attacker
                && attacker == source.getEntity()
                && attacker != holder) {
            attacker.knockback(ProtegoRules.MASTER_MELEE_KNOCKBACK, holder.getX() - attacker.getX(), holder.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }
    }

    private static void refundOnce(ServerLevel level, CompoundTag data) {
        if (!data.contains(TAG_REFUND) || !data.hasUUID(TAG_CASTER)) {
            return;
        }
        float refund = data.getFloat(TAG_REFUND);
        data.remove(TAG_REFUND);
        ServerPlayer caster = level.getServer().getPlayerList().getPlayer(data.getUUID(TAG_CASTER));
        if (caster != null) {
            SupportMana.add(caster, refund);
        }
    }

    private static void reflect(ServerLevel level, LivingEntity holder, SpellBeamEntity beam, int mode) {
        Vec3 direction = null;
        Entity original = beam.getOwner();
        if (mode == ProtegoRules.PARRY_AT_CASTER && original != null && original.isAlive()
                && original.level() == level && original != holder) {
            Vec3 toCaster = original.getEyePosition().subtract(holder.getEyePosition());
            if (toCaster.lengthSqr() > 1.0e-4) {
                direction = toCaster.normalize();
            }
        }
        if (direction == null) {
            float yaw = ProtegoRules.randomReflectYaw(holder.getYRot(), level.random.nextDouble());
            float pitch = (float) ((level.random.nextDouble() * 2.0 - 1.0) * ProtegoRules.RANDOM_REFLECT_PITCH_DEGREES);
            direction = Vec3.directionFromRotation(pitch, yaw);
        }
        // A fresh beam owned by the reflector: it starts at the holder's eyes and can't hit them on the way out.
        SpellBeamEntity copy = new SpellBeamEntity(level, holder, beam.getSpellType());
        copy.setCharge(beam.getCharge());
        copy.setElderWandCast(beam.isElderWandCast());
        copy.markReflected();
        float speed = (float) Math.max(1.0, beam.getDeltaMovement().length());
        copy.shoot(direction.x, direction.y, direction.z, speed, 0.0f);
        level.addFreshEntity(copy);
    }

    private static void parryFeedback(ServerLevel level, LivingEntity holder, boolean reflected) {
        Vec3 front = holder.getEyePosition().add(holder.getLookAngle().scale(0.8));
        level.sendParticles(ParticleTypes.END_ROD, front.x, front.y, front.z, reflected ? 18 : 10, 0.25, 0.25, 0.25, 0.04);
        level.sendParticles(ParticleTypes.FLASH, front.x, front.y, front.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, reflected ? 1.7f : 1.4f);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.6f);
    }
}
```

- [ ] **Step 3: Wire the cast.** In `SpellType.java`, replace:
```java
            case PROTEGO -> target.addEffect(new MobEffectInstance(ExampleMod.PROTEGO_EFFECT.getHolder().get(), 100, 0));
```
with:
```java
            case PROTEGO -> com.hogwartsmagic.spell.support.ProtegoSpell.raise(target, caster);
```

- [ ] **Step 4: The reflected flag on beams.** In `SpellBeamEntity.java`:

After:
```java
    private double castOriginZ = Double.NaN;
```
add:
```java
    /** Sent back by a Protego parry: never reflected again, and its new owner earns no mastery XP from it. */
    private boolean reflected;
```
After the `isElderWandCast()` method:
```java
    public boolean isElderWandCast() {
        return this.getEntityData().get(DATA_ELDER_WAND);
    }
```
add:
```java

    public void markReflected() {
        this.reflected = true;
    }

    public boolean isReflected() {
        return this.reflected;
    }
```
In `addAdditionalSaveData`, after `        tag.putBoolean("ElderWandCast", isElderWandCast());` add:
```java
        tag.putBoolean("Reflected", this.reflected);
```
In `readAdditionalSaveData`, replace:
```java
        if (tag.contains("ElderWandCast")) {
            setElderWandCast(tag.getBoolean("ElderWandCast"));
        }
```
with:
```java
        if (tag.contains("ElderWandCast")) {
            setElderWandCast(tag.getBoolean("ElderWandCast"));
        }
        this.reflected = tag.getBoolean("Reflected");
```
In `onHit`, replace:
```java
                if (getOwner() instanceof ServerPlayer masteryCaster) {
```
with:
```java
                if (getOwner() instanceof ServerPlayer masteryCaster && !this.reflected) {
```

- [ ] **Step 5: The beam intercept.** In `SpellBeamEntity.onHit`, replace:
```java
                // Intercept spell if the target has an active Protego shield
                if (living.hasEffect(com.hogwartsmagic.ExampleMod.PROTEGO_EFFECT.getHolder().get()) && spell != SpellType.AVADA_KEDAVRA && spell != SpellType.HEAL) {
```
with:
```java
                // A player's ranked Protego first: parry window (dissolve or reflect), or the shield breaks.
                com.hogwartsmagic.spell.support.ProtegoSpell.BeamOutcome parry =
                        com.hogwartsmagic.spell.support.ProtegoSpell.onBeamHit(living, this);
                if (parry != com.hogwartsmagic.spell.support.ProtegoSpell.BeamOutcome.NOT_SHIELDED) {
                    hitSuccessful = false; // stopped at the shield; a reflected copy is already in flight
                } else if (!com.hogwartsmagic.spell.support.ProtegoSpell.isRankedShield(living)
                        && living.hasEffect(com.hogwartsmagic.ExampleMod.PROTEGO_EFFECT.getHolder().get()) && spell != SpellType.AVADA_KEDAVRA && spell != SpellType.HEAL) {
                    // NPC layer shields and Protego Maxima keep the old rule: the shield eats the beam.
```
(The old block body and its `} else {` normal-hit branch stay as they are. A ranked shield hit from behind returns `NOT_SHIELDED`, skips the old branch because it is ranked, and lands as a normal hit.)

- [ ] **Step 6: The damage rule.** In `ModEvents.onLivingHurt`, replace:
```java
        if (event.getEntity().hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            // Cancel the damage
            event.setCanceled(true);
```
with:
```java
        if (com.hogwartsmagic.spell.support.ProtegoSpell.isRankedShield(event.getEntity())) {
            // A player's Protego (Spell Mastery): blunts blows from the front, never cancels them outright.
            com.hogwartsmagic.spell.support.ProtegoSpell.onShieldedHurt(event);
        } else if (event.getEntity().hasEffect(ExampleMod.PROTEGO_EFFECT.getHolder().get())) {
            // Cancel the damage
            event.setCanceled(true);
```

- [ ] **Step 7: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.protego.desc": "Creates a magical shield that reduces damage and reflects projectiles.",
```
with:
```json
  "spell.hogwartsmagic.protego.desc": "A 3-second shield that faces where you look and blunts blows from the front (40%, up to 75% at Master). Time it: a spell that hits in the first moment is parried. Adept reflects it at random, Expert back at its caster, Master refunds half the mana and knocks melee attackers back. The Unforgivables are never reflected.",
```

- [ ] **Step 8: Build, test and boot a dedicated server** (new beam spawn path, hurt hook)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `ProtegoSpell`, `SupportMana` or any `client` class.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/SupportMana.java src/main/java/com/hogwartsmagic/spell/support/ProtegoSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Turn Protego into a parry that reflects spells by rank" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Expelliarmus (silenced, Elder Wand allegiance)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`EXPELLIARMUS` case)
- Modify: `src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java` (`dropCustomDeathLoot`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ExpelliarmusRules` (Task 1); `DisableSupport.casterRank` (Phase 3); `CombatEffects.applySilenced/responsible` (Phase 2); `DisarmableWandUser`, `DuelManager.onDisarmed(LivingEntity)` (existing)
- Produces: `ExpelliarmusSpell.apply(LivingEntity target, @Nullable Entity caster)`, `ExpelliarmusSpell.ELDER_WAND_WON_TAG = "hogwartsmagic:elder_wand_won"`

- [ ] **Step 1: Create `ExpelliarmusSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.DisarmableWandUser;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Expelliarmus takes the wand, not just the item: the main-hand item drops, NPC casters go after
 * their wand (RetrieveDroppedWandGoal), and players and NPC casters are silenced ("disarmed"). A
 * player's dropped item can't be picked back up while the silence lasts. At Master the Elder Wand
 * changes allegiance: disarming its holder sends it to you, and disarming Voldemort wins you one
 * (once; he then drops none on death). Common code; callers are server side.
 */
public final class ExpelliarmusSpell {
    public static final String ELDER_WAND_WON_TAG = ExampleMod.MODID + ":elder_wand_won";

    private ExpelliarmusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.EXPELLIARMUS);
        Entity owner = CombatEffects.responsible(caster);
        int silenceTicks = ExpelliarmusRules.silenceTicks(rank);
        ServerPlayer winner = owner instanceof ServerPlayer player && player != target
                && ExpelliarmusRules.winsElderWand(rank) ? player : null;

        ItemStack held = target.getMainHandItem();
        if (!held.isEmpty()) {
            ItemStack taken = held.copy();
            target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (winner != null && taken.is(ExampleMod.ELDER_WAND.get())) {
                claimElderWand(winner, taken, target);
            } else {
                ItemEntity dropped = target.spawnAtLocation(taken);
                if (dropped != null && target instanceof Player) {
                    dropped.setPickUpDelay(silenceTicks);
                }
            }
        }
        if (winner != null && target instanceof VoldemortEntity
                && !target.getPersistentData().getBoolean(ELDER_WAND_WON_TAG)) {
            target.getPersistentData().putBoolean(ELDER_WAND_WON_TAG, true);
            claimElderWand(winner, new ItemStack(ExampleMod.ELDER_WAND.get()), target);
        }

        // NPC wand-wielders enter a SEEKING_WAND state: their top-priority AI becomes
        // running to and picking up the dropped wand. See RetrieveDroppedWandGoal.
        if (target instanceof DisarmableWandUser disarmable) {
            disarmable.setDisarmed(true);
        }
        // A disarm during a formal duel decides it, win or lose.
        com.hogwartsmagic.duel.DuelManager.onDisarmed(target);
        if (owner != null) {
            target.knockback(ExpelliarmusRules.knockback(rank), owner.getX() - target.getX(), owner.getZ() - target.getZ());
        }
        if (target instanceof Player || target instanceof DisarmableWandUser || target instanceof VoldemortEntity) {
            CombatEffects.applySilenced(target, silenceTicks, caster);
        }
    }

    private static void claimElderWand(ServerPlayer winner, ItemStack wand, LivingEntity from) {
        if (!winner.getInventory().add(wand)) {
            winner.drop(wand, false);
        }
        winner.displayClientMessage(Component.translatable("message.hogwartsmagic.expelliarmus.elder_wand",
                from.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        winner.level().playSound(null, winner.getX(), winner.getY(), winner.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }
}
```

- [ ] **Step 2: Wire the hit.** In `SpellType.java`, replace the whole case:
```java
            case EXPELLIARMUS -> {
                if (!target.getMainHandItem().isEmpty()) {
                    target.spawnAtLocation(target.getMainHandItem().copy());
                    target.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                }
                // NPC wand-wielders enter a SEEKING_WAND state: their top-priority AI becomes
                // running to and picking up the dropped wand. See RetrieveDroppedWandGoal.
                if (target instanceof com.hogwartsmagic.entity.DisarmableWandUser disarmable) {
                    disarmable.setDisarmed(true);
                }
                // A disarm during a formal duel decides it — win or lose.
                if (!target.level().isClientSide) {
                    com.hogwartsmagic.duel.DuelManager.onDisarmed(target);
                }
                // Knockback
                if (caster != null) {
                    target.knockback(0.5, caster.getX() - target.getX(), caster.getZ() - target.getZ());
                }
                // Disarmed effect (2-3 seconds) - Mining fatigue as a placeholder for "cannot hold items"
                // but usually, we'd need a custom effect to truly prevent holding.
                // For now, let's use weakness and slowness.
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            }
```
with:
```java
            case EXPELLIARMUS -> com.hogwartsmagic.spell.support.ExpelliarmusSpell.apply(target, caster);
```

- [ ] **Step 3: Voldemort's death drop respects allegiance.** In `VoldemortEntity.java`, replace:
```java
        this.spawnAtLocation(new ItemStack(ExampleMod.ELDER_WAND.get()));
```
with:
```java
        // The Elder Wand already answers to whoever disarmed him at Master rank; they hold it.
        if (!this.getPersistentData().getBoolean(com.hogwartsmagic.spell.support.ExpelliarmusSpell.ELDER_WAND_WON_TAG)) {
            this.spawnAtLocation(new ItemStack(ExampleMod.ELDER_WAND.get()));
        }
```

- [ ] **Step 4: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.expelliarmus.desc": "Knocks items out of a target's hand and deals minor knockback.",
```
with:
```json
  "spell.hogwartsmagic.expelliarmus.desc": "Knocks the item out of a target's hand and silences wand-users for 2 seconds (up to 4 at Master); a disarmed player can't grab it back until then. At Master, disarming the Elder Wand's holder, or Voldemort himself, makes the Elder Wand yours.",
```
Then directly after `  "gui.hogwartsmagic.wand_radial.silenced": "Silenced: no spells",` insert:
```json
  "message.hogwartsmagic.expelliarmus.elder_wand": "You disarmed %s. The Elder Wand's allegiance passes to you.",
```

- [ ] **Step 5: Check, build and test**

Run: `grep -n "MobEffects.WEAKNESS, 60, 1" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Expelliarmus silence wand-users and win the Elder Wand at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Revelio (reach by rank, Master reveals disguises and cloaks)

Below Master nothing changes in kind: a direct hit still dispels invisibility, a worn cloak (briefly) and a Polyjuice disguise (`ModEvents.dispelIllusionsAndInvisibility`), and a block hit still exposes Cave Inimicum wards and highlights ores and chests, now with rank-scaled radii. At Master every player within 20 blocks of the impact is revealed without the disguise being broken: their clients draw the real player (true skin and name tag) instead of the Polyjuice surrogate for 100 ticks, and a worn cloak drops for 100 ticks.

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/RevelioSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (radii, Master reveal on block and living hits)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`revealInvisibilityCloak` overload)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`RevealDisguisePacket`)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`
- Modify: `src/main/java/com/hogwartsmagic/client/PolyjuiceClientState.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` (`onRenderPlayerPre`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `RevelioRules` (Task 1); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible` (Phase 2); `PolyjuiceData.hasActive(Player)`, `InvisibilityCloakItem.isCloakActive(CompoundTag, long)`, `ModEvents.dispelIllusionsAndInvisibility(LivingEntity)` (existing)
- Produces:
  - `RevelioSpell.revealHidden(ServerLevel level, Vec3 center, @Nullable Entity caster)` → `int` (entities revealed; 0 below Master)
  - `ModEvents.revealInvisibilityCloak(ServerPlayer player, int ticks)` (the one-argument form keeps its signature and uses the config ticks)
  - `SpellPackets.RevealDisguisePacket(UUID playerId, int ticks)` (S2C)
  - `PolyjuiceClientState.reveal(UUID playerId, long untilGameTime)`, `PolyjuiceClientState.isRevealed(UUID playerId, long gameTime)` → `boolean`

- [ ] **Step 1: Cloak reveal with an explicit time.** In `ModEvents.java`, replace:
```java
    public static void revealInvisibilityCloak(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long gameTime = player.level().getGameTime();
        if (!InvisibilityCloakItem.isCloakActive(modData, gameTime)) {
            return;
        }

        long revealUntil = gameTime + Config.getInvisibilityCloakRevealTicks();
```
with:
```java
    public static void revealInvisibilityCloak(ServerPlayer player) {
        revealInvisibilityCloak(player, Config.getInvisibilityCloakRevealTicks());
    }

    /** Reveals a cloaked player for {@code ticks} (Revelio at Master uses its own, longer window). */
    public static void revealInvisibilityCloak(ServerPlayer player, int ticks) {
        net.minecraft.nbt.CompoundTag persisted = player.getPersistentData().getCompound(PlayerMagicData.PLAYER_PERSISTED);
        net.minecraft.nbt.CompoundTag modData = persisted.getCompound(ExampleMod.MODID);
        long gameTime = player.level().getGameTime();
        if (!InvisibilityCloakItem.isCloakActive(modData, gameTime)) {
            return;
        }

        long revealUntil = gameTime + Math.max(1, ticks);
```

- [ ] **Step 2: The packet.** In `SpellPackets.java`, in the registration method, directly after:
```java
                .decoder(buf -> new UseTomePacket(buf.readBoolean(), buf.readUtf(64)))
                .consumerMainThread(UseTomePacket::handle)
                .add();
```
add:
```java
        CHANNEL.messageBuilder(RevealDisguisePacket.class)
                .encoder((p, buf) -> {
                    buf.writeUUID(p.playerId);
                    buf.writeVarInt(p.ticks);
                })
                .decoder(buf -> new RevealDisguisePacket(buf.readUUID(), buf.readVarInt()))
                .consumerMainThread(RevealDisguisePacket::handle)
                .add();
```
Directly above:
```java
    /** Server to client: open the Tome of Mastery picker with the spells the server says are eligible. */
```
add:
```java
    /** Server to client: Revelio at Master shows {@code playerId} undisguised for {@code ticks}. */
    public record RevealDisguisePacket(UUID playerId, int ticks) {
        public static void handle(RevealDisguisePacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                // Server-to-client only: ignore anything a client sends.
                ctx.setPacketHandled(true);
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("revealDisguise", p));
        }
    }

```

- [ ] **Step 3: Create `RevelioSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.InvisibilityCloakItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.polyjuice.PolyjuiceData;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Revelio at Master: everything hidden within 20 blocks of the impact shows itself for 5 seconds.
 * A Polyjuice disguise is not broken (a direct hit still breaks it, as at every rank); instead every
 * client watching that player draws the real player for the window. A worn Invisibility Cloak drops
 * for the window. Invisible mobs are dispelled. Server only.
 */
public final class RevelioSpell {
    private RevelioSpell() {
    }

    public static int revealHidden(ServerLevel level, Vec3 center, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.REVELIO);
        if (!RevelioRules.revealsDisguises(rank)) {
            return 0;
        }
        Entity owner = CombatEffects.responsible(caster);
        double radius = RevelioRules.MASTER_RADIUS;
        long now = level.getGameTime();
        int revealed = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e != owner && e.distanceToSqr(center) <= radius * radius)) {
            boolean shown = false;
            if (living instanceof ServerPlayer player) {
                if (PolyjuiceData.hasActive(player)) {
                    SpellPackets.CHANNEL.send(new SpellPackets.RevealDisguisePacket(player.getUUID(), RevelioRules.REVEAL_TICKS),
                            PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
                    shown = true;
                }
                if (InvisibilityCloakItem.isCloakActive(PlayerMagicData.getModData(player), now)) {
                    ModEvents.revealInvisibilityCloak(player, RevelioRules.REVEAL_TICKS);
                    shown = true;
                }
            } else {
                shown = ModEvents.dispelIllusionsAndInvisibility(living);
            }
            if (shown) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, RevelioRules.REVEAL_TICKS, 0, false, false));
                level.sendParticles(ParticleTypes.GLOW, living.getX(), living.getY() + 1.0, living.getZ(),
                        12, 0.3, 0.5, 0.3, 0.02);
                revealed++;
            }
        }
        if (revealed > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        return revealed;
    }
}
```

- [ ] **Step 4: Rank-scaled radii and the Master reveal in the beam.** In `SpellBeamEntity.java`, delete the two constant lines:
```java
    private static final int REVELIO_ORE_RADIUS = 12;
    private static final int REVELIO_CHEST_RADIUS = 8;
```
In `onHit`, in the Revelio block branch, replace:
```java
                    if (this.level() instanceof ServerLevel sl) {
                        revealNearbySecrets(sl, pos);
                    }
```
with:
```java
                    if (this.level() instanceof ServerLevel sl) {
                        revealNearbySecrets(sl, pos,
                                com.hogwartsmagic.spell.disable.DisableSupport.casterRank(getOwner(), SpellType.REVELIO));
                        com.hogwartsmagic.spell.support.RevelioSpell.revealHidden(sl, this.position(), getOwner());
                    }
```
In the living-hit branch, replace:
```java
                            if (spell == SpellType.REVELIO) {
                                com.hogwartsmagic.event.ModEvents.dispelIllusionsAndInvisibility(living);
                            }
```
with:
```java
                            if (spell == SpellType.REVELIO) {
                                com.hogwartsmagic.event.ModEvents.dispelIllusionsAndInvisibility(living);
                                if (this.level() instanceof ServerLevel revelioLevel) {
                                    com.hogwartsmagic.spell.support.RevelioSpell.revealHidden(revelioLevel, living.position(), getOwner());
                                }
                            }
```
Replace the head of `revealNearbySecrets`:
```java
    private void revealNearbySecrets(ServerLevel level, BlockPos origin) {
        for (int x = -REVELIO_ORE_RADIUS; x <= REVELIO_ORE_RADIUS; x++) {
            for (int y = -REVELIO_ORE_RADIUS; y <= REVELIO_ORE_RADIUS; y++) {
                for (int z = -REVELIO_ORE_RADIUS; z <= REVELIO_ORE_RADIUS; z++) {
                    BlockPos targetPos = origin.offset(x, y, z);
                    if (origin.distSqr(targetPos) > (double) (REVELIO_ORE_RADIUS * REVELIO_ORE_RADIUS)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(targetPos);
                    if (isRevealableOre(state)) {
                        revealOre(level, origin, targetPos);
                    } else if (origin.distSqr(targetPos) <= (double) (REVELIO_CHEST_RADIUS * REVELIO_CHEST_RADIUS) && isRevealableChest(state)) {
```
with:
```java
    private void revealNearbySecrets(ServerLevel level, BlockPos origin, int rank) {
        int oreRadius = com.hogwartsmagic.spell.support.RevelioRules.oreRadius(rank);
        int chestRadius = com.hogwartsmagic.spell.support.RevelioRules.chestRadius(rank);
        for (int x = -oreRadius; x <= oreRadius; x++) {
            for (int y = -oreRadius; y <= oreRadius; y++) {
                for (int z = -oreRadius; z <= oreRadius; z++) {
                    BlockPos targetPos = origin.offset(x, y, z);
                    if (origin.distSqr(targetPos) > (double) (oreRadius * oreRadius)) {
                        continue;
                    }
                    if (!level.isLoaded(targetPos)) {
                        continue; // never load a chunk for a highlight
                    }

                    BlockState state = level.getBlockState(targetPos);
                    if (isRevealableOre(state)) {
                        revealOre(level, origin, targetPos);
                    } else if (origin.distSqr(targetPos) <= (double) (chestRadius * chestRadius) && isRevealableChest(state)) {
```
Run: `grep -n "REVELIO_ORE_RADIUS\|REVELIO_CHEST_RADIUS" src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`
Expected: no output.

- [ ] **Step 5: The client side.** In `PolyjuiceClientState.java`, directly after the `hasDisguise` method:
```java
    public static boolean hasDisguise(UUID playerId) {
        CompoundTag tag = ACTIVE_DISGUISES.get(playerId);
        return tag != null && !tag.isEmpty();
    }
```
add:
```java

    /** Client game time until which Revelio (Master) shows this player undisguised. */
    private static final Map<UUID, Long> REVEALED_UNTIL = new ConcurrentHashMap<>();

    public static void reveal(UUID playerId, long untilGameTime) {
        REVEALED_UNTIL.merge(playerId, untilGameTime, Math::max);
    }

    public static boolean isRevealed(UUID playerId, long gameTime) {
        Long until = REVEALED_UNTIL.get(playerId);
        if (until == null) {
            return false;
        }
        if (gameTime >= until) {
            REVEALED_UNTIL.remove(playerId);
            return false;
        }
        return true;
    }
```
In `ClientRuntimeBridge.java`, directly after:
```java
            case "openTomePicker" -> openTomePicker((SpellPackets.OpenTomePickerPacket) payload);
```
add:
```java
            case "revealDisguise" -> revealDisguise((SpellPackets.RevealDisguisePacket) payload);
```
and directly above `    private static void openTomePicker(SpellPackets.OpenTomePickerPacket packet) {` add:
```java
    private static void revealDisguise(SpellPackets.RevealDisguisePacket packet) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            PolyjuiceClientState.reveal(packet.playerId(), level.getGameTime() + Math.max(0, packet.ticks()));
        }
    }

```
In `ClientModEvents.java`, in `onRenderPlayerPre`, replace:
```java
            if (!PolyjuiceClientState.hasDisguise(event.getEntity().getUUID())) {
                return;
            }
```
with:
```java
            if (!PolyjuiceClientState.hasDisguise(event.getEntity().getUUID())
                    || PolyjuiceClientState.isRevealed(event.getEntity().getUUID(), event.getEntity().level().getGameTime())) {
                // No disguise, or Revelio (Master) is showing the real player: vanilla draws them, true name and all.
                return;
            }
```

- [ ] **Step 6: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.revelio.desc": "Reveals invisible mobs, highlights nearby ores through walls, and briefly breaks Cave Inimicum wards.",
```
with:
```json
  "spell.hogwartsmagic.revelio.desc": "Reveals invisible mobs, highlights nearby ores and chests through walls (farther with each rank), and briefly breaks Cave Inimicum wards. At Master it unmasks every Polyjuice disguise and Invisibility Cloak within 20 blocks for 5 seconds.",
```

- [ ] **Step 7: Build, test and boot a dedicated server** (new packet, client dispatch)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `RevelioSpell`, `RevealDisguisePacket` or any `client` class.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/RevelioSpell.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/PolyjuiceClientState.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Scale Revelio reach by rank and unmask disguises and cloaks at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Alohomora (iron doors, area, vault doors and hostile Ward barriers)

**Gringotts decision:** a vault door already opens to any empty hand, and the bank's security lives in `GringottsVaultDoorBlockEntity.beginOpening`: a non-goblin opener raises the alarm by 2 (guards target them, a response squad spawns at the exit) and the vault reseals. Master Alohomora is a remote hand: it calls `beginOpening` with the caster as the opener, so the alarm, the guards, the Polyjuice-goblin exemption and the reseal all work exactly as if the player had touched the door. Below Master the charm bounces off with a message. NPCs (rank 1) never open vaults.

**Ward decision:** a hostile Ward is one whose owner is not the caster and not on the caster's team. Its Protego Maxima barrier blocks projectiles at its boundary, including beams, so a Master Alohomora beam that reaches the barrier (or the Ward block itself) switches the barrier off for 100 ticks instead of being stopped. The Ward's illusion and Revelio field are untouched.

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (`ALOHOMORA` block branch)
- Modify: `src/main/java/com/hogwartsmagic/block/entity/CaveInimicumWardBlockEntity.java` (suppression)
- Modify: `src/main/java/com/hogwartsmagic/caveinimicum/CaveInimicumManager.java` (barrier checks)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `AlohomoraRules` (Task 1); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible` (Phase 2); `MasteryAwards.onBlockEffect` (Phase 1); `GringottsVaultDoorBlockEntity.beginOpening(ServerLevel, Vec3, @Nullable ServerPlayer)`, `DiagonArchwayBlock.enchant(ServerLevel, BlockPos, @Nullable ServerPlayer)` (existing)
- Produces:
  - `AlohomoraSpell.onBlockHit(ServerLevel level, BlockPos pos, BlockState state, Vec3 impact, @Nullable Entity caster)` → `boolean`
  - `AlohomoraSpell.tryUnlockBarrier(ServerLevel level, CaveInimicumWardBlockEntity ward, SpellBeamEntity beam)` → `boolean`
  - `CaveInimicumWardBlockEntity.suppressBarrier(ServerLevel level, int ticks)`, `CaveInimicumWardBlockEntity.isBarrierUp(long gameTime)` → `boolean`

- [ ] **Step 1: Barrier suppression on the Ward.** In `CaveInimicumWardBlockEntity.java`:

After the field line `    private boolean revelioActive;` add:
```java
    /** Game time until which the Protego Maxima barrier is down (Alohomora at Master). */
    private long barrierSuppressedUntil;
```
Replace:
```java
    public boolean shouldRenderProtegoBarrier(long gameTime) {
        return protegoMaximaActive && (!isActive() || isRevealed(gameTime));
    }
```
with:
```java
    public boolean shouldRenderProtegoBarrier(long gameTime) {
        return isBarrierUp(gameTime) && (!isActive() || isRevealed(gameTime));
    }

    /** The barrier is on and not switched off by an Alohomora. */
    public boolean isBarrierUp(long gameTime) {
        return protegoMaximaActive && gameTime >= barrierSuppressedUntil;
    }

    public void suppressBarrier(ServerLevel level, int ticks) {
        barrierSuppressedUntil = Math.max(barrierSuppressedUntil, level.getGameTime() + Math.max(1, ticks));
        outlineTicks = Math.max(outlineTicks, 40);
        setChanged();
        syncToClients();
    }
```
In the save method, after `        tag.putBoolean("RevelioActive", revelioActive);` add:
```java
        tag.putLong("BarrierSuppressedUntil", barrierSuppressedUntil);
```
In `loadAdditional`, after `        revelioActive = tag.getBoolean("RevelioActive");` add:
```java
        barrierSuppressedUntil = tag.getLong("BarrierSuppressedUntil");
```
(If `ServerLevel` is not imported in this file, use `net.minecraft.server.level.ServerLevel` in the signature.)

- [ ] **Step 2: Create `AlohomoraSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.DiagonArchwayBlock;
import com.hogwartsmagic.block.GringottsVaultDoorBlock;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Alohomora, the unlocking charm. Novice: wooden doors, trapdoors and fence gates. Adept: iron doors
 * and iron trapdoors too, which swing shut again after 100 ticks (an unloaded door is left open).
 * Expert: every openable door within 4 blocks. Master: a Gringotts vault door from range (through the
 * door's own beginOpening, so the bank's alarm still fires) and a hostile Ward's barrier, off for
 * 100 ticks. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AlohomoraSpell {
    private record PendingClose(ResourceKey<Level> dimension, BlockPos pos, long closeAt) {
    }

    private static final List<PendingClose> PENDING = new ArrayList<>();

    private AlohomoraSpell() {
    }

    public static boolean onBlockHit(ServerLevel level, BlockPos pos, BlockState state, Vec3 impact, @Nullable Entity caster) {
        Entity owner = CombatEffects.responsible(caster);
        if (state.getBlock() instanceof DiagonArchwayBlock) {
            // The wall behind the Leaky Cauldron. Arming it is what makes the taps
            // start counting; until then it is just a wall.
            return DiagonArchwayBlock.enchant(level, pos, owner instanceof ServerPlayer sp ? sp : null);
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ALOHOMORA);
        if (state.getBlock() instanceof GringottsVaultDoorBlock) {
            return openVault(level, pos, impact, owner, rank);
        }
        if (state.is(ExampleMod.WARD.get()) && level.getBlockEntity(pos) instanceof CaveInimicumWardBlockEntity ward) {
            return owner instanceof ServerPlayer player && suppressWard(level, ward, player, rank);
        }
        boolean opened = openOne(level, pos, state, owner, rank);
        int radius = AlohomoraRules.areaRadius(rank);
        if (radius > 0) {
            opened |= openAround(level, pos, radius, owner, rank) > 0;
        }
        return opened;
    }

    /** A Master Alohomora reaching a hostile barrier switches it off instead of being stopped by it. */
    public static boolean tryUnlockBarrier(ServerLevel level, CaveInimicumWardBlockEntity ward, SpellBeamEntity beam) {
        if (beam.getSpellType() != SpellType.ALOHOMORA || !(beam.getOwner() instanceof ServerPlayer player)) {
            return false;
        }
        int rank = DisableSupport.casterRank(player, SpellType.ALOHOMORA);
        if (!suppressWard(level, ward, player, rank)) {
            return false;
        }
        beam.discard();
        MasteryAwards.onBlockEffect(player, SpellType.ALOHOMORA);
        return true;
    }

    private static boolean openOne(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity owner, int rank) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock door) {
            if (state.getValue(DoorBlock.OPEN)) {
                return false;
            }
            boolean iron = !door.type().canOpenByHand();
            if (iron && !AlohomoraRules.opensIron(rank)) {
                return false;
            }
            door.setOpen(owner, level, state, pos, true); // plays the door's own sound
            if (iron) {
                PENDING.add(new PendingClose(level.dimension(), pos.immutable(), level.getGameTime() + AlohomoraRules.IRON_OPEN_TICKS));
            }
            return true;
        }
        if (block instanceof TrapDoorBlock) {
            if (state.getValue(TrapDoorBlock.OPEN)) {
                return false;
            }
            boolean iron = state.is(Blocks.IRON_TRAPDOOR);
            if (iron && !AlohomoraRules.opensIron(rank)) {
                return false;
            }
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), Block.UPDATE_CLIENTS);
            level.levelEvent(null, iron ? 1037 : 1007, pos, 0);
            if (iron) {
                PENDING.add(new PendingClose(level.dimension(), pos.immutable(), level.getGameTime() + AlohomoraRules.IRON_OPEN_TICKS));
            }
            return true;
        }
        if (block instanceof FenceGateBlock) {
            if (state.getValue(FenceGateBlock.OPEN)) {
                return false;
            }
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_CLIENTS);
            level.levelEvent(null, 1008, pos, 0);
            return true;
        }
        return false;
    }

    private static int openAround(ServerLevel level, BlockPos center, int radius, @Nullable Entity owner, int rank) {
        int opened = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (opened >= AlohomoraRules.MAX_AREA_OPENINGS) {
                break;
            }
            if (pos.equals(center) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue; // each door once, by its lower half
            }
            if (openOne(level, pos.immutable(), state, owner, rank)) {
                opened++;
            }
        }
        return opened;
    }

    private static boolean openVault(ServerLevel level, BlockPos pos, Vec3 impact, @Nullable Entity owner, int rank) {
        if (!(owner instanceof ServerPlayer player)) {
            return false;
        }
        if (!AlohomoraRules.opensVaults(rank)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.alohomora.vault")
                    .withStyle(ChatFormatting.GOLD), true);
            return false;
        }
        // The caster is the opener: a non-goblin raises the alarm and the vault reseals, as by hand.
        return level.getBlockEntity(pos) instanceof GringottsVaultDoorBlockEntity door
                && door.beginOpening(level, impact, player);
    }

    private static boolean suppressWard(ServerLevel level, CaveInimicumWardBlockEntity ward, ServerPlayer player, int rank) {
        if (!AlohomoraRules.suppressesWards(rank) || !ward.isBarrierUp(level.getGameTime()) || !isHostile(level, ward, player)) {
            return false;
        }
        ward.suppressBarrier(level, AlohomoraRules.WARD_SUPPRESS_TICKS);
        BlockPos pos = ward.getBlockPos();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 16, 0.5, 0.6, 0.5, 0.02);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.9f, 1.6f);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.alohomora.ward")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    /** Someone else's Ward, and not a team-mate's. An unowned Ward is nobody's enemy. */
    private static boolean isHostile(ServerLevel level, CaveInimicumWardBlockEntity ward, ServerPlayer player) {
        UUID ownerId = ward.getOwnerUuid();
        if (ownerId == null || ownerId.equals(player.getUUID())) {
            return false;
        }
        ServerPlayer wardOwner = level.getServer().getPlayerList().getPlayer(ownerId);
        return wardOwner == null || !wardOwner.isAlliedTo(player);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || PENDING.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<PendingClose> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingClose close = it.next();
            if (close.dimension() != level.dimension() || now < close.closeAt()) {
                continue;
            }
            it.remove();
            if (!level.isLoaded(close.pos())) {
                continue; // left open: never load a chunk to shut a door
            }
            BlockState state = level.getBlockState(close.pos());
            if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.OPEN)) {
                door.setOpen(null, level, state, close.pos(), false);
            } else if (state.is(Blocks.IRON_TRAPDOOR) && state.getValue(TrapDoorBlock.OPEN)) {
                level.setBlock(close.pos(), state.setValue(TrapDoorBlock.OPEN, false), Block.UPDATE_CLIENTS);
                level.levelEvent(null, 1036, close.pos(), 0);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
```
If `door.type()` does not compile in this mapping, replace `boolean iron = !door.type().canOpenByHand();` with `boolean iron = state.is(Blocks.IRON_DOOR);` and note it in the commit message.

- [ ] **Step 3: The beam's block branch.** In `SpellBeamEntity.onHit`, replace the whole Alohomora branch:
```java
                } else if (spell == SpellType.ALOHOMORA) {
                    if (state.getBlock() instanceof com.hogwartsmagic.block.DiagonArchwayBlock) {
                        // The wall behind the Leaky Cauldron. Arming it is what makes the taps
                        // start counting; until then it is just a wall.
                        hitSuccessful = this.level() instanceof ServerLevel sl
                                && com.hogwartsmagic.block.DiagonArchwayBlock.enchant(sl, pos,
                                        getOwner() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
                    } else if (state.getBlock() instanceof DoorBlock door) {
                        if (!state.getValue(DoorBlock.OPEN)) {
                            door.setOpen(getOwner(), this.level(), state, pos, true);
                            this.level().levelEvent(null, 1005, pos, 0);
                            hitSuccessful = true;
                        }
                    } else if (state.getBlock() instanceof TrapDoorBlock trapdoor) {
                        if (!state.getValue(TrapDoorBlock.OPEN)) {
                            this.level().setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 2);
                            this.level().levelEvent(null, 1007, pos, 0);
                            hitSuccessful = true;
                        }
                    } else if (state.getBlock() instanceof FenceGateBlock gate) {
                        if (!state.getValue(FenceGateBlock.OPEN)) {
                            this.level().setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 2);
                            this.level().levelEvent(null, 1008, pos, 0);
                            hitSuccessful = true;
                        }
                    }
```
with:
```java
                } else if (spell == SpellType.ALOHOMORA) {
                    // Doors, trapdoors and gates; iron from Adept; all around from Expert; at Master a
                    // Gringotts vault door or a hostile Ward. See AlohomoraSpell.
                    if (this.level() instanceof ServerLevel alohomoraLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.AlohomoraSpell.onBlockHit(
                                alohomoraLevel, pos, state, this.position(), getOwner());
                    }
```
(The following `} else if (spell == SpellType.GLACIUS) {` line stays. Unused `DoorBlock`, `TrapDoorBlock` and `FenceGateBlock` imports may remain; they don't fail the build.)

- [ ] **Step 4: The barrier answers to the suppression and to Alohomora.** In `CaveInimicumManager.java`, in `tickBarrierWards`, replace:
```java
            if (!ward.isProtegoMaximaActive()) {
                continue;
            }
            handleBarrierProjectiles(ward);
```
with:
```java
            if (!ward.isBarrierUp(level.getGameTime())) {
                continue; // off, or switched off for a while by a Master Alohomora
            }
            handleBarrierProjectiles(ward);
```
In `handleBarrierProjectiles`, replace:
```java
            blockProjectileAtBarrier(projectile, previousInside ? previous : current);
```
with:
```java
            if (projectile instanceof com.hogwartsmagic.entity.SpellBeamEntity beam
                    && com.hogwartsmagic.spell.support.AlohomoraSpell.tryUnlockBarrier(level, ward, beam)) {
                continue;
            }
            blockProjectileAtBarrier(projectile, previousInside ? previous : current);
```

- [ ] **Step 5: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.alohomora.desc": "Unlocks doors, trapdoors, and fence gates.",
```
with:
```json
  "spell.hogwartsmagic.alohomora.desc": "Unlocks doors, trapdoors and fence gates. Adept opens iron doors too (they swing shut after 5 seconds); Expert opens every door within 4 blocks. At Master it opens a Gringotts vault door from afar (the goblins still notice) and switches off a hostile Ward's barrier for 5 seconds.",
```
Then directly after `  "gui.hogwartsmagic.wand_radial.silenced": "Silenced: no spells",` insert:
```json
  "message.hogwartsmagic.alohomora.vault": "Goblin locks don't answer to a schoolroom charm. Not yet.",
  "message.hogwartsmagic.alohomora.ward": "The Ward's barrier falters.",
```

- [ ] **Step 6: Build, test and boot a dedicated server** (new tick subscriber, block-entity save field)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `AlohomoraSpell`, `CaveInimicum` or any `client` class.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/AlohomoraSpell.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/block/entity/CaveInimicumWardBlockEntity.java src/main/java/com/hogwartsmagic/caveinimicum/CaveInimicumManager.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let Alohomora open iron and vault doors and drop hostile Ward barriers by rank" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Lumos (brightness, Maxima orbs, Solem)

**Controls decision:** Novice and Adept keep today's controls: right-click toggles the wand light (Lumos/Nox). Left-click also toggles at every rank (it joins Protego, Heal and Arresto Momentum as a self-cast). From Expert, right-click fires a Lumos beam (orbs on surfaces, light on whoever it hits, Solem at Master) and the toggle lives on left-click.

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/support/LumosSpell.java`
- Create: `src/main/resources/data/hogwartsmagic/tags/entity_type/lumos_solem_vulnerable.json`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`LUMOS` case)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (Lumos cast branch)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Lumos block branch, no chip damage)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (left-click self-cast helper)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `LumosRules` (Task 1); `LumosLightBlock.placeTimedLight/isTimedLight` (Task 2); `DisableSupport.casterRank`, `TerrainRules.canPlace` (Phase 3); `DamageSupport.scaled` (Phase 4); `CombatEffects.responsible` (Phase 2)
- Produces:
  - `LumosSpell.toggle(LivingEntity target, @Nullable Entity caster)`, `LumosSpell.onHit(LivingEntity target, @Nullable Entity caster)`, `LumosSpell.placeOrb(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`, `LumosSpell.SOLEM_VULNERABLE` (`TagKey<EntityType<?>>`)
  - `ModEvents.isLeftClickSelfCast(SpellType)` → `boolean` (private static; Phase 5b adds Reparo to it)

- [ ] **Step 1: The Solem tag**

`src/main/resources/data/hogwartsmagic/tags/entity_type/lumos_solem_vulnerable.json`:
```json
{
  "replace": false,
  "values": [
    "#minecraft:undead",
    "minecraft:spider",
    "minecraft:cave_spider",
    "hogwartsmagic:acromantula"
  ]
}
```

- [ ] **Step 2: Create `LumosSpell`**
```java
package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.LumosLightBlock;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Lumos. The wand light is the {@code lumos} effect (amplifier = brightness, read by the light tick in
 * ModEvents). From Expert the cast is a beam: on a surface it leaves a Lumos Maxima orb (a timed
 * LumosLightBlock, at most three per caster, the oldest goes first), on a creature it hands over the
 * light, and at Master it burns creatures of darkness (Lumos Solem). Server side.
 */
public final class LumosSpell {
    public static final TagKey<EntityType<?>> SOLEM_VULNERABLE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "lumos_solem_vulnerable"));
    private static final String TAG_ORBS = ExampleMod.MODID + ":lumos_orbs";

    private LumosSpell() {
    }

    private static Holder<MobEffect> light() {
        return ExampleMod.LUMOS_EFFECT.getHolder().orElseThrow();
    }

    /** Lumos / Nox on {@code target}, at the caster's brightness. */
    public static void toggle(LivingEntity target, @Nullable Entity caster) {
        if (target.hasEffect(light())) {
            target.removeEffect(light());
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.LUMOS);
        target.addEffect(new MobEffectInstance(light(), LumosRules.LIGHT_TICKS, LumosRules.amplifierForRank(rank), false, false, true));
    }

    /** applyHitEffect: a self-cast toggles; a beam burns at Master or hands over the light. */
    public static void onHit(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        if (CombatEffects.responsible(caster) == target) {
            toggle(target, caster);
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.LUMOS);
        if (LumosRules.solem(rank) && target.getType().is(SOLEM_VULNERABLE)) {
            target.hurt(target.damageSources().magic(), DamageSupport.scaled(caster, LumosRules.SOLEM_DAMAGE));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, LumosRules.SOLEM_BLIND_TICKS, 0));
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.8f);
            }
            return;
        }
        target.addEffect(new MobEffectInstance(light(), LumosRules.LIGHT_TICKS, LumosRules.amplifierForRank(rank), false, false, true));
    }

    /** Lumos Maxima: a floating light at {@code pos} for 5 minutes. Players at Expert and up only. */
    public static boolean placeOrb(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        int rank = SpellMasteryData.getRank(player, SpellType.LUMOS);
        if (!LumosRules.placesOrbs(rank) || !level.isLoaded(pos) || !TerrainRules.canPlace(level, pos, player)) {
            return false;
        }
        if (!LumosLightBlock.placeTimedLight(level, pos, LumosRules.ORB_LIGHT, LumosRules.ORB_TICKS)) {
            return false;
        }
        rememberOrb(player, level, pos);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.2, 0.2, 0.2, 0.03);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
        return true;
    }

    private static void rememberOrb(ServerPlayer player, ServerLevel level, BlockPos pos) {
        CompoundTag data = player.getPersistentData();
        ListTag orbs = data.getList(TAG_ORBS, Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putString("Dim", level.dimension().location().toString());
        entry.putLong("Pos", pos.asLong());
        orbs.add(entry);
        while (orbs.size() > LumosRules.MAX_ORBS) {
            CompoundTag oldest = orbs.getCompound(0);
            orbs.remove(0);
            ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(oldest.getString("Dim")));
            ServerLevel orbLevel = level.getServer().getLevel(dim);
            BlockPos orbPos = BlockPos.of(oldest.getLong("Pos"));
            if (orbLevel != null && orbLevel.isLoaded(orbPos) && LumosLightBlock.isTimedLight(orbLevel.getBlockState(orbPos))) {
                orbLevel.removeBlock(orbPos, false);
            }
        }
        data.put(TAG_ORBS, orbs);
    }
}
```

- [ ] **Step 3: Wire the hit.** In `SpellType.java`, replace:
```java
            case LUMOS -> target.addEffect(new MobEffectInstance(ExampleMod.LUMOS_EFFECT.getHolder().get(), 12000, 0));
```
with:
```java
            case LUMOS -> com.hogwartsmagic.spell.support.LumosSpell.onHit(target, caster);
```

- [ ] **Step 4: Right-click toggles only below Expert.** In `SpellPackets.CastSpellPacket.handle`, replace:
```java
            if (spell == SpellType.LUMOS) {
                // Toggle Lumos
                if (player.hasEffect(ExampleMod.LUMOS_EFFECT.getHolder().get())) {
                    player.removeEffect(ExampleMod.LUMOS_EFFECT.getHolder().get());
                } else {
                    player.addEffect(new MobEffectInstance(ExampleMod.LUMOS_EFFECT.getHolder().get(), 12000, 0, false, false, true));
                }
```
with:
```java
            if (spell == SpellType.LUMOS && !com.hogwartsmagic.spell.support.LumosRules.firesBeam(
                    com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell))) {
                // Novice and Adept: right-click toggles the wand light (Lumos / Nox). From Expert this
                // falls through to the beam below and the toggle moves to left-click (self-cast).
                com.hogwartsmagic.spell.support.LumosSpell.toggle(player, player);
```
(The rest of that block, the cloak reveal, the sound, the cooldown and `return;`, stays.)

- [ ] **Step 5: Orbs on surfaces, and no chip damage from a beam of light.** In `SpellBeamEntity.onHit`, directly above:
```java
                } else if (spell == SpellType.REPARO) {
```
insert:
```java
                } else if (spell == SpellType.LUMOS) {
                    // Lumos Maxima (Expert and up): a floating light orb where the beam lands.
                    if (this.level() instanceof ServerLevel lumosLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.support.LumosSpell.placeOrb(
                                lumosLevel, pos.relative(blockHit.getDirection()), getOwner());
                    }
```
Then replace:
```java
                            if (spell != SpellType.HEAL && spell != SpellType.AVADA_KEDAVRA && spell != SpellType.PROTEGO) {
```
with:
```java
                            if (spell != SpellType.HEAL && spell != SpellType.AVADA_KEDAVRA && spell != SpellType.PROTEGO
                                    && spell != SpellType.LUMOS) {
```

- [ ] **Step 6: Left-click self-cast list in one place.** In `ModEvents.java`, the two left-click handlers (`onLeftClick` and `onLeftClickBlock`) contain the same three lines. Replace both occurrences (use replace-all) of:
```java
            if (selected != null && (selected == com.hogwartsmagic.spell.SpellType.PROTEGO
                    || selected == com.hogwartsmagic.spell.SpellType.HEAL
                    || selected == com.hogwartsmagic.spell.SpellType.ARRESTO_MOMENTUM)) {
```
with:
```java
            if (selected != null && isLeftClickSelfCast(selected)) {
```
Then directly above the line `    public static void onLeftClick(PlayerInteractEvent.LeftClickEmpty event) {` and its `@SubscribeEvent` annotation, insert:
```java
    /** Spells a wand left-click casts on yourself (the server handles them with selfCastProtego). */
    private static boolean isLeftClickSelfCast(SpellType spell) {
        return spell == SpellType.PROTEGO
                || spell == SpellType.HEAL
                || spell == SpellType.ARRESTO_MOMENTUM
                || spell == SpellType.LUMOS;
    }

```
Run: `grep -n "isLeftClickSelfCast" src/main/java/com/hogwartsmagic/event/ModEvents.java`
Expected: three lines (the method and two calls).

- [ ] **Step 7: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.lumos.desc": "Toggles a magical light source that follows the caster.",
```
with:
```json
  "spell.hogwartsmagic.lumos.desc": "A light that follows you (brighter from Adept); cast again for Nox. Left-click always toggles it. From Expert, right-click casts Lumos Maxima: a floating orb of light where it lands (up to 3, for 5 minutes). At Master the beam is Lumos Solem, searing and blinding undead and spiders.",
```

- [ ] **Step 8: Build, test, validate and boot a dedicated server** (new tag, new cast path)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/data/hogwartsmagic/tags/entity_type/lumos_solem_vulnerable.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no `Couldn't load tag` line mentioning `hogwartsmagic:`; no stack trace mentioning `LumosSpell` or any `client` class.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/support/LumosSpell.java src/main/resources/data/hogwartsmagic/tags/entity_type/lumos_solem_vulnerable.json src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Give Lumos ranked brightness, Lumos Maxima orbs and Lumos Solem" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests and dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `spell.support`, `LumosLightBlock`, `RevealDisguisePacket` or any `client` class.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed). Use `/spellmastery setrank @s <spell> <rank>` to switch ranks.
1. Lumos (Novice), log out, log back in: no light block is left where you stood (`/fill ~-3 ~-3 ~-3 ~3 ~3 ~3 air replace hogwartsmagic:lumos_light` reports 0 or 1, the current one).
2. Lumos on, then `/kill @s`: after respawn, the old spot is dark within a second.
3. Lumos at Novice is visibly dimmer (level 12) than at Adept (15) in a dark room.
4. Heal (Novice) left-click at 10 health: +6. Heal while bleeding at Novice: the bleed stays; at Adept it's removed. Drink milk while bleeding: still bleeding.
5. Heal at Master on a Dolohov-cursed target: max health comes back.
6. Protego (Novice), get hit by a Stupefy just after raising it: the beam dissolves and the shield stays. Same beam 1s later: the shield breaks and the stun doesn't land. Same beam from behind: it lands.
7. Protego (Adept): a parried beam flies off at an angle. (Expert): it flies back at the caster. (Master): it flies back, about half the Protego mana comes back, and a zombie hitting you from the front is knocked back.
8. Protego never reflects Avada Kedavra (it hits) or Crucio (the shield breaks).
9. A zombie hits you from the front with Protego up (Novice): the damage is about 60% of normal; from behind, full.
10. Expelliarmus a player holding a wand: the wand drops, they're silenced (greyed wheel) 2s, and can't pick it up before then.
11. Expelliarmus (Master) a player or mob holding the Elder Wand: it lands in your inventory with the gold message. Disarm Voldemort at Master: you get an Elder Wand; kill him afterwards: no second Elder Wand drops.
12. Revelio (Novice) on stone: ores within 12 glow; (Master): within 20.
13. Revelio (Master) near a friend under Polyjuice: for 5s you see their real skin and name; the disguise is back afterwards. A friend wearing the cloak becomes visible for 5s.
14. Alohomora (Novice) on an iron door: nothing. (Adept): it opens and shuts itself after 5s. (Expert): every door in a corridor within 4 blocks opens.
15. Alohomora (Novice) on a Gringotts vault door: the "Goblin locks" message. (Master): the door opens from range and the bank alarm raises guards.
16. Alohomora (Master) at a second player's Ward with Protego Maxima active: the barrier drops for 5s (arrows pass through). The same cast at your own Ward, or a team-mate's, does nothing.
17. Lumos (Expert) right-click at a wall: an orb of light appears and sparkles; a fourth orb removes the first; orbs vanish after 5 minutes; left-click toggles the wand light.
18. Lumos (Master) beam on a zombie: it takes damage and is blinded; on a cow: the cow just glows with light.

- [ ] **Step 3: Record results.** Note any failed check and fix it in a follow-up commit whose message says what was wrong. Then tell the user Phase 5a is ready to push and Phase 5b can start.

---

## Self-Review

- **Spec coverage (roadmap Phase 5 and the controller's notes, first half):** Lumos light bug fixed first (T2, before any Lumos rank work in T8); Heal cleanses bleeding at Adept and cursed wound at Master through `CombatEffects.cureCursedWound` (T3); Protego parry window, random reflect at Adept, back at the caster at Expert, refund and melee knockback at Master, Unforgivables never reflected (T4); Expelliarmus silenced plus the existing drop, Elder Wand allegiance including the Voldemort drop (T5); Revelio Polyjuice true name and cloak for 5s at Master (T6); Alohomora iron doors closing after 5s at Adept, vault doors with Gringotts security intact and hostile Ward barrier off for 5s at Master (T7); Lumos Maxima orbs at Expert (max 3, 5 minutes) and Master damage plus blind to undead, spiders and Acromantulas (T8); numeric scaling for the rest (T1 tables); dedicated-server boots in T2, T4, T6, T7, T8 and T9. Reparo, Accio, Ascendio, Depulso and Apparition are Phase 5b.
- **Cut on purpose (Decision 1):** Heal's double strength on tamed animals and Hippogriffs, Expelliarmus Expert "item flies to you" and Master clash win, Revelio's Adept/Expert ore tiers (ores reveal at every rank as today), Alohomora's locked chests (none exist).
- **Placeholder scan:** no TBD. One conditional fallback is spelled out (`door.type()` → `Blocks.IRON_DOOR`).
- **Type consistency:** `DisableSupport.casterRank(Entity, SpellType)` everywhere; `ProtegoSpell.BeamOutcome` used in `SpellBeamEntity` with its four constants; `LumosLightBlock.placeTimedLight(ServerLevel, BlockPos, int, int)` is used by `ModEvents.addTemporaryRevelioLight` (T2) and `LumosSpell.placeOrb` (T8); `RevealDisguisePacket(UUID, int)` matches the bridge's `"revealDisguise"`; `SupportMana.add/trySpend` (T4) is what 5b's Reparo consumes; `isLeftClickSelfCast` (T8) is what 5b extends.
