#!/usr/bin/env bash
# =============================================================================
# Installs the native library the Java (Panama FFM) bindings load at runtime.
# After running either mode once, `./build.sh && ./run.sh AdxExample` just
# works: the loader searches  -Dtulip.ffi.library / TULIP_RS_FFI_LIBRARY,
# then ffi/lib (prebuilt, walking up from the CWD), then the sibling
# ../tulip_rs_ffi/target/{release,debug} (source build).
#
# Usage:
#   ./bootstrap.sh --source   [REF]   # the fast path (full native CPU
#                                     # tuning): clone ../tulip_rs_ffi at REF
#                                     # if missing, then cargo build --release
#   ./bootstrap.sh --prebuilt [REF]   # no Rust toolchain needed: download the
#                                     # GitHub-release cdylib for this
#                                     # OS/arch (x86-64-v3 / aarch64 baseline:
#                                     # portable, not CPU-tuned)
#   ./bootstrap.sh --help
#
# ffi/ is entirely GENERATED and gitignored (unlike the Go binding there are
# no headers to sync: FFM resolves symbols at runtime, no C compilation).
# Windows --prebuilt requires REF >= v0.2.10 (the first ffi release whose
# windows asset carries the standalone DLL; older ones ship only the .a —
# pass a newer REF or use --source there).
#
# REF defaults to $FFI_REF or the pinned FFI_DEFAULT_REF below.
# =============================================================================
set -euo pipefail

REPO="me60732/tulip_rs_ffi"
FFI_DEFAULT_REF="v0.2.10"

REPO_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SIBLING="$REPO_DIR/../tulip_rs_ffi"

MODE="${1:---help}"
REF="${2:-${FFI_REF:-$FFI_DEFAULT_REF}}"

case "$MODE" in
  --source)
    if [ ! -d "$SIBLING/.git" ]; then
      echo "==> cloning $REPO@$REF into $SIBLING"
      git clone --depth 1 --branch "$REF" "https://github.com/$REPO" "$SIBLING" \
        || git clone --depth 1 --branch "$REF" "git@github.com:$REPO" "$SIBLING"
    else
      echo "==> using existing sibling checkout: $SIBLING"
    fi
    echo "==> cargo build --release (native CPU tuning from the ffi repo)"
    # MUST cd into the ffi repo: cargo reads .cargo/config.toml by walking up
    # from the CWD, not from --manifest-path. Invoked from elsewhere, the
    # `-C target-cpu=native` rustflags are silently dropped and the whole
    # build degrades to baseline x86-64 codegen.
    (cd "$SIBLING" && cargo build --release)
    echo "==> done. Verify: ./build.sh && ./run.sh AdxExample"
    ;;

  --prebuilt)
    case "$(uname -s)" in
      Linux)    OS=linux ;;
      Darwin)   OS=darwin ;;
      MINGW*|MSYS*) OS=windows ;;   # git-bash / msys2 on Windows
      *) echo "error: no prebuilt native for $(uname -s) — use --source" >&2; exit 1 ;;
    esac
    case "$(uname -m)" in
      x86_64)     ARCH=amd64 ;;
      arm64|aarch64) ARCH=arm64 ;;
      *) echo "error: no prebuilt native for $(uname -m) — use --source" >&2; exit 1 ;;
    esac
    NAME="tulip_rs_ffi-${OS}-${ARCH}.tar.gz"
    URL="https://github.com/$REPO/releases/download/$REF/$NAME"
    TMP="$(mktemp)"
    trap 'rm -f "$TMP"' EXIT
    echo "==> downloading $URL"
    if ! curl -fsSL "$URL" -o "$TMP"; then
      echo "error: no prebuilt '$NAME' for $REF available yet." >&2
      echo "       prebuilds exist from the first tagged ffi release onward;" >&2
      echo "       use --source (or a newer REF) until then." >&2
      exit 1
    fi
    mkdir -p "$REPO_DIR/ffi"
    # tarball layout: ./lib/libtulip_rs_ffi.{so,dylib} (+ ./include/ we ignore);
    # the windows tarball carries libtulip_rs_ffi.a AND tulip_rs_ffi.dll.
    tar xzf "$TMP" -C "$REPO_DIR/ffi"
    if [ "$OS" = windows ] && [ ! -f "$REPO_DIR/ffi/lib/tulip_rs_ffi.dll" ]; then
      echo "error: $REF windows asset has no DLL (ffi ships one from v0.2.10 on)." >&2
      echo "       pass a newer REF: ./bootstrap.sh --prebuilt v0.2.10, or use --source." >&2
      rm -rf "$REPO_DIR/ffi/lib" "$REPO_DIR/ffi/include"
      exit 1
    fi
    echo "==> installed $REPO_DIR/ffi/lib (prebuilt $REF, $OS/$ARCH)"
    echo "==> verify: ./build.sh && ./run.sh AdxExample"
    ;;

  --help | -h | *)
    sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
