# Audit report: creatures-b

Area: Giant, Goblin + Goblin Guard (+ GoblinAnimated, GoblinEvents), Grindylow, Merperson (+ MerfolkEvents,
spear, crown), House Elf, Hungarian Horntail (+ /summonhorntail), Ukrainian Ironbelly (+ IronbellyEvents),
Mountain Troll, Minator (Centaur), Golden Snitch (+ item), Gringotts Cart (entity), Mandrake (block + pull
events), Clankers. Matching models and renderers.

Resource sweep (script): every mob has its entity type, attributes, renderer, layer definition, entity
lang key and texture (eyes layers included). Every spawn egg has an item model and a lang key. All
ModSounds for these mobs are in sounds.json with .ogg files and subtitles. Every translatable key used
in these files exists. Loot tables (troll, grindylow, minator, merperson, merfolk_hoard, mandrake_plant)
are valid json and name only real items. Item models (spear, crown, snitch, clankers, mandrake root,
silver dagger, mandrake plant) and the mandrake blockstate/models point at real textures. Biome
modifiers, biome spawner lists, has_troll / has_goblin / has_mandrake tags and the merfolk_guarded
structure tag all resolve. No entity/event/item file in the area touches client classes (the crown's
initializeClient follows the accepted Sorting Hat pattern).

## Features checked

- Mountain Troll (spawn, AI states, slam/sweep, rage, stagger, loot, sounds, model): OK
- Minator / Centaur (bow + hoof melee, drops, sounds): OK
- Giant: FIXED (kick ignored Cave Inimicum; grab could drag the victim into blocks)
- Hungarian Horntail: FIXED (fire ignored Cave Inimicum; corpse kept fighting / hovering)
- Ukrainian Ironbelly: FIXED (cowering did nothing; could target creative players; freed dragon saved mid-climb reloaded loose)
- Clankers: FIXED (offhand use damaged the wrong slot)
- IronbellyEvents (hearing, anchor stubs, freeing): OK
- Gringotts Goblin (trades, alert guards, spawning, animations): OK
- Goblin Vault Guard (neutral anger, sentries, dagger): OK
- GoblinEvents: FIXED (sneak-placing a block against a bank chest raised the alarm)
- House Elf: FIXED (Apparate-to-master teleport could land inside a wall)
- Merperson: FIXED (chieftainess fully healed on every reload; beaching grace was 15 s, not the designed 5 min)
- MerfolkEvents (hoard alarm, crown conduit power), Merperson's Spear, Merchieftainess's Crown: OK
- Grindylow: FIXED (an awake grindylow never swam at its target; per-tick velocity packets to a held player)
- Golden Snitch: FIXED (release-and-catch XP exploit; could be released inside a wall)
- Gringotts Cart: FIXED (documented ride back up did not exist; a rider at the bottom was stranded)
- Mandrake: FIXED (pull left the plant stuck in the pulling pose on logout or dimension change; adventure-mode players could harvest by right-click)
- /summonhorntail: OK
- Wiki: FIXED (no section existed for any of these mobs, the mandrake, spear, crown, clankers or snitch item)

## Fixes

1. entity/GrindylowEntity.java:120 and new ChaseGoal (~line 455). Defect: the goal selector had only
   RandomSwimmingGoal, so a grindylow that woke (or spawned awake) picked a target but nothing ever moved
   it there. It only seized someone who swam into it. Player hits it: the nest wakes on the hoard alarm
   and just drifts. Change: a small goal that paths to the target while it is awake, in water, not holding.
2. entity/GrindylowEntity.java tickHold (~line 305). Defect: hurtMarked every tick on a held player sent a
   velocity packet every tick per grindylow (binding rule). Change: players get the packet every 4 ticks
   (the cadence the earlier rulings use); mobs unchanged.
