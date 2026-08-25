package io.github.dmitriyiliyov.idempify.core.config;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link IdempotencyConfigRegistry} implementation, backed by a {@link ConcurrentHashMap}.
 * <p>
 * Configs are normally all registered at startup and only read afterwards, but registering later is safe:
 * a name can be claimed once, and a lookup either sees a fully built config or none.
 */
public final class DefaultIdempotencyConfigRegistry implements IdempotencyConfigRegistry {

    private final Map<String, IdempotencyConfig> configs;

    public DefaultIdempotencyConfigRegistry() {
        this.configs = new ConcurrentHashMap<>();
    }

    public DefaultIdempotencyConfigRegistry(Map<String, IdempotencyConfig> configs) {
        Objects.requireNonNull(configs, "configs cannot be null");
        this.configs = new ConcurrentHashMap<>();
        configs.forEach(this::register);
    }

    @Override
    public IdempotencyConfig register(String name, IdempotencyConfig config) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }

        IdempotencyConfig registered = configs.putIfAbsent(name, config);
        if (registered != null) {
            throw new IllegalStateException("IdempotencyConfig with name %s is already registered".formatted(name));
        }
        return config;
    }

    @Override
    public IdempotencyConfig get(String name) {
        return name == null ? null : configs.get(name);
    }

    @Override
    public String toString() {
        return "DefaultIdempotencyConfigRegistry{names=" + configs.keySet() + '}';
    }
}
