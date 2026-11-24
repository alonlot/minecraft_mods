# Hidden Wizard Houses Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A rare overgrown ruin in temperate biomes that Revelio, cast within 10 blocks, turns permanently into the complete wizard's cottage it was hiding, with a shimmer sweeping out from the caster, loot inside, and two traps.

**Architecture:** Three new blocks share one block entity type. A *phantom ruin* block (solid, looks like stone) becomes air on reveal; a *glamoured* block (invisible, no collision) and a *jinx ward* hold an optional real `BlockState` (+ optional real block-entity NBT) and become it on reveal. Revelio finds any of these block entities within 10 blocks, flood-fills the house over loaded block entities (pure BFS in `HiddenHouseRules`), and reveals each block on a tick set by its distance from the cast. The generator writes the house as two views (ruin, cottage) merged cell by cell; a structure processor rotates the stored real states when the jigsaw rotates the template.

**Tech Stack:** Forge 51 / MC 1.21 / Java 21 (Mojang mappings), JUnit 5, Python 3 template generator, ElevenLabs SFX.

**Spec:** `docs/superpowers/specs/2026-11-24-wizarding-world-additions-design.md` section 3; rules in `C:/Users/alonl/hp-ww-tools/BUILDER_RULES.md` (read fully first).

## Global Constraints

- Work only in your own worktree; first check `git -C <worktree> log --oneline -1` prints `a8f933fd`. Never run `gradlew`.
- Compile: `bash C:/Users/alonl/hp-vfx-tools/compile.sh <worktree>` (timeout 600000) must print `COMPILE OK`. JUnit: `bash C:/Users/alonl/hp-ww-tools/test.sh <worktree> "<pattern>"` must print `TESTS OK`. GameTests are run by the architect.
- Server/client: nothing outside `client/` touches `net.minecraft.client.*`; every world edit, hex and loot runs on the server. The glamoured block entity never syncs its real state to clients (no update packet).
- No `switch` on an enum in client code; no entity event bytes 21/35/63; effects through `Fx` kinds only.
- Shared files (`ExampleMod.java`, `FxKinds.java`, `ModSounds.java`, `sounds.json`, `en_us.json`, `RevelioSpell.revealHidden`, `MagicBookChestLoot`, creative tab list, `generate_biome_structures.py`, vanilla tag files): each addition is one compact block at the END of its list under a `// Hidden wizard houses` comment.
- Template rules: plain `air` leaves the world alone, interiors are `cave_air`; datapack folders singular; the build is a jigsaw template, never a Feature.
- Spec values: Revelio reach **10 blocks**; biomes **forest, birch_forest, dark_forest, taiga, plains, meadow**; spacing **28 chunks**; loot: spellbooks/tomes, brewing ingredients, Floo Powder, sometimes `hogwartsmagic:old_portkey`; jinx = **knockback + blindness, once**.
- Style: class Javadoc says what the player sees, British spelling, named commented constants. Commit per task in the worktree (`git commit -F file`, ends `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`). Never push or merge.

## Review Focus

1. **The jigsaw rotates the template** but not block-entity NBT, so a stored `facing` would point the wrong way and beds/doors would split. Expect: a rotated house reveals with whole beds and doors. Test: `rotatedHouseKeepsBedsAndDoorsWhole` (Task 7).
2. **A player or mob is standing inside an invisible wall** when the shimmer reaches it. Expect: that block waits until the cell is clear (bounded retries), nobody is entombed. Test: `revealWaitsForSomeoneStandingInAWall` (Task 4).
3. **Casting twice, or casting at a half-revealed house** (server stopped mid-sweep, chunk unloaded). Expect: the second cast finishes what is left, nothing duplicates, loaded chunks only. Test: `doubleCastRevealsOnceWithoutDuplicates` (Task 4).
4. **Creepers, TNT, withers, pistons, water against glamoured blocks.** Expect: nothing breaks, drops or flows in; the hidden chest's loot survives. Test: `glamourSurvivesExplosionsAndDropsNothing` (Task 2).
5. **`old_portkey` is missing at merge time.** Expect: the house chest table still loads; only the portkey sub-table is empty. Test: `hiddenHouseDataLoads` (Task 7).

