package org.tuliprs.bench;

import org.ta4j.core.indicators.pivotpoints.PivotPointIndicator;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Pivotpoint;

/**
 * PIVOTPOINT — tulip_rs_java vs ta4j PivotPointIndicator.
 * ta4j constructor: new PivotPointIndicator(series, Method.STANDARD) — fixed method, no period parameter.
 * tulip sweeps period options {5,14,20,30} but ta4j's ctor accepts only Method enum → registration omitted.
 */
public final class BenchPivotpoint implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("pivotpoint")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {30.0}}) // matches Go/Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Pivotpoint.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j PivotPointIndicator uses TimeLevel enum, not period parameter.
                // tulip sweeps period options {5,14,20,30} but ta4j accepts only TimeLevel(BARBASED/DAY/WEEK/MONTH/YEAR).
                .ta4j(null)
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Pivotpoint.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Pivotpoint.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
