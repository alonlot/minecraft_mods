# Dark Magic Phase 4 (Advancements and Docs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** Two advancements mark the ends of the road. "A Torn Soul" is granted when your soul reaches Lost; "Redemption" is granted when a player who was Lost comes all the way back to Clean. Both are granted on the server from `DarkMagicData.write`, the one place every Darkness change passes and the tier change is announced, never on login or sync; neither is announced in chat, so nobody learns another player's tier. Then the docs catch up with the finished feature: a Dark Magic section in `MOD_FEATURES_WIKI.md`, a Dark Magic system card and three spell lines in the web wiki (`wiki/data.js`, which feeds `index.html` and `systems.html`), and a Dark Magic tab in the Hogwarts Guide. The docs describe the final behaviour of Phases 1-3 (as built, fix waves included) with every user ruling.

**Architecture:**
- **Pure rules** (`spell/dark/DarkAdvancementRules`): one function `afterWrite(enabled, newTier, wasLost)` returning which advancement to grant and the flag to store; the two advancement paths and the flag key. Minecraft-free, test-first (9 tests).
- **Server glue** (`spell/dark/DarkAdvancements`): reads and writes the persisted flag `DarkMagicWasLost`, calls the rule, and grants by completing every remaining criterion (the `VoldemortEntity.grantAllCriteria` pattern, copied, not shared).
- **Hook:** one line at the end of `DarkMagicData.write`, after the tier-change announcement.
- **Data:** `advancement/hp/torn_soul.json` and `advancement/hp/redemption.json`, each with one `minecraft:impossible` criterion (only code can complete it); four lang strings.
- **Docs:** `MOD_FEATURES_WIKI.md`, `wiki/data.js`, `client/HogwartsGuideScreen.java` (a new `DARK_MAGIC` section, English literals like every other page) and one lang string for its tab.
- **Nothing on the wire:** no packet, no protocol change, no config value, no `@SubscribeEvent`, no custom `CriterionTrigger`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-07-dark-magic-roadmap.md`: "Phase 4: Advancements and docs", decision 6 (tiers), decision 11 (nobody learns another player's tier), decision 13 (`enableDarkMagic`), "Advancements use only vanilla triggers. `VoldemortEntity.grantAllCriteria` is the one example of granting from code", and every decision and user ruling the docs describe (decisions 1-16, "User answers"). The docs also follow the Phase 1-3 plans and the controller rulings in their ledgers.

**Built after Dark Magic Phases 2 and 3.** This plan builds on names those plans define: Phase 2's `DarkCostRules` constants (`CORPOREAL_HALF`, `SHIELD_UPKEEP_MULTIPLIER`, `DARK_INSOMNIA_TICKS`, `STUDENT_KEEP_AWAY_DISTANCE`, `LOST_MAX_HEALTH_DELTA`) and `DarkMagicData.sync` calling `DarkCosts.refreshMaxHealth`; Phase 3's `DarkRewardRules` constants (`DARK_MANA_MULTIPLIER`, `ESCORT_DURATION_TICKS`, `ESCORT_MIN_SIZE`, `ESCORT_MAX_SIZE`, `ESCORT_CATCH_UP`) and `DarkMagicData.floor` reading `DarkRewardRules.floor`. The code tasks (1-2) use none of them; the docs task (3) checks them before writing (its Step 0).

## Requirements (Phase 4 of the spec)

1. **A Torn Soul:** granted when the player reaches Lost (Phase 4 bullet 1).
2. **Redemption:** granted only to a player who was Lost and returns to Clean; a persisted flag records Lost (Phase 4 bullet 1; controller brief).
3. Granted from code on the server, inside `DarkMagicData` where the tier change is announced; never on login or sync. Either `grantAllCriteria` or one `CriterionTrigger`, decided and justified (Phase 4 bullet 1; controller brief).
4. The decision logic is pure and tested first (controller brief; global constraint "pure rules first").
5. The advancements fit the existing `hp/` tree (parent), use existing items as icons, and have frames and lang keys (controller brief).
6. `MOD_FEATURES_WIKI.md`, the web wiki (`wiki/data.js` and the pages it feeds) and a Dark Magic section in the Hogwarts Guide describe the final behaviour of Phases 1-3 with every user ruling: the gain table, the tiers, decay in 50 hours, capped redemption, the costs, the rewards, Sneakoscope Part B (Phase 4 bullet 2; controller brief).
7. Nothing names another player's tier (decision 11), and `enableDarkMagic = false` turns it all off (decision 13).

## Rulings made while planning

1. **Granted with `minecraft:impossible`, not a custom `CriterionTrigger`.** Each advancement file has one criterion with the vanilla `minecraft:impossible` trigger, which nothing in the game ever fires, and `DarkAdvancements.grant` completes it from code (the `VoldemortEntity.grantAllCriteria` loop). Why: a custom trigger needs a `Registries.TRIGGER_TYPE` registration, a trigger class, an instance record and a codec, all to carry one event that only our own code raises, and it would add registry and class-loading surface for no gain; nobody writes data packs against it. `impossible` also means a player can never earn either advancement by any other route. `VoldemortEntity` is not touched: its helper is private and its advancements also have real kill triggers.
2. **The grant point is the end of `DarkMagicData.write`, after the tier-change announcement.** Every change of Darkness passes there: a paid dark cast, decay, the Phase 3 deeds and redemption, and `/darkmagic set|add`. Login, respawn, dimension change and the config reload only call `sync`, which never grants. So the toast comes right after the chat line "Your soul darkens. You are now Lost."
3. **A Torn Soul is granted on every write that leaves the effective tier at Lost**, not only on the write that crosses into it. A player who was already Lost before Phase 4 earns it on their next dark cast or decay step (a write at Lost happens at least once per `darkMagicDecayTicks` of quiet play). Granting a completed advancement does nothing (`AdvancementProgress.isDone` is checked first), so the repeat is one map lookup per write.
4. **The flag `DarkMagicWasLost`** (a boolean in `PlayerPersisted/hogwartsmagic`, so it survives death and relog like Darkness) is set by any write at Lost. A write that leaves the tier Clean while the flag is set grants Redemption and clears the flag, so a second Redemption journey needs Lost again (only matters after `/advancement revoke`). The tiers in between keep the flag either way. A missing key reads false: nobody is backfilled, and a Lost-to-Clean journey finished before Phase 4 earns nothing.
5. **With `enableDarkMagic` off nothing is granted and the flag is kept.** Off, every write reads tier Clean (`DarkRules.effectiveTier`), which must not count as coming back. Turning it back on resumes the journey where it was.
6. **The command counts.** `/darkmagic set` and `add` go through `write` and already announce a tier change; they also grant. It is op level 2 and is how testers check both advancements without playing 50 hours. A creative player can be granted through the command (gains never reach creative players).
7. **Privacy:** both files have `"announce_to_chat": false` (decision 11: no chat line names another player's tier). `"show_toast": true` (the toast is the player's own), `"hidden": false` like every advancement in the tree. Vanilla shows a player's advancements only to that player.
8. **Placement:** "A Torn Soul" `hogwartsmagic:hp/torn_soul`, parent `hogwartsmagic:hp/first_spellbook` ("Borrowed Magic": dark spells are learned from spellbooks, and it already parents the study branch), icon `hogwartsmagic:horcrux`, frame `goal`. "Redemption" `hogwartsmagic:hp/redemption`, parent `hogwartsmagic:hp/torn_soul`, icon `hogwartsmagic:essence_of_dittany` (healing; Heal is the way back), frame `challenge` (about 25 to 50 hours of play). No `rewards`.
9. **Lang:** "A Torn Soul" / "Let dark magic carry your soul all the way to Lost."; "Redemption" / "Bring a Lost soul all the way back to Clean." Inserted after the `basilisk_bane.description` line.
10. **A Horcrux blocks Redemption** while it exists: its floor of 60 (Phase 3) keeps the soul at Corrupted or darker. Intended; the guide says so.
11. **Hogwarts Guide:** a new `DARK_MAGIC` section between `SYSTEMS` ("Wizarding Ways") and `CREATURES`, tab label "Dark Magic" (`gui.hogwartsmagic.hogwarts_guide.section.dark_magic`), five pages written in the guide's in-world voice, English literals like every other page. Each page is checked to fit above the page counter (a 6 px per character estimate, at most 158 px of 168). Eight tabs end at `top + 204`, inside the 248 px page.
12. **MOD_FEATURES_WIKI.md:** a full `## Dark Magic` section before `## House Perks`, a "Dark Magic" bullet in Core Gameplay Systems, two lines in the Advancements list, and one-line updates where existing text is now incomplete (Expecto Patronum, Morsmordre, Heal, Dark Mark, Insomnia Dementors). No other section changes.
13. **wiki/data.js:** a `dark-magic` system card after `horcruxes` (`stats.systems` goes up by one), and one feature line each on Expecto Patronum, Morsmordre and Heal. `app.js` and the HTML pages render it unchanged.
14. **The docs follow the code.** Task 3 Step 0 greps the Phase 2 and 3 constants and reads both ledgers; where a fix wave changed a number or a behaviour, the doc text follows the code and the report lists the change.
15. **Nothing else changes:** no packet, no protocol change, no config value, no `@SubscribeEvent`, no entity event byte, no new item or texture.

