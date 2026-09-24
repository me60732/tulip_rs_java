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
cp .env.example .env       # then edit .env for your hosts

# smoke (console only, quick):
BENCH_ONLY=ema BENCH_REPEAT=3 BENCH_WARMUP=1 ./run.sh

# full suite (writes results to the DB per .env):
./run.sh
```

### Configuration

`run.sh` loads `bench/.env` first (also found when run from `tulip_rs_java/`
or the repo root). Real environment variables always win over `.env`.

| Key | Meaning (default) |
|---|---|
| `STOCKS_DATABASE_URL` | OHLCV source (`postgres://…/stocks`); `DATABASE_URL` also accepted |
| `BENCHMARK_DATABASE_URL` | result DB (`postgres://…/indicator_benchmark`) |
| `BENCHMARK_LOG_TO_DB` | `1` = write runs/results, `0` = console only (`0`) |
| `BENCH_NUMBER` | timed calls per sample (`10`) |
| `BENCH_REPEAT` | samples per measurement (`30`) |
| `BENCH_WARMUP` | untimed warm-up calls (`10`) |
| `BENCH_ONLY` | comma-separated subset, e.g. `bbands,ema` (empty = all 95) |

URLs accept both `postgres://user:pass@host/db` and `jdbc:postgresql://…`
form (credentials are parsed out automatically; a 5 s `connectTimeout` is
applied when the URL omits one).

## Adding an indicator bench

1. Copy `BenchEma.java`; the class name must match `Bench[A-Z]*` for discovery.
2. Options grid: copy from the Go twin `tulip_rs_go/bench/bench_<name>.go`.
3. ta4j twin: follow **TA4J_MAP.md**; omit `.ta4j(...)` (with a brief comment)
   when no param-compatible indicator exists. Never register a closure that
   does less work than the real computation.
4. SIMD closures only where the facade has them.
5. Smoke your subset: `BENCH_ONLY=<names> BENCH_REPEAT=2 ./run.sh > v.log 2>&1`
   and require `grep "\[warn\]" v.log` to be empty — closure failures are
   otherwise swallowed silently.
6. Coverage guard (every facade needs a bench, prints nothing when complete):
   `ls ../src/org/tuliprs/indicators/*.java | sed 's|.*/||;s|\.java||' | while
   read b; do [ -f src/org/tuliprs/bench/Bench$b.java ] || echo "missing: $b"; done`
