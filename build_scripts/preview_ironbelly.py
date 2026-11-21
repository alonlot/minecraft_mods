"""Renders the Ironbelly model offline, textured, from the mesh the geometry test writes.

    ./gradlew test --tests '*IronbellyModelGeometryTest*'
    python build_scripts/preview_ironbelly.py out.png

Orthographic, z-buffered, alpha-cutout like the game's entity render type, flat-lit by face
direction. It answers "does the sculpt read and does the hide sit on it", which no audit can.
"""
import json
import math
import sys

from PIL import Image

TEX = 'src/main/resources/assets/hogwartsmagic/textures/entity/ukrainian_ironbelly.png'


def render(mesh, tex, yaw, pitch, size, scale):
    tw, th = tex.size
    tpx = tex.load()
    img = Image.new('RGB', (size, size), (24, 26, 30))
    px = img.load()
    zbuf = [[1e9] * size for _ in range(size)]
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    light = (0.35, -0.85, -0.4)

    def tf(v):
        # Model space: x left, y down, z toward the tail. Turn it into screen space.
        x, y, z = -v[0], -v[1], v[2]
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (size / 2 + x * scale, size * 0.62 - y * scale, z)

    for quad in mesh:
        pts = [tf(v) for v in quad]
        a, b, c = quad[0][:3], quad[1][:3], quad[2][:3]
        n = (
            (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]),
            (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]),
            (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]),
        )
        ln = math.sqrt(sum(k * k for k in n)) or 1.0
        lit = 0.55 + 0.45 * abs(sum(n[i] / ln * light[i] for i in range(3)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            p = [pts[i] for i in tri]
            uv = [quad[i][3:5] for i in tri]
            x0 = max(0, int(min(q[0] for q in p)))
            x1 = min(size - 1, int(max(q[0] for q in p)) + 1)
            y0 = max(0, int(min(q[1] for q in p)))
            y1 = min(size - 1, int(max(q[1] for q in p)) + 1)
            d = (p[1][1] - p[2][1]) * (p[0][0] - p[2][0]) + (p[2][0] - p[1][0]) * (p[0][1] - p[2][1])
            if abs(d) < 1e-9:
                continue
            for yy in range(y0, y1 + 1):
                for xx in range(x0, x1 + 1):
                    w0 = ((p[1][1] - p[2][1]) * (xx - p[2][0]) + (p[2][0] - p[1][0]) * (yy - p[2][1])) / d
                    w1 = ((p[2][1] - p[0][1]) * (xx - p[2][0]) + (p[0][0] - p[2][0]) * (yy - p[2][1])) / d
                    w2 = 1 - w0 - w1
                    if w0 < -1e-4 or w1 < -1e-4 or w2 < -1e-4:
                        continue
                    z = w0 * p[0][2] + w1 * p[1][2] + w2 * p[2][2]
                    if z >= zbuf[yy][xx]:
                        continue
                    u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0]
                    v = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1]
                    tx = min(tw - 1, max(0, int(u * tw)))
                    ty = min(th - 1, max(0, int(v * th)))
                    r, g, bl, al = tpx[tx, ty]
                    if al < 128:
                        continue
                    zbuf[yy][xx] = z
                    px[xx, yy] = (int(r * lit), int(g * lit), int(bl * lit))
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else 'ironbelly_preview.png'
    mesh = json.load(open('build/ironbelly_mesh.json'))
    tex = Image.open(TEX).convert('RGBA')
    views = [(0.9, 0.25), (math.pi / 2, 0.0), (2.6, 0.45), (0.0, 1.2)]
    tiles = [render(mesh, tex, y, p, 420, 70) for y, p in views]
    sheet = Image.new('RGB', (840, 840))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 2) * 420, (i // 2) * 420))
    sheet.save(out)
    print('wrote', out)


if __name__ == '__main__':
    main()
