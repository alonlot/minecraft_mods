# Spell Mastery Phase 4 (Damage Spells) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Diffindo, Sectumsempra, Dolohov's Curse, Confringo, Bombarda and Incendio stop being "the same hit with a different number". Each gets one bespoke Novice identity and one bespoke Master capstone from the roadmap, with numeric scaling at Adept and Expert: Diffindo cuts through armour and is the magic shears (Master cleaves a second enemy); Sectumsempra bleeds (Master tears every wound open); Dolohov's Curse takes max health (Master spreads on death); Confringo is a pure combat blast that ignites creatures (Master punishes brittle targets); Bombarda is the terrain breaker (Master: bigger radius, obsidian); Incendio is fire as a tool (Adept and up spread flames).

**Architecture:** Every per-rank number lives in a Minecraft-free `*Rules` class in `spell/damage/` (plus `BlastShape`, the sphere of block offsets), each with JUnit tests. One Minecraft-bound class per spell (`DiffindoSpell`, `SectumsempraSpell`, `DolohovSpell`, `ConfringoSpell`, `BombardaSpell` + `BombardaBlasts`, `IncendioSpell`) holds the hit behaviour and that spell's own Forge hooks; `SpellType.applyHitEffect` and the block-hit branches of `SpellBeamEntity.onHit` just delegate. Ranks come from Phase 3's `DisableSupport.casterRank` (players: mastery rank; NPCs: 1). Terrain changes go through Phase 3's `TerrainRules.canPlace` (fire, lighting) and a new `TerrainRules.canBreak` (ward, adventure mode, spawn protection, a `BlockEvent.BreakEvent` posted as the caster). Bombarda queues its blocks and breaks at most 64 per tick from a level-tick hook, only in loaded chunks, so a blast never loads a chunk or stalls a tick.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 4: Damage spells", "Decisions that change the design doc" (Decision 1: bespoke Novice identity + Master capstone only, Adept/Expert numeric) and "Global constraints". Numbers the roadmap leaves open come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` section "B. Damage spells". Where they disagree, the roadmap wins. The Incendio flame jet (design Expert/Master) is cut.

**Depends on Phases 2 and 3 (both must be merged first).** This plan calls, by exact signature:
- Phase 2: `CombatEffects.responsible(@Nullable Entity)`, `CombatEffects.has(LivingEntity, RegistryObject<MobEffect>)`, `CombatEffects.applyCursedWound(LivingEntity target, int ticks, int hearts, @Nullable Entity source)` → `boolean`; `BleedRules.stacks(int)`, `BleedRules.MAX_STACKS`; `CombatTags.ALLIED`; `ExampleMod.BLEEDING`, `ExampleMod.CURSED_WOUND`, `ExampleMod.BRITTLE` (`RegistryObject<MobEffect>`). The brittle hit itself (×(1 + 0.3 × level) on an explosion, then consumed) is Phase 2's `CombatEffects.onLivingHurt` at normal priority.
- Phase 3: `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int`, `DisableSupport.holder(RegistryObject<MobEffect>)` → `Holder<MobEffect>`, `DisableSupport.isEnemyOf(@Nullable Entity caster, LivingEntity candidate, LivingEntity mainTarget)` → `boolean`, `TerrainRules.canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`. Phase 3's `RankIndex` is package-private to `spell.disable`, so this plan adds its own package-private `RankIndex` in `spell.damage` with the same body.

Before Task 1, confirm both are in:
```bash
grep -n "public static int casterRank" src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java
grep -n "public static boolean canPlace" src/main/java/com/hogwartsmagic/spell/TerrainRules.java
grep -n "public static boolean applyCursedWound" src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java
```
Each must print one line. If any doesn't, stop: the phase it belongs to isn't merged.

## Global Constraints

