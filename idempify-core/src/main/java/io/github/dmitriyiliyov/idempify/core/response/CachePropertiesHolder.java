package io.github.dmitriyiliyov.idempify.core.response;

/**
 * Tells a {@link ResponseCache} backend where its entries live.
 * <p>
 * The address of the store is deliberately kept out of {@code ResponseCacheConfig}: that config decides
 * <em>what</em> is worth caching and is layered per call site, while the name is a property of the deployment
 * that no policy reads and every entry shares. Keeping them apart is also what lets a backend depend on this
 * one method instead of on whoever holds the {@code idempify.*} properties.
 * <p>
 * The starter declares the bean; a backend asks for it and is the one that rejects a missing name, since only
 * it knows whether a name is needed at all.
 *
 * @see ResponseCache
 */
public interface CachePropertiesHolder {
    String getCacheName();
    int getInMemoryCacheCapacity();
}
