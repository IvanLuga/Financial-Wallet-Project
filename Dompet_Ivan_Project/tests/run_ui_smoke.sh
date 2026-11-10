#!/usr/bin/env bash
# Uji asap UI di JVM (tanpa HP/emulator). Butuh JDK 8 (Robolectric 3.8), Maven, aapt2, dan build.sh sudah dijalankan
# (untuk build/gen/R.java). Pustaka Robolectric diunduh sekali dari Maven Central ke tests/ui/lib (tidak di-commit).
set -euo pipefail
cd "$(dirname "$0")/.."
JAVA8="${JAVA8:-/usr/lib/jvm/java-8-openjdk-amd64}"
LIB=tests/ui/lib
if [ ! -d "$LIB" ]; then
  T=$(mktemp -d)
  cat > "$T/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>x</groupId><artifactId>robo</artifactId><version>1</version><dependencies>
<dependency><groupId>org.robolectric</groupId><artifactId>robolectric</artifactId><version>3.8</version></dependency>
<dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version></dependency>
<dependency><groupId>org.robolectric</groupId><artifactId>android-all</artifactId><version>8.1.0-robolectric-4611349</version></dependency>
</dependencies></project>
POM
  (cd "$T" && mvn -q -B dependency:copy-dependencies -DoutputDirectory=lib) && mv "$T/lib" "$LIB"
fi
[ -f build/gen/id/ivan/dompet/R.java ] || { echo "Jalankan build.sh dulu (butuh build/gen/R.java)"; exit 1; }
OUT=$(mktemp -d)
CP=$(ls "$LIB"/*.jar | tr '\n' ':')
unset JAVA_TOOL_OPTIONS
"$JAVA8/bin/javac" -nowarn -encoding UTF-8 -cp "$CP" -d "$OUT" $(find build/gen app/src/main/java -name '*.java') tests/ui/SmokeTest.java 2>&1 | grep -v "^Note:" || true
"$JAVA8/bin/java" -Drobolectric.offline=true -Drobolectric.dependency.dir="$LIB" -cp "$OUT:$CP" org.junit.runner.JUnitCore id.ivan.dompet.SmokeTest
rm -rf "$OUT"
