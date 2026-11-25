# Living Famous Wizard Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 20 Famous Wizard Cards living portraits. They get a reveal with VFX when a Chocolate Frog box opens,
are drawn alive in the hand, in item frames and in the album, and the frog escapes through windows.

**Architecture:** Code and art are built side by side and meet at the asset contract in the spec.
- **Code (tasks C1–C4, one builder):** works against generated placeholder art.
- **Art (task D1, main session with the user):** fills the contract.

A single client class `CardArt` draws a living card into any `PoseStack`/`GuiGraphics` target. The reveal screen, the
hand render, item frames and the album all call it.

**Tech Stack:** Forge 51 (MC 1.21), Java 21, JUnit 5, Forge GameTest, Flow (Nano Banana images, Veo 3.1 video), Python
(cv2, numpy) for asset processing, and ElevenLabs SFX.

**Spec:** `docs/superpowers/specs/2026-11-25-living-wizard-cards-design.md`. Its values are binding.

## Global Constraints

- Work only in `C:/Users/alonl/hp-cards`, branch `living-cards`. Never run gradle in the main checkout.
- Client-only code goes in `com.hogwartsmagic.cosy.client.card`. Pure timing rules go in `com.hogwartsmagic.cosy.CardTimeline` (common, so
  JUnit can test them).
- No enum switch in render code. No entity-event bytes 21, 35 or 63. Screens use `renderTransparentBackground`.
- Assets follow the spec's contract exactly: the paths, 256×384 portraits, 320×448 frames, and `cards/<key>.json`.
- Commit after each task with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Don't push.

## Review Focus

1. **A card with no art folder:** the placeholder must draw with no missing-texture purple and no log spam. C2 has a test
   that `CardAssets.forKey("nonexistent")` returns a placeholder.
2. **Opening several boxes quickly:** reveals queue up, one at a time, at most 5 queued. C1 has a JUnit test on
   `RevealQueue`.
3. **Dedicated server:** the reveal packet's handler must touch no client class on the server. C1 routes it through a
   `DistExecutor`-safe client hook, and runServer stays clean.
4. **Frog near a window it can't reach** (glass behind a wall): it gives up after 60 ticks of no progress and hops away.
   C4 has a GameTest.
5. **Video memory:** the frames of an exit clip are released 30 s after their last use. C2 test with a fake clock.

---

### Task C1: Reveal packet, queue and reveal screen

**Files:**
- Create: `cosy/client/card/CardRevealScreen.java`, `cosy/client/card/RevealQueue.java`, `cosy/client/card/CardRevealClient.java`
- Modify: `cosy/CosyNet.java` (`CardRevealPacket(int cardId)`, server to client), `cosy/ChocolateFrogItem.java` (send it after the card is given)
- Modify: `ModSounds.java` and `sounds.json` (`card.lid_pop`, `card.shimmer`, `card.legendary_chime`, `card.walk_out`, `card.puff`),
  using placeholder OGGs (short silent or vanilla-derived) until D1 replaces them
- Test: `src/test/.../cosy/RevealQueueTest.java`

**Interfaces:**
- Produces:
  - `RevealQueue.offer(int cardId)` drops the offer when 5 are already queued
  - `RevealQueue.poll()` returns `OptionalInt`
  - `CardRevealScreen(int cardId)`: `isPauseScreen()` returns false. Phases are lid 0–0.3 s, frog 0.3–0.6 s, rise
    0.6–1.4 s, then idle. The card flies to the hotbar on a click, any key, or after 8 s.

- [ ] **Step 1:** Write `RevealQueueTest`:
  - FIFO order
  - a 6th offer is dropped
  - `poll` on an empty queue returns empty
- [ ] **Step 2:** Run it and confirm it fails.
- [ ] **Step 3:** Implement.
  - The screen draws the box (`textures/card/box.png`, or a placeholder) and its sparkles as GUI particles in the screen's
    own list, then the card via `CardArt.drawCard` at the screen centre.
  - The card tilts with the mouse: rotate the pose about X and Y by up to 10°.
  - The shimmer is a moving diagonal additive band, the name engraves letter by letter, and rarity adds motes or rays.
  - Each phase plays its sound.
  - On close, the next reveal in the queue opens.
- [ ] **Step 4:** Run the tests and runServer. Commit with "Reveal a Famous Wizard Card with lid, shimmer and light".

### Task C2: CardArt, the living card renderer

**Files:**
- Create: `cosy/CardTimeline.java` (pure), `cosy/client/card/CardArt.java`, `cosy/client/card/CardAssets.java`, `cosy/client/card/CardVideo.java`
- Create: `tools/living_cards/make_placeholders.py`, which writes placeholder `textures/card/*` and `cards/*.json` for all 20 keys
  (a tinted plate, a dark silhouette as the figure, eye dots), so the game runs before the real art exists
- Test: `src/test/.../cosy/CardTimelineTest.java`

**Interfaces:**
- Produces:
  - `record CardTimeline.State(boolean present, float figureOffsetX, boolean blink, int look, int clipFrame)`. `look`
    is -1, 0 or 1. `clipFrame` is -1 when no clip is playing.
  - `static State CardTimeline.at(long gameTime, float partial, int cardId, long viewSeed, boolean legendaryClip, int clipFrames)`
  - `static void CardArt.drawCard(GuiGraphics g, int cardId, long viewSeed, float x, float y, float scale, float partial)`
  - `static void CardArt.drawCard(PoseStack pose, MultiBufferSource buffers, int light, int cardId, long viewSeed, float partial)`,
    a 1×1.4 quad in local space for the hand and item frames
  - `CardAssets.forKey(String key)` returns textures plus the json, or a placeholder

