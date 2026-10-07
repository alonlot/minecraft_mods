# Dark Magic Phase 1 (The Backbone) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** Every player gets one number, Darkness (0 to 100). A paid cast of one of nine dark spells adds to it, hit or miss; a cast forced through Imperio possession charges the puppeteer; creative and spectator players gain nothing. Darkness falls by 1 point per 30 minutes of online play once no dark spell has been cast for 10 minutes (100 to 0 in 50 hours). Five tiers (Clean, Shadowed, Tainted, Corrupted, Lost) are announced with one chat line and a sound when they change. The value survives death and relog, the Grimoire shows "Soul: <tier>", the mana bar turns dark green-black from Corrupted, `/darkmagic get|set|add` inspects and forces it, `enableDarkMagic` turns it all off, and a Sneakoscope now also whistles at a Corrupted or Lost player (Sneakoscope Part B). Costs and rewards per tier (Phases 2 and 3) are not in this plan; this plan gives them the tier query, the floor hook and the deed numbers.

**Architecture:**
- **Pure rules** (`spell/dark/DarkRules`): the dark set and gain table (keyed by spell id string, no `SpellType` import), the gain multiplier, the tier bounds (inclusive lower bounds), two-decimal rounding, the clamp, gain and loss with a floor, the 10-minute quiet window, the decay step with its online-time accumulator, the every-200-ticks check phase, who pays for a cast (body, puppeteer, nobody), the effective value with the toggle off, tier keys and colours, and the Phase 3 numbers (Horcrux +30 and floor 60, innocent Avada +3, redemption 1 point). Minecraft-free, test-first.
- **Server data** (`spell/dark/DarkMagicData`): reads and writes `PlayerPersisted/hogwartsmagic/DarkMagic` (float), `DarkMagicLastDarkCast` (game time) and `DarkMagicDecayProgress` (online quiet ticks toward the next point). One `write` path syncs the client and announces a tier change. `onDarkCastPaid(body, spell)` is the single cast hook; `tickDecay(player)` is the decay hook; `getTier(ServerPlayer)` and `floor(ServerPlayer)` are what Phases 2 and 3 read and change.
- **Network:** one new S2C packet `SyncDarkMagicPacket(float)`, protocol 6 to 7. It carries the effective value (0 with the toggle off), so the client needs no config values. Client side: `ClientSideBridge` to `ClientRuntimeBridge` to `client/DarkMagicClientState` (a static float, cleared on logout).
- **Hooks:** two lines in `SpellPackets.CastSpellPacket.castAs` (after the main mana charge, and in the Fiendfyre channel-start block), three sync lines in `ModEvents` (login, respawn, dimension change), one decay line in `ModEvents.onPlayerTick`.
- **UI:** the Grimoire draws "Soul: <tier>" right-aligned in its title row; `ManaOverlay` picks its fill colour from `DarkRules.manaBarColor(tier)`. No enum switch anywhere.
- **Sneakoscope Part B:** `SneakoscopeRules.Source.DARK_PLAYER(Reach.NEAR)`, a pure `detectsPlayer` rule, and one probe (`ServerPlayer.class` with a filter) in `SneakoscopeSensor.PROBES`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-07-dark-magic-roadmap.md`: "Decisions" 1-16 as they apply to Phase 1 (the gain table in decision 3 with the user rulings, the tiers in 6, decay in 9, the client sync in 11, UI in 12, config in 13, existing players in 14, the command in 15, NPCs in 16), "Phase 1: The backbone", "User answers", "Global constraints". `docs/superpowers/specs/2026-10-07-sneakoscope-design.md`: decision 2 (the player row), decision 10 (`sneakoscopeDetectsPlayers`), "Part B". Phases 2-4 of the roadmap are out of scope.

## Requirements (Phase 1 of the spec)

1. Pure `spell/dark/DarkRules`, test-first: the dark set and gain table, the deed bonuses, the tier bounds, the decay step, the floor, the clamp and the redemption amount (Phase 1 bullet 1; decisions 2, 3, 4, 6, 9, 10).
2. Darkness is a float 0..100 at `PlayerPersisted/<modid>/DarkMagic` with `DarkMagicLastDarkCast` (game time); it survives death like mastery; `DarkMagicData` owns read, write, add, set, tier and sync (decision 1; Phase 1 bullet 2).
3. Gain when a dark cast is paid, hit or miss: the main mana block in `castAs` and the Fiendfyre channel-start block; the gain table of decision 3 (Avada 6, Fiendfyre 2.5, Morsmordre 2.5, Imperio 1.5, Crucio 1.2, Tenebris Flight 0.6, Sectumsempra 0.4, Dolohov 0.4, Serpensortia 0.3) times `darkMagicGainMultiplier` (decision 3; Phase 1 bullet 3).
4. A cast forced through possession charges the puppeteer, never the victim; creative and spectator players gain nothing; NPCs have no Darkness (decisions 5, 16).
5. Tiers: Clean 0-19, Shadowed 20-39, Tainted 40-59, Corrupted 60-79, Lost 80-100; a tier change sends one chat line and a sound (a low bell going up, a chime going down) (decision 6).
6. Decay: with no dark cast for 10 minutes, -1 per 36000 ticks of online play, at every tier, never below the floor; checked once every 200 ticks in `ModEvents.onPlayerTick`, no per-tick persistent-data reads (decision 9; Phase 1 bullet 4).
7. Sync on login, respawn and dimension change; `SyncDarkMagicPacket(float)`; protocol 7; the client sees only its own value; nothing names another player's tier (decision 11; Phase 1 bullet 5).
8. Config (COMMON): `enableDarkMagic` (true; off means no gain and every tier reads 0), `darkMagicGainMultiplier` (1.0), `darkMagicDecayTicks` (36000), `darkMagicRedemptionTicks` (36000), and the server's values reach the client as needed (decision 13; Phase 1 bullet 6).
9. `/darkmagic get|set|add <player> [value]`, op level 2 (decision 15; Phase 1 bullet 6).
10. The Grimoire "Soul" line (tier name, white to dark green) and the mana bar dark green-black from tier 3; no new HUD bar (decision 12; Phase 1 bullet 7).
11. Existing players start at 0 (decision 14).
12. Sneakoscope Part B: players at Corrupted or Lost are a new `Source.DARK_PLAYER(Reach.NEAR)` and one probe; never the holder; never with `enableDarkMagic` off or `sneakoscopeDetectsPlayers` off; creative and spectator players never detected; the Cloak and Polyjuice do not hide you (Sneakoscope decisions 2, 10, Part B).
13. Done when: dark casts move you up the tiers with a message at each change, decay brings you down, the number survives death and relog, the Grimoire shows your tier, the toggle turns it all off, `runServer` boots clean; and a Corrupted player under the Cloak and under Polyjuice sets off a Sneakoscope while a Tainted player does not.

## Rulings made while planning

1. **The dark set is keyed by spell id string** (`"avada_kedavra"`, `"fiendfyre"`, `"morsmordre"`, `"imperio"`, `"crucio"`, `"tenebris_flight"`, `"sectumsempra"`, `"dolohov_curse"`, `"serpensortia"`, checked against `SpellType`), so `DarkRules` never imports `SpellType` (whose constructor touches `ParticleTypes` and cannot load in a plain JUnit run).
2. **Precision:** Darkness is kept to two decimals. Every value that is stored or compared goes through `DarkRules.sanitize`: NaN and infinities become 0, then clamp to 0..100, then round to 0.01 (`Math.round(v * 100.0) / 100.0f`). Gains are rounded the same way after the multiplier. So 200 Serpensortia casts land on exactly 60.00, and the cast counts in the decision 3 table hold exactly (tested).
3. **Tier bounds are inclusive lower bounds:** 0, 20, 40, 60, 80. 19.99 is Clean, 20.00 is Shadowed, 59.99 is Tainted, 60.00 is Corrupted, 80.00 to 100.00 is Lost.
4. **Order of operations.** Gain: sanitize the old value, add, sanitize (clamp at 100). Loss (decay, a negative `add`, and later redemption): sanitize, and if the value is at or below the floor do nothing; else subtract, raise to the floor, sanitize. The floor stops a loss; it never raises a value that is already below it. `set` (command only) writes the sanitized value and ignores the floor.
5. **Decay counts online quiet time.** `DarkMagicDecayProgress` (int ticks) grows by 200 at each check while the player is "quiet" (game time at least 12000 ticks after `DarkMagicLastDarkCast`, or the clock is behind it) and above the floor; each full `darkMagicDecayTicks` removes 1 point and keeps the remainder. Not quiet, or at or below the floor: progress resets to 0. A paid dark cast sets `DarkMagicLastDarkCast` to now and progress to 0. The quiet window is game time (as the spec stores it), so on a server it also passes while you are offline; the 30-minute steps count only online ticks (the player tick runs them).
6. **Lowering the config never dumps points:** before adding, progress is clamped to `decayTicks - 1`, so a smaller `darkMagicDecayTicks` drops at most one point per check, unless the period is under 200 ticks (a tester value), where a check drops `200 / period` points.
7. **The decay check** runs when `floorMod(player.tickCount + player.getId(), 200) == 0` (`DarkRules.dueToDecayCheck`), the first thing in `DarkMagicData.tickDecay`, before any config or persistent-data read; players are spread over the 200 ticks. It runs in every game mode. With `enableDarkMagic` off it does nothing (the value is frozen).
8. **The cast hook** is one call, `DarkMagicData.onDarkCastPaid(player, spell)`, placed after the main mana charge in `castAs` (after `MagicWandItem.rollManaRefund(player, wand, cost)`, so before the self-cast, Tenebris and beam branches) and in the Fiendfyre channel-start block after the startup mana is taken. A Fiendfyre tap (a beam, main block) and a Fiendfyre hold (a channel start) each count as one cast of 2.5. The Avada windup gains nothing; its re-entry with the ready flag goes through the main block and gains 6 then. Imperio refused by `ImperioThralls.mayCast` gains nothing (it returns before the mana). The mana refund roll does not undo the gain.
9. **Who pays** (`DarkRules.payer`): while `PossessionManager.castingPuppeteer` is set (a puppet cast, or an Avada windup finishing for a puppet), the puppeteer pays (looked up online; offline means nobody); otherwise a body that `PossessionManager.isForcedCaster` (a puppet, or 100 ticks after the session) pays nothing; otherwise the body pays. The payer must not be creative or spectator (`DarkRules.earns`).
10. **The toggle off** (`enableDarkMagic = false`): no gain, no decay, no tier message, `getTier` is 0 and the client is sent 0. The stored number is kept, so turning it back on restores it. `/darkmagic` still reads and writes the stored number and says the toggle is off.
11. **No config packet.** The client never needs a Dark Magic config value: the server sends the effective value (0 when off). After a config reload, `DarkMagicData.broadcastAfterReload()` resends every online player's value on the server thread (next to the mastery broadcast in `Config.onLoad`). `SyncMasteryConfigPacket` stays unchanged.
12. **The client copy** is `client/DarkMagicClientState` (a volatile static float), filled by `SyncDarkMagicPacket` through the bridge and cleared in `MasteryConfigClientEvents.onLoggingOut`. It is not written into the client player's persistent data (a static survives respawn and dimension change without a race).
13. **New storage key** `DarkMagicDecayProgress` next to the spec's two keys. Missing keys read as 0, so existing players start Clean (decision 14). Nothing is backfilled. `onPlayerClone` is not changed: `HorcruxData.copyPersistent` already copies the whole `PlayerPersisted` compound.
14. **Tier change announcement** (in `DarkMagicData.write`, on any change of the effective tier: gain, decay, command): one chat line to that player only (`sendSystemMessage`), "Your soul darkens. You are now <Tier>." (dark green) or "Your soul lightens. You are now <Tier>." (gray), the tier name in its tier colour; a jump of several tiers gives one line naming the new tier. Sound to that player only (`playNotifySound`, so nobody nearby learns a tier): up `SoundEvents.BELL_BLOCK` volume 0.8 pitch 0.5 (a low bell); down `SoundEvents.AMETHYST_BLOCK_CHIME` volume 1.0 pitch 1.2 (a chime). Nothing on login, sync or a toggle change.
15. **Tier colours (ARGB):** Clean `0xFFFFFFFF`, Shadowed `0xFFCDE8CD`, Tainted `0xFF98CC98`, Corrupted `0xFF5CAE5C`, Lost `0xFF2E8B3A` (white to dark green, all readable on the Grimoire's `0xF0140A1E` panel). Mana bar: `0xFF00AAFF` (unchanged) below Corrupted, `0xFF1B3A22` (dark green-black) at Corrupted and Lost. The number text on the bar stays white.
16. **The Grimoire "Soul" line** is right-aligned in the title row (`panelTop + 8`, right edge `panelLeft + PANEL_WIDTH - 8`), "Soul: <Tier>" in the tier colour. It shows the tier only, never the number. With the toggle off it reads "Soul: Clean".
17. **Lang keys:** `gui.hogwartsmagic.dark.tier.{clean,shadowed,tainted,corrupted,lost}`, `gui.hogwartsmagic.dark.tier_up`, `gui.hogwartsmagic.dark.tier_down`, `gui.hogwartsmagic.grimoire.soul`. `DarkRules.tierKey(tier)` builds the first five from one name array (index by tier, no switch). The command's own replies are English literals, like `/spellmastery`.
18. **Config** (COMMON, after the Sneakoscope values): `sneakoscopeDetectsPlayers` true; `enableDarkMagic` true; `darkMagicGainMultiplier` 1.0 (0.0..10.0); `darkMagicDecayTicks` 36000 (20..10000000); `darkMagicRedemptionTicks` 36000 (20..10000000). `darkMagicRedemptionTicks` is defined now (decision 13 and the Phase 1 config bullet) and read only by Phase 3.
19. **Phase 3 hooks in Phase 1:** `DarkRules` holds `AVADA_INNOCENT_BONUS = 3`, `HORCRUX_BONUS = 30`, `HORCRUX_FLOOR = 60`, `NO_FLOOR = 0`, `REDEMPTION_AMOUNT = 1`, `DEFAULT_REDEMPTION_TICKS = 36000` (tested, unused). `DarkMagicData.add(player, delta)` is the deed and redemption entry (goes through the floor; does not reset the quiet timer). `DarkMagicData.floor(player)` returns `NO_FLOOR`; Phase 3 makes it return `HORCRUX_FLOOR` while the player's Horcrux exists. `DarkMagicData.getTier(ServerPlayer)` is what every Phase 2 cost reads.
20. **The command:** `/darkmagic get <player>`, `/darkmagic set <player> <value 0..100>`, `/darkmagic add <player> <value -100..100>`, op level 2. `set` ignores the floor; `add` goes through it. Neither touches the quiet timer. Each reply: "<name>: Darkness 63.50 (Corrupted)", plus ", floor 60" when a floor applies, plus " - enableDarkMagic is off, so it reads as Clean" on `get` when off. `set` and `add` announce a tier change like play does (testers hear it).
21. **The packet** is appended at the end of `SpellPackets.register()` (after `LockPickStatePacket`) and ignores anything a client sends (the `SyncMasteryConfigPacket` guard). Protocol 6 to 7: a client from before this phase cannot join a server from after it.
22. **Sneakoscope Part B:** `Source.DARK_PLAYER(Reach.NEAR)` (16 blocks, the config `sneakoscopeRange`). Pure `SneakoscopeRules.playersDetectable(darkMagicOn, detectsPlayers)` and `detectsPlayer(darkMagicOn, detectsPlayers, creativeOrSpectator, tier)`, with `DARK_PLAYER_MIN_TIER = 3` (a test pins it to `DarkRules.CORRUPTED`; `SneakoscopeRules` keeps its no-mod-import rule). `SneakoscopeSensor.Probe` gains a `Predicate<Entity>` filter; the three Part A probes use "any"; the new probe is `ServerPlayer.class` filtered by `detectsPlayer` with `DarkMagicData.getTier`. The whole player probe is skipped when `playersDetectable` is false. The holder is excluded by the existing `e != holder`; dead players by `isAlive()`; invisibility and Polyjuice are never checked. `SneakoscopeCarried` and `SneakoscopeBlockEntity` do not change.
23. **Sneakoscope text:** the tooltip becomes "Spins and whistles when a Death Eater, a Dementor, the Dark Mark or a dark wizard is near. Place it for a redstone signal." One bullet in the `MOD_FEATURES_WIKI.md` Sneakoscope section and one feature in its `wiki/data.js` entry. The full Dark Magic docs are Phase 4.

## Global Constraints

- Server authoritative. `spell/dark/`, `command/DarkMagicCommand.java`, `sneakoscope/` and every common or server file this plan touches never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code goes only into `client/` (`DarkMagicClientState`, `ClientRuntimeBridge`, `MasteryConfigClientEvents`, `ManaOverlay`, `SpellCompendiumScreen`) and is reached through `util/ClientSideBridge`.
- No `switch` on an enum anywhere in the new code. Use if-chains, `==` and arrays indexed by tier.
- No new hurt, death or effect handler, and no new `@SubscribeEvent`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- Never load a chunk. The Sneakoscope probe uses `getEntitiesOfClass` only.
- `en_us.json` and every JSON file touched stay valid JSON. Validate with `python3 -c "import json;json.load(open('<file>',encoding='utf-8'))"`.
- Build and test (cloud):
  - First `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If a build is running, wait (poll every 30 s, at most 20 min).
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- Dedicated-server boot (Task 2 and Task 7), "the boot block":
  1. `cd /home/user/minecraft_mods && mkdir -p run && echo "eula=true" > run/eula.txt && rm -f run/logs/latest.log`
  2. `(timeout 600 bash ./gradlew runServer --console=plain --max-workers=2 > /tmp/srv.log 2>&1 &)`
  3. Poll every 5 s, up to 10 min: `grep -E "Done \(|Exception|Crash" run/logs/latest.log` until it prints something.
  4. `grep -n "Done (\|Exception\|ERROR" run/logs/latest.log | head -40`
  5. `pkill -f runServer; pkill -f "net.minecraftforge"; sleep 5; pgrep -fa java | grep -v GradleDaemon` must print no server process.
