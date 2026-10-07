# Dark Magic Phase 2 (The Costs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** The world now reacts to a dark soul. Shadowed halves the chance of a corporeal Patronus (15% to 7.5%); from Tainted the charm always answers as the shield, and insomnia Dementors come after 3 days without rest instead of 5. From Corrupted the shield costs half as much again to hold, Dementors in range choose you before anyone else, students keep about 8 blocks away, and faint smoke rises around you where other players can see it. At Lost you have one heart less (-2 max health), students attack you on sight, and House Elves flee from you even when bound. The Patronus never fails. Every cost reads the tier through `DarkMagicData.getTier`, so `enableDarkMagic = false` turns every one of them off. Rewards, deeds and redemption (Phase 3) and advancements and docs (Phase 4) are not in this plan.

**Architecture:**
- **Pure rules** (`spell/dark/DarkCostRules`): one `Cost` enum with the tier window each cost applies in, and the numbers: the corporeal chance per tier, the shield upkeep multiplier, the insomnia threshold, the max-health delta and the health clamp, the smoke count and its every-10-ticks phase, the NPC distances, the presence cache staleness and the NPC check rate. Minecraft-free, test-first.
- **Server glue** (`spell/dark/DarkCosts`): `tierOf(Entity)` (a player's effective tier, Clean for anything else), a cached "darkest tier online" that every NPC goal checks first (so no NPC searches anything while nobody online is Corrupted or worse), the Lost max-health modifier (idempotent, clamps health down, never kills), the smoke tick, and the two Patronus helpers.
- **Hooks:** `DarkMagicData.sync` (already called on every write, login, respawn, dimension change and config reload) re-applies the health modifier and drops the presence cache; the login handler moves that sync after the house and Horcrux modifiers; one smoke line in `ModEvents.onPlayerTick`; the two corporeal rolls in `SpellPackets.castAs`; `ModEvents.getPatronusShieldUpkeepCost`; the insomnia threshold in `ModEvents.trySpawnInsomniaDementor`.
- **NPC goals** (`entity/DarkPlayerGoals`): `TargetDarkPlayer` (a `NearestAttackableTargetGoal<Player>` filtered by a cost) for Dementors and Students, `AvoidDarkPlayer` (an `AvoidEntityGoal<Player>`) for Students. House Elves get a private `FleeLostWizardGoal` beside their existing flee goal, and their follow goal ignores a Lost master.
- **Nothing new on the wire or on disk:** no config value, no packet, no protocol change, no lang string, no NBT key, no `@SubscribeEvent`. Max health reaches the client through vanilla attribute sync; smoke through vanilla `sendParticles`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-07-dark-magic-roadmap.md`: decision 7 (costs per tier, with the user rulings: the Patronus never fails; Lost costs are -2 max health and House Elves flee; students keep away at Corrupted and attack at Lost; no night Dementors at Lost), decision 6 (tiers), decision 11 (only the smoke shows another player's darkness), decision 13 (`enableDarkMagic` off means every tier reads 0), decision 16 (NPCs have no Darkness), "Phase 2: The costs", "Global constraints". Phases 3 and 4 are out of scope.

## Requirements (Phase 2 of the spec)

1. **Patronus:** the corporeal chance is 15% at Clean, 7.5% at Shadowed, 0% from Tainted (always the shield). The shield's upkeep is +50% from Corrupted. The charm never fails at any tier (decision 7; Phase 2 bullet 1).
2. **Dementors:** a target goal that prefers a Corrupted or Lost player in range (ties go to the nearest); with none in range they target players, Wizards and Students exactly as today (decision 7; Phase 2 bullet 2).
3. **Insomnia Dementors:** after 3 days without rest from Tainted, 5 days below (decision 7; Phase 2 bullet 2). No night Dementors for resting Lost players (user ruling: cut).
4. **Students:** keep about 8 blocks away from a Corrupted player; attack a Lost player on sight; the existing hurt-by behaviour is unchanged (decision 7; Phase 2 bullet 3).
5. **Lost:** -2 max health as a transient attribute modifier, applied and removed on tier change, login, respawn and dimension change, like the Hufflepuff modifier; health above the new max is clamped; removing it never kills; it stacks with the Horcrux -2 and the Hufflepuff +4 (decision 7; Phase 2 bullet 4).
6. **House Elves** flee a Lost player, even when bound (decision 7; Phase 2 bullet 4).
7. **Smoke** particles around a Corrupted or Lost player, visible to other players, rate-limited, no per-tick cost when nobody is dark (decision 7; Phase 2 bullet 5; decision 11).
8. Every cost reads the tier through `DarkMagicData.getTier` and turns off with `enableDarkMagic` (decision 13; "Done when").
9. NPC goal predicates read the tier cheaply and do not scale badly with entities times players (controller brief).
10. Done when: each cost in decision 7 can be seen in play, and each one turns off with the config toggle.

## Rulings made while planning

1. **One cost table.** `DarkCostRules.Cost` lists ten costs, each with an inclusive tier window: `HALF_CORPOREAL_PATRONUS` 1..1, `NO_CORPOREAL_PATRONUS` 2..4, `EARLY_INSOMNIA` 2..4, `SHIELD_UPKEEP` 3..4, `DEMENTORS_PREFER` 3..4, `STUDENTS_KEEP_AWAY` 3..3, `SMOKE` 3..4, `LOST_MAX_HEALTH` 4..4, `STUDENTS_ATTACK` 4..4, `ELVES_FLEE` 4..4. "Each tier keeps the costs below it" holds except for two replacements by a harder cost: no corporeal Patronus replaces the halved chance from Tainted, and students attacking replaces students keeping away at Lost (a student cannot both flee and attack). A tier outside 0..4 is clamped into it.
2. **The corporeal chance** is `base` at Clean, `base * 0.5` at Shadowed, `0` from Tainted. `CORPOREAL_PATRONUS_CHANCE = 0.15f` stays in `SpellPackets` and is passed in; both rolls (the held cast and the tapped cast) change. A chance of 0 means the roll always gives the shield, so the charm never fails. A cast through Imperio possession reads the body's tier (it is the victim's Patronus).
3. **Shield upkeep** is multiplied by 1.5 from Corrupted, after the existing `Math.max(1.5f, ...)` floor, inside `getPatronusShieldUpkeepCost`. So both the "Not enough mana!" check when the shield starts and the drain while it is held use it. The per-Dementor surcharge (`heldOff * PATRONUS_SHIELD_PER_DEMENTOR_COST`) is unchanged.
4. **Insomnia:** `DarkCostRules.BASE_INSOMNIA_TICKS = 5 * 24000` (the old `DEMENTOR_INSOMNIA_TICKS`, removed from `ModEvents`) and `DARK_INSOMNIA_TICKS = 3 * 24000` from Tainted. Everything else (overworld, night or thunder, the 7% chance per 160 ticks, the cap of 4, creative and spectator exempt) is unchanged. The tier is read after the cheap early returns, at most once per 160 ticks per player.
5. **Dementors:** a new `TargetDarkPlayer(DEMENTORS_PREFER)` at target priority 2; the existing player goal moves from 2 to 3 and the Wizard and Student goals from 3 to 4, so their order among themselves is unchanged. A lower number preempts a running higher one, so a Dementor already chasing a Clean player or a Student switches to a Corrupted or Lost player who comes in range. `HurtByTargetGoal` stays at 1. "In range" is the Dementor's follow range (48 blocks) with line of sight, as the existing player goal; ties go to the nearest (the vanilla goal picks the nearest match).
6. **Students:** `AvoidDarkPlayer(STUDENTS_KEEP_AWAY, 8.0F, walk 0.7, sprint 0.9)` at goal priority 4: below the spell-cast goal (3), so a student who has a target (it was hurt, or a Lost player) still fights; above wandering (5); never while the student is in a duel; a sleeping student (priority 2) does not move. `TargetDarkPlayer(STUDENTS_ATTACK)` at target priority 2, beside the Death Eater goal; range is the student follow range (24 blocks) with line of sight. `HurtByTargetGoal` is unchanged.
7. **House Elves:** a bound or freed elf flees any Lost player within 8 blocks (its own master included) until 12 blocks away, and its follow goal neither starts nor continues (so it does not Apparate) for a Lost master. A wild elf keeps its existing flight from every wizard it notices, Lost or not. The bound elf's self-punishment and the freed elf's protection are unchanged.
8. **Creative and spectator players** trigger no NPC reaction (the vanilla combat targeting rules exclude them for Dementors and Students; the elf goal skips them, as `noticesWizard` does). The Patronus, upkeep, insomnia (already creative-exempt), health and smoke costs apply in creative; smoke never shows in spectator.
9. **The Lost health modifier** is `hogwartsmagic:dark_magic_lost_health`, `-2.0`, `ADD_VALUE`, transient. `DarkCosts.refreshMaxHealth` is idempotent: it compares the wanted amount with the modifier present and does nothing when they match; otherwise it removes, re-adds when wanted, and lowers health to the new max only when health is above it (never below 1, never touching a dead player). Removing the modifier raises the max and never changes health. It stacks by id with the Horcrux (`horcrux_health_cost`, -2) and Hufflepuff (`hufflepuff_health_bonus`, +4), whose per-tick re-application touches only their own ids: base 20, Hufflepuff, Horcrux and Lost gives 20.
10. **Where the health cost is applied:** inside `DarkMagicData.sync`, which already runs on every write (gain, decay, command, so every tier change), on login, respawn, dimension change and after a config reload (the toggle). The login handler is reordered so the sync runs after `HousePerks.applyHouseAttributes` and `HorcruxData.applyMaxHealthPenalty` (it clamps against the full max); respawn already has that order. Shown like the Horcrux cost: one heart fewer in the vanilla health row, no new UI.
11. **Smoke:** vanilla `ParticleTypes.SMOKE` through `ServerLevel.sendParticles` (every player within 32 blocks sees it, the dark player too) once every 10 ticks, staggered per player by entity id: 2 particles at Corrupted, 4 at Lost, centred 1 block above the feet, spread 0.3/0.5/0.3, speed 0.005. None while the player is invisible (the Cloak and the potion hide you), a spectator, or dead. It does show under Polyjuice (a dark soul under a borrowed face, as the Sneakoscope). The tick returns on 9 ticks out of 10 before any read.
12. **NPC cost, justified.** `DarkCosts.darkestOnlineTier(level)` is a static cache of the darkest effective tier among online players, recomputed at most once per 20 server ticks (one `getTier` per online player, three compound lookups each) and marked dirty on every `DarkMagicData.sync`. Every new NPC goal checks it first, so while nobody online is Corrupted or worse no NPC goal searches anything (one int compare per goal evaluation). When someone is: `TargetDarkPlayer` keeps the vanilla 1-in-10 random interval and its predicate checks distance before the tier; `AvoidDarkPlayer` and the elf goal search only once per 5 goal evaluations (about every 10 ticks), within 8 blocks, and read the tier only of players in that box. The elf follow goal reads its own master's tier only.
13. **Nothing else changes:** no config value (the costs are on with `enableDarkMagic`, off without it), no packet and no protocol change, no lang string, no persistent key, no `@SubscribeEvent`, no entity event byte. Dementor aura, Death Eaters, Voldemort and the Dark Mark are untouched (Phase 3 owns Death Eaters and the Dark Mark).

## Global Constraints

- Server authoritative. `spell/dark/`, `entity/DarkPlayerGoals.java` and every file this plan touches never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. This plan adds no client code.
- No `switch` on an enum anywhere in the new code. Use if-chains, `==` and the `Cost` windows.
- No new hurt, death or effect handler, and no new `@SubscribeEvent`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- Never load a chunk. Goals use `getEntitiesOfClass`, `level().players()` and the vanilla target search only.
- Every JSON file stays valid (this plan touches none).
- Build and test (cloud):
  - First `pgrep -fa "[G]radleWrapperMain"`. If it prints a process, another agent's build is running: wait (poll every 30 s, at most 20 min). An idle `GradleDaemon` alone is fine.
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkCostRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- **Base test count: the count at the start of Task 1 (420 at 7d5e64b plus whatever lands before it); all green.** Call it BASE. The implementer of Task 1 runs the full build before Step 1 and records BASE in the report. Task 1 adds 16 (BASE + 16); no other task adds tests. All tests stay green.
- **The network protocol is not changed by this plan.** Before Task 1, record the current line: `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Task 6 checks it is identical (unless another phase changed it in between, which the ledger will say).
- Dedicated-server boot (Task 4 and Task 6), "the boot block":
  1. `cd /home/user/minecraft_mods && mkdir -p run && echo "eula=true" > run/eula.txt && rm -f run/logs/latest.log && : > /tmp/dm2_srv_in.txt`
  2. `cd /home/user/minecraft_mods && (tail -f /tmp/dm2_srv_in.txt | timeout 900 bash ./gradlew runServer --console=plain --max-workers=2 > /tmp/dm2_srv.log 2>&1 &)`
  3. Wait up to 10 min: `cd /home/user/minecraft_mods && timeout 600 bash -c 'until grep -qE "Done \(|Exception|Crash" run/logs/latest.log 2>/dev/null; do sleep 5; done'; grep -nE "Done \(|Exception|Crash" run/logs/latest.log | head`
  4. Exercise the new goals (the console reads stdin, see `build.gradle`): `printf 'summon hogwartsmagic:dementor ~ ~8 ~\nsummon hogwartsmagic:student ~3 ~1 ~\nsummon hogwartsmagic:house_elf ~-3 ~1 ~\n' >> /tmp/dm2_srv_in.txt`, then `cd /home/user/minecraft_mods && timeout 60 bash -c 'until [ "$(grep -c "Summoned new" run/logs/latest.log)" -ge 3 ]; do sleep 2; done'; grep -c "Summoned new" run/logs/latest.log`. Expected `3`. Then let them tick: `cd /home/user/minecraft_mods && timeout 20 bash -c 'until grep -qE "Exception|Crash" run/logs/latest.log; do sleep 2; done'; echo exit=$?`. Expected `exit=124` (no exception in 20 s). If the count stays `0`, the console did not get stdin: write "summons skipped: console stdin not forwarded" in the report and go on (not a failure; the human checklist covers the goals).
  5. `printf 'stop\n' >> /tmp/dm2_srv_in.txt; cd /home/user/minecraft_mods && timeout 90 bash -c 'until grep -q "All dimensions are saved" run/logs/latest.log; do sleep 2; done'`
  6. `cd /home/user/minecraft_mods && grep -nE "Done \(|Exception|ERROR|Summoned new" run/logs/latest.log | head -40`
  7. Stop only the java server process and the helper `tail`, never a Gradle process:
     ```bash
     ps -eo pid=,comm=,args= | awk '$2=="java" && $0 !~ /org\.gradle\./ && $0 ~ /nogui|forge_userdev_server|ForgeBootstrap|BootstrapLauncher/ {print $1}' | xargs -r kill
     pkill -f "[t]ail -f /tmp/dm2_srv_in.txt"
     sleep 5
     ps -eo pid=,comm=,args= | awk '$2=="java" && $0 !~ /org\.gradle\./ && $0 ~ /nogui|forge_userdev_server|ForgeBootstrap|BootstrapLauncher/ {print $1}'
     ```
     The last command must print nothing (if it prints a pid, `kill -9` that pid and check again).
- Commit per task. Stage only the named files; never `.claude/settings.local.json` or `.superpowers/`. Never push. No apostrophes in heredoc bodies. Every commit message ends with a blank line and exactly:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
  ```
  Write the message with a heredoc to `/tmp/msg.txt`, `git add` the new files, then commit by path: `git commit -F /tmp/msg.txt -- <paths>`.
- Line numbers drift. Every edit names the exact text to find, quoted from 7d5e64b (or from an earlier task of this plan, which says so).
- **Brewing Phase 2 is being built on this branch at the same time.** It touches `entity/BasiliskEntity`, petrification, `CombatEffects.isStunned`, `MasteryAwards`, the `SpellPackets` protocol, `ExampleMod`, `en_us.json` and `Config.java`. This plan touches none of those spots: its anchors are the Patronus rolls, the shield upkeep, the insomnia check, the Dark Magic lines from Phase 1, and the goal lists of Dementors, Students and House Elves. If an anchor has moved anyway, find the same spot by its text, keep both changes, and say so in the report.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkCostRules.java` | Pure: the cost table and every number | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/dark/DarkCostRulesTest.java` | 16 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java` | `tierOf`, presence cache, Lost health modifier, smoke tick, Patronus helpers | 2 |
| Modify `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java` | `sync` re-applies the health cost and drops the cache | 2 |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Login order and smoke tick (T2); shield upkeep and insomnia (T3) | 2, 3 |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | The two corporeal rolls | 3 |
| Create `src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java` | `TargetDarkPlayer`, `AvoidDarkPlayer` | 4 |
| Modify `src/main/java/com/hogwartsmagic/entity/DementorEntity.java` | Target goals | 4 |
| Modify `src/main/java/com/hogwartsmagic/entity/StudentEntity.java` | Avoid and target goals | 4 |
| Modify `src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java` | Flee goal, follow goal | 5 |

Untouched on purpose: `Config.java`, `ExampleMod.java`, `en_us.json`, `SpellPackets.register()` and the protocol (ruling 13); `DarkRules` (Phase 1, its numbers stay); `HousePerks`, `HorcruxData` (their modifiers keep their own ids, ruling 9); `PatronusEntity`, the Dementor aura; `WizardEntity`, `VoldemortEntity`, `DarkMarkEntity`, `SpellCosts`, `HealSpell` (Phase 3); every `client/` file.

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2, T3, T4, T5 | `DarkCostRules.Cost` (`HALF_CORPOREAL_PATRONUS`, `NO_CORPOREAL_PATRONUS`, `EARLY_INSOMNIA`, `SHIELD_UPKEEP`, `DEMENTORS_PREFER`, `STUDENTS_KEEP_AWAY`, `SMOKE`, `LOST_MAX_HEALTH`, `STUDENTS_ATTACK`, `ELVES_FLEE`) with `fromTier()`, `toTier()`, `appliesAt(int)`; constants `CORPOREAL_HALF`, `SHIELD_UPKEEP_MULTIPLIER`, `TICKS_PER_DAY`, `BASE_INSOMNIA_TICKS`, `DARK_INSOMNIA_TICKS`, `LOST_MAX_HEALTH_DELTA`, `SMOKE_INTERVAL_TICKS`, `SMOKE_COUNT_CORRUPTED`, `SMOKE_COUNT_LOST`, `STUDENT_KEEP_AWAY_DISTANCE`, `ELF_FLEE_DISTANCE`, `ELF_FLEE_STOP_DISTANCE`, `PRESENCE_REFRESH_TICKS`, `NPC_CHECK_EVERY_CALLS`; methods `clampTier(int)`, `costsAt(int)`, `corporealPatronusChance(float,int)`, `shieldUpkeepMultiplier(int)`, `insomniaTicks(int)`, `dementorsPrefer(int)`, `studentsKeepAway(int)`, `studentsAttack(int)`, `elvesFlee(int)`, `maxHealthDelta(int)`, `healthAfterMaxChange(float,float)`, `smokeCount(int)`, `dueToSmoke(long,long)`, `presenceStale(int,int)` | names identical |
| T2 → T3, T4, T5 | `DarkCosts.tierOf(Entity)`, `darkestOnlineTier(Level)`, `anyoneOnlineAt(Level, DarkCostRules.Cost)`, `invalidatePresence()`, `corporealPatronusChance(ServerPlayer, float)`, `shieldUpkeepMultiplier(Entity)`, `refreshMaxHealth(ServerPlayer)`, `tickSmoke(ServerPlayer)`, `LOST_HEALTH_MODIFIER_ID` | written in T2 |
| T4 → T5 | none (the elf goal is its own inner class; it uses T1 and T2 only) | |
| Phase 1 → all | `DarkMagicData.getTier(ServerPlayer)`, `DarkMagicData.sync(ServerPlayer)`, `DarkRules.CLEAN..LOST`, `DarkRules.effectiveTier(boolean,float)` | on the branch since f6aa7c4/572d247 |

---

### Task 1: DarkCostRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkCostRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/dark/DarkCostRulesTest.java`

**Interfaces.** Consumes: `DarkRules.CLEAN`, `SHADOWED`, `TAINTED`, `CORRUPTED`, `LOST`, `DarkRules.effectiveTier(boolean, float)` (Phase 1). Produces: everything in the T1 row of the pre-flight table.

- [ ] **Step 0: Record the base.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, all green. Record the test count as BASE, and the output of `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`, in the report.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/spell/dark/DarkCostRulesTest.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.dark.DarkCostRules.Cost;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkCostRulesTest {
    private static final float EPS = 1.0e-6f;

    private static void assertWindow(Cost cost, int from, int to) {
        assertEquals(from, cost.fromTier(), cost + " from");
        assertEquals(to, cost.toTier(), cost + " to");
    }

    @Test
    void costWindowsMatchTheSpec() {
        assertWindow(Cost.HALF_CORPOREAL_PATRONUS, DarkRules.SHADOWED, DarkRules.SHADOWED);
        assertWindow(Cost.NO_CORPOREAL_PATRONUS, DarkRules.TAINTED, DarkRules.LOST);
        assertWindow(Cost.EARLY_INSOMNIA, DarkRules.TAINTED, DarkRules.LOST);
        assertWindow(Cost.SHIELD_UPKEEP, DarkRules.CORRUPTED, DarkRules.LOST);
        assertWindow(Cost.DEMENTORS_PREFER, DarkRules.CORRUPTED, DarkRules.LOST);
        assertWindow(Cost.STUDENTS_KEEP_AWAY, DarkRules.CORRUPTED, DarkRules.CORRUPTED);
        assertWindow(Cost.SMOKE, DarkRules.CORRUPTED, DarkRules.LOST);
        assertWindow(Cost.LOST_MAX_HEALTH, DarkRules.LOST, DarkRules.LOST);
        assertWindow(Cost.STUDENTS_ATTACK, DarkRules.LOST, DarkRules.LOST);
        assertWindow(Cost.ELVES_FLEE, DarkRules.LOST, DarkRules.LOST);
        assertEquals(10, Cost.values().length);
    }

    @Test
    void eachTierHasExactlyTheSpecCosts() {
        assertEquals(EnumSet.noneOf(Cost.class), DarkCostRules.costsAt(DarkRules.CLEAN));
        assertEquals(EnumSet.of(Cost.HALF_CORPOREAL_PATRONUS), DarkCostRules.costsAt(DarkRules.SHADOWED));
        assertEquals(EnumSet.of(Cost.NO_CORPOREAL_PATRONUS, Cost.EARLY_INSOMNIA),
                DarkCostRules.costsAt(DarkRules.TAINTED));
        assertEquals(EnumSet.of(Cost.NO_CORPOREAL_PATRONUS, Cost.EARLY_INSOMNIA, Cost.SHIELD_UPKEEP,
                        Cost.DEMENTORS_PREFER, Cost.STUDENTS_KEEP_AWAY, Cost.SMOKE),
                DarkCostRules.costsAt(DarkRules.CORRUPTED));
        assertEquals(EnumSet.of(Cost.NO_CORPOREAL_PATRONUS, Cost.EARLY_INSOMNIA, Cost.SHIELD_UPKEEP,
                        Cost.DEMENTORS_PREFER, Cost.SMOKE, Cost.LOST_MAX_HEALTH, Cost.STUDENTS_ATTACK,
                        Cost.ELVES_FLEE),
                DarkCostRules.costsAt(DarkRules.LOST));
    }

    @Test
    void aTierKeepsTheCostsBelowItExceptTheTwoReplacements() {
        Set<Cost> replaced = EnumSet.of(Cost.HALF_CORPOREAL_PATRONUS, Cost.STUDENTS_KEEP_AWAY);
        for (int tier = DarkRules.SHADOWED; tier <= DarkRules.LOST; tier++) {
            Set<Cost> below = EnumSet.noneOf(Cost.class);
            below.addAll(DarkCostRules.costsAt(tier - 1));
            below.removeAll(replaced);
            assertTrue(DarkCostRules.costsAt(tier).containsAll(below), "tier " + tier);
        }
        // Each replacement is replaced by something harder at the next tier.
        assertTrue(DarkCostRules.costsAt(DarkRules.TAINTED).contains(Cost.NO_CORPOREAL_PATRONUS));
        assertTrue(DarkCostRules.costsAt(DarkRules.LOST).contains(Cost.STUDENTS_ATTACK));
    }

    @Test
    void corporealChanceByTier() {
        float base = 0.15f;
        assertEquals(0.15f, DarkCostRules.corporealPatronusChance(base, DarkRules.CLEAN), EPS);
        assertEquals(0.075f, DarkCostRules.corporealPatronusChance(base, DarkRules.SHADOWED), EPS);
        assertEquals(0.0f, DarkCostRules.corporealPatronusChance(base, DarkRules.TAINTED), EPS);
        assertEquals(0.0f, DarkCostRules.corporealPatronusChance(base, DarkRules.CORRUPTED), EPS);
        assertEquals(0.0f, DarkCostRules.corporealPatronusChance(base, DarkRules.LOST), EPS);
    }

    @Test
    void thePatronusNeverFails() {
        // The chance only moves between 0 and the base: a roll gives a corporeal Patronus or the shield, never
        // nothing. Nothing in these rules can refuse the charm.
        for (int tier = -2; tier <= 6; tier++) {
            for (float base : new float[] {0.0f, 0.15f, 1.0f}) {
                float chance = DarkCostRules.corporealPatronusChance(base, tier);
                assertTrue(chance >= 0.0f && chance <= base, "tier " + tier + " base " + base);
            }
        }
    }

    @Test
    void shieldUpkeepIsHalfAgainFromCorrupted() {
        assertEquals(1.5f, DarkCostRules.SHIELD_UPKEEP_MULTIPLIER, EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.CLEAN), EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.SHADOWED), EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.TAINTED), EPS);
        assertEquals(1.5f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.CORRUPTED), EPS);
        assertEquals(1.5f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.LOST), EPS);
    }

    @Test
    void insomniaComesAfterThreeDaysFromTainted() {
        assertEquals(24_000, DarkCostRules.TICKS_PER_DAY);
        assertEquals(120_000, DarkCostRules.BASE_INSOMNIA_TICKS);
        assertEquals(72_000, DarkCostRules.DARK_INSOMNIA_TICKS);
        assertEquals(120_000, DarkCostRules.insomniaTicks(DarkRules.CLEAN));
        assertEquals(120_000, DarkCostRules.insomniaTicks(DarkRules.SHADOWED));
        assertEquals(72_000, DarkCostRules.insomniaTicks(DarkRules.TAINTED));
        assertEquals(72_000, DarkCostRules.insomniaTicks(DarkRules.CORRUPTED));
        assertEquals(72_000, DarkCostRules.insomniaTicks(DarkRules.LOST));
    }

    @Test
    void dementorsPreferCorruptedAndLost() {
        assertFalse(DarkCostRules.dementorsPrefer(DarkRules.CLEAN));
        assertFalse(DarkCostRules.dementorsPrefer(DarkRules.SHADOWED));
        assertFalse(DarkCostRules.dementorsPrefer(DarkRules.TAINTED));
        assertTrue(DarkCostRules.dementorsPrefer(DarkRules.CORRUPTED));
        assertTrue(DarkCostRules.dementorsPrefer(DarkRules.LOST));
    }

    @Test
    void studentsKeepAwayAtCorruptedAndAttackAtLost() {
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(tier == DarkRules.CORRUPTED, DarkCostRules.studentsKeepAway(tier), "keep away " + tier);
            assertEquals(tier == DarkRules.LOST, DarkCostRules.studentsAttack(tier), "attack " + tier);
            assertFalse(DarkCostRules.studentsKeepAway(tier) && DarkCostRules.studentsAttack(tier));
        }
        assertEquals(8.0f, DarkCostRules.STUDENT_KEEP_AWAY_DISTANCE, EPS);
    }

    @Test
    void elvesFleeOnlyFromLost() {
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(tier == DarkRules.LOST, DarkCostRules.elvesFlee(tier), "tier " + tier);
        }
        assertEquals(8.0D, DarkCostRules.ELF_FLEE_DISTANCE, 1.0e-9);
        assertEquals(12.0D, DarkCostRules.ELF_FLEE_STOP_DISTANCE, 1.0e-9);
    }

    @Test
    void lostCostsTwoMaxHealthAndStacks() {
        assertEquals(-2.0D, DarkCostRules.LOST_MAX_HEALTH_DELTA, 1.0e-9);
        for (int tier = DarkRules.CLEAN; tier < DarkRules.LOST; tier++) {
            assertEquals(0.0D, DarkCostRules.maxHealthDelta(tier), 1.0e-9, "tier " + tier);
        }
        assertEquals(-2.0D, DarkCostRules.maxHealthDelta(DarkRules.LOST), 1.0e-9);
        double base = 20.0D;
        double hufflepuff = 4.0D;
        double horcrux = -2.0D;
        assertEquals(20.0D, base + hufflepuff + horcrux + DarkCostRules.maxHealthDelta(DarkRules.LOST), 1.0e-9);
        assertEquals(16.0D, base + horcrux + DarkCostRules.maxHealthDelta(DarkRules.LOST), 1.0e-9);
    }

    @Test
    void healthIsClampedButNeverKills() {
        assertEquals(18.0f, DarkCostRules.healthAfterMaxChange(20.0f, 18.0f), EPS);
        assertEquals(17.0f, DarkCostRules.healthAfterMaxChange(17.0f, 18.0f), EPS);
        assertEquals(5.0f, DarkCostRules.healthAfterMaxChange(5.0f, 22.0f), EPS);
        assertEquals(1.0f, DarkCostRules.healthAfterMaxChange(3.0f, 0.5f), EPS);
        assertEquals(0.0f, DarkCostRules.healthAfterMaxChange(0.0f, 18.0f), EPS);
        assertTrue(Float.isNaN(DarkCostRules.healthAfterMaxChange(Float.NaN, 18.0f)));
    }

    @Test
    void smokeAtCorruptedAndThickerAtLost() {
        assertEquals(0, DarkCostRules.smokeCount(DarkRules.CLEAN));
        assertEquals(0, DarkCostRules.smokeCount(DarkRules.SHADOWED));
        assertEquals(0, DarkCostRules.smokeCount(DarkRules.TAINTED));
        assertEquals(2, DarkCostRules.smokeCount(DarkRules.CORRUPTED));
        assertEquals(4, DarkCostRules.smokeCount(DarkRules.LOST));
        assertEquals(10, DarkCostRules.SMOKE_INTERVAL_TICKS);
        int due = 0;
        for (long tick = 0L; tick < 10L; tick++) {
            if (DarkCostRules.dueToSmoke(tick, 7L)) {
                due++;
            }
        }
        assertEquals(1, due);
        assertTrue(DarkCostRules.dueToSmoke(3L, 7L));
        assertTrue(DarkCostRules.dueToSmoke(-3L, 3L));
    }

    @Test
    void presenceCacheGoesStaleAfterOneSecond() {
        assertEquals(20, DarkCostRules.PRESENCE_REFRESH_TICKS);
        assertEquals(5, DarkCostRules.NPC_CHECK_EVERY_CALLS);
        assertFalse(DarkCostRules.presenceStale(100, 100));
        assertFalse(DarkCostRules.presenceStale(119, 100));
        assertTrue(DarkCostRules.presenceStale(120, 100));
        assertTrue(DarkCostRules.presenceStale(50, 100));
    }

    @Test
    void tiersOutsideTheRangeAreClamped() {
        assertEquals(DarkRules.CLEAN, DarkCostRules.clampTier(-1));
        assertEquals(DarkRules.LOST, DarkCostRules.clampTier(5));
        assertEquals(DarkCostRules.costsAt(DarkRules.CLEAN), DarkCostRules.costsAt(-3));
        assertEquals(DarkCostRules.costsAt(DarkRules.LOST), DarkCostRules.costsAt(9));
        assertEquals(0.15f, DarkCostRules.corporealPatronusChance(0.15f, -1), EPS);
    }

    @Test
    void theToggleOffMeansNoCost() {
        // enableDarkMagic = false makes DarkMagicData.getTier read Clean (Phase 1), so every cost reads tier 0.
        int off = DarkRules.effectiveTier(false, 100.0f);
        assertEquals(DarkRules.CLEAN, off);
        assertTrue(DarkCostRules.costsAt(off).isEmpty());
        assertEquals(0.15f, DarkCostRules.corporealPatronusChance(0.15f, off), EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(off), EPS);
        assertEquals(DarkCostRules.BASE_INSOMNIA_TICKS, DarkCostRules.insomniaTicks(off));
        assertEquals(0.0D, DarkCostRules.maxHealthDelta(off), 1.0e-9);
        assertEquals(0, DarkCostRules.smokeCount(off));
        assertFalse(DarkCostRules.dementorsPrefer(off) || DarkCostRules.studentsKeepAway(off)
                || DarkCostRules.studentsAttack(off) || DarkCostRules.elvesFlee(off));
    }
}
```

That is 16 tests.

- [ ] **Step 2: Run it and see it fail.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkCostRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... DarkCostRules`).

