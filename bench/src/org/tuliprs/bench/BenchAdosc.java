package org.tuliprs.bench;

import org.ta4j.core.indicators.volume.ChaikinOscillatorIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Adosc;

/**
 * ADOSC (Accumulation/Distribution Oscillator): tulip_rs_java vs ta4j ChaikinOscillatorIndicator.
 * ta4j constructor: new ChaikinOscillatorIndicator(series, short_period, long_period).
 */
public final class BenchAdosc implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("adosc")
                .options(new double[][]{{2.0, 5.0}, {3.0, 10.0}, {5.0, 20.0}, {10.0, 30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Adosc.indicator(new double[][]{s.high, s.low, s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new ChaikinOscillatorIndicator(Ta4j.series(s), (int) o[0], (int) o[1]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Adosc.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Adosc.simdByOptions(new double[][]{s.high, s.low, s.close, s.volume}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
