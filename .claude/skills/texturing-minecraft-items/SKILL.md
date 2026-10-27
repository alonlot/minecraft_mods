---
name: texturing-minecraft-items
description: Use when creating, redrawing or reviewing a Minecraft item texture (the 16x16 inventory/hotbar sprite) for a mod or resource pack, when an item looks like a placeholder, flat programmer art, a blurry downscaled painting or an AI image, when a texture is not 16x16 or not a power of two, or when the log says "limits mip level".
---

# Texturing Minecraft Items

## Overview

A vanilla-quality item is **authored pixel by pixel on a 16x16 grid** with a few controlled colour
ramps, a dark selective outline and a silhouette that reads at 1x on the grey slot. Every shortcut
— a downscaled painting, procedural noise, a flat fill, a front-on box — reads as "modded junk"
next to vanilla items. Draw it, measure it against vanilla, look at it at real size, then ship it.

## The contract every item sprite meets

| Rule | Vanilla evidence (all 605 1.21 item sprites) |
|---|---|
| 16x16 PNG, RGBA. Animated: vertical strip of 16x16 frames plus `.png.mcmeta` | 605 of 605 are 16x16 |
| Alpha is 0 or 255, never between | 604 of 605 |
| 6-15 colours in total, 4-6 per material ramp | median 10, max 26 |
| Silhouette fills about 10-15 px of the 16 in each direction; 1-2 px margin | median bounding box 14x14 |
| 1 px outline in the **darkest shade of the local hue**, not black: lighter on the lit top-left, darkest bottom-right | 87% have darker edges; 7 of 605 use #000 |
| Light comes from the top-left | top-left half is brighter in 2 of 3 |
| Long items run diagonally, handle bottom-left to tip top-right, on a clean 1:1 line | sticks, tools, wands, rods |
| Reads on both the #8b8b8b slot (needs pixels with lum ≤ 70) and the dark hotbar (needs a highlight) | |

Why it is not just taste: every opaque pixel becomes a voxel when the item is held
(`item/generated` builds side walls at each alpha edge), so stray and semi-transparent pixels show up
as loose specks and ghost walls. A sprite that is not a power of two lowers the mipmap level of the
**whole block atlas** — the log says `Texture … limits mip level from 4 to 0` and distant terrain
shimmers. A 1024x1024 sprite takes 4096 times the atlas space of a 16x16 one.

## Workflow

1. **Reference.** `python scripts/vanilla_refs.py <mc_version>` extracts vanilla sprites to
   `%TEMP%/mc_vanilla_items`. Pick 2-3 vanilla items closest in shape or material (potion for any
   bottle, `stick` or `blaze_rod` for wands, `diamond` for gems, `book` for books, `iron_sword` for
   blades). Measure them; never copy them into the mod.
2. **Silhouette first.** Decide the outline shape before any colour, at 16x16. Choose a 3/4 view or a
   diagonal; a front-on rectangle reads as a tile. Bottles have a narrow neck, a cork and a round
   bulb. If the object is too detailed for 16 px, keep its single most recognisable feature.
3. **Ramps.** One ramp per material. For a material vanilla already has (gold, iron, wood, glass,
   paper, diamond), use **vanilla's measured ramp** from reference.md: it is what makes the sprite sit
   next to vanilla. For a new material, `ramp(base, n)` in `scripts/pixkit.py` follows vanilla's
   recipe; hand-pick from it.
4. **Author the grid.** Write a per-item script with a 16-row character grid, one character per
   shade (no spaces inside rows; use `.` for transparent), and `sprite(GRID, PALETTE)` → `save()`. `save()` refuses semi-transparent pixels. Keep the
   script in the repo so the sprite can be regenerated and edited.
5. **Lint.** `python scripts/texlint.py out.png`. Fix every ERROR. Each WARN is a prompt to look:
   fix it, or know why it is right.
6. **Look.** `python scripts/preview.py sheet.png out.png --ref potion,honey_bottle`, then **open the
   sheet and look at it**. The first three tiles are true GUI size on the slot, the hotbar and the sky.
   Ask: does it read as the thing at 1x? Does it look like it belongs in the same row as the vanilla
   references? Iterate on the grid until yes.
7. **Families.** Variants (tiers, houses, colours) share one master grid: palette swaps with
   `recolor()`, or a per-member emblem grid painted on with `overlay()`. Keep a 1px gap between an
   emblem and a frame of the same colour. Lay the whole family out in one preview.

Auditing a whole mod: `python scripts/texlint.py --audit src/main/resources/assets/<modid>` lints every
texture that an item model uses, worst first. Then preview the clean-but-suspicious ones: the linter
cannot see "looks like a placeholder".

## Common mistakes

| Mistake | Fix |
|---|---|
| Downscaling a painting or AI image to 16x16 | Redraw it as a grid. A downscale gives mush: lots of single-pixel colours and no outline |
| Front-on box (`box-silhouette`) | Tilt it: books lie at 3/4, scrolls curl, tags hang on a diagonal |
| Opaque milky bottle | The glass interior is transparent apart from the liquid; the glass edge is light blue (vanilla `#d4e5f7`→`#5d8fc2`), with a highlight streak on the left. Correct vanilla-style glass trips `no-dark`, `no-outline` and `slot-contrast` (vanilla `potion` does too): expected. Only if the bottle then vanishes on the slot preview, darken its bottom/right glass edge one step |
| Pendant, amulet, locket | The chain is a 1px diagonal arc over the top corners; the body is a compact shape filling the lower 10x10; leave out chain links that don't fit |
| Chain or sparkles as loose pixels | Connect them diagonally, or drop them; loose pixels float as voxels in hand |
| Pure black outline everywhere | Outline with the darkest shade of each material's own ramp |
| Pillow shading (bright centre, dark rim on all sides) | Light from the top-left: highlights hug the top-left edges, shadow the bottom-right |
| Glow made with semi-transparent halo pixels | Glow is a pale, saturated core with no dark outline (the `blaze_rod` recipe), alpha stays binary |
| Thin pale rod (wand, bone) whose lit edge is slot grey | The linter's slot check can miss a 3px-wide rod: look at the slot tile; push the lit edge lighter or darker than #8b8b8b by 25+ lum |
| Colour changing on every strand of a 1:1 diagonal | That is a checkerboard: keep each diagonal strand one colour, or vary in runs of 3+ |
| Ring/collar drawn as a horizontal run across a diagonal rod | A band around a 1:1 rod is a 2-row staircase of ~4 pixels perpendicular to the rod |
| Long item with a head (broom, brush, axe) squeezed into the tip | The corner limits width near the tip: curve the head so it fills the top-right corner, and run the handle from the bottom-left |
| 32x32 "for more detail" | 16x16. HD sprites look like another game next to vanilla |
| Judging the 8x zoom only | Judge the 1x slot tile; the zoom is for finding the pixel to fix |

Reference data — vanilla palettes, materials, outline maths, model JSON, tinting, animation and
emissive facts for Forge 1.21 — is in `reference.md`.
