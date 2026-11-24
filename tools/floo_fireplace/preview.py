"""Quick look at the generated Floo fireplace: assembles the hearth + part models into the full
3x3 frame and draws it with each face's average texture colour, plus a player-sized box standing
on the hearth. Run after gen_models.py:  python tools/floo_fireplace/preview.py [out.png]
"""
import json
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets")
MODELS = os.path.join(ROOT, "hogwartsmagic", "models", "block")
VANILLA = {"minecraft:block/dark_oak_planks": (66, 43, 20), "minecraft:block/dark_oak_log": (60, 46, 26),
           "minecraft:block/dark_oak_log_top": (85, 66, 40)}
_cache = {}


def colour(tex_ref, textures, uv):
    name = textures[tex_ref.lstrip("#")]
    while name.startswith("#"):
        name = textures[name.lstrip("#")]
    if name in VANILLA:
        return tuple(c / 255 for c in VANILLA[name])
    ns, path = name.split(":")
    if name not in _cache:
        _cache[name] = Image.open(os.path.join(ROOT, ns, "textures", path + ".png")).convert("RGBA")
    im = _cache[name]
    w = im.width
    frame = im.crop((0, 0, w, w))
    u1, v1, u2, v2 = uv if uv else (0, 0, 16, 16)
    box = (int(min(u1, u2) * w / 16), int(min(v1, v2) * w / 16), max(int(max(u1, u2) * w / 16), int(min(u1, u2) * w / 16) + 1),
           max(int(max(v1, v2) * w / 16), int(min(v1, v2) * w / 16) + 1))
    px = [p for p in frame.crop(box).getdata() if p[3] > 0] or [(255, 0, 255, 255)]
    return tuple(sum(p[i] for p in px) / len(px) / 255 for i in range(3))


def quads(e):
    (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
    return {
        "north": [(x1, y1, z1), (x2, y1, z1), (x2, y2, z1), (x1, y2, z1)],
        "south": [(x1, y1, z2), (x2, y1, z2), (x2, y2, z2), (x1, y2, z2)],
        "west": [(x1, y1, z1), (x1, y1, z2), (x1, y2, z2), (x1, y2, z1)],
        "east": [(x2, y1, z1), (x2, y1, z2), (x2, y2, z2), (x2, y2, z1)],
        "down": [(x1, y1, z1), (x2, y1, z1), (x2, y1, z2), (x1, y1, z2)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
    }


def gather(model_name, ox, oy, polys, cols):
    m = json.load(open(os.path.join(MODELS, model_name + ".json")))
    for e in m["elements"]:
        q = quads(e)
        for f, face in e["faces"].items():
            pts = [(x + ox, z, y + oy) for x, y, z in q[f]]  # plot axes: x, depth, up
            polys.append(pts)
            cols.append(colour(face["texture"], m["textures"], face.get("uv")))


def main(out, hearth="floo_fireplace"):
    polys, cols = [], []
    for c in range(3):
        for r in range(3):
            name = hearth if (c, r) == (1, 0) else f"floo_fireplace_part_{c}_{r}"
            gather(name, c * 16, r * 16, polys, cols)
    # a player (0.6 x 1.8 blocks) standing in the middle of the hearth floor
    px, pz, py = 24 - 4.8, 8 - 4.8, 2
    player = {"from": [px, py, pz], "to": [px + 9.6, py + 28.8, pz + 9.6]}
    fig = plt.figure(figsize=(12, 6))
    for i, (elev, azim) in enumerate(((15, -70), (10, -20))):
        ax = fig.add_subplot(1, 2, i + 1, projection="3d")
        ax.add_collection3d(Poly3DCollection(polys, facecolors=cols, edgecolors=(0, 0, 0, 0.15), linewidths=0.2))
        pq = [[(x, z, y) for x, y, z in v] for v in quads(player).values()]
        ax.add_collection3d(Poly3DCollection(pq, facecolors=(0.2, 0.4, 1.0, 0.35), edgecolors=(0, 0, 0.6, 0.6)))
        ax.set_xlim(-2, 50); ax.set_ylim(-18, 34); ax.set_zlim(0, 52)
        ax.set_box_aspect((1, 1, 1))
        ax.view_init(elev=elev, azim=azim)
        ax.invert_yaxis()
        ax.set_axis_off()
    plt.tight_layout()
    plt.savefig(out, dpi=110)
    print(out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "floo_preview.png", sys.argv[2] if len(sys.argv) > 2 else "floo_fireplace")
