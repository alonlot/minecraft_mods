"""Measure every Diagon Alley palette against the 40-luma rule.

    python build_scripts/audit_palettes.py

Mixing blocks inside one surface is how a wall stops looking like a single flat material.  Mixing
blocks that are far apart in *value* is how a wall starts looking like a chessboard, and the
difference between the two is not visible in the source -- `brown_terracotta` and `white_terracotta`
are one word apart and 128 luma apart.

So this opens the actual textures, averages them, and reports the spread.  Anything over ~40 is
reported as a failure; 40 is roughly where a two-block blend stops reading as grain and starts
reading as pattern.

Textures come from the mod first and the vanilla jar second, same order the game resolves them.
"""

import io
import json
import os
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import diagon_kit as K                                                          # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
JAR = os.path.expanduser('~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.21/'
                         'client-extra.jar')
MOD = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic')

LIMIT = 40.0

# Glazing is exempt, and only glazing.  The rule is about large flat surfaces: a wall built from two
# blocks 100 luma apart reads as a chessboard.  A window is a handful of panes inside a timber frame
# with mullions between them, and panes that differ from one another are the whole reason it reads
# as glass with a lit room behind it.  Exempt means "reported, not failed" -- the numbers still print
# so a genuinely wild glazing palette is still visible.
EXEMPT = {'GLASS_LIT', 'GLASS_PLAIN', 'GLASS_DARK'}

_zip = zipfile.ZipFile(JAR)
_names = set(_zip.namelist())
_cache = {}


def _json(ns, kind, path):
    if ns != 'minecraft':
        local = os.path.join(MOD, kind, path.replace('/', os.sep) + '.json')
        if os.path.exists(local):
            with open(local, encoding='utf-8') as fh:
                return json.load(fh)
    rel = 'assets/%s/%s/%s.json' % (ns, kind, path)
    return json.loads(_zip.read(rel)) if rel in _names else None


def _png(ns, path):
    if ns != 'minecraft':
        local = os.path.join(MOD, 'textures', path.replace('/', os.sep) + '.png')
        if os.path.exists(local):
            with open(local, 'rb') as fh:
                return fh.read()
    rel = 'assets/%s/textures/%s.png' % (ns, path)
    return _zip.read(rel) if rel in _names else None


def _split(res):
    return res.split(':', 1) if ':' in res else ('minecraft', res)


def _first_texture(block):
    """Walk blockstate -> model -> parent chain and return the first real texture it names."""
    ns, name = _split(block)
    st = _json(ns, 'blockstates', name)
    if not st:
        return None
    model = None
    if 'variants' in st:
        v = next(iter(st['variants'].values()))
        model = (v[0] if isinstance(v, list) else v).get('model')
    elif 'multipart' in st:
        v = st['multipart'][0]['apply']
        model = (v[0] if isinstance(v, list) else v).get('model')
    seen, textures = set(), {}
    while model and model not in seen:
        seen.add(model)
        mns, mpath = _split(model)
        mpath = mpath.replace('block/', '', 1) if mpath.startswith('block/') else mpath
        mj = _json(mns, 'models', 'block/' + mpath) or _json(mns, 'models', mpath)
        if not mj:
            break
        for k, v in (mj.get('textures') or {}).items():
            textures.setdefault(k, v)
        model = mj.get('parent')
    for key in ('all', 'texture', 'side', 'end', 'top', 'wall', 'bottom', 'cross', 'pane', 'front'):
        if key in textures and not textures[key].startswith('#'):
            return textures[key]
    for v in textures.values():
        if not v.startswith('#'):
            return v
    return None


def mean_rgb(block):
    """Alpha-weighted mean colour of a block's main texture, or None."""
    if block in _cache:
        return _cache[block]
    tex = _first_texture(block)
    val = None
    if tex:
        tns, tpath = _split(tex)
        raw = _png(tns, tpath)
        if raw:
            im = Image.open(io.BytesIO(raw)).convert('RGBA')
            im = im.crop((0, 0, im.width, min(im.width, im.height)))   # first frame if animated
            acc = [0.0, 0.0, 0.0]
            wsum = 0.0
            for r, g, b, a in im.getdata():
                if a < 8:
                    continue
                w = a / 255.0
                wsum += w
                acc[0] += r * w
                acc[1] += g * w
                acc[2] += b * w
            if wsum:
                val = (acc[0] / wsum, acc[1] / wsum, acc[2] / wsum)
    _cache[block] = val
    return val


