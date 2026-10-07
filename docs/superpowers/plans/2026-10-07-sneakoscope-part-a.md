# Sneakoscope Part A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. In the cloud container the superpowers plugin is not installed: the controller cuts briefs from this plan.

**Goal:** One new item, the Sneakoscope: a small glass spinning top on a brass base. Carried anywhere in the inventory, it spins, glows and whistles to its holder alone, faster as a Death Eater, Lord Voldemort, a Dementor or the Dark Mark comes closer. Placed, it is a redstone sensor: signal 1 at the edge of its range up to 15 at the source, from every side, light 7 and a spinning model while it detects, and one whistle when it starts detecting. It is crafted or bought from the Curio Dealer.

**Architecture:**
- **Pure rules** (`sneakoscope/SneakoscopeRules`): the source table (`Source` with a `Reach`), the ranges, the strength formula, "the strongest source wins", the whistle interval, the once-a-second phase, the whistle schedule for a carried Sneakoscope and the "whistle on rise" rule for a placed one. Minecraft-free, test-first.
- **Sensor** (`sneakoscope/SneakoscopeSensor`): one list of probes (a `Source` and an entity class). `strength(level, centre, holder)` asks `getEntitiesOfClass` for each probe inside a box (never loads a chunk) and hands the distances to the rules. It also plays the two whistles (to one player, or from a block). Part B adds one `Source` constant and one probe; nothing else changes.
- **Placed:** `SneakoscopeBlock extends BaseEntityBlock` with the vanilla `POWER` property (0..15). The signal and the light are read from the state. A `SneakoscopeBlockEntity` (no saved data) has a server ticker that works once a second, phased by position, and calls `setBlock` only when the power changes.
- **Carried:** `SneakoscopeCarried.tick(ServerPlayer)` runs from the existing `ModEvents.onPlayerTick`. Once a second (phased by entity id) it scans the inventory and the area; every tick it plays a due whistle. The spin is a transient, network-synced data component `hogwartsmagic:sneakoscope_spinning` on each Sneakoscope stack, set only when it changes, and read on the client by an item model property `hogwartsmagic:spinning`.
- **Look:** a new generator paints a brass sheet, a glass sheet and a 6-frame spinning glass sheet. Two block models (still, spinning with emissive glass faces), a blockstate with 16 `power` variants, two item models with an override.

**Tech Stack:** Forge 51.0.33, Minecraft 1.21 (official mappings), Java 21, JUnit 5, Python 3 with Pillow.

**Spec:** `docs/superpowers/specs/2026-10-07-sneakoscope-design.md`: "Decisions" 1-10 as they apply to Part A, "Part A", "Global constraints". Part B (dark players) is out of scope.

## Requirements (Part A of the spec)

1. One item `sneakoscope`, a `BlockItem` for `SneakoscopeBlock`; breaking the block drops the item (decision 1).
2. Pure `SneakoscopeRules`, test-first: the source table, the ranges (16; Dark Mark 48), `strength = 15 * (1 - d / range)` rounded up, the whistle interval (40 ticks at strength 1 to 10 at 15) (decisions 2, 3).
3. Detection of Death Eaters (`WizardEntity`), Voldemort (`VoldemortEntity`), Dementors (`DementorEntity`), the Dark Mark (`DarkMarkEntity`), never loading a chunk (decisions 2, 5).
4. Carried: one check per player per second for anyone with a Sneakoscope anywhere in the inventory; a whistle only the holder hears, faster as the source comes closer; the item spins and glows while it detects, through a data component and an item model property; no chat or action bar line (decision 4).
5. Placed: a block entity that checks once a second, staggered by position; blockstate `power` 0..15; a signal source with `getSignal = power` on every side; one whistle for everyone within 16 blocks when power goes from 0 to more; a still and a spinning model; light 7 while power > 0 (decision 5).
6. A glass top on a brass base, about 6 x 8 x 6 px, textures from a generator in `build_scripts/`, an animated spinning texture (decision 6).
7. Sounds: `NOTE_BLOCK_FLUTE` at pitch 2.0 with `NOTE_BLOCK_CHIME` at 1.8 (decision 7).
8. Recipe (glass pane, gold nuggets, arcane dust, gold ingot) and a Curio Dealer trade at level 2 for 14 emeralds (decision 8).
9. Server authoritative, no new packet (decision 9).
10. Config `sneakoscopeRange` (16) and `sneakoscopeDarkMarkRange` (48) (decision 10; `sneakoscopeDetectsPlayers` is Part B).
11. Done when: a carried Sneakoscope whistles faster as a Death Eater comes closer; a placed one lights a redstone lamp when a Dementor flies past, with a comparator-readable strength that rises as it comes closer; a Dark Mark 40 blocks up gives a weak signal; `runServer` boots clean.

## Rulings made while planning

