package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Wcprice;

/**
 * WCPRICE (Weighted Close Price): tulip_rs_java only. ta4j 0.19 has no
 * WeightedClose indicator equivalent; the class list grep shows none.
 */
public final class BenchWcprice implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("wcprice")
                .options(new double[][]{{}}) // matches Go/Python: no options
                .tulip((s, o) -> {
                    Outcome oc = Wcprice.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: no WeightedClose in ta4j-core-0.19.jar
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Wcprice.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions omitted: wcprice has no options
                .build();
    }
}
