# Spell Mastery Phase 7a (Patronus Forms, Serpensortia Snakes, Fiendfyre) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three of the roadmap's "expensive" features. **Expecto Patronum** grows with rank: Novice is a four-second silver mist that holds Dementors back, Adept summons a corporeal guardian that hunts them for 10s, Expert gives the caster their own animal form (chosen once from UUID + house, drawn with tinted vanilla animal models) for 20s, and Master lasts 30s, lights the dark, repels undead within 6 blocks and can carry a message to a player within 500 blocks. **Serpensortia** conjures real snakes instead of cave spiders: 1/2/3/3 snakes for 20/30/40/60s, Poison II from Expert, allied to and owned by the caster, and at Master the caster speaks Parseltongue (attack / guard / follow) while wild snakes leave them alone. **Fiendfyre** becomes rank-shaped: 6/8/10/12s, a 15/8/3/0% chance a serpent goes rogue after a mandatory one-second red glow, two serpents at Expert, three at Master that can be sent at separate targets, and a Master serpent devours a Horcrux. Every block of fire respects `mobGriefing`, claims, spawn protection and Cave Inimicum wards.

**Architecture:** Every per-rank number lives in a Minecraft-free class in the new package `spell/summon/` (`PatronusRules`, `PatronusForm`, `PatronusForms`, `SerpensortiaRules`, `FiendfyreRules`), tested with JUnit in Task 1. Minecraft-bound behaviour lives in one class per feature in the same package (`PatronusSpell`, `PatronusMessenger`, `SerpensortiaSpell`, `FiendfyreChannel`) plus the entities in `entity/` (`PatronusEntity` reworked, new `SnakeEntity`, `FiendfyreSerpentEntity` reworked, `FiendfyreEntity` made terrain-safe). The cast path (`SpellPackets.CastSpellPacket.castAs`, extracted in Phase 6a) and `ModEvents` only delegate. The Fiendfyre channel, today split between `SpellPackets` and `ModEvents`, moves whole into `FiendfyreChannel`. All visual state the client needs travels as synced entity data (Patronus form id, serpent state), so this phase adds no packets. Client code is two renderers and one model (`PatronusRenderer` reworked, new `SnakeModel` + `SnakeRenderer`) and one colour change in `FiendfyreSerpentRenderer`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`), Pillow for the two generated textures.

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 7: The expensive features" (Patronus forms with tinted vanilla models chosen by UUID + house; a Serpensortia snake entity replacing the cave spiders; Fiendfyre rogue AI with a telegraph and multiple serpents), Decision 11 (a serpent about to go rogue glows red for 1s first), Decision 9 (no corruption: Fiendfyre's "every cast adds corruption" is dropped) and "Global constraints". Numbers come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` sections F (Expecto Patronum) and G (Fiendfyre, Serpensortia). Where they disagree, the roadmap wins. The second half of Phase 7 (Wingardium blocks, Arresto freeze, Tenebris pass-through, Alatus quality) is `2026-10-02-spell-mastery-phase7b.md`.

**Depends on Phases 1–6 (all merged first).** This plan calls, by exact signature:
- Phase 2: `CombatEffects.responsible(@Nullable Entity)` → `Entity`, `CombatEffects.isCastingBlocked(LivingEntity)`, `CombatEffects.notifyCastBlocked(Player)`; `CombatTags.ALLIED` (`String`), `CombatTags.isBoss(@Nullable Entity)`.
- Phase 3: `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int` (players: their mastery rank; boss NPCs: 3; other NPCs and ownerless: 1); `TerrainRules.canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`.
- Phase 4: `TerrainRules.canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster)` → `boolean`. **It posts a real `BlockEvent.BreakEvent`**; it is called only as the very last step right before a block is actually destroyed (Task 10, Horcrux).
- Phase 5a: `SupportMana.trySpend(ServerPlayer, float)` → `boolean` (creative always true); `ModEvents.isLeftClickSelfCast(SpellType)` (private, extended in 5b with Reparo; this plan adds Serpensortia).
- Phase 6a: `SpellPackets.CastSpellPacket.castAs(ServerPlayer player, CastSpellPacket p)` holds the whole cast path; its body is the old `handle` body unchanged apart from the edits earlier phases list, so every text anchor below that names `castAs` is the old `handle` text.
- Phase 1: `SpellMasteryData.getRank(Player, SpellType)` → `int` 1..4; `SpellCosts.manaCost(Player, ItemStack, SpellType)`, `SpellCosts.masteryMultiplier(Player, SpellType)`, `SpellCosts.startCooldown(ServerPlayer, ItemStack, SpellType, long)`; `MasteryAwards` (Serpensortia stays a block-utility spell; Expecto Patronum and Fiendfyre stay self/channel spells, so no change there).
- `SpellType.applyHitEffect(LivingEntity target, Entity caster, @Nullable Vec3 beamDir)` holds the per-spell `switch`; the 2-argument form delegates with `null`.

Before Task 1, confirm they are in:
```bash
grep -n "public static int casterRank" src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java
grep -n "public static boolean canBreak" src/main/java/com/hogwartsmagic/spell/TerrainRules.java
grep -n "public static boolean trySpend" src/main/java/com/hogwartsmagic/spell/support/SupportMana.java
grep -n "private static boolean isLeftClickSelfCast" src/main/java/com/hogwartsmagic/event/ModEvents.java
grep -n "public static void castAs" src/main/java/com/hogwartsmagic/network/SpellPackets.java
```
Each must print one line. If any doesn't, stop: the phase it belongs to isn't merged.

## Global Constraints