## Global Constraints

- Server authoritative. `spell/dark/` never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. The one client file touched (`client/HogwartsGuideScreen`) gains only text.
- No `switch` on an enum anywhere in the new code. Use if-chains and `==`.
- No new hurt, death or effect handler, and no new `@SubscribeEvent`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- Every JSON file touched stays valid JSON: `python3 -c "import json; json.load(open('<file>', encoding='utf-8')); print('json ok')"`. `wiki/data.js` must pass `node --check wiki/data.js`.
- Build and test (cloud):
  - First `pgrep -fa "[G]radleWrapperMain"`. If it prints a process, another agent's build is running: wait (poll every 30 s, at most 20 min). An idle `GradleDaemon` alone is fine.
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkAdvancementRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- **Base test count: the count at the start of Task 1; all green.** Call it BASE. The implementer of Task 1 runs the full build before Step 1 and records BASE in the report. Task 1 adds 9 (BASE + 9); no other task adds tests. All tests stay green.
- **The network protocol is not changed by this plan.** Before Task 1, record the current line (never assume its number): `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Task 5 checks it is identical (unless another phase changed it in between, which the ledger will say).
- Dedicated-server boot (Task 2 and Task 5), "the boot block":
  1. `cd /home/user/minecraft_mods && mkdir -p run && echo "eula=true" > run/eula.txt && rm -f run/logs/latest.log && : > /tmp/dm4_srv_in.txt`
  2. `cd /home/user/minecraft_mods && (tail -f /tmp/dm4_srv_in.txt | timeout 900 bash ./gradlew runServer --console=plain --max-workers=2 > /tmp/dm4_srv.log 2>&1 &)`
  3. Wait up to 10 min: `cd /home/user/minecraft_mods && timeout 600 bash -c 'until grep -qE "Done \(|Exception|Crash" run/logs/latest.log 2>/dev/null; do sleep 5; done'; grep -nE "Done \(|Exception|Crash" run/logs/latest.log | head`
  4. The advancements loaded without a parse error: `cd /home/user/minecraft_mods && grep -nE "Parsing error loading custom advancement|Couldn't load advancement|torn_soul|hp/redemption" run/logs/latest.log | head`. Expected: no output (the loader logs only failures).
  5. `printf 'stop\n' >> /tmp/dm4_srv_in.txt; cd /home/user/minecraft_mods && timeout 90 bash -c 'until grep -q "All dimensions are saved" run/logs/latest.log; do sleep 2; done'`
  6. `cd /home/user/minecraft_mods && grep -nE "Done \(|Exception|ERROR" run/logs/latest.log | head -40`
  7. Stop only the java server process and the helper `tail`, never a Gradle process:
     ```bash
     ps -eo pid=,comm=,args= | awk '$2=="java" && $0 !~ /org\.gradle\./ && $0 ~ /nogui|forge_userdev_server|ForgeBootstrap|BootstrapLauncher/ {print $1}' | xargs -r kill
     pkill -f "[t]ail -f /tmp/dm4_srv_in.txt"
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
- Line numbers drift. Every edit names the exact text to find, quoted from 3e25d8b. Each anchor was checked to occur exactly once in its file at 3e25d8b, and none of them is a line that Dark Magic Phase 2 or 3 or Brewing Phase 2 edits. Before each edit, `grep -cF` the anchor; if it is not exactly 1, find the same spot by its text, keep the other phase's change, and say so in the report.
- **Other agents work on this branch.** `en_us.json`, `MOD_FEATURES_WIKI.md`, `wiki/data.js` and `HogwartsGuideScreen.java` are also edited by the brewing phases. Edit only at the anchors below, and commit by path so nobody else's uncommitted change is swept in: if `git diff -- <file>` before your edit already shows changes you did not make, stop and report (do not commit another agent's work).

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancementRules.java` | Pure: when each advancement is granted, the flag | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/dark/DarkAdvancementRulesTest.java` | 9 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancements.java` | Flag read and write, grant from code | 2 |
| Modify `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java` | One call at the end of `write` | 2 |
| Create `src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json` | "A Torn Soul" | 2 |
| Create `src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json` | "Redemption" | 2 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Four advancement strings (T2), the guide tab (T4) | 2, 4 |
| Modify `MOD_FEATURES_WIKI.md` | Dark Magic section and line updates | 3 |
| Modify `wiki/data.js` | Dark Magic system card, three spell lines, the systems count | 3 |
| Modify `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` | The `DARK_MAGIC` section | 4 |

Untouched on purpose: `VoldemortEntity` (ruling 1), `DarkRules`, `DarkCostRules`, `DarkCosts`, `DarkRewardRules`, `DarkRewards`, `DarkDeeds` (their behaviour is only described), `Config.java`, `ExampleMod.java`, `SpellPackets` and the protocol, `wiki/app.js` and every `wiki/*.html` (they render `data.js`), every other advancement file.

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2 | `DarkAdvancementRules.TORN_SOUL` (`"hp/torn_soul"`), `REDEMPTION` (`"hp/redemption"`), `KEY_WAS_LOST` (`"DarkMagicWasLost"`), `Outcome(boolean grantTornSoul, boolean grantRedemption, boolean wasLost)` with `flagChanged(boolean)`, `afterWrite(boolean, int, boolean)` | names identical |
| T2 → DarkMagicData | `DarkAdvancements.afterWrite(ServerPlayer, boolean, int)` | written in T2 |
| T2 → JSON | file names `torn_soul.json`, `redemption.json` in `advancement/hp/` match the two paths | checked in T2 Step 5 |
| Phase 1 → all | `DarkRules.CLEAN`, `LOST`, `SHADOWED`, `CORRUPTED`, `DarkRules.effectiveTier(boolean, float)`, `DarkMagicData.write` (private, its locals `enabled`, `oldTier`, `newTier`), `PlayerMagicData.getModData(Player)`, `saveModData(Player, CompoundTag)` | on the branch since f6aa7c4 |
| Phases 2-3 → T3 | the constants named under "Built after" | T3 Step 0 |

---

### Task 1: DarkAdvancementRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancementRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/dark/DarkAdvancementRulesTest.java`

**Interfaces.** Consumes: `DarkRules.CLEAN`, `SHADOWED`, `CORRUPTED`, `LOST`, `DarkRules.effectiveTier(boolean, float)` (Phase 1). Produces: the T1 row of the pre-flight table.

- [ ] **Step 0: Record the base.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, all green. Record the test count as BASE, the commit (`git rev-parse --short HEAD`) as `<T1>`, and the output of `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`, in the report.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/spell/dark/DarkAdvancementRulesTest.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.dark.DarkAdvancementRules.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkAdvancementRulesTest {

    @Test
    void reachingLostGrantsATornSoulAndRemembersIt() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.LOST, false);
        assertTrue(outcome.grantTornSoul());
        assertFalse(outcome.grantRedemption());
        assertTrue(outcome.wasLost());
        assertTrue(outcome.flagChanged(false));
    }

    @Test
    void everyWriteAtLostStillGrantsATornSoul() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.LOST, true);
        assertTrue(outcome.grantTornSoul());
        assertFalse(outcome.grantRedemption());
        assertTrue(outcome.wasLost());
        assertFalse(outcome.flagChanged(true));
    }

    @Test
    void cleanAfterLostGrantsRedemptionAndForgets() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.CLEAN, true);
        assertFalse(outcome.grantTornSoul());
        assertTrue(outcome.grantRedemption());
        assertFalse(outcome.wasLost());
        assertTrue(outcome.flagChanged(true));
    }

    @Test
    void cleanWithoutHavingBeenLostGrantsNothing() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.CLEAN, false);
        assertEquals(new Outcome(false, false, false), outcome);
        assertFalse(outcome.flagChanged(false));
    }

    @Test
    void theTiersBetweenKeepTheFlag() {
        for (int tier = DarkRules.SHADOWED; tier <= DarkRules.CORRUPTED; tier++) {
            assertEquals(new Outcome(false, false, true), DarkAdvancementRules.afterWrite(true, tier, true), "tier " + tier);
            assertEquals(new Outcome(false, false, false), DarkAdvancementRules.afterWrite(true, tier, false), "tier " + tier);
        }
    }

    @Test
    void theToggleOffGrantsNothingAndKeepsTheFlag() {
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(new Outcome(false, false, true), DarkAdvancementRules.afterWrite(false, tier, true), "tier " + tier);
            assertEquals(new Outcome(false, false, false), DarkAdvancementRules.afterWrite(false, tier, false), "tier " + tier);
        }
    }

    @Test
    void tiersOutsideTheRangeAreClamped() {
        assertEquals(new Outcome(true, false, true), DarkAdvancementRules.afterWrite(true, 7, false));
        assertEquals(new Outcome(false, true, false), DarkAdvancementRules.afterWrite(true, -3, true));
    }

    @Test
    void aFullJourneyGrantsEachOnceAndASecondNeedsLostAgain() {
        float[] path = {0.0f, 20.0f, 40.0f, 60.0f, 79.99f, 80.0f, 100.0f, 80.0f, 79.99f, 40.0f, 20.0f, 19.99f, 0.0f,
                25.0f, 19.0f};
        boolean wasLost = false;
        int tornSoul = 0;
        int redemption = 0;
        int redeemedAt = -1;
        for (int i = 0; i < path.length; i++) {
            Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.effectiveTier(true, path[i]), wasLost);
            if (outcome.grantTornSoul()) {
                tornSoul++;
            }
            if (outcome.grantRedemption()) {
                redemption++;
                redeemedAt = i;
            }
            wasLost = outcome.wasLost();
        }
        assertEquals(3, tornSoul, "80, 100 and 80 each leave the player Lost");
        assertEquals(1, redemption);
        assertEquals(11, redeemedAt, "19.99 is the first Clean value after Lost");
        assertFalse(wasLost);
    }

    @Test
    void idsAndKeyArePinned() {
        assertEquals("hp/torn_soul", DarkAdvancementRules.TORN_SOUL);
        assertEquals("hp/redemption", DarkAdvancementRules.REDEMPTION);
        assertEquals("DarkMagicWasLost", DarkAdvancementRules.KEY_WAS_LOST);
    }
}
```

- [ ] **Step 2: See it fail.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkAdvancementRulesTest" --console=plain --max-workers=2`. Expected: FAIL, `compileTestJava` reports `cannot find symbol` for `DarkAdvancementRules`. Record "RED seen" in the report.

- [ ] **Step 3: Write the rules.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancementRules.java`:

```java
package com.hogwartsmagic.spell.dark;

/**
 * When the two Dark Magic advancements are granted (Phase 4), free of Minecraft so it is unit-tested.
 *
 * DarkMagicData.write calls this after every write (gain, decay, deed, redemption, command) with the effective tier
 * the write left. "A Torn Soul" is granted on any write that leaves the player Lost, and the persisted flag
 * KEY_WAS_LOST is set. "Redemption" is granted on a write that leaves the player Clean while that flag is set, and the
 * flag clears, so a second journey needs Lost again. The tiers in between keep the flag. With enableDarkMagic off
 * every tier reads Clean, so nothing is granted and the flag is kept.
 */
