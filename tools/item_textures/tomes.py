"""Tome of Mastery sprites: one thick upright tome, recoloured per rarity tier.

    python tools/item_textures/tomes.py

Deliberately a different silhouette from the lying spellbooks (texturing-spellbooks):
a tome is the upgrade item, so it stands up, shows its page block and wears a gem
in a gold setting. Tier colours follow the original textures.
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, ".claude", "skills", "texturing-minecraft-items", "scripts"))
from pixkit import sprite, ramp, save  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item")

# k outline; 1-4 cover ramp; S spine highlight; G/g gold light/dark, o/O bezel;
# w/e/f/h gem specular/light/mid/dark; p/q/r pages; s/t strap.
TOME = """
................
....kkkkkkkkkk..
...k2ppppppppk..
..k2qqqqqqqqrk..
.k444444443qrk..
.kS12222GG1qrk..
.kGg22222g1qrk..
.kS122ee221qrk..
.kS12ewfh21qrk..
.kS12ffhh2ossk..
.kS122hh12Ottk..
.kS12211221qrk..
.kGg2222221qrk..
.kS111111g1qk...
.kS11111GG1k....
.kkkkkkkkkk.....
"""

BASE = {
    "G": "#f6d25a", "g": "#a8701a", "o": "#e2b23a", "O": "#8a5a14",
    "p": "#efe6cb", "q": "#c4b48e", "r": "#8f7c5c", "w": "#ffffff",
}

# tier: (cover midtone, gem light, gem mid, gem dark)
TIERS = {
    "common":   ("#6e6a62", "#f2f4f8", "#c4ccd8", "#5e6878"),
    "uncommon": ("#8a6a14", "#fff7b0", "#ffd83a", "#b88a10"),
    "rare":     ("#1d6a74", "#c8fdff", "#48e4f2", "#1890a0"),
    "epic":     ("#5e2e7a", "#f8dcff", "#d080ff", "#8a34c8"),
}


def build(tier):
    cover, gl, gm, gd = TIERS[tier]
    c = ramp(cover, 5, spread=0.42)
    pal = {".": None, **BASE, "k": c[0], "1": c[1], "2": c[2], "3": c[3], "4": c[3], "S": c[3],
           "e": gl, "f": gm, "h": gd, "s": c[2], "t": c[1]}
    return sprite(TOME, pal)


if __name__ == "__main__":
    for t in TIERS:
        save(build(t), os.path.join(OUT, f"{t}_tome_of_mastery.png"))
    print("wrote", len(TIERS), "tomes")
