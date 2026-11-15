"""Generate the soft glow and shockwave ring sprites used by the spell cast effects.

Both are white with the shape in the alpha channel, so the renderer tints them with the spell colour.
They are drawn with additive blending, so the RGB is kept white everywhere (no dark fringe).

Run from the repo root:  python tools/gen_spell_glow_textures.py
"""
import math
import os

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "hogwartsmagic", "textures", "misc")
SIZE = 64


def write(name, alpha_fn):
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    c = (SIZE - 1) / 2.0
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - c, y - c) / (SIZE / 2.0)  # 0 at the centre, 1 at the edge
            a = max(0.0, min(1.0, alpha_fn(r)))
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name))


def glow(r):
    # A gaussian that reaches exactly zero at the edge, plus a hot centre.
    edge = math.exp(-4.5)
    soft = (math.exp(-4.5 * r * r) - edge) / (1.0 - edge)
    core = math.exp(-60.0 * r * r)
    return soft * 0.85 + core * 0.15 if r < 1.0 else 0.0


def ring(r):
    # A thin bright band near the edge with a soft inner falloff.
    band = math.exp(-((r - 0.86) / 0.06) ** 2)
    inner = 0.25 * math.exp(-((r - 0.75) / 0.14) ** 2)
    return (band + inner) if r < 1.0 else 0.0


if __name__ == "__main__":
    write("spell_glow.png", glow)
    write("spell_ring.png", ring)
    print("wrote spell_glow.png and spell_ring.png to", OUT)
