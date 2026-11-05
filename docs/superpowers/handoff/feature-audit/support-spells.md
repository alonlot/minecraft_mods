# Audit report: support-spells (support, movement and summoning spells)

## Features checked

| Feature | Result |
|---|---|
| Protego (ranked shield, parry, reflect, refund, Master melee knockback) | OK |
| Protego Maxima (sky channel, bubble health, upkeep, ward mode) | OK |
| Lumos (toggle, follow light, Expert orbs, Solem, LumosLightBlock self-removal) | OK |
| Accio (reel, too-heavy, items, containers, Master snatch, held-target message) | OK |
| Heal / Episkey (heal, bleeding and cursed-wound cures, regen) | OK |
| Ascendio (self launch, double cast, water surge, take-off shove, fall immunity) | OK |
| Depulso (knockback, slam, area, deflect, mob-into-mob) | OK |
| Reparo (block repair) | FIXED (duplication, stale records) |
| Reparo (Expert item repair, cloak excluded) | OK |
| Reparo / Alatus beam on a creature | FIXED (no-op that paid hit XP) |
| Apparition (channel, map, splinch, side-along, dimension cancel, Master Floo jump) | OK |
| Revelio (non-lock: ores, chests, invisibility, Master disguise/cloak reveal) | OK |
| Wingardium Leviosa (hold, block lift, CarriedBlockEntity, throw, Master pair) | OK (one opinion, see Open) |
| Arresto Momentum (fall softening, Expert freeze, save/restore, no beams) | OK |
| Tenebris Flight (flight, pass-through, Master ram) | OK |
| Alatus (bench infusion, quality, Master re-infusion) | OK |
| Expecto Patronum: shield cone channel | OK |
| Expecto Patronum: corporeal stag + PatronusLightBlock | FIXED (leaked, unbuildable light blocks; chunk loads) |
| Fiendfyre (FiendfyreEntity, FiendfyreSerpentEntity) | FIXED (claims/wards ignored, PvP bypass) |
| Serpensortia (allied cave spiders) | FIXED (spiders attacked their own caster, never the target) |
| Piertotum Locomotor (StatueMultiblock, LivingStatueEntity, StatueHeartBlock, LivingStatueEvents) | FIXED (claims/spawn protection ignored, PvP bypass) |
| Gemino | Not a spell (Gringotts hoard curse in GeminoEvents): not audited here |
| Entity names (carried block, Fiendfyre, Fiendfyre serpent) | FIXED (missing lang keys) |
| HANDOFF.md 5b folded fixes | All 4 verified in code, plus the 3 extra wave items |

### HANDOFF.md folded 5b fixes, verified
1. Invisibility Cloak excluded from Reparo item repair: `ReparoSpell.mendable` (spell/support/ReparoSpell.java). Present.
2. Accio no longer streams velocity to players and skips held targets: `ModEvents.startAccioPull` gives a player one impulse; `isHeldInPlace` (Levicorpus hang, Incarcerous chains) refuses and `tickAccioPull` drops held targets. Present.
3. Apparition map: `MAX_TILES = 150` cap and `COLOR_OUT_OF_RANGE` grey-out beyond the range circle, plus list room (client/ApparitionMapScreen.java). Present.
4. Apparition channel cancelled on `PlayerChangedDimensionEvent` (ModEvents.onPlayerChangedDimension). Present.
Extra wave items also present: side-along skips hung/chained victims, Ascendio water-cast fall immunity (`fromGround || isInWater`), protocol bump. Phase 6a Task 0 carry-overs also present: Accio snatch in victim i-frames (`attackVetoed`), Depulso probe at min(0.5, half height), decoder drains extra fireplace entries, "could not fit" side-along message, Depulso rank cached on the beam, Accio held-target message.

## Fixes

1. **Patronus light blocks leaked and blocked building.** `block/PatronusLightBlock.java` (whole class), `ExampleMod.java:491`.
   Defect: the stag's cleanup lives in `PatronusEntity.remove()`, but a chunk unload or server stop calls `setRemoved()` directly, so its follow light and pulse-ring lights stayed in the world forever. The shield channel's lights were stranded the same way on a dimension change. The block was also not `replaceable`, so a stray light (invisible, no shape) stopped players placing a block in that spot.
   Player hit: summon a stag, walk away or quit; a permanent light-15 spot remains that you cannot build into.
   Change: the block schedules a tick on placement (saved with the chunk) and every 40 ticks removes itself unless a living PatronusEntity is within 32 blocks or a player holding the Patronus shield is within 22 blocks. Added `.replaceable()` to its properties.

