package org.tuliprs.bench;

import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Medprice;

/**
 * MEDPRICE — tulip_rs_java vs ta4j MedianPriceIndicator.
 * ta4j constructor: new MedianPriceIndicator(series, (int)opts[0]).
 * Note: Medprice facade has 0 options; ta4j's ctor takes timeFrame parameter.
 * Since tulip sweeps no options but ta4j requires a timeFrame, registration omitted with comment.
 */
public final class BenchMedprice implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("medprice")
                .options(new double[][]{{}}) // matches Go/Python (no swept options)
                .tulip((s, o) -> {
                    Outcome oc = Medprice.indicator(new double[][]{s.high, s.low}, new double[]{});
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: MedianPriceIndicator requires timeFrame param but tulip has no options
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Medprice.simdByAssets(assets, new double[]{})) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions omitted: Medprice facade has no simdByOptions method
                .build();
    }
}
