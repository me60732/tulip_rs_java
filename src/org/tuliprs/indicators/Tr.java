package org.tuliprs.indicators;

import org.tuliprs.Info;
import org.tuliprs.Native;
import org.tuliprs.Outcome;
import org.tuliprs.SimdResult;
import org.tuliprs.State;

/**
 * True Range — Java facade over the {@code tr_*} tulip_rs_ffi symbols.
 * All work happens in the shared {@link Native} engine; this class is just
 * the indicator's constants plus delegation.
 *
 * <p>Memory model: every {@link Outcome#result()} and {@link SimdResult} is
 * a closeable owner of zero-copy output views; every {@link State} is a
 * closeable streaming handle. Use try-with-resources; never call a free.
 */
public final class Tr {

    private Tr() {}

    /** Number of input series: high, low, close. */
    public static final int INPUTS = 3;

    /** Number of options: none (0). */
    public static final int OPTIONS = 0;

    /** FNV-1a32("tr") == C_INDICATOR_ID_TR from tulip_rs_ffi_state_ids.h. */
    public static final long ID = 0x47455003L;

    static final Native NATIVE = new Native("tr", ID, INPUTS, OPTIONS);

    /** Static metadata (name, type, input/option/output names, display groups). */
    public static Info info() {
        return NATIVE.info();
    }

    /** Minimum bars needed to produce any output for these options. */
    public static long minData(double... options) {
        return NATIVE.minData(options);
    }

    /**
     * Runs TR over the given bars.
     *
     * @param inputs  {high, low, close}, each of equal length
     */
    public static Outcome indicator(double[][] inputs) {
        return NATIVE.indicator(inputs, new double[0], null);
    }

    /**
     * As {@link #indicator(double[][])} but also computing optional
     * outputs in fixed order: atr, medprice.
     */
    public static Outcome indicator(double[][] inputs, boolean[] optionalOutputs) {
        return NATIVE.indicator(inputs, new double[0], optionalOutputs);
    }

    /** N assets (2/4/8/16 lanes) through one shared option set, in one pass. */
    public static SimdResult simdByAssets(double[][][] assets) {
        return NATIVE.simdByAssets(assets, new double[0], null);
    }

    /** As {@link #simdByAssets(double[][][])} plus optional outputs (atr, medprice). */
    public static SimdResult simdByAssets(double[][][] assets, boolean[] optionalOutputs) {
        return NATIVE.simdByAssets(assets, new double[0], optionalOutputs);
    }

    /** Rehydrates a streaming state from a {@link State#serialize} blob. */
    public static State deserializeState(byte[] blob) {
        return NATIVE.deserializeState(blob);
    }
}
