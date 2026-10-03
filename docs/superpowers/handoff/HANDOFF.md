# Spell Mastery: handoff for continuing in the cloud

Branch: `spell-mastery` (Forge 1.21 / 51.0.33, Java 21). Binding spec: `docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md`.
Idea source (lower authority): `docs/superpowers/specs/2026-10-02-spell-mastery-design.md`.
Plans: `docs/superpowers/plans/2026-10-02-spell-mastery-*.md`.

## Status
- DONE (built, reviewed, final-review fix wave done): Phase 1, 1b (tomes), 2, 3, 4, 5a.
- Phase 5b (`...-phase5b.md`): Tasks 1-6 DONE and review-approved (Reparo, Accio, Ascendio, Depulso, Apparition range/channel/splinch/map). Ledger: `ledgers/phase5b-progress.md`.
  - REMAINING: Task 7 (Apparition Master: jump to known Floo fireplaces; brief copy in `ledgers/phase5b-task-7-brief.md`), Task 8 (verification + in-game checklist), then the final whole-phase Opus review and ONE fix wave plus one re-review.
  - The final fix wave MUST include these folded items:
    1. Exclude the Invisibility Cloak from Reparo item repair (Magical Silk stays its repair material).
    2. `ModEvents.tickAccioPull` sends a velocity impulse to players every tick for 70 ticks and fights the Incarcerous tether and the Levicorpus hang. Make it a single impulse or exempt held targets.
    3. `ApparitionMapScreen`: cap tiles at about 150 per side (a Master map at GUI scale 1 does about 160k fills per frame), grey out tiles beyond the range circle.
    4. Cancel an Apparition channel on `PlayerChangedDimensionEvent`.
  - Deferred minors (only if cheap): Ascendio water-cast gives no fall immunity below Master; the Accio snatch is skipped during the victim's i-frames; Depulso wall probe is mid-height for short mobs.
- NOT STARTED: Phase 6a (mind spells, Imperio thrall), 6b (Imperio possession; its spike task comes first), 7a (Patronus forms, snake, Fiendfyre), 7b (Wingardium blocks, Arresto, Tenebris, Alatus, broom bench fix). Plans exist for all of them.

## Process (the skill `superpowers:subagent-driven-development`)
- Models: Sonnet 5.5 for implementers and fix rounds, Opus 5.5 for every review (task reviews, re-reviews, final reviews). Never Haiku. Always set `model` explicitly.
- Standing rules for subagents are in `standing-rules/` (implementer, reviewer, planner). Every dispatch tells the agent to read them first.
- Cumulative rulings are in `rulings.md` (one section per phase). Append new rulings there and to the ledger as `Ruling: <what> - <why> - <cost if wrong>`. Decide, don't stall.
- Workspace: `.superpowers/sdd/<plan-basename>/` is git-ignored scratch. For 5b, copy `ledgers/phase5b-progress.md` to `.superpowers/sdd/2026-10-02-spell-mastery-phase5b/progress.md` first. Generate briefs with the skill's `scripts/task-brief PLAN N` and diffs with `scripts/review-package PLAN BASE HEAD`. If the scripts are unavailable, cut the task text from the plan and use `git diff BASE..HEAD` into a file.
- Per task: record BASE, dispatch Sonnet implementer with the brief, build the review package, dispatch Opus reviewer with named risks, fix rounds resume the same implementer, scoped Opus re-review, log completion in the ledger. Leftovers from a final review that cannot get a second wave carry into the next phase as "Task 0".
- Per phase: create a ledger with a pre-flight table (plan tasks vs shared files and interfaces), check that the plan's anchors still match the code after the previous phase, run a final Opus review, one fix wave, one re-review, save rulings to `rulings.md`.

## Binding project rules
- Server authoritative; common/server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code is reached through `ClientSideBridge` to `ClientRuntimeBridge` string dispatch.
- No `switch` on an enum in client/render code. Screens use `renderTransparentBackground` (no blur).
- Entity event bytes 21/35/63 crash the client; use 61/62/64/66/67.
- Never run gradle while a Minecraft client runs. Build: `./gradlew compileJava test`; dedicated-server boot check via `./gradlew runServer` as in the implementer rules.
- No per-tick `getPersistentData` reads on every entity; no per-tick velocity packets to players; teleports/snaps only into collision-free space; never load chunks (`isLoaded` first); death/effect handlers at LOWEST priority with an `isCanceled` guard; reflected beams earn nothing.
- Commit per task. Stage only named files; never stage `.claude/settings.local.json`. Avoid apostrophes in bash heredoc bodies.

## To report to the user at the end
- Spec-drift sign-off: Incendio spread from Adept, Confringo splash from Adept, Bombarda obsidian 25% at Expert, Sectumsempra burst without consuming stacks.
- Tome name colours (white/yellow/aqua/light purple, not the Grimoire tier colours); NPC Heal buff (playtest).
- All rulings (`rulings.md` plus ledgers) and the in-game checklist of every phase.
- Then run `superpowers:finishing-a-development-branch`.

## Exact prompt to paste in the cloud session
```
Continue the Spell Mastery build on branch spell-mastery. Read docs/superpowers/handoff/HANDOFF.md first, then docs/superpowers/specs/2026-10-02-spell-mastery-roadmap.md. Use the superpowers:subagent-driven-development skill. Phase 5b Tasks 1-6 are done; resume at Task 7 of docs/superpowers/plans/2026-10-02-spell-mastery-phase5b.md, then Task 8, then the final review and fix wave (HANDOFF.md lists the folded fixes). After that do Phases 6a, 6b, 7a, 7b in order, each with the same process. Use Sonnet 5.5 for all coding subagents and Opus 5.5 for all reviews, never Haiku. Do not stop between tasks or phases; make rulings instead of asking. Commit per task with the trailer Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>, never stage .claude/settings.local.json, and push the branch after each phase. At the end, give me the rulings, the spec-drift sign-offs and the in-game checklists.
```
