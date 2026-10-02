# Spell Mastery Phase 6a (Mind Spells, Unforgivables and Imperio Thralls) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The mind spells stop being "clear the target and add Nausea", and the Unforgivables get a real cost. Obliviate makes a mob forget the caster (and silences players; at Master it pulls raiders out of their raid). Confundo turns a mob on the nearest other creature (contagious at Master). Crucio becomes a held channel that roots the caster and stuns the victim. Avada Kedavra gets an interruptible windup and a backlash that both shrink with rank, and deals 20% max health to bosses. Imperio at ranks I–III turns its target into a tame-dog thrall that follows the caster and fights whatever the caster fights or whatever attacks the caster: mobs for 20/30/45s, players and bosses for 3/4/5s, two thralls at Expert. Master possession is Phase 6b (`2026-10-02-spell-mastery-phase6b.md`).

**Architecture:** Every per-rank number lives in a Minecraft-free `*Rules` class in a new package `spell/mind/`, tested with JUnit in Task 1. One Minecraft-bound class per spell (`ObliviateSpell`, `ConfundoSpell`, `CrucioChannel`, `AvadaWindup` + `AvadaSpell`, `ImperioSpell` + `ImperioThralls` + `ThrallGoals`) holds the behaviour and that spell's own Forge hooks; `SpellType.applyHitEffect` just delegates. `MindControl` is the shared server guard: a controlled player's own attacks, item use, drops, container opening and casts are refused, while the mod's own forced actions (a thrall's attack) pass through a thread-local bypass; it also offers a target-setting bypass so each spell's `LivingChangeTargetEvent` filter only lets the spell's own picks through. Mob thralls get temporary goals that work on any `Mob` (not only `TamableAnimal`), added on enthrall and removed on release. Player thralls are driven by the server each tick (`ForcedPlayerMovement` pushes velocity with the motion packet); their own client zeroes their input from the synced hidden `imperius` effect. The cast path is extracted into `SpellPackets.CastSpellPacket.castAs(ServerPlayer, CastSpellPacket)` so the Avada windup (and Phase 6b's puppet casts) can re-enter it on the server.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`), SpongePowered Mixin (already set up).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 6: Mind spells and Unforgivables" including the whole "Imperio: Thrall (ranks I–III) and Possession (Master)" section (the user's own design, followed exactly), "Decisions that change the design doc" (Decision 1: bespoke Novice identity + Master capstone, Adept/Expert numeric; Decision 9: **corruption is out of scope**, the Unforgivables pay with backlash and windup instead; Decision 12: Imperio on players, mobs and bosses) and "Global constraints". Numbers the roadmap leaves open come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` sections "C. Mind spells" and "D. The Unforgivables". Where they disagree, the roadmap wins.

**Depends on Phases 2, 3, 4 and 5 (all merged first).** This plan calls, by exact signature:
- Phase 2: `CrowdControl.apply(LivingEntity target, Holder<MobEffect> effect, int baseTicks, @Nullable Entity source)` → `int` ticks applied; `CombatEffects.has(LivingEntity, RegistryObject<MobEffect>)`, `CombatEffects.responsible(@Nullable Entity)`, `CombatEffects.applySilenced(LivingEntity target, int ticks, @Nullable Entity source)` → `boolean`, `CombatEffects.isCastingBlocked(LivingEntity)`; `CombatTags.isBoss(@Nullable Entity)`, `CombatTags.ALLIED`; `ExampleMod.STUNNED`, `ExampleMod.SILENCED` (`RegistryObject<MobEffect>`). A stun or silence landing on a player already calls `ModEvents.interruptActiveCasting` (Phase 2 `CombatEffects.onEffectAdded`), so hooking that method is enough to make stuns and silences break the Avada windup and the Crucio channel.
- Phase 3: `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int`, `DisableSupport.holder(RegistryObject<MobEffect>)` → `Holder<MobEffect>`, `DisableSupport.interrupt(LivingEntity)` (a player's channels, Voldemort's queued windup). Stupefy already calls `DisableSupport.interrupt`, so it breaks the Avada windup through the same hook. Text anchors: the `GLACIUS_FROZEN` registration in `ExampleMod.java` and the line `  "effect.hogwartsmagic.glacius_frozen": "Frozen Solid",` in `en_us.json`.
- Phase 4: `DamageSupport.scaled(@Nullable Entity caster, float base)` → `float` (NPC Avada backlash).
- Phase 5a: nothing is called. Its Protego already lets Avada Kedavra through every shield and absorbs Crucio and Imperio even in the parry window, which is what this phase wants; nothing to change.
- Phase 1: `SpellMasteryData.getRank(Player, SpellType)`, `SpellCosts.manaCost(Player, ItemStack, SpellType)`, `SpellCosts.startCooldown(ServerPlayer, ItemStack, SpellType, long)` and its 6-argument form with `awardMastery`, `MasteryAwards.onCastCompleted(ServerPlayer, SpellType)`.
- `SpellType.applyHitEffect(LivingEntity target, Entity caster, @Nullable Vec3 beamDir)` holds the per-spell `switch`; the 2-argument form delegates with `null`.

Before Task 1, confirm they are in:
```bash
grep -n "public static int casterRank" src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java
grep -n "public static float scaled" src/main/java/com/hogwartsmagic/spell/damage/DamageSupport.java
grep -n "public static boolean applySilenced" src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java
grep -n "public static boolean trySpend" src/main/java/com/hogwartsmagic/spell/support/SupportMana.java
```
Each must print one line. If any doesn't, stop: the phase it belongs to isn't merged.

## Global Constraints

- **Ranks:** every per-rank behaviour reads the caster's rank through `DisableSupport.casterRank(caster, spell)` (players: `SpellMasteryData.getRank`, 1..4). **NPC casters (Students, Death Eaters/`WizardEntity`, Voldemort) and ownerless beams are rank 1**, with one exception: an NPC's Avada backlash stays today's 2 damage × house multiplier.
- **Per-rank numbers (ticks at 20/s):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Obliviate | mob drops its target and ignores the caster for 100 unless the caster hurts it; player `silenced` 40 | 200; silence 60 | 400; silence 60 | 600; silence 80; **memory charm:** a raider leaves its vanilla raid (and can't rejoin), a Death Eater leaves its Death Eater raid and loses raid-leader status, and the mob forgets its home (`clearRestriction`) |
  | Confundo | mob attacks the nearest other creature within 12 blocks (not the caster) for 100, retargeting every 20; player: Nausea 160 + Slowness I 50 | 140; Nausea 200, Slowness 70 | 180; 240, 90 | 240; 300, 120; **contagious:** a confused mob that hurts another mob confuses it for 60 (spread confusion doesn't spread again) |
  | Imperio | thrall: mobs up to 40 max health for 400; players and bosses 60; 1 thrall | up to 60, 600; 80; 1 thrall; the thrall gets Strength I | up to 100, 900; 100; 2 thralls; Strength I | possession (Phase 6b); until 6b ships, today's hold-to-control channel stays as the Master behaviour |
  | Crucio | hold up to 40: the target is `stunned` (through `CrowdControl`) and takes 1.5 every 10; the caster is rooted | up to 60 | up to 80 | up to 100; **a surviving non-boss mob flees from the caster for 200** |
  | Avada Kedavra | windup 30, then a beam that kills non-bosses; backlash 4 to the caster on a hit | windup 24; backlash 3 | windup 20; backlash 2 | windup 16; no backlash |

- **Bosses** (`CombatTags.isBoss`): Obliviate and Confundo do nothing to them (smoke puff, "resists" line to a player caster). Avada's instant kill doesn't work: they take 20% of their max health instead. Crucio damages them but doesn't stun them. Imperio enthralls them for the short column (60/80/100) and ignores the health cap.
- **Avada windup:** green sparks around the caster; it breaks on a stun, a silence, Stupefy (all through `ModEvents.interruptActiveCasting`) or any single hit of 4+ damage. A broken windup costs no mana and puts Avada on a 100-tick cooldown. Switching wand or spell drops it with no penalty. Mana is taken when the beam fires.
- **Crucio channel:** the beam starts it on impact. A player caster keeps it alive with the existing hold heartbeat (`CastSpellPacket` with `channelHeartbeat`), exactly like Wingardium; letting go, 6 ticks without a heartbeat, the rank cap, more than 24 blocks apart, a death, or the caster being interrupted ends it. An NPC caster's curse runs its full rank-1 length with no hold. The caster carries the hidden `rooted` effect (movement speed and jump strength −100%, synced to their client by vanilla attribute sync). The Crucio cooldown starts at cast, as today.
- **Imperio thralls:** a thrall follows its owner (teleports beside them beyond 12 blocks), attacks whoever hurt the owner in the last 100 ticks, else whatever the owner hurt in the last 100 ticks (melee, projectiles and spell hits all count), and never targets the owner, the owner's other thralls, team-mates or creative/spectator players. A thrall may still hit back at whatever hurts it. Mob thralls get the scoreboard tag `CombatTags.ALLIED` (only when the owner is a player, and only if they didn't already have it) and green sparkles plus a "◆ Thrall of <owner>" nameplate. Player thralls are walked by the server toward their owner or the owner's fight, attack it with `Player.attack`, see a green "Imperius" vignette, and their own keys (movement, attack, use, drop, swap, inventory, hotbar) do nothing. **Imperio's cooldown starts when the caster's last thrall ends** (expiry, death, unload, owner logout), and that is when the cast earns Imperio XP. Casting Imperio while at the thrall cap is refused before mana is spent. `imperioAffectsPlayers` (config, default on) gates player thralls; mobs are always allowed. A mob thrall's AI goes back to normal on release.
- **Removed on purpose:** Obliviate's Blindness/Nausea and Confundo's Nausea on mobs, Crucio's Wither/Slowness/Weakness, Imperio's "retarget a nearby hostile" and its Glowing. The design doc's Obliviate Expert area and villager gossip reset, Confundo Adept +25% ally damage and `confused_aim`, Crucio Adept Weakness, Expert 30% movement and Master armour wear, and Avada's Priori Incantatem beam collision are cut (Decision 1 and Decision 8). Corruption doesn't exist (Decision 9).
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/` (or an existing `Dist.CLIENT` subscriber such as `event/ClientModEvents`), is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch or a `Dist.CLIENT` event subscriber, and talks back through C2S packets the server validates. This plan adds one S2C packet (`ThrallLabelPacket`) and no C2S packet.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine. (`SpellType.applyHitEffect` is common code; its existing switch stays.)
- Screens: `renderTransparentBackground`, no blur. (This plan adds no screens.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards. (This plan changes no terrain. Crucio's Master flee and thrall teleports only move entities.)
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs).
- `en_us.json` must stay valid JSON after every edit: replacements keep the original line's trailing comma, and an inserted line ends with a comma and goes after a line that already ends with one.
- Line numbers drift (other phases edit the same files). Every edit below names the exact text to find; trust the text, not a number.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/mind/RankIndex.java` | Pure: rank 1..4 → table index, clamped (package-private) |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ObliviateRules.java` | Pure: ignore time, silence, memory charm |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ConfundoRules.java` | Pure: confusion time, contagion, player debuffs |
| Create `src/main/java/com/hogwartsmagic/spell/mind/CrucioRules.java` | Pure: channel length, damage ticks, heartbeat, flee |
| Create `src/main/java/com/hogwartsmagic/spell/mind/AvadaRules.java` | Pure: windup, backlash, boss damage, interrupt threshold |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ImperioRules.java` | Pure: thrall time, count, health cap, follow and assist numbers |
| Create `src/main/java/com/hogwartsmagic/spell/mind/MindControl.java` | Controlled-player guards, forced attack, target-rule bypass, stale `rooted` cleanup |
| Create `src/main/java/com/hogwartsmagic/spell/mind/MindFx.java` | Shared particles and the "resists" line |
| Create `src/main/java/com/hogwartsmagic/spell/mind/MindHooks.java` | One hook at the top of `applyHitEffect`: who hit whom with a spell |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ObliviateSpell.java` | Forget, silence, Master memory charm, target filter |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ConfundoSpell.java` | Confusion, retargeting, target filter, contagion |
| Create `src/main/java/com/hogwartsmagic/spell/mind/AvadaWindup.java` | Player windup: start, tick, interrupt, release into the cast path |
| Create `src/main/java/com/hogwartsmagic/spell/mind/AvadaSpell.java` | Kill, boss damage, backlash |
| Create `src/main/java/com/hogwartsmagic/spell/mind/CrucioChannel.java` | Channel registry, heartbeat, damage, root, stun, Master flee |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java` | Imperio hit: thrall at I–III, recent-hit mark at Master |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java` | Thrall registry, owner fights, player driving, cooldown on release, cleanup |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ThrallGoals.java` | Follow, Assist and Brawl goals for any `Mob` |
| Create `src/main/java/com/hogwartsmagic/spell/mind/ForcedPlayerMovement.java` | Server-driven player velocity with the motion packet |
| Create `src/main/java/com/hogwartsmagic/client/ImperiusClient.java` | Thrall nameplates, a thrall player's input lock |
| Create `src/main/java/com/hogwartsmagic/client/MindControlOverlay.java` | Green Imperius vignette |
| Create `src/main/java/com/hogwartsmagic/mixin/client/MindControlGuiMixin.java` | Draws the overlay at the tail of `Gui#render` |
| Modify `src/main/resources/hogwartsmagic.mixins.json` | Register the client mixin |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | `IMPERIUS`, `CONFUNDED`, `ROOTED` effects |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | `imperioAffectsPlayers` |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Five cases delegate; the `MindHooks` call |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `castAs` extraction, controlled-player refusals, Avada windup branch, Crucio heartbeat and release, Imperio cap and rank gate, `ThrallLabelPacket` |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | `removeFromWizardRaid`; interrupt hooks; Crucio and Imperio client branches in `handleWandUse` |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Crucio hold |
| Modify `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java` | Crucio hold start; Imperio hold only at Master |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | `thrallLabel` dispatch |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Effect names, messages, five descriptions |
| Create tests under `src/test/java/com/hogwartsmagic/spell/mind/` | Unit tests |

---

### Task 1: The pure rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/RankIndex.java`, `ObliviateRules.java`, `ConfundoRules.java`, `CrucioRules.java`, `AvadaRules.java`, `ImperioRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mind/ObliviateRulesTest.java`, `ConfundoRulesTest.java`, `CrucioRulesTest.java`, `AvadaRulesTest.java`, `ImperioRulesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private)
  - `ObliviateRules`: `static int ignoreTicks(int rank)`, `static int silenceTicks(int rank)`, `static boolean memoryCharm(int rank)`, `static boolean stillForgotten(long now, long until)`
  - `ConfundoRules.CONTAGION_TICKS = 60`, `RETARGET_INTERVAL = 20`, `SEARCH_RADIUS = 12.0`, `AMP_PLAIN = 0`, `AMP_CONTAGIOUS = 1`; `static int confuseTicks(int rank)`, `static boolean contagious(int rank)`, `static int amplifierFor(boolean contagious)`, `static int playerNauseaTicks(int rank)`, `static int playerSlownessTicks(int rank)`, `static boolean spreads(boolean sourceContagious, boolean victimBoss, boolean victimAlreadyConfused)`
  - `CrucioRules.DAMAGE_INTERVAL = 10`, `DAMAGE = 1.5f`, `HEARTBEAT_TIMEOUT = 6`, `MAX_RANGE = 24.0`, `FLEE_TICKS = 200`; `static int channelTicks(int rank)`, `static boolean fleesAfter(int rank)`, `static boolean isDamageTick(long elapsed)`, `static float fullChannelDamage(int rank)`, `static boolean heartbeatLapsed(long now, long lastSeen)`
  - `AvadaRules.BOSS_FRACTION = 0.20f`, `INTERRUPT_DAMAGE = 4.0f`, `INTERRUPT_LOCKOUT_TICKS = 100`, `NPC_BACKLASH = 2.0f`; `static int windupTicks(int rank)`, `static float backlash(int rank)`, `static float bossDamage(float maxHealth)`, `static boolean interrupts(float damage)`, `static boolean windupDone(long elapsed, int rank)`
  - `ImperioRules.FOLLOW_START = 6.0`, `FOLLOW_STOP = 2.5`, `TELEPORT_DISTANCE = 12.0`, `MAX_TARGET_DISTANCE = 32.0`, `PLAYER_REACH = 3.0`, `PLAYER_WALK_SPEED = 0.21`, `PLAYER_CHASE_SPEED = 0.25`, `ASSIST_MEMORY_TICKS = 100`, `BRAWL_DAMAGE = 2.0f`, `BRAWL_INTERVAL = 20`; `static int thrallTicks(int rank, boolean playerOrBoss)`, `static int maxThralls(int rank)`, `static float maxMobHealth(int rank)`, `static boolean grantsStrength(int rank)`, `static boolean isPossessionRank(int rank)`, `static boolean canEnthrall(int rank, float maxHealth, boolean boss, boolean player)`, `static boolean atCap(int rank, int current)`, `static boolean fresh(long now, long at)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/mind/ObliviateRulesTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObliviateRulesTest {
    @Test
    void mobsIgnoreTheCasterLongerWithRank() {
        assertEquals(100, ObliviateRules.ignoreTicks(1));
        assertEquals(200, ObliviateRules.ignoreTicks(2));
        assertEquals(400, ObliviateRules.ignoreTicks(3));
        assertEquals(600, ObliviateRules.ignoreTicks(4));
    }

    @Test
    void playersAreSilencedTwoToFourSeconds() {
        assertEquals(40, ObliviateRules.silenceTicks(1));
        assertEquals(60, ObliviateRules.silenceTicks(2));
        assertEquals(60, ObliviateRules.silenceTicks(3));
        assertEquals(80, ObliviateRules.silenceTicks(4));
    }

    @Test
    void onlyMasterCastsTheMemoryCharm() {
        assertFalse(ObliviateRules.memoryCharm(3));
        assertTrue(ObliviateRules.memoryCharm(4));
    }

    @Test
    void ranksOutsideOneToFourAreClamped() {
        assertEquals(100, ObliviateRules.ignoreTicks(0));
        assertEquals(600, ObliviateRules.ignoreTicks(9));
    }

    @Test
    void forgettingEndsAtTheStoredTime() {
        assertTrue(ObliviateRules.stillForgotten(99L, 100L));
        assertFalse(ObliviateRules.stillForgotten(100L, 100L));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/mind/ConfundoRulesTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfundoRulesTest {
    @Test
    void confusionLastsFiveToTwelveSeconds() {
        assertEquals(100, ConfundoRules.confuseTicks(1));
        assertEquals(140, ConfundoRules.confuseTicks(2));
        assertEquals(180, ConfundoRules.confuseTicks(3));
        assertEquals(240, ConfundoRules.confuseTicks(4));
    }

    @Test
    void onlyMasterIsContagious() {
        assertFalse(ConfundoRules.contagious(3));
        assertTrue(ConfundoRules.contagious(4));
        assertEquals(ConfundoRules.AMP_CONTAGIOUS, ConfundoRules.amplifierFor(true));
        assertEquals(ConfundoRules.AMP_PLAIN, ConfundoRules.amplifierFor(false));
    }

    @Test
    void playersReelForTheConfusionPlusThreeSeconds() {
        assertEquals(160, ConfundoRules.playerNauseaTicks(1));
        assertEquals(50, ConfundoRules.playerSlownessTicks(1));
        assertEquals(300, ConfundoRules.playerNauseaTicks(4));
        assertEquals(120, ConfundoRules.playerSlownessTicks(4));
    }

    @Test
    void contagionNeedsAContagiousSourceAndAFreshNonBossVictim() {
        assertTrue(ConfundoRules.spreads(true, false, false));
        assertFalse(ConfundoRules.spreads(false, false, false));
        assertFalse(ConfundoRules.spreads(true, true, false));
        assertFalse(ConfundoRules.spreads(true, false, true));
        assertEquals(60, ConfundoRules.CONTAGION_TICKS);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/mind/CrucioRulesTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrucioRulesTest {
    @Test
    void channelGrowsFromTwoToFiveSeconds() {
        assertEquals(40, CrucioRules.channelTicks(1));
        assertEquals(60, CrucioRules.channelTicks(2));
        assertEquals(80, CrucioRules.channelTicks(3));
        assertEquals(100, CrucioRules.channelTicks(4));
    }

    @Test
    void damageLandsEveryHalfSecondButNotOnTheFirstTick() {
        assertFalse(CrucioRules.isDamageTick(0));
        assertFalse(CrucioRules.isDamageTick(9));
        assertTrue(CrucioRules.isDamageTick(10));
        assertTrue(CrucioRules.isDamageTick(20));
    }

    @Test
    void aFullChannelDealsSixToFifteen() {
        assertEquals(6.0f, CrucioRules.fullChannelDamage(1), 1e-6);
        assertEquals(15.0f, CrucioRules.fullChannelDamage(4), 1e-6);
    }

    @Test
    void onlyMasterMakesTheVictimFlee() {
        assertFalse(CrucioRules.fleesAfter(3));
        assertTrue(CrucioRules.fleesAfter(4));
    }

    @Test
    void sixTicksWithoutAHeartbeatEndsTheChannel() {
        assertFalse(CrucioRules.heartbeatLapsed(106L, 100L));
        assertTrue(CrucioRules.heartbeatLapsed(107L, 100L));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/mind/AvadaRulesTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvadaRulesTest {
    @Test
    void windupShrinksFromOneAndAHalfSecondsToPointEight() {
        assertEquals(30, AvadaRules.windupTicks(1));
        assertEquals(24, AvadaRules.windupTicks(2));
        assertEquals(20, AvadaRules.windupTicks(3));
        assertEquals(16, AvadaRules.windupTicks(4));
    }

    @Test
    void backlashShrinksToNothingAtMaster() {
        assertEquals(4.0f, AvadaRules.backlash(1), 1e-6);
        assertEquals(3.0f, AvadaRules.backlash(2), 1e-6);
        assertEquals(2.0f, AvadaRules.backlash(3), 1e-6);
        assertEquals(0.0f, AvadaRules.backlash(4), 1e-6);
    }

    @Test
    void bossesLoseAFifthOfTheirMaxHealth() {
        assertEquals(60.0f, AvadaRules.bossDamage(300.0f), 1e-4);
    }

    @Test
    void hitsOfFourOrMoreBreakTheWindup() {
        assertFalse(AvadaRules.interrupts(3.9f));
        assertTrue(AvadaRules.interrupts(4.0f));
    }

    @Test
    void windupFinishesOnItsLastTick() {
        assertFalse(AvadaRules.windupDone(29L, 1));
        assertTrue(AvadaRules.windupDone(30L, 1));
        assertTrue(AvadaRules.windupDone(16L, 4));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/mind/ImperioRulesTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImperioRulesTest {
    @Test
    void mobThrallsLastTwentyThirtyFortyFiveSeconds() {
        assertEquals(400, ImperioRules.thrallTicks(1, false));
        assertEquals(600, ImperioRules.thrallTicks(2, false));
        assertEquals(900, ImperioRules.thrallTicks(3, false));
    }

    @Test
    void playersAndBossesGetThreeFourFiveSeconds() {
        assertEquals(60, ImperioRules.thrallTicks(1, true));
        assertEquals(80, ImperioRules.thrallTicks(2, true));
        assertEquals(100, ImperioRules.thrallTicks(3, true));
    }

    @Test
    void expertHoldsTwoThralls() {
        assertEquals(1, ImperioRules.maxThralls(1));
        assertEquals(1, ImperioRules.maxThralls(2));
        assertEquals(2, ImperioRules.maxThralls(3));
        assertFalse(ImperioRules.atCap(3, 1));
        assertTrue(ImperioRules.atCap(3, 2));
        assertTrue(ImperioRules.atCap(1, 1));
    }

    @Test
    void healthCapAppliesToMobsOnly() {
        assertTrue(ImperioRules.canEnthrall(1, 40.0f, false, false));
        assertFalse(ImperioRules.canEnthrall(1, 40.5f, false, false));
        assertTrue(ImperioRules.canEnthrall(2, 60.0f, false, false));
        assertTrue(ImperioRules.canEnthrall(3, 100.0f, false, false));
        assertTrue(ImperioRules.canEnthrall(1, 500.0f, true, false));
        assertTrue(ImperioRules.canEnthrall(1, 20.0f, false, true));
    }

    @Test
    void strengthFromAdeptAndPossessionAtMaster() {
        assertFalse(ImperioRules.grantsStrength(1));
        assertTrue(ImperioRules.grantsStrength(2));
        assertTrue(ImperioRules.grantsStrength(3));
        assertFalse(ImperioRules.isPossessionRank(3));
        assertTrue(ImperioRules.isPossessionRank(4));
    }

    @Test
    void assistMemoryLastsFiveSeconds() {
        assertTrue(ImperioRules.fresh(200L, 100L));
        assertFalse(ImperioRules.fresh(201L, 100L));
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.mind.*"`
Expected: compilation fails (`cannot find symbol: class ObliviateRules` and similar).

- [ ] **Step 3: Write the rules**

`src/main/java/com/hogwartsmagic/spell/mind/RankIndex.java`:
```java
package com.hogwartsmagic.spell.mind;

/** Maps a mastery rank (1..4) to a table index (0..3). Anything outside is clamped; NPC casters pass 1. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mind/ObliviateRules.java`:
```java
package com.hogwartsmagic.spell.mind;

/** Obliviate: the escape spell. Mobs forget the caster, players are silenced, Master wipes a raider's raid and home. */
public final class ObliviateRules {
    private static final int[] IGNORE_TICKS = {100, 200, 400, 600};
    private static final int[] SILENCE_TICKS = {40, 60, 60, 80};

    private ObliviateRules() {
    }

    /** How long a mob ignores the caster, unless the caster hurts it first. */
    public static int ignoreTicks(int rank) {
        return IGNORE_TICKS[RankIndex.of(rank)];
    }

    /** Silence on a player target. */
    public static int silenceTicks(int rank) {
        return SILENCE_TICKS[RankIndex.of(rank)];
    }

    /** Master capstone: the mob also forgets its raid and its home. */
    public static boolean memoryCharm(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Still forgetting at {@code now}; {@code until} is the game time the forgetting ends. */
    public static boolean stillForgotten(long now, long until) {
        return now < until;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mind/ConfundoRules.java`:
```java
package com.hogwartsmagic.spell.mind;

/** Confundo: friendly fire. The mob attacks the nearest other creature; at Master the confusion spreads. */
public final class ConfundoRules {
    private static final int[] CONFUSE_TICKS = {100, 140, 180, 240};
    public static final int CONTAGION_TICKS = 60;
    public static final int RETARGET_INTERVAL = 20;
    public static final double SEARCH_RADIUS = 12.0;
    /** Amplifier of the confunded marker: a plain confusion, or a Master one that spreads on hit. */
    public static final int AMP_PLAIN = 0;
    public static final int AMP_CONTAGIOUS = 1;
    private static final int PLAYER_NAUSEA_BONUS = 60;

    private ConfundoRules() {
    }

    public static int confuseTicks(int rank) {
        return CONFUSE_TICKS[RankIndex.of(rank)];
    }

    public static boolean contagious(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static int amplifierFor(boolean contagious) {
        return contagious ? AMP_CONTAGIOUS : AMP_PLAIN;
    }

    /** Nausea on a player outlasts the mob confusion by three seconds, so it reads. */
    public static int playerNauseaTicks(int rank) {
        return confuseTicks(rank) + PLAYER_NAUSEA_BONUS;
    }

    public static int playerSlownessTicks(int rank) {
        return confuseTicks(rank) / 2;
    }

    /** Only a Master confusion spreads, never onto a boss, never onto a mob that is already confused. */
    public static boolean spreads(boolean sourceContagious, boolean victimBoss, boolean victimAlreadyConfused) {
        return sourceContagious && !victimBoss && !victimAlreadyConfused;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mind/CrucioRules.java`:
```java
package com.hogwartsmagic.spell.mind;

/** Crucio: a held channel. The caster is rooted, the victim stunned and hurt; at Master a survivor flees. */
public final class CrucioRules {
    private static final int[] CHANNEL_TICKS = {40, 60, 80, 100};
    public static final int DAMAGE_INTERVAL = 10;
    public static final float DAMAGE = 1.5f;
    public static final int HEARTBEAT_TIMEOUT = 6;
    public static final double MAX_RANGE = 24.0;
    public static final int FLEE_TICKS = 200;

    private CrucioRules() {
    }

    public static int channelTicks(int rank) {
        return CHANNEL_TICKS[RankIndex.of(rank)];
    }

    public static boolean fleesAfter(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Damage lands every half second after the channel starts (elapsed 10, 20, ...). */
    public static boolean isDamageTick(long elapsed) {
        return elapsed > 0 && elapsed % DAMAGE_INTERVAL == 0;
    }

    public static float fullChannelDamage(int rank) {
        return (channelTicks(rank) / DAMAGE_INTERVAL) * DAMAGE;
    }

    public static boolean heartbeatLapsed(long now, long lastSeen) {
        return now - lastSeen > HEARTBEAT_TIMEOUT;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mind/AvadaRules.java`:
```java
package com.hogwartsmagic.spell.mind;

/** Avada Kedavra: a windup anyone can break, a backlash that fades with rank, a fifth of a boss's health. */
public final class AvadaRules {
    private static final int[] WINDUP_TICKS = {30, 24, 20, 16};
    private static final float[] BACKLASH = {4.0f, 3.0f, 2.0f, 0.0f};
    public static final float BOSS_FRACTION = 0.20f;
    public static final float INTERRUPT_DAMAGE = 4.0f;
    public static final int INTERRUPT_LOCKOUT_TICKS = 100;
    /** NPC casters keep today's backlash (before the house multiplier). */
    public static final float NPC_BACKLASH = 2.0f;

    private AvadaRules() {
    }

    public static int windupTicks(int rank) {
        return WINDUP_TICKS[RankIndex.of(rank)];
    }

    public static float backlash(int rank) {
        return BACKLASH[RankIndex.of(rank)];
    }

    public static float bossDamage(float maxHealth) {
        return maxHealth * BOSS_FRACTION;
    }

    public static boolean interrupts(float damage) {
        return damage >= INTERRUPT_DAMAGE;
    }

    public static boolean windupDone(long elapsed, int rank) {
        return elapsed >= windupTicks(rank);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mind/ImperioRules.java`:
```java
package com.hogwartsmagic.spell.mind;

/**
 * Imperio. Ranks I-III make a tame-dog thrall; Master is possession (Phase 6b). Players and bosses get
 * the short column; mobs the long one, under a max-health cap that bosses ignore.
 */
public final class ImperioRules {
    private static final int[] MOB_TICKS = {400, 600, 900, 900};
    private static final int[] SHORT_TICKS = {60, 80, 100, 100};
    private static final int[] MAX_THRALLS = {1, 1, 2, 2};
    private static final float[] MAX_MOB_HEALTH = {40.0f, 60.0f, 100.0f, 100.0f};
    public static final double FOLLOW_START = 6.0;
    public static final double FOLLOW_STOP = 2.5;
    public static final double TELEPORT_DISTANCE = 12.0;
    public static final double MAX_TARGET_DISTANCE = 32.0;
    public static final double PLAYER_REACH = 3.0;
    public static final double PLAYER_WALK_SPEED = 0.21;
    public static final double PLAYER_CHASE_SPEED = 0.25;
    public static final int ASSIST_MEMORY_TICKS = 100;
    public static final float BRAWL_DAMAGE = 2.0f;
    public static final int BRAWL_INTERVAL = 20;

    private ImperioRules() {
    }

    public static int thrallTicks(int rank, boolean playerOrBoss) {
        return playerOrBoss ? SHORT_TICKS[RankIndex.of(rank)] : MOB_TICKS[RankIndex.of(rank)];
    }

    public static int maxThralls(int rank) {
        return MAX_THRALLS[RankIndex.of(rank)];
    }

    public static float maxMobHealth(int rank) {
        return MAX_MOB_HEALTH[RankIndex.of(rank)];
    }

    public static boolean grantsStrength(int rank) {
        return RankIndex.of(rank) >= 1;
    }

    public static boolean isPossessionRank(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean canEnthrall(int rank, float maxHealth, boolean boss, boolean player) {
        return player || boss || maxHealth <= maxMobHealth(rank);
    }

    public static boolean atCap(int rank, int current) {
        return current >= maxThralls(rank);
    }

    /** An owner's fight (who hurt them, whom they hurt) stays interesting for five seconds. */
    public static boolean fresh(long now, long at) {
        return now - at <= ASSIST_MEMORY_TICKS;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.mind.*"`
Expected: `BUILD SUCCESSFUL`, 25 tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/RankIndex.java src/main/java/com/hogwartsmagic/spell/mind/ObliviateRules.java src/main/java/com/hogwartsmagic/spell/mind/ConfundoRules.java src/main/java/com/hogwartsmagic/spell/mind/CrucioRules.java src/main/java/com/hogwartsmagic/spell/mind/AvadaRules.java src/main/java/com/hogwartsmagic/spell/mind/ImperioRules.java src/test/java/com/hogwartsmagic/spell/mind/ObliviateRulesTest.java src/test/java/com/hogwartsmagic/spell/mind/ConfundoRulesTest.java src/test/java/com/hogwartsmagic/spell/mind/CrucioRulesTest.java src/test/java/com/hogwartsmagic/spell/mind/AvadaRulesTest.java src/test/java/com/hogwartsmagic/spell/mind/ImperioRulesTest.java
git commit -m "Add the tested per-rank rules for the mind spells and Unforgivables" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Shared plumbing (effects, config, guards, the extracted cast path)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (after Phase 3's `GLACIUS_FROZEN` registration)
- Modify: `src/main/java/com/hogwartsmagic/Config.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/MindControl.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/MindFx.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.handle`, `ImperioControlPacket.handle`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `CombatEffects.has`, `CombatEffects.responsible` (Phase 2).
- Produces:
  - `ExampleMod.IMPERIUS`, `ExampleMod.CONFUNDED`, `ExampleMod.ROOTED`: `RegistryObject<MobEffect>` (all applied hidden: not ambient, no particles, no icon). `ROOTED` carries movement speed −100% and jump strength −100% (`ADD_MULTIPLIED_TOTAL`).
  - `Config.isImperioAffectsPlayers()` → `boolean`
  - `MindControl.isControlled(LivingEntity)` → `boolean` (has `imperius`; Phase 6b widens it), `MindControl.forcedAttack(ServerPlayer attacker, Entity target)`, `MindControl.setTargetBypassing(Mob mob, @Nullable LivingEntity target)`, `MindControl.isBypassingTargetRules()` → `boolean`
  - `MindFx.burst(LivingEntity target, ParticleOptions particle)`, `MindFx.resisted(LivingEntity target, @Nullable Entity caster)`, `MindFx.tell(@Nullable Entity who, String key, Object... args)`
  - `SpellPackets.CastSpellPacket.castAs(ServerPlayer player, CastSpellPacket p)`: the whole cast path, run as `player`

- [ ] **Step 1: Register the three effects.** In `ExampleMod.java`, directly after Phase 3's:
```java
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> GLACIUS_FROZEN = MOB_EFFECTS.register("glacius_frozen",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x88FFFF) {});
```
add:
```java

    // Mind spells and Unforgivables (Spell Mastery Phase 6). All three are applied hidden.
    /** Imperio thrall marker. The thrall player's own client zeroes their input while it lasts; ImperioThralls owns the state. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> IMPERIUS = MOB_EFFECTS.register("imperius",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x88FF88) {});
    /** Confundo marker: the mob attacks the nearest other creature. Amplifier 1 marks a contagious (Master) confusion. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> CONFUNDED = MOB_EFFECTS.register("confunded",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0xFFAAAA) {});
    /**
     * Rooted: movement speed and jump strength drop to zero. Attributes sync to the player's own client, so a
     * rooted player can't walk or jump there either. Worn by a channelling Crucio caster (and, in 6b, a
     * possessing caster's body).
     */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> ROOTED = MOB_EFFECTS.register("rooted",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x5A3D7A) {}
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "rooted_speed"), -1.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "rooted_jump"), -1.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
```
The effects are always added with `showIcon = false`, so they need no textures.

- [ ] **Step 2: The config toggle.** In `Config.java`:

Directly above `    static final ForgeConfigSpec SPEC = BUILDER.build();` add:
```java
    public static final ForgeConfigSpec.BooleanValue IMPERIO_AFFECTS_PLAYERS = BUILDER
            .comment("Imperio: if true, players can be enthralled (ranks I-III) and possessed (Master). Mobs are always allowed.")
            .define("imperioAffectsPlayers", true);
```
Directly above `    private Config() {` add:
```java
    public static boolean imperioAffectsPlayers = true;
```
In `onLoad`, directly after `        pvpCrowdControlMultiplier = PVP_CROWD_CONTROL_MULTIPLIER.get();` add:
```java
        imperioAffectsPlayers = IMPERIO_AFFECTS_PLAYERS.get();
```
Directly after the method
```java
    public static double getPvpCrowdControlMultiplier() {
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }
```
add:
```java

    /** Imperio thralls and possession on players; mobs are always fair game. */
    public static boolean isImperioAffectsPlayers() {
        return imperioAffectsPlayers;
    }
```

- [ ] **Step 3: Create `MindControl`**

`src/main/java/com/hogwartsmagic/spell/mind/MindControl.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The shared rules for a body that isn't its owner's to drive. A controlled player's own attacks, item
 * use, block breaking, drops, hand swaps and containers are refused on both sides; the mod's own forced
 * actions (a thrall swinging at its owner's enemy) run inside {@link #forcedAttack} and pass. Each mind
 * spell filters LivingChangeTargetEvent for its mobs; {@link #setTargetBypassing} is how a spell sets the
 * one target it picked without tripping its own filter. Common code: safe on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MindControl {
    private static final ThreadLocal<Boolean> FORCED_ACTION = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> TARGET_BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private MindControl() {
    }

    /** Under someone else's control: an Imperio thrall. Phase 6b adds possession. Safe on both sides. */
    public static boolean isControlled(LivingEntity entity) {
        return CombatEffects.has(entity, ExampleMod.IMPERIUS);
    }

    /** A melee swing the mod makes on a controlled player's behalf (vanilla damage, cooldown and enchantments). */
    public static void forcedAttack(ServerPlayer attacker, Entity target) {
        FORCED_ACTION.set(Boolean.TRUE);
        try {
            attacker.attack(target);
            attacker.swing(InteractionHand.MAIN_HAND, true);
        } finally {
            FORCED_ACTION.set(Boolean.FALSE);
        }
    }

    /** Set a mob's target past every mind spell's target filter (the filters only stop the mob's own picks). */
    public static void setTargetBypassing(Mob mob, @Nullable LivingEntity target) {
        TARGET_BYPASS.set(Boolean.TRUE);
        try {
            mob.setTarget(target);
        } finally {
            TARGET_BYPASS.set(Boolean.FALSE);
        }
    }

    public static boolean isBypassingTargetRules() {
        return TARGET_BYPASS.get();
    }

    private static boolean blocksOwnAction(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && !FORCED_ACTION.get() && isControlled(living);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (blocksOwnAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelInteraction(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (blocksOwnAction(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player && blocksOwnAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (blocksOwnAction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * A cancelled toss would destroy the stack (vanilla already took it out of the inventory), so put it back.
     * Whatever doesn't fit still drops, so nothing is ever duplicated or lost.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide() || !blocksOwnAction(player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem().copy();
        player.getInventory().add(stack);
        if (stack.isEmpty()) {
            event.setCanceled(true);
        } else {
            event.getEntity().setItem(stack);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && blocksOwnAction(player)) {
            player.closeContainer();
        }
    }

    /** Channels (Crucio, a possession) never survive a rejoin, so nobody logs in still rooted. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Holder<MobEffect> rooted = ExampleMod.ROOTED.getHolder().orElse(null);
        if (rooted != null && player.hasEffect(rooted)) {
            player.removeEffect(rooted);
        }
    }

    private static void cancelInteraction(PlayerInteractEvent event) {
        if (event.isCancelable() && blocksOwnAction(event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
```

- [ ] **Step 4: Create `MindFx`**

`src/main/java/com/hogwartsmagic/spell/mind/MindFx.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** Particles and action-bar lines shared by the mind spells. Server side; no-ops on the client. */
public final class MindFx {
    private MindFx() {
    }

    public static void burst(LivingEntity target, ParticleOptions particle) {
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(particle, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    14, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
        }
    }

    /** A boss (or an immune target) shrugs the spell off: a puff of smoke and a line to the caster. */
    public static void resisted(LivingEntity target, @Nullable Entity caster) {
        burst(target, ParticleTypes.SMOKE);
        tell(caster, "mind.resisted", target.getDisplayName());
    }

    /** Action-bar line {@code gui.hogwartsmagic.<key>} to whoever is responsible for {@code who}, if a player. */
    public static void tell(@Nullable Entity who, String key, Object... args) {
        if (CombatEffects.responsible(who) instanceof Player player && !player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + "." + key, args)
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }
}
```

- [ ] **Step 5: Extract the cast path and refuse controlled players.** In `SpellPackets.java`, inside `CastSpellPacket`, replace:
```java
        public static void handle(CastSpellPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ItemStack wand = player.getMainHandItem();
```
with:
```java
        public static void handle(CastSpellPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // A controlled player's own cast keys do nothing; casts made on their behalf call castAs directly.
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) return;
            castAs(player, p);
        }

        /**
         * The whole cast path, run as {@code player}: learned spell, stun and silence gates, cooldown, mana,
         * then the spell. The packet handler, the Avada windup release and (Phase 6b) puppet casts all come
         * through here, so every cast is checked the same way.
         */
        public static void castAs(ServerPlayer player, CastSpellPacket p) {
            ItemStack wand = player.getMainHandItem();
```
The rest of the old method body is now `castAs`'s body, unchanged (its `return;` statements still work in a `void` method).

In `ImperioControlPacket.handle`, replace:
```java
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            ItemStack wand = player.getMainHandItem();
            if (!MagicWandItem.isAnyWand(wand)) {
                return;
            }
```
with:
```java
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a thrall can't start or steer a channel of its own
            }
            ItemStack wand = player.getMainHandItem();
            if (!MagicWandItem.isAnyWand(wand)) {
                return;
            }
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "effect.hogwartsmagic.glacius_frozen": "Frozen Solid",` insert:
```json
  "effect.hogwartsmagic.imperius": "Imperius",
  "effect.hogwartsmagic.confunded": "Confunded",
  "effect.hogwartsmagic.rooted": "Rooted",
  "gui.hogwartsmagic.mind.resisted": "%s resists the charm",
```

- [ ] **Step 7: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `MindControl`, `rooted`, `imperius` or any `client` class. (The attribute-modifier effect registers on the server; a bad `ResourceLocation` would fail here.)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/spell/mind/MindControl.java src/main/java/com/hogwartsmagic/spell/mind/MindFx.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add the mind spell effects, the control guards and an extracted cast path" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Obliviate (forget the caster, silence players, Master memory charm)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/ObliviateSpell.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/MindHooks.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`applyHitEffect`: the hook call and the `OBLIVIATE` case)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (new `removeFromWizardRaid`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ObliviateRules` (Task 1); `MindControl.setTargetBypassing`, `MindFx` (Task 2); `DisableSupport.casterRank/interrupt` (Phase 3); `CombatEffects.applySilenced/responsible`, `CombatTags.isBoss` (Phase 2).
- Produces:
  - `ObliviateSpell.apply(LivingEntity target, @Nullable Entity caster)`
  - `ObliviateSpell.onHurtBySpell(LivingEntity target, @Nullable Entity caster)` (package-private, called by `MindHooks`)
  - `MindHooks.beforeSpellHit(SpellType spell, LivingEntity target, @Nullable Entity caster)`; package-private `MindHooks.isHostile(SpellType)`
  - `ModEvents.removeFromWizardRaid(ServerLevel level, WizardEntity wizard)` → `boolean`

- [ ] **Step 1: Create `ObliviateSpell`**

`src/main/java/com/hogwartsmagic/spell/mind/ObliviateSpell.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Obliviate: the escape spell. A mob drops its target and won't pick the caster again until the time
 * runs out or the caster hurts it; a player is silenced. At Master the mob also forgets its raid (vanilla
 * raids and Death Eater raids) and its home. Bosses resist. State is two keys in the mob's persistent
 * data, so it survives a chunk reload.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ObliviateSpell {
    static final String TAG_FORGET = ExampleMod.MODID + ":obliviate_forget";
    static final String TAG_FORGET_UNTIL = ExampleMod.MODID + ":obliviate_forget_until";

    private ObliviateSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        DisableSupport.interrupt(target);
        if (CombatTags.isBoss(target)) {
            MindFx.resisted(target, caster);
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.OBLIVIATE);
        if (target instanceof Player) {
            CombatEffects.applySilenced(target, ObliviateRules.silenceTicks(rank), caster);
            MindFx.burst(target, ParticleTypes.ENCHANT);
            return;
        }
        if (!(target instanceof Mob mob)) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        if (owner != null) {
            CompoundTag data = mob.getPersistentData();
            data.putUUID(TAG_FORGET, owner.getUUID());
            data.putLong(TAG_FORGET_UNTIL, mob.level().getGameTime() + ObliviateRules.ignoreTicks(rank));
        }
        MindControl.setTargetBypassing(mob, null);
        mob.setLastHurtByMob(null);
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        mob.getNavigation().stop();
        if (ObliviateRules.memoryCharm(rank)) {
            memoryCharm(mob);
        }
        MindFx.burst(mob, ParticleTypes.ENCHANT);
    }

    /** Master: out of the raid (and no rejoining), no longer a raid leader, no home to go back to. */
    private static void memoryCharm(Mob mob) {
        if (mob instanceof Raider raider) {
            Raid raid = raider.getCurrentRaid();
            if (raid != null) {
                raid.removeFromRaid(raider, true);
            }
            raider.setCanJoinRaid(false);
        }
        if (mob instanceof WizardEntity wizard && mob.level() instanceof ServerLevel level) {
            ModEvents.removeFromWizardRaid(level, wizard);
            if (wizard.isRaidLeader()) {
                wizard.setRaidLeader(false);
            }
        }
        mob.clearRestriction();
    }

    /** A spell from the forgotten caster ("unless you hit it"). Spell chip damage carries no attacker, so it comes here. */
    static void onHurtBySpell(LivingEntity target, @Nullable Entity caster) {
        Entity owner = CombatEffects.responsible(caster);
        if (owner != null && target instanceof Mob mob) {
            remember(mob, owner);
        }
    }

    private static void remember(Mob mob, Entity attacker) {
        CompoundTag data = mob.getPersistentData();
        if (data.hasUUID(TAG_FORGET) && attacker.getUUID().equals(data.getUUID(TAG_FORGET))) {
            clear(data);
        }
    }

    private static void clear(CompoundTag data) {
        data.remove(TAG_FORGET);
        data.remove(TAG_FORGET_UNTIL);
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget == null || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TAG_FORGET)) {
            return;
        }
        if (!ObliviateRules.stillForgotten(mob.level().getGameTime(), data.getLong(TAG_FORGET_UNTIL))) {
            clear(data);
            return;
        }
        if (newTarget.getUUID().equals(data.getUUID(TAG_FORGET))) {
            event.setCanceled(true);
        }
    }

    /** Melee, arrows and anything else that names its attacker. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            Entity attacker = CombatEffects.responsible(event.getSource().getEntity());
            if (attacker != null) {
                remember(mob, attacker);
            }
        }
    }
}
```

- [ ] **Step 2: Create `MindHooks`**

`src/main/java/com/hogwartsmagic/spell/mind/MindHooks.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.spell.SpellType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Called at the top of SpellType.applyHitEffect for every beam that lands on a living target. A beam's
 * chip damage is anonymous magic damage, so this is the one place that knows a spell from X hit Y.
 */
public final class MindHooks {
    /** Helpful spells, and the two that set up control: they don't count as "hitting" anyone. */
    private static final Set<SpellType> NOT_HOSTILE = EnumSet.of(
            SpellType.HEAL, SpellType.PROTEGO, SpellType.LUMOS, SpellType.TENEBRIS_FLIGHT,
            SpellType.REVELIO, SpellType.OBLIVIATE, SpellType.IMPERIO);

    private MindHooks() {
    }

    public static void beforeSpellHit(SpellType spell, LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide() || !isHostile(spell)) {
            return;
        }
        ObliviateSpell.onHurtBySpell(target, caster);
    }

    static boolean isHostile(SpellType spell) {
        return !NOT_HOSTILE.contains(spell);
    }
}
```

- [ ] **Step 3: Wire `SpellType`.** In `SpellType.java`, directly after the line
```java
    public void applyHitEffect(LivingEntity target, Entity caster, @javax.annotation.Nullable net.minecraft.world.phys.Vec3 beamDir) {
```
insert:
```java
        com.hogwartsmagic.spell.mind.MindHooks.beforeSpellHit(this, target, caster);
```
Then replace:
```java
            case OBLIVIATE -> {
                interruptActiveCasting(target);
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setTarget(null); // Make them forget who they were attacking
                }
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            }
```
with:
```java
            case OBLIVIATE -> com.hogwartsmagic.spell.mind.ObliviateSpell.apply(target, caster);
```

- [ ] **Step 4: Let a Death Eater leave its raid.** In `ModEvents.java`, directly above the line
```java
    private static void updateRaidBarPlayers(ServerLevel level, ActiveWizardRaid raid) {
```
insert:
```java
    /**
     * Master Obliviate: the Death Eater forgets its raid. It stops counting toward the raid's progress; a raid
     * emptied this way ends without the Hero of the Village reward (nobody beat it).
     *
     * @return true if the wizard was in a raid
     */
    public static boolean removeFromWizardRaid(ServerLevel level, com.hogwartsmagic.entity.WizardEntity wizard) {
        java.util.UUID raidId = WIZARD_TO_RAID.remove(wizard.getUUID());
        if (raidId == null) {
            return false;
        }
        ActiveWizardRaid raid = ACTIVE_WIZARD_RAIDS.get(raidId);
        if (raid == null) {
            return true;
        }
        raid.members.remove(wizard.getUUID());
        updateRaidProgress(level, raid);
        if (raid.members.isEmpty()) {
            finishWizardRaid(level, raid, false);
        }
        return true;
    }

```

- [ ] **Step 5: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.obliviate.desc": "Wipes the target's memory, confusing them and making them forget who they were fighting.",
```
with:
```json
  "spell.hogwartsmagic.obliviate.desc": "Wipes a creature's memory of you: it drops its target and leaves you alone until you strike it (5s, up to 30s at Master). Silences players. At Master it also forgets its raid and its home. Bosses resist it.",
```

- [ ] **Step 6: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/ObliviateSpell.java src/main/java/com/hogwartsmagic/spell/mind/MindHooks.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Obliviate wipe a mob memory of the caster and silence players, with a Master raid wipe" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Confundo (friendly fire, contagious at Master)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/ConfundoSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`CONFUNDO` case)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ConfundoRules` (Task 1); `ExampleMod.CONFUNDED`, `MindControl.setTargetBypassing/isBypassingTargetRules`, `MindFx` (Task 2); `DisableSupport.casterRank/holder/interrupt` (Phase 3); `CombatEffects.responsible`, `CombatTags.isBoss` (Phase 2).
- Produces:
  - `ConfundoSpell.apply(LivingEntity target, @Nullable Entity caster)`
  - `ConfundoSpell.isConfused(LivingEntity)` → `boolean`

- [ ] **Step 1: Create `ConfundoSpell`**

`src/main/java/com/hogwartsmagic/spell/mind/ConfundoSpell.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Confundo: friendly fire. A confused mob attacks the nearest other creature (never the caster) and can't
 * pick its own targets until the confunded marker runs out. At Master the marker's amplifier is 1 and a
 * confused mob's hits confuse their victims for 3s (that confusion doesn't spread again). Players reel
 * with Nausea and Slowness. Bosses resist.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConfundoSpell {
    static final String TAG_CASTER = ExampleMod.MODID + ":confundo_caster";
    private static Holder<MobEffect> confundedHolder;

    private ConfundoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        DisableSupport.interrupt(target);
        if (CombatTags.isBoss(target)) {
            MindFx.resisted(target, caster);
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.CONFUNDO);
        Entity owner = CombatEffects.responsible(caster);
        if (target instanceof Player) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ConfundoRules.playerNauseaTicks(rank), 0), owner);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ConfundoRules.playerSlownessTicks(rank), 0), owner);
            MindFx.burst(target, ParticleTypes.NAUTILUS);
            return;
        }
        if (target instanceof Mob mob) {
            confuse(mob, owner != null ? owner.getUUID() : null, ConfundoRules.confuseTicks(rank), ConfundoRules.contagious(rank));
        }
    }

    public static boolean isConfused(LivingEntity entity) {
        Holder<MobEffect> holder = holder();
        return holder != null && entity.hasEffect(holder);
    }

    @Nullable
    private static Holder<MobEffect> holder() {
        if (confundedHolder == null) {
            confundedHolder = ExampleMod.CONFUNDED.getHolder().orElse(null);
        }
        return confundedHolder;
    }

    private static void confuse(Mob mob, @Nullable UUID casterId, int ticks, boolean contagious) {
        Holder<MobEffect> holder = holder();
        if (holder == null) {
            return;
        }
        mob.addEffect(new MobEffectInstance(holder, ticks, ConfundoRules.amplifierFor(contagious), false, false, false));
        if (casterId != null) {
            mob.getPersistentData().putUUID(TAG_CASTER, casterId);
        } else {
            mob.getPersistentData().remove(TAG_CASTER);
        }
        MindFx.burst(mob, ParticleTypes.NAUTILUS);
        retarget(mob);
    }

    /** The nearest living thing within 12 blocks that isn't the mob, the caster, a dummy or a creative player. */
    private static void retarget(Mob mob) {
        UUID casterId = mob.getPersistentData().hasUUID(TAG_CASTER) ? mob.getPersistentData().getUUID(TAG_CASTER) : null;
        AABB box = mob.getBoundingBox().inflate(ConfundoRules.SEARCH_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive())) {
            if (candidate instanceof ArmorStand || candidate.isSpectator()
                    || (candidate instanceof Player player && player.isCreative())
                    || (casterId != null && casterId.equals(candidate.getUUID()))) {
                continue;
            }
            double dist = candidate.distanceToSqr(mob);
            if (dist < bestDist) {
                best = candidate;
                bestDist = dist;
            }
        }
        MindControl.setTargetBypassing(mob, best);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !isConfused(mob)) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || mob.tickCount % ConfundoRules.RETARGET_INTERVAL == 0) {
            retarget(mob);
        }
    }

    /** A confused mob picks no targets of its own; only retarget() (through the bypass) chooses. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() == null || MindControl.isBypassingTargetRules()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && isConfused(mob)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect().value() == ExampleMod.CONFUNDED.get()) {
            clear(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == ExampleMod.CONFUNDED.get()) {
            clear(event.getEntity());
        }
    }

    private static void clear(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        entity.getPersistentData().remove(TAG_CASTER);
        if (entity instanceof Mob mob) {
            MindControl.setTargetBypassing(mob, null);
        }
    }

    /** Master contagion: a contagious mob's hit on another mob passes the confusion on for 3s. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide()
                || !(event.getSource().getEntity() instanceof Mob attacker) || attacker == victim) {
            return;
        }
        Holder<MobEffect> holder = holder();
        MobEffectInstance confusion = holder == null ? null : attacker.getEffect(holder);
        if (confusion == null) {
            return;
        }
        boolean contagious = confusion.getAmplifier() >= ConfundoRules.AMP_CONTAGIOUS;
        if (!ConfundoRules.spreads(contagious, CombatTags.isBoss(victim), isConfused(victim))) {
            return;
        }
        UUID casterId = attacker.getPersistentData().hasUUID(TAG_CASTER) ? attacker.getPersistentData().getUUID(TAG_CASTER) : null;
        confuse(victim, casterId, ConfundoRules.CONTAGION_TICKS, false);
    }
}
```

- [ ] **Step 2: Wire `SpellType`.** Replace:
```java
            case CONFUNDO -> {
                interruptActiveCasting(target);
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    // Make them attack a random nearby entity (not just allies/enemies, anyone)
                    net.minecraft.world.phys.AABB area = mob.getBoundingBox().inflate(10.0);
                    java.util.List<LivingEntity> nearby = mob.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != mob);
                    if (!nearby.isEmpty()) {
                        mob.setTarget(nearby.get(mob.level().random.nextInt(nearby.size())));
                    }
                }
            }
```
with:
```java
            case CONFUNDO -> com.hogwartsmagic.spell.mind.ConfundoSpell.apply(target, caster);
```

- [ ] **Step 3: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.confundo.desc": "Greatly confuses the target, causing them to attack anything nearby.",
```
with:
```json
  "spell.hogwartsmagic.confundo.desc": "Muddles a creature so it turns on the nearest other creature (5s, up to 12s at Master). Players reel with nausea. At Master the confusion spreads to whatever the creature hits. Bosses resist it.",
```

- [ ] **Step 4: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/ConfundoSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Confundo turn a mob on the nearest other creature, contagious at Master" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Avada Kedavra (interruptible windup, shrinking backlash, 20% vs bosses)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/AvadaWindup.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/AvadaSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.castAs`: the windup branch)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`interruptActiveCasting`)
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`AVADA_KEDAVRA` case)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `AvadaRules` (Task 1); `CastSpellPacket.castAs` (Task 2); `CombatEffects.responsible`, `CombatTags.isBoss` (Phase 2); `DamageSupport.scaled` (Phase 4); `SpellMasteryData.getRank`, `SpellCosts.manaCost` (Phase 1); `PlayerMagicData.getModData/getMaxMana`, `MagicWandItem.isAnyWand/getSelectedSpell/getSpellCooldown/setSpellCooldown` (existing).
- Produces:
  - `AvadaWindup.begin(ServerPlayer)`, `AvadaWindup.consumeReady(ServerPlayer)` → `boolean`, `AvadaWindup.cancel(ServerPlayer player, boolean interrupted)`, `AvadaWindup.isWindingUp(ServerPlayer)` → `boolean`
  - `AvadaSpell.apply(LivingEntity target, @Nullable Entity caster)`

