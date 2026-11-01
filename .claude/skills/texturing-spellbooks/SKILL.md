---
name: texturing-spellbooks
description: Use when a spellbook, spell tome or magic book item texture is needed in the Hogwarts Magic mod — a new spell gets added, a spellbook icon is missing, oversized, painted or AI-generated, or the books stop looking like one set.
---

# Texturing Spellbooks

> **Status (2026-10-27):** the user rejected the 16x16 redraw of the existing books and the mod
> went back to the original painted spellbooks. Do not regenerate existing books; the build script
> refuses to overwrite them. Ask the user before using this master for a new spell, because it will
> not match the painted set.
>
> **Approved route for a new or off-model book (2026-11-01):** generate it in Flow (flow-mcp,
> Nano Banana image, 0 credits) with 3 approved painted books on white as `reference_images`
> (protego, crucio, serpensortia), 2 variants, prompt naming cover colour, metal, gem and emblem
> on "a plain flat pure white background". Cut it out with
> `python tools/item_textures/flow_book_cutout.py <in.jpeg> <magic_book_x.png>` (1024x1024,
> outline-matted edge, no white fringe). Show the user old vs v1/v2 and let them pick.

## Overview

Every spellbook is **one master sprite** with three per-spell choices: cover colour, a 5x5 cover
emblem and the emblem's glow colour. The master is a 16x16 redraw of the format the mod's original
painted books share. The book lies at 3/4 view with the cover tilted up-left. The spine runs down
the lower-left edge, with two hinge clasps and a gem between them. A cream page block shows along
the lower-right edge, and the emblem sits mid-cover. Because the silhouette never changes, the
spellbooks read as a set and a player tells them apart by colour and glyph.

**REQUIRED BACKGROUND:** texturing-minecraft-items (the sprite contract, linter, preview).

## Adding or changing a spellbook

1. Add one line to `SPELLS` in `tools/item_textures/spellbooks.py`:
   ```python
   "protego": Spell("#2c3c6a", "shield", "#86dcf0"),
   #                cover      emblem    glow
   ```
   Optional fields: `shade` (second emblem colour, `b`), `core` (hot centre, `c`), `gem`,
   `metal` (`silver | gold | iron | bronze`), `pages` (`cream | aged | dark`),
   `halo` (0-1, a glow tint around the emblem; off by default because it blurs small glyphs),
   `emboss` (a darker cell below-right of the emblem; on by default).
2. Pick the emblem from `EMBLEMS` in `.claude/skills/texturing-spellbooks/scripts/spellbook.py`.
   Run `python .claude/skills/texturing-spellbooks/scripts/spellbook.py --sheet out.png` to see
   them all on a book. If none fits, add one to `EMBLEMS`: 5 rows × 5 (or 7) characters, using `a`
   for the emblem, `b` for its shadow and `c` for the core. Draw ONE bold silhouette of about 8-15
   pixels; a 5x5 scene is noise. (`Spell(emblem=[...rows...])` also takes the rows directly, which
   is handy for trying variants before you name one.)
3. Colours:
   - **Cover:** a dark, saturated leather themed to the spell: V 0.25-0.45 and **S ≥ 0.35**. It is
     the ramp midtone, and the master derives outline, spine and rim from it. Greyer covers wash out
     to a pale grey-blue on the slot.
   - **Glow:** bright and saturated, so it pops off the cover (fire is orange on a dark red, frost
     is cyan on navy).
   - If a painted original exists, start from `scripts/sample_colours.py <png>`.
   - Two spells with the same emblem must differ in cover hue (protego vs protego_maxima, or
     revelio vs imperio). Scan the `SPELLS` table for covers within ~20° of hue of yours; if one is
     close, the emblem and glow must differ strongly.
4. Build: `python tools/item_textures/spellbooks.py <spell>`. The model stays
   `minecraft:item/generated` with `layer0: hogwartsmagic:item/magic_books/magic_book_<spell>`.
5. Check: lint with `texlint.py`, then run
   `preview.py sheet.png <new> <the 2 books with the closest cover hue> --ref book` and **look at the 1x
   tile**. (`--ref` needs `vanilla_refs.py` from texturing-minecraft-items to have run once.) Can you name the spell from across the hotbar? Does it sit in the set?

## Rules

| Do | Don't |
|---|---|
| Change only cover / emblem / glow / gem / metal | Edit `MASTER` for one spell; that forks the set |
| Keep emblems upright on the tilted cover | Skew emblems to the cover angle; at 5x5 they turn into jaggies |
| Use emblem cells `a b c` only; the builder clips them to the cover | Paint over the rim, spine or pages |
| Give fire/explosive spells `metal="iron"`, light/holy ones `"gold"` | Use the same gem colour on every book |

A master change (new silhouette, new shading) is a set-wide decision: regenerate **every** book,
contact-sheet them all, and look at the whole set before committing.

## Common mistakes

- **Downscaling a painted book into the slot.** A 1024-2048 px image becomes mush at 16 px, inflates
  the block atlas and, at sizes that aren't powers of two, kills mipmapping for the whole atlas.
- **Halo on a thin glyph.** The glyph dissolves. Use `halo` only on solid emblems (`orb`, `heart`).
- **Low-contrast glow.** A glow close to the cover's value disappears at 1x. Lift it or change its hue.