---

### Task 1: Pure reveal rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/hiddenhouse/HiddenHouseRules.java`
- Test: `src/test/java/com/hogwartsmagic/hiddenhouse/HiddenHouseRulesTest.java`

**Interfaces:**
- Produces: constants `REVEAL_RADIUS = 10`, `LINK = 3` (max Chebyshev gap between blocks of one house), `MAX_HOUSE_SPAN = 24` (Chebyshev from the first seed), `MAX_HOUSE_BLOCKS = 2048`, `WAVE_SPEED = 0.8F` (blocks/tick), `MAX_DELAY = 40`, `OCCUPIED_RETRY_TICKS = 10`, `OCCUPIED_MAX_RETRIES = 12`, `JINX_PUSH = 1.6`, `JINX_BLIND_TICKS = 120`; `record Cell(int x, int y, int z)`; `static List<Cell> house(Set<Cell> candidates, double cx, double cy, double cz)`; `static int revealDelay(double distance)`; `static boolean isGlamourTarget(String blockId)`; `static int searchHalfWidth()` (= `REVEAL_RADIUS + MAX_HOUSE_SPAN`).

- [ ] **Step 1: Write the failing tests**

```java
@Test void nothingWithinTenBlocksRevealsNothing() {
    assertTrue(HiddenHouseRules.house(Set.of(new Cell(11, 0, 0)), 0.5, 0.5, 0.5).isEmpty()); }
@Test void floodReachesPastTheRadiusAlongConnectedBlocks() {
    Set<Cell> chain = new HashSet<>(); for (int x = 5; x <= 25; x += 2) chain.add(new Cell(x, 0, 0));
    assertEquals(chain.size(), HiddenHouseRules.house(chain, 0.5, 0.5, 0.5).size()); }
@Test void aGapWiderThanLinkSplitsHouses() {
    Set<Cell> s = Set.of(new Cell(5, 0, 0), new Cell(11, 0, 0));     // 11 is out of reach, gap 6 > LINK
    assertEquals(List.of(new Cell(5, 0, 0)), HiddenHouseRules.house(s, 0.5, 0.5, 0.5)); }
@Test void floodStopsAtSpanAndCap() {
    Set<Cell> line = new HashSet<>(); for (int x = 0; x <= 40; x++) line.add(new Cell(x, 0, 0));
    assertTrue(HiddenHouseRules.house(line, 0.5, 0.5, 0.5).stream().allMatch(c -> c.x() <= HiddenHouseRules.MAX_HOUSE_SPAN));
    Set<Cell> slab = new HashSet<>(); for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) for (int y = 0; y < 6; y++) slab.add(new Cell(x, y, z));
    assertEquals(HiddenHouseRules.MAX_HOUSE_BLOCKS, HiddenHouseRules.house(slab, 0.5, 0.5, 0.5).size()); }
@Test void revealDelayGrowsWithDistanceAndIsCapped() {
    assertEquals(0, revealDelay(0)); assertEquals(10, revealDelay(8.0)); assertEquals(11, revealDelay(8.1));
    assertEquals(HiddenHouseRules.MAX_DELAY, revealDelay(500)); }
@Test void glamourTargets() {
    for (String ok : List.of("minecraft:oak_planks", "minecraft:bookshelf", "minecraft:red_bed", "minecraft:chest",
            "minecraft:lantern", "minecraft:spruce_stairs", "minecraft:spruce_fence_gate", "minecraft:wall_torch",
            "minecraft:glass", "hogwartsmagic:wizard_table", "hogwartsmagic:wizard_cauldron"))
        assertTrue(isGlamourTarget(ok), ok);
    for (String no : List.of("minecraft:air", "minecraft:cave_air", "minecraft:water", "minecraft:oak_fence",
            "minecraft:glass_pane", "minecraft:cobblestone_wall", "minecraft:iron_bars", "minecraft:redstone_wire",
            "minecraft:spawner", "hogwartsmagic:glamoured_block", "hogwartsmagic:phantom_ruin",
            "hogwartsmagic:jinx_ward", "hogwartsmagic:cursed_glyph", "stone"))
        assertFalse(isGlamourTarget(no), no); }
```

