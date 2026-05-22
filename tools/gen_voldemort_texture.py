"""
Generate a properly UV-mapped voldemort.png for the WizardModel.

The model expects a 128x128 atlas; each cube is unwrapped in the standard
Minecraft layout:
  Top    (depth x width)  at  (texOffs+d, texOffs)        size  w x d
  Bottom                  at  (texOffs+d+w, texOffs)      size  w x d
  Right  (depth x height) at  (texOffs,    texOffs+d)     size  d x h
  Front                   at  (texOffs+d,  texOffs+d)     size  w x h
  Left                    at  (texOffs+d+w, texOffs+d)    size  d x h
  Back                    at  (texOffs+2d+w, texOffs+d)   size  w x h

Cubes from WizardModel.createBodyLayer():
  head      texOffs(28, 26)  8 x 10 x 8
  nose      texOffs( 0, 59)  2 x  4 x 2   (offset y=-2 relative to head)
  hat brim  texOffs(28, 15) 10 x  1 x10
  hat brim  texOffs( 0,  0) 12 x  3 x12
  hat upper texOffs(28, 44)  8 x  5 x 8
  hat top   texOffs(48,  0)  6 x  8 x 6
  body      texOffs( 0, 41)  8 x 12 x 6
  body robe texOffs( 0, 15)  8 x 20 x 6
  arm L/R   texOffs(28, 57)  4 x 12 x 4
  leg L/R   texOffs(44, 57)  4 x 12 x 4

For Voldemort we want pale (almost-white) skin on the head/nose, deep
black robes on the body and limbs, and a black hood-like hat. Red snake
eyes + slit nostrils + thin mouth painted on the front face of the head.
"""

from PIL import Image, ImageDraw
import os

# --- palette ------------------------------------------------------------------
SKIN        = (218, 213, 207, 255)
SKIN_SHADE  = (188, 183, 178, 255)
SKIN_DEEP   = (155, 150, 146, 255)
SKIN_HIGH   = (238, 234, 228, 255)
EYE_RED     = (185,  35,  45, 255)
EYE_GLOW    = (235, 110, 110, 255)
PUPIL       = ( 30,   8,  10, 255)
MOUTH       = ( 90,  35,  38, 255)
NOSTRIL     = ( 60,  30,  35, 255)

ROBE        = ( 18,  18,  22, 255)
ROBE_SHADE  = (  8,   8,  12, 255)
ROBE_HIGH   = ( 36,  36,  44, 255)
ROBE_EDGE   = ( 50,  50,  60, 255)

# --- canvas -------------------------------------------------------------------
img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

def fill_rect(x, y, w, h, color):
    draw.rectangle((x, y, x + w - 1, y + h - 1), fill=color)

def speckle(x, y, w, h, base, shade, density=0.18, seed=None):
    """Sprinkle darker pixels to give a subtle cloth/skin texture."""
    import random
    rnd = random.Random(seed if seed is not None else (x * 7919 + y * 31 + w))
    for py in range(y, y + h):
        for px in range(x, x + w):
            if rnd.random() < density:
                img.putpixel((px, py), shade)
            else:
                img.putpixel((px, py), base)

def draw_cube_unwrap(tx, ty, w, h, d, top, side, bottom=None, front_painter=None):
    """Paint a standard MC cube unwrap. `top/side/bottom` are base colors;
    `front_painter(face_x, face_y, w, h)` is optional and paints the front face
    after the base fill."""
    if bottom is None:
        bottom = top
    # Top
    speckle(tx + d,         ty,        w, d, top,   _darker(top),    0.12, seed=(tx+ty+1))
    # Bottom
    speckle(tx + d + w,     ty,        w, d, bottom, _darker(bottom),0.12, seed=(tx+ty+2))
    # Right (depth x height)
    speckle(tx,             ty + d,    d, h, side,  _darker(side),   0.16, seed=(tx+ty+3))
    # Front (width x height)
    speckle(tx + d,         ty + d,    w, h, side,  _darker(side),   0.16, seed=(tx+ty+4))
    # Left
    speckle(tx + d + w,     ty + d,    d, h, side,  _darker(side),   0.16, seed=(tx+ty+5))
    # Back
    speckle(tx + 2*d + w,   ty + d,    w, h, side,  _darker(side),   0.16, seed=(tx+ty+6))

    if front_painter is not None:
        front_painter(tx + d, ty + d, w, h)

