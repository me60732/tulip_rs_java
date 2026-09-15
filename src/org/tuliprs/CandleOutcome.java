package org.tuliprs;

/** A fresh candlestick detection run: CSR output plus the continuation state. */
public record CandleOutcome(CandleResult result, CandleState state) {}
