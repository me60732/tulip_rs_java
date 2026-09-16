package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Kvo;

/**
 * KVO (Klinger Volume Oscillator): tulip_rs_java vs ta4j.
 * No ta4j twin exists — KVO is not in ta4j 0.19.
 */
public final class BenchKvo implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("kvo")
                .options(new double[][]{{34.0, 55.0}, {20.0, 40.0}, {10.0, 30.0}, {5.0, 20.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Kvo.indicator(new double[][]{s.high, s.low, s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j: no twin — KVO not in ta4j-core-0.19.jar
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Kvo.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Kvo.simdByOptions(new double[][]{s.high, s.low, s.close, s.volume}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
