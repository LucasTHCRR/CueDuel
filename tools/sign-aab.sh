#!/bin/bash
# Signiert die mit "rbuild :android:aab" gebaute AAB (Upload-Key für Google Play) nach dist/
set -euo pipefail
cd "$(dirname "$0")/.."
KS_DIR=${CUEDUEL_KEYSTORE_DIR:-/media/lucasthcr/Daten/Tools/keystores}
VERSION=$(grep -oP "version = '\K[^']+" build.gradle)
IN="android/build/libs/CueDuel-$VERSION-unsigned.aab"
OUT="dist/CueDuel-$VERSION.aab"
ALIAS=$(grep -oP '^alias=\K.*' "$KS_DIR/cueduel-release.txt")
PASS=$(grep -oP '^password=\K.*' "$KS_DIR/cueduel-release.txt")
mkdir -p dist
cp "$IN" "$OUT"
jarsigner -keystore "$KS_DIR/cueduel-release.jks" -storepass "$PASS" -keypass "$PASS" "$OUT" "$ALIAS" > /dev/null
jarsigner -verify "$OUT" | tail -1
ls -la "$OUT"