- [ ] **Step 1:** Write `CardTimelineTest`. With timing in ticks:
  - `presentMostOfTheTime`: over 72 000 ticks, the figure is present for between 60% and 95% of the time.
  - `exitsWithinBounds`: every absent stretch lasts 400–1200 ticks.
  - `walkOutSlidesOffAnEdge`: during a walk-out, `|figureOffsetX|` rises steadily from 0 to at least 1.0, in portrait widths.
  - `legendaryUsesClip`: with `legendaryClip=true`, the exit has `clipFrame` running from 0 to clipFrames-1 at 12 fps and
    `figureOffsetX == 0`.
  - `sameInputsSameState`: the same inputs always give the same state.
  - `differentSeedsDiffer`: two seeds aren't in lockstep across 1000 samples.
- [ ] **Step 2:** Run it and confirm it fails.
- [ ] **Step 3:** Implement `CardTimeline` and the rest.
  - The cycle per card is seeded by `cardId*31 + viewSeed`:
    - present for 1200–3600 ticks
    - exit for 40 ticks (walk) or the clip length (legendary)
    - absent for 400–1200 ticks
    - return walk for 40 ticks
  - Blink for 3 ticks every 40–120 ticks. Glance for 20–40 ticks every 100–300.
  - `CardArt` draws layers into a scissored portrait window in this order:
    1. the empty plate
    2. the figure layer (offset, with a walking bob of ±2 px, and breathing at 1% Y scale)
    3. the eye overlays, only while present and not walking
    4. the clip frame when one is playing
    5. the frame
    6. the name plate text
  - `CardVideo` follows `PortraitVideo`: lazy load, prefetch 3, release after 30 s.
  - Run `make_placeholders.py` and commit its output.
- [ ] **Step 4:** Run the tests. Commit with "Draw Famous Wizard Cards as living portraits".

### Task C3: Hand, item frames, album

**Files:**
- Create: `cosy/client/card/CardHandRender.java` (`RenderHandEvent`, `Dist.CLIENT`), `cosy/client/card/CardFrameRender.java` (`RenderItemInFrameEvent`)
- Modify: `cosy/client/CardAlbumScreen.java` (living grid, click to enlarge into a non-reveal `CardRevealScreen` mode)

- [ ] **Step 1:** In first person, a held `wizard_card` cancels the vanilla hand item render and draws the card the way
  vanilla draws a held map. It's two-handed when the off hand is empty (the arms come up, as with `renderTwoHandedMap`),
  otherwise one-handed. The `viewSeed` is the player's UUID hash.
- [ ] **Step 2:** In item frames, draw the card quad flat in the frame. Respect the frame's rotation. The seed is the
  frame's entity id.
- [ ] **Step 3:** In the album, draw each collected cell with `CardArt.drawCard(g, …)` at its cell scale. Missing cards stay
  silhouettes. Clicking a collected card opens it large.
- [ ] **Step 4:** runServer must stay clean. Commit with "Show living cards in the hand, item frames and the album".

### Task C4: The frog escapes through a window

**Files:**
- Modify: `cosy/ChocolateFrogEntity.java`
- Test: `gametest/CosyFoodGameTests.java` (add to its batch)

- [ ] **Step 1:** Write the GameTests:
  - `frogEscapesThroughGlass`: glass 4 blocks away in the open. Tick by hand and the frog is removed before 100 ticks,
    with a puff (the removal reason is `DISCARDED`).
  - `frogGivesUpOnUnreachableGlass`: the glass is behind a stone wall. After 60 ticks with no progress it switches to
    hopping away.
  - The existing frog tests still pass.
- [ ] **Step 2:** Run them and confirm they fail.
- [ ] **Step 3:** Implement. At spawn, scan an 8-block box for the nearest glass block or pane and pick it as the target.
  Hops aim at it. Within 1.2 blocks of the target, broadcast event 61 (a client puff) and discard. If the distance doesn't
  improve for 60 ticks, clear the target.
- [ ] **Step 4:** Run the full `runGameTestServer`. Commit with "Chocolate Frogs make for the window".

### Task D1: Art (main session, with the user)

Work in the order below. Each step lands files on the asset contract and is committed when done.

1. **Paintings:** Flow, 3–4 takes per wizard. The user picks, in batches of 5.
2. **Empty plates:** a Flow edit of each base with the person removed. The user checks them.
3. **Overlays:** Flow edits for eyes closed, looking left and looking right. Then `tools/living_cards/make_cards.py`, adapted
   from `living_portraits/make_textures.py`, does the ECC alignment, crops to 2:3, scales to 256×384, writes the figure
   matte and writes `cards/<key>.json`.
4. **Frames, back and box:** Flow, picked by the user, cut to 320×448 with the window punched out. The 16×16 box sprite is
   hand-pixel, using the texturing skill.
5. **Legendary clips:** Veo 3.1, with first frame = base and last frame = empty, about 8 s. Frames are subsampled to 12 fps
   at 256×384.
6. **Sounds:** the 5 ElevenLabs SFX.
