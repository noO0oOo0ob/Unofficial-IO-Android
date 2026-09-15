#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/../rayneo-local-samples/RayNeo_AI_1.0.4_195.apk}"
EXPECTED="ef2e7dd346ca478e13d0f3f1bf31fa61412e44fb9b4ca9cf0d3e864ac608584b"
BUILD="$ROOT/enhancer/build"
DECODED="$BUILD/decoded"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"
OUTPUT="$BUILD/output"
APKTOOL="$ROOT/tools/apktool.jar"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
TOOLS="$SDK/build-tools/36.0.0"
KEYSTORE="${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"

[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
[[ -f "$APKTOOL" ]] || { echo "apktool not found: $APKTOOL" >&2; exit 1; }
[[ -f "$ANDROID_JAR" ]] || { echo "android.jar not found: $ANDROID_JAR" >&2; exit 1; }
[[ -f "$KEYSTORE" ]] || { echo "debug keystore not found: $KEYSTORE" >&2; exit 1; }

ACTUAL="$(shasum -a 256 "$APK" | awk '{print $1}')"
[[ "$ACTUAL" == "$EXPECTED" ]] || { echo "Unsupported APK SHA-256: $ACTUAL" >&2; exit 1; }

rm -rf "$BUILD"
mkdir -p "$CLASSES" "$DEX" "$OUTPUT"
java -jar "$APKTOOL" d -f -p "$BUILD/framework" "$APK" -o "$DECODED"
node "$ROOT/enhancer/patch-manifest.mjs" "$DECODED/AndroidManifest.xml"
node "$ROOT/enhancer/patch-assistant.mjs" "$DECODED"
# Only classes2 is patched. Keep the other official DEX files byte-for-byte.
rm -rf "$DECODED/smali" "$DECODED/smali_classes3"
unzip -p "$APK" classes.dex > "$DECODED/classes.dex"
unzip -p "$APK" classes3.dex > "$DECODED/classes3.dex"

# The previous diagnostic console/runtime and Codex bridge are intentionally not packaged.
SOURCES=()
for name in EnhancedActivity DebugMessageSender ApiKeyStore MinimaxResponsesClient NotificationResultFormatter PaseoConfigStore PaseoWebSocket PaseoClient AgentPlatform AgentSessionClient PaseoAgentSessionClient ManagedAgentClient AgentPlatformAssembler AgentPlatformConfigStore VoiceCommandRouter MinimaxIntentRouter AssistantWire DemoWire SpeechBuffer MinimaxAsr VendorTransport DemoHooks DemoController NotificationWire AmapNavigationText AmapNavigationBridge AmapNavigationListenerService DemoTrace TraceJournal; do
  SOURCES+=("$ROOT/enhancer/src/io/turboio/enhanced/$name.java")
done
javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$CLASSES" "${SOURCES[@]}"
jar --create --file "$BUILD/injector.jar" -C "$CLASSES" .
"$TOOLS/d8" --min-api 29 --output "$DEX" "$BUILD/injector.jar"
cp "$DEX/classes.dex" "$DECODED/classes4.dex"

java -jar "$APKTOOL" b -p "$BUILD/framework" "$DECODED" -o "$BUILD/unsigned.apk"
python3 "$ROOT/enhancer/assemble-preserving.py" "$APK" "$BUILD/unsigned.apk" "$BUILD/preserved.apk" "$TOOLS/aapt2"
"$TOOLS/zipalign" -f -P 16 4 "$BUILD/preserved.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUTPUT/RayNeo_AI_1.0.4_195-turbo-debug.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$OUTPUT/RayNeo_AI_1.0.4_195-turbo-debug.apk"
shasum -a 256 "$OUTPUT/RayNeo_AI_1.0.4_195-turbo-debug.apk"

python3 "$ROOT/enhancer/verify-apk.py" "$APK" "$OUTPUT/RayNeo_AI_1.0.4_195-turbo-debug.apk" "$TOOLS/aapt2" "$OUTPUT/verification.json"
