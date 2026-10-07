# Ledger: Dark Magic Phase 3, the rewards and the deeds (plan docs/superpowers/plans/2026-10-08-dark-magic-phase3.md)

Process: Opus for all agents, one implementer at a time (runs after Dark Magic Phase 2 is complete), review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings 1-14 accepted as written.
- Spec corrections accepted: the Horcrux +30 is given at forging (a ritual can still fail after it starts), as max(x + 30, 60); the 15% discount also covers the Fiendfyre and Imperio channel costs; the floor freezes a value below 60 only for a Horcrux made in creative or with the toggle off, until the Horcrux is gone.
- The escort is an ordinary Death Eater group: it still attacks non-Lost players and villagers. Accepted as canon.

## Progress
- T1 done a14f090 (BASE 448, protocol 8; 465 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 rename redemptionAtMostHalvesTheTimeFromLostToClean to redemptionWindowEqualsTheDecayPeriod. M2 and M3 no action. For the Task 5 review: the floor caller passes a FINISHED binding only, a heal that lightens nothing does not store DarkMagicLastRedemption, and the window reads Stats.PLAY_TIME.
- T2 done 0962bba (465 tests). Review and T3 dispatched.
- T3 done e175348 (465 tests). Its review is folded into the final review. Fix-wave queue: the WizardEntity comment says the hurt-by goal above but it is below. T4 dispatched.
- T2 review: Approved (every dark check and spend uses the same discounted cost; non-dark costs bit-identical; dedicated server safe). Fix-wave queue: M1 the DarkRewards javadoc names DarkMagicData.getTier. M2 (Unicorn refund scales with the paid cost) accepted.
