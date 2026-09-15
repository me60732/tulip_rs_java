package org.tuliprs.indicators;

import org.tuliprs.Info;
import org.tuliprs.Native;
import org.tuliprs.Outcome;
import org.tuliprs.SimdResult;
import org.tuliprs.State;

/**
 * WAD (Wave/Demo accumulation) — Java facade over the {@code wad_*}
 * tulip_rs_ffi symbols. All work happens in the shared {@link Native} engine;
 * this class is just the indicator's constants plus delegation.
 *
 * <p>Memory model: every {@link Outcome#result()} and {@link SimdResult} is
 * a closeable owner of zero-copy output views; every {@link State} is a
 * closeable streaming handle. Use try-with-resources; never call a free.
 */
public final class Wad {

    private Wad() {}

    /** Number of input series: high, low, close. */
    public static final int INPUTS = 3;

    /** Number of options: none. */
    public static final int OPTIONS = 0;

    /** FNV-1a32("wad") == C_INDICATOR_ID_WAD from tulip_rs_ffi_state_ids.h. */
    public static final long ID = 0x21e2d343L;

    static final Native NATIVE = new Native("wad", ID, INPUTS, OPTIONS);

    /** Static metadata (name, type, input/option/output names, display groups). */
    public static Info info() {
        return NATIVE.info();
    }

    /** Minimum bars needed to produce any output for these options. */
    public static long minData(double... options) {
        return NATIVE.minData(options);
    }

    /**
     * Runs WAD over the given bars.
     *
     * @param inputs  {high, low, close}, each of equal length
     */
    public static Outcome indicator(double[][] inputs, double[] options) {
        return NATIVE.indicator(inputs, options, null);
    }

    /** N assets (2/4/8/16 lanes) through one shared option set, in one pass. */
    public static SimdResult simdByAssets(double[][][] assets, double[] options) {
        return NATIVE.simdByAssets(assets, options, null);
    }

    /** As {@link #simdByAssets(double[], double[])} (no optional outputs). */
    public static SimdResult simdByAssets(double[][][] assets, double[] options, boolean[] optionalOutputs) {
        return NATIVE.simdByAssets(assets, options, optionalOutputs);
    }

    /** Rehydrates a streaming state from a {@link State#serialize} blob. */
    public static State deserializeState(byte[] blob) {
        return NATIVE.deserializeState(blob);
    }
}
