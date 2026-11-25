"""Placeholder art for the Living Famous Wizard Cards, so the game runs before the real art exists.

Writes, under src/main/resources/assets/hogwartsmagic/:
  textures/card/frame_common.png, frame_rare.png, frame_legendary.png, back.png   (320x448, window punched out)
  textures/card/box.png                                                           (192x192 RGBA, top 2/5 = lid)
  textures/card/<key>/base.png empty.png figure.png blink.png look_l.png look_r.png  (256x384)
  textures/card/<key>/clips/exit/0000.png ...                                     (legendaries only, 36 frames)
  textures/card/_placeholder/...                                                  (the stand-in for a card with no art)
  cards/<key>.json and cards/_placeholder.json

The real art (main session, task D1) overwrites these files at the same paths. Run:  python tools/living_cards/make_placeholders.py
"""
import colorsys
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
TEX = os.path.join(ASSETS, "textures", "card")
CARDS = os.path.join(ASSETS, "cards")

PW, PH = 256, 384            # portrait textures
CW, CH = 320, 448            # card frames
WIN = (32, 22, 32 + PW, 22 + PH)   # the portrait window inside the frame: x0, y0, x1, y1
SS = 2                       # supersampling for smooth edges

# key, rarity (0 common, 1 rare, 2 legendary) in WizardCards order
KEYS = [
    ("merlin", 2), ("albus_dumbledore", 2),
    ("godric_gryffindor", 1), ("helga_hufflepuff", 1), ("rowena_ravenclaw", 1), ("salazar_slytherin", 1),
    ("nicolas_flamel", 1), ("morgana", 1),
    ("circe", 0), ("agrippa", 0), ("ptolemy", 0), ("paracelsus", 0), ("cliodna", 0), ("hengist_of_woodcroft", 0),
    ("bowman_wright", 0), ("elfrida_clagg", 0), ("gulliver_pokeby", 0), ("herpo_the_foul", 0), ("bertie_bott", 0),
    ("newt_scamander", 0),
]
CLIP_FRAMES = 36

EYES = [128, 140, 28, 8]
FACE = [128, 150, 46, 60]
FIGURE = (26, 20, 24, 255)
EYE_WHITE = (232, 226, 200, 255)


def save(img, *path):
    full = os.path.join(*path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full, optimize=True)


def tint_for(index):
    """A plate colour per card: a muted hue stepping round the wheel."""
    if index < 0:
        return (92, 86, 84)
    r, g, b = colorsys.hsv_to_rgb((index * 0.137) % 1.0, 0.32, 0.62)
    return (int(r * 255), int(g * 255), int(b * 255))


def plate(tint):
    """The empty plate: a wall, a window glow and a floor, so it reads as a place."""
    img = Image.new("RGBA", (PW * SS, PH * SS))
    d = ImageDraw.Draw(img)
    top = tuple(int(c * 0.72) for c in tint)
    bottom = tuple(min(255, int(c * 1.12)) for c in tint)
    for y in range(PH * SS):
        k = y / (PH * SS)
        d.line([(0, y), (PW * SS, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3)) + (255,))
    # a lit window and a floor line
    glow = tuple(min(255, int(c * 1.55)) for c in tint)
    d.rounded_rectangle([60 * SS, 40 * SS, 196 * SS, 150 * SS], radius=10 * SS, fill=glow + (255,))
    d.rectangle([126 * SS, 40 * SS, 130 * SS, 150 * SS], fill=top + (255,))
    d.rectangle([0, 330 * SS, PW * SS, PH * SS], fill=tuple(int(c * 0.55) for c in tint) + (255,))
    return img


