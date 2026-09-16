package org.tuliprs.bench;

/**
 * tulip_rs_java benchmark suite entry point — the twin of
 * tulip_rs_go/cmd/bench. Loads .env, pulls the four benchmark stocks from
 * Postgres, discovers every Bench&lt;Name&gt; provider class and runs the grid.
 *
 * Env knobs: BENCH_NUMBER, BENCH_REPEAT, BENCH_WARMUP, BENCH_ONLY
 * ("bbands,ema" to subset), BENCHMARK_LOG_TO_DB=1 to write results.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Env.loadDotEnv();

        System.out.println("================================================================");
        System.out.println("  tulip_rs_java Benchmark Suite");
        System.out.println("================================================================");

        int n = Harness.discover();
        System.out.println("  discovered " + n + " benchmark definitions");

        System.out.println("\n[1/2] Loading stock data from Postgres...");
        var stocks = Stocks.load();
        if (stocks.isEmpty()) {
            System.err.println("[error] no stock data loaded — cannot run benchmarks");
            System.exit(1);
        }
        System.out.printf("  Loaded %d stocks%n", stocks.size());

        System.out.println("\n[2/2] Running benchmarks...");
        Harness.runAll(stocks);

        System.out.println("\n================================================================");
        System.out.println("  Done");
        System.out.println("================================================================");
    }
}