def luma(block):
    """Mean perceptual luma of a block's main texture, 0-255."""
    rgb = mean_rgb(block)
    return None if rgb is None else 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def hue(block):
    """Mean hue in degrees, or None for a near-neutral colour where hue is meaningless."""
    rgb = mean_rgb(block)
    if rgb is None:
        return None
    r, g, bl = rgb
    mx, mn = max(r, g, bl), min(r, g, bl)
    if mx - mn < 12:                 # grey: no meaningful hue
        return None
    d = mx - mn
    if mx == r:
        h = 60 * (((g - bl) / d) % 6)
    elif mx == g:
        h = 60 * ((bl - r) / d + 2)
    else:
        h = 60 * ((r - g) / d + 4)
    return h % 360


def _hue_gap(blocks):
    """Widest hue separation among a set of blocks, ignoring neutrals.  Returns 0 if fewer than
    two have a hue at all."""
    hs = [h for h in (hue(b) for b in blocks) if h is not None]
    if len(hs) < 2:
        return 0.0
    worst = 0.0
    for i in range(len(hs)):
        for j in range(i + 1, len(hs)):
            d = abs(hs[i] - hs[j]) % 360
            worst = max(worst, min(d, 360 - d))
    return worst


def check(label, palette):
    rows = []
    for block, weight in palette:
        rows.append((block, weight, luma(block)))
    known = [r for r in rows if r[2] is not None]
    unknown = [r for r in rows if r[2] is None]
    if not known:
        return ['%-12s no textures resolved' % label]

    lo = min(known, key=lambda r: r[2])
    hi = max(known, key=lambda r: r[2])
    spread = hi[2] - lo[2]

    # Weight matters: a 1-in-20 block that is far off is a speckle, not a chessboard.  So report
    # the spread among members carrying real weight separately from the raw spread.
    total = sum(r[1] for r in known)
    heavy = [r for r in known if r[1] / total >= 0.15]
    hspread = (max(heavy, key=lambda r: r[2])[2] - min(heavy, key=lambda r: r[2])[2]) \
        if len(heavy) > 1 else 0.0

    # A same-hue pair gets more room than a cross-hue one.  Yellow and orange terracotta are 40
    # luma apart, but they are the same colour, so a wall mixing them reads as an ochre wall that
    # has weathered unevenly.  Forty luma between a brown and a white reads as a chessboard.  So the
    # allowance widens when everything carrying weight sits inside a narrow hue band.
    gap = _hue_gap([r[0] for r in heavy]) if len(heavy) > 1 else 0.0
    allowance = LIMIT * (1.4 if gap <= 35 else 1.0)

    if label in EXEMPT:
        flag = 'glaz'
    else:
        flag = 'FAIL' if hspread > allowance else ('warn' if spread > LIMIT * 1.6 else 'ok  ')
    out = ['%s %-12s spread %5.1f   weighted %5.1f  hue %3.0f   %s .. %s'
           % (flag, label, spread, hspread, gap,
              '%s %.0f' % (lo[0].split(':')[-1], lo[2]),
              '%s %.0f' % (hi[0].split(':')[-1], hi[2]))]
    if flag not in ('ok  ', 'glaz'):
        for block, weight, l in sorted(known, key=lambda r: r[2]):
            out.append('        %-46s w%-3d luma %5.1f' % (block, weight, l))
    for block, weight, _ in unknown:
        out.append('        %-46s w%-3d luma   ?  (texture not found)' % (block, weight))
    return out


def main():
    groups = [('COBBLE', K.COBBLE), ('KERB', K.KERB), ('GUTTER', K.GUTTER),
              ('PLINTH', K.PLINTH), ('BRICK', K.BRICK),
              ('MARBLE', K.MARBLE), ('MARBLE_TRIM', K.MARBLE_TRIM),
              ('FRAME_DARK', K.FRAME_DARK), ('FRAME_MID', K.FRAME_MID),
              ('FRAME_GREY', K.FRAME_GREY),
              ('GLASS_LIT', K.GLASS_LIT), ('GLASS_PLAIN', K.GLASS_PLAIN),
              ('GLASS_DARK', K.GLASS_DARK)]
    groups += [('paint:' + k, v) for k, v in sorted(K.PAINTS.items())]

    failures = 0
    for label, pal in groups:
        for line in check(label, pal):
            print(line)
            if line.startswith('FAIL'):
                failures += 1

    print()
    print('roof materials (stairs / solid / slab should be the same stone):')
    for name, (st, so, sl) in sorted(K.ROOFS.items()):
        vals = [luma(b) for b in (st, so, sl)]
        vals = [v for v in vals if v is not None]
        if vals:
            print('     %-8s luma %5.1f  spread %4.1f' % (name, sum(vals) / len(vals),
                                                          max(vals) - min(vals)))
    print()
    print('%d palettes over the %d-luma weighted limit' % (failures, LIMIT))
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