1. **The spin is a registered data component, not `CustomData`.** The mod already has a `DATA_COMPONENTS` register (`ExampleMod.BROOM_QUALITY`). `SNEAKOSCOPE_SPINNING` is a `DataComponentType<Boolean>` with `networkSynchronized(ByteBufCodecs.BOOL)` and **no** `persistent` codec, so it is transient: it reaches clients but is never saved (checked: `DataComponentType.isTransient()` and the filter in `DataComponentPatch`'s codec in the 1.21 mapped jar). A stack can never stay spinning across a reload. `CustomData` would be saved to disk and compared as NBT.
2. **The item stacks to 1.** All stacks in one inventory get the same spin state, but a picked-up still Sneakoscope would not merge with a spinning one; one per stack avoids split stacks.
3. **No re-equip bob, no spinning drops.** `SneakoscopeItem` overrides Forge `shouldCauseReequipAnimation` (`slotChanged || !ItemStack.isSameItem(old, new)`), so a spin change in the hand does not replay the equip animation, and `onDroppedByPlayer` clears the spin before the item is thrown. A spinning stack moved into a chest by hand keeps spinning until it is picked up again (next check) or the chunk reloads (transient): accepted.
4. **Who carries one.** The carried check looks at every slot of `player.getInventory()` (`getContainerSize()` = 36 main + 4 armour + 1 off hand), not the cursor stack. Spectators and dead players detect nothing (their stacks stop spinning).
5. **Where the carried check runs:** the existing server branch of `ModEvents.onPlayerTick` (Phase END), right after `trySpawnInsomniaDementor(player);`, by calling `SneakoscopeCarried.tick(player)`. The area scan runs when `SneakoscopeRules.dueToCheck(gameTime, player.getId())` (once every 20 ticks, phased by entity id); every other tick only a `WeakHashMap` lookup and a time comparison run.
6. **The spin is written only on change:** a stack is touched only when `SneakoscopeItem.isSpinning(stack) != spinning`, so the inventory menu sends a slot update only then.
7. **Carried whistle timing:** a per-player state (strength, next whistle tick) in a `WeakHashMap<ServerPlayer, State>` (server thread only, no leak on logout). `SneakoscopeRules.nextWhistle(now, scheduled, old, new)`: the first whistle plays at once when detection starts; when the source comes closer the next whistle moves earlier (`min(scheduled, now + interval(new))`); when it moves away the scheduled one stays; strength 0 stops it and removes the state. After each whistle the next is `now + interval(strength)`.
8. **Only the holder hears the carried whistle:** `ServerPlayer.playNotifySound` (it sends a `ClientboundSoundPacket` at the player's position to that player's connection only). `NOTE_BLOCK_FLUTE` volume 0.6 pitch 2.0, then `NOTE_BLOCK_CHIME` volume 0.35 pitch 1.8, same tick, `SoundSource.PLAYERS`.
9. **The placed whistle:** `level.playSound(null, centre, NOTE_BLOCK_FLUTE, BLOCKS, 1.0, 2.0)` and `NOTE_BLOCK_CHIME` 0.6 / 1.8 at the block centre; a volume of 1.0 or less is heard within 16 blocks. It plays only when power goes from 0 to more than 0 (`SneakoscopeRules.whistlesOnRise`).
10. **The strongest source wins, not the nearest** (spec decision 3 says "the nearest detected source decides it"). With one range they are the same. With the Dark Mark's longer range they differ: a Dark Mark at 14 blocks (11) and a Death Eater at 12 blocks (4) give 11, so a weaker source walking in can never lower the signal. Recorded as a spec deviation.
11. **Strength:** `ceil(15 * (range - d) / range - 1e-9)`, clamped 1..15 inside the range, 0 at `d >= range` or for a range of 0 or less. The epsilon keeps exact integers exact (d = 16/15 at range 16 gives 14, not 15). The distance is 3D, from the block centre (placed) or the centre of the holder's bounding box (carried) to the entity's position.
12. **The area check:** for each probe, `level.getEntitiesOfClass(type, AABB.ofSize(centre, 2r, 2r, 2r), e -> e != holder && e.isAlive())`, then the rules ignore anything at or past the range (a sphere). `getEntitiesOfClass` reads only loaded entity sections and never loads a chunk. Voldemort extends `WizardEntity`, so the `WizardEntity` probe covers both; invisible Death Eaters are detected (it senses intent).
13. **Placed block:** `POWER` is the vanilla `BlockStateProperties.POWER` (0..15). `isSignalSource` true; `getSignal` returns `power` on every side; `getDirectSignal` returns 0 (weak power, as the daylight detector: it powers an adjacent lamp, wire or comparator, not the block it sits on). No analog-output override: a comparator reads the block's `getSignal` directly because the Sneakoscope is not a redstone conductor. Light `power > 0 ? 7 : 0` through `Properties.lightLevel`. The ticker calls `level.setBlock(pos, state.setValue(POWER, p), Block.UPDATE_ALL)` only when `p` differs; `UPDATE_ALL` notifies the neighbours, so no extra `updateNeighborsAt`.
14. **The block entity holds no data.** It exists for the ticker (spec decision 5). The ticker is registered on the server only and does its work when `dueToCheck(gameTime, (long) pos.hashCode())`.
15. **Block properties:** `MapColor.GOLD`, strength 0.5, `SoundType.GLASS`, `PushReaction.DESTROY`, no tool needed, no support requirement, no facing. Shape `Block.box(5, 0, 5, 11, 8, 11)`. The loot table drops itself with `survives_explosion`.
   Changed by the accepted Task 4 fix round (c6f1b49): a spinning top 7 x 10 x 7 px, shape Block.box(4.5, 0, 4.5, 11.5, 10, 11.5).
16. **Model:** base plate 6x1x6 (y 0..1), pivot 2x1x2, glass body 6x4x6 (y 2..6), glass shoulder 4x1x4, brass spindle 1x1x1 (y 7..8): 6 x 8 x 6 px. `render_type: minecraft:translucent`. The spinning model swaps the glass texture for the animated one and gives its glass faces `forge_data` `block_light` 15 and `sky_light` 15 (Forge `ForgeFaceData`, checked in the 51.0.33 sources), which is the glow in the world and in the hand. The blockstate maps `power=0` to the still model and `power=1..15` to the spinning one.
   Changed by the accepted Task 4 fix round (c6f1b49): a spinning top 7 x 10 x 7 px, shape Block.box(4.5, 0, 4.5, 11.5, 10, 11.5).
17. **Textures:** `build_scripts/generate_sneakoscope_textures.py` writes `sneakoscope_brass.png`, `sneakoscope_glass.png` and `sneakoscope_glass_spin.png` (6 frames, 16x96, `frametime` 2, no interpolation, so a bright facet edge steps one column per frame across every side face). UVs are left out, so faces map by position.
18. **Preview:** the generator's `--preview` flag writes `/tmp/sneakoscope_preview.png` (each sheet and each spin frame at 8x over a checkerboard). The implementer runs it and looks at it with the Read tool, and the controller looks at the same file before accepting Task 4. No 3D render.
19. **Recipe:** the spec's "gold nuggets on the four sides" collides with "gold ingot on top" and "arcane dust below" (those are two of the four sides). Pattern `NIN / NPN / " A "`: gold ingot top centre, gold nuggets in the two top corners and on both sides of the pane, glass pane centre, arcane dust below. Four nuggets, no recipe-book advancement (the Wizard Cauldron has none either).
20. **Trade:** Curio Dealer level 2, `sells(event, 2, 14, 1, one(ExampleMod.SNEAKOSCOPE_ITEM), 4, 10)`: exactly 14 emeralds (spread 1), 4 uses, 10 villager XP, after the existing level-2 dagger line.
21. **Config:** `sneakoscopeRange` 16 (4..64) and `sneakoscopeDarkMarkRange` 48 (8..128), COMMON config like the rest. `sneakoscopeDetectsPlayers` is added by Part B, which is the only code that reads it.
22. **Text and docs:** lang `block.hogwartsmagic.sneakoscope` and a tooltip `item.hogwartsmagic.sneakoscope.tooltip`. Creative tab right after the Wizard Cauldron. No advancement. One entry in each wiki (`MOD_FEATURES_WIKI.md`, `wiki/data.js`). No Hogwarts Guide page.
23. **Part B hook:** Part B adds `DARK_PLAYER(Reach.NEAR)` to `SneakoscopeRules.Source` and one probe (players filtered by `DarkMagicData`, never the holder, never spectators or creative) to `SneakoscopeSensor.PROBES`. The existing constants, probes and tests do not change.
24. **No new packet, no event subscriber.** The spin travels in the stack, the placed spin in the block state, the sounds in vanilla sound packets.

## Global Constraints

- Server authoritative. `sneakoscope/`, `block/SneakoscopeBlock.java`, `block/entity/SneakoscopeBlockEntity.java` and `item/SneakoscopeItem.java` never reference `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code goes only into the existing client-only `event/ClientModEvents.java`.
- No `switch` on an enum anywhere in the new code. Use if-chains and `==`.
- Never load a chunk. Detection uses `getEntitiesOfClass` only.
- No new hurt, death or effect handler, and no new `@SubscribeEvent`.
- Entity event bytes 21, 35 and 63 crash the client. This plan sends none.
- `en_us.json` and every JSON file touched stay valid JSON. Validate with `python3 -c "import json;json.load(open('<file>',encoding='utf-8'))"`.
- Build and test (cloud):
  - First `pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`. If a build is running, wait (poll every 30 s, at most 20 min).
  - `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2` (gradlew is not executable: always `bash ./gradlew`).
  - One class: `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*SneakoscopeRulesTest" --console=plain --max-workers=2`.
  - A failure that says `429 Too Many Requests` is Maven Central rate limiting, not a code failure: wait 60 s and run the same command again (at most 3 times).
  - Test count: `find build/test-results/test -name "*.xml" | xargs grep -ho 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`.
- Dedicated-server boot (Task 2 and Task 6), "the boot block":
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
- **360 tests at f5033f3**, all green. They stay unchanged and green. Task 1 adds 21 (381 total).
- Line numbers drift. Every edit names the exact text to find, quoted from f5033f3 (or from an earlier task of this plan, which says so).

## File Structure

| File | Responsibility | Task |
|---|---|---|
| Create `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java` | Pure: sources, ranges, strength, whistle interval and schedule, phase | 1 |
| Create `src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java` | 21 tests | 1 |
| Modify `src/main/java/com/hogwartsmagic/Config.java` | Two range values | 2 |
| Create `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java` | Probes, area scan, the two whistles | 2 |
| Create `src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java` | `POWER`, signal, light, shape, ticker | 2 |
| Create `src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java` | Once-a-second server tick | 2 |
| Create `src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java` | BlockItem, spin helpers, tooltip, no re-equip bob, drop clears spin | 2 |
| Modify `src/main/java/com/hogwartsmagic/ExampleMod.java` | Component, block, block entity type, item, creative tab | 2 |
| Create `src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json` | Drops itself | 2 |
| Modify `src/main/resources/assets/hogwartsmagic/lang/en_us.json` | Name, tooltip | 2 |
| Create `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeCarried.java` | Per-player check, spin, whistle schedule | 3 |
| Modify `src/main/java/com/hogwartsmagic/event/ModEvents.java` | One call in `onPlayerTick` | 3 |
| Modify `src/main/java/com/hogwartsmagic/event/ClientModEvents.java` | Item property `hogwartsmagic:spinning` | 3 |
| Create `build_scripts/generate_sneakoscope_textures.py` | Paints 3 sheets, mcmeta, optional preview | 4 |
| Create `src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_{brass,glass,glass_spin}.png`, `sneakoscope_glass_spin.png.mcmeta` | Generated | 4 |
| Create `src/main/resources/assets/hogwartsmagic/models/block/sneakoscope.json`, `sneakoscope_spinning.json` | Still and spinning models | 4 |
| Create `src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json` | 16 `power` variants | 4 |
| Create `src/main/resources/assets/hogwartsmagic/models/item/sneakoscope.json`, `sneakoscope_spinning.json` | Item model with the spin override | 4 |
| Create `src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json` | Shaped recipe | 5 |
| Modify `src/main/java/com/hogwartsmagic/entity/ModVillagers.java` | Curio Dealer trade | 5 |
| Modify `MOD_FEATURES_WIKI.md`, `wiki/data.js` | One entry each | 5 |

Untouched on purpose: `WizardEntity`, `VoldemortEntity`, `DementorEntity`, `DarkMarkEntity` (read only through `getEntitiesOfClass`), `network/` (no packet), the mineable tags (no tool needed), the advancements, `HogwartsGuideScreen`.

## Pre-flight table (shared names between tasks)

| Pair / task | Shared thing | Check |
|---|---|---|
| T1 → T2, T3 | `SneakoscopeRules.Reach {NEAR, SKY}`; `Source {DEATH_EATER, DEMENTOR, DARK_MARK}` with `reach()`; `Hit(Source source, double distance)`; constants `MAX_STRENGTH`, `DEFAULT_RANGE`, `DEFAULT_DARK_MARK_RANGE`, `CHECK_INTERVAL_TICKS`, `SLOWEST_WHISTLE_TICKS`, `FASTEST_WHISTLE_TICKS`; `range(Source,int,int)`, `strength(double,int)`, `strength(List<Hit>,int,int)`, `whistleInterval(int)`, `dueToCheck(long,long)`, `whistlesOnRise(int,int)`, `spinning(int)`, `nextWhistle(long,long,int,int)` | names identical |
| T2 → T3 | `SneakoscopeSensor.strength(ServerLevel, Vec3, @Nullable Entity)`, `whistleTo(ServerPlayer)`; `SneakoscopeItem.isSpinning(ItemStack)`, `setSpinning(ItemStack, boolean)`; `ExampleMod.SNEAKOSCOPE_ITEM`, `ExampleMod.SNEAKOSCOPE_SPINNING` | written in T2 |
| T2 → T4 | Registry name `hogwartsmagic:sneakoscope`; property name `power`; item property `hogwartsmagic:spinning` (T3) used in the item model override | identical strings |
| T2 → T5 | `ExampleMod.SNEAKOSCOPE_ITEM` (a `RegistryObject<Item>`, so `one(...)` takes it) | identical |
| T2 → T2 | `Config.getSneakoscopeRange()`, `Config.getSneakoscopeDarkMarkRange()` | written in the same task |
| T2 lang | Anchor: the last two entries at f5033f3, `"brew.hogwartsmagic.pepperup"` and `"brew.hogwartsmagic.polyjuice"` | quoted in T2 step 8 |

---

### Task 1: SneakoscopeRules (pure rules, test-first)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java`
- Test: `src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java`

**Interfaces.** Consumes: nothing. Produces: everything in the T1 row of the pre-flight table.

- [ ] **Step 1: Write the failing test.** Create `src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java`:

```java
package com.hogwartsmagic.sneakoscope;

import com.hogwartsmagic.sneakoscope.SneakoscopeRules.Hit;
import com.hogwartsmagic.sneakoscope.SneakoscopeRules.Reach;
import com.hogwartsmagic.sneakoscope.SneakoscopeRules.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SneakoscopeRulesTest {
    private static final int NEAR = SneakoscopeRules.DEFAULT_RANGE;
    private static final int SKY = SneakoscopeRules.DEFAULT_DARK_MARK_RANGE;

    private static int strengthOf(Hit... hits) {
        return SneakoscopeRules.strength(List.of(hits), NEAR, SKY);
    }

    @Test
    void defaultsMatchTheSpec() {
        assertEquals(16, NEAR);
        assertEquals(48, SKY);
        assertEquals(15, SneakoscopeRules.MAX_STRENGTH);
        assertEquals(20, SneakoscopeRules.CHECK_INTERVAL_TICKS);
        assertEquals(40, SneakoscopeRules.SLOWEST_WHISTLE_TICKS);
        assertEquals(10, SneakoscopeRules.FASTEST_WHISTLE_TICKS);
    }

    @Test
    void atTheSourceTheStrengthIsFifteen() {
        assertEquals(15, SneakoscopeRules.strength(0.0, 16));
        assertEquals(15, SneakoscopeRules.strength(0.5, 16));
        assertEquals(15, SneakoscopeRules.strength(0.0, 48));
    }

    @Test
    void atOrBeyondTheEdgeTheStrengthIsZero() {
        assertEquals(0, SneakoscopeRules.strength(16.0, 16));
        assertEquals(0, SneakoscopeRules.strength(17.0, 16));
        assertEquals(0, SneakoscopeRules.strength(48.0, 48));
        assertEquals(0, SneakoscopeRules.strength(100.0, 48));
    }

    @Test
    void justInsideTheEdgeTheStrengthIsOne() {
        assertEquals(1, SneakoscopeRules.strength(15.9, 16));
        assertEquals(1, SneakoscopeRules.strength(47.9, 48));
    }

    @Test
    void strengthRoundsUp() {
        assertEquals(8, SneakoscopeRules.strength(8.0, 16));
        assertEquals(14, SneakoscopeRules.strength(2.0, 16));
        assertEquals(4, SneakoscopeRules.strength(12.0, 16));
        // 15 * (1 - (16/15) / 16) is exactly 14: an exact value is not pushed up by rounding noise.
        assertEquals(14, SneakoscopeRules.strength(16.0 / 15.0, 16));
    }

    @Test
    void strengthRisesAsTheSourceComesCloser() {
        int previous = 0;
        for (int i = 64; i >= 0; i--) {
            int s = SneakoscopeRules.strength(i * 0.25, 16);
            assertTrue(s >= previous, "strength fell at distance " + (i * 0.25));
            assertTrue(s >= 0 && s <= 15);
            previous = s;
        }
        assertEquals(15, previous);
    }

    @Test
    void aDarkMarkFortyBlocksUpIsWeak() {
        assertEquals(3, strengthOf(new Hit(Source.DARK_MARK, 40.0)));
    }

    @Test
    void onlyTheDarkMarkUsesTheSkyRange() {
        assertEquals(0, strengthOf(new Hit(Source.DEATH_EATER, 40.0)));
        assertEquals(0, strengthOf(new Hit(Source.DEMENTOR, 40.0)));
        assertEquals(Reach.NEAR, Source.DEATH_EATER.reach());
        assertEquals(Reach.NEAR, Source.DEMENTOR.reach());
        assertEquals(Reach.SKY, Source.DARK_MARK.reach());
        assertEquals(16, SneakoscopeRules.range(Source.DEATH_EATER, 16, 48));
        assertEquals(16, SneakoscopeRules.range(Source.DEMENTOR, 16, 48));
        assertEquals(48, SneakoscopeRules.range(Source.DARK_MARK, 16, 48));
    }

    @Test
    void theStrongestSourceWins() {
        // Dark Mark at 14 of 48 gives 11; a Death Eater at 12 of 16 gives 4. The weaker, nearer one does not lower it.
        assertEquals(11, strengthOf(new Hit(Source.DARK_MARK, 14.0), new Hit(Source.DEATH_EATER, 12.0)));
        // Dementor at 2 gives 14; Dark Mark at 30 gives 6.
        assertEquals(14, strengthOf(new Hit(Source.DEMENTOR, 2.0), new Hit(Source.DARK_MARK, 30.0)));
    }

    @Test
    void nothingNearIsZero() {
        assertEquals(0, strengthOf());
    }

    @Test
    void aBadRangeDetectsNothing() {
        assertEquals(0, SneakoscopeRules.strength(1.0, 0));
        assertEquals(0, SneakoscopeRules.strength(1.0, -5));
    }

    @Test
    void configuredRangesAreUsed() {
        assertEquals(6, SneakoscopeRules.strength(List.of(new Hit(Source.DEATH_EATER, 20.0)), 32, 48));
        assertEquals(1, SneakoscopeRules.strength(List.of(new Hit(Source.DARK_MARK, 60.0)), 16, 64));
    }

    @Test
    void whistleIntervalRunsFromFortyToTen() {
        assertEquals(40, SneakoscopeRules.whistleInterval(1));
        assertEquals(25, SneakoscopeRules.whistleInterval(8));
        assertEquals(10, SneakoscopeRules.whistleInterval(15));
        assertEquals(10, SneakoscopeRules.whistleInterval(20));
        assertEquals(0, SneakoscopeRules.whistleInterval(0));
        assertEquals(0, SneakoscopeRules.whistleInterval(-1));
    }

    @Test
    void whistleIntervalShrinksAsTheStrengthRises() {
        for (int s = 1; s < 15; s++) {
            int here = SneakoscopeRules.whistleInterval(s);
            int next = SneakoscopeRules.whistleInterval(s + 1);
            assertTrue(next <= here, "interval grew at strength " + s);
            assertTrue(here >= 10 && here <= 40);
        }
    }

    @Test
    void eachKeyIsCheckedOnceEveryTwentyTicks() {
        long[] keys = {0L, 7L, 19L, 123456789L, -3L};
        for (long key : keys) {
            int due = 0;
            for (long t = 1000L; t < 1020L; t++) {
                if (SneakoscopeRules.dueToCheck(t, key)) {
                    due++;
                    assertTrue(SneakoscopeRules.dueToCheck(t + 20L, key));
                }
            }
            assertEquals(1, due, "key " + key);
        }
    }

    @Test
    void aPlacedSneakoscopeWhistlesOnlyWhenItStartsDetecting() {
        assertTrue(SneakoscopeRules.whistlesOnRise(0, 1));
        assertTrue(SneakoscopeRules.whistlesOnRise(0, 15));
        assertFalse(SneakoscopeRules.whistlesOnRise(3, 5));
        assertFalse(SneakoscopeRules.whistlesOnRise(5, 0));
        assertFalse(SneakoscopeRules.whistlesOnRise(0, 0));
    }

    @Test
    void spinningMeansAnySignal() {
        assertFalse(SneakoscopeRules.spinning(0));
        assertTrue(SneakoscopeRules.spinning(1));
        assertTrue(SneakoscopeRules.spinning(15));
    }

    @Test
    void nextWhistleIsImmediateWhenDetectionStarts() {
        assertEquals(100L, SneakoscopeRules.nextWhistle(100L, Long.MAX_VALUE, 0, 5));
    }

    @Test
    void nextWhistleComesSoonerWhenTheSourceComesCloser() {
        assertEquals(110L, SneakoscopeRules.nextWhistle(100L, 130L, 1, 15));
        assertEquals(105L, SneakoscopeRules.nextWhistle(100L, 105L, 1, 15));
    }

    @Test
    void nextWhistleKeepsItsTimeWhenTheSourceMovesAway() {
        assertEquals(110L, SneakoscopeRules.nextWhistle(100L, 110L, 15, 1));
    }

    @Test
    void nextWhistleStopsWhenNothingIsNear() {
        assertEquals(Long.MAX_VALUE, SneakoscopeRules.nextWhistle(100L, 110L, 5, 0));
    }
}
```

- [ ] **Step 2: Run it and see it fail.** Run the gradle check (`pgrep -f "[G]radleDaemon|[G]radleWrapperMain"`), then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*SneakoscopeRulesTest" --console=plain --max-workers=2`. Expected: compilation failure (`cannot find symbol ... SneakoscopeRules`).

- [ ] **Step 3: Write `SneakoscopeRules`.** Create `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java`:

```java
package com.hogwartsmagic.sneakoscope;

import java.util.List;

/**
 * The Sneakoscope's rules, free of Minecraft so they are unit-tested. What it detects, how far, how strongly, and
 * how often a carried one whistles. SneakoscopeSensor finds the sources in the world and hands their distances here.
 *
 * Part B (dark players) adds one Source constant with Reach.NEAR and one probe in SneakoscopeSensor; nothing here
 * changes for it.
 */
public final class SneakoscopeRules {
    /** Which configured range a source uses: the normal range, or the long range for something in the sky. */
    public enum Reach {
        NEAR,
        SKY
    }

    /** What a Sneakoscope detects. Voldemort is a Death Eater here (VoldemortEntity extends WizardEntity). */
    public enum Source {
        DEATH_EATER(Reach.NEAR),
        DEMENTOR(Reach.NEAR),
        DARK_MARK(Reach.SKY);

        private final Reach reach;

        Source(Reach reach) {
            this.reach = reach;
        }

        public Reach reach() {
            return reach;
        }
    }

    /** One detected source and its distance in blocks from the Sneakoscope. */
    public record Hit(Source source, double distance) {
    }

    public static final int MAX_STRENGTH = 15;
    public static final int DEFAULT_RANGE = 16;
    public static final int DEFAULT_DARK_MARK_RANGE = 48;
    /** Both the carried and the placed check run once in this many ticks. */
    public static final int CHECK_INTERVAL_TICKS = 20;
    /** Carried whistle interval at strength 1. */
    public static final int SLOWEST_WHISTLE_TICKS = 40;
    /** Carried whistle interval at strength 15. */
    public static final int FASTEST_WHISTLE_TICKS = 10;

    private SneakoscopeRules() {
    }

    /** The range in blocks for a source, given the two configured ranges. */
    public static int range(Source source, int range, int darkMarkRange) {
        return source.reach() == Reach.SKY ? darkMarkRange : range;
    }

    /**
     * 15 * (1 - distance / range), rounded up: 15 at the source, 1 just inside the edge, 0 at or past it.
     * The small epsilon keeps an exact whole number from being pushed up by floating-point noise.
     */
    public static int strength(double distance, int range) {
        if (range <= 0 || distance >= range) {
            return 0;
        }
        double d = Math.max(0.0, distance);
        int s = (int) Math.ceil(MAX_STRENGTH * (range - d) / range - 1.0e-9);
        return Math.max(1, Math.min(MAX_STRENGTH, s));
    }

    /** The strongest of all hits; each source is measured against its own range. 0 when nothing is in range. */
    public static int strength(List<Hit> hits, int range, int darkMarkRange) {
        int best = 0;
        for (Hit hit : hits) {
            best = Math.max(best, strength(hit.distance(), range(hit.source(), range, darkMarkRange)));
        }
        return best;
    }

    /** Ticks between carried whistles: 40 at strength 1 down to 10 at 15, linear, rounded. 0 means no whistle. */
    public static int whistleInterval(int strength) {
        if (strength <= 0) {
            return 0;
        }
        int s = Math.min(MAX_STRENGTH, strength);
        int span = SLOWEST_WHISTLE_TICKS - FASTEST_WHISTLE_TICKS;
        int steps = MAX_STRENGTH - 1;
        return SLOWEST_WHISTLE_TICKS - ((s - 1) * span + steps / 2) / steps;
    }

    /** True once in every CHECK_INTERVAL_TICKS ticks for a key (entity id or packed block position), spread by key. */
    public static boolean dueToCheck(long gameTime, long phaseKey) {
        return Math.floorMod(gameTime, (long) CHECK_INTERVAL_TICKS) == Math.floorMod(phaseKey, (long) CHECK_INTERVAL_TICKS);
    }

    /** A placed Sneakoscope whistles once, when it goes from detecting nothing to detecting something. */
    public static boolean whistlesOnRise(int oldPower, int newPower) {
        return oldPower <= 0 && newPower > 0;
    }

    /** The Sneakoscope spins (and glows) while it detects anything. */
    public static boolean spinning(int strength) {
        return strength > 0;
    }

    /**
     * When a carried Sneakoscope next whistles, after a check moved the strength from oldStrength to newStrength.
     * Nothing near: never. Detection starts: now. Otherwise the earlier of the scheduled whistle and one interval
     * at the new strength, so a closer source speeds it up at once and a farther one lets the scheduled one play.
     */
    public static long nextWhistle(long now, long scheduled, int oldStrength, int newStrength) {
        if (newStrength <= 0) {
            return Long.MAX_VALUE;
        }
        if (oldStrength <= 0) {
            return now;
        }
        return Math.min(scheduled, now + whistleInterval(newStrength));
    }
}
```

- [ ] **Step 4: Run it and see it pass.** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew test --tests "*SneakoscopeRulesTest" --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 21 tests pass. Then `timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL; the test-count command (Global Constraints) prints `381`.

- [ ] **Step 5: Greps.** `grep -n "import net.minecraft\|import com.hogwartsmagic\|switch" src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java`. Expected: nothing.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add SneakoscopeRules: sources, ranges, strength and whistle timing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeRules.java src/test/java/com/hogwartsmagic/sneakoscope/SneakoscopeRulesTest.java
```

---

### Task 2: The placed Sneakoscope (sensor, block, block entity, item, registration)

**Files:**
- Modify: `src/main/java/com/hogwartsmagic/Config.java`
- Create: `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java`
- Create: `src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java`
- Create: `src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java`
- Create: `src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java`
- Modify: `src/main/java/com/hogwartsmagic/ExampleMod.java`
- Create: `src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json`
- Modify: `src/main/resources/assets/hogwartsmagic/lang/en_us.json`

**Interfaces.** Consumes: T1 (`SneakoscopeRules`). Produces: `SneakoscopeSensor.strength(ServerLevel, Vec3, @Nullable Entity)`, `SneakoscopeSensor.whistleTo(ServerPlayer)`, `SneakoscopeSensor.whistleAt(ServerLevel, BlockPos)`; `SneakoscopeBlock.POWER`; `SneakoscopeBlockEntity.serverTick(Level, BlockPos, BlockState, SneakoscopeBlockEntity)`; `SneakoscopeItem.isSpinning(ItemStack)`, `SneakoscopeItem.setSpinning(ItemStack, boolean)`; `ExampleMod.SNEAKOSCOPE_SPINNING`, `SNEAKOSCOPE`, `SNEAKOSCOPE_BLOCK_ENTITY`, `SNEAKOSCOPE_ITEM`; `Config.getSneakoscopeRange()`, `Config.getSneakoscopeDarkMarkRange()`.

The block has no model until Task 4 (it shows the missing-model cube in a client). That is expected between tasks.

- [ ] **Step 1: Config.** In `src/main/java/com/hogwartsmagic/Config.java`, find:

```java
    public static final ForgeConfigSpec.BooleanValue IMPERIO_AFFECTS_PLAYERS = BUILDER
            .comment("Imperio: if true, players can be enthralled (ranks I-III) and possessed (Master). Mobs are always allowed.")
            .define("imperioAffectsPlayers", true);
```

and add right after it:

```java

    public static final ForgeConfigSpec.IntValue SNEAKOSCOPE_RANGE = BUILDER
            .comment("Sneakoscope: range in blocks for Death Eaters, Voldemort and Dementors.")
            .defineInRange("sneakoscopeRange", 16, 4, 64);

    public static final ForgeConfigSpec.IntValue SNEAKOSCOPE_DARK_MARK_RANGE = BUILDER
            .comment("Sneakoscope: range in blocks for the Dark Mark, which hangs high in the sky.")
            .defineInRange("sneakoscopeDarkMarkRange", 48, 8, 128);
```

Find:

```java
    public static boolean imperioAffectsPlayers = true;
```

and add right after it:

```java
    public static int sneakoscopeRange = 16;
    public static int sneakoscopeDarkMarkRange = 48;
```

Find:

```java
        apparitionSplinchChance = APPARITION_SPLINCH_CHANCE.get();
```

and add right after it:

```java
        sneakoscopeRange = SNEAKOSCOPE_RANGE.get();
        sneakoscopeDarkMarkRange = SNEAKOSCOPE_DARK_MARK_RANGE.get();
```

Find the last method of the class:

```java
    public static double getApparitionSplinchChance() {
        return apparitionSplinchChance;
    }
}
```

and replace it with:

```java
    public static double getApparitionSplinchChance() {
        return apparitionSplinchChance;
    }

    public static int getSneakoscopeRange() {
        return sneakoscopeRange;
    }

    public static int getSneakoscopeDarkMarkRange() {
        return sneakoscopeDarkMarkRange;
    }
}
```

- [ ] **Step 2: The sensor.** Create `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java`:

```java
package com.hogwartsmagic.sneakoscope;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.hogwartsmagic.entity.DementorEntity;
import com.hogwartsmagic.entity.WizardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds what a Sneakoscope detects and plays its whistle. Server only. Each probe pairs a SneakoscopeRules.Source
 * with an entity class; the area check is getEntitiesOfClass, which reads loaded entity sections and never loads a
 * chunk. Part B adds one probe (dark players) to PROBES.
 */
public final class SneakoscopeSensor {
    private record Probe(SneakoscopeRules.Source source, Class<? extends Entity> type) {
    }

    /** VoldemortEntity extends WizardEntity, so the Death Eater probe finds him too. */
    private static final List<Probe> PROBES = List.of(
            new Probe(SneakoscopeRules.Source.DEATH_EATER, WizardEntity.class),
            new Probe(SneakoscopeRules.Source.DEMENTOR, DementorEntity.class),
            new Probe(SneakoscopeRules.Source.DARK_MARK, DarkMarkEntity.class));

    private SneakoscopeSensor() {
    }

    /** Signal strength 0..15 at centre. The holder (null for a placed Sneakoscope) is never detected. */
    public static int strength(ServerLevel level, Vec3 centre, @Nullable Entity holder) {
        int range = Config.getSneakoscopeRange();
        int darkMarkRange = Config.getSneakoscopeDarkMarkRange();
        List<SneakoscopeRules.Hit> hits = new ArrayList<>();
        for (Probe probe : PROBES) {
            int probeRange = SneakoscopeRules.range(probe.source(), range, darkMarkRange);
            collect(level, probe.type(), probe.source(), centre, probeRange, holder, hits);
        }
        return SneakoscopeRules.strength(hits, range, darkMarkRange);
    }

    private static <T extends Entity> void collect(ServerLevel level, Class<T> type, SneakoscopeRules.Source source,
                                                   Vec3 centre, int range, @Nullable Entity holder,
                                                   List<SneakoscopeRules.Hit> hits) {
        if (range <= 0) {
            return;
        }
        double size = range * 2.0;
        AABB box = AABB.ofSize(centre, size, size, size);
        for (T entity : level.getEntitiesOfClass(type, box, e -> e != holder && e.isAlive())) {
            hits.add(new SneakoscopeRules.Hit(source, Math.sqrt(entity.distanceToSqr(centre))));
        }
    }

    /** The carried whistle: only this player hears it. */
    public static void whistleTo(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.6f, 2.0f);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
    }

    /** The placed whistle: everyone within 16 blocks hears it. */
    public static void whistleAt(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.4;
        double z = pos.getZ() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_FLUTE, SoundSource.BLOCKS, 1.0f, 2.0f);
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.8f);
    }
}
```

- [ ] **Step 3: The block.** Create `src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java`:

```java
package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.SneakoscopeBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A placed Sneakoscope: a redstone sensor. POWER (0..15) is the strength of the strongest dark source in range,
 * written once a second by SneakoscopeBlockEntity. The signal and the light come from the state, so redstone never
 * touches the block entity. Weak power on every side, like a daylight detector. While POWER > 0 the blockstate picks
 * the spinning model and the block gives light 7.
 */
public class SneakoscopeBlock extends BaseEntityBlock {
    public static final MapCodec<SneakoscopeBlock> CODEC = simpleCodec(SneakoscopeBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 8.0, 11.0);

    public SneakoscopeBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(POWER) > 0 ? 7 : 0).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SneakoscopeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ExampleMod.SNEAKOSCOPE_BLOCK_ENTITY.get(), SneakoscopeBlockEntity::serverTick);
    }
}
```

- [ ] **Step 4: The block entity.** Create `src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java`:

```java
package com.hogwartsmagic.block.entity;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.SneakoscopeBlock;
import com.hogwartsmagic.sneakoscope.SneakoscopeRules;
import com.hogwartsmagic.sneakoscope.SneakoscopeSensor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The placed Sneakoscope's clock. It holds no data: once a second (phased by position, as the cauldron) it measures
 * the strength and writes it into the block state, only when it changed. Going from 0 to more whistles once.
 */
public class SneakoscopeBlockEntity extends BlockEntity {
    public SneakoscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.SNEAKOSCOPE_BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SneakoscopeBlockEntity sneakoscope) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!SneakoscopeRules.dueToCheck(level.getGameTime(), pos.asLong())) {
            return;
        }
        if (!state.hasProperty(SneakoscopeBlock.POWER)) {
            return;
        }
        int power = SneakoscopeSensor.strength(serverLevel, Vec3.atCenterOf(pos), null);
        int old = state.getValue(SneakoscopeBlock.POWER);
        if (power == old) {
            return;
        }
        // UPDATE_ALL notifies the neighbours, so lamps, wire and comparators re-read getSignal.
        level.setBlock(pos, state.setValue(SneakoscopeBlock.POWER, power), Block.UPDATE_ALL);
        if (SneakoscopeRules.whistlesOnRise(old, power)) {
            SneakoscopeSensor.whistleAt(serverLevel, pos);
        }
    }
}
```

- [ ] **Step 5: The item.** Create `src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java`:

```java
package com.hogwartsmagic.item;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Sneakoscope item. Placed, it is SneakoscopeBlock. Carried, SneakoscopeCarried sets the transient
 * SNEAKOSCOPE_SPINNING component while it detects something; the client draws the spin from it.
 */
public class SneakoscopeItem extends BlockItem {
    public SneakoscopeItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static boolean isSpinning(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ExampleMod.SNEAKOSCOPE_SPINNING.get()));
    }

    /** Sets or clears the spin. Callers only call it when the value changes, so the slot syncs only then. */
    public static void setSpinning(ItemStack stack, boolean spinning) {
        if (spinning) {
            stack.set(ExampleMod.SNEAKOSCOPE_SPINNING.get(), Boolean.TRUE);
        } else {
            stack.remove(ExampleMod.SNEAKOSCOPE_SPINNING.get());
        }
    }

    /** A spin change in the hand is the same item: no equip bob. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    /** A thrown Sneakoscope stops spinning: nobody holds it any more. */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        setSpinning(stack, false);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hogwartsmagic.sneakoscope.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
```

- [ ] **Step 6: Register it.** In `src/main/java/com/hogwartsmagic/ExampleMod.java`:

(a) Find:

```java
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(
                            com.hogwartsmagic.spell.motion.AlatusRules::clampQuality, q -> q))
                    .build());
```

and add right after it:

```java
    /** Sneakoscope: true while a carried Sneakoscope detects something. Synced to clients, never saved (transient). */
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<Boolean>> SNEAKOSCOPE_SPINNING =
            DATA_COMPONENTS.register("sneakoscope_spinning", () -> net.minecraft.core.component.DataComponentType.<Boolean>builder()
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                    .build());
```

(b) Find:

```java
            BLOCK_ENTITY_TYPES.register("wizard_cauldron", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.WizardCauldronBlockEntity::new,
                    WIZARD_CAULDRON.get()
            ).build(null));
```

and add right after it:

```java
    public static final RegistryObject<Block> SNEAKOSCOPE = BLOCKS.register("sneakoscope",
            () -> new com.hogwartsmagic.block.SneakoscopeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(0.5f)
                    .sound(net.minecraft.world.level.block.SoundType.GLASS)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final RegistryObject<BlockEntityType<com.hogwartsmagic.block.entity.SneakoscopeBlockEntity>> SNEAKOSCOPE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("sneakoscope", () -> BlockEntityType.Builder.of(
                    com.hogwartsmagic.block.entity.SneakoscopeBlockEntity::new,
                    SNEAKOSCOPE.get()
            ).build(null));
```

(c) Find:

```java
    public static final RegistryObject<Item> WIZARD_CAULDRON_ITEM = ITEMS.register("wizard_cauldron", () -> new BlockItem(WIZARD_CAULDRON.get(), new Item.Properties()));
```

and add right after it:

```java
    public static final RegistryObject<Item> SNEAKOSCOPE_ITEM = ITEMS.register("sneakoscope",
            () -> new com.hogwartsmagic.item.SneakoscopeItem(SNEAKOSCOPE.get(), new Item.Properties().stacksTo(1)));
```

(d) Find (creative tab):

```java
                output.accept(WIZARD_CAULDRON_ITEM.get());
```

and add right after it:

```java
                output.accept(SNEAKOSCOPE_ITEM.get());
```

`MapColor` and `BlockBehaviour` are already imported in `ExampleMod.java` (check: `grep -n "^import net.minecraft.world.level.material.MapColor;\|^import net.minecraft.world.level.block.state.BlockBehaviour;" src/main/java/com/hogwartsmagic/ExampleMod.java` prints two lines).

- [ ] **Step 7: Loot table.** Create `src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json`:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "hogwartsmagic:sneakoscope" }
      ],
      "conditions": [
        { "condition": "minecraft:survives_explosion" }
      ]
    }
  ]
}
```

- [ ] **Step 8: Text.** In `src/main/resources/assets/hogwartsmagic/lang/en_us.json`, find the last two entries:

```json
  "brew.hogwartsmagic.pepperup": "Pepperup Potion",
  "brew.hogwartsmagic.polyjuice": "Polyjuice Potion"
}
```

and replace them with:

```json
  "brew.hogwartsmagic.pepperup": "Pepperup Potion",
  "brew.hogwartsmagic.polyjuice": "Polyjuice Potion",
  "block.hogwartsmagic.sneakoscope": "Sneakoscope",
  "item.hogwartsmagic.sneakoscope.tooltip": "Spins and whistles when a Death Eater, a Dementor or the Dark Mark is near. Place it for a redstone signal."
}
```

- [ ] **Step 9: Validate, build, greps, boot.**
  - `cd /home/user/minecraft_mods && python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json`. Expected: no output.
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 381 tests, all green.
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch\|SubscribeEvent" src/main/java/com/hogwartsmagic/sneakoscope src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java`. Expected: nothing.
  - Run the boot block (Global Constraints). Expected: `Done (`; no registry, data component or class-loading errors; no server java process left.

