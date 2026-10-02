# Spell Mastery Phase 7b (Wingardium Blocks, Arresto Freeze, Tenebris Pass-Through, Alatus Quality) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The second half of the roadmap's "expensive" features. First the **Broom Workbench** stops refusing the Nimbus and Firebolt ingredients (its slot filter disagreed with its own recipes). Then **Alatus** infuses faster with rank and, from Adept, can make a **Fine** (+5% speed) or **Masterwork** (+10% speed, +15% boost stamina) broom, stored as an item data component and applied in `BroomEntity`; a Master can re-infuse a broom with Arcane Dust to reroll it. **Wingardium Leviosa** at Expert lifts a block (no block entity, up to stone hardness, through `TerrainRules.canBreak` as the last step) and carries it as a block entity you can drop, place or throw; at Master it holds two things and anything thrown deals 6 damage. **Arresto Momentum** at Expert freezes arrows, tridents, fireballs and spell beams within 4 blocks for 2s, then drops them; at Master the radius is 6 and your next cast redirects the frozen beams where you aim. **Tenebris Flight** at Expert lets 30% of projectiles and beams pass through you (50% at Master), and a Master's smoke ram deals 5 damage and Blindness.

**Architecture:** Every per-rank number lives in a Minecraft-free class in the new package `spell/motion/` (`WingardiumRules`, `ArrestoRules`, `TenebrisRules`, `AlatusRules`), tested with JUnit in Task 2. Behaviour lives in one class per spell in the same package (`WingardiumSpell`, `ArrestoFreeze`, `TenebrisFlightSpell`) plus one new entity, `entity/CarriedBlockEntity` (a block in flight: held by the Wingardium channel, falling and placing itself through `TerrainRules.canPlace` when let go, striking what it hits when thrown). The existing Wingardium channel (`SpellPackets.CastSpellPacket.castAs` heartbeat + `ModEvents.tickWingardiumChannel`) is extended, not replaced: a carried block is just another entity it can hold. Frozen projectiles are tracked per dimension in `ArrestoFreeze` and held in place at the end of each level tick, so nothing per-tick touches a player. Tenebris pass-through is a `ProjectileImpactEvent` listener that answers `SKIP_ENTITY`, with one roll per projectile and flyer cached on the projectile. Broom quality is a real `DataComponentType<Integer>` (`ExampleMod.BROOM_QUALITY`), copied onto the `BroomEntity` as synced entity data so the client-driven flight physics read it. This phase adds no packets; the Wingardium throw rides the existing heartbeat's fourth flag (today only Fiendfyre uses it).

**Tech Stack:** Forge 51.0.33, Minecraft 1.21, Java 21, JUnit 5 (already in `build.gradle`).

**Spec:** `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`, section "Phase 7: The expensive features" (Wingardium block carrying, Arresto projectile freezing, Tenebris pass-through, Alatus broom quality rolls after fixing the broom bench slot filter), "Decisions that change the design doc" (Decision 1: bespoke behaviour only where the roadmap names it) and "Global constraints". Numbers come from `docs/superpowers/specs/2026-10-02-spell-mastery-design.md` sections E (Wingardium Leviosa, Arresto Momentum), G (Tenebris Flight) and H (Alatus). Where they disagree, the roadmap wins. The first half of Phase 7 (Patronus forms, Serpensortia snakes, Fiendfyre) is `2026-10-02-spell-mastery-phase7a.md`; the two halves share no code and can be built in either order.

**Depends on Phases 1–6 (all merged first).** This plan calls, by exact signature:
- Phase 2: `CombatEffects.responsible(@Nullable Entity)` → `Entity`; `CombatTags.ALLIED` (`String`).
- Phase 3: `DisableSupport.casterRank(@Nullable Entity caster, SpellType spell)` → `int` (players: mastery rank; boss NPCs: 3; other NPCs and ownerless: 1); `TerrainRules.canPlace(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`.
- Phase 4: `TerrainRules.canBreak(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity caster)` → `boolean`. **It posts a real `BlockEvent.BreakEvent`**: Task 6 calls it only after every other filter, immediately before removing the block, and a `true` result means the block is removed at once.
- Phase 5a: `SpellBeamEntity.markReflected()` (a reflected beam is never reflected again and earns its new owner no mastery XP); the Phase 5a/5b `ModEvents.isLeftClickSelfCast(SpellType)` list (Arresto is in it, so a left-click self-casts Arresto through `CastSpellPacket(selfCastProtego = true)` → `spell.applyHitEffect(player, player)`).
- Phase 6a: `SpellPackets.CastSpellPacket.castAs(ServerPlayer player, CastSpellPacket p)` holds the whole cast path; every text anchor below that names `castAs` is the old `handle` body text.
- Phase 1: `SpellMasteryData.getRank(Player, SpellType)` → `int` 1..4; `MasteryAwards` (Wingardium stays a channel spell and Alatus a block-utility spell; nothing changes there).
- `SpellType.applyHitEffect(LivingEntity target, Entity caster, @Nullable Vec3 beamDir)` holds the per-spell `switch`.

Before Task 1, confirm they are in:
```bash
grep -n "public static int casterRank" src/main/java/com/hogwartsmagic/spell/disable/DisableSupport.java
grep -n "public static boolean canBreak" src/main/java/com/hogwartsmagic/spell/TerrainRules.java
grep -n "public void markReflected" src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
grep -n "public static void castAs" src/main/java/com/hogwartsmagic/network/SpellPackets.java
```
Each must print one line. If any doesn't, stop: the phase it belongs to isn't merged.

## Global Constraints

