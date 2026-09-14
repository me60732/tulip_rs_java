package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.Cleaner;

/**
 * Output rows of an indicator or batch call: zero-copy read-only
 * {@link MemorySegment} views into the Rust-allocated buffers
 * (bindings_memory_model.md §4). The views borrow from this owner — keep the
 * Result open while using them; every accessor throws after {@code close()},
 * which releases the buffers back to Rust and NEVER touches the streaming
 * state handle.
 *
 * <p>{@code close()} is idempotent (CAS-guarded task); a Cleaner registration
 * is the leak backstop, not the mechanism. The C result struct is rebuilt
 * field-by-field at free time from the saved row/lens addresses (the downcall
 * return buffer is confined to the calling frame, and the free never reads
 * the state field — so it is stored NULL, mirroring the Go binding's shims).
 */
public final class Result implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final MemorySegment[] rows;
    private final long[] lengths;
    private final int numOutputs;
    private final FreeTask task;
    private final Cleaner.Cleanable cleanable;

    Result(MemorySegment outputsAddr, MemorySegment lensAddr, int numOutputs, boolean batch) {
        this.numOutputs = numOutputs;
        MemoryLayout layout = batch ? Tulip.BATCH_RESULT : Tulip.INDICATOR_RESULT;
        MemorySegment ptrs = outputsAddr.reinterpret(8L * numOutputs);
        MemorySegment lens = lensAddr.reinterpret(8L * numOutputs);
        this.rows = new MemorySegment[numOutputs];
        this.lengths = new long[numOutputs];
        for (int i = 0; i < numOutputs; i++) {
            lengths[i] = lens.get(ValueLayout.JAVA_LONG, 8L * i);
            rows[i] = ptrs.get(ValueLayout.ADDRESS, 8L * i)
                    .reinterpret(8L * lengths[i])
                    .asReadOnly();
        }
        // Backstop only — the task must never capture `this`.
        this.task = new FreeTask(ptrs, lens, numOutputs, layout, batch, new AtomicBoolean());
        this.cleanable = CLEANER.register(this, task);
    }

    private record FreeTask(MemorySegment outputs, MemorySegment outputLens, long numOutputs,
            MemoryLayout layout, boolean batch, AtomicBoolean freed) implements Runnable {
        @Override
        public void run() {
            if (!freed.compareAndSet(false, true)) {
                return;
            }
            try (Arena a = Arena.ofConfined()) {
                MemorySegment s = a.allocate(layout);
                s.set(ValueLayout.JAVA_INT, 0, 0); // error: irrelevant to the free
                s.set(ValueLayout.ADDRESS, Tulip.off(layout, "outputs"), outputs);
                s.set(ValueLayout.ADDRESS, Tulip.off(layout, "output_lens"), outputLens);
                s.set(ValueLayout.JAVA_LONG, Tulip.off(layout, "num_outputs"), numOutputs);
                if (!batch) {
                    s.set(ValueLayout.ADDRESS,
                            Tulip.off(Tulip.INDICATOR_RESULT, "state"), MemorySegment.NULL);
                }
                Tulip.invokeVoid(batch ? Tulip.BATCH_RESULT_FREE : Tulip.RESULT_FREE, s);
            }
        }
    }

    /** Number of output rows (mandatory + requested optional outputs). */
    public int numOutputs() {
        checkOpen();
        return numOutputs;
    }

    /** Length in f64 values of output row {@code row}. */
    public long rowLength(int row) {
        checkOpen();
        return lengths[row];
    }

    /**
     * Zero-copy read-only view over output row {@code row}. The returned
     * segment borrows from this Result: do not close before reading it.
     */
    public MemorySegment row(int row) {
        checkOpen();
        return rows[row];
    }

    /** Element {@code index} of output row {@code row}. */
    public double get(int row, long index) {
        checkOpen();
        return rows[row].get(ValueLayout.JAVA_DOUBLE, 8L * index);
    }

    /** Copies row {@code row} into a fresh {@code double[]} (use to keep past close()). */
    public double[] toDoubleArray(int row) {
        checkOpen();
        return rows[row].toArray(ValueLayout.JAVA_DOUBLE);
    }

    /** Copies every row into fresh arrays. */
    public double[][] toArrays() {
        checkOpen();
        double[][] out = new double[numOutputs][];
        for (int i = 0; i < numOutputs; i++) {
            out[i] = toDoubleArray(i);
        }
        return out;
    }

    @Override
    public void close() {
        cleanable.clean(); // runs the free exactly once (Cleanable + CAS guard)
    }

    private void checkOpen() {
        if (task.freed().get()) {
            throw new IllegalStateException("tulip: use of closed Result");
        }
    }
}