- Commit per task. Stage only the named files; never `.claude/settings.local.json` or `.superpowers/`. Never push. No apostrophes in heredoc bodies. Every commit message ends with a blank line and exactly:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
  ```
  Write the message with a heredoc to `/tmp/msg.txt`, `git add` the new files, then commit by path: `git commit -F /tmp/msg.txt -- <paths>`.
- **383 tests at 996f638**, all green. They stay unchanged and green. Task 1 adds 21 (404), Task 6 adds 2 (406 total).
- Line numbers drift. Every edit names the exact text to find, quoted from 996f638 (or from an earlier task of this plan, which says so).
- Another planner is planning Brewing Phase 2 at the same time. If its implementation lands first and changes a quoted anchor (most likely the end of `en_us.json`, the end of `SpellPackets.register()`, or the protocol number), find the same spot by its text and keep both changes; if the protocol was already bumped past 6, bump it once more instead of setting 7.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java` | Pure: dark set, gain table, tiers, rounding, clamp, floor, decay, payer, colours, Phase 3 numbers | 1 |
| Create `src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java` | 21 tests | 1 |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | Five values, reload broadcast | 2 |
| Create `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java` | Storage, write, sync, announce, cast hook, decay tick | 2 |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `SyncDarkMagicPacket`, protocol 7 | 2 |
| Create `src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java` | The client copy | 2 |
| Modify `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` | `syncDarkMagic` action | 2 |
| Modify `src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java` | Clear on logout | 2 |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Sync on login, respawn, dimension change (T2); decay call (T3) | 2, 3 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Tier names, tier lines, Soul line (T2); Sneakoscope tooltip (T6) | 2, 6 |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | The two cast hooks | 3 |
| Create `src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java` | `/darkmagic get|set|add` | 4 |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Register the command | 4 |
| Modify `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java` | Soul line | 5 |
| Modify `src/main/java/com/hogwartsmagic/client/ManaOverlay.java` | Bar tint | 5 |
| Modify `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java` | `DARK_PLAYER`, player rules | 6 |
| Modify `src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java` | 2 tests | 6 |
| Modify `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java` | Probe filter, the player probe | 6 |
| Modify `MOD_FEATURES_WIKI.md`, `wiki/data.js` | One Sneakoscope line each | 6 |

Untouched on purpose: `SpellType` (no dark field, spec decision 2), `SpellCosts` (the discount is Phase 3), `PossessionManager`, `MindControl`, `AvadaWindup` (read only), `onPlayerClone` (ruling 13), `SyncMasteryConfigPacket` and `MasteryConfig` (ruling 11), `SneakoscopeCarried`, `SneakoscopeBlockEntity` (ruling 22), `DementorEntity`, `StudentEntity`, `WizardEntity`, `DarkMarkEntity`, `HorcruxData`, `HealSpell` (Phases 2 and 3).

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2, T3, T4, T5, T6 | `DarkRules` constants `MIN_DARKNESS`, `MAX_DARKNESS`, `CLEAN`, `SHADOWED`, `TAINTED`, `CORRUPTED`, `LOST`, `MANA_BAR_COLOR`, `MANA_BAR_DARK_COLOR`, `QUIET_TICKS`, `DECAY_STEP`, `DEFAULT_DECAY_TICKS`, `DECAY_CHECK_INTERVAL_TICKS`, `NO_FLOOR`, `HORCRUX_FLOOR`, `HORCRUX_BONUS`, `AVADA_INNOCENT_BONUS`, `REDEMPTION_AMOUNT`, `DEFAULT_REDEMPTION_TICKS`; `enum Payer {BODY, PUPPETEER, NOBODY}`; `record Decay(float darkness, int progressTicks)`; `darkSpellIds()`, `isDark(String)`, `baseGain(String)`, `gain(String,double)`, `sanitize(float)`, `tier(float)`, `tierLowerBound(int)`, `afterGain(float,float)`, `afterLoss(float,float,float)`, `add(float,float,float)`, `quiet(long,long)`, `decay(float,int,int,boolean,float,int)`, `dueToDecayCheck(long,long)`, `payer(boolean,boolean)`, `earns(boolean,boolean)`, `effective(boolean,float)`, `effectiveTier(boolean,float)`, `tierChange(int,int)`, `tierKey(int)`, `tierColor(int)`, `manaBarColor(int)` | names identical |
| T2 → T3 | `DarkMagicData.onDarkCastPaid(ServerPlayer, SpellType)`, `DarkMagicData.tickDecay(ServerPlayer)` | written in T2 |
| T2 → T4 | `DarkMagicData.getStored(Player)`, `set(ServerPlayer,float)`, `add(ServerPlayer,float)`, `floor(ServerPlayer)`; `Config.isDarkMagicEnabled()` | written in T2 |
| T2 → T5 | `DarkMagicClientState.tier()` (client package); lang key `gui.hogwartsmagic.grimoire.soul` | written in T2 |
| T2 → T6 | `DarkMagicData.getTier(ServerPlayer)`; `Config.isSneakoscopeDetectsPlayers()`, `Config.isDarkMagicEnabled()` | written in T2 |
| T2 bridge | Action string `"syncDarkMagic"` in `SyncDarkMagicPacket.handle` and in `ClientRuntimeBridge.handlePacket` | identical |
| T2 lang | Anchor: the last entry at 996f638, `"item.hogwartsmagic.sneakoscope.tooltip"` | quoted in T2 step 8 |
| T6 lang | Anchor: the same tooltip line, with the trailing comma T2 gave it | quoted in T6 step 5 |

---

### Task 1: DarkRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java`

