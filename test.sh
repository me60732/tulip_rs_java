#!/bin/sh
# Build everything and run every example as an integration test. Each of the
# 95 examples verifies streaming continuity, state persistence and (where the
# FFI has them) both SIMD modes against scalar re-computation, so this doubles
# as the suite's test target: `./test.sh` exits non-zero on any failure.
#
# Env: JAVA_HOME/TULIP_JAVA_HOME selects the JDK (JDK 22+ required).
set -e
cd "$(dirname "$0")"

sh build.sh

for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" /usr/lib/jvm/java-2[5-9]-openjdk-* /usr/lib/jvm/java-*; do
    if [ -n "$c" ] && [ -x "$c/bin/java" ]; then
        JAVA="$c/bin/java"
        break
    fi
done
if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1; then
    JAVA=java
fi
if [ -z "$JAVA" ]; then
    echo "no java found; set JAVA_HOME (or TULIP_JAVA_HOME) to a JDK 22+ installation" >&2
    exit 1
fi

pass=0
fail=0
mkdir -p .testlogs
for f in examples/org/tuliprs/examples/*Example.java; do
    e=$(basename "$f" .java)
    if "$JAVA" --enable-native-access=ALL-UNNAMED -cp "out:out-examples" "org.tuliprs.examples.${e}" > ".testlogs/${e}.log" 2>&1; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        echo "FAIL: $e (see .testlogs/${e}.log)"
    fi
done
echo "test.sh: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
