# Audit report: blocks-items

Area: workbenches, brooms, Ward + Cave Inimicum, Floo, decorative blocks, Gillyweed, Fine Wood,
Polyjuice, Sorting / House Perks, cloak / silk / loot modifiers, Ollivanders ceremony, and the
all-registry resource check.

Resource check script (not in the repo):
`/tmp/claude-0/-home-user-minecraft-mods/094cca22-3c6a-51d9-857c-62a8ef26976c/scratchpad/blocks-items-resource-check.py`
It parses ExampleMod.java for every ITEMS / BLOCKS registration and checks: item model, blockstate,
every model in the blockstate and its parent chain, every texture png, lang key, creative tab entry,
block loot table, every recipe json (parses, has a type, every item / tag id exists), every loot table
item id, effect lang keys and potion lang keys.

## Features checked

| Feature | Status |
|---|---|
| Resource check, all 99 items / 35 blocks / 37 recipes | FIXED (see below) |
| Wand workbench (block, BE, menu, slot filters, crafting, starter Lumos, persistence) | FIXED (screen double background) |
| Broom workbench (slots, recipes, Alatus infusion, re-infusion, quality floor, persistence) | FIXED (block dropped nothing) |
| Broom item / entity: riding, tiers, quality component, save / load | FIXED (boost, pickup, placement in walls) |
| Ward block + Cave Inimicum (prepare, activate, preview, size cycle, expiry, visibility, totem) | FIXED (several) |
| Cave Inimicum client mixins (sound mute, entity hide) | OK |
| PlayerChunkSenderMixin (it is Diagon Alley, not Cave Inimicum) | OK |
| Floo fireplace / powder / binding token / travel / naming | FIXED (several) |
| Moving Portrait | FIXED (no directional placement, full-cube model) |
| Candle Cluster, Enchanted Book Pile, Wizard Cauldron (decorative only) | OK |
| Gillyweed plant / item / effect | FIXED (bone meal turned it into vanilla tall seagrass) |
| Fine Wood set (log, stripping, stripped log, leaves, sapling, planks, tags, recipes) | FIXED (no item tag: no charcoal, not fuel) |
| Arcane Cluster | OK |
| House banner blocks | OK |
| Thief's Downfall | OK |
| Chamber door | OK |
| Diagon archway brick | FIXED (no loot table) |
| Polyjuice: collector, sample, brewing chain, redstone, splash, drinking, delay, effect, cleanup | OK |
| Wizard Potion (Mana Regeneration) + Wizard Elixir effect | FIXED (potion name lang keys) |
| Sorting Hat, Ollama service (fails gracefully with no server), session flow | FIXED (fallback ignored answers) |
| House Perks | FIXED (Hufflepuff attribute packet every tick) |
| House robes, house reveal items | OK |
| Hogwarts invite / guide items + screens | FIXED (lang key, screen background) |
| Invisibility cloak item, mixin, loot modifier; magical silk + loot; sorting hat loot | OK |
| Ollivanders ceremony + /ollivanders | FIXED (body left at camera spot, endAll never called) |

## Fixes

