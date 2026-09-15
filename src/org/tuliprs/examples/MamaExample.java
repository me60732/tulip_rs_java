package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Mama;

/**
 * MAMA example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / clone), and both SIMD modes — the Java mirror of
 * the Go/C examples, with every step verified.
 */
public final class MamaExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {0.5, 0.05}; // fastlimit, slowlimit

        Info info = Mama.info();
        System.out.printf("=== %s (%s) ===%n", info.name(), info.fullName());
        System.out.printf("Inputs: %s, Options: %s, Optional: %s, Type: %s%n",
                info.inputs(), info.options(), info.optionalOutputs(), info.type());
        for (Info.DisplayGroup dg : info.displayGroups()) {
            System.out.printf("Display group \"%s\" (%s): outputs %s, %s%n",
                    dg.label(), dg.id(), dg.outputs(), dg.displayType());
            if (dg.offset() != null) {
                System.out.printf("  offset: %s%n", dg.offset());
            }
        }

        // Size the synthetic series from the indicator's own min_data so the
        // partial (n-50) slice is always big enough.
        int n = 2 * (int) Mama.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] real = series[0];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all optional outputs) ===");
        Outcome oc = Mama.indicator(series, options, new boolean[]{true, true});
        double[] fullMama;
        try (Result res = oc.result()) {
            fullMama = res.toDoubleArray(0);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Mama.indicator(slices(series, 0, partial), options, null);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n));
            try (br) {
                double[] continued = br.toDoubleArray(0);
                double[] tail = Arrays.copyOfRange(fullMama, fullMama.length - continued.length,
                        fullMama.length);
                c.match("partial+continued equals full recompute", Demo.sameTol(tail, continued, 1e-6, 1e-9));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Mama.ID);
                State rs = Mama.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest);
                    Result b2 = rs.batch(rest);
                    Result b3 = cl.batch(rest);
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically",
                                Demo.sameTol(b1.toDoubleArray(0), b2.toDoubleArray(0), 1e-6, 1e-9));
                        c.match("cloned state continues identically",
                                Demo.sameTol(b1.toDoubleArray(0), b3.toDoubleArray(0), 1e-6, 1e-9));
                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {Demo.scale(real, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Mama.simdByAssets(assets, options, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Mama.indicator(assets[i], options, null);
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD asset " + (i + 1) + " equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{0.3, 0.03}, {0.5, 0.05}, {0.7, 0.07}, {0.9, 0.09}};
        try (SimdResult sim = Mama.simdByOptions(series, optSets, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Mama.indicator(series, optSets[i], null);
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD option set " + (i + 1) + " equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                }
            }
        }

        c.done();
    }

    /** Mandatory output names first, then optional output names. */
    private static String[] rowNames(Info info) {
        int numMandatory = info.outputs().size();
        String[] out = new String[numMandatory + info.optionalOutputs().size()];
        for (int i = 0; i < numMandatory; i++) {
            out[i] = info.outputs().get(i);
        }
        for (int i = 0; i < info.optionalOutputs().size(); i++) {
            out[numMandatory + i] = info.optionalOutputs().get(i);
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
