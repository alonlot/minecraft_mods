# The Sorting Memory - design

Date: 2026-10-17. Status: draft for the user's approval.

## Intent

Putting on the Sorting Hat should feel like the start of *Philosopher's Stone*. Before the hat
sorts you, it shows you your own way to Hogwarts, about four minutes long, staged like the first
film: the Dursleys' house, the letters, the island, Hagrid, Diagon Alley, the train, the boats and
the Great Hall. You are the child in it, wearing your own skin. The memory ends with the hat coming
down onto your head in the Great Hall. Then you are back in your own world with the hat on, and the
hat asks you three questions in a real voice and sorts you.

This replaces today's AI sorting (a local Ollama model writing questions in chat) with fixed,
voiced questions that you answer by clicking one of four choices.

The user wants it cinematic and as close to the film as blocks allow. Their three most important
moments are **the train, the boats and the Great Hall**.

## Goals

- One cinematic Pensieve memory of about 4 minutes (target 250 s), played automatically the first
  time an unsorted player wears the Sorting Hat.
- A fixed sorting quiz: 10 questions, 3 picked at random each time, 4 answers each, every line the
  hat speaks voiced with Fish Audio.
- Locations that are recognisable from the film at Minecraft scale.
- Every scene is shot like a film, with real camera work, not just narrated.

## Non-goals

- A literal 1:1 copy of the film's sets or script. Locations are recognisable at block scale, and
  the dialogue is original with a few short nods to the film.
- Player memory capture (Pensieve plan 2). That is separate.
- Changing what a house does once chosen. The house perks (`HousePerks`) and the house reveal
  (`HouseRevealPacket`) stay as they are.
- Multiplayer viewing. The sorting memory is watched alone, by the player being sorted.

## Anti-goals (it fails if...)

- ...the train, the boats or the Great Hall look cheap or rushed.
- ...a voiced line says a name the voice cannot know. Spoken lines never use the player's name.
- ...the four-minute memory feels like a slideshow: still sets, narration over static shots.
- ...sorting gets stuck: a player who leaves the memory early, dies or disconnects must still be
  able to get sorted.
- ...anything authoritative runs on the client, or common/server code touches client-only classes
  (it must work on a dedicated server).

## What the player experiences

1. An unsorted player puts the Sorting Hat on (the trigger `SortingHatManager.maybeStartSession`
   already detects: wearing the hat and no saved house).
2. The sorting memory starts at once, with no Pensieve basin (as `/pensieve preview` does, see
   `PensieveSessions.startPreview`). It is solo: nobody else can join it.
3. The memory plays (below). It ends with the hat lowered onto the player's head, shown in first
   person: the brim comes down over the eyes, then black.
4. The player is back where they stood, still wearing the hat. The sorting screen opens and the hat
   speaks.
5. Three questions, each a click from four answers. The house with the most points wins. On a tie,
   the hat says it cannot decide and lets the player choose between the tied houses (as Harry asked
   for "not Slytherin").
6. The hat shouts the house (a voiced line per house), and the existing reveal and perks apply.

**Replays:** the memory plays every time an unsorted player puts the hat on. Once sorted, the hat does
nothing new. (User approved this guess.)

**Leaving early:** pressing X to leave the memory skips straight to the questions. Dying or
disconnecting during the memory or the quiz saves nothing. The next time the hat is worn,
everything starts again from the memory. *(My call.)*

## The memory, beat by beat

Target 250 s. Times are approximate. The builder writes the full shot list (as the
`writing-pensieve-memories` skill asks) before any JSON and keeps these proportions. The three key
moments get the most time.

| # | Beat | ~Time | What must be there |
|---|---|---|---|
| 1 | Privet Drive | 35 s | **Opens in first person:** black, then the player's eyes blink open in the dark of the cupboard under the stairs, then the camera pulls out to show them lying in the small bed. The house is built to look like the film's 4 Privet Drive, inside and out: the street, the hall, the cupboard, the living room with its fireplace. One letter arrives and Uncle Vernon grabs it; then more and more letters, and Vernon nailing the letter slot shut; owls lined up on the rooftops and fences; and the climax, letters pouring down the chimney and flooding the living room. |
| 2 | Crossing to the island | 12 s | A stormy night at sea. The Dursleys and the player cross in a small rowing boat that rocks on rough water: rain, spray, lightning, the boat pitching. The user wants to see waves. |
| 3 | The hut on the rock | 28 s | A bare rock with a shack in the storm. Dudley asleep, the player awake on the floor at midnight. A pounding at the door, the door crashing in, Hagrid filling the doorway, huge. His key line is voiced and uses no name ("You're a wizard"). |
| 4 | Diagon Alley | 25 s | A bright, busy montage: the brick wall in the Leaky Cauldron's yard folding open into the archway, the crooked street, Gringotts, a wand shop. Builds on the mod's existing `diagon_alley` template. |
| 5 | King's Cross and the train | 50 s | A short shot of running through the barrier onto Platform 9 3/4, with the scarlet engine steaming. **Key moment.** The wide Glenfinnan Viaduct shot (the user's reference photo): a curved viaduct of tall concrete arches across a green Highland valley, the black engine and maroon carriages crossing it with white steam trailing, and the train really moving. Inside, a cosy compartment with Ron and Hermione sitting with the player (the sweets trolley is a welcome extra). |
| 6 | The boats | 35 s | **Key moment.** Night. A flotilla of little boats with lanterns glides across the black lake, and Hogwarts rises lit up on its cliff. Hold on the castle reveal. |
| 7 | The Great Hall | 55 s | **Key moment.** The doors open. Older students sit at the four long house tables; the teachers, Hagrid and Dumbledore among them, at the staff table; hundreds of floating candles; the enchanted ceiling showing the night sky. Good music throughout. McGonagall calls the first-years by name, aloud and voiced: "Granger, Hermione", "Weasley, Ronald", and each is sorted. Then she looks down at her scroll, the hall goes quiet, and **the player's name appears large on screen** (text, not voice). The player walks up to the stool and the hat comes down: first-person, brim over the eyes, black. |
| | Transitions | ~10 s | Fades and cuts between locations. |

