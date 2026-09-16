package org.tuliprs.bench;

import org.ta4j.core.indicators.PPOIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Ppo;

/**
 * PPO (Percentage Price Oscillator) — tulip_rs_java vs ta4j PPOIndicator.
 * ta4j constructor: new PPOIndicator(close, short_period, long_period).
 */
public final class BenchPpo implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("ppo")
                .options(new double[][]{{12.0, 26.0}, {8.0, 18.0}, {5.0, 13.0}, {3.0, 9.0}}) // matches Go/Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Ppo.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new PPOIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0], (int) o[1]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Ppo.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Ppo.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
