# Audit report: combat-spells

Area: spell/damage, spell/disable, spell/combat, CrowdControlCommand, Expelliarmus, Aqua Eructo, the
shared effects (bleeding, cursed wound, stunned, brittle, silenced, the hidden markers), the stun and
bleeding mixins, PetrifiedRemainsEntity, and the branches for these spells in SpellBeamEntity, SpellType
and ModEvents.

Sources used: rulings.md, FINAL-REPORT.md, the Phase 2, 3, 4 and 5a plans (per-rank tables and
"Cut on purpose" lists), the spell-mastery design spec and roadmap, MOD_FEATURES_WIKI.md, en_us.json.

## Features checked

| Feature | Result |
|---|---|
| Stupefy (stun, interrupt, knockback, Master chain, Master beam speed) | OK |
| Petrificus Totalus (stun + petrified marker, break on damage, Master shatter, topple render) | FIXED (marker icon, see fix 6) |
| Incarcerous (tether, area bind, Expert flyer pull, Master bind together and shared knockback) | FIXED (chain wind-up animation, marker icon) |
| Incarcerous chain animation (intro phases, sustained chains, Master links) | FIXED (fix 5) |
| Glacius (slow, chill window, freeze solid, brittle, frosted ice, fire thaw, Master shatter) | FIXED (cold biome bonus, water-surface hit, marker icons) |
| Levicorpus (hang, hover hold, no items or shields, +25% damage, Liberacorpus, upside-down render) | FIXED (marker icon) |
| Confringo (no-terrain blast, ignite, splash ignite, Master brittle bonus, steam combo) | FIXED (impact jolt to players, fix 8) |
| Incendio (ignite, campfires/candles/TNT/furnace, flint-and-steel fire, fire burst) | OK |
| Bombarda (splash, budgeted block breaking, tiers, drops, mobGriefing/claims/wards) | FIXED (impact jolt to players, fix 8) |
| Diffindo (armour pierce, sheep shearing, block cutting, outline ray, Master cleave) | OK |
| Sectumsempra (cut, rank-capped bleed stacks, Master detonate, kill credit) | OK |
| Dolohov's Curse (cursed wound, milk-proof, Master mark and spread on death) | OK |
| Expelliarmus (drop, silence, knockback, NPC retrieve, Elder Wand allegiance) | FIXED (fix 7) |
| Aqua Eructo (extinguish, push, fire-mob damage, rain push, stream and wave visuals) | FIXED (fixes 3, 4) + OPEN items |
| CrowdControl, diminishing returns, boss and PvP rules | OK |
| /crowdcontrol command (registered, lang keys present) | OK |
| Bleeding effect (1/s per stack, i-frame restore, kill credit, death messages, damage type + tags) | OK |
| Brittle (consume on physical/explosion hit, Glacius +60%) | OK |
| Cursed wound (max health, milk refusal, Heal Master cure) | OK |
| Stunned effect + LivingEntityStunMixin + attack/use/interact/cast refusals | OK |
| PlayerBleedingMixin, FoodDataBleedingMixin (registered, targets valid in 1.21) | OK |
| VisibleEffects (pose markers synced to watchers) | OK |
| LevicorpusLivingEntityRendererMixin | OK (left unregistered on purpose, rulings Phase 3 Task 2) |
| PetrifiedRemainsEntity | FIXED (lang key) |

Counts: 14 OK, 10 FIXED (9 fixes), 8 OPEN items listed below (none of them block a feature).

## Fixes

1. **Glacius cold-biome bonus was missing.**
   `spell/disable/GlaciusRules.java:12,28` and `spell/disable/GlaciusSpell.java:61,155`.
   The Phase 3 plan table says "Slowness II 80 (+40 in cold biomes)" and gives
   `COLD_BONUS_TICKS = 40` and `slowTicks(int rank, boolean cold)`; the wiki says "Becomes stronger in cold
   biomes". The code had only `slowTicks(rank)` and no cold check, so a player casting Glacius in a snowy
   biome got the same slow as anywhere. Added the constant, the two-argument overload (the old one
   delegates with `cold = false`, so the existing test is unchanged), and `isCold` (biome base temperature
   under 0.15, exactly as the plan wrote it). New test `slowLastsTwoSecondsLongerInTheCold` in
   `GlaciusRulesTest`.

2. **Glacius block hit on water could never run.**
   `entity/SpellBeamEntity.java:215` (call) and `:565` (new `tryHitWaterSurface`).
   Vanilla projectile hit detection ignores fluids, so the beam always flew through the water and struck
   the bottom; the existing `state.is(Blocks.WATER)` branch in `onHit` (frosted ice on a water source) was
   dead. A player aiming Glacius at a pond never froze it (lang: "Turns water into ice"). Added a
   Glacius-only fluid clip each tick, like the Diffindo outline ray from the Phase 4 Task 3 ruling: when
   the path enters a water source from outside, whose block above is air (the surface), and no entity is
   closer, it is resolved as the Glacius block hit. The existing branch then places frosted ice through
   `TerrainRules.canPlace` (wards, spawn protection, claims, mobGriefing for NPCs). A beam fired from
   under water is not affected (inside hits are skipped). Side effect to know: Glacius now stops at a
   water surface, so it cannot hit a mob under the water from above.

