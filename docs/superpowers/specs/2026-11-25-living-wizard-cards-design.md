# Living Famous Wizard Cards: design

## Intent

The 20 Famous Wizard Cards (cosy pack, `cosy/WizardCards`) are flat sprites. The user wants them to feel like the
first film's train scene. You open a pentagonal Chocolate Frog box, the frog bolts for the window, and the card shows a
living portrait whose wizard moves and sometimes walks out of the frame, leaving it empty. Opening a box gets full VFX.

**Anti-goals:**
- Copying the film's actual card artwork. Frames, box and portraits are original, in the film's spirit.
- Dialogue or lip-sync on the cards.
- Anything that runs on the server beyond rolling the card and spawning the frog.

## Approaches (picked: B)

- **A. Puppet only:** a painting, an empty-background plate and face overlays per card. Idle motion plus a procedural
  walk-out. About 40 Flow image credits.
- **B. Puppet for all 20, plus a Veo clip as the exit for the two legendaries** (picked by the user). That's A plus about 20
  video credits.
- **C. A video loop for every card:** about 200 credits, a big download, and visible repetition. Rejected.

## What the player sees

1. **The reveal, client-only, about 3 s.** It plays when you open a `chocolate_frog` box. The server rolls the card and tells that client which one.
   - 0.0 s: the lid pops with gold and cocoa sparkles and a pop sound.
   - 0.3 s: the frog (the existing `ChocolateFrogEntity`) leaps out.
   - 0.6 s: the card rises to screen centre, tilting up to about 10° with the mouse. A shimmer sweeps across the
     frame and the name engraves letter by letter.
     - Rare: silver motes.
     - Legendary: golden light rays and a chime.
   - A click, any key, or 8 s idle sends the card flying down to the hotbar, and the overlay ends.
   - The game is not paused. Escape closes it too.
2. **In the hand:** holding a `wizard_card` in first person draws it large, like a map. It's two-handed when the off hand
   is empty, otherwise one-handed. The portrait is alive.
3. **In item frames:** a card in an item frame draws flat and alive.
4. **In the album:** the existing 5×4 grid draws each collected card as a small living portrait. Clicking one opens it large
   in the same view as the reveal, without the box effects.
5. **The frog:** after leaping it looks for glass (a block or pane) within 8 blocks. If it finds some, it hops toward it and
   vanishes in a puff when it reaches the glass ("out of the window"). Otherwise it keeps its current hop-away
   behaviour. Catching it still feeds you.

## How portraits move

- **Idle (all 20):**
  - blinks every 2–6 s
  - glances left or right now and then
  - breathes: a vertical scale of about 1% on the figure layer
  - a slight sway on the puppet mesh, as `PuppetMesh` does for wall portraits
- **The walk-out (18 non-legendary cards):** every 60–180 s the figure layer (the painting minus the empty plate) slides off
  the left or right edge with a walking bob, clipped to the portrait window. The card then shows the empty plate for
  20–60 s, and the figure walks back in from a random side.
- **The legendary exit (Merlin, Dumbledore):** a Veo clip plays as the exit instead of the walk-out. Its first frame is the
  base painting and its last frame is the empty plate, and it plays at 12 fps.
  - Dumbledore smiles over his half-moon glasses, gives a small nod and steps out of the frame.
  - Merlin sweeps his cloak and vanishes in a puff.
  - They come back with the procedural walk-in.
- **Timing:** each card's state is computed from `(game time, card id, a per-view seed)`, so the same card in two places
  isn't in lockstep but stays stable from frame to frame. It's pure client maths with no saved state.

## Art (main session, the user picks)

- **Painting:** Flow (Nano Banana), 3–4 takes per wizard, and the user picks one. Style: an oil-painted portrait from the
  waist up, a period setting fitting the figure, warm light, and plenty of background around the figure, so the empty plate
  reads as a place.
- **Per card:**
  - `base`
  - `empty`: a Flow edit of base with the person removed and the background continued
  - `blink`, `look_l`, `look_r` overlays, aligned with ECC like `tools/living_portraits/make_textures.py`
  - `figure`: an RGBA matte from |base − empty|, feathered
- Textures are 256×384 (2:3).
- **Card frames:** three original gilded frames (bronze for common, silver for rare, gold for legendary) and a card back, all
  painted via Flow and cut to a 320×448 card with a transparent 256×384 portrait window. The name plate is drawn in code.
- **The pentagonal box:** a new `chocolate_frog` item sprite (16×16, hand pixel), plus the reveal's box drawn from a
  painted Flow image.

## Asset contract (code and art meet here)

`assets/hogwartsmagic/textures/card/`:
- `frame_common.png`, `frame_rare.png`, `frame_legendary.png`, `back.png` (320×448)
- `box.png` (the reveal box, RGBA)
- `<key>/base.png`, `empty.png`, `figure.png`, `blink.png`, `look_l.png`, `look_r.png` (256×384, RGBA overlays)
- `<key>/clips/exit/0000.png…` (legendaries only)

`assets/hogwartsmagic/cards/<key>.json`:
```json
{"eyes": [cx, cy, rx, ry], "face": [cx, cy, rx, ry], "exit_clip": {"frames": N, "fps": 12} | null}
```
The values are in texture pixels. A missing card folder falls back to a generated placeholder: a silhouette on a tinted
plate. This lets code land before the art.

## Constraints

- It must be dedicated-server safe: every renderer, overlay and screen is a client-only class, registered from
  `ClientModEvents` or `Dist.CLIENT` subscribers.
  - The server sends `(cardId)` in a play-to-client packet after it rolls the card.
  - The existing `CosyNet` channel and the card-gives-before-shrink rule stay as they are.
- The reveal is a `Screen` with `isPauseScreen() == false` and `renderTransparentBackground`, never `renderBackground`
  (avoid the blur). Forge 51 has no HUD event, so it isn't a HUD overlay.
- No enum switches in render code. Video frames load lazily and are released after 30 s unused, as in `PortraitVideo`.
- Sounds come from ElevenLabs (SFX only): `card.lid_pop`, `card.shimmer`, `card.legendary_chime`, `card.walk_out`,
  `card.puff`. Envelope-trimmed, level-matched, mono OGG.
- Builds happen only in the `living-cards` worktree (`C:/Users/alonl/hp-cards`).

## Left to the builder

Exact easing curves, particle counts, shimmer shader versus a scrolling texture, hand-render offsets, and the placeholder
generator.
