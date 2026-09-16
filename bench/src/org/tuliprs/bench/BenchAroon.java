package org.tuliprs.bench;

import org.ta4j.core.indicators.aroon.AroonDownIndicator;
import org.ta4j.core.indicators.aroon.AroonUpIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Aroon;

/**
 * AROON — tulip_rs_java vs ta4j AroonUpIndicator + AroonDownIndicator.
 */
public final class BenchAroon implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("aroon")
                .options(new double[][]{{25.0}, {35.0}, {50.0}, {100.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Aroon.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        java.util.List.of(
                                new AroonUpIndicator(new HighPriceIndicator(Ta4j.series(s)), (int) o[0]),
                                new AroonDownIndicator(new LowPriceIndicator(Ta4j.series(s)), (int) o[0])))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Aroon.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Aroon.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
