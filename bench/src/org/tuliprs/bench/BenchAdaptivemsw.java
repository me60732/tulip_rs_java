package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Adaptivemsw;

/**
 * Adaptivemsw (Adaptive Moving Average): tulip_rs_java only.
 * No ta4j counterpart exists (AdaptiveMSW is Tulip-specific).
 */
public final class BenchAdaptivemsw implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("adaptivemsw")
                .options(new double[][]{{}}) // matches Go/Python: options_list=[[]]
                .tulip((s, o) -> {
                    Outcome oc = Adaptivemsw.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // No ta4j equivalent: AdaptiveMSW is Tulip-specific
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Adaptivemsw.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // adaptivemsw has no options, so simd_by_options not applicable
                .build();
    }
}
