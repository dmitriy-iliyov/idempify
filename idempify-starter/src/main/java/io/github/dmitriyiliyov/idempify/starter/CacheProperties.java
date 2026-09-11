package io.github.dmitriyiliyov.idempify.starter;

import io.github.dmitriyiliyov.idempify.core.StringUtils;
import io.github.dmitriyiliyov.idempify.core.cache.CachePropertiesHolder;
import io.github.dmitriyiliyov.idempify.core.cache.CacheType;
import io.github.dmitriyiliyov.idempify.core.config.IdempifyDefaults;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

/**
 * Holds the {@code idempify.cache.*} properties - whether a cache stands in front of the response store,
 * which one, and how it is bounded.
 * <p>
 * The record stays the source of truth either way: a cache here only answers a replay sooner, and a miss
 * falls through to the row. That is why these properties tune latency and database load and never what a
 * repeat call receives.
 * <p>
 * Caching is off until {@code idempify.cache.enabled} asks for it, and what it then installs is decided by
 * {@code idempify.cache.type}. The remaining two are read by whichever backend needs them:
 * {@code idempify.cache.name} keeps one application's entries apart from another's in a store they share and
 * is therefore required of {@link CacheType#DISTRIBUTED} - an in-memory cache shares nothing and leaves it
 * unset - while {@code idempify.cache.capacity} bounds {@link CacheType#IN_MEMORY} and must be positive,
 * a cache holding nothing being a cache that answers no replay.
 * <p>
 * Both are checked in the constructor rather than where they are used, so a misconfigured application fails
 * to start instead of failing on its first replay.
 */
public final class CacheProperties implements CachePropertiesHolder {

    private final Boolean enabled;
    private final CacheType type;
    private final String name;
    private final Integer capacity;

    public CacheProperties(@DefaultValue(IdempifyDefaults.CACHE_ENABLED_VALUE) Boolean enabled,
                           @DefaultValue(IdempifyDefaults.CACHE_TYPE_VALUE) CacheType type,
                           String name,
                           @DefaultValue(IdempifyDefaults.IN_MEMORY_CACHE_CAPACITY_VALUE) Integer capacity) {
        this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
        if (enabled) {
            this.type = Objects.requireNonNull(type, "type cannot be null");

            if (CacheType.DISTRIBUTED.equals(type) && StringUtils.isBlank(name)) {
                throw new IllegalArgumentException("""
                        'idempify.cache.name' cannot be null when type is %s, empty or blank: set 'idempify.cache.name', 
                        or set 'idempify.cache.enabled' to false if the application does not cache responses
            """.formatted(type));
            }
            this.name = name;

            Objects.requireNonNull(capacity, "capacity cannot be null");
            if (CacheType.IN_MEMORY.equals(type) && capacity <= 0) {
                throw new IllegalArgumentException(
                        "'idempify.cache.capacity' must be positive, but was %s: a store that holds nothing "
                                .formatted(capacity)
                                + "answers no replay, so set a capacity or leave the property out"
                );
            }
            this.capacity = capacity;
        } else {
            this.type = null;
            this.name = null;
            this.capacity = 0;
        }
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public CacheType getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getCacheCapacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return "CacheProperties{" +
                "enabled=" + enabled +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", capacity=" + capacity +
                '}';
    }
}