- [ ] **Step 1: Create `AvadaWindup`**

`src/main/java/com/hogwartsmagic/spell/mind/AvadaWindup.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * A player's Avada Kedavra winds up before it fires. The cast path calls {@link #begin}; each server tick
 * the windup sparks, and when it completes it re-enters the cast path with a one-shot ready flag, which
 * charges the mana, fires the beam and starts the cooldown exactly like any other cast. Stuns, silences and
 * Stupefy break it through ModEvents.interruptActiveCasting; so does any single hit of 4 or more. State is
 * two keys in the player's (non-persisted) data.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AvadaWindup {
    private static final String TAG_START = ExampleMod.MODID + ":avada_windup_start";
    private static final String TAG_READY = ExampleMod.MODID + ":avada_windup_ready";
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.15f, 1.0f, 0.25f), 1.2f);

    private AvadaWindup() {
    }

    public static boolean isWindingUp(ServerPlayer player) {
        return player.getPersistentData().contains(TAG_START);
    }

    /** Start the windup (once). Refuses without enough mana for the cast, so nobody winds up for nothing. */
    public static void begin(ServerPlayer player) {
        if (isWindingUp(player)) {
            return;
        }
        if (!player.isCreative()) {
            ItemStack wand = player.getMainHandItem();
            CompoundTag modData = PlayerMagicData.getModData(player);
            float mana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(player);
            if (mana < SpellCosts.manaCost(player, wand, SpellType.AVADA_KEDAVRA)) {
                player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        player.getPersistentData().putLong(TAG_START, player.level().getGameTime());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 0.5f);
    }

    /** One-shot: true right after the windup finished, so the cast path fires the beam instead of winding up again. */
    public static boolean consumeReady(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_READY)) {
            return false;
        }
        data.remove(TAG_READY);
        return true;
    }

    /**
     * Drop the windup. Interrupted: no mana is spent and Avada is locked for 5s; dropped (wand or spell
     * switched, logout): no penalty.
     */
    public static void cancel(ServerPlayer player, boolean interrupted) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_START)) {
            return;
        }
        data.remove(TAG_START);
        data.remove(TAG_READY);
        if (!interrupted) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        long now = player.level().getGameTime();
        if (!player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            long lockout = now + AvadaRules.INTERRUPT_LOCKOUT_TICKS;
            MagicWandItem.setSpellCooldown(wand, SpellType.AVADA_KEDAVRA,
                    Math.max(lockout, MagicWandItem.getSpellCooldown(wand, SpellType.AVADA_KEDAVRA)));
        }
        player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".avada.interrupted")
                .withStyle(ChatFormatting.RED), true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.3, player.getZ(),
                    12, 0.25, 0.25, 0.25, 0.02);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_START)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        if (!player.isAlive() || !MagicWandItem.isAnyWand(wand)
                || MagicWandItem.getSelectedSpell(wand) != SpellType.AVADA_KEDAVRA) {
            cancel(player, false);
            return;
        }
        long elapsed = player.level().getGameTime() - data.getLong(TAG_START);
        int rank = SpellMasteryData.getRank(player, SpellType.AVADA_KEDAVRA);
        sparkle(player, elapsed, AvadaRules.windupTicks(rank));
        if (elapsed % 6 == 0) {
            player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".avada.winding")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
        if (AvadaRules.windupDone(elapsed, rank)) {
            data.remove(TAG_START);
            data.putBoolean(TAG_READY, true);
            SpellPackets.CastSpellPacket.castAs(player, new SpellPackets.CastSpellPacket(false, false, false, false));
            data.remove(TAG_READY); // refused before it was consumed (cooldown, silence): never leave it armed
        }
    }

    /** A green spiral that tightens around the wand hand as the curse gathers; everyone nearby sees it. */
    private static void sparkle(ServerPlayer player, long elapsed, int windupTicks) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double charge = Math.min(1.0, elapsed / (double) Math.max(1, windupTicks));
        double radius = 0.9 - charge * 0.6;
        for (int i = 0; i < 3; i++) {
            double angle = elapsed * 0.5 + i * (Math.PI * 2.0 / 3.0);
            level.sendParticles(GREEN, player.getX() + Math.cos(angle) * radius, player.getY() + 1.2 + charge * 0.4,
                    player.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isWindingUp(player)
                && AvadaRules.interrupts(event.getAmount())) {
            cancel(player, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, false);
        }
    }
}
```

