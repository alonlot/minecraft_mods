# Ledger: Cauldron Brewing Phase 2 (plan docs/superpowers/plans/2026-10-08-cauldron-brewing-phase2.md)

Base: 6a0c340 (406 tests green; Dark Magic Phase 1 built, its final review and fix wave may land between tasks). Process: Opus for all agents (user ruling), one implementer at a time, review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings accepted as written.
- Spec corrections accepted: the give-in key defaults to J (G is the Grimoire key); ingredient chest loot uses one structure-aware global loot modifier (the camps use vanilla chest tables); vanilla gives 2 random trades per level, so one apothecary may not stock every herb (accepted; Diagon Alley shopkeepers keep their baked trades); basilisk_petrification is now synced to other players.
- Added: the wiki Basilisk entries (MOD_FEATURES_WIKI.md and wiki/data.js) must describe the freeze and the give-in choice in this phase (fix wave), not wait for Phase 3.

## Progress
- Brewing P2 T1 done 41c4e55 (420 tests = 406 + 14, RED seen). BASE for this phase = 406. Review dispatched; the Dark Magic Phase 1 fix wave runs before Task 2.
