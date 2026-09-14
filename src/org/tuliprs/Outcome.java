package org.tuliprs;

/** A fresh indicator call: output views plus the continuation state handle. */
public record Outcome(Result result, State state) {}