- [ ] **Step 3: Write the rules.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkCostRules.java`:

```java
package com.hogwartsmagic.spell.dark;

import java.util.EnumSet;
import java.util.Set;

/**
 * Dark Magic costs (Phase 2), free of Minecraft so they are unit-tested. Every cost reads the tier
 * (DarkRules.CLEAN..LOST, through DarkMagicData.getTier, so enableDarkMagic off reads Clean), never the raw number.
 * A tier outside 0..4 is clamped into it. A tier keeps the costs of the tiers below it, except where a harder cost
 * replaces a softer one: no corporeal Patronus replaces the halved chance from Tainted, and students attacking
 * replaces students keeping away at Lost. The Patronus never fails: the corporeal chance only falls, and a roll that
 * misses it gives the shield.
 */
public final class DarkCostRules {

    /** One cost and the tiers it applies at (inclusive). */
    public enum Cost {
        HALF_CORPOREAL_PATRONUS(DarkRules.SHADOWED, DarkRules.SHADOWED),
        NO_CORPOREAL_PATRONUS(DarkRules.TAINTED, DarkRules.LOST),
        EARLY_INSOMNIA(DarkRules.TAINTED, DarkRules.LOST),
        SHIELD_UPKEEP(DarkRules.CORRUPTED, DarkRules.LOST),
        DEMENTORS_PREFER(DarkRules.CORRUPTED, DarkRules.LOST),
        STUDENTS_KEEP_AWAY(DarkRules.CORRUPTED, DarkRules.CORRUPTED),
        SMOKE(DarkRules.CORRUPTED, DarkRules.LOST),
        LOST_MAX_HEALTH(DarkRules.LOST, DarkRules.LOST),
        STUDENTS_ATTACK(DarkRules.LOST, DarkRules.LOST),
        ELVES_FLEE(DarkRules.LOST, DarkRules.LOST);

