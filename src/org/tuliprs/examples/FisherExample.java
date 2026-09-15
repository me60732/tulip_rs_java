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
import org.tuliprs.indicators.Fisher;

/**
 * Fisher example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class FisherExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {10.0}; // period

        Info info = Fisher.info();
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
        int n = 2 * (int) Fisher.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] high = series[0], low = series[1];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation ===");
        Outcome oc = Fisher.indicator(series, options, null);
        double[] fullFisher;
        double[] fullFishersignal;
        try (Result res = oc.result()) {
            fullFisher = res.toDoubleArray(0);
            fullFishersignal = res.toDoubleArray(1);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Fisher.indicator(slices(series, 0, partial), options, null);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n));
            try (br) {
                double[] continuedFisher = br.toDoubleArray(0);
                double[] continuedFishersignal = br.toDoubleArray(1);
                double[] tailFisher = Arrays.copyOfRange(fullFisher, fullFisher.length - continuedFisher.length,
                        fullFisher.length);
                double[] tailFishersignal = Arrays.copyOfRange(fullFishersignal, fullFishersignal.length - continuedFishersignal.length,
                        fullFishersignal.length);
                c.match("partial+continued fisher equals full recompute", Demo.same(tailFisher, continuedFisher));
                c.match("partial+continued fishersignal equals full recompute", Demo.same(tailFishersignal, continuedFishersignal));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Fisher.ID);
                State rs = Fisher.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest);
                    Result b2 = rs.batch(rest);
                    Result b3 = cl.batch(rest);
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically fisher",
                                Demo.same(b1.toDoubleArray(0), b2.toDoubleArray(0)));
                        c.match("cloned state continues identically fisher",
                                Demo.same(b1.toDoubleArray(0), b3.toDoubleArray(0)));
                        c.match("deserialized state continues identically fishersignal",
                                Demo.same(b1.toDoubleArray(1), b2.toDoubleArray(1)));
                        c.match("cloned state continues identically fishersignal",
                                Demo.same(b1.toDoubleArray(1), b3.toDoubleArray(1)));

                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {
                Demo.scale(high, 1.2), Demo.scale(low, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Fisher.simdByAssets(assets, options, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Fisher.indicator(assets[i], options, null);
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD asset " + (i + 1) + " fisher equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " fishersignal equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));

                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{5}, {10}, {20}, {30}};
        try (SimdResult sim = Fisher.simdByOptions(series, optSets, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Fisher.indicator(series, optSets[i], null);
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD option set " + (i + 1) + " fisher equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " fishersignal equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));

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
