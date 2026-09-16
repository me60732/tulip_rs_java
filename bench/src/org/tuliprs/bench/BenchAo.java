package org.tuliprs.bench;

import org.ta4j.core.indicators.AwesomeOscillatorIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Ao;

/**
 * AO (Awesome Oscillator): tulip_rs_java vs ta4j AwesomeOscillatorIndicator.
 * ta4j constructor: new AwesomeOscillatorIndicator(series) fixed 5/34 windows == tulip defaults.
 */
public final class BenchAo implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("ao")
                .options(new double[][]{{}}) // matches Go/Python options_list=[[]]
                .tulip((s, o) -> {
                    Outcome oc = Ao.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new AwesomeOscillatorIndicator(Ta4j.series(s)))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Ao.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
