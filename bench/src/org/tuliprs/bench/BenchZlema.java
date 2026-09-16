package org.tuliprs.bench;

import org.ta4j.core.indicators.averages.ZLEMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Zlema;

/** ZLEMA: tulip_rs_java vs ta4j ZLEMAIndicator (period-swept). */
public final class BenchZlema implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("zlema")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {50.0}}) // matches Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Zlema.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        for (int row = 0; row < res.numOutputs(); row++) {
                            if (res.rowLength(row) > 0) {
                                Harness.consume(res.get(row, 0));
                            }
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(new ZLEMAIndicator(
                        new ClosePriceIndicator(Ta4j.series(s)), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Zlema.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Zlema.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
