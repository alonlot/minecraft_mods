# Ledger: Boggart and Riddikulus (plan docs/superpowers/plans/2026-10-08-boggart.md)

Branch: `ccr-f8e6c9c8-akgyo0`. Three implementers ran at the same time on disjoint files. The shared API in the plan was fixed; nobody changed it.

## What was built, by whom

- **A (server)**: `BoggartRules` (pure rules + test), `BoggartFears` (per-player death record, persisted), `BoggartEntity` (hidden / revealed / laugh states, fear pulse, hide timer, `onRiddikulus`, banish + advancement), `ModEntities.BOGGART`, attributes, spawn placement (indoors, sky light 0, 48-block spacing), spawn egg and creative tab line, biome spawners for `forbidden_forest` and `dementor_fen`, the one-line death hook in `ModEvents.onLivingDeath`, advancement `hp/riddikulus`, lang keys for entity, egg and advancement.
- **B (spell)**: `SpellType.RIDDIKULUS` (pink, NOTE and HAPPY_VILLAGER particles, 40 mana, 15 cooldown, uncommon), `RiddikulusSpell` hit effect, spellbook id and model override, book icon, chest loot (common pool), villager trade, spell lang keys, beam trail and impact particles.
- **C (client + docs)**: `BoggartRenderer` (surrogate per form; hidden form is a vex drawn through a translucent dark violet shadow texture; revealed form copies pose and walk animation, shrinks by `laughScale`, spins while laughing), `build_scripts/generate_boggart_texture.py` and `boggart_shadow.png` (64x64), renderer registration in `ClientModEvents`, guide pages (CREATURES "On boggarts", SPELLS one line), `MOD_FEATURES_WIKI.md` entries (Boggart, Riddikulus, advancement line), `wiki/data.js` cards, this ledger.

## Decisions by C

- The renderer does not know the caster's rank. It uses `max(hits, hitsToBanish(1))` as the "needed" count, so each hit shrinks the form by a visible step even for a Master.
- The walk animation position is stepped once per boggart tick with `WalkAnimationState.update(speed, 1.0F)`. Vanilla has no position setter.
- A form id that is not a registered type, or whose `create()` returns null, falls back to the shadow vex.
- The wiki has no Grindylow or Colloportus entries, so the new entries sit at the end of the Spells and Mobs sections. `wiki/data.js` stats were bumped to 38 spells and 18 mobs to match the arrays.

## In-game checklist (the user's)

| # | Step | Expect |
|---|------|--------|
| 1 | Use the Boggart spawn egg in a dark, roofed room and step back past 8 blocks. | A dark violet, smoky shape stands still. It bobs a little. It does not move or attack. |
| 2 | Walk within 8 blocks with a clear line of sight. | A warden boom, a burst of ink, and it takes the shape of the mob that has killed you most. With no deaths it is an Acromantula. If a player killed you most, it is a Dementor. |
| 3 | Stand within 6 blocks for a few seconds. | Darkness every 2 seconds. Within 3 blocks also Slowness I. It walks to you slowly and hits for 1 heart. |
| 4 | Cast Riddikulus at it once. | Villager "yes" sound and notes. It shrinks, wobbles, spins for 3 seconds and does not attack. Your Darkness and Slowness end. |
| 5 | Keep hitting it with Riddikulus (3 hits at Novice, 2 at Adept and Expert, 1 at Master). | On the last hit: poof and notes, a firework twinkle, no drops, and the advancement "Riddikulus!" is granted. |
| 6 | Reveal a second Boggart, then walk more than 16 blocks away and wait 10 seconds. | It is a shadow again. Walk back: it is at full health and needs the full number of hits again. |
| 7 | At night, explore roofed places (ruins, caves under cover) in the Forbidden Forest or the Dementor Fen. | Boggarts spawn only where the sky light is 0. Never two within 48 blocks of each other. |
| 8 | Open chests that hold spellbooks (ruins, camps). | The Riddikulus spellbook turns up in the common pool, with its own pink icon. |
| 9 | Find a villager that sells advanced spellbooks. | The Riddikulus book is among the trades. |
| 10 | Open the Hogwarts Guide. | CREATURES has "On boggarts". SPELLS names Riddikulus on the disciplines page. The wiki page lists a Boggart mob card and a Riddikulus spell card. |
