package org.tuliprs.bench;

import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Vwma;

/**
 * VWMA (Volume Weighted Moving Average): tulip_rs_java vs ta4j VWMAIndicator.
 * ta4j constructor: new VWMAIndicator(close, volume, period) or
 * new VWMAIndicator(close, period). We use the two-Indicator variant with
 * ClosePriceIndicator and VolumeIndicator to match tulip's {close, volume} inputs.
 */
public final class BenchVwma implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("vwma")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Vwma.indicator(new double[][]{s.close, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .ta4j((s, o) -> Harness.consume(Ta4j.runFull(
                        new VWMAIndicator(new ClosePriceIndicator(Ta4j.series(s)),
                                new VolumeIndicator(Ta4j.series(s)), (int) o[0], null))))
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close, stocks[i].volume};
                    }
                    try (SimdResult sim = Vwma.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Vwma.simdByOptions(new double[][]{s.close, s.volume}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
