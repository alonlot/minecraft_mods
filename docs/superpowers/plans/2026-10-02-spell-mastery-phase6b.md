# Spell Mastery Phase 6b (Imperio Master: Possession) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At Master, Imperio stops making a thrall and becomes possession. Hit a target with Imperio and keep holding right-click: your camera jumps to the victim's eyes and your keys and mouse drive its body (walk, look, jump, attack, its held item) for up to 20 seconds (5 on a boss), while your own body stands frozen in a trance. Letting go ends it early. A hit of 4+ damage on your body throws you out. A possessed player can mash SPACE to resist after 8 seconds. Possessing a player hands you their hotbar to scroll and, while they hold their wand, their learned spells at their ranks, their mana and their cooldowns, cast through the normal cast path as them (never Imperio or Apparition). When it ends, the camera snaps back, both bodies get their controls back, a victim who broke free is immune to Imperio for 3s, and you are nauseous for 3s.

**Architecture:** The server owns everything. `PossessionManager` holds one `PossessionSession` per caster (keyed both ways), ticks it, validates every packet (only the session's caster may drive it, rate limits per packet type, sanitised axes and angles, a heartbeat timeout, session expiry) and ends it for every reason the spec lists. Input comes in on the existing `ImperioControlPacket` (forward, strafe, jump, attack, yaw, pitch), which the Imperio hold channel in `ClientModEvents` already streams; the old ModEvents channel that consumed it (`TAG_IMPERIO_*`, `tickImperioChannel`, `applyImperioControl`) is replaced by the session. A mob victim is driven server-side (velocity, as the old channel did) with its AI paused through a hidden `possessed` marker that makes it report `isImmobile()`. A player victim is driven by forwarding the caster's input to the victim's own client (`PuppetInputPacket`), whose vanilla physics carries it out, so movement stays client-authoritative and doesn't fight the server's movement checks. All camera, look and input code lives in `client/PossessionClient` (a `Dist.CLIENT` subscriber) plus one client mixin on `Entity#turn`: the caster's client calls `setCameraEntity(victim)`, steers a virtual look from the mouse, and zeroes its own body's input. Task 2 is a spike: the smallest end-to-end session (op command, camera, input, mob and player victims) proven on a real dedicated server with two clients before anything else is built on it.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`), SpongePowered Mixin (already set up).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 6: Mind spells and Unforgivables", subsection "Imperio: Thrall (ranks I–III) and Possession (Master)" (the user's own design, followed exactly), Decision 12, and "Global constraints". Where it disagrees with `2026-10-02-spell-mastery-design.md`, the roadmap wins.

