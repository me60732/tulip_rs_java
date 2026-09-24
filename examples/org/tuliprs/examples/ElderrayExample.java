package org.tuliprs.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Elderray;

/**
 * Elderray example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class ElderrayExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {13.0}; // period

        Info info = Elderray.info();
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
        int n = 2 * (int) Elderray.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] high = series[0], low = series[1], close = series[2];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all optional outputs) ===");
        Outcome oc = Elderray.indicator(series, options, new boolean[] {true});
        double[] fullBull;
        double[] fullBear;
        double[] fullEma;
        try (Result res = oc.result()) {
            fullBull = res.toDoubleArray(0);
            fullBear = res.toDoubleArray(1);
            fullEma = res.toDoubleArray(2);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Elderray.indicator(slices(series, 0, partial), options, new boolean[] {true});
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n), new boolean[] {true});
            try (br) {
                double[] continuedBull = br.toDoubleArray(0);
                double[] continuedBear = br.toDoubleArray(1);
                double[] continuedEma = br.toDoubleArray(2);
                double[] tailBull = Arrays.copyOfRange(fullBull, fullBull.length - continuedBull.length,
                        fullBull.length);
                double[] tailBear = Arrays.copyOfRange(fullBear, fullBear.length - continuedBear.length,
                        fullBear.length);
                double[] tailEma = Arrays.copyOfRange(fullEma, fullEma.length - continuedEma.length,
                        fullEma.length);
                c.match("partial+continued bull equals full recompute", Demo.same(tailBull, continuedBull));
                c.match("partial+continued bear equals full recompute", Demo.same(tailBear, continuedBear));
                c.match("partial+continued ema equals full recompute", Demo.same(tailEma, continuedEma));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Elderray.ID);
                State rs = Elderray.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest, new boolean[] {true});
                    Result b2 = rs.batch(rest, new boolean[] {true});
                    Result b3 = cl.batch(rest, new boolean[] {true});
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically bull",
                                Demo.same(b1.toDoubleArray(0), b2.toDoubleArray(0)));
                        c.match("cloned state continues identically bull",
                                Demo.same(b1.toDoubleArray(0), b3.toDoubleArray(0)));
                        c.match("deserialized state continues identically bear",
                                Demo.same(b1.toDoubleArray(1), b2.toDoubleArray(1)));
                        c.match("cloned state continues identically bear",
                                Demo.same(b1.toDoubleArray(1), b3.toDoubleArray(1)));
                        c.match("deserialized state continues identically ema",
                                Demo.same(b1.toDoubleArray(2), b2.toDoubleArray(2)));
                        c.match("cloned state continues identically ema",
                                Demo.same(b1.toDoubleArray(2), b3.toDoubleArray(2)));

                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {
                Demo.scale(high, 1.2), Demo.scale(low, 1.2), Demo.scale(close, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Elderray.simdByAssets(assets, options, new boolean[] {true})) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Elderray.indicator(assets[i], options, new boolean[] {true});
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD asset " + (i + 1) + " bull equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " bear equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " ema equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 2), ind.toDoubleArray(2), 1e-6, 1e-9));

                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{7}, {13}, {20}, {25}};
        try (SimdResult sim = Elderray.simdByOptions(series, optSets, new boolean[] {true})) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Elderray.indicator(series, optSets[i], new boolean[] {true});
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD option set " + (i + 1) + " bull equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " bear equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " ema equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 2), ind.toDoubleArray(2), 1e-6, 1e-9));

                }
            }
        }

        c.done();
    }

    /** Mandatory output names first, then optional output names. */
    private static String[] rowNames(Info info) {
        List<String> allOutputs = new ArrayList<>();
        for (String s : info.outputs()) {
            allOutputs.add(s);
        }
        for (String s : info.optionalOutputs()) {
            allOutputs.add(s);
        }
        return allOutputs.toArray(new String[0]);
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
