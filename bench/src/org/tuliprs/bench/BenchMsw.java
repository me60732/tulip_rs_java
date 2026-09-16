package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Msw;

/**
 * MSW (Mesa Sine Wave) — tulip_rs_java only.
 * ta4j has no equivalent: no Mesa Sine Wave function in cinar v2/ta4j 0.19.
 */
public final class BenchMsw implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("msw")
                .options(new double[][]{{5.0}, {8.0}, {14.0}, {20.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Msw.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        // Consume both rows: sine, lead.
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                        if (res.numOutputs() > 1 && res.rowLength(1) > 0) {
                            Harness.consume(res.get(1, 0));
                        }
                    }
                })
                // no ta4j twin: no MSW function
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Msw.simdByAssets(assets, o)) {
                        // Consume both rows: sine, lead.
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(1, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Msw.simdByOptions(new double[][]{s.close}, optSets)) {
                        // Consume both rows: sine, lead.
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(1, 0, 0));
                        }
                    }
                })
                .build();
    }
}
