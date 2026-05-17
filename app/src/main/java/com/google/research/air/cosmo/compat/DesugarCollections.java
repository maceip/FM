package com.google.research.air.cosmo.compat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Minimal compatibility surface for recovered SODA bytecode helper calls. */
public final class DesugarCollections {
    private DesugarCollections() {}

    public static <T> List<T> unmodifiableList(List<? extends T> list) {
        return Collections.unmodifiableList(list);
    }

    public static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> map) {
        return Collections.unmodifiableMap(map);
    }

    public static <T> Set<T> unmodifiableSet(Set<? extends T> set) {
        return Collections.unmodifiableSet(set);
    }
}
