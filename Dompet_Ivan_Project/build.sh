#!/usr/bin/env bash
# Build Dompet Ivan tanpa Gradle: javac -> dx -> aapt2 -> zipalign -> apksigner.
# Kebutuhan (Ubuntu/Debian): apt install openjdk-17-jdk aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
# atau Android SDK resmi (ANDROID_JAR ke platforms/android-XX/android.jar; DX boleh diganti d8).
# Catatan: kode sengaja tanpa lambda (dx tidak melakukan desugaring) dan dikompilasi terhadap API 23;
# API lebih baru dipanggil lewat refleksi (lihat Notifier.java).
set -euo pipefail
cd "$(dirname "$0")"
: "${KEY_PASSWORD:?Set KEY_PASSWORD, mis. KEY_PASSWORD=\"\$(cat signing-password.txt)\"}"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX="${DX:-dalvik-exchange}"
OUT="${OUT:-Dompet_Ivan_v4.0.apk}"
rm -rf build && mkdir -p build/gen build/classes build/res
aapt2 compile --dir app/src/main/res -o build/res/res.zip
aapt2 link -o build/unsigned.apk -I "$ANDROID_JAR" --manifest app/src/main/AndroidManifest.xml \
  --java build/gen --min-sdk-version 23 --target-sdk-version 34 --version-code 6 --version-name 4.0 build/res/res.zip
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -Xlint:deprecation -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d build/classes $(find build/gen app/src/main/java -name '*.java')
"$DX" --dex --min-sdk-version=23 --output=build/classes.dex build/classes
(cd build && zip -q -u unsigned.apk classes.dex)
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks dompet-release.keystore --ks-key-alias dompet --ks-pass "pass:$KEY_PASSWORD" --key-pass "pass:$KEY_PASSWORD" --out "$OUT" build/aligned.apk
apksigner verify --verbose "$OUT" | head -5
echo "OK -> $OUT"