- [ ] **Step 2: Run** `bash C:/Users/alonl/hp-ww-tools/test.sh <worktree> "com.hogwartsmagic.hiddenhouse.HiddenHouseRulesTest"` — expect compile failure.
- [ ] **Step 3: Implement `HiddenHouseRules`.** `house`: seeds are candidates whose centre (`x+0.5` …) lies within `REVEAL_RADIUS` of the cast, sorted by distance; BFS from all seeds over the `(2*LINK+1)^3` neighbour cube via `HashSet` lookups, skipping cells beyond `MAX_HOUSE_SPAN` (Chebyshev) of the seed nearest the cast, stopping at `MAX_HOUSE_BLOCKS`; returns BFS order. `revealDelay = min(ceil(d / WAVE_SPEED), MAX_DELAY)`. `isGlamourTarget`: false without a `:`; false for the exact set {air, cave_air, void_air, water, lava, bubble_column, fire, soul_fire, redstone_wire, tripwire, iron_bars, chorus_plant, spawner, trial_spawner, piston_head, moving_piston, nether_portal, end_portal, end_gateway (all `minecraft:`), `hogwartsmagic:` glamoured_block, phantom_ruin, jinx_ward, cursed_glyph, concealed_sandstone, concealed_rock}; false for ids ending `_fence`, `_pane`, `_wall` (their shape comes from neighbours, and the reveal does no shape updates). Javadoc says why.
- [ ] **Step 4: Run the tests** — `TESTS OK`.
- [ ] **Step 5: Commit** "Add the rules for revealing a hidden wizard house".

### Task 2: Phantom, glamoured and ward blocks

**Files:**
- Create: `block/PhantomLook.java` (enum `MOSSY_COBBLESTONE, COBBLESTONE, STONE_BRICKS`, `StringRepresentable`), `block/PhantomRuinBlock.java`, `block/GlamouredBlock.java`, `block/JinxWardBlock.java` (extends `GlamouredBlock`), `block/entity/HiddenHouseBlockEntity.java`, `gametest/HiddenHouseGameTests.java`
- Modify: `ExampleMod.java` (blocks, BE type, item, creative tab), `assets/hogwartsmagic/blockstates/{phantom_ruin,glamoured_block,jinx_ward}.json`, `models/block/glamoured.json` (particle texture only, `minecraft:block/glass`), `models/item/phantom_ruin.json`, `lang/en_us.json`, `data/minecraft/tags/block/mineable/pickaxe.json`, create `data/minecraft/tags/block/prevent_mob_spawning_inside.json`
- (paths under `src/main/java/com/hogwartsmagic/` and `src/main/resources/`)

**Interfaces:**
- Produces: `ExampleMod.PHANTOM_RUIN`, `GLAMOURED_BLOCK`, `JINX_WARD`, `PHANTOM_RUIN_ITEM`, `HIDDEN_HOUSE_BLOCK_ENTITY` (id `hidden_house`, valid for all three blocks); `PhantomRuinBlock.LOOK : EnumProperty<PhantomLook>`; on `HiddenHouseBlockEntity`: `@Nullable BlockState real()`, `@Nullable CompoundTag realNbt()`, `void setReal(@Nullable BlockState state, @Nullable CompoundTag nbt)`; NBT keys `"Real"` (written by `NbtUtils.writeBlockState`) and `"RealNbt"`.

