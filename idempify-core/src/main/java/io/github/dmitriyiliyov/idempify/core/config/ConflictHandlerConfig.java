package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;

/**
 * Marker for the settings of a single {@link ConflictHandler} implementation.
 * <p>
 * Each handler that needs tuning declares its own sub-interface and reads it from
 * {@link ConflictConfig#getHandlerConfig()}, casting to the type it expects; handlers that need no settings
 * ignore it entirely.
 *
 * @see WaitConflictHandlerConfig
 */
public interface ConflictHandlerConfig {

    /**
     * The config of a handler that has nothing to tune, so that such a strategy still carries a value here
     * instead of a {@code null} every reader would have to guard.
     */
    ConflictHandlerConfig NOOP = new ConflictHandlerConfig() { };
}
