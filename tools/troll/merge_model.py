"""Splices createBodyLayer from the Blockbench export into TrollModel.java and adds club_loose.

    python tools/troll/merge_model.py

club_loose is the same cubes (same UVs) as club, parented to root instead of the hand: it is what
flies off under Wingardium Leviosa and lies on the ground while the held club is hidden. Its bind pose
is the held club's rest pose in root space, so a clip starting at zero delta starts in the fist.
"""
import pathlib
import re

HERE = pathlib.Path(__file__).parent
ROOT = HERE.parent.parent
EXPORT = (HERE / "TrollModel_export.java").read_text(encoding="utf8")
TARGET = ROOT / "src/main/java/com/hogwartsmagic/client/model/TrollModel.java"
# Measured with TrollHarness --club: the held club's pivot in root space at rest.
LOOSE_POSE = "PartPose.offsetAndRotation(-19.6F, -16.0F, -4.9F, -1.05F, 0.0F, 0.28F)"

m = re.search(r"MeshDefinition meshdefinition.*?return LayerDefinition\.create\(meshdefinition, 256, 256\);", EXPORT, re.S)
body = m.group(0)
club = re.search(r'PartDefinition club = hand_r\.addOrReplaceChild\("club", (CubeListBuilder\.create\(\).*?), PartPose', body, re.S)
insert = ('\t\troot.addOrReplaceChild("club_loose", ' + club.group(1) + ", " + LOOSE_POSE + ");\n\n")
anchor = "\t\treturn LayerDefinition.create"
body = body.replace(anchor, insert + anchor)
body = "\n".join("    " + line.replace("\t\t", "    ", 1) if line.strip() else "" for line in body.split("\n"))
body = body.strip()

src = TARGET.read_text(encoding="utf8")
a = src.index("    public static LayerDefinition createBodyLayer() {")
b = src.index("    @Override\n    public ModelPart root()") if "    @Override\n    public ModelPart root()" in src else src.index("    @Override\r\n    public ModelPart root()")
src = src[:a] + "    public static LayerDefinition createBodyLayer() {\n        " + body + "\n    }\n\n" + src[b:]
TARGET.write_text(src, encoding="utf8", newline="\n")
print("merged", len(body), "chars")