**Depends on Phase 6a (`2026-10-02-spell-mastery-phase6a.md`, merged first) and Phases 1–5.** This plan calls, by exact signature:
- Phase 6a: `ImperioRules.isPossessionRank(int)`; `ImperioThralls.startImperioCooldown(ServerPlayer)`, `ImperioThralls.isThrall(Entity)`; `ImperioSpell.apply(LivingEntity, Entity)` (edited here); `MindControl.isControlled(LivingEntity)`, `MindControl.forcedAttack(ServerPlayer, Entity)`, `MindControl.setTargetBypassing(Mob, LivingEntity)` (edited here); `MindFx.tell/resisted/burst`; `ForcedPlayerMovement.verticalVelocity/push` (spike fallback only); `ExampleMod.ROOTED`; `Config.isImperioAffectsPlayers()`; `SpellPackets.CastSpellPacket.castAs(ServerPlayer, CastSpellPacket)`; `client/ImperiusClient` (locks a controlled player's keys, screens and scroll, and zeroes movement only for the `imperius` effect); `client/MindControlOverlay.render(GuiGraphics)` and its package-private `vignette(GuiGraphics, int, int)` (edited here); the 6a lang line `  "gui.hogwartsmagic.imperio.victim": "Imperius: your body is not your own",` as a text anchor.
- Phase 2: `CombatEffects.has/isCastingBlocked/notifyCastBlocked`, `CombatTags.isBoss`; the `/crowdcontrol` registration line in `ExampleMod.onRegisterCommands` as a text anchor.
- Phase 3: `DisableSupport.holder(RegistryObject<MobEffect>)`, `DisableSupport.interrupt(LivingEntity)`.
- Phase 1: `SpellMasteryData.getRank(Player, SpellType)`, `MasteryMath.roman(int)`, `MasteryAwards` (its private `eligible`, edited here).
- Existing: `ModEvents.consumeRecentImperioHitTarget(ServerPlayer)`, `ModEvents.getImperioStartCost(Player, ItemStack)`, `ModEvents.revealInvisibilityCloak(ServerPlayer)`, `ModEvents.interruptActiveCasting(ServerPlayer)`, `SpellPackets.ImperioControlPacket(boolean startChannel, boolean stopChannel, float forward, float strafe, boolean jump, boolean attack, float yaw, float pitch)`, `SpellPackets.SyncManaPacket(float, float)`, `HungarianHorntailEntity.canStartAction()/beginBreath()` (package-private), `BasiliskEntity.getGazeOrigin()`.

Before Task 1, confirm 6a is in:
```bash
grep -n "public static void startImperioCooldown" src/main/java/com/hogwartsmagic/spell/mind/ImperioThralls.java
grep -n "public static void castAs" src/main/java/com/hogwartsmagic/network/SpellPackets.java
grep -n "static void vignette" src/main/java/com/hogwartsmagic/client/MindControlOverlay.java
```
Each must print one line. If any doesn't, stop: Phase 6a isn't merged.

## Global Constraints

- **Possession numbers:** up to 400 ticks (20s) on a mob or player, 100 (5s) on a boss (`CombatTags.isBoss`). A hit of 4.0+ on the caster's body ends it. A player victim may resist from tick 160 (8s): each 10-tick window in which they press SPACE at least 6 times gives a 20% chance to break free; presses are capped at 12 per window and the victim may report at most once every 8 ticks. Boss possession (5s) and the 6a thrall are too short to resist. A broken-free victim is immune to Imperio for 60 ticks; the caster gets Nausea for 60 ticks whenever a session ends (unless they logged out).
- **How it starts:** Master Imperio (`ImperioRules.isPossessionRank`), beam hit (6a records it for 60 ticks), keep holding right-click: the existing 40-tick client hold threshold sends `ImperioControlPacket(startChannel=true)`. The server checks the wand, the learned spell, Master rank, stun/silence, cooldown, the start mana (`ModEvents.getImperioStartCost`), the recent hit target, `imperioAffectsPlayers` for players, creative/spectator, immunity, and that nobody involved is already possessing, possessed or a thrall. Mana upkeep every 10 ticks as the old channel (`ModEvents.getImperioUpkeepCost`); running out ends it. Imperio's cooldown starts when the session ends (`ImperioThralls.startImperioCooldown`), which is also when the caster earns Imperio XP.
- **End conditions:** the caster lets go (stop packet, or 10 ticks without an input packet), time runs out, the victim breaks free, the body takes 4+, either dies, either logs out, either changes dimension, the victim gets more than 48 blocks from the body, the caster switches off their wand or off Imperio, the caster is interrupted (stunned, silenced, Stupefied: `ModEvents.interruptActiveCasting`), mana runs out, or the server stops.
- **The caster's body:** carries the hidden `rooted` effect (6a: speed and jump zero), its client zeroes its movement input, cancels its attack, use and pick-block clicks, swallows drop, swap, inventory and hotbar keys (hotbar keys and the scroll wheel are re-routed to the puppet's hotbar), and the mouse turns a virtual look instead of the body. The server refuses the caster's own casts and interactions (`MindControl`). It can still be hurt.
- **The victim:** carries the hidden `possessed` effect. A mob's AI is paused (`isImmobile()`), its target cleared, and it is driven by velocity. A player's own keys do nothing (6a's `ImperiusClient`, via `MindControl.isControlled`, now true for `possessed`), their movement and look come from the caster through their own client, they can't scroll their hotbar (the server re-applies the forced slot every tick), and they can't drop, swap, open containers or their inventory, or cast for themselves.
- **Puppet casting:** only while the victim holds a wand in their main hand. `PossessionCastPacket(spellId)` → the server sets the victim's selected spell and runs `CastSpellPacket.castAs(victim, plain cast)`, so the victim's learned spells, ranks, mana, cooldowns, stun and silence are checked exactly as if they cast it. Imperio and Apparition are refused. A puppet earns no mastery XP while possessed.
- **Rate limits per session:** input 30 per 20 ticks, hotbar 10 per 20 ticks, casts 4 per 20 ticks, resist reports one per 8 ticks; excess packets are dropped. Axes are clamped to −1..1, pitch to −90..90, yaw wrapped to −180..180, NaN becomes 0.
- **Debug sessions** (`/imperio possess <target>`, op level 2) skip the hold requirement, mana, cooldown and the caster-wand check; everything else is identical. `/imperio release` ends your session.
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/` (and one client-list mixin), is reached through the `ClientSideBridge` → `ClientRuntimeBridge` string dispatch or a `Dist.CLIENT` event subscriber, and talks back through C2S packets the server validates. This plan adds three S2C packets (`PossessionStatePacket`, `PuppetInputPacket`, `PuppetSnapshotPacket`) and three C2S packets (`PossessionResistPacket`, `PossessionHotbarPacket`, `PossessionCastPacket`); input reuses `ImperioControlPacket`.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine. (`EndReason` carries its own lang key; nothing switches on it.)
- Screens: `renderTransparentBackground`, no blur. (`PuppetSpellWheelScreen` has no widgets and never calls `super.render`.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Entity event bytes 21, 35 and 63 crash the client. This plan broadcasts no entity events; if you add one, use 61/62/64/66/67.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards. (This plan changes no terrain itself; a possessed Horntail's fire and a puppet's spells go through their own existing rules.)
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs).
- `en_us.json` must stay valid JSON after every edit: an inserted line ends with a comma and goes after a line that already ends with one.
- Line numbers drift. Every edit names the exact text to find; trust the text, not a number.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/mind/PossessionRules.java` | Pure: durations, resist windows and roll, ejection, distance, sanitising, roles, blocked puppet spells |
| Create `src/main/java/com/hogwartsmagic/spell/mind/RateWindow.java` | Pure: fixed-window packet rate limiter |
| Create `src/main/java/com/hogwartsmagic/spell/mind/PossessionSession.java` | One session's state (package-private) |
| Create `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java` | Sessions: start, validate input, tick, drive, end, events, resist, hotbar, snapshot, puppet casts |
| Create `src/main/java/com/hogwartsmagic/spell/mind/PossessionDriver.java` | Drives a mob victim by velocity |
| Create `src/main/java/com/hogwartsmagic/spell/mind/PossessedAttacks.java` | A possessed body's attack: player melee, mob melee, ranged, boss specials, wand users' beams |
| Create `src/main/java/com/hogwartsmagic/mixin/LivingEntityPossessedMixin.java` | A possessed mob reports `isImmobile()` (AI paused) |
| Create `src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java` | Mouse look goes to the virtual look while possessing or possessed |
| Create `src/main/java/com/hogwartsmagic/client/PossessionClient.java` | Camera swap, virtual look, caster lock and input stream, victim input and look, resist counting, puppet hotbar and wheel glue |
| Create `src/main/java/com/hogwartsmagic/client/PuppetSpellWheelScreen.java` | The "Puppet" spell wheel |
| Create `src/main/java/com/hogwartsmagic/command/ImperioCommand.java` | `/imperio possess <target>`, `/imperio release` (op) |
| Modify `src/main/resources/hogwartsmagic.mixins.json` | Register both mixins |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | `POSSESSED` effect; command registration |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | Six packets; `ImperioControlPacket.handle` rewritten; casters refused in `CastSpellPacket.handle` |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Old Imperio channel removed; `stopImperioChannel` and `interruptActiveCasting` end the session; upkeep cost public |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Hold block no longer streams input; puppet wheel key; puppet spell-slot keys |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | Three dispatch cases |
| Modify `src/main/java/com/hogwartsmagic/client/MindControlOverlay.java` | Victim and caster HUD: resist prompt, puppet header, hotbar, mana |
| Modify `src/main/java/com/hogwartsmagic/spell/mind/MindControl.java` | `possessed` counts as controlled; a possessing caster's own actions are refused |
| Modify `src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java` | Immune and possessed targets resist |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | No XP for a puppet |
| Modify `src/main/java/com/hogwartsmagic/entity/HungarianHorntailEntity.java` | `possessedAttack()` (fire breath along the look) |
| Modify `src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java` | `possessedAttack()` (venom along the look) |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Effect, messages, HUD |
| Create tests under `src/test/java/com/hogwartsmagic/spell/mind/` | `PossessionRulesTest`, `RateWindowTest` |

---

### Task 1: The pure possession rules and the rate limiter

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/PossessionRules.java`, `src/main/java/com/hogwartsmagic/spell/mind/RateWindow.java`
- Test: `src/test/java/com/hogwartsmagic/spell/mind/PossessionRulesTest.java`, `src/test/java/com/hogwartsmagic/spell/mind/RateWindowTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `PossessionRules.ROLE_NONE = 0`, `ROLE_CASTER = 1`, `ROLE_VICTIM = 2` (`byte`); `MAX_TICKS = 400`, `BOSS_MAX_TICKS = 100`, `RESIST_DELAY_TICKS = 160`, `RESIST_WINDOW_TICKS = 10`, `RESIST_MIN_PRESSES = 6`, `RESIST_MAX_PRESSES = 12`, `RESIST_REPORT_GAP_TICKS = 8`, `RESIST_CHANCE = 0.2`, `BODY_HIT_EJECT = 4.0f`, `IMMUNITY_TICKS = 60`, `NAUSEA_TICKS = 60`, `MAX_DISTANCE = 48.0`, `HEARTBEAT_TIMEOUT = 10`, `ATTACK_COOLDOWN = 10`, `SPECIAL_ATTACK_COOLDOWN = 20`, `INPUTS_PER_WINDOW = 30`, `HOTBAR_PER_WINDOW = 10`, `CASTS_PER_WINDOW = 4`, `RATE_WINDOW_TICKS = 20`, `SNAPSHOT_INTERVAL_TICKS = 20`, `STATE_KEEPALIVE_TICKS = 20`, `CLIENT_STATE_TIMEOUT = 40`, `MARKER_GRACE_TICKS = 40`, `UPKEEP_INTERVAL = 10`
  - `static int maxTicks(boolean boss)`, `static boolean expired(long elapsed, int maxTicks)`, `static boolean heartbeatLapsed(long now, long lastSeen)`, `static boolean resistOpen(long elapsed)`, `static int resistWindow(long elapsed)` (−1 while closed), `static int clampPresses(int)`, `static boolean breaksFree(int presses, double roll)`, `static boolean reportAllowed(long now, long lastReport)`, `static boolean ejects(float damage)`, `static boolean tooFar(double distanceSqr)`, `static float axis(float)`, `static float pitch(float)`, `static float yaw(float)`, `static boolean validHotbarSlot(int)`, `static boolean isPuppetBlocked(String spellId)`
  - `new RateWindow(int capacity, long windowTicks)`, `boolean tryAcquire(long now)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/mind/PossessionRulesTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PossessionRulesTest {
    @Test
    void twentySecondsOrFiveOnABoss() {
        assertEquals(400, PossessionRules.maxTicks(false));
        assertEquals(100, PossessionRules.maxTicks(true));
        assertFalse(PossessionRules.expired(399, 400));
        assertTrue(PossessionRules.expired(400, 400));
    }

    @Test
    void releasingMeansTenTicksWithoutInput() {
        assertFalse(PossessionRules.heartbeatLapsed(110, 100));
        assertTrue(PossessionRules.heartbeatLapsed(111, 100));
    }

    @Test
    void resistingOpensAfterEightSecondsInHalfSecondWindows() {
        assertFalse(PossessionRules.resistOpen(159));
        assertTrue(PossessionRules.resistOpen(160));
        assertEquals(-1, PossessionRules.resistWindow(159));
        assertEquals(0, PossessionRules.resistWindow(160));
        assertEquals(0, PossessionRules.resistWindow(169));
        assertEquals(1, PossessionRules.resistWindow(170));
    }

    @Test
    void aBossPossessionIsTooShortToResist() {
        assertFalse(PossessionRules.resistOpen(PossessionRules.BOSS_MAX_TICKS - 1));
    }

    @Test
    void sixPressesGiveATwentyPercentRoll() {
        assertFalse(PossessionRules.breaksFree(5, 0.0));
        assertTrue(PossessionRules.breaksFree(6, 0.19));
        assertFalse(PossessionRules.breaksFree(6, 0.2));
        assertTrue(PossessionRules.breaksFree(12, 0.0));
    }

    @Test
    void pressesAreCappedSoAMacroCantEscapeAtOnce() {
        assertEquals(12, PossessionRules.clampPresses(500));
        assertEquals(0, PossessionRules.clampPresses(-3));
        assertTrue(PossessionRules.reportAllowed(108, 100));
        assertFalse(PossessionRules.reportAllowed(107, 100));
    }

    @Test
    void fourDamageThrowsTheCasterOut() {
        assertFalse(PossessionRules.ejects(3.99f));
        assertTrue(PossessionRules.ejects(4.0f));
    }

    @Test
    void fortyEightBlocksIsTooFar() {
        assertFalse(PossessionRules.tooFar(48.0 * 48.0));
        assertTrue(PossessionRules.tooFar(48.0 * 48.0 + 1.0));
    }

    @Test
    void inputIsSanitised() {
        assertEquals(1.0f, PossessionRules.axis(7.0f), 1e-6);
        assertEquals(-1.0f, PossessionRules.axis(-7.0f), 1e-6);
        assertEquals(0.0f, PossessionRules.axis(Float.NaN), 1e-6);
        assertEquals(90.0f, PossessionRules.pitch(300.0f), 1e-6);
        assertEquals(0.0f, PossessionRules.pitch(Float.NaN), 1e-6);
        assertEquals(-170.0f, PossessionRules.yaw(190.0f), 1e-4);
        assertEquals(170.0f, PossessionRules.yaw(-190.0f), 1e-4);
        assertEquals(0.0f, PossessionRules.yaw(Float.POSITIVE_INFINITY), 1e-6);
    }

    @Test
    void hotbarSlotsAreZeroToEight() {
        assertTrue(PossessionRules.validHotbarSlot(0));
        assertTrue(PossessionRules.validHotbarSlot(8));
        assertFalse(PossessionRules.validHotbarSlot(9));
        assertFalse(PossessionRules.validHotbarSlot(-1));
    }

    @Test
    void imperioAndApparitionCantBeCastThroughAPuppet() {
        assertTrue(PossessionRules.isPuppetBlocked("imperio"));
        assertTrue(PossessionRules.isPuppetBlocked("apparition"));
        assertFalse(PossessionRules.isPuppetBlocked("stupefy"));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/mind/RateWindowTest.java`:
```java
package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateWindowTest {
    @Test
    void allowsCapacityPerWindowThenRefuses() {
        RateWindow window = new RateWindow(3, 20);
        assertTrue(window.tryAcquire(100));
        assertTrue(window.tryAcquire(101));
        assertTrue(window.tryAcquire(102));
        assertFalse(window.tryAcquire(103));
    }

    @Test
    void aNewWindowRefills() {
        RateWindow window = new RateWindow(1, 20);
        assertTrue(window.tryAcquire(100));
        assertFalse(window.tryAcquire(119));
        assertTrue(window.tryAcquire(120));
    }

    @Test
    void timeGoingBackwardsStartsAFreshWindow() {
        RateWindow window = new RateWindow(1, 20);
        assertTrue(window.tryAcquire(500));
        assertTrue(window.tryAcquire(10));
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.mind.Possession*" --tests "com.hogwartsmagic.spell.mind.RateWindow*"`
Expected: compilation fails (`cannot find symbol: class PossessionRules`).

- [ ] **Step 3: Write the classes**

`src/main/java/com/hogwartsmagic/spell/mind/PossessionRules.java`:
```java
package com.hogwartsmagic.spell.mind;

/**
 * Imperio Master: possession. Every number of the session lives here so it is testable without
 * Minecraft. Times are ticks (20 per second).
 */
public final class PossessionRules {
    public static final byte ROLE_NONE = 0;
    public static final byte ROLE_CASTER = 1;
    public static final byte ROLE_VICTIM = 2;

    public static final int MAX_TICKS = 400;
    public static final int BOSS_MAX_TICKS = 100;
    public static final int RESIST_DELAY_TICKS = 160;
    public static final int RESIST_WINDOW_TICKS = 10;
    public static final int RESIST_MIN_PRESSES = 6;
    public static final int RESIST_MAX_PRESSES = 12;
    public static final int RESIST_REPORT_GAP_TICKS = 8;
    public static final double RESIST_CHANCE = 0.2;
    public static final float BODY_HIT_EJECT = 4.0f;
    public static final int IMMUNITY_TICKS = 60;
    public static final int NAUSEA_TICKS = 60;
    public static final double MAX_DISTANCE = 48.0;
    public static final int HEARTBEAT_TIMEOUT = 10;
    public static final int ATTACK_COOLDOWN = 10;
    public static final int SPECIAL_ATTACK_COOLDOWN = 20;
    public static final int INPUTS_PER_WINDOW = 30;
    public static final int HOTBAR_PER_WINDOW = 10;
    public static final int CASTS_PER_WINDOW = 4;
    public static final int RATE_WINDOW_TICKS = 20;
    public static final int SNAPSHOT_INTERVAL_TICKS = 20;
    public static final int STATE_KEEPALIVE_TICKS = 20;
    public static final int CLIENT_STATE_TIMEOUT = 40;
    public static final int MARKER_GRACE_TICKS = 40;
    public static final int UPKEEP_INTERVAL = 10;

    private PossessionRules() {
    }

    public static int maxTicks(boolean boss) {
        return boss ? BOSS_MAX_TICKS : MAX_TICKS;
    }

    public static boolean expired(long elapsed, int maxTicks) {
        return elapsed >= maxTicks;
    }

    /** The caster streams input every tick while holding; ten ticks of silence means they let go. */
    public static boolean heartbeatLapsed(long now, long lastSeen) {
        return now - lastSeen > HEARTBEAT_TIMEOUT;
    }

    public static boolean resistOpen(long elapsed) {
        return elapsed >= RESIST_DELAY_TICKS;
    }

    /** Index of the half-second resist window {@code elapsed} falls in, or -1 before resisting opens. */
    public static int resistWindow(long elapsed) {
        return resistOpen(elapsed) ? (int) ((elapsed - RESIST_DELAY_TICKS) / RESIST_WINDOW_TICKS) : -1;
    }

    public static int clampPresses(int presses) {
        return Math.max(0, Math.min(RESIST_MAX_PRESSES, presses));
    }

    public static boolean breaksFree(int presses, double roll) {
        return presses >= RESIST_MIN_PRESSES && roll < RESIST_CHANCE;
    }

    public static boolean reportAllowed(long now, long lastReport) {
        return now - lastReport >= RESIST_REPORT_GAP_TICKS;
    }

    public static boolean ejects(float damage) {
        return damage >= BODY_HIT_EJECT;
    }

    public static boolean tooFar(double distanceSqr) {
        return distanceSqr > MAX_DISTANCE * MAX_DISTANCE;
    }

    public static float axis(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    public static float pitch(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(-90.0f, Math.min(90.0f, value));
    }

    public static float yaw(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    public static boolean validHotbarSlot(int slot) {
        return slot >= 0 && slot <= 8;
    }

    /** No puppet chains, and no teleporting the victim away. */
    public static boolean isPuppetBlocked(String spellId) {
        return "imperio".equals(spellId) || "apparition".equals(spellId);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/mind/RateWindow.java`:
```java
package com.hogwartsmagic.spell.mind;

/** A fixed-window limiter: at most {@code capacity} acquisitions per {@code windowTicks} of game time. */
public final class RateWindow {
    private final int capacity;
    private final long windowTicks;
    private long windowStart = Long.MIN_VALUE;
    private int used;

    public RateWindow(int capacity, long windowTicks) {
        this.capacity = capacity;
        this.windowTicks = windowTicks;
    }

    public boolean tryAcquire(long now) {
        if (this.windowStart == Long.MIN_VALUE || now < this.windowStart || now - this.windowStart >= this.windowTicks) {
            this.windowStart = now;
            this.used = 0;
        }
        if (this.used >= this.capacity) {
            return false;
        }
        this.used++;
        return true;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.mind.Possession*" --tests "com.hogwartsmagic.spell.mind.RateWindow*"`
Expected: `BUILD SUCCESSFUL`, 14 tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/PossessionRules.java src/main/java/com/hogwartsmagic/spell/mind/RateWindow.java src/test/java/com/hogwartsmagic/spell/mind/PossessionRulesTest.java src/test/java/com/hogwartsmagic/spell/mind/RateWindowTest.java
git commit -m "Add the tested possession rules and a packet rate limiter" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Spike: a bare possession session, the camera swap and forced movement, proven on a dedicated server

This is the risky core, built small and checked on a real dedicated server with two clients before anything else depends on it. It adds a possession that only an op command can start (`/imperio possess <target>`), drives a mob or a player victim from the caster's keys and mouse, swaps the caster's camera, freezes the caster's body, and ends on `/imperio release`, timeout or either party vanishing. The Master hold, mana, cooldown, the full end-condition list, resisting, attacks, the hotbar and puppet spells come in Tasks 3–8, all built on this. **Tasks 3–9 must not start until Step 11 passes.**

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (`POSSESSED`; command registration)
- Create: `src/main/java/com/hogwartsmagic/spell/mind/PossessionSession.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java`
- Create: `src/main/java/com/hogwartsmagic/spell/mind/PossessionDriver.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/LivingEntityPossessedMixin.java`
- Create: `src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java`
- Modify: `src/main/resources/hogwartsmagic.mixins.json`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (two packets; the session hook in `ImperioControlPacket.handle`)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`
- Create: `src/main/java/com/hogwartsmagic/client/PossessionClient.java`
- Create: `src/main/java/com/hogwartsmagic/command/ImperioCommand.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `PossessionRules`, `RateWindow` (Task 1); 6a's `ExampleMod.ROOTED`, `MindControl.setTargetBypassing`, `MindFx`, `ImperioThralls.isThrall`, `Config.isImperioAffectsPlayers`; `DisableSupport.holder/interrupt` (Phase 3); `CombatTags.isBoss` (Phase 2).
- Produces:
  - `ExampleMod.POSSESSED`: `RegistryObject<MobEffect>` (hidden marker on the victim)
  - `PossessionManager.EndReason` (`RELEASED, TIMEOUT, BROKE_FREE, BODY_HIT, DIED, LOGGED_OUT, DIMENSION, TOO_FAR, INTERRUPTED, NO_MANA, INVALID`) with `String langKey()`
  - `PossessionManager.start(ServerPlayer caster, LivingEntity victim, boolean debug)` → `boolean`
  - `PossessionManager.endByCaster(ServerPlayer caster, EndReason reason)`
  - `PossessionManager.handleCasterPacket(ServerPlayer sender, SpellPackets.ImperioControlPacket p)` → `boolean` (true: the packet belonged to a session and was consumed)
  - `PossessionManager.isPossessing(LivingEntity)`, `PossessionManager.isPuppet(LivingEntity)`, `PossessionManager.isPossessedMob(LivingEntity)` → `boolean`
  - package-private: `PossessionManager.end(PossessionSession, EndReason)`, the `PossessionSession` fields listed in Step 2, `PossessionDriver.driveMob(Mob, PossessionSession)`
  - `SpellPackets.PossessionStatePacket(byte role, int otherId, int ticksLeft, boolean resistOpen, boolean holdRequired)` (S2C), `SpellPackets.PuppetInputPacket(float forward, float strafe, boolean jump, float yaw, float pitch)` (S2C)
  - `PossessionClient.acceptState(PossessionStatePacket)`, `PossessionClient.acceptPuppetInput(PuppetInputPacket)`, `PossessionClient.interceptTurn(Entity, double, double)` → `boolean`, `PossessionClient.isCaster()`, `PossessionClient.isVictim()`, `PossessionClient.ticksLeft()`, `PossessionClient.resistOpen()`, `PossessionClient.otherId()`
  - `ImperioCommand.register(CommandDispatcher<CommandSourceStack>)`

- [ ] **Step 1: The victim marker.** In `ExampleMod.java`, directly after 6a's `ROOTED` registration (the statement ending with `"rooted_jump"), -1.0,` and `net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));`), add:
```java
    /** Imperio Master: the possessed body (hidden). A possessed mob's AI is paused; a possessed player's own keys do nothing. */
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> POSSESSED = MOB_EFFECTS.register("possessed",
            () -> new net.minecraft.world.effect.MobEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x55CC66) {});
```

- [ ] **Step 2: Create `PossessionSession`**

`src/main/java/com/hogwartsmagic/spell/mind/PossessionSession.java`:
```java
package com.hogwartsmagic.spell.mind;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** One possession, server side. Mutated only on the server thread by PossessionManager. */
final class PossessionSession {
    final UUID casterId;
    final UUID victimId;
    final ResourceKey<Level> dimension;
    final long startedAt;
    final int maxTicks;
    final boolean boss;
    final boolean victimIsPlayer;
    /** Started by /imperio possess: no hold needed, no mana, no cooldown, no caster-wand check. */
    final boolean debug;

    final RateWindow inputRate = new RateWindow(PossessionRules.INPUTS_PER_WINDOW, PossessionRules.RATE_WINDOW_TICKS);
    final RateWindow hotbarRate = new RateWindow(PossessionRules.HOTBAR_PER_WINDOW, PossessionRules.RATE_WINDOW_TICKS);
    final RateWindow castRate = new RateWindow(PossessionRules.CASTS_PER_WINDOW, PossessionRules.RATE_WINDOW_TICKS);

    long lastSeen;
    long lastDrainAt;
    long lastStateAt = Long.MIN_VALUE / 2;
    long lastSnapshotAt = Long.MIN_VALUE / 2;
    long lastAttackAt = Long.MIN_VALUE / 2;
    long lastResistReportAt = Long.MIN_VALUE / 2;
    int attackCooldown = PossessionRules.ATTACK_COOLDOWN;

    float forward;
    float strafe;
    float yaw;
    float pitch;
    boolean jump;
    boolean attack;
    double lastVictimY;

    int forcedSlot = -1;
    int resistWindow = -1;
    int resistPresses;
    boolean resistAnnounced;

    PossessionSession(UUID casterId, UUID victimId, ResourceKey<Level> dimension, long startedAt, int maxTicks,
                      boolean boss, boolean victimIsPlayer, boolean debug) {
        this.casterId = casterId;
        this.victimId = victimId;
        this.dimension = dimension;
        this.startedAt = startedAt;
        this.maxTicks = maxTicks;
        this.boss = boss;
        this.victimIsPlayer = victimIsPlayer;
        this.debug = debug;
        this.lastSeen = startedAt;
        this.lastDrainAt = startedAt;
    }
}
```

- [ ] **Step 3: Create `PossessionDriver`**

`src/main/java/com/hogwartsmagic/spell/mind/PossessionDriver.java`:
```java
package com.hogwartsmagic.spell.mind;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Drives a possessed mob by velocity (the old Imperio channel's movement, kept). Its AI is paused by the
 * possessed marker (LivingEntityPossessedMixin), so nothing fights the steering; hurtMarked sends the
 * motion to every watcher.
 */
final class PossessionDriver {
    private PossessionDriver() {
    }

    static void driveMob(Mob mob, PossessionSession session) {
        mob.setYBodyRot(session.yaw);
        if (mob.getTarget() != null) {
            MindControl.setTargetBypassing(mob, null);
        }
        mob.getNavigation().stop();
        Vec3 look = Vec3.directionFromRotation(0.0F, session.yaw).normalize();
        Vec3 left = new Vec3(look.z, 0.0, -look.x); // input strafe is positive to the left
        Vec3 desired = look.scale(session.forward).add(left.scale(session.strafe));
        if (desired.lengthSqr() > 1.0) {
            desired = desired.normalize();
        }
        double speed = Math.max(0.1, mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 1.05;
        Vec3 motion = mob.getDeltaMovement();
        boolean idle = Math.abs(session.forward) < 0.01F && Math.abs(session.strafe) < 0.01F;
        Vec3 next = idle
                ? new Vec3(motion.x * 0.35, motion.y, motion.z * 0.35)
                : new Vec3(desired.x * speed + motion.x * 0.15, motion.y, desired.z * speed + motion.z * 0.15);
        if (session.jump && mob.onGround()) {
            next = new Vec3(next.x, 0.42, next.z);
        }
        mob.setDeltaMovement(next);
        mob.hurtMarked = true;
        mob.hasImpulse = true;
    }
}
```

- [ ] **Step 4: Create `PossessionManager`**

`src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Imperio Master: possession sessions. The server owns them: it starts them, takes input only from each
 * session's own caster (rate-limited and sanitised), drives the victim every tick, keeps both clients told
 * what they are (PossessionStatePacket, re-sent every second as a keepalive), and ends the session for
 * every reason the spec lists. A mob victim is driven here by velocity; a player victim's own client
 * carries out the caster's input (PuppetInputPacket), so player movement stays client-authoritative.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PossessionManager {
    /** Why a possession ended. Each carries the caster-facing line. */
    public enum EndReason {
        RELEASED("released"),
        TIMEOUT("timeout"),
        BROKE_FREE("broke_free"),
        BODY_HIT("body_hit"),
        DIED("died"),
        LOGGED_OUT("logged_out"),
        DIMENSION("dimension"),
        TOO_FAR("too_far"),
        INTERRUPTED("interrupted"),
        NO_MANA("no_mana"),
        INVALID("invalid");

        private final String key;

        EndReason(String key) {
            this.key = key;
        }

        public String langKey() {
            return "gui." + ExampleMod.MODID + ".possession.end." + this.key;
        }
    }

    private static final Map<UUID, PossessionSession> BY_CASTER = new HashMap<>();
    private static final Map<UUID, PossessionSession> BY_VICTIM = new HashMap<>();
    private static Holder<MobEffect> possessedHolder;

    private PossessionManager() {
    }

    // ---- Queries -------------------------------------------------------------------------------

    public static boolean isPossessing(LivingEntity entity) {
        return BY_CASTER.containsKey(entity.getUUID());
    }

    public static boolean isPuppet(LivingEntity entity) {
        return BY_VICTIM.containsKey(entity.getUUID());
    }

    /** Hot path (LivingEntityPossessedMixin runs it for every living entity every tick): a possessed non-player. */
    public static boolean isPossessedMob(LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }
        Holder<MobEffect> holder = possessedHolder;
        if (holder == null) {
            holder = possessedHolder = ExampleMod.POSSESSED.getHolder().orElse(null);
            if (holder == null) {
                return false;
            }
        }
        return entity.hasEffect(holder);
    }

    @Nullable
    private static LivingEntity living(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // ---- Start ---------------------------------------------------------------------------------

    public static boolean start(ServerPlayer caster, LivingEntity victim, boolean debug) {
        if (!(caster.level() instanceof ServerLevel level) || victim.level() != level || victim == caster || !victim.isAlive()) {
            return false;
        }
        if (isPossessing(caster) || isPuppet(caster) || isPossessing(victim) || isPuppet(victim) || ImperioThralls.isThrall(victim)) {
            MindFx.tell(caster, "possession.busy", victim.getDisplayName());
            return false;
        }
        boolean player = victim instanceof ServerPlayer;
        if (player) {
            ServerPlayer target = (ServerPlayer) victim;
            if (!Config.isImperioAffectsPlayers()) {
                MindFx.tell(caster, "imperio.players_off");
                return false;
            }
            if (target.isCreative() || target.isSpectator()) {
                return false;
            }
        } else if (!(victim instanceof Mob)) {
            return false;
        }
        boolean boss = CombatTags.isBoss(victim);
        long now = level.getGameTime();
        PossessionSession session = new PossessionSession(caster.getUUID(), victim.getUUID(), level.dimension(), now,
                PossessionRules.maxTicks(boss), boss, player, debug);
        session.yaw = victim.getYRot();
        session.pitch = victim.getXRot();
        session.lastVictimY = victim.getY();
        if (victim instanceof ServerPlayer target) {
            session.forcedSlot = target.getInventory().selected;
        }
        BY_CASTER.put(session.casterId, session);
        BY_VICTIM.put(session.victimId, session);
        int markerTicks = session.maxTicks + PossessionRules.MARKER_GRACE_TICKS;
        caster.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.ROOTED), markerTicks, 0, false, false, false));
        victim.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.POSSESSED), markerTicks, 0, false, false, false));
        DisableSupport.interrupt(victim);
        if (victim instanceof Mob mob) {
            MindControl.setTargetBypassing(mob, null);
            mob.getNavigation().stop();
        }
        sendState(session, caster, victim, now);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SpellType.IMPERIO.getSoundEvent(),
                SoundSource.PLAYERS, 1.0F, 0.8F);
        MindFx.burst(victim, ParticleTypes.HAPPY_VILLAGER);
        return true;
    }

    // ---- Input ---------------------------------------------------------------------------------

    /** Every ImperioControlPacket from a possessing caster lands here; only their own session is ever touched. */
    public static boolean handleCasterPacket(ServerPlayer sender, SpellPackets.ImperioControlPacket packet) {
        PossessionSession session = BY_CASTER.get(sender.getUUID());
        if (session == null) {
            return false;
        }
        if (packet.stopChannel()) {
            end(session, EndReason.RELEASED);
        } else if (!packet.startChannel()) {
            acceptInput(session, packet, sender.level().getGameTime());
        }
        return true;
    }

    private static void acceptInput(PossessionSession session, SpellPackets.ImperioControlPacket packet, long now) {
        if (!session.inputRate.tryAcquire(now)) {
            return; // over the rate: dropped (a flood can't hold the session open faster than allowed)
        }
        session.lastSeen = now;
        session.forward = PossessionRules.axis(packet.forward());
        session.strafe = PossessionRules.axis(packet.strafe());
        session.yaw = PossessionRules.yaw(packet.yaw());
        session.pitch = PossessionRules.pitch(packet.pitch());
        session.jump = packet.jump();
        session.attack = packet.attack();
    }

    // ---- End -----------------------------------------------------------------------------------

    public static void endByCaster(ServerPlayer caster, EndReason reason) {
        PossessionSession session = BY_CASTER.get(caster.getUUID());
        if (session != null) {
            end(session, reason);
        }
    }

    static void end(PossessionSession session, EndReason reason) {
        if (BY_CASTER.get(session.casterId) != session) {
            return; // already ended
        }
        BY_CASTER.remove(session.casterId);
        BY_VICTIM.remove(session.victimId);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer caster = server.getPlayerList().getPlayer(session.casterId);
        ServerLevel level = server.getLevel(session.dimension);
        Entity rawVictim = level == null ? null : level.getEntity(session.victimId);
        if (rawVictim == null) {
            rawVictim = server.getPlayerList().getPlayer(session.victimId);
        }
        if (caster != null) {
            caster.removeEffect(DisableSupport.holder(ExampleMod.ROOTED));
            sendNone(caster);
            caster.displayClientMessage(Component.translatable(reason.langKey()).withStyle(ChatFormatting.GREEN), true);
        }
        if (rawVictim instanceof LivingEntity victim) {
            victim.removeEffect(DisableSupport.holder(ExampleMod.POSSESSED));
            if (victim instanceof ServerPlayer victimPlayer) {
                sendNone(victimPlayer);
            }
            if (victim instanceof Mob mob) {
                MindControl.setTargetBypassing(mob, null);
                mob.getNavigation().stop();
            }
        }
    }

    // ---- Tick ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BY_CASTER.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (PossessionSession session : new ArrayList<>(BY_CASTER.values())) {
            tickSession(server, session);
        }
    }

    private static void tickSession(MinecraftServer server, PossessionSession session) {
        ServerLevel level = server.getLevel(session.dimension);
        ServerPlayer caster = server.getPlayerList().getPlayer(session.casterId);
        LivingEntity victim = level == null ? null : living(level.getEntity(session.victimId));
        if (level == null || caster == null || !caster.isAlive() || victim == null) {
            end(session, EndReason.INVALID);
            return;
        }
        long now = level.getGameTime();
        long elapsed = now - session.startedAt;
        if (PossessionRules.expired(elapsed, session.maxTicks)) {
            end(session, EndReason.TIMEOUT);
            return;
        }
        if (!session.debug && PossessionRules.heartbeatLapsed(now, session.lastSeen)) {
            end(session, EndReason.RELEASED);
            return;
        }
        drive(session, victim);
        if (now - session.lastStateAt >= PossessionRules.STATE_KEEPALIVE_TICKS) {
            sendState(session, caster, victim, now);
        }
    }

    private static void drive(PossessionSession session, LivingEntity victim) {
        victim.setYRot(session.yaw);
        victim.setYHeadRot(session.yaw);
        victim.setXRot(session.pitch);
        if (victim instanceof ServerPlayer victimPlayer) {
            SpellPackets.CHANNEL.send(new SpellPackets.PuppetInputPacket(session.forward, session.strafe, session.jump,
                    session.yaw, session.pitch), PacketDistributor.PLAYER.with(victimPlayer));
        } else if (victim instanceof Mob mob) {
            PossessionDriver.driveMob(mob, session);
        }
    }

    // ---- Packets out ---------------------------------------------------------------------------

    private static void sendState(PossessionSession session, ServerPlayer caster, LivingEntity victim, long now) {
        session.lastStateAt = now;
        long elapsed = now - session.startedAt;
        int left = (int) Math.max(0L, session.maxTicks - elapsed);
        boolean resistOpen = session.victimIsPlayer && PossessionRules.resistOpen(elapsed);
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionStatePacket(PossessionRules.ROLE_CASTER, victim.getId(), left,
                resistOpen, !session.debug), PacketDistributor.PLAYER.with(caster));
        if (victim instanceof ServerPlayer victimPlayer) {
            SpellPackets.CHANNEL.send(new SpellPackets.PossessionStatePacket(PossessionRules.ROLE_VICTIM, caster.getId(), left,
                    resistOpen, false), PacketDistributor.PLAYER.with(victimPlayer));
        }
    }

    private static void sendNone(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionStatePacket(PossessionRules.ROLE_NONE, -1, 0, false, false),
                PacketDistributor.PLAYER.with(player));
    }
}
```

- [ ] **Step 5: The two mixins**

`src/main/java/com/hogwartsmagic/mixin/LivingEntityPossessedMixin.java`:
```java
package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.mind.PossessionManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A possessed mob is immobile: LivingEntity.aiStep skips its AI step (goals, brain, navigation), so only
 * the caster's steering (PossessionDriver sets its velocity) moves it. Players are excluded; a possessed
 * player is moved by their own client. Common mixin: the dedicated server needs it.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPossessedMixin {

    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$possessedIsImmobile(CallbackInfoReturnable<Boolean> cir) {
        if (PossessionManager.isPossessedMob((LivingEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
```

`src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java`:
```java
package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.PossessionClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mouse turns the local player through Entity#turn. While possessing, the turn steers the caster's
 * virtual look (the victim's eyes) and the frozen body stays put; while possessed, the player's own mouse
 * does nothing. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(Entity.class)
public abstract class PossessionTurnMixin {

    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$possessionTurn(double yaw, double pitch, CallbackInfo ci) {
        if (PossessionClient.interceptTurn((Entity) (Object) this, yaw, pitch)) {
            ci.cancel();
        }
    }
}
```

In `src/main/resources/hogwartsmagic.mixins.json`: in the `"mixins"` list, directly above the line `    "LivingEntityStunMixin",` insert:
```json
    "LivingEntityPossessedMixin",
```
and in the `"client"` list, directly above the line `    "client.BasiliskGazeGuiMixin"` insert:
```json
    "client.PossessionTurnMixin",
```

- [ ] **Step 6: The two S2C packets and the session hook.** In `SpellPackets.java`, at the end of `register()` (directly above the closing `    }` that comes right before `    public static void syncRelics(ServerPlayer player) {`), add:
```java
        CHANNEL.messageBuilder(PossessionStatePacket.class)
                .encoder((p, buf) -> {
                    buf.writeByte(p.role);
                    buf.writeVarInt(p.otherId);
                    buf.writeVarInt(p.ticksLeft);
                    buf.writeBoolean(p.resistOpen);
                    buf.writeBoolean(p.holdRequired);
                })
                .decoder(buf -> new PossessionStatePacket(buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean()))
                .consumerMainThread(PossessionStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(PuppetInputPacket.class)
                .encoder((p, buf) -> {
                    buf.writeFloat(p.forward);
                    buf.writeFloat(p.strafe);
                    buf.writeBoolean(p.jump);
                    buf.writeFloat(p.yaw);
                    buf.writeFloat(p.pitch);
                })
                .decoder(buf -> new PuppetInputPacket(buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                        buf.readFloat(), buf.readFloat()))
                .consumerMainThread(PuppetInputPacket::handle)
                .add();
```
At the end of the class, directly above its final closing brace, add:
```java

    /** Server to client: your part in a possession (ROLE_NONE ends it), the other body, ticks left, resisting open. */
    public record PossessionStatePacket(byte role, int otherId, int ticksLeft, boolean resistOpen, boolean holdRequired) {
        public static void handle(PossessionStatePacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                ctx.setPacketHandled(true); // server-to-client only
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("possessionState", p));
        }
    }

    /** Server to client (the possessed player): the caster's movement and look, carried out by the victim's own client. */
    public record PuppetInputPacket(float forward, float strafe, boolean jump, float yaw, float pitch) {
        public static void handle(PuppetInputPacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                ctx.setPacketHandled(true);
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("puppetInput", p));
        }
    }
```
In `ImperioControlPacket.handle`, directly after 6a's guard:
```java
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a thrall can't start or steer a channel of its own
            }
```
insert:
```java
            // A running possession takes every packet from its own caster (input, heartbeat, release).
            if (com.hogwartsmagic.spell.mind.PossessionManager.handleCasterPacket(player, p)) {
                return;
            }
```

- [ ] **Step 7: Dispatch on the client.** In `ClientRuntimeBridge.java`, `handlePacket`, directly above:
```java
            default -> throw new IllegalArgumentException("Unknown client packet action: " + action);
```
add:
```java
            case "possessionState" -> PossessionClient.acceptState((SpellPackets.PossessionStatePacket) payload);
            case "puppetInput" -> PossessionClient.acceptPuppetInput((SpellPackets.PuppetInputPacket) payload);
```

- [ ] **Step 8: Create `PossessionClient`**

`src/main/java/com/hogwartsmagic/client/PossessionClient.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mind.PossessionRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Client half of possession. The caster's client swaps its camera to the victim (setCameraEntity), turns a
 * virtual look with the mouse, zeroes its own body's input, and streams raw keys plus the virtual look to the
 * server every tick on ImperioControlPacket. The victim's client applies the caster's input (PuppetInputPacket)
 * as its own movement and look. Every exit path snaps the camera back: the server's ROLE_NONE, a lapsed
 * keepalive, a level change, the victim entity vanishing, a logout.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PossessionClient {
    private static final float MOUSE_SCALE = 0.15F;
    private static final int PUPPET_INPUT_STALE_TICKS = 5;
    private static final int IGNORE_AFTER_LOCAL_STOP = 10;

    private static byte role = PossessionRules.ROLE_NONE;
    private static int otherId = -1;
    private static int ticksLeft;
    private static boolean resistOpen;
    private static boolean holdRequired = true;
    private static int ticksSinceState;
    private static int missingTicks;
    private static int ignoreStateTicks;
    @Nullable
    private static ClientLevel stateLevel;
    private static float virtualYaw;
    private static float virtualPitch;
    private static float puppetForward;
    private static float puppetStrafe;
    private static float puppetYaw;
    private static float puppetPitch;
    private static boolean puppetJump;
    private static int puppetAge = Integer.MAX_VALUE / 2;

    private PossessionClient() {
    }

    // ---- Packets in ----------------------------------------------------------------------------

    public static void acceptState(SpellPackets.PossessionStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.role() == PossessionRules.ROLE_NONE || minecraft.player == null) {
            reset();
            return;
        }
        if (ignoreStateTicks > 0) {
            return; // a keepalive that crossed our own stop packet
        }
        boolean becameCaster = packet.role() == PossessionRules.ROLE_CASTER && role != PossessionRules.ROLE_CASTER;
        role = packet.role();
        otherId = packet.otherId();
        ticksLeft = packet.ticksLeft();
        resistOpen = packet.resistOpen();
        holdRequired = packet.holdRequired();
        ticksSinceState = 0;
        stateLevel = minecraft.level;
        if (becameCaster) {
            virtualYaw = minecraft.player.getYRot();
            virtualPitch = minecraft.player.getXRot();
            missingTicks = 0;
        }
    }

    public static void acceptPuppetInput(SpellPackets.PuppetInputPacket packet) {
        if (role != PossessionRules.ROLE_VICTIM) {
            return;
        }
        puppetForward = packet.forward();
        puppetStrafe = packet.strafe();
        puppetJump = packet.jump();
        puppetYaw = packet.yaw();
        puppetPitch = packet.pitch();
        puppetAge = 0;
    }

    // ---- Queries -------------------------------------------------------------------------------

    public static boolean isCaster() {
        return role == PossessionRules.ROLE_CASTER;
    }

    public static boolean isVictim() {
        return role == PossessionRules.ROLE_VICTIM;
    }

    public static int ticksLeft() {
        return ticksLeft;
    }

    public static boolean resistOpen() {
        return resistOpen;
    }

    public static int otherId() {
        return otherId;
    }

    // ---- State ---------------------------------------------------------------------------------

    private static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        role = PossessionRules.ROLE_NONE;
        otherId = -1;
        ticksLeft = 0;
        resistOpen = false;
        holdRequired = true;
        ticksSinceState = 0;
        missingTicks = 0;
        stateLevel = null;
        puppetAge = Integer.MAX_VALUE / 2;
    }

    /** The caster lets go (or its view lost the victim): tell the server, snap back, ignore stale keepalives. */
    private static void stopLocally() {
        SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, true, 0.0F, 0.0F, false, false,
                virtualYaw, virtualPitch), PacketDistributor.SERVER.noArg());
        reset();
        ignoreStateTicks = IGNORE_AFTER_LOCAL_STOP;
    }

    /** Called by PossessionTurnMixin for every Entity#turn: true cancels the vanilla turn. */
    public static boolean interceptTurn(Entity entity, double yaw, double pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (role == PossessionRules.ROLE_NONE || entity != minecraft.player) {
            return false;
        }
        if (role == PossessionRules.ROLE_CASTER) {
            virtualYaw += (float) yaw * MOUSE_SCALE;
            virtualPitch = Mth.clamp(virtualPitch + (float) pitch * MOUSE_SCALE, -90.0F, 90.0F);
        }
        return true;
    }

    // ---- Ticks ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            if (role != PossessionRules.ROLE_NONE) {
                drainBodyKeys(minecraft);
            }
            return;
        }
        if (ignoreStateTicks > 0) {
            ignoreStateTicks--;
        }
        if (role == PossessionRules.ROLE_NONE) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.level != stateLevel
                || ++ticksSinceState > PossessionRules.CLIENT_STATE_TIMEOUT) {
            if (role == PossessionRules.ROLE_CASTER && minecraft.player != null) {
                stopLocally();
            } else {
                reset();
            }
            return;
        }
        if (ticksLeft > 0) {
            ticksLeft--;
        }
        if (role == PossessionRules.ROLE_CASTER) {
            tickCaster(minecraft);
        } else {
            puppetAge++;
        }
    }

    /** Runs before vanilla handles keys this tick: neither body drops, swaps, opens its inventory or changes slot. */
    private static void drainBodyKeys(Minecraft minecraft) {
        drain(minecraft.options.keyDrop);
        drain(minecraft.options.keyInventory);
        drain(minecraft.options.keySwapOffhand);
        for (KeyMapping hotbar : minecraft.options.keyHotbarSlots) {
            drain(hotbar);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // swallowed
        }
    }

    private static void tickCaster(Minecraft minecraft) {
        Entity victim = minecraft.level.getEntity(otherId);
        if (victim == null || victim.isRemoved()) {
            if (++missingTicks > PossessionRules.CLIENT_STATE_TIMEOUT) {
                stopLocally();
            }
            return;
        }
        missingTicks = 0;
        if (minecraft.getCameraEntity() != victim) {
            minecraft.setCameraEntity(victim);
        }
        if (holdRequired && !minecraft.options.keyUse.isDown()) {
            stopLocally();
            return;
        }
        SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, false, rawForward(minecraft), rawStrafe(minecraft),
                minecraft.options.keyJump.isDown(), minecraft.options.keyAttack.isDown(), virtualYaw, virtualPitch),
                PacketDistributor.SERVER.noArg());
    }

    /** Raw keys: the caster's own movement input is zeroed below, so it can't be read from the player. */
    private static float rawForward(Minecraft minecraft) {
        return (minecraft.options.keyUp.isDown() ? 1.0F : 0.0F) - (minecraft.options.keyDown.isDown() ? 1.0F : 0.0F);
    }

    private static float rawStrafe(Minecraft minecraft) {
        return (minecraft.options.keyLeft.isDown() ? 1.0F : 0.0F) - (minecraft.options.keyRight.isDown() ? 1.0F : 0.0F);
    }

    /** Every frame: the caster's camera looks exactly along the virtual look; a puppet looks where the caster looks. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || role == PossessionRules.ROLE_NONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (role == PossessionRules.ROLE_CASTER) {
            Entity victim = minecraft.level.getEntity(otherId);
            if (victim != null && minecraft.getCameraEntity() == victim) {
                pin(victim, virtualYaw, virtualPitch);
            }
        } else if (puppetAge <= PUPPET_INPUT_STALE_TICKS) {
            pin(minecraft.player, puppetYaw, puppetPitch);
        }
    }

    private static void pin(Entity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
    }

    // ---- Input ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (role == PossessionRules.ROLE_NONE || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        boolean drive = role == PossessionRules.ROLE_VICTIM && puppetAge <= PUPPET_INPUT_STALE_TICKS;
        float forward = drive ? puppetForward : 0.0F;
        float strafe = drive ? puppetStrafe : 0.0F;
        Input input = event.getInput();
        input.forwardImpulse = forward;
        input.leftImpulse = strafe;
        input.up = forward > 0.0F;
        input.down = forward < 0.0F;
        input.left = strafe > 0.0F;
        input.right = strafe < 0.0F;
        input.jumping = drive && puppetJump;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (role != PossessionRules.ROLE_NONE) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Looking through the victim's eyes, the caster's own hand and wand would float in view. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (role == PossessionRules.ROLE_CASTER) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ignoreStateTicks = 0;
    }
}
```

- [ ] **Step 9: The debug command.** `src/main/java/com/hogwartsmagic/command/ImperioCommand.java`:
```java
package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.mind.PossessionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /imperio possess <target>} and {@code /imperio release}, op only. A debug possession needs no
 * Master rank, beam hit, held key, mana or cooldown; everything else runs exactly as a real one. This is how
 * possession is tested on a dedicated server with two clients.
 */
public final class ImperioCommand {
    private ImperioCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("imperio")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("possess")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ImperioCommand::possess)))
                .then(Commands.literal("release")
                        .executes(ImperioCommand::release)));
    }

    private static int possess(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caster = ctx.getSource().getPlayerOrException();
        Entity entity = EntityArgument.getEntity(ctx, "target");
        if (!(entity instanceof LivingEntity target) || !PossessionManager.start(caster, target, true)) {
            ctx.getSource().sendFailure(Component.translatable("commands.hogwartsmagic.imperio.failed", entity.getDisplayName()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.imperio.started", target.getDisplayName()), false);
        return 1;
    }

    private static int release(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caster = ctx.getSource().getPlayerOrException();
        PossessionManager.endByCaster(caster, PossessionManager.EndReason.RELEASED);
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.hogwartsmagic.imperio.released"), false);
        return 1;
    }
}
```
Register it in `ExampleMod.onRegisterCommands`, directly after:
```java
        com.hogwartsmagic.command.CrowdControlCommand.register(event.getDispatcher());
```
add:
```java
        com.hogwartsmagic.command.ImperioCommand.register(event.getDispatcher());
```

- [ ] **Step 10: Strings.** In `en_us.json`, directly after 6a's `  "gui.hogwartsmagic.imperio.victim": "Imperius: your body is not your own",` insert:
```json
  "effect.hogwartsmagic.possessed": "Possessed",
  "gui.hogwartsmagic.possession.busy": "%s is already bound to another will",
  "gui.hogwartsmagic.possession.end.released": "You let go",
  "gui.hogwartsmagic.possession.end.timeout": "Your hold on the mind slips away",
  "gui.hogwartsmagic.possession.end.broke_free": "They broke free of your control!",
  "gui.hogwartsmagic.possession.end.body_hit": "A blow to your body throws you out!",
  "gui.hogwartsmagic.possession.end.died": "The possession ends in death",
  "gui.hogwartsmagic.possession.end.logged_out": "The connection to the mind is lost",
  "gui.hogwartsmagic.possession.end.dimension": "The mind slips beyond your reach",
  "gui.hogwartsmagic.possession.end.too_far": "You strayed too far from your body",
  "gui.hogwartsmagic.possession.end.interrupted": "Your concentration breaks",
  "gui.hogwartsmagic.possession.end.no_mana": "Not enough mana to hold the mind",
  "gui.hogwartsmagic.possession.end.invalid": "The possession ends",
  "commands.hogwartsmagic.imperio.started": "Possessing %s",
  "commands.hogwartsmagic.imperio.failed": "Could not possess %s",
  "commands.hogwartsmagic.imperio.released": "Released",
```

- [ ] **Step 11: Build, boot, and the go/no-go gate on a real dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessionManager`, `LivingEntityPossessedMixin`, `PossessionClient`, `PossessionTurnMixin` or any other `client` class. (`PossessionTurnMixin` is in the `"client"` list and must not load on the server.)

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/hogwartsmagic.mixins.json', encoding='utf-8'))"`
Expected: no output.

Two-client check (a human runs this; close every Minecraft first):
1. `./gradlew build`; copy `build/libs/hogwartsmagic-1.0.0.jar` into `C:\Users\alonl\OneDrive\Desktop\Hogwarts Magic Server\mods\` (replace the old jar); set `online-mode=false` in its `server.properties` for the test; run `START SERVER.bat` until `Done (`. On the console: `op Dev`.
2. Client A: `./gradlew runClient`, join `localhost`. Client B: the user's launcher (Forge 1.21-51.0.33 profile with the same jar), join `localhost`.
3. A runs `/summon minecraft:zombie ~3 ~ ~` then `/imperio possess @e[type=minecraft:zombie,limit=1,sort=nearest]`. Expected: A's view jumps to the zombie's eyes; A's own body stands still (visible in front of the camera) and doesn't turn; WASD walks the zombie smoothly in the direction A looks, space makes it jump, the mouse turns its head and body (B sees it turn); the zombie never wanders on its own; `/imperio release` snaps A's view back and the zombie resumes normal AI.
4. A runs `/imperio possess <B's name>`. Expected: A sees through B's eyes; A's WASD walks B, space jumps, the mouse turns B (B's own screen turns with it); B's own WASD, mouse movement, mouse buttons, Q, F, E and number keys do nothing (B's scroll wheel is locked in Task 5); after 20s the session ends by itself and both views are normal. Watch B's screen while A walks B in circles for 10 seconds: no rubber-banding back more than about a block, no "moved wrongly" or kick.
5. A possesses B, then B disconnects: A's view snaps back within 2 seconds. A possesses the zombie and A disconnects: on rejoining, A is not rooted.

