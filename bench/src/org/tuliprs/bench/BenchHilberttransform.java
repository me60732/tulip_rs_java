package org.tuliprs.bench;

import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Hilberttransform;

/**
 * Hilberttransform — tulip_rs_java vs no ta4j twin.
 * No ta4j counterpart: Hilbert transform not in TA4J_MAP.md (Ehlers-specific).
 * Two mandatory output rows: in_phase, quadrature — consume ALL.
 */
public final class BenchHilberttransform implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("hilberttransform")
                .options(new double[][]{{10.0, 20.0}, {15.0, 30.0}, {20.0, 40.0}, {25.0, 50.0}}) // matches Go/Python
                .tulip((s, o) -> {
                    Outcome oc = Hilberttransform.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0) {
                            for (int row = 0; row < res.numOutputs(); row++) {
                                if (res.rowLength(row) > 0) {
                                    Harness.consume(res.get(row, 0));
                                }
                            }
                        }
                    }
                })
                .ta4j(null) // no ta4j twin — Hilbert transform not in TA4J_MAP.md
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Hilberttransform.simdByAssets(assets, o)) {
                        if (sim.numOutputs() > 0) {
                            for (int row = 0; row < sim.numOutputs(); row++) {
                                if (sim.rowLength(0, row) > 0) {
                                    Harness.consume(sim.get(0, row, 0));
                                }
                            }
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Hilberttransform.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.numOutputs() > 0) {
                            for (int row = 0; row < sim.numOutputs(); row++) {
                                if (sim.rowLength(0, row) > 0) {
                                    Harness.consume(sim.get(0, row, 0));
                                }
                            }
                        }
                    }
                })
                .build();
    }
}
