package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * FFI plumbing specific to the candlestick pattern detector: its result is
 * CSR-packed pattern ids (not f64 rows), it takes a forecast filter instead
 * of optional-output flags, it has NO SIMD variants, and its state
 * serializes through the generic {@code tulip_state_*} functions with
 * {@code C_INDICATOR_ID_CANDLESTICK}.
 */
public final class CandleEngine {

    /** FNV-1a32("candlestick") == C_INDICATOR_ID_CANDLESTICK (state ids header). */
    static final long ID = 0xdf26b608L;

    // ---- struct layouts (explicit padding: FFM structLayout does not infer it)

    static final MemoryLayout CANDLE_STICK_RESULT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("error"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.JAVA_LONG.withName("num_bars"),
            ValueLayout.JAVA_LONG.withName("total_patterns"),
            ValueLayout.ADDRESS.withName("bar_offsets"),
            ValueLayout.ADDRESS.withName("pattern_ids"),
            ValueLayout.ADDRESS.withName("state"));

    static final MemoryLayout CANDLE_STICK_BATCH_RESULT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("error"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.JAVA_LONG.withName("num_bars"),
            ValueLayout.JAVA_LONG.withName("total_patterns"),
            ValueLayout.ADDRESS.withName("bar_offsets"),
            ValueLayout.ADDRESS.withName("pattern_ids"));

    static final MemoryLayout CANDLE_PATTERN_INFO = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("id"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("name"),
            ValueLayout.ADDRESS.withName("full_name"),
            ValueLayout.ADDRESS.withName("japanese_name"),
            ValueLayout.JAVA_INT.withName("forecast"),
            ValueLayout.JAVA_INT.withName("bars"));

    // ---- downcalls ----------------------------------------------------------

