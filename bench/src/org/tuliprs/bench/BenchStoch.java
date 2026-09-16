package org.tuliprs.bench;

import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Stoch;

/**
 * Stoch: tulip_rs_java vs ta4j StochasticOscillatorKIndicator.
 * ta4j's D is a fixed-3 SMA of K; registering only K to match tulip's swept k_period param.
 */
public final class BenchStoch implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("stoch")
                .options(new double[][]{{28.0, 16.0, 12.0}, {35.0, 21.0, 14.0}, {50.0, 30.0, 21.0}, {100.0, 50.0, 30.0}}) // matches Go/Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Stoch.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                        if (res.numOutputs() > 1 && res.rowLength(1) > 0) {
                            Harness.consume(res.get(1, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new StochasticOscillatorKIndicator(Ta4j.series(s), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Stoch.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(0, 1, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Stoch.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(0, 1, 0));
                        }
                    }
                })
                .build();
    }
}
