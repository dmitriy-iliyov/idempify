package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;
import io.github.dmitriyiliyov.idempify.core.config.ConflictConfig;
import io.github.dmitriyiliyov.idempify.core.config.ConflictHandlerConfig;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;

import java.time.Clock;
import java.util.Objects;

/**
 * Builds the built-in handlers directly rather than through a registry of per-strategy factories.
 * <p>
 * The set of strategies is closed by {@link ConflictHandleStrategy}, every built-in handler lives in this
 * module, and a user-written handler reaches the call site as an instance on the config - so there is no
 * third party left to register a factory, and nothing for one to reach that this class cannot.
 */
public class DefaultConflictHandlerProvider implements ConflictHandlerProvider {

    private final OperationRepository repository;
    private final ResultDeserializer resultDeserializer;
    private final Clock clock;

    public DefaultConflictHandlerProvider(OperationRepository repository,
                                          ResultDeserializer resultDeserializer,
                                          Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "resultDeserializer cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public ConflictHandler provide(ConflictConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        if (!config.isEnabled()) {
            return null;
        }

        ConflictHandler handler = config.getHandler();
        if (handler != null) {
            return handler;
        }

        ConflictHandleStrategy strategy = config.getStrategy();
        if (strategy == null) {
            return null;
        }

        return switch (strategy) {
            case REJECT -> new RejectConflictHandler();
            case WAIT -> waitHandler(config);
        };
    }

    /**
     * Answers {@code null} when the backoff was never decided: a handler polling on settings this class made
     * up would wait on a schedule nobody chose, which is worse than leaving the duplicate to the caller.
     * A backoff of the wrong type is a different matter - somebody did decide it, and decided it wrong.
     */
    private ConflictHandler waitHandler(ConflictConfig config) {
        ConflictHandlerConfig handlerConfig = config.getHandlerConfig();
        if (handlerConfig == null) {
            return null;
        }

        if (!(handlerConfig instanceof WaitConflictHandlerConfig waitConflictHandlerConfig)) {
            throw new IllegalStateException(
                    "handlerConfig must be a WaitConflictHandlerConfig when strategy is WAIT, but was %s"
                            .formatted(handlerConfig.getClass().getName())
            );
        }

        return new WaitConflictHandler(waitConflictHandlerConfig, repository, resultDeserializer, clock);
    }
}