- **Ranks:** player-only paths (the Wingardium channel, broom infusion) read `SpellMasteryData.getRank` or `DisableSupport.casterRank`; paths NPCs can reach (Arresto and Tenebris hit effects) read `DisableSupport.casterRank`, so a boss casts them at Expert.
- **Per-rank numbers (ticks at 20/s):**

  | Spell | I Novice | II Adept | III Expert | IV Master |
  |---|---|---|---|---|
  | Wingardium Leviosa | today's mob hold | today's mob hold | also lifts a struck block (no block entity, hardness 0–1.5, not replaceable, no fluid, not a two-part or extended block, not in `#hogwartsmagic:wingardium_immune`); letting go drops it where it lands placed; left-click while holding throws what you hold, 3 damage to whatever a thrown block or mob hits | holds two (the struck mob plus the nearest other creature within 3 blocks, or the struck block plus the one above it); thrown things deal 6 |
  | Arresto Momentum | today's | today's | arrows, tridents, fireballs, spell beams (not Arresto's own) and Basilisk venom within 4 blocks of the impact (of you, on a left-click self-cast) stop in midair for 40, then drop | radius 6; your next beam cast within 40 sends the beams you froze toward where you aim, as your own reflected beams (`markReflected`: no mastery XP) |
  | Tenebris Flight | today's 40-tick smoke flight | today's | 30% of projectiles and beams that would hit you pass through (one roll per projectile) | 50%; flying into an enemy deals 5 and Blindness 40, once per second per target |
  | Alatus | infusion 110 (today's 5.5s) | 80; Fine 15% | 60; Fine 30%, Masterwork 5% | 40; Fine 40%, Masterwork 15%; re-infuse: a finished broom in Base and 4 Arcane Dust in Magic, Boost and Catalyst empty, rerolls its quality |

- **Broom quality:** `ExampleMod.BROOM_QUALITY` component, 0 normal, 1 Fine (+5% base and boost speed), 2 Masterwork (+10% speed, boost stamina drains at 1/1.15 the rate, i.e. lasts 15% longer). Shown as a name prefix and a tooltip line; carried onto the `BroomEntity` when placed. Wizard (NPC) broom flight is unchanged.
- **Wingardium blocks are never lost or duplicated:** a carried block that can't be placed where it lands (claims, wards, spawn protection, an occupied spot, a block that can't survive there) drops as its own item. The carried block is saved with the world, so a restart mid-carry still lands it. It only breaks the original through `TerrainRules.canBreak` as the last step; it places only through `TerrainRules.canPlace`.
- **Freezing never fights the network:** a frozen projectile gets one motion packet when it freezes and one when it drops; in between it is snapped back to its frozen spot at the end of each level tick (its position doesn't change, so nothing is re-sent). Its previous `noGravity` is restored on release. A frozen spell beam's age is held below its 200-tick limit so it doesn't fizzle mid-freeze. Every frozen record lives in its dimension's map.
- **Tenebris:** the pass-through handler checks the flight effect before it touches the projectile's persistent data; the ram reads the flyer's persistent data only while the effect is active and the flyer is flying.
- **Teleports and snaps only into collision-free space** (no new teleports in this phase; a thrown mob is only pushed).
- **New entity** (`CarriedBlockEntity`): renderer registered only in `ClientModEvents`; the block state is synced entity data defined in `defineSynchedData`; no attributes (not living), no spawn egg (not useful). No `broadcastEntityEvent` is used; if you add one, never bytes 21, 35 or 63 (use 61/62/64/66/67).
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code lives in `client/`. This plan adds no packets.
- No `switch` on an enum in client or render code. if-chains, `EnumSet` and `EnumMap` are fine.
- Screens: `renderTransparentBackground`, no blur (Task 1 removes the Broom Workbench screen's duplicate background pass; `AbstractContainerScreen` already draws the transparent background).
- **Never run gradle while the Minecraft client runs.** Every gradle step starts with `tasklist | grep -i javaw`; expected output is empty. If a `javaw` line shows, stop and ask the user to close Minecraft.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent`, spawn protection and Cave Inimicum wards (lifting through `TerrainRules.canBreak`, placing through `TerrainRules.canPlace`).
- Commit per task. The message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (the second `-m` in each commit command does that). Never push. Stage only the named files, never `.claude/settings.local.json`. No apostrophes inside bash heredoc bodies (this plan uses no heredocs).
- `en_us.json` must stay valid JSON after every edit: replacements keep the original line's trailing comma, and an inserted line ends with a comma and goes after a line that already ends with one. A literal percent sign in a translated string is written `%%` (they are rendered through `Component.translatable`).
- Line numbers drift (other phases edit the same files). Every edit below names the exact text to find; trust the text, not a number.

## File Structure

| File | Responsibility |
|---|---|
| Modify `src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java` | Recipe table drives the slot filter (bug fix); rank infusion time; quality roll; Master re-infuse |
| Modify `src/main/java/com/hogwartsmagic/menu/BroomWorkbenchMenu.java` | Progress bar uses the rank infusion time |
| Modify `src/main/java/com/hogwartsmagic/client/BroomWorkbenchScreen.java` | Drop the duplicate background pass |
| Create `src/main/java/com/hogwartsmagic/spell/motion/RankIndex.java` | Pure: rank 1..4 → table index, clamped (package-private) |
| Create `src/main/java/com/hogwartsmagic/spell/motion/WingardiumRules.java` | Pure: block lifting gates, throw damage, held count, carry physics |
| Create `src/main/java/com/hogwartsmagic/spell/motion/ArrestoRules.java` | Pure: freeze radius, duration, redirect window |
| Create `src/main/java/com/hogwartsmagic/spell/motion/TenebrisRules.java` | Pure: pass-through chance, ram numbers |
| Create `src/main/java/com/hogwartsmagic/spell/motion/AlatusRules.java` | Pure: infusion time, quality roll, speed and stamina multipliers |
| Create `src/main/java/com/hogwartsmagic/entity/CarriedBlockEntity.java` | A block in flight: held, falling, thrown, placing or dropping |
| Create `src/main/java/com/hogwartsmagic/client/renderer/CarriedBlockRenderer.java` | Draws the carried block |
| Create `src/main/java/com/hogwartsmagic/spell/motion/WingardiumSpell.java` | Lifting, the second target, throwing, thrown-mob impacts |
| Create `src/main/java/com/hogwartsmagic/spell/motion/ArrestoFreeze.java` | Freezing, holding, dropping and redirecting projectiles |
| Create `src/main/java/com/hogwartsmagic/spell/motion/TenebrisFlightSpell.java` | Pass-through listener, Master ram |
| Create `src/main/resources/data/hogwartsmagic/tags/block/wingardium_immune.json` | Blocks Wingardium never lifts |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | `DATA_COMPONENTS`, `BROOM_QUALITY` |
| Modify `src/main/java/com/hogwartsmagic/item/BroomItem.java` | Quality helpers, name prefix, tooltip, quality onto the entity |
| Modify `src/main/java/com/hogwartsmagic/entity/BroomEntity.java` | Synced quality; speed and stamina |
| Modify `src/main/java/com/hogwartsmagic/entity/ModEntities.java` | `CARRIED_BLOCK` |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Carried block renderer; Wingardium heartbeat carries the attack key |
| Modify `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` | Alatus passes its caster; Wingardium and Arresto block hits; Wingardium second target |
| Modify `src/main/java/com/hogwartsmagic/network/SpellPackets.java` | Wingardium heartbeat (throw, second target); Arresto redirect hook |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | Wingardium channel holds two and knows a throw; Tenebris ram and cleanup |
| Modify `src/main/java/com/hogwartsmagic/spell/SpellType.java` | Arresto freeze; Tenebris amplifier carries the rank |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Descriptions, broom quality, messages |
| Create tests under `src/test/java/com/hogwartsmagic/spell/motion/` | Unit tests |

---

### Task 1: Fix the Broom Workbench slot filter

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/client/BroomWorkbenchScreen.java`

**Interfaces:**
- Consumes: `ExampleMod.FINE_WOOD_STICK/BROOM/NIMBUS_2000/FIREBOLT/ARCANE_DUST/ARCANE_CORE/DRAGON_HEARTSTRING_CORE` (existing).
- Produces: nothing new for later tasks (the private recipe table is reused in Tasks 3–4).

**The bug:** the inventory's `isItemValid` hard-codes per-slot item lists that don't match `canCraftBroom`. The Nimbus recipe needs a Phantom Membrane in Boost (slot B only accepts wheat, feather, netherite) and a Netherite Ingot in Magic (slot C only accepts arcane dust, gold block, phantom membrane); the Firebolt recipe needs an Elytra in Magic (not accepted either). So neither can be crafted. The menu's slots (`SlotItemHandler`) and shift-click both ask `isItemValid`, so fixing it there fixes all three paths. The fix makes one recipe table the single source for the filter, the recipe check and the result.

- [ ] **Step 1: One recipe table.** In `BroomWorkbenchBlockEntity.java`, add these imports to the import block:
```java
import net.minecraft.world.item.Item;
import java.util.List;
```
Replace the whole `isItemValid` override inside the `inventory` field:
```java
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_RESULT) {
                return false;
            }
            if (slot == SLOT_A) {
                return stack.is(ExampleMod.FINE_WOOD_STICK.get())
                        || stack.is(ExampleMod.BROOM.get())
                        || stack.is(ExampleMod.NIMBUS_2000.get());
            }
            if (slot == SLOT_B) {
                return stack.is(Items.WHEAT)
                        || stack.is(Items.FEATHER)
                        || stack.is(Items.NETHERITE_INGOT);
            }
            if (slot == SLOT_C) {
                return stack.is(ExampleMod.ARCANE_DUST.get())
                        || stack.is(Items.GOLD_BLOCK)
                        || stack.is(Items.PHANTOM_MEMBRANE);
            }
            if (slot == SLOT_D) {
                return stack.is(Items.IRON_INGOT)
                        || stack.is(ExampleMod.ARCANE_CORE.get())
                        || stack.is(ExampleMod.DRAGON_HEARTSTRING_CORE.get());
            }
            return false;
        }
```
with:
```java
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_RESULT) {
                return false;
            }
            // Derived from the recipes, so every ingredient of every recipe fits its slot. The old
            // hand-written lists refused the Nimbus and Firebolt ingredients.
            for (BroomRecipe recipe : recipes()) {
                Item wanted = recipe.slot(slot);
                if (wanted != null && stack.is(wanted)) {
                    return true;
                }
            }
            return false;
        }
```
Directly above the line `    private final ContainerData dataAccess = new ContainerData() {` add:
```java
    /** One broom recipe, in slot order: Base (A), Boost (B), Magic (C), Catalyst (D). */
    private record BroomRecipe(Item base, Item boost, Item magic, Item catalyst, Item result) {
        @Nullable
        Item slot(int slot) {
            if (slot == SLOT_A) {
                return this.base;
            }
            if (slot == SLOT_B) {
                return this.boost;
            }
            if (slot == SLOT_C) {
                return this.magic;
            }
            if (slot == SLOT_D) {
                return this.catalyst;
            }
            return null;
        }

        boolean matches(ItemStack a, ItemStack b, ItemStack c, ItemStack d) {
            return a.is(this.base) && b.is(this.boost) && c.is(this.magic) && d.is(this.catalyst);
        }
    }

    /** The single source for the slot filter, the recipe check and the result (the recipe book text matches). */
    private static List<BroomRecipe> recipes() {
        return List.of(
                new BroomRecipe(ExampleMod.FINE_WOOD_STICK.get(), Items.WHEAT, ExampleMod.ARCANE_DUST.get(),
                        Items.IRON_INGOT, ExampleMod.BROOM.get()),
                new BroomRecipe(ExampleMod.BROOM.get(), Items.PHANTOM_MEMBRANE, Items.NETHERITE_INGOT,
                        ExampleMod.ARCANE_CORE.get(), ExampleMod.NIMBUS_2000.get()),
                new BroomRecipe(ExampleMod.NIMBUS_2000.get(), Items.NETHERITE_INGOT, Items.ELYTRA,
                        ExampleMod.DRAGON_HEARTSTRING_CORE.get(), ExampleMod.FIREBOLT.get()));
    }

```
Replace the whole `canCraftBroom()` method and the whole `getBroomResultForCurrentInputs()` method (from `    private boolean canCraftBroom() {` down to the closing brace of `getBroomResultForCurrentInputs`, including its `@Nullable` line) with:
```java
    private boolean canCraftBroom() {
        return currentRecipe() != null;
    }

    @Nullable
    private BroomRecipe currentRecipe() {
        ItemStack a = this.inventory.getStackInSlot(SLOT_A);
        ItemStack b = this.inventory.getStackInSlot(SLOT_B);
        ItemStack c = this.inventory.getStackInSlot(SLOT_C);
        ItemStack d = this.inventory.getStackInSlot(SLOT_D);
        if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
            return null;
        }
        for (BroomRecipe recipe : recipes()) {
            if (recipe.matches(a, b, c, d)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack getBroomResultForCurrentInputs() {
        BroomRecipe recipe = currentRecipe();
        return recipe == null ? null : new ItemStack(recipe.result());
    }
```
(`Nullable` here is the file's existing `org.jetbrains.annotations.Nullable` import.)

- [ ] **Step 2: One background pass.** In `BroomWorkbenchScreen.java`, in `render`, replace:
```java
        this.positionRecipeBookButton();
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
```
with:
```java
        this.positionRecipeBookButton();
        // AbstractContainerScreen.render already draws renderTransparentBackground + renderBg once (no blur);
        // the explicit call that stood here drew the dim and the panel a second time.
        super.render(guiGraphics, mouseX, mouseY, partialTick);
```

- [ ] **Step 3: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java src/main/java/com/hogwartsmagic/client/BroomWorkbenchScreen.java
git commit -m "Let the Broom Workbench accept the Nimbus and Firebolt ingredients by deriving its slot filter from the recipes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The pure rules

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/motion/RankIndex.java`, `WingardiumRules.java`, `ArrestoRules.java`, `TenebrisRules.java`, `AlatusRules.java`
- Test: `src/test/java/com/hogwartsmagic/spell/motion/WingardiumRulesTest.java`, `ArrestoRulesTest.java`, `TenebrisRulesTest.java`, `AlatusRulesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `RankIndex.of(int rank)` → `int` 0..3 (package-private)
  - `WingardiumRules.MAX_HARDNESS = 1.5F`, `HELD_GRACE_TICKS = 3`, `IDLE_MAX_TICKS = 1200`, `GRAVITY = 0.04`, `DRAG = 0.98`, `THROW_SPEED = 1.6`, `THROW_LIFT = 0.1`, `THROW_TRACK_TICKS = 40`, `SECOND_TARGET_RADIUS = 3.0`, `PAIR_SPACING = 0.9`, `HOLD_RANGE = 7.5`; `static boolean liftsBlocks(int rank)`, `static boolean canThrow(int rank)`, `static int maxHeld(int rank)`, `static float throwDamage(int rank)`, `static boolean isLiftable(float hardness, boolean air, boolean hasBlockEntity, boolean replaceable, boolean fluidEmpty, boolean multipart, boolean immune)`, `static boolean risingEdge(boolean wasDown, boolean isDown)`
  - `ArrestoRules.FREEZE_TICKS = 40`, `REDIRECT_WINDOW_TICKS = 40`, `MAX_FROZEN_PER_LEVEL = 64`, `BEAM_LIFE_CAP = 150`, `DROP_SPEED = 0.6`; `static boolean freezes(int rank)`, `static double freezeRadius(int rank)`, `static boolean redirects(int rank)`, `static boolean released(long until, long now)`, `static boolean inRedirectWindow(long until, long now)`
  - `TenebrisRules.RAM_DAMAGE = 5.0F`, `RAM_BLIND_TICKS = 40`, `RAM_COOLDOWN_TICKS = 20`, `RAM_REACH = 0.4`; `static double passChance(int rank)`, `static boolean passes(int rank, double roll)`, `static boolean rams(int rank)`, `static int amplifier(int rank)`, `static boolean ramReady(boolean rammedBefore, long lastRam, long now)`
  - `AlatusRules.QUALITY_NORMAL = 0`, `QUALITY_FINE = 1`, `QUALITY_MASTERWORK = 2`, `REINFUSE_DUST_COST = 4`; `static int infusionTicks(int rank)`, `static double fineChance(int rank)`, `static double masterworkChance(int rank)`, `static int rollQuality(int rank, double roll)`, `static boolean reinfuses(int rank)`, `static int clampQuality(int quality)`, `static double speedMultiplier(int quality)`, `static double staminaDrainMultiplier(int quality)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hogwartsmagic/spell/motion/WingardiumRulesTest.java`:
```java
package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingardiumRulesTest {
    @Test
    void blocksAndThrowsFromExpertTwoHeldAtMaster() {
        assertFalse(WingardiumRules.liftsBlocks(2));
        assertTrue(WingardiumRules.liftsBlocks(3));
        assertFalse(WingardiumRules.canThrow(2));
        assertTrue(WingardiumRules.canThrow(3));
        assertEquals(1, WingardiumRules.maxHeld(3));
        assertEquals(2, WingardiumRules.maxHeld(4));
        assertEquals(0f, WingardiumRules.throwDamage(2), 1e-6);
        assertEquals(3f, WingardiumRules.throwDamage(3), 1e-6);
        assertEquals(6f, WingardiumRules.throwDamage(4), 1e-6);
    }

    @Test
    void onlyPlainBlocksUpToStoneLift() {
        assertTrue(WingardiumRules.isLiftable(1.5f, false, false, false, true, false, false)); // stone
        assertTrue(WingardiumRules.isLiftable(0.0f, false, false, false, true, false, false)); // a flower pot or torch
        assertFalse(WingardiumRules.isLiftable(1.6f, false, false, false, true, false, false)); // harder than stone
        assertFalse(WingardiumRules.isLiftable(-1.0f, false, false, false, true, false, false)); // bedrock
        assertFalse(WingardiumRules.isLiftable(0.5f, true, false, false, true, false, false)); // air
        assertFalse(WingardiumRules.isLiftable(0.5f, false, true, false, true, false, false)); // a chest
        assertFalse(WingardiumRules.isLiftable(0.0f, false, false, true, true, false, false)); // short grass
        assertFalse(WingardiumRules.isLiftable(0.5f, false, false, false, false, false, false)); // waterlogged
        assertFalse(WingardiumRules.isLiftable(1.0f, false, false, false, true, true, false)); // a door half
        assertFalse(WingardiumRules.isLiftable(0.5f, false, false, false, true, false, true)); // tagged immune
        assertFalse(WingardiumRules.isLiftable(Float.NaN, false, false, false, true, false, false));
    }

    @Test
    void aThrowIsAFreshPress() {
        assertTrue(WingardiumRules.risingEdge(false, true));
        assertFalse(WingardiumRules.risingEdge(true, true));
        assertFalse(WingardiumRules.risingEdge(false, false));
        assertFalse(WingardiumRules.risingEdge(true, false));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/motion/ArrestoRulesTest.java`:
```java
package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrestoRulesTest {
    @Test
    void freezingFromExpertWiderAtMaster() {
        assertFalse(ArrestoRules.freezes(2));
        assertTrue(ArrestoRules.freezes(3));
        assertEquals(0.0, ArrestoRules.freezeRadius(2), 1e-9);
        assertEquals(4.0, ArrestoRules.freezeRadius(3), 1e-9);
        assertEquals(6.0, ArrestoRules.freezeRadius(4), 1e-9);
        assertFalse(ArrestoRules.redirects(3));
        assertTrue(ArrestoRules.redirects(4));
    }

    @Test
    void timing() {
        assertFalse(ArrestoRules.released(140L, 139L));
        assertTrue(ArrestoRules.released(140L, 140L));
        assertTrue(ArrestoRules.inRedirectWindow(140L, 140L));
        assertFalse(ArrestoRules.inRedirectWindow(140L, 141L));
        assertEquals(40, ArrestoRules.FREEZE_TICKS);
    }
}
```

`src/test/java/com/hogwartsmagic/spell/motion/TenebrisRulesTest.java`:
```java
package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenebrisRulesTest {
    @Test
    void passThroughFromExpert() {
        assertEquals(0.0, TenebrisRules.passChance(2), 1e-9);
        assertEquals(0.3, TenebrisRules.passChance(3), 1e-9);
        assertEquals(0.5, TenebrisRules.passChance(4), 1e-9);
        assertTrue(TenebrisRules.passes(3, 0.29));
        assertFalse(TenebrisRules.passes(3, 0.3));
        assertTrue(TenebrisRules.passes(4, 0.49));
        assertFalse(TenebrisRules.passes(2, 0.0));
    }

    @Test
    void ramAtMasterOncePerSecondPerTarget() {
        assertFalse(TenebrisRules.rams(3));
        assertTrue(TenebrisRules.rams(4));
        assertTrue(TenebrisRules.ramReady(false, 0L, 5L));
        assertFalse(TenebrisRules.ramReady(true, 100L, 119L));
        assertTrue(TenebrisRules.ramReady(true, 100L, 120L));
    }

    @Test
    void amplifierCarriesTheRank() {
        assertEquals(0, TenebrisRules.amplifier(1));
        assertEquals(3, TenebrisRules.amplifier(4));
        assertEquals(3, TenebrisRules.amplifier(7));
    }
}
```

`src/test/java/com/hogwartsmagic/spell/motion/AlatusRulesTest.java`:
```java
package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlatusRulesTest {
    @Test
    void fasterInfusionWithRank() {
        assertEquals(110, AlatusRules.infusionTicks(1));
        assertEquals(80, AlatusRules.infusionTicks(2));
        assertEquals(60, AlatusRules.infusionTicks(3));
        assertEquals(40, AlatusRules.infusionTicks(4));
    }

    @Test
    void qualityRollsByRank() {
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(1, 0.0));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(2, 0.14));
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(2, 0.15));
        assertEquals(AlatusRules.QUALITY_MASTERWORK, AlatusRules.rollQuality(3, 0.04));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(3, 0.05));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(3, 0.349));
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(3, 0.35));
        assertEquals(AlatusRules.QUALITY_MASTERWORK, AlatusRules.rollQuality(4, 0.149));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(4, 0.15));
        assertEquals(AlatusRules.QUALITY_FINE, AlatusRules.rollQuality(4, 0.549));
        assertEquals(AlatusRules.QUALITY_NORMAL, AlatusRules.rollQuality(4, 0.55));
    }

    @Test
    void reinfuseAtMasterOnly() {
        assertFalse(AlatusRules.reinfuses(3));
        assertTrue(AlatusRules.reinfuses(4));
        assertEquals(4, AlatusRules.REINFUSE_DUST_COST);
    }

    @Test
    void speedAndStamina() {
        assertEquals(1.0, AlatusRules.speedMultiplier(0), 1e-9);
        assertEquals(1.05, AlatusRules.speedMultiplier(1), 1e-9);
        assertEquals(1.10, AlatusRules.speedMultiplier(2), 1e-9);
        assertEquals(1.10, AlatusRules.speedMultiplier(9), 1e-9);
        assertEquals(1.0, AlatusRules.staminaDrainMultiplier(1), 1e-9);
        assertEquals(1.0 / 1.15, AlatusRules.staminaDrainMultiplier(2), 1e-9);
        assertEquals(0, AlatusRules.clampQuality(-3));
        assertEquals(2, AlatusRules.clampQuality(5));
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.motion.*"`
Expected: compilation fails (`cannot find symbol: class WingardiumRules` and similar).

- [ ] **Step 3: Implement**

`src/main/java/com/hogwartsmagic/spell/motion/RankIndex.java`:
```java
package com.hogwartsmagic.spell.motion;

/** Rank 1..4 to a table index 0..3; anything outside is clamped. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/motion/WingardiumRules.java`:
```java
package com.hogwartsmagic.spell.motion;

/**
 * Wingardium Leviosa: from Expert it lifts blocks and throws what it holds; at Master it holds two and
 * throws harder. Also the carried block's physics.
 */
public final class WingardiumRules {
    /** Stone's hardness: anything harder stays put. */
    public static final float MAX_HARDNESS = 1.5F;
    /** A carried block counts as held for this long after the channel last steered it. */
    public static final int HELD_GRACE_TICKS = 3;
    /** A loose carried block that hasn't landed after this long lands where it is. */
    public static final int IDLE_MAX_TICKS = 1200;
    public static final double GRAVITY = 0.04;
    public static final double DRAG = 0.98;
    public static final double THROW_SPEED = 1.6;
    public static final double THROW_LIFT = 0.1;
    public static final int THROW_TRACK_TICKS = 40;
    public static final double SECOND_TARGET_RADIUS = 3.0;
    /** Sideways distance of each of two held things from the centre of the hold. */
    public static final double PAIR_SPACING = 0.9;
    /** Same as the channel's own hold range (ModEvents.WINGARDIUM_HOLD_RANGE): the second held thing slips away past it. */
    public static final double HOLD_RANGE = 7.5;
    private static final float[] THROW_DAMAGE = {0.0F, 0.0F, 3.0F, 6.0F};

    private WingardiumRules() {
    }

    public static boolean liftsBlocks(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static boolean canThrow(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static int maxHeld(int rank) {
        return RankIndex.of(rank) == 3 ? 2 : 1;
    }

    public static float throwDamage(int rank) {
        return THROW_DAMAGE[RankIndex.of(rank)];
    }

    /**
     * A plain block: not air, no block entity (no inventories or vaults), not replaceable (grass, snow
     * layers, fire), no fluid in it, not half of a two-part or extended block, not tagged immune, and
     * hardness between 0 and stone's (bedrock and similar are -1).
     */
    public static boolean isLiftable(float hardness, boolean air, boolean hasBlockEntity, boolean replaceable,
                                     boolean fluidEmpty, boolean multipart, boolean immune) {
        if (air || hasBlockEntity || replaceable || !fluidEmpty || multipart || immune) {
            return false;
        }
        return hardness >= 0.0F && hardness <= MAX_HARDNESS;
    }

    public static boolean risingEdge(boolean wasDown, boolean isDown) {
        return isDown && !wasDown;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/motion/ArrestoRules.java`:
```java
package com.hogwartsmagic.spell.motion;

/** Arresto Momentum from Expert: projectiles near the impact freeze; at Master, frozen beams can be redirected. */
public final class ArrestoRules {
    public static final int FREEZE_TICKS = 40;
    public static final int REDIRECT_WINDOW_TICKS = 40;
    public static final int MAX_FROZEN_PER_LEVEL = 64;
    /** A frozen spell beam's age is held at or below this (beams fizzle at 200). */
    public static final int BEAM_LIFE_CAP = 150;
    /** Downward speed given to beams and fireballs (no gravity of their own) when they drop. */
    public static final double DROP_SPEED = 0.6;
    private static final double[] RADIUS = {0.0, 0.0, 4.0, 6.0};

    private ArrestoRules() {
    }

    public static boolean freezes(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static double freezeRadius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static boolean redirects(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean released(long until, long now) {
        return now >= until;
    }

    public static boolean inRedirectWindow(long until, long now) {
        return now <= until;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/motion/TenebrisRules.java`:
```java
package com.hogwartsmagic.spell.motion;

/** Tenebris Flight from Expert: projectiles and beams may pass through; a Master's smoke ram hurts. */
public final class TenebrisRules {
    public static final float RAM_DAMAGE = 5.0F;
    public static final int RAM_BLIND_TICKS = 40;
    public static final int RAM_COOLDOWN_TICKS = 20;
    public static final double RAM_REACH = 0.4;
    private static final double[] PASS_CHANCE = {0.0, 0.0, 0.3, 0.5};

    private TenebrisRules() {
    }

    public static double passChance(int rank) {
        return PASS_CHANCE[RankIndex.of(rank)];
    }

    public static boolean passes(int rank, double roll) {
        return roll < passChance(rank);
    }

    public static boolean rams(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** The flight effect's amplifier carries the caster's rank (rank − 1), so the effect alone tells the rank. */
    public static int amplifier(int rank) {
        return RankIndex.of(rank);
    }

    public static boolean ramReady(boolean rammedBefore, long lastRam, long now) {
        return !rammedBefore || now - lastRam >= RAM_COOLDOWN_TICKS;
    }
}
```

`src/main/java/com/hogwartsmagic/spell/motion/AlatusRules.java`:
```java
package com.hogwartsmagic.spell.motion;

/**
 * Alatus: faster broom infusion with rank and a chance of a better broom. A broom's quality is stored as
 * the BROOM_QUALITY item component and copied onto the BroomEntity, which reads the multipliers here.
 */
public final class AlatusRules {
    public static final int QUALITY_NORMAL = 0;
    public static final int QUALITY_FINE = 1;
    public static final int QUALITY_MASTERWORK = 2;
    public static final int REINFUSE_DUST_COST = 4;
    private static final int[] INFUSION_TICKS = {110, 80, 60, 40};
    private static final double[] FINE = {0.0, 0.15, 0.30, 0.40};
    private static final double[] MASTERWORK = {0.0, 0.0, 0.05, 0.15};
    private static final double[] SPEED = {1.0, 1.05, 1.10};

    private AlatusRules() {
    }

    public static int infusionTicks(int rank) {
        return INFUSION_TICKS[RankIndex.of(rank)];
    }

    public static double fineChance(int rank) {
        return FINE[RankIndex.of(rank)];
    }

    public static double masterworkChance(int rank) {
        return MASTERWORK[RankIndex.of(rank)];
    }

    /** {@code roll} in [0, 1): below the Masterwork chance is Masterwork, then the Fine band, else normal. */
    public static int rollQuality(int rank, double roll) {
        int i = RankIndex.of(rank);
        if (roll < MASTERWORK[i]) {
            return QUALITY_MASTERWORK;
        }
        if (roll < MASTERWORK[i] + FINE[i]) {
            return QUALITY_FINE;
        }
        return QUALITY_NORMAL;
    }

    public static boolean reinfuses(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static int clampQuality(int quality) {
        return Math.max(QUALITY_NORMAL, Math.min(QUALITY_MASTERWORK, quality));
    }

    public static double speedMultiplier(int quality) {
        return SPEED[clampQuality(quality)];
    }

    /** Masterwork boost stamina lasts 15% longer, so it drains at 1/1.15 the rate. */
    public static double staminaDrainMultiplier(int quality) {
        return clampQuality(quality) == QUALITY_MASTERWORK ? 1.0 / 1.15 : 1.0;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test --tests "com.hogwartsmagic.spell.motion.*"`
Expected: `BUILD SUCCESSFUL`, 12 tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/motion/RankIndex.java src/main/java/com/hogwartsmagic/spell/motion/WingardiumRules.java src/main/java/com/hogwartsmagic/spell/motion/ArrestoRules.java src/main/java/com/hogwartsmagic/spell/motion/TenebrisRules.java src/main/java/com/hogwartsmagic/spell/motion/AlatusRules.java src/test/java/com/hogwartsmagic/spell/motion/WingardiumRulesTest.java src/test/java/com/hogwartsmagic/spell/motion/ArrestoRulesTest.java src/test/java/com/hogwartsmagic/spell/motion/TenebrisRulesTest.java src/test/java/com/hogwartsmagic/spell/motion/AlatusRulesTest.java
git commit -m "Add the tested per-rank rules for Wingardium blocks, Arresto freezing, Tenebris pass-through and Alatus quality" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 3: Alatus by rank: faster infusion and Fine or Masterwork brooms

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java` (`DATA_COMPONENTS`, `BROOM_QUALITY`, registration)
- Modify: `src/main/java/com/hogwartsmagic/item/BroomItem.java` (whole file replaced)
- Modify: `src/main/java/com/hogwartsmagic/entity/BroomEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java`
- Modify: `src/main/java/com/hogwartsmagic/menu/BroomWorkbenchMenu.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Alatus block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `AlatusRules` (Task 2); `DisableSupport.casterRank` (Phase 3); the recipe table (Task 1).
- Produces:
  - `ExampleMod.DATA_COMPONENTS` (`DeferredRegister<DataComponentType<?>>`), `ExampleMod.BROOM_QUALITY` (`RegistryObject<DataComponentType<Integer>>`)
  - `BroomItem.quality(ItemStack)` → `int`, `BroomItem.setQuality(ItemStack, int)`
  - `BroomEntity.getQuality()` → `int` (synced), `BroomEntity.setQuality(int)`
  - `BroomWorkbenchBlockEntity.beginInfusionFromSpell(ServerLevel level, @Nullable Entity caster)` → `boolean` (was `(ServerLevel)`), `BroomWorkbenchBlockEntity.getCraftTotalTicks()` → `int`

- [ ] **Step 1: The data component.** In `ExampleMod.java`, directly after the line that starts `    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS` (a single line), add:
```java
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    /** Broom quality from an Alatus infusion: 0 normal, 1 Fine, 2 Masterwork (Spell Mastery Phase 7b). */
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<Integer>> BROOM_QUALITY =
            DATA_COMPONENTS.register("broom_quality", () -> net.minecraft.core.component.DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.intRange(0, 2))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());
```
In the constructor, replace:
```java
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
```
with:
```java
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
```

- [ ] **Step 2: The item shows and carries its quality.** Replace the whole content of `src/main/java/com/hogwartsmagic/item/BroomItem.java` with:
```java
package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.spell.motion.AlatusRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BroomItem extends Item {

    public final int broomType;

    public BroomItem(Properties properties, int broomType) {
        super(properties);
        this.broomType = broomType;
    }

    /** The broom's Alatus quality (AlatusRules.QUALITY_*); a broom without the component is normal. */
    public static int quality(ItemStack stack) {
        Integer quality = stack.get(ExampleMod.BROOM_QUALITY.get());
        return quality == null ? AlatusRules.QUALITY_NORMAL : AlatusRules.clampQuality(quality);
    }

    public static void setQuality(ItemStack stack, int quality) {
        int clamped = AlatusRules.clampQuality(quality);
        if (clamped == AlatusRules.QUALITY_NORMAL) {
            stack.remove(ExampleMod.BROOM_QUALITY.get());
        } else {
            stack.set(ExampleMod.BROOM_QUALITY.get(), clamped);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Vec3 pos = player.position().add(player.getLookAngle().scale(1.5)).add(0.0, 1.0, 0.0);
            BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), level);
            broom.setBroomType(this.broomType);
            broom.setQuality(quality(itemstack));
            broom.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
            level.addFreshEntity(broom);

            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        int quality = quality(stack);
        Component base = super.getName(stack);
        if (quality == AlatusRules.QUALITY_FINE) {
            return Component.translatable("item.hogwartsmagic.broom.quality.fine", base);
        }
        if (quality == AlatusRules.QUALITY_MASTERWORK) {
            return Component.translatable("item.hogwartsmagic.broom.quality.masterwork", base);
        }
        return base;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int quality = quality(stack);
        if (quality == AlatusRules.QUALITY_FINE) {
            tooltip.add(Component.translatable("item.hogwartsmagic.broom.quality.fine.tooltip").withStyle(ChatFormatting.AQUA));
        } else if (quality == AlatusRules.QUALITY_MASTERWORK) {
            tooltip.add(Component.translatable("item.hogwartsmagic.broom.quality.masterwork.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
```

- [ ] **Step 3: The broom flies by its quality.** In `BroomEntity.java`:

After:
```java
    private static final EntityDataAccessor<Byte> DATA_STAMINA = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BYTE);
```
add:
```java
    /** Alatus quality (AlatusRules.QUALITY_*). Synced: while ridden, the rider's client runs the flight physics. */
    private static final EntityDataAccessor<Integer> DATA_QUALITY = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
```
After:
```java
        builder.define(DATA_STAMINA, (byte) STAMINA_MAX);
```
add:
```java
        builder.define(DATA_QUALITY, 0);
```
Replace:
```java
        if (tag.contains("BroomType")) {
            this.setBroomType(tag.getInt("BroomType"));
        }
```
with:
```java
        if (tag.contains("BroomType")) {
            this.setBroomType(tag.getInt("BroomType"));
        }
        this.setQuality(tag.getInt("Quality"));
```
Replace:
```java
        tag.putInt("BroomType", this.getBroomType());
```
with:
```java
        tag.putInt("BroomType", this.getBroomType());
        tag.putInt("Quality", this.getQuality());
```
Replace:
```java
    public void setBroomType(int type) {
        this.entityData.set(DATA_TYPE, type);
    }
```
with:
```java
    public void setBroomType(int type) {
        this.entityData.set(DATA_TYPE, type);
    }

    public int getQuality() {
        return this.entityData.get(DATA_QUALITY);
    }

    public void setQuality(int quality) {
        this.entityData.set(DATA_QUALITY, com.hogwartsmagic.spell.motion.AlatusRules.clampQuality(quality));
    }
```
In `handlePlayerFlight`, replace:
```java
        double baseSpeed = type == 2 ? 3.0 : (type == 1 ? 2.5 : 2.0);
        double boostSpeed = type == 2 ? 10.0 : (type == 1 ? 8.0 : 6.5);
```
with:
```java
        // Spell Mastery Phase 7b: a Fine broom is 5% faster, a Masterwork 10%.
        double qualitySpeed = com.hogwartsmagic.spell.motion.AlatusRules.speedMultiplier(this.getQuality());
        double baseSpeed = (type == 2 ? 3.0 : (type == 1 ? 2.5 : 2.0)) * qualitySpeed;
        double boostSpeed = (type == 2 ? 10.0 : (type == 1 ? 8.0 : 6.5)) * qualitySpeed;
```
Replace:
```java
                this.staminaF = Math.max(0.0F, this.staminaF - 0.347F);
```
with:
```java
                // A Masterwork broom's boost lasts 15% longer (Spell Mastery Phase 7b).
                this.staminaF = Math.max(0.0F, this.staminaF - 0.347F
                        * (float) com.hogwartsmagic.spell.motion.AlatusRules.staminaDrainMultiplier(this.getQuality()));
```

- [ ] **Step 4: The bench infuses by the caster's rank and rolls the quality.** In `BroomWorkbenchBlockEntity.java`, add these imports to the import block:
```java
import com.hogwartsmagic.item.BroomItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.motion.AlatusRules;
import net.minecraft.world.entity.Entity;
```
Replace:
```java
    private int craftTicksRemaining;
    private int craftAccentPhase;
```
with:
```java
    private int craftTicksRemaining;
    private int craftAccentPhase;
    /** Length of the current infusion, from the caster's Alatus rank (Spell Mastery Phase 7b). */
    private int craftTotalTicks = BROOM_CRAFT_TICKS_TOTAL;
    /** The caster's Alatus rank at the start of the current infusion; it decides the quality roll. */
    private int infusionRank = 1;
```
Replace:
```java
                case 1 -> isInfusing() ? BROOM_CRAFT_TICKS_TOTAL : 0;
```
with:
```java
                case 1 -> isInfusing() ? craftTotalTicks : 0;
```
Replace:
```java
    public boolean isInfusing() {
        return this.craftTicksRemaining > 0;
    }
```
with:
```java
    public boolean isInfusing() {
        return this.craftTicksRemaining > 0;
    }

    public int getCraftTotalTicks() {
        return this.craftTotalTicks;
    }
```
Replace the signature and opening of `beginInfusionFromSpell`:
```java
    public boolean beginInfusionFromSpell(ServerLevel level) {
        if (this.craftTicksRemaining > 0) {
            return false;
        }
        if (!this.inventory.getStackInSlot(SLOT_RESULT).isEmpty()) {
            return false;
        }
        if (!canCraftBroom()) {
            return false;
        }

        this.craftTicksRemaining = BROOM_CRAFT_TICKS_TOTAL;
```
with:
```java
    public boolean beginInfusionFromSpell(ServerLevel level, @Nullable Entity caster) {
        if (this.craftTicksRemaining > 0) {
            return false;
        }
        if (!this.inventory.getStackInSlot(SLOT_RESULT).isEmpty()) {
            return false;
        }
        if (!canCraftBroom()) {
            return false;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ALATUS);

        this.infusionRank = rank;
        this.craftTotalTicks = AlatusRules.infusionTicks(rank);
        this.craftTicksRemaining = this.craftTotalTicks;
```
In `tickInfusionEffects`, replace:
```java
        int total = BROOM_CRAFT_TICKS_TOTAL;
```
with:
```java
        int total = this.craftTotalTicks;
```
Replace the whole `finishCrafting()` method (from `    private void finishCrafting() {` to its closing brace, the line above `    private void spawnInfusionStartBurst(ServerLevel level, BlockPos pos) {`) with:
```java
    private void finishCrafting() {
        ItemStack out = getBroomResultForCurrentInputs();
        if (out == null) {
            return;
        }
        this.inventory.extractItem(SLOT_A, 1, false);
        this.inventory.extractItem(SLOT_B, 1, false);
        this.inventory.extractItem(SLOT_C, 1, false);
        this.inventory.extractItem(SLOT_D, 1, false);
        int quality = this.rollQuality(out);
        this.inventory.setStackInSlot(SLOT_RESULT, out);
        this.playFinishEffects(quality);
        this.setChanged();
    }

    /** From Adept an infusion may make a Fine or Masterwork broom (a normal one carries no component). */
    private int rollQuality(ItemStack broom) {
        RandomSource random = this.level != null ? this.level.random : RandomSource.create();
        int quality = AlatusRules.rollQuality(this.infusionRank, random.nextDouble());
        BroomItem.setQuality(broom, quality);
        return quality;
    }

    private void playFinishEffects(int quality) {
        Level level = this.level;
        if (level == null) {
            return;
        }
        BlockPos pos = this.getBlockPos();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.72;
        double z = pos.getZ() + 0.5;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.35, 0.2, 0.35, 0.04);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 8, 0.4, 0.15, 0.4, 0.0);
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 14, 0.45, 0.18, 0.45, 0.02);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, x, y + 0.1, z, 12, 0.5, 0.25, 0.5, 0.03);
            if (quality == AlatusRules.QUALITY_MASTERWORK) {
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.2, z, 24, 0.4, 0.3, 0.4, 0.15);
            } else if (quality == AlatusRules.QUALITY_FINE) {
                serverLevel.sendParticles(ParticleTypes.WAX_ON, x, y + 0.2, z, 16, 0.4, 0.3, 0.4, 0.05);
            }
        }
        level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.28f, 1.35f);
        level.playSound(null, x, y, z, SoundEvents.BREEZE_SHOOT, SoundSource.BLOCKS, 0.4f, 1.1f);
        level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.55f, 1.2f);
        if (quality != AlatusRules.QUALITY_NORMAL) {
            level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.9f, quality == AlatusRules.QUALITY_MASTERWORK ? 1.4f : 1.1f);
        }
    }
```
In `saveAdditional`, replace:
```java
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
```
with:
```java
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
        tag.putInt("CraftTotal", this.craftTotalTicks);
        tag.putInt("InfusionRank", this.infusionRank);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
```
In `loadAdditional`, replace:
```java
        this.craftTicksRemaining = Math.max(0, tag.getInt("CraftRemaining"));
    }
```
with:
```java
        this.craftTicksRemaining = Math.max(0, tag.getInt("CraftRemaining"));
        this.craftTotalTicks = tag.contains("CraftTotal") ? Math.max(1, tag.getInt("CraftTotal")) : BROOM_CRAFT_TICKS_TOTAL;
        this.infusionRank = tag.contains("InfusionRank") ? tag.getInt("InfusionRank") : 1;
    }
```
In `getUpdateTag`, replace:
```java
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
        return tag;
```
with:
```java
        tag.putInt("CraftRemaining", this.craftTicksRemaining);
        tag.putInt("CraftTotal", this.craftTotalTicks);
        return tag;
```

- [ ] **Step 5: The progress bar uses the real length.** In `BroomWorkbenchMenu.java`, replace:
```java
        return this.valid && this.blockEntity != null && this.blockEntity.isInfusing()
                ? BroomWorkbenchBlockEntity.BROOM_CRAFT_TICKS_TOTAL
                : 0;
```
with:
```java
        return this.valid && this.blockEntity != null && this.blockEntity.isInfusing()
                ? this.blockEntity.getCraftTotalTicks()
                : 0;
```

- [ ] **Step 6: The charm passes its caster.** In `SpellBeamEntity.java`, in the Alatus block branch, replace:
```java
                            if (bbe.beginInfusionFromSpell(sl)) {
```
with:
```java
                            if (bbe.beginInfusionFromSpell(sl, getOwner())) {
```
Run: `grep -rn "beginInfusionFromSpell(" src/main/java`
Expected: two lines (the definition and this call).

- [ ] **Step 7: Strings.** In `en_us.json`, directly after `  "item.hogwartsmagic.firebolt": "Firebolt",` insert:
```json
  "item.hogwartsmagic.broom.quality.fine": "Fine %s",
  "item.hogwartsmagic.broom.quality.masterwork": "Masterwork %s",
  "item.hogwartsmagic.broom.quality.fine.tooltip": "+5%% flight speed",
  "item.hogwartsmagic.broom.quality.masterwork.tooltip": "+10%% flight speed, +15%% boost stamina",
```
Replace:
```json
  "spell.hogwartsmagic.alatus.desc": "Channels flight magic into a Broom Workbench that already holds a valid recipe, beginning the infusion.",
```
with:
```json
  "spell.hogwartsmagic.alatus.desc": "Channels flight magic into a Broom Workbench that holds a valid recipe. The infusion takes 5.5s (Novice), 4s (Adept), 3s (Expert) or 2s (Master). Adept: 15%% chance of a Fine broom (+5%% speed). Expert: Fine 30%%, Masterwork 5%% (+10%% speed, +15%% boost stamina). Master: Fine 40%%, Masterwork 15%%, and you can re-infuse a finished broom with 4 Arcane Dust to reroll its quality.",
```

- [ ] **Step 8: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `broom_quality`, `DataComponentType`, `BroomEntity` or any `client` class (the component and the synced int register on the server).

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/ExampleMod.java src/main/java/com/hogwartsmagic/item/BroomItem.java src/main/java/com/hogwartsmagic/entity/BroomEntity.java src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java src/main/java/com/hogwartsmagic/menu/BroomWorkbenchMenu.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make Alatus infuse faster by rank and roll Fine or Masterwork brooms that fly faster" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Master Alatus re-infuses a broom

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `AlatusRules.reinfuses/REINFUSE_DUST_COST` (Task 2); Task 3's `beginInfusionFromSpell`, `rollQuality`, `playFinishEffects`; `CombatEffects.responsible` (Phase 2).
- Produces: nothing new for later tasks.

A re-infusion is a finished broom (any of the three) in Base, at least 4 Arcane Dust in Magic, and Boost and Catalyst empty. A Master's Alatus on that bench rerolls the broom's quality at Master odds (it can come out better or worse) and consumes 4 dust. Below Master the cast fails with a message and earns no mastery (the existing `masteryEligible = false` path for a failed infusion).

- [ ] **Step 1: Any broom fits the Base slot.** In `BroomWorkbenchBlockEntity.java`, in `isItemValid`, replace:
```java
            // Derived from the recipes, so every ingredient of every recipe fits its slot. The old
            // hand-written lists refused the Nimbus and Firebolt ingredients.
```
with:
```java
            // A finished broom of any kind may sit in Base for a Master's re-infusion.
            if (slot == SLOT_A && stack.getItem() instanceof BroomItem) {
                return true;
            }
            // Derived from the recipes, so every ingredient of every recipe fits its slot. The old
            // hand-written lists refused the Nimbus and Firebolt ingredients.
```

- [ ] **Step 2: Recognise and start a re-infusion.** Add these imports to the import block:
```java
import com.hogwartsmagic.spell.combat.CombatEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
```
Directly above `    private boolean canCraftBroom() {` add:
```java
    /** Master re-infusion: a finished broom in Base, enough Arcane Dust in Magic, Boost and Catalyst empty. */
    private boolean canReinfuse() {
        ItemStack a = this.inventory.getStackInSlot(SLOT_A);
        ItemStack b = this.inventory.getStackInSlot(SLOT_B);
        ItemStack c = this.inventory.getStackInSlot(SLOT_C);
        ItemStack d = this.inventory.getStackInSlot(SLOT_D);
        return a.getItem() instanceof BroomItem && b.isEmpty() && d.isEmpty()
                && c.is(ExampleMod.ARCANE_DUST.get()) && c.getCount() >= AlatusRules.REINFUSE_DUST_COST;
    }

```
In `beginInfusionFromSpell`, replace:
```java
        if (!canCraftBroom()) {
            return false;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ALATUS);
```
with:
```java
        boolean crafting = canCraftBroom();
        boolean reinfusing = !crafting && canReinfuse();
        if (!crafting && !reinfusing) {
            return false;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.ALATUS);
        if (reinfusing && !AlatusRules.reinfuses(rank)) {
            if (CombatEffects.responsible(caster) instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.alatus.reinfuse_master")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return false;
        }
```
In `serverTick`, replace:
```java
        if (!canCraftBroom()) {
            this.craftTicksRemaining = 0;
```
with:
```java
        if (!canCraftBroom() && !canReinfuse()) {
            this.craftTicksRemaining = 0;
```

- [ ] **Step 3: Finish a re-infusion.** Replace Task 3's `finishCrafting()`:
```java
    private void finishCrafting() {
        ItemStack out = getBroomResultForCurrentInputs();
        if (out == null) {
            return;
        }
        this.inventory.extractItem(SLOT_A, 1, false);
        this.inventory.extractItem(SLOT_B, 1, false);
        this.inventory.extractItem(SLOT_C, 1, false);
        this.inventory.extractItem(SLOT_D, 1, false);
        int quality = this.rollQuality(out);
```
with:
```java
    private void finishCrafting() {
        ItemStack out = getBroomResultForCurrentInputs();
        if (out != null) {
            this.inventory.extractItem(SLOT_A, 1, false);
            this.inventory.extractItem(SLOT_B, 1, false);
            this.inventory.extractItem(SLOT_C, 1, false);
            this.inventory.extractItem(SLOT_D, 1, false);
        } else if (canReinfuse()) {
            // The same broom comes back with a fresh roll at the infusing Master's odds.
            out = this.inventory.getStackInSlot(SLOT_A).copyWithCount(1);
            this.inventory.extractItem(SLOT_A, 1, false);
            this.inventory.extractItem(SLOT_C, AlatusRules.REINFUSE_DUST_COST, false);
        } else {
            return;
        }
        int quality = this.rollQuality(out);
```

- [ ] **Step 4: Strings.** In `en_us.json`, replace:
```json
  "gui.hogwartsmagic.broom_workbench.recipe.3": "Nothing crafts until Alatus hits the workbench block while the recipe is complete and the result slot is empty.",
```
with:
```json
  "gui.hogwartsmagic.broom_workbench.recipe.3": "Nothing crafts until Alatus hits the workbench block while the recipe is complete and the result slot is empty. A Master of Alatus can re-infuse a finished broom: broom in Base, 4 Arcane Dust in Magic, the rest empty.",
  "message.hogwartsmagic.alatus.reinfuse_master": "Only a Master of Alatus can re-infuse a finished broom",
```

- [ ] **Step 5: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let a Master of Alatus re-infuse a finished broom with Arcane Dust to reroll its quality" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The carried block entity

**Files:**
- Create: `src/main/java/com/hogwartsmagic/entity/CarriedBlockEntity.java`
- Create: `src/main/java/com/hogwartsmagic/client/renderer/CarriedBlockRenderer.java`
- Modify: `src/main/java/com/hogwartsmagic/entity/ModEntities.java`, `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`

**Interfaces:**
- Consumes: `WingardiumRules.HELD_GRACE_TICKS/IDLE_MAX_TICKS/GRAVITY/DRAG` (Task 2); `TerrainRules.canPlace` (Phase 3).
- Produces:
  - `ModEntities.CARRIED_BLOCK` (`RegistryObject<EntityType<CarriedBlockEntity>>`)
  - `CarriedBlockEntity.setCarried(BlockState state, @Nullable UUID owner)`, `getCarriedState()` → `BlockState` (synced), `markHeld(long gameTime)`, `isHeld()` → `boolean`, `launch(Vec3 velocity, float damage)`
  - `new CarriedBlockRenderer(EntityRendererProvider.Context)`

- [ ] **Step 1: The entity**

`src/main/java/com/hogwartsmagic/entity/CarriedBlockEntity.java`:
```java
package com.hogwartsmagic.entity;

import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.motion.WingardiumRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A block lifted by Wingardium Leviosa (Spell Mastery Phase 7b). While the caster's channel steers it
 * (markHeld every tick) it floats with no gravity; let go, it falls and, once on the ground, places itself
 * through TerrainRules.canPlace as its lifter, or drops as its own item where it may not (claims, wards,
 * spawn protection, an occupied spot, a block that can't stand there). Thrown, it strikes the first
 * creature in its path for the thrower's damage and then falls. It never breaks blocks, is never hurt,
 * and is saved with the world, so a carried block survives a restart.
 */
public class CarriedBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> DATA_BLOCK =
            SynchedEntityData.defineId(CarriedBlockEntity.class, EntityDataSerializers.BLOCK_STATE);

    @Nullable
    private UUID ownerUuid;
    private long heldAt = Long.MIN_VALUE / 2;
    private boolean thrown;
    private float impactDamage;
    private int idleTicks;

    public CarriedBlockEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK, Blocks.STONE.defaultBlockState());
    }

    /** The block it carries and who lifted it (placement rights). Call before adding it to the level. */
    public void setCarried(BlockState state, @Nullable UUID owner) {
        this.entityData.set(DATA_BLOCK, state);
        this.ownerUuid = owner;
    }

    public BlockState getCarriedState() {
        return this.entityData.get(DATA_BLOCK);
    }

    /** The Wingardium channel calls this every tick it steers the block. */
    public void markHeld(long gameTime) {
        this.heldAt = gameTime;
        this.thrown = false;
        this.idleTicks = 0;
    }

    public boolean isHeld() {
        return this.level().getGameTime() - this.heldAt <= WingardiumRules.HELD_GRACE_TICKS;
    }

    /** Thrown by its lifter: flies on {@code velocity}, hits the first creature in its way for {@code damage}. */
    public void launch(Vec3 velocity, float damage) {
        this.heldAt = Long.MIN_VALUE / 2;
        this.thrown = true;
        this.impactDamage = damage;
        this.setDeltaMovement(velocity);
        this.hasImpulse = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return; // the client only draws it; positions arrive every tick (updateInterval 1)
        }
        boolean held = this.isHeld();
        if (!held) {
            this.idleTicks++;
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -WingardiumRules.GRAVITY, 0.0D).scale(WingardiumRules.DRAG));
        }
        Vec3 motion = this.getDeltaMovement();
        if (this.thrown && this.strikeAlongPath(level, motion)) {
            return;
        }
        this.move(MoverType.SELF, motion);
        if (this.thrown && (this.horizontalCollision || this.verticalCollision)) {
            // Hit a wall or a ceiling: the flight ends and it drops from here.
            this.thrown = false;
            this.setDeltaMovement(0.0D, Math.min(0.0D, this.getDeltaMovement().y), 0.0D);
            this.hasImpulse = true;
        }
        if (!held && (this.onGround() || this.idleTicks > WingardiumRules.IDLE_MAX_TICKS)) {
            this.land(level);
        }
    }

    /** True when it hit a creature this tick (it then stops flying and falls). */
    private boolean strikeAlongPath(ServerLevel level, Vec3 motion) {
        Entity owner = this.ownerUuid == null ? null : level.getEntity(this.ownerUuid);
        for (Entity hit : level.getEntities(this, this.getBoundingBox().expandTowards(motion).inflate(0.25D),
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && !candidate.isSpectator()
                        && (this.ownerUuid == null || !this.ownerUuid.equals(candidate.getUUID())))) {
            DamageSource source = this.damageSources().thrown(this, owner);
            if (this.impactDamage > 0.0F) {
                hit.hurt(source, this.impactDamage);
            }
            this.thrown = false;
            this.setDeltaMovement(motion.scale(0.2D));
            this.hasImpulse = true;
            return true;
        }
        return false;
    }

    /** Places itself if it may, otherwise drops as its own item; never lost, never doubled. */
    private void land(ServerLevel level) {
        BlockState state = this.getCarriedState();
        BlockPos pos = this.blockPosition();
        Entity owner = this.ownerUuid == null ? null : level.getEntity(this.ownerUuid);
        if (level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced() && state.canSurvive(level, pos)
                && TerrainRules.canPlace(level, pos, owner)) {
            level.setBlockAndUpdate(pos, state);
        } else {
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
            }
        }
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BlockState")) {
            this.entityData.set(DATA_BLOCK, NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState")));
        }
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.thrown = tag.getBoolean("Thrown");
        this.impactDamage = tag.getFloat("Damage");
        this.idleTicks = tag.getInt("Idle");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(this.getCarriedState()));
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
        tag.putBoolean("Thrown", this.thrown);
        tag.putFloat("Damage", this.impactDamage);
        tag.putInt("Idle", this.idleTicks);
    }
}
```

- [ ] **Step 2: The renderer**

`src/main/java/com/hogwartsmagic/client/renderer/CarriedBlockRenderer.java`:
```java
package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.entity.CarriedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Draws a Wingardium-carried block like a falling block: the block's own model, centred on the entity. */
public class CarriedBlockRenderer extends EntityRenderer<CarriedBlockEntity> {
    private final BlockRenderDispatcher dispatcher;

    public CarriedBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(CarriedBlockEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getCarriedState();
        if (state.getRenderShape() == RenderShape.MODEL) {
            poseStack.pushPose();
            poseStack.translate(-0.5D, 0.0D, -0.5D);
            this.dispatcher.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CarriedBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
```

- [ ] **Step 3: Register it.** In `ModEntities.java`, replace:
```java
                            .sized(1.2F, 1.5F) // Stag size
                            .clientTrackingRange(32)
                            .build("patronus"));
```
with:
```java
                            .sized(1.2F, 1.5F) // Stag size
                            .clientTrackingRange(32)
                            .build("patronus"));

    // A block lifted by Wingardium Leviosa (Spell Mastery Phase 7b). The server steers it every tick,
    // so it sends every tick too; it is not living and has no attributes.
    public static final RegistryObject<EntityType<CarriedBlockEntity>> CARRIED_BLOCK =
            ENTITY_TYPES.register("carried_block",
                    () -> EntityType.Builder.<CarriedBlockEntity>of(CarriedBlockEntity::new, MobCategory.MISC)
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("carried_block"));
```
In `ClientModEvents.java`, replace:
```java
        event.registerEntityRenderer(ModEntities.FIENDFYRE_SERPENT.get(), FiendfyreSerpentRenderer::new);
```
with:
```java
        event.registerEntityRenderer(ModEntities.FIENDFYRE_SERPENT.get(), FiendfyreSerpentRenderer::new);
        event.registerEntityRenderer(ModEntities.CARRIED_BLOCK.get(), com.hogwartsmagic.client.renderer.CarriedBlockRenderer::new);
```

- [ ] **Step 4: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, then in the console `summon hogwartsmagic:carried_block 0 120 0`, then `stop`.
Expected: `Done (`; the summon prints `Summoned new ...`; no stack trace mentioning `CarriedBlockEntity`, `CarriedBlockRenderer` or any `client` class. (The summoned block falls and turns into stone where it lands, or drops a stone item if it may not.)

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/entity/CarriedBlockEntity.java src/main/java/com/hogwartsmagic/client/renderer/CarriedBlockRenderer.java src/main/java/com/hogwartsmagic/entity/ModEntities.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java
git commit -m "Add a carried block entity that floats when held and places itself safely when let go" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Expert Wingardium Leviosa lifts, places and throws

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/motion/WingardiumSpell.java`
- Create: `src/main/resources/data/hogwartsmagic/tags/block/wingardium_immune.json`
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Wingardium block branch)
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`castAs` Wingardium heartbeat)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`controlWingardiumTarget`, `tickWingardiumChannel`, `stopWingardiumChannel`)
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` (Wingardium heartbeat)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `WingardiumRules` (Task 2); `CarriedBlockEntity`, `ModEntities.CARRIED_BLOCK` (Task 5); `TerrainRules.canBreak` (Phase 4, last step); `CombatEffects.responsible` (Phase 2); `SpellMasteryData.getRank` (Phase 1); `ModEvents.markRecentWingardiumTarget(ServerPlayer, Entity)`, `ModEvents.stopWingardiumChannel(ServerPlayer, ItemStack)` (existing).
- Produces:
  - `WingardiumSpell.IMMUNE` (`TagKey<Block>`, `#hogwartsmagic:wingardium_immune`)
  - `WingardiumSpell.liftBlock(ServerLevel level, BlockPos pos, @Nullable Entity caster)` → `boolean`
  - `WingardiumSpell.onChannelStart(ServerPlayer player, boolean attackDown)`, `WingardiumSpell.onHeartbeat(ServerPlayer player, ItemStack wand, boolean attackDown)`, `WingardiumSpell.onChannelStop(ServerPlayer player)` → `boolean` (true if the channel ended in a throw)
  - `ModEvents.controlWingardiumTarget(ServerPlayer caster, Entity target, double lateral)` (now public, with a sideways offset)

The block flow reuses the whole existing channel: the beam lifts the block into a `CarriedBlockEntity` on impact and marks it as the recent Wingardium target, exactly as a mob hit does, so holding the key picks it up like a mob (6-block grab range, line of sight, mana upkeep, 7.5-block hold range). Released, it falls and places itself; never picked up (the key let go before the beam landed), it simply drops back and places itself where it falls. While the channel holds anything, a fresh press of the attack key (sent as the heartbeat's fourth flag) throws it, from Expert.

- [ ] **Step 1: The immune tag**

`src/main/resources/data/hogwartsmagic/tags/block/wingardium_immune.json`:
```json
{
  "replace": false,
  "values": [
    "minecraft:piston_head",
    "hogwartsmagic:patronus_light_block",
    {"id": "hogwartsmagic:lumos_light", "required": false}
  ]
}
```
(Most protected blocks are already refused because they have a block entity, are harder than stone, or are replaceable; the piston head is the one plain block whose removal would break its piston.)

- [ ] **Step 2: The spell**

`src/main/java/com/hogwartsmagic/spell/motion/WingardiumSpell.java`:
```java
package com.hogwartsmagic.spell.motion;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.CarriedBlockEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Wingardium Leviosa from Expert (Spell Mastery Phase 7b): the beam lifts a plain block into a
 * CarriedBlockEntity that the existing channel holds like a mob, and a fresh attack press while holding
 * throws what is held. A thrown mob is tracked for a moment so it hurts what it crashes into. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WingardiumSpell {
    public static final TagKey<Block> IMMUNE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "wingardium_immune"));
    /** The channel's held target: the same key ModEvents and SpellPackets use. */
    private static final String TAG_TARGET = "hogwartsmagic:wingardium_target";
    private static final String TAG_ATTACK_DOWN = "hogwartsmagic:wingardium_attack_down";
    private static final String TAG_THROWN = "hogwartsmagic:wingardium_thrown";
    /** Thrown mobs still in flight, per dimension. */
    private static final Map<ResourceKey<Level>, Map<UUID, ThrownMob>> THROWN_MOBS = new HashMap<>();

    private record ThrownMob(@Nullable UUID throwerId, float damage, long until) {
    }

    private WingardiumSpell() {
    }

    /** The beam struck a block: from Expert, lift it and offer it to the channel. */
    public static boolean liftBlock(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        if (!WingardiumRules.liftsBlocks(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA))) {
            return false;
        }
        CarriedBlockEntity carried = liftOne(level, pos, player);
        if (carried == null) {
            return false;
        }
        ModEvents.markRecentWingardiumTarget(player, carried);
        return true;
    }

    @Nullable
    private static CarriedBlockEntity liftOne(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!isLiftable(level, pos, state)) {
            return null;
        }
        // The last check, after every filter: canBreak posts the real BreakEvent (claims, a Horcrux, the
        // Reparo log), so a true result means the block is taken right now.
        if (!TerrainRules.canBreak(level, pos, state, player)) {
            return null;
        }
        level.removeBlock(pos, false);
        CarriedBlockEntity carried = new CarriedBlockEntity(ModEntities.CARRIED_BLOCK.get(), level);
        carried.setCarried(state, player.getUUID());
        carried.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(carried);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                16, 0.4D, 0.4D, 0.4D, 0.05D);
        return carried;
    }

    private static boolean isLiftable(ServerLevel level, BlockPos pos, BlockState state) {
        boolean multipart = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART)
                || (state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED));
        return WingardiumRules.isLiftable(state.getDestroySpeed(level, pos), state.isAir(), state.hasBlockEntity(),
                state.canBeReplaced(), state.getFluidState().isEmpty(), multipart, state.is(IMMUNE));
    }

    /** The channel just started holding something: remember whether attack was already down. */
    public static void onChannelStart(ServerPlayer player, boolean attackDown) {
        player.getPersistentData().putBoolean(TAG_ATTACK_DOWN, attackDown);
    }

    /** Every held tick: a fresh attack press throws what is held (Expert and up). */
    public static void onHeartbeat(ServerPlayer player, ItemStack wand, boolean attackDown) {
        CompoundTag data = player.getPersistentData();
        boolean wasDown = data.getBoolean(TAG_ATTACK_DOWN);
        data.putBoolean(TAG_ATTACK_DOWN, attackDown);
        if (WingardiumRules.risingEdge(wasDown, attackDown)
                && WingardiumRules.canThrow(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA))) {
            throwHeld(player, wand);
        }
    }

    /** ModEvents.stopWingardiumChannel: clears this spell's keys; true if the channel ended in a throw. */
    public static boolean onChannelStop(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        boolean thrown = data.getBoolean(TAG_THROWN);
        data.remove(TAG_THROWN);
        data.remove(TAG_ATTACK_DOWN);
        return thrown;
    }

    private static void throwHeld(ServerPlayer player, ItemStack wand) {
        ServerLevel level = player.serverLevel();
        CompoundTag data = player.getPersistentData();
        float damage = WingardiumRules.throwDamage(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA));
        Vec3 velocity = player.getLookAngle().normalize().scale(WingardiumRules.THROW_SPEED)
                .add(0.0D, WingardiumRules.THROW_LIFT, 0.0D);
        boolean threw = false;
        if (data.hasUUID(TAG_TARGET)) {
            threw = launch(level, player, level.getEntity(data.getUUID(TAG_TARGET)), velocity, damage);
        }
        if (!threw) {
            return;
        }
        data.putBoolean(TAG_THROWN, true); // stopWingardiumChannel then skips its gentle-release damping
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.8f, 1.1f);
        ModEvents.stopWingardiumChannel(player, wand);
    }

    private static boolean launch(ServerLevel level, ServerPlayer thrower, @Nullable Entity target, Vec3 velocity, float damage) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof CarriedBlockEntity carried) {
            carried.launch(velocity, damage);
            return true;
        }
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        target.hasImpulse = true;
        if (target instanceof ServerPlayer thrownPlayer) {
            thrownPlayer.connection.send(new ClientboundSetEntityMotionPacket(thrownPlayer)); // once, not per tick
        }
        if (target instanceof LivingEntity && damage > 0.0F) {
            THROWN_MOBS.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(target.getUUID(),
                    new ThrownMob(thrower.getUUID(), damage, level.getGameTime() + WingardiumRules.THROW_TRACK_TICKS));
        }
        return true;
    }

    /** A thrown mob hurts the first creature it crashes into (and itself), or itself on a wall. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, ThrownMob> thrown = THROWN_MOBS.get(level.dimension());
        if (thrown == null) {
            return;
        }
        if (thrown.isEmpty()) {
            THROWN_MOBS.remove(level.dimension());
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, ThrownMob>> it = thrown.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ThrownMob> entry = it.next();
            ThrownMob record = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity mob) || !mob.isAlive() || now > record.until()) {
                it.remove();
                continue;
            }
            Entity thrower = record.throwerId() == null ? null : level.getEntity(record.throwerId());
            LivingEntity struck = null;
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.3D),
                    candidate -> candidate != mob && candidate != thrower && candidate.isAlive() && !candidate.isSpectator())) {
                struck = other;
                break;
            }
            if (struck != null) {
                struck.hurt(level.damageSources().thrown(mob, thrower), record.damage());
                mob.hurt(level.damageSources().thrown(mob, thrower), record.damage());
                it.remove();
            } else if (mob.horizontalCollision) {
                mob.hurt(level.damageSources().flyIntoWall(), record.damage());
                it.remove();
            } else if (mob.onGround() && now > record.until() - WingardiumRules.THROW_TRACK_TICKS + 3) {
                it.remove(); // landed without hitting anything; ordinary fall damage applies
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        THROWN_MOBS.clear();
    }
}
```
(`CombatTags` is imported for Task 7.)

- [ ] **Step 3: The beam lifts blocks.** In `SpellBeamEntity.java`, in `onHit`, replace:
```java
                } else if (spell == SpellType.PIERTOTUM_LOCOMOTOR) {
```
with:
```java
                } else if (spell == SpellType.WINGARDIUM_LEVIOSA) {
                    // Expert and up: the struck block rises into a carried block the channel can hold.
                    if (this.level() instanceof ServerLevel wingardiumLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.motion.WingardiumSpell.liftBlock(wingardiumLevel, pos, getOwner());
                    }
                } else if (spell == SpellType.PIERTOTUM_LOCOMOTOR) {
```

- [ ] **Step 4: The channel can throw.** In `SpellPackets.java`, inside `CastSpellPacket.castAs`, in the `if (p.channelHeartbeat && spell == SpellType.WINGARDIUM_LEVIOSA) {` block, replace:
```java
                if (playerData.getBoolean(TAG_WINGARDIUM_CHANNELING)) {
                    playerData.putLong(TAG_WINGARDIUM_LAST_SEEN, now);
                    return;
                }
```
with:
```java
                if (playerData.getBoolean(TAG_WINGARDIUM_CHANNELING)) {
                    playerData.putLong(TAG_WINGARDIUM_LAST_SEEN, now);
                    // The fourth flag carries "attack held" for Wingardium too: a fresh press throws (Expert).
                    com.hogwartsmagic.spell.motion.WingardiumSpell.onHeartbeat(player, wand, p.fiendfyreAdvance);
                    return;
                }
```
and replace:
```java
                playerData.putUUID(TAG_WINGARDIUM_TARGET, target.getUUID());
```
with:
```java
                playerData.putUUID(TAG_WINGARDIUM_TARGET, target.getUUID());
                com.hogwartsmagic.spell.motion.WingardiumSpell.onChannelStart(player, p.fiendfyreAdvance);
```

- [ ] **Step 5: The channel steers carried blocks and knows a throw.** In `ModEvents.java`:

Replace:
```java
    private static void controlWingardiumTarget(ServerPlayer caster, Entity target) {
```
with:
```java
    /** Steers one held thing toward the hold point, {@code lateral} blocks to the caster's right (two held: ±). */
    public static void controlWingardiumTarget(ServerPlayer caster, Entity target, double lateral) {
```
In that method, replace:
```java
        Vec3 look = caster.getLookAngle();
        Vec3 holdPos = caster.getEyePosition().add(look.scale(3.0D));
```
with:
```java
        Vec3 look = caster.getLookAngle();
        Vec3 holdPos = caster.getEyePosition().add(look.scale(3.0D));
        if (lateral != 0.0D) {
            Vec3 right = new Vec3(-look.z, 0.0D, look.x);
            if (right.lengthSqr() > 1.0E-6D) {
                holdPos = holdPos.add(right.normalize().scale(lateral));
            }
        }
        if (target instanceof com.hogwartsmagic.entity.CarriedBlockEntity carried) {
            carried.markHeld(caster.level().getGameTime()); // a held block floats; let go, it falls
        }
```
In `tickWingardiumChannel`, replace:
```java
        controlWingardiumTarget(player, target);
```
with:
```java
        controlWingardiumTarget(player, target, 0.0D);
```
In `stopWingardiumChannel`, replace:
```java
        player.getPersistentData().remove(TAG_WINGARDIUM_LAST_DRAIN);

        if (controlled != null && controlled.isAlive()) {
```
with:
```java
        player.getPersistentData().remove(TAG_WINGARDIUM_LAST_DRAIN);
        // A throw already set the flight; only a plain release gets the gentle drop.
        boolean thrown = com.hogwartsmagic.spell.motion.WingardiumSpell.onChannelStop(player);

        if (controlled != null && controlled.isAlive() && !thrown) {
```

- [ ] **Step 6: The client sends the attack key while holding.** In `ClientModEvents.java`, replace:
```java
                        if (useDown) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                            if (!wingardiumChannelActive) {
```
with:
```java
                        if (useDown) {
                            // The fourth flag is "attack held": a fresh left-click throws (server decides by rank).
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, mc.options.keyAttack.isDown()), PacketDistributor.SERVER.noArg());
                            if (!wingardiumChannelActive) {
```

- [ ] **Step 7: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.wingardium_leviosa.desc": "Causes the target to levitate into the air.",
```
with:
```json
  "spell.hogwartsmagic.wingardium_leviosa.desc": "Hit a creature and keep holding to float it in front of you. Expert: also lifts a block (up to stone, nothing with an inventory); let go to set it down where it lands, or left-click while holding to throw what you hold (3 damage to whatever it hits). Master: holds two at once and a throw deals 6.",
```

- [ ] **Step 8: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `grep -n "controlWingardiumTarget(" src/main/java/com/hogwartsmagic/event/ModEvents.java`
Expected: two lines (the definition and the one call).

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `WingardiumSpell`, `wingardium_immune` or any `client` class (a bad tag entry fails the data load here).

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/data/hogwartsmagic/tags/block/wingardium_immune.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 9: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/motion/WingardiumSpell.java src/main/resources/data/hogwartsmagic/tags/block/wingardium_immune.json src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/java/com/hogwartsmagic/network/SpellPackets.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let an Expert Wingardium Leviosa lift, place and throw blocks" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Master Wingardium Leviosa holds two

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/motion/WingardiumSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`tickWingardiumChannel`)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Wingardium living hit)

**Interfaces:**
- Consumes: `WingardiumRules.maxHeld/SECOND_TARGET_RADIUS/PAIR_SPACING/HOLD_RANGE` (Task 2); Task 6's `WingardiumSpell` and `ModEvents.controlWingardiumTarget(ServerPlayer, Entity, double)`; `CombatTags.isBoss` (Phase 2).
- Produces: `WingardiumSpell.TAG_TARGET_2 = "hogwartsmagic:wingardium_target_2"`, `WingardiumSpell.noteLivingHit(ServerPlayer caster, LivingEntity primary)`, `WingardiumSpell.primaryLateral(ServerPlayer)` → `double`, `WingardiumSpell.tickSecondTarget(ServerPlayer)`

At Master the beam picks up two things: the struck creature plus the nearest other creature (not a boss) within 3 blocks of it, or the struck block plus the plain block above it. Both float side by side, a throw sends both, and Master throws deal 6 (already in `WingardiumRules.throwDamage`). The second one only slips away (out of range or sight) without ending the channel.

- [ ] **Step 1: The second target.** In `WingardiumSpell.java`, replace:
```java
    private static final String TAG_THROWN = "hogwartsmagic:wingardium_thrown";
```
with:
```java
    private static final String TAG_THROWN = "hogwartsmagic:wingardium_thrown";
    /** Master: the second thing the channel holds. */
    public static final String TAG_TARGET_2 = "hogwartsmagic:wingardium_target_2";
    private static final String TAG_RECENT_TARGET_2 = "hogwartsmagic:wingardium_recent_target_2";
    private static final String TAG_RECENT_EXPIRY_2 = "hogwartsmagic:wingardium_recent_expiry_2";
    /** How long a beam-marked second target waits for the channel to start (as the first one does). */
    private static final long RECENT_WINDOW_TICKS = 40L;
```
Replace the whole `liftBlock` method:
```java
    /** The beam struck a block: from Expert, lift it and offer it to the channel. */
    public static boolean liftBlock(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        if (!WingardiumRules.liftsBlocks(SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA))) {
            return false;
        }
        CarriedBlockEntity carried = liftOne(level, pos, player);
        if (carried == null) {
            return false;
        }
        ModEvents.markRecentWingardiumTarget(player, carried);
        return true;
    }
```
with:
```java
    /** The beam struck a block: from Expert, lift it (at Master, the block above it too) and offer it to the channel. */
    public static boolean liftBlock(ServerLevel level, BlockPos pos, @Nullable Entity caster) {
        if (!(CombatEffects.responsible(caster) instanceof ServerPlayer player)) {
            return false;
        }
        int rank = SpellMasteryData.getRank(player, SpellType.WINGARDIUM_LEVIOSA);
        if (!WingardiumRules.liftsBlocks(rank)) {
            return false;
        }
        if (!level.isLoaded(pos) || !isLiftable(level, pos, level.getBlockState(pos))) {
            return false; // the struck block decides; the one above never goes alone
        }
        // The block above first, so sand or gravel on top is carried instead of being left to fall.
        CarriedBlockEntity second = WingardiumRules.maxHeld(rank) >= 2 ? liftOne(level, pos.above(), player) : null;
        CarriedBlockEntity first = liftOne(level, pos, player);
        if (first == null && second == null) {
            return false;
        }
        if (first != null) {
            ModEvents.markRecentWingardiumTarget(player, first);
            if (second != null) {
                markSecondTarget(player, second);
            }
        } else {
            ModEvents.markRecentWingardiumTarget(player, second); // a claim kept the struck block in place
        }
        return true;
    }

    /** The beam struck a creature: at Master, also mark the nearest other creature (not a boss) within reach. */
    public static void noteLivingHit(ServerPlayer caster, LivingEntity primary) {
        if (WingardiumRules.maxHeld(SpellMasteryData.getRank(caster, SpellType.WINGARDIUM_LEVIOSA)) < 2) {
            return;
        }
        double radius = WingardiumRules.SECOND_TARGET_RADIUS;
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (LivingEntity candidate : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(radius),
                c -> c != primary && c != caster && c.isAlive() && c.isPickable() && !c.isSpectator() && !CombatTags.isBoss(c))) {
            double distance = candidate.distanceToSqr(primary);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best != null) {
            markSecondTarget(caster, best);
        }
    }

    private static void markSecondTarget(ServerPlayer player, Entity second) {
        CompoundTag data = player.getPersistentData();
        data.putUUID(TAG_RECENT_TARGET_2, second.getUUID());
        data.putLong(TAG_RECENT_EXPIRY_2, player.level().getGameTime() + RECENT_WINDOW_TICKS);
    }

    /** The first held thing sits to the right while a second one is held, else in the middle. */
    public static double primaryLateral(ServerPlayer player) {
        return player.getPersistentData().hasUUID(TAG_TARGET_2) ? WingardiumRules.PAIR_SPACING : 0.0D;
    }

    /** Steers the second held thing to the left; it alone slips away past range or sight. */
    public static void tickSecondTarget(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(TAG_TARGET_2)) {
            return;
        }
        Entity second = player.serverLevel().getEntity(data.getUUID(TAG_TARGET_2));
        if (second == null || !second.isAlive() || second == player || !second.isPickable()
                || second.distanceToSqr(player) > WingardiumRules.HOLD_RANGE * WingardiumRules.HOLD_RANGE
                || !player.hasLineOfSight(second)) {
            data.remove(TAG_TARGET_2);
            return;
        }
        ModEvents.controlWingardiumTarget(player, second, -WingardiumRules.PAIR_SPACING);
    }

    private static void releaseSecondTarget(ServerPlayer player, boolean thrown) {
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(TAG_TARGET_2)) {
            return;
        }
        Entity second = player.serverLevel().getEntity(data.getUUID(TAG_TARGET_2));
        data.remove(TAG_TARGET_2);
        if (second != null && second.isAlive() && !thrown) {
            // The same gentle drop ModEvents gives the first one.
            Vec3 motion = second.getDeltaMovement();
            second.setDeltaMovement(motion.x * 0.35D, Math.min(motion.y, -0.18D), motion.z * 0.35D);
            second.hurtMarked = true;
            second.hasImpulse = true;
            if (second instanceof ServerPlayer secondPlayer) {
                secondPlayer.connection.send(new ClientboundSetEntityMotionPacket(secondPlayer));
            }
        }
    }
```
Replace the whole `onChannelStart` method:
```java
    /** The channel just started holding something: remember whether attack was already down. */
    public static void onChannelStart(ServerPlayer player, boolean attackDown) {
        player.getPersistentData().putBoolean(TAG_ATTACK_DOWN, attackDown);
    }
```
with:
```java
    /** The channel just started holding something: remember the attack key and take up a marked second target. */
    public static void onChannelStart(ServerPlayer player, boolean attackDown) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(TAG_ATTACK_DOWN, attackDown);
        data.remove(TAG_TARGET_2);
        if (data.hasUUID(TAG_RECENT_TARGET_2) && data.getLong(TAG_RECENT_EXPIRY_2) >= player.level().getGameTime()) {
            Entity second = player.serverLevel().getEntity(data.getUUID(TAG_RECENT_TARGET_2));
            UUID primary = data.hasUUID(TAG_TARGET) ? data.getUUID(TAG_TARGET) : null;
            if (second != null && second.isAlive() && second != player && second.isPickable()
                    && !second.getUUID().equals(primary)) {
                data.putUUID(TAG_TARGET_2, second.getUUID());
            }
        }
        data.remove(TAG_RECENT_TARGET_2);
        data.remove(TAG_RECENT_EXPIRY_2);
    }
