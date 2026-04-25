from __future__ import annotations

import struct
import zlib
from pathlib import Path


WIDTH = 128
HEIGHT = 64


def make_canvas(width: int, height: int, color=(0, 0, 0, 0)):
    return [[list(color) for _ in range(width)] for _ in range(height)]


def set_px(canvas, x: int, y: int, color):
    if 0 <= y < len(canvas) and 0 <= x < len(canvas[0]):
        canvas[y][x] = list(color)


def fill_rect(canvas, x0: int, y0: int, x1: int, y1: int, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            set_px(canvas, x, y, color)


def add_scale_pattern(canvas):
    for y in range(HEIGHT):
        for x in range(WIDTH):
            wave = ((x * 5 + y * 3) % 19) / 18.0
            stripe = (x + y) % 8
            if y > 42:
                base = [84, 108, 55]
            else:
                base = [30, 86, 40]

            if stripe in (0, 1):
                base[0] += 18
                base[1] += 28
                base[2] += 10
            elif stripe == 4:
                base[0] -= 10
                base[1] -= 18
                base[2] -= 6

            base[0] = min(255, max(0, int(base[0] + 10 * wave)))
            base[1] = min(255, max(0, int(base[1] + 16 * wave)))
            base[2] = min(255, max(0, int(base[2] + 8 * wave)))
            set_px(canvas, x, y, (*base, 255))


def add_belly(canvas, x0: int, y0: int, x1: int, y1: int):
    for y in range(y0, y1):
        for x in range(x0, x1):
            tone = 96 + ((x + y) % 14)
            color = (
                min(255, tone + 18),
                min(255, tone + 28),
                max(0, tone - 14),
                255,
            )
            if (x + y) % 6 == 0:
                color = (color[0] - 18, color[1] - 22, color[2] - 14, 255)
            set_px(canvas, x, y, color)


def add_head_marks(canvas):
    gold = (201, 177, 64, 255)
    dark = (22, 38, 20, 255)
    for x in range(7, 13):
        set_px(canvas, x, 5, gold)
        set_px(canvas, x, 6, gold)
    for x in range(20, 26):
        set_px(canvas, x, 5, gold)
        set_px(canvas, x, 6, gold)
    fill_rect(canvas, 14, 4, 19, 8, dark)
    fill_rect(canvas, 0, 19, 19, 24, (116, 132, 63, 255))
    fill_rect(canvas, 36, 8, 48, 16, (123, 147, 70, 255))
    fill_rect(canvas, 36, 0, 44, 7, (92, 118, 47, 255))


def add_fangs(canvas):
    fang = (222, 214, 188, 255)
    shadow = (176, 168, 144, 255)
    for x in range(0, 5):
        for y in range(27, 31):
            set_px(canvas, x, y, fang)
    set_px(canvas, 1, 27, shadow)
    set_px(canvas, 3, 27, shadow)
    set_px(canvas, 1, 30, shadow)
    set_px(canvas, 3, 30, shadow)


def add_tail_banding(canvas):
    for start_x in (52, 68, 84):
        for x in range(start_x, min(start_x + 4, WIDTH)):
            for y in range(30, HEIGHT):
                current = canvas[y][x]
                set_px(canvas, x, y, (max(0, current[0] - 18), max(0, current[1] - 28), max(0, current[2] - 12), 255))


def make_stone_texture():
    canvas = make_canvas(64, 64)
    for y in range(64):
        for x in range(64):
            base = 126 + ((x * 7 + y * 11) % 22)
            crack = 0
            if (x - y) % 17 == 0 or (x + y) % 29 == 0:
                crack = -34
            if (x // 8 + y // 8) % 2 == 0:
                base += 10
            color = (
                max(0, min(255, base + crack)),
                max(0, min(255, base - 4 + crack)),
                max(0, min(255, base - 10 + crack)),
                255,
            )
            set_px(canvas, x, y, color)
    return canvas


def write_png(path: Path, canvas) -> None:
    height = len(canvas)
    width = len(canvas[0])
    raw = bytearray()
    for row in canvas:
        raw.append(0)
        for pixel in row:
            raw.extend(pixel)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    png.extend(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
    png.extend(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
    png.extend(chunk(b"IEND", b""))
    path.write_bytes(png)


def main():
    canvas = make_canvas(WIDTH, HEIGHT)
    add_scale_pattern(canvas)
    add_belly(canvas, 0, 42, WIDTH, HEIGHT)
    add_head_marks(canvas)
    add_fangs(canvas)
    add_tail_banding(canvas)

    out_path = Path("src/main/resources/assets/hogwartsmagic/textures/entity/basilisk.png")
    write_png(out_path, canvas)
    stone_path = Path("src/main/resources/assets/hogwartsmagic/textures/entity/petrified_stone.png")
    write_png(stone_path, make_stone_texture())
    print(f"Wrote {out_path}")
    print(f"Wrote {stone_path}")


if __name__ == "__main__":
    main()
