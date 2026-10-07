# Dark Magic Phase 3 (The Rewards and the Deeds) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** Dark magic becomes a temptation as well as a tax. From Corrupted a dark spell costs 15% less mana (after the wand and mastery multipliers, shown the same in the Grimoire), and a Dark Mark buffs you as it buffs Death Eaters (Strength II, Regeneration). At Lost, Death Eaters leave you alone until you strike (Voldemort does not), they no longer raid your home, and a Morsmordre sky raid you start calls a Death Eater escort to your side for 5 minutes instead of a raid near you. The deeds count: an Avada kill of an innocent adds 3 Darkness, forging a Horcrux adds 30 (and lifts you to at least 60), and while the Horcrux exists Darkness cannot fall below 60. The way back is the light spell: a Heal on another hurt living thing removes 1 Darkness, at most once per `darkMagicRedemptionTicks` of play. Every reward reads the tier, so `enableDarkMagic = false` turns every one of them off. Advancements and docs (Phase 4) are not in this plan.

**Architecture:**
- **Pure rules** (`spell/dark/DarkRewardRules`): a `Reward` enum with the lowest tier of each reward, the 0.85 multiplier and the rounded cost, the escort numbers (duration, size, distances, leash, check phase), the innocent test, the deed payer, the Horcrux result and floor, the "was hurt" test, the heal rule, the redemption result and the play-time window. Minecraft-free, test-first (17 tests).
- **Server glue** (`spell/dark/DarkRewards`): the mana multiplier for a player (the server's tier for a `ServerPlayer`, the client's own synced tier on a client level), the Dark Mark and Death Eater tier checks, and whether a caster earns the escort. (`spell/dark/DarkDeeds`): the Avada innocent bonus, the Horcrux forge bonus, and redemption through Heal.
- **The escort** (`entity/DeathEaterEscort`): summon, the per-second owner/expiry/catch-up check, Disapparition, and two goals (`FollowOwnerGoal`, `DefendOwnerGoal`) that every Death Eater carries and that do nothing unless it is an escort. `WizardEntity` stores the owner and end time in its own NBT.
- **Hooks:** `SpellCosts.manaCost` and three channel costs (Fiendfyre startup and upkeep, Imperio start and upkeep); `DarkMagicClientState` feeds the client copy; `DarkMarkEntity.applyBuffs`; the plain player target goal of `WizardEntity`; `ModEvents.tryStartDeathEaterBaseRaid`; `SpellBeamEntity.tryStartMorsmordreSkyRaid` and the Avada branch of `onHitInner`; `ModEvents.tickHorcruxRitual` at the forging; `DarkMagicData.floor`; `HealSpell.apply`.
- **On the wire and on disk:** no packet, no protocol change, no config value. Three lang strings. One player key (`DarkMagicLastRedemption`) and two Death Eater keys (`EscortOwner`, `EscortUntil`).

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-07-dark-magic-roadmap.md`: decision 4 (deeds: innocent +3, Horcrux +30 and floor 60), decision 5 (puppets charge the puppeteer; creative and spectator gain nothing), decision 6 (tiers), decision 8 (rewards: Corrupted discount and Dark Mark; Lost neutrality and escort), decision 10 (redemption through Heal, capped), decision 13 (`enableDarkMagic`, `darkMagicRedemptionTicks`), decision 16 (NPCs have no Darkness), "Phase 3: The rewards and the deeds", "User answers", "Global constraints". Phase 4 is out of scope.

## Requirements (Phase 3 of the spec)

1. **Discount:** a dark spell costs 15% less mana at Corrupted and Lost, in `SpellCosts.manaCost`, after the wand and mastery multipliers; players only; the Grimoire cost line shows the same number the server charges (decision 8; Phase 3 bullet 1).
2. **Dark Mark:** a Corrupted or Lost player within a Dark Mark's buff area gets the buffs Death Eaters get (decision 8; Phase 3 bullet 2).
3. **Neutral Death Eaters:** at Lost, Death Eaters do not pick you as a target; one you strike still fights back (the hurt-by goal is unchanged); Voldemort stays hostile (decision 8; Phase 3 bullet 3).
4. **Escort:** a Morsmordre sky raid started by a Lost player gives a Death Eater escort that follows them for 5 minutes instead of a raid near them (decision 8; Phase 3 bullet 3).
5. **Innocent:** an Avada kill of a villager, Student, House Elf or any mob `MasteryAwards.isPassive` calls passive adds 3; a puppet's kill charges the puppeteer (decisions 4, 5; Phase 3 bullet 4).
6. **Horcrux:** making a Horcrux adds 30; Darkness cannot decay below 60 while it exists; the floor goes when it shatters or is reconciled away (decision 4; Phase 3 bullet 4).
7. **Redemption:** a Heal that heals another hurt living thing (not yourself) removes 1, at most once per `darkMagicRedemptionTicks` of play; NPC heals and puppet heals redeem nobody (decision 10; Phase 3 bullet 5).
8. Every reward and deed reads the tier or checks `enableDarkMagic`, so the toggle turns them all off (decision 13).
9. Done when: a Lost player walks through a Death Eater camp without a fight until they strike, a Morsmordre cast at Lost gives an escort, and healing villagers brings a Lost player back down.

## Rulings made while planning

1. **One reward table.** `DarkRewardRules.Reward`: `DARK_MANA_DISCOUNT` and `DARK_MARK_BUFF` from Corrupted, `DEATH_EATERS_NEUTRAL` and `MORSMORDRE_ESCORT` from Lost. A tier keeps every reward below it. A tier outside 0..4 is clamped.
2. **The discount** is `0.85` for the nine `DarkRules` dark spells at Corrupted and Lost, multiplied after `base x wand x mastery` and before the one rounding (`max(1, round(...))`, now `DarkRewardRules.manaCost`). Avada 100 to 85, Morsmordre 90 to 77, Fiendfyre 85 to 72, Imperio 80 to 68, Crucio 60 to 51. The same multiplier goes into the three channel costs that do not come through `manaCost`: the Fiendfyre startup (`SpellPackets.getFiendfyreStartupCost`) and upkeep (`ModEvents.getFiendfyreUpkeepCost`), and the Imperio start and upkeep (`ModEvents.getImperioUpkeepCost`), each inside its existing minimum. Mana refunds, cooldowns and every other spell are unchanged. A puppet cast reads the body's tier (the body pays the mana).
3. **The client cost.** The Grimoire line (`SpellCompendiumScreen`), the client "Not enough mana!" checks (`ModEventsClientHooks` and `ModEvents.handleWandUse`, which is common code running on the client) all call `SpellCosts.manaCost` with the client player. `SpellCosts` is common code and cannot read `client/DarkMagicClientState`, so `DarkRewards` keeps a volatile copy of the client's own tier, written only by `DarkMagicClientState.accept` and `clear` (the `MasteryConfig` synced-values pattern). It is read only for a non-`ServerPlayer` on a client level; a server player always reads `DarkMagicData.getTier`. With `enableDarkMagic` off the server sends 0, so the client copy is Clean too.
4. **The Dark Mark** buffs any Dark Mark's area (the player's own, a raid's, an NPC leader's): the existing box (100 x 120 x 100 around the Mark, refreshed every 100 ticks after the first 40), the existing buffs (Strength II and Regeneration I for 200 ticks), for every player in the box at Corrupted or Lost who is not a spectator. Creative players at those tiers get it too (harmless).
5. **Neutral Death Eaters.** `WizardEntity`'s plain player goal becomes `NearestAttackableTargetGoal(this, Player.class, 10, true, false, target -> !ignoresPlayer(target))` (the same interval and sight rule as before) and `ignoresPlayer` is true for a Lost player (and, from Task 4, for the Death Eater's own escort owner). `HurtByTargetGoal` is unchanged and still alerts nobody: the one you strike fights you, the rest of the camp stays neutral. A Death Eater already fighting you when you turn Lost keeps fighting until it loses you. `VoldemortEntity` has its own `registerGoals` and is untouched; the Death Eaters he summons still take his target (`VoldemortSummoningEntity`), so the boss fight stays a fight. Death Eater base raids (`tryStartDeathEaterBaseRaid`) never start against a Lost player. Other raids near a Lost player happen as before, but their members do not pick the Lost player.
6. **Who earns the escort:** a Morsmordre beam that reaches the sky (the existing `canTriggerMorsmordreSkyRaid`) whose owner is a Lost `ServerPlayer` casting of their own will: not a puppet beam, not a forced body (`PossessionManager.isForcedCaster`), not a reflected beam. The Dark Mark and the lightning appear as before; then 4 to 6 Death Eaters Apparate (smoke, a crack, a 2-second Tenebris Flight) 3 to 8 blocks from the caster on the surface (or at the caster's feet when underground), spawned as `EVENT` (so never raid leaders, never on brooms). No raid bar, no `startWizardRaid`, no Hero of the Village. One chat line to the caster. One escort per player: a new one dismisses the old one's members within 96 blocks.
7. **Escort behaviour.** `FollowOwnerGoal` (goal priority 1, MOVE and LOOK): with no target it walks back when more than 8 blocks away and stops at 4; past 20 blocks (the leash) it drops its target and walks back whatever it was doing. `DefendOwnerGoal` (target priority 1): it targets whatever last hurt the owner, or whatever the owner last struck in melee or with an arrow (vanilla `getLastHurtMob`); a spell the owner casts does not direct it (spell damage does not set `getLastHurtMob`). It ignores what the owner fought before it arrived. It never targets another Death Eater (`WizardEntity.canAttack`) or its owner. Otherwise an escort is an ordinary Death Eater: it still attacks villagers, golems, Students and players who are not Lost. Past 32 blocks (a broom, a portal-free sprint) it Apparates to a spot near the owner.
8. **The escort ends** after 5 minutes of game time (`EscortUntil`), or at once when the owner is not in its level, is dead or is spectating (logout, death, dimension change), checked once a second per member: each member Disapparates (smoke and a crack, no drops). A saved end more than 5 minutes ahead is treated as expired. The owner and end time are saved in the Death Eater's NBT, so an escort in an unloaded chunk leaves as soon as it ticks again.
9. **Striking your escort:** the member the owner hurts stops being an escort at once and fights back through its unchanged hurt-by goal; the other members stay. Any hit whose damage names the owner as the attacker counts (melee, arrows, and spells whose damage source carries the caster).
10. **Innocents:** `Villager`, `StudentEntity`, `HouseElfEntity`, or `MasteryAwards.isPassive` (made public, unchanged). Students and elves count even while they fight you. Players, Death Eaters and monsters never count. Only a kill counts (the victim is dead after the Avada effect); a survivor (a totem, Voldemort's evasion) adds nothing. The +3 is on top of the cast's +6.
11. **Who pays for a deed** (`DarkRewardRules.deedPayer`): a reflected beam charges nobody; a puppet beam charges its puppeteer (if online); a forced body charges nobody; otherwise the caster (the Phase 1 rule). Creative and spectator payers and NPC casters gain nothing. An action-bar line tells the payer.
12. **The Horcrux bonus** is given when the Horcrux is forged (the end of `tickHorcruxRitual`, after `bindToHorcrux`), not in `HorcruxData.startRitual`: a ritual can still fail in its one-second window (the altar is broken), and a failed ritual adds nothing. The result is `max(Darkness + 30, 60)`, capped at 100 (`afterHorcrux`): the floor would otherwise freeze a value under 60 (a Phase 1 loss never moves a value already at or below the floor). The bonus does not reset the quiet window. Creative, spectator or toggle off: no bonus.
13. **The floor** is 60 while `HorcruxData.hasActiveHorcrux(player)` and the ritual is not active (bound and forged), else 0. It needs no hook of its own: the binding is cleared by `clearBinding` when the Horcrux shatters at a rebirth (`resolveHorcruxRespawn`), when a failed ritual is undone, and when `reconcileMissingHorcrux` finds the block gone (every player tick, so also at login after it was broken while the owner was away). Decay and redemption stop at it; `/darkmagic set` ignores it (Phase 1).
14. **Redemption:** `HealSpell.apply` reads "was hurt" (alive and below max health) before any cleanse or heal, then after the heal calls `DarkDeeds.onHeal`. It redeems when the caster is a `ServerPlayer`, the heal is not a puppet beam (`MindControl.hitPuppeteer()`) and the caster is not a forced body, the caster is not creative or spectator, the target is another living thing (not the caster, not an armour stand), and it was hurt. Healing a mob you hurt yourself counts (the cap makes it slow). 1 point through the floor; a heal that would lighten nothing (Clean, or at the floor) does not spend the window.
15. **The window** counts ticks of play with the vanilla play-time stat (`Stats.PLAY_TIME`, online ticks only, kept across death), stored at the last redemption as `DarkMagicLastRedemption`. Open with no record, after a full `darkMagicRedemptionTicks`, or if the stat went backwards. An action-bar line confirms a redemption; a tier change is announced as usual by `DarkMagicData`.
16. **Nothing else changes:** no config value (the escort numbers are fixed), no packet, no protocol change, no `@SubscribeEvent`, no entity event byte, no advancement (Phase 4). Students, Dementors, House Elves and the Patronus (Phase 2) are untouched.
17. **Phase 2 dependency:** `DarkRewards` reads a player's tier through Phase 2's `DarkCosts.tierOf(Entity)` (Phase 2 Task 2). Nothing else from Phase 2 is used or changed.

## Global Constraints

- Server authoritative. `spell/dark/`, `entity/DeathEaterEscort.java` and every common file this plan touches never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. The one client file touched (`client/DarkMagicClientState`) calls common code only.
- No `switch` on an enum anywhere in the new code. Use if-chains, `==` and the `Reward` tiers.
- No new hurt, death or effect handler, and no new `@SubscribeEvent`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- Never load a chunk on purpose: the escort spawns and lands within 8 blocks of its owner; every search is `getEntitiesOfClass` or `getPlayerByUUID`.
- Terrain: this plan changes no block.
- `en_us.json` stays valid JSON (each task that touches it checks with `python3 -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json'))"`).
- Build and test (cloud):
  - First `pgrep -fa "[G]radleWrapperMain"`. If it prints a process, another agent's build is running: wait (poll every 30 s, at most 20 min). An idle `GradleDaemon` alone is fine.
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkRewardRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- **Base test count: the count at the start of Task 1; all green.** Call it BASE. The implementer of Task 1 runs the full build before Step 1 and records BASE in the report. Task 1 adds 17 (BASE + 17); no other task adds tests. All tests stay green.
- **The network protocol is not changed by this plan.** Before Task 1, record the current line (never assume its number): `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Task 6 checks it is identical (unless another phase changed it in between, which the ledger will say).
- Dedicated-server boot (Task 4 and Task 6), "the boot block":
  1. `cd /home/user/minecraft_mods && mkdir -p run && echo "eula=true" > run/eula.txt && rm -f run/logs/latest.log && : > /tmp/dm3_srv_in.txt`
  2. `cd /home/user/minecraft_mods && (tail -f /tmp/dm3_srv_in.txt | timeout 900 bash ./gradlew runServer --console=plain --max-workers=2 > /tmp/dm3_srv.log 2>&1 &)`
  3. Wait up to 10 min: `cd /home/user/minecraft_mods && timeout 600 bash -c 'until grep -qE "Done \(|Exception|Crash" run/logs/latest.log 2>/dev/null; do sleep 5; done'; grep -nE "Done \(|Exception|Crash" run/logs/latest.log | head`
  4. Exercise the changed entities (the console reads stdin, see `build.gradle`): `printf 'summon hogwartsmagic:wizard ~3 ~1 ~\nsummon hogwartsmagic:student ~-3 ~1 ~\nsummon hogwartsmagic:dark_mark ~ ~20 ~\n' >> /tmp/dm3_srv_in.txt`, then `cd /home/user/minecraft_mods && timeout 60 bash -c 'until [ "$(grep -c "Summoned new" run/logs/latest.log)" -ge 3 ]; do sleep 2; done'; grep -c "Summoned new" run/logs/latest.log`. Expected `3`. Then let them tick (the Mark buffs after 100 ticks): `cd /home/user/minecraft_mods && timeout 20 bash -c 'until grep -qE "Exception|Crash" run/logs/latest.log; do sleep 2; done'; echo exit=$?`. Expected `exit=124` (no exception in 20 s). If the count stays `0`, the console did not get stdin: write "summons skipped: console stdin not forwarded" in the report and go on (not a failure; the human checklist covers them).
  5. `printf 'stop\n' >> /tmp/dm3_srv_in.txt; cd /home/user/minecraft_mods && timeout 90 bash -c 'until grep -q "All dimensions are saved" run/logs/latest.log; do sleep 2; done'`
  6. `cd /home/user/minecraft_mods && grep -nE "Done \(|Exception|ERROR|Summoned new" run/logs/latest.log | head -40`
  7. Stop only the java server process and the helper `tail`, never a Gradle process:
     ```bash
     ps -eo pid=,comm=,args= | awk '$2=="java" && $0 !~ /org\.gradle\./ && $0 ~ /nogui|forge_userdev_server|ForgeBootstrap|BootstrapLauncher/ {print $1}' | xargs -r kill
     pkill -f "[t]ail -f /tmp/dm3_srv_in.txt"
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
- Line numbers drift. Every edit names the exact text to find, quoted from 876555c (or from an earlier task of this plan, which says so). Each anchor was checked to occur exactly once in its file at 876555c.
- **Built after Brewing Phase 2 and Dark Magic Phase 2.** Brewing Phase 2 touches `BasiliskEntity`, petrification, statues, thrown potions, `ModEvents`, the `SpellPackets` protocol, `CombatEffects`, `ExampleMod` and `en_us.json`; Dark Magic Phase 2 touches `DarkMagicData.sync`, the login order, the smoke and decay lines and the shield upkeep and insomnia check in `ModEvents`, the Patronus rolls in `SpellPackets`, and the Dementor, Student and House Elf goals. This plan's anchors avoid all of those: in `ModEvents` it edits only `getImperioUpkeepCost`, `getFiendfyreUpkeepCost`, the start of `tryStartDeathEaterBaseRaid` and the forging lines of `tickHorcruxRitual`; in `SpellPackets` only `getFiendfyreStartupCost`; in `DarkMagicData` only `floor`; in `en_us.json` it inserts after the Phase 1 `tier_down` line. It builds on one Phase 2 symbol, `DarkCosts.tierOf(Entity)` (pre-flight in Task 2). If an anchor has moved anyway, find the same spot by its text, keep both changes, and say so in the report.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java` | Pure: the reward table, the discount, escort numbers, deeds, floor, redemption | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java` | 17 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkRewards.java` | Mana multiplier (server and client tier), Mark and Death Eater checks, escort eligibility | 2 |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellCosts.java` | `manaCost` with the dark multiplier; `darkMultiplier` | 2 |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | Fiendfyre startup cost | 2 |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Imperio and Fiendfyre upkeep (T2); base raid skip (T3); Horcrux forge bonus (T5) | 2, 3, 5 |
| Modify `src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java` | Feeds the client tier copy | 2 |
| Modify `src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java` | Buffs Corrupted and Lost players | 3 |
| Modify `src/main/java/com/hogwartsmagic/entity/WizardEntity.java` | Player goal predicate (T3); escort state, goals, tick, NBT (T4) | 3, 4 |
| Create `src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java` | Escort summon, tick, dismissal, follow and defend goals | 4 |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Escort instead of a sky raid (T4); Avada deed payer and hook (T5) | 4, 5 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Three strings | 4, 5 |
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java` | Innocent bonus, Horcrux bonus, redemption | 5 |
| Modify `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java` | `floor` reads the Horcrux | 5 |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | `isPassive` made public | 5 |
| Modify `src/main/java/com/hogwartsmagic/spell/support/HealSpell.java` | "Was hurt" and the redemption call | 5 |

Untouched on purpose: `Config.java`, `ExampleMod.java`, `SpellPackets.register()` and the protocol (ruling 16); `DarkRules` (its Phase 1 deed constants are used as they are); `VoldemortEntity`, `VoldemortSummoningEntity` (ruling 5); `HorcruxData`, `HorcruxBlockEntity` (the floor follows the binding they already clear, ruling 13); `ModEvents.startWizardRaid` and the raid bar; `DarkCosts`, `DarkCostRules` and every Phase 2 goal; every `client/` file except `DarkMagicClientState`.

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2-T5 | `DarkRewardRules.Reward` (`DARK_MANA_DISCOUNT`, `DARK_MARK_BUFF`, `DEATH_EATERS_NEUTRAL`, `MORSMORDRE_ESCORT`) with `fromTier()`, `appliesAt(int)`; constants `DARK_MANA_MULTIPLIER`, `ESCORT_DURATION_TICKS`, `ESCORT_MIN_SIZE`, `ESCORT_MAX_SIZE`, `ESCORT_SPAWN_MIN_DISTANCE`, `ESCORT_SPAWN_MAX_DISTANCE`, `ESCORT_FOLLOW_START`, `ESCORT_FOLLOW_STOP`, `ESCORT_LEASH`, `ESCORT_CATCH_UP`, `ESCORT_CHECK_INTERVAL_TICKS`, `ESCORT_REPLACE_RADIUS`; methods `manaMultiplier(String,int)`, `manaCost(float,float)`, `darkMarkBuffs(int)`, `deathEatersNeutral(int)`, `morsmordreEscort(int)`, `escortSize(int)`, `escortUntil(long)`, `escortActive(long,long)`, `dueToEscortCheck(long,long)`, `beyondLeash(double)`, `escortStartsFollowing(double,boolean)`, `escortKeepsFollowing(double,boolean)`, `needsCatchUp(double)`, `isInnocent(boolean,boolean,boolean,boolean)`, `innocentBonus(boolean,boolean)`, `deedPayer(boolean,boolean,boolean)`, `afterHorcrux(float)`, `floor(boolean)`, `wasHurt(float,float)`, `healRedeems(boolean,boolean,boolean,boolean,boolean)`, `afterRedemption(float,float)`, `redemptionWindowOpen(boolean,int,int,int)` | names identical |
| T2 → T3, T4 | `DarkRewards.acceptClientTier(int)`, `costTier(Player)`, `manaMultiplier(Player, SpellType)`, `darkMarkBuffs(Entity)`, `deathEatersIgnore(Entity)`, `earnsEscort(ServerPlayer)`; `SpellCosts.darkMultiplier(Player, SpellType)` | written in T2 |
| T3 → T4 | `WizardEntity.ignoresPlayer(LivingEntity)` (T4 extends its body) | quoted in T4 |
| T4 → T4 | `WizardEntity.setEscort(UUID,long)`, `releaseEscort()`, `isEscort()`, `getEscortOwnerId()`, `getEscortUntil()`, `getEscortOwner()`, `isEscortOwner(LivingEntity)`; `DeathEaterEscort.summon(ServerLevel, ServerPlayer)`, `tick(WizardEntity)`, `dismiss(WizardEntity)`, `FollowOwnerGoal`, `DefendOwnerGoal` | written in T4 |
| T5 | `DarkDeeds.onAvadaHit(LivingEntity, ServerPlayer)`, `onHorcruxForged(ServerPlayer)`, `onHeal(LivingEntity, Entity, boolean)`, `KEY_LAST_REDEMPTION`; `MasteryAwards.isPassive(LivingEntity)` public | written in T5 |
| Phase 1 → all | `DarkRules` (`CLEAN..LOST`, `isDark`, `darkSpellIds`, `sanitize`, `afterGain`, `afterLoss`, `payer`, `Payer`, `earns`, `effectiveTier`, `tier`, `AVADA_INNOCENT_BONUS`, `HORCRUX_BONUS`, `HORCRUX_FLOOR`, `NO_FLOOR`, `REDEMPTION_AMOUNT`, `DEFAULT_REDEMPTION_TICKS`, `DEFAULT_DECAY_TICKS`), `DarkMagicData.getTier/getStored/add/floor`, `Config.isDarkMagicEnabled()`, `Config.getDarkMagicRedemptionTicks()` | on the branch since f6aa7c4/572d247 |
| Phase 2 → T2 | `DarkCosts.tierOf(Entity)` | Phase 2 Task 2; pre-flight grep in T2 Step 0 |

---

### Task 1: DarkRewardRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java`