- [ ] **Step 1: Write the GameTest** `glamourSurvivesExplosionsAndDropsNothing` (template `hogwartsmagic:gametest/empty9`): set a glamoured block at (4,1,4) holding `Blocks.CHEST` with `RealNbt {id:"minecraft:chest", LootTable:"hogwartsmagic:chests/hidden_house"}`; `level.explode(null, centre, 4.0F, Level.ExplosionInteraction.TNT)`; after 2 ticks assert the block is still `GLAMOURED_BLOCK`, its BE still has `real().is(Blocks.CHEST)` and `realNbt().getString("LootTable")` unchanged, and `getEntitiesOfClass(ItemEntity.class, box).isEmpty()`. Also assert `getCollisionShape(...).isEmpty()`, `state.getLightBlock(level, pos) == 0`, and `state.getPistonPushReaction() == PushReaction.BLOCK`.
- [ ] **Step 2: Implement the block entity.** `loadAdditional(tag, registries)` reads `Real` with `NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Real"))` (the level is not set yet when a template or chunk loads it), and `RealNbt` as a copy; `saveAdditional` writes both when present. No ticker, no update packet.
- [ ] **Step 3: Implement `GlamouredBlock`** (`implements EntityBlock, LiquidBlockContainer`): shape, collision, occlusion and visual shapes empty; `RenderShape.INVISIBLE`; `propagatesSkylightDown` true; Forge `canEntityDestroy` false; `getCloneItemStack` → `ItemStack.EMPTY`; `canPlaceLiquid`/`placeLiquid` false (copy `ConcealedRockBlock`). Registered properties: `of().noCollission().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F).pushReaction(PushReaction.BLOCK).isValidSpawn(never).isRedstoneConductor(never).isSuffocating(never).isViewBlocking(never)` — and **never** `.air()` or `.replaceable()` (trees and block placement would overwrite the house). `JinxWardBlock` adds nothing but its own Javadoc. No items for these two.
- [ ] **Step 4: Implement `PhantomRuinBlock`** (`EntityBlock`, state `LOOK`, default `MOSSY_COBBLESTONE`): `ofFullCopy(Blocks.COBBLESTONE).noLootTable()` (mined or blown up, it leaves nothing: it was never there). The blockstate maps each `look` to the vanilla model (`minecraft:block/mossy_cobblestone`, `cobblestone`, `stone_bricks`). One block with a property, not three: one registry entry, one BE validity entry, and the generator only switches a string.
- [ ] **Step 5: Register and add data.** Lang names: "Phantom Ruin", "Glamoured Block", "Jinx Ward"; pickaxe tag gets `phantom_ruin`; `prevent_mob_spawning_inside` (`replace: false`) gets `glamoured_block`, `jinx_ward`.
- [ ] **Step 6: Compile** — `COMPILE OK`.
- [ ] **Step 7: Commit** "Add the phantom ruin and glamoured blocks that hide wizard houses".

### Task 3: Shimmer effect and sounds

**Files:**
- Modify: `fx/FxKinds.java`, `ModSounds.java`, `assets/hogwartsmagic/sounds.json`, `lang/en_us.json`
- Create: `client/fx/HiddenHouseFx.java`, `assets/hogwartsmagic/sounds/hidden_house/reveal.ogg`, `jinx.ogg`

**Interfaces:**
- Produces: `FxKinds.HIDDEN_HOUSE_SHIMMER = "hidden_house_shimmer"` (params: radius blocks, speed blocks/tick); `ModSounds.HIDDEN_HOUSE_REVEAL` (`"hidden_house.reveal"`), `ModSounds.HIDDEN_HOUSE_JINX` (`"hidden_house.jinx"`); subtitles `subtitles.hogwartsmagic.hidden_house.reveal` "Glamour breaks", `.jinx` "Jinx cracks".

- [ ] **Step 1: Client effect.** `HiddenHouseFx` (`Dist.CLIENT`, MOD-bus `Setup` like `CameraShake.Setup`) keeps at most 4 waves; each client tick a wave of age `a` spawns up to 24 `SpellVfxOptions` GLOW/MOTE particles (pale silver-gold `0xE8E4C8`) on a shell of radius `a*speed` around its origin (upper hemisphere plus a ground ring), until the radius passes the param. Cull beyond 64 blocks; clear on `ClientPlayerNetworkEvent.LoggingOut`.
- [ ] **Step 2: Sounds** via the ElevenLabs pipeline in BUILDER_RULES (master every take: envelope cut, RMS level match, limiter, mono 44.1 kHz q5). Prompts: reveal (3.0 s) "a magical illusion shattering into shimmering glassy chimes, a rising sparkling swell and a soft whoosh, close-miked, loud, no silence"; jinx (1.2 s) "a sharp magical hex crack, an electric snap and a heavy thump, close-miked, loud, no silence". Register both, `sounds.json` entries with subtitles.
- [ ] **Step 3: Compile** — `COMPILE OK`. **Step 4: Commit** "Give the hidden house reveal its shimmer and sounds".

### Task 4: The reveal