- **Ranks:** every per-rank behaviour reads the caster's rank through `SpellMasteryData.getRank` (player-only paths: the Patronus, the Fiendfyre channel, Parseltongue) or `DisableSupport.casterRank` (paths NPCs also reach: Serpensortia beams, Fiendfyre beams). Voldemort and other bosses therefore conjure at Expert (3 snakes, Poison II).
- **Per-rank numbers (ticks at 20/s):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Expecto Patronum | held mist shield (today's cone), at most 80; no animal | corporeal guardian (the stag model) for 200, hunts Dementors | 400; the caster's own form (one of 8, chosen once) | 600; follow light + light ring, repels undead within 6 blocks, `/patronus <player> <message>` within 500 blocks (30s message cooldown, costs the spell's mana) |
  | Serpensortia | 1 snake, 400, bite Poison I 60 | 2 snakes, 600 | 3 snakes, 800, Poison II | 3 snakes, 1200, Parseltongue: left-click commands (aimed creature = attack it; otherwise toggle follow / guard at the aimed block); wild snakes never target a Master |
  | Fiendfyre (held channel) | 1 serpent, 120, rogue 15% | 1 serpent, 160, rogue 8% | 2 serpents, 200, rogue 3% | 3 serpents, 240, never rogue; left-click while holding sends the next serpent at the creature you aim at; a serpent within 2 blocks of an active or primed Horcrux destroys it |

- **Rogue Fiendfyre (Decision 11):** at channel start one roll decides whether this cast goes rogue; if so, one serpent (random index) turns at a random tick between 50 and `duration − 20`. For the 20 ticks before it turns it glows red (renderer tint plus red dust particles) and the caster sees "The Fiendfyre writhes against your will!". When it turns it stops following aim, hunts the nearest living creature within 16 blocks (the caster included; creative and spectator players excluded) until the cast's duration ends, burns everything it touches, and keeps setting terrain-safe fire. If no controlled serpent is left, the channel ends and the cooldown starts.
- **Fire is terrain-safe:** every fire block a serpent or a Fiendfyre beam's ground fire places goes through `TerrainRules.canPlace(level, pos, caster)` and only into loaded, empty positions. An offline or unknown caster falls back to the `mobGriefing` rule.
- **Snakes are the caster's:** `SnakeEntity` implements `OwnableEntity` (so `DisableSupport.isEnemyOf`, `DamageSupport.isSplashTarget` and `MasteryAwards` treat them as owned), carries `CombatTags.ALLIED` when the owner is a player, never targets its owner, the owner's other snakes, the owner's owned or team-mate mobs, `ALLIED` mobs (player owners) or creative/spectator players, and is refused any such target even through `setTarget`. A new cast dismisses the caster's previous snakes. A snake's guard post stores its dimension.
- **Patronus forms:** chosen the first time the caster casts at Expert or higher, from `PatronusForms.pick(uuid, house)` (house pools of four; unsorted players draw from all eight), stored in `PlayerPersisted/hogwartsmagic/PatronusForm` and never re-rolled. Stag and Doe use the existing stag model (Doe hides the antlers); Hare, Wolf, Cat, Owl, Fox and Phoenix use the vanilla rabbit, wolf, ocelot, fox and parrot models and the mod's owl model, all drawn with one silver texture through `RenderType.entityTranslucentEmissive` and a pale translucent tint.
- **Teleports and spawns only into collision-free space** (Patronus following its owner, the messenger Patronus, snakes following their owner and every conjured snake).
- **Per-tick handlers** read fields or gate on an effect/flag before touching persistent data; the existing player-tick channel checks keep their current pattern.
- **New entities:** attributes registered in `ModEntityAttributes`, renderers registered only in `ClientModEvents` (client-only), synced data defined in `defineSynchedData`; one spawn egg (snake, useful for testing wild snakes). No `broadcastEntityEvent` is used; if you add one, never bytes 21, 35 or 63 (use 61/62/64/66/67).
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/`. This plan adds no packets.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine. (`PatronusRenderer` compares `PatronusForm` values with `==`.)
- Screens: `renderTransparentBackground`, no blur. (This plan adds no screens.)
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards (fire through `TerrainRules.canPlace`, the Horcrux through `TerrainRules.canBreak` as the last step).
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs).
- `en_us.json` must stay valid JSON after every edit: replacements keep the original line's trailing comma, and an inserted line ends with a comma and goes after a line that already ends with one. A literal percent sign in a translated string is written `%%` (they are rendered through `Component.translatable`).
- Line numbers drift (other phases edit the same files). Every edit below names the exact text to find; trust the text, not a number.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/summon/RankIndex.java` | Pure: rank 1..4 → table index, clamped (package-private) |
| Create `src/main/java/com/hogwartsmagic/spell/summon/PatronusRules.java` | Pure: shield cap, corporeal lifespan, form and Master gates, message range, cooldown and sanitising |
| Create `src/main/java/com/hogwartsmagic/spell/summon/PatronusForm.java` | Pure: the nine form ids (generic + eight animals) and their lang keys |
| Create `src/main/java/com/hogwartsmagic/spell/summon/PatronusForms.java` | Pure: UUID + house → form |
| Create `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaRules.java` | Pure: snake count, lifetime, poison, Parseltongue, command numbers |
| Create `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreRules.java` | Pure: duration, rogue chance and timing, serpent count and formation, dispatch, Horcrux |
| Create `src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java` | Corporeal summon, form choice, the shield's start-time key |
| Create `src/main/java/com/hogwartsmagic/spell/summon/PatronusMessenger.java` | Master message: checks, mana, cooldown, delivery |
| Create `src/main/java/com/hogwartsmagic/command/PatronusCommand.java` | `/patronus <player> <message>` |
| Create `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java` | Conjure, dismiss, Parseltongue commands |
| Create `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java` | The whole held channel: start, heartbeat, tick, stop, serpents, dispatch |
| Create `src/main/java/com/hogwartsmagic/entity/SnakeEntity.java` | The snake: owner, life, poison bite, follow/guard/attack, wild hunting |
| Create `src/main/java/com/hogwartsmagic/client/model/SnakeModel.java` | Java-coded six-segment snake with a slither and tongue flick |
| Create `src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java` | Snake renderer |
| Create `src/main/java/com/hogwartsmagic/client/renderer/PatronusFormDummies.java` | Client: one stand-in vanilla entity per model type, for the typed vanilla models |
| Create `build_scripts/generate_snake_texture.py`, `build_scripts/generate_patronus_form_texture.py` | Pillow texture generators |
| Create `src/main/resources/assets/hogwartsmagic/textures/entity/snake.png`, `patronus_form.png` | Generated textures |
| Create `src/main/resources/assets/hogwartsmagic/models/item/snake_spawn_egg.json` | Spawn egg model |
| Modify `src/main/java/com/hogwartsmagic/entity/PatronusEntity.java` | Owner, rank lifespan, synced form, Master light and undead ward, follow, save |
| Modify `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` | Rewritten: formation, states (controlled, telegraph, rogue, dispatched), terrain-safe fire, Horcrux |
| Modify `src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java` | Remembers its caster; terrain-safe fire |
| Modify `src/main/java/com/hogwartsmagic/entity/ModEntities.java` | `SNAKE` |
| Modify `src/main/java/com/hogwartsmagic/event/ModEntityAttributes.java` | Snake attributes |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Snake spawn egg and tab entry; `/patronus` registration |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Snake layer and renderer |
| Modify `src/main/java/com/hogwartsmagic/client/model/PatronusModel.java` | `setAntlersVisible` |
| Modify `src/main/java/com/hogwartsmagic/client/renderer/PatronusRenderer.java` | Draws the personal forms |
| Modify `src/main/java/com/hogwartsmagic/client/FiendfyreSerpentRenderer.java` | Red tint while telegraphing or rogue |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | Patronus rank gate; Fiendfyre heartbeat and release delegate; Parseltongue hook; old helpers removed |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Shield cap; Fiendfyre channel removed (delegates); Serpensortia left-click |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Serpensortia and Fiendfyre cases |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Serpensortia block hit; Fiendfyre ground fire remembers its caster |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Three descriptions, entity, forms, messages |
| Create tests under `src/test/java/com/hogwartsmagic/spell/summon/` | Unit tests |

---

### Task 1: The pure rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/RankIndex.java`, `PatronusRules.java`, `PatronusForm.java`, `PatronusForms.java`, `SerpensortiaRules.java`, `FiendfyreRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/summon/PatronusRulesTest.java`, `PatronusFormsTest.java`, `SerpensortiaRulesTest.java`, `FiendfyreRulesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private)
  - `PatronusRules.SHIELD_MAX_TICKS = 80`, `UNDEAD_RADIUS = 6.0`, `UNDEAD_INTERVAL = 5`, `FOLLOW_START = 6.0`, `FOLLOW_STOP = 3.0`, `TELEPORT_DISTANCE = 24.0`, `MESSAGE_RANGE = 500`, `MESSAGE_MAX_LENGTH = 200`, `MESSAGE_COOLDOWN_TICKS = 600`, `MESSENGER_TICKS = 100`; `static boolean isCorporeal(int rank)`, `static int lifespanTicks(int rank)`, `static boolean personalForm(int rank)`, `static boolean masterAura(int rank)`, `static boolean sendsMessages(int rank)`, `static boolean shieldExpired(long startedAt, long now)`, `static boolean inMessageRange(double distanceSqr)`, `static long messageCooldownLeft(boolean sentBefore, long lastSentAt, long now)`, `static String sanitizeMessage(String raw)`
  - `PatronusForm` enum `GENERIC(0) STAG(1) DOE(2) HARE(3) WOLF(4) CAT(5) OWL(6) FOX(7) PHOENIX(8)`; `int id()`, `String langKey()`, `static PatronusForm byId(int id)` (unknown → `GENERIC`)
  - `PatronusForms.pick(long mostBits, long leastBits, int houseIndex)` → `int` form id (houseIndex = `HogwartsHouse.ordinal()`, or −1 when unsorted); `static int[] pool(int houseIndex)`
  - `SerpensortiaRules.MODE_FOLLOW = 0`, `MODE_GUARD = 1`, `POISON_TICKS = 60`, `COMMAND_RANGE = 24.0`, `GUARD_RADIUS = 8.0`, `GUARD_LEASH = 4.0`, `COMMAND_COOLDOWN_TICKS = 10`, `FOLLOW_START = 5.0`, `FOLLOW_STOP = 2.0`, `TELEPORT_DISTANCE = 16.0`, `SPAWN_SPACING = 0.8`, `DISMISS_RADIUS = 64.0`; `static int snakeCount(int rank)`, `static int lifetimeTicks(int rank)`, `static int poisonAmplifier(int rank)`, `static boolean parseltongue(int rank)`, `static int toggledMode(int mode)`, `static double spawnOffset(int index, int count)`
  - `FiendfyreRules.TELEGRAPH_TICKS = 20`, `MIN_TURN_TICKS = 30`, `ROGUE_SEARCH_RADIUS = 16.0`, `ROGUE_RETARGET_INTERVAL = 20`, `DISPATCH_TICKS = 80`, `DISPATCH_RANGE = 48.0`, `HORCRUX_REACH = 2`, `HORCRUX_SCAN_INTERVAL = 10`; `static int durationTicks(int rank)`, `static double rogueChance(int rank)`, `static boolean goesRogue(int rank, double roll)`, `static int serpentCount(int rank)`, `static int rogueIndex(int count, double roll)`, `static int rogueTurnTick(int durationTicks, double roll)`, `static boolean telegraphing(int lifeTicks, int turnTick)`, `static int rogueLifeEnd(int durationTicks, int turnTick)`, `static boolean expired(long startedAt, long now, int rank)`, `static double lateralOffset(int index, int count)`, `static boolean dispatches(int rank)`, `static boolean destroysHorcrux(int rank)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/summon/PatronusRulesTest.java`:
```java
package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronusRulesTest {
    @Test
    void noviceIsMistAndTheRestAreCorporeal() {
        assertFalse(PatronusRules.isCorporeal(1));
        assertTrue(PatronusRules.isCorporeal(2));
        assertTrue(PatronusRules.isCorporeal(4));
        assertEquals(0, PatronusRules.lifespanTicks(1));
        assertEquals(200, PatronusRules.lifespanTicks(2));
        assertEquals(400, PatronusRules.lifespanTicks(3));
        assertEquals(600, PatronusRules.lifespanTicks(4));
        assertEquals(600, PatronusRules.lifespanTicks(9));
    }

    @Test
    void formAtExpertAuraAndMessagesAtMaster() {
        assertFalse(PatronusRules.personalForm(2));
        assertTrue(PatronusRules.personalForm(3));
        assertFalse(PatronusRules.masterAura(3));
        assertTrue(PatronusRules.masterAura(4));
        assertFalse(PatronusRules.sendsMessages(3));
        assertTrue(PatronusRules.sendsMessages(4));
    }

    @Test
    void mistHoldsForFourSeconds() {
        assertFalse(PatronusRules.shieldExpired(100L, 179L));
        assertTrue(PatronusRules.shieldExpired(100L, 180L));
    }

    @Test
    void messagesReachFiveHundredBlocksAndCoolDown() {
        assertTrue(PatronusRules.inMessageRange(500.0 * 500.0));
        assertFalse(PatronusRules.inMessageRange(500.1 * 500.1));
        assertEquals(0L, PatronusRules.messageCooldownLeft(false, 0L, 10L));
        assertEquals(500L, PatronusRules.messageCooldownLeft(true, 100L, 200L));
        assertEquals(0L, PatronusRules.messageCooldownLeft(true, 100L, 700L));
    }

    @Test
    void messagesAreTrimmedStrippedAndCapped() {
        assertEquals("hello", PatronusRules.sanitizeMessage("  hello  "));
        // The section sign is what turns text into a colour code; removing it is enough, and the
        // letter that followed it stays as plain text.
        assertEquals("cred text", PatronusRules.sanitizeMessage("§cred text"));
        assertEquals("ab", PatronusRules.sanitizeMessage("a\nb"));
        assertEquals("", PatronusRules.sanitizeMessage("   "));
        assertEquals("", PatronusRules.sanitizeMessage(null));
        assertEquals(200, PatronusRules.sanitizeMessage("x".repeat(500)).length());
    }
}
```

`src/test/java/com/hogwartsmagic/spell/summon/PatronusFormsTest.java`:
```java
package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatronusFormsTest {
    @Test
    void sameUuidAndHouseAlwaysGiveTheSameForm() {
        int first = PatronusForms.pick(123456789L, -987654321L, 0);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, PatronusForms.pick(123456789L, -987654321L, 0));
        }
    }

    @Test
    void housesDrawFromTheirOwnPool() {
        for (int house = 0; house < 4; house++) {
            Set<Integer> pool = new HashSet<>();
            for (int id : PatronusForms.pool(house)) {
                pool.add(id);
            }
            assertEquals(4, pool.size());
            for (long seed = 0; seed < 200; seed++) {
                assertTrue(pool.contains(PatronusForms.pick(seed * 31L, seed * 17L + 5L, house)));
            }
        }
    }

    @Test
    void unsortedPlayersCanGetAnyAnimalButNeverTheGenericShape() {
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 400; seed++) {
            int id = PatronusForms.pick(seed * 7919L, ~seed, -1);
            assertNotEquals(PatronusForm.GENERIC.id(), id);
            seen.add(id);
        }
        assertEquals(8, seen.size());
    }

    @Test
    void unknownIdsFallBackToGeneric() {
        assertEquals(PatronusForm.GENERIC, PatronusForm.byId(99));
        assertEquals(PatronusForm.OWL, PatronusForm.byId(6));
        assertEquals("patronus.hogwartsmagic.form.owl", PatronusForm.OWL.langKey());
    }
}
```

`src/test/java/com/hogwartsmagic/spell/summon/SerpensortiaRulesTest.java`:
```java
package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerpensortiaRulesTest {
    @Test
    void moreSnakesLongerAndPoisonTwoFromExpert() {
        assertEquals(1, SerpensortiaRules.snakeCount(1));
        assertEquals(2, SerpensortiaRules.snakeCount(2));
        assertEquals(3, SerpensortiaRules.snakeCount(3));
        assertEquals(3, SerpensortiaRules.snakeCount(4));
        assertEquals(400, SerpensortiaRules.lifetimeTicks(1));
        assertEquals(600, SerpensortiaRules.lifetimeTicks(2));
        assertEquals(800, SerpensortiaRules.lifetimeTicks(3));
        assertEquals(1200, SerpensortiaRules.lifetimeTicks(4));
        assertEquals(0, SerpensortiaRules.poisonAmplifier(2));
        assertEquals(1, SerpensortiaRules.poisonAmplifier(3));
    }

    @Test
    void parseltongueAtMasterAndModesToggle() {
        assertFalse(SerpensortiaRules.parseltongue(3));
        assertTrue(SerpensortiaRules.parseltongue(4));
        assertEquals(SerpensortiaRules.MODE_GUARD, SerpensortiaRules.toggledMode(SerpensortiaRules.MODE_FOLLOW));
        assertEquals(SerpensortiaRules.MODE_FOLLOW, SerpensortiaRules.toggledMode(SerpensortiaRules.MODE_GUARD));
    }

    @Test
    void snakesSpreadAroundTheImpact() {
        assertEquals(0.0, SerpensortiaRules.spawnOffset(0, 1), 1e-9);
        assertEquals(-0.4, SerpensortiaRules.spawnOffset(0, 2), 1e-9);
        assertEquals(0.4, SerpensortiaRules.spawnOffset(1, 2), 1e-9);
        assertEquals(-0.8, SerpensortiaRules.spawnOffset(0, 3), 1e-9);
        assertEquals(0.8, SerpensortiaRules.spawnOffset(2, 3), 1e-9);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/summon/FiendfyreRulesTest.java`:
```java
package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiendfyreRulesTest {
    @Test
    void longerFireAndMoreSerpentsWithRank() {
        assertEquals(120, FiendfyreRules.durationTicks(1));
        assertEquals(160, FiendfyreRules.durationTicks(2));
        assertEquals(200, FiendfyreRules.durationTicks(3));
        assertEquals(240, FiendfyreRules.durationTicks(4));
        assertEquals(1, FiendfyreRules.serpentCount(1));
        assertEquals(1, FiendfyreRules.serpentCount(2));
        assertEquals(2, FiendfyreRules.serpentCount(3));
        assertEquals(3, FiendfyreRules.serpentCount(4));
    }

    @Test
    void rogueChanceShrinksToNothing() {
        assertEquals(0.15, FiendfyreRules.rogueChance(1), 1e-9);
        assertEquals(0.08, FiendfyreRules.rogueChance(2), 1e-9);
        assertEquals(0.03, FiendfyreRules.rogueChance(3), 1e-9);
        assertEquals(0.0, FiendfyreRules.rogueChance(4), 1e-9);
        assertTrue(FiendfyreRules.goesRogue(1, 0.14));
        assertFalse(FiendfyreRules.goesRogue(1, 0.15));
        assertFalse(FiendfyreRules.goesRogue(4, 0.0));
    }

    @Test
    void theTurnLeavesRoomForTheTelegraphAndTheRampage() {
        assertEquals(50, FiendfyreRules.rogueTurnTick(120, 0.0));
        assertEquals(100, FiendfyreRules.rogueTurnTick(120, 0.999999));
        assertEquals(100, FiendfyreRules.rogueTurnTick(120, 1.0));
        assertEquals(50, FiendfyreRules.rogueTurnTick(40, 0.5));
        assertFalse(FiendfyreRules.telegraphing(79, 100));
        assertTrue(FiendfyreRules.telegraphing(80, 100));
        assertTrue(FiendfyreRules.telegraphing(99, 100));
        assertFalse(FiendfyreRules.telegraphing(100, 100));
        assertFalse(FiendfyreRules.telegraphing(90, -1));
        assertEquals(120, FiendfyreRules.rogueLifeEnd(120, 60));
        assertEquals(140, FiendfyreRules.rogueLifeEnd(120, 100));
    }

    @Test
    void rogueIndexStaysInRange() {
        assertEquals(0, FiendfyreRules.rogueIndex(1, 0.9));
        assertEquals(0, FiendfyreRules.rogueIndex(2, 0.4));
        assertEquals(1, FiendfyreRules.rogueIndex(2, 0.6));
        assertEquals(1, FiendfyreRules.rogueIndex(2, 1.0));
    }

    @Test
    void channelEndsAtTheRankDuration() {
        assertFalse(FiendfyreRules.expired(1000L, 1119L, 1));
        assertTrue(FiendfyreRules.expired(1000L, 1120L, 1));
        assertFalse(FiendfyreRules.expired(1000L, 1239L, 4));
    }

    @Test
    void formationAndMasterPerks() {
        assertEquals(0.0, FiendfyreRules.lateralOffset(0, 1), 1e-9);
        assertEquals(-1.6, FiendfyreRules.lateralOffset(0, 2), 1e-9);
        assertEquals(1.6, FiendfyreRules.lateralOffset(1, 2), 1e-9);
        assertEquals(-2.4, FiendfyreRules.lateralOffset(0, 3), 1e-9);
        assertEquals(0.0, FiendfyreRules.lateralOffset(1, 3), 1e-9);
        assertEquals(2.4, FiendfyreRules.lateralOffset(2, 3), 1e-9);
        assertFalse(FiendfyreRules.dispatches(3));
        assertTrue(FiendfyreRules.dispatches(4));
        assertFalse(FiendfyreRules.destroysHorcrux(3));
        assertTrue(FiendfyreRules.destroysHorcrux(4));
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.summon.*"`
Expected: compilation fails (`cannot find symbol: class PatronusRules` and similar).

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/summon/RankIndex.java`:
```java
package com.hogwartsmagic.spell.summon;

/** Rank 1..4 to a table index 0..3; anything outside is clamped. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/summon/PatronusRules.java`:
```java
package com.hogwartsmagic.spell.summon;

/**
 * Expecto Patronum by rank. Novice is the incorporeal mist (the held cone of light, capped at four
 * seconds); Adept answers with a corporeal guardian; Expert gives it the caster's own form; Master makes
 * it last, light the dark, ward off the undead and carry messages.
 */
public final class PatronusRules {
    public static final int SHIELD_MAX_TICKS = 80;
    public static final double UNDEAD_RADIUS = 6.0;
    public static final int UNDEAD_INTERVAL = 5;
    public static final double FOLLOW_START = 6.0;
    public static final double FOLLOW_STOP = 3.0;
    public static final double TELEPORT_DISTANCE = 24.0;
    public static final int MESSAGE_RANGE = 500;
    public static final int MESSAGE_MAX_LENGTH = 200;
    public static final int MESSAGE_COOLDOWN_TICKS = 600;
    public static final int MESSENGER_TICKS = 100;
    private static final int[] LIFESPAN = {0, 200, 400, 600};

    private PatronusRules() {
    }

    public static boolean isCorporeal(int rank) {
        return RankIndex.of(rank) >= 1;
    }

    public static int lifespanTicks(int rank) {
        return LIFESPAN[RankIndex.of(rank)];
    }

    public static boolean personalForm(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    /** Master only: the follow light, the light ring and the undead ward. */
    public static boolean masterAura(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean sendsMessages(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean shieldExpired(long startedAt, long now) {
        return now - startedAt >= SHIELD_MAX_TICKS;
    }

    public static boolean inMessageRange(double distanceSqr) {
        return distanceSqr <= (double) MESSAGE_RANGE * MESSAGE_RANGE;
    }

    /** Ticks until the next message may be sent; 0 when ready or when none was ever sent. */
    public static long messageCooldownLeft(boolean sentBefore, long lastSentAt, long now) {
        if (!sentBefore) {
            return 0L;
        }
        return Math.max(0L, lastSentAt + MESSAGE_COOLDOWN_TICKS - now);
    }

    /** Drops the section sign (colour codes) and control characters, caps the length and trims. */
    public static String sanitizeMessage(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length() && out.length() < MESSAGE_MAX_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c == '§' || Character.isISOControl(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString().strip();
    }
}
```

`src/main/java/com/hogwartsmagic/spell/summon/PatronusForm.java`:
```java
package com.hogwartsmagic.spell.summon;

/**
 * The shapes a corporeal Patronus can take. GENERIC is the indistinct stag every Adept summons; the
 * other eight are personal forms (Expert and up). The id is what PatronusEntity syncs to clients and
 * what PlayerPersisted stores, so ids never change meaning.
 */
public enum PatronusForm {
    GENERIC(0, "generic"),
    STAG(1, "stag"),
    DOE(2, "doe"),
    HARE(3, "hare"),
    WOLF(4, "wolf"),
    CAT(5, "cat"),
    OWL(6, "owl"),
    FOX(7, "fox"),
    PHOENIX(8, "phoenix");

    private final int id;
    private final String key;

    PatronusForm(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() {
        return this.id;
    }

    public String langKey() {
        return "patronus.hogwartsmagic.form." + this.key;
    }

    public static PatronusForm byId(int id) {
        for (PatronusForm form : values()) {
            if (form.id == id) {
                return form;
            }
        }
        return GENERIC;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/summon/PatronusForms.java`:
```java
package com.hogwartsmagic.spell.summon;

/**
 * Which animal answers a caster. Deterministic: the same UUID and house always give the same form, and
 * the caller stores the first answer so a later house change can't alter it. Each house draws from four
 * forms in keeping with it; an unsorted caster draws from all eight.
 */
public final class PatronusForms {
    /** Indexed by HogwartsHouse.ordinal(): GRYFFINDOR, RAVENCLAW, HUFFLEPUFF, SLYTHERIN. */
    private static final int[][] HOUSE_POOLS = {
            {PatronusForm.STAG.id(), PatronusForm.WOLF.id(), PatronusForm.PHOENIX.id(), PatronusForm.HARE.id()},
            {PatronusForm.OWL.id(), PatronusForm.HARE.id(), PatronusForm.FOX.id(), PatronusForm.CAT.id()},
            {PatronusForm.DOE.id(), PatronusForm.HARE.id(), PatronusForm.CAT.id(), PatronusForm.WOLF.id()},
            {PatronusForm.FOX.id(), PatronusForm.CAT.id(), PatronusForm.WOLF.id(), PatronusForm.OWL.id()},
    };
    private static final int[] ALL = {
            PatronusForm.STAG.id(), PatronusForm.DOE.id(), PatronusForm.HARE.id(), PatronusForm.WOLF.id(),
            PatronusForm.CAT.id(), PatronusForm.OWL.id(), PatronusForm.FOX.id(), PatronusForm.PHOENIX.id(),
    };

    private PatronusForms() {
    }

    public static int[] pool(int houseIndex) {
        int[] source = houseIndex >= 0 && houseIndex < HOUSE_POOLS.length ? HOUSE_POOLS[houseIndex] : ALL;
        return source.clone();
    }

    public static int pick(long mostBits, long leastBits, int houseIndex) {
        int[] pool = houseIndex >= 0 && houseIndex < HOUSE_POOLS.length ? HOUSE_POOLS[houseIndex] : ALL;
        long hash = mix(mostBits ^ Long.rotateLeft(leastBits, 32));
        return pool[(int) Math.floorMod(hash, (long) pool.length)];
    }

    /** The 64-bit MurmurHash3 finaliser: spreads nearby UUIDs across the whole range. */
    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
```

`src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaRules.java`:
```java
package com.hogwartsmagic.spell.summon;

/** Serpensortia: more and longer-lived snakes with rank, Poison II from Expert, Parseltongue at Master. */
public final class SerpensortiaRules {
    public static final int MODE_FOLLOW = 0;
    public static final int MODE_GUARD = 1;
    public static final int POISON_TICKS = 60;
    public static final double COMMAND_RANGE = 24.0;
    public static final double GUARD_RADIUS = 8.0;
    public static final double GUARD_LEASH = 4.0;
    public static final int COMMAND_COOLDOWN_TICKS = 10;
    public static final double FOLLOW_START = 5.0;
    public static final double FOLLOW_STOP = 2.0;
    public static final double TELEPORT_DISTANCE = 16.0;
    public static final double SPAWN_SPACING = 0.8;
    public static final double DISMISS_RADIUS = 64.0;
    private static final int[] COUNT = {1, 2, 3, 3};
    private static final int[] LIFETIME = {400, 600, 800, 1200};
    private static final int[] POISON_AMPLIFIER = {0, 0, 1, 1};

    private SerpensortiaRules() {
    }

    public static int snakeCount(int rank) {
        return COUNT[RankIndex.of(rank)];
    }

    public static int lifetimeTicks(int rank) {
        return LIFETIME[RankIndex.of(rank)];
    }

    public static int poisonAmplifier(int rank) {
        return POISON_AMPLIFIER[RankIndex.of(rank)];
    }

    public static boolean parseltongue(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static int toggledMode(int mode) {
        return mode == MODE_FOLLOW ? MODE_GUARD : MODE_FOLLOW;
    }

    /** Sideways offset of snake {@code index} of {@code count}, centred on the impact. */
    public static double spawnOffset(int index, int count) {
        return (index - (count - 1) / 2.0) * SPAWN_SPACING;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/summon/FiendfyreRules.java`:
```java
package com.hogwartsmagic.spell.summon;

/**
 * Fiendfyre by rank: how long the held fire lasts, how many serpents answer, and how likely one is to
 * turn on its caster. A serpent that will turn glows red for TELEGRAPH_TICKS first (roadmap Decision 11).
 */
public final class FiendfyreRules {
    public static final int TELEGRAPH_TICKS = 20;
    public static final int MIN_TURN_TICKS = 30;
    public static final double ROGUE_SEARCH_RADIUS = 16.0;
    public static final int ROGUE_RETARGET_INTERVAL = 20;
    public static final int DISPATCH_TICKS = 80;
    public static final double DISPATCH_RANGE = 48.0;
    public static final int HORCRUX_REACH = 2;
    public static final int HORCRUX_SCAN_INTERVAL = 10;
    /** A rogue serpent rampages for at least this long, even when it turns late. */
    private static final int MIN_RAMPAGE_TICKS = 40;
    private static final int[] DURATION = {120, 160, 200, 240};
    private static final double[] ROGUE_CHANCE = {0.15, 0.08, 0.03, 0.0};
    private static final int[] SERPENTS = {1, 1, 2, 3};

    private FiendfyreRules() {
    }

    public static int durationTicks(int rank) {
        return DURATION[RankIndex.of(rank)];
    }

    public static double rogueChance(int rank) {
        return ROGUE_CHANCE[RankIndex.of(rank)];
    }

    public static boolean goesRogue(int rank, double roll) {
        return roll < rogueChance(rank);
    }

    public static int serpentCount(int rank) {
        return SERPENTS[RankIndex.of(rank)];
    }

    public static int rogueIndex(int count, double roll) {
        return Math.min(count - 1, Math.max(0, (int) (roll * count)));
    }

    /** Between MIN_TURN + TELEGRAPH (so the glow never starts at once) and duration − TELEGRAPH. */
    public static int rogueTurnTick(int durationTicks, double roll) {
        int earliest = MIN_TURN_TICKS + TELEGRAPH_TICKS;
        int latest = Math.max(earliest, durationTicks - TELEGRAPH_TICKS);
        int span = latest - earliest + 1;
        double r = Math.max(0.0, Math.min(roll, 0.999999));
        return earliest + (int) (r * span);
    }

    public static boolean telegraphing(int lifeTicks, int turnTick) {
        return turnTick >= 0 && lifeTicks >= turnTick - TELEGRAPH_TICKS && lifeTicks < turnTick;
    }

    public static int rogueLifeEnd(int durationTicks, int turnTick) {
        return Math.max(durationTicks, turnTick + MIN_RAMPAGE_TICKS);
    }

    public static boolean expired(long startedAt, long now, int rank) {
        return now - startedAt >= durationTicks(rank);
    }

    /** Sideways place in the formation, in blocks to the caster's right (negative is left). */
    public static double lateralOffset(int index, int count) {
        if (count <= 1) {
            return 0.0;
        }
        if (count == 2) {
            return index == 0 ? -1.6 : 1.6;
        }
        return (index - (count - 1) / 2.0) * 2.4;
    }

    public static boolean dispatches(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean destroysHorcrux(int rank) {
        return RankIndex.of(rank) == 3;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.summon.*"`
Expected: `BUILD SUCCESSFUL`, 18 tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/RankIndex.java src/main/java/com/hogwartsmagic/spell/summon/PatronusRules.java src/main/java/com/hogwartsmagic/spell/summon/PatronusForm.java src/main/java/com/hogwartsmagic/spell/summon/PatronusForms.java src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaRules.java src/main/java/com/hogwartsmagic/spell/summon/FiendfyreRules.java src/test/java/com/hogwartsmagic/spell/summon/PatronusRulesTest.java src/test/java/com/hogwartsmagic/spell/summon/PatronusFormsTest.java src/test/java/com/hogwartsmagic/spell/summon/SerpensortiaRulesTest.java src/test/java/com/hogwartsmagic/spell/summon/FiendfyreRulesTest.java
git commit -m "Add the tested per-rank rules for Patronus forms, Serpensortia snakes and Fiendfyre" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 2: Patronus by rank (Novice mist, Adept guardian, Master light and undead ward)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/PatronusEntity.java` (whole file replaced)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`CastSpellPacket.castAs` Patronus branches, `startPatronusShieldChannel`, `summonCorporealPatronus` removed)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`tickPatronusShieldChannel`, `stopPatronusShieldChannel`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `PatronusRules`, `PatronusForm` (Task 1); `SpellMasteryData.getRank`, `SpellCosts.manaCost/startCooldown` (Phase 1); `ModEvents.revealInvisibilityCloak(ServerPlayer)` (existing).
- Produces:
  - `PatronusSpell.TAG_SHIELD_STARTED = "hogwartsmagic:patronus_shield_started"`, `PatronusSpell.TAG_ACTIVE = "hogwartsmagic:patronus_active"`, `PatronusSpell.FORM_KEY = "PatronusForm"`
  - `PatronusSpell.summonCorporeal(ServerPlayer player, int rank)` → `PatronusEntity`
  - `PatronusEntity.configure(@Nullable UUID owner, int lifespanTicks, int formId, boolean masterAura)`, `PatronusEntity.getFormId()` → `int` (synced, safe on the client), `PatronusEntity.getOwnerUuid()` → `@Nullable UUID`, `PatronusEntity.hasAura()` → `boolean`

- [ ] **Step 1: Create `PatronusSpell`**

`src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java`:
```java
package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The corporeal half of Expecto Patronum (Adept and up). One Patronus per caster: a new summon replaces
 * the last. Server only.
 */
public final class PatronusSpell {
    /** Player persistent data: game time the Novice mist went up (it lasts SHIELD_MAX_TICKS). */
    public static final String TAG_SHIELD_STARTED = "hogwartsmagic:patronus_shield_started";
    /** Player persistent data: UUID of the caster's current corporeal Patronus. */
    public static final String TAG_ACTIVE = "hogwartsmagic:patronus_active";
    /** PlayerPersisted/hogwartsmagic: the caster's personal form id, chosen once. */
    public static final String FORM_KEY = "PatronusForm";

    private PatronusSpell() {
    }

    public static PatronusEntity summonCorporeal(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        if (data.hasUUID(TAG_ACTIVE) && level.getEntity(data.getUUID(TAG_ACTIVE)) instanceof PatronusEntity previous) {
            previous.discard(); // its remove() clears any light it placed
        }
        PatronusForm form = PatronusForm.GENERIC;
        PatronusEntity patronus = new PatronusEntity(ModEntities.PATRONUS.get(), level);
        patronus.configure(player.getUUID(), PatronusRules.lifespanTicks(rank), form.id(), PatronusRules.masterAura(rank));
        patronus.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        level.addFreshEntity(patronus);
        data.putUUID(TAG_ACTIVE, patronus.getUUID());

        ModEvents.revealInvisibilityCloak(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SpellType.EXPECTO_PATRONUM.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
        return patronus;
    }
}
```

- [ ] **Step 2: Replace `PatronusEntity`**

Replace the whole content of `src/main/java/com/hogwartsmagic/entity/PatronusEntity.java` with:
```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.summon.PatronusRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A corporeal Patronus. It hunts Dementors (a growing burn aura and a pushing wavefront) and otherwise
 * stays near the caster who summoned it. Spell Mastery Phase 7a: the lifespan comes from the caster's
 * rank, a synced form id tells the renderer which animal to draw, and only a Master's Patronus lights
 * the dark (follow light and light ring) and drives the undead away.
 */
public class PatronusEntity extends PathfinderMob {
    private static final EntityDataAccessor<Byte> DATA_FORM =
            SynchedEntityData.defineId(PatronusEntity.class, EntityDataSerializers.BYTE);

    /** 30 seconds and lit, for a Patronus saved before Phase 7a or summoned by command. */
    private int lifespan = 600;
    private boolean aura = true;
    @Nullable
    private UUID ownerUuid;
    @Nullable
    private BlockPos lastLightPos = null;
    private final Map<BlockPos, Integer> pulseLightBlocks = new HashMap<>();

    public PatronusEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
        this.getTags().add("patronus");
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FORM, (byte) 0);
    }

    /** Set once, right after construction and before the Patronus is added to the level. */
    public void configure(@Nullable UUID owner, int lifespanTicks, int formId, boolean masterAura) {
        this.ownerUuid = owner;
        this.lifespan = Math.max(1, lifespanTicks);
        this.aura = masterAura;
        this.entityData.set(DATA_FORM, (byte) formId);
    }

    /** The PatronusForm id; synced, so the renderer reads it on the client. */
    public int getFormId() {
        return this.entityData.get(DATA_FORM);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public boolean hasAura() {
        return this.aura;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.PATRONUS_AMBIENT.get();
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.FLYING_SPEED, 0.8D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 20.0D) // High damage to Dementors
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        // Hunt down Dementors!
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, DementorEntity.class, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(BlockPos pos) {
                return true;
            }
        };
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifespan", this.lifespan);
        tag.putBoolean("Aura", this.aura);
        tag.putByte("Form", this.entityData.get(DATA_FORM));
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        if (this.lastLightPos != null) {
            tag.putLong("LightPos", this.lastLightPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Lifespan")) {
            this.lifespan = Math.max(1, tag.getInt("Lifespan"));
        }
        if (tag.contains("Aura")) {
            this.aura = tag.getBoolean("Aura");
        }
        if (tag.contains("Form")) {
            this.entityData.set(DATA_FORM, tag.getByte("Form"));
        }
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // Remembered so the light it left behind is cleared after a reload instead of staying forever.
        this.lastLightPos = tag.contains("LightPos") ? BlockPos.of(tag.getLong("LightPos")) : null;
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        this.lifespan--;
        if (this.lifespan <= 0) {
            this.cleanupLight();
            this.cleanupPulseLights();
            this.discard();
            return;
        }

        if (this.aura) {
            this.updateLightBlock();
            this.tickPulseLights();
            if (this.tickCount % PatronusRules.UNDEAD_INTERVAL == 0) {
                this.repelUndead(serverLevel);
            }
        }

        // Radiate light/damage to nearby dementors. The damage radius grows over time.
        double auraRadius = Math.min(32.0D, 8.0D + (this.tickCount * 0.15D));
        if (this.tickCount % 10 == 0) {
            List<DementorEntity> dementors = this.level().getEntitiesOfClass(DementorEntity.class, this.getBoundingBox().inflate(auraRadius));
            for (DementorEntity d : dementors) {
                d.hurt(this.damageSources().magic(), 10.0F); // Burns dementors
            }
        }

        // Pulse ring extends farther every pulse. The flash of light is a Master's; the push is every Patronus's.
        if (this.tickCount % 40 == 0) {
            int pulseRadius = (int) Math.ceil(Math.min(30.0D, 8.0D + (this.tickCount * 0.15D)));
            if (this.aura) {
                this.createPulseRingAtRadius(pulseRadius);
            }
            this.pushDementorsNearRing(pulseRadius);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2.0F, 1.5F);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide) {
            this.cleanupLight();
            this.cleanupPulseLights();
        }
        super.remove(reason);
    }

    @Nullable
    private Player owner() {
        return this.ownerUuid == null ? null : this.level().getPlayerByUUID(this.ownerUuid);
    }

    /** Jumps beside the owner, but only into open air; returns false if no free spot was found. */
    private boolean teleportNear(Player owner) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double x = owner.getX() + (this.random.nextDouble() - 0.5D) * 4.0D;
            double y = owner.getY() + 1.0D + this.random.nextDouble();
            double z = owner.getZ() + (this.random.nextDouble() - 0.5D) * 4.0D;
            AABB box = this.getBoundingBox().move(x - this.getX(), y - this.getY(), z - this.getZ());
            if (this.level().noCollision(this, box)) {
                this.moveTo(x, y, z, this.getYRot(), this.getXRot());
                this.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    /** Master only: undead within UNDEAD_RADIUS are shoved away and lose interest in the caster. */
    private void repelUndead(ServerLevel level) {
        double radius = PatronusRules.UNDEAD_RADIUS;
        AABB box = this.getBoundingBox().inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getType().is(EntityTypeTags.UNDEAD))) {
            double dx = mob.getX() - this.getX();
            double dz = mob.getZ() - this.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > radius) {
                continue;
            }
            Vec3 away = horizontal > 1.0E-3D
                    ? new Vec3(dx / horizontal, 0.0D, dz / horizontal)
                    : new Vec3(this.random.nextDouble() - 0.5D, 0.0D, this.random.nextDouble() - 0.5D).normalize();
            mob.setDeltaMovement(mob.getDeltaMovement().add(away.x * 0.5D, 0.15D, away.z * 0.5D));
            mob.hurtMarked = true;
            mob.getNavigation().stop();
            if (mob.getTarget() != null && this.ownerUuid != null && this.ownerUuid.equals(mob.getTarget().getUUID())) {
                mob.setTarget(null);
            }
            level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D, mob.getZ(),
                    3, 0.25D, 0.35D, 0.25D, 0.02D);
        }
    }

    private void updateLightBlock() {
        BlockPos currentPos = this.blockPosition();

        // If we moved to a new block
        if (this.lastLightPos == null || !this.lastLightPos.equals(currentPos)) {
            // Remove old light
            this.cleanupLight();

            // Try to place new light if the block is air
            if (this.level().getBlockState(currentPos).isAir()) {
                this.level().setBlock(currentPos, ExampleMod.PATRONUS_LIGHT_BLOCK.get().defaultBlockState(), 3);
                this.lastLightPos = currentPos;
            }
        }
    }

    private void cleanupLight() {
        if (this.lastLightPos != null) {
            if (this.level().getBlockState(this.lastLightPos).is(ExampleMod.PATRONUS_LIGHT_BLOCK.get())) {
                // Restore air
                this.level().removeBlock(this.lastLightPos, false);
            }
            this.lastLightPos = null;
        }
    }

    private void createPulseRingAtRadius(int radius) {
        BlockPos center = this.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt((x * x) + (z * z));
                    if (distance > radius - 0.6D && distance < radius + 0.6D) {
                        BlockPos p = center.offset(x, y, z);
                        if (this.level().getBlockState(p).isAir()) {
                            this.level().setBlock(p, ExampleMod.PATRONUS_LIGHT_BLOCK.get().defaultBlockState(), 3);
                            this.pulseLightBlocks.put(p, 6); // brief flash on moving ring only
                        }
                    }
                }
            }
        }
    }

    private void pushDementorsNearRing(int radius) {
        List<DementorEntity> dementors = this.level().getEntitiesOfClass(DementorEntity.class, this.getBoundingBox().inflate(radius + 2.0D));
        for (DementorEntity d : dementors) {
            double dx = d.getX() - this.getX();
            double dz = d.getZ() - this.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist < radius - 1.0D || horizontalDist > radius + 1.0D) {
                continue; // only affect the outer wavefront ring
            }
            if (horizontalDist > 0.1D) {
                double force = Math.min(2.8D, 1.0D + (this.tickCount * 0.008D));
                d.setDeltaMovement(d.getDeltaMovement().add((dx / horizontalDist) * force, 0.28D, (dz / horizontalDist) * force));
                d.hurtMarked = true;
            }
        }
    }

    private void tickPulseLights() {
        Iterator<Map.Entry<BlockPos, Integer>> it = this.pulseLightBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                BlockPos pos = entry.getKey();
                if (this.level().getBlockState(pos).is(ExampleMod.PATRONUS_LIGHT_BLOCK.get())) {
                    this.level().removeBlock(pos, false);
                }
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }
    }

    private void cleanupPulseLights() {
        for (BlockPos pos : this.pulseLightBlocks.keySet()) {
            if (this.level().getBlockState(pos).is(ExampleMod.PATRONUS_LIGHT_BLOCK.get())) {
                this.level().removeBlock(pos, false);
            }
        }
        this.pulseLightBlocks.clear();
    }

    /** Idle Patronuses stay with their caster; a hunting one (it has a target) is left to hunt. */
    static final class FollowOwnerGoal extends Goal {
        private final PatronusEntity patronus;
        private int recalc;

        FollowOwnerGoal(PatronusEntity patronus) {
            this.patronus = patronus;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.patronus.getTarget() != null) {
                return false;
            }
            Player owner = this.patronus.owner();
            return owner != null && owner.isAlive() && !owner.isSpectator()
                    && this.patronus.distanceToSqr(owner) > PatronusRules.FOLLOW_START * PatronusRules.FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            Player owner = this.patronus.owner();
            return this.patronus.getTarget() == null && owner != null && owner.isAlive()
                    && this.patronus.distanceToSqr(owner) > PatronusRules.FOLLOW_STOP * PatronusRules.FOLLOW_STOP;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.patronus.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player owner = this.patronus.owner();
            if (owner == null) {
                return;
            }
            this.patronus.getLookControl().setLookAt(owner, 10.0F, this.patronus.getMaxHeadXRot());
            if (--this.recalc > 0) {
                return;
            }
            this.recalc = this.adjustedTickDelay(10);
            if (this.patronus.distanceToSqr(owner) > PatronusRules.TELEPORT_DISTANCE * PatronusRules.TELEPORT_DISTANCE) {
                this.patronus.teleportNear(owner);
            } else {
                this.patronus.getNavigation().moveTo(owner.getX(), owner.getY() + 1.5D, owner.getZ(), 1.2D);
            }
        }
    }
}
```

- [ ] **Step 3: Gate the cast by rank.** In `SpellPackets.java`, inside `CastSpellPacket`:

Delete these two lines:
```java
        /** How often the charm answers in full rather than as a bare shield of light. */
        private static final float CORPOREAL_PATRONUS_CHANCE = 0.15f;
```

In `castAs`, in the `if (p.channelHeartbeat && spell == SpellType.EXPECTO_PATRONUM) {` block, replace:
```java
                if (player.getRandom().nextFloat() < CORPOREAL_PATRONUS_CHANCE) {
                    int fullCost = SpellCosts.manaCost(player, wand, spell);
```
with:
```java
                // Spell Mastery Phase 7a: a Novice only manages the mist; from Adept the charm always answers in full.
                int patronusRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell);
                if (com.hogwartsmagic.spell.summon.PatronusRules.isCorporeal(patronusRank)) {
                    int fullCost = SpellCosts.manaCost(player, wand, spell);
```
In the same block, replace:
```java
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    summonCorporealPatronus(player);
                    if (!creative) {
                        SpellCosts.startCooldown(player, wand, spell, now);
                    }
                    return;
                }
```
with:
```java
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    com.hogwartsmagic.spell.summon.PatronusSpell.summonCorporeal(player, patronusRank);
                    if (!creative) {
                        SpellCosts.startCooldown(player, wand, spell, now);
                    }
                    return;
                }
```
Replace the plain-cast branch:
```java
            if (spell == SpellType.EXPECTO_PATRONUM) {
                // Full mana was already taken above, so either outcome is paid for here.
                if (player.getRandom().nextFloat() < CORPOREAL_PATRONUS_CHANCE) {
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    summonCorporealPatronus(player);
                    if (!creative) {
                        SpellCosts.startCooldown(player, wand, spell, now);
                    }
                } else {
                    startPatronusShieldChannel(player, now);
                }
                return;
            }
```
with:
```java
            if (spell == SpellType.EXPECTO_PATRONUM) {
                // Full mana was already taken above, so either outcome is paid for here.
                int patronusRank = com.hogwartsmagic.spell.mastery.SpellMasteryData.getRank(player, spell);
                if (com.hogwartsmagic.spell.summon.PatronusRules.isCorporeal(patronusRank)) {
                    playerData.putLong(TAG_PATRONUS_CAST_SPENT, now);
                    com.hogwartsmagic.spell.summon.PatronusSpell.summonCorporeal(player, patronusRank);
                    if (!creative) {
                        SpellCosts.startCooldown(player, wand, spell, now);
                    }
                } else {
                    startPatronusShieldChannel(player, now);
                }
                return;
            }
```
In `startPatronusShieldChannel`, replace:
```java
            playerData.putLong(TAG_PATRONUS_SHIELD_LAST_SEEN, now);

            // Everyone who can see the caster needs to draw the light, not just the caster.
```
with:
```java
            playerData.putLong(TAG_PATRONUS_SHIELD_LAST_SEEN, now);
            playerData.putLong(com.hogwartsmagic.spell.summon.PatronusSpell.TAG_SHIELD_STARTED, now);

            // Everyone who can see the caster needs to draw the light, not just the caster.
```
Delete the whole method, from its Javadoc line to its closing brace:
```java
        /** The charm answering in full: a corporeal stag that hunts Dementors down. */
        private static void summonCorporealPatronus(ServerPlayer player) {
            com.hogwartsmagic.entity.PatronusEntity patronus = new com.hogwartsmagic.entity.PatronusEntity(
                    ModEntities.PATRONUS.get(), player.level());
            patronus.setPos(player.getX(), player.getY(), player.getZ());
            player.level().addFreshEntity(patronus);

            com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SpellType.EXPECTO_PATRONUM.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }

```
Run: `grep -n "CORPOREAL_PATRONUS_CHANCE\|summonCorporealPatronus" src/main/java/com/hogwartsmagic/network/SpellPackets.java`
Expected: no output.

- [ ] **Step 4: The mist lasts four seconds.** In `ModEvents.java`, in `tickPatronusShieldChannel`, replace:
```java
        long gameTime = player.level().getGameTime();
        if (gameTime - player.getPersistentData().getLong(TAG_PATRONUS_SHIELD_LAST_SEEN) > PATRONUS_SHIELD_HEARTBEAT_TIMEOUT) {
            stopPatronusShieldChannel(player, wand);
            return;
        }
```
with:
```java
        long gameTime = player.level().getGameTime();
        if (gameTime - player.getPersistentData().getLong(TAG_PATRONUS_SHIELD_LAST_SEEN) > PATRONUS_SHIELD_HEARTBEAT_TIMEOUT) {
            stopPatronusShieldChannel(player, wand);
            return;
        }
        // Spell Mastery Phase 7a: the Novice mist holds for four seconds, then gutters out.
        if (com.hogwartsmagic.spell.summon.PatronusRules.shieldExpired(
                player.getPersistentData().getLong(com.hogwartsmagic.spell.summon.PatronusSpell.TAG_SHIELD_STARTED), gameTime)) {
            stopPatronusShieldChannel(player, wand);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.patronus.mist_fades")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            return;
        }
```
In `stopPatronusShieldChannel`, replace:
```java
        data.remove(TAG_PATRONUS_SHIELD_LAST_SEEN);

        SpellPackets.CHANNEL.send(new SpellPackets.PatronusShieldStatePacket(player.getId(), false),
```
with:
```java
        data.remove(TAG_PATRONUS_SHIELD_LAST_SEEN);
        data.remove(com.hogwartsmagic.spell.summon.PatronusSpell.TAG_SHIELD_STARTED);

        SpellPackets.CHANNEL.send(new SpellPackets.PatronusShieldStatePacket(player.getId(), false),
```

- [ ] **Step 5: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.expecto_patronum.desc": "Hold to throw a shield of silver light that holds Dementors back while it drains your mana. Rarely, the charm answers in full and a corporeal Patronus takes the field.",
```
with:
```json
  "spell.hogwartsmagic.expecto_patronum.desc": "Novice: hold for a silver mist that holds Dementors back for up to 4s. Adept: a corporeal Patronus hunts Dementors for 10s. Expert: it takes your own form for 20s. Master: 30s, it lights the dark, drives the undead away and can carry a message (/patronus <player> <message>).",
  "message.hogwartsmagic.patronus.mist_fades": "The silver mist fades",
```

- [ ] **Step 6: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (` is reached; no stack trace mentioning `PatronusEntity`, `PatronusSpell` or any `client` class (the synced byte registers on the server).

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java src/main/java/com/hogwartsmagic/entity/PatronusEntity.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Expecto Patronum a Novice mist, an Adept guardian and a lit Master ward against the undead" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The caster's own Patronus form (Expert)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/client/model/PatronusModel.java`
- Modify: `src/main/java/com/hogwartsmagic/client/renderer/PatronusRenderer.java` (whole file replaced)
- Create: `src/main/java/com/hogwartsmagic/client/renderer/PatronusFormDummies.java`
- Create: `build_scripts/generate_patronus_form_texture.py`, `src/main/resources/assets/hogwartsmagic/textures/entity/patronus_form.png` (generated)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `PatronusForms.pick`, `PatronusForm`, `PatronusRules.personalForm` (Task 1); `PatronusEntity.getFormId()` (Task 2); `HousePerks.getHouse(Player)` → `@Nullable HogwartsHouse`, `PlayerMagicData.getModData/saveModData` (existing).
- Produces:
  - `PatronusSpell.formFor(ServerPlayer)` → `PatronusForm` (chooses and stores on first use; tells the player)
  - `PatronusModel.setAntlersVisible(boolean)`

- [ ] **Step 1: Choose and keep the form.** In `PatronusSpell.java`, replace:
```java
        PatronusForm form = PatronusForm.GENERIC;
```
with:
```java
        PatronusForm form = PatronusRules.personalForm(rank) ? formFor(player) : PatronusForm.GENERIC;
```
Directly above the class's closing brace (the last `}` in the file), add:
```java

    /**
     * The caster's personal form: picked from their UUID and house the first time it is needed, then
     * stored in PlayerPersisted (it survives death) and never re-rolled, even if the house changes.
     */
    public static PatronusForm formFor(ServerPlayer player) {
        net.minecraft.nbt.CompoundTag modData = com.hogwartsmagic.util.PlayerMagicData.getModData(player);
        if (modData.contains(FORM_KEY)) {
            PatronusForm stored = PatronusForm.byId(modData.getInt(FORM_KEY));
            if (stored != PatronusForm.GENERIC) {
                return stored;
            }
        }
        com.hogwartsmagic.sorting.HogwartsHouse house = com.hogwartsmagic.sorting.HousePerks.getHouse(player);
        java.util.UUID id = player.getUUID();
        PatronusForm form = PatronusForm.byId(PatronusForms.pick(id.getMostSignificantBits(), id.getLeastSignificantBits(),
                house == null ? -1 : house.ordinal()));
        modData.putInt(FORM_KEY, form.id());
        com.hogwartsmagic.util.PlayerMagicData.saveModData(player, modData);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.hogwartsmagic.patronus.form",
                net.minecraft.network.chat.Component.translatable(form.langKey()))
                .withStyle(net.minecraft.ChatFormatting.AQUA), false);
        return form;
    }
```

- [ ] **Step 2: The doe is the stag without antlers.** In `PatronusModel.java`, directly above:
```java
    @Override
    public void setupAnim(PatronusEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
```
add:
```java
    /** The Doe form reuses the stag with its antlers hidden; the renderer restores them after drawing. */
    public void setAntlersVisible(boolean visible) {
        this.head.getChild("antlers").visible = visible;
    }

```

- [ ] **Step 3: Stand-in entities for the typed vanilla models**

`src/main/java/com/hogwartsmagic/client/renderer/PatronusFormDummies.java`:
```java
package com.hogwartsmagic.client.renderer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla's wolf, fox, rabbit and parrot models (and the mod's owl model) are typed to their own entity
 * and read entity state in setupAnim, so they can't be handed a PatronusEntity. Each gets one idle,
 * never-added stand-in of its own type per client level, created on first use; the Patronus supplies
 * the pose numbers (limb swing, head angles, age) and the stand-in only answers the type's getters with
 * defaults. Dropped whenever the client level changes.
 */
final class PatronusFormDummies {
    @Nullable
    private Level level;
    private final Map<EntityType<?>, Entity> cache = new HashMap<>();

    @Nullable
    <T extends Entity> T get(EntityType<T> type, Level current) {
        if (current != this.level) {
            this.cache.clear();
            this.level = current;
        }
        Entity entity = this.cache.get(type);
        if (entity == null) {
            entity = type.create(current);
            if (entity == null) {
                return null;
            }
            this.cache.put(type, entity);
        }
        return type.tryCast(entity);
    }
}
```

- [ ] **Step 4: Draw the forms.** Replace the whole content of `src/main/java/com/hogwartsmagic/client/renderer/PatronusRenderer.java` with:
```java
package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.OwlModel;
import com.hogwartsmagic.client.model.PatronusModel;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.OwlEntity;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.spell.summon.PatronusForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.OcelotModel;
import net.minecraft.client.model.ParrotModel;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;

/**
 * Draws a corporeal Patronus. The generic guardian, the Stag and the Doe use the mod's stag model and
 * texture (the Doe hides the antlers). The other personal forms (Spell Mastery Phase 7a) reuse vanilla
 * animal models and the mod's owl model, all drawn with one silver texture through the translucent
 * emissive render type and a pale translucent tint, so every form reads as the same ghostly light.
 * Client only; forms are compared with ==, never switched on.
 */
public class PatronusRenderer extends MobRenderer<PatronusEntity, PatronusModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/patronus.png");
    private static final ResourceLocation FORM_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/patronus_form.png");
    /** Pale silver-blue at about two-thirds opacity. */
    private static final int FORM_TINT = FastColor.ARGB32.color(176, 214, 236, 255);

    private final WolfModel<Wolf> wolfModel;
    private final FoxModel<Fox> foxModel;
    private final RabbitModel<Rabbit> rabbitModel;
    private final OcelotModel<PatronusEntity> catModel;
    private final ParrotModel parrotModel;
    private final OwlModel<OwlEntity> owlModel;
    private final PatronusFormDummies dummies = new PatronusFormDummies();

    public PatronusRenderer(EntityRendererProvider.Context context) {
        super(context, new PatronusModel(context.bakeLayer(PatronusModel.LAYER_LOCATION)), 0.0F); // No shadow for light spirits
        this.wolfModel = new WolfModel<>(context.bakeLayer(ModelLayers.WOLF));
        this.foxModel = new FoxModel<>(context.bakeLayer(ModelLayers.FOX));
        this.rabbitModel = new RabbitModel<>(context.bakeLayer(ModelLayers.RABBIT));
        this.catModel = new OcelotModel<>(context.bakeLayer(ModelLayers.OCELOT));
        this.parrotModel = new ParrotModel(context.bakeLayer(ModelLayers.PARROT));
        this.owlModel = new OwlModel<>(context.bakeLayer(OwlModel.LAYER_LOCATION));
    }

    @Override
    public void render(PatronusEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PatronusForm form = PatronusForm.byId(entity.getFormId());
        if (form == PatronusForm.GENERIC || form == PatronusForm.STAG || form == PatronusForm.DOE) {
            this.model.setAntlersVisible(form != PatronusForm.DOE);
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            this.model.setAntlersVisible(true);
            return;
        }
        this.renderForm(entity, form, partialTicks, poseStack, buffer);
    }

    @Override
    protected void scale(PatronusEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(1.2F, 1.2F, 1.2F); // Majestic size
        super.scale(entity, poseStack, partialTickTime);
    }

    @Override
    protected RenderType getRenderType(PatronusEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // Force the translucent emissive (glowing) render type for the celestial look
        return RenderType.entityTranslucentEmissive(getTextureLocation(entity));
    }

    @Override
    public ResourceLocation getTextureLocation(PatronusEntity entity) {
        return TEXTURE;
    }

    /** The same body transform LivingEntityRenderer applies, then the form's model in silver light. */
    private void renderForm(PatronusEntity entity, PatronusForm form, float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        Level level = entity.level();
        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        float age = entity.tickCount + partialTicks;
        float limbSwing = entity.walkAnimation.position(partialTicks);
        float limbAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
        float scale = formScale(form);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(FORM_TEXTURE));
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        if (form == PatronusForm.CAT) {
            this.catModel.young = false;
            this.catModel.prepareMobModel(entity, limbSwing, limbAmount, partialTicks);
            this.catModel.setupAnim(entity, limbSwing, limbAmount, age, netHeadYaw, headPitch);
            this.catModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
        } else if (form == PatronusForm.WOLF) {
            Wolf wolf = this.dummies.get(EntityType.WOLF, level);
            if (wolf != null) {
                this.wolfModel.young = false;
                this.wolfModel.prepareMobModel(wolf, limbSwing, limbAmount, partialTicks);
                this.wolfModel.setupAnim(wolf, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.wolfModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.FOX) {
            Fox fox = this.dummies.get(EntityType.FOX, level);
            if (fox != null) {
                this.foxModel.young = false;
                this.foxModel.prepareMobModel(fox, limbSwing, limbAmount, partialTicks);
                this.foxModel.setupAnim(fox, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.foxModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.HARE) {
            Rabbit rabbit = this.dummies.get(EntityType.RABBIT, level);
            if (rabbit != null) {
                this.rabbitModel.young = false;
                this.rabbitModel.prepareMobModel(rabbit, limbSwing, limbAmount, partialTicks);
                this.rabbitModel.setupAnim(rabbit, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.rabbitModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.PHOENIX) {
            Parrot parrot = this.dummies.get(EntityType.PARROT, level);
            if (parrot != null) {
                this.parrotModel.young = false;
                this.parrotModel.prepareMobModel(parrot, limbSwing, limbAmount, partialTicks);
                this.parrotModel.setupAnim(parrot, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.parrotModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        } else if (form == PatronusForm.OWL) {
            OwlEntity owl = this.dummies.get(ModEntities.OWL.get(), level);
            if (owl != null) {
                this.owlModel.young = false;
                this.owlModel.prepareMobModel(owl, limbSwing, limbAmount, partialTicks);
                this.owlModel.setupAnim(owl, limbSwing, limbAmount, age, netHeadYaw, headPitch);
                this.owlModel.renderToBuffer(poseStack, consumer, light, overlay, FORM_TINT);
            }
        }
        poseStack.popPose();
    }

    /** Small animals are drawn larger so a Patronus still reads as a guardian. */
    private static float formScale(PatronusForm form) {
        if (form == PatronusForm.HARE) {
            return 1.6F;
        }
        if (form == PatronusForm.CAT) {
            return 1.2F;
        }
        if (form == PatronusForm.OWL) {
            return 1.4F;
        }
        if (form == PatronusForm.FOX) {
            return 1.1F;
        }
        if (form == PatronusForm.PHOENIX) {
            return 1.8F;
        }
        return 1.0F;
    }
}
```

- [ ] **Step 5: The silver texture**

`build_scripts/generate_patronus_form_texture.py`:
```python
"""Paint the silver texture every personal Patronus form is drawn with.

    python build_scripts/generate_patronus_form_texture.py

Writes assets/hogwartsmagic/textures/entity/patronus_form.png (64x64).

The forms reuse vanilla animal models whose UV layouts all differ, so the texture is deliberately
uniform: a near-white silver with a faint cold mottle and a soft vertical sheen. Any face of any model
lands on the same kind of light, and the renderer's tint and translucency do the rest.
"""

import os
import random

from PIL import Image

SIZE = 64
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "entity", "patronus_form.png")


def main():
    rng = random.Random(7)
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    for y in range(SIZE):
        sheen = 0.88 + 0.12 * (1.0 - abs((y % 16) - 8) / 8.0)
        for x in range(SIZE):
            mottle = rng.uniform(-0.06, 0.04)
            level = max(0.0, min(1.0, sheen + mottle))
            r = int(205 * level)
            g = int(232 * level)
            b = int(255 * level)
            pixels[x, y] = (r, g, b, 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
```
Run: `python build_scripts/generate_patronus_form_texture.py`
Expected: `wrote ...patronus_form.png`.

- [ ] **Step 6: Strings.** In `en_us.json`, directly after the line `  "message.hogwartsmagic.patronus.mist_fades": "The silver mist fades",` insert:
```json
  "message.hogwartsmagic.patronus.form": "Your Patronus takes its true form: %s.",
  "patronus.hogwartsmagic.form.generic": "Silver Guardian",
  "patronus.hogwartsmagic.form.stag": "Stag",
  "patronus.hogwartsmagic.form.doe": "Doe",
  "patronus.hogwartsmagic.form.hare": "Hare",
  "patronus.hogwartsmagic.form.wolf": "Wolf",
  "patronus.hogwartsmagic.form.cat": "Cat",
  "patronus.hogwartsmagic.form.owl": "Owl",
  "patronus.hogwartsmagic.form.fox": "Fox",
  "patronus.hogwartsmagic.form.phoenix": "Phoenix",
```

- [ ] **Step 7: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `PatronusRenderer`, `PatronusFormDummies` or any `client` class (the renderer is only referenced from the `Dist.CLIENT` `ClientModEvents`).

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java src/main/java/com/hogwartsmagic/client/model/PatronusModel.java src/main/java/com/hogwartsmagic/client/renderer/PatronusRenderer.java src/main/java/com/hogwartsmagic/client/renderer/PatronusFormDummies.java build_scripts/generate_patronus_form_texture.py src/main/resources/assets/hogwartsmagic/textures/entity/patronus_form.png src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Give an Expert Patronus the caster's own animal form, chosen once from UUID and house" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The Master's Patronus messenger

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/PatronusMessenger.java`
- Create: `src/main/java/com/hogwartsmagic/command/PatronusCommand.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java` (`setForm`)
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (`onRegisterCommands`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `PatronusRules` (Task 1); `PatronusSpell.formFor` (Task 3); `PatronusEntity.configure` (Task 2); `SupportMana.trySpend` (Phase 5a); `CombatEffects.isCastingBlocked/notifyCastBlocked` (Phase 2); `SpellMasteryData.getRank`, `SpellCosts.manaCost` (Phase 1); `MagicWandItem.isAnyWand`, `MagicBookItem.isSpellLearned`, `ModEvents.revealInvisibilityCloak`, `ModSounds.PATRONUS_AMBIENT` (existing).
- Produces: `PatronusMessenger.send(ServerPlayer sender, ServerPlayer recipient, String rawText)` → `boolean`; `PatronusSpell.setForm(ServerPlayer, PatronusForm)` (testing aid); `PatronusCommand.register(CommandDispatcher<CommandSourceStack>)` (`/patronus <player> <message>` for everyone, `/patronus form <1-8>` for ops)

- [ ] **Step 1: The messenger**

`src/main/java/com/hogwartsmagic/spell/summon/PatronusMessenger.java`:
```java
package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.PatronusEntity;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.spell.support.SupportMana;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A Master of Expecto Patronum can send a message by Patronus to a player up to 500 blocks away in the
 * same dimension. The caster's Patronus leaves in a burst of light and a messenger Patronus in the
 * caster's form appears beside the recipient for five seconds while the message is spoken in chat. It
 * costs the spell's mana and has its own 30-second cooldown (the combat cooldown is untouched). Server
 * only; the text is sanitised and attributed to the sender.
 */
public final class PatronusMessenger {
    private static final String TAG_LAST_SENT = "hogwartsmagic:patronus_message_at";

    private PatronusMessenger() {
    }

    public static boolean send(ServerPlayer sender, ServerPlayer recipient, String rawText) {
        SpellType spell = SpellType.EXPECTO_PATRONUM;
        ItemStack wand = sender.getMainHandItem();
        if (!MagicWandItem.isAnyWand(wand)) {
            fail(sender, "message.hogwartsmagic.patronus.need_wand");
            return false;
        }
        if (!MagicBookItem.isSpellLearned(sender, spell)
                || !PatronusRules.sendsMessages(SpellMasteryData.getRank(sender, spell))) {
            fail(sender, "message.hogwartsmagic.patronus.need_master");
            return false;
        }
        if (CombatEffects.isCastingBlocked(sender)) {
            CombatEffects.notifyCastBlocked(sender);
            return false;
        }
        if (recipient == sender) {
            fail(sender, "message.hogwartsmagic.patronus.self");
            return false;
        }
        if (recipient.level() != sender.level() || !PatronusRules.inMessageRange(recipient.distanceToSqr(sender))) {
            fail(sender, "message.hogwartsmagic.patronus.too_far", recipient.getDisplayName());
            return false;
        }
        String text = PatronusRules.sanitizeMessage(rawText);
        if (text.isEmpty()) {
            fail(sender, "message.hogwartsmagic.patronus.empty");
            return false;
        }
        CompoundTag data = sender.getPersistentData();
        long now = sender.level().getGameTime();
        long left = sender.isCreative() ? 0L
                : PatronusRules.messageCooldownLeft(data.contains(TAG_LAST_SENT), data.getLong(TAG_LAST_SENT), now);
        if (left > 0L) {
            fail(sender, "message.hogwartsmagic.patronus.cooldown", (left + 19L) / 20L);
            return false;
        }
        if (!SupportMana.trySpend(sender, SpellCosts.manaCost(sender, wand, spell))) {
            sender.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return false;
        }
        data.putLong(TAG_LAST_SENT, now);

        departFrom(sender);
        arriveAt(recipient, sender, text);
        sender.sendSystemMessage(Component.translatable("message.hogwartsmagic.patronus.sent", recipient.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    private static void departFrom(ServerPlayer sender) {
        ServerLevel level = sender.serverLevel();
        ModEvents.revealInvisibilityCloak(sender);
        level.sendParticles(ParticleTypes.END_ROD, sender.getX(), sender.getY() + 1.2D, sender.getZ(),
                30, 0.6D, 0.8D, 0.6D, 0.08D);
        level.playSound(null, sender.getX(), sender.getY(), sender.getZ(),
                SpellType.EXPECTO_PATRONUM.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** The messenger appears in front of the recipient, but only in open air; the words arrive either way. */
    private static void arriveAt(ServerPlayer recipient, ServerPlayer sender, String text) {
        ServerLevel level = recipient.serverLevel();
        PatronusEntity messenger = new PatronusEntity(ModEntities.PATRONUS.get(), level);
        messenger.configure(recipient.getUUID(), PatronusRules.MESSENGER_TICKS, PatronusSpell.formFor(sender).id(), false);
        Vec3 look = recipient.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        boolean placed = false;
        for (double distance : new double[]{2.0D, 1.5D, 1.0D}) {
            Vec3 at = recipient.position().add(flat.scale(distance)).add(0.0D, 0.5D, 0.0D);
            messenger.moveTo(at.x, at.y, at.z, recipient.getYRot() + 180.0F, 0.0F);
            if (level.noCollision(messenger)) {
                placed = true;
                break;
            }
        }
        if (placed) {
            level.addFreshEntity(messenger);
            level.sendParticles(ParticleTypes.END_ROD, messenger.getX(), messenger.getY() + 0.8D, messenger.getZ(),
                    24, 0.5D, 0.6D, 0.5D, 0.05D);
        }
        level.playSound(null, recipient.getX(), recipient.getY(), recipient.getZ(),
                ModSounds.PATRONUS_AMBIENT.get(), SoundSource.PLAYERS, 0.9f, 1.2f);
        recipient.sendSystemMessage(Component.translatable("message.hogwartsmagic.patronus.delivered",
                sender.getDisplayName(), Component.literal(text)).withStyle(ChatFormatting.AQUA));
    }

    private static void fail(ServerPlayer sender, String key, Object... args) {
        sender.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }
}
```

- [ ] **Step 2: The command**

In `PatronusSpell.java`, directly above the Javadoc of `formFor` (the line `    /**` followed by `     * The caster's personal form: picked from their UUID and house the first time it is needed, then`), add:
```java
    /** Testing aid for ops (/patronus form): overwrite the stored personal form. */
    public static void setForm(ServerPlayer player, PatronusForm form) {
        net.minecraft.nbt.CompoundTag modData = com.hogwartsmagic.util.PlayerMagicData.getModData(player);
        modData.putInt(FORM_KEY, form.id());
        com.hogwartsmagic.util.PlayerMagicData.saveModData(player, modData);
    }

```

`src/main/java/com/hogwartsmagic/command/PatronusCommand.java`:
```java
package com.hogwartsmagic.command;

import com.hogwartsmagic.spell.summon.PatronusForm;
import com.hogwartsmagic.spell.summon.PatronusMessenger;
import com.hogwartsmagic.spell.summon.PatronusSpell;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /patronus <player> <message>}: a Master of Expecto Patronum sends a message by Patronus. Open to
 * every player; PatronusMessenger checks the wand, rank, range, mana and cooldown.
 * {@code /patronus form <1-8>} (op): set your stored personal form, to check each form's rendering.
 */
public final class PatronusCommand {
    private PatronusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("patronus")
                .then(Commands.literal("form")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1, 8))
                                .executes(ctx -> {
                                    ServerPlayer self = ctx.getSource().getPlayerOrException();
                                    PatronusForm form = PatronusForm.byId(IntegerArgumentType.getInteger(ctx, "id"));
                                    PatronusSpell.setForm(self, form);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(form.langKey()), false);
                                    return 1;
                                })))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                    ServerPlayer recipient = EntityArgument.getPlayer(ctx, "player");
                                    return PatronusMessenger.send(sender, recipient,
                                            StringArgumentType.getString(ctx, "message")) ? 1 : 0;
                                }))));
    }
}
```
In `ExampleMod.java`, in `onRegisterCommands`, directly after:
```java
        com.hogwartsmagic.command.CrowdControlCommand.register(event.getDispatcher());
```
add:
```java
        com.hogwartsmagic.command.PatronusCommand.register(event.getDispatcher());
```

- [ ] **Step 3: Strings.** In `en_us.json`, directly after the line `  "patronus.hogwartsmagic.form.phoenix": "Phoenix",` insert:
```json
  "message.hogwartsmagic.patronus.need_wand": "Hold your wand to send a Patronus.",
  "message.hogwartsmagic.patronus.need_master": "Only a Master of Expecto Patronum can send a Patronus with a message.",
  "message.hogwartsmagic.patronus.self": "Your Patronus is already with you.",
  "message.hogwartsmagic.patronus.too_far": "%s is beyond your Patronus (500 blocks, same dimension).",
  "message.hogwartsmagic.patronus.empty": "Your Patronus needs something to say.",
  "message.hogwartsmagic.patronus.cooldown": "Your Patronus is still returning (%s s).",
  "message.hogwartsmagic.patronus.sent": "Your Patronus bounds away toward %s.",
  "message.hogwartsmagic.patronus.delivered": "A silver Patronus speaks in the voice of %s: \"%s\"",
```

- [ ] **Step 4: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, then type `patronus Dev hello` in the server console, then `stop`.
Expected: `Done (`; the console command answers with a failure ("A player is required to run this command here" or similar), no stack trace mentioning `PatronusMessenger`, `PatronusCommand` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/PatronusMessenger.java src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java src/main/java/com/hogwartsmagic/command/PatronusCommand.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master of Expecto Patronum send a message by Patronus within 500 blocks" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 5: The snake entity

**Files:**
- Create: `src/main/java/com/hogwartsmagic/entity/SnakeEntity.java`
- Create: `src/main/java/com/hogwartsmagic/client/model/SnakeModel.java`, `src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java`
- Create: `build_scripts/generate_snake_texture.py`, `src/main/resources/assets/hogwartsmagic/textures/entity/snake.png` (generated), `src/main/resources/assets/hogwartsmagic/models/item/snake_spawn_egg.json`
- Modify: `src/main/java/com/hogwartsmagic/entity/ModEntities.java`, `src/main/java/com/hogwartsmagic/event/ModEntityAttributes.java`, `src/main/java/com/hogwartsmagic/ExampleMod.java`, `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SerpensortiaRules` (Task 1); `CombatTags.ALLIED` (Phase 2); `SpellMasteryData.getRank` (Phase 1); `StudentEntity` (existing).
- Produces:
  - `ModEntities.SNAKE` (`RegistryObject<EntityType<SnakeEntity>>`), `ExampleMod.SNAKE_SPAWN_EGG`
  - `SnakeEntity implements OwnableEntity`: `createAttributes()`, `conjure(LivingEntity owner, int lifeTicks, int poisonAmplifier)`, `isConjured()`, `getOwnerUUID()`, `getOwner()` (players and, server side, any living entity), `isFairTarget(@Nullable LivingEntity)` → `boolean`, `getMode()` → `int`, `commandAttack(LivingEntity)`, `commandMode(int mode, BlockPos anchor, ResourceKey<Level> dimension)`, `vanish()`, `static isParselmouth(Player)` → `boolean`
  - `SnakeModel.LAYER_LOCATION`, `SnakeModel.createBodyLayer()`; `new SnakeRenderer(EntityRendererProvider.Context)`

- [ ] **Step 1: The entity**

`src/main/java/com/hogwartsmagic/entity/SnakeEntity.java`:
```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.spell.summon.SerpensortiaRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * A snake. Conjured by Serpensortia it belongs to its caster: it follows them, bites whatever hurts them
 * or whatever they hurt (Poison by the caster's rank), never turns on them or on their allies, and
 * vanishes when its time runs out. A Master caster can order it to attack a creature or guard a spot
 * (SerpensortiaSpell.command). A snake with no owner (spawn egg, /summon) is wild: it hunts players,
 * except Masters of Serpensortia, whom snakes recognise as Parselmouths.
 */
public class SnakeEntity extends PathfinderMob implements OwnableEntity {
    @Nullable
    private UUID ownerUuid;
    /** Ticks left before a conjured snake vanishes; -1 for a wild snake. */
    private int lifeTicks = -1;
    private int poisonAmplifier;
    private int mode = SerpensortiaRules.MODE_FOLLOW;
    @Nullable
    private BlockPos guardPos;
    @Nullable
    private ResourceKey<Level> guardDimension;

    public SnakeEntity(EntityType<? extends SnakeEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, true));
        this.goalSelector.addGoal(2, new GuardPostGoal(this));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new DefendOwnerGoal(this));
        this.targetSelector.addGoal(2, new AssistOwnerGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new GuardTargetGoal(this));
        // Wild snakes only (the predicate reads ownerUuid lazily, after construction finished).
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                candidate -> this.ownerUuid == null && candidate instanceof Player player && !isParselmouth(player)));
    }

    /** Serpensortia: bind this snake to its caster for {@code lifeTicks}. Call before adding it to the level. */
    public void conjure(LivingEntity owner, int lifeTicks, int poisonAmplifier) {
        this.ownerUuid = owner.getUUID();
        this.lifeTicks = lifeTicks;
        this.poisonAmplifier = Math.max(0, poisonAmplifier);
        this.mode = SerpensortiaRules.MODE_FOLLOW;
        this.guardPos = null;
        this.guardDimension = null;
    }

    public boolean isConjured() {
        return this.ownerUuid != null;
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUuid;
    }

    /** Players anywhere this level can see them; NPC owners (Voldemort) only on the server. */
    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (this.ownerUuid == null) {
            return null;
        }
        Player player = this.level().getPlayerByUUID(this.ownerUuid);
        if (player != null) {
            return player;
        }
        if (this.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(this.ownerUuid) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    public static boolean isParselmouth(Player player) {
        return !player.level().isClientSide()
                && SerpensortiaRules.parseltongue(SpellMasteryData.getRank(player, SpellType.SERPENSORTIA));
    }

    /**
     * Who this snake may bite. A wild snake may bite anyone; a conjured one never its owner, the owner's
     * other snakes or owned mobs, team-mates, ALLIED mobs (player owners) or creative/spectator players.
     */
    public boolean isFairTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || candidate == this || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player player && player.isCreative()) {
            return false;
        }
        if (this.ownerUuid == null) {
            return true;
        }
        if (this.ownerUuid.equals(candidate.getUUID())) {
            return false;
        }
        if (candidate instanceof SnakeEntity other && this.ownerUuid.equals(other.ownerUuid)) {
            return false;
        }
        if (candidate instanceof OwnableEntity ownable && this.ownerUuid.equals(ownable.getOwnerUUID())) {
            return false;
        }
        LivingEntity owner = this.getOwner();
        if (owner != null) {
            if (candidate.isAlliedTo(owner) || owner.isAlliedTo(candidate)) {
                return false;
            }
            if (owner instanceof Player && candidate.getTags().contains(CombatTags.ALLIED)) {
                return false;
            }
        }
        return true;
    }

    /** Every route to a target (goals, retaliation, commands) is checked here. */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && this.ownerUuid != null && !this.isFairTarget(target)) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (this.ownerUuid != null) {
            if (this.ownerUuid.equals(other.getUUID())) {
                return true;
            }
            if (other instanceof SnakeEntity snake && this.ownerUuid.equals(snake.ownerUuid)) {
                return true;
            }
        }
        return super.isAlliedTo(other);
    }

    public int getMode() {
        return this.mode;
    }

    public void commandAttack(LivingEntity target) {
        if (this.isFairTarget(target)) {
            this.setTarget(target);
        }
    }

    /** Follow, or guard {@code anchor} in {@code dimension} (the post is ignored in any other dimension). */
    public void commandMode(int newMode, BlockPos anchor, ResourceKey<Level> dimension) {
        this.mode = newMode;
        if (newMode == SerpensortiaRules.MODE_GUARD) {
            this.guardPos = anchor.immutable();
            this.guardDimension = dimension;
        } else {
            this.guardPos = null;
            this.guardDimension = null;
        }
        this.getNavigation().stop();
    }

    @Nullable
    BlockPos activeGuardPost() {
        if (this.mode != SerpensortiaRules.MODE_GUARD || this.guardPos == null || this.guardDimension == null) {
            return null;
        }
        return this.level().dimension().equals(this.guardDimension) ? this.guardPos : null;
    }

    /** A puff of smoke and gone: the end of a conjured snake's time, or a new cast replacing it. */
    public void vanish() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2D, this.getZ(),
                    12, 0.25D, 0.1D, 0.25D, 0.01D);
        }
        this.discard();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide() && this.lifeTicks >= 0) {
            this.lifeTicks--;
            if (this.lifeTicks <= 0) {
                this.vanish();
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, SerpensortiaRules.POISON_TICKS, this.poisonAmplifier), this);
        }
        return hit;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    /** Wild snakes despawn like any monster; conjured ones stay until their time runs out. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return this.ownerUuid == null;
    }

    /** Jumps beside the owner onto solid ground, but only into free space. */
    boolean teleportNear(LivingEntity owner) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = this.random.nextInt(5) - 2;
            int dz = this.random.nextInt(5) - 2;
            if (dx == 0 && dz == 0) {
                continue;
            }
            BlockPos feet = BlockPos.containing(owner.getX() + dx, owner.getY(), owner.getZ() + dz);
            BlockPos below = feet.below();
            if (!this.level().getBlockState(below).isFaceSturdy(this.level(), below, Direction.UP)) {
                continue;
            }
            double x = feet.getX() + 0.5D;
            double y = feet.getY();
            double z = feet.getZ() + 0.5D;
            AABB box = this.getBoundingBox().move(x - this.getX(), y - this.getY(), z - this.getZ());
            if (this.level().noCollision(this, box)) {
                this.moveTo(x, y, z, this.getYRot(), this.getXRot());
                this.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAT_HISS;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HISS;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public float getVoicePitch() {
        return 1.6F + (this.random.nextFloat() - 0.5F) * 0.2F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        tag.putInt("LifeTicks", this.lifeTicks);
        tag.putInt("Poison", this.poisonAmplifier);
        tag.putInt("Mode", this.mode);
        if (this.guardPos != null && this.guardDimension != null) {
            tag.putLong("GuardPos", this.guardPos.asLong());
            tag.putString("GuardDim", this.guardDimension.location().toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.lifeTicks = tag.contains("LifeTicks") ? tag.getInt("LifeTicks") : -1;
        this.poisonAmplifier = tag.getInt("Poison");
        this.mode = tag.getInt("Mode");
        this.guardPos = null;
        this.guardDimension = null;
        if (tag.contains("GuardPos") && tag.contains("GuardDim")) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("GuardDim"));
            if (dimension != null) {
                this.guardPos = BlockPos.of(tag.getLong("GuardPos"));
                this.guardDimension = ResourceKey.create(Registries.DIMENSION, dimension);
            }
        }
    }

    /** Stays near the owner in follow mode; jumps to them past TELEPORT_DISTANCE. */
    static final class FollowOwnerGoal extends Goal {
        private final SnakeEntity snake;
        private int recalc;

        FollowOwnerGoal(SnakeEntity snake) {
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.snake.mode != SerpensortiaRules.MODE_FOLLOW || this.snake.getTarget() != null) {
                return false;
            }
            LivingEntity owner = this.snake.getOwner();
            return owner != null && !owner.isSpectator()
                    && this.snake.distanceToSqr(owner) > SerpensortiaRules.FOLLOW_START * SerpensortiaRules.FOLLOW_START;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity owner = this.snake.getOwner();
            return this.snake.mode == SerpensortiaRules.MODE_FOLLOW && this.snake.getTarget() == null && owner != null
                    && !this.snake.getNavigation().isDone()
                    && this.snake.distanceToSqr(owner) > SerpensortiaRules.FOLLOW_STOP * SerpensortiaRules.FOLLOW_STOP;
        }

        @Override
        public void start() {
            this.recalc = 0;
        }

        @Override
        public void stop() {
            this.snake.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity owner = this.snake.getOwner();
            if (owner == null) {
                return;
            }
            this.snake.getLookControl().setLookAt(owner, 10.0F, this.snake.getMaxHeadXRot());
            if (--this.recalc > 0) {
                return;
            }
            this.recalc = this.adjustedTickDelay(10);
            if (this.snake.distanceToSqr(owner) > SerpensortiaRules.TELEPORT_DISTANCE * SerpensortiaRules.TELEPORT_DISTANCE) {
                this.snake.teleportNear(owner);
            } else {
                this.snake.getNavigation().moveTo(owner, 1.2D);
            }
        }
    }

    /** Walks back to the guard post when it strays past GUARD_LEASH with nothing to fight. */
    static final class GuardPostGoal extends Goal {
        private final SnakeEntity snake;

        GuardPostGoal(SnakeEntity snake) {
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            BlockPos post = this.snake.activeGuardPost();
            return post != null && this.snake.getTarget() == null
                    && this.snake.distanceToSqr(Vec3.atBottomCenterOf(post)) > SerpensortiaRules.GUARD_LEASH * SerpensortiaRules.GUARD_LEASH;
        }

        @Override
        public boolean canContinueToUse() {
            return this.snake.activeGuardPost() != null && this.snake.getTarget() == null && !this.snake.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos post = this.snake.activeGuardPost();
            if (post != null) {
                this.snake.getNavigation().moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 1.1D);
            }
        }

        @Override
        public void stop() {
            this.snake.getNavigation().stop();
        }
    }

    /** The owner's attacker becomes the target, like OwnerHurtByTargetGoal but for any owner. */
    static final class DefendOwnerGoal extends TargetGoal {
        private final SnakeEntity snake;
        @Nullable
        private LivingEntity attacker;
        private int seenTimestamp;

        DefendOwnerGoal(SnakeEntity snake) {
            super(snake, false);
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.snake.getOwner();
            if (owner == null) {
                return false;
            }
            this.attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != this.seenTimestamp && this.snake.isFairTarget(this.attacker)
                    && this.canAttack(this.attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.attacker);
            LivingEntity owner = this.snake.getOwner();
            if (owner != null) {
                this.seenTimestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** Whatever the owner hits becomes the target, like OwnerHurtTargetGoal but for any owner. */
    static final class AssistOwnerGoal extends TargetGoal {
        private final SnakeEntity snake;
        @Nullable
        private LivingEntity victim;
        private int seenTimestamp;

        AssistOwnerGoal(SnakeEntity snake) {
            super(snake, false);
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.snake.getOwner();
            if (owner == null) {
                return false;
            }
            this.victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != this.seenTimestamp && this.snake.isFairTarget(this.victim)
                    && this.canAttack(this.victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.victim);
            LivingEntity owner = this.snake.getOwner();
            if (owner != null) {
                this.seenTimestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    /** On guard: the nearest hostile mob (or mob hunting the owner) within GUARD_RADIUS of the post. */
    static final class GuardTargetGoal extends TargetGoal {
        private final SnakeEntity snake;
        @Nullable
        private LivingEntity found;

        GuardTargetGoal(SnakeEntity snake) {
            super(snake, false);
            this.snake = snake;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            BlockPos post = this.snake.activeGuardPost();
            if (post == null || this.snake.getTarget() != null || this.snake.tickCount % 10 != 0) {
                return false;
            }
            LivingEntity owner = this.snake.getOwner();
            Vec3 centre = Vec3.atBottomCenterOf(post);
            AABB box = new AABB(post).inflate(SerpensortiaRules.GUARD_RADIUS);
            this.found = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity candidate : this.snake.level().getEntitiesOfClass(LivingEntity.class, box, this.snake::isFairTarget)) {
                boolean hostile = (candidate instanceof Enemy && !(candidate instanceof StudentEntity))
                        || (candidate instanceof Mob mob && owner != null && mob.getTarget() == owner);
                if (!hostile) {
                    continue;
                }
                double distance = candidate.distanceToSqr(centre);
                if (distance < best) {
                    best = distance;
                    this.found = candidate;
                }
            }
            return this.found != null;
        }

        @Override
        public void start() {
            this.mob.setTarget(this.found);
            super.start();
        }
    }
}
```

- [ ] **Step 2: The model and renderer**

`src/main/java/com/hogwartsmagic/client/model/SnakeModel.java`:
```java
package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SnakeEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A small snake written as code: a head and six tapering body segments. As in BasiliskModel the
 * segments are siblings under root, not a nested chain, so each is placed directly. A body this short
 * doesn't need the Basilisk's path ring buffer: a sine wave travelling down the body sets each
 * segment's sideways offset (locked to the limb swing while moving, a slow idle sway at rest), and each
 * segment is aimed at the next one so the body stays joined.
 */
public class SnakeModel extends HierarchicalModel<SnakeEntity> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "snake"), "main");
    public static final int SEGMENTS = 6;
    /** Every segment's cube is 4 model units long (16 units is one block). */
    private static final float SEGMENT_LENGTH = 4.0F;
    private static final int[][] SEGMENT_UV = {{0, 8}, {16, 8}, {32, 8}, {48, 8}, {0, 16}, {16, 16}};
    private static final float[] SEGMENT_WIDTH = {3.0F, 3.0F, 3.0F, 2.0F, 2.0F, 1.0F};
    private static final float[] SEGMENT_HEIGHT = {2.0F, 2.0F, 2.0F, 2.0F, 1.0F, 1.0F};

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart tongue;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];
    /** Scratch buffer reused every frame. */
    private final float[] offsets = new float[SEGMENTS + 1];

    public SnakeModel(ModelPart root) {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.tongue = this.head.getChild("tongue");
        for (int i = 0; i < SEGMENTS; i++) {
            this.segments[i] = this.root.getChild("seg" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition root = parts.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.5F, -2.0F, -4.0F, 3.0F, 2.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, 0.0F, -2.0F));
        head.addOrReplaceChild("tongue", CubeListBuilder.create()
                        .texOffs(16, 0).addBox(-0.5F, 0.0F, -3.0F, 1.0F, 0.0F, 3.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -0.5F, -4.0F));

        for (int i = 0; i < SEGMENTS; i++) {
            float width = SEGMENT_WIDTH[i];
            float height = SEGMENT_HEIGHT[i];
            root.addOrReplaceChild("seg" + i, CubeListBuilder.create()
                            .texOffs(SEGMENT_UV[i][0], SEGMENT_UV[i][1])
                            .addBox(-width / 2.0F, -height, 0.0F, width, height, SEGMENT_LENGTH, CubeDeformation.NONE),
                    PartPose.offset(0.0F, 0.0F, -2.0F + i * SEGMENT_LENGTH));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(SnakeEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        boolean moving = limbSwingAmount > 0.05F;
        float time = moving ? limbSwing * 1.4F : ageInTicks * 0.08F;
        float amplitude = moving ? 1.4F + Math.min(1.0F, limbSwingAmount) * 1.6F : 0.5F;
        for (int i = 0; i <= SEGMENTS; i++) {
            // The wave grows toward the tail, the way a slithering body whips.
            float taper = 0.35F + 0.65F * (i / (float) SEGMENTS);
            this.offsets[i] = Mth.sin(time - i * 0.9F) * amplitude * taper;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            ModelPart segment = this.segments[i];
            segment.x = this.offsets[i];
            // Each cube runs from its own origin toward +Z; aim it at where the next segment starts.
            segment.yRot = (float) Math.atan2(this.offsets[i + 1] - this.offsets[i], SEGMENT_LENGTH);
        }

        // The head runs toward -Z, so it faces away from the first segment's lean, plus damped tracking.
        this.head.x = this.offsets[0];
        this.head.yRot = (float) Math.atan2(this.offsets[1] - this.offsets[0], SEGMENT_LENGTH)
                + Mth.clamp(netHeadYaw, -40.0F, 40.0F) * Mth.DEG_TO_RAD * 0.5F;
        this.head.xRot = Mth.clamp(headPitch, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.5F;

        // Tasting the air: a quick flick every two seconds.
        float cycle = (ageInTicks % 40.0F) / 40.0F;
        this.tongue.visible = cycle < 0.15F;
        this.tongue.yRot = Mth.sin(ageInTicks * 0.9F) * 0.25F;
    }
}
```

`src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java`:
```java
package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.SnakeModel;
import com.hogwartsmagic.entity.SnakeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SnakeRenderer extends MobRenderer<SnakeEntity, SnakeModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/snake.png");

    public SnakeRenderer(EntityRendererProvider.Context context) {
        super(context, new SnakeModel(context.bakeLayer(SnakeModel.LAYER_LOCATION)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(SnakeEntity entity) {
        return TEXTURE;
    }
}
```

- [ ] **Step 3: The texture**

`build_scripts/generate_snake_texture.py`:
```python
"""Paint the 64x32 snake texture.

    python build_scripts/generate_snake_texture.py

Writes assets/hogwartsmagic/textures/entity/snake.png.

Layout (texOffs in client/model/SnakeModel.java): head 3x2x4 at (0,0), tongue 1x0x3 at (16,0),
segments 0-2 (3x2x4) at (0,8), (16,8), (32,8), segment 3 (2x2x4) at (48,8), segment 4 (2x1x4) at
(0,16), segment 5 (1x1x4) at (16,16). The body is a grass-snake green with darker diamond scales
stamped over the whole sheet, so every face of every segment carries the pattern; the head gets two
yellow eyes on its front face (north face of the head box: (4,4) to (6,5)) and the tongue is red.
"""

import os
import random

from PIL import Image

W, H = 64, 32
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "entity", "snake.png")

BASE = (66, 112, 48)
DARK = (34, 64, 28)
LIGHT = (120, 156, 76)
EYE = (232, 204, 58)
PUPIL = (20, 20, 16)
TONGUE = (176, 40, 44)


def main():
    rng = random.Random(11)
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = image.load()
    for y in range(24):
        for x in range(W):
            diamond = (x + y) % 4 == 0 or (x - y) % 4 == 0
            colour = DARK if diamond else BASE
            jitter = rng.randint(-8, 8)
            if rng.random() < 0.08:
                colour = LIGHT
            px[x, y] = (max(0, min(255, colour[0] + jitter)),
                        max(0, min(255, colour[1] + jitter)),
                        max(0, min(255, colour[2] + jitter)), 255)
    # Tongue sheet (16,0)-(24,3).
    for y in range(0, 3):
        for x in range(16, 24):
            px[x, y] = TONGUE + (255,)
    # Eyes on the head's front face, mouth line below them.
    px[4, 4] = EYE + (255,)
    px[6, 4] = EYE + (255,)
    px[5, 4] = DARK + (255,)
    for x in range(4, 7):
        px[x, 5] = PUPIL + (255,)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
```
Run: `python build_scripts/generate_snake_texture.py`
Expected: `wrote ...snake.png`.

`src/main/resources/assets/hogwartsmagic/models/item/snake_spawn_egg.json`:
```json
{
    "parent": "minecraft:item/template_spawn_egg"
}
```

- [ ] **Step 4: Register it.** In `ModEntities.java`, replace:
```java
                            .fireImmune()
                            .build("living_statue"));
}
```
with:
```java
                            .fireImmune()
                            .build("living_statue"));

    // Serpensortia's snakes (Spell Mastery Phase 7a). Long and low: the box is the coiled body, the
    // model's tail trails behind it.
    public static final RegistryObject<EntityType<SnakeEntity>> SNAKE =
            ENTITY_TYPES.register("snake",
                    () -> EntityType.Builder.<SnakeEntity>of(SnakeEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 0.35F)
                            .clientTrackingRange(8)
                            .build("snake"));
}
```
In `ModEntityAttributes.java`, directly after:
```java
        event.put(ModEntities.LIVING_STATUE.get(), com.hogwartsmagic.entity.LivingStatueEntity.createAttributes().build());
