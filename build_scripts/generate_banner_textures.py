"""The Great Hall's hanging cloth banners, for the hogwartsmagic:memory_banner_<design> Pensieve actors.

    python build_scripts/generate_banner_textures.py [preview.png]

Writes textures/entity/memory_banner_<design>.png for the designs gryffindor, hufflepuff, ravenclaw, slytherin and
hogwarts: 128 x 512 each (64 px a block on the 2 x 8 block banner), drawn 4x supersampled and filtered down.
MemoryBannerRenderer maps the whole sheet onto the cloth (u across, v down) and textures the hanging rod from the
gold sleeve at the top (ROD_UV there: keep it in step with SLEEVE below):

    v   0 ..  20   the gold rod sleeve (a darker seam, stitching)
    v  20 .. 416   the field: rich cloth with soft vertical folds and a fine weave, a gold border inset from the
                   edge, the house emblem (lion, badger, eagle, snake) or the Hogwarts crest, and a chevron in the
                   house's second colour following the point
    v 416 .. 476   the point, closing to the centre at v 476
    v 476 .. 512   the gold fringe below the point's two edges and a tassel at the tip; alpha 0 elsewhere

Alpha is either 0 (outside the point and between fringe strands' ragged ends) or 255: the renderer uses a cutout
render type. The art is kept low in fine contrast (folds and weave are gentle) so it does not shimmer at the
distances the hall is seen from (entity textures have no mipmaps). Needs Pillow (plain `python`, not -I).
"""
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity')

W, H = 128, 512
SS = 4                                  # supersampling
SW, SH = W * SS, H * SS
SLEEVE = 20                             # rod sleeve rows (final pixels)
POINT_TOP, POINT_TIP = 416, 476         # the point's shoulders and tip (final pixels)
FRINGE = 30                             # fringe length below the point's edges (final pixels)
CHEVRON = 310                           # the chevron's top at the edges (final pixels)

GOLD = (212, 168, 72)
GOLD_HI = (246, 214, 128)
GOLD_LO = (132, 92, 34)
SILVER = (196, 200, 206)
SILVER_HI = (240, 242, 246)
SILVER_LO = (110, 114, 122)
BRONZE = (184, 120, 62)
BRONZE_HI = (226, 170, 104)
BRONZE_LO = (106, 64, 30)
BLACK = (34, 30, 28)
BLACK_HI = (78, 70, 64)
BLACK_LO = (14, 12, 12)

# design: field colour, emblem metal (base, highlight, shade), chevron colour, outline colour
DESIGNS = {
    'gryffindor': dict(field=(124, 14, 20), metal=(GOLD, GOLD_HI, GOLD_LO), chevron=(206, 160, 60),
                       outline=(58, 6, 10)),
    'hufflepuff': dict(field=(214, 160, 34), metal=(BLACK, BLACK_HI, BLACK_LO), chevron=(40, 34, 30),
                       outline=(96, 62, 10)),
    'ravenclaw': dict(field=(22, 40, 96), metal=(BRONZE, BRONZE_HI, BRONZE_LO), chevron=(170, 112, 58),
                      outline=(8, 14, 40)),
    'slytherin': dict(field=(20, 84, 48), metal=(SILVER, SILVER_HI, SILVER_LO), chevron=(176, 182, 190),
                      outline=(6, 34, 18)),
    'hogwarts': dict(field=(46, 26, 58), metal=(GOLD, GOLD_HI, GOLD_LO), chevron=(196, 152, 62),
                     outline=(20, 10, 26)),
}
HOUSE_FIELDS = {k: DESIGNS[k]['field'] for k in ('gryffindor', 'hufflepuff', 'ravenclaw', 'slytherin')}
HOUSE_METALS = {k: DESIGNS[k]['metal'] for k in HOUSE_FIELDS}


# ---------------------------------------------------------------- geometry helpers (supersampled pixels)
def S(v):
    return v * SS


def ell(cx, cy, rx, ry, ang=0.0, n=48):
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        x, y = rx * math.cos(t), ry * math.sin(t)
        out.append((cx + x * ca - y * sa, cy + x * sa + y * ca))
    return out


