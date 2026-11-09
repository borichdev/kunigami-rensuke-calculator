#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

ANDROID_JAR="/home/borichdev/Android/Sdk/platforms/android-34/android.jar"
BUILD_TOOLS="/home/borichdev/Android/Sdk/build-tools/34.0.0"
KEYSTORE="$HOME/.android/debug.keystore"
APK_OUT="kunigami-calc.apk"

echo "=== 1. Clean & Prepare ==="
rm -rf bin compiled_res.zip base.apk classes.dex app-aligned.apk "$APK_OUT" "$APK_OUT.idsig"
mkdir -p bin

echo "=== 2. Compile Resources ==="
"$BUILD_TOOLS/aapt2" compile --dir res -o compiled_res.zip

echo "=== 3. Link APK with Assets ==="
"$BUILD_TOOLS/aapt2" link \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -A assets \
    -0 .k3d \
    -0 .mp3 \
    compiled_res.zip \
    -o base.apk

echo "=== 4. Compile Java ==="
javac -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR" -d bin $(find src -name "*.java")

echo "=== 5. Dex Bytecode ==="
"$BUILD_TOOLS/d8" --min-api 26 --output . --lib "$ANDROID_JAR" $(find bin -name "*.class")

echo "=== 6. Package DEX into APK ==="
zip -u base.apk classes.dex

echo "=== 7. Zipalign ==="
"$BUILD_TOOLS/zipalign" -f -p 4 base.apk app-aligned.apk

echo "=== 8. Sign APK ==="
"$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --ks-key-alias androiddebugkey \
    --key-pass pass:android \
    --out "$APK_OUT" \
    app-aligned.apk

rm -f base.apk app-aligned.apk classes.dex compiled_res.zip
echo "=== Build Complete: $(ls -lh "$APK_OUT" | awk '{print $5, $9}') ==="