2. **Patronus pulse ring could load chunks.** `entity/PatronusEntity.java:193` (plus the two cleanup loops).
   Defect: the ring reaches 30 blocks and read block states with no `isLoaded` check (binding rule: never load chunks).
   Change: `isLoaded` guard before every ring read and removal.

3. **Fiendfyre ignored claims, wards, spawn protection and mobGriefing.** `entity/FiendfyreSerpentEntity.java:216-224`, `entity/FiendfyreEntity.java:87-93`.
   Defect: both entities set fire blocks with only `isEmptyBlock`; Incendio routes every fire through `TerrainRules.canPlace`.
   Player hit: channel Fiendfyre inside another player's claim or a Cave Inimicum ward and it burns.
   Change: `isLoaded` plus `TerrainRules.canPlace` (the serpent as its caster; the beam-spawned controller with no caster, so the mobGriefing rule).

4. **Fiendfyre serpent burned players with PvP off.** `entity/FiendfyreSerpentEntity.java:155-158`.
   Defect: `onFire()` damage names no attacker, so PvP-off and team rules never applied.
   Change: the scorch skips players `MindControl.puppeteerSpares(caster)` spares (same check CarriedBlockEntity and Tenebris use).

5. **Serpensortia spiders turned on their caster and ignored the target.** `spell/SpellType.java:80, 296-306`, `event/ModEvents.java:3860-3866`.
   Defect: the spell says "allied spiders to attack your enemies", but the cave spiders got `setTarget(null)` and an ALLIED tag that only stops them targeting other ALLIED mobs; at night they hunted the caster.
   Change: each spider stores its summoner's UUID; `onTargetChange` cancels the summoner as a target; the struck creature becomes the first target (not a player the caster may not harm).

6. **Piertotum took six blocks with no protection checks.** `util/StatueMultiblock.java:104-116`.
   Defect: the rite removed the heart and five body blocks without spawn protection, claim or ward checks.
   Player hit: raise (steal) a statue built inside someone else's claim or spawn.
   Change: after the shape matches, `mayInteract` on every body block, then `TerrainRules.canBreak` on the heart as the last step (the real BreakEvent claim mods veto), before anything is removed.

7. **Bound Living Statue cut down protected players.** `entity/LivingStatueEntity.java:510-519`.
   Defect: a player's sentry sweeps every living thing in its arc; its blows name the statue, so PvP-off was bypassed (vanilla wolves check the owner's canHarmPlayer).
   Change: a bound statue's `canStrike` spares players its owner may not harm (also every player while the owner is offline).

8. **Reparo duplicated items.** `util/ReparoManager.java:388-424`.
   Defect: Reparo charges one item per block, so a double slab, 4 candles, sea pickles, turtle eggs, pink petals or multi-layer snow came back whole for one item. Break, repair, break again: +1 or more items each cycle. Door and tall-plant halves were "repaired" alone.
   Change: such states are not recorded as repairable.

9. **Reparo records crossed worlds.** `util/ReparoManager.java:426-444`.
   Defect: the static record and flight maps were never cleared; records are game-time stamped per world, so switching single-player worlds could replay another world's broken blocks.
   Change: `clearAll()` on `ServerStoppedEvent`.

10. **Reparo and Alatus beams on a creature paid hit XP for nothing.** `entity/SpellBeamEntity.java:884-887`.
   Defect: both are block-only (empty `applyHitEffect` cases) but a creature hit counted as a success: impact burst, 0.1 chip damage, and living-hit mastery XP, so shooting zombies levelled Reparo.
   Change: they fizzle on a living target, like Morsmordre, Piertotum and Colloportus.

11. **Missing entity names.** `lang/en_us.json:107-109`: added `entity.hogwartsmagic.carried_block` ("Levitated Block", shown in "pelted by" death messages when the thrower is gone), `entity.hogwartsmagic.fiendfyre`, `entity.hogwartsmagic.fiendfyre_serpent`. JSON validated.

