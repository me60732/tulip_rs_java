package org.tuliprs.bench;

import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Chaikinmf;

/**
 * CHAIKINMF (Chaikin Money Flow) — tulip_rs_java vs ta4j ChaikinMoneyFlowIndicator.
 * ta4j constructor: new ChaikinMoneyFlowIndicator(series, (int)opts[0]).
 */
public final class BenchChaikinmf implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("chaikinmf")
                // matches Go bench_chaikinmf.go Options: {{14.0}, {20.0}, {25.0}, {30.0}}
                .options(new double[][]{{14.0}, {20.0}, {25.0}, {30.0}})
                .tulip((s, o) -> {
                    Outcome oc = Chaikinmf.indicator(new double[][]{s.high, s.low, s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new ChaikinMoneyFlowIndicator(Ta4j.series(s), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Chaikinmf.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Chaikinmf.simdByOptions(new double[][]{s.high, s.low, s.close, s.volume}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
