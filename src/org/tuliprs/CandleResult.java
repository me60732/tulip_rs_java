package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CSR-packed candlestick detection output (indicator or batch form):
 * {@code bar_offsets} has numBars+1 u32 entries into {@code pattern_ids}
 * (u32 pattern-table ids). Zero-copy views over the Rust-allocated arrays;
 * valid while open, {@code close()} releases the buffers back to Rust and
 * NEVER touches the streaming state (same contract as {@link Result}).
 */
public final class CandleResult implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final MemorySegment offsets; // u32[numBars + 1]
    private final MemorySegment ids;     // u32[totalPatterns]
    private final int numBars;
    private final int totalPatterns;
    private final boolean batch;
    private final FreeTask task;
    private final Cleaner.Cleanable cleanable;

    CandleResult(MemorySegment barOffsets, MemorySegment patternIds, int numBars,
            int totalPatterns, boolean batch) {
        this.offsets = barOffsets.reinterpret(4L * (numBars + 1));
        this.ids = patternIds.reinterpret(4L * Math.max(totalPatterns, 1));
        this.numBars = numBars;
        this.totalPatterns = totalPatterns;
        this.batch = batch;
        this.task = new FreeTask(barOffsets, patternIds, numBars, totalPatterns, batch,
                new AtomicBoolean());
        this.cleanable = CLEANER.register(this, task);
    }

    /** Rebuilds the C result struct from saved parts and frees the CSR buffers. */
    private record FreeTask(MemorySegment offsets, MemorySegment ids, int numBars,
            int totalPatterns, boolean batch, AtomicBoolean freed) implements Runnable {
        @Override
        public void run() {
            if (!freed.compareAndSet(false, true)) {
                return;
            }
            try (Arena a = Arena.ofConfined()) {
                if (batch) {
                    MemoryLayout L = CandleEngine.CANDLE_STICK_BATCH_RESULT;
                    MemorySegment s = a.allocate(L);
                    s.set(ValueLayout.JAVA_INT, 0, 0);
                    s.set(ValueLayout.JAVA_LONG, Tulip.off(L, "num_bars"), numBars);
                    s.set(ValueLayout.JAVA_LONG, Tulip.off(L, "total_patterns"), totalPatterns);
                    s.set(ValueLayout.ADDRESS, Tulip.off(L, "bar_offsets"), offsets);
                    s.set(ValueLayout.ADDRESS, Tulip.off(L, "pattern_ids"), ids);
                    Tulip.invokeVoid(CandleEngine.BATCH_RESULT_FREE, s);
                } else {
                    MemoryLayout L = CandleEngine.CANDLE_STICK_RESULT;
                    MemorySegment s = a.allocate(L);
                    s.set(ValueLayout.JAVA_INT, 0, 0);
                    s.set(ValueLayout.JAVA_LONG, Tulip.off(L, "num_bars"), numBars);
                    s.set(ValueLayout.JAVA_LONG, Tulip.off(L, "total_patterns"), totalPatterns);
                    s.set(ValueLayout.ADDRESS, Tulip.off(L, "bar_offsets"), offsets);
                    s.set(ValueLayout.ADDRESS, Tulip.off(L, "pattern_ids"), ids);
                    s.set(ValueLayout.ADDRESS, Tulip.off(L, "state"), MemorySegment.NULL);
                    Tulip.invokeVoid(CandleEngine.RESULT_FREE, s);
                }
            }
        }
    }

    /** Number of bars covered by this result. */
    public int numBars() {
        checkOpen();
        return numBars;
    }

    /** Total pattern ids across all bars. */
    public int totalPatterns() {
        checkOpen();
        return totalPatterns;
    }

    /** Pattern-table ids detected at bar {@code bar} (copied to a fresh array). */
    public int[] patterns(int bar) {
        checkOpen();
        if (bar < 0 || bar >= numBars) {
            throw new IndexOutOfBoundsException("bar " + bar + " of " + numBars);
        }
        int from = (int) (offsets.get(ValueLayout.JAVA_INT, 4L * bar) & 0xFFFFFFFFL);
        int to = (int) (offsets.get(ValueLayout.JAVA_INT, 4L * (bar + 1)) & 0xFFFFFFFFL);
        int[] out = new int[to - from];
        for (int i = 0; i < out.length; i++) {
            out[i] = (int) (ids.get(ValueLayout.JAVA_INT, 4L * (from + i)) & 0xFFFFFFFFL);
        }
        return out;
    }

    /** Short names of the patterns detected at bar {@code bar}. */
    public String[] names(int bar) {
        int[] pats = patterns(bar);
        String[] names = CandleEngine.patternNames();
        String[] out = new String[pats.length];
        for (int i = 0; i < pats.length; i++) {
            out[i] = names[pats[i]];
        }
        return out;
    }

    /** Flattens every bar's ids into one array (for equality checks past close). */
    public int[][] toArrays() {
        checkOpen();
        int[][] out = new int[numBars][];
        for (int b = 0; b < numBars; b++) {
            out[b] = patterns(b);
        }
        return out;
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private void checkOpen() {
        if (task.freed().get()) {
            throw new IllegalStateException("tulip: use of closed CandleResult");
        }
    }
}
