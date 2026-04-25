"""Apply a subtle unique H/S/V shift per spell so books never match another spell's look."""
import re
import sys
from pathlib import Path

import cv2
import numpy as np

MOD_ROOT = Path(__file__).resolve().parents[1]
JAVA = MOD_ROOT / "src/main/java/com/hogwartsmagic/item/MagicBookSpellOrder.java"
TEXTURES = MOD_ROOT / "src/main/resources/assets/hogwartsmagic/textures/item/magic_books"


def load_spell_order() -> list[str]:
    text = JAVA.read_text(encoding="utf-8")
    start = text.index("List.of(")
    sub = text[start:]
    open_idx = sub.index("(") + 1
    depth = 1
    i = open_idx
    while depth and i < len(sub):
        c = sub[i]
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        i += 1
    body = sub[open_idx : i - 1]
    return re.findall(r'"([a-z_]+)"', body)


def main() -> None:
    spell_order = load_spell_order()
    if not spell_order:
        print("No spells parsed", file=sys.stderr)
        sys.exit(1)

    for spell_idx, sid in enumerate(spell_order):
        path = TEXTURES / f"magic_book_{sid}.png"
        if not path.is_file():
            print("MISSING", path.name, file=sys.stderr)
            continue

        img = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
        if img is None:
            print("BAD READ", path.name, file=sys.stderr)
            continue

        has_a = img.ndim == 3 and img.shape[2] == 4
        bgr = img[:, :, :3].copy()
        alpha = img[:, :, 3] if has_a else None

        hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV).astype(np.float32)
        h, s, v = hsv[:, :, 0], hsv[:, :, 1], hsv[:, :, 2]
        h = np.mod(h + spell_idx * 0.41, 180.0)
        s = np.clip(s * (1.0 + spell_idx * 0.0011), 0.0, 255.0)
        v = np.clip(v * (1.0 + (spell_idx % 13) * 0.0022), 0.0, 255.0)
        hsv2 = np.stack([h, s, v], axis=-1).astype(np.uint8)
        bgr2 = cv2.cvtColor(hsv2, cv2.COLOR_HSV2BGR)

        if has_a:
            out = np.dstack([bgr2, alpha])
        else:
            out = bgr2

        cv2.imwrite(str(path), out, [cv2.IMWRITE_PNG_COMPRESSION, 6])

    print("Processed", len(spell_order), "spell book textures")


if __name__ == "__main__":
    main()
