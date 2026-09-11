package io.github.dmitriyiliyov.idempify.core.cache;

import java.util.Arrays;

/**
 * Which cache stands in front of the operation store. The value of {@code idempify.cache.type} is matched
 * against the {@link ConditionalOnCacheType} of every candidate wrapper, so exactly one backend is installed
 * and switching backends is a property change rather than a dependency change.
 * <p>
 * {@code IN_MEMORY} is the cache the core itself supplies; {@code DISTRIBUTED} comes from a backend module
 * such as {@code idempify-cache-redis}. {@code CUSTOM} names neither - nothing in the library declares a
 * wrapper for it, so the application is the only thing that can.
 */
public enum CacheType {
    IN_MEMORY, DISTRIBUTED, CUSTOM;

    public static CacheType fromStr(String str) {
        return Arrays.stream(CacheType.values())
                .filter(t -> t.name().equalsIgnoreCase(str))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "'idempify.cache.type' must be one of %s, but was %s"
                                .formatted(Arrays.toString(values()), str)
                ));
    }
}
