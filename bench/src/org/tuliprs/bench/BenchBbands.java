package org.tuliprs.bench;

import java.util.List;

import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.SigmaIndicator;
import org.ta4j.core.num.DoubleNum;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Bbands;

/**
 * BBands (Bollinger Bands): tulip_rs_java vs ta4j BollingerBandsLower/Middle/Upper.
 * ta4j construction: compose all 3 bands over SMAIndicator(close, period) with SigmaIndicator.
 * ctor style: new BollingerBandsLowerIndicator(middle, sigma, DoubleNum.valueOf(stdDev)).
 */
public final class BenchBbands implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("bbands")
                .options(new double[][]{{5.0, 2.0}, {14.0, 2.0}, {20.0, 2.0}, {50.0, 2.0}}) // matches Go
                .tulip((s, o) -> {
                    Outcome oc = Bbands.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        // Consume all three rows so the compiler cannot elide computation.
                        for (int rowIdx = 0; rowIdx < res.numOutputs(); rowIdx++) {
                            if (res.rowLength(rowIdx) > 0) {
                                Harness.consume(res.get(rowIdx, 0));
                            }
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(List.of(
                        new BollingerBandsLowerIndicator(
                                new BollingerBandsMiddleIndicator(new SMAIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0])),
                                new SigmaIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0]),
                                DoubleNum.valueOf(o[1])),
                        new BollingerBandsMiddleIndicator(new SMAIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0])),
                        new BollingerBandsUpperIndicator(
                                new BollingerBandsMiddleIndicator(new SMAIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0])),
                                new SigmaIndicator(new ClosePriceIndicator(Ta4j.series(s)), (int) o[0]),
                                DoubleNum.valueOf(o[1]))))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Bbands.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Bbands.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