```
add:
```java
        event.put(ModEntities.SNAKE.get(), com.hogwartsmagic.entity.SnakeEntity.createAttributes().build());
```
In `ExampleMod.java`, directly after:
```java
    public static final RegistryObject<Item> WHOMPING_WILLOW_SPAWN_EGG = ITEMS.register("whomping_willow_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.WHOMPING_WILLOW, 0x42372A, 0x4A5C30, new Item.Properties()));
```
add:
```java

    public static final RegistryObject<Item> SNAKE_SPAWN_EGG = ITEMS.register("snake_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(ModEntities.SNAKE, 0x3A6B2E, 0xC9B458, new Item.Properties()));
```
and replace:
```java
                output.accept(WHOMPING_WILLOW_SPAWN_EGG.get()); // Whomping Willow Spawn Egg
```
with:
```java
                output.accept(WHOMPING_WILLOW_SPAWN_EGG.get()); // Whomping Willow Spawn Egg
                output.accept(SNAKE_SPAWN_EGG.get()); // Wild Snake Spawn Egg
```
In `ClientModEvents.java`, replace:
```java
        event.registerLayerDefinition(com.hogwartsmagic.client.LivingStatueModel.LAYER,
                com.hogwartsmagic.client.LivingStatueModel::createBodyLayer);
```
with:
```java
        event.registerLayerDefinition(com.hogwartsmagic.client.LivingStatueModel.LAYER,
                com.hogwartsmagic.client.LivingStatueModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.SnakeModel.LAYER_LOCATION,
                com.hogwartsmagic.client.model.SnakeModel::createBodyLayer);
```
and replace:
```java
        event.registerEntityRenderer(ModEntities.LIVING_STATUE.get(),
                com.hogwartsmagic.client.LivingStatueRenderer::new);
```
with:
```java
        event.registerEntityRenderer(ModEntities.LIVING_STATUE.get(),
                com.hogwartsmagic.client.LivingStatueRenderer::new);
        event.registerEntityRenderer(ModEntities.SNAKE.get(),
                com.hogwartsmagic.client.renderer.SnakeRenderer::new);
```

- [ ] **Step 5: Strings.** In `en_us.json`, directly after `  "item.hogwartsmagic.whomping_willow_spawn_egg": "Whomping Willow Spawn Egg",` insert:
```json
  "entity.hogwartsmagic.snake": "Snake",
  "item.hogwartsmagic.snake_spawn_egg": "Snake Spawn Egg",
```

- [ ] **Step 6: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, then in the server console `summon hogwartsmagic:snake 0 100 0` (or near world spawn), then `stop`.
Expected: `Done (`; the summon prints `Summoned new Snake`; no stack trace mentioning `SnakeEntity`, `SnakeModel`, `SnakeRenderer` or any `client` class; no "missing attributes" error.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/entity/SnakeEntity.java src/main/java/com/hogwartsmagic/client/model/SnakeModel.java src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java build_scripts/generate_snake_texture.py src/main/resources/assets/hogwartsmagic/textures/entity/snake.png src/main/resources/assets/hogwartsmagic/models/item/snake_spawn_egg.json src/main/java/com/hogwartsmagic/entity/ModEntities.java src/main/java/com/hogwartsmagic/event/ModEntityAttributes.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Add a snake mob with a coded slithering model, owner loyalty and wild hunting" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Serpensortia conjures snakes (ranks I–III)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (Serpensortia case)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (block-hit branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SerpensortiaRules` (Task 1); `SnakeEntity.conjure/isFairTarget/vanish/getOwnerUUID`, `ModEntities.SNAKE` (Task 5); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible`, `CombatTags.ALLIED` (Phase 2).
- Produces: `SerpensortiaSpell.conjureAt(ServerLevel level, Vec3 at, @Nullable LivingEntity target, @Nullable Entity caster)` → `int` snakes conjured; `SerpensortiaSpell.snakesOf(ServerLevel level, Entity owner)` → `List<SnakeEntity>`

- [ ] **Step 1: The spell**

`src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java`:
```java
package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.SnakeEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Serpensortia: conjures the caster's snakes (count, lifetime and venom by rank) where the beam lands.
 * A creature hit is their first target. A new cast replaces the caster's previous snakes. Works for
 * NPC casters too: Voldemort's snakes are his and defend him. Server only.
 */
public final class SerpensortiaSpell {
    private SerpensortiaSpell() {
    }

    public static int conjureAt(ServerLevel level, Vec3 at, @Nullable LivingEntity target, @Nullable Entity caster) {
        Entity responsible = CombatEffects.responsible(caster);
        if (!(responsible instanceof LivingEntity owner) || !owner.isAlive()) {
            return 0;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.SERPENSORTIA);
        for (SnakeEntity previous : snakesOf(level, owner)) {
            previous.vanish();
        }

        int count = SerpensortiaRules.snakeCount(rank);
        float yaw = owner.getYRot();
        double radians = Math.toRadians(yaw);
        // The caster's right-hand side, so the snakes line up across the line of the beam.
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            SnakeEntity snake = ModEntities.SNAKE.get().create(level);
            if (snake == null) {
                continue;
            }
            double offset = SerpensortiaRules.spawnOffset(i, count);
            double x = at.x + rightX * offset;
            double z = at.z + rightZ * offset;
            snake.moveTo(x, at.y, z, yaw, 0.0F);
            if (!level.noCollision(snake)) {
                snake.moveTo(at.x, at.y, at.z, yaw, 0.0F);
                if (!level.noCollision(snake)) {
                    continue; // nowhere free to stand: skip this snake rather than wedge it in a wall
                }
            }
            snake.conjure(owner, SerpensortiaRules.lifetimeTicks(rank), SerpensortiaRules.poisonAmplifier(rank));
            if (owner instanceof Player) {
                snake.addTag(CombatTags.ALLIED);
            }
            if (target != null && snake.isFairTarget(target)) {
                snake.setTarget(target);
            }
            level.addFreshEntity(snake);
            level.sendParticles(ParticleTypes.SQUID_INK, snake.getX(), snake.getY() + 0.2D, snake.getZ(),
                    8, 0.2D, 0.1D, 0.2D, 0.02D);
            spawned++;
        }
        if (spawned > 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CAT_HISS, SoundSource.HOSTILE, 0.8f, 1.5f);
        }
        return spawned;
    }

    /** The conjured snakes belonging to {@code owner} within DISMISS_RADIUS. */
    public static List<SnakeEntity> snakesOf(ServerLevel level, Entity owner) {
        return level.getEntitiesOfClass(SnakeEntity.class,
                owner.getBoundingBox().inflate(SerpensortiaRules.DISMISS_RADIUS),
                snake -> owner.getUUID().equals(snake.getOwnerUUID()));
    }
}
```

- [ ] **Step 2: Wire the hit.** In `SpellType.java`, replace the whole Serpensortia case, from `            case SERPENSORTIA -> {` down to its closing `            }` (the line directly above `            case ARRESTO_MOMENTUM -> {`), which today reads:
```java
            case SERPENSORTIA -> {
                if (target.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 3; i++) {
                        net.minecraft.world.entity.monster.CaveSpider snake = new net.minecraft.world.entity.monster.CaveSpider(net.minecraft.world.entity.EntityType.CAVE_SPIDER, serverLevel);
                        snake.moveTo(target.getX() + (serverLevel.random.nextDouble() - 0.5) * 2, target.getY(), target.getZ() + (serverLevel.random.nextDouble() - 0.5) * 2, serverLevel.random.nextFloat() * 360F, 0.0F);
                        if (caster instanceof LivingEntity livingCaster) {
                            snake.setTarget(null);
                            // Set allied to caster by making them target what caster targets or attack who hurts caster (vanilla behavior doesn't support easy allies for spiders, we can add tag and ignore in Wizard logic)
                            snake.addTag(com.hogwartsmagic.spell.combat.CombatTags.ALLIED);
                        }
                        serverLevel.addFreshEntity(snake);
                    }
                }
            }
```
with:
```java
            case SERPENSORTIA -> {
                // Real snakes now (Spell Mastery Phase 7a), owned by the caster and sent at the creature hit.
                if (target.level() instanceof ServerLevel serverLevel) {
                    com.hogwartsmagic.spell.summon.SerpensortiaSpell.conjureAt(serverLevel, target.position(), target, caster);
                }
            }
```

- [ ] **Step 3: Snakes from a block hit.** In `SpellBeamEntity.java`, in `onHit`, replace:
```java
                    hitSuccessful = this.level() instanceof ServerLevel serverLevel
                            && com.hogwartsmagic.block.ChamberDoorBlock.speakTo(serverLevel, pos);
```
with:
```java
                    hitSuccessful = this.level() instanceof ServerLevel serverLevel
                            && com.hogwartsmagic.block.ChamberDoorBlock.speakTo(serverLevel, pos);
                } else if (spell == SpellType.SERPENSORTIA) {
                    // Snakes rise from the face the beam struck and wait at the caster's side for a fight.
                    if (this.level() instanceof ServerLevel serpentLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.summon.SerpensortiaSpell.conjureAt(serpentLevel,
                                Vec3.atBottomCenterOf(pos.relative(blockHit.getDirection())), null, getOwner()) > 0;
                    }
```
(The original next line, `} else if (spell == SpellType.ALOHOMORA) {` or whatever Phase 5a made of it, closes the new branch.) A successful block hit earns Serpensortia's gated block-utility XP through the existing `MasteryAwards.onBlockEffect` call.

- [ ] **Step 4: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.serpensortia.desc": "Summons several allied, venomous spiders to attack your enemies.",
```
with:
```json
  "spell.hogwartsmagic.serpensortia.desc": "Conjures snakes loyal to you that bite with poison: 1 for 20s (Novice), 2 for 30s (Adept), 3 for 40s with Poison II (Expert). Master: 3 for 60s, and you speak Parseltongue: left-click to send them at a creature or to switch them between following you and guarding a spot. Wild snakes leave a Master alone.",
```

- [ ] **Step 5: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `SerpensortiaSpell` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Serpensortia conjure loyal snakes by rank instead of cave spiders" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Parseltongue (Master commands)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`castAs`)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`isLeftClickSelfCast`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `SerpensortiaRules.parseltongue/toggledMode/COMMAND_RANGE/COMMAND_COOLDOWN_TICKS` (Task 1); `SnakeEntity.commandAttack/commandMode/getMode/isFairTarget` (Task 5); `SerpensortiaSpell.snakesOf` (Task 6); `SpellMasteryData.getRank` (Phase 1).
- Produces: `SerpensortiaSpell.command(ServerPlayer player)`

The wand's crouch + right-click already opens the spell menu, so commands ride the left-click self-cast that Protego, Heal, Arresto, Lumos and Reparo use: a left-click with Serpensortia selected sends `CastSpellPacket(selfCastProtego = true)`, and the server turns it into a command before any cooldown or mana check. Below Master, or with no snakes, the left-click does nothing (and costs nothing).

- [ ] **Step 1: The command.** In `SerpensortiaSpell.java`, add these imports to the import block:
```java
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
```
Directly above the line `    /** The conjured snakes belonging to {@code owner} within DISMISS_RADIUS. */` add:
```java
    private static final String TAG_LAST_COMMAND = "hogwartsmagic:parseltongue_at";

    /**
     * Parseltongue (Master): aimed at a creature within COMMAND_RANGE, every snake of the caster attacks
     * it; aimed anywhere else, they switch between following the caster and guarding the aimed spot (or
     * the caster's feet when aiming at the sky). Never costs mana or starts a cooldown.
     */
    public static void command(ServerPlayer player) {
        if (!SerpensortiaRules.parseltongue(SpellMasteryData.getRank(player, SpellType.SERPENSORTIA))) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (data.contains(TAG_LAST_COMMAND) && now - data.getLong(TAG_LAST_COMMAND) < SerpensortiaRules.COMMAND_COOLDOWN_TICKS) {
            return;
        }
        data.putLong(TAG_LAST_COMMAND, now);

        ServerLevel level = player.serverLevel();
        List<SnakeEntity> snakes = snakesOf(level, player);
        if (snakes.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.parseltongue.no_snakes")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
            return;
        }
        LivingEntity aimed = aimedCreature(player, snakes);
        if (aimed != null && snakes.get(0).isFairTarget(aimed)) {
            for (SnakeEntity snake : snakes) {
                snake.commandAttack(aimed);
            }
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.parseltongue.attack", aimed.getDisplayName())
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        } else {
            int mode = SerpensortiaRules.toggledMode(snakes.get(0).getMode());
            BlockPos anchor = player.blockPosition();
            HitResult hit = player.pick(SerpensortiaRules.COMMAND_RANGE, 1.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                anchor = blockHit.getBlockPos().relative(blockHit.getDirection());
            }
            for (SnakeEntity snake : snakes) {
                snake.commandMode(mode, anchor, level.dimension());
            }
            player.displayClientMessage(Component.translatable(mode == SerpensortiaRules.MODE_GUARD
                    ? "message.hogwartsmagic.parseltongue.guard" : "message.hogwartsmagic.parseltongue.follow")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CAT_HISS, SoundSource.PLAYERS, 0.7f, 1.8f);
    }

    @Nullable
    private static LivingEntity aimedCreature(ServerPlayer player, List<SnakeEntity> own) {
        double range = SerpensortiaRules.COMMAND_RANGE;
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult block = player.pick(range, 1.0F, false);
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(start);
        Vec3 end = start.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end, box,
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity.isPickable()
                        && !entity.isSpectator() && !own.contains(entity));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

```

- [ ] **Step 2: The server hook.** In `SpellPackets.java`, inside `CastSpellPacket.castAs`, replace:
```java
            // Apparition is its own channel: it opens a destination minimap rather than casting immediately.
            if (spell == SpellType.APPARITION && !p.selfCastProtego && !p.releaseChannel && !p.channelHeartbeat) {
                com.hogwartsmagic.event.ModEvents.startApparitionChannel(player, wand);
                return;
            }
```
with:
```java
            // Apparition is its own channel: it opens a destination minimap rather than casting immediately.
            if (spell == SpellType.APPARITION && !p.selfCastProtego && !p.releaseChannel && !p.channelHeartbeat) {
                com.hogwartsmagic.event.ModEvents.startApparitionChannel(player, wand);
                return;
            }

            // Parseltongue: a left-click with Serpensortia commands the caster's snakes (Master) and never casts.
            if (spell == SpellType.SERPENSORTIA && p.selfCastProtego) {
                com.hogwartsmagic.spell.summon.SerpensortiaSpell.command(player);
                return;
            }
```

- [ ] **Step 3: The client left-click.** In `ModEvents.java`, in `isLeftClickSelfCast`, replace:
```java
                || spell == SpellType.LUMOS
                || spell == SpellType.REPARO;
```
with:
```java
                || spell == SpellType.LUMOS
                || spell == SpellType.REPARO
                || spell == SpellType.SERPENSORTIA;
```

- [ ] **Step 4: Strings.** In `en_us.json`, directly after `  "item.hogwartsmagic.snake_spawn_egg": "Snake Spawn Egg",` insert:
```json
  "message.hogwartsmagic.parseltongue.no_snakes": "No snakes answer you",
  "message.hogwartsmagic.parseltongue.attack": "Your snakes strike at %s",
  "message.hogwartsmagic.parseltongue.guard": "Your snakes coil to guard that spot",
  "message.hogwartsmagic.parseltongue.follow": "Your snakes follow you",
```

- [ ] **Step 5: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `grep -n "isLeftClickSelfCast" src/main/java/com/hogwartsmagic/event/ModEvents.java`
Expected: three lines (the method and its two calls).

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master of Serpensortia command their snakes in Parseltongue" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 8: Fiendfyre fire respects mobGriefing, claims and wards

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java` (whole file replaced)
- Modify: `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` (`spreadFire`)
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (Fiendfyre case)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Fiendfyre block branch)

**Interfaces:**
- Consumes: `TerrainRules.canPlace` (Phase 3); `CombatEffects.responsible` (Phase 2).
- Produces: `FiendfyreEntity.setCaster(@Nullable Entity caster)`

Today both the ground fire a Fiendfyre beam leaves (`FiendfyreEntity`) and the held serpent set fire to every empty block they pick, ignoring `mobGriefing`, claims, spawn protection and wards. This task routes every placement through `TerrainRules.canPlace` and skips unloaded positions (a fire pick on an unloaded chunk would otherwise load it). Task 9 rewrites the serpent and keeps the same rule.

- [ ] **Step 1: The ground fire remembers its caster.** Replace the whole content of `src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java` with:
```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Entity that manages the spreading of Fiendfyre where a Fiendfyre beam lands. It starts small and
 * spreads for 15 seconds. Every fire block goes through TerrainRules.canPlace as its caster (an NPC,
 * an unknown or an offline caster falls back to the mobGriefing rule), so claims, spawn protection and
 * Cave Inimicum wards hold.
 */
public class FiendfyreEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreEntity.class, EntityDataSerializers.INT);
    private static final int MAX_LIFE = 300; // 15 seconds (20 ticks per second)

    @Nullable
    private UUID casterUuid;

    public FiendfyreEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Whoever cast the beam (a projectile's owner is resolved to the caster). Call before adding it. */
    public void setCaster(@Nullable Entity caster) {
        Entity responsible = CombatEffects.responsible(caster);
        this.casterUuid = responsible == null ? null : responsible.getUUID();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifeTicks(tag.getInt("LifeTicks"));
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", getLifeTicks());
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
    }

    public int getLifeTicks() {
        return this.entityData.get(DATA_LIFE_TICKS);
    }

    public void setLifeTicks(int ticks) {
        this.entityData.set(DATA_LIFE_TICKS, ticks);
    }

    @Override
    public void tick() {
        super.tick();

        int ticks = getLifeTicks();
        if (ticks >= MAX_LIFE) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }
        setLifeTicks(ticks + 1);

        if (this.level() instanceof ServerLevel serverLevel) {
            // Spreading logic every 10 ticks
            if (ticks % 10 == 0) {
                spreadFire(serverLevel);
            }

            // Visuals on server (particles)
            if (ticks % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.5, 0.5, 0.5, 0.05);
                serverLevel.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private void spreadFire(ServerLevel level) {
        // Randomly pick blocks around the center to ignite
        BlockPos center = this.blockPosition();
        int radius = 1 + (getLifeTicks() / 40); // Grows from 1 to 8 radius over 15s (300 ticks)
        Entity caster = this.casterUuid == null ? null : level.getEntity(this.casterUuid);

        for (int i = 0; i < 5; i++) {
            int rx = this.random.nextInt(radius * 2 + 1) - radius;
            int ry = this.random.nextInt(3) - 1;
            int rz = this.random.nextInt(radius * 2 + 1) - radius;

            BlockPos target = center.offset(rx, ry, rz);
            if (!level.isLoaded(target) || !level.isEmptyBlock(target)) {
                continue;
            }
            if (TerrainRules.canPlace(level, target, caster)) {
                level.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
```

- [ ] **Step 2: Hand the caster over.** In `SpellType.java`, in the Fiendfyre case, replace:
```java
                fiendfyre.moveTo(target.getX(), target.getY(), target.getZ());
                target.level().addFreshEntity(fiendfyre);
```
with:
```java
                fiendfyre.moveTo(target.getX(), target.getY(), target.getZ());
                fiendfyre.setCaster(caster);
                target.level().addFreshEntity(fiendfyre);
```
In `SpellBeamEntity.java`, in the `spell == SpellType.FIENDFYRE` block branch, replace:
```java
                    FiendfyreEntity fiendfyre = new FiendfyreEntity(ModEntities.FIENDFYRE.get(), this.level());
                    fiendfyre.moveTo(this.getX(), this.getY(), this.getZ());
                    this.level().addFreshEntity(fiendfyre);
```
with:
```java
                    FiendfyreEntity fiendfyre = new FiendfyreEntity(ModEntities.FIENDFYRE.get(), this.level());
                    fiendfyre.moveTo(this.getX(), this.getY(), this.getZ());
                    fiendfyre.setCaster(getOwner());
                    this.level().addFreshEntity(fiendfyre);
```

- [ ] **Step 3: The serpent's fire too.** In `FiendfyreSerpentEntity.java`, in `spreadFire()`, replace:
```java
            BlockPos target = center.offset(rx, ry, rz);
            if (this.level().isEmptyBlock(target)) {
                this.level().setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
```
with:
```java
            BlockPos target = center.offset(rx, ry, rz);
            if (!(this.level() instanceof ServerLevel serverLevel)
                    || !serverLevel.isLoaded(target) || !serverLevel.isEmptyBlock(target)) {
                continue;
            }
            if (com.hogwartsmagic.spell.TerrainRules.canPlace(serverLevel, target, getCaster(serverLevel))) {
                serverLevel.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
```

- [ ] **Step 4: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
git commit -m "Make Fiendfyre fire respect mobGriefing, claims, spawn protection and wards" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Fiendfyre by rank (duration, two serpents at Expert, rogue serpents with a red telegraph)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` (whole file replaced)
- Modify: `src/main/java/com/hogwartsmagic/client/FiendfyreSerpentRenderer.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`castAs` heartbeat and release; three helpers removed)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (channel tick, interrupt; old channel code removed)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `FiendfyreRules` (Task 1); `TerrainRules.canPlace` (Phase 3); `SpellMasteryData.getRank`, `SpellCosts.masteryMultiplier/startCooldown` (Phase 1); `PlayerMagicData.getModData/saveModData/getMaxMana`, `SpellPackets.SyncManaPacket`, `ModEvents.revealInvisibilityCloak` (existing).
- Produces:
  - `FiendfyreChannel.TAG_CHANNELING = "hogwartsmagic:fiendfyre_channeling"`, `FiendfyreChannel.TAG_ADVANCE = "hogwartsmagic:fiendfyre_advance"` (unchanged keys)
  - `FiendfyreChannel.isChanneling(Player)` → `boolean`, `heartbeat(ServerPlayer player, ItemStack wand, boolean advance)`, `release(ServerPlayer player, ItemStack wand)`, `stop(ServerPlayer player, ItemStack wand, boolean startCooldown)`, `tick(ServerPlayer player, CompoundTag modData)`
  - package-private `FiendfyreChannel.serpents(ServerPlayer)` → `List<FiendfyreSerpentEntity>`
  - `FiendfyreSerpentEntity.STATE_CONTROLLED/STATE_TELEGRAPH/STATE_ROGUE/STATE_DISPATCHED` (`byte`), `setCaster(ServerPlayer)`, `configure(int index, int count, int rogueTurnTick, int durationTicks)`, `getState()` → `byte`, `isTelegraphing()`, `isRogue()` (both synced, safe on the client)

The channel today is split between `SpellPackets` (start, heartbeat, stop on release) and `ModEvents` (tick, upkeep, stop); it moves whole into `FiendfyreChannel`, with the same persistent-data keys, costs and upkeep, plus the rank rules.

- [ ] **Step 1: The channel**

`src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java`:
```java
package com.hogwartsmagic.spell.summon;

import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The held Fiendfyre channel. Hold the cast key and serpents of cursed fire follow your aim; hold attack
 * to drive them further out. Rank sets how long the fire lasts, how many serpents answer, and how likely
 * one is to turn on its caster after glowing red for a second (Spell Mastery Phase 7a). Releasing,
 * switching off the spell, running out of mana, a stun or silence, or the rank duration ends it and
 * starts the cooldown; a rogue serpent outlives the channel until the cast's time is up. Server only.
 */
public final class FiendfyreChannel {
    public static final String TAG_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
    public static final String TAG_ADVANCE = "hogwartsmagic:fiendfyre_advance";
    private static final String TAG_LAST_DRAIN = "hogwartsmagic:fiendfyre_last_drain";
    private static final String TAG_LAST_SEEN = "hogwartsmagic:fiendfyre_last_seen";
    private static final String TAG_SERPENTS = "hogwartsmagic:fiendfyre_serpents";
    private static final String TAG_STARTED = "hogwartsmagic:fiendfyre_started";
    private static final String TAG_RANK = "hogwartsmagic:fiendfyre_rank";
    private static final String TAG_DISPATCH_INDEX = "hogwartsmagic:fiendfyre_dispatch_index";
    /** The single-serpent key used before Phase 7a; cleared on stop so old saves don't keep it. */
    private static final String LEGACY_TAG_ENTITY = "hogwartsmagic:fiendfyre_entity";
    private static final int UPKEEP_INTERVAL = 4;
    private static final int HEARTBEAT_TIMEOUT = 4;

    private FiendfyreChannel() {
    }

    public static boolean isChanneling(Player player) {
        return player.getPersistentData().getBoolean(TAG_CHANNELING);
    }

    /** Every held tick: starts the channel on the first one (cooldown and startup mana), then keeps it alive. */
    public static void heartbeat(ServerPlayer player, ItemStack wand, boolean advance) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (data.getBoolean(TAG_CHANNELING)) {
            data.putLong(TAG_LAST_SEEN, now);
            data.putBoolean(TAG_ADVANCE, advance);
            return;
        }

        boolean creative = player.isCreative();
        if (!creative && MagicWandItem.isOnCooldown(wand, SpellType.FIENDFYRE, now)) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(player);
        float maxMana = PlayerMagicData.getMaxMana(player);
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float startup = startupCost(player, wand);
        if (!creative && mana < startup) {
            player.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!creative) {
            mana -= startup;
            modData.putFloat("Mana", mana);
            PlayerMagicData.saveModData(player, modData);
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(mana, maxMana), PacketDistributor.PLAYER.with(player));
        }

        int rank = SpellMasteryData.getRank(player, SpellType.FIENDFYRE);
        data.putBoolean(TAG_CHANNELING, true);
        data.putLong(TAG_LAST_DRAIN, now);
        data.putLong(TAG_LAST_SEEN, now);
        data.putBoolean(TAG_ADVANCE, advance);
        data.putLong(TAG_STARTED, now);
        data.putInt(TAG_RANK, rank);
        data.putInt(TAG_DISPATCH_INDEX, 0);
        spawnSerpents(player, rank);

        ModEvents.revealInvisibilityCloak(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SpellType.FIENDFYRE.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 0.86f);
    }

    /** The cast key was let go (or the caster was interrupted): end it and start the cooldown. */
    public static void release(ServerPlayer player, ItemStack wand) {
        if (isChanneling(player)) {
            stop(player, wand, true);
        }
    }

    public static void stop(ServerPlayer player, ItemStack wand, boolean startCooldown) {
        CompoundTag data = player.getPersistentData();
        for (FiendfyreSerpentEntity serpent : serpents(player)) {
            if (!serpent.isRogue()) {
                serpent.discard(); // a rogue serpent is nobody's to dismiss
            }
        }
        data.remove(TAG_CHANNELING);
        data.remove(TAG_LAST_DRAIN);
        data.remove(TAG_LAST_SEEN);
        data.remove(TAG_ADVANCE);
        data.remove(TAG_SERPENTS);
        data.remove(TAG_STARTED);
        data.remove(TAG_RANK);
        data.remove(TAG_DISPATCH_INDEX);
        data.remove(LEGACY_TAG_ENTITY);
        if (startCooldown && !player.isCreative() && MagicWandItem.isAnyWand(wand)) {
            SpellCosts.startCooldown(player, wand, SpellType.FIENDFYRE, player.level().getGameTime());
        }
    }

    /** Player tick: wand and heartbeat checks, the rank duration, lost serpents, and the mana upkeep. */
    public static void tick(ServerPlayer player, CompoundTag modData) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_CHANNELING)) {
            return;
        }
        ItemStack wand = player.getMainHandItem();
        SpellType selected = MagicWandItem.isAnyWand(wand) ? MagicWandItem.getSelectedSpell(wand) : null;
        if (selected != SpellType.FIENDFYRE || !player.isAlive()) {
            stop(player, wand, true);
            return;
        }
        long now = player.level().getGameTime();
        if (now - data.getLong(TAG_LAST_SEEN) > HEARTBEAT_TIMEOUT) {
            stop(player, wand, true);
            return;
        }
        int rank = Math.max(1, data.getInt(TAG_RANK));
        if (FiendfyreRules.expired(data.getLong(TAG_STARTED), now, rank)) {
            stop(player, wand, true);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.burnt_out")
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        int controlled = 0;
        for (FiendfyreSerpentEntity serpent : serpents(player)) {
            if (!serpent.isRogue()) {
                serpent.setCaster(player);
                controlled++;
            }
        }
        if (controlled == 0) {
            stop(player, wand, true); // the only serpent went rogue, or the fire was lost in an unloaded chunk
            return;
        }

        float maxMana = PlayerMagicData.getMaxMana(player);
        float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        float upkeepCost = upkeepCost(player, wand);
        long lastDrain = data.getLong(TAG_LAST_DRAIN);
        if (!player.isCreative() && now - lastDrain >= UPKEEP_INTERVAL) {
            if (currentMana < upkeepCost) {
                stop(player, wand, true);
                player.displayClientMessage(Component.literal("Fiendfyre collapses").withStyle(ChatFormatting.GOLD), true);
                return;
            }
            float previousMana = currentMana;
            currentMana -= upkeepCost;
            modData.putFloat("Mana", currentMana);
            PlayerMagicData.saveModData(player, modData);
            data.putLong(TAG_LAST_DRAIN, now);
            if ((int) previousMana != (int) currentMana) {
                SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(currentMana, maxMana), PacketDistributor.PLAYER.with(player));
            }
        }
    }

    /** The serpents this channel spawned that are still loaded and alive (rogue ones included). */
    static List<FiendfyreSerpentEntity> serpents(ServerPlayer player) {
        List<FiendfyreSerpentEntity> out = new ArrayList<>();
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_SERPENTS)) {
            return out;
        }
        ServerLevel level = player.serverLevel();
        ListTag ids = data.getList(TAG_SERPENTS, Tag.TAG_INT_ARRAY);
        for (Tag id : ids) {
            Entity entity = level.getEntity(NbtUtils.loadUUID(id));
            if (entity instanceof FiendfyreSerpentEntity serpent && serpent.isAlive()) {
                out.add(serpent);
            }
        }
        return out;
    }

    /** One roll per cast decides whether a serpent will turn, which one, and when. */
    private static void spawnSerpents(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        int count = FiendfyreRules.serpentCount(rank);
        int duration = FiendfyreRules.durationTicks(rank);
        int rogueIndex = -1;
        int turnTick = -1;
        if (FiendfyreRules.goesRogue(rank, random.nextDouble())) {
            rogueIndex = FiendfyreRules.rogueIndex(count, random.nextDouble());
            turnTick = FiendfyreRules.rogueTurnTick(duration, random.nextDouble());
        }
        ListTag ids = new ListTag();
        for (int i = 0; i < count; i++) {
            FiendfyreSerpentEntity serpent = new FiendfyreSerpentEntity(ModEntities.FIENDFYRE_SERPENT.get(), level);
            serpent.setCaster(player);
            serpent.configure(i, count, i == rogueIndex ? turnTick : -1, duration);
            serpent.moveTo(player.getX(), player.getEyeY(), player.getZ(), player.getYRot(), player.getXRot());
            level.addFreshEntity(serpent);
            ids.add(NbtUtils.createUUID(serpent.getUUID()));
        }
        player.getPersistentData().put(TAG_SERPENTS, ids);
    }

    private static float startupCost(ServerPlayer player, ItemStack wand) {
        return Math.max(3.0f, SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE) * 0.16f);
    }

    private static float upkeepCost(ServerPlayer player, ItemStack wand) {
        return Math.max(1.25f, (SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE)) / 14.0f);
    }
}
```

- [ ] **Step 2: Replace the serpent.** Replace the whole content of `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` with:
```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.summon.FiendfyreChannel;
import com.hogwartsmagic.spell.summon.FiendfyreRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * One serpent of a held Fiendfyre channel (FiendfyreChannel). While controlled it flies where its
 * caster aims, at its place in the formation. A serpent configured to turn glows red for
 * FiendfyreRules.TELEGRAPH_TICKS (synced state; the renderer tints it, the server adds red dust), then
 * goes rogue: it hunts the nearest living creature, its caster included, until the cast's time is up,
 * independent of the channel. All fire it sets goes through TerrainRules.canPlace.
 */
