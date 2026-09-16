package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Vortex;

/**
 * VORTEX: tulip_rs_java only.
 * ta4j has no Vortex indicator in 0.19 (jar contains no vortex class),
 * so ta4jFn is omitted with a comment.
 */
public final class BenchVortex implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("vortex")
                .options(new double[][]{{14.0}, {20.0}, {25.0}, {30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Vortex.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // no ta4j Vortex indicator in 0.19
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Vortex.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Vortex.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