- [ ] **Step 2: Create `AvadaSpell`**

`src/main/java/com/hogwartsmagic/spell/mind/AvadaSpell.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * The Killing Curse landing. Non-bosses die; bosses lose a fifth of their max health. The caster pays a
 * backlash that shrinks with rank (none at Master); NPC casters keep today's flat backlash.
 */
public final class AvadaSpell {
    private AvadaSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        DamageSource source = owner != null
                ? target.damageSources().indirectMagic(owner, owner)
                : target.damageSources().magic();
        if (CombatTags.isBoss(target)) {
            target.hurt(source, AvadaRules.bossDamage(target.getMaxHealth()));
        } else {
            target.hurt(source, Float.MAX_VALUE);
        }
        if (owner instanceof ServerPlayer player) {
            float backlash = AvadaRules.backlash(SpellMasteryData.getRank(player, SpellType.AVADA_KEDAVRA));
            if (backlash > 0.0f) {
                player.hurt(player.damageSources().magic(), backlash);
            }
        } else if (owner instanceof LivingEntity npc && owner != target) {
            npc.hurt(npc.damageSources().magic(), DamageSupport.scaled(npc, AvadaRules.NPC_BACKLASH));
        }
    }
}
```

- [ ] **Step 3: The windup branch in the cast path.** In `SpellPackets.java`, inside `CastSpellPacket.castAs`, directly above the line
```java
            // Mana Check
```
insert:
```java
            // Avada Kedavra winds up first (Spell Mastery Phase 6). AvadaWindup re-enters castAs when the windup
            // completes, with the ready flag set, and only then is the mana taken and the beam fired.
            if (spell == SpellType.AVADA_KEDAVRA && !p.selfCastProtego
                    && !com.hogwartsmagic.spell.mind.AvadaWindup.consumeReady(player)) {
                com.hogwartsmagic.spell.mind.AvadaWindup.begin(player);
                return;
            }

```
(It sits after the cooldown check, so a cooling-down Avada can't start a windup.)

- [ ] **Step 4: Interrupts break the windup.** In `ModEvents.java`, in `interruptActiveCasting`, directly after its first line:
```java
    public static void interruptActiveCasting(ServerPlayer player) {
        ItemStack wand = player.getMainHandItem();
```
insert:
```java
        com.hogwartsmagic.spell.mind.AvadaWindup.cancel(player, true);
```

- [ ] **Step 5: Wire `SpellType`.** Replace:
```java
            case AVADA_KEDAVRA -> {
                target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
                if (caster instanceof LivingEntity livingCaster) {
                    livingCaster.hurt(livingCaster.damageSources().magic(), scaledSpellDamage(caster, 2.0f)); // Dark magic cost
                }
            }
```
with:
```java
            case AVADA_KEDAVRA -> com.hogwartsmagic.spell.mind.AvadaSpell.apply(target, caster);
```
If `scaledSpellDamage` now has no callers left in `SpellType.java` (`grep -n "scaledSpellDamage(" src/main/java/com/hogwartsmagic/spell/SpellType.java` prints only its declaration), delete that private method too. Otherwise leave it.

- [ ] **Step 6: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.avada_kedavra.desc": "The Killing Curse: Instantly kills the target. Costs some health to cast.",
```
with:
```json
  "spell.hogwartsmagic.avada_kedavra.desc": "The Killing Curse. A long windup that a hard hit, a stun or a silence can break, then death. Bosses lose a fifth of their health instead. It hurts you too, less with rank, and not at all at Master.",
```
and directly after `  "gui.hogwartsmagic.mind.resisted": "%s resists the charm",` insert:
```json
  "gui.hogwartsmagic.avada.winding": "Avada Kedavra...",
  "gui.hogwartsmagic.avada.interrupted": "The Killing Curse breaks apart!",
```

- [ ] **Step 7: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `AvadaWindup`, `AvadaSpell` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/AvadaWindup.java src/main/java/com/hogwartsmagic/spell/mind/AvadaSpell.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Give Avada Kedavra an interruptible windup, a rank-scaled backlash and boss damage" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Crucio (a held channel that roots the caster and stuns the victim)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/CrucioChannel.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`CRUCIO` case)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.castAs`: release and heartbeat)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`interruptActiveCasting`; `handleWandUse`)
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` (the Crucio hold)
- Modify: `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java` (`castSelectedSpell`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `CrucioRules` (Task 1); `ExampleMod.ROOTED`, `MindControl.setTargetBypassing/isBypassingTargetRules` (Task 2); `CrowdControl.apply`, `CombatEffects.responsible`, `CombatTags.isBoss`, `ExampleMod.STUNNED` (Phase 2); `DisableSupport.casterRank/holder/interrupt` (Phase 3).
- Produces:
  - `CrucioChannel.onBeamHit(LivingEntity target, @Nullable Entity caster)`
  - `CrucioChannel.heartbeat(ServerPlayer)`, `CrucioChannel.release(ServerPlayer)`, `CrucioChannel.isChanneling(Entity)` → `boolean`
  - `ClientModEvents.startCrucioHold()`, `ClientModEvents.isCrucioHoldPending()` → `boolean`

- [ ] **Step 1: Create `CrucioChannel`**

`src/main/java/com/hogwartsmagic/spell/mind/CrucioChannel.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crucio as a channel. The beam's impact starts it: the victim is stunned for the rank's full length
 * (through CrowdControl, so diminishing returns, the boss rule and PvP apply; bosses aren't stunned at
 * all) and takes 1.5 every half second, while a player caster is rooted and must keep holding (the cast
 * packet heartbeat). It ends on release, a lapsed heartbeat, the rank cap, 24 blocks of distance, a death,
 * or an interrupt. Ending early lifts the stun it put on. At Master a surviving non-boss mob flees from the
 * caster for 10s. NPC casters run the full rank-1 channel without holding. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrucioChannel {
    private static final String TAG_FLEE_FROM = ExampleMod.MODID + ":crucio_flee_from";
    private static final String TAG_FLEE_UNTIL = ExampleMod.MODID + ":crucio_flee_until";
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.05f), 1.0f);

    private static final class Channel {
        final UUID casterId;
        final UUID targetId;
        final ResourceKey<Level> dimension;
        final long startedAt;
        final int maxTicks;
        final int rank;
        final boolean held;
        long lastSeen;
        long stunEndsAt;

        Channel(UUID casterId, UUID targetId, ResourceKey<Level> dimension, long startedAt, int maxTicks, int rank, boolean held) {
            this.casterId = casterId;
            this.targetId = targetId;
            this.dimension = dimension;
            this.startedAt = startedAt;
            this.maxTicks = maxTicks;
            this.rank = rank;
            this.held = held;
            this.lastSeen = startedAt;
        }
    }

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    private CrucioChannel() {
    }

    public static boolean isChanneling(Entity caster) {
        return CHANNELS.containsKey(caster.getUUID());
    }

    public static void onBeamHit(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        if (!(owner instanceof LivingEntity livingOwner) || owner == target || !livingOwner.isAlive()) {
            return;
        }
        Channel previous = CHANNELS.get(owner.getUUID());
        if (previous != null) {
            end(level.getServer(), previous);
        }
        int rank = DisableSupport.casterRank(caster, SpellType.CRUCIO);
        boolean held = owner instanceof ServerPlayer;
        long now = level.getGameTime();
        int maxTicks = CrucioRules.channelTicks(rank);
        Channel channel = new Channel(owner.getUUID(), target.getUUID(), level.dimension(), now, maxTicks, rank, held);
        DisableSupport.interrupt(target);
        if (!CombatTags.isBoss(target)) {
            int stun = CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED), maxTicks, caster);
            channel.stunEndsAt = now + stun;
        }
        if (held) {
            livingOwner.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.ROOTED),
                    maxTicks + CrucioRules.HEARTBEAT_TIMEOUT, 0, false, false, false));
        }
        CHANNELS.put(owner.getUUID(), channel);
    }

    public static void heartbeat(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel != null) {
            channel.lastSeen = player.level().getGameTime();
        }
    }

    public static void release(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel != null && player.getServer() != null) {
            end(player.getServer(), channel);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHANNELS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Channel channel : new ArrayList<>(CHANNELS.values())) {
            tickChannel(server, channel);
        }
    }

    private static void tickChannel(MinecraftServer server, Channel channel) {
        ServerLevel level = server.getLevel(channel.dimension);
        LivingEntity caster = level == null ? null : living(level.getEntity(channel.casterId));
        LivingEntity target = level == null ? null : living(level.getEntity(channel.targetId));
        if (level == null || caster == null || target == null) {
            end(server, channel);
            return;
        }
        long now = level.getGameTime();
        long elapsed = now - channel.startedAt;
        boolean over = elapsed >= channel.maxTicks
                || caster.distanceToSqr(target) > CrucioRules.MAX_RANGE * CrucioRules.MAX_RANGE
                || (channel.held && (CrucioRules.heartbeatLapsed(now, channel.lastSeen) || !holdsCrucio(caster)));
        if (over) {
            end(server, channel);
            return;
        }
        if (CrucioRules.isDamageTick(elapsed)) {
            target.hurt(target.damageSources().indirectMagic(caster, caster), CrucioRules.DAMAGE);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 4, 0.25, 0.25, 0.25, 0.1);
        }
        if (elapsed % 4 == 0) {
            Vec3 from = caster.getEyePosition();
            Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
            for (int i = 1; i <= 6; i++) {
                Vec3 point = from.lerp(to, i / 7.0);
                level.sendParticles(RED, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
    }

    private static boolean holdsCrucio(LivingEntity caster) {
        ItemStack wand = caster.getMainHandItem();
        return MagicWandItem.isAnyWand(wand) && MagicWandItem.getSelectedSpell(wand) == SpellType.CRUCIO;
    }

    private static void end(MinecraftServer server, Channel channel) {
        CHANNELS.remove(channel.casterId);
        ServerLevel level = server.getLevel(channel.dimension);
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        LivingEntity caster = living(level.getEntity(channel.casterId));
        LivingEntity target = living(level.getEntity(channel.targetId));
        if (caster != null && channel.held) {
            caster.removeEffect(DisableSupport.holder(ExampleMod.ROOTED));
        }
        if (target == null) {
            return;
        }
        long stunLeft = channel.stunEndsAt - now;
        MobEffectInstance stun = target.getEffect(DisableSupport.holder(ExampleMod.STUNNED));
        if (stun != null && stunLeft > 0 && stun.getDuration() <= stunLeft + 1) {
            target.removeEffect(DisableSupport.holder(ExampleMod.STUNNED)); // ours, not a longer stun from someone else
        }
        if (caster != null && CrucioRules.fleesAfter(channel.rank) && target instanceof Mob mob && !CombatTags.isBoss(mob)) {
            CompoundTag data = mob.getPersistentData();
            data.putUUID(TAG_FLEE_FROM, caster.getUUID());
            data.putLong(TAG_FLEE_UNTIL, now + CrucioRules.FLEE_TICKS);
            MindControl.setTargetBypassing(mob, null);
        }
    }

    @Nullable
    private static LivingEntity living(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** Master: run from the caster, re-pathing every half second, until the time is up. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || mob.tickCount % 10 != 0) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TAG_FLEE_FROM) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Entity from = level.getEntity(data.getUUID(TAG_FLEE_FROM));
        if (from == null || level.getGameTime() >= data.getLong(TAG_FLEE_UNTIL)) {
            data.remove(TAG_FLEE_FROM);
            data.remove(TAG_FLEE_UNTIL);
            return;
        }
        Vec3 away = mob instanceof PathfinderMob pathfinder
                ? DefaultRandomPos.getPosAway(pathfinder, 16, 7, from.position())
                : null;
        if (away == null) {
            Vec3 flat = mob.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
            away = mob.position().add(flat.lengthSqr() < 1.0E-4 ? new Vec3(8.0, 0.0, 0.0) : flat.normalize().scale(12.0));
        }
        mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
    }

    /** A fleeing mob doesn't turn back on the caster. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() == null || MindControl.isBypassingTargetRules()
                || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (data.hasUUID(TAG_FLEE_FROM) && event.getNewTarget().getUUID().equals(data.getUUID(TAG_FLEE_FROM))
                && mob.level().getGameTime() < data.getLong(TAG_FLEE_UNTIL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Channel channel : new ArrayList<>(CHANNELS.values())) {
            end(event.getServer(), channel);
        }
        CHANNELS.clear();
    }
}
```

- [ ] **Step 2: Wire `SpellType`.** Replace:
```java
            case CRUCIO -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2));
            }
```
with:
```java
            case CRUCIO -> com.hogwartsmagic.spell.mind.CrucioChannel.onBeamHit(target, caster);
```

- [ ] **Step 3: Heartbeat and release in the cast path.** In `SpellPackets.java`, inside `CastSpellPacket.castAs`, replace:
```java
            if (p.releaseChannel) {
                if (playerData.getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
```
with:
```java
            if (p.releaseChannel) {
                com.hogwartsmagic.spell.mind.CrucioChannel.release(player);
                if (playerData.getBoolean(TAG_PROTEGO_MAXIMA_CHANNELING)) {
```
Then, directly after the end of that release block:
```java
                playerData.remove(TAG_FIENDFYRE_ADVANCE);
                playerData.remove(TAG_PATRONUS_CAST_SPENT);
                return;
            }
```
insert:
```java

            // Crucio is held after its beam lands; the heartbeat keeps the channel alive (it never casts again).
            if (p.channelHeartbeat && spell == SpellType.CRUCIO) {
                com.hogwartsmagic.spell.mind.CrucioChannel.heartbeat(player);
                return;
            }
```

- [ ] **Step 4: Interrupts end the channel.** In `ModEvents.java`, in `interruptActiveCasting`, directly after the line you added in Task 5:
```java
        com.hogwartsmagic.spell.mind.AvadaWindup.cancel(player, true);
```
insert:
```java
        com.hogwartsmagic.spell.mind.CrucioChannel.release(player);
```

- [ ] **Step 5: The client hold.** In `ClientModEvents.java`:

Directly after `    private static final int IMPERIO_HOLD_THRESHOLD_TICKS = 40;` add:
```java
    private static boolean crucioHoldPending = false;
    private static boolean crucioChannelActive = false;
```
In the client tick, directly above the line
```java
                    if (imperioHoldPending && (!holdingWand || !imperioSelected)) {
```
insert:
```java
                    // Crucio is held after the beam: heartbeats keep the curse on, letting go ends it.
                    boolean crucioSelected = holdingWand
                            && selected == com.hogwartsmagic.spell.SpellType.CRUCIO;
                    if (crucioHoldPending && (!holdingWand || !crucioSelected)) {
                        if (crucioChannelActive) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                        }
                        crucioHoldPending = false;
                        crucioChannelActive = false;
                    } else if (crucioHoldPending) {
                        if (useDown) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                            crucioChannelActive = true;
                        } else {
                            if (crucioChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                            }
                            crucioHoldPending = false;
                            crucioChannelActive = false;
                        }
                    }

```
In the `else` branch that resets every hold when there is no player, replace:
```java
                    patronusHoldPending = false;
                    patronusChannelActive = false;
                    imperioHoldPending = false;
```
(the 20-space-indented copy) with:
```java
                    patronusHoldPending = false;
                    patronusChannelActive = false;
                    crucioHoldPending = false;
                    crucioChannelActive = false;
                    imperioHoldPending = false;
```
Directly after the method
```java
    public static void startImperioHold() {
        if (!imperioHoldPending) {
            imperioHoldPending = true;
            imperioChannelActive = false;
            imperioHoldTicks = 0;
        }
    }
```
add:
```java

    public static void startCrucioHold() {
        if (!crucioHoldPending) {
            crucioHoldPending = true;
            crucioChannelActive = false;
        }
    }

    /** Doubles as the use-key latch: while the hold is pending, holding right-click never casts Crucio again. */
    public static boolean isCrucioHoldPending() {
        return crucioHoldPending;
    }