public class FiendfyreSerpentEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(FiendfyreSerpentEntity.class, EntityDataSerializers.BYTE);
    public static final byte STATE_CONTROLLED = 0;
    public static final byte STATE_TELEGRAPH = 1;
    public static final byte STATE_ROGUE = 2;
    public static final byte STATE_DISPATCHED = 3;
    private static final DustParticleOptions WARNING_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.05F), 2.0F);
    private static final double CONTROL_DISTANCE = 6.2D;
    private static final double ADVANCE_BONUS_DISTANCE = 4.8D;
    private static final double MAX_STEP = 0.72D;
    private static final double ADVANCE_MAX_STEP = 1.08D;
    private static final double TURN_RESPONSE = 0.18D;
    private static final double ADVANCE_TURN_RESPONSE = 0.12D;
    private static final int TRAIL_LENGTH = 18;

    @Nullable
    private UUID casterUuid;
    private int formationIndex;
    private int formationCount = 1;
    /** Life tick at which this serpent turns rogue; -1 if it never will. */
    private int rogueTurnTick = -1;
    /** Life tick at which a rogue serpent burns out. */
    private int rogueEndTick = -1;
    @Nullable
    private UUID rogueTargetId;
    private final Deque<Vec3> trailPoints = new ArrayDeque<>();

    public FiendfyreSerpentEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
        builder.define(DATA_STATE, STATE_CONTROLLED);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifeTicks(tag.getInt("LifeTicks"));
        this.casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        this.formationIndex = tag.getInt("Index");
        this.formationCount = Math.max(1, tag.getInt("Count"));
        this.rogueTurnTick = tag.contains("RogueTurn") ? tag.getInt("RogueTurn") : -1;
        this.rogueEndTick = tag.contains("RogueEnd") ? tag.getInt("RogueEnd") : -1;
        this.setState(tag.getByte("State"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", getLifeTicks());
        if (this.casterUuid != null) {
            tag.putUUID("Caster", this.casterUuid);
        }
        tag.putInt("Index", this.formationIndex);
        tag.putInt("Count", this.formationCount);
        tag.putInt("RogueTurn", this.rogueTurnTick);
        tag.putInt("RogueEnd", this.rogueEndTick);
        tag.putByte("State", this.getState());
    }

    public int getLifeTicks() {
        return this.entityData.get(DATA_LIFE_TICKS);
    }

    public void setLifeTicks(int ticks) {
        this.entityData.set(DATA_LIFE_TICKS, ticks);
    }

    public void setCaster(ServerPlayer caster) {
        this.casterUuid = caster.getUUID();
    }

    /**
     * Place {@code index} of {@code count} in the formation; {@code rogueTurnTick} is the life tick it
     * turns at (-1: never) and {@code durationTicks} the cast's length. Call before adding it.
     */
    public void configure(int index, int count, int rogueTurnTick, int durationTicks) {
        this.formationIndex = Math.max(0, index);
        this.formationCount = Math.max(1, count);
        this.rogueTurnTick = rogueTurnTick;
        this.rogueEndTick = rogueTurnTick >= 0 ? FiendfyreRules.rogueLifeEnd(durationTicks, rogueTurnTick) : -1;
    }

    public byte getState() {
        return this.entityData.get(DATA_STATE);
    }

    private void setState(byte state) {
        this.entityData.set(DATA_STATE, state);
    }

    public boolean isTelegraphing() {
        return this.getState() == STATE_TELEGRAPH;
    }

    public boolean isRogue() {
        return this.getState() == STATE_ROGUE;
    }

    @Override
    public void tick() {
        super.tick();

        int ticks = getLifeTicks() + 1;
        setLifeTicks(ticks);
        this.noPhysics = true;
        this.setNoGravity(true);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.isRogue()) {
            tickRogue(serverLevel, ticks);
            return;
        }

        ServerPlayer caster = getCaster(serverLevel);
        if (caster == null || !caster.isAlive() || !FiendfyreChannel.isChanneling(caster)) {
            this.discard();
            return;
        }
        if (this.rogueTurnTick >= 0) {
            if (ticks >= this.rogueTurnTick) {
                turnRogue(serverLevel, caster);
                tickRogue(serverLevel, ticks);
                return;
            }
            if (!this.isTelegraphing() && FiendfyreRules.telegraphing(ticks, this.rogueTurnTick)) {
                beginTelegraph(serverLevel, caster);
            }
        }

        steerToCasterAim(caster, ticks);
        recordTrail();
        scorchNearby(caster);

        if (ticks % 2 == 0) {
            spreadFire(serverLevel, caster);
        }
        spawnTrailParticles(serverLevel, ticks);
        spawnBurstParticles(serverLevel, ticks);
        if (this.isTelegraphing() && ticks % 2 == 0) {
            serverLevel.sendParticles(WARNING_DUST, this.getX(), this.getY(), this.getZ(), 6, 0.5D, 0.5D, 0.5D, 0.0D);
        }
    }

    @Nullable
    private ServerPlayer getCaster(ServerLevel level) {
        return this.casterUuid == null ? null : level.getServer().getPlayerList().getPlayer(this.casterUuid);
    }

    /** The mandatory one-second warning (roadmap Decision 11). */
    private void beginTelegraph(ServerLevel level, ServerPlayer caster) {
        this.setState(STATE_TELEGRAPH);
        caster.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.writhes")
                .withStyle(ChatFormatting.RED), true);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f);
    }

    private void turnRogue(ServerLevel level, ServerPlayer caster) {
        this.setState(STATE_ROGUE);
        this.rogueTargetId = null;
        caster.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.rogue")
                .withStyle(ChatFormatting.DARK_RED), true);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    /** Rogue: hunt the nearest living creature (caster included) until the cast's time is up. */
    private void tickRogue(ServerLevel level, int ticks) {
        if (this.rogueEndTick >= 0 && ticks >= this.rogueEndTick) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 20, 0.6D, 0.6D, 0.6D, 0.02D);
            this.discard();
            return;
        }
        LivingEntity target = null;
        if (this.rogueTargetId != null && level.getEntity(this.rogueTargetId) instanceof LivingEntity living && living.isAlive()) {
            target = living;
        }
        if (target == null || ticks % FiendfyreRules.ROGUE_RETARGET_INTERVAL == 0) {
            target = nearestVictim(level);
            this.rogueTargetId = target == null ? null : target.getUUID();
        }
        Vec3 desired = target != null
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                : this.position().add(0.0D, Math.sin(ticks * 0.3D) * 0.3D, 0.0D);
        moveToward(desired, MAX_STEP, TURN_RESPONSE);
        recordTrail();
        Entity caster = this.casterUuid == null ? null : level.getEntity(this.casterUuid);
        scorchNearby(null);
        if (ticks % 2 == 0) {
            spreadFire(level, caster);
            level.sendParticles(WARNING_DUST, this.getX(), this.getY(), this.getZ(), 6, 0.5D, 0.5D, 0.5D, 0.0D);
        }
        spawnTrailParticles(level, ticks);
        spawnBurstParticles(level, ticks);
    }

    @Nullable
    private LivingEntity nearestVictim(ServerLevel level) {
        double radius = FiendfyreRules.ROGUE_SEARCH_RADIUS;
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius),
                c -> c.isAlive() && !c.isSpectator() && !(c instanceof Player player && player.isCreative()))) {
            double distance = candidate.distanceToSqr(this);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private void steerToCasterAim(ServerPlayer caster, int ticks) {
        boolean advancing = caster.getPersistentData().getBoolean(FiendfyreChannel.TAG_ADVANCE);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 direction = look.normalize();
        double distanceScale = CONTROL_DISTANCE + (advancing ? ADVANCE_BONUS_DISTANCE : 0.0D);
        Vec3 desired = eye.add(direction.scale(distanceScale));
        double lateral = FiendfyreRules.lateralOffset(this.formationIndex, this.formationCount);
        if (lateral != 0.0D) {
            Vec3 right = new Vec3(-direction.z, 0.0D, direction.x);
            if (right.lengthSqr() > 1.0E-6D) {
                desired = desired.add(right.normalize().scale(lateral));
            }
        }
        desired = desired.add(0.0D, Math.sin(ticks * 0.3D + this.formationIndex) * (advancing ? 0.25D : 0.45D), 0.0D);
        moveToward(desired, advancing ? ADVANCE_MAX_STEP : MAX_STEP, advancing ? ADVANCE_TURN_RESPONSE : TURN_RESPONSE);
    }

    private void moveToward(Vec3 desired, double maxStep, double turnResponse) {
        Vec3 current = this.position();
        Vec3 desiredMovement = desired.subtract(current);
        if (desiredMovement.length() > maxStep) {
            desiredMovement = desiredMovement.normalize().scale(maxStep);
        }
        Vec3 movement = this.getDeltaMovement().scale(1.0D - turnResponse).add(desiredMovement.scale(turnResponse));
        if (movement.lengthSqr() > (maxStep * maxStep)) {
            movement = movement.normalize().scale(maxStep);
        }
        Vec3 next = current.add(movement);
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(movement);
        if (movement.horizontalDistanceSqr() > 1.0E-4D) {
            this.setYRot((float) (Mth.atan2(movement.z, movement.x) * (180F / Math.PI)) - 90.0F);
        }
    }

    private void recordTrail() {
        this.trailPoints.addFirst(this.position());
        while (this.trailPoints.size() > TRAIL_LENGTH) {
            this.trailPoints.removeLast();
        }
    }

    /** Burns everything close; {@code spared} (the caster of a controlled serpent) is left alone. */
    private void scorchNearby(@Nullable Entity spared) {
        AABB area = this.getBoundingBox().inflate(2.6D, 1.4D, 2.6D);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && entity != spared)) {
            target.igniteForSeconds(5);
            target.hurt(target.damageSources().onFire(), 4.0f);
            Vec3 lift = target.position().subtract(this.position());
            if (lift.lengthSqr() > 1.0E-4D) {
                Vec3 push = lift.normalize().scale(0.32D);
                target.push(push.x, 0.12D, push.z);
            }
        }
    }

    private void spawnTrailParticles(ServerLevel serverLevel, int ticks) {
        Vec3 previous = null;
        int index = 0;
        for (Vec3 point : this.trailPoints) {
            serverLevel.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 3, 0.18D, 0.18D, 0.18D, 0.01D);
            if (index % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.LAVA, point.x, point.y, point.z, 1, 0.08D, 0.08D, 0.08D, 0.0D);
            }
            if (previous != null) {
                Vec3 segment = previous.subtract(point);
                int steps = Math.max(2, (int) Math.ceil(segment.length() * 5.0D));
                for (int i = 1; i < steps; i++) {
                    Vec3 mid = point.add(segment.scale(i / (double) steps));
                    serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, mid.x, mid.y, mid.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
                }
            }
            previous = point;
            index++;
        }

        if (ticks % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.25D, 0.2D, 0.01D);
        }
    }

    private void spawnBurstParticles(ServerLevel serverLevel, int ticks) {
        if (ticks % 4 != 0) {
            return;
        }
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i) / 12.0D + ticks * 0.08D;
            double radius = 1.4D + Math.sin(ticks * 0.22D + i) * 0.25D;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + 0.2D + (i % 3) * 0.18D;
            double z = this.getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.04D, 0.04D, 0.04D, 0.01D);
            if (i % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.01D, 0.05D, 0.01D, 0.0D);
            }
        }
    }

    /** Terrain-safe fire: loaded, empty positions only, and TerrainRules.canPlace as the caster. */
    private void spreadFire(ServerLevel level, @Nullable Entity caster) {
        BlockPos center = this.blockPosition();
        int radius = 2 + Math.min(5, getLifeTicks() / 20);

        for (int i = 0; i < 8; i++) {
            int rx = this.random.nextInt(radius * 2 + 1) - radius;
            int ry = this.random.nextInt(5) - 2;
            int rz = this.random.nextInt(radius * 2 + 1) - radius;
            BlockPos target = center.offset(rx, ry, rz);
            if (!level.isLoaded(target) || !level.isEmptyBlock(target)) {
                continue;
            }
            if (TerrainRules.canPlace(level, target, caster)) {
                level.setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
```
(`ExampleMod`, `HorcruxBlockEntity` and `BlockState` are imported for Task 10; so are `LivingEntity`, `ProjectileUtil`, `AABB`, `EntityHitResult`, `HitResult`, `Vec3` and `Nullable` in `FiendfyreChannel`. Unused imports compile.)

- [ ] **Step 3: The red telegraph on screen.** In `FiendfyreSerpentRenderer.java`, replace:
```java
        // Layered fire passes.
        for (float[] layer : LAYERS) {
            float radMul = layer[0];
            float r = layer[1], g = layer[2], b = layer[3], a = layer[4];
```
with:
```java
        // Layered fire passes. A serpent about to turn (or already rogue) burns blood red; while it is
        // only warning, the glow also pulses (Spell Mastery Phase 7a, roadmap Decision 11).
        boolean warning = entity.isTelegraphing() || entity.isRogue();
        float warningPulse = entity.isTelegraphing() ? 0.7f + 0.3f * (float) Math.sin(age * 1.6f) : 1.0f;
        for (float[] layer : LAYERS) {
            float radMul = layer[0];
            float r = layer[1], g = layer[2], b = layer[3], a = layer[4];
            if (warning) {
                r = 1.0f;
                g *= 0.18f;
                b *= 0.18f;
                a *= warningPulse;
            }
```

- [ ] **Step 4: The cast path delegates.** In `SpellPackets.java`, inside `CastSpellPacket.castAs`, in the release block replace:
```java
                if (playerData.getBoolean(TAG_FIENDFYRE_CHANNELING)) {
                    stopFiendfyreChannel(player, wand, now, creative);
                }
```
with:
```java
                com.hogwartsmagic.spell.summon.FiendfyreChannel.release(player, wand);
```
Replace the whole Fiendfyre heartbeat block:
```java
            if (p.channelHeartbeat && spell == SpellType.FIENDFYRE) {
                if (!creative && MagicWandItem.isOnCooldown(wand, spell, now)) return;

                net.minecraft.nbt.CompoundTag modData = playerData.getCompound("PlayerPersisted").getCompound(ExampleMod.MODID);
                float maxMana = PlayerMagicData.getMaxMana(player);
                float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
                float startupCost = getFiendfyreStartupCost(player, wand);

                if (playerData.getBoolean(TAG_FIENDFYRE_CHANNELING)) {
                    playerData.putLong(TAG_FIENDFYRE_LAST_SEEN, now);
                    playerData.putBoolean(TAG_FIENDFYRE_ADVANCE, p.fiendfyreAdvance);
                    ensureFiendfyreExists(player);
                    return;
                }

                if (!creative && currentMana < startupCost) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("Not enough mana!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }

                if (!creative) {
                    currentMana -= startupCost;
                    modData.putFloat("Mana", currentMana);
                    player.getPersistentData().getCompound("PlayerPersisted").put(ExampleMod.MODID, modData);
                    CHANNEL.send(new SyncManaPacket(currentMana, maxMana), net.minecraftforge.network.PacketDistributor.PLAYER.with(player));
                }

                playerData.putBoolean(TAG_FIENDFYRE_CHANNELING, true);
                playerData.putLong(TAG_FIENDFYRE_LAST_DRAIN, now);
                playerData.putLong(TAG_FIENDFYRE_LAST_SEEN, now);
                playerData.putBoolean(TAG_FIENDFYRE_ADVANCE, p.fiendfyreAdvance);
                ensureFiendfyreExists(player);
                com.hogwartsmagic.event.ModEvents.revealInvisibilityCloak(player);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        spell.getSoundEvent(), SoundSource.PLAYERS, 1.0f, 0.86f);
                return;
            }
```
with:
```java
            if (p.channelHeartbeat && spell == SpellType.FIENDFYRE) {
                // The whole channel (rank duration, serpents, rogue roll, upkeep) is FiendfyreChannel's.
                com.hogwartsmagic.spell.summon.FiendfyreChannel.heartbeat(player, wand, p.fiendfyreAdvance);
                return;
            }
```
Delete the three helper methods below `castAs`, each from its signature line to its closing brace: `private static float getFiendfyreStartupCost(ServerPlayer player, ItemStack wand) {`, `private static void ensureFiendfyreExists(ServerPlayer player) {` and `private static void stopFiendfyreChannel(ServerPlayer player, ItemStack wand, long now, boolean creative) {`. Leave the private `TAG_FIENDFYRE_*` constants: the release block still clears `TAG_FIENDFYRE_ADVANCE` (Phase 6a uses that line as an anchor).
Run: `grep -n "ensureFiendfyreExists\|getFiendfyreStartupCost\|stopFiendfyreChannel" src/main/java/com/hogwartsmagic/network/SpellPackets.java`
Expected: no output.

- [ ] **Step 5: The player tick delegates.** In `ModEvents.java`, replace:
```java
            tickFiendfyreChannel(player, modData);
```
with:
```java
            com.hogwartsmagic.spell.summon.FiendfyreChannel.tick(player, modData);
```
In `interruptActiveCasting`, replace:
```java
        if (player.getPersistentData().getBoolean(TAG_FIENDFYRE_CHANNELING)) {
            stopFiendfyreChannel(player, wand);
        }
```
with:
```java
        com.hogwartsmagic.spell.summon.FiendfyreChannel.release(player, wand);
```
Delete these six constants:
```java
    private static final String TAG_FIENDFYRE_CHANNELING = "hogwartsmagic:fiendfyre_channeling";
    private static final String TAG_FIENDFYRE_LAST_DRAIN = "hogwartsmagic:fiendfyre_last_drain";
    private static final String TAG_FIENDFYRE_LAST_SEEN = "hogwartsmagic:fiendfyre_last_seen";
    private static final String TAG_FIENDFYRE_ENTITY = "hogwartsmagic:fiendfyre_entity";
    private static final int FIENDFYRE_UPKEEP_INTERVAL = 4;
    private static final int FIENDFYRE_HEARTBEAT_TIMEOUT = 4;
```
and delete these four methods, each from its signature line to its closing brace: `private static void tickFiendfyreChannel(ServerPlayer player, net.minecraft.nbt.CompoundTag modData) {`, `private static com.hogwartsmagic.entity.FiendfyreSerpentEntity getOrCreateFiendfyreEntity(ServerPlayer player, ServerLevel level) {`, `private static void stopFiendfyreChannel(ServerPlayer player, ItemStack wand) {` and `private static float getFiendfyreUpkeepCost(Player player, ItemStack wand) {`.
Run: `grep -n "FIENDFYRE_\|tickFiendfyreChannel\|getOrCreateFiendfyreEntity\|stopFiendfyreChannel\|getFiendfyreUpkeepCost" src/main/java/com/hogwartsmagic/event/ModEvents.java`
Expected: no output.

- [ ] **Step 6: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.fiendfyre.desc": "Summons uncontrollable, destructive cursed fire.",
```
with:
```json
  "spell.hogwartsmagic.fiendfyre.desc": "Hold to loose serpents of cursed fire that follow your aim; hold attack to drive them further. Novice: one serpent for 6s, with a 15%% chance it turns on you (it glows red first). Adept: 8s, 8%%. Expert: 10s, 3%%, two serpents. Master: 12s, never rogue, three serpents; left-click while holding sends the next one at the creature you aim at, and the fire devours Horcruxes.",
  "message.hogwartsmagic.fiendfyre.writhes": "The Fiendfyre writhes against your will!",
  "message.hogwartsmagic.fiendfyre.rogue": "The Fiendfyre turns on you!",
  "message.hogwartsmagic.fiendfyre.burnt_out": "The Fiendfyre burns itself out",
```

- [ ] **Step 7: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `FiendfyreChannel`, `FiendfyreSerpentEntity` or any `client` class (the new synced byte registers on the server).

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/java/com/hogwartsmagic/client/FiendfyreSerpentRenderer.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Shape Fiendfyre by rank with split serpents and rogue fire that glows red before it turns" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Master Fiendfyre (separate targets, Horcrux destruction)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `FiendfyreRules.dispatches/destroysHorcrux/DISPATCH_TICKS/DISPATCH_RANGE/HORCRUX_REACH/HORCRUX_SCAN_INTERVAL` (Task 1); `TerrainRules.canBreak` (Phase 4: posts the real `BreakEvent`, so it is the last step before the block is destroyed); `HorcruxBlockEntity.isActive/isPrimed`, `ExampleMod.HORCRUX` (existing; `ModEvents.onBlockBreak` already runs the Horcrux's forced-destruction effects, including the Dementor for a hostile break, when that `BreakEvent` is posted).
- Produces: `FiendfyreSerpentEntity.dispatchAt(LivingEntity target, long now)`, `isAvailableForDispatch()` → `boolean`, `setDestroysHorcrux(boolean)`

Each fresh press of the attack key while the Fiendfyre is held (the heartbeat already carries "attack held" as `fiendfyreAdvance`; its rising edge is the press) sends the next free serpent at the creature under the crosshair within 48 blocks. A sent serpent homes in for up to 4s, then returns to the formation. With nothing under the crosshair the press only drives the fire forward, as at lower ranks.

- [ ] **Step 1: The serpent's new abilities.** In `FiendfyreSerpentEntity.java`, replace:
```java
    @Nullable
    private UUID rogueTargetId;
    private final Deque<Vec3> trailPoints = new ArrayDeque<>();
```
with:
```java
    @Nullable
    private UUID rogueTargetId;
    @Nullable
    private UUID dispatchTargetId;
    private long dispatchUntil;
    private boolean destroysHorcrux;
    private final Deque<Vec3> trailPoints = new ArrayDeque<>();
```
Replace (in `tick()`, the controlled serpent's movement):
```java
        steerToCasterAim(caster, ticks);
        recordTrail();
        scorchNearby(caster);
```
with:
```java
        if (this.getState() != STATE_DISPATCHED || !tickDispatch(serverLevel, caster)) {
            steerToCasterAim(caster, ticks);
        }
        recordTrail();
        scorchNearby(caster);
        if (this.destroysHorcrux && ticks % FiendfyreRules.HORCRUX_SCAN_INTERVAL == 0) {
            devourHorcruxes(serverLevel, caster);
        }
```
Directly above the `getCaster` method (its two lines `    @Nullable` and `    private ServerPlayer getCaster(ServerLevel level) {`), add:
```java
    public void setDestroysHorcrux(boolean destroys) {
        this.destroysHorcrux = destroys;
    }

    public boolean isAvailableForDispatch() {
        return this.getState() == STATE_CONTROLLED;
    }

    /** Master: home in on {@code target} for DISPATCH_TICKS, then rejoin the formation. */
    public void dispatchAt(LivingEntity target, long now) {
        this.dispatchTargetId = target.getUUID();
        this.dispatchUntil = now + FiendfyreRules.DISPATCH_TICKS;
        this.setState(STATE_DISPATCHED);
    }

    /** Returns false (and rejoins the formation) once the target is gone, out of range or time is up. */
    private boolean tickDispatch(ServerLevel level, ServerPlayer caster) {
        LivingEntity target = null;
        if (this.dispatchTargetId != null && level.getEntity(this.dispatchTargetId) instanceof LivingEntity living && living.isAlive()) {
            target = living;
        }
        if (target == null || level.getGameTime() > this.dispatchUntil
                || target.distanceToSqr(caster) > FiendfyreRules.DISPATCH_RANGE * FiendfyreRules.DISPATCH_RANGE) {
            this.dispatchTargetId = null;
            this.setState(STATE_CONTROLLED);
            return false;
        }
        moveToward(target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), ADVANCE_MAX_STEP, ADVANCE_TURN_RESPONSE);
        return true;
    }

    /**
     * Master: an active or primed Horcrux within HORCRUX_REACH is destroyed (canon: Fiendfyre is one of the
     * few things that can). TerrainRules.canBreak is the last check; it posts the real BreakEvent, which is
     * what runs the Horcrux's own break effects, and a true result means the block is destroyed now.
     */
    private void devourHorcruxes(ServerLevel level, @Nullable Entity caster) {
        BlockPos center = this.blockPosition();
        int reach = FiendfyreRules.HORCRUX_REACH;
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (!level.isLoaded(scan)) {
                continue;
            }
            BlockState state = level.getBlockState(scan);
            if (!state.is(ExampleMod.HORCRUX.get())) {
                continue;
            }
            if (!(level.getBlockEntity(scan) instanceof HorcruxBlockEntity horcrux) || !(horcrux.isActive() || horcrux.isPrimed())) {
                continue;
            }
            BlockPos pos = scan.immutable();
            if (!TerrainRules.canBreak(level, pos, state, caster)) {
                continue;
            }
            level.destroyBlock(pos, false);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                    30, 0.4D, 0.4D, 0.4D, 0.04D);
            if (caster instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.horcrux")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

```

- [ ] **Step 2: The channel sends serpents.** In `FiendfyreChannel.java`, in `heartbeat`, replace:
```java
        if (data.getBoolean(TAG_CHANNELING)) {
            data.putLong(TAG_LAST_SEEN, now);
            data.putBoolean(TAG_ADVANCE, advance);
            return;
        }
```
with:
```java
        if (data.getBoolean(TAG_CHANNELING)) {
            boolean wasAdvancing = data.getBoolean(TAG_ADVANCE);
            data.putLong(TAG_LAST_SEEN, now);
            data.putBoolean(TAG_ADVANCE, advance);
            // A fresh attack press at Master sends the next serpent at whatever is under the crosshair.
            if (advance && !wasAdvancing && FiendfyreRules.dispatches(Math.max(1, data.getInt(TAG_RANK)))) {
                dispatchNext(player, now);
            }
            return;
        }
```
In `spawnSerpents`, replace:
```java
            serpent.configure(i, count, i == rogueIndex ? turnTick : -1, duration);
```
with:
```java
            serpent.configure(i, count, i == rogueIndex ? turnTick : -1, duration);
            serpent.setDestroysHorcrux(FiendfyreRules.destroysHorcrux(rank));
```
Directly above the line `    /** One roll per cast decides whether a serpent will turn, which one, and when. */` add:
```java
    private static void dispatchNext(ServerPlayer player, long now) {
        LivingEntity target = aimedTarget(player);
        if (target == null) {
            return;
        }
        List<FiendfyreSerpentEntity> free = new ArrayList<>();
        for (FiendfyreSerpentEntity serpent : serpents(player)) {
            if (serpent.isAvailableForDispatch()) {
                free.add(serpent);
            }
        }
        if (free.isEmpty()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        int index = Math.floorMod(data.getInt(TAG_DISPATCH_INDEX), free.size());
        free.get(index).dispatchAt(target, now);
        data.putInt(TAG_DISPATCH_INDEX, index + 1);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.fiendfyre.dispatch", target.getDisplayName())
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** The living creature under the crosshair within DISPATCH_RANGE, stopping at the first block. */
    @Nullable
    private static LivingEntity aimedTarget(ServerPlayer player) {
        double range = FiendfyreRules.DISPATCH_RANGE;
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult block = player.pick(range, 1.0F, false);
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(start);
        Vec3 end = start.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.5D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, start, end, box,
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity.isPickable()
                        && !entity.isSpectator() && entity != player);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

```

- [ ] **Step 3: Strings.** In `en_us.json`, directly after `  "message.hogwartsmagic.fiendfyre.burnt_out": "The Fiendfyre burns itself out",` insert:
```json
  "message.hogwartsmagic.fiendfyre.dispatch": "A serpent of fire lunges at %s",
  "message.hogwartsmagic.fiendfyre.horcrux": "The Fiendfyre devours a Horcrux",
```

- [ ] **Step 4: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `FiendfyreChannel`, `FiendfyreSerpentEntity` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master send Fiendfyre serpents at separate targets and burn Horcruxes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Full verification

**Files:** none new.

**Interfaces:** none.

- [ ] **Step 1: Automated checks**

Run: `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/summon src/main/java/com/hogwartsmagic/entity/SnakeEntity.java src/main/java/com/hogwartsmagic/entity/PatronusEntity.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java src/main/java/com/hogwartsmagic/command/PatronusCommand.java`
Expected: no output (common code stays client-free).

Run: `grep -rn "switch (" src/main/java/com/hogwartsmagic/client/renderer/PatronusRenderer.java src/main/java/com/hogwartsmagic/client/renderer/PatronusFormDummies.java src/main/java/com/hogwartsmagic/client/model/SnakeModel.java src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java`
Expected: no output.

Run: `grep -rn "broadcastEntityEvent" src/main/java/com/hogwartsmagic/spell/summon src/main/java/com/hogwartsmagic/entity/SnakeEntity.java src/main/java/com/hogwartsmagic/entity/PatronusEntity.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java`
Expected: no output.

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `spell.summon`, `SnakeEntity`, `PatronusEntity`, `FiendfyreSerpentEntity` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed)

Survival world, cheats on, wand in hand, the three spells learned. Switch ranks with `/spellmastery setrank Dev <spell> <1-4>`; summon Dementors with their spawn egg.
1. Expecto Patronum I, hold: the silver cone pushes a Dementor back; after 4s it fades with "The silver mist fades" and the short cooldown starts. No stag ever appears at Novice.
2. Expecto Patronum II, hold: a corporeal stag every time, hunting the Dementor; it lasts 10s and, with no Dementor near, flies back to you and stays within a few blocks. No light blocks follow it.
3. Expecto Patronum III: chat says "Your Patronus takes its true form: <animal>"; the Patronus is that animal (silver, translucent, glowing) and lasts 20s. Cast again: the same animal, no new message. `/spellmastery reset Dev`, then setrank 3 again and cast: still the same animal (the form is stored outside mastery).
4. `/patronus form <1-8>` (op) before each Expert cast: stag, doe (no antlers), hare, wolf, cat, owl, fox and phoenix each draw in silver, face the way the Patronus flies, animate their legs or wings, and leave no errors in the log.
5. Expecto Patronum IV at night beside zombies and skeletons: the Patronus lights its block and flashes a light ring; zombies within ~6 blocks are shoved away and stop targeting you; it lasts 30s; when it vanishes no light block is left behind (check with F3 light level).
6. `/patronus <friend> hello there` at Master within 500 blocks (two clients): you see "Your Patronus bounds away toward <friend>"; the friend sees your silver animal appear in front of them for 5s and the chat line "A silver Patronus speaks in the voice of Dev: "hello there"". A second message within 30s says "still returning"; at rank III it says only a Master can; 600 blocks away it says beyond your Patronus; mana drops by the spell's cost.
7. Serpensortia I on a zombie: one snake appears and attacks it; a bite gives Poison I for 3s; after 20s the snake vanishes in smoke. Cast on the ground: a snake appears there and follows you.
8. Serpensortia II: two snakes for 30s. III: three snakes, Poison II. A new cast removes the old snakes first. The snakes never attack you, your tamed wolf or another of your snakes; hit a pig and they bite it; let a skeleton shoot you and they go for the skeleton.
9. Serpensortia IV: left-click looking at a cow 15 blocks away: "Your snakes strike at Cow" and they go for it. Left-click at the ground: "coil to guard that spot", they stay near it and attack a zombie that walks within 8 blocks; left-click again: "follow you". At rank III a left-click does nothing.
10. Snake spawn egg: the wild snake attacks you at rank III; at rank IV it ignores you until you hit it.
11. Voldemort casting Serpensortia (or `/summon` a Death Eater fight): his snakes go for you and never for him.
12. Fiendfyre I, hold: one serpent follows your aim and stops after 6s on its own ("burns itself out"), cooldown starts. Over about 10 casts at least one serpent glows and pulses red for 1s with "The Fiendfyre writhes against your will!" and then hunts the nearest creature, you included ("The Fiendfyre turns on you!"), until the 6s are up; your channel ends when it turns.
13. Fiendfyre III: two serpents side by side, 10s. Fiendfyre IV: three serpents, 12s, never red; while holding, left-click a zombie: one serpent breaks formation and burns it, comes back within 4s; the next left-click sends the next serpent.
14. Fiendfyre IV through an active Horcrux (forge one with the ritual on a test world): the serpent destroys it with "The Fiendfyre devours a Horcrux". At rank III it doesn't.
15. `/gamerule mobGriefing false`, then a Death Eater's Fiendfyre beam: flames, damage, but no fire blocks. Your own Fiendfyre inside a Cave Inimicum ward or in spawn protection on a server (non-op): no fire blocks there.
16. Log out mid-Fiendfyre, log back in: no serpent is left, the cooldown is running.

- [ ] **Step 3: Commit** (only if Step 1 or 2 needed a fix; otherwise nothing to commit)
```bash
git add <the files you fixed>
git commit -m "Fix issues found in the Phase 7a verification pass" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage (roadmap Phase 7, first half; design doc F and G):** Patronus Novice incorporeal mist for 4s that pushes Dementors back, no animal (T2); Adept corporeal Patronus for 10s that hunts Dementors (T2); Expert 20s in the caster's own form, chosen once from UUID + house from a fixed list and kept forever, drawn with tinted vanilla models (T1, T3); Master 30s, lights dark areas, repels undead within 6 blocks (T2), carries a message to a player within 500 blocks (T4). Serpensortia snake entity with a Java-coded model and a slither (T5) replacing the cave spiders (T6); 1/2/3 snakes for 20/30/40s with Poison I, Poison II from Expert (T1, T6); Master Parseltongue attack/guard/follow, 60s, hostile snakes ignore the caster (T5, T7); allied through `CombatTags.ALLIED` and ownership (T5, T6). Fiendfyre 6/8/10/12s and rogue 15/8/3/0% (T1, T9), rogue serpents target the nearest creature including the caster (T9) after a mandatory 1s red telegraph (T9, Decision 11), 2 serpents at Expert (T9), 3 at Master sent at separate targets with each new press (T10), Master destroys a Horcrux (T10), block fire respects `mobGriefing`, claims, spawn protection and wards (T8, T9). Corruption: none (Decision 9).
- **Lessons applied:** per-tick work reads fields or a flag before persistent data (snakes, Patronus and serpents use fields; the Fiendfyre player tick keeps its existing flag check); teleports and spawns only into collision-free space (Patronus follow and messenger, snake follow and conjure); the snake guard post stores its dimension; no shared effect is removed; no velocity packets are sent to players; new entities register attributes (snake), renderers only in `ClientModEvents`, a spawn egg only for the snake, synced data in `defineSynchedData` (Patronus form, serpent state); no entity event bytes.
- **Placeholder scan:** no TBD or "similar to"; every edit names its anchor text, including anchors in files this plan creates.
- **Type consistency:** `PatronusEntity.configure(UUID, int, int, boolean)` is used by T2 (`summonCorporeal`) and T4 (messenger); `PatronusSpell.formFor(ServerPlayer)` → `PatronusForm` defined in T3, used in T3 and T4; `PatronusForm.byId(int)` / `id()` agree with the synced byte; `SnakeEntity.conjure(LivingEntity, int, int)`, `isFairTarget(LivingEntity)`, `commandAttack(LivingEntity)`, `commandMode(int, BlockPos, ResourceKey<Level>)` defined in T5, used in T6 and T7; `SerpensortiaSpell.snakesOf(ServerLevel, Entity)` defined in T6, used in T7; `FiendfyreSerpentEntity.configure(int, int, int, int)` and `setCaster(ServerPlayer)` defined in T9, used by `FiendfyreChannel.spawnSerpents`; `dispatchAt(LivingEntity, long)` / `isAvailableForDispatch()` / `setDestroysHorcrux(boolean)` defined and used in T10; `FiendfyreChannel.TAG_CHANNELING` / `TAG_ADVANCE` keep the old key strings, so the existing client heartbeat needs no change.
