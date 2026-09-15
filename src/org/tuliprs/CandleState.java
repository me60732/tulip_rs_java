package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-lived candlestick streaming handle (same contract as {@link State}):
 * continue detection with {@link #batch}, persist with {@link #serialize} +
 * the facade's {@code deserializeState}. {@code close()} is idempotent; the
 * Cleaner registration is a leak backstop only.
 */
public final class CandleState implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final MemorySegment ptr;
    private final FreeTask task;
    private final Cleaner.Cleanable cleanable;

    CandleState(MemorySegment ptr) {
        this.ptr = ptr;
        this.task = new FreeTask(CandleEngine.STATE_FREE, ptr, new AtomicBoolean());
        this.cleanable = CLEANER.register(this, task);
    }

    private record FreeTask(java.lang.invoke.MethodHandle stateFree, MemorySegment ptr,
            AtomicBoolean freed) implements Runnable {
        @Override
        public void run() {
            if (freed.compareAndSet(false, true)) {
                Tulip.invokeVoid(stateFree, ptr);
            }
        }
    }

    /** Continues detection on new bars (no forecast filter). */
    public CandleResult batch(double[][] inputs) {
        return batch(inputs, CandlePattern.FORECAST_NONE);
    }

    /** Continues detection on new bars with a forecast filter. */
    public CandleResult batch(double[][] inputs, int forecast) {
        checkOpen();
        return CandleEngine.batch(ptr, inputs, forecast);
    }

    /** Copies the serialized state into managed memory (native blob freed inline). */
    public byte[] serialize(Format format) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment cb = (MemorySegment) Tulip.invoke(
                    Tulip.STATE_SERIALIZE, a, (int) CandleEngine.ID, format.code, ptr);
            MemorySegment p = cb.get(ValueLayout.ADDRESS, Tulip.off(Tulip.BYTES, "ptr"));
            long len = cb.get(ValueLayout.JAVA_LONG, Tulip.off(Tulip.BYTES, "len"));
            if (p.address() == 0) {
                throw new IndicatorException("candlestick state serialization failed");
            }
            byte[] out = p.reinterpret(len).toArray(ValueLayout.JAVA_BYTE);
            Tulip.invokeVoid(Tulip.BYTES_FREE, cb);
            return out;
        }
    }

    /** Value-semantics snapshot via Rust Clone: independent handle. */
    public CandleState duplicate() {
        checkOpen();
        MemorySegment copy = (MemorySegment) Tulip.invoke(
                Tulip.STATE_CLONE, (int) CandleEngine.ID, ptr);
        if (copy.address() == 0) {
            throw new IndicatorException("candlestick state clone failed");
        }
        return new CandleState(copy);
    }

    /** Rehydrates a candlestick state from a {@link #serialize} blob. */
    static CandleState deserialize(byte[] blob) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment bytes = a.allocate(Math.max(blob.length, 1));
            MemorySegment.copy(blob, 0, bytes, ValueLayout.JAVA_BYTE, 0, blob.length);
            MemorySegment ptr = (MemorySegment) Tulip.invoke(
                    Tulip.STATE_DESERIALIZE, bytes, (long) blob.length);
            if (ptr.address() == 0) {
                throw new IndicatorException(
                        "candlestick state deserialization failed (corrupt blob)");
            }
            return new CandleState(ptr);
        }
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private void checkOpen() {
        if (task.freed().get()) {
            throw new IllegalStateException("tulip: operation on closed candlestick state");
        }
    }
}
