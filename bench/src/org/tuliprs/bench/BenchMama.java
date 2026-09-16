package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Mama;

/**
 * MAMA (MESA Adaptive Moving Average): tulip_rs_java vs ta4j.
 * No ta4j twin exists — MAMA is not in ta4j 0.19 (listed as "mama" in TA4J_MAP.md nil section).
 */
public final class BenchMama implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("mama")
                .options(new double[][]{{0.5, 0.05}, {0.4, 0.04}, {0.6, 0.06}, {0.7, 0.07}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Mama.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        // Consume both mandatory rows (mama, dc_period)
                        for (int i = 0; i < res.numOutputs(); i++) {
                            if (res.rowLength(i) > 0) {
                                Harness.consume(res.get(i, 0));
                            }
                        }
                    }
                })
                // ta4j: no twin — MAMA not in ta4j-core-0.19.jar
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Mama.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Mama.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
