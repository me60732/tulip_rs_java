package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Per-indicator bundle of FFI downcall handles, resolved from the C symbol
 * prefix (e.g. {@code "adx"} → {@code adx_indicator, adx_batch, ...}). All
 * marshalling, wrapper construction, and error checks live here, so each
 * indicator facade is just constants plus delegation.
 */
public final class Native {

    /** Lowercase C symbol prefix ("adx", "macd", ...). */
    public final String prefix;
    /**
     * FNV-1a32 of the indicator name == {@code C_INDICATOR_ID_<NAME>} from
     * {@code include/tulip_rs_ffi_state_ids.h}; never re-hashed (§6).
     */
    public final long id;
    /** Expected number of input series. */
    public final int inputs;
    /** Expected number of options. */
    public final int options;

    private final MethodHandle hInfo;
    private final MethodHandle hMinData;
    private final MethodHandle hIndicator;
    private final MethodHandle hBatch;
    private final MethodHandle hStateFree;
    private final MethodHandle hSimdAssets; // null when the FFI omits it
    private final MethodHandle hSimdOptions;

    public Native(String prefix, long id, int inputs, int options) {
        this.prefix = prefix;
        this.id = id;
        this.inputs = inputs;
        this.options = options;
        this.hInfo = Tulip.downcall(prefix + "_info",
                FunctionDescriptor.of(Tulip.INDICATOR_INFO));
        this.hMinData = Tulip.downcall(prefix + "_min_data",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        this.hIndicator = Tulip.downcall(prefix + "_indicator",
                FunctionDescriptor.of(Tulip.INDICATOR_RESULT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.hBatch = Tulip.downcall(prefix + "_batch",
                FunctionDescriptor.of(Tulip.BATCH_RESULT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.hStateFree = Tulip.downcall(prefix + "_state_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.hSimdAssets = Tulip.downcallOrNull(prefix + "_simd_by_assets",
                FunctionDescriptor.of(Tulip.SIMD_RESULT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        this.hSimdOptions = Tulip.downcallOrNull(prefix + "_simd_by_options",
                FunctionDescriptor.of(Tulip.SIMD_RESULT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    }

    MethodHandle stateFree() {
        return hStateFree;
    }

    /** Static metadata: name, inputs/options/outputs, display groups. */
    public Info info() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment s = (MemorySegment) Tulip.invoke(hInfo, a);
            return Info.read(s);
        }
    }

    /** Minimum bars needed to produce any output. */
    public long minData(double[] options) {
        try (Arena a = Arena.ofConfined()) {
            return (long) Tulip.invoke(hMinData, Tulip.packOptions(a, options));
        }
    }

    /** Fresh full (or first-chunk) calculation: outputs + continuation state. */
    public Outcome indicator(double[][] inputs, double[] options, boolean[] optionalOutputs) {
        validateSeries(inputs, options);
        try (Arena a = Arena.ofConfined()) {
            long dataLen = inputs[0].length;
            MemorySegment raw = (MemorySegment) Tulip.invoke(hIndicator, a,
                    Tulip.packInputs(a, inputs), dataLen,
                    Tulip.packOptions(a, options),
                    Tulip.packBools(a, optionalOutputs), (long) boolLen(optionalOutputs));
            int err = raw.get(ValueLayout.JAVA_INT, 0);
            if (err != 0) {
                throw new IndicatorException(err);
            }
            Result result = new Result(
                    raw.get(ValueLayout.ADDRESS, Tulip.off(Tulip.INDICATOR_RESULT, "outputs")),
                    raw.get(ValueLayout.ADDRESS, Tulip.off(Tulip.INDICATOR_RESULT, "output_lens")),
                    (int) raw.get(ValueLayout.JAVA_LONG, Tulip.off(Tulip.INDICATOR_RESULT, "num_outputs")),
                    false);
            State state = new State(this,
                    raw.get(ValueLayout.ADDRESS, Tulip.off(Tulip.INDICATOR_RESULT, "state")));
            return new Outcome(result, state);
        }
    }

    /** Continuation for {@link State#batch}; inputs validated, options stored in state. */
    Result batch(MemorySegment statePtr, double[][] inputs, boolean[] optionalOutputs) {
        if (inputs == null || inputs.length != this.inputs || inputs[0].length == 0) {
            throw new IndicatorException("this indicator requires " + this.inputs
                    + " non-empty input series of equal length");
        }
        validateEqualLengths(inputs);
        try (Arena a = Arena.ofConfined()) {
            long dataLen = inputs[0].length;
            MemorySegment raw = (MemorySegment) Tulip.invoke(hBatch, a,
                    statePtr, Tulip.packInputs(a, inputs), dataLen,
                    Tulip.packBools(a, optionalOutputs), (long) boolLen(optionalOutputs));
            int err = raw.get(ValueLayout.JAVA_INT, 0);
            if (err != 0) {
                throw new IndicatorException(err);
            }
            return new Result(
                    raw.get(ValueLayout.ADDRESS, Tulip.off(Tulip.BATCH_RESULT, "outputs")),
                    raw.get(ValueLayout.ADDRESS, Tulip.off(Tulip.BATCH_RESULT, "output_lens")),
                    (int) raw.get(ValueLayout.JAVA_LONG, Tulip.off(Tulip.BATCH_RESULT, "num_outputs")),
                    true);
        }
    }

    /** N assets (2/4/8/16) in one pass; assets[asset][input][bar]. */
    public SimdResult simdByAssets(double[][][] assets, double[] options, boolean[] optionalOutputs) {
        requireHandle(hSimdAssets, "simd_by_assets");
        try (Arena a = Arena.ofConfined()) {
            int n = assets.length;
            MemorySegment outer = a.allocate(8L * n, 8);
            long dataLen = -1;
            for (int i = 0; i < n; i++) {
                validateSeries(assets[i], options);
                if (dataLen < 0) {
                    dataLen = assets[i][0].length;
                } else if (assets[i][0].length != dataLen) {
                    throw new IndicatorException("SIMD by-assets: all assets must share one length");
                }
                outer.set(ValueLayout.ADDRESS, 8L * i, Tulip.packInputs(a, assets[i]));
            }
            MemorySegment raw = (MemorySegment) Tulip.invoke(hSimdAssets, a,
                    outer, (long) n, dataLen,
                    Tulip.packOptions(a, options),
                    Tulip.packBools(a, optionalOutputs), (long) boolLen(optionalOutputs));
            checkError(raw);
            return new SimdResult(raw, this);
        }
    }

    /** N option sets (2/4/8/16) in one pass over shared inputs. */
    public SimdResult simdByOptions(double[][] inputs, double[][] optionSets, boolean[] optionalOutputs) {
        requireHandle(hSimdOptions, "simd_by_options");
        try (Arena a = Arena.ofConfined()) {
            int n = optionSets.length;
            MemorySegment outer = a.allocate(8L * n, 8);
            for (int i = 0; i < n; i++) {
                validateOptions(optionSets[i]);
                outer.set(ValueLayout.ADDRESS, 8L * i, Tulip.packOptions(a, optionSets[i]));
            }
            if (inputs == null || inputs.length != this.inputs || inputs[0].length == 0) {
                throw new IndicatorException("this indicator requires " + this.inputs
                        + " non-empty input series");
            }
            validateEqualLengths(inputs);
            MemorySegment raw = (MemorySegment) Tulip.invoke(hSimdOptions, a,
                    Tulip.packInputs(a, inputs), (long) inputs[0].length,
                    outer, (long) n,
                    Tulip.packBools(a, optionalOutputs), (long) boolLen(optionalOutputs));
            checkError(raw);
            return new SimdResult(raw, this);
        }
    }

    /** Rehydrates a state handle from a {@code serialize} blob (self-describing header). */
    public State deserializeState(byte[] blob) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment bytes = a.allocate(Math.max(blob.length, 1));
            MemorySegment.copy(blob, 0, bytes, ValueLayout.JAVA_BYTE, 0, blob.length);
            MemorySegment ptr = (MemorySegment) Tulip.invoke(
                    Tulip.STATE_DESERIALIZE, bytes, (long) blob.length);
            if (ptr.address() == 0) {
                throw new IndicatorException("state deserialization failed for indicator \""
                        + prefix + "\" (corrupt blob or unknown id in header)");
            }
            return new State(this, ptr);
        }
    }

    // ---- validation ---------------------------------------------------------

    private void validateSeries(double[][] in, double[] opts) {
        if (in == null || in.length != inputs) {
            throw new IndicatorException(inputs + " input series required by " + prefix
                    + ", got " + (in == null ? 0 : in.length));
        }
        validateEqualLengths(in);
        validateOptions(opts);
    }

    private void validateEqualLengths(double[][] in) {
        long n = in[0].length;
        if (n == 0) {
            throw new IndicatorException("input series must be non-empty");
        }
        for (double[] s : in) {
            if (s == null || s.length != n) {
                throw new IndicatorException("input series must share one length");
            }
        }
    }

    private void validateOptions(double[] opts) {
        int len = opts == null ? 0 : opts.length;
        if (len != options) {
            throw new IndicatorException(options + " option(s) required by " + prefix + ", got " + len);
        }
    }

    private static void checkError(MemorySegment raw) {
        int err = raw.get(ValueLayout.JAVA_INT, 0);
        if (err != 0) {
            throw new IndicatorException(err);
        }
    }

    private void requireHandle(MethodHandle h, String what) {
        if (h == null) {
            throw new UnsupportedOperationException(prefix + ": no " + what);
        }
    }

    private static int boolLen(boolean[] flags) {
        return flags == null ? 0 : flags.length;
    }
}
