package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Di;

/**
 * DI (Directional Indicator) — tulip_rs_java vs ta4j PlusDIIndicator + MinusDIIndicator.
 * ta4j: runFull(List.of(new PlusDIIndicator(series, p), new MinusDIIndicator(series, p))).
 */
public final class BenchDi implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("di")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {30.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Di.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> {
                    int p = (int) o[0];
                    List<Indicator<org.ta4j.core.num.Num>> inds = List.of(
                            new PlusDIIndicator(Ta4j.series(s), p),
                            new MinusDIIndicator(Ta4j.series(s), p));
                    Harness.consume(Ta4j.runFull(inds));
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Di.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Di.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
