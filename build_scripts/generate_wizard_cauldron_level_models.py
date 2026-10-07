"""Write the per-level Wizard Cauldron block models from the full one.

    python3 build_scripts/generate_wizard_cauldron_level_models.py

models/block/wizard_cauldron.json is the full pot (blockstate level=3). Its brew surface is the one element whose
only face is an "up" face with the #brew texture. This script gives that face tintindex 0 (the client BlockColor
colours it from the cauldron's block entity) and writes three copies with the surface moved or removed:

  wizard_cauldron_empty.json    level 0: no surface
  wizard_cauldron_level1.json   level 1: the surface at y 5, across the belly (3..13)
  wizard_cauldron_level2.json   level 2: the surface at y 7, across the belly (3..13)
  wizard_cauldron.json          level 3: the surface stays at y 9.5, inside the neck (4..12)

The belly walls run from 2 to 3 and 13 to 14 between y 3 and 9, so the lower surfaces are 10 wide; the neck walls
leave 4..12 above y 9. Running the script again changes nothing.
"""

import json
import os

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                         "assets", "hogwartsmagic", "models", "block")

# name: (surface height, low edge, high edge)
LEVELS = {
    "wizard_cauldron_level1": (5, 3, 13),
    "wizard_cauldron_level2": (7, 3, 13),
}


def is_surface(element):
    faces = element.get("faces", {})
    return list(faces.keys()) == ["up"] and faces["up"].get("texture") == "#brew"


def write(name, model):
    with open(os.path.join(MODEL_DIR, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")


def main():
    with open(os.path.join(MODEL_DIR, "wizard_cauldron.json"), encoding="utf-8") as fh:
        full = json.load(fh)
    surfaces = [e for e in full["elements"] if is_surface(e)]
    if len(surfaces) != 1:
        raise SystemExit("expected one brew surface in wizard_cauldron.json, found %d" % len(surfaces))
    surfaces[0]["faces"]["up"]["tintindex"] = 0
    write("wizard_cauldron", full)

    pot = [e for e in full["elements"] if not is_surface(e)]
    write("wizard_cauldron_empty", dict(full, elements=pot))
    for name, (y, lo, hi) in LEVELS.items():
        surface = {
            "from": [lo, y, lo],
            "to": [hi, y, hi],
            "faces": {"up": {"texture": "#brew", "tintindex": 0}},
        }
        write(name, dict(full, elements=pot + [surface]))
    print("wrote 4 cauldron models to", os.path.abspath(MODEL_DIR))


if __name__ == "__main__":
    main()
