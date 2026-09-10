#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-/Users/sheng/Downloads/RayNeo_AI_1.0.1.apk}"
EXPECTED="9149fb0cf41c3007a7aea912657377cc06e719b4c893027087cf5d1097fb2510"
BUILD="$ROOT/enhancer/build"
DECODED="$BUILD/decoded"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"
OUTPUT="$BUILD/output"
APKTOOL="$ROOT/tools/apktool.jar"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
TOOLS="$SDK/build-tools/36.1.0"
KEYSTORE="${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"

[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
[[ -f "$APKTOOL" ]] || { echo "apktool not found: $APKTOOL" >&2; exit 1; }
[[ -f "$ANDROID_JAR" ]] || { echo "android.jar not found: $ANDROID_JAR" >&2; exit 1; }
[[ -f "$KEYSTORE" ]] || { echo "debug keystore not found: $KEYSTORE" >&2; exit 1; }

ACTUAL="$(shasum -a 256 "$APK" | awk '{print $1}')"
[[ "$ACTUAL" == "$EXPECTED" ]] || { echo "Unsupported APK SHA-256: $ACTUAL" >&2; exit 1; }

rm -rf "$BUILD"
mkdir -p "$CLASSES" "$DEX" "$OUTPUT"
java -jar "$APKTOOL" d -f -s -p "$BUILD/framework" "$APK" -o "$DECODED"
node "$ROOT/enhancer/patch-manifest.mjs" "$DECODED/AndroidManifest.xml"
mkdir -p "$DECODED/res/xml"
cp "$ROOT/enhancer/res/xml/turbo_network_security_config.xml" "$DECODED/res/xml/turbo_network_security_config.xml"

find "$ROOT/enhancer/src" -name '*.java' -print0 | xargs -0 javac \
  -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$CLASSES"
jar --create --file "$BUILD/injector.jar" -C "$CLASSES" .
"$TOOLS/d8" --min-api 29 --output "$DEX" "$BUILD/injector.jar"
cp "$DEX/classes.dex" "$DECODED/classes4.dex"

java -jar "$APKTOOL" b -p "$BUILD/framework" "$DECODED" -o "$BUILD/unsigned.apk"
"$TOOLS/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out "$OUTPUT/RayNeo_AI_1.0.1-turbo-debug.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$OUTPUT/RayNeo_AI_1.0.1-turbo-debug.apk"
shasum -a 256 "$OUTPUT/RayNeo_AI_1.0.1-turbo-debug.apk"
