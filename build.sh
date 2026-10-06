#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK_DIR="${ANDROID_HOME:?ANDROID_HOME is required}"
BT="$SDK_DIR/build-tools/35.0.0"
PLATFORM="$SDK_DIR/platforms/android-35/android.jar"
rm -rf build
mkdir -p build/classes build/dex build/resources
# Test the journal as plain Java before compiling the Android activity.
javac -encoding UTF-8 -d build/classes src/com/qingsongji/diary/RecordStore.java tests/RecordStoreTest.java
java -cp build/classes RecordStoreTest
"$BT/aapt2" compile --dir res -o build/resources
"$BT/aapt2" link -o build/unsigned.apk --manifest AndroidManifest.xml -I "$PLATFORM" --min-sdk-version 26 --target-sdk-version 35 -A assets build/resources/*.flat
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM" -d build/classes src/com/qingsongji/diary/*.java
find build/classes/com -name '*.class' -print0 | xargs -0 "$BT/d8" --min-api 26 --lib "$PLATFORM" --output build/dex
(cd build/dex && zip -q ../unsigned.apk classes*.dex)
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk
printf '%s' "$APK_SIGNING_STORE_B64" | base64 --decode > build/signing.p12
"$BT/apksigner" sign --ks build/signing.p12 --ks-type PKCS12 --ks-pass env:APK_STORE_PASSWORD --out build/qingsongji-1.2.0.apk build/aligned.apk
rm -f build/signing.p12
"$BT/apksigner" verify --verbose --print-certs build/qingsongji-1.2.0.apk
"$BT/aapt2" dump badging build/qingsongji-1.2.0.apk
sha256sum build/qingsongji-1.2.0.apk > build/SHA256SUMS
