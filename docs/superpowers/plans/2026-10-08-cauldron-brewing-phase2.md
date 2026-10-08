# Cauldron Brewing Phase 2 (Ingredients and the other four potions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** Five new ingredients (a `dittany` plant that grows in the Forbidden Forest and the Horntail Peaks, `asphodel`, `wormwood`, `valerian_sprigs`, `ashwinder_egg`) with apothecary trades, chest loot and a Blaze drop; four new cauldron potions (Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis) with splash variants made by a gunpowder step; and canon petrification: the Basilisk's full gaze turns a player to stone instead of killing them. A stone player chooses on a small screen to wait for rescue or give in; a Mandrake Draught (right-click, drink or splash) brings them back. A petrified mob's statue now keeps the whole mob, drops no loot or XP, and comes back with its name, owner and inventory when splashed. Bosses keep the lethal gaze.

**Architecture:**
- **Pure rules (test-first):** `brewing/BrewRules` gains four recipes; new `brewing/PotionRules` holds every potion number and drop chance; new `petrification/PetrificationRules` holds the stone state rules (gaze outcome, which damage passes, the time limit, the ward, revive rules, the NBT keys a statue drops).
- **Ingredients:** a `DittanyPlantBlock` (bush) placed by a `DittanyPatchFeature` through a Forge biome modifier; the `dittany` item plants it. Textures from a new generator. Trades in `ModVillagers`, chest loot through one global loot modifier that checks the structure at the chest, the Blaze drop in a small `brewing/IngredientDrops` handler.
- **Potions:** `BrewedPotionItem.Kind` gains `DITTANY`, `FELIX`, `MANDRAKE`. Splash potions are a new `SplashBrewItem` thrown as a new `ThrownBrewEntity` (custom impact: the statue revive and the crowd control). Felix's +25% mastery XP is one multiplier in `MasteryAwards.grant`.
- **Petrification:** `petrification/Petrification` (server and common logic: petrify, tick, cure, give in, ward, capture and revive statues) and `petrification/PetrificationEvents` (its own Forge subscriber: player tick, damage filter, target filter, death cleanup, no drops or XP). The stone state is an infinite `basilisk_petrification` effect plus `PlayerPersisted/hogwartsmagic/Petrified` and `PetrifiedSince`. `CombatEffects.isStunned` counts the stone state, which reuses the whole stun lock (cast, use, attack, interact refusal on the server; movement and click lock on the client). Client: `client/PetrificationClient` (look lock, key drain, screen gate, HUD line, the key) and `client/PetrifiedChoiceScreen`. One new C2S packet, `GiveInPacket`.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5, Python 3 with Pillow.

**Spec:** `docs/superpowers/specs/2026-10-07-cauldron-brewing-roadmap.md`: "Phase 2", decisions 1-15 as they apply, "The potions" (Dittany, Mandrake Draught, Living Death, Felix), "Draft recipes" (four rows), "New ingredients (Phase 2)", the whole "Petrification (user ruling, 2026-10-07)", "Global constraints". Phase 3 (advancements, Mini Hogwarts classroom, wiki pages) is out of scope.

## Requirements (Phase 2 of the spec)

1. Five ingredient items; `dittany` is also a plant with a worldgen patch (Forbidden Forest, Horntail Peaks) following `MandrakePatchFeature`; apothecary trades (dittany 2 for 6, asphodel L2 1 for 8, wormwood L2 2 for 6, valerian L1 2 for 4); valerian in Hogwarts ruin camp chests; ashwinder eggs in Death Eater camp and Chamber chests and from a Blaze at 5%.
2. Essence of Dittany: cures Cursed Wound through `CombatEffects.cureCursedWound`, heals 4 HP. Recipe: 3 dittany, stir clockwise 5, simmer 30 s.
3. Mandrake Restorative Draught: cures petrification (splash, 4 blocks, players and statues; or right-click a petrified player with the bottle); drunk by a free player it clears `basilisk_gaze` and gives 8 s where the gaze cannot build. Recipe: 2 mandrake root, 1 asphodel, stir clockwise 4, simmer 45 s, 1 arcane dust; gunpowder after it makes the splash variant.
4. Draught of Living Death: splash only, `stunned` 20 s through `CrowdControl.apply` (diminishing returns, half on bosses, 60% PvP). Recipe: asphodel, wormwood, 2 valerian, stir anticlockwise 7, stir clockwise 1, simmer 60 s, gunpowder.
5. Felix Felicis: 3 minutes of Luck III and +25% mastery XP (players, through the `MasteryAwards` award path). Recipe: ashwinder egg, venom, gold nugget, stir clockwise 3, simmer 600 s, stir anticlockwise 1. One dose.
6. Splash variants through the gunpowder step and a thrown entity following the vanilla splash potion.
7. Petrification, players: the full gaze petrifies (persisted state with the start time, effect with no end); no moving, looking, jumping, attacking, using, casting or inventory; no damage except void, `/kill` and giving in; not a target; no hunger; air held; the stone tint; the choice screen (wait or give in; HUD line with a rebindable key that reopens it; does not pause; chat stays usable); `GiveInPacket` checked on the server, death by the Basilisk damage source as a normal death; state survives logout; `petrificationMaxSeconds` (0 = none); the cure (effect and keys gone, Slowness I 5 s, a line to both players).
8. Petrification, mobs: a statue as today, but it stores the full mob NBT (without the death fields); no loot and no XP on a petrified death (LOWEST, `isCanceled` guard); the cure loads the NBT, full health, no petrification effects, the statue's position and yaw, removes the statue; an old statue brings back a fresh mob; breaking a statue still destroys it.
9. Bosses (`hogwartsmagic:bosses`) keep the lethal gaze.
10. Guide pages for the four recipes and petrification.
11. Done when: each potion does what the table says; a petrified player can wait, give in, or be saved by a second player; a petrified named, tamed wolf comes back with its name and owner; a petrified death drops nothing; Living Death respects diminishing returns and the boss rule; the stone state survives a relog on a dedicated server.

## Rulings made while planning

