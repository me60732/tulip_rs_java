package org.tuliprs.bench;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmark runner mirroring the Go/Python/Rust harness methodology:
 * warm-up → repeat × (number × timed calls), ns-per-call samples, then
 * mean/stddev/min/max logged per stock × option-set. Implementation
 * phases are isolated (tulip grid fully before the ta4j grid) so one
 * implementation's GC churn cannot leak into another's timing window.
 *
 * Results are printed in the C-suite line format and optionally written
 * to the shared indicator_benchmark Postgres (BENCHMARK_LOG_TO_DB=1).
 */
public final class Harness {

    /** One full indicator call + consume cycle for one stock and option set. */
    public interface RunFn {
        void run(Stock s, double[] options) throws Exception;
    }

    /** SIMD across N assets (all stocks, one option set). */
    public interface SimdAssetsFn {
        void run(Stock[] stocks, double[] options) throws Exception;
    }

    /** SIMD across N option sets (one stock, all option sets at once). */
    public interface SimdOptionsFn {
        void run(Stock s, double[][] optionSets) throws Exception;
    }

    // ---- registration --------------------------------------------------------

    private static final List<Benchmark> DEFS = new ArrayList<>();

    public static void register(Benchmark def) {
        DEFS.add(def);
    }

    /**
     * Instantiate every {@code Bench[A-Z]*} provider class sitting next to the
     * compiled harness classes — per-indicator files stay fully independent.
     */
    public static int discover() throws Exception {
        URL loc = Harness.class.getProtectionDomain().getCodeSource().getLocation();
        File dir = new File(loc.toURI()).toPath().resolve("org/tuliprs/bench").toFile();
        File[] files = dir.listFiles((d, n) -> n.matches("Bench[A-Z].*\\.class"));
        if (files == null) {
            throw new IllegalStateException("no benchmark classes found under " + dir);
        }
        int n = 0;
        for (File f : files) {
            String cls = "org.tuliprs.bench." + f.getName().replace(".class", "");
            Class<?> loaded = Class.forName(cls);
            if (loaded.isInterface() || !BenchProvider.class.isAssignableFrom(loaded)) {
                continue;
            }
            BenchProvider p = (BenchProvider) loaded.getDeclaredConstructor().newInstance();
            register(p.def());
            n++;
        }
        return n;
    }

    // ---- config (mirrors the Python harness env names) -----------------------

    public static int benchNumber = 10;
    public static int benchRepeat = 30;
    public static int benchWarmup = 10;
    public static boolean logToDb;

    public static void initConfig() {
        benchNumber = Env.envInt("BENCH_NUMBER", 10);
        benchRepeat = Env.envInt("BENCH_REPEAT", 30);
        benchWarmup = Env.envInt("BENCH_WARMUP", 10);
        logToDb = "1".equals(Env.envOr("BENCHMARK_LOG_TO_DB", "0"));
    }

    /** Anti-DCE sink: every timed closure folds a real value in here. */
    public static volatile double sink;

    public static void consume(double v) {
        sink += v;
    }

    // ---- timing ----------------------------------------------------------------

    static final class Timing {
        int meanNs;
        int stdDevNs;
        int minNs;
        int maxNs;
        int samples;
    }

    static Timing timeFn(Runnable fn, int number, int repeat, int warmup) {
        for (int i = 0; i < warmup; i++) {
            fn.run();
        }
        double[] ns = new double[repeat];
        for (int r = 0; r < repeat; r++) {
            long start = System.nanoTime();
            for (int n = 0; n < number; n++) {
                fn.run();
            }
            ns[r] = (double) (System.nanoTime() - start) / number;
        }
        double mean = 0;
        for (double v : ns) {
            mean += v;
        }
        mean /= ns.length;
        double var = 0;
        for (double v : ns) {
            var += (v - mean) * (v - mean);
        }
        var = ns.length > 1 ? var / (ns.length - 1) : 0;
        Timing t = new Timing();
        t.meanNs = (int) mean;
        t.stdDevNs = (int) Math.sqrt(var);
        t.minNs = (int) Arrays.stream(ns).min().orElse(0);
        t.maxNs = (int) Arrays.stream(ns).max().orElse(0);
        t.samples = repeat;
        return t;
    }

