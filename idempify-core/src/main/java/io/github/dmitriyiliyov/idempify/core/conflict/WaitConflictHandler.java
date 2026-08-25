package io.github.dmitriyiliyov.idempify.core.conflict;

import io.github.dmitriyiliyov.idempify.core.Operation;
import io.github.dmitriyiliyov.idempify.core.OperationRepository;
import io.github.dmitriyiliyov.idempify.core.OperationStatus;
import io.github.dmitriyiliyov.idempify.core.ResultDeserializer;
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
    private final OperationRepository repository;
    private final ResultDeserializer resultDeserializer;
    private final Clock clock;

    public WaitConflictHandler(WaitConflictHandlerConfig config,
                               OperationRepository repository,
                               ResultDeserializer resultDeserializer,
                               Clock clock) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        if (!config.notEmpty()) {
            throw new IllegalArgumentException(
                    "config must decide every setting before it reaches a handler, but was %s".formatted(config)
            );
        }
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.resultDeserializer = Objects.requireNonNull(resultDeserializer, "responseDeserializer cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public <T> T handle(ConflictContext<T> context) {
        UUID idempotencyKey = context.getIdempotencyKey();
        Instant start = clock.instant();

        for (int i = 0; i < config.getMaxAttempts(); i++) {

            if (isWaitThresholdCrossed(start, config.getMaxDuration())) {
                throw new WaitTimeoutException(idempotencyKey);
            }

            Optional<Operation> nullableOperation = repository.findByIdempotencyKey(idempotencyKey);
            if (nullableOperation.isEmpty()) {
                throw new OperationDisappearedException(idempotencyKey);
            }

            Operation operation = nullableOperation.get();
            if (OperationStatus.PROCESSED.equals(operation.getStatus())) {
                return resultDeserializer.deserialize(
                        operation.getResult(),
                        context.getOperationResultType()
                );
            } else {
                backoff(config.getDelay(), config.getMultiplier(), i, idempotencyKey);
            }
        }

        throw new WaitAttemptsExhaustedException(idempotencyKey);
    }

    private boolean isWaitThresholdCrossed(Instant start, long waitDuration) {
        return Duration.between(start, clock.instant()).toMillis() > waitDuration;
    }

    private void backoff(long delay, double multiplier, int attempt, UUID key) {
        long timeToSleep = delay * (int) Math.pow(multiplier, attempt);
        try {
            Thread.sleep(timeToSleep);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for operation (idempotencyKey={}) to complete", key);
            throw new RuntimeException(
                    "Interrupted while waiting for operation (idempotencyKey=%s) to complete".formatted(key), ie
            );
        }
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }
}
