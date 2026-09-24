package org.tuliprs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared low-level plumbing for the Java bindings to the tulip_rs_ffi C API:
 * library loading, struct layouts, generic downcalls, and marshalling helpers.
 *
 * Implements the ownership contract documented in the repo-root
 * bindings_memory_model.md:
 *
 * <ul>
 * <li>Indicator calls return rows allocated by Rust. {@link Result#row(int)}
 * hands out zero-copy read-only {@link MemorySegment} views valid until
 * {@code close()}, which releases the buffers back to Rust. Close never
 * touches the streaming state handle.</li>
 * <li>{@link State} handles are long-lived, released exactly once via
 * {@code close()} (idempotent).</li>
 * <li>{@link SimdResult#close()} releases every lane state FIRST, then the
 * outer SIMD buffers — that order is contractual (§2).</li>
 * <li>Serialized blobs are copied into a {@code byte[]} and the native buffer
 * freed inside the same call (copy-then-free: cold path, no handle).</li>
 * <li>{@link Info} strings are process-lifetime on the Rust side: copied
 * once, never freed.</li>
 * </ul>
 *
 * Cleaner registrations are leak nets, not mechanisms — prefer
 * try-with-resources.
 */
public final class Tulip {

    static final Linker LINKER = Linker.nativeLinker();
    static final Arena GLOBAL_ARENA = Arena.global();
    static final Path LIBRARY_PATH = resolveLibraryPath();
    static final SymbolLookup LOOKUP = SymbolLookup.libraryLookup(LIBRARY_PATH, GLOBAL_ARENA);

    // ---- struct layouts (FFM inserts the padding the C ABI needs) ---------

    static final MemoryLayout STRING_ARRAY = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("ptr"),
            ValueLayout.JAVA_LONG.withName("len"));

    static final MemoryLayout DISPLAY_GROUP = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("id"),
            ValueLayout.ADDRESS.withName("label"),
            ValueLayout.JAVA_INT.withName("display_type"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("offset"),
            STRING_ARRAY.withName("outputs"));

    static final MemoryLayout DISPLAY_GROUP_ARRAY = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("ptr"),
            ValueLayout.JAVA_LONG.withName("len"));

    static final MemoryLayout INDICATOR_INFO = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("name"),
            ValueLayout.ADDRESS.withName("full_name"),
            ValueLayout.JAVA_INT.withName("indicator_type"),
            MemoryLayout.paddingLayout(4),
            STRING_ARRAY.withName("inputs"),
            STRING_ARRAY.withName("options"),
            STRING_ARRAY.withName("outputs"),
            STRING_ARRAY.withName("optional_outputs"),
            DISPLAY_GROUP_ARRAY.withName("display_groups"));

    static final MemoryLayout INDICATOR_RESULT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("error"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("outputs"),
            ValueLayout.ADDRESS.withName("output_lens"),
            ValueLayout.JAVA_LONG.withName("num_outputs"),
            ValueLayout.ADDRESS.withName("state"));

    static final MemoryLayout BATCH_RESULT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("error"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("outputs"),
            ValueLayout.ADDRESS.withName("output_lens"),
            ValueLayout.JAVA_LONG.withName("num_outputs"));

    static final MemoryLayout SIMD_RESULT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("error"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("outputs"),
            ValueLayout.ADDRESS.withName("output_lens"),
            ValueLayout.JAVA_LONG.withName("num_outputs"),
            ValueLayout.ADDRESS.withName("states"),
            ValueLayout.JAVA_LONG.withName("num_results"));

    static final MemoryLayout BYTES = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("ptr"),
            ValueLayout.JAVA_LONG.withName("len"));

    // ---- generic downcalls shared by every indicator ----------------------

    static final MethodHandle RESULT_FREE =
            downcall("tulip_ffi_result_free", FunctionDescriptor.ofVoid(INDICATOR_RESULT));
    static final MethodHandle BATCH_RESULT_FREE =
            downcall("tulip_ffi_batch_result_free", FunctionDescriptor.ofVoid(BATCH_RESULT));
    static final MethodHandle SIMD_RESULT_FREE =
            downcall("tulip_ffi_simd_result_free", FunctionDescriptor.ofVoid(SIMD_RESULT));
    static final MethodHandle BYTES_FREE =
            downcall("tulip_ffi_bytes_free", FunctionDescriptor.ofVoid(BYTES));
    static final MethodHandle STATE_SERIALIZE = downcall("tulip_state_serialize",
            FunctionDescriptor.of(BYTES,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    static final MethodHandle STATE_DESERIALIZE = downcall("tulip_state_deserialize",
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    static final MethodHandle STATE_CLONE = downcall("tulip_state_clone",
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private Tulip() {}

    // ---- library discovery -------------------------------------------------

    /**
     * Resolves the native library. Search order (first hit wins):
     * {@code -Dtulip.ffi.library} / {@code TULIP_RS_FFI_LIBRARY} override,
     * then — walking up from the working directory — {@code ffi/lib}
     * (prebuilt, installed by {@code bootstrap.sh --prebuilt}) and the
     * sibling source build {@code tulip_rs_ffi/target/{release,debug}}
     * ({@code bootstrap.sh --source}, which therefore overrides the jar
     * baseline), and finally the native embedded in the platform classifier
     * jar ({@code tulip-rs-java-<version>-<os>-<arch>.jar}) extracted to a
     * temp file. Windows has no classifier artifact (FFM cannot load the
     * static lib shipped by ffi releases) — use {@code bootstrap.sh --source}.
     */
    private static Path resolveLibraryPath() {
        String override = System.getProperty("tulip.ffi.library");
        if (override == null) {
            override = System.getenv("TULIP_RS_FFI_LIBRARY");
        }
        if (override != null) {
            return Path.of(override);
        }
        String libName = System.mapLibraryName("tulip_rs_ffi"); // e.g. libtulip_rs_ffi.so
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path prebuilt = base.resolve("ffi").resolve("lib").resolve(libName);
            if (Files.isRegularFile(prebuilt)) {
                return prebuilt;
            }
            for (String profile : new String[] {"release", "debug"}) {
                Path p = base.resolve("tulip_rs_ffi").resolve("target").resolve(profile).resolve(libName);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        }
        // Last resort: the platform classifier jar embedded this JVM's native
        // (x86-64-v3 / aarch64 baseline — portable, not CPU-tuned).
        Path bundled = extractBundled(libName);
        if (bundled != null) {
            return bundled;
        }
        throw new IllegalStateException("could not locate " + libName + " near "
                + Path.of("").toAbsolutePath() + " and none was embedded on this "
                + "platform (" + System.getProperty("os.name") + "/"
                + System.getProperty("os.arch") + "). Add the tulip-rs-java "
                + "platform classifier dependency, run ./bootstrap.sh --prebuilt or"
                + " --source (required on Windows), or set"
                + " -Dtulip.ffi.library=/path/to/" + libName + " / TULIP_RS_FFI_LIBRARY");
    }

    /**
     * Maps the running JVM to the embedded-native directory key used by the
     * platform classifier jars (mirrors the ffi release asset names).
     * Returns null for unsupported platforms (e.g. Windows).
     */
    private static String platformKey() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osKey;
        if (os.contains("linux")) {
            osKey = "linux";
        } else if (os.contains("mac") || os.contains("macos") || os.contains("darwin")) {
            osKey = "darwin";
        } else {
            return null;
        }
        String archKey = switch (arch) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            default -> null;
        };
        return archKey == null ? null : osKey + "-" + archKey;
    }

    /**
     * Extracts {@code /native/<platform>/<libName>} from the classpath (the
     * platform classifier jar) into a temp file and returns its path, or
     * null when no embedded native matches this JVM.
     *
     * <p>deleteOnExit covers normal JVM shutdowns; SIGKILL leaks a temp file
     * in java.io.tmpdir — harmless, cleaned by the OS eventually.
     */
    private static Path extractBundled(String libName) {
        String key = platformKey();
        if (key == null) {
            return null;
        }
        String resource = "/native/" + key + "/" + libName;
        try (InputStream in = Tulip.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            int dot = libName.lastIndexOf('.');
            Path tmp = Files.createTempFile("tulip_rs_ffi-", dot >= 0 ? libName.substring(dot) : ".lib");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
                in.transferTo(out);
            }
            tmp.toFile().deleteOnExit();
            if (!tmp.toFile().setExecutable(true, false)) {
                // Best effort: some platforms/filesystems ignore this; loadLibrary
                // fails with a clear error if the OS actually needs +x.
            }
            return tmp;
        } catch (IOException e) {
            throw new IllegalStateException("failed to extract bundled native " + resource, e);
        }
    }

    // ---- downcall helpers --------------------------------------------------

    static MethodHandle downcall(String symbol, FunctionDescriptor desc) {
        MemorySegment addr = LOOKUP.find(symbol).orElseThrow(
                () -> new UnsatisfiedLinkError("tulip_rs_ffi: symbol not found: " + symbol
                        + " in " + LIBRARY_PATH));
        return LINKER.downcallHandle(addr, desc);
    }

    static MethodHandle downcallOrNull(String symbol, FunctionDescriptor desc) {
        return LOOKUP.find(symbol)
                .map(addr -> LINKER.downcallHandle(addr, desc))
                .orElse(null);
    }

    static Object invoke(MethodHandle h, Object... args) {
        try {
            return h.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new IndicatorException("FFI downcall failed: " + t, t);
        }
    }

    static void invokeVoid(MethodHandle h, Object... args) {
        invoke(h, args);
    }

    // ---- struct field helpers ----------------------------------------------

    static long off(MemoryLayout layout, String field) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(field));
    }

    /** Reads a null-terminated C string at the pointer stored in a field. */
    static String fieldString(MemorySegment s, MemoryLayout layout, String field) {
        return cstr(s.get(ValueLayout.ADDRESS, off(layout, field)));
    }

    /** Copies a null-terminated UTF-8 C string into a Java String. */
    static String cstr(MemorySegment addr) {
        if (addr.address() == 0) {
            return null;
        }
        MemorySegment s = addr.reinterpret(Long.MAX_VALUE);
        int len = 0;
        while (s.get(ValueLayout.JAVA_BYTE, len) != 0) {
            len++;
        }
        byte[] buf = new byte[len];
        MemorySegment.copy(s, ValueLayout.JAVA_BYTE, 0, buf, 0, len);
        return new String(buf, StandardCharsets.UTF_8);
    }

    /** Copies a {@code CStringArray} (given as ptr + len) into a String[]. */
    static List<String> strings(MemorySegment ptrArray, long len) {
        if (ptrArray.address() == 0 || len == 0) {
            return List.of();
        }
        MemorySegment arr = ptrArray.reinterpret(8L * len);
        List<String> out = new ArrayList<>((int) len);
        for (int i = 0; i < len; i++) {
            out.add(cstr(arr.get(ValueLayout.ADDRESS, 8L * i)));
        }
        return List.copyOf(out);
    }

    // ---- input marshalling (Arena closed after the call: Rust never keeps
    //      input pointers past the call, per bindings_memory_model.md §2) ----

    static MemorySegment packOptions(Arena arena, double[] options) {
        double[] padded = options == null || options.length == 0
                ? new double[] {0.0} : options;
        MemorySegment seg = arena.allocate(8L * padded.length, 8);
        MemorySegment.copy(padded, 0, seg, ValueLayout.JAVA_DOUBLE, 0, padded.length);
        return seg;
    }

    static MemorySegment packBools(Arena arena, boolean[] flags) {
        if (flags == null || flags.length == 0) {
            return MemorySegment.NULL;
        }
        byte[] bytes = new byte[flags.length];
        for (int i = 0; i < flags.length; i++) {
            bytes[i] = (byte) (flags[i] ? 1 : 0);
        }
        MemorySegment seg = arena.allocate(bytes.length);
        MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length);
        return seg;
    }

    static MemorySegment packInputs(Arena arena, double[][] inputs) {
        MemorySegment ptrs = arena.allocate(8L * inputs.length, 8);
        for (int i = 0; i < inputs.length; i++) {
            MemorySegment row = arena.allocate(8L * inputs[i].length, 8);
            MemorySegment.copy(inputs[i], 0, row, ValueLayout.JAVA_DOUBLE, 0, inputs[i].length);
            ptrs.set(ValueLayout.ADDRESS, 8L * i, row);
        }
        return ptrs;
    }
}
