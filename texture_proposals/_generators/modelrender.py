"""Render a Minecraft block model JSON isometrically, sampling the real texture UVs.

This exists so UV-atlas block textures can be judged as they actually appear in game
rather than as a flat sheet -- and so mis-placed art lands visibly on the wrong face.
"""
import json, os, math
from PIL import Image

MODELS = r"C:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\models\block"
TEXROOT = r"C:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\resources\assets\hogwartsmagic\textures"

# face brightness, matching vanilla's directional shading
SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}

# corners of each face in (x, y, z), ordered to match MC's uv winding
# uv = [u0, v0, u1, v1]; corner order is (u0,v0) (u1,v0) (u1,v1) (u0,v1)
def face_corners(f, t, face):
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


def project(p, scale, ox, oy):
    """Standard MC inventory-ish isometric."""
    x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
    sx = (x - z) * math.cos(math.radians(30))
    sy = (x + z) * math.sin(math.radians(30)) - y
    return (ox + sx * scale, oy + sy * scale)


def depth(p):
    return p[0] + p[1] + p[2]


def raster(out, zbuf, pts2, uvs, tex, shade, W, H):
    """Rasterise a quad as two triangles, nearest-sampling the texture."""
    tw, th = tex.size
    px = tex.load()
    tris = [(0, 1, 2), (0, 2, 3)]
    for a, b, c in tris:
        p0, p1, p2 = pts2[a], pts2[b], pts2[c]
        u0, u1, u2 = uvs[a], uvs[b], uvs[c]
        minx = max(0, int(min(p0[0], p1[0], p2[0])))
        maxx = min(W - 1, int(max(p0[0], p1[0], p2[0])) + 1)
        miny = max(0, int(min(p0[1], p1[1], p2[1])))
        maxy = min(H - 1, int(max(p0[1], p1[1], p2[1])) + 1)
        d = ((p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1]))
        if abs(d) < 1e-9:
            continue
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                cx, cy = x + .5, y + .5
                w0 = ((p1[1] - p2[1]) * (cx - p2[0]) + (p2[0] - p1[0]) * (cy - p2[1])) / d
                w1 = ((p2[1] - p0[1]) * (cx - p2[0]) + (p0[0] - p2[0]) * (cy - p2[1])) / d
                w2 = 1 - w0 - w1
                if w0 < -0.002 or w1 < -0.002 or w2 < -0.002:
                    continue
                u = w0 * u0[0] + w1 * u1[0] + w2 * u2[0]
                v = w0 * u0[1] + w1 * u1[1] + w2 * u2[1]
                tx = min(tw - 1, max(0, int(u / 16 * tw)))
                ty = min(th - 1, max(0, int(v / 16 * th)))
                col = px[tx, ty]
                if len(col) == 4 and col[3] < 24:
                    continue
                z = w0 * uvs[a][2] + w1 * uvs[b][2] + w2 * uvs[c][2]
                if z <= zbuf[y][x]:
                    continue
                zbuf[y][x] = z
                out.putpixel((x, y), (int(col[0] * shade), int(col[1] * shade),
                                      int(col[2] * shade), 255))


def render(model_name, tex_override=None, size=192, scale=6.0):
    """tex_override maps a texture key (e.g. 'texture' or 'all') -> PIL image."""
    m = json.load(open(os.path.join(MODELS, model_name + ".json"), encoding="utf-8"))
    parent = m.get("parent", "")
    elements = m.get("elements")
    if elements is None:
        # resolve the handful of vanilla parents we need
        if parent.endswith("cube_all") or parent.endswith("leaves") or parent.endswith("cube_bottom_top"):
            elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                f: {"uv": [0, 0, 16, 16], "texture": "#all"} for f in
                ("up", "down", "north", "south", "east", "west")}}]
        elif parent.endswith("cross"):
            elements = "CROSS"
        else:
            raise SystemExit("unhandled parent " + parent)

    textures = {}
    for k, v in m.get("textures", {}).items():
        if tex_override and k in tex_override:
            textures["#" + k] = tex_override[k]
        elif isinstance(v, str) and v.startswith("hogwartsmagic:"):
            p = os.path.join(TEXROOT, *v.split(":")[1].split("/")) + ".png"
            if os.path.exists(p):
                textures["#" + k] = Image.open(p).convert("RGBA")
    if tex_override:
        for k, v in tex_override.items():
            textures["#" + k] = v

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    zbuf = [[-1e9] * size for _ in range(size)]
    ox, oy = size / 2, size / 2 + size * 0.06

    if elements == "CROSS":
        # two crossed quads
        tex = textures.get("#cross") or textures.get("#all")
        for (a, b) in (((0.6, 0, 0.6), (15.4, 16, 15.4)), ((0.6, 0, 15.4), (15.4, 16, 0.6))):
            pts = [(a[0], b[1], a[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], a[2])]
            pts2 = [project(p, scale, ox, oy) for p in pts]
            uvs = [(0, 0, depth(pts[0])), (16, 0, depth(pts[1])),
                   (16, 16, depth(pts[2])), (0, 16, depth(pts[3]))]
            raster(out, zbuf, pts2, uvs, tex, 1.0, size, size)
        return out

    order = sorted(elements, key=lambda e: sum(e["from"]) + sum(e["to"]))
    for el in order:
        f, t = el["from"], el["to"]
        for face, fd in el.get("faces", {}).items():
            if face == "down":
                continue
            tex = textures.get(fd.get("texture", ""))
            if tex is None:
                tex = textures.get("#all") or next(iter(textures.values()), None)
            if tex is None:
                continue
            u0, v0, u1, v1 = fd.get("uv", [0, 0, 16, 16])
            corners = face_corners(f, t, face)
            pts2 = [project(p, scale, ox, oy) for p in corners]
            uvxy = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            uvs = [(uvxy[i][0], uvxy[i][1], depth(corners[i])) for i in range(4)]
            raster(out, zbuf, pts2, uvs, tex, SHADE[face], size, size)
    return out
