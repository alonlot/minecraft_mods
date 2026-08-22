"""Renders the vault door opening, one frame per beat, without launching a client.

    python build_scripts/preview_vault_door.py

This is a **mirror** of `GringottsVaultDoorRenderer.poseFor` and the only way to look at that
function at all: the animation is drawn by a block-entity renderer, so no structure viewer can show
it, and in game it takes a vault, a cart ride and a bare hand to see one frame of it.

Being a mirror it can drift from the Java, so the numbers live at the top of both and the shape of
the code is deliberately the same. What it is for is catching the mistakes that are obvious the
moment you see them and invisible in source: a ripple with no crest, a fold that turns the wrong
way, plates that overlap into a solid slab, a beat that does nothing at all.

Writes `build/vault_door_beats.png` - a strip of frames across the whole ten seconds.
"""

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'build', 'vault_door_beats.png')

TOUCH, RIPPLE, COGS, BOLTS, PART = 20, 44, 40, 48, 48
TOTAL = TOUCH + RIPPLE + COGS + BOLTS + PART
BOLT_COUNT = 24

RIPPLE_AMP = 0.32
RIPPLE_SIGMA = 0.85
BOLT_PULL = 0.55
PART_FOLD = 1.75

HALF = 2                      # the leaf is five by five
RADIUS = math.hypot(HALF, HALF)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def cogs(du, dv, r, radius, s):
    ease = min(1.0, s / 8.0)
    ring = int(clamp(r / max(0.8, radius / 3.0), 0, 2))
    direction = 0.0 if ring == 2 else (1.0 if ring == 0 else -1.0)
    spin = direction * ease * s * 0.004
    c, sn = math.cos(spin), math.sin(spin)
    nu = du * c - dv * sn
    nv = du * sn + dv * c
    return dict(du=nu - du, dv=nv - dv, dn=-0.08 * ease, spin=spin, fold=0.0, tip=0.0, hot=False)


def pose_for(t, du, dv, r, angle, radius):
    if t < TOUCH:
        lean = 0.03 * math.exp(-(r * r) / 9.0) * (t / TOUCH)
        return dict(du=0.0, dv=0.0, dn=0.0, spin=0.0, fold=0.0, tip=lean, hot=False)

    if t < TOUCH + RIPPLE:
        s = (t - TOUCH) / float(RIPPLE)
        front = s * (radius + 1.2)
        d = r - front
        env = math.exp(-(d * d) / (2.0 * RIPPLE_SIGMA * RIPPLE_SIGMA))
        push = RIPPLE_AMP * env * math.cos(d * 1.25)
        return dict(du=0.0, dv=0.0, dn=push, spin=0.0, fold=0.0, tip=push * 1.1,
                    hot=push > RIPPLE_AMP * 0.62)

    if t < TOUCH + RIPPLE + COGS:
        return cogs(du, dv, r, radius, t - TOUCH - RIPPLE)

    if t < TOUCH + RIPPLE + COGS + BOLTS:
        s = t - TOUCH - RIPPLE - COGS
        settled = cogs(du, dv, r, radius, COGS)
        if r < radius - 1.2:
            return settled
        index = ((angle + math.pi) / (2.0 * math.pi)) * BOLT_COUNT
        fired = s * BOLT_COUNT / BOLTS
        k = clamp((fired - index) / 3.0, 0.0, 1.0)
        out = dict(settled)
        out['dn'] = settled['dn'] - BOLT_PULL * k
        out['spin'] = settled['spin'] + k * 0.26
        return out

    if t < TOTAL:
        s = (t - TOUCH - RIPPLE - COGS - BOLTS) / float(PART)
        eased = s * s * (3.0 - 2.0 * s)
        side = 1.0 if du >= 0.0 else -1.0
        local = clamp(eased * 1.7 - (abs(du) / (radius + 1.0)) * 0.7, 0.0, 1.0)
        return dict(du=side * local * (radius + 1.2), dv=0.0, dn=-local * 1.4,
                    spin=0.0, fold=side * local * PART_FOLD, tip=0.0, hot=False)

    return dict(du=0.0, dv=0.0, dn=0.0, spin=0.0, fold=0.0, tip=0.0, hot=False)


