package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelMiddleIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Donchianchannel;

/**
 * Donchianchannel — tulip_rs_java vs ta4j DonchianChannelUpper/Lower/MiddleIndicator.
 * ta4j construction: new DonchianChannelUpper/Lower/MiddleIndicator(series, (int)opts[0]).
 */
public final class BenchDonchianchannel implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("donchianchannel")
                .options(new double[][]{{14.0}, {20.0}, {25.0}, {30.0}})
                .tulip((s, o) -> {
                    Outcome oc = Donchianchannel.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        List.of(new DonchianChannelUpperIndicator(Ta4j.series(s), (int) o[0]),
                                new DonchianChannelLowerIndicator(Ta4j.series(s), (int) o[0]),
                                new DonchianChannelMiddleIndicator(Ta4j.series(s), (int) o[0])))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Donchianchannel.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Donchianchannel.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
