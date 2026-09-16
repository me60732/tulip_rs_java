package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Vosc;

/**
 * VOSC (Volume Oscillator): tulip_rs_java only. ta4j 0.19 has no VolumeOscillator
 * equivalent; the TA4J_MAP.md explicitly lists vosc as absent.
 */
public final class BenchVosc implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("vosc")
                .options(new double[][]{{5.0, 20.0}, {9.0, 26.0}, {12.0, 26.0}, {3.0, 10.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Vosc.indicator(new double[][]{s.volume}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j omitted: no VolumeOscillator in ta4j-core-0.19.jar
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].volume};
                    }
                    try (SimdResult sim = Vosc.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Vosc.simdByOptions(new double[][]{s.volume}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
