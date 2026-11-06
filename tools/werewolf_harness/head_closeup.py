"""Close-up renders of a bone subtree: python head_closeup.py faces.json texture.png out.png boneSubstr az1,az2,.. [el]"""
import json, sys
import numpy as np
from PIL import Image
import render_werewolf as R

polys = json.load(open(sys.argv[1]))["polys"]
tex = np.array(Image.open(sys.argv[2]).convert("RGBA"))
sub = sys.argv[4]
azs = [float(a) for a in sys.argv[5].split(",")]
el = float(sys.argv[6]) if len(sys.argv) > 6 else 5.0
sel = [p for p in polys if sub in p["bone"]] if sub != "*" else polys
P = np.array([v[:3] for p in sel for v in p["v"]])
c = (P.min(0) + P.max(0)) / 2
ext = float(np.max(P.max(0) - P.min(0)))
frame = {"name": sub, "quads": [[x for v in p["v"] for x in v] for p in sel]}
R.GROUND = 0
sheet = Image.new("RGB", (len(azs) * R.W, R.H))
for i, a in enumerate(azs):
    sheet.paste(R.render(frame, tex, a, el=el, dist=ext * 2.6, target=tuple(c)), (i * R.W, 0))
sheet.save(sys.argv[3])