def capsule(d, p0, p1, w0, w1, fill):
    """A tapered stroke from p0 (half-width w0) to p1 (half-width w1) with round ends."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    ln = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / ln, dx / ln
    d.polygon([(p0[0] + nx * w0, p0[1] + ny * w0), (p1[0] + nx * w1, p1[1] + ny * w1),
               (p1[0] - nx * w1, p1[1] - ny * w1), (p0[0] - nx * w0, p0[1] - ny * w0)], fill=fill)
    d.ellipse([p0[0] - w0, p0[1] - w0, p0[0] + w0, p0[1] + w0], fill=fill)
    d.ellipse([p1[0] - w1, p1[1] - w1, p1[0] + w1, p1[1] + w1], fill=fill)


def bezier(pts, n=40):
    out = []
    for i in range(n + 1):
        t = i / n
        q = list(pts)
        while len(q) > 1:
            q = [(q[j][0] + (q[j + 1][0] - q[j][0]) * t, q[j][1] + (q[j + 1][1] - q[j][1]) * t)
                 for j in range(len(q) - 1)]
        out.append(q[0])
    return out


def stroke(d, pts, w0, w1, fill):
    for i in range(len(pts) - 1):
        f0, f1 = i / (len(pts) - 1), (i + 1) / (len(pts) - 1)
        capsule(d, pts[i], pts[i + 1], w0 + (w1 - w0) * f0, w0 + (w1 - w0) * f1, fill)


# ---------------------------------------------------------------- emblems
# Each draws a white silhouette into mask draw `m` (and dark detail into `det`) inside the box (x0, y0, size):
# a unit square scaled by size. P(u, v) maps unit coordinates.
def _p(x0, y0, s):
    return lambda u, v: (x0 + u * s / 100.0, y0 + v * s / 100.0)


def lion(m, det, x0, y0, s):
    """A lion rampant facing the viewer's left: rearing on one hind leg, forepaws raised and clawing, the mane
    flowing down the neck, the tail swept up in an S with a tuft."""
    P = _p(x0, y0, s)
    k = s / 100.0
    # torso, leaning forward (top to the left), the chest deep
    m.polygon(ell(*P(52, 52), 12 * k, 24 * k, -28), fill=255)
    m.polygon(ell(*P(44, 40), 11 * k, 12 * k), fill=255)                     # chest
    m.polygon(ell(*P(60, 68), 12 * k, 13 * k, -10), fill=255)                # haunch
    # hind legs: the standing one behind, the raised one forward
    stroke(m, [P(64, 74), P(70, 84), P(64, 95)], 6.5 * k, 4 * k, 255)
    capsule(m, P(64, 95), P(55, 96), 3.6 * k, 3 * k, 255)
    stroke(m, [P(54, 74), P(42, 78), P(38, 90)], 6.5 * k, 4 * k, 255)
    capsule(m, P(38, 90), P(29, 88), 3.6 * k, 3 * k, 255)
    # forelegs reaching out to the left, one high, one low, with claws
    stroke(m, [P(40, 36), P(28, 30), P(18, 20)], 5.5 * k, 3.8 * k, 255)
    stroke(m, [P(44, 46), P(30, 48), P(18, 42)], 5.5 * k, 3.8 * k, 255)
    for (px, py), ang in ((P(18, 20), -135), (P(18, 42), -175)):
        for da in (-35, 0, 35):
            a = math.radians(ang + da)
            capsule(m, (px, py), (px + math.cos(a) * 7 * k, py + math.sin(a) * 7 * k), 2.2 * k, 0.8 * k, 255)
    # the mane: a shaggy mass of lobes round the head and down the neck to the chest
    mane = ((40, 9, 7), (47, 14, 7), (50, 22, 7), (49, 30, 7), (45, 37, 6.5), (38, 39, 6), (31, 9, 6.5),
            (36, 6, 6), (43, 24, 9), (40, 31, 8))
    for u, v, r in mane:
        cx_, cy_ = P(u, v)
        m.ellipse([cx_ - r * k, cy_ - r * k, cx_ + r * k, cy_ + r * k], fill=255)
    # head in profile: skull, a blunt muzzle, the jaw open
    hx, hy = P(30, 20)
    m.polygon(ell(hx, hy, 8.5 * k, 8.5 * k), fill=255)
    m.polygon([(hx - 4 * k, hy - 6 * k), (hx - 15 * k, hy - 3 * k), (hx - 16 * k, hy + 3 * k), (hx - 4 * k, hy + 4 * k)],
              fill=255)
    m.polygon([(hx - 4 * k, hy + 5.5 * k), (hx - 13 * k, hy + 6.5 * k), (hx - 12 * k, hy + 9.5 * k),
               (hx - 2 * k, hy + 9 * k)], fill=255)
    # tail: from the rump out behind, up in an S, ending in a tuft
    tail = bezier([P(68, 72), P(92, 74), P(76, 44), P(88, 30)], 30)
    stroke(m, tail, 3.4 * k, 2.4 * k, 255)
    tx, ty = tail[-1]
    for ang in (-120, -80, -40, -10):
        a = math.radians(ang)
        capsule(m, (tx, ty + 3 * k), (tx + math.cos(a) * 9 * k, ty + 3 * k + math.sin(a) * 9 * k), 3 * k, 0.8 * k,
                255)
    # detail: eye, nose, the mouth, locks in the mane and the line of the ribs and thigh
    w = max(1, int(1.8 * k))
    det.ellipse([hx - 6 * k, hy - 3.5 * k, hx - 2.5 * k, hy - 0.5 * k], fill=255)
    det.ellipse([hx - 17 * k, hy - 3.5 * k, hx - 13.5 * k, hy + 0.5 * k], fill=255)
    det.line([(hx - 14 * k, hy + 4.8 * k), (hx - 3 * k, hy + 4.8 * k)], fill=255, width=w)
    for u, v, a0, a1 in ((44, 16, 200, 300), (46, 28, 180, 280), (40, 34, 200, 300), (36, 10, 160, 260)):
        cx_, cy_ = P(u, v)
        det.arc([cx_ - 6 * k, cy_ - 6 * k, cx_ + 6 * k, cy_ + 6 * k], a0, a1, fill=255, width=w)
    det.line([P(50, 56), P(58, 50)], fill=255, width=w)
    det.arc([P(50, 58)[0], P(50, 58)[1], P(70, 80)[0], P(70, 80)[1]], 180, 280, fill=255, width=w)


def badger(m, det, x0, y0, s):
    """A badger walking to the viewer's left: low heavy body, short legs, the striped head lowered."""
    P = _p(x0, y0, s)
    k = s / 100.0
    m.polygon(ell(*P(54, 56), 30 * k, 16 * k, -4), fill=255)
    m.polygon(ell(*P(70, 54), 18 * k, 15 * k, 8), fill=255)
    # legs
    for a, b in (((36, 62), (34, 80)), ((46, 64), (48, 80)), ((70, 62), (66, 80)), ((80, 60), (82, 79))):
        stroke(m, [P(*a), P(*b)], 6 * k, 5 * k, 255)
        capsule(m, P(b[0] - 1, b[1] + 1), P(b[0] - 7, b[1] + 2), 3.5 * k, 2.5 * k, 255)     # paw and claws
    # head: a wedge to the snout
    m.polygon([P(32, 44), P(30, 64), P(14, 62), P(4, 58), P(6, 54), P(18, 46)], fill=255)
    m.polygon(ell(*P(28, 52), 9 * k, 10 * k), fill=255)
    m.polygon(ell(*P(30, 41), 3.5 * k, 3 * k), fill=255)                     # ear
    # tail
    stroke(m, [P(86, 50), P(94, 46), P(97, 48)], 4 * k, 2 * k, 255)
    # detail: the white face stripe (drawn as 'detail' then filled light), the eye, a shoulder line
    det.polygon([P(5, 55), P(18, 47.5), P(31, 44.5), P(34, 48), P(20, 52), P(7, 58)], fill=255)
    det.polygon(ell(*P(30, 41), 1.6 * k, 1.4 * k), fill=255)


