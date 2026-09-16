package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CMOIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Cmo;

/**
 * CMO (Chande Momentum Oscillator) — tulip_rs_java vs ta4j CMOIndicator.
 * ta4j constructor: new CMOIndicator(close, (int)opts[0]).
 */
public final class BenchCmo implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("cmo")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Cmo.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new CMOIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0]))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Cmo.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Cmo.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
