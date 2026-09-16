package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Ccfisher;

/**
 * CCFISHER (Cyber Cycle Fisher) — tulip_rs_java vs ta4j.
 * No param-compatible ta4j twin exists (Ehlers-specific, omit .ta4j(...) with comment).
 */
public final class BenchCcfisher implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("ccfisher")
                // matches Go bench_ccfisher.go Options: {{0.0}, {0.05}, {0.07}, {0.10}}
                .options(new double[][]{{0.0}, {0.05}, {0.07}, {0.10}})
                .tulip((s, o) -> {
                    Outcome oc = Ccfisher.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j: no CCFISHER/Ehlers-specific indicator in 0.19 — omit with comment
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Ccfisher.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Ccfisher.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
