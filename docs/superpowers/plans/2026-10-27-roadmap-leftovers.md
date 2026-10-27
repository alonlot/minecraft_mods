# Roadmap Leftovers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the roadmap items still open after the Spell Mastery, Brewing and Dark Magic runs: Fiendfyre by rank, real Serpensortia snakes, the Master Patronus, the Remorse potion, grey spells and house reactions to Darkness, brewing quality, the Advanced Potion-Making book, Veritaserum, and three small Tome of Mastery fixes.

**Architecture:**
- **Pure rules first.** Every number and decision lives in a Minecraft-free class with JUnit 5 tests: `spell/summon/FiendfyreRules`, `SerpensortiaRules`, the existing `PatronusRules`, `spell/dark/DarkRules`, `brewing/BrewRules`, `PotionRules`, the new `brewing/BrewQuality`, and `spell/mastery/TomeRules`.
- **Spells.** Three spells follow the Phase 7a plan, `docs/superpowers/plans/2026-10-02-spell-mastery-phase7a.md`: Serpensortia (its Tasks 5–7), Fiendfyre (Tasks 8–10) and the Patronus messenger (Task 4). Its code blocks are the reference design. Its text anchors are three weeks old, and the Patronus forms work has since merged (commit `67b00c3a`), so every task below lists the deltas that override 7a.
- **Brewing.** Brewing extends the existing `WizardCauldronBlockEntity`. A quality grade is stored on each bottle as a data component. Advanced recipes refuse a step from a player who has not read the book. Veritaserum and Remorse are new `BrewedPotionItem` kinds.
- **Behaviour checks.** Behaviour that unit tests cannot reach is pinned by Forge gametests in `gametest/LeftoversGameTests.java`, plus a `runServer` boot.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5, Forge gametests, Python 3 + Pillow for textures.

**Spec:** this plan implements the "later / not planned" items of three roadmaps. The executor reads the three roadmaps and the 7a plan:
- `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md` (Phase 7, Decision 11);
- `docs/superpowers/specs/2026-10-07-cauldron-brewing-roadmap.md` ("Later": quality tiers, the Advanced Potion-Making book, Veritaserum);
- `docs/superpowers/specs/2026-10-07-dark-magic-roadmap.md` ("Later": grey spells, Remorse, house reactions);
- `docs/superpowers/plans/2026-10-02-spell-mastery-phase7a.md`;
- the deferred items in `docs/superpowers/handoff/rulings.md` (the Phase 1b line: "Deferred: picker scroll cue; creative consumes tome; trial vault tomes").

The roadmaps leave many values open. The planner chose them; they are listed under "Calls made by the planner" at the end.

**Ledger:** `.superpowers/sdd/2026-10-27-roadmap-leftovers/progress.md` (never committed).

**Order and lanes:** tasks run in the order given, **one implementer at a time** (rulings.md, Phase 6a: "no more parallel implementers on this branch"). Reviews may overlap. Dependencies:
- 2 needs 1, and 3 needs 2;
- 5 needs 4, and 6 needs 5;
- 8 needs 7;
- 10 needs 9;
- 12 needs 9 and 11;
- 13 needs 11;
- 15 needs 12 and 14;
- 17 is last.

**Models:** Sonnet 5.5 implements every task except Task 2, which goes to Opus 5.5 because it consolidates the Fiendfyre channel out of two 2,000- and 4,000-line files. All reviews use Opus 5.5. Never Haiku.

**Review gates:** each task gets one Opus review (spec, then quality), then fix rounds as needed. Per the user's rule, no re-review after a fix round unless the fix is risky. The controller runs `timeout 60 git push origin sorting-memory` after every commit; implementers never push.

## Global Constraints

**Code**
- **Version:** Forge 51.0.33, Minecraft 1.21, Java 21.
- **Server/client safety:**
  - Authoritative logic runs on the server.
  - Nothing in `spell/`, `entity/`, `item/`, `brewing/`, `block/`, `loot/`, `network/` or `event/ModEvents.java` imports `net.minecraft.client.*` or `com.hogwartsmagic.client.*`.
  - Renderers, models and screens live in `client/` and are registered only in `ClientModEvents`.
  - Client screens open through `ClientSideBridge.handlePacket("<op>", packet)`, as every existing screen packet does.
  - Every step must be checked to run on a dedicated server (`runServer`).
- **Render and client code:** no `switch` on an enum. Use if-chains, `EnumSet` or `EnumMap`.
- **Entity events:** use no `broadcastEntityEvent` unless needed. If one is, never use bytes 21, 35 or 63; use 61/62/64/66/67 (the goblins hold 61).
- **1.21 API:**
  - `MobType` is gone (undead is `EntityTypeTags.UNDEAD`).
  - `getDimensions` is final, so override `getDefaultDimensions(Pose)`.
  - `canBreatheUnderwater` is final.
  - `addAdditionalSaveData` and `readAdditionalSaveData` keep the visibility of the class they override (public on `Mob` subclasses).
  - Loot tables use `minecraft:enchanted_count_increase`, not `looting_enchant`.
  - Datapack folders are singular (`loot_table/`, `advancement/`, `recipe/`, `tags/entity_type/`).
- **Terrain:** terrain changes go through `TerrainRules.canPlace` / `TerrainRules.canBreak`, which respect `mobGriefing`, claims, spawn protection and Cave Inimicum wards. `canBreak` posts a real `BreakEvent`, so call it only as the last step before a block is destroyed. Never load a chunk: check `level.isLoaded(pos)` first.
- **Event handlers:** any new hurt, death or effect handler runs at LOWEST priority behind an `isCanceled` guard.
- **Cures:** effect removal for `bleeding` and `cursed_wound` goes through the existing cure helpers.
- **Per-tick handlers:** gate on a field, an effect or `tickCount % n` before reading persistent data.

**Shared files (another agent is writing a Pensieve plan in this checkout)**
- Before editing `ExampleMod.java`, `ModEntities.java`, `ClientModEvents.java`, `ModEvents.java`, `SpellPackets.java`, `ClientSideBridge`, `ClientRuntimeBridge` or `en_us.json`, re-read the file from disk.
- Never touch any file with `pensieve` in its path, or `docs/superpowers/plans/*pensieve*`.
- Run `git diff --stat` before staging, and stage only the named files. If a file you must stage holds hunks that are not yours, stop and report.
- Never stage `.claude/`, `logs/`, `build.gradle` or `diagon_alley.nbt` (they hold uncommitted user changes).

**Gradle**
- Never run gradle while a Minecraft dev client runs: `tasklist | grep -i javaw` must print nothing. The daemon is fine.
- Take the lock with `mkdir .superpowers/gradle.lock`. If it exists, wait 20 s and retry. `rmdir .superpowers/gradle.lock` afterwards, even on failure.
- **Unit tests:** `./gradlew test --console=plain` (or `--tests "<pattern>"`) must end with `BUILD SUCCESSFUL`.
- **Gametests:** `./gradlew runGameTestServer --console=plain`. The log must say that all required tests passed, and the exit code must be 0. New tests log `LEFTOVERS-GAMETEST PASS <name>`. Grep for those lines and quote them in the report.
- **Dedicated server:**
  - Run `printf 'stop\n' | ./gradlew runServer --console=plain > .superpowers/runserver.log 2>&1`.
  - It must print `Done (`.
  - `grep -n "ERROR\|Exception" .superpowers/runserver.log` must show nothing new that names `hogwartsmagic`, a loot table, an advancement or a `client` class.
  - Quote any `WARN` that names the mod.

**Resources**
- `en_us.json` must stay valid JSON. Check it with `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`, which prints nothing on success.
  - A replaced line keeps its trailing comma.
  - An inserted line ends with a comma and goes after a line that already has one.
  - A literal `%` is written `%%`.
- Every other JSON file touched is checked the same way.
- **Network:** a task that adds or changes a packet bumps `networkProtocolVersion` in `SpellPackets.java` by one from the value on disk (10 when this plan was written; another session may have raised it).

**Commits**
- One commit per task, using the message given.
- The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (or Sonnet 5.5 for a Sonnet implementer), passed as a second `-m`.
- No apostrophes inside bash heredoc bodies: use `-m` arguments or the Write tool.

**Line numbers drift.** Every anchor below is text: find it with Grep.

## Review Focus

1. **A rogue Fiendfyre serpent among other players.** It may hunt its caster, but it must never hunt a creative or spectator player, or another player when PvP is off or the caster cannot harm them. This is `FiendfyreRules.rogueMayTarget`, tested in Task 1 and wired in Task 2.
2. **Conjured snakes whose owner is gone** (logged out, dead, in another dimension, or a dedicated-server restart). They must vanish within 2 s and never turn wild. This is the `snakeVanishesWhenItsOwnerIsGone` gametest in Task 4.
3. **Two players at one cauldron.** The Advanced Potion-Making gate is checked against the player making each step. A refused step consumes nothing and does not ruin the brew. This is the `unreadPlayerIsRefusedWithoutLoss` gametest in Task 14.
4. **Old saves and old bottles.** A cauldron saved before quality tracking existed has no `Q*` keys: it loads with zero counters and grades Standard. A bottle with no `brew_quality` component drinks as Standard. This is `BrewQualityTest.emptyCountersAreStandard`, plus the `oldCauldronSaveLoadsAsStandard` gametest in Task 11.
5. **Remorse when there is nothing to lighten** (Clean, or at the Horcrux floor). It must change nothing and must not spend the player's Remorse window. This is the `remorseAtTheFloorKeepsTheWindow` gametest in Task 9.

---

### Task 1: Fiendfyre rules, and fire that respects the land

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/RankIndex.java`, `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreRules.java`
- Create: `src/test/java/com/hogwartsmagic/spell/summon/FiendfyreRulesTest.java`
- Create: `src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java`, `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` (`spreadFire`), `src/main/java/com/hogwartsmagic/spell/SpellType.java` (FIENDFYRE case), `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (FIENDFYRE block branch)

**Interfaces:**
- Consumes: `TerrainRules.canPlace(ServerLevel, BlockPos, @Nullable Entity)`, `CombatEffects.responsible(@Nullable Entity)`.
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private, clamped).
  - `FiendfyreRules`: every constant and method listed in the 7a plan Task 1 "Produces" for `FiendfyreRules`, with the same values.
  - New in `FiendfyreRules`: `static boolean rogueMayTarget(boolean isCaster, boolean isPlayer, boolean creativeOrSpectator, boolean pvpAllowed, boolean casterCanHarm)`.
  - `FiendfyreEntity.setCaster(@Nullable Entity caster)`.
  - `LeftoversGameTests` (holder class, `@GameTestHolder(ExampleMod.MODID)`, template `hogwartsmagic:gametest/empty3`). Later tasks add their gametests here.

- [ ] **Step 1: Write the failing test.** Copy `FiendfyreRulesTest` from the 7a plan, Task 1 Step 1 (the `FiendfyreRulesTest.java` block). Then add:

```java
@Test
void rogueSparesTheProtected() {
    assertFalse(FiendfyreRules.rogueMayTarget(true, true, true, true, true));    // creative caster
    assertTrue(FiendfyreRules.rogueMayTarget(true, true, false, false, false));  // its own caster, PvP off
    assertFalse(FiendfyreRules.rogueMayTarget(false, true, false, false, true)); // other player, PvP off
    assertFalse(FiendfyreRules.rogueMayTarget(false, true, false, true, false)); // caster may not harm them
    assertTrue(FiendfyreRules.rogueMayTarget(false, true, false, true, true));
    assertTrue(FiendfyreRules.rogueMayTarget(false, false, false, false, false)); // any mob
}
```

- [ ] **Step 2: Run it and confirm it fails.** Run `./gradlew test --tests "com.hogwartsmagic.spell.summon.FiendfyreRulesTest"`. It should fail to compile (`FiendfyreRules` not found).