1. **Recipes (exact):** `dittany` = add `hogwartsmagic:dittany` 3, stir clockwise 5, simmer 30. `mandrake_draught` = add `mandrake_root` 2, add `asphodel` 1, stir clockwise 4, simmer 45, add `arcane_dust` 1, tail gunpowder. `living_death` = add asphodel 1, wormwood 1, valerian_sprigs 2, stir anticlockwise 7, stir clockwise 1, simmer 60, **add gunpowder 1 as a required last step** (it is splash only, so the gunpowder is part of the recipe, not a tail). `felix_felicis` = add `ashwinder_egg` 1, `acromantula_venom` 1, `minecraft:gold_nugget` 1, stir clockwise 3, simmer 600, stir anticlockwise 1; 1 dose. No two first steps collide, no consecutive steps share an action, the Mandrake tail differs from its last step (the Phase 1 table test checks all of it).
2. **Splash variants:** only the Mandrake Draught (optional gunpowder tail) and Living Death (always) are throwable (spec decision 11). Dittany and Felix are drink-only.
3. **Colours (RGB).** Dittany steps `0x7FA65A, 0x9CC27A, 0xC9E3A6`. Mandrake `0x8C6E4A, 0xB7A6C9, 0x9A8FBF, 0x7FB8A8, 0x5FD3C0`, tail gunpowder `0x6E8F86`. Living Death `0xB7A6C9, 0x8F9A6A, 0xC8A2E0, 0xD9C2EC, 0xE8EEF2` (clear as water after the clockwise stir), `0xD8E2EA, 0x9AA3AD`. Felix `0xD9542B, 0x8E9A3A, 0xC9A23A, 0xE0B84A, 0xF2C94C, 0xFFD966`. Bottle tints: Dittany `0xC9E3A6`, Mandrake (both) `0x5FD3C0`, Living Death `0xD8E2EA`, Felix `0xFFD966`.
4. **Arcane dust and gold nuggets become cauldron ingredients** (`BrewRules.isIngredient`), so at a cauldron they are consumed as brewing steps and no longer pass through to vanilla, like every Phase 1 ingredient.
5. **Numbers (`PotionRules`):** Dittany heals 4.0 instantly (`heal`). Felix: `LUCK` amplifier 2 for 3600 ticks; the XP bonus is x1.25 while the caster has Luck at amplifier 2 or more (only Felix gives that in survival; no new effect). Mandrake ward 160 ticks. Cure: Slowness I 100 ticks. Living Death: 400 ticks base. Splash radius 4.0 (a sphere: box `inflate(4, 2, 4)` then distance squared at most 16). Drops: valerian 25% (1-2) in ruin camp chests, ashwinder 10% in Death Eater camp chests, 15% in Chamber chests, 5% from a Blaze (any kill, no looting bonus).
6. **Chest loot is one global loot modifier.** The Death Eater camp uses the vanilla `minecraft:chests/pillager_outpost` table and the Hogwarts ruin camp `minecraft:chests/simple_dungeon` (checked in the gzipped NBT), so the modifier adds loot to those tables only when `ServerLevel.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(key))` finds the camp at the chest (the chest's chunk is loaded; no chunk is loaded). The Chamber's own `hogwartsmagic:chests/chamber_of_secrets` gets eggs directly; the Chamber's `stronghold_corridor` chests do not.
7. **Trades:** Apothecary L1 `sells(event, 1, 6, 1, some(DITTANY, 2), 12, 2)` and `sells(event, 1, 4, 1, some(VALERIAN_SPRIGS, 2), 12, 2)`; L2 `sells(event, 2, 8, 1, one(ASPHODEL), 12, 5)` and `sells(event, 2, 6, 1, some(WORMWOOD, 2), 12, 5)`. Spread 1 = exactly the spec price. Vanilla gives a villager 2 random offers per level, so each apothecary stocks some of these; the second Diagon Alley apothecary and levelling cover the rest. Villagers already past a level keep their old offers. The class Javadoc changes "bottled ingredients" to "brewing ingredients".
8. **The dittany plant** is block `dittany_plant` (no block item of its own); the `dittany` item is an `ItemNameBlockItem` that plants it and is the ingredient. It survives on `#minecraft:dirt`, `#minecraft:base_stone_overworld` and `#minecraft:base_stone_nether` (the Horntail Peaks surface is blackstone, tuff and basalt). Breaking it drops 1-2 dittany. Patch: 2-4 plants within 5 blocks; placed feature count 1, `rarity_filter` 6; biome tag `hogwartsmagic:has_dittany` (both mod biomes, `required: false`); step `vegetal_decoration`.
9. **Ingredient items** are plain items with no tooltip (the guide lists sources); the ashwinder egg is `fireResistant()`. Textures: `build_scripts/generate_brewing_ingredient_textures.py`, hand-placed 16x16 palette grids, byte-stable, `--preview` for a look.
10. **Potion items:** `essence_of_dittany`, `felix_felicis` (Rarity EPIC, enchantment glint), `mandrake_draught` (UNCOMMON) are `BrewedPotionItem`s; `splash_mandrake_draught` (UNCOMMON) and `draught_of_living_death` are `SplashBrewItem`s. All stack to 1 and use vanilla bottle textures tinted by an `ItemColor` (`potion` or `splash_potion` under `potion_overlay`).
11. **The thrown entity** `thrown_brew` (`ThrownBrewEntity extends ThrowableItemProjectile`, 0.25 x 0.25, tracking 4, interval 10, gravity 0.05, `ThrownItemRenderer`). On impact: level event 2002 (vanilla splash particles and glass sound) in the potion's colour, then the effect, then `discard`. Vanilla's `ThrownPotion` cannot carry custom impact logic, hence a new entity. Dispensers do not throw these potions.
12. **Living Death splash** applies `CrowdControl.apply(target, stunned, 400, thrownEntity)` to every living, non-stone entity in the sphere, the thrower included (as a vanilla splash); the projectile as source makes `CombatEffects.responsible` return the thrower for the PvP rule.
13. **Mandrake splash:** stone players in the sphere are cured (rescuer = thrower), statues in the sphere are revived, every other living entity gets the drink's ward. Right-click: a `mandrake_draught` bottle used on a stone player cures them, and used on a statue revives it (one dose, the bottle comes back). Drinking it: `basilisk_gaze` removed and an 8 s ward (`PersistentData` long `hogwartsmagic:gaze_ward_until`); while warded `BasiliskEntity.tickGaze` counts the eyes as not meeting, so the charge drains at its decay rate (2 a tick, 25 ticks from full).
14. **The stone state:** an infinite (`MobEffectInstance.INFINITE_DURATION`) `basilisk_petrification` effect, plus `Petrified` (boolean) and `PetrifiedSince` (long, overworld game time) under `PlayerPersisted/hogwartsmagic`. The keys are the truth: each tick the server re-adds a missing effect when the keys say stone and removes an infinite effect the keys do not back. `Petrification.isPetrified(entity)` (both sides) = the effect is present and infinite, so a dying mob's 60-tick effect is not "stone".
15. **Who is petrified:** survival and adventure players. Creative and spectator players keep today's harmless path (60-tick effect, the magic damage does nothing). Mobs and bosses keep today's lethal path (60-tick effect, `Float.MAX_VALUE` magic damage, statue). The method is renamed `petrify` as the spec says.
16. **The stun lock is reused:** `CombatEffects.isStunned` also returns true for the stone state. That alone refuses casts (`CastSpellPacket` and the other packets check it), item use, attack, block and entity interaction and block breaking on the server, makes the body immobile on both sides (`LivingEntityStunMixin`), and on the client zeroes movement input and swallows clicks (`StunInputLock`). `notifyCastBlocked` gets a stone line. On petrify the server also cancels an Avada windup and any channel (`AvadaWindup.cancel`, `ModEvents.interruptActiveCasting`) and stops item use.
17. **Look and inventory (client):** `PossessionTurnMixin` (the existing `Entity.turn` hook) also cancels the turn for the stone local player; while stone, the client drains the drop, swap-hands and inventory keys and cancels any `AbstractContainerScreen`, the wand radial menu and the spell menu from opening (the Imperius pattern). Drop is not enforced on the server.
18. **Damage while stone:** a `LivingAttackEvent` handler at LOWEST with an `isCanceled` guard cancels every source except `minecraft:out_of_world`, `minecraft:generic_kill` and `hogwartsmagic:petrification`. Hunger: exhaustion set to 0 each tick. Air: set to max each tick.
19. **Not a target:** a `LivingChangeTargetEvent` handler cancels any new target that is stone; once a second the server also clears `Mob`s within 32 blocks whose current target is the stone player. The Basilisk's gaze skips stone victims (`isValidVictim`). Brain-driven mobs (piglins, wardens) are not covered.
20. **Others see the stone:** `basilisk_petrification` joins `VisibleEffects.isVisible`, because vanilla never sends a player's effects to watchers; `ClientForgeEvents` already tints anyone with the effect, and the client knows to offer the right-click cure.
21. **Giving in:** a new damage type `hogwartsmagic:petrification` (message "gave in to the stone"), in the damage type tags `bypasses_armor`, `bypasses_effects`, `bypasses_enchantments`, `bypasses_invulnerability` (so a totem does not undo the choice), `bypasses_resistance`, `no_knockback`. `Petrification.giveIn` checks the keys and that the player is alive, clears the keys and the effect first, then deals `Float.MAX_VALUE` of that type: a normal death (Horcrux, drops, `keepInventory`). The mob path keeps vanilla magic damage.
22. **Death cleanup:** a `LivingDeathEvent` handler (LOWEST, guard) clears the keys of a stone player who dies another way (void, `/kill`), so the respawned player is free (`HorcruxData.copyPersistent` copies `PlayerPersisted` on respawn).
23. **Time limit:** config `petrificationMaxSeconds` (0..86400, default 0). Checked once a second, phased by entity id, against `PetrifiedSince` in overworld game time, so time logged out counts; at the limit the player gives in automatically with a line.
24. **The choice screen** opens on the client when the local player becomes stone, but only once no other screen is open (so it never replaces the world-loading screen or open chat); it also opens on login as stone. "Wait for rescue" closes it; "Give in" sends `GiveInPacket` once and closes it; Esc = wait. It does not pause; the chat key opens chat from it. It closes itself when the stone state ends. HUD line (no screen open): "You are petrified. Press [key] to give in." centred 24 px below the screen centre, drawn from `BasiliskGazeGuiMixin` (Forge 51 has no HUD event).
25. **The key** `key.hogwartsmagic.give_in` defaults to **J**, not G: G is already the Grimoire key (`ClientModEvents.GRIMOIRE_KEY`), and a shared default would make one of them dead. The HUD line names whatever key is bound.
26. **The network protocol** goes up by one from whatever `SpellPackets` declares when Task 6 runs (Dark Magic Phase 1 raises it first). `GiveInPacket` is an empty record, registered last in `register()`.
27. **Statues keep the mob:** `PetrifiedRemainsEntity.fromLiving` stores `victim.saveWithoutId(new CompoundTag())` minus `Health, DeathTime, HurtTime, HurtByTimestamp, Fire, FallDistance, Motion, Passengers, leash, SleepingX, SleepingY, SleepingZ, TicksFrozen, HasVisualFire` under `StoredMob`, for non-bosses only. `active_effects`, `attributes` and `Brain` are kept: effects add permanent attribute modifiers saved in `attributes`, so stripping the effects alone would leave a revived mob slowed forever. It is saved with the statue and never synced, so `PetrifiedRemainsRenderer` (which builds its surrogate from the synced type) is unchanged. A boss statue stays cosmetic and cannot be revived.
28. **No loot, no XP:** `LivingDropsEvent` and `LivingExperienceDropEvent` handlers (LOWEST, guard) cancel for a non-player, non-boss entity that has `basilisk_petrification` (the 60-tick effect outlives the 20-tick death animation). Bosses keep their drops (the old rule). Breaking a statue loses the mob and its items (spec item 9).
29. **The revive:** `type.create(level)`, `load(StoredMob)` when present (a statue from an older world has none: a fresh mob, no `finalizeSpawn`), a new random UUID if the old one is still in the level (the corpse lasts 20 ticks), `moveTo` the statue's position and yaw, body and head yaw, `deathTime = 0`, full health, `addFreshEntity`, statue discarded, a stone burst (30 stone block particles and `STONE_BREAK` at pitch 0.8), and an action bar line to the rescuer. After loading the stored data, the revive calls removeAllEffects() so vanilla removes the effects' attribute modifiers; attributes and Brain are kept.
30. **Lines:** to the stone player on petrify (chat): "You are turned to stone..."; on a cure by someone else, both get a chat line ("X brought you back." / "You brought X back."); a cure with no other player as rescuer (the thrower of the splash has logged out, so the projectile has no owner) gets "The stone falls away."; time limit: "You have been stone too long. You give in."
31. **Felix drink:** 16 `WAX_ON` particles and `AMETHYST_BLOCK_CHIME` at pitch 1.6 and an action bar line; Dittany: 8 `HAPPY_VILLAGER`; Mandrake drink: an action bar line.
32. **Guide:** nine new pages at the end of the Potions section: the four recipes (two pages each for Living Death and Felix, which have six or seven steps), how to use the Mandrake Draught, where ingredients come from and "Turned to stone". The basilisk page is unchanged. `MOD_FEATURES_WIKI.md` and `wiki/` are Phase 3.
33. **Shared files are anchored on Phase 1 or older text** that Dark Magic Phase 1 is unlikely to touch: lang lines go after `"brew.hogwartsmagic.polyjuice": "Polyjuice Potion",` (and each later task after the previous task's last line); `Config` after the `pvpCrowdControlMultiplier` lines; `SpellPackets` by "the end of `register()`" and "the end of the class"; `ExampleMod` next to the mandrake, gillyweed, failed-potion and tome-loot lines. `ModEvents.onPlayerTick` is not touched: the stone tick is its own subscriber.

## Global Constraints

- Server authoritative. `brewing/`, `petrification/`, `item/`, `entity/`, `block/`, `loot/`, `worldgen/` never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/`, the client-only `event/ClientModEvents.java` and the client mixins.
- No `switch` on an enum anywhere in the new code. Use if-chains and `==`.
- Never load a chunk. Entity scans use `getEntitiesOfClass`; the loot modifier reads the structure references of the chest's own (loaded) chunk.
- Every new hurt, death or drop handler: `EventPriority.LOWEST` and an `isCanceled` guard.
- Cursed Wound is cured only through `CombatEffects.cureCursedWound`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- Screens: `renderTransparentBackground`, no blur.
- `en_us.json` and every JSON file touched stay valid JSON. Validate with `python3 -c "import json;json.load(open('<file>',encoding='utf-8'))"`.
- Build and test (cloud):
  - First `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If a build is running, wait (poll every 30 s, at most 20 min).
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*PotionRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- Dedicated-server boot (Tasks 3, 6, 9 and 11), "the boot block":
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
- **Tests: the count at the start of Task 1 (383 at 996f638 plus whatever Dark Magic Phase 1 added); all green.** Call it BASE. They stay unchanged (two lines of `BrewRulesTest` are edited in Task 1, no test is removed) and green. Task 1 adds 14 tests, Task 2 adds 11; no other task adds tests. Before Task 1, run the test count command once and write BASE into the ledger.
- Line numbers drift, and Dark Magic Phase 1 lands on this branch before this plan runs. Every edit names the exact text to find, quoted from 996f638 (or from an earlier task of this plan, which says so). If an anchor is not found exactly once, stop and report it; do not guess.

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Modify `src/main/java/com/hogwartsmagic/brewing/BrewRules.java` | 7 item names, `FELIX_DOSES`, 4 recipes, `RECIPES` | 1 |
| Modify `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java` | Table test: Felix doses, 7 recipes | 1 |
| Create `src/test/java/com/hogwartsmagic/brewing/BrewRulesPhase2Test.java` | 9 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/brewing/PotionRules.java` | Potion numbers, drop chances | 1 |
| Create `src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java` | 5 tests | 1 |
| Create `src/main/java/com/hogwartsmagic/petrification/PetrificationRules.java` | Stone state rules | 2 |
| Create `src/test/java/com/hogwartsmagic/petrification/PetrificationRulesTest.java` | 11 tests | 2 |
| Create `src/main/java/com/hogwartsmagic/block/DittanyPlantBlock.java` | The plant | 3 |
| Create `src/main/java/com/hogwartsmagic/worldgen/DittanyPatchFeature.java` | The patch | 3 |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Plant, feature, 5 items, tab (T3); loot modifier (T4); 2 potions (T5); 3 potions (T9) | 3, 4, 5, 9 |
| Create `build_scripts/generate_brewing_ingredient_textures.py` + 6 PNGs | Textures | 3 |
| Create plant blockstate, block model, 5 item models, plant loot table, worldgen JSONs, biome tag, biome modifier | Data and assets | 3 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Lines in T3, T5, T6, T7, T8, T9 | 3-9 |
| Modify `src/main/java/com/hogwartsmagic/entity/ModVillagers.java` | Four trades, Javadoc | 4 |
| Create `src/main/java/com/hogwartsmagic/loot/BrewingIngredientLootModifier.java`, `loot_modifiers/brewing_ingredients_in_chests.json`; modify `data/forge/loot_modifiers/global_loot_modifiers.json` | Chest loot | 4 |
| Create `src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java` | Blaze egg | 4 |
| Modify `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java` | Dittany, Felix (T5); Mandrake drink, right-click cure, `useUpDose` (T9) | 5, 9 |
| Modify `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java` | Bottling the new brews | 5, 9 |
| Modify `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` | Felix XP | 5 |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Item colours (T5, T9), key (T8), renderer (T9) | 5, 8, 9 |
| Create item models `essence_of_dittany`, `felix_felicis` (T5); `mandrake_draught`, `splash_mandrake_draught`, `draught_of_living_death` (T9) | Vanilla bottle layers | 5, 9 |
| Create `src/main/java/com/hogwartsmagic/petrification/Petrification.java` | Server and common stone logic | 6 (T7, T9 add) |
| Create `src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java` | Tick, damage, target, death (T6); drops, XP (T7) | 6, 7 |
| Create `data/hogwartsmagic/damage_type/petrification.json`; modify/create 6 tags under `data/minecraft/tags/damage_type/` | Give-in damage | 6 |
| Modify `src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java` | `petrify`, victim filter, ward | 6 |
| Modify `src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java` | `isStunned`, `notifyCastBlocked` | 6 |
| Modify `src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java` | Stone is visible | 6 |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | `petrificationMaxSeconds` | 6 |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | `GiveInPacket`, protocol +1 | 6 |
| Modify `src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java` | `StoredMob` (T7), right-click revive (T9) | 7, 9 |
| Create `src/main/java/com/hogwartsmagic/client/PetrificationClient.java`, `client/PetrifiedChoiceScreen.java` | Client half | 8 |
| Modify `src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java`, `mixin/client/BasiliskGazeGuiMixin.java` | Look lock, HUD line | 8 |
| Create `src/main/java/com/hogwartsmagic/item/SplashBrewItem.java`, `entity/ThrownBrewEntity.java` | Splash potions | 9 |
| Modify `src/main/java/com/hogwartsmagic/entity/ModEntities.java` | `THROWN_BREW` | 9 |
| Modify `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java` | Nine pages | 10 |

Untouched on purpose: `event/ModEvents.java` (the statue is still made in its `onLivingDeath`; the stone tick has its own subscriber), `BasiliskPetrificationEffect`, `PetrifiedRemainsRenderer`, `ClientForgeEvents` (the tint already follows the effect), `StunInputLock`, `LivingEntityStunMixin`, `CrowdControl`, the mixin config (no new mixin), the advancements, `MOD_FEATURES_WIKI.md` and `wiki/` (Phase 3).

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T3, T5, T9 | `BrewRules.DITTANY, ASPHODEL, WORMWOOD, VALERIAN_SPRIGS, ASHWINDER_EGG, ARCANE_DUST, GOLD_NUGGET, FELIX_DOSES`; recipes `DITTANY_ESSENCE` ("dittany"), `MANDRAKE_DRAUGHT` ("mandrake_draught"), `LIVING_DEATH` ("living_death"), `FELIX_FELICIS` ("felix_felicis") | ids match the `brew.hogwartsmagic.<id>` lang keys of T3 |
| T1 → T4-T9 | `PotionRules.DITTANY_HEAL, FELIX_TICKS, FELIX_LUCK_AMPLIFIER, FELIX_XP_MULTIPLIER, MANDRAKE_GAZE_WARD_TICKS, CURE_SLOWNESS_TICKS, LIVING_DEATH_STUN_TICKS, SPLASH_RADIUS, VALERIAN_RUIN_CAMP_CHANCE, ASHWINDER_DEATH_EATER_CAMP_CHANCE, ASHWINDER_CHAMBER_CHANCE, ASHWINDER_BLAZE_CHANCE`; `felixXpMultiplier(int)`, `inSplash(double)`, `rolls(double,double)` | identical |
| T2 → T6, T7 | `PetrificationRules.GazeOutcome {PETRIFY, KILL}`, `gazeOutcome(boolean,boolean)`, `damagePasses(String)`, `limitReached(long,long,int)`, `ticksPetrified(long,long)`, `canRevive(boolean,boolean,boolean)`, `giveInAccepted(boolean,boolean)`, `wardUntil(long)`, `warded(long,long)`, `dueCheck(long,int)`; constants `KEY_PETRIFIED, KEY_SINCE, WARD_KEY, DAMAGE_TYPE, VOID_DAMAGE, KILL_DAMAGE, DEFAULT_MAX_SECONDS, MAX_MAX_SECONDS, TARGET_CLEAR_RADIUS, CHECK_INTERVAL_TICKS, STRIPPED_MOB_KEYS` | identical |
| T3 → T4, T5, T9 | `ExampleMod.DITTANY_PLANT, DITTANY, ASPHODEL, WORMWOOD, VALERIAN_SPRIGS, ASHWINDER_EGG, DITTANY_PATCH_FEATURE` | identical |
| T5 → T9 | `ExampleMod.ESSENCE_OF_DITTANY, FELIX_FELICIS`; `BrewedPotionItem.Kind.DITTANY, FELIX`; T5's text in `bottledPotion`, the colour list and the tab, quoted by T9 | identical |
| T6 → T7, T8, T9 | `Petrification.isPetrified(LivingEntity)`, `hasPetrifiedFlag(ServerPlayer)`, `petrify`, `tick`, `cure(ServerPlayer, Entity)`, `giveIn(ServerPlayer)`, `clearFlag`, `damage(Level)`, `wardGaze(LivingEntity)`, `isGazeWarded(LivingEntity)`, `stoneBurst(ServerLevel, Entity)`, `stone()`; `SpellPackets.GiveInPacket` | identical |
| T7 → T9 | `Petrification.captureMob(LivingEntity)`, `isStatueDeath(LivingEntity)`, `reviveStatue(ServerLevel, PetrifiedRemainsEntity, Entity)`; `PetrifiedRemainsEntity.getStoredMob()` | identical |
| Lang chain | T3 inserts after `"brew.hogwartsmagic.polyjuice": "Polyjuice Potion",` and ends with `"brew.hogwartsmagic.felix_felicis": "Felix Felicis",`; T5 ends with `"message.hogwartsmagic.felix.drink"`; T6 with `"death.attack.hogwartsmagic.petrification"`; T7 with `"message.hogwartsmagic.petrification.statue_revived"`; T8 with `"gui.hogwartsmagic.petrified.hud"`; T9 with `"entity.hogwartsmagic.thrown_brew"` | each task quotes the previous task's last line |

---

### Task 1: New recipes and PotionRules (pure, test-first)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/brewing/BrewRules.java`
- Modify: `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java`
- Create: `src/test/java/com/hogwartsmagic/brewing/BrewRulesPhase2Test.java`
- Create: `src/main/java/com/hogwartsmagic/brewing/PotionRules.java`
- Create: `src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java`

**Interfaces.** Consumes: Phase 1 `BrewRules` (`Step`, `Recipe`, `match`, `append`, `simmerLeft`, `wantsSimmer`, `overbrewClock`, `color`, `usedTail`, `isIngredient`). Produces: the T1 rows of the pre-flight table.

- [ ] **Step 0: Record BASE.** Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --console=plain --max-workers=2` and the test count command. Expected: BUILD SUCCESSFUL. Write the number as BASE in the ledger.

- [ ] **Step 1: Write the failing tests.** Create `src/test/java/com/hogwartsmagic/brewing/BrewRulesPhase2Test.java`:

```java
package com.hogwartsmagic.brewing;

import com.hogwartsmagic.brewing.BrewRules.Match;
import com.hogwartsmagic.brewing.BrewRules.Status;
import com.hogwartsmagic.brewing.BrewRules.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2 recipes: Essence of Dittany, Mandrake Restorative Draught, Draught of Living Death, Felix Felicis. */
class BrewRulesPhase2Test {
    private static final String DITTANY = "hogwartsmagic:dittany";
    private static final String ASPHODEL = "hogwartsmagic:asphodel";
    private static final String WORMWOOD = "hogwartsmagic:wormwood";
    private static final String VALERIAN = "hogwartsmagic:valerian_sprigs";
    private static final String ASHWINDER = "hogwartsmagic:ashwinder_egg";
    private static final String MANDRAKE = "hogwartsmagic:mandrake_root";
    private static final String VENOM = "hogwartsmagic:acromantula_venom";
    private static final String DUST = "hogwartsmagic:arcane_dust";
    private static final String NUGGET = "minecraft:gold_nugget";
    private static final String GUNPOWDER = "minecraft:gunpowder";

    /** Applies single actions one by one, as the cauldron does. */
    private static List<Step> run(List<Step> start, Step... actions) {
        List<Step> history = start;
        for (Step action : actions) {
            history = BrewRules.append(history, action);
        }
        return history;
    }

    private static List<Step> run(Step... actions) {
        return run(new ArrayList<>(), actions);
    }

    private static List<Step> times(List<Step> start, Step action, int count) {
        List<Step> history = start;
        for (int i = 0; i < count; i++) {
            history = BrewRules.append(history, action);
        }
        return history;
    }

    private static List<Step> simmer(List<Step> history, int seconds) {
        return times(history, Step.simmer(1), seconds);
    }

    private static Step add(String item) {
        return Step.add(item, 1);
    }

    private static Step cw() {
        return Step.stirClockwise(1);
    }

    private static Step ccw() {
        return Step.stirAnticlockwise(1);
    }

    private static List<Step> dittanyToSimmer() {
        return times(times(new ArrayList<>(), add(DITTANY), 3), cw(), 5);
    }

    private static List<Step> mandrakeSimmered() {
        return simmer(run(add(MANDRAKE), add(MANDRAKE), add(ASPHODEL), cw(), cw(), cw(), cw()), 45);
    }

    private static List<Step> livingDeathStart() {
        return run(add(ASPHODEL), add(WORMWOOD), add(VALERIAN), add(VALERIAN));
    }

    private static List<Step> livingDeathSimmered() {
        return simmer(run(times(livingDeathStart(), ccw(), 7), cw()), 60);
    }

    private static List<Step> felixSimmered() {
        return simmer(run(add(ASHWINDER), add(VENOM), add(NUGGET), cw(), cw(), cw()), 600);
    }

    @Test
    void dittanyBrews() {
        assertEquals(30, BrewRules.simmerLeft(dittanyToSimmer()));
        Match match = BrewRules.match(simmer(dittanyToSimmer(), 30));
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.DITTANY_ESSENCE, match.recipe());
        assertEquals(BrewRules.MAX_DOSES, match.recipe().doses());
        assertEquals(0xC9E3A6, BrewRules.color(simmer(dittanyToSimmer(), 30)));
    }

    @Test
    void mandrakeDraughtFinishesOnArcaneDust() {
        List<Step> simmered = mandrakeSimmered();
        assertEquals(Status.ON_TRACK, BrewRules.match(simmered).status());
        assertTrue(BrewRules.overbrewClock(simmered));
        List<Step> brewed = run(simmered, add(DUST));
        Match match = BrewRules.match(brewed);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.MANDRAKE_DRAUGHT, match.recipe());
        assertFalse(BrewRules.usedTail(brewed, GUNPOWDER));
        assertEquals(0x5FD3C0, BrewRules.color(brewed));
    }

    @Test
    void mandrakeGunpowderMakesItSplashOnce() {
        List<Step> splash = run(mandrakeSimmered(), add(DUST), add(GUNPOWDER));
        assertEquals(Status.FINISHED, BrewRules.match(splash).status());
        assertTrue(BrewRules.usedTail(splash, GUNPOWDER));
        assertEquals(0x6E8F86, BrewRules.color(splash));
        assertEquals(Status.RUINED, BrewRules.match(run(splash, add(GUNPOWDER))).status());
    }

    @Test
    void livingDeathNeedsItsGunpowder() {
        List<Step> simmered = livingDeathSimmered();
        Match waiting = BrewRules.match(simmered);
        assertEquals(Status.ON_TRACK, waiting.status());
        assertSame(BrewRules.LIVING_DEATH, waiting.recipe());
        assertFalse(BrewRules.wantsSimmer(simmered));
        assertTrue(BrewRules.overbrewClock(simmered));
        List<Step> done = run(simmered, add(GUNPOWDER));
        Match finished = BrewRules.match(done);
        assertEquals(Status.FINISHED, finished.status());
        assertSame(BrewRules.LIVING_DEATH, finished.recipe());
        assertEquals(0x9AA3AD, BrewRules.color(done));
    }

    @Test
    void livingDeathStirsAreCounted() {
        List<Step> start = livingDeathStart();
        assertEquals(Status.RUINED, BrewRules.match(run(start, cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(run(times(start, ccw(), 6), cw())).status());
        assertEquals(Status.RUINED, BrewRules.match(times(start, ccw(), 8)).status());
        List<Step> right = run(times(start, ccw(), 7), cw());
        assertEquals(Status.ON_TRACK, BrewRules.match(right).status());
        assertEquals(0xE8EEF2, BrewRules.color(right));
        assertEquals(60, BrewRules.simmerLeft(right));
    }

    @Test
    void felixSimmersTenMinutesAndGivesOneDose() {
        assertEquals(600, BrewRules.simmerLeft(run(add(ASHWINDER), add(VENOM), add(NUGGET), cw(), cw(), cw())));
        List<Step> simmered = felixSimmered();
        assertEquals(Status.ON_TRACK, BrewRules.match(simmered).status());
        assertTrue(BrewRules.overbrewClock(simmered));
        List<Step> done = run(simmered, ccw());
        Match match = BrewRules.match(done);
        assertEquals(Status.FINISHED, match.status());
        assertSame(BrewRules.FELIX_FELICIS, match.recipe());
        assertEquals(1, BrewRules.FELIX_DOSES);
        assertEquals(BrewRules.FELIX_DOSES, match.recipe().doses());
        assertEquals(0xFFD966, BrewRules.color(done));
    }

    @Test
    void felixLastStirMustBeAnticlockwise() {
        assertEquals(Status.RUINED, BrewRules.match(run(felixSimmered(), cw())).status());
    }

    @Test
    void newIngredientsAreIngredients() {
        assertEquals(DITTANY, BrewRules.DITTANY);
        assertEquals(ASPHODEL, BrewRules.ASPHODEL);
        assertEquals(WORMWOOD, BrewRules.WORMWOOD);
        assertEquals(VALERIAN, BrewRules.VALERIAN_SPRIGS);
        assertEquals(ASHWINDER, BrewRules.ASHWINDER_EGG);
        assertEquals(DUST, BrewRules.ARCANE_DUST);
        assertEquals(NUGGET, BrewRules.GOLD_NUGGET);
        for (String id : List.of(DITTANY, ASPHODEL, WORMWOOD, VALERIAN, ASHWINDER, DUST, NUGGET)) {
            assertTrue(BrewRules.isIngredient(id), id);
        }
    }

    @Test
    void aFirstIngredientNamesTheNewRecipes() {
        assertSame(BrewRules.DITTANY_ESSENCE, BrewRules.match(run(add(DITTANY))).recipe());
        assertSame(BrewRules.MANDRAKE_DRAUGHT, BrewRules.match(run(add(MANDRAKE))).recipe());
        assertSame(BrewRules.LIVING_DEATH, BrewRules.match(run(add(ASPHODEL))).recipe());
        assertSame(BrewRules.FELIX_FELICIS, BrewRules.match(run(add(ASHWINDER))).recipe());
        assertEquals(Status.RUINED, BrewRules.match(run(add(NUGGET))).status());
        assertEquals(Status.RUINED, BrewRules.match(run(add(DUST))).status());
    }
}
```

Create `src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java`:

```java
package com.hogwartsmagic.brewing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionRulesTest {
    @Test
    void numbersMatchTheSpec() {
        assertEquals(4.0f, PotionRules.DITTANY_HEAL);
        assertEquals(3600, PotionRules.FELIX_TICKS);
        assertEquals(2, PotionRules.FELIX_LUCK_AMPLIFIER);
        assertEquals(1.25, PotionRules.FELIX_XP_MULTIPLIER);
        assertEquals(160, PotionRules.MANDRAKE_GAZE_WARD_TICKS);
        assertEquals(100, PotionRules.CURE_SLOWNESS_TICKS);
        assertEquals(400, PotionRules.LIVING_DEATH_STUN_TICKS);
        assertEquals(4.0, PotionRules.SPLASH_RADIUS);
    }

    @Test
    void felixBonusNeedsLuckThree() {
        assertEquals(1.0, PotionRules.felixXpMultiplier(-1));
        assertEquals(1.0, PotionRules.felixXpMultiplier(0));
        assertEquals(1.0, PotionRules.felixXpMultiplier(1));
        assertEquals(1.25, PotionRules.felixXpMultiplier(2));
        assertEquals(1.25, PotionRules.felixXpMultiplier(5));
    }

    @Test
    void splashReachesFourBlocks() {
        assertTrue(PotionRules.inSplash(0.0));
        assertTrue(PotionRules.inSplash(16.0));
        assertFalse(PotionRules.inSplash(16.01));
    }

    @Test
    void rollsLandBelowTheChance() {
        assertTrue(PotionRules.rolls(0.0, 0.05));
        assertTrue(PotionRules.rolls(0.0499, 0.05));
        assertFalse(PotionRules.rolls(0.05, 0.05));
        assertFalse(PotionRules.rolls(0.5, 0.0));
    }

    @Test
    void dropChancesAreTheRuledOnes() {
        assertEquals(0.25, PotionRules.VALERIAN_RUIN_CAMP_CHANCE);
        assertEquals(0.10, PotionRules.ASHWINDER_DEATH_EATER_CAMP_CHANCE);
        assertEquals(0.15, PotionRules.ASHWINDER_CHAMBER_CHANCE);
        assertEquals(0.05, PotionRules.ASHWINDER_BLAZE_CHANCE);
    }
}
```

In `src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java`, method `theTableIsWellFormed`, find:

```java
            assertEquals(BrewRules.MAX_DOSES, recipe.doses(), recipe.id());
```

and replace it with:

```java
            int doses = recipe == BrewRules.FELIX_FELICIS ? BrewRules.FELIX_DOSES : BrewRules.MAX_DOSES;
            assertEquals(doses, recipe.doses(), recipe.id());
```

then find:

```java
        assertEquals(List.of(BrewRules.WIGGENWELD, BrewRules.PEPPERUP, BrewRules.POLYJUICE), BrewRules.RECIPES);
```

and replace it with:

```java
        assertEquals(List.of(BrewRules.WIGGENWELD, BrewRules.PEPPERUP, BrewRules.POLYJUICE, BrewRules.DITTANY_ESSENCE,
                BrewRules.MANDRAKE_DRAUGHT, BrewRules.LIVING_DEATH, BrewRules.FELIX_FELICIS), BrewRules.RECIPES);
```

- [ ] **Step 2: Run them and see them fail.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*BrewRules*" --tests "*PotionRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... DITTANY_ESSENCE`, `PotionRules`).

- [ ] **Step 3: The recipes.** In `src/main/java/com/hogwartsmagic/brewing/BrewRules.java`, find:

```java
    public static final String POLYJUICE_SAMPLE = "hogwartsmagic:polyjuice_sample";
```

and replace it with:

```java
    public static final String POLYJUICE_SAMPLE = "hogwartsmagic:polyjuice_sample";
    public static final String DITTANY = "hogwartsmagic:dittany";
    public static final String ASPHODEL = "hogwartsmagic:asphodel";
    public static final String WORMWOOD = "hogwartsmagic:wormwood";
    public static final String VALERIAN_SPRIGS = "hogwartsmagic:valerian_sprigs";
    public static final String ASHWINDER_EGG = "hogwartsmagic:ashwinder_egg";
    public static final String ARCANE_DUST = "hogwartsmagic:arcane_dust";
    public static final String GOLD_NUGGET = "minecraft:gold_nugget";

    /** Felix Felicis is the one brew that gives a single dose (spec decision 7). */
    public static final int FELIX_DOSES = 1;
```

Then find:

```java
    public static final List<Recipe> RECIPES = List.of(WIGGENWELD, PEPPERUP, POLYJUICE);
```

and replace it with:

```java
    public static final Recipe DITTANY_ESSENCE = new Recipe("dittany",
            List.of(Step.add(DITTANY, 3), Step.stirClockwise(5), Step.simmer(30)),
            List.of(0x7FA65A, 0x9CC27A, 0xC9E3A6),
            List.of(), List.of(), MAX_DOSES);

    /** The arcane dust finishes it; gunpowder after it makes every dose a splash potion. */
    public static final Recipe MANDRAKE_DRAUGHT = new Recipe("mandrake_draught",
            List.of(Step.add(MANDRAKE_ROOT, 2), Step.add(ASPHODEL, 1), Step.stirClockwise(4), Step.simmer(45),
                    Step.add(ARCANE_DUST, 1)),
            List.of(0x8C6E4A, 0xB7A6C9, 0x9A8FBF, 0x7FB8A8, 0x5FD3C0),
            List.of(Step.add(GUNPOWDER, 1)),
            List.of(0x6E8F86),
            MAX_DOSES);

    /**
     * Splash only, so the gunpowder is a required last step. "Anticlockwise seven, then clockwise once" is the
     * Half-Blood Prince's note; after the clockwise stir the brew turns clear as water.
     */
    public static final Recipe LIVING_DEATH = new Recipe("living_death",
            List.of(Step.add(ASPHODEL, 1), Step.add(WORMWOOD, 1), Step.add(VALERIAN_SPRIGS, 2),
                    Step.stirAnticlockwise(7), Step.stirClockwise(1), Step.simmer(60), Step.add(GUNPOWDER, 1)),
            List.of(0xB7A6C9, 0x8F9A6A, 0xC8A2E0, 0xD9C2EC, 0xE8EEF2, 0xD8E2EA, 0x9AA3AD),
            List.of(), List.of(), MAX_DOSES);

    public static final Recipe FELIX_FELICIS = new Recipe("felix_felicis",
            List.of(Step.add(ASHWINDER_EGG, 1), Step.add(ACROMANTULA_VENOM, 1), Step.add(GOLD_NUGGET, 1),
                    Step.stirClockwise(3), Step.simmer(600), Step.stirAnticlockwise(1)),
            List.of(0xD9542B, 0x8E9A3A, 0xC9A23A, 0xE0B84A, 0xF2C94C, 0xFFD966),
            List.of(), List.of(), FELIX_DOSES);

    public static final List<Recipe> RECIPES = List.of(WIGGENWELD, PEPPERUP, POLYJUICE, DITTANY_ESSENCE,
            MANDRAKE_DRAUGHT, LIVING_DEATH, FELIX_FELICIS);
```

- [ ] **Step 4: PotionRules.** Create `src/main/java/com/hogwartsmagic/brewing/PotionRules.java`:

```java
package com.hogwartsmagic.brewing;

/**
 * The numbers of the Phase 2 potions and of the ingredient drops, free of Minecraft so they are unit-tested. The
 * items, the thrown entity, the loot modifier and the petrification code read them from here.
 */
public final class PotionRules {
    /** Essence of Dittany heals this much at once, after curing a Cursed Wound. */
    public static final float DITTANY_HEAL = 4.0f;
    /** Felix Felicis: 3 minutes of Luck III. */
    public static final int FELIX_TICKS = 20 * 60 * 3;
    public static final int FELIX_LUCK_AMPLIFIER = 2;
    /** Mastery XP while lucky (+25%). */
    public static final double FELIX_XP_MULTIPLIER = 1.25;
    /** A drink of Mandrake Draught: the basilisk's gaze cannot build on the drinker for 8 seconds. */
    public static final int MANDRAKE_GAZE_WARD_TICKS = 20 * 8;
    /** A cured stone player gets Slowness I for 5 seconds. */
    public static final int CURE_SLOWNESS_TICKS = 20 * 5;
    /** Draught of Living Death: base stun, before CrowdControl's rules. */
    public static final int LIVING_DEATH_STUN_TICKS = 20 * 20;
    /** Both splash potions reach this far (a sphere). */
    public static final double SPLASH_RADIUS = 4.0;

    public static final double VALERIAN_RUIN_CAMP_CHANCE = 0.25;
    public static final double ASHWINDER_DEATH_EATER_CAMP_CHANCE = 0.10;
    public static final double ASHWINDER_CHAMBER_CHANCE = 0.15;
    public static final double ASHWINDER_BLAZE_CHANCE = 0.05;

    private PotionRules() {
    }

    /** The mastery XP multiplier for a caster whose Luck amplifier is this (-1 without Luck). Luck III or more is Felix. */
    public static double felixXpMultiplier(int luckAmplifier) {
        return luckAmplifier >= FELIX_LUCK_AMPLIFIER ? FELIX_XP_MULTIPLIER : 1.0;
    }

    /** An entity this far (squared) from the impact is splashed. */
    public static boolean inSplash(double distanceSqr) {
        return distanceSqr <= SPLASH_RADIUS * SPLASH_RADIUS;
    }

    /** A roll in [0, 1) lands when it is below the chance. */
    public static boolean rolls(double roll, double chance) {
        return roll < chance;
    }
}
```

- [ ] **Step 5: Run them and see them pass.** Same command as Step 2. Expected: BUILD SUCCESSFUL, all `BrewRulesTest`, `BrewRulesPhase2Test` and `PotionRulesTest` tests pass. Then the full `compileJava test`: BASE + 14 tests, all green.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Brewing Phase 2: four recipes and PotionRules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/test/java/com/hogwartsmagic/brewing/BrewRulesPhase2Test.java src/main/java/com/hogwartsmagic/brewing/PotionRules.java src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/brewing/BrewRules.java src/test/java/com/hogwartsmagic/brewing/BrewRulesTest.java src/test/java/com/hogwartsmagic/brewing/BrewRulesPhase2Test.java src/main/java/com/hogwartsmagic/brewing/PotionRules.java src/test/java/com/hogwartsmagic/brewing/PotionRulesTest.java
```

---

### Task 2: PetrificationRules (pure, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/petrification/PetrificationRules.java`
- Create: `src/test/java/com/hogwartsmagic/petrification/PetrificationRulesTest.java`

**Interfaces.** Consumes: `PotionRules.MANDRAKE_GAZE_WARD_TICKS` (T1). Produces: the T2 row of the pre-flight table.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/petrification/PetrificationRulesTest.java`:

```java
package com.hogwartsmagic.petrification;

import com.hogwartsmagic.petrification.PetrificationRules.GazeOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetrificationRulesTest {
    @Test
    void constantsMatchThePlan() {
        assertEquals("Petrified", PetrificationRules.KEY_PETRIFIED);
        assertEquals("PetrifiedSince", PetrificationRules.KEY_SINCE);
        assertEquals("hogwartsmagic:gaze_ward_until", PetrificationRules.WARD_KEY);
        assertEquals("hogwartsmagic:petrification", PetrificationRules.DAMAGE_TYPE);
        assertEquals(0, PetrificationRules.DEFAULT_MAX_SECONDS);
        assertEquals(86400, PetrificationRules.MAX_MAX_SECONDS);
        assertEquals(32, PetrificationRules.TARGET_CLEAR_RADIUS);
        assertEquals(20, PetrificationRules.CHECK_INTERVAL_TICKS);
    }

    @Test
    void aPlayerTurnsToStoneAndAMobDies() {
        assertEquals(GazeOutcome.PETRIFY, PetrificationRules.gazeOutcome(true, false));
        assertEquals(GazeOutcome.KILL, PetrificationRules.gazeOutcome(true, true));
        assertEquals(GazeOutcome.KILL, PetrificationRules.gazeOutcome(false, false));
        assertEquals(GazeOutcome.KILL, PetrificationRules.gazeOutcome(false, true));
    }

    @Test
    void onlyTheVoidKillAndGivingInHurtStone() {
        assertTrue(PetrificationRules.damagePasses("minecraft:out_of_world"));
        assertTrue(PetrificationRules.damagePasses("minecraft:generic_kill"));
        assertTrue(PetrificationRules.damagePasses("hogwartsmagic:petrification"));
        for (String id : List.of("minecraft:magic", "minecraft:lava", "minecraft:starve", "minecraft:drown",
                "minecraft:player_attack", "hogwartsmagic:bleeding", "")) {
            assertFalse(PetrificationRules.damagePasses(id), id);
        }
    }

    @Test
    void noLimitMeansStoneForever() {
        assertFalse(PetrificationRules.limitReached(0L, 1_000_000_000L, 0));
        assertFalse(PetrificationRules.limitReached(0L, 1_000_000_000L, -5));
    }

    @Test
    void theLimitCountsGameTicks() {
        assertFalse(PetrificationRules.limitReached(100L, 199L, 5));
        assertTrue(PetrificationRules.limitReached(100L, 200L, 5));
        assertTrue(PetrificationRules.limitReached(100L, 900L, 5));
    }

    @Test
    void timeAsStoneIsNeverNegative() {
        assertEquals(0L, PetrificationRules.ticksPetrified(100L, 50L));
        assertEquals(60L, PetrificationRules.ticksPetrified(100L, 160L));
    }

    @Test
    void onlyKnownLivingNonBossesComeBack() {
        assertTrue(PetrificationRules.canRevive(true, true, false));
        assertFalse(PetrificationRules.canRevive(false, true, false));
        assertFalse(PetrificationRules.canRevive(true, false, false));
        assertFalse(PetrificationRules.canRevive(true, true, true));
    }

    @Test
    void givingInNeedsALivingStatue() {
        assertTrue(PetrificationRules.giveInAccepted(true, true));
        assertFalse(PetrificationRules.giveInAccepted(false, true));
        assertFalse(PetrificationRules.giveInAccepted(true, false));
    }

    @Test
    void theWardLastsEightSeconds() {
        assertEquals(1160L, PetrificationRules.wardUntil(1000L));
        assertTrue(PetrificationRules.warded(1160L, 1159L));
        assertFalse(PetrificationRules.warded(1160L, 1160L));
        assertFalse(PetrificationRules.warded(0L, 5L));
    }

    @Test
    void aStatueKeepsWhoTheMobWas() {
        for (String key : List.of("Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "FallDistance",
                "Motion", "Passengers", "leash", "SleepingX", "SleepingY", "SleepingZ", "TicksFrozen",
                "HasVisualFire")) {
            assertTrue(PetrificationRules.STRIPPED_MOB_KEYS.contains(key), key);
        }
        for (String key : List.of("UUID", "CustomName", "Owner", "Pos", "Rotation", "HandItems", "ArmorItems",
                "Inventory", "Offers", "Tags", "active_effects", "attributes", "Brain")) {
            assertFalse(PetrificationRules.STRIPPED_MOB_KEYS.contains(key), key);
        }
    }

    @Test
    void eachPlayerIsCheckedOnceASecond() {
        int[] ids = {0, 7, 19, 12345, -3};
        for (int id : ids) {
            int due = 0;
            for (long t = 1000L; t < 1020L; t++) {
                if (PetrificationRules.dueCheck(t, id)) {
                    due++;
                    assertTrue(PetrificationRules.dueCheck(t + 20L, id));
                }
            }
            assertEquals(1, due, "id " + id);
        }
    }
}
```

- [ ] **Step 2: Run it and see it fail.** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*PetrificationRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... PetrificationRules`).

