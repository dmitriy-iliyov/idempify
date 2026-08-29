package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Wraps the response cache a backend module registered, so that a cross-cutting concern - counting, tracing,
 * logging - reaches every lookup without the backend knowing about it.
 * <p>
 * A wrapper is a bean: the module that has something to add contributes one, the module that owns the cache
 * asks {@code ResponseCacheWrapperUtils} to apply them all, and neither module names the other. Nothing is
 * wrapped when no wrapper is registered, so the plain cache stays the plain cache.
 *
 * @see ResponseCacheWrapperUtils
 */
public interface ResponseCacheWrapper {

    /**
     * Returns the cache to be used in place of the given one - normally a decorator delegating to it.
     * <p>
     * The argument is not necessarily the backend's own cache: with several wrappers registered it is
     * whatever the wrappers of higher priority have already produced.
     */
    ResponseCache wrap(ResponseCache responseCache);

    /**
     * Returns how close to the cache this wrapper sits - <strong>the higher the priority, the deeper it is
     * nested</strong>, so the highest of them wraps the backend itself and the lowest is what a lookup enters
     * first.
     * <p>
     * The order matters whenever one wrapper's answer is another's subject: a wrapper that counts what the
     * store answers wants a high priority, one that measures what the caller waits for wants a low one. Two
     * wrappers of equal priority are nested in no particular order.
     */
    int getPriority();
}
