# Feature audit (2026-11-04)

Every feature area was checked against rulings.md, the ledgers, plans, specs, FINAL-REPORT.md and
MOD_FEATURES_WIKI.md, then fixed where it did not work as intended. One report per area is in this
folder. Script paths in the reports point to a scratch folder that no longer exists.

## How it was verified

| Check | At HEAD before the audit | After the audit |
|---|---|---|
| `./gradlew compileJava test` | pass, 0 warnings | pass, 0 warnings, 340 tests |
| Dedicated server boot | clean | clean, all mixins apply |
| Server smoke: summon all 35 mod entities, roll all loot tables, `/buildbank`, `/spawnminihogwarts`, `/spawnalley`, `/wizardraid`, `/summonhorntail`, `/reload` | no exceptions | no exceptions |
| Horntail Peaks surface at seed 12345, (-224, 288) | grass_block (surface rules never ran) | blackstone |
| Headless client (Xvfb + Mesa) resource load | 20 missing Floo blockstate variants, no Patronus light blockstate, 8 missing Willow sounds, 2 invalid texture names | only the 8 Willow sounds remain |
| Headless client: Singleplayer menu, open the populated smoke world, die, respawn, 3 min with all mod entities, bosses and structures loaded | not run | no exceptions on client or server |
| Built jar refmap (`./gradlew build`) | the biome and Cave Inimicum mixins had no SRG entries | all mapped (for example `addBiomes` to `m_187175_`) |

Note: starting the client with `--quickPlaySingleplayer` crashes on the first loot roll ("Can not
retrieve LootModifierManager until resources have loaded once") at HEAD and after the audit alike.
The mod does not touch reload events; the likely cause is that quick play loads the world before Forge
starts its event bus, so the event is dropped (not proven). Opening the same world from the
Singleplayer menu works, so treat it as a Forge quick-play issue, not a mod defect.

Nothing was played by hand. The in-game checklists in FINAL-REPORT.md are still to be run.

## Fix count by area

| Area | OK | Fixed | Open |
|---|---|---|---|
| core-magic | 26 | 10 | 5 |
| combat-spells | 14 | 10 | 8 |
| mind-spells | 16 | 5 (8 fixes) | 0 defects |
| support-spells | 16 | 7 (11 fixes) | 8 |
| locks | 19 | 4 | 3 |
| creatures-a | 21 | 15 (16 fixes) | 2 |
| creatures-b | 9 | 15 | 6 |
| blocks-items | 15 | 16 (19 fixes) | 11 |
| worldgen | 24 | 18 (16 fixes) | 3 |
| client | 18 | 6 | 3 |

Controller fixes after the area pass: all 32 Floo Fireplace blockstate variants (20 were missing),
`remap = false` removed from the two Cave Inimicum client mixins (a reobfuscated jar could not find
the SRG method names, and `defaultRequire: 1` makes that a crash), the five mind-spell marker effects
hidden from the inventory, the ruin camp portraits turned to face out of the wall, a 2 s window that
stops the wand cooldown line from overwriting server action-bar messages, Aqua Eructo water removed
on server stop, two unused book art files moved to texture_proposals/book_sources.

## Decisions for the owner

1. Broom boost now works (before, the rider's client never saw the boost flag). Its numbers are
   unchanged: 6.5 / 8 / 10 blocks per tick, which is 130 to 200 blocks per second. That is faster
   than chunks load. Recommend lowering it.
2. Wand "spell power" only changes trail particles, but the wiki says Dragon and Elder wands hit
   harder. Wire it into damage (balance change, 60 to 150 lines) or fix the wiki.
3. Whomping Willow is silent: the 8 .ogg files were never committed. Run
   `build_scripts/generate_whomping_willow_sounds.py` on the PC that has the ElevenLabs key and commit
   `src/main/resources/assets/hogwartsmagic/sounds/whomping_willow/`.
4. Levicorpus spellbook has no icon art (it shows the plain book). Needs a texture, then 3 small edits.
5. `flying_mob` is a leftover test entity with no texture or name. Remove it or give it art.
6. Diagon Bricks have no survival source, so Diagon Alley is creative or op only.
7. A wand cast at a chest, door or lever also uses that block on the server.
8. An off-hand wand works in the client menus but the server ignores it.
9. Accio and Wingardium can still move players who have PvP off (only snatch and throw check PvP).
10. PvP-off for Stupefy, Petrificus, Levicorpus, Incarcerous and Glacius (open since FINAL-REPORT).
11. Giant, Goblin, House Elf, Horntail and Ironbelly drop nothing.
12. Merperson out-of-water grace changed from 15 s to 5 min to match its comment and spec. Revert
    if 15 s was meant.
13. `function/place_skyblockhogwartshacxs_chunks.mcfunction` places 544 templates the mod does not
    have. Delete it unless those templates live somewhere else.
14. The Diagon shop and Knockturn chest loot tables were missing and were written from existing items.
    Review their contents.
