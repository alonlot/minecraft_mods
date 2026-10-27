"""Spellbook item sprites for every spell, built on the texturing-spellbooks skill's master book.

    python tools/item_textures/spellbooks.py [spell ...]

Writes textures/item/magic_books/magic_book_<spell>.png and textures/item/magic_book.png.
Cover and glow colours were sampled from the original painted 1024-2048px books
(sample_colours.py). The redrawn set was rejected: the mod keeps the painted books,
so this script refuses to overwrite an existing texture.
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, ".claude", "skills", "texturing-spellbooks", "scripts"))
from spellbook import Spell, save_book  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item")

SPELLS = {
    "accio":               Spell("#0f6a70", "arrow_in", "#5ff0f0", gem="#37e1e2"),
    "alatus":              Spell("#3a384a", "wing", "#e6ea98"),
    "alohomora":           Spell("#2c3c74", "key", "#f6d25a", gem="#88e1f6", metal="gold"),
    "apparition":          Spell("#3e3650", "vortex", "#c49aec"),
    "aqua_eructo":         Spell("#263070", "drop", "#8fc4f2"),
    "arresto_momentum":    Spell("#2f5254", "hourglass", "#e4d27a"),
    "ascendio":            Spell("#363552", "up", "#a8b2ee"),
    "avada_kedavra":       Spell("#243a30", "skull", "#5af0a0", core="#d8ffe8"),
    "bombarda":            Spell("#6c4416", "burst", "#f6a23e", core="#fff2c0", metal="iron"),
    "cave_inimicum":       Spell("#561a58", "dome", "#e070ec"),
    "colloportus":         Spell("#30483f", "lock", "#f1ce58", shade="#a8801c", metal="gold"),
    "confringo":           Spell("#742f08", "flame", "#fa9a22", core="#fff0a0", metal="iron"),
    "confundo":            Spell("#38344a", "spiral", "#f0a27a"),
    "crucio":              Spell("#52241a", "bolt", "#f58a20", core="#ffe0a0", metal="iron"),
    "depulso":             Spell("#5a4e3c", "arrow_out", "#e2e49c", metal="bronze"),
    "diffindo":            Spell("#5c5632", "slash", "#e8f08e", metal="bronze"),
    "dolohov_curse":       Spell("#5a2258", "slash", "#f650ec", core="#ffd8fa"),
    "expecto_patronum":    Spell("#3a4678", "stag", "#c8d8ff", gem="#4658e1"),
    "expelliarmus":        Spell("#6c2e1e", "wand", "#f2d8a8", shade="#8a5a32", core="#6ad0ff", gem="#48b2ec", metal="iron"),
    "fiendfyre":           Spell("#4a2418", "serpent", "#f8b420", core="#fff4a0", metal="iron"),
    "glacius":             Spell("#173462", "snowflake", "#5ff2f6"),
    "heal":                Spell("#0c7452", "heart", "#ff8a9a", shade="#c8405a", gem="#68e06c", metal="gold"),
    "imperio":             Spell("#35224a", "eye", "#8a90f6", core="#ffffff"),
    "incarcerous":         Spell("#466866", "rope", "#a8e2de", shade="#4a8a86"),
    "incendio":            Spell("#822b16", "flame", "#fbc040", core="#fff8d0", metal="iron"),
    "levicorpus":          Spell("#482e60", "dangle", "#eed8aa"),
    "lumos":               Spell("#32487a", "orb", "#bfe6ff", core="#ffffff", gem="#4fa2e1"),
    "morsmordre":          Spell("#2e4530", "dark_mark", "#a8f29c", shade="#3a9a3a"),
    "obliviate":           Spell("#4e5a36", "fog", "#b0eade"),
    "petrificus_totalus":  Spell("#333849", "figure", "#a8bfee"),
    "piertotum_locomotor": Spell("#4e4f55", "knight", "#e0aa56", metal="iron"),
    "protego":             Spell("#2c3c6a", "shield", "#86dcf0"),
    "protego_maxima":      Spell("#425240", "shield_star", "#96eaf2", metal="gold"),
    "reparo":              Spell("#4c4436", "hammer", "#eec05a", metal="bronze"),
    "revelio":             Spell("#145864", "eye", "#4ff0e8"),
    "riddikulus":          Spell("#663584", "grin", "#ff98d8"),
    "sectumsempra":        Spell("#42251a", "cuts", "#e8343a", shade="#8a1a1e", gem="#d2c98c", metal="iron"),
    "serpensortia":        Spell("#204c3c", "snake", "#48dc78", core="#f0e060"),
    "stupefy":             Spell("#722218", "star", "#f27a3a", core="#fff0d0", metal="iron"),
    "tenebris_flight":     Spell("#302a44", "dark_wing", "#a49ac8", shade="#5a5078"),
    "wingardium_leviosa":  Spell("#742a82", "feather", "#e0f6a8"),
}
GENERIC = Spell("#73271f", "star", "#f0b830", core="#fff4c2", metal="gold")   # "Spellbook" / Hogwarts guide


def main(names):
    """Build only the named spells, and never over an existing texture: the mod's
    spellbooks are the original painted books (the 16x16 redraw was rejected 2026-10-27)."""
    force = "--force" in names
    names = [n for n in names if n != "--force"]
    if not names:
        print("usage: spellbooks.py <spell> [...] [--force]  (writes only spells with no texture yet)")
        return
    for n in names:
        path = os.path.join(OUT, "magic_book.png") if n == "_generic" else             os.path.join(OUT, "magic_books", f"magic_book_{n}.png")
        if os.path.exists(path) and not force:
            print(f"skip {n}: {path} exists")
            continue
        save_book(GENERIC if n == "_generic" else SPELLS[n], path)
        print("wrote", path)


if __name__ == "__main__":
    main(sys.argv[1:])
