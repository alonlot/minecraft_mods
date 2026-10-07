# Ledger: Dark Magic Phase 4, advancements and docs (plan docs/superpowers/plans/2026-10-08-dark-magic-phase4.md)

Process: Opus for all agents, one implementer at a time (runs after Dark Magic Phase 3 is complete), review per task, final review, one fix wave, re-review, push. The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings 1-12 accepted as written.
- Spec corrections accepted: neither advancement is announced to chat (an announcement would reveal a Lost player, against decision 11); a Horcrux floor makes Redemption impossible while it exists (intended); Lost-to-Clean journeys before Phase 4 earn nothing.

## Progress
- T1 done fce4f06 (BASE 468, protocol 8; 477 tests, RED seen). Its review is folded into the final review. T2 dispatched.
- T2 done 5d04e6b (477 tests, advancements ok, boot Done 3.579s). Its review is folded into the final review. Fix-wave queue: wrap the long DarkMagicData javadoc line.
- T3 done f325566 (docs only, node parse ok). 8 deviations from the plan text, all to match the built code (floor at 60+, safe escort landing, guard tag, Fiendfyre/steam credit, drop-at-once, /darkmagic shows tiers to ops, decay 10 h per tier, heal window at 0 Darkness, systems count 11). Controller checked the decay figure against DarkRules. Review folded into the final review. T4 dispatched.
- T4 done 025f1d5 (478 tests, 5 pages fit: 136/81/141/116/126 px). 7 text changes to match the built code. Fix-wave queue: the Horcrux guide line "cannot rise above Corrupted" reads as "cannot get darker"; the floor stops Darkness from falling, so say it cannot fade below Corrupted. Review folded into the final review. T5 next.
- T5 done (controller): clean build 478 tests 0 fail; greps clean (afterWrite only in DarkMagicData.java:168, protocol 8); 3x json ok, node ok; boot Done 3.711s, no hogwarts errors, no server left; jar build/libs/hogwartsmagic-1.0.0.jar. Final review dispatched (covers T1-T4).
