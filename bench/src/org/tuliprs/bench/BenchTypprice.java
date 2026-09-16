package org.tuliprs.bench;

import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Typprice;

/**
 * TYPPRICE (Typical Price): tulip_rs_java vs ta4j TypicalPriceIndicator.
 * ta4j constructor: new TypicalPriceIndicator(series) (0-option).
 */
public final class BenchTypprice implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("typprice")
                .options(new double[][]{{}}) // no options — matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Typprice.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new TypicalPriceIndicator(Ta4j.series(s))))
                )
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Typprice.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions omitted: typprice has no options to SIMD
                .build();
    }
}
