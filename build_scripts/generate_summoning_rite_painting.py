"""Generate the 'summoning_rite' painting texture -- the Dark Mark plate in the Death Eater camp.

    python build_scripts/generate_summoning_rite_painting.py

The painting is 4x3 blocks, so the texture is exactly 64x48: a data-driven painting variant's
asset_id resolves to assets/<ns>/textures/painting/<id>.png and the file MUST be width*16 by
height*16 or the variant fails to load.

WHAT IT DEPICTS
---------------
The rite that actually summons Voldemort in this mod, painted rather than diagrammed. Three ward
stones stand on the forest floor in the arrangement the code demands -- two on a line four apart,
the third set back two diagonal steps from each (VoldemortSummoningEntity.formsDiagonalTriangle
wants squared distances of 8, 8 and 16) -- with the light between them drawn in so the triangle is
readable. Above them the Dark Mark hangs in the smoke, which is what Morsmordre leaves behind when
the rite takes. A player who has found the camp and learned the spell has been told everything.

STYLE
-----
Vanilla paintings are not illustrations, they are dark canvases with one luminous subject, a very
short palette and visible stipple. Three things carry that here:

  * The mark is rendered as LIGHT, not as line art. It is drawn into a greyscale glow buffer, blurred
    so the edges go smoky, then the sharp silhouette is laid back over the blur at partial strength
    so the skull still reads. Only then is that intensity mapped to colour.
  * Colour is a six-step ramp, and everything -- sky, ground, mark -- is quantised onto its ramp. A
    smooth gradient would look like a screenshot; banding is what makes it look painted.
  * Every quantisation goes through a 4x4 Bayer matrix first, so the bands break up into checkered
    stipple instead of hard stripes. That dither is the single most vanilla-looking thing about it.

The last pass snaps the whole canvas onto PALETTE, and that pass is not cosmetic. Compositing light
over ground over sky generates hundreds of in-between colours -- the first draft came out at 580,
which looks like a screenshot of a render, not like paint. Snapping to twenty-five holds the line.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter

W, H = 64, 48
HORIZON = 33          # first row of forest floor

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'assets', 'hogwartsmagic', 'textures', 'painting', 'summoning_rite.png')

# --- palette -------------------------------------------------------------------------------------
# Short ramps, deliberately. Each is ordered dark -> bright and indexed by a 0..1 intensity.

SKY = [(9, 10, 13), (14, 16, 18), (19, 22, 23), (25, 29, 28)]
GROUND = [(14, 12, 10), (20, 17, 13), (27, 23, 18), (34, 29, 23)]
MARK = [(0, 0, 0), (18, 38, 22), (32, 72, 34), (56, 120, 50), (102, 180, 78),
        (158, 224, 124), (218, 252, 192)]
STONE = [(28, 30, 28), (44, 47, 44), (62, 66, 61), (84, 89, 82), (110, 116, 106)]

TRUNK_NEAR = (6, 7, 7)
TRUNK_FAR = (20, 23, 21)

# Every colour the finished canvas is allowed to use.
PALETTE = SKY + GROUND + MARK + STONE + [TRUNK_NEAR, TRUNK_FAR, (12, 14, 13)]

BAYER = [[0, 8, 2, 10],
         [12, 4, 14, 6],
         [3, 11, 1, 9],
         [15, 7, 13, 5]]


def dither(x, y):
    """4x4 ordered-dither offset in -0.5..0.5 of one quantisation step."""
    return (BAYER[y & 3][x & 3] + 0.5) / 16.0 - 0.5


def ramp(palette, t, x, y):
    """Quantise t (0..1) onto a palette, dithering the band edges."""
    t = min(max(t, 0.0), 1.0)
    idx = t * (len(palette) - 1) + dither(x, y)
    return palette[min(max(int(round(idx)), 0), len(palette) - 1)]


def noise(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


# --- the mark ------------------------------------------------------------------------------------
# Skull and serpent, in a greyscale buffer. Coordinates are chosen so nothing crosses the horizon:
# the mark belongs to the sky, and a serpent tail lying on the ground would flatten the whole thing.

SKULL_CX, SKULL_CY = 32, 13


def draw_mark(glow):
    """Fill `glow` (mode 'L') with the Dark Mark's light. Bright = solid bone, dark = hole."""
    d = ImageDraw.Draw(glow)
    cx, cy = SKULL_CX, SKULL_CY

    # Cranium and jaw as two overlapping ellipses -- a single ellipse reads as an egg, not a skull.
    d.ellipse([cx - 12, cy - 12, cx + 12, cy + 7], fill=255)
    d.ellipse([cx - 8, cy + 1, cx + 8, cy + 12], fill=255)
    d.rectangle([cx - 8, cy - 1, cx + 8, cy + 4], fill=255)     # weld the two together

    # Eye sockets. Punched right through: the holes are what make it a skull at this size.
    d.ellipse([cx - 9, cy - 7, cx - 2, cy + 1], fill=0)
    d.ellipse([cx + 2, cy - 7, cx + 9, cy + 1], fill=0)
    d.polygon([(cx, cy + 1), (cx - 3, cy + 6), (cx + 3, cy + 6)], fill=0)   # nasal cavity

    # Mouth, then teeth cut back into it.
    d.rectangle([cx - 6, cy + 7, cx + 6, cy + 11], fill=0)
    for tx in range(cx - 6, cx + 7, 2):
        d.line([(tx, cy + 7), (tx, cy + 9)], fill=200)
    d.line([(cx - 6, cy + 7), (cx + 6, cy + 7)], fill=190)

    # The serpent leaves the mouth, sweeps left, crosses back under the jaw and lifts its head.
    spine = [(cx, cy + 11), (cx - 5, cy + 13), (cx - 10, cy + 15), (cx - 8, cy + 17),
             (cx - 1, cy + 16), (cx + 6, cy + 15), (cx + 12, cy + 13), (cx + 16, cy + 11)]

    # Cut a dark channel along the path first. Where the body crosses back under the jaw it is the
    # only thing separating serpent from bone -- without it the two merge into one pale smear.
    for i in range(len(spine) - 1):
        d.line([spine[i], spine[i + 1]], fill=0, width=max(3, 6 - i // 2))
    for i in range(len(spine) - 1):
        d.line([spine[i], spine[i + 1]], fill=235, width=max(1, 4 - i // 2))

    hx, hy = spine[-1]
    d.ellipse([hx - 3, hy - 3, hx + 2, hy + 2], fill=0)         # dark halo behind the head
    d.ellipse([hx - 2, hy - 2, hx + 2, hy + 2], fill=255)       # head
    d.point((hx, hy - 1), fill=0)                               # eye
    d.line([(hx + 2, hy + 1), (hx + 6, hy - 1)], fill=210)      # forked tongue
    d.line([(hx + 2, hy + 1), (hx + 6, hy + 2)], fill=210)


def mark_layer():
    """Sharp silhouette, blurred copy, and the two combined into smoky light."""
    sharp = Image.new('L', (W, H), 0)
    draw_mark(sharp)

    smoke = sharp.filter(ImageFilter.GaussianBlur(2.2))
    out = Image.new('L', (W, H), 0)
    sp, sm, op = sharp.load(), smoke.load(), out.load()
    for y in range(H):
        for x in range(W):
            # Blur carries the haze; the sharp copy is laid back over it so the skull keeps an edge.
            v = sm[x, y] * 0.85 + sp[x, y] * 0.55
            v *= 0.75 + 0.5 * noise(x, y, 3)          # break the light up so it reads as smoke
            op[x, y] = int(min(255, v))
    return out


# --- the floor -----------------------------------------------------------------------------------
# Two stones on a line and a third set back: the arrangement the summoning code actually checks for.

# The far pair sit on one line and the third is set nearer the viewer -- the isosceles triangle the
# rite wants, seen from the ground. Size and height carry the depth: shrinking the far pair is what
# stops the three reading as a row of fenceposts.
# x, top y, width, height, tone. Tone is what sells the depth: the mark is the only light in the
# picture and it hangs behind the ring, so the far pair catch it full on the face while the near
# stone is backlit and comes out almost black. Painting all three the same grey put a pale slab in
# the middle of the canvas that read as a doorway rather than as the nearest stone.
STONES = [(12, 33, 4, 7, 0.0), (49, 33, 4, 7, 0.0), (29, 36, 6, 11, -0.5)]


def stone_glow(x, y):
    """Pooled green light on the ground, brightest at the feet of each stone."""
    best = 0.0
    for sx, sy, sw, sh, _ in STONES:
        fx, fy = sx + sw / 2.0, sy + sh - 1
        d = math.hypot((x - fx) * 0.8, y - fy)
        best = max(best, math.exp(-(d * d) / 12.0))
    return best


def draw_floor(px):
    for y in range(HORIZON, H):
        depth = (y - HORIZON) / float(H - 1 - HORIZON)
        for x in range(W):
            # Dark soil, and darker still toward the viewer. A bright floor would compete with the
            # mark, and the whole canvas only works if there is exactly one source of light in it.
            t = 0.42 - depth * 0.34 + noise(x, y, 11) * 0.16
            r, g, b = ramp(GROUND, t, x, y)
            lit = stone_glow(x, y)
            if lit > 0.05:
                # Green light lies ON the ground rather than replacing it, so the soil still reads.
                mix = min(0.9, lit * 1.1)
                gr, gg, gb = ramp(MARK, 0.2 + lit * 0.4, x, y)
                r = int(r * (1 - mix) + gr * mix)
                g = int(g * (1 - mix) + gg * mix)
                b = int(b * (1 - mix) + gb * mix)
            px[x, y] = (r, g, b, 255)


def draw_triangle(px):
    """The light lying between the stones -- this is the part that tells you the shape."""
    feet = [(sx + sw // 2, sy + sh - 1) for sx, sy, sw, sh, _ in STONES]
    edges = [(feet[0], feet[1]), (feet[0], feet[2]), (feet[1], feet[2])]
    for (x0, y0), (x1, y1) in edges:
        steps = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(steps + 1):
            x = int(round(x0 + (x1 - x0) * i / steps))
            y = int(round(y0 + (y1 - y0) * i / steps))
            if not (0 <= x < W and HORIZON <= y < H):
                continue
            if noise(x, y, 21) < 0.16:           # a broken line reads as light, a solid one as paint
                continue
            base = px[x, y]
            gr, gg, gb = ramp(MARK, 0.62 + noise(x, y, 22) * 0.22, x, y)
            px[x, y] = (int(base[0] * 0.15 + gr * 0.85),
                        int(base[1] * 0.15 + gg * 0.85),
                        int(base[2] * 0.15 + gb * 0.85), 255)


def draw_stones(px):
    for sx, sy, sw, sh, tone in STONES:
        for y in range(sy, sy + sh):
            for x in range(sx, sx + sw):
                if not (0 <= x < W and 0 <= y < H):
                    continue
                # Taper the top corners so they read as split rock rather than fenceposts.
                if (y - sy) < 2 and (x == sx or x == sx + sw - 1):
                    continue
                t = 0.55 + tone - (x - sx) / float(sw) * 0.35 + noise(x, y, 31) * 0.3
                px[x, y] = ramp(STONE, t, x, y) + (255,)

        # The rune slit: a carved channel down the face, lit from inside. It stays at full strength
        # on the dark near stone -- that slit is the only thing identifying it as a ward and not a
        # rock, and it is the one part of the silhouette allowed to be bright.
        rx = sx + sw // 2
        for y in range(sy + 2, sy + sh - 2):
            t = 0.55 + 0.45 * math.sin((y - sy) / float(sh) * math.pi)
            px[rx, y] = ramp(MARK, t, rx, y) + (255,)
            if t > 0.8:
                px[rx - 1, y] = ramp(MARK, t - 0.35, rx - 1, y) + (255,)


# --- assembly ------------------------------------------------------------------------------------

def draw_sky(px):
    for y in range(HORIZON):
        for x in range(W):
            # Darkest at the top, lifting toward the horizon haze.
            t = 0.12 + (y / float(HORIZON)) * 0.7 + noise(x, y, 5) * 0.12
            px[x, y] = ramp(SKY, t, x, y) + (255,)


def draw_far_trees(px):
    """Trunks only. Foliage at this size turns into mush; the Forbidden Forest reads from verticals."""
    for x0, w, bottom in [(9, 2, HORIZON), (21, 1, HORIZON - 2),
                          (43, 1, HORIZON - 3), (55, 2, HORIZON)]:
        for y in range(0, bottom):
            lean = int((bottom - y) * 0.06)
            for x in range(x0 + lean, x0 + lean + w):
                if 0 <= x < W and noise(x, y, 41) > 0.12:
                    px[x, y] = TRUNK_FAR + (255,)


def draw_near_trees(px):
    """Drawn AFTER the mark, so the two flanking trunks are cut out of its glow.

    That is the whole reason they exist. Against the raw sky they were invisible -- both are darker
    than the darkest sky step -- and a silhouette needs something bright behind it before it is a
    silhouette at all.
    """
    for x0, w, bottom in [(0, 4, H), (5, 3, HORIZON + 8), (56, 3, HORIZON + 7), (60, 4, H)]:
        for y in range(0, min(bottom, H)):
            for x in range(x0, x0 + w):
                if 0 <= x < W:
                    px[x, y] = TRUNK_NEAR + (255,)
        # A branch or two, to stop them reading as pillars.
        by = 5 + (x0 % 6)
        direction = 1 if x0 < W // 2 else -1
        for i in range(7):
            bx = x0 + (w // 2) + direction * i
            if 0 <= bx < W and 0 <= by + i // 2 < H:
                px[bx, by + i // 2] = TRUNK_NEAR + (255,)


def apply_mark(px, glow):
    g = glow.load()
    for y in range(H):
        for x in range(W):
            v = g[x, y] / 255.0
            if v < 0.04:
                continue
            r, gg, b = ramp(MARK, v, x, y)
            a = min(1.0, v * 1.5)
            base = px[x, y]
            px[x, y] = (int(base[0] * (1 - a) + r * a),
                        int(base[1] * (1 - a) + gg * a),
                        int(base[2] * (1 - a) + b * a), 255)


def apply_shaft(px):
    """A cone of dim light from the mark down onto the stones, tying the two halves together."""
    for y in range(SKULL_CY + 16, H):
        spread = 6 + (y - SKULL_CY - 16) * 1.6
        for x in range(W):
            d = abs(x - SKULL_CX) / spread
            if d > 1.0:
                continue
            t = (1.0 - d) * 0.22 * (1.0 - (y - SKULL_CY - 16) / 40.0)
            if t <= 0.02 or noise(x, y, 51) < 0.45:
                continue
            r, g, b = ramp(MARK, 0.35, x, y)
            base = px[x, y]
            px[x, y] = (int(base[0] * (1 - t) + r * t),
                        int(base[1] * (1 - t) + g * t),
                        int(base[2] * (1 - t) + b * t), 255)


def apply_vignette(px):
    """Vanilla canvases sit darkest at the frame. Also hides the seam where the side faces wrap."""
    for y in range(H):
        for x in range(W):
            edge = min(x, y, W - 1 - x, H - 1 - y)
            if edge >= 3:
                continue
            k = 0.52 + edge * 0.16
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * k), int(g * k), int(b * k), a)


def snap_palette(px):
    """Force every pixel onto PALETTE. Run last, after the vignette, so darkened edges land on a
    genuinely darker entry rather than on a multiplied off-palette colour."""
    cache = {}
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            key = (r, g, b)
            hit = cache.get(key)
            if hit is None:
                hit = min(PALETTE, key=lambda c: (c[0] - r) ** 2 + (c[1] - g) ** 2 + (c[2] - b) ** 2)
                cache[key] = hit
            px[x, y] = hit + (a,)


def main():
    img = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    px = img.load()

    draw_sky(px)
    draw_far_trees(px)
    apply_shaft(px)
    apply_mark(px, mark_layer())
    draw_near_trees(px)
    draw_floor(px)
    draw_triangle(px)
    draw_stones(px)
    apply_vignette(px)
    snap_palette(px)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    used = len(img.convert('RGB').getcolors(65536))
    print('wrote %s (%dx%d, %d colours of %d)'
          % (os.path.normpath(OUT), W, H, used, len(PALETTE)))


if __name__ == '__main__':
    main()
