# Dark Wizard Hideouts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A rare underground Death Eater hideout under a collapsed hut, whose hidden ritual room holds a named, buffed dark wizard guarding a cursed golden cup, a Horcrux that dies to the existing Horcrux destruction path, frees a soul and leaves a reward chest.

**Architecture:** The cup is a `HorcruxBlock` subclass that shares `HorcruxBlockEntity` (baked `Active:1b`, no owner), so the existing paths (Fiendfyre rank 3 `devourHorcruxes`, and breaking it, which runs `HorcruxBlock.onRemove`) destroy it; the cup's own `onRemove` adds the soul and the reward. The leader is a plain baked `hogwartsmagic:wizard` with a tag, armed once by a join handler (attributes, name, home) and held near the altar by a leash goal. The build is one `dry_jigsaw` template written by `generate_biome_structures.py`, audited for reachability with the concealed wall open and shut.

**Tech Stack:** Forge 51 / Minecraft 1.21 / Java 21 (Mojang mappings), JUnit 5, Forge GameTest, Python 3 generator.

**Spec:** `docs/superpowers/specs/2026-11-24-wizarding-world-additions-design.md`, section 5. Read `C:/Users/alonl/hp-ww-tools/BUILDER_RULES.md` first: it overrides anything here.

## Global Constraints

- Worktree `C:/Users/alonl/hp-wizard-world`, branch `wizard-world`, base sha `a8f933fd`. Check with `git -C C:/Users/alonl/hp-wizard-world log --oneline -1`.
- Never run `gradlew`. Compile: `bash C:/Users/alonl/hp-vfx-tools/compile.sh C:/Users/alonl/hp-wizard-world` (timeout 600000) must print `COMPILE OK`. JUnit: `bash C:/Users/alonl/hp-ww-tools/test.sh C:/Users/alonl/hp-wizard-world "<pattern>"` must print `TESTS OK`. GameTests are run by the architect.
- Biomes: `minecraft:dark_forest`, `minecraft:swamp`, `minecraft:mangrove_swamp`, `hogwartsmagic:forbidden_forest`, `hogwartsmagic:dementor_fen`. Spacing 30 chunks.
- Rooms 10–20 blocks below the surface: cells with Inferi, a potion lab, a library, a ritual room with an altar behind a concealed wall. Cursed Glyph tiles in the corridor.
- Server/client safety: nothing in this feature touches `client/` or `net.minecraft.client.*`. All world edits on the server thread.
- No new packets, no entity event bytes 21/35/63, no worldgen `Feature`, carve interiors with `minecraft:cave_air`.
- Shared-file additions go at the END of each list under a `// Dark wizard hideouts` (or `# --- Dark wizard hideouts`) comment. The three edits to existing logic (Task 2 step 4, Task 2 step 5, Task 3 step 4) are one line each and must be flagged in the final report.
- Loot may name `hogwartsmagic:old_portkey`. It comes from the Portkeys branch, which the architect merges first; until then a datapack load rejects the tables that name it. Do not create that item here.
- British spelling, Javadoc that says what the player sees. Commit in the worktree after each task (`git commit -F file`, ending `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`). Do not push or merge.

## Review Focus

1. **Chunk reload of the leader**: arming runs once (a persistent flag); a reload must not heal him to full or add a second leash goal. Test: `leaderArmsOnceAndIsLeashed` (Task 3).
2. **An unbound cup** (`/setblock` without NBT, or one already spent) must free nothing and pay nothing when broken or replaced. Test: `unboundCupFreesNothing` (Task 2).
3. **Reward spot occupied**: Fiendfyre leaves fire where the cup stood; a player may have put a block there. Fire (replaceable) gives way to the chest; a solid block makes the reward spill as items. Tests: `fiendfyreDevoursTheCupAndFreesTheSoul`, `occupiedSpotSpillsTheReward` (Task 2).
4. **The player's own Horcrux must still burn**: widening the Fiendfyre check must not break it. The Fiendfyre test arms a plain `hogwartsmagic:horcrux` beside the cup and asserts both go (Task 2).
5. **Baked wizards during worldgen**: `WizardEntity.finalizeSpawn` runs with `MobSpawnType.STRUCTURE` on a worldgen thread and has a 5% chance to `addFreshEntity` a broom into the live level, plus a 10% raid-leader roll. Exclude `STRUCTURE` from the broom roll (Task 3 step 4); the join handler clears the raid-leader flag and banner on the leader. Reviewed by reading, not tested.

