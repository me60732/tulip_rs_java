package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Marketfi;

/**
 * Marketfi (Market Force Index): tulip_rs_java vs ta4j.
 * No ta4j twin exists — Marketfi is not in ta4j 0.19 (listed as "marketfi" in TA4J_MAP.md nil section).
 * Zero-option indicator: no simdOptions phase registered.
 */
public final class BenchMarketfi implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("marketfi")
                .options(new double[][]{{}}) // matches Go/Python — no options
                .tulip((s, o) -> {
                    Outcome oc = Marketfi.indicator(new double[][]{s.high, s.low, s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j: no twin — Marketfi not in ta4j-core-0.19.jar
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low, stocks[i].volume};
                    }
                    try (SimdResult sim = Marketfi.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                // simdOptions: null — zero-option indicators have no option sets to vary
                .build();
    }
}