        private final int fromTier;
        private final int toTier;

        Cost(int fromTier, int toTier) {
            this.fromTier = fromTier;
            this.toTier = toTier;
        }

        /** The lowest tier this cost applies at. NPC goals compare the darkest online tier with it. */
        public int fromTier() {
            return this.fromTier;
        }

        public int toTier() {
            return this.toTier;
        }

        public boolean appliesAt(int tier) {
            int t = clampTier(tier);
            return t >= this.fromTier && t <= this.toTier;
        }
    }

    /** Shadowed: the corporeal chance is halved (15% to 7.5%). */
    public static final float CORPOREAL_HALF = 0.5f;
    /** Corrupted and Lost: the Patronus shield costs half as much again to hold. */
    public static final float SHIELD_UPKEEP_MULTIPLIER = 1.5f;
    public static final int TICKS_PER_DAY = 24_000;
    /** Same stat as phantoms (ticks since the last bed or respawn): insomnia Dementors after 5 days... */
    public static final int BASE_INSOMNIA_TICKS = 5 * TICKS_PER_DAY;
    /** ...and after 3 days from Tainted. */
    public static final int DARK_INSOMNIA_TICKS = 3 * TICKS_PER_DAY;
    /** Lost: one heart (the soul is torn). */
    public static final double LOST_MAX_HEALTH_DELTA = -2.0D;
    public static final int SMOKE_INTERVAL_TICKS = 10;
    public static final int SMOKE_COUNT_CORRUPTED = 2;
    public static final int SMOKE_COUNT_LOST = 4;
    public static final float STUDENT_KEEP_AWAY_DISTANCE = 8.0f;
    public static final double ELF_FLEE_DISTANCE = 8.0D;
    public static final double ELF_FLEE_STOP_DISTANCE = 12.0D;
    /** The darkest-online cache is recomputed at most once per second. */
    public static final int PRESENCE_REFRESH_TICKS = 20;
    /** Avoid and flee goals search once per this many goal evaluations (a mob evaluates every 2 ticks). */
    public static final int NPC_CHECK_EVERY_CALLS = 5;