**Gate:** if steps 3–5 pass, continue to Task 3. If the player victim (step 4) doesn't move, moves a lot later than the caster, or rubber-bands, switch the player branch of `PossessionManager.drive` to server-pushed velocity and re-run step 4. Replace:
```java
        if (victim instanceof ServerPlayer victimPlayer) {
            SpellPackets.CHANNEL.send(new SpellPackets.PuppetInputPacket(session.forward, session.strafe, session.jump,
                    session.yaw, session.pitch), PacketDistributor.PLAYER.with(victimPlayer));
        } else if (victim instanceof Mob mob) {
```
with:
```java
        if (victim instanceof ServerPlayer victimPlayer) {
            // Fallback transport: the server pushes the velocity (6a's thrall movement); the puppet packet keeps the look.
            net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, session.yaw);
            net.minecraft.world.phys.Vec3 left = new net.minecraft.world.phys.Vec3(look.z, 0.0, -look.x);
            net.minecraft.world.phys.Vec3 desired = look.scale(session.forward).add(left.scale(session.strafe));
            if (desired.lengthSqr() > 1.0) {
                desired = desired.normalize();
            }
            double vy = ForcedPlayerMovement.verticalVelocity(victimPlayer, session.lastVictimY, session.jump);
            session.lastVictimY = victimPlayer.getY();
            ForcedPlayerMovement.push(victimPlayer, desired.x * 0.22, vy, desired.z * 0.22);
            SpellPackets.CHANNEL.send(new SpellPackets.PuppetInputPacket(0.0F, 0.0F, false, session.yaw, session.pitch),
                    PacketDistributor.PLAYER.with(victimPlayer));
        } else if (victim instanceof Mob mob) {
```
If neither transport passes step 4, or the camera swap fails (step 3 or 4), stop and report to the user with what you saw; do not start Task 3.

