package org.tuliprs.bench;

import org.ta4j.core.indicators.MassIndexIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Mass;

/**
 * MASS (Mass Index) — tulip_rs_java vs ta4j MassIndexIndicator.
 * ta4j constructor: new MassIndexIndicator(series).
 * Note: ta4j's MassIndexIndicator uses fixed 25/9 windows; tulip sweeps period option.
 * Registration omitted with comment per TA4J_MAP.md mass entry.
 */
public final class BenchMass implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("mass")
                .options(new double[][]{{14.0}, {20.0}, {25.0}, {30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Mass.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: MassIndexIndicator fixed 25/9 windows, tulip sweeps period param
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Mass.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Mass.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