- [ ] **Step 10: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Add the placed Sneakoscope: redstone sensor block, sensor, item and config

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/Config.java src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeSensor.java src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java src/main/java/com/hogwartsmagic/ExampleMod.java src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json src/main/resources/assets/hogwartsmagic/lang/en_us.json
```

---

### Task 3: The carried Sneakoscope (check, spin, whistle)

**Files:**
- Create: `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeCarried.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ModEvents.java`
- Modify: `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`

**Interfaces.** Consumes: T1 (`dueToCheck`, `spinning`, `nextWhistle`, `whistleInterval`), T2 (`SneakoscopeSensor.strength`, `whistleTo`, `SneakoscopeItem.isSpinning/setSpinning`, `ExampleMod.SNEAKOSCOPE_ITEM`). Produces: `SneakoscopeCarried.tick(ServerPlayer)`; item property `hogwartsmagic:spinning` (0 or 1) on `SNEAKOSCOPE_ITEM`.

- [ ] **Step 1: The carried logic.** Create `src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeCarried.java`:

```java
package com.hogwartsmagic.sneakoscope;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.SneakoscopeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Sneakoscope anywhere in a player's inventory (main, armour, off hand). Called every server tick from
 * ModEvents.onPlayerTick. Once a second (phased by entity id) it measures the strength, turns the spin on or off on
 * every Sneakoscope stack (only when it changes) and schedules the whistle; every tick it plays a due whistle, which
 * only the holder hears. Server thread only.
 */
