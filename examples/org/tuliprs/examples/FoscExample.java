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
import org.tuliprs.indicators.Fosc;

/**
 * FOSC example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class FoscExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        double[] options = {10.0}; // period

        Info info = Fosc.info();
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
        int n = 2 * (int) Fosc.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] real = series[0];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all optional outputs) ===");
        Outcome oc = Fosc.indicator(series, options, new boolean[] {true, true, true, true, true});
        double[] fullFosc;
        double[] fullTsf;
        double[] fullLinreg;
        double[] fullLinregslope;
        double[] fullLinregintercept;
        try (Result res = oc.result()) {
            fullFosc = res.toDoubleArray(0);
            fullTsf = res.toDoubleArray(1);
            fullLinreg = res.toDoubleArray(2);
            fullLinregslope = res.toDoubleArray(3);
            fullLinregintercept = res.toDoubleArray(4);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Fosc.indicator(slices(series, 0, partial), options, new boolean[] {true, true, true, true, true});
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n), new boolean[] {true, true, true, true, true});
            try (br) {
                double[] continuedFosc = br.toDoubleArray(0);
                double[] continuedTsf = br.toDoubleArray(1);
                double[] continuedLinreg = br.toDoubleArray(2);
                double[] continuedLinregslope = br.toDoubleArray(3);
                double[] continuedLinregintercept = br.toDoubleArray(4);
                double[] tailFosc = Arrays.copyOfRange(fullFosc, fullFosc.length - continuedFosc.length,
                        fullFosc.length);
                double[] tailTsf = Arrays.copyOfRange(fullTsf, fullTsf.length - continuedTsf.length,
                        fullTsf.length);
                double[] tailLinreg = Arrays.copyOfRange(fullLinreg, fullLinreg.length - continuedLinreg.length,
                        fullLinreg.length);
                double[] tailLinregslope = Arrays.copyOfRange(fullLinregslope, fullLinregslope.length - continuedLinregslope.length,
                        fullLinregslope.length);
                double[] tailLinregintercept = Arrays.copyOfRange(fullLinregintercept, fullLinregintercept.length - continuedLinregintercept.length,
                        fullLinregintercept.length);
                c.match("partial+continued fosc equals full recompute", Demo.same(tailFosc, continuedFosc));
                c.match("partial+continued tsf equals full recompute", Demo.same(tailTsf, continuedTsf));
                c.match("partial+continued linreg equals full recompute", Demo.same(tailLinreg, continuedLinreg));
                c.match("partial+continued linregslope equals full recompute", Demo.same(tailLinregslope, continuedLinregslope));
                c.match("partial+continued linregintercept equals full recompute", Demo.same(tailLinregintercept, continuedLinregintercept));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Fosc.ID);
                State rs = Fosc.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest, new boolean[] {true, true, true, true, true});
                    Result b2 = rs.batch(rest, new boolean[] {true, true, true, true, true});
                    Result b3 = cl.batch(rest, new boolean[] {true, true, true, true, true});
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically fosc",
                                Demo.same(b1.toDoubleArray(0), b2.toDoubleArray(0)));
                        c.match("cloned state continues identically fosc",
                                Demo.same(b1.toDoubleArray(0), b3.toDoubleArray(0)));
                        c.match("deserialized state continues identically tsf",
                                Demo.same(b1.toDoubleArray(1), b2.toDoubleArray(1)));
                        c.match("cloned state continues identically tsf",
                                Demo.same(b1.toDoubleArray(1), b3.toDoubleArray(1)));
                        c.match("deserialized state continues identically linreg",
                                Demo.same(b1.toDoubleArray(2), b2.toDoubleArray(2)));
                        c.match("cloned state continues identically linreg",
                                Demo.same(b1.toDoubleArray(2), b3.toDoubleArray(2)));
                        c.match("deserialized state continues identically linregslope",
                                Demo.same(b1.toDoubleArray(3), b2.toDoubleArray(3)));
                        c.match("cloned state continues identically linregslope",
                                Demo.same(b1.toDoubleArray(3), b3.toDoubleArray(3)));
                        c.match("deserialized state continues identically linregintercept",
                                Demo.same(b1.toDoubleArray(4), b2.toDoubleArray(4)));
                        c.match("cloned state continues identically linregintercept",
                                Demo.same(b1.toDoubleArray(4), b3.toDoubleArray(4)));

                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {Demo.scale(real, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Fosc.simdByAssets(assets, options, new boolean[] {true, true, true, true, true})) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Fosc.indicator(assets[i], options, new boolean[] {true, true, true, true, true});
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD asset " + (i + 1) + " fosc equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " tsf equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " linreg equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 2), ind.toDoubleArray(2), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " linregslope equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 3), ind.toDoubleArray(3), 1e-6, 1e-9));
                    c.match("SIMD asset " + (i + 1) + " linregintercept equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 4), ind.toDoubleArray(4), 1e-6, 1e-9));

                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        double[][] optSets = {{5}, {10}, {20}, {30}};
        try (SimdResult sim = Fosc.simdByOptions(series, optSets, new boolean[] {true, true, true, true, true})) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Fosc.indicator(series, optSets[i], new boolean[] {true, true, true, true, true});
                try (Result ind = r.result(); State st2 = r.state()) {
                    c.match("SIMD option set " + (i + 1) + " fosc equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " tsf equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " linreg equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 2), ind.toDoubleArray(2), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " linregslope equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 3), ind.toDoubleArray(3), 1e-6, 1e-9));
                    c.match("SIMD option set " + (i + 1) + " linregintercept equals individual", Demo.sameTol(
                            sim.toDoubleArray(i, 4), ind.toDoubleArray(4), 1e-6, 1e-9));

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
