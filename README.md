# tulip-rs-java

[![Maven Central](https://img.shields.io/maven-central/v/io.github.me60732/tulip-rs-java.svg)](https://central.sonatype.com/artifact/io.github.me60732/tulip-rs-java)
[![CI status](https://github.com/me60732/tulip_rs_java/actions/workflows/ci.yml/badge.svg)](https://github.com/me60732/tulip_rs_java/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Docs](https://img.shields.io/badge/docs-me60732.github.io-blue)](https://me60732.github.io/tulip_rs/)

**High-performance technical analysis for Java — powered by Rust.**

Native Java bindings for [TulipRS](https://github.com/me60732/tulip_rs) built on
the Panama FFM API (`java.lang.foreign`) — no JNI, no code generation, zero
runtime dependencies. Implements 95 technical indicators and 77+ candlestick
patterns with first-class SIMD acceleration. Process multiple assets or
multiple parameter sets in a single CPU pass, stream live bars into stateful
indicators without reprocessing history.

Full API documentation: [me60732.github.io/tulip_rs](https://me60732.github.io/tulip_rs/)

---

## Why tulip-rs-java?

| | tulip-rs-java | ta4j / pure-Java libraries |
|---|---|---|
| **SIMD — multiple assets** | ✅ N assets in one pass | ❌ one asset at a time |
| **SIMD — multiple options** | ✅ N parameter sets in one pass | ❌ one parameter set at a time |
| **Stateful streaming** | ✅ resume from saved state | ❌ full recompute each tick |
| **State serialisation** | ✅ bincode + JSON blobs | ❌ |
| **Performance** | ✅ native Rust + SIMD | ❌ interpreted Java |
| **Runtime dependencies** | ✅ none (FFM ships with the JDK) | — |

Requires **JDK 22+** (the FFM API is final from 22; no `--enable-preview`).

---

## Installation

### From Maven Central (recommended)

The native library **comes with the dependency**: a platform-activated profile
in the published pom pulls the matching classifier jar (`linux-amd64`,
`linux-arm64`, `darwin-amd64`, `darwin-arm64`, `windows-amd64`) containing the
portable-baseline native, extracted automatically at first load.

```xml
<dependency>
  <groupId>io.github.me60732</groupId>
  <artifactId>tulip-rs-java</artifactId>
  <version>0.2.10</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.me60732:tulip-rs-java:0.2.10'
```

### Build from source (fastest)

Building the native on your own machine with `-C target-cpu=native` lets LLVM
use every instruction set your CPU supports — a substantial speed-up over the
portable baseline. Requires a Rust toolchain:

```sh
git clone https://github.com/me60732/tulip_rs_java
cd tulip_rs_java
git checkout {latest tag}   # or omit for the bleeding edge
./bootstrap.sh --source     # clones ../tulip_rs_ffi, builds with native CPU tuning
./build.sh                  # -> out/ (library) + out-examples/
```

Anything `bootstrap.sh` installs also wins the runtime library search:
`-Dtulip.ffi.library` / `TULIP_RS_FFI_LIBRARY` → `ffi/lib/` walking up from the
CWD → sibling `../tulip_rs_ffi/target/{release,debug}` → embedded classifier jar.

---

## Quick Start

Every indicator follows the same API — learn it once, use it everywhere.

```java
import org.tuliprs.*;
import org.tuliprs.indicators.Ema;

double[] close = {81.59, 81.06, 82.87, 83.00, 83.61,
                  83.15, 82.84, 83.99, 84.55, 84.36};

// inputs: double[][]  |  options: double[]
Outcome oc = Ema.indicator(new double[][] {close}, new double[] {5});
try (Result res = oc.result(); State st = oc.state()) {
    double[] ema = res.toDoubleArray(0);   // EMA(5) values

    // Streaming: feed new bars without reprocessing history
    Result next = st.batch(new double[][] {{85.10}});
}
```

---

## API

### Indicator info

```java
Info info = Sma.info();
// name "sma", fullName "Simple Moving Average",
// inputs [real], options [period], outputs [sma],
// indicator type + display groups for charting

long need = Sma.minData(new double[] {5});   // minimum bars to produce output

Sma.INPUTS == 1 && Sma.OPTIONS == 1          // constants; ID is the FFI's FNV-1a32
```

### Running an indicator

```java
Outcome oc = Sma.indicator(inputs, new double[] {5});
Result res = oc.result();   // output rows: zero-copy views over Rust memory
State  st  = oc.state();    // streaming snapshot after the last bar
```

### Streaming continuation

Save the state after an initial batch, then feed new bars incrementally:

```java
Outcome oc = Sma.indicator(head, new double[] {5});
State st = oc.state();
Result tail = st.batch(moreBars);   // continues exactly where the head left off
```

### State serialisation

States round-trip to bincode (fast, binary) or JSON (human-readable,
cross-language). Blobs are self-describing:

```java
byte[] blob = st.serialize(Format.BINCODE);
State a = Sma.deserializeState(blob);
State b = st.duplicate();               // value-semantics snapshot (Rust Clone)
```

### SIMD — multiple assets

Process N assets in a single CPU pass. N must be 2, 4, 8, or 16.

```java
double[][][] assets = {{a1Close}, {a2Close}, {a3Close}, {a4Close}};
try (SimdResult sim = Sma.simdByAssets(assets, new double[] {5}, null)) {
    double[] sma1 = sim.toDoubleArray(0, 0);   // asset 1, output row 0
}
```

### SIMD — multiple option sets

Run N different parameter sets against the same data in one pass.

```java
double[][] optionSets = {{2}, {5}, {8}, {10}};
try (SimdResult sim = Sma.simdByOptions(new double[][] {close}, optionSets, null)) {
    for (int i = 0; i < sim.numResults(); i++) { /* ... */ }
}
```

### Multi-input indicators

Indicators that need more than one price series take them as additional arrays:

```java
// ADX — high, low, close (3 inputs, 1 option)
Adx.indicator(new double[][] {high, low, close}, new double[] {14});

// MACD — close only → outputs: MACD, signal, histogram
Macd.indicator(new double[][] {close}, new double[] {2, 5, 9});

// BBands — close only → outputs: upper, middle, lower
Bbands.indicator(new double[][] {close}, new double[] {20, 2.0, 2.0});
```

### Candlestick patterns

Pattern detection returns per-bar pattern objects instead of f64 rows, takes a
forecast filter (`CandlePattern.FORECAST_NONE` and friends), and has no SIMD
variants. See `examples/org/tuliprs/examples/CandlestickExample.java`.

### Memory management: close, never free

Outputs and states are Rust-allocated, so they live outside the JVM heap — no
garbage collector can reclaim them. The binding makes that invisible: wrap
`Result` / `SimdResult` / `State` in try-with-resources and every native free
happens in the right order automatically (all `close()`s are idempotent; a
Cleaner backstop catches leaks). The full contract:
[`docs/MEMORY_MODEL.md`](docs/MEMORY_MODEL.md).

---

## Indicators

| Category | Indicators |
|---|---|
| **Moving Averages** | SMA, EMA, WMA, DEMA, TEMA, TRIMA, HMA, ZLEMA, KAMA, VIDYA, VWMA, Wilders, SMA Envelope |
| **Oscillators** | RSI, MACD, Stochastic, StochRSI, Williams %R, CCI, CMO, Ultimate Oscillator, AO, Fisher Transform, FOSC, MSW, TRIX |
| **Trend** | ADX, ADXR, DI, DM, DX, Aroon, Aroon Osc, PSAR, PPO, APO, Vortex, Elder-Ray, Donchian Channel, Ichimoku, SuperTrend, Efficiency Ratio, MAMA |
| **Volatility** | BBands, ATR, NATR, TR, StdDev, Volatility, VHF, CVI, Chandelier Exit, Keltner Channel, TRVI |
| **Volume** | AD, ADOSC, OBV, MFI, NVI, PVI, VOSC, KVO, EMV, WAD, MarketFi, ChaikinMF, VWAP |
| **Price & Statistical** | AvgPrice, MedPrice, TypPrice, WCPrice, Max, Min, MOM, ROC, ROCR, BOP, LinReg, TSF, DPO, Mass, MD, QStick, PivotPoint |
| **Cycle & Ehlers** | CyberCycle, Adaptive MSW, Homodyne Discriminator, Instantaneous Trendline, TrendMode, High Pass Filter, Hilbert Transform, Roofing Filter, Super Smoother, CC Fisher |
| **Candlestick** | 77+ patterns via `Candlestick` |

---

## Running the Examples

One fully verified example per indicator (streaming, persistence, and both
SIMD modes compared against recomputation):

```sh
./bootstrap.sh --prebuilt   # or --source, or rely on the Maven Central dep
./build.sh
./run.sh AdxExample         # org.tuliprs.examples.AdxExample
./test.sh                   # build + run all examples (integration suite)
```

`bench/` contains the benchmark suite measuring every indicator against
[ta4j](https://github.com/ta4j/ta4j) on real OHLCV data.

---

## Language Support

| Language | Status | Package |
|---|---|---|
| **Java** | ✅ Supported | [`tulip-rs-java`](https://central.sonatype.com/artifact/io.github.me60732/tulip-rs-java) (this repo) |
| **Rust** | ✅ Native | [`tulip_rs`](https://github.com/me60732/tulip_rs) |
| **Node.js** | ✅ Supported | [`tulip-rs-node`](https://www.npmjs.com/package/tulip-rs-node) |
| **Python** | ✅ Supported | [`tulip_rs_python`](https://github.com/me60732/tulip_rs_python) · `pip install tulip-rs` |
| **Go** | ✅ Supported | [`tulip_rs_go`](https://github.com/me60732/tulip_rs_go) |
| R | 🔜 Planned | — |
| Julia | 🔜 Planned | — |

---

## Repo Layout

```
src/org/tuliprs/            core: Tulip (FFM plumbing), Native (per-indicator engine),
                            Result, State, SimdResult, Info, Outcome, Format, IndicatorException
src/org/tuliprs/indicators/ one facade class per indicator (constants + delegation)
examples/                   one verified example per indicator + shared demo harness
bench/                      tulip-rs-java vs ta4j benchmark suite (own build/run scripts)
docs/                       maintainer docs (RELEASING.md: cut a Maven Central release)
ffi/                        generated by bootstrap.sh — prebuilt native (gitignored)
```

---

## License

[MIT](LICENSE)