public final class SneakoscopeCarried {
    private static final class State {
        int strength;
        long nextWhistle = Long.MAX_VALUE;
    }

    /** Weak keys: a player who logs out or respawns (a new ServerPlayer) leaves nothing behind. */
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    private SneakoscopeCarried() {
    }

    public static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (SneakoscopeRules.dueToCheck(now, player.getId())) {
            check(player, now);
        }
        State state = STATES.get(player);
        if (state != null && state.strength > 0 && now >= state.nextWhistle) {
            SneakoscopeSensor.whistleTo(player);
            state.nextWhistle = now + SneakoscopeRules.whistleInterval(state.strength);
        }
    }

    private static void check(ServerPlayer player, long now) {
        Item sneakoscope = ExampleMod.SNEAKOSCOPE_ITEM.get();
        Inventory inventory = player.getInventory();
        boolean carries = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(sneakoscope)) {
                carries = true;
                break;
            }
        }
        if (!carries) {
            STATES.remove(player);
            return;
        }
        int strength = 0;
        if (player.isAlive() && !player.isSpectator()) {
            strength = SneakoscopeSensor.strength(player.serverLevel(), player.getBoundingBox().getCenter(), player);
        }
        boolean spinning = SneakoscopeRules.spinning(strength);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(sneakoscope) && SneakoscopeItem.isSpinning(stack) != spinning) {
                SneakoscopeItem.setSpinning(stack, spinning);
            }
        }
        State state = STATES.get(player);
        int old = state == null ? 0 : state.strength;
        if (strength <= 0) {
            STATES.remove(player);
            return;
        }
        if (state == null) {
            state = new State();
            STATES.put(player, state);
        }
        state.nextWhistle = SneakoscopeRules.nextWhistle(now, state.nextWhistle, old, strength);
        state.strength = strength;
    }
}
```

- [ ] **Step 2: Call it.** In `src/main/java/com/hogwartsmagic/event/ModEvents.java`, inside `onPlayerTick` (server branch), find:

```java
            trySpawnInsomniaDementor(player);
