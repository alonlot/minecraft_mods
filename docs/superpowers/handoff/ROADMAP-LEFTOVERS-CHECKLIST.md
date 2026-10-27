# Roadmap leftovers: in-game checklist

Branch `roadmap-leftovers` (from `sorting-memory`). Plan: `docs/superpowers/plans/2026-10-27-roadmap-leftovers.md`. This is the user's own pass: the unit tests and the 23 `LEFTOVERS-GAMETEST` gametests passed, but they cannot show feel, visuals or multi-player behaviour.

Commands: `/spellmastery setrank @s <spell> <1-4>`, `/darkmagic set @s <n>`, `/patronus form <1-8>`, `/patronus <player> <message>`.

## The checklist

1. **Fiendfyre I**, 10 casts: about 1 in 7 has a serpent glow red with "writhes" a second before it turns and chases you. Creative players are never chased.
2. **Fiendfyre III:** two serpents. **IV:** three, never rogue. Left-click while holding sends one at the zombie you aim at. Aimed at a friend with PvP off, nothing happens. (The dispatch itself has no gametest.)
3. **Fiendfyre IV** next to an active Horcrux: "devours a Horcrux", and the Horcrux breaks with its usual effects. (No gametest.)
4. **Fire and the land:** with `mobGriefing false` an NPC Fiendfyre places no fire. In spawn protection or a ward, player Fiendfyre places none.
5. **Serpensortia I/II/III:** 1/2/3 snakes that bite with poison (Poison II at III) and never bite you. Recast, and the old ones vanish. Log out with snakes out and log back in: they are gone. Check the Expert lifetime (40 s) and poison level by eye; no gametest covers them.
6. **Serpensortia IV:** left-click a zombie and the snakes attack it. Left-click the floor and they guard the spot; again, and they follow. Spawn-egg snakes ignore you at IV and attack you at III. Click a player with PvP off: refused.
7. **Patronus IV:** the corporeal Patronus follows you, you get Night Vision near it (it can linger up to about 13 s after you leave), and zombies and skeletons within 6 blocks are shoved back and drop their target. At III none of this happens.
8. **`/patronus <friend> hello`** at IV (friend within 500 blocks, same dimension): a Patronus of your form appears beside them with the message. A second message within 30 s is refused. At Tainted Darkness it is refused.
9. **Remorse:** brew it (Ghast Tear, Dittany x2, anticlockwise 3, simmer 90). At Darkness 50, drinking hurts, gives nausea and weakness, and Darkness drops to 45. A second drink hurts but changes nothing ("spent").
10. **Grey spells:** Obliviate x5 raises Darkness by 1.0. A Slytherin's Avada adds 4.5, not 6. A Hufflepuff's Heal redemption removes 1.5. A grey cast does not restart the fading clock (Darkness keeps fading after Obliviate; a dark cast stops it).
11. **Quality, Wiggenweld:** stir at about one turn a second and you hear chimes, and the potion says "Exceptional quality" (12 HP). Spam-clicked stirs give no quality line. Drink a Fine and an Exceptional bottle and compare the heal (10 and 12 HP); no gametest drinks a graded bottle.
12. **Quality, Dittany Essence:** wild dittany plus steady stirs gives Exceptional; bought dittany plus steady stirs gives Fine. Bottling an Exceptional potion earns "Outstanding Brew" (the advancement is untested).
13. **The book gate:** without reading the book, Asphodel into water is refused and nothing is lost. Buy the book from a level-3 Bookseller (22-27 emeralds) and read it: the brew works. The Guide no longer shows Living Death or Felix; the book page does. A friend who has not read the book is still refused.
14. **Veritaserum** (plain right-click, no sneaking) on a Diagon Alley villager: prices drop, and a second dose that day is refused. On a Death Eater in a camp: the others within 48 blocks glow. On Voldemort: "resists". On a disguised friend (PvP on): the disguise breaks. On a disguised friend with PvP off: refused.
15. **Tomes:** a creative player keeps the tome. A picker with 12+ spells shows the scrollbar and arrows (check that the up arrow does not overlap the title). Trial vault keys sometimes give tomes; ominous vaults can give Rare and Epic.
16. **Dedicated server:** every item above works on the Desktop Forge server with a fresh jar (`build/libs`).