- [ ] **Step 3: Write `PetrificationRules`.** Create `src/main/java/com/hogwartsmagic/petrification/PetrificationRules.java`:

```java
package com.hogwartsmagic.petrification;

import com.hogwartsmagic.brewing.PotionRules;

import java.util.List;

/**
 * Canon petrification, free of Minecraft so it is unit-tested: who the full gaze turns to stone, which damage still
 * reaches stone, the optional time limit, the Mandrake ward, which statues can come back and what a statue forgets of
 * its mob. Petrification and PetrificationEvents apply these rules in the world.
 */
public final class PetrificationRules {
    /** What the Basilisk's full gaze does to a victim. */
    public enum GazeOutcome {
        /** A survival or adventure player: stone, with the choice to wait or give in. */
        PETRIFY,
        /**
         * Everyone else, bosses included: the old lethal rule (a mob leaves a statue). For creative and spectator
         * players this path is harmless: the magic damage does not bypass invulnerability.
         */
        KILL
    }

    /** Under PlayerPersisted/hogwartsmagic: the player is stone. */
    public static final String KEY_PETRIFIED = "Petrified";
    /** Under PlayerPersisted/hogwartsmagic: the overworld game time the stone began. */
    public static final String KEY_SINCE = "PetrifiedSince";
    /** Entity persistent data: the game time until which the gaze cannot build (a Mandrake Draught drink). */
    public static final String WARD_KEY = "hogwartsmagic:gaze_ward_until";
    public static final String DAMAGE_TYPE = "hogwartsmagic:petrification";
    public static final String VOID_DAMAGE = "minecraft:out_of_world";
    public static final String KILL_DAMAGE = "minecraft:generic_kill";
    public static final int DEFAULT_MAX_SECONDS = 0;
    public static final int MAX_MAX_SECONDS = 86400;
    /** Mobs this close that still target a stone player lose the target. */
    public static final int TARGET_CLEAR_RADIUS = 32;
    public static final int CHECK_INTERVAL_TICKS = 20;
    /**
     * What a statue drops from the mob's save: the death itself and anything that would duplicate or misplace it.
     * active_effects is kept on purpose: effects add permanent attribute modifiers saved in attributes, so the revive
     * loads the effects and calls removeAllEffects() to let vanilla take those modifiers off. attributes and Brain are
     * kept.
     */
    public static final List<String> STRIPPED_MOB_KEYS = List.of("Health", "DeathTime", "HurtTime",
            "HurtByTimestamp", "Fire", "FallDistance", "Motion", "Passengers", "leash", "SleepingX", "SleepingY",
            "SleepingZ", "TicksFrozen", "HasVisualFire");

    private PetrificationRules() {
    }

    public static GazeOutcome gazeOutcome(boolean player, boolean creativeOrSpectator) {
        return player && !creativeOrSpectator ? GazeOutcome.PETRIFY : GazeOutcome.KILL;
    }

    /** Stone takes no damage except the void, /kill and giving in. */
    public static boolean damagePasses(String damageTypeId) {
        return VOID_DAMAGE.equals(damageTypeId) || KILL_DAMAGE.equals(damageTypeId) || DAMAGE_TYPE.equals(damageTypeId);
    }

    /** The configured limit (0 or less: none) has run out. Game time, so time logged out counts. */
    public static boolean limitReached(long since, long now, int maxSeconds) {
        return maxSeconds > 0 && now - since >= maxSeconds * 20L;
    }

    public static long ticksPetrified(long since, long now) {
        return Math.max(0L, now - since);
    }

    /** A statue comes back only as a known, living, non-boss type. */
    public static boolean canRevive(boolean typeKnown, boolean living, boolean boss) {
        return typeKnown && living && !boss;
    }

    /** The GiveInPacket is acted on only for a player who is stone and alive. */
    public static boolean giveInAccepted(boolean petrified, boolean alive) {
        return petrified && alive;
    }

    public static long wardUntil(long now) {
        return now + PotionRules.MANDRAKE_GAZE_WARD_TICKS;
    }

    public static boolean warded(long wardUntil, long now) {
        return now < wardUntil;
    }

    /** Once every CHECK_INTERVAL_TICKS per entity, phased by its id. */
    public static boolean dueCheck(long gameTime, int entityId) {
        return Math.floorMod(gameTime + entityId, (long) CHECK_INTERVAL_TICKS) == 0L;
    }
}
```

