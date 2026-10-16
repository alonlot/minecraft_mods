"""Check sounds.json: every file entry resolves to a real .ogg, every subtitle key exists in en_us.json,
and every `pensieve.*` event is registered in ModSounds. Exit 1 on any problem."""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
sounds = json.load(open(os.path.join(A, "sounds.json"), encoding="utf-8"))
lang = json.load(open(os.path.join(A, "lang", "en_us.json"), encoding="utf-8"))
java = open(os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic", "ModSounds.java"), encoding="utf-8").read()
bad = []
files = 0
for ev, body in sounds.items():
    if "subtitle" in body and body["subtitle"] not in lang:
        bad.append("%s: subtitle key %s missing from en_us.json" % (ev, body["subtitle"]))
    for s in body.get("sounds", []):
        name = s if isinstance(s, str) else s["name"]
        typ = "sound" if isinstance(s, str) else s.get("type", "file")
        if typ == "event":
            continue
        ns, _, path = name.partition(":")
        if ns != "hogwartsmagic":
            continue
        files += 1
        if not os.path.isfile(os.path.join(A, "sounds", path + ".ogg")):
            bad.append("%s: no file sounds/%s.ogg" % (ev, path))
    if ev.startswith("pensieve.") and 'register("%s")' % ev not in java:
        bad.append("%s: not registered in ModSounds" % ev)
print("%d events, %d file entries, %d problems" % (len(sounds), files, len(bad)))
for b in bad:
    print("  " + b)
sys.exit(1 if bad else 0)
