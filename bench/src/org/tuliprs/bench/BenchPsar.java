package org.tuliprs.bench;

import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Psar;

/**
 * PSAR (Parabolic SAR) — tulip_rs_java vs ta4j ParabolicSarIndicator.
 * ta4j constructor: new ParabolicSarIndicator(series) — fixed default af=0.02, max=0.2.
 * tulip sweeps {acceleration_factor, maximum} pairs like {0.02,0.2}, {0.01,0.2}, etc. → registration omitted
 * because ta4j's ctor accepts no parameters and uses fixed defaults only.
 */
public final class BenchPsar implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("psar")
                .options(new double[][]{{0.02, 0.2}, {0.01, 0.2}, {0.02, 0.1}, {0.04, 0.4}}) // matches Go/Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Psar.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j ParabolicSarIndicator ctor accepts BarSeries only (fixed defaults).
                // tulip sweeps {acceleration_factor, maximum} pairs but ta4j's basic ctor has no parameters.
                .ta4j(null)
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Psar.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Psar.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
