package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Cvi;

/**
 * CVI (Chaikin Volatility Index) — tulip_rs_java only.
 * ta4j: Chande CVI absent from ta4j 0.19; RWI ≠ cvi; omit with comment.
 */
public final class BenchCvi implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("cvi")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Cvi.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j: Chande CVI absent from ta4j 0.19
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Cvi.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Cvi.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
