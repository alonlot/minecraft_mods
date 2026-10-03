# Ledger: lock-picking hold (plan docs/superpowers/plans/2026-10-03-lock-picking-hold.md)

Base: 8adfe2a (pushed). Tasks 0-5 by SDD (Sonnet implementers, Opus reviews), final review, fix wave, push. Task 6 is the user's in-game checklist.

## Controller rulings on the planner's open points
1. Accept one S2C packet LockPickStatePacket (needed: client cannot know a read started; cooldown bar would overwrite progress). No C2S packet.
2. One cast per press for Alohomora and Revelio - accepted.
3. Slot-key cast held by its slot key - accepted.
4. Mana once at cast, no refund on release - accepted.
5. Released Revelio keeps its beam XP; Alohomora pays nothing - accepted.
6. Single solver thread kept; a queued caster shows ~?s and its 30 s cap starts when the worker takes the job - accepted.
7. Heartbeat timeout 6 ticks as Crucio - accepted (the client heartbeats every tick).
8. Sparks and piston sounds at the struck block - accepted.
9. "Reading" line dropped for OPEN/REVEAL - accepted.
10. M2 rule (door counts only if steady over the last simulated tick) and the M3 test shape - accepted.
11. OVERRULED: the planner's premise is wrong (rulings.md shows the version was bumped to 3, 4 and 5 when packets were added). Task 3 bumps networkProtocolVersion 5 -> 6, since a new packet shifts message ids.

## Pre-flight table
See the plan's pre-flight table (T0->T2, T1->T2/T4, T2->T4, T3->T4, T3 bridge names).

## Progress
- T0 dispatched (Sonnet implementer).
- T0 done 094d6ba (286 tests). Review dispatched (Opus); T1 dispatched.
- T1 done 9f63c24 (301 tests). RED step skipped (new class; RED would only be a compile failure) - accepted. Review dispatched. T2 waits for the T0 review (it edits LockSolver on T0's text).
- T0 review: Ready. Ruling: Minor 1 (three tests: held subset that never settles via runHeld; clock-driven trap during a hold -> NO_SOLUTION; flickering goal not counted) goes to the fix-wave queue. Minor 2 recorded: M2 also shortens slow-but-stable forced on-phases (>30 redstone ticks) to the world hold - correct, matches the 60-game-tick force. Minor 3 accepted (a period>=2 clock goal may count by chance; ruling-mandated); a comment goes into the fix wave.
- T2 dispatched.
- T2 done c7034a2 (306 tests, RED seen). Review dispatched; T3 dispatched.
- T1 review: Ready. Ruling: Minors 1 (etaSeconds capTicks<=0 guard like fraction), 2 (ceil with -1e-9) and 3 (six extra attempts/ETA tests) go to the fix-wave queue.
- T2 review: Ready. Ruling: Minors 1-3 (mixed lever/button/forced count test = 26, ANALOG domain and budget cut-off tests, pin attempts == 1 in aSolvedSearchStopsWithinThePlan) go to the fix-wave queue. Note for T4: Progress.work is stale during one long settle; the time term keeps the bar moving - accepted.
- T3 done 7de41c2 (306 tests, protocol 6, boot clean). Review dispatched; T4 dispatched.
- T4 done 3e2f9c1 (306 tests, boot clean). Review dispatched. T5 (final verification) waits for the T3/T4 reviews.
- T3 review: Ready. Ruling: fix wave takes M1 (send lock release only while a lock spell is still selected; on selection/wand change just clear the pending hold), M2 (LockPickClient onRenderHand at EventPriority.LOW plus !PossessionClient.isCaster(), fix javadoc), M3 (skip a second lock cast while lockHoldPending, whichever key), M4 (if (p.channelHeartbeat) return; before the cooldown check in castAs - a stray heartbeat must never cast or charge mana).
- T4 review: Needs fixes. I1 verified by controller (ModEventsClientHooks redraws the cooldown line once isPicking() is false, so end-of-pick lines last one tick). Ruling: one fix round (task-4-fix-brief.md) fixes I1 (40-tick quiet window), M1 (INSPECT deadline from worker start, 60 s queued ceiling), M2 (ETA >= 1 s), and folds every queued minor from T0-T3; M3 accepted. Then T5 verification is part of the final review package.
- T4 fix round done 8f10aa1 (319 tests, boot clean). Deviation accepted: aSolvedSearchStopsWithinThePlan asserts 3 (the solver finishes the one-action tier). Point 8 also stops a Protego Maxima heartbeat at a non-sky angle from falling through to a real cast - intended. Final review dispatched on bd2d1ab..8f10aa1; controller runs T5.
- T5 (controller, 8f10aa1): clean build OK, 319 tests 0 failures, lang JSON valid, client-reference grep identical to bd2d1ab, no switch in LockPickClient, jar build/libs/hogwartsmagic-1.0.0.jar; boot clean per the fix-round run at the same commit. Waiting on the final review.
- Final review: With fixes. I1 verified by controller (radial path has no startLockHold). Ruling: fix wave (fixwave-brief.md) - hold start moves into castSelectedSpell with a key param, radial casts get a 20-tick grace to press right-click; M1 latch above mana check; M2 resend picking=false at playback end to reopen the quiet window. Dispatched.
- Fix wave done 9873820 (319 tests, boot clean). Accepted extras: a failed slot/radial cast no longer starts a hold; a right-click takeover of a radial hold does not set the use latch. Pushed. Scoped re-review dispatched.
- Re-review: Ready. Nit accepted (radial re-pick during grace with low mana shows Not enough mana!, no cast). Hold-to-pick run complete.
