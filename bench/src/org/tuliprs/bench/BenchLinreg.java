package org.tuliprs.bench;

import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Linreg;

/**
 * Linreg (Linear Regression): tulip_rs_java vs ta4j SimpleLinearRegressionIndicator.
 * ta4j constructor: new SimpleLinearRegressionIndicator(series, (int)opts[0], Type.Y).
 * Only the Y (closure/value at end) output matches tulip's single-row behavior.
 */
public final class BenchLinreg implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("linreg")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Linreg.indicator(new double[][]{s.close}, o);
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
                    try (SimdResult sim = Linreg.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Linreg.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