All dialogue is original, written for this memory. Short, famous nods are fine; whole scenes of the
film's script are not copied.

## The sorting quiz

- **The question bank:** 10 original questions, written in the spirit of the books. Not the
  official Wizarding World quiz text. Each has 4 answers, and each answer counts toward exactly one
  house. The bank lives in a data file the builder chooses, so questions can be edited without
  code.
- **Each sorting:** the server picks 3 different questions at random. The four answers appear in a
  random order each time *(my call, so the house mapping is not obvious by position)*.
- **The screen:** a custom screen with the question and four answer buttons. It uses
  `renderTransparentBackground`, never the blurred menu background. The hat's voice line plays as
  the question appears. The answers are shown, not voiced *(my call)*.
- **Scoring:** one point per answer. Most points wins. A tie between two or three houses (for
  example 1-1-1) leads to a voiced "difficult, very difficult" line and a second screen offering only
  the tied houses. The server decides everything; the client only sends which button was clicked.
- **Voiced hat lines:** a greeting, the 10 questions, a short reaction between questions (a few
  variants), the tie line, and one shout per house (4).
- **Removing the AI:** `OllamaSortingService`, its config entries and the chat handling in
  `SortingHatManager.handleChat` go away. Taking the hat off mid-quiz cancels it, as today.

## Assets and where they come from

- **Skins.** Needed: the player (their own skin, see engine work), Vernon, Petunia, Dudley, Hagrid,
  McGonagall, Dumbledore, Ron, Hermione (shipped), more teachers for the staff table, and students
  for all four houses (Gryffindor and Slytherin ship today; Hufflepuff and Ravenclaw are new).
  Claude finds them online (NovaSkin and similar, as for Harry and Hermione) and checks with the
  user before each download, naming the file and the source. The user may supply any of them
  instead.
- **Voices.** Claude searches Fish Audio's voice library for each character (Hagrid, McGonagall,
  Dumbledore, Ron, Hermione, Vernon, the Sorting Hat, plus anyone else who speaks). Where several
  voices fit, or none clearly matches, the user picks. Every line is delivered cinematically, not
  read flat. The pipeline is the one already used for the Lake memory (MP3 to mono OGG,
  `pensieve.voice.*`).
- **Music.** The user supplies the soundtrack files. Claude does not download copyrighted film
  music. Claude fits the cues to the beats (crossfades baked in, about 2 s of overlap). Suggested
  cues for the user to source: the opening theme over Privet Drive, the letters music, Diagon Alley,
  the journey to Hogwarts over the viaduct, and the Great Hall entrance and feast music.
- **Structures.** Claude looks online for schematics of Privet Drive, the Great Hall, the Hogwarts
  Express and the viaduct. One goes into the mod only if its licence allows redistribution, with
  the creator credited, and only after the user approves the download. Otherwise it is used as a
  reference and our own build is made from film stills and real references (the real house used
  for Privet Drive, the Glenfinnan Viaduct). Existing pieces to reuse: `diagon_alley.nbt` and the
  `mini_hogwarts` schematic as the castle seen from the boats, the same way the Lake memory uses it.

## Engine work the memory needs

The memory engine (`pensieve/scene`, `client/pensieve`) cannot do all of this today. New
capabilities, each a general feature other memories can use:

1. **Starting from the hat.** A server-side way to start a solo memory for one player without a
   basin, and a hook that runs when it ends (or is left early) to open the sorting quiz. It keeps
   the body protection that basin sessions have (`PensieveBodyEntity`, `forwardDamage`).
2. **The viewer's own skin** for a player actor (for example `"skin": {"viewer": true}`).
3. **The viewer's name in text**, a placeholder in `words` text, for McGonagall's name card and the
   letters' address ("Mr. ___, The Cupboard under the Stairs").
