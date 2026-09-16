package org.tuliprs.bench;

import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Macd;

/**
 * MACD (Moving Average Convergence Divergence): tulip_rs_java vs ta4j MACDIndicator.
 * ta4j constructor: new MACDIndicator(series, (int)opts[0], (int)opts[1]).
 * tulip macd opts {fastperiod, slowperiod, signalperiod}: register MACDIndicator
 * with first two opts (histogram/signal row separately not consumed in this benchmark).
 */
public final class BenchMacd implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("macd")
                .options(new double[][]{{5.0, 13.0}, {19.0, 39.0}, {10.0, 30.0}, {6.0, 20.0}}) // matches Go/Python (first two opts)
                .tulip((s, o) -> {
                    Outcome oc = Macd.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        // Consume all three rows (macd, short_ema, long_ema)
                        for (int i = 0; i < res.numOutputs(); i++) {
                            if (res.rowLength(i) > 0) {
                                Harness.consume(res.get(i, 0));
                            }
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new MACDIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0], (int) o[1]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Macd.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Macd.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
