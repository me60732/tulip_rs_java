package org.tuliprs;

/** Wire format selector for state serialization (FFI {@code CStateFormat}). */
public enum Format {

    /** Compact binary encoding; handles NaN/Inf. Recommended for persistence. */
    BINCODE(0),

    /** Human-readable JSON (serde_json); fails on non-finite f64s. */
    JSON(1);

    public final int code;

    Format(int code) {
        this.code = code;
    }
}
