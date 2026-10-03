# SDD ledger - plan: docs/superpowers/plans/2026-10-02-spell-mastery-phase6a.md

Spec: roadmap Phase 6 (part a: Obliviate, Confundo, Avada, Crucio, Imperio thralls). Branch spell-mastery (Phases 1-5b done, HEAD c2efae6). Models: Sonnet implementers, Opus reviewers.

## Pre-flight
See preflight.md (opus). Blocking anchor fixes are in the briefs: T3 hook goes in the 4-arg applyHitEffect; T5 windup cancels from CombatEffects.onEffectAdded and DisableSupport.interrupt, not onLivingHurt; T6 anchor after `ItemStack wand = player.getMainHandItem();` in interruptActiveCasting. Rule-conflict corrections (magicFrom damage, no persistent data on every mob, isLoaded in teleportNear, LOWEST Imperius-removal handler, priorities) are in the briefs.
Ruling: player thrall movement sends velocity only on change plus a resend every 4 ticks - binding no-per-tick-packets rule - cost: slightly looser thrall steering at high ping.
Ruling: keep the plan's green particles instead of the spec's green glow outline - vanilla glow is team-coloured and per-team colour would fight scoreboard teams - spec drift, reported to the user for sign-off - cost: a cosmetic change later.
Ruling: an Imperio I-III cast that makes no thrall starts no cooldown and earns no XP - consistent with refused Accio pulls - cost: none.
Ruling: bump networkProtocolVersion to 4 in Task 8 (it adds a packet) - no cost.
Ruling: Task 0 (carried from the Phase 5b final review) runs in parallel with Task 1 (new files only); Task 2 waits for both.

