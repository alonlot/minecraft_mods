# SDD ledger — plan: docs/superpowers/plans/2026-10-02-spell-mastery-phase5b.md

Spec: roadmap Phase 5 (part b: Reparo, Accio, Ascendio, Depulso, Apparition). Branch: spell-mastery (Phases 1-5a done). Models: Sonnet implementers, Opus reviewers.

## Pre-flight scan
The Phase 5a final review checked every 5b anchor mechanically. Plan fixes applied: the pre-flight grep is `public static isLeftClickSelfCast`; the Accio claim check also refuses useBlock DENY; a reflected Accio (masteryEligible false, passed from the 4-arg applyHitEffect) never snatches; a note that the self-cast whitelist is a security boundary (only Reparo is added in T2; Ascendio's self-launch uses the normal cast path).
Ruling: Accept the planner's resolutions (NPC Reparo keeps the old sweep, Accio never pulls the Elder Wand and reserves pulled items, Ascendio self-cast by pitch ≥60 or crouch, splinch = config × 1/½/⅕/0, Master Floo list of 32 remembered with the nearest 8 shown) — consistent with the spec — tuning if wrong.
Lessons: effect check before getPersistentData; snaps only into free space with the dimension stored; canBreak last; claims via RightClickBlock (cancel or DENY); reflected beams earn nothing; NPC-perspective targeting.

## Progress
Task 1: Ruling: (plan-mandated) splinchChance returns 0.0 for NaN, plus tests; fold in the cheap boundary tests (Accio 40.5/80.5 false, pitch 59.9 false) — no cost. Queued after the T2 implementer.
Task 1: minor (deferred): itemExtraMana rounding boundaries; out-of-range rank assertions per class.
Task 1: fix round 1/5 (NaN guard and tests; commit 4037127 — the controller verified the one-line main change)
Task 1: complete (commits d1599d7..8445e69 + 4037127)
Task 2: Ruling: exclude the Invisibility Cloak from Reparo item repair (keeps the Magical Silk anvil economy) — fold into the final fix wave.
Task 2: minor (deferred): no wand mana refund on item repair; actionbar spam on held left-click; unused rank for NPCs; no tests.
Task 2: complete (commits 8445e69..cbf0d84, review clean)
Task 3: implemented a4dc067 (DONE_WITH_CONCERNS: isPvp for PvP; tether/hang interplay unaddressed; ITEM_PULLS stale dims); Opus review dispatched
Task 3: review Needs fixes (refused-pull XP; NPC push regression; snatch claim veto; reflected block rank; loose-item XP farm). Fix round 1/5 dispatched to the implementer.
Task 3: deferred to the final wave: tickAccioPull sends per-tick velocity to players and fights the tether/hang (pre-existing).
Task 3: fix round 1 done (161ba1b); re-review dispatched
Task 4: BASE 161ba1b; implementer dispatched (sonnet) in parallel with the T3 re-review (read-only; no file overlap except en_us.json)
Task 3: minor (deferred): the snatch is skipped during the victim's i-frame window (hurt returns false); PAID_ITEMS is shared across players; no unit tests for BlockResult.
Task 3: complete (commits 4037127..161ba1b, re-review approved)
Task 4: implemented badf24b; review dispatched
Task 4: review Needs fixes (surge cooldown bypass; chained flight).
Task 4: Ruling: arm the air charge only on a ground launch; a midair full launch gives no new fall immunity (overrides the plan) — the spec says one midair second cast per jump — cost if wrong: Expert/Master feel less floaty.
Task 4: Ruling: the surge re-push uses a throttled cadence (every 4 ticks, max 40 ticks, in water only) instead of a stale server velocity check — the server does not simulate player physics — cost: about 10 velocity packets per surge.
Task 4: fix round 1/5 dispatched
Task 4: fix round 1 done (02fb145); re-review dispatched
Task 4: minor (deferred): casting from water below Master gives no fall immunity (changelog note); a shallow-water ground launch silently loses the charge; lag over 250ms can drop the charge.
Task 4: complete (commits 161ba1b..02fb145, re-review approved)
Task 5: BASE 02fb145; implementer dispatched (sonnet)
Task 5: implemented 502144e; review dispatched.
Task 5: Ruling: Depulso deflects projectiles only, not spell beams (the spec says projectile; Protego owns beam reflection) — my dispatch wording caused the drift — cost if wrong: one fewer Master trick.
Task 5: review Needs fixes (beam redirect; arrow and trident pickup farm; pearl and hook re-own; flyIntoWall gives no credit).
Task 5: Ruling: deflect only AbstractArrow (excluding tridents) and AbstractHurtingProjectile; restore the pickup state; slams use magicFrom — prevents farms and keeps kill credit — cost: no trident deflection.
Task 5: fix round 1/5 dispatched
Task 5: fix round 1 done (cb98be2); re-review dispatched
Task 6: BASE cb98be2; implementer dispatched (sonnet), in parallel with the T5 re-review
Task 5: minor (deferred): a rare same-tick double hit on a pair (i-frames absorb it); the deflect reads rank every beam tick; the wall probe is mid-height for short mobs.
Task 5: complete (commits 02fb145..cb98be2, re-review approved)
Task 6: implemented 81a7574; review dispatched.
Task 6: Ruling: splinching stays as pre-existing (8 magic plus Wither, can kill) since it is now config-toggleable — cost: surprise deaths at low HP.
Task 6: Ruling: zero-distance apparition XP is bounded by the cast throttle and mana like every cast; no minimum distance — cost: slow XP farming.
Task 6: Ruling: fold into the final wave: cap map tiles at about 150 per side (160k fills per frame at GUI scale 1), cancel the channel on dimension change, grey out tiles beyond the range circle.
Task 6: minor (deferred): view distance under 200 blocks limits Master range; caster marker off by step/2.
Task 6: complete (commits cb98be2..81a7574, review approved)

## Cloud session (2026-10-03)
Ruling: user instruction (2026-10-03): no pushes at all, work stays local in the container. One backup push to github claude/keen-lamport-g1ykt4 happened before that instruction.
Ruling: superpowers plugin is not installed; briefs are cut from the plan by hand and review packages are git diff files, per HANDOFF.md - no cost.
Ruling: commit trailer is Co-Authored-By: Claude Sonnet 5.5 (user instruction overrides implementer-rules.md) - no cost.
Task 7: BASE 790bf8b; implementer dispatched (sonnet). Phase 6a pre-flight dispatched (opus, read-only) in parallel.
Task 7: implemented d9698fd (DONE_WITH_CONCERNS: one getChunkAt load at the destination). Ruling: accept a single destination chunk load on a validated Master jump - vanilla teleportTo loads it anyway; the no-load rule is for probes and ticks - cost: one chunk load per jump.
Ruling: user picked pushing to github alonlot/minecraft_mods claude/keen-lamport-g1ykt4 (pushed d9698fd); push there after each phase.
Task 7: review Needs fixes (no held-wand check at completion lets a forged client skip the cooldown). Ruling: also guard Task 6 completeApparition the same way, and fold Minor 1 (border check before the chunk load) and Minor 2 (refresh the stored name on jump) - cheap - no cost.
Task 7: minor (deferred): passengers left behind silently; decode clamp leaves extra bytes; protocol version bump to confirm in the final review.
Task 7: fix round 1/5 dispatched
Task 7: fix round 1 done (b6b33d4); re-review dispatched
Task 7: complete (commits 790bf8b..b6b33d4, re-review approved)
Task 8: tests 159/0 failures, dedicated server Done (2.752s) clean. In-game checklist (plan Task 8 Step 2, 16 checks) handed to the user. Final review BASE 2455f1a55e77d2bb2fdf482274b87e4b86134106
Final review: With fixes. Ruling: one fix wave of 7 items - the 4 folded items (cloak excluded from Reparo; Accio exempts hung/chained targets and gives players one impulse; map tile cap 150, range-circle grey-out and list room; cancel the channel on dimension change) plus protocol version 3, side-along skips hung/chained victims, and Ascendio water-cast fall immunity - all small and local - tuning only if wrong.
Final review: Ruling: carried to Phase 6a Task 0 - the Accio snatch skipped during victim i-frames (needs a claim-veto-safe distinction); deferred minors kept (wall probe height, decode clamp bytes, silent passengers, bound caster may start Apparition, Depulso slam on a hanging target).
Final fix wave: done (d882523); re-review dispatched
Final fix wave: re-review Ready (d882523). Phase 5b complete.
Phase 6a Task 0 carry-overs: (1) Accio Master snatch skipped during victim i-frames (needs a claim-veto-safe check, not a plain invulnerableTime test); (2) Depulso blockedAhead probes mid-height, misses walls for short mobs; (3) OpenApparitionMapPacket decode clamp does not drain extra entries; (4) side-along passengers that do not fit are skipped silently (add a message); (5) Depulso deflectProjectiles reads the owner rank every beam tick (cache at spawn); (6) Accio on a held mob fails silently (actionbar message).
