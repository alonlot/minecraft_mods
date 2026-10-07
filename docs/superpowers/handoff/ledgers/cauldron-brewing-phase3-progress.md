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
