# The First Task Memory — design

Date: 2026-10-21. Branch: `sorting-memory`. Engine: the Pensieve story-memory engine (spec `2026-10-16-pensieve-design.md`, skill `.claude/skills/writing-pensieve-memories/SKILL.md`). Build it after the Flying memory (`2026-10-21-flying-memory-design.md`), and reuse that memory's crowd, broom riders, storm and cheering.

## Intent

The first time a player slays a Hungarian Horntail (`HungarianHorntailEntity`), a film memory plays: the First Task from Goblet of Fire, told as the memory of the champion who faced her. The user asked for it to be "fully cinematic and amazing looking" and approved the outline below as Claude proposed it.

## Goals: the beats (about 110 s in all)

1. **The tent, about 15 s.**
   - Inside the champions' tent, the crowd's roar comes through the canvas and the ground shakes from a dragon out of sight.
   - The cannon booms and the viewer's turn is called.
   - They walk out through the tent flap into blinding daylight.
2. **The arena, about 20 s.**
   - A rocky ravine with packed, cheering stands on every side.
   - On a nest of boulders sits the Horntail: black scales, a spiked tail, yellow eyes. The golden egg glints among her eggs.
   - She rears up and roars, and the crowd goes quiet.
3. **"Accio Firebolt!", about 15 s.**
   - The viewer dodges a jet of fire behind a rock, raises their wand and summons the broom (the mod's Accio).
   - It streaks in over the stands, and they jump on as fire scorches the rock behind them.
4. **The chase, about 45 s, the centrepiece.**
   - The viewer dives past her and she snaps at them.
   - Her chain snaps and she launches after them.
   - She chases them out of the arena and across Hogwarts' towers and roofs, breathing fire and smashing slates.
   - They weave under a bridge, and she crashes into it.
5. **The egg, about 15 s.**
   - The viewer loops back into the arena alone and dives at the nest.
   - In first person, their hand closes on the golden egg.
   - The crowd erupts, the cannon fires, and the memory wakes.

## Non-goals

- No playable fight.
- No other champions or other dragons on screen, apart from a glimpse of the tent.

## Anti-goals

- **Cheap-looking shots:** a dragon sliding stiffly on straight lines, fire that reads as particles in a void, an empty or quiet crowd, or a castle with no ground.
- **Stuttering cameras**, or actions that run past their moment (the Letter memory lessons).
- **A dragon that clips through towers** or the bridge, except the scripted crash.

## Constraints

- **Engine rules and tools from the Letter work, all binding:**
  - the smooth clock;
  - `"ease": "spline"` for every moving camera, and the generator's per-frame smoothness and per-tick clearance asserts;
  - the eye-height easing;
  - `lightning`, `rain` (not needed), `sky` (day, possibly with drifting cloud), and fire effects, either existing ones or a new dragon-fire breath effect that reads as a real jet of flame with heat shimmer, smoke and scorch.
- **The Horntail:** use the mod's own `HungarianHorntailModel`, rigged as a memory actor that can fly along spline paths with wingbeats matched to its speed, roar and breathe fire (a `look`/pose plus a fire event), and crash. The chain can be a simple actor or prop.
- **Hogwarts:** for the chase, use the mod's Mini Hogwarts schematic, the same backdrop as the Flying memory, but close enough to fly over and through its towers and roofs. The bridge must be a real piece of the set, so the crash lands on it.
- **The arena:** a new set, a rocky ravine with a stand ring and the nest of boulders, built by a Python set generator like the others.
- **Reuse from the Flying memory:**
  - the crowd and its cheering, gasps and roar layers;
  - the broom rider rig;
  - the champion (the viewer, as a 14-year-old, scale 0.85).
- **The cannon:** a sound, and a puff of smoke from the stands.
- **Sound effects:** ElevenLabs, covering the Horntail roar, the wingbeats, the fire roar, the chain snap, slate smashing, the bridge crash, the cannon and the crowd. **Voice:** a commentator line or two would be welcome (Bagman-style, original lines), as would the viewer's "Accio Firebolt!"; the user picks voices from samples.
- **Music:** the user supplies it. Ask them; "Harry in Winter" or "The Dragon Task" style cues from the Goblet of Fire score are the fit.
- **Lines:** no spoken line contains the player's name, and the name appears only as `{viewer}` text.
- **First person** only for the summon, the moment of mounting if needed, and the egg grab.
- **The usual rules:** server/client safety, no enum switches in client code, and never entity event bytes 21, 35 or 63.

## Claude's calls (the user can override)

- **Trigger:** the first time a player kills a Horntail, once per player. The memory starts when the kill's death animation has finished, so the fight's end is not cut off. If the player is already in a memory, it waits.
- **Existing players:** everyone gets it once, on their next Horntail kill.
- **The broom in the memory** is a Firebolt look-alike, whatever the mod's best broom model is.
- **The champion's age:** the viewer is a 14-year-old at scale 0.85, the same as the Flying memory's third-years.
- **Build order:** the Flying memory is built first (the user's order). This memory's plan reuses the crowd, the broom-rider rig, the flier-airborne rule and the crowd sounds that the Flying plan builds, and does not rebuild them.
- **Who it plays for:** every player who damaged that Horntail in its last 60 seconds and is within 64 blocks when it dies. A kill by a non-player (a wolf, fire, an Imperio'd mob) counts for the players who damaged it in that window. Each player gets it once.
- **No house gate:** the champion wears house-neutral Triwizard champion robes, the crowd is all four houses plus the foreign schools, and no line uses the house. An unsorted player who kills a Horntail sees it straight away.
- **No reward:** the player wakes with nothing new; a golden egg item is a non-goal for now, and the user can ask for one later.

## Left to the builder

- Set layouts.
- The shot list within the beats.
- The dragon's flight paths and fire timing.
- The crowd density against frame cost.
- How the arena and the castle are placed in one scene radius, or split across chained scenes.
