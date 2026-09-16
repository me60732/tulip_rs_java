package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Roofingfilter;

/**
 * RoofingFilter — tulip_rs_java benchmark.
 * No ta4j twin (roofingfilter is an Ehlers filter, not in ta4j 0.19).
 */
public final class BenchRoofingfilter implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("roofingfilter")
                .options(new double[][]{{10.0, 20.0}, {15.0, 30.0}, {20.0, 40.0}, {25.0, 50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Roofingfilter.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // No ta4j counterpart — roofingfilter not in ta4j 0.19
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Roofingfilter.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Roofingfilter.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
