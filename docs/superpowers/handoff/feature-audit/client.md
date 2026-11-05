# Audit report: client (client side and cross-cutting wiring)

Scope: client/ (screens, HUD overlays, key bindings, ClientRuntimeBridge, client/sounds), client/model, client/renderer,
event/ClientModEvents.java, event/ClientForgeEvents.java, util/ClientSideBridge.java, mixin config, sounds.json, lang.
Check scripts (not in the repo): /tmp/claude-0/-home-user-minecraft-mods/094cca22-3c6a-51d9-857c-62a8ef26976c/scratchpad/clientaudit/
(sounds.py, lang.py, lang2.py, unused.py). Mixin targets were checked with javap against
~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.21-51.0.33_mapped_official_1.21/forge-1.21-51.0.33_mapped_official_1.21.jar.

## Features checked

| # | Feature | Result |
|---|---------|--------|
| 1 | Entity renderers: all 35 entity types in ModEntities have registerEntityRenderer | OK |
| 2 | Block entity renderers (vault door, archway, wand and broom workbench); the other BEs use MODEL or INVISIBLE render shapes on purpose | OK |
| 3 | Layer definitions: all 29 ModelLayerLocations are registered; every bakeLayer call (renderers, SortingHat and Crown armor models) uses a registered layer | OK |
| 4 | Menu screens: both menu types (wand_workbench, broom_workbench) have MenuScreens.register | OK |
| 5 | Key bindings: radial (R), grimoire (G), 9 spell slot keys, all registered in RegisterKeyMappingsEvent with IN_GAME conflict context | OK |
| 6 | Key binding names in lang (cast_spell_slot_1..9 had no lang entry) | FIXED |
| 7 | ClientSideBridge to ClientRuntimeBridge: all 27 handlePacket actions sent by server code have a case, every case has a sender (undercroftFog included); the 5 direct bridge methods exist with matching signatures | OK |
| 8 | No enum switch in client code (3 found) | FIXED |
| 9 | Screens use renderTransparentBackground (7 screens drew blur + menu backdrop over their own panel) | FIXED by another agent during this run (FlooNaming, FlooTravel, HogwartsGuide, HogwartsInvite, Relic, SpellMenu, WandWorkbench); I verified the result |
| 10 | Side safety: no net.minecraft.client / com.hogwartsmagic.client reference in common packages (only javadoc links, and the standard IClientItemExtensions / IClientMobEffectExtensions pattern inside initializeClient) | OK |
| 11 | Side safety: ModEvents.handleWandUse calls com.hogwartsmagic.event.ClientModEvents directly | OPEN (not a crash, see below) |
| 12 | All client event subscriber classes use Dist.CLIENT; mod-bus events are in the MOD subscriber and forge events in FORGE subscribers | OK |
| 13 | Mixin config: every listed class exists, every target method and descriptor matches the 1.21 jar (Gui.render, ClientPacketListener sound handlers, EntityRenderDispatcher.shouldRender, HumanoidArmorLayer.render/setPartVisibility, LocalPlayer.sendPosition redirect, Entity.turn, plus all common mixins and the new NoiseGeneratorSettingsMixin.surfaceRule) | OK |
| 14 | LevicorpusLivingEntityRendererMixin is not listed | OK (deliberate, rulings.md) |
| 15 | Sounds: 197 SoundEvents registered, all 197 in sounds.json, no extra entries, all subtitles have lang keys | OK |
| 16 | spell.fizzle pointed at spells/default while spells/frizzle.ogg sat unused | FIXED |
| 17 | Whomping Willow: 8 sounds.json entries point at .ogg files that do not exist | OPEN |
| 18 | Lang: every literal Component.translatable key and every resolvable concatenated key family (spell names/desc/rank texts, mastery ranks, tome tiers, relic slots, grimoire sources, lock hints, Colloportus, mind/possession lines, possession end reasons) exists | OK |
| 19 | Entity display names: spell_beam, voldemort_summoning, flying_mob had no entity.* key | FIXED |
| 20 | Textures referenced from Java exist | OPEN (one: flying_mob.png) |
| 21 | Undercroft fog client state never cleared when leaving a world | FIXED |
| 22 | HUD overlays (mana, broom HUD via the chat overlay event; mind control and basilisk gaze via Gui.render mixins) respect hideGui | OK |
| 23 | Client sound instances (broom flight, cart) stop when the player leaves the vehicle | OK |
| 24 | Pose stack push/pop balance in render events (Levicorpus flip, Petrificus topple, broom tricks, Polyjuice surrogate) | OK |

Totals: OK 18, FIXED 6 (plus 1 fixed by another agent and verified), OPEN 3.

## Fixes

1. client/ClientRuntimeBridge.java:126 (showHouseReveal)
   - Defect: `switch (packet.house())` on the HogwartsHouse enum in client code. Project rule: the synthetic switch-map class
     fails to load in this env. Player hits it when the Sorting Hat sorts them (house reveal totem animation).
   - Change: if-chain on HogwartsHouse, same four outcomes.
2. client/InvisibilityCloakClientState.java:116 (applyToHumanoidArmorSlot)
   - Defect: enum switch on EquipmentSlot, called from the armor-layer mixin every frame a cloaked player with armor is drawn.
   - Change: if-chain, same behaviour (other slots do nothing, as the old default did).
3. client/SpellBeamRenderer.java:71
   - Defect: enum switch on SpellType in the beam renderer, runs for every visible spell beam (FINAL-REPORT listed it as pre-existing).
   - Change: if-chain with identical branches and numbers.