public final class DarkAdvancementRules {
    /** Advancement path inside the hogwartsmagic namespace: reach Lost. */
    public static final String TORN_SOUL = "hp/torn_soul";
    /** Advancement path inside the hogwartsmagic namespace: from Lost back to Clean. */
    public static final String REDEMPTION = "hp/redemption";
    /** PlayerPersisted/hogwartsmagic boolean: the player has been Lost since they were last Clean. */
    public static final String KEY_WAS_LOST = "DarkMagicWasLost";

    /** What one write grants, and the flag to store after it. */
    public record Outcome(boolean grantTornSoul, boolean grantRedemption, boolean wasLost) {
        /** True when the stored flag must be rewritten. */
        public boolean flagChanged(boolean before) {
            return this.wasLost != before;
        }
    }

    private DarkAdvancementRules() {
    }

    public static Outcome afterWrite(boolean enabled, int newTier, boolean wasLost) {
        if (!enabled) {
            return new Outcome(false, false, wasLost);
        }
        int tier = Math.max(DarkRules.CLEAN, Math.min(DarkRules.LOST, newTier));
        if (tier == DarkRules.LOST) {
            return new Outcome(true, false, true);
        }
        if (tier == DarkRules.CLEAN && wasLost) {
            return new Outcome(false, true, false);
        }
        return new Outcome(false, false, wasLost);
    }
}
```

- [ ] **Step 4: See it pass.** Gradle check, then the one-class command. Expected: BUILD SUCCESSFUL, 9 tests, 0 failures. Then the full build: BASE + 9, all green.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 4: DarkAdvancementRules, when A Torn Soul and Redemption are granted

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancementRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkAdvancementRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancementRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkAdvancementRulesTest.java
```

