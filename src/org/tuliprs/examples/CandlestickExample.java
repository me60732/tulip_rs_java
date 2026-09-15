package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.CandleOutcome;
import org.tuliprs.CandlePattern;
import org.tuliprs.CandleResult;
import org.tuliprs.CandleState;
import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Candlestick;

/**
 * Candlestick example: pattern detection over synthetic OHLC bars, streaming
 * continuation, and state persistence — the Java mirror of the Go/C example.
 * Candlestick is scalar-only (no SIMD) and its output is pattern ids, not
 * f64 series.
 */
public final class CandlestickExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {5.0, 1.0, 1.0}; // candle, trend, trend-signal periods

        Info info = Candlestick.info();
        System.out.printf("=== %s (%s) ===%n", info.name(), info.fullName());
        System.out.printf("Inputs: %s, Options: %s, Pattern table: %d entries%n",
                info.inputs(), info.options(), Candlestick.numPatterns());

        int n = 2 * (int) Candlestick.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] open = series[0], high = series[1], low = series[2], close = series[3];

        // ---- full detection run ----------------------------------------------
        System.out.println("\n=== full run (no forecast filter) ===");
        int[][] full;
        CandleOutcome oc = Candlestick.indicator(series, options);
        try (CandleResult res = oc.result()) {
            int detected = 0;
            for (int b = 0; b < res.numBars(); b++) {
                if (res.patterns(b).length > 0) {
                    detected++;
                    if (detected <= 5) {
                        System.out.printf("  bar %2d: %s%n", b, Arrays.toString(res.names(b)));
                    }
                }
            }
            System.out.printf("  %d detections across %d bars (%d pattern ids total)%n",
                    detected, res.numBars(), res.totalPatterns());
            CandlePattern p0 = Candlestick.patternInfo(0);
            System.out.printf("  pattern 0: %s (%s, %d bars, %s)%n",
                    p0.fullName(), p0.name(), p0.bars(), p0.forecast());
            full = res.toArrays();
        }
        oc.state().close();

        // ---- partial + batch continuation ------------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        CandleOutcome p = Candlestick.indicator(slices(series, 0, partial), options);
        try (CandleResult pr = p.result(); CandleState pst = p.state()) {
            CandleResult br = pst.batch(slices(series, partial, n));
            try (br) {
                // The batch re-emits the detection window it can still affect:
                // compare its rows against the same-length tail of the full run
                // (Go example semantics; a naive join would misalign context bars).
                int[][] continued = br.toArrays();
                int[][] tail = Arrays.copyOfRange(full, full.length - continued.length,
                        full.length);
                c.match("partial+continued equals full recompute", eq(tail, continued));

                // ---- persistence ----------------------------------------------
                System.out.println("\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Candlestick.ID);
                CandleState rs = Candlestick.deserializeState(blob);
                CandleState cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    CandleResult b1 = pst.batch(rest);
                    CandleResult b2 = rs.batch(rest);
                    CandleResult b3 = cl.batch(rest);
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically",
                                eq(b1.toArrays(), b2.toArrays()));
                        c.match("cloned state continues identically",
                                eq(b1.toArrays(), b3.toArrays()));
                    }
                }
            }
        }

        // ---- forecast filter smoke check -------------------------------------
        System.out.println("\n=== forecast filter (BullishReversal only) ===");
        CandleOutcome f = Candlestick.indicator(series, options,
                CandlePattern.FORECAST_BULLISH_REVERSAL);
        try (CandleResult fres = f.result()) {
            boolean allBullish = true;
            int total = 0;
            for (int b = 0; b < fres.numBars() && allBullish; b++) {
                for (int id : fres.patterns(b)) {
                    total++;
                    if (!Candlestick.patternInfo(id).forecast().equals("BullishReversal")) {
                        allBullish = false;
                    }
                }
            }
            System.out.printf("  %d ids, filter honored: %s%n", total, allBullish);
            c.match("forecast filter yields only BullishReversal patterns", allBullish);
        }
        f.state().close();

        c.done();
    }

    /** Equality over CSR rows (pattern ids per bar). */
    private static boolean eq(int[][] a, int[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!Arrays.equals(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    /** Column-slices every input series to [from, to). */
    private static double[][] slices(double[][] series, int from, int to) {
        double[][] out = new double[series.length][];
        for (int i = 0; i < series.length; i++) {
            out[i] = Arrays.copyOfRange(series[i], from, to);
        }
        return out;
    }
}
