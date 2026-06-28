package com.griefprevention.compat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Compat {

    private Compat() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static String repeat(String s, int count) {
        if (count <= 0 || s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... kv) {
        if (kv.length == 0) {
            return Collections.emptyMap();
        }
        Map<K, V> m = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            m.put((K) kv[i], (V) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }
}