    private DarkCostRules() {
    }

    public static int clampTier(int tier) {
        return Math.max(DarkRules.CLEAN, Math.min(DarkRules.LOST, tier));
    }

    /** Every cost that applies at this tier. */
    public static Set<Cost> costsAt(int tier) {
        EnumSet<Cost> costs = EnumSet.noneOf(Cost.class);
        for (Cost cost : Cost.values()) {
            if (cost.appliesAt(tier)) {
                costs.add(cost);
            }
        }
        return costs;
    }

    /** The chance a Patronus cast is corporeal: the base, half of it at Shadowed, none from Tainted. */
    public static float corporealPatronusChance(float base, int tier) {
        if (Cost.NO_CORPOREAL_PATRONUS.appliesAt(tier)) {
            return 0.0f;
        }
        if (Cost.HALF_CORPOREAL_PATRONUS.appliesAt(tier)) {
            return base * CORPOREAL_HALF;
        }
        return base;
    }

    public static float shieldUpkeepMultiplier(int tier) {
        return Cost.SHIELD_UPKEEP.appliesAt(tier) ? SHIELD_UPKEEP_MULTIPLIER : 1.0f;
    }

    /** Ticks without rest before insomnia Dementors may come at night. */
    public static int insomniaTicks(int tier) {
        return Cost.EARLY_INSOMNIA.appliesAt(tier) ? DARK_INSOMNIA_TICKS : BASE_INSOMNIA_TICKS;
    }

