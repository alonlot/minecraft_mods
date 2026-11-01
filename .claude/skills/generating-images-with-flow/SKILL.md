---
name: generating-images-with-flow
description: Use when the Hogwarts Magic mod needs a new painted image (an item icon, a spellbook, a GUI picture, concept art, a reference for a model) and the flow-mcp tools (mcp__flow__*) are available, or when a Flow call fails with "no project is open", a locator timeout, "no x2 option" or "could not be found again in the grid".
---

# Generating Images with Flow

## Overview

flow-mcp drives Google Flow in its own Chrome window. **Images (Nano Banana) cost 0 credits;
video costs credits.** The work is to make an image that matches art the user already approved,
not a fresh look. So always attach approved art as `reference_images`, generate 2 variants, and
let the user pick from an old-vs-new sheet. The user decides; you never pick for them silently.

Scripts live in `.claude/skills/generating-images-with-flow/scripts/`.

**Which route for an item texture?** Use Flow if the item belongs to a family that is approved
painted HD art (the spellbooks), or if the user asks for Flow. Otherwise the item is a 16x16
hand-drawn sprite: use texturing-minecraft-items. A downscaled painting there is the "modded
junk" that skill warns about.

## Workflow

1. **`mcp__flow__flow_status`** (`detailed: false`). If it says *no project is open*, run
   `node .claude/skills/generating-images-with-flow/scripts/open_project.mjs` and call
   flow_status again. With no argument the script creates a new project. Pass a
   `https://flow.google.com/project/…` URL to reopen one instead. If the script says the user is
   signed out, stop and ask them to sign in. Don't ask the user to do it, and don't use the Claude in Chrome extension
   or computer-use: neither can reach Flow's Chrome. It runs a separate profile with a debug port,
   and the script drives it over CDP.
2. **References.** Pick 2-3 approved textures in the same family as the image you need. Flatten
   them onto white at 1024 px in the scratchpad, so the references match the white background you
   ask for and alpha handling never comes into it. Pass their absolute paths in `reference_images` (max 3).
3. **One `flow_generate` call for the whole batch**, one scene per image:
   `type: "image"`, `aspect_ratio: "1:1"`, `variants: 2`, the same `reference_images` on each
   scene. Use a **new `project` folder name per batch** (e.g. `Textures-2026-11-01`): files are
   named `scene-NN-vK.jpeg` and a reused folder overwrites older scenes. Prompt shape:
   > `<what it is>` matching the reference images exactly: the same `<style, angle, framing,
   > parts>` … `<per-item specifics: colours, emblem, materials>`. Centered with margin, isolated
   > on a plain flat pure white background, no shadow, no particles or sparkles outside the
   > object, no text.
4. **Wait.** Scenes run one at a time with 25-70 s pauses, about 2 min per scene (both variants
   together). Poll with
   `flow_wait` on ONE job id at a time, because the whole-queue form returns every prompt. Give
   the user a progress line with a time estimate.
5. **Fix failures** (see table), then **look at every file**. Flow sometimes saves another
   scene's image under this scene's name.
6. **Picking sheet:** `python …/scripts/compare_sheet.py <out.png> --row <name> <old.png|-> <v1> <v2> …`.
   Put the output path first, and use `-` as the old image for a brand-new item. Show the sheet
   with `SendUserFile` if that tool exists. Otherwise `Read` it and give its absolute path. State
   your recommendation and the reason (what reads at 32 px). Wait for the user's pick.
7. **Cut out and install:** `python …/scripts/cutout.py <in.jpeg> <out.png> [--size 1024] [--fill 0.92]`.
   Then run `python …/scripts/preview.py <out.png> <installed.png> …` (or `--dir <folder>` for
   the whole family). Check for a fringe on dark and that you can name the item at 32 px. Commit
   and push each batch the user approved.

## Failures

| Symptom | Cause | Fix |
|---|---|---|
| `Signed in, but no project is open` | Flow is on its home page | `open_project.mjs` |
| `locator.click: Timeout 30000ms exceeded` / `no "x2" option` | Flow UI flake, not your prompt | `flow_retry` with that job id (it requeues at the end). If it fails again, re-check `flow_status` and retry once more, then report |
| `could not be found again in the grid` | Generated, but download lost | `flow_assets` lists it (sometimes titled with the prompt's first words), then `flow_download` it. Downloads are named `clip-NN.jpeg`, not `scene-NN-vK` |
| Image shows another item's emblem or subject | Either Flow copied a reference image, or it mislabelled another scene's asset | Copied: swap that reference and add "do not copy any emblem from the reference images". Mislabelled: don't use it. Either way, regenerate (0 credits, no need to ask) and tell the user |
| Cut-out has a white or grey fringe on dark | Subject has no dark outline | Ask for "a thick dark outline" in the prompt, or composite on dark to check before shipping |

## Rules

- Never spend credits (video, `flow_edit`, `flow_continue`) without the user's yes and a quote.
- Output PNGs are powers of two (1024, 2048). A non-power-of-two size turns off mipmapping for
  the whole item atlas.
- Generated images go to `C:\Users\alonl\flow-mcp-out\<project>\`. Install only the cut-out PNG.
  Don't install the jpeg, and don't commit flow-mcp-out.
- Items the user has rejected a redraw of stay as they are (see memory). Ask before regenerating
  them.
- Use the family's skill on top of this one when it exists: texturing-spellbooks for spellbooks.
