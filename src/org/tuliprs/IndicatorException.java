package org.tuliprs;

/**
 * Runtime mirror of the FFI's {@code CIndicatorError} codes, plus any
 * binding-local failure (code 0).
 */
public class IndicatorException extends RuntimeException {

    public static final int INVALID_INPUTS = 1;
    public static final int NOT_ENOUGH_DATA = 2;
    public static final int INVALID_OPTIONS = 3;
    public static final int INVALID_INDICATOR_STATE = 4;

    /** The FFI error code (0 for binding-local failures). */
    public final int code;

    IndicatorException(int code) {
        super(message(code));
        this.code = code;
    }

    IndicatorException(String message) {
        super("tulip: " + message);
        this.code = 0;
    }

    IndicatorException(String message, Throwable cause) {
        super("tulip: " + message, cause);
        this.code = 0;
    }

    private static String message(int code) {
        return switch (code) {
            case INVALID_INPUTS ->
                    "tulip: invalid inputs (nil/empty series or mismatched lengths)";
            case NOT_ENOUGH_DATA -> "tulip: not enough data";
            case INVALID_OPTIONS -> "tulip: invalid options";
            case INVALID_INDICATOR_STATE -> "tulip: invalid indicator state";
            default -> "tulip: ffi error " + code;
        };
    }
}