```
In `onChannelStop`, replace:
```java
        data.remove(TAG_THROWN);
        data.remove(TAG_ATTACK_DOWN);
        return thrown;
```
with:
```java
        data.remove(TAG_THROWN);
        data.remove(TAG_ATTACK_DOWN);
        releaseSecondTarget(player, thrown);
        return thrown;
```
In `throwHeld`, replace:
```java
        if (data.hasUUID(TAG_TARGET)) {
            threw = launch(level, player, level.getEntity(data.getUUID(TAG_TARGET)), velocity, damage);
        }
        if (!threw) {
```
with:
```java
        if (data.hasUUID(TAG_TARGET)) {
            threw = launch(level, player, level.getEntity(data.getUUID(TAG_TARGET)), velocity, damage);
        }
        if (data.hasUUID(TAG_TARGET_2)) {
            threw |= launch(level, player, level.getEntity(data.getUUID(TAG_TARGET_2)), velocity, damage);
        }
        if (!threw) {
```

- [ ] **Step 2: The channel steers both.** In `ModEvents.java`, in `tickWingardiumChannel`, replace:
```java
        controlWingardiumTarget(player, target, 0.0D);
```
with:
```java
        controlWingardiumTarget(player, target, com.hogwartsmagic.spell.motion.WingardiumSpell.primaryLateral(player));
        com.hogwartsmagic.spell.motion.WingardiumSpell.tickSecondTarget(player);
```

- [ ] **Step 3: The beam marks a second creature.** In `SpellBeamEntity.java`, replace:
```java
                            if (spell == SpellType.WINGARDIUM_LEVIOSA && getOwner() instanceof ServerPlayer caster) {
                                com.hogwartsmagic.event.ModEvents.markRecentWingardiumTarget(caster, living);
                            }
```
with:
```java
                            if (spell == SpellType.WINGARDIUM_LEVIOSA && getOwner() instanceof ServerPlayer caster) {
                                com.hogwartsmagic.event.ModEvents.markRecentWingardiumTarget(caster, living);
                                com.hogwartsmagic.spell.motion.WingardiumSpell.noteLivingHit(caster, living);
                            }
```

- [ ] **Step 4: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/motion/WingardiumSpell.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
git commit -m "Let a Master Wingardium Leviosa hold and throw two things at once" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---
### Task 8: Expert Arresto Momentum freezes projectiles

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/motion/ArrestoFreeze.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (Arresto case)
- Modify: `src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java` (Arresto block branch)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `ArrestoRules` (Task 2); `DisableSupport.casterRank` (Phase 3); `CombatEffects.responsible` (Phase 2); `SpellBeamEntity.getSpellType()`, `BasiliskVenomEntity` (existing).
- Produces: `ArrestoFreeze.freezeAround(ServerLevel level, Vec3 center, @Nullable Entity caster)` → `int` projectiles frozen

The freeze happens wherever Arresto resolves: a beam hitting a creature (around the creature), a beam hitting a block (around the impact), and a left-click self-cast (around the caster, since `applyHitEffect(player, player)` runs the same case). That last one is the "stop the volley coming at me" move.

- [ ] **Step 1: The freezer**

`src/main/java/com/hogwartsmagic/spell/motion/ArrestoFreeze.java`:
```java
package com.hogwartsmagic.spell.motion;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BasiliskVenomEntity;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Arresto Momentum from Expert (Spell Mastery Phase 7b): arrows, tridents, fireballs, spell beams and
 * Basilisk venom near where the charm lands stop in midair for FREEZE_TICKS, then drop. Records live per
 * dimension and hold only UUIDs and positions. A frozen projectile is snapped back to its spot at the end
 * of each level tick (its position doesn't change, so nothing is re-sent); it gets one motion packet when
 * it freezes and one when it drops. At Master the caster's next beam cast redirects the beams they froze
 * (see redirect, Task 9). Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArrestoFreeze {
    private static final Map<ResourceKey<Level>, Map<UUID, Frozen>> FROZEN = new HashMap<>();
    /** Master casters with frozen beams waiting for their next cast, and until when. */
    private static final Map<UUID, Long> REDIRECT_UNTIL = new HashMap<>();

    private record Frozen(Vec3 at, long until, boolean hadNoGravity, double speed, @Nullable UUID frozenBy) {
    }

    private ArrestoFreeze() {
    }

    public static int freezeAround(ServerLevel level, Vec3 center, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.ARRESTO_MOMENTUM);
        if (!ArrestoRules.freezes(rank)) {
            return 0;
        }
        double radius = ArrestoRules.freezeRadius(rank);
        Map<UUID, Frozen> frozen = FROZEN.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Entity responsible = CombatEffects.responsible(caster);
        UUID frozenBy = responsible instanceof ServerPlayer player ? player.getUUID() : null;
        long now = level.getGameTime();
        long until = now + ArrestoRules.FREEZE_TICKS;
        int count = 0;
        boolean beamFrozen = false;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(radius),
                candidate -> isFreezable(candidate) && candidate.position().distanceToSqr(center) <= radius * radius)) {
            if (frozen.size() >= ArrestoRules.MAX_FROZEN_PER_LEVEL) {
                break;
            }
            if (frozen.containsKey(projectile.getUUID())) {
                continue;
            }
            frozen.put(projectile.getUUID(), new Frozen(projectile.position(), until, projectile.isNoGravity(),
                    projectile.getDeltaMovement().length(), frozenBy));
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(Vec3.ZERO);
            projectile.hasImpulse = true; // one motion packet: clients stop predicting its flight
            level.sendParticles(ParticleTypes.GLOW, projectile.getX(), projectile.getY(), projectile.getZ(),
                    6, 0.15D, 0.15D, 0.15D, 0.01D);
            count++;
            if (projectile instanceof SpellBeamEntity) {
                beamFrozen = true;
            }
        }
        if (count > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        if (beamFrozen && frozenBy != null && ArrestoRules.redirects(rank)) {
            REDIRECT_UNTIL.put(frozenBy, now + ArrestoRules.REDIRECT_WINDOW_TICKS);
        }
        return count;
    }

    /** In flight (a stuck arrow has no motion), and one of the kinds the charm stops; never Arresto's own beam. */
    private static boolean isFreezable(Projectile projectile) {
        if (!projectile.isAlive() || projectile.getDeltaMovement().lengthSqr() < 1.0E-4D) {
            return false;
        }
        if (projectile instanceof SpellBeamEntity beam) {
            return beam.getSpellType() != SpellType.ARRESTO_MOMENTUM;
        }
        return projectile instanceof AbstractArrow
                || projectile instanceof AbstractHurtingProjectile
                || projectile instanceof BasiliskVenomEntity;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Frozen> frozen = FROZEN.get(level.dimension());
        if (frozen == null) {
            return;
        }
        if (frozen.isEmpty()) {
            FROZEN.remove(level.dimension());
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Frozen>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> entry = it.next();
            Frozen record = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                it.remove();
                continue;
            }
            if (ArrestoRules.released(record.until(), now)) {
                release(entity, record);
                it.remove();
                continue;
            }
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(record.at().x, record.at().y, record.at().z);
            if (entity instanceof SpellBeamEntity beam) {
                // Beams fizzle at 200 ticks of age; a beam frozen late in its flight must outlast the freeze.
                beam.tickCount = Math.min(beam.tickCount, ArrestoRules.BEAM_LIFE_CAP);
            }
            if (now % 4L == 0L) {
                level.sendParticles(ParticleTypes.GLOW, record.at().x, record.at().y, record.at().z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
        }
    }

    /** Drops it: gravity restored to what it had; beams and fireballs (no gravity of their own) are sent down. */
    private static void release(Entity entity, Frozen record) {
        entity.setNoGravity(record.hadNoGravity());
        if (entity instanceof SpellBeamEntity || entity instanceof AbstractHurtingProjectile) {
            entity.setDeltaMovement(0.0D, -ArrestoRules.DROP_SPEED, 0.0D);
        } else {
            entity.setDeltaMovement(Vec3.ZERO);
        }
        entity.hasImpulse = true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FROZEN.clear();
        REDIRECT_UNTIL.clear();
    }
}
```
(`HitResult` is imported for Task 9.)

- [ ] **Step 2: Freeze when the charm resolves on a creature or yourself.** In `SpellType.java`, in the Arresto case, replace:
```java
                target.setDeltaMovement(dampedX, Math.max(motion.y, minY), dampedZ);
                target.fallDistance = 0.0f;
                target.hurtMarked = true;
```
with:
```java
                target.setDeltaMovement(dampedX, Math.max(motion.y, minY), dampedZ);
                target.fallDistance = 0.0f;
                target.hurtMarked = true;
                // Spell Mastery Phase 7b: from Expert the charm also stops projectiles around where it lands
                // (around the caster on a left-click self-cast).
                if (target.level() instanceof ServerLevel arrestoLevel) {
                    com.hogwartsmagic.spell.motion.ArrestoFreeze.freezeAround(arrestoLevel,
                            target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), caster);
                }
```

- [ ] **Step 3: Freeze around a block impact.** In `SpellBeamEntity.java`, in `onHit`, replace:
```java
                } else if (spell == SpellType.PIERTOTUM_LOCOMOTOR) {
```
with:
```java
                } else if (spell == SpellType.ARRESTO_MOMENTUM) {
                    // Expert and up: projectiles around the impact stop in midair (nothing else happens to a block).
                    if (this.level() instanceof ServerLevel arrestoLevel) {
                        hitSuccessful = com.hogwartsmagic.spell.motion.ArrestoFreeze.freezeAround(arrestoLevel, this.position(), getOwner()) > 0;
                    }
                } else if (spell == SpellType.PIERTOTUM_LOCOMOTOR) {
```
(Arresto is not a block-utility spell, so a successful block freeze earns no mastery XP; only the impact effects play.)

- [ ] **Step 4: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.arresto_momentum.desc": "Softens a fall and bleeds away momentum. Self-cast to survive a plunge or strike a target to slow its descent.",
```
with:
```json
  "spell.hogwartsmagic.arresto_momentum.desc": "Softens a fall and bleeds away momentum. Self-cast to survive a plunge or strike a target to slow its descent. Expert: arrows, fireballs, tridents and spell beams within 4 blocks of where it lands (around you on a self-cast) stop in midair for 2s, then drop. Master: 6 blocks, and your next cast within 2s sends the frozen spells where you aim.",
```

- [ ] **Step 5: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `ArrestoFreeze` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/motion/ArrestoFreeze.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Make an Expert Arresto Momentum stop nearby arrows, fireballs, tridents and spell beams in midair" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Master Arresto Momentum redirects frozen beams

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/spell/motion/ArrestoFreeze.java`
- Modify: `src/main/java/com/hogwartsmagic/network/SpellPackets.java` (`castAs`)

**Interfaces:**
- Consumes: `ArrestoRules.inRedirectWindow` (Task 2); Task 8's `ArrestoFreeze`; `SpellBeamEntity(Level, LivingEntity, SpellType)`, `setCharge/getCharge`, `setElderWandCast/isElderWandCast`, `shoot` (existing), `SpellBeamEntity.markReflected()` (Phase 5a).
- Produces: `ArrestoFreeze.redirect(ServerPlayer caster)` → `int` beams sent

"Your next cast" is the next beam cast: right before the cast path launches its own beam, the frozen beams this Master froze are relaunched from where they hang toward the point under the crosshair (up to 64 blocks), at their original speed. Like a Protego reflection, each is replaced by a fresh beam owned by the redirector and marked reflected, so it earns no mastery XP and can't be reflected again. Other frozen projectiles (arrows, fireballs) stay frozen and drop as normal.

- [ ] **Step 1: The redirect.** In `ArrestoFreeze.java`, directly above:
```java
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
```
add:
```java
    /** Master: the caster's next beam cast within the window sends the beams they froze toward their aim. */
    public static int redirect(ServerPlayer caster) {
        Long until = REDIRECT_UNTIL.remove(caster.getUUID());
        if (until == null) {
            return 0;
        }
        ServerLevel level = caster.serverLevel();
        if (!ArrestoRules.inRedirectWindow(until, level.getGameTime())) {
            return 0;
        }
        Map<UUID, Frozen> frozen = FROZEN.get(level.dimension());
        if (frozen == null) {
            return 0;
        }
        HitResult aim = caster.pick(64.0D, 1.0F, false);
        Vec3 aimPoint = aim.getLocation();
        int sent = 0;
        Iterator<Map.Entry<UUID, Frozen>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> entry = it.next();
            Frozen record = entry.getValue();
            if (!caster.getUUID().equals(record.frozenBy())) {
                continue;
            }
            if (!(level.getEntity(entry.getKey()) instanceof SpellBeamEntity beam) || !beam.isAlive()) {
                continue;
            }
            Vec3 direction = aimPoint.subtract(record.at());
            if (direction.lengthSqr() < 1.0E-4D) {
                direction = caster.getLookAngle();
            }
            direction = direction.normalize();
            SpellBeamEntity copy = new SpellBeamEntity(level, caster, beam.getSpellType());
            copy.setCharge(beam.getCharge());
            copy.setElderWandCast(beam.isElderWandCast());
            copy.markReflected(); // no mastery XP for its new owner, and never reflected again
            copy.setPos(record.at().x, record.at().y, record.at().z);
            copy.shoot(direction.x, direction.y, direction.z, (float) Math.max(1.0D, record.speed()), 0.0F);
            level.addFreshEntity(copy);
            beam.discard();
            it.remove();
            sent++;
        }
        if (sent > 0) {
            level.sendParticles(ParticleTypes.END_ROD, caster.getX(), caster.getEyeY(), caster.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.05D);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        }
        return sent;
    }

