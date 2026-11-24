# Dragon Nests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Real Horntail and Ironbelly egg blocks that you steal at great risk (every dragon within 64 blocks hunts the thief), a new Ironbelly nest on the cold peaks, and Borgin buying the eggs.

**Architecture:** Two instances of one egg block class (`DragonNestEggBlock`), shaped like an egg. A Forge `BreakEvent` handler gives every dragon nearby a grudge, stored in the Horntail base class (and so inherited by the Ironbelly) as thief UUID + expiry game time. A priority-0 target goal enforces it. All the numbers are pure statics in `DragonEggRules`. The nest is a generator-built `.nbt` with the wild Ironbelly baked in as `Chained:0b`. The first time it ticks it takes its own spot as its nest, and it walks back there after a hunt.

**Tech Stack:** Forge 51 / MC 1.21 / Java 21 (Mojang mappings), JUnit 5, Forge GameTest, Python 3 + PIL (`build_scripts/generate_biome_structures.py`, `tools/item_textures/`).

**Spec:** `docs/superpowers/specs/2026-11-24-wizarding-world-additions-design.md` section 4. Also read `C:/Users/alonl/hp-ww-tools/BUILDER_RULES.md` first: it covers the compile/test commands, the shared-file rules and the commit style.

## Global Constraints

