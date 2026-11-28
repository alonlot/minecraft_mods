#!/usr/bin/env bash
# Compare speech settings (model, beam, gain, prompt) on the voice lines and any practice takes. See BenchVoice.java.
# Args: MODEL_DIR [takes=DIR] VARIANT... e.g. model=base,beam=1,gain=1,prompt=all,cond=quiet
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$(find ~/.gradle/caches/modules-2 -name 'whisper-jni-1.7.1.jar' | head -1)"
OUT="${TMPDIR:-${TEMP:-/tmp}}/spell_voice_bench"
mkdir -p "$OUT"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";"; JAR="$(cygpath -w "$JAR")"; OUT="$(cygpath -w "$OUT")";; esac
V="$ROOT/src/main/java/com/hogwartsmagic/spell/voice"
javac -d "$OUT" -cp "$JAR" "$V/IncantationMatcher.java" "$V/VoiceAudio.java" \
    "$ROOT/tools/spell_voice/CheckSpellVoice.java" "$ROOT/tools/spell_voice/BenchVoice.java"
java -cp "$OUT$SEP$JAR" BenchVoice "$ROOT" "$@"
