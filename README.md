# tulip-rs-java

Java bindings for [TulipRS](https://github.com/me60732/tulip_rs) via the
Panama FFM API (`java.lang.foreign`) on top of the shared
[`tulip_rs_ffi`](../tulip_rs_ffi) native library. No JNI, no code
generation: one downcall bundle per indicator, resolved at runtime from the
C symbol prefix.

## Requirements

- **JDK 22+** (the FFM API is final from 22; tested on Temurin 25). No
  `--enable-preview` needed.
- **The native `libtulip_rs_ffi` comes with the dependency**: when you
  consume `io.github.me60732:tulip-rs-java` from Maven Central, an
  OS-activated profile in the published pom pulls the matching platform
  classifier jar (`linux-amd64`, `linux-arm64`, `darwin-amd64`,
  `darwin-arm64`) containing the portable-baseline cdylib
  (x86-64-v3 / ARMv8) — extracted to a temp file at first load. No extra
  setup on Linux/macOS.
- `./bootstrap.sh` is optional and gives the other two things, mirroring the
  Go/Node/Python bindings:
  - `--prebuilt` — same baseline binary, installed into `ffi/lib/`
  - `--source` — clone + `cargo build` with `-C target-cpu=native` (the
    runner's CPU, all ISA extensions; anything you build here also wins the
    loader search below). **Required on Windows**, where FFM needs a `.dll`
    the ffi releases don't ship as prebuilds.
- Loader search order (first hit wins):
  `-Dtulip.ffi.library` / `TULIP_RS_FFI_LIBRARY` → `ffi/lib/` walking up
  from the CWD → `../tulip_rs_ffi/target/{release,debug}` → embedded
  classifier jar.

## Build & run

```sh
./bootstrap.sh --prebuilt   # or: ./bootstrap.sh --source
./build.sh                  # -> out/ (library) + out-examples/ (JDK 22+)
./run.sh AdxExample         # org.tuliprs.examples.AdxExample
./test.sh                   # build + run all examples (integration suite)
```

## API shape

Every indicator exposes the same surface (mirroring the Go/Node bindings):

```java
import org.tuliprs.*;
import org.tuliprs.indicators.Adx;

double[] options = {14.0};
double[][] inputs = {high, low, close};          // INPUTS series, equal length

// Metadata + constants (IDs are the FFI's FNV-1a32 constants, never re-hashed)
Info info = Adx.info();                          // names, types, display groups
long need = Adx.minData(options);                // minimum bars
assert Adx.INPUTS == 3 && Adx.OPTIONS == 1 && Adx.ID == 0x1f38e660L;

// Compute: outputs are zero-copy views; state continues the stream
Outcome oc = Adx.indicator(inputs, options, new boolean[] {true, true, true});
try (Result res = oc.result(); State st = oc.state()) {
    double[] adx = res.toDoubleArray(0);         // or res.row(i) as a MemorySegment
    Result next = st.batch(moreBars);            // streaming continuation

    // Persistence: copy-then-free blob; blobs are self-describing
    byte[] blob = st.serialize(Format.BINCODE);
    State restored = Adx.deserializeState(blob);
    State snapshot = st.duplicate();
}

// SIMD: N assets or N option sets in one pass (N = 2, 4, 8, 16)
try (SimdResult sim = Adx.simdByAssets(assets, options, null)) { ... }
try (SimdResult sim = Adx.simdByOptions(inputs, new double[][] {{3}, {5}, {7}, {10}}, null)) { ... }
```

**Exception — candlestick:** pattern detection has no SIMD variants, emits
CSR-packed pattern ids instead of f64 rows (`CandleResult.patterns(bar)` /
`.names(bar)`), and takes a forecast filter
(`CandlePattern.FORECAST_NONE` and friends) instead of optional-output flags. See
`examples/org/tuliprs/examples/CandlestickExample.java`.

## Releasing (Maven Central)

Java releases are lockstep with the ffi: the workflow packages
`tulip_rs_ffi` release `v<version>` assets into the classifier jars, so the
ffi tag must exist before tagging this repo (as it does for 0.2.9).

One-time setup (already done unless noted):

- Signing key `287D9C78DB2F314848ACFECA899BB7F516638DD6` (uid
  `me60732@gmail.com`) uploaded to `keys.openpgp.org` and
  `keyserver.ubuntu.com`; click the verification email from
  keys.openpgp.org once.
- Central Portal namespace `io.github.me60732` verified (GitHub login).
- Repo secrets (Settings → Secrets and variables → Actions):
  - `CENTRAL_USERNAME` / `CENTRAL_PASSWORD` — the Portal *Create Token* pair
  - `GPG_PRIVATE_KEY` — output of
    `gpg --armor --export-secret-keys 287D9C78DB2F314848ACFECA899BB7F516638DD6`
    (asks for the key passphrase; paste the whole `-----BEGIN PGP PRIVATE
    KEY BLOCK-----…END…` text)
  - `GPG_PASSPHRASE` — that same passphrase

Cut a release:

1. bump `<version>` in `pom.xml`, commit it
2. tag + push:

```sh
git tag v0.2.9 && git push origin main --tags
```

The release workflow then: asserts tag == pom version, runs the 95-example
integration suite, packages the `tulip_rs_ffi` binaries of the SAME version
into the platform classifier jars (the matching ffi release must exist —
version lockstep), signs all artifacts, deploys to Central, and auto-publishes
once Central's validation finishes (~10–20 min).

Manual one-off from this machine instead: `mvn deploy -Prelease` with the
Portal token + `gpg.passphrase` present in `~/.m2/settings.xml` (servers
`central` and `gpg.passphrase`).

## Memory model

Outputs and states are Rust-allocated. Per the repo-root
[`bindings_memory_model.md`](../bindings_memory_model.md):

- `Result` / `SimdResult` hand out read-only `MemorySegment` **views** valid
  while open; `close()` releases the buffers (Cleaner is a leak backstop, not
  the mechanism — use try-with-resources).
- `State` is an explicit closeable handle (its lifetime spans calls; no GC can
  own it). All `close()`s are idempotent.
- `SimdResult.close()` frees every lane state first, then the SIMD buffers
  (contractual order).
- `Info` and serialized blobs are plain Java objects/`byte[]` — nothing to
  free.

## Layout

```
src/org/tuliprs/            core: Tulip (FFM plumbing), Native (per-indicator engine),
                            Result, State, SimdResult, Info, Outcome, Format, IndicatorException
src/org/tuliprs/indicators/ one facade class per indicator (constants + delegation)
examples/org/tuliprs/examples/ one verified example per indicator
examples/org/tuliprs/demo/    shared example harness (synthetic series, comparisons, checks)
bench/                      tulip-rs-java vs ta4j benchmark suite (own build/run scripts)
ffi/                        generated by bootstrap.sh — prebuilt native + headers (gitignored)
```
