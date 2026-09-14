#!/bin/sh
# Run an example, e.g. ./run.sh AdxExample
set -e
cd "$(dirname "$0")"

for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" /home/mark/opt/jdk-2[5-9]* /usr/lib/jvm/java-2[5-9]-openjdk-*; do
    if [ -n "$c" ] && [ -x "$c/bin/java" ]; then
        JAVA="$c/bin/java"
        break
    fi
done
if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1; then
    JAVA=java
fi
if [ -z "$JAVA" ]; then
    echo "no java found; set TULIP_JAVA_HOME to a JDK 22+ installation" >&2
    exit 1
fi

exec "$JAVA" --enable-native-access=ALL-UNNAMED -cp out "org.tuliprs.examples.$1"