```

- [ ] **Step 6: Start the hold from both cast entry points.**

In `ModEventsClientHooks.java`, `castSelectedSpell`, directly above its last three lines:
```java
        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
        player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static ItemStack getHeldWand(Player player) {
```
insert (so it reads right after the client mana check):
```java
        if (selected == SpellType.CRUCIO) {
            if (com.hogwartsmagic.event.ClientModEvents.isCrucioHoldPending()) {
                return true;
            }
            com.hogwartsmagic.event.ClientModEvents.startCrucioHold();
        }

```
In `ModEvents.java`, `handleWandUse`: find the comment line
```java
                // Break Protego on cast! Server side will handle actual spell logic, but we can do visuals here if we want, or rely on server syncing.
```
Two lines above it is `                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());` (the plain cast after the client mana check). Directly above that send line, insert:
```java
                if (selected == SpellType.CRUCIO) {
                    if (com.hogwartsmagic.event.ClientModEvents.isCrucioHoldPending()) {
                        return true;
                    }
                    com.hogwartsmagic.event.ClientModEvents.startCrucioHold();
                }
```
(`handleWandUse` already only runs on the client, the same way it calls `ClientModEvents.startImperioHold()`.)

- [ ] **Step 7: Description.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.crucio.desc": "Inflicts extreme pain, dealing damage over time and slowing the target.",
```
with:
```json
  "spell.hogwartsmagic.crucio.desc": "Keep holding after the curse lands: the victim is held in agony and hurt every half second while you stand still (2s, up to 5s at Master). At Master a surviving creature flees from you.",
```