3. **Aqua Eructo erased natural water and lava, and could load chunks.**
   `event/ModEvents.java:188-200` (`addTemporaryAquaWater`) and `:219-223` (`tickTemporaryAquaWater`).
   The stream replaced any water block it passed (including natural source blocks) with flowing water for
   3 ticks and then set the block to AIR. A beam fired across or into a pond deleted its source blocks for
   good; a beam through lava deleted the lava the same way. Now a block that already holds a fluid the
   spell did not place itself is left alone (the splash particles still show). Fire is still put out.
   The tick also called `getBlockState` with no `isLoaded` check; it now drops entries in unloaded chunks
   instead of loading the chunk.

4. **Aqua Eructo wave push never reached players.**
   `entity/SpellBeamEntity.java:1609`. The cone wave calls `push` on everything in front of it, but a
   player's motion is client-side and is only sent when `hurtMarked` is set; the wave does no damage to
   them, so other players in the wave were never pushed. Set `hurtMarked` for player targets once per hit.

5. **Incarcerous chain wind-up played all at once.**
   `spell/IncarcerousChainAnimation.java:39-80`. The six intro phases were queued as vanilla `TickTask`s
   at t0, t0+7, ..., t0+36, but `MinecraftServer.shouldRun` runs a TickTask as soon as the server has idle
   time (the tick number is not a delay; checked in the 1.21 bytecode). So on a normal server every phase
   and both chain sounds fired in the same tick. Replaced with a small due-tick list run from
   `ServerTickEvent` (END), cleared on `ServerStoppedEvent`; each phase still re-checks that the target is
   alive and in a server level.

6. **Hidden markers showed a missing-texture icon in the inventory.**
   New `spell/disable/HiddenMarkerEffect.java`; `ExampleMod.java:156,160,207,210,213`.
   `incarcerous_chains`, `levicorpus`, `petrified`, `glacius_chill` and `glacius_frozen` are always applied
   hidden and have no texture, but the inventory screen lists every effect regardless of the icon flag
   (Forge only filters on `IClientMobEffectExtensions.isVisibleInInventory`, default true; checked in the
   bytecode). A player who was hung, petrified, chained or frozen saw a magenta square named "Levicorpus",
   "Petrificus Totalus" and so on. The five markers now use `HiddenMarkerEffect`, whose
   `initializeClient` hides it in the inventory and HUD (same pattern as `SortingHatItem.initializeClient`;
   Forge only calls it on the client, and the class references no `net.minecraft.client` type).

7. **Expelliarmus knockback was lost on players inside i-frames.**
   `spell/support/ExpelliarmusSpell.java:84`. The knockback was only sent to a player client when the beam's
   0.1 chip hit happened to mark the player hurt; a player hit again within half a second saw no
   knockback. Set `hurtMarked` after the knockback, as Stupefy and Bombarda already do.

8. **Confringo/Bombarda/Fiendfyre impact jolt never reached players.**
   `entity/SpellBeamEntity.java:1569` (`applyHeavyImpactImpulse`). Same cause as fix 4: `push` on a
   player with no `hurtMarked` does nothing on their client. Set `hurtMarked` once per impact.

9. **Petrified Remains had no name.** `lang/en_us.json`: added
   `"entity.hogwartsmagic.petrified_remains": "Petrified Remains"` (the entity is pickable, so tooltips
   and commands show its name).

## Open items (not fixed)

