"""Flat front / back / side previews of a 64x64 player skin, optionally dressed in a chest armor layer (layer_1) and a
leggings armor layer (layer_2), at Minecraft pixel scale. Not a 3D render: it composites the faces a player sees from
each side (head + hat, body + jacket, arms, legs, each with its overlay and armor on top), enough to judge a skin or a
kit the way it reads in game. Used by generate_memory_skins.py and generate_quidditch_kits.py.
"""

from PIL import Image


def _face(img, x, y, w, h, flip=False):
    f = img.crop((x, y, x + w, y + h))
    return f.transpose(Image.FLIP_LEFT_RIGHT) if flip else f


def _over(canvas, tile, xy):
    canvas.alpha_composite(tile, xy)


def _view(skin, armor1, armor2, side):
    """One view as a 16 x 32 pixel image (front/back) or 8 x 32 (right side)."""
    skin = skin.convert("RGBA")
    c = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def put(img, rect, at, flip=False, armor=None):
        if img is None:
            return
        _over(c, _face(img, *rect, flip=flip), at)

    if side == "front":
        # head, body, right arm (viewer's left), left arm, right leg, left leg
        put(skin, (8, 8, 8, 8), (4, 0)); put(skin, (40, 8, 8, 8), (4, 0))
        put(skin, (20, 20, 8, 12), (4, 8)); put(skin, (20, 36, 8, 12), (4, 8))
        put(skin, (44, 20, 4, 12), (0, 8)); put(skin, (44, 36, 4, 12), (0, 8))
        put(skin, (36, 52, 4, 12), (12, 8)); put(skin, (52, 52, 4, 12), (12, 8))
        put(skin, (4, 20, 4, 12), (4, 20)); put(skin, (4, 36, 4, 12), (4, 20))
        put(skin, (20, 52, 4, 12), (8, 20)); put(skin, (4, 52, 4, 12), (8, 20))
        put(armor2, (4, 20, 4, 12), (4, 20)); put(armor2, (4, 20, 4, 12), (8, 20), flip=True)
        put(armor1, (20, 20, 8, 12), (4, 8))
        put(armor1, (44, 20, 4, 12), (0, 8)); put(armor1, (44, 20, 4, 12), (12, 8), flip=True)
    elif side == "back":
        put(skin, (24, 8, 8, 8), (4, 0)); put(skin, (56, 8, 8, 8), (4, 0))
        put(skin, (32, 20, 8, 12), (4, 8)); put(skin, (32, 36, 8, 12), (4, 8))
        put(skin, (52, 20, 4, 12), (12, 8)); put(skin, (52, 36, 4, 12), (12, 8))
        put(skin, (44, 52, 4, 12), (0, 8)); put(skin, (60, 52, 4, 12), (0, 8))
        put(skin, (12, 20, 4, 12), (8, 20)); put(skin, (12, 36, 4, 12), (8, 20))
        put(skin, (28, 52, 4, 12), (4, 20)); put(skin, (12, 52, 4, 12), (4, 20))
        put(armor2, (12, 20, 4, 12), (8, 20)); put(armor2, (12, 20, 4, 12), (4, 20), flip=True)
        put(armor1, (32, 20, 8, 12), (4, 8))
        put(armor1, (52, 20, 4, 12), (12, 8)); put(armor1, (52, 20, 4, 12), (0, 8), flip=True)
    else:  # the player's right side
        c = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
        put(skin, (0, 8, 8, 8), (4, 0)); put(skin, (32, 8, 8, 8), (4, 0))
        put(skin, (16, 20, 4, 12), (6, 8)); put(skin, (16, 36, 4, 12), (6, 8))
        put(skin, (0, 20, 4, 12), (6, 20)); put(skin, (0, 36, 4, 12), (6, 20))
        put(armor2, (0, 20, 4, 12), (6, 20))
        put(armor1, (16, 20, 4, 12), (6, 8))
        put(skin, (40, 20, 4, 12), (6, 8)); put(skin, (40, 36, 4, 12), (6, 8))
        put(armor1, (40, 20, 4, 12), (6, 8))
    return c


def preview(skin_path, out_path, armor1=None, armor2=None, scale=12, bg=(96, 110, 128, 255)):
    skin = Image.open(skin_path).convert("RGBA")
    a1 = Image.open(armor1).convert("RGBA") if armor1 else None
    a2 = Image.open(armor2).convert("RGBA") if armor2 else None
    views = [_view(skin, a1, a2, s) for s in ("front", "back", "side")]
    sheet = Image.new("RGBA", (16 * 3 * scale + 4 * scale, 32 * scale + 2 * scale), bg)
    for i, v in enumerate(views):
        v = v.resize((16 * scale, 32 * scale), Image.NEAREST)
        sheet.alpha_composite(v, (scale + i * (16 * scale + scale), scale))
    sheet.save(out_path)
    return out_path


def head_closeup(skin_path, out_path, scale=24, bg=(96, 110, 128, 255)):
    """Front, three-quarter-ish side and back of just the head (head + hat layer), big."""
    skin = Image.open(skin_path).convert("RGBA")
    tiles = []
    for r in ((8, 8), (0, 8), (24, 8)):
        t = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        t.alpha_composite(skin.crop((r[0], r[1], r[0] + 8, r[1] + 8)))
        t.alpha_composite(skin.crop((r[0] + 32, r[1], r[0] + 40, r[1] + 8)))
        tiles.append(t.resize((8 * scale, 8 * scale), Image.NEAREST))
    sheet = Image.new("RGBA", (3 * 8 * scale + 4 * scale, 8 * scale + 2 * scale), bg)
    for i, t in enumerate(tiles):
        sheet.alpha_composite(t, (scale + i * (9 * scale), scale))
    sheet.save(out_path)
    return out_path
