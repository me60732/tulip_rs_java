package org.tuliprs;

/**
 * One entry of the stable candlestick pattern table (copied; the C side is
 * process-lifetime, nothing to free). Mirrors {@code CCandlePatternInfo}.
 */
public record CandlePattern(
        int id, String name, String fullName, String japaneseName, String forecast, int bars) {

    /** CForecastType codes (see FFI header); any other value is "no filter". */
    public static final int FORECAST_NONE = -1;
    public static final int FORECAST_BEARISH_REVERSAL = 0;
    public static final int FORECAST_BULLISH_REVERSAL = 1;
    public static final int FORECAST_BEARISH_CONTINUATION = 2;
    public static final int FORECAST_BULLISH_CONTINUATION = 3;
    public static final int FORECAST_BEARISH_REVERSAL_OR_CONTINUATION = 4;
    public static final int FORECAST_BULLISH_REVERSAL_OR_CONTINUATION = 5;

    static String forecastName(int code) {
        return switch (code) {
            case FORECAST_BEARISH_REVERSAL -> "BearishReversal";
            case FORECAST_BULLISH_REVERSAL -> "BullishReversal";
            case FORECAST_BEARISH_CONTINUATION -> "BearishContinuation";
            case FORECAST_BULLISH_CONTINUATION -> "BullishContinuation";
            case FORECAST_BEARISH_REVERSAL_OR_CONTINUATION -> "BearishReversalOrContinuation";
            case FORECAST_BULLISH_REVERSAL_OR_CONTINUATION -> "BullishReversalOrContinuation";
            default -> "Unknown(" + code + ")";
        };
    }
}