```

and replace it with:

```java
            trySpawnInsomniaDementor(player);
            com.hogwartsmagic.sneakoscope.SneakoscopeCarried.tick(player);
```

(`grep -c "trySpawnInsomniaDementor(player);" src/main/java/com/hogwartsmagic/event/ModEvents.java` prints `1` at f5033f3.)

- [ ] **Step 3: The item model property.** In `src/main/java/com/hogwartsmagic/event/ClientModEvents.java`, inside `onClientSetup`'s `enqueueWork`, find:

```java
            ItemBlockRenderTypes.setRenderLayer(ExampleMod.HORCRUX.get(), RenderType.cutout());
```

and replace it with:

```java
            ItemBlockRenderTypes.setRenderLayer(ExampleMod.HORCRUX.get(), RenderType.cutout());
            net.minecraft.client.renderer.item.ItemProperties.register(ExampleMod.SNEAKOSCOPE_ITEM.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "spinning"),
                    (stack, level, entity, seed) -> com.hogwartsmagic.item.SneakoscopeItem.isSpinning(stack) ? 1.0f : 0.0f);
```

(`ItemProperties.register(Item, ResourceLocation, ItemPropertyFunction)` is public in the 1.21 mapped jar.)

- [ ] **Step 4: Build and greps.**
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 381 tests, all green.
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client\|switch\|SubscribeEvent" src/main/java/com/hogwartsmagic/sneakoscope`. Expected: nothing.
  - `grep -n "SneakoscopeCarried" src/main/java/com/hogwartsmagic/event/ModEvents.java`. Expected: exactly one line.

