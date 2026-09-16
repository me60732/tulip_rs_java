package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.indicators.averages.KAMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Kama;

/**
 * KAMA — tulip_rs_java vs ta4j KAMAIndicator.
 * ta4j ctor: new KAMAIndicator(indicator, erPeriod, fastPeriod, slowPeriod).
 * tulip opts = {er_period, fast, slow}; both sweep all three parameters.
 * Compatible: same 3-param signature. Go bench shows only er_period swept,
 * so use fixed fast=2/slow=30 matching ta4j defaults.
 */
public final class BenchKama implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("kama")
                .options(new double[][]{{5.0}, {10.0}, {14.0}, {20.0}}) // matches Go/Python (only er_period swept)
                .tulip((s, o) -> {
                    Outcome oc = Kama.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new KAMAIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0], 2, 30))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Kama.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Kama.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