**Interfaces.** Consumes: Phase 1 `DarkRules` (the row in the pre-flight table). Produces: the T1 row of the pre-flight table.

- [ ] **Step 0: Record the base.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, all green. Record the test count as BASE, the commit as `<T1>` (`git rev-parse --short HEAD`), and the output of `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`, in the report.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.dark.DarkRewardRules.Reward;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkRewardRulesTest {
    private static final float EPS = 1.0e-6f;

    @Test
    void rewardTiersMatchTheSpec() {
        assertEquals(DarkRules.CORRUPTED, Reward.DARK_MANA_DISCOUNT.fromTier());
        assertEquals(DarkRules.CORRUPTED, Reward.DARK_MARK_BUFF.fromTier());
        assertEquals(DarkRules.LOST, Reward.DEATH_EATERS_NEUTRAL.fromTier());
        assertEquals(DarkRules.LOST, Reward.MORSMORDRE_ESCORT.fromTier());
        assertEquals(4, Reward.values().length);
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(tier >= DarkRules.CORRUPTED, DarkRewardRules.darkMarkBuffs(tier), "mark " + tier);
            assertEquals(tier == DarkRules.LOST, DarkRewardRules.deathEatersNeutral(tier), "neutral " + tier);
            assertEquals(tier == DarkRules.LOST, DarkRewardRules.morsmordreEscort(tier), "escort " + tier);
        }
    }

    @Test
    void onlyDarkSpellsGetTheDiscountAndOnlyFromCorrupted() {
        assertEquals(0.85f, DarkRewardRules.DARK_MANA_MULTIPLIER, EPS);
        for (String id : DarkRules.darkSpellIds()) {
            assertEquals(1.0f, DarkRewardRules.manaMultiplier(id, DarkRules.CLEAN), EPS, id);
            assertEquals(1.0f, DarkRewardRules.manaMultiplier(id, DarkRules.TAINTED), EPS, id);
            assertEquals(0.85f, DarkRewardRules.manaMultiplier(id, DarkRules.CORRUPTED), EPS, id);
            assertEquals(0.85f, DarkRewardRules.manaMultiplier(id, DarkRules.LOST), EPS, id);
        }
        for (String id : new String[] {"heal", "expecto_patronum", "stupefy", "obliviate", "confundo", "protego"}) {
            assertEquals(1.0f, DarkRewardRules.manaMultiplier(id, DarkRules.LOST), EPS, id);
        }
        assertEquals(1.0f, DarkRewardRules.manaMultiplier(null, DarkRules.LOST), EPS);
    }

    @Test
    void discountedCostsRoundAfterWandAndMastery() {
        // Base costs from SpellType: Avada 100, Morsmordre 90, Fiendfyre 85, Imperio 80, Crucio 60.
        float lost = DarkRewardRules.manaMultiplier("avada_kedavra", DarkRules.LOST);
        assertEquals(85, DarkRewardRules.manaCost(100 * 1.0f * 1.0f, lost));
        assertEquals(77, DarkRewardRules.manaCost(90 * 1.0f * 1.0f, lost));
        assertEquals(72, DarkRewardRules.manaCost(85 * 1.0f * 1.0f, lost));
        assertEquals(68, DarkRewardRules.manaCost(80 * 1.0f * 1.0f, lost));
        assertEquals(51, DarkRewardRules.manaCost(60 * 1.0f * 1.0f, lost));
        assertEquals(100, DarkRewardRules.manaCost(100 * 1.0f * 1.0f, 1.0f));
        // After a 0.8 wand and a 0.76 mastery multiplier: 100 * 0.8 * 0.76 = 60.8, then 0.85 = 51.68.
        assertEquals(52, DarkRewardRules.manaCost(100 * 0.8f * 0.76f, lost));
        assertEquals(61, DarkRewardRules.manaCost(100 * 0.8f * 0.76f, 1.0f));
        assertEquals(1, DarkRewardRules.manaCost(0.5f, lost));
    }

    @Test
    void theToggleOffMeansNoReward() {
        int off = DarkRules.effectiveTier(false, 100.0f);
        assertEquals(1.0f, DarkRewardRules.manaMultiplier("avada_kedavra", off), EPS);
        assertFalse(DarkRewardRules.darkMarkBuffs(off) || DarkRewardRules.deathEatersNeutral(off)
                || DarkRewardRules.morsmordreEscort(off));
    }

    @Test
    void tiersOutsideTheRangeAreClamped() {
        assertEquals(1.0f, DarkRewardRules.manaMultiplier("crucio", -1), EPS);
        assertEquals(0.85f, DarkRewardRules.manaMultiplier("crucio", 9), EPS);
        assertTrue(DarkRewardRules.deathEatersNeutral(7));
        assertFalse(DarkRewardRules.darkMarkBuffs(-4));
    }

    @Test
    void escortLastsFiveMinutesAndHasFourToSix() {
        assertEquals(6_000L, DarkRewardRules.ESCORT_DURATION_TICKS);
        assertEquals(1_006_000L, DarkRewardRules.escortUntil(1_000_000L));
        long until = DarkRewardRules.escortUntil(1_000L);
        assertTrue(DarkRewardRules.escortActive(1_000L, until));
        assertTrue(DarkRewardRules.escortActive(6_999L, until));
        assertFalse(DarkRewardRules.escortActive(7_000L, until));
        assertFalse(DarkRewardRules.escortActive(1_000L, 1_000L + 6_001L)); // a bad saved value ends it
        assertEquals(4, DarkRewardRules.escortSize(0));
        assertEquals(6, DarkRewardRules.escortSize(2));
        assertEquals(4, DarkRewardRules.escortSize(3));
        assertEquals(6, DarkRewardRules.escortSize(-1));
    }

    @Test
    void escortChecksOncePerSecond() {
        assertEquals(20, DarkRewardRules.ESCORT_CHECK_INTERVAL_TICKS);
        int due = 0;
        for (long tick = 0L; tick < 20L; tick++) {
            if (DarkRewardRules.dueToEscortCheck(tick, 13L)) {
                due++;
            }
        }
        assertEquals(1, due);
        assertTrue(DarkRewardRules.dueToEscortCheck(7L, 13L));
    }

    @Test
    void escortFollowsWithALeash() {
        double near = 3.0D * 3.0D;
        double mid = 10.0D * 10.0D;
        double far = 21.0D * 21.0D;
        assertFalse(DarkRewardRules.escortStartsFollowing(near, false));
        assertTrue(DarkRewardRules.escortStartsFollowing(mid, false));
        assertFalse(DarkRewardRules.escortStartsFollowing(mid, true)); // fighting near the owner
        assertTrue(DarkRewardRules.escortStartsFollowing(far, true)); // past the leash: back, target or not
        assertTrue(DarkRewardRules.escortKeepsFollowing(6.0D * 6.0D, false));
        assertFalse(DarkRewardRules.escortKeepsFollowing(near, false));
        assertFalse(DarkRewardRules.escortKeepsFollowing(mid, true));
        assertTrue(DarkRewardRules.escortKeepsFollowing(far, true));
        assertTrue(DarkRewardRules.beyondLeash(far));
        assertFalse(DarkRewardRules.beyondLeash(mid));
        assertFalse(DarkRewardRules.needsCatchUp(far));
        assertTrue(DarkRewardRules.needsCatchUp(33.0D * 33.0D));
    }

    @Test
    void innocentsAreVillagersStudentsElvesAndPassiveMobs() {
        assertTrue(DarkRewardRules.isInnocent(true, false, false, false));
        assertTrue(DarkRewardRules.isInnocent(false, true, false, false));
        assertTrue(DarkRewardRules.isInnocent(false, false, true, false));
        assertTrue(DarkRewardRules.isInnocent(false, false, false, true));
        assertFalse(DarkRewardRules.isInnocent(false, false, false, false)); // a zombie, a Death Eater, a player
    }

    @Test
    void innocentBonusOnlyForAKill() {
        assertEquals(3.0f, DarkRewardRules.innocentBonus(true, true), EPS);
        assertEquals(0.0f, DarkRewardRules.innocentBonus(false, true), EPS); // survived (a totem, a boss)
        assertEquals(0.0f, DarkRewardRules.innocentBonus(true, false), EPS);
        assertEquals(DarkRules.AVADA_INNOCENT_BONUS, DarkRewardRules.innocentBonus(true, true), EPS);
    }

    @Test
    void deedsChargeLikeCasts() {
        assertEquals(DarkRules.Payer.BODY, DarkRewardRules.deedPayer(false, false, false));
        assertEquals(DarkRules.Payer.PUPPETEER, DarkRewardRules.deedPayer(false, true, true));
        assertEquals(DarkRules.Payer.NOBODY, DarkRewardRules.deedPayer(false, false, true));
        assertEquals(DarkRules.Payer.NOBODY, DarkRewardRules.deedPayer(true, false, false));
        assertEquals(DarkRules.Payer.NOBODY, DarkRewardRules.deedPayer(true, true, false));
    }

    @Test
    void horcruxAddsThirtyAndLiftsToTheFloor() {
        assertEquals(60.0f, DarkRewardRules.afterHorcrux(0.0f), EPS);
        assertEquals(60.0f, DarkRewardRules.afterHorcrux(25.0f), EPS);
        assertEquals(66.5f, DarkRewardRules.afterHorcrux(36.5f), EPS);
        assertEquals(100.0f, DarkRewardRules.afterHorcrux(85.0f), EPS);
        assertEquals(100.0f, DarkRewardRules.afterHorcrux(100.0f), EPS);
        assertEquals(60.0f, DarkRewardRules.afterHorcrux(Float.NaN), EPS);
    }

    @Test
    void floorWhileTheHorcruxExists() {
        assertEquals(60.0f, DarkRewardRules.floor(true), EPS);
        assertEquals(0.0f, DarkRewardRules.floor(false), EPS);
        // Decay and redemption stop at the floor and resume when it goes.
        assertEquals(60.0f, DarkRules.afterLoss(60.5f, 1.0f, DarkRewardRules.floor(true)), EPS);
        assertEquals(59.5f, DarkRules.afterLoss(60.5f, 1.0f, DarkRewardRules.floor(false)), EPS);
    }

    @Test
    void redemptionLightensByOneThroughTheFloor() {
        assertEquals(84.0f, DarkRewardRules.afterRedemption(85.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(0.0f, DarkRewardRules.afterRedemption(0.5f, DarkRules.NO_FLOOR), EPS);
        assertEquals(0.0f, DarkRewardRules.afterRedemption(0.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(60.0f, DarkRewardRules.afterRedemption(60.4f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(60.0f, DarkRewardRules.afterRedemption(60.0f, DarkRules.HORCRUX_FLOOR), EPS);
    }

    @Test
    void onlyAWillingHealOfAnotherHurtThingRedeems() {
        assertTrue(DarkRewardRules.healRedeems(false, false, false, true, true));
        assertFalse(DarkRewardRules.healRedeems(true, false, false, true, true)); // yourself
        assertFalse(DarkRewardRules.healRedeems(false, true, false, true, true)); // an armour stand
        assertFalse(DarkRewardRules.healRedeems(false, false, true, true, true)); // a puppet heal
        assertFalse(DarkRewardRules.healRedeems(false, false, false, false, true)); // creative or spectator
        assertFalse(DarkRewardRules.healRedeems(false, false, false, true, false)); // already at full health
        assertTrue(DarkRewardRules.wasHurt(10.0f, 20.0f));
        assertFalse(DarkRewardRules.wasHurt(20.0f, 20.0f));
        assertFalse(DarkRewardRules.wasHurt(0.0f, 20.0f));
    }

    @Test
    void redemptionAtMostOncePerWindowOfPlay() {
        int window = DarkRules.DEFAULT_REDEMPTION_TICKS;
        assertTrue(DarkRewardRules.redemptionWindowOpen(false, 0, 0, window));
        assertFalse(DarkRewardRules.redemptionWindowOpen(true, 50_000, 40_000, window));
        assertFalse(DarkRewardRules.redemptionWindowOpen(true, 75_999, 40_000, window));
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, 76_000, 40_000, window));
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, 100, 40_000, window)); // the stat was reset
        assertFalse(DarkRewardRules.redemptionWindowOpen(true, 40_000, 40_000, 0)); // a bad window is one tick
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, 40_001, 40_000, 0));
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, Integer.MAX_VALUE, Integer.MIN_VALUE + 1, window));
    }

    @Test
    void redemptionAtMostHalvesTheTimeFromLostToClean() {
        // Decay: 1 point per 36000 ticks; redemption: 1 more per 36000 ticks of play at most.
        int window = DarkRules.DEFAULT_REDEMPTION_TICKS;
        int decay = DarkRules.DEFAULT_DECAY_TICKS;
        double hoursAlone = 100.0D * decay / 72_000.0D;
        double hoursWithHeals = 100.0D / (1.0D / decay + 1.0D / window) / 72_000.0D;
        assertEquals(50.0D, hoursAlone, 1.0e-9);
        assertEquals(25.0D, hoursWithHeals, 1.0e-9);
    }
}
```

That is 17 tests.

- [ ] **Step 2: Run it and see it fail.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkRewardRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... DarkRewardRules`).