    public static boolean dementorsPrefer(int tier) {
        return Cost.DEMENTORS_PREFER.appliesAt(tier);
    }

    public static boolean studentsKeepAway(int tier) {
        return Cost.STUDENTS_KEEP_AWAY.appliesAt(tier);
    }

    public static boolean studentsAttack(int tier) {
        return Cost.STUDENTS_ATTACK.appliesAt(tier);
    }

    public static boolean elvesFlee(int tier) {
        return Cost.ELVES_FLEE.appliesAt(tier);
    }

    /** The max-health modifier amount this tier wants: -2 at Lost, else none. */
    public static double maxHealthDelta(int tier) {
        return Cost.LOST_MAX_HEALTH.appliesAt(tier) ? LOST_MAX_HEALTH_DELTA : 0.0D;
    }

    /**
     * Health after the max changed: lowered to the max when above it, never below 1, never raised, and a dead (or
     * NaN) value is returned as it is. So applying the Lost cost never kills and removing it never heals.
     */
    public static float healthAfterMaxChange(float health, float maxHealth) {
        if (!(health > 0.0f) || health <= maxHealth) {
            return health;
        }
        return Math.max(1.0f, maxHealth);
    }

    /** Smoke particles per puff: none below Corrupted, 2 at Corrupted, 4 at Lost. */
    public static int smokeCount(int tier) {
        if (!Cost.SMOKE.appliesAt(tier)) {
            return 0;
        }
        return clampTier(tier) >= DarkRules.LOST ? SMOKE_COUNT_LOST : SMOKE_COUNT_CORRUPTED;
    }

    /** One tick in SMOKE_INTERVAL_TICKS, spread over players by a per-player phase key (the entity id). */
    public static boolean dueToSmoke(long tickCount, long phaseKey) {
        return Math.floorMod(tickCount + phaseKey, (long) SMOKE_INTERVAL_TICKS) == 0L;
    }

    /** True when the cached darkest-online tier must be recomputed (a second old, or the clock went back). */
    public static boolean presenceStale(int now, int cachedAt) {
        return now < cachedAt || now - cachedAt >= PRESENCE_REFRESH_TICKS;
    }
}
```

- [ ] **Step 4: Run the class.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkCostRulesTest" --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 16 tests, 0 failures.

- [ ] **Step 5: Full build.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 16 tests, all green.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 2: DarkCostRules, the cost table per tier

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/spell/dark/DarkCostRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkCostRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkCostRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkCostRulesTest.java
```

---

### Task 2: DarkCosts, the Lost health cost and the smoke

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`

**Interfaces.** Consumes: T1 (`DarkCostRules`), Phase 1 (`DarkMagicData.getTier(ServerPlayer)`, `DarkMagicData.sync(ServerPlayer)`, `DarkRules.CLEAN`). Produces: the T2 row of the pre-flight table.

- [ ] **Step 1: The glue class.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;

/**
 * Dark Magic costs (Phase 2), the server side. Every cost reads the tier through DarkMagicData.getTier (so
 * enableDarkMagic off reads Clean and turns them all off); the numbers live in DarkCostRules. NPC goals check
 * darkestOnlineTier first, a cache refreshed at most once a second and dropped on every DarkMagicData.sync, so no NPC
 * searches anything while nobody online is dark enough to matter.
 */
public final class DarkCosts {
    /** Lost: -2 max health. Its own id, so it stacks with the Horcrux cost and the Hufflepuff bonus. */
    public static final ResourceLocation LOST_HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dark_magic_lost_health");

    private static WeakReference<MinecraftServer> presenceServer = new WeakReference<>(null);
    private static int presenceAt;
    private static int darkestOnline = DarkRules.CLEAN;
    private static volatile boolean presenceDirty = true;

    private DarkCosts() {
    }

    /** A player's effective tier (Clean with enableDarkMagic off); Clean for anything that is not a server player. */
    public static int tierOf(Entity entity) {
        return entity instanceof ServerPlayer player ? DarkMagicData.getTier(player) : DarkRules.CLEAN;
    }

    /** The darkest effective tier among online players, cached for up to a second. Clean on the client. */
    public static int darkestOnlineTier(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return DarkRules.CLEAN;
        }
        int now = server.getTickCount();
        if (presenceDirty || presenceServer.get() != server || DarkCostRules.presenceStale(now, presenceAt)) {
            presenceDirty = false;
            int darkest = DarkRules.CLEAN;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                darkest = Math.max(darkest, DarkMagicData.getTier(player));
            }
            darkestOnline = darkest;
            presenceAt = now;
            presenceServer = new WeakReference<>(server);
        }
        return darkestOnline;
    }

    /** True when someone online is at or past the lowest tier this cost applies at. The NPC goals' first check. */
    public static boolean anyoneOnlineAt(Level level, DarkCostRules.Cost cost) {
        return darkestOnlineTier(level) >= cost.fromTier();
    }

    /** From DarkMagicData.sync: a tier may have changed, so the next NPC check recomputes. */
    public static void invalidatePresence() {
        presenceDirty = true;
    }

    /** The corporeal Patronus chance for this caster (the body that casts it). */
    public static float corporealPatronusChance(ServerPlayer caster, float base) {
        return DarkCostRules.corporealPatronusChance(base, DarkMagicData.getTier(caster));
    }

    /** The Patronus shield upkeep multiplier for this caster: 1.5 from Corrupted. */
    public static float shieldUpkeepMultiplier(Entity caster) {
        return DarkCostRules.shieldUpkeepMultiplier(tierOf(caster));
    }

    /**
     * Adds or removes the Lost -2 max health so it matches the tier. Does nothing when it already matches. Health
     * above the new max is lowered to it (never below 1, never on a dead player); removing the cost never changes
     * health, so it can never kill.
     */
    public static void refreshMaxHealth(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double wanted = DarkCostRules.maxHealthDelta(DarkMagicData.getTier(player));
        AttributeModifier present = attribute.getModifier(LOST_HEALTH_MODIFIER_ID);
        double have = present == null ? 0.0D : present.amount();
        if (have == wanted) {
            return;
        }
        attribute.removeModifier(LOST_HEALTH_MODIFIER_ID);
        if (wanted != 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(LOST_HEALTH_MODIFIER_ID, wanted,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        float health = player.getHealth();
        float clamped = DarkCostRules.healthAfterMaxChange(health, player.getMaxHealth());
        if (clamped != health) {
            player.setHealth(clamped);
        }
    }

    /**
     * From ModEvents.onPlayerTick (server, END phase). Returns on 9 ticks out of 10 before any read. Faint smoke around
     * a Corrupted or Lost player, sent to everyone within 32 blocks; none while invisible, spectating or dead.
     */
    public static void tickSmoke(ServerPlayer player) {
        if (!DarkCostRules.dueToSmoke(player.tickCount, player.getId())) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isInvisible()) {
            return;
        }
        int count = DarkCostRules.smokeCount(DarkMagicData.getTier(player));
        if (count <= 0) {
            return;
        }
        player.serverLevel().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0D, player.getZ(),
                count, 0.3D, 0.5D, 0.3D, 0.005D);
    }
}
```

- [ ] **Step 2: Sync applies the health cost.** In `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`, find:

```java
        SpellPackets.CHANNEL.send(new SpellPackets.SyncDarkMagicPacket(get(player)),
                PacketDistributor.PLAYER.with(player));
    }
```

and replace it with:

```java
        SpellPackets.CHANNEL.send(new SpellPackets.SyncDarkMagicPacket(get(player)),
                PacketDistributor.PLAYER.with(player));
        // Phase 2: every sync point (each write, so each tier change; login, respawn, dimension change; a config
        // reload, so the toggle) also re-applies the Lost health cost and drops the NPCs' darkest-online cache.
        DarkCosts.refreshMaxHealth(player);
        DarkCosts.invalidatePresence();
    }
```

Also change the javadoc line right above `public static void sync(ServerPlayer player) {` from `/** Sends this player their own effective Darkness. Never sent about anyone else. */` to `/** Sends this player their own effective Darkness (never anyone else's) and re-applies the Lost health cost. */`.

- [ ] **Step 3: Login order.** In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, inside `onPlayerJoin`, find:

```java
            com.hogwartsmagic.spell.dark.DarkMagicData.sync(player);
            HousePerks.applyHouseAttributes(player);
            HorcruxData.applyMaxHealthPenalty(player);
```

and replace it with:

```java
            HousePerks.applyHouseAttributes(player);
            HorcruxData.applyMaxHealthPenalty(player);
            // Dark Magic: after the house and Horcrux modifiers, so the Lost health cost clamps against the full max.
            com.hogwartsmagic.spell.dark.DarkMagicData.sync(player);
```

