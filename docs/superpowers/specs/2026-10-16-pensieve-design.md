# The Pensieve — design

Date: 2026-10-16. Mod: HogwartsMagic (Forge 51, Minecraft 1.21).

## Intent

A player pours a memory into a stone basin, leans in, and is pulled into a past moment, the way the films show it. The point is the *feeling*: being sucked into silver liquid, falling through darkness, and watching a remembered place rebuild itself out of shadow, then reliving a real moment exactly as it happened. The bar the user set is that every transition should look cinematic, the kind of thing people do not expect a Minecraft mod to be able to do.

Memories are real moments players lived through on their own world (a fight, a duel, a build, a conversation in chat), so the Pensieve is also a way to keep and share stories between players.

## Goals

1. **Capture** the last 60 seconds around a player, after the fact, into a memory phial item.
2. **Replay** that memory inside a Pensieve: the exact blocks, every player and creature moving exactly as they moved, and the chat as it was sent, played once from start to end like a film.
3. **Cinematic transitions** in, out, and when torn out.
4. **Shared viewing**: several players can dive into the same playing memory and see each other inside it as faint silver ghosts.
5. **Story memories**: hand-made cinematic tutorials shipped with the mod, written as scene files Claude can author: the Locket cave, the Basilisk in the Chamber, and the Voldemort summoning.

## Non-goals