3. entity/GringottsCartEntity.java (DATA_REVERSE, releaseSeen, tick launch block, advance, applyOrientation,
   save/load). Defect: the cart stopped in STATE_ARRIVED forever. The wiki and GeminoEvents both promise
   "ride back through the Downfall", and a thief at the bottom (96-180 blocks down) had no way back; the
   next player found no cart at the station. Change: at the bottom, let go of W and press it again and the
   cart runs the same line in reverse (same physics, gravity sign follows travel), facing the way it
   travels, and stops at the station in STATE_IDLE ready for the next ride. A release latch stops a rider
   who holds W to the bottom being fired straight back up. A cart saved at the bottom reloads as ARRIVED,
   and a mid-return save resumes upward. Note: the Gringotts agent added washRidersInDownfall to the same
   file at the same time; it works in both directions.
4. entity/GoldenSnitchEntity.java tryCatch. Defect: released while on a broom, the snitch spawns 1.5 blocks
   ahead and is caught on the next tick: 50 XP per click, forever (the item comes back). Change: it cannot be
   caught in the first 60 ticks after release, so a catch needs a chase.
   item/GoldenSnitchItem.java:41. Defect: released facing a wall it started inside the block. Change: falls
   back to the eye position when the spot is not free.
5. entity/MerpersonEntity.java readAdditionalSaveData. Defect: setChieftainess(true) sets health to max, so a
   wounded chieftainess healed fully on every chunk reload. Change: keep the loaded health.
   entity/MerpersonEntity.java:107. Defect: AIR_GRACE_TICKS = 300 (15 seconds) while its own comment and the
   spec say five minutes of grace. Change: 20 * 60 * 5. (Units error, not a tuning change; flag if the
   controller prefers 15 s and the comment/spec changed instead.)
6. entity/GiantEntity.java:485. Defect: the kick broke blocks inside Cave Inimicum wards (only mobGriefing was
   checked). Change: TerrainRules.canBreak per block, as the last step before the break.
   entity/GiantEntity.java:573. Defect: the grab moved/teleported the victim to the fist each tick with no
   collision check, so a forest canopy or a ceiling buried them (suffocation). Change: skip the move for a
   frame whose spot is not collision-free.
7. entity/HungarianHorntailEntity.java:769. Defect: fire breath set blocks alight inside Cave Inimicum wards.
   Change: TerrainRules.canPlace on the fire block.
   entity/HungarianHorntailEntity.java:307. Defect: the state machine kept running on the corpse (a dive or
   breath could still hurt players during the death animation) and a dragon killed in the air hung there.
   Change: while dying, drop NoGravity/flying, reset the attack state, and skip the state machine.
8. entity/UkrainianIronbellyEntity.java IronbellyGuardGoal.canUse. Defect: canUse returned !isCowering, so the
   "backs toward its anchor" branch in tick() never ran; Clankers just froze it. Change: canUse returns true
   (the combat goal still outranks it). Same goal: the noise-target pick could select a creative or
   spectator player; now filtered. readAdditionalSaveData: a freed dragon saved before it reached the shaft
   reloaded unchained and roaming; it now resumes leaving.
9. entity/HouseElfEntity.java FollowMasterGoal (~line 795). Defect: the "Apparate to master" teleport used a
   random 1-block offset with no collision check, so in a tunnel the elf landed in the wall and suffocated.
   Change: try the offset, then the master's own spot, else keep walking.
10. item/ClankersItem.java:83. Defect: durability always charged EquipmentSlot.MAINHAND, so ringing from the
    offhand broke with the wrong slot. Change: LivingEntity.getSlotForHand(hand).
11. event/MandrakePullEvents.java. Defect: ACTIVE_PULLS was never cleaned on logout or dimension change, so the
    plant stayed in its PULLING pose for good and the entry leaked. Change: PlayerLoggedOut and
    PlayerChangedDimension handlers restore the plant and clear the crack overlay. Also: right-click pulling
    harvested the plant without the build check the break path has, so adventure-mode players could take
    mandrakes; now needs player.mayBuild().
