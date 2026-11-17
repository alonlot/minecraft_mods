#!/usr/bin/env python3
"""Generates the 18x18 status effect icons that had none (they showed the missing-texture square).

Each icon is a filled shape in the effect's colour, shaded like the hand-drawn icons beside them: a dark
outline, a darker lower right and a light upper left. Existing icons are left alone.

Run from the repository root: python3 tools/gen_effect_icons.py
"""
import math
import os

from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/hogwartsmagic/textures/mob_effect"
N = 18


def blank():
    return Image.new("L", (N, N), 0)


def circle(d, cx, cy, r):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)


def sun():
    m = blank()
    d = ImageDraw.Draw(m)
    circle(d, 8.5, 8.5, 3.6)
    for k in range(8):
        a = k * math.pi / 4
        x0, y0 = 8.5 + math.cos(a) * 5.0, 8.5 + math.sin(a) * 5.0
        x1, y1 = 8.5 + math.cos(a) * 7.6, 8.5 + math.sin(a) * 7.6
        d.line([x0, y0, x1, y1], fill=255, width=2)
    return m


def shield():
    m = blank()
    d = ImageDraw.Draw(m)
    d.polygon([(3, 2), (14, 2), (14, 9), (8.5, 16), (3, 9)], fill=255)
    return m


def cloud():
    m = blank()
    d = ImageDraw.Draw(m)
    circle(d, 6, 10, 4)
    circle(d, 11, 8, 5)
    circle(d, 13, 12, 3.5)
    d.rectangle([4, 10, 14, 14], fill=255)
    return m


def chain():
    m = blank()
    d = ImageDraw.Draw(m)
    for cx, cy in ((6, 6), (11.5, 11.5)):
        d.ellipse([cx - 4.5, cy - 4.5, cx + 4.5, cy + 4.5], outline=255, width=2)
    return m


def arrow_up():
    m = blank()
    d = ImageDraw.Draw(m)
    d.polygon([(8.5, 1), (15, 8), (11, 8), (11, 16), (6, 16), (6, 8), (2, 8)], fill=255)
    return m


def flask():
    m = blank()
    d = ImageDraw.Draw(m)
    d.rectangle([7, 1, 10, 6], fill=255)
    d.rectangle([6, 1, 11, 2], fill=255)
    circle(d, 8.5, 11, 5.5)
    return m


def leaf():
    m = blank()
    d = ImageDraw.Draw(m)
    d.polygon([(2, 15), (4, 7), (9, 3), (16, 2), (15, 9), (11, 14), (4, 15)], fill=255)
    return m


def bottle():
    m = blank()
    d = ImageDraw.Draw(m)
    d.rectangle([7, 1, 10, 5], fill=255)
    d.rounded_rectangle([4, 5, 13, 16], radius=3, fill=255)
    return m


def eye():
    m = blank()
    d = ImageDraw.Draw(m)
    d.ellipse([1, 4, 16, 13], fill=255)
    return m


def diamond():
    m = blank()
    d = ImageDraw.Draw(m)
    d.polygon([(8.5, 1), (16, 8.5), (8.5, 16), (1, 8.5)], fill=255)
    return m


def snowflake():
    m = blank()
    d = ImageDraw.Draw(m)
    for k in range(3):
        a = k * math.pi / 3 + math.pi / 2
        dx, dy = math.cos(a) * 7.5, math.sin(a) * 7.5
        d.line([8.5 - dx, 8.5 - dy, 8.5 + dx, 8.5 + dy], fill=255, width=2)
        for s in (-1, 1):
            bx, by = 8.5 + s * dx * 0.6, 8.5 + s * dy * 0.6
            for t in (-1, 1):
                b = a + t * math.pi / 4 + (0 if s > 0 else math.pi)
                d.line([bx, by, bx + math.cos(b) * 2.5, by + math.sin(b) * 2.5], fill=255, width=1)
    return m


def cube():
    m = blank()
    d = ImageDraw.Draw(m)
    d.polygon([(8.5, 1), (16, 5), (16, 13), (8.5, 17), (1, 13), (1, 5)], fill=255)
    return m


