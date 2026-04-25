import re

PATH = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\java\com\hogwartsmagic\client\model\WhulvkLycanIdleToWolfAnimations.java"
OUT = r"c:\Users\alonl\OneDrive\Desktop\harry potter mod\src\main\java\com\hogwartsmagic\client\model\WhulvkLycanSlowHumanToWolfAnimations.java"
OLD = 0.4167

LEGS = {
    "tighLeft",
    "tighFurLeft",
    "legLeft",
    "legFurLeft",
    "shankLeft",
    "footLeft",
    "tighRight",
    "tighFurRight",
    "legRight",
    "legFurRIght",
    "shankRight",
    "footRight",
}


def window(name: str) -> tuple[float, float]:
    # Fingers / claws first (~0–2.2s)
    if name.startswith("finger") or name.startswith("upperFinger") or name.startswith("lowerFinger"):
        return (0.0, 2.2)
    # Hands widen into paws (~0.5–2.8s)
    if name in ("handLeft", "handRight"):
        return (0.5, 2.8)
    # Arms / shoulders (~2–5s)
    if name in (
        "armLeft",
        "armRight",
        "upperArmLeft",
        "upperArmRight",
        "lowerArmLeft",
        "lowerArmRight",
        "LowerArmFurLeft",
        "lowerArmFurRight",
    ):
        return (2.0, 5.0)
    # Legs / hips (~3.5–6.8s)
    if name in LEGS:
        return (3.5, 6.8)
    # Tail last (~8.3–10s)
    if name == "tail":
        return (8.3, 10.0)
    # Torso / quadruped drop (~5–8.5s)
    if name in ("body", "lowerBody", "middleBody", "upperBody", "chestRight", "leftChest"):
        return (5.0, 8.5)
    if name == "root":
        return (5.5, 9.0)
    # Head / face (~6.5–9.5s)
    if name in (
        "lowerNeck",
        "upperNeck",
        "head",
        "headFurLeft",
        "headFurRight",
        "headFurTop",
        "earLeft",
        "earRight",
        "eyebrowLeft",
        "eyebrowRight",
        "jaw",
        "tongue",
    ):
        return (6.5, 9.5)
    return (0.0, 10.0)


def fmt_t(t: float) -> str:
    s = f"{t:.4f}".rstrip("0").rstrip(".")
    if s in ("-0", "-0.0"):
        s = "0"
    return s + "F"


def map_kf(m: re.Match, ws: float, we: float) -> str:
    t = float(m.group(1))
    nt = ws + (t / OLD) * (we - ws)
    return "new Keyframe(" + fmt_t(nt)


with open(PATH, "r", encoding="utf-8") as f:
    lines = f.readlines()

out: list[str] = []
i = 0
while i < len(lines):
    line = lines[i]
    if "public final class WhulvkLycanIdleToWolfAnimations" in line:
        line = line.replace("WhulvkLycanIdleToWolfAnimations", "WhulvkLycanSlowHumanToWolfAnimations")
    if "idle_to_wolf" in line and "AnimationDefinition" in line:
        line = line.replace("idle_to_wolf", "slow_human_to_wolf")
    if "withLength(0.4167F)" in line:
        line = line.replace("withLength(0.4167F)", "withLength(10.0F)")
    m = re.search(r'\.addAnimation\("([^"]+)"', line)
    if m:
        bone = m.group(1)
        ws, we = window(bone)
        out.append(line)
        i += 1
        while i < len(lines):
            l = lines[i]
            if l.strip().startswith(".addAnimation") or ".build()" in l:
                break
            l2 = re.sub(
                r"new Keyframe\(([0-9.]+)F",
                lambda mo: map_kf(mo, ws, we),
                l,
            )
            out.append(l2)
            i += 1
        continue
    out.append(line)
    i += 1

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("".join(out))
print("wrote", OUT)
