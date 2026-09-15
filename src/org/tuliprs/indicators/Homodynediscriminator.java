package org.tuliprs.indicators;

import org.tuliprs.Info;
import org.tuliprs.Native;
import org.tuliprs.Outcome;
import org.tuliprs.SimdResult;
import org.tuliprs.State;

/**
 * Homodynediscriminator — Java facade over the {@code homodynediscriminator_*}
 * tulip_rs_ffi symbols. All work happens in the shared {@link Native} engine;
 * this class is just the indicator's constants plus delegation.
 *
 * <p>Memory model: every {@link Outcome#result()} and {@link SimdResult}
 * is a closeable owner of zero-copy output views; every {@link State} is a
 * closeable streaming handle. Use try-with-resources; never call a free.
 */
public final class Homodynediscriminator {

    private Homodynediscriminator() {}

    /** Number of input series: real. */
    public static final int INPUTS = 1;

    /** Number of options: none (recon counts header!). */
    public static final int OPTIONS = 0;

    /** FNV-1a32("homodynediscriminator") == C_INDICATOR_ID_HOMODYNEDISCRIMINATOR from tulip_rs_ffi_state_ids.h. */
    public static final long ID = 0xfcd31f7aL;

    static final Native NATIVE = new Native("homodynediscriminator", ID, INPUTS, OPTIONS);

    /** Static metadata (name, type, input/option/output names, display groups). */
    public static Info info() {
        return NATIVE.info();
    }

    /** Minimum bars needed to produce any output for these options. */
    public static long minData(double... options) {
        return NATIVE.minData(options);
    }

    /**
     * Runs Homodynediscriminator over the given bars.
     *
     * @param inputs  {real}, of equal length
     * @param options {} (empty array)
     */
    public static Outcome indicator(double[][] inputs, double[] options) {
        return NATIVE.indicator(inputs, options, null);
    }

    /**
     * As {@link #indicator(double[][], double[])} but also computing optional
     * outputs (none for Homodynediscriminator).
     */
    public static Outcome indicator(double[][] inputs, double[] options, boolean[] optionalOutputs) {
        return NATIVE.indicator(inputs, options, optionalOutputs);
    }

    /** N assets (2/4/8/16 lanes) through one shared option set, in one pass. */
    public static SimdResult simdByAssets(double[][][] assets, double[] options) {
        return NATIVE.simdByAssets(assets, options, null);
    }

    /** As {@link #simdByAssets(double[][], double[])} plus optional outputs (none). */
    public static SimdResult simdByAssets(double[][][] assets, double[] options, boolean[] optionalOutputs) {
        return NATIVE.simdByAssets(assets, options, optionalOutputs);
    }

    // NOTE: Go binding omits SimdByOptions - FFI symbol not implemented

    /** Rehydrates a streaming state from a {@link State#serialize} blob. */
    public static State deserializeState(byte[] blob) {
        return NATIVE.deserializeState(blob);
    }
}