def crown():
    m = blank()
    d = ImageDraw.Draw(m)
    d.polygon([(2, 14), (2, 5), (5.5, 9), (8.5, 3), (11.5, 9), (15, 5), (15, 14)], fill=255)
    return m


def spiral():
    m = blank()
    d = ImageDraw.Draw(m)
    pts = []
    for i in range(60):
        a = i * 0.2
        r = 0.6 + a * 0.55
        pts.append((8.5 + math.cos(a) * r, 8.5 + math.sin(a) * r))
    d.line(pts, fill=255, width=2)
    return m


def roots():
    m = blank()
    d = ImageDraw.Draw(m)
    d.line([8.5, 1, 8.5, 9], fill=255, width=3)
    for x1, y1 in ((2, 16), (6, 16), (11, 16), (15, 16)):
        d.line([8.5, 8, x1, y1], fill=255, width=2)
    return m


def ghost():
    m = blank()
    d = ImageDraw.Draw(m)
    circle(d, 8.5, 7, 6)
    d.rectangle([2.5, 7, 14.5, 14], fill=255)
    for x in (2.5, 6.5, 10.5):
        d.polygon([(x, 14), (x + 2, 17), (x + 4, 14)], fill=255)
    return m


ICONS = {
    "lumos": (sun, (255, 236, 140)),
    "protego": (shield, (120, 190, 255)),
    "tenebris_flight": (cloud, (70, 62, 86)),
    "incarcerous_chains": (chain, (150, 150, 158)),
    "levicorpus": (arrow_up, (150, 90, 255)),
    "polyjuice": (flask, (120, 140, 60)),
    "gillyweed": (leaf, (70, 170, 90)),
    "wizard_elixir": (bottle, (240, 150, 60)),
    "basilisk_petrification": (eye, (150, 170, 120)),
    "basilisk_gaze": (eye, (230, 200, 60)),
    "petrified": (diamond, (140, 140, 140)),
    "glacius_chill": (snowflake, (150, 225, 255)),
    "glacius_frozen": (cube, (170, 235, 255)),
    "imperius": (crown, (120, 220, 120)),
    "confunded": (spiral, (255, 150, 170)),
    "rooted": (roots, (130, 90, 50)),
    "possessed": (ghost, (210, 200, 235)),
    "possessing": (ghost, (160, 120, 220)),
}

# Marks drawn on top of a shape, in the outline colour: the eye's slit, the crack, the ghost's eyes.
DETAILS = {
    "basilisk_petrification": [((8, 5), (9, 12))],
    "basilisk_gaze": [((8, 5), (9, 12))],
    "petrified": [((6, 5), (6, 6)), ((7, 7), (7, 8)), ((8, 9), (9, 10)), ((10, 11), (10, 11))],
    "possessed": [((6, 6), (6, 8)), ((10, 6), (10, 8))],
    "possessing": [((6, 6), (6, 8)), ((10, 6), (10, 8))],
}


def scale(c, k):
    return tuple(max(0, min(255, round(v * k))) for v in c)


def render(mask, color):
    """The shape in its colour, lit from the upper left, with a dark outline drawn around it (outside)."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = mask.load()
    inside = lambda x, y: 0 <= x < N and 0 <= y < N and px[x, y] > 127
    outline = scale(color, 0.32)
    for y in range(N):
        for x in range(N):
            if inside(x, y):
                k = 1.0
                if not inside(x - 1, y - 1):
                    k = 1.3
                elif not inside(x + 1, y + 1):
                    k = 0.72
                img.putpixel((x, y), scale(color, k) + (255,))
            elif any(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), outline + (255,))
    return img


def main():
    for name, (shape, color) in ICONS.items():
        path = f"{OUT}/{name}.png"
        if os.path.exists(path):
            continue
        img = render(shape(), color)
        dark = scale(color, 0.32) + (255,)
        for (x0, y0), (x1, y1) in DETAILS.get(name, []):
            for x in range(x0, x1 + 1):
                for y in range(y0, y1 + 1):
                    img.putpixel((x, y), dark)
        img.save(path)
        print("wrote", path)


if __name__ == "__main__":
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    main()
