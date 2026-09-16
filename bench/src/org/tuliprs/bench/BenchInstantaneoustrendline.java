package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Instantaneoustrendline;

/**
 * Instantaneoustrendline — tulip_rs_java only (no ta4j equivalent).
 * TA4J_MAP.md lists instantaneoustrendline under "No ta4j counterpart".
 */
public final class BenchInstantaneoustrendline implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("instantaneoustrendline")
                .options(new double[][]{{}}) // matches Go/Python options_list=[[]]
                .tulip((s, o) -> {
                    Outcome oc = Instantaneoustrendline.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Instantaneoustrendline.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> { }) // no options, simd_by_options not applicable
                .build();
    }
}
