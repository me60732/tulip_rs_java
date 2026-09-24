package org.tuliprs.demo;

import java.util.Arrays;

/**
 * Shared example harness: deterministic synthetic series, NaN-safe output
 * comparison, and pass/fail bookkeeping. Mirrors the Go binding's
 * examples/internal/demo package.
 */
public final class Demo {

    private Demo() {}

    /** Accumulates assertions; {@link #done()} prints the verdict. */
    public static final class Check {

        private boolean failed;

        public void require(boolean cond, String msg) {
            if (!cond) {
                System.out.println("  FAIL: " + msg);
                failed = true;
            }
        }

        public void match(String what, boolean cond) {
            System.out.println((cond ? "  MATCH: " : "  MISMATCH: ") + what);
            if (!cond) {
                failed = true;
            }
        }

        public void done() {
            if (failed) {
                System.out.println("\nSOME CHECKS FAILED");
                System.exit(1);
            }
            System.out.println("\nALL CHECKS PASSED");
        }
    }

    /**
     * NaN-safe bit-exact comparison (NaN==NaN counts). Use only where the
     * core guarantees bit-identical results (scalar-vs-scalar code paths).
     */
    public static boolean same(double[] a, double[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            double x = a[i], y = b[i];
            if (x != y && !(Double.isNaN(x) && Double.isNaN(y))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares with |a-b| <= abs + rel*max(|a|,|b|). SIMD-vs-scalar code paths
     * legitimately differ by ulps (vectorized accumulation, reciprocal
     * division), so examples comparing them must use tolerance, not equality.
     */
    public static boolean sameTol(double[] a, double[] b, double rel, double abs) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            double x = a[i], y = b[i];
            if (Double.isNaN(x) && Double.isNaN(y)) {
                continue;
            }
            double tol = abs + rel * Math.max(Math.abs(x), Math.abs(y));
            if (Math.abs(x - y) > tol) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds plausible, non-degenerate input series for the named FFI inputs
     * ("high", "low", "open", "volume", "close"/"real"). Oscillating +
     * mild drift keeps filter math (atan/sin, division) away from NaN traps
     * that constant or linear data would hit.
     */
    public static double[][] seriesFor(String[] names, int n) {
        double[] cs = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i;
            cs[i] = 100 + 10 * Math.sin(t * 0.3) + t * 0.05;
        }
        double[][] out = new double[names.length][];
        for (int i = 0; i < names.length; i++) {
            out[i] = switch (names[i]) {
                case "high" -> offset(cs, 2);
                case "low" -> offset(cs, -2);
                case "open" -> lag(cs);
                case "volume" -> volume(n);
                default -> cs.clone(); // "close", "real"
            };
        }
        return out;
    }

    /** Multiplies a series by k (for SIMD asset variants). */
    public static double[] scale(double[] s, double k) {
        double[] out = new double[s.length];
        for (int i = 0; i < s.length; i++) {
            out[i] = s[i] * k;
        }
        return out;
    }

    public static double[] slice(double[] s, int from, int to) {
        return Arrays.copyOfRange(s, from, to);
    }

    private static double[] offset(double[] cs, double k) {
        double[] o = new double[cs.length];
        for (int i = 0; i < cs.length; i++) {
            o[i] = cs[i] + k;
        }
        return o;
    }

    private static double[] lag(double[] cs) {
        double[] l = new double[cs.length];
        l[0] = cs[0];
        System.arraycopy(cs, 0, l, 1, cs.length - 1);
        return l;
    }

    private static double[] volume(int n) {
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = 1e6 + i * 137.0;
        }
        return v;
    }
}
