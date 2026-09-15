package org.tuliprs.indicators;

import org.tuliprs.CandleEngine;
import org.tuliprs.CandleOutcome;
import org.tuliprs.CandlePattern;
import org.tuliprs.CandleResult;
import org.tuliprs.CandleState;
import org.tuliprs.Info;

/**
 * Candlestick patterns (77-entry table) — Java facade over the
 * {@code candlestick_*} tulip_rs_ffi symbols. Unlike the f64-series
 * indicators this one emits CSR-packed pattern ids (see {@link CandleResult}),
 * takes a forecast filter ({@link CandlePattern#FORECAST_NONE} and friends)
 * instead of optional-output flags, and has NO SIMD variants (the FFI omits
 * them). Its state still serializes through the generic blob API.
 */
public final class Candlestick {

    private Candlestick() {}

    /** Number of input series: open, high, low, close. */
    public static final int INPUTS = 4;

    /** Number of options: candle_period, trend_period, trend_signal_period. */
    public static final int OPTIONS = 3;

    /** FNV-1a32("candlestick") == C_INDICATOR_ID_CANDLESTICK. */
    public static final long ID = 0xdf26b608L;

    /** Static metadata (name, type, input/option names, display groups). */
    public static Info info() {
        return CandleEngine.info();
    }

    /** Minimum bars needed before any detection occurs. */
    public static long minData(double... options) {
        return CandleEngine.minData(options);
    }

    /** Size of the stable pattern table (77). */
    public static int numPatterns() {
        return CandleEngine.numPatterns();
    }

    /** One pattern-table entry by id, or null when out of range. */
    public static CandlePattern patternInfo(int id) {
        return CandleEngine.patternInfo(id);
    }

    /** Id-ordered table of pattern short names. */
    public static String[] patternNames() {
        return CandleEngine.patternNames();
    }

    /** Detects patterns over the given bars (no forecast filter). */
    public static CandleOutcome indicator(double[][] inputs, double[] options) {
        return CandleEngine.indicator(inputs, options, CandlePattern.FORECAST_NONE);
    }

    /** Detects patterns with a forecast filter (CandlePattern.FORECAST_*). */
    public static CandleOutcome indicator(double[][] inputs, double[] options, int forecast) {
        return CandleEngine.indicator(inputs, options, forecast);
    }

    /** Rehydrates a streaming state from a {@link CandleState#serialize} blob. */
    public static CandleState deserializeState(byte[] blob) {
        return CandleEngine.deserializeState(blob);
    }
}