- [ ] **Step 3: Write the rules.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java`:

```java
package com.hogwartsmagic.spell.dark;

/**
 * Dark Magic rewards and deeds (Phase 3), free of Minecraft so they are unit-tested. Every reward reads the tier
 * (DarkRules.CLEAN..LOST, through DarkMagicData.getTier, so enableDarkMagic off reads Clean), never the raw number;
 * a tier outside 0..4 is clamped into it. The deed numbers (innocent +3, Horcrux +30 and floor 60, redemption -1)
 * are DarkRules constants from Phase 1; this class decides when they apply.
 */
public final class DarkRewardRules {

    /** One reward and the lowest tier it applies at. A tier keeps every reward below it. */
    public enum Reward {
        DARK_MANA_DISCOUNT(DarkRules.CORRUPTED),
        DARK_MARK_BUFF(DarkRules.CORRUPTED),
        DEATH_EATERS_NEUTRAL(DarkRules.LOST),
        MORSMORDRE_ESCORT(DarkRules.LOST);

        private final int fromTier;

        Reward(int fromTier) {
            this.fromTier = fromTier;
        }

        public int fromTier() {
            return this.fromTier;
        }

        public boolean appliesAt(int tier) {
            return clampTier(tier) >= this.fromTier;
        }
    }

    /** Corrupted and Lost: a dark spell costs 15% less mana, after the wand and mastery multipliers. */
    public static final float DARK_MANA_MULTIPLIER = 0.85f;

