package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Avgprice;

/**
 * AVGPRICE — tulip_rs_java benchmark.
 * ta4j: No 4/5-price-average twin exists; omitted per TA4J_MAP.md rules.
 */
public final class BenchAvgprice implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("avgprice")
                .options(new double[][]{{}}) // AVGPRICE has no options, matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Avgprice.indicator(new double[][]{s.open, s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: no 4/5-price-average twin in ta4j 0.19
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].open, stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Avgprice.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions omitted: no options means this phase is not applicable
                .build();
    }
}