---

### Task 1: Hideout rules (pure)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/hideout/HideoutRules.java`
- Test: `src/test/java/com/hogwartsmagic/hideout/HideoutRulesTest.java`

**Interfaces:**
- Produces (all `public static final` / `public static`, no Minecraft imports):
  - `String LEADER_TAG = "hogwartsmagic:hideout_leader"`, `String HOME_KEY = "hogwartsmagic:hideout_home"`, `String ARMED_KEY = "hogwartsmagic:hideout_armed"`
  - `double LEADER_MAX_HEALTH = 80.0`, `LEADER_ARMOR = 8.0`, `LEADER_KNOCKBACK_RESISTANCE = 0.5`, `LEADER_FOLLOW_RANGE = 24.0`
  - `double LEASH_RADIUS = 9.0` (start walking home beyond this), `double HOME_RADIUS = 2.5` (stop once this close), `int LEASH_REPATH_TICKS = 20`
  - `int SOUL_RISE_BLOCKS = 12`, `int REWARD_DELAY_TICKS = 30`, `int FREED_MESSAGE_RADIUS = 16`
  - `String VAULT_LOOT = "chests/dark_hideout_vault"` (path under the mod namespace)
  - `List<String> LEADER_NAMES` = exactly `"Corvin Mortlake", "Aldous Grimsditch", "Morwenna Vex", "Silas Blackwood", "Theron Ashcombe", "Hester Grimwald"`
  - `static String leaderName(long seed)` — deterministic pick; mix the seed (e.g. `Long.hashCode(seed * 0x9E3779B97F4A7C15L)`) then `Math.floorMod(.., size)`.
  - `static boolean shouldHeadHome(double distSqFromHome, boolean alreadyReturning)` — `alreadyReturning ? d > HOME_RADIUS² : d > LEASH_RADIUS²`.

- [ ] **Step 1: Write the failing tests** (JUnit 5, style of `locket/InferiusRulesTest`)

```java
@Test void leaderNameIsStable() { assertEquals(HideoutRules.leaderName(42L), HideoutRules.leaderName(42L)); }
@Test void everyNameIsReachableAndNoneBlank() {
    Set<String> seen = new HashSet<>();
    for (long s = 0; s < 2000; s++) { String n = HideoutRules.leaderName(s * 7919L - 1000L); assertFalse(n.isBlank()); seen.add(n); }
    assertEquals(new HashSet<>(HideoutRules.LEADER_NAMES), seen);
}
@Test void leaderStaysPutInsideTheLeash() { assertFalse(HideoutRules.shouldHeadHome(0.0, false)); assertFalse(HideoutRules.shouldHeadHome(5 * 5, false)); }
@Test void leaderTurnsBackBeyondTheLeash() { assertTrue(HideoutRules.shouldHeadHome(10 * 10, false)); }
@Test void returningLeaderWalksAllTheWayHome() { assertTrue(HideoutRules.shouldHeadHome(5 * 5, true)); assertFalse(HideoutRules.shouldHeadHome(2 * 2, true)); }
@Test void soulRisesClearOfTheRooms() { assertTrue(HideoutRules.SOUL_RISE_BLOCKS >= 10); }
```

- [ ] **Step 2: Run** `bash C:/Users/alonl/hp-ww-tools/test.sh C:/Users/alonl/hp-wizard-world "com.hogwartsmagic.hideout.*"` — expect compile FAIL (class missing).
- [ ] **Step 3: Implement `HideoutRules`** with the members above (final class, private constructor).
- [ ] **Step 4: Run the same command** — expect `TESTS OK`.
- [ ] **Step 5: Commit** — "Add the rules for dark wizard hideouts".

---

