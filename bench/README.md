# Java Benchmark Suite

Java benchmarks mirroring the Go suite (`tulip_rs_go/bench/`), comparing
**tulip-rs-java** against **ta4j 0.19** (the Java reference library:
https://github.com/ta4j/ta4j).

## Methodology (identical to Go/Python/Rust/C)

- 4 stocks: BHP_ASX, CBA_ASX, AAPL_NYSE, MSFT_NYSE, 6705 bars each, loaded
  from the shared stocks Postgres.
- Same per-indicator option sets as the Go twin files.
- Warm-up → repeat × number timed calls, ns/call samples → mean ± stddev.
- Implementation-isolated phases (tulip grid first, then ta4j, `System.gc()`
  between) so one runtime's GC churn never lands in the other's window.
- Results printed in the C-suite line format and optionally written to the
  shared `indicator_benchmark` Postgres.

Implementation type strings: `tulip_rs_java`, `ta4j`,
`tulip_rs_java_simd_by_assets`, `tulip_rs_java_simd_by_options`.

## Layout

```
bench/lib/       third-party jars (ta4j-core-0.19 + deps, postgresql JDBC)
bench/src/org/tuliprs/bench/
  Harness.java   timing, phases, DB logger, discovery, run-all
  Benchmark.java fluent per-indicator definition (name/options/closures)
  BenchProvider.java interface every Bench<Name> class implements
  Ta4j.java      BaseBarSeries build + cache + full-series timed pass
  Stocks.java    Postgres OHLCV loader (same query/limit as Go)
  Env.java       .env loader (shares the Go bench's .env format)
  Main.java      entry point
  Bench<Name>.java  one file per indicator (auto-discovered; no central list)
```

## Run

```sh
cd tulip_rs_java
./build.sh                 # indicator classes (../out)
cd bench && ./build.sh     # bench classes (bench/out)

# smoke (no DB writes, quick):
BENCH_ONLY=ema BENCH_REPEAT=3 BENCH_WARMUP=1 ./run.sh

# full suite with DB logging (uses .env or the built-in defaults):
BENCHMARK_LOG_TO_DB=1 ./run.sh
```

`BENCH_ONLY="bbands,ema"` subsets; `BENCH_NUMBER/REPEAT/WARMUP` tune timing.

## Adding an indicator bench

1. Copy `BenchEma.java`; the class name must match `Bench[A-Z]*` for discovery.
2. Options grid: copy from the Go twin `tulip_rs_go/bench/bench_<name>.go`.
3. ta4j twin: follow **TA4J_MAP.md**; omit `.ta4j(...)` (with a brief comment)
   when no param-compatible indicator exists. Never register a closure that
   does less work than the real computation.
4. SIMD closures only where the facade has them.