def figure_layer(index, rarity):
    """A dark silhouette with eye dots, on transparent. Odd cards wear a hat, legendaries a taller one."""
    img = Image.new("RGBA", (PW * SS, PH * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = SS
    d.ellipse([28 * s, 250 * s, 228 * s, 520 * s], fill=FIGURE)                # shoulders
    d.rectangle([108 * s, 196 * s, 148 * s, 262 * s], fill=FIGURE)              # neck
    d.ellipse([88 * s, 98 * s, 168 * s, 206 * s], fill=FIGURE)                  # head
    if index % 2 == 0 or rarity >= 1:
        tip = 10 if rarity == 2 else 34
        d.polygon([(128 * s, tip * s), (80 * s, 112 * s), (176 * s, 112 * s)], fill=FIGURE)
        d.ellipse([60 * s, 102 * s, 196 * s, 124 * s], fill=FIGURE)             # brim
    return img


def eyes_on(img, shift=0):
    d = ImageDraw.Draw(img)
    s = SS
    for cx in (114, 142):
        d.ellipse([(cx - 4 + shift) * s, 136 * s, (cx + 4 + shift) * s, 144 * s], fill=EYE_WHITE)
        d.ellipse([(cx - 1.5 + shift * 1.4) * s, 138 * s, (cx + 1.5 + shift * 1.4) * s, 142 * s], fill=FIGURE)


def down(img):
    return img.resize((PW, PH), Image.LANCZOS)


def overlay(kind):
    """blink / look_l / look_r: transparent except the eyes, aligned to base.png."""
    img = Image.new("RGBA", (PW * SS, PH * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = SS
    for cx in (114, 142):                                                        # cover the open eye with head colour
        d.ellipse([(cx - 6) * s, 134 * s, (cx + 6) * s, 146 * s], fill=FIGURE)
    if kind == "blink":
        for cx in (114, 142):
            d.line([((cx - 5) * s, 141 * s), ((cx + 5) * s, 141 * s)], fill=(60, 48, 52, 255), width=2 * s)
    else:
        eyes_on(img, -3 if kind == "look_l" else 3)
    return down(img)


def card_art(key, index, rarity, folder):
    tint = tint_for(index)
    pl = plate(tint)
    fig = figure_layer(index, rarity)
    eyes_on(fig)
    base = Image.alpha_composite(pl, fig)
    empty = pl
    save(down(base).convert("RGBA"), TEX, folder, "base.png")
    save(down(empty).convert("RGBA"), TEX, folder, "empty.png")
    save(down(fig), TEX, folder, "figure.png")
    for kind in ("blink", "look_l", "look_r"):
        save(overlay(kind), TEX, folder, kind + ".png")
    meta = {"eyes": EYES, "face": FACE, "exit_clip": None}
    if rarity == 2 and folder != "_placeholder":
        meta["exit_clip"] = {"frames": CLIP_FRAMES, "fps": 12}
        # first frame = base, last = empty; in between the silhouette lifts and fades with a few motes
        for i in range(CLIP_FRAMES):
            t = i / (CLIP_FRAMES - 1)
            k = t * t * (3 - 2 * t)
            f = Image.alpha_composite(pl, Image.new("RGBA", pl.size, (0, 0, 0, 0)))
            layer = fig.copy()
            if 0 < i < CLIP_FRAMES - 1:
                alpha = layer.getchannel("A").point(lambda v, k=k: int(v * (1 - k)))
                layer.putalpha(alpha)
                layer = layer.transform(layer.size, Image.AFFINE, (1, 0, 0, 0, 1, int(k * 60 * SS)), Image.BICUBIC)
                d = ImageDraw.Draw(layer)
                for m in range(8):
                    a = m * 0.8 + t * 3
                    x = (128 + math.cos(a) * (30 + 90 * k)) * SS
                    y = (150 + math.sin(a * 1.3) * (30 + 80 * k) - 40 * k) * SS
                    r = (3 + 2 * math.sin(a * 2)) * SS
                    d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 226, 140, int(220 * math.sin(math.pi * t))))
                f = Image.alpha_composite(pl, layer)
            elif i == 0:
                f = base
            frame = down(f).convert("RGB")
            save(frame, TEX, folder, "clips", "exit", "%04d.png" % i)
    os.makedirs(CARDS, exist_ok=True)
    with open(os.path.join(CARDS, folder + ".json"), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(meta, fh)
        fh.write("\n")


def frame_art(name, metal, light, dark):
    s = 2
    img = Image.new("RGBA", (CW * s, CH * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, CW * s - 1, CH * s - 1], radius=16 * s, fill=dark + (255,))
    d.rounded_rectangle([4 * s, 4 * s, CW * s - 5 * s, CH * s - 5 * s], radius=13 * s, fill=metal + (255,))
    d.rounded_rectangle([9 * s, 9 * s, CW * s - 10 * s, CH * s - 10 * s], radius=10 * s, outline=light + (255,), width=2 * s)
    # name plate
    d.rounded_rectangle([40 * s, 410 * s, 280 * s, 440 * s], radius=6 * s, fill=(236, 220, 176, 255), outline=dark + (255,), width=2 * s)
    # corner studs
    for cx, cy in ((20, 14), (300, 14), (20, 434), (300, 434)):
        d.ellipse([(cx - 4) * s, (cy - 4) * s, (cx + 4) * s, (cy + 4) * s], fill=light + (255,), outline=dark + (255,))
    # the window: a recessed bevel, then punched through
    x0, y0, x1, y1 = WIN
    d.rectangle([(x0 - 6) * s, (y0 - 6) * s, (x1 + 5) * s, (y1 + 5) * s], fill=dark + (255,))
    d.rectangle([(x0 - 3) * s, (y0 - 3) * s, (x1 + 2) * s, (y1 + 2) * s], fill=light + (255,))
    d.rectangle([x0 * s, y0 * s, (x1 - 1) * s + s - 1, (y1 - 1) * s + s - 1], fill=(0, 0, 0, 0))
    img = img.resize((CW, CH), Image.LANCZOS)
    # LANCZOS can leave a faint fringe inside the window; make the punched area exactly clear
    px = img.load()
    for y in range(WIN[1], WIN[3]):
        for x in range(WIN[0], WIN[2]):
            px[x, y] = (0, 0, 0, 0)
    save(img, TEX, name + ".png")


def back_art():
    img = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, CW - 1, CH - 1], radius=16, fill=(34, 22, 70, 255))
    d.rounded_rectangle([10, 10, CW - 11, CH - 11], radius=10, outline=(224, 184, 72, 255), width=3)
    for gx in range(30, CW - 20, 28):
        for gy in range(30, CH - 20, 28):
            d.polygon([(gx, gy - 9), (gx + 9, gy), (gx, gy + 9), (gx - 9, gy)], outline=(120, 92, 190, 255))
    d.ellipse([CW // 2 - 50, CH // 2 - 50, CW // 2 + 50, CH // 2 + 50], fill=(34, 22, 70, 255), outline=(224, 184, 72, 255), width=4)
    d.text((CW // 2 - 12, CH // 2 - 8), "FWC", fill=(224, 184, 72, 255))
    save(img, TEX, "back.png")


def box_art():
    """A pentagonal Chocolate Frog box, front on. The top 2/5 of the picture is the lid (the reveal lifts it)."""
    s = 3
    W = H = 192
    img = Image.new("RGBA", (W * s, H * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = [(96, 6), (186, 72), (158, 186), (34, 186), (6, 72)]
    d.polygon([(x * s, y * s) for x, y in pts], fill=(92, 52, 30, 255), outline=(236, 196, 90, 255))
    d.line([(x * s, y * s) for x, y in pts + [pts[0]]], fill=(236, 196, 90, 255), width=4 * s)
    d.polygon([(96 * s, 20 * s), (172 * s, 74 * s), (20 * s, 74 * s)], fill=(124, 72, 40, 255))   # lid face
    d.line([(8 * s, 77 * s), (184 * s, 77 * s)], fill=(236, 196, 90, 255), width=3 * s)             # the lid seam
    d.ellipse([66 * s, 104 * s, 126 * s, 160 * s], fill=(236, 196, 90, 255))                         # a gold crest
    d.text((84 * s, 126 * s), "FROG", fill=(60, 30, 14, 255))
    save(img.resize((W, H), Image.LANCZOS), TEX, "box.png")


def main():
    for i, (key, rarity) in enumerate(KEYS):
        card_art(key, i, rarity, key)
    card_art("_placeholder", -1, 0, "_placeholder")
    frame_art("frame_common", (176, 112, 58), (226, 168, 98), (84, 48, 22))
    frame_art("frame_rare", (196, 204, 218), (240, 244, 252), (96, 104, 124))
    frame_art("frame_legendary", (232, 190, 70), (255, 232, 140), (132, 90, 18))
    back_art()
    box_art()
    print("placeholders written under", ASSETS)


if __name__ == "__main__":
    sys.exit(main())
