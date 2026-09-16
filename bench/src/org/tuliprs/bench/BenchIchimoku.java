package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Ichimoku;

/**
 * Ichimoku — tulip_rs_java vs ta4jIchimoku indicators.
 * ta4j 0.19 ctor: IchimokuTenkanSenIndicator(series, tenkan), KijunSenIndicator(series, kijun),
 * SenkouSpanAIndicator(series, tenkan, kijun), SenkouSpanBIndicator(series, kijun).
 * tulip opts = {short_period, long_period}; ta4j uses two params per indicator.
 * Compatible: both sweep {tenkan, kijun} pairs (9/26, 5/10, etc.).
 */
public final class BenchIchimoku implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("ichimoku")
                .options(new double[][]{{9.0, 26.0}, {5.0, 10.0}, {7.0, 14.0}, {9.0, 52.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Ichimoku.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        List.of(
                                new IchimokuTenkanSenIndicator(Ta4j.series(s), (int) o[0]),
                                new IchimokuKijunSenIndicator(Ta4j.series(s), (int) o[1]),
                                new IchimokuSenkouSpanAIndicator(Ta4j.series(s), (int) o[0], (int) o[1]),
                                new IchimokuSenkouSpanBIndicator(Ta4j.series(s), (int) o[1])
                        ))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Ichimoku.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Ichimoku.simdByOptions(new double[][]{s.high, s.low, s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