## Open items (not fixed)

- **Accio reel and Wingardium hold work on PvP-protected players.** Only the snatch (Accio) and the throw (Wingardium) check PvP; a protected player can still be yanked or carried to a cliff and dropped. Docs only scope the snatch/throw, so carrying friends may be intended. Opinion: gate both on `puppeteerSpares` (about 10 lines). Not changed: design call.
- **Wingardium sends a motion packet to a held player every tick** (`ModEvents.controlWingardiumTarget`). Conflicts with the "no per-tick velocity packets" rule, but the hold cannot work otherwise (player motion is client-side). Pre-existing, reviewed in 7b; left as is.
- **A bound Living Statue never walks back to its post after a fight** (only after being "put down"). The lang says it "holds the post". A return goal (restrictTo + MoveTowardsRestrictionGoal, saved home radius) is about 20 lines. Not built: design detail, unsure of intent.
- **Reparo flights lost on a server stop mid-flight** (16-tick window): the payment item floats unpickable for 5 minutes and the block is not restored. Rare; about 15 lines to restore on stop.
- **Patronus lights already leaked in existing worlds** have no scheduled tick and `.air()` blocks never random-tick, so they stay lit; they are now replaceable, so building over them works.
- **Patronus stag**: `hurt` always returns false, so `/kill` cannot remove it (the 30 s lifespan does); lifespan is not saved, so a reloaded stag gets a fresh 30 s. Minor, pre-existing.
- **Fiendfyre serpent still burns pets, villagers and allied mobs** (only players are spared now). Cursed fire burning everything may be intended; the 7a rework was skipped on purpose.
- **Ascendio beam on a target**: nearby players are given velocity without `hurtMarked`, so they never actually move (harmless; adding it would create a PvP bypass, so left alone).

## Doc mismatches

- FINAL-REPORT 5b checklist item 14 says a Master Apparition focus takes 1 s. Code: 4 s at every rank (`ApparitionRules.CHANNEL`, deliberate commit 0c89a08 "Keep the Apparition focus time at 4 seconds at every rank"); the lang text says the same. Code is right; the checklist is stale.
- `LivingStatueEvents` javadoc says a bound statue "follows them about". Code and lang: it holds its post. Code is right; comment stale.
- MOD_FEATURES_WIKI.md is pre-mastery for most of this area (code is right in each case): Accio (no items, containers or snatch), Wingardium ("applies levitation"; really a held channel, block lifting and throws), Protego (no parry/reflect), Lumos (no Expert orbs or Solem), Depulso (no slam/deflect), Ascendio (says fall punishment is prevented; a target hit gets none, only a self launch does), Arresto (no Expert freeze), Tenebris (no pass-through or ram), Reparo (no item repair). There is no wiki section for Apparition or Piertotum Locomotor. Serpensortia ("cave spiders") is accurate.

## Build

- Compile: `compile.sh` BUILD SUCCESSFUL (after all fixes).
- Tests: `com.hogwartsmagic.spell.support.*` BUILD SUCCESSFUL; `com.hogwartsmagic.spell.motion.*` BUILD SUCCESSFUL (15 tests, 0 failures). No Rules classes were changed, so no tests were added.

## Files changed
- src/main/java/com/hogwartsmagic/block/PatronusLightBlock.java
- src/main/java/com/hogwartsmagic/ExampleMod.java (one line: `.replaceable()` on PATRONUS_LIGHT_BLOCK)
- src/main/java/com/hogwartsmagic/entity/PatronusEntity.java
- src/main/java/com/hogwartsmagic/entity/FiendfyreEntity.java
- src/main/java/com/hogwartsmagic/entity/FiendfyreSerpentEntity.java
- src/main/java/com/hogwartsmagic/spell/SpellType.java (SERPENSORTIA case and one constant)
- src/main/java/com/hogwartsmagic/event/ModEvents.java (onTargetChange, one branch)
- src/main/java/com/hogwartsmagic/util/ReparoManager.java
- src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java (one condition)
- src/main/java/com/hogwartsmagic/util/StatueMultiblock.java
- src/main/java/com/hogwartsmagic/entity/LivingStatueEntity.java
- src/main/resources/assets/hogwartsmagic/lang/en_us.json (three keys)