def eagle(m, det, x0, y0, s):
    """An eagle displayed: wings spread, head turned to the viewer's left, tail fanned, talons out."""
    P = _p(x0, y0, s)
    k = s / 100.0
    # body and breast
    m.polygon(ell(*P(50, 52), 11 * k, 22 * k), fill=255)
    # wings: raised, the feathers fingered at the ends
    for side in (-1, 1):
        def W(u, v):
            return P(50 + side * u, v)
        wing = [W(6, 40), W(18, 26), W(30, 16), W(44, 6), W(48, 10), W(46, 18)]
        # the trailing edge, fingered: alternating long and short feather tips back down to the body
        feathers = [(48, 22), (40, 26), (46, 30), (36, 34), (42, 39), (31, 41), (36, 47), (24, 47), (26, 53),
                    (14, 52), (8, 56)]
        wing += [W(u, v) for u, v in feathers]
        m.polygon(wing, fill=255)
    # head and neck, beak to the left
    stroke(m, [P(50, 34), P(46, 24)], 7 * k, 6 * k, 255)
    m.polygon(ell(*P(44, 20), 8 * k, 7 * k), fill=255)
    m.polygon([P(37, 17), P(28, 20), P(30, 23), P(37, 24)], fill=255)      # beak
    # tail fan
    m.polygon([P(44, 70), P(56, 70), P(64, 90), P(56, 88), P(50, 94), P(44, 88), P(36, 90)], fill=255)
    # legs and talons
    for side in (-1, 1):
        stroke(m, [P(50 + side * 6, 68), P(50 + side * 14, 78)], 3.5 * k, 3 * k, 255)
        tx, ty = P(50 + side * 14, 78)
        for da in (-40, 0, 40):
            a = math.radians((90 if side > 0 else 90) + da + side * 20)
            capsule(m, (tx, ty), (tx + math.cos(a) * 6 * k * side, ty + math.sin(a) * 6 * k), 1.8 * k, 0.8 * k,
                    255)
    # detail: eye, beak line, wing coverts, breast feathers
    det.ellipse([P(41, 18)[0], P(41, 18)[1], P(44, 21)[0], P(44, 21)[1]], fill=255)
    for side in (-1, 1):
        for i, v in enumerate((30, 38)):
            a0 = P(50 + side * (12 + i * 3), v)
            a1 = P(50 + side * (32 + i * 4), v - 12 + i * 4)
            det.line([a0, a1], fill=255, width=max(1, int(1.8 * k)))
    for v in (48, 56, 62):
        det.arc([P(44, v)[0], P(44, v)[1], P(56, v + 6)[0], P(56, v + 6)[1]], 20, 160, fill=255,
                width=max(1, int(1.6 * k)))


