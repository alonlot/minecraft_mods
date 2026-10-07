# Ledger: Sneakoscope Part A (plan docs/superpowers/plans/2026-10-07-sneakoscope-part-a.md)

Base: f5033f3 (360 tests green). Process: Sonnet implementer per task, Opus review per task, final Opus review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings accepted as written.
- Spec corrections accepted: the strongest signal wins (not the nearest source), so a near Death Eater never lowers a Dark Mark signal; the four gold nuggets go in the two top corners and beside the pane, because the ingot and the dust take two sides; getSignal alone makes it comparator-readable.

## Progress
- T1 done da65920 (381 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 add the reversed-order case to theStrongestSourceWins, M2 NaN distance gives 0 (guard !(distance < range)) with a test, M3 assert negative distance gives 15, M4 whistle interval test asserts strict decrease. Spec decision 3 text updated to strongest-wins by the controller.
- T2 done c6bfd1d (381 tests, JSON valid, boot Done 3.6 s). Review and T3 dispatched.
- T3 done 7e19bf8 (381 tests). Review and T4 dispatched.
- T2 review: Approved. Fix-wave queue: M1 the per-second stagger key BlockPos.asLong() uses only 5 of 20 phases; use a mixed key (BlockPos.hashCode() through floorMod) in SneakoscopeBlockEntity AND WizardCauldronBlockEntity (same pattern). M2 (whistleAt comment) no action.
- T3 review: Approved; minors (cursor slot not scanned, up to 19 ticks of stale whistle, negligible scan cost) accepted as the plan's design. T4 done b415e64 (381 tests, JSON valid). Controller 3D render: the shape reads as a glass box on a tray and the spinning sheet is opaque cream. Fix round sent to the T4 implementer: a top silhouette (tip, two lower cones, belly 4.5..11.5, two upper cones, brass spindle to y 10), shape box 4.5,0,4.5 to 11.5,10,11.5, spin frames stay glass-blue and brighter with near-white facets.
- T4 fix round done c6f1b49 (381 tests; brass and still glass byte-identical). Controller 3D render: reads as a glass spinning top on a brass plate; spinning glows blue-white. Accepted. T4 assets (16-variant blockstate, item override, mcmeta) go into the final review instead of a separate task review.
- T5 done 490046f, trailer corrected by the controller before push (now 51c2218). T6 (controller): clean build OK, 381 tests green, every touched JSON valid, dedicated-server boot Done (3.704s) with no Exception or ERROR, server stopped. Final Opus review dispatched.
- Final review: Approved (no Critical or Important). Task 4 assets fully reviewed there and correct. Fix wave dispatched with: T1 M1-M4 test and NaN items, T2 M1 even stagger (Sneakoscope and Wizard Cauldron, pos.hashCode), setPlacedBy clears the transient component (no save warning), onEntityItemUpdate clears the spin on any dropped item, generator docstring to the fix-round model (PNGs unchanged), checklist rows 6 and 20 and rulings 15-16 updated. Accepted: vanilla price drift on the 14-emerald trade, texture edge period, non-ticking chunk keeps power, tooltip omits Voldemort.