- No pause, rewind, scrubbing or speed control. A memory plays once, start to finish (user's decision).
- No interaction with the memory. Viewers cannot place, break, hit, use, open, or pick up anything, and nothing in the memory reacts to them.
- No editing or trimming of memories.
- No recordings longer than the buffer (60 s default).

## Anti-goals (would be a failure even if it technically works)

- A replay that changes the real world in any way (no real blocks touched, no real entities spawned outside the memory space, no loot or XP generated).
- A capture or session start that causes a visible lag spike or a server freeze.
- A viewer dying "trapped" inside a memory with no warning.
- Anything that breaks on a dedicated server, or trusts the client for anything authoritative.
- Transitions that look like a plain fade-to-black teleport. If it looks like a dimension change loading screen, it has failed the brief.

## Player experience (decided with the user)

### Capturing a memory

- The mod quietly keeps a rolling record of the **last 60 seconds** around every player: the blocks within about **32 blocks** of them, the movement and actions of players and creatures in that area, and the chat they saw. Both numbers are configurable (my call on the defaults).
- To draw out a memory: hold a **wand** in one hand and an **Empty Memory Phial** in the other, **sneak and use the wand**. The camera tilts back slightly, a silver thread is drawn from the player's temple along the wand into the phial, with a soft whispering sound.
- The phial becomes a **Memory** item named after the moment, e.g. "Alon's Memory — Day 214". It can be renamed at an anvil, given away, traded and stored.
- Watching a memory does **not** use it up.
- Empty Memory Phial recipe: glass bottle + Arcane Dust (my call).

### The Pensieve block

- A stone basin with a swirling silver surface.
- One stands in the headmaster's office at Mini Hogwarts (my call). Players can also craft one from a stone basin shape, polished deepslate and Arcane Dust (my call; exact recipe left to the builder).
- Using a Memory item on the basin pours it in; the memory stays in the basin (the phial comes back empty-labelled or the item is held by the basin — builder's choice, but the player must be able to take the memory back out).
- From outside, other players see a viewer bent over the basin, and the silver surface swirls brighter while a memory is playing in it (my call).

### Entering (cinematic)

1. Player uses the basin. The camera is pulled down into the silver surface, as if the liquid swallows them.
2. Darkness. The player falls through a black void with drifting silver wisps (my guess at the look, matching the repainted memory phial).
3. The memory builds itself out of shadow: grey, shadowy shapes first, sharpening into the real blocks, players and creatures. It builds outward from the centre (my guess).

### Inside the memory

- The remembered area **floats in a dark shadow void**. Only what was remembered exists. At the edge of the remembered radius the world **frays and dissolves** into drifting dark fragments and then nothing: no sky, no distant terrain, only an island of memory hanging in the dark (user's idea; the gradual fray is my guess).
- A slightly washed-out, silvery colour grade so it always reads as a memory (my guess).
- **Free ghost camera** by default: the viewer floats and flies freely but cannot leave the remembered area (invisible bounds). One key snaps the viewer into the eyes of the memory's owner; the same key releases them (my recommendation, accepted).
- Everything moves exactly as it really did: walking, sneaking, swinging, held items, taking hits, doors opening, blocks being placed and broken, at original speed.
- Chat messages appear as they were sent, in the same silvery tone.
- Sound (my call): footsteps, hits and blocks breaking from the memory play muffled and slightly echoing, as if heard through water, over a low void hum.
- Other viewers in the same memory appear as faint silver ghosts.

### Leaving (cinematic)

- When the memory ends, it dissolves back into shadow and the player is pulled up out of the basin, back where they were standing. The exit mirrors the entrance (my guess).
- A viewer may also leave early (key or menu; builder's choice).

### The body left behind (user's decision)

- While a player is inside, their body stays bent over the basin, frozen and **helpless**. Mobs and players can hit it.
- A hit is heard inside the memory **from far away**, like a thud through water. The edges of the void flicker faint red and the scene shudders slightly. The viewer stays in.
- If the player's health falls **below a third**, the memory tears apart and they are violently yanked out: a sharp gasp, a flash, and they are back at the basin with whoever is attacking them.

### Shared viewing (user's decision)

- Any player who uses a basin while its memory is playing joins that same playback, at its current moment.
- Viewers see each other inside as faint silver ghosts. Each viewer's body is separately left at the basin and separately vulnerable.

### Story memories: cinematic tutorials (user's decision)

Story memories are hand-made cinematic scenes that double as **tutorials**: each one shows, like a short film, how a piece of the mod works. The user wants them to be **easy for Claude to author**, so they are written as data, not recorded by hand.

**Authoring format (the core requirement).** A story memory is a text scene file (e.g. `data/hogwartsmagic/memories/<id>.json`) that a model can write and iterate on without touching Java:
- **Set**: where the blocks come from. Either a structure `.nbt` shipped with the mod, or one of the mod's existing generators run into the memory space (the Locket cave builder, the Chamber builder, a small ritual clearing), plus optional block edits.
- **Actors**: each with an entity type (or a player skin), a name, and a timeline of actions: walk/fly to a point, look at, swing, sneak, hold an item, cast a named mod spell at a target (using the real spell visuals), take a hit, die, vanish into smoke.
- **World events on the timeline**: block place/break, sounds, particles, mod effects (e.g. the summoning ritual entity, a basilisk gaze).
- **Words**: chat lines from actors and silvery narration lines, timed.
- **Camera**: a directed camera track of keyframed shots (position, look-at, FOV, easing, cuts). See "Camera in story memories" below.
- **Length** and an optional end action (e.g. give a Cave Map).
- Time in ticks or seconds; positions relative to the set's origin.

**Fast iteration.** An op/dev command plays a scene file straight from disk and can reload it without restarting (e.g. `/pensieve preview <id>`), and a validation step (unit test or command) reports bad references (unknown entity, spell, structure, sound) by name. The goal: write a scene, preview it, adjust, repeat, in minutes.

**The first three scenes** (scene content is my call; Claude writes them during the build):
1. **Memory of the Cave** (replaces today's three chat lines). A hooded figure stands on a clifftop over a black sea at night, finds the hidden crack with Revelio, swims the tunnel, pays the Blood Seal, raises the chain boat, rows to the island and stops at the Basin of Despair as the scene fades. It teaches the route without spoiling the drink and the ambush. After the memory, the vial still gives the Cave Map to the nearest unlooted cave, so the Pensieve is an alternative to drinking it (my call; drinking still works as today).
2. **Memory of the Chamber** (Basilisk). A student enters the Chamber, the Basilisk wakes, the student's view goes grey at the edges under the gaze, they look away and fight from the edge of their vision, the serpent rears before a strike, spits venom, burrows; blows to the head blind it, and then it hunts by sound while the student crouch-walks. A second, careless figure meets its eyes and turns to stone, then a friend brings them back with a Mandrake Restorative Draught.
3. **Memory of the Summoning** (Voldemort). Death Eaters place three Wards in a diagonal triangle in a dark clearing, one strikes a Ward with a spell, the ritual builds (green beams, smoke, the Dark Mark), and Voldemort rises. It cuts away as he turns toward the viewer.

**Where players get them** (my call): the Memory of the Cave keeps its current sources. The Chamber memory is found in Mini Hogwarts library chests and Chamber-adjacent loot. The Summoning memory comes from Death Eater camp chests and rare Death Eater drops. Story memory items are not consumed by watching.

**Camera in story memories (user's decision).** Directed like a film: the scene file's planned shots drive the viewer's camera from start to end (cuts, dollies, orbits, slow push-ins, FOV changes, eased moves). The viewer does not fly freely in a story memory. Player memories keep the free ghost camera.

**Claude writes memories on the user's command (user's decision).** After the build, the user can ask Claude in any session for a new memory ("make a memory of a duel between Harry and Draco in the Great Hall"), and Claude writes the scene file, previews it in the dev client, and iterates. To make that work, the scene format must give Claude:
- **Any player skin on any actor**: actors can be player models with a chosen skin, either a skin PNG shipped in the mod's assets or a Minecraft username's skin (my call: both; usernames resolve on the client and fall back to a default skin offline). Slim and wide arm models.
- **Real spellcasting**: actors cast any of the mod's spells at a target or a point, with the real beam, particles, sounds and impact visuals, without any of the spell's real effects on the world.
- **Body-part animation**: keyframed rotations (and offsets) of individual body parts for any actor: head, body, each arm and each leg for player models, and the named model parts of the mod's own creatures (e.g. the Basilisk's jaw and head, a Dementor's arms), with easing. These layer over the actor's normal walk/idle animation or replace it. Named reusable poses (e.g. "wand raised", "kneel", "cower") can be defined once and reused across scenes. Vanilla mobs (zombies, skeletons, villagers and the rest) get the same part-by-part control through their model parts.
- **The creatures' own animations by name**: a scene can trigger any of the mod's existing keyframed entity animations (e.g. the Basilisk's rear and strike, the Living Statue's kneel) at a moment on the timeline, and custom limb moves can layer on top of them.
- **Held and worn items**: wands, robes, armour, any item in either hand.
- **Clear errors**: a bad body-part name, skin, spell or entity is reported by name by the preview and the validation step.

A short authoring guide for the scene format ships as a Claude Code project skill (`.claude/skills/`), so a fresh Claude session knows how to write, preview and check a memory without rediscovering the format.

## Constraints from this codebase

- **Server/client safety**: every authoritative action server-side; never reference client-only classes from common or server code (use the existing `ClientSideBridge` pattern). Must run on a dedicated server.
- **Big builds must not happen in one tick.** Placing a memory's blocks must be spread over ticks (see how Mini Hogwarts is placed), never as a worldgen feature.
- There is already a custom flat empty dimension (`data/hogwartsmagic/dimension/diagon_alley.json`); a similar empty dimension is a natural home for memory playback.
- Forge 51 has no HUD event; HUD overlays are drawn with a mixin into `Gui#render` (see `mixin/client/*GuiMixin`).
- World rendering via `RenderLevelStageEvent` must start from an identity matrix, not `event.getPoseStack()`.
- Avoid `switch` on enums in render/model code (synthetic switch-map class has failed to load in this dev environment).
- `broadcastEntityEvent` bytes 21/35/63 crash the client; use 61/62/64/66/67.
- Custom screens use `renderTransparentBackground`, never the blur backdrop.
- Never run gradle while the dev client is running.
- New network packets go on the existing `SpellPackets` channel; bump its protocol version.

## Technical direction (my calls; builder may improve on them with reason)

- **Playback space**: a dedicated empty "memory" dimension. Each playing memory gets its own far-apart region in it; the server rebuilds the snapshot's blocks there (spread over ticks, centre outward to match the "builds from shadow" look) and clears it when the last viewer leaves. The viewer's real player is moved there; a stand-in **body** entity (the player's skin, bent over the basin) stays at the Pensieve, receives damage and forwards it to the real player.
- **Recorded actors**: replayed by puppet entities that render as the original entity type and the original player skin, follow the recorded track exactly, and have no AI, no drops, no collisions with viewers, and cannot be damaged.
- **Recording**: per player, a rolling buffer of block changes (old and new state), per-tick entity tracks (position, rotation, pose, held/worn items, swing/hurt events) for entities within the radius, and chat lines. On capture, the block snapshot at the start of the window is reconstructed from the current blocks plus the buffered changes. Caps on entity count and data size are configurable.
- **Storage**: the Memory item holds an id, title, owner and date; the recording itself is stored compressed in the world save, not in the item.
- **Viewer**: a ghost state with flight and no interaction; bounded to the memory's radius; immune to everything inside the memory.

## Calls made after the builder review (my calls)

- **Remembered area.** A fixed area centred on where the owner stood at the moment of capture (32-block radius, configurable). If the owner moved away during the 60 seconds, they walk off the edge of the island and dissolve into the void, which fits the memory fading at its edges. If the window crosses a teleport, apparition, Floo trip or dimension change, the memory starts after the last one. Snap-to-owner follows the owner even off the island: there they are seen floating in the void.
- **What "exactly as it happened" covers.** Everything the owner could see inside the area: all entities (players, creatures, spell beams, projectiles, dropped items), all block changes whatever caused them (players, fire, fluids, explosions, pistons, Fiendfyre), sounds, particles, and the mod's client-drawn spell effects (Protego, the Patronus shield, Incarcerous chains and others). Duels must replay convincingly. My suggested direction: record what the server sends to the owner's client (entity, block, sound, particle, chat and mod effect packets) and filter it to the area, rather than adding a hook to every spell. The builder may choose another approach that meets this fidelity at an acceptable cost for the always-on buffer.
- **Capture gesture.** With an Empty Memory Phial in the other hand, sneak + use with a wand captures a memory instead of opening the spell menu. Without a phial, nothing changes.
- **The Mini Hogwarts Pensieve.** There is no headmaster's office in the schematic. The basin is placed in a suitable existing room when Mini Hogwarts is placed (`MiniHogwartsManager`). Copies already placed in existing worlds do not get one; players there craft their own.
- **Who can take a memory out.** Only the player who poured it in, or an op. On the public Mini Hogwarts basin, anyone can take it out while nobody is inside.

## Edge cases the builder must handle

- Viewer disconnects or the server stops mid-memory: on next login the player is back at their body's position, never stranded in the memory dimension.
- The basin is broken, or the memory taken out, while people are inside: everyone is pulled out with the exit transition.
- The body is killed outright by a single big hit: normal death, viewer leaves the memory.
- Capture with no wand or no empty phial: nothing happens, with a short hint.
- Capturing in any dimension except the memory dimension itself.

## Left to the builder

- Exact shader / rendering techniques for the swallow, the fall, the build-from-shadow, the fraying edge, the colour grade and the muffled sound, provided they meet the look described above.
- Exact timings of each transition (aim for the entry to feel like ~3 seconds of film, not a loading screen).
- Pensieve block model and texture, phial and memory item textures (keep the silver-wisp style of the existing Memory of the Cave).
- Recipe shapes, config names, the snap-to-owner key binding, the leave-early control.
- The exact syntax of the scene file, as long as it meets the authoring requirements above.