- [ ] **Step 8: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `CrucioChannel`, `ClientModEvents` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/CrucioChannel.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Turn Crucio into a held channel that roots the caster and stuns the victim" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Imperio thralls (server side: mobs, bosses and players)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/ThrallGoals.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/ForcedPlayerMovement.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/MindHooks.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`IMPERIO` case)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.castAs` cap check; `ImperioControlPacket.handle` rank gate)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`handleWandUse`, Imperio branch)
- Modify: `src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java` (`castSelectedSpell`, Imperio branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ImperioRules` (Task 1); `ExampleMod.IMPERIUS`, `Config.isImperioAffectsPlayers`, `MindControl.forcedAttack/setTargetBypassing/isBypassingTargetRules`, `MindFx` (Task 2); `MindHooks` (Task 3); `DisableSupport.casterRank/holder/interrupt` (Phase 3); `CombatEffects.responsible`, `CombatTags.isBoss/ALLIED` (Phase 2); `SpellCosts.startCooldown` (both forms), `MasteryAwards.onCastCompleted`, `SpellMasteryData.getRank` (Phase 1); `HouseElfEntity.markSubdued(int)` (existing).
- Produces:
  - `ImperioThralls.enthrall(LivingEntity owner, LivingEntity target, int rank)` → `boolean`
  - `ImperioThralls.mayCast(ServerPlayer)` → `boolean` (sends the "slots full" line when false)
  - `ImperioThralls.isThrall(Entity)` → `boolean`, `ImperioThralls.countFor(UUID ownerId)` → `int`
  - `ImperioThralls.noteAttack(@Nullable Entity caster, LivingEntity target)`
  - `ImperioThralls.startImperioCooldown(ServerPlayer)` (cooldown on every wand the player carries, Imperio XP once; Phase 6b reuses it)
  - `ImperioThralls.teleportNear(LivingEntity mover, LivingEntity owner)` → `boolean`
  - package-private: `ImperioThralls.ownerOf(Mob, UUID)`, `ImperioThralls.pickTarget(LivingEntity owner, LivingEntity self)`, `ImperioThralls.isFairTarget(LivingEntity owner, LivingEntity self, @Nullable LivingEntity candidate)`
  - `ImperioSpell.apply(LivingEntity target, @Nullable Entity caster)`; `ImperioSpell.TAG_RECENT_HIT_TARGET`, `TAG_RECENT_HIT_EXPIRY` (the existing persistent-data keys)
  - `ForcedPlayerMovement.verticalVelocity(ServerPlayer player, double previousY, boolean wantJump)` → `double`, `ForcedPlayerMovement.push(ServerPlayer player, double vx, double vy, double vz)`
  - `ThrallGoals.Follow(Mob, UUID)`, `ThrallGoals.Assist(Mob, UUID)`, `ThrallGoals.Brawl(PathfinderMob)` (package-private)

- [ ] **Step 1: Create `ThrallGoals`**

`src/main/java/com/hogwartsmagic/spell/mind/ThrallGoals.java`:
```java
package com.hogwartsmagic.spell.mind;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Tame-dog goals that work on any Mob, not only TamableAnimal (vanilla's FollowOwnerGoal and the owner
 * target goals need a TamableAnimal). ImperioThralls adds them on enthrall and removes them on release.
 */
final class ThrallGoals {
    private ThrallGoals() {
    }

    /** Walk after the owner like a wolf; beyond 12 blocks, teleport beside them. Only while not fighting. */
    static final class Follow extends Goal {
        private final Mob mob;
        private final UUID ownerId;
        @Nullable
        private LivingEntity owner;
        private int recalc;

        Follow(Mob mob, UUID ownerId) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null) {
                return false;
            }
            LivingEntity found = ImperioThralls.ownerOf(this.mob, this.ownerId);
            double start = ImperioRules.FOLLOW_START;
            if (found == null || this.mob.distanceToSqr(found) < start * start) {
                return false;
            }
            this.owner = found;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            double stop = ImperioRules.FOLLOW_STOP;
            return this.owner != null && this.owner.isAlive() && this.mob.getTarget() == null
                    && this.mob.distanceToSqr(this.owner) > stop * stop;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
            if (--this.recalc > 0) {
                return;
            }
            this.recalc = this.adjustedTickDelay(10);
            double teleport = ImperioRules.TELEPORT_DISTANCE;
            if (this.mob.distanceToSqr(this.owner) > teleport * teleport) {
                ImperioThralls.teleportNear(this.mob, this.owner);
            } else {
                this.mob.getNavigation().moveTo(this.owner, 1.2);
            }
        }
    }

    /** Take up the owner's fight: whoever hurt the owner, else whatever the owner hurt (last 5s). */
    static final class Assist extends Goal {
        private final Mob mob;
        private final UUID ownerId;
        @Nullable
        private LivingEntity pick;

        Assist(Mob mob, UUID ownerId) {
            this.mob = mob;
            this.ownerId = ownerId;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = ImperioThralls.ownerOf(this.mob, this.ownerId);
            if (owner == null) {
                return false;
            }
            LivingEntity candidate = ImperioThralls.pickTarget(owner, this.mob);
            if (candidate == null || candidate == this.mob.getTarget()) {
                return false;
            }
            this.pick = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            MindControl.setTargetBypassing(this.mob, this.pick);
            this.pick = null;
        }
    }

    /** A creature with no attack of its own (a villager, a cow) still throws a 2-damage punch for its owner. */
    static final class Brawl extends Goal {
        private final PathfinderMob mob;
        private int cooldown;
        private int recalc;

        Brawl(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.cooldown = 0;
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.mob.isWithinMeleeAttackRange(target)) {
                this.mob.getNavigation().stop();
                if (--this.cooldown <= 0) {
                    this.cooldown = ImperioRules.BRAWL_INTERVAL;
                    this.mob.swing(InteractionHand.MAIN_HAND);
                    target.hurt(this.mob.damageSources().mobAttack(this.mob), ImperioRules.BRAWL_DAMAGE);
                }
            } else if (--this.recalc <= 0) {
                this.recalc = 10;
                this.mob.getNavigation().moveTo(target, 1.2);
            }
        }
    }
}
```

- [ ] **Step 2: Create `ForcedPlayerMovement`**

`src/main/java/com/hogwartsmagic/spell/mind/ForcedPlayerMovement.java`:
```java
package com.hogwartsmagic.spell.mind;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Moves a player from the server. Player movement is client-authoritative, so the server sets the velocity
 * and sends it straight to that player's client (the motion packet), whose own physics then carries it out
 * with its input zeroed. The server never simulates player motion, so its stored vertical velocity is stale:
 * the caller passes the Y from the previous tick and the push keeps whatever the player really did.
 */
