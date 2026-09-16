package org.tuliprs.bench;

import org.ta4j.core.indicators.WilliamsRIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Willr;

/** Williams %R: tulip_rs_java vs ta4j WilliamsRIndicator (period-swept). */
public final class BenchWillr implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("willr")
                .options(new double[][]{{25.0}, {35.0}, {50.0}, {100.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Willr.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        for (int row = 0; row < res.numOutputs(); row++) {
                            if (res.rowLength(row) > 0) {
                                Harness.consume(res.get(row, 0));
                            }
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new WilliamsRIndicator(Ta4j.series(s), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{
                                stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Willr.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Willr.simdByOptions(
                            new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