```

- [ ] **Step 2: The cast path triggers it.** In `SpellPackets.java`, inside `CastSpellPacket.castAs`, replace:
```java
            SpellBeamEntity beam = new SpellBeamEntity(player.level(), player, spell);
```
with:
```java
            // Arresto Momentum Master (Spell Mastery Phase 7b): the next beam cast also sends the beams
            // this caster froze toward where they aim.
            com.hogwartsmagic.spell.motion.ArrestoFreeze.redirect(player);
            SpellBeamEntity beam = new SpellBeamEntity(player.level(), player, spell);
```

- [ ] **Step 3: Build and test**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `grep -n "ArrestoFreeze.redirect" src/main/java/com/hogwartsmagic/network/SpellPackets.java`
Expected: one line.

- [ ] **Step 4: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/motion/ArrestoFreeze.java src/main/java/com/hogwartsmagic/network/SpellPackets.java
git commit -m "Let a Master of Arresto Momentum send frozen spell beams where they aim with their next cast" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Tenebris Flight pass-through and the Master ram

**Files:**
- Create: `src/main/java/com/hogwartsmagic/spell/motion/TenebrisFlightSpell.java`
- Modify: `src/main/java/com/hogwartsmagic/spell/SpellType.java` (Tenebris case)
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java` (`handleTenebrisFlight`, `onLivingUpdate`)
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces:**
- Consumes: `TenebrisRules` (Task 2); `DisableSupport.casterRank` (Phase 3); `CombatTags.ALLIED` (Phase 2); `ExampleMod.TENEBRIS_FLIGHT_EFFECT`, `StudentEntity` (existing); Forge `ProjectileImpactEvent` with `ImpactResult.SKIP_ENTITY` (arrows then ignore that entity for the rest of their flight; beams, fireballs and other throwables skip the hit for that tick, so the roll is cached per projectile and flyer and answered the same way every tick).
- Produces: `TenebrisFlightSpell.TAG_RAMS = "hogwartsmagic:tenebris_rams"`, `TenebrisFlightSpell.tickRam(ServerPlayer player, int rank)`

