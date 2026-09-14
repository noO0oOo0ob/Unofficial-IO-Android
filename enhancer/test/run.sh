#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JSON_JAR="${JSON_JAR:-$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.json/json" -name 'json-*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)}"
[[ -f "$JSON_JAR" ]] || { echo 'Set JSON_JAR to an org.json jar' >&2; exit 1; }
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
find "$ROOT/enhancer/test/stubs" -name '*.java' > "$OUT/sources"
for name in DemoWire AssistantWire SpeechBuffer NotificationWire VoiceCommandRouter DemoHooks DemoController; do echo "$ROOT/enhancer/src/io/turboio/enhanced/$name.java" >> "$OUT/sources"; done
echo "$ROOT/enhancer/test/DemoTest.java" >> "$OUT/sources"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/classes" @"$OUT/sources"
java -cp "$OUT/classes:$JSON_JAR" io.turboio.enhanced.DemoTest
mkdir -p "$OUT/amap-classes"
javac -encoding UTF-8 -d "$OUT/amap-classes" \
  "$ROOT/enhancer/src/io/turboio/enhanced/AmapNavigationText.java" \
  "$ROOT/enhancer/test/AmapNavigationTextTest.java"
java -cp "$OUT/amap-classes" io.turboio.enhanced.AmapNavigationTextTest
mkdir -p "$OUT/model-classes"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/model-classes" \
  "$ROOT/enhancer/test/stubs/android/content/Context.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/VoiceCommandRouter.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/MinimaxIntentRouter.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/MinimaxResponsesClient.java" \
  "$ROOT/enhancer/test/ModelClientTest.java"
java -cp "$OUT/model-classes:$JSON_JAR" io.turboio.enhanced.ModelClientTest
mkdir -p "$OUT/paseo-classes"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/paseo-classes" \
  "$ROOT/enhancer/test/stubs/android/content/Context.java" \
  "$ROOT/enhancer/test/stubs/android/util/Base64.java" \
  "$ROOT/enhancer/test/stubs/io/turboio/enhanced/PaseoConfigStore.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/PaseoWebSocket.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/PaseoClient.java" \
  "$ROOT/enhancer/test/PaseoClientTest.java"
java -cp "$OUT/paseo-classes:$JSON_JAR" io.turboio.enhanced.PaseoClientTest
node "$ROOT/enhancer/test/patch.test.mjs"
find "$ROOT/enhancer/test/adapter-stubs" -name '*.java' > "$OUT/adapter-sources"
echo "$ROOT/enhancer/src/io/turboio/enhanced/VendorTransport.java" >> "$OUT/adapter-sources"
echo "$ROOT/enhancer/test/AdapterTest.java" >> "$OUT/adapter-sources"
javac -encoding UTF-8 -d "$OUT/adapter-classes" @"$OUT/adapter-sources"
java -cp "$OUT/adapter-classes" io.turboio.enhanced.AdapterTest
javac -encoding UTF-8 -d "$OUT/journal-classes" "$ROOT/enhancer/src/io/turboio/enhanced/TraceJournal.java" "$ROOT/enhancer/test/TraceJournalTest.java"
java -cp "$OUT/journal-classes" io.turboio.enhanced.TraceJournalTest
