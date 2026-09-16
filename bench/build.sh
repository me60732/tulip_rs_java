#!/bin/sh
# Build the Java benchmark suite. Requires the indicator classes to be
# built first (../build.sh) and the jars in bench/lib/ (see README).
set -e
cd "$(dirname "$0")"

for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" /home/mark/opt/jdk-2[5-9]* /usr/lib/jvm/java-2[5-9]-openjdk-*; do
    if [ -n "$c" ] && [ -x "$c/bin/javac" ]; then
        JAVAC="$c/bin/javac"
        break
    fi
done
if [ -z "$JAVAC" ]; then
    echo "no javac found; set TULIP_JAVA_HOME to a JDK 22+ installation" >&2
    exit 1
fi

[ -d ../out ] || { echo "run ../build.sh first (indicator classes)" >&2; exit 1; }

"$JAVAC" -cp "../out:lib/ta4j-core-0.19.jar:lib/commons-math3.jar:lib/gson.jar:lib/slf4j-api.jar:lib/postgresql.jar" \
    -d out $(find src -name '*.java')
echo "bench build ok -> out/"
