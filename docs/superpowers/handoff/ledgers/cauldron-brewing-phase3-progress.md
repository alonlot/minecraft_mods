# Ledger: Cauldron Brewing Phase 3 (plan docs/superpowers/plans/2026-10-08-cauldron-brewing-phase3.md)

Process: Opus for all agents, one implementer at a time (runs after Dark Magic Phase 4 or whenever the branch is free), review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Task 0 (carried from Brewing Phase 2, do first)
- After a failed give-in (another mod cancelled the damage), re-arm PetrifiedSince to now (or send the time-up line only once).
- BrewRulesTest uniqueness test asserts step 0 of every recipe is an ADD.
- PetrificationEvents class javadoc: the keys clear on every death, giving in included.

## Controller rulings
- Planner rulings accepted as written (classroom in the floorless apse tower behind the gatehouse bookshelf room; seven level-3 cauldrons over magma; chests filled in Java because the schematic loader ignores block entities; no venom, gold nugget or sample in the chests; old castles untouched; advancements First Brew and Toil and Trouble by code, Liquid Luck by the vanilla trigger).
- Out of scope, reported to the user: room signs in Mini Hogwarts are blank in game because the schematic loader ignores block entities (an existing bug).

## Progress
- Task 0 done 70dd295 (479 tests, RED seen). Failed give-in re-arms PetrifiedSince (PetrificationRules.giveInFailed); step-0 ADD assert moved ahead of uniqueness; death javadoc corrected (behavior already right). Controller read the diff. Review folded into the final review. T1 dispatched.
- BASE = 479 (after Task 0).
- T1 done f77a406 (483 tests, RED seen). T2 done 4d08dea (advancements, boot Done 4.070s). Deviation accepted: Forge 51.0.33 has no FakePlayer class (controller checked the mapped jar), so the grant requires the player to be in the server player list. Reviews folded into the final review. T3 dispatched.
- T3 done 318477e (483 tests; 124 cells changed, none outside the room; renders checked by the controller: 7 cauldrons over magma, door to the bookshelf room). Chests for T4: (42, 33, 111) and (42, 33, 114), facing west. Noted: an old glowstone at (42, 35, 115) above the new lantern, left as is. Review folded into the final review. T4 dispatched.
- T4 done 30ad440 (483 tests, boot Done 3.457s). Added a level.isLoaded guard so the fill never loads a chunk (accepted). The in-game checklist must cover opening both chests in a new castle. Review folded into the final review. T5+T6 (wikis) dispatched to Sonnet at the user's request.
- T5 done ed265c8, T6 done 3a32dc9 (Sonnet; node ok; stats 37/17/17/13 set to the real counts). Petrification placed under Potions and Effects before Dark Magic, because House Perks now follows Dark Magic. Review folded into the final review. T7 next (controller).
- T7 done (controller): clean build 483 tests 0 fail; 4x json ok; schematic dry run 'nothing to do'; scope = File Structure files only; boot Done 3.785s, 0 Exception, no advancement errors, no server left. Final review dispatched (covers Task 0 to T6).

## In-game checklist (for the user)
| # | Check |
|---|---|
| 1 | In a new world, run `/spawnminihogwarts` (or walk into a Forbidden Forest) and wait for the build to finish. `latest.log` has "Mini Hogwarts Potions classroom: 2 ingredient chests stocked" and no "Unknown block in mini_hogwarts palette". |
| 2 | Climb the gatehouse stairs to the upper room with the bookshelves; the door in its south wall opens onto a solid stone brick floor, no drop. |
| 3 | The room has seven green cauldrons over magma (two rows of three, one at the front by the lectern), chiseled shelves on the west wall, barrels with two lanterns and two chests on the east wall. |
| 4 | Both chests hold glass bottles, a water bucket, a Fine Wood Stick, honey bottles, glow berries, blaze powder, mandrake roots, dittany, asphodel, wormwood, valerian sprigs, arcane dust and gunpowder. |
| 5 | Brew a Wiggenweld Potion in a classroom cauldron without adding water first: the first honey bottle is accepted (the cauldron starts as three doses of water), and the brew simmers and finishes over the magma. |
| 6 | Bottle the first dose: the "First Brew" toast and chat line; in the advancement tab it hangs under "Potions Class". |
| 7 | With a fresh player, ruin a brew with a wrong ingredient: "Toil and Trouble". |
| 8 | With another fresh player, let a finished brew overbrew on the heat (120 s): it is ruined, and no "Toil and Trouble". Bottling the Failed Potion gives no "First Brew". |
| 9 | Brewing Polyjuice (with a sample) and bottling it gives "First Brew" to a player who lacks it; buying Polyjuice from an apothecary does not. |
| 10 | `/give @s hogwartsmagic:felix_felicis`: "Liquid Luck" (challenge frame), under "First Brew". |
| 11 | Load a world saved before this phase that already has a Mini Hogwarts: the castle is unchanged (the apse tower behind the upstairs room still has no floor), nothing is rebuilt, no new errors in the log. |
| 12 | Open `wiki/index.html`: the hero stats match, Systems shows Cauldron Brewing and Petrification, Items shows the seven potions and Brewing Ingredients, Blocks shows Dittany Plant, Mobs shows Petrified Remains. |