public final class ForcedPlayerMovement {
    private static final double JUMP_VELOCITY = 0.42;
    private static final double MAX_VERTICAL = 3.0;

    private ForcedPlayerMovement() {
    }

    /** On the ground: a jump if wanted, else none. In the air: what the player actually moved last tick. */
    public static double verticalVelocity(ServerPlayer player, double previousY, boolean wantJump) {
        if (player.onGround()) {
            return wantJump ? JUMP_VELOCITY : 0.0;
        }
        return Mth.clamp(player.getY() - previousY, -MAX_VERTICAL, MAX_VERTICAL);
    }

    public static void push(ServerPlayer player, double vx, double vy, double vz) {
        player.setDeltaMovement(vx, vy, vz);
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
```

- [ ] **Step 3: Create `ImperioThralls`**

`src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imperio ranks I-III: the thrall registry. A mob thrall gets three temporary goals (follow, assist,
 * brawl if it has no attack of its own) and only fights what its owner fights; a player thrall is walked
 * by the server each tick toward the owner or the owner's fight and swings with Player.attack. Everything
 * lives in memory on the server; a thrall that unloads, dies, logs out or changes dimension simply ends,
 * and EntityJoinLevelEvent strips anything it carried (the allied tag, the imperius marker) when it comes
 * back. Imperio's cooldown starts, and the cast earns its XP, when the owner's last thrall ends.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImperioThralls {
    private static final String TAG_ADDED_ALLIED = ExampleMod.MODID + ":thrall_added_allied";
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.45f), 0.9f);

    static final class Thrall {
        final UUID victimId;
        final UUID ownerId;
        final String ownerName;
        final ResourceKey<Level> dimension;
        final boolean playerVictim;
        long expiresAt;
        double lastY;
        boolean addedStrength;
        final List<Goal> goals = new ArrayList<>();
        final List<Goal> targetGoals = new ArrayList<>();

        Thrall(UUID victimId, UUID ownerId, String ownerName, ResourceKey<Level> dimension, boolean playerVictim, long expiresAt) {
            this.victimId = victimId;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.dimension = dimension;
            this.playerVictim = playerVictim;
            this.expiresAt = expiresAt;
        }
    }

    /** "{@code other} was involved at game time {@code at}" for the assist logic. */
    private record Note(UUID other, long at) {
    }

    private static final Map<UUID, Thrall> THRALLS = new HashMap<>();
    /** attacker -> whom it last hit with a spell (only kept for owners). */
    private static final Map<UUID, Note> LAST_ATTACKED = new HashMap<>();
    /** victim -> who last hit it with a spell (only kept for owners). */
    private static final Map<UUID, Note> LAST_ATTACKER = new HashMap<>();

    private ImperioThralls() {
    }

    // ---- Queries -------------------------------------------------------------------------------

    public static boolean isThrall(Entity entity) {
        return THRALLS.containsKey(entity.getUUID());
    }

    public static int countFor(UUID ownerId) {
        int count = 0;
        for (Thrall thrall : THRALLS.values()) {
            if (thrall.ownerId.equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    /** The cast-path gate: ranks I-III can't cast (or pay) while every thrall slot is taken. */
    public static boolean mayCast(ServerPlayer caster) {
        int rank = SpellMasteryData.getRank(caster, SpellType.IMPERIO);
        if (ImperioRules.isPossessionRank(rank) || !ImperioRules.atCap(rank, countFor(caster.getUUID()))) {
            return true;
        }
        MindFx.tell(caster, "imperio.at_cap", ImperioRules.maxThralls(rank));
        return false;
    }

    @Nullable
    static LivingEntity ownerOf(Mob mob, UUID ownerId) {
        return mob.level() instanceof ServerLevel level ? resolveOwner(level, ownerId) : null;
    }

    @Nullable
    private static LivingEntity resolveOwner(ServerLevel level, UUID ownerId) {
        return level.getEntity(ownerId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Nullable
    private static LivingEntity living(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // ---- Enthralling ---------------------------------------------------------------------------

    public static boolean enthrall(LivingEntity owner, LivingEntity target, int rank) {
        if (!(target.level() instanceof ServerLevel level) || owner == target || !target.isAlive() || !owner.isAlive()) {
            return false;
        }
        boolean player = target instanceof Player;
        if (player) {
            Player victim = (Player) target;
            if (!Config.isImperioAffectsPlayers()) {
                MindFx.tell(owner, "imperio.players_off");
                return false;
            }
            if (victim.isCreative() || victim.isSpectator()) {
                return false;
            }
        } else if (!(target instanceof Mob)) {
            return false;
        }
        boolean boss = CombatTags.isBoss(target);
        if (!ImperioRules.canEnthrall(rank, target.getMaxHealth(), boss, player)) {
            MindFx.tell(owner, "imperio.too_strong", target.getDisplayName());
            MindFx.burst(target, ParticleTypes.SMOKE);
            return false;
        }
        long now = level.getGameTime();
        int ticks = ImperioRules.thrallTicks(rank, player || boss);
        Thrall existing = THRALLS.get(target.getUUID());
        if (existing != null) {
            if (!existing.ownerId.equals(owner.getUUID())) {
                MindFx.resisted(target, owner);
                return false;
            }
            existing.expiresAt = now + ticks;
            addMarker(target, ticks);
            return true;
        }
        if (ImperioRules.atCap(rank, countFor(owner.getUUID()))) {
            MindFx.tell(owner, "imperio.at_cap", ImperioRules.maxThralls(rank));
            return false;
        }
        Thrall thrall = new Thrall(target.getUUID(), owner.getUUID(), owner.getName().getString(), level.dimension(), player, now + ticks);
        thrall.lastY = target.getY();
        addMarker(target, ticks);
        if (target instanceof Mob mob) {
            setUpMob(mob, owner, thrall, rank, ticks);
        }
        THRALLS.put(target.getUUID(), thrall);
        MindFx.burst(target, GREEN);
        MindFx.tell(owner, "imperio.thrall_gained", target.getDisplayName());
        return true;
    }

    private static void addMarker(LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.IMPERIUS), ticks, 0, false, false, false));
    }

    private static void setUpMob(Mob mob, LivingEntity owner, Thrall thrall, int rank, int ticks) {
        Goal follow = new ThrallGoals.Follow(mob, thrall.ownerId);
        Goal assist = new ThrallGoals.Assist(mob, thrall.ownerId);
        mob.goalSelector.addGoal(2, follow);
        mob.targetSelector.addGoal(0, assist);
        thrall.goals.add(follow);
        thrall.targetGoals.add(assist);
        if (mob instanceof PathfinderMob pathfinder && mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            Goal brawl = new ThrallGoals.Brawl(pathfinder);
            mob.goalSelector.addGoal(2, brawl);
            thrall.goals.add(brawl);
        }
        if (owner instanceof Player && !mob.getTags().contains(CombatTags.ALLIED)) {
            mob.addTag(CombatTags.ALLIED);
            mob.getPersistentData().putBoolean(TAG_ADDED_ALLIED, true);
        }
        if (ImperioRules.grantsStrength(rank)) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
            thrall.addedStrength = true;
        }
        MindControl.setTargetBypassing(mob, null);
        mob.setLastHurtByMob(null);
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        mob.getNavigation().stop();
    }

    // ---- The owner's fight -------------------------------------------------------------------

    /** Record a spell hit (from MindHooks); melee and arrows already land in vanilla's last-hurt fields. */
    public static void noteAttack(@Nullable Entity caster, LivingEntity target) {
        Entity attacker = CombatEffects.responsible(caster);
        if (attacker == null || attacker == target || target.level().isClientSide()) {
            return;
        }
        long now = target.level().getGameTime();
        if (countFor(attacker.getUUID()) > 0) {
            LAST_ATTACKED.put(attacker.getUUID(), new Note(target.getUUID(), now));
        }
        if (countFor(target.getUUID()) > 0) {
            LAST_ATTACKER.put(target.getUUID(), new Note(attacker.getUUID(), now));
        }
    }

    /** Whoever hurt the owner in the last 5s, else whatever the owner hurt (a mob owner: its target). */
    @Nullable
    static LivingEntity pickTarget(LivingEntity owner, LivingEntity self) {
        long now = self.level().getGameTime();
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && ImperioRules.fresh(owner.tickCount, owner.getLastHurtByMobTimestamp())
                && isFairTarget(owner, self, attacker)) {
            return attacker;
        }
        LivingEntity spellAttacker = noted(LAST_ATTACKER.get(owner.getUUID()), self, now);
        if (isFairTarget(owner, self, spellAttacker)) {
            return spellAttacker;
        }
        LivingEntity victim = owner instanceof Mob mobOwner
                ? mobOwner.getTarget()
                : (ImperioRules.fresh(owner.tickCount, owner.getLastHurtMobTimestamp()) ? owner.getLastHurtMob() : null);
        if (isFairTarget(owner, self, victim)) {
            return victim;
        }
        LivingEntity spellVictim = noted(LAST_ATTACKED.get(owner.getUUID()), self, now);
        return isFairTarget(owner, self, spellVictim) ? spellVictim : null;
    }

    @Nullable
    private static LivingEntity noted(@Nullable Note note, LivingEntity self, long now) {
        if (note == null || !ImperioRules.fresh(now, note.at()) || !(self.level() instanceof ServerLevel level)) {
            return null;
        }
        return living(level.getEntity(note.other()));
    }

    /** Never the owner, itself, the owner's team or other thralls and allies, creative players, or anything far off. */
    static boolean isFairTarget(LivingEntity owner, LivingEntity self, @Nullable LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive() || candidate == owner || candidate == self
                || candidate.level() != self.level()) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (candidate.isAlliedTo(owner) || (owner instanceof Player && candidate.getTags().contains(CombatTags.ALLIED))) {
            return false;
        }
        Thrall other = THRALLS.get(candidate.getUUID());
        if (other != null && other.ownerId.equals(owner.getUUID())) {
            return false;
        }
        double max = ImperioRules.MAX_TARGET_DISTANCE;
        return candidate.distanceToSqr(self) <= max * max;
    }

