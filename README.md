# tulip-rs-java

Java bindings for [TulipRS](https://github.com/me60732/tulip_rs) via the
Panama FFM API (`java.lang.foreign`) on top of the shared
[`tulip_rs_ffi`](../tulip_rs_ffi) native library. No JNI, no code
generation: one downcall bundle per indicator, resolved at runtime from the
C symbol prefix.

## Requirements

- **JDK 22+** (the FFM API is final from 22; tested on Temurin 25). No
  `--enable-preview` needed.
- A built `libtulip_rs_ffi.{so,dylib}` — from `tulip_rs_ffi` via
  `cargo build` (debug or release). It is auto-discovered by walking up from
  the working directory for `tulip_rs_ffi/target/{release,debug}`; override
  with `-Dtulip.ffi.library=/path/to/lib` or `TULIP_RS_FFI_LIBRARY`.

## Build & run

```sh
./build.sh                  # -> out/ (uses TULIP_JAVA_HOME/JAVA_HOME or a JDK 25 install)
./run.sh AdxExample         # org.tuliprs.examples.AdxExample
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
src/org/tuliprs/demo/       shared example harness (synthetic series, comparisons, checks)
src/org/tuliprs/indicators/ one facade class per indicator (constants + delegation)
src/org/tuliprs/examples/   one verified example per indicator
```
