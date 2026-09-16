package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Wad;

/**
 * WAD (Wave/Demo accumulation): tulip_rs_java only. ta4j 0.19 has no WilliamsAD
 * equivalent; the TA4J_MAP.md explicitly lists wad as absent.
 */
public final class BenchWad implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("wad")
                .options(new double[][]{{}}) // matches Go/Python: no options
                .tulip((s, o) -> {
                    Outcome oc = Wad.indicator(new double[][]{s.high, s.low, s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: no WilliamsAD in ta4j-core-0.19.jar
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].close};
                    }
                    try (SimdResult sim = Wad.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions omitted: wad has no options
                .build();
    }
}
