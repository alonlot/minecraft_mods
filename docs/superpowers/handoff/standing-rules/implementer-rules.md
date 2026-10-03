# Standing rules for every implementer on this project

Repo: C:/Users/alonl/OneDrive/Desktop/harry potter mod. Branch `spell-mastery`; stay on it and never rewrite history.
Stack: Minecraft Forge 1.21 (Forge 51.0.33), Java 21.

## Process
- Read your brief(s) first. They are your requirements, with exact code and values to use verbatim. Where the brief's line numbers have drifted, find each spot by its code text.
- If something is unclear before you start, reply NEEDS_CONTEXT with your questions instead of guessing.
- TDD where the brief says so: write the test, watch it fail, implement, watch it pass.
- Never dispatch subagents or reviewers. Review is the controller's job.
- Self-review your whole diff before reporting: completeness against the brief, nothing extra, existing patterns followed, server/client safety.

## Project rules (binding)
- Server authoritative. Common and server code (spell/, spell/mastery/, network/, event/, command/, util/, entity/, item/, block/, loot/) never references `com.hogwartsmagic.client.*` or `net.minecraft.client.*`. Client-only code lives in `client/` and is reached through `util/ClientSideBridge` → `client/ClientRuntimeBridge` string dispatch.
- No `switch` on an enum in client or render code (the synthetic `$1` class fails to load in this dev env). A switch on a String is fine. if-chains, EnumSet and EnumMap are fine.
- Custom screens use `renderTransparentBackground` / no blur. The Grimoire overrides `renderBlurredBackground` as a no-op; mirror that.
- Entity event bytes 21, 35 and 63 crash the client. Use 61/62/64/66/67 for any new `broadcastEntityEvent`.
- Terrain changes respect `mobGriefing`, `BlockEvent.BreakEvent` (claims), spawn protection and Cave Inimicum wards.

## Building
- Before ANY gradle run: `tasklist | grep -i javaw`. If a Minecraft client is running, stop and report BLOCKED. Never run gradle while the client runs.
- Build and test: `cd "C:/Users/alonl/OneDrive/Desktop/harry potter mod" && ./gradlew compileJava test` (10-minute timeout).
- Validate en_us.json after editing it: `python -c "import json;json.load(open(r'src/main/resources/assets/hogwartsmagic/lang/en_us.json',encoding='utf-8'))"`. Validate any JSON you create the same way.
- Dedicated-server boot, when the brief asks for one:
  ```
  cd "C:/Users/alonl/OneDrive/Desktop/harry potter mod"
  rm -f run/logs/latest.log
  (./gradlew runServer > "$TEMP/srv-check.log" 2>&1 &)
  for i in $(seq 1 120); do sleep 5; grep -q "Done (" run/logs/latest.log 2>/dev/null && break; grep -qi "Exception\|Crash" run/logs/latest.log 2>/dev/null && break; done
  grep -n "Done (\|Exception\|ERROR" run/logs/latest.log | head -40
  powershell -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'runServer|forge|bootstraplauncher' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"
  ```
  Then confirm no server java is left (`tasklist | grep -ci java.exe` prints 0). If it never reaches "Done (", report the log.

## Committing
- One commit per task, using the brief's subject. The message ends with a blank line and then exactly:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Avoid apostrophes inside bash heredoc bodies (they break the command).
- Stage only the files you changed. Never stage `.claude/settings.local.json`. Never push.

## Reporting
Write the full report to the report path the controller gives you. Include: what you implemented, TDD evidence (RED and GREEN commands with output) where applicable, build/test/server results with log lines, files changed, self-review, concerns.
Then reply with ONLY (under 15 lines): Status (DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT), commits (short SHA + subject), a one-line test/build summary, concerns, and the report path.
After review findings you'll be resumed: fix, re-run the covering tests, append a fix report (changes with file:line, commands, output) to the same report file, and reply with the same short contract.
