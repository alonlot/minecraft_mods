# Audit report: creatures-a

Area: Wizard / Death Eater, Student, Voldemort, Voldemort summoning, Dementor, Basilisk (+ part, venom,
gaze and petrification effects, gaze GUI mixin), Acromantula, Hippogriff, Owl (+ perch, route tag, owl
post), Centaur, Lycanthrope Villager, Whomping Willow (+ sapling), Flying Mob, DisarmableWandUser +
RetrieveDroppedWandGoal, duel/DuelManager, Death Eater camp structure.

## Features checked

| Feature | Status |
|---|---|
| Registration in ModEntities, attributes, spawn placements, renderers, layer definitions (all mobs in area) | OK |
| Spawn eggs + item models + lang (wizard, student, owl, hippogriff, acromantula, dementor, basilisk, willow) | OK |
| Loot tables for every mob in the area | OK |
| Sound events, subtitles and .ogg files (all except the willow) | OK |
| Death Eater: spells, Protego charges, Tenebris Flight, broom spawn, disarm and wand retrieval | OK |
| Death Eater raid leader summons a raid | FIXED (summoned again after every chunk reload; ritual particles never showed) |
| Wand retrieval (RetrieveDroppedWandGoal) | FIXED (a picked-up player wand was destroyed on the NPC's death) |
| NPC duels (DuelManager) | FIXED (stale "dueling" flag saved into NPC NBT; Voldemort could be "dueled" and healed to full) |
| Death Eater camp structure (nbt, jigsaw, structure set, biome tag, painting variant 64x48, baked wizards) | OK |
| Hogwarts Student: sleep, combat casting, Protego / Protego Maxima, Arresto, Reparo, golem truce | OK |
| Lord Voldemort: queued spells, boss bar, storm, Elder Wand drop, advancements | OK |
| Voldemort Avada windup pose (renderer) | FIXED (pose read a server-only field, never showed) |
| Voldemort summoning ritual (three wards + Morsmordre) | OK |
| Dementor: aura, kiss, recoil, Patronus avoidance, fen spawns, insomnia spawns | OK |
| Dementor soul-suck pull | FIXED (velocity packet to the player every goal tick) |
| Basilisk: multipart hitboxes, spine trail, strike / spit / sweep / burrow, blinding, venom | OK |
| Basilisk saved while burrowed | FIXED (reloaded stuck in rock with NoGravity, suffocating) |
| Basilisk gaze on creative / spectator players | FIXED (they got the overlay and petrification) |
| Basilisk gaze heartbeat sound (spec) | FIXED (sound registered and shipped but never played) |
| BasiliskGazeGuiMixin + BasiliskGazeOverlay + mixin json entry | OK |
| Acromantula: scale, hitbox, alert / lunge / web spit, venom drop | OK |
| Acromantula cobwebs | FIXED (placed blocks ignoring mobGriefing and Cave Inimicum) |
| Hippogriff: bow, taming with rabbit, sit, riding and flight, free flight | OK |
| Hippogriff breeding by the owner | FIXED (owner's rabbit always mounted her, never bred) |
| Owl: taming, follow owner, perch routes, invite delivery | OK |
| Owl sit command | FIXED (tamed owl sat forever: nothing could ever stand it up) |
| Owl carried mail / route tag on death | FIXED (both deleted with the owl) |
| Owl Perch: queue, link, dispatch, receive | OK |
| Owl Perch broken with mail on it | FIXED (mail deleted with the block entity) |
| Owl Route Tag | OK |
| Centaur | OK (it is MinatorEntity, owned by the other creatures agent; talisman 35% drop in ModEvents.onLivingDrops works) |
| Lycanthrope Villager: forms, goals, full moon, morph animations, village replacement, drops | OK |
| Whomping Willow: states, knot, redstone freeze, ownership, persistence, spawns, willow_clearing | OK |
| Whomping Willow block smashing | FIXED (ignored Cave Inimicum wards) |
| Whomping Willow sounds | OPEN (all 8 .ogg files missing) |
| Willow Sapling: ages, bone meal, raise by hand, item model, loot, lang | OK |
| Willow Sapling block model | FIXED (no cutout render type: drawn as an opaque black square) |
| Flying Mob | OPEN (vestigial: no texture, no name, no way to obtain) |

Counts: 21 OK, 15 FIXED rows (16 separate fixes below), 2 OPEN.

## Fixes

1. `entity/WizardEntity.java:49, 246, 254, 338, 348-355, 378` - Raid leader summoned again after reload.
   `hasSummoned` lived only on the goal object. A raid leader that had already called its 5-9 Death Eaters
   summoned a fresh batch (and a new raid and Dark Mark) every time its chunk unloaded and reloaded near
   villagers. Player hits it by leaving and coming back. Change: the flag is now a `raidSummoned` field on
   the wizard, saved as `RaidSummoned`. Also the ritual's ink particles used client-only `addParticle`
   inside a goal (goals only run on the server), so they never appeared; now sent with `sendParticles`.
2. `entity/RetrieveDroppedWandGoal.java:108-111` - Any wand on the ground is picked up, including a
   player's own wand knocked out by an NPC's Expelliarmus. The goal then set the drop chance to 0, so when
   the NPC died the player's wand was gone for good. Change: `setGuaranteedDrop(MAINHAND)`, which returns
   exactly the item that was picked up (no new item is created, so no farm).
3. `duel/DuelManager.java:57, 81, 96-115, 298-305` - The "dueling" flag was written into the NPC's
   persistent NBT. If the player logged out or the server stopped mid-duel, the student stayed "dueling"
   forever (never flees again) and the per-tick panic check read persistent data every tick. Change:
   `isDueling` reads the live duel map, and entries whose challenger is offline are pruned there. The tag
   is no longer written; `endDuel` still strips it.
4. `entity/VoldemortEntity.java:105-112` - Voldemort inherited the Death Eater duel. Sneak + right-click
   with a wand started a "duel"; holding him still for 5 s and winning it (25% health) healed him to full
   and paid duel XP and a spellbook roll. Change: `mobInteract` returns PASS for Voldemort.
5. `entity/VoldemortEntity.java:57-60, 89-93, 242-247, 292, 311, 423` - `isChargingAvadaKedavra()` read
   `queuedSpell`, which only exists on the server, so WizardModel's theatrical Avada windup never played.
   Change: a synced `DATA_CHARGING_AVADA` flag set when the Avada is queued and cleared on cast/cancel.
6. `entity/DementorEntity.java:314-325` - The soul-suck pull set `hurtMarked` on every goal tick within 48
   blocks, a velocity packet to the victim each time (binding rule). Change: the pull is applied every 5th
   goal tick at 5x strength (same total pull).
7. `entity/BasiliskEntity.java:1161, 1170-1181` - Action state is not saved but NoGravity is. A basilisk
   saved while BURROWED (player logs out during the burrow) reloaded inside rock with no gravity and no
   state to bring it up, and suffocated in place. Change: save `Burrowed`; on load resume as BURROWED with
   travel time spent, so the next tick surfaces it through the normal eruption.
8. `entity/BasiliskEntity.java:855-859` - The gaze charged on creative and spectator players: overlay,
   slowness and a petrification effect on players who cannot die. Change: they are excluded from the gaze.
9. `entity/BasiliskEntity.java:885-892` - Spec: "the heartbeat rises in pitch". `basilisk.gaze_heartbeat`
   was registered with an .ogg and subtitle but never played. Change: played to the victim only, once per
   32 ticks (the clip is 1.6 s), pitch and volume rising with the tier.
10. `entity/AcromantulaEntity.java:191-196, 241-246` - Ambient cobwebs and the web spit placed blocks with
    no mobGriefing or Cave Inimicum check, so forests and bases filled with webs with mobGriefing off.
    Change: both go through `TerrainRules.canPlace` (the project's helper for mob/spell block placement).
11. `entity/WhompingWillowEntity.java:859-864` - SLAM / SMASH broke blocks inside Cave Inimicum wards (only
    the mobGriefing game rule was checked). Change: each block also passes `TerrainRules.canBreak`, as the
    Giant already does.
12. `entity/HippogriffEntity.java:446-454` - The owner's branch mounted her before any food handling, so a
    rabbit in the owner's hand never bred her or grew a foal; only a stranger could breed someone's tame
    hippogriff. Change: food from the owner goes to the vanilla breed/grow path first.
13. `entity/OwlEntity.java:198-225` - Taming orders the owl to sit and nothing ever released it (no sit
    toggle existed), so a tamed owl could never follow its owner or fly to a perch. Change: owner
    right-click toggles sit like a wolf; sneak + empty hand still removes a route tag first; seeds still
    breed; an owl in flight with mail is left alone.
14. `entity/OwlEntity.java:121-143` - An owl killed in flight deleted the letter it carried, and the route
    tag stored in its data. Change: `dropCustomDeathLoot` drops both.
15. `block/OwlPerchBlock.java:47-52` - The perch is a container but had no `onRemove`, so breaking it
    deleted the queued or delivered item. Change: `Containers.dropContentsOnDestroy`.
16. `resources/assets/hogwartsmagic/models/block/willow_sapling.json` - Cross model with no
    `render_type`, so the sapling rendered solid (black box around the leaves). Change: `"render_type":
    "cutout"` plus a particle texture, the same as gillyweed_plant.json. JSON validated.

## Open items

- Whomping Willow sounds (about 0 code lines; needs 8 audio files). `sounds.json` maps
  `whomping_willow.{ambient,alert,whip,impact,groan,roots,hurt,death}` to
  `sounds/whomping_willow/*.ogg`, and the folder does not exist. The generator
  (`build_scripts/generate_whomping_willow_sounds.py`) needs the ElevenLabs key and was never run or its
  output never committed. The willow is silent and the log warns about missing files. Not invented. A
  stop-gap through the json is possible (`"type": "event"` entries pointing at vanilla wood sounds for
  hurt/death/impact) but it is not clearly correct for whip/groan/alert, so I did not apply it.
- Flying Mob (`hogwartsmagic:flying_mob`). Registered with attributes, renderer, model and a loot table,
  but `textures/entity/flying_mob.png` is missing, there is no `entity.hogwartsmagic.flying_mob` lang key,
  no spawn egg, no natural spawn and no doc. Only `/summon` reaches it, and it renders as the missing
  texture. It looks like a leftover template. Suggest removing it (small: 4 registrations plus 4 files) or
  giving it an asset. Note also `entity/FlyingMobRenderer.java` is an empty file and the model class sits
  in `event/FlyingMobModel.java` with package `client.model`.

## Opinions (not changed)

- Owl perches have no owner checks: anyone can take mail off any perch, and anyone with a route tag can
  relink someone else's perch and become its owner, which reroutes that perch's mail to their own owls.
- Voldemort has no persistence flag, so he can despawn if every player goes more than 128 blocks away.
- Lycanthrope `!level.isDay()` is always true in the Nether and End (fixed time), so it is a werewolf
  there permanently.
- Lycanthrope wolf fang drops twice: the loot table (0-1) and a 50% extra in ModEvents.onLivingDrops.
- Lycanthrope Villager has no spawn egg.
- Perf: `OwlEntity.getRouteTag()` parses an ItemStack from NBT and is called from three goals every tick
  per owl; `WizardSpellCastGoal.tick` reads persistent data every tick while in combat.
- `BasiliskPetrificationEffect.applyEffectTick` sets `hurtMarked` every tick (3 s, only on a victim that
  survives, for example with a totem).
- Client classes in common packages: `entity/StudentModel.java` and `event/FlyingMobModel.java`. Only
  client code references them, so this is not a crash, just misplaced.
- A Death Eater raid leader that already leads a base raid can still summon a second raid if it reaches
  villagers. Possibly intended.

## Doc mismatches

- Basilisk spec: "Spawn egg only, no natural spawns". Code and data spawn it naturally in the
  `chamber_depths` biome (later feature). The data is right; the spec is out of date.
- Basilisk spec: overlay tiers 2-3 use "a stone-crackle texture". The overlay draws plain grey fills, no
  texture. Cosmetic.
- Willow spec: "knot on one compass side, chosen per tree" and "at full age ... replaces itself with a
  willow that records the planter". Code fixes the knot to branch 0 and makes the player raise a mature
  sapling by hand (both changes explained in code comments). The wiki matches the code; the spec is stale.
- Wiki "Hogwarts Student: supports flight behavior". Students never cast Tenebris Flight themselves (it is
  not in their spell list); the flight handling only runs if the effect is put on them from outside.
- Wiki "Owl: Can sit". It could not be ordered to sit or stand before this fix; now true.
- Wiki "Centaur: registered as MinatorEntity". True; that entity belongs to the other creatures agent.

## Build

Compile: `compile.sh` BUILD SUCCESSFUL (run after the last change). No tests run: none of the changed
code has pure Rules/Math classes with tests.