    /** The Morsmordre escort: 5 minutes, 4 to 6 Death Eaters. */
    public static final long ESCORT_DURATION_TICKS = 6_000L;
    public static final int ESCORT_MIN_SIZE = 4;
    public static final int ESCORT_MAX_SIZE = 6;
    /** They appear 3 to 8 blocks from the caster. */
    public static final double ESCORT_SPAWN_MIN_DISTANCE = 3.0D;
    public static final double ESCORT_SPAWN_MAX_DISTANCE = 8.0D;
    /** With no target an escort walks back when 8 blocks away and stops at 4; past 20 it drops any target. */
    public static final double ESCORT_FOLLOW_START = 8.0D;
    public static final double ESCORT_FOLLOW_STOP = 4.0D;
    public static final double ESCORT_LEASH = 20.0D;
    /** Past 32 blocks an escort Apparates to its owner. */
    public static final double ESCORT_CATCH_UP = 32.0D;
    /** The owner, expiry and catch-up checks run once a second per escort member. */
    public static final int ESCORT_CHECK_INTERVAL_TICKS = 20;
    /** A new escort dismisses the caster's old one within this many blocks. */
    public static final double ESCORT_REPLACE_RADIUS = 96.0D;

    private DarkRewardRules() {
    }

    private static int clampTier(int tier) {
        return Math.max(DarkRules.CLEAN, Math.min(DarkRules.LOST, tier));
    }

    /** The mana multiplier for a spell at a tier: 0.85 for a dark spell from Corrupted, else 1. */
    public static float manaMultiplier(String spellId, int tier) {
        return DarkRules.isDark(spellId) && Reward.DARK_MANA_DISCOUNT.appliesAt(tier) ? DARK_MANA_MULTIPLIER : 1.0f;
    }

    /**
     * SpellCosts.manaCost: the base times the wand and mastery multipliers (computed by the caller in that order),
     * times the dark multiplier, rounded, never below 1.
     */
    public static int manaCost(float baseWandMastery, float darkMultiplier) {
        return Math.max(1, Math.round(baseWandMastery * darkMultiplier));
    }

    public static boolean darkMarkBuffs(int tier) {
        return Reward.DARK_MARK_BUFF.appliesAt(tier);
    }

    public static boolean deathEatersNeutral(int tier) {
        return Reward.DEATH_EATERS_NEUTRAL.appliesAt(tier);
    }

    public static boolean morsmordreEscort(int tier) {
        return Reward.MORSMORDRE_ESCORT.appliesAt(tier);
    }

    /** The escort size for a roll in 0..(MAX - MIN); any other roll wraps into it. */
    public static int escortSize(int roll) {
        return ESCORT_MIN_SIZE + Math.floorMod(roll, ESCORT_MAX_SIZE - ESCORT_MIN_SIZE + 1);
    }

    public static long escortUntil(long now) {
        return now + ESCORT_DURATION_TICKS;
    }

    /** Still escorting: before the end, and the end is no more than 5 minutes away (a bad saved value ends it). */
    public static boolean escortActive(long now, long until) {
        return now < until && until - now <= ESCORT_DURATION_TICKS;
    }

    /** One tick in ESCORT_CHECK_INTERVAL_TICKS, spread over the escort by a per-entity phase key (the entity id). */
    public static boolean dueToEscortCheck(long tickCount, long phaseKey) {
        return Math.floorMod(tickCount + phaseKey, (long) ESCORT_CHECK_INTERVAL_TICKS) == 0L;
    }

    public static boolean beyondLeash(double distanceSqr) {
        return distanceSqr > ESCORT_LEASH * ESCORT_LEASH;
    }

    /** Walk back to the owner: always past the leash; otherwise only with no target and more than 8 blocks away. */
    public static boolean escortStartsFollowing(double distanceSqr, boolean hasTarget) {
        return beyondLeash(distanceSqr) || (!hasTarget && distanceSqr > ESCORT_FOLLOW_START * ESCORT_FOLLOW_START);
    }

    /** Keep walking: always past the leash; otherwise until a target appears or within 4 blocks. */
    public static boolean escortKeepsFollowing(double distanceSqr, boolean hasTarget) {
        return beyondLeash(distanceSqr) || (!hasTarget && distanceSqr > ESCORT_FOLLOW_STOP * ESCORT_FOLLOW_STOP);
    }

    public static boolean needsCatchUp(double distanceSqr) {
        return distanceSqr > ESCORT_CATCH_UP * ESCORT_CATCH_UP;
    }

    /** An innocent: a villager, a Student, a House Elf, or any mob MasteryAwards.isPassive calls passive. */
    public static boolean isInnocent(boolean villager, boolean student, boolean houseElf, boolean passive) {
        return villager || student || houseElf || passive;
    }

    /** The Darkness an Avada hit adds on top of the cast: +3 for killing an innocent, else nothing. */
    public static float innocentBonus(boolean killed, boolean innocent) {
        return killed && innocent ? DarkRules.AVADA_INNOCENT_BONUS : 0.0f;
    }

    /**
     * Who answers for a beam's deed: nobody for a reflected beam (the reflector did not choose it), the puppeteer for
     * a puppet beam, nobody for another forced body, else the caster (the Phase 1 cast rule).
     */
    public static DarkRules.Payer deedPayer(boolean reflected, boolean puppetBeam, boolean ownerForced) {
        if (reflected) {
            return DarkRules.Payer.NOBODY;
        }
        return DarkRules.payer(puppetBeam, ownerForced);
    }

    /** Darkness after forging a Horcrux: +30, and never less than the floor of 60 (capped at 100). */
    public static float afterHorcrux(float darkness) {
        return DarkRules.sanitize(Math.max(DarkRules.afterGain(darkness, DarkRules.HORCRUX_BONUS), DarkRules.HORCRUX_FLOOR));
    }

    /** The floor: 60 while the player's Horcrux exists (bound, ritual finished), else none. */
    public static float floor(boolean horcruxBound) {
        return horcruxBound ? DarkRules.HORCRUX_FLOOR : DarkRules.NO_FLOOR;
    }

    /** Hurt before the heal: alive and below full health. */
    public static boolean wasHurt(float health, float maxHealth) {
        return health > 0.0f && health < maxHealth;
    }

    /**
     * A Heal redeems only when a player cast it of their own will (not a puppet, not a forced body), not in creative
     * or spectator, on another living thing (not themselves, not an armour stand) that was hurt before the heal.
     */
    public static boolean healRedeems(boolean selfHeal, boolean armorStand, boolean forced, boolean earns,
                                      boolean targetWasHurt) {
        return !selfHeal && !armorStand && !forced && earns && targetWasHurt;
    }

    /** Darkness after one redemption: -1 through the floor (Phase 1 loss rule). */
    public static float afterRedemption(float darkness, float floor) {
        return DarkRules.afterLoss(darkness, DarkRules.REDEMPTION_AMOUNT, floor);
    }

    /**
     * The once-per-window cap, in ticks of play (the vanilla play-time stat, which counts only online ticks). Open with
     * no earlier redemption, once a full window has passed, or when the stat went backwards (it was reset).
     */
    public static boolean redemptionWindowOpen(boolean hasRecord, int playTicks, int lastRedemptionPlayTicks,
                                               int windowTicks) {
        if (!hasRecord || playTicks < lastRedemptionPlayTicks) {
            return true;
        }
        return (long) playTicks - lastRedemptionPlayTicks >= Math.max(1, windowTicks);
    }
}
```

- [ ] **Step 4: Run the class.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkRewardRulesTest" --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 17 tests, 0 failures.

- [ ] **Step 5: Full build.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 17 tests, all green.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'MSG'
Dark Magic Phase 3: DarkRewardRules, the rewards, deeds and redemption rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
MSG
git add src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java
```

---

### Task 2: The dark mana discount

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkRewards.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellCosts.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java`

**Interfaces.** Consumes: T1 (`DarkRewardRules`), Phase 1 (`DarkMagicData.getTier(ServerPlayer)`, `DarkRules.isDark`, `DarkRules.tier`), Phase 2 (`DarkCosts.tierOf(Entity)`). Produces: the T2 row of the pre-flight table.

- [ ] **Step 0: Pre-flight.** `grep -n "public static int tierOf(Entity entity)" src/main/java/com/hogwartsmagic/spell/dark/DarkCosts.java`. Expected: one line (Dark Magic Phase 2, Task 2). If it prints nothing, Phase 2 is not on the branch: stop and report.

- [ ] **Step 1: The glue class.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkRewards.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.SpellType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Dark Magic rewards (Phase 3), the glue. Every reward reads the tier (DarkCosts.tierOf on the server, so
 * enableDarkMagic off reads Clean); the numbers live in DarkRewardRules. The client computes its own mana cost for the
 * Grimoire line and the "Not enough mana!" check, so it keeps a copy of its own synced tier here (filled by
 * client/DarkMagicClientState), read only for a player on a client level.
 */
public final class DarkRewards {
    private static volatile int clientTier = DarkRules.CLEAN;

    private DarkRewards() {
    }

    /** From client/DarkMagicClientState only: this client player's own tier, as the server last sent it. */
    public static void acceptClientTier(int tier) {
        clientTier = tier;
    }

    /** The tier a mana cost reads: the server's for a server player, the synced copy on the client, else Clean. */
    public static int costTier(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return DarkMagicData.getTier(serverPlayer);
        }
        return player.level().isClientSide() ? clientTier : DarkRules.CLEAN;
    }

    /** 0.85 for a dark spell at Corrupted or Lost, else 1. A spell that is not dark reads no tier at all. */
    public static float manaMultiplier(Player player, SpellType spell) {
        if (!DarkRules.isDark(spell.getId())) {
            return 1.0f;
        }
        return DarkRewardRules.manaMultiplier(spell.getId(), costTier(player));
    }

    /** A Corrupted or Lost player under a Dark Mark gets the Death Eaters' buffs. */
    public static boolean darkMarkBuffs(Entity entity) {
        return DarkRewardRules.darkMarkBuffs(DarkCosts.tierOf(entity));
    }

    /** Death Eaters do not pick a Lost player as a target (the hurt-by goal still answers a strike). */
    public static boolean deathEatersIgnore(Entity entity) {
        return DarkRewardRules.deathEatersNeutral(DarkCosts.tierOf(entity));
    }

    /** A Morsmordre sky raid this player starts calls an escort instead. */
    public static boolean earnsEscort(ServerPlayer player) {
        return DarkRewardRules.morsmordreEscort(DarkMagicData.getTier(player));
    }
}
```