---

### Task 2: The grant, the hook and the two advancements

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancements.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`
- Create: `src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json`
- Create: `src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: T1 (`DarkAdvancementRules`), `PlayerMagicData.getModData(Player)`, `PlayerMagicData.saveModData(Player, CompoundTag)`, `ExampleMod.MODID`; vanilla (checked against the Forge 1.21 mapped jar): `ServerPlayer.server` (public field), `MinecraftServer.getAdvancements()` returning `ServerAdvancementManager`, `ServerAdvancementManager.get(ResourceLocation)` returning `AdvancementHolder` (null when missing), `ServerPlayer.getAdvancements()` returning `PlayerAdvancements`, `PlayerAdvancements.getOrStartProgress(AdvancementHolder)`, `PlayerAdvancements.award(AdvancementHolder, String)`, `AdvancementProgress.isDone()`, `AdvancementProgress.getRemainingCriteria()` (`Iterable<String>`), `CriteriaTriggers.IMPOSSIBLE` (id `minecraft:impossible`). Produces: `DarkAdvancements.afterWrite(ServerPlayer, boolean, int)`.

- [ ] **Step 1: The glue class.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancements.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Grants the two Dark Magic advancements (Phase 4) from code. Both advancement files use the vanilla
 * {@code minecraft:impossible} criterion, so nothing but this class can complete them. Called only from
 * DarkMagicData.write, never on login, sync or a config reload. Server only.
 */
public final class DarkAdvancements {
    private DarkAdvancements() {
    }

    /** After a write: the effective tier it left, and whether Dark Magic is on (DarkAdvancementRules decides). */
    public static void afterWrite(ServerPlayer player, boolean enabled, int newTier) {
        CompoundTag modData = PlayerMagicData.getModData(player);
        boolean wasLost = modData.getBoolean(DarkAdvancementRules.KEY_WAS_LOST);
        DarkAdvancementRules.Outcome outcome = DarkAdvancementRules.afterWrite(enabled, newTier, wasLost);
        if (outcome.flagChanged(wasLost)) {
            modData.putBoolean(DarkAdvancementRules.KEY_WAS_LOST, outcome.wasLost());
            PlayerMagicData.saveModData(player, modData);
        }
        if (outcome.grantTornSoul()) {
            grant(player, DarkAdvancementRules.TORN_SOUL);
        }
        if (outcome.grantRedemption()) {
            grant(player, DarkAdvancementRules.REDEMPTION);
        }
    }

    /** Completes every remaining criterion (as VoldemortEntity.grantAllCriteria); a done or missing one is skipped. */
    private static void grant(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path));
        if (advancement == null) {
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        AdvancementProgress state = progress.getOrStartProgress(advancement);
        if (state.isDone()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        state.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            progress.award(advancement, criterion);
        }
    }
}
```

- [ ] **Step 2: The hook.** In `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`, inside `private static void write(ServerPlayer player, float value)`, find (Phase 1 text; Phases 2 and 3 do not edit `write`):

```java
        if (newTier != oldTier) {
            announce(player, oldTier, newTier);
        }
    }
```

and replace it with:

```java
        if (newTier != oldTier) {
            announce(player, oldTier, newTier);
        }
        // Phase 4: A Torn Soul at Lost, Redemption back at Clean after Lost. Granted only here, never on login or sync.
        DarkAdvancements.afterWrite(player, enabled, newTier);
    }
```

Also, in the class javadoc of `DarkMagicData`, find `in client/DarkMagicClientState. Every change goes through write, which syncs and announces a tier change.` and replace it with `in client/DarkMagicClientState. Every change goes through write, which syncs, announces a tier change and grants the Dark Magic advancements.`

- [ ] **Step 3: The advancement files.** Create `src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json`:

```json
{
  "parent": "hogwartsmagic:hp/first_spellbook",
  "display": {
    "icon": {
      "id": "hogwartsmagic:horcrux",
      "count": 1
    },
    "title": {
      "translate": "advancement.hogwartsmagic.torn_soul.title"
    },
    "description": {
      "translate": "advancement.hogwartsmagic.torn_soul.description"
    },
    "frame": "goal",
    "show_toast": true,
    "announce_to_chat": false,
    "hidden": false
  },
  "criteria": {
    "reached_lost": {
      "trigger": "minecraft:impossible"
    }
  }
}
```

Create `src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json`:

```json
{
  "parent": "hogwartsmagic:hp/torn_soul",
  "display": {
    "icon": {
      "id": "hogwartsmagic:essence_of_dittany",
      "count": 1
    },
    "title": {
      "translate": "advancement.hogwartsmagic.redemption.title"
    },
    "description": {
      "translate": "advancement.hogwartsmagic.redemption.description"
    },
    "frame": "challenge",
    "show_toast": true,
    "announce_to_chat": false,
    "hidden": false
  },
  "criteria": {
    "returned_to_clean": {
      "trigger": "minecraft:impossible"
    }
  }
}
```

- [ ] **Step 4: The lang strings.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find:

```json
  "advancement.hogwartsmagic.basilisk_bane.description": "Slay a Basilisk and live to tell the tale.",
```

and replace it with:

```json
  "advancement.hogwartsmagic.basilisk_bane.description": "Slay a Basilisk and live to tell the tale.",
  "advancement.hogwartsmagic.torn_soul.title": "A Torn Soul",
  "advancement.hogwartsmagic.torn_soul.description": "Let dark magic carry your soul all the way to Lost.",
  "advancement.hogwartsmagic.redemption.title": "Redemption",
  "advancement.hogwartsmagic.redemption.description": "Bring a Lost soul all the way back to Clean.",
