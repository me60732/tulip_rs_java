package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Chandelierexit;

/**
 * CHANDELIEREXIT example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class ChandelierexitExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {14.0, 3.0}; // period, multiplier

        Info info = Chandelierexit.info();
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
        int n = 2 * (int) Chandelierexit.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] high = series[0], low = series[1], close = series[2];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all optional outputs) ===");
        Outcome oc = Chandelierexit.indicator(series, options, new boolean[] {true, true, true});
        double[][] fullReference;
        try (Result res = oc.result()) {
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
            fullReference = res.toArrays();
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Chandelierexit.indicator(slices(series, 0, partial), options, null);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n));
            try (br) {
                for (int o = 0; o < br.numOutputs(); o++) {
                    double[] continued = br.toDoubleArray(o);
                    double[] tail = Arrays.copyOfRange(fullReference[o], fullReference[o].length - continued.length,
                            fullReference[o].length);
                    c.match("partial+continued equals full recompute (row " + o + ")", Demo.same(tail, continued));
                }
            }

            // ---- persistence -------------------------------------------
            System.out.println(
                    "\n=== state persistence (serialize / deserialize / clone) ===");
            byte[] blob = pst.serialize(Format.BINCODE);
            System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                    blob.length, Chandelierexit.ID);
            State rs = Chandelierexit.deserializeState(blob);
            State cl = pst.duplicate();
            try (rs; cl) {
                double[][] rest = slices(series, partial, n);
                Result b1 = pst.batch(rest);
                Result b2 = rs.batch(rest);
                Result b3 = cl.batch(rest);
                try (b1; b2; b3) {
                    for (int o = 0; o < b1.numOutputs(); o++) {
                        c.match("deserialized state continues identically (row " + o + ")",
                                Demo.same(b1.toDoubleArray(o), b2.toDoubleArray(o)));
                        c.match("cloned state continues identically (row " + o + ")",
                                Demo.same(b1.toDoubleArray(o), b3.toDoubleArray(o)));
                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {
                Demo.scale(high, 1.2), Demo.scale(low, 1.2), Demo.scale(close, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Chandelierexit.simdByAssets(assets, options, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Chandelierexit.indicator(assets[i], options, null);
                try (Result ind = r.result(); State st2 = r.state()) {
                    for (int o = 0; o < ind.numOutputs(); o++) {
                        c.match("SIMD asset " + (i + 1) + " equals individual (row " + o + ")", Demo.sameTol(
                                sim.toDoubleArray(i, o), ind.toDoubleArray(o), 1e-6, 1e-9));
                    }
                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{10, 2.5}, {14, 3.0}, {20, 3.5}, {25, 4.0}};
        try (SimdResult sim = Chandelierexit.simdByOptions(series, optSets, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Chandelierexit.indicator(series, optSets[i], null);
                try (Result ind = r.result(); State st2 = r.state()) {
                    for (int o = 0; o < ind.numOutputs(); o++) {
                        c.match("SIMD option set " + (i + 1) + " equals individual (row " + o + ")", Demo.sameTol(
                                sim.toDoubleArray(i, o), ind.toDoubleArray(o), 1e-6, 1e-9));
                    }
                }
            }
        }

        c.done();
    }

    /** All output names in order: mandatory outputs first, then optional. */
    private static String[] rowNames(Info info) {
        int total = info.outputs().size() + info.optionalOutputs().size();
        String[] out = new String[total];
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