def _darker(c, amt=22):
    return (max(0, c[0] - amt), max(0, c[1] - amt), max(0, c[2] - amt), c[3])

def _lighter(c, amt=22):
    return (min(255, c[0] + amt), min(255, c[1] + amt), min(255, c[2] + amt), c[3])

# --- head (28, 26) 8x10x8 ----------------------------------------------------
def paint_voldemort_face(fx, fy, w, h):
    """Paint the front 8x10 of the head: skull face with red eyes, slit nose,
    thin mouth. (fx, fy) is the top-left of the front face."""
    # Skin base already drawn; lay shading + features.
    # Cheekbone shadows
    for px in range(fx, fx + w):
        for py in range(fy, fy + h):
            # rim shadow on sides
            edge = min(px - fx, fx + w - 1 - px)
            if edge == 0:
                img.putpixel((px, py), SKIN_SHADE)
    # Brow shadow ridge
    for px in range(fx + 1, fx + w - 1):
        img.putpixel((px, fy + 2), SKIN_SHADE)
    # Red snake eyes (rows 3-4)
    eye_y = fy + 3
    # Left eye 2x1 with pupil
    fill_rect(fx + 1, eye_y, 2, 1, EYE_RED)
    img.putpixel((fx + 1, eye_y), EYE_GLOW)
    img.putpixel((fx + 2, eye_y + 1), PUPIL) if eye_y + 1 < fy + h else None
    # Right eye
    fill_rect(fx + 5, eye_y, 2, 1, EYE_RED)
    img.putpixel((fx + 6, eye_y), EYE_GLOW)
    img.putpixel((fx + 5, eye_y + 1), PUPIL) if eye_y + 1 < fy + h else None
    # Slit nostrils (rows 5-6, middle 2 pixels)
    img.putpixel((fx + 3, fy + 6), NOSTRIL)
    img.putpixel((fx + 4, fy + 6), NOSTRIL)
    # Thin lipless mouth row 8
    fill_rect(fx + 2, fy + 8, 4, 1, MOUTH)
    img.putpixel((fx + 2, fy + 8), _darker(MOUTH, 30))
    img.putpixel((fx + 5, fy + 8), _darker(MOUTH, 30))

draw_cube_unwrap(28, 26, 8, 10, 8, SKIN, SKIN, bottom=SKIN_SHADE,
                 front_painter=paint_voldemort_face)
# Cranium highlight on top of head — Voldemort's bald shiny scalp
for px in range(28 + 8 + 2, 28 + 8 + 8 - 2):
    for py in range(26 + 2, 26 + 8 - 2):
        img.putpixel((px, py), SKIN_HIGH)

# --- nose (0, 59) 2x4x2 ------------------------------------------------------
# Voldemort has almost no nose — paint this cube nearly flat skin tone so it
# reads as a small skin bump with the slits doing the work on the head face.
draw_cube_unwrap(0, 59, 2, 4, 2, SKIN, SKIN_SHADE, bottom=SKIN_SHADE)
# Darken the front so it casts a tiny shadow against the face
fill_rect(0 + 2, 59 + 2, 2, 4, SKIN_SHADE)
img.putpixel((0 + 2, 59 + 4), NOSTRIL)
img.putpixel((0 + 3, 59 + 4), NOSTRIL)

# --- hat brim disk (28, 15) 10x1x10 -----------------------------------------
draw_cube_unwrap(28, 15, 10, 1, 10, ROBE_SHADE, ROBE, bottom=ROBE_SHADE)

