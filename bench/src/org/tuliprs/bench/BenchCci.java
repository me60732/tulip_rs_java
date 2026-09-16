package org.tuliprs.bench;

import org.ta4j.core.indicators.CCIIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Cci;

/**
 * CCI (Commodity Channel Index) — tulip_rs_java vs ta4j CCIIndicator.
 * ta4j constructor: new CCIIndicator(series, (int)opts[0]).
 */
public final class BenchCci implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("cci")
                // matches Go bench_cci.go Options: {{20.0}, {25.0}, {30.0}, {50.0}}
                .options(new double[][]{{20.0}, {25.0}, {30.0}, {50.0}})
                .tulip((s, o) -> {
                    Outcome oc = Cci.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new CCIIndicator(Ta4j.series(s), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Cci.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Cci.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
