package io.github.dmitriyiliyov.idempify.cache.redis;

import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;

/**
 * Stands in for whoever holds the {@code idempify.cache.*} properties - the redis backend only ever asks it
 * for a name, so the capacity of the in-memory fallback is answered with the built-in default.
 */
final class TestCachePropertiesHolder implements CachePropertiesHolder {

    private final String cacheName;

    TestCachePropertiesHolder(String cacheName) {
        this.cacheName = cacheName;
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public int getInMemoryCacheCapacity() {
        return 100;
    }
}
