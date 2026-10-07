# Ledger: Sneakoscope Part A (plan docs/superpowers/plans/2026-10-07-sneakoscope-part-a.md)

Base: f5033f3 (360 tests green). Process: Sonnet implementer per task, Opus review per task, final Opus review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings accepted as written.
- Spec corrections accepted: the strongest signal wins (not the nearest source), so a near Death Eater never lowers a Dark Mark signal; the four gold nuggets go in the two top corners and beside the pane, because the ingot and the dust take two sides; getSignal alone makes it comparator-readable.

## Progress
- T1 done da65920 (381 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 add the reversed-order case to theStrongestSourceWins, M2 NaN distance gives 0 (guard !(distance < range)) with a test, M3 assert negative distance gives 15, M4 whistle interval test asserts strict decrease. Spec decision 3 text updated to strongest-wins by the controller.
- T2 done c6bfd1d (381 tests, JSON valid, boot Done 3.6 s). Review and T3 dispatched.