    private static final MethodHandle INFO = Tulip.downcall("candlestick_info",
            FunctionDescriptor.of(Tulip.INDICATOR_INFO));
    private static final MethodHandle MIN_DATA = Tulip.downcall("candlestick_min_data",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle NUM_PATTERNS = Tulip.downcall("candlestick_num_patterns",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle PATTERN_INFO = Tulip.downcall("candlestick_pattern_info",
            FunctionDescriptor.of(CANDLE_PATTERN_INFO, ValueLayout.JAVA_INT));
    private static final MethodHandle PATTERN_NAMES = Tulip.downcall("candlestick_pattern_names",
            FunctionDescriptor.of(Tulip.STRING_ARRAY));
    private static final MethodHandle INDICATOR = Tulip.downcall("candlestick_indicator",
            FunctionDescriptor.of(CANDLE_STICK_RESULT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle BATCH = Tulip.downcall("candlestick_batch",
            FunctionDescriptor.of(CANDLE_STICK_BATCH_RESULT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT));
    static final MethodHandle STATE_FREE = Tulip.downcall("candlestick_state_free",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    static final MethodHandle RESULT_FREE = Tulip.downcall("candlestick_result_free",
            FunctionDescriptor.ofVoid(CANDLE_STICK_RESULT));
    static final MethodHandle BATCH_RESULT_FREE = Tulip.downcall("candlestick_batch_result_free",
            FunctionDescriptor.ofVoid(CANDLE_STICK_BATCH_RESULT));

    private CandleEngine() {}

    // ---- public engine surface ----------------------------------------------

    public static Info info() {
        try (Arena a = Arena.ofConfined()) {
            return Info.read((MemorySegment) Tulip.invoke(INFO, a));
        }
    }

    public static long minData(double[] options) {
        try (Arena a = Arena.ofConfined()) {
            return (long) Tulip.invoke(MIN_DATA, Tulip.packOptions(a, options));
        }
    }

    public static int numPatterns() {
        return (int) (long) Tulip.invoke(NUM_PATTERNS);
    }

    /** Copy of one pattern-table entry, or null for out-of-range ids. */
    public static CandlePattern patternInfo(int id) {
        if (id < 0 || id >= numPatterns()) {
            return null;
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = (MemorySegment) Tulip.invoke(PATTERN_INFO, a, id);
            return new CandlePattern(
                    s.get(ValueLayout.JAVA_INT, Tulip.off(CANDLE_PATTERN_INFO, "id")),
                    Tulip.cstr(s.get(ValueLayout.ADDRESS, Tulip.off(CANDLE_PATTERN_INFO, "name"))),
                    Tulip.cstr(s.get(ValueLayout.ADDRESS,
                            Tulip.off(CANDLE_PATTERN_INFO, "full_name"))),
                    Tulip.cstr(s.get(ValueLayout.ADDRESS,
                            Tulip.off(CANDLE_PATTERN_INFO, "japanese_name"))),
                    CandlePattern.forecastName(s.get(ValueLayout.JAVA_INT,
                            Tulip.off(CANDLE_PATTERN_INFO, "forecast"))),
                    s.get(ValueLayout.JAVA_INT, Tulip.off(CANDLE_PATTERN_INFO, "bars")));
        }
    }

    /** Id-ordered table of pattern short names (copied; process-lifetime source). */
    public static String[] patternNames() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment sa = (MemorySegment) Tulip.invoke(PATTERN_NAMES, a);
            MemorySegment ptr = sa.get(ValueLayout.ADDRESS, 0);
            long len = sa.get(ValueLayout.JAVA_LONG, 8);
            return Tulip.strings(ptr, len).toArray(new String[0]);
        }
    }

    public static CandleOutcome indicator(double[][] inputs, double[] options, int forecast) {
        if (inputs == null || inputs.length != 4 || inputs[0].length == 0) {
            throw new IndicatorException("candlestick requires 4 non-empty series (open, high, low, close)");
        }
        for (double[] s : inputs) {
            if (s == null || s.length != inputs[0].length) {
                throw new IndicatorException("candlestick input series must share one length");
            }
        }
        if (options == null || options.length != 3) {
            throw new IndicatorException("candlestick requires 3 options, got "
                    + (options == null ? 0 : options.length));
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment raw = (MemorySegment) Tulip.invoke(INDICATOR, a,
                    Tulip.packInputs(a, inputs), (long) inputs[0].length,
                    Tulip.packOptions(a, options), forecast);
            int err = raw.get(ValueLayout.JAVA_INT, 0);
            if (err != 0) {
                throw new IndicatorException(err);
            }
            CandleResult result = new CandleResult(
                    raw.get(ValueLayout.ADDRESS, Tulip.off(CANDLE_STICK_RESULT, "bar_offsets")),
                    raw.get(ValueLayout.ADDRESS, Tulip.off(CANDLE_STICK_RESULT, "pattern_ids")),
                    (int) raw.get(ValueLayout.JAVA_LONG, Tulip.off(CANDLE_STICK_RESULT, "num_bars")),
                    (int) raw.get(ValueLayout.JAVA_LONG,
                            Tulip.off(CANDLE_STICK_RESULT, "total_patterns")),
                    false);
            CandleState state = new CandleState(
                    raw.get(ValueLayout.ADDRESS, Tulip.off(CANDLE_STICK_RESULT, "state")));
            return new CandleOutcome(result, state);
        }
    }

    /** Rehydrates a candlestick state from a serialize blob. */
    public static CandleState deserializeState(byte[] blob) {
        return CandleState.deserialize(blob);
    }

    static CandleResult batch(MemorySegment statePtr, double[][] inputs, int forecast) {
        if (inputs == null || inputs.length != 4 || inputs[0].length == 0) {
            throw new IndicatorException("candlestick requires 4 non-empty series (open, high, low, close)");
        }
        for (double[] s : inputs) {
            if (s == null || s.length != inputs[0].length) {
                throw new IndicatorException("candlestick input series must share one length");
            }
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment raw = (MemorySegment) Tulip.invoke(BATCH, a,
                    statePtr, Tulip.packInputs(a, inputs), (long) inputs[0].length, forecast);
            int err = raw.get(ValueLayout.JAVA_INT, 0);
            if (err != 0) {
                throw new IndicatorException(err);
            }
            return new CandleResult(
                    raw.get(ValueLayout.ADDRESS, Tulip.off(CANDLE_STICK_BATCH_RESULT, "bar_offsets")),
                    raw.get(ValueLayout.ADDRESS, Tulip.off(CANDLE_STICK_BATCH_RESULT, "pattern_ids")),
                    (int) raw.get(ValueLayout.JAVA_LONG,
                            Tulip.off(CANDLE_STICK_BATCH_RESULT, "num_bars")),
                    (int) raw.get(ValueLayout.JAVA_LONG,
                            Tulip.off(CANDLE_STICK_BATCH_RESULT, "total_patterns")),
                    true);
        }
    }
}