    // ---- DB logger (mirrors the Python BenchmarkLogger w/ reconnect-retry) ----

    private static final class DbLogger implements AutoCloseable {
        Connection conn;
        int runId;
        final Map<String, Integer> indCache = new HashMap<>();
        final String url;

        DbLogger(String url) throws Exception {
            this.url = url;
            this.conn = DB.open(url);
            loadIndicators();
        }

        void withRetry(String label, SqlOp op) throws Exception {
            try {
                op.run(conn);
            } catch (Exception e) {
                System.err.println("[warn] " + label + ": " + e.getMessage()
                        + " — reconnecting and retrying once");
                conn.close();
                conn = DB.open(url);
                op.run(conn);
            }
        }

        interface SqlOp {
            void run(Connection c) throws Exception;
        }

        void loadIndicators() throws Exception {
            withRetry("loadIndicators", c -> {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id, name FROM indicators")) {
                    while (rs.next()) {
                        indCache.put(rs.getString(2), rs.getInt(1));
                    }
                }
            });
        }

        void startRun(String notes) throws Exception {
            String os = System.getProperty("os.name", "?") + " " + System.getProperty("os.arch");
            int cores = Runtime.getRuntime().availableProcessors();
            String host;
            try {
                host = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "?";
            }
            String info = "{\"os\": \"" + os + "\", \"arch\": \"" + System.getProperty("os.arch")
                    + "\", \"cpu_cores\": " + cores + ", \"hostname\": \"" + host
                    + "\", \"java_version\": \"" + System.getProperty("java.version") + "\"}";
            withRetry("startRun", c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO benchmark_runs (notes, system_info) VALUES (?, ?::jsonb)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, notes);
                    ps.setString(2, info);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        runId = keys.getInt(1);
                    }
                }
            });
            System.out.println("  benchmark run id: " + runId);
        }

        void log(String indicator, String impl, double[] opts, Timing t, String symbol, int inputSize) {
            if (!indCache.containsKey(indicator)) {
                System.err.println("[warn] '" + indicator + "' not in indicators table — skipping");
                return;
            }
            String optsJson = optsJson(opts);
            try {
                withRetry("log(" + indicator + "/" + impl + "/" + symbol + ")", c -> {
                    try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO benchmark_results
                            (run_id, indicator_id, implementation_type, stock_symbol,
                             data_source, options, mean_time_ns, std_dev_ns,
                             min_time_ns, max_time_ns, sample_count, input_size)
                        SELECT ?, id, ?, ?, 'real_data', ?::jsonb,
                               ?, ?, ?, ?, ?, ?
                        FROM indicators WHERE name = ?""")) {
                        ps.setInt(1, runId);
                        ps.setString(2, impl);
                        ps.setString(3, symbol);
                        ps.setString(4, optsJson);
                        ps.setInt(5, t.meanNs);
                        ps.setInt(6, t.stdDevNs);
                        ps.setInt(7, t.minNs);
                        ps.setInt(8, t.maxNs);
                        ps.setInt(9, t.samples);
                        ps.setInt(10, inputSize);
                        ps.setString(11, indicator);
                        ps.executeUpdate();
                    }
                });
            } catch (Exception e) {
                System.err.println("[warn] INSERT failed for " + indicator + "/" + impl
                        + "/" + symbol + ": " + e.getMessage());
            }
        }

        @Override
        public void close() {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String optsJson(double[] opts) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < opts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (opts[i] == Math.rint(opts[i]) && !Double.isInfinite(opts[i])) {
                sb.append((long) opts[i]);
            } else {
                sb.append(opts[i]);
            }
        }
        return sb.append(']').toString();
    }

    // ---- output ---------------------------------------------------------------

    private static String fmtOptions(double[] opts) {
        if (opts.length == 0) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < opts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (opts[i] == Math.rint(opts[i])) {
                sb.append((long) opts[i]);
            } else {
                sb.append(opts[i]);
            }
        }
        return sb.toString();
    }

    private static void printRow(String impl, String symbol, double[] opts, Timing t) {
        System.out.printf("    %-8s %-30s %-10s %-16s %10d ns +/- %d%n",
                impl, symbol, fmtOptions(opts), String.valueOf(t.samples),
                t.meanNs, t.stdDevNs);
    }

    private static void safe(String label, ThrowingRunnable r) {
        try {
            r.run();
        } catch (Exception e) {
            System.err.println("[warn] " + label + " failed: " + e);
        }
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ---- core runner (identical phase order to the Go harness) -----------------

    private static void runBenchmark(Benchmark def, List<Stock> stocks, DbLogger logger) {
        System.out.printf("%n--- %s ---%n", def.name);
        Stock[] stockArr = stocks.toArray(new Stock[0]);

        // SIMD by assets — every stock in one SIMD call, per option set.
        if (def.simdAssetsFn != null && !stocks.isEmpty()) {
            for (double[] opts : def.options) {
                Timing t = timeFn(() -> safe("simd_assets_fn",
                        () -> def.simdAssetsFn.run(stockArr, opts)),
                        benchNumber, benchRepeat, benchWarmup);
                String symbol = "ALL_" + stocks.size() + "_ASSETS";
                printRow("simd_by_assets", symbol, opts, t);
                if (logger != null) {
                    logger.log(def.name, "tulip_rs_java_simd_by_assets", opts, t,
                            symbol, stocks.get(0).bars());
                }
            }
        }

        // SIMD by options — every option set in one SIMD call, per stock.
        if (def.simdOptionsFn != null && def.options.length > 0) {
            for (Stock s : stocks) {
                Timing t = timeFn(() -> safe("simd_options_fn",
                        () -> def.simdOptionsFn.run(s, def.options)),
                        benchNumber, benchRepeat, benchWarmup);
                printRow("simd_by_options", s.symbol, def.options[0], t);
                if (logger != null) {
                    logger.log(def.name, "tulip_rs_java_simd_by_options", def.options[0], t,
                            s.symbol, s.bars());
                }
            }
        }

        // Phase 1: tulip_rs_java — full stock × option grid.
        for (Stock s : stocks) {
            for (double[] opts : def.options) {
                Timing t = timeFn(() -> safe("tulip_fn", () -> def.tulipFn.run(s, opts)),
                        benchNumber, benchRepeat, benchWarmup);
                printRow("tulip_rs_java", s.symbol, opts, t);
                if (logger != null) {
                    logger.log(def.name, "tulip_rs_java", opts, t, s.symbol, s.bars());
                }
            }
        }

        // Phase 2: ta4j reference — full grid AFTER tulip, with a GC between.
        if (def.ta4jFn != null) {
            System.gc();
            for (Stock s : stocks) {
                for (double[] opts : def.options) {
                    Timing t = timeFn(() -> safe("ta4j_fn", () -> def.ta4jFn.run(s, opts)),
                            benchNumber, benchRepeat, benchWarmup);
                    printRow("ta4j", s.symbol, opts, t);
                    if (logger != null) {
                        logger.log(def.name, "ta4j", opts, t, s.symbol, s.bars());
                    }
                }
            }
        }
    }

    public static void runAll(List<Stock> stocks) {
        initConfig();
        DEFS.sort((a, b) -> a.name.compareTo(b.name));

        String only = Env.envOr("BENCH_ONLY", "");
        if (!only.isEmpty()) {
            List<String> wanted = new ArrayList<>();
            for (String n : only.split(",")) {
                n = n.trim().toLowerCase();
                if (!n.isEmpty()) {
                    wanted.add(n);
                }
            }
            DEFS.removeIf(def -> !wanted.contains(def.name));
            if (DEFS.isEmpty()) {
                System.err.println("[error] BENCH_ONLY matched no registered indicators");
                return;
            }
        }

        DbLogger logger = null;
        if (logToDb) {
            String url = Env.envOr("BENCHMARK_DATABASE_URL",
                    "postgres://tulip:tulip@192.168.50.10:5433/indicator_benchmark?sslmode=disable");
            try {
                logger = new DbLogger(url);
                logger.startRun("Java bindings benchmarks -- tulip_rs_java, ta4j");
            } catch (Exception e) {
                System.err.println("[warn] failed to create benchmark logger: " + e);
                if (logger != null) {
                    logger.close();
                }
                logger = null;
            }
        }

        for (Benchmark def : DEFS) {
            runBenchmark(def, stocks, logger);
        }

        if (logger != null) {
            logger.close();
        }
        System.out.printf("%n================================================================%n");
        System.out.println("  Collected benchmark results");
    }
}
