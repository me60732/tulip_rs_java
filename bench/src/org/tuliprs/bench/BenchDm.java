package org.tuliprs.bench;

import org.ta4j.core.indicators.adx.MinusDMIndicator;
import org.ta4j.core.indicators.adx.PlusDMIndicator;
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Dm;

/**
 * DM (Directional Movement): tulip_rs_java vs ta4j PlusDMIndicator/MinusDMIndicator.
 * ta4j: fixed 14-bar window — no constructor taking period parameter. Register via runFull(List.of(both))
 * only if Go dm grid sweeps nothing matching; else omit with comment.
 * Go bench sweeps {5.0, 14.0, 20.0, 30.0} → ta4j fixed window mismatch → omitted.
 */
public final class BenchDm implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("dm")
                .options(new double[][]{{5.0}, {14.0}, {20.0}, {30.0}}) // matches Go
                .tulip((s, o) -> {
                    Outcome oc = Dm.indicator(new double[][]{s.high, s.low}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                    }
                })
                // ta4j PlusDM/MinusDM have fixed 14-bar window; tulip sweeps options → no param-compatible twin
                .ta4j(null)
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].high, stocks[i].low};
                    }
                    try (SimdResult sim = Dm.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Dm.simdByOptions(new double[][]{s.high, s.low}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                    }
                })
                .build();
    }
}
