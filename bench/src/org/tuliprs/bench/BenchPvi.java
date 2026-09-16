package org.tuliprs.bench;

import org.ta4j.core.indicators.volume.PVIIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Pvi;

/**
 * PVI (Positive Volume Index) — tulip_rs_java vs ta4j PVIIndicator.
 * ta4j constructor: new PVIIndicator(series) — 0-option fixed.
 */
public final class BenchPvi implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("pvi")
                .options(new double[][]{{}}) // matches Go/Python empty options_list
                .tulip((s, o) -> {
                    Outcome oc = Pvi.indicator(new double[][]{s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new PVIIndicator(Ta4j.series(s)))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Pvi.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions(null) // pvi has no options, so simd_by_options not applicable
                .build();
    }
}
