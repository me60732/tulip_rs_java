#!/bin/sh
# Compile the Java bindings + examples into out/. Requires JDK 22+ (the FFM
# API in java.lang.foreign is final from 22). Set TULIP_JAVA_HOME to pick a
# specific JDK; otherwise JAVA_HOME or a JDK 25+ on PATH is used.
set -e
cd "$(dirname "$0")"

for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" /home/mark/opt/jdk-2[5-9]* /usr/lib/jvm/java-2[5-9]-openjdk-*; do
    if [ -n "$c" ] && [ -x "$c/bin/javac" ]; then
        JAVAC="$c/bin/javac"
        break
    fi
done
if [ -z "$JAVAC" ] && command -v javac >/dev/null 2>&1; then
    JAVAC=javac
fi
if [ -z "$JAVAC" ]; then
    echo "no javac found; set TULIP_JAVA_HOME to a JDK 22+ installation" >&2
    exit 1
fi

"$JAVAC" -Xlint:all -d out $(find src -name '*.java')
echo "build ok -> out/"
