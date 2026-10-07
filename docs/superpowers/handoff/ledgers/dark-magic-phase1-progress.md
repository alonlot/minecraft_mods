# Ledger: Dark Magic Phase 1 + Sneakoscope Part B (plan docs/superpowers/plans/2026-10-08-dark-magic-phase1.md)

Base: 996f638 (383 tests green). Process: implementer per task, review per task, final review, one fix wave, re-review, push. User ruling (2026-10-07): Opus for ALL agents from now on (planners, implementers, reviewers, fix waves). The controller commits ledger lines by path only.

## Controller rulings
- Planner rulings 1-22 accepted as written.
- Spec drift accepted: the client gets the effective Darkness value instead of a config sync packet; the 10-minute quiet window is game time (passes offline), the 30-minute decay steps are online time only.

## Progress
- T1 done f6aa7c4 (404 tests, RED seen). Review and T2 dispatched.
- T1 review: Approved. Fix-wave queue: M1 config comment for darkMagicGainMultiplier says values under about 0.02 round small gains to 0, M2 gainMultiplierScalesTheWholeTable checks every dark spell, M3 tierKeysAndColours pins all five colours.
- T2 done 572d247 (404 tests, protocol 7, boot Done 3.588s). Review and T3 dispatched.
- T3 done b490777 (404 tests). Review and T4 dispatched.
- T2 review: Approved. Minors accepted with no action: extra PlayerPersisted reads per cast (per cast or per 200 ticks only), Forge config-upgrade WARNs, client state getters used from T5.
- T4 done 0ebfa31 (404 tests). Controller review (small task): matches the plan; op 2; set 0..100 ignores the floor, add -100..100 goes through it; get notes the toggle. Approved. T5 dispatched.
- T3 review: Approved; every paid dark cast path reaches exactly one hook, refusals, heartbeats, windup begin and creative charge nothing. Fix-wave queue: M1 castAs javadoc says mana payer = the victim, Darkness goes to the puppeteer. M2 (survival puppeteer charged for a creative victim's cast) accepted: the puppeteer chose to cast it. M3 old build-log notes, no action.
- T5 done 0140337 (404 tests). Review and T6 dispatched.
- T5 review: Approved (font widths measured: 78 px or more gap to the title at any GUI scale). Minors accepted with no action: narrow-screen clipping is the existing panel behaviour; fully qualified names are plan-mandated.
- T6 done bb232d5 (406 tests, RED seen). Its review is folded into the final review (stale class comment in SneakoscopeRules reported by the implementer goes to the fix wave).
- T7 (controller, cf3c424): clean build OK, 406 tests green, touched JSON valid, wiki/data.js parses, dedicated-server boot Done (3.742s) with no Exception or ERROR, server stopped. Final review dispatched.
- Final review: Approved (Task 6 fully reviewed there; Part B cost acceptable at scale; Cloak and Polyjuice do not hide a dark player). Fix wave (runs after Brewing P2 Task 1 commits): T1 M1-M3, T3 M1 castAs javadoc, SneakoscopeRules and SneakoscopeSensor class javadocs in the present tense, MOD_FEATURES_WIKI.md Sneakoscope line (a carried one never senses you, a placed one senses everyone; both config toggles turn it off). Accepted items as listed by the reviewer.
