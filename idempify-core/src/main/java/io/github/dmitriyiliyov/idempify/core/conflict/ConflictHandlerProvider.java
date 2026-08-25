package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.config.ConflictConfig;

/**
 * Resolves the {@link ConflictHandler} that implements a given strategy.
 * <p>
 * A hand-written handler is outside the lookup: it reaches the call site as an instance on the config, and
 * being supplied is what makes it custom, so it is handed back as it stands and the strategy is never asked.
 */
public interface ConflictHandlerProvider {
    ConflictHandler provide(ConflictConfig config);
}