- [ ] **Step 3: Implement the rules.** Copy `RankIndex` and `FiendfyreRules` from 7a Task 1 Step 3, then add `rogueMayTarget`:
  - false if `creativeOrSpectator`;
  - true if `isCaster`;
  - `pvpAllowed && casterCanHarm` if `isPlayer`;
  - otherwise true.

- [ ] **Step 4: Make the fire terrain-safe.** Apply 7a Task 8 Steps 1–3 with these deltas:
  - **Edit `FiendfyreEntity` in place; do not replace the file.** Add the `casterUuid` field, `setCaster`, the save/load of `"Caster"`, and the `isLoaded` / `isEmptyBlock` / `canPlace` guard in `spreadFire`. Keep the existing method visibility.
  - **Handing over the caster:** find the place where `SpellType`'s FIENDFYRE case and `SpellBeamEntity`'s FIENDFYRE block branch construct `FiendfyreEntity`, and call `setCaster(caster)` / `setCaster(getOwner())` before `addFreshEntity`.
  - **The serpent:** in `FiendfyreSerpentEntity.spreadFire`, every placement checks `serverLevel.isLoaded(target) && serverLevel.isEmptyBlock(target) && TerrainRules.canPlace(serverLevel, target, caster)`. The caster is the `ServerPlayer` that `casterUuid` resolves to, or null.

- [ ] **Step 5: Add the gametest.** Create `LeftoversGameTests` with `groundFireRespectsMobGriefing` (template `EMPTY`, `timeoutTicks = 200`):
  1. Save the `mobGriefing` value, then set it false.
  2. Fill a 9×9 stone floor at y 1 with `helper.setBlock`.
  3. Spawn a `FiendfyreEntity` with `setCaster(null)` at (4.5, 2, 4.5).
  4. In `helper.runAtTickTime(120, ...)`, assert no `Blocks.FIRE` in the 9×9×3 volume above the floor.
  5. In the same block, restore the saved game rule in a `finally`, so it is restored even when the assertion fails. Gametests share one level.
  6. Call `helper.succeed()` and log `LEFTOVERS-GAMETEST PASS groundFireRespectsMobGriefing`.

- [ ] **Step 6: Verify.** Under the gradle lock, run `./gradlew test --console=plain` and then `./gradlew runGameTestServer --console=plain`. Expect `BUILD SUCCESSFUL`, the new PASS line, and every test passing.

