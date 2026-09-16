package org.tuliprs.bench;

/**
 * One indicator benchmark: the option-set grid (mirroring the Python/Rust/Go
 * suites) plus the implementation closures to time. {@code ta4jFn},
 * {@code simdAssetsFn} and {@code simdOptionsFn} may be null — the harness
 * skips absent phases (never registers no-op closures).
 *
 * <p>Build with the fluent {@link #builder(String)} so argument order can
 * never be mixed up across the 90+ per-indicator files.
 */
public final class Benchmark {

    public final String name;
    public final double[][] options;
    public final Harness.RunFn tulipFn;
    public final Harness.RunFn ta4jFn;
    public final Harness.SimdAssetsFn simdAssetsFn;
    public final Harness.SimdOptionsFn simdOptionsFn;

    private Benchmark(Builder b) {
        this.name = b.name;
        this.options = b.options;
        this.tulipFn = b.tulipFn;
        this.ta4jFn = b.ta4jFn;
        this.simdAssetsFn = b.simdAssetsFn;
        this.simdOptionsFn = b.simdOptionsFn;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private double[][] options = {{}};
        private Harness.RunFn tulipFn;
        private Harness.RunFn ta4jFn;
        private Harness.SimdAssetsFn simdAssetsFn;
        private Harness.SimdOptionsFn simdOptionsFn;

        private Builder(String name) {
            this.name = name;
        }

        /** Option sets swept per stock — copy from the Go bench twin file. */
        public Builder options(double[][] options) {
            this.options = options;
            return this;
        }

        /** tulip-rs-java implementation (required). */
        public Builder tulip(Harness.RunFn fn) {
            this.tulipFn = fn;
            return this;
        }

        /** ta4j reference, or null when no param-compatible twin exists. */
        public Builder ta4j(Harness.RunFn fn) {
            this.ta4jFn = fn;
            return this;
        }

        public Builder simdAssets(Harness.SimdAssetsFn fn) {
            this.simdAssetsFn = fn;
            return this;
        }

        public Builder simdOptions(Harness.SimdOptionsFn fn) {
            this.simdOptionsFn = fn;
            return this;
        }

        public Benchmark build() {
            if (tulipFn == null) {
                throw new IllegalStateException("benchmark " + name + ": tulipFn is required");
            }
            return new Benchmark(this);
        }
    }
}
