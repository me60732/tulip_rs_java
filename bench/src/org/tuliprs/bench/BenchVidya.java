package org.tuliprs.bench;

import org.ta4j.core.indicators.averages.VIDYAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Vidya;

/**
 * VIDYA (Variable Index Dynamic Average): tulip_rs_java vs ta4j VIDYAIndicator.
 * ta4j constructor: new VIDYAIndicator(Indicator<Num>, int shortPeriod, int longPeriod).
 * Tulip options are {short_period, long_period, alpha}; the Vidya ctor takes
 * (Indicator<Num>, shortPeriod, longPeriod) — alpha is not used by ta4j's impl.
 * Compatible for the first two swept params; register with comment.
 */
public final class BenchVidya implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("vidya")
                .options(new double[][]{{2.0, 5.0, 0.2}, {5.0, 20.0, 0.2}, {9.0, 30.0, 0.2}, {12.0, 26.0, 0.1}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Vidya.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new VIDYAIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0], (int) o[1]))))
                // ta4j VIDYA uses only period params; alpha in tulip opts is unused
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Vidya.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Vidya.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
