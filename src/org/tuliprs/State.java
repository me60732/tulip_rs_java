package org.tuliprs;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-lived streaming state handle — the one native resource a GC language
 * can never make invisible (§4): its lifetime spans many calls, so it always
 * becomes a closeable object. Continue an indicator with {@link #batch},
 * snapshot via {@link #duplicate()}, persist via {@link #serialize} plus the
 * owning indicator's {@code deserializeState}.
 *
 * <p>{@code close()} releases the boxed state to Rust exactly once
 * (idempotent); the Cleaner registration is a leak backstop only.
 */
public final class State implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final Native indicator;
    private final MemorySegment ptr;
    private final FreeTask task;
    private final Cleaner.Cleanable cleanable;

    State(Native indicator, MemorySegment ptr) {
        this.indicator = indicator;
        this.ptr = ptr;
        // The task captures only the free handle + pointer, never `this`.
        this.task = new FreeTask(indicator.stateFree(), ptr, new AtomicBoolean());
        this.cleanable = CLEANER.register(this, task);
    }

    private record FreeTask(MethodHandle stateFree, MemorySegment ptr, AtomicBoolean freed)
            implements Runnable {
        @Override
        public void run() {
            if (freed.compareAndSet(false, true)) {
                Tulip.invokeVoid(stateFree, ptr);
            }
        }
    }

    /** The indicator this state belongs to (id, counts, symbol prefix). */
    public Native indicator() {
        return indicator;
    }

    /** Continues the calculation on new bars; outputs arrive in a fresh Result. */
    public Result batch(double[][] inputs) {
        return batch(inputs, null);
    }

    /** As {@link #batch(double[])} but also requesting optional outputs. */
    public Result batch(double[][] inputs, boolean[] optionalOutputs) {
        checkOpen();
        return indicator.batch(ptr, inputs, optionalOutputs);
    }

    /**
     * Copies the serialized state into managed memory; the native blob is
     * freed inline (copy-then-free: cold path, no handle — §4).
     */
    public byte[] serialize(Format format) {
        checkOpen();
        try (Arena a = Arena.ofConfined()) {
            MemorySegment cb = (MemorySegment) Tulip.invoke(
                    Tulip.STATE_SERIALIZE, a, (int) indicator.id, format.code, ptr);
            MemorySegment p = cb.get(ValueLayout.ADDRESS, Tulip.off(Tulip.BYTES, "ptr"));
            long len = cb.get(ValueLayout.JAVA_LONG, Tulip.off(Tulip.BYTES, "len"));
            if (p.address() == 0) {
                throw new IndicatorException("state serialization failed for indicator \""
                        + indicator.prefix + "\" (the JSON format rejects non-finite values)");
            }
            byte[] out = p.reinterpret(len).toArray(ValueLayout.JAVA_BYTE);
            Tulip.invokeVoid(Tulip.BYTES_FREE, cb); // copy-then-free, inline
            return out;
        }
    }

    /** Value-semantics snapshot via Rust {@code Clone} (§6): independent handle. */
    public State duplicate() {
        checkOpen();
        MemorySegment copy = (MemorySegment) Tulip.invoke(
                Tulip.STATE_CLONE, (int) indicator.id, ptr);
        if (copy.address() == 0) {
            throw new IndicatorException(
                    "state clone failed for indicator \"" + indicator.prefix + '"');
        }
        return new State(indicator, copy);
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private void checkOpen() {
        if (task.freed().get()) {
            throw new IllegalStateException("tulip: operation on closed state handle");
        }
    }
}
