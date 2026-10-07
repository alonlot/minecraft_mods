# Ledger: Dark Magic Phase 2, the costs (plan docs/superpowers/plans/2026-10-08-dark-magic-phase2.md)

Process: Opus for all agents, one implementer at a time (it runs after Brewing Phase 2 is complete), review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings 1-14 accepted as written.
- Spec corrections accepted: the halved corporeal chance is replaced by no corporeal at Tainted, and keep-away is replaced by attack at Lost (replacements, not stacking costs); the Lost health cost has no UI beyond the vanilla hearts, like the Horcrux; Dementor range is the 48-block follow range.

## Progress
- T1 done afa458c (BASE 432, protocol 8; 448 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 rename thePatronusNeverFails to corporealChanceNeverRisesAboveBase (the Task 3 review checks a miss gives the shield), M2 remove the local-constant stacking assertion (the Task 2 review checks real modifier stacking), M3 tighten or drop aTierKeepsTheCostsBelowItExceptTheTwoReplacements (covered by eachTierHasExactlyTheSpecCosts).