- **No baby dragons, no hatching, no dragon riding** (the user's own anti-goals). Eggs never tick, never grow, never spawn anything.
- Server/client safety: nothing under `client/` or `net.minecraft.client.*` is touched. This feature needs no client Java at all (JSON models only). Grudges, targeting and world edits are server-side only.
- No entity event bytes 21/35/63. No `switch` on an enum anywhere new.
- 1.21 API: `add/readAdditionalSaveData` are public; datapack folders are singular (`loot_table/`, `tags/block/`, `tags/item/`, `structure/`).
- Compile only with `bash C:/Users/alonl/hp-vfx-tools/compile.sh <worktree>` and run tests only with `bash C:/Users/alonl/hp-ww-tools/test.sh <worktree> "<pattern>"` (timeout 600000). Never run `gradlew`.
- Shared files (`ExampleMod.java`, `lang/en_us.json`, `ModVillagers.java`, `generate_biome_structures.py`, `mineable/pickaxe.json`, `bombarda_immune.json`): append a compact block at the END of each list, under a comment `// Dragon nests` / `# --- Dragon nests ---`.
- The Gringotts Ironbelly (`UndercroftBuilder.spawnDragon`: `setAnchor`, chained) must behave exactly as before.
- Javadoc says what the player SEES; British spelling; named, commented constants.

## Review Focus

1. **The mock GameTest player is creative.** `makeMockServerPlayerInLevel` overrides `isCreative()` to true, so a theft test using it passes vacuously or fails for the wrong reason. Use a real survival player (Task 3 copies the `LeftoversGameTests.survivalPlayerWithDarkness` pattern).
2. **Wild versus Gringotts Ironbelly.** A summoned Ironbelly defaults to `Chained=true` with no anchor, and a freed Gringotts one has `Chained=false` with an anchor. Neither may adopt a nest. Pinned by `onlyAWildIronbellyAdoptsANest` (Task 1) and `theGringottsIronbellyNeverTakesANest` (Task 3).
3. **The thief leaves.** They log out, die, change dimension, or run beyond 96 blocks. The goal must resolve the thief only through `ServerLevel#getPlayerByUUID` on the dragon's own level, must not crash on null, and keeps the grudge until it expires. A thief who dies clears it. Pinned by `aThiefWhoLeftIsNoLongerHunted` (Task 3).
4. **Eggs removed without a player.** Pistons, explosions and Bombarda would hand out a free trophy or wipe the egg out. Use `PushReaction.BLOCK`, explosion resistance 1200, and the `bombarda_immune` tag. Wingardium and Diffindo already post a real `BreakEvent` through `TerrainRules.canBreak`, so they count as theft.
5. **Template drift.** A later `put` can overwrite an egg, an old tuff-wall post can be left floating above a new egg, or the dragon's box can intersect a boulder. Pinned by `audit_dragon_eggs` and `audit_ironbelly_nest` (Task 4) plus the existing `audit_entity_room`.

---

### Task 1: DragonEggRules (pure rules + JUnit)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/dragon/DragonEggRules.java`
- Test: `src/test/java/com/hogwartsmagic/dragon/DragonEggRulesTest.java`

**Interfaces:**
- Produces (all `public static`, final class, private ctor):
  - `double THEFT_RADIUS = 64.0D` (how far a dragon notices a theft); `int GRUDGE_TICKS = 6000` (5 min); `double PURSUIT_RANGE = 96.0D` (how far it keeps after a thief it already has); `double NEST_RADIUS = 12.0D`; `int HORNTAIL_EGG_PRICE = 32`; `int IRONBELLY_EGG_PRICE = 40` (emeralds).
  - `boolean raisesGrudge(boolean creative, boolean spectator)`
  - `boolean notices(double distanceSqr)`: `distanceSqr <= THEFT_RADIUS²`
  - `long grudgeExpiry(long now)`: `now + GRUDGE_TICKS`
  - `boolean grudgeActive(long now, long expiresAt)`: `now < expiresAt`
  - `long renewedExpiry(long now, long expiresAt, boolean thiefCarriesEgg, double distanceSqr)`: carrying an egg inside the pursuit range pushes the expiry to `max(expiresAt, now + GRUDGE_TICKS)`; it never shortens it
  - `boolean pursues(long now, long expiresAt, double distanceSqr)`: active and within `PURSUIT_RANGE²`
  - `boolean adoptsNest(boolean chained, boolean hasAnchor, boolean hasNest)`: `!chained && !hasAnchor && !hasNest`
  - `boolean shouldReturnHome(boolean hasTarget, double distanceSqrFromNest)`: `!hasTarget && d > NEST_RADIUS²`
  - `int priceEmeralds(boolean ironbelly)`

- [ ] **Step 1: Write the failing tests**

```java
@Test void aDragonWithinSixtyFourBlocksNoticesTheTheft() {
    assertTrue(DragonEggRules.notices(64.0 * 64.0));
    assertFalse(DragonEggRules.notices(64.01 * 64.01));
}
@Test void creativeAndSpectatorPlayersNeverRaiseAGrudge() {
    assertTrue(DragonEggRules.raisesGrudge(false, false));
    assertFalse(DragonEggRules.raisesGrudge(true, false));
    assertFalse(DragonEggRules.raisesGrudge(false, true));
}
@Test void theGrudgeLastsFiveMinutes() {
    assertEquals(7000L, DragonEggRules.grudgeExpiry(1000L));
    assertTrue(DragonEggRules.grudgeActive(6999L, 7000L));
    assertFalse(DragonEggRules.grudgeActive(7000L, 7000L));
}
@Test void carryingTheEggKeepsTheGrudgeAlive() {
    assertEquals(11000L, DragonEggRules.renewedExpiry(5000L, 7000L, true, 50.0 * 50.0));
    assertEquals(7000L, DragonEggRules.renewedExpiry(5000L, 7000L, false, 50.0 * 50.0));
    assertEquals(7000L, DragonEggRules.renewedExpiry(5000L, 7000L, true, 97.0 * 97.0));
    assertEquals(99999L, DragonEggRules.renewedExpiry(100L, 99999L, true, 0.0)); // never shortens
}
@Test void theDragonPursuesOnlyInsideItsRangeAndWindow() {
    assertTrue(DragonEggRules.pursues(100L, 200L, 96.0 * 96.0));
    assertFalse(DragonEggRules.pursues(100L, 200L, 96.5 * 96.5));
    assertFalse(DragonEggRules.pursues(200L, 200L, 0.0));
}
@Test void onlyAWildIronbellyAdoptsANest() {
    assertFalse(DragonEggRules.adoptsNest(true, true, false));   // Gringotts, chained
    assertFalse(DragonEggRules.adoptsNest(false, true, false));  // Gringotts, freed
    assertFalse(DragonEggRules.adoptsNest(true, false, false));  // summoned (defaults to chained)
    assertTrue(DragonEggRules.adoptsNest(false, false, false));  // baked into the nest
    assertFalse(DragonEggRules.adoptsNest(false, false, true));  // already has one
}
@Test void aStrayedIronbellyHeadsHome() {
    assertTrue(DragonEggRules.shouldReturnHome(false, 13.0 * 13.0));
    assertFalse(DragonEggRules.shouldReturnHome(true, 13.0 * 13.0));
    assertFalse(DragonEggRules.shouldReturnHome(false, 11.0 * 11.0));
}
@Test void borginPaysMoreForTheRarerIronbellyEgg() {
    assertEquals(32, DragonEggRules.priceEmeralds(false));
    assertEquals(40, DragonEggRules.priceEmeralds(true));
}
```

- [ ] **Step 2:** Run `bash C:/Users/alonl/hp-ww-tools/test.sh <worktree> "com.hogwartsmagic.dragon.*"`. Expected: a compile failure (no `DragonEggRules`).
- [ ] **Step 3:** Implement `DragonEggRules` with the signatures above.
- [ ] **Step 4:** Rerun the same command. Expected: `TESTS OK`.

### Task 2: The two egg blocks, items, models, textures

**Files:**
- Create: `src/main/java/com/hogwartsmagic/block/DragonNestEggBlock.java`
- Modify (end of lists, shared): `ExampleMod.java` (BLOCKS, ITEMS, `MAIN_TAB` displayItems), `assets/hogwartsmagic/lang/en_us.json`, `data/minecraft/tags/block/mineable/pickaxe.json`, `data/hogwartsmagic/tags/block/bombarda_immune.json`
- Create: `assets/hogwartsmagic/blockstates/{horntail_egg,ironbelly_egg}.json`, `models/block/dragon_egg.json` (the shared parent), `models/block/{horntail_egg,ironbelly_egg}.json`, `models/item/{horntail_egg,ironbelly_egg}.json`, `data/hogwartsmagic/loot_table/blocks/{horntail_egg,ironbelly_egg}.json`, `data/hogwartsmagic/tags/block/dragon_eggs.json`, `data/hogwartsmagic/tags/item/dragon_eggs.json`, `tools/item_textures/dragon_eggs.py` → `textures/block/{horntail_egg,ironbelly_egg}.png` + `textures/item/{horntail_egg,ironbelly_egg}.png`

**Interfaces:**
- Produces: `ExampleMod.HORNTAIL_EGG`, `ExampleMod.IRONBELLY_EGG` (`RegistryObject<Block>`, ids `horntail_egg`, `ironbelly_egg`); `ExampleMod.HORNTAIL_EGG_ITEM`, `ExampleMod.IRONBELLY_EGG_ITEM` (`RegistryObject<Item>`, plain `BlockItem`); `DragonNestEggBlock.DRAGON_EGGS : TagKey<Block>` and `DragonNestEggBlock.DRAGON_EGG_ITEMS : TagKey<Item>` (both `hogwartsmagic:dragon_eggs`); `DragonNestEggBlock.SHAPE : VoxelShape`.

- [ ] **Step 1: `DragonNestEggBlock extends Block`**, modelled on `block/CrystalGobletBlock.java`. `SHAPE = Shapes.or(box(3,0,3,13,2,13), box(2,2,2,14,10,14), box(3,10,3,13,13,13), box(5,13,5,11,15,11))`, used for both `getShape` and collision. Override `appendHoverText(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)` to add the grey line `block.hogwartsmagic.dragon_egg.tooltip`. No ticking, no block entity, no gravity.
- [ ] **Step 2: Register.** Both use `Properties.of().strength(3.0F, 1200.0F).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)`. Horntail: `MapColor.COLOR_GRAY` and `SoundType.STONE`. Ironbelly: `MapColor.METAL` and `SoundType.METAL`. Any pickaxe drops the egg (no silk touch, no tier tag). By hand it is slow and drops nothing. Add both items to the end of `MAIN_TAB`.
- [ ] **Step 3: Data.** Loot tables drop the block itself under `minecraft:survives_explosion`. Add both ids to `dragon_eggs` (block and item tags), `mineable/pickaxe` and `bombarda_immune`. Lang: `"block.hogwartsmagic.horntail_egg": "Horntail Egg"`, `"block.hogwartsmagic.ironbelly_egg": "Ironbelly Egg"`, `"block.hogwartsmagic.dragon_egg.tooltip": "Long cold, and heavy as stone. Knockturn Alley pays for these."`, `"block.hogwartsmagic.dragon_egg.stolen": "Somewhere close, a dragon has your scent."`.
- [ ] **Step 4: Models.** `dragon_egg.json` has the four `SHAPE` boxes as elements, every face `"texture": "#egg"` with no `uv` (auto UV from the box, so the speckle is never stretched), and `"particle": "#egg"`. The child models only set `egg`. Each blockstate is one random-weighted variant list with y = 0/90/180/270, so the eggs in a clutch don't look copy-pasted. Item models are `item/generated` with `layer0 = hogwartsmagic:item/<id>`.
- [ ] **Step 5: Textures.** Load the `texturing-minecraft-items` skill and follow it. `tools/item_textures/dragon_eggs.py` uses `pixkit` the way `tools/item_textures/advanced_potion_making.py` does and writes all four PNGs at 16x16. Horntail: a speckled grey shell (base ramp around `#8b8d88`, charcoal specks `#3e403d`, a few rust flecks `#7a5a40`, clustered, not uniform noise). Ironbelly: a pale metallic grey (ramp around `#c3c7cb`, faint cooler mottling `#9da3aa`, one soft highlight band `#e4e8ec`). Item icons: an egg silhouette about 10 wide and 13 tall, lit from the top left, `outline(img, darken=0.30)`. Run `python tools/item_textures/dragon_eggs.py`, then the skill's `texlint.py` on all four files. Expected: no lint errors.
- [ ] **Step 6:** Run `bash C:/Users/alonl/hp-vfx-tools/compile.sh <worktree>`. Expected: `COMPILE OK`.

### Task 3: Theft response (grudge, target goal, wild Ironbelly nest) + GameTests

**Files:**
- Create: `src/main/java/com/hogwartsmagic/dragon/EggThiefTargetGoal.java`, `src/main/java/com/hogwartsmagic/dragon/DragonEggEvents.java`, `src/main/java/com/hogwartsmagic/gametest/DragonNestGameTests.java`
- Modify: `entity/HungarianHorntailEntity.java` (fields, save/load, `registerGoals`), `entity/UkrainianIronbellyEntity.java` (`registerGoals`, `tick`, save/load, a new private goal)

**Interfaces:**
- Consumes: `DragonEggRules.*` (Task 1), `DragonNestEggBlock.DRAGON_EGGS` / `DRAGON_EGG_ITEMS`, `ExampleMod.*_EGG(_ITEM)` (Task 2).
- Produces, on `HungarianHorntailEntity` (and so on the Ironbelly too):
  - `public void swearVengeance(ServerPlayer thief)`: sets the thief, `grudgeExpiry(gameTime)`, `setTarget(thief)`, and `beginRoar()` when `!isBusy() && !isAerial()`
  - `@Nullable public UUID eggThief()`, `public long eggGrudgeUntil()`, `public void renewEggGrudge(long until)`, `public void forgiveEggThief()`
  - NBT: `"EggThief"` (`putUUID`, only when set) and `"EggGrudgeUntil"` (long)
- On `UkrainianIronbellyEntity`: `@Nullable public BlockPos nest()`, NBT `"Nest"` (long).
- `public final class EggThiefTargetGoal extends Goal` with `EggThiefTargetGoal(HungarianHorntailEntity dragon)`, flags `EnumSet.of(Flag.TARGET)`, plus `@Nullable static ServerPlayer resolveThief(HungarianHorntailEntity dragon)`.
- `DragonEggEvents` (`@Mod.EventBusSubscriber(modid = ExampleMod.MODID)`): `onEggBroken(BlockEvent.BreakEvent)` and `static int alertDragons(ServerLevel level, BlockPos egg, ServerPlayer thief)`, which returns the number of dragons alerted.

- [ ] **Step 1: Write the failing GameTests** in `DragonNestGameTests` (`@GameTestHolder(ExampleMod.MODID)`, template `hogwartsmagic:gametest/empty3`, `batch = "dragon_nest"`, each test synchronous and ending in `helper.succeed()`). Put a stone floor at y=1, give the player an iron pickaxe, and spawn each dragon at relative `(1, 2, 9)` with `setNoAi(true)`. Copy a private `survivalPlayer(helper, name)` / `leave(helper, player)` pair from `LeftoversGameTests.survivalPlayerWithDarkness` (drop the darkness line), because the helper's mock player is creative.
  - `breakingAHorntailEggSetsTheDragonOnTheThief`: egg at `(1,2,1)`. Assert `dragon.getTarget() == null`, then `player.gameMode.destroyBlock(helper.absolutePos(egg))`. Then assert `dragon.getTarget() == player`, `player.getUUID().equals(dragon.eggThief())`, `dragon.eggGrudgeUntil() == level.getGameTime() + 6000`, the block is air, and a `HORNTAIL_EGG_ITEM` `ItemEntity` lies within 3 blocks.
  - `aCreativePlayerTakesAnEggWithoutAGrudge`: same setup but `player.setGameMode(GameType.CREATIVE)`. Assert `dragon.getTarget() == null` and `dragon.eggThief() == null`.
  - `theWildIronbellyHuntsTheThiefAndKeepsItsNest`: Ironbelly freed through NBT (`addAdditionalSaveData` into a tag, `putBoolean("Chained", false)`, `readAdditionalSaveData`), then `dragon.tick()` once. Assert `dragon.nest().equals(dragon.blockPosition())` and `dragon.anchor() == null`. Break an Ironbelly egg and assert `dragon.getTarget() == player`.
  - `theGringottsIronbellyNeverTakesANest`: `setAnchor(dragon.blockPosition())`, tick 5 times. Assert `dragon.nest() == null` and `dragon.isChained()`.
  - `aThiefWhoLeftIsNoLongerHunted`: after a theft, `leave(helper, player)`. Assert `EggThiefTargetGoal.resolveThief(dragon) == null` and `dragon.eggThief()` still equals the old UUID (the grudge outlives the log-out).
- [ ] **Step 2:** Run `bash C:/Users/alonl/hp-vfx-tools/compile.sh <worktree>`. Expected: compile errors for the missing members (GameTests themselves run only at the architect's merge).
- [ ] **Step 3: The grudge on `HungarianHorntailEntity`.** Add the fields, accessors, `swearVengeance` and NBT above. In `registerGoals` add `this.targetSelector.addGoal(0, new EggThiefTargetGoal(this));`.
- [ ] **Step 4: `EggThiefTargetGoal`.** `resolveThief` takes the dragon's `ServerLevel#getPlayerByUUID` (null when absent, so another dimension or logged out) and requires a `ServerPlayer` that is alive and passes `raisesGrudge`. `canUse`: there is a thief, `resolveThief != null`, and `pursues(now, until, distSqr)`. On `start` and every `tick`: if `getTarget() != thief`, then `setTarget(thief)`. Every 20 ticks: `renewEggGrudge(renewedExpiry(now, until, thief.getInventory().contains(DRAGON_EGG_ITEMS), distSqr))`. `canContinueToUse`: the same as `canUse`, except a thief who `isDeadOrDying()` calls `forgiveEggThief()` and returns false. `stop` does nothing (the vanilla target goals take over again).
- [ ] **Step 5: `DragonEggEvents.onEggBroken`.** Ignore the event unless the level is a `ServerLevel`, the state `is(DRAGON_EGGS)`, the player is a `ServerPlayer` and `raisesGrudge(...)` holds. `alertDragons` loops over `getEntitiesOfClass(HungarianHorntailEntity.class, new AABB(egg).inflate(THEFT_RADIUS))` (this includes Ironbellies, chained or wild; either species defends either egg). Each one that `isAlive()` and `notices(distanceToSqr(center))` gets `swearVengeance(thief)`. If the count is above 0, send `block.hogwartsmagic.dragon_egg.stolen` to the thief's action bar in gold. `IronbellyEvents.onBreak` already makes Ironbellies hear the break; leave it alone.
- [ ] **Step 6: The wild Ironbelly.** In its `registerGoals` add `targetSelector.addGoal(0, new EggThiefTargetGoal(this))` and `goalSelector.addGoal(5, new ReturnToNestGoal(this))`. In server `tick()`: when `DragonEggRules.adoptsNest(isChained(), anchor != null, nest != null)` holds, set `nest = blockPosition().immutable()`. Whenever `nest` is set (here or on load), call `restrictTo(nest, (int) NEST_RADIUS)` so `RoamWhenFreeGoal`'s `DefaultRandomPos` stays near the clutch. The private `ReturnToNestGoal` has `Flag.MOVE`. `canUse` needs `nest != null`, `!isCowering()`, `!recentlyHeard()` and `shouldReturnHome(getTarget() != null, distanceToSqr(center of nest))`; `start` calls `moveTo(nest, 0.6D)`; `canContinueToUse` is `!navigation.isDone() && getTarget() == null`. Save and load `"Nest"`. Do not touch the chain, anchor or `free()` code.
- [ ] **Step 7:** Run `compile.sh` (expected `COMPILE OK`) and then `test.sh <worktree> "com.hogwartsmagic.dragon.*"` (expected `TESTS OK`).

### Task 4: Real eggs in the lair and the camp, and the new Ironbelly nest

**Files:**
- Modify: `build_scripts/generate_biome_structures.py`: `egg()` and its two callers (lair clutch around line 1187, `egg_sledge` at 1517); a `# --- Dragon nests ---` block holding the nest builder and audits, placed just before `BUILDS`; `'ironbelly_nest': ironbelly_nest` at the end of `BUILDS`; one line `ENTITY_BOX['hogwartsmagic:ukrainian_ironbelly'] = (3.6, 3.8)  # Dragon nests` right after `ENTITY_BOX`; two lines in the `__main__` audit chain
- Create: `data/hogwartsmagic/worldgen/structure/ironbelly_nest.json`, `worldgen/structure_set/ironbelly_nest.json`, `worldgen/template_pool/ironbelly_nest.json`, `tags/worldgen/biome/has_structure/ironbelly_nest.json`; regenerated `structure/{horntail_nest,dragon_poacher_camp,ironbelly_nest}.nbt`

**Interfaces:**
- Consumes: the block ids `hogwartsmagic:horntail_egg` and `hogwartsmagic:ironbelly_egg` (Task 2); the `Chained` / `PersistenceRequired` NBT read by the Ironbelly (Task 3).
- Produces: `egg(b, x, y, z, kind='horntail')` places `'hogwartsmagic:%s_egg' % kind` as one block (no wall post). Also `ironbelly_nest() -> (blocks, entities)`, `audit_dragon_eggs(name, blocks) -> list[str]` and `audit_ironbelly_nest(blocks, entities) -> list[str]`.

- [ ] **Step 1: Audits first.** `EGG_CLUTCHES = {'horntail_nest': ('hogwartsmagic:horntail_egg', 5), 'dragon_poacher_camp': ('hogwartsmagic:horntail_egg', 1), 'ironbelly_nest': ('hogwartsmagic:ironbelly_egg', 4)}`. `audit_dragon_eggs` reports: a build not in the dict that holds an egg; a count different from the expected one; an egg whose block below is missing, in `NOT_SUPPORT`, `cave_air` or `snow`; and anything other than air or cave_air directly above an egg (catches a leftover `tuff_wall` / `blackstone_wall`). `audit_ironbelly_nest` reports: anything other than exactly one `hogwartsmagic:ukrainian_ironbelly`; its NBT lacking `Chained == ('byte', 0)` or `PersistenceRequired == ('byte', 1)`; the NBT containing `Anchor`; any egg nearer than 5 or further than 10 blocks (horizontally) from the dragon. Wire them into `__main__`: `problems += audit_dragon_eggs(name, blocks)` for every build, and `if name == 'ironbelly_nest': problems += audit_ironbelly_nest(blocks, entities)`.
- [ ] **Step 2:** Run `python build_scripts/generate_biome_structures.py`. Expected: `horntail_nest` and `dragon_poacher_camp` report PROBLEMS (count 0, eggs missing), exit 1.
- [ ] **Step 3: Real eggs.** Rewrite `egg()` as above and update its docstring. The lair loop becomes `egg(b, ex, G + 1, ez)` (all five become Horntail eggs, the gold-flecked one included); `egg_sledge` becomes `egg(b, 23, G + 3, 12)`, the caged stolen egg, now real and takeable once you break the bars.
- [ ] **Step 4: `ironbelly_nest()`.** Constants `NEST_SINK = 3`, `NEST_G = NEST_SINK + 1`, `NEST_W = 29`, `NEST_C = (14, 14)`. The ground palette stays inside the peaks' own ground, in coherent patches, never a one-block mix: `SCREE = [('minecraft:stone', 6), ('minecraft:andesite', 3), ('minecraft:cobblestone', 1)]`, snow-block drifts where `vnoise > 0.45` on the outer ring (`r > 7`), and a `minecraft:tuff` clutch bed so the pale eggs read. The footprint is a ragged disc (`r <= 11.5 + 2.5 * vnoise(x, z, 4, 1701)`) with a plug tapering with depth like `lair_ground`. The scrape is a dish with its floor one row down (stand level `NEST_G - 1`) for `r <= 6.5`. Inside `r <= 9.5`, carve `cave_air` up to `NEST_G + 5`, so a mountainside never sits inside the dragon. Ring the rim at `r` 7–9 with 2–3-high boulder clumps of SCREE, with gaps on the east and south. Add four parallel gravel claw gouges, four blocks long, at the dish edge, and a few bone blocks (its last meal) outside the west rim. Place four eggs at `(13,11)`, `(15,11)`, `(14,10)`, `(12,12)` at `NEST_G - 1`, with a few stone slabs heaped against them. Return the entity `entity(14.5, float(NEST_G - 1), 17.5, 'hogwartsmagic:ukrainian_ironbelly', {'PersistenceRequired': ('byte', 1), 'Chained': ('byte', 0), 'Rotation': ('list', 'float', [('float', 0.0), ('float', 0.0)])})`, facing south out of the scrape.
- [ ] **Step 5: Worldgen JSON.** Copy `horntail_nest.json` but set `biomes "#hogwartsmagic:has_structure/ironbelly_nest"`, `start_height.absolute -3`, and **no `spawn_overrides`**: a spawned Ironbelly defaults to chained, which would leave it standing guard over an anchor it doesn't have. The structure set is `random_spread`, spacing 36, separation 14, salt `741224004` (grep `worldgen/structure_set` to make sure no other file uses it). The biome tag holds `minecraft:jagged_peaks` and `minecraft:frozen_peaks`. The template pool copies `horntail_nest`'s.
- [ ] **Step 6:** Rerun the generator. Expected: every build prints `ok`, including `ironbelly_nest` with 1 entity; exit 0.
- [ ] **Step 7: Look at it.** Use the `rendering-minecraft-structures` skill on `ironbelly_nest`, `horntail_nest` and `dragon_poacher_camp`. The clutch must read as eggs from the scrape's entry, the boulders as a scrape (not a wall), and the one-block lair eggs must still show against the magma (if they vanish, raise the clutch bed one slab, not back to two blocks). Fix and rerender until right.

### Task 5: Borgin buys eggs; final verification and commit

**Files:**
- Modify: `entity/ModVillagers.java` (inside the `CURIO_DEALER` branch, at its end)

**Interfaces:**
- Consumes: `ExampleMod.HORNTAIL_EGG_ITEM`, `IRONBELLY_EGG_ITEM`, `DragonEggRules.priceEmeralds`.

- [ ] **Step 1: The trade.** Add ONE level-5 listing to the curio dealer: `(entity, random) -> { boolean iron = random.nextBoolean(); return new MerchantOffer(new ItemCost(iron ? IRONBELLY_EGG_ITEM.get() : HORNTAIL_EGG_ITEM.get(), 1), new ItemStack(Items.EMERALD, DragonEggRules.priceEmeralds(iron)), 2, 30, 0.05f); }`. It has to be a single listing. The Diagon Alley shopkeepers are baked at level 5 with no `Offers`, so vanilla rolls them two offers from the level-5 list, and a list of polyjuice plus one egg listing keeps both.
- [ ] **Step 2:** Run `compile.sh` (expected `COMPILE OK`), `test.sh <worktree> "com.hogwartsmagic.dragon.*"` (expected `TESTS OK`) and the generator (expected exit 0).
- [ ] **Step 3: Commit** one feature commit (write the message to a file, `git commit -F`). Subject: `Steal dragon eggs from Horntail lairs and new Ironbelly nests, at a price`; a short body; `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push.
- [ ] **Step 4: Report** (BUILDER_RULES format). Flag the shared files. Give `/locate structure hogwartsmagic:ironbelly_nest` and `/give @s hogwartsmagic:horntail_egg`. Note that already-generated lairs keep their tuff-wall eggs, and that Borgins whose offers already exist won't gain the trade.
