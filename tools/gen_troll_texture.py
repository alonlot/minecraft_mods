"""
Generate troll.png — a 256x256 atlas for TrollModel.

The model was originally laid out against a 128x128 sheet, but several cubes
(body slab 26x15x15, legs 7x13x10 / 10x5x15, club bits) have unwraps that
overflow the 128 boundary and wrap into random pixels. We bumped the model's
LayerDefinition to 256x256; this script paints each cube's unwrap in that
larger atlas with proper mountain-troll colors.

Cube UV layout (Minecraft convention) for a w×h×d cube at texOffs (tx, ty):
  Top    at (tx + d,         ty)        size w × d
  Bottom at (tx + d + w,     ty)        size w × d
  Right  at (tx,             ty + d)    size d × h
  Front  at (tx + d,         ty + d)    size w × h
  Left   at (tx + d + w,     ty + d)    size d × h
  Back   at (tx + 2d + w,    ty + d)    size w × h
"""

from PIL import Image
import os
import random

# ---------------------------------------------------------------------------
# Palette — Philosopher's Stone mountain troll: dingy grey-brown leather skin,
# moss on top of the shoulders/head, brown wooden club.
# ---------------------------------------------------------------------------
SKIN        = (120, 108,  92, 255)
SKIN_SHADE  = ( 90,  80,  68, 255)
SKIN_DEEP   = ( 64,  56,  46, 255)
SKIN_HIGH   = (148, 134, 116, 255)
SKIN_FLESH  = (148, 110,  98, 255)   # face / mouth interior tint
SKIN_BELLY  = (138, 124, 104, 255)   # softer underbelly

MOSS        = ( 82, 104,  56, 255)
MOSS_DARK   = ( 54,  74,  40, 255)
MOSS_LIGHT  = (108, 128,  76, 255)

TOOTH       = (228, 218, 188, 255)
TOOTH_SHADE = (188, 178, 150, 255)

EYE_YELLOW  = (228, 188,  60, 255)
EYE_DARK    = ( 60,  46,  18, 255)
PUPIL       = ( 18,  12,   8, 255)

NAIL        = ( 78,  60,  44, 255)
NAIL_DARK   = ( 46,  34,  22, 255)

CLUB_WOOD   = (108,  78,  50, 255)
CLUB_DARK   = ( 70,  50,  30, 255)
CLUB_LIGHT  = (140, 104,  68, 255)
CLUB_BARK   = ( 86,  60,  38, 255)

LOIN        = ( 92,  72,  52, 255)
LOIN_DARK   = ( 62,  46,  32, 255)

BG          = (  0,   0,   0,   0)


# ---------------------------------------------------------------------------
# Canvas
# ---------------------------------------------------------------------------
SIZE = 256
img = Image.new("RGBA", (SIZE, SIZE), BG)
RNG = random.Random(0x70_11_C0_DE)


def _clamp(v, lo, hi):
    return max(lo, min(hi, v))


def _darker(c, amt=22):
    return (_clamp(c[0] - amt, 0, 255),
            _clamp(c[1] - amt, 0, 255),
            _clamp(c[2] - amt, 0, 255),
            c[3])


def _lighter(c, amt=22):
    return (_clamp(c[0] + amt, 0, 255),
            _clamp(c[1] + amt, 0, 255),
            _clamp(c[2] + amt, 0, 255),
            c[3])


def fill_rect(x, y, w, h, color):
    if w <= 0 or h <= 0:
        return
    for py in range(y, y + h):
        if 0 <= py < SIZE:
            for px in range(x, x + w):
                if 0 <= px < SIZE:
                    img.putpixel((px, py), color)