# --------------------------------------------------------------------------- drawing

def rot(axis, p, a):
    x, y, z = p
    c, s = math.cos(a), math.sin(a)
    if axis == 'u':                 # about the horizontal in-plane axis
        return x, y * c - z * s, y * s + z * c
    if axis == 'v':                 # about the vertical in-plane axis
        return x * c + z * s, y, -x * s + z * c
    return x * c - y * s, x * s + y * c, z     # about the normal


def frame(t, size=250, scale=22.0, yaw=0.62, pitch=0.34):
    img = Image.new('RGBA', (size, size), (16, 16, 20, 255))
    draw = ImageDraw.Draw(img)

    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def project(p):
        x, y, z = p
        rx = x * cy + z * sy
        rz = -x * sy + z * cy
        ry = y * cp - rz * sp
        return rx, -ry, y * sp + rz * cp

    quads = []
    for u in range(-HALF, HALF + 1):
        for v in range(-HALF, HALF + 1):
            du, dv = float(u), float(v)
            r = math.hypot(du, dv)
            angle = math.atan2(dv, du)
            p = pose_for(t, du, dv, r, angle, RADIUS)

            cx = du + p['du']
            cyy = dv + p['dv']
            cz = p['dn']
            # A plate is a slab: a block face-on, a fifth of a block deep.
            corners = []
            for sxv in (-0.5, 0.5):
                for syv in (-0.5, 0.5):
                    for szv in (-0.1, 0.1):
                        q = (sxv, syv, szv)
                        if p['spin']:
                            q = rot('n', q, p['spin'])
                        if p['fold']:
                            q = rot('v', q, p['fold'])
                        if p['tip']:
                            q = rot('u', q, p['tip'])
                        corners.append((q[0] + cx, q[1] + cyy, q[2] + cz))

            idx = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6),
                   (0, 2, 6, 4), (1, 3, 7, 5)]
            for face in idx:
                pts = [project(corners[i]) for i in face]
                depth = sum(q[2] for q in pts) / 4.0
                base = (236, 214, 150) if p['hot'] else (104, 110, 120)
                shade = 0.55 + 0.45 * clamp((depth + 2.0) / 4.0, 0.0, 1.0)
                colour = tuple(min(255, int(c * shade)) for c in base)
                quads.append((depth, [(q[0], q[1]) for q in pts], colour))

    quads.sort(key=lambda q: q[0])
    for _, pts, colour in quads:
        poly = [(q[0] * scale + size / 2.0, q[1] * scale + size / 2.0) for q in pts]
        draw.polygon(poly, fill=colour, outline=(20, 20, 24, 255))
    draw.text((6, 6), 't=%d' % t, fill=(200, 200, 210, 255))
    return img


def main():
    beats = [
        ('shut', 0),
        ('touch', TOUCH - 2),
        ('ripple in', TOUCH + 12),
        ('ripple out', TOUCH + 32),
        ('cogs', TOUCH + RIPPLE + 30),
        ('bolts', TOUCH + RIPPLE + COGS + 30),
        ('parting', TOUCH + RIPPLE + COGS + BOLTS + 18),
        ('open', TOTAL - 2),
    ]
    tiles = [(name, frame(t)) for name, t in beats]
    w = sum(im.width for _, im in tiles)
    strip = Image.new('RGBA', (w, tiles[0][1].height + 18), (10, 10, 13, 255))
    draw = ImageDraw.Draw(strip)
    x = 0
    for name, im in tiles:
        strip.paste(im, (x, 18))
        draw.text((x + 6, 4), name, fill=(210, 210, 220, 255))
        x += im.width
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    strip.save(OUT)
    print('->', OUT)


if __name__ == '__main__':
    main()
