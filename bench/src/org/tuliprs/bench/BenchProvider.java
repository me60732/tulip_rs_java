package org.tuliprs.bench;

/**
 * Implemented by every per-indicator {@code Bench<Name>} class. The harness
 * discovers them by scanning the bench output directory for classes named
 * {@code Bench[A-Z]*}, so adding a benchmark never touches a central file.
 */
public interface BenchProvider {

    Benchmark def();
}
