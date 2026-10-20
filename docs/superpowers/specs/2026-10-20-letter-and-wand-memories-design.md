# The Letter Memory and the Wand Memory - design

Date: 2026-10-20. Status: draft for the user's approval (builder check done; the user answered the wand-lines and old-saves questions).

## Intent

Two more film-style Pensieve memories in the same spirit as the Great Hall Sorting memory
(`docs/superpowers/plans/2026-10-20-sorting-great-hall.md`). Each plays at a real milestone of the
player's journey:

1. **The Letter Memory** plays the first time the player opens their Hogwarts acceptance letter. It
   is the start of *Philosopher's Stone*: the letters at the Dursleys' house, then Hagrid finding you
   in the hut on the rock.
2. **The Wand Memory** plays the first time the player buys a wand. You open your eyes in a crowded
   Diagon Alley, wander the street looking through the shop windows, then step into Ollivanders for
   the wand-choosing scene from the film.

These belong with the Sorting memory: the same first-person bookends (the eyes open and close), the
same film craft, the same player-as-the-boy rules. Together they cover the three milestones before
Hogwarts: the letter, the wand and the Sorting.

## Decided by the user

- **The Letter Memory:**
  - It is triggered by opening the acceptance letter.
  - It covers the Dursleys' house and the hut on the island with Hagrid's rescue.
  - It is built around two moments: the letters pouring down the chimney and whirling round the
    living room while the boy jumps to catch one, and Hagrid smashing through the hut door.
  - It ends with Hagrid handing the boy the letter, then the eyes open in the real world, holding the
    letter just opened.
- **The Wand Memory:**
  - It is triggered by getting a wand for the first time.
  - Ollivander's lines are the same for every wand.
- **Both:** they play only for new players. Old saves that already passed the milestone don't get
  them on their own.
  - You open your eyes in a populated Diagon Alley and walk the streets, looking through the shop
    windows (brooms and similar).
  - You enter Ollivanders for the full wand-selection scene "just like the movie".
- **Carried over from the Sorting memory:**
  - The player is the boy, in their own skin, always treated as a boy. Spoken lines never use the
    player's name; it appears only as on-screen text.
  - Children are about 0.75 scale and Hagrid about 1.5.
  - First person only where needed (the eyes opening and closing).
  - Cinematic and as close to the film as blocks allow.
  - Subtitles on spoken lines.
  - Characters move naturally and individually, never in unison.
  - The user supplies the film music.
  - Claude may find skins and voices online, asking before each download. Fish Audio voices come
    from the user's curated `HP_FISH_VOICES` where one exists, and the user picks when there are
    several options or none fits.

## Goals

- Two memories, each about 2½–3 minutes, each a chain of scenes (`next`) that plays as one unbroken
  memory, opening from black with the eyes opening and closing into `end_action: "wake"` so the
  player opens their eyes in the real world.
- They feel as finished as the Great Hall: populated, animated with the human-motion clips, voiced,
  subtitled, scored, with real camera work.
- They reuse what already exists rather than rebuilding it.

## Non-goals

- Changing what the letter or the wand does in gameplay. The letter screen, the wand item and the
  wandmaker trades stay as they are.
- Multiplayer viewing. Each memory is watched alone (`startSolo`).
- Replaying the Sorting memory's content. The Great Hall stays its own memory.

## Anti-goals (it fails if...)

- ...the memory traps the player or loses their item: the letter must still open, and the wand must
  still be in the inventory, whatever happens (leave early, death, disconnect).
- ...it plays more than once on its own. A memory every time you open the letter would be a punishment.
- ...the letter flood or Hagrid's entrance looks cheap or rushed, or the Diagon Alley street feels
  empty.
- ...a spoken line says the player's name.
- ...anything authoritative runs on the client, or common/server code touches client-only classes.

## What exists already (reuse)

- **The engine (from the Sorting work):**
  - solo start and chaining;
  - `eyes_open`, `eyes_close` and `eyes_peek`;
  - `end_action: "wake"`;
  - subtitles and narration `size`;
  - the `sfx` cues and the `equip` action;
  - the `memory_door` / `memory_door_mirrored` actors, `light_rays`, `floating_candles` and
    `enchanted_ceiling`;
  - the human-motion life layer and its clips (`TALK`, `NERVOUS`, `WAVE`, `SIT_DOWN`...);
  - the seated-leg geometry (`human_motion.py`);
  - the 900,000-character scene cap;
  - the gradle lock protocol.
