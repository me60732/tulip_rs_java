package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Pivotpoint;

/**
 * Pivotpoint example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class PivotpointExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {10.0}; // period

        Info info = Pivotpoint.info();
        System.out.printf("=== %s (%s) ===%n", info.name(), info.fullName());
        System.out.printf("Inputs: %s, Options: %s, Optional: %s, Type: %s%n",
                info.inputs(), info.options(), info.optionalOutputs(), info.type());

        // Size the synthetic series from the indicator's own min_data so the
        // partial (n-50) slice is always big enough.
        int n = 2 * (int) Pivotpoint.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] high = series[0], low = series[1], close = series[2];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all mandatory outputs) ===");
        Outcome oc = Pivotpoint.indicator(series, options);
        double[] fullPivotpoint;
        try (Result res = oc.result()) {
            fullPivotpoint = res.toDoubleArray(0);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Pivotpoint.indicator(slices(series, 0, partial), options);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n));
            try (br) {
                double[] continuedPivotpoint = br.toDoubleArray(0);
                double[] tailPivotpoint = Arrays.copyOfRange(fullPivotpoint, fullPivotpoint.length - continuedPivotpoint.length,
                        fullPivotpoint.length);
                c.match("partial+continued equals full recompute", Demo.same(tailPivotpoint, continuedPivotpoint));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Pivotpoint.ID);
                State rs = Pivotpoint.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest);
                    Result b2 = rs.batch(rest);
                    Result b3 = cl.batch(rest);
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically",
                                Demo.same(b1.toDoubleArray(0), b2.toDoubleArray(0)));
                        c.match("cloned state continues identically",
                                Demo.same(b1.toDoubleArray(0), b3.toDoubleArray(0)));
                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {
                Demo.scale(high, 1.2), Demo.scale(low, 1.2), Demo.scale(close, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Pivotpoint.simdByAssets(assets, options)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Pivotpoint.indicator(assets[i], options);
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD asset " + (i + 1) + " equals individual", Demo.same(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0)));
                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{5}, {10}, {15}, {20}};
        try (SimdResult sim = Pivotpoint.simdByOptions(series, optSets)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Pivotpoint.indicator(series, optSets[i]);
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD option set " + (i + 1) + " equals individual", Demo.same(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0)));
                }
            }
        }

        c.done();
    }

    /** Output names in order (mandatory outputs). */
    private static String[] rowNames(Info info) {
        String[] out = new String[info.outputs().size()];
        for (int i = 0; i < info.outputs().size(); i++) {
            out[i] = info.outputs().get(i);
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
