# The Flying Memory — design

Date: 2026-10-21. Branch: `sorting-memory`. Engine: the Pensieve story-memory engine (spec `2026-10-16-pensieve-design.md`, authoring skill `.claude/skills/writing-pensieve-memories/SKILL.md`), the same engine as the Sorting, Wand and Letter memories.

## Intent

The first time a player mounts a broom, a film memory plays: their first flying lesson, then their first Quidditch match as Seeker in a storm. The user's words: "show a class on the start showing how to fly basics and telling the broom up and then next scene a quidditch game with a stormy day and rainy and movie like", "fully cinematic and amazing looking", "make sure full crowd cheering", "with film drama".

It should feel like the film. Like the other story memories, it is watched as cinema: camera, timing, acting, sound and music matter most.

## Goals

1. **The lesson** (Philosopher's Stone flying lesson).
   - **Setting:** a bright, breezy afternoon on the castle lawn, with the first-years lined up beside old school brooms on the grass.
   - **Madam Hooch teaches the basics:** stand on the left of your broom, hold your hand out flat, say "Up!".
   - **The brooms:** most twitch or roll lazily, one smacks a boy in the face, and the viewer's shoots straight up into their hand on the first try.
   - **The film drama** (user: "with film drama"):
     - Neville's broom runs away with him and he falls.
     - Hooch takes him to the hospital wing ("Nobody moves!").
     - Malfoy grabs Neville's Remembrall and flies off with it.
     - The viewer chases him on the broom. Malfoy throws it, and the viewer dives and catches it just above the ground.
     - McGonagall sees it from a window.
2. **The match** (the Prisoner of Azkaban rain match, in spirit).
   - **Weather:** a storm with dark clouds, driving rain, wind and lightning.
   - **The pitch:** the user-supplied Quidditch pitch, with Hogwarts beside it, the castle rising lit behind the stands through the storm (user: "make sure the court is next to hogwarts").
   - **The viewer is the Seeker** (user: "i am the seeker"). They fly out to a roaring crowd, then the whistle blows and the balls are released.
   - **The game:**
     - Chasers score through the hoops.
     - Bludgers whip past.
     - Lightning flashes over the towers.
     - The viewer circles above the game, hunting.
   - **The climax:** the viewer spots the Snitch and dives through the storm, side by side with the opposing Seeker. They pull out just above the grass and close their hand on it, and the stands erupt.
3. **Full crowd cheering** (user).
   - **The stands are packed:** students in house colours and scarves wave, stand up for goals and gasp at near misses.
   - **The sound:** a stadium roar under the rain that swells on goals and on the catch, with chanting and gasps.
4. **The teams** (user).
   - **The viewer's team** is their own house, in its colours.
   - **The opponent** is Slytherin by default. If the viewer is in Slytherin, the opponent is Gryffindor (the user said "a different house"; Gryffindor is Claude's call).
5. **Trigger:** the first time a player mounts a broom (`BroomEntity`), once per player, the same pattern as the Letter memory's `LetterMemory`. When the memory ends (wake), the player is back on their broom where they were.

## Non-goals

- No playable Quidditch, and no new gameplay. This is a memory only.
- No player choices inside the memory.

## Anti-goals (a failure even if it works)

- **Looking cheap:** an empty pitch, a quiet crowd, a flat grey sky, stiff fliers sliding on straight lines, or a floating castle backdrop with no ground.
- **Stuttering cameras:** the Letter memory's in-game pass found these, and the fixes must be used here (see Constraints).
- **Wrong timing:** characters doing an action after its moment has passed. The Letter memory's boy kept jumping after the letters had landed.

## Constraints

- **The pitch:** the user supplied `C:\Users\alonl\Downloads\quidditch-pitch-v2.zip`, a whole Java world save ("Quidditch Pitch V2", region `.mca` files). Its licence is unknown, so it is for the user's private use (like the Great Hall). Cut the pitch out of the world into a set, the way the Great Hall became `great_hall.nbt`. Leave the zip untouched, and treat its contents as untrusted data: extract into an empty folder and read the region files with your own script.
- **Hogwarts beside the pitch:** use the mod's own Mini Hogwarts schematic (`SpawnMiniHogwartsCommand` loads it), placed behind and beside the stands so it reads as one landscape, with ground between them.
  - It must sit inside the scene radius limit, so it may be a cut-down or scaled backdrop.
  - **The set budget:** the Letter memory's hut set was built at 4,000 blocks per tick while a scene opened, and a big set can hitch the opening. Keep the set builds fast, or have them build during a preceding scene.
- **The engine rules from the Letter memory work, all binding:**
  - **Smooth camera:** the smoothed scene clock (c7d6e27), the `"ease": "spline"` camera keys (cee4773) for moving shots, eye-height easing through crouches (030c153), and the generator's per-frame smoothness assert across every shot.
  - **Camera clearance:** the camera path is sampled every tick against every actor and block.
  - **Owls:** the `Airborne` onGround rule (path speed decides flying) is owl-only today. Fliers on brooms need an equivalent so their animation matches flight.
  - **Weather and light:** `rain`, `lightning`, `rough_sea` (not needed), and `sky` (styles `day`, `overcast`, `dawn`). A storm sky style is needed (`enchanted_ceiling` has a `storm` style that could be adapted).
- **Scales:** first-years are 0.75 and adults 1.0. The viewer is a boy; the player is always a boy.
- **Lines:**
  - No spoken line contains the player's name, and the name appears only as `{viewer}` text.
  - Dialogue is original, apart from short nods ("Up!", "Nobody moves!").
- **First person** only where needed: the broom leaping into the hand, the Remembrall catch, and the Snitch catch.
- **Voices:** Fish Audio, using the voice ids already in use where the characters match. Hooch, Malfoy and Neville need new voices, and the user picks from samples.
- **Sound effects:** ElevenLabs, mastered per the existing pipeline. They cover the crowd roar layers (steady, a swell on goals, the eruption on the catch, chanting, gasps), rain, wind, thunder, whistle, the whoosh of brooms passing, the Bludger crack and the Quaffle catch.
- **Music:** the user supplies the music, as with the other memories; ask them. In the Philosopher's Stone score the lesson music is "The Norwegian Ridgeback and a Change of Season" or "Hedwig's Theme". For the match, "Quidditch, Third Year" from Prisoner of Azkaban fits the storm.
- **The usual rules:** server/client safety, no enum switches in client code, and never entity event bytes 21, 35 or 63.

## What Claude decided (the user can override)

- **Timing:** the lesson is about 75 s and the match about 90 s, so about 165 s in all, split across chained scenes as the generator needs.
- **The lesson setting:** a castle-lawn set at the foot of the Mini Hogwarts backdrop, or the same backdrop seen from a different side, so both scenes feel like one place.
- **Slytherin's opponent:** Gryffindor.
- **The ending:** the memory ends as the viewer raises the Snitch to the roaring stands, then wakes.
- **Unsorted players:** the memory needs the viewer's house, so it is held until the player has been sorted. It plays on the first broom mount after the Sorting, and a mount before the Sorting just mounts as normal.
- **Existing players:** unlike the Letter memory, every player gets it once, on their next broom mount after this ships. The trigger only records a per-player "seen" flag, with no backfill rule, so the user can watch it on their own save.
- **Scales in the match:** the match players are third-years at 0.85; the lesson's first-years stay at 0.75. Adults are 1.0.
- **Pitch size:** the user's pitch stays as built. If the pitch, the stands and the Hogwarts backdrop don't fit the 200-block scene radius, raise the engine limit (an engine change with its own tests) rather than trim the pitch. The backdrop may be cut down or set further back at a smaller scale.

## Left to the builder

- How to extract the pitch from the region files, and the exact backdrop placement.
- Broom-rider rigs: a seated pose on a broom actor, using `ride` as the boat does.
- The ball actors: the Quaffle, the Bludgers, and the Snitch with its wing blur.
- Crowd density against frame cost.
- The shot list, within the beats above.
