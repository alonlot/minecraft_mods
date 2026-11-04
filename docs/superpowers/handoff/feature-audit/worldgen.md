# Audit report: worldgen (world generation, structures, dimensions, world events)

## Features checked

| Feature | Status |
|---|---|
| Biome mixin (OverworldBiomeBuilderMixin), 7 mod biomes as overworld climate points | FIXED (production crash) |
| Surface rules for Dementor Fen, Horntail Peaks, Chamber Depths, Goblin Undercity | FIXED (never applied in a real world) |
| ClimateSlices / *ClimateZones | OK (opinion below on Forbidden Forest) |
| Biome JSONs (8), placed/configured features, carvers, biome modifiers (11) | OK |
| Biome tags, structure tags, #minecraft:is_overworld | FIXED (Forbidden Forest missing from is_overworld) |
| Features: arcane cluster, gillyweed swamp, mandrake patch, fine wood tree | OK |
| Feature: cobwebs (registered, never used by any JSON) | OK (dead code, harmless) |
| 9 jigsaw structures (structure, structure_set, template_pool, nbt, entities, loot refs) | OK |
| Death Eater camp (spec 2026-08-15): placement, salt, spacing, biome tag, painting, wizards | OK (painting lang added) |
| painting_variant summoning_rite (64x48 texture present) | FIXED (title/author lang keys missing) |
| damage_type bleeding + tags + death messages | OK |
| Diagon Alley dimension JSON + biome | OK |
| Diagon Alley build (DiagonAlleyManager) | FIXED (stale job after world close) |
| Diagon Alley shop chests (21 chests in diagon_alley.nbt) | FIXED (loot tables did not exist, chests empty) |
| Diagon Alley portal / view / registry / archway brick | OK as read (see risks) |
| Diagon Brick survival source | OPEN (no recipe, no loot) |
| /findalley | FIXED (stale help text) |
| /spawnalley, /findminihogwarts, /spawnminihogwarts | OK / FIXED (see Mini Hogwarts) |
| Mini Hogwarts auto spawn + build | FIXED (synchronous chunk loads, stale job) |
| Gringotts village bank (VillageGringottsBank, tracker, schematic, furnishing, gate lock) | FIXED (stale job after world close) |
| Gringotts gate | FIXED (guards came at the break, not the first swing) |
| Gringotts lift | OK (rule note below) |
| Vault door | OK |
| Thief's Downfall | FIXED x2 (cart riders never washed; cursed gold never removed) |
| Cart (GringottsCartEntity) | FIXED (Downfall sweep added) |
| Undercroft builder / carvers / tracker | FIXED (sync chunk loads after players leave, stale job, fog desync) |
| Gringotts alarm (heat) | FIXED x2 (goblin at a vault chest raised heat; killing staff raised nothing) |
| GringottsDisguise | OK |
| Commands: buildbank, buildundercroft, carttrack, findgringotts, gringottslandmarks, undercroftdump, wizardraid | OK (all registered, op level 2 where needed) |
| Death Eater raids (raid leader, Morsmordre, base raids, boss bar, Hero reward) | FIXED (free Hero reward by walking away; raids leaked across worlds) |
| First-year invitation owl (invite + guide) | OK |
| Insomnia Dementors | OK |
| Structure-themed spawning (students, outpost Death Eaters, lycanthrope villagers) | OK (risk note) |
| Spawn placements for every naturally spawning mod mob | OK |
| Advancements (28 hp/ + 8 recipe) | OK (all triggers reachable, parents/icons/lang exist; code ids hp/root, hp/dark_wizard_down, hp/voldemort_falls exist) |
| Loot modifiers (5 JSON + global list) | OK (all 5 codecs registered) |
| Chest loot tables (gringotts_vault, chamber_of_secrets, merfolk_hoard) | OK |
| function/place_skyblockhogwartshacxs_chunks.mcfunction (+ legacy functions/ copy) | OPEN (references 544 templates that do not exist) |

## Fixes

