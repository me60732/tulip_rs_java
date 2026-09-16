package org.tuliprs.bench;

import org.ta4j.core.indicators.volume.AccumulationDistributionIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Ad;

/**
 * AD (Accumulation/Distribution Line): tulip_rs_java vs ta4j AccumulationDistributionIndicator.
 * ta4j equivalent has no options, matching tulip's 0-option grid.
 */
public final class BenchAd implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("ad")
                .options(new double[][]{{}}) // matches Go/Python: options_list=[[]]
                .tulip((s, o) -> {
                    Outcome oc = Ad.indicator(new double[][]{s.high, s.low, s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new AccumulationDistributionIndicator(Ta4j.series(s)))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Ad.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // ad has no options, so simd_by_options not applicable
                .build();
    }
}
