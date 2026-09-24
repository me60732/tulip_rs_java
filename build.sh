#!/bin/sh
# Compile the Java bindings library (src/ -> out/) and the example programs
# (examples/ -> out-examples/). Requires JDK 22+ (the FFM API in
# java.lang.foreign is final from 22); JAVA_HOME, TULIP_JAVA_HOME or a
# javac on PATH is used, whichever resolves first.
set -e
cd "$(dirname "$0")"

for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" /usr/lib/jvm/java-2[5-9]-openjdk-* /usr/lib/jvm/java-*; do
    if [ -n "$c" ] && [ -x "$c/bin/javac" ]; then
        JAVAC="$c/bin/javac"
        break
    fi
done
if [ -z "$JAVAC" ] && command -v javac >/dev/null 2>&1; then
    JAVAC=javac
fi
if [ -z "$JAVAC" ]; then
    echo "no javac found; set JAVA_HOME (or TULIP_JAVA_HOME) to a JDK 22+ installation" >&2
    exit 1
fi

rm -rf out out-examples
"$JAVAC" -Xlint:all -d out $(find src -name '*.java')
"$JAVAC" -Xlint:all -cp out -d out-examples $(find examples -name '*.java')
echo "build ok -> out/ (library), out-examples/ (examples + demo harness)"