(`onPlayerRespawn` already applies the house and Horcrux modifiers before its `DarkMagicData.sync(serverPlayer)`; `syncPersistentMagicState` keeps its sync: a dimension change keeps the same player entity, so the refresh there finds the modifier in place and does nothing.)

- [ ] **Step 4: The smoke tick.** In the same file, inside `onPlayerTick`, find (Phase 1 text):

```java
            // Dark Magic decay: returns at once on 199 ticks out of 200, before any read.
            com.hogwartsmagic.spell.dark.DarkMagicData.tickDecay(player);
```

and replace it with:

```java
            // Dark Magic decay: returns at once on 199 ticks out of 200, before any read.
            com.hogwartsmagic.spell.dark.DarkMagicData.tickDecay(player);
            // Dark Magic smoke (Corrupted and Lost): returns at once on 9 ticks out of 10, before any read.
            com.hogwartsmagic.spell.dark.DarkCosts.tickSmoke(player);
```

- [ ] **Step 5: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 16 tests, all green.
  - `grep -c "DarkCosts.refreshMaxHealth\|DarkCosts.invalidatePresence" src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`. Expected: `2`.
  - `grep -c "DarkMagicData.sync" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: `3` (login, respawn, dimension change; unchanged count).
  - `grep -c "DarkCosts.tickSmoke" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: `1`.
  - `grep -n "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java`. Expected: nothing.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 2: Lost max health cost and dark smoke

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java src/main/java/com/hogwartsmagic/event/ModEvents.java
```

---

### Task 3: The Patronus and insomnia costs

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`

**Interfaces.** Consumes: T1 (`DarkCostRules.insomniaTicks(int)`), T2 (`DarkCosts.corporealPatronusChance(ServerPlayer, float)`, `DarkCosts.shieldUpkeepMultiplier(Entity)`), Phase 1 (`DarkMagicData.getTier(ServerPlayer)`). Produces: the Patronus and insomnia costs.

- [ ] **Step 1: The two corporeal rolls.** In `src/main/java/com/hogwartsmagic/network/SpellPackets.java`, inside `CastSpellPacket.castAs`, the text

```java
player.getRandom().nextFloat() < CORPOREAL_PATRONUS_CHANCE
```

occurs exactly twice (the held cast, after `float currentMana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;`, and the tapped cast, after `// Full mana was already taken above, so either outcome is paid for here.`). Replace both occurrences with:

```java
player.getRandom().nextFloat() < com.hogwartsmagic.spell.dark.DarkCosts.corporealPatronusChance(player, CORPOREAL_PATRONUS_CHANCE)
```

Leave the constant `CORPOREAL_PATRONUS_CHANCE = 0.15f` and both shield branches as they are: a roll that misses gives the shield, so the charm never fails. Then change the constant's javadoc `/** How often the charm answers in full rather than as a bare shield of light. */` to `/** How often the charm answers in full rather than as a bare shield of light (a Clean caster; Dark Magic lowers it, DarkCostRules). */`.

- [ ] **Step 2: The shield upkeep.** In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, find:

```java
    public static float getPatronusShieldUpkeepCost(Player player, ItemStack wand) {
        return Math.max(1.5f, (SpellType.EXPECTO_PATRONUM.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.EXPECTO_PATRONUM)) / 30.0f);
    }
```

and replace it with:

```java
    public static float getPatronusShieldUpkeepCost(Player player, ItemStack wand) {
        float upkeep = Math.max(1.5f, (SpellType.EXPECTO_PATRONUM.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.EXPECTO_PATRONUM)) / 30.0f);
        // Dark Magic: from Corrupted the shield costs half as much again to hold (and to raise).
        return upkeep * com.hogwartsmagic.spell.dark.DarkCosts.shieldUpkeepMultiplier(player);
    }
```

- [ ] **Step 3: The insomnia threshold.** In the same file, find and delete these two lines (the constant moves to `DarkCostRules.BASE_INSOMNIA_TICKS`):

```java
    /** Same stat as phantoms: ticks since last bed / respawn reset (1 day = 24000 ticks). */
    private static final int DEMENTOR_INSOMNIA_TICKS = 5 * 24000;
```

Then, inside `trySpawnInsomniaDementor`, find:

```java
        int sinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        if (sinceRest < DEMENTOR_INSOMNIA_TICKS) {
```

and replace it with:

```java
        int sinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        // Same stat as phantoms. 5 days without rest, or 3 from Tainted (Dark Magic, DarkCostRules).
        if (sinceRest < com.hogwartsmagic.spell.dark.DarkCostRules.insomniaTicks(
                com.hogwartsmagic.spell.dark.DarkMagicData.getTier(player))) {
```

- [ ] **Step 4: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 16 tests, all green.
  - `grep -c "DarkCosts.corporealPatronusChance(player, CORPOREAL_PATRONUS_CHANCE)" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: `2`.
  - `grep -c "nextFloat() < CORPOREAL_PATRONUS_CHANCE" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: `0`.
  - `grep -c "DEMENTOR_INSOMNIA_TICKS" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: `0`.
  - `grep -c "DarkCosts.shieldUpkeepMultiplier\|DarkCostRules.insomniaTicks" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: `2`.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 2: weaker Patronus and earlier insomnia Dementors for dark souls

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java
```

---

### Task 4: Dementors and Students

**Files:**
- Create: `src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/DementorEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/StudentEntity.java`

**Interfaces.** Consumes: T1 (`DarkCostRules.Cost`, `NPC_CHECK_EVERY_CALLS`, `STUDENT_KEEP_AWAY_DISTANCE`), T2 (`DarkCosts.tierOf`, `DarkCosts.anyoneOnlineAt`). Produces: `DarkPlayerGoals.TargetDarkPlayer(Mob, DarkCostRules.Cost)`, `DarkPlayerGoals.AvoidDarkPlayer(PathfinderMob, DarkCostRules.Cost, float, double, double)`.

- [ ] **Step 1: The goals.** Create `src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java`:

```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.dark.DarkCostRules;
import com.hogwartsmagic.spell.dark.DarkCosts;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * NPC reactions to a dark player (Dark Magic Phase 2). Each goal is tied to one DarkCostRules.Cost and first checks
 * the cached darkest tier online (DarkCosts.darkestOnlineTier), so while nobody online is dark enough it searches
 * nothing. Creative and spectator players are never chosen (vanilla combat targeting).
 */
public final class DarkPlayerGoals {

    private DarkPlayerGoals() {
    }

    /**
     * Targets the nearest player in follow range, with line of sight, whose tier carries the cost. Give it a lower
     * priority number than the mob's plain player goal and it takes over from a target already chosen.
     */
    public static class TargetDarkPlayer extends NearestAttackableTargetGoal<Player> {
        private final DarkCostRules.Cost cost;

        public TargetDarkPlayer(Mob mob, DarkCostRules.Cost cost) {
            super(mob, Player.class, 10, true, false, target -> {
                double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                // Distance first: the tier is read only for players the mob could choose.
                return mob.distanceToSqr(target) <= range * range && cost.appliesAt(DarkCosts.tierOf(target));
            });
            this.cost = cost;
        }

        @Override
        public boolean canUse() {
            return DarkCosts.anyoneOnlineAt(this.mob.level(), this.cost) && super.canUse();
        }
    }

    /** Keeps away from a player whose tier carries the cost. Searches once per NPC_CHECK_EVERY_CALLS evaluations. */
    public static class AvoidDarkPlayer extends AvoidEntityGoal<Player> {
        private final DarkCostRules.Cost cost;
        private int checkCountdown;

        public AvoidDarkPlayer(PathfinderMob mob, DarkCostRules.Cost cost, float distance, double walkSpeed,
                double sprintSpeed) {
            super(mob, Player.class, distance, walkSpeed, sprintSpeed,
                    target -> cost.appliesAt(DarkCosts.tierOf(target)));
            this.cost = cost;
        }

        @Override
        public boolean canUse() {
            if (--this.checkCountdown > 0) {
                return false;
            }
            this.checkCountdown = DarkCostRules.NPC_CHECK_EVERY_CALLS;
            return DarkCosts.anyoneOnlineAt(this.mob.level(), this.cost) && super.canUse();
        }
    }
}
```

- [ ] **Step 2: Dementors prefer a dark player.** In `src/main/java/com/hogwartsmagic/entity/DementorEntity.java`, inside `registerGoals`, find:

```java
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
```

and replace it with:

```java
        // Dark Magic: a Corrupted or Lost player in range comes first (the nearest such one), even over a target
        // already chosen. With none in range the goals below choose exactly as before (same order, renumbered).
        this.targetSelector.addGoal(2, new DarkPlayerGoals.TargetDarkPlayer(this,
                com.hogwartsmagic.spell.dark.DarkCostRules.Cost.DEMENTORS_PREFER));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
```

`HurtByTargetGoal` stays at 1 and the Patronus avoid goal is untouched.

- [ ] **Step 3: Students keep away from a Corrupted player.** In `src/main/java/com/hogwartsmagic/entity/StudentEntity.java`, inside `registerGoals`, find:

```java
        this.goalSelector.addGoal(4, new StudentUtilityCastGoal(this));
```

and replace it with:

```java
        this.goalSelector.addGoal(4, new StudentUtilityCastGoal(this));
        // Dark Magic: keep about 8 blocks from a Corrupted player. Below the spell-cast goal (3), so a student with
        // a target still fights (the hurt-by goal is unchanged); never mid-duel.
        this.goalSelector.addGoal(4, new DarkPlayerGoals.AvoidDarkPlayer(this,
                com.hogwartsmagic.spell.dark.DarkCostRules.Cost.STUDENTS_KEEP_AWAY,
                com.hogwartsmagic.spell.dark.DarkCostRules.STUDENT_KEEP_AWAY_DISTANCE, 0.7D, 0.9D) {
            @Override
            public boolean canUse() {
                return !com.hogwartsmagic.duel.DuelManager.isDueling(StudentEntity.this) && super.canUse();
            }
        });