12. event/GoblinEvents.java:43. Defect: sneaking with a block in hand against a bank chest (placing, not
    opening) raised the Gringotts alarm. Change: the same guard MerfolkEvents already uses.

## Open items (not fixed)

- No drops at all for the Giant, Gringotts Goblin, House Elf, Hungarian Horntail and Ukrainian Ironbelly (no
  loot table, nothing in onLivingDrops). The guard drops its dagger only via equipment drop chance. No doc
  promises drops, so this is a design gap, not a defect. A 320-health boss with nothing to show is worth a
  decision (for example Dragon Heartstring Core off the Horntail). Size: one loot table json each.
- Goblin teller offers never restock (AbstractVillager does not restock), so a worldgen teller is used up
  after 24 gold trades. Opinion; size ~15 lines (restock on a timer) if wanted.
- Golden Snitch: even with the 3 s grace, catch-and-release still pays 50 XP per chase on a fast broom.
  If that is too generous, scale XP by chase time. Opinion.
- Giant targets every other Mob, plus Death Eaters and students, whether or not he is angry. The giant spec
  says "while angry"; the code comments say it is deliberate ("he is no friendlier to the local wildlife").
  Left as is; controller to rule.
- Minator re-equips a bow in onAddedToWorld whenever its hand is empty, so a mob disarmed by Expelliarmus gets
  a new bow after a chunk reload. Minor; the Centaur is also listed under the other creatures agent.
- Troll uses a switch on its AttackState enum inside entity tick (server side only). Not covered by the
  client/render rule; left alone.

## Doc mismatches

- MOD_FEATURES_WIKI.md had no entry for any mob in this area except the Ironbelly (inside the Gringotts
  section). Added short sections: Mountain Troll, Giant, Hungarian Horntail, Ukrainian Ironbelly, Gringotts
  Goblin, Goblin Vault Guard, House Elf, Merperson, Grindylow, Golden Snitch (mobs); Mandrake Plant (blocks);
  Merperson's Spear, Merchieftainess's Crown, Clankers, Golden Snitch item (items); spawn modifier lines; and
  a sentence on the cart's return ride in the Gringotts section. The Centaur section was left to the other
  agent.
- Giant spec: rage speed "x1.35"; code uses navigation speed 1.25 when raging. Code right (balance).
- House elf spec: crouch-safe distance 2.5 blocks; code uses 1.5 with a comment explaining why (it must be
  inside entity reach). Code right; wiki written to the code.
- Merperson spec and code comment: five minutes of beaching grace; constant was 15 s. Code fixed to the spec.
- Wiki/GeminoEvents: "ride back through the Downfall"; the cart could not. Code built to the doc.

## Files changed

- src/main/java/com/hogwartsmagic/entity/GrindylowEntity.java
- src/main/java/com/hogwartsmagic/entity/GringottsCartEntity.java (shared with the Gringotts agent)
- src/main/java/com/hogwartsmagic/entity/GoldenSnitchEntity.java
- src/main/java/com/hogwartsmagic/item/GoldenSnitchItem.java
- src/main/java/com/hogwartsmagic/entity/MerpersonEntity.java
- src/main/java/com/hogwartsmagic/entity/GiantEntity.java
- src/main/java/com/hogwartsmagic/entity/HungarianHorntailEntity.java
- src/main/java/com/hogwartsmagic/entity/UkrainianIronbellyEntity.java
- src/main/java/com/hogwartsmagic/entity/HouseElfEntity.java
- src/main/java/com/hogwartsmagic/item/ClankersItem.java
- src/main/java/com/hogwartsmagic/event/MandrakePullEvents.java
- src/main/java/com/hogwartsmagic/event/GoblinEvents.java (shared with the Gringotts agent)
- MOD_FEATURES_WIKI.md

## Build

compile.sh: BUILD SUCCESSFUL (after all edits). No tests run: no pure Rules/Math class in this area changed.
