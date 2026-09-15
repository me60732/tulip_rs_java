package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Ppo;

/**
 * PPO example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class PpoExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {12.0, 26.0}; // fast_period, slow_period

        Info info = Ppo.info();
        System.out.printf("=== %s (%s) ===%n", info.name(), info.fullName());
        System.out.printf("Inputs: %s, Options: %s, Optional: %s, Type: %s%n",
                info.inputs(), info.options(), info.optionalOutputs(), info.type());

        // Size the synthetic series from the indicator's own min_data so the
        // partial (n-50) slice is always big enough.
        int n = 2 * (int) Ppo.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] real = series[0];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all optional outputs) ===");
        Outcome oc = Ppo.indicator(series, options, new boolean[] {true, true});
        double[] fullPpo;
        double[] fullShortEma;
        double[] fullLongEma;
        try (Result res = oc.result()) {
            fullPpo = res.toDoubleArray(0);
            fullShortEma = res.toDoubleArray(1);
            fullLongEma = res.toDoubleArray(2);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Ppo.indicator(slices(series, 0, partial), options);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n), new boolean[] {true, true});
            try (br) {
                double[] continuedPpo = br.toDoubleArray(0);
                double[] continuedShortEma = br.toDoubleArray(1);
                double[] continuedLongEma = br.toDoubleArray(2);
                double[] tailPpo = Arrays.copyOfRange(fullPpo, fullPpo.length - continuedPpo.length,
                        fullPpo.length);
                double[] tailShortEma = Arrays.copyOfRange(fullShortEma, fullShortEma.length - continuedShortEma.length,
                        fullShortEma.length);
                double[] tailLongEma = Arrays.copyOfRange(fullLongEma, fullLongEma.length - continuedLongEma.length,
                        fullLongEma.length);
                c.match("partial+continued equals full recompute (ppo)", Demo.sameTol(tailPpo, continuedPpo, 1e-6, 1e-9));
                c.match("partial+continued equals full recompute (short_ema)", Demo.sameTol(tailShortEma, continuedShortEma, 1e-6, 1e-9));
                c.match("partial+continued equals full recompute (long_ema)", Demo.sameTol(tailLongEma, continuedLongEma, 1e-6, 1e-9));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Ppo.ID);
                State rs = Ppo.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest, new boolean[] {true, true});
                    Result b2 = rs.batch(rest, new boolean[] {true, true});
                    Result b3 = cl.batch(rest, new boolean[] {true, true});
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically (ppo)", Demo.sameTol(
                                b1.toDoubleArray(0), b2.toDoubleArray(0), 1e-6, 1e-9));
                        c.match("deserialized state continues identically (short_ema)", Demo.sameTol(
                                b1.toDoubleArray(1), b2.toDoubleArray(1), 1e-6, 1e-9));
                        c.match("deserialized state continues identically (long_ema)", Demo.sameTol(
                                b1.toDoubleArray(2), b2.toDoubleArray(2), 1e-6, 1e-9));
                        c.match("cloned state continues identically (ppo)", Demo.sameTol(
                                b1.toDoubleArray(0), b3.toDoubleArray(0), 1e-6, 1e-9));
                        c.match("cloned state continues identically (short_ema)", Demo.sameTol(
                                b1.toDoubleArray(1), b3.toDoubleArray(1), 1e-6, 1e-9));
                        c.match("cloned state continues identically (long_ema)", Demo.sameTol(
                                b1.toDoubleArray(2), b3.toDoubleArray(2), 1e-6, 1e-9));
                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {
                Demo.scale(real, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Ppo.simdByAssets(assets, options, new boolean[] {true, true})) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Ppo.indicator(assets[i], options, new boolean[] {true, true});
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD asset " + (i + 1) + " equals individual (ppo)", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " equals individual (short_ema)", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " equals individual (long_ema)", Demo.sameTol(
                            sim.toDoubleArray(i, 2), ind.toDoubleArray(2), 1e-6, 1e-9));
                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{10, 20}, {12, 26}, {15, 30}, {20, 40}};
        try (SimdResult sim = Ppo.simdByOptions(series, optSets, new boolean[] {true, true})) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Ppo.indicator(series, optSets[i], new boolean[] {true, true});
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD option set " + (i + 1) + " equals individual (ppo)", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " equals individual (short_ema)", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " equals individual (long_ema)", Demo.sameTol(
                            sim.toDoubleArray(i, 2), ind.toDoubleArray(2), 1e-6, 1e-9));
                }
            }
        }

        c.done();
    }

    /** Output names in order (mandatory outputs, then optional). */
    private static String[] rowNames(Info info) {
        String[] out = new String[info.outputs().size() + info.optionalOutputs().size()];
        for (int i = 0; i < info.outputs().size(); i++) {
            out[i] = info.outputs().get(i);
        }
        for (int i = 0; i < info.optionalOutputs().size(); i++) {
            out[info.outputs().size() + i] = info.optionalOutputs().get(i);
        }
        return out;
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
