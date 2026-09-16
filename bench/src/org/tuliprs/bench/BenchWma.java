package org.tuliprs.bench;

import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Wma;

/** WMA: tulip_rs_java vs ta4j WMAIndicator (period-swept). */
public final class BenchWma implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("wma")
                .options(new double[][]{{14.0}, {20.0}, {25.0}, {30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Wma.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        for (int row = 0; row < res.numOutputs(); row++) {
                            if (res.rowLength(row) > 0) {
                                Harness.consume(res.get(row, 0));
                            }
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(new WMAIndicator(
                        new ClosePriceIndicator(Ta4j.series(s)), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Wma.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Wma.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
