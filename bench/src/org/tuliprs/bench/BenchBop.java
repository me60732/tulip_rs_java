package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Bop;

/**
 * BOP (Balance of Power) — tulip_rs_java vs ta4j.
 * No param-compatible ta4j twin exists (omit .ta4j(...) with comment).
 */
public final class BenchBop implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("bop")
                // BOP has no options — matches Go bench_bop.go Options: {{}}
                .options(new double[][]{{}})
                .tulip((s, o) -> {
                    Outcome oc = Bop.indicator(new double[][]{s.open, s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j: no BOP indicator in 0.19 — omit with comment
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].open, stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Bop.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions: nil — BOP has no options to sweep
                .build();
    }
}
