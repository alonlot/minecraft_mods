# Audit report: core-magic

Area: mana, regen, learning, selection, cooldowns, relics, wands, spell menu and radial wheel, the cast path,
spellbooks and their loot, SpellPackets as a whole, the shared SpellBeamEntity mechanics, spell mastery
(data, awards, tomes, command, Grimoire, rank text), and the Config values these read.

## Features checked

- Mana storage, max mana sum (base + house + relics + mastery), regen tick, Wizard Elixir boost: OK
- Max mana change from a source with no resync of its own (house sorting): FIXED (no client sync, no clamp)
- Mana sync on login, respawn, dimension change, relic equip/unequip, rank change, config reload: OK
- Spell learning from books (whole-token match, sync to client, already-learned message, sound): OK
- Spell selection packet and 9 wand slots (server checks learned spell, slot range, main hand wand): OK
- Per-spell cooldown stored on the wand, SpellCosts (base x wand x mastery; cooldown also x house): OK
- Cast path, right-click: FIXED (one press at a block sent two cast packets; off-hand item was used too)
- Cast path, packet handler gates (learned, self-cast whitelist, stun/silence, heartbeats never cast, mana): OK
- Wand types: cost, cooldown, beam speed multipliers match the wiki words: OK
- Wand "spell power": OPEN (visual only, see doc mismatches)
- Unicorn mana refund (12% chance, 18% back, main cast, Wingardium start, corporeal Patronus): OK
- Elder Wand wins beam clashes against non-Elder beams: OK
- Wands wearing out from melee: FIXED (59 durability, Elder Wand could break)
- Spell menu screen: FIXED (panel blurred and covered by the menu backdrop)
- Relic screen: FIXED (same blur problem)
- Radial wheel (R hold, release to cast, click to bind, auto-fill, rank numerals, stun/silence grey): OK
- Radial and Grimoire hotkeys when unbound or bound to a mouse button: FIXED
- Slot cast keys 1 to 9: FIXED (no lang keys; Controls screen showed raw ids)
- Spell HUD cooldown bar: FIXED (bar drew outside its box when a cooldown ran longer than today's figure)
- Mana overlay: OK
- Relics: 2 slots, slot validation, equip/unequip with no dupe, recipes for the 3 crafted, found-only in chests: OK
- Spellbook item: name, tooltip, custom model data per spell: OK, except Levicorpus icon: OPEN
- MagicBookLootModifier and pools, guaranteed-table hook, Grimoire source hints, found-only relic roll: OK
- Global loot modifier jsons and registration (books, tomes): OK
- SpellPackets: every packet registered once, main-thread handlers, protocol version 6: OK
- SpellPackets: client-bound packets sent by a client: FIXED (only 6 of about 25 were guarded)
- SpellPackets: Floo choose/name packets validation: FIXED (no reach or loaded check)
- SpellBeamEntity travel, gravity 0, 200 tick life, hit dispatch, fizzle, mastery hook, reflected beams no XP: OK
- SpellBeamEntity clash detection: FIXED (about half of head-on clashes passed through)
- Mastery data (XP per spell, rank, sanitising, sync, reset, rank change resyncs mana): OK
- Mastery awards (hit / block / cast paths, ledger windows, PvP half and pair cap, spawner tag, logout cleanup): OK
- Mastery config sync to clients, LAN guard, reload broadcast, logout clear: OK
- Tome of Mastery (picker packet, full server re-validation, exactly one rank, celebrate, 4 items, models, lang): OK
- Tome loot (one roll, rare/endgame gates, structure tags exist and name real structures): OK
- /spellmastery get/set/setrank/reset (op 2, strict spell id): OK
- Rank-change text: every spell has spell.<id>.rank.2/3/4, plus rank names, header and generic line: OK
- Grimoire screen (no blur, rank line, bar, sources): OK
- Mastery unit tests: pass

## Fixes

1. network/SpellPackets.java `enqueueClientWork` (around line 582)
   Defect: client-bound packets (sync mana, learned spells, mastery, relics, open Floo screen, open tome picker,
   Horcrux sequence, possession, and so on) had no direction guard except six of them. On a LAN host the
   client bridge is live in the same JVM, so a guest that sent one made the server thread run client code:
   open screens on the host, overwrite the host's mana or learned spell display, or crash the host.
   Player: a LAN guest with a modified client.
   Change: one guard in the shared helper; a packet with a sender is marked handled and dropped. Every
   client-bound handler goes through that helper.

2. event/ModEvents.java `onRightClickItem` and `onRightClickBlock` (wand branch, around lines 449 and 522)
   Defect: the wand cancelled the use events with the default PASS result. Right-clicking within reach of a
   block fired RightClickBlock (cast packet 1), then vanilla went on to RightClickItem for the same wand (cast
   packet 2 in the same tick), then to the off-hand item. In creative that fired two beams (and Novice Lumos
   toggled on and straight off again); in survival it started using whatever was in the off-hand (eating, a
   raised shield) on every cast.
   Change: setCancellationResult(SUCCESS) before cancelling, so the client stops after the wand.

3. event/ModEvents.java `onPlayerTick` (around line 765)
   Defect: when max mana changed from a source with no resync of its own (being sorted into or out of
   Ravenclaw), MaxMana was saved but no packet was sent and mana was never clamped, so the bar showed the old
   max and mana could sit above a lowered max.
   Change: on a change, clamp mana and send one SyncManaPacket (only on the tick it changes).

4. entity/SpellBeamEntity.java `tryResolveBeamClash` (around line 1305) plus `segmentDistanceSqr` and two constants
   Defect: the clash test only looked at where the beams stood after moving (0.25 boxes, one inflated by 0.45).
   Two beams closing head-on cover 3 or more blocks between them per tick against a 1.4 block window, so about
   half of all duel clashes passed straight through (and the Elder Wand win never triggered).
   Player: two players firing at each other.
   Change: compare the paths each beam took on its last move (xo..x) with a closest-approach test at the same
   0.7 reach. Every beam checks (the id filter is gone) so the second of a pair to tick compares the same tick's
   moves. Beams that have not moved yet (tickCount 0) and a beam already removed this tick are skipped.

5. item/MagicWandItem.java `postHurtEnemy` override (line 39)
   Defect: MagicWandItem extends SwordItem on the wooden tier, so it has 59 durability and lost one per melee
   hit. Punching mobs with a wand broke it, the Elder Wand included. Nothing in the docs means wands to wear out.
   Change: override postHurtEnemy as a no-op. (If wand wear is wanted after all, delete the override.)

6. client/SpellMenuScreen.java and client/RelicScreen.java `render`
   Defect: both called renderBackground, drew the panel, then super.render, which in 1.21 calls renderBackground
   again: the menu blur ran twice and the darkened backdrop was drawn over the panel and its text. Against the
   standing rule ("custom screens use renderTransparentBackground, no blur").
   Change: the TomePicker pattern: no-op renderBlurredBackground and renderBackground, renderTransparentBackground
   at the start of render.

7. event/ClientModEvents.java radial and Grimoire key polling (new helper `isRawKeyHeld`)
   Defect: the keys were read with InputConstants.isKeyDown(window, key value). An unbound key (-1) makes GLFW
   log an "Invalid key" GL error every client tick, and a key rebound to a mouse button was read as a keyboard key.
   Change: unbound reads as not held, mouse buttons go through glfwGetMouseButton (same pattern as
   PossessionClient.isUseHeld).

8. lang/en_us.json `key.hogwartsmagic.cast_spell_slot_1..9`
   Defect: the nine slot cast key mappings had no lang keys, so Options, Controls showed raw ids.
   Change: added "Cast Spell Slot N". Another agent added the same nine keys at the same moment; I removed my
   copy so the file has exactly one set (checked: valid json, no duplicate keys).

9. network/SpellPackets.java `ChooseFlooDestinationPacket` and `SetFlooNamePacket`
   Defect: neither checked that the fireplace was loaded or near the sender. getBlockEntity on an unloaded
   position loads the chunk (a client could force chunk loads), a forged choose packet started Floo travel from
   anywhere in the world as long as it named a linked pair, and a forged name packet renamed any fireplace from
   any distance with a name of any length (up to 32767 chars, saved and synced).
   Change: both refuse unloaded positions and positions more than 8 blocks from the player; names are cut to 32
   characters (the naming screen's own limit).

10. client/SpellHUD.java cooldown bar
    Defect: progress = 1 - remaining / cooldownTicks went negative when the cooldown on the wand was longer than
    today's figure (very common: the rank-up from a cast lands right after that cast's cooldown was set, and
    penalty cooldowns are longer), and GuiGraphics.fill then drew the bar out to the left of the box.
    Change: clamp progress to 0..1.

## Open items (not fixed)

- Wand "spell power" does nothing in play. getSpellPowerMultiplier (and the attack-strength "charge tier")
  only sets the beam's charge, which only scales trail particles. The wiki says wands change spell power and
  that Dragon and Elder have higher power. Wiring it into damage touches every damaging spell (other agents'
  areas) and is a balance change. Estimate: 60 to 150 lines across spell classes. Needs a design decision.
- Levicorpus spellbook has no icon. SPELL_MODEL_ORDER skips "levicorpus" and there is no
  magic_book_levicorpus texture or model, so its book shows the plain magic_book. Needs art; then append
  "levicorpus" at index 40 and add the override and model json (3 small edits).
- Casting at a block within reach still uses the block on the server. The client cancels RightClickBlock for a
  wand, but the server never does, so a wand cast aimed at a chest, door, lever or a block with a menu also
  opens or toggles it. Cancelling server side is a one-line change, but it would also stop every block menu
  (for example the Wand Workbench) from opening while a wand is held, so it needs a decision. Estimate: 5 to
  20 lines with a whitelist.
- Off-hand wand is half supported. The spell menu, radial wheel, slot keys, HUD and mana bar all fall back to
  an off-hand wand, but the server only reads the main hand (select, set slot, cast). With the wand in the
  off-hand, slot edits only change the client copy and casts do nothing. Either make the server fall back too
  or drop the client fallbacks. Estimate: 15 to 30 lines.
- Stray files: textures/item/magic_books/"Gemini_Generated_Image_ (6)-Photoroom.png" and "(7)" are not
  referenced anywhere. Harmless; can be deleted.

## Opinions (not defects, not changed)

- Cooldowns live on the wand item, so carrying several wands lets a player skip a spell's cooldown by
  swapping wands. That is how it was built ("per-spell cooldown on the wand").
- The Chamber of Secrets spell pool has no Grimoire source hint of its own; its spells already show
  "Ancient City", which may be deliberate for a secret room.
- getMaxMana is called every player tick and deserialises the relic NBT twice per call. Fine for players,
  but could be cached if profiling ever shows it.

## Doc mismatches

- Wiki "Wand specializations ... spell power" and "Higher spell power" (Phoenix, Dragon, Elder): code makes
  spell power visual only. The code side is the one missing work (see Open items); or reword the wiki.
- Wiki "Spell Beam: Supports beam clashes / Elder priority": true, but before fix 4 half of head-on clashes
  were missed. Code now matches the wiki.
- Everything else in "Core Gameplay Systems", "Wand Types", "Relics" and "Spellbooks" matches the code
  (numbers: Student 1.12 cost / 1.06 cd / 0.95 beam, Phoenix 1.0 / 0.84 / 1.15, Unicorn 0.88 / 1.02 / 1.0 with
  the refund, Dragon 1.10 / 1.06 / 0.88, Elder 0.94 / 0.92 / 1.08; relics +20 / +35 / +50 max mana and
  +18% / +28% / +36% regen).

## Build

- Compile (scratchpad compile.sh): BUILD SUCCESSFUL after all fixes.
- Tests: `com.hogwartsmagic.spell.mastery.*` BUILD SUCCESSFUL (no pure class changed, so no new test).
- en_us.json validated with python json.load, no duplicate keys.

## Files changed

- src/main/java/com/hogwartsmagic/network/SpellPackets.java
- src/main/java/com/hogwartsmagic/event/ModEvents.java
- src/main/java/com/hogwartsmagic/event/ClientModEvents.java
- src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java
- src/main/java/com/hogwartsmagic/item/MagicWandItem.java
- src/main/java/com/hogwartsmagic/client/SpellMenuScreen.java
- src/main/java/com/hogwartsmagic/client/RelicScreen.java
- src/main/java/com/hogwartsmagic/client/SpellHUD.java
- src/main/resources/assets/hogwartsmagic/lang/en_us.json (only the 9 slot keys, now present once)