**Files:**
- Create: `hiddenhouse/HiddenHouses.java`
- Modify: `spell/support/RevelioSpell.java:39` (one line after `TombCurses.reveal`), `gametest/HiddenHouseGameTests.java`

**Interfaces:**
- Consumes: Task 1 rules, Task 2 blocks/BE, Task 3 `HIDDEN_HOUSE_SHIMMER` and sounds, `tomb.CurseScheduler.after(int, Runnable)`.
- Produces: `public static int HiddenHouses.reveal(ServerLevel level, Vec3 center, @Nullable Entity caster)` (blocks scheduled; 0 when no house is in reach); `static void revealOne(ServerLevel level, BlockPos pos, int retries)` (package-private, also used by tests via `reveal`).

- [ ] **Step 1: Write the GameTests** (template `empty9`, helpers like `DesertGameTests.player`):
  - `revealTurnsGlamourRealAndPhantomToAirForGood`: glamoured stone, glamoured chest (LootTable RealNbt), glamoured red bed (foot+head), two phantoms, one more glamoured stone 12 blocks off and unlinked. Cast at (1.5,2,1.5). After `MAX_DELAY + 5` ticks: in-reach cells hold exactly their real states, phantoms are air, the chest's `getLootTable()` is `chests/hidden_house`, the bed's head is at `foot.relative(facing)`, no `HiddenHouseBlockEntity` remains in reach, `level.getBlockTicks().hasScheduledTick(p, block)` is false for all; the far block is still glamoured. Re-check the states 100 ticks later.
  - `revealWaitsForSomeoneStandingInAWall`: mock player inside a glamoured stone cell; after 15 ticks it is still glamoured; teleport the player away; within `OCCUPIED_RETRY_TICKS + 2` more ticks it is stone.
  - `doubleCastRevealsOnceWithoutDuplicates`: two casts the same tick at a glamoured chest; after the sweep there is one chest with one loot table, the second cast after the sweep returns 0.
- [ ] **Step 2: Implement `reveal`.** First a cheap pass: walk `getChunkNow` block entities (as `TombCurses.hiddenWithin`) in the chunks covering `REVEAL_RADIUS`; none found → return 0. Else gather every `HiddenHouseBlockEntity` within `searchHalfWidth()` into `Set<Cell>`, call `HiddenHouseRules.house`. For each cell schedule `revealOne` after `revealDelay(distance)`; a real state with `DOUBLE_BLOCK_HALF` or `BED_PART` uses the smaller delay of itself and its partner (lower↔above; foot→`relative(facing)`, head→`relative(facing.getOpposite())`) so the halves appear together. Send `HIDDEN_HOUSE_SHIMMER` (range 64, params max distance, `WAVE_SPEED`) and play `HIDDEN_HOUSE_REVEAL` at the cast and at the house's centroid.
- [ ] **Step 3: Implement `revealOne`.** Skip if `!level.isLoaded(pos)` or the BE is gone (already revealed, or a second cast). Target = `real()` or `Blocks.AIR`. If the target has a non-empty collision shape and a non-spectator `LivingEntity` intersects the cell and `retries < OCCUPIED_MAX_RETRIES`, reschedule after `OCCUPIED_RETRY_TICKS` with `retries + 1`. Otherwise copy `realNbt()`, `setBlock(pos, target, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)` (no shape cascade: a bed half or a lantern must not pop while its partner is still glamoured), then if NBT: put a fresh `LootTableSeed` when it has `LootTable`, `loadWithComponents(nbt, level.registryAccess())`, `setChanged()`. One `SpellVfxOptions` MOTE burst (count 2) per block; a ward also gets a small red spark (the jinx lifting). No scheduled tick anywhere: the reveal is permanent.
- [ ] **Step 4: Hook Revelio:** `com.hogwartsmagic.hiddenhouse.HiddenHouses.reveal(level, center, caster);` after the TombCurses line, every rank.
- [ ] **Step 5: Compile** — `COMPILE OK`. **Step 6: Commit** "Revelio unmasks hidden wizard houses".

### Task 5: The jinxed chest

**Files:**
- Create: `hiddenhouse/HiddenHouseJinx.java` (`@Mod.EventBusSubscriber`, FORGE bus)
- Modify: `gametest/HiddenHouseGameTests.java`

