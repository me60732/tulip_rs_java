package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Fisher;

/**
 * Fisher (Fisher Transform) — tulip_rs_java vs cinar momentum.EhlersFisher.
 * ta4j: FisherIndicator(series) fixed 9 window ≠ swept period — OMIT (comment).
 */
public final class BenchFisher implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("fisher")
                .options(new double[][]{{5.0}, {9.0}, {14.0}, {20.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Fisher.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j(null) // FisherIndicator fixed 9 window ≠ swept period; see TA4J_MAP.md
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Fisher.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Fisher.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