    // ---- Ticking -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!THRALLS.isEmpty()) {
            for (Thrall thrall : new ArrayList<>(THRALLS.values())) {
                tickThrall(server, thrall);
            }
        }
        if (server.getTickCount() % 100 == 0) {
            pruneNotes(server.overworld().getGameTime());
        }
    }

    private static void tickThrall(MinecraftServer server, Thrall thrall) {
        ServerLevel level = server.getLevel(thrall.dimension);
        LivingEntity victim = level == null ? null : living(level.getEntity(thrall.victimId));
        if (level == null || victim == null) {
            THRALLS.remove(thrall.victimId);
            afterRelease(server, thrall);
            return;
        }
        LivingEntity owner = resolveOwner(level, thrall.ownerId);
        long now = level.getGameTime();
        if (owner == null || now >= thrall.expiresAt) {
            end(server, thrall, victim);
            return;
        }
        if (victim instanceof Mob mob && mob.getTarget() != null && !isFairTarget(owner, mob, mob.getTarget())) {
            MindControl.setTargetBypassing(mob, null);
        }
        if (victim instanceof ServerPlayer player) {
            drivePlayer(thrall, player, owner);
        }
        if (now % 10 == 0) {
            level.sendParticles(GREEN, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                    3, victim.getBbWidth() * 0.4, victim.getBbHeight() * 0.3, victim.getBbWidth() * 0.4, 0.0);
        }
    }

    /** Walk the player toward the owner's fight (or the owner), and swing when in reach and recharged. */
    private static void drivePlayer(Thrall thrall, ServerPlayer victim, LivingEntity owner) {
        LivingEntity fight = pickTarget(owner, victim);
        LivingEntity goal = fight != null ? fight : owner;
        double previousY = thrall.lastY;
        thrall.lastY = victim.getY();
        double dist = victim.distanceTo(goal);
        if (fight == null && dist > ImperioRules.TELEPORT_DISTANCE) {
            if (teleportNear(victim, owner)) {
                thrall.lastY = victim.getY();
            }
            return;
        }
        Vec3 flat = new Vec3(goal.getX() - victim.getX(), 0.0, goal.getZ() - victim.getZ());
        if (flat.lengthSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(flat.z, flat.x) * (180.0 / Math.PI)) - 90.0F;
            victim.setYRot(yaw);
            victim.setYHeadRot(yaw);
        }
        double stopAt = fight != null ? ImperioRules.PLAYER_REACH - 0.4 : ImperioRules.FOLLOW_STOP;
        double vy = ForcedPlayerMovement.verticalVelocity(victim, previousY, victim.horizontalCollision);
        if (dist <= stopAt || flat.lengthSqr() <= 1.0E-4) {
            ForcedPlayerMovement.push(victim, 0.0, vy, 0.0);
        } else {
            double speed = fight != null ? ImperioRules.PLAYER_CHASE_SPEED : ImperioRules.PLAYER_WALK_SPEED;
            Vec3 velocity = flat.normalize().scale(speed);
            ForcedPlayerMovement.push(victim, velocity.x, vy, velocity.z);
        }
        if (fight != null && dist <= ImperioRules.PLAYER_REACH && victim.getAttackStrengthScale(0.5F) >= 0.9F) {
            MindControl.forcedAttack(victim, fight);
        }
    }

    /** Like a wolf: a random standable spot within 3 blocks of the owner; false if none was free. */
    public static boolean teleportNear(LivingEntity mover, LivingEntity owner) {
        if (mover.level() != owner.level()) {
            return false;
        }
        RandomSource random = mover.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            double x = Mth.floor(owner.getX()) + random.nextInt(7) - 3 + 0.5;
            double z = Mth.floor(owner.getZ()) + random.nextInt(7) - 3 + 0.5;
            double y = Mth.floor(owner.getY());
            if (Math.abs(x - owner.getX()) < 1.5 && Math.abs(z - owner.getZ()) < 1.5) {
                continue;
            }
            BlockPos below = BlockPos.containing(x, y - 1, z);
            if (!mover.level().getBlockState(below).isFaceSturdy(mover.level(), below, Direction.UP)) {
                continue;
            }
            AABB box = mover.getBoundingBox().move(x - mover.getX(), y - mover.getY(), z - mover.getZ());
            if (!mover.level().noCollision(mover, box)) {
                continue;
            }
            if (mover instanceof ServerPlayer player) {
                player.teleportTo(x, y, z);
            } else {
                mover.teleportTo(x, y, z);
            }
            if (mover instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            return true;
        }
        return false;
    }

    private static void pruneNotes(long now) {
        long horizon = ImperioRules.ASSIST_MEMORY_TICKS * 2L;
        LAST_ATTACKED.values().removeIf(note -> now - note.at() > horizon);
        LAST_ATTACKER.values().removeIf(note -> now - note.at() > horizon);
    }

    // ---- Release -------------------------------------------------------------------------------

    private static void end(MinecraftServer server, Thrall thrall, LivingEntity victim) {
        THRALLS.remove(thrall.victimId);
        release(victim, thrall);
        afterRelease(server, thrall);
    }

    /** Undo everything on a victim that is still in the world. */
    private static void release(LivingEntity victim, Thrall thrall) {
        if (victim instanceof Mob mob) {
            for (Goal goal : thrall.goals) {
                mob.goalSelector.removeGoal(goal);
            }
            for (Goal goal : thrall.targetGoals) {
                mob.targetSelector.removeGoal(goal);
            }
            if (mob.getPersistentData().getBoolean(TAG_ADDED_ALLIED)) {
                mob.removeTag(CombatTags.ALLIED);
                mob.getPersistentData().remove(TAG_ADDED_ALLIED);
            }
            if (thrall.addedStrength) {
                mob.removeEffect(MobEffects.DAMAGE_BOOST);
            }
            MindControl.setTargetBypassing(mob, null);
        }
        victim.removeEffect(DisableSupport.holder(ExampleMod.IMPERIUS));
    }

    /** The owner's last thrall is gone: Imperio's cooldown starts now. */
    private static void afterRelease(MinecraftServer server, Thrall thrall) {
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(thrall.ownerId);
        if (ownerPlayer != null && countFor(thrall.ownerId) == 0) {
            startImperioCooldown(ownerPlayer);
        }
    }

    /**
     * Imperio's cooldown on every wand the player carries (so swapping wands can't dodge it), and the cast's
     * Imperio XP once. Called when the last thrall ends and (Phase 6b) when a possession ends.
     */
    public static void startImperioCooldown(ServerPlayer owner) {
        if (owner.isCreative()) {
            return;
        }
        long now = owner.level().getGameTime();
        ItemStack main = owner.getMainHandItem();
        boolean awarded = false;
        if (MagicWandItem.isAnyWand(main)) {
            SpellCosts.startCooldown(owner, main, SpellType.IMPERIO, now);
            awarded = true;
        }
        Inventory inventory = owner.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != main && MagicWandItem.isAnyWand(stack)) {
                SpellCosts.startCooldown(owner, stack, SpellType.IMPERIO, now, SpellType.IMPERIO.getCooldownTicks(), false);
            }
        }
        if (!awarded) {
            MasteryAwards.onCastCompleted(owner, SpellType.IMPERIO);
        }
    }

    // ---- Events --------------------------------------------------------------------------------

    /** A thrall picks no fights of its own; it may only take its owner's fight or hit back at its attacker. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        LivingEntity self = event.getEntity();
        if (newTarget == null || MindControl.isBypassingTargetRules() || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        Thrall thrall = THRALLS.get(self.getUUID());
        if (thrall == null) {
            return;
        }
        LivingEntity owner = resolveOwner(level, thrall.ownerId);
        if (owner == null || !isFairTarget(owner, self, newTarget) || newTarget != self.getLastHurtByMob()) {
            event.setCanceled(true);
        }
    }

    /**
     * The victim is leaving (unload, death, logout, portal). Don't touch its goals here (this can fire from
     * inside its own AI tick); EntityJoinLevelEvent strips the marker and the tag if it ever comes back.
     */
    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Thrall thrall = THRALLS.remove(event.getEntity().getUUID());
        if (thrall != null) {
            afterRelease(level.getServer(), thrall);
        }
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)
                || THRALLS.containsKey(living.getUUID())) {
            return;
        }
        if (living.getPersistentData().getBoolean(TAG_ADDED_ALLIED)) {
            living.removeTag(CombatTags.ALLIED);
            living.getPersistentData().remove(TAG_ADDED_ALLIED);
        }
        Holder<MobEffect> imperius = ExampleMod.IMPERIUS.getHolder().orElse(null);
        if (imperius != null && living.hasEffect(imperius)) {
            living.removeEffect(imperius);
        }
    }

    /** Runs before the worlds save, so no thrall is written to disk still carrying the allied tag. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Iterator<Thrall> iterator = new ArrayList<>(THRALLS.values()).iterator();
        while (iterator.hasNext()) {
            Thrall thrall = iterator.next();
            ServerLevel level = event.getServer().getLevel(thrall.dimension);
            LivingEntity victim = level == null ? null : living(level.getEntity(thrall.victimId));
            if (victim != null) {
                release(victim, thrall);
            }
        }
        THRALLS.clear();
        LAST_ATTACKED.clear();
        LAST_ATTACKER.clear();
    }
}
```

- [ ] **Step 4: Create `ImperioSpell`**

`src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.entity.HouseElfEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * An Imperio beam landing. Ranks I-III (and every NPC caster): the target becomes the caster's thrall.
 * Master: the hit is remembered for 3 seconds so the held channel (Phase 6b: possession) can take it.
 */
public final class ImperioSpell {
    /** The existing keys the Master hold reads through ModEvents.consumeRecentImperioHitTarget. */
    public static final String TAG_RECENT_HIT_TARGET = "hogwartsmagic:imperio_recent_hit_target";
    public static final String TAG_RECENT_HIT_EXPIRY = "hogwartsmagic:imperio_recent_hit_expiry";

    private ImperioSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide()) {
            return;
        }
        DisableSupport.interrupt(target);
        if (target instanceof HouseElfEntity elf) {
            // An elf under the Imperius Curse cannot will itself away: the dark road to binding one.
            elf.markSubdued(200);
        }
        Entity owner = CombatEffects.responsible(caster);
        int rank = DisableSupport.casterRank(caster, SpellType.IMPERIO);
        if (owner instanceof ServerPlayer player && ImperioRules.isPossessionRank(rank)) {
            CompoundTag data = player.getPersistentData();
            data.putUUID(TAG_RECENT_HIT_TARGET, target.getUUID());
            data.putLong(TAG_RECENT_HIT_EXPIRY, player.level().getGameTime() + 60L);
            return;
        }
        if (owner instanceof LivingEntity livingOwner) {
            ImperioThralls.enthrall(livingOwner, target, rank);
        }
    }
}
```

- [ ] **Step 5: Spell hits feed the owner's fight.** In `MindHooks.java`, replace:
```java
        ObliviateSpell.onHurtBySpell(target, caster);
    }
```
with:
```java
        ObliviateSpell.onHurtBySpell(target, caster);
        ImperioThralls.noteAttack(caster, target);
    }
```

- [ ] **Step 6: Wire `SpellType`.** Replace the whole Imperio case, from the line `            case IMPERIO -> {` down to its closing `            }` (the line directly above `            case FIENDFYRE -> {`), with:
```java
            case IMPERIO -> com.hogwartsmagic.spell.mind.ImperioSpell.apply(target, caster);
```
Then run `grep -n "interruptActiveCasting(" src/main/java/com/hogwartsmagic/spell/SpellType.java`. If the only hit left is the declaration `    private static void interruptActiveCasting(LivingEntity target) {`, delete that private helper (its five lines). If something else still calls it, leave it.

- [ ] **Step 7: The slot cap in the cast path.** In `SpellPackets.java`, `CastSpellPacket.castAs`, directly above the line
```java
            // Mana Check
```
(below the Avada branch from Task 5) insert:
```java
            // Imperio ranks I-III: no cast, and no mana spent, while every thrall slot is taken.
            if (spell == SpellType.IMPERIO && !com.hogwartsmagic.spell.mind.ImperioThralls.mayCast(player)) {
                return;
            }

```

- [ ] **Step 8: Only Master holds.** In `ImperioControlPacket.handle`, directly above the two lines
```java
            long now = player.level().getGameTime();
            boolean creative = player.isCreative();
```
insert:
```java
            // Ranks I-III make a thrall when the beam lands; only a Master holds the channel.
            if (!com.hogwartsmagic.spell.mind.ImperioRules.isPossessionRank(
                    com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell))) {
                return;
            }

```
In `ModEvents.java`, `handleWandUse`, inside
```java
                if (selected == SpellType.IMPERIO) {
                    if (imperioUseLatched) {
                        return true;
                    }
                    imperioUseLatched = true;
                    com.hogwartsmagic.event.ClientModEvents.startImperioHold();
```
replace the last line shown with:
```java
                    if (com.hogwartsmagic.spell.mind.ImperioRules.isPossessionRank(
                            com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.IMPERIO))) {
                        com.hogwartsmagic.event.ClientModEvents.startImperioHold(); // Master: hold to take control
                    }
```
In `ModEventsClientHooks.java`, `castSelectedSpell`, inside
```java
        if (selected == SpellType.IMPERIO) {
            com.hogwartsmagic.event.ClientModEvents.startImperioHold();
```
replace the second line shown with:
```java
            if (com.hogwartsmagic.spell.mind.ImperioRules.isPossessionRank(
                    com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.IMPERIO))) {
                com.hogwartsmagic.event.ClientModEvents.startImperioHold(); // Master: hold to take control
            }
```
(The client reads its own synced rank; the server refuses a non-Master hold on its own.)

- [ ] **Step 9: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.imperio.desc": "Confuses the target's mind.",
```
with:
```json
  "spell.hogwartsmagic.imperio.desc": "Bends a creature to your will: it follows you and fights whatever you fight or whatever attacks you (20s for creatures, 3s for players and bosses, longer with rank; two thralls at Expert). At Master, keep holding to possess the target and see through its eyes.",
```
and directly after `  "gui.hogwartsmagic.avada.interrupted": "The Killing Curse breaks apart!",` insert:
```json
  "gui.hogwartsmagic.imperio.thrall_gained": "%s is under your Imperius Curse",
  "gui.hogwartsmagic.imperio.at_cap": "Every thrall slot is taken (%s)",
  "gui.hogwartsmagic.imperio.too_strong": "%s is too strong for your Imperius Curse",
  "gui.hogwartsmagic.imperio.players_off": "Imperio on players is turned off on this server",
```

- [ ] **Step 10: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `ImperioThralls`, `ThrallGoals`, `ForcedPlayerMovement` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 11: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/ThrallGoals.java src/main/java/com/hogwartsmagic/spell/mind/ForcedPlayerMovement.java src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java src/main/java/com/hogwartsmagic/spell/mind/MindHooks.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/ModEventsClientHooks.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Imperio ranks I to III enthrall mobs, bosses and players as tame-dog thralls" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Imperio thralls (client side: nameplates, the thrall player's lock, the vignette) and the dedicated-server check

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`ThrallLabelPacket` and its registration)
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java` (label broadcasts, tracking)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`
- Create: `src/main/java/com/hogwartsmagic/client/ImperiusClient.java`
- Create: `src/main/java/com/hogwartsmagic/client/MindControlOverlay.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/client/MindControlGuiMixin.java`
- Modify: `src/main/resources/hogwartsmagic.mixins.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ImperioThralls` (Task 7); `MindControl.isControlled` (Task 2); `CombatEffects.has` (Phase 2); `ExampleMod.IMPERIUS` (Task 2).
- Produces:
  - `SpellPackets.ThrallLabelPacket(int entityId, String ownerName)` (S2C; an empty name clears the label)
  - `ImperiusClient.acceptLabel(SpellPackets.ThrallLabelPacket)`, `ImperiusClient.isLocalPlayerLocked()` → `boolean`
  - `MindControlOverlay.render(GuiGraphics)`; package-private `MindControlOverlay.vignette(GuiGraphics graphics, int rgb, int peak)` (Phase 6b reuses both)

