# Ledger: Cauldron Brewing Phase 1 (plan docs/superpowers/plans/2026-10-07-cauldron-brewing-phase1.md)

Base: ea0bbf6 (339 tests green). Process: Sonnet implementer per task, Opus review per task, final Opus review, one fix wave, re-review, push.

## Controller rulings
- Planner rulings 1-29 accepted as written.
- Spec fix: the roadmap "Later" list no longer names moving Polyjuice into the cauldron (decision 10 does it now).
- Spec drift accepted: Wiggenweld heals over 2.4 s (Regeneration tick rate), splash Polyjuice stays a vanilla SPLASH_POTION (the impact handler needs it).

## Progress
- T1 done e1e0e43 (359 tests, RED seen). Review and T2 dispatched.
- T2 done 8ea2c23 (359 tests, JSON valid). Review and T3 dispatched.
- T1 review: Approved. Fix-wave queue: M1 overbrew warning and ruin one second apart (make overbrewLeft and overbrewed agree), M2 table test also checks last required step vs tail[0] and adjacent tail steps, M3 tests pin recipe identity for one-step Pepperup and Polyjuice histories and the Polyjuice waiting state. M4 (null kind/item on NBT load) sent to the T3 implementer as a guard.
- T2 review: Approved. Fix-wave queue: M1 BrewedPotionItem fires CriteriaTriggers.CONSUME_ITEM like vanilla PotionItem, M3 rename fine_wood_stick display name so it matches its "stirring rod" tooltip (ruling: "Fine Wood Rod"; keep the id). M2 (null target into PolyjuiceStacks) sent to the T3 implementer as a guard.
