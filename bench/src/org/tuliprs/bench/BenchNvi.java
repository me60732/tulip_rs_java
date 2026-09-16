package org.tuliprs.bench;

import org.ta4j.core.indicators.volume.NVIIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Nvi;

/**
 * NVI (Negative Volume Index) — tulip_rs_java vs ta4j NVIIndicator.
 * ta4j constructor: new NVIIndicator(series). No options (tulip has none).
 */
public final class BenchNvi implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("nvi")
                .options(new double[][]{{}}) // matches Go/Python: no options
                .tulip((s, o) -> {
                    Outcome oc = Nvi.indicator(new double[][]{s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new NVIIndicator(Ta4j.series(s)))))
                // simdOptions not applicable: nvi has no options
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Nvi.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
