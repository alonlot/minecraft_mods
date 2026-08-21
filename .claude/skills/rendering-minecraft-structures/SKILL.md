---
name: rendering-minecraft-structures
description: Use when a Minecraft structure template (.nbt) has been generated or edited and nobody has seen what it looks like — before committing a structure, after changing a build script, or when a build passes its block-level audits but its shape, scale, density and composition are still unverified.
---

# Rendering Minecraft Structures

## Overview

Structure templates are written blind. Block-level audits check that a lantern has something to hang
from, that a chest does not face a wall, that a fern is rooted in soil. **None of that can tell you
the build looks wrong.** Render it and look before calling it done.

## When to Use

- A build script wrote or rewrote a `.nbt` and nobody has looked at the result
- A structure passed its audits and is about to be committed
- Judging proportion, silhouette, **density**, or whether a shape reads as the thing it is meant to be
- Reviewing a template saved in game with a structure block, or vanilla's own

Not for block legality, blockstate validity or entity placement — those need a real audit.

## Two renderers, and they answer different questions

| | `render_structure.py` | `textured/` (deepslate) |
|---|---|---|
| Draws | flat-shaded cubes | **real block models and real textures** |
| Needs | Pillow | a browser tab + local server |
| Good for | layout, extent, "is anything in the wrong place" | **density, tone, visual weight, does it read** |
| Blind to | how loud a texture is; slab/stair/fence shapes | nothing much |

A block-type census is the third tool and often the fastest: dump the palette counts out of the
`.nbt` and read them. "Fourteen light sources in the whole build" and "emerald is a quarter of the
hoard" are one-line findings there and a long squint in either renderer.

**Use the textured one before committing.** The flat one is a fast check while iterating; it cannot
answer the question that matters most, for the reason in Common Mistakes below.

## Quick Reference

```bash
S=.claude/skills/rendering-minecraft-structures
python $S/render_structure.py build.nbt --hide cobweb        # fast, flat
python $S/render_structure.py build.nbt --section x=6        # text cross-section
```

Textured — export once for **all** structures (one shared atlas), then drive the page:

```bash
python $S/textured/export_assets.py data/.../structure/*.nbt \
  --jar ~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client-extra.jar \
  --mod src/main/resources/assets/<modid>
```

Then `preview_start` the `structure-viewer` config and open
`http://localhost:8731/?nbt=<name>.nbt`. URL params: `yaw`, `pitch`, `dist`, `w`, `h`, `hide=water`,
`maxy=N`, `cut=x|z`. The page exposes `__renderer`, `__gl`, `__names` and `__missing`, so one script
can re-render every structure from a single page load — navigating per structure destroys the
calling script's own context. `POST /save?name.png` writes a render to `build/shots/`.

## The cutaway is not optional

Sealed builds are common — an underground hall is a solid shell hollowed out, a sunken ruin is
flooded solid with water. **Four of the eight structures in this repo are opaque boxes from
outside.** Use `cut=z` to drop the near half, and `hide=water` on anything flooded, or the renderer
tells you nothing about exactly the builds whose interiors are the point.

## What to look for

Every one of these survived a fully passing audit:

1. **Density of loud blocks** — see below. The most expensive mistake available.
2. **Something out of proportion** — one element eating the frame. A wall meant as a backdrop that
   reads as a rampart.
3. **A focal point with no presence** — a fire pit needs a raised kerb; paving plus a campfire reads
   as a courtyard.
4. **Scatter where structure was intended** — a web needs radial threads *and* concentric ones, or
   it reads as confetti. Noise-gating a line turns it into dots.
5. **Palette contrast at one-block frequency** — mixing blocks 60 luma apart is not texture, it is a
   chessboard. Keep a ground mix inside roughly a 40 luma spread and one hue family.
6. **Mechanical regularity** — objects at equal angles and equal heights read as fence posts. Jitter
   angle *and* radius, vary height widely, flare the base where a column meets the ground.

## Common Mistakes

| Mistake | Fix |
|---|---|
| **Judging density from the flat renderer** | It draws cobweb, torches and plants as small dots. 367 cobwebs looked like a canopy there and were a **blizzard** in game. Anything with a bright busy texture on transparency carries far more weight than its block count. Check density textured, or in game. |
| Rendering a sealed build without a cutaway | `cut=z`, plus `hide=water` when flooded. |
| Rendering with the canopy or roof on | `--hide` / `hide=`. Anything overhead hides everything beneath it. |
| Judging a detail from the whole-build view | `--crop` and raise `--cell`, or use `dist`. |
| Guessing geometry from the picture | `--section` prints the actual blocks. |
| **Reading `yaw`/`pitch` as degrees** | They are **radians** (defaults 0.7 and 0.55). `yaw=35` is a legal but arbitrary angle, not 35 degrees, and the shot comes out from nowhere useful. |
| POSTing a Blob to `/save` | The endpoint expects the **data-URL string** from `canvas.toDataURL('image/png')`. A Blob body fails with `TypeError: Failed to fetch`. |
| Driving the page before it is built | A 150k-cell structure takes the best part of a minute to mesh, and JS calls into the page time out while it does. Poll `window.__ready` (`'ok'` / `'error'`) rather than assuming the load is finished. |
| Reloading the page to move the camera | `?yaw=&pitch=&dist=` re-fetches and re-meshes the whole structure. Define a helper once against `window.__renderer` / `window.__gl` / `window.glMatrix` and re-`drawStructure` with your own matrix — you can then pan to any point (`mat4.translate(m, m, [-cx, -cy, -cz])`), which the URL parameters cannot do at all. |
| Judging a procedural build from one whole-build shot | Fly the camera to the parts. A 700-block descent renders to a thread at full extent; the failures live at arm's length. |
| Assuming a magenta cube means a broken block | Usually it means a texture the model walk never finds: **block-entity renderers** (chest, sign, bed, banner, bell, decorated pot) keep textures under `entity/`, **fluids** have no model at all, and **explicit `minecraft:air`** has no blockstate. All three are handled; the page reports anything else in `__missing`. |
| Trusting a "no missing textures" check that never fires | deepslate returns the atlas's *invalid-texture* UV on a miss, which is truthy. Test whether a blob was supplied, not whether a UV came back. |
| Believing any render replaces the game | It catches composition and density. Standing in the build still catches more. |

## Real-World Impact

The Death Eater camp passed every audit, then was wrong four ways that only an isometric render
showed, then was **still** wrong in a way only the game showed: 367 cobwebs, 81% coverage of the
dome surface, 29% of every block in the build. Textured rendering would have caught it; flat did
not. The canopy is 72 webs now.