- [ ] **Step 5: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Carried Sneakoscope: once-a-second check, spin component and holder-only whistle

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeCarried.java
git commit -F /tmp/msg.txt -- src/main/java/com/hogwartsmagic/sneakoscope/SneakoscopeCarried.java src/main/java/com/hogwartsmagic/event/ModEvents.java src/main/java/com/hogwartsmagic/event/ClientModEvents.java
```

---

### Task 4: The look (textures, models, blockstate)

**Files:**
- Create: `build_scripts/generate_sneakoscope_textures.py`
- Create (generated): `src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_brass.png`, `sneakoscope_glass.png`, `sneakoscope_glass_spin.png`, `sneakoscope_glass_spin.png.mcmeta`
- Create: `src/main/resources/assets/hogwartsmagic/models/block/sneakoscope.json`, `sneakoscope_spinning.json`
- Create: `src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json`
- Create: `src/main/resources/assets/hogwartsmagic/models/item/sneakoscope.json`, `sneakoscope_spinning.json`

**Interfaces.** Consumes: registry name `hogwartsmagic:sneakoscope` and property `power` (T2); item property `hogwartsmagic:spinning` (T3). Produces: resources only.

- [ ] **Step 1: The generator.** Create `build_scripts/generate_sneakoscope_textures.py`:

```python
"""Paint the Sneakoscope block textures.

    python3 build_scripts/generate_sneakoscope_textures.py [--preview]

Writes three sheets to assets/hogwartsmagic/textures/block/:

  sneakoscope_brass.png        base plate, pivot and spindle (opaque)
  sneakoscope_glass.png        the glass top at rest (translucent)
  sneakoscope_glass_spin.png   the glass top spinning and glowing, 6 animated frames (16x96, with .mcmeta)

With --preview it also writes /tmp/sneakoscope_preview.png: the brass and glass sheets and the six spin frames at
8x over a checkerboard (so the glass alpha shows), for a look before the game.

WHAT IT DEPICTS
---------------
A Sneakoscope: a small glass spinning top on a brass stand, 6 x 8 x 6 pixels in the block (models/block/
sneakoscope.json). At rest the glass is pale aqua and clear. When it detects something it spins, lights up and
whistles: the spinning sheet is warmer and brighter, and the model draws it at full brightness (forge_data light 15).

WHY THE SPIN IS A COLUMN THAT STEPS
-----------------------------------
The models leave the UVs out, so Minecraft maps each face from its position in the block. Every side face of the
glass body (x or z from 5 to 11, y from 2 to 6) reads texture columns 5..10 and rows 10..13. A bright facet edge
that steps one column per frame across columns 5..10 (period 6, the face width) sweeps across all four sides at
once, which reads as the top turning. Two ticks a frame and no interpolation: a crisp, quick spin.

STYLE
-----
As generate_wizard_cauldron_textures.py: tileable value noise (the lattice wraps at 16), then every colour is
snapped onto a short ramp through a 4x4 Bayer matrix. The glass ramps carry alpha, so the dither also dithers the
transparency. Brass gets a few single-pixel polish glints.
"""

import math
import os
import random
import sys

from PIL import Image

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "block")
PREVIEW = "/tmp/sneakoscope_preview.png"

SEED = 1993

BAYER = [
    [0, 8, 2, 10],
    [12, 4, 14, 6],
    [3, 11, 1, 9],
    [15, 7, 13, 5],
]

# Polished brass, dark in the scratches, bright where it is rubbed.
BRASS = [
    (70, 48, 20, 255),
    (100, 72, 30, 255),
    (134, 100, 42, 255),
    (170, 132, 56, 255),
    (204, 164, 78, 255),
    (234, 200, 116, 255),
]
# Clear glass at rest: pale aqua, more opaque where it is brighter.
GLASS = [
    (120, 168, 176, 110),
    (150, 196, 202, 130),
    (184, 222, 224, 150),
    (220, 242, 240, 175),
    (246, 252, 250, 210),
]
# Spinning glass: lit from inside, warm.
GLOW = [
    (196, 150, 120, 170),
    (226, 176, 132, 190),
    (246, 204, 150, 210),
    (255, 228, 180, 225),
    (255, 246, 222, 240),
]
EDGE_HI = (255, 252, 240, 235)
EDGE_MID = (250, 232, 200, 215)

FRAMES = 6
FACE_LEFT = 5    # first texture column of every glass side face
FACE_WIDTH = 6   # columns 5..10


def value_noise(rng, cells=4, size=16):
    """Tileable value noise in 0..1 on a size x size grid, lattice wraps every `cells`."""
    step = size // cells
    lattice = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            fx, fy = gx - int(gx), gy - int(gy)
            sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            top = lattice[y0][x0] * (1 - sx) + lattice[y0][x1] * sx
            bot = lattice[y1][x0] * (1 - sx) + lattice[y1][x1] * sx
            out[y][x] = top * (1 - sy) + bot * sy
    return out


def ramp_pick(ramp, value, x, y):
    """Snap value (0..1) onto the ramp with an ordered dither."""
    value = max(0.0, min(1.0, value))
    scaled = value * (len(ramp) - 1)
    lo = int(scaled)
    frac = scaled - lo
    threshold = (BAYER[y % 4][x % 4] + 0.5) / 16.0
    idx = lo + 1 if frac > threshold and lo + 1 < len(ramp) else lo
    return ramp[idx]


def paint_brass(rng):
    img = Image.new("RGBA", (16, 16))
    coarse = value_noise(rng, cells=4)
    detail = value_noise(rng, cells=8)
    for y in range(16):
        for x in range(16):
            v = 0.52 + (coarse[y][x] - 0.5) * 0.30 + (detail[y][x] - 0.5) * 0.12
            if y == 15:
                v -= 0.12  # the base plate's sides read row 15: its lower edge sits in shadow
            img.putpixel((x, y), ramp_pick(BRASS, v, x, y))
    for _ in range(10):
        img.putpixel((rng.randrange(16), rng.randrange(15)), BRASS[5])
    return img


def glass_value(field, x, y):
    v = 0.45 + (field[y][x] - 0.5) * 0.35
    if y in (11, 12):
        v += 0.15  # the bright band round the widest part of the top
    if (x - FACE_LEFT) % FACE_WIDTH in (0, FACE_WIDTH - 1):
        v -= 0.12  # face edges a little darker, so the top reads as faceted
    return v


def paint_glass(field):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), ramp_pick(GLASS, glass_value(field, x, y), x, y))
    return img


def paint_spin(field):
    sheet = Image.new("RGBA", (16, 16 * FRAMES))
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                v = glass_value(field, x, y) + 0.10 + 0.08 * math.sin((y + f) * math.pi / 3)
                colour = ramp_pick(GLOW, v, x, y)
                phase = (x - FACE_LEFT - f) % FACE_WIDTH
                if phase == 0:
                    colour = EDGE_HI
                elif phase == 1:
                    colour = EDGE_MID
                elif phase == 3:
                    colour = ramp_pick(GLOW, v + 0.25, x, y)  # the opposite facet, fainter
                sheet.putpixel((x, y + 16 * f), colour)
    return sheet


def write_preview(tiles, scale=8, gap=8):
    side = 16 * scale
    img = Image.new("RGBA", (gap + len(tiles) * (side + gap), side + 2 * gap), (24, 24, 28, 255))
    for i, tile in enumerate(tiles):
        ox, oy = gap + i * (side + gap), gap
        for ty in range(16):
            for tx in range(16):
                shade = 70 if (tx + ty) % 2 == 0 else 50
                img.paste((shade, shade, shade, 255),
                          (ox + tx * scale, oy + ty * scale, ox + (tx + 1) * scale, oy + (ty + 1) * scale))
        img.alpha_composite(tile.resize((side, side), Image.Resampling.NEAREST), (ox, oy))
    img.save(PREVIEW)


def main():
    rng = random.Random(SEED)
    out = os.path.abspath(OUT_DIR)
    os.makedirs(out, exist_ok=True)
    brass = paint_brass(rng)
    field = value_noise(rng, cells=4)
    glass = paint_glass(field)
    spin = paint_spin(field)
    brass.save(os.path.join(out, "sneakoscope_brass.png"))
    glass.save(os.path.join(out, "sneakoscope_glass.png"))
    spin.save(os.path.join(out, "sneakoscope_glass_spin.png"))
    with open(os.path.join(out, "sneakoscope_glass_spin.png.mcmeta"), "w", encoding="utf-8") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')
    print("wrote 3 textures to", out)
    if "--preview" in sys.argv[1:]:
        frames = [spin.crop((0, 16 * f, 16, 16 * f + 16)) for f in range(FRAMES)]
        write_preview([brass, glass] + frames)
        print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run it and look.** `cd /home/user/minecraft_mods && python3 build_scripts/generate_sneakoscope_textures.py --preview`. Expected: `wrote 3 textures to .../textures/block` and `wrote /tmp/sneakoscope_preview.png`. Then `python3 -c "from PIL import Image;import sys;[print(p, Image.open(p).size, Image.open(p).mode) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_brass.png src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass.png src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass_spin.png`. Expected: `(16, 16) RGBA`, `(16, 16) RGBA`, `(16, 96) RGBA`. Open `/tmp/sneakoscope_preview.png` with the Read tool and check: the brass is warm gold, not green or grey; the glass is pale and see-through over the checkerboard; across the six spin frames a bright column steps to the right. Report what you saw; the controller looks at the same file.

