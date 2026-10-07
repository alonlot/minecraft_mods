# Ledger: Cauldron Brewing Phase 2 (plan docs/superpowers/plans/2026-10-08-cauldron-brewing-phase2.md)

Base: 6a0c340 (406 tests green; Dark Magic Phase 1 built, its final review and fix wave may land between tasks). Process: Opus for all agents (user ruling), one implementer at a time, review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings accepted as written.
- Spec corrections accepted: the give-in key defaults to J (G is the Grimoire key); ingredient chest loot uses one structure-aware global loot modifier (the camps use vanilla chest tables); vanilla gives 2 random trades per level, so one apothecary may not stock every herb (accepted; Diagon Alley shopkeepers keep their baked trades); basilisk_petrification is now synced to other players.
- Added: the wiki Basilisk entries (MOD_FEATURES_WIKI.md and wiki/data.js) must describe the freeze and the give-in choice in this phase (fix wave), not wait for Phase 3.

## Progress
- Brewing P2 T1 done 41c4e55 (420 tests = 406 + 14, RED seen). BASE for this phase = 406. Review dispatched; the Dark Magic Phase 1 fix wave runs before Task 2.
- T1 review: Approved (the seven first ingredients all differ, so match and the first-fitting recipe cannot pick the wrong one). Fix-wave queue: M1 the table test asserts first steps are unique; M2 livingDeathNeedsItsGunpowder asserts usedTail(done, GUNPOWDER) is false, and the Task 9 review checks Living Death splash is chosen by recipe; M3 (Felix XP from any Luck II+) accepted as plan ruling 5.
- T2 done 37fb9f2 (431 tests, RED seen). Review and T3 dispatched.
- T2 review: Needs fixes, Important I1 (plan-mandated): STRIPPED_MOB_KEYS strips active_effects but the effects' permanent attribute modifiers live in attributes, so a revived mob stays frozen forever. Ruling: KEEP active_effects (remove it from STRIPPED_MOB_KEYS, invert that test line); the Task 7 revive calls removeAllEffects() after load so vanilla removes the modifiers; never strip attributes. Also strip SleepingX, SleepingY, SleepingZ, TicksFrozen and HasVisualFire; keep Brain (villager home and job memories). Fix plan rulings 27 and 29 and the Task 7 code to match. M2 GazeOutcome.KILL doc: harmless for creative and spectator. M3 for Task 6: write Petrified and PetrifiedSince together, treat a missing PetrifiedSince as now. Fix round runs right after Task 3 commits.
- T3 done 770b1ba (431 tests, 13 JSON valid, boot Done 3.268s; registry-ID WARNs from the old dev world only). Controller looked at the sprite preview: six clear, distinct sprites. Accepted. Review and the T2 fix round dispatched.
- T2 fix round done 0eb5b72 (431 tests): active_effects kept, sleep/freeze/visual-fire keys stripped, attributes and Brain kept; plan rulings 27/29, Task 6 (keys written together, missing PetrifiedSince = now) and Task 7 (removeAllEffects after load, health to max after) updated. Controller checked the diff. T2 approved. T4 dispatched.