- [ ] **Step 4: Run it and see it pass.** Same command as Step 2: 11 tests pass. Then the full `compileJava test`: BASE + 25, all green.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Petrification rules: stone state, damage filter, limit, ward, revive

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/petrification/PetrificationRules.java src/test/java/com/hogwartsmagic/petrification/PetrificationRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/petrification/PetrificationRules.java src/test/java/com/hogwartsmagic/petrification/PetrificationRulesTest.java
```

---

### Task 3: The five ingredients and the dittany plant

**Files:**
- Create: `src/main/java/com/hogwartsmagic/block/DittanyPlantBlock.java`
- Create: `src/main/java/com/hogwartsmagic/worldgen/DittanyPatchFeature.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Create: `build_scripts/generate_brewing_ingredient_textures.py`
- Create (generated): `src/main/resources/assets/hogwartsmagic/textures/item/{dittany,asphodel,wormwood,valerian_sprigs,ashwinder_egg}.png`, `textures/block/dittany_plant.png`
- Create: `src/main/resources/assets/hogwartsmagic/blockstates/dittany_plant.json`, `models/block/dittany_plant.json`, `models/item/{dittany,asphodel,wormwood,valerian_sprigs,ashwinder_egg}.json`
- Create: `src/main/resources/data/hogwartsmagic/loot_table/blocks/dittany_plant.json`
- Create: `src/main/resources/data/hogwartsmagic/worldgen/configured_feature/dittany_patch.json`, `worldgen/placed_feature/placed_dittany_patch.json`, `tags/worldgen/biome/has_dittany.json`, `forge/biome_modifier/dittany_in_highlands.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `FEATURES`, `BLOCKS`, `ITEMS` registers in `ExampleMod`; the T1 recipe ids. Produces: `ExampleMod.DITTANY_PLANT`, `DITTANY_PATCH_FEATURE`, `DITTANY`, `ASPHODEL`, `WORMWOOD`, `VALERIAN_SPRIGS`, `ASHWINDER_EGG`; the lang anchor `"brew.hogwartsmagic.felix_felicis": "Felix Felicis",`.

- [ ] **Step 1: The plant.** Create `src/main/java/com/hogwartsmagic/block/DittanyPlantBlock.java`:

```java
package com.hogwartsmagic.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dittany, the woolly mountain herb of Essence of Dittany. It grows on earth and on bare rock (the Horntail Peaks
 * are blackstone, tuff and basalt). Breaking it gives 1 or 2 dittany (loot table); the dittany item plants it again.
 */
public class DittanyPlantBlock extends BushBlock {
    public static final MapCodec<DittanyPlantBlock> CODEC = simpleCodec(DittanyPlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);

    public DittanyPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        return floor.is(BlockTags.DIRT) || floor.is(BlockTags.BASE_STONE_OVERWORLD) || floor.is(BlockTags.BASE_STONE_NETHER);
    }
}
```

- [ ] **Step 2: The patch feature.** Create `src/main/java/com/hogwartsmagic/worldgen/DittanyPatchFeature.java`:

```java
package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** A small patch of dittany (2-4 plants within 5 blocks) on the surface, as MandrakePatchFeature does for mandrakes. */
public class DittanyPatchFeature extends Feature<NoneFeatureConfiguration> {

    public DittanyPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState plant = ExampleMod.DITTANY_PLANT.get().defaultBlockState();
        int target = 2 + random.nextInt(3);
        int placed = 0;
        for (int attempt = 0; attempt < 20 && placed < target; attempt++) {
            int x = origin.getX() + random.nextInt(6) - random.nextInt(6);
            int z = origin.getZ() + random.nextInt(6) - random.nextInt(6);
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isEmptyBlock(pos) || !plant.canSurvive(level, pos)) {
                continue;
            }
            level.setBlock(pos, plant, 2);
            placed++;
        }
        return placed > 0;
    }
}
```

- [ ] **Step 3: Register.** In `src/main/java/com/hogwartsmagic/ExampleMod.java`:

(a) Find:

```java
            FEATURES.register("mandrake_patch", () -> new com.hogwartsmagic.worldgen.MandrakePatchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
```

and replace it with:

```java
            FEATURES.register("mandrake_patch", () -> new com.hogwartsmagic.worldgen.MandrakePatchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration>> DITTANY_PATCH_FEATURE =
            FEATURES.register("dittany_patch", () -> new com.hogwartsmagic.worldgen.DittanyPatchFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));
```

(b) Find:

```java
            () -> new MandrakePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollission().instabreak()));
```

and replace it with:

```java
            () -> new MandrakePlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollission().instabreak()));
    public static final RegistryObject<Block> DITTANY_PLANT = BLOCKS.register("dittany_plant",
            () -> new com.hogwartsmagic.block.DittanyPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).noCollission().instabreak()));
```

(c) Find:

```java
    public static final RegistryObject<Item> GILLYWEED = ITEMS.register("gillyweed",
            () -> new GillyweedItem(new Item.Properties()));
```

and replace it with:

```java
    public static final RegistryObject<Item> GILLYWEED = ITEMS.register("gillyweed",
            () -> new GillyweedItem(new Item.Properties()));
    // Cauldron Brewing Phase 2 ingredients. The dittany item also plants dittany.
    public static final RegistryObject<Item> DITTANY = ITEMS.register("dittany",
            () -> new net.minecraft.world.item.ItemNameBlockItem(DITTANY_PLANT.get(), new Item.Properties()));
    public static final RegistryObject<Item> ASPHODEL = ITEMS.register("asphodel",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WORMWOOD = ITEMS.register("wormwood",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VALERIAN_SPRIGS = ITEMS.register("valerian_sprigs",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ASHWINDER_EGG = ITEMS.register("ashwinder_egg",
            () -> new Item(new Item.Properties().fireResistant()));
```

(d) Find:

```java
                output.accept(GILLYWEED.get());
```

and replace it with:

```java
                output.accept(GILLYWEED.get());
                output.accept(DITTANY.get());
                output.accept(ASPHODEL.get());
                output.accept(WORMWOOD.get());
                output.accept(VALERIAN_SPRIGS.get());
                output.accept(ASHWINDER_EGG.get());
```

- [ ] **Step 4: The texture generator.** Create `build_scripts/generate_brewing_ingredient_textures.py`:

```python
"""Paint the five brewing ingredient icons and the dittany plant (Cauldron Brewing Phase 2).

    python build_scripts/generate_brewing_ingredient_textures.py [--preview]

Writes to assets/hogwartsmagic/textures/:

  item/dittany.png          a sprig of round, woolly grey-green leaves under a pink flower spike
  item/asphodel.png         white star flowers with yellow hearts on a green stalk
  item/wormwood.png         a feathery silver sprig
  item/valerian_sprigs.png  three stems of pink-white flower heads tied with string
  item/ashwinder_egg.png    a glowing red egg with ember specks
  block/dittany_plant.png   the plant as it grows (a cross model): a low woolly bush with pink spikes

STYLE
-----
Hand-placed pixel art: each sheet is a 16x16 grid of palette letters ('.' is transparent), so a
reviewer can read the picture in this file. The palettes are short (three to five colours) like the
mod's other plant icons (mandrake_root, gillyweed). No randomness: the output is byte-stable.

--preview also writes /tmp/brewing_ingredients_preview.png: every sheet at 8x over a dark
checkerboard, side by side, to look at with an image viewer.
"""

import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "hogwartsmagic", "textures")

DITTANY_LEAF = {
    "L": (169, 185, 154),  # woolly leaf, light
    "l": (127, 148, 112),  # leaf, shade
    "s": (94, 107, 62),    # stem
    "p": (217, 138, 184),  # flower
    "P": (168, 80, 127),   # flower, deep
}

DITTANY_ITEM = [
    "................",
    ".........pP.....",
    "........pPpP....",
    ".........pP.....",
    ".....LL..s......",
    "....LllL.s..LL..",
    "....LllLss.LllL.",
    ".....LLs.s.LllL.",
    ".......s.sssLL..",
    "..LL...ss.s.....",
    ".LllL...s.......",
    ".LllLsssss......",
    "..LL....s.LL....",
    "........sLllL...",
    ".......s.LllL...",
    "......s...LL....",
]

DITTANY_PLANT = [
    "................",
    "......pP...pP...",
    ".....pPp..pPp...",
    "......P....P....",
    "...LL..s..sLL...",
    "..LllL.s.s.LllL.",
    "..LllLLs.sLLllL.",
    "...LLlllsLlllL..",
    ".LL.LlllLLllL.LL",
    "LllLsLLLsLLLsLlL",
    "LllLLs.LsL.sLLlL",
    ".LLlLLs.s.sLlLL.",
    "...LlllLsLlllL..",
    "....LLlLsLlLL...",
    "......LLsLL.....",
    ".......sss......",
]

ASPHODEL = (
    {
        "w": (242, 238, 245),  # petal
        "v": (201, 184, 222),  # petal shade
        "y": (232, 201, 74),   # heart
        "g": (110, 138, 74),   # stalk
        "G": (85, 112, 56),    # leaf
    },
    [
        "................",
        ".......w........",
        "......wyw..w....",
        ".......v..wyw...",
        "....w..g...v....",
        "...wyw.g..g.....",
        "....v.g.gg......",
        ".....g..g.......",
        ".....g.g..w.....",
        "......gg.wyw....",
        "......g...v.....",
        ".....Gg..g......",
        "....GG.gg.......",
        "...GG..g........",
        "......g.........",
        ".....g..........",
    ],
)

WORMWOOD = (
    {
        "S": (197, 204, 192),  # silver leaf
        "s": (152, 163, 148),  # leaf shade
        "t": (122, 132, 98),   # stem
    },
    [
        "................",
        "..........S.....",
        ".........SsS....",
        ".....S..S.t.S...",
        "....SsS..tsSs...",
        "...S.t.S.t..S...",
        "..SsS.tSt.S.....",
        "...S..tt.SsS....",
        ".......tSt.S....",
        "....SsStt.......",
        "...S.S.t.S......",
        "......t.SsS.....",
        ".....tSt.S......",
        "....t.S.........",
        "...t............",
        "..t.............",
    ],
)

VALERIAN = (
    {
        "p": (240, 214, 228),  # flower, pale
        "P": (217, 154, 186),  # flower, pink
        "g": (94, 138, 62),    # stem
        "k": (176, 138, 90),   # string
    },
    [
        "................",
        "...pPp...pPp....",
        "..pPpPp.pPpPp...",
        "...pPp.p.pPp....",
        "....g.pPp.g.....",
        "....g..pg.g.....",
        ".....g.g.g......",
        ".....g.g.g......",
        "......ggg.......",
        "......kkk.......",
        "......ggg.......",
        ".....g.g.g......",
        ".....g.g.g......",
        "....g..g..g.....",
        "....g..g..g.....",
        "................",
    ],
)

ASHWINDER_EGG = (
    {
        "a": (90, 20, 8),      # outline
        "b": (200, 48, 26),    # shell
        "c": (255, 154, 90),   # highlight
        "d": (255, 194, 58),   # ember speck
        "e": (142, 32, 16),    # shade
    },
    [
        "................",
        "................",
        "......aaaa......",
        ".....abbbba.....",
        "....abccbbba....",
        "....abcbbbba....",
        "...abbbbdbbba...",
        "...abbbbbbbba...",
        "...abdbbbbbba...",
        "...abbbbbbdba...",
        "...aebbbbbbea...",
        "....aebbdbea....",
        "....aeebbeea....",
        ".....aeeeea.....",
        "......aaaa......",
        "................",
    ],
)

SHEETS = [
    ("item/dittany", DITTANY_LEAF, DITTANY_ITEM),
    ("item/asphodel", ASPHODEL[0], ASPHODEL[1]),
    ("item/wormwood", WORMWOOD[0], WORMWOOD[1]),
    ("item/valerian_sprigs", VALERIAN[0], VALERIAN[1]),
    ("item/ashwinder_egg", ASHWINDER_EGG[0], ASHWINDER_EGG[1]),
    ("block/dittany_plant", DITTANY_LEAF, DITTANY_PLANT),
]


def paint(name, palette, grid):
    if len(grid) != 16 or any(len(row) != 16 for row in grid):
        raise ValueError(name + ": every sheet is 16 rows of 16 characters")
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, char in enumerate(row):
            if char == ".":
                continue
            if char not in palette:
                raise ValueError(name + ": unknown palette letter " + repr(char))
            image.putpixel((x, y), palette[char] + (255,))
    return image


def preview(images, path, scale=8):
    width = 16 * scale * len(images) + 8 * (len(images) + 1)
    height = 16 * scale + 16
    sheet = Image.new("RGBA", (width, height), (60, 60, 60, 255))
    for y in range(height):
        for x in range(width):
            if ((x // 8) + (y // 8)) % 2:
                sheet.putpixel((x, y), (84, 84, 84, 255))
    left = 8
    for image in images:
        big = image.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(big, (left, 8))
        left += 16 * scale + 8
    sheet.save(path)


def main():
    images = []
    for name, palette, grid in SHEETS:
        image = paint(name, palette, grid)
        out = os.path.join(ROOT, name + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        image.save(out)
        images.append(image)
        print("wrote " + os.path.normpath(out))
    if "--preview" in sys.argv[1:]:
        preview(images, "/tmp/brewing_ingredients_preview.png")
        print("wrote /tmp/brewing_ingredients_preview.png")


if __name__ == "__main__":
    main()
```

Run `cd /home/user/minecraft_mods && python3 build_scripts/generate_brewing_ingredient_textures.py --preview`. Expected: seven `wrote ...` lines. Look at `/tmp/brewing_ingredients_preview.png` with the Read tool: six recognisable 16x16 sprites (sprig, white flowers, silver sprig, tied pink sprigs, red egg, bush). Run it a second time and check `git status --short src/main/resources/assets/hogwartsmagic/textures` shows the same six new files (byte-stable).

- [ ] **Step 5: Models and blockstate.**

`src/main/resources/assets/hogwartsmagic/blockstates/dittany_plant.json`:

```json
{
  "variants": {
    "": { "model": "hogwartsmagic:block/dittany_plant" }
  }
}
```

`src/main/resources/assets/hogwartsmagic/models/block/dittany_plant.json`:

```json
{
  "parent": "minecraft:block/cross",
  "render_type": "minecraft:cutout",
  "textures": {
    "cross": "hogwartsmagic:block/dittany_plant"
  }
}
```

Five item models, each `src/main/resources/assets/hogwartsmagic/models/item/<name>.json` for `<name>` in `dittany`, `asphodel`, `wormwood`, `valerian_sprigs`, `ashwinder_egg`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hogwartsmagic:item/<name>"
  }
}
```

(with `<name>` replaced, for example `"layer0": "hogwartsmagic:item/valerian_sprigs"`).

- [ ] **Step 6: Loot and worldgen data.**

`src/main/resources/data/hogwartsmagic/loot_table/blocks/dittany_plant.json`:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "hogwartsmagic:dittany",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "type": "minecraft:uniform", "min": 1, "max": 2 }
            }
          ]
        }
      ],
      "conditions": [
        { "condition": "minecraft:survives_explosion" }
      ]
    }
  ]
}
```

`src/main/resources/data/hogwartsmagic/worldgen/configured_feature/dittany_patch.json`:

```json
{
  "type": "hogwartsmagic:dittany_patch",
  "config": {}
}
```

`src/main/resources/data/hogwartsmagic/worldgen/placed_feature/placed_dittany_patch.json`:

```json
{
  "feature": "hogwartsmagic:dittany_patch",
  "placement": [
    { "type": "minecraft:count", "count": 1 },
    { "type": "minecraft:rarity_filter", "chance": 6 },
    { "type": "minecraft:in_square" },
    { "type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG" },
    { "type": "minecraft:biome" }
  ]
}
```

`src/main/resources/data/hogwartsmagic/tags/worldgen/biome/has_dittany.json`:

```json
{
  "replace": false,
  "values": [
    { "id": "hogwartsmagic:forbidden_forest", "required": false },
    { "id": "hogwartsmagic:horntail_peaks", "required": false }
  ]
}
```

`src/main/resources/data/hogwartsmagic/forge/biome_modifier/dittany_in_highlands.json`:

```json
{
  "type": "forge:add_features",
  "biomes": "#hogwartsmagic:has_dittany",
  "features": "hogwartsmagic:placed_dittany_patch",
  "step": "vegetal_decoration"
}
```

- [ ] **Step 7: Lang.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find:

```json
  "brew.hogwartsmagic.polyjuice": "Polyjuice Potion",
```

and replace it with:

```json
  "brew.hogwartsmagic.polyjuice": "Polyjuice Potion",
  "block.hogwartsmagic.dittany_plant": "Dittany",
  "item.hogwartsmagic.dittany": "Dittany",
  "item.hogwartsmagic.asphodel": "Asphodel",
  "item.hogwartsmagic.wormwood": "Wormwood",
  "item.hogwartsmagic.valerian_sprigs": "Valerian Sprigs",
  "item.hogwartsmagic.ashwinder_egg": "Ashwinder Egg",
  "brew.hogwartsmagic.dittany": "Essence of Dittany",
  "brew.hogwartsmagic.mandrake_draught": "Mandrake Restorative Draught",
  "brew.hogwartsmagic.living_death": "Draught of Living Death",
  "brew.hogwartsmagic.felix_felicis": "Felix Felicis",
