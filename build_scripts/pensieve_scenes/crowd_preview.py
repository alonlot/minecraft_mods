"""Draw the crowd mesh without the game (a look at the figures' shapes, poses and colours, not the in-game light).

    set CROWD_PREVIEW=<folder> and run  ./gradlew test --tests com.hogwartsmagic.pensieve.CrowdPreviewDump
    python -I build_scripts/pensieve_scenes/crowd_preview.py <folder>

CrowdPreviewDump writes crowd_lod0/1/2.txt (a small four-tier stand in four moments, side by side 15 blocks apart:
idle, goal, gasp, erupt); this draws each moment at each level of detail from the pitch, backface-culled and painted far
to near, into <folder>/crowd_<moment>_lod<n>.png, and a contact sheet crowd_sheet.png.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

MOMENTS = ['idle', 'goal', 'gasp', 'erupt']
W, H, FOV = 720, 420, 62.0


def quads(path):
    rows = [ln.split() for ln in open(path, encoding='utf-8') if ln.strip()]
    out = []
    for i in range(0, len(rows) - 3, 4):
        v = [tuple(float(x) for x in r[:3]) for r in rows[i:i + 4]]
        out.append((v, int(rows[i][3])))
    return out


def render(qs, cam, look, path):
    fx, fy, fz = look[0] - cam[0], look[1] - cam[1], look[2] - cam[2]
    n = math.sqrt(fx * fx + fy * fy + fz * fz)
    f = (fx / n, fy / n, fz / n)
    r = (f[2], 0.0, -f[0])                                  # right = f x up, flattened
    rn = math.sqrt(r[0] ** 2 + r[2] ** 2)
    r = (r[0] / rn, 0.0, r[2] / rn)
    u = (f[1] * r[2] - f[2] * r[1], f[2] * r[0] - f[0] * r[2], f[0] * r[1] - f[1] * r[0])
    u = (-u[0], -u[1], -u[2]) if u[1] < 0 else u
    k = (W / 2) / math.tan(math.radians(FOV / 2))
    img = Image.new('RGB', (W, H), (92, 104, 116))
    d = ImageDraw.Draw(img)
    d.rectangle([0, H * 0.62, W, H], fill=(74, 98, 60))
    items = []
    for v, c in qs:
        pts, depth, ok = [], 0.0, True
        for p in v:
            q = (p[0] - cam[0], p[1] - cam[1], p[2] - cam[2])
            z = q[0] * f[0] + q[1] * f[1] + q[2] * f[2]
            if z < 0.1:
                ok = False
                break
            x = q[0] * r[0] + q[2] * r[2]
            y = q[0] * u[0] + q[1] * u[1] + q[2] * u[2]
            pts.append((W / 2 - k * x / z, H / 2 - k * y / z))
            depth += z
        if not ok:
            continue
        a = sum(pts[i][0] * pts[(i + 1) % 4][1] - pts[(i + 1) % 4][0] * pts[i][1] for i in range(4))
        if a >= 0:                                          # counter-clockwise on screen (y down): seen from outside
            continue
        items.append((depth / 4, pts, ((c >> 16) & 255, (c >> 8) & 255, c & 255)))
    items.sort(key=lambda it: -it[0])
    for _, pts, col in items:
        d.polygon(pts, fill=col)
    img.save(path)
    return img


def main():
    folder = sys.argv[1]
    sheet = Image.new('RGB', (W * 4, H * 3), (0, 0, 0))
    for lod in range(3):
        qs = quads(os.path.join(folder, 'crowd_lod%d.txt' % lod))
        for m, name in enumerate(MOMENTS):
            sx = (m - 1.5) * 15.0
            dist = [2.0, 9.0, 16.0][lod]
            img = render(qs, (sx + 1.5, 2.6, 10.0 - dist - 3.0), (sx, 2.4, 11.5), os.path.join(
                folder, 'crowd_%s_lod%d.png' % (name, lod)))
            sheet.paste(img, (W * m, H * lod))
    sheet.save(os.path.join(folder, 'crowd_sheet.png'))
    print('wrote', os.path.join(folder, 'crowd_sheet.png'))


if __name__ == '__main__':
    main()