**Interfaces.** Consumes: nothing. Produces: everything in the T1 row of the pre-flight table.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java`:

```java
package com.hogwartsmagic.spell.dark;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DarkRulesTest {
    private static final float EPS = 1.0e-4f;

    private static int castsToCorrupted(String spellId) {
        float darkness = 0.0f;
        int casts = 0;
        while (DarkRules.tier(darkness) < DarkRules.CORRUPTED) {
            darkness = DarkRules.afterGain(darkness, DarkRules.gain(spellId, 1.0));
            casts++;
            if (casts > 1000) {
                fail(spellId + " never reached Corrupted");
            }
        }
        return casts;
    }

    private static long quietTicksToDecay(float from, float to) {
        DarkRules.Decay d = new DarkRules.Decay(from, 0);
        long ticks = 0L;
        while (d.darkness() > to) {
            d = DarkRules.decay(d.darkness(), d.progressTicks(), DarkRules.DECAY_CHECK_INTERVAL_TICKS, true,
                    DarkRules.NO_FLOOR, DarkRules.DEFAULT_DECAY_TICKS);
            ticks += DarkRules.DECAY_CHECK_INTERVAL_TICKS;
            if (ticks > 10_000_000L) {
                fail("decay never reached " + to);
            }
        }
        return ticks;
    }

    @Test
    void darkSetIsTheNineSpellsOfTheTable() {
        assertEquals(Set.of("avada_kedavra", "fiendfyre", "morsmordre", "imperio", "crucio", "tenebris_flight",
                "sectumsempra", "dolohov_curse", "serpensortia"), DarkRules.darkSpellIds());
        assertTrue(DarkRules.isDark("crucio"));
        assertFalse(DarkRules.isDark("obliviate"));
        assertFalse(DarkRules.isDark("confundo"));
        assertFalse(DarkRules.isDark("expecto_patronum"));
        assertFalse(DarkRules.isDark(null));
    }

    @Test
    void gainTableMatchesTheSpec() {
        assertEquals(6.0f, DarkRules.baseGain("avada_kedavra"), EPS);
        assertEquals(2.5f, DarkRules.baseGain("fiendfyre"), EPS);
        assertEquals(2.5f, DarkRules.baseGain("morsmordre"), EPS);
        assertEquals(1.5f, DarkRules.baseGain("imperio"), EPS);
        assertEquals(1.2f, DarkRules.baseGain("crucio"), EPS);
        assertEquals(0.6f, DarkRules.baseGain("tenebris_flight"), EPS);
        assertEquals(0.4f, DarkRules.baseGain("sectumsempra"), EPS);
        assertEquals(0.4f, DarkRules.baseGain("dolohov_curse"), EPS);
        assertEquals(0.3f, DarkRules.baseGain("serpensortia"), EPS);
        assertEquals(0.0f, DarkRules.baseGain("stupefy"), EPS);
        assertEquals(0.0f, DarkRules.baseGain(null), EPS);
    }

    @Test
    void castsFromCleanToCorruptedMatchTheTable() {
        assertEquals(10, castsToCorrupted("avada_kedavra"));
        assertEquals(24, castsToCorrupted("fiendfyre"));
        assertEquals(24, castsToCorrupted("morsmordre"));
        assertEquals(40, castsToCorrupted("imperio"));
        assertEquals(50, castsToCorrupted("crucio"));
        assertEquals(100, castsToCorrupted("tenebris_flight"));
        assertEquals(150, castsToCorrupted("sectumsempra"));
        assertEquals(150, castsToCorrupted("dolohov_curse"));
        assertEquals(200, castsToCorrupted("serpensortia"));
    }

    @Test
    void gainMultiplierScalesTheWholeTable() {
        assertEquals(12.0f, DarkRules.gain("avada_kedavra", 2.0), EPS);
        assertEquals(3.0f, DarkRules.gain("avada_kedavra", 0.5), EPS);
        assertEquals(0.15f, DarkRules.gain("serpensortia", 0.5), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", 0.0), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", -1.0), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", Double.NaN), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", Double.POSITIVE_INFINITY), EPS);
        assertEquals(0.0f, DarkRules.gain("stupefy", 2.0), EPS);
    }

    @Test
    void tierLowerBoundsAreInclusive() {
        assertEquals(DarkRules.CLEAN, DarkRules.tier(0.0f));
        assertEquals(DarkRules.CLEAN, DarkRules.tier(19.99f));
        assertEquals(DarkRules.SHADOWED, DarkRules.tier(20.0f));
        assertEquals(DarkRules.SHADOWED, DarkRules.tier(39.99f));
        assertEquals(DarkRules.TAINTED, DarkRules.tier(40.0f));
        assertEquals(DarkRules.TAINTED, DarkRules.tier(59.99f));
        assertEquals(DarkRules.CORRUPTED, DarkRules.tier(60.0f));
        assertEquals(DarkRules.CORRUPTED, DarkRules.tier(79.99f));
        assertEquals(DarkRules.LOST, DarkRules.tier(80.0f));
        assertEquals(DarkRules.LOST, DarkRules.tier(100.0f));
        assertEquals(20.0f, DarkRules.tierLowerBound(DarkRules.SHADOWED), EPS);
        assertEquals(60.0f, DarkRules.tierLowerBound(DarkRules.CORRUPTED), EPS);
        assertEquals(80.0f, DarkRules.tierLowerBound(99), EPS);
        assertEquals(0.0f, DarkRules.tierLowerBound(-1), EPS);
    }

    @Test
    void sanitizeClampsRoundsAndRejectsGarbage() {
        assertEquals(0.0f, DarkRules.sanitize(-5.0f), EPS);
        assertEquals(100.0f, DarkRules.sanitize(150.0f), EPS);
        assertEquals(0.0f, DarkRules.sanitize(Float.NaN), EPS);
        assertEquals(0.0f, DarkRules.sanitize(Float.POSITIVE_INFINITY), EPS);
        assertEquals(0.0f, DarkRules.sanitize(Float.NEGATIVE_INFINITY), EPS);
        assertEquals(12.35f, DarkRules.sanitize(12.346f), EPS);
        assertEquals(12.34f, DarkRules.sanitize(12.344f), EPS);
        assertEquals(60.0f, DarkRules.sanitize(59.999998f), 0.0f);
    }

    @Test
    void gainAddsAndStopsAtOneHundred() {
        assertEquals(16.0f, DarkRules.afterGain(10.0f, 6.0f), EPS);
        assertEquals(100.0f, DarkRules.afterGain(99.0f, 6.0f), EPS);
        assertEquals(10.0f, DarkRules.afterGain(10.0f, -3.0f), EPS);
        assertEquals(10.0f, DarkRules.afterGain(10.0f, Float.NaN), EPS);
        assertEquals(10.0f, DarkRules.afterGain(10.0f, Float.POSITIVE_INFINITY), EPS);
    }

    @Test
    void lossStopsAtTheFloorAndNeverRaisesToIt() {
        assertEquals(60.0f, DarkRules.afterLoss(65.0f, 10.0f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(55.0f, DarkRules.afterLoss(55.0f, 1.0f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(0.0f, DarkRules.afterLoss(5.0f, 10.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(49.0f, DarkRules.afterLoss(50.0f, 1.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.afterLoss(50.0f, -1.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.afterLoss(50.0f, Float.NaN, DarkRules.NO_FLOOR), EPS);
    }

    @Test
    void addPicksGainOrLossBySign() {
        assertEquals(53.0f, DarkRules.add(50.0f, 3.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(49.0f, DarkRules.add(50.0f, -1.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(60.0f, DarkRules.add(61.0f, -5.0f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.add(50.0f, 0.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.add(50.0f, Float.NaN, DarkRules.NO_FLOOR), EPS);
    }

    @Test
    void quietAfterTenMinutesWithoutADarkCast() {
        assertTrue(DarkRules.quiet(12_000L, 0L));
        assertFalse(DarkRules.quiet(11_999L, 0L));
        assertTrue(DarkRules.quiet(112_000L, 100_000L));
        assertFalse(DarkRules.quiet(100_500L, 100_000L));
        // The clock is behind the last cast (another world's save): never strand the player.
        assertTrue(DarkRules.quiet(100L, 5_000L));
    }

    @Test
    void decayTakesOnePointPerThirtyMinutesOfQuietPlay() {
        DarkRules.Decay d = new DarkRules.Decay(50.0f, 0);
        for (int i = 0; i < 179; i++) {
            d = DarkRules.decay(d.darkness(), d.progressTicks(), 200, true, DarkRules.NO_FLOOR, 36_000);
        }
        assertEquals(50.0f, d.darkness(), EPS);
        assertEquals(35_800, d.progressTicks());
        d = DarkRules.decay(d.darkness(), d.progressTicks(), 200, true, DarkRules.NO_FLOOR, 36_000);
        assertEquals(49.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void decayFromOneHundredToZeroTakesFiftyHours() {
        assertEquals(3_600_000L, quietTicksToDecay(100.0f, 0.0f)); // 50 hours
        assertEquals(2_160_000L, quietTicksToDecay(60.0f, 0.0f));  // Corrupted to Clean-at-zero: 30 hours
        assertEquals(DarkRules.CLEAN, DarkRules.tier(DarkRules.decay(20.5f, 35_900, 200, true,
                DarkRules.NO_FLOOR, 36_000).darkness()));
    }

    @Test
    void noDecayWhileADarkCastIsRecent() {
        DarkRules.Decay d = DarkRules.decay(50.0f, 1_000, 200, false, DarkRules.NO_FLOOR, 36_000);
        assertEquals(50.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void decayStopsAtTheFloor() {
        DarkRules.Decay d = DarkRules.decay(60.5f, 35_900, 200, true, DarkRules.HORCRUX_FLOOR, 36_000);
        assertEquals(60.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
        d = DarkRules.decay(60.0f, 5_000, 200, true, DarkRules.HORCRUX_FLOOR, 36_000);
        assertEquals(60.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
        d = DarkRules.decay(40.0f, 0, 200, true, DarkRules.HORCRUX_FLOOR, 36_000);
        assertEquals(40.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
        d = DarkRules.decay(0.0f, 0, 200, true, DarkRules.NO_FLOOR, 36_000);
        assertEquals(0.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void aLoweredDecayPeriodNeverDropsManyPointsAtOnce() {
        DarkRules.Decay d = DarkRules.decay(50.0f, 30_000, 200, true, DarkRules.NO_FLOOR, 200);
        assertEquals(49.0f, d.darkness(), EPS);
        assertEquals(199, d.progressTicks());
        // A tester period under one check drops one point per period inside the check.
        d = DarkRules.decay(50.0f, 0, 200, true, DarkRules.NO_FLOOR, 20);
        assertEquals(40.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void decayCheckRunsOnceEveryTwoHundredTicks() {
        for (long id : new long[]{0L, 7L, 199L, 12_345L, -3L}) {
            int due = 0;
            for (long t = 1_000L; t < 1_200L; t++) {
                if (DarkRules.dueToDecayCheck(t, id)) {
                    due++;
                }
            }
            assertEquals(1, due, "id " + id);
        }
        assertEquals(200, DarkRules.DECAY_CHECK_INTERVAL_TICKS);
    }

    @Test
    void puppetCastsChargeThePuppeteerAndCreativeEarnsNothing() {
        assertEquals(DarkRules.Payer.PUPPETEER, DarkRules.payer(true, true));
        assertEquals(DarkRules.Payer.PUPPETEER, DarkRules.payer(true, false));
        assertEquals(DarkRules.Payer.NOBODY, DarkRules.payer(false, true));
        assertEquals(DarkRules.Payer.BODY, DarkRules.payer(false, false));
        assertTrue(DarkRules.earns(false, false));
        assertFalse(DarkRules.earns(true, false));
        assertFalse(DarkRules.earns(false, true));
    }

    @Test
    void disabledDarkMagicReadsAsClean() {
        assertEquals(0.0f, DarkRules.effective(false, 90.0f), EPS);
        assertEquals(DarkRules.CLEAN, DarkRules.effectiveTier(false, 90.0f));
        assertEquals(90.0f, DarkRules.effective(true, 90.0f), EPS);
        assertEquals(DarkRules.LOST, DarkRules.effectiveTier(true, 90.0f));
    }

    @Test
    void tierChangeDirection() {
        assertEquals(1, DarkRules.tierChange(DarkRules.CLEAN, DarkRules.SHADOWED));
        assertEquals(1, DarkRules.tierChange(DarkRules.CLEAN, DarkRules.LOST));
        assertEquals(-1, DarkRules.tierChange(DarkRules.CORRUPTED, DarkRules.TAINTED));
        assertEquals(0, DarkRules.tierChange(DarkRules.TAINTED, DarkRules.TAINTED));
    }

    @Test
    void tierKeysAndColours() {
        assertEquals("gui.hogwartsmagic.dark.tier.clean", DarkRules.tierKey(DarkRules.CLEAN));
        assertEquals("gui.hogwartsmagic.dark.tier.shadowed", DarkRules.tierKey(DarkRules.SHADOWED));
        assertEquals("gui.hogwartsmagic.dark.tier.tainted", DarkRules.tierKey(DarkRules.TAINTED));
        assertEquals("gui.hogwartsmagic.dark.tier.corrupted", DarkRules.tierKey(DarkRules.CORRUPTED));
        assertEquals("gui.hogwartsmagic.dark.tier.lost", DarkRules.tierKey(DarkRules.LOST));
        assertEquals("gui.hogwartsmagic.dark.tier.clean", DarkRules.tierKey(-1));
        assertEquals("gui.hogwartsmagic.dark.tier.lost", DarkRules.tierKey(9));
        assertEquals(0xFFFFFFFF, DarkRules.tierColor(DarkRules.CLEAN));
        assertEquals(0xFF2E8B3A, DarkRules.tierColor(DarkRules.LOST));
        assertEquals(DarkRules.MANA_BAR_COLOR, DarkRules.manaBarColor(DarkRules.TAINTED));
        assertEquals(DarkRules.MANA_BAR_DARK_COLOR, DarkRules.manaBarColor(DarkRules.CORRUPTED));
        assertEquals(DarkRules.MANA_BAR_DARK_COLOR, DarkRules.manaBarColor(DarkRules.LOST));
        assertEquals(0xFF00AAFF, DarkRules.MANA_BAR_COLOR);
    }

    @Test
    void deedAndRedemptionNumbersMatchTheSpec() {
        assertEquals(3.0f, DarkRules.AVADA_INNOCENT_BONUS, EPS);
        assertEquals(30.0f, DarkRules.HORCRUX_BONUS, EPS);
        assertEquals(60.0f, DarkRules.HORCRUX_FLOOR, EPS);
        assertEquals(0.0f, DarkRules.NO_FLOOR, EPS);
        assertEquals(1.0f, DarkRules.REDEMPTION_AMOUNT, EPS);
        assertEquals(36_000, DarkRules.DEFAULT_REDEMPTION_TICKS);
        assertEquals(36_000, DarkRules.DEFAULT_DECAY_TICKS);
        assertEquals(12_000L, DarkRules.QUIET_TICKS);
        assertEquals(1.0f, DarkRules.DECAY_STEP, EPS);
        assertEquals(0.0f, DarkRules.MIN_DARKNESS, EPS);
        assertEquals(100.0f, DarkRules.MAX_DARKNESS, EPS);
    }
}
```

- [ ] **Step 2: Run it and see it fail.** Run the gradle check (`pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`), then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... DarkRules`).

- [ ] **Step 3: Write `DarkRules`.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java`:

```java
package com.hogwartsmagic.spell.dark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Dark Magic rules, free of Minecraft so they are unit-tested: which spells are dark and how much Darkness a paid
 * cast adds, the tiers, the clamp and the two-decimal rounding, losses that stop at a floor, the slow decay, who pays
 * for a forced cast, and the tier keys and colours the client draws. Every cost and reward reads the tier, never the
 * raw number. The deed and redemption numbers are here for Phase 3.
 *
 * Darkness is a float 0..100 kept to two decimals: every stored or compared value goes through sanitize, so a table
 * value added N times lands exactly on a tier bound. Spells are keyed by their id string; no SpellType import.
 */
public final class DarkRules {
    public static final float MIN_DARKNESS = 0.0f;
    public static final float MAX_DARKNESS = 100.0f;

    public static final int CLEAN = 0;
    public static final int SHADOWED = 1;
    public static final int TAINTED = 2;
    public static final int CORRUPTED = 3;
    public static final int LOST = 4;

    /** Inclusive lower bound of each tier, indexed by tier: 20.00 is Shadowed, 19.99 is Clean. */
    private static final float[] TIER_LOWER_BOUNDS = {0.0f, 20.0f, 40.0f, 60.0f, 80.0f};
    private static final String[] TIER_NAMES = {"clean", "shadowed", "tainted", "corrupted", "lost"};
    /** ARGB, white to dark green, indexed by tier. */
    private static final int[] TIER_COLORS = {0xFFFFFFFF, 0xFFCDE8CD, 0xFF98CC98, 0xFF5CAE5C, 0xFF2E8B3A};
    private static final String TIER_KEY_PREFIX = "gui.hogwartsmagic.dark.tier.";

    /** The mana bar fill below Corrupted (the colour it always had). */
    public static final int MANA_BAR_COLOR = 0xFF00AAFF;
    /** The mana bar fill at Corrupted and Lost: a dark green-black. */
    public static final int MANA_BAR_DARK_COLOR = 0xFF1B3A22;

    /** Decay waits until no dark spell has been cast for this long (10 minutes of game time). */
    public static final long QUIET_TICKS = 12_000L;
    /** Points lost per full decay period. */
    public static final float DECAY_STEP = 1.0f;
    /** Online quiet ticks per point lost (30 minutes): 100 to 0 in 50 hours. The config can change it. */
    public static final int DEFAULT_DECAY_TICKS = 36_000;
    /** The decay check runs once in this many player ticks. */
    public static final int DECAY_CHECK_INTERVAL_TICKS = 200;

    /** No floor: Darkness can fall to 0. */
    public static final float NO_FLOOR = 0.0f;
    /** Phase 3: Darkness cannot decay below this while the player's Horcrux exists. */
    public static final float HORCRUX_FLOOR = 60.0f;
    /** Phase 3: added when a Horcrux is made. */
    public static final float HORCRUX_BONUS = 30.0f;
    /** Phase 3: added by an Avada kill of an innocent. */
    public static final float AVADA_INNOCENT_BONUS = 3.0f;
    /** Phase 3: removed by one Heal of another hurt living thing. */
    public static final float REDEMPTION_AMOUNT = 1.0f;
    /** Phase 3: at most one redemption per this many ticks of play. The config can change it. */
    public static final int DEFAULT_REDEMPTION_TICKS = 36_000;

    /** Who gains for a paid dark cast. */
    public enum Payer {
        BODY,
        PUPPETEER,
        NOBODY
    }

    /** The result of one decay check: the new Darkness and the online quiet ticks carried to the next check. */
    public record Decay(float darkness, int progressTicks) {
    }

    private static final Map<String, Float> GAIN;

    static {
        Map<String, Float> gain = new LinkedHashMap<>();
        gain.put("avada_kedavra", 6.0f);
        gain.put("fiendfyre", 2.5f);
        gain.put("morsmordre", 2.5f);
        gain.put("imperio", 1.5f);
        gain.put("crucio", 1.2f);
        gain.put("tenebris_flight", 0.6f);
        gain.put("sectumsempra", 0.4f);
        gain.put("dolohov_curse", 0.4f);
        gain.put("serpensortia", 0.3f);
        GAIN = Collections.unmodifiableMap(gain);
    }

    private DarkRules() {
    }

    /** The ids of the nine dark spells. */
    public static Set<String> darkSpellIds() {
        return GAIN.keySet();
    }

    public static boolean isDark(String spellId) {
        return spellId != null && GAIN.containsKey(spellId);
    }

    /** Darkness per paid cast before the multiplier; 0 for a spell that is not dark. */
    public static float baseGain(String spellId) {
        Float gain = spellId == null ? null : GAIN.get(spellId);
        return gain == null ? 0.0f : gain;
    }

    /** Darkness per paid cast after the server multiplier, rounded to two decimals. 0 for a bad multiplier. */
    public static float gain(String spellId, double multiplier) {
        if (!(multiplier > 0.0) || Double.isInfinite(multiplier)) {
            return 0.0f;
        }
        double raw = baseGain(spellId) * multiplier;
        return (float) (Math.round(raw * 100.0) / 100.0);
    }

    /** NaN and infinities become 0, then clamp to 0..100, then round to two decimals. */
    public static float sanitize(float darkness) {
        if (Float.isNaN(darkness) || Float.isInfinite(darkness)) {
            return MIN_DARKNESS;
        }
        float clamped = Math.max(MIN_DARKNESS, Math.min(MAX_DARKNESS, darkness));
        return Math.round(clamped * 100.0) / 100.0f;
    }

    private static int clampTier(int tier) {
        return Math.max(CLEAN, Math.min(LOST, tier));
    }

    public static int tier(float darkness) {
        float value = sanitize(darkness);
        for (int t = LOST; t > CLEAN; t--) {
            if (value >= TIER_LOWER_BOUNDS[t]) {
                return t;
            }
        }
        return CLEAN;
    }

    public static float tierLowerBound(int tier) {
        return TIER_LOWER_BOUNDS[clampTier(tier)];
    }

    /** Adds a positive amount and stops at 100. Anything else leaves the value as it is. */
    public static float afterGain(float darkness, float amount) {
        float value = sanitize(darkness);
        if (!(amount > 0.0f) || Float.isInfinite(amount)) {
            return value;
        }
        return sanitize(value + amount);
    }

    /** Removes a positive amount, never below the floor; a value already at or below the floor is left alone. */
    public static float afterLoss(float darkness, float amount, float floor) {
        float value = sanitize(darkness);
        float bottom = sanitize(floor);
        if (!(amount > 0.0f) || Float.isInfinite(amount) || value <= bottom) {
            return value;
        }
        return sanitize(Math.max(bottom, value - amount));
    }

    /** A signed change: a gain above 0, a loss through the floor below 0. */
    public static float add(float darkness, float delta, float floor) {
        if (delta > 0.0f) {
            return afterGain(darkness, delta);
        }
        if (delta < 0.0f) {
            return afterLoss(darkness, -delta, floor);
        }
        return sanitize(darkness);
    }

    /** True once 10 minutes of game time have passed since the last dark cast (or the clock is behind it). */
    public static boolean quiet(long now, long lastDarkCast) {
        return now < lastDarkCast || now - lastDarkCast >= QUIET_TICKS;
    }

    /**
     * One decay check. Not quiet, or at or below the floor: nothing falls and the carried ticks reset. Otherwise the
     * elapsed ticks are added to the carried ticks (first clamped below one period, so a lowered config never dumps
     * points), each full period removes DECAY_STEP through the floor, and the rest is carried.
     */
    public static Decay decay(float darkness, int progressTicks, int elapsedTicks, boolean quiet, float floor,
                              int decayTicks) {
        float value = sanitize(darkness);
        float bottom = sanitize(floor);
        if (!quiet || value <= bottom) {
            return new Decay(value, 0);
        }
        int period = Math.max(1, decayTicks);
        long progress = Math.min(Math.max(0L, progressTicks), period - 1L) + Math.max(0L, elapsedTicks);
        long steps = progress / period;
        int rest = (int) (progress % period);
        if (steps == 0L) {
            return new Decay(value, rest);
        }
        float after = afterLoss(value, steps * DECAY_STEP, bottom);
        return new Decay(after, after <= bottom ? 0 : rest);
    }

    /** True on one tick in every DECAY_CHECK_INTERVAL_TICKS, phased by a per-player key (the entity id). */
    public static boolean dueToDecayCheck(long tickCount, long phaseKey) {
        return Math.floorMod(tickCount + phaseKey, (long) DECAY_CHECK_INTERVAL_TICKS) == 0L;
    }

    /** A puppet cast (a puppeteer recorded for it) charges the puppeteer; any other forced cast charges nobody. */
    public static Payer payer(boolean puppetCastRunning, boolean bodyIsForced) {
        if (puppetCastRunning) {
            return Payer.PUPPETEER;
        }
        return bodyIsForced ? Payer.NOBODY : Payer.BODY;
    }

    /** Creative and spectator players gain nothing. */
    public static boolean earns(boolean creative, boolean spectator) {
        return !creative && !spectator;
    }

    /** What the world and the client see: the stored value, or 0 with Dark Magic off. */
    public static float effective(boolean enabled, float stored) {
        return enabled ? sanitize(stored) : MIN_DARKNESS;
    }

    public static int effectiveTier(boolean enabled, float stored) {
        return enabled ? tier(stored) : CLEAN;
    }

    /** 1 when the soul darkened, -1 when it lightened, 0 when the tier is the same. */
    public static int tierChange(int oldTier, int newTier) {
        return Integer.compare(newTier, oldTier);
    }

    public static String tierKey(int tier) {
        return TIER_KEY_PREFIX + TIER_NAMES[clampTier(tier)];
    }

    public static int tierColor(int tier) {
        return TIER_COLORS[clampTier(tier)];
    }

    public static int manaBarColor(int tier) {
        return tier >= CORRUPTED ? MANA_BAR_DARK_COLOR : MANA_BAR_COLOR;
    }
}
```

- [ ] **Step 4: Run it and see it pass.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*DarkRulesTest" --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 21 tests pass. Then `timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL; the test-count command (Global Constraints) prints `404`.

- [ ] **Step 5: Greps.** `grep -n "import net.minecraft\|import com.hogwartsmagic\|switch" src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java`. Expected: nothing.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add DarkRules: dark spells, gain table, tiers, floor, clamp and decay

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java
```

---

### Task 2: DarkMagicData, config, the sync packet and the client copy

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/Config.java`
- Create: `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`
- Create: `src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java`
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`
- Modify: `src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: T1 (`DarkRules`). Produces: `Config.isDarkMagicEnabled()`, `Config.getDarkMagicGainMultiplier()` (double), `Config.getDarkMagicDecayTicks()` (int), `Config.getDarkMagicRedemptionTicks()` (int), `Config.isSneakoscopeDetectsPlayers()`; `DarkMagicData.KEY`, `KEY_LAST_DARK_CAST`, `KEY_DECAY_PROGRESS`, `getStored(Player)`, `get(ServerPlayer)`, `getTier(ServerPlayer)`, `floor(ServerPlayer)`, `onDarkCastPaid(ServerPlayer, SpellType)`, `add(ServerPlayer, float)`, `set(ServerPlayer, float)`, `tickDecay(ServerPlayer)`, `sync(ServerPlayer)`, `broadcastAfterReload()`; `SpellPackets.SyncDarkMagicPacket(float darkness)`; `DarkMagicClientState.accept(float)`, `clear()`, `darkness()`, `tier()`.

- [ ] **Step 1: Config.** In `src/main/java/com/hogwartsmagic/Config.java`, find:

```java
    public static final ForgeConfigSpec.IntValue SNEAKOSCOPE_DARK_MARK_RANGE = BUILDER
            .comment("Sneakoscope: range in blocks for the Dark Mark, which hangs high in the sky.")
            .defineInRange("sneakoscopeDarkMarkRange", 48, 8, 128);
```

and add after it:

```java

    public static final ForgeConfigSpec.BooleanValue SNEAKOSCOPE_DETECTS_PLAYERS = BUILDER
            .comment("Sneakoscope: if true, players at Corrupted or Lost (Dark Magic) set it off. Needs enableDarkMagic.")
            .define("sneakoscopeDetectsPlayers", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_DARK_MAGIC = BUILDER
            .comment("Dark Magic: if false, dark spells add no Darkness, nothing decays and every player reads as Clean. Stored values are kept.")
            .define("enableDarkMagic", true);

    public static final ForgeConfigSpec.DoubleValue DARK_MAGIC_GAIN_MULTIPLIER = BUILDER
            .comment("Dark Magic: multiplier on the Darkness each paid dark cast adds. 0 means no gain.")
            .defineInRange("darkMagicGainMultiplier", 1.0, 0.0, 10.0);

    public static final ForgeConfigSpec.IntValue DARK_MAGIC_DECAY_TICKS = BUILDER
            .comment("Dark Magic: ticks of online play, with no dark cast in the last 10 minutes, per point of Darkness lost.",
                    "36000 is 30 minutes, so 100 to 0 takes 50 hours. Lower it to test decay.")
            .defineInRange("darkMagicDecayTicks", 36000, 20, 10000000);

    public static final ForgeConfigSpec.IntValue DARK_MAGIC_REDEMPTION_TICKS = BUILDER
            .comment("Dark Magic: the shortest time in ticks of play between two Heal redemptions (one point each).")
            .defineInRange("darkMagicRedemptionTicks", 36000, 20, 10000000);
```

Find:

```java
    public static int sneakoscopeDarkMarkRange = 48;
```

and add after it:

```java
    public static boolean sneakoscopeDetectsPlayers = true;
    public static boolean enableDarkMagic = true;
    public static double darkMagicGainMultiplier = 1.0;
    public static int darkMagicDecayTicks = 36000;
    public static int darkMagicRedemptionTicks = 36000;
```

Find:

```java
        sneakoscopeDarkMarkRange = SNEAKOSCOPE_DARK_MARK_RANGE.get();
```

and add after it:

```java
        sneakoscopeDetectsPlayers = SNEAKOSCOPE_DETECTS_PLAYERS.get();
        enableDarkMagic = ENABLE_DARK_MAGIC.get();
        darkMagicGainMultiplier = DARK_MAGIC_GAIN_MULTIPLIER.get();
        darkMagicDecayTicks = DARK_MAGIC_DECAY_TICKS.get();
        darkMagicRedemptionTicks = DARK_MAGIC_REDEMPTION_TICKS.get();
```

Find:

```java
        if (event instanceof ModConfigEvent.Reloading) {
            com.hogwartsmagic.spell.mastery.MasteryConfig.broadcastAfterReload();
        }
```

and replace it with:

```java
        if (event instanceof ModConfigEvent.Reloading) {
            com.hogwartsmagic.spell.mastery.MasteryConfig.broadcastAfterReload();
            com.hogwartsmagic.spell.dark.DarkMagicData.broadcastAfterReload();
        }
```

Find:

```java
    public static int getSneakoscopeDarkMarkRange() {
        return sneakoscopeDarkMarkRange;
    }
```

and add after it:

```java

    /** Sneakoscope Part B: Corrupted and Lost players set it off (also needs enableDarkMagic). */
    public static boolean isSneakoscopeDetectsPlayers() {
        return sneakoscopeDetectsPlayers;
    }

    public static boolean isDarkMagicEnabled() {
        return enableDarkMagic;
    }

    public static double getDarkMagicGainMultiplier() {
        return darkMagicGainMultiplier;
    }

    public static int getDarkMagicDecayTicks() {
        return darkMagicDecayTicks;
    }

    /** Read by Dark Magic Phase 3 (redemption through Heal). */
    public static int getDarkMagicRedemptionTicks() {
        return darkMagicRedemptionTicks;
    }
```

- [ ] **Step 2: DarkMagicData.** Create `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java`:

```java
package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mind.PossessionManager;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * Per-player Darkness, stored as {@code PlayerPersisted/<modid>/DarkMagic} (float, two decimals) with
 * {@code DarkMagicLastDarkCast} (game time of the last paid dark cast) and {@code DarkMagicDecayProgress} (online
 * quiet ticks toward the next point of decay). Forge copies PlayerPersisted across death, so it survives dying.
 * Server only: the client gets its own effective value through {@link SpellPackets.SyncDarkMagicPacket} and keeps it
 * in client/DarkMagicClientState. Every change goes through write, which syncs and announces a tier change.
 */
public final class DarkMagicData {
    public static final String KEY = "DarkMagic";
    public static final String KEY_LAST_DARK_CAST = "DarkMagicLastDarkCast";
    public static final String KEY_DECAY_PROGRESS = "DarkMagicDecayProgress";
    private static final String KEY_TIER_UP = "gui.hogwartsmagic.dark.tier_up";
    private static final String KEY_TIER_DOWN = "gui.hogwartsmagic.dark.tier_down";

    private DarkMagicData() {
    }

    /** The stored Darkness, 0..100, whatever the toggle says. A missing key (every existing player) reads 0. */
    public static float getStored(Player player) {
        return DarkRules.sanitize(PlayerMagicData.getModData(player).getFloat(KEY));
    }

    /** What the world and the client see: the stored value, or 0 with enableDarkMagic off. */
    public static float get(ServerPlayer player) {
        return DarkRules.effective(Config.isDarkMagicEnabled(), getStored(player));
    }

    /** The tier every cost and reward reads (Phases 2 and 3): 0 with enableDarkMagic off. */
    public static int getTier(ServerPlayer player) {
        return DarkRules.effectiveTier(Config.isDarkMagicEnabled(), getStored(player));
    }

    /** Losses stop here. Phase 1: none. Phase 3 returns DarkRules.HORCRUX_FLOOR while the player's Horcrux exists. */
    public static float floor(ServerPlayer player) {
        return DarkRules.NO_FLOOR;
    }

    /**
     * The cast hook: called right after a cast's mana is paid (castAs main block, Fiendfyre channel start), hit or
     * miss. A puppet cast charges the puppeteer; any other forced cast charges nobody; creative and spectator payers
     * gain nothing. A paid dark cast also restarts the quiet window and the decay progress.
     */
    public static void onDarkCastPaid(ServerPlayer body, SpellType spell) {
        if (!Config.isDarkMagicEnabled()) {
            return;
        }
        float amount = DarkRules.gain(spell.getId(), Config.getDarkMagicGainMultiplier());
        if (!(amount > 0.0f)) {
            return;
        }
        UUID puppeteer = PossessionManager.castingPuppeteer;
        DarkRules.Payer who = DarkRules.payer(puppeteer != null, PossessionManager.isForcedCaster(body));
        ServerPlayer payer = null;
        if (who == DarkRules.Payer.BODY) {
            payer = body;
        } else if (who == DarkRules.Payer.PUPPETEER) {
            payer = body.server.getPlayerList().getPlayer(puppeteer);
        }
        if (payer == null || !DarkRules.earns(payer.isCreative(), payer.isSpectator())) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(payer);
        modData.putLong(KEY_LAST_DARK_CAST, payer.level().getGameTime());
        modData.putInt(KEY_DECAY_PROGRESS, 0);
        PlayerMagicData.saveModData(payer, modData);
        write(payer, DarkRules.afterGain(getStored(payer), amount));
    }

    /** Deeds and redemption (Phase 3) and the command: a signed change through the floor and the clamp. */
    public static void add(ServerPlayer player, float delta) {
        write(player, DarkRules.add(getStored(player), delta, floor(player)));
    }

    /** The command only: writes the value as given (clamped and rounded), ignoring the floor. */
    public static void set(ServerPlayer player, float value) {
        write(player, value);
    }

    /**
     * From ModEvents.onPlayerTick (server, END phase). Does nothing on 199 ticks out of 200, before any config or
     * persistent-data read. On the check tick: 200 ticks of online quiet time toward the next point, through the floor.
     */
    public static void tickDecay(ServerPlayer player) {
        if (!DarkRules.dueToDecayCheck(player.tickCount, player.getId()) || !Config.isDarkMagicEnabled()) {
            return;
        }
        CompoundTag modData = PlayerMagicData.getModData(player);
        float stored = DarkRules.sanitize(modData.getFloat(KEY));
        int progress = modData.getInt(KEY_DECAY_PROGRESS);
        if (stored <= DarkRules.MIN_DARKNESS && progress == 0) {
            return;
        }
        boolean quiet = DarkRules.quiet(player.level().getGameTime(), modData.getLong(KEY_LAST_DARK_CAST));
        DarkRules.Decay next = DarkRules.decay(stored, progress, DarkRules.DECAY_CHECK_INTERVAL_TICKS, quiet,
                floor(player), Config.getDarkMagicDecayTicks());
        if (next.progressTicks() != progress) {
            modData.putInt(KEY_DECAY_PROGRESS, next.progressTicks());
            PlayerMagicData.saveModData(player, modData);
        }
        if (next.darkness() != stored) {
            write(player, next.darkness());
        }
    }

    /** Sends this player their own effective Darkness. Never sent about anyone else. */
    public static void sync(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.SyncDarkMagicPacket(get(player)),
                PacketDistributor.PLAYER.with(player));
    }

    /** After a config reload (the toggle may have changed): resend everyone online, on the server thread. */
    public static void broadcastAfterReload() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sync(player);
            }
        });
    }

    private static void write(ServerPlayer player, float value) {
        boolean enabled = Config.isDarkMagicEnabled();
        CompoundTag modData = PlayerMagicData.getModData(player);
        float before = DarkRules.sanitize(modData.getFloat(KEY));
        float after = DarkRules.sanitize(value);
        modData.putFloat(KEY, after);
        PlayerMagicData.saveModData(player, modData);
        sync(player);
        int oldTier = DarkRules.effectiveTier(enabled, before);
        int newTier = DarkRules.effectiveTier(enabled, after);
        if (newTier != oldTier) {
            announce(player, oldTier, newTier);
        }
    }

    /** One chat line and one sound, to this player only: nobody else learns a tier. */
    private static void announce(ServerPlayer player, int oldTier, int newTier) {
        boolean darker = DarkRules.tierChange(oldTier, newTier) > 0;
        int colour = DarkRules.tierColor(newTier) & 0xFFFFFF;
        Component tierName = Component.translatable(DarkRules.tierKey(newTier))
                .withStyle(style -> style.withColor(colour));
        player.sendSystemMessage(Component.translatable(darker ? KEY_TIER_UP : KEY_TIER_DOWN, tierName)
                .withStyle(darker ? ChatFormatting.DARK_GREEN : ChatFormatting.GRAY));
        if (darker) {
            player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8f, 0.5f);
        } else {
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }
}
```

- [ ] **Step 3: The packet and protocol 7.** In `src/main/java/com/hogwartsmagic/network/SpellPackets.java`, find:

```java
            .networkProtocolVersion(6)
```

and replace it with:

```java
            .networkProtocolVersion(7)
```

Find (the end of `register()`):

```java
        CHANNEL.messageBuilder(LockPickStatePacket.class)
                .encoder((p, buf) -> buf.writeBoolean(p.picking))
                .decoder(buf -> new LockPickStatePacket(buf.readBoolean()))
                .consumerMainThread(LockPickStatePacket::handle)
                .add();
    }
```

and replace it with:

```java
        CHANNEL.messageBuilder(LockPickStatePacket.class)
                .encoder((p, buf) -> buf.writeBoolean(p.picking))
                .decoder(buf -> new LockPickStatePacket(buf.readBoolean()))
                .consumerMainThread(LockPickStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncDarkMagicPacket.class)
                .encoder((p, buf) -> buf.writeFloat(p.darkness))
                .decoder(buf -> new SyncDarkMagicPacket(buf.readFloat()))
                .consumerMainThread(SyncDarkMagicPacket::handle)
                .add();
    }
```

Find (the end of `SyncMasteryConfigPacket`):

```java
            enqueueClientWork(ctx, () -> com.hogwartsmagic.spell.mastery.MasteryConfig.acceptSynced(
                    new com.hogwartsmagic.spell.mastery.MasteryConfig.Values(
                            p.adept, p.expert, p.master, p.rankDiscount, p.manaPerRank, p.manaCap)));
        }
    }
```

and add after it:

```java

    /** Server to client: this player's own effective Darkness (0 with Dark Magic off). Never about anyone else. */
    public record SyncDarkMagicPacket(float darkness) {
        public static void handle(SyncDarkMagicPacket p, CustomPayloadEvent.Context ctx) {
            if (ctx.getSender() != null) {
                // Server-to-client only: ignore anything a client sends (e.g. a LAN guest).
                ctx.setPacketHandled(true);
                return;
            }
            enqueueClientWork(ctx, () -> com.hogwartsmagic.util.ClientSideBridge.handlePacket("syncDarkMagic", p));
        }
    }
```

- [ ] **Step 4: The client copy.** Create `src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java`:

```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.dark.DarkRules;

/**
 * Client only: this player's own Darkness as the server last sent it (0 with Dark Magic off on the server).
 * Never another player's. A static, so it survives respawn and dimension change; cleared on logout.
 */
public final class DarkMagicClientState {
    private static volatile float darkness;

    private DarkMagicClientState() {
    }

    public static void accept(float value) {
        darkness = DarkRules.sanitize(value);
    }

    public static void clear() {
        darkness = DarkRules.MIN_DARKNESS;
    }

    public static float darkness() {
        return darkness;
    }

    public static int tier() {
        return DarkRules.tier(darkness);
    }
}
```

- [ ] **Step 5: The bridge.** In `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java`, find:

```java
            case "syncSpellMastery" -> syncSpellMastery((SpellPackets.SyncSpellMasteryPacket) payload);
```

and add after it:

```java
            case "syncDarkMagic" -> DarkMagicClientState.accept(((SpellPackets.SyncDarkMagicPacket) payload).darkness());
```

(This is a `switch` on a `String`, not an enum.)

- [ ] **Step 6: Clear on logout.** In `src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java`, find:

```java
        MasteryConfig.clearSynced();
        PolyjuiceClientState.clearReveals();
```

and replace it with:

```java
        MasteryConfig.clearSynced();
        PolyjuiceClientState.clearReveals();
        DarkMagicClientState.clear();
```

- [ ] **Step 7: Sync on login, respawn and dimension change.** In `src/main/java/com/hogwartsmagic/event/ModEvents.java`:

In `onPlayerJoin`, find:

```java
            com.hogwartsmagic.spell.mastery.MasteryConfig.sendTo(player);
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);
```

and replace it with:

```java
            com.hogwartsmagic.spell.mastery.MasteryConfig.sendTo(player);
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);
            com.hogwartsmagic.spell.dark.DarkMagicData.sync(player);
```

In `onPlayerRespawn`, find:

```java
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(serverPlayer);
```

and replace it with:

```java
            com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(serverPlayer);
            com.hogwartsmagic.spell.dark.DarkMagicData.sync(serverPlayer);
```

In `syncPersistentMagicState` (called on dimension change), find (8-space indent; the login line above has 12):

```java
        com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);

        float maxMana = PlayerMagicData.getMaxMana(player);
```

and replace it with:

```java
        com.hogwartsmagic.spell.mastery.SpellMasteryData.sync(player);
        com.hogwartsmagic.spell.dark.DarkMagicData.sync(player);

        float maxMana = PlayerMagicData.getMaxMana(player);
```

- [ ] **Step 8: Text.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find the last entry:

```json
  "item.hogwartsmagic.sneakoscope.tooltip": "Spins and whistles when a Death Eater, a Dementor or the Dark Mark is near. Place it for a redstone signal."
}
```

and replace it with:

```json
  "item.hogwartsmagic.sneakoscope.tooltip": "Spins and whistles when a Death Eater, a Dementor or the Dark Mark is near. Place it for a redstone signal.",
  "gui.hogwartsmagic.dark.tier.clean": "Clean",
  "gui.hogwartsmagic.dark.tier.shadowed": "Shadowed",
  "gui.hogwartsmagic.dark.tier.tainted": "Tainted",
  "gui.hogwartsmagic.dark.tier.corrupted": "Corrupted",
  "gui.hogwartsmagic.dark.tier.lost": "Lost",
  "gui.hogwartsmagic.dark.tier_up": "Your soul darkens. You are now %s.",
  "gui.hogwartsmagic.dark.tier_down": "Your soul lightens. You are now %s.",
  "gui.hogwartsmagic.grimoire.soul": "Soul: %s"
}
```

If another phase has added entries after the tooltip line, add the eight new lines after the last entry instead, with commas so the file stays valid.

- [ ] **Step 9: Validate, build, greps, boot.**
  - `cd /home/user/minecraft_mods && python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`. Expected: no output.
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 404 tests, all green.
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch\|SubscribeEvent" src/main/java/com/hogwartsmagic/spell/dark`. Expected: nothing.
  - `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: one line, `(7)`.
  - Run the boot block (Global Constraints). Expected: `Done (`; no network registration, config or class-loading errors; no server java process left.

- [ ] **Step 10: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add DarkMagicData, Dark Magic config and the Darkness sync (protocol 7)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/client/DarkMagicClientState.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/MasteryConfigClientEvents.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 3: The cast hooks and the decay tick

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`

**Interfaces.** Consumes: T2 (`DarkMagicData.onDarkCastPaid(ServerPlayer, SpellType)`, `DarkMagicData.tickDecay(ServerPlayer)`). Produces: Darkness gain on every paid dark cast and decay while online.

- [ ] **Step 1: The main cast hook.** In `src/main/java/com/hogwartsmagic/network/SpellPackets.java`, inside `CastSpellPacket.castAs`, find (the end of the one block where mana is checked and spent, right before the self-cast branch):

```java
            if (!creative) {
                MagicWandItem.rollManaRefund(player, wand, cost);
            }
```

and replace it with:

```java
            if (!creative) {
                MagicWandItem.rollManaRefund(player, wand, cost);
            }
            // Dark Magic: a paid dark cast darkens the payer, hit or miss (a puppet cast charges the puppeteer).
            com.hogwartsmagic.spell.dark.DarkMagicData.onDarkCastPaid(player, spell);
```

- [ ] **Step 2: The Fiendfyre channel start.** In the same method, inside `if (p.channelHeartbeat && spell == SpellType.FIENDFYRE)`, find:

```java
                playerData.putBoolean(TAG_FIENDFYRE_CHANNELING, true);
```

and replace it with:

```java
                // Dark Magic: the channel start is the paid Fiendfyre cast (its startup mana was just taken).
                com.hogwartsmagic.spell.dark.DarkMagicData.onDarkCastPaid(player, spell);
                playerData.putBoolean(TAG_FIENDFYRE_CHANNELING, true);
```

The Avada windup needs no change: `AvadaWindup.begin` returns before the mana block, and its re-entry (ready flag, with `castingPuppeteer` set for a puppet) reaches the hook in Step 1.

- [ ] **Step 3: The decay tick.** In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, inside `onPlayerTick` (server branch, END phase), find:

```java
            trySpawnInsomniaDementor(player);
            com.hogwartsmagic.sneakoscope.SneakoscopeCarried.tick(player);
```

and replace it with:

```java
            trySpawnInsomniaDementor(player);
            com.hogwartsmagic.sneakoscope.SneakoscopeCarried.tick(player);
            // Dark Magic decay: returns at once on 199 ticks out of 200, before any read.
            com.hogwartsmagic.spell.dark.DarkMagicData.tickDecay(player);
```

- [ ] **Step 4: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 404 tests, all green.
  - `grep -c "DarkMagicData.onDarkCastPaid" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: `2`.
  - `grep -c "DarkMagicData.tickDecay" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: `1`.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic: gain on paid dark casts and decay while online

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java
```

---

### Task 4: The /darkmagic command

**Files:**
- Create: `src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`

**Interfaces.** Consumes: T1 (`DarkRules.tier`, `tierKey`), T2 (`DarkMagicData.getStored`, `set`, `add`, `floor`; `Config.isDarkMagicEnabled`). Produces: `DarkMagicCommand.register(CommandDispatcher<CommandSourceStack>)`.

- [ ] **Step 1: The command.** Create `src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java`:

```java
package com.hogwartsmagic.command;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.spell.dark.DarkMagicData;
import com.hogwartsmagic.spell.dark.DarkRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * {@code /darkmagic get|set|add <player> [value]} -- inspect and force a player's Darkness while testing. Op-only.
 * set ignores the floor, add goes through it; neither restarts the 10-minute quiet window. A tier change is
 * announced to the player exactly as in play.
 */
public class DarkMagicCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("darkmagic")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DarkMagicCommand::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(
                                                DarkRules.MIN_DARKNESS, DarkRules.MAX_DARKNESS))
                                        .executes(DarkMagicCommand::set))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(
                                                -DarkRules.MAX_DARKNESS, DarkRules.MAX_DARKNESS))
                                        .executes(DarkMagicCommand::add)))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        MutableComponent line = describe(player);
        if (!Config.isDarkMagicEnabled()) {
            line.append(" - enableDarkMagic is off, so it reads as Clean");
        }
        ctx.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DarkMagicData.set(player, FloatArgumentType.getFloat(ctx, "value"));
        MutableComponent line = describe(player);
        ctx.getSource().sendSuccess(() -> line, true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        DarkMagicData.add(player, FloatArgumentType.getFloat(ctx, "value"));
        MutableComponent line = describe(player);
        ctx.getSource().sendSuccess(() -> line, true);
        return 1;
    }

    /** "Name: Darkness 63.50 (Corrupted)", plus ", floor 60" when a floor applies. The stored value and its tier. */
    private static MutableComponent describe(ServerPlayer player) {
        float stored = DarkMagicData.getStored(player);
        MutableComponent line = Component.literal(player.getScoreboardName() + ": Darkness "
                        + String.format(Locale.ROOT, "%.2f", stored) + " (")
                .append(Component.translatable(DarkRules.tierKey(DarkRules.tier(stored))))
                .append(")");
        float floor = DarkMagicData.floor(player);
        if (floor > DarkRules.NO_FLOOR) {
            line.append(String.format(Locale.ROOT, ", floor %.0f", floor));
        }
        return line;
    }
}
```

- [ ] **Step 2: Register it.** In `src/main/java/com/hogwartsmagic/ExampleMod.java`, inside `onRegisterCommands`, find:

```java
        com.hogwartsmagic.command.SpellMasteryCommand.register(event.getDispatcher());
```

and replace it with:

```java
        com.hogwartsmagic.command.SpellMasteryCommand.register(event.getDispatcher());
        com.hogwartsmagic.command.DarkMagicCommand.register(event.getDispatcher());
```

- [ ] **Step 3: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 404 tests, all green.
  - `grep -n "net.minecraft.client\|com.hogwartsmagic.client\|switch" src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java`. Expected: nothing.

- [ ] **Step 4: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add the /darkmagic get, set and add command

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java src/main/java/com/hogwartsmagic/ExampleMod.java
```

---

### Task 5: The Grimoire Soul line and the mana bar tint

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java`
- Modify: `src/main/java/com/hogwartsmagic/client/ManaOverlay.java`

**Interfaces.** Consumes: T1 (`DarkRules.tierKey`, `tierColor`, `manaBarColor`), T2 (`DarkMagicClientState.tier()`, lang `gui.hogwartsmagic.grimoire.soul`). Produces: UI only.

- [ ] **Step 1: The Soul line.** In `src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java`, inside `render`, find:

```java
        drawProgressBar(g, panelLeft + PANEL_WIDTH / 2 - 80, panelTop + 33, 160, 3);
```

and replace it with:

```java
        drawProgressBar(g, panelLeft + PANEL_WIDTH / 2 - 80, panelTop + 33, 160, 3);

        // Dark Magic: the reader's own Soul, right-aligned in the title row. The tier only, never the number.
        int soulTier = DarkMagicClientState.tier();
        Component soul = Component.translatable("gui." + ExampleMod.MODID + ".grimoire.soul",
                Component.translatable(com.hogwartsmagic.spell.dark.DarkRules.tierKey(soulTier)));
        g.drawString(this.font, soul, right - 8 - this.font.width(soul), panelTop + 8,
                com.hogwartsmagic.spell.dark.DarkRules.tierColor(soulTier));
```

(`right` is the local `int right = panelLeft + PANEL_WIDTH;` declared at the top of `render`.)

- [ ] **Step 2: The mana bar.** In `src/main/java/com/hogwartsmagic/client/ManaOverlay.java`, find:

```java
        // Light blue color
        guiGraphics.fill(x, y, x + fillWidth, y + barHeight, 0xFF00AAFF);
```

and replace it with:

```java
        // Light blue; a dark green-black from Corrupted (Dark Magic). The client knows only its own tier.
        guiGraphics.fill(x, y, x + fillWidth, y + barHeight,
                com.hogwartsmagic.spell.dark.DarkRules.manaBarColor(DarkMagicClientState.tier()));
```

- [ ] **Step 3: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 404 tests, all green.
  - `git diff -- src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java src/main/java/com/hogwartsmagic/client/ManaOverlay.java | grep "^+.*switch"`. Expected: nothing.

- [ ] **Step 4: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Dark Magic UI: Grimoire Soul line and the dark mana bar from Corrupted

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/client/SpellCompendiumScreen.java src/main/java/com/hogwartsmagic/client/ManaOverlay.java
```

---

### Task 6: Sneakoscope Part B (dark players)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java`
- Test: `src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java`
- Modify: `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`
- Modify: `MOD_FEATURES_WIKI.md`, `wiki/data.js`

**Interfaces.** Consumes: T1 (`DarkRules.CORRUPTED`, test only), T2 (`DarkMagicData.getTier(ServerPlayer)`, `Config.isDarkMagicEnabled()`, `Config.isSneakoscopeDetectsPlayers()`). Produces: `SneakoscopeRules.Source.DARK_PLAYER`, `SneakoscopeRules.DARK_PLAYER_MIN_TIER`, `playersDetectable(boolean, boolean)`, `detectsPlayer(boolean, boolean, boolean, int)`.

- [ ] **Step 1: Write the failing tests.** In `src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java`, find the last test and the class end:

```java
    @Test
    void nextWhistleStopsWhenNothingIsNear() {
        assertEquals(Long.MAX_VALUE, SneakoscopeRules.nextWhistle(100L, 110L, 5, 0));
    }
}
```

and replace it with:

```java
    @Test
    void nextWhistleStopsWhenNothingIsNear() {
        assertEquals(Long.MAX_VALUE, SneakoscopeRules.nextWhistle(100L, 110L, 5, 0));
    }

    @Test
    void aDarkPlayerUsesTheNearRange() {
        assertEquals(Reach.NEAR, Source.DARK_PLAYER.reach());
        assertEquals(16, SneakoscopeRules.range(Source.DARK_PLAYER, 16, 48));
        assertEquals(8, strengthOf(new Hit(Source.DARK_PLAYER, 8.0)));
        assertEquals(0, strengthOf(new Hit(Source.DARK_PLAYER, 40.0)));
    }

    @Test
    void onlyCorruptedOrLostPlayersInSurvivalAreDetected() {
        assertEquals(com.hogwartsmagic.spell.dark.DarkRules.CORRUPTED, SneakoscopeRules.DARK_PLAYER_MIN_TIER);
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, false, 0));
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, false, 1));
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, false, 2));
        assertTrue(SneakoscopeRules.detectsPlayer(true, true, false, 3));
        assertTrue(SneakoscopeRules.detectsPlayer(true, true, false, 4));
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, true, 4));
        assertFalse(SneakoscopeRules.detectsPlayer(false, true, false, 4));
        assertFalse(SneakoscopeRules.detectsPlayer(true, false, false, 4));
        assertTrue(SneakoscopeRules.playersDetectable(true, true));
        assertFalse(SneakoscopeRules.playersDetectable(false, true));
        assertFalse(SneakoscopeRules.playersDetectable(true, false));
    }
}
```

Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*SneakoscopeRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... DARK_PLAYER`).

- [ ] **Step 2: The rules.** In `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java`, find:

```java
        DEATH_EATER(Reach.NEAR),
        DEMENTOR(Reach.NEAR),
        DARK_MARK(Reach.SKY);
```

and replace it with:

```java
        DEATH_EATER(Reach.NEAR),
        DEMENTOR(Reach.NEAR),
        DARK_MARK(Reach.SKY),
        /** Part B: a player at Corrupted or Lost (Dark Magic). */
        DARK_PLAYER(Reach.NEAR);
```

Find:

```java
    /** Carried whistle interval at strength 15. */
    public static final int FASTEST_WHISTLE_TICKS = 10;
```

and add after it:

```java
    /** Part B: the lowest Dark Magic tier that sets a Sneakoscope off (Corrupted; a test pins it to DarkRules). */
    public static final int DARK_PLAYER_MIN_TIER = 3;

    /** Part B: whether players are looked for at all (both toggles on). */
    public static boolean playersDetectable(boolean darkMagicOn, boolean detectsPlayers) {
        return darkMagicOn && detectsPlayers;
    }

    /** Part B: a player sets it off at Corrupted or Lost, unless creative or spectator, and only with both toggles on. */
    public static boolean detectsPlayer(boolean darkMagicOn, boolean detectsPlayers, boolean creativeOrSpectator,
                                        int tier) {
        return playersDetectable(darkMagicOn, detectsPlayers) && !creativeOrSpectator && tier >= DARK_PLAYER_MIN_TIER;
    }
```

- [ ] **Step 3: The probe.** In `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java`, find:

```java
import com.hogwartsmagic.entity.WizardEntity;
```

and replace it with:

```java
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.spell.dark.DarkMagicData;
```

Find:

```java
import java.util.List;
```

and replace it with:

```java
import java.util.List;
import java.util.function.Predicate;
```

Find:

```java
    private record Probe(SneakoscopeRules.Source source, Class<? extends Entity> type) {
    }

    /** VoldemortEntity extends WizardEntity, so the Death Eater probe finds him too. */
    private static final List<Probe> PROBES = List.of(
            new Probe(SneakoscopeRules.Source.DEATH_EATER, WizardEntity.class),
            new Probe(SneakoscopeRules.Source.DEMENTOR, DementorEntity.class),
            new Probe(SneakoscopeRules.Source.DARK_MARK, DarkMarkEntity.class));
```

and replace it with:

```java
    private record Probe(SneakoscopeRules.Source source, Class<? extends Entity> type, Predicate<Entity> accepts) {
    }

    private static final Predicate<Entity> ANY = e -> true;

    /**
     * VoldemortEntity extends WizardEntity, so the Death Eater probe finds him too. Part B: Corrupted and Lost players,
     * whatever they look like (the Cloak and Polyjuice do not hide intent); never creative or spectator players.
     */
    private static final List<Probe> PROBES = List.of(
            new Probe(SneakoscopeRules.Source.DEATH_EATER, WizardEntity.class, ANY),
            new Probe(SneakoscopeRules.Source.DEMENTOR, DementorEntity.class, ANY),
            new Probe(SneakoscopeRules.Source.DARK_MARK, DarkMarkEntity.class, ANY),
            new Probe(SneakoscopeRules.Source.DARK_PLAYER, ServerPlayer.class, SneakoscopeSensor::isDarkPlayer));
```

Find:

```java
        for (Probe probe : PROBES) {
            int probeRange = SneakoscopeRules.range(probe.source(), range, darkMarkRange);
            collect(level, probe.type(), probe.source(), centre, probeRange, holder, hits);
        }
```

and replace it with:

```java
        boolean playersOn = SneakoscopeRules.playersDetectable(Config.isDarkMagicEnabled(),
                Config.isSneakoscopeDetectsPlayers());
        for (Probe probe : PROBES) {
            if (probe.source() == SneakoscopeRules.Source.DARK_PLAYER && !playersOn) {
                continue;
            }
            int probeRange = SneakoscopeRules.range(probe.source(), range, darkMarkRange);
            collect(level, probe.type(), probe.source(), probe.accepts(), centre, probeRange, holder, hits);
        }
```

Find:

```java
    private static <T extends Entity> void collect(ServerLevel level, Class<T> type, SneakoscopeRules.Source source,
                                                   Vec3 centre, int range, @Nullable Entity holder,
                                                   List<SneakoscopeRules.Hit> hits) {
```

and replace it with:

```java
    private static <T extends Entity> void collect(ServerLevel level, Class<T> type, SneakoscopeRules.Source source,
                                                   Predicate<Entity> accepts, Vec3 centre, int range,
                                                   @Nullable Entity holder, List<SneakoscopeRules.Hit> hits) {
```

Find:

```java
        for (T entity : level.getEntitiesOfClass(type, box, e -> e != holder && e.isAlive())) {
```

and replace it with:

```java
        for (T entity : level.getEntitiesOfClass(type, box, e -> e != holder && e.isAlive() && accepts.test(e))) {
```

Find:

```java
    /** The carried whistle: only this player hears it. */
```

and add before it:

```java
    /** Part B: a Corrupted or Lost player in survival or adventure (the toggles are checked before the scan). */
    private static boolean isDarkPlayer(Entity entity) {
        return entity instanceof ServerPlayer player
                && SneakoscopeRules.detectsPlayer(Config.isDarkMagicEnabled(), Config.isSneakoscopeDetectsPlayers(),
                        player.isCreative() || player.isSpectator(), DarkMagicData.getTier(player));
    }

```

- [ ] **Step 4: Run the tests.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*SneakoscopeRulesTest" --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, all pass (2 new). Then `timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL; the test-count command prints `406`.

- [ ] **Step 5: The tooltip.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find (as Task 2 left it):

```json
  "item.hogwartsmagic.sneakoscope.tooltip": "Spins and whistles when a Death Eater, a Dementor or the Dark Mark is near. Place it for a redstone signal.",
```

and replace it with:

```json
  "item.hogwartsmagic.sneakoscope.tooltip": "Spins and whistles when a Death Eater, a Dementor, the Dark Mark or a dark wizard is near. Place it for a redstone signal.",
```

- [ ] **Step 6: The wikis.** In `MOD_FEATURES_WIKI.md`, find:

```markdown
- A small glass spinning top on a brass plate. It spins, glows and whistles when a Death Eater, Lord Voldemort or a Dementor is within 16 blocks, or the Dark Mark within 48 (both ranges are in the config).
```

and add after it:

```markdown
- It also senses a player whose soul is Corrupted or Lost (Dark Magic) within 16 blocks, even under the Invisibility Cloak or Polyjuice. You never set off your own Sneakoscope, creative and spectator players are never sensed, and the config value `sneakoscopeDetectsPlayers` turns this off.
```

In `wiki/data.js` (the Sneakoscope entry's `features` array), find:

```js
"Death Eaters, Voldemort and Dementors within 16 blocks, the Dark Mark within 48",
```

and replace it with:

```js
"Death Eaters, Voldemort and Dementors within 16 blocks, the Dark Mark within 48", "Corrupted or Lost players within 16 blocks, even cloaked or disguised",
```

- [ ] **Step 7: Validate and greps.**
  - `cd /home/user/minecraft_mods && python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`. Expected: no output.
  - `cd /home/user/minecraft_mods && node --check wiki/data.js && grep -c "Corrupted or Lost players" wiki/data.js`. Expected: `1` and no syntax error.
  - `grep -n "import net.minecraft\|import com.hogwartsmagic\|switch" src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java`. Expected: nothing.
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch\|SubscribeEvent\|getChunk" src/main/java/com/hogwartsmagic/sneakoscope`. Expected: nothing.

- [ ] **Step 8: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Sneakoscope Part B: Corrupted and Lost players set it off

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java src/main/resources/assets/hogwartsmagic/lang/en_us.json MOD_FEATURES_WIKI.md wiki/data.js
```

---

### Task 7: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 406 tests (383 + 21 + 2), all green.
- [ ] **Step 2:** `cd /home/user/minecraft_mods && python3 -c "import json;json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`. Expected: no output.
- [ ] **Step 3:** Safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/dark src/main/java/com/hogwartsmagic/command/DarkMagicCommand.java src/main/java/com/hogwartsmagic/sneakoscope src/main/java/com/hogwartsmagic/Config.java`. Expected: nothing.
  - `git diff 996f638 -- src/main/java | grep "^+" | grep -n "switch"`. Expected: nothing (the `case "syncDarkMagic"` line joins the existing String switch in `ClientRuntimeBridge`; no new switch).
  - `git diff 996f638 -- src/main/java | grep "^+" | grep -n "SubscribeEvent\|broadcastEntityEvent"`. Expected: nothing.
  - `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: `(7)` (or one more than the value before this phase, Global Constraints).
  - `grep -c "onDarkCastPaid" src/main/java/com/hogwartsmagic/network/SpellPackets.java`. Expected: `2`.
  - `git status --short`. Expected: clean except untracked `run/` output.
- [ ] **Step 4:** Run the boot block (Global Constraints). Expected: `Done (`; no network, config, command or class-loading errors; no server java process left afterwards.
- [ ] **Step 5:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 8: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 7. Use a survival world older than 10 minutes of game time (`/time query gametime` at least 12000), with the spells learned (`/spellmastery` is not needed; learn by books or creative, then switch to survival). Two players for rows 15, 16 and 19-25.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | Survival, an existing character from before this update | `/darkmagic get @s` | "Darkness 0.00 (Clean)" |
| 2 | Row 1 | Open the Grimoire | "Soul: Clean" in white at the top right of the title row; nothing overlaps the title |
| 3 | `/darkmagic set @s 19.5` | Cast Sectumsempra twice into the air (a miss) | After the second cast: chat "Your soul darkens. You are now Shadowed." (Shadowed in pale green), a low bell; `/darkmagic get @s` shows 20.30 |
| 4 | Row 3 | Cast Stupefy, Expecto Patronum and Lumos | Darkness unchanged |
| 5 | Note the value | Tap Fiendfyre (a beam), wait for the cooldown, then hold it (the channel) | Each adds 2.50 |
| 6 | Avada selected | Start the windup and switch spell before it finishes | No change. Let a windup finish: +6.00 |
| 7 | Creative mode | Cast Sectumsempra | No change |
| 8 | `/darkmagic set @s 60` | Hold a wand | Chat "You are now Corrupted", a low bell; the mana bar fill is dark green-black; the Grimoire shows "Soul: Corrupted" in mid green |
| 9 | Row 8 | `/darkmagic set @s 59` | "Your soul lightens. You are now Tainted." in gray, a chime; the mana bar is blue again |
| 10 | `/darkmagic set @s 100` from Clean | | One chat line ("You are now Lost"), not four |
| 11 | `/darkmagic set @s 45` | Die and respawn; then relog; then go through a Nether portal | 45.00 each time; the Grimoire says Tainted each time |
| 12 | Config `darkMagicDecayTicks = 200`, restart; no dark cast for 10 minutes | `/darkmagic set @s 21`, wait 20 s | 21 to 20 then 19 within about 10-20 s; "You are now Clean." with a chime at 19 |
| 13 | Row 12 | Cast Sectumsempra, then watch `/darkmagic get @s` for 9 minutes | No decay for 10 minutes after the cast, then it falls again |
| 14 | Config `enableDarkMagic = false` (edit the file while the server runs; it reloads) | Look at the Grimoire and the mana bar; cast Sectumsempra; `/darkmagic get @s` | "Soul: Clean", a blue bar, no gain, no decay; get shows the stored number and "enableDarkMagic is off"; turning it back on restores the tier with no chat line |
| 15 | Player A has Imperio at Master, player B holds a wand with Sectumsempra learned | A possesses B and makes B cast Sectumsempra | A gains 0.40; B gains nothing |
| 16 | Row 15, B is Shadowed | Watch B's chat while A changes tier, and A's while B does | Nobody sees a line or hears a bell for another player's tier |
| 17 | Config `darkMagicGainMultiplier = 2.0`, restart | Cast Sectumsempra | +0.80 |
| 18 | Dedicated server and a client built from this jar | Join | No protocol or registry mismatch; the Grimoire shows your tier |
| 19 | Player A `/darkmagic set A 60`, player B carries a Sneakoscope | A walks to 10 blocks from B | B hears the whistle and the item spins; A hears nothing from B's Sneakoscope |
| 20 | Row 19 | A puts on the Invisibility Cloak; then drinks Polyjuice (disguised as anyone) | B's Sneakoscope still whistles each time |
| 21 | `/darkmagic set A 59` (Tainted) | Same distance | Silent |
| 22 | A at 80, A carries a Sneakoscope, nobody else near | | A's own Sneakoscope is silent |
| 23 | A at 80 in creative, then in spectator | Near B | Silent both times |
| 24 | A placed Sneakoscope with a redstone lamp next to it, A at 70 | A walks in from 20 blocks | The lamp lights inside 16 blocks, one whistle when it starts |
| 25 | Config `sneakoscopeDetectsPlayers = false`, then (back to true) `enableDarkMagic = false` | A at 80 near B | Silent both times; Death Eaters and Dementors are still detected |
| 26 | Hover the Sneakoscope item | | The tooltip mentions a dark wizard |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Phase 1.**
  - Bullet 1, pure `DarkRules` (dark set and gain table, deed bonuses, tier bounds, decay step, floor, clamp, redemption amount): Task 1; tests `darkSetIsTheNineSpellsOfTheTable`, `gainTableMatchesTheSpec`, `castsFromCleanToCorruptedMatchTheTable`, `tierLowerBoundsAreInclusive`, `decay*`, `lossStopsAtTheFloor...`, `sanitize...`, `deedAndRedemptionNumbersMatchTheSpec`; rulings 1-6, 19.
  - Bullet 2, `DarkMagicData` (get, add with floor and clamp, set, tier, sync) and the tier change message: Task 2 step 2; rulings 4, 13, 14; rows 3, 8-10.
  - Bullet 3, the two cast hooks with the puppeteer rule: Task 3 steps 1-2; `DarkRules.payer`, `earns`; rulings 8-9; rows 3-7, 15. Decision 5 (puppeteer, creative, spectator), decision 16 (NPCs never reach `castAs`).
  - Bullet 4, decay every 200 ticks, no per-tick reads: Task 3 step 3, `tickDecay` returns on `dueToDecayCheck` first; rulings 5-7; tests `decayCheckRunsOnceEveryTwoHundredTicks`, `decayFromOneHundredToZeroTakesFiftyHours` (50 h; 30 h from 60); rows 12-13. The user ruling (1 point per 36000 ticks at every tier) is the default config and `DEFAULT_DECAY_TICKS`.
  - Bullet 5, sync on login, respawn, dimension change; `SyncDarkMagicPacket`; protocol 7: Task 2 steps 3, 7; rulings 11, 12, 21; rows 11, 18. Decision 11 (own tier only): `PacketDistributor.PLAYER`, `playNotifySound`, `sendSystemMessage` to the player; row 16.
  - Bullet 6, config and its sync, the command: Task 2 step 1, Task 4; rulings 10, 11, 18, 20; rows 12, 14, 17. Decision 13's four values plus `sneakoscopeDetectsPlayers`.
  - Bullet 7, the Grimoire Soul line and the mana bar tint: Task 5; rulings 15-16; rows 2, 8-9.
  - Bullet 8, Sneakoscope Part B: Task 6; ruling 22-23; tests `aDarkPlayerUsesTheNearRange`, `onlyCorruptedOrLostPlayersInSurvivalAreDetected`; rows 19-26.
  - Decision 14 (existing players at 0): missing keys read 0; row 1. Decision 1 (survives death): `copyPersistent`; row 11.
  - User rulings: gain table lowered twice (the decision 3 table, tested); decay 1 per 36000 at every tier (tested, 50 h); redemption capped (Phase 3; `REDEMPTION_AMOUNT`, `darkMagicRedemptionTicks` defined, ruling 19); Lost costs cut and the Patronus never fails (Phase 2; nothing here touches the Patronus).
  - Done when: rows 3, 8-12 (tiers and messages, decay), 11 (death and relog), 2 and 8 (Grimoire), 14 (toggle), Task 7 step 4 (boot); Part B: rows 19-21.
- **Type consistency.** `DarkRules.gain(String, double)` called with `(spell.getId(), Config.getDarkMagicGainMultiplier())`; `decay(float, int, int, boolean, float, int)` called with `(stored, progress, DECAY_CHECK_INTERVAL_TICKS, quiet, floor(player), Config.getDarkMagicDecayTicks())`; `dueToDecayCheck(long, long)` called with `(player.tickCount, player.getId())` (int widened); `payer(boolean, boolean)` with `(castingPuppeteer != null, isForcedCaster(body))`; `SyncDarkMagicPacket(float darkness)` encoded with `writeFloat`, read with `readFloat`, accessed as `p.darkness` and `.darkness()`; `ClientSideBridge.handlePacket(String, Object)` with `"syncDarkMagic"` on both ends; `SneakoscopeRules.detectsPlayer(boolean, boolean, boolean, int)` with `(Config.isDarkMagicEnabled(), Config.isSneakoscopeDetectsPlayers(), creative || spectator, DarkMagicData.getTier(player))`; `collect(...)` gains a `Predicate<Entity>` in the same position at its one call site. Checked against the 1.21 mapped jar: `ServerPlayer.playNotifySound(SoundEvent, SoundSource, float, float)`, `ServerPlayer.sendSystemMessage(Component)`, `SoundEvents.BELL_BLOCK` and `SoundEvents.AMETHYST_BLOCK_CHIME` are plain `SoundEvent` fields (no `.value()`), `Player.isCreative()`/`isSpectator()`; `ServerPlayer.server` is used the same way by `PossessionManager.castAsPuppet`; `PossessionManager.castingPuppeteer` is a public static `UUID`, `isForcedCaster(ServerPlayer)` public.
- **Placeholder scan:** none. Every number, colour, sound, lang string, anchor and command is given.
- **Counts:** Task 1 adds 21 tests, Task 6 adds 2. 383 + 21 + 2 = 406.
- **Spec notes:** the roadmap says the config is "synced like the mastery config"; this plan sends the effective value instead and needs no config packet (ruling 11). The roadmap's build line (`timeout 900 ./gradlew`) is superseded by the Global Constraints (`bash ./gradlew ... --max-workers=2`, gradlew is not executable). `darkMagicRedemptionTicks` exists from this phase but does nothing until Phase 3.