```

- [ ] **Step 4: Students attack a Lost player.** In the same method, find:

```java
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true)); // Attack death eaters
```

and replace it with:

```java
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true)); // Attack death eaters
        // Dark Magic: a Lost player is attacked on sight, like a Death Eater.
        this.targetSelector.addGoal(2, new DarkPlayerGoals.TargetDarkPlayer(this,
                com.hogwartsmagic.spell.dark.DarkCostRules.Cost.STUDENTS_ATTACK));
```

Also change the comment line `// Students attack monsters and death eaters, NOT players` to `// Students attack monsters and death eaters; a player only after hurting them, or on sight at Lost (Dark Magic)`.

- [ ] **Step 5: Build, greps and boot.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 16 tests, all green.
  - `grep -c "DarkPlayerGoals" src/main/java/com/hogwartsmagic/entity/DementorEntity.java src/main/java/com/hogwartsmagic/entity/StudentEntity.java`. Expected: `1` and `2`.
  - `grep -n "addGoal(1, new HurtByTargetGoal" src/main/java/com/hogwartsmagic/entity/DementorEntity.java src/main/java/com/hogwartsmagic/entity/StudentEntity.java`. Expected: one line each (unchanged).
  - Run the boot block (Global Constraints). Expected: `Done (`, three `Summoned new` lines (or the stdin note), no `Exception` or `ERROR` from `hogwartsmagic`, no server java process left.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 2: Dementors prefer dark players, students avoid or attack them

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java src/main/java/com/hogwartsmagic/entity/DementorEntity.java src/main/java/com/hogwartsmagic/entity/StudentEntity.java
```

---

### Task 5: House Elves flee a Lost wizard

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java`

**Interfaces.** Consumes: T1 (`DarkCostRules.elvesFlee`, `ELF_FLEE_DISTANCE`, `ELF_FLEE_STOP_DISTANCE`, `NPC_CHECK_EVERY_CALLS`, `Cost.ELVES_FLEE`), T2 (`DarkCosts.tierOf`, `DarkCosts.anyoneOnlineAt`). Produces: the elf cost. Do not touch `canDisapparate` (Brewing Phase 2 owns `CombatEffects.isStunned`).

- [ ] **Step 1: Register the goal.** In `src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java`, inside `registerGoals`, find:

```java
        this.goalSelector.addGoal(1, new FleeNoticedWizardGoal());
```

and replace it with:

```java
        this.goalSelector.addGoal(1, new FleeNoticedWizardGoal());
        // Dark Magic: a bound or freed elf backs away from a Lost wizard, its master included.
        this.goalSelector.addGoal(1, new FleeLostWizardGoal());
```

- [ ] **Step 2: The helper and the goal.** In the same file, find:

```java
    /** A bound elf keeps a servile distance; a freed one simply keeps up. */
    private class FollowMasterGoal extends Goal {
```

and replace it with:

```java
    /** A Lost wizard frightens every elf (Dark Magic): never a creative or spectating one. */
    private static boolean frightens(Player player) {
        return !player.isSpectator() && !player.isCreative()
                && com.hogwartsmagic.spell.dark.DarkCostRules.elvesFlee(
                        com.hogwartsmagic.spell.dark.DarkCosts.tierOf(player));
    }

    /**
     * Dark Magic Phase 2: a bound or freed elf backs away from any Lost wizard within 8 blocks until 12 blocks lie
     * between them, its own master included. A wild elf keeps FleeNoticedWizardGoal. Searches once per
     * NPC_CHECK_EVERY_CALLS evaluations, and not at all while nobody online is Lost.
     */
    private class FleeLostWizardGoal extends Goal {

        @Nullable
        private Player threat;
        private int checkCountdown;

        FleeLostWizardGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (HouseElfEntity.this.isWild()) {
                return false;
            }
            if (--this.checkCountdown > 0) {
                return false;
            }
            this.checkCountdown = com.hogwartsmagic.spell.dark.DarkCostRules.NPC_CHECK_EVERY_CALLS;
            if (!com.hogwartsmagic.spell.dark.DarkCosts.anyoneOnlineAt(HouseElfEntity.this.level(),
                    com.hogwartsmagic.spell.dark.DarkCostRules.Cost.ELVES_FLEE)) {
                return false;
            }
            this.threat = this.nearestLostWizard();
            return this.threat != null;
        }

        @Override
        public boolean canContinueToUse() {
            double stop = com.hogwartsmagic.spell.dark.DarkCostRules.ELF_FLEE_STOP_DISTANCE;
            return this.threat != null && this.threat.isAlive()
                    && !HouseElfEntity.this.isWild()
                    && HouseElfEntity.this.distanceToSqr(this.threat) < stop * stop
                    && frightens(this.threat);
        }

        @Override
        public void stop() {
            this.threat = null;
            HouseElfEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.threat == null) {
                return;
            }
            HouseElfEntity.this.getLookControl().setLookAt(this.threat, 30.0F, 30.0F);
            Vec3 away = HouseElfEntity.this.position()
                    .subtract(this.threat.position()).normalize().scale(6.0D);
            Vec3 target = HouseElfEntity.this.position().add(away);
            HouseElfEntity.this.getNavigation().moveTo(target.x, target.y, target.z, 1.3D);
        }

        @Nullable
        private Player nearestLostWizard() {
            double range = com.hogwartsmagic.spell.dark.DarkCostRules.ELF_FLEE_DISTANCE;
            Player nearest = null;
            double best = range * range;
            for (Player player : HouseElfEntity.this.level().players()) {
                double distance = HouseElfEntity.this.distanceToSqr(player);
                if (distance < best && frightens(player)) {
                    best = distance;
                    nearest = player;
                }
            }
            return nearest;
        }
    }

    /** A bound elf keeps a servile distance; a freed one simply keeps up. */
    private class FollowMasterGoal extends Goal {
```

- [ ] **Step 3: No following a Lost master.** In `FollowMasterGoal`, find:

```java
            return this.master != null && !this.master.isSpectator()
                    && HouseElfEntity.this.distanceToSqr(this.master) > START_DISTANCE * START_DISTANCE;
```

and replace it with:

```java
            return this.master != null && !this.master.isSpectator()
                    && !frightens(this.master)
                    && HouseElfEntity.this.distanceToSqr(this.master) > START_DISTANCE * START_DISTANCE;
```

Then find:

```java
            return this.master != null && this.master.isAlive() && !HouseElfEntity.this.isWild()
                    && HouseElfEntity.this.distanceToSqr(this.master) > STOP_DISTANCE * STOP_DISTANCE;
```

and replace it with:

```java
            return this.master != null && this.master.isAlive() && !HouseElfEntity.this.isWild()
                    && !frightens(this.master)
                    && HouseElfEntity.this.distanceToSqr(this.master) > STOP_DISTANCE * STOP_DISTANCE;
```

(So an elf whose master turns Lost stops following, and never Apparates to them; it follows again when the master is Tainted or lighter.)

