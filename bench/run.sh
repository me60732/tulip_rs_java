#!/bin/sh
# Run the benchmark suite. Env knobs work as CLI-style prefixes, e.g.:
#   BENCH_ONLY=ema BENCH_REPEAT=3 ./run.sh
#   BENCHMARK_LOG_TO_DB=1 ./run.sh
set -e
cd "$(dirname "$0")"

for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" /home/mark/opt/jdk-2[5-9]* /usr/lib/jvm/java-2[5-9]-openjdk-*; do
    if [ -n "$c" ] && [ -x "$c/bin/java" ]; then
        JAVA="$c/bin/java"
        break
    fi
done
if [ -z "$JAVA" ]; then
    echo "no java found; set TULIP_JAVA_HOME to a JDK 22+ installation" >&2
    exit 1
fi

exec "$JAVA" --enable-native-access=ALL-UNNAMED \
    -cp "../out:out:lib/*" \
    org.tuliprs.bench.Main