```

- [ ] **Step 8: Check and build.**
  - Validate every JSON file of this task: `cd /home/user/minecraft_mods && python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/resources/assets/hogwartsmagic/blockstates/dittany_plant.json src/main/resources/assets/hogwartsmagic/models/block/dittany_plant.json src/main/resources/assets/hogwartsmagic/models/item/dittany.json src/main/resources/assets/hogwartsmagic/models/item/asphodel.json src/main/resources/assets/hogwartsmagic/models/item/wormwood.json src/main/resources/assets/hogwartsmagic/models/item/valerian_sprigs.json src/main/resources/assets/hogwartsmagic/models/item/ashwinder_egg.json src/main/resources/data/hogwartsmagic/loot_table/blocks/dittany_plant.json src/main/resources/data/hogwartsmagic/worldgen/configured_feature/dittany_patch.json src/main/resources/data/hogwartsmagic/worldgen/placed_feature/placed_dittany_patch.json src/main/resources/data/hogwartsmagic/tags/worldgen/biome/has_dittany.json src/main/resources/data/hogwartsmagic/forge/biome_modifier/dittany_in_highlands.json`. Expected: no output.
  - Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25.
  - The boot block. Expected: `Done (`; no registry, biome modifier, feature or model errors.

- [ ] **Step 9: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Brewing ingredients: dittany plant and patch, asphodel, wormwood, valerian, ashwinder egg

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
NEW="src/main/java/com/hogwartsmagic/block/DittanyPlantBlock.java src/main/java/com/hogwartsmagic/worldgen/DittanyPatchFeature.java build_scripts/generate_brewing_ingredient_textures.py src/main/resources/assets/hogwartsmagic/textures/item/dittany.png src/main/resources/assets/hogwartsmagic/textures/item/asphodel.png src/main/resources/assets/hogwartsmagic/textures/item/wormwood.png src/main/resources/assets/hogwartsmagic/textures/item/valerian_sprigs.png src/main/resources/assets/hogwartsmagic/textures/item/ashwinder_egg.png src/main/resources/assets/hogwartsmagic/textures/block/dittany_plant.png src/main/resources/assets/hogwartsmagic/blockstates/dittany_plant.json src/main/resources/assets/hogwartsmagic/models/block/dittany_plant.json src/main/resources/assets/hogwartsmagic/models/item/dittany.json src/main/resources/assets/hogwartsmagic/models/item/asphodel.json src/main/resources/assets/hogwartsmagic/models/item/wormwood.json src/main/resources/assets/hogwartsmagic/models/item/valerian_sprigs.json src/main/resources/assets/hogwartsmagic/models/item/ashwinder_egg.json src/main/resources/data/hogwartsmagic/loot_table/blocks/dittany_plant.json src/main/resources/data/hogwartsmagic/worldgen/configured_feature/dittany_patch.json src/main/resources/data/hogwartsmagic/worldgen/placed_feature/placed_dittany_patch.json src/main/resources/data/hogwartsmagic/tags/worldgen/biome/has_dittany.json src/main/resources/data/hogwartsmagic/forge/biome_modifier/dittany_in_highlands.json"
git add $NEW
git commit -F /tmp/msg.txt -- $NEW src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 4: Where the ingredients come from (trades, chests, the Blaze)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/entity/ModVillagers.java`
- Create: `src/main/java/com/hogwartsmagic/loot/BrewingIngredientLootModifier.java`
- Create: `src/main/resources/data/hogwartsmagic/loot_modifiers/brewing_ingredients_in_chests.json`
- Modify: `src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Create: `src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java`

**Interfaces.** Consumes: `PotionRules` chances and `rolls` (T1); the T3 items. Produces: `ExampleMod.BREWING_INGREDIENT_LOOT_MODIFIER`.

- [ ] **Step 1: Trades.** In `src/main/java/com/hogwartsmagic/entity/ModVillagers.java`, find:

```java
            sells(event, 1, 5, 3, some(ExampleMod.GILLYWEED, 2), 12, 2);
```

and replace it with:

```java
            sells(event, 1, 5, 3, some(ExampleMod.GILLYWEED, 2), 12, 2);
            sells(event, 1, 6, 1, some(ExampleMod.DITTANY, 2), 12, 2);
            sells(event, 1, 4, 1, some(ExampleMod.VALERIAN_SPRIGS, 2), 12, 2);
```

Find:

```java
            sells(event, 2, 9, 4, one(ExampleMod.MOONLIT_PHIAL), 8, 5);
```

and replace it with:

```java
            sells(event, 2, 9, 4, one(ExampleMod.MOONLIT_PHIAL), 8, 5);
            sells(event, 2, 8, 1, one(ExampleMod.ASPHODEL), 12, 5);
            sells(event, 2, 6, 1, some(ExampleMod.WORMWOOD, 2), 12, 5);
```

Find:

```java
 * apothecaries are the only source of bottled ingredients, Quality Quidditch is the only place to
```

and replace it with:

```java
 * apothecaries are the only shop for brewing ingredients, Quality Quidditch is the only place to
```

- [ ] **Step 2: The loot modifier.** Create `src/main/java/com/hogwartsmagic/loot/BrewingIngredientLootModifier.java`:

```java
package com.hogwartsmagic.loot;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.PotionRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Brewing ingredients in mod structure chests. The Death Eater camp fills its chests from the vanilla pillager
 * outpost table and the Hogwarts ruin camp from the vanilla simple dungeon table, so those two only count when the
 * chest stands inside the camp (the structure references of the chest's own, loaded chunk). The Chamber has its own
 * table.
 */
public class BrewingIngredientLootModifier extends LootModifier {
    private static final ResourceLocation SIMPLE_DUNGEON = ResourceLocation.parse("minecraft:chests/simple_dungeon");
    private static final ResourceLocation PILLAGER_OUTPOST = ResourceLocation.parse("minecraft:chests/pillager_outpost");
    private static final ResourceLocation CHAMBER = ResourceLocation.parse("hogwartsmagic:chests/chamber_of_secrets");
    private static final ResourceKey<Structure> HOGWARTS_RUIN_CAMP = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "hogwarts_ruin_camp"));
    private static final ResourceKey<Structure> DEATH_EATER_CAMP = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "death_eater_camp"));

    public static final MapCodec<BrewingIngredientLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst).apply(inst, BrewingIngredientLootModifier::new));

    protected BrewingIngredientLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null) {
            return generatedLoot;
        }
        RandomSource random = context.getRandom();
        if (CHAMBER.equals(tableId)) {
            if (PotionRules.rolls(random.nextDouble(), PotionRules.ASHWINDER_CHAMBER_CHANCE)) {
                generatedLoot.add(new ItemStack(ExampleMod.ASHWINDER_EGG.get()));
            }
            return generatedLoot;
        }
        boolean dungeon = SIMPLE_DUNGEON.equals(tableId);
        boolean outpost = PILLAGER_OUTPOST.equals(tableId);
        if (!dungeon && !outpost) {
            return generatedLoot;
        }
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) {
            return generatedLoot;
        }
        ServerLevel level = context.getLevel();
        BlockPos pos = BlockPos.containing(origin);
        if (dungeon && inStructure(level, pos, HOGWARTS_RUIN_CAMP)
                && PotionRules.rolls(random.nextDouble(), PotionRules.VALERIAN_RUIN_CAMP_CHANCE)) {
            generatedLoot.add(new ItemStack(ExampleMod.VALERIAN_SPRIGS.get(), 1 + random.nextInt(2)));
        }
        if (outpost && inStructure(level, pos, DEATH_EATER_CAMP)
                && PotionRules.rolls(random.nextDouble(), PotionRules.ASHWINDER_DEATH_EATER_CAMP_CHANCE)) {
            generatedLoot.add(new ItemStack(ExampleMod.ASHWINDER_EGG.get()));
        }
        return generatedLoot;
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, ResourceKey<Structure> key) {
        return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(key)).isValid();
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
```

In `src/main/java/com/hogwartsmagic/ExampleMod.java`, find:

```java
            LOOT_MODIFIER_SERIALIZERS.register("tomes_of_mastery", () -> com.hogwartsmagic.loot.TomeOfMasteryLootModifier.CODEC);
```

and replace it with:

```java
            LOOT_MODIFIER_SERIALIZERS.register("tomes_of_mastery", () -> com.hogwartsmagic.loot.TomeOfMasteryLootModifier.CODEC);
    public static final RegistryObject<com.mojang.serialization.MapCodec<com.hogwartsmagic.loot.BrewingIngredientLootModifier>> BREWING_INGREDIENT_LOOT_MODIFIER =
            LOOT_MODIFIER_SERIALIZERS.register("brewing_ingredients_chest", () -> com.hogwartsmagic.loot.BrewingIngredientLootModifier.CODEC);
```

Create `src/main/resources/data/hogwartsmagic/loot_modifiers/brewing_ingredients_in_chests.json`:

```json
{
  "type": "hogwartsmagic:brewing_ingredients_chest",
  "conditions": []
}
```

In `src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json`, add `"hogwartsmagic:brewing_ingredients_in_chests"` as a new last entry of the `entries` array (mind the comma on the entry before it). At 996f638 that is: find

```json
    "hogwartsmagic:tomes_of_mastery_in_chests"
```

and replace it with:

```json
    "hogwartsmagic:tomes_of_mastery_in_chests",
    "hogwartsmagic:brewing_ingredients_in_chests"
```

- [ ] **Step 3: The Blaze egg.** Create `src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java`:

```java
package com.hogwartsmagic.brewing;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A Blaze now and then drops an ashwinder egg (any kill, no looting bonus). */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IngredientDrops {
    private IngredientDrops() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead.getType() != EntityType.BLAZE) {
            return;
        }
        if (PotionRules.rolls(dead.getRandom().nextDouble(), PotionRules.ASHWINDER_BLAZE_CHANCE)) {
            event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(),
                    new ItemStack(ExampleMod.ASHWINDER_EGG.get())));
        }
    }
}
```

- [ ] **Step 4: Check and build.**
  - `cd /home/user/minecraft_mods && python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/data/hogwartsmagic/loot_modifiers/brewing_ingredients_in_chests.json src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json`. Expected: no output.
  - `grep -c "brewing_ingredients_in_chests" src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json` prints `1`.
  - Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Brewing ingredients: apothecary trades, camp and Chamber chest loot, Blaze egg drop

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/loot/BrewingIngredientLootModifier.java src/main/resources/data/hogwartsmagic/loot_modifiers/brewing_ingredients_in_chests.json src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/entity/ModVillagers.java src/main/java/com/hogwartsmagic/loot/BrewingIngredientLootModifier.java src/main/resources/data/hogwartsmagic/loot_modifiers/brewing_ingredients_in_chests.json src/main/resources/data/forge/loot_modifiers/global_loot_modifiers.json src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java
```

---

### Task 5: Essence of Dittany and Felix Felicis

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Modify: `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Create: `src/main/resources/assets/hogwartsmagic/models/item/essence_of_dittany.json`, `models/item/felix_felicis.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `BrewRules.DITTANY_ESSENCE`, `FELIX_FELICIS`, `PotionRules` (T1); `CombatEffects.cureCursedWound(LivingEntity)`. Produces: `ExampleMod.ESSENCE_OF_DITTANY`, `ExampleMod.FELIX_FELICIS`, `BrewedPotionItem.Kind.DITTANY`, `Kind.FELIX`.

- [ ] **Step 1: The kinds.** In `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`, find:

```java
        FAILED("failed_potion", 0x6B5A45);
```

and replace it with:

```java
        FAILED("failed_potion", 0x6B5A45),
        DITTANY("essence_of_dittany", 0xC9E3A6),
        FELIX("felix_felicis", 0xFFD966);
```

- [ ] **Step 2: The drinks.** In the same file, in `applyDrink`, find:

```java
        } else {
            drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FAILED_NAUSEA_TICKS, 0));
```

and replace it with:

```java
        } else if (kind == Kind.DITTANY) {
            // The only removal path for Cursed Wound that the drink guard lets through.
            com.hogwartsmagic.spell.combat.CombatEffects.cureCursedWound(drinker);
            drinker.heal(com.hogwartsmagic.brewing.PotionRules.DITTANY_HEAL);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, drinker.getX(), drinker.getY(0.5), drinker.getZ(),
                        8, 0.3, 0.4, 0.3, 0.0);
            }
        } else if (kind == Kind.FELIX) {
            drinker.addEffect(new MobEffectInstance(MobEffects.LUCK, com.hogwartsmagic.brewing.PotionRules.FELIX_TICKS,
                    com.hogwartsmagic.brewing.PotionRules.FELIX_LUCK_AMPLIFIER));
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.WAX_ON, drinker.getX(), drinker.getY(0.6), drinker.getZ(),
                        16, 0.35, 0.5, 0.35, 0.02);
                server.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 1.0f, 1.6f);
            }
            if (drinker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.felix.drink"), true);
            }
        } else {
            drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FAILED_NAUSEA_TICKS, 0));
```

(`ServerLevel`, `ParticleTypes`, `SoundEvents`, `SoundSource`, `Component` and `Player` are already imported there.)

- [ ] **Step 3: Register.** In `src/main/java/com/hogwartsmagic/ExampleMod.java`, find:

```java
    public static final RegistryObject<Item> FAILED_POTION = ITEMS.register("failed_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FAILED));
```

and replace it with:

```java
    public static final RegistryObject<Item> FAILED_POTION = ITEMS.register("failed_potion",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FAILED));
    public static final RegistryObject<Item> ESSENCE_OF_DITTANY = ITEMS.register("essence_of_dittany",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties(),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.DITTANY));
    public static final RegistryObject<Item> FELIX_FELICIS = ITEMS.register("felix_felicis",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FELIX));
```

Find:

```java
                output.accept(FAILED_POTION.get());
```

and replace it with:

```java
                output.accept(FAILED_POTION.get());
                output.accept(ESSENCE_OF_DITTANY.get());
                output.accept(FELIX_FELICIS.get());
```

- [ ] **Step 4: Bottling.** In `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`, method `bottledPotion`, find:

```java
        if (BrewRules.POLYJUICE.id().equals(id) && !polyjuiceTarget.isEmpty()) {
```

and replace it with:

```java
        if (BrewRules.DITTANY_ESSENCE.id().equals(id)) {
            return new ItemStack(ExampleMod.ESSENCE_OF_DITTANY.get());
        }
        if (BrewRules.FELIX_FELICIS.id().equals(id)) {
            return new ItemStack(ExampleMod.FELIX_FELICIS.get());
        }
        if (BrewRules.POLYJUICE.id().equals(id) && !polyjuiceTarget.isEmpty()) {
```

(Felix gives 1 dose with no other change: `finish` already sets `doses = recipe.doses()`.)

- [ ] **Step 5: Felix XP.** In `src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java`, method `grant`, find:

```java
        float xp = MasteryMath.scaledXp(MasteryMath.xpPerCast(spell.getCooldownTicks()),
                multiplier * Config.getMasteryXpMultiplier());
```

and replace it with:

```java
        // Felix Felicis (Luck III or more): +25% mastery XP.
        net.minecraft.world.effect.MobEffectInstance luck = caster.getEffect(net.minecraft.world.effect.MobEffects.LUCK);
        double felix = com.hogwartsmagic.brewing.PotionRules.felixXpMultiplier(luck == null ? -1 : luck.getAmplifier());
        float xp = MasteryMath.scaledXp(MasteryMath.xpPerCast(spell.getCooldownTicks()),
                multiplier * Config.getMasteryXpMultiplier() * felix);
```

- [ ] **Step 6: Item models and colours.** Create `src/main/resources/assets/hogwartsmagic/models/item/essence_of_dittany.json` and `felix_felicis.json`, both:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/potion_overlay",
    "layer1": "minecraft:item/potion"
  }
}
```

In `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`, find:

```java
                ExampleMod.WIGGENWELD_POTION.get(), ExampleMod.PEPPERUP_POTION.get(), ExampleMod.FAILED_POTION.get());
```

and replace it with:

```java
                ExampleMod.WIGGENWELD_POTION.get(), ExampleMod.PEPPERUP_POTION.get(), ExampleMod.FAILED_POTION.get(),
                ExampleMod.ESSENCE_OF_DITTANY.get(), ExampleMod.FELIX_FELICIS.get());
```

- [ ] **Step 7: Lang.** In `en_us.json`, find:

```json
  "brew.hogwartsmagic.felix_felicis": "Felix Felicis",
```

and replace it with:

```json
  "brew.hogwartsmagic.felix_felicis": "Felix Felicis",
  "item.hogwartsmagic.essence_of_dittany": "Essence of Dittany",
  "item.hogwartsmagic.essence_of_dittany.tooltip": "Closes a Cursed Wound and heals 4 health.",
  "item.hogwartsmagic.felix_felicis": "Felix Felicis",
  "item.hogwartsmagic.felix_felicis.tooltip": "Liquid luck: 3 minutes of Luck III and a quarter more spell mastery.",
  "message.hogwartsmagic.felix.drink": "Everything seems possible.",
```

- [ ] **Step 8: Check and build.** Validate `en_us.json` and the two models (the JSON command). Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25. `grep -n "felixXpMultiplier" src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java` prints one line.

- [ ] **Step 9: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Essence of Dittany and Felix Felicis: drinks, bottling, Felix mastery bonus

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/resources/assets/hogwartsmagic/models/item/essence_of_dittany.json src/main/resources/assets/hogwartsmagic/models/item/felix_felicis.json
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/spell/mastery/MasteryAwards.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/models/item/essence_of_dittany.json src/main/resources/assets/hogwartsmagic/models/item/felix_felicis.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 6: Player petrification (server) and GiveInPacket

**Files:**
- Create: `src/main/java/com/hogwartsmagic/petrification/Petrification.java`
- Create: `src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java`
- Create: `src/main/resources/data/hogwartsmagic/damage_type/petrification.json`
- Modify: `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`, `no_knockback.json`
- Create: `src/main/resources/data/minecraft/tags/damage_type/bypasses_effects.json`, `bypasses_enchantments.json`, `bypasses_invulnerability.json`, `bypasses_resistance.json`
- Modify: `src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java`
- Modify: `src/main/java/com/hogwartsmagic/Config.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `PetrificationRules` (T2), `PotionRules.CURE_SLOWNESS_TICKS` (T1), `PlayerMagicData.getModData/saveModData`, `ModEvents.interruptActiveCasting(ServerPlayer)`, `AvadaWindup.cancel(ServerPlayer, boolean)`. Produces: the T6 row of the pre-flight table; `Config.getPetrificationMaxSeconds()`.

- [ ] **Step 1: The damage type.** Create `src/main/resources/data/hogwartsmagic/damage_type/petrification.json`:

```json
{
  "exhaustion": 0.0,
  "message_id": "hogwartsmagic.petrification",
  "scaling": "never"
}
```

In `src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json` and in `no_knockback.json`, find:

```json
    "hogwartsmagic:bleeding"
```

and replace it with:

```json
    "hogwartsmagic:bleeding",
    "hogwartsmagic:petrification"
```

Create `bypasses_effects.json`, `bypasses_enchantments.json`, `bypasses_invulnerability.json` and `bypasses_resistance.json` in `src/main/resources/data/minecraft/tags/damage_type/`, each:

```json
{
  "replace": false,
  "values": [
    "hogwartsmagic:petrification"
  ]
}
```

- [ ] **Step 2: Config.** In `src/main/java/com/hogwartsmagic/Config.java`, find:

```java
            .defineInRange("pvpCrowdControlMultiplier", 0.6, 0.0, 1.0);
```

and replace it with:

```java
            .defineInRange("pvpCrowdControlMultiplier", 0.6, 0.0, 1.0);

    public static final ForgeConfigSpec.IntValue PETRIFICATION_MAX_SECONDS = BUILDER
            .comment("Basilisk: a player turned to stone gives in automatically after this many seconds of game time (time logged out counts). 0 = never.")
            .defineInRange("petrificationMaxSeconds", 0, 0, 86400);
```

Find:

```java
    public static double pvpCrowdControlMultiplier = 0.6;
```

and replace it with:

```java
    public static double pvpCrowdControlMultiplier = 0.6;
    public static int petrificationMaxSeconds = 0;
```

Find:

```java
        pvpCrowdControlMultiplier = PVP_CROWD_CONTROL_MULTIPLIER.get();
```

and replace it with:

```java
        pvpCrowdControlMultiplier = PVP_CROWD_CONTROL_MULTIPLIER.get();
        petrificationMaxSeconds = PETRIFICATION_MAX_SECONDS.get();
```

Find:

```java
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }
```

and replace it with:

```java
        return pvpCrowdControlReduction ? pvpCrowdControlMultiplier : 1.0;
    }

    /** Seconds a player may stay stone before giving in automatically; 0 = no limit. */
    public static int getPetrificationMaxSeconds() {
        return petrificationMaxSeconds;
    }
```

- [ ] **Step 3: `Petrification`.** Create `src/main/java/com/hogwartsmagic/petrification/Petrification.java`:

