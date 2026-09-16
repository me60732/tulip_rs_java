package org.tuliprs.bench;

// SMAEnvelope: no ta4j twin (ta4j has no envelope indicator; SMA ± pct would measure building blocks, not a comparable indicator).
// tulip outputs 3 rows: lower, middle, upper — all consumed.
import org.tuliprs.Outcome;
import org.tuliprs.Result;
import org.tuliprs.SimdResult;
import org.tuliprs.State;
import org.tuliprs.indicators.Smaenvelope;

public final class BenchSmaenvelope implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("smaenvelope")
                .options(new double[][]{{20.0, 2.5}, {20.0, 5.0}, {50.0, 2.5}, {50.0, 5.0}}) // matches Go/Python options_list
                .tulip((s, o) -> {
                    Outcome oc = Smaenvelope.indicator(new double[][]{s.close}, o);
                    try (Result res = oc.result(); State st = oc.state()) {
                        if (res.numOutputs() > 0 && res.rowLength(0) > 0) {
                            Harness.consume(res.get(0, 0));
                        }
                        if (res.numOutputs() > 1 && res.rowLength(1) > 0) {
                            Harness.consume(res.get(1, 0));
                        }
                        if (res.numOutputs() > 2 && res.rowLength(2) > 0) {
                            Harness.consume(res.get(2, 0));
                        }
                    }
                })
                .simdAssets((stocks, o) -> {
                    double[][][] assets = new double[stocks.length][][];
                    for (int i = 0; i < stocks.length; i++) {
                        assets[i] = new double[][]{stocks[i].close};
                    }
                    try (SimdResult sim = Smaenvelope.simdByAssets(assets, o)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(0, 1, 0));
                        }
                        if (sim.rowLength(2, 0) > 0) {
                            Harness.consume(sim.get(0, 2, 0));
                        }
                    }
                })
                .simdOptions((s, optSets) -> {
                    try (SimdResult sim = Smaenvelope.simdByOptions(new double[][]{s.close}, optSets)) {
                        if (sim.rowLength(0, 0) > 0) {
                            Harness.consume(sim.get(0, 0, 0));
                        }
                        if (sim.rowLength(1, 0) > 0) {
                            Harness.consume(sim.get(0, 1, 0));
                        }
                        if (sim.rowLength(2, 0) > 0) {
                            Harness.consume(sim.get(0, 2, 0));
                        }
                    }
                })
                .build();
    }
}