```

- [ ] **Step 5: Check the data.**
  - `cd /home/user/minecraft_mods && for f in src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json src/main/resources/assets/hogwartsmagic/lang/en_us.json; do python3 -c "import json,sys; json.load(open(sys.argv[1], encoding='utf-8')); print('json ok')" "$f"; done`. Expected: `json ok` three times.
  - The parents, icons, triggers, privacy and lang keys:
    ```bash
    cd /home/user/minecraft_mods && python3 - <<'EOF'
    import json, os, re
    base = "src/main/resources/data/hogwartsmagic/advancement/hp/"
    lang = json.load(open("src/main/resources/assets/hogwartsmagic/lang/en_us.json", encoding="utf-8"))
    mod = open("src/main/java/com/hogwartsmagic/ExampleMod.java", encoding="utf-8").read()
    for name in ("torn_soul", "redemption"):
        d = json.load(open(base + name + ".json", encoding="utf-8"))
        parent = d["parent"].split("hp/")[1]
        assert os.path.exists(base + parent + ".json"), parent
        icon = d["display"]["icon"]["id"].split(":")[1]
        assert re.search(r'ITEMS\.register\("' + icon + '"', mod), icon
        assert [c["trigger"] for c in d["criteria"].values()] == ["minecraft:impossible"]
        assert d["display"]["announce_to_chat"] is False
        for k in ("title", "description"):
            assert d["display"][k]["translate"] in lang, d["display"][k]["translate"]
    print("advancements ok")
    EOF
    ```
    Expected: `advancements ok`.

- [ ] **Step 6: Build.** Gradle check, then the full build. Expected: BUILD SUCCESSFUL, BASE + 9, all green.

- [ ] **Step 7: Boot.** Run the boot block (Global Constraints). Expected: `Done (`; step 4 prints nothing; no `Exception` or `ERROR` from `hogwartsmagic`; no server java process left afterwards. (The grant itself needs a player; the human checklist covers it.)

- [ ] **Step 8: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 4: A Torn Soul and Redemption, granted when Darkness is written

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancements.java src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkAdvancements.java src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 3: MOD_FEATURES_WIKI.md and the web wiki

**Files:**
- Modify: `MOD_FEATURES_WIKI.md`
- Modify: `wiki/data.js`

**Interfaces.** Consumes: the built Phase 1-3 code (read only). Produces: documentation only.

- [ ] **Step 0: Check the built numbers.** Dark Magic Phases 2 and 3 must be committed (their ledgers say "complete"). Run:
  - `cd /home/user/minecraft_mods && grep -nE "CORPOREAL_HALF|SHIELD_UPKEEP_MULTIPLIER|DARK_INSOMNIA_TICKS|BASE_INSOMNIA_TICKS|STUDENT_KEEP_AWAY_DISTANCE|LOST_MAX_HEALTH_DELTA|ELF_FLEE_DISTANCE" src/main/java/com/hogwartsmagic/spell/dark/DarkCostRules.java | grep "static final"`. Expected values: `CORPOREAL_HALF` 0.5, `SHIELD_UPKEEP_MULTIPLIER` 1.5, `DARK_INSOMNIA_TICKS` 3 days, `BASE_INSOMNIA_TICKS` 5 days, `STUDENT_KEEP_AWAY_DISTANCE` 8, `LOST_MAX_HEALTH_DELTA` -2, `ELF_FLEE_DISTANCE` 8.
  - `cd /home/user/minecraft_mods && grep -nE "DARK_MANA_MULTIPLIER|ESCORT_DURATION_TICKS|ESCORT_MIN_SIZE|ESCORT_MAX_SIZE|ESCORT_CATCH_UP" src/main/java/com/hogwartsmagic/spell/dark/DarkRewardRules.java | grep "static final"`. Expected: 0.85, 5 minutes (6000 ticks), 4, 6, 32.
  - `cd /home/user/minecraft_mods && grep -n "gain.put" src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java`. Expected: avada_kedavra 6.0, fiendfyre 2.5, morsmordre 2.5, imperio 1.5, crucio 1.2, tenebris_flight 0.6, sectumsempra 0.4, dolohov_curse 0.4, serpensortia 0.3.
  - Read the "Controller rulings" and the fix-wave lines of `docs/superpowers/handoff/ledgers/dark-magic-phase2-progress.md` and `dark-magic-phase3-progress.md`.
  If either file is missing, stop and report BLOCKED (Phases 2 and 3 come first). If a value or a ledger line differs from the text in Steps 1-2, write what the code does instead and list each change in the report.

- [ ] **Step 1: MOD_FEATURES_WIKI.md.** Seven edits.

(a) Core Gameplay Systems. Find:

```markdown
- Dynamic world events
```

and replace it with:

```markdown
- Dark Magic
  Dark spells darken your soul: a hidden Darkness score from 0 to 100 in five tiers, from Clean to Lost. The world reacts to each tier with costs and, from Corrupted, rewards. Time and healing bring you back.

- Dynamic world events
```

(b) Expecto Patronum. Find `- 15% of casts produce a corporeal Patronus — the stag,` (the dash is an em dash, U+2014) and replace that text with `- 15% of casts produce a corporeal Patronus (7.5% with a Shadowed soul, never from Tainted; see Dark Magic) — the stag,`. The rest of the line stays.

(c) Morsmordre. Find:

```markdown
- Raid trigger includes lightning, a large Dark Mark, and spawned hostile wizards.
```

and replace it with:

```markdown
- Raid trigger includes lightning, a large Dark Mark, and spawned hostile wizards.
- Cast into open sky by a player with a Lost soul, it calls a Death Eater escort for 5 minutes instead of a raid (see Dark Magic).
```

(d) Heal / Episkey. Find:

```markdown
- Used by both players and some NPC casters.
```

and replace it with:

```markdown
- Used by both players and some NPC casters.
- Healing another hurt creature lifts 1 Darkness from the caster, at most once per 30 minutes of play (see Dark Magic).
```

(e) Dark Mark. Find:

```markdown
- Periodically buffs nearby dark wizards with damage boost and regeneration.
```

and replace it with:

```markdown
- Periodically buffs nearby dark wizards, and players with a Corrupted or Lost soul, with damage boost and regeneration.
```

(f) Insomnia Dementors. Find:

```markdown
- Players who go too long without rest can trigger nighttime Dementor spawns in the Overworld.
```

and replace it with:

```markdown
- Players who go five days without rest (three with a Tainted or darker soul) can trigger nighttime Dementor spawns in the Overworld.
```

(g) Advancements list. Find:

```markdown
- Defeating Voldemort.
```

and replace it with:

```markdown
- Defeating Voldemort.
- Letting dark magic take your soul to Lost (A Torn Soul).
- Coming back from Lost to a Clean soul (Redemption).
```

(h) The Dark Magic section. Find:

```markdown
## House Perks
```

and replace it with the block below followed by a blank line and `## House Perks`:

```markdown
## Dark Magic

Every player has a hidden score, Darkness, from 0 to 100. Dark spells raise it, and it falls slowly once you stop. It survives death and relogging. Only you see your own tier.

### Gaining Darkness

- A dark spell adds Darkness when you pay for the cast, hit or miss. An Avada Kedavra windup counts when it completes; a Fiendfyre tap or hold counts once.

| Spell | Darkness per cast |
|---|---|
| Avada Kedavra | 6 |
| Fiendfyre | 2.5 |
| Morsmordre | 2.5 |
| Imperio | 1.5 |
| Crucio | 1.2 |
| Tenebris Flight | 0.6 |
| Sectumsempra | 0.4 |
| Dolohov's Curse | 0.4 |
| Serpensortia | 0.3 |

- Ten Avada Kedavra casts take a Clean soul to Corrupted. Mastering an Unforgivable means a dark soul.
- Killing an innocent with Avada Kedavra (a villager, a Hogwarts Student, a House Elf or a peaceful animal) adds 3 more.
- Forging a Horcrux adds 30 and lifts you to at least 60. While your Horcrux exists, Darkness cannot fall below 60. When it shatters or is destroyed, that floor goes.
- A dark spell cast through a body you possess with Imperio darkens you, the puppeteer, never the victim. Creative and spectator players gain nothing. NPCs have no Darkness.
- The server setting `darkMagicGainMultiplier` scales every gain.

### Tiers

| Tier | Darkness |
|---|---|
| Clean | 0 to 19 |
| Shadowed | 20 to 39 |
| Tainted | 40 to 59 |
| Corrupted | 60 to 79 |
| Lost | 80 to 100 |

- A tier change sends you one chat line and a sound: a low bell going darker, a chime going lighter. Nobody else hears it.
- The Grimoire shows "Soul: <tier>" in its header, white to dark green. From Corrupted the mana bar turns a dark green-black.
- No chat line or screen names another player's tier. Others can only see your smoke (Corrupted and Lost) and hear a Sneakoscope.

### Costs

Each tier keeps the costs of the tiers below it, except where a harder cost replaces a softer one.

- Shadowed: a corporeal Patronus answers 7.5% of the time instead of 15%.
- Tainted: never a corporeal Patronus; the charm always answers as the shield. Insomnia Dementors come after 3 days without rest instead of 5.
- Corrupted: holding the Patronus shield costs 50% more mana. Dementors within their 48-block follow range that can see you choose you before anyone else. Hogwarts Students keep about 8 blocks away. Faint smoke rises around you, which other players can see (not while you are invisible or spectating; Polyjuice does not hide it).
- Lost: one heart less (-2 max health), shown in the health bar like the Horcrux cost. Students attack you on sight instead of keeping away. House Elves flee from you, even one bound to you, and your own elf stops following you.
- The Patronus never fails. A dark wizard's Patronus is weaker, never gone.

### Rewards

- Corrupted: dark spells cost 15% less mana (after the wand and mastery multipliers), including the Fiendfyre and Imperio channels; the Grimoire shows the lower cost. A Dark Mark gives you Strength II and Regeneration as it gives Death Eaters.
- Lost: Death Eaters leave you alone until you strike one, and only the one you strike fights back. Death Eater base raids never come for you. Lord Voldemort, and the Death Eaters he summons, still fight you.
- Lost: a Morsmordre cast into the open sky calls 4 to 6 Death Eaters to your side for 5 minutes instead of a raid. They follow you, fight whatever hurts you or whatever you strike with a weapon or an arrow, and Apparate to you if you get more than 32 blocks away. They are still Death Eaters: they attack villagers, Students and players who are not Lost. Strike one and it turns on you. They leave when the 5 minutes end, or when you log out, die or change dimension. A new escort replaces the old one.

### The way back

- Decay: once you have cast no dark spell for 10 minutes, Darkness falls by 1 for every 30 minutes of online play, at every tier. From 100 to 0 takes 50 hours; from Corrupted to Clean, 30 hours.
- Redemption: a Heal that heals another hurt living thing (not yourself) removes 1 Darkness, at most once per 30 minutes of play. That at most doubles the pace: 100 to 0 in about 25 hours. A heal that cannot lighten you (Clean, or at the Horcrux floor) does not use up the 30 minutes. A Heal cast in creative or through a possessed body redeems nobody.
- Decay and redemption both stop at the Horcrux floor, so while a Horcrux exists you stay Corrupted or darker.

### Sneakoscope

- A Sneakoscope spins and whistles at a player with a Corrupted or Lost soul within 16 blocks, even under the Invisibility Cloak or Polyjuice. One you carry never senses you; a placed one senses everyone, its owner too. Creative and spectator players are never sensed.

### Advancements

- A Torn Soul: your soul reaches Lost.
- Redemption: a soul that was Lost comes all the way back to Clean.
- Neither is announced in chat, so nobody learns your tier from them.

### Settings and commands

- `enableDarkMagic` (true): off means no gain, no decay, no redemption, and every tier reads as Clean, so every cost, reward and advancement is off. The stored number is kept for when it is turned back on.
- `darkMagicGainMultiplier` (1.0), `darkMagicDecayTicks` (36000, 30 minutes), `darkMagicRedemptionTicks` (36000, 30 minutes), and `sneakoscopeDetectsPlayers` (true).
- `/darkmagic get|set|add <player> [value]` (operators) reads or changes a player's Darkness. `set` ignores the Horcrux floor; `add` respects it.
```

- [ ] **Step 2: wiki/data.js.** Five edits.

(a) The systems count. Find `    systems: 10` and replace the number with one more than it is now (11 at 3e25d8b; if another phase changed it, add one to that).

(b) The system card. Find:

```js
        "Hostile destruction can summon a Dementor"
      ]
    },
```

and replace it with:

```js
        "Hostile destruction can summon a Dementor"
      ]
    },
    {
      id: "dark-magic",
      title: "Dark Magic",
      icon: "DM",
      summary: "Dark spells darken your soul. Darkness runs from 0 to 100 in five tiers, Clean to Lost, and the world reacts to each.",
      features: [
        "Paid dark casts add Darkness, hit or miss: Avada Kedavra 6, Fiendfyre and Morsmordre 2.5, Imperio 1.5, Crucio 1.2, Tenebris Flight 0.6, Sectumsempra and Dolohov 0.4, Serpensortia 0.3",
        "Deeds: an Avada kill of an innocent adds 3; forging a Horcrux adds 30, and Darkness cannot fall below 60 while it exists",
        "Tiers: Clean 0-19, Shadowed 20-39, Tainted 40-59, Corrupted 60-79, Lost 80-100; only you see yours",
        "A weaker Patronus that never fails: 7.5% corporeal at Shadowed, shield only from Tainted, shield upkeep +50% from Corrupted",
        "Tainted: insomnia Dementors after 3 days instead of 5",
        "Corrupted: Dementors choose you first, Students keep away, faint smoke others can see",
        "Lost: one heart less, Students attack on sight, House Elves flee even when bound",
        "Rewards from Corrupted: dark spells cost 15% less mana and the Dark Mark buffs you",
        "Rewards at Lost: Death Eaters ignore you until you strike, and Morsmordre calls a 5-minute Death Eater escort instead of a raid",
        "The way back: -1 per 30 minutes of play after 10 quiet minutes (100 to 0 in 50 hours); Heal another hurt creature for -1, at most once per 30 minutes",
        "A Sneakoscope whistles at Corrupted and Lost players; advancements A Torn Soul and Redemption"
      ]
    },
```

(c) Expecto Patronum. Find `"15% chance to summon the corporeal Patronus"` and replace it with `"15% chance to summon the corporeal Patronus (7.5% with a Shadowed soul, never from Tainted)"`.

(d) Morsmordre. Find `"Can start a Death Eater sky raid if cast high into open sky"` and replace it with `"Can start a Death Eater sky raid if cast high into open sky", "Cast by a Lost soul, it calls a Death Eater escort for 5 minutes instead"`.

(e) Heal. Find `"Used by some NPC casters too"` and replace it with `"Used by some NPC casters too", "Healing another hurt creature lifts 1 Darkness, at most once per 30 minutes"`.

- [ ] **Step 3: Check.**
  - `cd /home/user/minecraft_mods && node --check wiki/data.js && node -e "const fs=require('fs');eval(fs.readFileSync('wiki/data.js','utf8')+';globalThis.W=WIKI_DATA');const s=W.systems;console.log(s.length===W.stats.systems?'count ok':'count MISMATCH '+s.length+' vs '+W.stats.systems, s.some(x=>x.id==='dark-magic')?'card ok':'card MISSING')"`. Expected: no syntax error, then `count ok card ok`. (If `count MISMATCH` and the two numbers differed already at 3e25d8b, set `systems` to the old number plus one and note it.)
  - `cd /home/user/minecraft_mods && grep -c "^## Dark Magic$" MOD_FEATURES_WIKI.md && grep -c "see Dark Magic" MOD_FEATURES_WIKI.md && grep -c "A Torn Soul" MOD_FEATURES_WIKI.md`. Expected: `1`, `3`, `2`.
  - `cd /home/user/minecraft_mods && git diff --stat -- MOD_FEATURES_WIKI.md wiki/data.js`. Expected: only these two files; in `MOD_FEATURES_WIKI.md` three changed lines (Expecto Patronum, Dark Mark, Insomnia) and the rest insertions, in `data.js` four changed lines (the count, Expecto Patronum, Morsmordre, Heal) and the card inserted.

- [ ] **Step 4: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 4: the feature wiki and the web wiki describe Dark Magic

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- MOD_FEATURES_WIKI.md wiki/data.js
```

---

### Task 4: The Hogwarts Guide, Dark Magic tab

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: the private `Section(String labelKey, List<Page> pages)` enum constructor and `Section.page(String, List<String>)` of `HogwartsGuideScreen`. Produces: `Section.DARK_MAGIC`.

- [ ] **Step 1: The section.** In `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`, find:

```java
        CREATURES("gui.hogwartsmagic.hogwarts_guide.section.creatures", List.of(
```

and replace it with:

```java
        DARK_MAGIC("gui.hogwartsmagic.hogwarts_guide.section.dark_magic", List.of(
                page("The weight of dark magic", List.of(
                        "Every curse marks the one who casts it. The Killing Curse, Fiendfyre, Morsmordre, the Imperius and Cruciatus Curses, Tenebris Flight, Sectumsempra, Dolohov's Curse and Serpensortia all darken the soul, whether they strike or miss.",
                        "The Killing Curse weighs heaviest and Serpensortia least. Ten Killing Curses will take a clean soul to Corrupted.",
                        "Killing an innocent with it weighs more still, and tearing your soul into a Horcrux most of all.",
                        "Your Grimoire names the state of your soul. Nobody else can read it."
                )),
                page("The five states of a soul", List.of(
                        "Clean, Shadowed, Tainted, Corrupted, Lost. A low bell tolls when you sink further; a chime sounds when you rise.",
                        "From Corrupted your mana runs a dark green-black.",
                        "A curse cast through a body you have taken with the Imperius Curse darkens you, never your puppet."
                )),
                page("What darkness costs", List.of(
                        "Shadowed: your Patronus takes its full form only half as often.",
                        "Tainted: it answers only as a shield, and Dementors come for the sleepless after three days instead of five.",
                        "Corrupted: the shield drinks more of your mana, Dementors seek you before all others, students keep their distance, and a faint smoke clings to you.",
                        "Lost: a heart of your life is gone, students attack on sight, and even your own house elf flees you. Yet your Patronus never fails."
                )),
                page("What darkness offers", List.of(
                        "From Corrupted, dark spells cost less mana, and the Dark Mark lends you its strength as it does the Death Eaters.",
                        "The Lost walk among Death Eaters unchallenged until they strike one. Cast the Mark into an open sky and they come to guard you for five minutes, though they remain what they are.",
                        "The Dark Lord himself is not so easily fooled."
                )),
                page("The way back", List.of(
                        "Darkness fades once you let the dark arts rest for ten minutes: one step for every half hour you spend in this world. From the very bottom it takes some fifty hours.",
                        "Heal another who is hurt and you may hasten it, though no more than once in each half hour.",
                        "While your Horcrux stands, your soul can never rise above Corrupted.",
                        "A Sneakoscope whistles at a Corrupted or Lost soul, cloak or no cloak."
                ))
        )),
        CREATURES("gui.hogwartsmagic.hogwarts_guide.section.creatures", List.of(
```

- [ ] **Step 2: The tab label.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find:

```json
  "gui.hogwartsmagic.hogwarts_guide.section.systems": "Wizarding Ways",
```

and replace it with:

```json
  "gui.hogwartsmagic.hogwarts_guide.section.systems": "Wizarding Ways",
  "gui.hogwartsmagic.hogwarts_guide.section.dark_magic": "Dark Magic",
```

- [ ] **Step 3: Check that every page fits.** Write `/tmp/dm4_pages.py`:

```python
import re, sys
src = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r'DARK_MAGIC\("gui\.hogwartsmagic\.hogwarts_guide\.section\.dark_magic", List\.of\((.*?)\n        \)\),', src, re.S)
if not m:
    print("section not found"); sys.exit(1)
bad = 0
for title, lines in re.findall(r'page\("([^"]*)", List\.of\((.*?)\)\)', m.group(1), re.S):
    items = re.findall(r'"((?:[^"\\]|\\.)*)"', lines)
    h = 16
    for it in items:
        n, w = 1, 0
        for wd in it.split():
            ww = 6 * len(wd) + (6 if w else 0)
            if w + ww > 380:
                n += 1; w = 6 * len(wd)
            else:
                w += ww
        h += n * 10 + 5
    ok = h <= 158
    bad += not ok
    print(f"{title}: {len(items)} items, {h}px {'ok' if ok else 'TOO TALL'}")
print("pages ok" if not bad else "pages too tall")
```

Then `python3 /tmp/dm4_pages.py src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`. Expected: five lines ending `ok` (136, 81, 126, 91, 126 px) and `pages ok`. (6 px per character over-estimates the vanilla font; 158 px leaves room above the page counter at `top + 226`.)

- [ ] **Step 4: Check JSON and build.** `cd /home/user/minecraft_mods && python3 -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); print('json ok')"`. Expected: `json ok`. `grep -c "section.dark_magic" src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`. Expected: `1` each. Gradle check, then the full build. Expected: BUILD SUCCESSFUL, BASE + 9, all green.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic Phase 4: a Dark Magic tab in the Hogwarts Guide

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 5: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 9 tests (plus any tests another phase committed since Task 1, which the ledger names), all green.
- [ ] **Step 2:** Safety greps (`<T1>` is the commit recorded in Task 1 Step 0):
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark`. Expected: nothing.
  - `git diff <T1> -- src/main/java/com/hogwartsmagic/spell/dark src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java | grep "^+" | grep -n "switch\|SubscribeEvent\|broadcastEntityEvent\|CriterionTrigger"`. Expected: nothing.
  - `grep -rn "DarkAdvancements.afterWrite" src/main/java`. Expected: exactly one line, in `DarkMagicData.java` (never in a login, sync or reload path).
  - `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: the line recorded in Task 1 Step 0 (unless the ledger records another phase changing it).
  - `for f in src/main/resources/data/hogwartsmagic/advancement/hp/torn_soul.json src/main/resources/data/hogwartsmagic/advancement/hp/redemption.json src/main/resources/assets/hogwartsmagic/lang/en_us.json; do python3 -c "import json,sys; json.load(open(sys.argv[1], encoding='utf-8')); print('json ok')" "$f"; done` and `node --check wiki/data.js`. Expected: `json ok` three times, no syntax error.
  - `git status --short`. Expected: clean except untracked `run/` output (and other agents' in-flight files, which are not this phase's).
- [ ] **Step 3:** Run the boot block (Global Constraints). Expected: `Done (`; step 4 prints nothing; no `Exception` or `ERROR` from `hogwartsmagic`; no server java process left afterwards.
- [ ] **Step 4:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 6: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 5. A survival world; two players (A and B) for rows 1 and 9. `/darkmagic set` sets the value; tiers: 0 Clean, 20 Shadowed, 40 Tainted, 60 Corrupted, 80 Lost. `/advancement revoke @s from hogwartsmagic:hp/torn_soul` revokes both (the child too).

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | A at 0, B watching chat | A: `/darkmagic set @s 80` | A: the "You are now Lost" line, then the goal toast "A Torn Soul". B: no chat line at all |
| 2 | Row 1 | Open the advancements screen (L) | "A Torn Soul" (a Horcrux icon) under "Borrowed Magic", "Redemption" (Essence of Dittany, challenge frame) under it, both with their descriptions |
| 3 | Row 1 | `/darkmagic set @s 79`, then 80 | No second toast |
| 4 | Row 3 | `/darkmagic set @s 40`, then 20 | No Redemption |
| 5 | Row 4 | `/darkmagic set @s 19` | The "You are now Clean" line, then the challenge toast "Redemption"; nothing in B's chat |
| 6 | Row 5, `/advancement revoke @s only hogwartsmagic:hp/redemption` | `/darkmagic set @s 10`, then 0 | No Redemption (the journey ended at Row 5); `/darkmagic set @s 85`, then 0: Redemption again |
| 7 | Revoke both; `darkMagicDecayTicks = 20` in `config/hogwartsmagic-common.toml` (it reloads); cast no dark spell for 10 minutes first | `/darkmagic set @s 81` (A Torn Soul), then wait | Darkness falls about 10 points every 10 s; when it reaches Clean, the Redemption toast (decay grants it too). Set the config back to 36000 |
| 8 | Revoke both, `/darkmagic set @s 85` (toast), then revoke both again | Relog | No toast on login; `/darkmagic add @s 1`: the A Torn Soul toast |
| 9 | Revoke both; A at 79 | A casts Avada Kedavra once (the cast pays 6) | A: tier line and A Torn Soul; B: nothing |
| 10 | Revoke both; `enableDarkMagic = false` (reloads) | `/darkmagic set @s 85`, then `/darkmagic set @s 0` | No toast either time. Turn it back on: `/darkmagic set @s 85` gives A Torn Soul |
| 11 | `/darkmagic set @s 85`, then revoke both; die and respawn | `/darkmagic set @s 0` | Redemption (the flag survived death) |
| 12 | A has a Horcrux, at 85 | `/darkmagic add @s -100` | Stops at 60 (the floor): no Redemption |
| 13 | Any | Open the Hogwarts Guide | A "Dark Magic" tab between "Wizarding Ways" and "Creatures"; five pages; every line fits above the "n / 5" counter at GUI scales 2, 3 and 4 and every other tab still works |
| 14 | A browser | Open `wiki/index.html` and `wiki/systems.html` | The "Dark Magic" system card with its features; the systems count on the index is one higher |
| 15 | A text viewer | Read the Dark Magic section of `MOD_FEATURES_WIKI.md` against your play of rows 1-12 and the Phase 2 and 3 checklists | It matches what the game does |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Phase 4.**
  - Bullet 1, "A Torn Soul" (reach Lost) and "Redemption" (Lost back to Clean), granted from code, `grantAllCriteria` or a `CriterionTrigger` decided: Task 1 (tests `reachingLostGrantsATornSoulAndRemembersIt`, `everyWriteAtLostStillGrantsATornSoul`, `cleanAfterLostGrantsRedemptionAndForgets`, `cleanWithoutHavingBeenLostGrantsNothing`, `theTiersBetweenKeepTheFlag`, `aFullJourneyGrantsEachOnceAndASecondNeedsLostAgain`), Task 2; rulings 1-8; rows 1-12. The persisted flag: ruling 4, row 11. Granted in `DarkMagicData.write` after the announcement, never on login or sync: ruling 2, Task 5 Step 2 grep, row 8. Toggle: test `theToggleOffGrantsNothingAndKeepsTheFlag`, row 10. Tree, icons, frames, lang: ruling 8-9, Task 2 Steps 3-5, row 2.
  - Bullet 2, `MOD_FEATURES_WIKI.md`, the web wiki and the Hogwarts Guide: Tasks 3-4; rulings 11-14; rows 13-15. The docs carry the gain table (decision 3 with the user rulings), deeds (decision 4), puppets (5), tiers (6), costs (7, with the Phase 2 replacements and "the Patronus never fails"), rewards (8, with the Phase 3 rulings on Voldemort, base raids and the escort), decay in 50 hours (9), capped redemption (10), privacy (11), UI (12), config (13), the command (15), NPCs (16), and Sneakoscope Part B.
  - Decision 11 (nothing names another player's tier): ruling 7, `announce_to_chat: false` checked in Task 2 Step 5, rows 1, 5 and 9.
- **Type consistency.** `DarkAdvancements.afterWrite(ServerPlayer, boolean, int)` is called from `write` with its `player` (`ServerPlayer`), `enabled` (`boolean`) and `newTier` (`int`) locals. `DarkAdvancementRules.afterWrite(boolean, int, boolean)` returns `Outcome`; `flagChanged(boolean)` takes the flag read with `CompoundTag.getBoolean`. The test's `DarkRules.effectiveTier(boolean, float)` returns `int`. Vanilla signatures were checked with `javap` against `forge-1.21-51.0.33_mapped_official_1.21.jar`: `PlayerAdvancements.award(AdvancementHolder, String)`, `getOrStartProgress(AdvancementHolder)`, `AdvancementProgress.isDone()`, `getRemainingCriteria()` (`Iterable<String>`), `ServerAdvancementManager.get(ResourceLocation)` (`AdvancementHolder`), `CriteriaTriggers.IMPOSSIBLE` (registered as `impossible`).
- **Compile check while planning.** `DarkAdvancementRules`, its 9 tests and `DarkAdvancements` (with one-line stand-ins for `ExampleMod.MODID` and `PlayerMagicData`) were compiled with `javac` against the Phase 1 `DarkRules` and the Forge 1.21 mapped jar: no errors. The 9 tests were run: all pass. The guide pages were checked with the Task 4 Step 3 script: all fit. Every anchor occurs exactly once in its file at 3e25d8b.
- **Placeholder scan:** none. Every string, path, frame, icon, number and command is given. `<T1>` is the commit recorded in Task 1 Step 0, not a placeholder in code.
- **Counts:** Task 1 adds 9 tests (`reachingLostGrantsATornSoulAndRemembersIt`, `everyWriteAtLostStillGrantsATornSoul`, `cleanAfterLostGrantsRedemptionAndForgets`, `cleanWithoutHavingBeenLostGrantsNothing`, `theTiersBetweenKeepTheFlag`, `theToggleOffGrantsNothingAndKeepsTheFlag`, `tiersOutsideTheRangeAreClamped`, `aFullJourneyGrantsEachOnceAndASecondNeedsLostAgain`, `idsAndKeyArePinned`). Total BASE + 9.
- **Concurrency:** Phases 2 and 3 edit `DarkMagicData.sync` and `DarkMagicData.floor`; this plan edits only the end of `write` and one class-javadoc sentence. In `en_us.json` it inserts after the `basilisk_bane.description` and `section.systems` lines, which no other plan edits. In the guide it inserts before `CREATURES`; Brewing edits the `POTIONS` section. The wiki anchors are lines no other Dark Magic or Brewing plan quotes.
- **Spec notes:** (1) The spec allows `grantAllCriteria` or a `CriterionTrigger`; a code grant needs a criterion nothing else fires, so the files use `minecraft:impossible` (ruling 1). (2) A vanilla `announce_to_chat` line would tell every player who reached Lost, against decision 11; both are off (ruling 7). (3) While a Horcrux exists its floor (60) keeps the player at Corrupted or darker, so Redemption needs the Horcrux gone (ruling 10). (4) Players who were Lost before Phase 4 get "A Torn Soul" on their next write at Lost; a Lost-to-Clean journey finished before Phase 4 is not recorded and earns nothing (rulings 3-4). (5) The roadmap's build line (`timeout 900 ./gradlew`) is superseded by the Global Constraints.
