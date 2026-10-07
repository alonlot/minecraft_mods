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
- T4 done 8f1bbcd (465 tests, boot Done 3.256s). Review and T5 dispatched.
- T5 done a1ae997 (465 tests; floor only for a finished binding, no window spent on a no-op heal, PLAY_TIME window). Final verification and final review dispatched.
- T6 (controller): clean build OK, 465 tests green, protocol 8, lang valid, boot Done (3.692s) with no Exception or ERROR, server stopped.
- T4 review: Needs fixes. Fix wave: I1 landingNear accepts a column only above getMinBuildHeight with a sturdy, non-lava block below and two free blocks of headroom; otherwise the owner's own block on arrival (only when it has headroom) and no teleport on catch-up. M1 catch-up uses horizontal distance and skips the teleport when the landing spot is still past the catch-up range. M2 DefendOwnerGoal.canContinueToUse requires wizard.isEscort(). M3 an escort also ends when the owner's player object changed (respawn) since the last check (store the owner's death count or entity id). M5 headroom check as in I1. M4 and M6 accepted.
- Final review: Needs fixes. I1 the floor froze values below 60 for Horcruxes forged before Phase 3 (and for creative and toggle-off forgings). Ruling: the floor holds only a value already at or above 60. Also M1 Fiendfyre and steam credit the caster, M2 Voldemort guards never turn neutral, M3 no Horcrux priming from a puppet or forced Avada, M4 checklist row 24. Fix wave dispatched with these and the T1-T4 queue (brief dm3-fixwave-brief.md).
