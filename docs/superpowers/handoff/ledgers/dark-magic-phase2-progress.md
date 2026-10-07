# Ledger: Dark Magic Phase 2, the costs (plan docs/superpowers/plans/2026-10-08-dark-magic-phase2.md)

Process: Opus for all agents, one implementer at a time (it runs after Brewing Phase 2 is complete), review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings 1-14 accepted as written.
- Spec corrections accepted: the halved corporeal chance is replaced by no corporeal at Tainted, and keep-away is replaced by attack at Lost (replacements, not stacking costs); the Lost health cost has no UI beyond the vanilla hearts, like the Horcrux; Dementor range is the 48-block follow range.

## Progress
- T1 done afa458c (BASE 432, protocol 8; 448 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 rename thePatronusNeverFails to corporealChanceNeverRisesAboveBase (the Task 3 review checks a miss gives the shield), M2 remove the local-constant stacking assertion (the Task 2 review checks real modifier stacking), M3 tighten or drop aTierKeepsTheCostsBelowItExceptTheTwoReplacements (covered by eachTierHasExactlyTheSpecCosts).
- T2 done 44c6ac0 (448 tests). Review and T3 dispatched.
- T3 done 3174c53 (448 tests). Controller review (small diff): both corporeal rolls use the tier chance, a miss still falls to the shield (the Patronus never fails), upkeep x1.5 from Corrupted, insomnia threshold by tier. Approved. T4 dispatched.
- T2 review: Approved (stacking with Horcrux and Hufflepuff checked against the jar; never kills; cache server-only; smoke gated; petrification compatible). Minors accepted with no action: cache not dropped on logout (up to 20 ticks of empty NPC searches), recompute bounded by syncs as ruled, WeakReference per recompute.
- T4 done a48d0eb (448 tests; boot Done 3.206s with a Dementor, Student and House Elf summoned; no errors). Review and T5 dispatched.
- T5 done bd8deb7 (448 tests). Its review is folded into the final review. Final verification and final review dispatched.
- T4 review: Approved. Fix-wave queue: M1 TargetDarkPlayer predicate skips petrified players (Petrification.isPetrified) after the distance check, M2 the Students' Lost-player target goal is gated by !isDueling, M3 the keep-away canUse order becomes super.canUse() && !isDueling(this). Controller T6 verification: clean build OK, 448 tests green, protocol still 8, boot Done (3.137s) with no Exception or ERROR, server stopped.
- Final review: Needs fixes. I1 dark-tier targets are never re-checked (vanilla canContinueToUse ignores the predicate): Students keep attacking a player who left Lost or with the toggle off. Fix wave dispatched (brief dm2-fixwave-brief.md): canContinueToUse re-checks the tier and the cache, stone players skipped, student duel gates, elf follow reads the tier only when someone is Lost, elf flee ignores dead players and other dimensions, T1 test tidy-ups, checklist rows. Accepted: smoke around a stone Lost player, elves sense a cloaked Lost player, bounded avoid search, elf repath, freed and bound elf behaviour unchanged.