- [ ] **Step 3: The still block model.** Create `src/main/resources/assets/hogwartsmagic/models/block/sneakoscope.json`:

```json
{
  "parent": "block/block",
  "render_type": "minecraft:translucent",
  "ambientocclusion": false,
  "textures": {
    "particle": "hogwartsmagic:block/sneakoscope_brass",
    "brass": "hogwartsmagic:block/sneakoscope_brass",
    "glass": "hogwartsmagic:block/sneakoscope_glass"
  },
  "elements": [
    {
      "from": [5, 0, 5],
      "to": [11, 1, 11],
      "faces": {
        "down": { "texture": "#brass", "cullface": "down" },
        "up": { "texture": "#brass" },
        "north": { "texture": "#brass" },
        "south": { "texture": "#brass" },
        "east": { "texture": "#brass" },
        "west": { "texture": "#brass" }
      }
    },
    {
      "from": [7, 1, 7],
      "to": [9, 2, 9],
      "faces": {
        "north": { "texture": "#brass" },
        "south": { "texture": "#brass" },
        "east": { "texture": "#brass" },
        "west": { "texture": "#brass" }
      }
    },
    {
      "from": [5, 2, 5],
      "to": [11, 6, 11],
      "faces": {
        "down": { "texture": "#glass" },
        "up": { "texture": "#glass" },
        "north": { "texture": "#glass" },
        "south": { "texture": "#glass" },
        "east": { "texture": "#glass" },
        "west": { "texture": "#glass" }
      }
    },
    {
      "from": [6, 6, 6],
      "to": [10, 7, 10],
      "faces": {
        "up": { "texture": "#glass" },
        "north": { "texture": "#glass" },
        "south": { "texture": "#glass" },
        "east": { "texture": "#glass" },
        "west": { "texture": "#glass" }
      }
    },
    {
      "from": [7.5, 7, 7.5],
      "to": [8.5, 8, 8.5],
      "faces": {
        "up": { "texture": "#brass" },
        "north": { "texture": "#brass" },
        "south": { "texture": "#brass" },
        "east": { "texture": "#brass" },
        "west": { "texture": "#brass" }
      }
    }
  ]
}
```

- [ ] **Step 4: The spinning block model.** Create `src/main/resources/assets/hogwartsmagic/models/block/sneakoscope_spinning.json` (same geometry; the glass is the animated sheet and its faces are full-bright):

```json
{
  "parent": "block/block",
  "render_type": "minecraft:translucent",
  "ambientocclusion": false,
  "textures": {
    "particle": "hogwartsmagic:block/sneakoscope_brass",
    "brass": "hogwartsmagic:block/sneakoscope_brass",
    "glass": "hogwartsmagic:block/sneakoscope_glass_spin"
  },
  "elements": [
    {
      "from": [5, 0, 5],
      "to": [11, 1, 11],
      "faces": {
        "down": { "texture": "#brass", "cullface": "down" },
        "up": { "texture": "#brass" },
        "north": { "texture": "#brass" },
        "south": { "texture": "#brass" },
        "east": { "texture": "#brass" },
        "west": { "texture": "#brass" }
      }
    },
    {
      "from": [7, 1, 7],
      "to": [9, 2, 9],
      "faces": {
        "north": { "texture": "#brass" },
        "south": { "texture": "#brass" },
        "east": { "texture": "#brass" },
        "west": { "texture": "#brass" }
      }
    },
    {
      "from": [5, 2, 5],
      "to": [11, 6, 11],
      "faces": {
        "down": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "up": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "north": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "south": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "east": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "west": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } }
      }
    },
    {
      "from": [6, 6, 6],
      "to": [10, 7, 10],
      "faces": {
        "up": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "north": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "south": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "east": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } },
        "west": { "texture": "#glass", "forge_data": { "block_light": 15, "sky_light": 15 } }
      }
    },
    {
      "from": [7.5, 7, 7.5],
      "to": [8.5, 8, 8.5],
      "faces": {
        "up": { "texture": "#brass" },
        "north": { "texture": "#brass" },
        "south": { "texture": "#brass" },
        "east": { "texture": "#brass" },
        "west": { "texture": "#brass" }
      }
    }
  ]
}
```

- [ ] **Step 5: The blockstate.** Create `src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json`:

```json
{
  "variants": {
    "power=0": { "model": "hogwartsmagic:block/sneakoscope" },
    "power=1": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=2": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=3": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=4": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=5": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=6": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=7": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=8": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=9": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=10": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=11": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=12": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=13": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=14": { "model": "hogwartsmagic:block/sneakoscope_spinning" },
    "power=15": { "model": "hogwartsmagic:block/sneakoscope_spinning" }
  }
}
```

- [ ] **Step 6: The item models.** Create `src/main/resources/assets/hogwartsmagic/models/item/sneakoscope.json`:

```json
{
  "parent": "hogwartsmagic:block/sneakoscope",
  "display": {
    "gui": { "rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.9, 0.9, 0.9] }
  },
  "overrides": [
    { "predicate": { "hogwartsmagic:spinning": 1.0 }, "model": "hogwartsmagic:item/sneakoscope_spinning" }
  ]
}
```

Create `src/main/resources/assets/hogwartsmagic/models/item/sneakoscope_spinning.json`:

```json
{
  "parent": "hogwartsmagic:block/sneakoscope_spinning",
  "display": {
    "gui": { "rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.9, 0.9, 0.9] }
  }
}
```

- [ ] **Step 7: Validate and build.**
  - `cd /home/user/minecraft_mods && python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/models/block/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/block/sneakoscope_spinning.json src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/item/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/item/sneakoscope_spinning.json src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass_spin.png.mcmeta`. Expected: no output.
  - `python3 -c "import json;v=json.load(open('src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json'))['variants'];assert sorted(v)==sorted('power=%d'%i for i in range(16)),v;print('16 variants')"`. Expected: `16 variants`.
  - Run the generator a second time and `git status --short src/main/resources/assets/hogwartsmagic/textures/block/` shows only the four new untracked files (the output is deterministic).
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 381 tests.

- [ ] **Step 8: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Sneakoscope look: generated brass and glass textures, still and spinning models

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
P="build_scripts/generate_sneakoscope_textures.py src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_brass.png src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass.png src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass_spin.png src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass_spin.png.mcmeta src/main/resources/assets/hogwartsmagic/models/block/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/block/sneakoscope_spinning.json src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/item/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/item/sneakoscope_spinning.json"
git add $P
git commit -F /tmp/msg.txt -- $P
```

---

### Task 5: Getting one (recipe, trade) and the wikis

**Files:**
- Create: `src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json`
- Modify: `src/main/java/com/hogwartsmagic/entity/ModVillagers.java`
- Modify: `MOD_FEATURES_WIKI.md`, `wiki/data.js`

**Interfaces.** Consumes: `ExampleMod.SNEAKOSCOPE_ITEM` (T2), `ModVillagers.sells` and `one(RegistryObject)` (existing). Produces: nothing other tasks use.

- [ ] **Step 1: The recipe.** Create `src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json`:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "NIN",
    "NPN",
    " A "
  ],
  "key": {
    "N": { "item": "minecraft:gold_nugget" },
    "I": { "item": "minecraft:gold_ingot" },
    "P": { "item": "minecraft:glass_pane" },
    "A": { "item": "hogwartsmagic:arcane_dust" }
  },
  "result": { "id": "hogwartsmagic:sneakoscope", "count": 1 }
}
```

- [ ] **Step 2: The trade.** In `src/main/java/com/hogwartsmagic/entity/ModVillagers.java`, in the `CURIO_DEALER` branch, find:

```java
            sells(event, 2, 12, 5, one(ExampleMod.GOBLIN_SILVER_DAGGER), 4, 10);
```

and replace it with:

```java
            sells(event, 2, 12, 5, one(ExampleMod.GOBLIN_SILVER_DAGGER), 4, 10);
            sells(event, 2, 14, 1, one(ExampleMod.SNEAKOSCOPE_ITEM), 4, 10); // always exactly 14 emeralds
```

- [ ] **Step 3: `MOD_FEATURES_WIKI.md`.** Find:

```markdown
### Gillyweed Plant
```

and replace it with:

```markdown
### Sneakoscope

- A small glass spinning top on a brass stand. It spins, glows and whistles when a Death Eater, Lord Voldemort or a Dementor is within 16 blocks, or the Dark Mark within 48 (both ranges are in the config).
- Carried anywhere in your inventory, it whistles to you alone, faster as the danger comes closer: every 2 seconds at the edge of the range, every half second up close.
- Placed, it is a redstone sensor: signal 1 at the edge of the range up to 15 at the source, on every side, readable by a comparator. It whistles once when it starts detecting and gives light while it spins.
- Crafting: a gold ingot on top, a glass pane in the middle, four gold nuggets round it and arcane dust below. The Curio Dealer sells one for 14 emeralds.

### Gillyweed Plant
```

- [ ] **Step 4: `wiki/data.js`.** Find:

```js
    { name: "Gillyweed Plant", kind: "Worldgen Plant",
```

and replace it with:

```js
    { name: "Sneakoscope", kind: "Magic Block", summary: "Glass spinning top that spins and whistles when dark wizards, Dementors or the Dark Mark are near.", features: ["Carried: whistles to the holder, faster as danger comes closer", "Placed: redstone signal 1-15 on every side, light while it spins", "Death Eaters, Voldemort and Dementors within 16 blocks, the Dark Mark within 48", "Crafted, or bought from the Curio Dealer for 14 emeralds"] },
    { name: "Gillyweed Plant", kind: "Worldgen Plant",
```

- [ ] **Step 5: Check and build.**
  - `cd /home/user/minecraft_mods && python3 -c "import json;json.load(open('src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json',encoding='utf-8'))"`. Expected: no output.
  - `grep -c 'name: "Sneakoscope"' wiki/data.js` prints `1`; if `command -v node` prints a path, `node --check wiki/data.js` prints nothing.
  - `grep -c "### Sneakoscope" MOD_FEATURES_WIKI.md` prints `1`.
  - Gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 381 tests.