def snake(m, det, x0, y0, s):
    """A serpent rising in an S, head up and to the viewer's left, tongue out, coiled tail at the foot."""
    P = _p(x0, y0, s)
    k = s / 100.0
    body = bezier([P(70, 96), P(20, 92), P(16, 72), P(56, 64), P(92, 56), P(84, 30), P(52, 30), P(30, 30),
                   P(30, 16), P(42, 10)], 120)
    n = len(body)
    for i in range(n - 1):
        f = i / (n - 1)
        w = (2.5 + 7.5 * min(1.0, f * 3.0)) * (1.0 - 0.25 * f) * k
        capsule(m, body[i], body[i + 1], w, w, 255)
    hx, hy = body[-1]
    m.polygon(ell(hx - 2 * k, hy, 10 * k, 7 * k, -15), fill=255)
    # tongue: forked, out to the left
    tw = max(1, int(1.6 * k))
    m.line([(hx - 11 * k, hy + 2 * k), (hx - 20 * k, hy + 2 * k)], fill=255, width=tw)
    m.line([(hx - 20 * k, hy + 2 * k), (hx - 24 * k, hy - 1 * k)], fill=255, width=tw)
    m.line([(hx - 20 * k, hy + 2 * k), (hx - 24 * k, hy + 5 * k)], fill=255, width=tw)
    # detail: the eye and a belly line of scales down the body
    det.ellipse([hx - 5 * k, hy - 4 * k, hx - 1 * k, hy - 0.5 * k], fill=255)
    for i in range(4, n - 8, 4):
        (xa, ya), (xb, yb) = body[i - 1], body[i + 1]
        ln = math.hypot(xb - xa, yb - ya) or 1.0
        nx, ny = -(yb - ya) / ln, (xb - xa) / ln
        x, y = body[i]
        f = i / (n - 1)
        w = (2.5 + 7.5 * min(1.0, f * 3.0)) * (1.0 - 0.25 * f) * k * 0.75
        det.line([(x - nx * w, y - ny * w), (x + nx * w, y + ny * w)], fill=110, width=max(1, int(1.3 * k)))


EMBLEMS = {'gryffindor': lion, 'hufflepuff': badger, 'ravenclaw': eagle, 'slytherin': snake}


# ---------------------------------------------------------------- painting
def fabric(img, field, rnd, x_folds=4.0):
    """Fill with the field colour: soft vertical folds and a fine low-contrast weave."""
    px = img.load()
    fr, fg, fb = field
    phase = [rnd.uniform(0, 6.28) for _ in range(3)]
    for y in range(H):
        for x in range(W):
            u = x / W
            fold = (0.07 * math.sin(u * math.pi * 2 * x_folds + phase[0] + y * 0.004)
                    + 0.035 * math.sin(u * math.pi * 2 * 2.3 + phase[1] - y * 0.006))
            weave = 0.025 if (x + y) % 2 == 0 else -0.025
            if y % 4 == 0:
                weave -= 0.02
            grain = rnd.uniform(-0.02, 0.02)
            # deeper at the edges, as cloth turns away; a little darker toward the foot
            edge = -0.08 * (abs(u - 0.5) * 2) ** 3 - 0.06 * (y / H)
            f = 1.0 + fold + weave + grain + edge
            px[x, y] = (max(0, min(255, int(fr * f))), max(0, min(255, int(fg * f))), max(0, min(255, int(fb * f))),
                        255)


