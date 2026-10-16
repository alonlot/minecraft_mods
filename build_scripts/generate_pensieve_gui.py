"""Paint the Pensieve dive overlays.

    python build_scripts/generate_pensieve_gui.py

Writes, under assets/hogwartsmagic/textures/gui/pensieve/:

* silver_surface.png (2048x128): 8 horizontal frames of 256x128. The swirling silver liquid that rises up the
  screen as the basin swallows the viewer (and drains away on the way back out). PensieveOverlay picks the frame
  from the client clock, so there is no .mcmeta. The swirl loops seamlessly over the 8 frames and tiles
  horizontally, so the overlay can stretch it across any screen width.
* wisp.png (32x128): two 16x128 streaks of silver mist. Column 0 has its bright head at the top (a wisp
  streaking upward past a falling viewer); column 1 is the same streak flipped (rising viewer, wisps fall past).

Same hue-shifted silver ramp as the Memory of the Cave phial (blue-violet shadows to cyan-white highlights).
Fully deterministic: no randomness, so re-running it produces identical PNGs.
"""

import math
import os

from PIL import Image

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "gui", "pensieve")
FRAMES = 8
FW, FH = 256, 128
TAU = 2 * math.pi


def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16))


RAMP = [hexc(c) for c in ("#1a2140", "#26304f", "#4b5a86", "#7d92bf", "#b3c8e6", "#e0eefa", "#ffffff")]


def ramp(v):
    """Silver colour for v in 0..1, interpolated along RAMP."""
    v = min(1.0, max(0.0, v)) * (len(RAMP) - 1)
    i = min(len(RAMP) - 2, int(v))
    f = v - i
    a, b = RAMP[i], RAMP[i + 1]
    return tuple(int(round(a[k] + (b[k] - a[k]) * f)) for k in range(3))


def swirl(x, y, phase):
    """Layered, horizontally periodic sine field; phase in 0..1 loops the animation."""
    u = x / FW * TAU
    w = y / FH
    p = phase * TAU
    # Domain warp: the bands bend around slow eddies, which reads as liquid rather than stripes.
    warp = 0.9 * math.sin(2 * u + 3.1 * w * TAU / 2 + p) + 0.5 * math.sin(3 * u - 2.0 * w * TAU / 2 - p)
    bands = math.sin(4 * u + 9.0 * w + warp * 1.6 + p)
    fine = math.sin(7 * u - 15.0 * w + warp * 2.3 - 2 * p) * 0.45
    eddy = math.sin(1 * u + 5.0 * w + 2.0 * math.sin(u * 2 - p)) * 0.6
    v = (bands + fine + eddy) / 2.05
    return 0.5 + 0.5 * v


def silver_surface():
    img = Image.new("RGBA", (FW * FRAMES, FH))
    px = img.load()
    for f in range(FRAMES):
        phase = f / FRAMES
        for y in range(FH):
            # Lighter near the top edge (the meniscus catching the light), deeper silver below.
            depth = y / (FH - 1)
            for x in range(FW):
                s = swirl(x, y, phase)
                # Sharpen into wisps: bright filaments on a mid-silver body.
                filament = max(0.0, (s - 0.62) / 0.38) ** 1.6
                v = 0.38 + 0.22 * s + 0.40 * filament - 0.12 * depth
                c = ramp(v)
                px[f * FW + x, y] = (c[0], c[1], c[2], 255)
    return img


def wisp_column(head_top):
    col = Image.new("RGBA", (16, 128), (0, 0, 0, 0))
    px = col.load()
    for y in range(128):
        # t = 0 at the head, 1 at the tail end.
        t = y / 127 if head_top else 1 - y / 127
        # Head is a soft bright bulb; the tail thins and fades.
        width = 1.0 + 2.2 * (1 - t) ** 1.5 + 4.2 * math.exp(-((t - 0.04) / 0.05) ** 2)
        bright = math.exp(-3.2 * t) * min(1.0, 0.4 + t * 25)
        for x in range(16):
            d = abs(x - 7.5)
            if d > width:
                continue
            edge = 1 - (d / width) ** 2
            a = edge * (0.15 + 0.85 * bright)
            # A faint ripple along the tail keeps it from reading as a ruled line.
            a *= 0.85 + 0.15 * math.sin(t * 18 + x * 0.6)
            c = ramp(0.55 + 0.45 * bright * edge)
            px[x, y] = (c[0], c[1], c[2], int(round(255 * min(1.0, max(0.0, a)))))
    return col


def wisp():
    img = Image.new("RGBA", (32, 128), (0, 0, 0, 0))
    img.paste(wisp_column(True), (0, 0))
    img.paste(wisp_column(False), (16, 0))
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    silver_surface().save(os.path.join(OUT_DIR, "silver_surface.png"))
    wisp().save(os.path.join(OUT_DIR, "wisp.png"))
    print("wrote", os.path.normpath(OUT_DIR))


if __name__ == "__main__":
    main()
