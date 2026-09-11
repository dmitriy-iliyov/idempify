package io.github.dmitriyiliyov.idempify.core.cache;

/**
 * The cache settings the core reads from whoever bound them, so that binding {@code idempify.cache.*} stays
 * in the starter and out of the core.
 * <p>
 * {@link #getName()} is what a shared backend keeps one application's entries apart by, and is required only
 * there - an in-memory cache has no use for it and may be given {@code null}. {@link #getCacheCapacity()}
 * bounds an in-memory cache and means nothing to a backend that expires entries itself.
 */
public interface CachePropertiesHolder {
    String getName();

    int getCacheCapacity();
}