- **Aqua Eructo stream mows plants without any terrain check.** `SpellBeamEntity.canReplaceWithTransientWater`
  deliberately lists grass, ferns, dead bushes, seagrass, kelp and snow layers (plus anything replaceable,
  such as vines and glow lichen). They become water for 3 ticks and then AIR, with no drop and no
  ward / claim / spawn-protection / mobGriefing check, and NPC casts too. It looks intended ("the water
  washes the grass away"), so I left it; if it should respect claims, gate the non-air, non-fire case
  through `TerrainRules.canPlace` (about 10 lines).
- **`AQUA_ERUCTO_WATER` is never cleared on server stop.** Entries live 3 ticks, so only a world quit in
  that window leaves water behind (or, in single player, could clear water at the same spot in the next
  world loaded). 5 lines (a `ServerStoppingEvent` clear). Left because it is in the shared ModEvents and
  the window is tiny.
- **Other areas' hidden markers have the same inventory icon problem**: `imperius`, `confunded`, `rooted`,
  `possessed`, `possessing` (mind area). They can switch their registration to `HiddenMarkerEffect`
  (1 line each). Not my area, so not changed.
- **Glacius now stops at the water surface** (fix 2). If hitting mobs under the water from above matters
  more than freezing ponds, drop the new clip; the frosted ice under a struck target still works.
- **Steam combo damage is mostly lost.** `SpellType.triggerSteamCombo` deals 2 magic damage in the same
  tick as the Confringo/Incendio/Bombarda hit, so i-frames swallow it unless it is bigger than that hit.
  The blindness and visuals do work. A design point, so not changed.
- **Stupefy's interrupt only breaks player channels and Voldemort's windup.** A Death Eater's Fiendfyre
  windup (WizardEntity) is paused by the stun and resumes after; the stun still cancels any beam it tries
  to fire while stunned. About 15 lines if wanted.
- **A Polyjuice-disguised player shows no Levicorpus/Petrificus pose** (the surrogate entity has no
  effects). Client only, about 10 lines in PolyjuiceClientState.
- **PvP-off**: Stupefy, Petrificus, Levicorpus, Incarcerous and Glacius still land on players with PvP off;
  this is the open question in FINAL-REPORT.md, not a defect. (Bleeding, Confringo's vanilla explosion and
  Bombarda's splash credited to a player do respect PvP-off through vanilla `ServerPlayer.hurt`.)

## Doc mismatches

MOD_FEATURES_WIKI.md still describes the pre-mastery spells for most of this area. The plans and rulings
are the higher authority, so the code is right in each case:
- Stupefy: wiki "heavy slowness + blindness"; code: a true stun (1.5 to 2.5s), interrupt, knockback, Master chain.
- Petrificus: wiki "extreme slowness, no jump, weakness"; code: stun + topple that breaks on damage.
- Incarcerous: wiki "slowness on the target and weakness/slowness nearby"; code: a tether, area bind from Adept.
- Levicorpus: wiki "levitation + weakness"; code: a hover at 1.5 blocks, no items or shields, +25% damage.
- Sectumsempra and Dolohov: wiki "wither"; code: bleeding stacks / cursed wound (max health).
- Expelliarmus: wiki "applies weakness"; code: silence ("disarmed") 2 to 4s, Elder Wand allegiance at Master.
- Bombarda: wiki "does not focus on terrain destruction"; code: it is the terrain breaker (player casts, mobGriefing on).
- Diffindo: wiki "high direct damage"; code: 6 to 9 armour-piercing damage plus cutting.
- Glacius: wiki "strongly slows"; code: Slowness II then freeze solid on a second hit. (The cold bonus the wiki
  mentions now exists again, fix 1.)
- Aqua Eructo: wiki matches the code.
Note: another agent is editing MOD_FEATURES_WIKI.md in this tree; I did not touch it.

Design-spec items that look missing but were cut on purpose (Decision 1 and the plan "Cut" lists), not
defects: Stupefy Expert 4s on a mid-windup target, Glacius Expert lava to obsidian and Master Tria cone,
Levicorpus Expert disarm chance, Petrificus Expert flyer drop, Incendio flame jet, Diffindo Expert armour
durability damage, Dolohov Expert scar, Confringo Master delayed fuse (replaced by the brittle payoff),
Expelliarmus Expert item flies to you and Master clash win.

## Build and tests

- Compile: `compile.sh` BUILD SUCCESSFUL (after all fixes).
- Tests: `test.sh 'com.hogwartsmagic.spell.d*'` BUILD SUCCESSFUL: damage (7 suites, 25 tests) and disable
  (6 suites, 31 tests, including the new Glacius cold test) all pass; `test.sh 'com.hogwartsmagic.spell.combat.*'`
  BUILD SUCCESSFUL (4 suites pass).

## Files changed

- src/main/java/com/hogwartsmagic/spell/disable/GlaciusRules.java
- src/main/java/com/hogwartsmagic/spell/disable/GlaciusSpell.java
- src/main/java/com/hogwartsmagic/spell/disable/HiddenMarkerEffect.java (new)
- src/main/java/com/hogwartsmagic/spell/IncarcerousChainAnimation.java
- src/main/java/com/hogwartsmagic/spell/support/ExpelliarmusSpell.java
- src/main/java/com/hogwartsmagic/entity/SpellBeamEntity.java (Glacius water clip, two hurtMarked lines)
- src/main/java/com/hogwartsmagic/event/ModEvents.java (addTemporaryAquaWater, tickTemporaryAquaWater)
- src/main/java/com/hogwartsmagic/ExampleMod.java (five marker registrations)
- src/main/resources/assets/hogwartsmagic/lang/en_us.json (one key)
- src/test/java/com/hogwartsmagic/spell/disable/GlaciusRulesTest.java (one test)