- [ ] **Step 6: Commit.**

```bash
cd /home/user/minecraft_mods
cat > /tmp/msg.txt <<'EOF'
Sneakoscope recipe, Curio Dealer trade and wiki entries

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01UR9Q5BKK9YdjWooMfEvCTs
EOF
git add src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json
git commit -F /tmp/msg.txt -- src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json src/main/java/com/hogwartsmagic/entity/ModVillagers.java MOD_FEATURES_WIKI.md wiki/data.js
```

---

### Task 6: Final verification

**Files:** none changed. A fix goes back to the task that owns the file.

- [ ] **Step 1:** Run the gradle check, then `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew clean compileJava test --console=plain --max-workers=2`. Expected: BUILD SUCCESSFUL, 381 tests (360 + 21), all green.
- [ ] **Step 2:** Validate every JSON file the phase touched: `python3 -c "import json,sys;[json.load(open(p,encoding='utf-8')) for p in sys.argv[1:]]" src/main/resources/assets/hogwartsmagic/lang/en_us.json src/main/resources/data/hogwartsmagic/loot_table/blocks/sneakoscope.json src/main/resources/data/hogwartsmagic/recipe/sneakoscope.json src/main/resources/assets/hogwartsmagic/blockstates/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/block/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/block/sneakoscope_spinning.json src/main/resources/assets/hogwartsmagic/models/item/sneakoscope.json src/main/resources/assets/hogwartsmagic/models/item/sneakoscope_spinning.json src/main/resources/assets/hogwartsmagic/textures/block/sneakoscope_glass_spin.png.mcmeta`. Expected: no output.
- [ ] **Step 3:** Safety greps:
  - `grep -rn "net.minecraft.client\|com.hogwartsmagic.client" src/main/java/com/hogwartsmagic/sneakoscope src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java`. Expected: nothing.
  - `grep -rn "switch" src/main/java/com/hogwartsmagic/sneakoscope src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java src/main/java/com/hogwartsmagic/item/SneakoscopeItem.java`. Expected: nothing.
  - `grep -rn "SubscribeEvent\|getChunk\|getBlockState" src/main/java/com/hogwartsmagic/sneakoscope`. Expected: nothing (no new handler, no chunk access).
  - `grep -rn "broadcastEntityEvent" src/main/java/com/hogwartsmagic/sneakoscope src/main/java/com/hogwartsmagic/block/SneakoscopeBlock.java src/main/java/com/hogwartsmagic/block/entity/SneakoscopeBlockEntity.java`. Expected: nothing.
  - `git status --short`. Expected: clean except untracked `run/` output.
- [ ] **Step 4:** Run the boot block (Global Constraints). Expected: `Done (`; no registry, data component, blockstate, model or class-loading errors; no server java process left afterwards.
- [ ] **Step 5:** `cd /home/user/minecraft_mods && timeout 1800 bash ./gradlew build -x test --console=plain --max-workers=2` and report the jar path under `build/libs/`. No commit.

---

### Task 7: In-game checklist (for the human)

This cannot be automated. Install the jar from Task 6. Use a creative world, then survival for rows 3 and 15.

| # | Setup | Do | Expected |
|---|---|---|---|
| 1 | Creative tab | Look right after the Wizard Cauldron | "Sneakoscope": a small brass stand with a clear glass top; tooltip names Death Eaters, Dementors and the Dark Mark |
| 2 | Hotbar | Try to stack two Sneakoscopes | They do not stack (one per slot) |
| 3 | Survival, crafting table | Gold ingot top centre, nuggets in the top corners and beside the pane, pane in the middle, arcane dust below | One Sneakoscope |
| 4 | Sneakoscope in your inventory (not in the hand), peaceful off | Spawn a Death Eater (`/summon hogwartsmagic:wizard ~20 ~ ~`) | Nothing: 20 blocks is out of range |
| 5 | Row 4 | Let it walk towards you (or `/tp` it to 15, 8 and 2 blocks) | Within 16 blocks: a short high flute-and-chime whistle, about every 2 s at 15 blocks, faster as it comes closer, about 2 a second at 2 blocks |
| 6 | Row 5 | Look at the item in the inventory and in the hand | It spins (a bright edge sweeping round), the glass glows blue-white; no bob of the hand when it starts or stops |
| 7 | Row 5, a second player 5 blocks away | Listen on the second player | The second player hears nothing |
| 8 | Row 5 | Kill the Death Eater | Within a second the whistle stops and the item stops spinning |
| 9 | Sneakoscope in the off hand only | Summon a Dementor 6 blocks away | Whistles and spins |
| 10 | Row 9 | Press Q on a spinning one in the main hand | The dropped item is not spinning |
| 11 | Summon Voldemort (`/summon hogwartsmagic:voldemort ~10 ~ ~`) near a carried Sneakoscope | | Whistles and spins |
| 12 | Place a Sneakoscope on a table, a redstone lamp next to it | Nothing near | Still model, no light, lamp off |
| 13 | Row 12 | Summon a Dementor about 12 blocks away, let it fly in | One whistle when it first comes in range (heard by everyone nearby); the top spins and glows, gives light; the lamp turns on |
| 14 | Row 13, a comparator facing away from the Sneakoscope, then a line of redstone dust | Move the Dementor from 15 to 2 blocks (`/tp`) | The dust's lit length grows as it comes closer (1 at the edge, 14 at 2 blocks); no extra whistles while it stays in range |
| 15 | Row 13 | Kill the Dementor | Within a second: lamp off, still model, no light |
| 16 | Place a Sneakoscope, cast Morsmordre (or summon `hogwartsmagic:dark_mark` 40 blocks above it) | Read with a comparator and dust | A weak signal, 3 |
| 17 | Row 16, place a second one 50 blocks to the side | | The second detects nothing |
| 18 | Break a placed Sneakoscope by hand | | It drops itself |
| 19 | Push one with a piston | | It breaks and drops itself |
| 20 | Curio Dealer (Knockturn Alley), a villager that reaches level 2 after this update | Look at the trades | Sneakoscope for exactly 14 emeralds |
| 21 | Config `sneakoscopeRange = 32` (restart) | Death Eater at 20 blocks | Carried whistles; placed signal 6 |
| 22 | Dedicated server and a client | Rows 5, 6 and 13 | The spin and the sounds show; no registry or component mismatch on join |
| 23 | Leave a spinning one in a chest, quit and reload | Open the chest | It is still (the spin is never saved) |

A failure goes back to the implementer of the owning task with the row number and `latest.log`.

---

## Self-review notes

- **Spec coverage, Part A.**
  - Decision 1 (one item, a `BlockItem`, drops itself): Task 2 steps 5-7; rows 1, 18, 19.
  - Decision 2 (sources and ranges, never the holder, invisibility does not hide): `SneakoscopeRules.Source`, `SneakoscopeSensor.PROBES` (Voldemort through `WizardEntity`), `e != holder`, no visibility filter; rulings 12, 23; tests `defaultsMatchTheSpec`, `onlyTheDarkMarkUsesTheSkyRange`; rows 4-5, 9, 11, 13, 16-17. The Corrupted/spectator/creative player rules are Part B (ruling 23).
  - Decision 3 (strength formula, own range for the Dark Mark): `strength(double,int)`, tests `strengthRoundsUp`, `aDarkMarkFortyBlocksUpIsWeak`; "nearest wins" changed to "strongest wins" (ruling 10, spec problem); rows 14, 16.
  - Decision 4 (carried: once a second per player, whistle 40 to 10 ticks, holder only, spin by component and model property, no chat): Task 3; rulings 1-8; tests `whistleInterval*`, `nextWhistle*`, `eachKeyIsChecked...`; rows 5-10.
  - Decision 5 (placed: block entity once a second staggered, `power`, signal on every side, whistle on 0 to more within 16 blocks, still and spinning models, light 7, no chunk loading): Task 2 steps 3-4, Task 4 steps 3-5; rulings 9, 12-14; test `aPlacedSneakoscopeWhistles...`; rows 12-17.
  - Decision 6 (glass top on brass, about 6 x 8 x 6, generator, animated strip): Task 4; rulings 16-18.
  - Decision 7 (flute 2.0 + chime 1.8): `SneakoscopeSensor.whistleTo/whistleAt`; rulings 8-9.
  - Decision 8 (recipe, trade level 2 for 14): Task 5 steps 1-2; rulings 19-20; rows 3, 20.
  - Decision 9 (server authoritative, no new packet): ruling 24; greps in Task 6.
  - Decision 10 (config): Task 2 step 1; ruling 21; row 21.
  - Done when: rows 5 (whistles faster), 13-14 (lamp and comparator), 16 (Dark Mark weak), Task 6 step 4 (boot).
- **Type consistency.** `SneakoscopeRules.Hit(Source, double)` everywhere; `strength(List<Hit>, int, int)` called by the sensor with `(hits, range, darkMarkRange)`; `SneakoscopeSensor.strength(ServerLevel, Vec3, Entity)` called with `(serverLevel, Vec3.atCenterOf(pos), null)` and `(player.serverLevel(), player.getBoundingBox().getCenter(), player)`; `SneakoscopeBlockEntity.serverTick(Level, BlockPos, BlockState, SneakoscopeBlockEntity)` matches `BlockEntityTicker`; the item property lambda matches `ItemPropertyFunction.call(ItemStack, ClientLevel, LivingEntity, int)`; `getSignal`/`getDirectSignal`/`isSignalSource`, `ServerPlayer.playNotifySound(SoundEvent, SoundSource, float, float)`, `Level.playSound(Player, double, double, double, Holder<SoundEvent>, SoundSource, float, float)`, `AABB.ofSize`, `Vec3.atCenterOf`, `ItemProperties.register`, `shouldCauseReequipAnimation`, `onDroppedByPlayer` and `DataComponentType.Builder` were checked against the 1.21 mapped jar; `forge_data` against the Forge 51.0.33 sources. `SoundEvents.NOTE_BLOCK_FLUTE` is a `Holder.Reference`, hence `.value()` for `playNotifySound`.
- **Placeholder scan:** none. Every number, sound, colour, pattern and message is given.
- **Counts:** Task 1 adds 21 tests. 360 + 21 = 381.