1. `mixin/OverworldBiomeBuilderMixin.java:33-35` - `remap = false` on a vanilla method. Forge 1.21 (51.0.33) runs on SRG names in production (the runtime jar has `m_187175_`, and the refmap is how every other mod mixin reaches it). With `remap = false` the mixin has no refmap entry, so in a built jar the injection finds no target and, with `defaultRequire: 1`, the game crashes when it loads the overworld biome builder (world creation / server start). Dev runs use Mojang names, which is why boot checks passed. Removed `remap = false`; the refmap now maps it to `m_187175_`.
2. Surface rules - the old `SurfaceRuleDataMixin` injected into `SurfaceRuleData.overworld()`, which in 1.21 is only called by datagen and `Commands.validate`; real worlds read `noise_settings/overworld.json` from the vanilla data pack. So the fen, the scorched peaks, and both cave biomes never got their blocks (grass/stone instead of mud, blackstone, tuff, deepslate tiles). It also had `remap = false` (same crash as fix 1). Moved the rules unchanged into new `worldgen/ModSurfaceRules.java`, added `mixin/NoiseGeneratorSettingsMixin.java` that prepends them to `NoiseGeneratorSettings.surfaceRule()` (the accessor every reader uses; cached per settings object; rules are biome-guarded, so other dimensions are unaffected), deleted `SurfaceRuleDataMixin.java`, and swapped the entry in `hogwartsmagic.mixins.json`. Only new chunks change.
3. `block/ThiefsDownfallBlock.java` + `entity/GringottsCartEntity.java` (new `washRidersInDownfall`, called from `tick`) - passengers never move on their own, so `entityInside` never fired for cart riders, and the cart is not a LivingEntity. The Downfall (the counter to Polyjuice "free passes to the deepest vaults", per the vault door's own comment) did nothing to anyone riding the line, which is the only way along it. The cart now sweeps the cells it passed this tick and washes every rider (the block's 60 tick cooldown prevents repeats).
4. `block/ThiefsDownfallBlock.java` (`washCursedGold`) - wiki and `GeminoEvents` say the Downfall destroys cursed gold ("the trap on the way in is the cure on the way out"); no code did it. Now removes every cursed galleon from the player's inventory.
5. `event/ModEvents.java` (raids: `ActiveWizardRaid.lastSeen`, `startWizardRaid`, `updateRaidProgress`, new `onServerStoppingClearRaids`) - a raid member was counted as beaten whenever `level.getEntity(id)` returned null, which is also true when its chunk unloads, and raid wizards were not persistent, so they also despawned (random despawn once the player is 32+ blocks away). Hitting one raider and walking away paid out Hero of the Village. Now raid members are made persistent (as vanilla raiders are mid-raid) and a member missing from an unloaded chunk still counts as alive. Raids are also cleared on server stop so a single player world closed mid-raid does not carry a boss bar and reward into the next world.
6. `worldgen/MiniHogwartsManager.java` (`requestSpawn`, `maybeAutoSpawn`) + `command/SpawnMiniHogwartsCommand.java` - no residency check: the 96x145 castle was placed 90,90 from the player and its far corner reaches about 11 chunks, so heightmap sampling and every setBlock loaded or generated chunks synchronously on the server thread (the class doc promised the opposite). Now every footprint chunk must be loaded; auto spawn falls back to +90 / -90 along X (about 9 chunks) so it still works at a dedicated server's default view distance; the command explains the failure.
7. `worldgen/undercroft/UndercroftBuilder.java` (`tick`) - the carve kept writing after everyone left and the footprint unloaded, loading chunks from the tick thread. It now saves its cursor and pauses; `maybeResume` restarts it on the next visit (the tracker already supported this).
8. `UndercroftBuilder.java` (`maybeSyncFog`, `FOGGED`) - keyed by UUID and never cleared: after a relog inside the undercroft the server thought the fog was already sent, so the client had none until the player left and came back; the map also grew forever. Now a weak map keyed by the player object, and a new player object (login, respawn) is always told its state.
9. `VillageGringottsBank.java`, `DiagonAlleyManager.java`, `MiniHogwartsManager.java`, `UndercroftBuilder.java` (`tick`) - static build jobs hold a ServerLevel. In single player, closing a world mid-build and opening another left the job writing into the old, stopped level from the new server thread (blocks on the old chunk executor: hang, or an exception that MiniHogwarts did not catch). Each tick now drops a job whose level belongs to another server.
10. `block/GringottsGateBlock.java` (new `attack`) - wiki and class doc: "the first swing brings every guard in the hall". Guards were only alerted when a bar actually broke, about 12 s later. Now any non-goblin starting to hit the screen alerts guards within 48; heat is still raised on the break.
11. `event/GoblinEvents.java` (`onChestOpened`) - a Polyjuiced goblin opening a vault chest raised heat and set every guard on them, although opening the vault door as a goblin is deliberately silent ("the difference between a robbery and a withdrawal"). Goblins are now exempt here too.
12. `event/GoblinEvents.java` (new `onStaffKilled`, LOWEST + isCanceled guard) + lang `gringotts.alarm.staff` - wiki lists "killing staff" as a heat source; nothing raised heat for it. A player killing a goblin or goblin guard inside a bank region now raises heat by 1.
13. `data/hogwartsmagic/loot_table/chests/diagon_shop.json`, `knockturn.json` (new) - `diagon_alley.nbt` sets these tables on 20 shop/strongroom chests and Borgin's chest, but the tables did not exist, so every chest in Diagon Alley was empty (and the tome/book modifiers keyed on them could not fire). Added modest tables from existing items (galleons, floo powder, stationery, arcane dust; Knockturn: venom, bones, wither rose, rare dagger/core). No new items or textures.
14. `data/minecraft/tags/worldgen/biome/is_overworld.json` - the other six mod biomes are listed, Forbidden Forest was not. Strongholds (`#has_structure/stronghold` = `#is_overworld`) could not generate in the biome, which covers a large area, and goblin spawns (`#has_goblin`) skipped it. Added with required:false.
15. `command/FindDiagonAlleyCommand.java` - failure text told players to "explore plains/meadow biomes"; alleys are opened through a brick wall now. Text updated.
16. lang `painting.hogwartsmagic.summoning_rite.title/.author` added (the painting tooltip showed raw keys).