- [ ] **Step 2: The cost.**

In `src/main/java/com/hogwartsmagic/spell/SpellCosts.java`, find:

```java
    public static int manaCost(Player player, ItemStack wand, SpellType spell) {
        return Math.max(1, Math.round(spell.getManaCost()
                * MagicWandItem.getManaCostMultiplier(wand) * masteryMultiplier(player, spell)));
    }
```

and replace it with:

```java
    public static int manaCost(Player player, ItemStack wand, SpellType spell) {
        return com.hogwartsmagic.spell.dark.DarkRewardRules.manaCost(spell.getManaCost()
                * MagicWandItem.getManaCostMultiplier(wand) * masteryMultiplier(player, spell),
                darkMultiplier(player, spell));
    }

    /**
     * Dark Magic (Phase 3): 0.85 for a dark spell at Corrupted or Lost, else 1. Applied after the wand and mastery
     * multipliers. On the client it reads the player's own synced tier, so the Grimoire shows the same cost.
     */
    public static float darkMultiplier(Player player, SpellType spell) {
        return com.hogwartsmagic.spell.dark.DarkRewards.manaMultiplier(player, spell);
    }
```

Do not change `cooldownTicks`, `masteryMultiplier` or `startCooldown`. Every `manaCost` caller (the `castAs` main block and the Patronus roll in `SpellPackets`, `AvadaWindup`, `ReparoSpell`, `ProtegoSpell`, `ModEvents.handleWandUse`, `ModEventsClientHooks`, `SpellCompendiumScreen`) now gets the discounted cost for a dark spell without any change of its own.

- [ ] **Step 3: The Fiendfyre startup.**

In `src/main/java/com/hogwartsmagic/network/SpellPackets.java`, find:

```java
            return Math.max(3.0f, SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                    * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE) * 0.16f);
```

and replace it with:

```java
            return Math.max(3.0f, SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                    * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE) * 0.16f
                    * SpellCosts.darkMultiplier(player, SpellType.FIENDFYRE));
```

- [ ] **Step 4: The Imperio start and upkeep.** (`getImperioStartCost` returns `getImperioUpkeepCost`, so both change.)

In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, find:

```java
        return Math.max(0.75f, (SpellType.IMPERIO.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.IMPERIO)) / 18.0f);
```

and replace it with:

```java
        return Math.max(0.75f, (SpellType.IMPERIO.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.IMPERIO)
                * SpellCosts.darkMultiplier(player, SpellType.IMPERIO)) / 18.0f);
```

- [ ] **Step 5: The Fiendfyre upkeep.**

In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, find:

```java
        return Math.max(1.25f, (SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE)) / 14.0f);
```

and replace it with:

```java
        return Math.max(1.25f, (SpellType.FIENDFYRE.getManaCost() * MagicWandItem.getManaCostMultiplier(wand)
                * SpellCosts.masteryMultiplier(player, SpellType.FIENDFYRE)
                * SpellCosts.darkMultiplier(player, SpellType.FIENDFYRE)) / 14.0f);
```

- [ ] **Step 6: The client copy.**

In `src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java`, find:

```java
    public static void accept(float value) {
        darkness = DarkRules.sanitize(value);
    }

    public static void clear() {
        darkness = DarkRules.MIN_DARKNESS;
    }
```

and replace it with:

```java
    public static void accept(float value) {
        darkness = DarkRules.sanitize(value);
        // Phase 3: SpellCosts (common code) reads this copy for the client's own dark mana discount.
        com.hogwartsmagic.spell.dark.DarkRewards.acceptClientTier(DarkRules.tier(darkness));
    }

    public static void clear() {
        darkness = DarkRules.MIN_DARKNESS;
        com.hogwartsmagic.spell.dark.DarkRewards.acceptClientTier(DarkRules.CLEAN);
    }
```

- [ ] **Step 7: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 17 tests, all green.
  - `grep -c "SpellCosts.darkMultiplier(player, SpellType" src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: `2` and `1`.
  - `grep -c "DarkRewards.acceptClientTier" src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java`. Expected: `2`.
  - `grep -n "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark/DarkRewards.java src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java src/main/java/com/hogwartsmagic/spell/SpellCosts.java`. Expected: nothing.

- [ ] **Step 8: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'MSG'
Dark Magic Phase 3: dark spells cost 15 percent less mana from Corrupted

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
MSG
git add src/main/java/com/hogwartsmagic/spell/dark/DarkRewards.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkRewards.java src/main/java/com/hogwartsmagic/spell/SpellCosts.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java
```

---

### Task 3: The Dark Mark buff and neutral Death Eaters

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`

**Interfaces.** Consumes: T2 (`DarkRewards.darkMarkBuffs(Entity)`, `DarkRewards.deathEatersIgnore(Entity)`). Produces: `WizardEntity.ignoresPlayer(LivingEntity)` (protected).

- [ ] **Step 1: The Mark buffs a dark player.** In `applyBuffs`:

In `src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java`, find:

```java
        List<WizardEntity> wizards = this.level().getEntitiesOfClass(WizardEntity.class, area);
        for (WizardEntity wizard : wizards) {
            wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 200, 1));
            wizard.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
        }
    }
```

and replace it with:

```java
        List<WizardEntity> wizards = this.level().getEntitiesOfClass(WizardEntity.class, area);
        for (WizardEntity wizard : wizards) {
            giveMarkBuffs(wizard);
        }
        // Dark Magic: the Mark answers a Corrupted or Lost wizard as it answers a Death Eater.
        List<net.minecraft.world.entity.player.Player> darkPlayers = this.level().getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, area,
                player -> !player.isSpectator() && com.hogwartsmagic.spell.dark.DarkRewards.darkMarkBuffs(player));
        for (net.minecraft.world.entity.player.Player player : darkPlayers) {
            giveMarkBuffs(player);
        }
    }

    /** Strength II and Regeneration I for 10 seconds, refreshed every 5. */
    private static void giveMarkBuffs(LivingEntity entity) {
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 200, 1));
        entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 0));
    }
```

- [ ] **Step 2: The player goal skips a Lost wizard.** Inside `WizardEntity.registerGoals`:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
```

and replace it with:

```java
        // Dark Magic: a Lost wizard is not chosen (the hurt-by goal above still answers a strike).
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                target -> !this.ignoresPlayer(target)));
```

(`VoldemortEntity` has the same line in its own `registerGoals`; do not touch that file.)
- [ ] **Step 3: The predicate.**

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
    @Override
    public boolean canAttack(LivingEntity target) {
        // Death Eaters never attack each other!
```

and replace it with:

```java
    /** Dark Magic: players the plain player goal never picks (a Lost wizard). */
    protected boolean ignoresPlayer(LivingEntity target) {
        return com.hogwartsmagic.spell.dark.DarkRewards.deathEatersIgnore(target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        // Death Eaters never attack each other!
```

- [ ] **Step 4: No base raids on a Lost wizard.** Inside `tryStartDeathEaterBaseRaid`:

In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, find:

```java
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (isPlayerNearActiveWizardRaid(level, player)) {
            return;
        }
```

and replace it with:

```java
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        // Dark Magic: Death Eaters do not raid a Lost wizard's home.
        if (com.hogwartsmagic.spell.dark.DarkRewards.deathEatersIgnore(player)) {
            return;
        }
        if (isPlayerNearActiveWizardRaid(level, player)) {
            return;
        }
```

- [ ] **Step 5: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 17 tests, all green.
  - `grep -c "giveMarkBuffs(" src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java`. Expected: `3`.
  - `grep -c "ignoresPlayer(" src/main/java/com/hogwartsmagic/entity/WizardEntity.java`. Expected: `2`.
  - `grep -n "addGoal(1, new HurtByTargetGoal(this))" src/main/java/com/hogwartsmagic/entity/WizardEntity.java src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java`. Expected: one line each (unchanged).
  - `git diff --stat -- src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java`. Expected: nothing.
  - `grep -c "DarkRewards.deathEatersIgnore(player)" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: `1`.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'MSG'
Dark Magic Phase 3: the Dark Mark buffs dark wizards, Death Eaters leave the Lost alone

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
MSG
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java src/main/java/com/hogwartsmagic/entity/WizardEntity.java src/main/java/com/hogwartsmagic/event/ModEvents.java
```

---

### Task 4: The Morsmordre escort

**Files:**
- Create: `src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: T1 (escort numbers), T2 (`DarkRewards.earnsEscort`), T3 (`WizardEntity.ignoresPlayer`). Produces: the T4 row of the pre-flight table.

- [ ] **Step 1: The escort class.** Create `src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java`:

```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.dark.DarkRewardRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Dark Magic Phase 3: the Morsmordre escort. A Lost caster's sky raid calls 4 to 6 Death Eaters to the caster's side
 * for 5 minutes instead of a raid: no raid bar, no raid record. They follow (a 20-block leash), fight whatever hurts
 * their owner or whatever the owner strikes, Apparate to the owner past 32 blocks, and Disapparate when the time is
 * up, the owner leaves the dimension, logs out or dies, or a new escort replaces them. One the owner strikes breaks
 * off and fights back as any Death Eater would. The numbers live in DarkRewardRules.
 */
public final class DeathEaterEscort {
    private static final String KEY_ARRIVES = "gui.hogwartsmagic.dark.escort_arrives";

    private DeathEaterEscort() {
    }

    /** Summons the escort around the owner. Returns how many came (0 means nothing happened). */
    public static int summon(ServerLevel level, ServerPlayer owner) {
        for (WizardEntity old : level.getEntitiesOfClass(WizardEntity.class,
                owner.getBoundingBox().inflate(DarkRewardRules.ESCORT_REPLACE_RADIUS),
                wizard -> owner.getUUID().equals(wizard.getEscortOwnerId()))) {
            dismiss(old);
        }
        long until = DarkRewardRules.escortUntil(level.getGameTime());
        int size = DarkRewardRules.escortSize(owner.getRandom().nextInt(
                DarkRewardRules.ESCORT_MAX_SIZE - DarkRewardRules.ESCORT_MIN_SIZE + 1));
        int came = 0;
        for (int i = 0; i < size; i++) {
            WizardEntity wizard = ModEntities.WIZARD.get().create(level);
            if (wizard == null) {
                continue;
            }
            BlockPos pos = landingNear(level, owner);
            wizard.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            wizard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            wizard.setEscort(owner.getUUID(), until);
            wizard.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
            if (level.addFreshEntity(wizard)) {
                came++;
                puff(level, wizard);
            }
        }
        if (came > 0) {
            owner.sendSystemMessage(Component.translatable(KEY_ARRIVES).withStyle(ChatFormatting.DARK_GREEN));
        }
        return came;
    }