4. **Actor scale**: the children (the player, Ron, Hermione, Dudley, the first-years) at about
   0.75 of an adult, and Hagrid at about 1.5. The sets are built for those sizes from the start:
   the cupboard, doors, the compartment, the boats and the stool all fit a child, and the hut's
   doorway is low enough that Hagrid has to stoop through it.
5. **A first-person eyelid effect**, screen-only, for the eyes opening at the start and the hat's
   brim coming down at the end.
6. **A moving Hogwarts Express**: a new entity actor with a modelled engine and carriages, driven
   along a path like `cave_boat`, with steam. This is the largest single asset. The Blockbench MCP
   server failed to connect in this session; it must be running for the model work.
7. **Letters as actors**: a small envelope entity (or equivalent) so dozens of letters can fly
   along paths, through the letter slot and down the chimney.
8. **Rough water**: whatever makes the island crossing read as a storm: boat pitch and roll, spray
   and rain particles, lightning. The builder judges what reads as waves.
9. **The enchanted ceiling**: a night sky with stars and drifting cloud over the hall, however the
   Pensieve dimension can show it.
10. **Size**: a scene's JSON is capped at 262,144 characters (the Lake memory is already about
    240,000). This memory will not fit as it stands. The builder chooses how: building locations as
    `.nbt` templates instead of thousands of `fills`, raising the cap, or chaining several scenes
    that play back to back invisibly. To the player it must feel like one unbroken memory.

## Constraints

- The set radius is at most 200 blocks, so every location sits side by side inside a 400 x 400 area
  and the camera cuts between them. Use `fog_far` for the long viaduct and castle views.
- Server/client safety: the house decision, the question draw and the memory start live on the
  server. Screens and effects are client-only and never referenced from common code.
- Never run gradle while the dev client is running.
- Follow the `writing-pensieve-memories` skill: shot list first, a Python generator in
  `build_scripts/pensieve_scenes/`, the physics traps (no unsupported gravel or sand, no open water
  edges), and `SceneDataTest` passing.
- Avoid switch-on-enum in render code, and entity event bytes 21, 35 and 63 (project rules).
- The user caps weekly usage per session. This is a big job, so the plan must split it into stages
  the user can stop between.

## Build order (each one watchable in game when done)

1. Engine work 1-5 and the new sorting quiz, with placeholder voices. Sorting works end to end
   with a short stub memory.
2. Privet Drive and the letters (engine work 7).
3. The island crossing and the hut (engine work 8).
4. Diagon Alley.
5. King's Cross, the viaduct and the train (engine work 6).
6. The boats and the castle.
7. The Great Hall (engine work 9) and the handoff to the hat.
8. Final voices, music fitting and a full pass watched in game.

## Decided by the user

- About 4 minutes. Starts when the Sorting Hat is put on.
- The child in the memory is the player, in their own skin.
- Spoken lines never use names ("boy", "you"). The player's name appears only as on-screen text.
- The player is always treated as a boy, in every line and text ("boy", "Mr. ___"). No girl
  variants, no choice.
- Children are child-sized and Hagrid is giant-sized, as in the film.
- First person only where it is needed (the eyes opening, the hat coming down); everything else is
  shot from outside like a film.
- The train: the Glenfinnan Viaduct shot and a cosy compartment with Ron and Hermione.
- The Great Hall: seated older students, all the teachers including Hagrid and Dumbledore, floating
  candles, the enchanted ceiling, good music, names called and sorted, then the player's name.
- The wake-up in first person (eyes opening, lying down). The island crossing with waves. Everything
  as close to the film as possible.
- The quiz: about 10 questions, 3 at random, 4 choices each. A tie lets the player pick. The hat is
  voiced with Fish Audio.
- Claude may find skins, structures and voices online, asking first; the user may supply them.

## My calls (flagged for the user)

- Leaving the memory early goes straight to the questions; dying or disconnecting restarts the whole
  thing the next time the hat is worn.
- Answers are shuffled each time and are not voiced.
- The memory ends in first person under the hat's brim, mirroring the eyes opening at the start.
- The Ollama sorting code and its config are removed, not kept as an option.
- The rowing boat and the lake boats reuse `cave_boat` if it reads right; otherwise a new boat.
- Being yanked out of the memory (`ExitReason.YANK`, the real body badly hurt) counts like dying:
  nothing is saved and it restarts next time, so the quiz never opens on a player under attack.
- **Build wait:** the memory opens in darkness, with the hat's voice murmuring that it will look
  into your past, which covers the first set building. The locations are chained scenes, each
  building while the one before plays, so there is no wait between them. Chaining also solves the
  size cap (engine work 10). Not the builder's choice any more.
- **Shipping:** all of it stays on its own branch until stage 8 is done and watched. The old AI
  sorting keeps working on `main` until then, so nothing half-built reaches players.

## Left to the builder

- The exact shot list, camera moves and timings inside each beat.
- The layout of the locations inside the 400 x 400 area.
- The data format of the question bank and the exact wording of the questions and lines (shown to
  the user before voicing).
