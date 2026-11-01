# Living Portraits: the Fat Lady (design)

## Intent

Harry Potter living portraits for the mod, starting with one: the Fat Lady. She hangs on a wall like a vanilla
painting, notices what happens around her and talks about it out loud in her own voice (Fish Audio "Voice A").
Most of the time she is a **paper puppet**: one painting stretched on a grid mesh (breathing, head tilts toward
you, eyes follow you, blinks, mouth moves with the voice, face changes mood). For three big moments she plays a
**Flow video** in which her lips say the line, dubbed with Voice A.

The approved prototype is `tools/living_portraits_spike/fat_lady_test.html` (served by the `portrait-spike`
launch config); its source is `tools/living_portraits_spike/scripts/template.html`. The in-game puppet should
look and move like that page.

## Goals / non-goals

- Goals: 19 reactions (below), 2+ lines each, never chatty (one line at a time, cooldowns), safe on a dedicated
  server and in multiplayer, she can be placed by players (item) and hangs in Mini Hogwarts.
- Non-goals: other portraits (later), Gryffindor password mechanic, portraits visiting each other's frames.
- Anti-goals: talking over herself, repeating the same line twice in a row, crashing a dedicated server,
  a frozen-looking painting.

## Assets (made by the art pipeline, not the Java builder)

All under `src/main/resources/assets/hogwartsmagic/`:

| Path | What |
|---|---|
| `textures/entity/portrait/fat_lady/base.png` | 512x1024 painting |
| `textures/entity/portrait/fat_lady/{blink,look_l,look_r,talk_ah,talk_oh,mood_happy,mood_cross,mood_scared}.png` | 512x1024 RGBA overlays: same painting, alpha = feathered mask of the changed face part (zero elsewhere) |
| `textures/entity/portrait/fat_lady/frame.png` | 64x64 gilded frame texture |
| `textures/entity/portrait/fat_lady/clips/<clip>/000.png ...` | video frames, 192x384, clips `creeper`, `punched`, `sing` |
| `sounds/portrait/fat_lady/<key>.ogg` | one per line in `FatLadyLines` |
| `portraits/fat_lady.json` | `{"lines": {key: {"seconds": s, "envelope": [0..1 every 50 ms]}}, "clips": {clip: {"frames": n, "fps": f}}}` |

`look_l` = pupils toward the image's LEFT edge. `sounds.json` entries `portrait.fat_lady.<key>` are added by
the pipeline too.

## Code

Package `com.hogwartsmagic.portrait` (common) and `com.hogwartsmagic.client.portrait` (client only).
**Do not edit** `ExampleMod.java`, `event/ClientModEvents.java`, `lang/en_us.json`, `entity/ModVillagers.java`
(another session has uncommitted work in them). Register everything from new classes instead.

- `FatLadyLines` (exists, generated, do not hand-edit): `Reaction`, `Mood`, `Line(key, reaction, mood, seconds, clip, text)`, `LINES`.
- `PortraitRegistry`: `@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = MOD)`. In `RegisterEvent`
  construct and register (construct INSIDE the event: items and entity types take intrusive holders):
  entity type `hogwartsmagic:fat_lady_portrait` (MISC, size 0.5x0.5, clientTrackingRange 10, updateInterval
  Integer.MAX_VALUE like the vanilla painting), item `hogwartsmagic:fat_lady_portrait`, and one SoundEvent per
  line `hogwartsmagic:portrait.fat_lady.<key>`. Add the item to `ExampleMod.MAIN_TAB` in
  `BuildCreativeModeTabContentsEvent`. Names come from literals (`getTypeName`, `Item#getName` overrides:
  "The Fat Lady"), not lang keys.
- `FatLadyPortraitEntity extends HangingEntity`: 2 blocks wide, 4 tall. Copy the vanilla `Painting` mechanics
  (bounding box from `calculateBoundingBox` with width 2 / height 4, facing in save data and in the add-entity
  packet, `moveTo`/`lerpTo`/`trackingPosition` overrides, placement sound, drop the portrait item).
  Breaking: a normal punch from a player does NOT break her: it triggers `PUNCHED`. Sneak + punch takes her down
  (drops the item unless creative). Explosions never hurt her (they trigger `EXPLOSION`).
  Synced data: `SERIAL` (int, +1 per new line), `LINE` (index into `LINES`), `LOOK` (entity id of the player
  she looks at, -1 none).
