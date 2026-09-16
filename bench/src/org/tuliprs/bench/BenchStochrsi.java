package org.tuliprs.bench;

import org.ta4j.core.indicators.StochasticRSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Stochrsi;

/**
 * Stochrsi: tulip_rs_java vs ta4j StochasticRSIIndicator.
 * javap ctor: StochasticRSIIndicator(Indicator<Num>, int) — compatible with close + period.
 */
public final class BenchStochrsi implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("stochrsi")
                .options(new double[][]{{14.0}, {20.0}, {25.0}, {30.0}}) // matches Go/Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Stochrsi.indicator(new double[][]{s.close}, o);
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
                        new StochasticRSIIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Stochrsi.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(0, 1, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Stochrsi.simdByOptions(new double[][]{s.close}, optSets)) {
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
