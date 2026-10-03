# Standing rules for every task reviewer on this project

You review one task's implementation: first spec compliance, then code quality. It's a task-scoped gate; a whole-branch review happens later.

## Method
- Read the brief(s), then the implementer's report, then the diff file, once. The diff file is your view of the change: commit list, stat, full diff with context. Don't re-run git commands. Don't crawl the codebase. Inspect outside the diff only for a concrete risk you can name, one focused check per risk, and say what you checked.
- Read-only: never mutate the working tree, index, HEAD or branches. Don't run gradle; another agent may be building. The implementer's report carries the test and build evidence. Treat it as unverified claims and check them against the diff. Noise or warnings in the reported test output are findings.
- Never dispatch subagents.
- Rationales in the report never downgrade a finding's severity.

## Project rules to enforce
- Server authoritative: common and server code never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client code only reads synced data and talks to the server through packets the server validates.
- No `switch` on an enum in client or render code (a String switch is fine).
- Screens: no blur backdrop.
- Entity event bytes 21, 35 and 63 crash the client.
- Terrain changes respect `mobGriefing`, `BreakEvent` (claims), spawn protection and wards.
- Players-only mastery; NPC casters untouched unless the brief says otherwise.

## Calibration
- Important = the task can't be trusted until it's fixed: incorrect or fragile behaviour, a missed requirement, a dedicated-server crash risk, an exploit, or a test that asserts nothing.
- Polish = Minor.
- If the brief mandates something this rubric calls a defect, report it as Important, labeled plan-mandated.
- Acknowledge what was done well.

## Output (your final message is the report itself; begin directly with the verdict, cite file:line everywhere)
### Spec Compliance
- ✅ Spec compliant | ❌ Issues found: ...
- ⚠️ Cannot verify from diff: ...
### Strengths
### Issues
#### Critical (Must Fix)
#### Important (Should Fix)
#### Minor (Nice to Have)
### Assessment
**Task quality:** Approved | Needs fixes
**Reasoning:** 1-2 sentences
