#!/bin/bash
# Build the Bangla Browser APK without Gradle / Android Studio.
set -e
cd "$(dirname "$0")"

SDK="$PWD/sdk"
AJ="$SDK/android.jar"
R8="$SDK/r8.jar"
OUT="$PWD/build"
NAME="BanglaBrowser-1.1.01.apk"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> 1/6 resources"
aapt package -f -m \
  -J "$OUT/gen" \
  -M AndroidManifest.xml \
  -S res \
  -A assets \
  -I "$AJ" \
  --min-sdk-version 21 --target-sdk-version 34 \
  -F "$OUT/base.apk"

echo "==> 2/6 compile java"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -source 11 -target 11 -classpath "$AJ" -d "$OUT/classes" @"$OUT/sources.txt" 2>&1 | grep -v "bootstrap class path" || true

echo "==> 3/6 dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$R8" com.android.tools.r8.D8 \
  --min-api 21 --lib "$AJ" --output "$OUT/dex" @"$OUT/classes.txt" 2>&1 | grep -v "^Warning" || true

echo "==> 4/6 pack"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "==> 5/6 sign"
if [ ! -f keystore.jks ]; then
  keytool -genkeypair -v -keystore keystore.jks -storepass bangla123 \
    -keypass bangla123 -alias bangla -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Bangla Browser, O=Mobile, C=BD" > /dev/null 2>&1
fi
apksigner sign --ks keystore.jks --ks-pass pass:bangla123 --key-pass pass:bangla123 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$NAME" "$OUT/aligned.apk"

echo "==> 6/6 verify"
apksigner verify --print-certs "$NAME" | head -5
ls -lh "$NAME"
