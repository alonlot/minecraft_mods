# Ledger: Colloportus (plan docs/superpowers/plans/2026-10-03-colloportus.md)

Base: 5d716bc (texture + generator). Tasks 1-4 by SDD (Sonnet implementers, Opus reviews), final review, fix wave, push. Task 5 is the user's checklist.

## Controller rulings on preflight decisions 1-25
- Accepted as written: 1-7, 9-11, 13-25.
- 8 CHANGED: a seal breaks when the Alohomora rank is strictly higher, OR when both are Master (rank 4). Reason: otherwise a Master seal is unbreakable forever, which is griefing-grade. Cost: one condition in SealRules.decide plus a test.
- 12 CHANGED: add an op command `/colloportus clear [radius]` (permission level 2, default radius 8, max 64) that removes every seal covering any block within the radius of the executor's position in the executor's dimension, and prints the count. Seals still outlive the door. Cost: one small command class registered next to /lockinfo.
- Out of scope, reported: the Apparition book (custom model data 37) has no model override and shows Reparo's icon.

## Progress
- T1 dispatched.
- T1 done 4d20f70 (335 tests, RED seen, 16 tests incl. two for ruling 8). Review and T2 dispatched.
- T1 review: Ready. Minors 1 (TOP_RANK in index()), 2 (distinct createdAt in oldest-of-equals test), 3 (breaks() unclamped, harmless) -> fix-wave queue. Plan text after T1 still states old ruling 8 (lang desc and rank.4 lines, checklist row 12): T2 implementer told by message; checklist row 12 now expects the Master seal to break.
- T2 done f110d07 (335 tests, boot clean, lang lines per ruling 8). Review and T3 dispatched.
- T3 done 8e1be32 (335 tests; no boot run - controller boots in T4). Clear command uses literal strings like /lockinfo - accepted. Review dispatched.
- T3 review: Ready. Ruling: fix-wave queue takes M1 (a door counts as sealed if either half is covered: lockPositions adds the other DoorBlock half, openAround tests both halves), M2 (clear command uses source.getLevel() and BlockPos.containing(source.getPosition()) so console/command blocks/execute work), M3 (LockInfoCommand spacing; Revelio seal reveal gets the hitPuppeteer()==null guard like the piston hints).
- T2 review: Ready. M1 Bookseller sells it at level 3 (Journeyman), consistent with the existing advanced list - accepted, checklist updated. M3 stale counts - known.
- Ruling on T2 M2 (design gap): a seal cast at a piston lock's button far from the pistons did not cover the pistons, so an Alohomora struck at a piston passed. Fix wave: Seal gains anchors (the door pistons traced when the seal is made, at most 16, empty for plain doors); a lock position is covered when it is within radius of the centre or of any anchor. Saved in SealData (missing tag = no anchors), used for the gate, Revelio, /lockinfo, lifting and the clear command. Cost: a record field, NBT list, tests.
- T4 (controller, 8e1be32): clean build OK, 335 tests, JSON valid, dedicated-server boot Done (3.201s) with no ERROR/Exception; server stopped.
- Final review: Ready once the queue lands; Minors M1 (cheap gate), M2 (outline cap), M3 (refuse only real locks - ruled yes, a wall inside a seal behaves as normal), M4 (eviction tiebreak) added. Fix wave dispatched (fixwave-brief.md).
- Fix wave done 2432aa0 (339 tests, boot clean). Item 7 already held (strict < over list order) - documented only. Anchor NBT round-trip untested (no MC-free test) - in-game row. Pushed; scoped re-review dispatched.
- Re-review: Ready. Accepted notes: an area Alohomora at a plain wall beside a sealed door skips that door (no break); double piston scan near a seal; SealData rank clamp literal; 16-anchor cap vs 32-piston doors; save-data anchors trusted. Colloportus run complete.