- **From the parked Privet Drive work** (`docs/superpowers/plans/2026-10-20-sorting-memory-stage2.md`):
  - the `memory_letter` envelope actor (commit 260dd31). Its Opus review fixes are still to apply;
    see that plan's ledger;
  - the `letter_flood` effect (ce110d6), not yet reviewed;
  - the Dursley skins the user approved (Vernon, Petunia, Dudley, with Petunia's hair to recolour
    blonde and Dudley recoloured blond in a striped jumper), downloaded to the scratchpad only;
  - Vernon and Petunia Fish Audio voice samples, not yet picked by the user
    (`run/voice_samples/stage2`);
  - the Privet Drive set design brief and beat outline (that plan's Tasks 3 and 4).
- **Characters:**
  - the Hagrid skin (NovaSkin, shipped);
  - Hagrid's Fish voice `f6fd51174e2743db91c4e91fe5de4ec1` from the user's set. Its note says it "may
    sound more wise-old than gruff giant", so the user samples it first;
  - the 130 procedural student skins;
  - McGonagall, Dumbledore, Ron and Hermione.
- **Sets and objects:**
  - `diagon_alley.nbt` (233 x 45 x 49; wider than the 400-block memory area at radius 200, so it is
    cropped or rebuilt);
  - the mod's goblin, owl and other mobs;
  - the Hogwarts letter item `hogwarts_invite` and its screen (`HogwartsInviteItem.use` sends
    `OpenHogwartsInviteScreenPacket`);
  - the owl delivery of the letter (`ModEvents.spawnFirstYearInvite`);
  - the wandmaker villager and the existing in-world Ollivanders ceremony (`OllivandersCeremony`).
    The ceremony is a 26-second server-camera cutscene on the first wand bought from the wandmaker,
    with the flag `hogwartsmagic:ollivanders_done`, replayable with `/ollivanders`. It has its own
    Ollivanders sounds and a nine-beat structure (stillness, remembering, the measuring tape, three
    wrong wands, the right one, "curious", release) that the new memory should keep.

## The Letter Memory

**Trigger.** The first time a *new* player right-clicks their acceptance letter, the server starts
the memory (solo, no basin) instead of opening the letter screen. "New" (user decision): only players
whose letter is delivered after this feature ships. `spawnFirstYearInvite` sets a per-player
`LetterMemoryPending` tag at delivery, and old saves never get it, so they never see the memory on
their own. When the memory ends with `wake`, the
letter screen opens (the eyes open on the real world, then the letter unfolds). After that, the letter
opens normally. A per-player flag marks it done. If the player leaves early, dies or disconnects
before the end, the flag is not set: the letter screen opens on leaving, and the memory plays again
the next time the letter is opened. *(My call.)*

**Beats** (about 170 s; times approximate; the builder writes the shot list first):

| # | Beat | ~s | What must be there |
|---|---|---|---|
| 1 | The cupboard | 15 | Black, then the eyes open in first person in the cupboard under the stairs, looking up at the stair underside. Feet thud overhead and dust falls. A knock and Petunia's voice. The camera pulls out to reveal the boy on his cot in the tiny cupboard. |
| 2 | The first letter | 20 | The street of identical houses in morning light. The post drops through the slot. The boy picks up a heavy envelope addressed in green ink to "Mr. {viewer}, The Cupboard under the Stairs" (shown as text). In the kitchen Vernon snatches it. |
| 3 | More letters | 20 | A montage of days: 3, 8, then 20 letters through the slot. Vernon nails planks over the slot. Owls line every roof, fence and chimney, their heads turning to No. 4. |
| 4 | **The flood** (key moment) | 30 | Sunday. Vernon smug in his armchair ("No post on Sundays!", a short nod). A rumble in the chimney, then hundreds of letters burst from the fireplace and whirl round the living room (`letter_flood`). The boy jumps and grabs at them while Vernon shouts. Slow push-in on the boy's hand closing on one letter. |
| 5 | The crossing | 15 | A stormy night at sea. The family in a small rowing boat on rough water: rain, spray, lightning and the boat pitching. The user wants to see waves. |
| 6 | **Hagrid** (key moment) | 50 | A bare rock with a shack in the storm. Dudley asleep, the boy awake on the floor at midnight. A pounding at the door, the door crashing in, and Hagrid stooping through the low doorway, huge, lit by lightning. The Dursleys cower. Hagrid finds the boy, softens and crouches. His voiced lines (original, no names; "You're a wizard" as a short nod) and the letter offered in his giant hand. |
| 7 | The letter | 10 | First person: Hagrid's hand holds out the letter, the boy's hand takes it, then `eyes_close`, then `end_action: "wake"`, then the real letter screen opens. |
| | Transitions | ~10 | Chain fades between locations. |

**Sets:**
- 4 Privet Drive and its street, inside and out, as in the Stage 2 brief (the cupboard at child
  scale; the hall, living room and fireplace, kitchen and letter slot).
- The sea and the rock with its one-room shack. The shack's doorway is low enough that Hagrid stoops.

**Engine work:**
- Apply the outstanding `memory_letter` review fixes and review `letter_flood`.
- **Rough water** for the crossing: the boat's pitch and roll, rain and spray, waves that read as
  waves, and lightning (the effect exists). The builder judges how. Candidates: a boat actor with
  scripted roll, a wave-surface effect and rain particles.
- **The door crashing in:** the `memory_door` leaf can be animated falling flat, or a variant can be
  added.

**Voices** (Fish Audio; lines shown to the user before generating):
- Vernon (pick from the existing samples);
- Petunia (pick from the samples);
- Hagrid (sample his voice id first);
- optionally Dudley.

**Music:** the user supplies it. Suggested cues: "Visit to the Zoo and Letters from Hogwarts" for
beats 1–4, and a storm or Hagrid cue for beats 5–7.

## The Wand Memory

**Trigger.** It replaces the in-world Ollivanders ceremony. The first time a player buys a wand from
the wandmaker, the memory plays instead of the old ceremony (same flag
`hogwartsmagic:ollivanders_done`, set when the memory ends). Players who already bought a wand already
have that flag, so, as the user decided ("new players only"), they don't see it on their own. The bought wand is already in the
inventory before the memory starts, so nothing can be lost. `/ollivanders` replays the memory.
`OllivandersCeremony` stays in the code as the fallback if the memory cannot start (for example, a
missing scene). *(My call: the memory replaces the ceremony rather than playing after it.)*

**The player's own wand.** In the memory, the wand that chooses the boy is the wand the player bought
(its item id). This needs a small engine addition: a scene can name `{viewer_wand}` as an item, which
the server fills in from the triggering purchase. With no purchase (`/ollivanders`) it falls back to
the held wand, then to `harry_potter_wand`. *(My call.)*

**Ollivander's lines are the same whatever the wand** (user decision: it only plays on the first
wand). So the lines never name a wood or core. The "Curious..." beat stays as a short, general nod: the
wand chose you, and he remembers every wand he has sold.

