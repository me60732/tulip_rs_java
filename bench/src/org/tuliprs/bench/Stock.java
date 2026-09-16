package org.tuliprs.bench;

/** OHLCV history for one ticker, chronologically ordered (oldest → newest). */
public final class Stock {

    public final String symbol;
    public final double[] open;
    public final double[] high;
    public final double[] low;
    public final double[] close;
    public final double[] volume;

    public Stock(String symbol, double[] open, double[] high, double[] low,
            double[] close, double[] volume) {
        this.symbol = symbol;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public int bars() {
        return close.length;
    }
}
