#!/usr/bin/env bash
# Uji logika murni Java (tanpa Android SDK).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
S=app/src/main/java/id/ivan/dompet
javac -encoding UTF-8 -d "$OUT" $S/Fmt.java $S/Model.java $S/Analytics.java $S/Forecast.java $S/Simulator.java tests/LogicTest.java tests/ForecastTest.java tests/V4Test.java
java -cp "$OUT" id.ivan.dompet.LogicTest
java -cp "$OUT" id.ivan.dompet.ForecastTest
java -cp "$OUT" id.ivan.dompet.V4Test
rm -rf "$OUT"
