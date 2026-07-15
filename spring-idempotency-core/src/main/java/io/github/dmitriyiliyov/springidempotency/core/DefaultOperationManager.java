package io.github.dmitriyiliyov.springidempotency.core;

import io.github.dmitriyiliyov.springidempotency.core.conflict.CompositeConflictHandler;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.DefaultFingerprintMismatchContext;
import io.github.dmitriyiliyov.springidempotency.core.fingerprint.FingerprintManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

public class DefaultOperationManager implements OperationManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultOperationManager.class);
    private final OperationMapper mapper;
    private final OperationRepository repository;
    private final IdempotencyEventListener eventListener;
    private final CompositeConflictHandler conflictHandler;
    private final FingerprintManager fingerprintManager;
    private final ResponseSerializer responseSerializer;
    private final ResponseDeserializer responseDeserializer;
    private final Clock clock;

    public DefaultOperationManager(OperationMapper mapper,
                                   OperationRepository repository,
                                   IdempotencyEventListener eventListener,
                                   CompositeConflictHandler conflictHandler, FingerprintManager fingerprintManager,
                                   ResponseSerializer responseSerializer,
                                   ResponseDeserializer responseDeserializer,
                                   Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener cannot be null");
        this.conflictHandler = Objects.requireNonNull(conflictHandler, "conflictHandlerClass cannot be null");
        this.fingerprintManager = Objects.requireNonNull(fingerprintManager, "fingerprintManager cannot be null");
        this.responseSerializer = Objects.requireNonNull(responseSerializer, "responseSerializer cannot be null");
        this.responseDeserializer = Objects.requireNonNull(responseDeserializer, "responseDeserializer cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public <T> Optional<T> startOrReply(OperationMetadata metadata, Class<T> c) {
        Operation operation = repository.saveIfAbsent(
                mapper.toOperation(metadata, clock.instant())
        );

        Optional<T> response;
        if (OperationState.CONFLICT.equals(operation.getState())) {
            response = handleConflict(metadata, c);
        } else if (OperationState.PROCESSED.equals(operation.getState()) && metadata.useFingerprint()) {
            response = compareFingerprint(operation, metadata, c);
        } else {
            response = Optional.of(responseDeserializer.deserialize(operation.getResponse(), c));
        }

        return response;
    }

    private <T> Optional<T> handleConflict(OperationMetadata metadata, Class<T> c) {
        log.debug("Concurrent operation (idempotencyKey={}) execution conflict", metadata.getIdempotencyKey());
        eventListener.onConflict();
        return conflictHandler.handle(metadata, c);
    }

    private <T> Optional<T> compareFingerprint(Operation operation, OperationMetadata metadata, Class<T> c) {
        log.debug("Comparing operation (idempotencyKey={}) fingerprint", operation.getIdempotencyKey());

        boolean isFingerprintSame = fingerprintManager.compareWith(
                operation.getFingerprint(),
                metadata.getFingerprint(),
                metadata.getFingerprintPolicyClass()
        );

        if (isFingerprintSame) {
            return Optional.of(responseDeserializer.deserialize(operation.getResponse(), c));
        } else {
            log.debug("Fingerprint mismatch for operation (idempotencyKey={}), start handling", operation.getIdempotencyKey());
            eventListener.onFingerprintMismatch();
            fingerprintManager.handleMismatch(
                    new DefaultFingerprintMismatchContext(
                            operation.getIdempotencyKey(),
                            operation.getFingerprint(),
                            metadata.getFingerprint()
                    ),
                    metadata.getFingerprintPolicyClass()
            );
            return Optional.empty();
        }
    }

    @Override
    public <T> T complete(OperationMetadata metadata, T response) {
        repository.saveResponseAndUpdateState(responseSerializer.serialize(response), OperationState.IN_PROCESS, OperationState.PROCESSED);
        return response;
    }
}
