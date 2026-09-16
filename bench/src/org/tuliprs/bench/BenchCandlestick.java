package org.tuliprs.bench;

import org.tuliprs.CandleOutcome;
import org.tuliprs.CandleResult;
import org.tuliprs.CandleState;
import org.tuliprs.indicators.Candlestick;

/**
 * Candlestick (77 pattern table): tulip_rs_java only — NO ta4j twin.
 * ta4j candle patterns are boolean detectors, not numeric indicators.
 * No SIMD closures — the FFI omits them. Tulip closure uses try-with-resources
 * BOTH Result+State; consume EVERY row's first value guarded by rowLength>0.
 */
public final class BenchCandlestick implements BenchProvider {

    @Override
    public Benchmark def() {
        return Benchmark.builder("candlestick")
                .options(new double[][]{{5.0, 1.0, 1.0}}) // Go/CandlestickExample grid: candle/trend/signal periods
                .tulip((s, o) -> {
                    CandleOutcome oc = Candlestick.indicator(new double[][]{s.open, s.high, s.low, s.close}, o);
                    try (CandleResult r = oc.result(); CandleState st = oc.state()) {
                        // Guard-consume totalPatterns first
                        Harness.consume((double) r.totalPatterns());
                        // Then consume first pattern id at last bar if any patterns exist
                        int[] pats = r.patterns(r.numBars() - 1);
                        if (pats.length > 0) {
                            Harness.consume((double) pats[0]);
                        }
                    }
                })
                .ta4j(null) // ta4j candle patterns are boolean detectors, not numeric
                .simdAssets(null) // Candlestick has no SIMD (ffi omits)
                .simdOptions(null) // Candlestick has no SIMD (ffi omits)
                .build();
    }
}
