package com.google.research.air.cosmo.compat;

import java.util.Arrays;
import java.util.stream.Stream;

/** Minimal compatibility surface for recovered SODA bytecode helper calls. */
public final class DesugarArrays {
    private DesugarArrays() {}

    public static <T> Stream<T> stream(T[] array) {
        return Arrays.stream(array);
    }
}