The flight effect's amplifier now carries the caster's rank (rank − 1), so the listener and the ram read the rank from the effect alone. Durations stay today's 40 ticks: the flight already makes the flyer immune to damage, so longer flights would be a balance change outside this phase. What pass-through adds is that a beam's non-damage payload (a stun, a silence, a Petrificus) misses, and an arrow flies on to whatever is behind.

- [ ] **Step 1: The listener and the ram**

`src/main/java/com/hogwartsmagic/spell/motion/TenebrisFlightSpell.java`:
```java
package com.hogwartsmagic.spell.motion;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.spell.combat.CombatTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tenebris Flight by rank (Spell Mastery Phase 7b). From Expert, projectiles and spell beams that would
 * hit a flyer pass through it 30% (Master 50%) of the time; the roll is made once per projectile and
 * flyer and stored on the projectile, so a beam that keeps overlapping the flyer gets the same answer
 * every tick. At Master, a flying player who rams an enemy deals 5 damage and Blindness, once per second
 * per target. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TenebrisFlightSpell {
    /** Flyer persistent data: target UUID → game time of the last ram. Cleared when the flight ends. */
    public static final String TAG_RAMS = "hogwartsmagic:tenebris_rams";
    /** Projectile persistent data: flyer UUID → whether it passes through that flyer. */
    private static final String TAG_ROLLS = "hogwartsmagic:tenebris_rolls";

    private TenebrisFlightSpell() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        Entity struck = hit.getEntity();
        if (struck instanceof PartEntity<?> part) {
            struck = part.getParent();
        }
        if (!(struck instanceof LivingEntity flyer) || flyer.level().isClientSide()) {
            return;
        }
        // Effect first: only a flyer's hits ever touch the projectile's persistent data.
        MobEffectInstance flight = flyer.getEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get());
        if (flight == null) {
            return;
        }
        int rank = flight.getAmplifier() + 1;
        if (TenebrisRules.passChance(rank) <= 0.0D) {
            return;
        }
        Projectile projectile = event.getProjectile();
        CompoundTag rolls = projectile.getPersistentData().getCompound(TAG_ROLLS);
        String key = flyer.getStringUUID();
        boolean passes;
        if (rolls.contains(key)) {
            passes = rolls.getBoolean(key);
        } else {
            passes = TenebrisRules.passes(rank, flyer.getRandom().nextDouble());
            rolls.putBoolean(key, passes);
            projectile.getPersistentData().put(TAG_ROLLS, rolls);
            if (passes && flyer.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, projectile.getX(), projectile.getY(), projectile.getZ(),
                        8, 0.2D, 0.2D, 0.2D, 0.01D);
            }
        }
        if (passes) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    /** Master: called every tick a player flies in smoke form (ModEvents.handleTenebrisFlight). */
    public static void tickRam(ServerPlayer player, int rank) {
        if (!TenebrisRules.rams(rank)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        CompoundTag rams = player.getPersistentData().getCompound(TAG_RAMS);
        boolean changed = false;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(TenebrisRules.RAM_REACH), candidate -> isRamTarget(player, candidate))) {
            String key = victim.getStringUUID();
            if (!TenebrisRules.ramReady(rams.contains(key), rams.getLong(key), now)) {
                continue;
            }
            if (victim.hurt(player.damageSources().playerAttack(player), TenebrisRules.RAM_DAMAGE)) {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TenebrisRules.RAM_BLIND_TICKS, 0), player);
                Vec3 motion = player.getDeltaMovement();
                if (motion.horizontalDistanceSqr() > 1.0E-4D) {
                    victim.knockback(0.6D, -motion.x, -motion.z);
                }
                level.sendParticles(ParticleTypes.SQUID_INK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                        16, 0.3D, 0.4D, 0.3D, 0.05D);
            }
            rams.putLong(key, now);
            changed = true;
        }
        if (changed) {
            player.getPersistentData().put(TAG_RAMS, rams);
        }
    }

    /** Hostile mobs (not Students), mobs hunting the flyer, and players the flyer may harm; never allies. */
    private static boolean isRamTarget(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isSpectator() || candidate instanceof ArmorStand) {
            return false;
        }
        if (candidate.getTags().contains(CombatTags.ALLIED) || candidate.isAlliedTo(player)) {
            return false;
        }
        if (candidate instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        if (candidate instanceof Player other) {
            return !other.isCreative() && player.canHarmPlayer(other);
        }
        if (candidate instanceof Mob mob && mob.getTarget() == player) {
            return true;
        }
        return candidate instanceof Enemy && !(candidate instanceof StudentEntity);
    }
}
```

