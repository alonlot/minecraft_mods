"""Render arbitrary Minecraft box models (armor nets, entity models) from their UV nets.

Used to check that art painted into a 64x32 armor net or a 32x32 entity net actually
lands on the intended face.
"""
import os, math
from PIL import Image, ImageDraw
from modelrender import raster, SHADE

TEXROOT = r"C:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures"
HERE = os.path.dirname(os.path.abspath(__file__))


def box_faces(ox, oy, oz, w, h, d, u, v):
    """Standard MC box net -> per-face uv rectangles."""
    return {
        "up":    (u + d, v, u + d + w, v + d),
        "down":  (u + d + w, v, u + d + 2 * w, v + d),
        "east":  (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west":  (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def corners(f, t, face):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "up":    [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down":  [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west":  [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east":  [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    }[face]


def render_boxes(boxes, tex, size=220, scale=7.0, yaw=32.0, pitch=22.0):
    """boxes: list of (ox,oy,oz,w,h,d,u,v) in model units, y up."""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    zb = [[-1e9] * size for _ in range(size)]
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def proj(p):
        x, y, z = p
        X = x * cy - z * sy
        Z = x * sy + z * cy
        Y = y * cp - Z * sp
        return (size / 2 + X * scale, size / 2 - Y * scale), (Z * cp + y * sp)

    for b in boxes:
        ox, oy, oz, w, h, d, u, v = b
        fs = box_faces(ox, oy, oz, w, h, d, u, v)
        f = (ox, oy, oz)
        t = (ox + w, oy + h, oz + d)
        for face, uv in fs.items():
            cs = corners(f, t, face)
            pr = [proj(p) for p in cs]
            pts = [a for a, _ in pr]
            ds = [b2 for _, b2 in pr]
            u0, v0, u1, v1 = uv
            uvxy = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            sx = 16.0 / tex.width
            sy2 = 16.0 / tex.height
            uvs = [(uvxy[i][0] * sx, uvxy[i][1] * sy2, ds[i]) for i in range(4)]
            raster(out, zb, pts, uvs, tex, SHADE[face], size, size)
    return out


ARMOR = [
    (-4, 0, -2, 8, 12, 4, 16, 16),     # body
    (-8, 0, -2, 4, 12, 4, 40, 16),     # right arm
    (4, 0, -2, 4, 12, 4, 40, 16),      # left arm (mirrored in game)
]
SNITCH = [
    (-1.5, -1.5, -1.5, 3, 3, 3, 0, 0),
    (1.5, -0.5, -1.5, 6, 1, 3, 0, 8),
    (-7.5, -0.5, -1.5, 6, 1, 3, 0, 12),
]

JOBS = [
    ("gryffindor_robes_layer_1", "models/armor", "models_armor", ARMOR, 6.5),
    ("hufflepuff_robes_layer_1", "models/armor", "models_armor", ARMOR, 6.5),
    ("ravenclaw_robes_layer_1", "models/armor", "models_armor", ARMOR, 6.5),
    ("slytherin_robes_layer_1", "models/armor", "models_armor", ARMOR, 6.5),
    ("golden_snitch", "entity", "entity", SNITCH, 11.0),
]

S = 200
cells = []
for name, origdir, propdir, boxes, sc in JOBS:
    o = Image.open(os.path.join(TEXROOT, *origdir.split("/"), name + ".png")).convert("RGBA")
    p = Image.open(os.path.join(HERE, "proposed", propdir, name + ".png")).convert("RGBA")
    cells.append((name, render_boxes(boxes, o, S, sc), render_boxes(boxes, p, S, sc)))

COLS = 3
rows = (len(cells) + COLS - 1) // COLS
CW, LAB = S * 2 + 12, 18
im = Image.new("RGB", (COLS * (CW + 10), rows * (S + LAB + 12)), (28, 28, 32))
d = ImageDraw.Draw(im)
for i, (name, a, b) in enumerate(cells):
    ox = (i % COLS) * (CW + 10) + 5
    oy = (i // COLS) * (S + LAB + 12) + 4
    im.paste(Image.new("RGB", (CW, S), (58, 58, 66)), (ox, oy))
    im.paste(a, (ox, oy), a)
    im.paste(b, (ox + S + 12, oy), b)
    d.line([(ox + S + 6, oy), (ox + S + 6, oy + S)], fill=(90, 90, 100))
    d.text((ox + 2, oy + S + 3), f"{name}   [ before | after ]", fill=(230, 230, 235))
out = os.path.join(HERE, "sheets", "box_preview.png")
im.save(out)
print(out, im.size)
