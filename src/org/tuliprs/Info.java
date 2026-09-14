package org.tuliprs;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable Java copy of the FFI's {@code CIndicatorInfo} — all strings are
 * copied at read time; nothing here is ever freed (§7.4 of
 * bindings_memory_model.md).
 */
public record Info(
        String name,
        String fullName,
        String type,
        List<String> inputs,
        List<String> options,
        List<String> outputs,
        List<String> optionalOutputs,
        List<DisplayGroup> displayGroups) {

    /**
     * One named grouping of an indicator's outputs (e.g. ADX's "adx_dx"
     * Directional Index group) — how chart/front-end layers should plot them.
     */
    public record DisplayGroup(
            String id, String label, String displayType, String offset, List<String> outputs) {}

    static Info read(MemorySegment s) {
        MemoryLayout L = Tulip.INDICATOR_INFO;
        return new Info(
                Tulip.fieldString(s, L, "name"),
                Tulip.fieldString(s, L, "full_name"),
                typeName(s.get(ValueLayout.JAVA_INT, Tulip.off(L, "indicator_type"))),
                stringArray(s, L, "inputs"),
                stringArray(s, L, "options"),
                stringArray(s, L, "outputs"),
                stringArray(s, L, "optional_outputs"),
                displayGroups(s));
    }

    private static List<String> stringArray(MemorySegment s, MemoryLayout L, String field) {
        long base = Tulip.off(L, field);
        return Tulip.strings(
                s.get(ValueLayout.ADDRESS, base),
                s.get(ValueLayout.JAVA_LONG, base + Tulip.off(Tulip.STRING_ARRAY, "len")));
    }

    private static List<DisplayGroup> displayGroups(MemorySegment s) {
        long base = Tulip.off(Tulip.INDICATOR_INFO, "display_groups");
        MemorySegment ptr = s.get(ValueLayout.ADDRESS, base);
        long n = s.get(ValueLayout.JAVA_LONG, base + Tulip.off(Tulip.DISPLAY_GROUP_ARRAY, "len"));
        if (ptr.address() == 0 || n == 0) {
            return List.of();
        }
        long size = Tulip.DISPLAY_GROUP.byteSize();
        MemorySegment arr = ptr.reinterpret(size * n);
        List<DisplayGroup> out = new ArrayList<>((int) n);
        for (int i = 0; i < n; i++) {
            long g = i * size;
            long outputsAt = g + Tulip.off(Tulip.DISPLAY_GROUP, "outputs");
            out.add(new DisplayGroup(
                    Tulip.cstr(arr.get(ValueLayout.ADDRESS, g + Tulip.off(Tulip.DISPLAY_GROUP, "id"))),
                    Tulip.cstr(arr.get(ValueLayout.ADDRESS, g + Tulip.off(Tulip.DISPLAY_GROUP, "label"))),
                    displayTypeName(arr.get(ValueLayout.JAVA_INT,
                            g + Tulip.off(Tulip.DISPLAY_GROUP, "display_type"))),
                    Tulip.cstr(arr.get(ValueLayout.ADDRESS,
                            g + Tulip.off(Tulip.DISPLAY_GROUP, "offset"))),
                    Tulip.strings(
                            arr.get(ValueLayout.ADDRESS, outputsAt),
                            arr.get(ValueLayout.JAVA_LONG,
                                    outputsAt + Tulip.off(Tulip.STRING_ARRAY, "len")))));
        }
        return List.copyOf(out);
    }

    private static String typeName(int code) {
        return switch (code) {
            case 0 -> "Trend";
            case 1 -> "Momentum";
            case 2 -> "Volume";
            case 3 -> "Volatility";
            case 4 -> "Price";
            case 5 -> "Cycle";
            case 6 -> "CandleStick";
            case 7 -> "Math";
            case 8 -> "Other";
            default -> "Unknown(" + code + ")";
        };
    }

    private static String displayTypeName(int code) {
        return switch (code) {
            case 0 -> "Overlay";
            case 1 -> "Indicator";
            case 2 -> "Volume";
            case 3 -> "Price";
            default -> "Unknown(" + code + ")";
        };
    }
}