```java
package com.hogwartsmagic.petrification;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.PotionRules;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Canon petrification in the world. A player the Basilisk's full gaze meets is turned to stone: an infinite
 * basilisk_petrification effect, backed by Petrified and PetrifiedSince under PlayerPersisted/hogwartsmagic (the
 * keys are the truth; tick() keeps the effect in line with them). Stone counts as stunned (CombatEffects.isStunned),
 * which locks casting, use, attack and movement; PetrificationEvents keeps damage and targeting off it. A Mandrake
 * Restorative Draught cures it; giving in kills the player with the petrification damage type. Common code: no
 * client classes.
 */
public final class Petrification {
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "petrification"));

    /** Hot path (CombatEffects.isStunned runs for every living entity every tick): resolve the holder once. */
    private static Holder<MobEffect> stoneHolder;

    private Petrification() {
    }

    @Nullable
    public static Holder<MobEffect> stone() {
        Holder<MobEffect> holder = stoneHolder;
        if (holder == null) {
            holder = stoneHolder = ExampleMod.BASILISK_PETRIFICATION.getHolder().orElse(null);
        }
        return holder;
    }

    /**
     * Both sides: lasting stone, an infinite basilisk_petrification effect. The 60-tick effect of a mob the gaze is
     * killing does not count.
     */
    public static boolean isPetrified(LivingEntity entity) {
        Holder<MobEffect> holder = stone();
        if (holder == null) {
            return false;
        }
        MobEffectInstance instance = entity.getEffect(holder);
        return instance != null && instance.isInfiniteDuration();
    }

    /** Server: the persisted key, the source of truth for players. */
    public static boolean hasPetrifiedFlag(ServerPlayer player) {
        return PlayerMagicData.getModData(player).getBoolean(PetrificationRules.KEY_PETRIFIED);
    }

    /** The Basilisk's full gaze meets a survival or adventure player. */
    public static void petrify(ServerPlayer player) {
        CompoundTag data = PlayerMagicData.getModData(player);
        data.putBoolean(PetrificationRules.KEY_PETRIFIED, true);
        data.putLong(PetrificationRules.KEY_SINCE, now(player));
        PlayerMagicData.saveModData(player, data);
        com.hogwartsmagic.spell.mind.AvadaWindup.cancel(player, true);
        com.hogwartsmagic.event.ModEvents.interruptActiveCasting(player);
        player.stopUsingItem();
        applyStone(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_PLACE,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.petrified")
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** Every server tick for every player (PetrificationEvents). */
    public static void tick(ServerPlayer player) {
        if (!hasPetrifiedFlag(player)) {
            Holder<MobEffect> holder = stone();
            if (holder != null && isPetrified(player)) {
                player.removeEffect(holder); // an infinite stone effect the keys do not back
            }
            return;
        }
        if (!player.isAlive()) {
            return;
        }
        if (!isPetrified(player)) {
            applyStone(player); // removed by something that bypassed the stun lock
        }
        player.setAirSupply(player.getMaxAirSupply());
        player.getFoodData().setExhaustion(0.0F);
        long now = now(player);
        if (PetrificationRules.dueCheck(now, player.getId())) {
            clearHunters(player);
            CompoundTag stoneData = PlayerMagicData.getModData(player);
            long since = stoneData.contains(PetrificationRules.KEY_SINCE)
                    ? stoneData.getLong(PetrificationRules.KEY_SINCE) : now; // a missing start counts as now
            if (PetrificationRules.limitReached(since, now, Config.getPetrificationMaxSeconds())) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.time_up")
                        .withStyle(ChatFormatting.GRAY), false);
                giveIn(player);
            }
        }
    }

    /**
     * Ends a player's stone state (a Mandrake Draught). rescuer: who brought them back, or null. Returns false when
     * the player was not stone.
     */
    public static boolean cure(ServerPlayer player, @Nullable Entity rescuer) {
        if (!hasPetrifiedFlag(player) && !isPetrified(player)) {
            return false;
        }
        clearFlag(player);
        Holder<MobEffect> holder = stone();
        if (holder != null) {
            player.removeEffect(holder);
        }
        player.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.DIG_SLOWDOWN);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PotionRules.CURE_SLOWNESS_TICKS, 0));
        stoneBurst(player.serverLevel(), player);
        if (rescuer instanceof ServerPlayer saver && saver != player) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.saved_by",
                    saver.getDisplayName()), false);
            saver.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.you_saved",
                    player.getDisplayName()), false);
        } else {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.freed"), false);
        }
        return true;
    }

    /**
     * The player gives in (GiveInPacket, or the time limit). Checked here, never trusted: only a stone, living
     * player. The keys and the effect go first, so this is a normal death (Horcrux, drops, keepInventory).
     */
    public static void giveIn(ServerPlayer player) {
        if (!PetrificationRules.giveInAccepted(hasPetrifiedFlag(player), player.isAlive())) {
            return;
        }
        clearFlag(player);
        Holder<MobEffect> holder = stone();
        if (holder != null) {
            player.removeEffect(holder);
        }
        player.hurt(damage(player.level()), Float.MAX_VALUE);
    }

    public static void clearFlag(ServerPlayer player) {
        CompoundTag data = PlayerMagicData.getModData(player);
        data.remove(PetrificationRules.KEY_PETRIFIED);
        data.remove(PetrificationRules.KEY_SINCE);
        PlayerMagicData.saveModData(player, data);
    }

    /** "gave in to the stone": bypasses armour, effects, enchantments, resistance, invulnerability and totems. */
    public static DamageSource damage(Level level) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE);
        return new DamageSource(type);
    }

    /** A drink of Mandrake Draught: the gaze's hold is gone, and for 8 seconds it cannot build. */
    public static void wardGaze(LivingEntity entity) {
        entity.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        entity.getPersistentData().putLong(PetrificationRules.WARD_KEY,
                PetrificationRules.wardUntil(entity.level().getGameTime()));
    }

    public static boolean isGazeWarded(LivingEntity entity) {
        return PetrificationRules.warded(entity.getPersistentData().getLong(PetrificationRules.WARD_KEY),
                entity.level().getGameTime());
    }

    /** Stone cracking away: 30 stone particles and a stone break. */
    public static void stoneBurst(ServerLevel level, Entity at) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                at.getX(), at.getY(0.5D), at.getZ(), 30, 0.3D, 0.5D, 0.3D, 0.1D);
        level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void applyStone(ServerPlayer player) {
        Holder<MobEffect> holder = stone();
        if (holder != null) {
            player.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION, 0, false, true, true));
        }
    }

    /** Mobs that already had the stone player as their target let go (new targets are refused in PetrificationEvents). */
    private static void clearHunters(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(PetrificationRules.TARGET_CLEAR_RADIUS), m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }
}
```

Keys rule (binding): `Petrified` and `PetrifiedSince` are always written together (in `petrify`) and removed together (in `clearFlag`). A missing `PetrifiedSince` is treated as the current game time, so a hand-edited or partial save never trips the time limit at once.

- [ ] **Step 4: `PetrificationEvents`.** Create `src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java`:

```java
package com.hogwartsmagic.petrification;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The world around stone: the player tick, no damage, no targeting, keys cleared on another death. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PetrificationEvents {
    private PetrificationEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            Petrification.tick(player);
        }
    }

    /** Stone takes no damage except the void, /kill and giving in. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !Petrification.isPetrified(entity)) {
            return;
        }
        if (!PetrificationRules.damagePasses(typeId(event.getSource()))) {
            event.setCanceled(true);
        }
    }

    /** Mobs, the Basilisk, Dementors and NPC casters never take stone as a new target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (target != null && Petrification.isPetrified(target)) {
            event.setCanceled(true);
        }
    }

    /** A stone player who dies another way (the void, /kill) respawns free. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && Petrification.hasPetrifiedFlag(player)) {
            Petrification.clearFlag(player);
        }
    }

    private static String typeId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("");
    }
}
```

- [ ] **Step 5: The Basilisk.** In `src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java`:

(a) Find:

```java
        return candidate != this && candidate.isAlive() && !(candidate instanceof BasiliskEntity);
```

and replace it with:

```java
        return candidate != this && candidate.isAlive() && !(candidate instanceof BasiliskEntity)
                && !com.hogwartsmagic.petrification.Petrification.isPetrified(candidate);
```

(b) Find:

```java
            boolean meeting = gazeAvailable && isMeetingEyes(candidate);
```

and replace it with:

```java
            // A Mandrake Draught drinker's eyes are steady: the charge drains instead of building.
            boolean meeting = gazeAvailable && isMeetingEyes(candidate)
                    && !com.hogwartsmagic.petrification.Petrification.isGazeWarded(candidate);
```

(c) Find:

```java
                petrifyAndKill(candidate);
```

and replace it with:

```java
                petrify(candidate);
```

(d) Find the whole method:

```java
    private void petrifyAndKill(LivingEntity victim) {
        victim.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        victim.addEffect(new MobEffectInstance(ExampleMod.BASILISK_PETRIFICATION.getHolder().get(),
                PETRIFY_DURATION, 0, false, true, true));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 2, false, false, false));

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    victim.getX(), victim.getY(0.5D), victim.getZ(), 24, 0.3D, 0.6D, 0.3D, 0.01D);
        }
        if (victim.isAlive()) {
            victim.hurt(this.damageSources().magic(), Float.MAX_VALUE);
        }
    }
```

and replace it with:

```java
    /**
     * The full gaze. A survival or adventure player turns to stone and chooses to wait or give in (Petrification).
     * Everyone else keeps the old lethal rule, bosses included: a short petrification, then death (a mob leaves a
     * statue in ModEvents.onLivingDeath).
     */
    private void petrify(LivingEntity victim) {
        victim.removeEffect(ExampleMod.BASILISK_GAZE.getHolder().get());
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ASH,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    victim.getX(), victim.getY(0.5D), victim.getZ(), 24, 0.3D, 0.6D, 0.3D, 0.01D);
        }
        boolean exempt = victim instanceof Player player && (player.isCreative() || player.isSpectator());
        if (victim instanceof ServerPlayer serverPlayer
                && com.hogwartsmagic.petrification.PetrificationRules.gazeOutcome(true, exempt)
                == com.hogwartsmagic.petrification.PetrificationRules.GazeOutcome.PETRIFY) {
            com.hogwartsmagic.petrification.Petrification.petrify(serverPlayer);
            return;
        }
        victim.addEffect(new MobEffectInstance(ExampleMod.BASILISK_PETRIFICATION.getHolder().get(),
                PETRIFY_DURATION, 0, false, true, true));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 2, false, false, false));
        if (victim.isAlive()) {
            victim.hurt(this.damageSources().magic(), Float.MAX_VALUE);
        }
    }
```

(`Player` and `ServerPlayer` are already imported in `BasiliskEntity`.)

- [ ] **Step 6: Stone counts as stunned.** In `src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java`, find (the end of `isStunned` and the start of `isSilenced`):

```java
        return entity.hasEffect(holder);
    }

    public static boolean isSilenced(LivingEntity entity) {
```

and replace it with:

```java
        // Basilisk stone (an infinite basilisk_petrification) locks everything a stun does.
        return entity.hasEffect(holder) || com.hogwartsmagic.petrification.Petrification.isPetrified(entity);
    }

    public static boolean isSilenced(LivingEntity entity) {
```

Find:

```java
        String key = isStunned(player)
                ? "gui." + ExampleMod.MODID + ".combat.cast_blocked_stunned"
```

and replace it with:

```java
        String key = com.hogwartsmagic.petrification.Petrification.isPetrified(player)
                ? "gui." + ExampleMod.MODID + ".combat.cast_blocked_petrified"
                : isStunned(player)
                ? "gui." + ExampleMod.MODID + ".combat.cast_blocked_stunned"
```

- [ ] **Step 7: Others see the stone.** In `src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java`, find:

```java
        return effect == ExampleMod.LEVICORPUS_EFFECT.get() || effect == ExampleMod.PETRIFIED.get();
```

and replace it with:

```java
        // Basilisk stone too: watchers draw the stone tint, and a rescuer's client offers the Mandrake cure.
        return effect == ExampleMod.LEVICORPUS_EFFECT.get() || effect == ExampleMod.PETRIFIED.get()
                || effect == ExampleMod.BASILISK_PETRIFICATION.get();
```

- [ ] **Step 8: `GiveInPacket`.** In `src/main/java/com/hogwartsmagic/network/SpellPackets.java`:

(a) **Protocol:** read the current number in `.networkProtocolVersion(N)` (it is 6 at 996f638; Dark Magic Phase 1 raises it before this task runs) and replace it with N + 1. Write the old and the new number in the task report.

(b) **Registration:** at the end of `register()`, after the last `.add();` of the last `CHANNEL.messageBuilder(...)` chain (the `register()` method ends just above `    public static void syncRelics(ServerPlayer player) {`), insert:

```java
        CHANNEL.messageBuilder(GiveInPacket.class)
                .encoder((p, buf) -> {})
                .decoder(buf -> new GiveInPacket())
                .consumerMainThread(GiveInPacket::handle)
                .add();
```

(c) **The record:** add as the last member of the class, just before the class's final closing brace (the last line of the file):

```java

    /** Client to server: a petrified player gives in. The server checks the stone state itself (Petrification.giveIn). */
    public record GiveInPacket() {
        public static void handle(GiveInPacket p, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.hogwartsmagic.petrification.Petrification.giveIn(player);
            }
        }
    }
```

- [ ] **Step 9: Lang.** In `en_us.json`, find:

```json
  "message.hogwartsmagic.felix.drink": "Everything seems possible.",
```

and replace it with:

```json
  "message.hogwartsmagic.felix.drink": "Everything seems possible.",
  "message.hogwartsmagic.petrification.petrified": "You are turned to stone. Wait for someone with a Mandrake Restorative Draught, or give in.",
  "message.hogwartsmagic.petrification.saved_by": "%s brought you back.",
  "message.hogwartsmagic.petrification.you_saved": "You brought %s back.",
  "message.hogwartsmagic.petrification.freed": "The stone falls away. You can move again.",
  "message.hogwartsmagic.petrification.time_up": "You have been stone too long. You give in.",
  "gui.hogwartsmagic.combat.cast_blocked_petrified": "You are stone and can't cast!",
  "death.attack.hogwartsmagic.petrification": "%1$s gave in to the stone",
```

- [ ] **Step 10: Check and build.**
  - Validate: `en_us.json`, `damage_type/petrification.json` and the six damage type tags (the JSON command).
  - `grep -n "networkProtocolVersion" src/main/java/com/hogwartsmagic/network/SpellPackets.java` shows N + 1; `grep -c "GiveInPacket" src/main/java/com/hogwartsmagic/network/SpellPackets.java` prints `5`.
  - `grep -n "petrifyAndKill" -r src/main/java` prints nothing.
  - Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25.
  - The boot block. Expected: `Done (`; no damage type, tag, config or class-loading errors.

- [ ] **Step 11: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Petrification: the full gaze turns players to stone; give in packet, damage type, limit

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
T=src/main/resources/data/minecraft/tags/damage_type
NEW="src/main/java/com/hogwartsmagic/petrification/Petrification.java src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java src/main/resources/data/hogwartsmagic/damage_type/petrification.json $T/bypasses_effects.json $T/bypasses_enchantments.json $T/bypasses_invulnerability.json $T/bypasses_resistance.json"
git add $NEW
git commit -F /tmp/msg.txt -- $NEW $T/bypasses_armor.json $T/no_knockback.json src/main/java/com/hogwartsmagic/entity/BasiliskEntity.java src/main/java/com/hogwartsmagic/spell/combat/CombatEffects.java src/main/java/com/hogwartsmagic/spell/disable/VisibleEffects.java src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 7: Statues keep the mob; no loot or XP; the revive

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/petrification/Petrification.java` (T6 file)
- Modify: `src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java` (T6 file)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `PetrificationRules.STRIPPED_MOB_KEYS`, `canRevive` (T2); `Petrification.stone()`, `stoneBurst` (T6); `CombatTags.isBoss(Entity)`, `CombatTags.BOSSES`. Produces: `Petrification.captureMob(LivingEntity)`, `isStatueDeath(LivingEntity)`, `reviveStatue(ServerLevel, PetrifiedRemainsEntity, Entity)`; `PetrifiedRemainsEntity.getStoredMob()`.

- [ ] **Step 1: The statue stores the mob.** In `src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java`:

(a) Find:

```java
    public PetrifiedRemainsEntity(EntityType<? extends PetrifiedRemainsEntity> type, Level level) {
```

and replace it with:

```java
    /**
     * The whole mob as it was (saveWithoutId minus the death fields), so a Mandrake Draught brings it back with its
     * name, owner, trades and inventory. Saved with the statue, never synced: the renderer needs only the type.
     * Empty for bosses and for statues from older worlds.
     */
    private CompoundTag storedMob = new CompoundTag();

    public PetrifiedRemainsEntity(EntityType<? extends PetrifiedRemainsEntity> type, Level level) {
```

(b) Find:

```java
        remains.setStoredHeadYaw(victim.getYHeadRot());
        return remains;
```

and replace it with:

```java
        remains.setStoredHeadYaw(victim.getYHeadRot());
        if (!com.hogwartsmagic.spell.combat.CombatTags.isBoss(victim)) {
            remains.storedMob = com.hogwartsmagic.petrification.Petrification.captureMob(victim);
        }
        return remains;
```

(c) Find:

```java
        if (tag.contains("HeadYaw")) {
            this.setStoredHeadYaw(tag.getFloat("HeadYaw"));
        }
    }
```

and replace it with:

```java
        if (tag.contains("HeadYaw")) {
            this.setStoredHeadYaw(tag.getFloat("HeadYaw"));
        }
        this.storedMob = tag.getCompound("StoredMob").copy();
    }
```

(d) Find:

```java
        tag.putFloat("HeadYaw", this.getStoredHeadYaw());
    }
```

and replace it with:

```java
        tag.putFloat("HeadYaw", this.getStoredHeadYaw());
        if (!this.storedMob.isEmpty()) {
            tag.put("StoredMob", this.storedMob.copy());
        }
    }

    /** A copy of the stored mob, empty when there is none. */
    public CompoundTag getStoredMob() {
        return this.storedMob.copy();
    }
```

- [ ] **Step 2: Capture, no drops, revive.** In `src/main/java/com/hogwartsmagic/petrification/Petrification.java` (written in Task 6), find:

```java
    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }
```

and replace it with:

```java
    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    // ---- Statues ---------------------------------------------------------------------------------

    /** The mob's save without the death fields, for its statue. */
    public static CompoundTag captureMob(LivingEntity victim) {
        CompoundTag tag = victim.saveWithoutId(new CompoundTag());
        for (String key : PetrificationRules.STRIPPED_MOB_KEYS) {
            tag.remove(key);
        }
        return tag;
    }

    /** A non-player, non-boss that dies under the gaze's petrification: it became a statue, so no loot and no XP. */
    public static boolean isStatueDeath(LivingEntity entity) {
        Holder<MobEffect> holder = stone();
        return holder != null && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !com.hogwartsmagic.spell.combat.CombatTags.isBoss(entity) && entity.hasEffect(holder);
    }

    /**
     * Brings a statue's mob back where the statue stands, with full health. A statue with no stored mob (an older
     * world) brings back a fresh mob of the type. Bosses and unknown types stay stone. Returns true when it came back.
     */
    public static boolean reviveStatue(ServerLevel level, com.hogwartsmagic.entity.PetrifiedRemainsEntity statue,
                                       @Nullable Entity rescuer) {
        if (statue.isRemoved()) {
            return false;
        }
        net.minecraft.world.entity.EntityType<?> type =
                net.minecraft.world.entity.EntityType.byString(statue.getMimicTypeId()).orElse(null);
        Entity created = type == null ? null : type.create(level);
        boolean boss = type != null && type.is(com.hogwartsmagic.spell.combat.CombatTags.BOSSES);
        if (!PetrificationRules.canRevive(type != null, created instanceof LivingEntity, boss)
                || !(created instanceof LivingEntity mob)) {
            if (created != null) {
                created.discard();
            }
            return false;
        }
        CompoundTag stored = statue.getStoredMob();
        if (!stored.isEmpty()) {
            mob.load(stored);
        }
        mob.removeAllEffects(); // vanilla also takes off the attribute modifiers the stored effects added
        if (level.getEntity(mob.getUUID()) != null) {
            mob.setUUID(net.minecraft.util.Mth.createInsecureUUID(level.getRandom())); // the corpse is still here
        }
        mob.moveTo(statue.getX(), statue.getY(), statue.getZ(), statue.getYRot(), 0.0F);
        mob.setYBodyRot(statue.getStoredBodyYaw());
        mob.setYHeadRot(statue.getStoredHeadYaw());
        mob.deathTime = 0;
        mob.setHealth(mob.getMaxHealth());
        level.addFreshEntity(mob);
        statue.discard();
        stoneBurst(level, mob);
        if (rescuer instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.petrification.statue_revived",
                    mob.getDisplayName()), true);
        }
        return true;
    }
