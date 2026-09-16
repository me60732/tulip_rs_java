package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.indicators.keltner.KeltnerChannelLowerIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelMiddleIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelUpperIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Keltnerchannel;

/**
 * KeltnerChannel — tulip_rs_java vs ta4j KeltnerChannel indicators.
 * ta4j 0.19 ctor: KeltnerChannelMiddleIndicator(series, period),
 * KeltnerChannelUpper/LowerIndicator(middle, multiplier, period).
 * tulip opts = {period, step}; period matches, step=multiplier passed separately.
 * Compatible: both sweep {period, multiplier} pairs like {20.0, 2.0}.
 */
public final class BenchKeltnerchannel implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("keltnerchannel")
                .options(new double[][]{{20.0, 2.0}, {20.0, 1.5}, {14.0, 2.0}, {10.0, 1.5}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Keltnerchannel.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> {
                    int period = (int) o[0];
                    double multiplier = o[1];
                    var middle = new KeltnerChannelMiddleIndicator(Ta4j.series(s), period);
                    var upper = new KeltnerChannelUpperIndicator(middle, multiplier, period);
                    var lower = new KeltnerChannelLowerIndicator(middle, multiplier, period);
                    Harness.consume(Ta4j.runFull(List.of(lower, middle, upper)));
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Keltnerchannel.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Keltnerchannel.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
