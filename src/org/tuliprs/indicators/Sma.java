package org.tuliprs.indicators;

import org.tuliprs.Info;
import org.tuliprs.Native;
import org.tuliprs.Outcome;
import org.tuliprs.SimdResult;
import org.tuliprs.State;

/**
 * SMA (Simple Moving Average) — Java facade over the {@code sma_*}
 * tulip_rs_ffi symbols. All work happens in the shared {@link Native} engine;
 * this class is just the indicator's constants plus delegation.
 *
 * <p>Memory model: every {@link Outcome#result()} and {@link SimdResult} is
 * a closeable owner of zero-copy output views; every {@link State} is a
 * closeable streaming handle. Use try-with-resources; never call a free.
 */
public final class Sma {

    private Sma() {}

    /** Number of input series: real. */
    public static final int INPUTS = 1;

    /** Number of options: period. */
    public static final int OPTIONS = 1;

    /** FNV-1a32("sma") == C_INDICATOR_ID_SMA from tulip_rs_ffi_state_ids.h. */
    public static final long ID = 0xe93ac5d4L;

    static final Native NATIVE = new Native("sma", ID, INPUTS, OPTIONS);

    /** Static metadata (name, type, input/option/output names, display groups). */
    public static Info info() {
        return NATIVE.info();
    }

    /** Minimum bars needed to produce any output for these options. */
    public static long minData(double... options) {
        return NATIVE.minData(options);
    }

    /**
     * Runs SMA over the given bars.
     *
     * @param inputs  {real}, each of equal length
     * @param options {period}
     */
    public static Outcome indicator(double[][] inputs, double[] options) {
        return NATIVE.indicator(inputs, options, null);
    }

    /** As {@link #indicator(double[][], double[])} plus optional outputs. */
    public static Outcome indicator(double[][] inputs, double[] options, boolean[] optionalOutputs) {
        return NATIVE.indicator(inputs, options, optionalOutputs);
    }

    /** N assets (2/4/8/16 lanes) through one shared option set, in one pass. */
    public static SimdResult simdByAssets(double[][][] assets, double[] options) {
        return NATIVE.simdByAssets(assets, options, null);
    }

    /** As {@link #simdByAssets(double[][][], double[])} plus optional outputs. */
    public static SimdResult simdByAssets(double[][][] assets, double[] options, boolean[] optionalOutputs) {
        return NATIVE.simdByAssets(assets, options, optionalOutputs);
    }

    /** One asset through N option sets (2/4/8/16 lanes) in one pass. */
    public static SimdResult simdByOptions(double[][] inputs, double[][] optionSets) {
        return NATIVE.simdByOptions(inputs, optionSets, null);
    }

    /** As {@link #simdByOptions(double[][], double[][])} plus optional outputs. */
    public static SimdResult simdByOptions(double[][] inputs, double[][] optionSets, boolean[] optionalOutputs) {
        return NATIVE.simdByOptions(inputs, optionSets, optionalOutputs);
    }

    /** Rehydrates a streaming state from a {@link State#serialize} blob. */
    public static State deserializeState(byte[] blob) {
        return NATIVE.deserializeState(blob);
    }
}
