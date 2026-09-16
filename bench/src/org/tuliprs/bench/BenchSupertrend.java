package org.tuliprs.bench;

import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Supertrend;

/**
 * Supertrend — tulip_rs_java vs ta4j SuperTrendIndicator.
 * ta4j constructor: new SuperTrendIndicator(series, (int)o[0], o[1]) — period + multiplier match.
 */
public final class BenchSupertrend implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("supertrend")
                .options(new double[][]{{7.0, 3.0}, {5.0, 2.0}, {10.0, 2.5}, {14.0, 2.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Supertrend.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new SuperTrendIndicator(Ta4j.series(s), (int) o[0], o[1]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Supertrend.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Supertrend.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