- **Ranks:** every per-rank behaviour reads the caster's rank through `DisableSupport.casterRank(caster, spell)` (players: `SpellMasteryData.getRank`, 1..4). **NPC casters (Students, Death Eaters/`WizardEntity`, Voldemort) and ownerless beams are rank 1.** NPC-cast Bombarda never breaks blocks (see decisions).
- **Per-rank numbers (ticks at 20/s; damage before the house multiplier):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Diffindo | 6 dmg, ignores 25% armour; shears a sheep instead of hurting it; block hit cuts the struck cuttable block | 7, 40%; cuts within radius 1 | 8, 40%; radius 1 | 9, 60%; radius 2; **cleave:** same hit on the nearest enemy up to 3 blocks behind the target (lane half-width 1.25) |
  | Sectumsempra | 5 dmg + bleeding 120 ticks, max 1 stack | 6, max 2 stacks | 7, max 3 stacks | 8, max 3; **detonate:** hitting a bleeding target adds 2 dmg per stack already on it (same hurt call) and refreshes the bleed |
  | Dolohov's Curse | 6 dmg + `cursed_wound` 2 hearts, 600 | 6, 3 hearts, 900 | 6, 4 hearts, 1200 | 6, 5 hearts, 1800; **spread:** a victim dying while cursed curses enemies within 3 blocks for half the hearts rounded up (3) and half the time (900); spread curses don't spread again |
  | Confringo | blast power 1.5 (×0.77 in rain), never breaks or lights blocks; ignites the target 4s | 1.8; 5s; ignites enemies within 1.5 for 2s | 2.1; 6s; within 2.0 for 3s | 2.4; 7s; within 2.5 for 3s; **brittle payoff:** the blast deals ×1.5 to brittle targets (on top of Phase 2's brittle bonus) |
  | Bombarda | breaks blocks with hardness ≤ 3.0 in radius 1.5; 50% of broken blocks drop; 5 entity dmg (falls off to 25% at radius + 1) | radius 2.0; 100% drops; 6 dmg | radius 2.5; hardness ≤ 6.0; obsidian-tier 25% per block; 7 dmg | **Maxima:** radius 3.5; obsidian-tier always; 8 dmg |
  | Incendio | ignites 4s (rain: ×0.4, min 1s); lights campfires, candles, candle cakes; primes TNT; adds 200 ticks of furnace fuel (cap 1600); otherwise one fire on the struck face, like flint and steel | 5s; **fire burst:** flames on flammable blocks within radius 1 (at most 24 fires) | 6s; radius 2 | 7s; radius 3 |

- **Bombarda never breaks:** air, fluids, fire, any block with a block entity (spawners, chests, furnaces, horcruxes, wards, signs, beds), unbreakable blocks (hardness < 0: bedrock, barriers, the Chamber stone, vault doors), `#hogwartsmagic:bombarda_immune` (`#minecraft:wither_immune`, mandrake plants, the Chamber door, the Gringotts gate and lift, the Diagon archway brick, gemino piles), or anything `TerrainRules.canBreak` refuses. TNT in the blast is primed, not broken.
- **Terrain changes** respect `mobGriefing`, `BlockEvent.BreakEvent` (claims), spawn protection and Cave Inimicum wards:
  - `TerrainRules.canBreak`: refused in a ward; a player caster also needs to be outside adventure-mode restrictions, `mayInteract` (spawn protection, world border) and an uncancelled `BreakEvent` posted as that player; an NPC caster needs `mobGriefing`.
  - Bombarda additionally needs `mobGriefing` for player casters too (the roadmap names it explicitly), checked once per blast.
  - Incendio's fire burst additionally needs `doFireTick` and `mobGriefing` and no rain at the impact. Its single flint-and-steel fire and the lighting of campfires, candles, TNT and furnaces only need `TerrainRules.canPlace`.
  - Diffindo's cutting needs only `TerrainRules.canBreak`.
- **Chunk safety:** every block read in this plan is preceded by `level.isLoaded(pos)`, and no code calls `getChunk`. Bombarda's blocks are broken by `BombardaBlasts` at 64 positions per tick per level from `TickEvent.LevelTickEvent` (server, END). Jobs die with their caster's logout or dimension change, on level unload and on server stop.
- **House multiplier:** every direct spell damage in this plan goes through `DamageSupport.scaled(caster, base)` = base × `HousePerks.getSpellDamageMultiplier(caster)`, as Diffindo, Sectumsempra, Dolohov and Bombarda do today. Confringo's vanilla explosion, bleeding ticks and fire are not scaled (unchanged).
- **One hurt per hit:** a living hit already takes a 0.1 chip hit in `SpellBeamEntity.onHit` before `applyHitEffect`. Extra damage on the same target (Sectumsempra's detonation) is added to the spell's single `hurt` call, never a second call in the same tick, or invulnerability frames would swallow it.
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/`, is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch or a `Dist.CLIENT` event subscriber, and talks back through C2S packets the server validates. This plan adds no client code and no packets.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine. (`SpellType.applyHitEffect` is common code; its existing switch stays.)
- Screens: `renderTransparentBackground`, no blur. (This plan adds no screens.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs; the commit messages have no apostrophes).
- `en_us.json` must stay valid JSON after every edit: replacements keep the original line's trailing comma, and an inserted line ends with a comma and goes after a line that already ends with one.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/damage/RankIndex.java` | Pure: rank 1..4 → table index, clamped (package-private) |
| Create `src/main/java/com/hogwartsmagic/spell/damage/BlastShape.java` | Pure: block offsets within a sphere, nearest first |
| Create `src/main/java/com/hogwartsmagic/spell/damage/DiffindoRules.java` | Pure: damage, armour pierce math, cut radius, cleave lane |
| Create `src/main/java/com/hogwartsmagic/spell/damage/SectumsempraRules.java` | Pure: damage, stack caps, detonation |
| Create `src/main/java/com/hogwartsmagic/spell/damage/DolohovRules.java` | Pure: hearts, durations, spread |
| Create `src/main/java/com/hogwartsmagic/spell/damage/ConfringoRules.java` | Pure: power, ignite times, splash radius, brittle bonus |
| Create `src/main/java/com/hogwartsmagic/spell/damage/BombardaRules.java` | Pure: radius, hardness tiers, drops, splash damage, tick budget |
| Create `src/main/java/com/hogwartsmagic/spell/damage/IncendioRules.java` | Pure: ignite times, fire burst radius, furnace fuel |
| Create `src/main/java/com/hogwartsmagic/spell/damage/DamageSupport.java` | Block tags, house-scaled damage, splash target filter |
| Create `src/main/java/com/hogwartsmagic/spell/damage/DiffindoSpell.java` | Armour-piercing hit, sheep shearing, block cutting, Master cleave |
| Create `src/main/java/com/hogwartsmagic/spell/damage/SectumsempraSpell.java` | Hit, rank-capped bleed stacks, Master detonation |
| Create `src/main/java/com/hogwartsmagic/spell/damage/DolohovSpell.java` | Hit, cursed wound, Master spread on death |
| Create `src/main/java/com/hogwartsmagic/spell/damage/ConfringoSpell.java` | Combat blast, ignition, Master brittle bonus hook |
| Create `src/main/java/com/hogwartsmagic/spell/damage/BombardaSpell.java` | Blast: splash damage, knockback, queues the blocks |
| Create `src/main/java/com/hogwartsmagic/spell/damage/BombardaBlasts.java` | Budgeted per-tick block breaking |
| Create `src/main/java/com/hogwartsmagic/spell/damage/IncendioSpell.java` | Ignition, lighting, TNT, furnace fuel, fire burst |
| Modify `src/main/java/com/hogwartsmagic/spell/TerrainRules.java` | `canBreak` |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | Bombarda and Diffindo earn gated block-effect XP |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Six cases delegate to the new classes |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Confringo and Incendio block branches; new Bombarda and Diffindo block branches |
| Create `src/main/resources/data/hogwartsmagic/tags/block/diffindo_cuttable.json`, `bombarda_immune.json`, `bombarda_obsidian_tier.json` | Block tags |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Six spell descriptions, one Bombarda message |
| Create tests under `src/test/java/com/hogwartsmagic/spell/damage/` | Unit tests |

---

### Task 1: The pure rules and the blast shape

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/RankIndex.java`, `BlastShape.java`, `DiffindoRules.java`, `SectumsempraRules.java`, `DolohovRules.java`, `ConfringoRules.java`, `BombardaRules.java`, `IncendioRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/damage/BlastShapeTest.java`, `DiffindoRulesTest.java`, `SectumsempraRulesTest.java`, `DolohovRulesTest.java`, `ConfringoRulesTest.java`, `BombardaRulesTest.java`, `IncendioRulesTest.java`

**Interfaces:**
- Consumes: `BleedRules.MAX_STACKS` (Phase 2, pure)
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private)
  - `BlastShape.MAX_RADIUS = 4.0`; `static List<int[]> offsets(double radius)` (each `{dx, dy, dz}` with dx²+dy²+dz² ≤ radius², nearest first, `{0,0,0}` first)
  - `DiffindoRules.CLEAVE_RANGE = 3.0`, `CLEAVE_HALF_WIDTH = 1.25`; `static float damage(int rank)`, `static double armorIgnore(int rank)`, `static double cutRadius(int rank)`, `static boolean cleaves(int rank)`, `static float damageAfterArmor(float damage, double armor, double toughness, double ignoreFraction)`, `static boolean inCleaveLane(double forwardX, double forwardZ, double offsetX, double offsetZ)`
  - `SectumsempraRules.BLEED_TICKS = 120`, `DETONATE_DAMAGE_PER_STACK = 2.0f`; `static float damage(int rank)`, `static int maxStacks(int rank)`, `static boolean detonates(int rank)`, `static int nextStacks(int rank, int currentStacks)`, `static float burstDamage(int rank, int currentStacks)`
  - `DolohovRules.DAMAGE = 6.0f`, `SPREAD_RADIUS = 3.0`; `static int hearts(int rank)`, `static int ticks(int rank)`, `static boolean spreads(int rank)`, `static int spreadHearts(int hearts)`, `static int spreadTicks(int ticks)`
  - `ConfringoRules.RAIN_POWER_FACTOR = 0.77f`, `MASTER_BRITTLE_BONUS = 1.5f`; `static float power(int rank, boolean raining)`, `static int igniteSeconds(int rank)`, `static double splashIgniteRadius(int rank)`, `static int splashIgniteSeconds(int rank)`, `static float brittleBonus(int rank)`
  - `BombardaRules.BLOCKS_PER_TICK = 64`, `MIN_SPLASH_FACTOR = 0.25f`; `static double radius(int rank)`, `static float entityDamage(int rank)`, `static double dropChance(int rank)`, `static boolean canBreak(int rank, float hardness, boolean obsidianTier, double roll)`, `static float splashDamage(float base, double distance, double radius)`
  - `IncendioRules.RAIN_FACTOR = 0.4`, `MAX_FIRES = 24`, `FURNACE_FUEL_TICKS = 200`, `FURNACE_FUEL_CAP = 1600`; `static int igniteSeconds(int rank, boolean raining)`, `static double fireRadius(int rank)`, `static boolean spreadsFire(int rank)`, `static int furnaceBurnTime(int currentBurnTime)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/damage/BlastShapeTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastShapeTest {
    @Test
    void sphereSizesMatchTheRadii() {
        assertEquals(1, BlastShape.offsets(0.0).size());
        assertEquals(7, BlastShape.offsets(1.0).size());
        assertEquals(19, BlastShape.offsets(1.5).size());
        assertEquals(33, BlastShape.offsets(2.0).size());
        assertEquals(81, BlastShape.offsets(2.5).size());
        assertEquals(123, BlastShape.offsets(3.0).size());
        assertEquals(179, BlastShape.offsets(3.5).size());
    }

    @Test
    void centreComesFirstAndDistanceNeverDecreases() {
        List<int[]> offsets = BlastShape.offsets(3.5);
        assertArrayEquals(new int[]{0, 0, 0}, offsets.get(0));
        int previous = 0;
        for (int[] o : offsets) {
            int d2 = o[0] * o[0] + o[1] * o[1] + o[2] * o[2];
            assertTrue(d2 >= previous);
            previous = d2;
        }
    }

    @Test
    void badRadiiAreClamped() {
        assertEquals(1, BlastShape.offsets(Double.NaN).size());
        assertEquals(1, BlastShape.offsets(-2.0).size());
        assertEquals(BlastShape.offsets(BlastShape.MAX_RADIUS).size(), BlastShape.offsets(50.0).size());
    }
}
```

`src/test/java/com/hogwartsmagic/spell/damage/DiffindoRulesTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffindoRulesTest {
    @Test
    void damageAndPierceScaleWithRank() {
        assertEquals(6f, DiffindoRules.damage(1), 1e-6);
        assertEquals(7f, DiffindoRules.damage(2), 1e-6);
        assertEquals(8f, DiffindoRules.damage(3), 1e-6);
        assertEquals(9f, DiffindoRules.damage(4), 1e-6);
        assertEquals(0.25, DiffindoRules.armorIgnore(1), 1e-9);
        assertEquals(0.40, DiffindoRules.armorIgnore(3), 1e-9);
        assertEquals(0.60, DiffindoRules.armorIgnore(4), 1e-9);
        assertEquals(6f, DiffindoRules.damage(0), 1e-6);
        assertEquals(9f, DiffindoRules.damage(9), 1e-6);
    }

    @Test
    void cutRadiusAndCleave() {
        assertEquals(0.0, DiffindoRules.cutRadius(1), 1e-9);
        assertEquals(1.0, DiffindoRules.cutRadius(2), 1e-9);
        assertEquals(2.0, DiffindoRules.cutRadius(4), 1e-9);
        assertFalse(DiffindoRules.cleaves(3));
        assertTrue(DiffindoRules.cleaves(4));
    }

    @Test
    void armourMathMatchesVanillaWithPartOfTheArmourIgnored() {
        assertEquals(6.0f, DiffindoRules.damageAfterArmor(6f, 0, 0, 0.25), 1e-4);
        assertEquals(1.92f, DiffindoRules.damageAfterArmor(6f, 20, 0, 0.0), 1e-4);
        assertEquals(3.12f, DiffindoRules.damageAfterArmor(6f, 20, 0, 0.25), 1e-4);
        assertEquals(7.74f, DiffindoRules.damageAfterArmor(9f, 20, 0, 0.6), 1e-4);
        assertEquals(3.0f, DiffindoRules.damageAfterArmor(10f, 20, 8, 0.0), 1e-4);
        assertEquals(6.0f, DiffindoRules.damageAfterArmor(6f, 20, 8, 1.0), 1e-4);
    }

    @Test
    void armourMathRejectsBadInput() {
        assertEquals(0f, DiffindoRules.damageAfterArmor(Float.NaN, 10, 0, 0.25), 1e-6);
        assertEquals(0f, DiffindoRules.damageAfterArmor(-3f, 10, 0, 0.25), 1e-6);
    }

    @Test
    void cleaveLaneIsBehindTheTargetAndNarrow() {
        assertTrue(DiffindoRules.inCleaveLane(1, 0, 2, 0.5));
        assertTrue(DiffindoRules.inCleaveLane(5, 0, 2, 0));
        assertFalse(DiffindoRules.inCleaveLane(1, 0, 3.5, 0));
        assertFalse(DiffindoRules.inCleaveLane(1, 0, -1, 0));
        assertFalse(DiffindoRules.inCleaveLane(1, 0, 2, 2));
        assertFalse(DiffindoRules.inCleaveLane(0, 0, 1, 0));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/damage/SectumsempraRulesTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.combat.BleedRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectumsempraRulesTest {
    @Test
    void damageAndStackCaps() {
        assertEquals(5f, SectumsempraRules.damage(1), 1e-6);
        assertEquals(8f, SectumsempraRules.damage(4), 1e-6);
        assertEquals(1, SectumsempraRules.maxStacks(1));
        assertEquals(2, SectumsempraRules.maxStacks(2));
        assertEquals(3, SectumsempraRules.maxStacks(3));
        assertEquals(BleedRules.MAX_STACKS, SectumsempraRules.maxStacks(4));
    }

    @Test
    void stacksGrowToTheRankCapAndNeverDrop() {
        assertEquals(1, SectumsempraRules.nextStacks(1, 0));
        assertEquals(1, SectumsempraRules.nextStacks(1, 1));
        assertEquals(2, SectumsempraRules.nextStacks(2, 1));
        assertEquals(2, SectumsempraRules.nextStacks(2, 2));
        assertEquals(3, SectumsempraRules.nextStacks(3, 2));
        assertEquals(3, SectumsempraRules.nextStacks(4, 3));
        assertEquals(3, SectumsempraRules.nextStacks(1, 3));
    }

    @Test
    void onlyMasterDetonatesExistingStacks() {
        assertFalse(SectumsempraRules.detonates(3));
        assertTrue(SectumsempraRules.detonates(4));
        assertEquals(0f, SectumsempraRules.burstDamage(4, 0), 1e-6);
        assertEquals(6f, SectumsempraRules.burstDamage(4, 3), 1e-6);
        assertEquals(0f, SectumsempraRules.burstDamage(3, 3), 1e-6);
        assertEquals(120, SectumsempraRules.BLEED_TICKS);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/damage/DolohovRulesTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DolohovRulesTest {
    @Test
    void woundGrowsDeeperAndLonger() {
        assertEquals(2, DolohovRules.hearts(1));
        assertEquals(3, DolohovRules.hearts(2));
        assertEquals(4, DolohovRules.hearts(3));
        assertEquals(5, DolohovRules.hearts(4));
        assertEquals(600, DolohovRules.ticks(1));
        assertEquals(900, DolohovRules.ticks(2));
        assertEquals(1200, DolohovRules.ticks(3));
        assertEquals(1800, DolohovRules.ticks(4));
        assertEquals(6f, DolohovRules.DAMAGE, 1e-6);
    }

    @Test
    void onlyMasterSpreadsForHalf() {
        assertFalse(DolohovRules.spreads(3));
        assertTrue(DolohovRules.spreads(4));
        assertEquals(3, DolohovRules.spreadHearts(5));
        assertEquals(1, DolohovRules.spreadHearts(1));
        assertEquals(900, DolohovRules.spreadTicks(1800));
        assertEquals(20, DolohovRules.spreadTicks(10));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/damage/ConfringoRulesTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfringoRulesTest {
    @Test
    void powerGrowsAndRainDampensIt() {
        assertEquals(1.5f, ConfringoRules.power(1, false), 1e-6);
        assertEquals(1.8f, ConfringoRules.power(2, false), 1e-6);
        assertEquals(2.1f, ConfringoRules.power(3, false), 1e-6);
        assertEquals(2.4f, ConfringoRules.power(4, false), 1e-6);
        assertEquals(1.155f, ConfringoRules.power(1, true), 1e-4);
    }

    @Test
    void ignitionAndSplash() {
        assertEquals(4, ConfringoRules.igniteSeconds(1));
        assertEquals(7, ConfringoRules.igniteSeconds(4));
        assertEquals(0.0, ConfringoRules.splashIgniteRadius(1), 1e-9);
        assertEquals(1.5, ConfringoRules.splashIgniteRadius(2), 1e-9);
        assertEquals(2.5, ConfringoRules.splashIgniteRadius(4), 1e-9);
        assertEquals(2, ConfringoRules.splashIgniteSeconds(2));
        assertEquals(3, ConfringoRules.splashIgniteSeconds(4));
    }

    @Test
    void onlyMasterPunishesBrittle() {
        assertEquals(1.0f, ConfringoRules.brittleBonus(3), 1e-6);
        assertEquals(1.5f, ConfringoRules.brittleBonus(4), 1e-6);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/damage/BombardaRulesTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BombardaRulesTest {
    @Test
    void radiusDamageAndDrops() {
        assertEquals(1.5, BombardaRules.radius(1), 1e-9);
        assertEquals(2.0, BombardaRules.radius(2), 1e-9);
        assertEquals(2.5, BombardaRules.radius(3), 1e-9);
        assertEquals(3.5, BombardaRules.radius(4), 1e-9);
        assertEquals(5f, BombardaRules.entityDamage(1), 1e-6);
        assertEquals(8f, BombardaRules.entityDamage(4), 1e-6);
        assertEquals(0.5, BombardaRules.dropChance(1), 1e-9);
        assertEquals(1.0, BombardaRules.dropChance(2), 1e-9);
    }

    @Test
    void hardnessTiers() {
        assertTrue(BombardaRules.canBreak(1, 1.5f, false, 0.5));   // stone
        assertTrue(BombardaRules.canBreak(1, 3.0f, false, 0.5));   // wooden door, deepslate
        assertFalse(BombardaRules.canBreak(2, 4.5f, false, 0.0));  // deepslate ore
        assertTrue(BombardaRules.canBreak(3, 5.0f, false, 0.0));   // iron door
        assertFalse(BombardaRules.canBreak(4, 25f, false, 0.0));   // Gringotts lift
        assertFalse(BombardaRules.canBreak(4, -1f, false, 0.0));   // bedrock
        assertFalse(BombardaRules.canBreak(4, Float.NaN, false, 0.0));
    }

    @Test
    void obsidianIsAChanceAtExpertAndCertainAtMaster() {
        assertFalse(BombardaRules.canBreak(2, 50f, true, 0.0));
        assertTrue(BombardaRules.canBreak(3, 50f, true, 0.2));
        assertFalse(BombardaRules.canBreak(3, 50f, true, 0.3));
        assertTrue(BombardaRules.canBreak(4, 50f, true, 0.99));
    }

    @Test
    void splashFallsOffToAQuarterThenStops() {
        assertEquals(5f, BombardaRules.splashDamage(5f, 0.0, 1.5), 1e-6);
        assertEquals(4f, BombardaRules.splashDamage(8f, 2.25, 3.5), 1e-6);
        assertEquals(2f, BombardaRules.splashDamage(8f, 4.0, 3.5), 1e-6);
        assertEquals(0f, BombardaRules.splashDamage(5f, 2.5, 1.5), 1e-6);
        assertEquals(0f, BombardaRules.splashDamage(5f, Double.NaN, 1.5), 1e-6);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/damage/IncendioRulesTest.java`:
```java
package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncendioRulesTest {
    @Test
    void ignitionScalesAndRainCutsIt() {
        assertEquals(4, IncendioRules.igniteSeconds(1, false));
        assertEquals(5, IncendioRules.igniteSeconds(2, false));
        assertEquals(7, IncendioRules.igniteSeconds(4, false));
        assertEquals(2, IncendioRules.igniteSeconds(1, true));
        assertEquals(2, IncendioRules.igniteSeconds(3, true));
        assertEquals(3, IncendioRules.igniteSeconds(4, true));
    }

    @Test
    void fireBurstStartsAtAdept() {
        assertFalse(IncendioRules.spreadsFire(1));
        assertTrue(IncendioRules.spreadsFire(2));
        assertEquals(1.0, IncendioRules.fireRadius(2), 1e-9);
        assertEquals(2.0, IncendioRules.fireRadius(3), 1e-9);
        assertEquals(3.0, IncendioRules.fireRadius(4), 1e-9);
    }

    @Test
    void furnaceFuelAddsTenSecondsUpToTheCap() {
        assertEquals(200, IncendioRules.furnaceBurnTime(0));
        assertEquals(200, IncendioRules.furnaceBurnTime(-5));
        assertEquals(1600, IncendioRules.furnaceBurnTime(1500));
        assertEquals(1600, IncendioRules.furnaceBurnTime(1600));
        assertEquals(2000, IncendioRules.furnaceBurnTime(2000));
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.damage.*"`
Expected: compile FAIL, `cannot find symbol` for `BlastShape`, `DiffindoRules` and the other rules classes.

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/damage/RankIndex.java`:
```java
package com.hogwartsmagic.spell.damage;

/** Rank 1..4 to a table index 0..3; anything outside is clamped. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/BlastShape.java`:
```java
package com.hogwartsmagic.spell.damage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The block offsets a spherical blast touches, nearest first, so breaking ripples outward. */
public final class BlastShape {
    public static final double MAX_RADIUS = 4.0;

    private BlastShape() {
    }

    /** Every {dx, dy, dz} with dx² + dy² + dz² ≤ radius²; NaN and negatives mean 0, the radius is capped at 4. */
    public static List<int[]> offsets(double radius) {
        double r = Double.isNaN(radius) ? 0.0 : Math.max(0.0, Math.min(MAX_RADIUS, radius));
        int reach = (int) Math.floor(r);
        double limit = r * r + 1.0e-9;
        List<int[]> out = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= limit) {
                        out.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        out.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
                .thenComparingInt(o -> o[1])
                .thenComparingInt(o -> o[0])
                .thenComparingInt(o -> o[2]));
        return out;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/DiffindoRules.java`:
```java
package com.hogwartsmagic.spell.damage;

/** Diffindo: honest damage that cuts partly through armour, the magic shears, and a Master cleave. */
public final class DiffindoRules {
    public static final double CLEAVE_RANGE = 3.0;
    public static final double CLEAVE_HALF_WIDTH = 1.25;
    private static final float[] DAMAGE = {6f, 7f, 8f, 9f};
    private static final double[] ARMOR_IGNORE = {0.25, 0.40, 0.40, 0.60};
    private static final double[] CUT_RADIUS = {0.0, 1.0, 1.0, 2.0};

    private DiffindoRules() {
    }

    public static float damage(int rank) {
        return DAMAGE[RankIndex.of(rank)];
    }

    public static double armorIgnore(int rank) {
        return ARMOR_IGNORE[RankIndex.of(rank)];
    }

    public static double cutRadius(int rank) {
        return CUT_RADIUS[RankIndex.of(rank)];
    }

    public static boolean cleaves(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /**
     * Vanilla's armour formula (CombatRules.getDamageAfterAbsorb, without weapon enchantments) run on
     * armour and toughness reduced by {@code ignoreFraction}. The result is dealt as armour-bypassing
     * magic damage, so armour only counts once.
     */
    public static float damageAfterArmor(float damage, double armor, double toughness, double ignoreFraction) {
        if (!(damage > 0f)) {
            return 0f;
        }
        double keep = 1.0 - Math.max(0.0, Math.min(1.0, ignoreFraction));
        double a = Math.max(0.0, armor) * keep;
        double t = Math.max(0.0, toughness) * keep;
        double f = 2.0 + t / 4.0;
        double effective = Math.max(a * 0.2, Math.min(20.0, a - damage / f));
        return (float) (damage * (1.0 - effective / 25.0));
    }

    /** Offset (from the first target) lies behind it along the cast direction, within 3 blocks, in a 1.25-wide lane. */
    public static boolean inCleaveLane(double forwardX, double forwardZ, double offsetX, double offsetZ) {
        double length = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (length < 1.0e-6) {
            return false;
        }
        double fx = forwardX / length;
        double fz = forwardZ / length;
        double along = offsetX * fx + offsetZ * fz;
        if (along <= 0.0 || along > CLEAVE_RANGE) {
            return false;
        }
        double lateralSq = offsetX * offsetX + offsetZ * offsetZ - along * along;
        return lateralSq <= CLEAVE_HALF_WIDTH * CLEAVE_HALF_WIDTH;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/SectumsempraRules.java`:
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.combat.BleedRules;

/** Sectumsempra: a cut plus bleeding stacks capped by rank; at Master a hit tears every open wound. */
public final class SectumsempraRules {
    public static final int BLEED_TICKS = 120;
    public static final float DETONATE_DAMAGE_PER_STACK = 2.0f;
    private static final float[] DAMAGE = {5f, 6f, 7f, 8f};
    private static final int[] MAX_STACKS = {1, 2, 3, 3};

    private SectumsempraRules() {
    }

    public static float damage(int rank) {
        return DAMAGE[RankIndex.of(rank)];
    }

    public static int maxStacks(int rank) {
        return Math.min(BleedRules.MAX_STACKS, MAX_STACKS[RankIndex.of(rank)]);
    }

    public static boolean detonates(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** One more stack up to the rank cap; stacks someone else already put on the target are never lowered. */
    public static int nextStacks(int rank, int currentStacks) {
        int current = Math.max(0, currentStacks);
        int capped = Math.min(maxStacks(rank), current + 1);
        return Math.min(BleedRules.MAX_STACKS, Math.max(current, capped));
    }

    /** Extra damage at Master for the stacks already on the target when the hit lands. */
    public static float burstDamage(int rank, int currentStacks) {
        if (!detonates(rank) || currentStacks <= 0) {
            return 0f;
        }
        return DETONATE_DAMAGE_PER_STACK * Math.min(BleedRules.MAX_STACKS, currentStacks);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/DolohovRules.java`:
```java
package com.hogwartsmagic.spell.damage;

/** Dolohov's Curse: a lasting max-health wound that grows with rank and, at Master, spreads on death. */
public final class DolohovRules {
    public static final float DAMAGE = 6.0f;
    public static final double SPREAD_RADIUS = 3.0;
    private static final int[] HEARTS = {2, 3, 4, 5};
    private static final int[] TICKS = {600, 900, 1200, 1800};

    private DolohovRules() {
    }

    public static int hearts(int rank) {
        return HEARTS[RankIndex.of(rank)];
    }

    public static int ticks(int rank) {
        return TICKS[RankIndex.of(rank)];
    }

    public static boolean spreads(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Half the hearts, rounded up, at least one. */
    public static int spreadHearts(int hearts) {
        return Math.max(1, (hearts + 1) / 2);
    }

    /** Half the duration, at least one second. */
    public static int spreadTicks(int ticks) {
        return Math.max(20, ticks / 2);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/ConfringoRules.java`:
```java
package com.hogwartsmagic.spell.damage;

/** Confringo: the combat blast. Bigger and hotter with rank; at Master it hits brittle targets harder. */
public final class ConfringoRules {
    /** Today's rain blast is 1.15 against 1.5 dry. */
    public static final float RAIN_POWER_FACTOR = 0.77f;
    public static final float MASTER_BRITTLE_BONUS = 1.5f;
    private static final float[] POWER = {1.5f, 1.8f, 2.1f, 2.4f};
    private static final int[] IGNITE_SECONDS = {4, 5, 6, 7};
    private static final double[] SPLASH_IGNITE_RADIUS = {0.0, 1.5, 2.0, 2.5};
    private static final int[] SPLASH_IGNITE_SECONDS = {0, 2, 3, 3};

    private ConfringoRules() {
    }

    public static float power(int rank, boolean raining) {
        float power = POWER[RankIndex.of(rank)];
        return raining ? power * RAIN_POWER_FACTOR : power;
    }

    public static int igniteSeconds(int rank) {
        return IGNITE_SECONDS[RankIndex.of(rank)];
    }

    public static double splashIgniteRadius(int rank) {
        return SPLASH_IGNITE_RADIUS[RankIndex.of(rank)];
    }

    public static int splashIgniteSeconds(int rank) {
        return SPLASH_IGNITE_SECONDS[RankIndex.of(rank)];
    }

    public static float brittleBonus(int rank) {
        return RankIndex.of(rank) == 3 ? MASTER_BRITTLE_BONUS : 1.0f;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/BombardaRules.java`:
```java
package com.hogwartsmagic.spell.damage;

/** Bombarda: the terrain breaker. Radius and hardness tier grow with rank; it is gentler on creatures. */
public final class BombardaRules {
    /** Positions examined per level per tick; a Master blast (179 positions) finishes in 3 ticks. */
    public static final int BLOCKS_PER_TICK = 64;
    public static final float MIN_SPLASH_FACTOR = 0.25f;
    private static final double[] RADIUS = {1.5, 2.0, 2.5, 3.5};
    private static final float[] MAX_HARDNESS = {3.0f, 3.0f, 6.0f, 6.0f};
    private static final double[] OBSIDIAN_CHANCE = {0.0, 0.0, 0.25, 1.0};
    private static final double[] DROP_CHANCE = {0.5, 1.0, 1.0, 1.0};
    private static final float[] ENTITY_DAMAGE = {5f, 6f, 7f, 8f};

    private BombardaRules() {
    }

    public static double radius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static float entityDamage(int rank) {
        return ENTITY_DAMAGE[RankIndex.of(rank)];
    }

    public static double dropChance(int rank) {
        return DROP_CHANCE[RankIndex.of(rank)];
    }

    /**
     * @param hardness     the block's destroy speed (negative means unbreakable)
     * @param obsidianTier the block is in #hogwartsmagic:bombarda_obsidian_tier
     * @param roll         a random number in [0, 1), used only for the obsidian chance
     */
    public static boolean canBreak(int rank, float hardness, boolean obsidianTier, double roll) {
        if (Float.isNaN(hardness) || hardness < 0f) {
            return false;
        }
        int i = RankIndex.of(rank);
        if (obsidianTier) {
            return roll < OBSIDIAN_CHANCE[i];
        }
        return hardness <= MAX_HARDNESS[i];
    }

    /** Full damage at the centre, falling linearly to a quarter, none from radius + 1 out. */
    public static float splashDamage(float base, double distance, double radius) {
        double reach = radius + 1.0;
        if (!(distance < reach)) {
            return 0f;
        }
        double factor = Math.max(MIN_SPLASH_FACTOR, 1.0 - Math.max(0.0, distance) / reach);
        return (float) (base * factor);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/damage/IncendioRules.java`:
```java
package com.hogwartsmagic.spell.damage;

/** Incendio: fire as a tool. Longer burns with rank; from Adept the flames spread over flammable blocks. */
public final class IncendioRules {
    /** Today's rain burn is 2s against 5s dry. */
    public static final double RAIN_FACTOR = 0.4;
    public static final int MAX_FIRES = 24;
    public static final int FURNACE_FUEL_TICKS = 200;
    public static final int FURNACE_FUEL_CAP = 1600;
    private static final int[] IGNITE_SECONDS = {4, 5, 6, 7};
    private static final double[] FIRE_RADIUS = {0.0, 1.0, 2.0, 3.0};

    private IncendioRules() {
    }

    public static int igniteSeconds(int rank, boolean raining) {
        int seconds = IGNITE_SECONDS[RankIndex.of(rank)];
        return raining ? Math.max(1, (int) Math.round(seconds * RAIN_FACTOR)) : seconds;
    }

    public static double fireRadius(int rank) {
        return FIRE_RADIUS[RankIndex.of(rank)];
    }

    public static boolean spreadsFire(int rank) {
        return fireRadius(rank) > 0.0;
    }

    /** Ten more seconds of fuel, up to one coal's worth; a furnace already above the cap keeps what it has. */
    public static int furnaceBurnTime(int currentBurnTime) {
        int current = Math.max(0, currentBurnTime);
        if (current >= FURNACE_FUEL_CAP) {
            return current;
        }
        return Math.min(FURNACE_FUEL_CAP, current + FURNACE_FUEL_TICKS);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.damage.*"`
Expected: `BUILD SUCCESSFUL`, all tests in the seven classes pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/RankIndex.java src/main/java/com/hogwartsmagic/spell/damage/BlastShape.java src/main/java/com/hogwartsmagic/spell/damage/DiffindoRules.java src/main/java/com/hogwartsmagic/spell/damage/SectumsempraRules.java src/main/java/com/hogwartsmagic/spell/damage/DolohovRules.java src/main/java/com/hogwartsmagic/spell/damage/ConfringoRules.java src/main/java/com/hogwartsmagic/spell/damage/BombardaRules.java src/main/java/com/hogwartsmagic/spell/damage/IncendioRules.java src/test/java/com/hogwartsmagic/spell/damage
git commit -m "Add the tested per-rank rules for the damage spells" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Shared plumbing (break rule, block tags, damage helpers, block XP)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/TerrainRules.java` (Phase 3)
- Create: `src/main/java/com/hogwartsmagic/spell/damage/DamageSupport.java`
- Create: `src/main/resources/data/hogwartsmagic/tags/block/diffindo_cuttable.json`, `bombarda_immune.json`, `bombarda_obsidian_tier.json`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` (`BLOCK_UTILITY`)

**Interfaces:**
- Consumes: `CombatEffects.responsible`, `CombatTags.ALLIED` (Phase 2); `DisableSupport.isEnemyOf` (Phase 3); `CaveInimicumManager.get(ServerLevel).isWardedAt(BlockPos)`, `HousePerks.getSpellDamageMultiplier(Entity)` (existing)
- Produces:
  - `TerrainRules.canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster)` → `boolean`
  - `DamageSupport.DIFFINDO_CUTTABLE`, `BOMBARDA_IMMUNE`, `BOMBARDA_OBSIDIAN_TIER` (`TagKey<Block>`)
  - `DamageSupport.scaled(@Nullable Entity caster, float base)` → `float`
  - `DamageSupport.isSplashTarget(@Nullable Entity caster, LivingEntity candidate, @Nullable LivingEntity primary)` → `boolean`

- [ ] **Step 1: `TerrainRules.canBreak`.** In `TerrainRules.java`, after the import line `import net.minecraft.server.level.ServerLevel;` add:
```java
import net.minecraft.server.level.ServerPlayer;
```
After `import net.minecraft.world.level.GameRules;` add:
```java
import net.minecraft.world.level.block.state.BlockState;
```
Then replace the end of the class:
```java
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
}
```
with:
```java
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * May a spell break (or cut) the block here? Refused inside a Cave Inimicum ward. A player caster
     * must also be allowed to break blocks in their game mode (adventure mode), pass {@code mayInteract}
     * (spawn protection, world border) and see an uncancelled {@link BlockEvent.BreakEvent} posted as
     * them, which is the hook claim mods guard breaking with (and the one Reparo logs). An NPC caster, or
     * no caster, needs the {@code mobGriefing} game rule. Callers check {@code level.isLoaded(pos)} first.
     */
    public static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster) {
        if (CaveInimicumManager.get(level).isWardedAt(pos)) {
            return false;
        }
        Entity responsible = CombatEffects.responsible(caster);
        if (responsible instanceof Player player) {
            if (player instanceof ServerPlayer serverPlayer
                    && serverPlayer.blockActionRestricted(level, pos, serverPlayer.gameMode.getGameModeForPlayer())) {
                return false;
            }
            if (!level.mayInteract(player, pos)) {
                return false;
            }
            return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
        }
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
}
```

- [ ] **Step 2: Create `DamageSupport`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.sorting.HousePerks;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.UUID;

/** Helpers shared by the six damage spells. Common code with no client imports; callers are server side. */
public final class DamageSupport {
    /** Blocks Diffindo cuts free as items (dropped as if sheared). */
    public static final TagKey<Block> DIFFINDO_CUTTABLE = tag("diffindo_cuttable");
    /** Blocks Bombarda never breaks, on top of block entities and unbreakable blocks. */
    public static final TagKey<Block> BOMBARDA_IMMUNE = tag("bombarda_immune");
    /** Obsidian-hard blocks: a 25% chance per block at Expert, certain at Master. */
    public static final TagKey<Block> BOMBARDA_OBSIDIAN_TIER = tag("bombarda_obsidian_tier");

    private DamageSupport() {
    }

    /** Spell damage after the caster's house multiplier (Gryffindor +10%), as the old SpellType code did. */
    public static float scaled(@Nullable Entity caster, float base) {
        return base * HousePerks.getSpellDamageMultiplier(CombatEffects.responsible(caster));
    }

    /**
     * Who a splash (Confringo's burning debris) may catch. With a primary target this is exactly
     * {@link DisableSupport#isEnemyOf}. Without one (a blast on a block) it is the same rule minus the
     * player case: never players, the caster, allied, owned or team-mate mobs, armour stands; otherwise
     * hostile mobs (not Students) and any mob targeting the caster.
     */
    public static boolean isSplashTarget(@Nullable Entity caster, LivingEntity candidate, @Nullable LivingEntity primary) {
        if (primary != null) {
            return DisableSupport.isEnemyOf(caster, candidate, primary);
        }
        Entity owner = CombatEffects.responsible(caster);
        if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()
                || candidate instanceof ArmorStand || candidate instanceof Player) {
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
            if (candidate instanceof Mob mob && mob.getTarget() == owner) {
                return true;
            }
        }
        return candidate instanceof Enemy && !(candidate instanceof StudentEntity);
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
    }
}
```

- [ ] **Step 3: The block tags** (1.21 uses the singular `tags/block` folder).

`src/main/resources/data/hogwartsmagic/tags/block/diffindo_cuttable.json`:
```json
{
  "replace": false,
  "values": [
    "#minecraft:leaves",
    "#minecraft:wool",
    "hogwartsmagic:fine_wood_leaves",
    "minecraft:vine",
    "minecraft:cave_vines",
    "minecraft:cave_vines_plant",
    "minecraft:weeping_vines",
    "minecraft:weeping_vines_plant",
    "minecraft:twisting_vines",
    "minecraft:twisting_vines_plant",
    "minecraft:glow_lichen",
    "minecraft:hanging_roots",
    "minecraft:cobweb",
    "minecraft:tripwire",
    "minecraft:short_grass",
    "minecraft:tall_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:dead_bush",
    "minecraft:seagrass",
    "minecraft:tall_seagrass"
  ]
}
```

`src/main/resources/data/hogwartsmagic/tags/block/bombarda_immune.json`:
```json
{
  "replace": false,
  "values": [
    "#minecraft:wither_immune",
    "hogwartsmagic:mandrake_plant",
    "hogwartsmagic:chamber_door",
    "hogwartsmagic:gringotts_gate",
    "hogwartsmagic:gringotts_lift",
    "hogwartsmagic:diagon_archway_brick",
    "hogwartsmagic:gemino_pile"
  ]
}
```
(Why each mod block: a mandrake must be pulled, and a `BreakEvent` on one starts the pull; the Chamber door opens to Parseltongue only; the gate, lift and archway are puzzle or structure pieces; gemino piles are a vault trap.)

`src/main/resources/data/hogwartsmagic/tags/block/bombarda_obsidian_tier.json`:
```json
{
  "replace": false,
  "values": [
    "minecraft:obsidian",
    "minecraft:crying_obsidian"
  ]
}
```

- [ ] **Step 4: Bombarda and Diffindo earn block-effect XP.** In `MasteryAwards.java`, replace:
```java
    private static final Set<SpellType> BLOCK_UTILITY = EnumSet.of(
            SpellType.ACCIO, SpellType.ALOHOMORA, SpellType.INCENDIO, SpellType.AQUA_ERUCTO,
            SpellType.MORSMORDRE, SpellType.CAVE_INIMICUM, SpellType.ALATUS, SpellType.REPARO,
            SpellType.PIERTOTUM_LOCOMOTOR, SpellType.SERPENSORTIA);
```
with:
```java
    // Bombarda and Diffindo joined in Spell Mastery Phase 4: blasting and cutting blocks are now real
    // uses of them. Like Incendio they also keep earning living-hit XP; the two keys never collide.
    private static final Set<SpellType> BLOCK_UTILITY = EnumSet.of(
            SpellType.ACCIO, SpellType.ALOHOMORA, SpellType.INCENDIO, SpellType.AQUA_ERUCTO,
            SpellType.MORSMORDRE, SpellType.CAVE_INIMICUM, SpellType.ALATUS, SpellType.REPARO,
            SpellType.PIERTOTUM_LOCOMOTOR, SpellType.SERPENSORTIA, SpellType.BOMBARDA, SpellType.DIFFINDO);
```

- [ ] **Step 5: Build, test, validate the tags and boot a dedicated server** (new data tags)

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run:
```bash
python -c "import json; [json.load(open('src/main/resources/data/hogwartsmagic/tags/block/' + n + '.json', encoding='utf-8')) for n in ('diffindo_cuttable', 'bombarda_immune', 'bombarda_obsidian_tier')]"
```
Expected: no output.

Run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; the log has no `Couldn't load tag` line mentioning `hogwartsmagic:` and no stack trace mentioning `TerrainRules`, `DamageSupport` or any `client` class.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/TerrainRules.java src/main/java/com/hogwartsmagic/spell/damage/DamageSupport.java src/main/resources/data/hogwartsmagic/tags/block/diffindo_cuttable.json src/main/resources/data/hogwartsmagic/tags/block/bombarda_immune.json src/main/resources/data/hogwartsmagic/tags/block/bombarda_obsidian_tier.json src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java
git commit -m "Add the shared break rule, block tags and helpers for the damage spells" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Diffindo (armour-piercing cut, magic shears, Master cleave)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/DiffindoSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case DIFFINDO`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (`onHit`, new block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.diffindo.desc`)

**Interfaces:**
- Consumes: `DiffindoRules`, `BlastShape` (Task 1); `TerrainRules.canBreak`, `DamageSupport.DIFFINDO_CUTTABLE/scaled` (Task 2); `DisableSupport.casterRank/isEnemyOf` (Phase 3); `CombatEffects.responsible` (Phase 2)
- Produces: `DiffindoSpell.apply(LivingEntity target, @Nullable Entity caster)`, `DiffindoSpell.cutBlocks(ServerLevel level, BlockPos hit, @Nullable Entity caster)` → `int` blocks cut

- [ ] **Step 1: Create `DiffindoSpell`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Diffindo: a precise severing charm. It cuts partly through armour (dealt as armour-bypassing magic
 * damage after the reduced armour is applied once), shears a sheep instead of hurting it, and on a
 * block hit cuts leaves, vines, webs, wool and grass free as if sheared. At Master the same cut also
 * lands on the nearest enemy up to 3 blocks behind the target.
 */
public final class DiffindoSpell {
    private DiffindoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Entity responsible = CombatEffects.responsible(caster);
        if (target instanceof Sheep sheep && sheep.readyForShearing()) {
            sheep.shear(SoundSource.PLAYERS);
            sheep.gameEvent(GameEvent.SHEAR, responsible);
            return; // the shearing charm, not a killing blow
        }
        int rank = DisableSupport.casterRank(caster, SpellType.DIFFINDO);
        float damage = DamageSupport.scaled(caster, DiffindoRules.damage(rank));
        double ignore = DiffindoRules.armorIgnore(rank);
        sever(target, damage, ignore);
        if (DiffindoRules.cleaves(rank) && responsible != null) {
            cleave(level, target, caster, responsible, damage, ignore);
        }
    }

    private static void sever(LivingEntity target, float damage, double ignore) {
        float dealt = DiffindoRules.damageAfterArmor(damage, target.getArmorValue(),
                target.getAttributeValue(Attributes.ARMOR_TOUGHNESS), ignore);
        if (dealt > 0f) {
            target.hurt(target.damageSources().magic(), dealt);
        }
    }

    private static void cleave(ServerLevel level, LivingEntity first, @Nullable Entity caster, Entity responsible,
                               float damage, double ignore) {
        double fx = first.getX() - responsible.getX();
        double fz = first.getZ() - responsible.getZ();
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                first.getBoundingBox().inflate(DiffindoRules.CLEAVE_RANGE + 1.0),
                e -> DisableSupport.isEnemyOf(caster, e, first)
                        && DiffindoRules.inCleaveLane(fx, fz, e.getX() - first.getX(), e.getZ() - first.getZ()));
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double d = candidate.distanceToSqr(first);
            if (d < best) {
                best = d;
                next = candidate;
            }
        }
        if (next == null) {
            return;
        }
        sever(next, damage, ignore);
        Vec3 from = first.position().add(0.0, first.getBbHeight() * 0.6, 0.0);
        Vec3 to = next.position().add(0.0, next.getBbHeight() * 0.6, 0.0);
        int steps = Math.max(3, (int) (from.distanceTo(to) * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    /**
     * Cuts every #hogwartsmagic:diffindo_cuttable block within the rank's radius of the struck block,
     * dropping what shears would. A tall plant hit on its top half is cut from its bottom half, where its
     * loot lives. Each block must pass TerrainRules.canBreak (ward, spawn protection, BreakEvent).
     *
     * @return how many blocks were cut
     */
    public static int cutBlocks(ServerLevel level, BlockPos hit, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.DIFFINDO);
        Entity responsible = CombatEffects.responsible(caster);
        ItemStack shears = new ItemStack(Items.SHEARS);
        Set<BlockPos> done = new HashSet<>();
        int cut = 0;
        for (int[] o : BlastShape.offsets(DiffindoRules.cutRadius(rank))) {
            BlockPos pos = hit.offset(o[0], o[1], o[2]);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                pos = pos.below();
                if (!level.isLoaded(pos)) {
                    continue;
                }
                state = level.getBlockState(pos);
            }
            if (!state.is(DamageSupport.DIFFINDO_CUTTABLE) || !done.add(pos)) {
                continue;
            }
            if (!TerrainRules.canBreak(level, pos, state, caster)) {
                continue;
            }
            Block.dropResources(state, level, pos, null, responsible, shears);
            level.levelEvent(2001, pos, Block.getId(state));
            level.removeBlock(pos, false);
            level.gameEvent(responsible, GameEvent.BLOCK_DESTROY, pos);
            cut++;
        }
        return cut;
    }
}
```

- [ ] **Step 2: Delegate from `SpellType`.** Replace:
```java
            case DIFFINDO -> {
                target.hurt(target.damageSources().magic(), scaledSpellDamage(caster, 15.0f)); // High sharp damage
            }
```
with:
```java
            case DIFFINDO -> com.hogwartsmagic.spell.damage.DiffindoSpell.apply(target, caster);
```

- [ ] **Step 3: Diffindo block hits cut.** In `SpellBeamEntity.onHit`, find the end of the Incendio block branch and the start of the Revelio one:
```java
                    } else if (this.level().isEmptyBlock(pos.relative(blockHit.getDirection()))) {
                        this.level().setBlockAndUpdate(pos.relative(blockHit.getDirection()), Blocks.FIRE.defaultBlockState());
                        hitSuccessful = true;
                    }
                } else if (spell == SpellType.REVELIO) {
```
and replace it with:
```java
                    } else if (this.level().isEmptyBlock(pos.relative(blockHit.getDirection()))) {
                        this.level().setBlockAndUpdate(pos.relative(blockHit.getDirection()), Blocks.FIRE.defaultBlockState());
                        hitSuccessful = true;
                    }
                } else if (spell == SpellType.DIFFINDO) {
                    // The magic shears: leaves, vines, webs, wool and grass come free as items.
                    if (this.level() instanceof ServerLevel diffindoLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.damage.DiffindoSpell.cutBlocks(diffindoLevel, pos, getOwner()) > 0;
                    }
                } else if (spell == SpellType.REVELIO) {
```
(Task 8 replaces the Incendio lines above this anchor; this step leaves them untouched.)

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.diffindo.desc": "Severs the target, dealing high physical damage.",
```
with:
```json
  "spell.hogwartsmagic.diffindo.desc": "A precise severing charm that cuts partly through armour. It also cuts leaves, vines, webs, wool and grass free as items, and shears sheep. At Master it cleaves a second enemy behind the first.",
```

- [ ] **Step 5: Check the old hit is gone and the JSON is valid**

Run: `grep -n "scaledSpellDamage(caster, 15.0f)); // High sharp damage" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 6: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/DiffindoSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Diffindo pierce armour, cut plants and wool, shear sheep and cleave at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Sectumsempra (bleeding stacks, Master detonation)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/SectumsempraSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case SECTUMSEMPRA`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.sectumsempra.desc`)

**Interfaces:**
- Consumes: `SectumsempraRules` (Task 1); `DamageSupport.scaled` (Task 2); `DisableSupport.casterRank/holder` (Phase 3); `BleedRules.stacks`, `CombatEffects.responsible`, `ExampleMod.BLEEDING` (Phase 2)
- Produces: `SectumsempraSpell.apply(LivingEntity target, @Nullable Entity caster)`

- [ ] **Step 1: Create `SectumsempraSpell`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.BleedRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Sectumsempra: invisible blades and bleeding that shuts off natural healing (Phase 2's bleeding
 * effect). The stacks a cast can build are capped by rank (1/2/3/3). At Master, hitting a target that
 * is already bleeding tears every wound open: +2 damage per existing stack, in the same hurt call as
 * the cut (a second call would be eaten by invulnerability frames), and the bleed is refreshed.
 *
 * <p>The rank cap on stacks is computed here (SectumsempraRules.nextStacks) and passed to Phase 2's
 * CombatEffects.applyBleeding, which also credits the caster for a bleed-out kill.
 */
public final class SectumsempraSpell {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.4f);

    private SectumsempraSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.SECTUMSEMPRA);
        Holder<MobEffect> bleeding = DisableSupport.holder(ExampleMod.BLEEDING);
        MobEffectInstance current = target.getEffect(bleeding);
        int stacksBefore = current == null ? 0 : BleedRules.stacks(current.getAmplifier());
        float burst = SectumsempraRules.burstDamage(rank, stacksBefore);

        target.hurt(target.damageSources().magic(),
                DamageSupport.scaled(caster, SectumsempraRules.damage(rank) + burst));
        if (!target.isAlive()) {
            return;
        }
        int stacksAfter = SectumsempraRules.nextStacks(rank, stacksBefore);
        // Phase 2's applyBleeding keeps the longer duration, caps the stacks and records the caster for kill credit.
        CombatEffects.applyBleeding(target, SectumsempraRules.BLEED_TICKS, stacksAfter, caster);

        if (burst > 0f) {
            level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    12 + 8 * stacksBefore, target.getBbWidth() * 0.45, target.getBbHeight() * 0.35,
                    target.getBbWidth() * 0.45, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    ModSounds.SPELL_SECTUMSEMPRA.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
        }
    }
}
```

- [ ] **Step 2: Delegate from `SpellType`.** Replace:
```java
            case SECTUMSEMPRA -> {
                target.hurt(target.damageSources().magic(), scaledSpellDamage(caster, 10.0f));
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 2)); // Bleeding
            }
```
with:
```java
            case SECTUMSEMPRA -> com.hogwartsmagic.spell.damage.SectumsempraSpell.apply(target, caster);
```

- [ ] **Step 3: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.sectumsempra.desc": "Slashes the target with invisible blades, causing bleeding.",
```
with:
```json
  "spell.hogwartsmagic.sectumsempra.desc": "Invisible blades that leave the target bleeding: damage over time and no natural healing. Higher ranks open more wounds. At Master, striking a bleeding target tears every wound open at once.",
```

Also add, directly after the existing `"death.attack.hogwartsmagic.bleeding.player"` line (bleed kills become routine with Sectumsempra, and vanilla uses this key when the killer holds a renamed item):
```json
  "death.attack.hogwartsmagic.bleeding.item": "%1$s bled out whilst fighting %2$s using %3$s",
```

- [ ] **Step 4: Check and build**

Run: `grep -n "MobEffects.WITHER, 100, 2" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/SectumsempraSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Sectumsempra build bleeding stacks by rank and detonate them at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Dolohov's Curse (cursed wound, Master spread on death)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/DolohovSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case DOLOHOV_CURSE`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.dolohov_curse.desc`)

**Interfaces:**
- Consumes: `DolohovRules` (Task 1); `DamageSupport.scaled` (Task 2); `DisableSupport.casterRank/holder/isEnemyOf` (Phase 3); `CombatEffects.applyCursedWound/responsible`, `ExampleMod.CURSED_WOUND` (Phase 2)
- Produces: `DolohovSpell.apply(LivingEntity target, @Nullable Entity caster)`, `DolohovSpell.MASTER_MARK_TAG = "hogwartsmagic:dolohov_master"`

- [ ] **Step 1: Create `DolohovSpell`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Dolohov's Curse: a lasting wound. Phase 2's cursed_wound lowers max health (2/3/4/5 hearts for
 * 30/45/60/90s by rank) and milk can't lift it. At Master the victim carries a mark; if it dies while
 * still cursed, the curse leaps to the caster's enemies within 3 blocks for half the hearts (rounded
 * up) and half the time. Spread curses carry no mark, so they never chain.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DolohovSpell {
    public static final String MASTER_MARK_TAG = "hogwartsmagic:dolohov_master";

    private DolohovSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.DOLOHOV_CURSE);
        target.hurt(target.damageSources().magic(), DamageSupport.scaled(caster, DolohovRules.DAMAGE));
        if (!target.isAlive()) {
            return;
        }
        int ticks = DolohovRules.ticks(rank);
        boolean landed = CombatEffects.applyCursedWound(target, ticks, DolohovRules.hearts(rank), caster);
        CompoundTag data = target.getPersistentData();
        if (landed && DolohovRules.spreads(rank)) {
            CompoundTag mark = new CompoundTag();
            mark.putLong("Until", level.getGameTime() + ticks);
            Entity responsible = CombatEffects.responsible(caster);
            if (responsible != null) {
                mark.putUUID("By", responsible.getUUID());
            }
            data.put(MASTER_MARK_TAG, mark);
        } else if (landed) {
            data.remove(MASTER_MARK_TAG);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = dead.getPersistentData();
        if (!data.contains(MASTER_MARK_TAG)) {
            return;
        }
        CompoundTag mark = data.getCompound(MASTER_MARK_TAG);
        data.remove(MASTER_MARK_TAG);
        MobEffectInstance wound = dead.getEffect(DisableSupport.holder(ExampleMod.CURSED_WOUND));
        if (wound == null || mark.getLong("Until") < level.getGameTime()) {
            return;
        }
        Entity caster = mark.hasUUID("By") ? level.getEntity(mark.getUUID("By")) : null;
        int hearts = DolohovRules.spreadHearts(wound.getAmplifier() + 1);
        int ticks = DolohovRules.spreadTicks(DolohovRules.ticks(4));
        double radius = DolohovRules.SPREAD_RADIUS;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(radius),
                e -> e.distanceToSqr(dead) <= radius * radius && DisableSupport.isEnemyOf(caster, e, dead))) {
            CombatEffects.applyCursedWound(nearby, ticks, hearts, caster);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, nearby.getX(), nearby.getY() + nearby.getBbHeight() * 0.5,
                    nearby.getZ(), 12, nearby.getBbWidth() * 0.4, nearby.getBbHeight() * 0.3, nearby.getBbWidth() * 0.4, 0.01);
        }
        level.sendParticles(ParticleTypes.WITCH, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(),
                40, radius * 0.4, 0.5, radius * 0.4, 0.02);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), ModSounds.SPELL_DOLOHOV_CURSE.get(),
                SoundSource.PLAYERS, 1.0f, 0.6f);
    }
}
```

- [ ] **Step 2: Delegate from `SpellType`.** Replace:
```java
            case DOLOHOV_CURSE -> {
                target.hurt(target.damageSources().magic(), scaledSpellDamage(caster, 8.0f));
                // Reduced healing via custom effect or long-lasting effect?
                // Let's use Wither for now as a "curse"
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 0));
            }
```
with:
```java
            case DOLOHOV_CURSE -> com.hogwartsmagic.spell.damage.DolohovSpell.apply(target, caster);
```

- [ ] **Step 3: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.dolohov_curse.desc": "A purple flame curse that deals magic damage and prevents healing.",
```
with:
```json
  "spell.hogwartsmagic.dolohov_curse.desc": "A purple-flame curse that lowers the target's maximum health for a long while. Milk cannot lift it. At Master the curse leaps to nearby enemies when its victim dies.",
```

- [ ] **Step 4: Check and build**

Run: `grep -n "MobEffects.WITHER, 200, 0" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/DolohovSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Dolohov curse a lasting max-health wound that spreads on death at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Confringo (combat blast, ignition, Master brittle payoff)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/ConfringoSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case CONFRINGO`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Confringo block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.confringo.desc`)

**Interfaces:**
- Consumes: `ConfringoRules` (Task 1); `DamageSupport.isSplashTarget` (Task 2); `DisableSupport.casterRank` (Phase 3); `CombatEffects.has`, `ExampleMod.BRITTLE` (Phase 2)
- Produces: `ConfringoSpell.apply(LivingEntity target, @Nullable Entity caster)`, `ConfringoSpell.detonateAt(ServerLevel level, Vec3 at, @Nullable Entity caster)`

- [ ] **Step 1: Create `ConfringoSpell`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Confringo: the combat blast. A vanilla explosion with {@code ExplosionInteraction.NONE} and no fire,
 * so it never breaks or lights blocks (Bombarda breaks, Incendio lights). It sets the target alight;
 * from Adept the burning debris also ignites the caster's enemies nearby. At Master the blast deals
 * ×1.5 to brittle targets: this hook runs at HIGH priority, before Phase 2's brittle hook multiplies
 * and consumes the effect, so the two bonuses stack.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConfringoSpell {
    /** Set only while a Master Confringo explosion is resolving (explosions resolve synchronously). */
    private static float activeBrittleBonus = 1.0f;

    private ConfringoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.CONFRINGO);
        target.igniteForSeconds(ConfringoRules.igniteSeconds(rank));
        detonate(level, target.position(), caster, target, rank);
    }

    /** A Confringo that struck a block: the same blast with no primary target. */
    public static void detonateAt(ServerLevel level, Vec3 at, @Nullable Entity caster) {
        detonate(level, at, caster, null, DisableSupport.casterRank(caster, SpellType.CONFRINGO));
    }

    private static void detonate(ServerLevel level, Vec3 at, @Nullable Entity caster, @Nullable LivingEntity primary, int rank) {
        boolean raining = level.isRainingAt(BlockPos.containing(at));
        float power = ConfringoRules.power(rank, raining);
        activeBrittleBonus = ConfringoRules.brittleBonus(rank);
        try {
            level.explode(caster, at.x, at.y, at.z, power, Level.ExplosionInteraction.NONE);
        } finally {
            activeBrittleBonus = 1.0f;
        }
        double radius = ConfringoRules.splashIgniteRadius(rank);
        if (radius <= 0.0) {
            return;
        }
        int seconds = ConfringoRules.splashIgniteSeconds(rank);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, radius * 2, radius * 2, radius * 2),
                e -> e != primary && e.position().distanceToSqr(at) <= radius * radius
                        && DamageSupport.isSplashTarget(caster, e, primary))) {
            nearby.igniteForSeconds(seconds);
            level.sendParticles(ParticleTypes.FLAME, nearby.getX(), nearby.getY() + nearby.getBbHeight() * 0.5, nearby.getZ(),
                    8, nearby.getBbWidth() * 0.3, nearby.getBbHeight() * 0.3, nearby.getBbWidth() * 0.3, 0.02);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (activeBrittleBonus <= 1.0f || !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !CombatEffects.has(target, ExampleMod.BRITTLE)) {
            return;
        }
        event.setAmount(event.getAmount() * activeBrittleBonus);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    14, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.2);
        }
    }
}
```

- [ ] **Step 2: Delegate from `SpellType` and keep the steam combo.** Replace:
```java
            case CONFRINGO -> {
                target.igniteForSeconds(5);
                float power = target.level().isRainingAt(target.blockPosition()) ? 1.15f : 1.5f;
                target.level().explode(caster, target.getX(), target.getY(), target.getZ(), power, Level.ExplosionInteraction.NONE);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
```
with:
```java
            case CONFRINGO -> {
                com.hogwartsmagic.spell.damage.ConfringoSpell.apply(target, caster);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
```
(The two lines after it, `triggerSteamCombo(target);` and the `setTicksFrozen(... - 120)`, stay.)

- [ ] **Step 3: The block-hit blast.** In `SpellBeamEntity.onHit`, replace:
```java
                } else if (spell == SpellType.CONFRINGO) {
                    // Confringo is the combat blast: fire and knockback, never terrain. Bombarda is the one that breaks blocks.
                    this.level().explode(getOwner(), this.getX(), this.getY(), this.getZ(), 1.5f, true, Level.ExplosionInteraction.NONE);
                    hitSuccessful = true;
```
with:
```java
                } else if (spell == SpellType.CONFRINGO) {
                    // Confringo is the combat blast: it burns creatures, never terrain. Bombarda is the one that breaks blocks.
                    if (this.level() instanceof ServerLevel confringoLevel) {
                        com.hogwartsmagic.spell.damage.ConfringoSpell.detonateAt(confringoLevel, this.position(), getOwner());
                    }
                    hitSuccessful = true;
```

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.confringo.desc": "Creates a fiery explosion at the target.",
```
with:
```json
  "spell.hogwartsmagic.confringo.desc": "A fiery combat blast that sets its targets alight but never harms the ground. Higher ranks hit harder and spread the flames to nearby enemies. At Master it hits brittle (frozen) targets half again as hard.",
```

- [ ] **Step 5: Check and build**

Run: `grep -n "1.5f, true, Level.ExplosionInteraction.NONE\|isRainingAt(target.blockPosition()) ? 1.15f" src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/ConfringoSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Confringo a rank-scaled combat blast that ignites and punishes brittle at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Bombarda (the terrain breaker)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/BombardaSpell.java`
- Create: `src/main/java/com/hogwartsmagic/spell/damage/BombardaBlasts.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case BOMBARDA`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (`onHit`, new block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (description, one message)

**Interfaces:**
- Consumes: `BombardaRules`, `BlastShape` (Task 1); `TerrainRules.canBreak`, `DamageSupport.BOMBARDA_IMMUNE/BOMBARDA_OBSIDIAN_TIER/scaled` (Task 2); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible` (Phase 2)
- Produces: `BombardaSpell.onEntityHit(LivingEntity target, @Nullable Entity caster)`, `BombardaSpell.detonateAtBlock(ServerLevel level, BlockPos pos, Vec3 impact, @Nullable Entity caster)` → `int` positions queued; `BombardaBlasts.queue(ServerLevel level, ServerPlayer caster, int rank, List<BlockPos> positions)` (package-private)

- [ ] **Step 1: Create `BombardaBlasts`** (the budgeted breaker)
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.TerrainRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Breaks Bombarda's blocks a few dozen at a time on the server thread: at most
 * {@link BombardaRules#BLOCKS_PER_TICK} positions per level per tick, nearest first. Every position is
 * re-checked when its turn comes (loaded chunk, still breakable, TerrainRules.canBreak with a fresh
 * BreakEvent), so nothing here can load a chunk or act on a stale block. A job ends early if its caster
 * logs out or leaves the dimension.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BombardaBlasts {
    private static final int MAX_JOBS_PER_LEVEL = 32;
    private static final Map<ResourceKey<Level>, ArrayDeque<Job>> JOBS = new HashMap<>();

    private static final class Job {
        final UUID casterId;
        final int rank;
        final List<BlockPos> positions;
        int next;

        Job(UUID casterId, int rank, List<BlockPos> positions) {
            this.casterId = casterId;
            this.rank = rank;
            this.positions = positions;
        }
    }

    private BombardaBlasts() {
    }

    static void queue(ServerLevel level, ServerPlayer caster, int rank, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }
        ArrayDeque<Job> jobs = JOBS.computeIfAbsent(level.dimension(), key -> new ArrayDeque<>());
        if (jobs.size() < MAX_JOBS_PER_LEVEL) {
            jobs.add(new Job(caster.getUUID(), rank, positions));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        ArrayDeque<Job> jobs = JOBS.get(level.dimension());
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        int budget = BombardaRules.BLOCKS_PER_TICK;
        while (budget > 0 && !jobs.isEmpty()) {
            Job job = jobs.peek();
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(job.casterId);
            if (caster == null || caster.level() != level) {
                jobs.poll();
                continue;
            }
            while (budget > 0 && job.next < job.positions.size()) {
                breakOne(level, job.positions.get(job.next++), caster, job.rank);
                budget--;
            }
            if (job.next >= job.positions.size()) {
                jobs.poll();
            }
        }
    }

    private static void breakOne(ServerLevel level, BlockPos pos, ServerPlayer caster, int rank) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof BaseFireBlock) {
            return;
        }
        if (state.hasBlockEntity() || state.is(DamageSupport.BOMBARDA_IMMUNE)) {
            return; // spawners, containers, horcruxes, wards; bedrock and the mod's locks
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (!BombardaRules.canBreak(rank, hardness, state.is(DamageSupport.BOMBARDA_OBSIDIAN_TIER), level.random.nextDouble())) {
            return;
        }
        if (!TerrainRules.canBreak(level, pos, state, caster)) {
            return;
        }
        if (state.getBlock() instanceof TntBlock) {
            state.onCaughtFire(level, pos, null, caster);
            level.removeBlock(pos, false);
            return;
        }
        level.destroyBlock(pos, level.random.nextDouble() < BombardaRules.dropChance(rank), caster);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            JOBS.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }
}
```

- [ ] **Step 2: Create `BombardaSpell`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bombarda: the terrain-breaking charm. The blast hurts creatures less than Confringo (5..8 damage,
 * falling off to a quarter at the edge, plus knockback; everyone but the caster) and breaks blocks in a
 * sphere whose radius and hardness tier grow with rank. Only a player's Bombarda breaks blocks, and only
 * with mobGriefing on; the blocks themselves are broken over the next few ticks by BombardaBlasts.
 */
public final class BombardaSpell {
    private BombardaSpell() {
    }

    public static void onEntityHit(LivingEntity target, @Nullable Entity caster) {
        if (target.level() instanceof ServerLevel level) {
            detonate(level, target.blockPosition(), target.position(), caster, target);
        }
    }

    /** @return how many positions were queued for breaking (0: nothing to break, or not allowed to) */
    public static int detonateAtBlock(ServerLevel level, BlockPos pos, Vec3 impact, @Nullable Entity caster) {
        return detonate(level, pos, impact, caster, null);
    }

    private static int detonate(ServerLevel level, BlockPos center, Vec3 impact, @Nullable Entity caster,
                                @Nullable LivingEntity primary) {
        int rank = DisableSupport.casterRank(caster, SpellType.BOMBARDA);
        double radius = BombardaRules.radius(rank);
        level.sendParticles(radius >= 2.5 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                impact.x, impact.y, impact.z, radius >= 2.5 ? 1 : 3, radius * 0.3, radius * 0.3, radius * 0.3, 0.0);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                2.0f + (float) radius * 0.3f, 0.9f + level.random.nextFloat() * 0.2f);
        splash(level, impact, caster, primary, rank, radius);
        return queueBlocks(level, center, caster, rank, radius);
    }

    private static void splash(ServerLevel level, Vec3 impact, @Nullable Entity caster, @Nullable LivingEntity primary,
                               int rank, double radius) {
        Entity responsible = CombatEffects.responsible(caster);
        float base = DamageSupport.scaled(caster, BombardaRules.entityDamage(rank));
        double reach = radius + 1.0;
        DamageSource source = level.damageSources().explosion(responsible, responsible);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(impact, reach * 2, reach * 2, reach * 2),
                e -> e != responsible && e.isAlive() && !e.isSpectator())) {
            double distance = e == primary ? 0.0 : e.position().distanceTo(impact);
            float amount = BombardaRules.splashDamage(base, distance, radius);
            if (amount <= 0f) {
                continue;
            }
            e.hurt(source, amount);
            double dx = impact.x - e.getX();
            double dz = impact.z - e.getZ();
            if (dx * dx + dz * dz < 1.0e-4) {
                dx = level.random.nextDouble() - 0.5;
                dz = level.random.nextDouble() - 0.5;
            }
            e.knockback(0.6 * amount / base, dx, dz);
            e.hurtMarked = true;
        }
    }

    private static int queueBlocks(ServerLevel level, BlockPos center, @Nullable Entity caster, int rank, double radius) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return 0; // NPC Bombarda (Voldemort) never breaks terrain
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.bombarda.no_griefing")
                    .withStyle(ChatFormatting.GRAY), true);
            return 0;
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int[] o : BlastShape.offsets(radius)) {
            BlockPos pos = center.offset(o[0], o[1], o[2]);
            if (level.isLoaded(pos) && !level.getBlockState(pos).isAir()) {
                positions.add(pos);
            }
        }
        BombardaBlasts.queue(level, player, rank, positions);
        return positions.size();
    }
}
```

- [ ] **Step 3: Delegate from `SpellType` and keep the steam combo.** Replace:
```java
            case BOMBARDA -> {
                target.level().explode(caster, target.getX(), target.getY(), target.getZ(), 2.5f, Level.ExplosionInteraction.NONE);
                target.hurt(target.damageSources().explosion(null), scaledSpellDamage(caster, 12.0f));
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
```
with:
```java
            case BOMBARDA -> {
                com.hogwartsmagic.spell.damage.BombardaSpell.onEntityHit(target, caster);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
```
(The `triggerSteamCombo(target);` line and the closing braces after it stay.)

- [ ] **Step 4: Bombarda block hits.** In `SpellBeamEntity.onHit`, replace (the anchor Task 3 created):
```java
                } else if (spell == SpellType.DIFFINDO) {
```
with:
```java
                } else if (spell == SpellType.BOMBARDA) {
                    // The terrain breaker: blast now, blocks over the next few ticks (BombardaBlasts).
                    if (this.level() instanceof ServerLevel bombardaLevel) {
                        int queued = com.hogwartsmagic.spell.damage.BombardaSpell.detonateAtBlock(
                                bombardaLevel, pos, this.position(), getOwner());
                        hitSuccessful = true;
                        masteryEligible = queued > 0;
                    }
                } else if (spell == SpellType.DIFFINDO) {
```

- [ ] **Step 5: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.bombarda.desc": "Creates a powerful explosion at the target's location without destroying blocks.",
```
with:
```json
  "spell.hogwartsmagic.bombarda.desc": "Blasts open walls and tunnels, breaking blocks around the impact (never bedrock, spawners, containers or warded blocks). Weaker against creatures than Confringo. Higher ranks blast wider and break harder stone; at Master it breaks obsidian.",
  "message.hogwartsmagic.bombarda.no_griefing": "The blast leaves the ground alone here (mobGriefing is off).",
```

- [ ] **Step 6: Check, build, test and boot a dedicated server** (new tick subscriber that breaks blocks)

Run: `grep -n "2.5f, Level.ExplosionInteraction.NONE\|scaledSpellDamage(caster, 12.0f)" src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.
Then run `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached and the server stops cleanly; no stack trace mentioning `BombardaBlasts`, `BombardaSpell` or any `client` class.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/BombardaSpell.java src/main/java/com/hogwartsmagic/spell/damage/BombardaBlasts.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Bombarda the terrain breaker with budgeted, permission-checked block breaking" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Incendio (lighting, furnace fuel, fire burst)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/damage/IncendioSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case INCENDIO`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Incendio block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json` (`spell.hogwartsmagic.incendio.desc`)

**Interfaces:**
- Consumes: `IncendioRules`, `BlastShape` (Task 1); `TerrainRules.canPlace`, `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible` (Phase 2)
- Produces: `IncendioSpell.apply(LivingEntity target, @Nullable Entity caster)`, `IncendioSpell.onBlockHit(ServerLevel level, BlockPos pos, Direction face, @Nullable Entity caster)` → `boolean` (something was lit)

- [ ] **Step 1: Create `IncendioSpell`**
```java
package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;

/**
 * Incendio: fire as a tool. Creatures burn (4..7s by rank, cut to 40% in rain). On blocks it lights
 * campfires, candles and candle cakes, primes TNT, and adds ten seconds of fuel to a furnace; anything
 * else gets one fire on the struck face, like flint and steel (so it still lights Nether portals). From
 * Adept the flames also catch on flammable blocks around the impact, when doFireTick and mobGriefing are
 * on and it isn't raining there. Every block change passes TerrainRules.canPlace (ward, spawn
 * protection, claims; mobGriefing for NPC casters).
 */
public final class IncendioSpell {
    private IncendioSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.INCENDIO);
        boolean raining = target.level().isRainingAt(target.blockPosition());
        target.igniteForSeconds(IncendioRules.igniteSeconds(rank, raining));
    }

    public static boolean onBlockHit(ServerLevel level, BlockPos pos, Direction face, @Nullable Entity caster) {
        BlockState state = level.getBlockState(pos);
        Entity responsible = CombatEffects.responsible(caster);
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            if (!TerrainRules.canPlace(level, pos, caster)) {
                return false;
            }
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 11);
            level.gameEvent(responsible, GameEvent.BLOCK_CHANGE, pos);
            playIgnite(level, pos);
            return true;
        }
        if (state.getBlock() instanceof TntBlock) {
            if (!TerrainRules.canPlace(level, pos, caster)) {
                return false;
            }
            state.onCaughtFire(level, pos, face, responsible instanceof LivingEntity living ? living : null);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            return true;
        }
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            return TerrainRules.canPlace(level, pos, caster) && fuelFurnace(level, pos, state, furnace);
        }

        int rank = DisableSupport.casterRank(caster, SpellType.INCENDIO);
        BlockPos firePos = pos.relative(face);
        boolean lit = false;
        if (level.isLoaded(firePos) && BaseFireBlock.canBePlacedAt(level, firePos, face)
                && TerrainRules.canPlace(level, firePos, caster)) {
            level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
            level.gameEvent(responsible, GameEvent.BLOCK_PLACE, firePos);
            lit = true;
        }
        if (IncendioRules.spreadsFire(rank) && !level.isRainingAt(firePos)
                && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)
                && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            lit |= spreadFire(level, firePos, IncendioRules.fireRadius(rank), caster) > 0;
        }
        if (lit) {
            playIgnite(level, firePos);
        }
        return lit;
    }

    /**
     * Ten more seconds of burn time (up to 1600). Furnace fields are package-private and this mod has no
     * access transformer, so the burn time goes through the block entity's own NBT round trip; the
     * furnace recomputes its fuel bar from the fuel slot and turns itself off when the time runs out.
     */
    private static boolean fuelFurnace(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace) {
        CompoundTag tag = furnace.saveWithoutMetadata(level.registryAccess());
        int current = tag.getInt("BurnTime");
        int next = IncendioRules.furnaceBurnTime(current);
        if (next <= current) {
            return false;
        }
        tag.putInt("BurnTime", next);
        furnace.loadWithComponents(tag, level.registryAccess());
        furnace.setChanged();
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
        }
        playIgnite(level, pos);
        return true;
    }

    /** Fire on empty spots next to flammable blocks within the radius, nearest first, at most 24. */
    private static int spreadFire(ServerLevel level, BlockPos center, double radius, @Nullable Entity caster) {
        int placed = 0;
        for (int[] o : BlastShape.offsets(radius)) {
            if (placed >= IncendioRules.MAX_FIRES) {
                break;
            }
            BlockPos pos = center.offset(o[0], o[1], o[2]);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !touchesFlammable(level, pos)) {
                continue;
            }
            if (!BaseFireBlock.canBePlacedAt(level, pos, Direction.UP) || !TerrainRules.canPlace(level, pos, caster)) {
                continue;
            }
            level.setBlock(pos, BaseFireBlock.getState(level, pos), 11);
            placed++;
        }
        return placed;
    }

    private static boolean touchesFlammable(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.isLoaded(neighbor) && level.getBlockState(neighbor).isFlammable(level, neighbor, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    private static void playIgnite(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f,
                level.random.nextFloat() * 0.4f + 0.8f);
    }
}
```

- [ ] **Step 2: Delegate from `SpellType` and keep the steam combo.** Replace:
```java
            case INCENDIO -> {
                int burnTime = target.level().isRainingAt(target.blockPosition()) ? 2 : 5;
                target.igniteForSeconds(burnTime);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
```
with:
```java
            case INCENDIO -> {
                com.hogwartsmagic.spell.damage.IncendioSpell.apply(target, caster);
                if (target.getTicksFrozen() > 0 || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
```
(The `triggerSteamCombo(target);` and `setTicksFrozen(... - 80)` lines after it stay.)

- [ ] **Step 3: The block branch.** In `SpellBeamEntity.onHit`, replace:
```java
                } else if (spell == SpellType.INCENDIO) {
                    if (state.getBlock() instanceof CampfireBlock) {
                        if (!state.getValue(CampfireBlock.LIT)) {
                            this.level().setBlockAndUpdate(pos, state.setValue(CampfireBlock.LIT, true));
                            hitSuccessful = true;
                        }
                    } else if (state.getBlock() instanceof TntBlock) {
                        TntBlock.explode(this.level(), pos);
                        this.level().removeBlock(pos, false);
                        hitSuccessful = true;
                    } else if (this.level().isEmptyBlock(pos.relative(blockHit.getDirection()))) {
                        this.level().setBlockAndUpdate(pos.relative(blockHit.getDirection()), Blocks.FIRE.defaultBlockState());
                        hitSuccessful = true;
                    }
                } else if (spell == SpellType.BOMBARDA) {
```
with:
```java
                } else if (spell == SpellType.INCENDIO) {
                    // Campfires, candles, TNT, furnaces, flint-and-steel fire; a fire burst from Adept.
                    if (this.level() instanceof ServerLevel incendioLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.damage.IncendioSpell.onBlockHit(
                                incendioLevel, pos, blockHit.getDirection(), getOwner());
                    }
                } else if (spell == SpellType.BOMBARDA) {
```
If the compiler then warns about unused imports of `CampfireBlock` or `TntBlock` in `SpellBeamEntity`, leave them; unused imports don't fail the build. Remove them only if `grep -n "CampfireBlock\|TntBlock" src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` shows nothing but the import lines.

- [ ] **Step 4: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.incendio.desc": "Sets blocks and mobs on fire.",
```
with:
```json
  "spell.hogwartsmagic.incendio.desc": "Sets creatures alight and lights campfires, candles, TNT and furnaces (ten seconds of fuel). From Adept the flames spread over flammable blocks around the impact, when fire spread and mob griefing are on.",
```

- [ ] **Step 5: Check and build**

Run: `grep -n "TntBlock.explode(this.level(), pos)\|isRainingAt(target.blockPosition()) ? 2 : 5" src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/spell/SpellType.java`
Expected: no output.
Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.
Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/damage/IncendioSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Incendio light campfires, candles, TNT and furnaces and spread fire from Adept" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Full verification

**Files:** none changed unless a check fails.

- [ ] **Step 1: Unit tests and dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots and stops with no stack trace mentioning `spell.damage`, `TerrainRules`, `Couldn't load tag` for a `hogwartsmagic:` tag, or any `client` class.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed). Use a survival world, a wand, the six spells learned, and `/spellmastery setrank @s <spell> <1..4>` to switch ranks.

1. Diffindo I on an unarmoured zombie: about 6 damage. On a zombie in full diamond armour: noticeably more than a plain 6-damage armoured hit would do (about 2.8 instead of about 1.6).
2. Diffindo on a woolly sheep: it is sheared (wool drops) and takes no damage. Again on the shorn sheep: it takes damage.
3. Diffindo I on an oak leaf block: the leaf block drops as an item. On a cobweb: a cobweb item. On the top half of tall grass: the grass drops and both halves go. On stone: fizzle.
4. Diffindo II on leaves: the leaves within 1 block of the hit are cut too. Diffindo IV: within 2.
5. Diffindo IV on a zombie with a second zombie 2 blocks behind it: both are hit, with a sweep trail between them. A cow behind instead: the cow is untouched.
6. Diffindo inside spawn protection as a non-op, and inside an active Cave Inimicum ward: nothing is cut.
7. Sectumsempra I twice on a zombie: bleeding I only (stack cap 1). Rank III three times: bleeding III. Bled players don't regenerate.
8. Sectumsempra IV on a zombie with bleeding III: a big blood burst, the hit does about 14 damage (8 + 6), and the bleed timer resets.
9. Dolohov I on a player alt or a mob: max health drops by 2 hearts for 30s; drinking milk doesn't remove it. Rank IV: 5 hearts, 90s.
10. Dolohov IV on a zombie standing next to two more zombies, then kill it: both neighbours get a 3-heart cursed wound with a purple burst. Kill one of those: nothing spreads further.
11. Confringo I on grass with a zombie: the zombie burns, the grass and ground are untouched, no fire appears on blocks. Rank II: a second zombie 1 block away catches fire too.
12. Confringo IV on a zombie frozen solid by Glacius (Phase 3): the hit is clearly bigger than Confringo IV on an unfrozen zombie (×1.5 on top of the brittle bonus).
13. Bombarda I into a stone wall: a small hole (about 19 blocks) appears over a couple of ticks; about half the stone drops cobblestone. Rank II: wider, every block drops.
14. Bombarda I on a deepslate-ore wall: the ores stay. Rank III: they break. Rank III on obsidian: roughly one block in four breaks. Rank IV: all obsidian in radius 3.5 breaks.
15. Bombarda never breaks: bedrock, a monster spawner, a chest (contents stay), a furnace, a Cave Inimicum ward area, a mandrake plant, the Chamber door. TNT in the blast is primed.
16. `/gamerule mobGriefing false`, then Bombarda a wall: an action-bar line says the ground is left alone; the zombie next to it still takes damage. Set it back to true.
17. Bombarda in spawn protection as a non-op: no blocks break. Then cast Reparo on a Bombarda hole: the blocks come back (each break fired a BreakEvent, which Reparo logs).
18. Bombarda on a zombie: it takes less damage than from Confringo at the same rank, and gets knocked back.
19. Let Voldemort cast Bombarda near terrain: no blocks break.
20. Incendio I on an unlit campfire, a candle, a candle cake, a TNT block and an unfuelled furnace with raw iron inside: the campfire, candle and cake light; the TNT primes; the furnace lights and smelts for about 10 seconds.
21. Incendio I on a plank wall: a single fire on the struck face. Incendio on an obsidian portal frame's inner face: the portal opens.
22. Incendio III next to a wooden house: several fires catch on the planks around the impact (at most 24). With `/gamerule doFireTick false` or `mobGriefing false`, only the single face fire appears. In rain, no burst and creatures burn for only 2-3s.
23. NPC casters: Students and Death Eaters still cast Confringo and Incendio; with mobGriefing off, their Incendio lights no blocks.
24. Bombarda and Diffindo block hits earn mastery XP at most once per 10s (`/spellmastery get @s bombarda`).

- [ ] **Step 3: Record results.** Note any failed check and fix it in a follow-up commit with a message describing what was wrong. Then tell the user Phase 4 is ready to push.

---

## Self-Review

- **Spec coverage (roadmap, Phase 4):**
  - Diffindo: partly ignores armour by rank (T1 `damageAfterArmor`, T3), cuts leaves, vines, webs, wool and tall grass as drops through `canBreak` (claims, `BreakEvent`, ward, spawn protection) (T2, T3), shears sheep (T3), Master cleaves a second target behind (T1 `inCleaveLane`, T3).
  - Sectumsempra: Phase 2 bleeding with rank stack caps (T1, T4); healing shut off is Phase 2's bleeding; Master detonates stacks in the same hurt call (T1, T4).
  - Dolohov: Phase 2 `cursed_wound` with hearts and duration per rank (T1, T5); Master spreads on death, without chaining (T5).
  - Confringo: never breaks or lights terrain (`NONE`, no fire flag, T6); ignites entities, splash from Adept (T6); Master +50% vs brittle via a HIGH-priority hook ahead of Phase 2's brittle hook (T6).
  - Bombarda: breaks blocks by hardness tier and radius per rank, drops 100% from Adept, larger radius and obsidian at Master (T1, T7); respects `mobGriefing`, per-block `BreakEvent` as the caster, spawn protection, adventure mode and Cave Inimicum wards (T2, T7); never bedrock, spawners, block entities, unbreakable or tagged blocks (T2, T7); weaker against creatures than Confringo (T1, T7); budgeted, loaded-chunks-only breaking on the server thread (T7).
  - Incendio: lights campfires, candles, TNT and furnaces with fuel (T8); fire burst from Adept respecting `doFireTick` and `mobGriefing`, Master widest (T1, T8); rain penalty kept (T1, T8); flame jet cut (not planned).
  - House multiplier kept on every direct spell damage (T2 `DamageSupport.scaled`, used in T3, T4, T5, T7). Steam combos kept for Confringo, Incendio and Bombarda (T6, T8, T7).
  - Dedicated-server boots in T2 (tags), T7 (block-breaking tick hook) and T9.
- **Placeholder scan:** no TBD or "similar to". Anchors are quoted code. Task 7 Step 4 anchors on the Diffindo branch Task 3 adds, and Task 8 Step 3 anchors on the Bombarda branch Task 7 adds, so the tasks must run in order (final branch order: Incendio, Bombarda, Diffindo, Revelio).
- **Type consistency:** `DisableSupport.casterRank(Entity, SpellType)` → `int` everywhere. `BlastShape.offsets(double)` → `List<int[]>` in T3, T7, T8. `TerrainRules.canBreak(ServerLevel, BlockPos, BlockState, Entity)` in T3 and T7; `canPlace(ServerLevel, BlockPos, Entity)` in T8. `DamageSupport.scaled(Entity, float)` → `float`. `BombardaBlasts.queue(ServerLevel, ServerPlayer, int, List<BlockPos>)` is package-private and called only from `BombardaSpell` in the same package. `CombatEffects.applyCursedWound(LivingEntity, int ticks, int hearts, Entity)` argument order matches Phase 2. `SectumsempraRules.nextStacks(int rank, int currentStacks)` returns stacks (1..3) and the amplifier passed to `MobEffectInstance` is `stacks - 1`.
