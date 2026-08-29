package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the registered {@link ResponseCacheWrapper}s to the cache a backend module built.
 * <p>
 * Wrapping is left to the module that owns the cache rather than done by a bean post-processor, so that a
 * backend decides for itself what it hands out - and an application that registers no wrapper gets back the
 * very object it would have got without this indirection.
 */
public final class ResponseCacheWrapperUtils {

    private ResponseCacheWrapperUtils() {}

    /**
     * Returns the cache wrapped by every given wrapper, nested by {@link ResponseCacheWrapper#getPriority()}:
     * the highest priority is applied first and therefore ends up closest to the cache, the lowest is applied
     * last and is what a lookup enters first.
     *
     * @param cache    the cache to wrap; handed back unchanged when there is nothing to wrap it with.
     * @param wrappers the wrappers to apply, in any order.
     */
    public static ResponseCache wrapWithPriority(ResponseCache cache, Set<ResponseCacheWrapper> wrappers) {
        Objects.requireNonNull(cache, "cache cannot be null");
        Objects.requireNonNull(wrappers, "wrappers cannot be null");
        if (wrappers.isEmpty()) {
            return cache;
        }

        List<ResponseCacheWrapper> sortedWrappers = wrappers
                .stream()
                .sorted(Comparator.comparingInt(ResponseCacheWrapper::getPriority).reversed())
                .toList();

        ResponseCache wrappedResponseCache = cache;
        for (ResponseCacheWrapper wrapper : sortedWrappers) {
            wrappedResponseCache = wrapper.wrap(wrappedResponseCache);
        }

        return wrappedResponseCache;
    }
}
