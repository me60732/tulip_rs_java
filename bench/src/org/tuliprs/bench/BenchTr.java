package org.tuliprs.bench;

import org.ta4j.core.indicators.helpers.TRIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Tr;

/**
 * TR (True Range) — tulip_rs_java vs ta4j TRIndicator.
 * ta4j constructor: new TRIndicator(series) — 0-option fixed window, tulip tr sweeps nothing.
 */
public final class BenchTr implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("tr")
                .options(new double[][]{{}}) // TR has no options (matches Go/Python)
                .tulip((s, o) -> {
                    Outcome oc = Tr.indicator(new double[][]{s.high, s.low, s.close});
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new TRIndicator(Ta4j.series(s)))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Tr.simdByAssets(assets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
