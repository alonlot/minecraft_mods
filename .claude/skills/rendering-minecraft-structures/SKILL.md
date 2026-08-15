---
name: rendering-minecraft-structures
description: Use when a Minecraft structure template (.nbt) has been generated or edited and nobody has seen what it looks like — before committing a structure, after changing a build script, or when a build passes its block-level audits but its shape, scale and composition are still unverified.
---

# Rendering Minecraft Structures

## Overview

Structure templates are written blind. Block-level audits check that a lantern has something to hang
from, that a chest does not face a wall, that a fern is rooted in soil. **None of that can tell you
the build looks wrong.** Render it and look before calling it done.

## When to Use

- A build script wrote or rewrote a `.nbt` and nobody has looked at the result
- A structure passed its audits and is about to be committed
- Judging proportion, silhouette, density, or whether a shape reads as the thing it is meant to be
- Reviewing a template saved in game with a structure block, or vanilla's own

Not for block legality, blockstate validity or entity placement — those need a real audit, not a
picture.

## Quick Reference

```bash
S=.claude/skills/rendering-minecraft-structures/render_structure.py
python $S build.nbt                                 # iso + layers, into a temp dir
python $S build.nbt --hide cobweb,leaves            # strip the roof off
python $S build.nbt --crop 2,6,11,14 --cell 26      # close-up on one detail
python $S build.nbt --section x=6                   # text cross-section; draws nothing
```

| View | Answers |
|---|---|
| `iso` | Proportion, silhouette, composition. **The one that finds real faults.** |
| `layers` | Whether a pattern holds — a dome, a ring, a spiral, a gradient |
| `elevation` | What you see walking up to one side |
| `--section` | Exactly which block sits at exactly which height |

`--out` defaults to a temp folder. `--hide`, `--crop` and `--max-y` all shrink the drawn extent, so
cropping really does zoom.

## Reading the Render

Look for these four. Every one survived a fully passing audit in practice:

1. **Something out of proportion** — one element eating the frame. A wall meant as a backdrop that
   reads as a rampart.
2. **A focal point with no presence** — the thing everything is arranged around, flat and invisible.
   A fire pit needs a raised kerb; paving plus a campfire reads as a courtyard.
3. **Scatter where structure was intended** — a web needs radial threads *and* concentric ones or it
   reads as confetti. Noise-gating a line turns it into dots.
4. **Mechanical regularity** — objects at equal angles and equal heights read as fence posts. Jitter
   angle *and* radius, vary height widely, and flare the base where a column meets the ground.

## Common Mistakes

| Mistake | Fix |
|---|---|
| Rendering with the canopy or roof on | `--hide` it. Anything overhead hides everything beneath it. |
| Judging a detail from the whole-build view | `--crop` to it and raise `--cell`. Small cells cannot settle fine geometry. |
| Guessing geometry from the picture | `--section` prints the actual blocks. Use it to settle "is this A-frame right". |
| Treating blockiness as a fault | These are flat-shaded untextured cubes. In game, texture and lighting do work these images cannot show. |
| Believing the render replaces the game | It catches composition. Standing in the build still catches more. |

Unlisted blocks get a stable hashed colour and are named in a `hashed colours` line. If one matters
to your judgement, add it to `PALETTE` first.

## Real-World Impact

The Death Eater camp in this mod passed every audit — legal blocks, supported lanterns, a surviving
painting, correct entity placement — and was wrong in all four ways above. None of it was visible
until it was rendered isometrically.