**Beats** (about 150 s):

| # | Beat | ~s | What must be there |
|---|---|---|---|
| 1 | Eyes open | 10 | Black, then the eyes open in first person in the middle of a crowded Diagon Alley in daylight: noise, owls and a crowd flowing past. |
| 2 | The street | 50 | Hagrid at 1.5 scale leads the boy up the crooked street. The camera follows, then frames the windows. The windows: **Quality Quidditch Supplies** with a gleaming new broom on a stand and children pressing their noses to the glass; **Eeylops Owl Emporium** with owls in cages, hooting; the apothecary; Flourish and Blotts' stacked books; cauldrons hanging outside a shop; Gringotts' white marble at the end, with goblin guards at the doors. Shoppers (witches and wizards in robes and pointed hats) haggle, chat and carry parcels. Every one moves on its own. |
| 3 | Ollivanders | 10 | A narrow, dusty shop front with gold lettering ("Makers of Fine Wands since 382 B.C."). The bell over the door tinkles as the boy goes in alone. Inside it is dim and silent. |
| 4 | **The wandmaker** (key moment) | 15 | Towering shelves of thousands of narrow wand boxes rising into the dark. A rolling ladder slides along them, and Ollivander appears suddenly on it. His voiced lines are original, with a short nod. |
| 5 | The tape | 10 | A measuring tape measures the boy by itself while Ollivander talks. |
| 6 | **Three wrong wands** | 25 | Each one worse than the last: a wave sends boxes flying out of the shelves; the next shatters a vase; the third makes a wretched fizzle. Ollivander snatches each back. |
| 7 | **The one** (key moment) | 15 | The right wand. A warm golden light gathers, a wind rises from the floor and lifts the boy's robe and hair, glowing motes spiral up, then a soft settling. |
| 8 | "Curious..." | 10 | The quiet beat: a very still camera and his voice only. Original lines, with "curious" as a nod. |
| 9 | Out | 5 | `eyes_close`, then `end_action: "wake"`, then the player is back in the real world, wand in hand. |

