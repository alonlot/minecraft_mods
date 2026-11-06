"""Paint entity textures by where each texel lands on the model.

    from paint import texels, preview
    for t in texels('wizard'):          # model name as dumped by run.sh into out/quads.json
        t.u, t.v                        # texel in the model's declared texture size
        t.part                          # ModelPart path, e.g. '/head/bone'
        t.cube                          # cube index inside that part
        t.face                          # 'top' 'bottom' 'front' 'back' 'left' 'right'
        t.n                             # (nx, ny, nz) in 0..1 across the cube, unposed:
                                        #   nx 0 = the model's right (-X) .. 1 = left
                                        #   ny 0 = top .. 1 = bottom,  nz 0 = front (-Z) .. 1 = back
        t.local                         # unposed point in model pixels (part space)
        t.size                          # cube size in pixels (dx, dy, dz)
        t.world                         # rest-pose point in blocks (y up, entity facing +Z)

Each texel is reported once, for the first face that samples it (shared regions such as mirrored
limbs then paint once, the way the game samples them).
"""
import json
import math
import os

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
QUADS = os.path.join(HERE, 'out', 'quads.json')
_cache = {}


class Texel:
    __slots__ = ('u', 'v', 'part', 'cube', 'face', 'n', 'local', 'size', 'world')


def _model(name):
    if not _cache:
        _cache.update(json.load(open(QUADS)))
    return _cache[name]


def _face_name(normal):
    k = int(np.argmax(np.abs(normal)))
    s = normal[k] > 0
    return [('left' if s else 'right'), ('bottom' if s else 'top'), ('back' if s else 'front')][k]


def texels(name):
    m = _model(name)
    tw, th = m['tw'], m['th']
    bounds = {}
    for q in m['quads']:
        L = np.array(q['l']).reshape(4, 3)
        key = (q['p'], q['c'])
        lo, hi = bounds.get(key, (L.min(0), L.max(0)))
        bounds[key] = (np.minimum(lo, L.min(0)), np.maximum(hi, L.max(0)))
    seen = set()
    for q in m['quads']:
        V = np.array(q['v']).reshape(4, 5)
        W, UV = V[:, :3], V[:, 3:] * np.array([tw, th])
        L = np.array(q['l']).reshape(4, 3)
        # find a corner A, a neighbour B along u and a neighbour C along v
        A = 0
        B = next((j for j in range(4) if j != A and abs(UV[j, 1] - UV[A, 1]) < 1e-4 and abs(UV[j, 0] - UV[A, 0]) > 1e-4), None)
        C = next((j for j in range(4) if j != A and abs(UV[j, 0] - UV[A, 0]) < 1e-4 and abs(UV[j, 1] - UV[A, 1]) > 1e-4), None)
        if B is None or C is None:
            continue
        du, dv = UV[B, 0] - UV[A, 0], UV[C, 1] - UV[A, 1]
        nrm = np.cross(L[1] - L[0], L[2] - L[0])
        if np.linalg.norm(nrm) < 1e-9:
            continue
        # outward normal: from the cube centre toward the face
        lo, hi = bounds[(q['p'], q['c'])]
        centre = (lo + hi) / 2
        if nrm @ (L.mean(0) - centre) < 0:
            nrm = -nrm
        face = _face_name(nrm)
        size = hi - lo
        u0, u1 = sorted((UV[A, 0], UV[B, 0]))
        v0, v1 = sorted((UV[A, 1], UV[C, 1]))
        for v in range(int(round(v0)), int(round(v1))):
            for u in range(int(round(u0)), int(round(u1))):
                if (u, v) in seen or not (0 <= u < tw and 0 <= v < th):
                    continue
                seen.add((u, v))
                s = (u + 0.5 - UV[A, 0]) / du
                t = (v + 0.5 - UV[A, 1]) / dv
                x = Texel()
                x.u, x.v, x.part, x.cube, x.face = u, v, q['p'], q['c'], face
                x.local = L[A] + s * (L[B] - L[A]) + t * (L[C] - L[A])
                x.world = W[A] + s * (W[B] - W[A]) + t * (W[C] - W[A])
                x.size = size
                x.n = tuple(float((x.local[k] - lo[k]) / size[k]) if size[k] > 1e-6 else 0.5 for k in range(3))
                yield x


def preview(name, texture, out, extra=None):
    """Five-view sheet of the model wearing `texture` (a PIL image), with optional extra images
    (e.g. the old texture's sheet) stacked below."""
    import sys
    sys.path.insert(0, HERE)
    from audit import quads_of, render, TILE
    from PIL import Image
    quads = quads_of(_model(name))
    T = np.asarray(texture.convert('RGBA'), dtype=float) / 255
    views = [(0, 10), (40, 25), (90, 5), (180, 15), (330, 30)]
    tiles = [render(quads, T, az, el) for az, el in views]
    sheet = Image.new('RGB', (TILE * 5, TILE), (20, 20, 20))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * TILE, 0))
    sheet.save(out)
    return sheet
