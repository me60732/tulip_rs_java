package org.tuliprs.bench;

import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Tsf;

/**
 * TSF (Time Series Forecast): tulip_rs_java vs ta4j SimpleLinearRegressionIndicator.
 * ta4j constructor: new SimpleLinearRegressionIndicator(close, (int)opts[0], Type.Y).
 * Note: tsf projects the END value via least-squares fit; ta4j's Y type returns
 * the fitted line's y-value at each bar (endpoint projection), matching tulip semantics.
 */
public final class BenchTsf implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("tsf")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Tsf.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new SimpleLinearRegressionIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0], SimpleLinearRegressionIndicator.SimpleLinearRegressionType.Y))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Tsf.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Tsf.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
