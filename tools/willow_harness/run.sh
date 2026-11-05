#!/usr/bin/env bash
# Whomping Willow parity harness.
#
#   bash tools/willow_harness/run.sh [CLASSES_DIR]
#
# Proves that the branch the server hit-tests is the branch the client draws:
#   WillowHarness  - rig FK vs Minecraft's own ModelPart + LivingEntityRenderer transform, every
#                    attack state and yaw; pose continuity (no pops); IK targets met on damage ticks;
#                    dumps posed, textured quads to frames.json
#   HitCheck       - the entity's hit tests against a standing / side-stepping player on a grid
#   render_willow  - z-buffered textured preview of frames.json with the hit line in red:
#                    python tools/willow_harness/render_willow.py OUT/frames.json <texture> sheet.png 104
#
# CLASSES_DIR is a compiled sourcesSets/main for everything except the three willow classes, which
# are recompiled fresh from src/. Default: C:/Users/alonl/hp-verify/build/sourcesSets/main (compile
# there first, never in the main checkout while a client runs).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CLASSES="${1:-C:/Users/alonl/hp-verify/build/sourcesSets/main}"
JDK="${JDK:-/c/Program Files/Java/jdk-21.0.10/bin}"
OUT="${OUT:-$HERE/out}"
MCV=$(grep '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')
FV=$(grep '^forge_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')
MC=$(ls ~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/${MCV}-${FV}_mapped_official_*/forge-*-recomp.jar | head -1)
LIBS=$(find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -v -- -sources | xargs -n1 cygpath -m | tr '\n' ';')
mkdir -p "$OUT/classes"
printf -- '-cp\n"%s;%s;%s;%s"\n' "$(cygpath -m "$OUT/classes")" "$CLASSES" "$(cygpath -m "$MC")" "$LIBS" > "$OUT/cp.args"
SRC="$ROOT/src/main/java/com/hogwartsmagic"
"$JDK/javac" -nowarn -implicit:none @"$OUT/cp.args" -d "$OUT/classes" \
    "$SRC/entity/WhompingWillowRig.java" "$SRC/entity/WhompingWillowEntity.java" \
    "$SRC/client/model/WhompingWillowModel.java" "$HERE/WillowHarness.java" "$HERE/HitCheck.java"
"$JDK/java" @"$OUT/cp.args" WillowHarness "$OUT/frames.json" 2>&1 | grep -v SLF4J
"$JDK/java" @"$OUT/cp.args" HitCheck 2>&1 | grep -v SLF4J