### Task 2: The cursed golden cup (a Horcrux on the existing path)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/block/CursedCupBlock.java`, `src/main/java/com/hogwartsmagic/hideout/CursedCup.java`, `src/main/java/com/hogwartsmagic/gametest/HideoutGameTests.java`
- Create: `assets/hogwartsmagic/blockstates/cursed_cup.json`, `assets/hogwartsmagic/models/block/cursed_cup.json`, `data/hogwartsmagic/loot_table/chests/dark_hideout_vault.json` (all under `src/main/resources/`)
- Modify (shared, append at end): `ExampleMod.java` (block registration), `lang/en_us.json`, `data/minecraft/tags/block/mineable/pickaxe.json`
- Modify (one line each, flag): `ExampleMod.java` `HORCRUX_BLOCK_ENTITY` valid blocks (~line 485); `entity/FiendfyreSerpentEntity.java` `devourHorcruxes` (~line 384)

**Interfaces:**
- Consumes: `HideoutRules.SOUL_RISE_BLOCKS`, `REWARD_DELAY_TICKS`, `FREED_MESSAGE_RADIUS`, `VAULT_LOOT`.
- Produces:
  - `ExampleMod.CURSED_CUP : RegistryObject<Block>` (id `cursed_cup`, no BlockItem: a placed cup would be inert).
  - `public class CursedCupBlock extends HorcruxBlock` with `public static final MapCodec<CursedCupBlock> CODEC`.
  - `public final class CursedCup`: `static void arm(ServerLevel level, BlockPos pos)` (makes the block entity bound, for GameTests), `static void release(ServerLevel level, BlockPos cup)`, `static void payOut(ServerLevel level, BlockPos cup)`.
  - Block entity NBT the generator bakes: `{"id": "hogwartsmagic:horcrux", "Active": 1b}`.

