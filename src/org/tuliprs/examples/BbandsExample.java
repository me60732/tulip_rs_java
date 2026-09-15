package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Bbands;

/**
 * Bollinger Bands example: full compute (3 mandatory band rows), streaming
 * continuation, state persistence (serialize / deserialize / duplicate),
 * and both SIMD modes — every row of every step verified.
 */
public final class BbandsExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {5.0, 2.0}; // period, std_dev

        Info info = Bbands.info();
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

        int n = 2 * (int) Bbands.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation ===");
        double[][] full;
        Outcome oc = Bbands.indicator(series, options);
        try (Result res = oc.result()) {
            full = res.toArrays(); // [lower, middle, upper]
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-14s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Bbands.indicator(slices(series, 0, partial), options);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n));
            try (br) {
                for (int row = 0; row < full.length; row++) {
                    double[] continued = br.toDoubleArray(row);
                    double[] tail = Arrays.copyOfRange(full[row],
                            full[row].length - continued.length, full[row].length);
                    c.match("partial+continued " + rowNames(info)[row] + " equals full recompute",
                            Demo.same(tail, continued));
                }

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Bbands.ID);
                State rs = Bbands.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest);
                    Result b2 = rs.batch(rest);
                    Result b3 = cl.batch(rest);
                    try (b1; b2; b3) {
                        for (int row = 0; row < full.length; row++) {
                            c.match("deserialized state continues identically row " + row,
                                    Demo.same(b1.toDoubleArray(row), b2.toDoubleArray(row)));
                            c.match("cloned state continues identically row " + row,
                                    Demo.same(b1.toDoubleArray(row), b3.toDoubleArray(row)));
                        }
                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = {Demo.scale(series[0], 1.2)};
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Bbands.simdByAssets(assets, options)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Bbands.indicator(assets[i], options);
                try (Result ind = r.result(); State st2 = r.state()) {
                    for (int row = 0; row < ind.numOutputs(); row++) {
                        c.match("SIMD asset " + (i + 1) + " row " + row + " equals individual",
                                Demo.sameTol(sim.toDoubleArray(i, row),
                                        ind.toDoubleArray(row), 1e-6, 1e-9));
                    }
                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{3, 1.5}, {5, 2.0}, {7, 2.5}, {10, 3.0}};
        try (SimdResult sim = Bbands.simdByOptions(series, optSets)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Bbands.indicator(series, optSets[i]);
                try (Result ind = r.result(); State st2 = r.state()) {
                    for (int row = 0; row < ind.numOutputs(); row++) {
                        c.match("SIMD option set " + (i + 1) + " row " + row + " equals individual",
                                Demo.sameTol(sim.toDoubleArray(i, row),
                                        ind.toDoubleArray(row), 1e-6, 1e-9));
                    }
                }
            }
        }

        c.done();
    }

    /** Mandatory output names first, then optional output names. */
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