```

- [ ] **Step 3: No loot, no XP.** In `src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java` (Task 6), find:

```java
    private static String typeId(DamageSource source) {
```

and replace it with:

```java
    /** A mob that became a statue drops nothing: bringing it back must not duplicate its items. Bosses keep theirs. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!event.isCanceled() && !event.getEntity().level().isClientSide()
                && Petrification.isStatueDeath(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperienceDrop(net.minecraftforge.event.entity.living.LivingExperienceDropEvent event) {
        if (!event.isCanceled() && !event.getEntity().level().isClientSide()
                && Petrification.isStatueDeath(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static String typeId(DamageSource source) {
```

- [ ] **Step 4: Lang.** In `en_us.json`, find:

```json
  "death.attack.hogwartsmagic.petrification": "%1$s gave in to the stone",
```

and replace it with:

```json
  "death.attack.hogwartsmagic.petrification": "%1$s gave in to the stone",
  "message.hogwartsmagic.petrification.statue_revived": "%s is flesh again.",
```

- [ ] **Step 5: Check and build.** Validate `en_us.json`. Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25. `grep -n "StoredMob" src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java` prints 2 lines; `grep -n "StoredMob\|storedMob" src/main/java/com/hogwartsmagic/client/renderer/PetrifiedRemainsRenderer.java` prints nothing.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Petrified statues keep the whole mob, drop nothing, and can be revived

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java src/main/java/com/hogwartsmagic/petrification/Petrification.java src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 8: The client: choice screen, HUD line, key, look lock

**Files:**
- Create: `src/main/java/com/hogwartsmagic/client/PetrificationClient.java`
- Create: `src/main/java/com/hogwartsmagic/client/PetrifiedChoiceScreen.java`
- Modify: `src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java`
- Modify: `src/main/java/com/hogwartsmagic/mixin/client/BasiliskGazeGuiMixin.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `Petrification.isPetrified(LivingEntity)`, `SpellPackets.GiveInPacket` (T6). Produces: `PetrificationClient.GIVE_IN_KEY`, `blocksTurn(Entity)`, `renderHud(GuiGraphics)`.

- [ ] **Step 1: The screen.** Create `src/main/java/com/hogwartsmagic/client/PetrifiedChoiceScreen.java`:

```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

/**
 * Shown when the local player turns to stone, like the death screen: wait for rescue or give in. It does not pause,
 * Esc means "wait", and the chat key opens chat so the player can call for help.
 */
public class PetrifiedChoiceScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int TITLE_COLOR = 0xC8CCD0;
    private static final int BODY_COLOR = 0xE0E0E0;
    private boolean sent;

    public PetrifiedChoiceScreen() {
        super(Component.translatable("gui.hogwartsmagic.petrified.title"));
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = this.height / 4 + 72;
        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.petrified.wait"), b -> onClose())
                .bounds(x, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hogwartsmagic.petrified.give_in"), b -> giveIn())
                .bounds(x, y + 24, BUTTON_WIDTH, 20).build());
    }

    private void giveIn() {
        if (!sent) {
            sent = true;
            SpellPackets.CHANNEL.send(new SpellPackets.GiveInPacket(), PacketDistributor.SERVER.noArg());
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null && this.minecraft.options.keyChat.matches(keyCode, scanCode)) {
            this.minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Hard no-op: never run the menu-blur post effect for this screen.
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op so super.render() can't draw the blurred backdrop; render() draws the plain dim itself.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.pose().pushPose();
        g.pose().scale(2.0F, 2.0F, 2.0F);
        g.drawCenteredString(this.font, this.title, this.width / 4, this.height / 8, TITLE_COLOR);
        g.pose().popPose();
        g.drawCenteredString(this.font, Component.translatable("gui.hogwartsmagic.petrified.body"),
                this.width / 2, this.height / 4 + 36, BODY_COLOR);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
```

- [ ] **Step 2: `PetrificationClient`.** Create `src/main/java/com/hogwartsmagic/client/PetrificationClient.java`:

```java
package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.petrification.Petrification;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client half of basilisk stone, driven by the stone effect vanilla syncs to the local player. Movement and clicks
 * are already locked by StunInputLock (stone counts as stunned). This adds the look lock (through
 * PossessionTurnMixin), drains the drop, swap-hands and inventory keys, refuses container screens and the spell
 * menus, opens the choice screen when the stone begins (once no other screen is open), closes it when the stone
 * ends, reopens it on the give-in key, and draws the HUD line (through BasiliskGazeGuiMixin).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PetrificationClient {
    /** J by default: G is the Grimoire key. Registered in ClientModEvents.onRegisterKeyMappings. */
    public static final KeyMapping GIVE_IN_KEY = new KeyMapping(
            "key.hogwartsmagic.give_in",
            GLFW.GLFW_KEY_J,
            "key.categories.hogwartsmagic"
    );
    private static final int HUD_COLOR = 0xD0D0D0;

    private static boolean wasPetrified;
    private static boolean openPending;

    private PetrificationClient() {
    }

    public static boolean isLocalPetrified() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Petrification.isPetrified(player);
    }

    /** Called from PossessionTurnMixin: the stone local player's mouse does not turn them. */
    public static boolean blocksTurn(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && entity == player && Petrification.isPetrified(player);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean petrified = isLocalPetrified();
        if (event.phase == TickEvent.Phase.START) {
            if (petrified) {
                drain(minecraft.options.keyDrop);
                drain(minecraft.options.keySwapOffhand);
                drain(minecraft.options.keyInventory);
            }
            return;
        }
        if (petrified && !wasPetrified) {
            openPending = true;
        }
        if (!petrified) {
            openPending = false;
            if (wasPetrified && minecraft.screen instanceof PetrifiedChoiceScreen) {
                minecraft.setScreen(null);
            }
        }
        wasPetrified = petrified;
        while (GIVE_IN_KEY.consumeClick()) {
            if (petrified && minecraft.screen == null) {
                openPending = true;
            }
        }
        if (openPending && minecraft.screen == null && minecraft.player != null) {
            openPending = false;
            minecraft.setScreen(new PetrifiedChoiceScreen());
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isLocalPetrified() && (event.getNewScreen() instanceof AbstractContainerScreen<?>
                || event.getNewScreen() instanceof WandRadialMenuScreen
                || event.getNewScreen() instanceof SpellMenuScreen)) {
            event.setCanceled(true);
        }
    }

    /** The HUD line, drawn while no screen is open. */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || !isLocalPetrified()) {
            return;
        }
        Component line = Component.translatable("gui.hogwartsmagic.petrified.hud", GIVE_IN_KEY.getTranslatedKeyMessage());
        graphics.drawCenteredString(minecraft.font, line, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 24, HUD_COLOR);
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // swallowed: stone does not drop, swap or open its pockets
        }
    }
}
```

- [ ] **Step 3: The two mixins.** In `src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java`, find:

```java
        if (PossessionClient.interceptTurn((Entity) (Object) this, yaw, pitch)) {
```

and replace it with:

```java
        if (com.hogwartsmagic.client.PetrificationClient.blocksTurn((Entity) (Object) this)
                || PossessionClient.interceptTurn((Entity) (Object) this, yaw, pitch)) {
```

In `src/main/java/com/hogwartsmagic/mixin/client/BasiliskGazeGuiMixin.java`, find:

```java
        BasiliskGazeOverlay.render(guiGraphics);
```

and replace it with:

```java
        BasiliskGazeOverlay.render(guiGraphics);
        com.hogwartsmagic.client.PetrificationClient.renderHud(guiGraphics);
```

- [ ] **Step 4: Register the key.** In `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`, method `onRegisterKeyMappings`, find:

```java
        event.register(GRIMOIRE_KEY);
```

and replace it with:

```java
        event.register(GRIMOIRE_KEY);
        event.register(com.hogwartsmagic.client.PetrificationClient.GIVE_IN_KEY);
```

- [ ] **Step 5: Lang.** In `en_us.json`, find:

```json
  "message.hogwartsmagic.petrification.statue_revived": "%s is flesh again.",
```

and replace it with:

```json
  "message.hogwartsmagic.petrification.statue_revived": "%s is flesh again.",
  "key.hogwartsmagic.give_in": "Give in (while petrified)",
  "gui.hogwartsmagic.petrified.title": "Petrified",
  "gui.hogwartsmagic.petrified.body": "You are stone. Someone with a Mandrake Restorative Draught can bring you back.",
  "gui.hogwartsmagic.petrified.wait": "Wait for rescue",
  "gui.hogwartsmagic.petrified.give_in": "Give in",
  "gui.hogwartsmagic.petrified.hud": "You are petrified. Press [%s] to give in.",
```

- [ ] **Step 6: Check and build.** Validate `en_us.json`. Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25. `grep -rn "switch" src/main/java/com/hogwartsmagic/client/PetrificationClient.java src/main/java/com/hogwartsmagic/client/PetrifiedChoiceScreen.java` prints nothing.

- [ ] **Step 7: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Petrification client: choice screen, give in key, HUD line, look and key locks

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/client/PetrificationClient.java src/main/java/com/hogwartsmagic/client/PetrifiedChoiceScreen.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/client/PetrificationClient.java src/main/java/com/hogwartsmagic/client/PetrifiedChoiceScreen.java src/main/java/com/hogwartsmagic/mixin/client/PossessionTurnMixin.java src/main/java/com/hogwartsmagic/mixin/client/BasiliskGazeGuiMixin.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 9: Mandrake Restorative Draught, Draught of Living Death and the thrown brew

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`
- Create: `src/main/java/com/hogwartsmagic/item/SplashBrewItem.java`
- Create: `src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/ModEntities.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java` (T7 file)
- Modify: `src/main/java/com/hogwartsmagic/petrification/Petrification.java` (T6/T7 file)
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Modify: `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`
- Create: `src/main/resources/assets/hogwartsmagic/models/item/mandrake_draught.json`, `splash_mandrake_draught.json`, `draught_of_living_death.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: `Petrification.cure`, `wardGaze`, `isPetrified`, `reviveStatue`, `hasPetrifiedFlag` (T6, T7); `PotionRules.SPLASH_RADIUS`, `inSplash`, `LIVING_DEATH_STUN_TICKS` (T1); `CrowdControl.apply(LivingEntity, Holder<MobEffect>, int, Entity)`; `ExampleMod.STUNNED`. Produces: `ExampleMod.MANDRAKE_DRAUGHT`, `SPLASH_MANDRAKE_DRAUGHT`, `DRAUGHT_OF_LIVING_DEATH`; `ModEntities.THROWN_BREW`; `SplashBrewItem.Kind`; `BrewedPotionItem.useUpDose(Player, InteractionHand, ItemStack)`; `Petrification.splashMandrake(ServerLevel, Entity, AABB, Entity)`.

- [ ] **Step 1: The Mandrake bottle.** In `src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java`:

(a) Find (Task 5 text):

```java
        FELIX("felix_felicis", 0xFFD966);
```

and replace it with:

```java
        FELIX("felix_felicis", 0xFFD966),
        MANDRAKE("mandrake_draught", 0x5FD3C0);
```

(b) Find (Task 5 text):

```java
        } else if (kind == Kind.FELIX) {
```

and replace it with:

```java
        } else if (kind == Kind.MANDRAKE) {
            // A free drinker: the basilisk's hold is gone and cannot build for 8 seconds.
            com.hogwartsmagic.petrification.Petrification.wardGaze(drinker);
            if (drinker instanceof Player player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.mandrake.drink"), true);
            }
        } else if (kind == Kind.FELIX) {
```

(c) Find:

```java
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
```

and replace it with:

```java
    /** A Mandrake Draught used on a player turned to stone brings them back (one dose). */
    @Override
    public net.minecraft.world.InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                                      InteractionHand hand) {
        if (kind != Kind.MANDRAKE || !com.hogwartsmagic.petrification.Petrification.isPetrified(target)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!player.level().isClientSide() && target instanceof ServerPlayer stone
                && com.hogwartsmagic.petrification.Petrification.cure(stone, player)) {
            useUpDose(player, hand, stack);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide());
    }

    /** One dose used on someone else: the bottle comes back (not in creative). */
    public static void useUpDose(Player player, InteractionHand hand, ItemStack stack) {
        if (player.hasInfiniteMaterials()) {
            return;
        }
        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            player.setItemInHand(hand, bottle);
        } else if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
```

(`InteractionHand`, `ServerPlayer`, `Items` and `Player` are already imported there.)

- [ ] **Step 2: The splash item.** Create `src/main/java/com/hogwartsmagic/item/SplashBrewItem.java`:

```java
package com.hogwartsmagic.item;

import com.hogwartsmagic.entity.ThrownBrewEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A cauldron brew made to be thrown, as the vanilla splash potion: the Mandrake Draught with gunpowder, and Living Death. */
public class SplashBrewItem extends Item {
    public enum Kind {
        MANDRAKE("splash_mandrake_draught", 0x5FD3C0),
        LIVING_DEATH("draught_of_living_death", 0xD8E2EA);

        private final String id;
        private final int color;

        Kind(String id, int color) {
            this.id = id;
            this.color = color;
        }

        /** The item registry path. */
        public String id() {
            return id;
        }

        /** RGB tint of the bottle and of the splash particles. */
        public int color() {
            return color;
        }
    }

    private final Kind kind;

    public SplashBrewItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ThrownBrewEntity thrown = new ThrownBrewEntity(level, player);
            thrown.setItem(stack);
            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), -20.0F, 0.5F, 1.0F);
            level.addFreshEntity(thrown);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic." + kind.id() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
```

- [ ] **Step 3: The thrown entity.** Create `src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java`:

```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.brewing.PotionRules;
import com.hogwartsmagic.item.SplashBrewItem;
import com.hogwartsmagic.petrification.Petrification;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/**
 * A thrown cauldron brew. Like the vanilla splash potion it breaks on whatever it hits and reaches 4 blocks, but its
 * effect is the mod's own: the Mandrake Draught frees stone players and statues, Living Death stuns through
 * CrowdControl (diminishing returns, the boss rule, the PvP rule: this entity's owner is the caster).
 */
public class ThrownBrewEntity extends ThrowableItemProjectile {

    public ThrownBrewEntity(EntityType<? extends ThrownBrewEntity> type, Level level) {
        super(type, level);
    }

    public ThrownBrewEntity(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_BREW.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ExampleMod.DRAUGHT_OF_LIVING_DEATH.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05D;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        SplashBrewItem.Kind kind = this.getItem().getItem() instanceof SplashBrewItem brew
                ? brew.kind() : SplashBrewItem.Kind.LIVING_DEATH;
        AABB area = this.getBoundingBox().inflate(PotionRules.SPLASH_RADIUS, 2.0D, PotionRules.SPLASH_RADIUS);
        if (kind == SplashBrewItem.Kind.MANDRAKE) {
            Petrification.splashMandrake(level, this, area, this.getOwner());
        } else {
            splashLivingDeath(level, area);
        }
        level.levelEvent(LevelEvent.PARTICLES_SPELL_POTION_SPLASH, this.blockPosition(), kind.color());
        this.discard();
    }

    private void splashLivingDeath(ServerLevel level, AABB area) {
        Holder<MobEffect> stunned = ExampleMod.STUNNED.getHolder().orElse(null);
        if (stunned == null) {
            return;
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && !Petrification.isPetrified(e) && PotionRules.inSplash(this.distanceToSqr(e)))) {
            CrowdControl.apply(target, stunned, PotionRules.LIVING_DEATH_STUN_TICKS, this);
        }
    }
}
```

In `src/main/java/com/hogwartsmagic/entity/ModEntities.java`, find:

```java
                            .build("petrified_remains"));
```

and replace it with:

```java
                            .build("petrified_remains"));

    public static final RegistryObject<EntityType<ThrownBrewEntity>> THROWN_BREW =
            ENTITY_TYPES.register("thrown_brew",
                    () -> EntityType.Builder.<ThrownBrewEntity>of(ThrownBrewEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("thrown_brew"));
```

- [ ] **Step 4: The Mandrake splash and the statue right-click.** In `src/main/java/com/hogwartsmagic/petrification/Petrification.java`, find (Task 7 text):

```java
    // ---- Statues ---------------------------------------------------------------------------------
```

and replace it with:

```java
    // ---- The Mandrake splash ---------------------------------------------------------------------

    /**
     * A splash of Mandrake Draught at {@code center}: stone players in the sphere are cured (by the thrower), every
     * other living thing gets the drink's ward, then statues in the sphere come back.
     */
    public static void splashMandrake(ServerLevel level, Entity center, net.minecraft.world.phys.AABB area,
                                      @Nullable Entity thrower) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            if (!PotionRules.inSplash(center.distanceToSqr(entity))) {
                continue;
            }
            if (entity instanceof ServerPlayer player && (hasPetrifiedFlag(player) || isPetrified(player))) {
                cure(player, thrower);
            } else if (!isPetrified(entity)) {
                wardGaze(entity);
            }
        }
        for (com.hogwartsmagic.entity.PetrifiedRemainsEntity statue
                : level.getEntitiesOfClass(com.hogwartsmagic.entity.PetrifiedRemainsEntity.class, area)) {
            if (PotionRules.inSplash(center.distanceToSqr(statue))) {
                reviveStatue(level, statue, thrower);
            }
        }
    }

    // ---- Statues ---------------------------------------------------------------------------------
```

In `src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java`, find:

```java
    @Override
    public boolean isPickable() {
        return true;
    }
```

and replace it with:

```java
    @Override
    public boolean isPickable() {
        return true;
    }

    /** Right-click with a Mandrake Restorative Draught: the mob comes back (one dose). Bosses stay stone. */
    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(com.hogwartsmagic.ExampleMod.MANDRAKE_DRAUGHT.get())) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && com.hogwartsmagic.petrification.Petrification.reviveStatue(serverLevel, this, player)) {
            com.hogwartsmagic.item.BrewedPotionItem.useUpDose(player, hand, stack);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide());
    }
```

- [ ] **Step 5: Register and bottle.** In `src/main/java/com/hogwartsmagic/ExampleMod.java`, find (Task 5 text):

```java
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FELIX));
```

and replace it with:

```java
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.FELIX));
    public static final RegistryObject<Item> MANDRAKE_DRAUGHT = ITEMS.register("mandrake_draught",
            () -> new com.hogwartsmagic.item.BrewedPotionItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    com.hogwartsmagic.item.BrewedPotionItem.Kind.MANDRAKE));
    public static final RegistryObject<Item> SPLASH_MANDRAKE_DRAUGHT = ITEMS.register("splash_mandrake_draught",
            () -> new com.hogwartsmagic.item.SplashBrewItem(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON),
                    com.hogwartsmagic.item.SplashBrewItem.Kind.MANDRAKE));
    public static final RegistryObject<Item> DRAUGHT_OF_LIVING_DEATH = ITEMS.register("draught_of_living_death",
            () -> new com.hogwartsmagic.item.SplashBrewItem(new Item.Properties(),
                    com.hogwartsmagic.item.SplashBrewItem.Kind.LIVING_DEATH));
```

Find (Task 5 text):

```java
                output.accept(FELIX_FELICIS.get());
```

and replace it with:

```java
                output.accept(FELIX_FELICIS.get());
                output.accept(MANDRAKE_DRAUGHT.get());
                output.accept(SPLASH_MANDRAKE_DRAUGHT.get());
                output.accept(DRAUGHT_OF_LIVING_DEATH.get());
```

In `src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java`, find (Task 5 text):

```java
        if (BrewRules.FELIX_FELICIS.id().equals(id)) {
            return new ItemStack(ExampleMod.FELIX_FELICIS.get());
        }
```

and replace it with:

```java
        if (BrewRules.FELIX_FELICIS.id().equals(id)) {
            return new ItemStack(ExampleMod.FELIX_FELICIS.get());
        }
        if (BrewRules.MANDRAKE_DRAUGHT.id().equals(id)) {
            return new ItemStack(BrewRules.usedTail(history, BrewRules.GUNPOWDER)
                    ? ExampleMod.SPLASH_MANDRAKE_DRAUGHT.get() : ExampleMod.MANDRAKE_DRAUGHT.get());
        }
        if (BrewRules.LIVING_DEATH.id().equals(id)) {
            return new ItemStack(ExampleMod.DRAUGHT_OF_LIVING_DEATH.get());
        }
```

- [ ] **Step 6: Client registration.** In `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`:

(a) Find (Task 5 text):

```java
                ExampleMod.ESSENCE_OF_DITTANY.get(), ExampleMod.FELIX_FELICIS.get());
```

and replace it with:

```java
                ExampleMod.ESSENCE_OF_DITTANY.get(), ExampleMod.FELIX_FELICIS.get(), ExampleMod.MANDRAKE_DRAUGHT.get());
        event.register(
                (stack, tintIndex) -> tintIndex == 0 && stack.getItem() instanceof com.hogwartsmagic.item.SplashBrewItem splash
                        ? net.minecraft.util.FastColor.ARGB32.opaque(splash.kind().color())
                        : -1,
                ExampleMod.SPLASH_MANDRAKE_DRAUGHT.get(), ExampleMod.DRAUGHT_OF_LIVING_DEATH.get());
