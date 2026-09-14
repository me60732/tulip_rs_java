package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * N parallel SIMD lanes (by-assets or by-options). Rows are zero-copy views
 * exactly like {@link Result}'s; each lane also owns an ordinary streaming
 * state, released when this result closes (§2: every lane state FIRST, then
 * the outer SIMD buffers — that order is contractual).
 *
 * <p>Lane {@link State} objects are handed out via {@link #state(int)} for
 * continued streaming; prefer closing the whole SimdResult. Closing a lane
 * early is safe (idempotent CAS guards prevent double-free) and the SIMD
 * buffers stay owned until {@code close()}.
 */
public final class SimdResult implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final int numResults;
    private final int numOutputs;
    private final State[] states;
    private final MemorySegment[][] rows;
    private final long[][] lens;
    private final FreeTask task;
    private final Cleaner.Cleanable cleanable;

    SimdResult(MemorySegment raw, Native indicator) {
        MemoryLayout L = Tulip.SIMD_RESULT;
        this.numResults = (int) raw.get(ValueLayout.JAVA_LONG, Tulip.off(L, "num_results"));
        this.numOutputs = (int) raw.get(ValueLayout.JAVA_LONG, Tulip.off(L, "num_outputs"));

        MemorySegment outArr = raw.get(ValueLayout.ADDRESS, Tulip.off(L, "outputs"))
                .reinterpret(8L * numResults);
        MemorySegment lenArr = raw.get(ValueLayout.ADDRESS, Tulip.off(L, "output_lens"))
                .reinterpret(8L * numResults);
        MemorySegment stateArr = raw.get(ValueLayout.ADDRESS, Tulip.off(L, "states"))
                .reinterpret(8L * numResults);

        this.states = new State[numResults];
        this.rows = new MemorySegment[numResults][numOutputs];
        this.lens = new long[numResults][numOutputs];
        for (int r = 0; r < numResults; r++) {
            states[r] = new State(indicator, stateArr.get(ValueLayout.ADDRESS, 8L * r));
            MemorySegment laneOuts = outArr.get(ValueLayout.ADDRESS, 8L * r)
                    .reinterpret(8L * numOutputs);
            MemorySegment laneLens = lenArr.get(ValueLayout.ADDRESS, 8L * r)
                    .reinterpret(8L * numOutputs);
            for (int o = 0; o < numOutputs; o++) {
                lens[r][o] = laneLens.get(ValueLayout.JAVA_LONG, 8L * o);
                rows[r][o] = laneOuts.get(ValueLayout.ADDRESS, 8L * o)
                        .reinterpret(8L * lens[r][o])
                        .asReadOnly();
            }
        }
        // Backstop for the OUTER buffers only: each lane State carries its own
        // cleaner for its state object, so this can never double-free those.
        this.task = new FreeTask(outArr, lenArr, stateArr, numResults, numOutputs,
                new AtomicBoolean());
        this.cleanable = CLEANER.register(this, task);
    }

    /**
     * Rebuilds the {@code CSimdResult} from the saved array addresses and hands
     * it to Rust. The free drops the states ARRAY only (never the state
     * objects), so running it after the lane States were closed is exactly the
     * contractual order.
     */
    private record FreeTask(MemorySegment outputs, MemorySegment outputLens, MemorySegment states,
            long numResults, long numOutputs, AtomicBoolean freed) implements Runnable {
        @Override
        public void run() {
            if (!freed.compareAndSet(false, true)) {
                return;
            }
            MemoryLayout L = Tulip.SIMD_RESULT;
            try (Arena a = Arena.ofConfined()) {
                MemorySegment s = a.allocate(L);
                s.set(ValueLayout.JAVA_INT, 0, 0);
                s.set(ValueLayout.ADDRESS, Tulip.off(L, "outputs"), outputs);
                s.set(ValueLayout.ADDRESS, Tulip.off(L, "output_lens"), outputLens);
                s.set(ValueLayout.JAVA_LONG, Tulip.off(L, "num_outputs"), numOutputs);
                s.set(ValueLayout.ADDRESS, Tulip.off(L, "states"), states);
                s.set(ValueLayout.JAVA_LONG, Tulip.off(L, "num_results"), numResults);
                Tulip.invokeVoid(Tulip.SIMD_RESULT_FREE, s);
            }
        }
    }

    /** Number of parallel lanes (assets or option sets). */
    public int numResults() {
        checkOpen();
        return numResults;
    }

    /** Output rows per lane (mandatory + requested optional outputs). */
    public int numOutputs() {
        checkOpen();
        return numOutputs;
    }

    /** Length in f64 values of lane {@code result}'s output row {@code output}. */
    public long rowLength(int result, int output) {
        checkOpen();
        return lens[result][output];
    }

    /** Zero-copy read-only view over lane {@code result}'s output row {@code output}. */
    public MemorySegment row(int result, int output) {
        checkOpen();
        return rows[result][output];
    }

    /** Element {@code index} of lane {@code result}'s output row {@code output}. */
    public double get(int result, int output, long index) {
        checkOpen();
        return rows[result][output].get(ValueLayout.JAVA_DOUBLE, 8L * index);
    }

    /** Copies lane {@code result}'s row {@code output} into a fresh double[]. */
    public double[] toDoubleArray(int result, int output) {
        checkOpen();
        return rows[result][output].toArray(ValueLayout.JAVA_DOUBLE);
    }

    /** Copies every row of lane {@code result} into fresh arrays. */
    public double[][] toArrays(int result) {
        checkOpen();
        double[][] out = new double[numOutputs][];
        for (int o = 0; o < numOutputs; o++) {
            out[o] = toDoubleArray(result, o);
        }
        return out;
    }

    /** The lane's streaming state (continuable with {@code batch}); owned by this result. */
    public State state(int lane) {
        checkOpen();
        return states[lane];
    }

    /** Frees every lane state first, then the SIMD buffers (§7.3 order). Idempotent. */
    @Override
    public void close() {
        for (State s : states) {
            s.close();
        }
        cleanable.clean();
    }

    private void checkOpen() {
        if (task.freed().get()) {
            throw new IllegalStateException("tulip: use of closed SimdResult");
        }
    }
}
