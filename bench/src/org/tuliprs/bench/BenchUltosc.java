package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Ultosc;

/**
 * ULTOSC (Ultimate Oscillator): tulip_rs_java vs ta4j UltimateOscillator.
 * ta4j has no param-compatible twin in 0.19 (no period parameter variant),
 * so ta4jFn is omitted with a comment.
 */
public final class BenchUltosc implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("ultosc")
                .options(new double[][]{{7.0, 14.0, 28.0}, {4.0, 8.0, 16.0}, {5.0, 10.0, 20.0}, {6.0, 12.0, 24.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Ultosc.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // no ta4j param-compatible twin in 0.19
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Ultosc.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Ultosc.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
