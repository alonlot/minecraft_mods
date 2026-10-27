# Item texturing reference

Measured from the vanilla 1.21 `client-extra.jar` unless a URL is given.

## Vanilla ramps (light → dark)

| Material | Ramp | What it teaches |
|---|---|---|
| Stick / tool handle | `#896727 #684e1e #493615 #281e0b` | straight 4-step ramp at constant hue: centre highlight, top-left edge mid-dark, bottom-right edge darkest |
| Iron | `#ffffff #d8d8d8 #a8a8a8 #828282 #727272 #5e5e5e #353535` | pure grey; tools jump from #c1c1c1 straight to #444444 — the big value gap is what reads as metal |
| Gold | `#ffffff #fffde0 #fdf55f #fad64a #e9b115 #dc9613 #b26411 #752802` | shadows shift toward red (−37°), not blue; saturation rises toward the shadow |
| Diamond | `#ffffff #d5fff6 #a1fbe8 #4aedd9 #20c5b5 #1aaaa7 #11727a #145e53` | facets are flat value regions; the outline is lighter top-left (#11727a), darker bottom-right (#145e53) |
| Emerald | `#dbffeb … #002d00` | hue 146 → 120 |
| Amethyst | base `#54398a`, highlights `#fecbe6 #fffdd5` | the clearest highlight shift: toward pink and yellow |
| Redstone / apple / leather | one hue each | many vanilla ramps barely hue-shift at all |
| Glass bottle | `#d4e5f7 #b3cfec #8badd0 #5d8fc2`, cork `#d46d49 #a94725 #973716` | glass is outlined in LIGHT blue; interior transparent; the liquid is a separate tinted layer |
| Paper | `#fcfcf2 … #878787` | almost neutral, with a grey edge shadow |
| Book pages | `#d6d6d6 #b7b7b7 #999999` | a 3-grey stripe along the page edge |
| Blaze rod (glow) | all shades V ≥ 58, darkest `#953300`, core `#fff87e #fff32d` | glowing = no dark outline, a saturated darkest shade and a pale core down the middle |

Ramp recipe: 4-7 shades; adjacent steps differ by 10-20 V (of 100); the top shade is V 95-100 at
S ≤ 20 (or #ffffff for a specular); saturation rises toward the dark end; total hue drift is 0-20°
(cool hues drift blue in shadow, gold/orange/wood drift red). Never put the darkest shade directly
against the lightest one — a transition shade goes between them.
(https://www.slynyrd.com/blog/2018/1/10/pixelblog-1-color-palettes is the generic version; vanilla
uses much less hue shift than it suggests.)

## Lines, clusters, shading

- Clean slopes only: 1:0, 2:1, 1:1, 1:2, 0:1. Every run along one straight line has the same length.
  Curves change run length monotonically (5,2,2,1,1 — never 5,2,1,2,1).
  (https://opengameart.org/content/chapter-2-lines-and-curves, https://lospec.com/articles/pixel-art-outlines)
- A 1 px line connects diagonally; two line pixels side by side form a "double".
- No orphans, except a deliberate specular or sparkle pixel.
- Avoid these: pillow shading (concentric light), banding (ends of runs lining up), pancake shading,
  noise, large dithered areas.
  (https://blockbench.net/wiki/guides/minecraft-style-guide, https://pixeljoint.com/forum/forum_posts.asp?TID=11299)
- Selective outline: each outline pixel is one ramp step darker than the fill it touches, and keeps a
  closed contour. "An outline should always increase contrast, never decrease it."
  (https://lospec.com/articles/pixel-art-outlines-part-2-using-color)
- Less detail is more at 16 px (https://docs.faithfulpack.net/pages/textures/cf32-texturing-guidelines).

## Materials at 16 px

- **Metal:** high contrast, a white specular, a big gap between mid and dark, no dither. Blades are
  grey, not white.
- **Gems:** flat facet planes in one hue, a white highlight cluster top-left, the deepest shade next
  to the bottom-right outline, no black lines between facets.
- **Wood:** grain runs along the length as parallel lines; no scribbles.
- **Cloth / leather:** soft linear shading, few shades; fold lines follow gravity.
- **Glass / potion:** light outline, transparent interior, the liquid with a light top surface,
  1-2 near-white specular pixels on the bulb, the cork in warm terracotta.
- **Plants:** 5-6 desaturated greens, no dither.
- **Magic glow:** pale core, saturated shades, no outline darker than V ≈ 40, no halo outside the
  silhouette.

## Readability

- The slot is `#8b8b8b`, with a `#373737` top/left bevel and a `#ffffff` bottom/right bevel; the panel
  is `#c6c6c6`. The hotbar is translucent near-black over the world.
- One texel = one GUI pixel × GUI scale. The preview's first tile (32 px) is GUI scale 2.
- Enchant glint is additive: it washes out light sprites. Keep enchantable items mid-tone.
- Check the silhouette in greyscale: hues with similar values merge at small sizes.

## Forge 1.21 (51.x) technical facts

- Model `assets/<ns>/models/item/<id>.json`. `minecraft:item/generated` for flat items,
  `minecraft:item/handheld` for tools and wands (held at an angle). Layers `layer0`-`layer4`; layerN
  has tintindex N. (https://docs.neoforged.net/docs/1.21.1/resources/client/models/)
  The 1.21.4 `items/` folder does NOT exist in 1.21.
- Tint: `RegisterColorHandlersEvent.Item`, `event.register((stack, tint) -> …, items)`. Return an
  **opaque** ARGB (`FastColor.ARGB32.opaque(rgb)`): a bare `0xRRGGBB` has alpha 0 and makes the
  layer invisible. Return −1 for no tint. Tinted layers should be greyscale.
- Extrusion: front and back quads plus side walls wherever an alpha>0 pixel meets alpha 0. The shader
  discards alpha < 0.1, so alpha 1-25 pixels are invisible but still get walls.
- Items render on the translucent item sheet, so semi-alpha does blend — and looks like a smear next
  to crisp vanilla edges. Keep alpha binary unless the item really is glass.
- Animation: `<name>.png.mcmeta` with `{"animation": {"frametime": 2, "interpolate": false}}` and
  frames stacked vertically. Frame size must divide the image, or you get the missing texture.
- Emissive in Forge 51.0.33: per-layer `forge_data` on `forge:item_layers` is documented but **not
  read** by the loader. `"render_types": {"forge:item_unlit": [1]}` removes directional shading only.
  True fullbright needs an element model with face-level
  `"forge_data": {"block_light": 15, "sky_light": 15}`.
- The atlas mip level drops to the least power-of-two-friendly sprite: one 267x300 sprite turns
  mipmapping off for every block.
- Pillow: pass `Image.NEAREST` on every resize. RGBA `quantize()` only supports FASTOCTREE.
