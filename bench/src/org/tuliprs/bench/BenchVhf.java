package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Vhf;

/**
 * VHF (Vertical Horizontal Filter): tulip_rs_java only.
 * ta4j has no VerticalHorizontalFilter implementation in 0.19,
 * so ta4jFn is omitted with a comment.
 */
public final class BenchVhf implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("vhf")
                .options(new double[][]{{14.0}, {20.0}, {28.0}, {50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Vhf.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // no ta4j VerticalHorizontalFilter implementation in 0.19
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Vhf.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Vhf.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