## Open items (not fixed)

- **Client mixins with the same production crash (not my area, please route):** `mixin/client/CaveInimicumClientPacketListenerMixin.java` (`handleSoundEvent`, `handleSoundEntityEvent`) and `mixin/client/CaveInimicumEntityRenderDispatcherMixin.java` (`shouldRender`) target vanilla methods with `remap = false`; both are in the required client mixin list, so a built jar crashes the client. `LevicorpusLivingEntityRendererMixin` has the same pattern but is not listed in the mixin json (dead). Fix is removing `remap = false` (1 line each).
- **Diagon Bricks have no survival source** (no recipe, no loot, no trade). The tooltip tells players to build a 7x7 wall of them, so the alley is creative/op only. Needs a design call (for example 8 bricks around 1 arcane dust gives 8). About 1 JSON.
- **`function/place_skyblockhogwartshacxs_chunks.mcfunction`** (and the identical legacy `functions/` copy, which 1.21 ignores) places 544 `hogwartsmagic:skyblockhogwartshacxs_chunks/*` templates that are not in the mod. It can never work. Recommend deleting both files (not done: deletion is the controller's call).
- **Gringotts lift sends a velocity packet to each rider every tick** for the ~4 s ride (`hurtMarked = true` per tick), against the "no per-tick velocity packets" rule. It is the lift's whole movement model; changing it (for example to a few teleport steps) is a redesign, about 40 lines. Not changed.
- **/spawnalley** writes a 233-wide street into whatever world it runs in without a residency check (synchronous chunk loads). It is documented as a test/render command, so left alone; the real opening path builds into the empty alley dimension.
- **Structure-themed spawning** calls `findNearestMapStructure` from the main thread every 10 s per player (radius about 5 chunks). Inside view distance this is cheap, but at a very low view distance it can generate chunks to STRUCTURE_STARTS synchronously. Pre-existing; a containment check like `isInsideStructurePiece` would avoid it (about 30 lines).
- **Diagon Alley crossing** moves players between levels with `removePlayerImmediately` / `addDuringTeleport` and no `PlayerChangedDimensionEvent`. Intentional for the seamless view; other systems that clean up on dimension change will not see these crossings. Risk note only.
- A bank build interrupted by a server stop is half built and retried on the next visit (the anchor may move because the heightmap now includes the half bank). Edge case, not changed.

## Opinions (not defects, not changed)

- `ForbiddenForestClimateZones` registers only the depth 0.0 point, while `ClimateSlices` documents that a surface biome needs 0.0 and 1.0 or the biome name flips underground. Switching it to `ClimateSlices.surface(...)` would change worldgen; left as is.
- `CobwebFeature` is registered as `hogwartsmagic:cobwebs` but no configured feature uses it (fine wood trees use vanilla cobweb decorators).
- Recipe advancements `recipes/misc/broom|firebolt|harry_potter_wand|nimbus_2000` have no rewards and no matching recipe (those items come from the workbenches). Harmless leftovers.
- `#hogwartsmagic:has_diagon_alley` and `#hogwartsmagic:has_fine_wood` are unused tags.

## Doc mismatches

- Wiki "The lift: Stand on the plinth and it takes you down": code requires right-clicking the plinth. Code is fine; wiki should say "use the plinth".
- Wiki "Thief's Downfall ... also destroys cursed gold": was not true; code fixed to match the wiki.
- Wiki "heat ... killing staff": was not true; code fixed to match the wiki.
- Wiki "the first swing brings every guard": code fixed to match.
- Spec 2026-08-15 says the camp is 26 x 12 x 26; the compiled nbt is 27 x 14 x 27 (the build script is the source of truth; harmless).
- Wiki does not mention Diagon Alley, Mini Hogwarts or the Death Eater camp at all under "World Content and Generation".

## Files changed

- src/main/java/com/hogwartsmagic/mixin/OverworldBiomeBuilderMixin.java
- src/main/java/com/hogwartsmagic/mixin/NoiseGeneratorSettingsMixin.java (new)
- src/main/java/com/hogwartsmagic/mixin/SurfaceRuleDataMixin.java (deleted, rules moved)
- src/main/java/com/hogwartsmagic/worldgen/ModSurfaceRules.java (new)
- src/main/resources/hogwartsmagic.mixins.json (one entry renamed)
- src/main/java/com/hogwartsmagic/worldgen/MiniHogwartsManager.java
- src/main/java/com/hogwartsmagic/worldgen/DiagonAlleyManager.java
- src/main/java/com/hogwartsmagic/worldgen/VillageGringottsBank.java
- src/main/java/com/hogwartsmagic/worldgen/undercroft/UndercroftBuilder.java
- src/main/java/com/hogwartsmagic/block/GringottsGateBlock.java
- src/main/java/com/hogwartsmagic/block/ThiefsDownfallBlock.java
- src/main/java/com/hogwartsmagic/entity/GringottsCartEntity.java (another agent also edited this file for the return ride; no overlap)
- src/main/java/com/hogwartsmagic/event/GoblinEvents.java
- src/main/java/com/hogwartsmagic/event/ModEvents.java (raid code only)
- src/main/java/com/hogwartsmagic/command/SpawnMiniHogwartsCommand.java
- src/main/java/com/hogwartsmagic/command/FindDiagonAlleyCommand.java
- src/main/resources/data/hogwartsmagic/loot_table/chests/diagon_shop.json (new)
- src/main/resources/data/hogwartsmagic/loot_table/chests/knockturn.json (new)
- src/main/resources/data/minecraft/tags/worldgen/biome/is_overworld.json
- src/main/resources/assets/hogwartsmagic/lang/en_us.json (3 keys added)

## Build

- compile.sh: BUILD SUCCESSFUL (last run after all Java edits). The refmap now contains `OverworldBiomeBuilderMixin -> m_187175_` and `NoiseGeneratorSettingsMixin -> f_188871_`.
- All edited JSON validated. No tests run (the area has no Rules/Math classes with tests).
- In-game checks owed: new world, find a Dementor Fen / Horntail Peaks (mud and coarse dirt; blackstone and basalt); ride the cart through the Downfall while Polyjuiced as a goblin (disguise stripped, cursed galleons gone); open a Diagon Alley shop chest (items); start a Death Eater raid, hit one raider, fly 200 blocks away (no Hero reward); test a built jar (not runServer) to confirm the biome mixin applies.
