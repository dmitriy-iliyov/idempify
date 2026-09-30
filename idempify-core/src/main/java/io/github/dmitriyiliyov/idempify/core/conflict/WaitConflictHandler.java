package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.*;
import io.github.dmitriyiliyov.idempify.core.config.WaitConflictHandlerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class WaitConflictHandler implements ConflictHandler {

    private static final Logger log = LoggerFactory.getLogger(WaitConflictHandler.class);
    private final WaitConflictHandlerConfig config;
    private final NonBlockingOperationRepository repository;
    private final OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserialize;
    private final Clock clock;

    public WaitConflictHandler(WaitConflictHandlerConfig config,
                               NonBlockingOperationRepository repository,
                               OperationDeserializer<RawNonBlockingOperation, NonBlockingOperation> deserialize,
                               Clock clock) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        if (!config.notEmpty()) {
            throw new IllegalArgumentException(
                    "config must decide every setting before it reaches a handler, but was %s".formatted(config)
            );
        }
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.deserialize = Objects.requireNonNull(deserialize, "deserialize cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public NonBlockingOperation handle(ConflictContext context) {
        UUID idempotencyKey = context.getIdempotencyKey();
        Instant start = clock.instant();

        for (int i = 0; i < config.getMaxAttempts(); i++) {

            if (isWaitThresholdCrossed(start, config.getMaxDuration())) {
                throw new WaitTimeoutException(idempotencyKey);
            }

            Optional<RawNonBlockingOperation> nullableOperation = repository.find(idempotencyKey);
            if (nullableOperation.isEmpty()) {
                throw new OperationDisappearedException(
                        "Operation (idempotencyKey=%s) disappeared while waiting for it to complete"
                        .formatted(idempotencyKey)
                );
            }

            RawNonBlockingOperation operation = nullableOperation.get();
            if (OperationStatus.isProcessedStatus(operation.status())) {
                return deserialize.deserialize(operation, context.getOperationResultType());
            } else {
                backoff(i, start, idempotencyKey);
            }
        }

        throw new WaitAttemptsExhaustedException(idempotencyKey);
    }

    private boolean isWaitThresholdCrossed(Instant start, long waitDuration) {
        return Duration.between(start, clock.instant()).toMillis() > waitDuration;
    }

    /**
     * Sleeps no longer than what is left of the max duration, so the wait never overshoots it, and computes the
     * delay in floating point, so a long run of attempts saturates instead of overflowing.
     */
    private void backoff(int attempt, Instant start, UUID key) {
        long remaining = config.getMaxDuration() - Duration.between(start, clock.instant()).toMillis();
        if (remaining <= 0) {
            return;
        }

        double delay = config.getDelay() * Math.pow(config.getMultiplier(), attempt);
        long timeToSleep = (long) Math.min(delay, remaining);
        try {
            Thread.sleep(timeToSleep);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for operation (idempotencyKey={}) to complete", key);
            throw new WaitInterruptedException(key, ie);
        }
    }
}