- [ ] **Step 1: The label packet.** In `SpellPackets.java`, at the end of `register()`, directly above the closing `    }` that comes right before `    public static void syncRelics(ServerPlayer player) {`, add:
```java
        CHANNEL.messageBuilder(ThrallLabelPacket.class)
                .encoder((p, buf) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.ownerName, 64);
                })
                .decoder(buf -> new ThrallLabelPacket(buf.readVarInt(), buf.readUtf(64)))
                .consumerMainThread(ThrallLabelPacket::handle)
                .add();
```
At the end of the class, directly above its final closing brace, add:
```java

    /** Server to client: entity {@code entityId} is an Imperio thrall of {@code ownerName}; an empty name clears it. */
    public record ThrallLabelPacket(int entityId, String ownerName) {
        public static void handle(ThrallLabelPacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                ctx.setPacketHandled(true); // server-to-client only
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("thrallLabel", p));
        }
    }
```

- [ ] **Step 2: Broadcast the labels.** In `ImperioThralls.java`:

Add the imports `com.hogwartsmagic.network.SpellPackets`, `net.minecraftforge.event.entity.player.PlayerEvent` and `net.minecraftforge.network.PacketDistributor`.

In `enthrall`, replace:
```java
        THRALLS.put(target.getUUID(), thrall);
        MindFx.burst(target, GREEN);
```
with:
```java
        THRALLS.put(target.getUUID(), thrall);
        broadcastLabel(target, thrall.ownerName);
        MindFx.burst(target, GREEN);
```
In `release`, replace:
```java
        victim.removeEffect(DisableSupport.holder(ExampleMod.IMPERIUS));
    }
```
with:
```java
        victim.removeEffect(DisableSupport.holder(ExampleMod.IMPERIUS));
        broadcastLabel(victim, "");
    }

    /** The "Thrall of <owner>" nameplate for everyone watching the victim (and the victim, if a player). */
    private static void broadcastLabel(LivingEntity victim, String ownerName) {
        SpellPackets.CHANNEL.send(new SpellPackets.ThrallLabelPacket(victim.getId(), ownerName),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(victim));
    }

    /** Someone walks into view of a thrall after it was enthralled. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Thrall thrall = THRALLS.get(event.getTarget().getUUID());
        if (thrall != null && event.getEntity() instanceof ServerPlayer watcher) {
            SpellPackets.CHANNEL.send(new SpellPackets.ThrallLabelPacket(event.getTarget().getId(), thrall.ownerName),
                    PacketDistributor.PLAYER.with(watcher));
        }
    }
```

- [ ] **Step 3: Dispatch on the client.** In `ClientRuntimeBridge.java`, `handlePacket`, directly above:
```java
            default -> throw new IllegalArgumentException("Unknown client packet action: " + action);
```
add:
```java
            case "thrallLabel" -> ImperiusClient.acceptLabel((SpellPackets.ThrallLabelPacket) payload);
```

- [ ] **Step 4: Create `ImperiusClient`**

`src/main/java/com/hogwartsmagic/client/ImperiusClient.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.mind.MindControl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client half of Imperio thralls. Nameplates ("◆ Thrall of <owner>") come from ThrallLabelPacket. A
 * controlled local player (the imperius effect vanilla syncs to them) has their own controls taken away:
 * movement input is zeroed while they are a thrall (the server walks them), and attack, use, drop, hand
 * swap, hotbar, inventory, containers and the spell menus do nothing. The server refuses all of it on its
 * own too (MindControl); this only keeps the client from fighting it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ImperiusClient {
    private static final Map<Integer, String> LABELS = new HashMap<>();

    private ImperiusClient() {
    }

    public static void acceptLabel(SpellPackets.ThrallLabelPacket packet) {
        if (packet.ownerName().isEmpty()) {
            LABELS.remove(packet.entityId());
        } else {
            LABELS.put(packet.entityId(), packet.ownerName());
        }
    }

    public static boolean isLocalPlayerLocked() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && MindControl.isControlled(player);
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        String owner = LABELS.get(event.getEntity().getId());
        if (owner == null) {
            return;
        }
        event.setContent(Component.translatable("gui." + ExampleMod.MODID + ".imperio.thrall_label", owner)
                .withStyle(ChatFormatting.GREEN));
        event.setResult(Event.Result.ALLOW);
    }

    /** Thralls only: the server drives the body. (A Phase 6b puppet's input is set by PossessionClient instead.) */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!CombatEffects.has(event.getEntity(), ExampleMod.IMPERIUS)) {
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
        if (isLocalPlayerLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Runs before vanilla handles key presses this tick, so drained presses never reach it. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END) {
            if (minecraft.player != null && minecraft.player.tickCount % 100 == 0) {
                LABELS.keySet().removeIf(id -> minecraft.level == null || minecraft.level.getEntity(id) == null);
            }
            return;
        }
        if (!isLocalPlayerLocked()) {
            return;
        }
        Options options = minecraft.options;
        drain(options.keyDrop);
        drain(options.keyInventory);
        drain(options.keySwapOffhand);
        for (KeyMapping hotbar : options.keyHotbarSlots) {
            drain(hotbar);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // swallowed: a controlled body doesn't answer its owner's keys
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isLocalPlayerLocked() && (event.getNewScreen() instanceof AbstractContainerScreen<?>
                || event.getNewScreen() instanceof WandRadialMenuScreen
                || event.getNewScreen() instanceof SpellMenuScreen)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (isLocalPlayerLocked() && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LABELS.clear();
    }
}
```

- [ ] **Step 5: Create the overlay and its mixin**

`src/main/java/com/hogwartsmagic/client/MindControlOverlay.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mind.MindControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The green Imperius vignette over a controlled player's HUD. Its whole state is the imperius effect
 * vanilla syncs to the local player, so there is nothing to desync. Drawn by MindControlGuiMixin.
 */
public final class MindControlOverlay {
    private static final int STRIPS = 20;
    private static final int IMPERIUS_GREEN = 0x2FAF3F;

    private MindControlOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        if (MindControl.isControlled(player)) {
            float pulse = 0.85F + 0.15F * Mth.sin(player.tickCount * 0.2F);
            vignette(graphics, IMPERIUS_GREEN, (int) (170 * pulse));
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                    graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
        }
    }

    /** A soft edge faked with translucent strips down all four sides, strongest at the rim. */
    static void vignette(GuiGraphics graphics, int rgb, int peak) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int stripW = Math.max(1, (width / 5) / STRIPS);
        int stripH = Math.max(1, (height / 5) / STRIPS);
        for (int i = 0; i < STRIPS; i++) {
            float t = 1.0F - i / (float) STRIPS;
            int alpha = (int) (peak * t * t);
            if (alpha <= 2) {
                continue;
            }
            int colour = (alpha << 24) | rgb;
            int x = i * stripW;
            graphics.fill(x, 0, x + stripW, height, colour);
            graphics.fill(width - x - stripW, 0, width - x, height, colour);
            int y = i * stripH;
            graphics.fill(0, y, width, y + stripH, colour);
            graphics.fill(0, height - y - stripH, width, height - y, colour);
        }
    }
}
```

`src/main/java/com/hogwartsmagic/mixin/client/MindControlGuiMixin.java`:
```java
package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.MindControlOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 51 has no HUD event; draw the mind-control overlay over the finished HUD (same as BasiliskGazeGuiMixin). */
@Mixin(Gui.class)
public abstract class MindControlGuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void hogwartsmagic$renderMindControl(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        MindControlOverlay.render(guiGraphics);
    }
}
```

In `src/main/resources/hogwartsmagic.mixins.json`, in the `"client"` list, directly above the line `    "client.BasiliskGazeGuiMixin"` insert:
```json
    "client.MindControlGuiMixin",
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.imperio.players_off": "Imperio on players is turned off on this server",` insert:
```json
  "gui.hogwartsmagic.imperio.thrall_label": "◆ Thrall of %s",
  "gui.hogwartsmagic.imperio.victim": "Imperius: your body is not your own",
```

- [ ] **Step 7: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `ImperiusClient`, `MindControlOverlay`, `MindControlGuiMixin` or any other `client` class. (The client mixin is in the `"client"` list, so the server never applies it.)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/hogwartsmagic.mixins.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Player thrall movement on a real dedicated server (two clients; a human runs this)**

Player movement is client-authoritative, so this is the check that matters. Close every Minecraft first.
1. Run `./gradlew build`; copy `build/libs/hogwartsmagic-1.0.0.jar` into `C:\Users\alonl\OneDrive\Desktop\Hogwarts Magic Server\mods\` (replace the old jar). In that folder's `server.properties` set `online-mode=false` for the test (restore it afterwards). Start the server with `START SERVER.bat` and wait for `Done (`.
2. Client A: `./gradlew runClient`, Multiplayer → `localhost`. Client B: the user's own launcher with a Forge 1.21-51.0.33 profile holding the same jar, joining `localhost`. (Don't start a second gradle client while A runs.)
3. On the server console: `op Dev`, then for A: `/spellmastery setrank Dev imperio 1`; give A a wand with Imperio learned.
4. A casts Imperio on B. Expected: B sees the green vignette and "Imperius: your body is not your own"; B's WASD, mouse buttons, Q, F, E and the hotbar do nothing; B is walked to within ~2.5 blocks of A and follows A for 3s; B's nameplate (seen by A) reads "◆ Thrall of Dev"; after 3s B gets control back and A's Imperio cooldown starts.
5. A punches a zombie, then casts Imperio on B again. Expected: B walks to the zombie and punches it with its held item; B never hits A.
6. Watch B's own screen during step 4: movement may lag slightly behind A, but B must not rubber-band more than about one block or get "moved wrongly" kicks. If it does, stop here and report it (the Phase 6b spike depends on this transport).

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/ImperiusClient.java src/main/java/com/hogwartsmagic/client/MindControlOverlay.java src/main/java/com/hogwartsmagic/mixin/client/MindControlGuiMixin.java src/main/resources/hogwartsmagic.mixins.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Show thrall nameplates and lock a thrall player out of their own controls" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Full verification

**Files:** none new.

**Interfaces:** none.

- [ ] **Step 1: Automated checks**

Run: `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/mind`
Expected: no output (common code stays client-free).

Run: `grep -rn "switch (" src/main/java/com/hogwartsmagic/client/ImperiusClient.java src/main/java/com/hogwartsmagic/client/MindControlOverlay.java`
Expected: no output.

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `spell.mind`, `ImperiusClient`, `MindControlOverlay` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/hogwartsmagic.mixins.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed)

Survival world, cheats on, wand in hand, the five spells learned. Use `/spellmastery setrank @s <spell> <1-4>` to switch ranks and `/crowdcontrol reset @e` between crowd-control checks.
1. Obliviate I on a zombie chasing you: it stops, wanders, and ignores you for 5s even when you stand next to it; hitting it (melee or a spell) makes it attack again at once.
2. Obliviate on a second player (LAN): they are silenced for 2s (greyed wheel); no blindness.
3. Obliviate IV on a pillager during a village raid: the raid bar drops by one and that pillager wanders off; on a Death Eater in a Death Eater raid: the raid bar drops, and a raid leader loses its banner role.
4. Obliviate and Confundo on the Basilisk, the Horntail, the Ironbelly and Voldemort: a puff of smoke and "... resists the charm", nothing else.
5. Confundo I on a skeleton next to a zombie: the skeleton shoots the zombie for 5s, never you, then goes back to hunting you.
6. Confundo IV on a zombie in a crowd: whatever it hits starts fighting the nearest other creature for 3s; those don't pass it on.
7. Confundo on a second player: Nausea and Slowness, no other effect.
8. Crucio I on a zombie and keep holding: you can't walk or jump, the zombie can't move or attack, red sparks link you, and it takes 1.5 every half second; it ends after 2s or the moment you let go, and the zombie moves again at once.
9. Crucio on a second player while they channel Protego Maxima: their barrier drops; they're stunned for 60% of the channel (PvP rule).
10. Crucio IV on a zombie to the end: if it survives it runs from you for 10s and won't target you meanwhile.
11. Crucio on a boss: it takes damage but keeps moving and attacking.
12. Avada Kedavra I: green sparks spiral for 1.5s, then the beam fires and kills a cow; you take 4 damage. At Master the windup is 0.8s and you take nothing.
13. Avada windup interrupted three ways: a second player Stupefies you, a skeleton arrow of 4+ damage hits you, a second player Expelliarmus-silences you: "The Killing Curse breaks apart!", no mana spent, Avada locked for 5s.
14. Avada on Voldemort (if he doesn't evade) or the Ironbelly: it loses a fifth of its max health, doesn't die.
15. Imperio I on a 20-health zombie: green sparkles, "◆ Thrall of <you>" above it; it follows you, teleports to you when you run 12+ blocks off, attacks a skeleton that shoots you, attacks a pig you punch or hit with Diffindo, and never attacks you; after 20s it returns to normal and Imperio's cooldown starts then (not at cast).
16. Imperio I on an iron golem (100 health): "... is too strong", no thrall. Imperio III: it works, and a second thrall is allowed; a third cast says every slot is taken and spends no mana.
17. Imperio II thrall gets Strength I; a villager thrall punches your enemies for 2.
18. Imperio on a boss: a thrall for 3s at Novice (5s at Expert) whatever its health.
19. Imperio on a player (two clients): the Task 8 Step 8 checks; with `imperioAffectsPlayers=false` in the config, the cast says Imperio on players is off.
20. Log out while holding a thrall, log back in: the thrall is back to normal (no green nameplate, no allied tag) and Imperio is cooling down.
21. Imperio IV: the beam no longer makes a thrall; hit a zombie and keep holding for 2s: today's control channel starts (Phase 6b replaces it with possession).

- [ ] **Step 3: Commit** (only if Step 1 or 2 needed a fix; otherwise nothing to commit)
```bash
git add <the files you fixed>
git commit -m "Fix issues found in the Phase 6a verification pass" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage (roadmap Phase 6, first half, and the Imperio thrall section):** Obliviate makes the mob forget the caster and leave them alone (T3), silences players (T3), Master pulls raiders out of raids and forgets home (T3); Confundo friendly fire on the nearest other creature (T4), contagious at Master (T4); Crucio is a channel that locks caster (rooted) and target (stunned through `CrowdControl`) (T6); Avada windup interruptible by stun, silence, Stupefy and hard hits (T5), windup and backlash shrink with rank (T1, T5), 20% max health vs bosses (T5); bosses immune to Obliviate, Confundo and the instant kill (T3, T4, T5); Imperio I–III thrall: follows and teleports like a wolf, attacks what the owner attacks and what attacks the owner, never the owner or other thralls (T7), players driven server-side for 3/4/5s with a green vignette and dead keys (T7, T8), bosses at 3/4/5s ignoring the health cap (T1, T7), mobs 20/30/45s with the 40/60/100 health cap, 1/1/2 thralls, Strength I from Adept (T1, T7), green glow and owner nameplate (T7, T8), cooldown when the effect ends (T7), `imperioAffectsPlayers` default on, mobs always allowed (T2, T7), AI restored on release (T7), the old retarget behaviour replaced while keeping the allied tag (T7). Corruption: none (Decision 9). Master possession: Phase 6b.
- **Dedicated-server safety:** every new server class is in `spell/mind` with no client imports (T9 grep); client code is in `client/` (`ImperiusClient`, `MindControlOverlay`) and a client-list mixin; the one S2C packet goes through `ClientSideBridge`; `runServer` boots in T2, T5, T6, T7, T8, T9; the two-client dedicated-server movement check is T8 Step 8.
- **Placeholder scan:** no TBD or "similar to"; every edit names its anchor text.
- **Type consistency:** `DisableSupport.casterRank(Entity, SpellType)` → `int` everywhere; `CrowdControl.apply(LivingEntity, Holder<MobEffect>, int, Entity)` → `int` in T6; `CombatEffects.applySilenced(LivingEntity, int, Entity)` in T3; `ImperioThralls.startImperioCooldown(ServerPlayer)` defined in T7 and reused by 6b; `CastSpellPacket.castAs(ServerPlayer, CastSpellPacket)` defined in T2, called in T5 (and 6b); `MindControl.setTargetBypassing(Mob, LivingEntity)` used in T3, T4, T6, T7; `ThrallLabelPacket(int, String)` accessors `entityId()`/`ownerName()` used by `ImperiusClient`; `ConfundoRules.amplifierFor(boolean)` → `int`; `AvadaRules.windupDone(long, int)`.