def metal_fill(mask, metal, box):
    """An RGBA layer: metal colour over `mask` with a soft top-left light and a bottom-right shade."""
    base, hi, lo = metal
    x0, y0, x1, y1 = (int(v) for v in box)
    n = 32
    small = Image.new('RGBA', (n, n))
    sp = small.load()
    for y in range(n):
        for x in range(n):
            t = x / (n - 1) * 0.45 + y / (n - 1) * 0.55
            if t < 0.4:
                c = [int(hi[i] + (base[i] - hi[i]) * (t / 0.4)) for i in range(3)]
            else:
                c = [int(base[i] + (lo[i] - base[i]) * ((t - 0.4) / 0.6)) for i in range(3)]
            sp[x, y] = tuple(c) + (255,)
    grad = Image.new('RGBA', mask.size, lo + (255,))
    grad.paste(small.resize((max(1, x1 - x0), max(1, y1 - y0)), Image.BILINEAR), (x0, y0))
    layer = Image.new('RGBA', mask.size, (0, 0, 0, 0))
    layer.paste(grad, (0, 0), mask)
    return layer


def outline_of(mask, px):
    return mask.filter(ImageFilter.MaxFilter(px * 2 + 1))


def shape_points():
    """The banner outline (supersampled): straight sides down to the shoulders, then the point to the tip."""
    return [(0, 0), (SW, 0), (SW, S(POINT_TOP)), (SW / 2, S(POINT_TIP)), (0, S(POINT_TOP))]


def inset_points(d):
    """The outline moved in by d (supersampled px), the point kept parallel."""
    slope = (S(POINT_TIP) - S(POINT_TOP)) / (SW / 2)
    tip_drop = d * math.sqrt(1 + slope * slope)
    return [(d, S(SLEEVE) + d), (SW - d, S(SLEEVE) + d), (SW - d, S(POINT_TOP) + d * slope - tip_drop + d),
            (SW / 2, S(POINT_TIP) - tip_drop), (d, S(POINT_TOP) + d * slope - tip_drop + d)]


