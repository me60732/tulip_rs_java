package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Elderray;

/**
 * Elderray (Elder Ray) — tulip_rs_java only.
 * No ta4j 0.19 equivalent: bullPower/bearPower not in ta4j core.
 */
public final class BenchElderray implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("elderray")
                .options(new double[][]{{5.0}, {13.0}, {20.0}, {30.0}})
                .tulip((s, o) -> {
                    Outcome oc = Elderray.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        // Elderray has 3 output rows: bullPower, bearPower, medprice
                        for (int row = 0; row < res.numOutputs(); row++) {
                            if (res.rowLength(row) > 0) {
                                Harness.consume(res.get(row, 0));
                            }
                        }
                    }
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Elderray.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Elderray.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