# --- hat brim wider (0, 0) 12x3x12 ------------------------------------------
draw_cube_unwrap(0, 0, 12, 3, 12, ROBE_SHADE, ROBE, bottom=ROBE_SHADE)

# --- hat upper (28, 44) 8x5x8 -----------------------------------------------
draw_cube_unwrap(28, 44, 8, 5, 8, ROBE_SHADE, ROBE, bottom=ROBE_SHADE)

# --- hat top (48, 0) 6x8x6 --------------------------------------------------
draw_cube_unwrap(48, 0, 6, 8, 6, ROBE_SHADE, ROBE, bottom=ROBE_SHADE)

# --- body inner (0, 41) 8x12x6 ----------------------------------------------
def paint_body_front(fx, fy, w, h):
    # Robe centerline + clasp at chest
    for py in range(fy, fy + h):
        img.putpixel((fx + w // 2 - 1, py), ROBE_SHADE)
    # Subtle highlight along left edge
    for py in range(fy + 1, fy + h - 1):
        img.putpixel((fx, py), ROBE_HIGH)
    # Bone-pale clavicle hint at the very top
    img.putpixel((fx + 2, fy), SKIN_DEEP)
    img.putpixel((fx + 5, fy), SKIN_DEEP)

draw_cube_unwrap(0, 41, 8, 12, 6, ROBE_SHADE, ROBE, bottom=ROBE_SHADE,
                 front_painter=paint_body_front)

# --- body robe outer (0, 15) 8x20x6 -----------------------------------------
def paint_robe_front(fx, fy, w, h):
    # Long flowing robe lines
    for py in range(fy + 1, fy + h, 3):
        img.putpixel((fx + 2, py), ROBE_SHADE)
        img.putpixel((fx + 5, py), ROBE_SHADE)
    # Tattered bottom hem
    for px in range(fx, fx + w):
        if px % 2 == 0:
            img.putpixel((px, fy + h - 1), ROBE_SHADE)
        else:
            img.putpixel((px, fy + h - 1), ROBE)
    # Centerline crease
    for py in range(fy, fy + h):
        img.putpixel((fx + w // 2 - 1, py), ROBE_SHADE)
    # Side highlights
    for py in range(fy + 2, fy + h - 2):
        if py % 4 == 0:
            img.putpixel((fx, py), ROBE_EDGE)

draw_cube_unwrap(0, 15, 8, 20, 6, ROBE_SHADE, ROBE, bottom=ROBE_SHADE,
                 front_painter=paint_robe_front)

# --- arms (28, 57) 4x12x4 — shared between left + right arm ----------------
def paint_arm_front(fx, fy, w, h):
    # Pale wrist + hand peek at the bottom 2 rows
    fill_rect(fx, fy + h - 2, w, 2, SKIN_SHADE)
    # Bony hand knuckle dot
    img.putpixel((fx + 1, fy + h - 1), SKIN)
    img.putpixel((fx + 2, fy + h - 1), SKIN)
    # Long sleeve crease
    for py in range(fy, fy + h - 2):
        img.putpixel((fx + 1, py), ROBE_SHADE)

draw_cube_unwrap(28, 57, 4, 12, 4, ROBE_SHADE, ROBE, bottom=SKIN_SHADE,
                 front_painter=paint_arm_front)

# --- legs (44, 57) 4x12x4 — shared between left + right leg ----------------
def paint_leg_front(fx, fy, w, h):
    # Robe trousers — solid black with a faint vertical highlight
    for py in range(fy, fy + h):
        if py % 3 == 0:
            img.putpixel((fx + 2, py), ROBE_EDGE)
    # Pale ankle hint at bottom
    fill_rect(fx, fy + h - 1, w, 1, SKIN_DEEP)

draw_cube_unwrap(44, 57, 4, 12, 4, ROBE_SHADE, ROBE, bottom=ROBE_SHADE,
                 front_painter=paint_leg_front)

# --- save --------------------------------------------------------------------
out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "entity", "voldemort.png")
out = os.path.normpath(out)
img.save(out, "PNG")
print(f"wrote {out}")
