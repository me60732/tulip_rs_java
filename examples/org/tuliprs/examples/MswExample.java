package org.tuliprs.examples;

import java.util.Arrays;

import org.tuliprs.Format;
import org.tuliprs.Info;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.demo.Demo;
import org.tuliprs.indicators.Msw;

/**
 * MSW example: full compute, streaming continuation, state persistence
 * (serialize / deserialize / duplicate), and both SIMD modes — the Java
 * mirror of the Go/C examples, with every step verified.
 */
public final class MswExample {

    public static void main(String[] args) {
        Demo.Check c = new Demo.Check();
        
        // Test with multiple periods to verify data sensitivity
        double[][] testPeriods = {{9.0}, {14.0}, {21.0}, {25.0}};
        double[] options = testPeriods[1]; // period=14 as base
        
        Info info = Msw.info();
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
        int n = 2 * (int) Msw.minData(options) + 100;
        String[] names = info.inputs().toArray(new String[0]);
        double[][] series = Demo.seriesFor(names, n);
        double[] close = series[0];

        // ---- full compute --------------------------------------------------
        System.out.println("\n=== full calculation (all outputs) ===");
        Outcome oc = Msw.indicator(series, options, null);
        double[] fullSlope;
        double[] fullLead;
        try (Result res = oc.result()) {
            fullSlope = res.toDoubleArray(0);
            fullLead = res.toDoubleArray(1);
            String[] rowNames = rowNames(info);
            for (int i = 0; i < res.numOutputs(); i++) {
                System.out.printf("  %-6s %d values%n", rowNames[i], res.rowLength(i));
            }
        }
        oc.state().close();

        // ---- partial + batch continuation ----------------------------------
        System.out.println("\n=== partial calculation + batch continuation ===");
        int partial = n - 50;
        Outcome p = Msw.indicator(slices(series, 0, partial), options, null);
        try (Result pr = p.result(); State pst = p.state()) {
            Result br = pst.batch(slices(series, partial, n));
            try (br) {
                double[] continuedSlope = br.toDoubleArray(0);
                double[] continuedLead = br.toDoubleArray(1);
                
                double[] tailSlope = Arrays.copyOfRange(fullSlope, fullSlope.length - continuedSlope.length,
                        fullSlope.length);
                double[] tailLead = Arrays.copyOfRange(fullLead, fullLead.length - continuedLead.length,
                        fullLead.length);
                
                c.match("partial+continued slope equals full recompute", Demo.same(tailSlope, continuedSlope));
                c.match("partial+continued lead equals full recompute", Demo.same(tailLead, continuedLead));

                // ---- persistence -------------------------------------------
                System.out.println(
                        "\n=== state persistence (serialize / deserialize / clone) ===");
                byte[] blob = pst.serialize(Format.BINCODE);
                System.out.printf("  bincode blob: %d bytes (indicator id 0x%08x)%n",
                        blob.length, Msw.ID);
                State rs = Msw.deserializeState(blob);
                State cl = pst.duplicate();
                try (rs; cl) {
                    double[][] rest = slices(series, partial, n);
                    Result b1 = pst.batch(rest);
                    Result b2 = rs.batch(rest);
                    Result b3 = cl.batch(rest);
                    try (b1; b2; b3) {
                        c.match("deserialized state continues identically (slope)",
                                Demo.same(b1.toDoubleArray(0), b2.toDoubleArray(0)));
                        c.match("deserialized state continues identically (lead)",
                                Demo.same(b1.toDoubleArray(1), b2.toDoubleArray(1)));
                        c.match("cloned state continues identically (slope)",
                                Demo.same(b1.toDoubleArray(0), b3.toDoubleArray(0)));
                        c.match("cloned state continues identically (lead)",
                                Demo.same(b1.toDoubleArray(1), b3.toDoubleArray(1)));
                    }
                }
            }
        }

        // ---- SIMD by assets --------------------------------------------------
        System.out.println("\n=== SIMD by assets (N=2) ===");
        double[][] scaled = slices(new double[][] {
                Demo.scale(close, 1.2)}, 0, n);
        double[][][] assets = {series, scaled};
        try (SimdResult sim = Msw.simdByAssets(assets, options, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Msw.indicator(assets[i], options, null);
                try (Result ind = r.result()) {
                    c.match("SIMD asset " + (i + 1) + " slope equals individual",
                            Demo.sameTol(sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-5, 1e-7));
                    c.match("SIMD asset " + (i + 1) + " lead equals individual",
                            Demo.sameTol(sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-5, 1e-7));
                }
            }
        }

        // ---- SIMD by options -------------------------------------------------
        System.out.println("\n=== SIMD by options (N=4) ===");
        try (SimdResult sim = Msw.simdByOptions(series, testPeriods, null)) {
            for (int i = 0; i < sim.numResults(); i++) {
                Outcome r = Msw.indicator(series, testPeriods[i], null);
                try (Result ind = r.result()) {
                    c.match("SIMD option set " + (i + 1) + " slope equals individual",
                            Demo.sameTol(sim.toDoubleArray(i, 0), ind.toDoubleArray(0), 1e-4, 1e-6));
                    c.match("SIMD option set " + (i + 1) + " lead equals individual",
                            Demo.sameTol(sim.toDoubleArray(i, 1), ind.toDoubleArray(1), 1e-4, 1e-6));
                }
            }
        }

        c.done();
    }

    /** Mandatory output name first, then optional output names (none for MSW). */
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
