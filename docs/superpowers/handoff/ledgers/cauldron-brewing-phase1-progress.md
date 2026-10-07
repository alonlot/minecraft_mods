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
- T3 done 503fd87 (359 tests, 7 JSON valid, boot Done 12.4 s). NBT-load guard and empty-target guard added. Controller error: ledger commit 627b392 swept in the four staged stand-recipe deletions, so 627b392 alone does not compile; 503fd87 repairs it, history not rewritten. Rule from now: the controller commits ledger lines with `git commit -- <file>` only. Review and T4 dispatched.
- T4 done 72b2279 (359 tests, brew sheet greyscale, other sheets byte-identical). Review and T5 dispatched.
- T5 done 74d7bd9 (docs only; data.js syntax OK). Controller review: accurate against the code, except the stirring rod is called Fine Wood Stick while its display name is Wand Blank. Fix-wave queue: the T2-M3 rename to Fine Wood Rod also updates both wikis and the guide, and the wiki Wizard Cauldron entry adds: ingredients need a full cauldron (3 doses), keep the other hand empty when sneak-stirring.
- T4 review: code approved; Important I1 (plan-mandated): the guide says Fine Wood Stick but the item displays as Wand Blank. Ruling: rename the display name of item.hogwartsmagic.fine_wood_stick to "Fine Wood Stick" (it fits both wand crafting and stirring; guide, wikis and checklist then need no change). This replaces the earlier "Fine Wood Rod" idea. Check the wand_blank advancement and the tooltip still read well. Fix-wave queue adds M1 guide "over two seconds" -> "over about two seconds", M2 "a bucket pours it away" -> "an empty bucket pours it away".
- T3 review: Approved. Fix-wave queue: M1 an empty-hand status click must not initialize a never-used cauldron (keep it classic green; status reads as a full brew of unknown kind is not needed - just say it holds an old brew and to bottle or pour it is NOT wanted either: ruling - the status line for an uninitialized cauldron is "The cauldron is full." and nothing changes), M2 with an empty main hand and a non-empty off hand, return PASS so the off-hand item is used, M3 the load guard also clears the history and sets the ruined colour. M4 (deletions in 627b392) and M5 (server.properties first-run ERROR) need no action.
