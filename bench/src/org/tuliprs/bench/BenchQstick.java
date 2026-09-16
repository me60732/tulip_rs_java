package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Qstick;

/**
 * QStick — tulip_rs_java benchmark.
 * No ta4j twin (qstick not in ta4j 0.19).
 */
public final class BenchQstick implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("qstick")
                .options(new double[][]{{5.0}, {8.0}, {14.0}, {20.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Qstick.indicator(new double[][]{s.open, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].open, stocks[i].close};
                    }
                    try (SimdResult sim = Qstick.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Qstick.simdByOptions(new double[][]{s.open, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