- [ ] **Step 12: Commit**
```bash
git add src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/spell/mind/PossessionSession.java src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/spell/mind/PossessionDriver.java src/main/java/com/hogwartsmagic/mixin/LivingEntityPossessedMixin.java src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java src/main/resources/hogwartsmagic.mixins.json src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/PossessionClient.java src/main/java/com/hogwartsmagic/command/ImperioCommand.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Spike Imperio possession: server sessions, the camera swap and forced movement" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Master Imperio starts a possession (the hold, mana, cooldown; the old channel removed)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`ImperioControlPacket.handle` rewritten)
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java` (`startFromHold`, upkeep, cooldown on end)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (old channel removed; interrupt ends the session; upkeep cost public)
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` (the hold block stops streaming input)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: Task 2's `PossessionManager`; 6a's `ImperioRules.isPossessionRank`, `ImperioThralls.startImperioCooldown`, `MindControl.isControlled`; `CombatEffects.isCastingBlocked/notifyCastBlocked` (Phase 2); `ModEvents.consumeRecentImperioHitTarget`, `ModEvents.getImperioStartCost`, `ModEvents.revealInvisibilityCloak` (existing).
- Produces:
  - `PossessionManager.startFromHold(ServerPlayer caster, ItemStack wand, LivingEntity target)`
  - `ModEvents.getImperioUpkeepCost(Player, ItemStack)` → `float` (now public)
  - Removed: `ModEvents.stopImperioChannel`, `updateImperioInput`, `tickImperioChannel`, `applyImperioControl`, `performImperioMobAttack`, `getImperioAttackDamage`, `findImperioVictim` and the `TAG_IMPERIO_*` channel keys (the two recent-hit keys stay).

- [ ] **Step 1: Rewrite `ImperioControlPacket.handle`.** In `SpellPackets.java`, replace the whole method `public static void handle(ImperioControlPacket p, CustomPayloadEvent.Context ctx) { ... }` (everything from that line to the method's closing brace, just above the closing brace of the `ImperioControlPacket` record) with:
```java
        public static void handle(ImperioControlPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) {
                return; // a puppet or a thrall can't start or steer a channel of its own
            }
            // A running possession takes every packet from its own caster (input, heartbeat, release).
            if (com.hogwartsmagic.spell.mind.PossessionManager.handleCasterPacket(player, p)) {
                return;
            }
            if (!p.startChannel) {
                return; // input or a release with no session behind it: nothing to steer
            }
            ItemStack wand = player.getMainHandItem();
            if (!MagicWandItem.isAnyWand(wand) || MagicWandItem.getSelectedSpell(wand) != SpellType.IMPERIO) {
                return;
            }
            if (!com.hogwartsmagic.item.MagicBookItem.isSpellLearned(player, SpellType.IMPERIO)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.spell_not_learned"), true);
                return;
            }
            if (!com.hogwartsmagic.spell.mind.ImperioRules.isPossessionRank(
                    com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, SpellType.IMPERIO))) {
                return; // ranks I-III make a thrall when the beam lands; only a Master holds
            }
            if (com.hogwartsmagic.spell.combat.CombatEffects.isCastingBlocked(player)) {
                com.hogwartsmagic.spell.combat.CombatEffects.notifyCastBlocked(player);
                return;
            }
            if (!player.isCreative() && MagicWandItem.isOnCooldown(wand, SpellType.IMPERIO, player.level().getGameTime())) {
                return;
            }
            LivingEntity target = com.hogwartsmagic.event.ModEvents.consumeRecentImperioHitTarget(player);
            if (target == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.hogwartsmagic.possession.hit_first")
                        .withStyle(net.minecraft.ChatFormatting.GREEN), true);
                return;
            }
            com.hogwartsmagic.spell.mind.PossessionManager.startFromHold(player, wand, target);
        }