- [ ] **Step 4: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 16 tests, all green.
  - `grep -c "frightens(" src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java`. Expected: `5` (the definition, two in the flee goal, two in the follow goal).
  - `grep -c "new FleeLostWizardGoal()" src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java`. Expected: `1`.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 2: House Elves flee a Lost wizard, even when bound

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java
```

---

### Task 6: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 16 tests (plus any tests another phase committed since Task 1, which the ledger names), all green.
- [ ] **Step 2:** Safety greps (`<T1>` is the commit before Task 1, from the ledger):
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java`. Expected: nothing.
  - `git diff <T1> -- src/main/java/com/hogwartsmagic/spell/dark src/main/java/com/hogwartsmagic/entity/DarkPlayerGoals.java src/main/java/com/hogwartsmagic/entity/DementorEntity.java src/main/java/com/hogwartsmagic/entity/StudentEntity.java src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java | grep "^+" | grep -n "switch\|SubscribeEvent\|broadcastEntityEvent"`. Expected: nothing.
  - `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: the line recorded in Task 1 Step 0 (unless the ledger records another phase changing it).
  - This phase changed no config, registration or lang file: `git log --format=%s <T1>..HEAD -- src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json | grep -c "Dark Magic Phase 2"`. Expected: `0`.
  - `git status --short`. Expected: clean except untracked `run/` output (and other agents' in-flight files, which are not this phase's).
- [ ] **Step 3:** Run the boot block (Global Constraints). Expected: `Done (`; three `Summoned new` lines (or the stdin note); no `Exception` or `ERROR` from `hogwartsmagic`; no server java process left afterwards.
- [ ] **Step 4:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 7: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 6. A survival world, normal difficulty, the spells learned (Expecto Patronum at any rank). Two players (A and B) for rows 7-9 and 14. `/darkmagic set` (Phase 1) sets the tier; tiers: 0 Clean, 20 Shadowed, 40 Tainted, 60 Corrupted, 80 Lost.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | `/darkmagic set @s 0` | Cast Expecto Patronum 40 times (tap and hold, waiting out the cooldown) | About 6 stags (15%); every other cast is the shield |
| 2 | `/darkmagic set @s 25` (Shadowed) | Same 40 casts | About 3 stags (7.5%); never a failed cast |
| 3 | `/darkmagic set @s 45` (Tainted) | Same 40 casts | Never a stag; always the shield; the charm never fails |
| 4 | No Dementors near, full mana | Hold the shield 10 s at 45, then at 65 (Corrupted) | The mana drained at 65 is about 1.5 times the drain at 45 |
| 5 | `/darkmagic set @s 45`, overworld, do not sleep | `/tick sprint 3d`, then wait into the night near the surface | Insomnia Dementors start to come within a few minutes of night |
| 6 | Row 5 at `/darkmagic set @s 25`, sleep once, then `/tick sprint 3d` | Wait through a night | No insomnia Dementors (they need 5 days below Tainted) |
| 7 | A at 65, B at 0, `/summon hogwartsmagic:dementor` nearer to B than to A, both within 40 blocks and in sight | Watch the Dementor | It goes for A |
| 8 | Row 7, then `/darkmagic set A 45` | `/summon` a new Dementor nearer to B | It goes for the nearest player (B), as before this phase |
| 9 | No player near, a Student and a Dementor together | Watch | The Dementor still attacks the Student (NPC targets unchanged) |
| 10 | A at 65 walks into a group of Students | Walk up to them | They back off and keep about 8 blocks away; nobody attacks A |
| 11 | Row 10 | Hit one Student | That Student fights back (and its friends, as before) |
| 12 | `/darkmagic set @s 80` (Lost) near Students | Walk into view | Students attack on sight |
| 13 | Full health, `/darkmagic set @s 80` | Look at the hearts | 9 hearts (18), health lowered to 18; `/darkmagic set @s 79`: 10 heart slots again, health stays 18 (no heal, no damage) |
| 14 | At 80, full health 18, then `/damage @s 17 minecraft:out_of_world` (1 health left) | `/darkmagic set @s 79`, then 80 again | Never dies; health stays 1 |
| 15 | At 80, a Hufflepuff, or with a Horcrux | Look at the hearts | Hufflepuff: 11 hearts (22); Horcrux: 8 hearts (16); both: 10 hearts (20) |
| 16 | At 80 | Relog; die and respawn; go through a Nether portal | Still 9 hearts each time; `/darkmagic set @s 0` gives 10 back |
| 17 | A bound House Elf following you | `/darkmagic set @s 80` | The elf backs away to about 12 blocks and stops following; it never Apparates to you; at 79 it follows again |
| 18 | A freed willing elf and another player B at 80 near it | Watch | The elf backs away from B too |
| 19 | B watches A | `/darkmagic set A 60`, then 80, then 59 | B sees faint smoke rising around A at 60, thicker at 80, none at 59 |
| 20 | Row 19 at 80 | A wears the Invisibility Cloak; then spectator mode; then drinks Polyjuice | No smoke while cloaked or spectating; smoke under Polyjuice |
| 21 | Config `enableDarkMagic = false` (edit while running; it reloads), A at 80 | Check rows 1, 4, 10, 12, 13, 17, 19 | Every cost is gone: stags at 15%, normal upkeep, Students normal, 10 hearts at once, elves follow, no smoke. Turning it back on restores them |
| 22 | Twenty Students loaded, nobody dark, then A at 80 | `/tick query` both times | No visible change in tick time |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Phase 2.**
  - Bullet 1, Patronus (corporeal chance by tier, none from Tainted, shield upkeep from Corrupted, never fails): Task 1 (`corporealPatronusChance`, `shieldUpkeepMultiplier`; tests `corporealChanceByTier`, `thePatronusNeverFails`, `shieldUpkeepIsHalfAgainFromCorrupted`), Task 3 Steps 1-2; rulings 2-3; rows 1-4.
  - Bullet 2, Dementors (prefer Corrupted+ in range, ties to the nearest; otherwise as today) and insomnia from Tainted: Task 4 Step 2, Task 3 Step 3; rulings 4-5; tests `dementorsPreferCorruptedAndLost`, `insomniaComesAfterThreeDaysFromTainted`; rows 5-9. No night Dementors for rested Lost players: nothing added (user ruling).
  - Bullet 3, Students (avoid at Corrupted about 8 blocks, target at Lost, hurt-by unchanged): Task 4 Steps 3-4; ruling 6; test `studentsKeepAwayAtCorruptedAndAttackAtLost`; rows 10-12.
  - Bullet 4, Lost -2 max health (tier change, login, respawn, dimension change; clamp; never kills; stacks) and House Elves flee even when bound: Task 2 Steps 1-3, Task 5; rulings 7, 9-10; tests `lostCostsTwoMaxHealthAndStacks`, `healthIsClampedButNeverKills`, `elvesFleeOnlyFromLost`; rows 13-18.
  - Bullet 5, smoke at tier 3+ visible to others, rate-limited, nothing per tick when nobody is dark: Task 2 Steps 1, 4; ruling 11; test `smokeAtCorruptedAndThickerAtLost`; rows 19-20.
  - Decision 7's "each tier keeps the costs below it": test `aTierKeepsTheCostsBelowItExceptTheTwoReplacements`, ruling 1.
  - Toggle: every cost reads `DarkMagicData.getTier` (`DarkCosts.tierOf`, `corporealPatronusChance`, `refreshMaxHealth`, `tickSmoke`, the insomnia call); the reload resyncs through `broadcastAfterReload` to `sync` to `refreshMaxHealth` and `invalidatePresence`; test `theToggleOffMeansNoCost`; row 21.
  - NPC cost: ruling 12; `presenceCacheGoesStaleAfterOneSecond`; row 22.
- **Type consistency.** `DarkCostRules.corporealPatronusChance(float, int)` called by `DarkCosts.corporealPatronusChance(ServerPlayer, float)` with `(base, getTier(caster))`, and that from `castAs` with `(player, CORPOREAL_PATRONUS_CHANCE)` where `player` is the `ServerPlayer` parameter of `castAs`. `DarkCosts.shieldUpkeepMultiplier(Entity)` takes the `Player` of `getPatronusShieldUpkeepCost`. `DarkCostRules.insomniaTicks(int)` returns `int`, compared with the `int sinceRest`. `Cost.appliesAt(int)` takes `DarkCosts.tierOf(LivingEntity)` (an `Entity`). `TargetDarkPlayer(Mob, Cost)` gets `this` from `DementorEntity` (a `Monster`) and `StudentEntity` (an `AbstractIllager`); `AvoidDarkPlayer(PathfinderMob, Cost, float, double, double)` gets `StudentEntity` and `STUDENT_KEEP_AWAY_DISTANCE` (a `float`). Checked against the 1.21 mapped jar: `NearestAttackableTargetGoal(Mob, Class, int, boolean, boolean, Predicate<LivingEntity>)` and its `protected final Mob mob` (from `TargetGoal`); `AvoidEntityGoal(PathfinderMob, Class, float, double, double, Predicate<LivingEntity>)` and its `protected final PathfinderMob mob`; `AttributeInstance.getModifier(ResourceLocation)`, `removeModifier(ResourceLocation)`, `addTransientModifier(AttributeModifier)`; `AttributeModifier.amount()`; `MinecraftServer.getTickCount()`; `ServerLevel.sendParticles(ParticleOptions, double, double, double, int, double, double, double, double)`. `LivingEntity` has no attribute-change health clamp in 1.21 (so the existing per-tick Hufflepuff and Horcrux re-application does not clamp health, and neither does this phase except in `refreshMaxHealth`). The mob goal selector runs every 2 ticks (`Mob.serverAiStep`), which `NPC_CHECK_EVERY_CALLS = 5` turns into about every 10 ticks.
- **Placeholder scan:** none. Every number, anchor, priority, speed, particle and command is given. `<T1>` in Task 6 is the ledger's recorded commit, not a placeholder in code.
- **Counts:** Task 1 adds 16 tests (`costWindowsMatchTheSpec`, `eachTierHasExactlyTheSpecCosts`, `aTierKeepsTheCostsBelowItExceptTheTwoReplacements`, `corporealChanceByTier`, `thePatronusNeverFails`, `shieldUpkeepIsHalfAgainFromCorrupted`, `insomniaComesAfterThreeDaysFromTainted`, `dementorsPreferCorruptedAndLost`, `studentsKeepAwayAtCorruptedAndAttackAtLost`, `elvesFleeOnlyFromLost`, `lostCostsTwoMaxHealthAndStacks`, `healthIsClampedButNeverKills`, `smokeAtCorruptedAndThickerAtLost`, `presenceCacheGoesStaleAfterOneSecond`, `tiersOutsideTheRangeAreClamped`, `theToggleOffMeansNoCost`). Total BASE + 16.
- **Concurrency with Brewing Phase 2:** no shared file except `SpellPackets.java` (two expressions inside `castAs`, far from `register()` and the protocol) and `ModEvents.java` (four spots: login, the decay lines, the upkeep method, the insomnia check). `HouseElfEntity` references `CombatEffects.isStunned` in `canDisapparate`; this plan does not touch that method.
- **Spec notes:** (1) decision 7 says each tier keeps the costs below it, but students cannot both keep away and attack, and "no corporeal" absorbs "halved"; treated as replacements (ruling 1). (2) "Shown like the Horcrux health cost": the Horcrux cost has no UI beyond the vanilla heart row, so neither does this. (3) The Phase 2 bullet lists tier change, login and respawn; dimension change is covered too through `syncPersistentMagicState`. (4) The roadmap's build line (`timeout 900 ./gradlew`) is superseded by the Global Constraints. (5) "In range" for Dementors is not defined by the spec; it is the follow range (48) with line of sight (ruling 5).