def speckle(x, y, w, h, base, shade=None, highlight=None,
            shade_density=0.18, highlight_density=0.06, seed=0):
    """Fill a rectangle with mottled noise so the skin reads as leathery."""
    if w <= 0 or h <= 0:
        return
    if shade is None:
        shade = _darker(base)
    rnd = random.Random((x * 7919) ^ (y * 31) ^ (w * 17) ^ (h * 13) ^ seed)
    for py in range(y, y + h):
        if not (0 <= py < SIZE):
            continue
        for px in range(x, x + w):
            if not (0 <= px < SIZE):
                continue
            r = rnd.random()
            if r < shade_density:
                img.putpixel((px, py), shade)
            elif highlight is not None and r < shade_density + highlight_density:
                img.putpixel((px, py), highlight)
            else:
                img.putpixel((px, py), base)


def edge_shade(x, y, w, h, shade):
    """Darken just the outer pixels of a face — gives the cube some volume."""
    if w <= 0 or h <= 0:
        return
    for px in range(x, x + w):
        if 0 <= px < SIZE and 0 <= y < SIZE:
            img.putpixel((px, y), shade)
        if 0 <= px < SIZE and 0 <= y + h - 1 < SIZE:
            img.putpixel((px, y + h - 1), shade)
    for py in range(y, y + h):
        if 0 <= x < SIZE and 0 <= py < SIZE:
            img.putpixel((x, py), shade)
        if 0 <= x + w - 1 < SIZE and 0 <= py < SIZE:
            img.putpixel((x + w - 1, py), shade)


def paint_cube(tx, ty, w, h, d, top, side, bottom=None,
               top_highlight=None, side_highlight=None,
               front_painter=None, top_painter=None, seed=0):
    """Paint a standard MC cube unwrap with mottled fill, optional custom
    painters for the front face and the top face."""
    if bottom is None:
        bottom = _darker(side)
    if top_highlight is None:
        top_highlight = _lighter(top, 14)
    if side_highlight is None:
        side_highlight = _lighter(side, 16)
    side_shade = _darker(side, 24)

    # Top
    speckle(tx + d, ty, w, d, top, _darker(top), top_highlight,
            shade_density=0.16, highlight_density=0.08, seed=seed + 1)
    # Bottom
    speckle(tx + d + w, ty, w, d, bottom, _darker(bottom), None,
            shade_density=0.14, highlight_density=0.0, seed=seed + 2)
    # Right
    speckle(tx, ty + d, d, h, side, side_shade, side_highlight,
            shade_density=0.18, highlight_density=0.07, seed=seed + 3)
    # Front
    speckle(tx + d, ty + d, w, h, side, side_shade, side_highlight,
            shade_density=0.18, highlight_density=0.07, seed=seed + 4)
    # Left
    speckle(tx + d + w, ty + d, d, h, side, side_shade, side_highlight,
            shade_density=0.18, highlight_density=0.07, seed=seed + 5)
    # Back
    speckle(tx + 2 * d + w, ty + d, w, h, side, side_shade, side_highlight,
            shade_density=0.18, highlight_density=0.07, seed=seed + 6)

    # Light edge shading on every face — gives the cubes a bit of depth.
    edge_shade(tx + d,         ty,        w, d, _darker(top, 36))
    edge_shade(tx + d + w,     ty,        w, d, _darker(bottom, 28))
    edge_shade(tx,             ty + d,    d, h, side_shade)
    edge_shade(tx + d,         ty + d,    w, h, side_shade)
    edge_shade(tx + d + w,     ty + d,    d, h, side_shade)
    edge_shade(tx + 2 * d + w, ty + d,    w, h, side_shade)

    if top_painter is not None:
        top_painter(tx + d, ty, w, d)
    if front_painter is not None:
        front_painter(tx + d, ty + d, w, h)


def scatter_moss(x, y, w, h, density=0.18, seed=0):
    """Drop moss patches over an already-painted region (top of head, shoulders)."""
    rnd = random.Random((x ^ y ^ seed) * 2654435761 & 0xFFFFFFFF)
    for py in range(y, y + h):
        for px in range(x, x + w):
            if not (0 <= px < SIZE and 0 <= py < SIZE):
                continue
            r = rnd.random()
            if r < density * 0.55:
                img.putpixel((px, py), MOSS)
            elif r < density * 0.85:
                img.putpixel((px, py), MOSS_DARK)
            elif r < density:
                img.putpixel((px, py), MOSS_LIGHT)