- [ ] **Step 1: Write the failing GameTests** in `HideoutGameTests` (`@GameTestHolder(ExampleMod.MODID)`, template `hogwartsmagic:gametest/empty9`, style of `DesertGameTests` / `LeftoversGameTests`; each ends with `helper.succeed()` and a `LOGGER.info("HIDEOUT-GAMETEST PASS <name>")`):
  - `fiendfyreDevoursTheCupAndFreesTheSoul` (timeout 120): cup at rel (2,1,2), armed; plain `HORCRUX` at rel (4,1,2) armed the same way via `CursedCup.arm`. A `FiendfyreSerpentEntity` with `configure(0, 1, 400, 400)` and `setDestroysHorcrux(true)` at rel (3.5, 2.5, 2.5). `succeedWhen`: neither block is still present; at least one `HorcruxSoulEntity` within 4 blocks of the cup was seen (record it in a flag on the first tick the cup is gone). Then `runAfterDelay(REWARD_DELAY_TICKS + 5)`: a `ChestBlockEntity` at the cup's position whose `getLootTable()` is `hogwartsmagic:chests/dark_hideout_vault`. Discard the serpent in `finally`.
  - `minedCupFreesTheSoulAndDropsNothing` (timeout 80): cup armed at rel (2,1,2); survival player (copy `survivalPlayerWithDarkness`'s construction, gamemode survival) holding a diamond pickaxe calls `player.gameMode.destroyBlock(cupPos)`. Assert block gone, a soul within 4 blocks, no `ItemEntity` within 3 blocks, and later the chest as above. Remove the player from the player list at the end.
  - `unboundCupFreesNothing` (timeout 60): cup placed with `setBlock` and NOT armed; `level.destroyBlock(pos, false)`. After `REWARD_DELAY_TICKS + 5`: no soul within 6 blocks, no chest at the spot.
  - `occupiedSpotSpillsTheReward` (timeout 60): call `CursedCup.payOut` with the spot already holding stone. Assert the stone is still there and at least one `ItemEntity` lies within 2 blocks.
- [ ] **Step 2: Compile** — expect FAIL (`CursedCup`, `CURSED_CUP` missing).
- [ ] **Step 3: Register the block.** At the end of the block list in `ExampleMod` under `// Dark wizard hideouts`: `CURSED_CUP = BLOCKS.register("cursed_cup", () -> new CursedCupBlock(Properties.ofFullCopy(Blocks.GOLD_BLOCK).strength(30.0f, 1200.0f).requiresCorrectToolForDrops().noOcclusion().noLootTable().lightLevel(s -> 6)))`. Hardness 30 is about six seconds with a diamond pickaxe while the leader fights; 1200 resistance keeps creepers and Bombarda off it. Append `"hogwartsmagic:cursed_cup"` to `mineable/pickaxe.json`.
- [ ] **Step 4: Share the block entity type (one-line edit, flag).** In `HORCRUX_BLOCK_ENTITY`'s builder add `, ExampleMod.CURSED_CUP.get()` after `HORCRUX.get()` (qualified, to dodge an illegal forward reference). `HorcruxBlock.getTicker` and the green-mote particles then work for the cup unchanged.
- [ ] **Step 5: Widen the Fiendfyre filter (one-line edit, flag).** In `FiendfyreSerpentEntity.devourHorcruxes` replace `state.is(ExampleMod.HORCRUX.get())` with `state.getBlock() instanceof HorcruxBlock`. Nothing else in that method changes: it still needs `isActive() || isPrimed()`, still calls `TerrainRules.canBreak` last, still `destroyBlock(pos, false)`. `ModEvents.onHorcruxBreak` is left alone: with no owner, its Dementor never fires, and `HorcruxBlock.onRemove` already runs `handleForcedDestruction`.
- [ ] **Step 6: Implement `CursedCupBlock`.** It overrides `codec()`, `getShape`/`getOcclusionShape` (`Block.box(4, 0, 4, 12, 10, 12)`), `useWithoutItem` (action-bar `message.hogwartsmagic.cursed_cup.status` on the server), and `onRemove`. `onRemove` does this: if `!state.is(newState.getBlock())`, the level is a `ServerLevel`, and the block entity is a `HorcruxBlockEntity` with `isActive() || isPrimed()`, call `CursedCup.release(level, pos)` BEFORE `super.onRemove` (super clears the flags). Drops stay empty through the inherited `getDrops`. Class Javadoc: what the player sees (a small two-handled golden cup bleeding green motes; only breaking it or Fiendfyre ends it).
- [ ] **Step 7: Implement `CursedCup`.**
  - `arm` loads `{Active:1b}` into the block entity with `loadCustomOnly(tag, level.registryAccess())`, then `setChanged()`.
  - `release` does five things. It spawns `ModEntities.HORCRUX_SOUL.get().create(level)` at the cup centre with `setTarget(cup.above(SOUL_RISE_BLOCKS))` and a blank player name (the renderer then draws a nameless dark silhouette that rises through the ceiling and is discarded on arrival). It plays `ModSounds.HORCRUX_SCREAM` (HOSTILE) and `HORCRUX_SHATTER`. It sends a burst of `MagicParticles.SMOKE_GREEN`/`SMOKE_DARK`. It calls `Fx.shake(level, centre, 0.6F, 12.0F, 12)`. It sends `message.hogwartsmagic.cursed_cup.freed` to players within `FREED_MESSAGE_RADIUS`. Finally it calls `CurseScheduler.after(REWARD_DELAY_TICKS, () -> payOut(level, cup))`.
  - `payOut`: if the spot `canBeReplaced()`, place a chest facing the nearest player within 16 blocks (default south) and `setLootTable(ResourceKey VAULT_LOOT, level.random.nextLong())`, with an `ENCHANT` particle burst and `AMETHYST_BLOCK_RESONATE`. Otherwise roll the same table (`LootContextParamSets.CHEST`, origin = spot centre) and `Block.popResource` each stack at the spot.
- [ ] **Step 8: Resources.**
  - Blockstate with `lit=false` and `lit=true`, both mapping to `hogwartsmagic:block/cursed_cup`.
  - Model: parent `block/block`, textures `gold` = `minecraft:block/gold_block`, `inside` = `minecraft:block/raw_gold_block`, `particle` = gold. Elements: a foot 6×1×6, a stem 2×3×2, a bowl 8×5×8 open on top with `#inside` showing, and two handles 1×3×1 on the east and west sides. The whole thing stays inside the shape box.
  - Lang: `block.hogwartsmagic.cursed_cup` = "Cursed Golden Cup"; `message.hogwartsmagic.cursed_cup.status` = "The little golden cup is warm, and something inside it is watching you."; `message.hogwartsmagic.cursed_cup.freed` = "A shriek tears out of the cup, and something that was once a man flees through the stone."
  - `dark_hideout_vault.json` (type `minecraft:chest`). Pool 1, rolls 4–6: galleon w10 ×3–8, gold_ingot w8 ×2–5, emerald w5 ×1–3, diamond w4 ×1–2, cursed_galleon w3, `hogwartsmagic:rare_tome_of_mastery` w2, `hogwartsmagic:epic_tome_of_mastery` w1. Pool 2: `hogwartsmagic:old_portkey` at chance 0.35. Pool 3: enchanted_golden_apple at chance 0.05.
- [ ] **Step 9: Compile** — expect `COMPILE OK`. Reason through each GameTest against the code (the architect runs them).
- [ ] **Step 10: Commit** — "Add the cursed golden cup, a Horcrux that Fiendfyre or a pickaxe can end".

---

### Task 3: The hideout leader

**Files:**
- Create: `src/main/java/com/hogwartsmagic/hideout/HideoutLeader.java`
- Modify: `gametest/HideoutGameTests.java` (add one test)
- Modify (one line, flag): `entity/WizardEntity.java` `finalizeSpawn` broom roll (~line 431)

**Interfaces:**
- Consumes: every `HideoutRules.LEADER_*` and `LEASH_*` constant, `HOME_KEY`, `ARMED_KEY`, `leaderName`, `shouldHeadHome`.
- Produces: `@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = FORGE) public final class HideoutLeader` with `@SubscribeEvent static void onJoin(EntityJoinLevelEvent e)`, `public static void prepare(WizardEntity wizard)` (what `onJoin` calls; public for the test), and `static final class GuardAltarGoal extends Goal`.

- [ ] **Step 1: Write the failing GameTest** `leaderArmsOnceAndIsLeashed` (timeout 40). Create a `ModEntities.WIZARD` wizard, `addTag(HideoutRules.LEADER_TAG)`, `setRaidLeader(true)`, put a white banner on its head, place it at rel (4,1,4), `addFreshEntity`. Assert:
  - max health is 80 and current health is 80;
  - armour is 8;
  - it has a custom name from `LEADER_NAMES`;
  - `isRaidLeader()` is false and its head slot is empty;
  - `hasRestriction()` is true, centred on its spawn block, radius 9;
  - exactly one `GuardAltarGoal` in `goalSelector.getAvailableGoals()`.
  Then `setHealth(30)` and call `HideoutLeader.prepare(wizard)` again: health is still 30 and there is still exactly one `GuardAltarGoal`. Then `moveTo` it 12 blocks off and tick-wait 5: its navigation is not done, and its target position is within 3 blocks of home. Discard it at the end.
- [ ] **Step 2: Compile** — expect FAIL.
- [ ] **Step 3: Implement `HideoutLeader`.**
  - `onJoin` handles only the server side, only a `WizardEntity` whose `getTags()` holds `LEADER_TAG`; it calls `prepare`.
  - `prepare` does the following once, when `ARMED_KEY` is absent from `getPersistentData()`:
    - set the base values of MAX_HEALTH, ARMOR, KNOCKBACK_RESISTANCE and FOLLOW_RANGE;
    - `setHealth(getMaxHealth())`;
    - store `HOME_KEY = blockPosition().asLong()`;
    - `setCustomName(Component.literal(leaderName(home)))`, with the custom name not always visible;
    - `setRaidLeader(false)`, and clear the HEAD slot if it holds a banner;
    - `setPersistenceRequired()`;
    - put `ARMED_KEY`.

    Then, every time: `restrictTo(home, (int) LEASH_RADIUS)`, and add `new GuardAltarGoal(wizard)` at priority 1 unless one is already present. Priority 1 outranks `WizardSpellCastGoal` at 2, so he casts from the room but never chases up the stair.
  - `GuardAltarGoal` takes flags `MOVE`. `canUse` is `shouldHeadHome(d, false)` and `canContinueToUse` is `shouldHeadHome(d, true)`, where `d` is the squared distance to the home block's centre. `start` and every `LEASH_REPATH_TICKS` call `getNavigation().moveTo(home centre, 1.15)`. `stop` calls `getNavigation().stop()`.
- [ ] **Step 4: Stop worldgen brooms (one line, flag).** In `WizardEntity.finalizeSpawn`'s broom condition add `&& p_33284_ != MobSpawnType.STRUCTURE`, next to the existing `CHUNK_GENERATION` guard and with the same comment's reason: a template's entities are finalised on a worldgen thread, and the broom would be added straight into the live level.
- [ ] **Step 5: Compile** — expect `COMPILE OK`. Re-run Task 1's JUnit pattern — `TESTS OK`.
- [ ] **Step 6: Commit** — "A named dark wizard now guards a hideout's altar".

---

### Task 4: The hideout structure

**Files:**
- Modify (shared, one block under `# --- Dark wizard hideouts`, placed after `audit_yeti_cave`): `build_scripts/generate_biome_structures.py`. Also add `'dark_hideout': dark_hideout,` at the end of `BUILDS` and `if name == 'dark_hideout': problems += audit_dark_hideout(blocks, entities)` at the end of the audit chain in `__main__`.
- Create (generated): `src/main/resources/data/hogwartsmagic/structure/dark_hideout.nbt`
- Create: `data/hogwartsmagic/worldgen/structure/dark_hideout.json`, `worldgen/structure_set/dark_hideout.json`, `worldgen/template_pool/dark_hideout.json`, `tags/worldgen/biome/has_structure/dark_hideout.json`, `loot_table/chests/dark_hideout_cells.json`, `dark_hideout_lab.json`, `dark_hideout_library.json`

**Interfaces:**
- Consumes: the existing helpers `put`, `fill`, `stair`, `slab`, `tex`, `chest`, `entity`, `cursed` (cursed glyph + `tomb_marker`), `GLYPH`, `LOCKET_ROCK`, `dark_mark_banner`, and the global `audit`. Do not edit any of them.
- Produces: `dark_hideout() -> (blocks, entities)`, `hideout_walk(blocks, start, open_wall) -> set`, `audit_dark_hideout(blocks, entities) -> list[str]`, plus `hollow(b, air, x0, y0, z0, x1, y1, z1)` (like `carve` but `minecraft:cave_air`) and `hideout_shell(b, air)` (like `shell`, using `HIDE_G` and a deepslate palette).

**Layout** (template coordinates, x and z run 0..32; interiors at the standing level `F+1`, 4 high):

| Constant / room | Value |
|---|---|
| `HIDE_W = HIDE_D` | 33 |
| `HIDE_G` | 20 — first air level above ground; JSON `start_height` is `-20` |
| `HIDE_F` | 4 — room floor block; stand at 5, so the rooms are 15 below the surface |
| Hut (surface) | walls x 11..21, z 11..21, floor at G-1; door gap in the south wall at x=16; trapdoor (`spruce_trapdoor`, `half=top`, closed) at (14, G-1, 19) over the stairwell |
| Stairwell | x 13..18, z 19..31, two flights of at most 16 steps; the foot opens north into the corridor at x 16..17 |
| Corridor | x 3..29, z 15..17. Floor: a `GLYPH` runner, 3 wide, with ≥5 `cursed(...)` tiles among it (mix RISEN, WITHERING, COLLAPSE). None on the stair-foot cells; one within 3 blocks of the concealed wall |
| Cells (north-west) | three cells inside x 3..11, z 4..13. Fronts are iron bars in the corridor's north wall (z=14), with one bar missing per cell. One Inferius each, plus a barrel or chest `chests/dark_hideout_cells` |
| Library (north) | x 13..21, z 4..13: bookshelves, chiseled bookshelves, a lectern, cobwebs, chest `chests/dark_hideout_library`; doorway at z=14 |
| Potion lab (south-west) | x 3..11, z 19..29: brewing stands, cauldrons, barrels, a crafting shelf of candles, chest `chests/dark_hideout_lab`; doorway at z=18 |
| Ritual room (north-east) | x 23..29, z 3..13. Altar (polished blackstone + crying obsidian) at (26, F+1, 4), cup at (26, F+2, 4). Dark Mark banner on the north wall, candles, soul lanterns. Leader baked at (26.5, F+1, 8.5) facing north |
| Concealed wall | `hogwartsmagic:concealed_rock` (`revealed=false`) at x 25..27, y F+1..F+3, z=14. The corridor's north wall within 3 blocks of it uses `LOCKET_ROCK` so the patch does not stand out |

The hut is shabby and half collapsed: spruce/dark-oak log frame, plank walls with gaps, a mossy cobblestone foundation course, and a roof whose north-east third has fallen in (stairs and slabs scattered on the floor). Cobwebs, a broken bed, and one lantern on a fence post outside. Underground walls: deepslate bricks, cracked deepslate bricks and cobbled deepslate; floors of deepslate tiles outside the glyph runner. Light with soul lanterns and candles only; keep it dim.

Baked entities, all with `PersistenceRequired: 1b`: three `hogwartsmagic:inferius` (one per cell), and one `hogwartsmagic:wizard` with `Tags: ('list', 'string', [('string', 'hogwartsmagic:hideout_leader')])` and `Rotation` facing the wall. Cup block entity: `{'id': 'hogwartsmagic:horcrux', 'Active': ('byte', 1)}`.

- [ ] **Step 1: Write `audit_dark_hideout` first.** It reports a problem string for each of these:
  - **depth:** `10 <= HIDE_G - (HIDE_F + 1) <= 20` fails;
  - **open wall:** with `open_wall=True` from start `(16, HIDE_G, 23)`, the walk reaches no cell of the ritual room box (stand level), the lab, the library, or each of the three cell interiors;
  - **shut wall:** with `open_wall=False`, the walk reaches any ritual-room cell, or fails to reach the lab, the library or the cells;
  - **glyphs:** there are fewer than 5 `hogwartsmagic:cursed_glyph` tiles, or any one lacks a `GLYPH` neighbour (the tomb audit's ten offsets);
  - **cup:** there is not exactly one `hogwartsmagic:cursed_cup`, it lacks `Active` byte 1, or the block under it is not solid;
  - **leader:** there is not exactly one wizard carrying the leader tag, or he stands outside the ritual box;
  - **Inferi:** fewer than 3 `hogwartsmagic:inferius` inside the cells box;
  - **chests:** each of `dark_hideout_cells`, `_lab` and `_library` is not present at least once.

  `hideout_walk` copies `tomb_walk`'s search (climb 1, drop up to 3, headroom check, kept inside the template box). Unset cells are solid below `HIDE_G` and open at or above it. These are passable: names containing `air`, `cobweb`, `_trapdoor`, `_carpet` or `candle`. `concealed_rock` is passable only when `open_wall` is set.
- [ ] **Step 2: Register `dark_hideout` with a stub builder** that returns only the hut. Run `python build_scripts/generate_biome_structures.py` from the worktree; expect `dark_hideout ... N PROBLEMS`, with the open-wall and shut-wall walk lines among them. That proves the audit can fail.
- [ ] **Step 3: Build it**: `hideout_hut`, `hideout_stair`, `hideout_corridor`, `hideout_cells`, `hideout_lab`, `hideout_library`, `hideout_ritual`, then `hideout_shell` (every unset cell next to a carved one, below `HIDE_G`, becomes deepslate, so an aquifer or cave never opens into the hideout), then the entities.
- [ ] **Step 4: Run the generator** — expect `dark_hideout ... ok` and every other build unchanged (`git status` shows only `dark_hideout.nbt` among the templates). Also run the global audits (chests facing air, lanterns hung, entities not embedded). They must pass.
- [ ] **Step 5: Render it** with the `rendering-minecraft-structures` skill: a surface view of the hut and a cut-away of the rooms. Fix what reads wrong (a hut that looks intact, the concealed patch visible from the corridor, rooms too bright). Re-run Step 4.
- [ ] **Step 6: Worldgen JSON.**
  - Structure: `type: hogwartsmagic:dry_jigsaw`, `biomes: #hogwartsmagic:has_structure/dark_hideout`, `step: surface_structures`, `terrain_adaptation: none`, `start_pool: hogwartsmagic:dark_hideout`, `size: 1`, `start_height: {absolute: -20}`, `project_start_to_heightmap: WORLD_SURFACE_WG`, `max_distance_from_center: 80`, `use_expansion_hack: false`. `spawn_overrides.monster`: `bounding_box: piece`, inferius w60 (1–2), wizard w25 (1–1).
  - Structure set: `random_spread`, spacing 30, separation 12, salt 741220061 (grep to confirm it is unused).
  - Template pool: a copy of `cursed_tomb`'s, pointing at `hogwartsmagic:dark_hideout`.
  - Biome tag: `minecraft:dark_forest`, `minecraft:swamp`, `minecraft:mangrove_swamp`, plus `{"id": "hogwartsmagic:forbidden_forest", "required": false}` and `{"id": "hogwartsmagic:dementor_fen", "required": false}`.
- [ ] **Step 7: Loot tables** (type `minecraft:chest`):
  - **cells:** rolls 2–4 of bone w10 ×1–3, rotten_flesh w8 ×1–3, iron_nugget w6 ×1–4, galleon w4 ×1–3, candle w3; then `old_portkey` at chance 0.05.
  - **lab:** rolls 3–5 of glass_bottle w10 ×1–3, mandrake_root w8, dittany w8, asphodel w8, floo_powder w6 ×1–4, arcane_dust w6 ×2–5, essence_of_dittany w4, polyjuice_potion w2, wolfsbane_potion w2 (all `hogwartsmagic:` except the bottle).
  - **library:** rolls 2–4 of paper w8 ×2–6, book w10 ×1–3, magic_book w6, book with `minecraft:enchant_randomly` w4, common_tome_of_mastery w4, uncommon_tome_of_mastery w2, advanced_potion_making w2; then `old_portkey` at chance 0.12.
- [ ] **Step 8: Compile** — `COMPILE OK` (the data does not compile, but nothing else may break). Validate every new JSON with `python -c "import json,sys;[json.load(open(f)) for f in sys.argv[1:]]" <files>`.
- [ ] **Step 9: Commit** — "Dark wizard hideouts: a collapsed hut over a Death Eater lair with a Horcrux in it".

---

### Task 5: Final check and report

- [ ] **Step 1:** `bash C:/Users/alonl/hp-vfx-tools/compile.sh C:/Users/alonl/hp-wizard-world` → `COMPILE OK`; `bash C:/Users/alonl/hp-ww-tools/test.sh C:/Users/alonl/hp-wizard-world "com.hogwartsmagic.hideout.*"` → `TESTS OK`; the generator exits 0.
- [ ] **Step 2: Dedicated-server read-through.** `grep -rn "client" src/main/java/com/hogwartsmagic/hideout src/main/java/com/hogwartsmagic/block/CursedCupBlock.java` finds nothing. `CurseScheduler` and `EntityJoinLevelEvent` work run on the server thread only.
- [ ] **Step 3: Final report** in the BUILDER_RULES format. Flag:
  - the three one-line edits (HORCRUX_BLOCK_ENTITY valid blocks, Fiendfyre `instanceof`, WizardEntity STRUCTURE);
  - the shared files touched;
  - the `old_portkey` dependency;
  - that `dry_jigsaw` rejects wet starts, so hideouts will be rare in swamps and very rare in mangrove swamps;
  - that the glyph runner is sandstone in a deepslate lair (the cursed tile only exists in a sandstone look).

  In-game checks: `/locate structure hogwartsmagic:dark_hideout`, `/place structure hogwartsmagic:dark_hideout`, and `/setblock ~ ~ ~ hogwartsmagic:cursed_cup{Active:1b}`.