1. **Broom boost never made you faster.** `entity/BroomEntity.java:470`. The rider's client drives the
   broom (vanilla: the controlling player's client is authoritative and sends ServerboundMoveVehicle),
   but `isBoosting` is only set on the server by BroomBoostPacket, so on the client it was always false
   and the broom flew at cruise speed while boosting (only FOV, sound, particles and stamina drain
   happened). A player holds sprint on a broom and nothing speeds up. Change: on the client read the
   synced `isBoostingSynced()`. Note: this makes the existing numbers real: boost is 6.5 / 8 / 10
   blocks per tick (130 to 200 blocks per second) times quality. I did not change any number; the
   controller may want to look at whether those speeds are what is wanted now that they apply.
2. **A placed broom could never become an item again** (listed as a known leftover in FINAL-REPORT).
   `entity/BroomEntity.java:120-169`, `item/BroomItem.java:52`. Punching an empty broom now picks it
   up as its item with its Alatus quality (no drop in creative or with doEntityDrops off). Brooms placed
   from an item carry a saved `DropsItem` flag; NPC wizard brooms do not, so punching one just removes
   it (no free brooms from wizards, and their abandoned brooms no longer pile up forever). Brooms saved
   before the flag existed count as player brooms.
3. **Broom placed inside a wall.** `item/BroomItem.java:56`. Using the broom while looking at a wall put
   it 1.5 blocks ahead, inside the blocks. Now it falls back to the player's own spot.
4. **Broom workbench, Ward, Horcrux, Diagon archway brick and Willow sapling dropped nothing when
   broken.** New `data/hogwartsmagic/loot_table/blocks/{broom_workbench,ward,horcrux,diagon_archway_brick,willow_sapling}.json`
   (self drop, survives_explosion). A player who mined their broom bench or ward lost it. Horcrux keeps
   its own rule (HorcruxBlock.getDrops returns nothing while active or primed); this only lets an
   inactive one drop, which its code already intended.
5. **Potion names showed raw keys.** The lang had `item.minecraft.potion.effect.hogwartsmagic.wizard`,
   but Potion.getName uses only the registry path (checked in the 1.21 bytecode), so the real key is
   `item.minecraft.potion.effect.wizard`. Added potion / splash / lingering / tipped arrow keys for
   `wizard` and `stabilized_polyjuice_base` (the creative tab lists all four forms). Also added
   `effect.hogwartsmagic.lumos`, `.protego`, `.tenebris_flight` (raw keys in the inventory effect list),
   `item.hogwartsmagic.hogwarts_invite`, `block.hogwartsmagic.patronus_light_block`.
6. **Patronus light block had no blockstate** (model error spam at load, missing-texture particles).
   New `assets/hogwartsmagic/blockstates/patronus_light_block.json` pointing to `minecraft:block/air`,
   same as `lumos_light`.
7. **Cave Inimicum showed the real build to anyone further than 48 blocks.** `caveinimicum/CaveInimicumManager.java`.
   A chunk sent to a player carries the real blocks, and the manager only re-sent the illusion to
   players within `max(48, edge*3)` blocks, so a player flying in saw the hidden base from view distance
   until 48 blocks, and any block change inside reached far players as the real block. Change:
   (a) a `ChunkWatchEvent.Watch` handler queues a re-sync of any illusion ward whose chunk was just sent
   to a player who should not see reality, flushed once per tick (deduplicated);
   (b) relevance range is at least the server view distance plus the ward edge.
8. **Cave Inimicum could load chunks.** Same file plus `block/entity/CaveInimicumWardBlockEntity.java:393`.
   `trackedWards` called getBlockEntity before checking isLoaded; region sync, block entity re-send, the
   every-4-tick change scan, and the actual-state cache read every position with getBlockState. A big
   ward whose corner sits in an unloaded chunk (for example a ward in spawn chunks with nobody nearby)
   loaded that chunk every scan. Now all of these skip unloaded positions (a chunk the server has not
   loaded is not on any client, and the chunk-sent re-sync covers it once it arrives).
9. **Ward sent its whole snapshot to every client.** `CaveInimicumWardBlockEntity.getUpdateTag`. Up to a
   1 MB int array (64 edge) went out with every chunk send and every ward click (shift-click spam sends
   it to everyone nearby). Nothing on the client reads it. Removed from the update tag only (still saved).
10. **Floo travel took two powders.** `block/entity/FlooFireplaceBlockEntity.java:40-50,140,188-202`,
    `network/SpellPackets.java:1437,1459`. Dropping powder into the flames consumed it and opened the
    screen, then choosing a destination demanded and took a second powder from the inventory, so a
    player with one powder got "You need Floo Powder to travel". The dropped powder now pays for that
    one trip (single use, tied to that player and fireplace, 60 s); without it the inventory powder is
    still required, so there is no free-travel path.
11. **Floo could land you inside blocks.** `event/ModEvents.java:2379`. Arrival teleported to the block
    above the destination hearth without a collision check. Now a blocked exit fizzles the trip like a
    broken destination.
12. **Floo binding token loaded far chunks.** `item/FlooBindingTokenItem.java:49-57`. Linking to a stored
    fireplace in an unloaded chunk called getBlockEntity (sync chunk load). Now it says the first
    fireplace is too far away and keeps it stored. New lang key `message.hogwartsmagic.floo.first_unloaded`.
    (Travel already only lists loaded destinations, so this loses nothing.)
13. **Screens drew the blur and menu backdrop on top of their own panel.** `client/FlooNamingScreen.java`,
    `FlooTravelScreen.java`, `HogwartsGuideScreen.java`, `HogwartsInviteScreen.java`. They called
    `renderBackground` (blur) and then `super.render`, which in 1.21 calls `renderBackground` again
    after the panel was drawn. Now they use `renderTransparentBackground` and override
    `renderBackground` as a no-op (standing screen rule, same pattern as TomePickerScreen).
    `client/WandWorkbenchScreen.java:49` drew its background twice (same fix the broom bench already had).
14. **Moving Portrait had no directional placement and a full-cube model.** `block/MovingPortraitBlock.java:45-63`
    and `assets/hogwartsmagic/{blockstates,models/block,models/item}/moving_portrait.json`. It always
    placed facing north and rendered as a cube while its hit box was a 2-pixel panel. Now it faces out
    from the clicked wall (or toward the player on floor / ceiling), has rotate / mirror, a panel model
    using the existing texture, rotated blockstate variants, and a flat item icon.
15. **Bone meal destroyed Gillyweed.** `block/GillyweedPlantBlock.java:22`. It extends SeagrassBlock, whose
    bone meal replaces it with vanilla tall seagrass. Now not a bone meal target.
16. **Fine Wood logs were not fuel and could not make charcoal.** New
    `data/minecraft/tags/item/logs_that_burn.json` (only the block tag existed).
17. **Hufflepuff players got an attribute packet every tick.** `sorting/HousePerks.java:76-86`.
    applyHouseAttributes runs every player tick and removed and re-added the health modifier each time,
    which marks the attribute dirty. Now it only changes the modifier when the house state changes.
18. **Without Ollama the Sorting Hat sorted by hash, ignoring the answers.** `sorting/SortingHatManager.java:224-231`.
    The fallback house was `hash(transcript) % 4` even though a keyword scorer existed, so "brave, bold,
    I protect people" could land in Slytherin while the hat said "you could have suited Gryffindor".
    Now the fallback is the top keyword house (the scorer itself uses the hash when nothing matched).
    Graceful failure otherwise confirmed: connection refused gives fallback questions and a decision,
    and all callbacks hop back to the server thread.
19. **Ollivanders ceremony left the player where the camera was.** `event/OllivandersCeremony.java`.
    ServerPlayer.setCamera teleports the player to the camera and ServerPlayer.tick drags the body along
    with it every tick, so the body orbited through the shop walls and up to 3 blocks in the air, and at
    the end was left at the camera's last spot (possibly in a wall). Now the player is put back on the
    spot they stood on (same level only), and IN_WALL / FALL damage is cancelled while their scene runs.
    `endAll()` was never called (its own Javadoc says it runs on server stop); now hooked to
    ServerStoppingEvent so the marker armor stand is not saved into the world.

## Open items (not fixed)

- **Missing texture:** `textures/entity/flying_mob.png`, used by `client/FlyingMobRenderer.java:13`
  (flying_mob entity, mob area). No asset to reuse with certainty.
- **hogwarts_ruin_camp.nbt portraits face the wall.** The template stores `hogwartsmagic:moving_portrait`
  with no properties (default facing north) at [32,3,17] and [32,4,17], with the wall to their north.
  With the new panel model they need `facing=south`. Structure files are another agent's area; about a
  2-line palette edit with an NBT tool.
- **Floo network is limited to loaded fireplaces** (travel list skips unloaded ones, by the no-chunk-load
  rule). Design, not a bug; mention only.
- **Cave Inimicum, huge wards at the view edge:** a part of a 64-edge ward whose other chunks are not
  yet loaded shows reality until the last chunk arrives (then the chunk-sent re-sync fixes it).
  Small and rare.
- **Cave Inimicum block changes inside a ward** are broadcast by vanilla as real blocks and corrected at
  the next scan (4 ticks). A short flicker; fixing it needs a packet filter mixin (about 60 lines).
- **Wand workbench tornado** sends an extra ClientboundSetEntityMotionPacket to each nearby player every
  tick for 5 s on top of hurtMarked. Opinion: drop the explicit send; left as is (design, short window).
- **Ward block messages** and the Floo status line are hard-coded English (`Component.literal`). Opinion.
- **Floo fireplace** burns a player standing on it until the powder is dropped (vanilla campfire damage
  while not ACTIVE). Looks deliberate (entityInside skips only while active). Opinion.
- **Sample collector** cannot sample villagers (their trade screen takes the click first). Minor.
- **Fine Wood blocks are not flammable** (FireBlock flammability is not set). Minor.
- **Client mixins use `remap = false`** (CaveInimicumClientPacketListenerMixin,
  CaveInimicumEntityRenderDispatcherMixin, LevicorpusLivingEntityRendererMixin). Fine in dev; whether
  they apply in a production jar depends on the runtime mappings. Could not verify without a client.

## Doc mismatches (MOD_FEATURES_WIKI.md)

- Wizard Potion: wiki says Awkward + Werewolf Fang; code and the in-game tooltip say Awkward +
  Acromantula Venom. Code is right (wolf fang is a trade / loot item only).
- Polyjuice: wiki says Wizard Potion + sample; code is Wizard Potion + Mandrake Root -> Stabilized
  Polyjuice Base, then + filled sample -> Polyjuice, + gunpowder -> splash. Code (and tooltip) right.
- Wandmaker's Bench: wiki says "wand blank"; the item is the Fine Wood Stick. Code right.
- Moving Portrait: wiki says directional placement; it had none (now fixed).
- Broom Workbench, Floo and Cave Inimicum: wiki matches code after the fixes.
- Sorting Hat: wiki does not say how to get it (Wandmaker master trade, rare chest loot).

## Files changed

Java: entity/BroomEntity.java, item/BroomItem.java, caveinimicum/CaveInimicumManager.java,
block/entity/CaveInimicumWardBlockEntity.java, block/entity/FlooFireplaceBlockEntity.java,
network/SpellPackets.java (two lines, Floo), event/ModEvents.java (one condition, Floo),
item/FlooBindingTokenItem.java, block/MovingPortraitBlock.java, block/GillyweedPlantBlock.java,
sorting/HousePerks.java, sorting/SortingHatManager.java, event/OllivandersCeremony.java,
client/WandWorkbenchScreen.java, client/FlooNamingScreen.java, client/FlooTravelScreen.java,
client/HogwartsGuideScreen.java, client/HogwartsInviteScreen.java.

Resources: lang/en_us.json (14 keys added), blockstates/patronus_light_block.json (new),
blockstates/moving_portrait.json, models/block/moving_portrait.json, models/item/moving_portrait.json,
loot_table/blocks/{broom_workbench,ward,horcrux,diagon_archway_brick,willow_sapling}.json (new),
data/minecraft/tags/item/logs_that_burn.json (new). All json validated.

## Build

`compile.sh`: BUILD SUCCESSFUL (last run after all edits). Tests not run: no pure Rules/Math class in
this area was changed.