## Progress
Task 0: BASE c2efae6; implementer dispatched (sonnet). Task 1: implementer dispatched (sonnet) in parallel (new files only).
Task 1: implemented a024ea1; review dispatched
Task 1: complete (a024ea1, review approved).
Task 1: minor (deferred): RankIndex direct tests (negative, MIN_VALUE); Imperio rank 4 untested; bossDamage no guard; fresh/heartbeatLapsed overflow with Long.MIN_VALUE or future timestamps (callers must default to 0 - told to integration tasks); weak Confundo asserts; Crucio ranks II/III untested.
Ruling: Phase 6b must add a dedicated possessionTicks(boss) rule (20s mob, 5s boss) instead of reusing thrallTicks(4, ...) - carry into the 6b pre-flight - cost: wrong possession duration.
Task 0: implemented c898a0c (DONE_WITH_CONCERNS: Apparition-spell side-along has no passenger collision check); review dispatched
Task 0: review Needs fixes (Apparition-spell side-along places passengers with no collision check). Ruling: fix it like the Floo path plus splinch only moved passengers; fold Minor 1 (DecoderException above 64, bounded readUtf) - binding rule - no cost.
Task 0: Ruling: the beam chip source is damageSources().magic() with no attacker, so claim mods keyed on source.getEntity() can not see Accio hits as PvP - carry to the Phase 6a final review as a whole-branch check (switch to indirectMagic only after checking kill credit and mastery effects) - cost if wrong: a claim mod may miss a PvP snatch.
Task 0: fix round 1/5 dispatched
Task 0: fix round 1 done (8d4e5b5) plus controller one-liner 45087d1 (readUtf(64) reverted to the default: names come from anvil renames and SetFlooNamePacket with no 64 cap, so a long name would disconnect the client). Re-review dispatched.
Task 0: minor (deferred, pre-existing): SetFlooNamePacket has no server-side name length cap.
Task 0: complete (c2efae6..45087d1, re-review approved). Task 2: BASE 45087d1; implementer dispatched (sonnet)
Task 2: implemented fe9d74e; review dispatched. Task 3: BASE fe9d74e; implementer dispatched in parallel with the T2 review
Task 3: Ruling: Obliviate forget state lives in a server-only in-memory map (lost on reload or restart, max 30s) instead of persistent data on every mob - binding perf rule - cost: a reloaded mob remembers you early.
Task 3: implemented a06ce48; review dispatched. Task 4: BASE a06ce48; implementer dispatched in parallel
Task 2: review Needs fixes (mod C2S packets skip the controlled guard: Floo, both Apparition picks, UseTome, SelectSpell/SetSpellSlot, Equip/UnequipRelic, BroomBoost/Trick). Ruling: add `if (MindControl.isControlled(player)) return;` to each (Apparition picks cancel the channel first), not inside isCastingBlocked (castAs uses it); fold Minor 2 (castAs javadoc) and Minor 4 (clear ROOTED on PlayerLoggedInEvent with a cached holder) - no cost. Queued after the Task 4 implementer (avoid concurrent commits).
Ruling: carry into the Phase 6b pre-flight - (a) vanilla inventory clicks on the player's own InventoryMenu and hotbar changes are not refused for a controlled player (castAs reads the victim's main hand); (b) castAs must make the body vs payer roles explicit for possession.
Ruling: Task 6 (Crucio) must keep the caster rooted for the whole channel even if ROOTED is removed by milk (re-apply each heartbeat or end the channel) - added to the task-6 brief.
Task 4: implemented dd1b754; review dispatched. Task 2: fix round 1/5 dispatched
Task 2: fix round 1 done (88edb7c); re-review dispatched. Task 5: BASE 88edb7c; implementer dispatched
Task 2: complete (fe9d74e + 88edb7c, re-review approved)
Task 4: review Needs fixes (plan-mandated: brain-driven mobs - piglin, hoglin, zoglin, breeze, axolotl, warden - read ATTACK_TARGET from the brain, so Confundo retargeting does nothing and they keep attacking the caster; memory-less brain mobs scan every tick).
Task 4: Ruling: in confuse/retarget set the ATTACK_TARGET memory too when the mob has that module (erase on clear), throttle the scan to the retarget interval when the last search found nothing (Minor 1), and correct the lang text (Minor 6) - no cost. Queued after the Task 5 implementer. The same brain check goes to the Obliviate (Task 3) review result and the Task 7 (thrall) brief.
Task 3: review Needs fixes (brain-AI mobs keep their ATTACK_TARGET/ANGRY_AT memories so Obliviate does nothing mid-fight; plan-mandated: the Master raid wipe still lets the last kill grant Hero of the Village).
Task 3/4: Ruling: one combined fix round with a shared MindControl brain helper (clearBrainTarget / setBrainTarget, Warden clearAnger), the wizard raid gets an obliviated flag that denies the Hero reward, vanilla raids stay vanilla (Obliviating the last wave gives a vanilla victory - accepted as vanilla behaviour), fold Minor 1 (demote vanilla captains and the wizard banner) and Minor 3 (isClientSide first) - cost if wrong: one helper to adjust.
Task 3: Ruling: a resisted Obliviate or Confundo still interrupts a boss cast, consistent across mind spells - cost: Voldemort casts get interrupted by spells he resists.
Task 3/4: combined fix round queued after the Task 5 implementer.
Task 5: implemented 7bf7c6d (DONE_WITH_CONCERNS: death mid-windup cleanup relies on the tick isAlive check); review dispatched. Task 3/4 combined fix dispatched (sonnet, the Task 3 implementer).
Task 3/4: combined fix done (f014265); re-review dispatched. Task 6: BASE f014265; implementer dispatched
Task 3/4: re-review - A-F addressed; one Important left: a forgetting Warden re-aggros through Roar.stop -> setAttackTarget with no event. Ruling: fix it (cheap tick check only while FORGOTTEN is non-empty, Wardens only), fold Minor 1 (clearBrainTarget gets the responsible owner) and Minor 2 (purge expired NEXT_SEARCH entries on put) - no cost. Fix round 2/5 dispatched in parallel with the Task 6 implementer (no shared files; index.lock retry).
Task 5: review Needs fixes (a reflected Avada makes the reflecting defender pay the backlash).
Task 5: Ruling: pass masteryEligible into AvadaSpell.apply and skip the backlash on a reflected beam (kill credit stays with the reflector); fold Minor 1 (static Set<UUID> of winding-up players instead of a per-tick persistent-data read on every player) and Minor 2 (backlash only when target.hurt landed); Minor 3 (wand swap keeping the windup) accepted - nothing gained. Queued after the Task 6 implementer (both edit SpellType; staging a shared file mid-edit would capture the other's work).
Task 3/4: fix round 2 done (6e1c7a7); re-review dispatched
Task 6: implemented 590a1c7 (CrucioChannel.java itself landed inside 6e1c7a7 because the parallel fix staged spell/mind/). Ruling: no more parallel implementers on this branch - one implementer at a time, reviews may overlap - cost: slower wall clock.
Task 6: Ruling: the Crucio caster losing ROOTED ends the channel (implementer choice) - no cost. Review dispatched; the Task 5 fix round dispatched after it (sole implementer).
Task 3/4: re-review of round 2 - all fixes correct, no regressions; the only finding was the swept CrucioChannel.java. Ruling: do not split the commit (no history rewrite); the file is routed in full to the Task 6 review diff - no cost.
Task 3: complete (a06ce48 + f014265 + 6e1c7a7, re-review approved). Task 4: complete (dd1b754 + f014265 + 6e1c7a7).
Task 3/4: minor (deferred): no unit test for MindHooks.isHostile; Accio does not break the forget; Confundo target choice ignores line of sight; Confundo clear() overwrites concurrent mind targets; reflected Confundo uses the reflector rank.
Task 5: fix round 1 done (97cc8dd). Controller correction: the review's Important (reflected Avada backlash) was a false positive - ProtegoSpell.java:88 returns NOT_SHIELDED for Avada before any reflect, and ProtegoSpell.reflect is the only markReflected caller, so Avada can never be reflected. Ruling: keep the masteryEligible guard as harmless future-proofing; the two folded minors are real fixes. Lesson: the controller verifies a claimed exploit path in code before dispatching a fix.
Task 5: re-review dispatched.
Task 5: complete (7bf7c6d + 97cc8dd, re-review approved; the reviewer withdrew the reflected-Avada Important).
Task 5: minor (final wave): reword the AvadaSpell javadoc on the reflected-beam guard as future-proofing. Deferred: a second wand with Avada selected keeps the windup.
Task 6: complete (CrucioChannel.java in 6e1c7a7 + 590a1c7, review approved).
Task 6: Ruling: Crucio keeps living-hit XP - roadmap Decision 6 does not list it among self/channel spells - no cost.
Task 6: minors for the final wave: double end() in one tick (CHANNELS.remove(id, channel) guard); a player target who changes dimension keeps the stun (look the target up via the player list); stale class javadoc about bosses. Deferred: end() strips any ROOTED (guard needed once 6b roots too - carry to 6b pre-flight); FLEEING entries for unloaded mobs (sweep); report/code wording on clearBrainTarget.
Task 7: BASE 97cc8dd; implementer dispatched (sonnet)
Task 7: implemented a89ebf5; review dispatched. Task 8: BASE a89ebf5; implementer dispatched (review overlaps)
Task 8: implemented 357b99d (protocol 4; two-client rubber-band check deferred to the user); review dispatched
Task 8: review Needs fixes (the thrall nameplate is only cleared in release(); death, respawn (same entity id), portal and leave-level paths leave "Thrall of X" on the player for the session).
Task 8: Ruling: clear labels client side on EntityLeaveLevelEvent and ClientPlayerNetworkEvent.Clone, and send the empty label from one shared server helper on every thrall drop path; fold Minor 1 (do not force the nameplate on invisible or sneaking thralls - it would reveal the Invisibility Cloak) and Minor 3 (one predicate) - no cost. Queued with the Task 7 fix round (both touch ImperioThralls).
Task 7: review Needs fixes - C1 (server crash: the Imperius remove handler removes effects inside removeAllEffects iteration; controller verified in bytecode), I1 PvP-off ignored, I2 boss/player thrall refreshed forever at Expert, I3 friendly fire on the owner, I4 lava/drop for player thralls, I5 no tests.
Task 7/8: Ruling: one combined fix round (both touch ImperioThralls) per task-7-8-fix-brief.md; deferred: owner logout skipping the cooldown, rival player thralls, side effects on a refused cast.
User instruction (2026-10-03): leave out Phase 7a for now. Ruling: order is 6a, 6b, 7b - the 7b plan says the halves share no code - no cost.
Task 7/8: fix round 1 done (566dc84, e69d96c); re-reviews dispatched
Task 8: complete (357b99d + e69d96c, re-review approved). Controller checked: the Invisibility Cloak uses player.setInvisible(true), so isInvisible() hides the label for cloaked thralls.
Task 7: re-review - C1/I1/I3/I4 fixed; R1 left (boss/player thralls chain at Expert because the cooldown starts only at zero thralls; controller verified). Ruling: per-victim lockout of Imperio's cooldown length after a boss/player thrall ends, plus Strength-until and a PvP-refusal message. Fix round 2/5 dispatched.
Task 7: fix round 2 done (b5c1c65); re-review dispatched
Task 7: complete (a89ebf5 + 566dc84 + b5c1c65, re-review approved). Minor (deferred): mana spent on a locked-out cast; two bosses can be alternated.
Task 9: tests 188/0, server Done (2.502s) clean, greps clean (no client refs in spell/mind, no enum switch, JSON valid, persistent-data reads all gated). In-game checklist (21 items) handed to the user. Final review BASE c2efae6 dispatched.
Final review: With fixes (I1 thralls ignore stuns/holds; I2 PvP-off ignored by the mind spells; M1-M5). Ruling: one fix wave per fixwave-brief.md, plus item 8 (controller, from the user's rule that Protego can not block Avada): legacy NPC Protego (WizardEntity.hurt and the onLivingHurt PROTEGO branch) currently blocks Avada damage; make Avada pass every shield.
Final review: Ruling: Crucio stunning bosses for half the channel matches the Phase 2 boss rule (CC half on bosses) - kept; checklist item 11 corrected to "it takes damage and is stunned for half the channel".
Final review: Ruling: keep the beam chip as damageSources().magic() (attributed chip would add thorns, golem/pack aggro and team friendly-fire to every beam) - the Accio snatch keeps its pvp/canHarmPlayer gate.
Final review: for the user's sign-off at the end - Death Eaters can enthrall players for 3s (follows Decision 12); a branch-wide PvP-off gate for the Phase 3 crowd-control spells.
Final fix wave: done (5a4fbed); re-review dispatched
Final fix wave: re-review Ready (5a4fbed), all 8 items closed. New minors N1 (the Avada LANDING flag is per thread, so synchronous side damage such as Thorns also skips Protego - make it per target) and N2 (Master Confundo contagion still confuses thralls). Ruling: both go to Phase 6b Task 0 with the review's leftovers (Crucio on thralls gets a resists line and no XP; DisableSupport.npcFoe excludes the owner and the owner's thralls; the stale Avada hurt-site comment). Phase 6a complete.