- [ ] **Step 2: The flight carries the caster's rank.** In `SpellType.java`, replace the whole Tenebris case:
```java
            case TENEBRIS_FLIGHT -> {
                // effect lasts only 2 seconds (40 ticks)
                if (caster instanceof LivingEntity livingCaster) {
                    livingCaster.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
                    if (livingCaster instanceof com.hogwartsmagic.entity.WizardEntity wz) {
                        wz.setFlying(true); // set flying flag (invisibility via effect)
                    }
                }
                target.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
                if (target instanceof com.hogwartsmagic.entity.WizardEntity wz2) {
                    wz2.setFlying(true);
                }
            }
```
with:
```java
            case TENEBRIS_FLIGHT -> {
                // effect lasts only 2 seconds (40 ticks). The caster's amplifier carries their rank (Spell
                // Mastery Phase 7b): projectiles pass through from Expert, the smoke ram hurts at Master.
                int tenebrisAmplifier = com.hogwartsmagic.spell.motion.TenebrisRules.amplifier(
                        com.hogwartsmagic.spell.disable.DisableSupport.casterRank(caster, this));
                if (caster instanceof LivingEntity livingCaster) {
                    livingCaster.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, tenebrisAmplifier));
                    if (livingCaster instanceof com.hogwartsmagic.entity.WizardEntity wz) {
                        wz.setFlying(true); // set flying flag (invisibility via effect)
                    }
                }
                if (target != caster) {
                    target.addEffect(new MobEffectInstance(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get(), 40, 0));
                }
                if (target instanceof com.hogwartsmagic.entity.WizardEntity wz2) {
                    wz2.setFlying(true);
                }
            }
```

