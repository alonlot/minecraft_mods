"""Paint the Pensieve's textures.

    python build_scripts/generate_pensieve_textures.py

Block (16x16 unless noted):
  pensieve_stone.png    carved basin stone: two courses of blocks, cool grey with blue-violet shadows
  pensieve_surface.png  the silver surface, 8-frame strip, flowing bands over a seamless loop (+ .mcmeta)
  pensieve_swirl.png    32x32 spiral disc the block entity renderer spins over the surface (translucent)
Item (32x32, 8-frame strip, + .mcmeta): chamber_memory.png (green-tinged silver wisps) and summoning_memory.png
(sickly green and black), patronus_memory.png (silvery blue) and dark_lord_memory.png (black smoke, red glints), both the corked phial of generate_cave_memory_texture.py with a different palette.

Fully deterministic: no randomness, so a re-run produces identical files.
"""

import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate_cave_memory_texture as cave  # noqa: E402

ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "hogwartsmagic", "textures")
BLOCK = os.path.join(ASSETS, "block")
ITEM = os.path.join(ASSETS, "item")


def hexc(h, a=255):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), a)


def mcmeta(path, frametime, interpolate=False):
    with open(path + ".mcmeta", "w", newline="\n") as fh:
        fh.write('{"animation":{"frametime":%d,"interpolate":%s}}\n' % (frametime, "true" if interpolate else "false"))