    /** From WizardEntity.tick (server) while it is an escort. */
    public static void tick(WizardEntity wizard) {
        UUID ownerId = wizard.getEscortOwnerId();
        if (ownerId == null) {
            return;
        }
        LivingEntity hurtBy = wizard.getLastHurtByMob();
        if (hurtBy != null && ownerId.equals(hurtBy.getUUID())) {
            wizard.releaseEscort(); // struck by its owner: an ordinary Death Eater now, and the hurt-by goal answers
            return;
        }
        if (!DarkRewardRules.dueToEscortCheck(wizard.tickCount, wizard.getId())) {
            return;
        }
        ServerPlayer owner = wizard.getEscortOwner();
        if (owner == null || !DarkRewardRules.escortActive(wizard.level().getGameTime(), wizard.getEscortUntil())) {
            dismiss(wizard);
            return;
        }
        if (DarkRewardRules.needsCatchUp(wizard.distanceToSqr(owner)) && wizard.level() instanceof ServerLevel level) {
            puff(level, wizard);
            wizard.stopRiding();
            BlockPos pos = landingNear(level, owner);
            wizard.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            wizard.getNavigation().stop();
            puff(level, wizard);
        }
    }

    /** Disapparates: smoke, a crack, gone. Drops nothing. */
    public static void dismiss(WizardEntity wizard) {
        if (wizard.level() instanceof ServerLevel level) {
            puff(level, wizard);
        }
        wizard.discard();
    }

    private static void puff(ServerLevel level, WizardEntity wizard) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, wizard.getX(), wizard.getY() + 1.0D, wizard.getZ(),
                20, 0.3D, 0.6D, 0.3D, 0.02D);
        level.playSound(null, wizard.getX(), wizard.getY(), wizard.getZ(), ModSounds.HOUSE_ELF_CRACK.get(),
                SoundSource.HOSTILE, 0.8F, 0.8F);
    }

    /**
     * A spot 3 to 8 blocks from the owner on the surface. If the surface there is more than 6 blocks above the owner
     * (the owner is underground), the owner's own block instead. Near the owner, so the chunk is loaded.
     */
    private static BlockPos landingNear(ServerLevel level, ServerPlayer owner) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = DarkRewardRules.ESCORT_SPAWN_MIN_DISTANCE + level.random.nextDouble()
                * (DarkRewardRules.ESCORT_SPAWN_MAX_DISTANCE - DarkRewardRules.ESCORT_SPAWN_MIN_DISTANCE);
        BlockPos column = BlockPos.containing(owner.getX() + Math.cos(angle) * distance, owner.getY(),
                owner.getZ() + Math.sin(angle) * distance);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        return surface.getY() > owner.getBlockY() + 6 ? owner.blockPosition() : surface;
    }

    /** Walks back to the owner: past the leash always (dropping its target), else when idle and 8 blocks away. */
    public static final class FollowOwnerGoal extends Goal {
        private final WizardEntity wizard;
        @Nullable
        private ServerPlayer owner;
        private int repathTicks;

        public FollowOwnerGoal(WizardEntity wizard) {
            this.wizard = wizard;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.wizard.isEscort()) {
                return false;
            }
            ServerPlayer candidate = this.wizard.getEscortOwner();
            if (candidate == null || !DarkRewardRules.escortStartsFollowing(this.wizard.distanceToSqr(candidate),
                    this.wizard.getTarget() != null)) {
                return false;
            }
            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null && this.wizard.isEscort() && this.owner.isAlive()
                    && this.owner.level() == this.wizard.level()
                    && DarkRewardRules.escortKeepsFollowing(this.wizard.distanceToSqr(this.owner),
                    this.wizard.getTarget() != null);
        }

        @Override
        public void start() {
            this.repathTicks = 0;
            if (this.owner != null && DarkRewardRules.beyondLeash(this.wizard.distanceToSqr(this.owner))) {
                this.wizard.setTarget(null);
            }
        }

        @Override
        public void stop() {
            this.owner = null;
            this.wizard.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            this.wizard.getLookControl().setLookAt(this.owner, 10.0F, (float) this.wizard.getMaxHeadXRot());
            if (--this.repathTicks <= 0) {
                this.repathTicks = 10;
                this.wizard.getNavigation().moveTo(this.owner, 1.15D);
            }
        }
    }

    /** Targets whatever last hurt the owner, or whatever the owner last struck (never another Death Eater). */
    public static final class DefendOwnerGoal extends TargetGoal {
        private final WizardEntity wizard;
        @Nullable
        private LivingEntity pick;
        private boolean primed;
        private int seenHurtBy;
        private int seenStruck;

        public DefendOwnerGoal(WizardEntity wizard) {
            super(wizard, false);
            this.wizard = wizard;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!this.wizard.isEscort()) {
                return false;
            }
            ServerPlayer owner = this.wizard.getEscortOwner();
            if (owner == null) {
                return false;
            }
            if (!this.primed) {
                // What the owner fought before the escort came is not its business.
                this.primed = true;
                this.seenHurtBy = owner.getLastHurtByMobTimestamp();
                this.seenStruck = owner.getLastHurtMobTimestamp();
                return false;
            }
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && attacker != this.wizard && owner.getLastHurtByMobTimestamp() != this.seenHurtBy
                    && this.canAttack(attacker, TargetingConditions.DEFAULT)) {
                this.pick = attacker;
                return true;
            }
            LivingEntity struck = owner.getLastHurtMob();
            if (struck != null && struck != owner && owner.getLastHurtMobTimestamp() != this.seenStruck
                    && this.canAttack(struck, TargetingConditions.DEFAULT)) {
                this.pick = struck;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            ServerPlayer owner = this.wizard.getEscortOwner();
            if (owner != null) {
                this.seenHurtBy = owner.getLastHurtByMobTimestamp();
                this.seenStruck = owner.getLastHurtMobTimestamp();
            }
            this.mob.setTarget(this.pick);
            super.start();
        }
    }
}
```

- [ ] **Step 2: Escort state.**

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
    private long lastHitTime = 0; // Track when wizard was last hit for flight escape
```

and replace it with:

```java
    private long lastHitTime = 0; // Track when wizard was last hit for flight escape
    /** Dark Magic: the Lost player this Death Eater escorts (a Morsmordre escort), or null. */
    @javax.annotation.Nullable
    private java.util.UUID escortOwner;
    /** Game time the escort ends. */
    private long escortUntil;
```

- [ ] **Step 3: The escort tick.** In `WizardEntity.tick`:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
    public void tick() {
        super.tick();
```

and replace it with:

```java
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.escortOwner != null) {
            DeathEaterEscort.tick(this); // Dark Magic: follow, catch up, and leave after 5 minutes
        }
```

- [ ] **Step 4: The follow goal.** In `WizardEntity.registerGoals`:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
        this.goalSelector.addGoal(1, new WizardRaidLeaderGoal(this));
```

and replace it with:

```java
        this.goalSelector.addGoal(1, new WizardRaidLeaderGoal(this));
        this.goalSelector.addGoal(1, new DeathEaterEscort.FollowOwnerGoal(this)); // Dark Magic: escorts only
```

- [ ] **Step 5: The defend goal.** In the same method:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
```

and replace it with:

```java
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new DeathEaterEscort.DefendOwnerGoal(this)); // Dark Magic: escorts only
```

(Both goals return at once for a Death Eater that is not an escort.)
- [ ] **Step 6: The escort API.**

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
    public boolean isRaidLeader() {
        return this.entityData.get(DATA_IS_RAID_LEADER);
    }
```

and replace it with:

```java
    public boolean isRaidLeader() {
        return this.entityData.get(DATA_IS_RAID_LEADER);
    }

    /** Dark Magic: makes this Death Eater a Morsmordre escort of the owner until the given game time. */
    public void setEscort(java.util.UUID owner, long until) {
        this.escortOwner = owner;
        this.escortUntil = until;
    }

    /** Dark Magic: an ordinary Death Eater again (struck by its owner). */
    public void releaseEscort() {
        this.escortOwner = null;
        this.escortUntil = 0L;
    }

    public boolean isEscort() {
        return this.escortOwner != null;
    }

    @javax.annotation.Nullable
    public java.util.UUID getEscortOwnerId() {
        return this.escortOwner;
    }

    public long getEscortUntil() {
        return this.escortUntil;
    }

    /** The owner when they are in this level, alive and not spectating; else null (the escort then leaves). */
    @javax.annotation.Nullable
    public net.minecraft.server.level.ServerPlayer getEscortOwner() {
        if (this.escortOwner == null) {
            return null;
        }
        Player player = this.level().getPlayerByUUID(this.escortOwner);
        return player instanceof net.minecraft.server.level.ServerPlayer owner && owner.isAlive() && !owner.isSpectator()
                ? owner : null;
    }

    public boolean isEscortOwner(LivingEntity entity) {
        return this.escortOwner != null && this.escortOwner.equals(entity.getUUID());
    }
```

- [ ] **Step 7: Save the escort.** In `addAdditionalSaveData`:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
        p_33353_.putBoolean("Disarmed", this.isDisarmed());
```

and replace it with:

```java
        p_33353_.putBoolean("Disarmed", this.isDisarmed());
        if (this.escortOwner != null) {
            p_33353_.putUUID("EscortOwner", this.escortOwner);
            p_33353_.putLong("EscortUntil", this.escortUntil);
        }
```

- [ ] **Step 8: Load the escort.** In `readAdditionalSaveData`:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
        if (tag.contains("Disarmed")) this.setDisarmed(tag.getBoolean("Disarmed"));
```

and replace it with:

```java
        if (tag.contains("Disarmed")) this.setDisarmed(tag.getBoolean("Disarmed"));
        if (tag.hasUUID("EscortOwner")) this.setEscort(tag.getUUID("EscortOwner"), tag.getLong("EscortUntil"));
```

- [ ] **Step 9: Never target the owner.** Find the Task 3 method and widen it:

In `src/main/java/com/hogwartsmagic/entity/WizardEntity.java`, find:

```java
    /** Dark Magic: players the plain player goal never picks (a Lost wizard). */
    protected boolean ignoresPlayer(LivingEntity target) {
        return com.hogwartsmagic.spell.dark.DarkRewards.deathEatersIgnore(target);
    }
```

and replace it with:

```java
    /** Dark Magic: players the plain player goal never picks (a Lost wizard, and the owner of an escort). */
    protected boolean ignoresPlayer(LivingEntity target) {
        return com.hogwartsmagic.spell.dark.DarkRewards.deathEatersIgnore(target) || this.isEscortOwner(target);
    }
```

- [ ] **Step 10: An escort instead of a sky raid.** In `SpellBeamEntity.tryStartMorsmordreSkyRaid`, after the Dark Mark and the lightning:

In `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`, find:

```java
        java.util.List<WizardEntity> raidMembers = new java.util.ArrayList<>();
        int count = 5 + this.random.nextInt(5);
```

and replace it with:

```java
        // Dark Magic: a Lost caster's Mark calls an escort to their side instead of a raid near them.
        if (!this.reflected && this.puppeteer == null && getOwner() instanceof ServerPlayer escortOwner
                && !com.hogwartsmagic.spell.mind.PossessionManager.isForcedCaster(escortOwner)
                && com.hogwartsmagic.spell.dark.DarkRewards.earnsEscort(escortOwner)) {
            return DeathEaterEscort.summon(serverLevel, escortOwner) > 0;
        }

        java.util.List<WizardEntity> raidMembers = new java.util.ArrayList<>();
        int count = 5 + this.random.nextInt(5);
```

(`DeathEaterEscort` is in the same package as `SpellBeamEntity`; `ServerPlayer` is already imported there.)
- [ ] **Step 11: The string.**

In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find:

```json
  "gui.hogwartsmagic.dark.tier_down": "Your soul lightens. You are now %s.",
```

and replace it with:

```json
  "gui.hogwartsmagic.dark.tier_down": "Your soul lightens. You are now %s.",
  "gui.hogwartsmagic.dark.escort_arrives": "Death Eaters answer the Mark. They will guard you for five minutes.",
```