def crest(m_layer, x0, y0, s):
    """The Hogwarts crest: a quartered shield (Gryffindor, Slytherin over Hufflepuff, Ravenclaw), each quarter with
    its beast, a gold cross between them, a gold border and a central H on a gold boss."""
    k = s / 100.0
    P = _p(x0, y0, s)
    shield = [P(4, 4), P(96, 4), P(96, 46)] + bezier([P(96, 46), P(96, 80), P(70, 92), P(50, 104)], 16)[1:] + \
        bezier([P(50, 104), P(30, 92), P(4, 80), P(4, 46)], 16)[1:]
    smask = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(smask).polygon(shield, fill=255)
    quarters = [('gryffindor', (0, 0)), ('slytherin', (1, 0)), ('hufflepuff', (0, 1)), ('ravenclaw', (1, 1))]
    cx, cy = P(50, 46)
    for house, (qx, qy) in quarters:
        qmask = Image.new('L', (SW, SH), 0)
        qd = ImageDraw.Draw(qmask)
        qd.rectangle([cx if qx else 0, cy if qy else 0, SW if qx else cx, SH if qy else cy], fill=255)
        qmask = Image.composite(qmask, Image.new('L', (SW, SH), 0), smask)
        fill = Image.new('RGBA', (SW, SH), HOUSE_FIELDS[house] + (255,))
        m_layer.paste(fill, (0, 0), qmask)
        # the beast, centred in its quarter
        bx = x0 + (0.05 + 0.48 * qx) * s
        by = y0 + (0.04 + 0.44 * qy) * s
        bs = 0.42 * s
        em = Image.new('L', (SW, SH), 0)
        dm = Image.new('L', (SW, SH), 0)
        EMBLEMS[house](ImageDraw.Draw(em), ImageDraw.Draw(dm), bx, by, bs)
        em = Image.composite(em, Image.new('L', (SW, SH), 0), qmask)
        ol = outline_of(em, max(2, int(1.4 * k)))
        m_layer.paste(Image.new('RGBA', (SW, SH), DESIGNS[house]['outline'] + (255,)), (0, 0), ol)
        m_layer.alpha_composite(metal_fill(em, HOUSE_METALS[house], (int(bx), int(by), int(bx + bs), int(by + bs))))
        m_layer.paste(Image.new('RGBA', (SW, SH), DESIGNS[house]['outline'] + (255,)), (0, 0),
                      Image.composite(dm, Image.new('L', (SW, SH), 0), em))
    # gold cross and border
    gm = Image.new('L', (SW, SH), 0)
    gd = ImageDraw.Draw(gm)
    gd.rectangle([cx - 3.2 * k, y0, cx + 3.2 * k, y0 + 110 * k], fill=255)
    gd.rectangle([x0, cy - 3.2 * k, x0 + s, cy + 3.2 * k], fill=255)
    gm = Image.composite(gm, Image.new('L', (SW, SH), 0), smask)
    border = Image.composite(Image.new('L', (SW, SH), 0), outline_of(smask, int(4.5 * k)), smask)
    gm = Image.composite(Image.new('L', (SW, SH), 255), gm, border)
    dark = outline_of(gm, max(2, int(1.2 * k)))
    m_layer.paste(Image.new('RGBA', (SW, SH), (40, 26, 10, 255)), (0, 0),
                  Image.composite(dark, Image.new('L', (SW, SH), 0), outline_of(smask, int(6 * k))))
    m_layer.alpha_composite(metal_fill(gm, (GOLD, GOLD_HI, GOLD_LO), (int(x0), int(y0), int(x0 + s), int(y0 + 1.1 * s))))
    # a gold ribbon under the shield, its ends folded back
    rm = Image.new('L', (SW, SH), 0)
    rd = ImageDraw.Draw(rm)
    top = bezier([P(-2, 104), P(50, 116), P(102, 104)], 20)
    bot = bezier([P(102, 114), P(50, 126), P(-2, 114)], 20)
    rd.polygon(top + bot, fill=255)
    for side in (-1, 1):
        ex = 50 + side * 52
        rd.polygon([P(ex, 104), P(ex + side * 8, 108), P(ex + side * 4, 112), P(ex + side * 8, 118), P(ex, 114)],
                   fill=255)
    m_layer.paste(Image.new('RGBA', (SW, SH), (40, 26, 10, 255)), (0, 0), outline_of(rm, max(2, int(1.2 * k))))
    m_layer.alpha_composite(metal_fill(rm, (GOLD, GOLD_HI, GOLD_LO), (int(x0), int(P(0, 102)[1]), int(x0 + s),
                                                                       int(P(0, 128)[1]))))
    ld = ImageDraw.Draw(m_layer)
    for i in range(9):                       # the motto as a row of dark marks, too small to read anyway
        u = 14 + i * 9
        v = 111 + 5.2 * (1 - ((u - 50) / 50.0) ** 2) * 0.9
        ld.rectangle([P(u, v)[0], P(u, v)[1], P(u + 5, v + 2.2)[0], P(u + 5, v + 2.2)[1]], fill=(96, 62, 22, 255))
    # the boss and its H
    hk = k * 0.85
    bm = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(bm).ellipse([cx - 14 * hk, cy - 14 * hk, cx + 14 * hk, cy + 14 * hk], fill=255)
    m_layer.paste(Image.new('RGBA', (SW, SH), (40, 26, 10, 255)), (0, 0), outline_of(bm, max(2, int(1.4 * hk))))
    m_layer.alpha_composite(metal_fill(bm, (GOLD, GOLD_HI, GOLD_LO), (int(cx - 14 * hk), int(cy - 14 * hk),
                                                                       int(cx + 14 * hk), int(cy + 14 * hk))))
    hm = Image.new('L', (SW, SH), 0)
    hd = ImageDraw.Draw(hm)
    hd.rectangle([cx - 8.5 * hk, cy - 9 * hk, cx - 4 * hk, cy + 9 * hk], fill=255)
    hd.rectangle([cx + 4 * hk, cy - 9 * hk, cx + 8.5 * hk, cy + 9 * hk], fill=255)
    hd.rectangle([cx - 5 * hk, cy - 2 * hk, cx + 5 * hk, cy + 2 * hk], fill=255)
    for sx in (-6.25, 6.25):                    # serifs
        hd.rectangle([cx + (sx - 3.6) * hk, cy - 10 * hk, cx + (sx + 3.6) * hk, cy - 8 * hk], fill=255)
        hd.rectangle([cx + (sx - 3.6) * hk, cy + 8 * hk, cx + (sx + 3.6) * hk, cy + 10 * hk], fill=255)
    m_layer.paste(Image.new('RGBA', (SW, SH), (64, 14, 18, 255)), (0, 0), hm)


