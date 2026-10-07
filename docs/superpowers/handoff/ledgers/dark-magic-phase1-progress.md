# Ledger: Dark Magic Phase 1 + Sneakoscope Part B (plan docs/superpowers/plans/2026-10-08-dark-magic-phase1.md)

Base: 996f638 (383 tests green). Process: implementer per task, review per task, final review, one fix wave, re-review, push. User ruling (2026-10-07): Opus for ALL agents from now on (planners, implementers, reviewers, fix waves). The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings 1-22 accepted as written.
- Spec drift accepted: the client gets the effective Darkness value instead of a config sync packet; the 10-minute quiet window is game time (passes offline), the 30-minute decay steps are online time only.

## Progress
- T1 done f6aa7c4 (404 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 config comment for darkMagicGainMultiplier says values under about 0.02 round small gains to 0, M2 gainMultiplierScalesTheWholeTable checks every dark spell, M3 tierKeysAndColours pins all five colours.
