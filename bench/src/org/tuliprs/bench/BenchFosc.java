package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Fosc;

/**
 * FOSC (Fractal Oscillator) — tulip_rs_java vs no ta4j twin.
 * No ta4j counterpart: force oscillator not in TA4J_MAP.md (Ehlers/Tulip-specific).
 */
public final class BenchFosc implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("fosc")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Fosc.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j(null) // no ta4j twin — force oscillator not in TA4J_MAP.md
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Fosc.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Fosc.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
