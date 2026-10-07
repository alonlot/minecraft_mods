# Ledger: Boggart and Riddikulus (plan docs/superpowers/plans/2026-10-08-boggart.md)

Branch: `ccr-f8e6c9c8-akgyo0`. Three implementers ran at the same time on disjoint files. The shared API in the plan was fixed; nobody changed it.

## What was built, by whom

- **A (server)**: `BoggartRules` (pure rules + test), `BoggartFears` (per-player death record, persisted), `BoggartEntity` (hidden / revealed / laugh states, fear pulse, hide timer, `onRiddikulus`, banish + advancement), `ModEntities.BOGGART`, attributes, spawn placement (indoors, sky light 0, 48-block spacing), spawn egg and creative tab line, biome spawners for `forbidden_forest` and `dementor_fen`, the one-line death hook in `ModEvents.onLivingDeath`, advancement `hp/riddikulus`, lang keys for entity, egg and advancement.
- **B (spell)**: `SpellType.RIDDIKULUS` (pink, NOTE and HAPPY_VILLAGER particles, 40 mana, 15 cooldown, uncommon), `RiddikulusSpell` hit effect, spellbook id and model override, book icon, chest loot (common pool), villager trade, spell lang keys, beam trail and impact particles.
- **C (client + docs)**: `BoggartRenderer` (surrogate per form; hidden form was first a vex drawn through a translucent shadow texture (replaced, see below); revealed form copies pose and walk animation, shrinks by `laughScale`, spins while laughing), `build_scripts/generate_boggart_texture.py` and `boggart_shadow.png` (64x64, since removed), renderer registration in `ClientModEvents`, guide pages (CREATURES "On boggarts", SPELLS one line), `MOD_FEATURES_WIKI.md` entries (Boggart, Riddikulus, advancement line), `wiki/data.js` cards, this ledger.

## Decisions by C

- The renderer does not know the caster's rank. It uses `max(hits, hitsToBanish(1))` as the "needed" count, so each hit shrinks the form by a visible step even for a Master.
- The walk animation position is stepped once per boggart tick with `WalkAnimationState.update(speed, 1.0F)`. Vanilla has no position setter.
- A form id that is not a registered type, or whose `create()` returns null, falls back to the Boggart's own shape.
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

## Review and fix wave

Review (Fable, read-only) found 7 problems. All fixed, 494 tests green, server boots:

1. The beam's 0.1 chip damage hit the Boggart before Riddikulus did. Riddikulus is now in the no-chip list.
2. The spawn rule scanned a 97x97x97 box on every attempt. Now a 1-in-6 roll comes first and the box is flat (16 high).
3. Fears recorded arrows, anvils and TNT, and a Boggart could record itself. Only living killers count; a Boggart kill records the default fear.
4. The spawn egg model was missing. Added.
5. The "personal" cave sound was a broadcast. It is now a packet to the feared player only.
6. Creative players kept a Boggart awake and felt fear. They are now ignored like spectators.
7. A killing blow on a hidden Boggart still revealed it. It must be alive to reveal.

## Own shape (second pass)

The vex placeholder is gone. `client/model/BoggartModel` is a hand-built model: a floating hunched column of
dark, a lumpen head with two pale eye lights, two side wisps and six tendrils that hang to the floor. Everything
sways, breathes and waves in `setupAnim`; the head follows the look direction with a lag. The texture
`textures/entity/boggart.png` (64x64, from `build_scripts/generate_boggart_texture.py`) is dark violet smoke with
thinner alpha on the tendrils. The renderer draws it with `entityTranslucent` and the same pose set-up as
`MobRenderer`, and bobs it. Not seen in a client yet: check the eye position, the tendril length and that nothing
clips into the floor.
