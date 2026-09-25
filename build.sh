#!/bin/sh
# Compile the Java bindings library (src/ -> out/) and the example programs
# (examples/ -> out-examples/). Requires JDK 22+ (the FFM API in
# java.lang.foreign is final from 22); JAVA_HOME, TULIP_JAVA_HOME or a
# javac on PATH is used, whichever resolves first.
set -e
cd "$(dirname "$0")"

# Candidate JDK locations. The FFM API (java.lang.foreign) is only final from
# JDK 22; on JDK 20/21 it is a *preview* API and these sources fail to compile
# with hundreds of "preview API is disabled" errors. Order the 22+ globs BEFORE
# the loose /usr/lib/jvm/java-* fallback so a JDK 21 can never win (java-21 sorts
# ahead of java-25). Mirrors bench/build.sh's /home/mark/opt/jdk-* location.
for c in "$TULIP_JAVA_HOME" "$JAVA_HOME" \
         /home/mark/opt/jdk-2[2-9]* \
         /usr/lib/jvm/java-2[2-9]-openjdk-* \
         /usr/lib/jvm/java-*; do
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

# Reject a pre-22 javac explicitly instead of emitting 449 preview-API errors.
# FFM is final from 22, so accept 22-29 and any 30+; reject everything else
# (1.x, 9-21, or an unparseable version string).
javac_major="$("$JAVAC" -version 2>&1 | sed -E 's/javac ([0-9]+).*/\1/')"
if [ "$javac_major" -ge 22 ] 2>/dev/null; then
    :
else
    echo "javac $("$JAVAC" -version 2>&1 | head -1) is too old: the FFM API (java.lang.foreign) requires JDK 22+" >&2
    echo "set TULIP_JAVA_HOME or JAVA_HOME to a JDK 22+ installation" >&2
    exit 1
fi

rm -rf out out-examples
"$JAVAC" -Xlint:all -d out $(find src -name '*.java')
"$JAVAC" -Xlint:all -cp out -d out-examples $(find examples -name '*.java')
echo "build ok -> out/ (library), out-examples/ (examples + demo harness)"