- [ ] **Step 3: The ram while flying, and cleanup when the flight ends.** In `ModEvents.java`, in `handleTenebrisFlight`, replace:
```java
            if (player.getAbilities().flying) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 10, 5, false, false, false));
            }
```
with:
```java
            if (player.getAbilities().flying) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 10, 5, false, false, false));
                // Spell Mastery Phase 7b: a Master's smoke form rams what it flies into.
                net.minecraft.world.effect.MobEffectInstance flight = player.getEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get());
                if (player instanceof ServerPlayer serverPlayer && flight != null) {
                    com.hogwartsmagic.spell.motion.TenebrisFlightSpell.tickRam(serverPlayer, flight.getAmplifier() + 1);
                }
            }
```
In `onLivingUpdate`, replace:
```java
                if (player.getAbilities().mayfly && player.getPersistentData().getBoolean("hogwartsmagic:tenebris_flying")) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.getPersistentData().remove("hogwartsmagic:tenebris_flying");
```
with:
```java
                if (player.getAbilities().mayfly && player.getPersistentData().getBoolean("hogwartsmagic:tenebris_flying")) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.getPersistentData().remove("hogwartsmagic:tenebris_flying");
                    player.getPersistentData().remove(com.hogwartsmagic.spell.motion.TenebrisFlightSpell.TAG_RAMS);
```

