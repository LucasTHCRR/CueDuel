#!/bin/bash
# Signiert die mit "rbuild :android:apk" gebaute APK mit dem Release-Schlüssel nach dist/
set -euo pipefail
cd "$(dirname "$0")/.."
KS_DIR=${CUEDUEL_KEYSTORE_DIR:-/media/lucasthcr/Daten/Tools/keystores}
BT=${ANDROID_BUILD_TOOLS:-/media/lucasthcr/Daten/AndroidSdk/build-tools/36.1.0}
VERSION=$(grep -oP "version = '\K[^']+" build.gradle)
IN="android/build/libs/CueDuel-$VERSION-unsigned.apk"
OUT="dist/CueDuel-$VERSION.apk"
ALIAS=$(grep -oP '^alias=\K.*' "$KS_DIR/cueduel-release.txt")
PASS=$(grep -oP '^password=\K.*' "$KS_DIR/cueduel-release.txt")
mkdir -p dist
"$BT/zipalign" -f -p 4 "$IN" "$OUT.tmp"
"$BT/apksigner" sign --ks "$KS_DIR/cueduel-release.jks" --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$PASS" --key-pass "pass:$PASS" --out "$OUT" "$OUT.tmp"
rm -f "$OUT.tmp" "$OUT.idsig"
"$BT/apksigner" verify --print-certs "$OUT" | head -2
ls -la "$OUT"
