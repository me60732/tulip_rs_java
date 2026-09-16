package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Emv;

/**
 * EMV (Ease of Movement) — tulip_rs_java vs cinar volume.EMV.
 * No ta4j twin: not in TA4J_MAP.md (Ehlers/Tulip-specific).
 */
public final class BenchEmv implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("emv")
                .options(new double[][]{{}}) // matches Go/Python — no options
                .tulip((s, o) -> {
                    Outcome oc = Emv.indicator(new double[][]{s.high, s.low, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].volume};
                    }
                    try (SimdResult sim = Emv.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions(null)
                .build();
    }
}
