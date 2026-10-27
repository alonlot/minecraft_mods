# Battle of Hogwarts memory — rebuild plan (2026-10-27)

The first version (two scenes on the flying lawn, ~30 fighters in two lines) was rejected: "too generic, all good in
one line and all bad in one line, too fast, not in Hogwarts". This rebuild follows the film (Deathly Hallows Part 2)
at scale: ~100 students, ~250 Death Eaters (real actors in the foreground, the `crowd` effect for the far army),
several giants, acromantulas, McGonagall's stone knights, voiced lines (Fish Audio), and the fighting inside the
castle. Pacing is slower: shots 4-6 s, long holds, every scene breathes.

All spoken lines are original paraphrase of the film's beats; only incantations are canonical.

## Scenes (chained with `next`; ids are final)

| id | set | ~len | beats |
|---|---|---|---|
| `battle_of_hogwarts` | battle set | 75 s | Night. The courtyard full of students and teachers getting ready (crowd + actors milling, carrying, running). McGonagall at the great doors: "Hogwarts is threatened..." Piertotum Locomotor: the 24 stone knights on the courtyard plinths wake (kneel -> rise) and march in formation out through the south gate onto the viaduct. Her quip. The battlements: professors and students raise the shield; it climbs over the castle. |
| `battle_of_hogwarts_siege` | battle set | 65 s | The hill south of the viaduct: the army (crowd `death_eaters` ~400 + ~40 real Death Eaters + Voldemort + giants). Voldemort's line. The volley; ripples; students on the battlements flinch. The shield burns away; embers rain on the courtyard. |
| `battle_of_hogwarts_bridge` | battle set | 55 s | Neville on the covered wooden bridge facing the Snatchers massed on the far end; his taunt; they charge; he runs back; Seamus's charges blow the bridge in a wave from the castle end out; the planks and roof tumble into the ravine (memory_block actors) with the Snatchers. |
| `battle_of_hogwarts_courtyard` | battle set | 100 s | Death Eaters stream in as black smoke trails over the walls and land; giants smash through the south gate and the statues swarm them (one giant brought down, statues shattered); acromantulas pour over the east wall; ~80 students and ~120 Death Eaters fight in clusters all over (no lines: pairs, threes, a rush, a fall back, someone dragged to cover); rubble falls from a tower; fires; Ron and Hermione flee a spider; Hagrid. |
| `battle_of_hogwarts_hall` | great_hall | 60 s | Later. The Great Hall: wounded on the floor, the house tables pushed aside. Bellatrix fights three girls; Molly steps in ("Not my daughter..."), duels and petrifies her; she shatters. |
| `battle_of_hogwarts_duel` | battle set | 75 s | Grey pre-dawn. Harry and Voldemort in the ruined courtyard, the survivors pressing back to the arcades; the beam lock; Voldemort's curse rebounds; he flakes to ash. |
| `battle_of_hogwarts_dawn` | battle set | 45 s | Dawn. The broken stub of the wooden bridge: Harry, Ron, Hermione. He snaps the Elder Wand and throws it into the ravine. Crane up over the castle. "All was well." |

## The battle set (`structure/hogwarts_battle.nbt`, generator `build_scripts/pensieve_scenes/battle_set.py`)

Scene frame: X east, Z south, origin at the courtyard centre. **The courtyard floor block layer is y -1** (everyone
on it stands at y 0). Radius 200. Night palette: Hogwarts grey (stone bricks, cracked/mossy, andesite, tuff,
deepslate tiles on roofs), lit windows (glowstone/shroomlight behind glass) and torches/lanterns.

Fixed layout the scene generator relies on (the constants live in `battle_layout.py`, which both import):

