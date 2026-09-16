package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.indicators.ChandelierExitLongIndicator;
import org.ta4j.core.indicators.ChandelierExitShortIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Chandelierexit;

/**
 * CHANDELIEREXIT (Chandelier Exit) — tulip_rs_java vs ta4j.
 * ta4j: ChandelierExitLongIndicator/ShortIndicator(series, (int)opts[0], opts[1]).
 * Both indicators run via Ta4j.runFull(List.of(...)).
 */
public final class BenchChandelierexit implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("chandelierexit")
                // matches Go bench_chandelierexit.go Options: {{14.0, 3.0}, {20.0, 3.0}, {22.0, 3.0}, {22.0, 2.0}}
                .options(new double[][]{{14.0, 3.0}, {20.0, 3.0}, {22.0, 3.0}, {22.0, 2.0}})
                .tulip((s, o) -> {
                    Outcome oc = Chandelierexit.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(List.of(
                        new ChandelierExitLongIndicator(Ta4j.series(s), (int) o[0], o[1]),
                        new ChandelierExitShortIndicator(Ta4j.series(s), (int) o[0], o[1])))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Chandelierexit.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Chandelierexit.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
