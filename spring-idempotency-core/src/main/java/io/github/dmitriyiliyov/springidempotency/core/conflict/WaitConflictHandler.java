package io.github.dmitriyiliyov.springidempotency.core.conflict;

import io.github.dmitriyiliyov.springidempotency.core.Operation;
import io.github.dmitriyiliyov.springidempotency.core.OperationRepository;
import io.github.dmitriyiliyov.springidempotency.core.OperationState;
import io.github.dmitriyiliyov.springidempotency.core.ResponseDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class WaitConflictHandler implements ConflictHandler {

    private static final Logger log = LoggerFactory.getLogger(WaitConflictHandler.class);
    private final OperationRepository repository;
    private final ResponseDeserializer responseDeserializer;
    private final int maxAttempt;
    private final long delay;
    private final int multiplier;

    public WaitConflictHandler(OperationRepository repository,
                               ResponseDeserializer responseDeserializer,
                               Integer maxAttempt,
                               Long delay,
                               Integer multiplier) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.responseDeserializer = Objects.requireNonNull(responseDeserializer, "responseDeserializer cannot be null");
        this.maxAttempt = Objects.requireNonNull(maxAttempt, "maxAttempt cannot be null");
        this.delay = Objects.requireNonNull(delay, "delay cannot be null");
        this.multiplier = Objects.requireNonNull(multiplier, "multiplier cannot be null");
    }

    @Override
    public <T> Optional<T> handle(UUID idempotencyKey, Class<T> c) {
        for (int i = 0; i < maxAttempt; i++) {
            Optional<Operation> nullableOperation = repository.findByIdempotencyKey(idempotencyKey);
            if (nullableOperation.isEmpty()) {
                return Optional.empty();
            }

            Operation operation = nullableOperation.get();
            if (OperationState.PROCESSED.equals(operation.getState())) {
                return Optional.of(responseDeserializer.deserialize(operation.getResponse(), c));
            } else {
                backoff(i, idempotencyKey);
            }
        }
        throw new WaitTimeoutException(
                "Operation (idempotencyKey=%s) did not complete within the configured timeout".formatted(idempotencyKey)
        );
    }

    private void backoff(int attempt, UUID key) {
        long timeToSleep = delay * (int) Math.pow(multiplier, attempt);
        try {
            Thread.sleep(timeToSleep);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for operation (idempotencyKey={}) to complete", key);
            throw new RuntimeException(ie);
        }
    }

    @Override
    public ConflictHandleStrategy getStrategy() {
        return ConflictHandleStrategy.WAIT;
    }
}