def hash2(x, y, salt=0):
    n = (x * 374761393 + y * 668265263 + salt * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# ---- block ----------------------------------------------------------------------------------------------------

STONE = [hexc(c) for c in ("#3b3f4f", "#4d5264", "#61667a", "#797f93", "#9399ad")]
MORTAR = hexc("#2a2d3a")


def stone():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        row = y // 8
        off = 0 if row == 0 else 8
        for x in range(16):
            bx = (x + off) % 16
            ly = y % 8
            if ly == 7 or bx % 16 == 15 or (bx % 16 == 7 and False):
                px[x, y] = MORTAR
                continue
            base = 2
            if ly == 0 or bx % 16 == 0:
                base = 3  # lit top-left edges
            if ly == 6 or bx % 16 == 14:
                base = 1  # shaded bottom-right edges
            n = hash2(x, y)
            if n > 0.86:
                base = min(4, base + 1)
            elif n < 0.14:
                base = max(0, base - 1)
            px[x, y] = STONE[base]
    return img


SILVER = [hexc(c) for c in ("#5b6a9c", "#8fa3cf", "#c3d4ee", "#e8f3fc", "#ffffff")]
FRAMES = 8


def surface_frame(f):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    t = 2 * math.pi * f / FRAMES
    for y in range(16):
        for x in range(16):
            u, v = 2 * math.pi * x / 16, 2 * math.pi * y / 16
            val = (math.sin(u + 2 * v + t) + math.sin(2 * u - v - t) + 0.7 * math.sin(u - v * 3 + 2 * t)) / 2.7
            idx = 0 if val < -0.3 else 1 if val < 0.2 else 2 if val < 0.6 else 3 if val < 0.85 else 4
            px[x, y] = SILVER[idx]
    return img


def swirl():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if r > 15.2:
                continue
            ang = math.atan2(dy, dx)
            # three spiral arms tightening toward the middle
            arm = math.sin(3 * ang + r * 0.55)
            a = max(0.0, arm)
            edge = max(0.0, min(1.0, (15.2 - r) / 4.0))
            core = 0.35 if r < 2.5 else 0.0
            alpha = min(1.0, (a * 0.9 + core) * (0.35 + 0.65 * edge))
            if alpha < 0.12:
                continue
            col = SILVER[4] if a > 0.8 else SILVER[3] if a > 0.5 else SILVER[2]
            px[x, y] = col[:3] + (int(alpha * 255),)
    return img


def block_textures():
    os.makedirs(BLOCK, exist_ok=True)
    stone().save(os.path.join(BLOCK, "pensieve_stone.png"))
    strip = Image.new("RGBA", (16, 16 * FRAMES))
    for f in range(FRAMES):
        strip.paste(surface_frame(f), (0, 16 * f))
    p = os.path.join(BLOCK, "pensieve_surface.png")
    strip.save(p)
    mcmeta(p, 2, True)
    swirl().save(os.path.join(BLOCK, "pensieve_swirl.png"))


# ---- items ----------------------------------------------------------------------------------------------------

PALETTES = {
    # Silver with a green cast: the Chamber's cold damp.
    "chamber_memory": dict(
        M=("#1d3a38", "#3d695d", "#6fa38c", "#a9d6bf", "#dcf7e6", "#ffffff"),
        OUT_LIT="#44705f", OUT_MID="#2d4d45", OUT_DARK="#183029",
        G_HI="#eafff3", G_RIM_LIT="#8fc4ad", G_RIM_MID="#4f7c6c", G_RIM_DARK="#2e5148",
        G_TINT=("#c4e8d6", 160), G_SHADE=("#4f7c6c", 130)),
    # Sickly green and black: a memory that has gone bad.
    "summoning_memory": dict(
        M=("#020302", "#0a1007", "#18280d", "#34561a", "#6a9a2c", "#b5e05a"),
        OUT_LIT="#46603a", OUT_MID="#26361f", OUT_DARK="#0b110b",
        G_HI="#c3df93", G_RIM_LIT="#587a4a", G_RIM_MID="#2f432b", G_RIM_DARK="#121a12",
        G_TINT=("#0f160e", 215), G_SHADE=("#040704", 190)),
    # Bright silvery blue: a happy memory, the lake and the stag.
    "patronus_memory": dict(
        M=("#27407a", "#4f7ad0", "#8fb8f2", "#c4e0ff", "#e8f6ff", "#ffffff"),
        OUT_LIT="#6f8fc4", OUT_MID="#46639b", OUT_DARK="#243a66",
        G_HI="#f2fbff", G_RIM_LIT="#a9c8f0", G_RIM_MID="#5f82bd", G_RIM_DARK="#34508a",
        G_TINT=("#d4ecff", 150), G_SHADE=("#5f82bd", 120)),
    # Black smoke with a red glint in it: the Dark Lord himself, his eyes in the dark.
    "dark_lord_memory": dict(
        M=("#020202", "#0b0708", "#1c0d0e", "#4a1214", "#a3221e", "#ff5a44"),
        OUT_LIT="#4a3a3c", OUT_MID="#261c1e", OUT_DARK="#0c0809",
        G_HI="#e2c4c0", G_RIM_LIT="#6e5456", G_RIM_MID="#3a2a2c", G_RIM_DARK="#140e0f",
        G_TINT=("#120b0c", 215), G_SHADE=("#050304", 190)),
}


def item_texture(name, pal):
    cave.M = [hexc(c) for c in pal["M"]]
    cave.OUT_LIT, cave.OUT_MID, cave.OUT_DARK = hexc(pal["OUT_LIT"]), hexc(pal["OUT_MID"]), hexc(pal["OUT_DARK"])
    cave.G_HI = hexc(pal["G_HI"])
    cave.G_RIM_LIT, cave.G_RIM_MID, cave.G_RIM_DARK = (hexc(pal["G_RIM_LIT"]), hexc(pal["G_RIM_MID"]),
                                                       hexc(pal["G_RIM_DARK"]))
    cave.G_TINT = hexc(pal["G_TINT"][0], pal["G_TINT"][1])
    cave.G_SHADE = hexc(pal["G_SHADE"][0], pal["G_SHADE"][1])
    strip = Image.new("RGBA", (32, 32 * cave.FRAMES), (0, 0, 0, 0))
    for f in range(cave.FRAMES):
        strip.paste(cave.frame(f), (0, 32 * f))
    p = os.path.join(ITEM, name + ".png")
    strip.save(p)
    mcmeta(p, 4)


def main():
    block_textures()
    for name, pal in PALETTES.items():
        item_texture(name, pal)
    print("wrote pensieve block textures and", ", ".join(PALETTES))


if __name__ == "__main__":
    main()