**Interfaces:**
- Produces: `public static boolean HiddenHouseJinx.tryHex(ServerLevel level, BlockPos chest, ServerPlayer player)` — true when a ward sat above the chest and the hex fired.

A jinxed chest is a real vanilla chest with a `JINX_WARD` directly above it (Forge 51 block entities have no persistent data, so the flag lives in the world). The ward has no collision and is no redstone conductor, so the chest still opens normally once the ward is gone.

- [ ] **Step 1: Write the GameTests** `jinxedChestHexesOnceThenOpens` (chest + ward above, mock player 2 blocks south: first `tryHex` true, player has BLINDNESS ≥ `JINX_BLIND_TICKS - 1`, velocity points away from the chest, ward is air; second call false) and `revealDisarmsTheJinx` (chest + ward + one glamoured block beside; cast; after the sweep the ward is gone and `tryHex` is false).
- [ ] **Step 2: Implement.** `onRightClickBlock`: server side, main hand, not creative/spectator (`TombCurses.canTrigger`), clicked block is a `ChestBlock` and `tryHex` is true → `setCanceled(true)`, `setCancellationResult(InteractionResult.SUCCESS)`. `tryHex`: remove the ward, `player.knockback(JINX_PUSH, chestX - px, chestZ - pz)` plus `hurtMarked = true`, BLINDNESS for `JINX_BLIND_TICKS`, `Fx.send(SLAM)` at the chest, `Fx.shake`, a violet `SpellVfxOptions` burst, `HIDDEN_HOUSE_JINX` sound, actionbar `message.hogwartsmagic.hidden_house.jinx` "The chest was jinxed!".
- [ ] **Step 3: Compile** — `COMPILE OK`. **Step 4: Commit** "Jinx a hidden house chest for whoever opens it unrevealed".

### Task 6: Rotating real states with the template

**Files:**
- Create: `worldgen/structure/GlamourRotationProcessor.java`, `data/hogwartsmagic/worldgen/processor_list/hidden_house.json`
- Modify: `ExampleMod.java` (one line in the constructor: `GlamourRotationProcessor.register(modEventBus);`)

**Interfaces:**
- Produces: `GlamourRotationProcessor.INSTANCE`; its own `DeferredRegister<StructureProcessorType<?>> PROCESSOR_TYPES` with `TYPE` id `hogwartsmagic:glamour_rotation` (`() -> MapCodec.unit(INSTANCE)`); `static void register(IEventBus)`. Processor list JSON: `{"processors":[{"processor_type":"hogwartsmagic:glamour_rotation"}]}`.

- [ ] **Step 1: Implement `processBlock`:** for an info whose state is a glamoured block or ward and whose NBT has `Real`, return a copy whose NBT `Real` is `readBlockState(level.holderLookup(Registries.BLOCK), …).mirror(settings.getMirror()).rotate(settings.getRotation())` (the same order `StructureTemplate.placeInWorld` uses); never mutate the template's own tag. Everything else passes through.
- [ ] **Step 2: Compile** — `COMPILE OK`. The test is `rotatedHouseKeepsBedsAndDoorsWhole` in Task 7, which needs the template. **Step 3: Commit** "Turn a hidden house's furniture with its ruin".

### Task 7: The house template, worldgen and loot

