#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JSON_JAR="${JSON_JAR:-$(find "$HOME/.gradle/caches/modules-2/files-2.1/org.json/json" -name 'json-*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -1)}"
[[ -f "$JSON_JAR" ]] || { echo 'Set JSON_JAR to an org.json jar' >&2; exit 1; }
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
find "$ROOT/enhancer/test/stubs" -name '*.java' > "$OUT/sources"
for name in DemoWire AssistantWire SpeechBuffer NotificationWire NotificationResultFormatter VoiceCommandRouter DemoHooks AgentPlatform AgentSessionClient PaseoAgentSessionClient ManagedAgentClient AgentPlatformAssembler DemoController DebugMessageSender; do echo "$ROOT/enhancer/src/io/turboio/enhanced/$name.java" >> "$OUT/sources"; done
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
mkdir -p "$OUT/notification-format-classes"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/notification-format-classes" \
  "$ROOT/enhancer/src/io/turboio/enhanced/MinimaxResponsesClient.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/NotificationResultFormatter.java" \
  "$ROOT/enhancer/test/NotificationResultFormatterTest.java"
java -cp "$OUT/notification-format-classes:$JSON_JAR" io.turboio.enhanced.NotificationResultFormatterTest
mkdir -p "$OUT/paseo-classes"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/paseo-classes" \
  "$ROOT/enhancer/test/stubs/android/content/Context.java" \
  "$ROOT/enhancer/test/stubs/android/util/Base64.java" \
  "$ROOT/enhancer/test/stubs/io/turboio/enhanced/PaseoConfigStore.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/PaseoWebSocket.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/PaseoClient.java" \
  "$ROOT/enhancer/test/PaseoClientTest.java"
java -cp "$OUT/paseo-classes:$JSON_JAR" io.turboio.enhanced.PaseoClientTest
mkdir -p "$OUT/managed-agent-classes"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/managed-agent-classes" \
  "$ROOT/enhancer/src/io/turboio/enhanced/AgentPlatform.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/AgentSessionClient.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/ManagedAgentClient.java" \
  "$ROOT/enhancer/test/ManagedAgentClientTest.java"
java -cp "$OUT/managed-agent-classes:$JSON_JAR" io.turboio.enhanced.ManagedAgentClientTest
mkdir -p "$OUT/agent-platform-classes"
javac -encoding UTF-8 -cp "$JSON_JAR" -d "$OUT/agent-platform-classes" \
  "$ROOT/enhancer/test/stubs/android/content/Context.java" \
  "$ROOT/enhancer/test/stubs/io/turboio/enhanced/PaseoConfigStore.java" \
  "$ROOT/enhancer/test/stubs/io/turboio/enhanced/PaseoClient.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/AgentPlatform.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/AgentSessionClient.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/PaseoAgentSessionClient.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/ManagedAgentClient.java" \
  "$ROOT/enhancer/src/io/turboio/enhanced/AgentPlatformAssembler.java" \
  "$ROOT/enhancer/test/AgentPlatformAssemblerTest.java"
java -cp "$OUT/agent-platform-classes:$JSON_JAR" io.turboio.enhanced.AgentPlatformAssemblerTest
node "$ROOT/enhancer/test/patch.test.mjs"
find "$ROOT/enhancer/test/adapter-stubs" -name '*.java' > "$OUT/adapter-sources"
echo "$ROOT/enhancer/src/io/turboio/enhanced/VendorTransport.java" >> "$OUT/adapter-sources"
echo "$ROOT/enhancer/test/AdapterTest.java" >> "$OUT/adapter-sources"
javac -encoding UTF-8 -d "$OUT/adapter-classes" @"$OUT/adapter-sources"
java -cp "$OUT/adapter-classes" io.turboio.enhanced.AdapterTest
# 1.0.5 keeps the reflected ABI but moves the audio arbiter from D7.K to D7.L.
# Exercise both layouts so the adapter cannot accidentally depend on D7.K.
cp -R "$ROOT/enhancer/test/adapter-stubs" "$OUT/adapter-105"
python3 - "$OUT/adapter-105" "$ROOT/enhancer/test/AdapterTest.java" "$OUT/AdapterTest.java" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
p = root / 'D7/K.java'
p.rename(root / 'D7/L.java')
for p in root.rglob('*.java'):
    p.write_text(p.read_text().replace('D7.K', 'D7.L').replace('class K ', 'class L '))
Path(sys.argv[3]).write_text(Path(sys.argv[2]).read_text().replace('D7.K', 'D7.L'))
PY
find "$OUT/adapter-105" -name '*.java' > "$OUT/adapter-105-sources"
echo "$ROOT/enhancer/src/io/turboio/enhanced/VendorTransport.java" >> "$OUT/adapter-105-sources"
echo "$OUT/AdapterTest.java" >> "$OUT/adapter-105-sources"
javac -encoding UTF-8 -d "$OUT/adapter-105-classes" @"$OUT/adapter-105-sources"
java -cp "$OUT/adapter-105-classes" io.turboio.enhanced.AdapterTest
javac -encoding UTF-8 -d "$OUT/journal-classes" "$ROOT/enhancer/src/io/turboio/enhanced/TraceJournal.java" "$ROOT/enhancer/test/TraceJournalTest.java"
java -cp "$OUT/journal-classes" io.turboio.enhanced.TraceJournalTest