- `FatLadyPortraitItem`: places her like `HangingEntityItem` (horizontal clicked face, `survives()` check).
- `PortraitEvents` (FORGE bus, common): `ExplosionEvent.Detonate` and `LightningBolt` joining the level notify
  portraits in range.
- Server brain in the entity, every 10 ticks, idle when no player within 24 blocks. One line at a time:
  `busyUntil = now + seconds*20 + 30`. Only CREEPER, PUNCHED, EXPLOSION, THUNDER may interrupt a line, and not
  each other. Never the same line twice in a row for a reaction. For reactions with a video line (CREEPER,
  PUNCHED, SING) use the video line when that clip has not played for 2 minutes, else the puppet line.
  "Near" = within 6 blocks, in front of the canvas (dot with facing > 0), with line of sight.

| Reaction | Trigger | Cooldown |
|---|---|---|
| GREET | player becomes near (robes below take precedence) | 60 s per player |
| ROBES_GRYFFINDOR/SLYTHERIN/RAVENCLAW/HUFFLEPUFF | like GREET, chest item id path contains house name and "robes" | 60 s per player |
| LEAVE | a greeted player goes beyond 10 blocks | 60 s per player |
| DARK | player within 8, `getMaxLocalRawBrightness(front)` <= 4 | 90 s |
| LIGHT_BACK | after DARK, block light at front >= 7 with a player near | - |
| CREEPER | creeper within 7 blocks | 20 s |
| HOSTILE | other `Enemy` within 8 blocks | 25 s |
| PUNCHED | player hits her (not sneaking) | 3 s |
| EXPLOSION | explosion within 20 blocks | 8 s |
| RAIN | rain starts with a player within 16 | 10 min |
| THUNDER | lightning within 96 blocks | 20 s |
| PLAYER_HURT | near player at <= 6 health | 60 s per player |
| WAND | near player holding an item whose id path contains "wand" and looking at her (dot > 0.97) | 40 s per player |
| SING | a player near for 30 s and she has been quiet 15 s | 180 s |
| MORNING / NIGHTFALL | day time enters day / night (>= 13000) with a player within 16 | per transition |

- Client (`client.portrait`): register the renderer in a MOD-bus `Dist.CLIENT` subscriber. A FORGE-bus client
  tick handler watches every portrait near the player: when `SERIAL` changes it starts that line: plays the
  sound at her position (`SoundSource.VOICE`), shows `The Fat Lady: "text"` in the action bar if within 16
  blocks, and starts the animation. The first `SERIAL` value seen for an entity is recorded without playing.
- Renderer: orient like `PaintingRenderer`. A grid mesh (25 x 45 vertices) over the 2x4 canvas, deformed per
  frame with the prototype's `deform()` (head bend about the neck, breathing, flinch, edges pinned). Draw
  `base.png`, then each overlay with alpha = its weight, each a hair closer to the viewer, translucent.
  Light = the world light at her front (night darkness and torches show for free). Gilded frame border + back.
  A video line draws one flat quad with the current clip frame instead of the puppet, then returns to idle.
  Video frame textures load on first use and are released 30 s after the clip ends.
- Puppet constants (painting pixel space 688 x 1376, the texture is that scaled to 512x1024): head ellipse
  center (355, 400) radii (200, 235) falloff 0.8..1.45; torso center (350, 960) radii (440, 580) falloff
  0.55..1.1; neck pivot (348, 600); edge pin ramp 70 px.
- Moods: the line's mood face is shown during the line and 0.6 s after. SCARED: flinch kick and tremble, no mouth
  overlays. HAPPY: small bounce. CROSS: turns her head slightly away from the player. Mouth overlays follow the
  envelope (> 0.55 ah, > 0.18 oh). Eyes follow the `LOOK` player (image-right = facing.getCounterClockWise());
  idle glances otherwise; blink every 2.5-6 s.

## Mini Hogwarts

After the castle finishes (`MiniHogwartsManager.tick`, after `furnish()`), hang one Fat Lady on a free 2x4
wall near the Great Hall spot. (Done by the art/integration side.)

## Rules from this repo's history

- No `switch` on an enum in client render code (the synthetic `$1` class failed to load in this dev env).
- Common code never touches client classes; the entity polls nothing on the client side itself.
- Never run gradle while a Minecraft client from this repo is running.
