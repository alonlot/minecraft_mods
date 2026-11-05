# Audit report: mind-spells (mind and dark spells, possession, Morsmordre, Dark Mark, Horcrux)

## Features checked

- Avada Kedavra landing (kill, boss fifth, backlash, per-target Protego bypass): OK
- Avada windup (begin, sparks, 4+ hit / stun / silence interrupt, lockout, logout/login cleanup): OK
- Crucio channel (stun, damage ticks, rooted caster, heartbeat, range, Master flee, death/logout/dimension ends): OK
- Imperio I-III thralls (cap, lockouts, follow/assist/brawl goals, player thrall push throttle, labels, release paths, cooldown on last thrall): OK
- Imperio IV possession (start from hold, server hold check, input relay, resist mashing, hotbar lock, puppet casts and puppeteer context, end reasons, markers guard, logout/login/join sweeps, server stop): OK
- ForcedPlayerMovement, PossessedAttacks, PossessionDriver, PossessionSession, ThrallGoals, RateWindow: OK (read, no defect found)
- Obliviate (forget map, Warden roar watch, Master memory charm, raid flag): OK
- Confundo (retarget, throttle, Master contagion, thralls resist): OK
- MindControl / MindHooks / MindFx (controlled-player refusals, brain helpers, explosion spare): OK
- /imperio possess and /imperio release (registered, op level 2, lang keys present): OK
- Mixins: ServerGamePacketListenerMixin, PossessionSendPositionMixin, PossessionTurnMixin, MindControlGuiMixin (all listed in hogwartsmagic.mixins.json on the right side): OK
- Mind lang keys (every gui.hogwartsmagic.* key the mind code uses, all possession.end.* keys, commands.hogwartsmagic.imperio.*): OK
- Common code never touches client classes (spell/mind, command, Horcrux, Dark Mark files): OK
- Morsmordre beam (sky raid after 200 ticks high in open sky, Ward block starts the Voldemort summoning): FIXED (chunk loading in raider spawn)
- Dark Mark entity (storm, lightning, wizard buffs, monster waves, renderer registered): FIXED (monster spawn position, chunk loading, missing lang)
- Horcrux ritual (Avada kill over the altar primes it, binding costs 2 max health): FIXED (per-tick chunk loading, per-tick attribute packets)
- Horcrux block (status message, particles, drops, hostile break summons a Dementor): FIXED (claim bypass, missing loot table, drop rule never applied)
- Horcrux death and rebirth (inventory kept, soul flies to the vessel, respawn at the vessel): FIXED (death handler priority and cancel guard)
- Horcrux Soul entity and client death sequence (respawn button lock with 140-tick fallback): OK

Counts: OK 16, FIXED 5 feature lines (8 separate fixes), OPEN 0 defects (a few opinions and doc mismatches below).

## Fixes

1. `util/HorcruxData.java:260-268` (reconcileMissingHorcrux)
   - Defect: runs every player tick for anyone with a Horcrux and called `level.getBlockEntity(pos)` on the bound
     vessel, which loads the chunk. A bound player kept the vessel's chunk loaded from any distance and any
     dimension, forever (breaks the "never load chunks" rule).
   - How a player hits it: bind a Horcrux, walk 1000 blocks away or go to the Nether; the vessel chunk stays loaded.
   - Change: skip the check while the vessel's chunk is not loaded (`isLoaded`); it runs again when the chunk loads.
     Without this guard a non-loading lookup would have wrongly cleared the binding, so the skip is required.

2. `util/HorcruxData.java:110-128` (applyMaxHealthPenalty)
   - Defect: also every player tick, it removed and re-added the max-health modifier. Each change marks
     MAX_HEALTH dirty, so the server sent an attribute update packet every tick to the player and everyone
     tracking them, for as long as they had a Horcrux.
   - Change: only touch the attribute when the modifier is missing, has a different amount, or must go.

3. `event/ModEvents.java:2866-2880` (new `onHorcruxBreak`, moved out of `onBlockBreak`)
   - Defect: the bound-Horcrux break handling ran at NORMAL priority with no cancel check. A claim mod or spawn
     protection that cancels the break later still had the vessel disarmed (owner loses the Horcrux and the
     binding is cleared next tick) and a Dementor summoned on the breaker.
   - How a player hits it: try to break someone's active Horcrux inside their claim; the break is refused but the
     Horcrux is dead.
   - Change: the Horcrux part is now its own LOWEST-priority BreakEvent listener with an `isCanceled()` guard. The
     rest of `onBlockBreak` (Reparo tracking etc.) is unchanged.

4. `data/hogwartsmagic/loot_table/blocks/horcrux.json` (new file)
   - Defect: the Horcrux block had no loot table, so `super.getDrops` returned nothing. A crafted (unbound)
     Horcrux broken with a diamond pickaxe vanished.
   - Change: standard self-drop loot table with `survives_explosion` (the block already needs a diamond tool via the tags).

5. `block/HorcruxBlock.java:97-99` and `block/entity/HorcruxBlockEntity.java:156-163`
   - Defect: `getDrops` refuses to drop a primed or active vessel, but the break handler clears active/primed
     before vanilla asks for drops, so that rule could never apply to a player break (dead branch). With the
     loot table in place it would have dropped a fresh vessel from a destroyed bound one.
   - Change: new `wasDestroyedWhileBound()` getter (true once handleForcedDestruction or shatter ran) and
     getDrops checks it too. Explosion and Wither paths already saw the bound state and are unchanged.