```

- [ ] **Step 2: Start from the hold, pay upkeep, cool down at the end.** In `PossessionManager.java`:

Add the imports `com.hogwartsmagic.event.ModEvents`, `com.hogwartsmagic.util.PlayerMagicData`, `net.minecraft.nbt.CompoundTag` and `net.minecraft.world.item.ItemStack`.

Directly above the line `    // ---- Input ---------------------------------------------------------------------------------` add:
```java
    /** The Master hold reached its threshold on a target the beam hit: check the start mana, then possess. */
    public static void startFromHold(ServerPlayer caster, ItemStack wand, LivingEntity target) {
        if (!caster.isCreative()) {
            CompoundTag modData = PlayerMagicData.getModData(caster);
            float mana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(caster);
            if (mana < ModEvents.getImperioStartCost(caster, wand)) {
                caster.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (start(caster, target, false)) {
            ModEvents.revealInvisibilityCloak(caster);
        }
    }

```
In `tickSession`, directly above the line `        drive(session, victim);` add:
```java
        if (!drainUpkeep(caster, session, now)) {
            end(session, EndReason.NO_MANA);
            return;
        }
```
Directly above the line `    // ---- Packets out ---------------------------------------------------------------------------` add:
```java
    /** The old channel's upkeep every half second; false when the caster can't pay. */
    private static boolean drainUpkeep(ServerPlayer caster, PossessionSession session, long now) {
        if (session.debug || caster.isCreative() || now - session.lastDrainAt < PossessionRules.UPKEEP_INTERVAL) {
            return true;
        }
        session.lastDrainAt = now;
        float cost = ModEvents.getImperioUpkeepCost(caster, caster.getMainHandItem());
        CompoundTag modData = PlayerMagicData.getModData(caster);
        float maxMana = PlayerMagicData.getMaxMana(caster);
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        if (mana < cost) {
            return false;
        }
        float next = mana - cost;
        modData.putFloat("Mana", next);
        PlayerMagicData.saveModData(caster, modData);
        if ((int) mana != (int) next) {
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(next, maxMana), PacketDistributor.PLAYER.with(caster));
        }
        return true;
    }

```
In `end`, replace:
```java
            caster.displayClientMessage(Component.translatable(reason.langKey()).withStyle(ChatFormatting.GREEN), true);
        }
```
with:
```java
            caster.displayClientMessage(Component.translatable(reason.langKey()).withStyle(ChatFormatting.GREEN), true);
            if (!session.debug) {
                ImperioThralls.startImperioCooldown(caster); // the cooldown, and the Imperio XP, start when it ends
            }
        }
```

- [ ] **Step 3: Remove the old channel from `ModEvents`.**
1. Replace the constant block
```java
    private static final String TAG_IMPERIO_CHANNELING = "hogwartsmagic:imperio_channeling";
    private static final String TAG_IMPERIO_TARGET = "hogwartsmagic:imperio_target";
    private static final String TAG_IMPERIO_RECENT_HIT_TARGET = "hogwartsmagic:imperio_recent_hit_target";
    private static final String TAG_IMPERIO_RECENT_HIT_EXPIRY = "hogwartsmagic:imperio_recent_hit_expiry";
    private static final String TAG_IMPERIO_LAST_SEEN = "hogwartsmagic:imperio_last_seen";
    private static final String TAG_IMPERIO_LAST_DRAIN = "hogwartsmagic:imperio_last_drain";
    private static final String TAG_IMPERIO_LAST_ATTACK = "hogwartsmagic:imperio_last_attack";
    private static final String TAG_IMPERIO_FORWARD = "hogwartsmagic:imperio_forward";
    private static final String TAG_IMPERIO_STRAFE = "hogwartsmagic:imperio_strafe";
    private static final String TAG_IMPERIO_JUMP = "hogwartsmagic:imperio_jump";
    private static final String TAG_IMPERIO_ATTACK = "hogwartsmagic:imperio_attack";
    private static final String TAG_IMPERIO_YAW = "hogwartsmagic:imperio_yaw";
    private static final String TAG_IMPERIO_PITCH = "hogwartsmagic:imperio_pitch";
    private static final int IMPERIO_UPKEEP_INTERVAL = 10;
    private static final int IMPERIO_HEARTBEAT_TIMEOUT = 4;
    private static final int IMPERIO_ATTACK_COOLDOWN = 10;
```
with:
```java
    private static final String TAG_IMPERIO_RECENT_HIT_TARGET = "hogwartsmagic:imperio_recent_hit_target";
    private static final String TAG_IMPERIO_RECENT_HIT_EXPIRY = "hogwartsmagic:imperio_recent_hit_expiry";
```
2. Delete the line `            tickImperioChannel(player, modData);` in the server player tick.
3. Delete the whole method that starts with `    private static void tickImperioChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {` (down to its closing brace, the line before `    private static void tickWingardiumChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {`).
4. Delete the whole method `    public static void stopImperioChannel(ServerPlayer player, ItemStack wand) {` (down to its closing brace, above `    public static void interruptActiveCasting(ServerPlayer player) {`).
5. In `interruptActiveCasting`, replace:
```java
        if (player.getPersistentData().getBoolean(TAG_IMPERIO_CHANNELING)) {
            stopImperioChannel(player, wand);
        }
```
with:
```java
        com.hogwartsmagic.spell.mind.PossessionManager.endByCaster(player,
                com.hogwartsmagic.spell.mind.PossessionManager.EndReason.INTERRUPTED);
```
6. Delete everything from the line `    public static void updateImperioInput(ServerPlayer player, float forward, float strafe, boolean jump, boolean attack, float yaw, float pitch) {` down to, but not including, the line `    public static float getImperioStartCost(Player player, ItemStack wand) {` (that removes `updateImperioInput`, `applyImperioControl`, `performImperioMobAttack`, `getImperioAttackDamage` and `findImperioVictim`; Task 6 rebuilds the attacks in `PossessedAttacks`).
7. Replace `    private static float getImperioUpkeepCost(Player player, ItemStack wand) {` with `    public static float getImperioUpkeepCost(Player player, ItemStack wand) {`.

Then run: `grep -rn "stopImperioChannel(\|updateImperioInput(\|TAG_IMPERIO_CHANNELING\|imperio_channeling" src/main/java`
Expected: no output. (If anything else still calls `stopImperioChannel`, replace that call with `com.hogwartsmagic.spell.mind.PossessionManager.endByCaster(player, com.hogwartsmagic.spell.mind.PossessionManager.EndReason.RELEASED)`.)

- [ ] **Step 4: The hold block stops streaming.** In `ClientModEvents.java`, in the Imperio hold block, replace:
```java
                            if (!imperioChannelActive && imperioHoldTicks >= IMPERIO_HOLD_THRESHOLD_TICKS) {
                                SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(true, false, forward, strafe, jumpDown, attackDown, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                                imperioChannelActive = true;
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            } else if (imperioChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, false, forward, strafe, jumpDown, attackDown, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                            }
```
with:
```java
                            if (!imperioChannelActive && imperioHoldTicks >= IMPERIO_HOLD_THRESHOLD_TICKS) {
                                SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(true, false, forward, strafe, jumpDown, attackDown, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                                imperioChannelActive = true;
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            }
                            // Once possessing, PossessionClient streams the input (raw keys and the virtual look).
```
The block still sends the start packet at the 40-tick threshold and the stop packet on release.

- [ ] **Step 5: String.** In `en_us.json`, directly after `  "commands.hogwartsmagic.imperio.released": "Released",` insert:
```json
  "gui.hogwartsmagic.possession.hit_first": "Hit a player or creature with Imperio, then keep holding",
```

- [ ] **Step 6: Build, test and boot**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessionManager`, `ModEvents` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make the Master Imperio hold start a possession and retire the old control channel" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Every end condition (body hit, deaths, logout, dimension, distance, interrupts) and the aftermath

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java`

**Interfaces:**
- Consumes: Tasks 2–3; `MindFx.resisted` (6a); `MagicWandItem.isAnyWand/getSelectedSpell` (existing).
- Produces:
  - `PossessionManager.isImmune(LivingEntity)` → `boolean` (3s after breaking free)
  - Event hooks on `PossessionManager`: `onLivingDamage` (4+ on the body: `BODY_HIT`), `onLivingDeath` (`DIED`), `onLogout` (`LOGGED_OUT`), `onTravel` (`DIMENSION`), `onServerStopping` (`INVALID`)

- [ ] **Step 1: Immunity, distance, the caster's wand, the aftermath.** In `PossessionManager.java`:

Add the imports `com.hogwartsmagic.item.MagicWandItem`, `net.minecraft.world.effect.MobEffects`, `net.minecraftforge.event.entity.EntityTravelToDimensionEvent`, `net.minecraftforge.event.entity.living.LivingDamageEvent`, `net.minecraftforge.event.entity.living.LivingDeathEvent`, `net.minecraftforge.event.entity.player.PlayerEvent` and `net.minecraftforge.event.server.ServerStoppingEvent`.

Directly after the line `    private static Holder<MobEffect> possessedHolder;` add:
```java
    /** Game time until which a victim who broke free shrugs off Imperio. */
    static final String TAG_IMMUNE_UNTIL = ExampleMod.MODID + ":imperio_immune_until";
```
Directly above the line `    @Nullable` that precedes `    private static LivingEntity living(@Nullable Entity entity) {` add:
```java
    /** Broke free less than 3s ago: Imperio (thrall or possession) slides off. */
    public static boolean isImmune(LivingEntity entity) {
        return entity.getPersistentData().getLong(TAG_IMMUNE_UNTIL) > entity.level().getGameTime();
    }

    private static boolean holdsImperio(ServerPlayer caster) {
        return MagicWandItem.isAnyWand(caster.getMainHandItem())
                && MagicWandItem.getSelectedSpell(caster.getMainHandItem()) == SpellType.IMPERIO;
    }

```
In `start`, directly after the block
```java
        if (isPossessing(caster) || isPuppet(caster) || isPossessing(victim) || isPuppet(victim) || ImperioThralls.isThrall(victim)) {
            MindFx.tell(caster, "possession.busy", victim.getDisplayName());
            return false;
        }
```
add:
```java
        if (isImmune(victim)) {
            MindFx.resisted(victim, caster);
            return false;
        }
```
In `tickSession`, directly above the line `        if (!drainUpkeep(caster, session, now)) {` add:
```java
        if (caster.level() != level) {
            end(session, EndReason.DIMENSION);
            return;
        }
        if (PossessionRules.tooFar(victim.distanceToSqr(caster))) {
            end(session, EndReason.TOO_FAR);
            return;
        }
        if (!session.debug && !holdsImperio(caster)) {
            end(session, EndReason.RELEASED); // switched wand or spell: same as letting go
            return;
        }
```
In `end`, replace:
```java
            if (!session.debug) {
                ImperioThralls.startImperioCooldown(caster); // the cooldown, and the Imperio XP, start when it ends
            }
        }
```
with:
```java
            if (!session.debug) {
                ImperioThralls.startImperioCooldown(caster); // the cooldown, and the Imperio XP, start when it ends
            }
            if (caster.isAlive() && reason != EndReason.LOGGED_OUT) {
                caster.addEffect(new MobEffectInstance(MobEffects.CONFUSION, PossessionRules.NAUSEA_TICKS, 0));
            }
        }
```
and replace:
```java
            victim.removeEffect(DisableSupport.holder(ExampleMod.POSSESSED));
```
with:
```java
            victim.removeEffect(DisableSupport.holder(ExampleMod.POSSESSED));
            if (reason == EndReason.BROKE_FREE) {
                victim.getPersistentData().putLong(TAG_IMMUNE_UNTIL, victim.level().getGameTime() + PossessionRules.IMMUNITY_TICKS);
            }
```