**Sets:**
- Diagon Alley as in the film: a crooked, narrow, steep-roofed street, built on the mod's
  `diagon_alley` (cropped or rebuilt to fit the memory area) and restyled the way the Great Hall was
  restyled.
- The Ollivanders interior: narrow and tall, with walls of wand-box shelving to the ceiling (the box
  ends can be a small custom block or texture), a counter and a rolling ladder.

**New characters and assets:**
- **Ollivander:** a skin (NovaSkin search, the user approves) and a Fish voice (search for a John Hurt
  style voice, the user picks).
- **Crowd:** adult witch and wizard skins, procedurally generated like the students, with pointed
  hats as worn items where possible.
- **Others:** goblin guards (existing mob) and owls.
- **Props:** a broom prop in the window (an existing mod broom item, or an item frame or a held item
  on an invisible stand); flying wand boxes (a small actor like `memory_letter`); the measuring tape
  (a small actor or an effect); the vase (a block that shatters with particles); the wand-glow wind
  (a new effect, or `light_rays` / `patronus_light`-style particles).

**Sound:**
- ElevenLabs effects: the street crowd ambience, the door bell, the ladder rolling, boxes tumbling, a
  vase smashing, the fizzle, the golden whoosh and wind.
- The existing Ollivanders sounds (shop, door, ledger, measure) are reused where they fit.

**Music:** the user supplies it. Suggested cue: "Diagon Alley and the Gringotts Vault" for the
street, and a quiet, magical cue for Ollivanders.

## Constraints

- **Engine limits:** the set radius is at most 200, and the scene JSON is at most 900,000
  characters. Chain scenes where a location is too big.
- **Process:** follow `.claude/skills/writing-pensieve-memories/SKILL.md`:
  - shot list first;
  - a Python generator;
  - physics problems clean;
  - `SceneDataTest` green;
  - collision and leg-box asserts for walkers and seated actors;
  - subtitles with real clip lengths;
  - the human-motion clips instead of hand-keyed limbs.
- **Code safety:**
  - Server/client safety; no switch on an enum in client code; avoid entity event bytes 21, 35 and 63.
  - Never run gradle while the dev client runs.
  - The gradle lock applies when several agents work at once.
- **Licences:** the film music (user-supplied) and any downloaded builds stay private until licensed.
- **Usage:** the user caps weekly usage per session. Each memory is staged so the user can stop
  between stages and watch each stage in game.

## Build order

1. **The Letter Memory** goes first: half its engine already exists.
   1. The engine leftovers: the `memory_letter` fixes, reviewing `letter_flood`, rough water and the
      door falling.
   2. The Privet Drive set and the two Privet scenes.
   3. The sea, the rock and the hut, and the crossing and Hagrid scenes.
   4. Voices, music, the trigger on opening the letter, and an in-game pass.
2. **The Wand Memory:**
   1. The engine: `{viewer_wand}`, the flying-box and tape actors, the wand-glow effect.
   2. The Diagon Alley set restyle and the crowd skins.
   3. The street scene.
   4. The Ollivanders set and scene.
   5. Voices, effects, music, the trigger replacing the ceremony, and an in-game pass.

## My calls (flagged for the user)

- **The letter:**
  - The memory plays instead of the first opening, and the letter screen opens after the wake.
  - Leaving early still opens the letter, and the memory replays next time.
- **The parked Stage 2 plan:** `docs/superpowers/plans/2026-10-20-sorting-memory-stage2.md` is
  superseded by the Letter Memory. Privet Drive is built once, here; its asset names become
  `letter_*`; the Sorting chain stays as it is; and its review fixes are tracked in this work. The
  approved Dursley skins are still in the 2026-10-20 session scratchpad (`skins_dl/`). The first task
  copies them into the repo and does the approved touch-ups.
- **The wand:**
  - The memory replaces the old in-world ceremony, which is kept as a fallback.
  - `/ollivanders` replays the memory.
  - The wand that chooses the boy is the player's own purchased wand.
- **Order:** the Letter Memory is built first.
- **Hagrid:** he guides the boy through Diagon Alley, then the boy goes into Ollivanders alone.

## Left to the builder

- The exact shot lists, camera moves and timings inside each beat.
- How rough water, the falling door, the flying boxes, the tape and the wand glow are built.
- The layout of each location inside the memory area.
- The exact wording of the lines (shown to the user before voicing).