# ---------------------------------------------------------------------------
# HEAD — root.head cubes
# ---------------------------------------------------------------------------
def paint_head_face(fx, fy, w, h):
    """fx,fy = top-left of the head's FRONT face (w×h). Width 12, height 11.
    Paint a brutish troll face: brow ridge, sunken yellow eyes, scrunched nose
    line. Mouth lives on the jaw/snout cubes below; here we just do the eyes
    and brow."""
    # Brow ridge (darker band)
    for px in range(fx, fx + w):
        img.putpixel((px, fy + 2), SKIN_DEEP)
        img.putpixel((px, fy + 3), SKIN_SHADE)
    # Sunken eye sockets
    eye_y = fy + 4
    for ex in (fx + 2, fx + 8):
        fill_rect(ex, eye_y, 3, 2, SKIN_DEEP)
        # Iris yellow
        img.putpixel((ex + 1, eye_y + 1), EYE_YELLOW)
        # Pupil
        img.putpixel((ex + 1, eye_y + 1), PUPIL)
        # Tiny eye gleam to the right of the pupil
        if ex + 2 < fx + w:
            img.putpixel((ex + 2, eye_y), EYE_DARK)
    # Wrinkle running down the nose bridge
    for py in range(fy + 5, fy + h):
        img.putpixel((fx + w // 2, py), SKIN_SHADE)
        img.putpixel((fx + w // 2 - 1, py), _darker(SKIN, 12))
    # Cheek shadow patches
    fill_rect(fx + 1, fy + 7, 2, 2, SKIN_SHADE)
    fill_rect(fx + w - 3, fy + 7, 2, 2, SKIN_SHADE)


def paint_head_top(tx, ty, w, d):
    # Mossy scalp — heavy moss on top of the head.
    scatter_moss(tx, ty, w, d, density=0.55, seed=1)
    # A couple of small lighter highlights so it doesn't read as flat green
    for _ in range(5):
        px = tx + RNG.randint(0, w - 1)
        py = ty + RNG.randint(0, d - 1)
        img.putpixel((px, py), MOSS_LIGHT)


# head main: (0, 0) 12x11x9
paint_cube(0, 0, 12, 11, 9, SKIN, SKIN, bottom=SKIN_SHADE,
           front_painter=paint_head_face, top_painter=paint_head_top, seed=100)

# head jaw extension: (36, 0) 14x5x5 — bottom jaw, paint with mouth/teeth
def paint_jaw_front(fx, fy, w, h):
    # Mouth gash across the front of the jaw
    mouth_y = fy + 1
    fill_rect(fx + 1, mouth_y, w - 2, 2, _darker(SKIN_FLESH, 40))
    # Teeth — 3 squarish tusks
    for i, tx_off in enumerate((2, 6, 10)):
        if tx_off + 1 < w:
            img.putpixel((fx + tx_off, mouth_y), TOOTH)
            img.putpixel((fx + tx_off, mouth_y + 1), TOOTH_SHADE)
    # Lower lip line
    for px in range(fx + 1, fx + w - 1):
        img.putpixel((px, fy + h - 1), SKIN_SHADE)

paint_cube(36, 0, 14, 5, 5, SKIN, SKIN, bottom=SKIN_SHADE,
           front_painter=paint_jaw_front, seed=110)

# chin/snout: (0, 18) 10x6x5 — paint with a single big tusk
def paint_snout_front(fx, fy, w, h):
    # Snout shading: darker rim
    for px in range(fx, fx + w):
        img.putpixel((px, fy + h - 1), SKIN_SHADE)
    # Big lower tusk on the right side
    tusk_x = fx + w - 3
    fill_rect(tusk_x, fy + 1, 2, 3, TOOTH)
    img.putpixel((tusk_x, fy + 3), TOOTH_SHADE)
    img.putpixel((tusk_x + 1, fy + 3), TOOTH_SHADE)
    img.putpixel((tusk_x, fy + 4), TOOTH_SHADE)

paint_cube(0, 18, 10, 6, 5, SKIN_BELLY, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_snout_front, seed=120)

# nose: (66, 0) 4x5x4
paint_cube(66, 0, 4, 5, 4, SKIN_FLESH, SKIN, bottom=SKIN_SHADE, seed=130)
# Darken the two front nostril dots
fill_rect(66 + 4 + 1, 0 + 4 + 2, 1, 1, SKIN_DEEP)
fill_rect(66 + 4 + 2, 0 + 4 + 2, 1, 1, SKIN_DEEP)

# left brow: (36, 8) 3x3x2
paint_cube(36, 8, 3, 3, 2, SKIN_DEEP, SKIN_SHADE, bottom=SKIN_DEEP, seed=140)
# right brow: (42, 8) 3x3x2
paint_cube(42, 8, 3, 3, 2, SKIN_DEEP, SKIN_SHADE, bottom=SKIN_DEEP, seed=141)

# eye highlight 1: (48, 8) 1x1x1
paint_cube(48, 8, 1, 1, 1, EYE_YELLOW, EYE_DARK, bottom=PUPIL, seed=150)
# eye highlight 2: (52, 8) 1x1x1
paint_cube(52, 8, 1, 1, 1, EYE_YELLOW, EYE_DARK, bottom=PUPIL, seed=151)

# tooth row band: (56, 8) 8x2x2 — paint as teeth
def paint_tooth_band_front(fx, fy, w, h):
    speckle(fx, fy, w, h, TOOTH, TOOTH_SHADE, None,
            shade_density=0.25, highlight_density=0.0, seed=999)
    # Tooth gaps
    for gap in (2, 4, 6):
        if gap < w:
            img.putpixel((fx + gap, fy), SKIN_DEEP)
            if h > 1:
                img.putpixel((fx + gap, fy + 1), SKIN_DEEP)

paint_cube(56, 8, 8, 2, 2, TOOTH, TOOTH_SHADE, bottom=SKIN_DEEP,
           front_painter=paint_tooth_band_front, seed=160)

# left horn/ear: (80, 20) 2x9x2 — small horn nub
paint_cube(80, 20, 2, 9, 2, SKIN_SHADE, SKIN_DEEP, bottom=SKIN_DEEP, seed=170)

# right ear flap: (88, 20) 5x2x4
paint_cube(88, 20, 5, 2, 4, SKIN_FLESH, SKIN, bottom=SKIN_SHADE, seed=171)


# ---------------------------------------------------------------------------
# BODY — root.body cubes
# ---------------------------------------------------------------------------
# body main torso: (0, 35) 20x19x12
def paint_body_front(fx, fy, w, h):
    # Belly is slightly lighter
    mid_x = fx + w // 2
    for py in range(fy + h // 2, fy + h):
        for px in range(fx + 3, fx + w - 3):
            if (px + py) % 3 != 0:
                img.putpixel((px, py), SKIN_BELLY)
    # Belly button
    img.putpixel((mid_x, fy + h - 4), SKIN_DEEP)
    img.putpixel((mid_x, fy + h - 3), SKIN_DEEP)
    # Chest hair-line / fold
    for px in range(fx + 4, fx + w - 4):
        img.putpixel((px, fy + 2), SKIN_SHADE)
    # Scattered scars
    fill_rect(fx + 5, fy + 6, 4, 1, SKIN_DEEP)
    fill_rect(fx + 12, fy + 10, 3, 1, SKIN_DEEP)

paint_cube(0, 35, 20, 19, 12, SKIN_BELLY, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_body_front, seed=200)

# body shoulder slab: (54, 34) 26x15x15 — paint top with heavy moss
def paint_slab_top(tx, ty, w, d):
    scatter_moss(tx, ty, w, d, density=0.65, seed=2)

paint_cube(54, 34, 26, 15, 15, SKIN, SKIN, bottom=SKIN_SHADE,
           top_painter=paint_slab_top, seed=210)

# body belt strap: (0, 62) 18x4x2 — leather strap across chest
paint_cube(0, 62, 18, 4, 2, LOIN, LOIN, bottom=LOIN_DARK, seed=220)
# Add stitching dots along the strap front
strap_fy = 62 + 2
for sx in range(0 + 2, 0 + 2 + 18, 3):
    if sx + 1 < SIZE and strap_fy + 1 < SIZE:
        img.putpixel((sx, strap_fy + 1), LOIN_DARK)

# small body detail: (72, 0) 3x9x2 — paint as another strap segment
paint_cube(72, 0, 3, 9, 2, LOIN, LOIN, bottom=LOIN_DARK, seed=221)

# top tuft: (80, 0) 10x3x9 — mossy top
paint_cube(80, 0, 10, 3, 9, MOSS, SKIN, bottom=SKIN_SHADE, seed=230)
def paint_tuft_top(tx, ty, w, d):
    scatter_moss(tx, ty, w, d, density=0.85, seed=3)
paint_tuft_top(80 + 9, 0, 10, 9)

# moss patch top mid: (80, 9) 12x2x10
paint_cube(80, 9, 12, 2, 10, MOSS, SKIN, bottom=SKIN_SHADE, seed=231)
scatter_moss(80 + 10, 9, 12, 10, density=0.75, seed=4)

# moss patch top L: (80, 21) 8x2x9 — note this overlaps a bit with brow region
# (80,20) but in a different area of the unwrap (y=21 vs 20). Paint as moss.
# Skipping this one if it would clash; the existing (80,20) horn is 2x9x2 with
# unwrap going to (94, 31). The (80,21) moss unwrap goes to (114, 32). They
# overlap at x=80..82 but the horn occupies only those columns anyway. Live
# with it — moss-on-horn looks plausible.
paint_cube(80, 21, 8, 2, 9, MOSS_DARK, SKIN_SHADE, bottom=SKIN_DEEP, seed=232)


# ---------------------------------------------------------------------------
# LEFT ARM
# ---------------------------------------------------------------------------
# upper arm: (0, 68) 8x18x10
def paint_arm_front(fx, fy, w, h):
    # Vertical muscle striations
    for px in range(fx + 1, fx + w - 1, 2):
        for py in range(fy + 2, fy + h - 2, 3):
            img.putpixel((px, py), SKIN_SHADE)
    # A scar
    fill_rect(fx + 1, fy + 4, 3, 1, SKIN_DEEP)

paint_cube(0, 68, 8, 18, 10, SKIN_BELLY, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_arm_front, seed=300)
# forearm: (30, 68) 9x16x11
paint_cube(30, 68, 9, 16, 11, SKIN_BELLY, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_arm_front, seed=301)
# fist: (64, 68) 10x6x12
def paint_fist_front(fx, fy, w, h):
    # Knuckle creases
    for kx in (fx + 1, fx + 4, fx + 7):
        if kx + 1 < fx + w:
            img.putpixel((kx, fy + h // 2), SKIN_DEEP)
            img.putpixel((kx + 1, fy + h // 2), SKIN_DEEP)

paint_cube(64, 68, 10, 6, 12, SKIN_BELLY, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_fist_front, seed=302)

# wrist band: (86, 20) 3x10x2 — leather wrap
paint_cube(86, 20, 3, 10, 2, LOIN, LOIN, bottom=LOIN_DARK, seed=303)

# knuckle nubs: (0, 112) 3x2x3, (12, 112) 3x2x3
paint_cube(0, 112, 3, 2, 3, NAIL, SKIN_SHADE, bottom=NAIL_DARK, seed=310)
paint_cube(12, 112, 3, 2, 3, NAIL, SKIN_SHADE, bottom=NAIL_DARK, seed=311)


# ---------------------------------------------------------------------------
# CLUB (child of left arm)
# ---------------------------------------------------------------------------
def paint_club_front(fx, fy, w, h):
    # Vertical wood grain
    for py in range(fy, fy + h):
        for px in range(fx, fx + w):
            if (px + py * 2) % 7 == 0:
                img.putpixel((px, py), CLUB_DARK)
            elif (px + py * 3) % 11 == 0:
                img.putpixel((px, py), CLUB_LIGHT)


# handle: (0, 94) 5x32x5
paint_cube(0, 94, 5, 32, 5, CLUB_DARK, CLUB_WOOD, bottom=CLUB_DARK,
           front_painter=paint_club_front, seed=400)
# club head: (20, 94) 12x18x10
paint_cube(20, 94, 12, 18, 10, CLUB_DARK, CLUB_WOOD, bottom=CLUB_DARK,
           front_painter=paint_club_front, seed=401)
# club tip: (64, 94) 6x10x12
paint_cube(64, 94, 6, 10, 12, CLUB_DARK, CLUB_BARK, bottom=CLUB_DARK,
           front_painter=paint_club_front, seed=402)
# knob: (96, 94) 6x7x5
paint_cube(96, 94, 6, 7, 5, CLUB_DARK, CLUB_WOOD, bottom=CLUB_DARK,
           front_painter=paint_club_front, seed=403)
# bottom spike: (96, 106) 5x6x14
paint_cube(96, 106, 5, 6, 14, CLUB_DARK, CLUB_BARK, bottom=CLUB_DARK,
           front_painter=paint_club_front, seed=404)
# top wedge: (0, 118) 7x4x7
paint_cube(0, 118, 7, 4, 7, CLUB_DARK, CLUB_WOOD, bottom=CLUB_DARK,
           front_painter=paint_club_front, seed=405)


# ---------------------------------------------------------------------------
# RIGHT ARM (mirrors share UVs with left for the main 3 cubes)
# ---------------------------------------------------------------------------
# Mirrored cubes share UV with the left arm — already painted.
# right-hand knuckles: (24, 112), (36, 112)
paint_cube(24, 112, 3, 2, 3, NAIL, SKIN_SHADE, bottom=NAIL_DARK, seed=320)
paint_cube(36, 112, 3, 2, 3, NAIL, SKIN_SHADE, bottom=NAIL_DARK, seed=321)


# ---------------------------------------------------------------------------
# LEGS
# ---------------------------------------------------------------------------
# thigh: (102, 62) 7x13x10 — overflows 128 width, now fits at 256
def paint_leg_front(fx, fy, w, h):
    # A few dark vertical creases / dirt streaks
    for px in range(fx + 1, fx + w - 1, 3):
        for py in range(fy + 2, fy + h - 2):
            if py % 4 == 0:
                img.putpixel((px, py), SKIN_SHADE)
    # Dirt smudges near the bottom
    fill_rect(fx + 1, fy + h - 3, 2, 1, SKIN_DEEP)
    fill_rect(fx + w - 3, fy + h - 2, 2, 1, SKIN_DEEP)

paint_cube(102, 62, 7, 13, 10, SKIN_BELLY, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_leg_front, seed=500)
# foot: (102, 84) 10x5x15 — overflows now fits at 256
paint_cube(102, 84, 10, 5, 15, SKIN, SKIN, bottom=SKIN_DEEP,
           front_painter=paint_leg_front, seed=501)

# loincloth on right leg: (94, 9) 3x8x2
paint_cube(94, 9, 3, 8, 2, LOIN, LOIN, bottom=LOIN_DARK, seed=510)

# toes: (48, 112), (60, 112), (72, 112), (84, 112) — all 3x2x3
for tx in (48, 60, 72, 84):
    paint_cube(tx, 112, 3, 2, 3, NAIL, SKIN_SHADE, bottom=NAIL_DARK, seed=520 + tx)


# ---------------------------------------------------------------------------
# Save
# ---------------------------------------------------------------------------
out_path = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "src", "main", "resources", "assets", "hogwartsmagic",
    "textures", "entity", "troll.png",
)
out_path = os.path.normpath(out_path)
img.save(out_path)
print("Wrote", out_path)
