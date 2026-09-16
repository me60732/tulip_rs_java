package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Apo;

/**
 * APO (Average Price Oscillator) — tulip_rs_java benchmark.
 * ta4j: No param-compatible twin — PPO is percentage-based while APO is absolute
 * difference; TA4J_MAP.md lists apo as nil.
 */
public final class BenchApo implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("apo")
                .options(new double[][]{{5.0, 13.0}, {8.0, 18.0}, {12.0, 26.0}, {3.0, 9.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Apo.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: TA4J_MAP.md lists apo as nil; PPO is percentage-based
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Apo.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Apo.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