```

(b) Find:

```java
        event.registerEntityRenderer(ModEntities.PETRIFIED_REMAINS.get(), PetrifiedRemainsRenderer::new);
```

and replace it with:

```java
        event.registerEntityRenderer(ModEntities.PETRIFIED_REMAINS.get(), PetrifiedRemainsRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_BREW.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
```

- [ ] **Step 7: Item models.** `src/main/resources/assets/hogwartsmagic/models/item/mandrake_draught.json`:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/potion_overlay",
    "layer1": "minecraft:item/potion"
  }
}
```

`splash_mandrake_draught.json` and `draught_of_living_death.json`, both:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "minecraft:item/potion_overlay",
    "layer1": "minecraft:item/splash_potion"
  }
}
```

- [ ] **Step 8: Lang.** In `en_us.json`, find (Task 8 text):

```json
  "gui.hogwartsmagic.petrified.hud": "You are petrified. Press [%s] to give in.",
```

and replace it with:

```json
  "gui.hogwartsmagic.petrified.hud": "You are petrified. Press [%s] to give in.",
  "item.hogwartsmagic.mandrake_draught": "Mandrake Restorative Draught",
  "item.hogwartsmagic.mandrake_draught.tooltip": "Use it on someone turned to stone. Drunk, it steadies your eyes against a basilisk for 8 seconds.",
  "item.hogwartsmagic.splash_mandrake_draught": "Splash Mandrake Restorative Draught",
  "item.hogwartsmagic.splash_mandrake_draught.tooltip": "Throw it: frees everyone and everything turned to stone within 4 blocks.",
  "item.hogwartsmagic.draught_of_living_death": "Draught of Living Death",
  "item.hogwartsmagic.draught_of_living_death.tooltip": "Throw it: everything within 4 blocks sleeps like the dead for 20 seconds.",
  "message.hogwartsmagic.mandrake.drink": "Your eyes feel steady.",
  "entity.hogwartsmagic.thrown_brew": "Thrown Brew",
```

- [ ] **Step 9: Check and build.**
  - Validate `en_us.json` and the three item models.
  - Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25.
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java src/main/java/com/hogwartsmagic/item/SplashBrewItem.java src/main/java/com/hogwartsmagic/petrification` prints nothing.
  - The boot block. Expected: `Done (`; no entity type or item errors.

- [ ] **Step 10: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Mandrake Restorative Draught, Draught of Living Death and the thrown brew

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
M=src/main/resources/assets/hogwartsmagic/models/item
NEW="src/main/java/com/hogwartsmagic/item/SplashBrewItem.java src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java $M/mandrake_draught.json $M/splash_mandrake_draught.json $M/draught_of_living_death.json"
git add $NEW
git commit -F /tmp/msg.txt -- $NEW src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/entity/ModEntities.java src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java src/main/java/com/hogwartsmagic/petrification/Petrification.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/block/entity/WizardCauldronBlockEntity.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 10: Guide pages

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`

**Interfaces.** Consumes: nothing new. Produces: nine pages at the end of the `POTIONS` section.

- [ ] **Step 1: The pages.** In `src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java`, find:

```java
                        "The brew gives 3 doses, each carrying the same sample. The brewing stand no longer makes Polyjuice."
                ))
        ));
```

and replace it with:

```java
                        "The brew gives 3 doses, each carrying the same sample. The brewing stand no longer makes Polyjuice."
                )),
                page("Essence of Dittany", List.of(
                        "Closes a Cursed Wound and heals 4 health.",
                        "1. Add 3 Dittany.",
                        "2. Stir clockwise 5 times.",
                        "3. Simmer 30 seconds."
                )),
                page("Mandrake Restorative Draught", List.of(
                        "Brings back anyone the basilisk has turned to stone.",
                        "1. Add 2 Mandrake Roots.",
                        "2. Add 1 Asphodel.",
                        "3. Stir clockwise 4 times.",
                        "4. Simmer 45 seconds.",
                        "5. Add 1 Arcane Dust."
                )),
                page("Using the Mandrake Draught", List.of(
                        "Right-click a petrified friend, or a stone statue, with a bottle to bring them back.",
                        "Add 1 Gunpowder after the Arcane Dust and every dose becomes a splash potion that frees all the stone within 4 blocks.",
                        "Drunk, it breaks a basilisk's hold on you and steadies your eyes for 8 seconds."
                )),
                page("Draught of Living Death", List.of(
                        "A splash potion: everything within 4 blocks falls into a deathlike sleep for 20 seconds. The great shrug off half of it, and it wears thinner on a victim struck again soon.",
                        "1. Add 1 Asphodel.",
                        "2. Add 1 Wormwood.",
                        "3. Add 2 Valerian Sprigs.",
                        "4. Stir anticlockwise 7 times."
                )),
                page("Living Death: the finish", List.of(
                        "5. Stir clockwise once. The brew turns clear as water.",
                        "6. Simmer 60 seconds.",
                        "7. Add 1 Gunpowder.",
                        "Seven anticlockwise, then one clockwise. Do not trust the old textbook."
                )),
                page("Felix Felicis", List.of(
                        "Liquid luck: 3 minutes of Luck III, and every spell you master grows a quarter faster. The brew gives a single dose.",
                        "1. Add 1 Ashwinder Egg.",
                        "2. Add 1 Acromantula Venom.",
                        "3. Add 1 Gold Nugget.",
                        "4. Stir clockwise 3 times."
                )),
                page("Felix Felicis: patience", List.of(
                        "5. Simmer 600 seconds (10 minutes).",
                        "6. Stir anticlockwise once.",
                        "Take it off the heat after the simmer if you must leave it: it overbrews like any other."
                )),
                page("Where ingredients grow", List.of(
                        "Dittany grows on earth and bare rock in the Forbidden Forest and the Horntail Peaks. Plant it again with a sprig.",
                        "Apothecaries sell dittany, valerian, asphodel and wormwood, each shop only some of them.",
                        "Valerian sprigs turn up in the chests of the Hogwarts ruin camp.",
                        "Ashwinder eggs: Death Eater camps, the Chamber, and now and then a Blaze."
                )),
                page("Turned to stone", List.of(
                        "A basilisk's full stare no longer kills you: it turns you to stone. You cannot move or act, and nothing can hurt you.",
                        "Wait for a friend with a Mandrake Restorative Draught, or give in and wake at your bed (press the key on the screen).",
                        "A creature turned to stone leaves a statue. The Draught brings it back as it was, with its name and its master. Break the statue and it is gone for good."
                ))
        ));
```

(Nine new pages: seven topics, with Living Death and Felix on two pages each.)

- [ ] **Step 2: Build.** Gradle check, then `compileJava test`. Expected: BUILD SUCCESSFUL, BASE + 25.

- [ ] **Step 3: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Hogwarts Guide: Phase 2 potion pages, ingredients and petrification

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/client/HogwartsGuideScreen.java
```

---

### Task 11: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, BASE + 25 tests, all green.
- [ ] **Step 2:** Validate every JSON file the phase touched: `cd /home/user/minecraft_mods && git diff --name-only "$(git log --format=%H -1 --grep='Brewing Phase 2: four recipes and PotionRules')~1"..HEAD -- '*.json' | xargs python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]"`. Expected: no output.
- [ ] **Step 3:** Safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/brewing src/main/java/com/hogwartsmagic/petrification src/main/java/com/hogwartsmagic/item/SplashBrewItem.java src/main/java/com/hogwartsmagic/item/BrewedPotionItem.java src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java src/main/java/com/hogwartsmagic/entity/PetrifiedRemainsEntity.java src/main/java/com/hogwartsmagic/loot/BrewingIngredientLootModifier.java src/main/java/com/hogwartsmagic/block/DittanyPlantBlock.java src/main/java/com/hogwartsmagic/worldgen/DittanyPatchFeature.java`. Expected: nothing.
  - `grep -rn "switch" src/main/java/com/hogwartsmagic/petrification src/main/java/com/hogwartsmagic/client/PetrificationClient.java src/main/java/com/hogwartsmagic/client/PetrifiedChoiceScreen.java src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java src/main/java/com/hogwartsmagic/item/SplashBrewItem.java`. Expected: nothing.
  - `grep -n "SubscribeEvent(priority" src/main/java/com/hogwartsmagic/petrification/PetrificationEvents.java src/main/java/com/hogwartsmagic/brewing/IngredientDrops.java`: every hurt, death and drop handler says `EventPriority.LOWEST`, and each such method starts with an `isCanceled()` check.
  - `grep -rn "broadcastEntityEvent" src/main/java/com/hogwartsmagic/petrification src/main/java/com/hogwartsmagic/entity/ThrownBrewEntity.java`. Expected: nothing.
  - `grep -rn "removeEffect(.*CURSED_WOUND" src/main/java`. Expected: only the line inside `CombatEffects.cureCursedWound`.
  - `git status --short`. Expected: clean except untracked `run/` output.
- [ ] **Step 4:** The boot block. Expected: `Done (`; no registry, data component, damage type, biome modifier, loot modifier, model or class-loading errors; no server java process left afterwards.
- [ ] **Step 5:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 12: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 11. Creative for setup, survival for the petrification rows. Rows 17, 21 and 28 need two players; row 30 a dedicated server.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | Creative tab | Look after Gillyweed and after Failed Potion | Dittany, Asphodel, Wormwood, Valerian Sprigs, Ashwinder Egg; Essence of Dittany, Felix Felicis (purple name, glint), Mandrake Restorative Draught (yellow name), Splash Mandrake Draught, Draught of Living Death, each a tinted bottle |
| 2 | New world, `/locate biome hogwartsmagic:horntail_peaks` and `forbidden_forest` | Walk the surface | Small woolly bushes with pink spikes, on rock and on earth |
| 3 | A dittany bush | Break it; right-click the ground with a sprig | 1-2 Dittany drop; the sprig plants a bush |
| 4 | Apothecary in Diagon Alley | Look at level 1 and 2 trades of a new villager | Some of: Dittany 2 for 6, Valerian 2 for 4 (L1); Asphodel 1 for 8, Wormwood 2 for 6 (L2); exact prices |
| 5 | Kill Blazes (`/summon blaze`) about 40 times | | About two Ashwinder Eggs; the egg survives lava |
| 6 | Open fresh chests in a Hogwarts ruin camp, a Death Eater camp, the Chamber | | Sometimes Valerian Sprigs (ruin camp), an Ashwinder Egg (camp, Chamber); a vanilla dungeon or pillager outpost elsewhere never has them |
| 7 | Cauldron over a campfire, full | Brew Essence of Dittany by the guide | Each step changes the colour; finished after 30 s; 3 doses |
| 8 | Get a Cursed Wound (Dolohov's Curse from a duel or `/effect give @s hogwartsmagic:cursed_wound 60`) | Drink Essence of Dittany | The wound is gone, +4 health, green sparkles |
| 9 | Brew Felix Felicis (simmer 10 minutes, then sneak-stir once) | Bottle it | One dose only; the cauldron is empty after |
| 10 | Drink Felix | Check effects; cast a spell you are levelling and compare XP (`/spellmastery`) with before | Luck III for 3:00, gold sparkles, chime; about 25% more mastery XP per cast |
| 11 | Brew Felix and stir clockwise at the end | | Ruined |
| 12 | Brew the Mandrake Draught; bottle one, then add Gunpowder to a second brew | | Plain brew bottles Mandrake Restorative Draught; the gunpowder brew bottles Splash Mandrake Draught |
| 13 | Brew Living Death: 7 anticlockwise (sneak), 1 clockwise | Watch the colour after the clockwise stir | Clear, nearly white; simmer 60 s; it waits for gunpowder; then 3 Draughts of Living Death |
| 14 | Living Death, 6 anticlockwise then clockwise | | Ruined |
| 15 | Throw Living Death at three zombies | | Splash particles; all three stunned 20 s; a second throw right after stuns for less (diminishing returns) |
| 16 | Throw Living Death at the Hungarian Horntail | | Stunned for about 10 s (half) |
| 17 | Two players, survival | One throws Living Death at the other | Stunned for 12 s (60%) |
| 18 | Survival, a Basilisk (`/summon hogwartsmagic:basilisk`), look it in the eyes | | The screen greys, then you turn to stone: grey tint, a chat line, the choice screen; the game does not pause |
| 19 | Row 18 | Press Wait | The screen closes; HUD: "You are petrified. Press [J] to give in."; you cannot move, look, jump, attack, use, open the inventory, drop or cast; the Basilisk ignores you |
| 20 | Row 19 | Let a zombie, a Death Eater and a Dementor near you; stand in lava with `/tp`; wait under water | Nobody attacks; no damage, no drowning, no hunger loss |
| 21 | Row 19, second player with a Mandrake Draught | Right-click the stone player | Stone cracks away; Slowness 5 s; "X brought you back." and "You brought X back."; the bottle is empty |
| 22 | Stone again | Press J, then Give in | You die ("gave in to the stone"), drop items as usual (or keep them with keepInventory), respawn free; a totem does not save you |
| 23 | Stone again, with a bound Horcrux | Give in | The Horcrux rebirth plays |
| 24 | Stone again, with a second player and a mob statue (made as in row 27) 3 blocks from you | The second player throws one Splash Mandrake Draught between you | You are freed with both lines; the wolf comes back too; a zombie 6 blocks away is untouched |
| 25 | Stone again | Press T on the choice screen; chat | Chat opens and works |
| 26 | Config `petrificationMaxSeconds = 30` (restart) | Get petrified, wait | After 30 s: "You have been stone too long." and death |
| 27 | Named, tamed wolf; let the Basilisk petrify it | | It dies into a grey statue; no drops, no XP orbs |
| 28 | Row 27 | Splash Mandrake Draught on the statue (or right-click it with a bottle) | The wolf stands where the statue was, with its name, its owner, full health, sitting state |
| 29 | Petrify a villager with trades and a llama with a chest; revive them | | Same trades; the chest still holds its items; nothing duplicated on the ground |
| 30 | Dedicated server | Get petrified, log out, log back in | Still stone; the choice screen opens again once the world has loaded |
| 31 | A statue from before this update (or a boss statue: let the Basilisk kill the Horntail) | Splash it | An old statue brings back a fresh mob of its type; the boss statue stays stone, and the boss dropped its loot |
| 32 | Break a mob statue | | It is gone; nothing drops |
| 33 | Creative mode, meet the Basilisk's eyes | | No stone, no death (as before) |
| 34 | Drink a Mandrake Draught, then meet the Basilisk's eyes | | For 8 s the grey does not build ("Your eyes feel steady.") |
| 35 | Hogwarts Guide, Potions | Page through | The nine new pages after "Polyjuice: the sample" |
| 36 | Keybinds | Options > Controls > Harry Potter Mod | "Give in (while petrified)" on J; rebinding it changes the HUD line |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Phase 2.**
  - Ingredients (five items, the plant and its patch following `MandrakePatchFeature`, trades, chest loot, the Blaze): Task 3 (items, plant, worldgen), Task 4 (trades, loot modifier, Blaze); rulings 6-9; rows 2-6.
  - Essence of Dittany (cure through `cureCursedWound`, 4 HP): T1 recipe, T5; rows 7-8.
  - Mandrake Draught (splash on stone players and statues in 4 blocks, right-click a stone player, drink ward): T1 recipe, T9 (`interactLivingEntity`, `splashMandrake`, statue `interact`), T6 ward in `BasiliskEntity`; rulings 13, 29; rows 12, 21, 24, 28, 34.
  - Living Death (splash only, `CrowdControl.apply` stunned 20 s): T1 recipe, T9 `ThrownBrewEntity.splashLivingDeath`; rulings 1, 12; rows 13-17.
  - Felix (Luck III, +25% mastery XP in `MasteryAwards.grant`, 1 dose, 10-minute simmer): T1, T5; ruling 5; rows 9-11.
  - Splash variants and their thrown entity: T9; rulings 2, 11.
  - Petrification, players: the gaze petrifies (T6 `BasiliskEntity.petrify`, ruling 15); the persisted state with start time (T6 `petrify`, ruling 14); no movement, look, jump, attack, use, cast, inventory (T6 `isStunned`, T8 look and keys, rulings 16-17); no damage except void, `/kill`, give in (T6 `onLivingAttack`, ruling 18); not a target (T6 `onChangeTarget`, `clearHunters`, `isValidVictim`, ruling 19); no hunger, air held (T6 `tick`); the stone tint (ruling 20); the choice screen, HUD line, rebindable key, no pause, chat (T8, rulings 24-25); `GiveInPacket` checked, normal death with the Basilisk damage source (T6, rulings 21, 26); survives logout (keys and effect are saved; T8 opens the screen on login; row 30); `petrificationMaxSeconds` (T6, ruling 23; row 26); the cure with Slowness I 5 s and both lines (T6 `cure`, ruling 30; row 21).
  - Petrification, mobs: statue with full NBT (T7, ruling 27); no loot, no XP, LOWEST with guard (T7, ruling 28); the cure (T7 `reviveStatue`, ruling 29); older statue brings a fresh mob; breaking still destroys (unchanged `hurt`). Rows 27-32.
  - Bosses keep the lethal gaze (T6 `petrify` keeps the old path for every non-player; T7 refuses boss revives and keeps boss drops); row 31.
  - Pure parts test-first: T1 (14 tests), T2 (11 tests).
  - Guide pages: T10.
  - Done when: rows 7-17 (each potion), 18-26 (wait, give in, saved), 27-28 (named tamed wolf), 27 and 32 (nothing drops), 15-16 (diminishing returns and bosses), 30 (relog on a dedicated server).
- **Type consistency.** `Petrification.cure(ServerPlayer, Entity)` is called from `BrewedPotionItem.interactLivingEntity` (rescuer `Player`) and `splashMandrake` (thrower `Entity`); `reviveStatue(ServerLevel, PetrifiedRemainsEntity, Entity)` from `PetrifiedRemainsEntity.interact` and `splashMandrake`; `BrewedPotionItem.useUpDose(Player, InteractionHand, ItemStack)` from both right-click paths; `CrowdControl.apply(LivingEntity, Holder<MobEffect>, int, Entity)` exists at 996f638; `PotionRules.felixXpMultiplier(int)` takes the amplifier or -1. Checked against the 1.21 mapped jar: `StructureManager.getStructureWithPieceAt(BlockPos, Predicate<Holder<Structure>>)`, `Holder.is(ResourceKey)`, `StructureStart.isValid()`, `LootContext.getQueriedLootTableId/getLevel/getParamOrNull`, `ThrownPotion` (no hook for custom impact), `ThrowableItemProjectile(EntityType, LivingEntity, Level)`, `LevelEvent.PARTICLES_SPELL_POTION_SPLASH = 2002`, `ItemStack.consume(int, LivingEntity)`, `ItemNameBlockItem`, `Item.interactLivingEntity`, `Entity.interact/load/saveWithoutId/setUUID`, `EntityType.byString/create(Level)/is(TagKey)`, `Mth.createInsecureUUID(RandomSource)`, `LivingEntity.deathTime` (public), `FoodData.setExhaustion`, `MobEffectInstance.INFINITE_DURATION/isInfiniteDuration`, `ServerPlayer.server` (public), `ChatScreen(String)`, `Screen.renderBlurredBackground/renderTransparentBackground/isPauseScreen`; `LivingChangeTargetEvent`, `LivingExperienceDropEvent`, `LivingDropsEvent`, `LivingAttackEvent`, `LivingDeathEvent` are all cancelable in Forge 51.0.33. `LivingEntity.die` does not clear effects, so the 60-tick petrification is still on the mob at the drop and XP events.
- **Placeholder scan:** none. Every number, colour, sound, key, trade price, chance, lang line and page is given. The one value read at run time is the network protocol number (ruling 26, on purpose).
- **Counts:** Task 1 adds 14 tests (9 + 5), Task 2 adds 11; BASE + 25 at the end.
