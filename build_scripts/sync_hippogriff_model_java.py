"""Splices Blockbench's exported geometry into HippogriffModel.java.

The model class is hand-written apart from createBodyLayer(), whose body is baked
straight out of Blockbench. Rather than re-pasting 60 lines by hand every time the
geometry moves, export the project through the `modded_entity` codec and run this:

    python build_scripts/sync_hippogriff_model_java.py <exported.java>

Only the contents of createBodyLayer() are replaced; imports, fields, setupAnim and
renderToBuffer are left untouched. The export's tab indentation is converted to the
4-space style the rest of the file uses, and `_r1` sub-parts (Blockbench's name for a
cube carrying its own rotation) drop their unused local variable so javac stays quiet.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET = os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic",
                      "client", "model", "HippogriffModel.java")

BEGIN = "    public static LayerDefinition createBodyLayer() {"
END_MARKER = "    @Override\n    public ModelPart root() {"

# Bones the hand-written animation code looks up; everything else can lose its local.
KEEP_LOCALS = {
    "root", "body", "chest", "ruff", "neck", "neck_upper", "head", "crest",
    "left_wing", "left_wing_fore", "left_wing_hand",
    "right_wing", "right_wing_fore", "right_wing_hand",
    "front_left_leg", "front_left_shank", "front_left_foot",
    "front_right_leg", "front_right_shank", "front_right_foot",
    "back_left_leg", "back_left_shin", "back_right_leg", "back_right_shin",
    "tail", "tail_hair",
}


def extract_body(export_path):
    with open(export_path, encoding="utf-8") as fh:
        src = fh.read()

    start = src.index("public static LayerDefinition createBodyLayer() {")
    start = src.index("\n", start) + 1
    # Stop before the return line; main() emits its own.
    end = src.index("return LayerDefinition.create(", start)
    end = src.rindex("\n", start, end) + 1

    # The export indents the method body with two tabs, which is the 8 spaces we want.
    lines = [line.replace("\t", "    ") for line in src[start:end].split("\n")]
    body = "\n".join(lines).rstrip("\n")

    # Drop `PartDefinition x =` for bones nothing else refers to.
    def strip_unused(match):
        name = match.group(2)
        return match.group(0) if name in KEEP_LOCALS else match.group(3)

    body = re.sub(
        r"(PartDefinition (\w+) = )(\w+\.addOrReplaceChild\(\")",
        lambda m: m.group(0) if m.group(2) in KEEP_LOCALS else m.group(3),
        body,
    )
    return body


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    body = extract_body(sys.argv[1])

    with open(TARGET, encoding="utf-8") as fh:
        current = fh.read()

    head = current[:current.index(BEGIN) + len(BEGIN)]
    tail = current[current.index(END_MARKER):]

    out = head + "\n" + body + "\n\n        return LayerDefinition.create(meshdefinition, 256, 256);\n    }\n\n" + tail
    with open(TARGET, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(out)
    print("synced geometry into", TARGET)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
