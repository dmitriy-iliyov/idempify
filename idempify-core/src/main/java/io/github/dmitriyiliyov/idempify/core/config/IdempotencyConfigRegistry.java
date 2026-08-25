package io.github.dmitriyiliyov.idempify.core.config;

/**
 * Holds the named {@link IdempotencyConfig}s of the application and hands them out by name, so that a call
 * site can select a whole set of settings with a single name instead of restating them.
 * <p>
 * Implementations must be safe to read from several threads at once, since lookups happen on every intercepted
 * call.
 *
 * @see DefaultIdempotencyConfigRegistry
 */
public interface IdempotencyConfigRegistry {

    /**
     * Registers a config under the given name.
     *
     * @param name   the name the config is looked up by.
     * @param config the config to register.
     * @return the registered config.
     * @throws IllegalStateException if a config is already registered under that name.
     */
    IdempotencyConfig register(String name, IdempotencyConfig config);

    /**
     * Returns the config registered under the given name, or {@code null} if there is none.
     *
     * @param name the name the config was registered under.
     * @return the registered config, or {@code null}.
     */
    IdempotencyConfig get(String name);
}