- **Courtyard** (the film's Viaduct Courtyard): open paved floor x -24..24, z -18..18 (y -1), centre fountain at
  (0, 0) radius 3.5 (basin rim 1 high, a central column to y 4). Around it a **cloister arcade** 6 deep
  (x ±24..±30, z ±18..±24), pointed arches every 4 on the courtyard side, roofed at y 7, walkable floor y -1.
- **24 statue plinths** along the arcade's courtyard edge (on the courtyard floor just in front of the piers),
  plinth 1 high (top y 0, statue stands at y 1). Positions in `STATUE_PLINTHS`.
- **Ranges** behind the arcade: building mass x ±30..±40 / z ±24..±34, walls to y 22, roofs to y 30, gothic windows
  lit. **Corner towers** at (±35, ±29), radius 6, to y 52, conical/spire caps to y 70.
- **North range** (the castle's main block): taller (to y 36, roof 44), with the **great doors** in its courtyard
  face: opening x -4..3 at z -24, y 0..11 (pointed), steps; a door landing at (0, 0, -21). Its **battlements**: a
  walk on top of the south range at y 23 (floor block y 22), x -26..26, z 26..28, crenellated parapet on the outer
  (south) side, looking south over the viaduct (professors cast the shield from here; students line it).
- **South gate**: arch x -5..4 through the south range (z 24..34), 12 high, onto the **viaduct**: stone bridge
  deck x -5..4 (y -1), z 34..118, parapets 1 high, arched piers every 14 down to the valley floor.
- **East arch** x 30..40, z -3..2 (8 high) onto the **covered wooden bridge**: deck z -3..2 (y -1), x 40..128,
  dark oak deck and posts every 4, A-frame roof (peak y 7), open sides above a rail; it lands on a rocky outcrop
  (x 128..150, z -15..15, y -1) with a **stone circle** (12 standing stones) where the Snatchers mass.
- **The castle rock**: everything above sits on a plateau x -60..40, z -70..34 whose cliffs drop to the **valley
  floor at y -45** (the ravine under both bridges). The valley floor: rough grass, rocks, dead trees.
- **The army hill**: south of the viaduct the ground rises from the valley (z 118 at y -1, the viaduct's landing)
  to a crest about y 30 at z 180, broad (x -120..120), open grass with scattered rocks and a few dead trees. Rows
  for the crowd army: `ARMY_ROWS` (standing rows on the slope, facing north).
- **More castle to the north and west** for the skyline: a great hall block and the Astronomy Tower (to y 100) north
  of the main block, a second courtyard range to the west; only shells (seen from outside).

Every camera stays in `CAM_REGION`s listed in `battle_layout.py`; faces turned away from all of them may be left open.

## Engine additions

1. **Living statue cues** (puppet `hogwartsmagic:living_statue`): `anims` `WAKE` (the rise over its own wake time),
   `CRUMBLE` (the crumble), `IDLE`; `swing` action already swings the halberd. Default before any cue: dormant kneel.
2. **Crowd style `death_eaters`**: black robes and hoods, bone-white masks (face), no scarves, wands raised in the
   chant reaction; for the far army on the hill.
3. **Smoke flight** (`do: "smoke"` / `"unsmoke"`): while on, the actor is hidden and a thick black smoke trail is
   drawn along its path (always-visible particles plus a dark ribbon), the film's Death Eater flight. Landing:
   `unsmoke` with a smoke burst.

## Skins (bundled `memory_skin/*.png`, generated)

mcgonagall, neville_dh, ron_dh, molly, bellatrix, seamus, ginny, luna, hagrid, kingsley, flitwick, plus battle
students (4 houses x boy/girl, dusty) and Death Eater variants (masked x3, unmasked x2, snatcher x2).

## Voices (Fish Audio, private use)

Harry older 63efa34dab914c5085b89bcc8356ea94, Ron 5087254d8fe8466cb5e750a1b0dd44a4, Hermione older
4a5f1d19bfed442bb0ff388a669077a3, McGonagall d625beac29e2417faba8ace4c3d6ccdd, Voldemort (Tom Riddle)
db84ac02993a4f2f8ff4370258d61d7f, Neville ebe169afb0934b5bbcc5c1ed62a024b5, Bellatrix
239e968e73804926b586790018c42db1, Flitwick 041630e2d9c34042b6c0c09733916bcc, Hagrid f6fd51174e2743db91c4e91fe5de4ec1.
Molly: no clone on Fish; pick a warm older British woman voice.
