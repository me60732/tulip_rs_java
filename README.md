# tulip-rs-java

Java bindings for [TulipRS](https://github.com/me60732/tulip_rs) via the
Panama FFM API (`java.lang.foreign`) on top of the shared
[`tulip_rs_ffi`](../tulip_rs_ffi) native library. No JNI, no code
generation: one downcall bundle per indicator, resolved at runtime from the
C symbol prefix.

## Requirements

- **JDK 22+** (the FFM API is final from 22; tested on Temurin 25). No
  `--enable-preview` needed.
- A built `libtulip_rs_ffi.{so,dylib}` — installed by `./bootstrap.sh` (see
  Build & run): `--prebuilt` downloads the portable cdylib from the
  [`tulip_rs_ffi` GitHub releases](https://github.com/me60732/tulip_rs_ffi)
  (no Rust toolchain needed), `--source` clones the ffi repo and cargo-builds
  it with full native-CPU tuning. The loader searches
  `-Dtulip.ffi.library` / `TULIP_RS_FFI_LIBRARY`, then `ffi/lib/` (prebuilt),
  then `../tulip_rs_ffi/target/{release,debug}` (source build). Windows has no
  prebuilt asset (FFM cannot load the shipped static lib) — use `--source`.

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

```sh
# 1. bump <version> in pom.xml, commit it
# 2. tag + push — the release workflow then runs the 95-example integration
#    suite, signs all artifacts, deploys to Central, and auto-publishes
#    once Central's validation finishes (~10-20 min).
git tag v0.2.9 && git push origin main --tags
```

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