- [ ] **Step 7: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/RankIndex.java src/main/java/com/hogwartsmagic/spell/summon/FiendfyreRules.java src/test/java/com/hogwartsmagic/spell/summon/FiendfyreRulesTest.java src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
git commit -m "Add the Fiendfyre rank rules and make its fire respect mobGriefing, claims and wards" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Fiendfyre by rank, with a red telegraph before a serpent turns (Opus)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` (rewritten per 7a Task 9), `src/main/java/com/hogwartsmagic/client/FiendfyreSerpentRenderer.java` (red tint), `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (the Fiendfyre start, heartbeat and release go to `FiendfyreChannel`; `getFiendfyreStartupCost`, `ensureFiendfyreExists` and `stopFiendfyreChannel` move or go), `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`tickFiendfyreChannel`, `getOrCreateFiendfyreEntity` and `stopFiendfyreChannel` go; every other `TAG_FIENDFYRE_CHANNELING` reference, which `grep -n` finds in the interrupt path and the channel-conflict checks, calls `FiendfyreChannel.isChanneling` / `FiendfyreChannel.stop`), `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, `src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: `FiendfyreRules` including `rogueMayTarget` (Task 1); `SpellMasteryData.getRank`, `SpellCosts.masteryMultiplier/darkMultiplier/startCooldown`, `DarkMagicData.onDarkCastPaid(ServerPlayer, SpellType)`, `MasteryAwards` (existing).
- Produces: exactly the 7a Task 9 "Produces" list:
  - `FiendfyreChannel.TAG_CHANNELING` and `TAG_ADVANCE` (key strings unchanged);
  - `isChanneling(Player)`, `heartbeat(ServerPlayer, ItemStack, boolean advance)`, `release(ServerPlayer, ItemStack)`, `stop(ServerPlayer, ItemStack, boolean startCooldown)`, `tick(ServerPlayer, CompoundTag modData)`, and package-private `serpents(ServerPlayer)`;
  - `FiendfyreSerpentEntity.STATE_CONTROLLED/STATE_TELEGRAPH/STATE_ROGUE/STATE_DISPATCHED`, `setCaster(ServerPlayer)`, `configure(int index, int count, int rogueTurnTick, int durationTicks)`, `getState()`, `isTelegraphing()`, `isRogue()`.

Port 7a Task 9, reading its code as the reference. **Deltas that override 7a:**
1. **Read today's code before moving it.** The channel lives in `SpellPackets.CastSpellPacket.castAs` (the `p.channelHeartbeat && spell == SpellType.FIENDFYRE` block and the release block that calls `stopFiendfyreChannel`) and in `ModEvents.tickFiendfyreChannel`. Move it as it is today, then add the rank rules. Keep these intact:
   - the startup cost **with** its `SpellCosts.darkMultiplier(player, SpellType.FIENDFYRE)` factor;
   - the existing upkeep formula and interval;
   - the existing `DarkMagicData.onDarkCastPaid(player, spell)` call, exactly once when the channel starts (moved from `SpellPackets`);
   - the cooldown and the mastery award that `SpellPackets.stopFiendfyreChannel` performs today;
   - the possession refusal (`PossessionRules` already blocks `fiendfyre`; no change).
2. **Rogue targets:** the rogue serpent picks the nearest living creature within `ROGUE_SEARCH_RADIUS` for which `FiendfyreRules.rogueMayTarget(...)` is true. For players, `pvpAllowed = server.isPvpAllowed()` and `casterCanHarm = caster != null && caster.canHarmPlayer(player)`. An offline caster counts as `casterCanHarm = false`.
3. **No leftover duplicates:** after the task, `grep -rn "TAG_FIENDFYRE_\|hogwartsmagic:fiendfyre_" src/main/java` finds the keys only in `FiendfyreChannel.java` and `FiendfyreSerpentEntity.java`.
4. **Lang:** the literal `Component.literal("Fiendfyre collapses")` becomes `message.hogwartsmagic.fiendfyre.collapses` ("The Fiendfyre collapses"). Add `message.hogwartsmagic.fiendfyre.writhes` ("The Fiendfyre writhes against your will!"). Replace `spell.hogwartsmagic.fiendfyre.desc` with the 7a Task 9 text minus its Master sentence: "Hold to loose serpents of cursed fire that follow your aim; hold attack to drive them further. Novice: one serpent for 6s, with a 15%% chance it turns on you (it glows red first). Adept: 8s, 8%%. Expert: 10s, 3%%, two serpents. Master: 12s, three serpents, never rogue." (Task 3 extends the Master part).
5. **Renderer:** `FiendfyreSerpentRenderer` tints red when `isTelegraphing() || isRogue()`, using if-chains with no enum switch. The state is a synced `byte`.

- [ ] **Step 1: Write the failing gametest** `serpentTelegraphsThenTurns` (`timeoutTicks = 200`):
  1. Create a `FiendfyreSerpentEntity` with no caster and `configure(0, 1, 60, 120)`.
  2. Add it at (1.5, 3, 1.5).
  3. At tick 45 assert `isTelegraphing()` and `!isRogue()`.
  4. At tick 65 assert `isRogue()`.
  5. Log PASS.

  Run `./gradlew runGameTestServer`. It should fail to compile (`configure` does not exist).
- [ ] **Step 2: Implement** `FiendfyreChannel` and the serpent rewrite per 7a Task 9 plus the deltas above.
- [ ] **Step 3: Verify.**
  1. Run `./gradlew test` and `./gradlew runGameTestServer` under the lock. Expect all passing.
  2. Run the `runServer` boot. Expect `Done (` and no new mod errors.
  3. Run the grep from delta 3.
  4. Run the `en_us.json` check.
- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/java/com/hogwartsmagic/client/FiendfyreSerpentRenderer.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Shape Fiendfyre by rank: longer fire, more serpents, and a rogue serpent that glows red before it turns" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Master Fiendfyre: separate targets and Horcrux destruction

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java`, `src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java`, `src/main/resources/assets/hogwartsmagic/lang/en_us.json`
- Modify: `src/test/java/com/hogwartsmagic/spell/summon/FiendfyreRulesTest.java`

**Interfaces:**
- Consumes: Task 2's channel; `TerrainRules.canBreak` (posts the real `BreakEvent`; `ModEvents.onBlockBreak` already calls `HorcruxBlockEntity.handleForcedDestruction`); `HorcruxBlockEntity.isActive()/isPrimed()`.
- Produces: `FiendfyreSerpentEntity.dispatchAt(LivingEntity target, long now)`, `isAvailableForDispatch()`, `setDestroysHorcrux(boolean)`; and `FiendfyreRules.dispatchMayTarget(boolean isCaster, boolean isPlayer, boolean creativeOrSpectator, boolean pvpAllowed, boolean casterCanHarm, boolean alliedToCaster)`.

Port 7a Task 10 (the rising edge of `fiendfyreAdvance` sends the next free serpent; a serpent devours an active or primed Horcrux within `HORCRUX_REACH`, calling `canBreak` last). **Deltas:**
- **Who a serpent may be sent at.** `aimedTarget` filters through `dispatchMayTarget`, which is:
  - false for the caster;
  - false for creative or spectator targets;
  - false for allies: `alliedToCaster` is true for mobs carrying `CombatTags.ALLIED` that the caster owns (`OwnableEntity.getOwnerUUID()` equals the caster), and for players `isAlliedTo` the caster;
  - `pvpAllowed && casterCanHarm` for other players;
  - true otherwise.

  This closes the PvP gap the 6b rulings closed for puppet beams.
- **Lang:** add `message.hogwartsmagic.fiendfyre.dispatch` ("The Fiendfyre lunges at %s") and `message.hogwartsmagic.fiendfyre.horcrux` ("The Fiendfyre devours a Horcrux"). Extend the desc's Master sentence with: "left-click while holding sends the next serpent at the creature you aim at, and the fire devours Horcruxes."

- [ ] **Step 1: Test.** Add `dispatchSparesTheCasterAlliesAndProtectedPlayers` to `FiendfyreRulesTest`, with one assert per rule above (6 asserts). Run it and see it fail to compile.
- [ ] **Step 2: Implement** per 7a Task 10 plus the deltas.
- [ ] **Step 3: Verify.** Run `./gradlew test`, the `runServer` boot and the `en_us.json` check.
- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/FiendfyreChannel.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java src/main/java/com/hogwartsmagic/spell/summon/FiendfyreRules.java src/test/java/com/hogwartsmagic/spell/summon/FiendfyreRulesTest.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master send Fiendfyre serpents at separate targets and burn Horcruxes" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The snake entity

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaRules.java`, `src/test/java/com/hogwartsmagic/spell/summon/SerpensortiaRulesTest.java`
- Create: `src/main/java/com/hogwartsmagic/entity/SnakeEntity.java`, `src/main/java/com/hogwartsmagic/client/model/SnakeModel.java`, `src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java`
- Create: `build_scripts/generate_snake_texture.py`, `src/main/resources/assets/hogwartsmagic/textures/entity/snake.png`, `src/main/resources/assets/hogwartsmagic/models/item/snake_spawn_egg.json`
- Modify: `ModEntities.java` (`SNAKE`), `event/ModEntityAttributes.java`, `ExampleMod.java` (`SNAKE_SPAWN_EGG` and its creative tab entry, next to the other spawn eggs), `event/ClientModEvents.java` (layer and renderer), `en_us.json`, `gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: `RankIndex` (Task 1); `CombatTags.ALLIED`, `SpellMasteryData.getRank`, `StudentEntity` (existing).
- Produces:
  - `SerpensortiaRules`: the 7a Task 1 constants and methods, plus `OWNER_GONE_TICKS = 40`.
  - `ModEntities.SNAKE`, `ExampleMod.SNAKE_SPAWN_EGG`.
  - `SnakeEntity`: the 7a Task 5 "Produces" list (`conjure`, `isConjured`, `getOwnerUUID`, `getOwner`, `isFairTarget`, `getMode`, `commandAttack`, `commandMode`, `vanish`, `static isParselmouth(Player)`).
  - `SnakeModel.LAYER_LOCATION`, `createBodyLayer()`; `SnakeRenderer`.

Port 7a Task 5 (entity, the Java-coded six-segment `SnakeModel` with slither and tongue flick, the Pillow texture generator, the spawn egg). **The model is the hand-written Java model from 7a.** The Blockbench MCP is offline, and a Blockbench rebuild is optional later work, not part of this task. **Deltas:**
- **Owner gone:** a conjured snake whose owner cannot be resolved for `OWNER_GONE_TICKS` consecutive ticks vanishes (puff of smoke, `vanish()`). That covers an owner who is offline, dead, in another dimension, or missing after a restart. Count with an int field, never a per-tick persistent-data read. A wild (spawn-egg) snake is unaffected.
- **API:** follow the 1.21 rules in Global Constraints (`getDefaultDimensions`, no `MobType`, public save methods).
- **Model code:** no enum switch.
- **Sounds:** vanilla only (silverfish ambient/hurt, cat hiss on bite). No new sound files.

- [ ] **Step 1: Write the failing tests.**
  - Copy `SerpensortiaRulesTest` from 7a Task 1, and add `assertEquals(40, SerpensortiaRules.OWNER_GONE_TICKS)`.
  - Add the gametest `snakeNeverTurnsOnItsOwner` (`timeoutTicks = 160`):
    1. Spawn the owner, a no-AI villager, at (1.5, 2, 1.5), and a no-AI zombie at (5.5, 2, 1.5).
    2. Conjure a snake beside the owner with `conjure(owner, 400, 0)`.
    3. Call `snake.setTarget(owner)`.
    4. At tick 100, assert `snake.getTarget() != owner`, then call `snake.setTarget(owner)` once more and assert the target is still not the owner. A refused target holds even through `setTarget`.
  - Add the gametest `snakeVanishesWhenItsOwnerIsGone` (`timeoutTicks = 120`):
    1. Conjure with a no-AI pig as owner.
    2. Discard the pig at tick 5.
    3. Use `succeedWhen` to wait until `snake.isRemoved()`, and fail if it is still alive at tick 100.
- [ ] **Step 2: Run them and confirm they fail.** Expect compile errors.
- [ ] **Step 3: Implement** per 7a Task 5 plus the deltas. Generate the texture with `python build_scripts/generate_snake_texture.py`.
- [ ] **Step 4: Verify.** Run `./gradlew test`, `./gradlew runGameTestServer` (both PASS lines), the `runServer` boot (no `SnakeRenderer` or `client` class in any stack) and the `en_us.json` check.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaRules.java src/test/java/com/hogwartsmagic/spell/summon/SerpensortiaRulesTest.java src/main/java/com/hogwartsmagic/entity/SnakeEntity.java src/main/java/com/hogwartsmagic/client/model/SnakeModel.java src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java build_scripts/generate_snake_texture.py src/main/resources/assets/hogwartsmagic/textures/entity/snake.png src/main/resources/assets/hogwartsmagic/models/item/snake_spawn_egg.json src/main/java/com/hogwartsmagic/entity/ModEntities.java src/main/java/com/hogwartsmagic/event/ModEntityAttributes.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Add the snake: an ownable, poison-biting mob with a Java-coded slithering model" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Serpensortia conjures snakes by rank

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (`case SERPENSORTIA ->`, today the three tagged cave spiders), `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (block-hit branch), `en_us.json`, `gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: Task 4; `DisableSupport.casterRank(@Nullable Entity, SpellType)`; `CombatEffects.responsible`.
- Produces: `SerpensortiaSpell.conjureAt(ServerLevel level, Vec3 at, @Nullable LivingEntity target, @Nullable Entity caster)` → `int`; `SerpensortiaSpell.snakesOf(ServerLevel level, Entity owner)` → `List<SnakeEntity>`.

Port 7a Task 6, with these deltas:
- **Block hit:** the `ChamberDoorBlock.speakTo` branch stays first. The new `else if (spell == SpellType.SERPENSORTIA)` block branch goes directly after it.
- **Counts:**
  - Players use their own rank: 1/2/3/3 snakes.
  - Bosses get rank 3 through `casterRank` (3 snakes, Poison II).
  - Other NPCs get rank 1.
- **Unchanged:** Darkness gain (`DarkRules` already lists `serpensortia`) and the block-utility XP.
- **Lang:** replace the `spell.hogwartsmagic.serpensortia.desc` line with the 7a Task 6 Step 4 text, minus its Master sentence (Task 6 restores it): "Conjures snakes loyal to you that bite with poison: 1 for 20s (Novice), 2 for 30s (Adept), 3 for 40s with Poison II (Expert), 3 for 60s (Master)."

- [ ] **Step 1: Write the failing gametest** `serpensortiaConjuresByRank` (`timeoutTicks = 100`):
  1. Use a mock server player (`helper.makeMockServerPlayerInLevel()`) who has learned Serpensortia (`MagicBookItem.learnSpellIfAbsent`).
  2. `conjureAt(...)` at rank 1 returns 1, and that snake has `getOwnerUUID()` equal to the player and carries `CombatTags.ALLIED`.
  3. Raise the rank to Expert with `SpellMasteryData.setXp(player, SpellType.SERPENSORTIA, <the Expert threshold from Config.getMasteryThresholds()>)`.
  4. Conjure again. It returns 3, and the first snake `isRemoved()` (a new cast dismisses the old snakes).
- [ ] **Step 2: Run it and confirm it fails** to compile.
- [ ] **Step 3: Implement** per 7a Task 6 plus the deltas.
- [ ] **Step 4: Verify.** Run `./gradlew test`, `runGameTestServer`, the `runServer` boot and the `en_us.json` check. Then run `grep -n "CaveSpider" src/main/java/com/hogwartsmagic/spell/SpellType.java` and expect no output.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Make Serpensortia conjure loyal snakes by rank instead of cave spiders" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Parseltongue (Master commands)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java`, `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`castAs`), `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`public static boolean isLeftClickSelfCast`), `en_us.json`

**Interfaces:**
- Consumes: Tasks 4–5.
- Produces: `SerpensortiaSpell.command(ServerPlayer player)`.

Port 7a Task 7. **Deltas:**
- `isLeftClickSelfCast` is now `public static`; add `|| spell == SpellType.SERPENSORTIA` to its return chain.
- **The `castAs` hook:** place `if (spell == SpellType.SERPENSORTIA && p.selfCastProtego) { SerpensortiaSpell.command(player); return; }` right after the Apparition early-return. It must come before any mana check, cooldown check or `DarkMagicData.onDarkCastPaid` call, so a command never costs mana, Darkness or cooldown.
- **The client side:** check the client left-click path in `ModEvents` (the block that calls `isLeftClickSelfCast`). If a client-side mana or cooldown check would block the packet, exempt `SERPENSORTIA` there.
- **The Master desc sentence** returns: "At Master you speak Parseltongue: left-click to send your snakes at a creature, or to switch them between following you and guarding a spot. Wild snakes leave a Master alone."
- **Lang:** the four `message.hogwartsmagic.parseltongue.*` keys from 7a Task 7 Step 4.

- [ ] **Step 1: Implement** per 7a Task 7 plus the deltas.
- [ ] **Step 2: Verify.**
  - Run `./gradlew test` and the `runServer` boot.
  - `grep -n "isLeftClickSelfCast" src/main/java/com/hogwartsmagic/event/ModEvents.java` shows the method and its existing callers, unchanged in number.
  - Run the `en_us.json` check.
- [ ] **Step 3: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/SerpensortiaSpell.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master of Serpensortia command their snakes in Parseltongue" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The Master Patronus lights the dark and drives off the undead

This task adapts 7a's Master aura to the merged forms design, where every corporeal Patronus already sheds its own light. What the Master adds:
- the Patronus follows its caster, so its light goes with them;
- caster and allies within 8 blocks get Night Vision;
- undead and Inferi within 6 blocks are pushed out and lose their aggression toward players.

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/summon/PatronusRules.java`, `src/test/java/com/hogwartsmagic/spell/summon/PatronusRulesTest.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/PatronusEntity.java`, `src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java` (`summonCorporeal`)
- Modify: `en_us.json` (`spell.hogwartsmagic.expecto_patronum.desc`), `gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: `PatronusRules.personalForm`, `PatronusSpell.formFor` (existing); `InferiusEntity` (existing; it is **not** in `#minecraft:undead`, so it is matched by class; do not add it to the tag).
- Produces:
  - `PatronusRules`:
    - `MASTER_RANK = 4`, `UNDEAD_RADIUS = 6.0`, `UNDEAD_INTERVAL = 5`, `UNDEAD_PUSH = 0.6`;
    - `NIGHT_VISION_TICKS = 260`, `NIGHT_VISION_INTERVAL = 40`, `LIGHT_RADIUS = 8.0`;
    - `FOLLOW_START = 6.0`, `FOLLOW_STOP = 3.0`, `TELEPORT_DISTANCE = 24.0`;
    - `static boolean masterAura(int rank)`;
    - `static boolean repels(boolean undeadOrInferius, boolean boss, double distanceSqr)`.
  - `PatronusEntity`: `setOwner(@Nullable UUID)`, `getOwnerUuid()`, `setMasterAura(boolean)`, `hasMasterAura()`, `setLifespan(int ticks)`. Owner, aura and lifespan are all saved; `Owner`, `Aura` and `Lifespan` are the NBT keys.

- [ ] **Step 1: Write the failing tests** in `PatronusRulesTest`:
```java
@Test
void masterAuraOnlyAtMaster() {
    assertFalse(PatronusRules.masterAura(3));
    assertTrue(PatronusRules.masterAura(4));
    assertTrue(PatronusRules.masterAura(9));
}

@Test
void repelsUndeadInsideSixBlocksButNotBosses() {
    assertTrue(PatronusRules.repels(true, false, 36.0));
    assertFalse(PatronusRules.repels(true, false, 36.01));
    assertFalse(PatronusRules.repels(true, true, 1.0));
    assertFalse(PatronusRules.repels(false, false, 1.0));
}

@Test
void nightVisionNeverFlickers() {
    // Vanilla flickers Night Vision under 200 ticks; a refresh every 40 keeps it above that.
    assertTrue(PatronusRules.NIGHT_VISION_TICKS - PatronusRules.NIGHT_VISION_INTERVAL > 200);
}
```
Run them and see them fail to compile.

- [ ] **Step 2: Implement.**
  - **`PatronusRules`:** the constants and the two methods.
  - **`PatronusEntity` fields and saving:** add the fields and their save/load. Keep the existing `Form` key.
  - **Follow goal (priority 4):** active only when `hasMasterAura()`, the owner resolves to a live `ServerPlayer` in the same level, and the Patronus has no target. It walks toward the owner when it is farther than `FOLLOW_START` and stops inside `FOLLOW_STOP`. Beyond `TELEPORT_DISTANCE` it teleports next to the owner, but only into a spot where `level.noCollision(this, box)` is true.
  - **Server tick, when `hasMasterAura()`:**
    - every `NIGHT_VISION_INTERVAL` ticks, give `MobEffects.NIGHT_VISION` (`NIGHT_VISION_TICKS`, ambient, no particles) to the owner and to every non-spectator player within `LIGHT_RADIUS`;
    - every `UNDEAD_INTERVAL` ticks, for each `Mob` within `UNDEAD_RADIUS` that `repels(...)` (undead = `getType().is(EntityTypeTags.UNDEAD) || mob instanceof InferiusEntity`; boss = `CombatTags.isBoss(mob)`), push it horizontally away by `UNDEAD_PUSH` (`hurtMarked = true`) and clear its target when the target is a `Player`.
  - **`PatronusSpell.summonCorporeal`:** call `setOwner(player.getUUID())` and `setMasterAura(PatronusRules.masterAura(rank))`.
  - **Desc:** append " A Master's Patronus follows you, lights the dark around you and drives the undead back." to the existing text (keep its `%%`).
- [ ] **Step 3: Write the gametest** `masterPatronusRepelsUndead` (`timeoutTicks = 100`):
  1. Add a `PatronusEntity` with `setMasterAura(true)` and a no-AI zombie 3 blocks away.
  2. At tick 40, assert the zombie is farther than 3.5 blocks.
  3. A second Patronus without the aura, beside a second zombie 3 blocks away (10 blocks apart from the first pair), leaves its zombie within 3.2 blocks.

  Also write `masterPatronusGivesNightVision`: a mock server player as owner, 2 blocks from a Master Patronus, has `NIGHT_VISION` by tick 45.
- [ ] **Step 4: Verify.** Run `./gradlew test`, `runGameTestServer`, the `runServer` boot and the `en_us.json` check.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/PatronusRules.java src/test/java/com/hogwartsmagic/spell/summon/PatronusRulesTest.java src/main/java/com/hogwartsmagic/entity/PatronusEntity.java src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Let a Master Patronus follow its caster, light the dark and drive back the undead" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The Patronus messenger and /patronus

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/summon/PatronusMessenger.java`, `src/main/java/com/hogwartsmagic/command/PatronusCommand.java`
- Modify: `PatronusRules.java`, `PatronusRulesTest.java`, `PatronusSpell.java` (`setForm`), `ExampleMod.java` (`onRegisterCommands`), `en_us.json`

**Interfaces:**
- Consumes: Task 7's `PatronusEntity` setters; `PatronusSpell.formFor(ServerPlayer)`; `SupportMana.trySpend`, `SpellCosts.manaCost`, `CombatEffects.isCastingBlocked/notifyCastBlocked`, `MagicWandItem.isAnyWand`, `MagicBookItem.isSpellLearned`, `DarkCosts.corporealPatronusChance(ServerPlayer, float)`.
- Produces:
  - `PatronusRules`: `MESSAGE_RANGE = 500`, `MESSAGE_MAX_LENGTH = 200`, `MESSAGE_COOLDOWN_TICKS = 600`, `MESSENGER_TICKS = 100`; `static boolean sendsMessages(int rank)` (Master only), `static boolean inMessageRange(double distanceSqr)`, `static long messageCooldownLeft(boolean sentBefore, long lastSentAt, long now)`, `static String sanitizeMessage(String raw)`.
  - `PatronusMessenger.send(ServerPlayer sender, ServerPlayer recipient, String rawText)` → `boolean`.
  - `PatronusSpell.setForm(ServerPlayer, PatronusForm)`.
  - `PatronusCommand.register(CommandDispatcher<CommandSourceStack>)`: `/patronus <player> <message>` (level 0), and `/patronus form <1-8>` (level 2, a testing aid).

Port 7a Task 4 and the matching parts of 7a Task 1. Copy `sanitizeMessage` (lines 444–458 of 7a), `inMessageRange` and `messageCooldownLeft`. **Deltas:**
- **The messenger entity:** `new PatronusEntity(...)` with `setFormId(PatronusSpell.formFor(sender).id())`, `setLifespan(MESSENGER_TICKS)`, `setOwner(sender.getUUID())` and `setMasterAura(false)`. It spawns only into collision-free space beside the recipient.
- **Dark gate:** if `DarkCosts.corporealPatronusChance(sender, 1.0f) <= 0`, refuse with `message.hogwartsmagic.patronus.too_dark` ("Your Patronus will not take shape to carry it"). This is the Tainted rule: no corporeal Patronus, so no messenger.
- **Same dimension only.** Recipient offline or in another dimension → `message.hogwartsmagic.patronus.out_of_reach`.
- **Costs:** the spell's mana through `SupportMana.trySpend` (creative is free). No combat cooldown, no Darkness, no XP.

- [ ] **Step 1: Write the failing tests.** Add `messagesReachFiveHundredBlocksAndCoolDown` and `messagesAreTrimmedStrippedAndCapped` from 7a Task 1 Step 1 (the `PatronusRulesTest` block) to the existing `PatronusRulesTest`, plus `assertFalse(PatronusRules.sendsMessages(3)); assertTrue(PatronusRules.sendsMessages(4));`. Run them and see them fail.
- [ ] **Step 2: Implement** per 7a Task 4 plus the deltas. Lang keys: the 7a Task 4 keys plus the two above.
- [ ] **Step 3: Verify.** Run `./gradlew test`, the `runServer` boot (type nothing; the command registers at boot) and the `en_us.json` check.
- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/summon/PatronusMessenger.java src/main/java/com/hogwartsmagic/command/PatronusCommand.java src/main/java/com/hogwartsmagic/spell/summon/PatronusRules.java src/test/java/com/hogwartsmagic/spell/summon/PatronusRulesTest.java src/main/java/com/hogwartsmagic/spell/summon/PatronusSpell.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master send a message by Patronus with /patronus" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 9: The Potion of Remorse

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/brewing/BrewRules.java` (`GHAST_TEAR`, `REMORSE`, `RECIPES`), `src/main/java/com/hogwartsmagic/brewing/PotionRules.java`
- Modify: `src/test/java/com/hogwartsmagic/brewing/BrewRulesPhase2Test.java` (or `BrewRulesTest.java`, whichever holds the first-step uniqueness test), `src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java` (`onRemorse`), `src/main/java/com/hogwartsmagic/Config.java` (`remorseCooldownTicks`)
- Modify: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java` (`Kind.REMORSE`, drink), `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java` (`bottledPotion`)
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (`POTION_OF_REMORSE`, creative tab), `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` (add it to the brewed potion colour handler list)
- Modify: `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` (POTIONS: `page("potions", "potion_of_remorse", 6)` after `felix_felicis_patience`)
- Create: `src/main/resources/assets/hogwartsmagic/models/item/potion_of_remorse.json` (copy `wiggenweld_potion.json`)
- Modify: `en_us.json`, `gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: `DarkMagicData.getStored/floor/add`, `DarkRewardRules.redemptionWindowOpen(boolean, int, int, int)`, `DarkRules.earns`, `Config.isDarkMagicEnabled()`, `PlayerMagicData.getModData/saveModData`.
- Produces:
  - `BrewRules.GHAST_TEAR = "minecraft:ghast_tear"`; `BrewRules.REMORSE` = `Recipe("remorse", [add GHAST_TEAR 1, add DITTANY 2, stirAnticlockwise 3, simmer 90], colors [0x9FB4C8, 0x7E98A8, 0x6C7F96, 0x8FA8C8], no tail, MAX_DOSES)`, appended to `RECIPES`.
  - `PotionRules.REMORSE_DARKNESS = 5.0f`, `REMORSE_NAUSEA_TICKS = 160`, `REMORSE_WEAKNESS_TICKS = 600`, `DEFAULT_REMORSE_COOLDOWN_TICKS = 144_000`; `static float remorseDamage(float health)` = `min(3, health - 1)`, never below 0.
  - `DarkDeeds.RemorseResult` enum `{LIGHTENED, NOTHING_TO_REGRET, SPENT, OFF}`; `DarkDeeds.onRemorse(ServerPlayer drinker, float amount)` → `RemorseResult`; key `DarkDeeds.KEY_LAST_REMORSE = "DarkMagicLastRemorse"`.
  - `Config.getRemorseCooldownTicks()` (COMMON, default 144000, range 20..10000000, next to `darkMagicRedemptionTicks`).
  - `BrewedPotionItem.Kind.REMORSE("potion_of_remorse", 0x8FA8C8)`, `ExampleMod.POTION_OF_REMORSE`.

`onRemorse` logic, in order:
1. Return `OFF` when dark magic is disabled or the drinker cannot earn (`DarkRules.earns(creative, spectator)` false).
2. Return `NOTHING_TO_REGRET` when `DarkMagicData.getStored(p) <= DarkMagicData.floor(p)`, **without** spending the window.
3. Return `SPENT` when the window is closed. The window uses play time, like `onHeal`: `Stats.PLAY_TIME` against `KEY_LAST_REMORSE` with `Config.getRemorseCooldownTicks()`.
4. Otherwise write the play time, call `DarkMagicData.add(p, -amount)` and return `LIGHTENED`.

The drink always applies the pain, whatever the result:
- `hurt(damageSources().magic(), remorseDamage(health))` when that is above 0;
- Nausea for `REMORSE_NAUSEA_TICKS`;
- Weakness for `REMORSE_WEAKNESS_TICKS`.

Then an action-bar line per result:
- `message.hogwartsmagic.remorse.lightened` "Remorse tears through you, and your soul is lighter";
- `.nothing` "You have nothing left to regret";
- `.spent` "Your remorse is spent for now";
- `.off` "You feel nothing".

- [ ] **Step 1: Write the failing tests.**
  - BrewRules: a full Remorse history (`add ghast 1, add dittany 2, stirA 3, simmer 90`) matches FINISHED with recipe `REMORSE`; `isIngredient("minecraft:ghast_tear")`; the existing first-step-uniqueness test still passes with eight recipes.
  - PotionRules: `remorseDamage(20f) == 3f`, `remorseDamage(2.5f) == 1.5f`, `remorseDamage(1f) == 0f`, `remorseDamage(0.5f) == 0f`.
  - Gametest `remorseLightensOnceAWindow` (`timeoutTicks = 60`):
    1. Use a mock server player, survival, at 20 health.
    2. Set Darkness with `DarkMagicData.set(p, 50)`.
    3. Drink: give the stack, call `finishUsingItem`. Darkness is 45.
    4. Drink a second: still 45, and the health is lower.
  - Gametest `remorseAtTheFloorKeepsTheWindow`:
    1. Set Darkness to 0 and drink; Darkness stays 0.
    2. Set it to 50 and drink: 45, because the first drink did not spend the window.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
  - Lang:
    - `item.hogwartsmagic.potion_of_remorse` "Potion of Remorse";
    - its `.tooltip` "Painful to drink. Lightens a darkened soul (once every two hours of play).";
    - `brew.hogwartsmagic.remorse` "Potion of Remorse";
    - the four messages;
    - guide `guide.hogwartsmagic.potions.potion_of_remorse.title` and `.1`–`.6`: what it does, then the four steps, then a line saying a Ghast Tear is needed.
  - **Order matters in `finishUsingItem`:** `applyDrink` already runs before the stack shrinks, so the hurt cannot void the bottle return.
- [ ] **Step 4: Verify.** Run `./gradlew test`, `runGameTestServer` and the `runServer` boot. Run the `en_us.json` check, and the JSON check on the new model file.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/brewing/BrewRules.java src/main/java/com/hogwartsmagic/brewing/PotionRules.java src/test/java/com/hogwartsmagic/brewing/ src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/models/item/potion_of_remorse.json src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Brew a Potion of Remorse that painfully lightens a darkened soul" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Grey spells and house reactions to Darkness

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java`, `src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java`, `src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java` (`onDarkCastPaid`), `src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java` (`onHeal`, `onRemorse`)
- Modify: `en_us.json` (Obliviate and Confundo descs; guide `dark_magic.weight` and `way_back` lines), `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` (page line counts)

**Interfaces:**
- Consumes: `HousePerks.getHouse(Player)` → `HogwartsHouse` (may be null when unsorted; check the method).
- Produces:
  - `DarkRules.isGrey(String spellId)`;
  - `DarkRules.baseGain` now also returns `obliviate` 0.2 and `confundo` 0.1, so `gain` does too;
  - `DarkRules.houseGainMultiplier(@Nullable String house)` (`"slytherin"` → 0.75, anything else or null → 1.0);
  - `DarkRules.houseRedemptionMultiplier(@Nullable String house)` (`"hufflepuff"` → 1.5, else 1.0);
  - `DarkRules.houseKey(@Nullable Enum<?> house)` → lower-case name or null.

**Rules:** grey spells are **not** dark. `isDark`, `darkSpellIds()` (still nine), the Corrupted mana discount (`DarkRewardRules.manaMultiplier`) and anything else keyed on `isDark` must not see them. `onDarkCastPaid` already runs for every paid cast and returns early when the gain is 0, so the grey spells need no new hook.

- [ ] **Step 1: Write the failing tests.**
```java
@Test
void greySpellsDarkenSlightlyButAreNotDark() {
    assertFalse(DarkRules.isDark("obliviate"));
    assertTrue(DarkRules.isGrey("obliviate"));
    assertTrue(DarkRules.isGrey("confundo"));
    assertFalse(DarkRules.isGrey("avada_kedavra"));
    assertEquals(0.2f, DarkRules.gain("obliviate", 1.0), 1e-6);
    assertEquals(0.1f, DarkRules.gain("confundo", 1.0), 1e-6);
    assertEquals(9, DarkRules.darkSpellIds().size());
}

@Test
void slytherinsDarkenSlowerAndHufflepuffsRedeemFaster() {
    assertEquals(0.75, DarkRules.houseGainMultiplier("slytherin"), 1e-9);
    assertEquals(1.0, DarkRules.houseGainMultiplier("gryffindor"), 1e-9);
    assertEquals(1.0, DarkRules.houseGainMultiplier(null), 1e-9);
    assertEquals(1.5, DarkRules.houseRedemptionMultiplier("hufflepuff"), 1e-9);
    assertEquals(1.0, DarkRules.houseRedemptionMultiplier("slytherin"), 1e-9);
    assertEquals(4.5f, DarkRules.gain("avada_kedavra", 1.0 * DarkRules.houseGainMultiplier("slytherin")), 1e-6);
}
```
In `DarkRewardRulesTest`, add `assertEquals(1.0f, DarkRewardRules.manaMultiplier("obliviate", DarkRules.LOST), 1e-6)`.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
  - `onDarkCastPaid` passes `Config.getDarkMagicGainMultiplier() * DarkRules.houseGainMultiplier(DarkRules.houseKey(HousePerks.getHouse(payer)))` to `gain`. The payer's house counts, not the puppet's.
  - `onHeal` subtracts `REDEMPTION_AMOUNT * houseRedemptionMultiplier(...)`.
  - `onRemorse` multiplies its `amount` the same way.
  - Deeds (the Avada innocent bonus, Horcrux +30) are not scaled.
  - Lang: append to Obliviate's and Confundo's descs " A grey charm: each cast darkens your soul a little." Add one sentence each to the guide's `weight` page ("Obliviate and Confundo are grey: they darken you a little.") and `way_back` page ("Slytherins darken more slowly; Hufflepuffs find their way back faster."). Add new numbered lines if a page has no free sentence, and raise that page's line count in `HogwartsGuideScreen`.
- [ ] **Step 4: Verify.** Run `./gradlew test`, the `runServer` boot and the `en_us.json` check.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/dark/DarkRules.java src/test/java/com/hogwartsmagic/spell/dark/DarkRulesTest.java src/test/java/com/hogwartsmagic/spell/dark/DarkRewardRulesTest.java src/main/java/com/hogwartsmagic/spell/dark/DarkMagicData.java src/main/java/com/hogwartsmagic/spell/dark/DarkDeeds.java src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Obliviate and Confundo grey charms and let houses meet Darkness differently" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Brewing quality: graded by ingredients and stirring

**Files:**
- Create: `src/main/java/com/hogwartsmagic/brewing/BrewQuality.java`, `src/test/java/com/hogwartsmagic/brewing/BrewQualityTest.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (data components `BREW_QUALITY`, `FINE_INGREDIENT`)
- Modify: `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`, `src/main/java/com/hogwartsmagic/item/SplashBrewItem.java` (tooltips)
- Modify: `src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java` (`OUTSTANDING_BREW`)
- Create: `src/main/resources/data/hogwartsmagic/advancement/hp/outstanding_brew.json` (copy `ruined_brew.json`'s shape: parent `hogwartsmagic:hp/first_brew`, one `minecraft:impossible` criterion, icon `hogwartsmagic:felix_felicis`)
- Modify: `en_us.json`, `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` (page line count), `gametest/LeftoversGameTests.java`

**Interfaces:**
- Produces:
  - `BrewQuality`:
    - grades `STANDARD = 0`, `FINE = 1`, `EXCEPTIONAL = 2`;
    - `STIR_MIN_GAP_TICKS = 10`, `STIR_MAX_GAP_TICKS = 30`, `FINE_SCORE = 0.5`, `EXCEPTIONAL_SCORE = 0.9`;
    - `static boolean gradable(@Nullable String item)`: true for `BrewRules.DITTANY, MANDRAKE_ROOT, ASPHODEL, WORMWOOD, VALERIAN_SPRIGS, ASHWINDER_EGG`;
    - `static boolean preciseStir(boolean previousWasStir, long gapTicks)`;
    - `static double score(int gradableAdds, int fineAdds, int stirs, int preciseStirs)`;
    - `static int grade(double score)`, `static int clampGrade(int)`, `static String langKey(int grade)` (`quality.hogwartsmagic.standard|fine|exceptional`).
  - `ExampleMod.BREW_QUALITY` (`DataComponentType<Integer>`: persistent `Codec.intRange(0, 2)`, network `VAR_INT` mapped through `clampGrade`). Follow `BROOM_QUALITY`.
  - `ExampleMod.FINE_INGREDIENT` (`DataComponentType<Boolean>`: persistent `Codec.BOOL`, network `BOOL`).
  - `BrewAdvancements.OUTSTANDING_BREW = "outstanding_brew"`.

**Scoring algorithm** (the tests pin it):
- `preciseStir` = `gap >= STIR_MIN_GAP_TICKS && (!previousWasStir || gap <= STIR_MAX_GAP_TICKS)`.
- `score`:
  - the ingredient part is `fine / gradable` when `gradableAdds > 0`;
  - the stir part is `precise / stirs` when `stirs > 0`;
  - the result is the mean of the parts present, or 0 when neither is present.
  - Inputs are clamped so a part never leaves 0..1.
- `grade`: `>= 0.9` → EXCEPTIONAL; `>= 0.5` → FINE; otherwise (NaN included) → STANDARD.

- [ ] **Step 1: Write the failing tests** (`BrewQualityTest`):
```java
@Test void stirRhythm() {
    assertTrue(BrewQuality.preciseStir(false, 10));
    assertFalse(BrewQuality.preciseStir(false, 9));
    assertTrue(BrewQuality.preciseStir(false, 500));
    assertTrue(BrewQuality.preciseStir(true, 30));
    assertFalse(BrewQuality.preciseStir(true, 31));
    assertFalse(BrewQuality.preciseStir(true, 9));
}
@Test void emptyCountersAreStandard() {
    assertEquals(0.0, BrewQuality.score(0, 0, 0, 0), 1e-9);
    assertEquals(BrewQuality.STANDARD, BrewQuality.grade(BrewQuality.score(0, 0, 0, 0)));
}
@Test void scoresAverageThePartsPresent() {
    assertEquals(1.0, BrewQuality.score(0, 0, 3, 3), 1e-9);   // no rare ingredient: stirring alone
    assertEquals(0.5, BrewQuality.score(3, 0, 5, 5), 1e-9);
    assertEquals(0.625, BrewQuality.score(2, 1, 4, 3), 1e-9);
    assertEquals(0.4, BrewQuality.score(5, 2, 0, 0), 1e-9);
    assertEquals(1.0, BrewQuality.score(2, 9, 1, 7), 1e-9);   // clamped
}
@Test void gradeBoundaries() {
    assertEquals(BrewQuality.EXCEPTIONAL, BrewQuality.grade(0.9));
    assertEquals(BrewQuality.FINE, BrewQuality.grade(0.8999));
    assertEquals(BrewQuality.FINE, BrewQuality.grade(0.5));
    assertEquals(BrewQuality.STANDARD, BrewQuality.grade(0.4999));
    assertEquals(BrewQuality.STANDARD, BrewQuality.grade(Double.NaN));
    assertEquals(2, BrewQuality.clampGrade(7));
    assertEquals(0, BrewQuality.clampGrade(-1));
}
@Test void gradableIngredients() {
    assertTrue(BrewQuality.gradable(BrewRules.DITTANY));
    assertFalse(BrewQuality.gradable(BrewRules.HONEY_BOTTLE));
    assertFalse(BrewQuality.gradable(BrewRules.POLYJUICE_SAMPLE));
    assertFalse(BrewQuality.gradable(null));
    assertEquals("quality.hogwartsmagic.fine", BrewQuality.langKey(BrewQuality.FINE));
}
```
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement `BrewQuality`** and the two data components.
- [ ] **Step 4: Track quality in the cauldron.**
  - **Fields:** `gradableAdds`, `fineAdds`, `stirs`, `preciseStirs` (int), and `lastActionTick` (long, -1 = unknown).
  - **Saving:** under `QGradable`, `QFine`, `QStirs`, `QPrecise` and `QLastAction`. Missing keys load as 0 / -1.
  - **Reset:** `reset()` and `ruin()` zero them.
  - **`addIngredient`:**
    - before `consumeOne`, read `stack.getOrDefault(FINE_INGREDIENT, false)`;
    - after `applyStep` returns true, when `gradable(id)`, increment `gradableAdds`, and `fineAdds` too when fine;
    - set `lastActionTick = level.getGameTime()`.
  - **`stir`:**
    - before `applyStep`, `previousWasStir` = the last history run's kind is `STIR_CLOCKWISE` or `STIR_ANTICLOCKWISE`;
    - `gap = lastActionTick < 0 ? Long.MAX_VALUE : now - lastActionTick`;
    - when `applyStep` returns true, `stirs++`, and `preciseStirs++` when `preciseStir(previousWasStir, gap)`;
    - a precise stir also plays `SoundEvents.NOTE_BLOCK_CHIME` (pitch 1.6, volume 0.5) and 4 `ParticleTypes.ENCHANT` at the surface;
    - set `lastActionTick`.
  - **`bottle`:** when the result is a `BrewedPotionItem` (not `FAILED`) or a `SplashBrewItem`, set `BREW_QUALITY` to `grade(score(...))` on the stack. Polyjuice is not graded. An `EXCEPTIONAL` result grants `OUTSTANDING_BREW`.
  - **`finish`:** the finish line becomes `message.hogwartsmagic.cauldron.finished_quality` ("%s is ready (%s)"), with the quality name.
- [ ] **Step 5: Tooltips.** In `BrewedPotionItem` and `SplashBrewItem`, `appendHoverText` adds `Component.translatable(BrewQuality.langKey(q))` when `q > 0`: FINE is AQUA, EXCEPTIONAL is GOLD. Lang:
  - `quality.hogwartsmagic.standard` "Standard";
  - `quality.hogwartsmagic.fine` "Fine quality";
  - `quality.hogwartsmagic.exceptional` "Exceptional quality";
  - the finish line;
  - the advancement title "Outstanding!" and description "Bottle a potion of Exceptional quality";
  - one guide line on the `signs_in_the_liquid` page: "Stir in a steady rhythm, about one turn a second; a chime means the turn was true. Fine ingredients and true stirring make a stronger potion." Raise its line count.
- [ ] **Step 6: Gametests.**
  - `steadyStirsBrewExceptionalWiggenweld` (`timeoutTicks = 1000`):
    1. Put a lit campfire under a `WIZARD_CAULDRON` (`helper.setBlock`).
    2. Use a mock player (survival) with `useItem`: a water bucket, then a honey bottle (tick 5), then glow berries twice (ticks 25 and 45).
    3. Stir three times at ticks 65, 85 and 105.
    4. Wait for the simmer (20 s), then bottle.
    5. The result has `BREW_QUALITY == 2`.
  - `spammedStirsBrewStandardWiggenweld`: the same, with the three stirs at ticks 65, 66 and 67. The first stir is precise and the other two are not, so the score is 1/3: `BREW_QUALITY` is absent or 0.
  - `oldCauldronSaveLoadsAsStandard`: load a cauldron block entity from a tag with a finished Wiggenweld history and no `Q*` keys (`loadWithComponents`/`loadCustomOnly` as the class allows), then bottle. The result is graded 0.

  Use `helper.runAtTickTime` for each action.
- [ ] **Step 7: Verify.** Run `./gradlew test`, `runGameTestServer` and the `runServer` boot (the advancement must parse). Run the JSON checks.
- [ ] **Step 8: Commit**
```bash
git add src/main/java/com/hogwartsmagic/brewing/BrewQuality.java src/test/java/com/hogwartsmagic/brewing/BrewQualityTest.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/item/SplashBrewItem.java src/main/java/com/hogwartsmagic/brewing/BrewAdvancements.java src/main/resources/data/hogwartsmagic/advancement/hp/outstanding_brew.json src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Grade cauldron brews by their ingredients and the rhythm of the stirring" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Better brews are stronger

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/brewing/PotionRules.java`, `src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java`
- Modify: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java` (`applyDrink(ItemStack, Level, LivingEntity)`), `src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/petrification/Petrification.java` (overload `wardGaze(LivingEntity, int ticks)` if it has none)
- Modify: `en_us.json` (each brewed tooltip says "more when of better quality" where a number is quoted)

**Interfaces:**
- Consumes: `ExampleMod.BREW_QUALITY`, `BrewQuality.clampGrade` (Task 11); `DarkDeeds.onRemorse` (Task 9).
- Produces: `PotionRules.qualityTicks(int baseTicks, int grade)` → `int` (×1.0/1.5/2.0, rounded) and `PotionRules.qualityAmount(float base, int grade)` → `float` (×1.0/1.25/1.5). Out-of-range grades clamp.

**What scales** (grade = `stack.getOrDefault(BREW_QUALITY, 0)`, read before the stack shrinks):

| Potion | Scaled value |
|---|---|
| Wiggenweld | Regen ticks = `round(48 × amount multiplier)`: 8/10/12 HP |
| Pepperup | Mana `qualityAmount(40)`; fire resistance `qualityTicks(600)` |
| Dittany | Heal `qualityAmount(4)` |
| Felix | Luck `qualityTicks(FELIX_TICKS)` (amplifier unchanged) |
| Mandrake (drunk) | Gaze ward `qualityTicks(MANDRAKE_GAZE_WARD_TICKS)` |
| Mandrake (splash) | Same ward; the cure itself is unchanged |
| Living Death (splash) | Base stun `round(LIVING_DEATH_STUN_TICKS × amount multiplier)` before `CrowdControl` |
| Remorse | `onRemorse(p, qualityAmount(REMORSE_DARKNESS))`; the pain is unchanged |
| Failed | No change |

- [ ] **Step 1: Write the failing tests:**
  - `qualityTicks(48,0)==48`, `(48,1)==72`, `(48,2)==96`, `(48,9)==96`, `(48,-3)==48`;
  - `qualityAmount(40f,1)==50f`, `qualityAmount(4f,2)==6f`.
- [ ] **Step 2: Implement** per the table.
- [ ] **Step 3: Verify.** Run `./gradlew test` and `runGameTestServer` (Task 9's Remorse tests still pass with Standard bottles). Run the `en_us.json` check.
- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/brewing/PotionRules.java src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java src/main/java/com/hogwartsmagic/petrification/Petrification.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Fine and Exceptional brews last longer and work harder" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Where fine ingredients come from

**Files:**
- Modify: `src/main/resources/data/hogwartsmagic/loot_table/blocks/dittany_plant.json` (wild drops are fine)
- Modify: `src/main/java/com/hogwartsmagic/event/MandrakePullEvents.java` (a successful pull gives a fine root)
- Modify: the Blaze Ashwinder egg drop (in `brewing/IngredientDrops.java` or its event handler; find it by grepping `ASHWINDER_BLAZE_CHANCE`): Blaze eggs are fine
- Modify: `src/main/java/com/hogwartsmagic/entity/ModVillagers.java` (Apothecary level 4: three fine trades)
- Create: `src/main/java/com/hogwartsmagic/event/FineIngredientTooltip.java` (common-side `ItemTooltipEvent` listener: adds a gold "Fine" line when `FINE_INGREDIENT` is true)
- Modify: `en_us.json`, `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` (page line count), `gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: `ExampleMod.FINE_INGREDIENT` (Task 11).
- Produces: `FineIngredientTooltip` (Forge bus, `@Mod.EventBusSubscriber`). `ItemTooltipEvent` is a common Forge class that fires only on the client, so it is safe on a dedicated server. The listener must not reference any client class.

Details:
- **Dittany:** in the loot table, the pool or entry that only drops when `wild=true` (and, if the base drop is in the same conditioned entry, that one too) gets `{"function": "minecraft:set_components", "components": {"hogwartsmagic:fine_ingredient": true}}`. A planted (`wild=false`) dittany gives a standard leaf.
- **Mandrake:** the `new ItemStack(ExampleMod.MANDRAKE_ROOT.get())` in the success branch gets `set(FINE_INGREDIENT, true)`.
- **Apothecary level 4** (after the Polyjuice trade):
  - fine Asphodel ×1 for 14 emeralds (spread 4, 6 uses, 15 xp);
  - fine Wormwood ×2 for 12 (spread 3, 6 uses, 15 xp);
  - fine Valerian Sprigs ×2 for 10 (spread 3, 6 uses, 15 xp).

  Add a local helper `fine(ItemStack)` that sets the component and returns the stack.
- **Lang:** `tooltip.hogwartsmagic.fine_ingredient` "Fine ingredient". Add one guide line on `where_ingredients_grow`: "Wild dittany, pulled mandrakes, Ashwinder eggs from Blazes and the apothecary's best stock are fine ingredients." Raise the page count.

- [ ] **Step 1: Write the failing gametest** `wildDittanyDropsFineLeaves`:
  1. Place `DITTANY_PLANT` with `wild=true`.
  2. `Block.getDrops(state, level, pos, null)` contains a dittany stack with `FINE_INGREDIENT == true`.
  3. The same check with `wild=false` finds no fine stack.

  Run it and see it fail.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: Verify.** Run `./gradlew test`, `runGameTestServer`, and the `runServer` boot (the loot table must parse; grep the log for `dittany`). Run the JSON checks.
- [ ] **Step 4: Commit**
```bash
git add src/main/resources/data/hogwartsmagic/loot_table/blocks/dittany_plant.json src/main/java/com/hogwartsmagic/event/MandrakePullEvents.java src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java src/main/java/com/hogwartsmagic/entity/ModVillagers.java src/main/java/com/hogwartsmagic/event/FineIngredientTooltip.java src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Add fine ingredients: wild dittany, pulled mandrakes, Blaze eggs and the apothecary's best stock" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```
(Stage the file that actually holds the Blaze drop if it is not `IngredientDrops.java`.)

---

### Task 14: The Advanced Potion-Making book

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/brewing/BrewRules.java` (`ADVANCED_RECIPE_IDS`, `isAdvanced`, `needsBook`), `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java`
- Create: `src/main/java/com/hogwartsmagic/brewing/AdvancedPotions.java` (server: read flag), `src/main/java/com/hogwartsmagic/item/AdvancedPotionMakingItem.java`
- Modify: `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java` (gate in `addIngredient` and `stir`)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (new `OpenAdvancedPotionsScreenPacket()`, registered next to `OpenHogwartsGuideScreenPacket`; protocol +1)
- Modify: `src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java` (op `"openAdvancedPotionsScreen"`), `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` (book mode, `ADVANCED_POTIONS` section)
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (`ADVANCED_POTION_MAKING` item, creative tab), `src/main/java/com/hogwartsmagic/entity/ModVillagers.java` (Bookseller level 3)
- Modify: `src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java` + `src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java` + its caller `src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsManager.java`
- Create: `src/main/resources/assets/hogwartsmagic/models/item/advanced_potion_making.json`, `src/main/resources/assets/hogwartsmagic/textures/item/advanced_potion_making.png`
- Modify: `en_us.json`, `gametest/LeftoversGameTests.java`

**Interfaces:**
- Produces:
  - `BrewRules.ADVANCED_RECIPE_IDS = Set.of("living_death", "felix_felicis")` (Task 15 adds `"veritaserum"`);
  - `static boolean isAdvanced(Recipe)`;
  - `static boolean needsBook(List<Step> history)`: true when the history is non-empty, no non-advanced recipe fits it, and some advanced recipe does. Reuse the private `fit`;
  - `AdvancedPotions.KEY = "AdvancedPotionsRead"`, `AdvancedPotions.hasRead(Player)`, `AdvancedPotions.markRead(ServerPlayer)` → `boolean` (true the first time);
  - `ExampleMod.ADVANCED_POTION_MAKING`;
  - `ClassroomSupplies.ADVANCED_BOOK = "hogwartsmagic:advanced_potion_making"`, `ADVANCED_BOOK_CHANCE = 0.25`, `roll(IntBinaryOperator between, double eggRoll, double bookRoll)`. The book is appended after the egg when `bookRoll < ADVANCED_BOOK_CHANCE`. The old two-argument `roll` is removed and its caller updated.

**Behaviour:**
- **The gate.** In `addIngredient` (after the empty-sample check, **before** `consumeOne`) and in `stir` (before the splash sound and `applyStep`):
  - if `BrewRules.needsBook(BrewRules.append(history, action)) && !AdvancedPotions.hasRead(player)`, tell the player `needs_book` ("This brew is in Advanced Potion-Making, and you have not read it") and return;
  - nothing is consumed and nothing is ruined;
  - the simmer ticker is never gated.
- **The item.** `use` on the server:
  - `markRead` (the first time, a chat line `message.hogwartsmagic.advanced_potions.learned`, "You study Advanced Potion-Making. Its recipes are yours now.");
  - send `OpenAdvancedPotionsScreenPacket` to the player;
  - put a 10-tick cooldown on the item.

  A stack-size-1 book, rarity UNCOMMON.
- **The screen.**
  - Add a `HogwartsGuideScreen(boolean advancedBook)` constructor; the no-argument one means `false`.
  - The new section `ADVANCED_POTIONS` holds `page("potions", "advanced_intro", 3)` and the four moved pages `draught_of_living_death`, `living_death_the_finish`, `felix_felicis` and `felix_felicis_patience`, keeping their lang keys.
  - Guide mode lists every section except `ADVANCED_POTIONS`. Book mode shows only `ADVANCED_POTIONS`, no tab buttons, and the title `gui.hogwartsmagic.advanced_potion_making`.
  - The POTIONS section loses the four moved pages and gains `page("potions", "advanced_book", 3)`, which says where to find the book.
  - Use if-checks only.
- **Sources:**
  - Bookseller level 3: `sells(event, 3, 22, 6, one(ExampleMod.ADVANCED_POTION_MAKING), 4, 10)`;
  - the Mini Hogwarts Potions classroom chests (25% each);
  - creative tab.

  No crafting recipe.
- **Texture:** a 16×16 item sprite of a worn green potions textbook with a small flask on the cover, drawn with the `texturing-minecraft-items` skill. Not a spellbook; do not use the spellbook set's base.

- [ ] **Step 1: Write the failing tests.**
  - BrewRules: `needsBook(List.of())` false; `needsBook([add ASPHODEL 1])` true (only Living Death starts with asphodel); `needsBook([add ASHWINDER_EGG 1])` true; `needsBook([add DITTANY 1])` false; `needsBook([add HONEY_BOTTLE 1])` false; `needsBook([add GOLD_NUGGET 1])` false (it fits nothing, which ruins rather than gates); `isAdvanced(FELIX_FELICIS)` true; `isAdvanced(POLYJUICE)` false.
  - ClassroomSupplies: `roll(min, 0.99, 0.24)` ends with the book; `roll(min, 0.99, 0.25)` has no book; `roll(min, 0.05, 0.1)` ends with the egg, then the book.
  - Gametest `unreadPlayerIsRefusedWithoutLoss`:
    1. Fill a cauldron with water (mock player).
    2. The player holds 3 Asphodel; `useItem` leaves the count at 3.
    3. The cauldron's history stays empty and it is not ruined. Read it through a status call, or add a package-visible `historySize()`/`isRuined()` test accessor on the block entity.
    4. Call `AdvancedPotions.markRead`, use again: the count is 2.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.** Bump `networkProtocolVersion` by one from the value on disk. Lang:
  - the item name "Advanced Potion-Making" and tooltip "By Libatius Borage. Read it to brew the advanced recipes.";
  - `gui.hogwartsmagic.advanced_potion_making`;
  - the two messages;
  - the `advanced_intro` and `advanced_book` pages.
- [ ] **Step 4: Verify.**
  - Run `./gradlew test`, `runGameTestServer` and the `runServer` boot.
  - `grep -n "ADVANCED_POTIONS" src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` shows it excluded from the guide's tab loop.
  - Run the JSON checks.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/brewing/BrewRules.java src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java src/main/java/com/hogwartsmagic/brewing/AdvancedPotions.java src/main/java/com/hogwartsmagic/item/AdvancedPotionMakingItem.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/entity/ModVillagers.java src/main/java/com/hogwartsmagic/brewing/ClassroomSupplies.java src/test/java/com/hogwartsmagic/brewing/ClassroomSuppliesTest.java src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsManager.java src/main/resources/assets/hogwartsmagic/models/item/advanced_potion_making.json src/main/resources/assets/hogwartsmagic/textures/item/advanced_potion_making.png src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Gate Living Death and Felix Felicis behind the Advanced Potion-Making book" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 15: Veritaserum

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/brewing/BrewRules.java` (`FEATHER`, `AMETHYST_SHARD`, `VERITASERUM`, `RECIPES`, `ADVANCED_RECIPE_IDS`), `src/main/java/com/hogwartsmagic/brewing/PotionRules.java`, and the tests in `src/test/java/com/hogwartsmagic/brewing/`
- Modify: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java` (`Kind.VERITASERUM`, drink, `interactLivingEntity`), `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java` (`bottledPotion`)
- Modify: `ExampleMod.java` (`VERITASERUM`, creative tab), `ClientModEvents.java` (colour list), `client/HogwartsGuideScreen.java` (`ADVANCED_POTIONS` gains `page("potions", "veritaserum", 7)` and `page("potions", "veritaserum_use", 4)`)
- Create: `src/main/resources/assets/hogwartsmagic/models/item/veritaserum.json` (copy `wiggenweld_potion.json`)
- Modify: `en_us.json`, `gametest/LeftoversGameTests.java`

**Interfaces:**
- Consumes: `PotionRules.qualityTicks/qualityAmount` (Task 12); `ModEvents.breakPolyjuiceDisguise(ServerPlayer)`, `ModEvents.revealInvisibilityCloak(ServerPlayer, int)`, `CombatTags.isBoss`, `BrewedPotionItem.useUpDose` (existing).
- Produces:
  - `BrewRules.VERITASERUM` = `Recipe("veritaserum", [add "minecraft:feather" 1, add "minecraft:amethyst_shard" 1, add VALERIAN_SPRIGS 2, stirClockwise 7, simmer 120, add ARCANE_DUST 1], colors [0xDCE6EA, 0xC9B8E8, 0xD6CCE8, 0xE4ECF0, 0xEEF4F6, 0xF4FAFC], no tail, MAX_DOSES)`, with `"veritaserum"` added to `ADVANCED_RECIPE_IDS`.
  - `PotionRules.VERITASERUM_GOSSIP = 25`, `VERITASERUM_COOLDOWN_TICKS = 24_000`, `VERITASERUM_GLOW_TICKS = 600`, `VERITASERUM_SELF_GLOW_TICKS = 200`, `VERITASERUM_ACCOMPLICE_RADIUS = 48.0`; `static boolean veritaserumReady(boolean hasRecord, long lastAt, long now)`.
  - `BrewedPotionItem.Kind.VERITASERUM("veritaserum", 0xEEF4F6)`.

**Effects** (grade from the stack; a refusal never uses a dose):

| Used on | Result |
|---|---|
| An adult `Villager` | Refused (`veritaserum.already`) unless `veritaserumReady` against the villager's persistent `hogwartsmagic:veritaserum_at`. Otherwise `villager.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, round(VERITASERUM_GOSSIP × amount multiplier))`, record the time, 8 `HAPPY_VILLAGER` particles, line `veritaserum.villager` ("The villager admits its prices were padded"), one dose used. |
| A `WizardEntity` (Death Eater) | A boss (`CombatTags.isBoss`): `veritaserum.resists`, nothing used. Otherwise every other `WizardEntity` within 48 blocks gets Glowing for `qualityTicks(VERITASERUM_GLOW_TICKS)` and the line `veritaserum.accomplices` ("%s gives up %s accomplices") is shown. One dose used, even for 0. |
| Another `ServerPlayer` | Refused (`veritaserum.refuses`) unless `server.isPvpAllowed() && player.canHarmPlayer(target)`. Otherwise `breakPolyjuiceDisguise(target)`, `revealInvisibilityCloak(target, qualityTicks(200))`, Glowing for `qualityTicks(VERITASERUM_GLOW_TICKS)`, and a line to each player. One dose used. |
| Anything else | `PASS` |
| Drunk by yourself | Breaks your own Polyjuice disguise; Glowing for `qualityTicks(VERITASERUM_SELF_GLOW_TICKS)`; line `veritaserum.self` ("You could not lie now if you tried"). |

Client side: return `SUCCESS` for the three handled target classes, so the arm swings, and let the server decide.

- [ ] **Step 1: Write the failing tests.**
  - BrewRules: a full Veritaserum history finishes as `VERITASERUM`; `needsBook([add FEATHER 1])` is true; first steps stay unique (nine recipes).
  - PotionRules: `veritaserumReady(false, 0, 5)` true; `(true, 100, 24_099)` false; `(true, 100, 24_100)` true.
  - Gametest `veritaserumMakesAVillagerConfessOnce`:
    1. A mock server player uses a Veritaserum on a no-AI adult villager.
    2. The villager's `getGossips().getReputation(uuid, t -> true)` rose by 25, and the player's stack is now a glass bottle.
    3. A second use is refused: the count is unchanged and the reputation is unchanged.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
  - Lang:
    - the item "Veritaserum" and tooltip "Give it to someone: villagers confess, Death Eaters betray their friends, a disguise falls away.";
    - `brew.hogwartsmagic.veritaserum`;
    - the six `message.hogwartsmagic.veritaserum.*` lines;
    - the two guide pages: what it does, then the six steps, then "Feather, Amethyst Shard, Valerian Sprigs, Arcane Dust".
  - Add `"minecraft:feather"` and `"minecraft:amethyst_shard"` constants to BrewRules.
- [ ] **Step 4: Verify.** Run `./gradlew test`, `runGameTestServer`, the `runServer` boot and the JSON checks.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/brewing/BrewRules.java src/main/java/com/hogwartsmagic/brewing/PotionRules.java src/test/java/com/hogwartsmagic/brewing/ src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java src/main/resources/assets/hogwartsmagic/models/item/veritaserum.json src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Brew Veritaserum: villagers confess, Death Eaters betray their friends, disguises fall" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 16: Tome of Mastery fixes (creative, scroll cue, trial vaults)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/item/TomeOfMasteryItem.java` (`tryRaise`)
- Modify: `src/main/java/com/hogwartsmagic/client/TomePickerScreen.java`; create `src/main/java/com/hogwartsmagic/client/ScrollCue.java` (pure, no Minecraft imports) and `src/test/java/com/hogwartsmagic/client/ScrollCueTest.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/TomeRules.java`, `src/test/java/com/hogwartsmagic/spell/mastery/TomeRulesTest.java`, `src/main/java/com/hogwartsmagic/loot/TomeOfMasteryLootModifier.java`
- Modify: `gametest/LeftoversGameTests.java`

**Interfaces:**
- Produces:
  - `TomeRules.DUNGEON_TIER_TABLES` and `TomeRules.ENDGAME_TABLES` (`Set<String>`; today's two `ResourceLocation` sets in the modifier, moved here as strings);
  - `TomeRules.isDungeonTierTable(String id)`, `TomeRules.isEndgameTable(String id)`;
  - `ScrollCue.thumb(float scroll, float maxScroll, int trackHeight, int minThumb)` → `int[] {offsetY, height}`;
  - `ScrollCue.moreAbove(float scroll)`, `ScrollCue.moreBelow(float scroll, float maxScroll)`.

Changes:
1. **Creative keeps the tome:** `stack.shrink(1)` becomes `if (!player.hasInfiniteMaterials()) stack.shrink(1);`.
2. **Scroll cue:** when `maxScroll() > 0`, `TomePickerScreen.render` draws:
   - a 3-px track at the list's right edge (`0x40FFFFFF`) with a thumb in `COLOR_NAME`, sized and placed by `ScrollCue.thumb(scroll, maxScroll(), listHeight, 8)`;
   - a small `▲` above the list when `moreAbove` and `▼` below it when `moreBelow`, in `COLOR_RANK`.
3. **Trial vaults:**
   - Add to `DUNGEON_TIER_TABLES`: `minecraft:chests/trial_chambers/reward`, `reward_common`, `reward_rare` and `reward_unique`.
   - Add to `ENDGAME_TABLES`: `minecraft:chests/trial_chambers/reward_ominous`, `reward_ominous_common`, `reward_ominous_rare` and `reward_ominous_unique`.
   - The modifier reads `TomeRules` with `tableId.toString()`.
   - A vault ejects its loot through `LootTable.getRandomItems`, which Forge runs through global loot modifiers with the vault's table as the queried id, and the existing `chests/` gate admits it. No new gate is needed.

- [ ] **Step 1: Write the failing tests.**
  - `TomeRulesTest`: `isDungeonTierTable("minecraft:chests/trial_chambers/reward")` and `isEndgameTable("minecraft:chests/trial_chambers/reward_ominous")` are true; `isEndgameTable("minecraft:chests/trial_chambers/reward")` is false; `isDungeonTierTable("minecraft:chests/simple_dungeon")` is still true; `isEndgameTable("hogwartsmagic:chests/gringotts_vault")` is still true.
  - `ScrollCueTest`:
    - `thumb(0, 100, 100, 8)` → `{0, 50}`;
    - `thumb(100, 100, 100, 8)` → `{50, 50}`;
    - `thumb(0, 10000, 100, 8)[1] == 8` (minimum);
    - `moreAbove(0)` false;
    - `moreBelow(99.5f, 100)` true;
    - `moreBelow(100, 100)` false.

    The thumb height is `max(minThumb, round(track × track / (track + maxScroll)))`, and its offset is `round((track − height) × scroll / maxScroll)`.
  - Gametest `creativeKeepsItsTome`:
    1. A mock server player in creative (`setGameMode(GameType.CREATIVE)`) learns Lumos.
    2. They hold a Common Tome (`TomeOfMasteryItem.stackFor(SpellLootRarity.COMMON)`).
    3. `tryRaise(player, true, "lumos")` returns true, and the main-hand count is still 1.
- [ ] **Step 2: Run them and confirm they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Verify.** Run `./gradlew test`, `runGameTestServer` and the `runServer` boot.
- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/item/TomeOfMasteryItem.java src/main/java/com/hogwartsmagic/client/TomePickerScreen.java src/main/java/com/hogwartsmagic/client/ScrollCue.java src/test/java/com/hogwartsmagic/client/ScrollCueTest.java src/main/java/com/hogwartsmagic/spell/mastery/TomeRules.java src/test/java/com/hogwartsmagic/spell/mastery/TomeRulesTest.java src/main/java/com/hogwartsmagic/loot/TomeOfMasteryLootModifier.java src/main/java/com/hogwartsmagic/gametest/LeftoversGameTests.java
git commit -m "Keep tomes in creative, show a scroll cue in the tome picker, and put tomes in trial vaults" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 17: Docs, the whole-branch check, and the in-game checklist

**Files:**
- Modify: `MOD_FEATURES_WIKI.md`, `wiki/data.js`

- [ ] **Step 1: Docs.**
  - **Spell entries:** update the Fiendfyre, Serpensortia and Expecto Patronum entries (rank tables, Parseltongue, the messenger and `/patronus`).
  - **Potions:**
    - add Remorse and Veritaserum;
    - add a "Quality" paragraph: the rhythm of 10–30 ticks between turns, fine ingredients and where they come from, and the ×1.5/×2 durations and ×1.25/×1.5 amounts;
    - add the Advanced Potion-Making book (what it gates and where to find it);
    - remove Veritaserum from the "not in this mod" lines.
  - **Dark Magic:** add the grey spells and the house reactions.
  - **Tomes:** creative keeps them; trial vaults.

  Check `wiki/data.js` with `node -e "require('./wiki/data.js')"` if it is CommonJS. Otherwise run `node --check wiki/data.js`.
- [ ] **Step 2: Whole-branch check,** under the lock:
  - `./gradlew test`, `./gradlew runGameTestServer` (every `LEFTOVERS-GAMETEST PASS` line present) and the `runServer` boot.
  - `grep -rn "import net.minecraft.client\|import com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell src/main/java/com/hogwartsmagic/entity src/main/java/com/hogwartsmagic/item src/main/java/com/hogwartsmagic/brewing src/main/java/com/hogwartsmagic/block src/main/java/com/hogwartsmagic/loot` prints nothing new.
  - `grep -n "switch" src/main/java/com/hogwartsmagic/client/model/SnakeModel.java src/main/java/com/hogwartsmagic/client/renderer/SnakeRenderer.java src/main/java/com/hogwartsmagic/client/FiendfyreSerpentRenderer.java src/main/java/com/hogwartsmagic/client/TomePickerScreen.java src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` prints no enum switch.
  - `grep -rn "broadcastEntityEvent" src/main/java/com/hogwartsmagic/entity/SnakeEntity.java src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java` shows none, or only 61/62/64/66/67.
- [ ] **Step 3: Write the in-game checklist** (below) into the ledger for the user. Fix nothing that is not listed.
- [ ] **Step 4: Commit**
```bash
git add MOD_FEATURES_WIKI.md wiki/data.js
git commit -m "Document Fiendfyre and Serpensortia ranks, the Master Patronus, brewing quality, new potions and grey spells" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

**In-game checklist** (the user's pass; commands: `/spellmastery setrank @s <spell> <1-4>`, `/darkmagic set @s <n>`, `/patronus form <1-8>`):

1. **Fiendfyre I** ×10 casts: about 1 in 7 has a serpent glow red with "writhes" a second before it turns and chases you. Creative players are never chased.
2. **Fiendfyre III:** two serpents. **IV:** three, never rogue. Left-click while holding sends one at the zombie you aim at. Aimed at a friend with PvP off, nothing happens.
3. **Fiendfyre IV** next to an active Horcrux: "devours a Horcrux", and the Horcrux breaks with its usual effects.
4. **Fire and the land:** with `mobGriefing false` an NPC Fiendfyre places no fire. In spawn protection or a ward, player Fiendfyre places none.
5. **Serpensortia I/II/III:** 1/2/3 snakes that bite with poison (Poison II at III) and never bite you. Recast, and the old ones vanish. Log out with snakes out and log back in: they are gone.
6. **Serpensortia IV:** left-click a zombie and the snakes attack it. Left-click the floor and they guard the spot; again, and they follow. Spawn-egg snakes ignore you at IV and attack you at III.
7. **Patronus IV:** the corporeal Patronus follows you, you get Night Vision near it, and zombies and skeletons within 6 blocks are shoved back and drop their target. At III none of this happens.
8. **`/patronus <friend> hello`** at IV (friend within 500 blocks, same dimension): a Patronus of your form appears beside them with the message. A second message within 30 s is refused. At Tainted Darkness it is refused.
9. **Remorse:** brew it (Ghast Tear, Dittany ×2, anticlockwise 3, simmer 90). At Darkness 50, drinking hurts, gives nausea and weakness, and Darkness drops to 45. A second drink hurts but changes nothing ("spent").
10. **Grey spells:** Obliviate ×5 raises Darkness by 1.0. A Slytherin's Avada adds 4.5, not 6. A Hufflepuff's Heal redemption removes 1.5.
11. **Quality, Wiggenweld:** stir at about one turn a second and you hear chimes, and the potion says "Exceptional quality" (12 HP). Spam-clicked stirs give no quality line.
12. **Quality, Dittany Essence:** wild dittany plus steady stirs gives Exceptional; bought dittany plus steady stirs gives Fine.
13. **The book gate:** without reading the book, Asphodel into water is refused and nothing is lost. Read the book from a Bookseller and the brew works. The Guide no longer shows Living Death or Felix; the book does.
14. **Veritaserum** on a Diagon Alley villager: prices drop, and a second dose that day is refused. On a Death Eater in a camp: the others glow. On Voldemort: "resists". On a disguised friend (PvP on): the disguise breaks.
15. **Tomes:** a creative player keeps the tome. A picker with 12+ spells shows the scrollbar and arrows. Trial vault keys sometimes give tomes; ominous vaults can give Rare and Epic.
16. **Dedicated server:** every item above works on the Desktop Forge server with a fresh jar (`build/libs`).

---

## Self-review notes

- **Coverage of the request:**
  - Items 1–3 (Fiendfyre, Serpensortia, Patronus Master) are Tasks 1–8.
  - Item 4 (Remorse) is Task 9.
  - Item 5 (grey spells and houses) is Task 10.
  - Item 6 (quality) is Tasks 11–13.
  - Item 7 (the book) is Task 14.
  - Item 8 (Veritaserum) is Task 15.
  - Item 9 (the clearly specified deferred fixes) is Task 16.
  - Accio's invulnerability-window skip is already fixed in `AccioSpell` (its javadoc describes the claim-veto-safe check), so it is not planned.
  - The stun knock-ons are not specified enough to plan (see below).
- **Not planned (as instructed):** Revelio showing others' tier, Dementors ignoring Lost players, Wolfsbane, datapack recipes and JEI, the open sign-offs.

## Calls made by the planner

1. **The 7a plan's code is the reference, not a transcript.** It was written before Dark Magic, possession and the Patronus forms landed, so each 7a port lists deltas, and today's code wins wherever an anchor differs.
2. **The Master Patronus, adapted to the forms design.** Every corporeal Patronus already lights its block and pulses light rings, so "lights the dark" becomes: the Master's Patronus follows you (its light goes with you), plus Night Vision for you and players within 8 blocks. "Repels undead" covers `#minecraft:undead` plus Inferi (matched by class; the tag is not touched), spares bosses, pushes them out of 6 blocks and clears their player target. Lifespans and the corporeal chances are unchanged.
3. **The Patronus messenger needs a Patronus that can take shape.** It is refused from Tainted Darkness (no corporeal Patronus). It costs the spell's mana and no combat cooldown or XP, and works only in the same dimension.
4. **Rogue Fiendfyre and dispatched serpents respect PvP-off and allies.** The rogue may still hunt its own caster (the spec says so), never creative or spectator players.
5. **Conjured snakes vanish 2 s after their owner can't be found.** The 7a plan left this open.
6. **The snake model is the hand-written Java model from 7a.** A Blockbench rebuild is optional later work, since the MCP is offline.
7. **Remorse:**
   - the recipe is Ghast Tear, Dittany ×2, anticlockwise 3, simmer 90 s;
   - it lightens 5 Darkness (6.25 Fine, 7.5 Exceptional), once per 2 hours of play (config `remorseCooldownTicks`);
   - it always hurts (3, never lethal), with Nausea 8 s and Weakness 30 s;
   - it never goes below the Horcrux floor, and a useless drink does not spend the window.

   With decay and Heal redemption, a determined player goes from 100 to 0 in about 15 hours of play instead of 25–50.
8. **Grey spells:** Obliviate 0.2 and Confundo 0.1 Darkness per paid cast. They are not "dark" for the Corrupted discount or anything else.
9. **House reactions:** Slytherins gain Darkness from casts ×0.75. Hufflepuffs redeem ×1.5 (Heal and Remorse). Gryffindor and Ravenclaw are unchanged. Deeds (the Avada innocent bonus, the Horcrux) are not scaled.
10. **The quality model:**
    - The score is the mean of the ingredient part (fine gradable adds / gradable adds) and the stir part (precise stirs / stirs), counting only the parts a recipe has.
    - A stir is precise 10–30 ticks after the previous stir, or at least 10 ticks after a non-stir step.
    - Fine is a score of 0.5 or more; Exceptional is 0.9 or more.
    - Durations scale ×1/1.5/2, amounts ×1/1.25/1.5.
    - Polyjuice and the Failed Potion are not graded.
    - Recipes without rare ingredients (Wiggenweld, Pepperup's vanilla half) can reach Exceptional on stirring alone.
11. **Fine ingredient sources:** wild dittany, mandrakes pulled by hand, Ashwinder eggs from Blazes, and three Apothecary level-4 trades. Chest and classroom ingredients are standard.
12. **The Advanced Potion-Making book gates Draught of Living Death, Felix Felicis and Veritaserum.** Polyjuice stays in the Guide: it already shipped, and gating it now would take it from players.
    - The gate refuses the step (nothing lost) from a player who has not read the book. It is a per-player flag that survives death.
    - The book comes from Bookseller level 3 (22–27 emeralds), 25% of Mini Hogwarts classroom chests, and creative. There is no crafting recipe.
    - Players who already brewed Felix must read the book once.
13. **Veritaserum** is given, not thrown:
    - villagers confess (+25 minor-positive gossip, once per villager per day);
    - Death Eaters give up accomplices within 48 blocks (Glowing 30 s);
    - players lose their Polyjuice disguise and cloak and glow (PvP rules apply);
    - drinking it yourself breaks your own disguise;
    - bosses resist.

    The recipe is Feather, Amethyst Shard, Valerian ×2, clockwise 7, simmer 120 s, Arcane Dust (advanced).
14. **Trial vaults:** a normal vault counts as dungeon tier (up to Rare tomes), an ominous vault as endgame (up to Epic). The chances are the existing config values.
15. **One protocol bump (Task 14),** from whatever the value on disk is then.

## Needs the user

- **The stun knock-ons from the Phase 2 final review** (M1: free horses and tick-driven bosses ignore stun; M3: a silenced NPC's non-beam casts) were recorded only as "deferred, documented known limits", with no fix design. They are not planned. Say whether you want them, and for which bosses.
- **Two balance calls that change the feel of existing systems:**
  - Remorse makes the way back from Lost roughly 15 hours instead of 25–50;
  - the book gate takes Living Death and Felix away from players until they read it.

  Both are in "Calls made by the planner" and are one-line changes if you disagree.