- [ ] **Step 12: Build, greps and boot.**
  - `cd /home/user/minecraft_mods && python3 -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json')); print('json ok')"`. Expected: `json ok`.
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 17 tests, all green.
  - `grep -c "DeathEaterEscort\." src/main/java/com/hogwartsmagic/entity/WizardEntity.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`. Expected: `3` and `1`.
  - `grep -c "startWizardRaid" src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java`. Expected: `0`.
  - Run the boot block (Global Constraints). Expected: `Done (`, three `Summoned new` lines (or the stdin note), no `Exception` or `ERROR` from `hogwartsmagic`, no server java process left.

- [ ] **Step 13: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'MSG'
Dark Magic Phase 3: Morsmordre at Lost calls a Death Eater escort

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
MSG
git add src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java src/main/java/com/hogwartsmagic/entity/WizardEntity.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 5: The deeds and redemption

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/support/HealSpell.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: T1 (`DarkRewardRules` deed and redemption rules), Phase 1 (`DarkMagicData.add/getStored/floor`, `DarkRules`), `HorcruxData.hasActiveHorcrux(Player)`, `HorcruxData.isRitualActive(Player)`, `MindControl.hitPuppeteer()`, `PossessionManager.isForcedCaster(ServerPlayer)`, `Config.getDarkMagicRedemptionTicks()`. Produces: the T5 row of the pre-flight table.

- [ ] **Step 1: The deeds class.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.entity.HouseElfEntity;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.mind.PossessionManager;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;

import javax.annotation.Nullable;

/**
 * Dark Magic deeds and redemption (Phase 3): an Avada kill of an innocent (+3), forging a Horcrux (+30, at least 60),
 * and a Heal of another hurt living thing (-1, at most once per darkMagicRedemptionTicks of play). Server only. Every
 * change goes through DarkMagicData.add, so it passes the floor and the clamp, syncs, and announces a tier change.
 * Nothing here runs with enableDarkMagic off, for a creative or spectator player, or for an NPC.
 */
public final class DarkDeeds {
    /** The play-time stat (online ticks) at the last redemption. */
    public static final String KEY_LAST_REDEMPTION = "DarkMagicLastRedemption";
    private static final String KEY_INNOCENT = "gui.hogwartsmagic.dark.innocent";
    private static final String KEY_REDEEMED = "gui.hogwartsmagic.dark.redeemed";

    private DarkDeeds() {
    }

    /**
     * From SpellBeamEntity after an Avada hit on a living thing. The payer is the beam's caster, the puppeteer of a
     * puppet beam, or null (a reflected beam, another forced body); it is the same rule as a paid cast.
     */
    public static void onAvadaHit(LivingEntity victim, @Nullable ServerPlayer payer) {
        if (payer == null || !Config.isDarkMagicEnabled() || !DarkRules.earns(payer.isCreative(), payer.isSpectator())) {
            return;
        }
        boolean innocent = DarkRewardRules.isInnocent(victim instanceof Villager, victim instanceof StudentEntity,
                victim instanceof HouseElfEntity, MasteryAwards.isPassive(victim));
        float bonus = DarkRewardRules.innocentBonus(!victim.isAlive(), innocent);
        if (!(bonus > 0.0f)) {
            return;
        }
        DarkMagicData.add(payer, bonus);
        payer.displayClientMessage(Component.translatable(KEY_INNOCENT).withStyle(ChatFormatting.DARK_GREEN), true);
    }

    /** From ModEvents.tickHorcruxRitual when the Horcrux is forged: +30, and at least the floor of 60. */
    public static void onHorcruxForged(ServerPlayer player) {
        if (!Config.isDarkMagicEnabled() || !DarkRules.earns(player.isCreative(), player.isSpectator())) {
            return;
        }
        float stored = DarkMagicData.getStored(player);
        float after = DarkRewardRules.afterHorcrux(stored);
        if (after > stored) {
            DarkMagicData.add(player, after - stored);
        }
    }

    /**
     * From HealSpell.apply after the heal. wasHurt was read before any cleanse or heal. Redeems 1 point when a player
     * healed another hurt living thing of their own will, at most once per window of play; a heal that would lighten
     * nothing (Clean, or at the Horcrux floor) does not spend the window.
     */
    public static void onHeal(LivingEntity target, @Nullable Entity caster, boolean wasHurt) {
        if (!(caster instanceof ServerPlayer healer) || !Config.isDarkMagicEnabled()) {
            return;
        }
        boolean forced = MindControl.hitPuppeteer() != null || PossessionManager.isForcedCaster(healer);
        if (!DarkRewardRules.healRedeems(target == healer, target instanceof ArmorStand, forced,
                DarkRules.earns(healer.isCreative(), healer.isSpectator()), wasHurt)) {
            return;
        }
        float stored = DarkMagicData.getStored(healer);
        if (!(DarkRewardRules.afterRedemption(stored, DarkMagicData.floor(healer)) < stored)) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(healer);
        int played = healer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        if (!DarkRewardRules.redemptionWindowOpen(modData.contains(KEY_LAST_REDEMPTION), played,
                modData.getInt(KEY_LAST_REDEMPTION), Config.getDarkMagicRedemptionTicks())) {
            return;
        }
        modData.putInt(KEY_LAST_REDEMPTION, played);
        PlayerMagicData.saveModData(healer, modData);
        DarkMagicData.add(healer, -DarkRules.REDEMPTION_AMOUNT);
        healer.displayClientMessage(Component.translatable(KEY_REDEEMED).withStyle(ChatFormatting.GRAY), true);
    }
}
```

- [ ] **Step 2: The floor.**

In `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`, find:

```java
    /** Losses stop here. Phase 1: none. Phase 3 returns DarkRules.HORCRUX_FLOOR while the player's Horcrux exists. */
    public static float floor(ServerPlayer player) {
        return DarkRules.NO_FLOOR;
    }
```

and replace it with:

```java
    /**
     * Losses stop here: DarkRules.HORCRUX_FLOOR while the player's Horcrux exists (bound and forged), else none. The
     * binding is cleared when the Horcrux shatters (rebirth) or is reconciled away (destroyed), so the floor goes too.
     */
    public static float floor(ServerPlayer player) {
        return DarkRewardRules.floor(com.hogwartsmagic.util.HorcruxData.hasActiveHorcrux(player)
                && !com.hogwartsmagic.util.HorcruxData.isRitualActive(player));
    }
```

- [ ] **Step 3: `isPassive` public.** In `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` change only the modifier (the body and its javadoc stay):

In `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`, find:

```java
    private static boolean isPassive(LivingEntity target) {
```

and replace it with:

```java
    public static boolean isPassive(LivingEntity target) {
```

- [ ] **Step 4: The deed payer.** In `SpellBeamEntity`:

In `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`, find:

```java
    private void tryPrimeHorcruxFromAvada(LivingEntity victim) {
```

and replace it with:

```java
    /**
     * Dark Magic: who answers for this beam's deed. The caster; the puppeteer of a puppet beam (if online); nobody
     * for a reflected beam or another forced body. The same rule as a paid cast (DarkRules.payer).
     */
    @javax.annotation.Nullable
    private ServerPlayer darkDeedPayer() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity owner = getOwner();
        boolean ownerForced = owner instanceof ServerPlayer body
                && com.hogwartsmagic.spell.mind.PossessionManager.isForcedCaster(body);
        com.hogwartsmagic.spell.dark.DarkRules.Payer who =
                com.hogwartsmagic.spell.dark.DarkRewardRules.deedPayer(this.reflected, this.puppeteer != null, ownerForced);
        if (who == com.hogwartsmagic.spell.dark.DarkRules.Payer.PUPPETEER) {
            return serverLevel.getServer().getPlayerList().getPlayer(this.puppeteer);
        }
        return who == com.hogwartsmagic.spell.dark.DarkRules.Payer.BODY && owner instanceof ServerPlayer caster
                ? caster : null;
    }

    private void tryPrimeHorcruxFromAvada(LivingEntity victim) {
```

- [ ] **Step 5: The innocent hook.** In `SpellBeamEntity.onHitInner`, the Avada branch of the normal living hit:

In `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java`, find:

```java
                            } else if (spell == SpellType.AVADA_KEDAVRA) {
                                tryPrimeHorcruxFromAvada(living);
                            }
```

and replace it with:

```java
                            } else if (spell == SpellType.AVADA_KEDAVRA) {
                                tryPrimeHorcruxFromAvada(living);
                                // Dark Magic: killing an innocent stains the soul (+3).
                                com.hogwartsmagic.spell.dark.DarkDeeds.onAvadaHit(living, darkDeedPayer());
                            }
```

- [ ] **Step 6: The Horcrux bonus.** In `ModEvents.tickHorcruxRitual`, at the forging:

In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, find:

```java
        horcrux.completeBinding(level, player);
        HorcruxData.completeRitual(player);
        HorcruxData.bindToHorcrux(player, level, pos);
```

and replace it with:

```java
        horcrux.completeBinding(level, player);
        HorcruxData.completeRitual(player);
        HorcruxData.bindToHorcrux(player, level, pos);
        // Dark Magic: a torn soul (+30, and never below the floor of 60 while the Horcrux exists).
        com.hogwartsmagic.spell.dark.DarkDeeds.onHorcruxForged(player);
```

- [ ] **Step 7: "Was hurt".** In `HealSpell.apply`:

In `src/main/java/com/hogwartsmagic/spell/support/HealSpell.java`, find:

```java
        int rank = DisableSupport.casterRank(caster, SpellType.HEAL);
        if (HealRules.curesBleeding(rank)) {
```

and replace it with:

```java
        // Dark Magic redemption: was the target hurt before any cleanse or heal?
        boolean wasHurt = com.hogwartsmagic.spell.dark.DarkRewardRules.wasHurt(target.getHealth(), target.getMaxHealth());
        int rank = DisableSupport.casterRank(caster, SpellType.HEAL);
        if (HealRules.curesBleeding(rank)) {
```

- [ ] **Step 8: The redemption call.** In the same method:

In `src/main/java/com/hogwartsmagic/spell/support/HealSpell.java`, find:

```java
        if (regen > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));
        }
```

and replace it with:

```java
        if (regen > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));
        }
        // Dark Magic: healing another hurt living thing lightens the healer (at most once per window of play).
        com.hogwartsmagic.spell.dark.DarkDeeds.onHeal(target, caster, wasHurt);
```

- [ ] **Step 9: The strings.**

In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find:

```json
  "gui.hogwartsmagic.dark.tier_down": "Your soul lightens. You are now %s.",
```

and replace it with:

```json
  "gui.hogwartsmagic.dark.tier_down": "Your soul lightens. You are now %s.",
  "gui.hogwartsmagic.dark.innocent": "An innocent dies by your hand. Your soul darkens.",
  "gui.hogwartsmagic.dark.redeemed": "Healing another eases the darkness in you.",
```

- [ ] **Step 10: Build and greps.**
  - `cd /home/user/minecraft_mods && python3 -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json')); print('json ok')"`. Expected: `json ok`.
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 17 tests, all green.
  - `grep -c "DarkDeeds\." src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/spell/support/HealSpell.java`. Expected: `1`, `1`, `1`.
  - `grep -c "public static boolean isPassive(LivingEntity target)" src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`. Expected: `1`.
  - `grep -c "DarkRules.NO_FLOOR" src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`. Expected: `0`.
  - `grep -n "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java`. Expected: nothing.

- [ ] **Step 11: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'MSG'
Dark Magic Phase 3: innocent kills and the Horcrux darken, Heal redeems

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
MSG
git add src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/spell/support/HealSpell.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 6: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 17 tests (plus any tests another phase committed since Task 1, which the ledger names), all green.
- [ ] **Step 2:** Safety greps (`<T1>` is the commit recorded in Task 1 Step 0):
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java src/main/java/com/hogwartsmagic/spell/SpellCosts.java`. Expected: nothing.
  - `git diff <T1> -- src/main/java/com/hogwartsmagic/spell/dark src/main/java/com/hogwartsmagic/entity/DeathEaterEscort.java src/main/java/com/hogwartsmagic/entity/WizardEntity.java src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java src/main/java/com/hogwartsmagic/spell/support/HealSpell.java | grep "^+" | grep -n "switch\|SubscribeEvent\|broadcastEntityEvent"`. Expected: nothing.
  - `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: the line recorded in Task 1 Step 0 (unless the ledger records another phase changing it).
  - This phase changed no config or registration file: `git log --format=%s <T1>..HEAD -- src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/entity/VoldemortEntity.java | grep -c "Dark Magic Phase 3"`. Expected: `0`.
  - `cd /home/user/minecraft_mods && python3 -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json')); print('json ok')"`. Expected: `json ok`.
  - `git status --short`. Expected: clean except untracked `run/` output (and other agents' in-flight files, which are not this phase's).
