package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Mom;

/**
 * MOM (Momentum) — tulip_rs_java only.
 * ta4j has no equivalent: NetMomentumIndicator is volume-weighted, not absolute price diff.
 */
public final class BenchMom implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("mom")
                .options(new double[][]{{25.0}, {30.0}, {50.0}, {100.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Mom.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // no ta4j twin: ta4j NetMomentumIndicator is volume-weighted
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Mom.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Mom.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
