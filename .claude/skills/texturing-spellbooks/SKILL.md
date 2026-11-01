---
name: texturing-spellbooks
description: Use when a spellbook, spell tome or magic book item texture is needed in the Hogwarts Magic mod — a new spell gets added, a spellbook icon is missing, cropped, has a baked-in background or a placeholder, or a book does not match the rest of the set.
---

# Texturing Spellbooks

## Overview

The mod's 41 spellbooks are **painted HD art on one shared base book** (1024x1024 transparent PNG;
never 2048, which costs 16 MB of item atlas per book for no visible gain). That base is approved by the user, and every book must look like it. The spellbooks are the
one item family that is NOT a 16x16 sprite, so texturing-minecraft-items' 16x16 contract does not
apply to them. A new or off-model book is generated in Flow with approved books as references.
The user then picks a variant.

**REQUIRED SUB-SKILL:** generating-images-with-flow (opening a project, waiting, failures,
cutout, picking sheet).

## The base book (what "on-model" means)

- A thick tome lying at **3/4 isometric view, cover tilted up-left**, with a dark outline.
- **Cracked, weathered leather** cover in one dark saturated colour.
- **Spine down the lower-left edge**, with **two metal hinge clasps and a small round gem
  between them**.
- A **cream page block** along the lower-right edge.
- **One glowing emblem** mid-cover: a single bold silhouette.
- **No background.** Keep particles outside the book to a few at most. Some approved books have
  them, but ask for none.

A book is off-model if it is front-on or flat, has no clasps or gem, has a baked-in background,
is cropped, or is a 16x16 placeholder. Contact-sheet the whole folder to spot them.

## Making a book

1. **References:**
   `python .claude/skills/texturing-spellbooks/scripts/book_refs.py <scratchpad>/refs`
   writes protego, crucio and serpensortia flattened onto white.
2. **Design the variant**, keeping the old book's idea where one exists:
   - **Cover:** a dark, saturated leather in the spell's colour. Its hue must differ from any book
     with a similar emblem.
   - **Metal:** silver or gold for light spells, dark iron for dark or explosive ones, brass or
     bronze for neutral ones.
   - **Gem:** echoes the glow colour.
   - **Emblem:** one solid, glowing shape in a bright colour that contrasts with the cover.
3. **`flow_generate`**, one scene per spell, `type: "image"`, `aspect_ratio: "1:1"`,
   `variants: 2`, all three refs in `reference_images`. Prompt (fill in the four slots):
   > Spellbook game item icon matching the reference images exactly: the same painterly pixel-art
   > fantasy RPG style, the same thick ancient tome lying at the same 3/4 isometric angle with the
   > cover tilted up-left, the same cracked weathered leather cover, spine along the lower-left
   > edge with two **`<metal>`** metal hinge clasps and a small round gem between them, cream page
   > block along the lower-right edge, dark outline, same proportions and framing. Cover:
   > **`<cover>`** leather. Emblem in the middle of the cover: **`<emblem>`**, a **`<what the spell
   > does>`**. Gem: **`<gem>`**. Centered with margin, isolated on a plain flat pure white
   > background, no shadow, no particles or sparkles outside the book, no text.
4. **Pick:** use compare_sheet.py with the old book first (`-` for a new spell). Recommend the
   variant whose emblem is **solid** (see below), and let the user choose.
5. **Install:**
   `python .claude/skills/generating-images-with-flow/scripts/cutout.py <scene-NN-vK.jpeg> src/main/resources/assets/hogwartsmagic/textures/item/magic_books/magic_book_<spell>.png`
   gives 1024x1024 at the set's framing (`--fill 0.92`). A redraw needs nothing else.
   **A new spell** also needs wiring, or the texture never shows:
   - its id appended to `SPELL_MODEL_ORDER` in `item/MagicBookItem.java`, which makes its
     custom_model_data the list index + 1;
   - a matching `custom_model_data` override in `models/item/magic_book.json`;
   - `models/item/magic_book_<spell>.json` (`minecraft:item/generated`, `layer0:
     hogwartsmagic:item/magic_books/magic_book_<spell>`).
6. **Check the set:**
   `python .claude/skills/generating-images-with-flow/scripts/preview.py <out.png> --dir src/main/resources/assets/hogwartsmagic/textures/item/magic_books`.
   The new book must sit in the set, and you must be able to name it at 32 px. Then commit and push.

## Choosing between variants

| Pick | Over | Why |
|---|---|---|
| A solid, filled emblem (a filled feather, a smoky bird) | A thin glowing outline of the same shape | A 1-2 px line disappears at hotbar size |
| A recognisable object (a jack-in-the-box, a burst) | A busy scene or a vague glow blob | One silhouette reads; a scene is noise |
| A shape distinct from same-coloured books | Repeating a neighbour's emblem (a flame next to incendio) | The player tells books apart by emblem plus colour |

## Common mistakes

- **Skipping the old-vs-new sheet.** The user has rejected redraws before (the 16x16 set, the
  crests, the cloak). Never install a book they haven't picked.
- **Trusting the filename.** Flow has saved a piertotum book under the levicorpus name. Look at
  every image.
- **Shipping the old 16x16 master.** `scripts/spellbook.py` and `tools/item_textures/spellbooks.py`
  are a 16x16 redraw the user REJECTED on 2026-10-27. They are kept only because the tools script
  imports them. Don't use them for books.