def banner(design):
    cfg = DESIGNS[design]
    rnd = random.Random('banner-' + design)
    small = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    fabric(small, cfg['field'], rnd, 3.0 if design == 'hogwarts' else 4.0)
    img = small.resize((SW, SH), Image.NEAREST)

    gold = (GOLD, GOLD_HI, GOLD_LO)
    layer = Image.new('RGBA', (SW, SH), (0, 0, 0, 0))
    # the border: a gold band inset from the edge, a thin dark line each side of it
    band_out = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(band_out).polygon(inset_points(S(4)), fill=255)
    band_in = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(band_in).polygon(inset_points(S(10)), fill=255)
    band = Image.composite(Image.new('L', (SW, SH), 0), band_out, band_in)
    line = Image.composite(Image.new('L', (SW, SH), 0), outline_of(band, S(1)), band)
    layer.paste(Image.new('RGBA', (SW, SH), cfg['outline'] + (255,)), (0, 0), line)
    layer.alpha_composite(metal_fill(band, gold, (0, 0, SW, SH)))
    # a second, thin inner rule in the emblem's metal (black on Hufflepuff, which the gold alone would not frame)
    rule_o = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(rule_o).polygon(inset_points(S(14)), fill=255)
    rule_i = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(rule_i).polygon(inset_points(S(15.5)), fill=255)
    rule = Image.composite(Image.new('L', (SW, SH), 0), rule_o, rule_i)
    layer.alpha_composite(metal_fill(rule, cfg['metal'], (0, 0, SW, SH)))

    # the chevron in the second colour, parallel to the point
    ch_o = Image.new('L', (SW, SH), 0)
    slope = (POINT_TIP - POINT_TOP) / (W / 2)
    for msk, top in ((ch_o, CHEVRON),):
        ImageDraw.Draw(msk).polygon([(S(16), S(top)), (SW / 2, S(top + slope * (W / 2 - 16))), (SW - S(16), S(top)),
                                     (SW - S(16), S(top) + S(14)), (SW / 2, S(top + slope * (W / 2 - 16)) + S(14)),
                                     (S(16), S(top) + S(14))], fill=255)
    chv = ch_o
    layer.paste(Image.new('RGBA', (SW, SH), cfg['outline'] + (255,)), (0, 0), outline_of(chv, S(1)))
    ch_col = cfg['chevron']
    layer.alpha_composite(metal_fill(chv, (ch_col, tuple(min(255, int(c * 1.22)) for c in ch_col),
                                           tuple(int(c * 0.62) for c in ch_col)), (0, S(320), SW, S(400))))
    # a small diamond under the chevron's point
    dm = Image.new('L', (SW, SH), 0)
    dy = CHEVRON + slope * (W / 2 - 16) + 30
    ImageDraw.Draw(dm).polygon([(SW / 2, S(dy - 9)), (SW / 2 + S(7), S(dy)), (SW / 2, S(dy + 9)),
                                (SW / 2 - S(7), S(dy))], fill=255)
    layer.paste(Image.new('RGBA', (SW, SH), cfg['outline'] + (255,)), (0, 0), outline_of(dm, S(1)))
    layer.alpha_composite(metal_fill(dm, gold, (SW // 2 - S(7), S(dy - 9), SW // 2 + S(7), S(dy + 9))))

    # the emblem
    if design == 'hogwarts':
        crest(layer, S(10), S(70), S(108))
    else:
        em = Image.new('L', (SW, SH), 0)
        dt = Image.new('L', (SW, SH), 0)
        ex, ey, es = S(8), S(80), S(112)
        EMBLEMS[design](ImageDraw.Draw(em), ImageDraw.Draw(dt), ex, ey, es)
        layer.paste(Image.new('RGBA', (SW, SH), cfg['outline'] + (255,)), (0, 0), outline_of(em, S(2)))
        layer.alpha_composite(metal_fill(em, cfg['metal'], (ex, ey, ex + es, ey + es)))
        detail_col = cfg['outline'] if design != 'hufflepuff' else (236, 228, 210)
        layer.paste(Image.new('RGBA', (SW, SH), detail_col + (255,)), (0, 0),
                    Image.composite(dt, Image.new('L', (SW, SH), 0), em))
    # gold scroll bars above and below the emblem (a woven band across the cloth)
    for yb in (46, 218):
        bm = Image.new('L', (SW, SH), 0)
        ImageDraw.Draw(bm).rectangle([S(18), S(yb), SW - S(18), S(yb + 5)], fill=255)
        layer.paste(Image.new('RGBA', (SW, SH), cfg['outline'] + (255,)), (0, 0), outline_of(bm, S(1)))
        layer.alpha_composite(metal_fill(bm, gold, (S(18), S(yb), SW - S(18), S(yb + 5))))
        for xd in (S(18), SW - S(18)):
            dd = Image.new('L', (SW, SH), 0)
            ImageDraw.Draw(dd).polygon([(xd, S(yb - 3.5)), (xd + S(5), S(yb + 2.5)), (xd, S(yb + 8.5)),
                                        (xd - S(5), S(yb + 2.5))], fill=255)
            layer.alpha_composite(metal_fill(dd, gold, (xd - S(5), S(yb - 4), xd + S(5), S(yb + 9))))

    img.alpha_composite(layer)

    # the rod sleeve: a gold band across the top with a dark seam and stitching
    sl = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(sl).rectangle([0, 0, SW, S(SLEEVE)], fill=255)
    img.alpha_composite(metal_fill(sl, gold, (0, 0, SW, S(SLEEVE))))
    d = ImageDraw.Draw(img)
    d.rectangle([0, S(SLEEVE) - S(2), SW, S(SLEEVE)], fill=GOLD_LO + (255,))
    for x in range(0, SW, S(6)):
        d.rectangle([x, S(SLEEVE) - S(4), x + S(3), S(SLEEVE) - S(3.4)], fill=(120, 80, 30, 255))
    for x in range(0, SW, S(16)):                     # gathered pleats under the rod
        d.rectangle([x, S(2), x + S(1), S(SLEEVE) - S(5)], fill=(160, 118, 46, 255))

    # the outline of the cloth (alpha) and the fringe below the point's edges
    alpha = Image.new('L', (SW, SH), 0)
    ImageDraw.Draw(alpha).polygon(shape_points(), fill=255)
    fringe = Image.new('RGBA', (SW, SH), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fringe)
    fa = Image.new('L', (SW, SH), 0)
    fad = ImageDraw.Draw(fa)
    fr = random.Random('fringe-' + design)
    strand = S(3)
    for x in range(0, SW, strand):
        xc = x + strand / 2
        edge = S(POINT_TOP) + (S(POINT_TIP) - S(POINT_TOP)) * (1 - abs(xc - SW / 2) / (SW / 2))
        ln = S(FRINGE) * fr.uniform(0.82, 1.0)
        shade = fr.choice((GOLD, GOLD_HI, GOLD, GOLD_LO))
        fd.rectangle([x, edge - S(3), x + strand - SS, edge + ln], fill=shade + (255,))
        fd.rectangle([x + strand - SS, edge - S(3), x + strand, edge + ln], fill=(110, 76, 28, 255))
        fad.rectangle([x, edge - S(3), x + strand, edge + ln], fill=255)
    # a heading cord along the point's edges where the fringe hangs from
    cord = [(0, S(POINT_TOP)), (SW / 2, S(POINT_TIP)), (SW, S(POINT_TOP))]
    fd.line(cord, fill=GOLD_LO + (255,), width=S(5))
    fd.line(cord, fill=GOLD + (255,), width=S(3))
    # the tassel at the tip
    tx, ty = SW / 2, S(POINT_TIP)
    fd.ellipse([tx - S(5), ty - S(3), tx + S(5), ty + S(7)], fill=GOLD_HI + (255,))
    fad.ellipse([tx - S(5), ty - S(3), tx + S(5), ty + S(7)], fill=255)
    for i in range(-4, 5):
        x = tx + i * S(1.4)
        c = GOLD if i % 2 else GOLD_LO
        fd.line([(x, ty + S(5)), (x + i * S(0.4), H * SS - S(1))], fill=c + (255,), width=int(S(1.4)))
        fad.line([(x, ty + S(5)), (x + i * S(0.4), H * SS - S(1))], fill=255, width=int(S(1.4)))
    img.paste(fringe, (0, 0), fa)
    alpha = Image.composite(Image.new('L', (SW, SH), 255), alpha, fa)
    img.putalpha(alpha)

    rgb = img.convert('RGB').resize((W, H), Image.LANCZOS)
    a = img.getchannel('A').resize((W, H), Image.BOX).point(lambda v: 255 if v >= 128 else 0)
    out = rgb.convert('RGBA')
    out.putalpha(a)
    # transparent texels take their neighbours' colour, so a filtered edge never pulls in black
    px = out.load()
    for y in range(H):
        for x in range(W):
            if px[x, y][3] == 0:
                px[x, y] = (110, 80, 32, 0)
    return out


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    sheets = []
    for design in DESIGNS:
        img = banner(design)
        img.save(os.path.join(OUT_DIR, 'memory_banner_%s.png' % design))
        sheets.append(img)
        print('wrote memory_banner_%s.png' % design)
    if len(sys.argv) > 1:
        # a preview: the five at 2x on a dark hall background, plus a far view (a banner ~40 px wide)
        pad = 24
        big = Image.new('RGBA', (pad + len(sheets) * (W * 2 + pad), H * 2 + pad * 2 + 200), (26, 22, 24, 255))
        for i, s in enumerate(sheets):
            big.alpha_composite(s.resize((W * 2, H * 2), Image.NEAREST), (pad + i * (W * 2 + pad), pad))
            far = s.resize((40, 160), Image.NEAREST)
            big.alpha_composite(far, (pad + i * (W * 2 + pad) + W - 20, H * 2 + pad * 2 + 10))
        big.convert('RGB').save(sys.argv[1])
        print('preview', sys.argv[1])


if __name__ == '__main__':
    main()
