#!/bin/sh
# Run an example, e.g. ./run.sh AdxExample   (needs ./bootstrap.sh + ./build.sh)
set -e
cd "$(dirname "$0")"

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

exec "$JAVA" --enable-native-access=ALL-UNNAMED -cp "out:out-examples" "org.tuliprs.examples.$1"
