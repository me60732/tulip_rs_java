package org.tuliprs.bench;

import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Vwap;

/**
 * VWAP (Volume Weighted Average Price): tulip_rs_java vs ta4j VWAPIndicator.
 * ta4j constructor: new VWAPIndicator(series, timeframe). The Go bench shows
 * no options (tulip vwap has no swept parameters), so we register the ta4j twin
 * with a fixed default timeframe of 1 (matches the empty option grid).
 */
public final class BenchVwap implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("vwap")
                .options(new double[][]{{}}) // matches Go/Python: no options
                .tulip((s, o) -> {
                    Outcome oc = Vwap.indicator(new double[][]{s.high, s.low, s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new VWAPIndicator(Ta4j.series(s), 1)))) // timeframe=1 matches empty options
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Vwap.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions omitted: vwap has no options
                .build();
    }
}
