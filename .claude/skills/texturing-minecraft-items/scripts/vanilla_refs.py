"""Extract vanilla item sprites from the local ForgeGradle cache, for style reference only.

    python vanilla_refs.py [mc_version] [out_dir]

Defaults: newest client-extra.jar found, out_dir = %TEMP%/mc_vanilla_items.
Mojang's textures are reference material -- look at them, measure them, never
copy them into the mod.
"""
import glob, os, sys, zipfile

HOME = os.path.expanduser("~")
PATTERNS = [
    os.path.join(HOME, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "{v}", "client-extra.jar"),
    os.path.join(HOME, ".gradle", "caches", "forge_gradle", "mcp_repo", "de", "oceanlabs", "mcp", "mcp_config",
                 "{v}-*", "joined", "downloadClient", "client.jar"),
    os.path.join(HOME, ".gradle", "caches", "fabric-loom", "{v}", "minecraft-client.jar"),
    os.path.join(HOME, ".minecraft", "versions", "{v}", "{v}.jar"),
]


def find_jar(version):
    for pat in PATTERNS:
        hits = sorted(glob.glob(pat.format(v=version)))
        if hits:
            return hits[-1]
    return None


def main(argv):
    version = argv[0] if argv else "*"
    out = argv[1] if len(argv) > 1 else os.path.join(os.environ.get("TEMP", "/tmp"), "mc_vanilla_items")
    jar = find_jar(version)
    if not jar:
        sys.exit(f"no client jar for {version!r}; looked in:\n  " + "\n  ".join(p.format(v=version) for p in PATTERNS))
    os.makedirs(out, exist_ok=True)
    n = 0
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.startswith("assets/minecraft/textures/item/") and name.endswith(".png"):
                with open(os.path.join(out, os.path.basename(name)), "wb") as f:
                    f.write(z.read(name))
                n += 1
    print(f"{n} sprites from {jar} -> {out}")


if __name__ == "__main__":
    main(sys.argv[1:])