## Extra things the per-task reports flagged

Not in the plan's list, but nobody has seen them in game:

- **Fine ingredients in play (Task 13):** pull a Mandrake by hand, kill a Blaze for an Ashwinder Egg, and visit a level-5 Apothecary (fine Asphodel, fine Wormwood x2, fine Valerian x2). Each should show a "fine" tooltip and not stack with plain ones. Wild Dittany gives one fine and one plain; only the dittany drop is gametested. Check that wild dittany patches inside generated structures (structure nbt) still drop fine ones.
- **Brews in progress at the upgrade (Task 11):** a cauldron mid-brew from before this branch only counts the later steps for quality. The "is ready (quality)" line shows only when a player's step finishes a recipe, which is rare because recipes end in a simmer.
- **Snake that was hit (Task 6):** a snake bites a player who hit it, even with PvP off (self-defence). Say whether that should stay.
- **Compare the `no_pvp` refusal on a creative target (Task 6):** it shows for creative players too; harmless, but decide if it reads right.
- **Guide screen (Task 14/15):** no blur backdrop; the book page has a single header.
- **The snake model:** it is the hand-written Java model; a Blockbench rebuild is optional later work (the MCP was offline).
- **Trial vaults:** the vault display item can show a tome; six of the eight trial table ids never match (nested tables skip global loot modifiers), which is harmless.

## Known issues found during the whole-branch check (not fixed)

- `spell.hogwartsmagic.serpensortia.rank.2/.3/.4` in `en_us.json` still say "Only the rank discount applies: less mana and a faster recharge." That is now wrong (the ranks add snakes, lifetime and poison). The Fiendfyre rank lines were rewritten; these three were not.

## Before merging

- **Network protocol:** bumped 10 to 11 on this branch (Task 14) and on `pensieve-capture`. The merged value must be 12.
- Expect merge conflicts in `en_us.json`, `ModItems`/`ExampleMod` and `SpellPackets` against the Pensieve work.
- Run `./gradlew test runGameTestServer` and a `runServer` boot on the merged result.

## Deferred (not built)

- **Stun knock-ons:** free horses and tick-driven bosses ignore stun; a silenced NPC's non-beam casts still go off. No fix design exists.
- **Revelio** showing another player's Darkness tier at Master.
- **Dementors** that ignore Lost players.
- **Wolfsbane**, a datapack recipe format and **JEI** support.
- **Open sign-offs** from the roadmaps.
- Task minors recorded in the ledger: the offline-caster Fiendfyre falls back to `mobGriefing` (skips claim checks); a dispatched serpent burns players on the unfiltered controlled path and does not re-check its target while homing; dispatch state is not saved across reload; a commanded player attack is not re-checked for PvP while running; old snakes more than 64 blocks away are not dismissed by a new cast; an NPC owner in an unloaded chunk loses its snakes after 2 s; a player named `form` cannot be messaged by `/patronus`; messenger text bypasses chat settings; Patronus light blocks ignore terrain rules; the Remorse "never kills" guarantee is not airtight under Levicorpus/Petrificus damage multipliers; Slytherin rounding gives 0.8x on small gains.

## Whole-branch check (2026-10-27)

- `./gradlew test`: BUILD SUCCESSFUL.
- `./gradlew runGameTestServer` twice: "All 156 required tests passed" both times, exit 0, 23 of 23 `LEFTOVERS-GAMETEST PASS` lines both times, no flakes (the earlier `groundFireRespectsMobGriefing` and `snakeVanishesWhenItsOwnerIsGone` flakes were fixed in Tasks 6 and 10).
- `runServer`: `Done (0.823s)!`, no mod-related ERROR or Exception, no mod WARN.
- No `net.minecraft.client` or `hogwartsmagic.client` import in `spell`, `entity`, `item`, `brewing`, `block` or `loot`, except the existing `entity/StudentModel.java` (untouched by this branch).
- No enum `switch` in the client files (the only hit is a comment in `TomePickerScreen`); no `broadcastEntityEvent` in `SnakeEntity` or `FiendfyreSerpentEntity`.
