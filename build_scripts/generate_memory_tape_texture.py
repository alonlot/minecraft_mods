"""The memory_tape actor's texture: a cream cloth measuring tape with ticks and numbers, and a brass swatch.

Layout (64 x 256, see client/MemoryTapeRenderer):
  x 0..31   the tape, running down v: ticks from both edges every 4 px, long ones every 16 px with a number
            between them (1 to 16, reading along the tape).
  x 32..63, y 0..31   brushed brass for the end caps.
Run from the repo root: python build_scripts/generate_memory_tape_texture.py
"""
import random
from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/hogwartsmagic/textures/entity/memory_tape.png")
W, H = 64, 256
TAPE_W = 32

# 3 x 5 digits, rows top to bottom.
DIGITS = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
}


def main():
    rnd = random.Random(7)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    ink = (40, 30, 24, 255)
    for y in range(H):
        for x in range(TAPE_W):
            # Cream cloth with a faint weave and slightly darker selvedges.
            n = rnd.randint(-6, 6) + (3 if (x + y) % 2 == 0 else 0)
            edge = -18 if x in (0, TAPE_W - 1) else (-8 if x in (1, TAPE_W - 2) else 0)
            px[x, y] = (232 + n + edge, 214 + n + edge, 160 + n + edge, 255)
    for y in range(0, H, 4):
        long_tick = y % 16 == 0
        mid_tick = y % 8 == 0
        depth = 9 if long_tick else (7 if mid_tick else 5)
        for x in range(2, 2 + depth):
            px[x, y] = ink
            px[TAPE_W - 1 - x, y] = ink
    # The numbers, centred across the tape, reading down v (each digit turned a quarter: 5 across, 3 along).
    for k in range(H // 16):
        label = str(k + 1)
        along = len(label) * 4 - 1
        y0 = k * 16 + 8 - along // 2
        for d, ch in enumerate(label):
            glyph = DIGITS[ch]
            for row in range(5):
                for col in range(3):
                    if glyph[row][col] == "1":
                        x = TAPE_W // 2 + 2 - row
                        y = y0 + d * 4 + col
                        if 0 <= y < H:
                            px[x, y] = (150, 30, 24, 255) if (k + 1) % 4 == 0 else ink
    # Brushed brass.
    for y in range(32):
        for x in range(32, 64):
            n = rnd.randint(-10, 10)
            streak = 12 if y % 5 == 0 else 0
            px[x, y] = (196 + n + streak, 150 + n + streak, 62 + n // 2, 255)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
