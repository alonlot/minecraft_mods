# Piston locks (Alohomora and Revelio): handoff for the cloud

Branch: `claude/keen-lamport-g1ykt4` on GitHub. Plan: `docs/superpowers/plans/2026-10-03-alohomora-piston-locks.md` (binding; it carries the requirements, rulings and global constraints).

## State
- DONE by an earlier cloud session: the base `LockCircuit` and `LockSimulator`, plus `LockSimulatorTest` (8 tests) and `LockSimulatorExtraTest` (2 tests). Commits "Add a pure redstone lock simulator for lock-picking spells" and "Prime the lock simulator at construction and add a bounded settle". Do not rewrite them; Task 2 extends them.
- Also on the branch (local work since the last handoff): the Tome upgrade preview and rank-change text, Petrificus stone tint, possessed flyers that fly, and the Apparition focus time held at 4 s. These aren't part of this plan; leave them alone.

## What is left, in order
| Task | What | Notes |
|---|---|---|
| 1 | `LockRules` + test | Per-rank caps and timings; container item maths |
| 2 | Extend `LockCircuit` / `LockSimulator` + `LockCircuitGoalsTest` | PULSE and FORCE inputs, goals, dangers, keyed and truncated flags, `controllableGoals`, `goalPowered` and `dangerPowered`, widened `setInput` and `setLevel` |
| 3 | `LockSolver` + test | Fewest actions first; real switches before forced sources; never powers a trap |
| 4 | `CircuitTracer` | World → circuit; forceable sources (Alohomora Master) and item keys (Revelio Master) |
| 5 | `ForcedSignals` + `BlockStateSignalMixin` | Register in `hogwartsmagic.mixins.json`; check `runServer` for mixin apply errors |
| 6 | `PistonLockSpell` + Alohomora hook | Includes a `LockHints` stub so it compiles before Task 7 |
| 7 | `LockHints` + Revelio hook in `SpellBeamEntity` | Glowing block displays and text labels for 15 s; restart cleanup |
| 8 | `/lockinfo` + all lang text | `RankChangeTextTest` must stay green |
| 9 | In-game checklist | **The user does this locally.** Don't attempt it in the cloud; end the session by reporting the checklist |

Then: the final whole-plan Opus review, one fix wave, and one scoped re-review.

## Process
- Use the skill `superpowers:subagent-driven-development`. Sonnet 5.5 for implementers and fix rounds, Opus 5.5 for every review, never Haiku, and always set `model` explicitly.
- Every subagent reads `docs/superpowers/handoff/standing-rules/` first.
- Record rulings in the ledger and append them to `docs/superpowers/handoff/rulings.md` under a new "Piston locks" section. Decide, don't stall.
- Commit per task with the trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Never stage `.claude/settings.local.json`. No apostrophes in bash heredoc bodies.
- In the cloud there is no Minecraft client, so the "is javaw running" check always passes. Still run `./gradlew compileJava test` per task, and `./gradlew runServer` after Tasks 5 and 8 (boot to `Done (`, then `stop`).
- Push the branch after Task 4, after Task 8, and after the final fix wave.

## Report at the end
- Every ruling made.
- The `/lockinfo` output format, so the user knows what to send back.
- The Task 9 checklist, unchanged.
- Any place where the code had to differ from the plan, and why.