- [ ] **Step 4: Strings.** In `en_us.json`, replace:
```json
  "spell.hogwartsmagic.tenebris_flight.desc": "Death Eater Flight: Transform into black smoke for high-speed invulnerable flight.",
```
with:
```json
  "spell.hogwartsmagic.tenebris_flight.desc": "Death Eater Flight: Transform into black smoke for high-speed invulnerable flight. Expert: 30%% of arrows and spells that would hit you pass straight through. Master: 50%%, and flying into an enemy deals 5 damage and blinds it.",
```

- [ ] **Step 5: Build, test and boot a dedicated server**

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew compileJava test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

Run: `./gradlew runServer`, wait for `Done (`, type `stop`.
Expected: `Done (`; no stack trace mentioning `TenebrisFlightSpell` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 6: Commit**
```bash
git add src/main/java/com/hogwartsmagic/spell/motion/TenebrisFlightSpell.java src/main/java/com/hogwartsmagic/spell/SpellType.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/resources/assets/hogwartsmagic/lang/en_us.json
git commit -m "Let spells and arrows pass through Expert Tenebris Flight and give the Master smoke form a ram" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Full verification

**Files:** none new.

**Interfaces:** none.

- [ ] **Step 1: Automated checks**

Run: `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/spell/motion src/main/java/com/hogwartsmagic/entity/CarriedBlockEntity.java src/main/java/com/hogwartsmagic/item/BroomItem.java src/main/java/com/hogwartsmagic/block/entity/BroomWorkbenchBlockEntity.java`
Expected: no output (common code stays client-free).

Run: `grep -rn "switch (" src/main/java/com/hogwartsmagic/client/renderer/CarriedBlockRenderer.java src/main/java/com/hogwartsmagic/client/BroomWorkbenchScreen.java`
Expected: no output.

Run: `grep -rn "broadcastEntityEvent" src/main/java/com/hogwartsmagic/spell/motion src/main/java/com/hogwartsmagic/entity/CarriedBlockEntity.java`
Expected: no output.

Run: `tasklist | grep -i javaw` (expect no output), then `./gradlew test`, then `./gradlew runServer` until `Done (`, then `stop`.
Expected: all tests pass; the server boots with no stack trace mentioning `spell.motion`, `CarriedBlockEntity`, `broom_quality`, `wingardium_immune` or any `client` class.

Run: `python -c "import json; json.load(open('src/main/resources/assets/hogwartsmagic/lang/en_us.json', encoding='utf-8')); json.load(open('src/main/resources/data/hogwartsmagic/tags/block/wingardium_immune.json', encoding='utf-8'))"`
Expected: no output.

- [ ] **Step 2: In-game checklist** (`./gradlew runClient`; don't run gradle again until the client is closed)

Survival world, cheats on, wand in hand, the four spells learned. Switch ranks with `/spellmastery setrank Dev <spell> <1-4>`.
1. Broom Workbench: Phantom Membrane goes into Boost, Netherite Ingot into Magic, Elytra into Magic (by click and by shift-click); Feather and Gold Block no longer fit. Alatus crafts a Nimbus 2000 from a Training Broom and a Firebolt from a Nimbus. The screen is not dimmed twice.
2. Alatus I: the progress bar fills in 5.5s; the broom has no prefix. Alatus IV: 2s. Over a dozen Master infusions some come out "Fine ..." (aqua "+5% flight speed" tooltip) and some "Masterwork ..." (purple tooltip), each with its own sparkle.
3. Ride a Masterwork Firebolt beside a normal one: it is visibly faster and its boost bar lasts longer. Log out and back in while riding: still Masterwork (saved on the entity).
4. Re-infusion: a broom in Base and 4 Arcane Dust in Magic, Boost and Catalyst empty. At Alatus III: "Only a Master of Alatus can re-infuse a finished broom", nothing consumed. At IV: the same broom comes back with a new quality and 4 dust are gone.
5. Wingardium Leviosa II on stone: nothing (fizzle). III on a stone block within 6 blocks, keep holding: the block rises and floats in front of you; let go: it falls and becomes stone where it lands. On a chest, obsidian, a door or short grass: nothing.
6. Wingardium III, holding a block, left-click: it flies; it hits a zombie for 3 and drops. Holding a zombie, left-click: it flies and hurts whatever it crashes into (and itself). Throw a block into a wall: it drops and is placed at the foot of the wall.
7. In a claimed area or spawn protection (non-op on a server), or inside a Cave Inimicum ward: the block can't be lifted. Lift a block outside and let it fall into the protected area: it drops as an item instead of being placed.
8. Wingardium IV on a zombie standing next to a pig: both float side by side; left-click throws both, each dealing 6 to what it hits. On a dirt block with sand on top: both rise together, the sand doesn't fall.
9. Arresto III: have a skeleton shoot at you and left-click self-cast Arresto: arrows within 4 blocks hang in the air for 2s, then drop. Cast it at a Blaze's fireball volley: fireballs stop, then fall and burst on the ground. A Death Eater's Stupefy beam near the impact stops too.
10. Arresto IV: freeze a Death Eater's spell beam, then within 2s cast any beam spell while aiming at the Death Eater: the frozen beam flies at him as yours (no mastery XP for its spell); after 2s without casting it just drops.
11. Tenebris Flight III, a skeleton shooting you while you fly: roughly a third of the arrows sail through (smoke puff) and hit what is behind you. Tenebris IV: about half; fly into a zombie: it takes 5 and is blinded for 2s, at most once a second.
12. On a dedicated server with two clients: one player throws the other with Wingardium (Expert): the thrown player flies smoothly once, no rubber-banding while held. Arrows frozen in front of player B look frozen on both screens.

- [ ] **Step 3: Commit** (only if Step 1 or 2 needed a fix; otherwise nothing to commit)
```bash
git add <the files you fixed>
git commit -m "Fix issues found in the Phase 7b verification pass" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage (roadmap Phase 7, second half; design doc E, G, H):** Broom bench slot filter fixed first, so Nimbus and Firebolt craft (T1); Alatus infusion time by rank (T3), Fine and Masterwork rolls by rank stored as an item data component (T2, T3), speed and stamina applied in `BroomEntity` (T3), Master re-infuse with Arcane Dust (T4). Wingardium Expert lifts a non-block-entity block up to stone hardness through `TerrainRules.canBreak` as the last step, carries it as a falling-block-like entity, places it (through `TerrainRules.canPlace`) or throws it (T5, T6); Master holds two and thrown objects deal 6 (T2, T7). Arresto Expert freezes arrows, fireballs, tridents and spell beams within 4 blocks for 2s, then drops them (T8); Master radius 6 and the next cast redirects frozen beams (T2, T9). Tenebris Expert 30% / Master 50% pass-through for projectiles and beams (T10); Master ram 5 damage plus Blindness 2s (T10).
- **Lessons applied:** per-tick handlers gate first (the pass-through listener checks the flight effect before the projectile's persistent data; the ram runs only while flying with the effect; freeze and thrown-mob maps are skipped per dimension when empty); no teleports or snaps into blocks (a frozen projectile is held at its own position); every stored position lives in its dimension's map; no shared effect is removed, and a frozen projectile's `noGravity` goes back to its own previous value; no per-tick velocity packets to players (one on a throw, none while frozen); the new entity registers its renderer client-side only and syncs its block state through `defineSynchedData`; no entity event bytes.
- **Placeholder scan:** no TBD or "similar to"; every edit names its anchor text, including anchors inside files this plan creates (`WingardiumSpell`, `ArrestoFreeze`, the Task 3 `finishCrafting`).
- **Type consistency:** `BroomWorkbenchBlockEntity.beginInfusionFromSpell(ServerLevel, Entity)` defined in T3, its one caller updated in T3; `BroomItem.quality/setQuality` used by T3 and T4; `BroomEntity.setQuality(int)` used by `BroomItem.use`; `CarriedBlockEntity.markHeld(long)` / `launch(Vec3, float)` defined in T5, used in T6; `ModEvents.controlWingardiumTarget(ServerPlayer, Entity, double)` changed in T6 and called by T7's `tickSecondTarget`; `WingardiumSpell.onChannelStart(ServerPlayer, boolean)`, `onHeartbeat(ServerPlayer, ItemStack, boolean)`, `onChannelStop(ServerPlayer)` → `boolean` used by `SpellPackets` and `ModEvents`; `ArrestoFreeze.freezeAround(ServerLevel, Vec3, Entity)` → `int` used by `SpellType` and `SpellBeamEntity`, `redirect(ServerPlayer)` used by `castAs`; `TenebrisRules.amplifier(int)` written by `SpellType`, read back as `getAmplifier() + 1` by `TenebrisFlightSpell`.