6. `event/ModEvents.java:2726-2752` (new `onHorcruxOwnerDeath`, moved out of `onLivingDeath`)
   - Defect: the Horcrux death branch (store inventory, mark rebirth, spawn soul, lock respawn) ran at NORMAL
     priority with no cancel check (breaks the "death handlers at LOWEST with isCanceled guard" rule). If a
     revive mod cancelled the death, the player lived on with a stored inventory and the death-handled flag set,
     so the next real death cleared their drops and the client locked the respawn button.
   - Change: the Horcrux part is its own LOWEST listener with an `isCanceled()` guard. LivingDropsEvent still
     fires after it, so drops are cleared exactly as before. Cloak/Polyjuice lines stay in onLivingDeath.

7. `entity/DarkMarkEntity.java:288-315` (summonMonsters)
   - Defect: the spawn search started 50 blocks below the mark and scanned down while the block was air. The
     sky-raid mark sits between ground-3 and peak-31, so the start was often deep inside rock: zombies, skeletons
     and spiders were spawned inside stone (suffocating) or in caves. The scan could also read unloaded chunks
     (loads them). Monsters skipped finalizeSpawn, so skeletons had no bow.
   - Change: use the MOTION_BLOCKING_NO_LEAVES heightmap at the random column, skip the wave if that chunk is not
     loaded, centre the spawn on the block, and call finalizeSpawn (MobSpawnType.EVENT) like the raid wizards.

8. `entity/SpellBeamEntity.java:1201-1203` (tryStartMorsmordreSkyRaid)
   - Defect: raiders were placed with getHeightmapPos at random columns up to 20 blocks away, which loads the chunk
     if it is not loaded.
   - Change: `continue` past a column whose chunk is not loaded.

Also: `lang/en_us.json` gained `entity.hogwartsmagic.dark_mark` ("Dark Mark"); the registered entity had no name
(seen in /summon feedback, F3 and similar).

## Open items (not fixed)

None that are defects. Opinions and small notes:
- `HorcruxSoulEntity` flies in the dimension where the player died toward the vessel's coordinates even if the vessel
  is in another dimension; the client fallback unlocks respawn after 140 ticks and the rebirth itself teleports to
  the right dimension, so it only looks odd. Fixing it means a cross-dimension design (about 20 lines).
- If a bound vessel is destroyed while its owner is far away or offline, the owner's 2 max-health penalty now stays
  until the vessel's chunk is loaded again (before fix 1 it was cleared at once by force-loading the chunk). A
  one-off loading check at login could be added (about 10 lines) if that matters.
- `DarkMarkEntity` writes its duration to synced entity data every tick (one entity-data packet per tick per mark to
  trackers). It is not a velocity packet and the client uses it for the fade; left as is.
- `CrucioChannel.FLEEING` entries for a mob that unloads before its flee ends are only removed on death or its next
  tick; bounded and harmless.
- Horcrux block model is a full cube (cube_bottom_top) while its hit box is a small pedestal; cosmetic.

## Doc mismatches

- MOD_FEATURES_WIKI.md is pre-mastery for these spells. Code (plans phase6a/6b + rulings) is right in each case:
  - Crucio: wiki says wither, slowness, weakness. Code: a held channel that stuns and deals 1.5 per half second.
  - Imperio: wiki says confusion, damage boost, glowing, "recently hit" marker. Code: thralls with green particles
    (ruled, no glow), Strength from Adept, and Master possession.
  - Obliviate: wiki says blindness and confusion. Code: the mob forgets the caster (30s max) or a player is silenced.
  - Morsmordre: wiki says "applies blindness on hit" and "summons the Dark Mark". Code: a living hit fizzles
    (deliberate since before Phase 1, named in the phase1 plan), a Ward block hit starts the Voldemort rite, and
    the Dark Mark comes only from the sky raid. The MORSMORDRE branch in `SpellType.applyHitEffect` (mark plus
    blindness) is therefore dead code. Left alone: the fizzle is deliberate; either delete that branch or update the wiki.
- Horcrux status text says "Stand near it while it devours your heart-force", but the binding completes at the next
  20-tick check regardless of distance (within a second). Text or code, owner's choice; no change.
- FINAL-REPORT Phase 6a checklist item 20 ("Log out holding a thrall ... Imperio is cooling down") reads as the owner
  logging out; the owner's logout skipping the cooldown is a recorded deferral in rulings.md, so the item only holds
  for a thrall player logging out.

## Build and tests

- Compile: `compile.sh` BUILD SUCCESSFUL, no compiler warnings in the log.
- Tests: `test.sh 'com.hogwartsmagic.spell.mind.*'` BUILD SUCCESSFUL, 46 tests (Avada 5, Confundo 4, Crucio 5,
  Imperio 12, Obliviate 5, Possession 11, RateWindow 4), 0 failures.
- No unit test added: the fixes are in world/event code, not in the pure Rules classes.

## Files changed

- src/main/java/com/hogwartsmagic/util/HorcruxData.java
- src/main/java/com/hogwartsmagic/block/HorcruxBlock.java
- src/main/java/com/hogwartsmagic/block/entity/HorcruxBlockEntity.java
- src/main/java/com/hogwartsmagic/entity/DarkMarkEntity.java
- src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java (shared, one 3-line guard)
- src/main/java/com/hogwartsmagic/event/ModEvents.java (shared, two Horcrux blocks moved into new LOWEST listeners)
- src/main/resources/data/hogwartsmagic/loot_table/blocks/horcrux.json (new)
- src/main/resources/assets/hogwartsmagic/lang/en_us.json (shared, one key)