4. lang/en_us.json: added key.hogwartsmagic.cast_spell_slot_1 .. _9 ("Cast Spell Slot N")
   - Defect: the 9 slot key bindings showed raw keys in Options > Controls (and in the radial menu label fallback).
5. lang/en_us.json: added entity.hogwartsmagic.spell_beam ("Spell"), voldemort_summoning ("Dark Summoning"), flying_mob ("Flying Mob")
   - Defect: raw keys in death messages when a beam has no owner, in /kill and /summon feedback. (carried_block and
     petrified_remains were added by another agent during this run.)
6. sounds.json "spell.fizzle": spells/default -> spells/frizzle
   - Defect: a dedicated fizzle sound (spells/frizzle.ogg, misspelled file name) was shipped but never referenced; a failed
     cast (SpellBeamEntity fizzle paths) played the generic cast sound. Every other spell.X maps to spells/X; fizzle was the
     only event without its own file and frizzle the only file without an event. If the generic sound was intended, revert this one line.
7. event/ClientModEvents.java:170
   - Defect: UndercroftFogClientState.clear() existed but was never called. After leaving a world while inside a Gringotts
     undercroft, the next world (single player or another server) could show the undercroft fog at the same coordinates
     until the server's first-sight "no fog" packet (up to 1 s).
   - Change: clear it in the existing "no level / no player" branch next to CaveInimicumClientState.clear().

Fixed by another agent while I worked (verified, not my edits): FlooNamingScreen, FlooTravelScreen, HogwartsGuideScreen,
HogwartsInviteScreen, RelicScreen, SpellMenuScreen now call renderTransparentBackground and no-op renderBackground;
WandWorkbenchScreen dropped its extra renderBackground call. Before, Screen.render() re-ran the blur and in-world menu
backdrop after the panel was drawn, so the panel and title were blurred and dimmed under crisp buttons.

## Open items

1. Whomping Willow sounds missing (8 files): sounds/whomping_willow/{ambient,alert,whip,impact,groan,roots,hurt,death}.ogg.
   The spec says they come from build_scripts/generate_whomping_willow_sounds.py (ElevenLabs), which was never run or its
   output never committed. No crash: the game logs "Unable to open file" and the willow is silent. I did not invent audio.
   Possible stopgap if wanted (not clearly correct, so not done): "type": "event" entries pointing at vanilla
   minecraft:block.wood.break / entity.player.attack.sweep / block.wood.hit. Size: 8 generated files, or 8 small json edits.
2. textures/entity/flying_mob.png missing. FlyingMobEntity has a renderer, model and attributes but no texture, spawn egg,
   natural spawn or docs; it is only reachable with /summon and renders with the missing-texture checker. Looks like a
   leftover test entity. Fix: add a texture or unregister the entity (owner's call). Size: small.
3. ModEvents.handleWandUse (event/ModEvents.java ~3924-3990) calls com.hogwartsmagic.event.ClientModEvents directly
   (setProtegoMaximaChannelActive, startFiendfyreHold, startWingardiumHold, startImperioHold, isLockHoldPending,
   isCrucioHoldPending, startCrucioHold). Breaks the "common code reaches client only through ClientSideBridge" rule, but
   it is not a crash: the calls sit behind player.level().isClientSide(), and invokestatic with void/boolean results does
   not load ClientModEvents on a dedicated server. FINAL-REPORT already lists it as pre-existing. Fix: add 7 bridge methods
   (two returning boolean, so invoke() needs a result variant). Size: about 40 lines across ClientSideBridge,
   ClientRuntimeBridge and ModEvents. Left alone because ModEvents is shared and the code works.

## Opinions / minor notes (no change made)

- client/SpellHUD.java is dead code (render() is never called; ManaOverlay draws the HUD).
- SortingHatItem and MerchieftainessCrownItem bake a new armor model in every getHumanoidArmorModel call (every frame for
  each wearer). Works, but caching the baked model would save allocations. Owned by the item area.
- PolyjuiceClientState ACTIVE_DISGUISES / SURROGATES and InvisibilityCloakClientState transitions are not cleared on
  logout. The server resyncs your own disguise on login, so the only effect is a few stale entries and old surrogate
  entities kept in memory until the next sync.
- About 3 lang keys look unused: gui.hogwartsmagic.relics_button, gui.hogwartsmagic.relics_button_label,
  gringotts.alarm.trespass (the Relic screen is opened from a SpellMenuScreen button with another label).
- event/FlyingMobModel.java declares package com.hogwartsmagic.client.model while living in the event folder, and
  entity/StudentModel.java is a client model in the entity package. Both are only referenced from client code, so they are safe.

## Doc mismatches

- FINAL-REPORT.md lists "an enum switch in SpellBeamRenderer" as pre-existing: now fixed (code side changed).
- Whomping Willow spec promises 8 generated sounds in sounds/whomping_willow/: the files are absent (spec is right, assets missing).

## Build

- compile.sh: BUILD SUCCESSFUL (after all edits, twice). Tests not run (no Rules/Math classes touched).
- JSON validated: lang/en_us.json (no duplicate keys), sounds.json.

## Files I changed

- src/main/java/com/hogwartsmagic/client/ClientRuntimeBridge.java
- src/main/java/com/hogwartsmagic/client/InvisibilityCloakClientState.java
- src/main/java/com/hogwartsmagic/client/SpellBeamRenderer.java
- src/main/java/com/hogwartsmagic/event/ClientModEvents.java (one line plus comment)
- src/main/resources/assets/hogwartsmagic/lang/en_us.json (12 keys added)
- src/main/resources/assets/hogwartsmagic/sounds.json (spell.fizzle file)