- [ ] **Step 3:** Run the boot block (Global Constraints). Expected: `Done (`; three `Summoned new` lines (or the stdin note); no `Exception` or `ERROR` from `hogwartsmagic`; no server java process left afterwards.
- [ ] **Step 4:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 7: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 6. A survival world, normal difficulty, the dark spells and Heal learned. `/darkmagic set` (Phase 1) sets the value; tiers: 0 Clean, 20 Shadowed, 40 Tainted, 60 Corrupted, 80 Lost. Rows 13 and 20 need a second player (B). For row 21 set `darkMagicRedemptionTicks = 200` in `config/hogwartsmagic-common.toml` (it reloads while running).

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | `/darkmagic set @s 45` | Open the Grimoire on Avada Kedavra, Morsmordre, Heal; then `/darkmagic set @s 65` and look again | 100, 90, Heal unchanged at 45; 85, 77, Heal unchanged at 65 |
| 2 | At 65, spend mana (cheap spells) until 86 to 99 is left | Cast Avada Kedavra | It casts (no "Not enough mana!"), and 85 mana is spent |
| 3 | At 65 then 45 | Hold Fiendfyre 10 s each time | Less mana drained at 65 (about 85%) |
| 4 | At 65, `/summon hogwartsmagic:dark_mark ~ ~20 ~` | Wait 10 s, open the inventory | Strength II and Regeneration; at `/darkmagic set @s 59` they are not refreshed; in spectator none |
| 5 | At 80, `/summon hogwartsmagic:wizard` three times around you (or walk into a Death Eater camp) | Walk among them for a minute | Nobody attacks you |
| 6 | Row 5 | Hit one Death Eater | That one fights you; the others stay calm |
| 7 | Row 5, `/darkmagic set @s 79` | Summon a new Death Eater in sight | It attacks you |
| 8 | At 80 | Summon Voldemort (his usual summoning) | He fights you, and his Death Eaters fight you |
| 9 | At 80, a base (wards, Horcrux, Floo), `deathEaterBaseRaidChance = 1.0`, `deathEaterBaseRaidCooldownTicks = 20` | Stand at the base a few minutes | No Death Eater base raid; at 79 one comes |
| 10 | At 80, open sky | Cast Morsmordre straight up | The Dark Mark and lightning; 4 to 6 Death Eaters appear around you in smoke; the line "Death Eaters answer the Mark..."; no raid bar |
| 11 | Row 10 | Walk 30 blocks, then fly a broom 60 blocks away | They follow on foot; past 32 blocks they Apparate next to you |
| 12 | Row 10, a zombie at night | Let the zombie hit you; then hit a cow | The escort kills the zombie; then the cow |
| 13 | Row 10, B at 0 nearby | Watch | The escort attacks B (an ordinary Death Eater) but never you |
| 14 | Row 10 | Hit one escort member | That one turns on you; the others keep escorting |
| 15 | Row 10 | Wait 5 minutes | They vanish with smoke and a crack; nothing dropped |
| 16 | Row 10 | Log out and back in; or go through a Nether portal | The escort is gone |
| 17 | Row 10, wait out the Morsmordre cooldown | Cast it again | The old escort vanishes, a new one of 4 to 6 comes (never more than 6 at once) |
| 18 | At 79, open sky | Cast Morsmordre straight up | The old behaviour: a Death Eater raid with its bar |
| 19 | At 30 | Avada a villager; `/darkmagic get @s` | 39 (cast 6 + innocent 3), and the action-bar line; Avada a zombie from 30: 36; a cow, a Student, a House Elf: +9 each; a player: +6 |
| 20 | You have Master Imperio and possess B; you at 30, B at 0 | Make B cast Avada at a villager | You 39, B 0 |
| 21 | At 85, `darkMagicRedemptionTicks = 200` | Hit a villager, Heal it; Heal it again at once; wait 10 s, hit and Heal it again | 84 with the action-bar line; still 84; 83 |
| 22 | At 85 | Heal a villager at full health; Heal yourself (left click); creative Heal of a hurt villager | No change each time |
| 23 | At 0 | Heal a hurt villager, then `/darkmagic set @s 85` and Heal another hurt villager at once | No change the first time; 84 the second (the window was not spent at 0) |
| 24 | At 10, make a Horcrux (an Avada kill over the altar); in another world at 45 | `/darkmagic get @s` after "Your Horcrux is forged" | 60 (the cast makes 16, forging +30 makes 46, lifted to the floor of 60); from 45: 81 (51 + 30) |
| 25 | Row 24 | `/darkmagic add @s -30` | 60 (the floor), and `get` shows ", floor 60" |
| 26 | Row 24 | Let B break the altar (or die and be reborn through it) | `get` shows no floor; `/darkmagic add @s -30` goes to 30 |
| 27 | `enableDarkMagic = false` (reloads), at 85 | Check rows 1, 4, 5, 10, 19, 21 | No discount, no buff, Death Eaters attack, a normal raid, no +3, no redemption. Turning it back on restores them |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Phase 3.**
  - Bullet 1, the discount in `SpellCosts.manaCost` after the wand and mastery multipliers: Task 1 (`manaMultiplier`, `manaCost`; tests `onlyDarkSpellsGetTheDiscountAndOnlyFromCorrupted`, `discountedCostsRoundAfterWandAndMastery`), Task 2; rulings 2-3; rows 1-3. The Grimoire line reads the same function with the client copy of the tier (ruling 3).
  - Bullet 2, the Dark Mark buff for Corrupted players: Task 3 Step 1; ruling 4; test `rewardTiersMatchTheSpec`; row 4.
  - Bullet 3, Death Eaters neutral to Lost (predicate on the player goal, hurt-by unchanged, Voldemort hostile) and the escort: Task 3 Steps 2-4, Task 4; rulings 5-9; tests `escortLastsFiveMinutesAndHasFourToSix`, `escortChecksOncePerSecond`, `escortFollowsWithALeash`; rows 5-18.
  - Bullet 4, the Avada innocent bonus in `onHitInner` and the Horcrux +30 and floor: Task 5 Steps 1-6; rulings 10-13; tests `innocentsAreVillagersStudentsElvesAndPassiveMobs`, `innocentBonusOnlyForAKill`, `deedsChargeLikeCasts`, `horcruxAddsThirtyAndLiftsToTheFloor`, `floorWhileTheHorcruxExists`; rows 19-20, 24-26.
  - Bullet 5, redemption through Heal: Task 5 Steps 1, 7-8; rulings 14-15; tests `redemptionLightensByOneThroughTheFloor`, `onlyAWillingHealOfAnotherHurtThingRedeems`, `redemptionAtMostOncePerWindowOfPlay`, `redemptionAtMostHalvesTheTimeFromLostToClean`; rows 21-23.
  - Toggle: every reward reads `DarkMagicData.getTier` (through `DarkCosts.tierOf` or directly) or checks `Config.isDarkMagicEnabled()`; the client copy is 0 when the server sends 0; test `theToggleOffMeansNoReward`; row 27.
  - Done when: rows 5-6 (the camp), 10 (the escort), 21 (healing brings a Lost player down).
- **Type consistency.** `SpellCosts.manaCost` passes `spell.getManaCost() * wand * mastery` (an `int` times two `float`s, a `float`) and `darkMultiplier(player, spell)` (a `float`) to `DarkRewardRules.manaCost(float, float)`, which rounds exactly as before when the multiplier is 1. The three channel costs multiply `SpellCosts.darkMultiplier(Player, SpellType)` into their existing `float` expressions; `getFiendfyreStartupCost` takes a `ServerPlayer` (a `Player`). `DarkRewards.deathEatersIgnore(Entity)` takes the `LivingEntity` of the goal predicate and the `ServerPlayer` of the base raid. `WizardEntity.getEscortOwner()` returns `ServerPlayer` from `Level.getPlayerByUUID(UUID)`. `DarkDeeds.onAvadaHit(LivingEntity, ServerPlayer)` gets `living` and `darkDeedPayer()`; `onHeal(LivingEntity, Entity, boolean)` gets `HealSpell.apply`'s own parameters. `MasteryAwards.isPassive(LivingEntity)` is static. Checked against the 1.21 mapped jar: `NearestAttackableTargetGoal(Mob, Class, int, boolean, boolean, Predicate<LivingEntity>)`; `TargetGoal(Mob, boolean)`, its `protected final Mob mob` and `protected boolean canAttack(LivingEntity, TargetingConditions)`; `TargetingConditions.DEFAULT`; `LivingEntity.getLastHurtByMob()`, `getLastHurtByMobTimestamp()`, `getLastHurtMob()`, `getLastHurtMobTimestamp()`; `PathNavigation.moveTo(Entity, double)`; `Entity.teleportTo(double, double, double)`; `Stats.PLAY_TIME` (a `ResourceLocation`, read through `Stats.CUSTOM.get`, as the existing insomnia check reads `TIME_SINCE_REST`). Vanilla sets `getLastHurtMob` only in `Player.attack` and `Mob.doHurtTarget` (melee) and for projectiles, which is why ruling 7 says spells do not direct the escort.
- **Compile check while planning.** Every new file and every edit in this plan was applied to a scratch copy of the tree at 876555c (with a one-method stand-in for Phase 2's `DarkCosts.tierOf`) and compiled with `javac` against the Forge 1.21 mapped jar: no errors. The 17 tests of Task 1 were run against Phase 1's `DarkRules`: all pass. Each "find" text occurs exactly once in its file at 876555c (the Task 3 and Task 4 anchors in `WizardEntity` and `en_us.json` that an earlier task creates were checked after that task's edit).
- **Placeholder scan:** none. Every number, anchor, priority, distance, string and command is given. `<T1>` is the commit recorded in Task 1 Step 0, not a placeholder in code.
- **Counts:** Task 1 adds 17 tests (`rewardTiersMatchTheSpec`, `onlyDarkSpellsGetTheDiscountAndOnlyFromCorrupted`, `discountedCostsRoundAfterWandAndMastery`, `theToggleOffMeansNoReward`, `tiersOutsideTheRangeAreClamped`, `escortLastsFiveMinutesAndHasFourToSix`, `escortChecksOncePerSecond`, `escortFollowsWithALeash`, `innocentsAreVillagersStudentsElvesAndPassiveMobs`, `innocentBonusOnlyForAKill`, `deedsChargeLikeCasts`, `horcruxAddsThirtyAndLiftsToTheFloor`, `floorWhileTheHorcruxExists`, `redemptionLightensByOneThroughTheFloor`, `onlyAWillingHealOfAnotherHurtThingRedeems`, `redemptionAtMostOncePerWindowOfPlay`, `redemptionAtMostHalvesTheTimeFromLostToClean`). Total BASE + 17.
- **Spec notes:** (1) The spec puts the +30 "in `HorcruxData.startRitual`"; a ritual can fail after it starts, so the bonus is given at the forging (ruling 12). (2) A floor of 60 under a value below 60 would freeze decay there (Phase 1's loss rule); forging lifts the value to at least 60 (ruling 12). A Horcrux forged in creative or with the toggle off gets no bonus, so a value under 60 stays frozen until the Horcrux is gone. (3) The spec names only `SpellCosts.manaCost`, but the Fiendfyre and Imperio channel costs bypass it; they get the same multiplier (ruling 2). (4) "Death Eaters do not attack you unless you attack them first" is implemented per Death Eater (the hurt-by goal does not alert others), as the spec's "hurt-by unchanged" asks. (5) The escort count and its behaviour when struck by its owner are not in the spec (rulings 6-9). (6) The roadmap's build line (`timeout 900 ./gradlew`) is superseded by the Global Constraints.
