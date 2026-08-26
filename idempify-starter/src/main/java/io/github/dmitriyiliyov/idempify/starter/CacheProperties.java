package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.IdempifyDefaults;
import io.github.dmitriyiliyov.idempify.core.StringUtils;
import io.github.dmitriyiliyov.idempify.core.config.ResponseCacheConfig;
import io.github.dmitriyiliyov.idempify.core.response.CachePropertiesHolder;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

/**
 * Holds the {@code idempify.cache.*} properties - which results of a completed operation are copied into the
 * secondary store that sits in front of the repository.
 * <p>
 * The repository stays the source of truth either way, so these properties tune latency and database load,
 * not behaviour.
 * <p>
 * Caching is off until asked for, and asking for it means naming it: {@code cacheName} is what keeps the
 * entries of one application apart from another's in a store they share, so there is no default worth
 * inventing - a shared one would silently merge them. An application that says nothing about the cache
 * therefore starts; one that sets {@code idempify.cache.enabled: true} must also set
 * {@code idempify.cache.cache-name}.
 * <p>
 * Every other property is answered: an absent one falls back to its {@code @DefaultValue}, so this block
 * always hands the core a fully decided cache policy.
 * <p>
 * <strong>Switching the cache off here is final.</strong> Neither a named {@code IdempotencyConfig} nor
 * {@code @Idempotent(useCache = ENABLE)} can turn it back on for a single call site: this same property keeps
 * the backend's auto-configuration and the caching filter out of the context, so there would be nothing to
 * cache into - and {@code ResponseCacheConfig.merge} refuses the promotion rather than resolve metadata that
 * lies about it. Turning it back on is a change to this property, not to a call site.
 */
public final class CacheProperties implements CachePropertiesHolder {

    private final Boolean enabled;
    private final String cacheName;
    private final Boolean shouldCache4xx;
    private final Boolean shouldCache5xx;
    private final InMemoryCacheProperties inMemory;

    public CacheProperties(@DefaultValue(IdempifyDefaults.CACHE_ENABLED_VALUE) Boolean enabled,
                           String cacheName,
                           @DefaultValue(IdempifyDefaults.CACHE_4XX_VALUE) Boolean shouldCache4xx,
                           @DefaultValue(IdempifyDefaults.CACHE_5XX_VALUE) Boolean shouldCache5xx,
                           @DefaultValue InMemoryCacheProperties inMemory) {
        this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
        if (enabled && StringUtils.isBlank(cacheName)) {
            throw new IllegalArgumentException("""
                        cacheName cannot be null, empty or blank: set idempify.cache.cache-name, 
                        or set idempify.cache.enabled to false if the application does not cache responses
            """);
        }
        this.cacheName = cacheName;
        this.shouldCache4xx = Objects.requireNonNull(shouldCache4xx, "shouldCache4xx cannot be null");
        this.shouldCache5xx = Objects.requireNonNull(shouldCache5xx, "shouldCache5xx cannot be null");
        this.inMemory = Objects.requireNonNull(inMemory, "inMemory cannot be null");
    }

    public ResponseCacheConfig toResponseCacheConfig() {
        return ResponseCacheConfig.builder()
                .enabled(enabled)
                .shouldCache4xx(shouldCache4xx)
                .shouldCache5xx(shouldCache5xx)
                .build();
    }

    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getCacheName() {
        return cacheName;
    }

    @Override
    public int getInMemoryCacheCapacity() {
        return inMemory.getCapacity();
    }

    public Boolean shouldCache4xx() {
        return shouldCache4xx;
    }

    public Boolean shouldCache5xx() {
        return shouldCache5xx;
    }

    @Override
    public String toString() {
        return "CacheProperties{" +
                "enabled=" + enabled +
                ", cacheName='" + cacheName + '\'' +
                ", shouldCache4xx=" + shouldCache4xx +
                ", shouldCache5xx=" + shouldCache5xx +
                ", inMemory=" + inMemory +
                '}';
    }

    /**
     * Holds {@code idempify.cache.in-memory.*} - how much the fallback store keeps when no backend module is
     * on the classpath. The bound store evicts its eldest entry rather than grow, so the capacity is the
     * ceiling an application accepts for holding responses in its own heap.
     */
    public static final class InMemoryCacheProperties {

        private final Integer capacity;

        public InMemoryCacheProperties(
                @DefaultValue(IdempifyDefaults.IN_MEMORY_CACHE_CAPACITY_VALUE) Integer capacity
        ) {
            Objects.requireNonNull(capacity, "capacity cannot be null");
            if (capacity <= 0) {
                throw new IllegalArgumentException(
                        "idempify.cache.in-memory.capacity must be positive, but was %s: a store that holds nothing "
                                .formatted(capacity)
                                + "answers no replay, so set a capacity or leave the property out"
                );
            }
            this.capacity = capacity;
        }

        public Integer getCapacity() {
            return capacity;
        }

        @Override
        public String toString() {
            return "InMemoryCacheProperties{capacity=" + capacity + '}';
        }
    }
}