**Files:**
- Modify: `build_scripts/generate_biome_structures.py` (one block under `# --- Hidden wizard houses ---` before `BUILDS`, a `'hidden_house': hidden_house` entry at the end of `BUILDS`, and `if name == 'hidden_house': problems += audit_hidden_house(blocks)` at the end of the main loop's audit chain)
- Create: `build_scripts/hidden_house_views.py`, `data/hogwartsmagic/structure/hidden_house.nbt` (generated), `worldgen/structure/hidden_house.json`, `worldgen/structure_set/hidden_house.json`, `worldgen/template_pool/hidden_house.json`, `tags/worldgen/biome/has_structure/hidden_house.json`, `loot_table/chests/hidden_house.json`, `loot_table/chests/hidden_house_portkey.json`
- Modify: `loot/MagicBookChestLoot.java`, `lang/en_us.json`, `gametest/HiddenHouseGameTests.java`

**Interfaces:**
- Python: `glamour(b, pos, name, props=None, nbt=None)`, `phantom(b, pos, look)`, `ward(b, pos)`, `cottage_blocks() -> dict`, `ruin_blocks() -> dict`, `merge_hidden(cottage, ruin) -> (blocks, problems)`, `ruin_view(blocks) -> dict`, `cottage_view(blocks) -> dict`, `hidden_house() -> (blocks, [])`, `audit_hidden_house(blocks) -> list`, constants `HOUSE_G = 2`, `GLAMOUR_DENY` / `GLAMOUR_DENY_SUFFIX` (mirror `isGlamourTarget`, comment points at it), `HOUSE_LINK = 3`, `HOUSE_SPAN = 24`, `HOUSE_MAX = 2048`.
- Glamour NBT: `{'id': 'hogwartsmagic:hidden_house', 'Real': ('compound', {'Name': ('string', n), 'Properties': ('compound', {k: ('string', v)})}), 'RealNbt': ('compound', {...})}` (`build_template` passes typed tuples through).

- [ ] **Step 1: The cottage** (`cottage_blocks`, the complete house; template 17 x ~12 x 15, `HOUSE_G = 2`, y=0 dirt, y=1 floor). Footprint x 3..13, z 3..11, stone walls (`tex` of mossy_cobblestone / cobblestone / stone_bricks) G..G+3, spruce-plank gables, steep spruce-stair roof along x (explicit `shape: straight`) with a slab ridge at about G+8, cobblestone chimney on the east wall with a lit campfire fireplace inside, `glass` windows (full blocks, no panes), spruce door (both halves, south wall x=8). Inside: red bed in the NW corner, a 4x3 bookshelf wall, `hogwartsmagic:wizard_cauldron` by the hearth, a joined `hogwartsmagic:wizard_table` (write its north/south/east/west join properties explicitly: no shape update will fix them) with two `wizard_chair`s, a hanging lantern from a tie beam, red carpet. The loot chest (`LootTable hogwartsmagic:chests/hidden_house`) in the SE corner; the jinxed chest (same table) at the bed's foot. Floor: stone-brick flags with a 3x3 hieroglyph-sandstone warding mosaic in front of the loot chest holding two `cursed()` tiles (`collapse`, `withering`). Interior air is `cave_air`.
- [ ] **Step 2: The ruin** (`ruin_blocks`): the same floor and mosaic; wall stumps that are the cottage's own wall cells up to a noise height of 0–3 courses (corners highest); vines on stump outer faces, moss carpet, ferns and short grass on a grass ring outside the footprint; 25–40 phantom rubble blocks heaped inside and spilled outside, plus a fallen chimney line to the east; the jinxed chest real and visible, `ward()` above it.
- [ ] **Step 3: `merge_hidden`:** same block in both → real; cottage block where the ruin has nothing → `glamour` (problem if `GLAMOUR_DENY` rejects it); ruin stone where the cottage is air → `phantom` with that look; anything else differing → problem. `ruin_view` maps glamour/ward to air and phantom to its look; `cottage_view` maps glamour to `Real`, phantom/ward to air.
- [ ] **Step 4: `audit_hidden_house`:** returns merge problems; runs `audit()` on `cottage_view` and `ruin_view`; every house block (glamour/phantom/ward) is one component under Chebyshev link `HOUSE_LINK`, spans ≤ `HOUSE_SPAN` and counts ≤ `HOUSE_MAX`; the ruin view shows exactly one chest, nothing above G+3 but phantom, and no vine, plant, carpet or lantern resting on a glamour/phantom cell; the cottage view has a whole bed, a whole door, ≥ 8 bookshelves, a cauldron, a campfire, two chests; each cursed tile has plain hieroglyph tiles round it (the tomb's rule). Run `python build_scripts/generate_biome_structures.py`: `hidden_house` prints `ok`, every other build unchanged.
- [ ] **Step 5: Render.** `hidden_house_views.py <out_dir>` imports the generator and writes `hidden_house_ruin.nbt` and `hidden_house_cottage.nbt` (vanilla blocks only) to a scratch dir; render both with the `rendering-minecraft-structures` skill and fix what reads wrong (the ruin must look like a ruin from 20 blocks, the cottage must look lived in).
- [ ] **Step 6: Worldgen JSON.** Structure: `type hogwartsmagic:dry_jigsaw`, `biomes #hogwartsmagic:has_structure/hidden_house`, `step surface_structures`, `terrain_adaptation beard_thin`, `start_height {"absolute": -2}`, `project_start_to_heightmap WORLD_SURFACE_WG`, `size 1`, `max_distance_from_center 80`. Pool: one `single_pool_element`, `processors: "hogwartsmagic:hidden_house"`, `projection rigid`. Set: `random_spread`, spacing 28, separation 10, salt 741220041. Biome tag: the six spec biomes.
- [ ] **Step 7: Loot.** `chests/hidden_house`: pool 1, 4–7 rolls of floo_powder (w10, 2–6), asphodel (w8, 1–3), wormwood (w8, 1–3), dittany (w6, 1–2), valerian_sprigs (w6, 1–3), mandrake_root (w3, 1), glass_bottle (w8, 2–4), paper (w8, 3–8), book (w6, 1–3), ink_sac (w5, 1–3), hogwartsmagic:galleon (w6, 2–6), candle (w5, 1–3); pool 2, 1 roll: `{"type":"minecraft:loot_table","value":"hogwartsmagic:chests/hidden_house_portkey","weight":1}` or `minecraft:empty` weight 5. `chests/hidden_house_portkey`: one item `hogwartsmagic:old_portkey`. The reference by key is why a missing item cannot break the house table: only the sub-table fails to parse, and a missing reference is a validation warning. Spellbooks: `putGuaranteed("hogwartsmagic:chests/hidden_house", HIDDEN_HOUSE)` with `HIDDEN_HOUSE = {REVELIO, ALOHOMORA, COLLOPORTUS, REPARO, ACCIO, LUMOS, CONFUNDO, CAVE_INIMICUM, PROTEGO_MAXIMA, PIERTOTUM_LOCOMOTOR, CAELUM_FASCINO}` (a house's own charms), `indexSource("hidden_house", HIDDEN_HOUSE)`, lang `gui.hogwartsmagic.grimoire.source.hidden_house` "Hidden wizard houses". Tomes need nothing: `TomeOfMasteryLootModifier` already treats every `hogwartsmagic:chests/` table as rare-eligible.
- [ ] **Step 8: GameTests.**
  - `hiddenHouseTemplateRevealsCompletely` (`template = "hogwartsmagic:hidden_house"`, timeout `MAX_DELAY + 260`): before casting, record every house BE's expected state (real or air) and assert ≥ 150 glamoured and ≥ 20 phantom blocks; cast at (8.5, 3, 13.5); after `MAX_DELAY + 10` ticks every recorded cell holds its expected state, no `HiddenHouseBlockEntity` is left, the loot chest has its table, the ward is gone, ≥ 2 unrevealed cursed glyphs remain; 200 ticks later the states are unchanged.
  - `rotatedHouseKeepsBedsAndDoorsWhole` (`empty3`; follow `HighlandGameTests` for forced chunks and clean-up): `placeInWorld` with `setRotation(Rotation.CLOCKWISE_90).addProcessor(GlamourRotationProcessor.INSTANCE)`, cast, after the sweep every bed foot has its head at `relative(facing)` and every lower door half has a matching upper half above; then clear the area and release only the chunks this test forced.
  - `hiddenHouseDataLoads` (`empty3`): structure, structure set and `PROCESSOR_LIST` `hidden_house` are registered; the biome tag has 6 entries; `chests/hidden_house` is not `LootTable.EMPTY`; `chests/hidden_house_portkey` is non-empty exactly when `BuiltInRegistries.ITEM.containsKey(old_portkey)`; template size x ≥ 15.
- [ ] **Step 9: Compile** — `COMPILE OK`; run the Task 1 tests again — `TESTS OK`. **Step 10: Commit** "Hide wizard houses under overgrown ruins in the temperate woods and plains", then write the BUILDER_RULES final report (`/locate structure hogwartsmagic:hidden_house`, cast Revelio within 10 blocks).