- [ ] **Step 2: The event-driven ends.** At the end of `PossessionManager` (directly above the class's final closing brace), add:
```java

    // ---- Event-driven ends ---------------------------------------------------------------------

    @Nullable
    private static PossessionSession sessionOf(UUID id) {
        PossessionSession session = BY_CASTER.get(id);
        return session != null ? session : BY_VICTIM.get(id);
    }

    /** A real blow to the frozen body (4+ after armour) throws the caster out: everyone nearby's counterplay. */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        PossessionSession session = BY_CASTER.get(event.getEntity().getUUID());
        if (session != null && PossessionRules.ejects(event.getAmount())) {
            end(session, EndReason.BODY_HIT);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        PossessionSession session = sessionOf(event.getEntity().getUUID());
        if (session != null) {
            end(session, EndReason.DIED);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PossessionSession session = sessionOf(event.getEntity().getUUID());
        if (session != null) {
            end(session, EndReason.LOGGED_OUT);
        }
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        PossessionSession session = sessionOf(event.getEntity().getUUID());
        if (session != null) {
            end(session, EndReason.DIMENSION);
        }
    }

    /** Before the worlds save, so nobody is written to disk rooted or possessed. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (PossessionSession session : new ArrayList<>(BY_CASTER.values())) {
            end(session, EndReason.INVALID);
        }
    }
```

- [ ] **Step 3: Immune and possessed targets resist Imperio.** In `ImperioSpell.java`, in `apply`, replace:
```java
        if (target.level().isClientSide()) {
            return;
        }
        DisableSupport.interrupt(target);
```
with:
```java
        if (target.level().isClientSide()) {
            return;
        }
        if (PossessionManager.isImmune(target) || PossessionManager.isPuppet(target)) {
            MindFx.resisted(target, caster);
            return;
        }
        DisableSupport.interrupt(target);
```

- [ ] **Step 4: Build, test and boot**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessionManager` or any `client` class.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/spell/mind/ImperioSpell.java
git commit -m "End possessions on body hits, deaths, logouts, dimension changes, distance and interrupts" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The victim's lock and resisting (mash SPACE after 8 seconds)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/MindControl.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`PossessionResistPacket`; casters refused in `CastSpellPacket.handle`)
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java` (resist windows)
- Modify: `src/main/java/com/hogwartsmagic/client/PossessionClient.java` (counting and reporting presses)
- Modify: `src/main/java/com/hogwartsmagic/client/MindControlOverlay.java` (the prompt)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: Tasks 1–4; 6a's `MindControl`, `ImperiusClient` (locks keys, screens and scroll whenever `MindControl.isControlled` is true).
- Produces:
  - `MindControl.isControlled(LivingEntity)` now also true for the `possessed` effect
  - `SpellPackets.PossessionResistPacket(int presses)` (C2S)
  - `PossessionManager.acceptResist(ServerPlayer sender, int presses)`

- [ ] **Step 1: Puppets are controlled; a possessing caster's own actions are refused.** In `MindControl.java`, replace:
```java
    /** Under someone else's control: an Imperio thrall. Phase 6b adds possession. Safe on both sides. */
    public static boolean isControlled(LivingEntity entity) {
        return CombatEffects.has(entity, ExampleMod.IMPERIUS);
    }
```
with:
```java
    /** Under someone else's control: an Imperio thrall or a possessed body. Safe on both sides. */
    public static boolean isControlled(LivingEntity entity) {
        return CombatEffects.has(entity, ExampleMod.IMPERIUS) || CombatEffects.has(entity, ExampleMod.POSSESSED);
    }
```
and replace:
```java
    private static boolean blocksOwnAction(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && !FORCED_ACTION.get() && isControlled(living);
    }
```
with:
```java
    /** A controlled body, or a caster whose own body stands in a possession trance. */
    private static boolean blocksOwnAction(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && !FORCED_ACTION.get()
                && (isControlled(living) || PossessionManager.isPossessing(living));
    }
```
In `SpellPackets.java`, `CastSpellPacket.handle`, replace:
```java
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) return;
            castAs(player, p);
```
with:
```java
            if (com.hogwartsmagic.spell.mind.MindControl.isControlled(player)) return;
            // A possessing caster's own body is in a trance; puppet casts go through PossessionCastPacket.
            if (com.hogwartsmagic.spell.mind.PossessionManager.isPossessing(player)) return;
            castAs(player, p);
```

- [ ] **Step 2: The resist packet.** In `SpellPackets.java`, at the end of `register()` (directly above the closing `    }` that comes right before `    public static void syncRelics(ServerPlayer player) {`), add:
```java
        CHANNEL.messageBuilder(PossessionResistPacket.class)
                .encoder((p, buf) -> buf.writeVarInt(p.presses))
                .decoder(buf -> new PossessionResistPacket(buf.readVarInt()))
                .consumerMainThread(PossessionResistPacket::handle)
                .add();
```
At the end of the class, directly above its final closing brace, add:
```java

    /** Client to server (a possessed player): SPACE presses since the last report. Capped and rate-limited server-side. */
    public record PossessionResistPacket(int presses) {
        public static void handle(PossessionResistPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.spell.mind.PossessionManager.acceptResist(player, p.presses);
            }
        }
    }
```

- [ ] **Step 3: Server-side windows.** In `PossessionManager.java`:

Directly above the line `    // ---- End -----------------------------------------------------------------------------------` add:
```java
    /**
     * A possessed player's report of SPACE presses. Only the session's own victim counts, at most one report
     * per 8 ticks, at most 12 presses per half-second window, and nothing before the 8-second mark.
     */
    public static void acceptResist(ServerPlayer sender, int presses) {
        PossessionSession session = BY_VICTIM.get(sender.getUUID());
        if (session == null || !session.victimIsPlayer) {
            return;
        }
        long now = sender.level().getGameTime();
        if (!PossessionRules.reportAllowed(now, session.lastResistReportAt)) {
            return;
        }
        session.lastResistReportAt = now;
        if (!PossessionRules.resistOpen(now - session.startedAt)) {
            return;
        }
        session.resistPresses = PossessionRules.clampPresses(session.resistPresses + PossessionRules.clampPresses(presses));
    }

    /** Closes each half-second window: 6+ presses in it is a 20% roll to break free. Returns true if it ended. */
    private static boolean tickResist(PossessionSession session, ServerPlayer caster, LivingEntity victim, long now, long elapsed) {
        int window = PossessionRules.resistWindow(elapsed);
        if (window < 0) {
            return false;
        }
        if (!session.resistAnnounced) {
            session.resistAnnounced = true;
            sendState(session, caster, victim, now); // the victim's HUD switches to "Mash SPACE"
        }
        if (window == session.resistWindow) {
            return false;
        }
        int presses = session.resistPresses;
        boolean evaluate = session.resistWindow >= 0;
        session.resistWindow = window;
        session.resistPresses = 0;
        if (evaluate && PossessionRules.breaksFree(presses, victim.getRandom().nextDouble())) {
            end(session, EndReason.BROKE_FREE);
            return true;
        }
        return false;
    }

```
In `tickSession`, directly above the line `        if (!drainUpkeep(caster, session, now)) {` add:
```java
        if (session.victimIsPlayer && tickResist(session, caster, victim, now, elapsed)) {
            return;
        }
```

- [ ] **Step 4: The victim's client counts and reports.** In `PossessionClient.java`:

Directly after `    private static int puppetAge = Integer.MAX_VALUE / 2;` add:
```java
    private static int resistPresses;
    private static int resistTimer;
```
In `reset()`, replace:
```java
        puppetAge = Integer.MAX_VALUE / 2;
    }
```
with:
```java
        puppetAge = Integer.MAX_VALUE / 2;
        resistPresses = 0;
        resistTimer = 0;
    }
```
In `onClientTick`, replace:
```java
            if (role != PossessionRules.ROLE_NONE) {
                drainBodyKeys(minecraft);
            }
            return;
```
with:
```java
            if (role != PossessionRules.ROLE_NONE) {
                drainBodyKeys(minecraft);
            }
            if (role == PossessionRules.ROLE_VICTIM) {
                // SPACE presses (not holds): each press is one click. Before the 8s mark they count for nothing.
                while (minecraft.options.keyJump.consumeClick()) {
                    if (resistOpen) {
                        resistPresses++;
                    }
                }
            }
            return;
```
and replace:
```java
        } else {
            puppetAge++;
        }
    }
```
with:
```java
        } else {
            puppetAge++;
            reportResist();
        }
    }

    /** Twice a second while resisting is open: send the presses since the last report. */
    private static void reportResist() {
        if (++resistTimer < PossessionRules.RESIST_WINDOW_TICKS) {
            return;
        }
        resistTimer = 0;
        if (resistOpen && resistPresses > 0) {
            SpellPackets.CHANNEL.send(new SpellPackets.PossessionResistPacket(resistPresses), PacketDistributor.SERVER.noArg());
        }
        resistPresses = 0;
    }
```

- [ ] **Step 5: The prompt.** In `MindControlOverlay.java`, replace:
```java
            vignette(graphics, IMPERIUS_GREEN, (int) (170 * pulse));
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                    graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
        }
    }
```
with:
```java
            vignette(graphics, IMPERIUS_GREEN, (int) (170 * pulse));
            if (PossessionClient.isVictim()) {
                renderVictimPrompt(graphics, minecraft, player.tickCount);
            } else {
                graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                        graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
            }
        }
    }

    /** A possessed player: who has them, and from 8s on a flashing "Mash SPACE to resist!". */
    private static void renderVictimPrompt(GuiGraphics graphics, Minecraft minecraft, int tick) {
        int cx = graphics.guiWidth() / 2;
        graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.victim"),
                cx, 14, 0xFF9CFF9C);
        if (PossessionClient.resistOpen()) {
            int colour = (tick / 5) % 2 == 0 ? 0xFFFFFF55 : 0xFFFFAA00;
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.resist"),
                    cx, graphics.guiHeight() / 2 + 24, colour);
        }
    }
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.possession.hit_first": "Hit a player or creature with Imperio, then keep holding",` insert:
```json
  "gui.hogwartsmagic.possession.victim": "Someone else is moving your body",
  "gui.hogwartsmagic.possession.resist": "Mash SPACE to resist!",
```

- [ ] **Step 7: Build, test and boot**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessionManager`, `MindControl` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/MindControl.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/client/PossessionClient.java src/main/java/com/hogwartsmagic/client/MindControlOverlay.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Lock a possessed player out of their own controls and let them mash space to resist" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The possessed body's attack (player melee, mob melee and ranged, boss specials, wand users)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/mind/PossessedAttacks.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java` (`tickSession`)
- Modify: `src/main/java/com/hogwartsmagic/entity/HungarianHorntailEntity.java` (`possessedAttack`)
- Modify: `src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java` (`possessedAttack`)

**Interfaces:**
- Consumes: Tasks 2–5; 6a's `MindControl.forcedAttack`; `SpellBeamEntity(Level, LivingEntity, SpellType)` and `shootFromRotation` (existing); `HungarianHorntailEntity.canStartAction()/beginBreath()`, `BasiliskEntity.getGazeOrigin()`, its private `spitCooldown`, `SPIT_COOLDOWN` and `playActionSound` (existing).
- Produces:
  - `PossessedAttacks.attack(LivingEntity puppet, ServerPlayer caster)` → `int` (ticks before the next attack; package-private)
  - `HungarianHorntailEntity.possessedAttack()` (Ironbelly inherits it), `BasiliskEntity.possessedAttack()`

- [ ] **Step 1: Create `PossessedAttacks`**

`src/main/java/com/hogwartsmagic/spell/mind/PossessedAttacks.java`:
```java
package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.entity.BasiliskEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The attack button of a possessed body, through that body's own attack: a player swings their held item
 * (Player.attack: its damage, enchantments and cooldown), a mob bites with doHurtTarget or fires its ranged
 * attack, a creeper lights its fuse, the Horntail (and Ironbelly) breathes fire along the look, the Basilisk
 * spits venom, and wand users (Voldemort, Death Eaters, Students) fire one of their spells. Aim is the
 * body's own look (the caster's virtual look, applied by PossessionManager.drive).
 */
final class PossessedAttacks {
    private static final double PLAYER_REACH = 3.5;
    private static final double RANGED_REACH = 24.0;
    private static final float FALLBACK_DAMAGE = 2.0F;
    private static final SpellType[] VOLDEMORT_SPELLS = {
            SpellType.STUPEFY, SpellType.SECTUMSEMPRA, SpellType.CRUCIO, SpellType.BOMBARDA};
    private static final SpellType[] WAND_SPELLS = {SpellType.STUPEFY, SpellType.EXPELLIARMUS};

    private PossessedAttacks() {
    }

    /** @return ticks until the next attack may fire */
    static int attack(LivingEntity puppet, ServerPlayer caster) {
        if (puppet instanceof ServerPlayer victim) {
            LivingEntity target = aimed(puppet, PLAYER_REACH, caster);
            if (target != null) {
                MindControl.forcedAttack(victim, target);
            } else {
                victim.swing(InteractionHand.MAIN_HAND, true);
            }
            return PossessionRules.ATTACK_COOLDOWN;
        }
        if (!(puppet instanceof Mob mob)) {
            return PossessionRules.ATTACK_COOLDOWN;
        }
        if (mob instanceof HungarianHorntailEntity horntail) {
            horntail.possessedAttack();
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof BasiliskEntity basilisk) {
            basilisk.possessedAttack();
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof VoldemortEntity) {
            fireBeam(mob, VOLDEMORT_SPELLS[mob.getRandom().nextInt(VOLDEMORT_SPELLS.length)]);
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof WizardEntity || mob instanceof StudentEntity) {
            fireBeam(mob, WAND_SPELLS[mob.getRandom().nextInt(WAND_SPELLS.length)]);
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof Creeper creeper) {
            creeper.ignite();
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        if (mob instanceof RangedAttackMob ranged) {
            LivingEntity target = aimed(mob, RANGED_REACH, caster);
            mob.swing(InteractionHand.MAIN_HAND);
            if (target != null) {
                ranged.performRangedAttack(target, 1.0F);
            }
            return PossessionRules.SPECIAL_ATTACK_COOLDOWN;
        }
        LivingEntity target = aimed(mob, mob.getBbWidth() * 2.0 + 1.5, caster);
        mob.swing(InteractionHand.MAIN_HAND);
        if (target != null) {
            melee(mob, target);
        }
        return PossessionRules.ATTACK_COOLDOWN;
    }

    /** The first living thing along the body's look within {@code reach}, stopping at walls; never the caster's body. */
    @Nullable
    private static LivingEntity aimed(LivingEntity puppet, double reach, ServerPlayer caster) {
        Vec3 start = puppet.getEyePosition();
        Vec3 end = start.add(puppet.getLookAngle().scale(reach));
        HitResult wall = puppet.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, puppet));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB box = puppet.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(puppet.level(), puppet, start, end, box,
                entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator()
                        && entity != caster && entity != puppet);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static void melee(Mob mob, LivingEntity target) {
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            target.hurt(mob.damageSources().mobAttack(mob), FALLBACK_DAMAGE); // passive mobs have no attack of their own
            return;
        }
        try {
            mob.doHurtTarget(target);
        } catch (IllegalArgumentException ex) {
            // Some attack paths still expect the legacy attack-damage id in 1.21 (seen with the old Imperio channel).
            target.hurt(mob.damageSources().mobAttack(mob), FALLBACK_DAMAGE);
        }
    }

    private static void fireBeam(Mob caster, SpellType spell) {
        SpellBeamEntity beam = new SpellBeamEntity(caster.level(), caster, spell);
        beam.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0F, 1.75F, 0.5F);
        caster.level().addFreshEntity(beam);
        caster.swing(InteractionHand.MAIN_HAND);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), spell.getSoundEvent(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
```

- [ ] **Step 2: Attack from the session.** In `PossessionManager.java`, `tickSession`, directly after the line `        drive(session, victim);` add:
```java
        if (session.attack && now - session.lastAttackAt >= session.attackCooldown) {
            session.lastAttackAt = now;
            session.attackCooldown = PossessedAttacks.attack(victim, caster);
        }
```

- [ ] **Step 3: The Horntail breathes fire on command.** In `HungarianHorntailEntity.java`, directly after the line
```java
    void beginFlight() { beginTakeoff(); }
```
add:
```java

    /**
     * Imperio possession (Spell Mastery Phase 6b): breathe fire along the current look when a move can start.
     * The possessed dragon has no target, so the windup aims along getLookAngle(), which the caster steers.
     */
    public void possessedAttack() {
        if (canStartAction()) {
            beginBreath();
        }
    }
```
(The attack state machine runs in `tick()`, so it keeps working while the possessed dragon's AI step is paused.)

- [ ] **Step 4: The Basilisk spits on command.** In `BasiliskEntity.java`, directly above the line
```java
    /** Ranged venom: an arcing glob that leaves a lingering pool where it lands. */
```
add:
```java
    /**
     * Imperio possession (Spell Mastery Phase 6b): spit venom straight along the current look, on the
     * Basilisk's own spit cooldown (counted down in tick(), so it runs while the possessed AI is paused).
     */
    public void possessedAttack() {
        if (!(this.level() instanceof ServerLevel) || this.spitCooldown > 0) {
            return;
        }
        Vec3 from = getGazeOrigin();
        Vec3 look = this.getLookAngle();
        BasiliskVenomEntity venom = new BasiliskVenomEntity(this.level(), this);
        venom.setPos(from.x, from.y, from.z);
        venom.shoot(look.x, look.y + 0.12D, look.z, 1.05F, 1.0F);
        this.level().addFreshEntity(venom);
        playActionSound(ModSounds.BASILISK_SPIT.get(), 1.3F, 1.0F);
        this.spitCooldown = SPIT_COOLDOWN;
    }

```

- [ ] **Step 5: Build, test and boot**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessedAttacks` or any `client` class.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/mind/PossessedAttacks.java src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/entity/HungarianHorntailEntity.java src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java
git commit -m "Let a possessed body attack with its own attack, from a player swing to dragon fire" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The puppet's hotbar (scroll it, see it, see their mana)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`PuppetSnapshotPacket`, `PossessionHotbarPacket`)
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java` (snapshot, hotbar)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`
- Modify: `src/main/java/com/hogwartsmagic/client/PossessionClient.java` (snapshot, hotbar keys, scroll)
- Modify: `src/main/java/com/hogwartsmagic/client/MindControlOverlay.java` (caster HUD)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: Tasks 2–6; `MagicBookItem.isSpellLearned`, `MagicWandItem.isAnyWand/getSpellCooldown/getSpellSlot/SPELL_SLOT_COUNT`, `PlayerMagicData.getModData/getMaxMana` (existing); `SpellMasteryData.getRank` (Phase 1).
- Produces:
  - `SpellPackets.PuppetSnapshotPacket(int victimId, String victimName, boolean holdsWand, int selectedSlot, List<String> hotbarIds, List<Integer> hotbarCounts, float mana, float maxMana, List<String> learnedIds, List<Integer> learnedRanks, List<Integer> cooldownTicks, List<String> wandSlots)` (S2C, every second and after every change)
  - `SpellPackets.PossessionHotbarPacket(int slot)` (C2S)
  - `PossessionManager.selectHotbar(ServerPlayer sender, int slot)`
  - `PossessionClient.acceptSnapshot(PuppetSnapshotPacket)`, `PossessionClient.snapshot()` → `@Nullable PuppetSnapshotPacket`, `PossessionClient.displayedSlot()` → `int`, `PossessionClient.stackFor(String id, int count)` → `ItemStack`

- [ ] **Step 1: The two packets.** In `SpellPackets.java`, at the end of `register()` (directly above the closing `    }` that comes right before `    public static void syncRelics(ServerPlayer player) {`), add:
```java
        CHANNEL.messageBuilder(PuppetSnapshotPacket.class)
                .encoder((p, buf) -> {
                    buf.writeVarInt(p.victimId);
                    buf.writeUtf(p.victimName, 64);
                    buf.writeBoolean(p.holdsWand);
                    buf.writeVarInt(p.selectedSlot);
                    buf.writeVarInt(p.hotbarIds.size());
                    for (int i = 0; i < p.hotbarIds.size(); i++) {
                        buf.writeUtf(p.hotbarIds.get(i), 128);
                        buf.writeVarInt(p.hotbarCounts.get(i));
                    }
                    buf.writeFloat(p.mana);
                    buf.writeFloat(p.maxMana);
                    buf.writeVarInt(p.learnedIds.size());
                    for (int i = 0; i < p.learnedIds.size(); i++) {
                        buf.writeUtf(p.learnedIds.get(i), 64);
                        buf.writeVarInt(p.learnedRanks.get(i));
                        buf.writeVarInt(p.cooldownTicks.get(i));
                    }
                    buf.writeVarInt(p.wandSlots.size());
                    for (String id : p.wandSlots) {
                        buf.writeUtf(id, 64);
                    }
                })
                .decoder(buf -> {
                    int victimId = buf.readVarInt();
                    String victimName = buf.readUtf(64);
                    boolean holdsWand = buf.readBoolean();
                    int selectedSlot = buf.readVarInt();
                    int hotbarSize = clampDecodedSize(buf.readVarInt(), 9);
                    List<String> hotbarIds = new ArrayList<>(hotbarSize);
                    List<Integer> hotbarCounts = new ArrayList<>(hotbarSize);
                    for (int i = 0; i < hotbarSize; i++) {
                        hotbarIds.add(buf.readUtf(128));
                        hotbarCounts.add(buf.readVarInt());
                    }
                    float mana = buf.readFloat();
                    float maxMana = buf.readFloat();
                    int learnedSize = clampDecodedSize(buf.readVarInt(), SpellType.values().length);
                    List<String> learnedIds = new ArrayList<>(learnedSize);
                    List<Integer> learnedRanks = new ArrayList<>(learnedSize);
                    List<Integer> cooldownTicks = new ArrayList<>(learnedSize);
                    for (int i = 0; i < learnedSize; i++) {
                        learnedIds.add(buf.readUtf(64));
                        learnedRanks.add(buf.readVarInt());
                        cooldownTicks.add(buf.readVarInt());
                    }
                    int slotCount = clampDecodedSize(buf.readVarInt(), MagicWandItem.SPELL_SLOT_COUNT);
                    List<String> wandSlots = new ArrayList<>(slotCount);
                    for (int i = 0; i < slotCount; i++) {
                        wandSlots.add(buf.readUtf(64));
                    }
                    return new PuppetSnapshotPacket(victimId, victimName, holdsWand, selectedSlot, hotbarIds, hotbarCounts,
                            mana, maxMana, learnedIds, learnedRanks, cooldownTicks, wandSlots);
                })
                .consumerMainThread(PuppetSnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(PossessionHotbarPacket.class)
                .encoder((p, buf) -> buf.writeVarInt(p.slot))
                .decoder(buf -> new PossessionHotbarPacket(buf.readVarInt()))
                .consumerMainThread(PossessionHotbarPacket::handle)
                .add();
```
At the end of the class, directly above its final closing brace, add:
```java

    /**
     * Server to client (the possessing caster): what the puppet carries and knows. Item ids only (icons, no
     * components); cooldowns are ticks left on the puppet's held wand when it was sent.
     */
    public record PuppetSnapshotPacket(int victimId, String victimName, boolean holdsWand, int selectedSlot,
                                       List<String> hotbarIds, List<Integer> hotbarCounts, float mana, float maxMana,
                                       List<String> learnedIds, List<Integer> learnedRanks, List<Integer> cooldownTicks,
                                       List<String> wandSlots) {
        public static void handle(PuppetSnapshotPacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                ctx.setPacketHandled(true);
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("puppetSnapshot", p));
        }
    }

    /** Client to server (the possessing caster): select this slot of the puppet's hotbar. */
    public record PossessionHotbarPacket(int slot) {
        public static void handle(PossessionHotbarPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.spell.mind.PossessionManager.selectHotbar(player, p.slot);
            }
        }
    }
```

- [ ] **Step 2: Server side.** In `PossessionManager.java`:

Add the imports `com.hogwartsmagic.item.MagicBookItem`, `com.hogwartsmagic.spell.mastery.SpellMasteryData`, `net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket` and `java.util.List`.

In `start`, replace:
```java
        sendState(session, caster, victim, now);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SpellType.IMPERIO.getSoundEvent(),
```
with:
```java
        sendState(session, caster, victim, now);
        if (victim instanceof ServerPlayer target) {
            sendSnapshot(session, caster, target, now);
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SpellType.IMPERIO.getSoundEvent(),
```
In `tickSession`, directly above the line `        if (now - session.lastStateAt >= PossessionRules.STATE_KEEPALIVE_TICKS) {` add:
```java
        if (victim instanceof ServerPlayer victimPlayer) {
            enforceHotbar(session, victimPlayer);
            if (now - session.lastSnapshotAt >= PossessionRules.SNAPSHOT_INTERVAL_TICKS) {
                sendSnapshot(session, caster, victimPlayer, now);
            }
        }
```
Directly above the line `    // ---- End -----------------------------------------------------------------------------------` add:
```java
    /** The caster scrolled or pressed a number: only their own session, a player puppet, a real slot, 10 a second. */
    public static void selectHotbar(ServerPlayer sender, int slot) {
        PossessionSession session = BY_CASTER.get(sender.getUUID());
        if (session == null || !session.victimIsPlayer || !PossessionRules.validHotbarSlot(slot)) {
            return;
        }
        long now = sender.level().getGameTime();
        if (!session.hotbarRate.tryAcquire(now)) {
            return;
        }
        ServerPlayer victim = sender.server.getPlayerList().getPlayer(session.victimId);
        if (victim == null) {
            return;
        }
        session.forcedSlot = slot;
        enforceHotbar(session, victim);
        sendSnapshot(session, sender, victim, now);
    }

    /** The puppet can't change slot on its own: put the caster's choice back every tick. */
    private static void enforceHotbar(PossessionSession session, ServerPlayer victim) {
        if (session.forcedSlot >= 0 && victim.getInventory().selected != session.forcedSlot) {
            victim.getInventory().selected = session.forcedSlot;
            victim.connection.send(new ClientboundSetCarriedItemPacket(session.forcedSlot));
        }
    }

    private static void sendSnapshot(PossessionSession session, ServerPlayer caster, ServerPlayer victim, long now) {
        session.lastSnapshotAt = now;
        ItemStack wand = victim.getMainHandItem();
        boolean holdsWand = MagicWandItem.isAnyWand(wand);
        List<String> hotbarIds = new ArrayList<>(9);
        List<Integer> hotbarCounts = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = victim.getInventory().getItem(slot);
            hotbarIds.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            hotbarCounts.add(stack.getCount());
        }
        CompoundTag modData = PlayerMagicData.getModData(victim);
        float maxMana = PlayerMagicData.getMaxMana(victim);
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        List<String> learned = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        List<Integer> cooldowns = new ArrayList<>();
        for (SpellType spell : SpellType.values()) {
            if (!MagicBookItem.isSpellLearned(victim, spell)) {
                continue;
            }
            learned.add(spell.getId());
            ranks.add(SpellMasteryData.getRank(victim, spell));
            long left = holdsWand ? MagicWandItem.getSpellCooldown(wand, spell) - now : 0L;
            cooldowns.add((int) Math.max(0L, Math.min(Integer.MAX_VALUE, left)));
        }
        List<String> wandSlots = new ArrayList<>(MagicWandItem.SPELL_SLOT_COUNT);
        for (int slot = 0; slot < MagicWandItem.SPELL_SLOT_COUNT; slot++) {
            SpellType assigned = holdsWand ? MagicWandItem.getSpellSlot(wand, slot) : null;
            wandSlots.add(assigned == null ? "" : assigned.getId());
        }
        SpellPackets.CHANNEL.send(new SpellPackets.PuppetSnapshotPacket(victim.getId(), victim.getName().getString(), holdsWand,
                victim.getInventory().selected, hotbarIds, hotbarCounts, mana, maxMana, learned, ranks, cooldowns, wandSlots),
                PacketDistributor.PLAYER.with(caster));
    }

```

- [ ] **Step 3: Dispatch on the client.** In `ClientRuntimeBridge.java`, `handlePacket`, directly above the `default ->` line add:
```java
            case "puppetSnapshot" -> PossessionClient.acceptSnapshot((SpellPackets.PuppetSnapshotPacket) payload);
```

- [ ] **Step 4: The caster's hotbar keys and scroll go to the puppet.** In `PossessionClient.java`:

Add the imports `net.minecraft.core.registries.BuiltInRegistries`, `net.minecraft.resources.ResourceLocation` and `net.minecraft.world.item.ItemStack`.

Directly after `    private static int resistTimer;` add:
```java
    @Nullable
    private static SpellPackets.PuppetSnapshotPacket snapshot;
    private static int displayedSlot;
```
In `reset()`, replace:
```java
        resistPresses = 0;
        resistTimer = 0;
    }
```
with:
```java
        resistPresses = 0;
        resistTimer = 0;
        snapshot = null;
        displayedSlot = 0;
    }
```
Directly above the line `    // ---- Queries -------------------------------------------------------------------------------` add:
```java
    public static void acceptSnapshot(SpellPackets.PuppetSnapshotPacket packet) {
        if (role != PossessionRules.ROLE_CASTER || packet.victimId() != otherId) {
            return;
        }
        snapshot = packet;
        displayedSlot = packet.selectedSlot();
    }

```
Directly above the line `    // ---- State ---------------------------------------------------------------------------------` add:
```java
    @Nullable
    public static SpellPackets.PuppetSnapshotPacket snapshot() {
        return snapshot;
    }

    public static int displayedSlot() {
        return displayedSlot;
    }

    /** An icon for a hotbar entry of the snapshot (item id only). */
    public static ItemStack stackFor(String id, int count) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(key).map(item -> new ItemStack(item, Math.max(1, count))).orElse(ItemStack.EMPTY);
    }

    private static void selectPuppetSlot(int slot) {
        if (snapshot == null || !PossessionRules.validHotbarSlot(slot)) {
            return;
        }
        displayedSlot = slot; // shown at once; the next snapshot confirms it
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionHotbarPacket(slot), PacketDistributor.SERVER.noArg());
    }

```
In `drainBodyKeys`, replace:
```java
        for (KeyMapping hotbar : minecraft.options.keyHotbarSlots) {
            drain(hotbar);
        }
    }
```
with:
```java
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            while (minecraft.options.keyHotbarSlots[slot].consumeClick()) {
                if (role == PossessionRules.ROLE_CASTER) {
                    selectPuppetSlot(slot); // the caster picks the puppet's slot, never their own
                }
            }
        }
    }

    /** The caster's scroll wheel turns the puppet's hotbar (same direction as vanilla scrolling). */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (role != PossessionRules.ROLE_CASTER || Minecraft.getInstance().screen != null) {
            return;
        }
        event.setCanceled(true);
        int step = (int) Math.signum(event.getDeltaY());
        if (step != 0) {
            selectPuppetSlot(Math.floorMod(displayedSlot - step, 9));
        }
    }
```

- [ ] **Step 5: The caster's HUD.** In `MindControlOverlay.java`:

Add the imports `com.hogwartsmagic.network.SpellPackets`, `net.minecraft.resources.ResourceLocation`, `net.minecraft.world.entity.Entity` and `net.minecraft.world.item.ItemStack`.

Directly after `    private static final int IMPERIUS_GREEN = 0x2FAF3F;` add:
```java
    private static final ResourceLocation HOTBAR_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
```
In `render`, replace:
```java
                graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                        graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
            }
        }
    }
```
with:
```java
                graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".imperio.victim"),
                        graphics.guiWidth() / 2, 14, 0xFF9CFF9C);
            }
        }
        if (PossessionClient.isCaster()) {
            renderCasterHud(graphics, minecraft);
        }
    }

    /** Whose eyes you're behind and for how long; for a player puppet, their hotbar (over yours) and mana. */
    private static void renderCasterHud(GuiGraphics graphics, Minecraft minecraft) {
        int cx = graphics.guiWidth() / 2;
        Entity victim = minecraft.level == null ? null : minecraft.level.getEntity(PossessionClient.otherId());
        Component name = victim != null ? victim.getDisplayName() : Component.literal("?");
        int seconds = (PossessionClient.ticksLeft() + 19) / 20;
        graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.caster_header",
                name, seconds), cx, 14, 0xFF9CFF9C);
        SpellPackets.PuppetSnapshotPacket snapshot = PossessionClient.snapshot();
        if (snapshot == null) {
            return;
        }
        int x = cx - 91;
        int y = graphics.guiHeight() - 22;
        graphics.fill(x, y, x + 182, y + 22, 0xFF000000); // hide the caster's own hotbar underneath
        graphics.blitSprite(HOTBAR_SPRITE, x, y, 182, 22);
        graphics.blitSprite(HOTBAR_SELECTION_SPRITE, x - 1 + PossessionClient.displayedSlot() * 20, y - 1, 24, 23);
        for (int slot = 0; slot < snapshot.hotbarIds().size() && slot < 9; slot++) {
            ItemStack stack = PossessionClient.stackFor(snapshot.hotbarIds().get(slot), snapshot.hotbarCounts().get(slot));
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 3 + slot * 20, y + 3);
                graphics.renderItemDecorations(minecraft.font, stack, x + 3 + slot * 20, y + 3);
            }
        }
        float maxMana = snapshot.maxMana() <= 0.0F ? 1.0F : snapshot.maxMana();
        int barX = graphics.guiWidth() - 120;
        int barY = graphics.guiHeight() - 58;
        int fill = Math.max(0, Math.min(100, (int) (snapshot.mana() / maxMana * 100)));
        graphics.fill(barX, barY, barX + 100, barY + 8, 0x88000000);
        graphics.fill(barX, barY, barX + fill, barY + 8, 0xFF3FD06A);
        graphics.drawString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".puppet.mana",
                (int) snapshot.mana(), (int) snapshot.maxMana()), barX, barY - 10, 0xFFB8F5B8, true);
        if (snapshot.holdsWand()) {
            graphics.drawCenteredString(minecraft.font, Component.translatable("gui." + ExampleMod.MODID + ".possession.wheel_hint"),
                    cx, graphics.guiHeight() - 48, 0xFFB8F5B8);
        }
    }
```

- [ ] **Step 6: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.possession.resist": "Mash SPACE to resist!",` insert:
```json
  "gui.hogwartsmagic.possession.caster_header": "Possessing %s: %ss",
  "gui.hogwartsmagic.possession.wheel_hint": "Spell wheel key: their spells",
  "gui.hogwartsmagic.puppet.mana": "Puppet mana %s / %s",
```

- [ ] **Step 7: Build, test and boot**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessionManager`, `PossessionClient`, `MindControlOverlay` or any other `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/PossessionClient.java src/main/java/com/hogwartsmagic/client/MindControlOverlay.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Hand a possessing caster the puppet hotbar and mana" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Puppet spells (their wand, their spells, cast as them)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`PossessionCastPacket`)
- Modify: `src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java` (`castAsPuppet`)
- Create: `src/main/java/com/hogwartsmagic/client/PuppetSpellWheelScreen.java`
- Modify: `src/main/java/com/hogwartsmagic/client/PossessionClient.java` (wheel, slot keys, cast)
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` (wheel key, spell-slot keys, the hold while the wheel is open)
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` (`eligible`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: Tasks 2–7; 6a's `CastSpellPacket.castAs`; `MagicWandItem.setSelectedSpell` (existing); `MasteryMath.roman(int)` (Phase 1).
- Produces:
  - `SpellPackets.PossessionCastPacket(String spellId)` (C2S)
  - `PossessionManager.castAsPuppet(ServerPlayer caster, String spellId)`
  - `PossessionClient.openPuppetWheel(Minecraft)`, `PossessionClient.handlePuppetSlotKeys(KeyMapping[])`, `PossessionClient.castPuppetSpell(String)`
  - `new PuppetSpellWheelScreen(SpellPackets.PuppetSnapshotPacket)`, `PuppetSpellWheelScreen.confirmSelectionAndClose()`

- [ ] **Step 1: The cast packet.** In `SpellPackets.java`, at the end of `register()` (directly above the closing `    }` that comes right before `    public static void syncRelics(ServerPlayer player) {`), add:
```java
        CHANNEL.messageBuilder(PossessionCastPacket.class)
                .encoder((p, buf) -> buf.writeUtf(p.spellId, 64))
                .decoder(buf -> new PossessionCastPacket(buf.readUtf(64)))
                .consumerMainThread(PossessionCastPacket::handle)
                .add();
```
At the end of the class, directly above its final closing brace, add:
```java

    /** Client to server (the possessing caster): cast this spell as the puppet. Fully re-validated as the puppet. */
    public record PossessionCastPacket(String spellId) {
        public static void handle(PossessionCastPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.spell.mind.PossessionManager.castAsPuppet(player, p.spellId);
            }
        }
    }
```

- [ ] **Step 2: Cast as the puppet.** In `PossessionManager.java`, directly above the line `    // ---- End -----------------------------------------------------------------------------------` add:
```java
    /**
     * Cast {@code spellId} as the possessed player, through the normal cast path run as them: their learned
     * spells, mastery ranks, mana, cooldowns, stun and silence all apply exactly as if they had cast it.
     * Only the session's own caster, a player puppet holding a wand, 4 casts a second, never Imperio or Apparition.
     */
    public static void castAsPuppet(ServerPlayer caster, String spellId) {
        PossessionSession session = BY_CASTER.get(caster.getUUID());
        if (session == null || !session.victimIsPlayer) {
            return;
        }
        long now = caster.level().getGameTime();
        if (!session.castRate.tryAcquire(now)) {
            return;
        }
        if (PossessionRules.isPuppetBlocked(spellId)) {
            MindFx.tell(caster, "puppet.blocked");
            return;
        }
        SpellType spell = spellById(spellId);
        ServerPlayer victim = caster.server.getPlayerList().getPlayer(session.victimId);
        if (spell == null || victim == null || !victim.isAlive()) {
            return;
        }
        ItemStack wand = victim.getMainHandItem();
        if (!MagicWandItem.isAnyWand(wand)) {
            MindFx.tell(caster, "puppet.no_wand");
            return;
        }
        if (!MagicBookItem.isSpellLearned(victim, spell)) {
            return;
        }
        victim.setYRot(session.yaw);
        victim.setYHeadRot(session.yaw);
        victim.setXRot(session.pitch);
        MagicWandItem.setSelectedSpell(wand, spell);
        SpellPackets.CastSpellPacket.castAs(victim, new SpellPackets.CastSpellPacket(false, false, false, false));
        sendSnapshot(session, caster, victim, now);
    }

    /** Exact id match (SpellType.fromId falls back to Wingardium on a bad id, which a packet must never get). */
    @Nullable
    private static SpellType spellById(String id) {
        for (SpellType spell : SpellType.values()) {
            if (spell.getId().equals(id)) {
                return spell;
            }
        }
        return null;
    }

```

- [ ] **Step 3: No mastery XP for a puppet.** In `MasteryAwards.java`, replace:
```java
    private static boolean eligible(ServerPlayer caster) {
        return !caster.isCreative() && !caster.isSpectator();
    }
```
with:
```java
    private static boolean eligible(ServerPlayer caster) {
        // A possessed player's forced casts earn them nothing (Imperio Master, Spell Mastery Phase 6b).
        return !caster.isCreative() && !caster.isSpectator()
                && !com.hogwartsmagic.spell.mind.PossessionManager.isPuppet(caster);
    }
```

- [ ] **Step 4: Create `PuppetSpellWheelScreen`**

`src/main/java/com/hogwartsmagic/client/PuppetSpellWheelScreen.java`:
```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mastery.MasteryMath;
import com.hogwartsmagic.spell.mind.PossessionRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The spell wheel while possessing a player who holds their wand: the puppet's own wand slots, gaps filled
 * with their other learned spells, at their ranks and cooldowns, under a "Puppet" header. Hold the wheel key,
 * release over a spell (or click it) to cast it as them. Imperio and Apparition show greyed and can't be cast.
 * Draws itself (no widgets), so it never calls super.render.
 */
public class PuppetSpellWheelScreen extends Screen {
    private static final int SLOT_COUNT = 9;
    private static final double START_ANGLE = -Math.PI / 2.0;

    private final SpellPackets.PuppetSnapshotPacket snapshot;
    private final List<String> slots = new ArrayList<>(SLOT_COUNT);
    private final Map<String, Integer> ranks = new HashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private int hoveredSlot = -1;

    public PuppetSpellWheelScreen(SpellPackets.PuppetSnapshotPacket snapshot) {
        super(Component.translatable("gui." + ExampleMod.MODID + ".puppet.title"));
        this.snapshot = snapshot;
        for (int i = 0; i < snapshot.learnedIds().size(); i++) {
            this.ranks.put(snapshot.learnedIds().get(i), snapshot.learnedRanks().get(i));
            this.cooldowns.put(snapshot.learnedIds().get(i), snapshot.cooldownTicks().get(i));
        }
        for (String id : snapshot.wandSlots()) {
            this.slots.add(id == null || !this.ranks.containsKey(id) ? "" : id);
        }
        while (this.slots.size() < SLOT_COUNT) {
            this.slots.add("");
        }
        for (String id : snapshot.learnedIds()) {
            int empty = this.slots.indexOf("");
            if (empty < 0) {
                break;
            }
            if (!this.slots.contains(id) && !PossessionRules.isPuppetBlocked(id)) {
                this.slots.set(empty, id);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = Math.max(80, Math.min(this.width, this.height) / 5);
        this.hoveredSlot = slotAt(mouseX, mouseY, cx, cy, radius);
        graphics.drawCenteredString(this.font, Component.translatable("gui." + ExampleMod.MODID + ".puppet.header",
                this.snapshot.victimName()), cx, cy - radius - 40, 0xFF9CFF9C);
        for (int i = 0; i < SLOT_COUNT; i++) {
            double angle = START_ANGLE + Math.PI * 2.0 * i / SLOT_COUNT;
            int sx = cx + (int) Math.round(Math.cos(angle) * radius);
            int sy = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean hovered = i == this.hoveredSlot;
            graphics.fill(sx - 20, sy - 20, sx + 20, sy + 20, hovered ? 0xFFB8F5B8 : 0xAA2E6B36);
            graphics.fill(sx - 18, sy - 18, sx + 18, sy + 18, hovered ? 0xCC1E4A25 : 0xCC0E1F12);
            String id = this.slots.get(i);
            if (id.isEmpty()) {
                continue;
            }
            int cooldown = this.cooldowns.getOrDefault(id, 0);
            boolean usable = !PossessionRules.isPuppetBlocked(id) && cooldown <= 0;
            graphics.drawCenteredString(this.font, initials(id), sx, sy - 9, usable ? 0xFFFFFFFF : 0xFF8A8A8A);
            graphics.drawCenteredString(this.font, MasteryMath.roman(this.ranks.getOrDefault(id, 1)), sx, sy + 1, 0xFFD4AF37);
            if (cooldown > 0) {
                graphics.drawCenteredString(this.font, String.format("%.1fs", cooldown / 20.0f), sx, sy + 11, 0xFFFF6B6B);
            }
        }
        if (this.hoveredSlot >= 0 && !this.slots.get(this.hoveredSlot).isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("spell." + ExampleMod.MODID + "." + this.slots.get(this.hoveredSlot)),
                    cx, cy - 4, 0xFFFFFFFF);
        }
    }

    private int slotAt(int mouseX, int mouseY, int cx, int cy, int radius) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (dx * dx + dy * dy < (radius * 0.4) * (radius * 0.4)) {
            return -1;
        }
        double angle = Math.atan2(dy, dx) - START_ANGLE;
        double turns = angle / (Math.PI * 2.0);
        return Math.floorMod((int) Math.round(turns * SLOT_COUNT), SLOT_COUNT);
    }

    private String initials(String id) {
        String name = Component.translatable("spell." + ExampleMod.MODID + "." + id).getString();
        StringBuilder out = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty() && out.length() < 2) {
                out.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return out.length() == 0 ? "?" : out.toString();
    }

    /** Releasing the wheel key casts the hovered spell (the same gesture as the normal spell wheel). */
    public void confirmSelectionAndClose() {
        if (this.hoveredSlot >= 0) {
            String id = this.slots.get(this.hoveredSlot);
            if (!id.isEmpty()) {
                PossessionClient.castPuppetSpell(id);
            }
        }
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            confirmSelectionAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
```

- [ ] **Step 5: The client glue.** In `PossessionClient.java`:

Add the imports `net.minecraft.network.chat.Component`.

Directly above the line `    // ---- State ---------------------------------------------------------------------------------` add:
```java
    /** The wheel key while possessing: the puppet's spells, but only while they hold their wand. */
    public static void openPuppetWheel(Minecraft minecraft) {
        if (snapshot == null || !snapshot.holdsWand()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".puppet.no_wand"), true);
            }
            return;
        }
        minecraft.setScreen(new PuppetSpellWheelScreen(snapshot));
    }

    /** The spell-slot cast keys while possessing cast the puppet's wand slots. */
    public static void handlePuppetSlotKeys(KeyMapping[] keys) {
        for (int slot = 0; slot < keys.length; slot++) {
            while (keys[slot].consumeClick()) {
                if (snapshot != null && slot < snapshot.wandSlots().size() && !snapshot.wandSlots().get(slot).isEmpty()) {
                    castPuppetSpell(snapshot.wandSlots().get(slot));
                }
            }
        }
    }

    public static void castPuppetSpell(String spellId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (role != PossessionRules.ROLE_CASTER || snapshot == null || minecraft.player == null) {
            return;
        }
        if (!snapshot.holdsWand()) {
            minecraft.player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".puppet.no_wand"), true);
            return;
        }
        if (PossessionRules.isPuppetBlocked(spellId)) {
            minecraft.player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".puppet.blocked"), true);
            return;
        }
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionCastPacket(spellId), PacketDistributor.SERVER.noArg());
    }

```
In `tickCaster`, replace:
```java
        if (holdRequired && !minecraft.options.keyUse.isDown()) {
```
with:
```java
        // Opening the puppet wheel releases every key mapping; right-click still counts as held while it's open.
        boolean held = minecraft.options.keyUse.isDown() || minecraft.screen instanceof PuppetSpellWheelScreen;
        if (holdRequired && !held) {
```

- [ ] **Step 6: Keys in `ClientModEvents`.**

At the top of the hold handling, replace:
```java
                    boolean useDown = mc.options.keyUse.isDown();
```
with:
```java
                    // While the puppet wheel is open the key mappings are released; the Imperio hold must survive it.
                    boolean useDown = mc.options.keyUse.isDown()
                            || mc.screen instanceof com.hogwartsmagic.client.PuppetSpellWheelScreen;
```
In the wheel key handling, replace:
```java
                    if (radialDown && !wasRadialDown && holdingMainWand && !(mc.screen instanceof WandRadialMenuScreen) && mc.screen == null) {
```
with:
```java
                    if (com.hogwartsmagic.client.PossessionClient.isCaster()) {
                        if (radialDown && !wasRadialDown && mc.screen == null) {
                            com.hogwartsmagic.client.PossessionClient.openPuppetWheel(mc);
                        }
                        if (!radialDown && wasRadialDown && mc.screen instanceof com.hogwartsmagic.client.PuppetSpellWheelScreen puppetWheel) {
                            puppetWheel.confirmSelectionAndClose();
                        }
                    }
                    if (radialDown && !wasRadialDown && holdingMainWand && !com.hogwartsmagic.client.PossessionClient.isCaster()
                            && !(mc.screen instanceof WandRadialMenuScreen) && mc.screen == null) {
```
Directly after the line
```java
    private static void handleSpellSlotKeybinds(Minecraft mc) {
```
insert:
```java
        if (com.hogwartsmagic.client.PossessionClient.isCaster()) {
            com.hogwartsmagic.client.PossessionClient.handlePuppetSlotKeys(SPELL_SLOT_KEYS);
            return;
        }
```

- [ ] **Step 7: Strings.** In `en_us.json`, directly after `  "gui.hogwartsmagic.puppet.mana": "Puppet mana %s / %s",` insert:
```json
  "gui.hogwartsmagic.puppet.title": "Puppet Spells",
  "gui.hogwartsmagic.puppet.header": "Puppet: %s",
  "gui.hogwartsmagic.puppet.no_wand": "Select their wand first",
  "gui.hogwartsmagic.puppet.blocked": "That spell can't be cast through a puppet",
```

- [ ] **Step 8: Build, test and boot**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PossessionManager`, `PuppetSpellWheelScreen`, `MasteryAwards` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/spell/mind/PossessionManager.java src/main/java/com/hogwartsmagic/client/PuppetSpellWheelScreen.java src/main/java/com/hogwartsmagic/client/PossessionClient.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a possessing caster cast the puppet spells as the puppet" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Full verification (dedicated server, two clients)

**Files:** none new.

**Interfaces:** none.

- [ ] **Step 1: Automated checks**

Run: `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/mind src/main/java/com/hogwartsmagic/command/ImperioCommand.java src/main/java/com/hogwartsmagic/mixin/LivingEntityPossessedMixin.java`
Expected: no output (server code stays client-free).

Run: `grep -rn "switch (" src/main/java/com/hogwartsmagic/client/PossessionClient.java src/main/java/com/hogwartsmagic/client/PuppetSpellWheelScreen.java src/main/java/com/hogwartsmagic/client/MindControlOverlay.java`
Expected: no output.

Run: `grep -rn "imperio_channeling\|tickImperioChannel\|applyImperioControl" src/main/java`
Expected: no output (the old channel is gone).

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `Possession`, `Puppet`, `ImperioCommand` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/hogwartsmagic.mixins.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 2: In-game checklist on the dedicated server** (set up as in Task 2 Step 11: built jar in `Hogwarts Magic Server\mods`, `online-mode=false` for the test, client A `./gradlew runClient` as Dev, client B from the launcher; restore `online-mode` afterwards)

On A: `/spellmastery setrank Dev imperio 4`, wand with Imperio learned.
1. A hits a zombie with Imperio and keeps holding: after 2s A's view jumps behind the zombie's eyes; A's own body freezes (doesn't walk, jump or turn); WASD walks the zombie, space jumps, the mouse aims; left-click makes it bite what it looks at.
2. A lets go of right-click: the view snaps back at once; A is nauseous for 3s; Imperio's cooldown starts now and A gets Imperio XP (check `/spellmastery get`).
3. A possesses B (B holds a sword): A sees B's hotbar in place of A's own; scrolling or number keys change B's held item (B sees it change and can't change it back); left-click swings B's sword at whatever B looks at; B's Q, F, E, number keys, scroll, mouse and WASD do nothing.
4. A possesses B for the full 20s: it ends by itself; B's view and controls return; both HUDs clear.
5. B mashes SPACE: nothing happens before 8s ("Mash SPACE to resist!" appears at 8s); after that, fast mashing breaks free within a few seconds; then A's Imperio on B within 3s just puffs smoke.
6. While A possesses B, a third player (or a skeleton) hits A's frozen body for 4+: "A blow to your body throws you out!"; a hit under 4 doesn't.
7. Possession ends when: B dies; A dies; B logs out; A logs out (A rejoins unrooted); A walks B more than 48 blocks from A's body; A's main hand stops holding an Imperio wand (A's own hotbar keys now steer B, so test it from the console: `item replace entity Dev weapon.mainhand with minecraft:stick`); someone Stupefies A's body; A runs out of mana mid-session (set mana low first).
8. B holds a wand with Stupefy and Avada Kedavra learned: A presses the spell wheel key, sees "Puppet: B" with B's slots and ranks; releasing over Stupefy fires a Stupefy beam from B's eyes where A aims; B's mana drops and B's wand shows the cooldown; B earns no Stupefy XP. Imperio and Apparition are greyed and refused. The spell-slot cast keys also cast B's slots. With B holding a sword instead, the wheel key says "Select their wand first".
9. A possesses Voldemort (or another boss) with `/imperio possess`: 5s cap; no resist prompt; left-click fires one of his spells along A's aim. Possessing the Horntail: left-click breathes fire where A looks. The Basilisk: left-click spits venom.
10. A possesses a skeleton: left-click shoots at what it looks at; a creeper: left-click lights its fuse.
11. `imperioAffectsPlayers=false`: holding Imperio on B says Imperio on players is off; mobs still work.
12. Disconnect A while possessing (close the client): on the server, B regains control within half a second; reconnect A: normal camera, not rooted.
13. Spam: while possessing, holding a scroll wheel spin or a macro on the number keys never changes B's slot more than 10 times a second; the server log stays clean.

- [ ] **Step 3: Commit** (only if Step 1 or 2 needed a fix; otherwise nothing to commit)
```bash
git add <the files you fixed>
git commit -m "Fix issues found in the Phase 6b verification pass" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage (the Master half of "Imperio: Thrall and Possession"):** hold right-click on the target to possess for up to 20s, 5s on a boss (T1, T2, T3); camera to the victim's eyes, controls drive walking, looking, jumping, attacking and the held item (T2, T6); letting go ends it early (T2, T3); possessing a player shows and scrolls their hotbar (T7); selecting their wand switches the wheel and cast keys to their learned spells at their ranks, spending their mana and putting their wand's cooldowns on (T7, T8); "Puppet" header (T8); Imperio and Apparition refused through a puppet (T1, T8); forced casts earn the victim no XP, the caster earns Imperio XP for the possession (T3, T8); possessing a mob or boss drives its body and its own attack, Horntail fire included (T2, T6); the caster's body frozen in a trance, still hurtable, a 4+ hit ejects (T2, T4); players mash SPACE after 8s, 6+ presses per half second for a 20% roll, too short to resist on bosses (T1, T5); every end reason (release, 20s, broke free, body hit, either dies, logout, dimension change) plus distance, interrupt and mana (T2–T5); camera snaps back and both bodies get controls back, broken-free victim immune 3s, caster nauseous 3s (T4); cooldown starts when it ends (T3); can't make them drop, throw or open inventories or chests (6a guards widened in T5, client key draining in T2); `imperioAffectsPlayers` gates player possession, mobs always allowed (T2); mob AI restored afterwards (T2 end). Hotbar scrolling sets the victim's selected slot (T7); casts go through the normal cast path as the victim (T8); the victim's client zeroes its own input and reports space presses that the server counts and caps (T5). Camera and input code only in `client/` (T2).
- **Dedicated-server safety:** the server owns every session; the spike (T2) is proven on the real dedicated server with two clients before T3–T9; every C2S packet is validated (only the session's caster or victim, rate limits, slot range, sanitised floats, exact spell ids); `PossessionTurnMixin` is client-list only, `LivingEntityPossessedMixin` is common and client-free; `runServer` boots in T2–T9; T9 greps the server packages for client references.
- **Placeholder scan:** none; the spike's fallback transport is concrete code with a concrete trigger.
- **Type consistency:** `PossessionRules.ROLE_*` are `byte` in `PossessionStatePacket(byte, int, int, boolean, boolean)` and `PossessionClient`; `RateWindow.tryAcquire(long)`; `PossessionManager.start(ServerPlayer, LivingEntity, boolean)` used by `ImperioCommand` (debug true) and `startFromHold` (false); `EndReason.langKey()` keys match the T2 lang lines; `PuppetSnapshotPacket` accessors (`victimId()`, `victimName()`, `holdsWand()`, `selectedSlot()`, `hotbarIds()`, `hotbarCounts()`, `mana()`, `maxMana()`, `learnedIds()`, `learnedRanks()`, `cooldownTicks()`, `wandSlots()`) match their uses in `PossessionClient`, `MindControlOverlay` and `PuppetSpellWheelScreen`; `PossessedAttacks.attack(LivingEntity, ServerPlayer)` → `int` assigned to `PossessionSession.attackCooldown`; `ImperioThralls.startImperioCooldown(ServerPlayer)` and `CastSpellPacket.castAs(ServerPlayer, CastSpellPacket)` match 6a.

