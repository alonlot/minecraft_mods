#!/usr/bin/env bash
# Entity texture audit: bake every entity model with Minecraft's ModelPart, dump its quads, then
# score each texture against the UV regions the model really samples and render preview sheets.
#
#   bash tools/texture_audit/run.sh [CLASSES_DIR]
#
# CLASSES_DIR defaults to the hp-verify worktree's compiled classes (never build in the main
# checkout while a client runs); the model sources are recompiled fresh from src/ on top of it.
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
grep -l "static LayerDefinition create" "$SRC"/client/model/*Model.java "$SRC"/client/*Model*.java     "$SRC"/entity/StudentModel.java | while read -r f; do printf '"%s"
' "$(cygpath -m "$f")"; done > "$OUT/src.args"
printf '"%s"
' "$(cygpath -m "$HERE/AtlasDump.java")" >> "$OUT/src.args"
"$JDK/javac" -nowarn -implicit:none @"$OUT/cp.args" -d "$OUT/classes" @"$OUT/src.args"
M=com.hogwartsmagic.client.model
C=com.hogwartsmagic.client
"$JDK/java" @"$OUT/cp.args" AtlasDump "$OUT/quads.json" \
  acromantula=$M.AcromantulaModel#createBodyLayer basilisk=$M.BasiliskModel#createBodyLayer \
  boggart=$M.BoggartModel#createBodyLayer dementor=$M.DementorModel#createBodyLayer \
  giant=$M.GiantModel#createBodyLayer goblin=$M.GoblinModel#createBodyLayer \
  grindylow=$M.GrindylowModel#createBodyLayer hippogriff=$M.HippogriffModel#createBodyLayer \
  house_elf=$M.HouseElfModel#createBodyLayer horntail=$M.HungarianHorntailModel#createBodyLayer \
  inferius=$M.InferiusModel#createBodyLayer merperson=$M.MerpersonModel#createBodyLayer \
  minator=$M.MinatorModel#createBodyLayer patronus=$M.PatronusModel#createBodyLayer \
  snake=$M.SnakeModel#createBodyLayer troll=$M.TrollModel#createBodyLayer \
  willow=$M.WhompingWillowModel#createBodyLayer lycan=$M.WhulvkWerewolfLycanModel#createBodyLayer \
  cart=$M.GringottsCartModel#createBodyLayer flying=$M.FlyingMobModel#createBodyLayer \
  owl=$C.OwlModel#createBodyLayer wizard=$C.WizardModel#createBodyLayer \
  student=com.hogwartsmagic.entity.StudentModel#createBodyLayer statue=$C.LivingStatueModel#createBodyLayer \
  snitch=$C.GoldenSnitchModel#createBodyLayer broom_training=$C.BroomModelV2#createTrainingLayer \
  broom_nimbus=$C.BroomModelV2#createNimbusLayer broom_firebolt=$C.BroomModelV2#createFireboltLayer \
  2>&1 | grep -v SLF4J
python "$HERE/audit.py" "$OUT/quads.json" "$ROOT/src/main/resources/assets/hogwartsmagic/textures/entity" "$OUT"
